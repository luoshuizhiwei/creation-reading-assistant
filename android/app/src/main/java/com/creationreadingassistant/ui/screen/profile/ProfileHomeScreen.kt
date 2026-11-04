package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.settings.SelectionActionSettings
import com.creationreadingassistant.data.settings.SelectionActions
import com.creationreadingassistant.ui.components.CompactItemDivider
import com.creationreadingassistant.ui.components.CompactSettingRow
import com.creationreadingassistant.ui.components.CompactSettingsGroup
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SettingRow
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberCountUp
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.util.LocalDurationUnit
import com.creationreadingassistant.ui.util.durationUnitToggle
import com.creationreadingassistant.ui.util.formatDuration

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileHomeScreen(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
    searchActive: Boolean = false,
    searchQuery: String = "",
    onQueryChange: (String) -> Unit = {},
    onCloseSearch: () -> Unit = {},
) {
    val reducedMotion = rememberReducedMotion()
    val summary = state.homeSummary
    val results = remember(searchQuery) { SettingsSearchIndex.search(searchQuery) }

    // 阅读时长按当前单位（天/小时/分钟）呈现，供顶部档案卡的统计行使用。
    // S15-F1/F2：改用公共 formatter，不再本地重写 when 分支（本地版缺 ms<=0 保护，
    // 且与公共策略分叉）；长按能力由 ArchiveStat 的 valueModifier 提供。
    val durationUnit = LocalDurationUnit.current
    val durationText = formatDuration(summary.totalDurationMs, durationUnit)

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (searchActive) {
            // 搜索模式下：主屏作为纯净的搜索结果承载容器
            if (searchQuery.isNotBlank()) {
                item(key = "settings-search-results") {
                    SettingsSearchResults(
                        results = results,
                        emptyQuery = searchQuery,
                        onPick = { entry ->
                            onCloseSearch()
                            onAction(ProfileAction.OpenSettingsEntry(entry.page, entry.anchor))
                        },
                    )
                }
            } else {
                // 搜索输入为空时：展示快捷热搜词胶囊微岛
                item(key = "settings-search-suggestions") {
                    CompactSettingsGroup(title = "快捷搜索") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            val suggestions = listOf("字号", "行距", "阅读纸张", "护眼滤镜", "翻页效果", "语速", "听书", "WebDAV", "存储管理")
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                suggestions.forEach { tag ->
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                        modifier = Modifier.clickable { onQueryChange(tag) },
                                    ) {
                                        Text(
                                            tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // 阅读档案：身份行 + 一行三格统计，整卡点击进入「我的阅读」。
            // 统计只在此处呈现一次，下方「我的阅读」行不再重复。
            item(key = "profile-archive") {
                CompactSettingsGroup(
                    modifier = Modifier.animateEnter(reducedMotion = reducedMotion),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAction(ProfileAction.OpenSubPage(ProfileSubPage.READING)) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "创作阅读助手",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "本地优先 · 点击查看阅读档案",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    CompactItemDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ArchiveStat(durationText, "阅读时长", valueModifier = Modifier.durationUnitToggle())
                        ArchiveStat("${summary.completedBookCount} 本", "读完")
                        ArchiveStat("${summary.inspirationCount} 条", "灵感")
                    }
                }
            }

            // 快捷导航磁贴已移除（用户 2026-09-18 反馈首屏重复）：「阅读设置」与下方
            // 「阅读与外观」组首项重复；「灵感工坊」是创作工具不是设置，入口在灵感 Tab。
            // 首屏因此直接露出分组列表，最常用的前几项无需滚动。

        // 阅读与外观
        item(key = "reading-appearance") {
            MenuGroup(title = "阅读与外观") {
                MenuItem(Icons.Outlined.TextFields, "阅读设置", "字号、行距、主题、翻页模式", iconTint = Color(0xFF6750A4), onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.READER)) })
                MenuItem(
                    Icons.Outlined.TouchApp,
                    "选区与查词",
                    selectionSummary(state.selectionActions, state.dictionaries.size),
                    iconTint = Color(0xFF00897B),
                    onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.SELECTION)) },
                )
                MenuItem(Icons.Outlined.DarkMode, "应用外观", state.appThemeLabel, iconTint = Color(0xFF00639B), onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.APPEARANCE)) })
                MenuItem(
                    Icons.AutoMirrored.Outlined.MenuBook,
                    "我的阅读",
                    "进度、时长、书籍状态",
                    iconTint = Color(0xFF006874),
                    onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.READING)) },
                )
                MenuItem(
                    Icons.Outlined.Flag,
                    "阅读目标",
                    if (state.goal.goalEnabled) "每天 ${state.goal.dailyMinutes} 分钟" else "设定每日目标与提醒",
                    iconTint = Color(0xFF984715),
                    showDivider = false,
                    onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.GOAL)) },
                )
            }
        }

        // 数据与同步 / 内容整理
        // 「内容整理」独立成组（用户 2026-09-18 反馈）：标签/分类/书单是内容整理不是存储。
        // 「存储与备份」原先独占一张卡却只有一行 —— 与同步同为数据搬运语义，合并进同组。
        item(key = "content-organize") {
            MenuGroup(title = "内容整理") {
                MenuItem(Icons.Outlined.Sell, "标签管理", "书籍 / 灵感 / 笔记标签", iconTint = Color(0xFF5B5B7E), onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.TAGS)) })
                MenuItem(Icons.Outlined.Folder, "分类管理", desc = null, iconTint = Color(0xFF386568), onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.CATEGORIES)) })
                MenuItem(Icons.Outlined.Book, "书单管理", desc = null, iconTint = Color(0xFF6B5778), showDivider = false, onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.SHELVES)) })
            }
        }

        item(key = "data-storage") {
            MenuGroup(title = "数据与同步") {
                MenuItem(Icons.Outlined.Storage, "存储管理", "导出 / 导入快照，清缓存", iconTint = Color(0xFF4C626B), onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.STORAGE)) })
                MenuItem(Icons.Outlined.Refresh, "局域网同步", if (state.paired) "已连接电脑" else "从未同步", iconTint = Color(0xFF1967D2), onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.SYNC)) })
                MenuItem(Icons.Outlined.Cloud, "WebDAV 设置", if (state.webDavConfigured) "已配置" else "未配置", iconTint = Color(0xFF0284C7), onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.WEBDAV)) })
                MenuItem(Icons.Outlined.AutoAwesome, "AI 助手", if (state.aiConfigured) "已配置 Key" else "未配置", iconTint = Color(0xFF7B1FA2), showDivider = false, onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.AI)) })
            }
        }

        // 帮助与关于
        item(key = "help-about") {
            MenuGroup(title = "帮助与关于") {
                MenuItem(Icons.Outlined.BugReport, "日志与诊断", "运行环境与问题记录", iconTint = Color(0xFF4A6572), onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.DIAGNOSTICS)) })
                MenuItem(Icons.Outlined.Security, "隐私安全", "本地优先", iconTint = Color(0xFF2E7D32), onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.PRIVACY)) })
                MenuItem(Icons.Outlined.Info, "关于", desc = null, iconTint = Color(0xFF5E6266), showDivider = false, onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.ABOUT)) })
            }
        }
        } // 有查询词时，以上 4 个菜单组让位给搜索结果
    }
}

