package com.creationreadingassistant.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.creationreadingassistant.R
import com.creationreadingassistant.ui.screen.HomeScreen
import com.creationreadingassistant.ui.screen.InspirationScreen
import com.creationreadingassistant.ui.screen.ProfileScreen
import com.creationreadingassistant.ui.screen.QrPairingScreen
import com.creationreadingassistant.ui.screen.ReaderScreen
import com.creationreadingassistant.ui.screen.SearchScreen
import com.creationreadingassistant.ui.screen.ShelfScreen
import com.creationreadingassistant.ui.screen.StatsScreen
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

    Scaffold(
        // 外层不再消费窗口 inset。每个 Tab 自己带 Scaffold + TopAppBar，
        // 由内层去让开状态栏；外层若也让一次，顶部就会空出双份状态栏高度。
        // 底部导航栏自身会处理导航栏 inset，所以 innerPadding 的 bottom 仍然有效。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // 不设全局 topBar：五个 Tab 各自已经有自己的 TopAppBar（如 HomeScreen.kt:161），
        // 这里再放一层就会出现两个标题、两个搜索入口。
        // 改版前这里是一个裸 Row 塞着搜索图标，同样是重复的，只是没有标题所以不明显。
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val current = navBackStackEntry?.destination
                    TOP_LEVEL_ROUTES.forEach { top ->
                        NavigationBarItem(
                            // 路由可能带可选参数（如 "shelf?detailBookId={detailBookId}"），
                            // 全等比较会匹配失败，导致书架/灵感在当前页时底栏不高亮。
                            selected = current?.hierarchy?.any {
                                it.route?.substringBefore('?') == top.route
                            } == true,
                            onClick = {
                                navController.navigate(top.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(top.icon, contentDescription = null) },
                            label = { Text(stringResource(top.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(innerPadding)) {
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
                    ReaderScreen(navController, bookId = bookId, highlightId = highlightId)
                }
                composable("search") { SearchScreen(navController) }
            }
        }
    }
}
