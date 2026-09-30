package com.creationreadingassistant.ui.screen.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.util.LocalDurationUnit
import com.creationreadingassistant.ui.util.formatDuration

@Composable
fun HomeReadingArchiveSection(
    totalReadingMs: Long,
    totalReadBooksCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalLayoutTokens.current
    val spec = LocalComponentSpec.current
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateEnter(30, reducedMotion)
            .bounceable(interactionSource)
            .clickable(
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .testTag("reading-archive-card"),
        shape = RoundedCornerShape(spec.islandRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            spec.hairlineBorderWidth,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = spec.hairlineAlpha),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFD97706).copy(alpha = 0.07f),
                            Color(0xFFF59E0B).copy(alpha = 0.02f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(layout.cardPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(layout.contentGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 36dp 暖琥珀微彩底座与渐变光泽：圆角/发丝边框走令牌，渐变属专属艺术保留
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(spec.pedestalRadius))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFF59E0B).copy(alpha = 0.22f),
                                    Color(0xFFD97706).copy(alpha = 0.12f),
                                ),
                            ),
                        )
                        .border(
                            width = spec.hairlineBorderWidth,
                            color = Color(0xFFD97706).copy(alpha = spec.hairlineAlpha),
                            shape = RoundedCornerShape(spec.pedestalRadius),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.AutoStories,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFFD97706),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "累计阅读",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${formatDuration(totalReadingMs, LocalDurationUnit.current)} · $totalReadBooksCount 本",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 微水波纹箭头底座
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
