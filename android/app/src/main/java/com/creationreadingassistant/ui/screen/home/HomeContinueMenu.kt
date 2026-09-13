package com.creationreadingassistant.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.LocalComponentSpec

@Composable
internal fun MenuOverlay(
    menuView: MenuView,
    sortKey: ContinueSortKey,
    sortAsc: Boolean,
    manageMode: Boolean,
    hasReliableCreatedAt: Boolean,
    onSortOpen: () -> Unit,
    onSortBack: () -> Unit,
    onSortKey: (ContinueSortKey) -> Unit,
    onSortAsc: () -> Unit,
    onSortDesc: () -> Unit,
    onToggleManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss)
            .background(Color.Transparent),
        contentAlignment = Alignment.TopEnd,
    ) {
        SectionCard(
            modifier = Modifier
                .padding(top = 56.dp, end = 8.dp)
                .width(220.dp)
                .clickable(enabled = false) {},
            contentPadding = 0.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                when (menuView) {
                    MenuView.MORE -> {
                        MenuRow(label = "排序方式", hasChild = true, onClick = onSortOpen)
                        MenuRow(
                            label = if (manageMode) "完成管理" else "管理继续阅读",
                            onClick = onToggleManage,
                        )
                    }
                    MenuView.SORT -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onSortBack)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", modifier = Modifier.size(AppIconSize.Small))
                            Spacer(Modifier.width(8.dp))
                            Text("排序方式", style = MaterialTheme.typography.labelLarge)
                        }
                        ContinueSortKey.entries
                            .filter { it != ContinueSortKey.CREATED || hasReliableCreatedAt }
                            .forEach { key ->
                                MenuRow(
                                    label = SORT_LABELS[key] ?: key.name,
                                    selected = sortKey == key,
                                    onClick = { onSortKey(key) },
                                )
                            }
                        Spacer(Modifier.height(8.dp))
                        MenuRow(label = "升序", selected = sortAsc, onClick = onSortAsc)
                        MenuRow(label = "降序", selected = !sortAsc, onClick = onSortDesc)
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    selected: Boolean = false,
    hasChild: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        when {
            selected -> Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(AppIconSize.Small))
            hasChild -> Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(AppIconSize.Small))
        }
    }
}

@Composable
internal fun ActionBookPage(
    book: BookEntity,
    onBack: () -> Unit,
    onShowDetail: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onRemoveFromContinue: () -> Unit,
    onShelve: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SheetHandle()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回列表")
            }
            Text(
                book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Spacer(Modifier.width(48.dp))
        }
        Spacer(Modifier.height(16.dp))
        ActionButton(icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) }, label = "查看详情", onClick = onShowDetail)
        ActionButton(icon = { Icon(Icons.Outlined.Check, contentDescription = null) }, label = "标记为已读完", onClick = onMarkRead)
        ActionButton(icon = { Icon(Icons.Outlined.Close, contentDescription = null) }, label = "从继续阅读移除", onClick = onRemoveFromContinue)
        ActionButton(icon = { Icon(Icons.Outlined.Archive, contentDescription = null) }, label = "搁置本书", onClick = onShelve)
        ActionButton(icon = { Icon(Icons.Outlined.Book, contentDescription = null) }, label = "标记为未读", onClick = onMarkUnread)
        ActionButton(
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            label = "删除本地书籍",
            isDanger = true,
            onClick = onDelete,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    label: String,
    isDanger: Boolean = false,
    onClick: () -> Unit,
) {
    val spec = LocalComponentSpec.current
    Surface(
        shape = RoundedCornerShape(spec.hintRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
