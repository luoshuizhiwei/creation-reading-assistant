package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.repository.TaxonomyRepository
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
import org.junit.Before
import org.junit.Test

/**
 * TaxonomyViewModel 分类/标签/书单 CRUD 转发测试（全部落到 TaxonomyRepository）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaxonomyViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var repository: TaxonomyRepository
    private lateinit var vm: TaxonomyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        repository = mockk {
            every { observeTags() } returns flowOf(emptyList())
            every { observeCategories() } returns flowOf(emptyList())
            every { observeShelves() } returns flowOf(emptyList())
            coEvery { createTag(any(), any()) } returns "tag-x"
            coEvery { createCategory(any()) } returns "cat-x"
            coEvery { createShelf(any()) } returns "shelf-x"
            coEvery { renameTag(any(), any()) } returns Unit
            coEvery { renameCategory(any(), any()) } returns Unit
            coEvery { renameShelf(any(), any()) } returns Unit
            coEvery { updateCategoryTone(any(), any()) } returns Unit
            coEvery { deleteTag(any()) } returns Unit
            coEvery { deleteCategory(any()) } returns Unit
            coEvery { deleteShelf(any()) } returns Unit
            coEvery { addTagToBooks(any(), any()) } returns Unit
            coEvery { addTagToBook(any(), any()) } returns Unit
            coEvery { removeTagFromBook(any(), any()) } returns Unit
            coEvery { replaceCategoryForBooks(any(), any()) } returns Unit
            coEvery { removeCategoryFromBook(any(), any()) } returns Unit
            coEvery { addBooksToShelf(any(), any()) } returns Unit
            coEvery { removeBookFromShelf(any(), any()) } returns Unit
        }
        vm = TaxonomyViewModel(
            repository = repository,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `createTagAndGetId delegates to repository and returns its id`() = runTest(mainDispatcher.scheduler) {
        val id = vm.createTagAndGetId("悬疑", "book")

        assertEquals("tag-x", id)
        coVerify(exactly = 1) { repository.createTag("悬疑", "book") }
    }

    @Test
    fun `createCategoryAndGetId and createShelfAndGetId delegate`() = runTest(mainDispatcher.scheduler) {
        assertEquals("cat-x", vm.createCategoryAndGetId("科幻"))
        assertEquals("shelf-x", vm.createShelfAndGetId("待读"))

        coVerify(exactly = 1) { repository.createCategory("科幻") }
        coVerify(exactly = 1) { repository.createShelf("待读") }
    }

    @Test
    fun `rename methods and updateCategoryTone delegate`() = runTest(mainDispatcher.scheduler) {
        vm.renameTag("t1", "新标签")
        vm.renameCategory("c1", "新分类")
        vm.renameShelf("s1", "新书单")
        vm.updateCategoryTone("c1", "warm")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.renameTag("t1", "新标签") }
        coVerify(exactly = 1) { repository.renameCategory("c1", "新分类") }
        coVerify(exactly = 1) { repository.renameShelf("s1", "新书单") }
        coVerify(exactly = 1) { repository.updateCategoryTone("c1", "warm") }
    }

    @Test
    fun `delete methods delegate`() = runTest(mainDispatcher.scheduler) {
        vm.deleteTag("t1")
        vm.deleteCategory("c1")
        vm.deleteShelf("s1")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteTag("t1") }
        coVerify(exactly = 1) { repository.deleteCategory("c1") }
        coVerify(exactly = 1) { repository.deleteShelf("s1") }
    }

    @Test
    fun `addTagToBooks and addBooksToShelf delegate`() = runTest(mainDispatcher.scheduler) {
        vm.addTagToBooks(listOf("b1", "b2"), "t9")
        vm.addBooksToShelf(listOf("b1", "b2"), "sh1")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.addTagToBooks(listOf("b1", "b2"), "t9") }
        coVerify(exactly = 1) { repository.addBooksToShelf(listOf("b1", "b2"), "sh1") }
    }

    @Test
    fun `setCategoryForBooks empty list short circuits before repository`() = runTest(mainDispatcher.scheduler) {
        vm.setCategoryForBooks(emptyList(), "c1")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { repository.replaceCategoryForBooks(any(), any()) }
    }

    @Test
    fun `observe ids for books delegate and return repository results`() = runTest(mainDispatcher.scheduler) {
        coEvery { repository.observeTagIdsForBooks(any()) } returns flowOf(listOf("t1"))
        coEvery { repository.observeCategoryIdsForBooks(any()) } returns flowOf(listOf("c1"))
        coEvery { repository.observeShelfIdsForBooks(any()) } returns flowOf(listOf("s1"))

        assertEquals(listOf("t1"), vm.observeTagIdsForBooks(listOf("b1")).first())
        assertEquals(listOf("c1"), vm.observeCategoryIdsForBooks(listOf("b1")).first())
        assertEquals(listOf("s1"), vm.observeShelfIdsForBooks(listOf("b1")).first())
        coVerify(exactly = 1) { repository.observeTagIdsForBooks(listOf("b1")) }
        coVerify(exactly = 1) { repository.observeCategoryIdsForBooks(listOf("b1")) }
        coVerify(exactly = 1) { repository.observeShelfIdsForBooks(listOf("b1")) }
    }

    @Test
    fun `getBookIdsByShelf and getBookIdsByTag delegate`() = runTest(mainDispatcher.scheduler) {
        coEvery { repository.getBookIdsByShelf(any()) } returns listOf("b2", "b3")
        coEvery { repository.getBookIdsByTag(any()) } returns listOf("b1")

        assertEquals(listOf("b2", "b3"), vm.getBookIdsByShelf("sh1"))
        assertEquals(listOf("b1"), vm.getBookIdsByTag("t1"))
    }

    @Test
    fun `remove tag and book from shelf delegate`() = runTest(mainDispatcher.scheduler) {
        vm.removeTagFromBook("b1", "t1")
        vm.removeBookFromShelf("b1", "sh1")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.removeTagFromBook("b1", "t1") }
        coVerify(exactly = 1) { repository.removeBookFromShelf("b1", "sh1") }
    }
}
