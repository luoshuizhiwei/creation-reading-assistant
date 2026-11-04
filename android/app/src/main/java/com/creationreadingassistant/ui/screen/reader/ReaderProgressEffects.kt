package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.annotations.parseAnnotationNavigationTarget
import com.creationreadingassistant.feature.annotations.resolveAnnotationTarget
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.locator.AnchorCacheStore
import com.creationreadingassistant.feature.reader.locator.AnchorConfidence
import com.creationreadingassistant.feature.reader.locator.AnchorResolver
import com.creationreadingassistant.feature.reader.locator.EpubLocatorMapping
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.feature.reader.locator.LocatorBuilder
import com.creationreadingassistant.feature.reader.locator.ReaderLocator
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationTarget
import com.creationreadingassistant.feature.reader.pager.PagedChapterSource
import com.creationreadingassistant.feature.reader.pager.ScrollUnitContentLoader
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedBook
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedContent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull

/**
 * Phase 2 结构拆分：从 [com.creationreadingassistant.ui.screen.ReaderScreen] 抽出的 8 个
 * 「会话计时 / 阅读提醒 / 进度持久化 / 位置恢复 / 高亮精确定位」LaunchedEffect。
 *
 * 每个 effect 体逐字搬运自 ReaderScreen，不改任何时序与防抖参数（500ms）。
 *
 * ## 行为保真核心：State-holder 传参
 *
 * Compose 的 `LaunchedEffect(key) { body }` 在 key 不变时不会重启，且运行中的协程执行的是
 * **首次启动时捕获的 lambda**；体内对局部 `val` 的读取拿到的是启动时的快照值。因此凡是在
 * effect 体内被读取、却**不是** LaunchedEffect key 的可变状态，必须以 State-holder 形式
 * 传入（读 `.value` 才能拿到当前快照值），否则会读到旧值 —— 这是真行为变更：
 *
 * - [activeReadingMsState]：effect「阅读提醒」每秒在循环里读取，effect「计时」每秒自增；
 * - [pagedAbsOffsetState] / [pagedPercentState]：effect「翻页进度防抖」用 `snapshotFlow`
 *   观察二者，effect「滚动位置恢复」在体内读取 `pagedAbsOffset`。snapshotFlow 必须在块内
 *   读 `.value` 才会发射，传值参数会让 flow 永不更新。
 *
 * 既是 key 又在体内被读 / 被写的状态（[pendingInitialPositionState] /
 * [pendingHighlightIdState]）也走 holder：用 `.value` 既作 key（组合期快照读取，变化即重启）
 * 又作读写入口，与原 `var by` 行为一致。[navFocusBlockIndexState] 仅被写，holder 的 `.value`
 * 赋值等价原 `=` 写入。
 *
 * 非状态的局部 `val`（epubBook / plainContent / highlights 等）以值参数传入：原代码里它们
 * 也是被 lambda 捕获的局部值（启动时快照），与 holder 无关，行为一致。
 *
 * 顶层 helper（[nowIso] / [progressToChapterIndex] / [blockIndexForChapterOffset] /
 * [unitIndexForOffset]）与本文件同包，直接调用；ReaderScreen 的局部 fun
 * （[goToChapter] / [jumpToPlainOffset] / [showNotice]）与挂起回调
 * （[onLoadChapterBlocks] / [onExtractChapterText] / [onAction]）作为函数参数传入。
 */
