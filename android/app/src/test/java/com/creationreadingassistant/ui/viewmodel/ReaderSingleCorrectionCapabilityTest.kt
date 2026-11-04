package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.dao.ReaderTextRuleDao
import com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.ChapterReadRepository
import com.creationreadingassistant.data.repository.NoteRepository
import com.creationreadingassistant.data.repository.SearchIndexRepository
import com.creationreadingassistant.data.repository.SearchIndexScheduler
import com.creationreadingassistant.data.repository.TaxonomyRepository
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.feature.library.deletion.BookDeletionCoordinator
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.domain.model.EpubChapter
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.EpubDocument
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.locator.AnchorCacheStore
import com.creationreadingassistant.feature.reader.pager.PageIndexStore
import com.creationreadingassistant.feature.reader.pager.PagedReplacementAvailability
import com.creationreadingassistant.feature.reader.pager.PagerHealthStore
import com.creationreadingassistant.feature.reader.pager.ReaderPageIndexManager
import com.creationreadingassistant.feature.reader.rules.FakeReaderCorrectionDao
import com.creationreadingassistant.feature.reader.rules.RuleCommand
import com.creationreadingassistant.feature.reader.rules.RuleMutationResult
import com.creationreadingassistant.feature.reader.rules.RulesRepository
import com.creationreadingassistant.feature.reader.session.ReadingSessionRecorder
import com.creationreadingassistant.ui.screen.reader.ReaderReplacementCapability
import com.creationreadingassistant.ui.screen.reader.ReaderSheet
import com.creationreadingassistant.ui.screen.reader.readerReplacementCapability
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S07-F4.1 第（一）项：**单处纠错的能力门必须消费生产层的同一个事实源**。
 *
 * F4 的第一版把 capability 从 `ReaderLoadedContent` 类型推出来，`Epub` 一律映射成
 * `ESTIMATED_COORDINATES`。这在分页 EPUB 上是**错的**：分页引擎对 `EpubChapterSource`
 * 提前 return，给出的裁决是 `APPLIED / NO_EFFECTIVE_RULES / PARTIALLY_APPLIED /
 * ALL_SCOPES_OVERSIZED / UNVERIFIED_CHAPTER_LENGTHS`（全部 `Available`），只有
 * legacy/非分页路径才被 `effectiveReplacementAvailability` 遮蔽成 `ESTIMATED`。
 * 后果是「UI 给入口（读引擎裁决）、VM 拒绝保存（读类型推断）」的功能回归。
 *
 * 现在两端读**同一个** `pagerEngine.replacementAvailability`：UI 侧经
 * [ReaderAction.SetReplacementAvailability] 同步进 VM，VM 的
 * `SaveSingleCorrection` 边界直接消费它。本文件钉死的是：
 *
 * 1. 各 availability 经 `readerReplacementCapability` 的可用性（既有映射，不改）；
 * 2. **绕过 UI 直接构造命令**时，VM 按发布值放行或拒绝，并以真实 DAO 行数证明；
 * 3. 未发布 / 切书后 / 书身份不符 ⇒ 默认拒绝（绝不沿用上一本书的裁决）；
 * 4. 拒绝发生在 repository 写入之前（DAO 行数 0 而不是「写了再报错」）。
 *
 * 这些是**行为测试**：驱动真实 [ReaderViewModel]、真实 [RulesRepository]、
 * 真实 `FakeReaderCorrectionDao`，不是源码字符串守卫。
 *
 * 注意本文件**不再**比较「VM 的映射」与「写死的枚举常量」—— 那种断言把待证结论
 * 写进期望值，只证明同一个函数被调用两次（F4 的循环论证，Qoder ST-1）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSingleCorrectionCapabilityTest {

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
    private lateinit var correctionDao: FakeReaderCorrectionDao

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
        correctionDao = FakeReaderCorrectionDao()
        rulesRepository = RulesRepository(InMemoryRuleDao(), correctionDao)
        searchIndexRepository = mockk(relaxed = true)
        searchIndexScheduler = mockk(relaxed = true)

        io.mockk.every { taxonomyRepository.observeCategories() } returns flowOf(emptyList())
        io.mockk.every { taxonomyRepository.observeTags() } returns flowOf(emptyList())
        io.mockk.every { noteRepository.observeHighlightsByBook(any()) } returns flowOf(emptyList())
        io.mockk.every { noteRepository.observeNotesByBook(any()) } returns flowOf(emptyList())
        io.mockk.every { noteRepository.observeInspirationsByBook(any()) } returns flowOf(emptyList())
        io.mockk.every { bookRepository.observeSessionsByBook(any()) } returns flowOf(emptyList())
        io.mockk.every { chapterReadRepository.observeReadChapters(any()) } returns flowOf(emptyList())

        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        ioDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
    ): ReaderViewModel = ReaderViewModel(
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
        appScope = kotlinx.coroutines.CoroutineScope(testDispatcher),
        ioDispatcher = ioDispatcher,
        defaultDispatcher = UnconfinedTestDispatcher(),
    )

    // ── 1. availability → capability 的可用性（既有映射，防被误改） ──────

    @Test
    fun `paged replacement availability states that permit corrections are available`() {
        // 分页引擎能给出的全部「允许纠错」裁决。UI 与 VM 都读 readerReplacementCapability，
        // 因此这里钉的是两端共用的解释函数。
        listOf(
            PagedReplacementAvailability.APPLIED,
            PagedReplacementAvailability.NO_EFFECTIVE_RULES,
            PagedReplacementAvailability.PARTIALLY_APPLIED,
            PagedReplacementAvailability.ALL_SCOPES_OVERSIZED,
            PagedReplacementAvailability.UNVERIFIED_CHAPTER_LENGTHS,
        ).forEach { availability ->
            assertTrue(
                "$availability 必须允许单处纠错（否则分页 EPUB 会被误伤）",
                readerReplacementCapability(availability) is ReaderReplacementCapability.Available,
            )
        }
    }

    @Test
    fun `coordinate spaces that cannot carry a correction anchor are unavailable`() {
        listOf(
            PagedReplacementAvailability.ESTIMATED_COORDINATES,
            PagedReplacementAvailability.NON_SOURCE_COORDINATES,
            PagedReplacementAvailability.PAGER_ENGINE_DISABLED,
            PagedReplacementAvailability.SOURCE_UNAVAILABLE,
            PagedReplacementAvailability.INCOMPLETE_SCOPE,
            PagedReplacementAvailability.OVERSIZED_CURRENT_CHAPTER,
        ).forEach { availability ->
            val capability = readerReplacementCapability(availability)
            assertTrue(
                "$availability 不得判定为可锚定",
                capability is ReaderReplacementCapability.Unavailable,
            )
            assertTrue(
                (capability as ReaderReplacementCapability.Unavailable).message.isNotBlank(),
            )
        }
    }

    // ── 2. 分页 EPUB：发布 Available 的裁决 ⇒ 必须允许落库 ───────────────
    //
    // 这是 F4 P1 回归的核心反例：同一本 EPUB，UI 侧引擎裁决 Available，
    // VM 必须放行。F4 第一版在这里会拒绝（因为它按类型判 ESTIMATED）。

    @Test
    fun `a paged epub with applied replacement accepts a correction and writes one row`() = runTest {
        loadBook("book-epub-paged", epubContent())
        publishAvailability("book-epub-paged", PagedReplacementAvailability.APPLIED)
        // EPUB 的选区走 globalOffset 槽（全书 source 坐标）
        viewModel.onAction(ReaderAction.SetSelectedText("错字", -1, 100, 102))

        dispatchCorrection("book-epub-paged")

        val result = viewModel.ruleMutationResult.value
        assertTrue("分页 EPUB 的纠错必须被放行，实际为 $result", result is RuleMutationResult.Saved)
        val rows = correctionDao.listForBook("book-epub-paged")
        assertEquals("分页 EPUB 的纠错必须真的落库", 1, rows.size)
        assertEquals(100, rows.single().source_start)
        assertEquals(102, rows.single().source_end)
    }

    @Test
    fun `a paged epub with no effective rules still reaches the correction save path`() = runTest {
        // NO_EFFECTIVE_RULES = 「引擎可用，只是还没有规则」——同样必须允许纠错，
        // 否则用户在新书里根本建立不了第一条单处纠错。
        loadBook("book-epub-norules", epubContent())
        publishAvailability("book-epub-norules", PagedReplacementAvailability.NO_EFFECTIVE_RULES)
        viewModel.onAction(ReaderAction.SetSelectedText("错字", -1, 100, 102))

        dispatchCorrection("book-epub-norules")

        assertTrue(
            "NO_EFFECTIVE_RULES 必须允许纠错",
            viewModel.ruleMutationResult.value is RuleMutationResult.Saved,
        )
        assertEquals(1, correctionDao.listForBook("book-epub-norules").size)
    }

    // ── 3. legacy / 非分页 EPUB：引擎遮蔽为不可用 ⇒ 拒绝且不落库 ─────────

    @Test
    fun `a legacy epub with estimated coordinates is rejected before any write`() = runTest {
        // 关键反例：**不走 UI**。直接加载一本 EPUB 并发布引擎的遮蔽裁决，
        // 再手工构造 SaveSingleCorrection 送入 VM —— 模拟未来的任何新增入口。
        loadBook("book-epub-legacy", epubContent())
        publishAvailability(
            "book-epub-legacy",
            PagedReplacementAvailability.ESTIMATED_COORDINATES,
        )
        viewModel.onAction(ReaderAction.SetSelectedText("错字", -1, 100, 102))

        dispatchCorrection("book-epub-legacy")

        val result = viewModel.ruleMutationResult.value
        assertTrue(
            "估算坐标的 legacy EPUB 上绕过 UI 的纠错必须被拒绝，实际为 $result",
            result is RuleMutationResult.NotAnchorable,
        )
        assertTrue(
            "拒绝原因必须点明格式不支持，实际为 ${(result as RuleMutationResult.NotAnchorable).reason}",
            result.reason.contains("单处纠错"),
        )
        assertEquals(
            "拒绝后不得落库任何纠错记录",
            0,
            correctionDao.listForBook("book-epub-legacy").size,
        )
    }

    @Test
    fun `a legacy epub with the pager engine disabled is rejected`() = runTest {
        loadBook("book-epub-off", epubContent())
        publishAvailability("book-epub-off", PagedReplacementAvailability.PAGER_ENGINE_DISABLED)
        viewModel.onAction(ReaderAction.SetSelectedText("错字", -1, 100, 102))

        dispatchCorrection("book-epub-off")

        assertTrue(viewModel.ruleMutationResult.value is RuleMutationResult.NotAnchorable)
        assertEquals(0, correctionDao.listForBook("book-epub-off").size)
    }

    @Test
    fun `a bypassed correction on markdown is rejected at the capability gate`() = runTest {
        loadBook("book-md", markdownContent())
        publishAvailability("book-md", PagedReplacementAvailability.NON_SOURCE_COORDINATES)
        // Markdown 的选区落在 rangeStart 槽，同样能形成合法 source range
        viewModel.onAction(ReaderAction.SetSelectedText("错字", 100, -1, 102))

        dispatchCorrection("book-md")

        assertTrue(
            "Markdown 上绕过 UI 的纠错必须被能力门拒绝",
            viewModel.ruleMutationResult.value is RuleMutationResult.NotAnchorable,
        )
        assertEquals(0, correctionDao.listForBook("book-md").size)
    }

    // ── 4. 未发布 / 切书 / 书身份不符 ⇒ 默认拒绝 ─────────────────────────

    @Test
    fun `an unpublished availability rejects even a well formed correction`() = runTest {
        // 新书打开后引擎尚未裁决：默认拒绝，不得沿用任何先前的裁决。
        loadBook("book-txt-unpublished", txtContent())
        viewModel.onAction(ReaderAction.SetSelectedText("错字", 100, -1, 102))

        dispatchCorrection("book-txt-unpublished")

        assertTrue(
            "未发布裁决时必须默认拒绝，实际为 ${viewModel.ruleMutationResult.value}",
            viewModel.ruleMutationResult.value is RuleMutationResult.NotAnchorable,
        )
        assertEquals(0, correctionDao.listForBook("book-txt-unpublished").size)
    }

    @Test
    fun `a book switch invalidates the previous book availability`() = runTest {
        // 切书是「上一本书的裁决不再适用」的权威时刻。新书自己的裁决到达前，
        // 一次合法形状的纠错必须被拒 —— 绝不把上一本书的可用性带过来。
        loadBook("book-a", txtContent())
        publishAvailability("book-a", PagedReplacementAvailability.APPLIED)
        viewModel.onAction(ReaderAction.SetSelectedText("错字", 100, -1, 102))

        loadBook("book-b", txtContent())
        viewModel.onAction(ReaderAction.SetSelectedText("错字", 100, -1, 102))

        dispatchCorrection("book-b")

        assertTrue(
            "切书后未重新发布时必须默认拒绝",
            viewModel.ruleMutationResult.value is RuleMutationResult.NotAnchorable,
        )
        assertEquals(0, correctionDao.listForBook("book-b").size)
    }

    @Test
    fun `a stale publish from another book is ignored`() = runTest {
        // 迟到的发布（组合顺序：availability 先算、书身份后落定）不得改写当前书的裁决。
        // 这里当前书是 book-current 且已发布 APPLIED；来自 book-stale 的发布必须被丢弃。
        loadBook("book-current", txtContent())
        publishAvailability("book-current", PagedReplacementAvailability.APPLIED)
        publishAvailability("book-stale", PagedReplacementAvailability.ESTIMATED_COORDINATES)
        viewModel.onAction(ReaderAction.SetSelectedText("错字", 100, -1, 102))

        dispatchCorrection("book-current")

        assertTrue(
            "别书的迟到发布不得覆盖当前书裁决，实际为 ${viewModel.ruleMutationResult.value}",
            viewModel.ruleMutationResult.value is RuleMutationResult.Saved,
        )
        assertEquals(1, correctionDao.listForBook("book-current").size)
    }

    // ── 5. 能力门放行后，第 2/3 道门仍然生效 ────────────────────────────

    @Test
    fun `txt with an available capability accepts a correction anchored from the selection`() =
        runTest {
            loadBook("book-txt", txtContent())
            publishAvailability("book-txt", PagedReplacementAvailability.APPLIED)
            // TXT 用 rangeStart 槽，sourceEnd 为半开终点
            viewModel.onAction(ReaderAction.SetSelectedText("错字", 100, -1, 102))

            dispatchCorrection("book-txt")

            val result = viewModel.ruleMutationResult.value
            assertTrue("TXT 的真实 source 坐标必须允许锚定，实际为 $result", result is RuleMutationResult.Saved)
            val rows = correctionDao.listForBook("book-txt")
            assertEquals(1, rows.size)
            assertEquals("锚点起点必须来自当前选区", 100, rows.single().source_start)
            assertEquals("锚点终点必须来自当前选区", 102, rows.single().source_end)
            assertEquals("错字", rows.single().find_text)
            assertEquals("正字", rows.single().replace_text)
        }

    @Test
    fun `txt still rejects a correction whose find text is not the current selection`() = runTest {
        // 能力门放行后，第 2 道门（文字一致）必须仍在 —— 否则纠错会挂到别的文字上。
        loadBook("book-txt", txtContent())
        publishAvailability("book-txt", PagedReplacementAvailability.APPLIED)
        viewModel.onAction(ReaderAction.SetSelectedText("错字", 100, -1, 102))

        viewModel.onAction(
            ReaderAction.ExecuteRuleCommand(
                "book-txt",
                RuleCommand.SaveSingleCorrection(
                    sourceStart = -1,
                    sourceEnd = -1,
                    findText = "完全不同的文本",
                    replaceText = "x",
                ),
            ),
        )

        assertTrue(
            "TXT 上也必须拒绝 findText 与选区不一致的纠错",
            viewModel.ruleMutationResult.value is RuleMutationResult.NotAnchorable,
        )
        assertEquals(0, correctionDao.listForBook("book-txt").size)
    }

    @Test
    fun `txt without an anchorable selection is rejected even when capability allows`() = runTest {
        // 第 3 道门：没有选区就没有锚点（sourceEnd = -1 是 F3 之前的形态）。
        loadBook("book-txt", txtContent())
        publishAvailability("book-txt", PagedReplacementAvailability.APPLIED)
        viewModel.onAction(ReaderAction.SetSelectedText("错字", 100, -1, -1))

        dispatchCorrection("book-txt")

        assertTrue(
            "无可用锚点时不得写库",
            viewModel.ruleMutationResult.value is RuleMutationResult.NotAnchorable,
        )
        assertEquals(0, correctionDao.listForBook("book-txt").size)
    }

    // ── 6. 事实源同源：UI 与 VM 读到的是同一个值 ─────────────────────────
    //
    // 断言「VM 实际用来决策的 availability」== 「UI 发布的那一个值」。
    // 左值通过行为观测（放行/拒绝）反推，右值是发布的输入 —— 不是把同一个
    // 枚举常量分别写死在两边（那正是 F4 的循环论证）。

    @Test
    fun `the view model decides on exactly the availability the producer published`() = runTest {
        // 分页 EPUB 引擎侧可用：UI 会渲染「替换」入口，VM 必须按同一个值放行。
        loadBook("book-same-source", epubContent())
        publishAvailability("book-same-source", PagedReplacementAvailability.PARTIALLY_APPLIED)
        viewModel.onAction(ReaderAction.SetSelectedText("错字", -1, 100, 102))

        dispatchCorrection("book-same-source")

        // UI 侧的判据（工具栏摘槽用的那一个）：
        val uiSeesEntry = readerReplacementCapability(PagedReplacementAvailability.PARTIALLY_APPLIED)
        val vmAccepted = viewModel.ruleMutationResult.value is RuleMutationResult.Saved
        assertEquals(
            "UI 提供入口时 VM 必须放行（同一事实源）",
            uiSeesEntry is ReaderReplacementCapability.Available,
            vmAccepted,
        )
        assertEquals(1, correctionDao.listForBook("book-same-source").size)
    }

    // ── 辅助 ─────────────────────────────────────────────────────────

    /** 模拟 UI 侧 `LaunchedEffect(pagerEngine.replacementAvailability)` 的发布。 */
    private fun publishAvailability(bookId: String, availability: PagedReplacementAvailability) {
        viewModel.onAction(ReaderAction.SetReplacementAvailability(bookId, availability))
    }

    private fun dispatchCorrection(bookId: String) {
        viewModel.onAction(
            ReaderAction.ExecuteRuleCommand(
                bookId,
                RuleCommand.SaveSingleCorrection(
                    sourceStart = -1,
                    sourceEnd = -1,
                    findText = "错字",
                    replaceText = "正字",
                ),
            ),
        )
    }

    private suspend fun loadBook(bookId: String, content: ReaderLoadedContent) {
        coEvery { documentLoader.load(bookId) } returns ReaderLoadedBook(
            id = bookId,
            title = "Test Book",
            author = "Author",
            originalFileName = "test",
            sizeBytes = 1024,
            savedReadingTimeMs = 0L,
            initialProgressPercent = 0f,
            content = content,
        )
        viewModel.onAction(ReaderAction.OpenBook(bookId))
        // 真实 IO 线程加载：轮询等待 uiState 落定，不用 Thread.sleep 猜时长
        val deadline = System.currentTimeMillis() + 5_000
        while (viewModel.uiState.value.loadedBook?.id != bookId) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("加载 $bookId 超时（loadedBook 未落定）")
            }
            Thread.sleep(10)
        }
    }

    private fun epubContent(): ReaderLoadedContent.Epub {
        val book = EpubBook(
            id = "book-epub",
            title = "Test Book",
            author = "Author",
            chapters = listOf(
                EpubChapter(
                    title = "Chapter 0",
                    entryPath = "chapter0.xhtml",
                    chapterDir = "",
                    cachedEpubPath = "/tmp/fake.epub",
                    estimatedTextLength = 1000,
                ),
            ),
            localUri = "file:///fake.epub",
            cachedEpubPath = "/tmp/fake.epub",
        )
        return ReaderLoadedContent.Epub(
            book = book,
            document = mockk<EpubDocument>(relaxed = true),
            initialChapterIndex = 0,
            initialChapterBlocks = listOf(DocBlock.Text("Hello")),
            initialOffsetInChapter = 0,
        )
    }

    private fun markdownContent(): ReaderLoadedContent.Markdown = ReaderLoadedContent.Markdown(
        document = MarkdownDocument("# One\nfirst\n# Two\nsecond"),
        ownedTempFile = null,
        initialAbsoluteOffset = 0,
    )

    private fun txtContent(): ReaderLoadedContent.Text = ReaderLoadedContent.Text(
        fullText = "第一章\n错字在这里\n第二章",
        streamingDocument = null,
        fileIndex = null,
        sourceFile = null,
        ownedTempFile = null,
        initialAbsoluteOffset = 0,
    )
}

