@file:OptIn(ExperimentalFoundationApi::class)

package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.pager.PagedChapterSource
import com.creationreadingassistant.feature.reader.pager.PageIndexStore
import com.creationreadingassistant.feature.reader.pager.ReaderPageIndexManager
import com.creationreadingassistant.ui.screen.reader.tts.TtsController
import com.creationreadingassistant.ui.theme.ReaderPaperPalette

/**
 * 阅读设置与纸张调色板（B1 状态袋分组：设置域）。
 */
internal data class ReaderContentSettings(
    val readerSettings: ReaderSettings,
    val paper: ReaderPaperPalette,
    val paperFg: Color,
    val sentenceHighlightBg: Color,
    /** 统一搜索命中高亮底色（P1-B）：TXT/EPUB/Markdown 滚动与分页共用，随纸自适应。 */
    val searchHighlightBg: Color,
)

/**
 * 当前选区相关状态（B1 状态袋分组：选区域）。
 */
internal data class ReaderSelectionState(
    val selectedText: String,
    val selectedGlobalOffset: Int,
    val selectedRangeStart: Int,
)

/**
 * 分页引擎 / 自动翻页 / 阅读位置恢复（B1 状态袋分组：分页域）。
 */
internal data class ReaderPagingState(
    val pagerEngineOn: Boolean,
    val pagedSource: PagedChapterSource?,
    val pagedAbsOffset: Int,
    val pagedPercent: Float,
    val pendingInitialPosition: Boolean,
    val savedEpubOffsetInChapter: Int,
    val visiblePlainOffset: Int,
    val savedPlainOffset: Int,
    val savedPlainPercent: Float,
    val autoPagingActive: Boolean,
    val autoPagingPaused: Boolean,
    val pagedJumpRequest: MutableState<Int?>,
    val pagedHardwareTurnRequest: MutableState<Int?>,
    val pageIndexStore: PageIndexStore,
    val pageIndexManager: ReaderPageIndexManager,
)

/**
 * 文档内容 / 章节渲染 / TTS 朗读状态（B1 状态袋分组：内容源域）。
 */
internal data class ReaderContentSourceState(
    val bid: String,
    /** TXT 目录身份（TxtTocProfile.key）：规则变化后分页索引缓存键随之失效。 */
    val txtTocProfileKey: String,
    val bookTitle: String,
    val chapterStartOffsets: List<Int>,
    val txtStreamingDocument: PlainTextDocument?,
    val plainContent: String,
    val epubBook: EpubBook?,
    val markdownDocument: ReaderDocument?,
    val chapterIndex: Int,
    val txtChapterIndex: Int,
    val isChapterLoading: Boolean,
    val chapterBlocks: List<DocBlock>,
    val epubListState: LazyListState,
    val plainListState: LazyListState,
    val chapterFade: Animatable<Float, AnimationVector1D>,
    val blockGlobalOffsets: List<Int>,
    val chapterBase: Int,
    val ttsSentenceRangeInChapter: Pair<Int, Int>?,
    val focusBlockIndex: Int?,
    val epubBringRequester: BringIntoViewRequester,
    val readingUnits: List<ReadingUnit>,
    val isTxt: Boolean,
    val showTts: Boolean,
    val tts: TtsController,
    val highlights: List<HighlightEntity>,
    /** 搜索命中临时高亮（全书字符区间，含首不含尾）；null = 无当前命中。 */
    val searchHitRangeAbs: Pair<Int, Int>?,
    /**
     * 搜索滚动聚焦的一次性请求（P1 修复）：带书/章/渲染单元/命中身份，
     * 仅当前书/章匹配时消费并滚动，消费后由 [ReaderContentHostCallbacks]
     * 的 onSearchScrollFocusRequestConsumed ack 清除；手动切章/切书的旧请求
     * 身份不匹配即丢弃，不得劫持导航。
     */
    val searchScrollFocusRequest: SearchScrollFocusRequest?,
)

