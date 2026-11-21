package com.nairo.app.sync

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicInteger

/** Doze 下用允许空闲触发的闹钟再踢一次上报。 */
@Singleton
class SyncAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val seq = AtomicInteger(1)

    fun scheduleFlush(delayMs: Long = 2_000L) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val code = seq.getAndIncrement() and 0xffff
        val pi = pendingIntent(code)
        val at = SystemClock.elapsedRealtime() + delayMs
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
                    if (exactOk) {
                        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
                    }
                }
                else -> am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            }
        }
    }

    private fun pendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(context, SyncFlushReceiver::class.java).setAction(ACTION_FLUSH)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_FLUSH = "com.nairo.app.action.SYNC_FLUSH"
    }
}
