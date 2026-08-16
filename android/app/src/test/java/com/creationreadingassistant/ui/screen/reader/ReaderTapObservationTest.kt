package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * readerTextFieldCenterTapToToggle 的可 JVM 测试纯逻辑：
 * 中央区域判定 + 父/子观察器共享的"单一路径"仲裁门 + 消费路由（down/up 消费）
 * 与长按边界判定。
 *
 * 指针事件流本身（slop、多指、readOnly 文本域消费行为）只能在真机/设备上复核，
 * 见 ReaderTapObservation.kt 中的注释。
 */
class ReaderTapObservationTest {

    @Test
    fun `center zone is the middle third of the field width`() {
        assertTrue(isCenterTapZone(150f, 450f)) // 左边界包含
        assertTrue(isCenterTapZone(300f, 450f)) // 右边界包含
        assertTrue(isCenterTapZone(220f, 450f))
    }

    @Test
    fun `left and right thirds are not the center zone`() {
        assertFalse(isCenterTapZone(149.9f, 450f))
        assertFalse(isCenterTapZone(300.1f, 450f))
        assertFalse(isCenterTapZone(0f, 450f))
    }

    @Test
    fun `degenerate width never counts as center`() {
        assertFalse(isCenterTapZone(0f, 0f))
        assertFalse(isCenterTapZone(10f, -5f))
    }

    @Test
    fun `parent consumes a claimed gesture exactly once`() {
        val gate = ReaderTapToggleGate()
        gate.claim()
        assertTrue(gate.consumeClaimIfAny())
        assertFalse(gate.consumeClaimIfAny())
    }

    @Test
    fun `parent without a claim fires normally`() {
        val gate = ReaderTapToggleGate()
        assertFalse(gate.consumeClaimIfAny())
    }

    @Test
    fun `child clearing the claim after gesture end releases the gate`() {
        val gate = ReaderTapToggleGate()
        gate.claim()
        gate.clear()
        assertFalse(gate.consumeClaimIfAny())
    }

    @Test
    fun `child handles tap only when down or final up was consumed`() {
        // 完全不消费：交给父层 readerCenterTapToToggle，避免同一手势双 toggle。
        assertFalse(shouldChildHandleTap(downWasConsumed = false, finalUpIsConsumed = false))
        assertTrue(shouldChildHandleTap(downWasConsumed = true, finalUpIsConsumed = false))
        assertTrue(shouldChildHandleTap(downWasConsumed = false, finalUpIsConsumed = true))
        assertTrue(shouldChildHandleTap(downWasConsumed = true, finalUpIsConsumed = true))
    }

    @Test
    fun `tap at or under the long press timeout is not a long press`() {
        val timeout = 500L
        val downTime = 1_000L
        assertFalse(isLongPress(downTime, downTime, timeout)) // 瞬时抬起
        assertFalse(isLongPress(downTime, downTime + timeout - 1L, timeout)) // 未超时
        assertFalse(isLongPress(downTime, downTime + timeout, timeout)) // 恰好等于超时：仍算短按
    }

    @Test
    fun `tap exceeding the long press timeout is a long press and must not fire`() {
        val timeout = 500L
        // 覆盖 down→up 之间无任何中间事件、只有一次到位抬起的长按。
        assertTrue(isLongPress(1_000L, 1_000L + timeout + 1L, timeout))
    }
}
