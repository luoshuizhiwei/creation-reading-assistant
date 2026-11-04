package com.creationreadingassistant.ui.viewmodel

import app.cash.turbine.test
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.dao.ReaderTextRuleDao
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.ChapterReadRepository
import com.creationreadingassistant.data.repository.NoteRepository
import com.creationreadingassistant.data.repository.SearchIndexRepository
import com.creationreadingassistant.data.repository.SearchIndexScheduler
import com.creationreadingassistant.data.repository.TaxonomyRepository
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.feature.library.deletion.BookDeletionCoordinator
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.locator.AnchorCacheStore
import com.creationreadingassistant.feature.reader.pager.PageIndexStore
import com.creationreadingassistant.feature.reader.pager.PagerHealthStore
import com.creationreadingassistant.feature.reader.pager.ReaderPageIndexManager
import com.creationreadingassistant.feature.reader.rules.FakeReaderCorrectionDao
import com.creationreadingassistant.feature.reader.rules.RulesRepository
import com.creationreadingassistant.feature.reader.session.ReadingSessionRecorder
import com.creationreadingassistant.ui.screen.reader.ReaderScreenState
import com.creationreadingassistant.ui.screen.reader.ReaderSelectionSnapshot
import com.creationreadingassistant.ui.screen.reader.ReaderSheet
import com.creationreadingassistant.ui.screen.reader.consumesSelection
import com.creationreadingassistant.ui.screen.reader.withSelectionCaptured
import com.creationreadingassistant.ui.screen.reader.withSelectionSnapshotReleased
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S07-F2.4：长生命周期弹层的**消费者快照**（[ReaderSelectionSnapshot]）生命周期。
 *
 * 背景（F2.3 复核结论）：滚动 TXT 在 TTS 跟读时经 `scrollToItem` 程序化滚动，拥有当前
 * 选区的 ReadingUnit 随后 dispose 并**正确地**清除正文活动选区。笔记 / 规则 / AI 解读 /
 * 灵感 / AI 助读这些弹层若继续读实时 VM 选区，就会在正文清理后丢摘录、丢保存定位，
 * 规则页还会因 `initialReplaceText` 变化重建 `tab` / `editing`，丢掉用户正在编辑的草稿。
 *
 * 本类锁定的是「打开时原子捕获、之后只改活动选区不动快照、关闭即释放」这三条：
 * 用真实 [ReaderViewModel] reducer 驱动，不走 Compose，不替代任何运行时 UI 断言。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSelectionSnapshotTest {

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

    private lateinit var ruleDao: ReaderTextRuleDao

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
        ruleDao = SnapshotTestRuleDao()
        rulesRepository = RulesRepository(ruleDao, FakeReaderCorrectionDao())
        searchIndexRepository = mockk(relaxed = true)
        searchIndexScheduler = mockk(relaxed = true)
        appScope = CoroutineScope(testDispatcher)

        everyFlowDefaults()
        viewModel = createViewModel()
    }

    private fun everyFlowDefaults() {
        io.mockk.every { taxonomyRepository.observeCategories() } returns flowOf(emptyList())
        io.mockk.every { taxonomyRepository.observeTags() } returns flowOf(emptyList())
        io.mockk.every { noteRepository.observeHighlightsByBook(any()) } returns flowOf(emptyList())
        io.mockk.every { noteRepository.observeNotesByBook(any()) } returns flowOf(emptyList())
        io.mockk.every { noteRepository.observeInspirationsByBook(any()) } returns flowOf(emptyList())
        io.mockk.every { bookRepository.observeSessionsByBook(any()) } returns flowOf(emptyList())
        io.mockk.every { chapterReadRepository.observeReadChapters(any()) } returns flowOf(emptyList())
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
        appScope = appScope,
        ioDispatcher = ioDispatcher,
        defaultDispatcher = UnconfinedTestDispatcher(),
    )

    // ── 前置 / 断言辅助 ────────────────────────────────────────────

    private fun seedSelection(
        text: String = "一期一会",
        rangeStart: Int = 5,
        globalOffset: Int = 100,
        sourceEnd: Int = 120,
    ) {
        viewModel.onAction(ReaderAction.SetSelectedText(text, rangeStart, globalOffset, sourceEnd))
    }

    private fun snapshot(): ReaderSelectionSnapshot? = viewModel.screenState.value.selectionSnapshot

    private fun assertSnapshotEquals(
        stage: String,
        text: String = "一期一会",
        rangeStart: Int = 5,
        globalOffset: Int = 100,
        sourceEnd: Int = 120,
    ) {
        val s = snapshot()
        assertEquals("$stage: 快照不得为 null", ReaderSelectionSnapshot(text, rangeStart, globalOffset, sourceEnd), s)
    }

    private fun assertActiveSelectionCleared(stage: String) {
        val s = viewModel.screenState.value
        assertEquals("$stage: 活动 selectedText 必须清空", "", s.selectedText)
        assertEquals("$stage: 活动 selectedRangeStart 必须复位", -1, s.selectedRangeStart)
        assertEquals("$stage: 活动 selectedGlobalOffset 必须复位", -1, s.selectedGlobalOffset)
        assertEquals("$stage: 活动 selectedSourceEnd 必须复位", -1, s.selectedSourceEnd)
    }

    // ── 1. 打开消费者时原子捕获四个字段 ────────────────────────────
    @Test
    fun `opening a consumer sheet captures all four selection fields atomically`() {
        seedSelection()

        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.AI_EXPLAIN))

        assertSnapshotEquals("打开 AI 解读")
    }

    @Test
    fun `every audited consumer sheet captures the snapshot`() {
        listOf(
            ReaderSheet.RULES,
            ReaderSheet.AI_EXPLAIN,
            ReaderSheet.INSPIRATION,
            ReaderSheet.AI_ASSIST,
        ).forEach { sheet ->
            viewModel.onAction(ReaderAction.CloseSheet)
            seedSelection()
            viewModel.onAction(ReaderAction.OpenSheet(sheet))

            assertSnapshotEquals("打开 $sheet")
        }
    }

    @Test
    fun `non consumer sheets are classified as not consuming selection`() {
        listOf(
            ReaderSheet.TOC,
            ReaderSheet.NOTES,
            ReaderSheet.SETTINGS,
            ReaderSheet.PROGRESS,
            ReaderSheet.SEARCH,
            ReaderSheet.BOOK_INFO,
            ReaderSheet.THEME,
            ReaderSheet.DICTIONARY,
        ).forEach { sheet ->
            assertFalse("$sheet 不应被判为选区消费者", sheet.consumesSelection())
        }
    }

    // ── 2. 捕获后 ClearSelection 只清活动选区，不动快照 ────────────
    @Test
    fun `clear selection after capture clears the active fields but keeps the snapshot`() {
        // 这正是 F2.4 的核心：ReadingUnit dispose 派发 ClearSelection 后，
        // 弹层里显示的摘录与保存定位必须还在（旧实现会一起被清掉）。
        seedSelection()
        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.AI_EXPLAIN))

        viewModel.onAction(ReaderAction.ClearSelection)

        assertActiveSelectionCleared("ClearSelection 后")
        assertSnapshotEquals("ClearSelection 后快照")
    }

    @Test
    fun `a page turn after capture clears the active fields but keeps the snapshot`() {
        seedSelection()
        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.RULES))

        viewModel.onAction(ReaderAction.PageTurn)

        assertActiveSelectionCleared("PageTurn 后")
        assertSnapshotEquals("PageTurn 后快照")
    }

    @Test
    fun `a chapter change after capture clears the active fields but keeps the snapshot`() {
        seedSelection()
        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.INSPIRATION))

        viewModel.onAction(ReaderAction.LoadChapter("book-1", 2))

        assertActiveSelectionCleared("LoadChapter 后")
        assertSnapshotEquals("LoadChapter 后快照")
    }

    // ── 3. 捕获后正文建立新选区，旧快照不变 ────────────────────────
    @Test
    fun `establishing a new body selection after capture leaves the snapshot untouched`() {
        seedSelection()
        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.AI_EXPLAIN))

        // 用户在弹层打开期间又长按了正文另一段：活动选区变了，但弹层快照必须是打开时那份。
        viewModel.onAction(ReaderAction.SetSelectedText("新选区", 900, 2000, 2010))

        val s = viewModel.screenState.value
        assertEquals("活动选区应更新", "新选区", s.selectedText)
        assertSnapshotEquals("新选区建立后快照", text = "一期一会", rangeStart = 5, globalOffset = 100, sourceEnd = 120)
    }

    // ── 4. 关闭消费者即释放快照 ────────────────────────────────────
    @Test
    fun `closing the consumer releases the snapshot`() {
        seedSelection()
        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.AI_EXPLAIN))
        assertSnapshotEquals("打开后")

        viewModel.onAction(ReaderAction.CloseSheet)

        assertNull("关闭弹层必须释放快照，避免下次打开复用旧选区", snapshot())
    }

    @Test
    fun `switching to a non consuming sheet releases the snapshot`() {
        seedSelection()
        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.AI_EXPLAIN))
        assertSnapshotEquals("打开消费者后")

        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.TOC))

        assertNull("切到不消费选区的 sheet 必须释放快照", snapshot())
    }

    // ── 5. 无选区打开 / 顶栏直接打开：不得复用旧快照 ───────────────
    @Test
    fun `opening a consumer without a selection keeps the snapshot null`() {
        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.INSPIRATION))

        assertNull("无选区打开灵感必须没有摘录（保持原语义）", snapshot())
    }

    @Test
    fun `a top bar entry opened without selection does not reuse the previous snapshot`() {
        // 要求 9：上一轮从选区打开过灵感并关闭；这一轮从顶栏（无选区）打开，
        // 必须仍是「无摘录」，不得把上一轮的旧选区带出来。
        seedSelection()
        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.INSPIRATION))
        assertSnapshotEquals("第一轮从选区打开")
        // 第一轮结束：清选区并关弹层
        viewModel.onAction(ReaderAction.ClearSelection)
        viewModel.onAction(ReaderAction.CloseSheet)

        // 第二轮从顶栏打开（此刻没有活动选区）
        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.INSPIRATION))

        assertNull("顶栏直接打开且无选区时不得复用上一轮快照", snapshot())
    }

    // ── 6. 笔记：打开捕获 / 关闭释放 ──────────────────────────────
    @Test
    fun `opening the note dialog captures the snapshot`() {
        seedSelection()

        viewModel.onAction(ReaderAction.SetNoteOpen(true))

        assertSnapshotEquals("打开笔记")
        assertTrue(viewModel.screenState.value.noteOpen)
    }

    @Test
    fun `closing the note dialog releases the snapshot and clears the active selection`() {
        seedSelection()
        viewModel.onAction(ReaderAction.SetNoteOpen(true))

        viewModel.onAction(ReaderAction.SetNoteOpen(false))

        assertNull("关闭笔记必须释放快照", snapshot())
        assertFalse(viewModel.screenState.value.noteOpen)
    }

    @Test
    fun `the note snapshot survives a body clear so save keeps excerpt and locator source`() {
        seedSelection()
        viewModel.onAction(ReaderAction.SetNoteOpen(true))

        viewModel.onAction(ReaderAction.ClearSelection)

        assertActiveSelectionCleared("笔记打开期间清选区")
        assertSnapshotEquals("笔记快照")
    }

    // ── 8. 切书：旧书的选区数据必须整体作废（S07-F4 / F4.1） ──────────
    //
    // F4 只释放了快照，并把「活动选区保留」写成了产品契约。这是错的：活动选区四字段
    // 与 showColorRow 属于**上一本书**的坐标空间，留在新书会话里会导致
    // (a) 工具条带着上一本的文字显示、(b) 保存高亮/笔记/灵感/纠错消费旧坐标、
    // (c) 新书打开消费者时 `withSelectionCaptured()` 把旧数据**重新捕获**成一份
    // 看似合法的快照。F4.1 把「切书 = 旧书选区整体作废」收敛进同一处。

    @Test
    fun `switching books clears all five active selection fields`() {
        seedSelection()
        viewModel.onAction(ReaderAction.ToggleColorRow) // 二级视图也要复位

        viewModel.onAction(ReaderAction.OpenBook("book-2"))

        assertActiveSelectionCleared("切书后")
        assertFalse("切书后色板行必须收起", viewModel.screenState.value.showColorRow)
    }

    @Test
    fun `switching books releases the consumer snapshot`() {
        // F2.4 只保证了「关闭弹层 / 切到非消费者释放」，但切书**不一定经过 CloseSheet**
        // （从书架直接打开另一本、深链、返回栈重建）。同一个 ViewModel 实例被复用时，
        // 旧书的摘录文字与坐标会跨书残留 —— 比「复用上一轮快照」更糟，因为坐标
        // 属于另一本书：用户在新书里点保存，落的是旧书的 offset。
        seedSelection()
        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.INSPIRATION))
        assertSnapshotEquals("旧书打开消费者后")

        viewModel.onAction(ReaderAction.OpenBook("book-2"))

        assertNull("切书必须释放消费者快照，旧书坐标不得跨书残留", snapshot())
    }

    @Test
    fun `a fresh selection cannot be recaptured from the previous book after a switch`() {
        // F4.1 的核心反例：F4 只释放快照，活动选区仍在 —— 于是新书里打开消费者时
        // `withSelectionCaptured()` 会把**旧书的文字与坐标**重新捕获成一份新快照。
        // 这比「复用旧快照」更隐蔽：它看起来是一次合法的捕获。
        seedSelection()
        viewModel.onAction(ReaderAction.OpenBook("book-2"))
        // 新书会话：用户还没有在正文里选任何东西
        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.RULES))

        assertNull(
            "新书打开消费者不得重新捕获旧书的文字与坐标",
            snapshot(),
        )
        assertEquals("新书活动选区必须为空", "", viewModel.screenState.value.selectedText)
    }

    @Test
    fun `the consumer UI state is closed on a book switch`() {
        // `sheet` / `noteOpen` / `noteBody` 都是仍持有旧选区语义的消费者状态：
        // 笔记弹层带着旧书摘录、规则页带着旧书草稿。切书必须一并收回。
        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.RULES))
        viewModel.onAction(ReaderAction.SetNoteOpen(true))
        viewModel.onAction(ReaderAction.SetNoteBody("旧书笔记正文"))

        viewModel.onAction(ReaderAction.OpenBook("book-2"))

        val s = viewModel.screenState.value
        assertNull("切书必须关闭旧书的弹层", s.sheet)
        assertFalse("切书必须关闭旧书的笔记弹层", s.noteOpen)
        assertEquals("切书必须清掉旧书的笔记草稿", "", s.noteBody)
    }

    @Test
    fun `reopening the same book does not release an in flight snapshot`() {
        // 反向钉子：释放判据是「书身份变化」，不是「调用了 OpenBook」。
        // 同书重开（切章失败后的重试、返回栈重建）必须保留消费者快照，
        // 否则用户从灵感弹层返回正文再回到弹层时摘录会凭空消失。
        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        seedSelection()
        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.INSPIRATION))
        assertSnapshotEquals("打开后")

        viewModel.onAction(ReaderAction.OpenBook("book-1"))

        assertSnapshotEquals("同书重开不得释放快照")
        assertEquals(
            "同书重开不得清活动选区",
            "一期一会",
            viewModel.screenState.value.selectedText,
        )
    }

    @Test
    fun `a force reload rebuilds the document so it clears the selection too`() {
        // force 重载（错误页重试）会重新走 documentLoader.load，内容身份整体重建 ——
        // 旧选区的坐标指向被丢弃的文档实例，必须与切书同样作废。
        // 这里把「内容会重建」这一事实钉成契约，而不是依赖分支顺序碰巧成立。
        viewModel.onAction(ReaderAction.OpenBook("book-1"))
        seedSelection()
        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.RULES))

        viewModel.onAction(ReaderAction.Retry)

        assertActiveSelectionCleared("force 重载后")
        assertNull("force 重载后快照必须释放", snapshot())
    }

    @Test
    fun `a book switch clears the stale rule feedback too`() {
        // 与选区同源：旧书的写入反馈不得展示在新书会话里（F4 收口项）。
        seedSelection()
        viewModel.onAction(ReaderAction.OpenSheet(ReaderSheet.RULES))
        assertSnapshotEquals("打开后")

        viewModel.onAction(ReaderAction.OpenBook("book-2"))

        assertNull("切书必须清空旧书规则写入反馈", viewModel.ruleMutationResult.value)
    }

    // ── 9. 纯函数层：捕获 / 释放语义 ───────────────────────────────
    @Test
    fun `capture is a no-op snapshot when there is no selection`() {
        val blank = ReaderScreenState()
        assertNull(blank.withSelectionCaptured().selectionSnapshot)
        assertNull(blank.copy(selectedText = "   ").withSelectionCaptured().selectionSnapshot)
    }

    @Test
    fun `release always yields null snapshot and never touches the active selection`() {
        val seeded = ReaderScreenState(
            selectedText = "keep",
            selectedRangeStart = 1,
            selectedGlobalOffset = 2,
            selectedSourceEnd = 3,
        ).withSelectionCaptured()

        val released = seeded.withSelectionSnapshotReleased()

        assertNull(released.selectionSnapshot)
        assertEquals("释放快照不得改动活动选区", "keep", released.selectedText)
        assertEquals(1, released.selectedRangeStart)
        assertEquals(2, released.selectedGlobalOffset)
        assertEquals(3, released.selectedSourceEnd)
    }

    @Test
    fun `clearing the active selection never releases the snapshot`() {
        // 两个语义必须正交：withSelectionCleared 只管活动选区，快照归快照。
        val seeded = ReaderScreenState(
            selectedText = "keep",
            selectedRangeStart = 1,
            selectedGlobalOffset = 2,
            selectedSourceEnd = 3,
        ).withSelectionCaptured()

        val cleared = seeded.withSelectionCleared()

        assertEquals(ReaderSelectionSnapshot("keep", 1, 2, 3), cleared.selectionSnapshot)
        assertEquals("", cleared.selectedText)
    }
}

