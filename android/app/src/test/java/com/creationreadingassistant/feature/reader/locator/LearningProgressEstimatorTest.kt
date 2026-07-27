package com.creationreadingassistant.feature.reader.locator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningProgressEstimatorTest {
    @Test
    fun `known chinese chapter teaches the byte-to-character ratio`() {
        val estimated = listOf(300, 600, 300)
        val actual = mapOf(0 to 100)
        val percent = LearningProgressEstimator.percent(
            chapterIndex = 1,
            charOffset = 100,
            estimatedCounts = estimated,
            actualCounts = actual,
        )
        // 学到 1/3 后，全书估为 400 字；位置为 100 + 100。
        assertEquals(50f, percent, 0.01f)
    }

    @Test
    fun `more measured chapters converge without moving backwards inside chapter`() {
        val estimated = listOf(300, 600, 300)
        val actual = mapOf(0 to 100, 1 to 240)
        val p1 = LearningProgressEstimator.percent(1, 60, estimated, actual)
        val p2 = LearningProgressEstimator.percent(1, 120, estimated, actual)
        assertTrue(p2 > p1)
        assertEquals(100f, LearningProgressEstimator.percent(2, 999, estimated, actual, atBookEnd = true), 0f)
    }
}
