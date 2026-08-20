package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import com.creationreadingassistant.data.local.entity.BookTagEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.ShelfBookEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.data.repository.TaxonomyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 分类 / 标签 / 书单 的完整 CRUD + 书籍关联 ViewModel。
 * 取代 ShelfScreen / ProfileScreen 里此前「仅展示 / 待接入」的降级实现。
 */
@HiltViewModel
class TaxonomyViewModel @Inject constructor(
    private val repository: TaxonomyRepository,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    // ---- 全部列表 ----
    val allTags: StateFlow<List<TagEntity>> = repository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCategories: StateFlow<List<CategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allShelves: StateFlow<List<ShelfEntity>> = repository.observeShelves()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- 按书的关联 ID ----
    fun observeTagIdsForBooks(bookIds: List<String>): Flow<List<String>> =
        repository.observeTagIdsForBooks(bookIds)

    fun observeCategoryIdsForBooks(bookIds: List<String>): Flow<List<String>> =
        repository.observeCategoryIdsForBooks(bookIds)

    fun observeShelfIdsForBooks(bookIds: List<String>): Flow<List<String>> =
        repository.observeShelfIdsForBooks(bookIds)

    fun observeTagIdsForBook(bookId: String): Flow<List<String>> = repository.observeTagIdsForBook(bookId)
    fun observeCategoryIdsForBook(bookId: String): Flow<List<String>> = repository.observeCategoryIdsForBook(bookId)
    fun observeShelfIdsForBook(bookId: String): Flow<List<String>> = repository.observeShelfIdsForBook(bookId)

    suspend fun getBookIdsByShelf(shelfId: String): List<String> = repository.getBookIdsByShelf(shelfId)
    suspend fun getBookIdsByCategory(categoryId: String): List<String> = repository.getBookIdsByCategory(categoryId)
    suspend fun getBookIdsByTag(tagId: String): List<String> = repository.getBookIdsByTag(tagId)

    // ---- 创建 ----
    // 禁止写入空/纯空白名称，避免 TextField IME 合成文本未提交时创建出空名脏数据。
    fun createTag(name: String, type: String = "book") = viewModelScope.launch(ioDispatcher) {
        if (name.isBlank()) return@launch
        repository.createTag(name, type)
    }

    suspend fun createTagAndGetId(name: String, type: String = "book"): String =
        if (name.isBlank()) "" else repository.createTag(name, type)

    fun createCategory(name: String) = viewModelScope.launch(ioDispatcher) {
        if (name.isBlank()) return@launch
        repository.createCategory(name)
    }

    suspend fun createCategoryAndGetId(name: String): String =
        if (name.isBlank()) "" else repository.createCategory(name)

    fun createShelf(name: String) = viewModelScope.launch(ioDispatcher) {
        if (name.isBlank()) return@launch
        repository.createShelf(name)
    }

    suspend fun createShelfAndGetId(name: String): String =
        if (name.isBlank()) "" else repository.createShelf(name)

    // ---- 重命名 ----
    // 重命名同样禁止空名（留空时保持原名，防止误操作把条目改成"无标题"）。
    fun renameTag(id: String, newName: String) = viewModelScope.launch(ioDispatcher) {
        if (newName.isBlank()) return@launch
        repository.renameTag(id, newName)
    }

    fun renameCategory(id: String, newName: String) = viewModelScope.launch(ioDispatcher) {
        if (newName.isBlank()) return@launch
        repository.renameCategory(id, newName)
    }

    fun renameShelf(id: String, newName: String) = viewModelScope.launch(ioDispatcher) {
        if (newName.isBlank()) return@launch
        repository.renameShelf(id, newName)
    }

    fun updateCategoryTone(id: String, tone: String) = viewModelScope.launch(ioDispatcher) {
        repository.updateCategoryTone(id, tone)
    }

    // ---- 软删除 ----
    fun deleteTag(id: String) = viewModelScope.launch(ioDispatcher) {
        repository.deleteTag(id)
    }

    fun deleteCategory(id: String) = viewModelScope.launch(ioDispatcher) {
        repository.deleteCategory(id)
    }

    fun deleteShelf(id: String) = viewModelScope.launch(ioDispatcher) {
        repository.deleteShelf(id)
    }

    // ---- 书籍关联 ----
    fun addTagToBooks(bookIds: List<String>, tagId: String) = viewModelScope.launch(ioDispatcher) {
        repository.addTagToBooks(bookIds, tagId)
    }

    fun addTagToBook(bookId: String, tagId: String) = viewModelScope.launch(ioDispatcher) {
        repository.addTagToBook(bookId, tagId)
    }

    fun removeTagFromBook(bookId: String, tagId: String) = viewModelScope.launch(ioDispatcher) {
        repository.removeTagFromBook(bookId, tagId)
    }

    fun setCategoryForBooks(bookIds: List<String>, categoryId: String) = viewModelScope.launch(ioDispatcher) {
        if (bookIds.isEmpty()) return@launch
        repository.replaceCategoryForBooks(bookIds, categoryId)
    }

    fun removeCategoryFromBook(bookId: String, categoryId: String) = viewModelScope.launch(ioDispatcher) {
        repository.removeCategoryFromBook(bookId, categoryId)
    }

    fun addBooksToShelf(bookIds: List<String>, shelfId: String) = viewModelScope.launch(ioDispatcher) {
        repository.addBooksToShelf(bookIds, shelfId)
    }

    fun removeBookFromShelf(bookId: String, shelfId: String) = viewModelScope.launch(ioDispatcher) {
        repository.removeBookFromShelf(bookId, shelfId)
    }

    // ---- 排序：仅上移/下移一位（Repository 内部只交换相邻两项 sort_order，事务内完成） ----
    // 不提供拖拽、置顶、置底、稠密重排入口；遵循审核约束。

    fun moveTagUp(tagId: String) = viewModelScope.launch(ioDispatcher) {
        repository.moveTagUp(tagId)
    }

    fun moveTagDown(tagId: String) = viewModelScope.launch(ioDispatcher) {
        repository.moveTagDown(tagId)
    }

    fun moveCategoryUp(categoryId: String) = viewModelScope.launch(ioDispatcher) {
        repository.moveCategoryUp(categoryId)
    }

    fun moveCategoryDown(categoryId: String) = viewModelScope.launch(ioDispatcher) {
        repository.moveCategoryDown(categoryId)
    }

    fun moveShelfUp(shelfId: String) = viewModelScope.launch(ioDispatcher) {
        repository.moveShelfUp(shelfId)
    }

    fun moveShelfDown(shelfId: String) = viewModelScope.launch(ioDispatcher) {
        repository.moveShelfDown(shelfId)
    }
}
