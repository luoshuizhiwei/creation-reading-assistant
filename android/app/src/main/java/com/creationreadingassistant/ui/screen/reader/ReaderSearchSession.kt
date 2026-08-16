package com.creationreadingassistant.ui.screen.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 书内搜索会话阶段。 */
internal enum class BookSearchPhase {
    /** 无查询或查询已清空。 */
    IDLE,

    /** 用户已提交查询，搜索运行中（含 250ms 防抖窗口）。 */
    SEARCHING,

    /** 用户点击取消：当前运行已作废，不自动重启。 */
    CANCELLED,

    /** 搜索正常完成。 */
    COMPLETED,
}

/**
 * 搜索命中的统一导航目标。调用方（跳转 effect / 高亮渲染）只消费这个 target，
 * 不再按格式分别拼 offset：
 * - [bookKey]：目标所属书籍（用于跨书迟到跳转防护）；
 * - [chapterIndex]：命中所在章；-1 = 纯文本全书无章节；
 * - [absoluteRange]：命中在全书文本中的字符区间（含首不含尾，与各格式 locator 同一基准）；
 * - [resultIndex]：命中在 [BookSearchSession.results] 中的下标。
 */
internal data class SearchHitTarget(
    val bookKey: String,
    val chapterIndex: Int,
    val absoluteRange: IntRange,
    val resultIndex: Int,
)

/**
 * 书内搜索命中导航器公共 seam：results / currentIndex / select / next / previous。
 * 空结果安全；边界行为固定为循环（next 越过末处回第一处，previous 越过首处回末处）。
 */
internal interface SearchHitNavigator {
    val results: List<BookSearchResult>

    /** 当前命中下标；-1 = 无选中。 */
    val currentIndex: Int

    /** 当前命中对应的统一跳转目标；无选中时为 null。 */
    val currentTarget: SearchHitTarget?

    fun select(index: Int): SearchHitTarget?

    fun next(): SearchHitTarget?

    fun previous(): SearchHitTarget?
}

/**
 * 书内搜索会话状态机（[SearchSheet] 的取消/重启语义，纯 JVM 可测 seam）。
 *
 * 事件：
 * - [onQueryChanged]：用户显式修改/提交查询；
 * - [cancel]：用户点击「取消」；
 * - [onProgress] / [onSearchCompleted]：搜索协程回报。
 *
 * 可观察性：query / phase / results / progress 全部由 Compose snapshot state 支撑，
 * 组合中直接读取即可在回报写回时触发重组（搜索协程在 IO 线程写回，快照写入线程安全）。
 *
 * 不变式：
 * 1. 取消后同一查询的重复投递不会重启搜索 —— 只有显式的新查询字符串才会回到 SEARCHING；
 * 2. 旧运行（runId 不匹配）的完成回报被丢弃，不会覆盖新运行的结果/阶段；
 * 3. 取消后保留上一次已完成结果，阶段进入明确的 CANCELLED。
 */