@Suppress("LongParameterList")
@OptIn(FlowPreview::class)
@Composable
internal fun ReaderProgressEffects(
    bid: String,
    isLoading: Boolean,
    error: String?,
    readingActive: Boolean,
    progressPercent: Float,
    loadedBook: ReaderLoadedBook?,
    chapterIndex: Int,
    epubBook: EpubBook?,
    readingUnits: List<ReadingUnit>,
    pagerEngineOn: Boolean,
    plainListState: LazyListState,
    epubListState: LazyListState,
    txtStreamingDocument: PlainTextDocument?,
    plainContent: String,
    markdownDocument: ReaderDocument?,
    pagedSource: PagedChapterSource?,
    savedPlainOffset: Int,
    savedPlainPercent: Float,
    chapterBlocks: List<DocBlock>,
    savedEpubOffsetInChapter: Int,
    chapterBase: Int,
    blockGlobalOffsets: List<Int>,
    highlights: List<HighlightEntity>,
    notes: List<NoteEntity>,
    bookIndex: BookIndex?,
    chapterStartOffsets: List<Int>,
    anchorCacheStore: AnchorCacheStore,
    recentChapters: SnapshotStateList<Int>,
    activeReadingMsState: MutableLongState,
    pagedAbsOffsetState: MutableIntState,
    pagedPercentState: MutableFloatState,
    pendingInitialPositionState: MutableState<Boolean>,
    pendingHighlightIdState: MutableState<String?>,
    pendingSourceLocatorJsonState: MutableState<String?>,
    requestedSourceNavigationChapterState: MutableState<Int?>,
    navFocusBlockIndexState: MutableState<Int?>,
    scrollFocusRequestState: MutableState<SearchScrollFocusRequest?>,
    chapterIndexState: MutableIntState,
    settingsRef: MutableState<ReaderSettings>,
    pagedJumpRequest: MutableState<Int?>,
    onAction: (ReaderAction) -> Unit,
    showNotice: (String) -> Unit,
    goToChapter: (Int) -> Unit,
    jumpToPlainOffset: (Int) -> Unit,
    jumpToMarkdownOffset: (Int) -> Unit,
    onLoadChapterBlocks: suspend (String, Int) -> List<DocBlock>,
    onExtractChapterText: suspend (String, Int) -> String,
    /**
     * 滚动 TXT 正文加载状态观察接缝（CRA-JOINT-31-MERGE-FREEZE-ACCEPT-v2）：与
     * [ReaderContentHostPlainTextBranch] 共用同一实例（由 ReaderScreen 的 holders 持有），
     * 恢复落位判定据此区分「真实正文布局」与「Loading 占位布局」。
     */
    scrollUnitContentLoader: ScrollUnitContentLoader = remember { ScrollUnitContentLoader() },
    /**
     * R2-J1-I：真实普通阅读位置上报 seam。由 ReaderRoute 注入并落到临时查阅协调器的
     * `recordNormalReading`；只在「普通阅读、初始定位完成、source 坐标有效」时被调用
     * （见 [reportableSourceTarget]），temporary 模式绝不触发，因此不会覆盖普通阅读位置。
     */
    onSourcePositionChanged: (SourceNavigationTarget) -> Unit = {},
    temporaryInspection: Boolean = false,
    /**
     * CRA-READER-SESSION-PROGRESS-GUARD-FIX-v1：本阅读会话的位置保护守卫实例。
     * 由 ReaderScreen 按会话持有并透传到退出落库，与 [persistCurrentProgress] 共用
     * **同一个**实例，保证「武装 / 锚定 / 观测 / 退出裁决」读写的是同一份会话状态。
     */
    sessionPositionGuard: ReaderSessionPositionGuard = remember { ReaderSessionPositionGuard() },
) {
    // CRA-TASK48-v4：本会话拥有的**临时返回会话凭证**。守卫的所有修改入口
    // （退出裁决、三条防抖裁决、onDispose 释放）都限定在同一凭证上：本页进入组合时
    // 采纳该书**当前**武装会话，因此这些裁决只对本页拥有的会话生效。
    // 与 ReaderScreen.persistOnLeave 采纳的是同一个 Session（同一 key、同一守卫），
    // 保证「防抖解冻」与「退出裁决」对同一会话说话。
    val ownedTemporaryReturnSession = remember(bid, temporaryInspection) {
        val owner = if (temporaryInspection) null else TemporaryReturnSaveGuard.session(bid)
        ReaderClampDiagnostics.log("temporary_return_session_adopt", "bid" to bid,
            "temporary" to temporaryInspection, "owner" to owner)
        owner
    }

    // 本次阅读计时（对照 web useReaderSession.activeReadingMs）
    val currentReadingActive = rememberUpdatedState(readingActive)
    val currentProgressPercent = rememberUpdatedState(progressPercent)
    LaunchedEffect(bid) {
        trackActiveReadingTime(currentReadingActive, activeReadingMsState) {
            onAction(
                ReaderAction.UpdateReadingActivity(
                    bookId = bid,
                    active = true,
                    progressPercent = currentProgressPercent.value,
                ),
            )
        }
    }

    // 阅读提醒：护眼提醒 + 阅读节奏提示（对照 web useReaderReminders）
    LaunchedEffect(bid) {
        var eyeLast = 0L
        var rhythmLast = 0L
        while (true) {
            delay(1000)
            val st = settingsRef.value
            val eyeMin = st.eyeCareReminderMinutes.coerceAtLeast(1)
            val eyeThreshold = eyeMin * 60_000L
            if (activeReadingMsState.longValue >= eyeLast + eyeThreshold) {
                eyeLast = (activeReadingMsState.longValue / eyeThreshold) * eyeThreshold
                showNotice("已连续阅读 ${eyeMin} 分钟，建议休息一下眼睛。")
            }
            if (st.readingRhythmReminderEnabled) {
                val rMin = st.readingRhythmReminderMinutes.coerceAtLeast(1)
                val rThreshold = rMin * 60_000L
                if (activeReadingMsState.longValue >= rhythmLast + rThreshold) {
                    rhythmLast = (activeReadingMsState.longValue / rThreshold) * rThreshold
                    showNotice("已读 ${rMin} 分钟，注意休息。")
                }
            }
        }
    }

    // R5：文档加载完成后，用当前章节初始化「最近浏览」置顶项。
    LaunchedEffect(loadedBook, chapterIndex) {
        if (loadedBook != null && recentChapters.isEmpty()) {
            recentChapters.add(chapterIndex)
        }
    }

    // ── S12：未换算 Legado marker 的保存保护 + 首开提示 ──────────────────────────
    // seam 只在「marker 存在且本次打开未能换算」时非 null（见 ReaderDocumentLoader）。
    // 武装后，两处 500ms 防抖与本文件内的保存判定都会被 [LegadoMarkerSaveGuard] 抑制，
    // 直到用户真正改变阅读位置；退出/暂停落库由 ReaderScreen.persistOnLeave 读同一注册表。
    // 换算成功（seam == null）的书永远不武装，全部既有保存路径逐字保持不变。
    val unconvertedLegadoPosition = remember(loadedBook) {
        (loadedBook?.content as? ReaderLoadedContent.Text)?.unconvertedLegadoPosition
    }
    LaunchedEffect(bid, unconvertedLegadoPosition) {
        if (unconvertedLegadoPosition == null) {
            LegadoMarkerSaveGuard.disarm(bid)
        } else {
            LegadoMarkerSaveGuard.arm(bid)
        }
    }
    // 滚动↔分页切换改变位置身份量纲：重锚而非解除（切换本身不是用户移动）。
    LaunchedEffect(bid, pagerEngineOn) {
        LegadoMarkerSaveGuard.resetAnchor(bid)
    }
    // S12：分页路径的即时锚定——分页引擎首次上报的位置就是恢复落点（先于任何用户翻页），
    // 用它锚定，使「首次防抖采样前移动」也能判为移动。已锚定会话不接受后续覆盖；
    // 未武装书 no-op；临时查阅会话不锚定（查阅位置不是普通阅读锚点）。
    LaunchedEffect(bid, pagerEngineOn, temporaryInspection) {
        if (!pagerEngineOn || temporaryInspection) return@LaunchedEffect
        snapshotFlow { pagedAbsOffsetState.intValue }.collect { off ->
            if (off >= 0) LegadoMarkerSaveGuard.anchorAt(bid, off)
        }
    }
    // S12：分页→滚动切换时的即时锚定（上一次停在分页、本次进入滚动，且初始定位已完成）：
    // 立即以当前列表状态锚定，覆盖「切换后首次采样前移动」的窗口。首次组合不触发
    // （避免把恢复前的 (0,0) 误锚）——初始恢复的锚定由恢复 effect 在 scrollToItem 后完成。
    var previousPagerEngineOn by remember(bid) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(bid, pagerEngineOn, temporaryInspection) {
        val switchedIntoScroll = previousPagerEngineOn == true && !pagerEngineOn
        previousPagerEngineOn = pagerEngineOn
        if (switchedIntoScroll && !temporaryInspection && !pendingInitialPositionState.value) {
            LegadoMarkerSaveGuard.anchorAt(
                bid,
                if (markdownDocument != null) listOf(chapterIndexState.intValue,
                    epubListState.firstVisibleItemIndex, epubListState.firstVisibleItemScrollOffset)
                else plainListState.firstVisibleItemIndex to plainListState.firstVisibleItemScrollOffset,
            )
        }
    }
    var legadoMarkerNoticeShown by remember(bid) { mutableStateOf(false) }
    LaunchedEffect(bid, isLoading, error, unconvertedLegadoPosition) {
        if (!legadoMarkerNoticeShown && unconvertedLegadoPosition != null && !isLoading && error == null) {
            legadoMarkerNoticeShown = true
            // 文案必须与真实行为一致：无法换算 = 目录不可用或章号越界；当前落点是
            // 近似位置（导入百分比或书首）；保护期间 marker 不会被保存覆盖，用户
            // 开始阅读（改变位置）后才按本书记录新进度。
            showNotice("开源阅读导入的位置暂未能换算（本书目录不可用或章节越界），已按近似位置打开；开始阅读后将记录本书进度")
        }
    }

    // Markdown has its own chapter list. Finish restoration there, not in the TXT list.
    LaunchedEffect(bid, markdownDocument, chapterIndex, chapterBlocks, pagerEngineOn,
        pendingInitialPositionState.value, pendingHighlightIdState.value,
        pendingSourceLocatorJsonState.value) {
        val doc = markdownDocument ?: return@LaunchedEffect
        if (pagerEngineOn || !pendingInitialPositionState.value || chapterBlocks.isEmpty() ||
            pendingHighlightIdState.value != null || pendingSourceLocatorJsonState.value != null) return@LaunchedEffect
        val target = doc.locate(savedPlainOffset.coerceIn(0, doc.totalChars))
        if (chapterIndex != target.first) {
            goToChapter(target.first)
            return@LaunchedEffect
        }
        val blocks = onLoadChapterBlocks(bid, target.first)
        if (chapterIndexState.intValue != target.first) return@LaunchedEffect
        val chapter = blocks.filterIsInstance<DocBlock.Markdown>().firstOrNull()?.chapter ?: return@LaunchedEffect
        val unit = markdownRenderUnitIndexForChapterOffset(chapter, target.second,
            chapterStartOffsets.getOrElse(target.first) { 0 },
            (doc as? MarkdownDocument)?.isWholeDocumentParse == true) ?: return@LaunchedEffect
        scrollFocusRequestState.value = SearchScrollFocusRequest(
            bookKey = bid, chapterIndex = target.first, renderUnitIndex = unit,
            origin = ReaderScrollFocusOrigin.SOURCE_NAVIGATION,
        )
        pendingInitialPositionState.value = false
    }

    // Anchor only after the real restoration focus has been consumed.
    LaunchedEffect(bid, markdownDocument, pagerEngineOn, temporaryInspection,
        pendingInitialPositionState.value, scrollFocusRequestState.value) {
        if (markdownDocument != null && !pagerEngineOn && !temporaryInspection &&
            !pendingInitialPositionState.value && scrollFocusRequestState.value == null) {
            LegadoMarkerSaveGuard.anchorAt(bid, listOf(chapterIndexState.intValue,
                epubListState.firstVisibleItemIndex, epubListState.firstVisibleItemScrollOffset))
        }
    }

    // Debounce the actual Markdown list; keep temporary/initial/Legado save gates.
    LaunchedEffect(bid, markdownDocument, chapterIndex, chapterBlocks, pagerEngineOn, temporaryInspection) {
        if (bid.isBlank() || markdownDocument == null || pagerEngineOn || chapterBlocks.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            listOf(epubListState.firstVisibleItemIndex, epubListState.firstVisibleItemScrollOffset,
                if (pendingInitialPositionState.value || scrollFocusRequestState.value != null) 1 else 0)
        }.debounce(500).collect { sample ->
            val guarded = LegadoMarkerSaveGuard.observe(bid, legadoGuardObservation(
                initialPositionPending = sample[2] == 1,
                temporaryInspection = temporaryInspection,
                position = listOf(chapterIndex, sample[0], sample[1]),
            ))
            if (guarded == true || !canPersistNormalReadingProgress(sample[2] == 1, temporaryInspection)) return@collect
            val chapter = chapterBlocks.filterIsInstance<DocBlock.Markdown>().firstOrNull()?.chapter
                ?: return@collect
            val item = epubListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == sample[0] }
            val fraction = if (item != null && item.size > 0) sample[1].toFloat() / item.size else 0f
            val offset = markdownVisibleSourceOffset(chapter, chapterBase,
                (markdownDocument as? MarkdownDocument)?.isWholeDocumentParse == true, sample[0], fraction)
                ?: return@collect
            if (TemporaryReturnSaveGuard.observe(bid, offset, chapterIndex, owned = ownedTemporaryReturnSession)) return@collect
            reportableSourceTarget(bid, false, chapterStartOffsets, offset,
                initialPositionPending = false, temporaryInspection = temporaryInspection)?.let(onSourcePositionChanged)
            persistCurrentProgress(
                bid, loadedBook, error, false, null, false, -1, pagedSource, chapterIndex,
                epubListState, blockGlobalOffsets, chapterBase, chapterBlocks, bookIndex,
                chapterStartOffsets, 0, markdownDocument, txtStreamingDocument, plainContent,
                onAction = onAction,
            )
        }
    }

    // Drag intent comes from the real list's interaction channel, never from remeasurement.
    // Read the final coordinates synchronously on release, so quick exits do not await debounce.
    LaunchedEffect(bid, plainListState, temporaryInspection, epubBook, markdownDocument, pagerEngineOn) {
        if (!sessionPositionGuardParticipates(temporaryInspection) ||
            epubBook != null || markdownDocument != null || pagerEngineOn) return@LaunchedEffect
        try {
            plainListState.interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is DragInteraction.Start -> sessionPositionGuard.beginUserScroll(
                        plainListState.firstVisibleItemIndex, plainListState.firstVisibleItemScrollOffset,
                    )
                    is DragInteraction.Stop, is DragInteraction.Cancel -> sessionPositionGuard.endUserScroll(
                        plainListState.firstVisibleItemIndex, plainListState.firstVisibleItemScrollOffset,
                    )
                }
            }
        } finally {
            sessionPositionGuard.endUserScroll(
                plainListState.firstVisibleItemIndex, plainListState.firstVisibleItemScrollOffset,
            )
        }
    }

    // CRA-READER-SESSION-PROGRESS-GUARD-FIX-v1：会话位置守卫的**即时**位移观测。
    //
    // 缺陷（任务14引入）：守卫只在下面 500ms 防抖 collector 里 observe。用户滚动后
    // 防抖尚未发射即退出（快速滑动退出）时，守卫仍以为「未移动」，退出落库被压回旧初始位置。
    // 修复：这里按列表坐标变化即时采样（无 debounce），退出时再由 persistCurrentProgress
    // 用**实时**坐标做最终裁决，双保险。
    //
    // 列表坐标流不区分用户与几何来源；只有上方真实输入通道或明确导航可置位。
    // 临时查阅仍不参与，恢复滚动与被动测量都不会授权保存。
    LaunchedEffect(bid, plainListState, epubBook, markdownDocument, pagerEngineOn, temporaryInspection) {
        if (bid.isBlank() || !sessionPositionGuardParticipates(temporaryInspection)) return@LaunchedEffect
        if (epubBook != null || markdownDocument != null || pagerEngineOn) return@LaunchedEffect
        snapshotFlow {
            plainListState.firstVisibleItemIndex to plainListState.firstVisibleItemScrollOffset
        }.collect { (index, offsetPx) ->
            // CRA-READER-GUARD-BOUND-MUMU-ACCEPT-v2：只读诊断接缝（不改变任何保存/守卫行为）。
            // 记录守卫真实观测到的列表坐标，供设备验收重建「恢复/加载/锚定/重测量/用户移动」时序。
            ReaderClampDiagnostics.log(
                "session_guard_observe",
                // CRA-TASK48-v4：诊断接缝（只读字段）。该 effect 按 bid 生效，
                // 补上书籍身份后这条位移观测才能在整条身份链上绑定目标书，
                // 保护结论才可被核实；不改变任何守卫、保存或 pending/marker 行为。
                "bid" to bid,
                "index" to index,
                "offsetPx" to offsetPx,
                "armed" to sessionPositionGuard.isArmed(),
                "moved" to sessionPositionGuard.hasUserMoved(),
            )
            sessionPositionGuard.observe(index, offsetPx)
            // Read-only post-call snapshot: the original event above intentionally keeps its
            // pre-observe fields; this pairs the actual guard result with the same book/coordinate.
            ReaderClampDiagnostics.log(
                "session_guard_observe_result",
                "bid" to bid,
                "index" to index,
                "offsetPx" to offsetPx,
                "armed" to sessionPositionGuard.isArmed(),
                "moved" to sessionPositionGuard.hasUserMoved(),
            )
        }
    }

    // CRA-READER-GUARD-BOUND-MUMU-ACCEPT-v2：正文就绪度（Loading→Loaded）只读诊断接缝。
    // 独立 effect，只读取当前可见单元的就绪度并记录状态跃迁；不参与任何保存/落位判定。
    LaunchedEffect(bid, plainListState, epubBook, markdownDocument, pagerEngineOn, temporaryInspection) {
        if (bid.isBlank() || epubBook != null || markdownDocument != null || pagerEngineOn) return@LaunchedEffect
        snapshotFlow {
            scrollRestoreContentReadiness(
                visibleUnitIndices = plainListState.layoutInfo.visibleItemsInfo.map { it.index },
                stateOf = { scrollUnitContentLoader.currentStateOf(it) },
            ).name
        }.distinctUntilChanged().collect { state ->
            ReaderClampDiagnostics.log("content_readiness", "bid" to bid, "state" to state)
        }
    }

    // 纯文本分块滚动进度落库，500ms 防抖避免频繁写盘（分页引擎开启时由下面的翻页持久化接管）
    LaunchedEffect(bid, epubBook, markdownDocument, readingUnits, pagerEngineOn, temporaryInspection) {
        if (bid.isBlank() || epubBook != null || markdownDocument != null || readingUnits.isEmpty() || pagerEngineOn) return@LaunchedEffect
        snapshotFlow {
            listOf(
                plainListState.firstVisibleItemIndex,
                plainListState.firstVisibleItemScrollOffset,
                if (plainListState.canScrollForward) 1 else 0,
                if (pendingInitialPositionState.value) 1 else 0,
            )
        }
            .debounce(500)
            .collect { snapshot ->
                // S12：未换算 Legado marker 的保存保护。必须在既有 pending 门控**之前**
                // 判定——用户移动的观测依赖防抖快照，而武装期间 pending 门控本身也会
                // 抑制保存；锚点只取「初始定位完成后」的第一个落点，恢复前的起点与
                // 程序化恢复滚动都不会被误判为用户移动。位置是否可交给守卫由
                // [legadoGuardObservation] 统一裁定（含临时查阅必须传 null）。
                val guardDecision = LegadoMarkerSaveGuard.observe(
                    bid,
                    legadoGuardObservation(
                        initialPositionPending = snapshot[3] == 1,
                        temporaryInspection = temporaryInspection,
                        position = snapshot[0] to snapshot[1],
                    ),
                )
                if (guardDecision == true) {
                    ReaderClampDiagnostics.log("debounce_save_blocked_by_guard", "bid" to bid)
                    return@collect
                }
                // CRA-READER-SESSION-PROGRESS-GUARD-FIX-v1：会话守卫的即时观测在下方
                // 独立 effect 中完成（不等待 500ms 防抖）；这里只读它的会话状态决定是否
                // 抑制本次防抖落库。临时查阅会话不参与守卫（未武装 → hasUserMoved()=false
                // 且 savedPlainOffset>0 会抑制，与既有 pending/临时门控同向，不放松保存禁令）。
                if (!sessionPositionGuard.hasUserMoved() && savedPlainOffset > 0) {
                    ReaderClampDiagnostics.log("debounce_save_blocked_by_session_guard", "bid" to bid)
                    return@collect
                }
                val initialPositionPending = snapshot[3] == 1
                if (!canPersistNormalReadingProgress(initialPositionPending, temporaryInspection)) {
                    ReaderClampDiagnostics.log(
                        "debounce_save_blocked_by_pending_or_inspection",
                        "bid" to bid,
                        "pending" to initialPositionPending,
                        "temp" to temporaryInspection,
                    )
                    return@collect
                }
                val index = plainListState.firstVisibleItemIndex
                val totalChars = txtStreamingDocument?.totalChars ?: plainContent.length.coerceAtLeast(1)
                // CRA-JOINT-31-MERGE-FREEZE-ACCEPT-v2：视口物理位置 → 持久位置语义的映射
                // 收进纯函数 [plainScrollPersistOffset]（书末不可前滚时取全书末尾，而不是
                // 视口顶部项的字符偏移），供 JVM 直接回归两条语义的区分。
                val offset = plainScrollPersistOffset(
                    canScrollForward = plainListState.canScrollForward,
                    listState = plainListState,
                    units = readingUnits,
                    totalChars = totalChars,
                )
                if (offset == 0) {
                    val readiness = scrollRestoreContentReadiness(
                        visibleUnitIndices = plainListState.layoutInfo.visibleItemsInfo.map { it.index },
                        stateOf = { scrollUnitContentLoader.currentStateOf(it) },
                    )
                    if (readiness != ScrollRestoreContentReadiness.READY) {
                        ReaderClampDiagnostics.log("debounce_save_blocked_zero_unready", "readiness" to readiness)
                        return@collect
                    }
                }
                ReaderClampDiagnostics.log(
                    "debounce_save_dispatch",
                    "bid" to bid,
                    "offset" to offset,
                    "totalChars" to totalChars,
                    "canScrollForward" to plainListState.canScrollForward,
                    "fvi" to plainListState.firstVisibleItemIndex,
                    "fviOffset" to plainListState.firstVisibleItemScrollOffset,
                )
                val percent = when {
                    !plainListState.canScrollForward && index > 0 -> 100f
                    else -> (offset * 100f / totalChars).coerceIn(0f, 100f)
                }
                // R2-J1-I：把当前精确 source 位置上报给导航协调器。此处与下面的落库共用
                // [reportableSourceTarget] 门槛（普通阅读 + 非 initial pending + 有效坐标），
                // temporary 模式下返回 null，绝不写回普通阅读位置。滚动路径恒为 TXT / Markdown
                // （epubBook != null 已在上方守卫返回），isEpub=false 走 canonical 全局偏移规则。
                reportableSourceTarget(
                    bookId = bid,
                    isEpub = false,
                    chapterStartOffsets = chapterStartOffsets,
                    absoluteOffset = offset,
                    initialPositionPending = initialPositionPending,
                    temporaryInspection = temporaryInspection,
                )?.let(onSourcePositionChanged)
                // 书架进度卡要显示「第 N 章」，TXT 也存真实章号 —— 此前恒写 0，书架永远显示
                // 「第1章」（用户 2026-09-18 反馈）。恢复端（parseStoredAbsoluteOffset）只读
                // legacyOffset，不读 ci，因此对 TXT 写真实章号不影响续读定位。
                // 章号用全书 chapterStartOffsets 反推：effect 协程里读组合参数 chapterIndex
                // 会拿到启动时的旧值，不可靠。
                val ci = com.creationreadingassistant.feature.reader.locator.EpubLocatorMapping
                    .toChapterOffset(offset.coerceAtLeast(0), chapterStartOffsets).first
                if (TemporaryReturnSaveGuard.observe(bid, offset, ci, owned = ownedTemporaryReturnSession)) return@collect
                onAction(ReaderAction.SaveProgress(
                    ReadingProgressEntity(
                        book_id = bid,
                        progress_percent = percent,
                        completion_state = if (percent >= 99.9f) "finished" else "reading",
                        current_location_json = LocatorBuilder.progressJson(
                            legacyOffset = offset.coerceAtLeast(0),
                            chapterIndex = ci.coerceAtLeast(0),
                            charOffset = offset.coerceAtLeast(0),
                            space = if (markdownDocument != null) "canonical" else null,
                        ),
                        updated_at = nowIso(),
                    ),
                ))
            }
    }

    // 翻页进度落库（分页引擎侧），与滚动侧同样 500ms 防抖、同一张表同一套字段
    LaunchedEffect(bid, epubBook, pagerEngineOn, pagedSource, temporaryInspection) {
        if (bid.isBlank() || !pagerEngineOn) return@LaunchedEffect
        snapshotFlow {
            Triple(
                pagedAbsOffsetState.intValue,
                pagedPercentState.floatValue,
                pendingInitialPositionState.value,
            )
        }
            .debounce(500)
            .collect { (off, pct, initialPositionPending) ->
                // S12：未换算 Legado marker 的保存保护（分页路径，语义同滚动侧；
                // 位置是否可交给守卫由 [legadoGuardObservation] 统一裁定）。
                val guardDecision = LegadoMarkerSaveGuard.observe(
                    bid,
                    legadoGuardObservation(
                        initialPositionPending = initialPositionPending,
                        temporaryInspection = temporaryInspection,
                        position = if (off < 0) null else off,
                    ),
                )
                if (guardDecision == true) return@collect
                if (off < 0 || !canPersistNormalReadingProgress(initialPositionPending, temporaryInspection)) return@collect
                val source = pagedSource ?: return@collect
                val ci = if (epubBook != null) {
                    source.chapterIndexFor(off)
                } else {
                    com.creationreadingassistant.feature.reader.locator.EpubLocatorMapping
                        .toChapterOffset(off.coerceAtLeast(0), chapterStartOffsets).first
                }
                if (TemporaryReturnSaveGuard.observe(bid, off, ci, owned = ownedTemporaryReturnSession)) return@collect
                // R2-J1-I：分页路径同样上报精确 source 位置（EPUB 由 sourceNavigationTargetFor
                // 用 chapterStartOffsets 推导章节元组；TXT/Markdown 走 canonical 全局偏移）。
                reportableSourceTarget(
                    bookId = bid,
                    isEpub = epubBook != null,
                    chapterStartOffsets = chapterStartOffsets,
                    absoluteOffset = off,
                    initialPositionPending = initialPositionPending,
                    temporaryInspection = temporaryInspection,
                )?.let(onSourcePositionChanged)
                if (epubBook != null) {
                    val chapterOffset = off - source.chapterStartAbs(ci)
                    onAction(ReaderAction.SaveEpubProgress(bid, ci, pct, chapterOffset, absoluteOffset = off))
                } else {
                    // TXT/Markdown 分页路径同样存真实章号（见上方滚动路径的说明）。
                    // 注意不能用 pagedSource.chapterIndexFor —— 它是**当前章作用域**的
                    // source，章数恒为 1，只会返回 0（真机验证踩过）。这里用全书
                    // chapterStartOffsets 反推，与保存的 offset 自洽。
                    val ci = com.creationreadingassistant.feature.reader.locator.EpubLocatorMapping
                        .toChapterOffset(off.coerceAtLeast(0), chapterStartOffsets).first
                    onAction(ReaderAction.SaveProgress(
                        ReadingProgressEntity(
                            book_id = bid,
                            progress_percent = pct,
                            completion_state = if (pct >= 99.9f) "finished" else "reading",
                            current_location_json = LocatorBuilder.progressJson(
                                legacyOffset = off.coerceAtLeast(0),
                                chapterIndex = ci.coerceAtLeast(0),
                                charOffset = off.coerceAtLeast(0),
                                space = if (markdownDocument != null) "canonical" else null,
                            ),
                            updated_at = nowIso(),
                        ),
                    ))
                }
            }
    }

    // 纯文本恢复上次滚动位置（分页引擎自己按 initialOffset 恢复；从翻页切回滚动时接上当前页位置）
    // CRA-TXT-REENTRY-FIX-v4：显式接入 bid（书籍身份）与 plainListState（列表身份）。
    // 切书或列表状态对象被重建时，本 effect 以新 key 取消旧恢复协程——挂起点被取消
    // 后绝不用旧恢复目标操作新列表（此前仅 effect 体内隐式依赖、注释声称"key 失效
    // 即取消"，但 bid/plainListState 并不在 key 集合里，Codex P2）。
    LaunchedEffect(
        bid,
        plainListState,
        readingUnits,
        savedPlainOffset,
        savedPlainPercent,
        pagerEngineOn,
        temporaryInspection,
        pendingInitialPositionState.value,
        pendingSourceLocatorJsonState.value,
    ) {
        if (!(pendingInitialPositionState.value && epubBook == null && markdownDocument == null && readingUnits.isNotEmpty() && !pagerEngineOn)) {
            return@LaunchedEffect
        }
        val totalChars = txtStreamingDocument?.totalChars ?: plainContent.length
        val sourceTarget = pendingSourceLocatorJsonState.value?.let { locatorJson ->
            SourceNavigationContract.targetFromStoredLocation(bid, locatorJson)
        }?.let { target ->
            SourceNavigationContract.resolvePlainPosition(target)
        }
        val targetOffset = sourceTarget?.absoluteOffset ?: savedPlainOffset
        val targetPercent = if (sourceTarget != null) {
            if (totalChars > 0) (targetOffset.toFloat() / totalChars * 100f).coerceIn(0f, 100f) else 0f
        } else {
            savedPlainPercent
        }
        // CRA-TXT-REENTRY-FIX-v2/v3：恢复控制流收进生产函数 runPlainRestoreLoop
        //（本 effect 与动态测试调用同一实现）。v3 修复 v2 的两个缺口：
        // ①落位确认失败后不再每轮重订阅 snapshotFlow 立即重滚——挂起等待布局快照
        //   变化（同状态不重复滚动）；②书尾短项 clamp 的合法落位（首项≠目标但
        //   目标==末可见项）不再被永久拒绝。
        // 等待期间 pending 保持 true：初始 0 位置防抖/退出保存被
        // canPersistNormalReadingProgress 拦截；书籍身份（bid）与列表身份
        // （plainListState）已显式列入本 effect 的 key 集合（v4），连同
        // readingUnits/savedPlainOffset/pagerEngineOn 变化一起取消并重启恢复，
        // 挂起点被取消后绝不用旧目标操作新列表。
        // 正文就绪度观察（CRA-JOINT-31-MERGE-FREEZE-ACCEPT-v2）：可见单元的加载状态。
        val contentReadinessNow: () -> ScrollRestoreContentReadiness = {
            scrollRestoreContentReadiness(
                visibleUnitIndices = plainListState.layoutInfo.visibleItemsInfo.map { it.index },
                stateOf = { scrollUnitContentLoader.currentStateOf(it) },
            )
        }
        // CRA-READER-SESSION-PROGRESS-GUARD-FIX-v1：只有普通阅读会话武装守卫。
        // 临时查阅（同书 locator）根本不参与本守卫，避免污染普通阅读的会话状态。
        if (sessionPositionGuardParticipates(temporaryInspection)) {
            // CRA-READER-GUARD-BOUND-MUMU-ACCEPT-v2：只读诊断接缝。
            ReaderClampDiagnostics.log(
                "session_guard_arm",
                "bid" to bid,
                "offset" to targetOffset,
                "percent" to targetPercent,
            )
            sessionPositionGuard.arm(targetOffset, targetPercent)
        }
        runPlainRestoreLoop(
            savedPlainOffset = targetOffset,
            savedPlainPercent = targetPercent,
            totalChars = totalChars,
            units = readingUnits,
            pagedAbsOffset = pagedAbsOffsetState.intValue,
            scrollTo = { index ->
                val isEndClamp = index == readingUnits.lastIndex &&
                    (targetOffset >= totalChars || targetPercent >= 99f)
                if (isEndClamp) {
                    plainListState.scrollToItem(index, scrollOffset = 100_000)
                } else {
                    val unit = readingUnits.getOrNull(index)
                    val visibleItem = plainListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                    val intraOffsetPx = if (unit != null && unit.charCount > 0 && targetOffset > unit.charStart) {
                        val fraction = ((targetOffset - unit.charStart).toFloat() / unit.charCount).coerceIn(0f, 1f)
                        val itemHeight = visibleItem?.size ?: plainListState.layoutInfo.viewportSize.height
                        if (itemHeight > 0) {
                            (itemHeight * fraction).toInt()
                        } else {
                            0
                        }
                    } else {
                        0
                    }
                    plainListState.scrollToItem(index, scrollOffset = intraOffsetPx)
                }
            },
            settleSnapshot = {
                val info = plainListState.layoutInfo
                Triple(
                    info.visibleItemsInfo.firstOrNull()?.index ?: plainListState.firstVisibleItemIndex,
                    info.visibleItemsInfo.lastOrNull()?.index ?: plainListState.firstVisibleItemIndex,
                    info.totalItemsCount == 0,
                )
            },
            contentReadiness = contentReadinessNow,
            canScrollForward = { plainListState.canScrollForward },
            firstVisibleItemScrollOffset = { plainListState.firstVisibleItemScrollOffset },
            layoutChanges = snapshotFlow {
                val info = plainListState.layoutInfo
                // CRA-JOINT-31-MERGE-FREEZE-ACCEPT-v1（缺陷：等待流与落位判定源不一致）：
                // `settleSnapshot` 的首项取自 `layoutInfo.visibleItemsInfo`（**测量结果**，
                // 程序化 `scrollToItem` 后滞后一帧），而此处原取自 `firstVisibleItemIndex`
                // （`scrollToItem` 立即写入的**滚动请求**位置）。两源相差一帧：
                // 变化流会在“请求位置”上先发出更新，落位判定却仍读到测量前的旧首项，
                // 于是循环“等到了变化 → 判定未落位 → 再等下一次变化”，一旦列表在目标处
                // 稳定下来便永久挂起、`pendingInitialPosition` 永不解除、滚动保存被门控拦截
                // （设备实测：fvi=1 fviOff=0 vis=[0,1] target=1 反复判定未落位）。
                // 修复：变化流改为与落位判定**同源**（都用 `visibleItemsInfo` 测量结果），
                // 二者不再跨帧错位；落位判定语义（测量到的首个可见项）保持不变。
                //
                // CRA-JOINT-31-MERGE-FREEZE-ACCEPT-v2：比较键里必须再带上**正文就绪度**
                // 与加载状态修订号。短末项 clamp 场景中，占位帧与真实正文帧的
                // (totalItemsCount, first, last) 可能完全相同，只比较布局会让循环在
                // 占位帧之后永久挂起（pending 永不解除）；读 stateRevision 保证任何单元
                // 加载状态变化都会唤醒等待，读 currentStateOf 保证就绪度参与比较。
                scrollUnitContentLoader.stateRevision.value
                val visible = info.visibleItemsInfo.map { it.index }
                plainRestoreLayoutSnapshot(
                    totalItemsCount = info.totalItemsCount,
                    firstVisibleItemIndex = info.visibleItemsInfo.firstOrNull()?.index
                        ?: plainListState.firstVisibleItemIndex,
                    lastVisibleItemIndex = info.visibleItemsInfo.lastOrNull()?.index
                        ?: plainListState.firstVisibleItemIndex,
                    contentReadiness = scrollRestoreContentReadiness(
                        visibleUnitIndices = visible,
                        stateOf = { scrollUnitContentLoader.currentStateOf(it) },
                    ),
                    canScrollForward = plainListState.canScrollForward,
                )
            }.conflate(),
        )
        // S12：恢复完成即锚定（用真实列表状态，不依赖 500ms 防抖后的第一次
        // observe）——用户在首次采样前移动时，第一次 observe 就能判为移动并放行
        // 保存。临时查阅会话不锚定（查阅位置不是普通阅读锚点）；未武装书 no-op。
        if (sessionPositionGuardParticipates(temporaryInspection)) {
            LegadoMarkerSaveGuard.anchorAt(
                bid,
                plainListState.firstVisibleItemIndex to plainListState.firstVisibleItemScrollOffset,
            )
            // CRA-READER-GUARD-BOUND-MUMU-ACCEPT-v2：只读诊断接缝（锚点即恢复落位后的真实列表坐标）。
            ReaderClampDiagnostics.log(
                "session_guard_anchor",
                "bid" to bid,
                "index" to plainListState.firstVisibleItemIndex,
                "offsetPx" to plainListState.firstVisibleItemScrollOffset,
            )
            sessionPositionGuard.anchor(
                plainListState.firstVisibleItemIndex,
                plainListState.firstVisibleItemScrollOffset,
            )
        }
        ReaderClampDiagnostics.log(
            "restore_pending_released",
            "bid" to bid,
            "fvi" to plainListState.firstVisibleItemIndex,
            "fviOffset" to plainListState.firstVisibleItemScrollOffset,
            "canForward" to plainListState.canScrollForward,
        )
        if (sourceTarget != null) {
            pendingSourceLocatorJsonState.value = null
            requestedSourceNavigationChapterState.value = null
        }
        pendingInitialPositionState.value = false
    }

    // EPUB 滚动模式恢复到章节内的具体文本块；分页模式由 PagedTxtReaderHost 的
    // initialOffset / onPositionChanged 接管。
    LaunchedEffect(
        chapterBlocks,
        savedEpubOffsetInChapter,
        pagerEngineOn,
        pendingInitialPositionState.value,
    ) {
        if (pendingInitialPositionState.value && epubBook != null && !pagerEngineOn && chapterBlocks.isNotEmpty()) {
            val targetGlobal = chapterBase + savedEpubOffsetInChapter.coerceAtLeast(0)
            val itemIndex = blockGlobalOffsets
                .indexOfLast { it in 0..targetGlobal }
                .coerceAtLeast(0)
            epubListState.scrollToItem(itemIndex)
            pendingInitialPositionState.value = false
        }
    }

    // R2-J1.1：通用 source 路由定位。参数只承载原文 locator，绝不接受页码或渲染单元
    // 索引；跨章滚动通过带书/章身份的一次性 focus 请求落到 ContentHost，避免异步加载
    // 完成后用旧列表索引劫持用户手动切换的章节。
    LaunchedEffect(
        bid,
        isLoading,
        error,
        epubBook,
        markdownDocument,
        plainContent,
        txtStreamingDocument,
        readingUnits,
        chapterStartOffsets,
        chapterIndex,
        pagerEngineOn,
        pendingSourceLocatorJsonState.value,
    ) {
        val locatorJson = pendingSourceLocatorJsonState.value ?: return@LaunchedEffect
        if (isLoading || error != null) return@LaunchedEffect

        fun consumeSourceRequest() {
            pendingSourceLocatorJsonState.value = null
            requestedSourceNavigationChapterState.value = null
        }

        val target = SourceNavigationContract.targetFromStoredLocation(bid, locatorJson)
        if (target == null) {
            consumeSourceRequest()
            showNotice("无法识别此阅读位置")
            return@LaunchedEffect
        }

        suspend fun navigateChaptered(preferGlobalOffset: Boolean, markdown: Boolean) {
            val position = SourceNavigationContract.resolveChapteredPosition(
                target = target,
                chapterStartOffsets = chapterStartOffsets,
                preferGlobalOffset = preferGlobalOffset,
            )
            val targetChapter = position?.chapterIndex
            if (position == null || targetChapter == null) {
                consumeSourceRequest()
                showNotice("此阅读位置与当前正文不一致")
                return
            }
            if (chapterIndex != targetChapter) {
                if (requestedSourceNavigationChapterState.value != null) {
                    // 已主动切往目标章后又离开，视为用户手动导航，不能再把人拉回去。
                    consumeSourceRequest()
                } else {
                    requestedSourceNavigationChapterState.value = targetChapter
                    goToChapter(targetChapter)
                }
                return
            }
            if (pagerEngineOn) {
                pagedJumpRequest.value = position.absoluteOffset
                consumeSourceRequest()
                return
            }

            val blocks = onLoadChapterBlocks(bid, targetChapter)
            if (chapterIndexState.intValue != targetChapter) return
            val focusIndex = if (markdown) {
                val markdownBlock = blocks.filterIsInstance<DocBlock.Markdown>().firstOrNull()
                markdownBlock?.let {
                    markdownRenderUnitIndexForChapterOffset(
                        chapter = it.chapter,
                        inChapter = position.chapterOffset ?: 0,
                        chapterBase = chapterStartOffsets.getOrElse(targetChapter) { 0 },
                        blocksGlobal = (markdownDocument as? MarkdownDocument)?.isWholeDocumentParse == true,
                    )
                }
            } else {
                blockIndexForChapterOffset(blocks, position.chapterOffset ?: 0)
            }
            if (focusIndex == null) {
                consumeSourceRequest()
                showNotice("此阅读位置尚未准备好")
                return
            }
            scrollFocusRequestState.value = SearchScrollFocusRequest(
                bookKey = bid,
                chapterIndex = targetChapter,
                renderUnitIndex = focusIndex,
                origin = ReaderScrollFocusOrigin.SOURCE_NAVIGATION,
            )
            if (markdown) pendingInitialPositionState.value = false
            consumeSourceRequest()
        }

        when {
            epubBook != null -> navigateChaptered(preferGlobalOffset = false, markdown = false)
            markdownDocument != null -> navigateChaptered(preferGlobalOffset = true, markdown = true)
            (plainContent.isNotBlank() || txtStreamingDocument != null) &&
                (pagerEngineOn || readingUnits.isNotEmpty()) -> {
                if (pendingInitialPositionState.value && !pagerEngineOn) {
                    // TXT 滚动初始恢复由 runPlainRestoreLoop 统一接入就绪度与落位保护，此处不单发跳转
                    return@LaunchedEffect
                }
                val position = SourceNavigationContract.resolvePlainPosition(target)
                if (position == null) {
                    consumeSourceRequest()
                    showNotice("无法识别此阅读位置")
                } else {
                    jumpToPlainOffset(position.absoluteOffset)
                    consumeSourceRequest()
                }
            }
        }
    }

    // SE4：精确跳转 —— 打开阅读器并定位到该高亮/笔记所在位置（优先 locator_json 行内偏移，兜底 chapter_title / progress_percent）。
    //
    // M-BUG-10：key 里补上 isLoading / error / pendingSourceLocatorJsonState.value。
    // 这三项**在函数体内被读，却从来不是 key**：首次运行多半发生在文档尚未就绪时，
    // 于是走 `isLoading` 分支**提前返回且不消费** pendingHighlightId；此后它只能靠
    // highlights/notes 这类 Room 流发射才重跑 —— 表现为「阅读中途毫无征兆地跳到某条
    // 批注/高亮的位置」（用户报告的"突然回到之前的阅读进度"）。纳入 key 后，请求会在
    // 文档就绪 / 源定位让位之后**立即**被消费，不再滞留到某个无关的流发射时刻。
    LaunchedEffect(
        bid,
        epubBook,
        plainContent,
        highlights,
        notes,
        pendingHighlightIdState.value,
        isLoading,
        error,
        pendingSourceLocatorJsonState.value,
    ) {
        if (pendingSourceLocatorJsonState.value != null) return@LaunchedEffect
        val hid = pendingHighlightIdState.value ?: return@LaunchedEffect
        if (isLoading || error != null) return@LaunchedEffect
        // R1-N1.1：类型化回源 —— hid 可能是 bare rawId（旧入口，含书内高亮 / 搜索结果）或
        // typed stableId（highlight:{id} / note:{id} / bookmark:{id}，来自「我的→阅读笔记」
        // 与书内书签）。typed 目标按类型严格匹配，不跨表猜测；类型不符或记录不存在时清除
        // 请求并给出明确提示。bare rawId 保留旧「先高亮、后笔记」兼容语义。
        val typed = parseAnnotationNavigationTarget(hid)
        val target = resolveAnnotationTarget(typed, typed?.rawId ?: hid, highlights, notes)
        if (target == null) {
            pendingHighlightIdState.value = null
            if (typed != null) {
                showNotice("无法定位该阅读笔记，记录可能已删除或类型不匹配")
            }
            return@LaunchedEffect
        }
        val hl = target as? HighlightEntity
        val nt = target as? NoteEntity
        val chapterTitle = hl?.chapter_title ?: nt?.chapter_title
        val targetProgress = hl?.progress_percent ?: nt?.progress_percent
        val locatorJson = hl?.locator_json ?: nt?.locator_json
        val decodedLocator = LocatorCodec.decode(locatorJson)
        val targetKind = if (hl != null) "highlight" else "note"
        val targetId = hl?.id ?: nt?.id.orEmpty()
        val targetExcerpt = hl?.text ?: nt?.excerpt ?: nt?.body
        // CRA-MD-ANNOTATION-RETURN-FIX-v1：分派决策提取为纯函数并由本 effect 实际消费
        // （穷尽 when、无兜底）。红基线：占位实现逐字等价于原行为（Markdown 无支 →
        // 静默清除）；修复版见 git 历史与本文件 green 注释。
        val plainUsable = plainContent.isNotBlank() || txtStreamingDocument != null
        when (
            val plan = annotationNavigationPlan(
                epubBookPresent = epubBook != null,
                markdownDocumentPresent = markdownDocument != null,
                plainUsable = plainUsable,
                decodedLocatorPresent = decodedLocator != null,
                locatorJson = locatorJson,
                targetProgress = targetProgress,
                markdownTotalChars = markdownDocument?.totalChars ?: 0,
            )
        ) {
            AnnotationNavigationPlan.EpubBranch -> {
            val book = epubBook ?: return@LaunchedEffect
            if (decodedLocator != null && bookIndex != null) {
                val cached = anchorCacheStore.get(targetKind, targetId, bid)
                val resolved = cached ?: run {
                    val provisionalChapter = decodedLocator.chapterIndex
                        ?.coerceIn(0, book.chapters.lastIndex)
                        ?: chapterStartOffsets.indexOfLast {
                            it <= (decodedLocator.legacyOffset ?: 0)
                        }.coerceIn(0, book.chapters.lastIndex)
                    val chapterText = onExtractChapterText(bid, provisionalChapter)
                    AnchorResolver.resolve(
                        locator = decodedLocator,
                        chapterStarts = chapterStartOffsets,
                        chapterText = chapterText,
                        excerpt = targetExcerpt,
                    ).also {
                        anchorCacheStore.save(targetKind, targetId, bid, it)
                    }
                }
                val ci = resolved.chapterIndex.coerceIn(0, book.chapters.lastIndex)
                val locOffset = chapterStartOffsets.getOrElse(ci) { 0 } + resolved.charOffset
                goToChapter(ci)
                if (resolved.confidence == AnchorConfidence.APPROXIMATE) {
                    showNotice("原文可能已变化，已跳到最接近的位置")
                }
                if (pagerEngineOn) {
                    pagedJumpRequest.value = locOffset
                    navFocusBlockIndexState.value = null
                } else {
                    // R6：通过 ViewModel 加载目标章块，避免主线程 Zip I/O 造成 ANR
                    val blocks = onLoadChapterBlocks(bid, ci)
                    navFocusBlockIndexState.value = blockIndexForChapterOffset(blocks, resolved.charOffset)
                }
            } else {
                val idx = if (chapterTitle != null) {
                    val exact = book.chapters.indexOfFirst { it.title == chapterTitle }.takeIf { it >= 0 }
                    exact ?: progressToChapterIndex(book, targetProgress)
                } else {
                    progressToChapterIndex(book, targetProgress)
                }
                goToChapter(idx)
                navFocusBlockIndexState.value = null
            }
            }
            AnnotationNavigationPlan.PlainBranch -> {
            if (decodedLocator != null) {
                if (txtStreamingDocument != null) {
                    // 流式模式：有界窗口读取替代加载整个文件
                    // TXT locator 的 charOffset 实际为全书偏移（等价于 legacyOffset）
                    val targetOffset = decodedLocator.charOffset
                        ?: decodedLocator.legacyOffset
                        ?: 0
                    val windowStart = (targetOffset - 2000).coerceAtLeast(0)
                    val windowText = txtStreamingDocument.readWindowAround(targetOffset, 2000, 2000)
                    if (windowText.isNotEmpty()) {
                        // 将 locator 偏移调整为窗口相对偏移，供 AnchorResolver 使用
                        val windowRelativeLocator = decodedLocator.copy(
                            charOffset = (targetOffset - windowStart).coerceAtLeast(0),
                            legacyOffset = targetOffset - windowStart,
                            chapterIndex = 0,
                        )
                        val resolved = anchorCacheStore.get(targetKind, targetId, bid) ?: AnchorResolver.resolve(
                            locator = windowRelativeLocator,
                            chapterStarts = listOf(0),
                            chapterText = windowText,
                            excerpt = targetExcerpt,
                        ).also { anchorCacheStore.save(targetKind, targetId, bid, it) }
                        // 将解析结果转回全书偏移
                        val globalOffset = windowStart + resolved.charOffset
                        jumpToPlainOffset(globalOffset)
                        if (resolved.confidence == AnchorConfidence.APPROXIMATE) {
                            showNotice("原文可能已变化，已跳到最接近的位置")
                        }
                    }
                } else if (plainContent.isNotEmpty()) {
                    val resolved = anchorCacheStore.get(targetKind, targetId, bid) ?: AnchorResolver.resolve(
                        locator = decodedLocator,
                        chapterStarts = listOf(0),
                        chapterText = plainContent,
                        excerpt = targetExcerpt,
                    ).also { anchorCacheStore.save(targetKind, targetId, bid, it) }
                    jumpToPlainOffset(resolved.charOffset)
                    if (resolved.confidence == AnchorConfidence.APPROXIMATE) {
                        showNotice("原文可能已变化，已跳到最接近的位置")
                    }
                }
            } else if (targetProgress != null) {
                val totalLen = txtStreamingDocument?.totalChars ?: plainContent.length
                jumpToPlainOffset((targetProgress.coerceIn(0f, 100f) / 100f * totalLen).toInt())
            }
        }

            else -> {
            // 消费由 applyMarkdownAnnotationPlan 执行；deferred 转交 SE3 canonical 通道
            // （其跨章延迟机制：goToChapter 后 requestedSourceNavigationChapterState 等待
            // 目标章加载，再按带书/章身份的一次性 focus 应用渲染单元偏移）。
            applyMarkdownAnnotationPlan(
                plan = plan,
                handOffToSourceNavigation = { locator -> pendingSourceLocatorJsonState.value = locator },
                markdownJump = { offset -> jumpToMarkdownOffset(offset) },
                plainJump = { offset -> jumpToPlainOffset(offset) },
                notice = { message -> showNotice(message) },
            )
            }
        }
        pendingHighlightIdState.value = null
    }
}

