package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.ReadingUnit

/**
 * 搜索命中导航执行端口（深模块 seam）：执行器只依赖这个端口与 [SearchHitNavigator]
 * 会话，不接触任何 Compose 状态类型，因此可纯 JVM 测试（记录桩替代真实阅读器）。
 *
 * 端口操作与阅读器状态一一对应：
 * - [bookKey]：当前书籍 key，跨书迟到 target 的 stale guard；
 * - [currentChapterIndex]：block 异步加载后复核「用户未手动离章」；
 * - [switchToChapter]：章节切换（分页/滚动由阅读器内部处理）；
 * - [jumpToPagedOffset]：分页引擎按全书字符偏移精确跳转；
 * - [scrollToUnit]：TXT 滚动定位到命中所在 ReadingUnit；
 * - [publishScrollFocus]：EPUB/Markdown 滚动聚焦 —— 发布带身份的一次性聚焦请求
 *   （stale guard 通过后由执行器发布，ReaderContentHost 匹配书/章后消费并 ack）；
 * - [loadChapterBlocks]：滚动定位所需的章节块加载（阻塞、不可中断）。
 */
internal interface SearchHitNavigationSink {
    val bookKey: String
    val currentChapterIndex: Int

    fun switchToChapter(chapterIndex: Int)

    fun jumpToPagedOffset(absoluteOffset: Int)

    suspend fun scrollToUnit(unitIndex: Int)

    fun publishScrollFocus(request: SearchScrollFocusRequest)

    suspend fun loadChapterBlocks(chapterIndex: Int): List<DocBlock>
}

/**
 * 搜索命中导航执行器：消费 [SearchHitNavigator.currentTarget]，把统一目标落到阅读页。
 *
 * 调用方只理解两个稳定接口（[SearchHitNavigator] 产出目标、[SearchHitNavigationSink]
 * 执行动作），本实现集中全部导航行为：
 * - 章文档（EPUB / Markdown 同一 CHAPTERED 分支）先切章，分页再以全书偏移精确跳转，
 *   滚动加载目标章块后在 stale guard 通过时发布带身份的一次性
 *   [SearchScrollFocusRequest]（EPUB 按 DocBlock 文本块，Markdown 按 parser
 *   canonical mapping 定位渲染单元；ReaderContentHost 仅在当前书/章匹配时消费滚动，
 *   消费后 ack 清除 —— 手动切章/切书的旧请求被丢弃，不得劫持导航）；
 * - TXT 分页走 paged jump；TXT 滚动 scrollToItem 到命中所在 ReadingUnit；
 * - stale guard：跨书迟到 target 一律丢弃；滚动取块不可中断，返回后必须复核
 *   目标仍是当前选中（同章连点 / 换 query 由 currentTarget 变化暴露）且用户未手动离章。
 *
 * 不关闭搜索面板、不写数据库、不污染用户选区；临时高亮由 ReaderScreen 从
 * currentTarget.absoluteRange 派生（分页 underlay / 滚动 AnnotatedString）。
 */
internal class SearchHitNavigationExecutor(
    private val session: SearchHitNavigator,
    private val sink: SearchHitNavigationSink,
    /** 章文档（EPUB/Markdown）还是纯文本 TXT。 */
    private val chaptered: Boolean,
    /** 分页引擎开启（否则为滚动模式）。 */
    private val pagerEngineOn: Boolean,
    private val chapterStartOffsets: List<Int>,
    private val readingUnits: List<ReadingUnit>,
    /** Markdown 小文件整本解析：canonicalRange 为全书全局坐标（否则为章内局部）。 */
    private val markdownBlocksGlobal: Boolean = false,
) {
    /** 当前选中的搜索命中目标；effect 以它为 LaunchedEffect key（同一命中只执行一次）。 */
    val currentTarget: SearchHitTarget?
        get() = session.currentTarget

    /** 把当前选中的命中导航到阅读页；无选中或 stale 时安全 no-op。 */
    suspend fun navigateToCurrentTarget() {
        val target = session.currentTarget ?: return
        navigateTo(target)
    }

    /** 导航指定目标（含跨书 stale guard）；测试与 [navigateToCurrentTarget] 共用同一分派。 */
    suspend fun navigateTo(target: SearchHitTarget) {
        // 跨书迟到 target（旧书 session 被新书消费）一律丢弃
        if (target.bookKey != sink.bookKey) return
        when {
            chaptered -> navigateChaptered(target)
            pagerEngineOn -> sink.jumpToPagedOffset(target.absoluteRange.first)
            readingUnits.isNotEmpty() -> sink.scrollToUnit(unitIndexForOffset(readingUnits, target.absoluteRange.first))
        }
    }

    private suspend fun navigateChaptered(target: SearchHitTarget) {
        val ci = target.chapterIndex.coerceAtLeast(0)
        sink.switchToChapter(ci)
        if (pagerEngineOn) {
            sink.jumpToPagedOffset(target.absoluteRange.first)
            return
        }
        val blocks = sink.loadChapterBlocks(ci)
        // 旧请求不得覆盖新 hit：阻塞取块不可中断，返回后必须复核
        // 目标仍是当前选中（同章连点/换 query 会由 currentTarget 变化暴露），
        // 且用户未手动离开目标章。
        if (sink.currentChapterIndex != ci || session.currentTarget?.resultIndex != target.resultIndex) {
            return
        }
        val inChapter = target.absoluteRange.first - chapterStartOffsets.getOrElse(ci) { 0 }
        val markdownBlock = blocks.filterIsInstance<DocBlock.Markdown>().firstOrNull()
        val blockIndex = if (markdownBlock != null) {
            // P1-A：Markdown 渲染单元索引与 LazyColumn 项顺序一致，由 parser
            // canonical mapping 定位包含命中的渲染块（非首章经 chapterBase 换算）。
            markdownRenderUnitIndexForChapterOffset(
                chapter = markdownBlock.chapter,
                inChapter = inChapter,
                chapterBase = chapterStartOffsets.getOrElse(ci) { 0 },
                blocksGlobal = markdownBlocksGlobal,
            )
        } else {
            blockIndexForChapterOffset(blocks, inChapter)
        }
        // 发布带身份的一次性聚焦请求：跨书/跨章 stale 由 ReaderContentHost 消费侧
        // 以 bookKey + chapterIndex 匹配丢弃，滚动后 ack 清除，不再复用裸 Int?。
        sink.publishScrollFocus(
            SearchScrollFocusRequest(
                bookKey = target.bookKey,
                chapterIndex = ci,
                renderUnitIndex = blockIndex,
                resultIndex = target.resultIndex,
            ),
        )
    }
}

