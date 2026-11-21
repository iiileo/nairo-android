package com.nairo.app.bind

import org.json.JSONArray
import org.json.JSONObject

/**
 * Console 二维码 / 粘贴导入的账户绑定配置（与 api_service AppBindConfig 对齐）。
 */
data class AppBindConfig(
    val v: Int,
    val name: String,
    val baseUrl: String,
    val pushUrl: String,
    val heartbeatUrl: String,
    val heartbeatWsUrl: String,
    val callbackId: String,
    val secret: String,
    val channels: List<String>,
) {
    companion object {
        fun parse(raw: String): AppBindConfig {
            val trimmed = raw.trim()
            require(trimmed.isNotEmpty()) { "empty_config" }
            val obj = JSONObject(trimmed)
            val v = obj.optInt("v", 0)
            require(v == 1) { "unsupported_config_v" }
            val callbackId = obj.getString("callback_id").trim()
            val secret = obj.getString("secret").trim()
            val pushUrl = obj.getString("push_url").trim()
            val heartbeatUrl = obj.getString("heartbeat_url").trim()
            require(callbackId.isNotEmpty() && secret.isNotEmpty()) { "missing_secret_or_id" }
            require(pushUrl.startsWith("http")) { "bad_push_url" }
            require(heartbeatUrl.startsWith("http")) { "bad_heartbeat_url" }
            val channels = parseChannels(obj.optJSONArray("channels"))
            require(channels.isNotEmpty()) { "channels_required" }
            val ws = obj.optString("heartbeat_ws_url").trim().ifBlank {
                deriveWsUrl(heartbeatUrl, obj.optString("base_url").trim())
            }
            return AppBindConfig(
                v = v,
                name = obj.optString("name").trim(),
                baseUrl = obj.optString("base_url").trim(),
                pushUrl = pushUrl,
                heartbeatUrl = heartbeatUrl,
                heartbeatWsUrl = ws,
                callbackId = callbackId,
                secret = secret,
                channels = channels,
            )
        }

        fun deriveWsUrl(heartbeatUrl: String, baseUrl: String): String {
            fun toWs(httpish: String): String {
                val trimmed = httpish.trim().trimEnd('/')
                return when {
                    trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
                    trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
                    else -> ""
                }
            }
            val fromHb = toWs(heartbeatUrl)
            if (fromHb.isNotEmpty()) {
                return if (fromHb.endsWith("/app/heartbeat")) "$fromHb/ws" else "$fromHb/app/heartbeat/ws"
            }
            val fromBase = toWs(baseUrl)
            return if (fromBase.isNotEmpty()) "$fromBase/app/heartbeat/ws" else ""
        }

        private fun parseChannels(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            val out = LinkedHashSet<String>()
            for (i in 0 until arr.length()) {
                val c = arr.optString(i).trim().lowercase()
                if (c == "wechat" || c == "alipay") out += c
            }
            return out.toList()
        }
    }
}
