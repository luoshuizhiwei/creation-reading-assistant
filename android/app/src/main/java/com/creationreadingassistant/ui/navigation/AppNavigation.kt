package com.creationreadingassistant.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.creationreadingassistant.R
import com.creationreadingassistant.ui.screen.HomeScreen
import com.creationreadingassistant.ui.screen.InspirationScreen
import com.creationreadingassistant.ui.screen.profile.ProfileRoute
import com.creationreadingassistant.ui.screen.profile.profileSubPageFromRoute
import com.creationreadingassistant.ui.screen.homearchive.HomeCompletedRoute
import com.creationreadingassistant.ui.screen.homearchive.HomeInspirationDetailRoute
import com.creationreadingassistant.ui.screen.homearchive.HomeInspirationsRoute
import com.creationreadingassistant.ui.screen.readinghistory.MyReadingRoute
import com.creationreadingassistant.ui.screen.profile.QrPairingScreen
import com.creationreadingassistant.ui.screen.reader.ReaderRoute
import com.creationreadingassistant.ui.screen.search.SearchScreen
import com.creationreadingassistant.ui.screen.ShelfScreen
import com.creationreadingassistant.ui.screen.shelf.SHELF_IMPORT_ROUTE
import com.creationreadingassistant.ui.screen.shelf.SHELF_ORGANIZER_ROUTE
import com.creationreadingassistant.ui.screen.shelf.SHELF_SEARCH_ROUTE
import com.creationreadingassistant.ui.screen.shelf.ShelfImportRoute
import com.creationreadingassistant.ui.screen.shelf.ShelfOrganizerRoute
import com.creationreadingassistant.ui.screen.shelf.ShelfSearchRoute
import com.creationreadingassistant.ui.screen.shelf.ShelfSelectionRoute
import com.creationreadingassistant.ui.screen.StatsScreen
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.LocalVisualStyle
import com.creationreadingassistant.ui.theme.LocalVisualStyleState
import com.creationreadingassistant.ui.theme.VisualStyle
import com.creationreadingassistant.ui.theme.VisualStyleProvider
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.viewmodel.ProfileViewModel
import com.creationreadingassistant.ui.viewmodel.ShelfViewModel

/**
 * 应用级外层"铬层"状态：由 [AppNavigation] 提供，页面壳层 [AppScreenScaffold] 读取。
 *
 * 职责边界（勿重复消费同一 inset）：
 * 1. AppNavigation 外层 Scaffold：**不**消费任何系统 WindowInsets（status/nav/system），
 *    只管理自己的应用底部导航栏。
 * 2. AppNavigation 把 `navLayerPadding`（= 仅应用底部导航栏高度 + 0/start/end）作为
 *    Modifier.padding 应用给 NavHost 容器——给尚未迁移到 AppScreenScaffold 的旧页面兜底。
 * 3. AppScreenScaffold（统一页壳）：消费系统 `WindowInsets.systemBars` + 自己的 TopAppBar，
 *    并通过 [LocalAppChrome] 感知外层 `navLayerPadding` 是否已在父容器应用过 → 避免重复叠加。
 *
 * @param bottomNavHeight 当前显示的应用底部导航栏高度；隐藏时为 0
 * @param navLayerPaddingApplied true = 父容器已把 bottomNavHeight 作为 padding 应用于 NavHost 根
 */
data class AppChrome(
    val bottomNavHeight: Dp = 0.dp,
    val navLayerPaddingApplied: Boolean = true,
)

val LocalAppChrome = staticCompositionLocalOf { AppChrome() }

/** 把两个 PaddingValues 按各方向相加。纯函数工具，避免同一数值被重复叠加。 */
internal fun PaddingValues.plus(other: PaddingValues, layoutDirection: androidx.compose.ui.unit.LayoutDirection): PaddingValues {
    val start = calculateStartPadding(layoutDirection) + other.calculateStartPadding(layoutDirection)
    val top = calculateTopPadding() + other.calculateTopPadding()
    val end = calculateEndPadding(layoutDirection) + other.calculateEndPadding(layoutDirection)
    val bottom = calculateBottomPadding() + other.calculateBottomPadding()
    return PaddingValues(start = start, top = top, end = end, bottom = bottom)
}

