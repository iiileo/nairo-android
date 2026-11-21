package com.nairo.app.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nairo.app.keepalive.KeepAliveController
import com.nairo.app.ui.theme.NairoTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var keepAliveController: KeepAliveController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyKeepScreenOn()
        setContent {
            NairoTheme {
                NairoRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyKeepScreenOn()
    }

    private fun applyKeepScreenOn() {
        if (keepAliveController.enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
