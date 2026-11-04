package com.creationreadingassistant.acceptance

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * CRA-ABC-DEVICE-CONTRACT-PREP-v1 / 任务34-v1 —— C：TXT 错误显示与进度保护。
 *
 * 现有 7 个 `ReaderDocumentLoaderOpenFailureTest` JVM 用例只证明 Loader 的抛错语义，
 * **不等于**真实 UI 失败合同。本类建立**原正文路径不可读但 preview 非空**的合成「测试 TXT」，
 * 预置有效进度，经**真实生产阅读入口**（MainActivity → 阅读器）验证：
 *
 *   1. 可见**真实加载错误**（生产 `ReaderDocumentStatus` 的「无法打开这本书」）；
 *   2. preview **没有**作为成功正文显示（不把预览当全文）；
 *   3. 原完整进度行**没有被错误路径污染**（book_id / progress_percent / completion_state /
 *      current_location_json / updated_at 逐列不变）。
 *
 * 不改产品逻辑掩盖失败；不用测试页面模拟生产错误。夹具为 Task34 专属（独立 bookId 前缀）。
 * 设备结果 NOT_RUN（本轮只做编译准备）。
 */
@RunWith(AndroidJUnit4::class)
class Task34TxtErrorUiTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null

    private val bookId = "task34-unreadable-" + UUID.randomUUID()
    private val bookTitle = "测试 TXT（正文不可读）"
    private val previewText = "TASK34_PREVIEW_MARKER_不应作为全文显示"
    private val runId = "task34-run-" + UUID.randomUUID()

    @Before
    fun setUp() {
        db = J1RealReaderFixtures.openDatabase(context)
        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(context)
        val now = Instant.now().toString()
        runBlocking {
            // 1) 可进入阅读器的旧元数据；本机正文路径仍真实不存在，触发 Reader 的实际加载错误
            val missing = File(context.filesDir, "task34-missing-$bookId.txt")
            check(!missing.exists()) { "Task fixture path already exists; refuse to remove a pre-existing file" }
            db.bookDao().upsert(
                BookEntity(
                    id = bookId, title = bookTitle, format = "txt",
                    size = 1, local_content_path = missing.absolutePath,
                    content_status = "available", imported_at = now, updated_at = now,
                ),
            )
            // 2) 正文行：只有 preview，没有可读全文
            db.bookContentDao().upsert(BookContentEntity(book_id = bookId, reader_preview = previewText))
            // 3) 预置有效进度（完整行）
            db.readingProgressDao().upsert(
                ReadingProgressEntity(
                    book_id = bookId, progress_percent = 37.5f,
                    last_read_at = now, total_reading_time_ms = 12345L,
                    completion_state = "reading",
                    current_location_json = """{"offset":1234,"locator_v2":{"chapter":1,"charOffset":1234}}""",
                    updated_at = now,
                ),
            )
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        try {
            scenario?.close()
        } finally {
            runBlocking {
                val wdb = db.openHelper.writableDatabase
                wdb.execSQL("DELETE FROM reading_progress WHERE book_id = ?", arrayOf(bookId))
                wdb.execSQL("DELETE FROM book_content WHERE book_id = ?", arrayOf(bookId))
                wdb.execSQL("DELETE FROM books WHERE id = ?", arrayOf(bookId))
            }
            J1RealReaderFixtures.restoreOnboardingState(context, originalOnboarded)
            db.close()
        }
    }

    @Test
    fun task34_unreadableSourceWithPreviewShowsRealErrorAndKeepsProgress() {
        val evidence = Task34Evidence(context, runId, "task34_unreadableSourceWithPreviewShowsRealErrorAndKeepsProgress")
        val before = runBlocking { db.readingProgressDao().getByBook(bookId) }
            ?: error("预置进度行缺失")
        evidence.record("before_open", "top_level_shelf", null, null, null, before)

        // 经真实生产入口打开该书（shelf → reader）
        Task34ReaderEntry.openReader(composeRule, visibleTitle = bookTitle)

        // 1) 可见真实加载错误
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("无法打开这本书").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("无法打开这本书").assertIsDisplayed()

        // 2) preview 没有被当作成功正文显示
        val previewSemanticsPresent = composeRule.onAllNodesWithText(previewText, substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        evidence.record(
            "real_error_ui_visible",
            "reader_error",
            null,
            null,
            null,
            runBlocking { db.readingProgressDao().getByBook(bookId) },
            listOf("无法打开这本书", "preview_semantics_present=$previewSemanticsPresent"),
        )
        assertFalse("preview 文本绝不能作为正文出现在阅读界面", previewSemanticsPresent)

        // 3) 原完整进度行没有被错误路径污染
        val after = runBlocking { db.readingProgressDao().getByBook(bookId) }
            ?: error("错误UI检查后的进度行缺失")
        evidence.record("after_error_progress_readback", "reader_error", null, null, null, after)
        assertEquals("错误路径不得改写完整 ReadingProgressEntity（12列）", before, after)
    }
}
