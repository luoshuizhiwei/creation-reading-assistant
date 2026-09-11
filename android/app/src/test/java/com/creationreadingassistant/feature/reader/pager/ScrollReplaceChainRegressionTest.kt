package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.rules.BoundedReplaceProjector
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleScope
import com.creationreadingassistant.feature.reader.rules.ScrollUnitProjection
import com.creationreadingassistant.ui.screen.reader.ReaderReplacementCapability
import com.creationreadingassistant.ui.screen.reader.buildChapterAlignedPlainUnits
import com.creationreadingassistant.ui.screen.reader.readerReplacementCapability
import com.creationreadingassistant.ui.screen.reader.readerReplacementStartupNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R1-S1 回归测试：滚动 TXT 替换的生产投影链与能力状态契约。
 *
 * 覆盖「组装 source → 正文绑定 → ReadingUnit 投影」中可在 JVM 层验证的部分：
 * [ScrollingTxtChapterSource.fromText]（真实目录 / 无目录、readingUnits 可读）→
 * [preparePagedReplacement] → [loadScrollUnitContent]。
 *
 * 真机缺陷形态一：规则已保存、预览命中，但正文始终显示原文。
 * 真机缺陷形态二：入口显示「可用」（APPLIED），正文却因为没有任何可投影作用域
 * 而必然保留原文。契约要求：APPLIED 只能表示当前正文确实走精确投影；降级必须
 * 落成 PARTIALLY_APPLIED / ALL_SCOPES_OVERSIZED，并且规则仍可管理。
 */
class ScrollReplaceChainRegressionTest {

    private fun replaceRule(pattern: String, replacement: String): ReplaceRule = ReplaceRule(
        id = "r-scroll-chain",
        name = "test",
        pattern = pattern,
        replacement = replacement,
        enabled = true,
        position = 0,
        scope = RuleScope.GLOBAL,
    )

    private val phrase = "这个世界"
    private val phraseReplacement = "TTEAM"
    private val rule = "\\Q$phrase\\E" to phraseReplacement

    /** 构造 3 章文本 + 连续 ReadingUnit 切片（每章含标题行，正文按 [unitSize] 字符切块）。 */
    private fun buildTextAndUnits(
        unitSize: Int = 40,
    ): Pair<String, List<ReadingUnit>> {
        val chapters = listOf(
            "第1章 雨夜" to "许青跑过街角，心想这个世界真是复杂。雨水打湿裤脚，这个世界依然运转。",
            "第2章 长街" to "这个世界不会停下。他停下喘息，抬头看这个世界，路灯忽明忽暗。",
            "第3章 天亮" to "没有这个世界的地方，只有雨。天亮之后，这个世界照常醒来。",
        )
        val sb = StringBuilder()
        val units = mutableListOf<ReadingUnit>()
        var unitIndex = 0
        chapters.forEachIndexed { ch, (title, body) ->
            val chapterText = "$title\n$body"
            val chapterStart = sb.length
            sb.append(chapterText)
            var off = chapterStart
            while (off < sb.length) {
                val len = minOf(unitSize, sb.length - off)
                units += ReadingUnit(
                    unitIndex = unitIndex++,
                    chapterIndex = ch,
                    title = title,
                    charStart = off,
                    charCount = len,
                )
                off += len
            }
        }
        return sb.toString() to units
    }

    /** 单段正文重复 [times] 次，用于构造可控长度的章。 */
    private fun bodyOf(times: Int): String = "许青跑过街角，${phrase}依然运转。\n".repeat(times)

