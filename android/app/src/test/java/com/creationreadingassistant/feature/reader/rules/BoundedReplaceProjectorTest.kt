package com.creationreadingassistant.feature.reader.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-替换（slice 2）：BoundedReplaceProjector 有界且精确的 source scope 投影 seam。
 *
 * 契约：
 * - scope 完整长度 <= 上限时精确调用 [ReplaceProjection.project]；任意正则可跨内部
 *   ReadingUnit / 段落边界，仍只应用一次、无重无漏；
 * - scope 超限时不投影、不部分替换，返回 [BoundedReplaceResult.UnsupportedTooLarge]
 *   （仅携带实际长度与上限，不含正文 / 规则）；
 * - maxSourceLength <= 0 返回 [BoundedReplaceResult.InvalidLimit]（typed invalid，
 *   不抛异常，也不投影）；
 * - [BoundedReplaceResult.Exact] 携带 scopeSourceBase（全书 source 起点），提供
 *   local display ↔ global source 双向安全映射；clamp、floor、空文本语义与 slice 1
 *   的 [TextOffsetMap] 一致；source raw 权威、display 派生；
 * - 结果（含 toString）不得泄漏正文、规则内容或 profileKey。
 */
class BoundedReplaceProjectorTest {

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

    // ── 1. 边界与 typed result ────────────────────────────────────────

    @Test
    fun `scope exactly at max projects exactly`() {
        val max = 8
        val result = BoundedReplaceProjector.project(
            "abcXYZde", // 8 chars == max
            listOf(replaceRule("del", "XYZ", "", position = 0)),
            bookId = "book-1",
            maxSourceLength = max,
        )
        assertTrue("len == max 必须成功", result is BoundedReplaceResult.Exact)
        val exact = result as BoundedReplaceResult.Exact
        assertEquals("abcde", exact.projection.displayText)
        assertEquals(1, exact.hitCount)
        assertEquals(8, exact.sourceLength)
        assertEquals(5, exact.displayLength)
    }

    @Test
    fun `scope over max returns UnsupportedTooLarge with actual length and limit`() {
        val result = BoundedReplaceProjector.project(
            "abcXYZdef", // 9 chars > max 8
            listOf(replaceRule("del", "XYZ", "", position = 0)),
            bookId = "book-1",
            maxSourceLength = 8,
        )
        assertEquals(
            BoundedReplaceResult.UnsupportedTooLarge(actualSourceLength = 9, maxSourceLength = 8),
            result,
        )
    }

    @Test
    fun `oversized scope never invokes projection even with uncompilable regex`() {
        // 若投影被调用，RuleEngine 会对不可编译正则抛 IllegalArgumentException；
        // 超限先短路，因此返回 Unsupported 而非异常，证明没有发生任何（部分）替换。
        val result = BoundedReplaceProjector.project(
            "123456", // 6 chars > max 5
            listOf(replaceRule("bad", "[", "")),
            bookId = "book-1",
            maxSourceLength = 5,
        )
        assertEquals(BoundedReplaceResult.UnsupportedTooLarge(6, 5), result)
    }

    @Test
    fun `in-bound scope delegates projection so rule compilation errors surface`() {
        assertThrows(IllegalArgumentException::class.java) {
            BoundedReplaceProjector.project(
                "123", // 3 chars <= max 5，进入 ReplaceProjection.project
                listOf(replaceRule("bad", "[", "")),
                bookId = "book-1",
                maxSourceLength = 5,
            )
        }
    }

    @Test
    fun `zero or negative max returns InvalidLimit typed result without projecting`() {
        // 规则不可编译也不触发：max<=0 在投影前短路，证明未调用任何替换。
        val rules = listOf(replaceRule("bad", "[", ""))
        assertEquals(
            BoundedReplaceResult.InvalidLimit(0),
            BoundedReplaceProjector.project("abc", rules, "book-1", maxSourceLength = 0),
        )
        assertEquals(
            BoundedReplaceResult.InvalidLimit(-1),
            BoundedReplaceProjector.project("abc", rules, "book-1", maxSourceLength = -1),
        )
    }

    @Test
    fun `default max is a conservative documented constant and callers can override`() {
        assertEquals(256 * 1024, BoundedReplaceProjector.DEFAULT_MAX_SOURCE_CHARS)
        assertTrue(BoundedReplaceProjector.DEFAULT_MAX_SOURCE_CHARS > 0)
        // 自定义小上限同样生效：6 chars > 3
        assertEquals(
            BoundedReplaceResult.UnsupportedTooLarge(6, 3),
            BoundedReplaceProjector.project("abcdef", emptyList(), "book-1", maxSourceLength = 3),
        )
    }

