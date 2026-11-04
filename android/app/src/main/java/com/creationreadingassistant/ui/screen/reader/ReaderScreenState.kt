package com.creationreadingassistant.ui.screen.reader

/** 阅读页纯 UI 弹层类型；不参与 Locator、分页或文档加载。 */
enum class ReaderSheet {
    TOC,
    RULES,
    NOTES,
    AI_ASSIST,
    AI_EXPLAIN,
    INSPIRATION,
    SETTINGS,
    PROGRESS,
    SEARCH,
    BOOK_INFO,
    THEME,
    /** R3-X1：离线词典查词面板。 */
    DICTIONARY,
}

/**
 * 打开长生命周期选区消费者时捕获的**不可变**选区快照（S07-F2.4）。
 *
 * 为什么需要它：滚动 TXT 的 TTS 跟读会经 `scrollToItem` 程序化滚动，拥有当前选区的
 * ReadingUnit 随后可能 dispose 并（正确地）清除正文活动选区。而笔记、规则、AI 解读、
 * 灵感等弹层是**长生命周期**的 —— 它们若继续读实时 VM 选区，就会在正文清理后
 * 丢摘录、丢保存定位、甚至丢用户正在编辑的草稿。
 *
 * 因此这些消费者在**打开时**一次性捕获选区，之后全程只读本快照；
 * 正文活动选区仍按原规则清理（不引入 pending-clear 状态机、不暂停 TTS、
 * 不改变 ReadingUnit dispose 语义）。
 *
 * 坐标字段与 [ReaderScreenState] 的活动选区同名同义，仍是 **source** 口径
 * （不是 display offset），因此持久化契约不变。
 */
data class ReaderSelectionSnapshot(
    val text: String,
    val rangeStart: Int,
    val globalOffset: Int,
    val sourceEnd: Int,
)

/**
 * 该 sheet 是否是「长生命周期选区消费者」（S07-F2.4）。
 *
 * 消费者 = 打开后会**持续**使用选区文字/坐标的弹层。它们必须在打开时拿到不可变快照，
 * 否则正文活动选区被清理（TTS 滚动导致 ReadingUnit dispose）后会丢摘录、丢定位、丢草稿。
 *
 * 明确**不是**消费者的：
 * - `SEARCH`：查询串在打开前已由 `SetSearchQuery` 快照进 `searchQuery`；
 * - `DICTIONARY`：查词由 `dictionaryWord` 在打开前单独快照；
 * - `NOTES` / `TOC` / `SETTINGS` / `PROGRESS` / `BOOK_INFO` / `THEME`：不读选区。
 */
internal fun ReaderSheet.consumesSelection(): Boolean = when (this) {
    ReaderSheet.RULES,
    ReaderSheet.AI_EXPLAIN,
    ReaderSheet.INSPIRATION,
    ReaderSheet.AI_ASSIST,
    -> true

    ReaderSheet.TOC,
    ReaderSheet.NOTES,
    ReaderSheet.SETTINGS,
    ReaderSheet.PROGRESS,
    ReaderSheet.SEARCH,
    ReaderSheet.BOOK_INFO,
    ReaderSheet.THEME,
    ReaderSheet.DICTIONARY,
    -> false
}

/**
 * 原子捕获当前活动选区为消费者快照（S07-F2.4）。
 *
 * **无选区时写 null**，而不是保留上一轮快照 —— 这样从顶栏直接打开灵感等入口时，
 * 仍然是「无摘录」语义，不会复用上一轮残留的选区（要求 9）。
 */
internal fun ReaderScreenState.withSelectionCaptured(): ReaderScreenState = copy(
    selectionSnapshot = if (selectedText.isNotBlank()) {
        ReaderSelectionSnapshot(
            text = selectedText,
            rangeStart = selectedRangeStart,
            globalOffset = selectedGlobalOffset,
            sourceEnd = selectedSourceEnd,
        )
    } else {
        null
    },
)

/** 释放消费者快照（关闭消费者 / 切到不消费选区的 sheet），避免下一次打开复用旧选区。 */
internal fun ReaderScreenState.withSelectionSnapshotReleased(): ReaderScreenState =
    copy(selectionSnapshot = null)

/**
 * 阅读页短生命周期 UI 状态（不可变 data class）。
 *
 * 所有变更通过 ReaderViewModel.onAction → _screenState.update { it.copy(...) } 驱动；
 * ReaderScreen 通过 collectAsStateWithLifecycle 获取只读快照。
 */
data class ReaderScreenState(
    val controlsVisible: Boolean = true,
    /** 每次进度拖动开始递增，用于重启 chrome 自动隐藏倒计时；不改变可见性。 */
    val autoHideInteractionRevision: Long = 0L,
    val selectedText: String = "",
    val selectedRangeStart: Int = -1,
    val selectedGlobalOffset: Int = -1,
    /** 分页投影选区的全书 source 半开终点；旧路径不提供时为 -1。 */
    val selectedSourceEnd: Int = -1,
    val sheet: ReaderSheet? = null,
    val showTts: Boolean = false,
    val searchQuery: String = "",
    val showReaderOverflow: Boolean = false,
    val noteOpen: Boolean = false,
    val noteBody: String = "",
    val showColorRow: Boolean = false,
    /**
     * 长生命周期选区消费者的不可变快照（S07-F2.4）。
     *
     * `null` = 当前没有消费者持有快照。仅在「从选区工具栏打开需要选区的 sheet/dialog」
     * 时由 ViewModel 原子捕获；正文活动选区被清除**不影响**它。
     * 关闭消费者、保存完成或切到不消费选区的 sheet 时释放为 null。
     */
    val selectionSnapshot: ReaderSelectionSnapshot? = null,
)
