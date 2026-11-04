package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.pager.ScrollUnitContentLoader
import com.creationreadingassistant.feature.reader.pager.ScrollUnitContentState
import com.creationreadingassistant.feature.reader.pager.ScrollingTxtChapterSource
import com.creationreadingassistant.feature.reader.pager.loadScrollUnitContent
import com.creationreadingassistant.feature.reader.rules.ScrollUnitProjection
import com.creationreadingassistant.ui.theme.ShimmerBlock
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * ReaderContentHost 分支 4：TXT 流式/纯文本（else 分支，LazyColumn + 读取单元）。
 *
 * 从 [ReaderContentHost] 的 `when` 分支纯结构搬运而来，异步加载 effect 与
 * 释放时序逐字保留。
 */
@Suppress("LongMethod")
@Composable
internal fun ReaderContentHostPlainTextBranch(
    state: ReaderContentHostState,
    callbacks: ReaderContentHostCallbacks,
    readerFontFamily: FontFamily,
) {
    val settings = state.settings
    val s = state.source
    val txtDoc = s.txtStreamingDocument
    val reducedMotion = rememberReducedMotion()
    // 加载占位高度：按 3 行正文估算（与正文行高同源），sp → dp 需经当前密度
    val loadingPlaceholderHeight = with(LocalDensity.current) {
        (settings.readerSettings.fontSize * settings.readerSettings.lineHeight * 3).sp.toDp()
    }
    val scope = rememberCoroutineScope()
    // 滚动 TXT 的显示文本必须来自完整逻辑章投影。可用性裁决和 source 装配均在
    // rememberPagerEngineState 完成；这里绝不再自行 prepare 第二份 wrapper。
    // ReadingUnit 仅作为有界渲染单元，所有选择、搜索、高亮和 TTS 的持久化坐标
    // 都由 projection 映射回 source。
    val fallbackScrollSource = remember(
        txtDoc,
        s.plainContent,
        s.readingUnits,
    ) {
        if (txtDoc != null) {
            ScrollingTxtChapterSource.fromDocument(txtDoc)
        } else {
            ScrollingTxtChapterSource.fromText(s.plainContent, s.readingUnits)
        }
    }
    val scrollSource = state.paging.scrollProjectedSource ?: fallbackScrollSource
    val unitContentLoader = remember { ScrollUnitContentLoader() }
    LaunchedEffect(scrollSource) {
        unitContentLoader.switchSource(scrollSource)
        // 与 PagerEngineState 的 assemble trace 配对：这里只记录 source 是否真的被
        // 正文绑定，不记录书名、规则或正文内容。
        AppLog.debug(
            "ScrollReplaceTrace",
            "bind source=${scrollSource.javaClass.simpleName}, " +
                "projected=${state.paging.scrollProjectedSource === scrollSource}, " +
                "rules=${s.replaceRules.size}, units=${s.readingUnits.size}",
        )
    }
    // 单次轻点手势仲裁门：readOnly 文本域观察器按下时 claim，父级抬起时
    // consumeClaimIfAny；正文轻点走子路径，父层只负责空白/边距，避免双切换。
    val tapGate = remember { ReaderTapToggleGate() }
    // ── S07-F5 D-3：可见性失效（宿主层唯一判据） ──────────────────────────
    //
    // 本分支持有全局 `s.plainListState`，是唯一能看到「哪些单元真正可见」的层。
    // 这里观察 `layoutInfo.visibleItemsInfo`（经 snapshotFlow，只在布局变化时回调，
    // 不轮询、不 sleep），把可见的 unitIndex 集合交给纯策略函数判定。
    //
    // 为什么不在每个 item 里判断：LazyColumn 会同时组合多个相邻单元，让每个
    // 非 owner item 各自决定「要不要清别人」会让相邻单元误清真实选区。
    //
    // 两个输入都必须经 `rememberUpdatedState` 读**最新**值：effect 的 key 是
    // 「列表身份」，选区变化**不会**重启它；若 collect 里直接捕获组合期的
    // `selectionState` 快照，判据会永远用第一次组合时的选区起点（F2.3 已登记过
    // 同一类「捕获首次组合快照」缺陷）。
    val latestSelectedRangeStart by rememberUpdatedState(state.selection.selectedRangeStart)
    val latestReadingUnits by rememberUpdatedState(s.readingUnits)
    val latestClearSelection by rememberUpdatedState(callbacks.onSelect)
    val invalidationGate = remember { ScrollSelectionInvalidationGate() }
    LaunchedEffect(s.plainListState) {
        val listState = s.plainListState
        snapshotFlow {
            ScrollViewportSnapshot(
                visibleUnitIndices = listState.layoutInfo.visibleItemsInfo
                    .mapNotNull { info -> latestReadingUnits.getOrNull(info.index)?.unitIndex }
                    .toSet(),
                // `layoutInfo.totalItemsCount > 0` 即列表已完成首次测量。不用
                // `isInitialized`（该 API 不在本模块可见的 LazyListState 版本上），
                // 语义等价：可见项非空或总数非零都说明布局已落定。
                initialized = listState.layoutInfo.totalItemsCount > 0,
            )
        }
            .distinctUntilChanged()
            .collect { viewport ->
                val decision = scrollSelectionInvalidation(
                    selectedRangeStart = latestSelectedRangeStart,
                    readingUnits = latestReadingUnits,
                    visibleUnitIndices = viewport.visibleUnitIndices,
                    viewportInitialized = viewport.initialized,
                )
                if (invalidationGate.shouldDispatch(decision)) {
                    latestClearSelection("", -1, -1, -1)
                }
            }
    }
    // 中央点击统一唤出/隐藏（父级 tap observation，不 consume）：TXT 滚动分支的
    // 重试点击（UnitTextFailedItem）优先消费；readOnly 文本域内的中央短按由
    // 子观察器（readerTextFieldCenterTapToToggle）负责，父层保留空白/边距路径。
    Box(
        Modifier
            .fillMaxSize()
            .readerCenterTapToToggle(
                onCenterTap = { callbacks.onToggleControls() },
                gate = tapGate,
            ),
    ) {
        LazyColumn(
            state = s.plainListState,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = s.chapterFade.value },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = settings.readerSettings.pageMargin.dp,
                vertical = settings.readerSettings.pageMargin.dp,
            ),
        ) {
            itemsIndexed(
                items = s.readingUnits,
                key = { _, unit -> unit.unitIndex },
            ) { _, unit ->
                val unitState = unitContentLoader.stateFor(scrollSource, unit.unitIndex)
                LaunchedEffect(scrollSource, unit) {
                    unitContentLoader.load(scrollSource, unit.unitIndex) {
                        scrollSource.loadScrollUnitContent(unit)
                    }
                }
                DisposableEffect(scrollSource, unit.unitIndex) {
                    onDispose { unitContentLoader.release(scrollSource, unit.unitIndex) }
                }
                when (val loaded = unitState.value) {
                    is ScrollUnitContentState.Loaded -> ReaderUnitTextItem(
                        content = loaded.content,
                        unit = unit,
                        state = state,
                        callbacks = callbacks,
                        fontFamily = readerFontFamily,
                        searchHitRangeAbs = s.searchHitRangeAbs,
                        tapGate = tapGate,
                    )

                    ScrollUnitContentState.Loading -> ShimmerBlock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .height(loadingPlaceholderHeight)
                            .semantics { contentDescription = "正在加载正文" },
                        reducedMotion = reducedMotion,
                    )

                    is ScrollUnitContentState.Failed -> UnitTextFailedItem(
                        cause = loaded.cause,
                        paperFg = settings.paperFg,
                        onRetry = {
                            scope.launch {
                                unitContentLoader.retry(scrollSource, unit.unitIndex) {
                                    scrollSource.loadScrollUnitContent(unit)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 读取单元失败项：原因摘要 + 显式「点击重试」，带可访问语义。 */
@Composable
private fun UnitTextFailedItem(
    cause: Throwable,
    paperFg: Color,
    onRetry: () -> Unit,
) {
    val summary = cause.message?.takeIf { it.isNotBlank() } ?: "未知错误"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRetry)
            .semantics { contentDescription = "内容加载失败：$summary，点击重试" }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "内容加载失败：$summary",
            style = MaterialTheme.typography.bodySmall,
            color = paperFg,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "点击重试",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 某个滚动 ReadingUnit 是否**拥有** VM 当前的选区（S07-F2.2）。
 *
 * VM 存的是选区 source 起点（`selectedRangeStart`），本单元的 source 范围是半开区间
 * `[unitSourceStartAbs, unitSourceEndAbs)`。起点落在区间内即本单元拥有该选区。
 *
 * 为什么必须这样判：LazyColumn 会**同时组合**多个相邻单元，因此不能反过来用
 * 「当前任一渲染单元不包含选区就清空」——那会让相邻单元误清真实选区。
 * 只有「拥有选区的那个单元被回收 / 身份替换」才是选区真正失去宿主。
 */
internal fun readingUnitOwnsSelection(
    selectedRangeStart: Int,
    unitSourceStartAbs: Int,
    unitSourceEndAbs: Int,
): Boolean =
    unitSourceEndAbs > unitSourceStartAbs &&
        selectedRangeStart >= unitSourceStartAbs &&
        selectedRangeStart < unitSourceEndAbs

/**
 * 单元离开组合时是否应请求清除 VM 选区（S07-F2.2）。
 *
 * 只有拥有选区的单元 dispose 才清；无选区的单元、未拥有选区的相邻单元都不得误清。
 * 抽成函数是为了让「相邻单元不得误清」这条约束可被 JVM 断言。
 *
 * **S07-F5 D-3：这只是最后一道资源回收防线，不再是可见性失效机制。**
 * 它回答的是「组合被移除」，而真机缺陷要回答的是「已经看不见了」—— 两者不等价，
 * 见 [scrollSelectionInvalidation] 的说明。
 */
internal fun readingUnitShouldClearOnDispose(ownsSelection: Boolean): Boolean = ownsSelection

// ── S07-F5 D-3：可见性失效（宿主层策略） ───────────────────────────────
//
// 真机现象（`docs/qa/2026-09-21-s07-qoder-device-acceptance.md` §7.3 / D-3）：
// 滚动测试 TXT 第1章建立选区 → 连续下滑到第6章 → 第1章已不在无障碍树中，
// 工具栏与第1章摘录仍常驻，还能从该工具栏保存高亮。
//
// 根因（单一假设）：**当前唯一的选区失效机制是 `DisposableEffect.onDispose`，
// 它触发于「离开组合」，而不是「离开可见范围」。** LazyColumn 会把刚滚出视口的项
// 保留在预取/缓存窗口内继续组合（`unitContentLoader` 另有 5 条 LRU 缓存），
// 因此「屏幕上没有、无障碍树没有」并不蕴含「已 dispose」—— 于是 onDispose 根本不跑，
// 选区就一直挂在一个用户看不见的单元上。
//
// 修法（任务书要求）：在**持有全局 `LazyListState` 的宿主层**建立一条由
// `layoutInfo.visibleItemsInfo` 驱动的可见性策略，让「owner 离开可见范围」成为
// 权威失效判据；`DisposableEffect` 保留为最终防线。绝不让每个非 owner item
// 各自判断「要不要清别人」—— 那会让相邻单元误清真实选区。

/** 拥有选区的单元当前是否仍在可见范围内。 */
internal enum class ScrollSelectionInvalidation {
    /** 选区仍由可见单元持有（或当前根本没有选区）：不清，也不派发。 */
    KEEP,

    /** 选区已失去可见宿主：必须清除活动选区。 */
    CLEAR,

    /**
     * 无法判定（viewport 尚未初始化）。
     *
     * 安全策略：**不清**。一帧布局空值 / 首帧未测量不得误清用户的真实选区；
     * 一旦列表完成首次布局，后续判定都走 [KEEP] / [CLEAR]。
     */
    UNDECIDABLE,
}

/**
 * 滚动 TXT 活动选区的可见性失效判定（纯函数，宿主层唯一判据）。
 *
 * 输入：
 * - [selectedRangeStart]：VM 当前选区起点（source 全书坐标；无选区时为 -1）；
 * - [readingUnits]：当前加载的读取单元（按列表顺序）；
 * - [visibleUnitIndices]：`layoutInfo.visibleItemsInfo` 映射出的**可见单元 unitIndex 集合**；
 * - [viewportInitialized]：`LazyListState.isInitialized`。
 *
 * 判定顺序（每一条都有对应行为测试）：
 * 1. 无选区（`selectedRangeStart < 0`）→ [ScrollSelectionInvalidation.KEEP]；
 * 2. viewport 未初始化 → [ScrollSelectionInvalidation.UNDECIDABLE]（不因一帧空值误清）；
 * 3. 选区起点不属于任何已加载单元 → [ScrollSelectionInvalidation.CLEAR]（安全清除：
 *    它已经没有宿主，继续留着只会让工具栏引用一段不存在的文字）；
 * 4. owner 可见 → [ScrollSelectionInvalidation.KEEP]（相邻非 owner 单元不得误清）；
 * 5. owner 不可见 → [ScrollSelectionInvalidation.CLEAR]。
 *
 * 用户手势滚动与程序化（TTS / 进度跳转）滚动走**同一个判据**：两者都只改变
 * `visibleItemsInfo`，本函数不区分滚动来源。
 */
internal fun scrollSelectionInvalidation(
    selectedRangeStart: Int,
    readingUnits: List<ReadingUnit>,
    visibleUnitIndices: Set<Int>,
    viewportInitialized: Boolean,
): ScrollSelectionInvalidation {
    if (selectedRangeStart < 0) return ScrollSelectionInvalidation.KEEP
    if (!viewportInitialized) return ScrollSelectionInvalidation.UNDECIDABLE

    val owner = readingUnits.firstOrNull { unit ->
        unit.charCount > 0 &&
            selectedRangeStart >= unit.charStart &&
            selectedRangeStart < unit.charStart + unit.charCount
    } ?: return ScrollSelectionInvalidation.CLEAR

    return if (owner.unitIndex in visibleUnitIndices) {
        ScrollSelectionInvalidation.KEEP
    } else {
        ScrollSelectionInvalidation.CLEAR
    }
}

/**
 * 可见性失效的**去重门**：一次失效只派发一次清除。
 *
 * 为什么需要它：`visibleItemsInfo` 在滚动中会高频变化，同一个「owner 已不可见」
 * 结论会被反复观察到。若每次观察都派发一次清除，会向 VM 灌入大量重复
 * `ClearSelection`（无意义的状态写入与重组），也违背「只派发一次」的契约。
 *
 * 语义：只在「上一次不是 CLEAR → 这一次是 CLEAR」的跃变上返回 true。
 * 任何非 CLEAR 结论都会把门复位，使**新的**选区在之后离开可见范围时仍能被清一次。
 */
internal class ScrollSelectionInvalidationGate {

    private var invalidated = false

    /** @return true 表示本次应当派发清除（且仅此一次）。 */
    fun shouldDispatch(decision: ScrollSelectionInvalidation): Boolean {
        val dispatch = decision == ScrollSelectionInvalidation.CLEAR && !invalidated
        invalidated = decision == ScrollSelectionInvalidation.CLEAR
        return dispatch
    }
}

/**
 * 宿主层观察到的滚动视口快照（可见单元集合 + 列表是否已完成首次布局）。
 *
 * 用 `distinctUntilChanged` 去抖：`visibleItemsInfo` 在滚动中高频变化，但只有
 * 「可见集合」真正改变时才需要重新判定，避免每帧都跑策略。
 */
private data class ScrollViewportSnapshot(
    val visibleUnitIndices: Set<Int>,
    val initialized: Boolean,
)

/**
 * 滚动 TXT 单条 ReadingUnit 的纯渲染（TTS 高亮 + 选区 + BasicTextField）。
 * 文本和坐标映射由 [ScrollUnitContentLoader] 在 IO 线程加载。display 仅用于
 * 渲染；持久化定位始终映射回 source 全局坐标。
 */
@Composable
private fun ReaderUnitTextItem(
    content: ScrollUnitProjection,
    unit: ReadingUnit,
    state: ReaderContentHostState,
    callbacks: ReaderContentHostCallbacks,
    fontFamily: FontFamily,
    searchHitRangeAbs: Pair<Int, Int>?,
    tapGate: ReaderTapToggleGate,
) {
    val settings = state.settings
    val selectionState = state.selection
    val paging = state.paging
    val s = state.source
    val effectiveText = content.displayText
    val effectiveLength = effectiveText.length
    val ttsRange = if (s.showTts && s.tts.status != "idle" && s.isTxt) {
        if (s.txtStreamingDocument != null) {
            // 流式 TXT：contentText 是窗口，sentenceRange 是窗口内偏移
            // 加 visiblePlainOffset 转全书偏移后再与 unit.charStart 做差
            val base = paging.visiblePlainOffset
            (base + s.tts.currentSentenceRange.first) to (base + s.tts.currentSentenceRange.second)
        } else {
            s.tts.currentSentenceRange
        }
    } else {
        0 to 0
    }
    val localStart = content.globalSourceToLocalDisplay(ttsRange.first)
    val localEnd = content.globalSourceToLocalDisplay(ttsRange.second)
    val searchLocal = searchHitRangeAbs?.let { abs ->
        intersectTextRange(unit.charStart, unit.charCount, abs.first until abs.second)
            ?.let { content.globalSourceRangeToLocalDisplay(it.first, it.last + 1) }
    }
    val persistentHighlightSpans = remember(s.highlights, content, settings.paper) {
        s.highlights.mapNotNull { highlight ->
            val start = parseLocatorOffset(highlight.locator_json) ?: return@mapNotNull null
            val length = highlightSourceLength(highlight.payload, highlight.text.length)
            if (length <= 0) return@mapNotNull null
            val end = (start.toLong() + length.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val intersectionStart = maxOf(start, content.sourceStartAbs)
            val intersectionEnd = minOf(end, content.sourceEndAbs)
            if (intersectionEnd <= intersectionStart) return@mapNotNull null
            val displayRange = content.globalSourceRangeToLocalDisplay(intersectionStart, intersectionEnd)
            if (displayRange.second <= displayRange.first) return@mapNotNull null
            Triple(
                displayRange.first,
                displayRange.second,
                settings.paper.highlight(highlight.color ?: "yellow"),
            )
        }
    }
    val annotated = remember(
        effectiveText,
        localStart,
        localEnd,
        settings.sentenceHighlightBg,
        settings.searchHighlightBg,
        searchLocal,
        persistentHighlightSpans,
    ) {
        if (localEnd > localStart || searchLocal != null || persistentHighlightSpans.isNotEmpty()) {
            AnnotatedString.Builder(effectiveText).apply {
                persistentHighlightSpans.forEach { (start, end, color) ->
                    addStyle(SpanStyle(background = color), start, end)
                }
                if (localEnd > localStart) {
                    addStyle(
                        SpanStyle(background = settings.sentenceHighlightBg),
                        localStart,
                        localEnd,
                    )
                }
                if (searchLocal != null) {
                    addStyle(
                        SpanStyle(background = settings.searchHighlightBg),
                        searchLocal.first,
                        searchLocal.second,
                    )
                }
            }.toAnnotatedString()
        } else {
            AnnotatedString(effectiveText)
        }
    }
    var selection by remember(content.sourceStartAbs) { mutableStateOf(TextRange.Zero) }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // 长按锚点与"待收敛"标志：由不消费事件的旁听器写入，在首次选区上报时消费一次。
    var longPressAnchor by remember { mutableStateOf<Int?>(null) }
    var pendingWordClamp by remember { mutableStateOf(false) }
    // 外部清空选区（替换/纠错规则保存成功、ClearSelection、中央视图收起）时同步撤掉
    // 本地选区 —— 对齐分页路径 selectionCleared 语义。缺少此同步时：VM 侧 selectedText
    // 已清空、工具条已隐藏，但 BasicTextField 选区与自绘手柄残留在页面上；且规则保存
    // 触发重投影后 displayText 已变，残留的旧偏移还会指到错误位置。
    val selectionCleared = selectionState.selectedText.isBlank()
    LaunchedEffect(selectionCleared) {
        if (selectionCleared && selection != TextRange.Zero) {
            selection = TextRange.Zero
            pendingWordClamp = false
            longPressAnchor = null
        }
    }
    // S07-F2.2：单元被 LazyColumn 回收 / 身份替换时，若它**拥有** VM 当前选区，
    // 那个选区就失去了宿主（用户已经看不到它），必须请求清除 —— 否则工具栏会继续
    // 引用一段不可见的文字，此时保存高亮/笔记会落到屏幕上不存在的选区。
    //
    // 两个关键点：
    // 1. 只清「拥有者」。相邻单元同样在组合中，用「不包含就清」会让它们误清真实选区。
    // 2. 必须经 rememberUpdatedState 读取**最新**的归属与回调。DisposableEffect 的
    //    onDispose 在单元离开组合时执行，此时闭包若仍捕获首次组合的快照，
    //    会拿着过期的「无选区」结论而漏清（或拿旧回调写到错的 book）。
    val ownsCurrentSelection by rememberUpdatedState(
        readingUnitOwnsSelection(
            selectedRangeStart = selectionState.selectedRangeStart,
            unitSourceStartAbs = content.sourceStartAbs,
            unitSourceEndAbs = content.sourceEndAbs,
        ),
    )
    val latestClearSelection by rememberUpdatedState(callbacks.onSelect)
    DisposableEffect(content.sourceStartAbs) {
        onDispose {
            if (readingUnitShouldClearOnDispose(ownsCurrentSelection)) {
                latestClearSelection("", -1, -1, -1)
            }
        }
    }
    val handleRadiusPx = with(LocalDensity.current) { SELECTION_HANDLE_RADIUS_DP.dp.toPx() }
    val handleStemPx = with(LocalDensity.current) { SELECTION_HANDLE_STEM_DP.dp.toPx() }
    val handleColor = settings.paperFg
    // 系统手柄设为透明后**依然可以拖动**（命中区还在），这里只是把视觉尺寸换成自绘小球，
    // 不接管任何手势 —— 这是本方案低风险的关键：手柄交互行为完全不变，只变小。
    val selectionColors = LocalTextSelectionColors.current
    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = Color.Transparent,
            backgroundColor = selectionColors.backgroundColor,
        ),
    ) {
        Box(
            Modifier.drawWithContent {
                drawContent()
                val layout = textLayout
                if (layout != null && selection.length > 0) {
                    drawSmallSelectionHandle(
                        layout, selection.min, handleRadiusPx, handleStemPx, handleColor, isStart = true,
                    )
                    drawSmallSelectionHandle(
                        layout, selection.max, handleRadiusPx, handleStemPx, handleColor, isStart = false,
                    )
                }
            },
        ) {
            BasicTextField(
                value = TextFieldValue(annotatedString = annotated, selection = selection),
                onValueChange = { value ->
                    if (value.selection.collapsed) {
                        // 选区收起 → 复位收敛状态，下一次长按重新生效。
                        pendingWordClamp = false
                        longPressAnchor = null
                    }
                    val anchor = longPressAnchor
                    val finalSelection = if (pendingWordClamp && anchor != null && value.selection.length > 0) {
                        pendingWordClamp = false
                        longPressAnchor = null
                        clampedInitialSelection(effectiveText, value.selection, anchor)
                    } else {
                        value.selection
                    }
                    selection = finalSelection
                    if (finalSelection != TextRange.Zero && finalSelection.length > 0) {
                        val selectionStart = minOf(finalSelection.start, finalSelection.end)
                            .coerceIn(0, effectiveLength)
                        val selectionEnd = maxOf(finalSelection.start, finalSelection.end)
                            .coerceIn(0, effectiveLength)
                        callbacks.onSelect(
                            effectiveText.substring(selectionStart, selectionEnd),
                            -1,
                            content.localDisplayToGlobalSource(selectionStart),
                            content.localDisplayToGlobalSource(selectionEnd),
                        )
                    } else if (
                        // S07-F2.3：与 dispose 清除共用同一个半开区间判据，避免同一规则两份实现。
                        readingUnitOwnsSelection(
                            selectedRangeStart = selectionState.selectedRangeStart,
                            unitSourceStartAbs = content.sourceStartAbs,
                            unitSourceEndAbs = content.sourceEndAbs,
                        )
                    ) {
                        callbacks.onSelect("", -1, -1, -1)
                    }
                },
                readOnly = true,
                onTextLayout = { textLayout = it },
                textStyle = TextStyle(
                    fontSize = settings.readerSettings.fontSize.sp,
                    lineHeight = (settings.readerSettings.fontSize * settings.readerSettings.lineHeight).sp,
                    color = settings.paperFg,
                    textAlign = TextAlign.Justify,
                    fontWeight = if (settings.readerSettings.fontWeightBold) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = fontFamily,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .readerTextFieldLongPressAnchor { position ->
                        textLayout?.let { layout ->
                            longPressAnchor = layout.getOffsetForPosition(position).coerceIn(0, effectiveLength)
                            pendingWordClamp = true
                        }
                    }
                    .readerTextFieldCenterTapToToggle(
                        onCenterTap = { callbacks.onToggleControls() },
                        gate = tapGate,
                    ),
            )
        }
    }
}

/** 自绘手柄小球半径（dp）。系统 Material 手柄是水滴形且明显更大，这里收小到接近 md3 观感。 */
private const val SELECTION_HANDLE_RADIUS_DP = 5f

/** 自绘手柄竖柄宽度（dp）。 */
private const val SELECTION_HANDLE_STEM_DP = 1.6f

/**
 * 画一个"小圆球 + 短竖柄"的选区手柄。
 *
 * 起点手柄：球在文字上方、柄向下指到选区起点；终点手柄：球在下方、柄向上指到选区终点。
 * 位置直接用 [TextLayoutResult.getCursorRect]，与系统手柄落在同一处，
 * 所以视觉小球和被隐藏的系统手柄（真实拖动命中区）天然重合。
 */
private fun DrawScope.drawSmallSelectionHandle(
    layout: TextLayoutResult,
    offset: Int,
    radiusPx: Float,
    stemWidthPx: Float,
    color: Color,
    isStart: Boolean,
) {
    val textLength = layout.layoutInput.text.length
    val safeOffset = offset.coerceIn(0, textLength)
    val cursor = layout.getCursorRect(safeOffset)
    val x = if (isStart) cursor.left else cursor.right
    val ballCenterY = if (isStart) {
        cursor.top - radiusPx * 1.5f
    } else {
        cursor.bottom + radiusPx * 1.5f
    }
    val stemFrom = if (isStart) ballCenterY else cursor.bottom
    val stemTo = if (isStart) cursor.top else ballCenterY
    drawLine(
        color = color,
        start = Offset(x, stemFrom),
        end = Offset(x, stemTo),
        strokeWidth = stemWidthPx,
    )
    drawCircle(color = color, radius = radiusPx, center = Offset(x, ballCenterY))
}
