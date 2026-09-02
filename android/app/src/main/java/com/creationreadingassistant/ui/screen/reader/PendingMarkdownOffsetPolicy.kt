package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.ReaderDocument

/**
 * Markdown 滚动模式跨章定位的请求裁决。
 *
 * 裸偏移不足以表达一次性请求的生命周期：目标章加载期间必须保留，用户随后
 * 手动切到其他章节或切换到分页模式时则必须丢弃，避免旧定位在稍后劫持阅读。
 */
internal sealed interface PendingMarkdownOffsetOutcome {
    data object NoRequest : PendingMarkdownOffsetOutcome

    data object Pending : PendingMarkdownOffsetOutcome

    data object Discarded : PendingMarkdownOffsetOutcome

    data class Consume(val offset: Int) : PendingMarkdownOffsetOutcome
}

internal fun pendingMarkdownOffsetOutcome(
    pendingOffset: Int?,
    markdownDocument: ReaderDocument?,
    pagerEngineOn: Boolean,
    currentChapterIndex: Int,
    targetBlocksReady: Boolean,
): PendingMarkdownOffsetOutcome {
    val offset = pendingOffset ?: return PendingMarkdownOffsetOutcome.NoRequest
    val document = markdownDocument ?: return PendingMarkdownOffsetOutcome.Discarded
    if (pagerEngineOn) return PendingMarkdownOffsetOutcome.Discarded
    if (document.locate(offset).first != currentChapterIndex) {
        return PendingMarkdownOffsetOutcome.Discarded
    }
    return if (targetBlocksReady) {
        PendingMarkdownOffsetOutcome.Consume(offset)
    } else {
        PendingMarkdownOffsetOutcome.Pending
    }
}
