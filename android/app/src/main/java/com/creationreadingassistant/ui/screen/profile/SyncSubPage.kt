package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.SyncFailedItem

// ============================== 多端互联与同步页 ==============================

@Composable
internal fun SyncSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)

    val paired = state.paired
    val pairing = state.pairing
    val syncing = state.syncing
    val baseUrl = state.baseUrl
    val pendingDownloadCount = state.pendingDownloadCount
    val lastSyncResult = state.lastSyncResult
    val syncLogs = state.syncLogs

    var pairingText by remember { mutableStateOf("") }
    var showLogs by remember { mutableStateOf(false) }

    // 同步项开关（书架、阅读进度、笔记灵感）
    var syncBooksEnabled by rememberSaveable { mutableStateOf(true) }
    var syncProgressEnabled by rememberSaveable { mutableStateOf(true) }
    var syncInspirationsEnabled by rememberSaveable { mutableStateOf(true) }

    // 冲突解决策略（以远端为准 / 以本地为准）
    var conflictStrategy by rememberSaveable { mutableStateOf("remote") } // "remote" | "local"

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 1. 同步状态总览微岛：配备 40dp 独立微彩底座，显示当前同步策略与上一次同步时间微胶囊
        item(key = "sync-overview-card") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // 40dp 独立微彩底座
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (paired) Color(0xFF1967D2).copy(alpha = 0.14f)
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Devices,
                                    contentDescription = null,
                                    tint = if (paired) Color(0xFF1967D2) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }

                            Column {
                                Text(
                                    "局域网多端互联",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "电脑端开启同步后，扫码即可实现秒级双向安全同步",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // 状态指示微胶囊
                        Surface(
                            shape = PillShape,
                            color = if (paired) Color(0xFF10B981).copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                            border = BorderStroke(
                                0.8.dp,
                                if (paired) Color(0xFF10B981).copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (paired) Color(0xFF10B981) else Color.Gray),
                                )
                                Text(
                                    if (paired) "已连接电脑" else "未连接",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (paired) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // 策略与上一次同步时间微胶囊栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = PillShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = if (conflictStrategy == "remote") "冲突策略：远端为准" else "冲突策略：本地为准",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (lastSyncResult != null) {
                            Surface(
                                shape = PillShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF10B981))
                                    Text(
                                        text = "上次同步：${formatDateTimeShort(lastSyncResult.timestamp)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // 待下载正文微胶囊提示条
                    if (pendingDownloadCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(AppIconSize.Compact),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "$pendingDownloadCount 本待下载正文（可随时在书架按需下载）",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    SectionDivider()

                    // 配对状态与控制大屏
                    if (paired && !baseUrl.isNullOrBlank()) {
                        // 已配对节点微岛
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1967D2).copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.Wifi,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color(0xFF1967D2),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "已配对桌面端地址",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        baseUrl,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }

                        // 高质感主同步按钮
                        Button(
                            onClick = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                onAction(ProfileAction.SyncNow)
                            },
                            enabled = !syncing,
                            shape = PillShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                        ) {
                            if (syncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Text(
                                if (syncing) "正在双向同步中…" else "立即双向同步",
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }

                        // 重新扫码与解除配对微胶囊
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    onAction(ProfileAction.ScanQr)
                                },
                                shape = PillShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                            ) {
                                Icon(Icons.Outlined.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("重新扫码")
                            }
                            OutlinedButton(
                                onClick = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    onAction(ProfileAction.Unpair)
                                },
                                shape = PillShape,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                            ) {
                                Icon(Icons.Outlined.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("解除配对")
                            }
                        }
                    } else {
                        // 未配对状态微岛大屏
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 扫码直达大按钮
                            Button(
                                onClick = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    onAction(ProfileAction.ScanQr)
                                },
                                shape = PillShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                            ) {
                                Icon(Icons.Outlined.QrCode, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("扫码快速配对电脑", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                            }

                            Text(
                                "或手动粘贴电脑端同步二维码载荷 / URL：",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            OutlinedTextField(
                                value = pairingText,
                                onValueChange = { pairingText = it },
                                placeholder = { Text("http://192.168.x.x:port?token=…") },
                                singleLine = false,
                                maxLines = 3,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors(),
                            )

                            OutlinedButton(
                                onClick = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    onAction(ProfileAction.StartPairing(pairingText))
                                },
                                enabled = !pairing && pairingText.isNotBlank(),
                                shape = PillShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                            ) {
                                if (pairing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(if (pairing) "正在连接电脑…" else "确认连接")
                            }
                        }
                    }
                }
            }
        }

        // 2. 同步项细粒度开关微岛（书架、阅读进度、笔记灵感）
        item(key = "sync-items-card") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SubPageSectionTitle(
                        title = "数据同步项",
                        icon = Icons.Outlined.Sync,
                        iconTint = Color(0xFF10B981),
                    )

                    // 书架同步
                    SyncItemRow(
                        icon = Icons.Outlined.AutoStories,
                        title = "书架与书籍元数据",
                        subtitle = "书籍封面、书名、作者、分类标签及全书单同步",
                        checked = syncBooksEnabled,
                        onCheckedChange = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            syncBooksEnabled = it
                        },
                    )

                    SectionDivider()

                    // 阅读进度同步
                    SyncItemRow(
                        icon = Icons.Outlined.Schedule,
                        title = "阅读进度与阅读历史",
                        subtitle = "多端无缝续读，章节定位、字数进度及累计时长",
                        checked = syncProgressEnabled,
                        onCheckedChange = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            syncProgressEnabled = it
                        },
                    )

                    SectionDivider()

                    // 笔记灵感同步
                    SyncItemRow(
                        icon = Icons.Outlined.Lightbulb,
                        title = "灵感火花与随笔批注",
                        subtitle = "灵感工坊卡片、正文划线高亮及私享书评",
                        checked = syncInspirationsEnabled,
                        onCheckedChange = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            syncInspirationsEnabled = it
                        },
                    )
                }
            }
        }

        // 3. 冲突解决策略微胶囊单选导轨
        item(key = "sync-conflict-card") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SubPageSectionTitle(
                        title = "冲突解决策略",
                        icon = Icons.Outlined.Warning,
                        iconTint = Color(0xFFF59E0B),
                    )
                    Text(
                        "当手机与电脑在离线状态下修改了同一条数据时，合并时的裁决规则：",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SelectablePill(
                            text = "以远端（电脑）为准",
                            selected = conflictStrategy == "remote",
                            onClick = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                conflictStrategy = "remote"
                            },
                            modifier = Modifier.weight(1f),
                        )
                        SelectablePill(
                            text = "以本地（手机）为准",
                            selected = conflictStrategy == "local",
                            onClick = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                conflictStrategy = "local"
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // 4. 最近同步结果微岛
        lastSyncResult?.let { result ->
            item(key = "sync-last-result-card") {
                SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (result.success) Color(0xFF10B981).copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (result.success) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                                    contentDescription = null,
                                    tint = if (result.success) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column {
                                Text(
                                    if (result.success) "最近一次同步已完成" else "同步存在异常或部分失败",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "${formatDateTime(result.timestamp)} · 耗时 ${formatSyncDuration(result.durationMs)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (result.success) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SyncStatColumn(
                                    icon = Icons.Outlined.Upload,
                                    title = "上传",
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    items = listOf(
                                        "${result.uploaded.inspirations} 条灵感",
                                        "${result.uploaded.books} 本书",
                                        "${result.uploaded.progress} 条进度",
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                SyncStatColumn(
                                    icon = Icons.Outlined.Download,
                                    title = "下载",
                                    iconTint = Color(0xFF10B981),
                                    items = listOf(
                                        "${result.downloaded.inspirations} 条灵感",
                                        "${result.downloaded.books} 本书",
                                        "${result.downloaded.progress} 条进度",
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                SyncStatColumn(
                                    icon = Icons.Outlined.Description,
                                    title = "待下载",
                                    iconTint = Color(0xFFF59E0B),
                                    items = listOf("${result.pendingDownloadCount} 本正文"),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        if (result.failedItems.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Error,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                        Text(
                                            "${result.failedItems.size} 项同步失败",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                    result.failedItems.take(8).forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                "· ${item.title?.let { "《$it》" } ?: item.type}：${item.reason}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                                            )
                                            TextButton(
                                                onClick = { onAction(ProfileAction.RetryItem(item)) },
                                                enabled = !syncing,
                                            ) {
                                                Text("重试", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { onAction(ProfileAction.RetryFailed) },
                                        enabled = !syncing,
                                        shape = PillShape,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("全部重试失败项")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. 同步日志微岛
        item(key = "sync-logs-card") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLogs = !showLogs },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SubPageSectionTitle(
                            title = "同步事件与详细日志",
                            icon = Icons.Outlined.Description,
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            trailing = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (syncLogs.isNotEmpty()) {
                                        Surface(
                                            shape = PillShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        ) {
                                            Text(
                                                "${syncLogs.size} 条",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    Icon(
                                        if (showLogs) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                        contentDescription = if (showLogs) "收起" else "展开",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                    if (showLogs) {
                        if (syncLogs.isEmpty()) {
                            Text(
                                "暂无同步记录。",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    syncLogs.forEach { line ->
                                        Text(
                                            line,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
}

/**
 * 同步项开关行
 */
@Composable
private fun SyncItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SyncStatColumn(
    icon: ImageVector,
    title: String,
    iconTint: Color,
    items: List<String>,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = iconTint)
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = iconTint,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            items.forEach { item ->
                Text(
                    item,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
