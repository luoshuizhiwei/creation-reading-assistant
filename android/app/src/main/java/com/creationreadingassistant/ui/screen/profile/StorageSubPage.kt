package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.res.stringResource
import com.creationreadingassistant.R
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Upload
import com.creationreadingassistant.ui.theme.AppIconSize
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion

// ============================== 存储管理 ==============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StorageSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()
    val books = state.libraryState.books
    val totalBooks = books.size
    val downloadedCount = remember(books) { books.count { isBookDownloaded(it) } }
    val cachedCount = state.libraryState.cachedCount
    val cacheBytes = state.libraryState.cacheBytes
    val indexBytes = state.indexBytes
    val formatCounts = remember(books) { books.groupingBy { it.format }.eachCount() }

    val cacheBudget = 100 * 1024 * 1024L
    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("导出会保存灵感、书库元数据、进度、阅读记录、笔记、标签、分类和同步账号信息；不会导出 AI Key、WebDAV 密码 / token 或设备私有路径。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAction(ProfileAction.Export) }) { Icon(Icons.Outlined.Download, contentDescription = null); Text("导出数据", modifier = Modifier.padding(start = 6.dp)) }
                        OutlinedButton(onClick = { onAction(ProfileAction.Import) }) { Icon(Icons.Outlined.Upload, contentDescription = null); Text("导入数据", modifier = Modifier.padding(start = 6.dp)) }
                    }
                }
            }
        }
        item {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.backup_zip_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.backup_zip_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAction(ProfileAction.ExportZip) }) { Icon(Icons.Outlined.Download, contentDescription = null); Text(stringResource(R.string.backup_zip_export), modifier = Modifier.padding(start = 6.dp)) }
                        OutlinedButton(onClick = { onAction(ProfileAction.ImportZip) }) { Icon(Icons.Outlined.Upload, contentDescription = null); Text(stringResource(R.string.backup_zip_import), modifier = Modifier.padding(start = 6.dp)) }
                    }
                }
            }
        }
        item {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("存储概览", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        StorageStat(icon = Icons.Outlined.Book, value = totalBooks.toString(), label = "总书籍")
                        StorageStat(icon = Icons.Outlined.Download, value = "$downloadedCount 本", label = "已下载")
                        StorageStat(icon = Icons.Outlined.Storage, value = formatBytes(cacheBytes), label = "正文缓存")
                        StorageStat(icon = Icons.Outlined.Search, value = formatBytes(indexBytes), label = "索引大小")
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("正文缓存占用", style = MaterialTheme.typography.bodyMedium)
                            Text(formatBytes(cacheBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(
                            progress = { (cacheBytes.toFloat() / cacheBudget).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "参考预算 100 MB，超出后建议清理缓存。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (formatCounts.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("格式分布", style = MaterialTheme.typography.bodyMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                formatCounts.toSortedMap().forEach { (fmt, count) ->
                                    val label = when (fmt.lowercase()) {
                                        "txt" -> "TXT"
                                        "md" -> "Markdown"
                                        "epub" -> "EPUB"
                                        else -> fmt.uppercase()
                                    }
                                    Card(
                                        shape = LocalComponentSpec.current.pillShape,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(label, style = MaterialTheme.typography.bodySmall)
                                            Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("阅读器正文缓存")
                        Text("$cachedCount 条", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("缓存可在重新打开书籍时重新生成；清理不影响正式数据。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { onAction(ProfileAction.ClearReaderCache) }) { Icon(Icons.Outlined.Delete, contentDescription = null); Text("清理缓存", modifier = Modifier.padding(start = 6.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StorageStat(
    icon: ImageVector,
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 72.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(AppIconSize.Large), tint = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
