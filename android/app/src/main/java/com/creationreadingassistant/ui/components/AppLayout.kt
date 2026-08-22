package com.creationreadingassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.navigation.LocalAppChrome
import com.creationreadingassistant.ui.theme.DisplayFontFamily
import com.creationreadingassistant.ui.theme.pageEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion

// —————————————————————————————————————————————————————————————————
// 工具：PaddingValues 相加 / 判等 （纯函数，防重复叠加）
// —————————————————————————————————————————————————————————————————

private fun PaddingValues.plusTokensLocal(other: PaddingValues, layoutDirection: LayoutDirection): PaddingValues {
    val start = calculateStartPadding(layoutDirection) + other.calculateStartPadding(layoutDirection)
    val top = calculateTopPadding() + other.calculateTopPadding()
    val end = calculateEndPadding(layoutDirection) + other.calculateEndPadding(layoutDirection)
    val bottom = calculateBottomPadding() + other.calculateBottomPadding()
    return PaddingValues(start = start, top = top, end = end, bottom = bottom)
}

private fun PaddingValues.structurallyEquals(other: PaddingValues, layoutDirection: LayoutDirection): Boolean {
    return calculateStartPadding(layoutDirection) == other.calculateStartPadding(layoutDirection) &&
        calculateTopPadding() == other.calculateTopPadding() &&
        calculateEndPadding(layoutDirection) == other.calculateEndPadding(layoutDirection) &&
        calculateBottomPadding() == other.calculateBottomPadding()
}

/**
 * **统一页面壳层（唯一推荐）**。
 *
 * ## 职责边界（任何情况下不得越界消费同一 inset）
 *
 * | 层 | 消费内容 | 来源 |
 * |---|---|---|
 * | AppNavigation 外层 Scaffold | 仅应用底部导航栏高度 | bottomBar slot → `innerPadding.calculateBottom()`，已作为 padding 应用于 NavHost 根，不再重复！ |
 * | **AppScreenScaffold** | 状态栏 + 系统导航栏 + TopAppBar（含 supportingContent） | `WindowInsets.systemBars` + Scaffold topBar slot |
 * | 页面内容自身 | 页面级水平/垂直内容边距、卡片内部 padding | LayoutTokens / 调用方显式传入 |
 *
 * ## 内容 lambda 参数
 *
 * `content(viewportPadding: PaddingValues)` 为**视口避让**：你需要保证内容的可见区域不会被
 * 状态栏 / 顶部栏 / 系统导航栏遮挡。有两种正确姿势：
 *
 * 1. **懒加载容器（LazyColumn/LazyVerticalGrid）**：传给 [PageLazyColumn.viewportPadding]，
 *    让 LazyList 自行把 viewportPadding 算入 Modifier（不是 contentPadding！）。
 * 2. **非懒加载（Column/Box 等）**：`Modifier.padding(viewportPadding)` 直接应用到最外层容器。
 *
 * **切勿**把 viewportPadding 再叠加到 `LazyColumn.contentPadding` 或 `Modifier.padding` 第二次。
 *
 * @param title 顶部栏标题
 * @param topBarSupportingContent 附在 TopAppBar 下方的补充区域（例如搜索框/分段控件）；会计入 viewportPadding。
 * @param content 接收 viewport 避让用 PaddingValues；不得重复消费外层 `LocalAppChrome.navLayerPadding`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    topBarSupportingContent: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val chrome = LocalAppChrome.current

    Scaffold(
        modifier = modifier,
        snackbarHost = snackbarHost,
        // 明确消费状态栏 + 系统导航栏（系统手势/3键栏）。
        // 应用底部导航栏高度由 AppNavigation 外层已加到 NavHost 根；当 navLayerPaddingApplied=true 时此处不再重复。
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            Column {
                AppTopBar(
                    title = title,
                    navigationIcon = navigationIcon,
                    titleContent = titleContent,
                    actions = actions,
                )
                topBarSupportingContent?.invoke()
            }
        },
        content = { scaffoldInset ->
            // 最终传给页面的 viewportPadding：
            //   scaffoldInset = statusBars + TopAppBar + supportingContent + system navigationBars
            // 注意：应用内底部导航栏高度（LocalAppChrome.bottomNavHeight）**已被父容器应用**，
            //       这里禁止再次叠加（= 不与 AppNavigation 的职责重叠）。
            val viewportPadding = if (chrome.navLayerPaddingApplied) {
                scaffoldInset
            } else {
                val navOnly = PaddingValues(bottom = chrome.bottomNavHeight)
                scaffoldInset.plusTokensLocal(navOnly, layoutDirection)
            }
            // 整页进入淡入：页面切换的统一过渡（只 fade 不位移，区块级 stagger 另行叠加）。
            val reducedMotion = rememberReducedMotion()
            Box(Modifier.fillMaxSize().pageEnter(reducedMotion)) {
                content(viewportPadding)
            }
        },
    )
}

/**
 * 页面级 LazyColumn 快捷封装。
 *
 * ## 参数语义（**切勿传同一 PaddingValues 给下面两个参数**）
 *
 * - [scaffoldPadding]：**视口避让 padding（= viewport 偏移量）**。来自 [AppScreenScaffold] 传出的 PaddingValues。
 *   应用于 `Modifier.padding()` → 让整个 LazyColumn 视口退后到状态栏/顶部栏/系统导航栏之后。
 *   语义同 `viewportPadding`；历史遗留名，新代码优先按下面"防重复"规则理解。
 *
 * - [contentPadding]：**LazyList 内部内容边距**。等价于 `LazyColumn.contentPadding` 参数。
 *   影响首尾 item 的偏移量、滚动条范围、快速滚动到最后一项后是否能继续向下拖一点。
 *   默认 = `LayoutTokens.pageHorizontal / pageVertical`，调用方显式传入时会覆盖默认值。
 *
 * **防重复**：[scaffoldPadding] 只应用于 Modifier（整个 LazyColumn 容器的偏移）；
 * [contentPadding] 只应用于 LazyList 的首尾 item 间距。**严禁把同一 PaddingValues 同时传两处**。
 *
 * 如不确定该传哪个：顶部栏/状态栏挡住了列表首项 → 调 [scaffoldPadding]；
 * 想让列表第一项距离顶部栏再多 12dp、或最后一项距离底部再多 12dp → 调 [contentPadding]。
 *
 * @throws IllegalArgumentException 当两个参数结构完全相等（疑似重复叠加）时抛出。
 */
