package com.creationreadingassistant.acceptance

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
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
 * 任务210批E：搜索入口→正文命中→临时查阅→Back→回搜索 真机验证。
 *
 * 选择器源码实证（2026-11-01实读）：
 * - 书架搜索图标 contentDescription="搜索"（ShelfScreen.kt:205）；
 * - 搜索页标题"全局搜索"、输入框 placeholder"搜索书籍、灵感、笔记、高亮"（SearchScreen.kt:164）；
 * - 正文命中卡**无**"临时查看来源"胶囊——卡片整体onClick即走临时查阅route
 *   （SearchScreen.kt:469-483 readerTemporaryRouteForSource→navigate）；
 * - 命中卡标题=buildHighlighted单一AnnotatedString（SearchResultComponents.kt:35），
 *   全标题单节点可精确匹配。
 * reader退出用系统Back（临时路由LIFO返回搜索页；同批C v11判例）。
 */
class SearchEntryProductionTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val runId: String =
        InstrumentationRegistry.getArguments().getString("craRunId")
            ?: "T210B-" + (System.nanoTime() % 1_000_000_000L)

    private val bookIdA = "cra210b-$runId-book-a"

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
            bookIdA = bookIdA, bookIdB = "cra210b-$runId-book-b",
            bookIdUnreadable = "cra210b-$runId-book-unreadable", ledger = ledger)
        contentA = contents.first
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDownFixtures() {
        scenario?.close()
        J1RealReaderFixtures.restoreOnboardingState(appContext, originalOnboarded)
        J1RealReaderFixtures.cleanRunFixtures(appContext, db, ledger)
    }

    private fun ensureShelf() {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNode(hasText("书架") and isSelectable()).performClick()
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun ensureHome() {
        composeRule.waitForIdle()
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
    }

    private fun navigateToShelfAndOpenBookA() {
        ensureShelf()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("book-tile-$bookIdA").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("book-tile-$bookIdA").performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(contentA.marker1, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun navigateToHomeSearch() {
        // 全局搜索（"search"路由，含正文命中+临时查阅）唯一入口=首页搜索图标
        // （HomeScreen.kt:80-87 testTag"home-search-btn"→HomeAction.OpenSearch→navigate("search")，
        //  HomeRoute.kt:138）。书架图标走"shelf/search"（ShelfSearchRoute，书名检索，无正文命中）。
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
    }

    @Test
    fun searchEntry_contentHit_temporaryOpens_backToSearch() {
        // Phase 1: 打开 book A（构建索引）→关闭scenario→relaunch（索引存DB跨重启存活）
        navigateToShelfAndOpenBookA()
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()

        // Phase 2: relaunch后落首页→首页搜索图标→全局搜索页
        ensureHome()
        navigateToHomeSearch()

        // 输入搜索词：placeholder定位OutlinedTextField输入字段
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("搜索书籍、灵感、笔记、高亮")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("搜索书籍、灵感、笔记、高亮")
            .performTextInput(J1RealReaderFixtures.SEARCH_QUERY_A1)
        composeRule.waitForIdle()

        // 等待正文命中卡出现（索引留存；标题=单一AnnotatedString全文本可精确匹配）
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(J1RealReaderFixtures.BOOK_TITLE_A)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // 点击正文命中卡整体（onClick→readerTemporaryRouteForSource→临时查阅；无胶囊）
        composeRule.onAllNodesWithText(J1RealReaderFixtures.BOOK_TITLE_A)[0].performClick()
        composeRule.waitForIdle()

        // 临时reader打开：正文marker可见（正向确认到达reader正文）
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(contentA.marker1, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(contentA.marker1, substring = true)[0].assertIsDisplayed()

        // 系统Back→临时路由LIFO→回原搜索页。
        // 任务183判例：reader打开即chrome可见，第一次Back只藏控件不导航；第二次Back才退出。
        // waitUntil超时会抛异常，故条件探测用sleep+直查（批C判例）。
        scenario?.let { s ->
            s.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
        }
        composeRule.waitForIdle()
        Thread.sleep(3000)
        if (composeRule.onAllNodesWithText("全局搜索").fetchSemanticsNodes().isEmpty()) {
            // 仍在临时reader（chrome已藏）→第二次Back退出
            scenario?.let { s ->
                s.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            }
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("全局搜索").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("Back后应回到原搜索页",
            composeRule.onAllNodesWithText("全局搜索").fetchSemanticsNodes().isNotEmpty())
        assertTrue("搜索结果上下文应保留",
            composeRule.onAllNodesWithText(J1RealReaderFixtures.BOOK_TITLE_A)
                .fetchSemanticsNodes().isNotEmpty())
    }
}
