package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.settings.SelectionActionSettings
import com.creationreadingassistant.data.settings.SelectionActions
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SettingRow
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.rememberCountUp
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.util.DURATION_UNIT_TOGGLE_LABEL
import com.creationreadingassistant.ui.util.LocalDurationUnit
import com.creationreadingassistant.ui.util.LocalDurationUnitToggle
import com.creationreadingassistant.ui.util.formatDuration

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProfileHomeScreen(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()
    val summary = state.homeSummary

    // 阅读时长按当前单位（天/小时/分钟）呈现，供顶部档案卡的统计行使用。
    // S15-F1/F2：改用公共 formatter，不再本地重写 when 分支（本地版缺 ms<=0 保护，
    // 且与公共策略分叉）。
    val durationUnit = LocalDurationUnit.current
    // 长按切换由档案卡统一承载（见下方 combinedClickable），子文本不再单独挂手势。
    val toggleDurationUnit = LocalDurationUnitToggle.current

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 顶部同步状态卡（对应 ProfileHome 的 compact-profile-card + profile-grid）
        // 点击进入「我的阅读」：像开源阅读一样按书查看累计阅读时长 / 进度 / 最近阅读。
        item(key = "header-card") {
            // G6（行为保留版）：整卡是这块档案卡**唯一**的手势层——普通点击打开
            // 「我的阅读」（保留改动前的整卡点击区域，含空白处），长按切换全局时长单位。
            // 与 MyReadingScreen 的记录行同款：行内时长文本刻意不带任何手势——
            // 空 onClick 的子节点会截获父级点击，父子两层重复挂切换会让读屏
            // 在同一张卡里读到多个操作。两个 label 让读屏用户预先知道有哪些动作。
            SectionCard(
                modifier = Modifier.combinedClickable(
                    onClickLabel = "查看阅读档案",
                    onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.READING)) },
                    onLongClickLabel = DURATION_UNIT_TOGGLE_LABEL,
                    onLongClick = toggleDurationUnit,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(
                            "创作阅读助手",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp,
                            ),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "本地优先 · 点击查看阅读档案",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }

                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 0.6.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    // 列宽由**最终值**格式化后的隐形占位文本定宽（见 HomeStat），
                    // 所以 count-up 过程里文本形状变化不会挤动相邻统计项。
                    val totalMinutes = (summary.totalDurationMs / 60000).toInt()
                    HomeStat(
                        "阅读时长",
                        totalMinutes,
                        // S15-F1/F2：走公共 formatter，跟随全局时长单位（小时/天），
                        // 不再本地按 totalMinutes 重写 when 分支（本地版缺 ms<=0 保护且与公共策略分叉）。
                        // 长按切换挂在卡片手势层（见上），时长文本本身不带任何手势。
                        { v -> formatDuration(v * 60_000L, durationUnit) },
                        reducedMotion = reducedMotion,
                        countUpKey = "profile.home.duration",
                    )
                    HomeStat(
                        "累计读完",
                        summary.completedBookCount,
                        reducedMotion = reducedMotion,
                        countUpKey = "profile.home.completed",
                    )
                    // 灵感数量可点直达灵感中心：显示了数量就要有对应的入口（同类反馈：灵感在哪）
                    HomeStat(
                        "灵感数量",
                        summary.inspirationCount,
                        reducedMotion = reducedMotion,
                        onClick = { onAction(ProfileAction.OpenInspirations) },
                        countUpKey = "profile.home.inspirations",
                    )
                }
            }
        }

        // 快捷导航磁贴（产品规划无独立笔记概念：原「笔记」磁贴改为灵感中心直达）
        item(key = "quick-tiles") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile(
                    icon = Icons.Outlined.Refresh,
                    label = "局域网同步",
                    value = if (state.paired) "已连接电脑" else "从未同步",
                    iconTint = Color(0xFF1967D2),
                    statusIndicator = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.paired) Color(0xFF10B981)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                ),
                        )
                    },
                    onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.SYNC)) },
                )
                QuickTile(
                    icon = Icons.Outlined.AutoAwesome,
                    label = "灵感工坊",
                    value = if (summary.inspirationCount > 0) "已记录 ${summary.inspirationCount} 条" else "记录创作灵感",
                    iconTint = MaterialTheme.colorScheme.primary,
                    statusIndicator = {
                        if (summary.inspirationCount > 0) {
                            Text(
                                "${summary.inspirationCount}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    },
                    onClick = { onAction(ProfileAction.OpenInspirations) },
                )
            }
        }

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
                MenuItem(Icons.AutoMirrored.Outlined.MenuBook, "我的阅读", "进度、时长、书籍状态", iconTint = Color(0xFF006874), onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.READING)) })
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

        // 数据与存储
        item(key = "data-storage") {
            MenuGroup(title = "数据与存储") {
                MenuItem(Icons.Outlined.Storage, "存储管理", "导出 / 导入数据快照", iconTint = Color(0xFF4C626B), onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.STORAGE)) })
                MenuItem(Icons.Outlined.Delete, "清理缓存", "清理阅读器正文缓存", danger = true, onClick = { onAction(ProfileAction.ClearReaderCache) })
                MenuItem(Icons.Outlined.Sell, "标签管理", "书籍 / 灵感 / 笔记标签", iconTint = Color(0xFF5B5B7E), onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.TAGS)) })
                MenuItem(Icons.Outlined.Folder, "分类管理", "整理书籍分类", iconTint = Color(0xFF386568), onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.CATEGORIES)) })
                MenuItem(Icons.Outlined.Book, "书单管理", "自定义书单", iconTint = Color(0xFF6B5778), showDivider = false, onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.SHELVES)) })
            }
        }

        // 同步与工具
        item(key = "sync-tools") {
            MenuGroup(title = "同步与工具") {
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
                MenuItem(Icons.Outlined.Info, "关于", "版本与开源许可", iconTint = Color(0xFF5E6266), showDivider = false, onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.ABOUT)) })
            }
        }
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
private fun HomeStat(
    label: String,
    value: Int,
    format: (Int) -> String = { "$it" },
    reducedMotion: Boolean = false,
    onClick: (() -> Unit)? = null,
    /**
     * count-up 的跨重建记忆 key。默认取 [label] 保证不同卡片天然不同；
     * 显式传入可以避免文案调整（含多语言）后 key 跟着变、导致数字又从 0 重播一次。
     */
    countUpKey: String = label,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // onClick 非空时该项自身可点（如灵感数量→灵感中心），内层点击优先于顶卡点击
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val valueStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp)
            // 隐形占位文本按最终值定宽：count-up 从 0 滚动时槽位宽度恒定，
            // 相邻统计项不再被滚动过程挤来挤去（真机反馈）。
            //
            // 它是**纯装饰**：文字 `Color.Transparent` 不可见，只为测量占位，因此必须
            // 退出无障碍语义树（[clearAndSetSemantics]）。否则它会带着与可见值**同文本
            // 同坐标**的节点并入卡片合并语义：读屏在同一张卡里把同一个时长朗读两遍，
            // 自动化也无法唯一定位可见值。清除语义只影响语义树，不改变测量与布局，
            // count-up 动画与整卡手势都不受影响。
            Text(
                format(value),
                style = valueStyle,
                fontWeight = FontWeight.Bold,
                color = Color.Transparent,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Text(
                rememberCountUp(value, countUpKey, reducedMotion).let { format(it) },
                style = valueStyle,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowScope.QuickTile(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color,
    statusIndicator: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    SectionCard(
        modifier = Modifier.weight(1f),
        onClick = { haptic(HapticFeedbackType.TextHandleMove); onClick() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
            statusIndicator?.invoke()
        }
        Spacer(Modifier.height(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MenuGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LocalLayoutTokens.current.relatedGap)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 12.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionCard(contentPadding = 0.dp, content = content)
    }
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
    SettingRow(
        title = label,
        subtitle = desc,
        leading = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(actualTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = actualTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        trailing = {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
    )
    if (showDivider) {
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(start = 58.dp),
            thickness = 0.6.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        )
    }
}
