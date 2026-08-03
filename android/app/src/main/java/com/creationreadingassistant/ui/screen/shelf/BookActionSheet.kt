package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.theme.AppError
import com.creationreadingassistant.ui.theme.LocalComponentSpec

// ===================== 底部弹层：书籍操作 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookActionSheet(
    book: BookEntity,
    progressById: Map<String, ReadingProgressEntity>,
    onDismiss: () -> Unit,
    onContinue: (BookEntity) -> Unit,
    onDownload: (BookEntity) -> Unit,
    onRepair: (BookEntity) -> Unit,
    onOpenDetail: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCover(book = book, percent = progressFor(progressById, book.id), modifier = Modifier.size(48.dp, 66.dp), fallback = { ShelfCoverFallback(book) })
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${book.author ?: "作者未知"} · ${book.readiness().label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            val r = book.readiness()
            if (r.tone == ReadinessTone.READY) {
                ActionRow(icon = Icons.AutoMirrored.Filled.MenuBook, title = "继续阅读", subtitle = "从上次保存的位置打开", primary = true, onClick = { onContinue(book) })
            } else {
                if (r.tone == ReadinessTone.CLOUD) {
                    ActionRow(icon = Icons.Filled.Download, title = "下载正文", subtitle = "保存到本机后离线阅读", onClick = { onDownload(book) })
                }
            }
            ActionRow(
                icon = Icons.Filled.Refresh,
                title = "重新定位文件",
                subtitle = if (r.tone == ReadinessTone.READY) "更换本地文件并保留阅读记录" else "修复缺失的本地正文",
                onClick = { onRepair(book) },
            )
            ActionRow(icon = Icons.Outlined.Info, title = "书籍详情与管理", subtitle = "编辑信息、封面、分类和书单", onClick = { onOpenDetail(book.id) })
            ActionRow(icon = Icons.Filled.Delete, title = "删除书籍", subtitle = "同时移除本机正文和阅读数据", danger = true, onClick = { onDelete(book.id) })
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    primary: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = when {
                danger -> AppError
                primary -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (danger) AppError else MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}
