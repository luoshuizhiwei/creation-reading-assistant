package com.creationreadingassistant.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务93-v1-A：灵感分类纯策略层的定向回归。
 *
 * 这些函数被 ViewModel 保存路径与列表筛选路径直接调用：
 * - [resolveDraftCategoryIds]：未改保留 / 明确清空 / 明确选择；
 * - [matchesCategoryFilter]：列表分类筛选谓词；
 * - [inspirationCategoryOptions] / [inspirationCategoryFilterOptions] /
 *   [inspirationCategoryNames]：编辑器胶囊、筛选胶囊与名称投影（旧 id 不隐藏）。
 */
class InspirationCategoryLogicTest {

    private val directory = listOf("c1" to "人物", "c2" to "设定")

    @Test
    fun untouchedDraftKeepsPreviousIds() {
        // 未动分类 → 草稿传 null，旧 categoryIds 原样保留（草稿默认空列表不得清空）。
        assertEquals(listOf("c1", "cX"), resolveDraftCategoryIds(null, listOf("c1", "cX")))
    }

    @Test
    fun explicitEmptySelectionClears() {
        assertEquals(emptyList<String>(), resolveDraftCategoryIds(emptyList(), listOf("c1", "c2")))
    }

    @Test
    fun explicitSelectionReplacesAndDedupes() {
        assertEquals(listOf("c2"), resolveDraftCategoryIds(listOf("c2", "c2"), listOf("c1")))
    }

    @Test
    fun unknownIdIsPreservedAndMarkedUnavailable() {
        val options = inspirationCategoryOptions(listOf("c1", "cX"), directory)
        assertEquals(listOf("c1", "cX"), options.map { it.id })
        assertTrue(options[0].available)
        assertEquals("人物", options[0].name)
        assertFalse(options[1].available)
        assertEquals("不可用分类（cX）", options[1].name)
    }

    @Test
    fun categoryFilterPredicateRequiresMembership() {
        assertTrue(matchesCategoryFilter(emptyList(), "all"))
        assertTrue(matchesCategoryFilter(listOf("c1"), "c1"))
        assertFalse(matchesCategoryFilter(listOf("c1"), "c2"))
    }

    @Test
    fun filterOptionsCountRealItemsInFirstSeenOrder() {
        val options = inspirationCategoryFilterOptions(
            listOf(listOf("c2", "c1"), listOf("c2")),
            directory,
        )
        assertEquals(listOf("c2", "c1"), options.map { it.id })
        assertEquals(listOf(2, 1), options.map { it.count })
        assertEquals(listOf("设定", "人物"), options.map { it.name })
    }

    @Test
    fun unknownFilterOptionIsKept() {
        val options = inspirationCategoryFilterOptions(listOf(listOf("cZ")), directory)
        assertEquals(1, options.size)
        assertFalse(options[0].available)
        assertEquals("不可用分类（cZ）", options[0].name)
    }

    @Test
    fun namesProjectionKeepsUnknownIds() {
        assertEquals(
            listOf("人物", "不可用分类（cX）"),
            inspirationCategoryNames(listOf("c1", "cX", "c1"), directory),
        )
    }
}
