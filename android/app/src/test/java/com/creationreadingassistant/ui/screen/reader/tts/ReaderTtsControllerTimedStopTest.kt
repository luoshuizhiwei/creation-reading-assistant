package com.creationreadingassistant.ui.screen.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务 CRA-TTS-TIMER-LIFECYCLE-EVIDENCE-v1：睡眠定时（定时停止）生命周期回归证据。
 *
 * 覆盖的真实生产链路是 [TtsController] 里的定时停止接线：
 * `play()/resume()` → `scheduleTimedStop()` → [TtsTimedStopCoordinator.arm]；
 * `stop()/pause()/末句 onDone/reinitialize()` → `clearTimedStop()` → [TtsTimedStopCoordinator.cancel]；
 * `release()` → [TtsTimedStopCoordinator.release]。
 *
 * 本轮把「计时 → 淡出 → 停止」链路抽成 [TtsTimedStopCoordinator]，由 [TtsController] 用主线程
 * Handler 实际接线（见 ReaderTtsController.kt 的 `timedStop` 字段）。因此这里用虚拟时钟 +
 * 虚拟调度器驱动**生产代码本体**，不做 sleep，也不需要 Robolectric 或任何新增依赖。
 *
 * 本文件只补「定时到期 / 关闭 / 改时 / 停止清理 / 释放 / 跨章」这一缺口；
 * 不重复跨章朗读的设备证据，也不构成实体音频或完整 TTS 通过结论。
 */
class ReaderTtsControllerTimedStopTest {

    // ── 虚拟时钟与虚拟调度器（纯 JVM） ────────────────────────────

    private class VirtualClock(var now: Long = 0L)

    /**
     * 虚拟调度器。与主线程 Handler 的两点关键差异都要显式模拟：
     * 1. 回调执行时时钟必须等于该消息的计划时刻（空闲 Looper 就是这样派发的），
     *    否则 `postDelayed(this, 100)` 会按推进后的「当前时刻」重排，相对延时被静默累积；
     * 2. [removeCallbacks] 只能从 [queue] 里摘除，摘不掉已经出队的任务，
     *    与 `Handler.removeCallbacks` 对已派发消息无能为力一致（见 [dispatchUpTo]）。
     */
    private class VirtualScheduler(private val clock: VirtualClock) {
        private class Task(val dueAt: Long, val action: Runnable)

        private val queue = mutableListOf<Task>()
        private val dispatched = mutableListOf<Task>()

        fun postDelayed(delayMs: Long, action: Runnable) {
            queue += Task(clock.now + delayMs, action)
        }

        fun removeCallbacks(action: Runnable) {
            queue.removeAll { it.action === action }
        }

        /** 推进到 [t] 并按时间顺序逐条执行到期任务（等价空闲 Looper）。 */
        fun advanceAndRun(t: Long) {
            require(t >= clock.now) { "虚拟时钟不得回退：$t < ${clock.now}" }
            while (true) {
                val next = queue.filter { it.dueAt <= t }.minByOrNull { it.dueAt } ?: break
                queue.remove(next)
                clock.now = next.dueAt // 执行期间时钟停在该消息的计划时刻
                next.action.run()
            }
            clock.now = t
        }

        /**
         * 只推进时间并把到期任务移入「已出队、未执行」，不运行任何回调：
         * 用于构造「旧回调已经排队」的窗口。
         */
        fun dispatchUpTo(t: Long) {
            require(t >= clock.now) { "虚拟时钟不得回退：$t < ${clock.now}" }
            clock.now = t
            val due = queue.filter { it.dueAt <= t }
            queue.removeAll(due)
            dispatched += due
        }

        /** 执行所有已出队回调。 */
        fun runDispatched() {
            while (dispatched.isNotEmpty()) dispatched.removeAt(0).action.run()
        }

        /** 仍未执行的任务数（在队列里 + 已出队）。 */
        fun pendingCount(): Int = queue.size + dispatched.size
    }

    /** 记录协调器对宿主做了什么：音量写入序列与最终停止次数。 */
    private class FakeHost(
        var playing: Boolean = true,
        var volume: Float = 1f,
    ) : TtsTimedStopHost {
        var stopCount = 0
        val volumeWrites = mutableListOf<Pair<Float, Boolean>>()

        override fun isPlaying(): Boolean = playing
        override fun currentVolume(): Float = volume

        override fun setVolume(volume: Float, apply: Boolean) {
            this.volume = volume
            volumeWrites += volume to apply
        }

        override fun onTimedStop() {
            stopCount++
            playing = false
        }
    }