/**
 * 读取统一的有效阅读 State-holder，保证长生命周期计时协程会响应前后台、加载与错误状态变更。
 */
internal suspend fun trackActiveReadingTime(
    readingActiveState: State<Boolean>,
    activeReadingMsState: MutableLongState,
    onHeartbeat: () -> Unit,
) {
    while (true) {
        delay(1_000)
        if (readingActiveState.value) {
            activeReadingMsState.longValue += 1_000
            onHeartbeat()
        }
    }
}

/**
 * 由「当前实际可见的精确 source 坐标」构造 [SourceNavigationTarget]。
 *
 * 只接受 source 绝对偏移；页号、LazyList index、display offset、替换投影 offset 不在
 * 参数之列，无法进入状态。无有效坐标（书籍 ID 空白、绝对偏移为负）时返回 null，绝不
 * 构造 offset=0 的假位置。
 *
 * - TXT / Markdown：absoluteOffset 即全书 source 绝对偏移（chapterIndex 恒 0）。
 * - EPUB：absoluteOffset 为全书 source 绝对偏移，章节坐标由 [EpubLocatorMapping]
 *   从 chapterStartOffsets 推导（与历史 locator 同源），供后续 resolveChapteredPosition
 *   校验全局偏移与章节元组一致。
 */
internal fun sourceNavigationTargetFor(
    bookId: String,
    isEpub: Boolean,
    chapterStartOffsets: List<Int>,
    absoluteOffset: Int,
): SourceNavigationTarget? {
    if (absoluteOffset < 0) return null
    val locator = if (isEpub) {
        val (chapterIndex, charOffset) = EpubLocatorMapping.toChapterOffset(absoluteOffset, chapterStartOffsets)
        ReaderLocator(
            legacyOffset = absoluteOffset,
            chapterIndex = chapterIndex,
            charOffset = charOffset,
            excerptFingerprint = null,
        )
    } else {
        ReaderLocator(
            legacyOffset = absoluteOffset,
            chapterIndex = 0,
            charOffset = absoluteOffset,
            excerptFingerprint = null,
        )
    }
    return SourceNavigationContract.target(bookId, locator)
}

