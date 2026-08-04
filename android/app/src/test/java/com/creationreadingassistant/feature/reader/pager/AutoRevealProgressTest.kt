package com.creationreadingassistant.feature.reader.pager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRevealProgressTest {

    @Test
    fun `half of interval advances progress to 0_5`() {
        // 单帧上限 250ms(防卡顿补揭)；累积 40 帧 × 250ms = 半个 20s 间隔
        val revealer = AutoRevealProgress()
        var turned = false
        repeat(40) { if (revealer.advance(elapsedNanos = 250_000_000L, intervalMillis = 20_000L)) turned = true }
        assertFalse(turned)
        assertEquals(0.5f, revealer.value, 0.001f)
    }

    @Test
    fun `full interval reports page turned`() {
        // 累积越过一个 20s 间隔 → 整页揭开（100 帧确保越过 1.0，规避浮点累积误差）
        val revealer = AutoRevealProgress()
        var turned = false
        repeat(100) { if (revealer.advance(elapsedNanos = 250_000_000L, intervalMillis = 20_000L)) turned = true }
        assertTrue(turned)
        assertTrue(revealer.value >= 1.0f)
    }

    @Test
    fun `progress accumulates across frames`() {
        // 每帧被 cap 到 250ms：3 × (250/12000) = 0.0625，跨帧累积不重置
        val revealer = AutoRevealProgress()
        var turned = false
        repeat(3) { if (revealer.advance(4_000_000_000L, 12_000L)) turned = true }
        assertFalse(turned)
        assertEquals(0.0625f, revealer.value, 0.001f)
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
        // 暂停 = 不再喂帧（不 reset），恢复 = 继续喂：累计有效时长越过一个 interval
        val revealer = AutoRevealProgress()
        repeat(16) { revealer.advance(elapsedNanos = 250_000_000L, intervalMillis = 20_000L) } // ~0.2
        var turned = false
        repeat(100) { if (revealer.advance(elapsedNanos = 250_000_000L, intervalMillis = 20_000L)) turned = true }
        assertTrue(turned)
        assertTrue(revealer.value >= 1.0f)
    }
}
