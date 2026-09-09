package com.creationreadingassistant.ui.screen.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.R
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.ui.components.IconPedestal
import com.creationreadingassistant.ui.components.SectionEmptyHint
import com.creationreadingassistant.ui.components.SectionHeader
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

/**
 * 最近灵感 Section（首页第三块）。
 *
 * 只负责自己的布局：标题 + 右侧查看全部 + 灵感列表。
 * 灵感点击回调：onOpenDetail(inspId)；"查看全部"回调：onSeeAll()。
 */
@Composable
fun HomeInspirationSection(
    inspirations: List<InspirationEntity>,
    onSeeAll: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    Column(modifier) {
        SectionHeader(
            title = stringResource(R.string.home_recent_inspiration),
            modifier = Modifier
                .animateEnter(180, reducedMotion)
                .testTag("inspiration-header"),
            action = {
                TextButton(
                    onClick = onSeeAll,
                    modifier = Modifier.testTag("inspiration-see-all"),
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
        if (inspirations.isEmpty()) {
            SectionEmptyHint(
                text = "还没有灵感，阅读时选中文字即可保存为灵感。",
                leadingIcon = {
                    IconPedestal(
                        icon = Icons.Outlined.Lightbulb,
                        tint = Color(0xFFD97706),
                        size = 28.dp,
                        iconSize = 16.dp,
                    )
                },
                modifier = Modifier
                    .animateEnter(180, reducedMotion)
                    .testTag("inspiration-empty"),
            )
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.testTag("inspiration-items-stub"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(inspirations, key = { "insp-item-${it.id}" }) { insp ->
                    HomeInspirationItem(
                        inspiration = insp,
                        onClick = { onOpenDetail(insp.id) },
                        enterDelayMs = 180,
                    )
                }
            }
        }
    }
}

/**
 * 单条灵感微岛卡片。采用暖金灵感微彩底座、细微发丝边框与金句字体排版。
 */
@Composable
fun HomeInspirationItem(
    inspiration: InspirationEntity,
    onClick: () -> Unit,
    enterDelayMs: Int = 180,
) {
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val spec = LocalComponentSpec.current
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        interactionSource = interactionSource,
        shape = spec.listItemShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            spec.hairlineBorderWidth,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .animateEnter(enterDelayMs, reducedMotion)
            .bounceable(interactionSource)
            .testTag("insp-item-${inspiration.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFD97706).copy(alpha = 0.05f),
                            Color(0xFFF59E0B).copy(alpha = 0.02f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 暖金灵感微彩底座小图标：圆角/发丝边框走令牌，渐变属专属艺术保留
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(spec.pedestalRadius))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFFF59E0B).copy(alpha = 0.20f),
                                Color(0xFFD97706).copy(alpha = 0.10f),
                            ),
                        ),
                    )
                    .border(
                        spec.hairlineBorderWidth,
                        Color(0xFFD97706).copy(alpha = 0.25f),
                        RoundedCornerShape(spec.pedestalRadius),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.FormatQuote,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = inspiration.title.ifBlank { "无标题灵感" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (inspiration.body.isNotBlank()) {
                    Text(
                        text = "“${inspiration.body.take(80)}”",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 18.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
