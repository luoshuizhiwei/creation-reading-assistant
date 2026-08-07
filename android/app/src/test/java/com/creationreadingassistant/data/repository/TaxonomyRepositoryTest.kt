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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TaxonomyRepository 分类/标签/书单 CRUD 行为测试：
 * - 创建时 trim 名称、生成带前缀的 UUID、type 缺省为 "book"
 * - 空列表短路（不触碰 DAO）
 * - 重命名/删除/关联委托
 */
class TaxonomyRepositoryTest {

    private lateinit var tagDao: TagDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var shelfDao: ShelfDao
    private lateinit var bookTagDao: BookTagDao
    private lateinit var bookCategoryDao: BookCategoryDao
    private lateinit var shelfBookDao: ShelfBookDao
    private lateinit var repo: TaxonomyRepository

    @Before
    fun setUp() {
        tagDao = mockk {
            every { observeAllActive() } returns flowOf(emptyList())
            coEvery { upsert(any()) } returns Unit
            coEvery { rename(any(), any(), any()) } returns Unit
            coEvery { softDelete(any(), any()) } returns Unit
        }
        categoryDao = mockk {
            every { observeAllActive() } returns flowOf(emptyList())
            coEvery { upsert(any()) } returns Unit
            coEvery { rename(any(), any(), any()) } returns Unit
            coEvery { softDelete(any(), any()) } returns Unit
            coEvery { updateCoverTone(any(), any(), any()) } returns Unit
        }
        shelfDao = mockk {
            every { observeAllActive() } returns flowOf(emptyList())
            coEvery { upsert(any()) } returns Unit
            coEvery { rename(any(), any(), any()) } returns Unit
            coEvery { softDelete(any(), any()) } returns Unit
        }
        bookTagDao = mockk {
            coEvery { upsert(any()) } returns Unit
            coEvery { upsertAll(any()) } returns Unit
            coEvery { remove(any(), any()) } returns Unit
            coEvery { getBookIds(any()) } returns emptyList()
        }
        bookCategoryDao = mockk {
            coEvery { replaceForBooks(any(), any()) } returns Unit
            coEvery { remove(any(), any()) } returns Unit
        }
        shelfBookDao = mockk {
            coEvery { upsertAll(any()) } returns Unit
            coEvery { remove(any(), any()) } returns Unit
            coEvery { getBookIds(any()) } returns emptyList()
        }
        repo = TaxonomyRepository(tagDao, categoryDao, shelfDao, bookTagDao, bookCategoryDao, shelfBookDao)
    }

    @Test
    fun `createTag trims name and uses tag prefix id`() = runTest {
        val id = repo.createTag("  悬疑  ", "  book ")

        assertTrue(id.startsWith("tag-"))
        val saved = slot<TagEntity>()
        coVerify(exactly = 1) { tagDao.upsert(capture(saved)) }
        assertEquals("悬疑", saved.captured.name)
        assertEquals("book", saved.captured.type)
        assertEquals(id, saved.captured.id)
    }

    @Test
    fun `createTag defaults blank type to book`() = runTest {
        repo.createTag("  悬疑  ")

        val saved = slot<TagEntity>()
        coVerify(exactly = 1) { tagDao.upsert(capture(saved)) }
        assertEquals("book", saved.captured.type)
    }

    @Test
    fun `createCategory and createShelf trim names`() = runTest {
        val catId = repo.createCategory("  科幻  ")
        val shelfId = repo.createShelf("  待读  ")

        assertTrue(catId.startsWith("cat-"))
        assertTrue(shelfId.startsWith("shelf-"))
        val cat = slot<CategoryEntity>()
        val shelf = slot<ShelfEntity>()
        coVerify(exactly = 1) { categoryDao.upsert(capture(cat)) }
        coVerify(exactly = 1) { shelfDao.upsert(capture(shelf)) }
        assertEquals("科幻", cat.captured.name)
        assertEquals("待读", shelf.captured.name)
    }

    @Test
    fun `rename and tone update trim and pass non-empty timestamps`() = runTest {
        repo.renameTag("t1", "  新标签  ")
        repo.renameCategory("c1", "  新分类  ")
        repo.renameShelf("s1", "  新书单  ")
        repo.updateCategoryTone("c1", "  warm  ")

        coVerify(exactly = 1) { tagDao.rename("t1", "新标签", any()) }
        coVerify(exactly = 1) { categoryDao.rename("c1", "新分类", any()) }
        coVerify(exactly = 1) { shelfDao.rename("s1", "新书单", any()) }
        coVerify(exactly = 1) { categoryDao.updateCoverTone("c1", "warm", any()) }
    }

    @Test
    fun `delete methods soft delete with timestamps`() = runTest {
        repo.deleteTag("t1")
        repo.deleteCategory("c1")
        repo.deleteShelf("s1")

        coVerify(exactly = 1) { tagDao.softDelete("t1", any()) }
        coVerify(exactly = 1) { categoryDao.softDelete("c1", any()) }
        coVerify(exactly = 1) { shelfDao.softDelete("s1", any()) }
    }

    @Test
    fun `addTagToBooks maps to book tag entities`() = runTest {
        repo.addTagToBooks(listOf("b1", "b2"), "t9")

        val refs = slot<List<BookTagEntity>>()
        coVerify(exactly = 1) { bookTagDao.upsertAll(capture(refs)) }
        assertEquals(listOf(BookTagEntity("b1", "t9"), BookTagEntity("b2", "t9")), refs.captured)
    }

    @Test
    fun `addBooksToShelf maps to shelf book entities`() = runTest {
        repo.addBooksToShelf(listOf("b1", "b2"), "sh1")

        val refs = slot<List<ShelfBookEntity>>()
        coVerify(exactly = 1) { shelfBookDao.upsertAll(capture(refs)) }
        assertEquals(
            listOf(ShelfBookEntity("sh1", "b1"), ShelfBookEntity("sh1", "b2")),
            refs.captured,
        )
    }

    @Test
    fun `observe ids for books short circuit on empty list`() = runTest {
        assertEquals(emptyList<String>(), repo.observeTagIdsForBooks(emptyList()).first())
        assertEquals(emptyList<String>(), repo.observeCategoryIdsForBooks(emptyList()).first())
        assertEquals(emptyList<String>(), repo.observeShelfIdsForBooks(emptyList()).first())
        coVerify(exactly = 0) { bookTagDao.observeTagIdsForBooks(any()) }
        coVerify(exactly = 0) { bookCategoryDao.observeCategoryIdsForBooks(any()) }
        coVerify(exactly = 0) { shelfBookDao.observeShelfIdsForBooks(any()) }
    }

    @Test
    fun `remove tag and book from shelf delegate`() = runTest {
        repo.removeTagFromBook("b1", "t1")
        repo.removeBookFromShelf("b1", "sh1")

        coVerify(exactly = 1) { bookTagDao.remove("b1", "t1") }
        coVerify(exactly = 1) { shelfBookDao.remove("sh1", "b1") }
    }
}
