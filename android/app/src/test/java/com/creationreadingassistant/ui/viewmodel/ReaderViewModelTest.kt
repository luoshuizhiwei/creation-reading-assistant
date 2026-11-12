package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.dao.ReaderTextRuleDao
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.ChapterReadRepository
import com.creationreadingassistant.data.repository.ReaderProgressSaveOrder
import com.creationreadingassistant.data.repository.NoteRepository
import com.creationreadingassistant.data.repository.SearchIndexRepository
import com.creationreadingassistant.data.repository.SearchIndexScheduler
import com.creationreadingassistant.data.repository.TaxonomyRepository
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.data.settings.TtsResume
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.domain.model.EpubChapter
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.EpubDocument
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.TxtFileScanner
import com.creationreadingassistant.feature.reader.locator.AnchorCacheStore
import com.creationreadingassistant.feature.reader.pager.PageIndexStore
import com.creationreadingassistant.feature.reader.pager.PagerHealthStore
import com.creationreadingassistant.feature.reader.pager.ReaderPageIndexManager
import com.creationreadingassistant.feature.reader.rules.RuleCommand
import com.creationreadingassistant.feature.reader.rules.RuleKind
import com.creationreadingassistant.feature.reader.rules.RuleMutationResult
import com.creationreadingassistant.feature.reader.rules.RuleScope
import com.creationreadingassistant.feature.reader.rules.FakeReaderCorrectionDao
import com.creationreadingassistant.feature.reader.rules.RulesRepository
import com.creationreadingassistant.feature.reader.session.ReadingSessionRecorder
import com.creationreadingassistant.feature.reader.session.ReadingActivity
import com.creationreadingassistant.feature.library.deletion.BookDeletionCoordinator
import com.creationreadingassistant.ui.screen.reader.ReaderSheet
import com.creationreadingassistant.ui.screen.reader.ReaderLifecyclePersistGate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
// ScanTxtTocRule 为生产零派发死路径（已 @Deprecated），
// 但扫描状态机的取消/竞争/进度语义仅由本类经它覆盖，故保留驱动并抑制告警。
@Suppress("DEPRECATION")
class ReaderViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var documentLoader: ReaderDocumentLoader
    private lateinit var noteRepository: NoteRepository
    private lateinit var taxonomyRepository: TaxonomyRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var deletions: BookDeletionCoordinator
    private lateinit var chapterReadRepository: ChapterReadRepository
    private lateinit var readingSessionRecorder: ReadingSessionRecorder
    private lateinit var epubRepository: EpubRepository
    private lateinit var settingsStore: SettingsStore
    private lateinit var anchorCacheStore: AnchorCacheStore
    private lateinit var pageIndexStore: PageIndexStore
    private lateinit var pagerHealthStore: PagerHealthStore
    private lateinit var pageIndexManager: ReaderPageIndexManager
    private lateinit var aiClient: AiClient
    private lateinit var rulesRepository: RulesRepository
    private lateinit var searchIndexRepository: SearchIndexRepository
    private lateinit var searchIndexScheduler: SearchIndexScheduler
    private lateinit var appScope: CoroutineScope
    private lateinit var ruleDao: InMemoryReaderTextRuleDao

    private lateinit var viewModel: ReaderViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        documentLoader = mockk(relaxed = true)
        noteRepository = mockk(relaxed = true)
        taxonomyRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        deletions = mockk(relaxed = true)
        chapterReadRepository = mockk(relaxed = true)
        readingSessionRecorder = mockk(relaxed = true)
        epubRepository = mockk(relaxed = true)
        settingsStore = mockk(relaxed = true)
        anchorCacheStore = mockk(relaxed = true)
        pageIndexStore = mockk(relaxed = true)
        pagerHealthStore = mockk(relaxed = true)
        pageIndexManager = mockk(relaxed = true)
        aiClient = mockk(relaxed = true)

        ruleDao = InMemoryReaderTextRuleDao()
        rulesRepository = RulesRepository(ruleDao, FakeReaderCorrectionDao())
        searchIndexRepository = mockk(relaxed = true)
        searchIndexScheduler = mockk(relaxed = true)
        appScope = CoroutineScope(testDispatcher)

        every { taxonomyRepository.observeCategories() } returns flowOf(emptyList())
        every { taxonomyRepository.observeTags() } returns flowOf(emptyList())
        every { noteRepository.observeHighlightsByBook(any()) } returns flowOf(emptyList())
        every { noteRepository.observeNotesByBook(any()) } returns flowOf(emptyList())
        every { noteRepository.observeInspirationsByBook(any()) } returns flowOf(emptyList())
        every { bookRepository.observeSessionsByBook(any()) } returns flowOf(emptyList())
        every { chapterReadRepository.observeReadChapters(any()) } returns flowOf(emptyList())

        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        ioDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
    ): ReaderViewModel {
        return ReaderViewModel(
            documentLoader = documentLoader,
            noteRepository = noteRepository,
            taxonomyRepository = taxonomyRepository,
            bookRepository = bookRepository,
            deletions = deletions,
            chapterReadRepository = chapterReadRepository,
            readingSessionRecorder = readingSessionRecorder,
            epubRepository = epubRepository,
            settingsStore = settingsStore,
            anchorCacheStore = anchorCacheStore,
            pageIndexStore = pageIndexStore,
            pagerHealthStore = pagerHealthStore,
            pageIndexManager = pageIndexManager,
            aiClient = aiClient,
            rulesRepository = rulesRepository,
            searchIndexRepository = searchIndexRepository,
            searchIndexScheduler = searchIndexScheduler,
            appScope = appScope,
            ioDispatcher = ioDispatcher,
            defaultDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @Test
    fun `reading activity action is routed to the session recorder`() {
        viewModel.onAction(
            ReaderAction.UpdateReadingActivity(
                bookId = "book-1",
                active = true,
                progressPercent = 42f,
            ),
        )

        verify(exactly = 1) {
            readingSessionRecorder.update(ReadingActivity("book-1", active = true, progressPercent = 42f))
        }
    }

    @Test
    fun `delete book action uses the scoped deletion coordinator`() = runTest {
        viewModel.onAction(ReaderAction.DeleteBook("book-1"))
        advanceUntilIdle()

        coVerify(exactly = 1) { deletions.deleteBook("book-1") }
        coVerify(exactly = 0) { bookRepository.deleteBook(any()) }
    }

    // ── 辅助 ─────────────────────────────────────────────────────
    private fun fakeEpubBook(
        id: String = "book-1",
        chapterCount: Int = 3,
    ): EpubBook {
        val chapters = (0 until chapterCount).map { i ->
            EpubChapter(
                title = "Chapter $i",
                entryPath = "chapter$i.xhtml",
                chapterDir = "",
                cachedEpubPath = "/tmp/fake.epub",
                estimatedTextLength = 1000,
            )
        }
        return EpubBook(
            id = id,
            title = "Test Book",
            author = "Author",
            chapters = chapters,
            localUri = "file:///fake.epub",
            cachedEpubPath = "/tmp/fake.epub",
        )
    }

    private fun fakeEpubContent(
        book: EpubBook = fakeEpubBook(),
        document: EpubDocument = mockk(relaxed = true),
        chapterIndex: Int = 0,
        blocks: List<DocBlock> = listOf(DocBlock.Text("Hello")),
    ): ReaderLoadedContent.Epub {
        return ReaderLoadedContent.Epub(
            book = book,
            document = document,
            initialChapterIndex = chapterIndex,
            initialChapterBlocks = blocks,
            initialOffsetInChapter = 0,
        )
    }

    private fun fakeLoadedBook(
        id: String = "book-1",
        content: ReaderLoadedContent = fakeEpubContent(),
    ): ReaderLoadedBook {
        return ReaderLoadedBook(
            id = id,
            title = "Test Book",
            author = "Author",
            originalFileName = "test.epub",
            sizeBytes = 1024,
            savedReadingTimeMs = 0L,
            initialProgressPercent = 0f,
            content = content,
        )
    }

    /** 等待异步 IO 协程完成（viewModelScope.launch(Dispatchers.IO) 使用真实线程池） */
    private fun awaitIo() {
        Thread.sleep(300)
    }

    /** 轮询等待条件成立（真实线程并发场景）。 */
    private fun awaitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("awaitUntil 超时")
            }
            Thread.sleep(10)
        }
    }

    // ── 1. 切书代次 — 切换书籍时旧章节加载 job 被取消 ──────────────
    @Test
    fun `openBook cancels previous load job when switching books`() = runTest {
        val book1 = fakeLoadedBook("book-1")
        coEvery { documentLoader.load("book-1") } returns book1

        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()

        assertEquals("book-1", viewModel.uiState.value.requestedBookId)
        assertNotNull(viewModel.uiState.value.loadedBook)

        // 切换到 book-2
        val book2 = fakeLoadedBook("book-2")
        coEvery { documentLoader.load("book-2") } returns book2

        viewModel.onAction(ReaderAction.OpenBook("book-2"))
        awaitIo()

        assertEquals("book-2", viewModel.uiState.value.requestedBookId)
        assertEquals("book-2", viewModel.uiState.value.loadedBook?.id)
    }

    // ── 2. 切章代次 — 快速连续切章时旧请求被丢弃 ──────────────────
    @Test
    fun `loadChapter cancels previous chapter load on rapid switching`() = runTest {
        val book = fakeEpubBook()
        val document = mockk<EpubDocument>(relaxed = true)
        val content = fakeEpubContent(book = book, document = document)
        val loadedBook = fakeLoadedBook(content = content)

        coEvery { documentLoader.load("book-1") } returns loadedBook
        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()

        val blocks1 = listOf(DocBlock.Text("Chapter 1"))
        val blocks2 = listOf(DocBlock.Text("Chapter 2"))
        every { document.blocks(0) } returns blocks1
        every { document.blocks(1) } returns blocks2

        // 快速连续切章
        viewModel.onAction(ReaderAction.LoadChapter("book-1", 0))
        viewModel.onAction(ReaderAction.LoadChapter("book-1", 1))
        awaitIo()

        val state = viewModel.chapterLoadState.value
        assertTrue("Expected Loaded but was $state", state is ChapterLoadResult.Loaded)
        assertEquals(1, (state as ChapterLoadResult.Loaded).chapterIndex)
    }

    // ── 3. 资源释放 — onCleared() 取消 chapterLoadJob ─────────────
    @Test
    fun `onCleared cancels jobs and resets state`() = runTest {
        val book = fakeLoadedBook()
        coEvery { documentLoader.load("book-1") } returns book
        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()

        // 调用 onCleared (通过反射)
        val onClearedMethod = androidx.lifecycle.ViewModel::class.java
            .getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(viewModel)

        assertNull(viewModel.uiState.value.loadedBook)
        assertEquals("", viewModel.uiState.value.requestedBookId)
    }

    // ── 4. 会话幂等 — 相同章节重复加载不产生重复 IO ──────────────
    @Test
    fun `openBook same book id while loaded is no-op`() = runTest {
        val book = fakeLoadedBook()
        coEvery { documentLoader.load("book-1") } returns book

        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()

        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()

        coVerify(exactly = 1) { documentLoader.load("book-1") }
    }

    // ── 5. 进度保存 — epub 章节切换时进度正确保存 ─────────────────
    @Test
    fun `loadChapter saves epub progress after loading`() = runTest {
        val book = fakeEpubBook(chapterCount = 5)
        val document = mockk<EpubDocument>(relaxed = true)
        val content = fakeEpubContent(book = book, document = document)
        val loadedBook = fakeLoadedBook(content = content)

        coEvery { documentLoader.load("book-1") } returns loadedBook
        every { document.blocks(any()) } returns listOf(DocBlock.Text("content"))

        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()

        viewModel.onAction(ReaderAction.LoadChapter("book-1", 2))
        awaitIo()

        // 进度应为 (2+1)/5 * 100 ≈ 60%（浮点精度）
        // CRA-READER-DISPATCH-ORDER-MINIMAL-v6：章切换落库现在带 order，见 saveProgress 签名。
        coVerify(timeout = 2000) {
            epubRepository.saveProgress(
                bookId = "book-1",
                chapterIndex = 2,
                percent = any(),
                offsetInChapter = any(),
                absoluteOffset = any(),
                order = any(),
            )
        }
    }

    @Test
    fun `passive chapter sync with persistProgress=false does not overwrite epub locator`() = runTest {
        // 分页引擎的被动跨章同步：真实落点可能是上一章末页，VM 不得写「章首」locator
        // 覆盖真实位置（merge 无条件采信 incoming，真机反馈：退出重进回到前一章开头）。
        val book = fakeEpubBook(chapterCount = 5)
        val document = mockk<EpubDocument>(relaxed = true)
        val content = fakeEpubContent(book = book, document = document)
        val loadedBook = fakeLoadedBook(content = content)

        coEvery { documentLoader.load("book-1") } returns loadedBook
        every { document.blocks(any()) } returns listOf(DocBlock.Text("content"))

        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()

        viewModel.onAction(ReaderAction.LoadChapter("book-1", 2, persistProgress = false))
        awaitIo()

        coVerify(exactly = 0) {
            epubRepository.saveProgress(
                bookId = any(),
                chapterIndex = any(),
                percent = any(),
                offsetInChapter = any(),
                absoluteOffset = any(),
                order = any(),
            )
        }
        // 章块加载与已读标记不受影响
        coVerify(exactly = 1) { chapterReadRepository.markRead("book-1", 2) }
    }

    @Test
    fun `progress saves carry a dispatch-time order across the async io boundary`() = runTest {
        // CRA-READER-DISPATCH-ORDER-MINIMAL-v6：事件序号必须在**派发边界**取号后随事件带到持久入口，
        // 不能在 io 落库入口才分配。这里用排队式调度把两次保存的落库都推迟到两次派发之后，
        // 并在两次派发之间插入一次「另一条生产事件」的取号作为分界：
        // 若序号是落库时才取，两次保存都会晚于分界；只有派发时取号才会以分界为界一前一后。
        val calls = mutableListOf<Pair<String, Long>>()
        coEvery { bookRepository.saveProgress(any(), any()) } coAnswers {
            calls += Pair(firstArg<ReadingProgressEntity>().book_id, secondArg())
        }
        val vm = createViewModel(ioDispatcher = StandardTestDispatcher(testScheduler))

        vm.onAction(
            ReaderAction.SaveProgress(
                ReadingProgressEntity(book_id = "book-1", updated_at = "2026-10-12T10:00:00Z"),
            ),
        )
        val boundary = ReaderProgressSaveOrder.next() // 另一条生产事件在此刻派发
        vm.onAction(
            ReaderAction.SaveProgress(
                ReadingProgressEntity(book_id = "book-1", updated_at = "2026-10-12T10:00:01Z"),
            ),
        )
        advanceUntilIdle()

        assertEquals("两次保存都必须到达持久入口", 2, calls.size)
        assertTrue("第一次保存的序号在派发边界取得（早于中间事件）", calls[0].second < boundary)
        assertTrue("第二次保存的序号在派发边界取得（晚于中间事件）", calls[1].second > boundary)
    }

    @Test
    fun `loadChapter marks successful epub and markdown arrivals as read`() = runTest {
        val epubDocument = mockk<EpubDocument>(relaxed = true)
        every { epubDocument.blocks(1) } returns listOf(DocBlock.Text("epub"))
        coEvery { documentLoader.load("book-1") } returns fakeLoadedBook(
            content = fakeEpubContent(document = epubDocument),
        )

        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()
        viewModel.onAction(ReaderAction.LoadChapter("book-1", 1))
        awaitIo()

        coVerify(exactly = 1) { chapterReadRepository.markRead("book-1", 1) }

        val markdown = MarkdownDocument("# One\nfirst\n# Two\nsecond")
        coEvery { documentLoader.load("book-2") } returns fakeLoadedBook(
            id = "book-2",
            content = ReaderLoadedContent.Markdown(
                document = markdown,
                ownedTempFile = null,
                initialAbsoluteOffset = 0,
            ),
        )
        viewModel.onAction(ReaderAction.OpenBook("book-2"))
        awaitIo()
        viewModel.onAction(ReaderAction.LoadChapter("book-2", 1))
        awaitIo()

        coVerify(exactly = 1) { chapterReadRepository.markRead("book-2", 1) }
    }

    @Test
    fun `openBook marks the initial epub chapter as read`() = runTest {
        coEvery { documentLoader.load("book-1") } returns fakeLoadedBook(
            content = fakeEpubContent(chapterIndex = 2),
        )

        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()

        coVerify(exactly = 1) { chapterReadRepository.markRead("book-1", 2) }
    }

    @Test
    fun `failed chapter load does not mark the chapter as read`() = runTest {
        val document = mockk<EpubDocument>(relaxed = true)
        every { document.blocks(1) } throws IllegalStateException("broken chapter")
        coEvery { documentLoader.load("book-1") } returns fakeLoadedBook(
            content = fakeEpubContent(document = document),
        )

        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()
        viewModel.onAction(ReaderAction.LoadChapter("book-1", 1))
        awaitIo()

        coVerify(exactly = 0) { chapterReadRepository.markRead("book-1", 1) }
    }

    @Test
    fun `clear chapter reads action delegates to repository`() = runTest {
        viewModel.onAction(ReaderAction.ClearChapterReads("book-1"))
        awaitIo()

        coVerify(exactly = 1) { chapterReadRepository.clearForBook("book-1") }
    }

    @Test
    fun `route state observes read chapters for the active book`() = runTest {
        val reads = MutableStateFlow(listOf(0, 2))
        every { chapterReadRepository.observeReadChapters("book-1") } returns reads
        coEvery { documentLoader.load("book-1") } returns fakeLoadedBook()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.routeUiState.collect { }
        }

        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()
        assertEquals(listOf(0, 2), viewModel.routeUiState.value.readChapters)

        reads.value = emptyList()
        advanceUntilIdle()
        assertTrue(viewModel.routeUiState.value.readChapters.isEmpty())
    }

    @Test
    fun `route state publishes before room flows emit so first open shows loading not blank`() = runTest {
        // M-BUG-02 护栏：新书首开时 Room 读被导入的写事务饿住，bookData 的 combine 凑不齐首发射。
        // routeUiState 是多层 combine 嵌套（routeSources 再与 ruleData 合），而 combine 需
        // **所有源各发射至少一次**才发布 ⇒ 没有 onStart 兜底时它会一直停在 initialValue
        // （requestedBookId == ""），于是：①UI 收不到 isLoading，先看到白屏、转圈迟到；
        // ②_screenState 的 controlsVisible 变更也传不出去，菜单栏一直不隐去。
        // 只把这 4 路换成「活着但一条不发」即可：combine 只要有一路不发射就整体不发射，
        // chapterReadRepository 仍用 setup 的 flowOf(emptyList())，多换一路不会更严格。
        val highlights = MutableSharedFlow<List<HighlightEntity>>()
        val notes = MutableSharedFlow<List<NoteEntity>>()
        val inspirations = MutableSharedFlow<List<InspirationEntity>>()
        val sessions = MutableSharedFlow<List<ReadingSessionEntity>>()
        every { noteRepository.observeHighlightsByBook(any()) } returns highlights
        every { noteRepository.observeNotesByBook(any()) } returns notes
        every { noteRepository.observeInspirationsByBook(any()) } returns inspirations
        every { bookRepository.observeSessionsByBook(any()) } returns sessions
        // 正文解析闸门：放行前一直停在 loading，即用户报的「几十秒后才显示正在打开书籍」。
        // 用可放行的闸门而非 awaitCancellation()——后者会留下永不结束的 loadJob，其协程在
        // Dispatchers.resetMain() 之后仍持有 Main 引用，会污染后续测试类。
        val gate = CompletableDeferred<Unit>()
        coEvery { documentLoader.load("book-1") } coAnswers {
            gate.await()
            fakeLoadedBook()
        }

        val vm = createViewModel()
        // 顺序不可调换：生产里 Route 的 LaunchedEffect 先派发 OpenBook（openBook 同步落定
        // activeBookId 与 isLoading），collectAsStateWithLifecycle 随后才订阅 ⇒ stateIn 的
        // 收集起点直接落在慢 combine 上，flatMapLatest 的「无书身份」空分支根本没机会执行。
        // 若先订阅再打开，就会走空分支让 bookData 提前发射，测试假阳性通过。
        vm.onAction(ReaderAction.OpenBook("book-1"))
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.routeUiState.collect { }
        }
        advanceUntilIdle()

        val published = vm.routeUiState.value
        assertEquals(
            "bookData 首发射被饿住时，route 仍必须发布真实 uiState（onStart 兜底）",
            "book-1",
            published.document.requestedBookId,
        )
        assertTrue(
            "正文未就绪时 isLoading 必须可达 UI，否则先看到的是白屏",
            published.document.isLoading,
        )
        assertNull(
            "loadedBook 为空 ⇒ 必须落在加载占位分支，而不是渲染空正文",
            published.document.loadedBook,
        )

        // 放行解析，让 loadJob 正常收尾，不留悬挂协程。（「真实数据晚到后覆盖兜底快照」
        // 已由 `route state observes read chapters for the active book` 覆盖，此处不重复。）
        gate.complete(Unit)
        advanceUntilIdle()

        // 显式退订并当场结算：stateIn 的订阅计数簿记会恢复共享协程（其作用域是
        // viewModelScope＝测试 Main）。若拖到 runTest 收尾之后才结算，就会在
        // Dispatchers.resetMain() 之后去碰 Main，抛未捕获异常并随机污染后续测试类。
        collectJob.cancel()
        advanceUntilIdle()
    }

    // ── 6. TTS 安全 — TTS 相关操作在 ViewModel 层正确处理 ─────────
    @Test
    fun `saveTtsResume action persists to settings store`() = runTest {
        viewModel.onAction(ReaderAction.SaveTtsResume("book-1", 3, 500))
        awaitIo()

        coVerify(timeout = 2000) { settingsStore.saveTtsResume("book-1", 3, 500) }
    }

    @Test
    fun `loadTtsResume returns value from settings`() = runTest {
        val expected = TtsResume(bookId = "book-1", chapterIndex = 2, offset = 100)
        coEvery { settingsStore.loadTtsResume() } returns expected

        val result = viewModel.loadTtsResume()
        assertEquals(expected, result)
    }

    // ── 7. 返回键顺序 — 返回键 Action 转换逻辑 ────────────────────
    @Test
    fun `toggleControls toggles visibility`() {
        assertTrue(viewModel.screenState.value.controlsVisible)

        viewModel.onAction(ReaderAction.ToggleControls())
        assertFalse(viewModel.screenState.value.controlsVisible)

        viewModel.onAction(ReaderAction.ToggleControls())
        assertTrue(viewModel.screenState.value.controlsVisible)
    }

    @Test
    fun `toggleControls with explicit value sets directly`() {
        viewModel.onAction(ReaderAction.ToggleControls(visible = false))
        assertFalse(viewModel.screenState.value.controlsVisible)

        viewModel.onAction(ReaderAction.ToggleControls(visible = false))
        assertFalse(viewModel.screenState.value.controlsVisible)

        viewModel.onAction(ReaderAction.ToggleControls(visible = true))
        assertTrue(viewModel.screenState.value.controlsVisible)
    }

    // ── 8. LoadChapter / ScanTxtTocRule Action 转换正确 ───────────
    @Test
    fun `loadChapter action transitions to Loaded state`() = runTest {
        val book = fakeEpubBook()
        val document = mockk<EpubDocument>(relaxed = true)
        val content = fakeEpubContent(book = book, document = document)
        val loadedBook = fakeLoadedBook(content = content)

        coEvery { documentLoader.load("book-1") } returns loadedBook
        every { document.blocks(any()) } returns listOf(DocBlock.Text("content"))

        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()

        viewModel.onAction(ReaderAction.LoadChapter("book-1", 1))
        awaitIo()

        val state = viewModel.chapterLoadState.value
        assertTrue("Expected Loaded but was $state", state is ChapterLoadResult.Loaded)
        assertEquals(1, (state as ChapterLoadResult.Loaded).chapterIndex)
    }

    @Test
    fun `scanTxtTocRule action triggers scan`() = runTest {
        val tempFile = java.io.File.createTempFile("test_toc_", ".txt")
        tempFile.writeText("第一章 测试\n正文内容")
        try {
            viewModel.onAction(ReaderAction.ScanTxtTocRule("book-1", tempFile.absolutePath, "builtin"))
            awaitIo()

            val result = viewModel.txtRuleScanResult.value
            assertNotNull(result)
            assertEquals("builtin", result?.ruleId)
            assertEquals("book-1", result?.bookId)
            assertNull(result?.error)
        } finally {
            tempFile.delete()
        }
    }

    // ── 9. ChapterLoadResult 状态流转 ─────────────────────────────
    @Test
    fun `chapterLoadResult transitions Loading to Loaded`() = runTest {
        val book = fakeEpubBook()
        val document = mockk<EpubDocument>(relaxed = true)
        val content = fakeEpubContent(book = book, document = document)
        val loadedBook = fakeLoadedBook(content = content)

        coEvery { documentLoader.load("book-1") } returns loadedBook
        val blocks = listOf(DocBlock.Text("Chapter 1 content"))
        every { document.blocks(0) } returns blocks

        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()

        viewModel.onAction(ReaderAction.LoadChapter("book-1", 0))
        awaitIo()

        val state = viewModel.chapterLoadState.value
        assertTrue("Expected Loaded but was $state", state is ChapterLoadResult.Loaded)
        assertEquals(blocks, (state as ChapterLoadResult.Loaded).blocks)
    }

    @Test
    fun `chapterLoadResult transitions to Error on exception`() = runTest {
        val book = fakeEpubBook()
        val document = mockk<EpubDocument>(relaxed = true)
        val content = fakeEpubContent(book = book, document = document)
        val loadedBook = fakeLoadedBook(content = content)

        coEvery { documentLoader.load("book-1") } returns loadedBook
        every { document.blocks(any()) } throws RuntimeException("ZIP error")

        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()

        viewModel.onAction(ReaderAction.LoadChapter("book-1", 0))
        awaitIo()

        val state = viewModel.chapterLoadState.value
        assertTrue("Expected Error but was $state", state is ChapterLoadResult.Error)
        assertEquals("ZIP error", (state as ChapterLoadResult.Error).message)
    }

    // ── 10. TxtRuleScanResult 正确暴露 ────────────────────────────
    @Test
    fun `txtRuleScanResult exposes error on failure`() = runTest {
        viewModel.onAction(ReaderAction.ScanTxtTocRule("book-1", "/nonexistent/file.txt", "bad-rule"))
        awaitIo()

        val result = viewModel.txtRuleScanResult.value
        assertNotNull(result)
        assertNotNull("Expected error but was null: $result", result?.error)
    }

    @Test
    fun `scan result arriving after switching book is dropped`() = runTest {
        val tempFile = java.io.File.createTempFile("stale_toc_", ".txt")
        tempFile.writeText("第一章 测试\n正文内容")
        try {
            val book2 = fakeEpubBook(id = "book-2")
            val document = mockk<EpubDocument>(relaxed = true)
            every { document.blocks(0) } returns listOf(DocBlock.Text("B2"))
            coEvery { documentLoader.load("book-2") } returns fakeLoadedBook(
                content = fakeEpubContent(book = book2, document = document),
            )

            // 排队式调度：先发起的扫描协程在 openBook 之后才执行，模拟「旧书扫描晚到」
            val vm = createViewModel(ioDispatcher = StandardTestDispatcher(testScheduler))
            vm.onAction(ReaderAction.ScanTxtTocRule("book-1", tempFile.absolutePath, "builtin"))
            vm.onAction(ReaderAction.OpenBook("book-2"))
            advanceUntilIdle()

            assertNull("旧书迟到扫描结果不得发布到新书会话", vm.txtRuleScanResult.value)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `same book redundant open does not invalidate in-flight txt scan`() = runTest {
        val tempFile = java.io.File.createTempFile("samebook_toc_", ".txt")
        tempFile.writeText("第一章 测试\n正文内容")
        try {
            val book = fakeEpubBook(id = "book-1")
            val document = mockk<EpubDocument>(relaxed = true)
            every { document.blocks(0) } returns listOf(DocBlock.Text("B1"))
            coEvery { documentLoader.load("book-1") } returns fakeLoadedBook(
                content = fakeEpubContent(book = book, document = document),
            )

            // 队列式调度：扫描在途时冗余打开同一本书（不切换书身份）
            val vm = createViewModel(ioDispatcher = StandardTestDispatcher(testScheduler))
            vm.onAction(ReaderAction.OpenBook("book-1"))
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.loadedBook)

            vm.onAction(ReaderAction.ScanTxtTocRule("book-1", tempFile.absolutePath, "builtin"))
            vm.onAction(ReaderAction.OpenBook("book-1"))
            advanceUntilIdle()

            assertNotNull("同书冗余 open 不得使在途扫描失效", vm.txtRuleScanResult.value)
            assertEquals("book-1", vm.txtRuleScanResult.value?.bookId)
        } finally {
            tempFile.delete()
        }
    }

    // ── 10b. TXT 规则扫描状态机：进度 / 完成 / 取消 / 切书 ──────────

    @Test
    fun `txt scan publishes progress then Completed with real chapter count`() = runTest {
        val tempFile = java.io.File.createTempFile("status_toc_", ".txt")
        val body = "测试正文内容。".repeat(70) // ~560 chars, above density threshold
        tempFile.writeText("第一章 测试\n$body\n第二章 重逢\n$body")
        try {
            val ioExecutor = Executors.newSingleThreadExecutor { r ->
                Thread(r, "vm-scan-io").apply { isDaemon = true }
            }
            try {
                val vm = createViewModel(ioDispatcher = ioExecutor.asCoroutineDispatcher())
                val started = CountDownLatch(1)
                val release = CountDownLatch(1)
                val afterProgress = CountDownLatch(1)
                val resume = CountDownLatch(1)
                vm.txtFileScanner = { file, rule, monitor ->
                    started.countDown()
                    release.await()
                    monitor?.onProgress(0.5f)
                    afterProgress.countDown()
                    resume.await()
                    TxtFileScanner.scan(file, rule)
                }

                vm.onAction(ReaderAction.ScanTxtTocRule("book-1", tempFile.absolutePath, "builtin"))
                assertTrue("扫描应启动", started.await(5, TimeUnit.SECONDS))
                assertEquals(
                    TxtRuleScanStatus.Running("book-1", "builtin", null),
                    vm.txtRuleScanStatus.value,
                )

                release.countDown()
                assertTrue("进度应上报", afterProgress.await(5, TimeUnit.SECONDS))
                assertEquals(
                    TxtRuleScanStatus.Running("book-1", "builtin", 0.5f),
                    vm.txtRuleScanStatus.value,
                )

                resume.countDown()
                awaitUntil { vm.txtRuleScanStatus.value is TxtRuleScanStatus.Completed }
                val completed = vm.txtRuleScanStatus.value as TxtRuleScanStatus.Completed
                assertEquals("book-1", completed.bookId)
                assertEquals("builtin", completed.ruleId)
                assertEquals("完成章数应为真实识别数", 2, completed.chapterCount)
                assertNotNull("扫描结果应发布", vm.txtRuleScanResult.value?.fileIndex)
            } finally {
                ioExecutor.shutdown()
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `txt scan failure publishes Failed status`() = runTest {
        viewModel.onAction(ReaderAction.ScanTxtTocRule("book-1", "/nonexistent/file.txt", "bad-rule"))
        awaitIo()

        val status = viewModel.txtRuleScanStatus.value
        assertTrue("Expected Failed but was $status", status is TxtRuleScanStatus.Failed)
        assertEquals("book-1", (status as TxtRuleScanStatus.Failed).bookId)
        assertEquals("bad-rule", status.ruleId)
        assertNotNull(viewModel.txtRuleScanResult.value?.error)
    }

    @Test
    fun `new scan request cancels previous in-flight scan and drops its late result`() = runTest {
        val tempFile = java.io.File.createTempFile("cancel_stale_", ".txt")
        val body = "测试正文内容。".repeat(70)
        tempFile.writeText("第一章 测试\n$body")
        try {
            val ioExecutor = Executors.newSingleThreadExecutor { r ->
                Thread(r, "vm-scan-io").apply { isDaemon = true }
            }
            try {
                val vm = createViewModel(ioDispatcher = ioExecutor.asCoroutineDispatcher())
                val firstStarted = CountDownLatch(1)
                val firstRelease = CountDownLatch(1)
                val firstScanSawCancel = AtomicReference<Boolean?>(null)
                vm.txtFileScanner = { file, rule, monitor ->
                    if (rule == "rule-1") {
                        firstStarted.countDown()
                        firstRelease.await()
                        firstScanSawCancel.set(monitor?.isCancelled())
                        TxtFileScanner.scan(file, rule)
                    } else {
                        TxtFileScanner.scan(file, rule)
                    }
                }

                vm.onAction(ReaderAction.ScanTxtTocRule("book-1", tempFile.absolutePath, "rule-1"))
                assertTrue(firstStarted.await(5, TimeUnit.SECONDS))

                // 新请求必须取消旧 Job，且旧扫描的协作取消探针要能观察到取消
                vm.onAction(ReaderAction.ScanTxtTocRule("book-1", tempFile.absolutePath, "rule-2"))
                firstRelease.countDown()
                awaitUntil { firstScanSawCancel.get() == true }
                awaitUntil { vm.txtRuleScanStatus.value is TxtRuleScanStatus.Completed }

                val completed = vm.txtRuleScanStatus.value as TxtRuleScanStatus.Completed
                assertEquals("rule-2", completed.ruleId)
                awaitUntil { vm.txtRuleScanResult.value?.ruleId == "rule-2" }
            } finally {
                ioExecutor.shutdown()
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `explicit cancel publishes Cancelled and does not overwrite previous result`() = runTest {
        val tempFile = java.io.File.createTempFile("cancel_toc_", ".txt")
        val body = "测试正文内容。".repeat(70)
        tempFile.writeText("第一章 测试\n$body")
        try {
            val ioExecutor = Executors.newSingleThreadExecutor { r ->
                Thread(r, "vm-scan-io").apply { isDaemon = true }
            }
            try {
                val vm = createViewModel(ioDispatcher = ioExecutor.asCoroutineDispatcher())

                // 第一次扫描完整成功
                vm.onAction(ReaderAction.ScanTxtTocRule("book-1", tempFile.absolutePath, "rule-1"))
                awaitUntil { vm.txtRuleScanStatus.value is TxtRuleScanStatus.Completed }
                val firstResult = vm.txtRuleScanResult.value
                assertNotNull(firstResult?.fileIndex)

                // 第二次扫描在途中显式取消
                val secondStarted = CountDownLatch(1)
                val secondRelease = CountDownLatch(1)
                val secondFinished = AtomicReference(false)
                vm.txtFileScanner = { file, rule, monitor ->
                    if (rule == "rule-2") {
                        secondStarted.countDown()
                        secondRelease.await()
                        monitor?.onProgress(0.9f) // 取消后的迟到进度
                        secondFinished.set(true)
                        TxtFileScanner.scan(file, rule)
                    } else {
                        TxtFileScanner.scan(file, rule)
                    }
                }

                vm.onAction(ReaderAction.ScanTxtTocRule("book-1", tempFile.absolutePath, "rule-2"))
                assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
                vm.onAction(ReaderAction.CancelTxtTocScan)

                assertEquals(
                    TxtRuleScanStatus.Cancelled("book-1", "rule-2"),
                    vm.txtRuleScanStatus.value,
                )
                assertEquals("取消不得覆盖原 document/fileIndex", firstResult, vm.txtRuleScanResult.value)

                secondRelease.countDown()
                awaitUntil { secondFinished.get() }
                // 迟到 success / progress 一律不得发布
                assertEquals(
                    TxtRuleScanStatus.Cancelled("book-1", "rule-2"),
                    vm.txtRuleScanStatus.value,
                )
                assertEquals(firstResult, vm.txtRuleScanResult.value)
            } finally {
                ioExecutor.shutdown()
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `switching book cancels scan and resets status to Idle`() = runTest {
        val tempFile = java.io.File.createTempFile("switch_toc_", ".txt")
        tempFile.writeText("第一章 测试\n正文内容")
        try {
            val book2 = fakeEpubBook(id = "book-2")
            val document = mockk<EpubDocument>(relaxed = true)
            every { document.blocks(0) } returns listOf(DocBlock.Text("B2"))
            coEvery { documentLoader.load("book-2") } returns fakeLoadedBook(
                content = fakeEpubContent(book = book2, document = document),
            )

            val vm = createViewModel(ioDispatcher = StandardTestDispatcher(testScheduler))
            vm.onAction(ReaderAction.ScanTxtTocRule("book-1", tempFile.absolutePath, "builtin"))
            assertEquals(TxtRuleScanStatus.Running("book-1", "builtin", null), vm.txtRuleScanStatus.value)

            vm.onAction(ReaderAction.OpenBook("book-2"))
            assertEquals("切书后旧书扫描状态不得残留", TxtRuleScanStatus.Idle, vm.txtRuleScanStatus.value)

            advanceUntilIdle()
            assertNull("旧书迟到扫描结果不得发布", vm.txtRuleScanResult.value)
        } finally {
            tempFile.delete()
        }
    }

    // ── 11. loadChapterBlocks 有界加载 ────────────────────────────
    @Test
    fun `loadChapterBlocks returns empty when no book loaded`() = runTest {
        val result = viewModel.loadChapterBlocks("book-1", 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `loadChapterBlocks returns blocks for loaded epub`() = runTest {
        val expectedBlocks = listOf(DocBlock.Text("Block 1"), DocBlock.Text("Block 2"))
        val book = fakeEpubBook()
        val document = mockk<EpubDocument>(relaxed = true)
        every { document.blocks(0) } returns expectedBlocks
        val content = fakeEpubContent(book = book, document = document)
        val loadedBook = fakeLoadedBook(content = content)

        coEvery { documentLoader.load("book-1") } returns loadedBook
        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()

        val result = viewModel.loadChapterBlocks("book-1", 0)
        assertEquals(expectedBlocks, result)
    }

    @Test
    fun `loadChapterBlocks returns empty for wrong bookId`() = runTest {
        val book = fakeEpubBook()
        val document = mockk<EpubDocument>(relaxed = true)
        val content = fakeEpubContent(book = book, document = document)
        val loadedBook = fakeLoadedBook(content = content)

        coEvery { documentLoader.load("book-1") } returns loadedBook
        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()

        val result = viewModel.loadChapterBlocks("wrong-id", 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `loadChapterBlocks returns empty on exception`() = runTest {
        val book = fakeEpubBook()
        val document = mockk<EpubDocument>(relaxed = true)
        every { document.blocks(any()) } throws RuntimeException("read error")
        val content = fakeEpubContent(book = book, document = document)
        val loadedBook = fakeLoadedBook(content = content)

        coEvery { documentLoader.load("book-1") } returns loadedBook
        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()

        val result = viewModel.loadChapterBlocks("book-1", 0)
        assertTrue(result.isEmpty())
    }

    // ── 12. extractChapterText 文本提取 ───────────────────────────
    @Test
    fun `extractChapterText returns empty when no book loaded`() = runTest {
        val result = viewModel.extractChapterText("book-1", 0)
        assertEquals("", result)
    }

    @Test
    fun `extractChapterText returns text for loaded epub`() = runTest {
        val book = fakeEpubBook()
        val document = mockk<EpubDocument>(relaxed = true)
        // 显式 mock text() 方法（relaxed mock 不会调用默认实现）
        every { document.text(0) } returns "Chapter one\n full text"
        val content = fakeEpubContent(book = book, document = document)
        val loadedBook = fakeLoadedBook(content = content)

        coEvery { documentLoader.load("book-1") } returns loadedBook
        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()

        val result = viewModel.extractChapterText("book-1", 0)
        assertEquals("Chapter one\n full text", result)
    }

    @Test
    fun `extractChapterText returns empty on exception`() = runTest {
        val book = fakeEpubBook()
        val document = mockk<EpubDocument>(relaxed = true)
        every { document.blocks(any()) } throws RuntimeException("error")
        val content = fakeEpubContent(book = book, document = document)
        val loadedBook = fakeLoadedBook(content = content)

        coEvery { documentLoader.load("book-1") } returns loadedBook
        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()

        val result = viewModel.extractChapterText("book-1", 0)
        assertEquals("", result)
    }

    // ── 13. 并发保护 — 多协程同时访问文档 ─────────────────────────
    @Test
    fun `openBook while loading cancels previous and starts new`() = runTest {
        val book1 = fakeLoadedBook("book-1")
        val book2 = fakeLoadedBook("book-2")

        coEvery { documentLoader.load("book-1") } returns book1
        coEvery { documentLoader.load("book-2") } returns book2

        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        viewModel.onAction(ReaderAction.OpenBook("book-2"))
        awaitIo()

        assertEquals("book-2", viewModel.uiState.value.requestedBookId)
        assertEquals("book-2", viewModel.uiState.value.loadedBook?.id)
    }

    // ── 14. 错误处理 — IO 异常时 StateFlow 正确发射 Error 状态 ─────
    @Test
    fun `openBook emits error state on IO failure`() = runTest {
        coEvery { documentLoader.load("book-1") } throws java.io.IOException("File not found")

        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()

        val state = viewModel.uiState.value
        assertNotNull("Expected error message", state.errorMessage)
        assertEquals("File not found", state.errorMessage)
        assertNull(state.loadedBook)
    }

    @Test
    fun `openBook emits error for blank bookId`() = runTest {
        // 先加载一本书
        val book = fakeLoadedBook()
        coEvery { documentLoader.load("book-1") } returns book
        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()
        assertNotNull(viewModel.uiState.value.loadedBook)

        // 打开空字符串 bookId 会触发 require 失败
        coEvery { documentLoader.load("") } throws IllegalArgumentException("未指定书籍")
        viewModel.onAction(ReaderAction.OpenBook(""))
        awaitIo()

        // 应显示错误状态
        val state = viewModel.uiState.value
        assertNotNull("Expected error message for blank bookId", state.errorMessage)
        assertNull(state.loadedBook)
    }

    // ── UI Action 测试补充 ─────────────────────────────────────────
    @Test
    fun `openSheet and closeSheet actions update screen state`() {
        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.TOC))
        assertEquals(ReaderSheet.TOC, viewModel.screenState.value.sheet)

        viewModel.onAction(ReaderAction.CloseSheet)
        assertNull(viewModel.screenState.value.sheet)
    }

    @Test
    fun `setSelectedText and clearSelection actions`() {
        viewModel.onAction(ReaderAction.SetSelectedText("hello", 5, 100, 120))
        assertEquals("hello", viewModel.screenState.value.selectedText)
        assertEquals(5, viewModel.screenState.value.selectedRangeStart)
        assertEquals(100, viewModel.screenState.value.selectedGlobalOffset)
        assertEquals(120, viewModel.screenState.value.selectedSourceEnd)

        viewModel.onAction(ReaderAction.ClearSelection)
        assertEquals("", viewModel.screenState.value.selectedText)
        assertEquals(-1, viewModel.screenState.value.selectedRangeStart)
        assertEquals(-1, viewModel.screenState.value.selectedGlobalOffset)
        assertEquals(-1, viewModel.screenState.value.selectedSourceEnd)
    }

    // ── S07-F2：页面/章节身份变化后必须完整清除选区 ──────────────────

    /**
     * 选区不变量：五个字段必须一起复位。
     *
     * 只清 `selectedText` / `selectedRangeStart` 会留下 `selectedGlobalOffset`、
     * `selectedSourceEnd` 与 `showColorRow`，而 `ReaderScaffold` 的
     * `selectedSourceLength` 正是由 `selectedSourceEnd` 减去这两个起点之一推出的 ——
     * 半清状态下会算出跨字段的错值（进而静默写空 payload）。
     */
    private fun assertSelectionFullyCleared(stage: String) {
        val s = viewModel.screenState.value
        assertEquals("$stage: selectedText 必须清空", "", s.selectedText)
        assertEquals("$stage: selectedRangeStart 必须复位", -1, s.selectedRangeStart)
        assertEquals("$stage: selectedGlobalOffset 必须复位", -1, s.selectedGlobalOffset)
        assertEquals("$stage: selectedSourceEnd 必须复位", -1, s.selectedSourceEnd)
        assertFalse("$stage: showColorRow 必须收起", s.showColorRow)
    }

    /** 造一个「选区四字段 + 色板行全开」的状态，供各清除路径共用。 */
    private fun seedOpenSelectionWithColorRow() {
        viewModel.onAction(ReaderAction.SetSelectedText("一期一会", 5, 100, 120))
        viewModel.onAction(ReaderAction.ToggleColorRow)
        assertTrue("前置：色板行应为打开态", viewModel.screenState.value.showColorRow)
    }

    @Test
    fun `clear selection resets every field including the colour row`() {
        seedOpenSelectionWithColorRow()

        viewModel.onAction(ReaderAction.ClearSelection)

        assertSelectionFullyCleared("ClearSelection")
    }

    @Test
    fun `a committed page turn clears the whole selection`() {
        // 手势翻页 / 硬件键翻页 / 自动翻页 / 外部翻页请求在提交页码后都派发
        // ReaderAction.PageTurn。旧实现只隐藏控制栏，VM 选区原样保留，
        // 工具条会继续引用上一页的文字与坐标。
        seedOpenSelectionWithColorRow()

        viewModel.onAction(ReaderAction.PageTurn)

        assertSelectionFullyCleared("PageTurn")
        assertFalse("翻页仍必须隐藏控制栏", viewModel.screenState.value.controlsVisible)
    }

    @Test
    fun `a chapter change clears the whole selection`() {
        // 目录选择 / 上一章 / 下一章 / 搜索跳转 / 高亮回源都经 goToChapter → LoadChapter。
        seedOpenSelectionWithColorRow()

        viewModel.onAction(ReaderAction.LoadChapter("book-1", 2))

        assertSelectionFullyCleared("LoadChapter")
    }

    @Test
    fun `a centre tap that dismisses an open selection clears the whole selection`() {
        // 中央点击收起菜单时若正有选区，必须与 ClearSelection 同语义；
        // 旧实现只清 text 与 rangeStart，留下 globalOffset / sourceEnd / showColorRow。
        seedOpenSelectionWithColorRow()

        viewModel.onAction(ReaderAction.ToggleControls())

        assertSelectionFullyCleared("ToggleControls(CenterTap)")
    }

    @Test
    fun `clearing on a page turn preserves the new reading position`() {
        // 清选区不得顺手改动位置：页码/章号与选区是两套独立状态。
        viewModel.onAction(ReaderAction.ToggleControls(visible = false))
        assertFalse(viewModel.screenState.value.controlsVisible)

        viewModel.onAction(ReaderAction.SetSelectedText("一期一会", 5, 100, 120))
        viewModel.onAction(ReaderAction.PageTurn)

        assertSelectionFullyCleared("PageTurn")
        assertFalse(
            "翻页后的 chrome 可见性契约不变（仍是隐藏）",
            viewModel.screenState.value.controlsVisible,
        )
        assertEquals("翻页不得清空搜索词", "", viewModel.screenState.value.searchQuery)
        assertNull("翻页不得改动弹层状态", viewModel.screenState.value.sheet)
    }

    @Test
    fun `a page turn without a selection is a harmless no-op on selection state`() {
        // 到达第一页/最后一页等「没有真正换页」的情况不会派发 PageTurn
        // （resolveReaderTapAction 返回 NONE，见 ReaderTapActionTest）。
        // 这里锁定的是：即便 PageTurn 被派发，也不得凭空造出或改变选区。
        assertSelectionFullyCleared("初始态")

        viewModel.onAction(ReaderAction.PageTurn)

        assertSelectionFullyCleared("无选区的 PageTurn")
    }

    @Test
    fun `search query action updates screen state`() {
        viewModel.onAction(ReaderAction.SetSearchQuery("test query"))
        assertEquals("test query", viewModel.screenState.value.searchQuery)
    }

    @Test
    fun `note open and body actions`() {
        viewModel.onAction(ReaderAction.SetNoteOpen(true))
        assertTrue(viewModel.screenState.value.noteOpen)

        viewModel.onAction(ReaderAction.SetNoteBody("note content"))
        assertEquals("note content", viewModel.screenState.value.noteBody)
    }

    @Test
    fun `retry action re-opens current book with force`() = runTest {
        coEvery { documentLoader.load("book-1") } throws RuntimeException("fail")
        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Retry
        val book = fakeLoadedBook()
        coEvery { documentLoader.load("book-1") } returns book
        viewModel.onAction(ReaderAction.Retry)
        awaitIo()

        assertNotNull(viewModel.uiState.value.loadedBook)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `loadChapter does nothing when no book is loaded`() = runTest {
        viewModel.onAction(ReaderAction.LoadChapter("book-1", 0))
        awaitIo()

        // loadChapter 先设 Loading 再检查 book，所以可能是 Loading 状态
        val state = viewModel.chapterLoadState.value
        assertTrue(state == null || state is ChapterLoadResult.Loading)
    }

    // ── 12. 书内搜索查询所有权 — 切书清空 / 同书保留 ───────────────
    @Test
    fun `switching book clears stale search query`() = runTest {
        val book1 = fakeLoadedBook("book-1")
        coEvery { documentLoader.load("book-1") } returns book1
        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()
        viewModel.onAction(ReaderAction.SetSearchQuery("测试"))

        val book2 = fakeLoadedBook("book-2")
        coEvery { documentLoader.load("book-2") } returns book2
        viewModel.onAction(ReaderAction.OpenBook("book-2"))
        awaitIo()

        assertEquals(
            "切书后旧查询不得残留，否则会用旧词自动搜索新书",
            "",
            viewModel.screenState.value.searchQuery,
        )
    }

    @Test
    fun `same book redundant open keeps search query`() = runTest {
        val book = fakeLoadedBook("book-1")
        coEvery { documentLoader.load("book-1") } returns book
        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()
        viewModel.onAction(ReaderAction.SetSearchQuery("测试"))

        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()

        assertEquals("同书冗余打开不得清空搜索查询", "测试", viewModel.screenState.value.searchQuery)
    }

    @Test
    fun `same book force retry keeps search query`() = runTest {
        coEvery { documentLoader.load("book-1") } throws RuntimeException("fail")
        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()
        viewModel.onAction(ReaderAction.SetSearchQuery("测试"))

        val book = fakeLoadedBook()
        coEvery { documentLoader.load("book-1") } returns book
        viewModel.onAction(ReaderAction.Retry)
        awaitIo()

        assertEquals("同书重载（force）不得清空搜索查询", "测试", viewModel.screenState.value.searchQuery)
    }

    @Test
    fun `closing and reopening search sheet keeps query`() {
        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.SEARCH))
        viewModel.onAction(ReaderAction.SetSearchQuery("测试"))
        viewModel.onAction(ReaderAction.CloseSheet)
        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.SEARCH))

        assertEquals("关闭/重开搜索面板保留查询", "测试", viewModel.screenState.value.searchQuery)
    }

    // ── 15. 规则接入：快照随书隔离 / 写入反馈 / 旧值迁移 ────────────

    @Test
    fun `rule snapshot follows active book and per-book bindings stay isolated`() = runTest {
        val book1 = fakeLoadedBook("book-1")
        val book2 = fakeLoadedBook("book-2")
        coEvery { documentLoader.load("book-1") } returns book1
        coEvery { documentLoader.load("book-2") } returns book2

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.routeUiState.collect { }
        }

        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()
        viewModel.onAction(
            ReaderAction.ExecuteRuleCommand(
                "book-1",
                RuleCommand.ToggleBuiltinToc("num-dot", enabled = true),
            ),
        )
        awaitIo()

        val snap1 = viewModel.routeUiState.value.ruleSnapshot
        assertEquals("book-1", snap1.bookId)
        assertTrue("book-1 绑定应生效", snap1.effectiveToc.any { it.id == "num-dot" })

        viewModel.onAction(ReaderAction.OpenBook("book-2"))
        awaitIo()

        val snap2 = viewModel.routeUiState.value.ruleSnapshot
        assertEquals("book-2", snap2.bookId)
        assertTrue("别的书的宽松内置绑定不得泄漏", snap2.effectiveToc.none { it.id == "num-dot" })
        assertNull("切书后旧规则写入反馈应清空", viewModel.routeUiState.value.ruleMutationResult)

        // 切回 book-1：绑定仍在，快照恢复
        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        awaitIo()
        val snapBack = viewModel.routeUiState.value.ruleSnapshot
        assertEquals("book-1", snapBack.bookId)
        assertTrue("切回后 book-1 绑定应恢复", snapBack.effectiveToc.any { it.id == "num-dot" })
    }

    @Test
    fun `blank book id exposes safe empty rule snapshot`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.routeUiState.collect { }
        }

        val snapshot = viewModel.routeUiState.value.ruleSnapshot
        assertEquals("", snapshot.bookId)
        assertTrue(snapshot.tocRules.isEmpty())
        assertTrue(snapshot.replaceRules.isEmpty())
        assertTrue(snapshot.effectiveToc.isEmpty())
        assertNull("无书身份时不得携带旧写入反馈", viewModel.routeUiState.value.ruleMutationResult)
    }

    @Test
    fun `executeRuleCommand publishes mutation result and clear resets feedback`() = runTest {
        viewModel.onAction(
            ReaderAction.ExecuteRuleCommand(
                "book-1",
                RuleCommand.SaveCustomToc(id = "c1", name = "自定义", pattern = "^第\\d+章$"),
            ),
        )
        awaitIo()

        assertEquals(RuleMutationResult.Saved("c1"), viewModel.ruleMutationResult.value)
        val snapshot = rulesRepository.observe("book-1").first()
        assertTrue(snapshot.tocRules.any { it.id == "c1" && it.enabled })

        viewModel.onAction(ReaderAction.ClearRuleMutationResult)
        assertNull("清除反馈后应回到 null", viewModel.ruleMutationResult.value)
    }

    @Test
    fun `successful rule command clears stale selection so repaginated body keeps no residue`() = runTest {
        // 选区残留 bug（R6 静态排查）：规则保存会触发重分页，display 坐标选区若不清空，
        // 正文选区底色/把手错位、选区工具条不消失、规则表单下次还预填失效旧文本。
        viewModel.onAction(ReaderAction.SetSelectedText("hello", 5, 100, 120))
        assertEquals("hello", viewModel.screenState.value.selectedText)

        viewModel.onAction(
            ReaderAction.ExecuteRuleCommand(
                "book-1",
                RuleCommand.SaveCustomReplace(name = "净化", pattern = "广告", replacement = ""),
            ),
        )
        awaitIo()

        assertTrue(viewModel.ruleMutationResult.value is RuleMutationResult.Saved)
        val state = viewModel.screenState.value
        assertEquals("", state.selectedText)
        assertEquals(-1, state.selectedRangeStart)
        assertEquals(-1, state.selectedGlobalOffset)
        assertEquals(-1, state.selectedSourceEnd)
        assertFalse(state.showColorRow)
    }

    @Test
    fun `global replace rule change wakes a REPLACE sweep instead of a silently dropped KEEP enqueue`() =
        runTest {
            // 规则变更置脏后必须用 REPLACE 唤起后台重扫。
            //
            // 曾经的实现用 SearchIndexScheduler.enqueueOnce()（ExistingWorkPolicy.KEEP）：当唯一
            // 任务名对应的 work 正处于 RUNNING 时，这次入队会被 WorkManager **静默丢弃**；而在跑的
            // 那个 worker 会继续把 last_scanned_book_id 往前写，**覆盖掉**
            // invalidateSweepForFullRebuild() 刚重置的游标 ⇒ 换了全局替换规则后全库重扫
            // **根本不会发生**，旧规则下建的索引行一直生效（搜索命中旧文本）。
            //
            // 反向验证方式：把 ReaderViewModel 里那行改回 enqueueOnce()，本用例必须 FAIL。
            viewModel.onAction(
                ReaderAction.ExecuteRuleCommand(
                    "book-1",
                    RuleCommand.SaveCustomReplace(
                        name = "全局净化",
                        pattern = "广告",
                        replacement = "",
                        scope = RuleScope.GLOBAL,
                    ),
                ),
            )
            awaitIo()

            assertTrue(viewModel.ruleMutationResult.value is RuleMutationResult.Saved)
            coVerify(exactly = 1) { searchIndexRepository.invalidateSweepForFullRebuild() }
            verify(exactly = 1) { searchIndexScheduler.triggerNow(force = true) }
            verify(exactly = 0) { searchIndexScheduler.enqueueOnce() }
            // 规则变更不涉及删书，不得顺带走孤儿回收（千万行表的全表 distinct 扫描）。
            // 这条锁住「不改成 triggerFullRebuildNow()」的决定。
            coVerify(exactly = 0) { searchIndexRepository.purgeOrphanIndexRows() }
        }

    @Test
    fun `failed correction keeps selection intact for retry`() = runTest {
        // 失败路径不清选区：用户仍可重试或改用本书替换。
        // 构造 NotAnchorable：命令 findText 与当前选区文本不一致。
        viewModel.onAction(ReaderAction.SetSelectedText("hello", 5, 100, 120))

        viewModel.onAction(
            ReaderAction.ExecuteRuleCommand(
                "book-1",
                RuleCommand.SaveSingleCorrection(
                    sourceStart = -1,
                    sourceEnd = -1,
                    findText = "完全不同的文本",
                    replaceText = "x",
                ),
            ),
        )
        awaitIo()

        assertTrue(viewModel.ruleMutationResult.value is RuleMutationResult.NotAnchorable)
        assertEquals("hello", viewModel.screenState.value.selectedText)
        assertEquals(5, viewModel.screenState.value.selectedRangeStart)
        assertEquals(120, viewModel.screenState.value.selectedSourceEnd)
    }

    @Test
    fun `loadTxtTocRule migrates legacy num-dot into binding and effective id`() = runTest {
        coEvery { settingsStore.loadTxtTocRule("book-1") } returns "num-dot"

        viewModel.onAction(ReaderAction.LoadTxtTocRule("book-1"))
        awaitIo()

        assertEquals("num-dot", viewModel.txtTocRuleId.value)
        assertNotNull("旧值迁移应建立按书绑定", ruleDao.getById("builtin:num-dot:book-1"))
        val snapshot = rulesRepository.observe("book-1").first()
        assertTrue(snapshot.effectiveToc.any { it.id == "num-dot" })
    }

    @Test
    fun `loadTxtTocRule falls back to builtin for unknown legacy value`() = runTest {
        coEvery { settingsStore.loadTxtTocRule("book-1") } returns "no-such-rule"

        viewModel.onAction(ReaderAction.LoadTxtTocRule("book-1"))
        awaitIo()

        assertEquals("未知旧值应回退标准内置", "builtin", viewModel.txtTocRuleId.value)
        assertNull("未知旧值不得建立宽松内置绑定", ruleDao.getById("builtin:num-dot:book-1"))
        val snapshot = rulesRepository.observe("book-1").first()
        assertEquals(listOf("builtin"), snapshot.effectiveToc.map { it.id })
    }

    @Test
    fun `saveTxtTocRule still persists through settings store`() = runTest {
        viewModel.onAction(ReaderAction.SaveTxtTocRule("book-1", "num-dot"))
        awaitIo()

        coVerify(timeout = 2000) { settingsStore.saveTxtTocRule("book-1", "num-dot") }
    }

    // ── 16. P1-A：多规则目录接入实际管线 ──────────────────────────

    @Test
    fun `rescanTxtToc scans with effective room profile and publishes profile key identity`() = runTest {
        val tempFile = java.io.File.createTempFile("rescan_toc_", ".txt")
        tempFile.writeText("第一章 测试\n正文内容")
        try {
            coEvery { documentLoader.load("book-1") } returns fakeLoadedBook()
            viewModel.onAction(ReaderAction.OpenBook("book-1"))
            awaitIo()

            viewModel.onAction(
                ReaderAction.ExecuteRuleCommand("book-1", RuleCommand.ToggleBuiltinToc("num-dot", enabled = true)),
            )
            awaitIo()
            val profile = rulesRepository.observe("book-1").first().effectiveTocProfile
            assertEquals("num-dot", profile.key)

            viewModel.onAction(ReaderAction.RescanTxtToc("book-1", tempFile.absolutePath, profile.key))
            awaitIo()

            val result = viewModel.txtRuleScanResult.value
            assertNotNull(result)
            assertEquals("book-1", result?.bookId)
            assertEquals("扫描身份必须为 profile.key", "num-dot", result?.ruleId)
            assertNull(result?.error)
            assertTrue(
                "Expected Completed but was ${viewModel.txtRuleScanStatus.value}",
                viewModel.txtRuleScanStatus.value is TxtRuleScanStatus.Completed,
            )
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `rescanTxtToc with stale profile key hint is dropped`() = runTest {
        val tempFile = java.io.File.createTempFile("stale_rescan_", ".txt")
        tempFile.writeText("第一章 测试\n正文内容")
        try {
            coEvery { documentLoader.load("book-1") } returns fakeLoadedBook()
            viewModel.onAction(ReaderAction.OpenBook("book-1"))
            awaitIo()

            viewModel.onAction(ReaderAction.RescanTxtToc("book-1", tempFile.absolutePath, "stale-key"))
            awaitIo()

            assertNull("过期 key 提示不得触发扫描", viewModel.txtRuleScanResult.value)
            assertEquals(TxtRuleScanStatus.Idle, viewModel.txtRuleScanStatus.value)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `failed rescan publishes error without applying stale success`() = runTest {
        val tempFile = java.io.File.createTempFile("fail_rescan_", ".txt")
        tempFile.writeText("第一章 测试\n正文内容")
        try {
            coEvery { documentLoader.load("book-1") } returns fakeLoadedBook()
            viewModel.onAction(ReaderAction.OpenBook("book-1"))
            awaitIo()

            viewModel.onAction(ReaderAction.ExecuteRuleCommand("book-1", RuleCommand.ToggleBuiltinToc("num-dot", enabled = true)))
            awaitIo()
            val profile = rulesRepository.observe("book-1").first().effectiveTocProfile

            viewModel.txtFileScannerProfile = { _, _, _ -> throw RuntimeException("磁盘错误") }
            viewModel.onAction(ReaderAction.RescanTxtToc("book-1", tempFile.absolutePath, profile.key))
            awaitIo()

            val result = viewModel.txtRuleScanResult.value
            assertNotNull("失败结果应发布", result?.error)
            assertEquals(profile.key, result?.ruleId)
            assertTrue(
                "Expected Failed but was ${viewModel.txtRuleScanStatus.value}",
                viewModel.txtRuleScanStatus.value is TxtRuleScanStatus.Failed,
            )
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `book switch drops late rescan result`() = runTest {
        val tempFile = java.io.File.createTempFile("late_rescan_", ".txt")
        tempFile.writeText("第一章 测试\n正文内容")
        try {
            val book2 = fakeEpubBook(id = "book-2")
            val document = mockk<EpubDocument>(relaxed = true)
            every { document.blocks(0) } returns listOf(DocBlock.Text("B2"))
            coEvery { documentLoader.load("book-2") } returns fakeLoadedBook(
                content = fakeEpubContent(book = book2, document = document),
            )

            // 排队式调度：rescan 协程在 openBook 之后才执行，模拟「旧书重扫晚到」
            val vm = createViewModel(ioDispatcher = StandardTestDispatcher(testScheduler))
            vm.onAction(ReaderAction.RescanTxtToc("book-1", tempFile.absolutePath, "builtin"))
            vm.onAction(ReaderAction.OpenBook("book-2"))
            advanceUntilIdle()

            assertNull("切书后旧书迟到重扫结果不得发布", vm.txtRuleScanResult.value)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `executeRuleCommand single rule selection normalizes room and syncs display id`() = runTest {
        viewModel.onAction(ReaderAction.ExecuteRuleCommand("book-1", RuleCommand.ToggleBuiltinToc("num-dot", enabled = true)))
        awaitIo()
        viewModel.onAction(ReaderAction.ExecuteRuleCommand("book-1", RuleCommand.ToggleBuiltinToc("bracketed", enabled = true)))
        awaitIo()

        viewModel.onAction(ReaderAction.ExecuteRuleCommand("book-1", RuleCommand.SelectSingleTocRule("num-dot")))
        awaitIo()

        assertEquals("快速单选应同步显示 id", "num-dot", viewModel.txtTocRuleId.value)
        assertEquals("快速单选结果应发布供统一重扫消费", RuleMutationResult.Migrated("num-dot", null), viewModel.ruleMutationResult.value)
        val snapshot = rulesRepository.observe("book-1").first()
        assertEquals(listOf("builtin", "num-dot"), snapshot.effectiveToc.map { it.id })
        assertEquals("归一化后 profile key 等于所选规则", "num-dot", snapshot.effectiveTocProfile.key)
    }

    @Test
    fun `loadTxtTocRule respects migration marker and never re-enables disabled binding`() = runTest {
        coEvery { settingsStore.loadTxtTocRule("book-1") } returns "num-dot"
        coEvery { settingsStore.isTxtTocRuleMigrated("book-1") } returns true
        // 用户已在规则面板禁用 num-dot：绑定存在但 disabled，重开不得被旧值重新启用
        ruleDao.upsert(
            ReaderTextRuleEntity(
                id = "builtin:num-dot:book-1",
                kind = "TOC",
                name = "数字+标点",
                pattern = null,
                replacement = "",
                builtin = true,
                enabled = false,
                scope = "PER_BOOK",
                book_id = "book-1",
                position = 1,
                created_at = 1L,
                updated_at = 1L,
            ),
        )

        viewModel.onAction(ReaderAction.LoadTxtTocRule("book-1"))
        awaitIo()

        assertEquals("已迁移后不得重新写 Room", false, ruleDao.getById("builtin:num-dot:book-1")?.enabled)
        assertEquals("显示 id 应从生效快照推导", "builtin", viewModel.txtTocRuleId.value)
    }

    @Test
    fun `effective profile key changes after reorder of custom toc rules`() = runTest {
        viewModel.onAction(
            ReaderAction.ExecuteRuleCommand(
                "book-1",
                RuleCommand.SaveCustomToc(id = "c1", name = "甲", pattern = "^甲\\d+$"),
            ),
        )
        awaitIo()
        viewModel.onAction(
            ReaderAction.ExecuteRuleCommand(
                "book-1",
                RuleCommand.SaveCustomToc(id = "c2", name = "乙", pattern = "^乙\\d+$"),
            ),
        )
        awaitIo()
        val before = rulesRepository.observe("book-1").first().effectiveTocProfile.key

        viewModel.onAction(
            ReaderAction.ExecuteRuleCommand(
                "book-1",
                RuleCommand.ReorderRules(RuleKind.TOC, listOf("c2", "c1")),
            ),
        )
        awaitIo()

        val after = rulesRepository.observe("book-1").first().effectiveTocProfile.key
        org.junit.Assert.assertNotEquals("重排后旧 key 必须失效", before, after)
    }

    @Test
    fun `create category action persists the caller supplied id`() = runTest {
        val id = "mobile-category-identity-contract"
        viewModel.onAction(ReaderAction.CreateCategory(id = id, name = "身份测试分类"))
        advanceUntilIdle()

        val captured = slot<CategoryEntity>()
        coVerify(exactly = 1) { taxonomyRepository.upsertCategory(capture(captured)) }
        assertEquals("回调返回的 id 必须与最终落库 id 同源", id, captured.captured.id)
        assertEquals("身份测试分类", captured.captured.name)
    }

    // ── 退出保存完成边界（CRA-READER-EXIT-SAVE-DRAIN-v1；141-A 红的演进） ──────────
    /**
     * 141-A「直接 store.clear()」的旧 hazard，保留为**显式 characterization**：
     * framework 的强制清理（`ViewModel.clear()` → `ViewModelImpl.clear()` 先取消
     * `viewModelScope`，再 `onCleared()`）不会等待在途保存，这一语义**没有**被本批改变。
     * 因此生产不得把"强制 clear 后最终完成"当作"退出前已 drain"；真实退出必须走
     * [ReaderExitBoundary]（下方 exitThroughSharedBoundary… 覆盖）。
     *
     * 141-A 的原始红记录（同名用例、原始断言、log/XML）永久保留在
     * `task141-offline-implementation-v1/lane-a`，本文件不覆盖它。
     */
    @Test
    fun directClearWithoutAwaitStillCancelsPendingSave() = runTest {
        // 同调度器：viewModelScope（Main.immediate）与注入的 ioDispatcher 都落在本 testScheduler 上。
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val bookId = "book-exit-pending-save"
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        var completed = false
        var cancelled = false
        coEvery { bookRepository.saveProgress(any(), any()) } coAnswers {
            assertEquals(
                "派发到持久入口的书 id 必须是本用例唯一书",
                bookId,
                firstArg<ReadingProgressEntity>().book_id,
            )
            entered.complete(Unit)
            try {
                gate.await()
                completed = true
            } catch (c: CancellationException) {
                cancelled = true
                throw c
            }
        }

        val vm = createViewModel(ioDispatcher = StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val provider = ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
                },
            )
            assertSame(
                "夹具：store 必须真实持有同一个 VM（真实 clear 路径才成立）",
                vm,
                provider[ReaderViewModel::class.java],
            )

            // 1) 派发一次退出落库（生产分支 ReaderViewModel.kt:464-477；序号在派发边界取）。
            vm.onAction(
                ReaderAction.SaveProgress(
                    ReadingProgressEntity(book_id = bookId, updated_at = "2026-10-26T00:00:00Z"),
                ),
            )
            runCurrent()

            // 2) 闩锁前置：已进入持久入口、仍 pending、未被取消。
            assertTrue("前置：保存必须已进入 bookRepository.saveProgress", entered.isCompleted)
            assertFalse("前置：写操作此刻必须仍未完成", completed)
            assertFalse("前置：写操作此刻不得已被取消", cancelled)

            // 3) 真实退出：ViewModelStore.clear() → ViewModelImpl.clear()（取消 viewModelScope）→ onCleared()。
            store.clear()
            advanceUntilIdle() // 让取消投递到 await 处

            // 4) 打开闩锁：若退出未 drain，保存已被取消，永远不会恢复完成。
            gate.complete(Unit)
            advanceUntilIdle()

            assertFalse("强制 clear 不等待时，在途保存不会正常完成（framework 语义未变）", completed)
            assertTrue("强制 clear 会取消在途保存：生产不得把它当作退出前 drain", cancelled)
        } finally {
            gate.complete(Unit)
            store.clear()
        }
    }

    /**
     * 真实共用退出入口（[ReaderExitBoundary]）的严格顺序：
     * 先派发退出保存 → 等待本 reader 已派发的进度保存**全部正常完成** → 才导航一次。
     *
     * 本用例把四件事同时钉死：
     * - store/provider 在退出**之前**就持有真实 VM；`navigate` 回调内核对"全部完成 / 无 pending /
     *   作用域仍存活"，然后才执行真实 `store.clear()`，并记录 navigate→clear 顺序；
     * - 等待**已经开始之后**再派发后到事件，必须被计入，且只有它完成才放行；
     * - 等待期间重复 Back 不得再派发保存、不得排队第二次导航；
     * - 成功退出必须把 lifecycle 防重复门标记为已落库（onDispose 不再重复派发）。
     */
    @Test
    fun exitThroughSharedBoundaryMustDrainPendingSavesBeforeNavigation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val bookId = "book-exit-shared-boundary"
        val gates = mutableListOf<CompletableDeferred<Unit>>()
        var completed = 0
        var cancelled = 0
        coEvery { bookRepository.saveProgress(any(), any()) } coAnswers {
            assertEquals("持久入口只接收本书的进度保存", bookId, firstArg<ReadingProgressEntity>().book_id)
            val gate = CompletableDeferred<Unit>()
            gates += gate
            try {
                gate.await()
                completed += 1
            } catch (c: CancellationException) {
                cancelled += 1
                throw c
            }
        }
        val vm = createViewModel(ioDispatcher = StandardTestDispatcher(testScheduler))

        // store/provider 在退出前就持有真实 VM（clear 与导航的顺序由 navigate 回调绑定）。
        val store = ViewModelStore()
        ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
            },
        )[ReaderViewModel::class.java]

        val order = mutableListOf<String>()
        val persistGate = ReaderLifecyclePersistGate()
        var navigations = 0
        val boundary = ReaderExitBoundary(
            scope = backgroundScope,
            dispatchExitSave = {
                vm.onAction(
                    ReaderAction.SaveProgress(
                        ReadingProgressEntity(book_id = bookId, updated_at = "2026-10-26T02:00:02Z"),
                    ),
                )
            },
            awaitProgressSaves = { vm.awaitProgressSaves() },
            navigate = {
                assertEquals("导航时全部已派发保存必须已正常完成", gates.size, completed)
                assertTrue("导航时 VM 作用域必须仍然存活", vm.viewModelScope.isActive)
                order += "navigate"
                store.clear() // 真实 clear 绑定在导航之后
                order += "clear"
                navigations += 1
            },
            onDrained = { persistGate.onPauseOrStop() },
        )

        // 1) 退出之前已派发、仍在途的保存（滚动/防抖侧）。
        vm.onAction(
            ReaderAction.SaveProgress(
                ReadingProgressEntity(book_id = bookId, updated_at = "2026-10-26T02:00:01Z"),
            ),
        )
        runCurrent()
        assertEquals("前置：已有一次保存进入持久入口", 1, gates.size)

        // 2) 真实退出入口：先派发退出保存（派发先于等待），再等待。
        boundary.requestExit()
        runCurrent()
        assertEquals("退出保存必须已经派发（派发先于等待）", 2, gates.size)
        assertEquals("仍有 pending 保存时不得导航", 0, navigations)
        assertTrue("退出等待期间 VM 作用域不得被清除", vm.viewModelScope.isActive)

        // 3) 等待已经开始之后，再派发一个后到事件：必须被计入。
        vm.onAction(
            ReaderAction.SaveProgress(
                ReadingProgressEntity(book_id = bookId, updated_at = "2026-10-26T02:00:03Z"),
            ),
        )
        runCurrent()
        assertEquals("等待期间后到的事件必须被计入", 3, gates.size)

        // 4) 等待期间重复 Back：不得再派发保存，也不得排队第二次导航。
        boundary.requestExit()
        runCurrent()
        assertEquals("重复 Back 不得再派发退出保存", 3, gates.size)
        assertEquals("重复 Back 不得触发导航", 0, navigations)

        // 5) 只放行早期两次：后到事件仍 pending → 仍不得导航。
        gates[0].complete(Unit)
        gates[1].complete(Unit)
        runCurrent()
        assertEquals("只完成更早的保存不足以放行导航", 0, navigations)
        assertEquals(2, completed)

        // 6) 放行后到事件：全部正常完成后才导航，且导航后立即真实 clear。
        gates[2].complete(Unit)
        advanceUntilIdle()
        assertEquals("全部已派发保存正常完成后才导航，且只导航一次", 1, navigations)
        assertEquals("三次保存都必须正常完成", 3, completed)
        assertEquals("退出 drain 不得取消任何已派发保存", 0, cancelled)
        assertEquals("导航必须发生在真实 clear 之前", listOf("navigate", "clear"), order)
        assertFalse("clear 之后 VM 作用域必须已取消", vm.viewModelScope.isActive)
        assertFalse(
            "成功退出必须把 lifecycle 防重复门标记为已落库（onDispose 不得再重复派发）",
            persistGate.shouldPersistOnDispose(),
        )
    }

    /** 保存失败不得当作 drained 成功：不导航、原异常可观察、不标记已落库。 */
    @Test
    fun exitMustNotNavigateWhenPendingSaveFails() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val bookId = "book-exit-save-failure"
        coEvery { bookRepository.saveProgress(any(), any()) } coAnswers {
            throw IllegalStateException("progress write failed")
        }
        val vm = createViewModel(ioDispatcher = StandardTestDispatcher(testScheduler))
        var navigations = 0
        var failure: Throwable? = null
        val persistGate = ReaderLifecyclePersistGate()
        val boundary = ReaderExitBoundary(
            scope = backgroundScope,
            dispatchExitSave = {
                vm.onAction(
                    ReaderAction.SaveProgress(
                        ReadingProgressEntity(book_id = bookId, updated_at = "2026-10-26T02:00:04Z"),
                    ),
                )
            },
            awaitProgressSaves = { vm.awaitProgressSaves() },
            navigate = { navigations += 1 },
            onDrained = { persistGate.onPauseOrStop() },
        )

        boundary.requestExit(onFailure = { failure = it })
        advanceUntilIdle()

        assertEquals("保存失败时不得导航（reader 留在原页）", 0, navigations)
        assertTrue("失败必须可观察，且保留原异常类型", failure is IllegalStateException)
        assertEquals("失败必须保留原始消息（不包装、不吞）", "progress write failed", failure?.message)
        assertTrue("失败路径不得标记已落库：onDispose 仍应允许保存", persistGate.shouldPersistOnDispose())
    }

    /**
     * 保存**自身**被取消 ≠ 退出协程被取消：前者必须留 reader 并回传可观察原因
     * （142-v1 的缺口：CancellationException 被直接 rethrow，onFailure 从未调用）。
     */
    @Test
    fun exitMustSurfaceSaveCancellationInsteadOfSkippingFeedback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val bookId = "book-exit-save-cancelled"
        coEvery { bookRepository.saveProgress(any(), any()) } coAnswers {
            throw CancellationException("save cancelled by storage layer")
        }
        val vm = createViewModel(ioDispatcher = StandardTestDispatcher(testScheduler))
        var navigations = 0
        var failure: Throwable? = null
        val persistGate = ReaderLifecyclePersistGate()
        val boundary = ReaderExitBoundary(
            scope = backgroundScope,
            dispatchExitSave = {
                vm.onAction(
                    ReaderAction.SaveProgress(
                        ReadingProgressEntity(book_id = bookId, updated_at = "2026-10-26T02:00:05Z"),
                    ),
                )
            },
            awaitProgressSaves = { vm.awaitProgressSaves() },
            navigate = { navigations += 1 },
            onDrained = { persistGate.onPauseOrStop() },
        )

        boundary.requestExit(onFailure = { failure = it })
        advanceUntilIdle()

        assertEquals("保存自身被取消时不得导航", 0, navigations)
        assertTrue("保存取消必须作为可观察原因回传，不得静默跳过", failure is CancellationException)
        assertEquals("必须保留原始取消原因", "save cancelled by storage layer", failure?.message)
        assertTrue("失败路径不得标记已落库", persistGate.shouldPersistOnDispose())
    }

    /**
     * 可重试语义：失败不会被"消费"掉。
     * 第一次退出失败后，即使下一次退出**不派发任何新保存**（空保存路径）也必须继续失败，
     * 不允许假 drain；只有更晚派发的一次保存正常完成后才解除阻塞。
     */
    @Test
    fun exitMustNotFalselyDrainAfterFailureUntilALaterSaveSucceeds() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val bookId = "book-exit-retry-semantics"
        var failNext = true
        coEvery { bookRepository.saveProgress(any(), any()) } coAnswers {
            if (failNext) throw IllegalStateException("first write failed")
        }
        val vm = createViewModel(ioDispatcher = StandardTestDispatcher(testScheduler))
        var dispatch = true
        var dispatches = 0
        var navigations = 0
        val failures = mutableListOf<Throwable>()
        val boundary = ReaderExitBoundary(
            scope = backgroundScope,
            dispatchExitSave = {
                if (dispatch) {
                    dispatches += 1
                    vm.onAction(
                        ReaderAction.SaveProgress(
                            ReadingProgressEntity(book_id = bookId, updated_at = "2026-10-26T02:00:06Z"),
                        ),
                    )
                }
            },
            awaitProgressSaves = { vm.awaitProgressSaves() },
            navigate = { navigations += 1 },
        )

        // 1) 第一次退出：保存失败 → 不导航，原因可观察。
        //    用 runCurrent 精确驱动本次 requestExit 派生的任务（不用 advanceUntilIdle 含糊推进）。
        boundary.requestExit(onFailure = { failures += it })
        runCurrent()
        assertEquals("第 1 次退出必须真的派发保存", 1, dispatches)
        assertEquals(0, navigations)
        assertEquals("第 1 次退出必须真的走完失败回调", 1, failures.size)
        assertTrue(failures[0] is IllegalStateException)

        // 2) 失败不得被消费：空等一次仍然必须抛出同一失败（sticky，不返回成功）。
        dispatch = false
        val stillFailing = runCatching { withTimeoutOrNull(5_000) { vm.awaitProgressSaves() } }.exceptionOrNull()
        assertTrue("失败必须 sticky：空等不得返回 drained，也不得永久挂起", stillFailing is IllegalStateException)
        assertEquals("失败必须保留原始消息", "first write failed", stillFailing?.message)
        boundary.requestExit(onFailure = { failures += it })
        runCurrent()
        assertEquals("空保存退出不得再派发", 1, dispatches)
        assertEquals("第 2 次退出必须真的走完失败回调（证明 exitJob 已结束）", 2, failures.size)
        assertEquals("空保存再退一次不得假成功", 0, navigations)

        // 3) 更晚派发的一次保存正常完成 → 解除阻塞，退出才通过。
        //    这里先证明"第 3 次退出确实派发了保存"，再证明只导航一次。
        failNext = false
        dispatch = true
        boundary.requestExit(onFailure = { failures += it })
        runCurrent()
        assertEquals("第 3 次退出必须真的派发新保存（不得因 exitJob 仍 active 被忽略）", 2, dispatches)
        assertEquals("更晚的保存成功后才允许退出", 1, navigations)
        assertEquals("成功退出不应再产生新失败", 2, failures.size)
    }

    /**
     * 142-r3：同一 book 内「更新请求的失败」不得被更旧请求的成功清掉。
     *
     * 受控完成顺序：seq1 失败 → seq3 失败 → seq2 成功。
     * seq3 是比 seq2 更新的一次失败，seq2 的成功**不能**把它清掉；
     * 只有更晚（seq4）真正成功的一次保存才允许解除阻塞。
     */
    @Test
    fun newerSaveFailureMustNotBeClearedByAnOlderSaveSuccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val bookId = "book-failure-order"
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        coEvery { bookRepository.saveProgress(any(), any()) } coAnswers {
            val entity = firstArg<ReadingProgressEntity>()
            val key = entity.updated_at ?: error("updated_at 必填")
            val gate = CompletableDeferred<Unit>()
            gates[key] = gate
            gate.await()
            when (key) {
                "seq1" -> throw IllegalStateException("failure-1")
                "seq2" -> Unit
                "seq3" -> throw IllegalStateException("failure-3")
                else -> Unit
            }
        }
        val vm = createViewModel(ioDispatcher = StandardTestDispatcher(testScheduler))
        fun dispatch(key: String, book: String = bookId) {
            vm.onAction(
                ReaderAction.SaveProgress(
                    ReadingProgressEntity(book_id = book, updated_at = key),
                ),
            )
        }

        dispatch("seq1"); runCurrent()
        dispatch("seq2"); runCurrent()
        dispatch("seq3"); runCurrent()
        assertEquals("三次保存都必须已进入持久入口", 3, gates.size)

        // 受控完成顺序：1 失败 → 3 失败 → 2 成功
        gates.getValue("seq1").complete(Unit); runCurrent()
        gates.getValue("seq3").complete(Unit); runCurrent()
        gates.getValue("seq2").complete(Unit); runCurrent()

        val remaining = runCatching {
            withTimeoutOrNull(5_000) { vm.awaitProgressSaves() }
        }.exceptionOrNull()
        assertTrue(
            "比 seq2 更新的 seq3 失败不得被 seq2 的成功清掉",
            remaining is IllegalStateException,
        )
        assertEquals("必须保留更新那次失败的原始原因", "failure-3", remaining?.message)

        // 另一个 book 的成功不得清掉本书的失败。
        dispatch("other-book-success", book = "book-other")
        runCurrent()
        gates.getValue("other-book-success").complete(Unit)
        runCurrent()
        val afterOtherBook = runCatching {
            withTimeoutOrNull(5_000) { vm.awaitProgressSaves() }
        }.exceptionOrNull()
        assertTrue(
            "其它 book 的成功不得清掉本书未解决的失败",
            afterOtherBook is IllegalStateException,
        )
        assertEquals("failure-3", afterOtherBook?.message)

        // 只有更晚（seq4）真正成功的一次保存才解除阻塞。
        dispatch("seq4"); runCurrent()
        gates.getValue("seq4").complete(Unit)
        runCurrent()
        val cleared = runCatching {
            withTimeoutOrNull(5_000) { vm.awaitProgressSaves() }
        }.exceptionOrNull()
        assertNull("更晚的成功保存必须解除阻塞", cleared)
    }

    /**
     * 142-r3 反向完成顺序：更新请求（seq2）先成功，更旧请求（seq1）后失败。
     * 旧失败晚到不得把 reader 永久卡死（它已被更晚的成功保存覆盖），
     * 但随后 seq3 的新失败仍必须被保留。
     */
    @Test
    fun olderFailureArrivingAfterANewerSuccessMustNotBlockForever() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val bookId = "book-failure-order-reverse"
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        coEvery { bookRepository.saveProgress(any(), any()) } coAnswers {
            val entity = firstArg<ReadingProgressEntity>()
            val key = entity.updated_at ?: error("updated_at 必填")
            val gate = CompletableDeferred<Unit>()
            gates[key] = gate
            gate.await()
            when (key) {
                "seq1" -> throw IllegalStateException("failure-1")
                "seq2" -> Unit
                "seq3" -> throw IllegalStateException("failure-3")
                else -> Unit
            }
        }
        val vm = createViewModel(ioDispatcher = StandardTestDispatcher(testScheduler))
        fun dispatch(key: String) {
            vm.onAction(
                ReaderAction.SaveProgress(
                    ReadingProgressEntity(book_id = bookId, updated_at = key),
                ),
            )
        }

        dispatch("seq1"); runCurrent()
        dispatch("seq2"); runCurrent()
        assertEquals(2, gates.size)

        // 反向：seq2 先成功，seq1 后失败
        gates.getValue("seq2").complete(Unit); runCurrent()
        gates.getValue("seq1").complete(Unit); runCurrent()

        val afterLateOldFailure = runCatching {
            withTimeoutOrNull(5_000) { vm.awaitProgressSaves() }
        }.exceptionOrNull()
        assertNull(
            "已被更晚成功保存覆盖的旧失败晚到，不得把 reader 永久卡死",
            afterLateOldFailure,
        )

        // 随后一次更新的失败仍必须保留。
        dispatch("seq3"); runCurrent()
        gates.getValue("seq3").complete(Unit); runCurrent()
        val newest = runCatching {
            withTimeoutOrNull(5_000) { vm.awaitProgressSaves() }
        }.exceptionOrNull()
        assertTrue("更新的失败仍必须保留", newest is IllegalStateException)
        assertEquals("failure-3", newest?.message)
    }

    /** 无 pending / 临时查阅空保存：正常一次退出，不得误持久化进度，且标记已落库。 */
    @Test
    fun exitWithNoPendingSavesNavigatesExactlyOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = createViewModel(ioDispatcher = StandardTestDispatcher(testScheduler))
        var navigations = 0
        val persistGate = ReaderLifecyclePersistGate()
        val boundary = ReaderExitBoundary(
            scope = backgroundScope,
            // 等价生产 persistOnLeave 的早退（临时查阅 / 位置未就绪）：不派发任何保存。
            dispatchExitSave = {},
            awaitProgressSaves = { vm.awaitProgressSaves() },
            navigate = { navigations += 1 },
            onDrained = { persistGate.onPauseOrStop() },
        )

        boundary.requestExit()
        advanceUntilIdle()

        assertEquals("无 pending 时应正常退出且只导航一次", 1, navigations)
        coVerify(exactly = 0) { bookRepository.saveProgress(any(), any()) }
        assertFalse("成功退出必须标记已落库", persistGate.shouldPersistOnDispose())
    }

    // ── READER-PAGES-v1：灵感保存的落库完成信号 ──────────────────

    private fun inspirationEntity(id: String) = InspirationEntity(
        id = id,
        title = "t",
        body = "b",
        type = "note",
        status = "inbox",
        source_book_id = null,
        payload = "{}",
        created_at = "",
        device_id = null,
        revision = 1,
        updated_at = "",
        deleted_at = null,
    )

    @Test
    fun `save inspiration publishes Saved outcome with the entity id after real save`() = runTest {
        val entity = inspirationEntity("insp-ok")
        viewModel.onAction(ReaderAction.SaveInspiration(entity))
        val outcome = viewModel.inspirationSaveOutcome.first { it != null }
        assertEquals(
            "落库成功必须按实体 id 发布 Saved",
            InspirationSaveOutcome.Saved(seq = outcome!!.seq, requestId = "insp-ok"),
            outcome,
        )
        coVerify(exactly = 1) { noteRepository.saveInspiration(entity) }
    }

    @Test
    fun `save inspiration failure publishes Failed with the original message`() = runTest {
        coEvery { noteRepository.saveInspiration(any()) } throws RuntimeException("boom")
        viewModel.onAction(ReaderAction.SaveInspiration(inspirationEntity("insp-fail")))
        val outcome = viewModel.inspirationSaveOutcome.first { it != null }
        assertTrue("失败必须发布 Failed", outcome is InspirationSaveOutcome.Failed)
        outcome as InspirationSaveOutcome.Failed
        assertEquals("insp-fail", outcome.requestId)
        assertEquals("boom", outcome.message)
    }

    @Test
    fun `cancelled inspiration save never publishes an outcome`() = runTest {
        coEvery { noteRepository.saveInspiration(any()) } throws kotlinx.coroutines.CancellationException("cancelled")
        viewModel.onAction(ReaderAction.SaveInspiration(inspirationEntity("insp-cancel")))
        // UnconfinedTestDispatcher：io 协程同步执行完毕；取消异常原样抛出 → 不发布任何信号。
        runCurrent()
        assertNull("取消协程不能变成成功（也不得发布失败假象）", viewModel.inspirationSaveOutcome.value)
    }
}

/**
 * 内存版 [ReaderTextRuleDao]：MutableStateFlow 驱动，镜像 SQL 排序与
 * delete/update 返回语义（与 RulesRepositoryTest 中的 Fake 行为一致）。
 */
private class InMemoryReaderTextRuleDao : ReaderTextRuleDao {

    private val state = MutableStateFlow<List<ReaderTextRuleEntity>>(emptyList())

    override fun observeAll(): Flow<List<ReaderTextRuleEntity>> = state

    override fun observeByKind(kind: String): Flow<List<ReaderTextRuleEntity>> =
        state.map { rows -> rows.filter { it.kind == kind } }

    override fun observeById(id: String): Flow<ReaderTextRuleEntity?> =
        state.map { rows -> rows.firstOrNull { it.id == id } }

    override suspend fun getById(id: String): ReaderTextRuleEntity? =
        state.value.firstOrNull { it.id == id }

    override suspend fun upsert(entity: ReaderTextRuleEntity) {
        state.value = (state.value.filterNot { it.id == entity.id } + entity).sortedByPosition()
    }

    override suspend fun deleteCustom(id: String): Int {
        val existing = state.value.firstOrNull { it.id == id } ?: return 0
        if (existing.builtin) return 0
        state.value = state.value.filterNot { it.id == id }
        return 1
    }

    override suspend fun updateEnabled(id: String, enabled: Boolean, updatedAt: Long): Int {
        val idx = state.value.indexOfFirst { it.id == id }
        if (idx < 0) return 0
        val rows = state.value.toMutableList()
        rows[idx] = rows[idx].copy(enabled = enabled, updated_at = updatedAt)
        state.value = rows.sortedByPosition()
        return 1
    }

    override suspend fun updatePosition(id: String, position: Int, updatedAt: Long): Int {
        val idx = state.value.indexOfFirst { it.id == id }
        if (idx < 0) return 0
        val rows = state.value.toMutableList()
        rows[idx] = rows[idx].copy(position = position, updated_at = updatedAt)
        state.value = rows.sortedByPosition()
        return 1
    }

    private fun List<ReaderTextRuleEntity>.sortedByPosition(): List<ReaderTextRuleEntity> =
        sortedWith(compareBy<ReaderTextRuleEntity> { it.position }.thenBy { it.id })
}
