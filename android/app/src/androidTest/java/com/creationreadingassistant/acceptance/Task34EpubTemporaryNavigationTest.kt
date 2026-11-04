package com.creationreadingassistant.acceptance

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * CRA-ABC-DEVICE-CONTRACT-PREP-v1 / 任务34-v1 —— A：EPUB 真实临时查阅与普通导航。
 *
 * 既有 `J1RealReaderFixtures` 的书籍一律 `format = "txt"`、文件为 `.txt`；
 * `J1TemporaryNavigationJourneyTest` 明示其辅助 probe **不是** EPUB 真实阅读器。
 * 本类用**任务专属两章「测试 EPUB」**（[Task34EpubFixture]，含可验证章节 marker 与 nav 目录），
 * 经**真实生产阅读入口**验证：
 *
 *   1. 建立普通阅读位置并等待实际保存（真实 EPUB 章 1 marker 可见）；
 *   2. 临时模式跨章查阅（章 2 marker 可见）后，**普通续读行保持**（逐列不变）；
 *   3. 返回后继续普通阅读，真实位移能够保存（普通行合法推进）；
 *   4. 普通主动章节导航仍更新普通阅读位置。
 *
 * 明确证明临时模式、章节目标与真实可见 marker，不是只改测试名或注释。
 * 设备结果 NOT_RUN（本轮只做编译准备）。
 */
