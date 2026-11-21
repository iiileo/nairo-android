package com.nairo.app.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nairo.app.match.MatchRulesSnapshot
import com.nairo.app.ui.MonitorUiState
import com.nairo.app.ui.components.ChevronTrailing
import com.nairo.app.ui.components.GroupCard
import com.nairo.app.ui.components.GroupDivider
import com.nairo.app.ui.components.NSwitch
import com.nairo.app.ui.components.NairoIcons
import com.nairo.app.ui.components.PageHeader
import com.nairo.app.ui.components.PillAction
import com.nairo.app.ui.components.SectionTitle
import com.nairo.app.ui.components.SettingRow
import com.nairo.app.ui.components.StatusPill
import com.nairo.app.ui.theme.AlipayBlue
import com.nairo.app.ui.theme.AlipaySoft
import com.nairo.app.ui.theme.Brand
import com.nairo.app.ui.theme.BrandSoft
import com.nairo.app.ui.theme.Ink400
import com.nairo.app.ui.theme.Ink500
import com.nairo.app.ui.theme.Success
import com.nairo.app.ui.theme.SuccessSoft
import com.nairo.app.ui.theme.Track
import com.nairo.app.ui.theme.Warn
import com.nairo.app.ui.theme.WarnSoft
import com.nairo.app.ui.theme.WechatGreen
import com.nairo.app.ui.theme.WechatSoft

@Composable
fun StatusPage(
    uiState: MonitorUiState,
    matchRules: MatchRulesSnapshot,
    onOpenListenerSettings: () -> Unit,
    onRequestBattery: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onToggleKeepAlive: (Boolean) -> Unit,
    onRequestPostNotifications: () -> Unit,
    onOpenAutostartHint: () -> Unit,
    onRefreshMatchRules: () -> Unit,
    onOpenMatchRules: () -> Unit,
    update: UpdateUi,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PageHeader(title = "设置") }

        item { SectionTitle("运行状态") }
        item {
            GroupCard {
                SettingRow(
                    icon = NairoIcons.Bell,
                    tint = WechatGreen,
                    tileBackground = WechatSoft,
                    title = "通知使用权",
                    detail = if (uiState.listenerEnabled) "正在监听收款通知" else "未开启，无法读取收款通知",
                    onClick = onOpenListenerSettings,
                    trailing = { StateTrailing(uiState.listenerEnabled, "去开启") },
                )
                GroupDivider()
                SettingRow(
                    icon = NairoIcons.Battery,
                    tint = Warn,
                    tileBackground = WarnSoft,
                    title = "电池优化",
                    detail = if (uiState.batteryIgnored) "已允许后台运行" else "未允许，熄屏后可能中断",
                    onClick = onRequestBattery,
                    trailing = { StateTrailing(uiState.batteryIgnored, "去允许") },
                )
                GroupDivider()
                SettingRow(
                    icon = NairoIcons.Bolt,
                    tint = Brand,
                    tileBackground = BrandSoft,
                    title = "保持后台运行",
                    detail = "通知栏常驻，避免被系统清理",
                    trailing = {
                        NSwitch(
                            checked = uiState.keepAliveEnabled,
                            onCheckedChange = {
                                if (it) onRequestPostNotifications()
                                onToggleKeepAlive(it)
                            },
                        )
                    },
                )
            }
        }

        item { SectionTitle("后台保活") }
        item {
            GroupCard {
                SettingRow(
                    icon = NairoIcons.Lock,
                    tint = AlipayBlue,
                    tileBackground = AlipaySoft,
                    title = "自启动与后台白名单",
                    detail = "部分手机需在系统中手动允许",
                    onClick = onOpenAutostartHint,
                    trailing = { ChevronTrailing() },
                )
                GroupDivider()
                SettingRow(
                    icon = NairoIcons.Settings,
                    tint = Ink500,
                    tileBackground = Track,
                    title = "应用详情",
                    detail = "查看权限与电量设置",
                    onClick = onOpenAppDetails,
                    trailing = { ChevronTrailing() },
                )
            }
        }

        item { SectionTitle("识别与更新") }
        item {
            GroupCard {
                SettingRow(
                    icon = NairoIcons.Rules,
                    tint = Brand,
                    tileBackground = BrandSoft,
                    title = if (matchRules.fromServer) "识别规则 · 已同步" else "识别规则 · 内置",
                    detail = "版本 ${matchRules.rules.version}" +
                        (if (matchRules.rules.updatedAt.isNotBlank()) " · ${matchRules.rules.updatedAt}" else "") +
                        " · 点击查看",
                    onClick = onOpenMatchRules,
                    trailing = { ChevronTrailing() },
                )
                GroupDivider()
                SettingRow(
                    icon = NairoIcons.Refresh,
                    tint = WechatGreen,
                    tileBackground = WechatSoft,
                    title = "检查更新",
                    detail = update.statusText,
                    onClick = if (update.busy) null else update.onCheck,
                    trailing = {
                        PillAction(
                            text = if (update.busy) "请稍候" else "检查",
                            onClick = update.onCheck,
                            enabled = !update.busy,
                        )
                    },
                )
                GroupDivider()
                SettingRow(
                    icon = NairoIcons.Download,
                    tint = AlipayBlue,
                    tileBackground = AlipaySoft,
                    title = "加速下载",
                    detail = "通过 gh-proxy 加速，失败自动直连",
                    trailing = { NSwitch(update.useMirror, update.onToggleMirror) },
                )
            }
        }

        item {
            Spacer(Modifier.height(10.dp))
            Text(
                "Nairo 奈洛 · v${update.versionName}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = Ink400,
            )
        }
    }
}

@Composable
private fun StateTrailing(ok: Boolean, actionText: String) {
    if (ok) {
        StatusPill("已开启", Success, SuccessSoft, dot = true)
    } else {
        StatusPill(actionText, Warn, WarnSoft)
    }
}

/** 设置页「识别与更新」区需要的更新状态与回调。 */
data class UpdateUi(
    val versionName: String,
    val statusText: String,
    val busy: Boolean,
    val useMirror: Boolean,
    val onCheck: () -> Unit,
    val onToggleMirror: (Boolean) -> Unit,
)
