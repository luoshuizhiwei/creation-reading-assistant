package com.creationreadingassistant.feature.reader.pager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务86-v1-A：分页位置上报裁决的 JVM 回归。
 *
 * 85 实测：退出阅读器时同一 bookId 的持久位置由 offset 1235（28.46%）回退到
 * 1034（23.83%），期间没有用户翻页。`pagedAbsOffsetState` 只有一个写入方
 *（页变化回调取 `controller.currentPageStartAbs`），因此只可能是 controller
 * 重排后页首前移并被上报。
 *
 * 本测试锁定裁决：真实翻页/首次落位要上报；重排后锚点仍在页内时不得上报。
 */
class PagedPositionReportTest {

    @Test
    fun firstEmissionAlwaysReports() {
        val d = decidePagedPositionReport(everReported = false, anchor = 0, range = 0..200)
        assertFalse("首次落位不要求锚点移动", d.anchorMoved)
        assertTrue("本会话首次落位必须上报（用于清除 pending 与建立初始位置）", d.shouldReport)
    }

    @Test
    fun realPageTurnReports() {
        // 用户从上一页（锚点 1034）翻到下一页：旧锚点不在新页区间 [1235,1440) 内。
        val d = decidePagedPositionReport(everReported = true, anchor = 1034, range = 1235..1440)
        assertTrue("锚点离开旧页 = 真实翻页", d.anchorMoved)
        assertTrue(d.shouldReport)
    }

    @Test
    fun relayoutThatKeepsAnchorInPageDoesNotReport() {
        // 85 现场形态：重排后新页从 1034 开始，但阅读锚点 1235 仍在本页内。
        val d = decidePagedPositionReport(everReported = true, anchor = 1235, range = 1034 until 1300)
        assertFalse("锚点仍在页内 = 不是用户翻页", d.anchorMoved)
        assertFalse("重排不得覆盖已持久化的阅读位置", d.shouldReport)
    }

    @Test
    fun relayoutThatMovesAnchorOutOfPageStillReports() {
        // 极端重排：锚点已不在新页区间内，位置确实变了，必须上报。
        val d = decidePagedPositionReport(everReported = true, anchor = 1235, range = 900 until 1100)
        assertTrue(d.anchorMoved)
        assertTrue(d.shouldReport)
    }

    @Test
    fun layoutNotReadyReports() {
        val d = decidePagedPositionReport(everReported = true, anchor = 1235, range = null)
        assertTrue("排版未就绪时沿用既有行为（上报当前页首）", d.anchorMoved)
        assertTrue(d.shouldReport)
    }

    @Test
    fun repeatedRelayoutsStaySuppressed() {
        // 连续多次重排：只要锚点一直在页内，就持续不上报，不会逐步回退。
        var everReported = true
        val anchor = 1235
        val ranges = listOf(1034 until 1300, 1100 until 1360, 1180 until 1420)
        ranges.forEach { range ->
            val d = decidePagedPositionReport(everReported, anchor, range)
            assertFalse("range=$range 不应上报", d.shouldReport)
            everReported = everReported || d.shouldReport
        }
        assertEquals(true, everReported)
    }
}