/** 底部 Tab 的五个顶层路由，与原版 mobile/ 一致：首页/书架/灵感/统计/我的。 */
sealed class TopLevelRoute(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    object Home : TopLevelRoute("home", R.string.nav_home, Icons.Filled.Home)
    object Shelf : TopLevelRoute("shelf", R.string.nav_shelf, Icons.Filled.MenuBook)
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
    val routeBase = currentRoute.substringBefore('?')
    // 只在五个顶层目的地显示底栏；home/*、profile/* 等真实二级路由全部隐藏。
    val showBottomBar = TOP_LEVEL_ROUTES.any { it.route == routeBase }

    BackHandler(enabled = showBottomBar && routeBase != TopLevelRoute.Home.route) {
        navController.navigate(TopLevelRoute.Home.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                inclusive = false
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

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
                // 外层不消费系统窗口 inset。每个页面自己的 AppScreenScaffold 负责 status/nav bars；
                // 外层只管理应用底部导航栏（NavigationBar composable）的 innerPadding。
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showBottomBar) {
                        // 系统「减少动态效果」：开启时瞬切
                        val reducedMotion = rememberReducedMotion()
                        Column {
                            // 底部栏顶部一条 1dp 发丝线，让底栏与页面底分界分明（§2.2 / §3.2）
                            HorizontalDivider(
                                thickness = spec.dividerThickness,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            Surface(color = MaterialTheme.colorScheme.surface) {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp),
                                    ) {
                                        BottomBarItems(
                                            navBackStackEntry = navBackStackEntry,
                                            navController = navController,
                                        )
                                    }
                                    Spacer(
                                        Modifier
                                            .fillMaxWidth()
                                            .windowInsetsBottomHeight(WindowInsets.navigationBars),
                                    )
                                }
                            }
                        }
                    }
                },
            ) { innerPadding ->
                // innerPadding：Scaffold 仅根据 bottomBar 生成 bottom 非零，top/start/end=0。
                // 直接作为 NavHost 的 padding，让尚未迁移的页面不被应用底部栏遮挡。
                // 同时通过 LocalAppChrome 把外层的 bottomNavHeight + "已应用"标志暴露给 AppScreenScaffold，
                // 避免迁移后的壳层再次叠加同一 padding。
                val bottomNavHeight = innerPadding.calculateBottomPadding()
                val chrome = AppChrome(
                    bottomNavHeight = bottomNavHeight,
                    navLayerPaddingApplied = true,
                )
                CompositionLocalProvider(LocalAppChrome provides chrome) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues = innerPadding),
                    ) {
                        AppNavHost(
                            navController = navController,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelRoute.Home.route,
    ) {
        composable(TopLevelRoute.Home.route) { HomeScreen(navController) }
        navigation(
            route = "shelf-graph",
            startDestination = TopLevelRoute.Shelf.route,
        ) {
            composable(TopLevelRoute.Shelf.route) { backStackEntry ->
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry("shelf-graph") }
                val shelfVm: ShelfViewModel = hiltViewModel(graphEntry)
                ShelfScreen(navController = navController, viewModel = shelfVm)
            }
            composable(
                route = "shelf/detail/{detailBookId}",
                arguments = listOf(navArgument("detailBookId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry("shelf-graph") }
                val shelfVm: ShelfViewModel = hiltViewModel(graphEntry)
                ShelfScreen(
                    navController = navController,
                    viewModel = shelfVm,
                    initialDetailBookId = backStackEntry.arguments?.getString("detailBookId"),
                )
            }
            composable(SHELF_ORGANIZER_ROUTE) { backStackEntry ->
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry("shelf-graph") }
                ShelfOrganizerRoute(navController, hiltViewModel(graphEntry))
            }
            composable("shelf/organizer/select/{kind}") { backStackEntry ->
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry("shelf-graph") }
                ShelfSelectionRoute(
                    kind = backStackEntry.arguments?.getString("kind").orEmpty(),
                    navController = navController,
                    viewModel = hiltViewModel(graphEntry),
                )
            }
            composable(SHELF_SEARCH_ROUTE) { backStackEntry ->
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry("shelf-graph") }
                ShelfSearchRoute(navController, hiltViewModel(graphEntry))
            }
            composable(SHELF_IMPORT_ROUTE) { backStackEntry ->
                val graphEntry = remember(backStackEntry) { navController.getBackStackEntry("shelf-graph") }
                ShelfImportRoute(navController, hiltViewModel(graphEntry))
            }
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
        composable(TopLevelRoute.Profile.route) {
            ProfileRoute(navController = navController)
        }
        composable(
            route = "profile/{subPage}",
            arguments = listOf(navArgument("subPage") { type = NavType.StringType }),
        ) { backStackEntry ->
            val page = profileSubPageFromRoute(backStackEntry.arguments?.getString("subPage"))
            if (page != null) ProfileRoute(navController = navController, subPage = page)
        }
        composable("my-reading") { MyReadingRoute(navController) }
        composable("home/inspirations") { HomeInspirationsRoute(navController) }
        composable(
            route = "home/inspiration/{inspirationId}",
            arguments = listOf(navArgument("inspirationId") { type = NavType.StringType }),
        ) { backStackEntry ->
            HomeInspirationDetailRoute(
                navController = navController,
                inspirationId = backStackEntry.arguments?.getString("inspirationId").orEmpty(),
            )
        }
        composable("home/completed") { HomeCompletedRoute(navController) }
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
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val targetScale = if (pressed) 0.96f else 1f
        val animatedScale by animateFloatAsState(
            targetValue = targetScale,
            animationSpec = tween(if (reducedMotion) 0 else 90),
            label = "bottom-tab-press",
        )
        val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    interactionSource = interactionSource,
                    indication = null,
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
                )
                .scale(animatedScale)
                .alpha(if (pressed) 0.72f else 1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Icon(
                imageVector = top.icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = tint,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(top.labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                maxLines = 1,
            )
        }
    }
}
