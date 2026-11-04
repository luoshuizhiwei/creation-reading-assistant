package com.creationreadingassistant.ui.screen.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.creationreadingassistant.ui.onboarding.isCordHintSeen
import com.creationreadingassistant.ui.onboarding.markCordHintSeen
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.screen.reader.ReaderBottomActions
import com.creationreadingassistant.ui.screen.reader.ReaderChromeAction
import com.creationreadingassistant.ui.screen.reader.ReaderTopChrome
import com.creationreadingassistant.ui.screen.reader.tts.TtsBar
import com.creationreadingassistant.ui.screen.reader.tts.TtsEngineHost
import com.creationreadingassistant.data.settings.SelectionActionSettings
import com.creationreadingassistant.data.settings.SelectionActions
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
    /** R3-X1：选区工具条的动作配置（决定渲染哪些高频动作与溢出动作）。 */
    val selectionActions: SelectionActionSettings = SelectionActionSettings(),
    /**
     * R3-X1：AI 是否已配置可用（启用 + 有 Key）。
     *
     * 默认 `true` 是为了让「不关心 AI 的调用方/测试」保持原有渲染；真实阅读器一定显式传入，
     * 未配置时「AI 解读」不会出现在选区菜单里（门控不被绕过）。
     */
    val aiConfigured: Boolean = true,
    /** 当前是否处于临时查阅模式（由 navigationMode=TEMPORARY 决定）。 */
    val temporaryInspection: Boolean = false,
    /** 临时查阅协调器是否存在可返回目标（临时返回栈非空）。 */
    val hasReturnableTarget: Boolean = false,
    /**
     * 当前是否夜间态（一键日/夜灯绳用）。
     *
     * 由纸张明暗（`follow` 交给外壳解析）判定，与用户看到的内容一致，
     * 见 [com.creationreadingassistant.data.settings.ReaderDayNightToggle.isNight]。
     */
    val isNight: Boolean = false,
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
    val onPersistTts: (Float, Float, Float, String, Int, String) -> Unit,
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
    /**
     * 拉灯绳：一键切换日间 / 夜间模式（阅读纸张 + 应用外壳联动）。
     *
     * 落地逻辑在外层（要同时改两个偏好并做本书/全局路由），本层只暴露触发入口。
     */
    val onToggleDayNight: () -> Unit = {},
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

    // M-BUG-06：全书进度细线原先贴在这里（正文底部 2dp、始终压在文字上）。
    // 已移入菜单栏内的 ReaderBottomActions（底栏「本章进度滑块」上方）；
    // 这里不再渲染任何覆盖在正文之上的进度条。

    // 一键日/夜「灯绳」（对齐 QQ 阅读）：绳子的出现/消失时机与菜单栏完全一致——
    // 它就在顶栏 AnimatedVisibility 的节点内（见下方顶栏 Column），与菜单栏同一帧
    // 出现、同一帧收起，只是位置不同：从顶栏底缘垂下，坠子挂在绳子下端，
    // 绳头平齐栏底缘（上移 6dp 抵销顶栏容器内衬），只露出栏底以下的部分。
    //
    // 拉动动效的进度状态提在这一层（而非灯绳组件内部）：灯绳位于 AnimatedVisibility 内，
    // 菜单每次显隐都会重建子组合，状态留在组件里会在唤出菜单时被重置成 0（绳子自己动一下）。
    val cordPull = remember { Animatable(0f) }
    // 首帧只登记当前态、不播动画：进入阅读器时绳子不该自己动一下。
    // 该标记与本层同寿命（ReaderInteractionLayer 始终在组合树里），不受菜单显隐影响。
    val cordSettled = remember { mutableStateOf(false) }
    // 引导气泡只在**首次使用应用**时出现一次（用户 2026-09-18 反馈：此前每次进书都弹，
    // 且没有关闭按钮）。持久化在 onboarding 的 prefs 里 —— 该文件已在 App.attachBaseContext
    // 预热，这里的读取是内存命中，不构成首帧 I/O。
    val cordHintContext = LocalContext.current
    val cordHintDismissed = remember { mutableStateOf(isCordHintSeen(cordHintContext)) }
    val dismissCordHint: () -> Unit = {
        if (!cordHintDismissed.value) {
            cordHintDismissed.value = true
            runCatching { markCordHintSeen(cordHintContext) }
        }
    }
    LaunchedEffect(state.isNight) {
        if (!cordSettled.value) {
            cordSettled.value = true
            return@LaunchedEffect
        }
        if (reducedMotion) return@LaunchedEffect
        cordPull.snapTo(0f)
        cordPull.animateTo(1f, tween(durationMillis = 130))
        cordPull.animateTo(0f, tween(durationMillis = 190))
    }
    // 阅读控制区是正文之上的覆盖层。显示或隐藏不会改变正文容器尺寸，
    // 因而不会把当前页挤回中间或触发无关分页。
    AnimatedVisibility(
        visible = state.controlsVisible,
        modifier = Modifier
            .align(Alignment.TopCenter),
        enter = if (reducedMotion) fadeIn(tween(0)) else fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            initialOffsetY = { -it / 3 },
        ),
        exit = if (reducedMotion) fadeOut(tween(0)) else fadeOut(spring(stiffness = Spring.StiffnessMedium)) + slideOutVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            targetOffsetY = { -it / 3 },
        ),
    ) {
        // 灯绳挂在顶栏底缘之下（对齐 QQ 阅读真机截图 2026-09-18：绳上端平齐顶栏
        // 面板可见底缘、钟形坠子垂在绳子下端），并且就在顶栏这个 AnimatedVisibility
        // 节点之内——与菜单栏同一条 slide/fade 弹簧，同一帧出现、同一帧收起。
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(CORD_HEIGHT),
            ) {
                ReaderDayNightCord(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        // 与 QQ nightdialog.xml 同位：贴页面右缘（marginEnd=4dp）；
                        // 上移 6dp 抵销顶栏容器的纵向内衬，让绳头平齐面板可见底缘
                        //（真机像素核对）。
                        .padding(end = 4.dp)
                        .offset(y = (-6).dp),
                    paper = state.paper,
                    isNight = state.isNight,
                    onToggle = {
                        dismissCordHint()
                        callbacks.onToggleDayNight()
                    },
                    pull = cordPull.value,
                )
                // 引导气泡（对齐 QQ nightdialog.xml 的「拉拉灯绳…」引导行）：
                // 落在下坠的坠子左侧，让开绳子占位（右缘 4dp + 绳宽 30dp）再留 4dp 缝。
                // 与顶栏同一节点、同时显隐，不再单独做淡入。
                // 只在首次使用应用时出现；自带关闭按钮，拉绳也会一并消掉。
                if (!cordHintDismissed.value) {
                    ReaderDayNightHint(
                        paper = state.paper,
                        onClose = dismissCordHint,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 38.dp, top = 64.dp),
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = state.controlsVisible && state.selectedText.isBlank(),
        modifier = Modifier
            .align(Alignment.BottomCenter),
        enter = if (reducedMotion) fadeIn(tween(0)) else fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            initialOffsetY = { it / 3 },
        ),
        exit = if (reducedMotion) fadeOut(tween(0)) else fadeOut(spring(stiffness = Spring.StiffnessMedium)) + slideOutVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            targetOffsetY = { it / 3 },
        ),
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
                    onPersistTts = { r, p, v, id, t, e -> callbacks.onPersistTts(r, p, v, id, t, e) },
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
                    // M-BUG-07：底栏进度条统一为**全书**口径。
                    // 原先传的是 state.chapterProgress（本章百分比），而底栏跳转走
                    // seekToPercent（全书口径）——「显示本章、按全书跳」是错配的根源。
                    bookProgress = state.progressPercent,
                    showProgressBar = state.showProgressBar,
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
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding(),
        enter = if (reducedMotion) fadeIn(tween(120)) else (slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(tween(160))),
        exit = if (reducedMotion) fadeOut(tween(120)) else (slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut(tween(120))),
    ) {
        SelectionToolbar(
            paper = state.paper,
            selectedText = state.selectedText,
            showColorRow = state.showColorRow,
            canCreateReplaceRule = state.canCreateReplaceRule,
            // R3-X1：动作清单来自配置 + 能力门控（未知 id 已在 store 出口被 sanitize 掉）
            primaryActions = selectionToolbarActions(
                settings = state.selectionActions,
                canCreateReplaceRule = state.canCreateReplaceRule,
                aiConfigured = state.aiConfigured,
            ).first,
            moreActions = selectionToolbarActions(
                settings = state.selectionActions,
                canCreateReplaceRule = state.canCreateReplaceRule,
                aiConfigured = state.aiConfigured,
            ).second,
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
