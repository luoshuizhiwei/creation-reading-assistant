package com.creationreadingassistant.feature.reader.layout

/**
 * 测宽接缝。
 *
 * 排版内核只认这个接口，平台实现（基于 `TextPaint.getTextWidths()`）放在
 * `layout/android/`。这样内核可以在纯 JVM 下用 [FakeTextRuler] 跑单测 ——
 * 项目路径含中文导致 Gradle 跑不了测试，只有零 Android 依赖的代码才测得了。
 *
 * 为什么必须是 `getTextWidths()` 而不是 Compose 的 TextMeasurer：
 * Compose 不存在「不做布局就拿到字符宽度」的 API（`getBoundingBox` 首条指令即
 * `getLineForOffset`，必须先有 StaticLayout）。平台的 `getTextWidths()` 是唯一主路，
 * 也正是 legado 的做法。
 */
interface TextRuler {

    /** 字号（px）。1em 即等于它。 */
    val fontSizePx: Float

    /**
     * 逐字符推进宽度写入 [out]，长度必须等于 [text] 的长度（按 UTF-16 code unit）。
     * 代理对的第二个 code unit 宽度为 0，由 [Clusterizer] 归并进前一簇。
     */
    fun getTextWidths(text: String, out: FloatArray)

    /** 标识字体来源，进排版指纹。换系统字体后必须让缓存失效。 */
    val typefaceKey: String
}

/**
 * 纯 JVM 的假测宽器：按 Unicode East_Asian_Width 给宽度，全角 1em、半角 0.5em。
 *
 * 它不是玩具 —— 中文正文本来就是等宽的，所以用它测出来的断行、禁则、挤压、
 * 两端对齐结果与真机高度一致，绝大多数排版逻辑的正确性都能在这里验证。
 *
 * **刻意不复用 [CharClass] 判宽。** 早先是 `if (CharClass.isCjk(c) || isFullWidthPunct(...))`，
 * 于是分类表本身的错误对测试完全隐形：把半角的 `｡`(U+FF61) 误归进全角句读表后，
 * 这个 ruler 会顺着错误分类把它也测成 1em，测试自然测不出「行末削半会把它削成零宽」。
 * 宽度必须是独立于分类的第二信源。
 */
class FakeTextRuler(
    override val fontSizePx: Float = 100f,
    override val typefaceKey: String = "fake",
    /** 全角标点的宽度倍率。默认 1em；调小可模拟标点不满宽的真实字体。 */
    private val fullWidthPunctScale: Float = 1f,
) : TextRuler {

    override fun getTextWidths(text: String, out: FloatArray) {
        require(out.size >= text.length) { "out 长度不足：${out.size} < ${text.length}" }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (Character.isHighSurrogate(c) && i + 1 < text.length &&
                Character.isLowSurrogate(text[i + 1])
            ) {
                // 代理对（扩展汉字、emoji）：宽度记在首个 code unit 上，次个记 0
                out[i] = fontSizePx
                out[i + 1] = 0f
                i += 2
                continue
            }
            out[i] = widthOf(c.code)
            i++
        }
    }

    private fun widthOf(cp: Int): Float = when {
        cp == 0x200B || cp == 0x200C || cp == 0x200D || cp == 0xFEFF ||
            cp == 0xFE0F || cp == 0xFE0E -> 0f
        // 半角形式（含 ｡ ､ ｢ ｣ 与半角片假名）—— 明确是半角
        cp in 0xFF61..0xFF9F -> fontSizePx * 0.5f
        // 全角 ASCII 形式：其中的标点走标点倍率，字母数字仍是整宽
        cp in 0xFF01..0xFF60 -> if (isPunctCodePoint(cp)) fontSizePx * fullWidthPunctScale else fontSizePx
        // CJK 标点（。、「」《》等）
        cp in 0x3000..0x303F -> if (cp == 0x3000) fontSizePx else fontSizePx * fullWidthPunctScale
        cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF ||
            cp in 0x3040..0x30FF || cp in 0xF900..0xFAFF -> fontSizePx
        cp == 0x2014 || cp == 0x2015 || cp == 0x2026 || cp == 0x2025 -> fontSizePx // — ― … ‥
        cp == 0x201C || cp == 0x201D || cp == 0x2018 || cp == 0x2019 -> fontSizePx * fullWidthPunctScale
        else -> fontSizePx * 0.5f
    }

    /** 全角区里哪些码位是标点（而非字母数字）。 */
    private fun isPunctCodePoint(cp: Int): Boolean =
        cp !in 0xFF10..0xFF19 && cp !in 0xFF21..0xFF3A && cp !in 0xFF41..0xFF5A
}
