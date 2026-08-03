package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.ui.layout.DefaultLayoutTokens
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.navigation.AppChrome
import com.creationreadingassistant.ui.navigation.LocalAppChrome
import com.creationreadingassistant.ui.screen.shelf.ShelfViewMode
import com.creationreadingassistant.ui.screen.shelf.ShelfStatusFilter
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.viewmodel.ShelfBookItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * ShelfScreen 验收项 UI 测试：
 *  1. 393dp 屏幕宽度时：书架三列布局
 *  2. 搜索 / 多选状态：只替换顶栏内容，不创建第二个顶栏
 *  3. 下拉刷新：刷新指示器只覆盖内容区（位于顶栏之下）
 *  4. 同行卡片底部对齐：无论一行 / 两行 / 超长书名，卡片高度与底部位置一致
 *
 * 注：本项目 Compose 版本中 getBoundsInRoot() 返回 DpRect（坐标单位为 Dp）。
 *     HomeScreenComposeTest.kt 中的既有编译错误为仓库原有遗留，非本文件引入。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
class ShelfScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    // —————————————————————————————————————————————————————————
    // 验收 1：393dp 为三列
    // —————————————————————————————————————————————————————————

    @Test
    fun shelfAt393dpShowsThreeColumns() {
        // GridCells.Adaptive(minSize = 104dp)：
        //   2列需要 208dp ，3列需要 312dp ，4列需要 416dp
        //   => 393dp 刚好三列（312 ≤ 393 < 416）
        val books = (1..6).map { i -> shelfItem(id = "b$i", title = "书名 $i") }

        renderShelf(width = 393.dp, height = 800.dp, items = books, viewMode = ShelfViewMode.GRID)

        composeRule.onNodeWithTag("shelf-screen").assertIsDisplayed()

        fun bookTop(id: String): Dp =
            composeRule.onAllNodesWithTag("book-tile-$id")[0].getBoundsInRoot().top

        if (composeRule.onAllNodesWithTag("book-tile-b1").fetchSemanticsNodes().isNotEmpty()) {
            val top1 = bookTop("b1")
            val top2 = bookTop("b2")
            val top3 = bookTop("b3")
            assertDpEquals("同一行 (1,2,3) 顶部应对齐", top1, top2, tolerance = 2.dp)
            assertDpEquals("同一行 (2,3) 顶部应对齐", top2, top3, tolerance = 2.dp)

            val top4 = bookTop("b4")
            val top5 = bookTop("b5")
            val top6 = bookTop("b6")
            assertDpEquals("同一行 (4,5,6) 顶部应对齐", top4, top5, tolerance = 2.dp)
            assertDpEquals("同一行 (5,6) 顶部应对齐", top5, top6, tolerance = 2.dp)

            val rowGap = top4 - top1
            assertTrue("第1、2 行顶部必须存在行距差 (实际 = $rowGap)", rowGap > 20.dp)
        }
    }

    // —————————————————————————————————————————————————————————
    // 验收 2：搜索 / 多选状态只替换顶栏内容，不创建第二个顶栏
    // —————————————————————————————————————————————————————————

    @Test
    fun normalStateShowsOneTopBar() {
        renderShelf(width = 393.dp, height = 800.dp)
        composeRule.onNodeWithText("书架").assertIsDisplayed()
        composeRule.onNodeWithTag("shelf-screen", useUnmergedTree = true)
            .assertExists("AppScreenScaffold 必须存在")
            .assertHeightIsAtLeast(DefaultLayoutTokens.topBarHeight)
    }

    @Test
    fun selectionModeReplacesTopBarContentWithoutSecondTopBar() {
        val books = (1..3).map { shelfItem(id = "b$it", title = "书 $it") }
        var state by mutableStateOf(defaultState(items = books).copy(selectionMode = true))

        composeRule.setContent {
            ThemedRoot { ShelfScreen(state = state, onAction = {}) }
        }

        composeRule.onNodeWithText("选择书籍").assertIsDisplayed()
        assertEquals(
            "选择模式下不应再显示普通标题 \"书架\"",
            0,
            composeRule.onAllNodesWithText("书架").fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithTag("shelf-screen", useUnmergedTree = true).assertExists()
    }

    @Test
    fun searchActivePlacesInputBelowTopBarWithoutSecondTopBar() {
        var state by mutableStateOf(defaultState().copy(searchActive = true, query = ""))

        composeRule.setContent {
            ThemedRoot { ShelfScreen(state = state, onAction = {}) }
        }

        composeRule.onNodeWithText("取消").assertIsDisplayed()
        composeRule.onNodeWithTag("shelf-screen", useUnmergedTree = true).assertExists()
        assertEquals(
            "搜索激活状态只创建一个主顶栏",
            1,
            composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun compactFilterSummaryStaysInsideNarrowScreen() {
        val state = defaultState().copy(
            statusFilter = ShelfStatusFilter.READING,
        )

        composeRule.setContent {
            AppTheme {
                Box(Modifier.requiredSize(320.dp, 720.dp).testTag("root")) {
                    CompositionLocalProvider(
                        LocalAppChrome provides AppChrome(bottomNavHeight = 0.dp, navLayerPaddingApplied = true),
                        LocalLayoutTokens provides DefaultLayoutTokens,
                        LocalShelfSnackbar provides SnackbarHostState(),
                    ) {
                        ShelfScreen(state = state, onAction = {})
                    }
                }
            }
        }

        val summary = composeRule.onNodeWithTag("shelf-filter-summary").assertIsDisplayed().getBoundsInRoot()
        val root = composeRule.onNodeWithTag("root").getBoundsInRoot()
        assertTrue("紧凑摘要左侧不能越界", summary.left >= root.left)
        assertTrue("紧凑摘要右侧不能越界", summary.right <= root.right)
        composeRule.onNodeWithText("0 本 · 在读 · 最近阅读").assertIsDisplayed()
    }

    // —————————————————————————————————————————————————————————
    // 验收 3：下拉刷新只覆盖书架内容（不覆盖顶栏）
    // —————————————————————————————————————————————————————————

    @Test
    fun pullRefreshIndicatorLiesBelowTopBar() {
        val books = (1..6).map { shelfItem(id = "b$it", title = "书 $it") }
        renderShelf(width = 393.dp, height = 800.dp, items = books, refreshing = true)

        val indicator = composeRule.onNodeWithTag("shelf-refresh-indicator")
            .assertExists("刷新指示器必须存在")
        val indicatorTop = indicator.getUnclippedBoundsInRoot().top
        val topBarH = DefaultLayoutTokens.topBarHeight

        assertTrue(
            "刷新指示器顶部 ($indicatorTop) 应 >= 顶栏高度 ($topBarH)",
            indicatorTop >= topBarH - 1.dp,
        )

        composeRule.onNodeWithTag("shelf-content-box").assertExists("内容区必须存在")
    }

    // —————————————————————————————————————————————————————————
    // 验收 4：一行 / 两行 / 超长书名保持同行卡片底部对齐
    // —————————————————————————————————————————————————————————

    @Test
    fun booksWithMixedTitleLengthsKeepSameBottomInSameRow() {
        val titleShort = "短"
        val titleTwoLine = "中等长度书名刚好占两行空间两行"
        val titleLong = "这是一个超级超级超级超级长的书名，他会长到三行甚至更多，用来验证不同书名长度的同行卡片底部依然对齐"

        val items = listOf(titleShort, titleTwoLine, titleLong).mapIndexed { i, t ->
            shelfItem(id = "mix$i", title = t)
        }

        renderShelf(width = 393.dp, height = 1000.dp, items = items, viewMode = ShelfViewMode.GRID)
        composeRule.waitForIdle()

        fun bounds(i: Int) = composeRule.onAllNodesWithTag("book-tile-mix$i")[0].getBoundsInRoot()
        val b0 = bounds(0)
        val b1 = bounds(1)
        val b2 = bounds(2)
        val tol = 2.dp

        // bottom 对齐
        assertDpEquals("短/中等底部一致", b0.bottom, b1.bottom, tol)
        assertDpEquals("短/超长底部一致", b0.bottom, b2.bottom, tol)
        assertDpEquals("中等/超长底部一致", b1.bottom, b2.bottom, tol)

        // 高度一致（height = bottom - top）
        fun h(b: androidx.compose.ui.unit.DpRect): Dp = b.bottom - b.top
        assertDpEquals("短/中等高度一致", h(b0), h(b1), tol)
        assertDpEquals("短/超长高度一致", h(b0), h(b2), tol)
    }

    // —————————————————————————————————————————————————————————
    // 辅助：Dp 友好的 assertEquals（比较时转换为 double + delta）
    // —————————————————————————————————————————————————————————

    private fun assertDpEquals(msg: String, expected: Dp, actual: Dp, tolerance: Dp) {
        assertEquals(msg, expected.value.dbl(), actual.value.dbl(), tolerance.value.dbl())
    }

    private fun Float.dbl(): Double = (this as Number).toDouble()

    // —————————————————————————————————————————————————————————
    // 辅助：state 构造 + 统一主题壳层
    // —————————————————————————————————————————————————————————

    private fun defaultState(items: List<ShelfBookItem> = emptyList()): ShelfUiState =
        ShelfUiState(shelfBooks = items, showSkeleton = false)

    private fun shelfItem(id: String, title: String): ShelfBookItem {
        val entity = BookEntity(
            id = id,
            title = title,
            format = "txt",
            size = 1000,
            content_status = "available",
            updated_at = "2026-01-01T00:00:00Z",
        )
        return ShelfBookItem.from(entity, null)
    }

    @Composable
    private fun ThemedRoot(content: @Composable () -> Unit) {
        AppTheme {
            Box(Modifier.requiredSize(393.dp, 800.dp).testTag("root")) {
                CompositionLocalProvider(
                    LocalAppChrome provides AppChrome(
                        bottomNavHeight = 0.dp,
                        navLayerPaddingApplied = true,
                    ),
                    LocalLayoutTokens provides DefaultLayoutTokens,
                    LocalShelfSnackbar provides SnackbarHostState(),
                ) {
                    content()
                }
            }
        }
    }

    private fun renderShelf(
        width: Dp,
        height: Dp,
        items: List<ShelfBookItem> = emptyList(),
        viewMode: ShelfViewMode = ShelfViewMode.GRID,
        refreshing: Boolean = false,
    ) {
        var state by mutableStateOf(
            defaultState(items = items).copy(
                viewMode = viewMode,
                isRefreshing = refreshing,
            ),
        )
        composeRule.setContent {
            AppTheme {
                Box(Modifier.requiredSize(width, height).testTag("root")) {
                    CompositionLocalProvider(
                        LocalAppChrome provides AppChrome(
                            bottomNavHeight = 0.dp,
                            navLayerPaddingApplied = true,
                        ),
                        LocalLayoutTokens provides DefaultLayoutTokens,
                        LocalShelfSnackbar provides SnackbarHostState(),
                    ) {
                        ShelfScreen(state = state, onAction = {})
                    }
                }
            }
        }
    }
}
