package com.nairo.app.ui.pages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nairo.app.data.local.BoundAccount
import com.nairo.app.data.local.NotificationEvent
import com.nairo.app.data.local.PushOutbox
import com.nairo.app.sync.PushSyncEngine
import com.nairo.app.ui.components.ChannelAvatar
import com.nairo.app.ui.components.EmptyState
import com.nairo.app.ui.components.NCard
import com.nairo.app.ui.components.NairoIcons
import com.nairo.app.ui.components.PageHeader
import com.nairo.app.ui.components.PillAction
import com.nairo.app.ui.components.plainClickable
import com.nairo.app.ui.components.Segmented
import com.nairo.app.ui.components.StatusPill
import com.nairo.app.ui.components.channelLabel
import com.nairo.app.ui.theme.AlipayBlue
import com.nairo.app.ui.theme.Brand
import com.nairo.app.ui.theme.BrandDeep
import com.nairo.app.ui.theme.BrandLight
import com.nairo.app.ui.theme.Danger
import com.nairo.app.ui.theme.DangerSoft
import com.nairo.app.ui.theme.Ink400
import com.nairo.app.ui.theme.Ink500
import com.nairo.app.ui.theme.Ink900
import com.nairo.app.ui.theme.Success
import com.nairo.app.ui.theme.SuccessSoft
import com.nairo.app.ui.theme.Warn
import com.nairo.app.ui.theme.WarnSoft
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TimelinePage(
    events: List<NotificationEvent>,
    pushItems: List<PushOutbox>,
    accounts: List<BoundAccount>,
    onRetry: (Long) -> Unit,
    onRetryAll: () -> Unit,
    onClear: () -> Unit,
    onGoDevices: () -> Unit,
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val failedCount = pushItems.count { it.status == PushOutbox.STATUS_FAILED }

    fun copyEvent(event: NotificationEvent) {
        val text = buildString {
            appendLine(event.appLabel)
            appendLine(event.title)
            appendLine(event.body)
            if (!event.amountHint.isNullOrBlank()) appendLine("金额: ¥${event.amountHint}")
            append("时间: ${formatTime(event.postedAt)}")
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("nairo_notify", text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeader(
                title = "到账",
                trailing = {
                    if (events.isNotEmpty() || pushItems.isNotEmpty()) {
                        PillAction(
                            text = "清空",
                            onClick = onClear,
                            color = Ink500,
                            background = Color.White,
                        )
                    }
                },
            )
        }
        item { SummaryCard(pushItems = pushItems, accounts = accounts) }
        if (accounts.isEmpty()) {
            item { BindHint(onGoDevices) }
        }
        item {
            Segmented(
                options = listOf("收款记录 ${pushItems.size}", "全部通知 ${events.size}"),
                selected = tabIndex,
                onSelect = { tabIndex = it },
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (tabIndex == 0) {
            if (pushItems.isEmpty()) {
                item {
                    EmptyState(
                        icon = NairoIcons.Receipt,
                        title = "暂无收款",
                        body = "识别到微信、支付宝的收款后，\n会在这里显示上报结果。",
                    )
                }
            } else {
                if (failedCount > 0) {
                    item { FailedBanner(count = failedCount, onRetryAll = onRetryAll) }
                }
                items(pushItems, key = { "p${it.id}" }) { item ->
                    PushRow(item = item, onRetry = { onRetry(item.id) })
                }
            }
        } else {
            if (events.isEmpty()) {
                item {
                    EmptyState(
                        icon = NairoIcons.Bell,
                        title = "暂无通知",
                        body = "收到的支付类通知会显示在这里，\n点按可复制内容。",
                    )
                }
            } else {
                items(events, key = { "e${it.id}" }) { item ->
                    NotificationRow(item = item, onClick = { copyEvent(item) })
                }
            }
        }
    }
}

/* ───────────── 顶部概览 ───────────── */

@Composable
private fun SummaryCard(pushItems: List<PushOutbox>, accounts: List<BoundAccount>) {
    val start = remember(pushItems.firstOrNull()?.id) { startOfToday() }
    val today = pushItems.filter { it.createdAt >= start }
    val total = today.fold(BigDecimal.ZERO) { acc, it -> acc + parseMoney(it.price) }
    val done = today.count { it.status == PushOutbox.STATUS_DONE }
    val waiting = today.size - done
    val onlineCount = accounts.count { it.isOnline() }
    val text = total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
    val whole = text.substringBefore('.')
    val frac = text.substringAfter('.', "00")

    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(BrandDeep, Brand, BrandLight))),
    ) {
        Box(
            Modifier
                .offset(x = 230.dp, y = (-50).dp)
                .size(190.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Box(
            Modifier
                .offset(x = 270.dp, y = 60.dp)
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f)),
        )
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "今日收款",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.weight(1f),
                )
                val (label, dot) = when {
                    accounts.isEmpty() -> "未绑定设备" to Color(0xFFFFC857)
                    onlineCount > 0 -> "$onlineCount 台在线" to Color(0xFF5CF2A5)
                    else -> "设备离线" to Color(0xFFFF9A8F)
                }
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 22.sp)) { append("¥ ") }
                    append(whole)
                    withStyle(SpanStyle(fontSize = 22.sp)) { append(".$frac") }
                },
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("笔数", today.size.toString(), Modifier.weight(1f))
                Stat("已上报", done.toString(), Modifier.weight(1f))
                Stat("待处理", waiting.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun BindHint(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WarnSoft)
            .plainClickable(onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "还没有绑定设备，收款不会上报",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF93500A),
            modifier = Modifier.weight(1f),
        )
        Text("去绑定", style = MaterialTheme.typography.labelLarge, color = Warn)
    }
}

