package com.creationreadingassistant.ui.screen.homearchive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SelectablePill
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.viewmodel.HomeArchiveViewModel

@Composable
fun HomeInspirationsRoute(
    navController: NavHostController,
    viewModel: HomeArchiveViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(InspirationArchiveSort.UPDATED) }
    val visible = remember(state.inspirations, query, sort) {
        state.inspirations.filter {
            query.isBlank() || "${it.title} ${it.body}".contains(query.trim(), ignoreCase = true)
        }.let { list ->
            when (sort) {
                InspirationArchiveSort.UPDATED -> list.sortedByDescending { it.updated_at }
                InspirationArchiveSort.CREATED -> list.sortedByDescending { it.created_at }
                InspirationArchiveSort.TITLE -> list.sortedBy { it.title }
            }
        }
    }
    InspirationArchiveScreen(
        inspirations = visible,
        totalCount = state.inspirations.size,
        query = query,
        sort = sort,
        onBack = { navController.popBackStack() },
        onQueryChange = { query = it },
        onSelectSort = { sort = it },
        onOpen = { navController.navigate("home/inspiration/${it.id}") },
        onOpenBook = { bookId -> navController.navigate("reader/$bookId") },
    )
}

@Composable
internal fun InspirationArchiveScreen(
    inspirations: List<InspirationEntity>,
    totalCount: Int,
    query: String,
    sort: InspirationArchiveSort,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectSort: (InspirationArchiveSort) -> Unit,
    onOpen: (InspirationEntity) -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val layout = LocalLayoutTokens.current
    AppScreenScaffold(
        title = "全量灵感",
        navigationIcon = {
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        modifier = Modifier.testTag("home-inspirations-screen"),
    ) { viewportPadding ->
        PageLazyColumn(
            scaffoldPadding = viewportPadding,
            verticalArrangement = Arrangement.spacedBy(layout.contentGap),
        ) {
            // 1. 顶部搜索微岛
            item(key = "search", contentType = "search") {
                ArchiveSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholderText = "搜索 $totalCount 条灵感记录...",
                )
            }

            // 2. 排序微胶囊导轨
            item(key = "sort-rail", contentType = "sort-rail") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SelectablePill(
                        text = "更新时间",
                        selected = sort == InspirationArchiveSort.UPDATED,
                        onClick = { onSelectSort(InspirationArchiveSort.UPDATED) },
                    )
                    SelectablePill(
                        text = "创建时间",
                        selected = sort == InspirationArchiveSort.CREATED,
                        onClick = { onSelectSort(InspirationArchiveSort.CREATED) },
                    )
                    SelectablePill(
                        text = "标题",
                        selected = sort == InspirationArchiveSort.TITLE,
                        onClick = { onSelectSort(InspirationArchiveSort.TITLE) },
                    )
                }
            }

            // 3. 统计状态行
            item(key = "summary", contentType = "summary") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (query.isBlank()) "共 $totalCount 条灵感" else "筛选出 ${inspirations.size} 条灵感",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 4. 列表项 / 空状态
            if (inspirations.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    FullEmptyState(
                        icon = { LineArtBook(Modifier.size(52.dp)) },
                        title = if (query.isBlank()) "还没有灵感" else "没有找到灵感",
                        body = if (query.isBlank()) "阅读时保存的灵感会按时间顺序整理在这里。" else "换个搜索词试试吧。",
                        contentPadding = 28.dp,
                    )
                }
            } else {
                items(
                    items = inspirations,
                    key = { it.id },
                    contentType = { "inspiration-card" },
                ) { inspiration ->
                    InspirationArchiveCard(
                        inspiration = inspiration,
                        onOpenDetail = { onOpen(inspiration) },
                        onOpenBook = onOpenBook,
                    )
                }
            }

            item(key = "bottom-space", contentType = "spacer") { Spacer(Modifier.height(layout.pageVertical)) }
        }
    }
}

@Composable
internal fun InspirationArchiveCard(
    inspiration: InspirationEntity,
    onOpenDetail: () -> Unit,
    onOpenBook: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val payloadData = remember(inspiration) { parseInspirationPayload(inspiration) }
    val source = payloadData.source
    val bookId = inspiration.source_book_id ?: source?.bookId
    val bookTitle = source?.bookTitle
    val chapter = source?.chapterTitle ?: source?.locationLabel

    val accentColor = when (inspiration.type) {
        "setting" -> Color(0xFF7C3AED)
        "plot" -> Color(0xFFD97706)
        "excerpt" -> Color(0xFF059669)
        else -> MaterialTheme.colorScheme.primary
    }

    val accentGradientBrush = remember(accentColor) {
        Brush.verticalGradient(
            listOf(
                accentColor,
                accentColor.copy(alpha = 0.35f),
            )
        )
    }

    SectionCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("inspiration-archive-${inspiration.id}"),
        onClick = onOpenDetail,
        contentPadding = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            // 左侧 3.5dp 典雅墨线竖标
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight()
                    .background(accentGradientBrush),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 顶部小行：类型微胶囊 + 格式化时间戳
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.3f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(RoundedCornerShape(2.5.dp))
                                    .background(accentColor),
                            )
                            Text(
                                text = getTypeLabel(inspiration.type),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor,
                            )
                        }
                    }
                    Text(
                        text = formatInspirationTime(inspiration.updated_at),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }

                // 灵感标题
                Text(
                    text = inspiration.title.ifBlank { "未命名灵感" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // 正文摘录排版典雅
                val excerpt = source?.excerpt
                val displayText = inspiration.body.ifBlank { excerpt ?: "" }
                if (displayText.isNotBlank()) {
                    Text(
                        text = if (inspiration.type == "excerpt" || !excerpt.isNullOrBlank()) "“$displayText”" else displayText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = if (inspiration.type == "excerpt" || !excerpt.isNullOrBlank()) FontFamily.Serif else FontFamily.Default,
                            fontStyle = if (inspiration.type == "excerpt" || !excerpt.isNullOrBlank()) FontStyle.Italic else FontStyle.Normal,
                            lineHeight = 22.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 关联书籍微胶囊与章节位置
                if (!bookTitle.isNullOrBlank() || !chapter.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (!bookTitle.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.MenuBook,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = "《$bookTitle》",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        if (!chapter.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    text = chapter,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                // 操作行（打开书籍、查看详情）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (!bookId.isNullOrBlank() && onOpenBook != null) {
                        Surface(
                            onClick = { onOpenBook(bookId) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.AutoStories,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "打开书籍",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Surface(
                        onClick = onOpenDetail,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "查看详情",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
