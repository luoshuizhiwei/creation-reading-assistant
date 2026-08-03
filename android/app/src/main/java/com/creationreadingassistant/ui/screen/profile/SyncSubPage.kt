package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.viewmodel.SyncFailedItem

// ============================== 同步页 ==============================

@Composable
internal fun SyncSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val paired = state.paired
    val pairing = state.pairing
    val syncing = state.syncing
    val baseUrl = state.baseUrl
    val pendingDownloadCount = state.pendingDownloadCount
    val lastSyncResult = state.lastSyncResult
    val syncLogs = state.syncLogs

    var pairingText by remember { mutableStateOf("") }
    var showLogs by remember { mutableStateOf(false) }
    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Wifi, contentDescription = null, tint = if (paired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (paired) "已连接电脑" else "未连接电脑", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$pendingDownloadCount 本待下载正文", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("电脑端开启同步服务后，可以扫码或粘贴配对 URL。同步只更新书架、灵感和进度，书籍正文可在书架按需下载。每次同步前会自动备份本地数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = pairingText,
                        onValueChange = { pairingText = it },
                        placeholder = { Text("粘贴电脑端配对 URL 或二维码载荷") },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onAction(ProfileAction.ScanQr) }) { Icon(Icons.Filled.QrCode, contentDescription = null); Text("扫码", modifier = Modifier.padding(start = 6.dp)) }
                        OutlinedButton(onClick = { onAction(ProfileAction.StartPairing(pairingText)) }, enabled = !pairing) {
                            if (pairing) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Icon(Icons.Filled.Wifi, contentDescription = null)
                            Text(if (pairing) "连接中" else "连接电脑", modifier = Modifier.padding(start = 6.dp))
                        }
                        Button(onClick = { onAction(ProfileAction.SyncNow) }, enabled = paired && !syncing) {
                            if (syncing) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Icon(Icons.Filled.Sync, contentDescription = null)
                            Text(if (syncing) "同步中" else "立即同步", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    if (paired) {
                        TextButton(onClick = { onAction(ProfileAction.Unpair) }) { Icon(Icons.Filled.LinkOff, contentDescription = null); Text("解除配对", modifier = Modifier.padding(start = 6.dp)) }
                    }
                }
            }
        }
        item {
            if (paired && !baseUrl.isNullOrBlank()) {
                Text("已配对服务：$baseUrl", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("（尚未配对电脑端同步服务；扫码或粘贴配对 URL 后即可同步。）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        lastSyncResult?.let { result ->
            item {
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                if (result.success) Icons.Filled.CheckCircle else Icons.Filled.Info,
                                contentDescription = null,
                                tint = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                            Column {
                                Text(
                                    if (result.success) "最近一次同步成功" else "最近一次同步失败",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${formatDateTime(result.timestamp)} · 耗时 ${formatSyncDuration(result.durationMs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (result.success) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                SyncStatColumn(
                                    icon = Icons.Outlined.Upload,
                                    title = "上传",
                                    items = listOf(
                                        "${result.uploaded.inspirations} 条灵感",
                                        "${result.uploaded.books} 本书",
                                        "${result.uploaded.progress} 条进度",
                                        "${result.uploaded.bookFiles} 个正文文件",
                                    ),
                                )
                                SyncStatColumn(
                                    icon = Icons.Filled.Download,
                                    title = "下载",
                                    items = listOf(
                                        "${result.downloaded.inspirations} 条灵感",
                                        "${result.downloaded.books} 本书",
                                        "${result.downloaded.progress} 条进度",
                                        "${result.downloaded.sessions} 条阅读会话",
                                    ),
                                )
                                SyncStatColumn(
                                    icon = Icons.Filled.Description,
                                    title = "待处理",
                                    items = listOf("${result.pendingDownloadCount} 本待下载正文"),
                                )
                            }
                        }
                        if (result.failedItems.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.Error, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                    Text("${result.failedItems.size} 项失败", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                                result.failedItems.take(10).forEach { item ->
                                    val typeLabel = when (item.type) {
                                        "book" -> "书籍"
                                        "inspiration" -> "灵感"
                                        "progress" -> "进度"
                                        "session" -> "会话"
                                        "book_file" -> "正文下载"
                                        "sync" -> "同步"
                                        else -> item.type
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            "· $typeLabel${item.title?.let { "《$it》" } ?: ""}：${item.reason}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                                        )
                                        TextButton(onClick = { onAction(ProfileAction.RetryItem(item)) }, enabled = !syncing) {
                                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Text("重试", modifier = Modifier.padding(start = 4.dp))
                                        }
                                    }
                                }
                                if (result.failedItems.size > 10) {
                                    Text(
                                        "…以及其余 ${result.failedItems.size - 10} 项",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(onClick = { onAction(ProfileAction.RetryFailed) }, enabled = !syncing) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text("重试失败项", modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                        if (result.conflicts.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                                    Text("${result.conflicts.size} 条冲突（服务端保留）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                                result.conflicts.take(5).forEach { c ->
                                    val typeLabel = when (c.type) {
                                        "book" -> "书籍"
                                        "inspiration" -> "灵感"
                                        "progress" -> "进度"
                                        "session" -> "会话"
                                        else -> c.type
                                    }
                                    Text(
                                        "· $typeLabel${c.title?.let { "《$it》" } ?: ""}：本地 ${formatDateTimeShort(c.localUpdatedAt)} vs 远端 ${formatDateTimeShort(c.remoteUpdatedAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (result.conflicts.size > 5) {
                                    Text(
                                        "…以及其余 ${result.conflicts.size - 5} 条",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showLogs = !showLogs },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("同步日志 / 最近一次错误", style = MaterialTheme.typography.titleSmall)
                        Icon(
                            if (showLogs) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (showLogs) "收起" else "展开",
                        )
                    }
                    if (showLogs) {
                        if (syncLogs.isEmpty()) {
                            Text("暂无同步日志。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                syncLogs.forEach { line ->
                                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatColumn(
    icon: ImageVector,
    title: String,
    items: List<String>,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 80.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        items.forEach { item ->
            Text(item, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
