package com.creationreadingassistant.ui.screen.inspiration

import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 任务93-v1-A：列表分类筛选走真实 [filterAndSortItems]。
 *
 * 覆盖分类单独筛选，以及与关键词/标签关键词、状态筛选的 AND 组合；
 * payload 用真实 [InspirationPayloadData] 序列化，categoryIds 由真实 payload 解析。
 */
class InspirationCategoryFilterTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun item(
        id: String,
        title: String,
        categories: List<String>,
        tags: List<String> = emptyList(),
        status: String = "inbox",
        type: String = "note",
    ): InspirationEntity {
        val payload = InspirationPayloadData(tags = tags, categoryIds = categories)
        return InspirationEntity(
            id = id,
            title = title,
            body = "",
            type = type,
            status = status,
            payload = json.encodeToString(InspirationPayloadData.serializer(), payload),
            created_at = "2026-10-22T00:00:00Z",
            updated_at = "2026-10-22T00:00:00Z",
        )
    }

    private val items = listOf(
        item("a", "甲", categories = listOf("c1"), tags = listOf("t1")),
        item("b", "乙", categories = listOf("c2"), tags = listOf("t2"), status = "reviewing"),
        item("c", "丙", categories = listOf("c1", "c2")),
    )

    private fun payloadOf(entity: InspirationEntity): InspirationPayloadData =
        json.decodeFromString(InspirationPayloadData.serializer(), entity.payload ?: "{}")

    private val sourceOf: (InspirationEntity) -> InspirationSourceInfo? = { null }
    private val tagsOf: (InspirationEntity) -> List<String> = { payloadOf(it).tags }
    private val categoryIdsOf: (InspirationEntity) -> List<String> = { payloadOf(it).categoryIds }

    private fun ids(
        needle: String = "",
        typeFilter: String = "all",
        statusFilter: String = "all",
        categoryFilter: String = "all",
    ): List<String> = filterAndSortItems(
        items = items,
        needle = needle,
        typeFilter = typeFilter,
        statusFilter = statusFilter,
        categoryFilter = categoryFilter,
        sortMode = "updated",
        sourceOf = sourceOf,
        tagsOf = tagsOf,
        categoryIdsOf = categoryIdsOf,
    ).map { it.id }

    @Test
    fun categoryOnlyFiltersByMembership() {
        assertEquals(listOf("a", "c"), ids(categoryFilter = "c1"))
        assertEquals(listOf("b", "c"), ids(categoryFilter = "c2"))
    }

    @Test
    fun allCategoryKeepsEverything() {
        assertEquals(listOf("a", "b", "c"), ids())
    }

    @Test
    fun keywordAndCategoryComposeAsAnd() {
        // 关键词命中 b 的标题，但分类 c1 不包含 b → 空。
        assertEquals(emptyList<String>(), ids(needle = "乙", categoryFilter = "c1"))
        // 标签关键词 t1 + 分类 c1 → a。
        assertEquals(listOf("a"), ids(needle = "t1", categoryFilter = "c1"))
        // 关键词命中 c 的标题且分类包含 c1 → c。
        assertEquals(listOf("c"), ids(needle = "丙", categoryFilter = "c1"))
    }

    @Test
    fun statusAndCategoryComposeAsAnd() {
        assertEquals(listOf("b"), ids(statusFilter = "reviewing", categoryFilter = "c2"))
        assertEquals(listOf("c"), ids(statusFilter = "inbox", categoryFilter = "c2"))
    }

    @Test
    fun unknownCategoryDoesNotMatchAnything() {
        assertEquals(emptyList<String>(), ids(categoryFilter = "cZ"))
    }
}
