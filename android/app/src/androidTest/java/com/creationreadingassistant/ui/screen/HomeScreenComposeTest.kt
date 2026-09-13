package com.creationreadingassistant.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.ui.layout.DefaultLayoutTokens
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.navigation.AppChrome
import com.creationreadingassistant.ui.navigation.LocalAppChrome
import com.creationreadingassistant.ui.screen.home.HomeAction
import com.creationreadingassistant.ui.screen.home.HomeScreen
import com.creationreadingassistant.ui.screen.home.HomeUiState
import com.creationreadingassistant.ui.theme.AppTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Home 纯 Screen 的 Compose 测试（JVM instrumentation，基于 createComposeRule）。
 *
 * 覆盖 3 个场景：
 *  1. 空数据 —— 所有 section 的空态都能正常显示。
 *  2. 长书名 —— 长书名 Ellipsis，不撑破卡片，标签显示。
 *  3. 大量数据（100 continue books） —— 不崩、首屏首卡可渲染、可滚动。
 *
 * **测试策略**：只测试 Pure Screen（[HomeUiState] + onAction），不引入 Hilt / ViewModel / NavController。
 *  通过 [recordedActions] 记录 onAction 调用顺序，验证点击触发正确语义的 HomeAction。
 */
class HomeScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val recordedActions = mutableListOf<HomeAction>()
    private val onAction: (HomeAction) -> Unit = { recordedActions.add(it) }

    // ============================================
    // 1. 空数据场景
    // ============================================
    @Test
    fun emptyState_AllSectionsShowEmptyHints() {
        composeRule.setContent {
            DefaultHomeFrame {
                HomeScreen(state = HomeUiState.Empty, onAction = onAction)
            }
        }

        // continue 空态
        composeRule.onNodeWithTag("continue-empty").assertIsDisplayed()

        // metrics 行（即使全 0 也应该渲染）
        composeRule.onNodeWithTag("metrics-title").assertIsDisplayed()
        composeRule.onNodeWithTag("metrics-row").assertIsDisplayed()
        // 数值在 GridStat Column 的子 Text 里，用 hasAnyDescendant 匹配（assertTextContains 不读子节点文本）
        composeRule.onNode(hasTestTag("metric-week") and hasAnyDescendant(hasText("0", substring = true))).assertExists()
        composeRule.onNode(hasTestTag("metric-completed") and hasAnyDescendant(hasText("0", substring = true))).assertExists()
        composeRule.onNode(hasTestTag("metric-today") and hasAnyDescendant(hasText("0", substring = true))).assertExists()

        // inspiration 空态
        composeRule.onNodeWithTag("home-insp-empty").assertIsDisplayed()

        // completed 空态（首页卡片化后整体变高，320dp 极限视口下该区块在屏外，先滚动到位再断言）
        composeRule.onNodeWithTag("home-lazy-column")
            .performScrollToNode(hasTestTag("completed-empty"))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            kotlin.runCatching {
                composeRule.onNodeWithTag("completed-empty").fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNodeWithTag("completed-empty").assertExists()
    }

    // ============================================
    // 2. 长书名场景
    // ============================================
    @Test
    fun longBookTitle_ContinuedCardTruncatesWithoutBreaking() {
        val longTitle = "红楼梦·脂砚斋重评石头记·庚辰本·全百二十回·曹雪芹著·高鹗续·脂砚斋评·绣像版·权威校注·人民文学出版社珍藏影印本·1982年版"
        val longBook = stubBook(id = "long-1", title = longTitle, author = "曹雪芹 / 高鹗")
        val state = HomeUiState.Empty.copy(
            continueBooks = persistentListOf(longBook),
            completedBooks = persistentListOf(longBook),
            recentInspirations = persistentListOf(
                stubInspiration(
                    id = "long-insp-1",
                    title = "关于《红楼梦》中林黛玉葬花辞的意象分析及其与王维山水诗意境的比较研究",
                    body = "花谢花飞花满天，红消香断有谁怜？这一句是葬花辞的起兴，承接了整部红楼梦的悲怆基调。",
                ),
            ),
        )

        composeRule.setContent {
            DefaultHomeFrame {
                HomeScreen(state = state, onAction = onAction)
            }
        }

        // ContinueCard 应该被渲染（testTag 存在即可，证明不崩）
        composeRule.onNodeWithTag("continue-card-long-1").assertIsDisplayed()
        // CompletedCard 同样（卡片化后首页变高，320dp 极限视口下需先滚动到该节点；
        // 按本用例注释的原始意图「存在即可」，用 assertExists 而非 assertIsDisplayed）
        composeRule.onNodeWithTag("home-lazy-column")
            .performScrollToNode(hasTestTag("completed-card-long-1"))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            kotlin.runCatching {
                composeRule.onNodeWithTag("completed-card-long-1").fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNodeWithTag("completed-card-long-1").assertExists()
        // 灵感 item 渲染，长标题 Ellipsis 不崩
        composeRule.onNodeWithTag("insp-item-long-insp-1").assertIsDisplayed()
    }

    // ============================================
    // 3. 大量数据（continue books 100 条）
    // ============================================
    @Test
    fun largeDataSet_100ContinueBooks_DoesNotCrash() {
        val manyContinue = (1..100).map { i ->
            stubBook(id = "many-$i", title = "继续阅读第 ${i} 本书", author = "作者 $i")
        }
        val manyCompleted = (1..20).map { i ->
            stubBook(id = "many-compl-$i", title = "已读第 ${i} 本", author = "作者 X")
        }
        val manyInsp = (1..40).map { i ->
            stubInspiration(id = "many-insp-$i", title = "灵感 #$i", body = "身体内容 $i" + "x".repeat(100))
        }
        val state = HomeUiState.Empty.copy(
            continueBooks = manyContinue.toImmutableList(),
            completedBooks = manyCompleted.toImmutableList(),
            recentInspirations = manyInsp.toImmutableList(),
            thisWeekNew = 12,
            readingCount = 8,
            completedCount = 42,
            todayReadingMs = 68 * 60 * 1000L, // 68 分钟
        )

        var didCrash = false
        try {
            composeRule.setContent {
                DefaultHomeFrame {
                    HomeScreen(state = state, onAction = onAction)
                }
            }
            // 验证首屏首卡可见
            composeRule.onNodeWithTag("continue-card-many-1").assertIsDisplayed()
            // 验证 metrics（数值在子 Text 节点里，用 hasAnyDescendant 匹配）
            composeRule.onNode(hasTestTag("metric-week") and hasAnyDescendant(hasText("12", substring = true))).assertExists()
            composeRule.onNode(hasTestTag("metric-completed") and hasAnyDescendant(hasText("42", substring = true))).assertExists()
            composeRule.onNode(hasTestTag("metric-today") and hasAnyDescendant(hasText("1h 8m", substring = true, ignoreCase = true))).assertExists()
        } catch (t: Throwable) {
            didCrash = true
        }
        assertFalse("大量数据下 HomeScreen 不应抛出异常", didCrash)
        assertTrue("继续阅读列表应该实际渲染了（LazyRow testTag 存在）",
            kotlin.runCatching { composeRule.onNodeWithTag("continue-lazy-row").assertIsDisplayed() }.isSuccess)
    }

    // ============================================
    // 测试辅助
    // ============================================
    @Composable
    private fun DefaultHomeFrame(content: @androidx.compose.runtime.Composable () -> Unit) {
        AppTheme {
            CompositionLocalProvider(
                LocalLayoutTokens provides DefaultLayoutTokens,
                LocalAppChrome provides AppChrome(
                    bottomNavHeight = 80.dp,
                    navLayerPaddingApplied = true,
                ),
                // 320dp 紧凑屏 + 字体放大 1.3 倍是上一轮 shell 重构测试的边界，复用它增加 Home 测试严格度
                LocalDensity provides Density(density = 2.75f, fontScale = 1.3f),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .requiredSize(320.dp, 640.dp)
                        .testTag("home-frame"),
                ) {
                    content()
                }
            }
        }
    }

    private fun stubBook(
        id: String,
        title: String,
        author: String? = null,
    ): BookEntity = BookEntity(
        id = id,
        title = title,
        author = author,
        format = "txt",
        size = 1024 * 100,
        local_content_path = "/sdcard/fake/$id.txt",
        updated_at = Instant.now().toString(),
    )

    private fun stubInspiration(
        id: String,
        title: String,
        body: String,
    ): InspirationEntity = InspirationEntity(
        id = id,
        title = title,
        body = body,
        created_at = Instant.now().toString(),
        updated_at = Instant.now().toString(),
    )
}
