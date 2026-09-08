package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.DocChapter

internal data class ReaderTocState(
    val titles: List<String>,
    val current: Int,
    val total: Int,
    val isChapteredDocument: Boolean,
)

/**
 * 将阅读文档的章节坐标元数据收敛为目录可直接展示的字数标签。
 *
 * TXT / Markdown 的 [DocChapter.charCount] 是精确字符数；EPUB 当前来自 ZIP 解压字节估算，
 * 必须保留“约”前缀，不能把估算值伪装成精确统计。此模块只消费轻量元数据，不读取正文。
 */
internal fun readerTocWordCountLabels(chapters: List<DocChapter>): List<String> =
    chapters.map { chapter ->
        val count = chapter.charCount.coerceAtLeast(0)
        val compactCount = if (count < 10_000) {
            count.toString()
        } else {
            val roundedTenths = (count + 500) / 1_000
            val whole = roundedTenths / 10
            val fraction = roundedTenths % 10
            if (fraction == 0) whole.toString() else "$whole.$fraction"
        }
        buildString {
            if (chapter.charCountIsEstimated) append("约")
            append(compactCount)
            if (count >= 10_000) append("万")
            append("字")
        }
    }

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
