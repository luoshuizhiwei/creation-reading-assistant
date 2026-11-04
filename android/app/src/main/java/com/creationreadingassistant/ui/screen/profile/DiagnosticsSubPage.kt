package com.creationreadingassistant.ui.screen.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.ui.components.CompactItemDivider
import com.creationreadingassistant.ui.components.CompactSettingRow
import com.creationreadingassistant.ui.components.CompactSettingsGroup
import com.creationreadingassistant.ui.components.GlassAlertDialog
import androidx.compose.foundation.lazy.items
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ============================== 诊断 ==============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiagnosticsSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logs by AppLog.entries.collectAsStateWithLifecycle()
    var exportMsg by remember { mutableStateOf<String?>(null) }
    var selectedLevel by remember { mutableStateOf<String?>(null) }
    var selectedModule by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    // R6-B7：全库索引重建需要二次确认（会重置增量游标，后台分窗口重扫全书）
    var showRebuildIndexConfirm by remember { mutableStateOf(false) }
    // §6.9 停止索引构建二次确认
    var showCancelIndexConfirm by remember { mutableStateOf(false) }

    val modules = remember(logs) { logs.map { it.module }.distinct().sorted() }
    val filtered = remember(logs, selectedLevel, selectedModule) {
        logs.filter {
            (selectedLevel == null || it.level.name.equals(selectedLevel, ignoreCase = true)) &&
                (selectedModule == null || it.module == selectedModule)
        }
    }

    val levelCounts = remember(logs) { logs.groupingBy { it.level.name }.eachCount() }

    val deviceId = remember {
        runCatching {
            val prefs = context.getSharedPreferences("sync_config", 0)
            prefs.getString("device_id", null) ?: "未知"
        }.getOrDefault("未知")
    }
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrDefault("未知") ?: "未知"
    }

    fun copyToClipboard(text: String, label: String = "诊断日志") {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        exportMsg = "$label 已复制到剪贴板"
    }

    fun exportLog() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val dir = java.io.File(context.cacheDir, "diagnostics").apply { mkdirs() }
                val file = java.io.File(dir, "diagnostics-${System.currentTimeMillis()}.log")
                file.writeText(AppLog.snapshot().ifBlank { "（暂无日志）" })
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "导出诊断日志"))
                AppLog.event("Diagnostics", "导出日志")
            }.onFailure {
                exportMsg = "导出失败：${it.message}"
                AppLog.e("Diagnostics", "导出失败：${it.message}")
            }
        }
    }

    if (showClearConfirm) {
        GlassAlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空诊断日志") },
            text = { Text("清空后历史事件与异常记录无法找回。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        AppLog.clear()
                        AppLog.event("Diagnostics", "已清空日志")
                        exportMsg = "诊断日志已清空"
                    },
                ) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showRebuildIndexConfirm) {
        RebuildSearchIndexConfirmDialog(
            onDismiss = { showRebuildIndexConfirm = false },
            onConfirm = {
                showRebuildIndexConfirm = false
                onAction(ProfileAction.RebuildSearchIndex)
            },
        )
    }
    // §6.9-v3 停止索引构建二次确认（在跑与排队共用同一条真实动作分发）
    if (showCancelIndexConfirm) {
        SearchIndexCancelConfirmDialog(
            queued = !state.searchIndexProgress.isRunning && state.searchIndexProgress.isQueued,
            onDismiss = { showCancelIndexConfirm = false },
            onConfirm = {
                showCancelIndexConfirm = false
                onAction(ProfileAction.CancelSearchIndex)
            },
        )
    }

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 1. 运行环境：6 项指标仍是两列密排，但去掉「卡中卡」描边，降成分组卡内的文本格。
        item {
            CompactSettingsGroup(
                title = "运行环境",
                icon = Icons.Outlined.Info,
                tint = Color(0xFF0284C7),
                description = "设备与应用信息，用于问题定位",
                modifier = Modifier.animateEnter(reducedMotion = reducedMotion),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EnvMetricRow(
                        first = EnvMetric(
                            label = "应用版本",
                            value = appVersion,
                            icon = Icons.Outlined.Info,
                            iconTint = Color(0xFF3B82F6),
                        ),
                        second = EnvMetric(
                            label = "系统版本",
                            value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                            icon = Icons.Outlined.Android,
                            iconTint = Color(0xFF10B981),
                        ),
                    )
                    EnvMetricRow(
                        first = EnvMetric(
                            label = "厂商型号",
                            value = "${Build.MANUFACTURER} ${Build.MODEL}",
                            icon = Icons.Outlined.PhoneAndroid,
                            iconTint = Color(0xFF8B5CF6),
                        ),
                        second = EnvMetric(
                            label = "设备标识",
                            value = "${deviceId.take(8)}…",
                            icon = Icons.Outlined.Fingerprint,
                            iconTint = Color(0xFFF59E0B),
                        ),
                    )
                    EnvMetricRow(
                        first = EnvMetric(
                            label = "界面语言",
                            value = context.resources.configuration.locales.get(0)?.toString() ?: "默认",
                            icon = Icons.Outlined.Translate,
                            iconTint = Color(0xFF06B6D4),
                        ),
                        second = EnvMetric(
                            label = "诊断容量",
                            value = "500 条 (内存环形)",
                            icon = Icons.Outlined.Storage,
                            iconTint = Color(0xFFEC4899),
                        ),
                    )
                }
            }
        }

        // 2. 日志与索引维护：原「搜索索引」独立卡与「诊断日志」卡只差一个分区的语义，
        //    合并为一张操作卡 —— 导出 / 清空 / 重建索引三行动作 + 级别与模块筛选。
        //    链路：重建按钮 → ProfileAction.RebuildSearchIndex → ProfileViewModel.rebuildSearchIndex()
        //    → SearchIndexRepository.triggerFullRebuildNow()，只覆盖活跃书，期间仍可搜索。
        item {
            CompactSettingsGroup(
                title = "日志与索引",
                icon = Icons.Outlined.Storage,
                tint = MaterialTheme.colorScheme.primary,
                description = "共 ${filtered.size} 条 · 异常与关键动作自动记录",
                modifier = Modifier.animateEnter(reducedMotion = reducedMotion),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    CompactSettingRow(
                        title = "导出日志",
                        subtitle = "生成文本文件分享",
                        modifier = Modifier.weight(1f),
                        leading = {
                            Icon(
                                Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = { exportLog() },
                    )
                    CompactSettingRow(
                        title = "清空日志",
                        subtitle = "删除全部历史记录",
                        modifier = Modifier.weight(1f),
                        leading = {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = { showClearConfirm = true },
                    )
                }

                CompactItemDivider()
                CompactSettingRow(
                    title = "重建搜索索引",
                    subtitle = "后台执行，不阻塞阅读",
                    leading = {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFF7C3AED),
                        )
                    },
                    trailing = {
                        Text(
                            text = "执行",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { showRebuildIndexConfirm = true },
                )

                // §6.9-v3 在跑**或已排队**都显示停止入口：排队/等待重试阶段此前没有入口
                if (state.searchIndexProgress.canCancel) {
                    CompactItemDivider()
                    CompactSettingRow(
                        title = if (state.searchIndexProgress.isRunning) "停止搜索索引" else "撤下排队的索引构建",
                        subtitle = "保留断点，下次续建",
                        leading = {
                            Icon(
                                Icons.Outlined.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        trailing = {
                            Text(
                                text = "停止",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = { showCancelIndexConfirm = true },
                    )
                }

                exportMsg?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }

                CompactItemDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "级别",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LogLevelCapsule(
                            label = "全部",
                            badgeColor = Color(0xFF64748B),
                            count = logs.size,
                            selected = selectedLevel == null,
                            onClick = { selectedLevel = null },
                        )
                        LogLevelCapsule(
                            label = "VERBOSE",
                            badgeColor = Color(0xFF94A3B8),
                            count = levelCounts["VERBOSE"] ?: 0,
                            selected = selectedLevel == "VERBOSE",
                            onClick = { selectedLevel = "VERBOSE" },
                        )
                        LogLevelCapsule(
                            label = "DEBUG",
                            badgeColor = Color(0xFF3B82F6),
                            count = levelCounts["DEBUG"] ?: 0,
                            selected = selectedLevel == "DEBUG",
                            onClick = { selectedLevel = "DEBUG" },
                        )
                        LogLevelCapsule(
                            label = "INFO",
                            badgeColor = Color(0xFF10B981),
                            count = levelCounts["INFO"] ?: 0,
                            selected = selectedLevel == "INFO",
                            onClick = { selectedLevel = "INFO" },
                        )
                        LogLevelCapsule(
                            label = "WARN",
                            badgeColor = Color(0xFFF59E0B),
                            count = levelCounts["WARN"] ?: 0,
                            selected = selectedLevel == "WARN",
                            onClick = { selectedLevel = "WARN" },
                        )
                        LogLevelCapsule(
                            label = "ERROR",
                            badgeColor = Color(0xFFEF4444),
                            count = levelCounts["ERROR"] ?: 0,
                            selected = selectedLevel == "ERROR",
                            onClick = { selectedLevel = "ERROR" },
                        )
                        if ((levelCounts["EVENT"] ?: 0) > 0) {
                            LogLevelCapsule(
                                label = "EVENT",
                                badgeColor = Color(0xFF8B5CF6),
                                count = levelCounts["EVENT"] ?: 0,
                                selected = selectedLevel == "EVENT",
                                onClick = { selectedLevel = "EVENT" },
                            )
                        }
                    }

                    // 模块过滤
                    if (modules.isNotEmpty()) {
                        Text(
                            "模块",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SelectablePill(
                                text = "全部模块",
                                selected = selectedModule == null,
                                onClick = { selectedModule = null },
                            )
                            modules.forEach { module ->
                                SelectablePill(
                                    text = module,
                                    selected = selectedModule == module,
                                    onClick = { selectedModule = module },
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 日志条目列表（懒加载，按 Entry.id 键控，避免 500 条同帧全量组合）
        if (filtered.isEmpty()) {
            item(key = "diag-log-empty") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "（无匹配的诊断日志条目）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        } else {
            items(filtered.reversed(), key = { it.id }) { entry ->
                LogEntryItem(
                    entry = entry,
                    onCopy = { copyToClipboard(it) },
                )
            }
        }
    }
}

/** 一项环境指标（纯信息，不可点击）。 */
private data class EnvMetric(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val iconTint: Color,
)

/**
 * 两列环境指标行：不再各自套一层描边小卡，只留图标色点 + 名称 + 取值，
 * 让六项指标在分组卡内读起来像一张表而不是一屏按钮。
 */
@Composable
private fun EnvMetricRow(first: EnvMetric, second: EnvMetric) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EnvMetricCell(first, modifier = Modifier.weight(1f))
        EnvMetricCell(second, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EnvMetricCell(metric: EnvMetric, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(metric.iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                metric.icon,
                contentDescription = null,
                tint = metric.iconTint,
                modifier = Modifier.size(12.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = metric.value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LogLevelCapsule(
    label: String,
    badgeColor: Color,
    count: Int? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) badgeColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
        border = BorderStroke(
            if (selected) 1.dp else 0.6.dp,
            if (selected) badgeColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 11.5.sp,
                ),
                color = if (selected) badgeColor else MaterialTheme.colorScheme.onSurface,
            )
            if (count != null) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (selected) badgeColor.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun LogEntryItem(
    entry: AppLog.Entry,
    onCopy: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val levelColor = logLevelColor(entry.level)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.75f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 顶栏：级别微徽章 + 模块微胶囊 + 时间戳 + 复制按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = levelColor.copy(alpha = 0.14f),
                    border = BorderStroke(0.5.dp, levelColor.copy(alpha = 0.35f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(levelColor),
                        )
                        Text(
                            text = entry.level.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                            ),
                            color = levelColor,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                ) {
                    Text(
                        text = entry.module,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )

                IconButton(
                    onClick = { onCopy("[${entry.timestamp}] [${entry.level}] [${entry.module}] ${entry.message}") },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "复制日志",
                        modifier = Modifier.size(AppIconSize.Compact),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            // 等宽字体代码块微岛
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                )
            }

            // 错误码微岛
            if (entry.code != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { onCopy(entry.code) }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "错误码: ${entry.code}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp,
                            ),
                        )
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "复制错误码",
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

private fun logLevelColor(level: AppLog.Level): Color = when (level) {
    AppLog.Level.INFO -> Color(0xFF10B981)
    AppLog.Level.WARN -> Color(0xFFF59E0B)
    AppLog.Level.ERROR -> Color(0xFFEF4444)
    AppLog.Level.EVENT -> Color(0xFF8B5CF6)
}
