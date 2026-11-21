package com.nairo.app.network

import com.nairo.app.bind.AppSigner
import com.nairo.app.data.local.BoundAccount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 优先用 WebSocket 维持 App 在线心跳；失败由调用方回落 HTTP。
 */
@Singleton
class HeartbeatWsClient @Inject constructor(
    private val identity: com.nairo.app.bind.DeviceIdentity,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private val sessions = ConcurrentHashMap<String, Session>()

    data class Result(
        val ok: Boolean,
        val message: String,
        /** pong 中的 config（仅 config_ver 变化时由服务端返回） */
        val config: JSONObject? = null,
    )

    fun stopAll() {
        sessions.keys.toList().forEach { stop(it) }
    }

    fun stop(callbackId: String) {
        sessions.remove(callbackId)?.close()
    }

    fun retainOnly(callbackIds: Set<String>) {
        sessions.keys.filter { it !in callbackIds }.forEach { stop(it) }
    }

    suspend fun ensureAndPing(account: BoundAccount, configVer: String = ""): Result {
        val url = account.resolvedHeartbeatWsUrl()
        if (url.isBlank()) return Result(false, "no_ws_url")

        val existing = sessions[account.callbackId]
        if (existing != null && existing.isOpen()) {
            return existing.sendHeartbeat(account, configVer, identity.id)
        }
        existing?.close()
        sessions.remove(account.callbackId)

        val session = Session(account.callbackId)
        val req = Request.Builder().url(url).build()
        val ws = client.newWebSocket(req, session.listener)
        session.attach(ws)
        sessions[account.callbackId] = session

        val opened = withTimeoutOrNull(8_000L) { session.awaitOpen() } ?: false
        if (!opened) {
            session.close()
            sessions.remove(account.callbackId)
            return Result(false, "ws_open_timeout")
        }
        return session.sendHeartbeat(account, configVer, identity.id)
    }

    private class Session(val callbackId: String) {
        private val open = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)
        private val openWait = CompletableDeferred<Boolean>()
        private var socket: WebSocket? = null
        @Volatile private var pending: CompletableDeferred<Result>? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                open.set(true)
                openWait.complete(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = runCatching { JSONObject(text) }.getOrNull()
                val ok = obj?.optBoolean("ok", false) == true
                val err = obj?.optString("error").orEmpty()
                pending?.complete(
                    Result(
                        ok,
                        if (ok) "ok" else err.ifBlank { text.take(120) },
                        config = if (ok) obj?.optJSONObject("config") else null,
                    ),
                )
                pending = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                open.set(false)
                if (!openWait.isCompleted) openWait.complete(false)
                pending?.complete(Result(false, t.message ?: "ws_failure"))
                pending = null
                closed.set(true)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                open.set(false)
                closed.set(true)
                pending?.complete(Result(false, "ws_closed"))
                pending = null
            }
        }

        fun attach(ws: WebSocket) {
            socket = ws
        }

        fun isOpen(): Boolean = open.get() && !closed.get()

        suspend fun awaitOpen(): Boolean = openWait.await()

        suspend fun sendHeartbeat(account: BoundAccount, configVer: String, deviceId: String): Result {
            val ws = socket ?: return Result(false, "no_socket")
            if (!isOpen()) return Result(false, "not_open")
            val t = System.currentTimeMillis() / 1000
            val nonce = "ws_${t}_${UUID.randomUUID().toString().take(8)}"
            val sign = AppSigner.heartbeatSign(account.callbackId, t, nonce, account.secret)
            val body = JSONObject()
                .put("type", "ping")
                .put("callback_id", account.callbackId)
                .put("t", t)
                .put("nonce", nonce)
                .put("sign", sign)
                .put("conn_type", "ws")
                .put("config_ver", configVer)
                .put("device_id", deviceId)
                .toString()
            val wait = CompletableDeferred<Result>()
            pending = wait
            if (!ws.send(body)) {
                pending = null
                return Result(false, "send_failed")
            }
            return withTimeoutOrNull(8_000L) { wait.await() } ?: Result(false, "pong_timeout")
        }

        fun close() {
            closed.set(true)
            open.set(false)
            runCatching { socket?.close(1000, "bye") }
            socket = null
            if (!openWait.isCompleted) openWait.complete(false)
            pending?.complete(Result(false, "closed"))
            pending = null
        }
    }
}

fun BoundAccount.resolvedHeartbeatWsUrl(): String {
    val explicit = heartbeatWsUrl.trim()
    if (explicit.startsWith("ws://") || explicit.startsWith("wss://")) return explicit

    fun toWs(httpish: String): String {
        val trimmed = httpish.trim().trimEnd('/')
        return when {
            trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
            trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
            else -> ""
        }
    }

    val fromHeartbeat = toWs(heartbeatUrl)
    if (fromHeartbeat.isNotEmpty()) {
        return if (fromHeartbeat.endsWith("/app/heartbeat")) "$fromHeartbeat/ws"
        else "$fromHeartbeat/app/heartbeat/ws"
    }
    val fromBase = toWs(baseUrl)
    if (fromBase.isNotEmpty()) return "$fromBase/app/heartbeat/ws"
    return ""
}
