package com.creationreadingassistant.ui.screen.reader

/**
 * 异步解析高亮、笔记或书签锚点后的发布条件。
 *
 * 请求开始时记录所在书和章节。I/O 返回后，若用户已切书或切章，旧请求必须丢弃，
 * 让最后一次明确的阅读导航优先，不能把用户拉回原位置。
 */
internal fun canApplyResolvedReaderAnchor(
    requestBookId: String,
    requestChapterIndex: Int,
    currentBookId: String,
    currentChapterIndex: Int,
): Boolean = requestBookId == currentBookId && requestChapterIndex == currentChapterIndex

/** 流式 TXT 锚点读取前的坐标归一化，避免旧 locator 跳出当前文件边界。 */
internal data class StreamingAnchorWindowPlan(
    val targetOffset: Int,
    val windowStart: Int,
)

internal fun streamingAnchorWindowPlan(
    locatorOffset: Int,
    totalChars: Int,
    charsBeforeTarget: Int = 2_000,
): StreamingAnchorWindowPlan {
    val targetOffset = locatorOffset.coerceIn(0, totalChars.coerceAtLeast(0))
    return StreamingAnchorWindowPlan(
        targetOffset = targetOffset,
        windowStart = (targetOffset - charsBeforeTarget.coerceAtLeast(0)).coerceAtLeast(0),
    )
}
