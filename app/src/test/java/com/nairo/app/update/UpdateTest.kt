package com.nairo.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    private val json = """
        {"schema":1,"package":"com.nairo.app","version_name":"1.3.0","version_code":18,"min_sdk":24,
         "tag":"v1.3.0","published_at":"2026-11-19T12:00:00Z","notes":"新增检查更新","force":false,
         "mirrors":["https://gh-proxy.com"],
         "apk":{"name":"nairo-app-1.3.0-release.apk","size":1234,"sha256":"ABCDEF",
                "url":"https://github.com/iiileo/nairo-android/releases/download/v1.3.0/nairo-app-1.3.0-release.apk"}}
    """.trimIndent()

    @Test
    fun parseLatestJson() {
        val info = UpdateInfo.parse(json)
        assertEquals("1.3.0", info.versionName)
        assertEquals(18L, info.versionCode)
        assertEquals(1234L, info.apkSize)
        assertEquals("abcdef", info.apkSha256)
        assertEquals(listOf("https://gh-proxy.com/"), info.mirrors)
        assertTrue(info.isNewerThan(17))
        assertFalse(info.isNewerThan(18))
        assertTrue(info.supports(24))
        assertFalse(info.supports(23))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonHttpsApk() {
        UpdateInfo.parse(json.replace("https://github.com", "http://github.com"))
    }

    @Test
    fun apkUrlsOrderFollowsMirrorSwitch() {
        val info = UpdateInfo.parse(json)
        val on = UpdateSources.apkUrls(info, useMirror = true)
        assertEquals("https://gh-proxy.com/${info.apkUrl}", on.first())
        assertEquals(info.apkUrl, on.last())
        val off = UpdateSources.apkUrls(info, useMirror = false)
        assertEquals(info.apkUrl, off.first())
    }

    @Test
    fun manifestUrlsHaveFallbacks() {
        val on = UpdateSources.manifestUrls(useMirror = true)
        assertEquals(4, on.size)
        assertTrue(on.first().startsWith("https://gh-proxy.com/https://github.com/"))
        val off = UpdateSources.manifestUrls(useMirror = false)
        assertTrue(off.first().startsWith("https://github.com/"))
    }
}
