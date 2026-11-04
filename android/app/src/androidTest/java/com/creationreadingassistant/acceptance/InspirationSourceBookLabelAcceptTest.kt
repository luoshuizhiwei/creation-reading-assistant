package com.creationreadingassistant.acceptance

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.InspirationRepository
import com.creationreadingassistant.data.repository.NoteRepository
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.ui.layout.DefaultLayoutTokens
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.navigation.AppChrome
import com.creationreadingassistant.ui.navigation.LocalAppChrome
import com.creationreadingassistant.ui.screen.inspiration.InspirationPage
import com.creationreadingassistant.ui.screen.inspiration.InspirationRoute
import com.creationreadingassistant.ui.screen.inspiration.InspirationScreen
import com.creationreadingassistant.ui.screen.inspiration.InspirationUiState
import com.creationreadingassistant.ui.screen.reader.buildInspirationPayload
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.viewmodel.BookListState
import com.creationreadingassistant.ui.viewmodel.CategoryListState
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import com.creationreadingassistant.ui.viewmodel.InspirationViewModel
import io.mockk.every
import io.mockk.mockk
import com.creationreadingassistant.data.repository.TaxonomyRepository
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.model.MultipleFailureException
import java.io.File
import java.util.UUID

/**
 * CRA-TASK72-B-SOURCE-BOOK-STATE-v1 —— 来源书 Loading / Available / Missing 完整状态回归。
 *
 * 在 71-B 的基础上补齐 Root 指出的真实缺口：
 * - 详情可以先于 books 首发而 Loaded；此时必须是 Loading，不能误报缺书；
 * - `emptyList()` 只在 `BookListState.Loaded` 中代表“确定为空”；
 * - 仓储异常必须是显式 Failed，不能降级成空库/缺书成功；
 * - 历史书名快照与原书缺失要**同时**成立（快照保留 + 明确缺失）；
 * - 来源存在 → 缺失时摘录/想法与灵感行保持不变；
 * - 现有 Loading / NotFound 分支不显示任何确定缺书。
 *
 * 测试通过生产 `InspirationRoute`/`InspirationScreen`/`InspirationDetail` 消费
 * 生产 `InspirationViewModel.booksState`；灵感数据由真实 Room + 真实转换入口写入。
 * books 首发时序用可控 Flow 驱动，其余状态尽量走真实 `BookRepository`。
 */
