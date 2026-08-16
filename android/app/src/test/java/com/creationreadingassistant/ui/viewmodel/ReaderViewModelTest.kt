package com.creationreadingassistant.ui.viewmodel

import app.cash.turbine.test
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.dao.ReaderTextRuleDao
import com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.NoteRepository
import com.creationreadingassistant.data.repository.TaxonomyRepository
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.data.settings.TtsResume
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.domain.model.EpubChapter
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.EpubDocument
import com.creationreadingassistant.feature.reader.doc.TxtFileScanner
import com.creationreadingassistant.feature.reader.locator.AnchorCacheStore
import com.creationreadingassistant.feature.reader.pager.PageIndexStore
import com.creationreadingassistant.feature.reader.pager.PagerHealthStore
import com.creationreadingassistant.feature.reader.pager.ReaderPageIndexManager
import com.creationreadingassistant.feature.reader.rules.RuleCommand
import com.creationreadingassistant.feature.reader.rules.RuleKind
import com.creationreadingassistant.feature.reader.rules.RuleMutationResult
import com.creationreadingassistant.feature.reader.rules.RulesRepository
import com.creationreadingassistant.ui.screen.reader.ReaderSheet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var documentLoader: ReaderDocumentLoader
    private lateinit var noteRepository: NoteRepository
    private lateinit var taxonomyRepository: TaxonomyRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var epubRepository: EpubRepository
    private lateinit var settingsStore: SettingsStore
    private lateinit var anchorCacheStore: AnchorCacheStore
    private lateinit var pageIndexStore: PageIndexStore
    private lateinit var pagerHealthStore: PagerHealthStore
    private lateinit var pageIndexManager: ReaderPageIndexManager
    private lateinit var aiClient: AiClient
    private lateinit var rulesRepository: RulesRepository
    private lateinit var ruleDao: InMemoryReaderTextRuleDao

    private lateinit var viewModel: ReaderViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        documentLoader = mockk(relaxed = true)
        noteRepository = mockk(relaxed = true)
        taxonomyRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        epubRepository = mockk(relaxed = true)
        settingsStore = mockk(relaxed = true)
        anchorCacheStore = mockk(relaxed = true)
        pageIndexStore = mockk(relaxed = true)
        pagerHealthStore = mockk(relaxed = true)
        pageIndexManager = mockk(relaxed = true)
        aiClient = mockk(relaxed = true)

        ruleDao = InMemoryReaderTextRuleDao()
        rulesRepository = RulesRepository(ruleDao)

        every { taxonomyRepository.observeCategories() } returns flowOf(emptyList())
        every { taxonomyRepository.observeTags() } returns flowOf(emptyList())
        every { bookRepository.observeSessionsByBook(any()) } returns flowOf(emptyList())

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
            epubRepository = epubRepository,
            settingsStore = settingsStore,
            anchorCacheStore = anchorCacheStore,
            pageIndexStore = pageIndexStore,
            pagerHealthStore = pagerHealthStore,
            pageIndexManager = pageIndexManager,
            aiClient = aiClient,
            rulesRepository = rulesRepository,
            ioDispatcher = ioDispatcher,
            defaultDispatcher = UnconfinedTestDispatcher(),
        )
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
        coVerify(timeout = 2000) { epubRepository.saveProgress("book-1", 2, any()) }
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
        viewModel.onAction(ReaderAction.SetSelectedText("hello", 5, 100))
        assertEquals("hello", viewModel.screenState.value.selectedText)
        assertEquals(5, viewModel.screenState.value.selectedRangeStart)
        assertEquals(100, viewModel.screenState.value.selectedGlobalOffset)

        viewModel.onAction(ReaderAction.ClearSelection)
        assertEquals("", viewModel.screenState.value.selectedText)
        assertEquals(-1, viewModel.screenState.value.selectedRangeStart)
        assertEquals(-1, viewModel.screenState.value.selectedGlobalOffset)
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