@Composable
fun PageLazyColumn(
    modifier: Modifier = Modifier,
    scaffoldPadding: PaddingValues = PaddingValues(0.dp),
    contentPadding: PaddingValues? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(LocalLayoutTokens.current.contentGap),
    content: LazyListScope.() -> Unit,
) {
    val layout = LocalLayoutTokens.current
    val layoutDirection = LocalLayoutDirection.current
    val actualContentPadding = contentPadding ?: PaddingValues(
        horizontal = layout.pageHorizontal,
        vertical = layout.pageVertical,
    )

    // 防呆：怀疑调用方把同一个 PV 同时传给 viewport 与 content → 双重 padding。
    val duplicated = scaffoldPadding.structurallyEquals(actualContentPadding, layoutDirection)
    check(!duplicated) {
        "PageLazyColumn: scaffoldPadding and contentPadding resolve to the same values " +
            "(${describe(scaffoldPadding, layoutDirection)}); this likely means the same " +
            "PaddingValues was applied to both Modifier.padding (viewport) and " +
            "LazyColumn.contentPadding and would double every inset. " +
            "Use scaffoldPadding for system/top-bar avoidance and contentPadding " +
            "(default) for page-level content margins."
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues = scaffoldPadding),
        contentPadding = actualContentPadding,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

private fun describe(pv: PaddingValues, ld: LayoutDirection): String {
    return "start=${pv.calculateStartPadding(ld)}, top=${pv.calculateTopPadding()}, " +
        "end=${pv.calculateEndPadding(ld)}, bottom=${pv.calculateBottomPadding()}"
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LocalLayoutTokens.current.relatedGap),
    ) {
        Text(
            text = title,
            // 区块标题用展示衬线（书卷气），与正文黑体形成层次；字号沿用 headlineSmall 节奏。
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFontFamily),
            modifier = Modifier.weight(1f),
        )
        action?.invoke(this)
    }
}

@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 0.dp,
        content = content,
    )
}

@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    titleContent: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    compact: Boolean = false,
) {
    val tokens = LocalLayoutTokens.current
    val titleStyle = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge

    // 不使用 Material3 TopAppBar 的内部标题槽：它会把自定义中文 lineHeight
    // 重新限制在单行栏的裁剪层中，高状态栏设备上仍可能切掉字形上沿。
    // 这里明确拆开「状态栏安全区」和「64dp 标题内容区」，并给文字保留垂直呼吸空间。
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = tokens.topBarHeight)
            .padding(
                start = if (navigationIcon == null) 16.dp else 4.dp,
                end = 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigationIcon?.let { icon ->
            Box(
                modifier = Modifier.widthIn(min = tokens.minimumTouchTarget),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            titleContent?.invoke() ?: Text(
                text = title,
                style = titleStyle.copy(fontFamily = DisplayFontFamily),
                maxLines = 1,
            )
        }
        actions()
    }
}
