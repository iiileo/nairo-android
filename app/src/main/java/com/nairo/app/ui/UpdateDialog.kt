package com.nairo.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nairo.app.ui.components.NDialog
import com.nairo.app.ui.components.NProgress
import com.nairo.app.ui.theme.Danger
import com.nairo.app.ui.theme.Ink500
import com.nairo.app.ui.theme.Ink700
import com.nairo.app.update.UpdateInfo
import com.nairo.app.update.UpdateState

@Composable
fun UpdateDialog(
    state: UpdateState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val info: UpdateInfo = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.Ready -> state.info
        is UpdateState.Failed -> state.info ?: return
        else -> return
    }
    val downloading = state is UpdateState.Downloading
    val force = info.force

    val (confirmText, onConfirm) = when (state) {
        is UpdateState.Downloading -> "取消下载" to onCancel
        is UpdateState.Ready -> "立即安装" to onInstall
        is UpdateState.Failed -> "重试" to onDownload
        else -> "立即更新" to onDownload
    }

    NDialog(
        title = "发现新版本 ${info.versionName}",
        onDismiss = onDismiss,
        confirmText = confirmText,
        onConfirm = onConfirm,
        dismissText = if (!downloading && !force) "稍后" else null,
        cancelable = !downloading && !force,
    ) {
        if (info.apkSize > 0) {
            Text("安装包 ${formatSize(info.apkSize)}", style = MaterialTheme.typography.bodySmall, color = Ink500)
            Spacer(Modifier.height(10.dp))
        }
        if (info.notes.isNotBlank()) {
            Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                Text(info.notes, style = MaterialTheme.typography.bodyMedium, color = Ink700)
            }
            Spacer(Modifier.height(14.dp))
        }
        when (state) {
            is UpdateState.Downloading -> {
                val progress = if (state.total > 0) (state.received.toFloat() / state.total).coerceIn(0f, 1f) else null
                NProgress(progress)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.total > 0) {
                        "下载中 ${formatSize(state.received)} / ${formatSize(state.total)}"
                    } else {
                        "下载中 ${formatSize(state.received)}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Ink500,
                )
            }
            is UpdateState.Ready -> Text("下载完成，点击安装", style = MaterialTheme.typography.labelMedium, color = Ink500)
            is UpdateState.Failed -> Text(state.message, style = MaterialTheme.typography.labelMedium, color = Danger)
            else -> Unit
        }
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1) "%.1f MB".format(mb) else "%d KB".format(bytes / 1024)
}
