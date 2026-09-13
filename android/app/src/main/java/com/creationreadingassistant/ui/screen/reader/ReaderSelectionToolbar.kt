package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.settings.SelectionActionSettings
import com.creationreadingassistant.data.settings.SelectionActions
import com.creationreadingassistant.ui.screen.reader.sheets.HIGHLIGHT_COLORS
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.ReaderPanelSurface
import com.creationreadingassistant.ui.viewmodel.ReaderAction

internal data class SelectionToolbarActionSpec(
    val id: String,
    val label: String,
)

/**
 * 默认（未配置）的高频动作与「更多」动作。R3-X1 后工具条由
 * [com.creationreadingassistant.data.settings.SelectionActionSettings] 驱动，
 * 这两个常量只在没有任何配置时兜底，以及给测试/预览用。
 */
internal val selectionPrimaryActions = listOf(
    SelectionToolbarActionSpec("highlight", "高亮"),
    SelectionToolbarActionSpec("browser", "浏览器"),
    SelectionToolbarActionSpec("copy", "复制"),
)

internal val selectionMoreActions = listOf(
    SelectionToolbarActionSpec("dictionary", "字典"),
    SelectionToolbarActionSpec("note", "添加批注"),
    SelectionToolbarActionSpec("replace", "替换"),
    SelectionToolbarActionSpec("search", "书内搜索"),
    SelectionToolbarActionSpec("ai", "AI 解读"),
    SelectionToolbarActionSpec("inspiration", "记为灵感"),
    SelectionToolbarActionSpec("cancel", "取消选择"),
)

/** 「更多」溢出按钮本身的文案；不是可配置动作（没有它就无法进入次级动作）。 */
private const val OVERFLOW_LABEL = "更多"

/**
 * 把配置渲染成工具条实际要画的两个清单，并叠加**能力门控**（R3-X1 验收项「门控不被绕过」）。
 *
 * 门控规则：
 * - 「替换」在 TXT 分页引擎不可用时**不出现** —— 一个点了没反应的槽位比没有这个槽位更糟；
 * - 「AI 解读」未配置 Key 时**不出现** —— 否则用户点进去只会撞到「请先配置 AI」。
 *
 * 高频槽兜底：门控可能把用户配置的项全部摘掉（例如只选了「替换」+「AI 解读」），
 * 此时回退到「定义顺序里第一个可用动作」，保证选区永远有可点入口。
 */
internal fun selectionToolbarActions(
    settings: SelectionActionSettings,
    canCreateReplaceRule: Boolean,
    aiConfigured: Boolean,
): Pair<List<SelectionToolbarActionSpec>, List<SelectionToolbarActionSpec>> {
    fun allowed(id: String): Boolean = when (id) {
        "replace" -> canCreateReplaceRule
        "ai" -> aiConfigured
        else -> true
    }

    val primaryDefs = SelectionActions.effectivePrimary(settings)
        .filter { allowed(it.id) }
        .take(SelectionActions.MAX_PRIMARY_SLOTS)
        .ifEmpty { SelectionActions.pickableDefs().filter { allowed(it.id) }.take(1) }
    val moreDefs = SelectionActions.effectiveMore(settings).filter { allowed(it.id) }
    return primaryDefs.map { SelectionToolbarActionSpec(it.id, it.label) } to
        moreDefs.map { SelectionToolbarActionSpec(it.id, it.label) }
}

/** Snapshot the selection before clearing it so the search field never receives an empty query. */
internal fun readerSelectionSearchActions(selectedText: String): List<ReaderAction> = listOf(
    ReaderAction.SetSearchQuery(selectedText),
    ReaderAction.ClearSelection,
    ReaderAction.OpenSheet(ReaderSheet.SEARCH),
)

