package com.creationreadingassistant.ui.screen.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.R
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.FullEmptyState
import com.creationreadingassistant.ui.components.LineArtBook
import com.creationreadingassistant.ui.components.SectionHeader
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.listItemEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion

/**
 * 已阅读完成 Section（首页第四块）。
 *
 * 只负责自己的布局：标题 + 右侧查看全部 + LazyRow CompletedCard，
 * 空态或卡片列表。
 */
@Composable
fun HomeCompletedSection(
    completedBooks: List<BookEntity>,
    progressById: Map<String, ReadingProgressEntity>,
    onOpenBook: (BookEntity) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    Column(modifier) {
        SectionHeader(
            title = stringResource(R.string.home_completed),
            modifier = Modifier
                .animateEnter(240, reducedMotion)
                .testTag("completed-header"),
            action = {
                TextButton(
                    onClick = onSeeAll,
                    modifier = Modifier.testTag("completed-see-all"),
                ) {
                    Text(stringResource(R.string.home_see_all))
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.height(16.dp),
                    )
                }
            },
        )
        if (completedBooks.isEmpty()) {
            FullEmptyState(
                icon = { LineArtBook(sizeDp = 72.dp) },
                title = "还没有读完的书",
                body = "继续阅读吧，读完的书会陈列在这里。",
                modifier = Modifier
                    .fillMaxWidth()
                    .animateEnter(240, reducedMotion)
                    .testTag("completed-empty"),
            )
        } else {
            LazyRow(
                modifier = Modifier
                    .animateEnter(240, reducedMotion)
                    .testTag("completed-lazy-row"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(completedBooks, key = { _, book -> "completed-${book.id}" }) { index, book ->
                    CompletedCard(
                        book = book,
                        progress = progressById[book.id],
                        onClick = { onOpenBook(book) },
                        modifier = Modifier.listItemEnter(index, reducedMotion),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedCard(
    book: BookEntity,
    progress: ReadingProgressEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(116.dp)
            .clickable(onClick = onClick)
            .testTag("completed-card-${book.id}"),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BookCover(
            book = book,
            modifier = Modifier.size(84.dp, 112.dp),
            percent = null,
            fallback = {
                Text(
                    book.title.take(2),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                book.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!book.author.isNullOrBlank()) {
                Text(
                    book.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "已读完",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
