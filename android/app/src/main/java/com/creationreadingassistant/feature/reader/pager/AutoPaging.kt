package com.creationreadingassistant.feature.reader.pager

/**
 * 自动翻页的统一速度模型。
 *
 * 速度 1..10 映射到「翻完一屏所需时间」93..12 秒；分页模式直接使用该间隔，
 * 滚动模式则按同一时长把一个视口匀速滚完，两个模式的体感不会突然跳档。
 */
object AutoPagingTiming {
    const val MIN_SPEED = 1
    const val MAX_SPEED = 10

    fun pageIntervalMillis(speed: Int): Long {
        val level = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        return (102L - level * 9L) * 1_000L
    }
}

/**
 * 滚动模式的亚像素累加器。
 *
 * 每帧位移通常小于 1px，若直接转 Int 会长期变成 0。这里用 Double 保存余数，
 * 只在累计满整像素时交给 LazyListState 消费。
 */
class AutoScrollAccumulator {
    private var pendingPixels = 0.0

    fun consume(speed: Int, viewportHeightPx: Int, elapsedNanos: Long): Int {
        if (viewportHeightPx <= 0 || elapsedNanos <= 0L) return 0
        val safeElapsed = elapsedNanos.coerceAtMost(MAX_FRAME_NANOS)
        val intervalSeconds = AutoPagingTiming.pageIntervalMillis(speed) / 1_000.0
        pendingPixels += viewportHeightPx * (safeElapsed / 1_000_000_000.0) / intervalSeconds
        val wholePixels = pendingPixels.toInt()
        pendingPixels -= wholePixels
        return wholePixels
    }

    fun reset() {
        pendingPixels = 0.0
    }

    private companion object {
        // 切后台或卡顿后不补滚一大截；恢复时最多按 250ms 计算。
        const val MAX_FRAME_NANOS = 250_000_000L
    }
}
