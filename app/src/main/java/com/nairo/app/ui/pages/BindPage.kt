package com.nairo.app.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nairo.app.data.local.BoundAccount
import com.nairo.app.ui.components.IconTile
import com.nairo.app.ui.components.NCard
import com.nairo.app.ui.components.NDialog
import com.nairo.app.ui.components.NairoIcons
import com.nairo.app.ui.components.PageHeader
import com.nairo.app.ui.components.PillAction
import com.nairo.app.ui.components.PrimaryButton
import com.nairo.app.ui.components.SoftButton
import com.nairo.app.ui.components.StatusPill
import com.nairo.app.ui.components.EmptyState
import com.nairo.app.ui.components.channelLabel
import com.nairo.app.ui.components.plainClickable
import com.nairo.app.ui.theme.AlipayBlue
import com.nairo.app.ui.theme.AlipaySoft
import com.nairo.app.ui.theme.Brand
import com.nairo.app.ui.theme.BrandSoft
import com.nairo.app.ui.theme.Danger
import com.nairo.app.ui.theme.DangerSoft
import com.nairo.app.ui.theme.Ink400
import com.nairo.app.ui.theme.Ink500
import com.nairo.app.ui.theme.Ink900
import com.nairo.app.ui.theme.Success
import com.nairo.app.ui.theme.SuccessSoft
import com.nairo.app.ui.theme.Track
import com.nairo.app.ui.theme.WechatGreen
import com.nairo.app.ui.theme.WechatSoft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BindPage(
    accounts: List<BoundAccount>,
    onPaste: () -> Unit,
    onScan: () -> Unit,
    onUnbind: (String) -> Unit,
) {
    var pendingUnbind by remember { mutableStateOf<BoundAccount?>(null) }

    pendingUnbind?.let { acc ->
        NDialog(
            title = "解除绑定",
            onDismiss = { pendingUnbind = null },
            confirmText = "解除",
            confirmColor = Danger,
            onConfirm = {
                onUnbind(acc.callbackId)
                pendingUnbind = null
            },
        ) {
            Text(
                "解除后「${acc.name}」将不再上报收款，确定吗？",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink500,
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeader(
                title = "设备",
            )
        }
        if (accounts.isEmpty()) {
            item {
                EmptyState(
                    icon = NairoIcons.Smartphone,
                    title = "还没有绑定设备",
                    body = "在控制台创建监控设备，\n然后用本机扫码绑定即可开始监听。",
                ) {
                    PrimaryButton(text = "扫码绑定", onClick = onScan)
                    Spacer(Modifier.height(10.dp))
                    SoftButton(text = "粘贴绑定内容", onClick = onPaste)
                }
            }
        } else {
            items(accounts, key = { it.callbackId }) { acc ->
                AccountCard(acc = acc, onUnbind = { pendingUnbind = acc })
            }
            item {
                Text(
                    "一部手机只能绑定一个设备；如需绑定其他设备，请先解除当前绑定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink400,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun AccountCard(acc: BoundAccount, onUnbind: () -> Unit) {
    val online = acc.isOnline()
    NCard(radius = 20.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(
                    icon = NairoIcons.Smartphone,
                    tint = if (online) Brand else Ink400,
                    background = if (online) BrandSoft else Track,
                    size = 46.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        acc.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink900,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        heartbeatText(acc),
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink500,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (online) {
                    StatusPill("在线", Success, SuccessSoft, dot = true)
                } else {
                    StatusPill("离线", Ink500, Track, dot = true)
                }
            }
            val channels = acc.channels()
            if (channels.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    channels.forEach { ch ->
                        when (ch) {
                            "wechat", "wx" -> StatusPill("微信", WechatGreen, WechatSoft)
                            "alipay", "zfb" -> StatusPill("支付宝", AlipayBlue, AlipaySoft)
                            else -> StatusPill(channelLabel(ch), Ink500, Track)
                        }
                    }
                }
            }
            if (!online && !acc.lastError.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (acc.lastError.contains("bound_elsewhere")) {
                        "该设备已被其他手机绑定，本机已停止上报，请解除绑定"
                    } else {
                        "连接异常，请检查网络后重试"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Danger,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "解除绑定",
                    style = MaterialTheme.typography.labelLarge,
                    color = Danger,
                    modifier = Modifier.plainClickable(onUnbind),
                )
            }
        }
    }
}

private fun heartbeatText(acc: BoundAccount): String {
    val at = acc.lastHeartbeatAt ?: return "尚未收到心跳"
    val diff = System.currentTimeMillis() - at
    return when {
        diff < 10_000 -> "刚刚同步"
        diff < 60_000 -> "${diff / 1000} 秒前同步"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前同步"
        else -> "最近同步 " + SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(at))
    }
}
