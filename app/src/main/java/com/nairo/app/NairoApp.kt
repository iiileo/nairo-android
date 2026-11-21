package com.nairo.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NairoApp : Application() {
    // 注意：不要在 Application.onCreate 里启动前台服务。
    // 通知监听 / 广播拉起进程时 App 处于后台，Android 12+ 会抛
    // ForegroundServiceStartNotAllowedException，进程随之崩溃，监听也随之失效。
    // 前台服务改由 MonitorViewModel（界面打开时）与开机广播启动。
}
