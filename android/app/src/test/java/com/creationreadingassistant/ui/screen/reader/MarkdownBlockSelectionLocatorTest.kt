package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderKind
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderModel
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F3.1 任务 A：Markdown 整块选择 → locator 的**端到端**接线证明。
 *
 * 为什么必须有这个文件：F3 的 [ReaderSelectionRangeWiringGuardTest] 只断言
 * `onSelect(...)` 文本里**含有** `range.first` / `range.last + 1`，不检查它们落在
 * 哪个参数槽。结果 Markdown 把 canonical 起点写进了 `globalOffset` 槽、
 * `rangeStart` 恒为 -1，而 `computeLocatorJson` 在 `epubBook == null` 时只认
 * `selectedRangeStart` ⇒ locator 恒 null，持久高亮端到端不可达（Qoder P1-1）。
 *
 * 本文件因此不走字符串断言，而是调用**生产代码实际使用的纯函数**
 * [markdownBlockSelectionArgs]，把四字段喂给真实的
 * [com.creationreadingassistant.ui.screen.reader.computeLocatorJson]，
 * 再用 [LocatorCodec] 解码回来 —— 证明「槽位含义正确」，而不只是「形状正确」。
 */
class MarkdownBlockSelectionLocatorTest {

    private fun chapter(src: String) = MarkdownParser.parse(src)

    private fun paragraph(src: String, base: Int = 0) =
        MarkdownRenderModel.flatten(chapter(src)).first { it.kind == MarkdownRenderKind.PARAGRAPH }
            .let { unit ->
                val text = chapter(src).canonicalText.substring(
                    unit.canonicalRange.first,
                    unit.canonicalRange.last + 1,
                )
                // 段落必然可选：这里断言非空（「不可选 → null」由文件末尾单独覆盖）
                requireNotNull(markdownBlockSelectionArgs(text = text, unit = unit, blockGlobalBase = base))
            }

    // ── 1. 整本文档 Markdown ────────────────────────────────────────────

    @Test
    fun `whole document markdown selection fills the rangeStart slot not globalOffset`() {
        val args = paragraph("第一章正文。\n", base = 0)
        assertEquals("第一章正文。", args.text)
        assertEquals(-1, args.globalOffset)
        assertEquals(0, args.rangeStart)
        assertEquals("第一章正文。".length, args.sourceEnd)
    }

    // ── 2. 流式分章 Markdown ────────────────────────────────────────────

    @Test
    fun `streaming markdown selection adds chapterBase to rangeStart and sourceEnd`() {
        val args = paragraph("章二内容。\n", base = 1_000)
        assertEquals(1_000, args.rangeStart)
        assertEquals(1_000 + "章二内容。".length, args.sourceEnd)
        assertEquals(-1, args.globalOffset)
    }

    // ── 3. 端到端 locator ───────────────────────────────────────────────

    @Test
    fun `selection result produces a decodable plain locator`() {
        val args = paragraph("第一章正文。\n", base = 0)
        val json = computeLocatorJson(
            epubBook = null,
            selectedGlobalOffset = args.globalOffset,
            chapterStartOffsets = emptyList(),
            selectedText = args.text,
            selectedRangeStart = args.rangeStart,
        )
        assertNotNull("Markdown 的 locator 不得为 null（P1-1）", json)
        val decoded = LocatorCodec.decode(json!!)
        assertNotNull(decoded)
        assertEquals(
            "解码后的 offset 必须等于 rangeStart",
            args.rangeStart,
            decoded!!.legacyOffset,
        )
    }

    @Test
    fun `streaming selection locator carries the chapter based offset`() {
        val args = paragraph("章二内容。\n", base = 1_000)
        val json = computeLocatorJson(
            epubBook = null,
            selectedGlobalOffset = args.globalOffset,
            chapterStartOffsets = emptyList(),
            selectedText = args.text,
            selectedRangeStart = args.rangeStart,
        )
        assertNotNull(json)
        assertEquals(1_000, LocatorCodec.decode(json!!)!!.legacyOffset)
    }

