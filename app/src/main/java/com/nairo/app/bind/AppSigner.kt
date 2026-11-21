package com.nairo.app.bind

import java.security.MessageDigest

object AppSigner {
    fun md5Hex(raw: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** md5(callback_id + t + channel + price + nonce + app_secret) */
    fun pushSign(
        callbackId: String,
        t: Long,
        channel: String,
        price: String,
        nonce: String,
        secret: String,
    ): String = md5Hex("$callbackId$t$channel$price$nonce$secret")

    /** md5(callback_id + t + nonce + app_secret) */
    fun heartbeatSign(
        callbackId: String,
        t: Long,
        nonce: String,
        secret: String,
    ): String = md5Hex("$callbackId$t$nonce$secret")

    /** md5(callback_id + t + nonce + device_id + app_secret) —— 手机绑定/解绑 */
    fun bindSign(
        callbackId: String,
        t: Long,
        nonce: String,
        deviceId: String,
        secret: String,
    ): String = md5Hex("$callbackId$t$nonce$deviceId$secret")
}
