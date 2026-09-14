package com.creationreadingassistant.ui.viewmodel

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.InspirationRepository
import com.creationreadingassistant.data.repository.NoteRepository
import com.creationreadingassistant.data.repository.SearchIndexRepository
import com.creationreadingassistant.data.settings.SearchHistoryStore
import com.creationreadingassistant.feature.search.SearchCoveragePolicy
import com.creationreadingassistant.feature.search.SearchHit
import com.creationreadingassistant.feature.search.SearchTextBasis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 搜索完整性反馈。
 *
 * 存在的理由：全局搜索长期「只搜元数据 + 正文预览」却对 UI 声称是全量结果。
 * 与其假装，不如明确告诉用户「你现在看到的有多全」——这也是 README R2-S1
 * 「部分索引不是全文完成」在界面侧的落点。
 */
enum class SearchNoticeKind {
    /** 全文索引尚未建立：这一轮只搜到了书名/作者等元数据。 */
    INDEX_NOT_BUILT,

    /** 命中的书里有若干本不是全量覆盖，正文结果可能不全。 */
    COVERAGE_INCOMPLETE,
}

data class SearchNotice(
    val kind: SearchNoticeKind,
    /** 受影响的书数量；[SearchNoticeKind.INDEX_NOT_BUILT] 时为 0。 */
    val affectedBooks: Int,
)

