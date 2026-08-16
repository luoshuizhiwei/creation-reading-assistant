package com.creationreadingassistant.feature.reader.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-替换（slice 1）：ReplaceProjection 纯文本投影与 displaySliceForSourceRange。
 *
 * 语义约定（与 [TextOffsetMapTest] 的 floor map 一致）：
 * - sourceText 是持久化权威坐标，displayText 只用于渲染 / 搜索；
 * - 切片边界一律经 offsetMap 的 toDisplay，禁止 substring 猜偏移；
 * - 删除区间坍缩为空文本；插入尾随字符按 map floor 归属到后续 source 范围；
 * - 源区间划分窗口时，对应 display 区间恰好平铺整个 displayText（无重无漏）。
 */
class ReplaceProjectionTest {

    private fun replaceRule(
        id: String = "r1",
        pattern: String,
        replacement: String,
        enabled: Boolean = true,
        position: Int = 0,
    ) = ReplaceRule(
        id = id,
        name = id,
        pattern = pattern,
        replacement = replacement,
        enabled = enabled,
        position = position,
        scope = RuleScope.GLOBAL,
    )

    private fun assertMapInvariants(map: TextOffsetMap) {
        var prevSource = -1
        for (d in 0..map.displayLength) {
            val s = map.toSource(d)
            assertTrue("toSource 单调不减 d=$d", s >= prevSource)
            prevSource = s
            assertTrue("toSource 不越界 d=$d s=$s", s in 0..map.sourceLength)
            assertTrue("display round trip floor d=$d", map.toDisplay(s) <= d)
        }
        var prevDisplay = -1
        for (s in 0..map.sourceLength) {
            val d = map.toDisplay(s)
            assertTrue("toDisplay 单调不减 s=$s", d >= prevDisplay)
            prevDisplay = d
            assertTrue("toDisplay 不越界 s=$s d=$d", d in 0..map.displayLength)
            assertTrue("source round trip floor s=$s", map.toSource(d) <= s)
        }
        assertEquals(0, map.toSource(0))
        assertEquals(0, map.toDisplay(0))
        assertEquals(map.displayLength, map.toDisplay(map.sourceLength))
    }

    private fun assertSliceInvariants(slice: ReplaceSlice, projection: ReplaceProjection) {
        assertEquals(slice.sourceLength, slice.sourceEnd - slice.sourceStart)
        assertEquals(slice.displayLength, slice.displayEnd - slice.displayStart)
        assertEquals("切片文本长度等于局部 display 长度", slice.displayLength, slice.text.length)
        assertTrue(slice.sourceBase in 0..projection.offsetMap.sourceLength)
        assertTrue(slice.displayBase in 0..projection.offsetMap.displayLength)
        assertTrue(slice.sourceEnd in slice.sourceBase..projection.offsetMap.sourceLength)
        assertTrue(slice.displayEnd in slice.displayBase..projection.offsetMap.displayLength)

        var prevSource = -1
        for (x in 0..slice.displayLength) {
            val gs = slice.displayToSource(x)
            assertTrue("displayToSource 单调不减 x=$x", gs >= prevSource)
            prevSource = gs
            assertTrue("displayToSource 不越界 x=$x gs=$gs", gs in 0..projection.offsetMap.sourceLength)
            val localSource = (gs - slice.sourceBase).coerceIn(0, slice.sourceLength)
            val localDisplayBack = slice.sourceToDisplay(localSource) - slice.displayBase
            assertTrue("display round trip floor x=$x", localDisplayBack <= x)
        }
        var prevDisplay = -1
        for (y in 0..slice.sourceLength) {
            val gd = slice.sourceToDisplay(y)
            assertTrue("sourceToDisplay 单调不减 y=$y", gd >= prevDisplay)
            prevDisplay = gd
            assertTrue("sourceToDisplay 不越界 y=$y gd=$gd", gd in 0..projection.offsetMap.displayLength)
            val localDisplay = (gd - slice.displayBase).coerceIn(0, slice.displayLength)
            val sourceBack = slice.displayToSource(localDisplay)
            assertTrue("source round trip floor y=$y", sourceBack <= slice.sourceBase + y)
        }
    }

    @Test
    fun `no rules yields identity projection`() {
        val p = ReplaceProjection.project("hello world", emptyList(), bookId = "book-1")
        assertEquals("hello world", p.sourceText)
        assertEquals("hello world", p.displayText)
        assertEquals(0, p.hitCount)
        assertEquals(ReplaceProfile.EMPTY_KEY, p.profileKey)
        assertEquals(11, p.offsetMap.displayLength)
        assertEquals(11, p.offsetMap.sourceLength)
        for (i in 0..11) {
            assertEquals(i, p.offsetMap.toSource(i))
            assertEquals(i, p.offsetMap.toDisplay(i))
        }
        assertMapInvariants(p.offsetMap)
    }

