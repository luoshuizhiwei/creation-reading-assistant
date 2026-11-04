package com.creationreadingassistant.acceptance

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * AG04 补强：Profile笔记外部回源强语义验收（身份/位置/进度不污染/原上下文单落点）。
 *
 * 核心强断言：
 * 1. 建立普通阅读进度基线，强断言临时查阅前后进度逐字/逐偏移不污染；
 * 2. 绑定目标书 ID 与来源 locator，强断言正文目标 marker 渲染在屏幕；
 * 3. 拒绝双落点放宽，强制双 Back 后精确返回原 NOTES 来源子页上下文（原笔记标题 assertIsDisplayed）。
 */
class ReaderExternalEntryProductionTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val runId: String =
        InstrumentationRegistry.getArguments().getString("craRunId")
            ?: "T210-" + (System.nanoTime() % 1_000_000_000L)

    private val bookIdA = "cra210-$runId-book-a"
    private var noteOffset: Int = 0

    private lateinit var db: AppDatabase
    private lateinit var ledger: J1RealReaderFixtures.RunLifecycleLedger
    private lateinit var contentA: J1RealReaderFixtures.BookAContent
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null

    private val noteTitle = "测试 TXT 甲 外部入口源"
    private val noteBody = "测试 TXT 笔记正文（外部入口）"

    @Before
    fun setUpFixtures() {
        db = J1RealReaderFixtures.openDatabase(appContext)
        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(appContext)
        ledger = J1RealReaderFixtures.RunLifecycleLedger(runId)
        val contents = J1RealReaderFixtures.setupRunFixtures(
            context = appContext, db = db, runId = runId,
            bookIdA = bookIdA, bookIdB = "cra210-$runId-book-b",
            bookIdUnreadable = "cra210-$runId-book-unreadable", ledger = ledger)
        contentA = contents.first
        noteOffset = contentA.offset1
        seedNote()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDownFixtures() {
        scenario?.close()
        J1RealReaderFixtures.restoreOnboardingState(appContext, originalOnboarded)
        J1RealReaderFixtures.cleanRunFixtures(appContext, db, ledger)
    }

    private fun seedNote() {
        runBlocking {
            db.noteDao().upsert(NoteEntity(
                id = "cra210-$runId-note-ext", book_id = bookIdA,
                title = noteTitle, body = noteBody,
                locator_json = LocatorCodec.encode(
                    legacyOffset = noteOffset, chapterIndex = null, charOffset = null, excerpt = null),
                created_at = "2026-10-31T00:00:00Z", updated_at = "2026-10-31T00:00:00Z",
                kind = "note"))
        }
    }

    private fun navigateToProfileHome() {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("我的")[0].performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithContentDescription("搜索设置项").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun navigateToNotesViaSettingsSearch() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("阅读笔记").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("阅读笔记")[0].performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("我的书评 / 笔记").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("阅读沉淀").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(noteBody, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun externalEntry_profilePill_temporaryOpens_backToProfile() {
        // 1. 基线建立：记录进入临时查阅前目标书的真实普通进度状态（禁止默认0掩盖）
        val baselineProgress = runBlocking { db.readingProgressDao().getByBook(bookIdA) }
        val baselinePercent = baselineProgress?.progress_percent
        val baselineLoc = baselineProgress?.current_location_json

        navigateToProfileHome()
        navigateToNotesViaSettingsSearch()

        // 笔记卡pill「临时查看来源」（AnnotationEntryCard，locator有效→胶囊在场）
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("临时查看来源").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("临时查看来源")[0].performClick()
        composeRule.waitForIdle()

        // 2. 强断言：目标书正文内容与目标位置 marker 必须真实渲染在屏幕（强身份与位置证明，同目标范围）
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(contentA.marker1, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(hasText(contentA.marker1, substring = true))[0].assertIsDisplayed()
        val chromeOpen = composeRule.onAllNodesWithText("更多").fetchSemanticsNodes().isNotEmpty() ||
            composeRule.onAllNodesWithTag("reader-progress-scrubber").fetchSemanticsNodes().isNotEmpty()
        android.util.Log.i("BatchCR2", "Target content displayed, chromeOpen=$chromeOpen")

        // 3. 退出临时查阅
        scenario?.let { s ->
            s.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
        }
        composeRule.waitForIdle()
        Thread.sleep(2000)
        val inReader = composeRule.onAllNodesWithText(contentA.marker1, substring = true).fetchSemanticsNodes().isNotEmpty()
        if (inReader) {
            scenario?.let { s ->
                s.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            }
            composeRule.waitForIdle()
        }

        // 4. 强断言：精确返回原 NOTES 来源子页，原笔记卡内容（批注体）与胶囊按钮必须正向可见（拒绝双落点放宽）
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(noteBody, substring = true).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("临时查看来源").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(hasText(noteBody, substring = true))[0].assertIsDisplayed()
        composeRule.onAllNodes(hasText("临时查看来源"))[0].assertIsDisplayed()

        // 5. 强断言：离开临时阅读器，正文与阅读器 chrome 绝不残留
        val readerGone = composeRule.onAllNodesWithText("更多").fetchSemanticsNodes().isEmpty() &&
            composeRule.onAllNodesWithTag("reader-progress-scrubber").fetchSemanticsNodes().isEmpty()
        assertTrue("Back后应彻底离开临时reader", readerGone)

        // 6. 强断言：普通阅读进度前后绝对不被临时查阅污染（严格精确零容差比对，禁止0.001掩盖）
        val afterProgress = runBlocking { db.readingProgressDao().getByBook(bookIdA) }
        val afterPercent = afterProgress?.progress_percent
        val afterLoc = afterProgress?.current_location_json
        assertEquals("临时查阅前后进度记录存在性必须严格一致", baselineProgress == null, afterProgress == null)
        if (baselineProgress != null) {
            assertEquals("临时查阅前后目标书普通阅读百分比必须严格一致(零容差)，禁止污染", baselinePercent!!, afterPercent!!, 0.0f)
        }
        assertEquals("临时查阅前后目标书普通阅读定位必须严格一致，禁止污染", baselineLoc, afterLoc)
        android.util.Log.i("BatchCR2", "Baseline percent=$baselinePercent, after percent=$afterPercent, uncontaminated strict verified")
    }
}
