package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.settings.ContinueReadingStore
import com.creationreadingassistant.data.local.CoroutineScopeModule.DefaultDispatcher
import com.creationreadingassistant.feature.library.deletion.BookDeletionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookViewModel @Inject constructor(
    private val repository: BookRepository,
    private val continueStore: ContinueReadingStore,
    private val deletions: BookDeletionCoordinator,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val books: StateFlow<List<BookEntity>> = repository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val progress: StateFlow<List<ReadingProgressEntity>> = repository.observeProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val progressById: StateFlow<Map<String, ReadingProgressEntity>> =
        progress.map { list -> list.associateBy { it.book_id } }
            .distinctUntilChanged()
            .flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val sessions: StateFlow<Map<String, List<ReadingSessionEntity>>> =
        repository.observeSessions().map { list: List<ReadingSessionEntity> -> list.groupBy { it.book_id } }
            .distinctUntilChanged()
            .flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap<String, List<ReadingSessionEntity>>())

    val removedContinueIds: StateFlow<Map<String, String>> = continueStore.removedIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun deleteBook(id: String, onResult: (String) -> Unit = {}) = viewModelScope.launch {
        runCatching { deletions.deleteBook(id) }
            .onSuccess { onResult("已删除") }
            .onFailure { onResult("删除失败：${it.message}") }
    }

    /**
     * 「标记为已读完」。
     *
     * 走 [BookRepository.markFinished] 而不是 `updateReadingProgress(100f, "finished")`：
     * 后者会把 `last_read_at` 刷成当下，让一本从未打开过的书跳到「最近阅读」榜首。
     * 标记读完是管理操作，不是阅读行为，不应伪造阅读时间。
     */
    fun markRead(id: String) = viewModelScope.launch {
        repository.markFinished(id)
    }

    fun markUnread(id: String) = viewModelScope.launch {
        repository.updateReadingProgress(id, 0f, "reading")
    }

    fun shelve(id: String) = viewModelScope.launch {
        repository.setReadingState(id, ReadingCompletionState.SHELVED)
        continueStore.clear(id)
    }

    fun restoreReading(id: String) = viewModelScope.launch {
        repository.setReadingState(id, ReadingCompletionState.READING)
        continueStore.clear(id)
    }

    fun removeFromContinue(id: String) = viewModelScope.launch {
        continueStore.remove(id)
    }

    fun clearContinueRemoval(id: String) = viewModelScope.launch {
        continueStore.clear(id)
    }
}