/**
 * 是否允许把当前普通阅读进度持久化为「可恢复位置」。
 *
 * - 初始定位尚未完成（[initialPositionPending]）：route 初始跳转 / 跨章节程序化跳转不算
 *   用户普通阅读，禁止落库；
 * - 临时查阅进行中（[temporaryInspection]）：自动进度保存不得覆盖普通阅读进度。
 */
internal fun canPersistNormalReadingProgress(
    initialPositionPending: Boolean,
    temporaryInspection: Boolean,
): Boolean = !initialPositionPending && !temporaryInspection

/**
 * S12 流式 TXT marker 保存保护（单书会话判定状态机，纯 JVM 可测）。
 *
 * 背景：Legado 导入的 TXT 位置在「拿不到可靠章节边界」（流式目录兜底「全文」单章 /
 * 章号越界）时无法换算，`ReaderLoadedContent.Text.unconvertedLegadoPosition` 非 null。
 * 此时阅读器落在近似位置（导入百分比或书首），**任何**普通保存（500ms 防抖、退出落库）
 * 都会把 marker 覆盖为当前近似位置的原生 locator——用户并没有真正选择过这个位置。
 *
 * 规则：打开时武装；第一次观测到「初始定位完成后的落点」记为锚点并继续抑制；
 * 此后位置一旦偏离锚点（用户滚动 / 翻页 / 跳章 / 搜索跳转，含用户主动开启的自动翻页）
 * 即解除保护，之后的保存走既有正常流程。仅关闭提示不算移动——本状态机只认位置变化。
 *
 * 位置以调用方给定的「定位身份」比较（滚动 = 列表 index+偏移对，分页 = 全书偏移）；
 * null 表示该次快照尚不可定位（初始定位未完成 / 分页引擎未上报），按继续抑制处理，
 * 且不捕获锚点——防止把恢复前的起点误当锚点、把程序化恢复滚动误判为用户移动。
 */
