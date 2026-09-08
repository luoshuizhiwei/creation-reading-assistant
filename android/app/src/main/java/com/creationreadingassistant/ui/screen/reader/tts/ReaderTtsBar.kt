package com.creationreadingassistant.ui.screen.reader.tts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.IconPedestal
import com.creationreadingassistant.ui.components.SettingSegmentedRow
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.ReaderPaperTheme
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.screen.reader.sheets.ReaderSheetScaffold
import com.creationreadingassistant.ui.screen.reader.tts.engine.TtsEngineId

@Composable
internal fun rememberTts(): TtsEngineHost {
    val context = LocalContext.current
    val controller = remember { TtsEngineHost(context, TtsEngineId.SYSTEM) }
    DisposableEffect(controller) { onDispose { controller.release() } }
    return controller
}

/**
 * 为单个文本块构造带「当前朗读句」/「搜索命中」高亮背景的 AnnotatedString
 * （EPUB 逐句高亮，对照 TXT 机制；搜索命中为同一字符空间的临时高亮）。
 * [blockGlobalOffset] 为该块在全书文本中的全局偏移；[chapterBase] 为所在章节在全书中的起始偏移。
 * 两个入参的基准不同：TTS 句区间是章内偏移（相对章节 TTS contentText），须按「块在章内偏移」
 * （= [blockGlobalOffset] - [chapterBase]）换算；[searchRangeAbs] 是搜索命中的全书偏移区间
 * （含首不含尾，与 [com.creationreadingassistant.ui.screen.reader.SearchHitTarget] 同一基准），
 * 必须按 [blockGlobalOffset] 换算——旧实现误减章内偏移，导致非首章搜索高亮全部越界。
 */
