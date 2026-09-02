package com.creationreadingassistant.ui.screen.reader.sheets

/** 单次阅读辅助请求最多发送的正文字符数，防止误把整章长文本无提示地交给外部服务。 */
internal const val AI_READER_CONTEXT_LIMIT = 4_000

internal enum class ReaderAiContextScope {
    SELECTION,
    CURRENT_CHAPTER,
}

internal fun readerAiContextScope(hasSelectedText: Boolean): ReaderAiContextScope =
    if (hasSelectedText) ReaderAiContextScope.SELECTION else ReaderAiContextScope.CURRENT_CHAPTER

internal fun readerAiSentCharacterCount(contextText: String): Int =
    contextText.length.coerceAtMost(AI_READER_CONTEXT_LIMIT)

internal fun canSubmitReaderAiRequest(
    tab: String,
    contextText: String,
    question: String,
): Boolean = contextText.isNotBlank() && (tab != "qa" || question.isNotBlank())

/** 只有完整成功的流式结果才允许沉淀为灵感，避免保存取消或失败时的半成品。 */
internal fun canSaveReaderAiResult(
    result: String,
    loading: Boolean,
    error: String?,
): Boolean = result.isNotBlank() && !loading && error == null

/** 取消或失败都不能把流式半成品继续呈现为已完成的阅读辅助结果。 */
internal fun readerAiResultForRequestOutcome(result: String, successful: Boolean): String =
    if (successful) result else ""
