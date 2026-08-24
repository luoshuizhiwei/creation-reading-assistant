package com.creationreadingassistant.feature.reader.pager

import org.junit.Assert.assertEquals
import org.junit.Test

class PagedReaderViewportGeometryTest {
    @Test
    fun `page turn viewport stays full width while text margins live inside the page`() {
        val geometry = pagedReaderViewportGeometry(
            viewportWidthPx = 1220f,
            requestedMarginPx = 65f,
            maxTextWidthPx = 1050f,
        )

        assertEquals(1220f, geometry.viewportWidthPx, 0.001f)
        assertEquals(1050f, geometry.contentWidthPx, 0.001f)
        assertEquals(85f, geometry.contentInsetPx, 0.001f)
    }

    @Test
    fun `narrow viewport keeps symmetric minimum content instead of negative width`() {
        val geometry = pagedReaderViewportGeometry(
            viewportWidthPx = 320f,
            requestedMarginPx = 190f,
            maxTextWidthPx = 1000f,
        )

        assertEquals(320f, geometry.viewportWidthPx, 0.001f)
        assertEquals(160f, geometry.contentWidthPx, 0.001f)
        assertEquals(80f, geometry.contentInsetPx, 0.001f)
    }
}
