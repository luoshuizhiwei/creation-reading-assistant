package com.creationreadingassistant.feature.reader.rules

/**
 * 替换规则应用后的显示文本（display）↔ 原始文本（source）的单调双向偏移映射。
 *
 * 语义约定（由 [TextOffsetMapTest] 逐点断言）：
 * - 双向单调不减：display/source 偏移越大，映射结果不降；
 * - round trip floor：`toSource(toDisplay(s)) <= s` 且 `toDisplay(toSource(d)) <= d`；
 * - 边界：`toSource(0)=0`、`toDisplay(0)=0`，两端精确映射到各自全长；
 * - 越界：输入超出 [0, len] 时 clamp，负值按 0。
 *
 * 删除文本在 display 空间坍缩为一个点（[toDisplay] 返回坍缩位置），
 * 插入文本在 source 空间平铺到匹配末尾（[toSource] 对插入字符返回匹配末）。
 */
interface TextOffsetMap {
    val displayLength: Int
    val sourceLength: Int

    /** display 偏移 → source 偏移（floor）。 */
    fun toSource(displayOffset: Int): Int

    /** source 偏移 → display 偏移（floor）。 */
    fun toDisplay(sourceOffset: Int): Int

    companion object {
        /** 恒等映射（无规则命中时的兜底）。 */
        fun identity(length: Int): TextOffsetMap =
            LinearTextOffsetMap.normalized(listOf(0 to 0, length to length), length, length)

        /** 从控制点构建：自动排序、去重并补齐首尾端点。 */
        fun of(points: List<Pair<Int, Int>>, displayLength: Int, sourceLength: Int): TextOffsetMap =
            LinearTextOffsetMap.normalized(points, displayLength, sourceLength)
    }
}

/**
 * 分段线性映射：相邻控制点之间要么同增（slope 1）、要么 display 相等（删除坍缩）、
 * 要么 source 相等（插入平铺）。两个方向都用「严格小于」的 floor 二分，
 * 边界处自然取前一/后一语义，保证 round trip floor 与单调性。
 */
class LinearTextOffsetMap internal constructor(
    private val displayPoints: IntArray,
    private val sourcePoints: IntArray,
    override val displayLength: Int,
    override val sourceLength: Int,
) : TextOffsetMap {

    override fun toSource(displayOffset: Int): Int {
        val d = displayOffset.coerceIn(0, displayLength)
        val idx = floorIndex(displayPoints, d, strict = true)
        if (idx < 0) return 0
        val d0 = displayPoints[idx]
        val s0 = sourcePoints[idx]
        val s1 = sourcePoints[idx + 1]
        // 竖直跳变段（d0 == d1）不会被严格二分选中；水平段返回 s0；其余线性。
        return if (s0 == s1) s0 else s0 + (d - d0)
    }

    override fun toDisplay(sourceOffset: Int): Int {
        val s = sourceOffset.coerceIn(0, sourceLength)
        // 末端精确：源末偏移总映射到显示末尾（插入/删除在末尾时也一样）。
        if (s == sourceLength) return displayLength
        val idx = floorIndex(sourcePoints, s, strict = true)
        if (idx < 0) return 0
        val d0 = displayPoints[idx]
        val d1 = displayPoints[idx + 1]
        val s0 = sourcePoints[idx]
        // 竖直跳变 = 删除坍缩点；水平段（s0 == s1）不会被严格二分选中；其余线性。
        return if (d0 == d1) d0 else d0 + (s - s0)
    }

    private fun floorIndex(values: IntArray, target: Int, strict: Boolean): Int {
        var lo = 0
        var hi = values.lastIndex
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val ok = if (strict) values[mid] < target else values[mid] <= target
            if (ok) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }

    internal companion object {
        fun normalized(
            points: List<Pair<Int, Int>>,
            displayLength: Int,
            sourceLength: Int,
        ): LinearTextOffsetMap {
            val list = ArrayList<Pair<Int, Int>>(points.size + 2)
            list += 0 to 0
            list += points
            list += displayLength to sourceLength
            list.sortWith(compareBy({ it.first }, { it.second }))
            val ds = IntArray(list.size)
            val ss = IntArray(list.size)
            var n = 0
            for ((d, s) in list) {
                if (n > 0 && ds[n - 1] == d && ss[n - 1] == s) continue
                ds[n] = d
                ss[n] = s
                n++
            }
            return LinearTextOffsetMap(ds.copyOf(n), ss.copyOf(n), displayLength, sourceLength)
        }
    }
}

/**
 * 多规则按顺序应用的组合映射：source → map[0] → map[1] → … → display。
 * 单调、round trip floor、边界与 clamp 都随单段映射的组合保持。
 */
class ChainedTextOffsetMap(private val maps: List<TextOffsetMap>) : TextOffsetMap {
    override val displayLength: Int get() = maps.lastOrNull()?.displayLength ?: 0
    override val sourceLength: Int get() = maps.firstOrNull()?.sourceLength ?: 0

    override fun toSource(displayOffset: Int): Int {
        var acc = displayOffset
        for (i in maps.indices.reversed()) acc = maps[i].toSource(acc)
        return acc
    }

    override fun toDisplay(sourceOffset: Int): Int {
        var acc = sourceOffset
        for (map in maps) acc = map.toDisplay(acc)
        return acc
    }
}
