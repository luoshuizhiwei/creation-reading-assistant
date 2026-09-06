package com.creationreadingassistant.ui.screen.reader.tts.engine.edge

import java.security.MessageDigest

/**
 * Edge TTS 的 Sec-MS-GEC DRM 令牌（2024-06 起微软强制，缺失一律 403）。
 *
 * 算法与社区逆向实现（rany2/edge-tts 的 DRM.generate_sec_ms_gec）一致：
 * 1. 取当前 Unix 秒，向下对齐到 300 秒窗口；
 * 2. 加 Windows 纪元偏移 11644473600 后换算为 100ns ticks；
 * 3. 对 `"{ticks}{TRUSTED_CLIENT_TOKEN}"` 做 SHA-256，十六进制大写。
 *
 * 纯函数、无 Android 依赖，JVM 可测；[nowUnixSeconds] 单独抽出便于固定时钟测试。
 */
internal object EdgeTtsDrm {

    /** Edge 浏览器读 aloud 的公共客户端令牌（社区逆向的固定值，非账号凭据）。 */
    const val TRUSTED_CLIENT_TOKEN: String = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"

    /** 与令牌算法配套声明的 Edge 版本号（须与 UA 的 Edg/ 大版本一致，随 edge-tts 上游更新）。 */
    const val SEC_MS_GEC_VERSION: String = "1-143.0.3650.75"

    /** Chromium/Edge 大版本号（UA 与 Sec-CH 头共用）。 */
    const val CHROMIUM_MAJOR_VERSION: String = "143"

    /** Windows 纪元（1601-01-01）与 Unix 纪元的秒差。 */
    private const val WINDOWS_EPOCH_OFFSET_SECONDS = 11_644_473_600L

    /** 令牌时间窗口（秒）：跨窗口后令牌失效，需重新生成。 */
    private const val WINDOW_SECONDS = 300L

    /** 生成当前时刻的 Sec-MS-GEC 令牌。 */
    fun secMsGecToken(unixSeconds: Long): String {
        val windowed = unixSeconds - Math.floorMod(unixSeconds, WINDOW_SECONDS)
        val ticks = (windowed + WINDOWS_EPOCH_OFFSET_SECONDS) * 10_000_000L
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$ticks$TRUSTED_CLIENT_TOKEN".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02X".format(it) }
    }

    /** 当前 Unix 秒；独立函数便于测试注入固定时钟。 */
    fun nowUnixSeconds(): Long = System.currentTimeMillis() / 1000
}