    @Test
    fun `negative scope source base is rejected as a caller contract violation`() {
        assertThrows(IllegalArgumentException::class.java) {
            BoundedReplaceProjector.project("abc", emptyList(), "book-1", scopeSourceBase = -1)
        }
    }

    // ── 2. 跨人为内部边界的正则（完整 scope 一次投影） ─────────────────

    @Test
    fun `regex crossing an annotated internal boundary hits exactly once`() {
        // 调用方按 ReadingUnit 边界把 source 划分为 [0,2) 与 [2,4)；
        // 完整 scope 投影下跨边界正则只命中一次、无重无漏。
        val scope = "头段尾段"
        val boundaryIndex = 2
        assertEquals("段尾", scope.substring(boundaryIndex - 1, boundaryIndex + 1))
        assertTrue("命中 [1,3) 必须跨边界 2", 1 < boundaryIndex && boundaryIndex < 3)

        val exact = BoundedReplaceProjector.project(
            scope,
            listOf(replaceRule("cross", "段尾", "[X]", position = 0)),
            bookId = "book-1",
        ) as BoundedReplaceResult.Exact
        assertEquals(1, exact.hitCount)
        assertEquals("头[X]段", exact.projection.displayText)
    }

    @Test
    fun `regex crossing a paragraph newline boundary hits once`() {
        val scope = "第一段\n第二段"
        val boundaryIndex = 3 // '\n' 位置：段落边界 [0,3) 与 [3,7)
        assertEquals("\n", scope.substring(boundaryIndex, boundaryIndex + 1))

        val exact = BoundedReplaceProjector.project(
            scope,
            listOf(replaceRule("nl", "段\n第", "|", position = 0)),
            bookId = "book-1",
        ) as BoundedReplaceResult.Exact
        assertEquals(1, exact.hitCount)
        assertEquals("第一|二段", exact.projection.displayText)
    }

    @Test
    fun `multiline dotall regex crosses newline and replaces once`() {
        val exact = BoundedReplaceProjector.project(
            "第一段\n第二段",
            listOf(replaceRule("dotall", "(?s)第一段.*第二段", "合并", position = 0)),
            bookId = "book-1",
        ) as BoundedReplaceResult.Exact
        assertEquals(1, exact.hitCount)
        assertEquals("合并", exact.projection.displayText)
    }

    @Test
    fun `cross-boundary replacement can delete shorten or lengthen`() {
        val deleted = BoundedReplaceProjector.project(
            "头段尾段",
            listOf(replaceRule("del", "段尾", "", position = 0)),
            bookId = "book-1",
        ) as BoundedReplaceResult.Exact
        assertEquals("头段", deleted.projection.displayText)
        assertEquals(1, deleted.hitCount)

        val shortened = BoundedReplaceProjector.project(
            "头段尾段",
            listOf(replaceRule("short", "段尾", "短", position = 0)),
            bookId = "book-1",
        ) as BoundedReplaceResult.Exact
        assertEquals("头短段", shortened.projection.displayText)
        assertEquals(1, shortened.hitCount)

        val lengthened = BoundedReplaceProjector.project(
            "头段尾段",
            listOf(replaceRule("long", "段尾", "很长的替换", position = 0)),
            bookId = "book-1",
        ) as BoundedReplaceResult.Exact
        assertEquals("头很长的替换段", lengthened.projection.displayText)
        assertEquals(1, lengthened.hitCount)
    }

    @Test
    fun `multiple rules apply in position order across boundaries with no dup or omission`() {
        // 同一条规则命中两次：一次跨边界 [1,3)、一次在边界内 [5,7)
        val scope = "头段尾段X段尾"
        val boundaryIndex = 2
        val both = BoundedReplaceProjector.project(
            scope,
            listOf(replaceRule("r", "段尾", "X", position = 0)),
            bookId = "book-1",
        ) as BoundedReplaceResult.Exact
        assertEquals("跨边界与边界内各命中一次，无重无漏", 2, both.hitCount)
        assertEquals("头X段XX", both.projection.displayText)
        assertTrue(1 < boundaryIndex && boundaryIndex < 3)
        assertFalse(5 < boundaryIndex && boundaryIndex < 7)

        // 多规则顺序：先跨边界替换，再对上一规则的结果继续替换
        val chained = BoundedReplaceProjector.project(
            "头段尾段",
            listOf(
                replaceRule("a", "段尾", "X", position = 1),
                replaceRule("b", "头X", "Y", position = 2),
            ),
            bookId = "book-1",
        ) as BoundedReplaceResult.Exact
        assertEquals(2, chained.hitCount)
        assertEquals("Y段", chained.projection.displayText)
    }

