package com.nairo.app.keepalive

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeepAliveController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
            if (value) KeepAliveService.start(context) else KeepAliveService.stop(context)
        }

    fun ensureStartedIfEnabled() {
        if (enabled) KeepAliveService.start(context)
    }

    companion object {
        const val PREFS = "nairo_keepalive"
        const val KEY_ENABLED = "enabled"
    }
}
