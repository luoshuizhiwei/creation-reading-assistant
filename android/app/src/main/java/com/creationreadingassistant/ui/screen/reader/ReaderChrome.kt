package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.ReaderPaperPalette

internal sealed interface ReaderChromeAction {
    data object Back : ReaderChromeAction
    data object ToggleTts : ReaderChromeAction
    data class OpenSheet(val sheet: ReaderSheet) : ReaderChromeAction
    data object ToggleAutoPaging : ReaderChromeAction
}

internal enum class ReaderBottomChromeMode { NORMAL, AUTO_PAGING, TTS }

internal fun readerBottomChromeMode(showTts: Boolean, autoPagingActive: Boolean): ReaderBottomChromeMode = when {
    showTts -> ReaderBottomChromeMode.TTS
    autoPagingActive -> ReaderBottomChromeMode.AUTO_PAGING
    else -> ReaderBottomChromeMode.NORMAL
}

@Composable
internal fun AutoPagingBar(
    onAction: (ReaderChromeAction) -> Unit,
    speed: Int,
    onSpeedChange: (Int) -> Unit,
    paper: ReaderPaperPalette,
) {
    val minimumTouchTarget = LocalLayoutTokens.current.minimumTouchTarget
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = paper.accent.copy(alpha = 0.10f),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(paper.accent, shape = CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "自动翻页 · $speed 档",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = paper.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = { onSpeedChange(clampAutoPageSpeed(speed - 1)) },
            modifier = Modifier.heightIn(min = minimumTouchTarget),
        ) {
            Icon(Icons.Outlined.Remove, contentDescription = "放慢自动翻页", tint = paper.accent)
        }
        Surface(
            onClick = { onAction(ReaderChromeAction.ToggleAutoPaging) },
            shape = CircleShape,
            color = paper.accent.copy(alpha = 0.14f),
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Pause,
                    contentDescription = "暂停自动翻页",
                    tint = paper.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        IconButton(
            onClick = { onSpeedChange(clampAutoPageSpeed(speed + 1)) },
            modifier = Modifier.heightIn(min = minimumTouchTarget),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "加快自动翻页", tint = paper.accent)
        }
        IconButton(
            onClick = { onAction(ReaderChromeAction.OpenSheet(ReaderSheet.SETTINGS)) },
            modifier = Modifier.heightIn(min = minimumTouchTarget),
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = "自动翻页设置", tint = paper.accent)
        }
    }
}

@Composable
internal fun ReaderBottomActions(
    onAction: (ReaderChromeAction) -> Unit,
    chapterProgress: Float,
    onSeekProgress: (Float) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    isFirstChapter: Boolean,
    isLastChapter: Boolean,
    autoPagingActive: Boolean,
    autoPageSpeed: Int,
    onAutoPageSpeedChange: (Int) -> Unit,
    paper: ReaderPaperPalette? = null,
) {
    val layout = LocalLayoutTokens.current
    val accentColor = paper?.accent ?: MaterialTheme.colorScheme.primary
    var sliderValue by remember { mutableFloatStateOf(chapterProgress) }
    var isDragging by remember { mutableStateOf(false) }

    // 仅在非拖动拽状态下同步外部进度
    if (!isDragging && kotlin.math.abs(sliderValue - chapterProgress) > 0.5f) {
        sliderValue = chapterProgress
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = layout.relatedGap),
    ) {
        // 拖动预览：拖动中实时显示目标百分比微岛气泡，松手才真正跳转
        if (isDragging) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 2.dp),
            ) {
                Text(
                    text = "跳到 ${sliderValue.toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        // 进度条行：[上一章] ──── Slider ──── [下一章]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = layout.relatedGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPreviousChapter,
                enabled = !isFirstChapter,
                modifier = Modifier.heightIn(min = layout.minimumTouchTarget),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isFirstChapter) Color.Transparent else accentColor.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.SkipPrevious,
                        contentDescription = "上一章",
                        tint = if (isFirstChapter) accentColor.copy(alpha = 0.3f) else accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Slider(
                value = sliderValue,
                onValueChange = {
                    isDragging = true
                    sliderValue = it
                },
                onValueChangeFinished = {
                    isDragging = false
                    onSeekProgress(sliderValue)
                },
                valueRange = 0f..100f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = layout.relatedGap)
                    .testTag("reader-progress-scrubber"),
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = accentColor.copy(alpha = 0.2f),
                ),
            )

            IconButton(
                onClick = onNextChapter,
                enabled = !isLastChapter,
                modifier = Modifier.heightIn(min = layout.minimumTouchTarget),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isLastChapter) Color.Transparent else accentColor.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.SkipNext,
                        contentDescription = "下一章",
                        tint = if (isLastChapter) accentColor.copy(alpha = 0.3f) else accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // 自动翻页进行中：微胶囊化档位展示
        if (autoPagingActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = layout.relatedGap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.2f)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "自动翻页",
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor,
                        )
                        IconButton(
                            onClick = { onAutoPageSpeedChange(clampAutoPageSpeed(autoPageSpeed - 1)) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Remove,
                                contentDescription = "放慢自动翻页",
                                tint = accentColor,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            text = "$autoPageSpeed 档",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        IconButton(
                            onClick = { onAutoPageSpeedChange(clampAutoPageSpeed(autoPageSpeed + 1)) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = "加快自动翻页",
                                tint = accentColor,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        // 功能按钮行：微岛化触感
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = layout.relatedGap),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderFooterAction(Icons.Outlined.Menu, "目录", accentColor) {
                onAction(ReaderChromeAction.OpenSheet(ReaderSheet.TOC))
            }
            ReaderFooterAction(Icons.Outlined.Headphones, "听书", accentColor) {
                onAction(ReaderChromeAction.ToggleTts)
            }
            ReaderFooterAction(Icons.Outlined.Lightbulb, "灵感", accentColor) {
                onAction(ReaderChromeAction.OpenSheet(ReaderSheet.INSPIRATION))
            }
            ReaderFooterAction(Icons.Outlined.Palette, "主题", accentColor) {
                onAction(ReaderChromeAction.OpenSheet(ReaderSheet.THEME))
            }
            ReaderFooterAction(Icons.Outlined.Settings, "设置", accentColor) {
                onAction(ReaderChromeAction.OpenSheet(ReaderSheet.SETTINGS))
            }
        }
    }
}

