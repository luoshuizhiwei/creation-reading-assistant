package com.creationreadingassistant.ui.screen.reader.tts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Button
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
import com.creationreadingassistant.ui.components.SettingSegmentedRow
import com.creationreadingassistant.ui.components.SheetHandle
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.screen.reader.sheets.ReaderSheetScaffold

@Composable
internal fun rememberTts(): TtsController {
    val context = LocalContext.current
    val controller = remember { TtsController(context) }
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
    tts: TtsController,
    chapterLabel: String,
    onPersistTts: (pitch: Float, volume: Float, voiceId: String, timedStop: Int) -> Unit,
    onClose: () -> Unit,
) {
    val layout = LocalLayoutTokens.current
    var showSettings by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = layout.relatedGap, vertical = layout.microGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Headphones,
            contentDescription = null,
            modifier = Modifier.padding(horizontal = layout.microGap),
        )
        Text(
            "$chapterLabel · ${tts.progressPercent.toInt()}%",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = tts::prev, enabled = tts.status != "idle") {
            Icon(Icons.Outlined.SkipPrevious, contentDescription = "上一段")
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
                if (tts.status == "playing") Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (tts.status == "playing") "暂停" else "播放",
            )
        }
        IconButton(onClick = tts::next, enabled = tts.status != "idle") {
            Icon(Icons.Outlined.SkipNext, contentDescription = "下一段")
        }
        IconButton(onClick = { showSettings = true }) {
            Icon(Icons.Outlined.Settings, contentDescription = "朗读设置")
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = "关闭朗读")
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
    ReaderSheetScaffold(title = "朗读设置") {
      Column(
          Modifier
              .fillMaxWidth()
              .weight(1f)
              .verticalScroll(rememberScrollState())
              .padding(LocalLayoutTokens.current.cardPadding),
      ) {
        Text(
            "使用设备内置的朗读声音。可在系统文字转语音设置中安装或切换声音。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )

        SettingLabel("音调")
        Slider(
            value = tts.pitch,
            onValueChange = { tts.updatePitch(it) },
            onValueChangeFinished = { persist() },
            valueRange = 0.5f..2f,
            steps = 15,
        )
        Text("${"%.2f".format(tts.pitch)}x", style = MaterialTheme.typography.bodySmall)

        SettingLabel("音量")
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
        SettingLabel("声音")
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
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}
