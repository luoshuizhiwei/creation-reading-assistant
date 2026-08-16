package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ReaderChromeReducer 深 module 的纯逻辑测试：事件 → 状态。
 *
 * 规格（用户反馈 4）：
 * Enter→Visible；CenterTap→toggle；PageTurn→Hidden（无论 autoHideSeconds 是否 0）；
 * AutoHideElapsed(seconds>0)→Hidden；sheetOpen 或 seconds<=0 保持；SheetOpen 保持菜单且暂停 timer；SheetClose 恢复；
 * 切书重置 Visible。TTS 栏与 sheet 语义不误伤。
 */
class ReaderChromeReducerTest {

    @Test
    fun `default state is visible with no sheet`() {
        val state = ReaderChromeState()
        assertTrue(state.controlsVisible)
        assertFalse(state.sheetOpen)
        assertFalse(state.autoHidePaused)
    }

    @Test
    fun `enter makes controls visible`() {
        val state = readerChromeReducer(
            ReaderChromeState(controlsVisible = false),
            ReaderChromeEvent.Enter,
        )
        assertTrue(state.controlsVisible)
    }

    @Test
    fun `center tap toggles controls both ways`() {
        val hidden = readerChromeReducer(ReaderChromeState(), ReaderChromeEvent.CenterTap)
        assertFalse(hidden.controlsVisible)
        assertTrue(readerChromeReducer(hidden, ReaderChromeEvent.CenterTap).controlsVisible)
    }

    @Test
    fun `page turn hides controls regardless of auto hide setting`() {
        // 0 秒 = 不定时隐藏：翻页仍应立即隐藏菜单（规格 4）。
        val state = readerChromeReducer(ReaderChromeState(), ReaderChromeEvent.PageTurn)
        assertFalse(state.controlsVisible)
    }

    @Test
    fun `auto hide elapsed hides controls`() {
        val state = readerChromeReducer(ReaderChromeState(), ReaderChromeEvent.AutoHideElapsed(4))
        assertFalse(state.controlsVisible)
    }

    @Test
    fun `auto hide elapsed with seconds keeps original state while sheet is open`() {
        val open = readerChromeReducer(ReaderChromeState(), ReaderChromeEvent.SheetOpen)
        val state = readerChromeReducer(open, ReaderChromeEvent.AutoHideElapsed(4))
        assertTrue(state.controlsVisible)
        assertTrue(state.sheetOpen)
    }

    @Test
    fun `auto hide elapsed with zero seconds never hides`() {
        // autoHide=0 的"不隐藏"计时器若从 effect 竞态漏发：事件携带 0 秒 → 防御不隐藏。
        val state = readerChromeReducer(ReaderChromeState(), ReaderChromeEvent.AutoHideElapsed(0))
        assertTrue(state.controlsVisible)
    }

    @Test
    fun `auto hide elapsed with positive seconds hides when no sheet`() {
        val state = readerChromeReducer(ReaderChromeState(), ReaderChromeEvent.AutoHideElapsed(4))
        assertFalse(state.controlsVisible)
    }

    @Test
    fun `page turn still hides controls even while sheet is open`() {
        // 翻页隐藏语义不受 sheet 门控影响（与 AutoHideElapsed 不同）。
        val open = readerChromeReducer(ReaderChromeState(), ReaderChromeEvent.SheetOpen)
        val state = readerChromeReducer(open, ReaderChromeEvent.PageTurn)
        assertFalse(state.controlsVisible)
        assertTrue(state.sheetOpen)
    }

    @Test
    fun `sheet open keeps menu visible and pauses auto hide timer`() {
        val state = readerChromeReducer(ReaderChromeState(), ReaderChromeEvent.SheetOpen)
        assertTrue(state.controlsVisible)
        assertTrue(state.sheetOpen)
        assertTrue(state.autoHidePaused)
    }

    @Test
    fun `sheet open does not resurrect hidden controls`() {
        val hidden = readerChromeReducer(ReaderChromeState(), ReaderChromeEvent.PageTurn)
        val state = readerChromeReducer(hidden, ReaderChromeEvent.SheetOpen)
        assertFalse(state.controlsVisible)
        assertTrue(state.sheetOpen)
    }

    @Test
    fun `sheet close resumes timer without touching visibility`() {
        val open = readerChromeReducer(ReaderChromeState(), ReaderChromeEvent.SheetOpen)
        val closed = readerChromeReducer(open, ReaderChromeEvent.SheetClose)
        assertFalse(closed.sheetOpen)
        assertFalse(closed.autoHidePaused)
        assertTrue(closed.controlsVisible)
    }

    @Test
    fun `book switched resets controls to visible`() {
        val hidden = readerChromeReducer(ReaderChromeState(), ReaderChromeEvent.AutoHideElapsed(4))
        val switched = readerChromeReducer(hidden, ReaderChromeEvent.BookSwitched)
        assertTrue(switched.controlsVisible)
    }

    @Test
    fun `tts visibility is not part of chrome state`() {
        // TTS 栏由 ReaderScreenState.showTts 独立持有；chrome 状态机不得吞掉它。
        val state = readerChromeReducer(ReaderChromeState(), ReaderChromeEvent.CenterTap)
        assertFalse(state.controlsVisible)
    }
}
