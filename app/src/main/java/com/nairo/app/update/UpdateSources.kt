package com.nairo.app.update

/**
 * 更新信息与安装包的下载地址。
 * 加速方式：在原始 GitHub 地址前加 gh-proxy 前缀（https://gh-proxy.com/https://github.com/...）。
 * Fork 发版时请同时修改 [REPO] 与 CI 中的 RELEASE_REPO。
 */
object UpdateSources {
    const val REPO = "iiileo/nairo-android"
    const val DEFAULT_MIRROR = "https://gh-proxy.com/"

    private const val MANIFEST_RELEASE =
        "https://github.com/$REPO/releases/latest/download/latest.json"
    private const val MANIFEST_RAW =
        "https://raw.githubusercontent.com/$REPO/main/latest.json"

    fun proxied(mirror: String, url: String): String = mirror.trimEnd('/') + "/" + url

    /** 检测更新的候选地址（按尝试顺序）。useMirror=true 时加速地址优先，否则直连优先、加速兜底。 */
    fun manifestUrls(useMirror: Boolean, mirror: String = DEFAULT_MIRROR): List<String> {
        val direct = listOf(MANIFEST_RELEASE, MANIFEST_RAW)
        val proxied = direct.map { proxied(mirror, it) }
        return interleave(direct, proxied, useMirror)
    }

    /** 安装包候选地址。mirrors 取 latest.json 下发值，缺省用 [DEFAULT_MIRROR]。 */
    fun apkUrls(info: UpdateInfo, useMirror: Boolean): List<String> {
        val mirrors = info.mirrors.ifEmpty { listOf(DEFAULT_MIRROR) }
        val direct = listOf(info.apkUrl)
        val proxied = mirrors.map { proxied(it, info.apkUrl) }
        return if (useMirror) proxied + direct else direct + proxied
    }

    private fun interleave(direct: List<String>, proxied: List<String>, useMirror: Boolean): List<String> {
        val out = ArrayList<String>(direct.size * 2)
        for (i in direct.indices) {
            if (useMirror) {
                out += proxied[i]; out += direct[i]
            } else {
                out += direct[i]; out += proxied[i]
            }
        }
        return out
    }
}