internal class LegadoMarkerSaveGuardSession {
    internal var anchor: Any? = null
    internal var anchorCaptured = false

    /**
     * @return true = 继续抑制普通保存（尚未锚定，或仍在恢复锚点上，或本次快照不可定位）；
     *   false = 用户已改变阅读位置，解除保护并允许本次按正常流程保存。
     */
    fun observe(position: Any?): Boolean {
        if (position == null) {
            // 不可定位快照（初始定位未完成 / 临时查阅进行中）：既不锚定也不解除，
            // 维持抑制——临时查阅里的位置变化不是普通阅读位置变化。
            return true
        }
        if (!anchorCaptured) {
            anchor = position
            anchorCaptured = true
            return true
        }
        return position == anchor
    }
}

/**
 * 单书会话注册表：ReaderProgressEffects（两处 500ms 防抖保存）写，
 * ReaderScreen.persistOnLeave（退出/暂停/离开落库）读。进程内会话级状态，
 * 只按 bookId 记录「是否存在未换算 marker 且用户尚未移动」；换算成功或原生进度
 * 的书永远不会被武装，既有保存路径零变化。离开阅读器不主动清除——重新打开时
 * 由 armed effect 依据最新 loader 产物重新武装或解除（marker 仍在库则保持保护，
 * 已被原生进度覆盖则解除），陈旧条目只影响该书自己的退出保存且必被最新状态纠正。
 */
