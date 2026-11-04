package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.CompactItemDivider
import com.creationreadingassistant.ui.components.CompactSettingRow
import com.creationreadingassistant.ui.components.CompactSettingsGroup
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion

// ============================== 隐私安全 ==============================

private data class PrivacyCommitment(
    val title: String,
    val badge: String,
    val summary: String,
    val icon: ImageVector,
    val tint: Color,
)

/**
 * 四条承诺收进同一张分组卡：一个语义分区一张卡，不再一条信息一张卡。
 * 细节长句压到一行副标题，徽章词弱化成行尾文本。
 */
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
                summary = "原文、进度、笔记只存本机，离线可用",
                icon = Icons.Outlined.Security,
                tint = Color(0xFF0284C7),
            ),
            PrivacyCommitment(
                title = "同步可控",
                badge = "手动授权",
                summary = "需你扫码或主动配置后才发起请求",
                icon = Icons.Outlined.Wifi,
                tint = Color(0xFF10B981),
            ),
            PrivacyCommitment(
                title = "密钥隔离",
                badge = "加密存储",
                summary = "API Key 与凭证存于系统加密区，不同步",
                icon = Icons.Outlined.Key,
                tint = Color(0xFFF59E0B),
            ),
            PrivacyCommitment(
                title = "路径脱敏",
                badge = "同步前过滤",
                summary = "设备绝对路径与临时授权标识自动脱敏",
                icon = Icons.Outlined.Storage,
                tint = Color(0xFF8B5CF6),
            ),
        )
    }

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            CompactSettingsGroup(
                title = "隐私与安全承诺",
                icon = Icons.Outlined.Security,
                tint = Color(0xFF0284C7),
                description = "本地优先：非必要不采集、不上传，数据只留在你的设备上。",
                modifier = Modifier.animateEnter(reducedMotion = reducedMotion),
            ) {
                commitments.forEachIndexed { index, commitment ->
                    if (index > 0) CompactItemDivider()
                    CompactSettingRow(
                        title = commitment.title,
                        subtitle = commitment.summary,
                        leading = {
                            Icon(
                                imageVector = commitment.icon,
                                contentDescription = null,
                                tint = commitment.tint,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailing = {
                            Text(
                                text = commitment.badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }

        item {
            DegradedNote(
                text = "元数据存于本地 Room 数据库；文件资源遵循 Android Scoped Storage 隔离存放，其他应用无权直接读取。",
                modifier = Modifier.animateEnter(reducedMotion = reducedMotion),
            )
        }
    }
}
