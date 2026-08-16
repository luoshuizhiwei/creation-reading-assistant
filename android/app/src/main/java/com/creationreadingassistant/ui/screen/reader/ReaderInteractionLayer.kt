package com.creationreadingassistant.ui.screen.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.screen.reader.ReaderBottomActions
import com.creationreadingassistant.ui.screen.reader.ReaderChromeAction
import com.creationreadingassistant.ui.screen.reader.ReaderTopChrome
import com.creationreadingassistant.ui.screen.reader.tts.TtsBar
import com.creationreadingassistant.ui.screen.reader.tts.TtsController
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.rememberReducedMotion

/**
 * 正文覆盖层所需的只读展示数据。从 [ReaderScreen] 主函数局部状态中提取，
 * 仅承载「读」数据；任何对主函数可变状态的写入都通过 [ReaderInteractionLayerCallbacks] 回调上抛。
 */
internal data class ReaderInteractionLayerState(
    val controlsVisible: Boolean,
    val selectedText: String,
    val showColorRow: Boolean,
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
    val isLoading: Boolean,
    val error: String?,
    val showProgressBar: Boolean,
    val paper: ReaderPaperPalette,
)

/**
 * 正文覆盖层所需的回调集合。每个回调对应一次「主函数可变状态写入」或「主函数局部副作用」，
 * 复杂逻辑（高亮保存/locator 计算/剪贴板/notice）仍留在 [ReaderScreen] 主函数中定义，
 * 本层只暴露触发入口，保持为纯渲染层。
 */
internal data class ReaderInteractionLayerCallbacks(
    val onOverflowExpandedChange: (Boolean) -> Unit,
    val onChromeAction: (ReaderChromeAction) -> Unit,
    val onSeekChapterPercent: (Float) -> Unit,
    val onPrevChapter: () -> Unit,
    val onNextChapter: () -> Unit,
    val onAutoPageSpeedChange: (Int) -> Unit,
    val onPersistTts: (Float, Float, String, Int) -> Unit,
    val onCloseTts: () -> Unit,
    val onToggleColor: () -> Unit,
    val onPickColor: (String) -> Unit,
    val onAiExplain: () -> Unit,
    val onInspiration: () -> Unit,
    val onNote: () -> Unit,
    val onCopy: () -> Unit,
    val onSearch: () -> Unit,
    val onClearSelection: () -> Unit,
)

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
    tts: TtsController,
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
            formatLabel = if (state.isEpub) "EPUB" else "TXT",
            chapterTitle = state.currentChapterTitle,
            progressPercent = state.progressPercent,
            paper = state.paper,
            overflowExpanded = state.showReaderOverflow,
            autoPagingActive = state.autoPagingActive,
            onOverflowExpandedChange = callbacks.onOverflowExpandedChange,
            onAction = callbacks.onChromeAction,
        )
    }

    AnimatedVisibility(
        visible = state.controlsVisible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = if (reducedMotion) fadeIn(tween(0)) else fadeIn(tween(160)) + slideInVertically(initialOffsetY = { it / 4 }),
        exit = if (reducedMotion) fadeOut(tween(0)) else fadeOut(tween(120)) + slideOutVertically(targetOffsetY = { it / 4 }),
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                // 系统导航栏已隐藏：只避开底部手势区（恒定，不随 transient swipe 跳动，
                // 三键导航下为 0，不产生空白系统栏）。
                .windowInsetsPadding(WindowInsets.mandatorySystemGestures),
            shape = LocalComponentSpec.current.floatingBarShape,
            color = state.paper.panel.copy(alpha = 0.98f),
            contentColor = state.paper.fg,
            border = BorderStroke(1.dp, state.paper.outlineVariant),
        ) {
            if (state.showTts) {
                TtsBar(
                    paper = state.paper,
                    tts = tts,
                    chapterLabel = state.currentChapterTitle.ifBlank { "正文" },
                    onPersistTts = { p, v, id, t -> callbacks.onPersistTts(p, v, id, t) },
                ) {
                    tts.stop()
                    callbacks.onCloseTts()
                }
            } else {
                ReaderBottomActions(
                    onAction = callbacks.onChromeAction,
                    chapterProgress = state.chapterProgress,
                    onSeekProgress = { callbacks.onSeekChapterPercent(it) },
                    onPreviousChapter = callbacks.onPrevChapter,
                    onNextChapter = callbacks.onNextChapter,
                    isFirstChapter = state.isFirstChapter,
                    isLastChapter = state.isLastChapter,
                    autoPagingActive = state.autoPagingActive,
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
        enter = if (reducedMotion) fadeIn(tween(120)) else (slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(tween(160))),
        exit = if (reducedMotion) fadeOut(tween(120)) else (slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut(tween(120))),
    ) {
        SelectionToolbar(
            paper = state.paper,
            selectedText = state.selectedText,
            showColorRow = state.showColorRow,
            onToggleColor = callbacks.onToggleColor,
            onPickColor = callbacks.onPickColor,
            onAiExplain = callbacks.onAiExplain,
            onInspiration = callbacks.onInspiration,
            onNote = callbacks.onNote,
            onCopy = callbacks.onCopy,
            onSearch = callbacks.onSearch,
            onClear = callbacks.onClearSelection,
        )
    }
}