    /** 旧 chunkPlainText 派生行为：全文均匀切块（含换行对齐），chapterIndex 恒 0。 */
    private fun buildLegacyChunkUnits(text: String, targetChars: Int = 3_000): List<ReadingUnit> {
        val units = mutableListOf<ReadingUnit>()
        var start = 0
        while (start < text.length) {
            var end = (start + targetChars).coerceAtMost(text.length)
            if (end < text.length) {
                val searchEnd = (end + 512).coerceAtMost(text.length)
                val newline = text.indexOf('\n', startIndex = end)
                if (newline in end until searchEnd) end = newline + 1
            }
            if (end <= start) end = (start + targetChars).coerceAtMost(text.length)
            units += ReadingUnit(
                unitIndex = units.size,
                chapterIndex = 0,
                title = "全文",
                charStart = start,
                charCount = end - start,
            )
            start = end
        }
        return units
    }

    /** 真实检测器 + 真实章对齐切块的多章书（每章都远小于投影上限）。 */
    private fun realDetectedBook(chapters: Int, timesPerChapter: Int): Pair<String, List<DocChapter>> {
        val sb = StringBuilder()
        repeat(chapters) { i ->
            sb.append("第${i + 1}章 测试段\n")
            sb.append(bodyOf(timesPerChapter))
        }
        val text = sb.toString()
        // 章节必须来自真实 TxtChapterDetector（经 PlainTextDocument 构造期识别），不用假 DocChapter
        val detected = PlainTextDocument(text).chapters
        return text to detected
    }

    // ── 基线链路：装配、正文绑定与 source 坐标 ─────────────────────────────

    @Test
    fun `scroll chain applies replacement to every unit display text`() {
        val (text, units) = buildTextAndUnits()
        val totalOccurrences = Regex(phrase).findAll(text).count()
        assertTrue("fixture must contain target phrase, got=$totalOccurrences", totalOccurrences > 0)

        val delegate = ScrollingTxtChapterSource.fromText(text, units)
        assertFalse("scroll TXT must use segmented path", delegate.replaceProjectionScopeIsComplete)

        val prepared = preparePagedReplacement(
            delegate = delegate,
            bookId = "test-book",
            rules = listOf(replaceRule(rule.first, rule.second)),
        )
        assertEquals(
            "every scroll scope projects, so APPLIED is honest",
            PagedReplacementAvailability.APPLIED,
            prepared.availability,
        )
        val replaced = prepared.source as ReplacedSegmentedChapterSource

        var displayTotal = 0
        var displayChars = 0
        for (unit in units) {
            val projection: ScrollUnitProjection = replaced.loadScrollUnitContent(unit)
            val display = projection.displayText
            assertFalse(
                "unit ${unit.unitIndex} display still contains original phrase",
                display.contains(phrase),
            )
            displayTotal += Regex(phraseReplacement).findAll(display).count()
            displayChars += display.length

            // 持久化坐标仍为 source 坐标：unit 起点双向映射稳定
            assertEquals(
                "unit ${unit.unitIndex} display start must map to its source start",
                unit.charStart,
                projection.localDisplayToGlobalSource(0),
            )
            val (lo, hi) = projection.globalSourceRangeToLocalDisplay(unit.charStart, unit.charStart + unit.charCount)
            assertEquals(0, lo)
            assertTrue("unit ${unit.unitIndex} display end must cover replacement growth", hi >= 0)
        }
        assertEquals(
            "replacement occurrences across all unit displays",
            totalOccurrences,
            displayTotal,
        )
        assertEquals(
            "display length growth must equal 1 char per occurrence (4→5)",
            text.length + totalOccurrences,
            displayChars,
        )
        assertEquals(
            "coordinate space must stay SOURCE",
            ReplacementCoordinateSpace.SOURCE,
            delegate.replacementCoordinateSpace,
        )
    }

