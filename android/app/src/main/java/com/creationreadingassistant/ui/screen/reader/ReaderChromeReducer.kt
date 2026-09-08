package com.creationreadingassistant.ui.screen.reader

/**
 * 阅读器 chrome 状态机事件（纯逻辑，无 Android 依赖）。
 *
 * 中央点击统一负责显示/隐藏；翻页与自动隐藏只负责隐藏；sheet 只做计时门控。
 */
internal sealed interface ReaderChromeEvent {
    /** 进入阅读器：菜单可见。 */
    data object Enter : ReaderChromeEvent
    /** 中央区域点击：显示/隐藏切换。 */
    data object CenterTap : ReaderChromeEvent
    /** 翻页（手势/分区点击/音量键等）：菜单立即隐藏，与 autoHideSeconds 是否 0 无关。 */
    data object PageTurn : ReaderChromeEvent
    /** 进度拖动开始：保持 chrome 可见性，并以 revision 重启自动隐藏倒计时。 */
    data object ProgressScrubberInteractionStarted : ReaderChromeEvent
    /** 自动隐藏倒计时到点：菜单隐藏。[autoHideSeconds] 为派发时的自动隐藏秒数（0=不隐藏）。 */
    data class AutoHideElapsed(val autoHideSeconds: Int) : ReaderChromeEvent
    /** 弹层打开：保持菜单并暂停自动隐藏计时。 */
    data object SheetOpen : ReaderChromeEvent
    /** 弹层关闭：恢复计时（按设置重新计时由 effects 层依据 sheetOpen 门控重启）。 */
    data object SheetClose : ReaderChromeEvent
    /** 切书：菜单重置为可见。 */
    data object BookSwitched : ReaderChromeEvent
}

/**
 * 阅读器 chrome 状态：顶栏/底栏可见性 + sheet 门控。
 *
 * TTS 栏（showTts）与 sheet 本体由 [ReaderScreenState] 独立持有，本状态机不触碰。
 */
internal data class ReaderChromeState(
    val controlsVisible: Boolean = true,
    val sheetOpen: Boolean = false,
    val autoHideInteractionRevision: Long = 0L,
) {
    /** sheet 打开时自动隐藏倒计时暂停。 */
    val autoHidePaused: Boolean get() = sheetOpen
}

/**
 * 阅读器 chrome 状态机 reducer：纯函数，事件 → 新状态。
 *
 * - Enter / BookSwitched → Visible（进入阅读器、切书重置）；
 * - CenterTap → toggle（中央点击统一负责显示/隐藏）；
 * - PageTurn → Hidden（翻页立即隐藏，不受 autoHideSeconds=0 影响）；
 * - ProgressScrubberInteractionStarted → 保持可见性，递增自动隐藏计时 revision；
 * - AutoHideElapsed → sheet 未开且 seconds>0 时 Hidden；sheetOpen 或 seconds<=0 保持原状态；
 * - SheetOpen / SheetClose → 只改门控，不动可见性（"保持菜单且暂停 timer"）。
 */
internal fun readerChromeReducer(
    state: ReaderChromeState = ReaderChromeState(),
    event: ReaderChromeEvent,
): ReaderChromeState = when (event) {
    ReaderChromeEvent.Enter,
    ReaderChromeEvent.BookSwitched -> state.copy(controlsVisible = true)
    ReaderChromeEvent.CenterTap -> state.copy(controlsVisible = !state.controlsVisible)
    ReaderChromeEvent.PageTurn -> state.copy(controlsVisible = false)
    ReaderChromeEvent.ProgressScrubberInteractionStarted ->
        state.copy(autoHideInteractionRevision = state.autoHideInteractionRevision + 1L)
    is ReaderChromeEvent.AutoHideElapsed ->
        if (state.sheetOpen || event.autoHideSeconds <= 0) state
        else state.copy(controlsVisible = false)
    ReaderChromeEvent.SheetOpen -> state.copy(sheetOpen = true)
    ReaderChromeEvent.SheetClose -> state.copy(sheetOpen = false)
}

/** 从阅读页 UI 状态提取 chrome 子状态（单一真源仍在 [ReaderScreenState]）。 */
internal fun ReaderScreenState.toReaderChromeState(): ReaderChromeState =
    ReaderChromeState(
        controlsVisible = controlsVisible,
        sheetOpen = sheet != null,
        autoHideInteractionRevision = autoHideInteractionRevision,
    )

/** 把 reducer 产生的 chrome 状态写回阅读页 UI 状态（只动 controlsVisible）。 */
internal fun ReaderChromeState.into(screen: ReaderScreenState): ReaderScreenState =
    screen.copy(
        controlsVisible = controlsVisible,
        autoHideInteractionRevision = autoHideInteractionRevision,
    )
