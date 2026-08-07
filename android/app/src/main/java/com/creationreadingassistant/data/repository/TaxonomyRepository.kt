package com.creationreadingassistant.data.repository

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
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 分类仓储 —— 封装标签 / 分类 / 书架三类分类维度的观察、CRUD 与书籍关联。
 * 由书架页 / 阅读器 ViewModel 使用，ViewModel 不再直接依赖 DAO（APP_MODULE_PLAN §7.2）。
 */
@Singleton
class TaxonomyRepository @Inject constructor(
    private val tagDao: TagDao,
    private val categoryDao: CategoryDao,
    private val shelfDao: ShelfDao,
    private val bookTagDao: BookTagDao,
    private val bookCategoryDao: BookCategoryDao,
    private val shelfBookDao: ShelfBookDao,
) {
    // ---- 全部列表 ----
    fun observeTags(): Flow<List<TagEntity>> = tagDao.observeAllActive()

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAllActive()

    fun observeShelves(): Flow<List<ShelfEntity>> = shelfDao.observeAllActive()

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
    suspend fun upsertTag(entity: TagEntity) = tagDao.upsert(entity)

    suspend fun upsertCategory(entity: CategoryEntity) = categoryDao.upsert(entity)

    suspend fun createTag(name: String, type: String = "book"): String {
        val now = Instant.now().toString()
        val id = "tag-${UUID.randomUUID()}"
        tagDao.upsert(TagEntity(id = id, name = name.trim(), type = type.trim().takeIf { it.isNotBlank() } ?: "book", updated_at = now))
        return id
    }

    suspend fun createCategory(name: String): String {
        val now = Instant.now().toString()
        val id = "cat-${UUID.randomUUID()}"
        categoryDao.upsert(CategoryEntity(id = id, name = name.trim(), updated_at = now))
        return id
    }

    suspend fun createShelf(name: String): String {
        val now = Instant.now().toString()
        val id = "shelf-${UUID.randomUUID()}"
        shelfDao.upsert(ShelfEntity(id = id, name = name.trim(), updated_at = now))
        return id
    }

    // ---- 重命名 ----
    suspend fun renameTag(id: String, newName: String) =
        tagDao.rename(id, newName.trim(), Instant.now().toString())

    suspend fun renameCategory(id: String, newName: String) =
        categoryDao.rename(id, newName.trim(), Instant.now().toString())

    suspend fun renameShelf(id: String, newName: String) =
        shelfDao.rename(id, newName.trim(), Instant.now().toString())

    suspend fun updateCategoryTone(id: String, tone: String) =
        categoryDao.updateCoverTone(id, tone.trim(), Instant.now().toString())

    // ---- 软删除 ----
    suspend fun deleteTag(id: String) = tagDao.softDelete(id, Instant.now().toString())

    suspend fun deleteCategory(id: String) = categoryDao.softDelete(id, Instant.now().toString())

    suspend fun deleteShelf(id: String) = shelfDao.softDelete(id, Instant.now().toString())

    // ---- 书籍关联 ----
    suspend fun addTagToBooks(bookIds: List<String>, tagId: String) =
        bookTagDao.upsertAll(bookIds.map { BookTagEntity(book_id = it, tag_id = tagId) })

    suspend fun addTagToBook(bookId: String, tagId: String) =
        bookTagDao.upsert(BookTagEntity(book_id = bookId, tag_id = tagId))

    suspend fun removeTagFromBook(bookId: String, tagId: String) = bookTagDao.remove(bookId, tagId)

    /** 原子操作：clear + upsertAll 在同一事务中执行。 */
    suspend fun replaceCategoryForBooks(bookIds: List<String>, categoryId: String) =
        bookCategoryDao.replaceForBooks(bookIds, categoryId)

    suspend fun removeCategoryFromBook(bookId: String, categoryId: String) =
        bookCategoryDao.remove(bookId, categoryId)

    suspend fun addBooksToShelf(bookIds: List<String>, shelfId: String) =
        shelfBookDao.upsertAll(bookIds.map { ShelfBookEntity(shelf_id = shelfId, book_id = it) })

    suspend fun removeBookFromShelf(bookId: String, shelfId: String) =
        shelfBookDao.remove(shelfId, bookId)
}
