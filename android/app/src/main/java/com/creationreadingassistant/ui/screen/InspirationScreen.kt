package com.creationreadingassistant.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.InspirationVariantEntity
import com.creationreadingassistant.ui.viewmodel.InspirationDraft
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import com.creationreadingassistant.ui.viewmodel.InspirationViewModel
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.components.SettingRow
import com.creationreadingassistant.ui.components.SectionDivider
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.components.LineArtBookmark
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.ListSkeleton
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TYPE_OPTIONS = listOf(
    "note" to "灵感",
    "plot" to "剧情",
    "character" to "人物",
    "world" to "世界观",
    "scene" to "场景",
    "line" to "对话",
    "trope" to "设定",
    "conflict" to "冲突",
)

private val STATUS_OPTIONS = listOf(
    "inbox" to "未整理",
    "reviewing" to "待整理",
    "usable" to "可使用",
    "polished" to "已打磨",
    "used" to "已采用",
    "archived" to "已归档",
)

private val SORT_OPTIONS = listOf(
    "updated" to "最近更新",
    "created" to "创建时间",
    "title" to "标题",
    "source" to "来源书籍",
)

private fun getTypeLabel(type: String?): String =
    TYPE_OPTIONS.firstOrNull { it.first == type }?.second ?: "灵感"

private fun getStatusLabel(status: String?): String =
    STATUS_OPTIONS.firstOrNull { it.first == status }?.second ?: "未整理"

/** 灵感 AI 动作的具名标签（对照网页 aiActions / actionLabel）。 */
private val AI_ACTION_LABELS = mapOf(
    "polish" to "润色",
    "expand" to "扩写",
    "platform-style" to "平台风格化",
    "conflict" to "生成冲突",
    "humanize" to "去 AI 味",
)

private fun aiActionLabel(kind: String?): String = AI_ACTION_LABELS[kind] ?: (kind ?: "候选")

