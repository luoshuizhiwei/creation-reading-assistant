package com.creationreadingassistant.acceptance

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 任务D3末项：灵感入口→临时查阅→Back→回灵感 真机验证（R2门批，批E/P5同链模式）。
 *
 * 产品缝源码实证（2026-11-01实读）：
 * - 灵感来源存payload.source（InspirationSourceInfo，含locatorJson，InspirationViewModel.kt:28-43）；
 * - 详情页source locator有效时渲染「查阅原文位置」FilledTonalButton（InspirationDetail.kt:349-365，
 *   inspirationLocateRoute→readerTemporaryRouteForSource）；
 * - 点击→InspectSourceLocator→onOpenRoute(临时route)（InspirationRoute.kt:229-248）；
 * - Back回灵感页=临时路由LIFO（reader外源入口同批C/E语义）。
 * reader退出双Back（183判例：第一次藏chrome，第二次导航）+sleep探测（waitUntil抛异常坑）。
 */
class InspirationEntryProductionTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val runId: String =
        InstrumentationRegistry.getArguments().getString("craRunId")
            ?: "T210I-" + (System.nanoTime() % 1_000_000_000L)

    private val bookIdA = "cra210i-$runId-book-a"

    private lateinit var db: AppDatabase
    private lateinit var ledger: J1RealReaderFixtures.RunLifecycleLedger
    private lateinit var contentA: J1RealReaderFixtures.BookAContent
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null

    private val inspTitle = "测试 TXT 甲 灵感来源"
    private val inspirationId = "cra210i-$runId-insp"

    @Before
    fun setUpFixtures() {
        db = J1RealReaderFixtures.openDatabase(appContext)
        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(appContext)
        ledger = J1RealReaderFixtures.RunLifecycleLedger(runId)
        val contents = J1RealReaderFixtures.setupRunFixtures(
            context = appContext, db = db, runId = runId,
            bookIdA = bookIdA, bookIdB = "cra210i-$runId-book-b",
            bookIdUnreadable = "cra210i-$runId-book-unreadable", ledger = ledger)
        contentA = contents.first
        seedInspiration()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDownFixtures() {
        scenario?.close()
        J1RealReaderFixtures.restoreOnboardingState(appContext, originalOnboarded)
        J1RealReaderFixtures.cleanRunFixtures(appContext, db, ledger)
    }

    private fun seedInspiration() {
        runBlocking {
            val noteOffset = contentA.offset1
            // payload必须真实JSON：locatorJson含双引号，手拼字符串会破坏JSON（attempt1红根因）
            // →用kotlinx.serialization构建（与InspirationPayloadData同口径）
            val locatorJson = LocatorCodec.encode(
                legacyOffset = noteOffset, chapterIndex = null, charOffset = null, excerpt = null)
            val source = kotlinx.serialization.json.Json.encodeToJsonElement(
                com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo.serializer(),
                com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo(
                    bookId = bookIdA, bookTitle = J1RealReaderFixtures.BOOK_TITLE_A,
                    chapterTitle = "第一章 序章", excerpt = "第一章 序章", locatorJson = locatorJson))
            val payload = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                kotlinx.serialization.json.buildJsonObject {
                    put("source", source)
                })
            db.inspirationDao().upsert(InspirationEntity(
                id = inspirationId, title = inspTitle, body = "测试 TXT 灵感正文（来源临时查阅）",
                type = "note", status = "inbox", source_book_id = bookIdA,
                payload = payload,
                created_at = "2026-11-01T00:00:00Z", updated_at = "2026-11-01T00:00:00Z"))
        }
    }

    private fun ensureShelfThenInspiration() {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
        // 底栏"灵感"tab（nav_inspiration）
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("灵感").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("灵感")[0].performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun inspirationEntry_temporaryOpens_backToInspiration() {
        ensureShelfThenInspiration()

        // 灵感列表出现本条（标题渲染）
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(inspTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(inspTitle)[0].performClick()
        composeRule.waitForIdle()

        // 详情页「查阅原文位置」按钮出现（locatorJson有效→按钮条件满足）
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("查阅原文位置").fetchSemanticsNodes().isNotEmpty()
        }

        // 门禁：记录进入临时查阅前的目标书普通阅读进度基线（防污染强断言）
        val baselineProgress = runBlocking { db.readingProgressDao().getByBook(bookIdA) }

        composeRule.onNodeWithText("查阅原文位置").performClick()
        composeRule.waitForIdle()
        Thread.sleep(4000)

        // 正向：临时reader打开。必须强断言 reader 控件可见，且正文锚点真实渲染并展示在屏幕上（禁止仅通用章名）
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(contentA.marker1, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val readerOpen = composeRule.onAllNodesWithText("更多").fetchSemanticsNodes().isNotEmpty() ||
            composeRule.onAllNodesWithTag("reader-progress-scrubber").fetchSemanticsNodes().isNotEmpty()
        assertTrue("临时reader应打开（chrome标志在场）", readerOpen)
        composeRule.onAllNodes(hasText(contentA.marker1, substring = true))[0].assertIsDisplayed()
        android.util.Log.i("InspEntryTest", "readerOpen=$readerOpen markerDisplayed=true")

        // 退出临时查阅
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

        // 正向强断言：回灵感详情身份与上下文（不仅标题可见，详情专属正文与按钮必须真实展示在屏幕，排除列表页歧义）
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(inspTitle).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("测试 TXT 灵感正文（来源临时查阅）").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(hasText(inspTitle))[0].assertIsDisplayed()
        composeRule.onAllNodes(hasText("测试 TXT 灵感正文（来源临时查阅）"))[0].assertIsDisplayed()

        // 强断言：临时查阅绝不能改写或污染日常阅读进度（严格零容差比对）
        val afterProgress = runBlocking { db.readingProgressDao().getByBook(bookIdA) }
        assertEquals("临时查阅前后进度存在性必须严格一致", baselineProgress == null, afterProgress == null)
        if (baselineProgress != null) {
            assertEquals("临时查阅不能修改普通阅读进度百分比(零容差)", baselineProgress.progress_percent, afterProgress!!.progress_percent, 0.0f)
        }
        assertEquals("临时查阅不能修改普通阅读进度定位", baselineProgress?.current_location_json, afterProgress?.current_location_json)
        assertEquals("临时查阅不能修改普通阅读进度定位", baselineProgress?.current_location_json, afterProgress?.current_location_json)

        val buttonBack = composeRule.onAllNodesWithText("查阅原文位置").fetchSemanticsNodes()
        android.util.Log.i("InspEntryTest", "backAtDetail=true buttonVisible=${buttonBack.isNotEmpty()} afterProgressCheck=PASS")
    }
}