    @Test
    fun `scroll chain without rules keeps original text with source coordinates`() {
        val (text, units) = buildTextAndUnits()
        val delegate = ScrollingTxtChapterSource.fromText(text, units)
        val prepared = preparePagedReplacement(delegate, "test-book", rules = emptyList())
        assertEquals(
            "no rules on scroll TXT must be NO_EFFECTIVE_RULES (not INCOMPLETE_SCOPE)",
            PagedReplacementAvailability.NO_EFFECTIVE_RULES,
            prepared.availability,
        )
        // 无规则时正文绑定的是 delegate 本身：identity 投影，原文 + source 坐标。
        for (unit in units) {
            val projection = prepared.source.loadScrollUnitContent(unit)
            assertEquals(
                "unit ${unit.unitIndex} must show original text",
                text.substring(unit.charStart, unit.charStart + unit.charCount),
                projection.displayText,
            )
            assertEquals(unit.charStart, projection.localDisplayToGlobalSource(0))
        }
    }

    // ── 章对齐：真实检测器输出的章节必须原样喂给切块与投影链 ────────────────

    @Test
    fun `real detector chapters tile the book and keep every unit inside its chapter`() {
        val (text, detected) = realDetectedBook(chapters = 3, timesPerChapter = 60)
        assertEquals("检测器必须识别出 3 章", 3, detected.size)
        // 检测器章节必须完整铺满全文：这是章对齐切块的可用前提
        assertEquals(0, detected.first().startOffset)
        detected.zipWithNext().forEach { (a, b) ->
            assertEquals("章之间不得留空洞或重叠", a.startOffset + a.charCount, b.startOffset)
        }
        assertEquals(text.length, detected.last().startOffset + detected.last().charCount)

        val units = buildChapterAlignedPlainUnits(text, detected)
        assertTrue(units.isNotEmpty())
        assertEquals(
            "units 必须带上真实逻辑章编号",
            setOf(0, 1, 2),
            units.map { it.chapterIndex }.toSet(),
        )
        assertEquals("切块必须无损覆盖全文", text.length, units.sumOf { it.charCount })
        units.forEach { unit ->
            val chapter = detected[unit.chapterIndex]
            assertTrue(
                "unit ${unit.unitIndex} 越出其逻辑章区间",
                unit.charStart >= chapter.startOffset &&
                    unit.charStart + unit.charCount <= chapter.startOffset + chapter.charCount,
            )
        }
    }

    @Test
    fun `multi chapter book larger than the bound still projects per chapter`() {
        val (text, detected) = realDetectedBook(chapters = 3, timesPerChapter = 12_000)
        assertTrue(
            "整书必须超过投影上限以证明裁决按章而非按书：len=${text.length}",
            text.length > ReplaceProjectionScopeProvider.DEFAULT_MAX_CHARS_FOR_PROJECTION,
        )
        detected.forEach { chapter ->
            assertTrue(
                "每章都必须落在上限内：${chapter.charCount}",
                chapter.charCount <= ReplaceProjectionScopeProvider.DEFAULT_MAX_CHARS_FOR_PROJECTION,
            )
        }
        val units = buildChapterAlignedPlainUnits(text, detected)
        val delegate = ScrollingTxtChapterSource.fromText(text, units)
        var oversizedCallbacks = 0
        val prepared = preparePagedReplacement(
            delegate = delegate,
            bookId = "test-book",
            rules = listOf(replaceRule(rule.first, rule.second)),
            onUnsupportedTooLarge = { oversizedCallbacks++ },
        )
        assertEquals(PagedReplacementAvailability.APPLIED, prepared.availability)
        val replaced = prepared.source as ReplacedSegmentedChapterSource

        val totalOccurrences = Regex(phrase).findAll(text).count()
        var displayTotal = 0
        for (unit in units) {
            val projection = replaced.loadScrollUnitContent(unit)
            assertFalse(
                "unit ${unit.unitIndex} display still contains original phrase",
                projection.displayText.contains(phrase),
            )
            displayTotal += Regex(phraseReplacement).findAll(projection.displayText).count()
            assertEquals(
                "unit ${unit.unitIndex} 的持久化坐标必须回到 source",
                unit.charStart,
                projection.localDisplayToGlobalSource(0),
            )
            assertNotNull("每章都应拿到 Exact 投影", replaced.projectionForChapter(unit.unitIndex))
        }
        assertEquals(totalOccurrences, displayTotal)
        assertEquals("每章都在上限内时不得上报超限", 0, oversizedCallbacks)
    }