private fun parseTagInput(value: String): List<String> =
    value.split(Regex("[，,\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }

private fun formatListTime(value: String?): String {
    if (value.isNullOrBlank()) return "时间未知"
    val instant = runCatching { Instant.parse(value) }.getOrElse { return "时间未知" }
    val zdt = instant.atZone(ZoneId.systemDefault())
    val now = java.time.LocalDate.now()
    return if (zdt.toLocalDate() == now) {
        zdt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    } else {
        zdt.format(DateTimeFormatter.ofPattern("MM/dd"))
    }
}

private fun formatDetailTime(value: String?): String {
    if (value.isNullOrBlank()) return "时间未知"
    val instant = runCatching { Instant.parse(value) }.getOrElse { return "时间未知" }
    return instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
}

private fun timeMillis(entity: InspirationEntityRef, key: String): Long =
    runCatching { Instant.parse(if (key == "updated") entity.updatedAt else entity.createdAt).toEpochMilli() }
        .getOrDefault(0L)

/** 轻量引用，避免在此文件直接依赖 Room 实体类型（仅取时间字段）。 */
private data class InspirationEntityRef(val updatedAt: String, val createdAt: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InspirationScreen(
    viewModel: InspirationViewModel = hiltViewModel(),
    /** H3：从首页最近灵感卡片按 id 跳入时，直接进入该条灵感的详情面板。 */
    initialSelectedId: String? = null,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf(if (initialSelectedId != null) "detail" else "list") } // list | detail | editor
    var selectedId by remember { mutableStateOf(initialSelectedId) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("all") }
    val sortMode by viewModel.inspirationSort.collectAsStateWithLifecycle()
    var sortOpen by remember { mutableStateOf(false) }
    var actionItemId by remember { mutableStateOf<String?>(null) }
    var editorDirty by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    fun message(text: String) = scope.launch { snackbarHostState.showSnackbar(text) }

    // A 档打磨：首屏加载占位 + 系统「减少动态效果」感知
    val layout = LocalLayoutTokens.current
    val reducedMotion = rememberReducedMotion()
    var firstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(350); firstLoad = false }
    LaunchedEffect(items) { if (items.isNotEmpty()) firstLoad = false }
    val showSkeleton = firstLoad && items.isEmpty()

    val selectedEntity = items.firstOrNull { it.id == selectedId }
    val editingEntity = items.firstOrNull { it.id == editingId }

    val needle = query.trim().lowercase()
    val filtered = remember(items, needle, typeFilter, sortMode) {
        val list = items.filter { item ->
            if (typeFilter != "all" && (item.type ?: "note") != typeFilter) return@filter false
            if (needle.isBlank()) return@filter true
            val src = viewModel.sourceOf(item)
            val hay = listOf(
                item.title,
                item.body,
                src?.bookTitle,
                src?.locationLabel,
                src?.excerpt,
            ).plus(viewModel.tagsOf(item)).filterNotNull().joinToString(" ").lowercase()
            hay.contains(needle)
        }
        when (sortMode) {
            "updated" -> list.sortedByDescending { timeMillis(InspirationEntityRef(it.updated_at, it.created_at), "updated") }
            "created" -> list.sortedByDescending { timeMillis(InspirationEntityRef(it.updated_at, it.created_at), "created") }
            "title" -> list.sortedBy { it.title.lowercase() }
            "source" -> list.sortedBy { viewModel.sourceOf(it)?.bookTitle?.lowercase() ?: "" }
            else -> list
        }
    }

    val availableTypes = remember(items) {
        val counts = mutableMapOf<String, Int>()
        for (item in items) {
            val t = item.type ?: "note"
            counts[t] = (counts[t] ?: 0) + 1
        }
        TYPE_OPTIONS.filter { counts.containsKey(it.first) }
    }
    val typeCounts = remember(items) { items.groupingBy { it.type ?: "note" }.eachCount() }

    fun openDetail(id: String) {
        actionItemId = null
        selectedId = id
        mode = "detail"
    }

    fun closeEditor() {
        editorDirty = false
        if (editingId != null) {
            mode = "detail"
        } else {
            mode = "list"
            selectedId = null
        }
        editingId = null
    }

    fun requestEditorBack() {
        if (!editorDirty) {
            closeEditor()
            return
        }
        showUnsavedDialog = true
    }

    fun copyItem(item: InspirationEntity, cm: androidx.compose.ui.platform.ClipboardManager) {
        actionItemId = null
        val src = viewModel.sourceOf(item)
        val text = listOf(item.title, item.body, src?.excerpt).filterNotNull().joinToString("\n\n")
        cm.setText(AnnotatedString(text))
        message("灵感内容已复制。")
    }

    fun openSource(item: InspirationEntity) {
        actionItemId = null
        val src = viewModel.sourceOf(item)
        if (src?.bookId == null) {
            message("这条灵感没有关联来源书籍。")
            return
        }
        val book = books.firstOrNull { it.id == src.bookId }
        if (book == null) {
            message("来源书籍已不可用，这条灵感仍会保留。")
        } else {
            message("已定位来源：《${book.title}》")
        }
    }

    fun confirmDelete() {
        val id = pendingDeleteId ?: return
        pendingDeleteId = null
        actionItemId = null
        viewModel.deleteInspiration(id)
        if (selectedId == id) {
            selectedId = null
            mode = "list"
        }
        message("灵感已删除。")
    }

    BackHandler(enabled = mode != "list" || searchOpen || sortOpen || actionItemId != null) {
        when {
            actionItemId != null -> actionItemId = null
            sortOpen -> sortOpen = false
            mode == "editor" -> requestEditorBack()
            mode == "detail" -> { selectedId = null; mode = "list" }
            searchOpen -> { searchOpen = false; query = "" }
        }
    }

    Scaffold(
        topBar = {
            if (mode == "editor") {
                InspirationEditorTopBar(
                    editing = editingEntity != null,
                    onBack = { requestEditorBack() },
                )
            } else if (mode == "detail" && selectedEntity != null) {
                InspirationDetailTopBar(
                    onBack = { selectedId = null; mode = "list" },
                    onEdit = { editingId = selectedEntity.id; editorDirty = false; mode = "editor" },
                    onMore = { actionItemId = selectedEntity.id },
                )
            } else {
                InspirationListTopBar(
                    searchOpen = searchOpen,
                    query = query,
                    onQueryChange = { query = it },
                    onSearchToggle = { searchOpen = !searchOpen },
                    onCloseSearch = { searchOpen = false; query = "" },
                    onNew = { editingId = null; editorDirty = false; mode = "editor" },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                mode == "editor" -> {
                    InspirationEditor(
                        viewModel = viewModel,
                        existing = editingEntity,
                        onCancel = { requestEditorBack() },
                        onDirtyChange = { editorDirty = it },
                        onSaved = { id, isNew ->
                            editingId = null
                            selectedId = id
                            editorDirty = false
                            mode = "detail"
                            message(if (isNew) "灵感已保存。" else "修改已保存。")
                        },
                        onMessage = ::message,
                    )
                }
                mode == "detail" && selectedEntity != null -> {
                    InspirationDetailPanel(
                        entity = selectedEntity,
                        viewModel = viewModel,
                        onOpenSource = { openSource(selectedEntity) },
                        onMessage = ::message,
                        onCopyVariant = { text ->
                            clipboard.setText(AnnotatedString(text))
                            message("候选内容已复制。")
                        },
                    )
                }
                else -> {
                    val hasAny = items.isNotEmpty()
                    val hasSearch = needle.isNotBlank()
                    val hasFilter = typeFilter != "all"
                    val emptyKind = when {
                        !hasAny -> "empty"
                        hasSearch -> "search"
                        hasFilter -> "filter"
                        else -> "empty"
                    }
                    if (showSkeleton) {
                        ListSkeleton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = layout.pageHorizontal, vertical = layout.relatedGap),
                            count = 5,
                            reducedMotion = reducedMotion,
                        )
                    } else {
                    InspirationList(
                        itemsList = filtered,
                        typeFilter = typeFilter,
                        availableTypes = availableTypes,
                        sortMode = sortMode,
                        onTypeFilter = { typeFilter = it },
                        onOpenSort = { sortOpen = true },
                        onOpenDetail = ::openDetail,
                        onMore = { actionItemId = it },
                        viewModel = viewModel,
                        emptyKind = if (filtered.isEmpty()) emptyKind else null,
                        typeCounts = typeCounts,
                        onCreate = { editingId = null; editorDirty = false; mode = "editor" },
                        onResetFilter = { query = ""; typeFilter = "all" },
                    )
                    }
                }
            }
        }
    }

    // 排序底部弹层
    if (sortOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        GlassModalBottomSheet(onDismissRequest = { sortOpen = false }, sheetState = sheetState) {
            SheetHandle()
            Text(
                "排序方式",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            SORT_OPTIONS.forEachIndexed { index, (value, label) ->
                SettingRow(
                    title = label,
                    leading = {
                        Icon(
                            imageVector = if (value == "title") Icons.Outlined.SortByAlpha else Icons.Outlined.Tune,
                            contentDescription = null,
                        )
                    },
                    trailing = {
                        if (sortMode == value) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    onClick = { viewModel.setInspirationSort(value); sortOpen = false },
                )
                if (index < SORT_OPTIONS.lastIndex) {
                    SectionDivider()
                }
            }
            Box(Modifier.fillMaxWidth().padding(bottom = 24.dp))
        }
    }

    // 更多操作底部弹层
    val actionItem = items.firstOrNull { it.id == actionItemId }
    if (actionItem != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val src = viewModel.sourceOf(actionItem)
        GlassModalBottomSheet(onDismissRequest = { actionItemId = null }, sheetState = sheetState) {
            SheetHandle()
            Text(
                actionItem.title.ifBlank { "未命名灵感" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            val actionRows = buildList {
                add(Triple(Icons.Outlined.AutoAwesome, "查看详情") { actionItemId = null; openDetail(actionItem.id) })
                add(Triple(Icons.Outlined.Edit, "编辑") {
                    actionItemId = null
                    selectedId = actionItem.id
                    editingId = actionItem.id
                    editorDirty = false
                    mode = "editor"
                })
                add(Triple(Icons.Outlined.ContentCopy, "复制内容") { copyItem(actionItem, clipboard) })
                if (src?.bookId != null) {
                    add(Triple(Icons.Outlined.Book, "查看来源书籍") { openSource(actionItem) })
                }
                add(Triple(Icons.Outlined.Delete, "删除灵感") { actionItemId = null; pendingDeleteId = actionItem.id })
            }
            actionRows.forEachIndexed { index, (icon, label, onClick) ->
                SettingRow(
                    title = label,
                    leading = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (label == "删除灵感") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = onClick,
                )
                if (index < actionRows.lastIndex) {
                    SectionDivider()
                }
            }
            Box(Modifier.fillMaxWidth().padding(bottom = 24.dp))
        }
    }

    // 删除确认
    if (pendingDeleteId != null) {
        GlassAlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("删除这条灵感？") },
            text = { Text("只会删除当前灵感，不会删除来源书籍、笔记或正文。") },
            confirmButton = { TextButton(onClick = ::confirmDelete) { Text("删除") } },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("取消") } },
        )
    }

    // 未保存修改确认
    if (showUnsavedDialog) {
        GlassAlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("放弃未保存修改？") },
            text = { Text("返回后，本次输入的内容不会保存。") },
            confirmButton = {
                TextButton(onClick = { showUnsavedDialog = false; closeEditor() }) { Text("放弃") }
            },
            dismissButton = { TextButton(onClick = { showUnsavedDialog = false }) { Text("继续编辑") } },
        )
    }
}

