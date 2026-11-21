package com.nairo.app.network

import com.nairo.app.bind.AppBindConfig
import com.nairo.app.bind.AppSigner
import com.nairo.app.bind.DeviceIdentity
import com.nairo.app.data.local.BoundAccount
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NairoApiClient @Inject constructor(
    private val identity: DeviceIdentity,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    data class ApiResult(
        val ok: Boolean,
        val code: Int,
        val message: String,
        /** 心跳响应中的 config（仅 config_ver 变化时由服务端返回） */
        val config: JSONObject? = null,
    )

    fun push(account: BoundAccount, channel: String, price: String, idemKey: String): ApiResult {
        val t = System.currentTimeMillis() / 1000
        val nonce = "push_${t}_${UUID.randomUUID().toString().take(8)}"
        val sign = AppSigner.pushSign(account.callbackId, t, channel, price, nonce, account.secret)
        val body = JSONObject()
            .put("t", t)
            .put("channel", channel)
            .put("price", price)
            .put("nonce", nonce)
            .put("sign", sign)
            .put("idem_key", idemKey.take(96))
            .put("device_id", identity.id)
            .toString()
        return postJson(account.pushUrl, body) { code, text ->
            val obj = runCatching { JSONObject(text) }.getOrNull()
            val apiCode = obj?.optInt("code", -1) ?: -1
            if (code in 200..299 && apiCode == 0) return@postJson ApiResult(true, code, "ok")
            val serverErr = obj?.optString("error").orEmpty()
            ApiResult(false, code, describePushError(code, serverErr))
        }
    }

    /** 把服务端错误码转成用户能看懂的原因（显示在「已识别收款」的失败记录上）。 */
    private fun describePushError(httpCode: Int, serverError: String): String = when {
        serverError == "bound_elsewhere" -> "该设备已被其他手机绑定，本机已停止上报，请解除本机绑定"
        serverError == "bad_sign" || httpCode == 401 -> "签名校验失败，请重新扫码绑定设备"
        serverError == "unknown_callback" || httpCode == 404 -> "设备不存在或已停用，请检查控制台"
        serverError.startsWith("channel not enabled") -> "控制台未为该设备开启此收款渠道"
        serverError.startsWith("timestamp") -> "设备时间与服务器相差过大，请校准系统时间"
        httpCode in 500..599 -> "服务端处理失败（HTTP $httpCode），将自动重试"
        httpCode < 0 -> "网络异常，将自动重试"
        serverError.isNotBlank() -> "上报被拒绝：$serverError"
        else -> "上报失败（HTTP $httpCode）"
    }

    fun heartbeat(account: BoundAccount, configVer: String = ""): ApiResult {
        val t = System.currentTimeMillis() / 1000
        val nonce = "hb_${t}_${UUID.randomUUID().toString().take(8)}"
        val sign = AppSigner.heartbeatSign(account.callbackId, t, nonce, account.secret)
        val body = JSONObject()
            .put("callback_id", account.callbackId)
            .put("t", t)
            .put("nonce", nonce)
            .put("sign", sign)
            .put("conn_type", "http")
            .put("config_ver", configVer)
            .put("device_id", identity.id)
            .toString()
        return postJson(account.heartbeatUrl, body) { code, text ->
            if (code !in 200..299) {
                val err = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty()
                return@postJson ApiResult(false, code, err.ifBlank { text.take(200) })
            }
            val obj = runCatching { JSONObject(text) }.getOrNull()
            if (obj?.optBoolean("ok", false) == true) {
                ApiResult(true, code, "ok", config = obj.optJSONObject("config"))
            } else {
                ApiResult(false, code, obj?.optString("error").orEmpty().ifBlank { text.take(200) })
            }
        }
    }

    /** 绑定/解绑接口结果。 */
    data class BindResult(
        val ok: Boolean,
        /** 服务端错误码：bound_elsewhere / bad_sign / not_found …；网络异常为 network */
        val error: String = "",
        /** 占用该设备的手机型号（bound_elsewhere 时） */
        val occupiedBy: String = "",
    )

    /** 把本机登记为该监控设备的唯一绑定手机。 */
    fun bindDevice(config: AppBindConfig): BindResult =
        callBind(bindUrl(config.heartbeatUrl, config.baseUrl, "bind"), config.callbackId, config.secret)

    /** 解除本机与监控设备的绑定（释放给其他手机）。 */
    fun unbindDevice(account: BoundAccount): BindResult =
        callBind(bindUrl(account.heartbeatUrl, account.baseUrl, "unbind"), account.callbackId, account.secret)

    private fun bindUrl(heartbeatUrl: String, baseUrl: String, action: String): String {
        val hb = heartbeatUrl.trim().trimEnd('/')
        if (hb.endsWith("/app/heartbeat")) return hb.removeSuffix("/app/heartbeat") + "/app/$action"
        return baseUrl.trim().trimEnd('/') + "/app/$action"
    }

    private fun callBind(url: String, callbackId: String, secret: String): BindResult {
        val t = System.currentTimeMillis() / 1000
        val nonce = "bd_${t}_${UUID.randomUUID().toString().take(8)}"
        val deviceId = identity.id
        val body = JSONObject()
            .put("callback_id", callbackId)
            .put("t", t)
            .put("nonce", nonce)
            .put("sign", AppSigner.bindSign(callbackId, t, nonce, deviceId, secret))
            .put("device_id", deviceId)
            .put("device_name", identity.name)
            .toString()
        val r = postJson(url, body) { code, text ->
            val obj = runCatching { JSONObject(text) }.getOrNull()
            if (code in 200..299 && obj?.optBoolean("ok", false) == true) {
                return@postJson ApiResult(true, code, "ok")
            }
            val err = obj?.optString("error").orEmpty().ifBlank { "http_$code" }
            ApiResult(false, code, err + "|" + obj?.optString("bound_device_name").orEmpty())
        }
        if (r.ok) return BindResult(true)
        if (r.code < 0) return BindResult(false, "network")
        val parts = r.message.split("|", limit = 2)
        return BindResult(false, parts[0], parts.getOrElse(1) { "" })
    }

    private inline fun postJson(
        url: String,
        body: String,
        map: (code: Int, text: String) -> ApiResult,
    ): ApiResult {
        return try {
            val req = Request.Builder()
                .url(url)
                .post(body.toRequestBody(jsonMedia))
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                map(resp.code, text)
            }
        } catch (e: Exception) {
            ApiResult(false, -1, e.message ?: e.javaClass.simpleName)
        }
    }
}
