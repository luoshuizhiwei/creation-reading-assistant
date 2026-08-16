package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderContent
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderKind
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderModel
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderUnit
import com.creationreadingassistant.feature.reader.doc.MdInlineKind
import com.creationreadingassistant.feature.reader.layout.BlockRole
import com.creationreadingassistant.feature.reader.layout.LayoutBlock
import com.creationreadingassistant.feature.reader.layout.LayoutParagraph
import com.creationreadingassistant.ui.screen.reader.SearchHitTarget
import com.creationreadingassistant.ui.screen.reader.markdownRenderUnitIndexForChapterOffset
import com.creationreadingassistant.ui.screen.reader.markdownScrollSearchHits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分页端 MarkdownPageSource / LayoutBlockAdapter 与 doc 统一渲染模型
 * （MarkdownRenderModel.flatten）的契约测试。
 *
 * 核心不变式：分页端不再递归 AST，LayoutBlock 全部由扁平渲染单元 + canonical
 * 文本切片生成；同一 chapter 下，滚动端单元（阅读顺序 / identity / canonical
 * ranges）与分页端 LayoutParagraph（charOffset / 文本切片 / 角色 / marker /
 * inline spans / 图片 anchor）一致。
 */
class MarkdownPageSourceTest {

    // ── 适配 seam：嵌套引用 + 列表 ──────────────────────────────────

    @Test
    fun `nested quote and list keep reading order markers and depth`() {
        val chapter = MarkdownParser.parse(
            """
            > - 引用中的列表
            >   - 更深
            """.trimIndent(),
        )
        val units = MarkdownRenderModel.flatten(chapter)
        val paragraphs = textParagraphs(MarkdownPageSource.layoutBlocksOf(chapter))

        // 滚动端：两个 LIST_ITEM 单元，id / depth / blockquoteDepth 精确
        assertEquals(listOf("b0.q0.l0.c0", "b0.q0.l0.c1.l0.c0"), units.map { it.id })
        assertEquals(listOf(2, 3), units.map { it.depth })
        assertEquals(listOf(1, 1), units.map { it.blockquoteDepth })

        // 分页端：同一阅读顺序的两个列表块，专用角色 + marker 与缩进层级一致
        assertEquals(2, paragraphs.size)
        paragraphs.forEachIndexed { i, p ->
            assertEquals(BlockRole.LIST_ITEM_BULLET, p.role)
            assertEquals("• ", p.listMarker)
            assertEquals(slice(chapter.canonicalText, units[i].canonicalRange), p.text)
            assertEquals(units[i].canonicalRange.first, p.charOffset)
            assertEquals(units[i].depth, p.indentLevel)
        }
    }

    // ── 适配 seam：引用段落 → QUOTE，普通段落 → BODY ──────────────

    @Test
    fun `quote paragraphs map to QUOTE and keep quote depth in indent`() {
        val chapter = MarkdownParser.parse(
            """
            普通段落

            > 引用段
            >
            > > 嵌套引用
            """.trimIndent(),
        )
        val units = MarkdownRenderModel.flatten(chapter)
        val paragraphs = textParagraphs(MarkdownPageSource.layoutBlocksOf(chapter))

        // 滚动端：普通段 + 引用段 + 嵌套引用段，blockquoteDepth 精确
        assertEquals(listOf(0, 1, 2), units.map { it.blockquoteDepth })

        // 分页端：引用中的普通段落用 QUOTE，普通段落保持 BODY；
        // indentLevel 已包含引用层级，charOffset 仍与单元 canonical 起点一致
        assertEquals(listOf(BlockRole.BODY, BlockRole.QUOTE, BlockRole.QUOTE), paragraphs.map { it.role })
        assertEquals(units.map { it.depth }, paragraphs.map { it.indentLevel })
        assertEquals(units.map { it.canonicalRange.first }, paragraphs.map { it.charOffset })
    }

    // ── 适配 seam：引用中的列表保留列表角色 ────────────────────────

