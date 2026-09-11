package com.creationreadingassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.creationreadingassistant.data.local.entity.BookCategoryEntity
import com.creationreadingassistant.data.local.entity.BookTagEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.ShelfBookEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tag: TagEntity)

    @Query("SELECT * FROM tags WHERE deleted_at IS NULL ORDER BY sort_order ASC, created_at ASC")
    fun observeAllActive(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE deleted_at IS NULL ORDER BY sort_order ASC, created_at ASC")
    suspend fun getAllActive(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id AND deleted_at IS NULL LIMIT 1")
    suspend fun getById(id: String): TagEntity?

    @Query("UPDATE tags SET name = :newName, updated_at = :now WHERE id = :id")
    suspend fun rename(id: String, newName: String, now: String)

    @Query("UPDATE tags SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: String)

    @Query("UPDATE tags SET sort_order = :sortOrder, updated_at = :now WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int, now: String)

    /** 拖动排序后批量落库；调用方保证 ids 与 sortOrders 长度一致。 */
    @Transaction
    suspend fun updateSortOrders(updates: List<Pair<String, Int>>, now: String) {
        updates.forEach { (id, order) -> updateSortOrder(id, order, now) }
    }

    @Query("SELECT * FROM tags WHERE id IN (:ids)")
    suspend fun getByIds(ids: Collection<String>): List<TagEntity>
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE deleted_at IS NULL ORDER BY sort_order ASC, created_at ASC")
    fun observeAllActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deleted_at IS NULL ORDER BY sort_order ASC, created_at ASC")
    suspend fun getAllActive(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id AND deleted_at IS NULL LIMIT 1")
    suspend fun getById(id: String): CategoryEntity?

    @Query("UPDATE categories SET name = :newName, updated_at = :now WHERE id = :id")
    suspend fun rename(id: String, newName: String, now: String)

    @Query("UPDATE categories SET cover_tone = :tone, updated_at = :now WHERE id = :id")
    suspend fun updateCoverTone(id: String, tone: String, now: String)

    @Query("UPDATE categories SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: String)

    @Query("UPDATE categories SET sort_order = :sortOrder, updated_at = :now WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int, now: String)

    /** 拖动排序后批量落库；调用方保证 ids 与 sortOrders 长度一致。 */
    @Transaction
    suspend fun updateSortOrders(updates: List<Pair<String, Int>>, now: String) {
        updates.forEach { (id, order) -> updateSortOrder(id, order, now) }
    }

    @Query("SELECT * FROM categories WHERE id IN (:ids)")
    suspend fun getByIds(ids: Collection<String>): List<CategoryEntity>
}

@Dao
interface ShelfDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(shelf: ShelfEntity)

    @Query("SELECT * FROM shelves WHERE deleted_at IS NULL ORDER BY sort_order ASC, created_at ASC")
    fun observeAllActive(): Flow<List<ShelfEntity>>

    @Query("SELECT * FROM shelves WHERE deleted_at IS NULL ORDER BY sort_order ASC, created_at ASC")
    suspend fun getAllActive(): List<ShelfEntity>

    @Query("SELECT * FROM shelves WHERE id = :id AND deleted_at IS NULL LIMIT 1")
    suspend fun getById(id: String): ShelfEntity?

    @Query("UPDATE shelves SET name = :newName, updated_at = :now WHERE id = :id")
    suspend fun rename(id: String, newName: String, now: String)

    @Query("UPDATE shelves SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: String)

    @Query("UPDATE shelves SET sort_order = :sortOrder, updated_at = :now WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int, now: String)

    /** 拖动排序后批量落库；调用方保证 ids 与 sortOrders 长度一致。 */
    @Transaction
    suspend fun updateSortOrders(updates: List<Pair<String, Int>>, now: String) {
        updates.forEach { (id, order) -> updateSortOrder(id, order, now) }
    }

    @Query("SELECT * FROM shelves WHERE id IN (:ids)")
    suspend fun getByIds(ids: Collection<String>): List<ShelfEntity>
}

@Dao
interface BookTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ref: BookTagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(refs: List<BookTagEntity>)

    @Query("DELETE FROM book_tag WHERE book_id = :bookId AND tag_id = :tagId")
    suspend fun remove(bookId: String, tagId: String)

    @Query("DELETE FROM book_tag WHERE book_id = :bookId")
    suspend fun clearByBook(bookId: String)

    @Query("SELECT tag_id FROM book_tag WHERE book_id = :bookId")
    fun observeTagIds(bookId: String): Flow<List<String>>

    @Query("SELECT tag_id FROM book_tag WHERE book_id IN (:bookIds)")
    fun observeTagIdsForBooks(bookIds: List<String>): Flow<List<String>>

    @Query("SELECT book_id FROM book_tag WHERE tag_id = :tagId")
    suspend fun getBookIds(tagId: String): List<String>

    @Query("SELECT * FROM book_tag")
    suspend fun getAllActive(): List<BookTagEntity>

    /** 删除/撤销快照：连接表没有软删除列，只返回目标书籍的现存关联。 */
    @Query("SELECT * FROM book_tag WHERE book_id IN (:bookIds)")
    suspend fun getByBookIds(bookIds: Collection<String>): List<BookTagEntity>

    /** 备份恢复用：清空整张关联表后重建。 */
    @Query("DELETE FROM book_tag")
    suspend fun clearAll()
}

@Dao
interface BookCategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ref: BookCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(refs: List<BookCategoryEntity>)

    @Query("DELETE FROM book_category WHERE book_id = :bookId AND category_id = :categoryId")
    suspend fun remove(bookId: String, categoryId: String)

    @Query("DELETE FROM book_category WHERE book_id = :bookId")
    suspend fun clearByBook(bookId: String)

    /** 原子操作：先清除指定书籍的分类关联，再批量插入新关联。 */
    @Transaction
    suspend fun replaceForBooks(bookIds: List<String>, categoryId: String) {
        bookIds.forEach { clearByBook(it) }
        upsertAll(bookIds.map { BookCategoryEntity(book_id = it, category_id = categoryId) })
    }

    @Query("SELECT category_id FROM book_category WHERE book_id = :bookId")
    fun observeCategoryIds(bookId: String): Flow<List<String>>

    @Query("SELECT category_id FROM book_category WHERE book_id IN (:bookIds)")
    fun observeCategoryIdsForBooks(bookIds: List<String>): Flow<List<String>>

    @Query("SELECT book_id FROM book_category WHERE category_id = :categoryId")
    suspend fun getBookIds(categoryId: String): List<String>

    @Query("SELECT * FROM book_category")
    suspend fun getAllActive(): List<BookCategoryEntity>

    /** 删除/撤销快照：连接表没有软删除列，只返回目标书籍的现存关联。 */
    @Query("SELECT * FROM book_category WHERE book_id IN (:bookIds)")
    suspend fun getByBookIds(bookIds: Collection<String>): List<BookCategoryEntity>

    /** 备份恢复用：清空整张关联表后重建。 */
    @Query("DELETE FROM book_category")
    suspend fun clearAll()
}

@Dao
interface ShelfBookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ref: ShelfBookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(refs: List<ShelfBookEntity>)

    @Query("DELETE FROM shelf_book WHERE shelf_id = :shelfId AND book_id = :bookId")
    suspend fun remove(shelfId: String, bookId: String)

    @Query("DELETE FROM shelf_book WHERE book_id = :bookId")
    suspend fun clearByBook(bookId: String)

    @Query("SELECT shelf_id FROM shelf_book WHERE book_id = :bookId")
    fun observeShelfIds(bookId: String): Flow<List<String>>

    @Query("SELECT shelf_id FROM shelf_book WHERE book_id IN (:bookIds)")
    fun observeShelfIdsForBooks(bookIds: List<String>): Flow<List<String>>

    @Query("SELECT book_id FROM shelf_book WHERE shelf_id = :shelfId")
    suspend fun getBookIds(shelfId: String): List<String>

    @Query("SELECT * FROM shelf_book")
    suspend fun getAllActive(): List<ShelfBookEntity>

    /** 删除/撤销快照：连接表没有软删除列，只返回目标书籍的现存关联。 */
    @Query("SELECT * FROM shelf_book WHERE book_id IN (:bookIds)")
    suspend fun getByBookIds(bookIds: Collection<String>): List<ShelfBookEntity>

    /** 备份恢复用：清空整张关联表后重建。 */
    @Query("DELETE FROM shelf_book")
    suspend fun clearAll()
}
