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
 * 纯 JVM 的假测宽器：CJK 与全角标点算 1em，其余算 0.5em。
 *
 * 它不是玩具 —— 中文正文本来就是等宽的，所以用它测出来的断行、禁则、挤压、
 * 两端对齐结果与真机高度一致，绝大多数排版逻辑的正确性都能在这里验证。
 */
class FakeTextRuler(
    override val fontSizePx: Float = 100f,
    override val typefaceKey: String = "fake",
) : TextRuler {

    override fun getTextWidths(text: String, out: FloatArray) {
        require(out.size >= text.length) { "out 长度不足：${out.size} < ${text.length}" }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (Character.isHighSurrogate(c) && i + 1 < text.length &&
                Character.isLowSurrogate(text[i + 1])
            ) {
                // 代理对：宽度记在首个 code unit 上，次个记 0，交给 Clusterizer 归并
                out[i] = fontSizePx
                out[i + 1] = 0f
                i += 2
                continue
            }
            out[i] = if (CharClass.isCjk(c) || CharClass.isFullWidthPunct(CharClass.classify(c))) {
                fontSizePx
            } else if (c == '​' || c == '‌' || c == '‍' || c == '️') {
                0f // 零宽字符
            } else {
                fontSizePx * 0.5f
            }
            i++
        }
    }
}
