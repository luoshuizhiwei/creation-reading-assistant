package com.creationreadingassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.animateEnter
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

/**
 * 在文本中高亮匹配子串（SE3）。命中部分使用柔和底衬与主色加粗。
 */
fun buildHighlighted(text: String, query: String, highlightColor: Color): AnnotatedString {
    val keyword = query.trim()
    if (keyword.isBlank()) return AnnotatedString(text)
    val lower = text.lowercase()
    val lowerKeyword = keyword.lowercase()
    val builder = AnnotatedString.Builder()
    var index = 0
    while (index < text.length) {
        val found = lower.indexOf(lowerKeyword, index)
        if (found < 0) {
            builder.append(text.substring(index))
            break
        }
        if (found > index) builder.append(text.substring(index, found))
        builder.pushStyle(
            SpanStyle(
                color = highlightColor,
                fontWeight = FontWeight.Bold,
                background = highlightColor.copy(alpha = 0.14f),
            ),
        )
        builder.append(text.substring(found, (found + keyword.length).coerceAtMost(text.length)))
        builder.pop()
        index = found + keyword.length
    }
    return builder.toAnnotatedString()
}

/**
 * 通用全局搜索结果微岛卡片（支持书籍、正文片段、灵感、笔记、书签等多品类统一呈现）。
 */
@Composable
fun SearchResultCard(
    icon: ImageVector,
    title: AnnotatedString,
    typeLabel: String,
    typeColor: Color,
    sourceLabel: String?,
    snippet: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    entranceDelay: Int = 0,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    SectionCard(
        onClick = { haptic(HapticFeedbackType.TextHandleMove); onClick() },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .animateEnter(delayMillis = entranceDelay, reducedMotion = reducedMotion),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标底座收敛到共享 IconPedestal（pedestalRadius + 发丝边）
            IconPedestal(
                icon = icon,
                tint = typeColor,
                size = 40.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(typeColor.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = typeColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    sourceLabel?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (snippet.isNotBlank()) {
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
