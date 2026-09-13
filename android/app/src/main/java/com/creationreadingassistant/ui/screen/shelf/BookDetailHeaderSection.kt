package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.ui.components.BookCover
import com.creationreadingassistant.ui.theme.AppSuccess
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion

@Composable
internal fun DetailIslandCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content,
        )
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(bottom = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 13.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun CoverActionPill(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    val haptic = rememberHaptic(rememberReducedMotion())
    Surface(
        onClick = {
            haptic(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 0.5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun BookDetailHeaderSection(
    book: BookEntity,
    percent: Float,
    readiness: BookReadiness,
    onUpdateBook: (String, String?, String?) -> Unit,
    onChangeCover: () -> Unit,
    onChangeTextCover: () -> Unit,
    onResetCover: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var editTitle by remember(book.title) { mutableStateOf(book.title) }
    var editAuthor by remember(book.author) { mutableStateOf(book.author ?: "") }
    var editDescription by remember(book.description) { mutableStateOf(book.description ?: "") }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 3.dp,
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)),
                    modifier = Modifier.size(76.dp, 106.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        BookCover(
                            book = book,
                            percent = percent,
                            modifier = Modifier.fillMaxSize(),
                            fallback = { ShelfCoverFallback(book) },
                        )
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.22f),
                                            Color.Black.copy(alpha = 0.06f),
                                            Color.Transparent,
                                        ),
                                    ),
                                ),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (editing) {
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("书名") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = editAuthor,
                            onValueChange = { editAuthor = it },
                            label = { Text("作者") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = editDescription,
                            onValueChange = { editDescription = it },
                            label = { Text("简介") },
                            singleLine = false,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            book.author ?: "作者未知",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!book.description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                book.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val tone = toneColor(readiness.tone)
                    Surface(
                        color = tone.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.5.dp, tone.copy(alpha = 0.25f)),
                    ) {
                        Text(
                            readiness.label,
                            color = tone,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (editing) {
                                onUpdateBook(editTitle, editAuthor.takeIf { it.isNotBlank() }, editDescription.takeIf { it.isNotBlank() })
                            }
                            editing = !editing
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (editing) Icons.Outlined.CheckCircle else Icons.Outlined.Edit,
                            contentDescription = if (editing) "保存" else "编辑",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (!editing) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverActionPill(
                        icon = Icons.Outlined.PhotoLibrary,
                        text = "更换封面",
                        onClick = onChangeCover,
                    )
                    CoverActionPill(
                        icon = Icons.Outlined.TextFields,
                        text = "文字封面",
                        onClick = onChangeTextCover,
                    )
                    if (!book.cover_data_url.isNullOrBlank()) {
                        CoverActionPill(
                            icon = Icons.Outlined.RestartAlt,
                            text = "重置封面",
                            onClick = onResetCover,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BookDetailCtaSection(
    book: BookEntity,
    percent: Float,
    readiness: BookReadiness,
    onContinue: (BookEntity) -> Unit,
    onDownload: (BookEntity) -> Unit,
) {
    val isReady = readiness.tone == ReadinessTone.READY
    if (isReady) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { onContinue(book) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (percent > 0f) "继续阅读" else "开始阅读",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AppSuccess.copy(alpha = 0.08f),
                border = BorderStroke(0.8.dp, AppSuccess.copy(alpha = 0.28f)),
                modifier = Modifier.height(48.dp),
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppSuccess.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = AppSuccess,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "正文已就绪",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = AppSuccess,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { onDownload(book) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "重新下载",
                            tint = AppSuccess.copy(alpha = 0.75f),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    } else {
        Button(
            onClick = { onDownload(book) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "下载正文并阅读",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
