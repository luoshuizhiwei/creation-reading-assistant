package com.creationreadingassistant.feature.reader.eyecare

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EyeCareScheduleTest {
    @Test
    fun `same-day interval includes start and excludes end`() {
        assertTrue(EyeCareSchedule.isActive(9 * 60, 8 * 60, 18 * 60))
        assertFalse(EyeCareSchedule.isActive(18 * 60, 8 * 60, 18 * 60))
    }

    @Test
    fun `cross-midnight interval covers both sides of midnight`() {
        assertTrue(EyeCareSchedule.isActive(23 * 60, 22 * 60, 7 * 60))
        assertTrue(EyeCareSchedule.isActive(6 * 60, 22 * 60, 7 * 60))
        assertFalse(EyeCareSchedule.isActive(12 * 60, 22 * 60, 7 * 60))
    }
}
