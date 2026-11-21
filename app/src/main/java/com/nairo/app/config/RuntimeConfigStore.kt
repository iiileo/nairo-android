package com.nairo.app.config

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** 单个 IM 机器人 Webhook（心跳下发，商户级 + 本设备专属）。 */
data class NotifyWebhook(
    val id: String,
    val scope: String,
    val provider: String,
    val name: String,
    val webhookUrl: String,
    val secret: String,
    val eventOrderPaid: Boolean,
    val eventFeeLow: Boolean,
    val eventAppPush: Boolean,
    val eventDeviceOffline: Boolean,
)

/**
 * 心跳响应里的 config（notify_channels + version），按设备 callback_id 缓存。
 * 请求心跳时带上 [version]，服务端仅在变化时返回完整 config。
 */
@Singleton
class RuntimeConfigStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun version(callbackId: String): String =
        prefs.getString(verKey(callbackId), "").orEmpty()

    fun webhooks(callbackId: String): List<NotifyWebhook> {
        val raw = prefs.getString(jsonKey(callbackId), null) ?: return emptyList()
        return parseWebhooks(raw)
    }

    /** @param config 服务端 config 对象（含 version、notify_channels） */
    fun save(callbackId: String, config: JSONObject) {
        val ver = config.optString("version").trim()
        if (ver.isEmpty()) return
        prefs.edit()
            .putString(verKey(callbackId), ver)
            .putString(jsonKey(callbackId), config.toString())
            .apply()
    }

    fun clear(callbackId: String) {
        prefs.edit().remove(verKey(callbackId)).remove(jsonKey(callbackId)).apply()
    }

    companion object {
        private const val PREFS = "nairo_runtime_config"

        private fun verKey(id: String) = "ver_$id"
        private fun jsonKey(id: String) = "json_$id"

        fun parseWebhooks(raw: String): List<NotifyWebhook> {
            val arr = runCatching { JSONObject(raw).optJSONArray("notify_channels") }.getOrNull()
                ?: return emptyList()
            val out = ArrayList<NotifyWebhook>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("webhook_url").trim()
                if (url.isEmpty()) continue
                out += NotifyWebhook(
                    id = o.optString("id"),
                    scope = o.optString("scope"),
                    provider = o.optString("provider"),
                    name = o.optString("name"),
                    webhookUrl = url,
                    secret = o.optString("secret"),
                    eventOrderPaid = o.optBoolean("event_order_paid", true),
                    eventFeeLow = o.optBoolean("event_fee_low", false),
                    eventAppPush = o.optBoolean("event_app_push", true),
                    eventDeviceOffline = o.optBoolean("event_device_offline", true),
                )
            }
            return out
        }
    }
}
