package com.nairo.app.bind

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本机安装标识：首次启动生成并持久化，用于「一部手机只绑定一个监控设备、
 * 一个监控设备只被一部手机绑定」的服务端校验。卸载重装会生成新标识。
 */
@Singleton
class DeviceIdentity @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("nairo_device", Context.MODE_PRIVATE)

    val id: String by lazy {
        prefs.getString(KEY_ID, null)?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().replace("-", "").also {
                prefs.edit().putString(KEY_ID, it).apply()
            }
    }

    val name: String
        get() {
            val maker = Build.MANUFACTURER.orEmpty().trim()
            val model = Build.MODEL.orEmpty().trim()
            val full = if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model"
            return full.trim().ifBlank { "Android" }.take(40)
        }

    private companion object {
        const val KEY_ID = "install_id"
    }
}
