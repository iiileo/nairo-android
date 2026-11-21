package com.nairo.app.util

import com.nairo.app.match.AppMatchRules
import com.nairo.app.match.MatchRulesStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按服务端下发的 [AppMatchRules] 解析收款通知；无缓存时用内置默认规则。
 */
@Singleton
class PaymentHintParser @Inject constructor(
    private val store: MatchRulesStore,
) {
    fun isWatchPackage(packageName: String): Boolean =
        Engine.isWatchPackage(store.current(), packageName)

    fun shouldCapture(packageName: String, title: String, body: String, ticker: String): Boolean =
        Engine.shouldCapture(store.current(), packageName, title, body, ticker)

    fun channelHint(packageName: String, title: String, body: String): String =
        Engine.channelHint(store.current(), packageName, title, body)

    fun amountHint(title: String, body: String, ticker: String): String? =
        Engine.amountHint(store.current(), title, body, ticker)

    /** 单测 / 无 DI 场景 */
    object Engine {
        fun isWatchPackage(rules: AppMatchRules, packageName: String): Boolean =
            rules.watchPackages.values.any { it == packageName }

        fun isIncomingPayment(rules: AppMatchRules, title: String, body: String, ticker: String): Boolean {
            val blob = listOf(title, body, ticker).joinToString(" ")
            if (blob.isBlank()) return false
            if (rules.ignoreKeywords.any { blob.contains(it) }) return false
            if (rules.receiveKeywords.none { blob.contains(it) }) return false
            val hasOutbound = rules.outboundKeywords.any { blob.contains(it) }
            if (hasOutbound && !blob.contains("收款") && !blob.contains("到账") && !blob.contains("收钱")) {
                return false
            }
            return true
        }

        fun shouldCapture(
            rules: AppMatchRules,
            packageName: String,
            title: String,
            body: String,
            ticker: String,
        ): Boolean = isWatchPackage(rules, packageName) && isIncomingPayment(rules, title, body, ticker)

        fun channelHint(rules: AppMatchRules, packageName: String, title: String, body: String): String {
            val blob = "$packageName $title $body"
            for ((channel, pkg) in rules.watchPackages) {
                if (packageName == pkg) return channel
            }
            return when {
                blob.contains("微信") -> "wechat"
                blob.contains("支付宝") -> "alipay"
                else -> "other"
            }
        }

        fun amountHint(rules: AppMatchRules, title: String, body: String, ticker: String): String? {
            var blob = listOf(title, body, ticker).joinToString(" ")
            for (pat in rules.sanitizePatterns) {
                runCatching {
                    blob = Regex(pat).replace(blob, " ")
                }
            }
            for (p in rules.amountPatterns) {
                val re = runCatching { Regex(p.regex) }.getOrNull() ?: continue
                val m = re.find(blob) ?: continue
                val g = p.group.coerceAtLeast(1)
                val v = m.groupValues.getOrNull(g)?.trim().orEmpty()
                if (v.isNotEmpty()) return v
            }
            return null
        }
    }

    object Packages {
        const val WECHAT = "com.tencent.mm"
        const val ALIPAY = "com.eg.android.AlipayGphone"
    }

    companion object {
        /** 兼容旧测试调用：使用默认规则 */
        fun shouldCapture(packageName: String, title: String, body: String, ticker: String): Boolean =
            Engine.shouldCapture(AppMatchRules.DEFAULT, packageName, title, body, ticker)

        fun amountHint(title: String, body: String, ticker: String): String? =
            Engine.amountHint(AppMatchRules.DEFAULT, title, body, ticker)
    }
}
