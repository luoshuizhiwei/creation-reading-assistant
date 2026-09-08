package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.layout.adaptivePageMetrics
import com.creationreadingassistant.ui.theme.AppError
import com.creationreadingassistant.ui.theme.AppIconSize
import com.creationreadingassistant.ui.theme.AppSuccess
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import java.time.LocalDate

// ===================== 底部弹层：书籍详情 =====================
@OptIn(ExperimentalMaterial3Api::class)
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
    val layout = LocalLayoutTokens.current
    val percent = progressFor(progressById, book.id)
    val readiness = book.readiness()
    val progress = progressById[book.id]

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
                    .padding(bottom = 28.dp),
            ) {
                // 1. 书籍元数据卡片
                BookDetailHeaderSection(
                    book = book,
                    percent = percent,
                    readiness = readiness,
                    onUpdateBook = onUpdateBook,
                    onChangeCover = onChangeCover,
                    onChangeTextCover = onChangeTextCover,
                    onResetCover = onResetCover,
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2. 行为按钮（CTA重塑）
                BookDetailCtaSection(
                    book = book,
                    percent = percent,
                    readiness = readiness,
                    onContinue = onContinue,
                    onDownload = onDownload,
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 3. 阅读统计（4列独立微彩底座矩阵）
                BookDetailStatsSection(
                    progress = progress,
                    sessions = sessions,
                    percent = percent,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 4. 阅读记录（折叠微岛，解决失控倾泻）
                BookDetailSessionsSection(sessions = sessions)

                Spacer(modifier = Modifier.height(12.dp))

                // 5. 书签与笔记
                BookDetailNotesSection(notes = notes, highlights = highlights)

                Spacer(modifier = Modifier.height(12.dp))

                // 6. 灵感
                BookDetailInspirationsSection(inspirations = inspirations)

                Spacer(modifier = Modifier.height(12.dp))

                // 7. 文件信息
                BookDetailFileInfoSection(book = book)

                Spacer(modifier = Modifier.height(12.dp))

                // 8. 所在书单
                BookDetailShelvesSection(shelves = shelves, onRemoveShelf = onRemoveShelf)

                Spacer(modifier = Modifier.height(12.dp))

                // 9. 所属分类
                BookDetailCategoriesSection(categories = categories, onRemoveCategory = onRemoveCategory)

                Spacer(modifier = Modifier.height(12.dp))

                // 10. 书籍标签
                BookDetailTagsSection(
                    allTags = allTags,
                    assignedTagIds = assignedTagIds,
                    onAddTag = onAddTag,
                    onRemoveTag = onRemoveTag,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 11. 删除本书
                BookDetailDeleteSection(book = book, onDelete = onDelete)

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun BookDetailHeaderSection(
    book: BookEntity,
    percent: Float,
    readiness: BookReadiness,
    onUpdateBook: (String, String?, String?) -> Unit,
    onChangeCover: () -> Unit,
    onChangeTextCover: () -> Unit,
    onResetCover: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var editTitle by remember(book.title) { mutableStateOf(book.title) }
    var editAuthor by remember(book.author) { mutableStateOf(book.author ?: "") }
    var editDescription by remember(book.description) { mutableStateOf(book.description ?: "") }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 3.dp,
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
                    modifier = Modifier.size(76.dp, 106.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        BookCover(
                            book = book,
                            percent = percent,
                            modifier = Modifier.fillMaxSize(),
                            fallback = { ShelfCoverFallback(book) },
                        )
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.22f),
                                            Color.Black.copy(alpha = 0.06f),
                                            Color.Transparent,
                                        ),
                                    ),
                                ),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (editing) {
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("书名") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = editAuthor,
                            onValueChange = { editAuthor = it },
                            label = { Text("作者") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = editDescription,
                            onValueChange = { editDescription = it },
                            label = { Text("简介") },
                            singleLine = false,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            book.author ?: "作者未知",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!book.description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                book.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val tone = toneColor(readiness.tone)
                    Surface(
                        color = tone.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.5.dp, tone.copy(alpha = 0.25f)),
                    ) {
                        Text(
                            readiness.label,
                            color = tone,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (editing) {
                                onUpdateBook(editTitle, editAuthor.takeIf { it.isNotBlank() }, editDescription.takeIf { it.isNotBlank() })
                            }
                            editing = !editing
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (editing) Icons.Outlined.CheckCircle else Icons.Outlined.Edit,
                            contentDescription = if (editing) "保存" else "编辑",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (!editing) {
                Spacer(modifier = Modifier.height(12.dp))
                // 封面操作区：圆润微胶囊导轨
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverActionPill(
                        icon = Icons.Outlined.PhotoLibrary,
                        text = "更换封面",
                        onClick = onChangeCover,
                    )
                    CoverActionPill(
                        icon = Icons.Outlined.TextFields,
                        text = "文字封面",
                        onClick = onChangeTextCover,
                    )
                    if (!book.cover_data_url.isNullOrBlank()) {
                        CoverActionPill(
                            icon = Icons.Outlined.RestartAlt,
                            text = "重置封面",
                            onClick = onResetCover,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookDetailCtaSection(
    book: BookEntity,
    percent: Float,
    readiness: BookReadiness,
    onContinue: (BookEntity) -> Unit,
    onDownload: (BookEntity) -> Unit,
) {
    val isReady = readiness.tone == ReadinessTone.READY
    if (isReady) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧：主导型高质感微岛按钮
            Button(
                onClick = { onContinue(book) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (percent > 0f) "继续阅读" else "开始阅读",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // 右侧：典雅只读微徽章「正文已就绪」+ 轻量重新下载图标操作
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AppSuccess.copy(alpha = 0.08f),
                border = BorderStroke(0.8.dp, AppSuccess.copy(alpha = 0.28f)),
                modifier = Modifier.height(48.dp),
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppSuccess.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = AppSuccess,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "正文已就绪",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = AppSuccess,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // 轻量图标操作：支持重新下载，不诱导点击
                    IconButton(
                        onClick = { onDownload(book) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "重新下载",
                            tint = AppSuccess.copy(alpha = 0.75f),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    } else {
        // 未就绪：醒目展示「下载正文」主按钮
        Button(
            onClick = { onDownload(book) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "下载正文并阅读",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun BookDetailStatsSection(
    progress: ReadingProgressEntity?,
    sessions: List<ReadingSessionEntity>,
    percent: Float,
) {
    DetailIslandCard {
        SectionTitle("阅读统计")
        // 4 列独立微彩底座矩阵
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BookDetailStatCell(
                    icon = Icons.Outlined.AccessTime,
                    label = "总阅读时长",
                    value = formatDuration(bookDetailReadingTimeMs(progress?.total_reading_time_ms ?: 0L, sessions)),
                    tint = Color(0xFFB45309), // 琥珀暖色
                    modifier = Modifier.weight(1f),
                )
                BookDetailStatCell(
                    icon = Icons.AutoMirrored.Outlined.TrendingUp,
                    label = "阅读进度",
                    value = "${"%.1f".format(percent)}%",
                    tint = MaterialTheme.colorScheme.primary, // 墨绿原色
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BookDetailStatCell(
                    icon = Icons.Outlined.History,
                    label = "阅读次数",
                    value = "${sessions.size} 次",
                    tint = Color(0xFF2563EB), // 霁蓝青黛
                    modifier = Modifier.weight(1f),
                )
                BookDetailStatCell(
                    icon = Icons.Outlined.CalendarToday,
                    label = "上次阅读",
                    value = if (progress?.last_read_at != null) progress.last_read_at.take(10) else "从未阅读",
                    tint = Color(0xFF059669), // 松绿翡翠
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        ReadingHistoryChart(sessions = sessions)
    }
}

@Composable
private fun BookDetailSessionsSection(sessions: List<ReadingSessionEntity>) {
    val reducedMotion = rememberReducedMotion()
    val sortedSessions = remember(sessions) {
        sessions.sortedByDescending { it.started_at ?: it.created_at ?: "" }
    }
    var expandedAll by remember { mutableStateOf(false) }

    DetailIslandCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            SectionTitle("阅读记录", modifier = Modifier.weight(1f))
            if (sortedSessions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                ) {
                    Text(
                        "共 ${sortedSessions.size} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }

        if (sortedSessions.isEmpty()) {
            Text(
                "还没有阅读记录。开始阅读后，这里会显示每次阅读。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            val displaySessions = if (expandedAll) sortedSessions else sortedSessions.take(3)
            val todayStr = remember { LocalDate.now().toString() }
            val yesterdayStr = remember { LocalDate.now().minusDays(1).toString() }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = if (reducedMotion) snap() else tween(
                            durationMillis = 280,
                            easing = FastOutSlowInEasing,
                        ),
                    ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                displaySessions.forEach { s ->
                    SessionMicroIsland(
                        session = s,
                        todayStr = todayStr,
                        yesterdayStr = yesterdayStr,
                    )
                }
            }

            // 当记录多于3条时，提供平滑折叠/展开微胶囊
            if (sortedSessions.size > 3) {
                Spacer(modifier = Modifier.height(10.dp))
                val haptic = rememberHaptic(reducedMotion)
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            expandedAll = !expandedAll
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (expandedAll) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (!expandedAll) "展开更多记录 (共 ${sortedSessions.size} 条)" else "收起阅读记录",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 单条阅读记录卡片微岛化：紧凑圆润微徽章展示时长与进度，发丝描边 */
@Composable
private fun SessionMicroIsland(
    session: ReadingSessionEntity,
    todayStr: String,
    yesterdayStr: String,
) {
    val reducedMotion = rememberReducedMotion()
    var itemExpanded by remember { mutableStateOf(false) }
    val rawTime = (session.started_at ?: session.created_at ?: "未知时间").take(16).replace("T", " ")
    val datePart = rawTime.take(10)
    val timePart = if (rawTime.length >= 16) rawTime.substring(11, 16) else ""
    val dateLabel = when (datePart) {
        todayStr -> "今天"
        yesterdayStr -> "昨天"
        else -> if (datePart.length >= 10) datePart.substring(5) else datePart
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { itemExpanded = !itemExpanded },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .animateContentSize(animationSpec = if (reducedMotion) snap() else tween()),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 左侧：日期与时间
                Text(
                    text = "$dateLabel $timePart".trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.weight(1f))

                // 时长微徽章
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFB45309).copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, Color(0xFFB45309).copy(alpha = 0.20f)),
                ) {
                    Text(
                        text = formatDuration(session.duration_ms),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB45309),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 进度微徽章
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                ) {
                    Text(
                        text = "进度 ${(session.progress_percent ?: 0f).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    if (itemExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                    modifier = Modifier.size(16.dp),
                )
            }

            if (itemExpanded) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        InfoRow("开始时间", (session.started_at ?: "-").take(19).replace("T", " "))
                        InfoRow("结束时间", (session.ended_at ?: "-").take(19).replace("T", " "))
                    }
                }
            }
        }
    }
}

@Composable
private fun BookDetailNotesSection(
    notes: List<NoteEntity>,
    highlights: List<HighlightEntity>,
) {
    val bookmarks = notes.filter { it.kind == "bookmark" }
    val noteList = notes.filter { it.kind != "bookmark" }
    DetailIslandCard {
        SectionTitle("书签与笔记 · ${bookmarks.size} 书签 · ${noteList.size + highlights.size} 条")
        if (bookmarks.isEmpty() && noteList.isEmpty() && highlights.isEmpty()) {
            Text(
                "阅读时点“书签”或“笔记”，这本书的沉淀会集中在这里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            bookmarks.forEach { n ->
                ExpandableRow(
                    title = "书签 · ${(n.progress_percent ?: 0f).toInt()}%",
                    subtitle = n.excerpt ?: n.body ?: n.chapter_title ?: "当前位置",
                ) {
                    if (!n.chapter_title.isNullOrBlank()) InfoRow("章节", n.chapter_title)
                    if (n.body.isNotBlank()) InfoRow("正文", n.body)
                }
            }
            noteList.forEach { n ->
                ExpandableRow(
                    title = "笔记 · ${(n.progress_percent ?: 0f).toInt()}%",
                    subtitle = n.excerpt ?: n.body ?: n.chapter_title ?: "当前位置",
                ) {
                    if (!n.chapter_title.isNullOrBlank()) InfoRow("章节", n.chapter_title)
                    if (n.body.isNotBlank()) InfoRow("正文", n.body)
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
}

@Composable
private fun BookDetailInspirationsSection(inspirations: List<InspirationEntity>) {
    DetailIslandCard {
        SectionTitle("灵感 (${inspirations.size})")
        if (inspirations.isEmpty()) {
            Text(
                "还没有与本书相关的灵感。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            inspirations.forEach { ins ->
                ExpandableRow(title = ins.title, subtitle = ins.body.take(40)) {
                    InfoRow("类型", ins.type)
                    if (ins.body.isNotBlank()) InfoRow("正文", ins.body)
                }
            }
        }
    }
}

@Composable
private fun BookDetailFileInfoSection(book: BookEntity) {
    DetailIslandCard {
        SectionTitle("文件信息")
        InfoRow("原始文件名", book.original_file_name ?: "未知")
        InfoRow("导入时间", (book.imported_at ?: "未知").take(19).replace("T", " "))
        InfoRow("文件大小", formatBytes(book.size))
        InfoRow("格式", book.format.uppercase())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookDetailShelvesSection(
    shelves: List<ShelfEntity>,
    onRemoveShelf: (String) -> Unit,
) {
    DetailIslandCard {
        SectionTitle("所在书单")
        if (shelves.isEmpty()) {
            Text("尚未加入书单", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                shelves.forEach { shelf ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(shelf.name) },
                        shape = RoundedCornerShape(999.dp),
                        trailingIcon = {
                            IconButton(
                                modifier = Modifier.size(18.dp),
                                onClick = { onRemoveShelf(shelf.id) },
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "移除", modifier = Modifier.size(13.dp))
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookDetailCategoriesSection(
    categories: List<CategoryEntity>,
    onRemoveCategory: (String) -> Unit,
) {
    DetailIslandCard {
        SectionTitle("所属分类")
        if (categories.isEmpty()) {
            Text("尚未设置分类", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { category ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(category.name) },
                        shape = RoundedCornerShape(999.dp),
                        trailingIcon = {
                            IconButton(
                                modifier = Modifier.size(18.dp),
                                onClick = { onRemoveCategory(category.id) },
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "移除", modifier = Modifier.size(13.dp))
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookDetailTagsSection(
    allTags: List<TagEntity>,
    assignedTagIds: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    DetailIslandCard {
        SectionTitle("书籍标签 (${assignedTagIds.size})")
        if (allTags.isEmpty()) {
            Text(
                "还没有书籍标签。可以在“我的 / 标签管理”里创建类型为“书籍”的标签。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                allTags.forEach { tag ->
                    val assigned = assignedTagIds.contains(tag.id)
                    InputChip(
                        selected = assigned,
                        shape = RoundedCornerShape(999.dp),
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            if (assigned) onRemoveTag(tag.id) else onAddTag(tag.id)
                        },
                        label = { Text(tag.name) },
                        trailingIcon = if (assigned) {
                            {
                                IconButton(
                                    modifier = Modifier.size(18.dp),
                                    onClick = { onRemoveTag(tag.id) },
                                ) {
                                    Icon(Icons.Outlined.Close, contentDescription = "移除", modifier = Modifier.size(13.dp))
                                }
                            }
                        } else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookDetailDeleteSection(
    book: BookEntity,
    onDelete: (String) -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    Surface(
        color = AppError.copy(alpha = 0.05f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.8.dp, AppError.copy(alpha = 0.22f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                haptic(HapticFeedbackType.LongPress)
                onDelete(book.id)
            },
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppError.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = AppError, modifier = Modifier.size(AppIconSize.Medium))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("删除本书", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = AppError)
                Text("同时移除本机正文、进度、书签和笔记", style = MaterialTheme.typography.bodySmall, color = AppError.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun DetailIslandCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content,
        )
    }
}

@Composable
private fun CoverActionPill(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 0.5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
            .padding(vertical = 7.dp),
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(AppIconSize.Small),
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
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
    val now = LocalDate.now()
    val days = (6 downTo 0).map { now.minusDays(it.toLong()) }
    val durationsByDay = sessions.groupBy { session ->
        val instant = runCatching { java.time.Instant.parse(session.created_at ?: session.started_at) }.getOrNull()
        instant?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()?.toString() ?: ""
    }.mapValues { entry -> entry.value.sumOf { it.duration_ms } }
    val maxDuration = durationsByDay.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            val key = day.toString()
            val duration = durationsByDay[key] ?: 0L
            val fraction = if (maxDuration > 0) (duration.toFloat() / maxDuration).coerceIn(0f, 1f) else 0f
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 3.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction.coerceAtLeast(0.08f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        ),
                                    ),
                                ),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(width = 8.dp, height = 3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(dayLabel.format(day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BookDetailStatCell(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.07f),
        border = BorderStroke(0.8.dp, tint.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(bottom = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 13.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun ChipRow(names: List<String>, active: List<String>, onClick: () -> Unit) {
    if (names.isEmpty()) {
        Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
