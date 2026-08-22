package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.GlassAlertDialog
import com.creationreadingassistant.ui.screen.reader.formatDuration

@Composable
internal fun BookInfoSheet(
    bookTitle: String,
    bookAuthor: String?,
    bookFormat: String,
    chapterCount: Int,
    wordCount: Int,
    currentChapterTitle: String,
    progressPercent: Float,
    activeReadingMs: Long,
    savedReadingMs: Long,
    sessionsCount: Int,
    sourceFile: String?,
    onOpenSettings: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        GlassAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除本书") },
            text = { Text("确定从书架移除《${bookTitle}》吗？本地正文文件、阅读进度和笔记将一并移除，删除后可随时从书架恢复。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
    ReaderSheetScaffold(title = "书籍信息") {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        ) {
            item {
                Text(bookTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                Text(
                    "${bookAuthor ?: "作者未知"} · ${bookFormat.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(16.dp))
                Text("阅读统计", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatCell("本次已读", formatDuration(activeReadingMs))
                    StatCell("累计阅读", formatDuration(savedReadingMs))
                    StatCell("阅读次数", "$sessionsCount")
                    StatCell("进度", "${progressPercent.toInt()}%")
                }
                Spacer(Modifier.height(16.dp))
                Text("正文信息", style = MaterialTheme.typography.titleSmall)
                InfoRow("章节数", "$chapterCount 章")
                InfoRow("总字数", "${wordCount} 字")
                InfoRow("当前章节", currentChapterTitle.ifBlank { "正文" })
                InfoRow("来源文件", sourceFile ?: "本地导入")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("阅读设置") }
                Spacer(Modifier.height(24.dp))
                Text("危险操作", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("删除本书") }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
