package com.creationreadingassistant.feature.reader.locator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LearningProgressEstimatorTest {
    @Test
    fun `prepared progress agrees with original formula across chapter counts and invalid offsets`() {
        val random = Random(7341)
        repeat(150) {
            val estimated = List(random.nextInt(1, 300)) { random.nextInt(-10, 10000) }
            val actual = estimated.indices.filter { random.nextBoolean() }
                .associateWith { random.nextInt(-10, 10000) }
            val prepared = LearningProgressEstimator.prepare(estimated, actual)
            val knownEstimated = actual.keys.sumOf { estimated[it].coerceAtLeast(1).toLong() }
            val knownActual = actual.values.sumOf { it.coerceAtLeast(0).toLong() }
            val ratio = if (knownEstimated == 0L) 1.0 else
                (knownActual.toDouble() / knownEstimated).coerceIn(0.05, 1.0)
            val counts = estimated.mapIndexed { index, value ->
                actual[index]?.coerceAtLeast(0)?.toDouble() ?: (value.coerceAtLeast(1) * ratio)
            }
            for (chapter in listOf(-1, estimated.size / 2, estimated.size + 1)) {
                for (offset in listOf(-10, 0, 50, Int.MAX_VALUE)) {
                    val index = chapter.coerceIn(counts.indices)
                    val position = counts.take(index).sum() + offset.coerceIn(0, counts[index].toInt())
                    val expected = if (counts.sum() <= 0.0) 0f else
                        (position * 100.0 / counts.sum()).toFloat().coerceIn(0f, 100f)
                    assertEquals(expected, prepared.percent(chapter, offset), 0f)
                }
            }
        }
    }

    @Test
    fun `prepared progress is a snapshot and new metrics rebuild its ratio`() {
        val actual = mutableMapOf(0 to 100)
        val estimated = listOf(300, 600, 300)
        val first = LearningProgressEstimator.prepare(estimated, actual)
        assertEquals(50f, first.percent(1, 100), 0f)
        actual[1] = 240
        assertEquals(50f, first.percent(1, 100), 0f)
        val next = LearningProgressEstimator.prepare(estimated, actual)
        assertEquals(44.117645f, next.percent(1, 100), 0.00001f)
        assertEquals(100f, next.percent(2, 999, atBookEnd = true), 0f)
        assertEquals(0f, LearningProgressEstimator.prepare(emptyList(), actual).percent(0, 0, true), 0f)
    }

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
