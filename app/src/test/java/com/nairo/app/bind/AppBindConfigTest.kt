package com.nairo.app.bind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBindConfigTest {
    @Test
    fun parse_validBindJson() {
        val raw = """
            {
              "v": 1,
              "name": "desk",
              "base_url": "https://pay.example.com",
              "push_url": "https://pay.example.com/app/push/cb01",
              "heartbeat_url": "https://pay.example.com/app/heartbeat",
              "callback_id": "cb01",
              "secret": "s3cret",
              "channels": ["wechat", "alipay"]
            }
        """.trimIndent()
        val cfg = AppBindConfig.parse(raw)
        assertEquals(1, cfg.v)
        assertEquals("desk", cfg.name)
        assertEquals("cb01", cfg.callbackId)
        assertEquals("s3cret", cfg.secret)
        assertTrue(cfg.channels.containsAll(listOf("wechat", "alipay")))
        assertEquals("wss://pay.example.com/app/heartbeat/ws", cfg.heartbeatWsUrl)
    }

    @Test
    fun parse_usesExplicitWsUrl() {
        val raw = """
            {
              "v": 1,
              "push_url": "https://pay.example.com/app/push/cb01",
              "heartbeat_url": "https://pay.example.com/app/heartbeat",
              "heartbeat_ws_url": "wss://pay.example.com/app/heartbeat/ws",
              "callback_id": "cb01",
              "secret": "s3cret",
              "channels": ["wechat"]
            }
        """.trimIndent()
        assertEquals("wss://pay.example.com/app/heartbeat/ws", AppBindConfig.parse(raw).heartbeatWsUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parse_rejectsBadVersion() {
        AppBindConfig.parse("""{"v":2,"callback_id":"a","secret":"b","push_url":"https://x/a","heartbeat_url":"https://x/b","channels":["wechat"]}""")
    }
}
