package com.creationreadingassistant.ui.screen.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import com.creationreadingassistant.ui.theme.AppIconSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.theme.LocalComponentSpec
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
    var selectedLevel by remember { mutableStateOf<AppLog.Level?>(null) }
    var selectedModule by remember { mutableStateOf<String?>(null) }

    val modules = remember(logs) { logs.map { it.module }.distinct().sorted() }
    val filtered = remember(logs, selectedLevel, selectedModule) {
        logs.filter {
            (selectedLevel == null || it.level == selectedLevel) &&
                (selectedModule == null || it.module == selectedModule)
        }
    }

    val deviceId = remember {
        runCatching {
            val prefs = context.getSharedPreferences("sync_config", 0)
            prefs.getString("device_id", null) ?: "未知"
        }.getOrDefault("未知")
    }
    val info = remember {
        listOf<Pair<String, String>>(
            "应用版本" to (runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrDefault("未知") ?: "未知"),
            "设备标识" to "${deviceId.take(8)}…",
            "厂商 / 型号" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "系统版本" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "语言" to (context.resources.configuration.locales.get(0)?.toString() ?: "未知"),
        )
    }

    fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("错误码", text))
        exportMsg = "错误码 $text 已复制"
    }

    fun exportLog() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                // 写进专用子目录：FileProvider 只暴露 cache/diagnostics，
                // 不再把整个 cacheDir（含缓存的书籍原文）纳入可分享范围。
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

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
        SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
            Column {
                Text("运行环境", style = MaterialTheme.typography.titleMedium)
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    info.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        }
        item {
        SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("日志", style = MaterialTheme.typography.titleMedium)
                Text("运行中的导入、同步、备份与未捕获异常会自动记录到这里（最多保留 500 条）。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLog() }) { Icon(Icons.Outlined.Download, contentDescription = null); Text("导出日志", modifier = Modifier.padding(start = 6.dp)) }
                    OutlinedButton(onClick = { AppLog.clear(); AppLog.event("Diagnostics", "已清空日志") }) { Icon(Icons.Outlined.Delete, contentDescription = null); Text("清空日志", modifier = Modifier.padding(start = 6.dp)) }
                }
                exportMsg?.let { msg ->
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                SectionDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("级别过滤", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogFilterChip(label = "全部", selected = selectedLevel == null) { selectedLevel = null }
                    AppLog.Level.entries.forEach { level ->
                        LogFilterChip(
                            label = logLevelLabel(level),
                            selected = selectedLevel == level,
                        ) { selectedLevel = level }
                    }
                }
                if (modules.isNotEmpty()) {
                    Text("模块过滤", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LogFilterChip(label = "全部模块", selected = selectedModule == null) { selectedModule = null }
                        modules.forEach { module ->
                            LogFilterChip(
                                label = module,
                                selected = selectedModule == module,
                            ) { selectedModule = module }
                        }
                    }
                }
                SectionDivider(modifier = Modifier.padding(vertical = 4.dp))
                if (filtered.isEmpty()) {
                    Text("（无匹配日志）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    filtered.reversed().forEach { entry ->
                        LogEntryItem(entry = entry, onCopyCode = { copyToClipboard(it) })
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun LogEntryItem(
    entry: AppLog.Entry,
    onCopyCode: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = LocalComponentSpec.current.listItemShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    logLevelIcon(entry.level),
                    contentDescription = entry.level.name,
                    modifier = Modifier.size(AppIconSize.Small),
                    tint = logLevelColor(entry.level, MaterialTheme.colorScheme),
                )
                Text(logLevelLabel(entry.level), style = MaterialTheme.typography.labelSmall, color = logLevelColor(entry.level, MaterialTheme.colorScheme))
                Text(entry.module, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(entry.timestamp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (entry.code != null) {
                    IconButton(onClick = { onCopyCode(entry.code) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "复制错误码", modifier = Modifier.size(AppIconSize.Compact))
                    }
                }
            }
            Text(
                entry.message,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace,
            )
            if (expanded && entry.code != null) {
                Text(
                    "错误码：${entry.code}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    SelectablePill(
        text = label,
        selected = selected,
        onClick = onClick,
    )
}

private fun logLevelLabel(level: AppLog.Level): String = when (level) {
    AppLog.Level.INFO -> "信息"
    AppLog.Level.WARN -> "警告"
    AppLog.Level.ERROR -> "错误"
    AppLog.Level.EVENT -> "事件"
}

private fun logLevelIcon(level: AppLog.Level): ImageVector = when (level) {
    AppLog.Level.INFO -> Icons.Outlined.Info
    AppLog.Level.WARN -> Icons.Outlined.Warning
    AppLog.Level.ERROR -> Icons.Outlined.Error
    AppLog.Level.EVENT -> Icons.Outlined.AutoAwesome
}

private fun logLevelColor(level: AppLog.Level, colorScheme: ColorScheme): Color = when (level) {
    AppLog.Level.INFO -> colorScheme.primary
    AppLog.Level.WARN -> colorScheme.tertiary
    AppLog.Level.ERROR -> colorScheme.error
    AppLog.Level.EVENT -> colorScheme.primary
}
