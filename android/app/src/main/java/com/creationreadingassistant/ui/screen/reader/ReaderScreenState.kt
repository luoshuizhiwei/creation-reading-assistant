package com.creationreadingassistant.ui.screen.reader

/** 阅读页纯 UI 弹层类型；不参与 Locator、分页或文档加载。 */
enum class ReaderSheet {
    TOC,
    NOTES,
    AI_ASSIST,
    AI_EXPLAIN,
    INSPIRATION,
    SETTINGS,
    PROGRESS,
    SEARCH,
    BOOK_INFO,
    THEME,
}

/**
 * 阅读页短生命周期 UI 状态（不可变 data class）。
 *
 * 所有变更通过 ReaderViewModel.onAction → _screenState.update { it.copy(...) } 驱动；
 * ReaderScreen 通过 collectAsStateWithLifecycle 获取只读快照。
 */
data class ReaderScreenState(
    val controlsVisible: Boolean = true,
    val selectedText: String = "",
    val selectedRangeStart: Int = -1,
    val selectedGlobalOffset: Int = -1,
    val sheet: ReaderSheet? = null,
    val showTts: Boolean = false,
    val searchQuery: String = "",
    val showReaderOverflow: Boolean = false,
    val noteOpen: Boolean = false,
    val noteBody: String = "",
    val showColorRow: Boolean = false,
)