data class SearchResults(
    val books: List<BookEntity> = emptyList(),
    val inspirations: List<InspirationEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val highlights: List<HighlightEntity> = emptyList(),
    /** bookId -> 书名，用于结果项展示来源书籍名（SE6）。 */
    val bookTitles: Map<String, String> = emptyMap(),
    /**
     * 全文索引命中（按分降序），每条携带章节 / 章内偏移 / 文本基准 / 覆盖率。
     *
     * 与 [books] 是**两套不同的命中**：[books] 来自元数据 LIKE，这里来自倒排索引的正文。
     * 两者可以重叠（同一本书既在书名里命中、也在正文里命中）。
     */
    val contentHits: List<SearchHit> = emptyList(),
    /**
     * 元数据命中里「同时也在正文命中」的 bookId。
     * 「全部」页据此对书目行去重 —— 正文行信息更丰富，保留它。
     */
    val booksAlsoMatchedInContent: Set<String> = emptySet(),
    /** 搜索完整性反馈；null = 无需提示。 */
    val notice: SearchNotice? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val inspirationRepository: InspirationRepository,
    private val noteRepository: NoteRepository,
    private val searchIndexRepository: SearchIndexRepository,
    private val historyStore: SearchHistoryStore,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _results = MutableStateFlow(SearchResults())
    val results: StateFlow<SearchResults> = _results.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** 搜索历史（SE2）。 */
    val history: StateFlow<List<String>> = historyStore.history

    /** 全库全文索引构建状态与进度透明化流。 */
    val indexProgress: StateFlow<SearchIndexRepository.Progress> =
        runCatching { searchIndexRepository.progress }
            .getOrDefault(MutableStateFlow(SearchIndexRepository.Progress(0L, 0, false)))

    private var searchJob: Job? = null

    /**
     * 搜索词（R2-S1.5）。
     *
     * 从 composable 的 `remember { mutableStateOf("") }` 提升到 VM：SearchViewModel 绑定到
     * search 路由的 NavBackStackEntry，在「搜索页 → 阅读器临时查阅 → 返回搜索页」的导航往返中
     * 始终存活。若仍留在 composable 局部，离开搜索页时该 remember 会被丢弃，返回后词清空、
     * 结果被 [search] 以空词重置 —— 这正是 R2 退出条件「返回搜索页并保留查询」要杜绝的。
     */
    val queryState = mutableStateOf("")

    /**
     * 当前分类 Tab（R2-S1.5）。
     *
     * 同理必须跨往返保留：返回后若 Tab 落回默认「全部」，而滚动位置却指向原「正文」页的列表，
     * 二者所指内容不一致，滚动还原就失去意义。
     */
    val tabState = mutableStateOf("all")

    /**
     * 正文结果列表的滚动状态（R2-S1.5）。持有在 VM 中跨导航往返保留。
     */
    val listState = LazyListState()

    /** 上次已真正搜过的词（trim 后）；用于避免同一词被重复触发搜索（见 [search]）。 */
    private var lastSearchedQuery: String? = null

    fun search(q: String) {
        searchJob?.cancel()
        // R2-S1.5：同一词（trim 后）已在显示结果时，跳过 —— 例如从阅读器临时查阅返回搜索页，
        // SearchScreen 重组会重新触发 LaunchedEffect(query)。此时结果与 loading 都已就绪，
        // 重复搜索既浪费 I/O，又会让列表重新转圈闪烁。
        if (q.trim() == lastSearchedQuery) return
        if (q.isBlank()) {
            // 搜索中清空查询：取消在途 job 且显式复位 loading，避免永久转圈
            lastSearchedQuery = ""
            _results.value = SearchResults()
            _loading.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _loading.value = true
            val query = q.trim()
            val results = withContext(ioDispatcher) {
                val b = runCatching { bookRepository.search(query) }.getOrDefault(emptyList())
                val i = runCatching { inspirationRepository.search(query) }.getOrDefault(emptyList())
                val n = runCatching { noteRepository.searchNotes(query) }.getOrDefault(emptyList())
                val h = runCatching { noteRepository.searchHighlights(query) }.getOrDefault(emptyList())
                // R2-S1.3：全文索引首次真正被搜索 UI 消费（此前 searchContent 是死 seam）。
                // 索引是派生数据，任何失败都只降级为「没有正文命中」，绝不让搜索页报错。
                // R2-S1.6：双通道口径——原文与替换显示文都搜，命中按 textBasis 标注来源。
                val content = runCatching {
                    searchIndexRepository.searchContent(
                        query,
                        bases = setOf(SearchTextBasis.ORIGINAL, SearchTextBasis.DISPLAY),
                    )
                }.getOrDefault(emptyList())

                val metadataIds = b.map { it.id }.toSet()
                val contentIds = content.map { it.bookId }.toSet()
                // 收集笔记/高亮/**正文命中**所属书籍名，供结果项展示来源（SE6）
                // 用 IN 批量查询替代逐条 getById，消除 N+1。
                // 注意必须包含**全部** contentIds：正文命中若同时也在元数据里命中，
                // 列表里那本书是以正文行的形式出现的，仍然需要书名 ——
                // 只取差集会让这些行退化成显示裸 bookId。
                val neededIds = (n.map { it.book_id } + h.map { it.book_id }).filterNotNull().toSet() +
                    contentIds
                val titles = if (neededIds.isEmpty()) {
                    emptyMap()
                } else {
                    runCatching { bookRepository.getByIds(neededIds).associate { it.id to it.title } }
                        .getOrDefault(emptyMap())
                }
                SearchResults(
                    books = b,
                    inspirations = i,
                    notes = n,
                    highlights = h,
                    bookTitles = titles,
                    contentHits = content,
                    booksAlsoMatchedInContent = metadataIds.intersect(contentIds),
                    notice = buildNotice(content),
                )
            }
            _results.value = results
            _loading.value = false
            lastSearchedQuery = query
        }
    }

    /**
     * 生成完整性反馈：索引没建 / 命中的书不是全量覆盖。
     *
     * 覆盖率一律读 [SearchHit.coverage]（来自 S1.1 落库的 `search_index_coverage`），
     * **不按 format 或命中数推断** —— 那正是 README 点名禁止的做法。
     */
    private suspend fun buildNotice(contentHits: List<SearchHit>): SearchNotice? {
        val indexedBooks = runCatching { searchIndexRepository.countIndexedBooks() }.getOrDefault(0L)
        if (indexedBooks == 0L) {
            return SearchNotice(SearchNoticeKind.INDEX_NOT_BUILT, 0)
        }
        // coverage 为 null 表示「尚未得知」，按「已知未完成」处理会虚报，故只统计已知状态。
        val incomplete = contentHits.count { hit ->
            val state = hit.coverage ?: return@count false
            SearchCoveragePolicy.isIncomplete(state)
        }
        return if (incomplete > 0) {
            SearchNotice(SearchNoticeKind.COVERAGE_INCOMPLETE, incomplete)
        } else {
            null
        }
    }

    /**
     * 把一条正文命中换算成可精确跳转的**全书偏移**；无法定位时返回 null（R2-S1.4）。
     *
     * null 时调用方必须降级为「只打开书」，**绝不拿 0 冒充位置** ——
     * 伪造 offset=0 会让用户跳到书的最开头，还以为跳转成功了。
     *
     * 换算要读磁盘（TXT 解码分章 / EPUB 解析 spine），因此是 suspend，
     * 且只在**点击时**对单条命中调用，绝不对整页结果批量调用。
     */
    suspend fun resolvePreciseOffset(hit: SearchHit): Int? =
        runCatching { searchIndexRepository.resolveLegacyOffset(hit) }.getOrNull()

    /** 记录一条搜索历史（去重、置顶、上限 12）。 */
    fun addHistory(term: String) {
        viewModelScope.launch { historyStore.add(term) }
    }

    /** 清空搜索历史。 */
    fun clearHistory() {
        viewModelScope.launch { historyStore.clear() }
    }

    /** 触发全库全文索引重建（清空游标并立即唤起后台 Worker）。 */
    fun triggerRebuildIndex() {
        viewModelScope.launch(ioDispatcher) {
            runCatching {
                searchIndexRepository.triggerFullRebuildNow()
            }
        }
    }
}
