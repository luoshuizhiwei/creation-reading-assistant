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
import androidx.compose.material.icons.outlined.SubdirectoryArrowLeft
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
import androidx.compose.runtime.LaunchedEffect
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
import com.creationreadingassistant.ui.components.IconPedestal
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.ReaderPanelSurface
import kotlinx.coroutines.delay

internal sealed interface ReaderChromeAction {
    data object Back : ReaderChromeAction
    data object ToggleTts : ReaderChromeAction
    data class OpenSheet(val sheet: ReaderSheet) : ReaderChromeAction
    data object ToggleAutoPaging : ReaderChromeAction
    /** 返回阅读处（临时查阅 LIFO 返回一层）。 */
    data object ReturnToReading : ReaderChromeAction
}

internal enum class ReaderBottomChromeMode { NORMAL, AUTO_PAGING, TTS }

/** 同步 seek 收敛后仍保留提示的最短可观察时长，不能只留一个渲染帧。 */
internal const val SEEK_PREVIEW_LINGER_MILLIS = 650L
/** seek 被拒绝、取消或长期未收敛时的兜底清理时长。 */
internal const val SEEK_PREVIEW_TIMEOUT_MILLIS = 1_500L

internal fun seekPreviewHasConverged(
    isDragging: Boolean,
    seekFinished: Boolean,
    startPercent: Float,
    targetPercent: Float,
    chapterProgress: Float,
): Boolean {
    val movedFromStart = kotlin.math.abs(chapterProgress - startPercent) > 0.5f
    val reachedTarget = kotlin.math.abs(chapterProgress - targetPercent) <= 0.5f
    return !isDragging && seekFinished && movedFromStart && reachedTarget
}

