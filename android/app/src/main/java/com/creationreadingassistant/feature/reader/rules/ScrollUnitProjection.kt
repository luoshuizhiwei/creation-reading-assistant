package com.creationreadingassistant.feature.reader.rules

import com.creationreadingassistant.feature.reader.doc.ReadingUnit

/**
 * A single scrolling TXT unit derived from a complete logical-chapter projection.
 * Persistent coordinates remain global source offsets; [displayText] is render-only.
 */
class ScrollUnitProjection private constructor(
    val displayText: String,
    val sourceStartAbs: Int,
    val sourceEndAbs: Int,
    private val displayToSource: (Int) -> Int,
    private val sourceToDisplay: (Int) -> Int,
) {
    /** Local display offset to the authoritative global source offset. */
    fun localDisplayToGlobalSource(localDisplayOffset: Int): Int =
        displayToSource(localDisplayOffset.coerceIn(0, displayText.length))

    /** Authoritative global source offset to this unit's local display offset. */
    fun globalSourceToLocalDisplay(globalSourceOffset: Int): Int {
        val localSource = (globalSourceOffset - sourceStartAbs)
            .coerceIn(0, sourceEndAbs - sourceStartAbs)
        return sourceToDisplay(localSource).coerceIn(0, displayText.length)
    }

    /** Maps a global source half-open range to a local display half-open range. */
    fun globalSourceRangeToLocalDisplay(sourceStart: Int, sourceEnd: Int): Pair<Int, Int> {
        val start = globalSourceToLocalDisplay(sourceStart)
        val end = globalSourceToLocalDisplay(sourceEnd)
        return minOf(start, end) to maxOf(start, end)
    }

    companion object {
        /**
         * Returns null when the unit is not wholly backed by [exact]. Callers must retain the
         * original unit text in that case; partial or guessed replacement is forbidden.
         */
        fun fromExact(
            unit: ReadingUnit,
            exact: BoundedReplaceResult.Exact,
        ): ScrollUnitProjection? {
            val unitEnd = unit.charStart.toLong() + unit.charCount.toLong()
            val scopeEnd = exact.scopeSourceBase.toLong() + exact.sourceLength.toLong()
            if (unit.charStart < exact.scopeSourceBase || unitEnd > scopeEnd || unitEnd > Int.MAX_VALUE) {
                return null
            }

            val localStart = unit.charStart - exact.scopeSourceBase
            val localEnd = localStart + unit.charCount
            val slice = exact.projection.displaySliceForSourceRange(localStart, localEnd)
            return ScrollUnitProjection(
                displayText = slice.text,
                sourceStartAbs = unit.charStart,
                sourceEndAbs = unitEnd.toInt(),
                displayToSource = { local ->
                    exact.scopeSourceBase + slice.displayToSource(local)
                },
                sourceToDisplay = { local ->
                    slice.sourceToDisplay(local) - slice.displayBase
                },
            )
        }

        /** Identity projection used when no rule applies or a complete scope is unavailable. */
        fun fromSource(unit: ReadingUnit, sourceText: String): ScrollUnitProjection? {
            if (sourceText.length != unit.charCount) return null
            val end = unit.charStart.toLong() + unit.charCount.toLong()
            if (end > Int.MAX_VALUE) return null
            return ScrollUnitProjection(
                displayText = sourceText,
                sourceStartAbs = unit.charStart,
                sourceEndAbs = end.toInt(),
                displayToSource = { local -> unit.charStart + local.coerceIn(0, sourceText.length) },
                sourceToDisplay = { local -> local.coerceIn(0, sourceText.length) },
            )
        }
    }
}
