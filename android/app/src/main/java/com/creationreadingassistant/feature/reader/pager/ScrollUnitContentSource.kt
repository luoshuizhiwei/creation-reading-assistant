package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.rules.ScrollUnitProjection

/** Loads one scrolling unit and binds its display text to authoritative source coordinates. */
fun PagedChapterSource.loadScrollUnitContent(unit: ReadingUnit): ScrollUnitProjection {
    require(unit.unitIndex in 0 until chapterCount) { "ReadingUnit index is outside the source" }
    val content = loadChapter(unit.unitIndex)
    val exact = (this as? ProjectedChapterSource)?.projectionForChapter(unit.unitIndex)
    val projection = if (exact != null) {
        checkNotNull(ScrollUnitProjection.fromExact(unit, exact)) {
            "Projected scrolling unit is outside its complete logical-chapter scope"
        }
    } else {
        checkNotNull(ScrollUnitProjection.fromSource(unit, content.text)) {
            "Source scrolling unit length does not match its coordinate metadata"
        }
    }
    // 最后一跳：只输出单位序号、长度、是否拿到精确投影及作用域命中数。该日志
    // 仅 Debug 构建输出 logcat，用来证明 BasicTextField 前的 display 是投影还是原文。
    AppLog.debug(
        "ScrollReplaceTrace",
        "unit=${unit.unitIndex}, source=${javaClass.simpleName}, exact=${exact != null}, " +
            "scopeHits=${exact?.hitCount ?: 0}, sourceChars=${unit.charCount}, " +
            "displayChars=${projection.displayText.length}",
    )
    return projection
}
