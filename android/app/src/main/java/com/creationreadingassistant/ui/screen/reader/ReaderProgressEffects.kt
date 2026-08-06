package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.locator.AnchorCacheStore
import com.creationreadingassistant.feature.reader.locator.AnchorConfidence
import com.creationreadingassistant.feature.reader.locator.AnchorResolver
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.feature.reader.pager.PagedChapterSource
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedBook
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
    navFocusBlockIndexState: MutableState<Int?>,
    settingsRef: MutableState<ReaderSettings>,
    pagedJumpRequest: MutableState<Int?>,
    onAction: (ReaderAction) -> Unit,
    showNotice: (String) -> Unit,
    goToChapter: (Int) -> Unit,
    jumpToPlainOffset: (Int) -> Unit,
    onLoadChapterBlocks: suspend (String, Int) -> List<DocBlock>,
    onExtractChapterText: suspend (String, Int) -> String,
) {
    // 本次阅读计时（对照 web useReaderSession.activeReadingMs）
    LaunchedEffect(bid) {
        while (true) {
            delay(1000)
            if (!isLoading && error == null) activeReadingMsState.longValue += 1000
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

    // 纯文本分块滚动进度落库，500ms 防抖避免频繁写盘（分页引擎开启时由下面的翻页持久化接管）
    LaunchedEffect(bid, epubBook, readingUnits, pagerEngineOn) {
        if (bid.isBlank() || epubBook != null || readingUnits.isEmpty() || pagerEngineOn) return@LaunchedEffect
        snapshotFlow {
            Triple(
                plainListState.firstVisibleItemIndex,
                plainListState.firstVisibleItemScrollOffset,
                plainListState.canScrollForward,
            )
        }
            .debounce(500)
            .collect {
                val index = plainListState.firstVisibleItemIndex
                val unit = readingUnits.getOrNull(index)
                val item = plainListState.layoutInfo.visibleItemsInfo.firstOrNull()
                val fraction = if (item != null && item.size > 0) {
                    plainListState.firstVisibleItemScrollOffset.toFloat() / item.size
                } else {
                    0f
                }
                val offset = if (unit != null) {
                    unit.charStart + (unit.charCount * fraction).toInt()
                } else {
                    0
                }
                val totalChars = txtStreamingDocument?.totalChars ?: plainContent.length.coerceAtLeast(1)
                val percent = when {
                    !plainListState.canScrollForward && index > 0 -> 100f
                    else -> (offset * 100f / totalChars).coerceIn(0f, 100f)
                }
                onAction(ReaderAction.SaveProgress(
                    ReadingProgressEntity(
                        book_id = bid,
                        progress_percent = percent,
                        completion_state = if (percent >= 99.9f) "finished" else "reading",
                        current_location_json = if (markdownDocument != null) {
                            """{"offset":${offset.coerceAtLeast(0)},"space":"canonical"}"""
                        } else {
                            """{"offset":${offset.coerceAtLeast(0)}}"""
                        },
                        updated_at = nowIso(),
                    ),
                ))
            }
    }

    // 翻页进度落库（分页引擎侧），与滚动侧同样 500ms 防抖、同一张表同一套字段
    LaunchedEffect(bid, epubBook, pagerEngineOn, pagedSource) {
        if (bid.isBlank() || !pagerEngineOn) return@LaunchedEffect
        snapshotFlow { pagedAbsOffsetState.intValue to pagedPercentState.floatValue }
            .debounce(500)
            .collect { (off, pct) ->
                if (off < 0) return@collect
                val source = pagedSource ?: return@collect
                if (epubBook != null) {
                    val ci = source.chapterIndexFor(off)
                    val chapterOffset = off - source.chapterStartAbs(ci)
                    onAction(ReaderAction.SaveEpubProgress(bid, ci, pct, chapterOffset))
                } else {
                    onAction(ReaderAction.SaveProgress(
                        ReadingProgressEntity(
                            book_id = bid,
                            progress_percent = pct,
                            completion_state = if (pct >= 99.9f) "finished" else "reading",
                            current_location_json = if (markdownDocument != null) {
                                """{"offset":${off.coerceAtLeast(0)},"space":"canonical"}"""
                            } else {
                                """{"offset":${off.coerceAtLeast(0)}}"""
                            },
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

    // SE4：精确跳转 —— 打开阅读器并定位到该高亮/笔记所在位置（优先 locator_json 行内偏移，兜底 chapter_title / progress_percent）。
    LaunchedEffect(bid, epubBook, plainContent, highlights, notes, pendingHighlightIdState.value) {
        val hid = pendingHighlightIdState.value ?: return@LaunchedEffect
        if (isLoading || error != null) return@LaunchedEffect
        val target = highlights.firstOrNull { it.id == hid }
            ?: notes.firstOrNull { it.id == hid }
            ?: run {
                pendingHighlightIdState.value = null
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
