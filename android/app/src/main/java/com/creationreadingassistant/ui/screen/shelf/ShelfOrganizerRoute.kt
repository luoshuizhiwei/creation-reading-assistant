@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.theme.AppError
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.viewmodel.ShelfViewModel
import com.creationreadingassistant.ui.viewmodel.TaxonomyViewModel

private val TonePurple = Color(0xFF7C3AED)
private val ToneBlue = Color(0xFF0284C7)
private val ToneAmber = Color(0xFFD97706)
private val ToneGreen = Color(0xFF059669)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShelfOrganizerRoute(
    navController: NavHostController,
    viewModel: ShelfViewModel,
    taxonomyVm: TaxonomyViewModel = hiltViewModel(),
) {
    val source by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()

    val allShelves by taxonomyVm.allShelves.collectAsStateWithLifecycle()
    val allCategories by taxonomyVm.allCategories.collectAsStateWithLifecycle()
    val allTags by taxonomyVm.allTags.collectAsStateWithLifecycle()

    var createKind by remember { mutableStateOf<String?>(null) } // "shelf", "category", "tag"
    var newNameInput by remember { mutableStateOf("") }
    var itemToDelete by remember { mutableStateOf<Triple<String, String, String>?>(null) } // kind, id, name

    AppScreenScaffold(
        title = "书架整理",
        navigationIcon = { BackButton { navController.popBackStack() } },
        actions = { TextButton(onClick = viewModel::resetShelfFilters) { Text("清除筛选") } },
    ) { viewport ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(viewport).navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. 全屏批量管理入口微岛卡片
            item {
                SectionCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { navController.navigate("shelf/organizer/select/batch") },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Checklist,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("全屏批量管理", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "批量选择书籍并移入书单、设置分类或移除缓存",
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
            }

            // 2. 书单管理微岛卡片（14dp 细腻圆角微岛卡片排版，拖动排序微把手，增删微胶囊 Chip）
            item {
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OrganizerSectionHeader(
                            icon = Icons.Outlined.Folder,
                            tint = TonePurple,
                            title = "书单管理",
                            countText = "${allShelves.size} 个书单",
                            onAdd = { createKind = "shelf"; newNameInput = "" },
                            addLabel = "+ 新建书单",
                        )
                        Spacer(Modifier.height(8.dp))

                        if (allShelves.isEmpty()) {
                            OrganizerEmptyState(
                                icon = Icons.Outlined.Folder,
                                tint = TonePurple,
                                title = "暂无自定义书单",
                                subtitle = "点击右上角「+ 新建书单」按主题分类归纳藏书",
                            )
                        } else {
                            allShelves.forEachIndexed { index, shelf ->
                                if (index > 0) OrganizerDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // 拖动排序微把手与相邻换位
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { taxonomyVm.moveShelfUp(shelf.id) },
                                            enabled = index > 0,
                                            modifier = Modifier.size(26.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.ArrowUpward,
                                                contentDescription = "上移",
                                                modifier = Modifier.size(14.dp),
                                                tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                            )
                                        }
                                        IconButton(
                                            onClick = { taxonomyVm.moveShelfDown(shelf.id) },
                                            enabled = index < allShelves.lastIndex,
                                            modifier = Modifier.size(26.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.ArrowDownward,
                                                contentDescription = "下移",
                                                modifier = Modifier.size(14.dp),
                                                tint = if (index < allShelves.lastIndex) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                            )
                                        }
                                        Icon(
                                            Icons.Outlined.DragHandle,
                                            contentDescription = "排序手柄",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        shelf.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (shelf.id == session.selectedShelfId) {
                                        ActiveFilterMicroBadge("当前筛选")
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    IconButton(
                                        onClick = { itemToDelete = Triple("shelf", shelf.id, shelf.name) },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.DeleteOutline,
                                            contentDescription = "删除书单",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. 分类管理微岛卡片（14dp 细腻圆角微岛卡片排版，拖动排序微把手，增删微胶囊 Chip）
            item {
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OrganizerSectionHeader(
                            icon = Icons.Outlined.Category,
                            tint = ToneBlue,
                            title = "分类管理",
                            countText = "${allCategories.size} 个分类",
                            onAdd = { createKind = "category"; newNameInput = "" },
                            addLabel = "+ 新建分类",
                        )
                        Spacer(Modifier.height(8.dp))

                        if (allCategories.isEmpty()) {
                            OrganizerEmptyState(
                                icon = Icons.Outlined.Category,
                                tint = ToneBlue,
                                title = "暂无分类",
                                subtitle = "点击右上角「+ 新建分类」按文体或体裁组织书籍",
                            )
                        } else {
                            allCategories.forEachIndexed { index, category ->
                                if (index > 0) OrganizerDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // 拖动排序微把手与换位
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { taxonomyVm.moveCategoryUp(category.id) },
                                            enabled = index > 0,
                                            modifier = Modifier.size(26.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.ArrowUpward,
                                                contentDescription = "上移",
                                                modifier = Modifier.size(14.dp),
                                                tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                            )
                                        }
                                        IconButton(
                                            onClick = { taxonomyVm.moveCategoryDown(category.id) },
                                            enabled = index < allCategories.lastIndex,
                                            modifier = Modifier.size(26.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.ArrowDownward,
                                                contentDescription = "下移",
                                                modifier = Modifier.size(14.dp),
                                                tint = if (index < allCategories.lastIndex) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                            )
                                        }
                                        Icon(
                                            Icons.Outlined.DragHandle,
                                            contentDescription = "排序手柄",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        category.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (category.id == session.selectedCategoryId) {
                                        ActiveFilterMicroBadge("当前筛选")
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    IconButton(
                                        onClick = { itemToDelete = Triple("category", category.id, category.name) },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.DeleteOutline,
                                            contentDescription = "删除分类",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. 标签管理微岛卡片（紧凑雅致增删微胶囊 Chip）
            item {
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OrganizerSectionHeader(
                            icon = Icons.AutoMirrored.Outlined.Label,
                            tint = ToneAmber,
                            title = "标签管理",
                            countText = "${allTags.size} 个标签",
                            onAdd = { createKind = "tag"; newNameInput = "" },
                            addLabel = "+ 新建标签",
                        )
                        Spacer(Modifier.height(10.dp))

                        if (allTags.isEmpty()) {
                            OrganizerEmptyState(
                                icon = Icons.AutoMirrored.Outlined.Label,
                                tint = ToneAmber,
                                title = "暂无标签",
                                subtitle = "点击右上角「+ 新建标签」添加灵活的多维检索标记",
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                allTags.forEach { tag ->
                                    val isFilterSelected = tag.id in session.selectedTagIds
                                    Box(
                                        modifier = Modifier
                                            .clip(PillShape)
                                            .background(
                                                if (isFilterSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            )
                                            .border(
                                                0.5.dp,
                                                if (isFilterSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                                PillShape,
                                            )
                                            .clickable { viewModel.toggleSelectedTag(tag.id) }
                                            .padding(start = 12.dp, top = 3.dp, end = 4.dp, bottom = 3.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "#${tag.name}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isFilterSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isFilterSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { itemToDelete = Triple("tag", tag.id, tag.name) },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Close,
                                                    contentDescription = "删除标签",
                                                    modifier = Modifier.size(13.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
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

            // 5. 会话筛选与排序视图微岛卡片（保持 100% 导航兼容）
            item {
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        OrganizerRow(
                            icon = Icons.Outlined.FilterList,
                            title = "阅读状态",
                            value = statusLabel(session.statusFilter),
                            tint = ToneGreen,
                        ) {
                            navController.navigate("shelf/organizer/select/status")
                        }
                        OrganizerDivider()
                        OrganizerRow(
                            icon = Icons.Outlined.Folder,
                            title = "书单筛选",
                            value = source.library.shelves.firstOrNull { it.id == session.selectedShelfId }?.name ?: "全部",
                            tint = TonePurple,
                        ) {
                            navController.navigate("shelf/organizer/select/shelf")
                        }
                        OrganizerDivider()
                        OrganizerRow(
                            icon = Icons.Outlined.Category,
                            title = "分类筛选",
                            value = source.library.categories.firstOrNull { it.id == session.selectedCategoryId }?.name ?: "全部",
                            tint = ToneBlue,
                        ) {
                            navController.navigate("shelf/organizer/select/category")
                        }
                        OrganizerDivider()
                        OrganizerRow(
                            icon = Icons.AutoMirrored.Outlined.Label,
                            title = "标签筛选",
                            value = selectedTagSummary(session.selectedTagIds, source.library.tags.associate { it.id to it.name }),
                            tint = ToneAmber,
                        ) {
                            navController.navigate("shelf/organizer/select/tag")
                        }
                        OrganizerDivider()
                        OrganizerRow(
                            icon = Icons.AutoMirrored.Outlined.Sort,
                            title = "排序方式",
                            value = sortLabel(session.sortMode),
                            tint = MaterialTheme.colorScheme.primary,
                        ) {
                            navController.navigate("shelf/organizer/select/sort")
                        }
                    }
                }
            }

            // 6. 底部轻量微岛说明卡片（柔和底色、0.6dp 发丝描边、左侧信息微图标、12dp 圆角）
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "筛选仅在本次使用中保留；排序和自定义分类标签会长期保存。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
    }

    // 创建对话框（东方纸墨规范微岛输入框与胶囊按键）
    if (createKind != null) {
        val kindLabel = when (createKind) {
            "shelf" -> "新建书单"
            "category" -> "新建分类"
            else -> "新建标签"
        }
        GlassAlertDialog(
            onDismissRequest = { createKind = null },
            title = { Text(kindLabel, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newNameInput,
                        onValueChange = { newNameInput = it },
                        placeholder = {
                            Text(
                                "请输入名称",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newNameInput.trim()
                        if (name.isNotBlank()) {
                            when (createKind) {
                                "shelf" -> taxonomyVm.createShelf(name)
                                "category" -> taxonomyVm.createCategory(name)
                                "tag" -> taxonomyVm.createTag(name)
                            }
                        }
                        createKind = null
                    },
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { createKind = null }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    // 删除确认对话框
    if (itemToDelete != null) {
        val (kind, id, name) = itemToDelete!!
        val label = when (kind) {
            "shelf" -> "书单"
            "category" -> "分类"
            else -> "标签"
        }
        GlassAlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("删除$label？", fontWeight = FontWeight.SemiBold) },
            text = { Text("确定要删除$label「$name」吗？书籍本身不会被删除。") },
            confirmButton = {
                Button(
                    onClick = {
                        when (kind) {
                            "shelf" -> taxonomyVm.deleteShelf(id)
                            "category" -> taxonomyVm.deleteCategory(id)
                            "tag" -> taxonomyVm.deleteTag(id)
                        }
                        itemToDelete = null
                    },
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = AppError),
                ) {
                    Text("删除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun OrganizerSectionHeader(
    icon: ImageVector,
    tint: Color,
    title: String,
    countText: String,
    onAdd: () -> Unit,
    addLabel: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(countText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Spacer(Modifier.weight(1f))
        // 增删微胶囊 Chip 紧凑雅致
        Box(
            modifier = Modifier
                .clip(PillShape)
                .background(tint.copy(alpha = 0.12f))
                .border(0.5.dp, tint.copy(alpha = 0.35f), PillShape)
                .clickable(onClick = onAdd)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                addLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = tint,
            )
        }
    }
}

@Composable
private fun ActiveFilterMicroBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
    }
}

// 分区空状态精致引导占位微岛卡片（微彩圆角底座与轻柔引导文案）
@Composable
private fun OrganizerEmptyState(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(vertical = 14.dp, horizontal = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.12f))
                    .border(0.5.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun OrganizerRow(
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(AppIconSize.Small),
        )
    }
}

private fun sortLabel(value: ShelfSortMode): String = when (value) {
    ShelfSortMode.RECENT -> "最近阅读"
    ShelfSortMode.IMPORTED -> "最近导入"
    ShelfSortMode.TITLE -> "书名"
    ShelfSortMode.PROGRESS -> "阅读进度"
}

internal fun selectedTagSummary(selectedIds: Set<String>, names: Map<String, String>): String = when (selectedIds.size) {
    0 -> "全部"
    1 -> names[selectedIds.first()] ?: "1 个标签"
    else -> "${selectedIds.size} 个标签"
}
