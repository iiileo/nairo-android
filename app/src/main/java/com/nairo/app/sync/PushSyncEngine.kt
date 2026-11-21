package com.nairo.app.sync

import com.nairo.app.bind.BoundAccountRepository
import com.nairo.app.config.RuntimeConfigStore
import com.nairo.app.data.local.NotificationEvent
import com.nairo.app.data.local.PushOutbox
import com.nairo.app.data.local.PushOutboxDao
import com.nairo.app.match.MatchRulesSync
import com.nairo.app.network.HeartbeatWsClient
import com.nairo.app.network.NairoApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushSyncEngine @Inject constructor(
    private val accounts: BoundAccountRepository,
    private val outboxDao: PushOutboxDao,
    private val api: NairoApiClient,
    private val heartbeatWs: HeartbeatWsClient,
    private val wakeLock: SyncWakeLock,
    private val alarms: SyncAlarmScheduler,
    private val matchRulesSync: MatchRulesSync,
    private val runtimeConfig: RuntimeConfigStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var loopJob: Job? = null

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                runCatching { tick() }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        heartbeatWs.stopAll()
    }

    fun kick() {
        scope.launch { runCatching { tick() } }
    }

    /** 绑定后立刻拉取服务端匹配规则 */
    fun refreshMatchRules() {
        scope.launch { runCatching { matchRulesSync.refreshIfNeeded(force = true) } }
    }

    /** 手动重试失败的上报；id 为空则重试全部失败项。 */
    fun retryFailed(id: Long? = null) {
        scope.launch {
            val now = System.currentTimeMillis()
            val n = if (id != null) outboxDao.resetForRetry(id, now) else outboxDao.resetAllFailed(now)
            if (n > 0) {
                kick()
                alarms.scheduleFlush(3_000L)
            }
        }
    }

    suspend fun enqueueFromEvent(event: NotificationEvent) {
        val amount = event.amountHint?.trim().orEmpty()
        val channel = event.channelHint.trim().lowercase()
        if (amount.isEmpty() || (channel != "wechat" && channel != "alipay")) return
        val targets = accounts.forChannel(channel)
        if (targets.isEmpty()) return
        val now = System.currentTimeMillis()
        for (acc in targets) {
            // 仅按 rawKey 去重（同一条系统通知）；同金额合法连单不拦截
            outboxDao.insertIgnore(
                PushOutbox(
                    rawKey = event.rawKey,
                    callbackId = acc.callbackId,
                    channel = channel,
                    price = amount,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        // 立刻推 + Doze 闹钟兜底，避免熄屏时协程/网络被拖到亮屏
        kick()
        alarms.scheduleFlush(1_500L)
        alarms.scheduleFlush(8_000L)
    }

    private suspend fun tick() = wakeLock.withWakeSuspend(90_000L) {
        mutex.withLock {
            flushOutbox()
            heartbeatAll()
            runCatching { matchRulesSync.refreshIfNeeded() }
        }
    }

    private suspend fun flushOutbox() {
        val pending = outboxDao.pending()
        for (item in pending) {
            val acc = accounts.list().firstOrNull { it.callbackId == item.callbackId }
            val now = System.currentTimeMillis()
            if (acc == null) {
                outboxDao.updateResult(
                    id = item.id,
                    status = PushOutbox.STATUS_FAILED,
                    attempts = item.attempts + 1,
                    error = "account_missing",
                    updatedAt = now,
                )
                continue
            }
            val res = api.push(acc, item.channel, item.price, idemKey = item.rawKey)
            val attempts = item.attempts + 1
            if (res.ok) {
                outboxDao.updateResult(
                    id = item.id,
                    status = PushOutbox.STATUS_DONE,
                    attempts = attempts,
                    error = null,
                    updatedAt = now,
                )
            } else {
                val status = if (attempts >= MAX_ATTEMPTS) PushOutbox.STATUS_FAILED else PushOutbox.STATUS_PENDING
                outboxDao.updateResult(
                    id = item.id,
                    status = status,
                    attempts = attempts,
                    error = res.message,
                    updatedAt = now,
                )
                if (status == PushOutbox.STATUS_PENDING) {
                    alarms.scheduleFlush(5_000L)
                }
            }
        }
    }

    /** 保存心跳下发的配置，并同步设备当前启用的支付渠道。 */
    private suspend fun applyConfig(callbackId: String, config: org.json.JSONObject) {
        val arr = config.optJSONArray("channels")
        if (arr != null) {
            val list = (0 until arr.length()).map { arr.optString(it) }
            accounts.syncChannels(callbackId, list)
        }
        runtimeConfig.save(callbackId, config)
    }

    private suspend fun heartbeatAll() {
        val live = accounts.list()
        heartbeatWs.retainOnly(live.map { it.callbackId }.toSet())
        for (acc in live) {
            val ver = runtimeConfig.version(acc.callbackId)
            val ws = heartbeatWs.ensureAndPing(acc, ver)
            if (ws.ok) {
                ws.config?.let { applyConfig(acc.callbackId, it) }
                accounts.markHeartbeat(acc.callbackId, true, null)
                continue
            }
            val http = api.heartbeat(acc, ver)
            if (http.ok) http.config?.let { applyConfig(acc.callbackId, it) }
            accounts.markHeartbeat(
                acc.callbackId,
                http.ok,
                if (http.ok) null else "ws:${ws.message}; http:${http.message}",
            )
            if (!http.ok) {
                heartbeatWs.stop(acc.callbackId)
            }
        }
    }

    companion object {
        const val HEARTBEAT_INTERVAL_MS = 10_000L
        const val MAX_ATTEMPTS = 8
    }
}
