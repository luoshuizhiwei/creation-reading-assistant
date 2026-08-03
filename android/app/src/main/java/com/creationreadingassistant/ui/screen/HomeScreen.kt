package com.creationreadingassistant.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.creationreadingassistant.ui.screen.home.HomeRoute
import com.creationreadingassistant.ui.viewmodel.BookViewModel
import com.creationreadingassistant.ui.viewmodel.HomeViewModel

/**
 * 兼容入口（≤80 行）。直接委托给 [ui/screen/home/HomeRoute.kt]，
 * 避免调用方（AppNavigation、Tab 注册处）大面积改名失败。
 *
 * 新代码请直接 import [com.creationreadingassistant.ui.screen.home.HomeRoute]。
 * 本文件内禁止再写任何 UI 或业务逻辑——全部下沉至 home/ 子包。
 */
@Composable
fun HomeScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    bookViewModel: BookViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    HomeRoute(
        navController = navController,
        modifier = modifier,
        bookViewModel = bookViewModel,
        homeViewModel = homeViewModel,
    )
}