    // ── 3. scopeSourceBase 与双向安全映射 ──────────────────────────────

    @Test
    fun `exact result maps local display to global source with base and clamp`() {
        val base = 1000
        val exact = BoundedReplaceProjector.project(
            "abcXYZdef",
            listOf(replaceRule("del", "XYZ", "", position = 0)),
            bookId = "book-1",
            scopeSourceBase = base,
        ) as BoundedReplaceResult.Exact
        assertEquals(base, exact.scopeSourceBase)
        assertEquals("source raw 权威、原样保留", "abcXYZdef", exact.projection.sourceText)
        assertEquals("display 派生", "abcdef", exact.projection.displayText)
        assertEquals(9, exact.sourceLength)
        assertEquals(6, exact.displayLength)

        assertEquals(base + 0, exact.localDisplayToGlobalSource(0))
        assertEquals("删除坍缩点按 floor 回到删除起点", base + 3, exact.localDisplayToGlobalSource(3))
        assertEquals(base + 9, exact.localDisplayToGlobalSource(6))
        assertEquals("负偏移 clamp 到 0", base + 0, exact.localDisplayToGlobalSource(-5))
        assertEquals("越界 clamp 到源全长", base + 9, exact.localDisplayToGlobalSource(999))
    }

    @Test
    fun `global source to local display uses floor semantics and clamps`() {
        val base = 1000
        val exact = BoundedReplaceProjector.project(
            "abcXYZdef",
            listOf(replaceRule("del", "XYZ", "", position = 0)),
            bookId = "book-1",
            scopeSourceBase = base,
        ) as BoundedReplaceResult.Exact

        assertEquals(0, exact.globalSourceToLocalDisplay(base + 0))
        assertEquals(3, exact.globalSourceToLocalDisplay(base + 3))
        assertEquals("删除区间内源偏移折叠到删除起点", 3, exact.globalSourceToLocalDisplay(base + 4))
        assertEquals(3, exact.globalSourceToLocalDisplay(base + 5))
        assertEquals(3, exact.globalSourceToLocalDisplay(base + 6))
        assertEquals(4, exact.globalSourceToLocalDisplay(base + 7))
        assertEquals(6, exact.globalSourceToLocalDisplay(base + 9))
        assertEquals("越界 clamp 到起点", 0, exact.globalSourceToLocalDisplay(0))
        assertEquals("越界 clamp 到 display 全长", 6, exact.globalSourceToLocalDisplay(base + 999))
    }

    @Test
    fun `insertion tail maps by floor and round trips stay bounded both ways`() {
        val base = 500
        val exact = BoundedReplaceProjector.project(
            "abcXYZdef",
            listOf(replaceRule("long", "XYZ", "XYZW", position = 0)),
            bookId = "book-1",
            scopeSourceBase = base,
        ) as BoundedReplaceResult.Exact
        assertEquals("abcXYZWdef", exact.projection.displayText)

        assertEquals("插入字符 W 平铺到匹配末尾", base + 6, exact.localDisplayToGlobalSource(6))
        assertEquals(base + 6, exact.localDisplayToGlobalSource(7))
        assertEquals(6, exact.globalSourceToLocalDisplay(base + 6))

        for (d in 0..exact.displayLength) {
            val g = exact.localDisplayToGlobalSource(d)
            assertTrue("display round trip floor d=$d", exact.globalSourceToLocalDisplay(g) <= d)
        }
        for (g in base..base + exact.sourceLength) {
            val d = exact.globalSourceToLocalDisplay(g)
            assertTrue("source round trip floor g=$g", exact.localDisplayToGlobalSource(d) <= g)
        }
    }

