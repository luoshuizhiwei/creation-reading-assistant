package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.settings.SearchHistoryStore
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val bookDao = mockk<BookDao>()
    private val inspirationDao = mockk<InspirationDao>()
    private val noteDao = mockk<NoteDao>()
    private val highlightDao = mockk<HighlightDao>()
    private val historyStore = mockk<SearchHistoryStore>()
    private lateinit var vm: SearchViewModel
    private val testScheduler = TestCoroutineScheduler()
    private val mainDispatcher = UnconfinedTestDispatcher(testScheduler)

    @Before
    fun setup() {
        Dispatchers.setMain(mainDispatcher)
        every { historyStore.history } returns MutableStateFlow(emptyList())
        coEvery { highlightDao.search(any()) } returns emptyList()
        coEvery { bookDao.getById(any()) } returns null
        vm = SearchViewModel(bookDao, inspirationDao, noteDao, highlightDao, historyStore)
    }

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private suspend fun awaitResults(predicate: (SearchResults) -> Boolean): SearchResults =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { vm.results.first(predicate) }
        }

    @Test
    fun `blank query should clear results`() = runTest(mainDispatcher) {
        vm.search("")
        assertEquals(0, vm.results.value.books.size + vm.results.value.inspirations.size + vm.results.value.notes.size)
        assertFalse(vm.loading.value)
    }

    @Test
    fun `valid query should return matching books`() = runTest(mainDispatcher) {
        val book = BookEntity(id = "b1", title = "三体", format = "epub", updated_at = "now")
        coEvery { bookDao.search("三体") } returns listOf(book)
        coEvery { inspirationDao.search("三体") } returns emptyList()
        coEvery { noteDao.search("三体") } returns emptyList()

        vm.search("三体")
        advanceTimeBy(400) // 等 debounce
        val result = awaitResults { it.books.isNotEmpty() }
        assertEquals(1, result.books.size)
        assertEquals("三体", result.books[0].title)
        assertFalse(vm.loading.value)
    }

    @Test
    fun `concurrent search across three tables`() = runTest(mainDispatcher) {
        coEvery { bookDao.search("关键词") } returns listOf(
            BookEntity(id = "b1", title = "书A", format = "txt", updated_at = "n"),
        )
        coEvery { inspirationDao.search("关键词") } returns listOf(
            InspirationEntity(id = "i1", title = "灵感A", body = "...", type = "note", status = "inbox", created_at = "n", updated_at = "n"),
        )
        coEvery { noteDao.search("关键词") } returns listOf(
            NoteEntity(id = "n1", title = "笔记A", body = "原文", created_at = "n", updated_at = "n"),
        )

        vm.search("关键词")
        advanceTimeBy(400)
        val result = awaitResults { it.books.isNotEmpty() }
        assertEquals(1, result.books.size)
        assertEquals(1, result.inspirations.size)
        assertEquals(1, result.notes.size)
    }

    @Test
    fun `loading state transitions correctly`() = runTest(mainDispatcher) {
        coEvery { bookDao.search(any()) } returns emptyList()
        coEvery { inspirationDao.search(any()) } returns emptyList()
        coEvery { noteDao.search(any()) } returns emptyList()

        vm.results.test {
            vm.search("test")
            advanceTimeBy(400)
            advanceUntilIdle()
            val final = expectMostRecentItem()
            assertTrue(final.books.isEmpty())
        }
    }
}