/** Compose 侧 [SearchHitNavigationSink] 实现：把端口操作落到阅读器真实状态。 */
private class ComposeSearchHitNavigationSink(
    private val bookId: String,
    private val chapterIndexState: MutableIntState,
    private val pagedJumpRequest: MutableState<Int?>,
    private val plainListState: LazyListState,
    private val searchScrollFocusRequestState: MutableState<SearchScrollFocusRequest?>,
    private val goToChapter: (Int) -> Unit,
    private val onLoadChapterBlocks: suspend (String, Int) -> List<DocBlock>,
) : SearchHitNavigationSink {
    override val bookKey: String get() = bookId
    override val currentChapterIndex: Int get() = chapterIndexState.intValue
    override fun switchToChapter(chapterIndex: Int) = goToChapter(chapterIndex)
    override fun jumpToPagedOffset(absoluteOffset: Int) {
        pagedJumpRequest.value = absoluteOffset
    }

    override suspend fun scrollToUnit(unitIndex: Int) = plainListState.scrollToItem(unitIndex)

    override fun publishScrollFocus(request: SearchScrollFocusRequest) {
        searchScrollFocusRequestState.value = request
    }

    override suspend fun loadChapterBlocks(chapterIndex: Int): List<DocBlock> =
        onLoadChapterBlocks(bookId, chapterIndex)
}

/**
 * 装配搜索命中导航模块：把当前文档上下文、渲染模式与阅读器状态一次性接入
 * [SearchHitNavigationExecutor]；之后调用方只需 [SearchHitNavigationEffect]。
 *
 * [chaptered] = EPUB 或 Markdown 文档（章文档分支），否则为纯文本 TXT。
 */
@Suppress("LongParameterList")
internal fun buildSearchHitNavigation(
    session: SearchHitNavigator,
    bid: String,
    chaptered: Boolean,
    pagerEngineOn: Boolean,
    chapterStartOffsets: List<Int>,
    readingUnits: List<ReadingUnit>,
    pagedJumpRequest: MutableState<Int?>,
    chapterIndexState: MutableIntState,
    plainListState: LazyListState,
    searchScrollFocusRequestState: MutableState<SearchScrollFocusRequest?>,
    markdownBlocksGlobal: Boolean,
    goToChapter: (Int) -> Unit,
    onLoadChapterBlocks: suspend (String, Int) -> List<DocBlock>,
): SearchHitNavigationExecutor = SearchHitNavigationExecutor(
    session = session,
    sink = ComposeSearchHitNavigationSink(
        bookId = bid,
        chapterIndexState = chapterIndexState,
        pagedJumpRequest = pagedJumpRequest,
        plainListState = plainListState,
        searchScrollFocusRequestState = searchScrollFocusRequestState,
        goToChapter = goToChapter,
        onLoadChapterBlocks = onLoadChapterBlocks,
    ),
    chaptered = chaptered,
    pagerEngineOn = pagerEngineOn,
    chapterStartOffsets = chapterStartOffsets,
    readingUnits = readingUnits,
    markdownBlocksGlobal = markdownBlocksGlobal,
)

/**
 * 消费 [BookSearchSession.currentTarget] 的导航 effect：搜索面板内点结果 / 上一处 / 下一处。
 * 所有分派（章切换、paged jump、scroll block 定位、stale guard、TXT/EPUB/Markdown）
 * 集中在 [SearchHitNavigationExecutor]；本 effect 只负责以 target 为 key 触发一次执行。
 */
@Composable
internal fun SearchHitNavigationEffect(searchNav: SearchHitNavigationExecutor) {
    val target = searchNav.currentTarget
    LaunchedEffect(target) {
        searchNav.navigateToCurrentTarget()
    }
}