    @Test
    fun `projection slice stays scope local and base shifts to global source`() {
        val base = 7
        val exact = BoundedReplaceProjector.project(
            "aXbYc",
            listOf(
                replaceRule("r1", "X", "", position = 1),
                replaceRule("r2", "Y", "yy", position = 2),
            ),
            bookId = "book-1",
            scopeSourceBase = base,
        ) as BoundedReplaceResult.Exact
        assertEquals("abyyc", exact.projection.displayText)
        val slice = exact.projection.displaySliceForSourceRange(2, 4)
        // "Y"→"yy" 的插入余量（第二个 y）按 floor 归属后续源范围，
        // 故 "bY" 投影为 "by"：源窗口 display 区间平铺、无重无漏。
        assertEquals("by", slice.text)
        assertEquals(2, slice.sourceLength)
        assertEquals(2, slice.displayLength)
        assertEquals("切片 source 坐标是 scope 局部", 2, slice.sourceBase)
        // 删除坍缩：display 起点经 map floor 回退，全局结果不越过局部 source 起点
        assertTrue(exact.localDisplayToGlobalSource(slice.displayBase) <= base + slice.sourceBase)
    }

    @Test
    fun `empty scope maps both directions to base and zero`() {
        val base = 42
        val exact = BoundedReplaceProjector.project(
            "",
            listOf(replaceRule("r", "a", "b")),
            bookId = "book-1",
            scopeSourceBase = base,
        ) as BoundedReplaceResult.Exact
        assertEquals(0, exact.sourceLength)
        assertEquals(0, exact.displayLength)
        assertEquals(0, exact.hitCount)
        assertEquals(base, exact.localDisplayToGlobalSource(0))
        assertEquals(base, exact.localDisplayToGlobalSource(-1))
        assertEquals(base, exact.localDisplayToGlobalSource(100))
        assertEquals(0, exact.globalSourceToLocalDisplay(base))
        assertEquals(0, exact.globalSourceToLocalDisplay(base - 100))
        assertEquals(0, exact.globalSourceToLocalDisplay(base + 100))
    }

    // ── 4. profileKey 透传与隐私 ──────────────────────────────────────

    @Test
    fun `exact result passes through profileKey and identity for empty rules`() {
        val rules = listOf(replaceRule("r1", "广告", "", position = 0))
        val exact = BoundedReplaceProjector.project(
            "广告正文",
            rules,
            bookId = "book-1",
        ) as BoundedReplaceResult.Exact
        assertEquals("key 与投影内部一致", exact.projection.profileKey, exact.profileKey)
        assertEquals("key 与 slice 1 的 ReplaceProfile 一致", ReplaceProfile.key("book-1", rules), exact.profileKey)

        val empty = BoundedReplaceProjector.project("正文", emptyList(), bookId = "book-9") as BoundedReplaceResult.Exact
        assertEquals(ReplaceProfile.EMPTY_KEY, empty.profileKey)
        assertEquals(0, empty.hitCount)
    }

    @Test
    fun `unsupported result leaks no scope or rule content in fields or toString`() {
        val secretScope = "机密正文内容_不要外泄"
        val secretPattern = "机密_正则_内容"
        val secretReplacement = "机密_替换_内容"
        val result = BoundedReplaceProjector.project(
            secretScope,
            listOf(replaceRule("secret-rule", secretPattern, secretReplacement)),
            bookId = "secret-book",
            maxSourceLength = 3, // 故意超限
        )
        val unsupported = result as BoundedReplaceResult.UnsupportedTooLarge
        assertEquals(secretScope.length, unsupported.actualSourceLength)
        assertEquals(3, unsupported.maxSourceLength)

        val rendered = unsupported.toString()
        assertFalse("Unsupported.toString 不得包含正文", rendered.contains("机密正文"))
        assertFalse("Unsupported.toString 不得包含规则原文", rendered.contains(secretPattern))
        assertFalse("Unsupported.toString 不得包含替换文本", rendered.contains(secretReplacement))
        assertFalse("Unsupported.toString 不得包含 bookId", rendered.contains("secret-book"))
    }

    @Test
    fun `exact result toString omits scope rule content and profileKey`() {
        val scope = "机密正文内容"
        val pattern = "机密_正则"
        val replacement = "公开_替换"
        val exact = BoundedReplaceProjector.project(
            scope,
            listOf(replaceRule("r", pattern, replacement)),
            bookId = "secret-book",
        ) as BoundedReplaceResult.Exact
        val rendered = exact.toString()
        assertTrue("toString 仍可读地描述结果", rendered.contains("sourceLength="))
        assertFalse("Exact.toString 不得包含正文", rendered.contains(scope))
        assertFalse("Exact.toString 不得包含规则原文", rendered.contains(pattern))
        assertFalse("Exact.toString 不得包含替换文本", rendered.contains(replacement))
        assertFalse("Exact.toString 不得包含 profileKey（禁止写日志）", rendered.contains(exact.profileKey))
    }
}
