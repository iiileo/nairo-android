package com.nairo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nairo.app.ui.theme.AlipayBlue
import com.nairo.app.ui.theme.AlipaySoft
import com.nairo.app.ui.theme.Brand
import com.nairo.app.ui.theme.BrandDeep
import com.nairo.app.ui.theme.BrandSoft
import com.nairo.app.ui.theme.Hairline
import com.nairo.app.ui.theme.Ink400
import com.nairo.app.ui.theme.Ink500
import com.nairo.app.ui.theme.Ink900
import com.nairo.app.ui.theme.Track
import com.nairo.app.ui.theme.WechatGreen
import com.nairo.app.ui.theme.WechatSoft

/* ───────────── 基础容器 ───────────── */

@Composable
fun Modifier.plainClickable(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )

/** 白色圆角卡片：1dp 发丝边框，无阴影。 */
@Composable
fun NCard(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(BorderStroke(1.dp, Hairline), shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        content = content,
    )
}

/** 大标题页眉：不使用系统 TopAppBar。 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 0.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Ink900)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Ink500)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = Ink500,
    )
}

/* ───────────── 小部件 ───────────── */

@Composable
fun StatusPill(
    text: String,
    color: Color,
    background: Color,
    modifier: Modifier = Modifier,
    dot: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** 渠道头像：圆形底色 + 单字。 */
@Composable
fun ChannelAvatar(channel: String, modifier: Modifier = Modifier, size: Dp = 42.dp) {
    val (letter, fg, bg) = when (channel.lowercase()) {
        "wechat", "wx" -> Triple("微", WechatGreen, WechatSoft)
        "alipay", "zfb" -> Triple("支", AlipayBlue, AlipaySoft)
        else -> Triple("?", Ink500, Track)
    }
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            color = fg,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

fun channelLabel(channel: String): String = when (channel.lowercase()) {
    "wechat", "wx" -> "微信"
    "alipay", "zfb" -> "支付宝"
    else -> channel.ifBlank { "未知渠道" }
}

@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** iOS 风格分段控件：灰色轨道 + 白色滑块。 */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Track)
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selected, label = "segmentOffset")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(11.dp), clip = false)
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val color by animateColorAsState(if (index == selected) Ink900 else Ink500, label = "segmentColor")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .plainClickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = color,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/* ───────────── 按钮 ───────────── */

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(
                if (enabled) Brush.linearGradient(listOf(Brand, BrandDeep)) else Brush.linearGradient(listOf(Track, Track)),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) Color.White else Ink400,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    color: Color = Brand,
    background: Color = BrandSoft,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, style = MaterialTheme.typography.titleSmall)
    }
}

/** 紧凑的胶囊文字按钮。 */
@Composable
fun PillAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Brand,
    background: Color = BrandSoft,
    enabled: Boolean = true,
    leading: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = if (enabled) color else Ink400, style = MaterialTheme.typography.labelLarge)
    }
}

/* ───────────── 设置行 / 分组 ───────────── */

@Composable
fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    NCard(radius = 18.dp, content = content)
}

@Composable
fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp),
        color = Hairline,
    )
}

@Composable
fun SettingRow(
    icon: ImageVector,
    tint: Color,
    tileBackground: Color,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint, tileBackground)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink900)
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Ink500)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
fun ChevronTrailing() {
    Icon(NairoIcons.Chevron, null, tint = Ink400, modifier = Modifier.size(20.dp))
}

@Composable
fun NSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Brand,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFD0D5DD),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/* ───────────── 空状态 ───────────── */

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 40.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(BrandSoft),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Brand, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Ink900)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink500,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        content()
    }
}

/* ───────────── 底部导航 ───────────── */

data class BarItem(val label: String, val icon: ImageVector)

@Composable
fun NairoBottomBar(
    items: List<BarItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(Color.White)) {
        HorizontalDivider(color = Hairline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(62.dp),
        ) {
            items.forEachIndexed { index, item ->
                val active = index == selected
                val tint by animateColorAsState(if (active) Brand else Ink400, label = "tabTint")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .plainClickable { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .width(if (active) 18.dp else 0.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Brand),
                    )
                    Spacer(Modifier.height(7.dp))
                    Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.label,
                        color = tint,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/* ───────────── 对话框 ───────────── */

@Composable
fun NDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = "取消",
    confirmColor: Color = Brand,
    confirmEnabled: Boolean = true,
    cancelable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (cancelable) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = cancelable, dismissOnClickOutside = cancelable),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Ink900)
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (dismissText != null) {
                    SoftButton(
                        text = dismissText,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        height = 46.dp,
                        color = Ink500,
                        background = Track,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (confirmEnabled) confirmColor else Track)
                        .clickable(enabled = confirmEnabled, onClick = onConfirm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        confirmText,
                        color = if (confirmEnabled) Color.White else Ink400,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

/** 轻量进度条（圆角、品牌色）。progress 为 null 表示不确定。 */
@Composable
fun NProgress(progress: Float?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(Track),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fraction = progress?.coerceIn(0f, 1f) ?: 0.35f
            Box(
                Modifier
                    .width(maxWidth * fraction)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(Brand, BrandDeep))),
            )
        }
    }
}