@RunWith(AndroidJUnit4::class)
class InspirationSourceBookLabelAcceptTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var base: Context
    private lateinit var isolatedContext: Context
    private lateinit var rootDir: File
    private lateinit var dbName: String
    private lateinit var expectedDatabaseFile: File
    private lateinit var runId: String

    private val openedDatabases = mutableListOf<AppDatabase>()
    private val payloadJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val createdAt = "2026-10-20T12:00:00Z"

    private lateinit var database: AppDatabase
    private lateinit var bookRepository: BookRepository
    private lateinit var noteRepository: NoteRepository
    private lateinit var inspirationRepository: InspirationRepository

    private companion object {
        const val MISSING_BOOK_MESSAGE = "来源书籍已不可用，这条灵感仍会保留。"
        const val LOADING_ACTION_MESSAGE = "来源书籍信息加载中，请稍后再试。"
        const val FAILED_ACTION_MESSAGE = "无法确认来源书籍状态，请稍后再试。"
        const val LOADING_CAPTION = "正在加载来源书籍…"
        const val FAILED_CAPTION = "无法确认来源书籍状态"
        const val MISSING_CAPTION = "来源书籍已不可用"
    }

    private data class SeededConversion(
        val inspiration: InspirationEntity,
        val excerpt: String,
        val thought: String,
    )

    private data class SeededHistorical(
        val inspiration: InspirationEntity,
        val snapshotTitle: String,
        val excerpt: String,
        val thought: String,
    )

    @Before
    fun setUp() {
        runId = UUID.randomUUID().toString().replace("-", "")
        base = ApplicationProvider.getApplicationContext()
        dbName = "task72b_source_book_state_$runId"
        rootDir = File(base.cacheDir, "task72b-source-book-state-$runId")
        check(rootDir.mkdir()) { "无法排他创建测试目录：$rootDir" }
        val dbDir = File(rootDir, "databases")
        check(dbDir.mkdir()) { "无法创建本轮数据库目录：$dbDir" }
        expectedDatabaseFile = File(dbDir, dbName)
        check(listOf("", "-wal", "-shm", "-journal").none {
            File(expectedDatabaseFile.path + it).exists()
        }) { "新测试目录已有数据库资产，保留并阻塞：$dbDir" }

        isolatedContext = object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File {
                check(name == dbName) { "拒绝非本轮数据库名称：$name" }
                return expectedDatabaseFile
            }

            override fun openOrCreateDatabase(
                name: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?,
            ): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory)

            override fun openOrCreateDatabase(
                name: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?,
                errorHandler: DatabaseErrorHandler?,
            ): SQLiteDatabase = SQLiteDatabase.openDatabase(
                getDatabasePath(name).path,
                factory,
                SQLiteDatabase.CREATE_IF_NECESSARY,
                errorHandler,
            )

            override fun getApplicationContext(): Context = this
        }

        database = openDatabase()
        bookRepository = buildBookRepository(database)
        noteRepository = NoteRepository(
            highlightDao = database.highlightDao(),
            noteDao = database.noteDao(),
            inspirationDao = database.inspirationDao(),
        )
        inspirationRepository = InspirationRepository(
            database.inspirationDao(),
            database.inspirationVariantDao(),
        )
    }

    @After
    fun tearDown() {
        val failures = mutableListOf<Throwable>()
        openedDatabases.asReversed().forEach { db ->
            try {
                if (db.isOpen) db.close()
            } catch (failure: Exception) {
                failures += failure
            }
        }
        openedDatabases.clear()
        if (::rootDir.isInitialized) println("TASK72B_EVIDENCE_DIR=${rootDir.absolutePath}")
        MultipleFailureException.assertEmpty(failures)
    }

    // ---------------------------------------------------------------- 场景 1

    @Test
    fun detailResolvedBeforeBooksFirstEmissionThenLoadingEmptyAvailable() {
        phase("SEED_CONVERSION")
        val sourceBook = newBook("source", "中性来源书 $runId")
        val witnessBook = newBook("witness", "中性旁证书 $runId")
        runBlocking {
            bookRepository.upsert(sourceBook)
            bookRepository.upsert(witnessBook)
        }
        val seeded = seedConversion(sourceBook)
        // 真实转换的真实结果：有 bookId，没有 bookTitle。
        assertNull(decodeSource(seeded.inspiration)?.bookTitle)

        // 可控 books 首发：详情已经 Loaded，但 books 尚未首发。
        val controlledBooks = MutableSharedFlow<List<BookEntity>>(replay = 1)
        val viewModel = buildViewModel(bookFlow = controlledBooks)
        val openedBooks = mutableListOf<String>()
        var showUi by mutableStateOf(true)
        composeRule.setContent {
            if (showUi) {
                themedRoot {
                    InspirationRoute(
                        viewModel = viewModel,
                        initialSelectedId = seeded.inspiration.id,
                        onOpenBook = { openedBooks += it },
                        onOpenRoute = { },
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(seeded.excerpt, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        scrollTo("来源书籍与阅读足迹")

        phase("BOOKS_NOT_FIRST_EMISSION")
        waitForText(LOADING_CAPTION)
        composeRule.onNodeWithText(LOADING_CAPTION).assertIsDisplayed()
        assertNoText(MISSING_CAPTION)
        // 无快照时标题是中性“来源书籍”，不是“已不可用”。
        composeRule.onNodeWithText("来源书籍").assertIsDisplayed()
        composeRule.onNodeWithText("来源书籍").performClick()
        waitForSnackbar(LOADING_ACTION_MESSAGE)
        assertTrue("未加载时不得伪装可用：$openedBooks", openedBooks.isEmpty())

        phase("BOOKS_LOADED_EMPTY")
        runBlocking { controlledBooks.emit(emptyList()) }
        waitForText(MISSING_CAPTION)
        composeRule.onNodeWithText(MISSING_CAPTION).assertIsDisplayed()
        assertTrue(
            "明确空库时摘录仍需可读",
            composeRule.onAllNodesWithText(seeded.excerpt, substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        composeRule.onNodeWithText(MISSING_CAPTION).performClick()
        waitForSnackbar(MISSING_BOOK_MESSAGE)
        assertTrue("缺书时不得伪装可用：$openedBooks", openedBooks.isEmpty())

        phase("BOOKS_LOADED_WITH_BOOK")
        runBlocking { controlledBooks.emit(listOf(witnessBook, sourceBook)) }
        val actualTitle = "《${sourceBook.title}》"
        waitForText(actualTitle)
        composeRule.onNodeWithText(actualTitle).assertIsDisplayed()
        assertNoText(MISSING_CAPTION)
        composeRule.onNodeWithText(actualTitle).performClick()
        composeRule.waitForIdle()
        assertEquals("书存在时必须回调真实 onOpenBook", listOf(sourceBook.id), openedBooks)

        phase("ROW_UNCHANGED")
        val afterUi = requireNotNull(runBlocking { database.inspirationDao().getById(seeded.inspiration.id) }) {
            "状态切换不得删除灵感"
        }
        assertEquals("状态切换不得改写灵感", seeded.inspiration, afterUi)

        println("TASK72B_SOURCE_BOOK_ID=${sourceBook.id}")
        println("TASK72B_INSPIRATION_ID=${seeded.inspiration.id}")
        println("TASK72B_OPENED_BOOKS=${openedBooks.joinToString(",")}")

        disposeUi(viewModel) { showUi = false }
        phase("DONE")
    }

    // ---------------------------------------------------------------- 场景 2

    @Test
    fun realBookDeletionKeepsExcerptAndShowsMissingState() {
        phase("SEED_AND_RENDER_REAL_BOOK")
        val sourceBook = newBook("real-source", "真实来源书 $runId")
        val witnessBook = newBook("real-witness", "真实旁证书 $runId")
        runBlocking {
            bookRepository.upsert(sourceBook)
            bookRepository.upsert(witnessBook)
        }
        val seeded = seedConversion(sourceBook)

        val viewModel = buildViewModel()
        val openedBooks = mutableListOf<String>()
        var showUi by mutableStateOf(true)
        composeRule.setContent {
            if (showUi) {
                themedRoot {
                    InspirationRoute(
                        viewModel = viewModel,
                        initialSelectedId = seeded.inspiration.id,
                        onOpenBook = { openedBooks += it },
                        onOpenRoute = { },
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(seeded.excerpt, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        scrollTo("来源书籍与阅读足迹")
        val actualTitle = "《${sourceBook.title}》"
        waitForText(actualTitle)
        composeRule.onNodeWithText(actualTitle).assertIsDisplayed()
        assertNoText(MISSING_CAPTION)
        composeRule.onNodeWithText(actualTitle).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(sourceBook.id), openedBooks)

        phase("DELETE_REAL_BOOK")
        val beforeDelete = seeded.inspiration
        runBlocking { bookRepository.deleteBook(sourceBook.id) }
        waitForText(MISSING_CAPTION)
        composeRule.onNodeWithText(MISSING_CAPTION).assertIsDisplayed()
        assertTrue(
            "删书后来源摘录仍应渲染",
            composeRule.onAllNodesWithText(seeded.excerpt, substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        composeRule.onNodeWithText(MISSING_CAPTION).performClick()
        waitForSnackbar(MISSING_BOOK_MESSAGE)
        assertEquals("缺书时不得追加回源调用", listOf(sourceBook.id), openedBooks)

        phase("THOUGHT_STILL_READABLE")
        scrollToSubstring(seeded.thought)
        composeRule.onNodeWithText(seeded.thought, substring = true).assertIsDisplayed()

        phase("ROW_UNCHANGED")
        val afterUi = requireNotNull(runBlocking { database.inspirationDao().getById(seeded.inspiration.id) }) {
            "缺书提示不得删除灵感"
        }
        assertEquals("缺书提示不得改写灵感", beforeDelete, afterUi)
        assertEquals(sourceBook.id, afterUi.source_book_id)

        println("TASK72B_REAL_INSPIRATION_ID=${seeded.inspiration.id}")
        disposeUi(viewModel) { showUi = false }
        phase("DONE")
    }

    // ---------------------------------------------------------------- 场景 3

    @Test
    fun historicalSnapshotAndMissingStateAreShownTogether() {
        phase("SEED_HISTORICAL")
        val witnessBook = newBook("hist-witness", "历史旁证书 $runId")
        val historicalBook = newBook("hist-source", "数据库中的旧书名 $runId")
        runBlocking {
            bookRepository.upsert(witnessBook)
            bookRepository.upsert(historicalBook)
        }
        val seeded = seedHistorical(historicalBook)
        // 生产软删除：历史 payload 保留书名快照，来源书从活跃列表消失。
        runBlocking { bookRepository.deleteBook(historicalBook.id) }
        assertNull(runBlocking { database.bookDao().getById(historicalBook.id) })
        assertNotNull("旁证书必须仍在", runBlocking { database.bookDao().getById(witnessBook.id) })

        val viewModel = buildViewModel()
        val openedBooks = mutableListOf<String>()
        var showUi by mutableStateOf(true)
        composeRule.setContent {
            if (showUi) {
                themedRoot {
                    InspirationRoute(
                        viewModel = viewModel,
                        initialSelectedId = seeded.inspiration.id,
                        onOpenBook = { openedBooks += it },
                        onOpenRoute = { },
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(seeded.excerpt, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        scrollTo("来源书籍与阅读足迹")

        phase("SNAPSHOT_AND_MISSING_TOGETHER")
        val snapshotTitle = "《${seeded.snapshotTitle}》"
        // 快照在 Loading/Missing 都会显示；先等真实 books 首发并进入 Missing。
        waitForText(MISSING_CAPTION)
        composeRule.onNodeWithText(snapshotTitle).assertIsDisplayed()
        // 修正 71-B 的错误期望：快照保留的同时必须显示明确缺失。
        composeRule.onNodeWithText(MISSING_CAPTION).assertIsDisplayed()
        composeRule.onNodeWithText(MISSING_CAPTION).performClick()
        waitForSnackbar(MISSING_BOOK_MESSAGE)
        assertTrue("历史书缺失时不得回调 onOpenBook", openedBooks.isEmpty())

        phase("ROW_UNCHANGED")
        val afterUi = requireNotNull(runBlocking { database.inspirationDao().getById(seeded.inspiration.id) }) {
            "缺书提示不得删除历史灵感"
        }
        assertEquals("缺书提示不得改写历史灵感", seeded.inspiration, afterUi)
        assertEquals(seeded.snapshotTitle, requireNotNull(decodeSource(afterUi)).bookTitle)

        println("TASK72B_HISTORICAL_INSPIRATION_ID=${seeded.inspiration.id}")
        disposeUi(viewModel) { showUi = false }
        phase("DONE")
    }

    // ---------------------------------------------------------------- 场景 4

    @Test
    fun repositoryFailureIsExplicitAndNeverReportedAsMissing() {
        phase("SEED_AND_FAIL_BOOKS_FLOW")
        val sourceBook = newBook("failed-source", "失败来源书 $runId")
        runBlocking { bookRepository.upsert(sourceBook) }
        val seeded = seedConversion(sourceBook)

        val viewModel = buildViewModel(
            bookFlow = flow<List<BookEntity>> {
                throw IllegalStateException("task72b-books-flow-failure")
            },
        )
        val openedBooks = mutableListOf<String>()
        var showUi by mutableStateOf(true)
        composeRule.setContent {
            if (showUi) {
                themedRoot {
                    InspirationRoute(
                        viewModel = viewModel,
                        initialSelectedId = seeded.inspiration.id,
                        onOpenBook = { openedBooks += it },
                        onOpenRoute = { },
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(seeded.excerpt, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        scrollTo("来源书籍与阅读足迹")

        phase("FAILED_IS_EXPLICIT")
        waitForText(FAILED_CAPTION)
        composeRule.onNodeWithText(FAILED_CAPTION).assertIsDisplayed()
        assertNoText(MISSING_CAPTION)
        composeRule.onNodeWithText("来源书籍").performClick()
        waitForSnackbar(FAILED_ACTION_MESSAGE)
        assertTrue("读取失败不得伪装可用：$openedBooks", openedBooks.isEmpty())

        phase("ROW_UNCHANGED")
        val afterUi = requireNotNull(runBlocking { database.inspirationDao().getById(seeded.inspiration.id) }) {
            "读取失败不得删除灵感"
        }
        assertEquals("读取失败不得改写灵感", seeded.inspiration, afterUi)

        println("TASK72B_FAILED_INSPIRATION_ID=${seeded.inspiration.id}")
        disposeUi(viewModel) { showUi = false }
        phase("DONE")
    }

    // ---------------------------------------------------------------- 场景 5

    @Test
    fun existingLoadingAndNotFoundBranchesDoNotClaimMissing() {
        phase("RENDER_EXISTING_LOADING_STATE")
        val viewModel = buildViewModel(bookFlow = MutableSharedFlow<List<BookEntity>>())
        var resolving by mutableStateOf(true)
        composeRule.setContent {
            themedRoot {
                InspirationScreen(
                    state = InspirationUiState(
                        page = InspirationPage.Detail("task72b-loading-$runId"),
                    ),
                    items = emptyList(),
                    booksState = BookListState.Loading,
                    // 任务94-v1 合流补齐：93-A 给 InspirationScreen 新增了三个必填参数，
                    // 该调用点只用于验证详情加载/缺失分支，分类状态取 Loading 即可。
                    categoriesState = CategoryListState.Loading,
                    availableCategories = emptyList(),
                    categoryOptions = emptyList(),
                    filteredItems = emptyList(),
                    availableTypes = emptyList(),
                    typeCounts = emptyMap(),
                    availableStatuses = emptyList(),
                    statusCounts = emptyMap(),
                    sortMode = "updated",
                    showSkeleton = false,
                    selectedEntity = null,
                    detailResolving = resolving,
                    editingEntity = null,
                    actionSheetItem = null,
                    viewModel = viewModel,
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithTag("inspiration-detail-loading").assertIsDisplayed()
        assertNoText(MISSING_CAPTION)

        phase("RENDER_EXISTING_NOT_FOUND_STATE")
        composeRule.runOnIdle { resolving = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("inspiration-detail-missing").assertIsDisplayed()
        composeRule.onNodeWithText("灵感不存在，正在返回列表…").assertIsDisplayed()
        assertNoText(MISSING_CAPTION)

        viewModel.viewModelScope.cancel()
        phase("DONE")
    }

    // ---------------------------------------------------------------- 夹具

    private fun newBook(suffix: String, title: String): BookEntity = BookEntity(
        id = "task72b-$suffix-$runId",
        title = title,
        author = "测试作者 $runId",
        format = "txt",
        imported_at = createdAt,
        updated_at = createdAt,
    )

    private fun seedConversion(sourceBook: BookEntity): SeededConversion {
        val beforeIds = runBlocking {
            database.inspirationDao().observeAllActive().first().mapTo(HashSet<String>()) { it.id }
        }
        val excerpt = "任务72-v1-B 摘录 $runId：来源书状态必须真实。"
        val thought = "个人想法 $runId：来源书缺失后仍应保留。"
        val locator = LocatorCodec.encode(
            legacyOffset = 4096,
            chapterIndex = 2,
            charOffset = 512,
            excerpt = excerpt,
        )
        val highlight = HighlightEntity(
            id = "task72b-highlight-$runId",
            book_id = sourceBook.id,
            text = excerpt,
            note = thought,
            color = "#ff0",
            chapter_title = "第二章",
            progress_percent = 12.5f,
            locator_json = locator,
            created_at = createdAt,
            updated_at = createdAt,
        )
        runBlocking {
            noteRepository.saveHighlight(highlight)
            noteRepository.convertHighlightToInspiration(highlight.id)
        }
        val created = runBlocking { database.inspirationDao().observeAllActive().first() }
            .filter { it.id !in beforeIds }
        assertEquals("真实转换必须且只能新增一条灵感：$created", 1, created.size)
        return SeededConversion(created.single(), excerpt, thought)
    }

    private fun seedHistorical(book: BookEntity): SeededHistorical {
        val snapshotTitle = "历史书名快照 $runId"
        val excerpt = "历史摘录 $runId：快照与缺失状态应同时显示。"
        val thought = "历史想法 $runId：正文保持不变。"
        val payload = buildInspirationPayload(
            bid = book.id,
            bookTitle = snapshotTitle,
            chapterTitle = "历史章",
            excerpt = excerpt,
            progressPercent = 12.5f,
        )
        val entity = InspirationEntity(
            id = "task72b-historical-$runId",
            title = "历史灵感 $runId",
            body = "$excerpt\n\n$thought",
            type = "note",
            status = "inbox",
            source_book_id = book.id,
            payload = payload,
            created_at = createdAt,
            updated_at = createdAt,
        )
        runBlocking { inspirationRepository.upsert(entity) }
        return SeededHistorical(entity, snapshotTitle, excerpt, thought)
    }

    private fun openDatabase(): AppDatabase {
        val db = Room.databaseBuilder(isolatedContext, AppDatabase::class.java, dbName)
            .addCallback(AppDatabase.CreateIndexCallback())
            .build()
        openedDatabases += db
        val path = requireNotNull(db.openHelper.writableDatabase.path) {
            "文件型 Room 没有返回数据库路径；保留资产并阻塞"
        }
        assertTrue(
            "数据库实际路径与本轮精确预期不符：$path != ${expectedDatabaseFile.absolutePath}",
            File(path).absoluteFile == expectedDatabaseFile.absoluteFile,
        )
        return db
    }

    private fun buildBookRepository(db: AppDatabase): BookRepository = BookRepository(
        bookDao = db.bookDao(),
        bookContentDao = db.bookContentDao(),
        bookFileDao = db.bookFileDao(),
        progressDao = db.readingProgressDao(),
        sessionDao = db.readingSessionDao(),
        noteDao = db.noteDao(),
        highlightDao = db.highlightDao(),
        inspirationDao = db.inspirationDao(),
        bookTagDao = db.bookTagDao(),
        bookCategoryDao = db.bookCategoryDao(),
        shelfBookDao = db.shelfBookDao(),
        chapterReadDao = db.chapterReadDao(),
        context = isolatedContext,
    )

    /**
     * 真实 ViewModel + 真实灵感仓储。
     * - [bookFlow] 非空：只替换书籍来源为可控 Flow，用于精确制造“详情已解析、books 未首发”
     *   以及显式空库/异常状态；
     * - 为空：使用真实 `BookRepository` 与真实 Room Flow。
     */
    private fun buildViewModel(bookFlow: Flow<List<BookEntity>>? = null): InspirationViewModel {
        val repository = if (bookFlow == null) {
            bookRepository
        } else {
            mockk<BookRepository>(relaxed = true).also { mocked ->
                every { mocked.observeBooks() } returns bookFlow
            }
        }
        val settings = mockk<SettingsStore>(relaxed = true)
        every { settings.inspirationSort } returns MutableStateFlow("updated")
        return InspirationViewModel(
            inspirationRepository = InspirationRepository(
                database.inspirationDao(),
                database.inspirationVariantDao(),
            ),
            bookRepository = repository,
            aiClient = mockk<AiClient>(relaxed = true),
            settings = settings,
            taxonomyRepository = mockk<TaxonomyRepository>(relaxed = true).also { repo ->
                every { repo.observeCategories() } returns flowOf(emptyList())
            },
        )
    }

    private fun decodeSource(entity: InspirationEntity) =
        payloadJson.decodeFromString<InspirationPayloadData>(entity.payload ?: "{}").source

    @androidx.compose.runtime.Composable
    private fun themedRoot(content: @androidx.compose.runtime.Composable () -> Unit) {
        AppTheme {
            Box(Modifier.requiredSize(393.dp, 800.dp)) {
                CompositionLocalProvider(
                    LocalAppChrome provides AppChrome(
                        bottomNavHeight = 0.dp,
                        navLayerPaddingApplied = true,
                    ),
                    LocalLayoutTokens provides DefaultLayoutTokens,
                    content = content,
                )
            }
        }
    }

    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    private fun scrollToSubstring(text: String) {
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText(text, substring = true))
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertNoText(text: String) {
        assertTrue(
            "不得出现「$text」：${composeRule.onAllNodesWithText(text).fetchSemanticsNodes()}",
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun waitForSnackbar(text: String) {
        composeRule.waitUntil(timeoutMillis = 12_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun disposeUi(viewModel: InspirationViewModel, hide: () -> Unit) {
        phase("DISPOSE_UI_BEFORE_DB_CLOSE")
        composeRule.runOnIdle(hide)
        composeRule.waitForIdle()
        viewModel.viewModelScope.cancel()
        composeRule.waitForIdle()
    }

    private fun phase(name: String) {
        println("TASK72B_PHASE=$name")
    }
}