/* ---------- 顶部栏 ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspirationListTopBar(
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onCloseSearch: () -> Unit,
    onNew: () -> Unit,
) {
    TopAppBar(
        title = {
            if (searchOpen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text("搜索标题、正文、标签或来源") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCloseSearch) { Text("取消") }
                }
            } else {
                Text(
                    "灵感",
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
        },
        actions = {
            if (!searchOpen) {
                IconButton(onClick = onSearchToggle) { Icon(Icons.Outlined.Search, contentDescription = "搜索灵感") }
                IconButton(onClick = onNew) { Icon(Icons.Filled.Add, contentDescription = "新建灵感") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspirationDetailTopBar(onBack: () -> Unit, onEdit: () -> Unit, onMore: () -> Unit) {
    TopAppBar(
        title = { Text("灵感详情") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
        actions = {
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "编辑灵感") }
            IconButton(onClick = onMore) { Icon(Icons.Outlined.MoreHoriz, contentDescription = "更多操作") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspirationEditorTopBar(editing: Boolean, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(if (editing) "编辑灵感" else "新建灵感") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
    )
}

@Composable
private fun InspirationTypePill(selected: Boolean, label: String, onClick: () -> Unit) {
    SelectablePill(text = label, selected = selected, onClick = onClick)
}

/* ---------- 列表 ---------- */

