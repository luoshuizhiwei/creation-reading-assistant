package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.DocChapter

internal data class ReaderTocState(
    val titles: List<String>,
    val current: Int,
    val total: Int,
    val isChapteredDocument: Boolean,
)

internal fun readerTocState(
    epubTitles: List<String>?,
    markdownTitles: List<String>?,
    txtTitles: List<String>,
    chapterIndex: Int,
    txtChapterIndex: Int,
): ReaderTocState {
    val isChapteredDocument = epubTitles != null || markdownTitles != null
    val titles = epubTitles ?: markdownTitles ?: txtTitles
    return ReaderTocState(
        titles = titles,
        current = if (isChapteredDocument) chapterIndex else txtChapterIndex,
        total = titles.size,
        isChapteredDocument = isChapteredDocument,
    )
}

internal sealed interface ReaderTocPickTarget {
    data class Chapter(val index: Int) : ReaderTocPickTarget
    data class PlainOffset(val offset: Int) : ReaderTocPickTarget
    data object None : ReaderTocPickTarget
}

internal fun readerTocPickTarget(
    isEpub: Boolean,
    isMarkdown: Boolean,
    txtChapters: List<DocChapter>,
    selectedIndex: Int,
): ReaderTocPickTarget = when {
    selectedIndex < 0 -> ReaderTocPickTarget.None
    isEpub || isMarkdown -> ReaderTocPickTarget.Chapter(selectedIndex)
    else -> txtChapters.getOrNull(selectedIndex)
        ?.let { ReaderTocPickTarget.PlainOffset(it.startOffset) }
        ?: ReaderTocPickTarget.None
}
