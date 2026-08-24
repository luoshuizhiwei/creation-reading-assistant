package com.creationreadingassistant.feature.reader.pager

internal data class PagedReaderViewportGeometry(
    val viewportWidthPx: Float,
    val contentWidthPx: Float,
    val contentInsetPx: Float,
)

/** Keeps page animation full-width while constraining the text column inside each page. */
internal fun pagedReaderViewportGeometry(
    viewportWidthPx: Float,
    requestedMarginPx: Float,
    maxTextWidthPx: Float,
): PagedReaderViewportGeometry {
    val viewport = viewportWidthPx.coerceAtLeast(1f)
    val minimumContent = viewport * 0.5f
    val requestedContent = (viewport - requestedMarginPx.coerceAtLeast(0f) * 2f)
        .coerceAtLeast(minimumContent)
    val content = minOf(requestedContent, maxTextWidthPx.coerceAtLeast(minimumContent), viewport)
    return PagedReaderViewportGeometry(
        viewportWidthPx = viewport,
        contentWidthPx = content,
        contentInsetPx = (viewport - content) / 2f,
    )
}
