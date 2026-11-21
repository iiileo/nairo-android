package com.nairo.app.match

import com.nairo.app.bind.BoundAccountRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatchRulesSync @Inject constructor(
    private val accounts: BoundAccountRepository,
    private val store: MatchRulesStore,
) {
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var lastFetchAt = 0L

    suspend fun refreshIfNeeded(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastFetchAt < MIN_INTERVAL_MS) return
        mutex.withLock {
            if (!force && now - lastFetchAt < MIN_INTERVAL_MS) return
            val acc = accounts.list().firstOrNull() ?: return
            val base = resolveApiBase(acc.baseUrl, acc.heartbeatUrl)
            if (base.isEmpty()) return
            val url = "$base/api/v1/public/app-match-rules"
            val text = runCatching {
                val req = Request.Builder().url(url).get().header("Accept", "application/json").build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()
                }
            }.getOrNull()?.trim().orEmpty()
            if (text.isEmpty()) return
            val rules = runCatching { AppMatchRules.fromJson(text) }.getOrNull() ?: return
            store.save(rules, text)
            lastFetchAt = System.currentTimeMillis()
        }
    }

    companion object {
        const val MIN_INTERVAL_MS = 60_000L

        fun resolveApiBase(baseUrl: String, heartbeatUrl: String): String {
            val fromBase = baseUrl.trim().trimEnd('/')
            if (fromBase.startsWith("http")) return fromBase
            val hb = heartbeatUrl.trim().trimEnd('/')
            if (!hb.startsWith("http")) return ""
            return hb.removeSuffix("/app/heartbeat").trimEnd('/')
        }
    }
}
