package com.creationreadingassistant.feature.reader.pager

/**
 * 自动翻页揭动画的进度状态机。
 *
 * 对齐 legado AutoPager：动画时长 = 页间隔，progress 匀速从 0 推进到 1，
 * 到 1 即整页揭开完成（由调用方提交翻页并归零）。切后台或卡顿后不补揭一大截：
 * 单帧最多按 [MAX_FRAME_NANOS] 计算，与 [AutoScrollAccumulator] 同一纪律。
 *
 * 纯逻辑类：不持有协程作用域，由调用方逐帧喂 elapsed；暂停/恢复语义由调用方
 * 决定（保留实例 = 冻结续跑，reset = 重计时）。
 */
class AutoRevealProgress {

    private var progress = 0.0
    private var completed = false

    val value: Float get() = progress.toFloat()

    /** 按一帧经过时间推进进度；返回 true 表示已揭开整页（progress 到 1）。 */
    fun advance(elapsedNanos: Long, intervalMillis: Long): Boolean {
        if (completed || elapsedNanos <= 0L || intervalMillis <= 0L) return false
        val safeElapsed = elapsedNanos.coerceAtMost(MAX_FRAME_NANOS)
        progress += safeElapsed / 1_000_000.0 / intervalMillis
        if (progress >= 1.0) {
            // 锁定到 1：同一周期内后续帧不再重复触发翻页，直到 reset()。
            completed = true
            progress = 1.0
            return true
        }
        return false
    }

    fun reset() {
        progress = 0.0
        completed = false
    }

    private companion object {
        // 切后台或卡顿后不补揭一大截；恢复时最多按 250ms 计算。
        const val MAX_FRAME_NANOS = 250_000_000L
    }
}
