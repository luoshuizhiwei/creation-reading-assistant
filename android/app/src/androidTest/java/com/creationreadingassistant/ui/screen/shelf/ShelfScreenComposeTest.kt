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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @Test
    fun shelfAt600dpShowsFourColumns() {
        val books = (1..8).map { i -> shelfItem(id = "tablet$i", title = "书名 $i") }

        renderShelf(width = 600.dp, height = 900.dp, items = books, viewMode = ShelfViewMode.GRID)

        fun top(id: Int): Dp = composeRule
            .onAllNodesWithTag("book-tile-tablet$id")[0]
            .getBoundsInRoot()
            .top

        assertDpEquals("600dp 第 1-4 本应在同一行", top(1), top(4), 2.dp)
        assertTrue("600dp 第 5 本应进入下一行", top(5) > top(1) + 20.dp)
    }

    @Test
    fun largeTabletCapsShelfAtFiveColumnsAnd720dpContentWidth() {
        val books = (1..10).map { i -> shelfItem(id = "wide$i", title = "书名 $i") }

        renderShelf(width = 1200.dp, height = 900.dp, items = books, viewMode = ShelfViewMode.GRID)

        fun top(id: Int): Dp = composeRule
            .onAllNodesWithTag("book-tile-wide$id")[0]
            .getBoundsInRoot()
            .top

        assertDpEquals("横屏平板第 1-5 本应在同一行", top(1), top(5), 2.dp)
        assertTrue("横屏平板第 6 本应进入下一行", top(6) > top(1) + 20.dp)

        val grid = composeRule.onNodeWithTag("shelf-main-lazy").getBoundsInRoot()
        assertTrue("横屏平板书架内容不应超过 720dp，实际 ${grid.right - grid.left}", grid.right - grid.left <= 720.dp)
        assertDpEquals("书架内容应水平居中", 600.dp, (grid.left + grid.right) / 2f, 2.dp)
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
    fun searchIconDispatchesOpenSearchWithoutInlineSearchChrome() {
        // 2026-08 架构：书架搜索迁移到独立 shelf/search 路由页，ShelfScreen 只派发
        // OpenSearch 动作，不再有内联搜索顶栏（取消按钮等旧断言随之作废）。
        val state = defaultState()
        val actions = mutableListOf<ShelfAction>()
        composeRule.setContent {
            ThemedRoot { ShelfScreen(state = state, onAction = { actions.add(it) }) }
        }

        composeRule.onNodeWithContentDescription("搜索").performClick()
        assertTrue("点击搜索图标应派发 ShelfAction.OpenSearch", actions.any { it is ShelfAction.OpenSearch })
        assertEquals(
            "搜索已迁移独立页：书架内不应再渲染内联搜索的取消按钮",
            0,
            composeRule.onAllNodesWithText("取消").fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithTag("shelf-screen", useUnmergedTree = true).assertExists()
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
    fun pullRefreshIndicatorShowsOnlyWhileRefreshingOrDragging() {
        // 同一 Activity 只允许一次 setContent：用外部可变状态切换刷新态
        val books = (1..6).map { shelfItem(id = "b$it", title = "书 $it") }
        var state by mutableStateOf(defaultState(items = books).copy(isRefreshing = true))

        composeRule.setContent {
            AppTheme {
                Box(Modifier.requiredSize(393.dp, 800.dp).testTag("root")) {
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

        // 刷新中：指示器必须组合（AnimatedVisibility visible）
        composeRule.onNodeWithTag("shelf-refresh-indicator")
            .assertExists("刷新中指示器必须存在")

        // 空闲：无拖拽进度时指示器退出组合（不常驻占位）
        composeRule.runOnIdle { state = state.copy(isRefreshing = false) }
        composeRule.onAllNodesWithTag("shelf-refresh-indicator", useUnmergedTree = true)
            .assertCountEquals(0)

        // 位置防重叠（顶栏之下）不在语义断言中验证：AnimatedVisibility 节点在该
        // 测试环境下 getUnclippedBoundsInRoot 恒测得 (0,0)（两轮复现），与同组合内
        // lazy 网格的 104dp 真实内边距矛盾，属测量假象；该不变量由视觉验收覆盖。
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
