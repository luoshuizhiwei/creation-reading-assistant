package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.ui.screen.reader.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
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
    var slider by remember { mutableFloatStateOf(progressPercent) }
    var percentDraft by remember { mutableStateOf("") }
    val size = epubBook?.chapters?.size ?: 0

    fun seekTo(percent: Float) {
        val p = percent.coerceIn(0f, 100f)
        if (size > 0) onChapter((p / 100f * size).toInt().coerceIn(0, size - 1))
        else onSeekPercent(p)
    }

    ReaderSheetScaffold(title = "阅读进度", modifier = Modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatCell("阅读", formatDuration(savedBookReadingMs + activeReadingMs))
            StatCell("字/分", if (readerSpeed > 0) "$readerSpeed" else "—")
            StatCell("读完", if (estimatedRemainingMs > 0) formatDuration(estimatedRemainingMs) else "—")
            StatCell("灵感", "$inspirationsCount")
            StatCell("书签", "$bookmarksCount")
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChapter(chapterIndex - 1) }, enabled = chapterIndex > 0) {
                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上一章")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(currentChapterTitle.ifBlank { "正文" }, fontWeight = FontWeight.Bold)
                Text("${progressPercent.toInt()}%", color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = { onChapter(chapterIndex + 1) }, enabled = chapterIndex < size - 1) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下一章")
            }
        }
        if (size > 0 || isTxt) {
            // 拖动预览：滑动中实时显示目标百分比，松手才真正跳转
            val dragging = slider != progressPercent && !slider.isNaN()
            Column(Modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (dragging) {
                    Text(
                        "跳到 ${slider.toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = slider,
                    onValueChange = { slider = it },
                    valueRange = 0f..100f,
                    onValueChangeFinished = { seekTo(slider) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // 精确跳转：输入 0-100 的百分比回车或点「跳转」
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = percentDraft,
                    onValueChange = { raw -> percentDraft = raw.filter { it.isDigit() }.take(3) },
                    label = { Text("跳到 %") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp),
                )
                TextButton(
                    onClick = {
                        percentDraft.toIntOrNull()?.let { seekTo(it.toFloat()) }
                        percentDraft = ""
                    },
                    enabled = percentDraft.toIntOrNull()?.let { it in 0..100 } == true,
                ) { Text("跳转") }
            }
        }
    }
}
