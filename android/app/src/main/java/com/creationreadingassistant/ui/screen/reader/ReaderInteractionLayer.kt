package com.creationreadingassistant.ui.screen.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.screen.reader.ReaderBottomActions
import com.creationreadingassistant.ui.screen.reader.ReaderChromeAction
import com.creationreadingassistant.ui.screen.reader.ReaderTopChrome
import com.creationreadingassistant.ui.screen.reader.tts.TtsBar
import com.creationreadingassistant.ui.screen.reader.tts.TtsEngineHost
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.ReaderPanelSurface
import com.creationreadingassistant.ui.theme.rememberReducedMotion

/**
 * 正文覆盖层所需的只读展示数据。从 [ReaderScreen] 主函数局部状态中提取，
 * 仅承载「读」数据；任何对主函数可变状态的写入都通过 [ReaderInteractionLayerCallbacks] 回调上抛。
 */
internal data class ReaderInteractionLayerState(
    val controlsVisible: Boolean,
    val selectedText: String,
    val showColorRow: Boolean,
    val canCreateReplaceRule: Boolean,
    val showTts: Boolean,
    val showReaderOverflow: Boolean,
    val autoPagingActive: Boolean,
    val autoPageSpeed: Int,
    val progressPercent: Float,
    val bookTitle: String,
    val currentChapterTitle: String,
    val chapterProgress: Float,
    val isFirstChapter: Boolean,
    val isLastChapter: Boolean,
    val isEpub: Boolean,
    val isMarkdown: Boolean,
    val isLoading: Boolean,
    val error: String?,
    val showProgressBar: Boolean,
    val paper: ReaderPaperPalette,
    /** 当前是否处于临时查阅模式（由 navigationMode=TEMPORARY 决定）。 */
    val temporaryInspection: Boolean = false,
    /** 临时查阅协调器是否存在可返回目标（临时返回栈非空）。 */
    val hasReturnableTarget: Boolean = false,
)

/**
 * 正文覆盖层所需的回调集合。每个回调对应一次「主函数可变状态写入」或「主函数局部副作用」，
 * 复杂逻辑（高亮保存/locator 计算/剪贴板/notice）仍留在 [ReaderScreen] 主函数中定义，
 * 本层只暴露触发入口，保持为纯渲染层。
 */
internal data class ReaderInteractionLayerCallbacks(
    val onOverflowExpandedChange: (Boolean) -> Unit,
    val onChromeAction: (ReaderChromeAction) -> Unit,
    val onProgressScrubberInteractionStarted: () -> Unit,
    val onSeekChapterPercent: (Float) -> Unit,
    val onPrevChapter: () -> Unit,
    val onNextChapter: () -> Unit,
    val onAutoPageSpeedChange: (Int) -> Unit,
    val onPersistTts: (Float, Float, String, Int, String) -> Unit,
    val onCloseTts: () -> Unit,
    val onToggleColor: () -> Unit,
    val onPickColor: (String) -> Unit,
    val onBrowser: () -> Unit,
    val onDictionary: () -> Unit,
    val onReplace: () -> Unit,
    val onAiExplain: () -> Unit,
    val onInspiration: () -> Unit,
    val onNote: () -> Unit,
    val onCopy: () -> Unit,
    val onSearch: () -> Unit,
    val onClearSelection: () -> Unit,
    /** 返回阅读处（临时查阅 LIFO 返回一层）。 */
    val onReturnToReading: () -> Unit = {},
)

/** 顶部阅读器标题栏展示的文档格式标签。 */
internal fun readerFormatLabel(isEpub: Boolean, isMarkdown: Boolean): String =
    when {
        isEpub -> "EPUB"
        isMarkdown -> "MD"
        else -> "TXT"
    }

/**
 * 正文视口需要避开的底部浮层高度。
 *
 * 安全区只随底栏内容形态变化，不随 controlsVisible 变化：显示/隐藏同一种底栏时
 * 不触发分页重排或滚动位置跳动；打开 TTS/自动翻页这种明确改变底栏形态的动作，
 * 才使用对应的更高安全区。
 */
@Suppress("UNUSED_PARAMETER")
internal fun readerContentBottomReserve(
    showTts: Boolean,
    autoPagingActive: Boolean,
    fontScale: Float = 1f,
): Dp = 0.dp

/**
 * Legacy/滚动正文没有分页宿主自己的页眉行；始终预留 TopAppBar 的高度，避免控制栏
 * 显示时覆盖首段。分页宿主内部已经管理页眉/首行安全区，因此不重复扣除空间。
 */
@Suppress("UNUSED_PARAMETER")
internal fun readerContentTopReserve(
    usesPagedContent: Boolean,
    measuredTopChrome: Dp? = null,
): Dp = 0.dp

/**
 * 正文之上的覆盖层集合（进度条 / 顶栏 / 底栏+TTS / 选中工具条）。
 *
 * 从 ReaderScreen.kt 拆出，纯结构搬运，不改渲染语义。所有覆盖层用 [BoxScope.align]
 * 定位在正文容器之上，显示/隐藏不改变正文容器尺寸（不触发无关分页/重排）。
 *
 * @param tts TTS 控制器（TtsBar 直接消费，不在 state/callbacks 中）
 */