    @Test
    fun `lists inside quotes keep list role with quote depth in indent`() {
        val chapter = MarkdownParser.parse(
            """
            > - 引用中的列表
            > - 第二项
            """.trimIndent(),
        )
        val units = MarkdownRenderModel.flatten(chapter)
        val paragraphs = textParagraphs(MarkdownPageSource.layoutBlocksOf(chapter))

        assertEquals(listOf(1, 1), units.map { it.blockquoteDepth })
        assertEquals(
            listOf(BlockRole.LIST_ITEM_BULLET, BlockRole.LIST_ITEM_BULLET),
            paragraphs.map { it.role },
        )
        assertEquals(units.map { it.depth }, paragraphs.map { it.indentLevel })
        assertEquals(listOf("• ", "• "), paragraphs.map { it.listMarker })
    }

    // ── 适配 seam：有序列表非 1 起始 ────────────────────────────────

    @Test
    fun `ordered list keeps start number markers and ordinal`() {
        val chapter = MarkdownParser.parse("3. 第三\n4. 第四")
        val units = MarkdownRenderModel.flatten(chapter)
        val paragraphs = textParagraphs(MarkdownPageSource.layoutBlocksOf(chapter))

        assertEquals(
            listOf(MarkdownRenderKind.LIST_ITEM, MarkdownRenderKind.LIST_ITEM),
            units.map { it.kind },
        )
        assertEquals(listOf("3. ", "4. "), units.map { (it.content as MarkdownRenderContent.ListItem).marker })
        assertEquals(listOf(3, 4), units.map { (it.content as MarkdownRenderContent.ListItem).ordinal })

        assertEquals(listOf("3. ", "4. "), paragraphs.map { it.listMarker })
        assertEquals(listOf("第三", "第四"), paragraphs.map { it.text })
        assertEquals(units.map { it.canonicalRange.first }, paragraphs.map { it.charOffset })
        assertEquals(
            listOf(BlockRole.LIST_ITEM_NUMBER, BlockRole.LIST_ITEM_NUMBER),
            paragraphs.map { it.role },
        )
    }

    // ── 适配 seam：任务列表 ─────────────────────────────────────────

    @Test
    fun `task list maps checked state to markers`() {
        val chapter = MarkdownParser.parse("- [x] 完成\n- [ ] 未完成")
        val units = MarkdownRenderModel.flatten(chapter)
        val paragraphs = textParagraphs(MarkdownPageSource.layoutBlocksOf(chapter))

        assertEquals(listOf(true, false), units.map { (it.content as MarkdownRenderContent.TaskItem).checked })
        assertEquals(listOf("[x] ", "[ ] "), paragraphs.map { it.listMarker })
        assertEquals(listOf("完成", "未完成"), paragraphs.map { it.text })
        assertEquals(units.map { it.canonicalRange.first }, paragraphs.map { it.charOffset })
        assertEquals(
            listOf(BlockRole.TASK_ITEM_CHECKED, BlockRole.TASK_ITEM_UNCHECKED),
            paragraphs.map { it.role },
        )
    }

    // ── 适配 seam：代码语言 ─────────────────────────────────────────

    @Test
    fun `code block carries language on every line block`() {
        val chapter = MarkdownParser.parse(
            """
            ```kotlin
            val x = 1

            val y = 2
            ```
            """.trimIndent(),
        )
        val units = MarkdownRenderModel.flatten(chapter)
        val paragraphs = textParagraphs(MarkdownPageSource.layoutBlocksOf(chapter))

        val unit = units.single()
        assertEquals(MarkdownRenderKind.CODE_BLOCK, unit.kind)
        assertEquals("kotlin", (unit.content as MarkdownRenderContent.CodeBlock).language)

        // 代码逐非空行输出（空行不产生 LayoutBlock，与旧行为一致）
        assertEquals(listOf("val x = 1", "val y = 2"), paragraphs.map { it.text })
        paragraphs.forEach { p ->
            assertEquals(BlockRole.CODE_BLOCK, p.role)
            assertEquals("kotlin", p.codeLanguage)
        }
        assertEquals(unit.canonicalRange.first, paragraphs.first().charOffset)
        // 两行之间有一个空行（两个换行符），第二行起点在 canonical 文本中后移两个字符
        assertEquals(unit.canonicalRange.first + "val x = 1\n\n".length, paragraphs.last().charOffset)
    }

    // ── 适配 seam：表格 ─────────────────────────────────────────────

