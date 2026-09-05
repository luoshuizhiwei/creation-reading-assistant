package com.creationreadingassistant.ui.screen.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.components.SectionEmptyHint
import com.creationreadingassistant.ui.components.SectionHeader
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.listItemEnter
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.theme.rememberHaptic
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
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val cardInteraction = remember { MutableInteractionSource() }

    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        interactionSource = cardInteraction,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .width(260.dp)
            .bounceable(cardInteraction)
            .testTag("continue-card-${book.id}"),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                // heightIn 而非固定高度：大字号（fontScale 1.3+）下三行文本撑高时不裁切
                modifier = Modifier.heightIn(min = 88.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 左侧封面配 8dp 圆角、微细边框及立体书脊暗部渐变阴影（4dp 深度）
                BookCover(
                    book = book,
                    modifier = Modifier
                        .size(58.dp, 82.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = 0.8.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f),
                            shape = RoundedCornerShape(8.dp),
                        ),
                    shape = RoundedCornerShape(8.dp),
                    percent = null,
                    fallback = { MutedCoverFallback(book = book, maxTitleChars = 16, showFormat = false) },
                    overlay = {
                        // 立体书脊暗部渐变阴影（4dp 深度）
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
                    },
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = book.title,
                        // 默认无衬线：DisplayFontFamily 字形子集缺部分汉字，回退后忽粗忽细
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val author = book.author
                    if (!author.isNullOrBlank()) {
                        Text(
                            text = author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 进度百分比微胶囊标签（0% 未读、45% 等）
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    ) {
                        Text(
                            text = if (pct <= 0.05f) "0% 未读" else formatBookProgressForCard(pct),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
                // 进入箭头配微水波纹
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                // progress_percent 为 0-100，进度条需 0-1
                progress = { pct.coerceIn(0f, 100f) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .testTag("continue-progress-${book.id}"),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}
