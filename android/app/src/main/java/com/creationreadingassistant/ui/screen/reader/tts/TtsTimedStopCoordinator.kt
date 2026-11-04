package com.creationreadingassistant.ui.screen.reader.tts

/**
 * 定时停止宿主：由 [TtsController] 实现。协调器只决定「何时」动作，
 * 具体音量写入与最终停止动作交回宿主，保证抽取前后播放行为逐项一致。
 */
internal interface TtsTimedStopHost {
    /** 当前是否处于播放态。到期时若已非播放态，则不淡出、直接执行最终停止。 */
    fun isPlaying(): Boolean

    /** 淡出起始音量（0..1）。 */
    fun currentVolume(): Float

    /** 写入控制器音量；[apply] 为 true 时同时作用到音频流。 */
    fun setVolume(volume: Float, apply: Boolean)

    /** 定时停止最终动作（生产实现为 `TtsController.stop()`）。 */
    fun onTimedStop()
}

/**
 * 睡眠定时（定时停止）生命周期协调器。
 *
 * 从 [TtsController] 抽出的「计时 → 淡出 → 停止」链路，只依赖注入的时钟与调度函数，
 * 因而可以在纯 JVM 上用虚拟时钟覆盖到期、关闭、改时、stop / 播完 / release 与失效回调，
 * 既不需要 sleep，也不需要 Android 框架或新增测试依赖。
 *
 * 生产语义（抽取前后一致，由 [TtsController] 以主线程 Handler 接线）：
 * - [arm] 按分钟武装；到期前 [FADE_DURATION_MS] 触发，按 [FADE_INTERVAL_MS] 阶梯淡出，
 *   随后执行最终停止，并把控制器音量恢复为淡出前的值；
 * - 每次 [arm] 先取消旧计时；旧回调凭代号失效，不会作用于改时后的新计时；
 * - [release] 后 [arm] 恒为 false，任何已出队/已排队的旧回调都不得再产生动作。
 *
 * 本类不感知章节：是否「每章重置」由调用方在每次 play / resume 时显式 [arm] 决定，
 * 与抽取前 `scheduleTimedStop()` 在 `play()` / `resume()` 中调用的既有语义相同，
 * 本轮未改变该调用点，也未在协调器内新增章节概念。
 */
internal class TtsTimedStopCoordinator(
    private val clock: () -> Long,
    private val postDelayed: (delayMs: Long, action: Runnable) -> Unit,
    private val removeCallbacks: (action: Runnable) -> Unit,
    private val host: TtsTimedStopHost,
) {
    /** 当前定时停止的到期时刻（[clock] 口径，毫秒）；未定时为 null。 */
    var deadlineMs: Long? = null
        private set

    /** 是否已释放；释放后不得再武装，且所有旧回调失效。 */
    var isReleased: Boolean = false
        private set

    /** 代号：每次取消 / 重新武装自增，用于作废已排队的旧回调。 */
    private var generation = 0
    private var timerAction: Runnable? = null
    private var fadeAction: Runnable? = null
    private var fadeStartVolume: Float? = null
    private var fadeInitialVolume = 0f
    private var fadeStep = 0
    private var fadeTotalSteps = 0

    /**
     * 按分钟武装定时停止。`minutes <= 0` 表示用户关闭定时；已释放时不再武装。
     * 两种情况都只取消旧计时并返回 false。
     */
    fun arm(minutes: Int): Boolean {
        cancel()
        if (isReleased || minutes <= 0) return false
        val totalDelay = minutes * MINUTE_MS
        deadlineMs = clock() + totalDelay
        val gen = generation
        val triggerDelay = if (totalDelay > FADE_DURATION_MS) totalDelay - FADE_DURATION_MS else totalDelay
        val action = Runnable { onTimerDue(gen, totalDelay) }
        timerAction = action
        postDelayed(triggerDelay, action)
        return true
    }

    /** 取消当前计时与进行中的淡出，并恢复淡出前音量；不影响播放态本身。 */
    fun cancel() {
        generation++
        timerAction?.let { removeCallbacks(it) }
        timerAction = null
        fadeAction?.let { removeCallbacks(it) }
        fadeAction = null
        deadlineMs = null
        restoreFadeVolume(apply = true)
    }

    /** 仅取消进行中的淡出并恢复起始音量（用户手动调节音量时），不影响尚未到期的计时。 */
    fun cancelFade() {
        if (fadeStartVolume == null) return
        fadeAction?.let { removeCallbacks(it) }
        fadeAction = null
        restoreFadeVolume(apply = true)
    }

    /** 释放：取消计时并作废所有已排队的旧回调；之后 [arm] 恒为 false。 */
    fun release() {
        cancel()
        isReleased = true
    }

    /** 剩余秒数（向下取整、下限 0）；未定时为 0。 */
    fun remainingSeconds(): Int {
        val deadline = deadlineMs ?: return 0
        return ((deadline - clock()) / 1000L).coerceAtLeast(0L).toInt()
    }

    private fun onTimerDue(gen: Int, totalDelay: Long) {
        if (!isCurrent(gen)) return
        timerAction = null
        val durationMs = FADE_DURATION_MS.coerceAtMost(totalDelay)
        if (!host.isPlaying()) {
            // 到期时已不在播放（暂停/停止/播完）：不淡出，直接走最终停止，与抽取前一致。
            completeFade(restoreVolume = false)
            return
        }
        fadeInitialVolume = host.currentVolume()
        fadeStartVolume = fadeInitialVolume
        fadeStep = 0
        fadeTotalSteps = (durationMs / FADE_INTERVAL_MS).coerceAtLeast(1L).toInt()
        val action = Runnable { onFadeTick(gen) }
        fadeAction = action
        postDelayed(0L, action)
    }

    private fun onFadeTick(gen: Int) {
        if (!isCurrent(gen)) return
        fadeStep++
        val action = fadeAction
        if (fadeStep >= fadeTotalSteps || !host.isPlaying()) {
            fadeAction = null
            completeFade(restoreVolume = true)
            return
        }
        val ratio = 1f - (fadeStep.toFloat() / fadeTotalSteps)
        host.setVolume((fadeInitialVolume * ratio).coerceIn(0f, 1f), apply = true)
        if (action != null) postDelayed(FADE_INTERVAL_MS, action)
    }

    private fun completeFade(restoreVolume: Boolean) {
        // 先取走并清空起始音量：最终停止会回调宿主，宿主内部会再次 cancel()，
        // 此刻必须已无「进行中的淡出」，否则会重复恢复音量（与抽取前一致）。
        val saved = fadeStartVolume
        fadeStartVolume = null
        host.onTimedStop()
        if (restoreVolume && saved != null) host.setVolume(saved, apply = false)
    }

    private fun restoreFadeVolume(apply: Boolean) {
        val saved = fadeStartVolume ?: return
        fadeStartVolume = null
        host.setVolume(saved, apply = apply)
    }

    private fun isCurrent(gen: Int): Boolean = !isReleased && gen == generation

    private companion object {
        const val MINUTE_MS = 60_000L
        const val FADE_DURATION_MS = 3_000L
        const val FADE_INTERVAL_MS = 100L
    }
}