@Composable
private fun InspirationList(
    itemsList: List<InspirationEntity>,
    typeFilter: String,
    availableTypes: List<Pair<String, String>>,
    sortMode: String,
    onTypeFilter: (String) -> Unit,
    onOpenSort: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onMore: (String) -> Unit,
    viewModel: InspirationViewModel,
    emptyKind: String?,
    typeCounts: Map<String, Int>,
    onCreate: () -> Unit,
    onResetFilter: () -> Unit,
) {
    val layout = LocalLayoutTokens.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = layout.pageHorizontal,
                    vertical = layout.relatedGap,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(layout.relatedGap),
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                InspirationTypePill(selected = typeFilter == "all", label = "全部", onClick = { onTypeFilter("all") })
                availableTypes.forEach { (value, label) ->
                    val count = typeCounts[value] ?: 0
                    InspirationTypePill(selected = typeFilter == value, label = "$label $count", onClick = { onTypeFilter(value) })
                }
            }
            Surface(
                onClick = onOpenSort,
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(SORT_OPTIONS.firstOrNull { it.first == sortMode }?.second ?: "最近更新", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (emptyKind != null) {
            InspirationEmptyState(kind = emptyKind, onCreate = onCreate, onReset = onResetFilter)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = layout.pageHorizontal),
                verticalArrangement = Arrangement.spacedBy(layout.contentGap),
            ) {
                items(itemsList, key = { it.id }) { item ->
                    InspirationRecordCard(
                        item = item,
                        viewModel = viewModel,
                        onOpen = { onOpenDetail(item.id) },
                        onMore = { onMore(item.id) },
                    )
                }
                item { Box(Modifier.fillMaxWidth().padding(bottom = 16.dp)) }
            }
        }
    }
}

