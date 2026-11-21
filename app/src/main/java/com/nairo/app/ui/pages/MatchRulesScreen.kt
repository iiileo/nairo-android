package com.nairo.app.ui.pages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nairo.app.match.AppMatchRules
import com.nairo.app.match.MatchRulesSnapshot
import com.nairo.app.ui.components.ChannelAvatar
import com.nairo.app.ui.components.GroupCard
import com.nairo.app.ui.components.GroupDivider
import com.nairo.app.ui.components.NCard
import com.nairo.app.ui.components.NairoIcons
import com.nairo.app.ui.components.PillAction
import com.nairo.app.ui.components.SectionTitle
import com.nairo.app.ui.components.StatusPill
import com.nairo.app.ui.components.channelLabel
import com.nairo.app.ui.components.plainClickable
import com.nairo.app.ui.theme.Brand
import com.nairo.app.ui.theme.BrandSoft
import com.nairo.app.ui.theme.Canvas
import com.nairo.app.ui.theme.Ink400
import com.nairo.app.ui.theme.Ink500
import com.nairo.app.ui.theme.Ink900
import com.nairo.app.ui.theme.Success
import com.nairo.app.ui.theme.SuccessSoft
import com.nairo.app.ui.theme.Track
import com.nairo.app.ui.theme.Warn
import com.nairo.app.ui.theme.WarnSoft

/** 识别规则详情：展示当前生效的监听包名、关键词、金额正则与原始 JSON。 */
@Composable
fun MatchRulesScreen(
    snapshot: MatchRulesSnapshot,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val rules = snapshot.rules
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onClick = onBack) {
                    Icon(NairoIcons.Back, contentDescription = "返回", tint = Ink900, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "识别规则",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink900,
                    modifier = Modifier.weight(1f),
                )
                PillAction(text = "刷新", onClick = onRefresh)
            }
        }

        item { SectionTitle("概览") }
        item {
            NCard(radius = 18.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (snapshot.fromServer) {
                            StatusPill("已同步服务器", Success, SuccessSoft, dot = true)
                        } else {
                            StatusPill("内置默认", Warn, WarnSoft, dot = true)
                        }
                    }
                    InfoLine("版本", "v${rules.version}")
                    InfoLine("更新时间", rules.updatedAt.ifBlank { "—" })
                    InfoLine(
                        "规则条数",
                        "监听 ${rules.watchPackages.size} · 收款词 ${rules.receiveKeywords.size} · " +
                            "付款词 ${rules.outboundKeywords.size} · 忽略词 ${rules.ignoreKeywords.size} · " +
                            "金额正则 ${rules.amountPatterns.size}",
                    )
                }
            }
        }

        item { SectionTitle("监听的 App") }
        item {
            GroupCard {
                if (rules.watchPackages.isEmpty()) {
                    EmptyLine("未配置监听包名")
                } else {
                    rules.watchPackages.entries.forEachIndexed { index, (channel, pkg) ->
                        if (index > 0) GroupDivider()
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ChannelAvatar(channel, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(channelLabel(channel), style = MaterialTheme.typography.titleSmall, color = Ink900)
                                Text(
                                    pkg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Ink500,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        item { SectionTitle("收款关键词（命中即视为收款）") }
        item { KeywordCard(rules.receiveKeywords, tone = Success, toneSoft = SuccessSoft) }

        item { SectionTitle("付款关键词（出账，不上报）") }
        item { KeywordCard(rules.outboundKeywords, tone = Brand, toneSoft = BrandSoft) }

        item { SectionTitle("忽略关键词（命中即丢弃）") }
        item { KeywordCard(rules.ignoreKeywords, tone = Ink500, toneSoft = Track) }

        item { SectionTitle("文本清洗正则") }
        item {
            RegexCard(
                rules.sanitizePatterns.mapIndexed { i, p -> "规则 ${i + 1}" to p },
                emptyText = "无",
            )
        }

        item { SectionTitle("金额提取正则") }
        item {
            RegexCard(
                rules.amountPatterns.map { p -> "${p.name} · 第 ${p.group} 组" to p.regex },
                emptyText = "无",
            )
        }

        if (snapshot.rawJson.isNotBlank()) {
            item { SectionTitle("原始规则 JSON") }
            item {
                NCard(radius = 18.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            snapshot.rawJson,
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink900,
                            fontFamily = FontFamily.Monospace,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            PillAction(
                                text = "复制 JSON",
                                onClick = { copyJson(context, snapshot.rawJson) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeywordCard(words: List<String>, tone: Color, toneSoft: Color) {
    NCard(radius = 18.dp) {
        Column(Modifier.padding(16.dp)) {
            if (words.isEmpty()) {
                Text("无", style = MaterialTheme.typography.bodyMedium, color = Ink400)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    words.forEach { w ->
                        Text(
                            w,
                            style = MaterialTheme.typography.labelMedium,
                            color = tone,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(toneSoft)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RegexCard(items: List<Pair<String, String>>, emptyText: String) {
    NCard(radius = 18.dp) {
        if (items.isEmpty()) {
            EmptyLine(emptyText)
        } else {
            items.forEachIndexed { index, (title, regex) ->
                if (index > 0) GroupDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = Ink500)
                    Text(
                        regex,
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink900,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ink500, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Ink900, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Ink400,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun BackButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .plainClickable(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

private fun copyJson(context: Context, raw: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("nairo_match_rules", raw))
    Toast.makeText(context, "已复制规则 JSON", Toast.LENGTH_SHORT).show()
}