    // ── 能力状态契约：不得出现「入口可用、正文必然原文」 ───────────────────

    /**
     * 生产缺陷形态：小文件无目录时 unit 全部 chapterIndex=0（旧 chunkPlainText 派生行为），
     * 整本书成为唯一投影作用域；书长超过上限时逐 unit 判 UnsupportedTooLarge。
     * 契约修正后：装配期即给出 ALL_SCOPES_OVERSIZED，正文原文，且不再冒充 APPLIED。
     */
    @Test
    fun `single whole-book scope over the bound is not applied`() {
        val chapterText = "第1章 全文\n" + bodyOf(18_000)
        val text = chapterText + "第2章 续\n" + bodyOf(18_000)
        assertTrue(
            "fixture must exceed projection limit, len=${text.length}",
            text.length > ReplaceProjectionScopeProvider.DEFAULT_MAX_CHARS_FOR_PROJECTION,
        )
        val units = buildLegacyChunkUnits(text)
        val delegate = ScrollingTxtChapterSource.fromText(text, units)
        var oversizedCallbacks = 0
        val prepared = preparePagedReplacement(
            delegate = delegate,
            bookId = "test-book",
            rules = listOf(replaceRule(rule.first, rule.second)),
            onUnsupportedTooLarge = { oversizedCallbacks++ },
        )

        assertEquals(PagedReplacementAvailability.ALL_SCOPES_OVERSIZED, prepared.availability)
        val replaced = prepared.source as ReplacedSegmentedChapterSource
        units.take(10).forEach { unit ->
            val projection = replaced.loadScrollUnitContent(unit)
            assertEquals(
                "超限单一作用域必须保留原文（unit ${unit.unitIndex}）",
                text.substring(unit.charStart, unit.charStart + unit.charCount),
                projection.displayText,
            )
            assertNull(
                "降级作用域不得暴露 Exact 投影（unit ${unit.unitIndex}）",
                replaced.projectionForChapter(unit.unitIndex),
            )
            assertEquals(
                "降级后持久化坐标仍是 source（unit ${unit.unitIndex}）",
                unit.charStart,
                projection.localDisplayToGlobalSource(0),
            )
        }
        assertEquals("超限作用域按既有契约上抛一次", 1, oversizedCallbacks)
        // 规则仍可管理，但启动提示必须说清正文没被替换
        assertTrue(
            readerReplacementCapability(prepared.availability) is ReaderReplacementCapability.Available,
        )
        val notice = readerReplacementStartupNotice(prepared.availability, pagerEngineOn = false)
        assertNotNull("降级状态必须有明确提示", notice)
        assertTrue(notice!!.contains("保留原文"))
        assertFalse("不得出现「已替换正文」的表述", notice.contains("已替换正文"))
    }

    /** 1:1 完整作用域路径的同型缺陷：无目录检测兜底成「全文」单章且超限。 */
    @Test
    fun `complete-scope single whole-book chapter over the bound is not applied`() {
        val text = bodyOf(18_000) + bodyOf(18_000)
        // 真实检测器：无标题命中时返回单章「全文」而不是空列表
        val detected = PlainTextDocument(text).chapters
        assertEquals(1, detected.size)
        assertEquals("全文", detected.single().title)
        assertTrue(
            "fixture must exceed projection limit, len=${text.length}",
            text.length > BoundedReplaceProjector.DEFAULT_MAX_SOURCE_CHARS,
        )
        val delegate = TxtChapterSource(text, detected)
        assertTrue(delegate.replaceProjectionScopeIsComplete)
        val prepared = preparePagedReplacement(
            delegate = delegate,
            bookId = "test-book",
            rules = listOf(replaceRule(rule.first, rule.second)),
        )
        assertEquals(PagedReplacementAvailability.ALL_SCOPES_OVERSIZED, prepared.availability)
        val replaced = prepared.source as ReplacedChapterSource
        assertEquals(text, replaced.loadChapterText(0))
        assertNull("1:1 超限章不得暴露 Exact 投影", replaced.projectionForChapter(0))
    }

