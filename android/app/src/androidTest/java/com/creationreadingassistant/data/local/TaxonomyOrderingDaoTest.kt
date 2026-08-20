package com.creationreadingassistant.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.data.repository.TaxonomyRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaxonomyOrderingDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: TaxonomyRepository

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = TaxonomyRepository(
            database.tagDao(),
            database.categoryDao(),
            database.shelfDao(),
            database.bookTagDao(),
            database.bookCategoryDao(),
            database.shelfBookDao(),
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun adjacent_moves_change_persisted_order_for_tags_categories_and_shelves() = runTest {
        database.tagDao().upsert(TagEntity("tag-a", "A", sort_order = 0, created_at = "t1", updated_at = "t1"))
        database.tagDao().upsert(TagEntity("tag-b", "B", sort_order = 1, created_at = "t2", updated_at = "t2"))
        database.categoryDao().upsert(CategoryEntity("category-a", "A", sort_order = 0, created_at = "t1", updated_at = "t1"))
        database.categoryDao().upsert(CategoryEntity("category-b", "B", sort_order = 1, created_at = "t2", updated_at = "t2"))
        database.shelfDao().upsert(ShelfEntity("shelf-a", "A", sort_order = 0, created_at = "t1", updated_at = "t1"))
        database.shelfDao().upsert(ShelfEntity("shelf-b", "B", sort_order = 1, created_at = "t2", updated_at = "t2"))

        repository.moveTagUp("tag-b")
        repository.moveCategoryDown("category-a")
        repository.moveShelfUp("shelf-b")

        assertEquals(listOf("tag-b", "tag-a"), database.tagDao().getAllActive().map { it.id })
        assertEquals(listOf("category-b", "category-a"), database.categoryDao().getAllActive().map { it.id })
        assertEquals(listOf("shelf-b", "shelf-a"), database.shelfDao().getAllActive().map { it.id })
    }
}
