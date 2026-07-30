package com.creationreadingassistant.ui.screen.reader.tts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.GlassModalBottomSheet
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.ReaderPaperPalette

@Composable
internal fun rememberTts(): TtsController {
    val context = LocalContext.current
    val controller = remember { TtsController(context) }
    DisposableEffect(controller) { onDispose { controller.release() } }
    return controller
}

/**
 * 为单个文本块构造带「当前朗读句」高亮背景的 AnnotatedString（EPUB 逐句高亮，对照 TXT 机制）。
 * [blockGlobalOffset] 为该块在全书文本中的全局偏移；[chapterBase] 为所在章节在全书中的起始偏移，
 * 二者之差即为块在章节内（= TTS contentText）的偏移。
 */
internal fun buildSentenceHighlighted(
    text: String,
    blockGlobalOffset: Int,
    chapterBase: Int,
    ttsSentenceRange: Pair<Int, Int>?,
    bg: Color,
): AnnotatedString {
    if (ttsSentenceRange == null || blockGlobalOffset < 0) return AnnotatedString(text)
    val s = ttsSentenceRange.first - (blockGlobalOffset - chapterBase)
    val e = ttsSentenceRange.second - (blockGlobalOffset - chapterBase)
    if (s < 0 || s >= text.length || e <= s) return AnnotatedString(text)
    return AnnotatedString.Builder(text).apply {
        addStyle(SpanStyle(background = bg), s, e.coerceAtMost(text.length))
    }.toAnnotatedString()
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun TtsBar(
    paper: ReaderPaperPalette,
    tts: TtsController,
    chapterLabel: String,
    onPersistTts: (pitch: Float, volume: Float, voiceId: String, timedStop: Int) -> Unit,
    onClose: () -> Unit,
) {
    val layout = LocalLayoutTokens.current
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    var showSettings by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = layout.relatedGap, vertical = layout.microGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Headphones,
                contentDescription = null,
                modifier = Modifier.padding(layout.relatedGap),
            )
            Column(Modifier.weight(1f)) {
                Text(chapterLabel, style = MaterialTheme.typography.titleSmall)
                Text(
                    "段落进度 ${tts.progressPercent.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "朗读设置")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "关闭朗读")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = tts::prev, enabled = tts.status != "idle") {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "上一段")
            }
            IconButton(
                onClick = {
                    when (tts.status) {
                        "playing" -> tts.pause()
                        "paused" -> tts.resume()
                        else -> tts.resume()
                    }
                },
            ) {
                Icon(
                    if (tts.status == "playing") Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (tts.status == "playing") "暂停" else "播放",
                )
            }
            IconButton(onClick = tts::next, enabled = tts.status != "idle") {
                Icon(Icons.Filled.SkipNext, contentDescription = "下一段")
            }
            IconButton(onClick = tts::stop, enabled = tts.status != "idle") {
                Icon(Icons.Filled.Stop, contentDescription = "停止")
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = layout.microGap),
            horizontalArrangement = Arrangement.spacedBy(layout.microGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("语速", style = MaterialTheme.typography.labelSmall)
            speeds.forEach { s ->
                Button(
                    onClick = { tts.rate = s },
                    modifier = Modifier.heightIn(min = layout.minimumTouchTarget),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    colors = if (tts.rate == s) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.textButtonColors(),
                ) {
                    Text("${s}x", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    if (showSettings) {
        GlassModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = paper.bg,
            shape = LocalComponentSpec.current.sheetShape,
            dragHandle = { SheetHandle() },
        ) {
            TtsSettingsContent(
                tts = tts,
                onPersistTts = onPersistTts,
                onClose = { showSettings = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TtsSettingsContent(
    tts: TtsController,
    onPersistTts: (pitch: Float, volume: Float, voiceId: String, timedStop: Int) -> Unit,
    onClose: () -> Unit,
) {
    fun persist() = onPersistTts(tts.pitch, tts.volume, tts.voiceId, tts.timedStopMinutes)
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(LocalLayoutTokens.current.cardPadding),
    ) {
        Text("朗读设置", style = MaterialTheme.typography.titleMedium)
        Text(
            "说明：原生仅支持设备本地 TTS 引擎，暂不支持联网云端音色（web 端的 online 引擎）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )

        SettingLabel("音调（Pitch）")
        Slider(
            value = tts.pitch,
            onValueChange = { tts.updatePitch(it) },
            onValueChangeFinished = { persist() },
            valueRange = 0.5f..2f,
            steps = 15,
        )
        Text("${"%.2f".format(tts.pitch)}x", style = MaterialTheme.typography.bodySmall)

        SettingLabel("音量（Volume）")
        Slider(
            value = tts.volume,
            onValueChange = { tts.updateVolume(it) },
            onValueChangeFinished = { persist() },
            valueRange = 0f..1f,
            steps = 10,
        )
        Text("${(tts.volume * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)

        val voices = tts.availableVoices
            .filter { it.locale.language == "zh" }
            .ifEmpty { tts.availableVoices }
        var voiceMenu by remember { mutableStateOf(false) }
        SettingLabel("音色（Voice）")
        OutlinedButton(onClick = { voiceMenu = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                voices.firstOrNull { it.name == tts.voiceId }
                    ?.let { "${it.name} (${it.locale})" } ?: "默认（系统）",
            )
        }
        DropdownMenu(
            expanded = voiceMenu,
            onDismissRequest = { voiceMenu = false },
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            DropdownMenuItem(
                text = { Text("默认（系统）") },
                onClick = { tts.updateVoiceId(""); voiceMenu = false; persist() },
            )
            voices.forEach { v ->
                DropdownMenuItem(
                    text = { Text("${v.name} (${v.locale})") },
                    onClick = { tts.updateVoiceId(v.name); voiceMenu = false; persist() },
                )
            }
        }

        val stops = listOf(0, 15, 30, 45, 60)
        var stopMenu by remember { mutableStateOf(false) }
        SettingLabel("定时停止")
        OutlinedButton(onClick = { stopMenu = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (tts.timedStopMinutes == 0) "关闭" else "${tts.timedStopMinutes} 分钟")
        }
        DropdownMenu(
            expanded = stopMenu,
            onDismissRequest = { stopMenu = false },
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            stops.forEach { m ->
                DropdownMenuItem(
                    text = { Text(if (m == 0) "关闭" else "$m 分钟") },
                    onClick = { tts.setTimedStop(m); stopMenu = false; persist() },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("完成")
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}
