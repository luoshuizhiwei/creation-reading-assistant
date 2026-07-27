package com.creationreadingassistant.feature.reader.locator

enum class AnchorConfidence(val storedValue: Int) {
    APPROXIMATE(0),
    RECOVERED(1),
    EXACT(2),
}

data class ResolvedAnchor(
    val chapterIndex: Int,
    val charOffset: Int,
    val confidence: AnchorConfidence,
)

/** 历史混合偏移 → ci/co 的代数恢复，并用实体文本做指纹/原文校验。 */
object AnchorResolver {

    fun resolve(
        locator: ReaderLocator,
        chapterStarts: List<Int>,
        chapterText: String,
        excerpt: String?,
    ): ResolvedAnchor {
        val chapter = locator.chapterIndex
            ?.takeIf { it in chapterStarts.indices }
            ?: chapterForLegacy(locator.legacyOffset ?: 0, chapterStarts)
        val base = chapterStarts.getOrElse(chapter) { 0 }
        val rawOffset = locator.charOffset
            ?: ((locator.legacyOffset ?: base) - base)
        val clamped = rawOffset.coerceIn(0, chapterText.length)
        val target = excerpt?.takeIf { it.isNotBlank() }

        if (target == null) return ResolvedAnchor(chapter, clamped, AnchorConfidence.EXACT)
        if (matchesAt(chapterText, clamped, target) &&
            (locator.excerptFingerprint == null ||
                locator.excerptFingerprint == LocatorCodec.fingerprint(target))
        ) {
            return ResolvedAnchor(chapter, clamped, AnchorConfidence.EXACT)
        }

        val nearStart = (clamped - 512).coerceAtLeast(0)
        val nearEnd = (clamped + 512 + target.length).coerceAtMost(chapterText.length)
        val near = chapterText.indexOf(target, startIndex = nearStart)
            .takeIf { it >= 0 && it + target.length <= nearEnd }
        if (near != null) return ResolvedAnchor(chapter, near, AnchorConfidence.RECOVERED)

        val anywhere = chapterText.indexOf(target)
        if (anywhere >= 0) return ResolvedAnchor(chapter, anywhere, AnchorConfidence.RECOVERED)
        return ResolvedAnchor(chapter, clamped, AnchorConfidence.APPROXIMATE)
    }

    private fun chapterForLegacy(offset: Int, starts: List<Int>): Int {
        if (starts.isEmpty()) return 0
        var lo = 0
        var hi = starts.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    private fun matchesAt(text: String, offset: Int, excerpt: String): Boolean =
        offset >= 0 && offset + excerpt.length <= text.length &&
            text.regionMatches(offset, excerpt, 0, excerpt.length)
}
