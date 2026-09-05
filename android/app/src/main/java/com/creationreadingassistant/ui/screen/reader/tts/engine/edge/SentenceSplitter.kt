package com.creationreadingassistant.ui.screen.reader.tts.engine.edge

/**
 * 中文分句器：按标点切分为适合 TTS 合成的短句（单句 ≤ 300 字符）。
 *
 * 切分原则：
 * 1. 优先按句末标点（。！？!?.）切；
 * 2. 次按停顿标点（，,；;：:）切；
 * 3. 超过 300 字符仍无标点时按 200-280 字符就近切在逗号/顿号处，兜底硬切。
 *
 * 输出每句保留原句末标点，便于 TTS 停顿时长判断。
 */
internal object SentenceSplitter {

    private const val HARD_LIMIT = 300
    private const val SOFT_MIN = 200
    private const val SOFT_MAX = 280

    /** 句末强断点。 */
    private val STRONG_BREAK = charArrayOf('。', '！', '？', '!', '?', '.', '…')
    /** 句内弱断点（长句时在此切分）。 */
    private val WEAK_BREAK = charArrayOf('，', ',', '；', ';', '：', ':', '、')

    data class Sentence(
        /** 分句文本（含句末标点）。 */
        val text: String,
        /** 在原全文本中的 [start, endExclusive) 字符偏移。 */
        val start: Int,
        val endExclusive: Int,
    )

    fun split(fullText: String): List<Sentence> {
        if (fullText.isBlank()) return emptyList()

        val result = mutableListOf<Sentence>()
        var cursor = 0
        val n = fullText.length

        while (cursor < n) {
            // 跳过前导空白（保留到下一句起始，避免偏移错位：直接用 search 起点）
            val segStart = cursor

            // 1) 先找最近的强断点
            var strongAt = -1
            for (i in segStart until n) {
                if (fullText[i] in STRONG_BREAK) {
                    strongAt = i
                    break
                }
                if (i - segStart + 1 > HARD_LIMIT) break
            }

            val candidateEnd = if (strongAt >= 0) {
                strongAt + 1
            } else {
                // 2) 无强断点：在 SOFT_MIN..SOFT_MAX 区间内找弱断点；找不到就 HARD_LIMIT 兜底硬切
                val softSearchStart = (segStart + SOFT_MIN).coerceAtMost(n - 1)
                val softSearchEnd = (segStart + SOFT_MAX).coerceAtMost(n - 1)
                var weakAt = -1
                for (i in softSearchStart..softSearchEnd) {
                    if (fullText[i] in WEAK_BREAK) {
                        weakAt = i + 1
                        break
                    }
                }
                if (weakAt >= 0) weakAt else (segStart + HARD_LIMIT).coerceAtMost(n)
            }

            if (candidateEnd <= segStart) {
                // 极端情况：不足 1 字符，前进 1 字符避免死循环
                result += Sentence(fullText.substring(segStart, segStart + 1), segStart, segStart + 1)
                cursor = segStart + 1
            } else {
                result += Sentence(fullText.substring(segStart, candidateEnd), segStart, candidateEnd)
                cursor = candidateEnd
            }
        }
        return result
    }
}
