package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.listItemEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion

// ============================== 隐私安全 ==============================

private data class PrivacyCommitment(
    val title: String,
    val badge: String,
    val body: String,
    val icon: ImageVector,
    val tint: Color,
)

@Composable
internal fun PrivacySubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()

    val commitments = remember {
        listOf(
            PrivacyCommitment(
                title = "本地优先",
                badge = "本地沙箱",
                body = "没有云端账号系统。书籍原文、灵感草稿、阅读进度与个人笔记默认全部存放于设备本地沙箱，离线可用且不依赖外部服务。",
                icon = Icons.Outlined.Security,
                tint = Color(0xFF0284C7), // 淡蓝盾牌
            ),
            PrivacyCommitment(
                title = "同步可控",
                badge = "手动握手",
                body = "局域网同步需要你在同一 Wi-Fi 下主动扫码授权；WebDAV 仅在你自主配置第三方私有存储（如坚果云/Nextcloud）后才发起备份请求。",
                icon = Icons.Outlined.Wifi,
                tint = Color(0xFF10B981), // 翡翠绿WiFi
            ),
            PrivacyCommitment(
                title = "密钥隔离",
                badge = "密钥不出境",
                body = "AI 服务 API Key 与 WebDAV 认证凭证均通过 Android 加密沙箱持久化存储，绝不参与多端同步、快照导出或云端备份。",
                icon = Icons.Outlined.Key,
                tint = Color(0xFFF59E0B), // 暖金钥匙
            ),
            PrivacyCommitment(
                title = "路径隔离",
                badge = "隐私脱敏",
                body = "系统绝对路径（/storage/emulated/0/...）、readerPreview、临时 SAF 授权 URI 等设备私有标识在同步前自动脱敏过滤，不留痕迹。",
                icon = Icons.Outlined.Storage,
                tint = Color(0xFF8B5CF6), // 紫晶存储
            ),
        )
    }

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 1. 顶部承诺引言微岛
        item {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubPageSectionTitle(
                        title = "隐私与安全承诺",
                        icon = Icons.Outlined.Security,
                        iconTint = Color(0xFF0284C7),
                    )
                    Text(
                        "我们坚信阅读是一项私密且纯粹的活动。应用自架构之初便坚持「本地优先」原则，杜绝非必要的数据采集与上传，全方位守护你的数据自主权。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                }
            }
        }

        // 2. 4 大隐私微岛卡片
        commitments.forEachIndexed { index, commitment ->
            item(key = commitment.title) {
                Box(Modifier.fillMaxWidth().listItemEnter(index, reducedMotion)) {
                    PrivacyCardItem(commitment = commitment)
                }
            }
        }

        // 3. 底部沙箱合规注释
        item {
            DegradedNote(
                text = "所有核心元数据采用本地 SQLite (Room) 引擎驱动，文件资源严格遵守 Android Scoped Storage 规范隔离存放，其他第三方应用无权直接嗅探读取。",
                modifier = Modifier.animateEnter(reducedMotion = reducedMotion),
            )
        }
    }
}

@Composable
private fun PrivacyCardItem(
    commitment: PrivacyCommitment,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.75f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    // 36dp 独立微彩圆角底座
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(commitment.tint.copy(alpha = 0.12f))
                            .border(0.6.dp, commitment.tint.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = commitment.icon,
                            contentDescription = null,
                            tint = commitment.tint,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Text(
                        text = commitment.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // 微安全胶囊标签
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = commitment.tint.copy(alpha = 0.10f),
                    border = BorderStroke(0.5.dp, commitment.tint.copy(alpha = 0.3f)),
                ) {
                    Text(
                        text = commitment.badge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.5.sp,
                        ),
                        color = commitment.tint,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Text(
                text = commitment.body,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