    @Test
    fun `table maps to header and row line blocks with exact offsets`() {
        val chapter = MarkdownParser.parse(
            """
            | A | B |
            |---|---|
            | 1 | 2 |
            """.trimIndent(),
        )
        val units = MarkdownRenderModel.flatten(chapter)
        val paragraphs = textParagraphs(MarkdownPageSource.layoutBlocksOf(chapter))

        val unit = units.single()
        assertEquals(MarkdownRenderKind.TABLE, unit.kind)
        val table = unit.content as MarkdownRenderContent.Table
        assertEquals(listOf("A", "B"), table.header.map { cellText(chapter, it) })
        assertEquals(listOf("1", "2"), table.rows[0].map { cellText(chapter, it) })

        // canonical 表格文本 = 表头行 + 数据行（分隔行不进入规范文本）
        val canonicalTable = slice(chapter.canonicalText, unit.canonicalRange)
        assertEquals("| A | B |\n| 1 | 2 |", canonicalTable)
        assertEquals(listOf("| A | B |", "| 1 | 2 |"), paragraphs.map { it.text })
        assertEquals(listOf(BlockRole.TABLE_HEADER, BlockRole.TABLE_ROW), paragraphs.map { it.role })
        assertEquals(
            listOf(unit.canonicalRange.first, unit.canonicalRange.first + "| A | B |".length + 1),
            paragraphs.map { it.charOffset },
        )
    }

    // ── 适配 seam：链接 / 图片 ──────────────────────────────────────

    @Test
    fun `link spans and image anchors survive the adapter`() {
        val chapter = MarkdownParser.parse("文字 [链接](https://example.com) 与 ![图片](img.png \"图题\")")
        val units = MarkdownRenderModel.flatten(chapter)
        val blocks = MarkdownPageSource.layoutBlocksOf(chapter)
        val unit = units.single()

        // 图片块：挂在 alt 文本起点，不占字符
        val image = blocks.filterIsInstance<LayoutBlock.Image>().single()
        assertEquals("img.png", image.sourceKey)
        val imageSpan = (unit.content as MarkdownRenderContent.Paragraph).spans
            .filterIsInstance<com.creationreadingassistant.feature.reader.doc.MarkdownRenderSpan.Image>()
            .single()
        assertEquals(unit.canonicalRange.first + imageSpan.start, image.anchorOffset)

        // 段落文本 = canonical 切片（含图片 alt 文本，供 TTS/搜索）
        val paragraph = blocks.filterIsInstance<LayoutBlock.Text>().single().paragraph
        assertEquals(BlockRole.BODY, paragraph.role)
        assertEquals(unit.canonicalRange.first, paragraph.charOffset)
        assertEquals("文字 链接 与 图片", paragraph.text)

        // 链接 span 保留（相对段落起点的区间 + LINK kind）
        val linkSpan = paragraph.inlineSpans.single { it.kind == MdInlineKind.LINK }
        assertEquals("文字 ".length, linkSpan.start)
        assertEquals("文字 链接".length, linkSpan.end)
    }

    // ── 两端一致：阅读顺序 / 结构数量 / identity / canonical ranges ─

