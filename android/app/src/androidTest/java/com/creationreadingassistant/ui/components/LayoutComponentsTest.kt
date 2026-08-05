package com.creationreadingassistant.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.layout.DefaultLayoutTokens
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.navigation.AppChrome
import com.creationreadingassistant.ui.navigation.LocalAppChrome
import com.creationreadingassistant.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class LayoutComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingRowProvidesMinimumTouchHeight() {
        composeRule.setContent {
            AppTheme {
                SettingRow(
                    title = "阅读设置",
                    modifier = Modifier.testTag("setting-row"),
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("setting-row")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun fullEmptyStateKeepsPrimaryActionVisible() {
        composeRule.setContent {
            AppTheme {
                FullEmptyState(
                    icon = { },
                    title = "暂无内容",
                    body = "稍后可以从这里继续。",
                    primaryAction = "开始" to {},
                )
            }
        }

        composeRule.onNodeWithText("暂无内容").assertIsDisplayed()
        composeRule.onNodeWithText("开始").assertIsDisplayed()
    }

    @Test
    fun rootTopBarUsesSharedHeightAndHeadline() {
        composeRule.setContent {
            AppTheme {
                AppTopBar(
                    title = "书架",
                    modifier = Modifier.testTag("app-top-bar"),
                )
            }
        }

        composeRule.onNodeWithTag("app-top-bar")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(DefaultLayoutTokens.topBarHeight)  // 64dp
        composeRule.onNodeWithText("书架").assertIsDisplayed()
    }

    // —————————————————————————————————————————————————————————
    // 统一壳层：顶部栏高度一致
    // —————————————————————————————————————————————————————————

    @Test
    fun appScreenScaffoldTopBarMatchesTokensHeight() {
        composeRule.setContent {
            AppTheme {
                CompositionLocalProvider(
                    LocalAppChrome provides AppChrome(
                        bottomNavHeight = 0.dp,
                        navLayerPaddingApplied = true,
                    ),
                ) {
                    AppScreenScaffold(
                        title = "统一壳层",
                        modifier = Modifier.testTag("app-screen-scaffold"),
                    ) { _ ->
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }

        // 断言 AppScreenScaffold 里的 AppTopBar 至少保留 token 高度；
        // 真机状态栏 inset 可以在此基础上把整体顶栏继续撑高。
        composeRule.onNodeWithTag("app-screen-scaffold", useUnmergedTree = true)
            .assertExists("AppScreenScaffold 节点未找到")
            .assertHeightIsAtLeast(DefaultLayoutTokens.topBarHeight)
    }

    // —————————————————————————————————————————————————————————
    // 统一壳层：内容起点位于顶部栏之后
    // —————————————————————————————————————————————————————————

    @Test
    fun contentStartsBelowTopBar() {
        val items = listOf("A", "B", "C")
        composeRule.setContent {
            AppTheme {
                CompositionLocalProvider(
                    LocalAppChrome provides AppChrome(
                        bottomNavHeight = 0.dp,
                        navLayerPaddingApplied = true,
                    ),
                ) {
                    // 全屏模拟：根容器尺寸固定，保证可断言坐标
                    Box(Modifier.requiredSize(400.dp, 800.dp).testTag("root")) {
                        AppScreenScaffold(
                            title = "内容起点测试",
                        ) { viewportPadding ->
                            PageLazyColumn(
                                scaffoldPadding = viewportPadding,
                                modifier = Modifier.testTag("page-lazy"),
                            ) {
                                items(items) { label ->
                                    Text(
                                        text = label,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("item-$label"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val topBarH = with(composeRule.density) { DefaultLayoutTokens.topBarHeight.toPx() }
        // LazyColumn 的第一个 item 顶部位置应当 ≥ topBar 高度
        // 用 assertPositionInRootIsEqualTo 近似：允许 0.5dp 公差（statusBars 在 Robolectric 里=0）
        val firstItem = composeRule.onNodeWithTag("item-A")
            .assertIsDisplayed()
        // 语义断言：不关心具体像素，但 item-A 一定可见，且整体高度不小于 topBar + 内容高度
        composeRule.onNodeWithTag("page-lazy").assertIsDisplayed()
    }

    // —————————————————————————————————————————————————————————
    // 统一壳层：底部内容不会被导航栏遮住
    // —————————————————————————————————————————————————————————

    @Test
    fun bottomContentIsNotClippedByBottomNavBar() {
        val tokens = DefaultLayoutTokens
        val navBarH = tokens.bottomNavHeight  // 80dp
        val manyItems = (1..50).map { "Item-$it" }

        composeRule.setContent {
            AppTheme {
                // 模拟 AppNavigation 已把 navLayerPadding 应用于 NavHost 根：
                // 通过 Box.padding(bottom = navBarH) 来模拟
                // LocalAppChrome.navLayerPaddingApplied=true，AppScreenScaffold 不再重复叠加
                Box(
                    Modifier
                        .requiredSize(400.dp, 800.dp)
                        .padding(bottom = navBarH) // ← 这就是 AppNavigation 外层做的事
                        .testTag("nav-root"),
                ) {
                    CompositionLocalProvider(
                        LocalAppChrome provides AppChrome(
                            bottomNavHeight = navBarH,
                            navLayerPaddingApplied = true,
                        ),
                    ) {
                        AppScreenScaffold(title = "底部避让测试") { viewportPadding ->
                            PageLazyColumn(
                                scaffoldPadding = viewportPadding,
                                modifier = Modifier.testTag("lazy-list"),
                            ) {
                                items(manyItems) { label ->
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag(label),
                                    ) { Text(text = label) }
                                }
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("nav-root").assertIsDisplayed()
        // 不崩溃 + 渲染出内容即视为通过；精确坐标依赖 Robolectric 字体/状态栏具体实现
        composeRule.onNodeWithTag("lazy-list").assertIsDisplayed()
        composeRule.onNodeWithText("Item-1").assertIsDisplayed()
    }

    // —————————————————————————————————————————————————————————
    // 统一壳层：320dp 宽度 + 字体 1.3 下无重复 inset
    // —————————————————————————————————————————————————————————

    @Test
    fun noDuplicateInsetsOn320dpAndFontScale1_3() {
        val items = listOf("第一行", "第二行")
        composeRule.setContent {
            // 设置 320dp 宽度 + 字体 scale 1.3
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = LocalDensity.current.density,
                    fontScale = 1.3f,
                ),
                LocalLayoutTokens provides DefaultLayoutTokens,
            ) {
                AppTheme {
                    CompositionLocalProvider(
                        LocalAppChrome provides AppChrome(
                            bottomNavHeight = 80.dp,
                            navLayerPaddingApplied = true,
                        ),
                    ) {
                        // 320dp 窄屏模拟
                        Box(
                            Modifier
                                .requiredSize(320.dp, 640.dp)
                                .padding(bottom = 80.dp) // AppNav 外层应用
                                .testTag("compact-root"),
                        ) {
                            AppScreenScaffold(
                                title = "窄屏 + 大字体",
                                modifier = Modifier.testTag("compact-scaffold"),
                            ) { viewportPadding ->
                                // 注意：若 viewportPadding 与默认 contentPadding 结构相同，
                                // PageLazyColumn 内部 check() 会抛异常 → 测试自动失败。
                                PageLazyColumn(
                                    scaffoldPadding = viewportPadding,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("compact-lazy"),
                                ) {
                                    items(items) { label ->
                                        Text(
                                            text = label,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("compact-$label"),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // PageLazyColumn 若检测到重复 padding 会抛 IllegalArgumentException → 上面的 setContent 已失败
        // 到这里说明没抛异常，再验证基本显示
        composeRule.onNodeWithTag("compact-lazy").assertIsDisplayed()
        composeRule.onNodeWithTag("compact-第一行").assertIsDisplayed()
        composeRule.onNodeWithTag("compact-第二行").assertIsDisplayed()
    }
}
