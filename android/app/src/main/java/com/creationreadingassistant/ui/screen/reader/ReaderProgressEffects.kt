package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.lazy.LazyListState
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
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedBook
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedContent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce

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
     * R2-J1-I：真实普通阅读位置上报 seam。由 ReaderRoute 注入并落到临时查阅协调器的
     * `recordNormalReading`；只在「普通阅读、初始定位完成、source 坐标有效」时被调用
     * （见 [reportableSourceTarget]），temporary 模式绝不触发，因此不会覆盖普通阅读位置。
     */
    onSourcePositionChanged: (SourceNavigationTarget) -> Unit = {},
    temporaryInspection: Boolean = false,
) {
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
                plainListState.firstVisibleItemIndex to plainListState.firstVisibleItemScrollOffset,
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

    // 纯文本分块滚动进度落库，500ms 防抖避免频繁写盘（分页引擎开启时由下面的翻页持久化接管）
    LaunchedEffect(bid, epubBook, readingUnits, pagerEngineOn, temporaryInspection) {
        if (bid.isBlank() || epubBook != null || readingUnits.isEmpty() || pagerEngineOn) return@LaunchedEffect
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
                if (guardDecision == true) return@collect
                val initialPositionPending = snapshot[3] == 1
                if (!canPersistNormalReadingProgress(initialPositionPending, temporaryInspection)) return@collect
                val index = plainListState.firstVisibleItemIndex
                val totalChars = txtStreamingDocument?.totalChars ?: plainContent.length.coerceAtLeast(1)
                val offset = if (!plainListState.canScrollForward) {
                    totalChars
                } else {
                    currentPlainListOffset(plainListState, readingUnits, fallbackOffset = 0)
                }
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
                onAction(ReaderAction.SaveProgress(
                    ReadingProgressEntity(
                        book_id = bid,
                        progress_percent = percent,
                        completion_state = if (percent >= 99.9f) "finished" else "reading",
                        current_location_json = LocatorBuilder.progressJson(
                            legacyOffset = offset.coerceAtLeast(0),
                            chapterIndex = 0,
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
                    val ci = source.chapterIndexFor(off)
                    val chapterOffset = off - source.chapterStartAbs(ci)
                    onAction(ReaderAction.SaveEpubProgress(bid, ci, pct, chapterOffset, absoluteOffset = off))
                } else {
                    onAction(ReaderAction.SaveProgress(
                        ReadingProgressEntity(
                            book_id = bid,
                            progress_percent = pct,
                            completion_state = if (pct >= 99.9f) "finished" else "reading",
                            current_location_json = LocatorBuilder.progressJson(
                                legacyOffset = off.coerceAtLeast(0),
                                chapterIndex = 0,
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
    LaunchedEffect(
        readingUnits,
        savedPlainOffset,
        savedPlainPercent,
        pagerEngineOn,
        temporaryInspection,
        pendingInitialPositionState.value,
    ) {
        if (pendingInitialPositionState.value && epubBook == null && readingUnits.isNotEmpty() && !pagerEngineOn) {
            val totalChars = txtStreamingDocument?.totalChars ?: plainContent.length
            val targetOffset = if (pagedAbsOffsetState.intValue >= 0) {
                pagedAbsOffsetState.intValue
            } else if (savedPlainOffset > 0) {
                savedPlainOffset.coerceAtMost(totalChars.coerceAtLeast(0))
            } else if (savedPlainPercent > 0f) {
                (savedPlainPercent.coerceIn(0f, 100f) / 100f * totalChars).toInt()
            } else {
                0
            }
            plainListState.scrollToItem(unitIndexForOffset(readingUnits, targetOffset))
            // S12：恢复完成即锚定（用真实列表状态，不依赖 500ms 防抖后的第一次
            // observe）——用户在首次采样前移动时，第一次 observe 就能判为移动并放行
            // 保存。临时查阅会话不锚定（查阅位置不是普通阅读锚点）；未武装书 no-op。
            if (!temporaryInspection) {
                LegadoMarkerSaveGuard.anchorAt(
                    bid,
                    plainListState.firstVisibleItemIndex to plainListState.firstVisibleItemScrollOffset,
                )
            }
            pendingInitialPositionState.value = false
        }
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
            consumeSourceRequest()
        }

        when {
            epubBook != null -> navigateChaptered(preferGlobalOffset = false, markdown = false)
            markdownDocument != null -> navigateChaptered(preferGlobalOffset = true, markdown = true)
            (plainContent.isNotBlank() || txtStreamingDocument != null) &&
                (pagerEngineOn || readingUnits.isNotEmpty()) -> {
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
    LaunchedEffect(
        bid,
        epubBook,
        plainContent,
        highlights,
        notes,
        pendingHighlightIdState.value,
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
        if (epubBook != null) {
            val book = epubBook
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
        } else if (plainContent.isNotBlank() || txtStreamingDocument != null) {
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