@Composable
private fun FailedBanner(count: Int, onRetryAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DangerSoft)
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$count 笔上报失败",
            style = MaterialTheme.typography.titleSmall,
            color = Danger,
            modifier = Modifier.weight(1f),
        )
        PillAction(text = "全部重试", onClick = onRetryAll, color = Color.White, background = Danger)
    }
}

/* ───────────── 列表行 ───────────── */

@Composable
private fun PushRow(item: PushOutbox, onRetry: () -> Unit) {
    val (statusText, fg, bg) = when (item.status) {
        PushOutbox.STATUS_DONE -> Triple("已上报", Success, SuccessSoft)
        PushOutbox.STATUS_FAILED -> Triple("上报失败", Danger, DangerSoft)
        else -> Triple("上报中", Warn, WarnSoft)
    }
    NCard(radius = 18.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelAvatar(item.channel)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${channelLabel(item.channel)}收款",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink900,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${formatTime(item.createdAt)} · ${attemptsText(item)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "+¥${formatMoney(item.price)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink900,
                    )
                    Spacer(Modifier.height(4.dp))
                    StatusPill(statusText, fg, bg, dot = true)
                }
            }
            if (item.status != PushOutbox.STATUS_DONE && !item.lastError.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg.copy(alpha = 0.7f))
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.lastError,
                        style = MaterialTheme.typography.bodySmall,
                        color = fg,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.status == PushOutbox.STATUS_FAILED) {
                        Spacer(Modifier.width(8.dp))
                        PillAction(text = "重试", onClick = onRetry, color = Color.White, background = Danger)
                    }
                }
            } else if (item.status == PushOutbox.STATUS_FAILED) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PillAction(text = "重试", onClick = onRetry, color = Color.White, background = Danger)
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(item: NotificationEvent, onClick: () -> Unit) {
    NCard(radius = 18.dp, onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            ChannelAvatar(item.channelHint.ifBlank { item.packageName })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title.ifBlank { item.appLabel },
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink900,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!item.amountHint.isNullOrBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "¥${item.amountHint}",
                            style = MaterialTheme.typography.titleSmall,
                            color = Brand,
                        )
                    }
                }
                if (item.body.isNotBlank()) {
                    Text(
                        item.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink500,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${item.appLabel} · ${formatTime(item.postedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink400,
                )
            }
        }
    }
}

/* ───────────── 工具 ───────────── */

private fun attemptsText(item: PushOutbox): String = when (item.status) {
    PushOutbox.STATUS_DONE -> "上报 ${item.attempts} 次"
    PushOutbox.STATUS_FAILED -> "已试 ${item.attempts} 次"
    else -> "已试 ${item.attempts}/${PushSyncEngine.MAX_ATTEMPTS} 次"
}

private fun parseMoney(raw: String): BigDecimal =
    raw.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO

private fun formatMoney(raw: String): String =
    parseMoney(raw).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()

private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun formatTime(epoch: Long): String {
    val pattern = if (epoch >= startOfToday()) "今天 HH:mm:ss" else "MM-dd HH:mm"
    return SimpleDateFormat(pattern, Locale.CHINA).format(Date(epoch))
}
