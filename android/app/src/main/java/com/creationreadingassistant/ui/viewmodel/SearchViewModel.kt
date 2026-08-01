package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.settings.SearchHistoryStore
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
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
    private val bookDao: BookDao,
    private val inspirationDao: InspirationDao,
    private val noteDao: NoteDao,
    private val highlightDao: HighlightDao,
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
        if (q.isBlank()) { _results.value = SearchResults(); return }
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _loading.value = true
            val query = q.trim()
            val results = withContext(ioDispatcher) {
                val b = runCatching { bookDao.search(query) }.getOrDefault(emptyList())
                val i = runCatching { inspirationDao.search(query) }.getOrDefault(emptyList())
                val n = runCatching { noteDao.search(query) }.getOrDefault(emptyList())
                val h = runCatching { highlightDao.search(query) }.getOrDefault(emptyList())
                // 收集笔记/高亮所属书籍名，供结果项展示来源（SE6）
                val neededIds = (n.map { it.book_id } + h.map { it.book_id }).filterNotNull().toSet()
                val titles = neededIds.mapNotNull { id ->
                    runCatching { bookDao.getById(id) }.getOrNull()?.let { it.id to it.title }
                }.toMap()
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
