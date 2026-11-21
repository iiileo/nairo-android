package com.nairo.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import com.nairo.app.notify.PaymentNotificationListener

object NotificationAccess {
    fun isEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        if (TextUtils.isEmpty(flat)) return false
        val expected = ComponentName(context, PaymentNotificationListener::class.java)
        return flat.split(':').any { piece ->
            ComponentName.unflattenFromString(piece)?.let { it == expected } == true
        }
    }

    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}
