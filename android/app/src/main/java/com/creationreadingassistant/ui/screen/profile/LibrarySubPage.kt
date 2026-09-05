package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.TaxonomyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ============================== 书库管理（标签 / 分类 / 书单）微岛化 ==============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibrarySubPage(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
    page: ProfileSubPage,
) {
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val taxonomyVm: TaxonomyViewModel = hiltViewModel()
    val allTags by taxonomyVm.allTags.collectAsStateWithLifecycle()
    val allCategories by taxonomyVm.allCategories.collectAsStateWithLifecycle()
    val allShelves by taxonomyVm.allShelves.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val books = state.libraryState.books

    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var editingItem by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editName by remember { mutableStateOf("") }
    var deletingItem by remember { mutableStateOf<Pair<String, String>?>(null) }

    var expandedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val linkedBookIds = remember { SnapshotStateMap<String, List<String>>() }
    var sorting by remember(page) { mutableStateOf(false) }
    val bookMap = remember(books) { books.associateBy { it.id } }

    // 主题色与图标配置规范：书单-淡紫，分类-天蓝，标签-暖橙
    val (typeName, themeColor, typeIcon) = when (page) {
        ProfileSubPage.SHELVES -> Triple("书单", Color(0xFF8B5CF6), Icons.Outlined.Book)
        ProfileSubPage.CATEGORIES -> Triple("分类", Color(0xFF0EA5E9), Icons.Outlined.Folder)
        ProfileSubPage.TAGS -> Triple("标签", Color(0xFFF97316), Icons.Outlined.Sell)
        else -> Triple("项目", MaterialTheme.colorScheme.primary, Icons.Outlined.Book)
    }

    fun loadLinkedBooks(id: String) {
        scope.launch(Dispatchers.IO) {
            val ids = when (page) {
                ProfileSubPage.TAGS -> taxonomyVm.getBookIdsByTag(id)
                ProfileSubPage.CATEGORIES -> taxonomyVm.getBookIdsByCategory(id)
                ProfileSubPage.SHELVES -> taxonomyVm.getBookIdsByShelf(id)
                else -> emptyList()
            }
            linkedBookIds[id] = ids
        }
    }

    fun toggleExpanded(id: String) {
        if (expandedIds.contains(id)) {
            expandedIds = expandedIds - id
        } else {
            expandedIds = expandedIds + id
            if (!linkedBookIds.containsKey(id)) loadLinkedBooks(id)
        }
    }

    fun moveItem(id: String, up: Boolean) {
        when (page) {
            ProfileSubPage.TAGS -> if (up) taxonomyVm.moveTagUp(id) else taxonomyVm.moveTagDown(id)
            ProfileSubPage.CATEGORIES -> if (up) taxonomyVm.moveCategoryUp(id) else taxonomyVm.moveCategoryDown(id)
            ProfileSubPage.SHELVES -> if (up) taxonomyVm.moveShelfUp(id) else taxonomyVm.moveShelfDown(id)
            else -> Unit
        }
    }

    val items: List<Pair<String, String>> = when (page) {
        ProfileSubPage.TAGS -> allTags.map { it.id to it.name }
        ProfileSubPage.CATEGORIES -> allCategories.map { it.id to it.name }
        ProfileSubPage.SHELVES -> allShelves.map { it.id to it.name }
        else -> emptyList()
    }

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 1. 顶部操作与概览微岛
        item(key = "library_overview_header") {
            SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SubPageSectionTitle(
                        title = "${typeName}管理",
                        icon = typeIcon,
                        iconTint = themeColor,
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (items.size > 1) {
                                    SelectablePill(
                                        text = if (sorting) "完成排序" else "调整顺序",
                                        selected = sorting,
                                        onClick = { sorting = !sorting },
                                    )
                                }
                                // 新建微胶囊按钮
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = themeColor.copy(alpha = 0.12f),
                                    border = BorderStroke(0.8.dp, themeColor.copy(alpha = 0.35f)),
                                    modifier = Modifier.clickable {
                                        haptic(HapticFeedbackType.TextHandleMove)
                                        newName = ""
                                        showCreateDialog = true
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Add,
                                            contentDescription = null,
                                            tint = themeColor,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = "新建",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = themeColor,
                                        )
                                    }
                                }
                            }
                        },
                    )

                    Text(
                        text = if (sorting) {
                            "正在调整顺序：使用箭头微调前后位序，顺序将实时同步至书架"
                        } else {
                            "共 ${items.size} 项 · 归类整理书籍与阅读维度，点击条目可查看关联书目"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 2. 列表项微岛化卡片展示
        if (items.isNotEmpty()) {
            items.forEachIndexed { index, (id, name) ->
                item(key = id) {
                    val expanded = expandedIds.contains(id)
                    val entityTag = if (page == ProfileSubPage.TAGS) allTags.find { it.id == id } else null
                    val entityCategory = if (page == ProfileSubPage.CATEGORIES) allCategories.find { it.id == id } else null

                    SectionCard(
                        modifier = Modifier.animateEnter(reducedMotion = reducedMotion),
                        onClick = if (sorting) null else {
                            {
                                haptic(HapticFeedbackType.TextHandleMove)
                                toggleExpanded(id)
                            }
                        },
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // 32dp 独立分色圆角微底座
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(themeColor.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = typeIcon,
                                        contentDescription = null,
                                        tint = themeColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                // 名称与副信息
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (page == ProfileSubPage.TAGS && !entityTag?.type.isNullOrBlank()) {
                                        Text(
                                            text = "类型：${entityTag?.type}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                // 排序状态下操作 vs 常态操作
                                if (sorting) {
                                    TaxonomyOrderControls(
                                        name = name,
                                        position = index,
                                        total = items.size,
                                        themeColor = themeColor,
                                        onMoveUp = {
                                            haptic(HapticFeedbackType.TextHandleMove)
                                            moveItem(id, up = true)
                                        },
                                        onMoveDown = {
                                            haptic(HapticFeedbackType.TextHandleMove)
                                            moveItem(id, up = false)
                                        },
                                    )
                                } else {
                                    // 书籍数量微胶囊（如果已加载）
                                    val linkedCount = linkedBookIds[id]?.size
                                    if (linkedCount != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = themeColor.copy(alpha = 0.10f),
                                            modifier = Modifier.padding(end = 4.dp),
                                        ) {
                                            Text(
                                                text = "$linkedCount 本",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                                color = themeColor,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            )
                                        }
                                    }

                                    // 编辑按钮
                                    IconButton(
                                        onClick = {
                                            haptic(HapticFeedbackType.TextHandleMove)
                                            editingItem = id to name
                                            editName = name
                                        },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = "编辑",
                                            modifier = Modifier.size(AppIconSize.Medium),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    // 删除按钮
                                    IconButton(
                                        onClick = {
                                            haptic(HapticFeedbackType.TextHandleMove)
                                            deletingItem = id to name
                                        },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "删除",
                                            modifier = Modifier.size(AppIconSize.Medium),
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                        )
                                    }

                                    // 展开收起箭头微图标
                                    Icon(
                                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                        contentDescription = if (expanded) "收起" else "展开",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            // 展开微岛：分类色调调节与关联书籍列表
                            if (expanded && !sorting) {
                                Spacer(modifier = Modifier.height(10.dp))
                                SectionDivider()
                                Spacer(modifier = Modifier.height(10.dp))

                                // 分类专属色调选择微岛
                                if (page == ProfileSubPage.CATEGORIES) {
                                    Text(
                                        text = "专属色调",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        val toneOptions = listOf(
                                            Triple("default", "默认", MaterialTheme.colorScheme.primary),
                                            Triple("warm", "暖色", Color(0xFFD97706)),
                                            Triple("cool", "冷色", Color(0xFF2563EB)),
                                            Triple("green", "绿色", Color(0xFF059669)),
                                            Triple("night", "夜间", Color(0xFF4B5563)),
                                        )
                                        toneOptions.forEach { (tone, label, toneDotColor) ->
                                            val isSelected = entityCategory?.cover_tone == tone
                                            TonePill(
                                                label = label,
                                                dotColor = toneDotColor,
                                                selected = isSelected,
                                                onClick = {
                                                    haptic(HapticFeedbackType.TextHandleMove)
                                                    taxonomyVm.updateCategoryTone(id, tone)
                                                },
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                // 关联书籍列表展示
                                val linkedIds = linkedBookIds[id] ?: emptyList()
                                val linkedBooks = linkedIds.mapNotNull { bookMap[it] }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "关联书籍",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (linkedBooks.isNotEmpty()) {
                                        Text(
                                            text = "共 ${linkedBooks.size} 本",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                if (linkedBooks.isEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = "暂无关联书籍，可前往书架或书籍详情页添加关联",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        )
                                    }
                                } else {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        linkedBooks.forEach { book ->
                                            BookIslandCapsule(
                                                book = book,
                                                themeColor = themeColor,
                                                onClick = {
                                                    haptic(HapticFeedbackType.TextHandleMove)
                                                    onAction(ProfileAction.OpenBook(book.id))
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // 空态微岛
            item(key = "library_empty_island") {
                SectionCard(modifier = Modifier.animateEnter(reducedMotion = reducedMotion)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(themeColor.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = typeIcon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = themeColor,
                            )
                        }
                        Text(
                            text = "还没有任何${typeName}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = "创建专属${typeName}，让全库图书管理更加井井有条",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                haptic(HapticFeedbackType.TextHandleMove)
                                newName = ""
                                showCreateDialog = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("立即创建$typeName")
                        }
                    }
                }
            }
        }
    }

    // 新建对话框（GlassAlertDialog）
    if (showCreateDialog) {
        GlassAlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建$typeName") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "请输入${typeName}名称：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("如：科幻杰作、正在精读") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            },
            confirmButton = {
                val nameOk = newName.isNotBlank()
                TextButton(
                    enabled = nameOk,
                    onClick = {
                        if (nameOk) {
                            when (page) {
                                ProfileSubPage.TAGS -> taxonomyVm.createTag(newName.trim())
                                ProfileSubPage.CATEGORIES -> taxonomyVm.createCategory(newName.trim())
                                ProfileSubPage.SHELVES -> taxonomyVm.createShelf(newName.trim())
                                else -> {}
                            }
                        }
                        showCreateDialog = false
                        newName = ""
                    },
                ) {
                    Text("创建", color = if (nameOk) themeColor else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; newName = "" }) {
                    Text("取消")
                }
            },
        )
    }

    // 重命名对话框（GlassAlertDialog）
    editingItem?.let { (id, oldName) ->
        GlassAlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("重命名$typeName") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "修改${typeName}名称：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            },
            confirmButton = {
                val editOk = editName.isNotBlank() && editName.trim() != oldName
                TextButton(
                    enabled = editOk,
                    onClick = {
                        if (editOk) {
                            when (page) {
                                ProfileSubPage.TAGS -> taxonomyVm.renameTag(id, editName.trim())
                                ProfileSubPage.CATEGORIES -> taxonomyVm.renameCategory(id, editName.trim())
                                ProfileSubPage.SHELVES -> taxonomyVm.renameShelf(id, editName.trim())
                                else -> {}
                            }
                        }
                        editingItem = null
                    },
                ) {
                    Text("保存", color = if (editOk) themeColor else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("取消")
                }
            },
        )
    }

    // 删除确认对话框（GlassAlertDialog）
    deletingItem?.let { (id, name) ->
        GlassAlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text("确认删除$typeName") },
            text = {
                Text(
                    text = "确定要删除「$name」吗？删除后关联的书籍文件与阅读记录均会完整保留。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (page) {
                            ProfileSubPage.TAGS -> taxonomyVm.deleteTag(id)
                            ProfileSubPage.CATEGORIES -> taxonomyVm.deleteCategory(id)
                            ProfileSubPage.SHELVES -> taxonomyVm.deleteShelf(id)
                            else -> {}
                        }
                        deletingItem = null
                    },
                ) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) {
                    Text("取消")
                }
            },
        )
    }
}

/**
 * 带有小色点的分类专属色调选择胶囊。
 */
@Composable
private fun TonePill(
    label: String,
    dotColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (selected) 1.dp else 0.5.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            },
        ),
        modifier = modifier
            .bounceable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * 细致微岛卡片与格式微胶囊展示的书籍项。
 */
@Composable
private fun BookIslandCapsule(
    book: BookEntity,
    themeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .bounceable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = themeColor,
                modifier = Modifier.size(15.dp),
            )

            Text(
                text = book.title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // 格式微胶囊（如 EPUB / TXT）
            if (book.format.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = themeColor.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = book.format.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = themeColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/**
 * 排序控制器组件（微岛化）。
 */
@Composable
internal fun TaxonomyOrderControls(
    name: String,
    position: Int,
    total: Int,
    themeColor: Color,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = themeColor.copy(alpha = 0.10f),
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Text(
                text = (position + 1).toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = themeColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        IconButton(
            onClick = onMoveUp,
            enabled = position > 0,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowUpward,
                contentDescription = "上移$name",
                modifier = Modifier.size(AppIconSize.Small),
                tint = if (position > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
        }

        IconButton(
            onClick = onMoveDown,
            enabled = position < total - 1,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowDownward,
                contentDescription = "下移$name",
                modifier = Modifier.size(AppIconSize.Small),
                tint = if (position < total - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
        }
    }
}

@Composable
internal fun LibrarySortModeHeader(
    sorting: Boolean,
    itemCount: Int,
    onToggle: () -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (sorting) "正在调整顺序" else "列表顺序",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (sorting) "使用箭头调整，修改会立即保存" else "共 $itemCount 项 · 顺序同步到书架筛选与选择页",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onToggle) {
                Text(if (sorting) "完成排序" else "调整顺序")
            }
        }
    }
}