    @Test
    fun `identity projection slices are exact substrings with identity maps`() {
        val p = ReplaceProjection.project("hello world", emptyList(), bookId = "book-1")
        val slice = p.displaySliceForSourceRange(1, 4)
        assertEquals("ell", slice.text)
        assertEquals(1, slice.sourceBase)
        assertEquals(1, slice.displayBase)
        assertEquals(3, slice.sourceLength)
        assertEquals(3, slice.displayLength)
        assertEquals(1, slice.displayToSource(0))
        assertEquals(4, slice.displayToSource(3))
        assertEquals(1, slice.sourceToDisplay(0))
        assertEquals(4, slice.sourceToDisplay(3))
        assertSliceInvariants(slice, p)
    }

    @Test
    fun `deletion projection slices the collapsed region to empty text`() {
        val rules = listOf(replaceRule("del", "XYZ", "", position = 0))
        val p = ReplaceProjection.project("abcXYZdef", rules, bookId = "book-1")
        assertEquals("abcdef", p.displayText)
        assertEquals(1, p.hitCount)
        assertEquals(ReplaceProfile.key("book-1", rules), p.profileKey)

        val deleted = p.displaySliceForSourceRange(3, 6)
        assertEquals("删除区间坍缩为空文本", "", deleted.text)
        assertEquals(0, deleted.displayLength)
        assertEquals("删除区间仍有 source 宽度，只是无 display 字符", 3, deleted.sourceLength)
        assertEquals(3, deleted.sourceBase)
        assertEquals(3, deleted.displayBase)

        val spanning = p.displaySliceForSourceRange(2, 7)
        assertEquals("cd", spanning.text)
        assertEquals(2, spanning.sourceBase)
        assertEquals(2, spanning.displayBase)
        assertEquals(5, spanning.sourceLength)
        assertEquals(2, spanning.displayLength)
        assertEquals(2, spanning.displayToSource(0))
        assertEquals(7, spanning.displayToSource(2))
        assertEquals(2, spanning.sourceToDisplay(0))
        assertEquals(4, spanning.sourceToDisplay(5))

        val after = p.displaySliceForSourceRange(6, 9)
        assertEquals("def", after.text)
        assertEquals(3, after.displayBase)
        assertEquals("坍缩点按 floor 回到删除起点", 3, after.displayToSource(0))

        assertEquals("abcdef", p.displaySliceForSourceRange(0, 9).text)
        assertSliceInvariants(deleted, p)
        assertSliceInvariants(spanning, p)
        assertSliceInvariants(after, p)
    }

    @Test
    fun `shorter replacement slice covers the replacement exactly`() {
        val p = ReplaceProjection.project(
            "abcXYZdef",
            listOf(replaceRule("short", "XYZ", "XY", position = 0)),
            bookId = "book-1",
        )
        assertEquals("abcXYdef", p.displayText)
        val match = p.displaySliceForSourceRange(3, 6)
        assertEquals("XY", match.text)
        assertEquals(3, match.displayBase)
        assertEquals(5, match.displayEnd)
        assertEquals(2, match.displayLength)
        val straddle = p.displaySliceForSourceRange(5, 7)
        assertEquals("源 'Zd' 投影为保留的 d", "d", straddle.text)
        assertEquals(5, straddle.displayBase)
        assertEquals(1, straddle.displayLength)
        assertSliceInvariants(match, p)
        assertSliceInvariants(straddle, p)
    }

    @Test
    fun `longer replacement keeps floor semantics at match end`() {
        val p = ReplaceProjection.project(
            "abcXYZdef",
            listOf(replaceRule("long", "XYZ", "XYZW", position = 0)),
            bookId = "book-1",
        )
        assertEquals("abcXYZWdef", p.displayText)
        // 匹配区间取对齐前缀 "XYZ"，插入余量 W 按 map floor 归属到后续 source 范围
        // （与 TextOffsetMapTest 一致），窗口拼接后仍是完整 display 文本。
        assertEquals("XYZ", p.displaySliceForSourceRange(3, 6).text)
        assertEquals("Wdef", p.displaySliceForSourceRange(6, 9).text)
        assertEquals("XYZWd", p.displaySliceForSourceRange(3, 7).text)
        assertEquals("abcXYZWdef", p.displaySliceForSourceRange(0, 9).text)
        assertSliceInvariants(p.displaySliceForSourceRange(3, 6), p)
        assertSliceInvariants(p.displaySliceForSourceRange(6, 9), p)
    }

