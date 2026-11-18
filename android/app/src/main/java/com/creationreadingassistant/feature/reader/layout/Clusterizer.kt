package com.creationreadingassistant.feature.reader.layout

/**
 * 把文本切成字素簇并测宽。
 *
 * 为什么必须按簇而不是按字符：代理对（𠮷 U+20BB7）、变体选择符、ZWJ emoji 家族
 * （👨‍👩‍👧‍👦）在 UTF-16 里都是多个 code unit，按字符切会切出半个字，
 * 绘制出来是乱码方块，选区也会错位。
 *
 * 归并规则：**宽度为 0 的后续 code unit 并入前一簇**，同时排除真零宽字符
 * （U+200B/200C/200D/FE0F 本身宽度就是 0，但它们是簇的组成部分而非独立簇）。
 * 这条规则来自对 legado `measureTextSplit` 的行为观察，但实现是独立写的。
 */
object Clusterizer {

    private fun isZeroWidthJoinerLike(c: Char): Boolean = when (c.code) {
        0x200B, 0x200C, 0x200D, 0xFEFF, 0xFE0F, 0xFE0E -> true
        else -> false
    }

    fun of(text: String, ruler: TextRuler, checkActive: () -> Unit = {}): Clusters {
        if (text.isEmpty()) {
            return Clusters(text, intArrayOf(0), FloatArray(0), IntArray(0))
        }

        val widths = FloatArray(text.length)
        ruler.getTextWidths(text, widths, checkActive)
        checkActive()

        // 字符数是簇数上界，直接写基本类型数组，避免逐字符装箱和列表复制。
        val starts = IntArray(text.length + 1)
        val adv = FloatArray(text.length)
        val kls = IntArray(text.length)
        var count = 0

        var i = 0
        var nextCheck = 0
        while (i < text.length) {
            if (i >= nextCheck) {
                checkActive()
                nextCheck = i + 256
            }
            val start = i
            var w = widths[i]
            // 按码位取类别：代理对（𠮷 U+20BB7）单看高位半个不是汉字，
            // 用 text[i] 会把扩展汉字判成 OTHER。
            val repCodePoint = text.codePointAt(i)

            // 代理对：整体成簇
            if (Character.isHighSurrogate(text[i]) && i + 1 < text.length &&
                Character.isLowSurrogate(text[i + 1])
            ) {
                w += widths[i + 1]
                i += 2
            } else {
                i++
            }

            // 后续宽度为 0（组合字、变体选择符、ZWJ 及其后继）并入本簇
            while (i < text.length) {
                if (i >= nextCheck) {
                    checkActive()
                    nextCheck = i + 256
                }
                val c = text[i]
                val isJoiner = isZeroWidthJoinerLike(c)
                if (widths[i] == 0f || isJoiner) {
                    w += widths[i]
                    i++
                    // ZWJ 之后的那个字素也属于同一簇（emoji 家族）
                    if (c.code == 0x200D && i < text.length) {
                        if (Character.isHighSurrogate(text[i]) && i + 1 < text.length &&
                            Character.isLowSurrogate(text[i + 1])
                        ) {
                            w += widths[i] + widths[i + 1]
                            i += 2
                        } else {
                            w += widths[i]
                            i++
                        }
                    }
                } else {
                    break
                }
            }

            starts[count] = start
            adv[count] = w
            kls[count] = CharClass.classifyCodePoint(repCodePoint)
            count++
        }
        starts[count] = text.length

        return Clusters(
            text = text,
            startInText = if (count == text.length) starts else starts.copyOf(count + 1),
            advance = if (count == text.length) adv else adv.copyOf(count),
            klass = if (count == text.length) kls else kls.copyOf(count),
        )
    }
}
