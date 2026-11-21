package com.nairo.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bound_accounts")
data class BoundAccount(
    @PrimaryKey val callbackId: String,
    val name: String,
    val baseUrl: String,
    val pushUrl: String,
    val heartbeatUrl: String,
    val heartbeatWsUrl: String = "",
    val secret: String,
    /** 逗号分隔，如 alipay,wechat */
    val channelsCsv: String,
    val boundAt: Long,
    val lastHeartbeatAt: Long? = null,
    val lastHeartbeatOk: Boolean = false,
    val lastError: String? = null,
) {
    fun channels(): List<String> =
        channelsCsv.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    fun supportsChannel(channel: String): Boolean =
        channels().contains(channel.trim().lowercase())

    fun isOnline(now: Long = System.currentTimeMillis(), windowMs: Long = 35_000L): Boolean {
        val at = lastHeartbeatAt ?: return false
        return lastHeartbeatOk && now - at <= windowMs
    }
}
