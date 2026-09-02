package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.rules.ScrollUnitProjection

/** Loads one scrolling unit and binds its display text to authoritative source coordinates. */
fun PagedChapterSource.loadScrollUnitContent(unit: ReadingUnit): ScrollUnitProjection {
    require(unit.unitIndex in 0 until chapterCount) { "ReadingUnit index is outside the source" }
    val content = loadChapter(unit.unitIndex)
    val exact = (this as? ProjectedChapterSource)?.projectionForChapter(unit.unitIndex)
    if (exact != null) {
        return checkNotNull(ScrollUnitProjection.fromExact(unit, exact)) {
            "Projected scrolling unit is outside its complete logical-chapter scope"
        }
    }
    return checkNotNull(ScrollUnitProjection.fromSource(unit, content.text)) {
        "Source scrolling unit length does not match its coordinate metadata"
    }
}
