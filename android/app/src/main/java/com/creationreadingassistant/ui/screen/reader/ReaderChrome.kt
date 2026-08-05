package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.ReaderPaperPalette

internal sealed interface ReaderChromeAction {
    data object Back : ReaderChromeAction
    data object ToggleTts : ReaderChromeAction
    data class OpenSheet(val sheet: ReaderSheet) : ReaderChromeAction
    data object ToggleAutoPaging : ReaderChromeAction
    data object HideControls : ReaderChromeAction
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
                Icon(
                    Icons.Outlined.SkipPrevious,
                    contentDescription = "上一章",
                    tint = if (isFirstChapter) accentColor.copy(alpha = 0.3f) else accentColor,
                )
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
                    .padding(horizontal = layout.relatedGap),
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
                Icon(
                    Icons.Outlined.SkipNext,
                    contentDescription = "下一章",
                    tint = if (isLastChapter) accentColor.copy(alpha = 0.3f) else accentColor,
                )
            }
        }

        // 功能按钮行
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
internal fun ReaderCollapsedControl(
    onShowControls: () -> Unit,
) {
    // 任何收起状态（含自动翻页中）都只显示「展开菜单」FAB：
    // 暂停/开始自动翻页统一放在顶栏更多菜单里，收起态右下角不占常驻暂停按钮。
    FloatingActionButton(onClick = onShowControls) {
        Icon(Icons.Outlined.Menu, contentDescription = "展开菜单")
    }
}

@Composable
private fun ReaderFooterAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val layout = LocalLayoutTokens.current
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = layout.minimumTouchTarget),
        colors = ButtonDefaults.textButtonColors(
            contentColor = accent,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * 阅读器顶层控制栏。只描述用户动作，不直接读写文档、分页器或导航状态。
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
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = paper.bg,
            titleContentColor = paper.fg,
            navigationIconContentColor = paper.fg,
            actionIconContentColor = paper.fg,
        ),
        title = {
            Column {
                Text(
                    text = bookTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "$formatLabel · ${chapterTitle.ifBlank { "正文" }} · ${progressPercent.toInt()}%",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = paper.fgMuted,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = { onAction(ReaderChromeAction.Back) }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
        },
        actions = {
            Box {
                IconButton(onClick = { onOverflowExpandedChange(true) }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { onOverflowExpandedChange(false) },
                ) {
                    DropdownMenuItem(
                        text = { Text("阅读进度") },
                        leadingIcon = { Icon(Icons.Outlined.BarChart, contentDescription = null) },
                        onClick = {
                            onOverflowExpandedChange(false)
                            onAction(ReaderChromeAction.OpenSheet(ReaderSheet.PROGRESS))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("AI 助手") },
                        leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                        onClick = {
                            onOverflowExpandedChange(false)
                            onAction(ReaderChromeAction.OpenSheet(ReaderSheet.AI_ASSIST))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("笔记与标注") },
                        leadingIcon = { Icon(Icons.Outlined.BorderColor, contentDescription = null) },
                        onClick = {
                            onOverflowExpandedChange(false)
                            onAction(ReaderChromeAction.OpenSheet(ReaderSheet.NOTES))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("书内搜索") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
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
                            )
                        },
                        onClick = {
                            onOverflowExpandedChange(false)
                            onAction(ReaderChromeAction.ToggleAutoPaging)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("隐藏工具栏") },
                        leadingIcon = {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null)
                        },
                        onClick = {
                            onOverflowExpandedChange(false)
                            onAction(ReaderChromeAction.HideControls)
                        },
                    )
                }
            }
        },
    )
}
