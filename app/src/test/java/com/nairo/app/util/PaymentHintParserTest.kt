package com.nairo.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentHintParserTest {
    @Test
    fun ignoreWechatYesterdaySummary() {
        val title = "微信收款助手"
        val body = "[20条]微信收款助手: 昨日收款3笔，总计0.30元 金额: ¥0.30 时间: 今天 12:10:43"
        assertFalse(
            PaymentHintParser.shouldCapture(PaymentHintParser.Packages.WECHAT, title, body, ""),
        )
    }

    @Test
    fun ignoreAlipayZeroYuanPromo() {
        val title = "支付宝"
        val body = "碰一下收钱码，今日0元领 限量发放，领完恢复原价！收钱安全有保障 金额: ¥0 时间: 今天 21:29:27"
        assertFalse(
            PaymentHintParser.shouldCapture(PaymentHintParser.Packages.ALIPAY, title, body, ""),
        )
    }

    @Test
    fun captureWechatIncoming() {
        assertTrue(
            PaymentHintParser.shouldCapture(
                PaymentHintParser.Packages.WECHAT,
                "微信收款助手",
                "个人收款码到账¥2.00",
                "",
            ),
        )
        assertEquals(
            "2.00",
            PaymentHintParser.amountHint("微信收款助手", "个人收款码到账¥2.00", ""),
        )
    }

    @Test
    fun preferWechatAmountEndingWithYuan() {
        // 文案里可能混有其它数字，优先解析「xx元」
        assertEquals(
            "10.50",
            PaymentHintParser.amountHint(
                "微信支付",
                "收款成功，订单号20241117001，到账10.50元，备注123456",
                "",
            ),
        )
        assertEquals(
            "3",
            PaymentHintParser.amountHint("微信收款助手", "成功收款3元", ""),
        )
    }

    @Test
    fun captureAlipayIncoming() {
        assertTrue(
            PaymentHintParser.shouldCapture(
                PaymentHintParser.Packages.ALIPAY,
                "支付宝",
                "成功收款￥18.5",
                "",
            ),
        )
        assertEquals(
            "18.5",
            PaymentHintParser.amountHint("支付宝", "成功收款￥18.5", ""),
        )
    }

    @Test
    fun ignoreOtherApps() {
        assertFalse(
            PaymentHintParser.shouldCapture(
                "com.android.systemui",
                "收款",
                "到账¥1.00",
                "",
            ),
        )
    }

    @Test
    fun ignoreWechatChatNoise() {
        assertFalse(
            PaymentHintParser.shouldCapture(
                PaymentHintParser.Packages.WECHAT,
                "张三",
                "晚上吃饭吗",
                "",
            ),
        )
    }

    @Test
    fun ignoreOutboundPayment() {
        assertFalse(
            PaymentHintParser.shouldCapture(
                PaymentHintParser.Packages.ALIPAY,
                "支付宝",
                "你已支付￥20.00",
                "",
            ),
        )
    }

    @Test
    fun noAmount() {
        assertNull(PaymentHintParser.amountHint("系统", "电量过低", ""))
    }

    @Test
    fun ignoreWechatStackedBadgeAsAmount() {
        // 「[2条]」是汇总角标，不能解析成 2 元
        assertNull(
            PaymentHintParser.amountHint(
                "微信收款助手 [2条]",
                "微信收款助手：经营账户提现到账",
                "",
            ),
        )
        assertFalse(
            PaymentHintParser.shouldCapture(
                PaymentHintParser.Packages.WECHAT,
                "微信收款助手 [2条]",
                "微信收款助手：经营账户提现到账",
                "",
            ),
        )
    }

    @Test
    fun ignoreWithdrawEvenWithYuan() {
        assertFalse(
            PaymentHintParser.shouldCapture(
                PaymentHintParser.Packages.WECHAT,
                "微信收款助手",
                "经营账户提现到账100.00元",
                "",
            ),
        )
    }

    @Test
    fun ignoreServiceNoise() {
        assertFalse(
            PaymentHintParser.shouldCapture(
                PaymentHintParser.Packages.WECHAT,
                "微信支付",
                "服务通知：你有一笔账单待确认",
                "",
            ),
        )
    }

    @Test
    fun stackedBadgeStillParseRealAmount() {
        assertEquals(
            "0.11",
            PaymentHintParser.amountHint(
                "微信收款助手 [2条]",
                "个人收款码到账¥0.11",
                "",
            ),
        )
    }
}
