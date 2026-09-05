package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

// 暖金品牌色
private val WEBDAV_WARM_GOLD = Color(0xFFD97706)

// ============================== WebDAV 备份与恢复页 ==============================

@Composable
internal fun WebDavSubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)

    val initialUrl = state.webDavConfig?.url ?: ""
    val initialUser = state.webDavConfig?.user ?: ""
    val initialPass = state.webDavConfig?.pass ?: ""
    val backups = state.webDavBackups
    val hasSavedPass = initialPass.isNotBlank()

    var url by remember { mutableStateOf(initialUrl) }
    var user by remember { mutableStateOf(initialUser) }
    var pass by remember { mutableStateOf(initialPass) }
    var passVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.webDavConfig) {
        url = state.webDavConfig?.url ?: ""
        user = state.webDavConfig?.user ?: ""
        pass = state.webDavConfig?.pass ?: ""
    }

    LaunchedEffect(Unit) {
        if (initialUrl.isNotBlank()) onAction(ProfileAction.RefreshBackups)
    }

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 1. 顶部安全说明微岛
        item(key = "webdav-note") {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateEnter(reducedMotion = reducedMotion),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(WEBDAV_WARM_GOLD.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.CloudUpload,
                            contentDescription = null,
                            tint = WEBDAV_WARM_GOLD,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "云端独立冷备份",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "备份目录固定为「.creation-reading-assistant/」，自动打包同步书库元数据、阅读历史与进度。凭证仅存在手机本地沙箱，AI Key 绝不上云。",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }
        }

        // 2. WebDAV 服务器配置微岛
        item(key = "webdav-config-card") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // 独立微彩圆角底座（暖金云朵图标）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(WEBDAV_WARM_GOLD.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Cloud,
                                    contentDescription = null,
                                    tint = WEBDAV_WARM_GOLD,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column {
                                Text(
                                    "WebDAV 存储节点",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "兼容坚果云、Nextcloud、群晖 NAS 等标准 WebDAV",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // 配置状态微胶囊
                        Surface(
                            shape = PillShape,
                            color = if (hasSavedPass) Color(0xFF10B981).copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                            border = BorderStroke(
                                0.8.dp,
                                if (hasSavedPass) Color(0xFF10B981).copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (hasSavedPass) Color(0xFF10B981) else Color.Gray),
                                )
                                Text(
                                    if (hasSavedPass) "已配置" else "未连接",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (hasSavedPass) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    SectionDivider()

                    // 服务器地址、账号、密码输入框圆润微岛卡片化（12dp 圆角、微半透背景）
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "服务器基址 (URL)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = textFieldColors(),
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "用户名 / 认证账户",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        OutlinedTextField(
                            value = user,
                            onValueChange = { user = it },
                            placeholder = { Text("user@example.com") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = textFieldColors(),
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "密码 / 应用专用 Token",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it },
                            placeholder = {
                                Text(if (hasSavedPass) "已保存凭据；留空则继续沿用" else "输入密码或应用密码")
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passVisible = !passVisible }) {
                                    Icon(
                                        if (passVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (passVisible) "隐藏密码" else "显示密码",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = textFieldColors(),
                        )
                    }

                    // 凭证保存状态微胶囊说明条
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (hasSavedPass) Color(0xFF10B981).copy(alpha = 0.09f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        border = BorderStroke(
                            0.8.dp,
                            if (hasSavedPass) Color(0xFF10B981).copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                if (hasSavedPass) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                                contentDescription = null,
                                tint = if (hasSavedPass) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                if (hasSavedPass) "WebDAV 账户密码已安全加密存储于本设备沙箱。"
                                else "尚未保存 WebDAV 凭据，请录入后点击保存。",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = if (hasSavedPass) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // 主操作：保存 WebDAV 设置微胶囊按钮
                    Button(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            onAction(ProfileAction.SaveWebDav(url.trim(), user.trim(), pass))
                        },
                        shape = PillShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "保存 WebDAV 设置",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }

                    // “测试连接”与“立即备份/恢复”操作微胶囊化，带实时状态与时间戳微胶囊
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    onAction(ProfileAction.TestWebDav)
                                },
                                enabled = url.isNotBlank(),
                                shape = PillShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                            ) {
                                Icon(Icons.Outlined.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("测试连接")
                            }
                            OutlinedButton(
                                onClick = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    onAction(ProfileAction.UploadBackup)
                                },
                                enabled = url.isNotBlank(),
                                shape = PillShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                            ) {
                                Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("立即备份")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    onAction(ProfileAction.RefreshBackups)
                                },
                                enabled = url.isNotBlank(),
                                shape = PillShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("刷新列表")
                            }
                            OutlinedButton(
                                onClick = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    onAction(ProfileAction.ClearWebDav)
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
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("清除凭据")
                            }
                        }
                    }
                }
            }
        }

        // 3. 远程备份快照列表微岛
        item(key = "webdav-backups-card") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SubPageSectionTitle(
                        title = "云端备份快照",
                        icon = Icons.Outlined.FolderZip,
                        iconTint = WEBDAV_WARM_GOLD,
                        trailing = {
                            Surface(
                                shape = PillShape,
                                color = WEBDAV_WARM_GOLD.copy(alpha = 0.12f),
                                border = BorderStroke(0.6.dp, WEBDAV_WARM_GOLD.copy(alpha = 0.35f)),
                            ) {
                                Text(
                                    "${backups.size} 份快照",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = WEBDAV_WARM_GOLD,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        },
                    )

                    if (backups.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.CloudDone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(28.dp),
                                )
                                Text(
                                    "暂无云端备份文件",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "点击上方「立即备份」即可上传当前书库与进度快照",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            backups.take(15).forEach { file ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
                                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        // 独立微彩底座
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(WEBDAV_WARM_GOLD.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Outlined.FolderZip,
                                                contentDescription = null,
                                                tint = WEBDAV_WARM_GOLD,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }

                                        // 文件名与时间戳微胶囊
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                file.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Spacer(Modifier.height(3.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                // 大小胶囊
                                                Surface(
                                                    shape = PillShape,
                                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                                ) {
                                                    Text(
                                                        formatBytes(file.size),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                    )
                                                }
                                                // 修改时间
                                                Text(
                                                    file.lastModified,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }

                                        // 恢复微胶囊按钮
                                        OutlinedButton(
                                            onClick = {
                                                haptic(HapticFeedbackType.TextHandleMove)
                                                onAction(ProfileAction.DownloadRestore(file.name))
                                            },
                                            shape = PillShape,
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                            modifier = Modifier.height(32.dp),
                                        ) {
                                            Icon(Icons.Outlined.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("恢复", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
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
}
