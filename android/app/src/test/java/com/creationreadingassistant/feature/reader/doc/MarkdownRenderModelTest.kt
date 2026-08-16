package com.creationreadingassistant.feature.reader.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯语义渲染模型（[MarkdownRenderModel]）的契约测试。
 *
 * 覆盖：嵌套列表 + 引用、任务列表、有序列表、表格（行列/对齐/单元格不重复）、
 * 行内 spans（plain/emphasis/strong/strike/code/link/image）、代码块语言、
 * 分隔线、空列表项，以及稳定 id / 不改变 parser 范围。
 */
class MarkdownRenderModelTest {

    // ── 嵌套列表 + 引用 ─────────────────────────────────────────────

    @Test
    fun `nested list inside blockquote flattens in reading order`() {
        val source = """
            > - 引用中的列表
            >   - 更深
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val units = MarkdownRenderModel.flatten(chapter)

        assertEquals(listOf("b0.q0.l0.c0", "b0.q0.l0.c1.l0.c0"), units.map { it.id })
        assertEquals(
            listOf(MarkdownRenderKind.LIST_ITEM, MarkdownRenderKind.LIST_ITEM),
            units.map { it.kind },
        )
        assertEquals(listOf(2, 3), units.map { it.depth })
        assertEquals(listOf(1, 1), units.map { it.blockquoteDepth })
        assertEquals(listOf("引用中的列表", "更深"), units.map { it.textOf(chapter) })
        assertEquals(listOf("• ", "• "), units.map { (it.content as MarkdownRenderContent.ListItem).marker })
    }

    @Test
    fun `blockquote after list keeps reading order and quote depth`() {
        val source = "- 列表项\n\n> 引用段落"
        val chapter = MarkdownParser.parse(source)
        val units = MarkdownRenderModel.flatten(chapter)

        assertEquals(2, units.size)
        assertEquals(MarkdownRenderKind.LIST_ITEM, units[0].kind)
        assertEquals(MarkdownRenderKind.PARAGRAPH, units[1].kind)
        assertEquals(listOf(1, 1), units.map { it.depth })
        assertEquals(listOf(0, 1), units.map { it.blockquoteDepth })
        assertEquals(listOf("列表项", "引用段落"), units.map { it.textOf(chapter) })
        assertEquals("• ", (units[0].content as MarkdownRenderContent.ListItem).marker)
    }

    @Test
    fun `units carry top block index for top-level spacing`() {
        val chapter = MarkdownParser.parse(
            """
            > 引用中的列表
            >   - 更深

            普通段落
            """.trimIndent(),
        )
        val units = MarkdownRenderModel.flatten(chapter)

        // 顶层块 0（引用）内的所有展开单元归属 0；顶层块 1（普通段落）归属 1。
        assertEquals(listOf(0, 0, 1), units.map { it.topBlockIndex })
        assertEquals(listOf("b0.q0", "b0.q1.l0.c0", "b1"), units.map { it.id })
    }

    @Test
    fun `list item continuation paragraph has no marker`() {
        val source = "- 第一段\n\n  第二段"
        val chapter = MarkdownParser.parse(source)
        val units = MarkdownRenderModel.flatten(chapter)

        assertEquals(2, units.size)
        assertEquals(MarkdownRenderKind.LIST_ITEM, units[0].kind)
        assertEquals(MarkdownRenderKind.PARAGRAPH, units[1].kind)
        assertEquals("• ", (units[0].content as MarkdownRenderContent.ListItem).marker)
        assertEquals(1, units[0].depth)
        assertEquals(1, units[1].depth)
        assertEquals(listOf("第一段", "第二段"), units.map { it.textOf(chapter) })
    }

    // ── 任务列表 ────────────────────────────────────────────────────

    @Test
    fun `task list units carry checked state`() {
        val source = "- [x] 完成\n- [ ] 未完成"
        val chapter = MarkdownParser.parse(source)
        val units = MarkdownRenderModel.flatten(chapter)

        assertEquals(
            listOf(MarkdownRenderKind.TASK_ITEM, MarkdownRenderKind.TASK_ITEM),
            units.map { it.kind },
        )
        assertEquals(listOf(true, false), units.map { (it.content as MarkdownRenderContent.TaskItem).checked })
        assertEquals(listOf(1, 1), units.map { it.depth })
        assertEquals(listOf("完成", "未完成"), units.map { it.textOf(chapter) })
        assertEquals(listOf("b0.t0.c0", "b0.t1.c0"), units.map { it.id })
    }

    // ── 有序列表 ────────────────────────────────────────────────────

    @Test
    fun `ordered list units carry marker and ordinal`() {
        val source = "3. 第三\n4. 第四"
        val chapter = MarkdownParser.parse(source)
        val units = MarkdownRenderModel.flatten(chapter)

        assertEquals(
            listOf(MarkdownRenderKind.LIST_ITEM, MarkdownRenderKind.LIST_ITEM),
            units.map { it.kind },
        )
        val first = units[0].content as MarkdownRenderContent.ListItem
        val second = units[1].content as MarkdownRenderContent.ListItem
        assertEquals("3. ", first.marker)
        assertEquals(3, first.ordinal)
        assertEquals("4. ", second.marker)
        assertEquals(4, second.ordinal)
    }

    // ── 表格 ────────────────────────────────────────────────────────

    @Test
    fun `table flattens to one unit with row column alignment and no duplicated cells`() {
        val source = """
            | 左 | 中 | 右 |
            |:---|:---:|---:|
            | A | B | C |
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val units = MarkdownRenderModel.flatten(chapter)

        assertEquals(1, units.size)
        val unit = units.single()
        assertEquals(MarkdownRenderKind.TABLE, unit.kind)
        assertEquals(0, unit.depth)
        assertEquals(0, unit.blockquoteDepth)

        val table = unit.content as MarkdownRenderContent.Table
        assertEquals(3, table.header.size)
        assertEquals(1, table.rows.size)
        assertEquals(3, table.rows[0].size)

        assertEquals(
            listOf(MarkdownRenderAlignment.LEFT, MarkdownRenderAlignment.CENTER, MarkdownRenderAlignment.RIGHT),
            table.header.map { it.alignment },
        )
        assertEquals(listOf(0, 0, 0), table.header.map { it.rowIndex })
        assertEquals(listOf(0, 1, 2), table.header.map { it.columnIndex })
        assertEquals(listOf(1, 1, 1), table.rows[0].map { it.rowIndex })
        assertEquals(listOf(0, 1, 2), table.rows[0].map { it.columnIndex })
        // GFM 表格按列对齐：数据行单元格继承所在列的对齐
        assertEquals(
            listOf(MarkdownRenderAlignment.LEFT, MarkdownRenderAlignment.CENTER, MarkdownRenderAlignment.RIGHT),
            table.rows[0].map { it.alignment },
        )

        assertEquals(listOf("左", "中", "右"), table.header.map { it.cellTextOf(chapter) })
        assertEquals(listOf("A", "B", "C"), table.rows[0].map { it.cellTextOf(chapter) })

        // 单元格不重复：所有 cell 的 canonicalRange 互不重叠且都在表格单元范围内
        val cells = table.header + table.rows.flatten()
        assertEquals(6, cells.size)
        assertEquals(6, cells.map { it.canonicalRange }.toSet().size)
        for (cell in cells) {
            assertTrue("单元格范围应在表格范围内", cell.canonicalRange.first >= unit.canonicalRange.first)
            assertTrue("单元格范围应在表格范围内", cell.canonicalRange.last <= unit.canonicalRange.last)
        }
        val sorted = cells.map { it.canonicalRange }.sortedBy { it.first }
        for (i in 1 until sorted.size) {
            assertTrue("单元格范围不应重叠", sorted[i - 1].last < sorted[i].first)
        }
    }

