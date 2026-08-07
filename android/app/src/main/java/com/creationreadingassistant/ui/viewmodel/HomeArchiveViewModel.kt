package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.local.CoroutineScopeModule.DefaultDispatcher
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.InspirationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class CompletedArchiveItem(
    val book: BookEntity,
    val progress: ReadingProgressEntity,
)

data class HomeArchiveUiState(
    val inspirations: List<InspirationEntity> = emptyList(),
    val completedBooks: List<CompletedArchiveItem> = emptyList(),
    val isReady: Boolean = false,
)

@HiltViewModel
class HomeArchiveViewModel @Inject constructor(
    repository: BookRepository,
    inspirationRepository: InspirationRepository,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    val uiState: StateFlow<HomeArchiveUiState> = combine(
        repository.observeBooks(),
        repository.observeProgress(),
        inspirationRepository.observeAllActive(),
    ) { books, progress, inspirations ->
        val progressByBook = progress.associateBy { it.book_id }
        HomeArchiveUiState(
            inspirations = inspirations.sortedByDescending { it.updated_at },
            completedBooks = books.mapNotNull { book ->
                val itemProgress = progressByBook[book.id] ?: return@mapNotNull null
                if (book.deleted_at == null &&
                    (itemProgress.readingState == ReadingCompletionState.FINISHED || itemProgress.progress_percent >= 99.5f)
                ) CompletedArchiveItem(book, itemProgress) else null
            }.sortedByDescending { it.progress.completed_at ?: 0L },
            isReady = true,
        )
    }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeArchiveUiState())
}