    @Test
    fun `multi-rule projection applies in position order and chains maps`() {
        val a = replaceRule("a", "a", "X", position = 1)
        val b = replaceRule("b", "X", "Y", position = 2)
        val p = ReplaceProjection.project("abc", listOf(b, a), bookId = "book-1")
        assertEquals("Ybc", p.displayText)
        assertEquals(2, p.hitCount)
        assertEquals("Ybc", p.displaySliceForSourceRange(0, 3).text)
        assertEquals("bc", p.displaySliceForSourceRange(1, 3).text)
        assertMapInvariants(p.offsetMap)
        assertSliceInvariants(p.displaySliceForSourceRange(0, 3), p)

        val swapped = ReplaceProjection.project(
            "abc",
            listOf(b.copy(position = 1), a.copy(position = 2)),
            bookId = "book-1",
        )
        assertEquals("Xbc", swapped.displayText)
        assertEquals(1, swapped.hitCount)
        assertNotEquals("顺序变化后 key 必须失效", p.profileKey, swapped.profileKey)
    }

    @Test
    fun `sub range slices stay within parent bounds`() {
        val p = ReplaceProjection.project(
            "aXbXc",
            listOf(replaceRule("r", "X", "Y", position = 0)),
            bookId = "book-1",
        )
        assertEquals("aYbYc", p.displayText)
        val parent = p.displaySliceForSourceRange(0, 5)
        val sub = p.displaySliceForSourceRange(2, 4)
        assertEquals("aYbYc", parent.text)
        assertEquals("源 'bX' 投影为 'bY'", "bY", sub.text)
        assertTrue(sub.displayBase >= parent.displayBase)
        assertTrue(sub.displayEnd <= parent.displayEnd)
        assertTrue("子范围文本必须是父范围文本的子串", parent.text.contains(sub.text))
        assertSliceInvariants(sub, p)
    }

    @Test
    fun `empty and reversed source ranges yield empty slices`() {
        val p = ReplaceProjection.project(
            "abcXYZdef",
            listOf(replaceRule("del", "XYZ", "", position = 0)),
            bookId = "b",
        )
        val empty = p.displaySliceForSourceRange(3, 3)
        assertEquals("", empty.text)
        assertEquals(0, empty.displayLength)
        assertEquals(0, empty.sourceLength)
        assertEquals(3, empty.sourceBase)
        assertEquals(3, empty.displayBase)

        val reversed = p.displaySliceForSourceRange(7, 3)
        assertEquals("反转区间按空处理，锚定在 clamp 后的 end", "", reversed.text)
        assertEquals(3, reversed.sourceBase)
        assertEquals(3, reversed.displayBase)

        val clampedFull = p.displaySliceForSourceRange(-5, 100)
        assertEquals("abcdef", clampedFull.text)
        assertEquals(0, clampedFull.sourceBase)
        assertEquals(0, clampedFull.displayBase)

        val clampedEmpty = p.displaySliceForSourceRange(-3, -1)
        assertEquals("", clampedEmpty.text)
        assertEquals(0, clampedEmpty.sourceBase)
        assertSliceInvariants(empty, p)
        assertSliceInvariants(clampedFull, p)
    }

    @Test
    fun `empty source text projects to empty identity and empty slices`() {
        val p = ReplaceProjection.project(
            "",
            listOf(replaceRule("r", "a", "b", position = 0)),
            bookId = "book-1",
        )
        assertEquals("", p.sourceText)
        assertEquals("", p.displayText)
        assertEquals(0, p.hitCount)
        assertEquals(0, p.offsetMap.displayLength)
        assertEquals(0, p.offsetMap.sourceLength)
        val slice = p.displaySliceForSourceRange(0, 0)
        assertEquals("", slice.text)
        assertEquals(0, slice.sourceBase)
        assertEquals(0, slice.displayBase)
        assertMapInvariants(p.offsetMap)
        assertSliceInvariants(slice, p)
    }

    @Test
    fun `profile key is text independent and stable across repeated projections`() {
        val rules = listOf(replaceRule("r1", "广告", "", position = 0))
        val a = ReplaceProjection.project("广告A广告B", rules, bookId = "book-1")
        val b = ReplaceProjection.project("另一本书正文", rules, bookId = "book-1")
        assertEquals("AB", a.displayText)
        assertEquals(2, a.hitCount)
        assertEquals("key 与正文无关", a.profileKey, b.profileKey)
        assertEquals(ReplaceProfile.key("book-1", rules), a.profileKey)
    }

    @Test
    fun `slices tile the full display text across a source partition`() {
        val p = ReplaceProjection.project(
            "aXbYcZd",
            listOf(
                replaceRule("r1", "X", "", position = 1),
                replaceRule("r2", "Y", "yy", position = 2),
                replaceRule("r3", "Z", "", position = 3),
            ),
            bookId = "book-1",
        )
        assertEquals("abyycd", p.displayText)
        assertEquals(3, p.hitCount)
        val pieces = buildString {
            for (i in 0 until p.sourceText.length) {
                append(p.displaySliceForSourceRange(i, i + 1).text)
            }
        }
        assertEquals("逐字符分区拼接必须等于完整 display 文本", p.displayText, pieces)
        assertMapInvariants(p.offsetMap)
    }
}
