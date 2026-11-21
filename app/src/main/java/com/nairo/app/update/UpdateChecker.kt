package com.nairo.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** 依次尝试各地址，返回首个成功解析的 latest.json；全部失败返回 null。 */
    suspend fun fetchLatest(useMirror: Boolean): UpdateInfo? = withContext(Dispatchers.IO) {
        for (url in UpdateSources.manifestUrls(useMirror)) {
            val info = runCatching {
                val req = Request.Builder().url(url).get()
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-cache")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()?.let { UpdateInfo.parse(it) }
                }
            }.getOrNull()
            if (info != null) return@withContext info
        }
        null
    }
}