/**
 * F2.4 快照测试专用的最小 [ReaderTextRuleDao]：本类不触碰规则行为，
 * 只需要一个可用的 RulesRepository 来构造真实 [ReaderViewModel]。
 */
private class SnapshotTestRuleDao : ReaderTextRuleDao {

    private val rows = mutableListOf<com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity>()

    override fun observeAll(): kotlinx.coroutines.flow.Flow<List<com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity>> =
        kotlinx.coroutines.flow.flowOf(rows.toList())

    override fun observeByKind(kind: String): kotlinx.coroutines.flow.Flow<List<com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity>> =
        kotlinx.coroutines.flow.flowOf(rows.filter { it.kind == kind })

    override fun observeById(id: String): kotlinx.coroutines.flow.Flow<com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity?> =
        kotlinx.coroutines.flow.flowOf(rows.firstOrNull { it.id == id })

    override suspend fun getById(id: String): com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity? =
        rows.firstOrNull { it.id == id }

    override suspend fun upsert(entity: com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity) {
        rows.removeAll { it.id == entity.id }
        rows.add(entity)
    }

    override suspend fun deleteCustom(id: String): Int =
        if (rows.removeAll { it.id == id }) 1 else 0

    override suspend fun updateEnabled(id: String, enabled: Boolean, updatedAt: Long): Int {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx < 0) return 0
        rows[idx] = rows[idx].copy(enabled = enabled, updated_at = updatedAt)
        return 1
    }

    override suspend fun updatePosition(id: String, position: Int, updatedAt: Long): Int {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx < 0) return 0
        rows[idx] = rows[idx].copy(position = position, updated_at = updatedAt)
        return 1
    }
}
