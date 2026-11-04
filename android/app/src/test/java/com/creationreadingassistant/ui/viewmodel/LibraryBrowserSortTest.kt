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

/**
 * 排序切换回归：用户选了排序后，**本次重列**必须用新规则。
 *
 * 背景（修复前的真实缺陷）：`setSortMode()` 写完 DataStore 立刻回读
 * `rootStore.root.value`，而该 StateFlow 由 ApplicationScope（IO）异步发射，
 * 写入返回时它的值往往仍是旧的 —— 于是重列继续按旧排序，用户看到「点了排序
 * 但列表没变」。这里用「root 流故意滞后」的假 store 复现该时序：
 * `root` 的 StateFlow 始终只发射旧排序，只有 `setSortMode` 显式传参才能让
 * 本次列目录拿到新规则。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryBrowserSortTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `switching sort mode re-lists with the newly selected mode`() = runTest {
        // Main 必须与 runTest 共用同一个 scheduler，否则 advanceUntilIdle 推不动
        // viewModelScope 上的协程，uiState 会停在初始值。
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        val treeUri = fakeUri("content://books.example/tree/primary%3ABooks")
        // root 流固定在 NAME：模拟 DataStore 写入尚未经 StateFlow 发射的时序窗口。
        val staleRoot = LibraryRoot(
            treeUri = treeUri,
            displayName = "书籍",
            lastLocationDocumentId = null,
            sortMode = LibrarySortMode.NAME,
        )
        val rootFlow = MutableStateFlow<LibraryRoot?>(staleRoot)

        val rootStore = mockk<LibraryRootStore>(relaxed = true) {
            every { root } returns rootFlow
            coEvery { setSortMode(any()) } returns Unit
        }

        val receivedModes = mutableListOf<LibrarySortMode>()
        val librarySource = mockk<LibrarySource> {
            coEvery { list(any(), any()) } answers {
                receivedModes += firstArg<LibraryRoot>().sortMode
                LibraryListing(
                    documentId = "primary:Books",
                    entries = emptyList(),
                    truncated = false,
                    issue = LibraryListingIssue.NONE,
                )
            }
        }

        val viewModel = newViewModel(rootStore, librarySource)

        // uiState 是 WhileSubscribed：没有订阅者时 .value 恒为初始值（sortMode=NAME），
        // 必须先订阅并推进调度器，才能读到 combine 产出的排序。
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.setSortMode(LibrarySortMode.ADDED_DESC)
        advanceUntilIdle()

        assertEquals(
            "切换排序后本次重列必须用新规则；用旧值说明又读回了滞后的 root.value",
            LibrarySortMode.ADDED_DESC,
            receivedModes.last(),
        )
        assertEquals(
            "排序选中态必须立即反映用户选择，不能等 DataStore 异步发射",
            LibrarySortMode.ADDED_DESC,
            viewModel.uiState.value.sortMode,
        )
    }

    /**
     * 搜索必须同时过滤文件夹与文件。
     *
     * 修复前只过滤 `files`，`directories` 原样透出 —— 搜一个只匹配文件的词时，
     * 所有文件夹仍列在结果里，用户看到的就是「搜了没反应」。
     */
    @Test
    fun `search filters directories as well as files`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        val viewModel = buildViewModel(
            entries = listOf(
                entry("yellow", isDirectory = true),
                entry("加料", isDirectory = true),
                entry("yellow-book.txt", isDirectory = false),
                entry("other.epub", isDirectory = false),
            ),
        )
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.setQuery("yellow")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            "文件夹也要按关键词过滤，否则搜了等于没搜",
            listOf("yellow"),
            state.directories.map { it.displayName },
        )
        assertEquals(
            "文件同样按关键词过滤",
            listOf("yellow-book.txt"),
            state.files.map { it.entry.displayName },
        )
    }

    @Test
    fun `clearing the query restores every entry`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

        val viewModel = buildViewModel(
            entries = listOf(
                entry("yellow", isDirectory = true),
                entry("other.epub", isDirectory = false),
            ),
        )
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.setQuery("yellow")
        advanceUntilIdle()
        viewModel.setQuery("")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("yellow"), state.directories.map { it.displayName })
        assertEquals(listOf("other.epub"), state.files.map { it.entry.displayName })
    }

    private fun buildViewModel(entries: List<LibraryEntry>): LibraryBrowserViewModel {
        val treeUri = fakeUri("content://books.example/tree/primary%3ABooks")
        val configuredRoot = LibraryRoot(
            treeUri = treeUri,
            displayName = "书籍",
            lastLocationDocumentId = null,
            sortMode = LibrarySortMode.NAME,
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
        return newViewModel(rootStore, librarySource)
    }

    private fun newViewModel(
        rootStore: LibraryRootStore,
        librarySource: LibrarySource,
    ): LibraryBrowserViewModel = LibraryBrowserViewModel(
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

    private fun entry(name: String, isDirectory: Boolean): LibraryEntry = LibraryEntry(
        documentId = "primary:Books/$name",
        displayName = name,
        mimeType = if (isDirectory) "vnd.android.document/directory" else "text/plain",
        sizeBytes = 1,
        lastModifiedMillis = 1,
        isDirectory = isDirectory,
        uri = fakeUri("content://books.example/document/$name"),
    )

    private fun fakeUri(value: String): Uri {
        val uri = mockk<Uri>()
        every { uri.toString() } returns value
        every { uri.scheme } returns "content"
        return uri
    }
}
