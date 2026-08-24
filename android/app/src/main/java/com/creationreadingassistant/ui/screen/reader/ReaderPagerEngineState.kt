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
import com.creationreadingassistant.feature.reader.pager.PagedReplacementAvailability
import com.creationreadingassistant.feature.reader.pager.PagerHealthStore
import com.creationreadingassistant.feature.reader.pager.TxtChapterSource
import com.creationreadingassistant.feature.reader.pager.preparePagedReplacement
import com.creationreadingassistant.feature.reader.rules.ReplaceProfile
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
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
    val replacementAvailability: PagedReplacementAvailability,
    val replaceProjectionNotice: MutableState<String?>,
)

/**
 * 替换管理入口必须与实际渲染路径一致：分页引擎关闭时，即使为了预热或目录计算已经
 * 构造了 paged source，也不能把规则入口暴露给滚动/legacy 正文。
 */
internal fun effectiveReplacementAvailability(
    pagerEngineOn: Boolean,
    prepared: PagedReplacementAvailability?,
): PagedReplacementAvailability = when {
    !pagerEngineOn -> PagedReplacementAvailability.PAGER_ENGINE_DISABLED
    prepared != null -> prepared
    else -> PagedReplacementAvailability.SOURCE_UNAVAILABLE
}

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
    bookId: String,
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
    replaceRules: List<ReplaceRule>,
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
    val replaceProfileKey = remember(bookId, replaceRules) {
        ReplaceProfile.key(bookId, replaceRules)
    }
    val replaceProjectionNotice = remember(bookId, replaceProfileKey) {
        mutableStateOf<String?>(null)
    }

    // EPUB 文档缓存与流式 TXT 临时文件均由 ReaderViewModel 独占并释放。
    // 替换规则只在 source 明确保证「精确坐标 + 完整章节作用域」时接入；规则 key
    // 参与 remember，启停、增删、改序后会重建 source 与 controller。
    val preparedPagedSource = remember(
        epubBook,
        epubDocument,
        bookIndex,
        plainContent,
        txtChapters,
        txtStreamingDocument,
        readingUnits,
        markdownDocument,
        bookId,
        replaceProfileKey,
    ) {
        val index = bookIndex
        val document = epubDocument
        val streamingDoc = txtStreamingDocument
        val baseSource = when {
            epubBook != null && index != null && document != null ->
                EpubChapterSource(
                    titles = index.chapterTitles,
                    chapterStartOffsets = index.chapterStartOffsets,
                    totalChars = index.totalChars,
                    loadBlocks = document::blocks,
                )

            markdownDocument != null ->
                MarkdownChapterSource(markdownDocument)

            streamingDoc != null && txtChapters.isNotEmpty() ->
                TxtChapterSource(streamingDoc)

            plainContent.isNotBlank() && txtChapters.isNotEmpty() ->
                TxtChapterSource(plainContent, txtChapters)

            else -> null
        }
        baseSource?.let { source ->
            preparePagedReplacement(
                delegate = source,
                bookId = bookId,
                rules = replaceRules,
                onUnsupportedTooLarge = {
                    replaceProjectionNotice.value = "当前章节过大，已保留原文，暂不执行替换净化。"
                },
            )
        }
    }
    val pagedSource = preparedPagedSource?.source
    // 没有 source 与“source 存在但没有生效规则”是两个不同状态：前者必须隐藏
    // 替换管理入口，后者要保留空列表和“新增规则”入口。
    val replacementAvailability = effectiveReplacementAvailability(
        pagerEngineOn = pagerEngineOn,
        prepared = preparedPagedSource?.availability,
    )

    return PagerEngineState(
        pagerEngineOn = pagerEngineOn,
        pagedJumpRequest = pagedJumpRequest,
        pagedHardwareTurnRequest = pagedHardwareTurnRequest,
        pagedAbsOffsetState = pagedAbsOffsetState,
        pagedPercentState = pagedPercentState,
        txtChapters = txtChapters,
        txtRulePreviews = rulePreviews,
        pagedSource = pagedSource,
        replacementAvailability = replacementAvailability,
        replaceProjectionNotice = replaceProjectionNotice,
    )
}