internal fun seekPreviewTimeoutMillis(seekFinished: Boolean): Long? =
    if (seekFinished) SEEK_PREVIEW_TIMEOUT_MILLIS else null

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
    val spec = LocalComponentSpec.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            // 微岛收敛：自动翻页状态岛圆角统一走 hintRadius 令牌（原手写 12dp，取值不变）。
            shape = RoundedCornerShape(spec.hintRadius),
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
    onProgressInteractionStarted: () -> Unit = {},
) {
    val layout = LocalLayoutTokens.current
    val spec = LocalComponentSpec.current
    val accentColor = paper?.accent ?: MaterialTheme.colorScheme.primary
    var sliderValue by remember { mutableFloatStateOf(chapterProgress) }
    var isDragging by remember { mutableStateOf(false) }
    // T9：待落地的拖拽目标百分比。Material3 Slider 的无障碍 SetProgress 语义会在同一帧内
    // 连续调用 onValueChange 与 onValueChangeFinished，若预览气泡只挂在 isDragging 上，
    // 它会在 onSeekProgress 真正执行之前就被置回 false —— 气泡永不出现（TalkBack 用户与
    // ReaderBottomActionsTest 走的都是这条路径）。改为记录「待落地目标值」，直到外部
    // chapterProgress 推进（= seek 已生效）才收起，从而保证「seek 之前目标百分比可见」。
    var pendingSeekPercent by remember { mutableStateOf<Float?>(null) }
    // 记录拖拽开始时的外部进度，只有它真正向本次目标收敛才可关闭预览；无关的被动进度更新不能抢掉气泡。
    var pendingSeekStartPercent by remember { mutableStateOf<Float?>(null) }
    var pendingSeekFinished by remember { mutableStateOf(false) }

    // 仅在非拖拽状态下同步外部进度
    if (!isDragging && kotlin.math.abs(sliderValue - chapterProgress) > 0.5f) {
        sliderValue = chapterProgress
    }
    // seek 落地后保留一个可观察短时窗口。单帧等待在真机上仍可能被同一调度批次吞掉，
    // 所以同步 onSeekProgress 更新也至少显示 650ms；无关被动进度不会命中收敛条件。
    LaunchedEffect(chapterProgress, pendingSeekPercent, pendingSeekStartPercent, pendingSeekFinished) {
        val target = pendingSeekPercent ?: return@LaunchedEffect
        val start = pendingSeekStartPercent ?: return@LaunchedEffect
        if (
            seekPreviewHasConverged(
                isDragging = isDragging,
                seekFinished = pendingSeekFinished,
                startPercent = start,
                targetPercent = target,
                chapterProgress = chapterProgress,
            )
        ) {
            delay(SEEK_PREVIEW_LINGER_MILLIS)
            if (pendingSeekPercent == target) {
                pendingSeekPercent = null
                pendingSeekStartPercent = null
                pendingSeekFinished = false
            }
        }
    }
    // 拒绝 seek 或进度长期不收敛时也不能永久遮住底栏；超时仅清除本次已松手的待确认目标。
    LaunchedEffect(pendingSeekPercent, pendingSeekFinished) {
        val target = pendingSeekPercent ?: return@LaunchedEffect
        val timeoutMillis = seekPreviewTimeoutMillis(pendingSeekFinished) ?: return@LaunchedEffect
        delay(timeoutMillis)
        if (pendingSeekPercent == target) {
            pendingSeekPercent = null
            pendingSeekStartPercent = null
            pendingSeekFinished = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = layout.relatedGap),
    ) {
        // 拖动预览：拖拽中（及 seek 落地前）实时显示目标百分比微岛气泡，进度真正推进后才收起
        val previewPercent = pendingSeekPercent
        if (isDragging || previewPercent != null) {
            // 微岛收敛：拖拽预览气泡是覆盖在阅读页之上的提示岛，统一走 ReaderPanelSurface
            //（随纸 panel 纸面 + 发丝边 + panelElevation 0dp）。原手写 10dp 圆角 / 3dp 阴影中，
            // 阴影会在翻页动画期额外触发重绘并侵蚀帧预算；圆角改取 hintRadius 令牌。
            ReaderPanelSurface(
                shape = RoundedCornerShape(spec.hintRadius),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 2.dp),
            ) {
                Text(
                    text = "跳到 ${(previewPercent ?: sliderValue).toInt()}%",
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
                // 微岛收敛：圆形底座 → IconPedestal（pedestalRadius + hairline 令牌，禁用态统一降级）。
                IconPedestal(
                    icon = Icons.Outlined.SkipPrevious,
                    tint = accentColor,
                    size = 34.dp,
                    iconSize = 20.dp,
                    enabled = !isFirstChapter,
                    contentDescription = "上一章",
                )
            }

            Slider(
                value = sliderValue,
                onValueChange = {
                    if (!isDragging) {
                        pendingSeekStartPercent = chapterProgress
                        onProgressInteractionStarted()
                    }
                    isDragging = true
                    sliderValue = it
                    pendingSeekPercent = it
                    pendingSeekFinished = false
                },
                onValueChangeFinished = {
                    isDragging = false
                    pendingSeekFinished = pendingSeekPercent != null
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
                // 微岛收敛：圆形底座 → IconPedestal（pedestalRadius + hairline 令牌，禁用态统一降级）。
                IconPedestal(
                    icon = Icons.Outlined.SkipNext,
                    tint = accentColor,
                    size = 34.dp,
                    iconSize = 20.dp,
                    enabled = !isLastChapter,
                    contentDescription = "下一章",
                )
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
                    // 微岛收敛：发丝边宽度 / 透明度统一走 hairline 令牌（原手写 0.5dp / 0.2f）。
                    border = BorderStroke(spec.hairlineBorderWidth, accentColor.copy(alpha = spec.hairlineAlpha)),
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
        // 微岛收敛：底部操作栏点击区圆角统一走 hintRadius 令牌（原手写 12dp，取值不变）。
        shape = RoundedCornerShape(LocalComponentSpec.current.hintRadius),
        color = Color.Transparent,
        modifier = Modifier.padding(horizontal = 2.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            // 微岛收敛：图标底座统一走 IconPedestal（pedestalRadius + hairline 令牌）。
            IconPedestal(
                icon = icon,
                tint = accent,
                size = 34.dp,
                iconSize = 20.dp,
                contentDescription = label,
            )
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
 * 阅读器顶层控制栏。采用现代纸墨微岛（Material 3）设计语言：随纸 panel 纸面 + 发丝边框、
 * **零阴影**（顶栏覆盖在翻页动画层之上，阴影会侵蚀帧预算），触控均衡，书名与章节信息层次分明。
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
    /** 当前是否处于临时查阅模式。 */
    temporaryInspection: Boolean = false,
    /** 临时查阅协调器是否存在可返回目标。 */
    hasReturnableTarget: Boolean = false,
) {
    val spec = LocalComponentSpec.current
    // 微岛收敛：顶栏是覆盖在阅读页之上的 reader 专属面板，统一走 ReaderPanelSurface
    //（随纸 panel 纸面 + 发丝边 + panelElevation 0dp）。原手写 surfaceContainerHigh@0.94 与
    // 3dp 阴影已移除——阴影在翻页动画期额外触发重绘；圆角改取 dockRadius 令牌（同为 18dp）。
    ReaderPanelSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .windowInsetsPadding(WindowInsets.statusBars),
        shape = RoundedCornerShape(spec.dockRadius),
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
                // 微岛收敛：圆形底座 → IconPedestal（pedestalRadius + hairline 令牌）。
                IconPedestal(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    tint = paper.fg,
                    size = 34.dp,
                    iconSize = 20.dp,
                    contentDescription = "返回",
                )
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

            // 临时查阅模式：显示"返回阅读处"按钮（仅当 hasReturnableTarget 为 true 时可见）
            if (temporaryInspection && hasReturnableTarget) {
                IconButton(
                    onClick = { onAction(ReaderChromeAction.ReturnToReading) },
                    modifier = Modifier.size(40.dp),
                ) {
                    IconPedestal(
                        icon = Icons.Outlined.SubdirectoryArrowLeft,
                        tint = paper.accent,
                        size = 34.dp,
                        iconSize = 20.dp,
                        contentDescription = "返回阅读处",
                    )
                }
                Spacer(Modifier.width(4.dp))
            }

            Box {
                IconButton(
                    onClick = { onOverflowExpandedChange(true) },
                    modifier = Modifier.size(40.dp),
                ) {
                    // 微岛收敛：圆形底座 → IconPedestal（pedestalRadius + hairline 令牌）。
                    IconPedestal(
                        icon = Icons.Outlined.MoreVert,
                        tint = paper.fg,
                        size = 34.dp,
                        iconSize = 20.dp,
                        contentDescription = "更多",
                    )
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { onOverflowExpandedChange(false) },
                    // 微岛收敛：阅读溢出菜单同样落在阅读页之上——圆角取 islandRadius 令牌、
                    // 容器色改随纸 panel、发丝边走 hairline 令牌、阴影归零（保护翻页帧预算）。
                    shape = RoundedCornerShape(spec.islandRadius),
                    containerColor = paper.panel,
                    border = BorderStroke(spec.hairlineBorderWidth, paper.outlineVariant.copy(alpha = spec.hairlineAlpha)),
                    shadowElevation = paper.panelElevation,
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