internal fun buildSentenceHighlighted(
    text: String,
    blockGlobalOffset: Int,
    chapterBase: Int,
    ttsSentenceRange: Pair<Int, Int>?,
    bg: Color,
    searchRangeAbs: Pair<Int, Int>? = null,
    searchBg: Color = bg,
): AnnotatedString {
    if ((ttsSentenceRange == null && searchRangeAbs == null) || blockGlobalOffset < 0) return AnnotatedString(text)
    return AnnotatedString.Builder(text).apply {
        ttsSentenceRange?.let { (s0, e0) ->
            // TTS 句区间：章内偏移 → 块内偏移
            val blockLocalBase = blockGlobalOffset - chapterBase
            val s = s0 - blockLocalBase
            val e = e0 - blockLocalBase
            if (s >= 0 && s < text.length && e > s) {
                addStyle(SpanStyle(background = bg), s, e.coerceAtMost(text.length))
            }
        }
        searchRangeAbs?.let { (s0, e0) ->
            // 搜索命中区间：全书偏移 → 块内偏移
            val s = s0 - blockGlobalOffset
            val e = e0 - blockGlobalOffset
            if (s >= 0 && s < text.length && e > s) {
                addStyle(SpanStyle(background = searchBg), s, e.coerceAtMost(text.length))
            }
        }
    }.toAnnotatedString()
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun TtsBar(
    paper: ReaderPaperPalette,
    tts: TtsEngineHost,
    chapterLabel: String,
    onPersistTts: (pitch: Float, volume: Float, voiceId: String, timedStop: Int, engine: String) -> Unit,
    onClose: () -> Unit,
) {
    val layout = LocalLayoutTokens.current
    val spec = LocalComponentSpec.current
    var showSettings by remember { mutableStateOf(false) }
    val haptic = rememberHaptic(false)
    val isPlaying = tts.status == "playing"

    // 悬浮墨玉微岛（Floating Player Island）。微岛收敛：它是嵌在底部 chrome 面板（ReaderPanelSurface，
    // paper.panel）**内部**的子岛，故保留比父面板更亮的 paper.bg 作为层级区分（改用 ReaderPanelSurface
    // 会与父面板同色而失去层次）；但圆角 / 发丝边 / 阴影全部收敛到共享令牌：islandRadius +
    // hairlineBorderWidth + hairlineAlpha + panelElevation(0dp)——原 6dp 阴影与 0.95 半透底色会在
    // 朗读高亮逐帧重绘期额外侵蚀帧预算。
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(spec.islandRadius),
        color = paper.bg,
        border = BorderStroke(spec.hairlineBorderWidth, paper.outlineVariant.copy(alpha = spec.hairlineAlpha)),
        shadowElevation = paper.panelElevation,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 纸墨微底座耳机图标：微岛收敛到 IconPedestal（pedestalRadius + hairline 令牌）。
            IconPedestal(
                icon = Icons.Outlined.Headphones,
                tint = MaterialTheme.colorScheme.primary,
                size = 34.dp,
                iconSize = 18.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$chapterLabel · ${tts.progressPercent.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = paper.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 语速指示升级为带图标的独立微胶囊芯片，带触觉反馈
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    showSettings = true
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                // 微岛收敛：发丝边走 hairline 令牌（原手写 0.6dp / 0.25f）。
                border = BorderStroke(spec.hairlineBorderWidth, MaterialTheme.colorScheme.primary.copy(alpha = spec.hairlineAlpha)),
                modifier = Modifier.padding(end = 4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        Icons.Outlined.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "${"%.1f".format(tts.pitch)}x",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // 上一段：36dp 紧凑触控微胶囊按钮
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    tts.prev()
                },
                enabled = tts.status != "idle",
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.SkipPrevious,
                        contentDescription = "上一段",
                        tint = if (tts.status != "idle") paper.fg else paper.fg.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // 播放/暂停大圆盘按钮：双层高光同心圆呼吸微底座（46dp 外层光晕 + 38dp 主色圆盘 + 阴影）
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 2.dp),
            ) {
                // 46dp 外层光晕呼吸微底座
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = if (isPlaying) 0.22f else 0.12f,
                            ),
                        )
                        .border(
                            spec.hairlineBorderWidth,
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = if (isPlaying) 0.38f else 0.18f,
                            ),
                            CircleShape,
                        ),
                )
                // 38dp 主色圆盘 + 阴影
                Surface(
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        when (tts.status) {
                            "playing" -> tts.pause()
                            "paused" -> tts.resume()
                            else -> tts.resume()
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // 下一段：36dp 紧凑触控微胶囊按钮
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    tts.next()
                },
                enabled = tts.status != "idle",
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.SkipNext,
                        contentDescription = "下一段",
                        tint = if (tts.status != "idle") paper.fg else paper.fg.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // 设置：36dp 紧凑触控微胶囊按钮
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    showSettings = true
                },
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "朗读设置",
                        tint = paper.fg.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // 关闭：36dp 紧凑触控微胶囊按钮
            Surface(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onClose()
                },
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "关闭朗读",
                        tint = paper.fg.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
    if (showSettings) {
        // G26：朗读设置弹层同样去除玻璃窗口模糊（GlassModalBottomSheet = ModalBottomSheet +
        // glassWindowBlur）。朗读栏常驻在阅读页底部，弹层进/退场时的整窗实时模糊会直接与
        // 翻页/朗读高亮争 GPU，已实测造成 141ms 阻塞帧（见 shelf-sort-performance-report.md §7）。
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = paper.bg,
            shape = LocalComponentSpec.current.sheetShape,
            dragHandle = { SheetHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            // 微岛收敛：朗读设置弹层内容此前未进入 ReaderPaperTheme，而 sheet 容器色已是 paper.bg——
            // 深色外壳 + 亮纸时会出现「浅底浅字」对比度塌陷。与其他 reader sheet（ReaderSheetHost）
            // 对齐，统一包一层 ReaderPaperTheme，让弹层内 Material 语义色随纸。
            ReaderPaperTheme(paper) {
                TtsSettingsContent(
                    tts = tts,
                    onPersistTts = onPersistTts,
                    onClose = { showSettings = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TtsSettingsContent(
    tts: TtsEngineHost,
    onPersistTts: (pitch: Float, volume: Float, voiceId: String, timedStop: Int, engine: String) -> Unit,
    onClose: () -> Unit,
) {
    val haptic = rememberHaptic(false)
    fun persist() = onPersistTts(tts.pitch, tts.volume, tts.voiceId, tts.timedStopMinutes, tts.engineId.key)

    ReaderSheetScaffold(title = "朗读设置") {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(LocalLayoutTokens.current.cardPadding),
        ) {
            Text(
                text = "使用设备内置的朗读声音。可在系统文字转语音设置中安装或切换声音。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )

            // 1. 语速与音调（暖橙底座 + Speed 图标 + 精准数值微胶囊指示与刻度标签）
            SettingHeader(
                title = "语速与音调",
                badgeIconTint = Color(0xFFEA580C),
                badgeIcon = Icons.Outlined.Speed,
                indicatorText = "${"%.2f".format(tts.pitch)}x",
                indicatorColor = Color(0xFFEA580C),
            )
            // 语速微胶囊 Chip 预设行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0.8f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speedPreset ->
                    val selected = kotlin.math.abs(tts.pitch - speedPreset) < 0.08f
                    Surface(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            tts.updatePitch(speedPreset)
                            persist()
                        },
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${speedPreset}x",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            // 平滑微导轨滑块
            Slider(
                value = tts.pitch,
                onValueChange = { tts.updatePitch(it) },
                onValueChangeFinished = { persist() },
                valueRange = 0.5f..2f,
                steps = 15,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("0.5x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text("1.0x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text("1.5x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text("2.0x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }

            // 2. 音量（天蓝底座 + VolumeUp 图标 + 精准数值微胶囊指示与刻度标签）
            SettingHeader(
                title = "音量",
                badgeIconTint = Color(0xFF0284C7),
                badgeIcon = Icons.AutoMirrored.Outlined.VolumeUp,
                indicatorText = "${(tts.volume * 100).toInt()}%",
                indicatorColor = Color(0xFF0284C7),
            )
            Slider(
                value = tts.volume,
                onValueChange = { tts.updateVolume(it) },
                onValueChangeFinished = { persist() },
                valueRange = 0f..1f,
                steps = 10,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("0%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text("50%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text("100%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }

            // 2.5 朗读引擎（系统语音离线可用；神经语音需联网，失败自动回退系统）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TtsEngineId.entries.forEach { engine ->
                    val selected = tts.engineId == engine
                    Surface(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            tts.switchEngine(engine)
                            persist()
                        },
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = engine.displayLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // 3. 声音与音色（圆润微胶囊芯片单选导轨，带选中态高光微徽章）
            val voices = tts.availableVoices
                .filter { it.locale.startsWith("zh") }
                .ifEmpty { tts.availableVoices }
            var voiceMenu by remember { mutableStateOf(false) }
            val currentVoiceLabel = voices.firstOrNull { it.name == tts.voiceId }?.let { "${it.name} (${it.locale})" } ?: "默认（系统）"

            SettingHeader(
                title = "声音与音色",
                badgeIconTint = Color(0xFF9333EA),
                badgeIcon = Icons.Outlined.Headphones,
                indicatorText = if (tts.voiceId.isBlank()) "默认" else tts.voiceId.take(8),
                indicatorColor = Color(0xFF9333EA),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val isDefault = tts.voiceId.isBlank()
                VoiceMicroChip(
                    label = "默认（系统）",
                    selected = isDefault,
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        tts.updateVoiceId("")
                        persist()
                    },
                )
                voices.forEach { v ->
                    val isSelected = tts.voiceId == v.name
                    VoiceMicroChip(
                        label = v.name,
                        selected = isSelected,
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            tts.updateVoiceId(v.name)
                            persist()
                        },
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    voiceMenu = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(currentVoiceLabel)
            }
            DropdownMenu(
                expanded = voiceMenu,
                onDismissRequest = { voiceMenu = false },
                modifier = Modifier.fillMaxWidth(0.9f),
            ) {
                DropdownMenuItem(
                    text = { Text("默认（系统）") },
                    onClick = {
                        haptic(HapticFeedbackType.TextHandleMove)
                        tts.updateVoiceId("")
                        voiceMenu = false
                        persist()
                    },
                )
                voices.forEach { v ->
                    DropdownMenuItem(
                        text = { Text("${v.name} (${v.locale})") },
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            tts.updateVoiceId(v.name)
                            voiceMenu = false
                            persist()
                        },
                    )
                }
            }

            // 4. 定时停止（「关闭 / 15分 / 30分 / 60分 / 播完本章」圆润微胶囊导轨）
            val stops = listOf(
                0 to "关闭",
                15 to "15分",
                30 to "30分",
                60 to "60分",
                -1 to "播完本章",
            )
            val currentStopLabel = when (tts.timedStopMinutes) {
                0 -> "已关闭"
                -1 -> "播完本章"
                else -> "${tts.timedStopMinutes}分钟"
            }
            SettingHeader(
                title = "定时停止",
                badgeIconTint = Color(0xFF16A34A),
                badgeIcon = Icons.Outlined.AccessTime,
                indicatorText = currentStopLabel,
                indicatorColor = Color(0xFF16A34A),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                stops.forEach { (m, label) ->
                    val selected = tts.timedStopMinutes == m
                    Surface(
                        onClick = {
                            haptic(HapticFeedbackType.TextHandleMove)
                            tts.setTimedStop(m)
                            persist()
                        },
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    haptic(HapticFeedbackType.TextHandleMove)
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("完成")
            }
        }
    }
}

/** 带有 26dp 独立微彩底座与精准指示微胶囊的设置小标题 */
@Composable
private fun SettingHeader(
    title: String,
    badgeIconTint: Color,
    badgeIcon: ImageVector,
    indicatorText: String? = null,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
) {
    val spec = LocalComponentSpec.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 26dp 独立微彩底座：微岛收敛到 IconPedestal（底座底色由 tint@0.14 派生，
        // 圆角走 pedestalRadius、发丝边走 hairline 令牌），原手写 badgeBg 实色入参已移除。
        IconPedestal(
            icon = badgeIcon,
            tint = badgeIconTint,
            size = 26.dp,
            iconSize = 15.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (indicatorText != null) {
            Surface(
                shape = CircleShape,
                color = indicatorColor.copy(alpha = 0.10f),
                // 微岛收敛：读数徽章发丝边走 hairline 令牌（原手写 0.6dp / 0.22f）。
                border = BorderStroke(spec.hairlineBorderWidth, indicatorColor.copy(alpha = spec.hairlineAlpha)),
            ) {
                Text(
                    text = indicatorText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = indicatorColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** 发音人圆润微胶囊芯片单选，带选中态高光微徽章 */
@Composable
private fun VoiceMicroChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
        modifier = Modifier.height(32.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            if (selected) {
                // 选中态高光微徽章
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
