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
import androidx.compose.ui.test.onFirst
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
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.InspirationRepository
import com.creationreadingassistant.data.repository.NoteRepository
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.ui.layout.DefaultLayoutTokens
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.navigation.AppChrome
import com.creationreadingassistant.ui.navigation.LocalAppChrome
import com.creationreadingassistant.ui.screen.inspiration.InspirationRoute
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import com.creationreadingassistant.ui.viewmodel.InspirationViewModel
import io.mockk.every
import io.mockk.mockk
import com.creationreadingassistant.data.repository.TaxonomyRepository
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.model.MultipleFailureException
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * CRA-TASK70-B-ORPHAN-EXCERPT-v1 —— 原书缺失后，已存摘录与个人想法仍可读的**真实持久化 + 真实详情组件**仪器候选。
 *
 * ## 要证明的合同
 *
 * 首版规划 B 线要求：冷启动保留记录、来源正确、原书缺失时摘录仍可读。现有
 * [HighlightInspirationPersistenceAcceptTest] 已覆盖文件型 Room 关闭重开，但成功路径都
 * `seedBook`，没有覆盖「来源书被删除后」的详情。本候选补齐这一缺口，且**不改产品代码**：
 *
 * 1. 只经生产路径创建数据：`BookRepository.upsert` 写本轮唯一书、
 *    `NoteRepository.saveHighlight` 写摘录/个人想法、`NoteRepository.convertHighlightToInspiration`
 *    做真实转换；灵感 id 用转换前后活跃集合差集认领，不在测试里直写期望 payload。
 * 2. 只用生产 `BookRepository.deleteBook`（真实 `bookDao.softDelete` + 事务内级联清理）
 *    删除**本轮唯一且已核实的 bookId**；删除前后都核对活跃书集合，不用书名 LIKE 认领，
 *    不接触正式书库。
 * 3. 关闭并重开文件型 Room 后，逐字段核对灵感正文、`payload.source` 摘录快照、
 *    `source_book_id` 与 locator 仍在；原始行在删除前后、重开前后、UI 操作前后完全相等。
 * 4. 渲染**生产** `InspirationRoute`（内部即生产 `InspirationDetail`，ViewModel 用真实
 *    `InspirationRepository`/`BookRepository` 支撑，只有与本用例无关的 `SettingsStore`/
 *    `AiClient` 用 MockK 替身避免 DataStore/网络），断言：
 *    - 摘录与个人想法仍显示；
 *    - 缺书状态给出明确提示（占位与点击后的 snackbar 文案）；
 *    - 点击「回源」不会调用真实 `onOpenBook`/`onOpenRoute`，不以虚假回源成功掩盖缺书；
 *    - 上述失败路径不会删除灵感或用空内容替换成功。
 *
 * ## 边界（不要过度解读）
 *
 * - Room 关闭重开只证明文件持久化重载，**不声称 OS 进程冷启动**；
 * - 不证明真实阅读器 UI 的选择/转换操作手势，只证明转换入口与详情消费；
 * - 不访问正式库：数据库名每次唯一，路径由 [ContextWrapper.getDatabasePath] 重定向到
 *   本轮 cache 根目录，并在打开后断言底层文件确实落在该目录内；
 * - 本轮创建的数据库/证据文件**保留**给设备 owner 登记与恢复，不删除未知资产；
 * - 失败位置通过 `TASK70B_PHASE=` 与结构化打印暴露，不用字符串存在性替代数据库断言。
 */
