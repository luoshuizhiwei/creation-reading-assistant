package com.creationreadingassistant.ui.screen.reader

import android.app.Activity
import android.content.Context
import android.Manifest
import android.os.Build
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.ClipboardManager
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.feature.reader.pager.PagedChapterSource
import com.creationreadingassistant.ui.screen.reader.ReaderChromeAction
import com.creationreadingassistant.ui.screen.reader.ReaderSheet
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedBook
import com.creationreadingassistant.ui.screen.reader.tts.TtsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Phase 3 结构拆分：从 [com.creationreadingassistant.ui.screen.ReaderScreen] 主函数抽出的 9 个
 * 闭包辅助函数，改为顶层函数。
 *
 * ## 行为保真原则
 *
 * - 只读依赖（val / 不可变入参）作为值参数传入；
 * - 已转换为 State-holder 的可变状态（chapterIndexState / autoPagingActiveState）直接传入
 *   MutableState，在函数内用 `.value` 读写，与原 `var x` 语义一致；
 * - 未转换的可变状态（showTts / controlsVisible / selectedText / sheet / searchQuery）
 *   通过 `onXxxChange: (T) -> Unit` setter 参数写入，ReaderScreen 传 `{ xxx = it }`；
 * - 互相调用的 fun（seekToPercent → goToChapter / jumpToPlainOffset；handleChromeAction →
 *   openTts；openTts → showNotice）作为函数参数传入，避免循环依赖；
 * - 顶层 helper（[nowIso] / [unitIndexForOffset]）与本文件同包，直接调用。
 *
 * 这些是普通 `fun`（非 @Composable），由 ReaderScreen 在组合期调用并读取最新状态值，
 * 与原内联 fun 行为一致。
 */

/** 依据当前选区生成 locator_json（T1）。纯函数，无副作用。 */
internal fun computeLocatorJson(
    epubBook: EpubBook?,
    selectedGlobalOffset: Int,
    chapterStartOffsets: List<Int>,
    selectedText: String,
    selectedRangeStart: Int,
): String? = when {
    epubBook != null && selectedGlobalOffset >= 0 -> {
        val ci = chapterStartOffsets.indexOfLast { it <= selectedGlobalOffset }.coerceAtLeast(0)
        val co = selectedGlobalOffset - chapterStartOffsets.getOrElse(ci) { 0 }
        LocatorCodec.encode(selectedGlobalOffset, ci, co, selectedText)
    }
    epubBook == null && selectedRangeStart >= 0 ->
        LocatorCodec.encode(selectedRangeStart, 0, selectedRangeStart, selectedText)
    else -> null
}

/**
 * 跳转到指定章节。pagerEngineOn 时走翻页定位，否则只更新 chapterIndex + LoadChapter。
 * chapterIndex 写入已转换的 [chapterIndexState]。
 */
internal fun goToChapter(
    i: Int,
    epubBook: EpubBook?,
    markdownDocument: ReaderDocument?,
    pagerEngineOn: Boolean,
    chapterStartOffsets: List<Int>,
    pagedJumpRequest: MutableState<Int?>,
    chapterIndexState: MutableIntState,
    tts: TtsController,
    onAction: (ReaderAction) -> Unit,
    bid: String,
) {
    val maxIndex = when {
        epubBook != null -> epubBook!!.chapters.lastIndex
        markdownDocument != null -> markdownDocument.chapters.lastIndex
        else -> return
    }
    val clamped = i.coerceIn(0, maxIndex)
    if (pagerEngineOn) {
        pagedJumpRequest.value = chapterStartOffsets.getOrElse(clamped) { 0 }
    }
    chapterIndexState.value = clamped
    tts.stop()
    // R6：章节块加载 + 进度保存统一由 ViewModel 处理
    onAction(ReaderAction.LoadChapter(bid, clamped))
}

/** TXT 跳转统一入口：分页引擎开着走翻页定位，否则滚动列表。两条路都以全书字符偏移为准。 */
internal fun jumpToPlainOffset(
    offset: Int,
    pagerEngineOn: Boolean,
    readingUnits: List<ReadingUnit>,
    scope: CoroutineScope,
    plainListState: LazyListState,
    pagedJumpRequest: MutableState<Int?>,
) {
    if (pagerEngineOn) {
        pagedJumpRequest.value = offset
    } else if (readingUnits.isNotEmpty()) {
        scope.launch { plainListState.scrollToItem(unitIndexForOffset(readingUnits, offset)) }
    }
}

/**
 * 持久化当前阅读进度。EPUB 走 SaveEpubProgress，TXT/Markdown 走 SaveProgress。
 * 逐字搬运自 ReaderScreen，不改任何偏移计算或落库字段。
 */