/**
 * 正文内容宿主所需的只读展示数据。从 [com.creationreadingassistant.ui.screen.ReaderScreen] 主函数
 * 局部状态中提取，仅承载「读」数据；任何对主函数可变状态的写入都通过
 * [ReaderContentHostCallbacks] 回调上抛。
 *
 * B1 状态袋瘦身：原 48 个平铺字段按域分组为 4 个子对象（[settings] 设置+纸张 /
 * [selection] 选区 / [paging] 分页与位置 / [source] 内容源与渲染），纯结构搬运。
 *
 * 分页引擎、EPUB/Markdown/TXT 分支共用同一份 state；分支选择由 [ReaderPagingState.pagerEngineOn] /
 * [ReaderContentSourceState.epubBook] / [ReaderContentSourceState.markdownDocument] 决定，
 * 与 ReaderScreen 主函数 `when` 块的分支条件 1:1 对应。
 */
internal data class ReaderContentHostState(
    val settings: ReaderContentSettings,
    val selection: ReaderSelectionState,
    val paging: ReaderPagingState,
    val source: ReaderContentSourceState,
)

/**
 * 正文内容宿主所需的回调集合。每个回调对应一次「主函数可变状态写入」或「主函数局部副作用」，
 * 复杂逻辑（goToChapter / showNotice / persistCurrentProgress）仍留在 ReaderScreen 主函数中定义，
 * 本层只暴露触发入口，保持为纯渲染层。
 */
internal data class ReaderContentHostCallbacks(
    val onPagedPositionChanged: (offset: Int, percent: Float, chapterToGo: Int?) -> Unit,
    val onToggleControls: () -> Unit,
    val onHideControls: () -> Unit,
    val onSelect: (text: String, globalOffset: Int, rangeStart: Int) -> Unit,
    val onAutoPagingFinished: () -> Unit,
    val onStopAutoPaging: () -> Unit,
    val onGoToChapter: (chapterIndex: Int) -> Unit,
    /** ack：ReaderContentHost 消费（或丢弃）搜索滚动聚焦请求后清除持久状态。 */
    val onSearchScrollFocusRequestConsumed: () -> Unit,
)

/**
 * 正文内容宿主：按文档类型/分页模式分发到 PagedReaderHost / PagedEpubView / LazyColumn / BasicTextField。
 *
 * 从 ReaderScreen.kt 拆出，纯结构搬运，不改渲染语义。所有分支共用同一个 [BoxScope]-less
 * Composable（由 ReaderScreen 的 `Box` 容器直接调用），不自行创建 Scaffold 或 Box。
 *
 * 分支与原 `when` 块 1:1 对应，各分支渲染体已按域抽到独立文件（纯结构搬运）：
 * 1. pagerEngineOn → [ReaderContentHostPagedBranch]（试验分页引擎，TXT/EPUB 共用）
 * 2. epubBook != null → [ReaderContentHostEpubBranch]（PagedEpubView 翻页或 LazyColumn 滚动）
 * 3. markdownDocument != null → [ReaderContentHostMarkdownBranch]（RenderMarkdownChapter 滚动）
 * 4. else → [ReaderContentHostPlainTextBranch]（BasicTextField，TXT 流式/纯文本）
 */
@Composable
internal fun ReaderContentHost(
    state: ReaderContentHostState,
    callbacks: ReaderContentHostCallbacks,
) {
    val paging = state.paging
    val s = state.source
    // 自定义正文字体（空 = 系统字体），非分页路径共用。
    val readerFontFamily = rememberReaderFontFamily(state.settings.readerSettings.customFontPath)
    when {
        paging.pagerEngineOn && paging.pagedSource != null ->
            ReaderContentHostPagedBranch(state = state, callbacks = callbacks)

        s.epubBook != null ->
            ReaderContentHostEpubBranch(
                state = state,
                callbacks = callbacks,
                readerFontFamily = readerFontFamily,
            )

        s.markdownDocument != null ->
            ReaderContentHostMarkdownBranch(
                state = state,
                callbacks = callbacks,
                readerFontFamily = readerFontFamily,
            )

        else ->
            ReaderContentHostPlainTextBranch(
                state = state,
                callbacks = callbacks,
                readerFontFamily = readerFontFamily,
            )
    }
}
