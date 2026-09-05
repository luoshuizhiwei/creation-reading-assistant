package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.ui.screen.reader.formatDuration
import com.creationreadingassistant.ui.theme.DisplayFontFamily
import com.creationreadingassistant.ui.theme.PillShape
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import kotlin.math.abs

/** Progress-sheet input is always a book percentage, never an inferred chapter number. */
internal fun progressSheetTargetPercent(percent: Float): Float = percent.coerceIn(0f, 100f)

@Composable
internal fun ProgressSheet(
    epubBook: EpubBook?,
    chapterIndex: Int,
    currentChapterTitle: String,
    progressPercent: Float,
    activeReadingMs: Long,
    readerSpeed: Int,
    estimatedRemainingMs: Long,
    savedBookReadingMs: Long,
    inspirationsCount: Int,
    bookmarksCount: Int,
    onChapter: (Int) -> Unit,
    onSeekPercent: (Float) -> Unit = {},
    isTxt: Boolean = false,
) {
    var slider by remember(progressPercent) { mutableFloatStateOf(progressPercent) }
    var percentDraft by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val size = epubBook?.chapters?.size ?: 0
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    val scrollState = rememberScrollState()

    fun seekTo(percent: Float) {
        onSeekPercent(progressSheetTargetPercent(percent))
    }

    ReaderSheetScaffold(
        title = "阅读进度",
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. 顶部全书进度总览大屏微岛
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.AutoStories,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = "全书阅读进度",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // 累计阅读翡翠绿微徽章
                        Surface(
                            shape = PillShape,
                            color = Color(0xFF059669).copy(alpha = 0.12f),
                            border = BorderStroke(0.8.dp, Color(0xFF059669).copy(alpha = 0.35f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.HourglassEmpty,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = Color(0xFF059669),
                                )
                                Text(
                                    text = "累计 ${formatDuration(savedBookReadingMs + activeReadingMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF059669),
                                )
                            }
                        }
                    }

                    // 大字号百分比读数与微胶囊群
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "%.1f".format(slider),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 40.sp,
                                    fontFamily = DisplayFontFamily,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "%",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
                            )
                        }

                        // 状态胶囊群：已读章节 + 本次阅读暖琥珀徽章
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (size > 0) {
                                Surface(
                                    shape = PillShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                ) {
                                    Text(
                                        text = "已读 ${chapterIndex + 1} / $size 章",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }

                            // 本次阅读时长暖琥珀微徽章
                            Surface(
                                shape = PillShape,
                                color = Color(0xFFD97706).copy(alpha = 0.12f),
                                border = BorderStroke(0.8.dp, Color(0xFFD97706).copy(alpha = 0.35f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.AccessTime,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = Color(0xFFD97706),
                                    )
                                    Text(
                                        text = "本次已读 ${formatDuration(activeReadingMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFD97706),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. 预计剩余阅读时长与阅读速度微岛卡片矩阵
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProgressStatMicroCard(
                    icon = Icons.Outlined.Speed,
                    value = if (readerSpeed > 0) "$readerSpeed" else "—",
                    unit = "字/分",
                    label = "阅读速度",
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.weight(1f),
                )
                ProgressStatMicroCard(
                    icon = Icons.Outlined.HourglassEmpty,
                    value = if (estimatedRemainingMs > 0) formatDuration(estimatedRemainingMs) else "—",
                    unit = "",
                    label = "预计读完",
                    tint = Color(0xFF059669),
                    modifier = Modifier.weight(1f),
                )
                ProgressStatMicroCard(
                    icon = Icons.Outlined.Lightbulb,
                    value = "$inspirationsCount",
                    unit = "条",
                    label = "灵感沉淀",
                    tint = Color(0xFF7C3AED),
                    modifier = Modifier.weight(1f),
                )
                ProgressStatMicroCard(
                    icon = Icons.Outlined.BookmarkBorder,
                    value = "$bookmarksCount",
                    unit = "个",
                    label = "书签标记",
                    tint = Color(0xFFEA580C),
                    modifier = Modifier.weight(1f),
                )
            }

            // 3. 跳章与章节快速切换微岛
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val canPrev = chapterIndex > 0
                    val prevInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            onChapter(chapterIndex - 1)
                        },
                        enabled = canPrev,
                        shape = PillShape,
                        color = if (canPrev) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, if (canPrev) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent),
                        modifier = Modifier
                            .height(36.dp)
                            .bounceable(prevInteraction),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "上一章",
                                modifier = Modifier.size(15.dp),
                                tint = if (canPrev) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            )
                            Text(
                                "上一章",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (canPrev) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = currentChapterTitle.ifBlank { "正文" },
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = DisplayFontFamily),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    val canNext = chapterIndex < size - 1
                    val nextInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            onChapter(chapterIndex + 1)
                        },
                        enabled = canNext,
                        shape = PillShape,
                        color = if (canNext) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, if (canNext) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent),
                        modifier = Modifier
                            .height(36.dp)
                            .bounceable(nextInteraction),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "下一章",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (canNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            )
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = "下一章",
                                modifier = Modifier.size(15.dp),
                                tint = if (canNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            )
                        }
                    }
                }
            }

            // 4. 精确进度微导轨滑块与微胶囊锚点
            if (size > 0 || isTxt) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val dragging = slider != progressPercent && !slider.isNaN()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "滑块微导轨调节",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            // 拖动即时反馈微胶囊
                            Surface(
                                shape = PillShape,
                                color = if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            ) {
                                Text(
                                    text = if (dragging) "松手跳到 ${slider.toInt()}%" else "当前 ${progressPercent.toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (dragging) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                )
                            }
                        }

                        // 平滑微导轨滑块
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "0%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Slider(
                                value = slider,
                                onValueChange = { slider = it },
                                valueRange = 0f..100f,
                                onValueChangeFinished = {
                                    haptic(HapticFeedbackType.TextHandleMove)
                                    seekTo(slider)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                            )
                            Text(
                                "100%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }

                        // 快速跳转四分位微胶囊导轨
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(25, 50, 75, 100).forEach { target ->
                                val isNear = abs(slider - target) < 1f
                                val pillInteraction = remember { MutableInteractionSource() }
                                Surface(
                                    onClick = {
                                        haptic(HapticFeedbackType.TextHandleMove)
                                        slider = target.toFloat()
                                        seekTo(slider)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .bounceable(pillInteraction),
                                    shape = PillShape,
                                    color = if (isNear) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(0.5.dp, if (isNear) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                ) {
                                    Text(
                                        text = "$target%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isNear) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = if (isNear) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(2.dp))

                        // 精确百分比输入跳转微岛
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedTextField(
                                value = percentDraft,
                                onValueChange = { raw -> percentDraft = raw.filter { it.isDigit() }.take(3) },
                                label = { Text("精确输入目标进度 (%)", style = MaterialTheme.typography.bodySmall) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    percentDraft.toIntOrNull()?.let {
                                        haptic(HapticFeedbackType.TextHandleMove)
                                        seekTo(it.toFloat())
                                        slider = it.toFloat()
                                    }
                                    percentDraft = ""
                                    focusManager.clearFocus()
                                }),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                            )
                            val jumpInteraction = remember { MutableInteractionSource() }
                            val canJump = percentDraft.toIntOrNull()?.let { it in 0..100 } == true
                            Button(
                                onClick = {
                                    percentDraft.toIntOrNull()?.let {
                                        haptic(HapticFeedbackType.TextHandleMove)
                                        seekTo(it.toFloat())
                                        slider = it.toFloat()
                                    }
                                    percentDraft = ""
                                    focusManager.clearFocus()
                                },
                                enabled = canJump,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(52.dp)
                                    .bounceable(jumpInteraction),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.NearMe,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text("跳转", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 指标统计微岛卡片。
 */
@Composable
private fun ProgressStatMicroCard(
    icon: ImageVector,
    value: String,
    unit: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(tint.copy(alpha = 0.14f), shape = RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = DisplayFontFamily),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (unit.isNotBlank()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 1.dp, start = 1.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
            )
        }
    }
}
