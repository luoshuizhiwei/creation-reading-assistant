package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.data.settings.ImportHistoryEntry
import com.creationreadingassistant.ui.screen.shelf.ShelfSortMode
import com.creationreadingassistant.ui.screen.shelf.ShelfStatusFilter
import com.creationreadingassistant.ui.screen.shelf.ShelfViewMode

data class ImportTaskUi(
    val id: String,
    val fileName: String,
    val phase: String,
    val status: String, // "processing" | "done" | "error"
    val createdAt: Long = System.currentTimeMillis(),
)

data class ImportFailureUi(
    val uri: String,
    val fileName: String,
    val reason: String,
)

data class ImportBatchUiState(
    val id: String = "",
    val sourceLabel: String = "",
    val total: Int = 0,
    val completed: Int = 0,
    val succeeded: Int = 0,
    val duplicates: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val stopped: Int = 0,
    val unreadableFolders: Int = 0,
    val truncated: Boolean = false,
    val isScanning: Boolean = false,
    val isRunning: Boolean = false,
    val stopRequested: Boolean = false,
    val failures: List<ImportFailureUi> = emptyList(),
) {
    val hasResult: Boolean
        get() = total > 0 || failed > 0 || unreadableFolders > 0 || truncated
}

internal data class ShelfSessionState(
    val viewMode: ShelfViewMode = ShelfViewMode.GRID,
    val sortMode: ShelfSortMode = ShelfSortMode.RECENT,
    val statusFilter: ShelfStatusFilter = ShelfStatusFilter.ALL,
    /** 格式筛选："" = 全部；epub / txt / md。 */
    val formatFilter: String = "",
    val selectedShelfId: String = "",
    val selectedCategoryId: String = "",
    val selectedTagIds: Set<String> = emptySet(),
) {
    /** 清除临时筛选，但保留用户长期选择的排序和视图。 */
    fun clearFilters(): ShelfSessionState = copy(
        statusFilter = ShelfStatusFilter.ALL,
        formatFilter = "",
        selectedShelfId = "",
        selectedCategoryId = "",
        selectedTagIds = emptySet(),
    )
}

internal fun ImportBatchUiState.markStopRequested(): ImportBatchUiState =
    if (isRunning) copy(stopRequested = true) else this

internal fun remainingImportCount(total: Int, currentIndex: Int, currentCompleted: Boolean): Int =
    (total - currentIndex - if (currentCompleted) 1 else 0).coerceAtLeast(0)

data class ShelfUiState(
    val library: ShelfLibraryState = ShelfLibraryState(),
    val activity: ShelfActivityState = ShelfActivityState(),
    val auxiliary: ShelfAuxiliaryState = ShelfAuxiliaryState(),
)

data class ShelfLibraryState(
    val books: List<BookEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val shelves: List<ShelfEntity> = emptyList(),
    val progressById: Map<String, ReadingProgressEntity> = emptyMap(),
)

data class ShelfActivityState(
    val sessionsByBook: Map<String, List<ReadingSessionEntity>> = emptyMap(),
    val notesByBook: Map<String, List<NoteEntity>> = emptyMap(),
    val highlightsByBook: Map<String, List<HighlightEntity>> = emptyMap(),
    val inspirationsByBook: Map<String, List<InspirationEntity>> = emptyMap(),
)

data class ShelfAuxiliaryState(
    val importTasks: List<ImportTaskUi> = emptyList(),
    val importBatch: ImportBatchUiState = ImportBatchUiState(),
    val downloadingIds: Set<String> = emptySet(),
    val importHistory: List<ImportHistoryEntry> = emptyList(),
    val savedViewMode: String = "grid",
    val savedSortMode: String = "recent",
)