internal object LegadoMarkerSaveGuard {
    private val sessions = HashMap<String, LegadoMarkerSaveGuardSession>()

    @Synchronized
    fun arm(bookId: String) {
        if (bookId.isNotBlank()) sessions[bookId] = LegadoMarkerSaveGuardSession()
    }

    @Synchronized
    fun disarm(bookId: String) {
        sessions.remove(bookId)
    }

    @Synchronized
    fun isArmed(bookId: String): Boolean = sessions.containsKey(bookId)

    /**
     * 初始定位完成后的**即时锚定**（恢复落点由恢复路径直接上报，不依赖 500ms 防抖后的
     * 第一次 observe）：用户在首次防抖采样前移动时，第一次 observe 就能判为移动并放行
     * 保存。仅对已武装且尚未锚定的会话生效；未武装书是 no-op（既有路径零变化），
     * 迟到/重复的锚点请求不覆盖既有锚点，null 位置不锚定。
     */
    @Synchronized
    fun anchorAt(bookId: String, position: Any?) {
        val session = sessions[bookId] ?: return
        if (!session.anchorCaptured && position != null) {
            session.anchor = position
            session.anchorCaptured = true
        }
    }

    /**
     * 清除锚点但保持武装：滚动↔分页切换会改变「定位身份」的量纲（列表 index+偏移 vs
     * 全书偏移），切换本身不是用户移动——新渲染模式下的第一次落点重新锚定，继续抑制，
     * 直到用户在新模式下真正改变位置。
     */
    @Synchronized
    fun resetAnchor(bookId: String) {
        sessions[bookId]?.let { session ->
            session.anchor = null
            session.anchorCaptured = false
        }
    }

