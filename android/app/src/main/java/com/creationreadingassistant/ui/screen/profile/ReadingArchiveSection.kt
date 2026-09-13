package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.IslandCard
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.MutedCoverFallback
import com.creationreadingassistant.ui.components.PageLazyColumn
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.listItemEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.util.formatDuration
import kotlin.math.roundToInt

// ============================== 阅读档案（READING，原行为） ==============================

@Composable
internal fun ReadingArchiveList(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    scaffoldPadding: PaddingValues,
) {
    val reducedMotion = rememberReducedMotion()
    val libraryState = state.libraryState
    val books = libraryState.books
    val bookMap = remember(books) { books.associateBy { it.id } }
    val progressMap = libraryState.progressByBook
    val sessionsByBook = libraryState.readingDurationByBook
    val readingBooks = remember(books, progressMap) {
        books.sortedByDescending { progressMap[it.id]?.last_read_at ?: it.updated_at }
    }

    PageLazyColumn(
        scaffoldPadding = scaffoldPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (readingBooks.isEmpty()) {
            item {
                FullEmptyState(
                    icon = { LineArtBook(sizeDp = 72.dp) },
                    title = "还没有阅读记录",
                    body = "打开任意书籍开始阅读后，这里会按最近阅读时间展示档案。",
                )
            }
        } else {
            readingBooks.forEachIndexed { index, book ->
                item(key = book.id) {
                    val p = progressMap[book.id]
                    Box(Modifier.fillMaxWidth().listItemEnter(index, reducedMotion)) {
                        ReadingBookItem(
                            book = book,
                            progress = p,
                            totalMs = sessionsByBook[book.id] ?: 0L,
                            onClick = { onAction(ProfileAction.OpenBook(book.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReadingBookItem(
    book: BookEntity,
    progress: ReadingProgressEntity?,
    totalMs: Long,
    onClick: () -> Unit,
) {
    val pct = (progress?.progress_percent ?: 0f).roundToInt().coerceIn(0, 100)
    val spec = LocalComponentSpec.current
    IslandCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = 12.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 书脊 3D 微阴影封面
                Box(
                    modifier = Modifier.shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(5.dp),
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.25f),
                        spotColor = Color.Black.copy(alpha = 0.35f),
                    ),
                ) {
                    BookCover(
                        book = book,
                        modifier = Modifier
                            .width(46.dp)
                            .height(64.dp),
                        shape = RoundedCornerShape(5.dp),
                        fallback = {
                            MutedCoverFallback(book = book, showFormat = false)
                        },
                    )
                }

                // 书籍信息 + 胶囊栏
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!book.author.isNullOrBlank()) {
                        Text(
                            text = book.author,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(2.dp))

                    // 徽章与微胶囊栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 进度高亮微胶囊
                        Surface(
                            shape = spec.pillShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.primary.copy(alpha = spec.hairlineAlpha)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.AutoStories,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "进度 $pct%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        // 累计阅读时长暖琥珀微徽章
                        if (totalMs > 0L) {
                            Surface(
                                shape = spec.pillShape,
                                color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                                border = BorderStroke(spec.hairlineBorderWidth, Color(0xFFF59E0B).copy(alpha = spec.hairlineAlpha)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = Color(0xFFD97706),
                                    )
                                    Text(
                                        text = "累计 ${formatDuration(totalMs)}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 11.sp,
                                        ),
                                        color = Color(0xFFD97706),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 底部细长进度条与上次阅读时间
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { (pct / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Text(
                            text = progress?.last_read_at?.let { "上次阅读：${formatDateTime(it)}" } ?: "尚未开始阅读",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
