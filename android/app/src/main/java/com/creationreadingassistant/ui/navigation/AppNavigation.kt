package com.creationreadingassistant.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import com.creationreadingassistant.ui.components.BookmarkIndicator
import com.creationreadingassistant.ui.components.BookmarkIndicatorWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import com.creationreadingassistant.R
import com.creationreadingassistant.ui.screen.HomeScreen
import com.creationreadingassistant.ui.screen.InspirationScreen
import com.creationreadingassistant.ui.screen.ProfileScreen
import com.creationreadingassistant.ui.screen.QrPairingScreen
import com.creationreadingassistant.ui.screen.ReaderRoute
import com.creationreadingassistant.ui.screen.SearchScreen
import com.creationreadingassistant.ui.screen.ShelfScreen
import com.creationreadingassistant.ui.screen.StatsScreen
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.LocalVisualStyle
import com.creationreadingassistant.ui.theme.LocalVisualStyleState
import com.creationreadingassistant.ui.theme.VisualStyle
import com.creationreadingassistant.ui.theme.VisualStyleProvider
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.theme.rememberHaptic
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.creationreadingassistant.ui.viewmodel.ProfileViewModel

/** 底部 Tab 的五个顶层路由，与原版 mobile/ 一致：首页/书架/灵感/统计/我的。 */
sealed class TopLevelRoute(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    object Home : TopLevelRoute("home", R.string.nav_home, Icons.Filled.Home)
    object Shelf : TopLevelRoute("shelf", R.string.nav_shelf, Icons.AutoMirrored.Filled.MenuBook)
    object Inspiration : TopLevelRoute("inspiration", R.string.nav_inspiration, Icons.Filled.Lightbulb)
    object Stats : TopLevelRoute("stats", R.string.nav_stats, Icons.Filled.BarChart)
    object Profile : TopLevelRoute("profile", R.string.nav_profile, Icons.Filled.Person)
}

val TOP_LEVEL_ROUTES = listOf(
    TopLevelRoute.Home,
    TopLevelRoute.Shelf,
    TopLevelRoute.Inspiration,
    TopLevelRoute.Stats,
    TopLevelRoute.Profile,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: TopLevelRoute.Home.route
    // 阅读器（reader/{bookId}）为全屏覆盖层，不显示底部栏
    val showBottomBar = TOP_LEVEL_ROUTES.any { currentRoute.startsWith(it.route) }

    // 全局视觉样式状态：由 AppNavigation 创建并提供，所有页面（含首页切换按钮）共享同一状态，
    // 实现「在任意页面点切换，全局统一变主题」。
    val visualStyleState = remember { mutableStateOf(VisualStyle.DEFAULT) }

    CompositionLocalProvider(LocalVisualStyleState provides visualStyleState) {
        VisualStyleProvider(style = visualStyleState.value) {
            val style = LocalVisualStyle.current
            val spec = LocalComponentSpec.current
            // Apple 风格下强化底部栏选中态（默认 indicator 在浅底上几乎不可见），其余主题走常规。
            // 注意：毛玻璃浮层方案已移除——Apple 与非 Apple 共用同一套实心 NavigationBar，
            // 区别由各自 colorScheme（冷灰底/白卡/系统蓝）自然体现，层次分明、不糊进背景。

            Scaffold(
                // 外层不再消费窗口 inset。每个 Tab 自己带 Scaffold + TopAppBar，
                // 由内层去让开状态栏；外层若也让一次，顶部就会空出双份状态栏高度。
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showBottomBar) {
                        // 当前 active 项索引（仅顶层路由会显示底栏；reader 等全屏路由不显示）
                        val activeIndex = TOP_LEVEL_ROUTES.indexOfFirst { currentRoute.startsWith(it.route) }
                        // 系统「减少动态效果」：开启时书签切换退化为瞬切
                        val reducedMotion = rememberReducedMotion()
                        Column {
                            // 底部栏顶部一条 1dp 发丝线，让底栏与页面底分界分明（§2.2 / §3.2）
                            HorizontalDivider(
                                thickness = spec.dividerThickness,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            Box {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.background,
                                ) {
                                    BottomBarItems(
                                        navBackStackEntry = navBackStackEntry,
                                        navController = navController,
                                    )
                                }
                                // 书签旗标（§6.1）：单实例，锚定顶 Divider、向底栏内部垂下；
                                // 跨标签切换时水平滑动过渡（reduced-motion 时瞬切）。
                                // 不悬浮、不探出底栏上沿，与 24dp 导航图标垂直间隔 ≥4dp、不替代/遮挡。
                                if (activeIndex >= 0) {
                                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                                        val itemWidth = maxWidth / TOP_LEVEL_ROUTES.size
                                        val targetX = itemWidth * activeIndex + (itemWidth - BookmarkIndicatorWidth) / 2
                                        val animatedX by animateDpAsState(
                                            targetValue = targetX,
                                            animationSpec = if (reducedMotion) snap() else tween(durationMillis = 260),
                                            label = "bookmarkSlide",
                                        )
                                        Box(Modifier.fillMaxWidth().offset(x = animatedX)) {
                                            BookmarkIndicator()
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Box(Modifier.fillMaxSize()) {
                    val contentTop = innerPadding.calculateTopPadding()
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(top = contentTop, bottom = innerPadding.calculateBottomPadding()),
                    ) {
                        AppNavHost(navController = navController)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = TopLevelRoute.Home.route,
    ) {
        composable(TopLevelRoute.Home.route) { HomeScreen(navController) }
        composable(
            route = "${TopLevelRoute.Shelf.route}?detailBookId={detailBookId}",
            arguments = listOf(
                navArgument("detailBookId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val detailBookId = backStackEntry.arguments?.getString("detailBookId")
            ShelfScreen(navController = navController, initialDetailBookId = detailBookId)
        }
        composable(
            route = "${TopLevelRoute.Inspiration.route}?inspId={inspId}",
            arguments = listOf(
                navArgument("inspId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val inspId = backStackEntry.arguments?.getString("inspId")
            InspirationScreen(initialSelectedId = inspId)
        }
        composable(TopLevelRoute.Stats.route) { StatsScreen() }
        composable(TopLevelRoute.Profile.route) { ProfileScreen(navController = navController) }
        composable("pairing") {
            val profileVm: ProfileViewModel = hiltViewModel()
            QrPairingScreen(
                onScanned = { raw ->
                    profileVm.startPairing(raw)
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            route = "reader/{bookId}?highlightId={highlightId}",
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("highlightId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId")
            val highlightId = backStackEntry.arguments?.getString("highlightId")
            ReaderRoute(navController, bookId = bookId, highlightId = highlightId)
        }
        composable("search") { SearchScreen(navController) }
    }
}

/** 底栏五个 Tab 的共享内容（Apple 下使用自绘 1.5px 线性图标 + 强化选中态）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowScope.BottomBarItems(
    navBackStackEntry: NavBackStackEntry?,
    navController: NavHostController,
) {
    val current = navBackStackEntry?.destination
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)

    TOP_LEVEL_ROUTES.forEach { top ->
        val selected = current?.hierarchy?.any {
            it.route?.substringBefore('?') == top.route
        } == true
        NavigationBarItem(
            selected = selected,
            onClick = {
                if (!selected) haptic(HapticFeedbackType.TextHandleMove)
                navController.navigate(top.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(imageVector = top.icon, contentDescription = null) },
            label = { Text(stringResource(top.labelRes)) },
            colors = NavigationBarItemDefaults.colors(),
        )
    }
}
