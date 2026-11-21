package com.nairo.app.keepalive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nairo.app.R
import com.nairo.app.sync.PushSyncEngine
import com.nairo.app.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class KeepAliveService : Service() {

    @Inject lateinit var pushSync: PushSyncEngine

    private var silentVideo: SilentVideoKeeper? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // 亮屏兜底：Doze 期间积压的 outbox 立刻补推
            if (intent?.action == Intent.ACTION_SCREEN_ON ||
                intent?.action == Intent.ACTION_USER_PRESENT
            ) {
                pushSync.kick()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startAsForeground()
        pushSync.start()
        silentVideo = SilentVideoKeeper(applicationContext).also { it.start() }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        pushSync.start()
        pushSync.kick()
        if (silentVideo == null) {
            silentVideo = SilentVideoKeeper(applicationContext)
        }
        silentVideo?.start()
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        silentVideo?.stop()
        silentVideo = null
        pushSync.stop()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pulse_mark)
            .setContentTitle(getString(R.string.keepalive_title))
            .setContentText(getString(R.string.keepalive_body))
            .setContentIntent(launch)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.keepalive_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.keepalive_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "nairo_keepalive"
        private const val NOTIFICATION_ID = 10086

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: IllegalStateException) {
                // 后台无法启动前台服务（Android 12+ ForegroundServiceStartNotAllowedException 是其子类）：
                // 忽略，等界面打开或下次开机再启动，避免拖垮进程与通知监听。
            } catch (e: SecurityException) {
                // 通知/电池等权限缺失时同样忽略
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }
}