@RunWith(AndroidJUnit4::class)
class Task34EpubTemporaryNavigationTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null

    private val bookId = "task34-epub-" + UUID.randomUUID()
    private val bookTitle = "测试 EPUB 甲与乙"
    private val runId = "task34-epub-run-" + UUID.randomUUID()
    private val noteId = "task34-note-" + UUID.randomUUID()
    private val noteTitle = "TASK34_临时查阅来源"
    private lateinit var epubFile: File
    private lateinit var canonicalEpubFile: File
    private lateinit var fixtureSha256: String
    private var canonicalPathInitiallyAbsent = false

    private fun awaitStableProgressRow(differsFrom: ReadingProgressEntity? = null): ReadingProgressEntity {
        var previous: ReadingProgressEntity? = null
        var unchangedSince = 0L
        composeRule.waitUntil(timeoutMillis = 20_000) {
            val current = runBlocking { db.readingProgressDao().getByBook(bookId) }
            val now = android.os.SystemClock.elapsedRealtime()
            if (current != null && (differsFrom == null || current != differsFrom)) {
                if (current != previous) {
                    previous = current
                    unchangedSince = now
                    false
                } else {
                    now - unchangedSince >= 400L
                }
            } else {
                previous = current
                unchangedSince = now
                false
            }
        }
        return requireNotNull(previous)
    }

    @Before
    fun setUp() {
        db = J1RealReaderFixtures.openDatabase(context)
        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(context)

        val dir = File(context.filesDir, runId).apply { mkdirs() }
        epubFile = Task34EpubFixture.createTwoChapterEpub(File(dir, "$bookId.epub"), title = bookTitle)
        fixtureSha256 = MessageDigest.getInstance("SHA-256")
            .digest(epubFile.readBytes()).joinToString("") { "%02x".format(it) }
        canonicalEpubFile = File(context.filesDir, "books/epub/$bookId.epub")
        check(!canonicalEpubFile.exists()) {
            "任务专属 EPUB 的应用内副本在本次测试前已存在，拒绝覆盖或删除：${canonicalEpubFile.absolutePath}"
        }
        canonicalPathInitiallyAbsent = true
        val fixtureCheck = Task34EpubFixture.verify(epubFile)
        check(fixtureCheck["present"] == true) { "任务专属 EPUB 夹具结构不完整：$fixtureCheck" }

        val now = Instant.now().toString()
        runBlocking {
            db.bookDao().upsert(
                BookEntity(
                    id = bookId, title = bookTitle, format = "epub",
                    size = epubFile.length().toInt(),
                    local_content_path = epubFile.absolutePath,
                    local_uri = "file://${epubFile.absolutePath}",
                    content_status = "available", imported_at = now, updated_at = now,
                ),
            )
            // 普通阅读位置：第 1 章
            db.readingProgressDao().upsert(
                ReadingProgressEntity(
                    book_id = bookId, progress_percent = 5f, last_read_at = now,
                    total_reading_time_ms = 1000L, completion_state = "reading",
                    current_location_json = """{"offset":0,"locator_v2":{"chapter":0,"charOffset":0}}""",
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
                wdb.execSQL("DELETE FROM notes WHERE id = ?", arrayOf(noteId))
                wdb.execSQL("DELETE FROM books WHERE id = ?", arrayOf(bookId))
            }
            if (canonicalPathInitiallyAbsent && canonicalEpubFile.exists()) {
                val actualSha256 = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalEpubFile.readBytes()).joinToString("") { "%02x".format(it) }
                check(actualSha256 == fixtureSha256) {
                    "任务专属 EPUB 应用内副本内容已变化，拒绝删除：${canonicalEpubFile.absolutePath}"
                }
                check(canonicalEpubFile.delete()) {
                    "无法清理本测试生成的 EPUB 副本：${canonicalEpubFile.absolutePath}"
                }
            }
            if (::epubFile.isInitialized) {
                epubFile.parentFile?.deleteRecursively()
            }
            J1RealReaderFixtures.restoreOnboardingState(context, originalOnboarded)
            db.close()
        }
    }

    @Test
    fun task34_epubNormalReadingPositionIsStableAndSavesOnActiveNavigation() {
        val evidence = Task34Evidence(context, runId, "task34_epubNormalReadingPositionIsStableAndSavesOnActiveNavigation")
        // 1) 经真实生产入口打开 EPUB，建立普通阅读位置（章 1 marker 必须真实可见）
        Task34ReaderEntry.openReader(composeRule, visibleTitle = bookTitle)
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(Task34EpubFixture.CHAPTER1_MARKER, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(Task34EpubFixture.CHAPTER1_MARKER, substring = true).assertIsDisplayed()
        evidence.record("ordinary_reader_chapter1", "ordinary_reader", true, null, null,
            runBlocking { db.readingProgressDao().getByBook(bookId) })

        val initial = runBlocking { db.readingProgressDao().getByBook(bookId) }
        assertNotNull("普通阅读位置必须已建立", initial)
        // Establish an actual saved ordinary location and wait until its complete row stabilizes.
        Task34ReaderEntry.openTocAndSelectChapter(composeRule, chapterLabel = "第二章 乙")
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(Task34EpubFixture.CHAPTER2_MARKER, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(Task34EpubFixture.CHAPTER2_MARKER, substring = true).assertIsDisplayed()
        val stableBaseline = awaitStableProgressRow(differsFrom = initial)
        composeRule.onNodeWithText(Task34EpubFixture.CHAPTER2_MARKER, substring = true).assertIsDisplayed()
        evidence.record("ordinary_saved_baseline_chapter2", "ordinary_reader", null, true, null, stableBaseline)
        assertTrue(
            "普通章节导航后必须保存真实 locator 变化",
            stableBaseline.current_location_json != initial!!.current_location_json,
        )
    }

    @Test
    fun task34_temporaryEpubInspectionKeepsNormalReadingRowFrozen() {
        val evidence = Task34Evidence(context, runId, "task34_temporaryEpubInspectionKeepsNormalReadingRowFrozen")
        // Start in the production reader and perform an ordinary chapter navigation so the
        // temporary journey begins from a real, saved, stable baseline.
        Task34ReaderEntry.openReader(composeRule, visibleTitle = bookTitle)
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(Task34EpubFixture.CHAPTER1_MARKER, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val ordinaryStart = runBlocking { db.readingProgressDao().getByBook(bookId) }!!
        composeRule.onNodeWithText(Task34EpubFixture.CHAPTER1_MARKER, substring = true).assertIsDisplayed()
        evidence.record("ordinary_reader_entered", "ordinary_reader", true, null, null,
            ordinaryStart)
        Task34ReaderEntry.openTocAndSelectChapter(composeRule, chapterLabel = "第二章 乙")
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(Task34EpubFixture.CHAPTER2_MARKER, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val firstSaved = awaitStableProgressRow(differsFrom = ordinaryStart)
        assertTrue("临时查阅前的普通目录导航必须先保存真实 locator",
            firstSaved.current_location_json != ordinaryStart.current_location_json)
        val stableNormal = awaitStableProgressRow(differsFrom = ordinaryStart)
        assertEquals("临时查阅前完整 ReadingProgressEntity 必须稳定", firstSaved, stableNormal)
        composeRule.onNodeWithText(Task34EpubFixture.CHAPTER2_MARKER, substring = true).assertIsDisplayed()
        evidence.record("ordinary_saved_baseline", "ordinary_reader", null, true, null, stableNormal)

        // 预置一条带 source 坐标的阅读笔记（生产临时查阅入口的数据前置）
        runBlocking {
            db.noteDao().upsert(
                com.creationreadingassistant.data.local.entity.NoteEntity(
                    id = noteId, book_id = bookId, title = noteTitle, body = "临时查阅来源条目",
                    excerpt = "来源摘录", kind = "note", created_at = Instant.now().toString(),
                    locator_json = """{"offset":0,"locator_v2":{"chapter":0,"charOffset":0}}""",
                    updated_at = Instant.now().toString(),
                ),
            )
        }

        // Leave the ordinary reader through system Back, then use the actual top-level route.
        Task34ReaderEntry.leaveReaderToTopLevel(
            composeRule,
            requireNotNull(scenario),
            visibleTopLevelTitle = "书架",
        )
        val frozen = awaitStableProgressRow()
        evidence.record("ordinary_reader_exited_to_top_level", "top_level_shelf", null, null, null, frozen)
        Task34ReaderEntry.openTemporaryInspection(composeRule, noteTitle)
        composeRule.onNodeWithContentDescription("返回阅读处", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(Task34EpubFixture.CHAPTER1_MARKER, substring = true).assertIsDisplayed()
        evidence.record("entered_temporary_from_notes_action", "temporary_reader", true, null, true,
            runBlocking { db.readingProgressDao().getByBook(bookId) })

        // 临时模式内跨章/目录查阅：跳到章 2
        Task34ReaderEntry.openTocAndSelectChapter(composeRule, chapterLabel = "第二章 乙")
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(Task34EpubFixture.CHAPTER2_MARKER, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(Task34EpubFixture.CHAPTER2_MARKER, substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回阅读处", substring = true).assertIsDisplayed()
        evidence.record("temporary_cross_chapter_marker_visible", "temporary_reader", null, true, true,
            runBlocking { db.readingProgressDao().getByBook(bookId) })

        // The complete 12-column Room entity must remain frozen through temporary chapter travel.
        val during = awaitStableProgressRow()
        assertEquals("临时查阅期间完整 ReadingProgressEntity 必须冻结", frozen, during)

        // Use the dedicated production return action, verify ordinary chapter restoration,
        // then perform a real location change and require that it persists.
        Task34ReaderEntry.returnFromTemporaryInspection(composeRule)
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(Task34EpubFixture.CHAPTER2_MARKER, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val returnActionNodes = composeRule.onAllNodesWithContentDescription("返回阅读处", substring = true)
            .fetchSemanticsNodes()
        assertTrue("返回后必须回到普通阅读模式", returnActionNodes.isEmpty())
        composeRule.onNodeWithText(Task34EpubFixture.CHAPTER2_MARKER, substring = true).assertIsDisplayed()
        val returned = awaitStableProgressRow()
        evidence.record("returned_to_ordinary_reader", "ordinary_reader", null, true, false, returned)
        assertEquals("返回普通阅读后完整 ReadingProgressEntity 必须保持冻结", frozen, returned)

        Task34ReaderEntry.openTocAndSelectChapter(composeRule, chapterLabel = "第一章 甲")
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(Task34EpubFixture.CHAPTER1_MARKER, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val afterReturnMovement = awaitStableProgressRow(differsFrom = frozen)
        composeRule.onNodeWithText(Task34EpubFixture.CHAPTER1_MARKER, substring = true).assertIsDisplayed()
        val expectedStable = frozen.copy(
            progress_percent = afterReturnMovement.progress_percent,
            last_read_at = afterReturnMovement.last_read_at,
            total_reading_time_ms = afterReturnMovement.total_reading_time_ms,
            current_location_json = afterReturnMovement.current_location_json,
            revision = afterReturnMovement.revision,
            updated_at = afterReturnMovement.updated_at,
        )
        assertEquals("返回后仅允许普通阅读保存字段按合同变化", expectedStable, afterReturnMovement)
        assertTrue("返回后的普通阅读位移必须改变 locator",
            afterReturnMovement.current_location_json != frozen.current_location_json)
        evidence.record("ordinary_movement_saved_after_return", "ordinary_reader", true, null, null,
            afterReturnMovement)
    }
}

