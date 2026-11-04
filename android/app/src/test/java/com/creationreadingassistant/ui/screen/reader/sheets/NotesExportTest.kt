package com.creationreadingassistant.ui.screen.reader.sheets

import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import java.time.LocalDateTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 锁定阅读笔记导出 Markdown 的结构：书摘按章节分组（含批注）、笔记与书签按 kind
 * 拆分、灵感独立成节、空节整体省略；文件名对非法字符做净化。
 *
 * 来源补全（R5-I2 导出缺口）：灵感条目按 payload.source / payload.excerpts 逐条输出
 * 来源快照与 locator 信息；正文与原文摘录分区；来源重复按稳定规则去重；
 * 旧 payload / 缺可选字段维持既有行为；解析失败快速失败。
 */
class NotesExportTest {

    private val exportedAt = LocalDateTime.of(2026, 8, 16, 9, 30)

    private fun highlight(
        id: String,
        text: String,
        chapter: String? = null,
        note: String? = null,
        locatorJson: String? = null,
    ) = HighlightEntity(
        id = id, book_id = "b1", text = text, note = note,
        chapter_title = chapter, locator_json = locatorJson,
        created_at = "2026-08-01T00:00:00Z", updated_at = "2026-08-01T00:00:00Z",
    )

    private fun note(
        id: String,
        title: String,
        body: String = "",
        excerpt: String? = null,
        kind: String = "note",
    ) = NoteEntity(
        id = id, book_id = "b1", title = title, body = body, excerpt = excerpt,
        kind = kind, created_at = "2026-08-01T00:00:00Z", updated_at = "2026-08-01T00:00:00Z",
    )

    private fun inspiration(id: String, title: String, body: String) = InspirationEntity(
        id = id, title = title, body = body,
        created_at = "2026-08-01T00:00:00Z", updated_at = "2026-08-01T00:00:00Z",
    )

    private fun inspirationWithPayload(
        id: String,
        title: String,
        body: String,
        payload: String?,
    ) = InspirationEntity(
        id = id, title = title, body = body, payload = payload,
        created_at = "2026-08-01T00:00:00Z", updated_at = "2026-08-01T00:00:00Z",
    )

    private fun payload(
        source: InspirationSourceInfo? = null,
        excerpts: List<InspirationSourceInfo> = emptyList(),
        tags: List<String> = emptyList(),
    ): String = Json.encodeToString(
        InspirationPayloadData.serializer(),
        InspirationPayloadData(source = source, excerpts = excerpts, tags = tags),
    )

    @Test
    fun `full export contains all four sections with counts and grouping`() {
        val md = buildNotesExportMarkdown(
            bookTitle = "测试书",
            highlights = listOf(
                highlight("h1", "第一句", chapter = "第二章", locatorJson = """{"v":2,"offset":100,"ci":1,"co":10}"""),
                highlight("h2", "第二句", chapter = "第一章", locatorJson = """{"v":2,"offset":0,"ci":0,"co":5}"""),
                highlight("h3", "带批注的句子", chapter = "第一章", note = "这里写得好", locatorJson = """{"v":2,"offset":10,"ci":0,"co":80}"""),
                highlight("h4", "无章节的句子"),
            ),
            notes = listOf(
                note("n1", "伏笔", body = "注意主角的怀表", excerpt = "他摸了摸怀表"),
                note("bm1", "第三章开头", excerpt = "三年后", kind = "bookmark"),
            ),
            inspirations = listOf(inspiration("i1", "如果", "如果怀表是倒着走的呢？")),
            exportedAt = exportedAt,
        )

        assertTrue(md.startsWith("# 《测试书》阅读笔记"))
        assertTrue(md.contains("> 导出于 2026-08-16 09:30"))

        // 书摘：计数 + 章节按文档序分组（locator ci 升序）+ 批注缩进 + 无 locator 归「未分类」殿后
        assertTrue(md.contains("## 书摘（共 4 条）"))
        val ch1 = md.indexOf("### 第一章")
        val ch2 = md.indexOf("### 第二章")
        val unclassified = md.indexOf("### 未分类")
        assertTrue(ch1 in 0 until ch2) // ci=0 的第一章先于 ci=1 的第二章（非中文标题字典序）
        assertTrue(ch2 in 0 until unclassified) // 无 locator 的章节组排最后
        assertTrue(md.contains("1. 第二句"))
        assertTrue(md.contains("2. 带批注的句子"))
        assertTrue(md.contains("   批注：这里写得好"))
        assertTrue(md.contains("### 未分类"))
        assertTrue(md.contains("1. 无章节的句子"))

        // 笔记与书签按 kind 拆分
        assertTrue(md.contains("## 笔记（共 1 条）"))
        assertTrue(md.contains("- **伏笔**：注意主角的怀表"))
        assertTrue(md.contains("  > 摘录：他摸了摸怀表"))
        assertTrue(md.contains("## 书签（共 1 条）"))
        assertTrue(md.contains("- 第三章开头"))
        assertFalse(md.contains("**第三章开头**")) // 书签不进笔记节

        // 灵感
        assertTrue(md.contains("## 灵感（共 1 条）"))
        assertTrue(md.contains("### 如果"))
        assertTrue(md.contains("如果怀表是倒着走的呢？"))
    }

