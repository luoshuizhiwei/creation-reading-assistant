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

    // ── 嵌套合并（R4：聚合卡再次参与合并不丢结构化来源） ────────────────

    /** 真实生产路径形状：聚合卡 C 的 payload 只有 excerpts、source 为 null。 */
    private fun aggregatedCardPayload(
        vararg excerpts: InspirationSourceInfo,
    ) = InspirationPayloadData(
        tags = listOf("卡标签"),
        categoryIds = listOf("c-card"),
        excerpts = excerpts.toList(),
    )

    @Test
    fun `nested merge keeps structured sources of an aggregated card`() {
        // A、B 合成卡 C；C 与原始摘录 D 再次合并
        val a = sourceInfo(bookId = "b1", bookTitle = "书一", excerpt = "摘录A", locatorJson = """{"lo":10}""")
        val b = sourceInfo(bookId = "b2", bookTitle = "书二", excerpt = "摘录B")
        val d = sourceInfo(bookId = "b3", bookTitle = "书三", excerpt = "摘录D", locatorJson = """{"lo":30}""")
        val cardC = aggregatedCardPayload(a, b)

        val merged = mergedMaterialPayload(listOf(cardC, InspirationPayloadData(source = d)), tags = emptyList(), categoryIds = emptyList())

        // C 的全部结构化来源 A/B 与 D 都保留，顺序为 C 的 source 槽位（无）→ C.excerpts → D
        assertEquals(listOf(a, b, d), merged.excerpts)
        // 聚合卡自身的 tags/categoryIds 进入并集
        assertEquals(listOf("卡标签"), merged.tags)
        assertEquals(listOf("c-card"), merged.categoryIds)
    }

    @Test
    fun `nested merge of two aggregated cards keeps both excerpt lists in order`() {
        val a = sourceInfo(bookId = "b1", bookTitle = "书一", excerpt = "摘录A", locatorJson = """{"lo":10}""")
        val b = sourceInfo(bookId = "b2", bookTitle = "书二", excerpt = "摘录B")
        val c = sourceInfo(bookId = "b3", bookTitle = "书三", excerpt = "摘录C")
        val d = sourceInfo(bookId = "b4", bookTitle = "书四", excerpt = "摘录D", locatorJson = """{"lo":40}""")
        val card1 = aggregatedCardPayload(a, b)
        val card2 = aggregatedCardPayload(c, d)

        val merged = mergedMaterialPayload(listOf(card1, card2), tags = emptyList(), categoryIds = emptyList())

        // 两张聚合卡的摘录按输入顺序逐条保留，locator 原样
        assertEquals(listOf(a, b, c, d), merged.excerpts)
    }

    @Test
    fun `nested merge consumes source slot and excerpts of the same input in order`() {
        // source 与 excerpts 同时存在的输入：source 槽位在前，excerpts 随后
        val ownSource = sourceInfo(bookId = "b0", bookTitle = "卡自身", excerpt = "自身摘录", locatorJson = """{"lo":1}""")
        val nestedA = sourceInfo(bookId = "b1", bookTitle = "书一", excerpt = "嵌套A")
        val nestedB = sourceInfo(bookId = "b2", bookTitle = "书二", excerpt = "嵌套B")
        val d = sourceInfo(bookId = "b3", bookTitle = "书三", excerpt = "摘录D")
        val hybrid = InspirationPayloadData(
            tags = listOf("混合"),
            source = ownSource,
            excerpts = listOf(nestedA, nestedB),
        )

        val merged = mergedMaterialPayload(listOf(hybrid, InspirationPayloadData(source = d)), tags = emptyList(), categoryIds = emptyList())

        assertEquals(listOf(ownSource, nestedA, nestedB, d), merged.excerpts)
        assertEquals(listOf("混合"), merged.tags)
    }

    @Test
    fun `nested merge keeps same-text excerpts with different locators`() {
        // 去重口径 = InspirationSourceInfo 完整数据类精确相等（与导出侧合同一致）；
        // 相同摘录文字但 locator 不同 = 不同位置，必须保留两条
        val atPage1 = sourceInfo(bookId = "b1", bookTitle = "书一", excerpt = "相同文字", locatorJson = """{"lo":11}""")
        val atPage2 = sourceInfo(bookId = "b1", bookTitle = "书一", excerpt = "相同文字", locatorJson = """{"lo":22}""")
        val card = aggregatedCardPayload(atPage1, atPage2)

        val merged = mergedMaterialPayload(listOf(card), tags = emptyList(), categoryIds = emptyList())

        assertEquals(listOf(atPage1, atPage2), merged.excerpts)
    }

    @Test
    fun `nested merge dedupes only exactly equal source infos preserving first occurrence`() {
        // source 槽位与 excerpts 里完整相等的条目按既有精确相等口径去重，保首次出现顺序；
        // 不同条目（即使同书同摘录文字、仅 locator 不同）不合并
        val a = sourceInfo(bookId = "b1", bookTitle = "书一", excerpt = "摘录A", locatorJson = """{"lo":10}""")
        val b = sourceInfo(bookId = "b2", bookTitle = "书二", excerpt = "摘录B")
        val card1 = InspirationPayloadData(source = a, excerpts = listOf(a, b, a))
        val card2 = InspirationPayloadData(excerpts = listOf(b, a))

        val merged = mergedMaterialPayload(listOf(card1, card2), tags = emptyList(), categoryIds = emptyList())

        assertEquals(listOf(a, b), merged.excerpts)
    }

    @Test
    fun `nested merge keeps mergedInto and adoptions of inputs out of card payload`() {
        // 原条目归档/恢复语义由 ViewModel 维护；合并 payload 不继承 mergedInto/adoptions，
        // 但 tags/categoryIds 并集仍保序
        val adopted = sourceInfo(bookId = "b1", bookTitle = "书一", excerpt = "摘录A")
        val cardC = InspirationPayloadData(
            tags = listOf("甲", "乙"),
            categoryIds = listOf("c1", "c2"),
            adoptions = listOf(InspirationAdoptionRecord("text", "去向", null, "2026-10-13T00:00:00Z")),
            excerpts = listOf(adopted),
            mergedInto = "other-card",
        )

        val merged = mergedMaterialPayload(
            listOf(cardC, InspirationPayloadData(tags = listOf("乙", "丙"), categoryIds = listOf("c2", "c3"))),
            tags = listOf("丁"),
            categoryIds = emptyList(),
        )

        assertTrue(merged.adoptions.isEmpty())
        assertNull(merged.mergedInto)
        assertEquals(listOf("丁", "甲", "乙", "丙"), merged.tags)
        assertEquals(listOf("c1", "c2", "c3"), merged.categoryIds)
        // 已被归档的输入不因 mergedInto 被排除：其结构化摘录仍进入新卡
        assertEquals(listOf(adopted), merged.excerpts)
    }

    @Test
    fun `nested merge payload round trips with field order intact`() {
        // 序列化读回：excerpts 字段值与顺序一致（字段级对照，不重排不重建）
        val a = sourceInfo(bookId = "b1", bookTitle = "书一", excerpt = "摘录A", locatorJson = """{"lo":10}""")
        val d = sourceInfo(bookId = "b3", bookTitle = "书三", excerpt = "摘录D")
        val cardC = aggregatedCardPayload(a)
        val merged = mergedMaterialPayload(listOf(cardC, InspirationPayloadData(source = d)), tags = listOf("t"), categoryIds = listOf("c"))

        val encoded = json.encodeToString(InspirationPayloadData.serializer(), merged)
        val decoded = json.decodeFromString<InspirationPayloadData>(encoded)

        assertEquals(merged, decoded)
        assertEquals(merged.excerpts, decoded.excerpts)
        assertEquals(a, decoded.excerpts[0])
        assertEquals(d, decoded.excerpts[1])
        assertEquals("""{"lo":10}""", decoded.excerpts[0].locatorJson)
        // 序列化文本里 excerpts 逐条快照字段按声明顺序出现（bookId 在 bookTitle 前）
        val firstEntry = encoded.substringAfter("\"excerpts\":[{").substringBefore("}")
        assertTrue(firstEntry.indexOf("\"bookId\"") in 0 until firstEntry.indexOf("\"bookTitle\""))
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
