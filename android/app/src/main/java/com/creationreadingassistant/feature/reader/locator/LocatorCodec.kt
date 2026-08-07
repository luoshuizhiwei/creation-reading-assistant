package com.creationreadingassistant.feature.reader.locator

import java.security.MessageDigest

data class ReaderLocator(
    val legacyOffset: Int?,
    val chapterIndex: Int?,
    val charOffset: Int?,
    val excerptFingerprint: String?,
)

/**
 * locator v2 超集编码。始终保留旧版认识的 `offset`，新增 ci/co/fp；
 * 旧 App 的正则仍能跳到相同混合量纲位置，不需要同步迁移窗口。
 */
object LocatorCodec {
    private val offsetRegex = Regex(""""offset"\s*:\s*(\d+)""")
    private val chapterRegex = Regex(""""ci"\s*:\s*(\d+)""")
    private val charRegex = Regex(""""co"\s*:\s*(\d+)""")
    private val fingerprintRegex = Regex(""""fp"\s*:\s*"([0-9a-fA-F]+)"""")

    fun encode(
        legacyOffset: Int,
        chapterIndex: Int?,
        charOffset: Int?,
        excerpt: String?,
    ): String = buildString {
        append("""{"v":2,"offset":""")
        append(legacyOffset.coerceAtLeast(0))
        if (chapterIndex != null && charOffset != null) {
            append(""","ci":""")
            append(chapterIndex.coerceAtLeast(0))
            append(""","co":""")
            append(charOffset.coerceAtLeast(0))
        }
        val fp = excerpt?.takeIf { it.isNotBlank() }?.let(::fingerprint)
        if (fp != null) {
            append(""","fp":"""")
            append(fp)
            append('"')
        }
        append('}')
    }

    fun decode(json: String?): ReaderLocator? {
        if (json.isNullOrBlank()) return null
        val legacy = offsetRegex.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val chapter = chapterRegex.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val charOffset = charRegex.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val fp = fingerprintRegex.find(json)?.groupValues?.getOrNull(1)?.lowercase()
        if (legacy == null && (chapter == null || charOffset == null)) return null
        return ReaderLocator(legacy, chapter, charOffset, fp)
    }

    /**
     * 从进度 JSON 中提取嵌套的 v2 locator（"locator_v2":{...}），无则返回 null。
     * 只解码内层对象，避免顶部 legacy "offset" 干扰。
     */
    fun locatorFromProgress(json: String?): ReaderLocator? {
        if (json.isNullOrBlank()) return null
        val v2 = Regex(""""locator_v2"\s*:\s*(\{[^{}]*\})""")
            .find(json)?.groupValues?.getOrNull(1) ?: return null
        return decode(v2)
    }

    fun fingerprint(text: String): String {
        val normalized = normalizeExcerpt(text)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    fun normalizeExcerpt(text: String): String =
        text.trim().replace(Regex("""\s+"""), " ")
}
