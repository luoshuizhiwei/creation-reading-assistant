package com.creationreadingassistant.ui.screen.inspiration

import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.InspirationRepository
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.ui.viewmodel.BookListState
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import com.creationreadingassistant.ui.viewmodel.InspirationViewModel
import io.mockk.every
import io.mockk.mockk
import com.creationreadingassistant.data.repository.TaxonomyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CRA-TASK73-B-BOOK-STATE-JVM-v1 —— 来源书状态机的纯 JVM 回归
 * （由 androidTest 迁移，原 3 项测试此前只有编译证据、从未运行）。
 *
 * 直接消费生产 [InspirationViewModel.booksState] 与生产纯函数
 * [resolveSourceBookStatus]，验证：
 * 1. 首发前是 [BookListState.Loading]，不会被当成“已加载且为空”；
 * 2. 明确首发空列表是 [BookListState.Loaded]（确定为空）；
 * 3. 仓储异常是 [BookListState.Failed]，不会静默降级成空库/缺书成功；
 * 4. 解析器对 NotLinked / Loading / Available / Missing / Failed 全部分支正确。
 *
 * 不渲染 UI、不接触真实 Room：UI 与动作一致性、真实删除流程仍由 5 项 Compose/Room
 * instrumentation 覆盖（[com.creationreadingassistant.acceptance
 * .InspirationSourceBookLabelAcceptTest]），JVM 结果不代替它们。
 *
 * 迁移口径：沿用本仓库既有 JVM 约定（JUnit4 + MockK + `Dispatchers.setMain` /
 * `resetMain`，见 `ReaderViewModelTest`、`StatsRepositoryTest`），不新增依赖或通用框架。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InspirationSourceBookStateViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        // ViewModel 的 viewModelScope 取 Dispatchers.Main.immediate，必须先设置再构造。
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun booksStateIsLoadingUntilFirstEmissionThenLoadedEmptyIsExplicit() {
        val shared = MutableSharedFlow<List<BookEntity>>(replay = 1)
        val viewModel = buildViewModel(shared)
        try {
            assertEquals("首发前必须是 Loading", BookListState.Loading, viewModel.booksState.value)
            // 旧兼容投影仍为空，但新状态明确是 Loading，两者不可混同。
            assertTrue(viewModel.books.value.isEmpty())

            runBlocking {
                assertEquals(
                    BookListState.Loading,
                    withTimeout(5_000) { viewModel.booksState.first() },
                )
                shared.emit(emptyList())
                val loaded = withTimeout(5_000) {
                    viewModel.booksState.first { it is BookListState.Loaded }
                }
                assertTrue(
                    "明确首发空列表必须是 Loaded(empty)，不是 Loading",
                    (loaded as BookListState.Loaded).books.isEmpty(),
                )
            }
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun repositoryFailureIsFailedNotLoadedEmpty() {
        val viewModel = buildViewModel(
            flow<List<BookEntity>> {
                throw IllegalStateException("task72b-books-failure")
            },
        )
        try {
            runBlocking {
                val failed = withTimeout(5_000) {
                    viewModel.booksState.first { it is BookListState.Failed }
                }
                assertTrue(
                    "仓储错误必须保留原因，不能静默变成空库",
                    (failed as BookListState.Failed).message
                        ?.contains("task72b-books-failure") == true,
                )
            }
            assertTrue(viewModel.booksState.value is BookListState.Failed)
            assertFalse(viewModel.booksState.value is BookListState.Loaded)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun resolverMapsAllSourceBookStates() {
        val book = BookEntity(
            id = "task72b-book-resolver",
            title = "真实书名",
            format = "txt",
            updated_at = "2026-10-20T00:00:00Z",
        )
        val linked = InspirationSourceInfo(bookId = book.id)

        assertTrue(
            resolveSourceBookStatus(linked, BookListState.Loading)
                is SourceBookStatus.Loading,
        )
        assertTrue(
            resolveSourceBookStatus(linked, BookListState.Failed("boom"))
                is SourceBookStatus.Failed,
        )
        assertEquals(
            book,
            (
                resolveSourceBookStatus(linked, BookListState.Loaded(listOf(book)))
                    as SourceBookStatus.Available
                ).book,
        )
        assertTrue(
            resolveSourceBookStatus(linked, BookListState.Loaded(emptyList()))
                is SourceBookStatus.Missing,
        )
        assertTrue(
            resolveSourceBookStatus(
                InspirationSourceInfo(bookId = null),
                BookListState.Loaded(emptyList()),
            ) is SourceBookStatus.NotLinked,
        )
    }

    private fun buildViewModel(bookFlow: Flow<List<BookEntity>>): InspirationViewModel {
        val bookRepository = mockk<BookRepository>(relaxed = true)
        every { bookRepository.observeBooks() } returns bookFlow
        val settings = mockk<SettingsStore>(relaxed = true)
        every { settings.inspirationSort } returns MutableStateFlow("updated")
        // 与本用例无关的灵感列表上游：显式给空流，避免依赖 relaxed mock 对 Flow 的默认返回。
        val inspirationRepository = mockk<InspirationRepository>(relaxed = true)
        every { inspirationRepository.observeAllActive() } returns flowOf(emptyList())
        return InspirationViewModel(
            inspirationRepository = inspirationRepository,
            bookRepository = bookRepository,
            aiClient = mockk<AiClient>(relaxed = true),
            settings = settings,
            taxonomyRepository = mockk<TaxonomyRepository>(relaxed = true).also { repo ->
                every { repo.observeCategories() } returns flowOf(emptyList())
            },
        )
    }
}