    @Test
    fun `both ends agree on reading order identity and canonical ranges`() {
        val source = """
            # 标题

            段落 **粗体** 与 [链接](https://example.com)。

            > 引用段

            - 项目
              - 子项目

            - [x] 任务

            ```python
            print("hi")
            ```

            | A | B |
            |---|---|
            | 1 | 2 |

            ---
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val units = MarkdownRenderModel.flatten(chapter)
        val paragraphs = textParagraphs(MarkdownPageSource.layoutBlocksOf(chapter))

        // 滚动端单元身份唯一、index 连续
        assertEquals(units.size, units.map { it.id }.toSet().size)
        assertEquals(units.indices.toList(), units.map { it.index })

        // 阅读顺序：滚动端单元依次覆盖分页端全部文本块（代码块按行展开、表格按行展开）
        var p = 0
        for (unit in units) {
            when (unit.kind) {
                MarkdownRenderKind.HEADING,
                MarkdownRenderKind.PARAGRAPH,
                MarkdownRenderKind.LIST_ITEM,
                MarkdownRenderKind.TASK_ITEM -> {
                    val para = paragraphs[p++]
                    assertEquals(unit.canonicalRange.first, para.charOffset)
                    assertEquals(slice(chapter.canonicalText, unit.canonicalRange), para.text)
                    assertEquals(unit.depth, para.indentLevel)
                    if (unit.kind != MarkdownRenderKind.HEADING) {
                        assertEquals(expectedTextRole(unit), para.role)
                    }
                }

                MarkdownRenderKind.CODE_BLOCK -> {
                    val text = slice(chapter.canonicalText, unit.canonicalRange)
                    val lines = text.split('\n').filter { it.isNotEmpty() }
                    for (line in lines) {
                        val para = paragraphs[p++]
                        assertEquals(BlockRole.CODE_BLOCK, para.role)
                        assertEquals(line, para.text)
                        assertEquals(
                            unit.canonicalRange.first + text.indexOf(line),
                            para.charOffset,
                        )
                    }
                }

                MarkdownRenderKind.TABLE -> {
                    val text = slice(chapter.canonicalText, unit.canonicalRange)
                    val lines = text.split('\n').filter { it.isNotEmpty() }
                    lines.forEachIndexed { i, line ->
                        val para = paragraphs[p++]
                        assertEquals(if (i == 0) BlockRole.TABLE_HEADER else BlockRole.TABLE_ROW, para.role)
                        assertEquals(line, para.text)
                        assertEquals(unit.canonicalRange.first + text.indexOf(line), para.charOffset)
                    }
                }

                MarkdownRenderKind.HORIZONTAL_RULE -> {
                    val para = paragraphs[p++]
                    assertEquals(BlockRole.HORIZONTAL_RULE, para.role)
                    assertEquals(unit.canonicalRange.first, para.charOffset)
                    assertEquals(slice(chapter.canonicalText, unit.canonicalRange), para.text)
                }
            }
        }
        assertEquals("分页端应恰好覆盖滚动端全部单元", p, paragraphs.size)
    }

    // ── 搜索偏移定位：两端同一 canonical 起点 ───────────────────────

    @Test
    fun `search offsets resolve to same canonical start on both ends`() {
        val source = """
            # 标题

            - [x] 任务 **词**

            > 引用段

            | A | B |
            |---|---|
            | 1 | 2 |
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val canonical = chapter.canonicalText
        val units = MarkdownRenderModel.flatten(chapter)
        val paragraphs = textParagraphs(MarkdownPageSource.layoutBlocksOf(chapter))

        // 对每个有可见文本的单元：滚动端命中换算与索引映射、分页端 charOffset
        // 必须指向同一 canonical 起点。
        for (unit in units) {
            val visible = firstVisibleOffset(unit) ?: continue
            assertEquals(
                "单元 ${unit.id} 的命中应定位到其自身滚动索引",
                unit.index,
                markdownRenderUnitIndexForChapterOffset(chapter, visible, 0, blocksGlobal = false),
            )
            val hits = markdownScrollSearchHits(
                chapter = chapter,
                chapterBase = 0,
                chapterLength = canonical.length,
                blockGlobalBase = 0,
                target = SearchHitTarget(
                    bookKey = "book-1",
                    chapterIndex = 1,
                    absoluteRange = visible until visible + 1,
                    resultIndex = 0,
                ),
                currentBookKey = "book-1",
                currentChapterIndex = 1,
            )
            assertTrue(
                "单元 ${unit.id} 应有以 canonical 起点 $visible 开头的命中",
                hits.any { it.canonicalStart == visible },
            )
            assertTrue(
                "单元 ${unit.id} 的分页端块应覆盖该 canonical 起点",
                paragraphs.any {
                    it.charOffset <= visible && visible < it.charOffset + it.text.length
                },
            )
        }

        // 表格单元格命中：精确到单元格局部区间（滚动端）
        val tableUnit = units.single { it.kind == MarkdownRenderKind.TABLE }
        val table = tableUnit.content as MarkdownRenderContent.Table
        val firstCell = table.header.first()
        val cellHits = markdownScrollSearchHits(
            chapter = chapter,
            chapterBase = 0,
            chapterLength = canonical.length,
            blockGlobalBase = 0,
            target = SearchHitTarget(
                bookKey = "book-1",
                chapterIndex = 1,
                absoluteRange = firstCell.canonicalRange.first until firstCell.canonicalRange.first + 1,
                resultIndex = 0,
            ),
            currentBookKey = "book-1",
            currentChapterIndex = 1,
        )
        assertEquals(
            listOf(firstCell.canonicalRange.first),
            cellHits.map { it.canonicalStart },
        )
        assertEquals(listOf(0 until 1), cellHits.map { it.localRange })
    }

