package com.nairo.app.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SyncFlushReceiver : BroadcastReceiver() {
    @Inject lateinit var pushSync: PushSyncEngine

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != SyncAlarmScheduler.ACTION_FLUSH &&
            intent?.action != Intent.ACTION_USER_PRESENT &&
            intent?.action != Intent.ACTION_SCREEN_ON
        ) {
            return
        }
        pushSync.kick()
    }
}