@Suppress("LongParameterList")
internal fun persistCurrentProgress(
    bid: String,
    loadedBook: ReaderLoadedBook?,
    error: String?,
    pendingInitialPosition: Boolean,
    epubBook: EpubBook?,
    pagerEngineOn: Boolean,
    pagedAbsOffset: Int,
    pagedSource: PagedChapterSource?,
    chapterIndex: Int,
    epubListState: LazyListState,
    blockGlobalOffsets: List<Int>,
    chapterBase: Int,
    chapterBlocks: List<DocBlock>,
    bookIndex: BookIndex?,
    chapterStartOffsets: List<Int>,
    visiblePlainOffset: Int,
    markdownDocument: ReaderDocument?,
    txtStreamingDocument: PlainTextDocument?,
    plainContent: String,
    onAction: (ReaderAction) -> Unit,
) {
    if (bid.isBlank() || loadedBook == null || error != null || pendingInitialPosition) return
    if (epubBook != null) {
        val chapterOffset = if (pagerEngineOn && pagedAbsOffset >= 0) {
            val currentChapter = pagedSource?.chapterIndexFor(pagedAbsOffset) ?: chapterIndex
            pagedAbsOffset - (pagedSource?.chapterStartAbs(currentChapter) ?: chapterBase)
        } else {
            val itemIndex = epubListState.firstVisibleItemIndex
            val blockStart = blockGlobalOffsets.getOrElse(itemIndex) { chapterBase }
            val baseOffset = (blockStart - chapterBase).coerceAtLeast(0)
            val blockLength = (chapterBlocks.getOrNull(itemIndex) as? DocBlock.Text)
                ?.text
                ?.length
                ?: 0
            val visibleItem = epubListState.layoutInfo.visibleItemsInfo.firstOrNull()
            val fraction = if (visibleItem != null && visibleItem.size > 0) {
                epubListState.firstVisibleItemScrollOffset.toFloat() / visibleItem.size
            } else {
                0f
            }
            baseOffset + (blockLength * fraction).toInt()
        }.coerceAtLeast(0)
        val currentChapter = if (pagerEngineOn && pagedAbsOffset >= 0) {
            pagedSource?.chapterIndexFor(pagedAbsOffset) ?: chapterIndex
        } else {
            chapterIndex
        }
        val globalOffset = chapterStartOffsets.getOrElse(currentChapter) { 0 } + chapterOffset
        val totalChars = bookIndex?.totalChars?.coerceAtLeast(1) ?: 1
        val percent = (globalOffset * 100f / totalChars).coerceIn(0f, 100f)
        onAction(
            ReaderAction.SaveEpubProgress(
                bookId = bid,
                chapterIndex = currentChapter,
                percent = percent,
                offsetInChapter = chapterOffset,
            )
        )
    } else {
        val absoluteOffset = if (pagerEngineOn && pagedAbsOffset >= 0) {
            pagedAbsOffset
        } else {
            visiblePlainOffset
        }.coerceAtLeast(0)
        val totalChars = when {
            markdownDocument != null -> markdownDocument.totalChars
            txtStreamingDocument != null -> txtStreamingDocument!!.totalChars
            else -> plainContent.length
        }.coerceAtLeast(1)
        val percent = (absoluteOffset * 100f / totalChars).coerceIn(0f, 100f)
        onAction(
            ReaderAction.SaveProgress(
                ReadingProgressEntity(
                    book_id = bid,
                    progress_percent = percent,
                    completion_state = if (percent >= 99.9f) "finished" else "reading",
                    current_location_json = if (markdownDocument != null) {
                        """{"offset":$absoluteOffset,"space":"canonical"}"""
                    } else {
                        """{"offset":$absoluteOffset}"""
                    },
                    updated_at = nowIso(),
                )
            )
        )
    }
}

/** R6：进度滑块跳转（TXT 定位到百分比；EPUB 跳到对应章节；分页引擎按全书偏移精确定位）。 */
@Suppress("LongParameterList")
internal fun seekToPercent(
    p: Float,
    epubBook: EpubBook?,
    pagerEngineOn: Boolean,
    bookIndex: BookIndex?,
    txtStreamingDocument: PlainTextDocument?,
    plainContent: String,
    pagedJumpRequest: MutableState<Int?>,
    goToChapter: (Int) -> Unit,
    jumpToPlainOffset: (Int) -> Unit,
) {
    if (epubBook != null) {
        if (pagerEngineOn) {
            val total = bookIndex?.totalChars ?: 0
            if (total > 0) pagedJumpRequest.value = (p.coerceIn(0f, 100f) / 100f * total).toInt()
        } else {
            val sz = epubBook!!.chapters.size
            if (sz > 0) goToChapter((p / 100f * sz).toInt().coerceIn(0, sz - 1))
        }
    } else if (plainContent.isNotEmpty() || txtStreamingDocument != null) {
        val totalLen = if (txtStreamingDocument != null) txtStreamingDocument!!.totalChars else plainContent.length
        jumpToPlainOffset((p.coerceIn(0f, 100f) / 100f * totalLen).toInt())
    }
}

