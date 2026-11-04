package com.creationreadingassistant.feature.reader.pager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F2.1：程序化跳转与 VM→宿主选区回流的两条纯决策。
 *
 * 这两条链此前只有静态证据，没有可执行断言：
 * 1. `jumpRequest → controller.open(j)` 改变可见页后**必须**发一次选区失效事件，
 *    但目标仍落在当前页 / `open` 没生效时**不得**发；
 * 2. VM 清空选区后宿主本地 `selRange` 必须丢弃，VM 未清时**必须保留**。
 */
class PagedReaderSelectionInvalidationPolicyTest {

    // ── 1. 程序化跳转（进度条 / 目录 / 回源）是否产生可见页面身份变化 ──────

    @Test
    fun `a jump to another page reports an identity change`() {
        // 当前页覆盖 [100, 200)；目标 350 在区间外 → 一定会换页。
        assertTrue(
            readerJumpClearsSelection(
                targetOffset = 350,
                currentPageRangeAbs = 100 until 200,
            ),
        )
    }

    @Test
    fun `a jump that lands on the current page reports no change`() {
        // 目标 150 仍落在当前可见页 [100, 200) 内 → open 会停在同一页，
        // 页面身份没变，不得伪装成一次页面变化、不得误清选区。
        assertFalse(
            readerJumpClearsSelection(
                targetOffset = 150,
                currentPageRangeAbs = 100 until 200,
            ),
        )
    }

    @Test
    fun `a jump to the exact current page start reports no change`() {
        // 区间起点与终点都属于本页（Page 区间是半开的 [start, end)）。
        assertFalse(
            readerJumpClearsSelection(targetOffset = 100, currentPageRangeAbs = 100 until 200),
        )
        assertFalse(
            readerJumpClearsSelection(targetOffset = 199, currentPageRangeAbs = 100 until 200),
        )
    }

    @Test
    fun `a jump with no visible page yet reports no change`() {
        // 排版未就绪 / 源为空：没有可被改变的页面身份 → 不得误清选区。
        assertFalse(
            readerJumpClearsSelection(targetOffset = 0, currentPageRangeAbs = null),
        )
        assertFalse(
            readerJumpClearsSelection(targetOffset = 9_999, currentPageRangeAbs = null),
        )
    }

    @Test
    fun `a jump just past the current page reports an identity change`() {
        // 区间右端之外一个字符就是下一页 —— 边界必须判为变化。
        assertTrue(
            readerJumpClearsSelection(targetOffset = 200, currentPageRangeAbs = 100 until 200),
        )
    }

    // ── 2. 跳转执行体的「恰好一次」契约 ──────────────────────────────

    @Test
    fun `a jump across pages opens the target and notifies exactly once`() {
        var openCount = 0
        var openedTo = -1
        var invalidations = 0

        val cleared = applyProgrammaticJump(
            targetOffset = 350,
            currentPageRangeAbs = 100 until 200,
            open = { openCount++; openedTo = it },
            invalidateSelection = { invalidations++ },
        )

        assertTrue(cleared)
        assertEquals("open 必须被调用一次", 1, openCount)
        assertEquals("open 必须拿到跳转目标", 350, openedTo)
        assertEquals("选区失效通知必须恰好一次", 1, invalidations)
    }

    @Test
    fun `a jump within the current page opens but never notifies`() {
        var openCount = 0
        var invalidations = 0

        val cleared = applyProgrammaticJump(
            targetOffset = 150,
            currentPageRangeAbs = 100 until 200,
            open = { openCount++ },
            invalidateSelection = { invalidations++ },
        )

        assertFalse(cleared)
        assertEquals("open 仍然要执行（跳转本身是有效的）", 1, openCount)
        assertEquals("同页跳转不得清选区", 0, invalidations)
    }

    @Test
    fun `a jump with no visible page opens but never notifies`() {
        var openCount = 0
        var invalidations = 0

        val cleared = applyProgrammaticJump(
            targetOffset = 0,
            currentPageRangeAbs = null,
            open = { openCount++ },
            invalidateSelection = { invalidations++ },
        )

        assertFalse(cleared)
        assertEquals(1, openCount)
        assertEquals("没有可见页身份可改变时不得清选区", 0, invalidations)
    }

    // ── 3. VM 清空选区后宿主本地选区的去留 ──────────────────────────

    @Test
    fun `a cleared vm selection drops the local range`() {
        // 这是 F2 修复的必要一环：VM 清了、屏幕上旧色块还在画的话，用户看到的仍是残留。
        assertNull(shouldDropLocalSelection(selectionCleared = true, local = 5..9))
    }

    @Test
    fun `a cleared vm selection is a no-op when there is no local range`() {
        assertNull(shouldDropLocalSelection(selectionCleared = true, local = null))
    }

    @Test
    fun `a live vm selection keeps the local range untouched`() {
        val local = 5..9
        assertEquals(local, shouldDropLocalSelection(selectionCleared = false, local = local))
    }

    @Test
    fun `an absent local range stays absent while the vm selection lives`() {
        assertNull(shouldDropLocalSelection(selectionCleared = false, local = null))
    }
}
