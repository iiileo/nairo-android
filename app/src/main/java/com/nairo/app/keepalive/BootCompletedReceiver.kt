package com.nairo.app.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val prefs = context.applicationContext.getSharedPreferences(
            KeepAliveController.PREFS,
            Context.MODE_PRIVATE,
        )
        val enabled = prefs.getBoolean(KeepAliveController.KEY_ENABLED, true)
        if (enabled) {
            KeepAliveService.start(context.applicationContext)
        }
    }
}
