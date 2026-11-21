package com.nairo.app.keepalive

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.nairo.app.R

/**
 * 循环播放隐藏无声视频，降低部分 OEM 熄屏/休眠后杀后台的概率。
 * 使用 1×1 SurfaceTexture，不占可见 UI。
 */
class SilentVideoKeeper(
    private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var started = false

    private val restartRunnable = Runnable {
        if (started) {
            stopInternal()
            startInternal()
        }
    }

    fun start() {
        if (started) return
        started = true
        startInternal()
    }

    fun stop() {
        started = false
        mainHandler.removeCallbacks(restartRunnable)
        stopInternal()
    }

    private fun startInternal() {
        runCatching {
            @Suppress("DEPRECATION")
            val texture = SurfaceTexture(0).also { it.setDefaultBufferSize(1, 1) }
            val surf = Surface(texture)
            val mp = MediaPlayer().apply {
                setDataSource(
                    context,
                    Uri.parse("android.resource://${context.packageName}/${R.raw.silent_keepalive}"),
                )
                setSurface(surf)
                isLooping = true
                setVolume(0f, 0f)
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "silent video error what=$what extra=$extra")
                    scheduleRestart()
                    true
                }
                setOnCompletionListener {
                    // looping 正常不会走到；兜底重开
                    if (started) scheduleRestart()
                }
                setOnPreparedListener { prepared ->
                    if (!started) {
                        prepared.release()
                        return@setOnPreparedListener
                    }
                    runCatching { prepared.start() }
                        .onFailure {
                            Log.w(TAG, "silent video start failed", it)
                            scheduleRestart()
                        }
                }
                prepareAsync()
            }
            surfaceTexture = texture
            surface = surf
            player = mp
        }.onFailure {
            Log.w(TAG, "silent video init failed", it)
            scheduleRestart()
        }
    }

    private fun stopInternal() {
        runCatching {
            player?.setOnErrorListener(null)
            player?.setOnCompletionListener(null)
            player?.setOnPreparedListener(null)
            player?.stop()
        }
        runCatching { player?.reset() }
        runCatching { player?.release() }
        player = null
        runCatching { surface?.release() }
        surface = null
        runCatching { surfaceTexture?.release() }
        surfaceTexture = null
    }

    private fun scheduleRestart() {
        if (!started) return
        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.postDelayed(restartRunnable, RESTART_DELAY_MS)
    }

    companion object {
        private const val TAG = "SilentVideoKeeper"
        private const val RESTART_DELAY_MS = 3_000L
    }
}