    // ── 行内 spans：链接 / 图片 / 样式 ──────────────────────────────

    @Test
    fun `inline spans express plain emphasis strong strike code link and image`() {
        val source =
            "**粗体** *斜体* ~~删除~~ `代码` [链接](https://example.com \"标题\") ![图片](img.png \"图题\")"
        val chapter = MarkdownParser.parse(source)
        val units = MarkdownRenderModel.flatten(chapter)

        assertEquals(1, units.size)
        val unit = units.single()
        assertEquals(MarkdownRenderKind.PARAGRAPH, unit.kind)
        val spans = (unit.content as MarkdownRenderContent.Paragraph).spans
        val canonical = chapter.canonicalText

        fun textOf(span: MarkdownRenderSpan): String =
            canonical.substring(unit.canonicalRange.first + span.start, unit.canonicalRange.first + span.end)

        val strong = spans.filterIsInstance<MarkdownRenderSpan.Strong>().single()
        assertEquals("粗体", textOf(strong))
        val emphasis = spans.filterIsInstance<MarkdownRenderSpan.Emphasis>().single()
        assertEquals("斜体", textOf(emphasis))
        val strike = spans.filterIsInstance<MarkdownRenderSpan.Strikethrough>().single()
        assertEquals("删除", textOf(strike))
        val code = spans.filterIsInstance<MarkdownRenderSpan.Code>().single()
        assertEquals("代码", textOf(code))

        val link = spans.filterIsInstance<MarkdownRenderSpan.Link>().single()
        assertEquals("https://example.com", link.destination)
        assertEquals("标题", link.title)
        assertEquals("链接", textOf(link))

        val image = spans.filterIsInstance<MarkdownRenderSpan.Image>().single()
        assertEquals("img.png", image.source)
        assertEquals("图题", image.title)
        assertEquals("图片", image.alt)
        assertEquals("图片", textOf(image))

        // 叶 spans（plain/code/image）按阅读顺序无缝覆盖整个单元文本，且不重叠
        val leaves = spans.filter {
            it is MarkdownRenderSpan.Plain || it is MarkdownRenderSpan.Code || it is MarkdownRenderSpan.Image
        }
        var cursor = 0
        for (leaf in leaves) {
            assertEquals("叶 spans 应连续无空洞", cursor, leaf.start)
            cursor = leaf.end
        }
        assertEquals(unit.canonicalRange.last + 1 - unit.canonicalRange.first, cursor)
    }

