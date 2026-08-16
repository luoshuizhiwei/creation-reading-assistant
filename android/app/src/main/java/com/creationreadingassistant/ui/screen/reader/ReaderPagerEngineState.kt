package com.creationreadingassistant.ui.screen.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.feature.reader.doc.TxtTocProfile
import com.creationreadingassistant.feature.reader.pager.EpubChapterSource
import com.creationreadingassistant.feature.reader.pager.MarkdownChapterSource
import com.creationreadingassistant.feature.reader.pager.PagedChapterSource
import com.creationreadingassistant.feature.reader.pager.PagerHealthStore
import com.creationreadingassistant.feature.reader.pager.TxtChapterSource
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Phase 6 结构拆分：[rememberPagerEngineState] 的返回值。
 *
 * 仅暴露下游 [com.creationreadingassistant.ui.screen.ReaderScreen] 仍需消费的派生值 / 状态持有者；
 * 中间计算（configuredPagerMode 仅用于推导 pagerEngineOn）保留在 [rememberPagerEngineState] 内部。
 */
internal data class PagerEngineState(
    val pagerEngineOn: Boolean,
    val pagedJumpRequest: MutableState<Int?>,
    val pagedHardwareTurnRequest: MutableState<Int?>,
    val pagedAbsOffsetState: MutableIntState,
    val pagedPercentState: MutableFloatState,
    val txtChapters: List<DocChapter>,
    val txtRulePreviews: Map<String, List<TxtChapterDetector.Chapter>>,
    val pagedSource: PagedChapterSource?,
)

/**
 * Phase 6 结构拆分：从 [com.creationreadingassistant.ui.screen.ReaderScreen] 抽出的「分页引擎状态」
 * 逻辑（原自研分页引擎块）。
 *
 * 包含：configuredPagerMode / pagerEngineOn / DisposableEffect(pagerEngineOn) /
 * LaunchedEffect(pagerEngineOn, isLoading, error) / pagedJumpRequest / pagedHardwareTurnRequest /
 * pagedAbsOffsetState / pagedPercentState / txtChapters / txtRulePreviews / pagedSource。
 *
 * ## 行为保真
 *
 * 每个 `remember` / `DisposableEffect` / `LaunchedEffect` / `produceState` 逐字搬运自 ReaderScreen，
 * 不改任何 key、防抖参数与帧参数。本函数是 [com.creationreadingassistant.ui.screen.ReaderScreen]
 * 组合树内的子组合，effect 组合位置与原文一致，生命周期（含 DisposableEffect 的 installCrashGuard
 * 安装/释放时机）完全等价。
 *
 * ## State-holder 传参
 *
 * 可写状态以 State-holder 形式传出（pagedAbsOffsetState / pagedPercentState / pagedJumpRequest /
 * pagedHardwareTurnRequest），下游用 `var x by pagerEngine.xxxState` 还原原 `var by` delegate 语义，
 * 读写零改动；effect 体内被读 / 被写的可变状态同样走 `.value`。
 */
