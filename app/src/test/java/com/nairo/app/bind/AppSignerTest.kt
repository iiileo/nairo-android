package com.nairo.app.bind

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSignerTest {
    @Test
    fun pushSign_matchesGoSmokeConvention() {
        val sign = AppSigner.pushSign(
            callbackId = "abc123",
            t = 1700000000L,
            channel = "wechat",
            price = "0.01",
            nonce = "n1",
            secret = "secret",
        )
        assertEquals(AppSigner.md5Hex("abc1231700000000wechat0.01n1secret"), sign)
    }

    @Test
    fun heartbeatSign_matchesGoSmokeConvention() {
        val sign = AppSigner.heartbeatSign(
            callbackId = "abc123",
            t = 1700000000L,
            nonce = "hb1",
            secret = "secret",
        )
        assertEquals(AppSigner.md5Hex("abc1231700000000hb1secret"), sign)
    }
}
