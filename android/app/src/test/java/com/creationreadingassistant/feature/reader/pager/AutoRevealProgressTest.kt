package com.creationreadingassistant.feature.reader.pager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRevealProgressTest {

    @Test
    fun `half of interval advances progress to 0_5`() {
        val revealer = AutoRevealProgress()
        assertFalse(revealer.advance(elapsedNanos = 10_000_000_000L, intervalMillis = 20_000L))
        assertEquals(0.5f, revealer.value, 0.001f)
    }

    @Test
    fun `full interval reports page turned`() {
        val revealer = AutoRevealProgress()
        assertTrue(revealer.advance(elapsedNanos = 20_000_000_000L, intervalMillis = 20_000L))
        assertEquals(1.0f, revealer.value, 0.001f)
    }

    @Test
    fun `progress accumulates across frames`() {
        val revealer = AutoRevealProgress()
        var turned = false
        repeat(3) { if (revealer.advance(4_000_000_000L, 12_000L)) turned = true }
        assertFalse(turned)
        assertEquals(1.0f, revealer.value, 0.001f)
    }

    @Test
    fun `long stalled frame is capped to 250ms`() {
        val revealer = AutoRevealProgress()
        // 5 秒卡顿 / 20 秒间隔：最多按 250ms 算 → 0.0125
        revealer.advance(elapsedNanos = 5_000_000_000L, intervalMillis = 20_000L)
        assertEquals(0.0125f, revealer.value, 0.0001f)
    }

    @Test
    fun `invalid inputs advance nothing`() {
        val revealer = AutoRevealProgress()
        assertFalse(revealer.advance(0L, 20_000L))
        assertFalse(revealer.advance(16_000_000L, 0L))
        assertFalse(revealer.advance(-1L, 20_000L))
        assertEquals(0f, revealer.value, 0.001f)
    }

    @Test
    fun `reset returns progress to zero`() {
        val revealer = AutoRevealProgress()
        revealer.advance(10_000_000_000L, 20_000L)
        revealer.reset()
        assertEquals(0f, revealer.value, 0.001f)
    }

    @Test
    fun `freeze resume semantics preserve progress across instance reuse`() {
        // 暂停 = 不再喂帧（不 reset），恢复 = 继续喂：总时长仍是一个 interval
        val revealer = AutoRevealProgress()
        revealer.advance(8_000_000_000L, 20_000L) // 0.4
        assertTrue(revealer.advance(12_000_000_000L, 20_000L))
        assertEquals(1.0f, revealer.value, 0.001f)
    }
}
