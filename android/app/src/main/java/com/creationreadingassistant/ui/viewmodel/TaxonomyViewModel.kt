package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.local.dao.BookCategoryDao
import com.creationreadingassistant.data.local.dao.BookTagDao
import com.creationreadingassistant.data.local.dao.CategoryDao
import com.creationreadingassistant.data.local.dao.ShelfBookDao
import com.creationreadingassistant.data.local.dao.ShelfDao
import com.creationreadingassistant.data.local.dao.TagDao
import com.creationreadingassistant.data.local.entity.BookTagEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.ShelfBookEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * 分类 / 标签 / 书单 的完整 CRUD + 书籍关联 ViewModel。
 * 取代 ShelfScreen / ProfileScreen 里此前「仅展示 / 待接入」的降级实现。
 */
@HiltViewModel
class TaxonomyViewModel @Inject constructor(
    private val tagDao: TagDao,
    private val categoryDao: CategoryDao,
    private val shelfDao: ShelfDao,
    private val bookTagDao: BookTagDao,
    private val bookCategoryDao: BookCategoryDao,
    private val shelfBookDao: ShelfBookDao,
) : ViewModel() {

    // ---- 全部列表 ----
    val allTags: StateFlow<List<TagEntity>> = tagDao.observeAllActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCategories: StateFlow<List<CategoryEntity>> = categoryDao.observeAllActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allShelves: StateFlow<List<ShelfEntity>> = shelfDao.observeAllActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- 按书的关联 ID ----
    fun observeTagIdsForBooks(bookIds: List<String>): Flow<List<String>> =
        if (bookIds.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
        else bookTagDao.observeTagIdsForBooks(bookIds)

    fun observeCategoryIdsForBooks(bookIds: List<String>): Flow<List<String>> =
        if (bookIds.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
        else bookCategoryDao.observeCategoryIdsForBooks(bookIds)

    fun observeShelfIdsForBooks(bookIds: List<String>): Flow<List<String>> =
        if (bookIds.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
        else shelfBookDao.observeShelfIdsForBooks(bookIds)

    fun observeTagIdsForBook(bookId: String): Flow<List<String>> = bookTagDao.observeTagIds(bookId)
    fun observeCategoryIdsForBook(bookId: String): Flow<List<String>> = bookCategoryDao.observeCategoryIds(bookId)
    fun observeShelfIdsForBook(bookId: String): Flow<List<String>> = shelfBookDao.observeShelfIds(bookId)

    suspend fun getBookIdsByShelf(shelfId: String): List<String> = shelfBookDao.getBookIds(shelfId)
    suspend fun getBookIdsByCategory(categoryId: String): List<String> = bookCategoryDao.getBookIds(categoryId)
    suspend fun getBookIdsByTag(tagId: String): List<String> = bookTagDao.getBookIds(tagId)

    // ---- 创建 ----
    fun createTag(name: String, type: String = "book") = viewModelScope.launch(Dispatchers.IO) {
        createTagAndGetId(name, type)
    }

    suspend fun createTagAndGetId(name: String, type: String = "book"): String {
        val now = Instant.now().toString()
        val id = "tag-${UUID.randomUUID()}"
        tagDao.upsert(TagEntity(id = id, name = name.trim(), type = type.trim().takeIf { it.isNotBlank() } ?: "book", updated_at = now))
        return id
    }

    fun createCategory(name: String) = viewModelScope.launch(Dispatchers.IO) {
        createCategoryAndGetId(name)
    }

    suspend fun createCategoryAndGetId(name: String): String {
        val now = Instant.now().toString()
        val id = "cat-${UUID.randomUUID()}"
        categoryDao.upsert(CategoryEntity(id = id, name = name.trim(), updated_at = now))
        return id
    }

    fun createShelf(name: String) = viewModelScope.launch(Dispatchers.IO) {
        createShelfAndGetId(name)
    }

    suspend fun createShelfAndGetId(name: String): String {
        val now = Instant.now().toString()
        val id = "shelf-${UUID.randomUUID()}"
        shelfDao.upsert(ShelfEntity(id = id, name = name.trim(), updated_at = now))
        return id
    }

    // ---- 重命名 ----
    fun renameTag(id: String, newName: String) = viewModelScope.launch(Dispatchers.IO) {
        tagDao.rename(id, newName.trim(), Instant.now().toString())
    }

    fun renameCategory(id: String, newName: String) = viewModelScope.launch(Dispatchers.IO) {
        categoryDao.rename(id, newName.trim(), Instant.now().toString())
    }

    fun renameShelf(id: String, newName: String) = viewModelScope.launch(Dispatchers.IO) {
        shelfDao.rename(id, newName.trim(), Instant.now().toString())
    }

    fun updateCategoryTone(id: String, tone: String) = viewModelScope.launch(Dispatchers.IO) {
        categoryDao.updateCoverTone(id, tone.trim(), Instant.now().toString())
    }

    // ---- 软删除 ----
    fun deleteTag(id: String) = viewModelScope.launch(Dispatchers.IO) {
        tagDao.softDelete(id, Instant.now().toString())
    }

    fun deleteCategory(id: String) = viewModelScope.launch(Dispatchers.IO) {
        categoryDao.softDelete(id, Instant.now().toString())
    }

    fun deleteShelf(id: String) = viewModelScope.launch(Dispatchers.IO) {
        shelfDao.softDelete(id, Instant.now().toString())
    }

    // ---- 书籍关联 ----
    fun addTagToBooks(bookIds: List<String>, tagId: String) = viewModelScope.launch(Dispatchers.IO) {
        bookTagDao.upsertAll(bookIds.map { BookTagEntity(book_id = it, tag_id = tagId) })
    }

    fun addTagToBook(bookId: String, tagId: String) = viewModelScope.launch(Dispatchers.IO) {
        bookTagDao.upsert(BookTagEntity(book_id = bookId, tag_id = tagId))
    }

    fun removeTagFromBook(bookId: String, tagId: String) = viewModelScope.launch(Dispatchers.IO) {
        bookTagDao.remove(bookId, tagId)
    }

    fun setCategoryForBooks(bookIds: List<String>, categoryId: String) = viewModelScope.launch(Dispatchers.IO) {
        if (bookIds.isEmpty()) return@launch
        // 原子操作：clear + upsertAll 在同一事务中执行
        bookCategoryDao.replaceForBooks(bookIds, categoryId)
    }

    fun removeCategoryFromBook(bookId: String, categoryId: String) = viewModelScope.launch(Dispatchers.IO) {
        bookCategoryDao.remove(bookId, categoryId)
    }

    fun addBooksToShelf(bookIds: List<String>, shelfId: String) = viewModelScope.launch(Dispatchers.IO) {
        shelfBookDao.upsertAll(bookIds.map { ShelfBookEntity(shelf_id = shelfId, book_id = it) })
    }

    fun removeBookFromShelf(bookId: String, shelfId: String) = viewModelScope.launch(Dispatchers.IO) {
        shelfBookDao.remove(shelfId, bookId)
    }
}
