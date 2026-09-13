package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.LocalComponentSpec

// ===================== 导入来源 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportSourceSheet(
    onOpenLibrary: () -> Unit,
    onSelectFiles: () -> Unit,
    onImportFromDesktop: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // 与 BookDetailSheet 等其它 shelf 弹层同一宽度口径（平板/折叠屏居中，窄屏无影响）
        sheetMaxWidth = LocalLayoutTokens.current.contentMaxWidth,
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 水平内边距走布局令牌（同一 shell 规则），不再各自写 18dp 魔数
                .padding(horizontal = LocalLayoutTokens.current.pageHorizontal)
                .padding(bottom = 28.dp),
        ) {
            Text("导入书籍", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "可以一次选择多本书，也可以浏览已授权的书籍目录后批量导入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            // 格式微胶囊标签（东方纸墨柔和低饱和调色板）
            // 方案 12：格式链（真源 / 解析 / 阅读 / 搜索 / TTS）未闭环前不宣称 PDF。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("支持格式", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FormatCapsule("EPUB", FormatPurple)
                FormatCapsule("TXT", FormatBlue)
                FormatCapsule("MD", FormatGreen)
            }

            // 目录选择微岛卡片
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ImportSourceRow(
                        icon = Icons.Outlined.Folder,
                        title = "我的书籍目录",
                        description = "在 App 内浏览、智能识别和批量导入",
                        tint = FormatBlue,
                        onClick = onOpenLibrary,
                    )
                    OrganizerDivider()
                    ImportSourceRow(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        title = "从系统选择文件",
                        description = "临时选择一本或多本书",
                        tint = FormatPurple,
                        onClick = onSelectFiles,
                    )
                    OrganizerDivider()
                    ImportSourceRow(
                        icon = Icons.Outlined.Computer,
                        title = "从电脑导入",
                        description = "获取桌面端已同步的书籍",
                        tint = FormatGreen,
                        onClick = onImportFromDesktop,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ImportSourceRow(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 32dp 微彩底座
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(AppIconSize.Small),
        )
    }
}
