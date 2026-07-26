package com.creationreadingassistant.feature.reader.layout

/**
 * 页面命中测试：坐标 ↔ 字符偏移，以及区间 → 矩形。
 *
 * Canvas 方案下没有 `SelectionContainer` 可用（`SelectionRegistrar` / `Selectable`
 * 都是 internal，外部拿不到），选区、高亮、搜索命中、TTS 逐句高亮四件事的矩形
 * 全都要自己算。好在 [LayoutLine.clusterX] 已经把逐簇 x 坐标备好了，
 * 这里只是查表 —— 这也是自研断行的收益之一。
 *
 * 纯函数、零 Android 依赖，可直接 JVM 单测。
 */
object PageHitTest {

    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float)

    /**
     * 点 (x, y) 落在哪个字符上，返回章内字符偏移。
     *
     * 点在行间空白处时归入最近的一行；点在行首之前归行首、行末之后归行末，
     * 这样拖动选区把手到边缘不会「掉出去」。
     */
    fun offsetAt(
        lines: List<LayoutLine>,
        lineTops: FloatArray,
        lineHeight: Float,
        x: Float,
        y: Float,
    ): Int {
        if (lines.isEmpty()) return 0

        // 找到 y 所在的行；超出范围时钳到首行/末行
        var li = 0
        for (i in lines.indices) {
            val top = lineTops.getOrElse(i) { 0f }
            if (y >= top) li = i else break
        }
        val line = lines[li]
        val top = lineTops.getOrElse(li) { 0f }
        if (y > top + lineHeight && li == lines.lastIndex) return line.endInText

        return offsetInLine(line, x)
    }

    /** 在给定行内按 x 找字符偏移。取最近的簇边界，而不是簇的起点。 */
    fun offsetInLine(line: LayoutLine, x: Float): Int {
        val n = line.clusterCount
        if (n == 0) return line.startInText
        if (x <= line.clusterX[0]) return line.startInText
        if (x >= line.clusterX[n]) return line.endInText

        for (i in 0 until n) {
            val a = line.clusterX[i]
            val b = line.clusterX[i + 1]
            if (x in a..b) {
                // 落在簇的右半就算下一个字，符合选区把手的直觉
                val mid = (a + b) / 2f
                val cluster = if (x < mid) i else i + 1
                return clusterToOffset(line, cluster)
            }
        }
        return line.endInText
    }

    /**
     * 字符区间 [startOffset, endOffset) 在本页占据的矩形集合。
     *
     * 跨行时每行一个矩形。高亮、选区、搜索命中、TTS 当前句四类叠加层共用它，
     * 保证四者的视觉边界完全一致。
     */
    fun rectsForRange(
        lines: List<LayoutLine>,
        lineTops: FloatArray,
        lineHeight: Float,
        startOffset: Int,
        endOffset: Int,
    ): List<Rect> {
        if (endOffset <= startOffset) return emptyList()
        val out = ArrayList<Rect>()

        lines.forEachIndexed { i, line ->
            val s = maxOf(startOffset, line.startInText)
            val e = minOf(endOffset, line.endInText)
            if (s >= e) return@forEachIndexed

            val left = xForOffset(line, s)
            val right = xForOffset(line, e)
            val top = lineTops.getOrElse(i) { 0f }
            if (right > left) {
                out.add(Rect(left, top, right, top + lineHeight))
            }
        }
        return out
    }

    /** 字符偏移在行内的 x 坐标。偏移不在本行时钳到行首/行末。 */
    fun xForOffset(line: LayoutLine, offset: Int): Float {
        if (offset <= line.startInText) return line.clusterX[0]
        if (offset >= line.endInText) return line.clusterX[line.clusterCount]
        // 线性扫描：一行最多几十簇，二分的收益不值得多写的分支
        for (i in 0 until line.clusterCount) {
            val a = clusterToOffset(line, i)
            val b = clusterToOffset(line, i + 1)
            if (offset in a until b) return line.clusterX[i]
            if (offset == b) return line.clusterX[i + 1]
        }
        return line.clusterX[line.clusterCount]
    }

    /**
     * 行内簇序号 → 章内字符偏移。直接查 [LayoutLine.clusterStarts]。
     *
     * 早先这里是「按簇数等分首末偏移」的线性插值。簇与字符不是一一对应
     * （代理对 𠮷、ZWJ emoji 家族、组合字都是一簇多字符），插值出来的偏移
     * 会落在字符中间，含这类字符的行整行点错。
     */
    private fun clusterToOffset(line: LayoutLine, cluster: Int): Int {
        val n = line.clusterCount
        if (n == 0) return line.startInText
        return line.clusterStarts[cluster.coerceIn(0, n)]
    }
}
