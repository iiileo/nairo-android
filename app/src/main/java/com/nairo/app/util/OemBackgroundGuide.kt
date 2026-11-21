package com.nairo.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/**
 * 厂商杀后台无法靠代码彻底禁止，只能引导用户把本 App 加入自启动 / 后台白名单。
 * 各 ROM 设置页不公开稳定 API，以下为常见可跳转组件；失败则回退到应用详情。
 */
object OemBackgroundGuide {

    fun open(context: Context) {
        val candidates = buildList {
            addAll(vendorIntents(context))
            add(BatteryOptimization.appDetailsIntent(context))
            add(Intent(Settings.ACTION_SETTINGS))
        }
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (canStart(context, intent)) {
                runCatching { context.startActivity(intent) }
                    .onSuccess {
                        Toast.makeText(context, tipForBrand(), Toast.LENGTH_LONG).show()
                        return
                    }
            }
        }
        Toast.makeText(context, tipForBrand(), Toast.LENGTH_LONG).show()
    }

    fun tipForBrand(): String {
        val brand = Build.MANUFACTURER.orEmpty().lowercase()
        val modelHint = when {
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ->
                "小米：设置 → 应用设置 → 应用管理 → Nairo → 省电策略选「无限制」，并允许自启动/后台运行"
            brand.contains("huawei") || brand.contains("honor") ->
                "华为/荣耀：设置 → 电池 → 应用启动管理 → Nairo → 手动管理，允许自启动/关联启动/后台活动"
            brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") ->
                "OPPO/一加/真我：设置 → 电池 → 应用耗电管理 → Nairo → 允许后台运行，并打开自启动"
            brand.contains("vivo") || brand.contains("iqoo") ->
                "vivo/iQOO：设置 → 电池 → 后台耗电管理 → Nairo 允许高耗电，并在自启动中开启"
            brand.contains("samsung") ->
                "三星：设置 → 电池 → 后台使用限制，将 Nairo 移出休眠/受限；锁定最近任务"
            else ->
                "请在系统设置中允许 Nairo 自启动、后台运行，并关闭省电限制；最近任务里锁定本应用"
        }
        return modelHint
    }

    private fun vendorIntents(context: Context): List<Intent> {
        val pkg = context.packageName
        return listOf(
            // 小米
            component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            component("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
            // 华为 / 荣耀
            component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            component("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
            component("com.hihonor.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            // OPPO / Realme
            component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            component("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaugemodel.PowerUsageModelActivity"),
            // vivo
            component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            // 一加 / 通用电池优化列表
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$pkg")
            },
        )
    }

    private fun component(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls))

    private fun canStart(context: Context, intent: Intent): Boolean {
        val pm = context.packageManager
        intent.component?.let { cn ->
            return runCatching {
                pm.getActivityInfo(cn, PackageManager.MATCH_DEFAULT_ONLY)
                true
            }.getOrDefault(false)
        }
        return intent.resolveActivity(pm) != null
    }
}