/** 章节内进度跳转：将章节内百分比转换为全书绝对偏移后定位。 */
@Suppress("LongParameterList")
internal fun seekToChapterPercent(
    p: Float,
    epubBook: EpubBook?,
    chapterStartOffsets: List<Int>,
    chapterIndex: Int,
    contentText: String,
    pagerEngineOn: Boolean,
    txtChapters: List<com.creationreadingassistant.feature.reader.doc.DocChapter>,
    txtChapterIndex: Int,
    plainContent: String,
    pagedJumpRequest: MutableState<Int?>,
    jumpToPlainOffset: (Int) -> Unit,
) {
    val clamped = p.coerceIn(0f, 100f)
    if (epubBook != null) {
        val base = chapterStartOffsets.getOrElse(chapterIndex) { 0 }
        val chLen = contentText.length.coerceAtLeast(1)
        val absOffset = base + (clamped / 100f * chLen).toInt()
        if (pagerEngineOn) {
            pagedJumpRequest.value = absOffset
        } else {
            jumpToPlainOffset(absOffset)
        }
    } else {
        val ch = txtChapters.getOrNull(txtChapterIndex)
        val base = ch?.startOffset ?: 0
        val chLen = (ch?.charCount ?: plainContent.length).coerceAtLeast(1)
        val absOffset = base + (clamped / 100f * chLen).toInt()
        jumpToPlainOffset(absOffset)
    }
}

/** 打开 TTS：从当前正文（或跨会话续读位置）开始。R1：API 33+ 运行时申请通知权限。 */
@Suppress("LongParameterList")
internal fun openTts(
    contentText: String,
    epubBook: EpubBook?,
    ttsResumeChapter: Int,
    chapterIndex: Int,
    ttsResumeOffset: Int,
    tts: TtsController,
    bookTitle: String,
    currentChapterTitle: String,
    context: Context,
    onShowTtsChange: (Boolean) -> Unit,
    showNotice: (String) -> Unit,
) {
    if (contentText.isBlank()) {
        showNotice("当前没有可朗读的文字。")
        return
    }
    // R1：API 33+ 运行时申请通知权限，否则锁屏媒体控制无法显示
    if (Build.VERSION.SDK_INT >= 33) {
        val act = context as? Activity
        if (act != null && androidx.core.content.ContextCompat.checkSelfPermission(act, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(act, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
    val resumeAt = if (epubBook == null || ttsResumeChapter == chapterIndex) ttsResumeOffset else 0
    tts.play(contentText, bookTitle, currentChapterTitle.ifBlank { "正文" }, resumeAt)
    onShowTtsChange(true)
}

/** 处理顶栏/底栏 chrome 动作。逐字搬运自 ReaderScreen 的 when 分支。 */
@Suppress("LongParameterList")
internal fun handleChromeAction(
    action: ReaderChromeAction,
    showTts: Boolean,
    tts: TtsController,
    autoPagingActiveState: MutableState<Boolean>,
    autoPagingSupported: Boolean,
    onShowTtsChange: (Boolean) -> Unit,
    onControlsVisibleChange: (Boolean) -> Unit,
    onSelectedTextChange: (String) -> Unit,
    onSheetChange: (ReaderSheet) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    openTts: () -> Unit,
    showNotice: (String) -> Unit,
) {
    when (action) {
        ReaderChromeAction.Back -> onBack()
        ReaderChromeAction.ToggleTts -> {
            if (showTts) {
                tts.stop()
                onShowTtsChange(false)
            } else {
                autoPagingActiveState.value = false
                openTts()
            }
        }
        is ReaderChromeAction.OpenSheet -> {
            if (action.sheet == ReaderSheet.SEARCH) onSearchQueryChange("")
            onSheetChange(action.sheet)
        }
        ReaderChromeAction.ToggleAutoPaging -> {
            if (autoPagingActiveState.value) {
                autoPagingActiveState.value = false
                onControlsVisibleChange(true)
            } else if (!autoPagingSupported) {
                showNotice("左右翻页模式需先开启新分页引擎")
            } else {
                tts.stop()
                onShowTtsChange(false)
                onSelectedTextChange("")
                autoPagingActiveState.value = true
                onControlsVisibleChange(false)
            }
        }
        ReaderChromeAction.HideControls -> onControlsVisibleChange(false)
    }
}
