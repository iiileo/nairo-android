package com.nairo.app.update

import org.json.JSONArray
import org.json.JSONObject

/** 发布仓库 latest.json 的解析结果。 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val notes: String,
    val force: Boolean,
    val publishedAt: String,
    val apkName: String,
    val apkSize: Long,
    val apkSha256: String,
    val apkUrl: String,
    val mirrors: List<String>,
) {
    fun isNewerThan(currentVersionCode: Long): Boolean = versionCode > currentVersionCode

    fun supports(sdkInt: Int): Boolean = minSdk <= sdkInt

    companion object {
        fun parse(raw: String): UpdateInfo {
            val obj = JSONObject(raw.trim())
            val apk = obj.getJSONObject("apk")
            val url = apk.getString("url").trim()
            require(url.startsWith("https://")) { "bad_apk_url" }
            val code = obj.getLong("version_code")
            require(code > 0) { "bad_version_code" }
            return UpdateInfo(
                versionName = obj.getString("version_name").trim(),
                versionCode = code,
                minSdk = obj.optInt("min_sdk", 1),
                notes = obj.optString("notes").trim(),
                force = obj.optBoolean("force", false),
                publishedAt = obj.optString("published_at").trim(),
                apkName = apk.optString("name").ifBlank { url.substringAfterLast('/') },
                apkSize = apk.optLong("size", 0L),
                apkSha256 = apk.optString("sha256").trim().lowercase(),
                apkUrl = url,
                mirrors = readMirrors(obj.optJSONArray("mirrors")),
            )
        }

        private fun readMirrors(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val m = arr.optString(i).trim()
                if (m.startsWith("https://")) out += if (m.endsWith("/")) m else "$m/"
            }
            return out
        }
    }
}