@Composable
private fun ReaderFooterAction(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier.padding(horizontal = 2.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(20.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = accent,
            )
        }
    }
}

/**
 * 阅读器顶层控制栏。采用现代纸墨微岛（Material 3）设计语言，柔和阴影与发丝边框，
 * 触控均衡，书名与章节信息层次分明。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ReaderTopChrome(
    bookTitle: String,
    formatLabel: String,
    chapterTitle: String,
    progressPercent: Float,
    paper: ReaderPaperPalette,
    overflowExpanded: Boolean,
    autoPagingActive: Boolean,
    onOverflowExpandedChange: (Boolean) -> Unit,
    onAction: (ReaderChromeAction) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .windowInsetsPadding(WindowInsets.statusBars),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onAction(ReaderChromeAction.Back) },
                modifier = Modifier.size(40.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(paper.fg.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = paper.fg,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    text = bookTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = paper.fg,
                )
                Text(
                    text = "$formatLabel · ${chapterTitle.ifBlank { "正文" }} · ${progressPercent.toInt()}%",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = paper.fgMuted,
                )
            }

            Spacer(Modifier.width(8.dp))

            Box {
                IconButton(
                    onClick = { onOverflowExpandedChange(true) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(paper.fg.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = "更多",
                            tint = paper.fg,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { onOverflowExpandedChange(false) },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    shadowElevation = 6.dp,
                ) {
                    DropdownMenuItem(
                        text = { Text("阅读进度") },
                        leadingIcon = { Icon(Icons.Outlined.BarChart, contentDescription = null, tint = paper.accent) },
                        onClick = {
                            onOverflowExpandedChange(false)
                            onAction(ReaderChromeAction.OpenSheet(ReaderSheet.PROGRESS))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("AI 助手") },
                        leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = paper.accent) },
                        onClick = {
                            onOverflowExpandedChange(false)
                            onAction(ReaderChromeAction.OpenSheet(ReaderSheet.AI_ASSIST))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("笔记与标注") },
                        leadingIcon = { Icon(Icons.Outlined.BorderColor, contentDescription = null, tint = paper.accent) },
                        onClick = {
                            onOverflowExpandedChange(false)
                            onAction(ReaderChromeAction.OpenSheet(ReaderSheet.NOTES))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("书内搜索") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = paper.accent) },
                        onClick = {
                            onOverflowExpandedChange(false)
                            onAction(ReaderChromeAction.OpenSheet(ReaderSheet.SEARCH))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (autoPagingActive) "暂停自动翻页" else "开始自动翻页") },
                        leadingIcon = {
                            Icon(
                                if (autoPagingActive) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                tint = paper.accent,
                            )
                        },
                        onClick = {
                            onOverflowExpandedChange(false)
                            onAction(ReaderChromeAction.ToggleAutoPaging)
                        },
                    )
                }
            }
        }
    }
}
