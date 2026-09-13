package com.creationreadingassistant.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 锁定 count-up 的起始值语义。
 *
 * 这条规则的用户可见后果是「切回统计页时数字会不会又跳一次」：只要起始值等于目标值，
 * `rememberCountUp` 就一次动画都不会跑，数字完全静止。回归这里等于回归那个体验。
 */
class CountUpStartValueTest {

    @Test
    fun `reduced motion always starts at target so nothing ever rolls`() {
        assertEquals(128f, countUpStartValue(remembered = null, target = 128, reducedMotion = true))
        assertEquals(128f, countUpStartValue(remembered = 0, target = 128, reducedMotion = true))
        assertEquals(128f, countUpStartValue(remembered = 96, target = 128, reducedMotion = true))
    }

    @Test
    fun `first sighting starts from zero to keep the intro roll`() {
        assertEquals(0f, countUpStartValue(remembered = null, target = 128, reducedMotion = false))
    }

    @Test
    fun `revisit starts from last shown value instead of zero`() {
        assertEquals(128f, countUpStartValue(remembered = 128, target = 128, reducedMotion = false))
        assertEquals(96f, countUpStartValue(remembered = 96, target = 128, reducedMotion = false))
    }

    @Test
    fun `unchanged value yields a start equal to the target which means no animation`() {
        // rememberCountUp 用「起始值 == 目标值」直接跳过动画，这里锁住这个前提。
        val target = 42
        val start = countUpStartValue(remembered = target, target = target, reducedMotion = false)
        assertEquals(start, target.toFloat())
    }

    @Test
    fun `value that really changed still animates from the old value`() {
        val start = countUpStartValue(remembered = 128, target = 160, reducedMotion = false)
        assertEquals(128f, start)
    }
}
