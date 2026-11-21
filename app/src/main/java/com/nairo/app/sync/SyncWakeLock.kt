package com.nairo.app.sync

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 熄屏 Doze 下短时持有 CPU，避免 outbox 推送被拖到亮屏才发出。 */
@Singleton
class SyncWakeLock @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val lock: PowerManager.WakeLock by lazy {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nairo:push").apply {
            setReferenceCounted(false)
        }
    }

    fun withWake(timeoutMs: Long = 60_000L, block: () -> Unit) {
        synchronized(lock) {
            if (!lock.isHeld) lock.acquire(timeoutMs)
        }
        try {
            block()
        } finally {
            synchronized(lock) {
                if (lock.isHeld) runCatching { lock.release() }
            }
        }
    }

    suspend fun <T> withWakeSuspend(timeoutMs: Long = 60_000L, block: suspend () -> T): T {
        synchronized(lock) {
            if (!lock.isHeld) lock.acquire(timeoutMs)
        }
        try {
            return block()
        } finally {
            synchronized(lock) {
                if (lock.isHeld) runCatching { lock.release() }
            }
        }
    }
}
