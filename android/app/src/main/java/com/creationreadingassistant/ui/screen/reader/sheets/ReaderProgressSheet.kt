package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
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
    val size = epubBook?.chapters?.size ?: 0
    Column(Modifier.fillMaxWidth().padding(LocalLayoutTokens.current.cardPadding)) {
        Text("阅读进度", style = MaterialTheme.typography.titleLarge)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatCell("阅读", formatDuration(savedBookReadingMs + activeReadingMs))
            StatCell("字/分", if (readerSpeed > 0) "$readerSpeed" else "—")
            StatCell("读完", if (estimatedRemainingMs > 0) formatDuration(estimatedRemainingMs) else "—")
            StatCell("灵感", "$inspirationsCount")
            StatCell("书签", "$bookmarksCount")
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChapter(chapterIndex - 1) }, enabled = chapterIndex > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上一章")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(currentChapterTitle.ifBlank { "正文" }, fontWeight = FontWeight.Bold)
                Text("${progressPercent.toInt()}%", color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = { onChapter(chapterIndex + 1) }, enabled = chapterIndex < size - 1) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下一章")
            }
        }
        if (size > 0 || isTxt) {
            Slider(
                value = slider,
                onValueChange = { slider = it },
                valueRange = 0f..100f,
                onValueChangeFinished = {
                    if (size > 0) onChapter((slider / 100f * size).toInt().coerceIn(0, size - 1))
                    else onSeekPercent(slider)
                },
            )
        }
    }
}
