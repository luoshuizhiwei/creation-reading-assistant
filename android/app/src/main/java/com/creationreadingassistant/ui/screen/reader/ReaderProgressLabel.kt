package com.creationreadingassistant.ui.screen.reader

import java.util.Locale

/** Keeps all reader progress surfaces aligned at the beginning of a book. */
internal fun readerProgressPercentLabel(progressPercent: Float): String {
    val normalized = progressPercent.coerceIn(0f, 100f)
    return if (normalized in 0.1f..<10f) {
        String.format(Locale.US, "%.1f%%", normalized)
    } else {
        "${normalized.toInt()}%"
    }
}
