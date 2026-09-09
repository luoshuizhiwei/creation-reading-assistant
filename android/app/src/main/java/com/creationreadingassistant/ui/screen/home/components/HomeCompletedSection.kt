package com.creationreadingassistant.ui.screen.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.R
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.components.MutedCoverFallback
import com.creationreadingassistant.ui.components.SectionEmptyHint
import com.creationreadingassistant.ui.components.SectionHeader
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.LocalComponentSpec
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
            SectionEmptyHint(
                text = "读完的书会陈列在这里。",
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier
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
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val spec = LocalComponentSpec.current
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .width(116.dp)
            .bounceable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .testTag("completed-card-${book.id}"),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 封面圆角走 pedestalRadius 令牌，右上角翡翠绿「已读完」微徽章 / SealMark 质感印章徽标
        BookCover(
            book = book,
            modifier = Modifier
                .size(84.dp, 116.dp)
                .clip(RoundedCornerShape(spec.pedestalRadius))
                .border(
                    width = spec.hairlineBorderWidth,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha),
                    shape = RoundedCornerShape(spec.pedestalRadius),
                ),
            shape = RoundedCornerShape(spec.pedestalRadius),
            percent = null,
            fallback = { MutedCoverFallback(book = book, maxTitleChars = 8, showFormat = false) },
            overlay = {
                // 1. 立体书脊暗部渐变阴影（4dp 深度）
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.38f),
                                    Color.Black.copy(alpha = 0.12f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                // 2. 右上角翡翠绿「已读完」SealMark 质感印章徽标
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF059669).copy(alpha = 0.18f))
                        .border(
                            width = spec.hairlineBorderWidth,
                            color = Color(0xFF059669).copy(alpha = 0.85f),
                            shape = RoundedCornerShape(3.dp),
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "已读完",
                        color = Color(0xFF059669),
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        letterSpacing = 0.5.sp,
                    )
                }
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val author = book.author
            if (!author.isNullOrBlank()) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF059669)),
                )
                Text(
                    text = "已读完",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF059669),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
