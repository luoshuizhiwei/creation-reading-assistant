package com.creationreadingassistant.ui.viewmodel

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
import com.creationreadingassistant.data.settings.SearchHistoryStore
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

data class SearchResults(
    val books: List<BookEntity> = emptyList(),
    val inspirations: List<InspirationEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val highlights: List<HighlightEntity> = emptyList(),
    /** bookId -> 书名，用于结果项展示来源书籍名（SE6）。 */
    val bookTitles: Map<String, String> = emptyMap(),
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val inspirationRepository: InspirationRepository,
    private val noteRepository: NoteRepository,
    private val historyStore: SearchHistoryStore,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _results = MutableStateFlow(SearchResults())
    val results: StateFlow<SearchResults> = _results.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** 搜索历史（SE2）。 */
    val history: StateFlow<List<String>> = historyStore.history

    private var searchJob: Job? = null

    fun search(q: String) {
        searchJob?.cancel()
        if (q.isBlank()) {
            // 搜索中清空查询：取消在途 job 且显式复位 loading，避免永久转圈
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
                // 收集笔记/高亮所属书籍名，供结果项展示来源（SE6）
                // 用 IN 批量查询替代逐条 getById，消除 N+1
                val neededIds = (n.map { it.book_id } + h.map { it.book_id }).filterNotNull().toSet()
                val titles = if (neededIds.isEmpty()) {
                    emptyMap()
                } else {
                    runCatching { bookRepository.getByIds(neededIds).associate { it.id to it.title } }
                        .getOrDefault(emptyMap())
                }
                SearchResults(books = b, inspirations = i, notes = n, highlights = h, bookTitles = titles)
            }
            _results.value = results
            _loading.value = false
        }
    }

    /** 记录一条搜索历史（去重、置顶、上限 12）。 */
    fun addHistory(term: String) {
        viewModelScope.launch { historyStore.add(term) }
    }

    /** 清空搜索历史。 */
    fun clearHistory() {
        viewModelScope.launch { historyStore.clear() }
    }
}