    /**
     * 防抖收集器入口。返回 null = 该书未武装（调用方走既有门控，行为不变）；
     * true = 继续抑制本次保存；false = 用户已移动，保护已解除，本次按正常流程保存。
     * [position] 为 null 表示本次快照不可定位（初始定位未完成 / 引擎未上报）。
     */
    @Synchronized
    fun observe(bookId: String, position: Any?): Boolean? {
        val session = sessions[bookId] ?: return null
        val keepSuppressing = session.observe(position)
        if (!keepSuppressing) sessions.remove(bookId)
        return keepSuppressing
    }
}

/**
 * S12 接线判定（纯函数，两处防抖 collect 共用）：决定本次快照的位置是否可作为
 * 「普通阅读移动」交给 [LegadoMarkerSaveGuard]。
 *
 * - 初始定位未完成（[initialPositionPending]）→ null：恢复前的起点不是阅读位置；
 * - **临时查阅进行中（[temporaryInspection]）→ null**：查阅里的导航不是普通阅读移动，
 *   返回原锚点后 marker 必须仍受保护（本次集成复核修正的核心语义）；
 * - 其余情况透传 [position]（不可定位时为 null）。
 */
internal fun legadoGuardObservation(
    initialPositionPending: Boolean,
    temporaryInspection: Boolean,
    position: Any?,
): Any? = if (initialPositionPending || temporaryInspection) null else position

/**
 * R2-J1-I：可上报的普通阅读 source 目标。
 *
 * 只有「普通阅读、初始定位已完成、source 坐标有效」时才返回目标，其余一律返回 null。
 * 这是 [ReaderProgressEffects] 两处自动位置上报（滚动 500ms 防抖 / 翻页 500ms 防抖）共用的
 * 判定入口，与落库门槛 [canPersistNormalReadingProgress] 同源 —— 保证「上报位置」与
 * 「保存位置」不会分叉：temporary 查阅与 initial pending 期间二者同时被抑制。
 *
 * 坐标语义完全由 [sourceNavigationTargetFor] 决定（只接受 source 绝对偏移，页号 /
 * LazyList index / display offset / 替换投影 offset 无对应字段，进不来）。
 */
internal fun reportableSourceTarget(
    bookId: String,
    isEpub: Boolean,
    chapterStartOffsets: List<Int>,
    absoluteOffset: Int,
    initialPositionPending: Boolean,
    temporaryInspection: Boolean,
): SourceNavigationTarget? {
    if (!canPersistNormalReadingProgress(initialPositionPending, temporaryInspection)) return null
    return sourceNavigationTargetFor(bookId, isEpub, chapterStartOffsets, absoluteOffset)
}
