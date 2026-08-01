package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.screen.ProfileSubPage
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ============================== 书库管理（标签/分类/书单） ==============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibrarySubPage(
    modifier: Modifier,
    page: ProfileSubPage,
    taxonomyVm: com.creationreadingassistant.ui.viewmodel.TaxonomyViewModel,
    books: List<BookEntity>,
    onOpenBook: (BookEntity) -> Unit,
    onMessage: (String) -> Unit,
) {
    val allTags by taxonomyVm.allTags.collectAsStateWithLifecycle()
    val allCategories by taxonomyVm.allCategories.collectAsStateWithLifecycle()
    val allShelves by taxonomyVm.allShelves.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var newName by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editName by remember { mutableStateOf("") }
    var expandedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var linkedBookIds by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    val bookMap = remember(books) { books.associateBy { it.id } }

    fun loadLinkedBooks(id: String) {
        scope.launch(Dispatchers.IO) {
            val ids = when (page) {
                ProfileSubPage.TAGS -> taxonomyVm.getBookIdsByTag(id)
                ProfileSubPage.CATEGORIES -> taxonomyVm.getBookIdsByCategory(id)
                ProfileSubPage.SHELVES -> taxonomyVm.getBookIdsByShelf(id)
                else -> emptyList()
            }
            linkedBookIds = linkedBookIds + (id to ids)
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

    val items: List<Pair<String, String>> = when (page) {
        ProfileSubPage.TAGS -> allTags.map { it.id to it.name }
        ProfileSubPage.CATEGORIES -> allCategories.map { it.id to it.name }
        ProfileSubPage.SHELVES -> allShelves.map { it.id to it.name }
        else -> emptyList()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (items.isNotEmpty()) {
            items.forEach { (id, name) ->
                val expanded = expandedIds.contains(id)
                val entityTag = if (page == ProfileSubPage.TAGS) allTags.find { it.id == id } else null
                val entityCategory = if (page == ProfileSubPage.CATEGORIES) allCategories.find { it.id == id } else null
                SectionCard(
                    onClick = { toggleExpanded(id) },
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (page) {
                                    ProfileSubPage.TAGS -> Icons.Filled.Sell
                                    ProfileSubPage.CATEGORIES -> Icons.Filled.Folder
                                    else -> Icons.Filled.Book
                                },
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(name, style = MaterialTheme.typography.bodyLarge)
                                if (page == ProfileSubPage.TAGS && !entityTag?.type.isNullOrBlank()) {
                                    Text("类型：${entityTag?.type}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { editingItem = id to name; editName = name }) {
                                Icon(Icons.Filled.BorderColor, contentDescription = "编辑", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                when (page) {
                                    ProfileSubPage.TAGS -> taxonomyVm.deleteTag(id)
                                    ProfileSubPage.CATEGORIES -> taxonomyVm.deleteCategory(id)
                                    ProfileSubPage.SHELVES -> taxonomyVm.deleteShelf(id)
                                    else -> {}
                                }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                            }
                            Icon(
                                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (expanded) "收起" else "展开",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (expanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            if (page == ProfileSubPage.CATEGORIES) {
                                Text("分类色调", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        "default" to "默认",
                                        "warm" to "暖色",
                                        "cool" to "冷色",
                                        "green" to "绿色",
                                        "night" to "夜间",
                                    ).forEach { (tone, label) ->
                                        val selected = entityCategory?.cover_tone == tone
                                        SelectablePill(
                                            text = label,
                                            selected = selected,
                                            onClick = { taxonomyVm.updateCategoryTone(id, tone) },
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            val linkedIds = linkedBookIds[id] ?: emptyList()
                            val linkedBooks = remember(linkedIds, books) { linkedIds.mapNotNull { bookMap[it] } }
                            if (linkedBooks.isEmpty()) {
                                Text("暂无关联书籍", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("关联书籍", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    linkedBooks.forEach { book ->
                                        Card(
                                            modifier = Modifier.clickable { onOpenBook(book) },
                                            shape = LocalComponentSpec.current.pillShape,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                        ) {
                                            Text(
                                                book.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
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
            SectionCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        when (page) { ProfileSubPage.TAGS -> Icons.Filled.Sell; ProfileSubPage.CATEGORIES -> Icons.Filled.Folder; else -> Icons.Filled.Book },
                        contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(when (page) { ProfileSubPage.TAGS -> "还没有标签"; ProfileSubPage.CATEGORIES -> "还没有分类"; else -> "还没有书单" }, style = MaterialTheme.typography.titleMedium)
                    Text("点击下方按钮创建。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        SectionDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (showCreate) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, placeholder = { Text("输入名称") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { if (newName.isNotBlank()) { when (page) { ProfileSubPage.TAGS -> taxonomyVm.createTag(newName.trim()); ProfileSubPage.CATEGORIES -> taxonomyVm.createCategory(newName.trim()); ProfileSubPage.SHELVES -> taxonomyVm.createShelf(newName.trim()); else -> {} }; showCreate = false; newName = "" } }) { Text("创建") }
                TextButton(onClick = { showCreate = false; newName = "" }) { Text("取消") }
            }
        } else {
            TextButton(onClick = { showCreate = true }) { Text("+ 创建新的") }
        }
    }

    // 重命名对话框
    editingItem?.let { (id, name) ->
        GlassAlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("重命名") },
            text = { OutlinedTextField(value = editName, onValueChange = { editName = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (editName.isNotBlank() && editName != name) {
                        when (page) {
                            ProfileSubPage.TAGS -> taxonomyVm.renameTag(id, editName.trim())
                            ProfileSubPage.CATEGORIES -> taxonomyVm.renameCategory(id, editName.trim())
                            ProfileSubPage.SHELVES -> taxonomyVm.renameShelf(id, editName.trim())
                            else -> {}
                        }
                    }
                    editingItem = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingItem = null }) { Text("取消") } },
        )
    }
}
