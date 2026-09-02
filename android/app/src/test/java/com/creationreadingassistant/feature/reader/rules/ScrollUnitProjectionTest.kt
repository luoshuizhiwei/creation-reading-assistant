package com.creationreadingassistant.feature.reader.rules

import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollUnitProjectionTest {

    private val crossUnitRule = ReplaceRule(
        id = "cross-unit",
        name = "cross-unit",
        pattern = "XYZ",
        replacement = "Q",
        enabled = true,
        position = 0,
        scope = RuleScope.GLOBAL,
    )

    @Test
    fun `adjacent unit projections tile a full chapter replacement without gaps`() {
        val exact = BoundedReplaceProjector.project(
            scopeSource = "abXYZcd",
            rules = listOf(crossUnitRule),
            bookId = "book",
            scopeSourceBase = 100,
        ) as BoundedReplaceResult.Exact
        val first = ScrollUnitProjection.fromExact(unit(0, 100, 3), exact)
        val second = ScrollUnitProjection.fromExact(unit(1, 103, 4), exact)

        assertEquals("abQcd", requireNotNull(first).displayText + requireNotNull(second).displayText)
        assertEquals(exact.projection.displayText, first.displayText + second.displayText)
    }

    @Test
    fun `display selection maps back to global source and source highlights map to local display`() {
        val exact = BoundedReplaceProjector.project(
            scopeSource = "abXYZcd",
            rules = listOf(crossUnitRule),
            bookId = "book",
            scopeSourceBase = 100,
        ) as BoundedReplaceResult.Exact
        val first = requireNotNull(ScrollUnitProjection.fromExact(unit(0, 100, 3), exact))
        val second = requireNotNull(ScrollUnitProjection.fromExact(unit(1, 103, 4), exact))

        assertEquals("abQ", first.displayText)
        assertEquals("cd", second.displayText)
        assertEquals(102, first.localDisplayToGlobalSource(2))
        assertEquals(103, first.localDisplayToGlobalSource(3))
        assertEquals(2 to 3, first.globalSourceRangeToLocalDisplay(102, 105))
        assertEquals(0 to 2, second.globalSourceRangeToLocalDisplay(105, 107))
    }

    @Test
    fun `unit outside the exact source scope is rejected instead of producing a partial projection`() {
        val exact = BoundedReplaceProjector.project(
            scopeSource = "chapter",
            rules = emptyList(),
            bookId = "book",
            scopeSourceBase = 100,
        ) as BoundedReplaceResult.Exact

        assertNull(ScrollUnitProjection.fromExact(unit(0, 99, 3), exact))
        assertNull(ScrollUnitProjection.fromExact(unit(1, 106, 3), exact))
        assertTrue(ScrollUnitProjection.fromExact(unit(2, 100, 7), exact) != null)
    }

    private fun unit(index: Int, start: Int, length: Int) = ReadingUnit(
        unitIndex = index,
        chapterIndex = 0,
        title = "chapter",
        charStart = start,
        charCount = length,
    )
}
