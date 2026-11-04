package com.creationreadingassistant.acceptance

import android.content.Context
import android.util.Log
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 任务P5（限定加签，合同 docs/coordination/2026-11-01-task-p5-external-pending-failure.md）：
 * 测试书真实清缓存后，搜索命中→临时查阅遇真实加载失败→Back回原搜索页，
 * 查询/结果上下文与原普通阅读位置保留。
 *
 * 场景级命题（索引留存/失败UX形态/Back落点）按合同为待运行假设：
 * 失败UX不预设字符串，按观察分支记录（AUTO_POP_TO_SEARCH / READER_OPENED）；
 * 索引未命中则check失败并如实报P5_PREMISE_NOT_REACHED方向，不改产品门/DB迎合。
 */
class ReaderExternalPendingFailureProductionTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val runId: String =
        InstrumentationRegistry.getArguments().getString("craRunId")
            ?: "T210P5-" + (System.nanoTime() % 1_000_000_000L)

    private val bookIdA = "cra210p5-$runId-book-a"

    private lateinit var db: AppDatabase
    private lateinit var ledger: J1RealReaderFixtures.RunLifecycleLedger
    private lateinit var contentA: J1RealReaderFixtures.BookAContent
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null

    @Before
    fun setUpFixtures() {
        db = J1RealReaderFixtures.openDatabase(appContext)
        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(appContext)
        ledger = J1RealReaderFixtures.RunLifecycleLedger(runId)
        val contents = J1RealReaderFixtures.setupRunFixtures(
            context = appContext, db = db, runId = runId,
            bookIdA = bookIdA, bookIdB = "cra210p5-$runId-book-b",
            bookIdUnreadable = "cra210p5-$runId-book-unreadable", ledger = ledger)
        contentA = contents.first
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDownFixtures() {
        scenario?.close()
        J1RealReaderFixtures.restoreOnboardingState(appContext, originalOnboarded)
        J1RealReaderFixtures.cleanRunFixtures(appContext, db, ledger)
    }

    private fun navigateToShelf() {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNode(hasText("书架") and isSelectable()).performClick()
            composeRule.waitForIdle()
        }
    }

    private fun openBookAAndRead() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("book-tile-$bookIdA").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("book-tile-$bookIdA").performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(contentA.marker1, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        // 普通Back退出（真实落S0位置）。任务183判例：第一次Back只藏chrome不导航，
        // 第二次才退出reader——waitUntil超时会抛异常，故探测用sleep+直查（批C/E判例）。
        scenario?.let { s ->
            s.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
        }
        composeRule.waitForIdle()
        Thread.sleep(3000)
        if (composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isEmpty()) {
            scenario?.let { s ->
                s.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            }
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clearCacheOfBookAViaProductUi(): String {
        // 更多(页面菜单，ShelfScreen.kt:241)→批量选择(:262)→点book A瓦片切换选中(:556)→清缓存(BatchSheet.kt:149)
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("批量选择").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("book-tile-$bookIdA").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("清缓存").performClick()
        composeRule.waitForIdle()
        Thread.sleep(3000)
        return "clear-cache issued via product batch path"
    }

    private fun navigateToSearchAndQuery() {
        // 全局搜索（"search"路由，含正文命中+临时查阅）唯一入口=首页搜索图标
        // （HomeScreen.kt:80-87 testTag"home-search-btn"→navigate("search")，HomeRoute.kt:138）。
        // 书架图标走"shelf/search"书名检索（无正文命中）——attempt2实证。
        // 清缓存后仍在书架页：先回首页tab（nav_home="首页"）。
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNode(hasText("首页") and isSelectable()).performClick()
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("home-search-btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home-search-btn").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("全局搜索").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("搜索书籍、灵感、笔记、高亮")
            .performTextInput(J1RealReaderFixtures.SEARCH_QUERY_A1)
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(contentA.marker1, substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(J1RealReaderFixtures.BOOK_TITLE_A)
                    .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun clearCacheThenSearchHit_realFailure_backToSearch() {
        navigateToShelf()
        openBookAAndRead()

        // 基线快照：progress行 + content_status
        val beforeProgress = runBlocking { db.readingProgressDao().getByBook(bookIdA) }
        val beforeBook = runBlocking { db.bookDao().getById(bookIdA) }
        check(beforeBook != null && beforeBook.content_status != "missing") {
            "precondition: book A content present before clear"
        }

        // 真实清缓存（产品UI批量路径）
        val clearObservation = clearCacheOfBookAViaProductUi()

        // 断言DB真实变化：content_status=missing、local_uri清空
        val afterBook = runBlocking { db.bookDao().getById(bookIdA) }
        check(afterBook != null && afterBook.content_status == "missing") {
            "clear-cache must set content_status=missing via product path (observed: $clearObservation)"
        }
        check(afterBook.local_uri == null) { "clear-cache must null local_uri" }
        // 书仍活跃在架
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("book-tile-$bookIdA").fetchSemanticsNodes().isNotEmpty()
        }

        // 搜索命中留存（索引未清）——未命中则本waitUntil抛超时=场景前提不成立方向
        navigateToSearchAndQuery()

        // 点击命中卡→临时查阅→观察真实失败UX（不预设文案，按分支记录）
        composeRule.onAllNodesWithText(J1RealReaderFixtures.BOOK_TITLE_A)[0].performClick()
        composeRule.waitForIdle()
        Thread.sleep(4000)

        val backAtSearch = composeRule.onAllNodesWithText("全局搜索")
            .fetchSemanticsNodes().isNotEmpty()
        val readerVisible = composeRule.onAllNodesWithTag("reader-progress-scrubber")
            .fetchSemanticsNodes().isNotEmpty() ||
            composeRule.onAllNodesWithContentDescription("更多").fetchSemanticsNodes().isNotEmpty()
        val markerRendered = composeRule.onAllNodesWithText(contentA.marker1, substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        val observedBranch = when {
            backAtSearch -> "AUTO_POP_TO_SEARCH"
            readerVisible -> "READER_OPENED"
            else -> "UNKNOWN_TRANSITION"
        }
        Log.i("P5Test", "branch=$observedBranch markerRendered=$markerRendered")
        check(observedBranch != "UNKNOWN_TRANSITION") {
            "click on hit must land somewhere observable (reader or search); neither seen"
        }
        if (observedBranch == "READER_OPENED") {
            // 正文未渲染=真实不可读（失败UX核心证据；实际可见文案以设备回执为准，不预设字符串）
            check(!markerRendered) {
                "content missing but body marker rendered; real-failure premise broken"
            }
            // 失败未提交路径按生产Back
            scenario?.let { s ->
                s.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            }
            composeRule.waitForIdle()
        }

        // 正向断言：回原搜索页+结果上下文保留
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("全局搜索").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("Back后应回原搜索页",
            composeRule.onAllNodesWithText("全局搜索").fetchSemanticsNodes().isNotEmpty())
        assertTrue("原命中卡应保留",
            composeRule.onAllNodesWithText(J1RealReaderFixtures.BOOK_TITLE_A)
                .fetchSemanticsNodes().isNotEmpty())

        // 原普通阅读位置：位置字段逐字比对（阅读时长等非位置字段白名单外仅记录不断言）
        val afterProgress = runBlocking { db.readingProgressDao().getByBook(bookIdA) }
        if (beforeProgress != null) {
            check(afterProgress != null) { "progress row must survive" }
            check(beforeProgress.current_location_json == afterProgress.current_location_json) {
                "normal reading location must be unchanged " +
                    "(before=${beforeProgress.current_location_json} after=${afterProgress.current_location_json})"
            }
            check(beforeProgress.progress_percent == afterProgress.progress_percent) {
                "progress_percent must be unchanged"
            }
            Log.i("P5Test", "readingTimeDelta=${afterProgress.total_reading_time_ms - beforeProgress.total_reading_time_ms}")
        }
        Log.i("P5Test", "P5 scenario completed; branch=$observedBranch")
    }
}
