package com.creationreadingassistant.ui.viewmodel

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R5 素材（灵感）纯数据操作契约：
 * - payload 新字段（adoptions/excerpts/mergedInto/locatorJson）向后兼容旧 JSON；
 * - 采用去向校验（value 非空、kind 限 text/link）、首次采用推进「已采用」；
 * - 多摘录素材卡聚合保留各来源的摘录文本 + locator，tags 取并集；
 * - 回顾管线阶段筛选（organized 聚合 usable+polished）与计数。
 */
class InspirationMaterialOpsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun sourceInfo(
        bookId: String? = "book-1",
        bookTitle: String? = "旧书名",
        excerpt: String? = "一段摘录",
        locatorJson: String? = null,
    ) = InspirationSourceInfo(
        bookId = bookId,
        bookTitle = bookTitle,
        excerpt = excerpt,
        locatorJson = locatorJson,
    )

    // ── payload 向后兼容 ─────────────────────────────────────────────────

    @Test
    fun `legacy payload without new fields parses with defaults`() {
        val legacy = """{"tags":["设定"],"categoryIds":[],"source":{"bookId":"b1","excerpt":"原文"}}"""
        val payload = json.decodeFromString<InspirationPayloadData>(legacy)
        assertEquals(listOf("设定"), payload.tags)
        assertEquals("b1", payload.source?.bookId)
        assertTrue(payload.adoptions.isEmpty())
        assertTrue(payload.excerpts.isEmpty())
        assertNull(payload.mergedInto)
        assertNull(payload.source?.locatorJson)
    }

    @Test
    fun `new payload fields survive round trip`() {
        val payload = InspirationPayloadData(
            tags = listOf("t"),
            source = sourceInfo(locatorJson = """{"lo":42}"""),
            adoptions = listOf(
                InspirationAdoptionRecord(kind = "text", value = "用在第三章", createdAt = "2026-09-12T00:00:00Z"),
            ),
            excerpts = listOf(sourceInfo()),
            mergedInto = "card-1",
        )
        val decoded = json.decodeFromString<InspirationPayloadData>(
            json.encodeToString(InspirationPayloadData.serializer(), payload),
        )
        assertEquals(payload, decoded)
    }

    // ── 采用去向 ─────────────────────────────────────────────────────────

    @Test
    fun `first adoption promotes status to used`() {
        val (next, status) = payloadWithAdoption(
            payload = InspirationPayloadData(),
            record = InspirationAdoptionRecord("text", "已写进大纲", null, "2026-09-12T00:00:00Z"),
            nowStatus = "reviewing",
        )!!
        assertEquals(1, next.adoptions.size)
        assertEquals(STATUS_USED, status)
    }

    @Test
    fun `existing used status stays unchanged on further adoptions`() {
        val (next, status) = payloadWithAdoption(
            payload = InspirationPayloadData(),
            record = InspirationAdoptionRecord("link", "https://example.com", null, "2026-09-12T00:00:00Z"),
            nowStatus = STATUS_USED,
        )!!
        assertEquals(1, next.adoptions.size)
        assertEquals(STATUS_USED, status)
    }

    @Test
    fun `adoption validation rejects blank value and unknown kind`() {
        assertNull(
            payloadWithAdoption(
                InspirationPayloadData(),
                InspirationAdoptionRecord("text", "   ", null, "2026-09-12T00:00:00Z"),
                "inbox",
            ),
        )
        assertNull(
            payloadWithAdoption(
                InspirationPayloadData(),
                InspirationAdoptionRecord("video", "x", null, "2026-09-12T00:00:00Z"),
                "inbox",
            ),
        )
    }

    @Test
    fun `removing adoption keeps other records`() {
        val a = InspirationAdoptionRecord("text", "第一条", null, "2026-09-12T00:00:00Z")
        val b = InspirationAdoptionRecord("link", "第二条", null, "2026-09-12T00:01:00Z")
        val next = payloadWithoutAdoption(InspirationPayloadData(adoptions = listOf(a, b)), a)
        assertEquals(listOf(b), next.adoptions)
    }

    // ── 多摘录素材卡聚合 ────────────────────────────────────────────────

    @Test
    fun `merge keeps excerpt and locator per source and unions tags`() {
        val s1 = InspirationPayloadData(
            tags = listOf("甲"),
            categoryIds = listOf("c1"),
            source = sourceInfo(bookId = "b1", bookTitle = "书一", excerpt = "摘录一", locatorJson = """{"lo":10}"""),
        )
        val s2 = InspirationPayloadData(
            tags = listOf("甲", "乙"),
            source = sourceInfo(bookId = "b2", bookTitle = "书二", excerpt = "摘录二"),
        )
        val merged = mergedMaterialPayload(listOf(s1, s2), tags = listOf("丙"), categoryIds = emptyList())
        assertEquals(2, merged.excerpts.size)
        assertEquals("摘录一", merged.excerpts[0].excerpt)
        assertNotNull(merged.excerpts[0].locatorJson)
        assertEquals("书二", merged.excerpts[1].bookTitle)
        // tags 并集保序去重：卡片自身 tags 优先，再追加各条新出现的
        assertEquals(listOf("丙", "甲", "乙"), merged.tags)
        assertEquals(listOf("c1"), merged.categoryIds)
    }

    @Test
    fun `merge drops sources without excerpt and locator`() {
        val thoughtOnly = InspirationPayloadData(source = sourceInfo(excerpt = null, locatorJson = null))
        val merged = mergedMaterialPayload(listOf(thoughtOnly), tags = emptyList(), categoryIds = emptyList())
        assertTrue(merged.excerpts.isEmpty())
    }

    // ── 回顾管线 ─────────────────────────────────────────────────────────

    @Test
    fun `organized pipeline filter covers usable and polished`() {
        assertTrue(statusMatchesPipeline("usable", PIPELINE_ORGANIZED))
        assertTrue(statusMatchesPipeline("polished", PIPELINE_ORGANIZED))
        assertFalse(statusMatchesPipeline("inbox", PIPELINE_ORGANIZED))
        assertTrue(statusMatchesPipeline("inbox", "inbox"))
        assertTrue(statusMatchesPipeline("used", "used"))
        assertFalse(statusMatchesPipeline("used", "unknown"))
    }

    @Test
    fun `pipeline counts aggregate across stages`() {
        val counts = pipelineCounts(
            listOf(
                "inbox" to 3,
                "reviewing" to 2,
                "usable" to 4,
                "polished" to 1,
                "used" to 6,
                "archived" to 9,
            ),
        )
        assertEquals(3, counts["inbox"])
        assertEquals(2, counts["reviewing"])
        // 已整理 = 可使用 + 已打磨
        assertEquals(5, counts[PIPELINE_ORGANIZED])
        assertEquals(6, counts["used"])
    }
}