@Composable
internal fun BoxScope.ReaderInteractionLayer(
    state: ReaderInteractionLayerState,
    callbacks: ReaderInteractionLayerCallbacks,
    tts: TtsEngineHost,
) {
    val reducedMotion = rememberReducedMotion()

    // 显示进度条：正文底部 2dp 细线（此前该开关是摆设）
    if (state.showProgressBar && !state.isLoading && state.error == null) {
        LinearProgressIndicator(
            progress = { (state.progressPercent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter),
            color = state.paper.accent,
            trackColor = Color.Transparent,
        )
    }

    // 阅读控制区是正文之上的覆盖层。显示或隐藏不会改变正文容器尺寸，
    // 因而不会把当前页挤回中间或触发无关分页。
    AnimatedVisibility(
        visible = state.controlsVisible,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = if (reducedMotion) fadeIn(tween(0)) else fadeIn(tween(160)) + slideInVertically(initialOffsetY = { -it / 4 }),
        exit = if (reducedMotion) fadeOut(tween(0)) else fadeOut(tween(120)) + slideOutVertically(targetOffsetY = { -it / 4 }),
    ) {
        ReaderTopChrome(
            bookTitle = state.bookTitle,
            formatLabel = readerFormatLabel(state.isEpub, state.isMarkdown),
            chapterTitle = state.currentChapterTitle,
            progressPercent = state.progressPercent,
            paper = state.paper,
            overflowExpanded = state.showReaderOverflow,
            autoPagingActive = state.autoPagingActive,
            onOverflowExpandedChange = callbacks.onOverflowExpandedChange,
            onAction = callbacks.onChromeAction,
            temporaryInspection = state.temporaryInspection,
            hasReturnableTarget = state.hasReturnableTarget,
        )
    }

    AnimatedVisibility(
        visible = state.controlsVisible && state.selectedText.isBlank(),
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = if (reducedMotion) fadeIn(tween(0)) else fadeIn(tween(160)) + slideInVertically(initialOffsetY = { it / 4 }),
        exit = if (reducedMotion) fadeOut(tween(0)) else fadeOut(tween(120)) + slideOutVertically(targetOffsetY = { it / 4 }),
    ) {
        // 微岛收敛：底部操作栏容器是覆盖在阅读页之上的 reader 专属面板，统一走 ReaderPanelSurface
        //（随纸 panel 纸面 + 0.5dp 发丝边 + panelElevation 0dp）。原手写 panel@0.98 半透与 1dp 实边
        // 收敛为共享面板材质，避免翻页动画期的 alpha 混合与额外描边重绘。
        ReaderPanelSurface(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                // 系统导航栏已隐藏：只避开底部手势区（恒定，不随 transient swipe 跳动，
                // 三键导航下为 0，不产生空白系统栏）。
                .windowInsetsPadding(WindowInsets.mandatorySystemGestures),
            shape = LocalComponentSpec.current.floatingBarShape,
        ) {
            when (readerBottomChromeMode(state.showTts, state.autoPagingActive)) {
                ReaderBottomChromeMode.TTS -> TtsBar(
                    paper = state.paper,
                    tts = tts,
                    chapterLabel = state.currentChapterTitle.ifBlank { "正文" },
                    onPersistTts = { p, v, id, t, e -> callbacks.onPersistTts(p, v, id, t, e) },
                ) {
                    tts.stop()
                    callbacks.onCloseTts()
                }

                ReaderBottomChromeMode.AUTO_PAGING -> AutoPagingBar(
                    onAction = callbacks.onChromeAction,
                    speed = state.autoPageSpeed,
                    onSpeedChange = callbacks.onAutoPageSpeedChange,
                    paper = state.paper,
                )

                ReaderBottomChromeMode.NORMAL -> ReaderBottomActions(
                    onAction = callbacks.onChromeAction,
                    chapterProgress = state.chapterProgress,
                    onProgressInteractionStarted = callbacks.onProgressScrubberInteractionStarted,
                    onSeekProgress = { callbacks.onSeekChapterPercent(it) },
                    onPreviousChapter = callbacks.onPrevChapter,
                    onNextChapter = callbacks.onNextChapter,
                    isFirstChapter = state.isFirstChapter,
                    isLastChapter = state.isLastChapter,
                    autoPagingActive = false,
                    autoPageSpeed = state.autoPageSpeed,
                    onAutoPageSpeedChange = callbacks.onAutoPageSpeedChange,
                    paper = state.paper,
                )
            }
        }
    }

    // 选中文字工具条（对照 web 选中工具栏）：带入场动效（尊重「减少动态效果」）
    AnimatedVisibility(
        visible = state.selectedText.isNotBlank(),
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = if (reducedMotion) fadeIn(tween(120)) else (slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(tween(160))),
        exit = if (reducedMotion) fadeOut(tween(120)) else (slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut(tween(120))),
    ) {
        SelectionToolbar(
            paper = state.paper,
            selectedText = state.selectedText,
            showColorRow = state.showColorRow,
            canCreateReplaceRule = state.canCreateReplaceRule,
            onToggleColor = callbacks.onToggleColor,
            onPickColor = callbacks.onPickColor,
            onBrowser = callbacks.onBrowser,
            onDictionary = callbacks.onDictionary,
            onReplace = callbacks.onReplace,
            onAiExplain = callbacks.onAiExplain,
            onInspiration = callbacks.onInspiration,
            onNote = callbacks.onNote,
            onCopy = callbacks.onCopy,
            onSearch = callbacks.onSearch,
            onClear = callbacks.onClearSelection,
        )
    }
}
