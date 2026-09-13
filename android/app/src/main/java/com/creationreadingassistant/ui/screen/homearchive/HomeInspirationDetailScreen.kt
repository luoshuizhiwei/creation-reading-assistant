package com.creationreadingassistant.ui.screen.homearchive

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.creationreadingassistant.ui.util.copyText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.ui.components.AppScreenScaffold
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.viewmodel.HomeArchiveViewModel
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import com.creationreadingassistant.ui.viewmodel.InspirationViewModel

@Composable
fun HomeInspirationDetailRoute(
    navController: NavHostController,
    inspirationId: String,
    viewModel: HomeArchiveViewModel = hiltViewModel(),
    inspirationViewModel: InspirationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val inspiration = state.inspirations.firstOrNull { it.id == inspirationId }
    val books by inspirationViewModel.books.collectAsStateWithLifecycle()

    InspirationDetailScreen(
        inspiration = inspiration,
        allBooks = books,
        onBack = { navController.popBackStack() },
        onOpenBook = { bookId -> navController.navigate("reader/$bookId") },
        onDelete = { id ->
            inspirationViewModel.deleteInspiration(id)
            navController.popBackStack()
        },
    )
}

@Composable
internal fun InspirationDetailScreen(
    inspiration: InspirationEntity?,
    allBooks: List<BookEntity>,
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val layout = LocalLayoutTokens.current
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }

    val payloadData = remember(inspiration) { parseInspirationPayload(inspiration) }
    val source = payloadData.source
    val bookId = inspiration?.source_book_id ?: source?.bookId
    val excerptText = source?.excerpt?.takeIf { it.isNotBlank() }
        ?: (if (inspiration?.type == "excerpt") inspiration.body.takeIf { it.isNotBlank() } else null)
    val thoughtText = if (inspiration?.type == "excerpt" && source?.excerpt.isNullOrBlank()) "" else (inspiration?.body ?: "")

    BackHandler { onBack() }

    AppScreenScaffold(
        title = "灵感详情",
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
        modifier = Modifier.testTag("home-inspiration-detail-screen"),
    ) { viewportPadding ->
        PageLazyColumn(
            scaffoldPadding = viewportPadding,
            verticalArrangement = Arrangement.spacedBy(layout.contentGap),
        ) {
            if (inspiration == null) {
                item(key = "empty", contentType = "empty") {
                    FullEmptyState(
                        icon = { LineArtBook(Modifier.size(52.dp)) },
                        title = "灵感不存在",
                        body = "它可能已被删除或尚未加载完成。",
                        contentPadding = 28.dp,
                    )
                }
            } else {
                // 1. 摘录原句卡片（Excerpt Card）
                if (!excerptText.isNullOrBlank()) {
                    item(key = "excerpt-card", contentType = "excerpt-card") {
                        ExcerptCard(
                            excerptText = excerptText,
                            chapterTitle = source?.chapterTitle,
                        )
                    }
                }

                // 2. 笔记想法卡片（Thought Card）
                item(key = "thought-card", contentType = "thought-card") {
                    ThoughtCard(
                        inspiration = inspiration,
                        thoughtText = thoughtText,
                        tags = payloadData.tags,
                    )
                }

                // 3. 关联书籍微岛卡片（Book Source Island）
                if (source != null || !bookId.isNullOrBlank()) {
                    item(key = "book-source-island", contentType = "book-source-island") {
                        BookSourceIsland(
                            bookId = bookId,
                            source = source,
                            allBooks = allBooks,
                            onOpenBook = onOpenBook,
                        )
                    }
                }

                // 4. 底部操作栏（微胶囊化复制、分享与删除）
                item(key = "actions", contentType = "actions") {
                    val textToCopy = listOfNotNull(
                        inspiration.title.takeIf { it.isNotBlank() },
                        excerptText?.let { "“$it”" },
                        thoughtText.takeIf { it.isNotBlank() },
                    ).joinToString("\n\n")

                    val textToShare = listOfNotNull(
                        inspiration.title.takeIf { it.isNotBlank() },
                        excerptText?.let { "“$it”" },
                        thoughtText.takeIf { it.isNotBlank() },
                        source?.bookTitle?.let { "—— 摘自《$it》" },
                    ).joinToString("\n\n")

                    InspirationDetailActionRow(
                        onCopy = {
                            clipboard.copyText(textToCopy, coroutineScope, "inspiration")
                            Toast.makeText(context, "已复制灵感内容到剪贴板", Toast.LENGTH_SHORT).show()
                        },
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, textToShare)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(intent, "分享灵感"))
                        },
                        onDeleteClick = { showDeleteDialog = true },
                    )
                }

                item(key = "bottom-space", contentType = "spacer") {
                    Spacer(Modifier.height(layout.pageVertical))
                }
            }
        }
    }

    if (showDeleteDialog && inspiration != null) {
        GlassAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除此灵感？") },
            text = { Text("删除后将从灵感库中彻底移除，无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(inspiration.id)
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
internal fun ExcerptCard(
    excerptText: String,
    chapterTitle: String?,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            // 左侧 3.5dp 墨线竖标
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight()
                    .background(ExcerptAccentVerticalBrush),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF059669).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.FormatQuote,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Text(
                        text = "原句摘录",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669),
                    )
                    if (!chapterTitle.isNullOrBlank()) {
                        Text(
                            text = "· $chapterTitle",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = "“$excerptText”",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 26.sp,
                        letterSpacing = 0.3.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThoughtCard(
    inspiration: InspirationEntity,
    thoughtText: String,
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    val accentColor = when (inspiration.type) {
        "setting" -> Color(0xFF7C3AED)
        "plot" -> Color(0xFFD97706)
        "excerpt" -> Color(0xFF059669)
        else -> MaterialTheme.colorScheme.primary
    }

    SectionCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 16.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 顶部信息：类型、状态与更新时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.3f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
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
                    if (inspiration.status.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = when (inspiration.status) {
                                    "inbox" -> "收件箱"
                                    "archived" -> "已归档"
                                    "draft" -> "草稿"
                                    else -> inspiration.status
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                Text(
                    text = "更新于 ${formatDetailTime(inspiration.updated_at)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }

            // 标题
            if (inspiration.title.isNotBlank()) {
                Text(
                    text = inspiration.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // 想法内容
            if (thoughtText.isNotBlank()) {
                Text(
                    text = thoughtText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 26.sp,
                        letterSpacing = 0.2.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // 标签微胶囊
            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tags.forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BookSourceIsland(
    bookId: String?,
    source: InspirationSourceInfo?,
    allBooks: List<BookEntity>,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val matchedBook = remember(allBooks, bookId, source) {
        val targetId = bookId ?: source?.bookId
        allBooks.firstOrNull { it.id == targetId }
    }
    val title = matchedBook?.title ?: source?.bookTitle ?: "未知书籍"
    val author = matchedBook?.author?.takeIf { it.isNotBlank() } ?: source?.bookAuthor ?: "未知作者"
    val format = matchedBook?.format ?: "EPUB"
    val chapter = source?.chapterTitle ?: source?.locationLabel
    val effectiveBookId = bookId ?: source?.bookId

    SectionCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 14.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 封面或底座
            if (matchedBook != null) {
                BookCover(
                    book = matchedBook,
                    modifier = Modifier
                        .size(46.dp, 64.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    percent = null,
                    fallback = {
                        Text(
                            matchedBook.title.take(3),
                            modifier = Modifier.align(Alignment.Center).padding(2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp, 64.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Book,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "来源书籍",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = format.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                if (!chapter.isNullOrBlank()) {
                    Text(
                        text = chapter,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!effectiveBookId.isNullOrBlank()) {
                Surface(
                    onClick = { onOpenBook(effectiveBookId) },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AutoStories,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "打开书籍",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun InspirationDetailActionRow(
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onCopy,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "复制正文",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Surface(
            onClick = onShare,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "分享灵感",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Surface(
            onClick = onDeleteClick,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除灵感",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
