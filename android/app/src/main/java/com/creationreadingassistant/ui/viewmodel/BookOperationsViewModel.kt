package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.SyncRepository
import com.creationreadingassistant.data.settings.ContinueReadingStore
import com.creationreadingassistant.feature.library.ShelfImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 轻量书籍写操作 ViewModel —— 供只需要「删除/恢复阅读/修复文件/同步下载」
 * 这类写操作的页面使用（我的阅读、已读完成归档等）。
 *
 * 与 [ShelfViewModel] 的区别：不订阅任何热流、不在 init 跑播种/修复任务，
 * 避免在这些页面额外创建重量级书架实例带来的流订阅与初始化开销。
 * 操作语义与书架页完全一致：均委托 [ShelfImporter] / [ShelfBookActions] /
 * [SyncRepository] 同一批实现。仅依赖 Repository 层，不直接注入 DAO。
 */
@HiltViewModel
class BookOperationsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BookRepository,
    private val syncRepository: SyncRepository,
    private val continueReadingStore: ContinueReadingStore,
    private val shelfImporter: ShelfImporter,
) : ViewModel() {

    /**
     * 本 VM 只暴露 [reselectFile]（repairFile 路径），不会走导入批处理的重复检测，
     * 因此 booksProvider 保持默认空列表，不订阅书架热流。
     */
    private val importer: ShelfImporter = shelfImporter

    private val bookActions: ShelfBookActions = ShelfBookActions(
        context = context,
        repository = repository,
        continueReadingStore = continueReadingStore,
    )

    /** 防重复下载的进程内标记（与书架页行为一致）。 */
    private val downloadingIds = MutableStateFlow<Set<String>>(emptySet())

    /** 重新选择文件（修复缺失正文）。 */
    fun reselectFile(bookId: String, uri: Uri, onResult: (String) -> Unit = {}) = viewModelScope.launch {
        onResult(importer.repairFile(bookId, uri))
    }

    /** 删除书籍（软删除 + 级联清理，语义与书架页一致）。 */
    fun deleteBook(id: String) = viewModelScope.launch {
        bookActions.deleteBook(id)
    }

    /** 恢复阅读状态（搁置 → 在读）。 */
    fun restoreReading(id: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) = viewModelScope.launch {
        bookActions.restoreReading(id)
            .onSuccess { onResult(true, it) }
            .onFailure { onResult(false, "恢复失败：${it.message}") }
    }

    /** 同步下载书籍正文。 */
    fun downloadBookContent(bookId: String, onResult: (String) -> Unit = {}) = viewModelScope.launch {
        if (downloadingIds.value.contains(bookId)) {
            onResult("正在下载中…")
            return@launch
        }
        downloadingIds.value = downloadingIds.value + bookId
        syncRepository.downloadBookContent(bookId)
            .onSuccess { onResult("下载成功") }
            .onFailure { onResult("下载失败：${it.message}") }
        downloadingIds.value = downloadingIds.value - bookId
    }
}
