/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package local.creationReadingAssistant.reader.legado.model

enum class ReaderFormat {
    TXT,
    MARKDOWN,
    EPUB
}

enum class ReaderPageMode {
    NONE,
    SLIDE,
    COVER,
    SCROLL
}

enum class ReaderTheme {
    WHITE,
    WARM,
    GREEN,
    NIGHT
}

data class ReaderLocator @JvmOverloads constructor(
    val chapterIndex: Int = 0,
    val charOffset: Long = 0L,
    val pageIndex: Int = 0,
    val progressPercent: Double = 0.0,
    val epubHref: String? = null,
    val epubCfi: String? = null,
)

data class NativeReaderSettings @JvmOverloads constructor(
    val fontSizeSp: Float = 20f,
    val lineSpacingMultiplier: Float = 1.7f,
    val paragraphSpacingDp: Float = 12f,
    val horizontalPaddingDp: Float = 24f,
    val verticalPaddingDp: Float = 28f,
    val textBold: Boolean = false,
    val pageMode: ReaderPageMode = ReaderPageMode.SLIDE,
    val theme: ReaderTheme = ReaderTheme.WARM,
)

data class ReaderOpenRequest(
    val bookId: String,
    val title: String,
    val author: String? = null,
    val fileUri: String,
    val format: ReaderFormat,
    val locator: ReaderLocator = ReaderLocator(),
    val settings: NativeReaderSettings = NativeReaderSettings(),
)

data class ReaderCloseResult(
    val bookId: String,
    val locator: ReaderLocator,
    val activeDurationMs: Long,
    val chapterTitle: String? = null,
)
