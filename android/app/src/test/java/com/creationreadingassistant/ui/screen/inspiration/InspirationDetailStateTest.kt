package com.creationreadingassistant.ui.screen.inspiration

import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.ui.viewmodel.InspirationItemsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 详情页实体解析纯 JVM 测试（Route/State seam）：
 * - 短暂未解析（列表 Loading 或刚保存未落库）→ Loading + 顶部动作禁用，不是空 Box；
 * - 持久 id 命中 → Found + 两个顶部动作可用；
 * - id 最终不存在且无 pending → NotFound，Route 可安全返回列表/错误。
 */
class InspirationDetailStateTest {

    private fun entity(id: String): InspirationEntity = InspirationEntity(
        id = id,
        title = "标题$id",
        body = "正文$id",
        created_at = "2026-08-01T10:00:00Z",
        updated_at = "2026-08-01T10:00:00Z",
    )

    @Test
    fun `detail while items still loading resolves to Loading with actions disabled`() {
        val resolution = resolveDetail(
            inspirationId = "i1",
            itemsState = InspirationItemsState.Loading,
            pendingSavedIds = emptySet(),
        )

        assertEquals(DetailResolution.Loading, resolution)
        assertFalse("Loading 时顶部动作禁用", resolution.actionsEnabled)
    }

    @Test
    fun `detail resolves to Found when persistent id is in loaded items`() {
        val resolution = resolveDetail(
            inspirationId = "i1",
            itemsState = InspirationItemsState.Loaded(listOf(entity("i1"))),
            pendingSavedIds = emptySet(),
        )

        assertEquals(DetailResolution.Found(entity("i1")), resolution)
        assertTrue("持久 id 命中后两个顶部动作可用", resolution.actionsEnabled)
    }

    @Test
    fun `detail treats just-saved id as Loading until repository emits it`() {
        val resolution = resolveDetail(
            inspirationId = "A",
            itemsState = InspirationItemsState.Loaded(emptyList()),
            pendingSavedIds = setOf("A"),
        )

        assertEquals(DetailResolution.Loading, resolution)
        assertFalse("已保存未落库时顶部动作禁用", resolution.actionsEnabled)
    }

    @Test
    fun `detail with unknown id and no pending save resolves to NotFound`() {
        val resolution = resolveDetail(
            inspirationId = "ghost",
            itemsState = InspirationItemsState.Loaded(emptyList()),
            pendingSavedIds = emptySet(),
        )

        assertEquals(DetailResolution.NotFound, resolution)
        assertFalse(resolution.actionsEnabled)
    }
}
