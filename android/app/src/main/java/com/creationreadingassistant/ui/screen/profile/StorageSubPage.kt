package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.AutoAwesomeMotion
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.R
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.io.File

// ============================== 存储与缓存管理 ==============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StorageSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val context = LocalContext.current

    val books = state.libraryState.books
    val totalBooks = books.size
    val downloadedCount = remember(books) { books.count { isBookDownloaded(it) } }
    val cachedCount = state.libraryState.cachedCount
    val cacheBytes = state.libraryState.cacheBytes
    val indexBytes = state.indexBytes
    val formatCounts = remember(books) { books.groupingBy { it.format }.eachCount() }

    // 计算字体目录存储占用
    val fontsBytes = remember {
        runCatching {
            val fontDir = File(context.filesDir, "fonts")
            if (fontDir.exists()) {
                fontDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else 0L
        }.getOrDefault(0L)
    }

    // 危险清理二次确认弹窗状态
    var showDangerDialog by remember { mutableStateOf(false) }

    val cacheBudget = 100 * 1024 * 1024L

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 1. 存储占用 3 列独立微岛指标矩阵（正文缓存、字体资源、图片封面），采用分色大数字与微底座
        item(key = "storage-metrics-matrix") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SubPageSectionTitle(
                        title = "存储占用与资源分布",
                        icon = Icons.Outlined.Analytics,
                        iconTint = MaterialTheme.colorScheme.primary,
                        trailing = {
                            Surface(
                                shape = PillShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            ) {
                                Text(
                                    "共 $totalBooks 本 · 已下 $downloadedCount 本",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        },
                    )

                    // 3 列独立微岛指标矩阵
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StorageMetricMicroCard(
                            icon = Icons.Outlined.Storage,
                            iconTint = Color(0xFF0284C7), // 天蓝
                            value = formatBytes(cacheBytes),
                            label = "正文缓存",
                            subText = "$cachedCount 条缓存",
                            modifier = Modifier.weight(1f),
                        )
                        StorageMetricMicroCard(
                            icon = Icons.Outlined.TextFields,
                            iconTint = Color(0xFF8B5CF6), // 丁香紫
                            value = formatBytes(fontsBytes),
                            label = "字体资源",
                            subText = "自定义字体库",
                            modifier = Modifier.weight(1f),
                        )
                        StorageMetricMicroCard(
                            icon = Icons.Outlined.Image,
                            iconTint = Color(0xFFF59E0B), // 暖金
                            value = formatBytes(indexBytes),
                            label = "图片与索引",
                            subText = "封面与检索库",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // 缓存预算进度微导轨
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "正文缓存预算占用",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "${formatBytes(cacheBytes)} / 100 MB",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (cacheBytes.toFloat() / cacheBudget).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(PillShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        )
                        Text(
                            "参考基线 100 MB；缓存仅用于加速即时排版计算，可放心清理，打开书籍时将自动重建。",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // 格式分布胶囊组
                    if (formatCounts.isNotEmpty()) {
                        SectionDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "书库格式分布",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                formatCounts.toSortedMap().forEach { (fmt, count) ->
                                    val label = when (fmt.lowercase()) {
                                        "txt" -> "TXT 纯文本"
                                        "md" -> "Markdown 随笔"
                                        "epub" -> "EPUB 电子书"
                                        else -> fmt.uppercase()
                                    }
                                    val tint = when (fmt.lowercase()) {
                                        "epub" -> Color(0xFF1967D2)
                                        "txt" -> Color(0xFF059669)
                                        "md" -> Color(0xFF7C3AED)
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                    Surface(
                                        shape = PillShape,
                                        color = tint.copy(alpha = 0.1f),
                                        border = BorderStroke(0.8.dp, tint.copy(alpha = 0.3f)),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                label,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                count.toString(),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = tint,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. 清理缓存项卡片微岛化，右侧配置紧凑微胶囊清理按钮
        item(key = "storage-clean-cache-card") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0284C7).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.CleaningServices,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column {
                            Text(
                                "阅读器正文缓存",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "含 EPUB 解压临时文件及排版分片；不影响进度与笔记",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // 紧凑微胶囊清理按钮
                    OutlinedButton(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            onAction(ProfileAction.ClearReaderCache)
                        },
                        shape = PillShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("清理缓存", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }

        // 3. 危险清理操作（清空所有离线书籍）采用深红危险告警微岛，配 GlassAlertDialog 二次确认
        item(key = "storage-danger-zone-card") {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f),
                border = BorderStroke(0.9.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateEnter(reducedMotion = reducedMotion),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column {
                            Text(
                                "清空所有离线书籍资源",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                ),
                            )
                            Text(
                                "清除已下载正文源文件以释放空间；书架条目与云端数据仍保留",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // 紧凑危险微胶囊按钮
                    Button(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            showDangerDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        shape = PillShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Icon(Icons.Outlined.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "清空离线",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }

        // 4. 数据快照 (JSON)
        item(key = "storage-json-card") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SubPageSectionTitle(
                        title = "数据快照 (JSON)",
                        icon = Icons.Outlined.Storage,
                        iconTint = Color(0xFF4C626B),
                    )
                    Text(
                        "导出会保存灵感、书库元数据、进度、阅读记录、笔记、标签、分类和同步账号信息；不会导出 AI Key、WebDAV 密码 / token 或设备私有路径。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                onAction(ProfileAction.Export)
                            },
                            shape = PillShape,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("导出快照")
                        }
                        OutlinedButton(
                            onClick = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                onAction(ProfileAction.Import)
                            },
                            shape = PillShape,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                        ) {
                            Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("导入快照")
                        }
                    }
                }
            }
        }

        // 5. 完整离线包 (ZIP)
        item(key = "storage-zip-card") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SubPageSectionTitle(
                        title = stringResource(R.string.backup_zip_title),
                        icon = Icons.Outlined.FolderZip,
                        iconTint = Color(0xFF1967D2),
                    )
                    Text(
                        stringResource(R.string.backup_zip_desc),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                onAction(ProfileAction.ExportZip)
                            },
                            shape = PillShape,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.backup_zip_export))
                        }
                        OutlinedButton(
                            onClick = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                onAction(ProfileAction.ImportZip)
                            },
                            shape = PillShape,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                        ) {
                            Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.backup_zip_import))
                        }
                    }
                }
            }
        }
    }

    // 危险操作二次确认对话框 (GlassAlertDialog)
    if (showDangerDialog) {
        GlassAlertDialog(
            onDismissRequest = { showDangerDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "清空所有离线书籍与资源",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            },
            text = {
                Text(
                    "此操作将清理本地下载的书籍正文文件并释放空间。书架目录、阅读进度、书签和笔记均会完整保留，后续若需阅读可随时重新下载。确认继续？",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDangerDialog = false
                        // 清理应用内部下载目录与缓存
                        runCatching {
                            val booksDir = File(context.filesDir, "books")
                            if (booksDir.exists()) booksDir.deleteRecursively()
                        }
                        onAction(ProfileAction.ClearReaderCache)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = PillShape,
                ) {
                    Text("确认清空", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDangerDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

/**
 * 存储指标微岛卡片：包含分色微底座、大数字与说明
 */
@Composable
private fun StorageMetricMicroCard(
    icon: ImageVector,
    iconTint: Color,
    value: String,
    label: String,
    subText: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
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

            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )

            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                subText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
            )
        }
    }
}