internal class BookSearchSession(
    /** 当前书籍标识；target 携带它做跨书过期防护。 */
    val bookKey: String = "",
) : SearchHitNavigator {
    var query: String by mutableStateOf("")
        private set

    var phase: BookSearchPhase by mutableStateOf(BookSearchPhase.IDLE)
        private set

    override var results: List<BookSearchResult> by mutableStateOf(emptyList())
        private set

    var progress: Pair<Int, Int> by mutableStateOf(0 to 0)
        private set

    override var currentIndex: Int by mutableStateOf(-1)
        private set

    private var currentRunId = 0L

    /** 最近一次搜索运行绑定的文档上下文（面板重开守卫的补充维度）。 */
    private var searchContextKey: String? = null

    override val currentTarget: SearchHitTarget?
        get() = results.getOrNull(currentIndex)?.let {
            SearchHitTarget(
                bookKey = bookKey,
                chapterIndex = it.chapterIndex,
                absoluteRange = it.absoluteRange,
                resultIndex = currentIndex,
            )
        }

    override fun select(index: Int): SearchHitTarget? {
        if (index !in results.indices) return null
        currentIndex = index
        return currentTarget
    }

    override fun next(): SearchHitTarget? {
        if (results.isEmpty()) return null
        currentIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % results.size
        return currentTarget
    }

    override fun previous(): SearchHitTarget? {
        if (results.isEmpty()) return null
        currentIndex = if (currentIndex <= 0) results.lastIndex else currentIndex - 1
        return currentTarget
    }

    /**
     * 面板重开守卫：同一查询在 COMPLETED / CANCELLED 下不得重启搜索
     * （关闭/重开面板保留 query/results/current hit 的关键判定）；
     * 查询变化或面板关闭时仍在途的 SEARCHING 则允许（需要）重启。
     */
    fun shouldRestartSearch(newQuery: String): Boolean =
        newQuery != query || phase == BookSearchPhase.SEARCHING

    /**
     * 返回当前运行 id；搜索协程启动时捕获，回报时用于丢弃过期回调。
     * [contextKey] 记录本次运行绑定的文档上下文（同一文档重开面板不得重启搜索；
     * 文档变化——如 TXT 目录规则切换——即使查询未变也必须重启）。
     */
    fun beginRun(contextKey: String = ""): Long {
        searchContextKey = contextKey
        return currentRunId
    }

    /** 当前搜索结果是否仍属于 [contextKey] 描述的文档上下文。 */
    fun matchesSearchContext(contextKey: String): Boolean = searchContextKey == contextKey

    /**
     * 文档上下文变化（TXT 目录规则重扫 / 文档实例变化）时的显式重启语义：
     * 即使 query 未变也必须清旧 results/currentIndex 并重启搜索（不依赖取消 token）。
     * 立即作废在途运行（runId 递增，迟到回报被丢弃）并绑定新 [contextKey]。
     *
     * @return true = 已进入 SEARCHING（调用方应启动搜索协程）；false = query 为空已进入 IDLE。
     */
    fun onContextChanged(newQuery: String, contextKey: String): Boolean {
        searchContextKey = contextKey
        currentRunId += 1
        currentIndex = -1
        progress = 0 to 0
        if (newQuery.isBlank()) {
            query = ""
            results = emptyList()
            phase = BookSearchPhase.IDLE
            return false
        }
        query = newQuery
        results = emptyList()
        phase = BookSearchPhase.SEARCHING
        return true
    }

    fun onQueryChanged(newQuery: String) {
        // 取消后重放同一查询（旧实现中 cancelToken 变更导致 LaunchedEffect 以同一 query 重启）：
        // 不重启，保持 CANCELLED，等待用户显式提交新查询。
        if (newQuery == query && phase == BookSearchPhase.CANCELLED) return
        query = newQuery
        progress = 0 to 0
        // 新查询（含同查询重启）使旧命中失效；关闭/重开面板不经过此路径（见 shouldRestartSearch）
        currentIndex = -1
        if (newQuery.isBlank()) {
            results = emptyList()
            phase = BookSearchPhase.IDLE
        } else {
            phase = BookSearchPhase.SEARCHING
        }
        currentRunId += 1
    }

    fun onProgress(runId: Long, done: Int, total: Int) {
        if (runId == currentRunId && phase == BookSearchPhase.SEARCHING) {
            progress = done to total
        }
    }

    fun onSearchCompleted(runId: Long, newResults: List<BookSearchResult>) {
        if (runId != currentRunId) return
        results = newResults
        // 新一轮结果与旧命中下标不再对应
        currentIndex = -1
        phase = BookSearchPhase.COMPLETED
    }

    /** 用户点击取消：立即作废当前运行并进入 CANCELLED（不重启）。 */
    fun cancel() {
        if (phase == BookSearchPhase.SEARCHING) {
            currentRunId += 1
            phase = BookSearchPhase.CANCELLED
        }
    }
}
