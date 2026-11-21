package com.nairo.app.match

import org.json.JSONArray
import org.json.JSONObject

data class AmountPattern(
    val name: String,
    val regex: String,
    val group: Int = 1,
)

data class AppMatchRules(
    val version: Int,
    val updatedAt: String = "",
    val watchPackages: Map<String, String>,
    val receiveKeywords: List<String>,
    val outboundKeywords: List<String>,
    val ignoreKeywords: List<String>,
    val sanitizePatterns: List<String>,
    val amountPatterns: List<AmountPattern>,
) {
    companion object {
        val DEFAULT: AppMatchRules = AppMatchRules(
            version = 1,
            watchPackages = mapOf(
                "wechat" to "com.tencent.mm",
                "alipay" to "com.eg.android.AlipayGphone",
            ),
            receiveKeywords = listOf(
                "收款助手", "收款商业版", "成功收款", "已收款", "收款到账",
                "个人收款码", "二维码收款", "收钱码",
                "向你付款", "付款给你", "扫码向你付款", "通过扫码向你付款",
                "收到一笔", "收款", "到账", "收钱", "入账",
            ),
            outboundKeywords = listOf(
                "你已支付", "你支付了", "付款成功", "支付成功", "已付款",
                "转账成功", "向商家付款", "付款给",
            ),
            ignoreKeywords = listOf(
                "提现", "已打款", "转入零钱", "转入银行卡",
                "经营账户提现", "结算到账", "佣金到账",
                "服务通知", "账单", "对账单", "月度", "活动",
                "碰一下", "0元领", "限量发放", "恢复原价",
                "昨日收款", "今日收款", "笔，总计", "笔总计",
                "红包封面", "好友推荐", "新的朋友", "语音通话", "视频通话",
                "正在通话", "表情包", "拍了拍",
            ),
            sanitizePatterns = listOf(
                """\[\s*\d+\s*条\s*]""",
                """\d+\s*条消息""",
                """\d+\s*条新消息""",
            ),
            amountPatterns = listOf(
                AmountPattern(
                    name = "yuan",
                    regex = """(?<![0-9.])([0-9]+(?:\.[0-9]{1,2})?)\s*元(?!\s*条)""",
                    group = 1,
                ),
                AmountPattern(
                    name = "currency",
                    regex = """[¥￥]\s*([0-9]+(?:\.[0-9]{1,2})?)""",
                    group = 1,
                ),
                AmountPattern(
                    name = "plain",
                    regex = """(?:成功收款|已收款|收款到账|个人收款码到账|二维码收款|收钱码到账|到账|收钱|入账)[^0-9¥￥元\[\]]{0,8}([0-9]+(?:\.[0-9]{1,2})?)(?!\s*条)""",
                    group = 1,
                ),
            ),
        )

        fun fromJson(raw: String): AppMatchRules {
            val obj = JSONObject(raw)
            val watch = linkedMapOf<String, String>()
            obj.optJSONObject("watch_packages")?.let { wp ->
                val keys = wp.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    watch[k] = wp.optString(k).trim()
                }
            }
            val amounts = mutableListOf<AmountPattern>()
            val arr = obj.optJSONArray("amount_patterns") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val regex = p.optString("regex").trim()
                if (regex.isEmpty()) continue
                amounts += AmountPattern(
                    name = p.optString("name").ifBlank { "p${i + 1}" },
                    regex = regex,
                    group = p.optInt("group", 1).coerceAtLeast(1),
                )
            }
            val base = DEFAULT
            return AppMatchRules(
                version = obj.optInt("version", base.version).coerceAtLeast(1),
                updatedAt = obj.optString("updated_at").orEmpty(),
                watchPackages = watch.ifEmpty { base.watchPackages },
                receiveKeywords = readStringList(obj, "receive_keywords").ifEmpty { base.receiveKeywords },
                outboundKeywords = readStringList(obj, "outbound_keywords").ifEmpty { base.outboundKeywords },
                ignoreKeywords = readStringList(obj, "ignore_keywords").ifEmpty { base.ignoreKeywords },
                sanitizePatterns = readStringList(obj, "sanitize_patterns").ifEmpty { base.sanitizePatterns },
                amountPatterns = amounts.ifEmpty { base.amountPatterns },
            )
        }

        private fun readStringList(obj: JSONObject, key: String): List<String> {
            val arr = obj.optJSONArray(key) ?: return emptyList()
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val s = arr.optString(i).trim()
                if (s.isNotEmpty()) out += s
            }
            return out
        }
    }
}