// ============================== 首页辅助组件 ==============================

/**
 * 「选区与查词」入口的副标题（R3-X1）。
 *
 * 只暴露**用户真正关心的状态**：第一屏放了几个动作、离线词库装了几部。
 * 不写「已配置/未配置」这类没有信息量的字眼 —— 默认配置本来也是「已配置」。
 */
private fun selectionSummary(settings: SelectionActionSettings, dictionaryCount: Int): String {
    val primaryCount = SelectionActions.effectivePrimary(settings).size
    val dict = when (settings.dictionaryMode) {
        SelectionActions.MODE_SYSTEM -> "系统词典"
        SelectionActions.MODE_ONLINE -> "在线词典"
        else -> if (dictionaryCount > 0) "离线词库 $dictionaryCount 部" else "离线词库待导入"
    }
    return "第一屏 $primaryCount 个动作 · $dict"
}


@Composable
private fun RowScope.ArchiveStat(
    value: String,
    label: String,
    /** 时长行用它挂长按切换；「读完 / 灵感」两格不传，保持纯文本。 */
    valueModifier: Modifier = Modifier,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = value,
            modifier = valueModifier,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MenuGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    CompactSettingsGroup(
        title = title,
        content = content,
    )
}

@Composable
private fun ColumnScope.MenuItem(
    icon: ImageVector,
    label: String,
    desc: String? = null,
    danger: Boolean = false,
    iconTint: Color? = null,
    showDivider: Boolean = true,
    onClick: () -> Unit,
) {
    val actualTint = if (danger) MaterialTheme.colorScheme.error else (iconTint ?: MaterialTheme.colorScheme.primary)
    CompactSettingRow(
        title = label,
        subtitle = desc,
        leading = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(actualTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = actualTint,
                    modifier = Modifier.size(16.dp),
                )
            }
        },
        trailing = {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(16.dp),
            )
        },
        onClick = onClick,
    )
    if (showDivider) {
        CompactItemDivider(startIndent = 52.dp, endIndent = 14.dp)
    }
}

/**
 * 顶栏内嵌胶囊搜索输入框（点击右上角放大镜后展开）。
 */
@Composable
internal fun ProfileTopBarSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            "搜索设置项，如字号、护眼、纸张...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                },
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "清空",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
