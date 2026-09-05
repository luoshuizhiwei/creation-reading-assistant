@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.theme.AppError
import com.creationreadingassistant.ui.theme.AppSuccess
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.ShelfViewModel
import com.creationreadingassistant.ui.viewmodel.TaxonomyViewModel

private val TonePurple = Color(0xFF7C3AED)
private val ToneBlue = Color(0xFF0284C7)
private val ToneAmber = Color(0xFFD97706)
private val ToneGreen = Color(0xFF059669)

@Composable
internal fun ShelfSelectionRoute(
    kind: String,
    navController: NavHostController,
    viewModel: ShelfViewModel,
    taxonomyVm: TaxonomyViewModel = hiltViewModel(),
) {
    val source by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val haptic = rememberHaptic(rememberReducedMotion())

    // 全屏批量选择管理态
    if (kind == "batch") {
        val allBooks = source.library.books
        var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
        var showShelfDialog by remember { mutableStateOf(false) }
        var showCategoryDialog by remember { mutableStateOf(false) }
        var confirmDeleteDialog by remember { mutableStateOf(false) }

        val allShelves by taxonomyVm.allShelves.collectAsStateWithLifecycle()
        val allCategories by taxonomyVm.allCategories.collectAsStateWithLifecycle()

        AppScreenScaffold(
            title = "批量管理",
            navigationIcon = { BackButton { navController.popBackStack() } },
            actions = {
                // 顶部已选数量高亮微胶囊
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                        .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), PillShape)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        "已选 ${selectedIds.size} 本",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(6.dp))
                // 全选/反选胶囊按钮
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable {
                            haptic(HapticFeedbackType.TextHandleMove)
                            selectedIds = if (selectedIds.size == allBooks.size) emptySet() else allBooks.map { it.id }.toSet()
                        }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(
                        if (selectedIds.size == allBooks.size) "清空" else "全选",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable {
                            haptic(HapticFeedbackType.TextHandleMove)
                            val allIds = allBooks.map { it.id }.toSet()
                            selectedIds = allIds - selectedIds
                        }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text("反选", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                }
            },
        ) { viewport ->
            Box(modifier = Modifier.fillMaxSize().padding(viewport)) {
                if (allBooks.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    ) {
                        LineArtBook(modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("书架暂无书籍", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(allBooks, key = { it.id }) { book ->
                            val isSelected = book.id in selectedIds
                            BatchBookItemCard(
                                book = book,
                                isSelected = isSelected,
                                onToggle = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    selectedIds = if (isSelected) selectedIds - book.id else selectedIds + book.id
                                },
                            )
                        }
                    }
                }

                // 底部浮动批量操作栏微岛化（移入书单、设置分类、批量删除均配备 32dp 微彩底座图标与柔和层级）
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val hasSelection = selectedIds.isNotEmpty()
                        FloatingBatchActionItem(
                            icon = Icons.Outlined.Folder,
                            label = "移入书单",
                            tint = TonePurple,
                            enabled = hasSelection,
                            onClick = { showShelfDialog = true },
                        )
                        FloatingBatchActionItem(
                            icon = Icons.Outlined.Category,
                            label = "设置分类",
                            tint = ToneBlue,
                            enabled = hasSelection,
                            onClick = { showCategoryDialog = true },
                        )
                        FloatingBatchActionItem(
                            icon = Icons.Outlined.DeleteOutline,
                            label = "批量删除",
                            tint = AppError,
                            enabled = hasSelection,
                            onClick = { confirmDeleteDialog = true },
                        )
                    }
                }
            }

            // 移入书单弹层
            if (showShelfDialog) {
                GlassAlertDialog(
                    onDismissRequest = { showShelfDialog = false },
                    title = { Text("移入书单", fontWeight = FontWeight.SemiBold) },
                    text = {
                        if (allShelves.isEmpty()) {
                            Text("暂无可用书单，请先在书架整理中创建书单。")
                        } else {
                            Column {
                                allShelves.forEach { shelf ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                taxonomyVm.addBooksToShelf(selectedIds.toList(), shelf.id)
                                                showShelfDialog = false
                                                selectedIds = emptySet()
                                            }
                                            .padding(vertical = 10.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Outlined.Bookmark, contentDescription = null, tint = TonePurple, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(shelf.name, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showShelfDialog = false }) { Text("关闭") }
                    },
                )
            }

            // 设置分类弹层
            if (showCategoryDialog) {
                GlassAlertDialog(
                    onDismissRequest = { showCategoryDialog = false },
                    title = { Text("设置分类", fontWeight = FontWeight.SemiBold) },
                    text = {
                        if (allCategories.isEmpty()) {
                            Text("暂无可用分类，请先在书架整理中创建分类。")
                        } else {
                            Column {
                                allCategories.forEach { category ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                taxonomyVm.setCategoryForBooks(selectedIds.toList(), category.id)
                                                showCategoryDialog = false
                                                selectedIds = emptySet()
                                            }
                                            .padding(vertical = 10.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Outlined.Category, contentDescription = null, tint = ToneBlue, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(category.name, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showCategoryDialog = false }) { Text("关闭") }
                    },
                )
            }

            // 批量删除确认弹层
            if (confirmDeleteDialog) {
                GlassAlertDialog(
                    onDismissRequest = { confirmDeleteDialog = false },
                    title = { Text("批量删除书籍？", fontWeight = FontWeight.SemiBold) },
                    text = { Text("确定要从书架删除选中的 ${selectedIds.size} 本书籍吗？") },
                    confirmButton = {
                        Button(
                            onClick = {
                                selectedIds.forEach(viewModel::deleteBook)
                                confirmDeleteDialog = false
                                selectedIds = emptySet()
                            },
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(containerColor = AppError),
                        ) {
                            Text("删除", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDeleteDialog = false }) { Text("取消") }
                    },
                )
            }
        }
        return
    }

    // 默认单选/多选筛选与排序模式（保持 100% 原始逻辑与事件签名）
    val title = when (kind) {
        "status" -> "阅读状态"
        "shelf" -> "书单"
        "category" -> "分类"
        "tag" -> "标签"
        else -> "排序"
    }
    val options: List<Pair<String, String>> = when (kind) {
        "status" -> listOf("ALL" to "全部", "READING" to "在读", "SHELVED" to "搁置", "COMPLETED" to "已完成", "UNREAD" to "未开始")
        "shelf" -> listOf("" to "全部") + source.library.shelves.map { it.id to it.name }
        "category" -> listOf("" to "全部") + source.library.categories.map { it.id to it.name }
        "tag" -> listOf("" to "全部标签") + source.library.tags.map { it.id to it.name }
        else -> listOf("RECENT" to "最近阅读", "IMPORTED" to "最近导入", "TITLE" to "书名", "PROGRESS" to "阅读进度")
    }
    val selected = when (kind) {
        "status" -> session.statusFilter.name
        "shelf" -> session.selectedShelfId
        "category" -> session.selectedCategoryId
        "tag" -> ""
        else -> session.sortMode.name
    }

    AppScreenScaffold(
        title = title,
        navigationIcon = { BackButton { navController.popBackStack() } },
        actions = {
            if (kind == "tag") {
                // 顶部已选数量高亮微胶囊
                if (session.selectedTagIds.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                            .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), PillShape)
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "已选 ${session.selectedTagIds.size} 项",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable { viewModel.clearSelectedTags() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("清空", style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(onClick = { navController.popBackStack() }) { Text("完成") }
            }
        },
    ) { viewport ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(viewport),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        options.forEachIndexed { index, (id, label) ->
                            if (index > 0) OrganizerDivider()
                            val isSelected = if (kind == "tag") {
                                if (id.isEmpty()) session.selectedTagIds.isEmpty() else id in session.selectedTagIds
                            } else {
                                selected == id
                            }
                            val optionIcon = when (kind) {
                                "status" -> Icons.Outlined.FilterList
                                "shelf" -> Icons.Outlined.Folder
                                "category" -> Icons.Outlined.Category
                                "tag" -> Icons.AutoMirrored.Outlined.Label
                                else -> Icons.AutoMirrored.Outlined.Sort
                            }
                            val tintColor = when (kind) {
                                "status" -> ToneGreen
                                "shelf" -> TonePurple
                                "category" -> ToneBlue
                                "tag" -> ToneAmber
                                else -> MaterialTheme.colorScheme.primary
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            when (kind) {
                                                "status" -> ShelfStatusFilter.entries.firstOrNull { it.name == id }?.let(viewModel::setStatusFilter)
                                                "shelf" -> viewModel.setSelectedShelf(id)
                                                "category" -> viewModel.setSelectedCategory(id)
                                                "tag" -> if (id.isEmpty()) viewModel.clearSelectedTags() else viewModel.toggleSelectedTag(id)
                                                else -> viewModel.setSortMode(ShelfSortMode.valueOf(id))
                                            }
                                            if (kind != "tag") navController.popBackStack()
                                        },
                                    )
                                    .padding(horizontal = 4.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // 32dp 微彩底座图标
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(
                                            if (isSelected) tintColor.copy(alpha = 0.16f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        optionIcon,
                                        contentDescription = null,
                                        tint = if (isSelected) tintColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isSelected) {
                                    Icon(Icons.Outlined.Check, contentDescription = "已选择", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 批量模式书籍卡片（14dp 细腻圆角微岛卡片）
@Composable
private fun BatchBookItemCard(
    book: BookEntity,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    SectionCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 选择状态指示徽章（32dp 微彩底座）
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (book.format.isNotBlank()) {
                        FormatCapsule(book.format.uppercase())
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = book.author ?: "未知作者",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// 底部浮动微岛操作项（32dp 微彩底座图标与柔和层级）
@Composable
private fun FloatingBatchActionItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) {
                haptic(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        // 32dp 微彩底座
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (enabled) tint.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}
