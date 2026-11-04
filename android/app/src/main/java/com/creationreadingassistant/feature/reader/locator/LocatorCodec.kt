package com.creationreadingassistant.feature.reader.locator

import java.security.MessageDigest

data class ReaderLocator(
    val legacyOffset: Int?,
    val chapterIndex: Int?,
    val charOffset: Int?,
    val excerptFingerprint: String?,
)

/**
 * locator v2 超集编码。`legacyOffset`（v2 `"offset"`）语义是**全书字符偏移**，可选：
 * - 写入方能给出全书偏移时必须满足不变式 `offset == chapterStart[ci] + co`；
 * - 给不出（如导入时未解析正文、章切换落库时未知章起点表）必须传 null 省略该字段，
 *   读侧（[com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract]）
 *   会按 `chapterStart[ci] + co` 派生 —— 全书偏移要么真实、要么缺省，**绝不以章内偏移冒充**；
 *   伪造值正是 S12-① 的根因（导入 locator 被一致性校验拒绝、回源跳转全部失效）。
 *
 * 旧 App 的正则仍能从 ci/co 或顶层 legacy 字段跳到相同位置，不需要同步迁移窗口。
 */
object LocatorCodec {
    private val offsetRegex = Regex(""""offset"\s*:\s*(\d+)""")
    private val chapterRegex = Regex(""""ci"\s*:\s*(\d+)""")
    private val charRegex = Regex(""""co"\s*:\s*(\d+)""")
    private val fingerprintRegex = Regex(""""fp"\s*:\s*"([0-9a-fA-F]+)"""")

    fun encode(
        legacyOffset: Int?,
        chapterIndex: Int?,
        charOffset: Int?,
        excerpt: String?,
    ): String = buildString {
        append("""{"v":2""")
        if (legacyOffset != null) {
            append(""","offset":""")
            append(legacyOffset.coerceAtLeast(0))
        }
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
