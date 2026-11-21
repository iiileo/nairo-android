package com.nairo.app.update

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

data class AppVersion(val name: String, val code: Long) {
    companion object {
        fun current(context: Context): AppVersion {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            return AppVersion(
                name = info.versionName.orEmpty(),
                code = PackageInfoCompat.getLongVersionCode(info),
            )
        }
    }
}
