package com.nairo.app.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun updatesDir(): File = File(context.cacheDir, "updates").apply { mkdirs() }

    /** 清理历史安装包（保留 [keepName]）。 */
    fun cleanup(keepName: String? = null) {
        updatesDir().listFiles()?.forEach { if (it.name != keepName) it.delete() }
    }

    /**
     * 下载并校验安装包；依次尝试加速 / 直连地址。
     * @param onProgress (已下载字节, 总字节，未知为 0)
     */
    suspend fun download(
        info: UpdateInfo,
        useMirror: Boolean,
        onProgress: (Long, Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dest = File(updatesDir(), safeName(info))
        cleanup(keepName = dest.name)
        if (dest.exists() && verify(dest, info)) return@withContext dest

        var lastError: Throwable? = null
        for (url in UpdateSources.apkUrls(info, useMirror)) {
            try {
                return@withContext fetch(url, dest, info, onProgress)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw IllegalStateException(lastError?.message ?: "download_failed", lastError)
    }

    private suspend fun fetch(
        url: String,
        dest: File,
        info: UpdateInfo,
        onProgress: (Long, Long) -> Unit,
    ): File {
        val part = File(dest.parentFile, dest.name + ".part")
        part.delete()
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "http_${resp.code}" }
            val body = checkNotNull(resp.body) { "empty_body" }
            val total = body.contentLength().takeIf { it > 0 } ?: info.apkSize
            var received = 0L
            body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lastReport = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        received += n
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= 150) {
                            lastReport = now
                            onProgress(received, total)
                        }
                    }
                }
            }
            onProgress(received, total)
        }
        check(verify(part, info)) { "verify_failed" }
        dest.delete()
        check(part.renameTo(dest)) { "rename_failed" }
        return dest
    }

    private fun verify(file: File, info: UpdateInfo): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        if (info.apkSize > 0 && file.length() != info.apkSize) return false
        if (info.apkSha256.isNotEmpty() && sha256(file) != info.apkSha256) return false
        return true
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun safeName(info: UpdateInfo): String {
        val base = info.apkName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (base.endsWith(".apk")) base else "nairo-${info.versionName}.apk"
    }
}
