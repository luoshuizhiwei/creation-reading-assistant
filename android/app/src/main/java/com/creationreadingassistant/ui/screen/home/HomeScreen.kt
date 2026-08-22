package com.creationreadingassistant.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.R
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.home.components.HomeCompletedSection
import com.creationreadingassistant.ui.screen.home.components.HomeContinueSection
import com.creationreadingassistant.ui.screen.home.components.HomeInspirationItem
import com.creationreadingassistant.ui.screen.home.components.HomeMetricsSection
import com.creationreadingassistant.ui.screen.home.components.HomeReadingArchiveSection
import com.creationreadingassistant.ui.theme.ListSkeleton
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.components.SectionHeader

/**
 * **纯 UI 层 HomeScreen**——只负责渲染 + 把点击翻译成 HomeAction。
 *
 * ### 验收保证
 * 本文件内**禁止**出现：
 * - `hiltViewModel()` / `NavHostController` / `collectAsStateWithLifecycle` / 任何 DAO
 * - 任何业务计算（排序、过滤、分数合成…… Route 或 ViewModel 做）
 * - 嵌套 Scaffold（外层只应存在 AppScreenScaffold 提供的唯一壳）
 * - 嵌套页面级 Card（Section 组件自己的卡片除外）
 *
 * ### 结构
 * 1 个 AppScreenScaffold（统一页壳） + 1 个 PageLazyColumn（页面唯一懒加载容器）。
 * PageLazyColumn 内顺序是：骨架占位 → 累计阅读 → 继续阅读 → 统计摘要 → 最近灵感 → 已阅读完成。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalLayoutTokens.current
    val reducedMotion = rememberReducedMotion()

    AppScreenScaffold(
        modifier = modifier.testTag("home-screen"),
        title = stringResource(R.string.home_title),
        actions = {
            IconButton(
                onClick = { onAction(HomeAction.OpenSearch) },
                modifier = Modifier.testTag("home-search-btn"),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.home_open_search))
            }
        },
        // 桥接 Snackbar：Pure Screen 本地保持 null（= 空占位，不持有任何状态）；
        // Route 层通过 LocalHomeSnackbar 注入真实 SnackbarHostState。
        // 此实现不违反 "HomeScreen 只接收 HomeUiState + onAction"——参数签名保持不变。
        snackbarHost = {
            val hostState = com.creationreadingassistant.ui.screen.home.LocalHomeSnackbarHolder.current
            if (hostState != null) {
                androidx.compose.material3.SnackbarHost(hostState = hostState)
            }
        },
    ) { viewportPadding ->
        PageLazyColumn(
            scaffoldPadding = viewportPadding,
            modifier = Modifier
                .fillMaxSize()
                .testTag("home-lazy-column"),
            verticalArrangement = Arrangement.spacedBy(layout.contentGap),
        ) {
            // —— 首屏骨架（Route 控制 showSkeleton）——
            if (state.showSkeleton) {
                item(key = "skeleton") {
                    ListSkeleton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home-skeleton"),
                        count = 5,
                        reducedMotion = reducedMotion,
                    )
                }
            } else {
                item(key = "reading-archive") {
                    HomeReadingArchiveSection(
                        totalReadingMs = state.totalReadingMs,
                        totalReadBooksCount = state.totalReadBooksCount,
                        onClick = { onAction(HomeAction.OpenMyReading) },
                    )
                }

                // —— 2. 继续阅读 block（Section + 自己的 LazyRow）——
                item(key = "continue-section") {
                    HomeContinueSection(
                        continueBooks = state.continueBooks,
                        progressById = state.progressById,
                        onOpenBook = { onAction(HomeAction.OpenBook(it)) },
                        onOpenContinueSheet = { onAction(HomeAction.OpenContinueSheet) },
                        onEmptyNavigateShelf = { onAction(HomeAction.NavigateToShelf) },
                    )
                }

                // —— 3. 本周概览指标组——
                item(key = "metrics-section") {
                    HomeMetricsSection(
                        thisWeekNew = state.thisWeekNew,
                        readingCount = state.readingCount,
                        completedCount = state.completedCount,
                        todayReadingMs = state.todayReadingMs,
                        dailyGoalMinutes = state.dailyGoalMinutes,
                    )
                }

                // —— 4. 最近灵感（直接 items 嵌入，不嵌套第二个 LazyColumn）——
                item(key = "inspiration-header") {
                    InspirationHeaderRow(
                        onSeeAll = { onAction(HomeAction.OpenRecentInspirations) },
                        isEmpty = state.recentInspirations.isEmpty(),
                    )
                }
                if (state.recentInspirations.isEmpty()) {
                    item(key = "inspiration-empty") {
                        EmptyInspirationHint()
                    }
                } else {
                    items(
                        state.recentInspirations,
                        key = { "home-insp-${it.id}" },
                    ) { insp ->
                        HomeInspirationItem(
                            inspiration = insp,
                            onClick = { onAction(HomeAction.NavigateToInspirationDetail(insp.id)) },
                            enterDelayMs = 180,
                        )
                    }
                }

                // —— 5. 已阅读完成 block（Section + 自己的 LazyRow）——
                item(key = "completed-section") {
                    HomeCompletedSection(
                        completedBooks = state.completedBooks,
                        progressById = state.progressById,
                        onOpenBook = { onAction(HomeAction.OpenBook(it)) },
                        onSeeAll = { onAction(HomeAction.OpenCompletedBooks) },
                    )
                }

                // 底部再多 1 个小 gap，避免最后一个 completed section 的 card 紧贴 App 导航栏
                item(key = "home-bottom-spacer") {
                    Spacer(Modifier.height(layout.pageVertical))
                }
            }
        }
    }
}

@Composable
private fun InspirationHeaderRow(onSeeAll: () -> Unit, isEmpty: Boolean) {
    val reducedMotion = rememberReducedMotion()
    SectionHeader(
        title = stringResource(R.string.home_recent_inspiration),
        modifier = Modifier
            .animateEnter(180, reducedMotion)
            .testTag("home-insp-header"),
        action = {
            TextButton(
                onClick = onSeeAll,
                modifier = Modifier.testTag("home-insp-see-all"),
            ) {
                Text(stringResource(R.string.home_see_all))
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.height(16.dp),
                )
            }
        },
    )
}

@Composable
private fun EmptyInspirationHint() {
    val reducedMotion = rememberReducedMotion()
    com.creationreadingassistant.ui.components.SectionEmptyHint(
        text = "还没有灵感，阅读时选中文字即可保存为灵感。",
        modifier = Modifier
            .animateEnter(180, reducedMotion)
            .testTag("home-insp-empty"),
    )
}
