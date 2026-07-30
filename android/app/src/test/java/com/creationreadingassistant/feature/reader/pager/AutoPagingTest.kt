package com.creationreadingassistant.feature.reader.pager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPagingTest {
    @Test
    fun `speed is clamped and faster levels shorten page interval`() {
        assertEquals(93_000L, AutoPagingTiming.pageIntervalMillis(1))
        assertEquals(12_000L, AutoPagingTiming.pageIntervalMillis(10))
        assertEquals(93_000L, AutoPagingTiming.pageIntervalMillis(-5))
        assertEquals(12_000L, AutoPagingTiming.pageIntervalMillis(99))
        assertTrue(
            AutoPagingTiming.pageIntervalMillis(4) >
                AutoPagingTiming.pageIntervalMillis(5),
        )
    }

    @Test
    fun `scroll accumulator retains subpixel remainder`() {
        val accumulator = AutoScrollAccumulator()
        var total = 0
        repeat(60) {
            total += accumulator.consume(
                speed = 10,
                viewportHeightPx = 1_200,
                elapsedNanos = 16_666_667L,
            )
        }

        // 10 档约 12 秒滚完一屏，1 秒应消费约 100px。
        assertTrue(total in 99..101)
    }

    @Test
    fun `long stalled frame is capped`() {
        val accumulator = AutoScrollAccumulator()
        val pixels = accumulator.consume(
            speed = 10,
            viewportHeightPx = 1_200,
            elapsedNanos = 5_000_000_000L,
        )

        // 最多补 250ms，避免从后台回来瞬移。
        assertTrue(pixels in 24..26)
    }

    @Test
    fun `invalid viewport or elapsed time consumes nothing`() {
        val accumulator = AutoScrollAccumulator()
        assertEquals(0, accumulator.consume(5, 0, 16_000_000L))
        assertEquals(0, accumulator.consume(5, 1_000, 0L))
    }
}
