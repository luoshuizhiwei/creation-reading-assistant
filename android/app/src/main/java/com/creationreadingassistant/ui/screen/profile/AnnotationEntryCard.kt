package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.R
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.feature.annotations.AnnotationEntry
import com.creationreadingassistant.feature.annotations.AnnotationType
import com.creationreadingassistant.ui.components.IslandCard
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.bounceable

/** 高亮 5 色在「我的」外壳下的可视色（对齐阅读器白纸档的随纸批注实色）。 */
internal val ANNOTATION_COLOR_VISUALS = linkedMapOf(
    "yellow" to Color(0xFFE6C95A),
    "red" to Color(0xFFD08B7A),
    "green" to Color(0xFF7FA86B),
    "blue" to Color(0xFF6E8FC0),
    "purple" to Color(0xFFA884B0),
)

internal fun annotationVisualColor(name: String?): Color =
    ANNOTATION_COLOR_VISUALS[name] ?: ANNOTATION_COLOR_VISUALS.values.first()

/** 条目操作微胶囊（定位 / 编辑 / 删除），对齐 ReaderNotesSheet 的 NotesActionPill 视觉。 */
@Composable
internal fun AnnotationActionPill(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val spec = LocalComponentSpec.current
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = spec.pillShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            spec.hairlineBorderWidth,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        modifier = Modifier.bounceable(interaction),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = tint,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = tint,
            )
        }
    }
}

/**
 * 统一笔记条目卡片：类型徽标（高亮取批注色 / 批注取主色 / 书签取琥珀）+ 来源书 +
 * 章节行 + 原文摘录（引用块）+ 个人批注（气泡）+ 高亮改色圆点 + 操作微胶囊。
 * 无 locator 的历史条目显示「无定位」徽标，定位按钮降级为「打开书籍」。
 */
@Composable
internal fun AnnotationEntryCard(
    entry: AnnotationEntry,
    book: BookEntity?,
    selectMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onColorChange: (String) -> Unit,
    actionsAvailable: Boolean,
    /** J1-I.2 临时查阅来源入口；为 null 时不渲染（无有效 source locator 的条目传 null）。 */
    onInspectSource: (() -> Unit)? = null,
) {
    val spec = LocalComponentSpec.current
    val typeColor = when (entry.type) {
        AnnotationType.HIGHLIGHT -> annotationVisualColor(entry.color)
        AnnotationType.NOTE -> MaterialTheme.colorScheme.primary
        AnnotationType.BOOKMARK -> Color(0xFFD97706)
    }
    val typeLabel = annotationTypeLabel(entry.type)
    IslandCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                // 左侧 3dp 类型墨线竖标（延续原 NoteItem 的视觉语言）
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            Brush.verticalGradient(listOf(typeColor, typeColor.copy(alpha = 0.4f))),
                        ),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                // 顶部：类型徽标 + 无定位徽标 + 时间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = spec.pillShape,
                        color = typeColor.copy(alpha = 0.14f),
                        border = BorderStroke(spec.hairlineBorderWidth, typeColor.copy(alpha = spec.hairlineAlpha)),
                    ) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp,
                            ),
                            color = typeColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                    if (entry.bookId != null && !entry.hasLocator) {
                        Surface(
                            shape = spec.pillShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                spec.hairlineBorderWidth,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.annotations_badge_no_locator),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatDateTime(entry.createdAt),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }

                // 来源书 + 章节
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = spec.pillShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(
                            spec.hairlineBorderWidth,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Book,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = book?.title?.let { "《$it》" }
                                    ?: stringResource(R.string.annotations_badge_no_book),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val chapter = entry.chapterTitle
                    if (chapter != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Icon(
                                Icons.Outlined.BookmarkBorder,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            )
                            Text(
                                text = chapter,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // 原文摘录：引用块（与个人批注分区展示）
                entry.excerpt?.let { excerpt ->
                    Surface(
                        shape = RoundedCornerShape(spec.hintRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
                        border = BorderStroke(
                            spec.hairlineBorderWidth,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Outlined.FormatQuote,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = typeColor.copy(alpha = 0.75f),
                            )
                            Text(
                                text = excerpt,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    lineHeight = 19.sp,
                                    fontSize = 12.5.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // 个人批注气泡
                entry.annotation?.let { annotation ->
                    Surface(
                        shape = RoundedCornerShape(spec.hintRadius),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            spec.hairlineBorderWidth,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Outlined.EditNote,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = annotation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                if (!selectMode && actionsAvailable) {
                    // 高亮改色圆点（仅高亮；改色不动定位信息）
                    if (entry.type == AnnotationType.HIGHLIGHT) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ANNOTATION_COLOR_VISUALS.forEach { (name, cColor) ->
                                val isSelected = entry.color == name
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .clickable { onColorChange(name) }
                                        .then(
                                            if (isSelected) {
                                                Modifier
                                                    .background(cColor.copy(alpha = 0.25f), CircleShape)
                                                    .border(1.5.dp, cColor, CircleShape)
                                            } else {
                                                Modifier.border(
                                                    0.6.dp,
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                                    CircleShape,
                                                )
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(if (isSelected) 10.dp else 12.dp)
                                            .background(cColor, CircleShape),
                                    )
                                }
                            }
                        }
                    }

                    // 操作微胶囊：定位（无 locator 降级为打开书籍）· 编辑批注 · 删除
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnnotationActionPill(
                            text = stringResource(
                                if (entry.hasLocator) R.string.annotations_action_jump
                                else R.string.annotations_action_jump_degraded,
                            ),
                            icon = Icons.Outlined.NearMe,
                            onClick = onClick,
                        )
                        // J1-I.2：仅对携带有效 source locator 的条目提供「临时查看来源」——
                        // 进入可回退的一次性查阅（返回阅读处 / Back 回到进入前位置），
                        // 与上面的普通跳转语义区分开。
                        if (onInspectSource != null) {
                            AnnotationActionPill(
                                text = stringResource(R.string.annotations_action_temporary_inspect),
                                icon = Icons.Outlined.AutoStories,
                                onClick = onInspectSource,
                            )
                        }
                        if (entry.type != AnnotationType.BOOKMARK) {
                            AnnotationActionPill(
                                text = stringResource(R.string.annotations_action_edit),
                                icon = Icons.Outlined.EditNote,
                                onClick = onEdit,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        AnnotationActionPill(
                            text = stringResource(R.string.annotations_action_delete),
                            icon = Icons.Outlined.DeleteOutline,
                            onClick = onDelete,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
