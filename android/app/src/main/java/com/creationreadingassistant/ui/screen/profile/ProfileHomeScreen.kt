package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SettingRow
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.rememberCountUp
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

@Composable
internal fun ProfileHomeScreen(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()
    val summary = state.homeSummary

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 顶部同步状态卡（对应 ProfileHome 的 compact-profile-card + profile-grid）
        item(key = "header-card") {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Book, contentDescription = null, modifier = Modifier.size(28.dp))
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text("创作阅读助手", style = MaterialTheme.typography.titleMedium)
                        Text("本地优先", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    HomeStat("阅读时长", (summary.totalDurationMs / 60000).toInt(), { formatDuration(it.toLong() * 60000) }, reducedMotion = reducedMotion)
                    HomeStat("累计读完", summary.completedBookCount, reducedMotion = reducedMotion)
                    HomeStat("灵感数量", summary.inspirationCount, reducedMotion = reducedMotion)
                }
            }
        }

        // 快捷导航磁贴
        item(key = "quick-tiles") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile(label = "同步", value = if (state.paired) "已连接电脑" else "从未同步", onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.SYNC)) })
                QuickTile(label = "笔记", value = "查看", onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.NOTES)) })
            }
        }

        // 阅读与外观
        item(key = "reading-appearance") {
            MenuGroup(title = "阅读与外观") {
                MenuItem(Icons.Filled.TextFields, "阅读设置", "字号、行距、主题、翻页模式", onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.READER)) })
                MenuItem(Icons.Filled.DarkMode, "应用外观", state.appThemeLabel, onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.APPEARANCE)) })
                MenuItem(Icons.AutoMirrored.Filled.MenuBook, "我的阅读", "进度、时长、书籍状态", onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.READING)) })
            }
        }

        // 数据与存储
        item(key = "data-storage") {
            MenuGroup(title = "数据与存储") {
                MenuItem(Icons.Filled.Storage, "存储管理", "导出 / 导入数据快照", onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.STORAGE)) })
                MenuItem(Icons.Filled.Delete, "清理缓存", "清理阅读器正文缓存", danger = true, onClick = { onAction(ProfileAction.ClearReaderCache) })
                MenuItem(Icons.Filled.Sell, "标签管理", "书籍 / 灵感 / 笔记标签", onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.TAGS)) })
                MenuItem(Icons.Filled.Folder, "分类管理", "整理书籍分类", onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.CATEGORIES)) })
                MenuItem(Icons.Filled.Book, "书单管理", "自定义书单", onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.SHELVES)) })
            }
        }

        // 我的书评与笔记
        item(key = "notes") {
            MenuGroup(title = "我的书评与笔记") {
                MenuItem(Icons.Filled.Description, "我的书评 / 笔记", "书签与读书笔记", onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.NOTES)) })
            }
        }

        // 同步与工具
        item(key = "sync-tools") {
            MenuGroup(title = "同步与工具") {
                MenuItem(Icons.Filled.Refresh, "局域网同步", if (state.paired) "已连接电脑" else "从未同步", onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.SYNC)) })
                MenuItem(Icons.Filled.Cloud, "WebDAV 设置", if (state.webDavConfigured) "已配置" else "未配置", onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.WEBDAV)) })
                MenuItem(Icons.Filled.AutoAwesome, "AI 助手", if (state.aiConfigured) "已配置 Key" else "未配置", onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.AI)) })
            }
        }

        // 帮助与关于
        item(key = "help-about") {
            MenuGroup(title = "帮助与关于") {
                MenuItem(Icons.Filled.BugReport, "日志与诊断", "运行环境与问题记录", onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.DIAGNOSTICS)) })
                MenuItem(Icons.Filled.Security, "隐私安全", "本地优先", onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.PRIVACY)) })
                MenuItem(Icons.Filled.Info, "关于", "版本与开源许可", onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.ABOUT)) })
            }
        }
    }
}

// ============================== 首页辅助组件 ==============================

@Composable
private fun HomeStat(label: String, value: Int, format: (Int) -> String = { "$it" }, reducedMotion: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(rememberCountUp(value, reducedMotion).let { format(it) }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowScope.QuickTile(label: String, value: String, onClick: () -> Unit) {
    val haptic = rememberHaptic(rememberReducedMotion())
    SectionCard(
        modifier = Modifier.weight(1f),
        onClick = { haptic(HapticFeedbackType.TextHandleMove); onClick() },
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MenuGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LocalLayoutTokens.current.relatedGap)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionCard(contentPadding = 0.dp, content = content)
    }
}

@Composable
private fun ColumnScope.MenuItem(
    icon: ImageVector,
    label: String,
    desc: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    // G 档：SettingRow 已内建触感，此处不再手动触发（防双振）
    SettingRow(
        title = label,
        subtitle = desc,
        leading = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        },
        trailing = {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = onClick,
    )
}