    // ── 重复 flatten 稳定性 ─────────────────────────────────────────

    @Test
    fun `repeated flatten and layout are stable`() {
        val source = """
            # 标题

            - [ ] 任务

            > 引用

            | A | B |
            |---|---|
            | 1 | 2 |
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)

        val units1 = MarkdownRenderModel.flatten(chapter)
        val units2 = MarkdownRenderModel.flatten(chapter)
        assertEquals(units1, units2)
        assertEquals(units1.map { it.id }, units2.map { it.id })
        assertEquals(units1.map { it.canonicalRange }, units2.map { it.canonicalRange })

        assertEquals(
            MarkdownPageSource.layoutBlocksOf(chapter),
            MarkdownPageSource.layoutBlocksOf(chapter),
        )
    }

    // ── MarkdownChapterSource ───────────────────────────────────────

    @Test
    fun `chapter source exposes flattened layout and exact text`() {
        val source = "# 标题\n\n正文 **粗体**。\n\n- 项"
        val doc = MarkdownDocument(source)
        val chapterSource = MarkdownChapterSource(doc)

        assertEquals(doc.chapters.size, chapterSource.chapterCount)
        assertEquals(doc.totalChars, chapterSource.totalChars)
        assertEquals(doc.chapters.first().title, chapterSource.chapterTitle(0))
        assertEquals(doc.chapters.first().startOffset, chapterSource.chapterStartAbs(0))

        val content = chapterSource.loadChapter(0)
        assertEquals(doc.chapters.first().charCount, content.text.length)
        val joined = content.blocks.filterIsInstance<LayoutBlock.Text>()
            .joinToString("\n") { it.paragraph.text }
        assertEquals("章节 text 与 blocks 文本应一致", content.text, joined)
    }

    // ── 辅助 ────────────────────────────────────────────────────────

    private fun textParagraphs(blocks: List<LayoutBlock>): List<LayoutParagraph> =
        blocks.filterIsInstance<LayoutBlock.Text>().map { it.paragraph }

    private fun slice(canonical: String, range: IntRange): String =
        canonical.substring(range.first, range.last + 1)

    private fun cellText(
        chapter: MarkdownParser.MarkdownChapter,
        cell: com.creationreadingassistant.feature.reader.doc.MarkdownRenderCell,
    ): String = slice(chapter.canonicalText, cell.canonicalRange)

    /** 文本单元（非标题）在分页端的专用角色预期。 */
    private fun expectedTextRole(unit: MarkdownRenderUnit): BlockRole = when (unit.kind) {
        MarkdownRenderKind.PARAGRAPH ->
            if (unit.blockquoteDepth > 0) BlockRole.QUOTE else BlockRole.BODY

        MarkdownRenderKind.LIST_ITEM -> BlockRole.LIST_ITEM_BULLET
        MarkdownRenderKind.TASK_ITEM -> {
            val checked = (unit.content as MarkdownRenderContent.TaskItem).checked
            if (checked) BlockRole.TASK_ITEM_CHECKED else BlockRole.TASK_ITEM_UNCHECKED
        }

        else -> error("unreachable text role for ${unit.kind}")
    }

    /** 单元首个可见文本偏移：表格取首个非空单元格，分隔线无文本返回 null。 */
    private fun firstVisibleOffset(unit: MarkdownRenderUnit): Int? = when (unit.kind) {
        MarkdownRenderKind.TABLE -> {
            val table = unit.content as MarkdownRenderContent.Table
            (table.header + table.rows.flatten())
                .firstOrNull { !it.canonicalRange.isEmpty() }
                ?.canonicalRange
                ?.first
        }

        MarkdownRenderKind.HORIZONTAL_RULE -> null
        else -> unit.canonicalRange.takeIf { !it.isEmpty() }?.first
    }
}