/**
 * 本类专用的最小 [ReaderTextRuleDao]：关注点是纠错能力门，
 * 只需要一个可用的 [RulesRepository] 来构造真实 [ReaderViewModel]。
 */
private class InMemoryRuleDao : ReaderTextRuleDao {

    private var rows: List<ReaderTextRuleEntity> = emptyList()

    override fun observeAll(): Flow<List<ReaderTextRuleEntity>> = flowOf(rows)

    override fun observeByKind(kind: String): Flow<List<ReaderTextRuleEntity>> =
        flowOf(rows.filter { it.kind == kind })

    override fun observeById(id: String): Flow<ReaderTextRuleEntity?> =
        flowOf(rows.firstOrNull { it.id == id })

    override suspend fun getById(id: String): ReaderTextRuleEntity? = rows.firstOrNull { it.id == id }

    override suspend fun upsert(entity: ReaderTextRuleEntity) {
        rows = rows.filterNot { it.id == entity.id } + entity
    }

    override suspend fun deleteCustom(id: String): Int {
        val before = rows.size
        rows = rows.filterNot { it.id == id }
        return before - rows.size
    }

    override suspend fun updateEnabled(id: String, enabled: Boolean, updatedAt: Long): Int {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx < 0) return 0
        rows = rows.toMutableList().apply {
            this[idx] = this[idx].copy(enabled = enabled, updated_at = updatedAt)
        }
        return 1
    }

    override suspend fun updatePosition(id: String, position: Int, updatedAt: Long): Int {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx < 0) return 0
        rows = rows.toMutableList().apply {
            this[idx] = this[idx].copy(position = position, updated_at = updatedAt)
        }
        return 1
    }
}
