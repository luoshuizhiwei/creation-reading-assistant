package com.creationreadingassistant.ui.screen.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import com.creationreadingassistant.ui.theme.AppIconSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.R
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.MutedCoverFallback
import com.creationreadingassistant.ui.components.SectionCard
import com.creationreadingassistant.ui.components.SectionEmptyHint
import com.creationreadingassistant.ui.components.SectionHeader
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.listItemEnter
import com.creationreadingassistant.ui.theme.DisplayFontFamily
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.util.formatBookProgressForCard

/**
 * 继续阅读 Section（首页第一块）。
 *
 * 只负责本区块自己的布局：
 * - SectionHeader（标题 + 右侧"管理继续阅读"按钮）
 * - 空态或 LazyRow（横向 ContinueCard）
 *
 * **不**嵌套页面级 Scaffold/Card（卡片级 SectionCard 只包裹单条 ContinueCard）。
 */
@Composable
fun HomeContinueSection(
    continueBooks: List<BookEntity>,
    progressById: Map<String, ReadingProgressEntity>,
    onOpenBook: (BookEntity) -> Unit,
    onOpenContinueSheet: () -> Unit,
    onEmptyNavigateShelf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    Column(modifier) {
        SectionHeader(
            title = stringResource(R.string.home_continue),
            modifier = Modifier.animateEnter(60, reducedMotion),
            action = {
                TextButton(
                    onClick = onOpenContinueSheet,
                    modifier = Modifier.testTag("continue-manage-btn"),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                }
            },
        )
        if (continueBooks.isEmpty()) {
            SectionEmptyHint(
                text = "书架还空着，导入 TXT、Markdown 或 EPUB 开始本地阅读。",
                actionText = "去书架",
                onAction = onEmptyNavigateShelf,
                modifier = Modifier
                    .animateEnter(60, reducedMotion)
                    .testTag("continue-empty"),
            )
        } else {
            LazyRow(
                modifier = Modifier
                    .animateEnter(60, reducedMotion)
                    .testTag("continue-lazy-row"),
                horizontalArrangement = Arrangement.spacedBy(LocalLayoutTokens.current.contentGap),
            ) {
                itemsIndexed(continueBooks, key = { _, book -> "continue-${book.id}" }) { index, book ->
                    ContinueCard(
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
private fun ContinueCard(
    book: BookEntity,
    progress: ReadingProgressEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pct = progress?.progress_percent ?: 0f
    val layout = LocalLayoutTokens.current
    SectionCard(
        onClick = onClick,
        modifier = modifier
            .width(252.dp)
            .testTag("continue-card-${book.id}"),
        contentPadding = layout.compactCardPadding,
    ) {
        Column {
            Row(
                Modifier.height(88.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(layout.contentGap),
            ) {
                BookCover(
                    book = book,
                    modifier = Modifier.size(56.dp, 80.dp),
                    percent = null,
                    fallback = { MutedCoverFallback(book = book, maxTitleChars = 4, showFormat = false) },
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        book.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = DisplayFontFamily),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val author = book.author
                    if (!author.isNullOrBlank()) {
                        Text(
                            author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        formatBookProgressForCard(pct),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AppIconSize.Large),
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { pct.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .testTag("continue-progress-${book.id}"),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}
