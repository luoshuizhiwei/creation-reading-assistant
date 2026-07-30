package com.creationreadingassistant.feature.reader.doc

import java.io.FilterInputStream
import java.io.InputStream

/**
 * Test helper that wraps an [InputStream] and tracks close() calls.
 * Useful for verifying that code properly closes (or deliberately does not close) streams.
 */
class CloseTrackingInputStream(
    delegate: InputStream
) : FilterInputStream(delegate) {
    var isClosed = false
        private set
    var closeCount = 0
        private set

    override fun close() {
        isClosed = true
        closeCount++
        super.close()
    }
}
