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
        val maxOrder = tagDao.getAllActive().maxOfOrNull { it.sort_order } ?: -1
        tagDao.upsert(
            TagEntity(
                id = id,
                name = name.trim(),
                type = type.trim().takeIf { it.isNotBlank() } ?: "book",
                sort_order = maxOrder + 1,
                updated_at = now,
                created_at = now,
            ),
        )
        return id
    }

    suspend fun createCategory(name: String): String {
        val now = Instant.now().toString()
        val id = "cat-${UUID.randomUUID()}"
        val maxOrder = categoryDao.getAllActive().maxOfOrNull { it.sort_order } ?: -1
        categoryDao.upsert(
            CategoryEntity(
                id = id,
                name = name.trim(),
                sort_order = maxOrder + 1,
                updated_at = now,
                created_at = now,
            ),
        )
        return id
    }

    suspend fun createShelf(name: String): String {
        val now = Instant.now().toString()
        val id = "shelf-${UUID.randomUUID()}"
        val maxOrder = shelfDao.getAllActive().maxOfOrNull { it.sort_order } ?: -1
        shelfDao.upsert(
            ShelfEntity(
                id = id,
                name = name.trim(),
                sort_order = maxOrder + 1,
                updated_at = now,
                created_at = now,
            ),
        )
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

    // ---- 排序：仅上移/下移一位，只交换相邻两项的 sort_order（同一事务） ----
    //
    // 设计约束（审核要求）：
    // 1. 只实现上移一位 / 下移一位
    // 2. 每次只交换当前项与相邻项的 sort_order
    // 3. 不做全表 0..N-1 稠密重排
    // 4. 不增加置顶、置底、拖拽
    // 5. 首项上移 / 末项下移安全无操作
    // 6. 不使用 runCatching 静默吞错
    // 7. 两项交换处于同一事务（DAO 的 updateSortOrders 天然是 @Transaction）

    private inline fun <T> List<T>.indexByIdOrThrow(id: String, idOf: (T) -> String, targetName: String): Int {
        val idx = indexOfFirst { idOf(it) == id }
        require(idx >= 0) { "$targetName 找不到 id=$id" }
        return idx
    }

    /** 标签上移一位；已是第一项则安全无操作。 */
    suspend fun moveTagUp(tagId: String) {
        val all = tagDao.getAllActive().sortedWith(compareBy({ it.sort_order }, { it.created_at }))
        val current = all.indexByIdOrThrow(tagId, { it.id }, "moveTagUp")
        if (current == 0) return
        val prev = all[current - 1]
        val self = all[current]
        tagDao.updateSortOrders(
            updates = listOf(
                prev.id to self.sort_order,
                self.id to prev.sort_order,
            ),
            now = Instant.now().toString(),
        )
    }

    /** 标签下移一位；已是最后一项则安全无操作。 */
    suspend fun moveTagDown(tagId: String) {
        val all = tagDao.getAllActive().sortedWith(compareBy({ it.sort_order }, { it.created_at }))
        val current = all.indexByIdOrThrow(tagId, { it.id }, "moveTagDown")
        if (current >= all.size - 1) return
        val next = all[current + 1]
        val self = all[current]
        tagDao.updateSortOrders(
            updates = listOf(
                next.id to self.sort_order,
                self.id to next.sort_order,
            ),
            now = Instant.now().toString(),
        )
    }

    /** 分类上移一位；已是第一项则安全无操作。 */
    suspend fun moveCategoryUp(categoryId: String) {
        val all = categoryDao.getAllActive().sortedWith(compareBy({ it.sort_order }, { it.created_at }))
        val current = all.indexByIdOrThrow(categoryId, { it.id }, "moveCategoryUp")
        if (current == 0) return
        val prev = all[current - 1]
        val self = all[current]
        categoryDao.updateSortOrders(
            updates = listOf(
                prev.id to self.sort_order,
                self.id to prev.sort_order,
            ),
            now = Instant.now().toString(),
        )
    }

    /** 分类下移一位；已是最后一项则安全无操作。 */
    suspend fun moveCategoryDown(categoryId: String) {
        val all = categoryDao.getAllActive().sortedWith(compareBy({ it.sort_order }, { it.created_at }))
        val current = all.indexByIdOrThrow(categoryId, { it.id }, "moveCategoryDown")
        if (current >= all.size - 1) return
        val next = all[current + 1]
        val self = all[current]
        categoryDao.updateSortOrders(
            updates = listOf(
                next.id to self.sort_order,
                self.id to next.sort_order,
            ),
            now = Instant.now().toString(),
        )
    }

    /** 书单上移一位；已是第一项则安全无操作。 */
    suspend fun moveShelfUp(shelfId: String) {
        val all = shelfDao.getAllActive().sortedWith(compareBy({ it.sort_order }, { it.created_at }))
        val current = all.indexByIdOrThrow(shelfId, { it.id }, "moveShelfUp")
        if (current == 0) return
        val prev = all[current - 1]
        val self = all[current]
        shelfDao.updateSortOrders(
            updates = listOf(
                prev.id to self.sort_order,
                self.id to prev.sort_order,
            ),
            now = Instant.now().toString(),
        )
    }

    /** 书单下移一位；已是最后一项则安全无操作。 */
    suspend fun moveShelfDown(shelfId: String) {
        val all = shelfDao.getAllActive().sortedWith(compareBy({ it.sort_order }, { it.created_at }))
        val current = all.indexByIdOrThrow(shelfId, { it.id }, "moveShelfDown")
        if (current >= all.size - 1) return
        val next = all[current + 1]
        val self = all[current]
        shelfDao.updateSortOrders(
            updates = listOf(
                next.id to self.sort_order,
                self.id to next.sort_order,
            ),
            now = Instant.now().toString(),
        )
    }
}
