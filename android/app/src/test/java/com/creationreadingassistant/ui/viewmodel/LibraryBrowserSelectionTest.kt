package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import android.net.Uri
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.feature.library.LibraryEntry
import com.creationreadingassistant.feature.library.LibraryListing
import com.creationreadingassistant.feature.library.LibraryListingIssue
import com.creationreadingassistant.feature.library.LibraryRoot
import com.creationreadingassistant.feature.library.LibraryRootStore
import com.creationreadingassistant.feature.library.LibrarySortMode
import com.creationreadingassistant.feature.library.LibrarySource
import com.creationreadingassistant.feature.library.LibrarySourceIndex
import com.creationreadingassistant.feature.library.SmartBookRecognizer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryBrowserSelectionTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `select all after search imports only matching files`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = buildViewModel()
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.setQuery("match")
        viewModel.selectAllVisible()

        assertEquals(
            "全选必须只选择当前搜索结果，不能把隐藏文件交给导入器",
            listOf("content://books.example/document/match-one.txt", "content://books.example/document/match-two.epub"),
            viewModel.selectedUris().map { it.toString() },
        )
    }

    @Test
    fun `inverting a search selection never selects hidden files`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = buildViewModel()
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.setQuery("match")
        viewModel.toggleFile("primary:Books/match-one.txt")
        viewModel.invertSelection()

        assertEquals(
            "反选只能在当前搜索结果中翻转，隐藏文件不得被选中",
            listOf("content://books.example/document/match-two.epub"),
            viewModel.selectedUris().map { it.toString() },
        )
    }

    @Test
    fun `changing query clears selections from the previous result set`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = buildViewModel()
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.setQuery("match")
        viewModel.selectAllVisible()
        viewModel.setQuery("hidden")
        advanceUntilIdle()

        assertEquals(
            "更换搜索词后，旧结果的隐藏选择必须失效，避免误导入",
            emptyList<String>(),
            viewModel.selectedUris().map { it.toString() },
        )
        assertEquals(emptySet<String>(), viewModel.uiState.value.selection)
    }

    @Test
    fun `import boundary excludes a stale hidden selection`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = buildViewModel()
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.setQuery("match")
        // 模拟列表刷新等边界条件遗留的内部选择；真实 UI 不会提供这项点击入口。
        viewModel.toggleFile("primary:Books/hidden-book.txt")

        assertEquals(
            "即使内部残留隐藏文件，最终导入边界也必须只交出当前可见 URI",
            emptyList<String>(),
            viewModel.selectedUris().map { it.toString() },
        )
    }

    private fun buildViewModel(): LibraryBrowserViewModel {
        val treeUri = fakeUri("content://books.example/tree/primary%3ABooks")
        val configuredRoot = LibraryRoot(
            treeUri = treeUri,
            displayName = "书籍",
            lastLocationDocumentId = null,
            sortMode = LibrarySortMode.NAME,
        )
        val entries = listOf(
            entry("match-one.txt"),
            entry("match-two.epub"),
            entry("hidden-book.txt"),
        )
        val rootStore = mockk<LibraryRootStore>(relaxed = true) {
            every { root } returns MutableStateFlow(configuredRoot as LibraryRoot?)
        }
        val librarySource = mockk<LibrarySource> {
            coEvery { list(any(), any()) } returns LibraryListing(
                documentId = "primary:Books",
                entries = entries,
                truncated = false,
                issue = LibraryListingIssue.NONE,
            )
        }
        return LibraryBrowserViewModel(
            context = mockk<Context>(relaxed = true),
            rootStore = rootStore,
            librarySource = librarySource,
            recognizer = mockk<SmartBookRecognizer>(relaxed = true),
            sourceIndex = mockk<LibrarySourceIndex>(relaxed = true) {
                coEvery { snapshot() } returns emptyList()
                coEvery { markObserved(any(), any(), any(), any()) } returns Unit
            },
            bookRepository = mockk<BookRepository>(relaxed = true) {
                every { observeBooks() } returns flowOf(emptyList())
            },
        )
    }

    private fun entry(name: String): LibraryEntry = LibraryEntry(
        documentId = "primary:Books/$name",
        displayName = name,
        mimeType = "text/plain",
        sizeBytes = 1,
        lastModifiedMillis = 1,
        isDirectory = false,
        uri = fakeUri("content://books.example/document/$name"),
    )

    private fun fakeUri(value: String): Uri {
        val uri = mockk<Uri>()
        every { uri.toString() } returns value
        every { uri.scheme } returns "content"
        return uri
    }
}