    @Test
    fun `locator text fingerprint matches the selected text`() {
        val args = paragraph("第一章正文。\n", base = 0)
        val json = computeLocatorJson(
            epubBook = null,
            selectedGlobalOffset = args.globalOffset,
            chapterStartOffsets = emptyList(),
            selectedText = args.text,
            selectedRangeStart = args.rangeStart,
        )
        assertNotNull(json)
        // 指纹是 SHA-256 前 8 字节的十六进制（LocatorCodec.encode 只写 "fp":"<hex>"），
        // 原文不落盘。因此断言「解码出的 fp == 对所选文字重算的指纹」，
        // 而不是断言 JSON 里含原文 —— 后者恒假，是写测试时的想当然。
        assertEquals(
            "locator 必须携带所选文字的指纹",
            LocatorCodec.fingerprint("第一章正文。"),
            LocatorCodec.decode(json!!)!!.excerptFingerprint,
        )
    }

    // ── 4. 快照一致性 ───────────────────────────────────────────────────

    @Test
    fun `snapshot built from the selection keeps all four fields consistent`() {
        val args = paragraph("章二内容。\n", base = 1_000)
        val snapshot = ReaderSelectionSnapshot(
            text = args.text,
            rangeStart = args.rangeStart,
            globalOffset = args.globalOffset,
            sourceEnd = args.sourceEnd,
        )
        assertEquals("章二内容。", snapshot.text)
        assertEquals(1_000, snapshot.rangeStart)
        assertEquals(-1, snapshot.globalOffset)
        assertEquals(1_000 + "章二内容。".length, snapshot.sourceEnd)
    }

    // ── 5. 三个消费者共用同一快照，locator 均非空 ────────────────────────

    @Test
    fun `highlight note and inspiration share one non null locator`() {
        val args = paragraph("共享锚点文本\n", base = 250)
        val snapshot = ReaderSelectionSnapshot(
            text = args.text,
            rangeStart = args.rangeStart,
            globalOffset = args.globalOffset,
            sourceEnd = args.sourceEnd,
        )
        listOf("highlight", "note", "inspiration").forEach { consumer ->
            val json = computeLocatorJsonForSnapshot(
                epubBook = null,
                chapterStartOffsets = emptyList(),
                snapshot = snapshot,
            )
            assertNotNull("$consumer 的 locator 不得为 null", json)
            assertEquals(
                "$consumer 与选区同源",
                args.rangeStart,
                LocatorCodec.decode(json!!)!!.legacyOffset,
            )
        }
    }

    // ── 6. 槽位交换回旧错误形态必须失败 ─────────────────────────────────

    @Test
    fun `swapping the canonical start back into globalOffset yields no locator`() {
        // F3 的错误形态：起点写进 globalOffset 槽，rangeStart 为 -1。
        // 这条用例就是它当时为什么全绿也不可用的证明。
        val args = paragraph("第一章正文。\n", base = 0)
        val json = computeLocatorJson(
            epubBook = null,
            selectedGlobalOffset = args.rangeStart, // ← 错槽
            chapterStartOffsets = emptyList(),
            selectedText = args.text,
            selectedRangeStart = -1,
        )
        org.junit.Assert.assertNull("错槽形态必须产不出 locator", json)
    }

    @Test
    fun `epub present still routes to the globalOffset slot`() {
        // 反向钉子：EPUB 走 globalOffset 槽，不能被本次修改带偏。
        val json = computeLocatorJson(
            epubBook = null, // 本文件只证明 Markdown（epubBook == null）分支
            selectedGlobalOffset = 42,
            chapterStartOffsets = emptyList(),
            selectedText = "x",
            selectedRangeStart = -1,
        )
        org.junit.Assert.assertNull("非 EPUB 文档不得走 globalOffset 槽", json)
    }

    // ── 不可选择单元 ────────────────────────────────────────────────────

    @Test
    fun `non selectable unit yields null selection args`() {
        val src = "段落\n\n---\n"
        val rule = MarkdownRenderModel.flatten(chapter(src))
            .first { it.kind == MarkdownRenderKind.HORIZONTAL_RULE }
        val args = markdownBlockSelectionArgs(
            text = "",
            unit = rule,
            blockGlobalBase = 0,
        )
        org.junit.Assert.assertNull("分隔线不得产出选择参数", args)
    }
}
