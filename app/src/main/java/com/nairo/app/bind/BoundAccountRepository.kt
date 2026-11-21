package com.nairo.app.bind

import com.nairo.app.config.RuntimeConfigStore
import com.nairo.app.data.local.BoundAccount
import com.nairo.app.data.local.BoundAccountDao
import com.nairo.app.network.HeartbeatWsClient
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoundAccountRepository @Inject constructor(
    private val dao: BoundAccountDao,
    private val heartbeatWs: HeartbeatWsClient,
    private val runtimeConfig: RuntimeConfigStore,
) {
    val accounts: Flow<List<BoundAccount>> = dao.observeAll()

    suspend fun list(): List<BoundAccount> = dao.listAll()

    suspend fun bind(config: AppBindConfig) {
        val now = System.currentTimeMillis()
        dao.upsert(
            BoundAccount(
                callbackId = config.callbackId,
                name = config.name.ifBlank { config.callbackId },
                baseUrl = config.baseUrl,
                pushUrl = config.pushUrl,
                heartbeatUrl = config.heartbeatUrl,
                heartbeatWsUrl = config.heartbeatWsUrl,
                secret = config.secret,
                channelsCsv = config.channels.sorted().joinToString(","),
                boundAt = now,
            ),
        )
    }

    suspend fun unbind(callbackId: String) {
        heartbeatWs.stop(callbackId)
        runtimeConfig.clear(callbackId)
        dao.delete(callbackId)
    }

    suspend fun forChannel(channel: String): List<BoundAccount> =
        dao.listAll().filter { it.supportsChannel(channel) }

    /** 心跳下发的最新渠道（控制台改渠道后无需重新扫码）。 */
    suspend fun syncChannels(callbackId: String, channels: List<String>) {
        val csv = channels
            .map { it.trim().lowercase() }
            .filter { it == "wechat" || it == "alipay" }
            .distinct()
            .sorted()
            .joinToString(",")
        if (csv.isEmpty()) return
        dao.updateChannels(callbackId, csv)
    }

    suspend fun markHeartbeat(callbackId: String, ok: Boolean, error: String?) {
        dao.updateHeartbeat(
            callbackId = callbackId,
            at = System.currentTimeMillis(),
            ok = ok,
            error = error,
        )
    }
}
