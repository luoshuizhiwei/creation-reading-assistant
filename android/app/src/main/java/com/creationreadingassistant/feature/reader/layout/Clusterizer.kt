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

    fun of(text: String, ruler: TextRuler): Clusters {
        if (text.isEmpty()) {
            return Clusters(text, intArrayOf(0), FloatArray(0), IntArray(0))
        }

        val widths = FloatArray(text.length)
        ruler.getTextWidths(text, widths)

        val starts = ArrayList<Int>(text.length + 1)
        val adv = ArrayList<Float>(text.length)
        val kls = ArrayList<Int>(text.length)

        var i = 0
        while (i < text.length) {
            val start = i
            var w = widths[i]
            var repChar = text[i]

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

            starts.add(start)
            adv.add(w)
            kls.add(CharClass.classify(repChar))
        }
        starts.add(text.length)

        return Clusters(
            text = text,
            startInText = starts.toIntArray(),
            advance = adv.toFloatArray(),
            klass = kls.toIntArray(),
        )
    }
}