    /** 一个正常章 + 一个超限章：状态与正文都不得宣称全书已替换。 */
    @Test
    fun `mixed normal and oversized chapters report partial application`() {
        val chapter1 = "第1章 雨夜\n" + bodyOf(200)
        val chapter2 = "第2章 长夜\n" + bodyOf(18_000)
        val text = chapter1 + chapter2
        val detected = PlainTextDocument(text).chapters
        assertEquals(2, detected.size)
        val units = buildChapterAlignedPlainUnits(text, detected)
        assertEquals(setOf(0, 1), units.map { it.chapterIndex }.toSet())
        val delegate = ScrollingTxtChapterSource.fromText(text, units)
        var oversizedCallbacks = 0
        val prepared = preparePagedReplacement(
            delegate = delegate,
            bookId = "test-book",
            rules = listOf(replaceRule(rule.first, rule.second)),
            onUnsupportedTooLarge = { oversizedCallbacks++ },
        )

        assertEquals(PagedReplacementAvailability.PARTIALLY_APPLIED, prepared.availability)
        assertEquals(
            readerReplacementCapability(prepared.availability),
            ReaderReplacementCapability.Available(
                bodyNotice = readerReplacementStartupNotice(prepared.availability, pagerEngineOn = false),
            ),
        )
        val notice = readerReplacementStartupNotice(prepared.availability, pagerEngineOn = false)
        assertNotNull(notice)
        assertTrue("混合状态必须点明有章节保留原文", notice!!.contains("保留原文"))
        val replaced = prepared.source as ReplacedSegmentedChapterSource

        val firstChapterUnits = units.filter { it.chapterIndex == 0 }
        val oversizedUnits = units.filter { it.chapterIndex == 1 }
        assertTrue(firstChapterUnits.isNotEmpty() && oversizedUnits.isNotEmpty())
        firstChapterUnits.forEach { unit ->
            val projection = replaced.loadScrollUnitContent(unit)
            assertFalse(
                "可投影章必须真正替换（unit ${unit.unitIndex}）",
                projection.displayText.contains(phrase),
            )
            assertEquals(unit.charStart, projection.localDisplayToGlobalSource(0))
        }
        oversizedUnits.forEach { unit ->
            val projection = replaced.loadScrollUnitContent(unit)
            assertEquals(
                "超限章必须保留原文（unit ${unit.unitIndex}）",
                text.substring(unit.charStart, unit.charStart + unit.charCount),
                projection.displayText,
            )
            assertEquals(unit.charStart, projection.localDisplayToGlobalSource(0))
        }
        assertNull("超限章不得有 Exact 投影", replaced.projectionForChapter(oversizedUnits.first().unitIndex))
        assertNotNull("可投影章必须有 Exact 投影", replaced.projectionForChapter(firstChapterUnits.first().unitIndex))
        assertEquals(1, oversizedCallbacks)
    }

    @Test
    fun `applied availability is the only state claiming the body is projected`() {
        val degraded = listOf(
            PagedReplacementAvailability.PARTIALLY_APPLIED,
            PagedReplacementAvailability.ALL_SCOPES_OVERSIZED,
        )
        degraded.forEach { availability ->
            assertNotNull(
                "$availability 必须给出启动提示，避免用户误以为正文已替换",
                readerReplacementStartupNotice(availability, pagerEngineOn = true),
            )
        }
        assertNull(
            readerReplacementStartupNotice(PagedReplacementAvailability.APPLIED, pagerEngineOn = true),
        )
    }
}
