package com.creationreadingassistant.ui.screen.reader

/**
 * 带身份的一次性搜索滚动聚焦请求（P1 修复）。
 *
 * 旧实现用裸 `navFocusBlockIndexState: Int?` 传递 Markdown/EPUB 滚动聚焦：
 * 搜索命中 focus=K 后，用户手动切章/切书时 ReaderContentHost 仍会在新章用旧 K
 * 滚动（LaunchedEffect(chapterIndex, focus) 被 chapterIndex 变化重启），劫持导航。
 *
 * 本请求携带目标身份：
 * - [bookKey]：目标所属书（跨书迟到/切书遗留的 stale guard）；
 * - [chapterIndex]：目标章（手动切章后旧请求身份不匹配，不得滚动）；
 * - [renderUnitIndex]：滚动渲染单元/文本块索引（Markdown 渲染单元与 EPUB 块共用
 *   的 LazyColumn 项索引空间；null = 无可定位单元，安全 no-op）；
 * - [resultIndex]：搜索命中下标（目标/结果身份，发布方与消费者可核对）；通用 source
 *   导航不属于搜索结果，使用默认值 -1。
 * - [origin]：请求来源。消费者只以书/章身份决定是否滚动，来源仅用于保持语义清楚，
 *   避免把 source 路由误解为搜索状态。
 *
 * 由 [SearchHitNavigationExecutor] 在 stale guard 通过后发布，[ReaderContentHost]
 * 仅在当前书/章与请求一致时消费一次并滚动，消费后 ack 清除。
 */
internal data class SearchScrollFocusRequest(
    val bookKey: String,
    val chapterIndex: Int,
    val renderUnitIndex: Int?,
    val resultIndex: Int = -1,
    val origin: ReaderScrollFocusOrigin = ReaderScrollFocusOrigin.SEARCH,
)

/** 同一一次性 viewport-focus 通道的来源，不改变其书籍/章节 stale guard。 */
internal enum class ReaderScrollFocusOrigin {
    SEARCH,
    SOURCE_NAVIGATION,
}

/**
 * 一次性聚焦请求的一次消费结果（纯 JVM seam，S2 验收 2 的加载/ack 语义）：
 * - [Scroll]：身份匹配且渲染单元就绪，请求已消费；[renderUnitIndex] 为需滚动的
 *   渲染单元索引（null = 无可定位单元，仅 ack 不滚动）；
 * - [Pending]：身份匹配但渲染单元尚未就绪（章节加载中），请求保留 pending，
 *   调用方**不得 ack**，就绪后重试消费 —— 防止加载窗口内提前 ack 丢请求；
 * - [Discarded]：无请求 / 已消费过 / 身份不匹配（跨章、跨书 stale），调用方应
 *   ack 清除，永不滚动。
 */
internal sealed interface SearchScrollFocusOutcome {
    data class Scroll(val renderUnitIndex: Int?) : SearchScrollFocusOutcome

    data object Pending : SearchScrollFocusOutcome

    data object Discarded : SearchScrollFocusOutcome
}

/**
 * 一次性聚焦请求的匹配/消费状态机（纯 JVM seam）。
 *
 * - [consume] 仅在「当前书 + 当前章」与请求身份一致且 [renderUnitsReady] 时消费
 *   一次并清除 pending（[SearchScrollFocusOutcome.Scroll]）；
 * - 身份匹配但未就绪时返回 [SearchScrollFocusOutcome.Pending] 并**保留** pending
 *   （加载中不提前 ack）；身份不匹配（跨章 / 跨书 stale）或已消费过的请求返回
 *   [SearchScrollFocusOutcome.Discarded] 并清除 pending —— 手动切章/切书后的旧请求
 *   被丢弃，永不滚动；
 * - 生产侧以 `MutableState<SearchScrollFocusRequest?>` 持有 pending，
 *   [ReaderContentHost] 消费后把状态清空（ack），与 [consume] 的清空语义一致。
 */
internal class SearchScrollFocusConsumer(
    request: SearchScrollFocusRequest? = null,
) {
    private var pending: SearchScrollFocusRequest? = request

    /** 尚未消费的请求；null = 已消费/已丢弃/从未发布。 */
    val pendingRequest: SearchScrollFocusRequest?
        get() = pending

    /**
     * 尝试消费一次。
     *
     * @param renderUnitsReady 目标章渲染单元是否已就绪（章节加载完成且块/单元非空）；
     *   false 时返回 [SearchScrollFocusOutcome.Pending] 且保留 pending，等待就绪重试。
     */
    fun consume(
        currentBookKey: String,
        currentChapterIndex: Int,
        renderUnitsReady: Boolean = true,
    ): SearchScrollFocusOutcome {
        val req = pending ?: return SearchScrollFocusOutcome.Discarded
        if (req.bookKey != currentBookKey || req.chapterIndex != currentChapterIndex) {
            pending = null
            return SearchScrollFocusOutcome.Discarded
        }
        if (!renderUnitsReady) {
            return SearchScrollFocusOutcome.Pending
        }
        pending = null
        return SearchScrollFocusOutcome.Scroll(req.renderUnitIndex)
    }
}
