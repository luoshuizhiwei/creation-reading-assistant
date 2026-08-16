package com.creationreadingassistant.feature.reader.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 替换规则产出的 display↔source 双向偏移映射。
 *
 * 约定的不变量（全部在 [assertInvariants] 里逐点断言）：
 * - 双向单调不减；
 * - round trip floor：toSource(toDisplay(s)) <= s 且 toDisplay(toSource(d)) <= d；
 * - 边界：toSource(0)=0 / toDisplay(0)=0 / 两端精确到全长；
 * - 越界 clamp 到 [0, len]，负值按 0。
 */
class TextOffsetMapTest {

    private fun assertInvariants(map: TextOffsetMap, sourceLength: Int, displayLength: Int) {
        assertEquals(displayLength, map.displayLength)
        assertEquals(sourceLength, map.sourceLength)

        var prevSource = -1
        for (d in 0..displayLength) {
            val s = map.toSource(d)
            assertTrue("toSource 单调不减 d=$d", s >= prevSource)
            prevSource = s
            assertTrue("toSource 不越界 d=$d s=$s", s in 0..sourceLength)
            assertTrue("display round trip floor d=$d", map.toDisplay(s) <= d)
        }

        var prevDisplay = -1
        for (s in 0..sourceLength) {
            val d = map.toDisplay(s)
            assertTrue("toDisplay 单调不减 s=$s", d >= prevDisplay)
            prevDisplay = d
            assertTrue("toDisplay 不越界 s=$s d=$d", d in 0..displayLength)
            assertTrue("source round trip floor s=$s", map.toSource(d) <= s)
        }

        assertEquals(0, map.toSource(0))
        assertEquals(0, map.toDisplay(0))
        // 末端删除时 toSource(displayLength) 按 floor 回到删除起点（< sourceLength），
        // 其余情况精确等于 sourceLength；统一断言在界内。
        assertTrue("toSource(displayLength) 在界内", map.toSource(displayLength) in 0..sourceLength)
        assertEquals(displayLength, map.toDisplay(sourceLength))

        assertEquals(0, map.toSource(-5))
        assertEquals(0, map.toDisplay(-5))
        // 越界输入 clamp 到末端后按末端语义（末端删除时同样是 floor 起点）。
        assertEquals(map.toSource(displayLength), map.toSource(displayLength + 50))
        assertEquals(displayLength, map.toDisplay(sourceLength + 50))
    }

    @Test
    fun `identity map is exact both ways`() {
        val map = TextOffsetMap.of(listOf(0 to 0, 10 to 10), displayLength = 10, sourceLength = 10)
        for (i in 0..10) {
            assertEquals(i, map.toSource(i))
            assertEquals(i, map.toDisplay(i))
        }
        assertInvariants(map, 10, 10)
    }

    @Test
    fun `empty text identity`() {
        val map = TextOffsetMap.of(listOf(0 to 0), displayLength = 0, sourceLength = 0)
        assertEquals(0, map.toSource(0))
        assertEquals(0, map.toDisplay(0))
        assertInvariants(map, 0, 0)
    }

    @Test
    fun `deletion collapses source span to its display position`() {
        // source "abcXYZdef"(9) 删 XYZ[3,6) -> display "abcdef"(6)
        val map = TextOffsetMap.of(
            listOf(0 to 0, 3 to 3, 3 to 6, 6 to 9),
            displayLength = 6,
            sourceLength = 9,
        )
        // 删除区间内的源偏移折叠到 3（删除坍缩点）
        assertEquals(3, map.toDisplay(3))
        assertEquals(3, map.toDisplay(4))
        assertEquals(3, map.toDisplay(5))
        assertEquals(3, map.toDisplay(6))
        assertEquals(4, map.toDisplay(7))
        // 删除区间外的显示偏移精确映射
        assertEquals(7, map.toSource(4))
        assertEquals(8, map.toSource(5))
        // 删除坍缩点按 floor 回到删除起点（round trip floor）
        assertEquals(3, map.toSource(3))
        assertInvariants(map, 9, 6)
    }

    @Test
    fun `shorter replacement deletes the unmatched tail`() {
        // source "abcXYZdef"(9) XYZ[3,6)->XY -> display "abcXYdef"(8)
        // 点：(0,0) (3,3) (5,5) (5,6) (8,9) —— 未消费的 Z 是竖直跳变
        val map = TextOffsetMap.of(
            listOf(0 to 0, 3 to 3, 5 to 5, 5 to 6, 8 to 9),
            displayLength = 8,
            sourceLength = 9,
        )
        assertEquals(7, map.toSource(6)) // 显示 'e' -> 源 7
        assertEquals(5, map.toDisplay(5)) // 源 'Z' 起 -> 显示 5（替换末尾）
        assertEquals(5, map.toDisplay(6)) // 源 'Z' 末 -> 仍折叠在 5
        assertEquals(6, map.toDisplay(7)) // 源 'd' -> 显示 6
        assertInvariants(map, 9, 8)
    }

