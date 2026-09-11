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
import com.creationreadingassistant.feature.reader.pager.PreparedPagedReplacement
import com.creationreadingassistant.feature.reader.pager.ScrollingTxtChapterSource
import com.creationreadingassistant.feature.reader.pager.TxtChapterSource
import com.creationreadingassistant.feature.reader.pager.preparePagedReplacement
import com.creationreadingassistant.feature.reader.rules.BoundedReplaceResult
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
    /**
     * 滚动 TXT 正文实际消费的 source。它与 [replacementAvailability] 由同一份
     * [PreparedPagedReplacement] 产生，禁止正文分支另行创建第二份投影源。
     */
    val scrollProjectedSource: PagedChapterSource?,
    val replacementAvailability: PagedReplacementAvailability,
    val replaceProjectionNotice: MutableState<String?>,
)

/**
 * 替换管理入口必须与实际渲染路径一致。传统滚动正文只有在完整逻辑章投影已接入时
 * 才可使用规则；其余 legacy 路径仍不得因为预热 source 而误开放入口。
 */
internal fun effectiveReplacementAvailability(
    pagerEngineOn: Boolean,
    prepared: PagedReplacementAvailability?,
    scrollProjectionOn: Boolean = false,
): PagedReplacementAvailability = when {
    !pagerEngineOn && !scrollProjectionOn -> PagedReplacementAvailability.PAGER_ENGINE_DISABLED
    prepared != null -> prepared
    else -> PagedReplacementAvailability.SOURCE_UNAVAILABLE
}

/**
 * 组装滚动 TXT 的唯一正文 source。规则入口的能力裁决与 [ReaderContentHostPlainTextBranch]
 * 的正文显示必须复用此结果：若两边各自 prepare，会留下“菜单可用而正文仍是原文”的错误窗口。
 */
internal fun prepareScrollTxtReplacement(
    bookId: String,
    streamingDocument: PlainTextDocument?,
    plainContent: String,
    readingUnits: List<ReadingUnit>,
    rules: List<ReplaceRule>,
    onUnsupportedTooLarge: (BoundedReplaceResult.UnsupportedTooLarge) -> Unit = {},
): PreparedPagedReplacement? {
    if (bookId.isBlank() || readingUnits.isEmpty()) return null
    val delegate = when {
        streamingDocument != null -> ScrollingTxtChapterSource.fromDocument(streamingDocument)
        plainContent.isNotEmpty() -> ScrollingTxtChapterSource.fromText(plainContent, readingUnits)
        else -> return null
    }
    // 超限降级在 source 层裁决（R1-S1）：无目录大书整本坍缩为单一作用域时，
    // preparePagedReplacement 直接给出 ALL_SCOPES_OVERSIZED（正文必然原文，绝不标 APPLIED）；
    // 只有部分作用域超限时才是 PARTIALLY_APPLIED，逐章回退仍经 onUnsupportedTooLarge 提示。
    return preparePagedReplacement(delegate, bookId, rules, onUnsupportedTooLarge = onUnsupportedTooLarge)
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
    /**
     * TXT 章节识别结果（R1-S1 上提至 [rememberReaderDerivedState]）：
     * 小文件滚动 readingUnits 必须按真实逻辑章对齐切块，章节边界是切块输入，
     * 必须先于 units 就绪；本函数只读消费，不再自行检测。
     */
    txtChapters: List<DocChapter>,
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
    // 滚动模式不能复用 pagedSource：后者的 index 是逻辑章节，而滚动正文按
    // ReadingUnit 索引。两者必须各有 source，但“能力裁决”和“实际渲染”只能
    // 消费同一份滚动 source，避免 UI 声称可用而正文使用另一条原文链。
    val scrollPreparedSource = remember(
        pagerEngineOn,
        epubBook,
        markdownDocument,
        bookId,
        txtStreamingDocument,
        plainContent,
        txtChapters,
        readingUnits,
        replaceProfileKey,
    ) {
        // R1-S1：去掉 txtChapters.isEmpty() 门。章节未识别不是「替换不可用」的依据：
        // 无章小书以整书为完整作用域（fromText 的 chapterRanges[0] 覆盖全文，真实完整），
        // 整书作用域超限时 preparePagedReplacement 给出 ALL_SCOPES_OVERSIZED——正文必然原文，
        // 不再冒充 APPLIED；只有部分作用域超限才是 PARTIALLY_APPLIED，逐章回退仍提示「已保留原文」。
        // 降级事实在 source 层诚实表达，不在组装层静默拒绝。
        if (pagerEngineOn || epubBook != null || markdownDocument != null) {
            null
        } else {
            prepareScrollTxtReplacement(
                bookId = bookId,
                streamingDocument = txtStreamingDocument,
                plainContent = plainContent,
                readingUnits = readingUnits,
                rules = replaceRules,
                onUnsupportedTooLarge = {
                    replaceProjectionNotice.value = "当前章节过大，已保留原文，暂不执行替换净化。"
                },
            )
        }
    }
    val activePreparedSource = if (pagerEngineOn) preparedPagedSource else scrollPreparedSource
    // 没有 source 与“source 存在但没有生效规则”是两个不同状态：前者必须隐藏
    // 替换管理入口，后者要保留空列表和“新增规则”入口。
    val replacementAvailability = effectiveReplacementAvailability(
        pagerEngineOn = pagerEngineOn,
        prepared = activePreparedSource?.availability,
        scrollProjectionOn = !pagerEngineOn && scrollPreparedSource != null,
    )
    // 滚动 TXT 的替换此前出现过“规则已保存、预览命中、正文仍原文”的跨层故障。
    // 仅 Debug logcat 记录无正文/无规则内容的边界信息，供真机一次定位 source
    // 是未组装、被回退，还是进入单元投影后失效；不得把 profile/bookId/正文写入日志。
    LaunchedEffect(scrollPreparedSource, replacementAvailability) {
        if (!pagerEngineOn && epubBook == null && markdownDocument == null) {
            AppLog.debug(
                "ScrollReplaceTrace",
                "assemble rules=${replaceRules.size}, units=${readingUnits.size}, " +
                    "tocChapters=${txtChapters.size}, source=" +
                    (scrollPreparedSource?.source?.javaClass?.simpleName ?: "none") +
                    ", availability=$replacementAvailability",
            )
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
        scrollProjectedSource = scrollPreparedSource?.source,
        replacementAvailability = replacementAvailability,
        replaceProjectionNotice = replaceProjectionNotice,
    )
}