@Composable
internal fun SelectionToolbar(
    paper: ReaderPaperPalette,
    selectedText: String,
    showColorRow: Boolean,
    onToggleColor: () -> Unit,
    onPickColor: (String) -> Unit,
    canCreateReplaceRule: Boolean,
    /** R3-X1：由用户配置驱动的高频动作（按序渲染，末尾永远追加「更多」）。 */
    primaryActions: List<SelectionToolbarActionSpec> = selectionPrimaryActions,
    /** R3-X1：由用户配置驱动的溢出菜单动作。 */
    moreActions: List<SelectionToolbarActionSpec> = selectionMoreActions,
    onBrowser: () -> Unit,
    onDictionary: () -> Unit,
    onReplace: () -> Unit,
    onAiExplain: () -> Unit,
    onInspiration: () -> Unit,
    onNote: () -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var moreExpanded by remember { mutableStateOf(false) }
    val spec = LocalComponentSpec.current

    // 单一动作分发：高频槽与「更多」菜单共用同一份 id → 回调映射，
    // 避免「同一个动作换个位置就接错回调」这类只在真机上才暴露的错。
    val onActionClick: (String) -> Unit = { id ->
        when (id) {
            "highlight" -> onToggleColor()
            "browser" -> onBrowser()
            "copy" -> onCopy()
            "dictionary" -> onDictionary()
            "note" -> onNote()
            "replace" -> onReplace()
            "search" -> onSearch()
            "ai" -> onAiExplain()
            "inspiration" -> onInspiration()
            "cancel" -> onClear()
            else -> Unit
        }
    }

    // 微岛收敛：选句工具条是覆盖在阅读页之上的 reader 专属面板，统一走 ReaderPanelSurface
    //（随纸 panel 纸面 + 发丝边 + panelElevation 0dp）。原手写 22dp 圆角 / surfaceContainerHigh@0.96 /
    // 6dp 阴影已移除——阴影在翻页与选区手柄重绘期额外侵蚀帧预算；圆角改取 dockRadius 令牌。
    ReaderPanelSurface(
        shape = RoundedCornerShape(spec.dockRadius),
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            // 选中文本摘录微条
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(13.dp)
                        .clip(CircleShape)
                        .background(paper.accent),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = selectedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }

            if (showColorRow) {
                Text(
                    text = "选择高亮颜色",
                    modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    HIGHLIGHT_COLORS.forEach { colorName ->
                        val color = paper.highlightSolid(colorName)
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .semantics { contentDescription = "高亮颜色 $colorName" }
                                .clickable { onPickColor(colorName) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size(20.dp)
                                        .background(color, shape = CircleShape),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = onToggleColor,
                        // 微岛收敛：提示岛圆角统一走 hintRadius 令牌（原手写 12dp，取值不变）。
                        shape = RoundedCornerShape(spec.hintRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        modifier = Modifier.heightIn(min = 38.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = paper.accent,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("返回", style = MaterialTheme.typography.labelMedium, color = paper.accent)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    // R3-X1：高频动作由配置驱动（定义顺序渲染），每项权重相同；
                    // 末尾固定追加「更多」溢出按钮，保证次级动作永远可达。
                    primaryActions.forEach { action ->
                        val visual = selectionActionVisual(action.id)
                        SelectionCapsuleAction(
                            icon = visual.icon,
                            label = action.label,
                            pedestalColor = visual.pedestalColor,
                            iconTint = visual.iconTint,
                            onClick = { onActionClick(action.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SelectionCapsuleAction(
                            icon = Icons.Outlined.MoreHoriz,
                            label = OVERFLOW_LABEL,
                            pedestalColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { moreExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DropdownMenu(
                            expanded = moreExpanded,
                            onDismissRequest = { moreExpanded = false },
                            // 微岛收敛：溢出菜单同样落在阅读页之上——圆角取 islandRadius 令牌、
                            // 容器色改随纸 panel、发丝边走 hairline 令牌、阴影归零。
                            shape = RoundedCornerShape(spec.islandRadius),
                            containerColor = paper.panel,
                            border = BorderStroke(spec.hairlineBorderWidth, paper.outlineVariant.copy(alpha = spec.hairlineAlpha)),
                            shadowElevation = paper.panelElevation,
                        ) {
                            moreActions.forEach { action ->
                                val visual = selectionActionVisual(action.id)
                                val enabled = action.id != "replace" || canCreateReplaceRule
                                DropdownMenuItem(
                                    text = { Text(action.label, style = MaterialTheme.typography.bodyMedium) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(visual.iconTint.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                visual.icon,
                                                contentDescription = null,
                                                tint = visual.iconTint,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        moreExpanded = false
                                        onActionClick(action.id)
                                    },
                                    enabled = enabled,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 选区动作的视觉映射（R3-X1）。
 *
 * 覆盖**全部可配置动作**：高频槽现在允许任意非固定动作落位，如果这里只认
 * 「高亮 / 浏览器 / 复制」，用户把「书内搜索」放到第一屏就会看到一个灰点。
 * 未知 id（已被 [SelectionActions.sanitize] 挡在磁盘外侧）渲染成中性灰点。
 */
private data class PrimaryActionVisual(
    val icon: ImageVector,
    val pedestalColor: Color,
    val iconTint: Color,
)

private fun selectionActionVisual(id: String): PrimaryActionVisual = when (id) {
    "highlight" -> PrimaryActionVisual(
        icon = Icons.Outlined.BorderColor,
        pedestalColor = Color(0xFFF9A825).copy(alpha = 0.15f),
        iconTint = Color(0xFFF57F17),
    )

    "browser" -> PrimaryActionVisual(
        icon = Icons.Outlined.Language,
        pedestalColor = Color(0xFF00897B).copy(alpha = 0.14f),
        iconTint = Color(0xFF00796B),
    )

    "copy" -> PrimaryActionVisual(
        icon = Icons.Outlined.ContentCopy,
        pedestalColor = Color(0xFF1E88E5).copy(alpha = 0.15f),
        iconTint = Color(0xFF1565C0),
    )

    "dictionary" -> PrimaryActionVisual(
        icon = Icons.AutoMirrored.Outlined.MenuBook,
        pedestalColor = Color(0xFF6A1B9A).copy(alpha = 0.12f),
        iconTint = Color(0xFF6A1B9A),
    )

    "note" -> PrimaryActionVisual(
        icon = Icons.Outlined.EditNote,
        pedestalColor = Color(0xFF5E35B1).copy(alpha = 0.12f),
        iconTint = Color(0xFF5E35B1),
    )

    "replace" -> PrimaryActionVisual(
        icon = Icons.Outlined.FindReplace,
        pedestalColor = Color(0xFFEF6C00).copy(alpha = 0.12f),
        iconTint = Color(0xFFEF6C00),
    )

    "search" -> PrimaryActionVisual(
        icon = Icons.Outlined.Search,
        pedestalColor = Color(0xFF0288D1).copy(alpha = 0.12f),
        iconTint = Color(0xFF0288D1),
    )

    "ai" -> PrimaryActionVisual(
        icon = Icons.Outlined.AutoAwesome,
        pedestalColor = Color(0xFF00897B).copy(alpha = 0.12f),
        iconTint = Color(0xFF00897B),
    )

    "inspiration" -> PrimaryActionVisual(
        icon = Icons.Outlined.Lightbulb,
        pedestalColor = Color(0xFF7E57C2).copy(alpha = 0.12f),
        iconTint = Color(0xFF7E57C2),
    )

    "cancel" -> PrimaryActionVisual(
        icon = Icons.Outlined.Close,
        pedestalColor = Color(0xFFE53935).copy(alpha = 0.12f),
        iconTint = Color(0xFFE53935),
    )

    else -> PrimaryActionVisual(
        icon = Icons.Outlined.MoreHoriz,
        pedestalColor = Color(0x00000000),
        iconTint = Color(0xFF9E9E9E),
    )
}

@Composable
private fun SelectionCapsuleAction(
    icon: ImageVector,
    label: String,
    pedestalColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        // 微岛收敛：列表项 / 小卡圆角统一走 listItemRadius 令牌（原手写 14dp，取值不变）。
        shape = RoundedCornerShape(LocalComponentSpec.current.listItemRadius),
        color = Color.Transparent,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(pedestalColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