@Suppress("LongParameterList")
@Composable
internal fun rememberPagerEngineState(
    epubBook: EpubBook?,
    epubDocument: ReaderDocument?,
    markdownDocument: MarkdownDocument?,
    bookIndex: BookIndex?,
    readerSettings: ReaderSettings,
    pagerHealth: PagerHealthStore,
    isLoading: Boolean,
    error: String?,
    textContent: ReaderLoadedContent.Text?,
    plainContent: String,
    tocProfile: TxtTocProfile,
    txtStreamingDocument: PlainTextDocument?,
    readingUnits: List<ReadingUnit>,
): PagerEngineState {
    // ── 自研分页引擎（pagerEngineMode=on 时 TXT/EPUB 都走真正的章内逐页翻页）──
    val configuredPagerMode = if (epubBook != null) {
        readerSettings.epubPagerEngineMode
    } else {
        readerSettings.pagerEngineMode
    }
    val pagerEngineOn = readerSettings.readerMode == "paged" && when (configuredPagerMode) {
        "on" -> true
        "auto" -> !pagerHealth.isAutoDisabled
        else -> false
    }
    DisposableEffect(pagerEngineOn) {
        val guard = if (pagerEngineOn) pagerHealth.installCrashGuard() else null
        onDispose { guard?.close() }
    }
    LaunchedEffect(pagerEngineOn, isLoading, error) {
        if (pagerEngineOn && !isLoading && error == null) {
            delay(60_000)
            pagerHealth.clearAfterStableRead()
        }
    }
    // 外部跳转请求（进度条 / 目录 / 高亮定位），宿主消费后置回 null
    val pagedJumpRequest = remember { mutableStateOf<Int?>(null) }
    val pagedHardwareTurnRequest = remember { mutableStateOf<Int?>(null) }
    // 分页引擎上报的当前位置（全书字符偏移 + 百分比），-1 表示尚未上报
    val pagedAbsOffsetState = remember { mutableIntStateOf(-1) }
    val pagedPercentState = remember { mutableFloatStateOf(0f) }

    // TXT 章节识别：此前 TXT 完全没有章节概念，目录永远是「暂未识别到目录」。
    // P0 优化：优先使用 ReaderDocumentLoader 在 IO 线程预检测的结果，避免阻塞主线程。
    // P1-A：身份改为 TxtTocProfile.key —— 规则集合 / 顺序 / 内容变化后 key 变化，
    // 旧预检测不匹配即回退同步检测（极少触发）。
    val txtChapters = remember(plainContent, tocProfile.key, txtStreamingDocument, textContent) {
        val streamDoc = txtStreamingDocument
        if (epubBook == null && streamDoc != null) {
            streamDoc.chapters
        } else if (epubBook == null && plainContent.isNotBlank()) {
            val preDetected = textContent?.preDetectedChapters
            val preRule = textContent?.preDetectedRuleId
            if (!preDetected.isNullOrEmpty() && preRule == tocProfile.key) {
                // 快速路径：使用 IO 线程预检测结果，不阻塞主线程
                preDetected
            } else {
                // Fallback：规则切换或预检测缺失，同步检测（极少触发）
                AppLog.debug("TxtPerfSubTrace", "TxtChapterDetect: fallback=true, key=${tocProfile.key}, preRule=$preRule")
                PlainTextDocument(plainContent, tocProfile).chapters
            }
        } else {
            emptyList()
        }
    }
    val rulePreviews by produceState(
        initialValue = emptyMap<String, List<TxtChapterDetector.Chapter>>(),
        plainContent,
        epubBook,
    ) {
        value = if (plainContent.isBlank() || epubBook != null) {
            emptyMap()
        } else {
            withContext(Dispatchers.Default) {
                TxtChapterDetector.rules.associate { rule ->
                    rule.id to TxtChapterDetector.detect(plainContent, rule.id)
                }
            }
        }
    }
    // EPUB 文档缓存与流式 TXT 临时文件均由 ReaderViewModel 独占并释放。
    val pagedSource: PagedChapterSource? = remember(
        epubBook,
        epubDocument,
        bookIndex,
        plainContent,
        txtChapters,
        txtStreamingDocument,
        readingUnits,
        markdownDocument,
    ) {
        val index = bookIndex
        val document = epubDocument
        when {
            epubBook != null && index != null && document != null ->
                EpubChapterSource(
                    titles = index.chapterTitles,
                    chapterStartOffsets = index.chapterStartOffsets,
                    totalChars = index.totalChars,
                    loadBlocks = document::blocks,
                )

            markdownDocument != null ->
                MarkdownChapterSource(markdownDocument)

            txtStreamingDocument != null && txtChapters.isNotEmpty() ->
                TxtChapterSource(txtStreamingDocument!!)

            plainContent.isNotBlank() && txtChapters.isNotEmpty() ->
                TxtChapterSource(plainContent, txtChapters)

            else -> null
        }
    }

    return PagerEngineState(
        pagerEngineOn = pagerEngineOn,
        pagedJumpRequest = pagedJumpRequest,
        pagedHardwareTurnRequest = pagedHardwareTurnRequest,
        pagedAbsOffsetState = pagedAbsOffsetState,
        pagedPercentState = pagedPercentState,
        txtChapters = txtChapters,
        txtRulePreviews = rulePreviews,
        pagedSource = pagedSource,
    )
}
