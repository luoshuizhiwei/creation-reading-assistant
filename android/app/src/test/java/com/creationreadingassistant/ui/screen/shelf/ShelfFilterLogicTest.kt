package com.creationreadingassistant.ui.screen.shelf

import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.ui.screen.shelf.ShelfStatusFilter
import com.creationreadingassistant.ui.viewmodel.ShelfLibraryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShelfFilterLogicTest {

    @Test
    fun `multiple tags use union before intersecting other dimensions`() {
        val result = combineTaxonomyFilterIds(
            shelfIds = setOf("book-1", "book-2"),
            categoryIds = setOf("book-2", "book-3"),
            tagIdGroups = listOf(
                setOf("book-1", "book-3"),
                setOf("book-2", "book-4"),
            ),
        )

        assertEquals(setOf("book-2"), result)
    }

    @Test
    fun `no taxonomy filters returns null instead of hiding every book`() {
        assertNull(combineTaxonomyFilterIds(null, null, emptyList()))
    }

    @Test
    fun `single filter summary uses its concrete label`() {
        val state = ShelfUiState(
            library = library(),
            selectedShelfId = "shelf-1",
        )

        assertEquals("文学", state.filterSummary)
    }

    @Test
    fun `combined summary counts filter dimensions without hiding active conditions`() {
        val state = ShelfUiState(
            library = library(),
            statusFilter = ShelfStatusFilter.READING,
            selectedShelfId = "shelf-1",
            selectedCategoryId = "category-1",
            selectedTagIds = setOf("tag-1", "tag-2"),
        )

        assertEquals("4 项筛选", state.filterSummary)
    }

    @Test
    fun `tag summary names one tag and counts multiple tags`() {
        val names = mapOf("tag-1" to "推理", "tag-2" to "悬疑")

        assertEquals("全部", selectedTagSummary(emptySet(), names))
        assertEquals("推理", selectedTagSummary(setOf("tag-1"), names))
        assertEquals("2 个标签", selectedTagSummary(setOf("tag-1", "tag-2"), names))
    }

    private fun library() = ShelfLibraryState(
        shelves = listOf(ShelfEntity("shelf-1", "文学", updated_at = "2026-08-02T00:00:00Z")),
        categories = listOf(CategoryEntity("category-1", "小说", updated_at = "2026-08-02T00:00:00Z")),
        tags = listOf(
            TagEntity("tag-1", "推理", updated_at = "2026-08-02T00:00:00Z"),
            TagEntity("tag-2", "悬疑", updated_at = "2026-08-02T00:00:00Z"),
        ),
    )
}