    // ── 代码块 ──────────────────────────────────────────────────────

    @Test
    fun `code blocks carry language`() {
        val source = """
            ```kotlin
            val x = 1
            ```

                val y = 2
        """.trimIndent()
        val chapter = MarkdownParser.parse(source)
        val units = MarkdownRenderModel.flatten(chapter)

        assertEquals(
            listOf(MarkdownRenderKind.CODE_BLOCK, MarkdownRenderKind.CODE_BLOCK),
            units.map { it.kind },
        )
        assertEquals("kotlin", (units[0].content as MarkdownRenderContent.CodeBlock).language)
        assertNull((units[1].content as MarkdownRenderContent.CodeBlock).language)
        assertTrue(units[0].textOf(chapter).contains("val x = 1"))
        assertTrue(units[1].textOf(chapter).contains("val y = 2"))
    }

    // ── 分隔线 ──────────────────────────────────────────────────────

    @Test
    fun `horizontal rule is a textless structure unit`() {
        val chapter = MarkdownParser.parse("---")
        val units = MarkdownRenderModel.flatten(chapter)

        assertEquals(1, units.size)
        assertEquals(MarkdownRenderKind.HORIZONTAL_RULE, units.single().kind)
        assertTrue(units.single().content is MarkdownRenderContent.HorizontalRule)
        assertFalse("分隔线单元不应有行内 spans 语义", units.single().content is MarkdownRenderContent.Paragraph)
    }

    // ── 空列表项 ────────────────────────────────────────────────────

    @Test
    fun `empty list item produces no unit`() {
        val chapter = MarkdownParser.parse("- ")
        assertEquals(0, MarkdownRenderModel.flatten(chapter).size)
    }

    // ── 稳定 id / 范围 ──────────────────────────────────────────────

    @Test
    fun `flattened units keep parser ranges and stable ids`() {
        val source = """
            # 标题

            段落 **粗体** 与 [链接](https://example.com)。

            > 引用

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

        // id 唯一且 index 连续
        assertEquals(units.size, units.map { it.id }.toSet().size)
        assertEquals(units.indices.toList(), units.map { it.index })

        // 每个非空单元的范围必须精确等于解析树中某个叶块的范围（不改变 offset）
        val leaves = leafBlocks(chapter.blocks)
        for (unit in units) {
            if (unit.canonicalRange.isEmpty()) continue
            val matched = leaves.any {
                it.canonicalRange == unit.canonicalRange && it.sourceRange == unit.sourceRange
            }
            assertTrue("单元 ${unit.id} 的范围应来自解析树叶块", matched)
        }

        // 单元按阅读顺序出现（表格与分隔线是结构单元，文本另行验证）
        val expectedTexts = listOf("标题", "段落 粗体 与 链接。", "引用", "项目", "子项目", "任务", "print(\"hi\")\n")
        assertEquals(expectedTexts, units.take(expectedTexts.size).map { it.textOf(chapter) })
        assertEquals(MarkdownRenderKind.TABLE, units[7].kind)
        assertEquals(MarkdownRenderKind.HORIZONTAL_RULE, units[8].kind)
    }

    @Test
    fun `flatten is deterministic across parses`() {
        val source = """
            # 标题

            - [ ] 任务

            > 引用

            | A | B |
            |---|---|
            | 1 | 2 |
        """.trimIndent()
        val first = MarkdownRenderModel.flatten(MarkdownParser.parse(source))
        val second = MarkdownRenderModel.flatten(MarkdownParser.parse(source))
        assertEquals(first, second)
        assertEquals(first.map { it.id }, second.map { it.id })
    }

    // ── 辅助 ────────────────────────────────────────────────────────

    private fun MarkdownRenderUnit.textOf(chapter: MarkdownParser.MarkdownChapter): String =
        chapter.canonicalText.substring(canonicalRange.first, canonicalRange.last + 1)

    private fun MarkdownRenderCell.cellTextOf(chapter: MarkdownParser.MarkdownChapter): String =
        chapter.canonicalText.substring(canonicalRange.first, canonicalRange.last + 1)

    private fun leafBlocks(blocks: List<MarkdownBlock>): List<MarkdownBlock> {
        val out = mutableListOf<MarkdownBlock>()
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.BlockQuote -> out += leafBlocks(block.blocks)
                is MarkdownBlock.UnorderedList -> block.items.forEach { out += leafBlocks(it) }
                is MarkdownBlock.OrderedList -> block.items.forEach { out += leafBlocks(it) }
                is MarkdownBlock.TaskList -> block.items.forEach { out += leafBlocks(it.blocks) }
                else -> out += block
            }
        }
        return out
    }
}