    private class Fixture {
        val clock = VirtualClock()
        val scheduler = VirtualScheduler(clock)
        val host = FakeHost()
        val coordinator = TtsTimedStopCoordinator(
            clock = { clock.now },
            postDelayed = scheduler::postDelayed,
            removeCallbacks = scheduler::removeCallbacks,
            host = host,
        )
    }

    // ── 1. 定时到期：走实际淡出合同，且只停止一次 ────────────────

    @Test
    fun `timed stop fades out then stops exactly once`() {
        val f = Fixture()
        assertTrue(f.coordinator.arm(1))
        assertEquals(60_000L, f.coordinator.deadlineMs ?: -1L)

        // 到期前 1ms 不应有任何动作
        f.scheduler.advanceAndRun(56_999L)
        assertEquals(0, f.host.stopCount)
        assertEquals(0, f.host.volumeWrites.size)

        // 到期前 3 秒是淡出起点（60s - 3s），此处开始阶梯衰减，但还没停止
        f.scheduler.advanceAndRun(57_000L)
        assertEquals("淡出刚开始时不得停止", 0, f.host.stopCount)
        assertEquals("淡出第一步必须写入音量并作用到音频", 1, f.host.volumeWrites.size)
        assertEquals(1f - 1f / 30f, f.host.volumeWrites.first().first, 1e-4f)
        assertTrue("淡出步进必须实际作用到音频流", f.host.volumeWrites.first().second)

        // 走到到期时刻：淡出结束 → 执行最终停止 → 恢复淡出前音量（不再作用到音频流）
        f.scheduler.advanceAndRun(60_000L)
        assertEquals("到期只允许停止一次", 1, f.host.stopCount)
        assertEquals(30, f.host.volumeWrites.size) // 29 步衰减 + 1 次恢复
        assertEquals(1f, f.host.volumeWrites.last().first, 1e-4f)
        assertFalse("停止后的音量恢复不得再作用到音频流", f.host.volumeWrites.last().second)
        assertEquals(0, f.scheduler.pendingCount())

        // 再往后推进不得重复触发
        f.scheduler.advanceAndRun(10 * 60_000L)
        assertEquals("定时停止不得重复触发", 1, f.host.stopCount)
    }

    // ── 2. 关闭定时：旧回调不再停止当前播放 ──────────────────────

    @Test
    fun `disabling timed stop cancels the pending callback and keeps playing`() {
        val f = Fixture()
        assertTrue(f.coordinator.arm(1))
        f.scheduler.advanceAndRun(30_000L)
        assertEquals(0, f.host.stopCount)

        // 用户把定时改为「关闭」：setTimedStop(0) → scheduleTimedStop() → arm(0)
        assertFalse(f.coordinator.arm(0))
        assertNull(f.coordinator.deadlineMs)
        assertEquals(0, f.scheduler.pendingCount())
        assertEquals(0, f.coordinator.remainingSeconds())

        // 越过原到期时刻很久也不得停止，播放保持
        f.scheduler.advanceAndRun(30 * 60_000L)
        assertEquals("关闭定时后旧回调不得停止当前播放", 0, f.host.stopCount)
        assertEquals(0, f.host.volumeWrites.size)
        assertTrue(f.host.playing)
    }

    // ── 3. 关闭/取消时旧回调可能已经出队，仍不得动作 ─────────────

    @Test
    fun `cancel after the due callback was dispatched still does not act`() {
        val f = Fixture()
        assertTrue(f.coordinator.arm(1))

        // 到期回调已出队（Looper 已取出、尚未执行）
        f.scheduler.dispatchUpTo(60_000L)
        assertEquals(1, f.scheduler.pendingCount())

        f.coordinator.cancel()
        f.scheduler.runDispatched()

        assertEquals("已出队的旧回调必须凭代号失效", 0, f.host.stopCount)
        assertEquals(0, f.host.volumeWrites.size)
        assertNull(f.coordinator.deadlineMs)
    }

    // ── 4. 改时间：旧计时失效，新计时按实际语义重新计时 ──────────

    @Test
    fun `changing minutes invalidates the old timer and re-arms from now`() {
        val f = Fixture()
        assertTrue(f.coordinator.arm(30))
        val oldDeadline = f.coordinator.deadlineMs!!
        assertEquals(30 * 60_000L, oldDeadline)

        f.scheduler.advanceAndRun(5 * 60_000L)
        assertEquals(0, f.host.stopCount)

        // 用户改成 60 分钟：从「现在」重新计满 60 分钟（既有语义，未改）
        assertTrue(f.coordinator.arm(60))
        val newDeadline = f.coordinator.deadlineMs!!
        assertEquals(5 * 60_000L + 60 * 60_000L, newDeadline)

        f.scheduler.advanceAndRun(oldDeadline)
        assertEquals("旧计时必须失效", 0, f.host.stopCount)

        f.scheduler.advanceAndRun(newDeadline)
        assertEquals("新计时到期后必须停止", 1, f.host.stopCount)
    }

