package com.creationreadingassistant.feature.reader.edit

/**
 * 章节正文编辑产生的**一处**修改块（章节局部坐标）。
 *
 * 与 `RuleCommand.SaveSingleCorrection` 一一对应：
 * - [localStart] / [localEnd]（半开）→ 加上章节在全书中的 source 起点即得锚点区间；
 * - [originalText] → `findText`（保存时的原样文本，应用时用于内容校验）；
 * - [replacementText] → `replaceText`（空串即删除）。
 */
data class ChapterEditBlock(
    val localStart: Int,
    val localEnd: Int,
    val originalText: String,
    val replacementText: String,
)

/** 一次差异计算的结果。 */
sealed interface ChapterDiffResult {
    /** 正文没有被改动。 */
    data object NoChange : ChapterDiffResult

    /** 改动块列表（至少一块，且每块都满足纠错的落库前提）。 */
    data class Blocks(val blocks: List<ChapterEditBlock>) : ChapterDiffResult

    /**
     * 改动过于零碎（块数超过 [ChapterTextDiff.MAX_BLOCKS]）。
     * 不做成几百条纠错记录 —— 那种情况应改用替换规则，或让用户重选编辑范围。
     */
    data class TooLarge(val blockCount: Int) : ChapterDiffResult
}

/**
 * 章节编辑 → 纠错块的差异引擎（纯 JVM，无 Android 依赖）。
 *
 * 算法：按行（保留换行符）切段 → 段级 LCS 对齐 → 对齐后的空隙即改动块 →
 * 每块再做字符级公共前后缀裁剪，把替换区间压到最小。
 *
 * **为什么必须归一化纯插入**：纠错表（`reader_text_corrections`）的落库前提是
 * `sourceEnd > sourceStart` 且 `findText` 非空白（见 `RulesRepository.saveSingleCorrection`）。
 * 而纯插入的原文区间是空的、纯换行锚点的 `findText` 会被判为空白，两者都会被拒绝。
 * 因此这里把插入**扩成 1 个字符的替换**（`C` → `C+新增`），语义完全等价，
 * 却能落库；找不到任何可扩展的非空白锚点时干脆丢弃该块（宁可不做，也不留脏数据）。
 */
object ChapterTextDiff {

    /** 差异块上限：超过就判定为"改动过大"，由上层提示改用替换规则。 */
    const val MAX_BLOCKS = 200

    fun diff(original: String, edited: String): ChapterDiffResult {
        if (original == edited) return ChapterDiffResult.NoChange
        if (original.isEmpty()) return ChapterDiffResult.NoChange

        val a = segments(original)
        val b = segments(edited)
        val raw = rawBlocks(a, b, original)
        val normalized = raw.mapNotNull { normalizeInsertion(it, original) }
            .filter { it.originalText != it.replacementText }
        if (normalized.isEmpty()) return ChapterDiffResult.NoChange
        if (normalized.size > MAX_BLOCKS) return ChapterDiffResult.TooLarge(normalized.size)
        return ChapterDiffResult.Blocks(normalized)
    }

    // ── 切段：每段带上自己的换行符，拼接后可还原原文 ──────────────────────

    private fun segments(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        var start = 0
        for (index in text.indices) {
            if (text[index] == '\n') {
                out += text.substring(start, index + 1)
                start = index + 1
            }
        }
        if (start < text.length) out += text.substring(start)
        return out
    }

    /** 段起点 → 章节局部字符偏移。 */
    private fun offsetOf(segments: List<String>, segmentIndex: Int): Int {
        var sum = 0
        for (i in 0 until segmentIndex.coerceAtMost(segments.size)) sum += segments[i].length
        return sum
    }

    // ── 段级 LCS 对齐 → 空隙即改动块 ──────────────────────────────────────

    private fun rawBlocks(
        a: List<String>,
        b: List<String>,
        original: String,
    ): List<ChapterEditBlock> {
        val n = a.size
        val m = b.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val matches = ArrayList<IntArray>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> {
                    matches += intArrayOf(i, j)
                    i++
                    j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }

        val blocks = ArrayList<ChapterEditBlock>()
        var ai = 0
        var bi = 0
        for (match in matches) {
            if (match[0] > ai || match[1] > bi) {
                blocks += buildBlock(a, b, ai, match[0], bi, match[1], original)
            }
            ai = match[0] + 1
            bi = match[1] + 1
        }
        if (ai < n || bi < m) {
            blocks += buildBlock(a, b, ai, n, bi, m, original)
        }
        return blocks
    }

    private fun buildBlock(
        a: List<String>,
        b: List<String>,
        aStart: Int,
        aEnd: Int,
        bStart: Int,
        bEnd: Int,
        original: String,
    ): ChapterEditBlock {
        val localStart = offsetOf(a, aStart)
        val localEnd = offsetOf(a, aEnd).coerceAtMost(original.length)
        val originalText = a.subList(aStart, aEnd).joinToString("")
        val replacementText = b.subList(bStart, bEnd).joinToString("")
        // 字符级公共前后缀裁剪：替换区间压到最小，语义等价。
        val prefix = commonPrefix(originalText, replacementText)
        val suffix = commonSuffix(originalText, replacementText, prefix)
        val trimmedOriginal = originalText.substring(prefix, originalText.length - suffix)
        val trimmedReplacement = replacementText.substring(prefix, replacementText.length - suffix)
        return ChapterEditBlock(
            localStart = localStart + prefix,
            localEnd = localEnd - suffix,
            originalText = trimmedOriginal,
            replacementText = trimmedReplacement,
        )
    }

    private fun commonPrefix(x: String, y: String): Int {
        var p = 0
        val limit = minOf(x.length, y.length)
        while (p < limit && x[p] == y[p]) p++
        return p
    }

    private fun commonSuffix(x: String, y: String, prefix: Int): Int {
        var s = 0
        val limit = minOf(x.length, y.length) - prefix
        while (s < limit && x[x.length - 1 - s] == y[y.length - 1 - s]) s++
        return s
    }

    /**
     * 把"纯插入"扩成 1 个字符的替换，让它能落进纠错表。
     *
     * 左锚点优先（把前一个字符纳入替换）；行首或左锚点本身是空白（如换行）时改试右锚点。
     * 两侧都取不到非空白锚点时返回 null —— 该块被丢弃，不产出无法落库的数据。
     */
    private fun normalizeInsertion(block: ChapterEditBlock, original: String): ChapterEditBlock? {
        if (block.localEnd > block.localStart) return block
        val position = block.localStart

        if (position > 0) {
            val anchor = original.substring(position - 1, position)
            if (!anchor.isBlank()) {
                return block.copy(
                    localStart = position - 1,
                    localEnd = position,
                    originalText = anchor,
                    replacementText = anchor + block.replacementText,
                )
            }
        }
        if (position < original.length) {
            val anchor = original.substring(position, position + 1)
            if (!anchor.isBlank()) {
                return block.copy(
                    localStart = position,
                    localEnd = position + 1,
                    originalText = anchor,
                    replacementText = block.replacementText + anchor,
                )
            }
        }
        return null
    }
}
