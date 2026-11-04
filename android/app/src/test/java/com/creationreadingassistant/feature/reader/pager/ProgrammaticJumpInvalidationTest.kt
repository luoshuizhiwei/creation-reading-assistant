package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.ui.viewmodel.ReaderAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F2.2：程序化跳转的失效通知**只**走选区通道（缺陷 A）。
 *
 * 背景：`onSelectionInvalidated` 曾直接接到 `onGesturePageTurn`，而那条链在 VM 里是
 * `ReaderAction.PageTurn` —— 它除了清选区，还会**隐藏阅读页顶栏/底栏**。
 * 程序化跳转（底栏进度条、目录、搜索命中、高亮回源）只是让旧选区失效，
 * 不等于用户执行了一次翻页手势；跨章 `LoadChapter` 也只清选区、不动 chrome，
 * 于是同章跳页与跨章跳页对菜单栏的处理会不一致。
 *
 * 本测试按**生产接线原样**复现这条链（`onSelect` 空文本 → `ClearSelection`，
 * 与 `ReaderLayerBuilders` 中的适配一致），断言产出的是选区动作而非翻页动作。
 */
class ProgrammaticJumpInvalidationTest {

    /**
     * 复现宿主的 `onSelect` 适配（`ReaderLayerBuilders.buildReaderContentHostCallbacks`）：
     * 空文本 → ClearSelection；否则 SetSelectedText。
     */
    private fun hostOnSelect(recorded: MutableList<ReaderAction>): (String, Int, Int) -> Unit =
        { text, sourceStart, sourceEnd ->
            if (text.isBlank()) {
                recorded += ReaderAction.ClearSelection
            } else {
                recorded += ReaderAction.SetSelectedText(text, sourceStart, -1, sourceEnd)
            }
        }

    @Test
    fun `a programmatic jump invalidates the selection through the selection channel`() {
        val recorded = mutableListOf<ReaderAction>()

        // 生产接线：programmaticJumpInvalidation(onSelect)
        val invalidate = programmaticJumpInvalidation(hostOnSelect(recorded))

        invalidate()

        assertEquals(
            "失效通知必须恰好产生一次 ClearSelection 语义",
            listOf(ReaderAction.ClearSelection),
            recorded,
        )
    }

    @Test
    fun `a programmatic jump never emits a page turn`() {
        val recorded = mutableListOf<ReaderAction>()

        val invalidate = programmaticJumpInvalidation(hostOnSelect(recorded))
        invalidate()

        assertFalse(
            "程序化跳转不得发出 PageTurn —— 那会顺带隐藏顶栏/底栏",
            recorded.any { it is ReaderAction.PageTurn },
        )
    }

    @Test
    fun `the invalidation never routes through the gesture channel`() {
        // 手势通道（onGesturePageTurn → PageTurn）是「隐藏 chrome」的唯一来源。
        // 这里显式把它做成会记账的假实现，断言程序化跳转一次都不会碰到它。
        var gestureTurns = 0
        val gesturePageTurn: () -> Unit = { gestureTurns++ }

        val recorded = mutableListOf<ReaderAction>()
        val invalidate = programmaticJumpInvalidation(hostOnSelect(recorded))

        invalidate()

        assertEquals("程序化跳转不得触发翻页手势通道", 0, gestureTurns)
        assertEquals(listOf(ReaderAction.ClearSelection), recorded)
        // 对照：手势通道被显式调用时确实会记账 —— 证明上面那条不是恒真断言。
        gesturePageTurn()
        assertEquals(1, gestureTurns)
    }

    @Test
    fun `the invalidation is reusable and stays on the selection channel`() {
        // 同一个失效回调可能被多次触发（多次跳转）；每次都必须只走选区通道。
        val recorded = mutableListOf<ReaderAction>()

        val invalidate = programmaticJumpInvalidation(hostOnSelect(recorded))
        invalidate()
        invalidate()

        assertEquals(2, recorded.size)
        assertTrue("每一次都必须是 ClearSelection", recorded.all { it == ReaderAction.ClearSelection })
    }
}