    @Test
    fun `empty sections are omitted`() {
        val md = buildNotesExportMarkdown(
            bookTitle = "只有笔记",
            highlights = emptyList(),
            notes = listOf(note("n1", "一条", body = "内容")),
            inspirations = emptyList(),
            exportedAt = exportedAt,
        )
        assertTrue(md.contains("## 笔记（共 1 条）"))
        assertFalse(md.contains("书摘"))
        assertFalse(md.contains("书签"))
        assertFalse(md.contains("灵感"))
    }

    @Test
    fun `blank book title falls back to 未命名`() {
        val md = buildNotesExportMarkdown(
            bookTitle = "",
            highlights = emptyList(),
            notes = emptyList(),
            inspirations = emptyList(),
            exportedAt = exportedAt,
        )
        assertTrue(md.startsWith("# 《未命名》阅读笔记"))
    }

    @Test
    fun `export file name sanitizes illegal characters`() {
        val name = notesExportFileName("测书名/含:非*法?\"字符<>|", exportedAt)
        assertTrue(name.endsWith(".md"))
        assertFalse(name.any { it in setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|') })
        assertTrue(name.startsWith("《测"))
        assertTrue(name.contains("20260816-0930"))
    }

    @Test
    fun `blank title file name falls back`() {
        val name = notesExportFileName("  ", exportedAt)
        assertTrue(name.startsWith("《未命名》笔记-"))
    }

    // ============================== 灵感来源补全（原缺口） ==============================

    private val sourceA = InspirationSourceInfo(
        bookId = "b1",
        bookTitle = "测试 TXT",
        bookAuthor = "测试作者",
        chapterTitle = "第二章 转折",
        progressPercent = 42.5f,
        excerpt = "他摸了摸怀表。",
        locatorJson = """{"v":2,"offset":1200,"ci":1,"co":80}""",
    )

    @Test
    fun `single source inspiration exports book author chapter excerpt and locator`() {
        val md = buildNotesExportMarkdown(
            bookTitle = "测试书",
            highlights = emptyList(),
            notes = emptyList(),
            inspirations = listOf(
                inspirationWithPayload("i1", "怀表", "如果怀表是倒着走的呢？", payload(source = sourceA)),
            ),
            exportedAt = exportedAt,
        )

        val insp = md.substringAfter("## 灵感（共 1 条）")
        // 个人正文与原文摘录分区
        assertTrue(insp.contains("### 怀表"))
        assertTrue(insp.contains("如果怀表是倒着走的呢？"))
        assertTrue(insp.contains("> 摘录：他摸了摸怀表。"))
        // 来源快照逐字段输出（原书缺失也来自 payload，不查书库）
        assertTrue(insp.contains("来源：《测试 TXT》 · 测试作者 · 第二章 转折"))
        // locator 原始信息原样保留（不伪造可点击链接）
        assertTrue(insp.contains(""""offset":1200"""))
        assertTrue(insp.contains(""""ci":1,"co":80"""))
    }

    @Test
    fun `multi source excerpts keep order and dedupe repeats of first source`() {
        val second = InspirationSourceInfo(
            bookId = "b2",
            bookTitle = "测试 EPUB",
            bookAuthor = null,
            chapterTitle = "第三章",
            progressPercent = null,
            excerpt = "第二本书的句子。",
            locatorJson = """{"v":2,"ci":2,"co":15}""",
        )
        val md = buildNotesExportMarkdown(
            bookTitle = "测试书",
            highlights = emptyList(),
            notes = emptyList(),
            inspirations = listOf(
                inspirationWithPayload(
                    "i2", "双来源", "两本书的写法对比",
                    payload(source = sourceA, excerpts = listOf(sourceA, second, sourceA)),
                ),
            ),
            exportedAt = exportedAt,
        )

        val insp = md.substringAfter("## 灵感（共 1 条）")
        // 顺序保留：A 先于 B
        val posA = insp.indexOf("来源：《测试 TXT》")
        val posB = insp.indexOf("来源：《测试 EPUB》")
        assertTrue(posA in 0 until posB)
        // source 与 excerpts 中重复的 A 条目只出现一次（正文/其他字段不受影响）
        assertEquals(1, Regex("""来源：《测试 TXT》""").findAll(insp).count())
        assertEquals(1, Regex("""来源：《测试 EPUB》""").findAll(insp).count())
        assertTrue(insp.contains("> 摘录：他摸了摸怀表。"))
        assertTrue(insp.contains("> 摘录：第二本书的句子。"))
        // 第二来源无作者：不输出「null」或空作者段
        assertFalse(insp.contains("来源：《测试 EPUB》 · null"))
    }

    @Test
    fun `personal body and excerpt stay separate`() {
        val md = buildNotesExportMarkdown(
            bookTitle = "测试书",
            highlights = emptyList(),
            notes = emptyList(),
            inspirations = listOf(
                inspirationWithPayload("i1", "怀表", "我自己的想法", payload(source = sourceA)),
            ),
            exportedAt = exportedAt,
        )
        val insp = md.substringAfter("### 怀表")
        val bodyPos = insp.indexOf("我自己的想法")
        val excerptPos = insp.indexOf("> 摘录：他摸了摸怀表。")
        assertTrue(bodyPos in 0 until excerptPos)
        // 想法行不是摘录引用块
        assertFalse(insp.contains("> 我自己的想法"))
    }

    @Test
    fun `legacy payload without source keeps existing behavior`() {
        val md = buildNotesExportMarkdown(
            bookTitle = "测试书",
            highlights = emptyList(),
            notes = emptyList(),
            inspirations = listOf(
                inspirationWithPayload("i1", "旧灵感", "旧正文", payload = "{}"),
                inspiration("i2", "裸灵感", "无payload正文"),
            ),
            exportedAt = exportedAt,
        )
        val insp = md.substringAfter("## 灵感（共 2 条）")
        assertTrue(insp.contains("### 旧灵感"))
        assertTrue(insp.contains("旧正文"))
        assertTrue(insp.contains("### 裸灵感"))
        assertTrue(insp.contains("无payload正文"))
        assertFalse(insp.contains("来源："))
        assertFalse(insp.contains("> 摘录："))
    }

    @Test
    fun `corrupted payload fails fast instead of silent export`() {
        try {
            buildNotesExportMarkdown(
                bookTitle = "测试书",
                highlights = emptyList(),
                notes = emptyList(),
                inspirations = listOf(
                    inspirationWithPayload("i1", "坏灵感", "正文", payload = """{"source": """),
                ),
                exportedAt = exportedAt,
            )
            fail("损坏 payload 必须抛出，不能静默导出不完整来源")
        } catch (expected: SerializationException) {
            assertTrue(expected.message!!.contains("i1"))
            assertTrue(expected.message!!.contains("坏灵感"))
        }
    }

    @Test
    fun `multiline and markdown special characters stay verbatim`() {
        val multiline = InspirationSourceInfo(
            bookId = "b1",
            bookTitle = "测试 TXT",
            bookAuthor = null,
            chapterTitle = "第一章",
            progressPercent = null,
            excerpt = "第一行*强调*\n> 引用符号\n第三行#井号",
            locatorJson = null,
        )
        val md = buildNotesExportMarkdown(
            bookTitle = "测试书",
            highlights = emptyList(),
            notes = emptyList(),
            inspirations = listOf(
                inspirationWithPayload("i1", "多行", "正文", payload(source = multiline)),
            ),
            exportedAt = exportedAt,
        )
        val insp = md.substringAfter("## 灵感（共 1 条）")
        assertTrue(insp.contains("第一行*强调*"))
        assertTrue(insp.contains("> 引用符号"))
        assertTrue(insp.contains("第三行#井号"))
    }

    @Test
    fun `inspiration without excerpt but with locator still exports source line`() {
        val noExcerpt = InspirationSourceInfo(
            bookId = "b1",
            bookTitle = "测试 TXT",
            bookAuthor = null,
            chapterTitle = "第一章",
            progressPercent = 10f,
            excerpt = null,
            locatorJson = """{"v":2,"offset":5}""",
        )
        val md = buildNotesExportMarkdown(
            bookTitle = "测试书",
            highlights = emptyList(),
            notes = emptyList(),
            inspirations = listOf(
                inspirationWithPayload("i1", "无摘录", "只有想法", payload(source = noExcerpt)),
            ),
            exportedAt = exportedAt,
        )
        val insp = md.substringAfter("## 灵感（共 1 条）")
        assertTrue(insp.contains("来源：《测试 TXT》 · 第一章"))
        assertTrue(insp.contains(""""offset":5"""))
        assertFalse(insp.contains("> 摘录："))
    }

    @Test
    fun `different sources with null position differences or locationLabel differences are not deduplicated`() {
        // 反例1：字段在不同槽位（bookAuthor vs chapterTitle），旧实现用 listOfNotNull 拼出相同键导致误去重
        val s1 = InspirationSourceInfo(bookId = "b1", bookAuthor = "测试", chapterTitle = null)
        val s2 = InspirationSourceInfo(bookId = "b1", bookAuthor = null, chapterTitle = "测试")
        // 反例2：locationLabel 差异，旧实现 dedupeKey 漏掉 locationLabel 导致不同位置的来源被误合并
        val s3 = InspirationSourceInfo(bookId = "b1", locationLabel = "第1页")
        val s4 = InspirationSourceInfo(bookId = "b1", locationLabel = "第2页")
        val md = buildNotesExportMarkdown(
            bookTitle = "测试书",
            highlights = emptyList(),
            notes = emptyList(),
            inspirations = listOf(
                inspirationWithPayload("i1", "不同来源", "正文", payload(source = s1, excerpts = listOf(s2, s3, s4))),
            ),
            exportedAt = exportedAt,
        )
        val insp = md.substringAfter("## 灵感（共 1 条）")
        assertEquals(4, Regex("""来源：""").findAll(insp).count())
        assertTrue(insp.contains("来源：未记录书名 · 第1页"))
        assertTrue(insp.contains("来源：未记录书名 · 第2页"))
    }

    @Test
    fun `locationLabel and progressPercent are exported from snapshot`() {
        val srcWithLoc = InspirationSourceInfo(
            bookId = "b1",
            bookTitle = "测试书",
            bookAuthor = "作者",
            chapterTitle = "第三章",
            locationLabel = "第42页",
            progressPercent = 50.0f,
            locatorJson = """{"v":2,"ci":3}""",
        )
        val md = buildNotesExportMarkdown(
            bookTitle = "测试书",
            highlights = emptyList(),
            notes = emptyList(),
            inspirations = listOf(
                inspirationWithPayload("i1", "带位置", "正文", payload(source = srcWithLoc)),
            ),
            exportedAt = exportedAt,
        )
        val insp = md.substringAfter("## 灵感（共 1 条）")
        assertTrue(insp.contains("来源：《测试书》 · 作者 · 第三章 · 第42页 · 50.0%"))
        assertTrue(insp.contains("位置：{\"v\":2,\"ci\":3}"))
    }

    @Test
    fun `multiline excerpt structurally maintains quote blocks for all lines`() {
        val multiline = InspirationSourceInfo(
            bookId = "b1",
            bookTitle = "测试 TXT",
            excerpt = "第一行*强调*\n> 已有引用符号\n\n第四行#井号",
        )
        val md = buildNotesExportMarkdown(
            bookTitle = "测试书",
            highlights = emptyList(),
            notes = emptyList(),
            inspirations = listOf(
                inspirationWithPayload("i1", "多行结构", "正文", payload(source = multiline)),
            ),
            exportedAt = exportedAt,
        )
        val insp = md.substringAfter("## 灵感（共 1 条）")
        val sourceSection = insp.substringAfter("来源：《测试 TXT》").trim()
        val excerptLines = sourceSection.lines().filter { it.isNotBlank() }
        // 结构断言：摘录区的所有非空行必须全部以 ">" 开头，不能脱离引用块
        assertTrue("摘录区应包含至少4行非空行", excerptLines.size >= 4)
        excerptLines.forEachIndexed { idx, line ->
            assertTrue("第 ${idx + 1} 行应在引用块内（以 > 开头），实际为: $line", line.startsWith(">"))
        }
    }

    @Test
    fun `identical sources are deduplicated preserving first occurrence order`() {
        val sA = InspirationSourceInfo(bookId = "b1", bookTitle = "书A", excerpt = "摘录A")
        val sB = InspirationSourceInfo(bookId = "b2", bookTitle = "书B", excerpt = "摘录B")
        val md = buildNotesExportMarkdown(
            bookTitle = "测试书",
            highlights = emptyList(),
            notes = emptyList(),
            inspirations = listOf(
                inspirationWithPayload("i1", "去重", "正文", payload(source = sA, excerpts = listOf(sB, sA, sB))),
            ),
            exportedAt = exportedAt,
        )
        val insp = md.substringAfter("## 灵感（共 1 条）")
        assertEquals(1, Regex("""来源：《书A》""").findAll(insp).count())
        assertEquals(1, Regex("""来源：《书B》""").findAll(insp).count())
        val posA = insp.indexOf("来源：《书A》")
        val posB = insp.indexOf("来源：《书B》")
        assertTrue(posA in 0 until posB)
    }

    // ============================== 93-v1-B：标签导出 ==============================

    @Test
    fun `tags are exported as one independent line with stable first occurrence order`() {
        val md = buildNotesExportMarkdown(
            bookTitle = "测试书",
            highlights = emptyList(),
            notes = emptyList(),
            inspirations = listOf(
                inspirationWithPayload(
                    "i1", "带标签", "正文内容",
                    payload(tags = listOf("人物", "伏笔", "人物", "  ", " 节奏 ")),
                ),
            ),
            exportedAt = exportedAt,
        )
        val insp = md.substringAfter("## 灵感（共 1 条）")
        // 独立一行；空白标签丢弃、重复标签只保留首次出现、首尾空白被规范
        assertTrue(insp.contains("标签：人物、伏笔、节奏"))
        assertEquals(1, Regex("""标签：""").findAll(insp).count())
        assertEquals(1, Regex("""人物""").findAll(insp).count())
        // 顺序按首次出现
        val pos人物 = insp.indexOf("人物")
        val pos伏笔 = insp.indexOf("伏笔")
        val pos节奏 = insp.indexOf("节奏")
        assertTrue(pos人物 in 0 until pos伏笔)
        assertTrue(pos伏笔 in 0 until pos节奏)
        // 标签行与正文分区：正文仍在标签之前，且未被改写
        assertTrue(insp.indexOf("正文内容") in 0 until pos人物)
    }

    @Test
    fun `inspiration without tags adds no tag line or blank heading`() {
        val md = buildNotesExportMarkdown(
            bookTitle = "测试书",
            highlights = emptyList(),
            notes = emptyList(),
            inspirations = listOf(
                inspirationWithPayload("i1", "无标签", "正文", payload(tags = emptyList())),
                inspirationWithPayload("i2", "空标签", "正文", payload(tags = listOf("", "   ", "\n\t"))),
                inspiration("i3", "裸灵感", "无payload"),
            ),
            exportedAt = exportedAt,
        )
        val insp = md.substringAfter("## 灵感（共 3 条）")
        assertFalse("无标签时不得输出标签行", insp.contains("标签："))
        // 也不产生空标题或额外空节
        assertFalse(insp.contains("## 标签"))
    }

    @Test
    fun `multiline tags fold to single line and markdown specials are escaped`() {
        val md = buildNotesExportMarkdown(
            bookTitle = "测试书",
            highlights = emptyList(),
            notes = emptyList(),
            inspirations = listOf(
                inspirationWithPayload(
                    "i1", "特殊标签", "正文",
                    payload(
                        tags = listOf(
                            "第一行\n第二行",
                            "*强调*_下划线_[链接](x)",
                            "`代码`<html>~删除~",
                        ),
                    ),
                ),
            ),
            exportedAt = exportedAt,
        )
        val insp = md.substringAfter("## 灵感（共 1 条）")
        val tagLine = insp.lines().single { it.startsWith("标签：") }
        // 多行标签折成单行，不破坏段落结构
        assertTrue(tagLine.contains("第一行 第二行"))
        assertFalse(tagLine.contains("\n"))
        // 转义只作用于标签内容
        assertTrue(tagLine.contains("""\*强调\*\_下划线\_\[链接\](x)"""))
        assertTrue(tagLine.contains("""\`代码\`\<html\>\~删除\~"""))
        // 正文未被全局转义
        assertTrue(insp.contains("正文"))
        assertFalse(insp.contains("""\正"""))
    }

    @Test
    fun `tags coexist with body multiline excerpt and full source without rewriting them`() {
        val multiline = InspirationSourceInfo(
            bookId = "b1",
            bookTitle = "测试 TXT",
            bookAuthor = "测试作者",
            chapterTitle = "第二章",
            progressPercent = 42.5f,
            excerpt = "第一行*强调*\n> 引用符号\n第三行#井号",
            locatorJson = """{"v":2,"offset":1200,"ci":1,"co":80}""",
        )
        val md = buildNotesExportMarkdown(
            bookTitle = "测试书",
            highlights = emptyList(),
            notes = emptyList(),
            inspirations = listOf(
                inspirationWithPayload(
                    "i1", "完整条目", "我自己的想法\n第二行想法",
                    payload(source = multiline, tags = listOf("人物", "伏笔")),
                ),
            ),
            exportedAt = exportedAt,
        )
        val insp = md.substringAfter("## 灵感（共 1 条）")
        val bodyPos = insp.indexOf("我自己的想法")
        val tagPos = insp.indexOf("标签：人物、伏笔")
        val sourcePos = insp.indexOf("来源：《测试 TXT》")
        assertTrue("正文应在标签之前", bodyPos in 0 until tagPos)
        assertTrue("标签应在来源之前", tagPos in 0 until sourcePos)
        // 正文、多行摘录、来源、locator 保持原样，不被标签转义影响
        assertTrue(insp.contains("我自己的想法\n第二行想法"))
        assertTrue(insp.contains("> 摘录：第一行*强调*"))
        assertTrue(insp.contains("> 引用符号"))
        assertTrue(insp.contains("> 第三行#井号"))
        assertTrue(insp.contains("来源：《测试 TXT》 · 测试作者 · 第二章 · 42.5%"))
        assertTrue(insp.contains(""""offset":1200"""))
    }

    @Test
    fun `corrupted payload with tags still fails fast`() {
        try {
            buildNotesExportMarkdown(
                bookTitle = "测试书",
                highlights = emptyList(),
                notes = emptyList(),
                inspirations = listOf(
                    inspirationWithPayload(
                        "i1", "坏灵感", "正文",
                        payload = """{"tags":["人物"],"source": """,
                    ),
                ),
                exportedAt = exportedAt,
            )
            fail("损坏 payload 必须抛出，不能因为标签存在就静默导出")
        } catch (expected: SerializationException) {
            assertTrue(expected.message!!.contains("i1"))
        }
    }
}