    // ── 5. 手动 stop 与播放完成：回调清理 ────────────────────────

    @Test
    fun `clearTimedStop used by manual stop and playback completion removes the pending callback`() {
        // 生产映射：stop()（手动停止 / 媒体 STOP / 引擎出错）与末句 onDone（播放完成）
        // 都调用 clearTimedStop()，在协调器上就是 cancel()。
        val f = Fixture()
        assertTrue(f.coordinator.arm(1))
        f.scheduler.advanceAndRun(20_000L)
        assertEquals(0, f.host.stopCount)

        f.coordinator.cancel()

        assertNull(f.coordinator.deadlineMs)
        assertEquals(0, f.scheduler.pendingCount())
        assertEquals(0, f.coordinator.remainingSeconds())
        f.scheduler.advanceAndRun(5 * 60_000L)
        assertEquals("清理后旧定时不得再停止", 0, f.host.stopCount)
        assertEquals(0, f.host.volumeWrites.size)
    }

    // ── 6. release：已排队旧回调失效，且不再武装新会话 ────────────

    @Test
    fun `release invalidates an already dispatched callback and refuses new sessions`() {
        val f = Fixture()
        assertTrue(f.coordinator.arm(1))

        // 到期回调已出队、尚未执行时发生 release（例如 ReaderTtsBar 退出调用 release()）
        f.scheduler.dispatchUpTo(60_000L)
        assertEquals(1, f.scheduler.pendingCount())

        f.coordinator.release()
        assertTrue(f.coordinator.isReleased)
        assertNull(f.coordinator.deadlineMs)

        f.scheduler.runDispatched()
        assertEquals("释放后旧回调不得停止已释放会话", 0, f.host.stopCount)
        assertEquals(0, f.host.volumeWrites.size)

        // 释放后不得再武装，也不会产生新的回调
        assertFalse(f.coordinator.arm(5))
        assertNull(f.coordinator.deadlineMs)
        f.scheduler.advanceAndRun(60 * 60_000L)
        assertEquals(0, f.host.stopCount)
        assertEquals(0, f.scheduler.pendingCount())
    }

    // ── 7. 跨章语义：保持既有行为，协调器不感知章节 ───────────────

    @Test
    fun `existing cross chapter semantics are preserved - each play re-arms the full duration`() {
        // 既有语义（本轮未改）：末句播完 onDone → clearTimedStop()，下一章 play() →
        // scheduleTimedStop() → 按完整时长重新计时。协调器本身没有章节概念，
        // 是否「每章重置」完全由调用方在 play/resume 时显式 arm 决定。
        val f = Fixture()
        assertTrue(f.coordinator.arm(1))
        f.scheduler.advanceAndRun(40_000L)
        assertEquals(0, f.host.stopCount)

        // 章末：clearTimedStop()
        f.coordinator.cancel()
        assertEquals(0, f.scheduler.pendingCount())

        // 下一章 play()：重新按完整 1 分钟计时（而不是沿用上一章剩下的 20 秒）
        assertTrue(f.coordinator.arm(1))
        assertEquals(40_000L + 60_000L, f.coordinator.deadlineMs ?: -1L)

        // 新一章的到期前 1ms：不得因为上一章的剩余时间而提前触发
        f.scheduler.advanceAndRun(96_999L)
        assertEquals("上一章剩余时间不得提前触发", 0, f.host.stopCount)
        f.scheduler.advanceAndRun(100_000L)
        assertEquals(1, f.host.stopCount)
    }

    // ── 8. 倒计时读数来自真实到期时刻 ────────────────────────────

    @Test
    fun `remaining seconds derive from the armed deadline`() {
        val f = Fixture()
        assertEquals(0, f.coordinator.remainingSeconds())

        assertTrue(f.coordinator.arm(2))
        assertEquals(120, f.coordinator.remainingSeconds())

        f.scheduler.dispatchUpTo(60_500L)
        assertEquals(59, f.coordinator.remainingSeconds())

        f.coordinator.cancel()
        assertEquals(0, f.coordinator.remainingSeconds())
    }
}