@RunWith(AndroidJUnit4::class)
class InspirationOrphanExcerptPersistenceAcceptTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var base: Context
    private lateinit var isolatedContext: Context
    private lateinit var rootDir: File
    private lateinit var dbName: String

    /** 本用例打开过的所有数据库实例（收尾逐个关闭，失败单独暴露）。 */
    private val openedDatabases = mutableListOf<AppDatabase>()

    /** 本轮排他创建的精确数据库路径。 */
    private lateinit var expectedDatabaseFile: File

    /** 与生产 ViewModel / 详情消费完全一致的解析配置。 */
    private val payloadJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val createdAt = "2026-10-20T10:00:00Z"

    private lateinit var runId: String

    // ---------------------------------------------------------------- 生命周期

    @Before
    fun setUp() {
        runId = UUID.randomUUID().toString().replace("-", "")
        base = ApplicationProvider.getApplicationContext()
        dbName = "task70b_orphan_inspiration_$runId"
        rootDir = File(base.cacheDir, "task70b-orphan-$runId")
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

            // 框架 SQLiteOpenHelper 实际走 openOrCreateDatabase；仅重写 getDatabasePath
            // 不足以把文件钉在测试目录，因此这两个重载都显式打开隔离路径。
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
    }

    @After
    fun tearDown() {
        // 保留原始关闭异常，由 JUnit 与用例根因一起报告。
        val failures = mutableListOf<Throwable>()
        openedDatabases.asReversed().forEach { database ->
            try {
                if (database.isOpen) database.close()
            } catch (failure: Exception) {
                failures += failure
            }
        }
        openedDatabases.clear()
        // 仅观察路径不足以证明文件归属。本测试不删除数据库或目录；
        // 唯一设备 owner 须将创建物纳入清单，取证并按恢复合同处理。
        if (::rootDir.isInitialized) println("TASK70B_EVIDENCE_DIR=${rootDir.absolutePath}")
        MultipleFailureException.assertEmpty(failures)
    }

    // ---------------------------------------------------------------- 用例

    @Test
    fun excerptAndThoughtRemainReadableAfterSourceBookIsDeletedAndRoomReopens() {
        phase("CREATE_BOOK_HIGHLIGHT_AND_CONVERT")
        val bookId = "task70b-book-$runId"
        val highlightId = "task70b-highlight-$runId"
        // 摘录保持 >24 字符，使生产标题「高亮灵感：前24字符」不能靠整段匹配冒充正文。
        val excerpt = "任务70-v1-B 摘录 $runId：原书删除后，这段已存摘录仍必须可读。"
        val thought = "个人想法 $runId：这条批注也必须保留，不能被空内容或回源失败覆盖。"
        val locator = LocatorCodec.encode(
            legacyOffset = 4096,
            chapterIndex = 2,
            charOffset = 512,
            excerpt = excerpt,
        )

        val first = openDatabase()
        val noteRepository = NoteRepository(
            highlightDao = first.highlightDao(),
            noteDao = first.noteDao(),
            inspirationDao = first.inspirationDao(),
        )
        val bookRepository = buildBookRepository(first)

        val book = BookEntity(
            id = bookId,
            title = "中性测试书 $runId",
            author = "测试作者 $runId",
            format = "txt",
            imported_at = createdAt,
            updated_at = createdAt,
        )
        val highlight = HighlightEntity(
            id = highlightId,
            book_id = bookId,
            text = excerpt,
            note = thought,
            color = "#ff0",
            chapter_title = "第二章",
            progress_percent = 12.5f,
            locator_json = locator,
            created_at = createdAt,
            updated_at = createdAt,
        )

        val inspirationIdsBefore = runBlocking {
            first.inspirationDao().observeAllActive().first().mapTo(LinkedHashSet<String>()) { it.id }
        }
        runBlocking {
            bookRepository.upsert(book)
            noteRepository.saveHighlight(highlight)
        }

        // 隔离证明：本轮库中活跃书只有这一本，删除目标不是按书名猜的。
        val activeBookIdsBefore = runBlocking { first.bookDao().getAllActive().map { it.id }.toSet() }
        assertEquals("本轮隔离库中不应存在其他活跃书：$activeBookIdsBefore", setOf(bookId), activeBookIdsBefore)

        runBlocking { noteRepository.convertHighlightToInspiration(highlightId) }
        val createdRows = runBlocking { first.inspirationDao().observeAllActive().first() }
            .filter { it.id !in inspirationIdsBefore }
        assertEquals("真实转换必须且只能新增一条灵感：$createdRows", 1, createdRows.size)
        val inspiration = createdRows.single()
        val inspirationId = inspiration.id

        // 真实转换产物的结构核对（不直写 payload）。
        assertEquals(bookId, inspiration.source_book_id)
        assertEquals("$excerpt\n\n$thought", inspiration.body)
        assertEquals("inbox", inspiration.status)
        assertEquals("note", inspiration.type)
        val sourceAfterConvert = requireNotNull(decodeSource(inspiration)) {
            "真实转换未写出 payload.source，详情无法读取摘录"
        }
        assertEquals(bookId, sourceAfterConvert.bookId)
        assertEquals("第二章", sourceAfterConvert.chapterTitle)
        assertEquals(12.5f, sourceAfterConvert.progressPercent!!, 0f)
        assertEquals(excerpt, sourceAfterConvert.excerpt)
        assertEquals(locator, sourceAfterConvert.locatorJson)
        val payloadRaw = requireNotNull(inspiration.payload)
        val payloadSha256 = sha256(payloadRaw)

        println("TASK70B_BOOK_ID=$bookId")
        println("TASK70B_HIGHLIGHT_ID=$highlightId")
        println("TASK70B_INSPIRATION_ID=$inspirationId")
        println("TASK70B_PAYLOAD=$payloadRaw")
        println("TASK70B_PAYLOAD_SHA256=$payloadSha256")

        phase("DELETE_ONLY_THIS_ROUND_BOOK")
        runBlocking { bookRepository.deleteBook(bookId) }

        // 删除只命中本轮 bookId：活跃书清空，软删除行仅有本轮这一本。
        assertNull("本轮来源书必须从活跃集消失", runBlocking { first.bookDao().getById(bookId) })
        val activeBookIdsAfter = runBlocking { first.bookDao().getAllActive().map { it.id }.toSet() }
        assertTrue("删除后仍存在其他活跃书，说明隔离或认领范围有误：$activeBookIdsAfter", activeBookIdsAfter.isEmpty())
        val softDeletedBookIds = runBlocking { first.bookDao().getDeleted().map { it.id }.toSet() }
        assertEquals("软删除行必须恰好是本轮 bookId：$softDeletedBookIds", setOf(bookId), softDeletedBookIds)

        // 真实删书会同时软删除该书的高亮；灵感本身不在删除范围，必须原样保留。
        val deletedHighlight = requireNotNull(runBlocking { first.highlightDao().getById(highlightId) }) {
            "本轮高亮行不应被物理删除"
        }
        assertNotNull("真实删书应软删除本轮高亮", deletedHighlight.deleted_at)
        val inspirationSameDb = runBlocking { first.inspirationDao().getById(inspirationId) }
        assertNotNull("删除来源书不得删除灵感", inspirationSameDb)
        assertEquals("删除来源书不得改写灵感任何字段", inspiration, inspirationSameDb)

        phase("CLOSE_AND_REOPEN_FILE_ROOM")
        first.close()
        val second = openDatabase()

        assertNull("重开后来源书仍必须缺失", runBlocking { second.bookDao().getById(bookId) })
        val reopened = requireNotNull(runBlocking { second.inspirationDao().getById(inspirationId) }) {
            "Room 关闭重开后已存灵感丢失"
        }
        assertEquals("重开后灵感任一字段发生变化", inspiration, reopened)
        assertEquals(bookId, reopened.source_book_id)
        assertEquals("$excerpt\n\n$thought", reopened.body)
        assertNull("重开后灵感不得被软删除", reopened.deleted_at)

        val reopenedPayloadRaw = requireNotNull(reopened.payload)
        assertEquals("重开后 payload 字节内容变化", payloadRaw, reopenedPayloadRaw)
        val reopenedSource = requireNotNull(decodeSource(reopened)) {
            "重开后 payload.source 丢失，详情无法渲染摘录"
        }
        assertEquals("重开后来源摘录快照丢失", excerpt, reopenedSource.excerpt)
        assertEquals("重开后来源书籍快照丢失", bookId, reopenedSource.bookId)
        assertEquals("重开后 locator 快照丢失", locator, reopenedSource.locatorJson)

        println("TASK70B_REOPENED_BODY_SHA256=${sha256(reopened.body)}")
        println("TASK70B_REOPENED_PAYLOAD_SHA256=${sha256(reopenedPayloadRaw)}")

        phase("REAL_PRODUCTION_DETAIL_UI")
        val viewModel = buildViewModel(second)
        val openedBooks = mutableListOf<String>()
        val openedRoutes = mutableListOf<String>()
        // 收尾时先撤离组合，确保详情内 `observeVariants` 的收集在关闭数据库前已取消。
        var showDetail by mutableStateOf(true)
        composeRule.setContent {
            if (showDetail) {
                AppTheme {
                    Box(Modifier.requiredSize(393.dp, 800.dp)) {
                        CompositionLocalProvider(
                            LocalAppChrome provides AppChrome(
                                bottomNavHeight = 0.dp,
                                navLayerPaddingApplied = true,
                            ),
                            LocalLayoutTokens provides DefaultLayoutTokens,
                        ) {
                            // 生产详情路由与详情组件；只替换与本断言无关的 Settings/AI 依赖。
                            InspirationRoute(
                                viewModel = viewModel,
                                initialSelectedId = inspirationId,
                                onOpenBook = { openedBooks += it },
                                onOpenRoute = { openedRoutes += it },
                            )
                        }
                    }
                }
            }
        }

        // 详情解析由真实 repository flow 驱动，必须真正落到本轮灵感而不是 Loading/NotFound。
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(excerpt, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(excerpt, substring = true).onFirst().assertIsDisplayed()
        composeRule.onNodeWithText(thought, substring = true).assertIsDisplayed()

        // 滚到来源卡片：生产详情在有 source 时总会渲染该区块。
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("来源书籍与阅读足迹"))
        composeRule.onNodeWithText("来源书籍与阅读足迹").assertIsDisplayed()
        // bookTitle 从未在真实转换中编造，缺书/无快照时必须显示明确占位，而不是伪装成功。
        composeRule.onNodeWithText("来源书籍已不可用", substring = true).assertIsDisplayed()
        // 来源摘录快照仍由 payload.source.excerpt 渲染（滚到来源卡片后至少一个节点存在）。
        assertTrue(
            "来源卡片未渲染 payload.source.excerpt",
            composeRule.onAllNodesWithText(excerpt, substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )

        phase("SOURCE_ACTION_MISSING_BOOK")
        composeRule.onNodeWithText("来源书籍已不可用", substring = true).performClick()
        waitForSnackbar("来源书籍已不可用，这条灵感仍会保留。")
        assertTrue("缺书时不得把点击当成真实回源成功：$openedBooks", openedBooks.isEmpty())

        phase("LOCATOR_ACTION_MISSING_BOOK")
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("查阅原文位置"))
        composeRule.onNodeWithText("查阅原文位置").performClick()
        waitForSnackbar("来源书籍已删除，摘录内容仍保留。")
        assertTrue("缺书时不得伪造临时查阅 route：$openedRoutes", openedRoutes.isEmpty())
        assertTrue("缺书时不得把点击当成真实回源成功：$openedBooks", openedBooks.isEmpty())

        phase("POST_UI_DB_UNCHANGED")
        val afterUi = requireNotNull(runBlocking { second.inspirationDao().getById(inspirationId) }) {
            "失败的回源操作不得删除已存灵感"
        }
        assertEquals("失败的回源操作不得改写已存灵感", reopened, afterUi)
        assertNull("失败的回源操作不得软删除已存灵感", afterUi.deleted_at)
        assertTrue("正文不得被空内容替换", afterUi.body.isNotBlank())
        assertEquals("$excerpt\n\n$thought", afterUi.body)
        assertFalse("正文不得被空内容替换", afterUi.body.isEmpty())
        val afterUiSource = requireNotNull(decodeSource(afterUi)) {
            "失败的回源操作不得清空来源快照"
        }
        assertEquals(excerpt, afterUiSource.excerpt)
        assertNull("来源书仍应处于缺失状态", runBlocking { second.bookDao().getById(bookId) })

        // 关闭数据库前先解除所有生产 Flow 收集：
        // 1) 撤离组合会立即取消详情内 observeVariants 的收集；
        // 2) itemsState/books 的 stateIn(WhileSubscribed(5000)) 有 5 秒停止延迟，
        //    直接取消 viewModelScope 才不会在 @After 关库后触碰已关闭的 Room。
        phase("DISPOSE_UI_BEFORE_DB_CLOSE")
        composeRule.runOnIdle { showDetail = false }
        composeRule.waitForIdle()
        viewModel.viewModelScope.cancel()
        composeRule.waitForIdle()

        phase("WRITE_EVIDENCE")
        writeEvidence(
            mapOf(
                "runId" to runId,
                "dbName" to dbName,
                "databasePath" to expectedDatabaseFile.absolutePath,
                "bookId" to bookId,
                "highlightId" to highlightId,
                "inspirationId" to inspirationId,
                "payload" to payloadRaw,
                "payloadSha256" to payloadSha256,
                "body" to reopened.body,
                "sourceExcerpt" to reopenedSource.excerpt,
                "sourceBookId" to reopenedSource.bookId,
                "locatorJson" to reopenedSource.locatorJson,
                "bookActiveBeforeDelete" to true,
                "bookActiveAfterReopen" to false,
                "highlightDeletedAt" to deletedHighlight.deleted_at,
                "inspirationActiveAfterReopen" to true,
                "missingBookSnackbar" to "来源书籍已不可用，这条灵感仍会保留。",
                "missingLocatorSnackbar" to "来源书籍已删除，摘录内容仍保留。",
                "onOpenBookCalls" to openedBooks.size,
                "onOpenRouteCalls" to openedRoutes.size,
            ),
        )
        phase("DONE")
    }

    // ---------------------------------------------------------------- 夹具

    /** 打开真实文件型 Room，并断言底层路径确实落在本测试根目录内。 */
    private fun openDatabase(): AppDatabase {
        val database = Room.databaseBuilder(isolatedContext, AppDatabase::class.java, dbName)
            .addCallback(AppDatabase.CreateIndexCallback())
            .build()
        openedDatabases += database
        val path = requireNotNull(database.openHelper.writableDatabase.path) {
            "文件型 Room 没有返回数据库路径；保留资产并阻塞"
        }
        assertTrue(
            "数据库实际路径与本轮精确预期不符：$path != ${expectedDatabaseFile.absolutePath}",
            File(path).absoluteFile == expectedDatabaseFile.absoluteFile,
        )
        return database
    }

    /** 真实生产仓储；只把与本用例无关的索引刷新协调器保持在默认 null。 */
    private fun buildBookRepository(database: AppDatabase): BookRepository = BookRepository(
        bookDao = database.bookDao(),
        bookContentDao = database.bookContentDao(),
        bookFileDao = database.bookFileDao(),
        progressDao = database.readingProgressDao(),
        sessionDao = database.readingSessionDao(),
        noteDao = database.noteDao(),
        highlightDao = database.highlightDao(),
        inspirationDao = database.inspirationDao(),
        bookTagDao = database.bookTagDao(),
        bookCategoryDao = database.bookCategoryDao(),
        shelfBookDao = database.shelfBookDao(),
        chapterReadDao = database.chapterReadDao(),
        context = isolatedContext,
    )

    /**
     * 真实 ViewModel：灵感/书籍仓储都指向本轮隔离 Room。
     *
     * `SettingsStore` 会创建 DataStore/SecurePrefs，`AiClient` 会读取网络配置；两者都不参与
     * 本用例断言，用 MockK 替身避免产生额外持久化资产或网络副作用。详情/来源解析仍走
     * 生产 `InspirationViewModel.sourceOf/payloadFor`。
     */
    private fun buildViewModel(database: AppDatabase): InspirationViewModel {
        val settings = mockk<SettingsStore>(relaxed = true)
        every { settings.inspirationSort } returns MutableStateFlow("updated")
        val aiClient = mockk<AiClient>(relaxed = true)
        return InspirationViewModel(
            inspirationRepository = InspirationRepository(
                database.inspirationDao(),
                database.inspirationVariantDao(),
            ),
            bookRepository = buildBookRepository(database),
            aiClient = aiClient,
            settings = settings,
            taxonomyRepository = mockk<TaxonomyRepository>(relaxed = true).also { repo ->
                every { repo.observeCategories() } returns flowOf(emptyList())
            },
        )
    }

    private fun decodeSource(entity: com.creationreadingassistant.data.local.entity.InspirationEntity) =
        payloadJson.decodeFromString<InspirationPayloadData>(entity.payload ?: "{}").source

    private fun waitForSnackbar(text: String) {
        composeRule.waitUntil(timeoutMillis = 12_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun phase(name: String) {
        println("TASK70B_PHASE=$name")
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** 证据文件使用独占创建；已存在即拒绝，绝不覆盖未知资产。 */
    private fun writeEvidence(values: Map<String, Any?>) {
        val file = File(rootDir, "task70b-evidence.json")
        check(file.createNewFile()) { "证据文件已存在，不覆盖：$file" }
        val json = JSONObject()
        values.forEach { (key, value) -> json.put(key, value ?: JSONObject.NULL) }
        file.writeText(json.toString(2), Charsets.UTF_8)
        println("TASK70B_EVIDENCE_FILE=${file.absolutePath}")
    }
}
