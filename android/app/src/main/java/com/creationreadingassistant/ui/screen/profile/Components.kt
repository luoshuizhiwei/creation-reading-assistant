package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.components.SettingRow
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.ProfileSubPage
import com.creationreadingassistant.ui.theme.rememberCountUp
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

// ============================== 首页 ==============================

@Composable
internal fun ProfileHomeContent(
    modifier: Modifier,
    paired: Boolean,
    webDavConfigured: Boolean,
    aiConfigured: Boolean,
    appThemeLabel: String,
    totalDurationMs: Long,
    completedBookCount: Int,
    inspirationCount: Int,
    onNavigate: (ProfileSubPage) -> Unit,
    onClearCache: () -> Unit,
    reducedMotion: Boolean = false,
) {
    val layout = LocalLayoutTokens.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(layout.contentGap)) {
        // 顶部同步状态卡（对应 ProfileHome 的 compact-profile-card + profile-grid）
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
                HomeStat("阅读时长", (totalDurationMs / 60000).toInt(), { formatDuration(it.toLong() * 60000) }, reducedMotion = reducedMotion)
                HomeStat("累计读完", completedBookCount, reducedMotion = reducedMotion)
                HomeStat("灵感数量", inspirationCount, reducedMotion = reducedMotion)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickTile(label = "同步", value = if (paired) "已连接电脑" else "从未同步", onClick = { onNavigate(ProfileSubPage.SYNC) })
            QuickTile(label = "笔记", value = "查看", onClick = { onNavigate(ProfileSubPage.NOTES) })
        }

        MenuGroup(title = "阅读与外观") {
            MenuItem(Icons.Filled.TextFields, "阅读设置", "字号、行距、主题、翻页模式", onClick = { onNavigate(ProfileSubPage.READER) })
            MenuItem(Icons.Filled.DarkMode, "应用外观", appThemeLabel, onClick = { onNavigate(ProfileSubPage.APPEARANCE) })
            MenuItem(Icons.AutoMirrored.Filled.MenuBook, "我的阅读", "进度、时长、书籍状态", onClick = { onNavigate(ProfileSubPage.READING) })
        }

        MenuGroup(title = "数据与存储") {
            MenuItem(Icons.Filled.Storage, "存储管理", "导出 / 导入数据快照", onClick = { onNavigate(ProfileSubPage.STORAGE) })
            MenuItem(Icons.Filled.Delete, "清理缓存", "清理阅读器正文缓存", danger = true, onClick = onClearCache)
            MenuItem(Icons.Filled.Sell, "标签管理", "书籍 / 灵感 / 笔记标签", onClick = { onNavigate(ProfileSubPage.TAGS) })
            MenuItem(Icons.Filled.Folder, "分类管理", "整理书籍分类", onClick = { onNavigate(ProfileSubPage.CATEGORIES) })
            MenuItem(Icons.Filled.Book, "书单管理", "自定义书单", onClick = { onNavigate(ProfileSubPage.SHELVES) })
        }

        MenuGroup(title = "我的书评与笔记") {
            MenuItem(Icons.Filled.Description, "我的书评 / 笔记", "书签与读书笔记", onClick = { onNavigate(ProfileSubPage.NOTES) })
        }

        MenuGroup(title = "同步与工具") {
            MenuItem(Icons.Filled.Refresh, "局域网同步", if (paired) "已连接电脑" else "从未同步", onClick = { onNavigate(ProfileSubPage.SYNC) })
            MenuItem(Icons.Filled.Cloud, "WebDAV 设置", if (webDavConfigured) "已配置" else "未配置", onClick = { onNavigate(ProfileSubPage.WEBDAV) })
            MenuItem(Icons.Filled.AutoAwesome, "AI 助手", if (aiConfigured) "已配置 Key" else "未配置", onClick = { onNavigate(ProfileSubPage.AI) })
        }

        MenuGroup(title = "帮助与关于") {
            MenuItem(Icons.Filled.BugReport, "日志与诊断", "运行环境与问题记录", onClick = { onNavigate(ProfileSubPage.DIAGNOSTICS) })
            MenuItem(Icons.Filled.Security, "隐私安全", "本地优先", onClick = { onNavigate(ProfileSubPage.PRIVACY) })
            MenuItem(Icons.Filled.Info, "关于", "版本与开源许可", onClick = { onNavigate(ProfileSubPage.ABOUT) })
        }
    }
}

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

@Composable
internal fun EmptyCard(icon: ImageVector, title: String, body: String) {
    SectionCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

// ============================== 通用小组件 ==============================

@Composable
internal fun DegradedNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = modifier)
}

@Composable
internal fun RangeRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    valueLabel: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.widthIn(min = 48.dp))
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            steps = if (step > 0f) ((max - min) / step).toInt() - 1 else 0,
            modifier = Modifier.weight(1f),
        )
        Text(valueLabel(value), modifier = Modifier.widthIn(min = 56.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> SegmentedRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    // 视觉统一：与阅读器弹层 OptionPill 同语言（SelectablePill，pillShape + 内建触感），
    // 替代旧 OutlinedButton + 硬编码 8.dp 圆角的分段按钮
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = if (enabled) Modifier else Modifier.alpha(0.5f),
    ) {
        options.forEach { (value, label) ->
            SelectablePill(
                text = label,
                selected = value == selected,
                onClick = { if (enabled) onSelect(value) },
            )
        }
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingRow(
        title = label,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
internal fun textFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
)
