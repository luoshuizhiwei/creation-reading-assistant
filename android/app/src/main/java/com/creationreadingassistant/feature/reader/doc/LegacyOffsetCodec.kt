package com.creationreadingassistant.feature.reader.doc

/**
 * 冻结「旧版全局字符偏移」的计算公式。
 *
 * 为什么要单独抽出来：所有历史高亮、笔记、灵感的 `locator_json` 里存的都是按这套公式
 * 算出来的全书偏移。新分页内核要改用真实字符偏移，届时必须把旧值**精确**换算过去；
 * 而换算的前提是旧公式一个字节都不能记错。
 *
 * 所以这里的实现是**对既有行为的原样复刻，不是改进**。里面每一处看着别扭的地方
 * （比如每章要多加 1、estimate 至少取 1）都是既有语义，改动会让历史定位整体漂移。
 * 配套单测逐值锁死，任何修改都会立刻失败。
 *
 * 对应的原始位置（重构前）：
 * - `ReaderScreen.buildBookIndex`
 * - `ReaderScreen.computeBlockGlobalOffsets`
 * - `ReaderScreen.makeOffsetLocator` / `parseLocatorOffset`
 */
object LegacyOffsetCodec {

    /**
     * 各章在全书文本中的起始偏移。
     *
     * @param estimatedLengths 每章的 `EpubChapter.estimatedTextLength`
     *        （ZIP 解压后字节数，不是字符数 —— 中文书会偏大约 3 倍，这是既有语义）
     */
    fun chapterStartOffsets(estimatedLengths: List<Int>): List<Int> {
        val offsets = ArrayList<Int>(estimatedLengths.size)
        var len = 0L
        for (raw in estimatedLengths) {
            offsets.add(len.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            // estimate 至少取 1：空章节也要占一个位置，否则相邻两章偏移相同、无法区分
            val estimate = raw.coerceAtLeast(1)
            // +1 是章与章之间的分隔符位。漏掉它会让第 N 章之后的所有偏移少 N，
            // 历史高亮会整体前移。
            len = (len + estimate + 1L).coerceAtMost(Int.MAX_VALUE.toLong())
        }
        return offsets
    }

    /** 全书总字符数（同样是估算量纲）。 */
    fun totalChars(estimatedLengths: List<Int>): Int {
        var len = 0L
        for (raw in estimatedLengths) {
            len = (len + raw.coerceAtLeast(1) + 1L).coerceAtMost(Int.MAX_VALUE.toLong())
        }
        return len.toInt().coerceAtLeast(0)
    }

    /**
     * 各渲染块在全书文本中的偏移。图片块记为 -1（不占文本偏移）。
     *
     * @param blockTextLengths 与块顺序一一对应；图片位置传 null
     */
    fun blockOffsets(blockTextLengths: List<Int?>, chapterBase: Int): List<Int> {
        val out = ArrayList<Int>(blockTextLengths.size)
        var acc = chapterBase
        for (len in blockTextLengths) {
            if (len == null) {
                out.add(-1)
            } else {
                out.add(acc)
                // +1 对应块之间的 "\n"，与 text() 按 "\n" 拼接的语义一致
                acc += len + 1
            }
        }
        return out
    }

    /** 写入 locator_json。保持与旧版逐字节一致，旧版本读得懂。 */
    fun encodeLocator(offset: Int): String = """{"offset":$offset}"""

    /**
     * 从 locator_json 取偏移。
     *
     * 用正则而非 JSON 解析是既有行为，且这一点很有用：新版可以在同一份 JSON 里
     * 追加 v/ci/co 等字段做超集双写，旧版本仍能正常读到 offset，实现零迁移窗口。
     */
    fun decodeLocator(json: String?): Int? {
        if (json.isNullOrBlank()) return null
        val m = LOCATOR_OFFSET_REGEX.find(json) ?: return null
        return m.groupValues[1].toIntOrNull()
    }

    private val LOCATOR_OFFSET_REGEX = Regex("\"offset\"\\s*:\\s*(\\d+)")
}
