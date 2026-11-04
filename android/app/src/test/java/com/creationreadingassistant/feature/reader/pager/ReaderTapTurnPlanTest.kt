package com.creationreadingassistant.feature.reader.pager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F2.1：点按翻页的派发计划（缺陷 B）。
 *
 * 修复前，非 `OUTER_REVEAL` 的点按分支是「先 `onGesturePageTurn()`（派发 PageTurn），
 * 再设置 `turnRequest`」—— 于是 PageTurn 早于页码真正提交，选区被**乐观清除**；
 * 而 `PageTurner` 提交后还会经 `onPageTurned` 再派发一次，存在重复。
 *
 * 现在把「谁来提交这次翻页」显式化为一个计划：非 reveal 只登记 `turnRequest`，
 * PageTurn 一律由 `PageTurner` 的提交回调（`onPageTurned`）派发一次。
 * 计划本身不携带任何「立刻派发」的动作，因此点按处不可能再提前清选区。
 */
class ReaderTapTurnPlanTest {

    @Test
    fun `a non reveal tap only registers a turn request`() {
        // 关键断言：非 reveal 路径的计划是「登记请求」，不是「立刻翻页 + 通知」。
        assertEquals(
            ReaderTapTurnPlan.RequestTurn(-1),
            readerTapTurnPlan(ReaderTapAction.PREVIOUS_PAGE, PagedReaderPageTurnRenderer.PAGE_TURNER),
        )
        assertEquals(
            ReaderTapTurnPlan.RequestTurn(1),
            readerTapTurnPlan(ReaderTapAction.NEXT_PAGE, PagedReaderPageTurnRenderer.PAGE_TURNER),
        )
    }

    @Test
    fun `an outer reveal tap delegates to the manual reveal path`() {
        // OUTER_REVEAL 由外层动画在结束帧提交，提交后经 onPageTurned 通知。
        assertEquals(
            ReaderTapTurnPlan.ManualReveal(-1),
            readerTapTurnPlan(ReaderTapAction.PREVIOUS_PAGE, PagedReaderPageTurnRenderer.OUTER_REVEAL),
        )
        assertEquals(
            ReaderTapTurnPlan.ManualReveal(1),
            readerTapTurnPlan(ReaderTapAction.NEXT_PAGE, PagedReaderPageTurnRenderer.OUTER_REVEAL),
        )
    }

    @Test
    fun `a boundary tap never produces a turn plan`() {
        // 首/末页时 resolveReaderTapAction 已返回 NONE；计划必须是 None，
        // 因此不会登记请求、不会提交、也不会清选区。
        assertEquals(
            ReaderTapTurnPlan.None,
            readerTapTurnPlan(ReaderTapAction.NONE, PagedReaderPageTurnRenderer.PAGE_TURNER),
        )
        assertEquals(
            ReaderTapTurnPlan.None,
            readerTapTurnPlan(ReaderTapAction.NONE, PagedReaderPageTurnRenderer.OUTER_REVEAL),
        )
    }

    @Test
    fun `a centre tap only toggles controls`() {
        assertEquals(
            ReaderTapTurnPlan.ToggleControls,
            readerTapTurnPlan(ReaderTapAction.TOGGLE_CONTROLS, PagedReaderPageTurnRenderer.PAGE_TURNER),
        )
    }

    @Test
    fun `only a real turn plan carries a direction`() {
        // 「计划里有没有方向」等价于「这次点按是否要求翻页」。
        // 边界与中央点击都不带方向 —— 它们不该导致任何页面身份变化。
        val turning = listOf(
            readerTapTurnPlan(ReaderTapAction.PREVIOUS_PAGE, PagedReaderPageTurnRenderer.PAGE_TURNER),
            readerTapTurnPlan(ReaderTapAction.NEXT_PAGE, PagedReaderPageTurnRenderer.PAGE_TURNER),
            readerTapTurnPlan(ReaderTapAction.PREVIOUS_PAGE, PagedReaderPageTurnRenderer.OUTER_REVEAL),
            readerTapTurnPlan(ReaderTapAction.NEXT_PAGE, PagedReaderPageTurnRenderer.OUTER_REVEAL),
        )
        val notTurning = listOf(
            readerTapTurnPlan(ReaderTapAction.NONE, PagedReaderPageTurnRenderer.PAGE_TURNER),
            readerTapTurnPlan(ReaderTapAction.TOGGLE_CONTROLS, PagedReaderPageTurnRenderer.PAGE_TURNER),
        )

        assertTrue("翻页计划必须带方向", turning.all { it.directionOrNull != null })
        assertTrue("非翻页计划不得带方向", notTurning.all { it.directionOrNull == null })
    }
}