@Composable
private fun InspirationRecordCard(
    item: InspirationEntity,
    viewModel: InspirationViewModel,
    onOpen: () -> Unit,
    onMore: () -> Unit,
) {
    val spec = LocalComponentSpec.current
    val src = viewModel.sourceOf(item)
    val tags = viewModel.tagsOf(item)
    // 对齐 web .inspiration-record：扁平 + 1px 发丝线、圆角来自 spec、无投影
    SectionCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        getTypeLabel(item.type),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Text(
                    formatListTime(item.updated_at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
                IconButton(
                    onClick = onMore,
                    modifier = Modifier.size(LocalLayoutTokens.current.minimumTouchTarget),
                ) {
                    Icon(Icons.Outlined.MoreHoriz, contentDescription = "更多操作", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                item.title.ifBlank { "未命名灵感" },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.24).sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val summary = item.body.ifBlank { src?.excerpt ?: "还没有正文" }
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!src?.bookTitle.isNullOrBlank() || !src?.locationLabel.isNullOrBlank() || !src?.chapterTitle.isNullOrBlank()) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.Book, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    val locLabel = src?.locationLabel ?: src?.chapterTitle
                    Text(
                        "来源：${src?.bookTitle?.let { "《$it》" } ?: "阅读记录"}${locLabel?.let { " · $it" } ?: ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    tags.take(3).forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = spec.pillShape,
                        ) {
                            Text(tag, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InspirationEmptyState(kind: String, onCreate: () -> Unit, onReset: () -> Unit) {
    val (title, body) = when (kind) {
        "search" -> "没有搜索结果" to "试试更短的关键词，或清除搜索。"
        "filter" -> "这个类型还没有内容" to "切换到全部，或新建一条灵感。"
        else -> "还没有灵感" to "记录设定、摘录或创作片段。"
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.padding(bottom = 16.dp), tint = MaterialTheme.colorScheme.primary)
        LineArtBookmark(modifier = Modifier.padding(bottom = 4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        if (kind == "empty") {
            TextButton(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("新建灵感")
            }
        } else {
            TextButton(onClick = onReset) { Text("查看全部") }
        }
    }
}

/* ---------- 详情 ---------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspirationDetailPanel(
    entity: InspirationEntity,
    viewModel: InspirationViewModel,
    onOpenSource: () -> Unit,
    onMessage: (String) -> Unit,
    onCopyVariant: (String) -> Unit,
) {
    val spec = LocalComponentSpec.current
    val src = viewModel.sourceOf(entity)
    val tags = viewModel.tagsOf(entity)
    val variants by viewModel.observeVariants(entity.id).collectAsStateWithLifecycle(emptyList())
    var generatingAction by remember { mutableStateOf<String?>(null) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).animateEnter(reducedMotion = rememberReducedMotion()).padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = spec.pillShape) {
                Text(getTypeLabel(entity.type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = spec.pillShape) {
                Text(getStatusLabel(entity.status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
            Text("更新于 ${formatDetailTime(entity.updated_at)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text(entity.title.ifBlank { "未命名灵感" }, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        if (entity.body.isNotBlank()) {
            Text(entity.body, style = MaterialTheme.typography.bodyLarge)
        } else {
            Text("还没有正文。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { tag ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = spec.pillShape) {
                        Text("#$tag", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }
        if (src != null) {
            Spacer(Modifier.height(16.dp))
            SectionCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Book, contentDescription = null)
                        Text("来源", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onOpenSource, modifier = Modifier.fillMaxWidth().padding(0.dp)) {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                            Text(src.bookTitle ?: "来源书籍已不可用", style = MaterialTheme.typography.bodyMedium)
                            if (!src.bookAuthor.isNullOrBlank()) {
                                Text(src.bookAuthor, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    val locationParts = listOf(
                        src.chapterTitle,
                        src.locationLabel,
                        src.progressPercent?.let { String.format(java.util.Locale.US, "%.1f%%", it) },
                    ).filterNotNull()
                    if (locationParts.isNotEmpty()) {
                        Text(locationParts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (!src.excerpt.isNullOrBlank()) {
                        Text(
                            "“${src.excerpt}”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
        // ── AI 候选版本（对照 web InspirationVariant） ──
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("AI 候选版本", style = MaterialTheme.typography.titleSmall)
            if (variants.isNotEmpty()) {
                Text("${variants.size} 个", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            if (generatingAction != null) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        Text("AI 只生成辅助候选，不会自动覆盖正文。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            viewModel.AI_ACTIONS.forEach { (action, label) ->
                FilledTonalButton(
                    enabled = entity.body.isNotBlank() && generatingAction == null,
                    onClick = {
                        generatingAction = action
                        viewModel.runInspirationAction(entity.id, action, entity.title, entity.body) { ok ->
                            generatingAction = null
                            onMessage(if (ok) "AI 候选已保存，原文没有被覆盖。" else "生成失败，请检查 AI 设置")
                        }
                    },
                ) {
                    if (generatingAction == action) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (variants.isEmpty()) {
            Text("需要时再主动生成候选。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            variants.forEach { v ->
                VariantCard(
                    variant = v,
                    onApply = { viewModel.applyVariant(entity.id, v); onMessage("已采用候选版本") },
                    onDelete = { viewModel.deleteVariant(v.id) },
                    onCopy = onCopyVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VariantCard(
    variant: InspirationVariantEntity,
    onApply: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (String) -> Unit,
) {
    SectionCard(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(aiActionLabel(variant.kind), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!variant.model.isNullOrBlank()) {
                    Text("· ${variant.model}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text("删除", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
            }
            if (!variant.content.isNullOrBlank()) {
                Text(variant.content, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { variant.content?.let { onCopy(it) } }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("复制", style = MaterialTheme.typography.labelSmall)
                }
                Button(onClick = onApply) { Text("采用") }
            }
        }
    }
}

/* ---------- 编辑器 ---------- */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InspirationEditor(
    viewModel: InspirationViewModel,
    existing: InspirationEntity?,
    @Suppress("unused") onCancel: () -> Unit,
    onDirtyChange: (Boolean) -> Unit,
    onSaved: (String, Boolean) -> Unit,
    @Suppress("unused") onMessage: (String) -> Unit,
) {
    val spec = LocalComponentSpec.current
    val books by viewModel.books.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var body by remember { mutableStateOf(existing?.body ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: "note") }
    var status by remember { mutableStateOf(existing?.status ?: "inbox") }
    val src0 = existing?.let { viewModel.sourceOf(it) }
    val tags0 = existing?.let { viewModel.tagsOf(it) } ?: emptyList()
    var tagsInput by remember { mutableStateOf(tags0.joinToString("，")) }
    var sourceBookId by remember { mutableStateOf(src0?.bookId ?: "") }
    var sourceLocation by remember { mutableStateOf(src0?.locationLabel ?: "") }
    var sourceExcerpt by remember { mutableStateOf(src0?.excerpt ?: "") }
    var error by remember { mutableStateOf("") }

    val initialSignature = remember {
        listOf(title, body, type, status, tagsInput, sourceBookId, sourceLocation, sourceExcerpt).joinToString("|")
    }
    val signature = listOf(title, body, type, status, tagsInput, sourceBookId, sourceLocation, sourceExcerpt).joinToString("|")
    LaunchedEffect(signature) { onDirtyChange(signature != initialSignature) }

    var typeExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var bookExpanded by remember { mutableStateOf(false) }

    val selectedBook = books.firstOrNull { it.id == sourceBookId }

    fun save() {
        if (title.isBlank() && body.isBlank() && sourceExcerpt.isBlank()) {
            error = "请至少填写标题、正文或来源摘录中的一项。"
            return
        }
        error = ""
        val sourceInfo = if (sourceBookId.isNotBlank() || sourceLocation.isNotBlank() || sourceExcerpt.isNotBlank()) {
            InspirationSourceInfo(
                bookId = sourceBookId.ifBlank { null },
                bookTitle = selectedBook?.title,
                bookAuthor = selectedBook?.author,
                locationLabel = sourceLocation.trim().ifBlank { null },
                excerpt = sourceExcerpt.trim().ifBlank { null },
            )
        } else null
        val isNew = existing == null
        val id = existing?.id ?: java.util.UUID.randomUUID().toString()
        val draft = InspirationDraft(
            id = id,
            title = title.trim(),
            body = body,
            type = type,
            status = status,
            tags = parseTagInput(tagsInput),
            source = sourceInfo,
        )
        viewModel.saveInspiration(draft)
        onSaved(id, isNew)
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).animateEnter(reducedMotion = rememberReducedMotion()).padding(16.dp)) {
        FieldLabel("标题")
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text("给这条灵感一个清楚的名字") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel("类型")
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = getTypeLabel(type),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        TYPE_OPTIONS.forEach { (value, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { type = value; typeExpanded = false })
                        }
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel("状态")
                ExposedDropdownMenuBox(expanded = statusExpanded, onExpandedChange = { statusExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = getStatusLabel(status),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                        STATUS_OPTIONS.forEach { (value, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { status = value; statusExpanded = false })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        FieldLabel("正文")
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            placeholder = { Text("写下设定、冲突、人物动作或可以继续发展的片段……") },
            modifier = Modifier.fillMaxWidth().height(160.dp),
            maxLines = 10,
        )
        Spacer(Modifier.height(12.dp))

        FieldLabel("标签")
        OutlinedTextField(
            value = tagsInput,
            onValueChange = { tagsInput = it },
            placeholder = { Text("用逗号或空格分隔") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val previewTags = parseTagInput(tagsInput)
        if (previewTags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                previewTags.forEach { tag ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = spec.pillShape) {
                        Text("#$tag", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Text("来源（可选）", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
        FieldLabel("来源书籍")
        ExposedDropdownMenuBox(expanded = bookExpanded, onExpandedChange = { bookExpanded = it }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedBook?.let { "《${it.title}》${it.author?.let { it } ?: ""}" } ?: "不关联书籍",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bookExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = bookExpanded, onDismissRequest = { bookExpanded = false }) {
                DropdownMenuItem(text = { Text("不关联书籍") }, onClick = { sourceBookId = ""; bookExpanded = false })
                books.forEach { book ->
                    DropdownMenuItem(
                        text = { Text("《${book.title}》${book.author?.let { " · $it" } ?: ""}") },
                        onClick = { sourceBookId = book.id; bookExpanded = false },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        FieldLabel("章节或位置")
        OutlinedTextField(
            value = sourceLocation,
            onValueChange = { sourceLocation = it },
            placeholder = { Text("例如：第 12 章 / 38.5%") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        FieldLabel("原文摘录")
        OutlinedTextField(
            value = sourceExcerpt,
            onValueChange = { sourceExcerpt = it },
            placeholder = { Text("记录触发灵感的原文，不会混入正文") },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            maxLines = 8,
        )

        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        }

        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.Button(
            onClick = { save() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (existing != null) "保存修改" else "保存灵感")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
}
