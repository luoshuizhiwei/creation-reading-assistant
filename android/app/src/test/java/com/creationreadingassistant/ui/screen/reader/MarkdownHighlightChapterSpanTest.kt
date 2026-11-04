package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderKind
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F3.1 任务 B：删除「伪 stale-chapter 判据」后的真实过滤证明。
 *
 * 背景（Qoder P2-1）：F3 的调用方把每条 `HighlightEntity` 人工包装成
 * `chapterIndex = 当前 s.chapterIndex`，随后又用「当前章」去过滤它 ——
 * 该判据在生产路径**恒成立**，防串章实际只靠几何不相交。测试传入产品永不产生的
 * `chapterIndex = 7` 制造了假安全感。
 *
 * 修正后的契约（本文件钉死）：
 * - `SavedHighlight` **不再携带** chapterIndex（记录里没有真实章身份：
 *   `LocatorBuilder.forPlain` 恒写 `ci = 0`，见其 KDoc「章节索引恒为 0」）；
 * - 是否渲染只由「高亮全书区间」与「当前章节 canonical span」+「渲染单元区间」
 *   求交决定；
 * - 因此「高亮完全落在当前章 span 之外」必须**真的不投影**，且这个反例的构造
 *   方式必须是「换一章的几何位置」，而不是手工填一个不存在的章号。
 */
class MarkdownHighlightChapterSpanTest {

    private fun chapter(src: String) = MarkdownParser.parse(src)

    private fun saved(start: Int, length: Int, colorKey: String = "yellow") =
        MarkdownPersistentHighlights.SavedHighlight(
            bookKey = "book-1",
            start = start,
            length = length,
            colorKey = colorKey,
        )

    /** 当前章节在全书 canonical 空间中的半开 span。 */
    private fun project(
        src: String,
        highlight: MarkdownPersistentHighlights.SavedHighlight,
        chapterSpan: IntRange,
        chapterBase: Int = 0,
    ): Map<String, List<MarkdownPersistentHighlights.HighlightSpan>> {
        val ch = chapter(src)
        return MarkdownPersistentHighlights.projectPersistentHighlights(
            units = MarkdownRenderModel.flatten(ch),
            canonicalText = ch.canonicalText,
            highlights = listOf(highlight),
            currentBookKey = "book-1",
            chapterSpan = chapterSpan,
            blockGlobalBase = chapterBase,
        )
    }

    // ── 章内命中 ────────────────────────────────────────────────────────

    @Test
    fun `a highlight inside the current chapter span is projected`() {
        // 第 2 章 span = [1000, 1010)，canonicalRange 是章内局部 → base = 1000
        val spans = project(
            src = "甲段落\n",
            highlight = saved(start = 1002, length = 3),
            chapterSpan = 1000 until 1010,
            chapterBase = 1000,
        )
        assertTrue("章内高亮必须投影", spans.values.any { it.isNotEmpty() })
    }

    // ── 章外不投影（真反例，非手工章号） ────────────────────────────────

    @Test
    fun `a highlight entirely before the current chapter span is not projected`() {
        // 高亮在上一章 [500, 520)，当前章 span = [1000, 1010)
        // 注意：这里没有 chapterIndex 字段可用，只能靠几何判据
        val spans = project(
            src = "甲段落\n",
            highlight = saved(start = 505, length = 5),
            chapterSpan = 1000 until 1010,
            chapterBase = 1000,
        )
        assertTrue(
            "完全位于当前章 span 之前的高亮不得投影",
            spans.values.all { it.isEmpty() },
        )
    }

    @Test
    fun `a highlight entirely after the current chapter span is not projected`() {
        val spans = project(
            src = "甲段落\n",
            highlight = saved(start = 2000, length = 5),
            chapterSpan = 1000 until 1010,
            chapterBase = 1000,
        )
        assertTrue(
            "完全位于当前章 span 之后的高亮不得投影",
            spans.values.all { it.isEmpty() },
        )
    }

    @Test
    fun `a highlight touching only the span boundary is not projected`() {
        // 高亮紧邻 span 起点之前结束：[995, 1000)
        val before = project(
            src = "甲段落\n",
            highlight = saved(start = 995, length = 5),
            chapterSpan = 1000 until 1010,
            chapterBase = 1000,
        )
        assertTrue(before.values.all { it.isEmpty() })

        // 高亮从 span 终点开始：[1010, 1015)
        val after = project(
            src = "甲段落\n",
            highlight = saved(start = 1010, length = 5),
            chapterSpan = 1000 until 1010,
            chapterBase = 1000,
        )
        assertTrue(after.values.all { it.isEmpty() })
    }

