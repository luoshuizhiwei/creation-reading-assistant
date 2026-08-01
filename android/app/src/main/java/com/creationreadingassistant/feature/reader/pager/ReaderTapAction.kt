package com.creationreadingassistant.feature.reader.pager

/** A single, testable interpretation of taps on the reading canvas. */
enum class ReaderTapAction {
    PREVIOUS_PAGE,
    NEXT_PAGE,
    TOGGLE_CONTROLS,
    NONE,
}

fun resolveReaderTapAction(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    tapZoneMode: String,
    canPrevious: Boolean,
    canNext: Boolean,
): ReaderTapAction {
    if (width <= 0f || height <= 0f || x !in 0f..width || y !in 0f..height) {
        return ReaderTapAction.NONE
    }
    val requested = when {
        tapZoneMode == "five-zone" && y < height * 0.12f -> ReaderTapAction.PREVIOUS_PAGE
        tapZoneMode == "five-zone" && y > height * 0.88f -> ReaderTapAction.NEXT_PAGE
        x < width / 3f -> ReaderTapAction.PREVIOUS_PAGE
        x > width * 2f / 3f -> ReaderTapAction.NEXT_PAGE
        else -> ReaderTapAction.TOGGLE_CONTROLS
    }
    return when (requested) {
        ReaderTapAction.PREVIOUS_PAGE -> if (canPrevious) requested else ReaderTapAction.NONE
        ReaderTapAction.NEXT_PAGE -> if (canNext) requested else ReaderTapAction.NONE
        else -> requested
    }
}
