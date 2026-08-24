package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.TextFields
import com.creationreadingassistant.ui.theme.AppIconSize
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.layout.adaptivePageMetrics
import com.creationreadingassistant.ui.theme.AppError
import com.creationreadingassistant.ui.theme.AppShapes
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.ProgressBarShape
import java.time.LocalDate

// ===================== 底部弹层：书籍详情 =====================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BookDetailSheet(
    book: BookEntity,
    progressById: Map<String, ReadingProgressEntity>,
    sessions: List<ReadingSessionEntity>,
    notes: List<NoteEntity>,
    highlights: List<HighlightEntity>,
    inspirations: List<InspirationEntity>,
    shelves: List<ShelfEntity>,
    categories: List<CategoryEntity>,
    allTags: List<TagEntity>,
    assignedTagIds: List<String>,
    onDismiss: () -> Unit,
    onContinue: (BookEntity) -> Unit,
    onDownload: (BookEntity) -> Unit,
    onDelete: (String) -> Unit,
    onMessage: (String) -> Unit,
    onRemoveShelf: (String) -> Unit,
    onRemoveCategory: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onUpdateBook: (String, String?, String?) -> Unit,
    onChangeCover: () -> Unit,
    onChangeTextCover: () -> Unit,
    onResetCover: () -> Unit,
) {
    val spec = LocalComponentSpec.current
    val layout = LocalLayoutTokens.current
    val percent = progressFor(progressById, book.id)
    val readiness = book.readiness()
    val progress = progressById[book.id]
    var editing by remember { mutableStateOf(false) }
    var editTitle by remember(book.title) { mutableStateOf(book.title) }
    var editAuthor by remember(book.author) { mutableStateOf(book.author ?: "") }
    var editDescription by remember(book.description) { mutableStateOf(book.description ?: "") }
    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = layout.contentMaxWidth,
        shape = LocalComponentSpec.current.sheetShape,
        dragHandle = { SheetHandle() },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val adaptive = adaptivePageMetrics(maxWidth, layout)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = adaptive.horizontalPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    BookCover(book = book, percent = percent, modifier = Modifier.size(64.dp, 90.dp), fallback = { ShelfCoverFallback(book) })
                    IconButton(
                        onClick = onChangeCover,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(48.dp)
                            .semantics { contentDescription = "更换封面" },
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = AppShapes.small,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (editing) {
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("书名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = editAuthor,
                            onValueChange = { editAuthor = it },
                            label = { Text("作者") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = editDescription,
                            onValueChange = { editDescription = it },
                            label = { Text("简介") },
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(book.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(book.author ?: "作者未知", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!book.description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(book.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(color = toneColor(readiness.tone).copy(alpha = 0.15f), shape = spec.pillShape) {
                        Text(readiness.label, color = toneColor(readiness.tone), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                IconButton(onClick = {
                    if (editing) {
                        onUpdateBook(editTitle, editAuthor.takeIf { it.isNotBlank() }, editDescription.takeIf { it.isNotBlank() })
                    }
                    editing = !editing
                }) {
                    Icon(if (editing) Icons.Outlined.CheckCircle else Icons.Outlined.Edit, contentDescription = if (editing) "保存" else "编辑")
                }
            }
            // S2：文字封面 / 重置封面
            if (!editing) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onChangeTextCover, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(AppIconSize.Compact))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("文字封面")
                    }
                    if (!book.cover_data_url.isNullOrBlank()) {
                        OutlinedButton(onClick = onResetCover, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                            Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(AppIconSize.Compact))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("重置封面")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { if (readiness.tone == ReadinessTone.READY) onContinue(book) else onDownload(book) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (readiness.tone == ReadinessTone.READY) (if (percent > 0) "继续阅读" else "开始阅读") else "下载后阅读")
                }
                OutlinedButton(onClick = { onDownload(book) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (readiness.tone == ReadinessTone.READY) "正文已下载" else "下载正文")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("阅读统计")
                InfoRow(
                    "总阅读时长",
                    formatDuration(bookDetailReadingTimeMs(progress?.total_reading_time_ms ?: 0L, sessions)),
                )
                InfoRow("上次阅读", (progress?.last_read_at ?: "从未阅读").take(19).replace("T", " "))
                InfoRow("阅读进度", "${"%.1f".format(percent)}%")
                InfoRow("阅读次数", "${sessions.size} 次")
                Spacer(modifier = Modifier.height(8.dp))
                ReadingHistoryChart(sessions = sessions)
            }
            Spacer(modifier = Modifier.height(12.dp))

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                // S3：逐条阅读记录（按日期聚合，可展开明细）
                val sortedSessions = sessions.sortedByDescending { it.started_at ?: it.created_at ?: "" }
                SectionTitle("阅读记录 (${sortedSessions.size})")
                if (sortedSessions.isEmpty()) {
                    Text("还没有阅读记录。开始阅读后，这里会显示每次阅读。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    // S3：按日期二级分组（对齐网页 BookDetailSheet 按日聚合）
                    val todayStr = LocalDate.now().toString()
                    val yesterdayStr = LocalDate.now().minusDays(1).toString()
                    val grouped = sortedSessions.groupBy { s ->
                        (s.started_at ?: s.created_at ?: "").take(10)
                    }
                    val dateOrder = grouped.keys.sortedDescending()
                    dateOrder.forEach { date ->
                        val label = when (date) {
                            todayStr -> "今天"
                            yesterdayStr -> "昨天"
                            else -> date
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        grouped[date]?.forEach { s ->
                            ExpandableRow(
                                title = (s.started_at ?: s.created_at ?: "未知时间").take(16).replace("T", " "),
                                subtitle = "${formatDuration(s.duration_ms)} · 进度 ${(s.progress_percent ?: 0f).toInt()}%",
                            ) {
                                InfoRow("开始", (s.started_at ?: "-").take(19).replace("T", " "))
                                InfoRow("结束", (s.ended_at ?: "-").take(19).replace("T", " "))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                // S3：书签与笔记 / 高亮区块
                val bookmarks = notes.filter { it.kind == "bookmark" }
                val noteList = notes.filter { it.kind != "bookmark" }
                SectionTitle("书签与笔记 · ${bookmarks.size} 书签 · ${noteList.size + highlights.size} 条")
                if (bookmarks.isEmpty() && noteList.isEmpty() && highlights.isEmpty()) {
                    Text("阅读时点“书签”或“笔记”，这本书的沉淀会集中在这里。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    bookmarks.forEach { n ->
                        ExpandableRow(
                            title = "书签 · ${(n.progress_percent ?: 0f).toInt()}%",
                            subtitle = n.excerpt ?: n.body ?: n.chapter_title ?: "当前位置",
                        ) {
                            if (!n.chapter_title.isNullOrBlank()) InfoRow("章节", n.chapter_title)
                            if (!n.body.isBlank()) InfoRow("正文", n.body)
                        }
                    }
                    noteList.forEach { n ->
                        ExpandableRow(
                            title = "笔记 · ${(n.progress_percent ?: 0f).toInt()}%",
                            subtitle = n.excerpt ?: n.body ?: n.chapter_title ?: "当前位置",
                        ) {
                            if (!n.chapter_title.isNullOrBlank()) InfoRow("章节", n.chapter_title)
                            if (!n.body.isBlank()) InfoRow("正文", n.body)
                        }
                    }
                    highlights.forEach { h ->
                        ExpandableRow(
                            title = "高亮 · ${(h.progress_percent ?: 0f).toInt()}%",
                            subtitle = h.text,
                        ) {
                            if (!h.note.isNullOrBlank()) InfoRow("笔记", h.note)
                            if (!h.chapter_title.isNullOrBlank()) InfoRow("章节", h.chapter_title)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                // S3：灵感区块
                SectionTitle("灵感 (${inspirations.size})")
                if (inspirations.isEmpty()) {
                    Text("还没有与本书相关的灵感。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    inspirations.forEach { ins ->
                        ExpandableRow(title = ins.title, subtitle = ins.body.take(40)) {
                            InfoRow("类型", ins.type)
                            if (ins.body.isNotBlank()) InfoRow("正文", ins.body)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("文件信息")
                InfoRow("原始文件名", book.original_file_name ?: "未知")
                InfoRow("导入时间", (book.imported_at ?: "未知").take(19).replace("T", " "))
                InfoRow("文件大小", formatBytes(book.size))
                InfoRow("格式", book.format.uppercase())
            }
            Spacer(modifier = Modifier.height(12.dp))

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("所在书单")
                if (shelves.isEmpty()) Text("尚未加入书单", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    shelves.forEach { shelf ->
                        InputChip(
                            selected = false, onClick = {},
                            label = { Text(shelf.name) },
                            trailingIcon = { IconButton(modifier = Modifier.size(18.dp), onClick = { onRemoveShelf(shelf.id) }) { Icon(Icons.Outlined.Close, contentDescription = "移除", modifier = Modifier.size(14.dp)) } },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("所属分类")
                if (categories.isEmpty()) Text("尚未设置分类", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { category ->
                        InputChip(
                            selected = false, onClick = {},
                            label = { Text(category.name) },
                            trailingIcon = { IconButton(modifier = Modifier.size(18.dp), onClick = { onRemoveCategory(category.id) }) { Icon(Icons.Outlined.Close, contentDescription = "移除", modifier = Modifier.size(14.dp)) } },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                // S4：书籍标签可增删（点击切换：已分配则移除，未分配则添加）
                SectionTitle("书籍标签 (${assignedTagIds.size})")
                if (allTags.isEmpty()) Text("还没有书籍标签。可以在“我的 / 标签管理”里创建类型为“书籍”的标签。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    allTags.forEach { tag ->
                        val assigned = assignedTagIds.contains(tag.id)
                        InputChip(
                            selected = assigned,
                            onClick = { if (assigned) onRemoveTag(tag.id) else onAddTag(tag.id) },
                            label = { Text(tag.name) },
                            trailingIcon = if (assigned) {
                                { IconButton(modifier = Modifier.size(18.dp), onClick = { onRemoveTag(tag.id) }) { Icon(Icons.Outlined.Close, contentDescription = "移除", modifier = Modifier.size(14.dp)) } }
                            } else null,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = AppError.copy(alpha = 0.08f),
                shape = LocalComponentSpec.current.listItemShape,
                border = BorderStroke(LocalComponentSpec.current.borderWidth, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().clickable { onDelete(book.id) },
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = AppError)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("删除本书", style = MaterialTheme.typography.bodyLarge, color = AppError)
                        Text("同时移除本机正文、进度、书签和笔记", style = MaterialTheme.typography.bodySmall, color = AppError)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        }
    }
}

@Composable
internal fun ExpandableRow(
    title: String,
    subtitle: String? = null,
    expandedContent: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AppIconSize.Small),
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = LocalComponentSpec.current.listItemShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp)) { expandedContent() }
            }
        }
    }
}

@Composable
internal fun ReadingHistoryChart(sessions: List<ReadingSessionEntity>) {
    if (sessions.isEmpty()) {
        Text("暂无阅读记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val dayLabel = java.time.format.DateTimeFormatter.ofPattern("M/d")
    val now = java.time.LocalDate.now()
    val days = (6 downTo 0).map { now.minusDays(it.toLong()) }
    val durationsByDay = sessions.groupBy { session ->
        val instant = runCatching { java.time.Instant.parse(session.created_at ?: session.started_at) }.getOrNull()
        instant?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()?.toString() ?: ""
    }.mapValues { entry -> entry.value.sumOf { it.duration_ms } }
    val maxDuration = durationsByDay.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier = Modifier.fillMaxWidth().height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            val key = day.toString()
            val duration = durationsByDay[key] ?: 0L
            val fraction = (duration.toFloat() / maxDuration).coerceIn(0f, 1f)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(ProgressBarShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    )
                }
                Text(dayLabel.format(day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(88.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun ChipRow(names: List<String>, active: List<String>, onClick: () -> Unit) {
    if (names.isEmpty()) {
        Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEach { name ->
            val isActive = active.contains(name)
            SelectablePill(
                text = (if (isActive) "✓ " else "") + name,
                selected = isActive,
                onClick = onClick,
            )
        }
    }
}