    @Test
    fun `longer replacement inserts a flat tail`() {
        // source "abcXYZdef"(9) XYZ[3,6)->XYZW -> display "abcXYZWdef"(10)
        // 点：(0,0) (3,3) (6,6) (7,6) (10,9)
        val map = TextOffsetMap.of(
            listOf(0 to 0, 3 to 3, 6 to 6, 7 to 6, 10 to 9),
            displayLength = 10,
            sourceLength = 9,
        )
        assertEquals(6, map.toSource(6)) // 插入字符 W(d=6) 平铺到匹配末尾
        assertEquals(6, map.toSource(7)) // 'd' 起点
        assertEquals(6, map.toDisplay(6)) // 源 6（匹配末）-> 显示 6
        assertInvariants(map, 9, 10)
    }

    @Test
    fun `multiple matches stay monotonic`() {
        // source "abXcdYef"(8) 删 X[2,3)、Y[5,6) -> display "abcdef"(6)
        val map = TextOffsetMap.of(
            listOf(0 to 0, 2 to 2, 2 to 3, 4 to 5, 4 to 6, 6 to 8),
            displayLength = 6,
            sourceLength = 8,
        )
        assertEquals(2, map.toDisplay(3)) // 源 'c' -> 显示 2
        assertEquals(3, map.toDisplay(4)) // 源 'd' -> 显示 3
        assertEquals(4, map.toDisplay(5)) // 源 'Y' -> 折叠到 4
        assertEquals(4, map.toDisplay(6)) // 源 'e' -> 显示 4
        assertInvariants(map, 8, 6)
    }

    @Test
    fun `match at start still floors at zero`() {
        // source "XYabc"(5) 删 XY[0,2) -> display "abc"(3)
        val map = TextOffsetMap.of(
            listOf(0 to 0, 0 to 2, 3 to 5),
            displayLength = 3,
            sourceLength = 5,
        )
        assertEquals(0, map.toSource(0))
        assertEquals(0, map.toDisplay(0))
        assertEquals(0, map.toDisplay(1)) // 源 1（已删）折叠到 0
        assertEquals(3, map.toSource(1)) // 显示 'b' -> 源 3
        assertInvariants(map, 5, 3)
    }

    @Test
    fun `match at end maps tail to collapse point`() {
        // source "abcXY"(5) 删 XY[3,5) -> display "abc"(3)
        val map = TextOffsetMap.of(
            listOf(0 to 0, 3 to 3, 3 to 5),
            displayLength = 3,
            sourceLength = 5,
        )
        assertEquals(3, map.toDisplay(4))
        assertEquals(3, map.toDisplay(5))
        assertEquals(3, map.toSource(3))
        assertInvariants(map, 5, 3)
    }

    @Test
    fun `adjacent deletions collapse onto the same display position`() {
        // source "aXYb"(4) 删 X[1,2)、Y[2,3) -> display "ab"(2)
        val map = TextOffsetMap.of(
            listOf(0 to 0, 1 to 1, 1 to 2, 1 to 3, 2 to 4),
            displayLength = 2,
            sourceLength = 4,
        )
        assertEquals(1, map.toDisplay(2))
        assertEquals(1, map.toDisplay(3))
        assertEquals(1, map.toSource(1))
        assertEquals(4, map.toSource(2)) // 显示末 -> 源末（末尾是保留文本，精确）
        assertInvariants(map, 4, 2)
    }

    @Test
    fun `points are sorted and duplicates merged`() {
        val map = TextOffsetMap.of(
            listOf(3 to 3, 6 to 9, 0 to 0, 3 to 6, 3 to 3, 6 to 9),
            displayLength = 6,
            sourceLength = 9,
        )
        assertEquals(3, map.toSource(3))
        assertEquals(9, map.toSource(6))
        assertInvariants(map, 9, 6)
    }

    @Test
    fun `chained maps preserve invariants across multiple rules`() {
        // rule1: "aXbYc" 删 X -> "abYc"；rule2: "abYc" 删 Y -> "abc"
        val map1 = TextOffsetMap.of(
            listOf(0 to 0, 1 to 1, 1 to 2, 4 to 5),
            displayLength = 4,
            sourceLength = 5,
        )
        val map2 = TextOffsetMap.of(
            listOf(0 to 0, 2 to 2, 2 to 3, 3 to 4),
            displayLength = 3,
            sourceLength = 4,
        )
        val chained = ChainedTextOffsetMap(listOf(map1, map2))
        assertEquals(3, chained.displayLength)
        assertEquals(5, chained.sourceLength)
        // 组合映射在边界处取 floor：最终显示 'b'(1) 落到源 1（X 起点）、
        // 最终显示 'c'(2) 落到源 3（Y 起点），round trip floor 保证不越过原位置。
        assertEquals(1, chained.toSource(1))
        assertEquals(3, chained.toSource(2))
        assertEquals(1, chained.toDisplay(2))
        assertEquals(2, chained.toDisplay(4))
        assertInvariants(chained, 5, 3)
    }

    @Test
    fun `identity chain behaves like identity`() {
        val map = ChainedTextOffsetMap(listOf(TextOffsetMap.identity(7)))
        for (i in 0..7) {
            assertEquals(i, map.toSource(i))
            assertEquals(i, map.toDisplay(i))
        }
        assertInvariants(map, 7, 7)
    }
}