    @Test
    fun `a highlight straddling the span start renders only the inside part`() {
        // 高亮 [998, 1004) 与当前章 [1000, 1010) 相交 → 只渲染 [1000, 1004)
        val src = "甲段落乙\n"
        val ch = chapter(src)
        val units = MarkdownRenderModel.flatten(ch)
        val unit = units.first { it.kind == MarkdownRenderKind.PARAGRAPH }
        val spans = MarkdownPersistentHighlights.projectPersistentHighlights(
            units = units,
            canonicalText = ch.canonicalText,
            highlights = listOf(saved(start = 998, length = 6)),
            currentBookKey = "book-1",
            chapterSpan = 1000 until 1010,
            blockGlobalBase = 1000,
        )
        val hit = spans.getValue(unit.id).singleOrNull()
        assertNotNull("跨 span 起点的高亮必须渲染章内部分", hit)
        // 章内 [1000,1004) → 局部 [0, 4)（单元从 1000 开始）
        assertEquals(0, hit!!.localRange.first)
        assertEquals(4, hit.localRange.last + 1)
    }

    // ── bookKey 判据仍然有效（上游已按 book_id 查询，这里是二次保险） ────

    @Test
    fun `a highlight inside a unit but outside a narrow chapter span is not projected`() {
        // **chapterSpan 真正起作用的反例**（mutation 验证后补）。
        //
        // 其余「章外」用例其实都被 intersectLocal 的单元求交顺带挡住了 —— 把
        // chapterSpan 过滤整行删掉后它们照样绿，说明那些用例证明不了这个判据。
        // 唯一能区分的形态：高亮**落在某个渲染单元区间内**、但在**当前章 span 之外**
        // （本章 charCount 小于块实际跨度时会出现）。此时只有 chapterSpan 能拦住。
        val src = "甲乙丙丁戊己"
        val spans = project(
            src = src,
            highlight = saved(start = 4, length = 2), // [4, 6)：在单元 [0,6) 内
            chapterSpan = 0 until 3, // 但本章 span 只到 3
            chapterBase = 0,
        )
        assertTrue(
            "落在单元内但超出本章 span 的高亮不得投影（只有 chapterSpan 能拦住）",
            spans.values.all { it.isEmpty() },
        )
    }

    @Test
    fun `an unknown chapter span degrades to unit intersection instead of dropping all`() {
        // 章长度未知（charCount = 0 ⇒ chapterSpan 为空）时，不得静默吞掉全部高亮：
        // 退化为「仅按渲染单元求」的有记录的最佳努力。
        val src = "甲乙丙丁"
        val spans = project(
            src = src,
            highlight = saved(start = 1, length = 2),
            chapterSpan = 0 until 0, // 空 span = 未知
            chapterBase = 0,
        )
        assertTrue(
            "章长度未知时不得吞掉全部高亮（显式降级为单元求交）",
            spans.values.any { it.isNotEmpty() },
        )
    }

    @Test
    fun `whole document parse relies on chapterSpan instead of a chapter base`() {
        // 关键反例（mutation 验证后补）：整本解析模式下 blockGlobalBase = 0，
        // 若 chapterSpan 过滤被删掉，落在其他章的高亮会**错误地投影到本章** ——
        // 这是流式模式掩盖不到的盲区（流式靠 base 错位才不命中）。
        val src = "甲段落\n"
        val spans = project(
            src = src,
            // 高亮 [2000, 2003) 属于后面的章；本章 span = [0, 1000)
            highlight = saved(start = 2000, length = 3),
            chapterSpan = 0 until 1000,
            chapterBase = 0, // 整本解析：块 canonicalRange 已是全书坐标
        )
        assertTrue(
            "整本解析模式下，落在其他章 span 的高亮不得投影到本章",
            spans.values.all { it.isEmpty() },
        )
    }

    @Test
    fun `a highlight from another book is still ignored`() {
        val ch = chapter("甲段落\n")
        val spans = MarkdownPersistentHighlights.projectPersistentHighlights(
            units = MarkdownRenderModel.flatten(ch),
            canonicalText = ch.canonicalText,
            highlights = listOf(
                MarkdownPersistentHighlights.SavedHighlight(
                    bookKey = "other-book",
                    start = 0,
                    length = 3,
                    colorKey = "yellow",
                ),
            ),
            currentBookKey = "book-1",
            chapterSpan = 0 until 10,
            blockGlobalBase = 0,
        )
        assertTrue(spans.values.all { it.isEmpty() })
    }

    // ── 契约形状：SavedHighlight 不得再带 chapterIndex ──────────────────

    @Test
    fun `saved highlight carries no fabricated chapter identity`() {
        val fields = MarkdownPersistentHighlights.SavedHighlight::class.java.declaredFields
            .map { it.name }
            .toSet()
        assertTrue(
            "SavedHighlight 不得再有 chapterIndex（生产路径无法真实填充）",
            "chapterIndex" !in fields,
        )
        assertTrue("必须保留 start / length 供几何求交", "start" in fields && "length" in fields)
    }
}
