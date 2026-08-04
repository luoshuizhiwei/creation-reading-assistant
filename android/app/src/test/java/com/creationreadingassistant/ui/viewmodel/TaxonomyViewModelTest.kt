package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.local.dao.BookCategoryDao
import com.creationreadingassistant.data.local.dao.BookTagDao
import com.creationreadingassistant.data.local.dao.CategoryDao
import com.creationreadingassistant.data.local.dao.ShelfBookDao
import com.creationreadingassistant.data.local.dao.ShelfDao
import com.creationreadingassistant.data.local.dao.TagDao
import com.creationreadingassistant.data.local.entity.BookCategoryEntity
import com.creationreadingassistant.data.local.entity.BookTagEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.ShelfBookEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TaxonomyViewModel 分类/标签/书单 CRUD 委托与空列表短路测试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaxonomyViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var tagDao: TagDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var shelfDao: ShelfDao
    private lateinit var bookTagDao: BookTagDao
    private lateinit var bookCategoryDao: BookCategoryDao
    private lateinit var shelfBookDao: ShelfBookDao
    private lateinit var vm: TaxonomyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
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
            coEvery { upsertAll(any()) } returns Unit
            coEvery { remove(any(), any()) } returns Unit
            coEvery { getBookIds(any()) } returns listOf("b1")
        }
        bookCategoryDao = mockk {
            coEvery { replaceForBooks(any(), any()) } returns Unit
        }
        shelfBookDao = mockk {
            coEvery { upsertAll(any()) } returns Unit
            coEvery { getBookIds(any()) } returns listOf("b2", "b3")
        }
        vm = TaxonomyViewModel(
            tagDao = tagDao,
            categoryDao = categoryDao,
            shelfDao = shelfDao,
            bookTagDao = bookTagDao,
            bookCategoryDao = bookCategoryDao,
            shelfBookDao = shelfBookDao,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `createTagAndGetId trims name and uses tag prefix id`() = runTest(mainDispatcher.scheduler) {
        val id = vm.createTagAndGetId("  悬疑  ", "  book ")

        assertTrue(id.startsWith("tag-"))
        val saved = io.mockk.slot<TagEntity>()
        coVerify(exactly = 1) { tagDao.upsert(capture(saved)) }
        assertEquals("悬疑", saved.captured.name)
        assertEquals("book", saved.captured.type)
        assertEquals(id, saved.captured.id)
    }

    @Test
    fun `createCategoryAndGetId and createShelfAndGetId trim names`() = runTest(mainDispatcher.scheduler) {
        val catId = vm.createCategoryAndGetId("  科幻  ")
        val shelfId = vm.createShelfAndGetId("  待读  ")

        assertTrue(catId.startsWith("cat-"))
        assertTrue(shelfId.startsWith("shelf-"))
        val cat = io.mockk.slot<CategoryEntity>()
        val shelf = io.mockk.slot<ShelfEntity>()
        coVerify(exactly = 1) { categoryDao.upsert(capture(cat)) }
        coVerify(exactly = 1) { shelfDao.upsert(capture(shelf)) }
        assertEquals("科幻", cat.captured.name)
        assertEquals("待读", shelf.captured.name)
    }

    @Test
    fun `rename methods trim names and pass non-empty timestamps`() = runTest(mainDispatcher.scheduler) {
        vm.renameTag("t1", "  新标签  ")
        vm.renameCategory("c1", "  新分类  ")
        vm.renameShelf("s1", "  新书单  ")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { tagDao.rename("t1", "新标签", any()) }
        coVerify(exactly = 1) { categoryDao.rename("c1", "新分类", any()) }
        coVerify(exactly = 1) { shelfDao.rename("s1", "新书单", any()) }
    }

    @Test
    fun `updateCategoryTone trims tone value`() = runTest(mainDispatcher.scheduler) {
        vm.updateCategoryTone("c1", "  warm  ")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { categoryDao.updateCoverTone("c1", "warm", any()) }
    }

    @Test
    fun `delete methods soft delete with timestamps`() = runTest(mainDispatcher.scheduler) {
        vm.deleteTag("t1")
        vm.deleteCategory("c1")
        vm.deleteShelf("s1")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { tagDao.softDelete("t1", any()) }
        coVerify(exactly = 1) { categoryDao.softDelete("c1", any()) }
        coVerify(exactly = 1) { shelfDao.softDelete("s1", any()) }
    }

    @Test
    fun `addTagToBooks maps to book tag entities`() = runTest(mainDispatcher.scheduler) {
        vm.addTagToBooks(listOf("b1", "b2"), "t9")
        testScheduler.advanceUntilIdle()

        val refs = io.mockk.slot<List<BookTagEntity>>()
        coVerify(exactly = 1) { bookTagDao.upsertAll(capture(refs)) }
        assertEquals(listOf(BookTagEntity("b1", "t9"), BookTagEntity("b2", "t9")), refs.captured)
    }

    @Test
    fun `addBooksToShelf maps to shelf book entities`() = runTest(mainDispatcher.scheduler) {
        vm.addBooksToShelf(listOf("b1", "b2"), "sh1")
        testScheduler.advanceUntilIdle()

        val refs = io.mockk.slot<List<ShelfBookEntity>>()
        coVerify(exactly = 1) { shelfBookDao.upsertAll(capture(refs)) }
        assertEquals(
            listOf(ShelfBookEntity("sh1", "b1"), ShelfBookEntity("sh1", "b2")),
            refs.captured,
        )
    }

    @Test
    fun `setCategoryForBooks empty list short circuits`() = runTest(mainDispatcher.scheduler) {
        vm.setCategoryForBooks(emptyList(), "c1")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { bookCategoryDao.replaceForBooks(any(), any()) }
    }

    @Test
    fun `observe ids for books short circuit on empty list`() = runTest(mainDispatcher.scheduler) {
        assertEquals(emptyList<String>(), vm.observeTagIdsForBooks(emptyList()).first())
        assertEquals(emptyList<String>(), vm.observeCategoryIdsForBooks(emptyList()).first())
        assertEquals(emptyList<String>(), vm.observeShelfIdsForBooks(emptyList()).first())
        coVerify(exactly = 0) { bookTagDao.observeTagIdsForBooks(any()) }
        coVerify(exactly = 0) { bookCategoryDao.observeCategoryIdsForBooks(any()) }
        coVerify(exactly = 0) { shelfBookDao.observeShelfIdsForBooks(any()) }
    }

    @Test
    fun `getBookIdsByShelf delegates to dao`() = runTest(mainDispatcher.scheduler) {
        assertEquals(listOf("b2", "b3"), vm.getBookIdsByShelf("sh1"))
        assertEquals(listOf("b1"), vm.getBookIdsByTag("t1"))
    }

    @Test
    fun `remove tag and book from shelf delegate`() = runTest(mainDispatcher.scheduler) {
        coEvery { shelfBookDao.remove(any(), any()) } returns Unit
        vm.removeTagFromBook("b1", "t1")
        vm.removeBookFromShelf("b1", "sh1")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { bookTagDao.remove("b1", "t1") }
        coVerify(exactly = 1) { shelfBookDao.remove("sh1", "b1") }
    }
}
