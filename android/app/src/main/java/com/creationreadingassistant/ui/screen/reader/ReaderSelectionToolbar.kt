package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.components.SettingRow
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.screen.reader.sheets.HIGHLIGHT_COLORS
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SelectionToolbar(
    paper: ReaderPaperPalette,
    selectedText: String,
    showColorRow: Boolean,
    onToggleColor: () -> Unit,
    onPickColor: (String) -> Unit,
    onAiExplain: () -> Unit,
    onInspiration: () -> Unit,
    onNote: () -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = paper.bg,
            border = BorderStroke(1.dp, paper.outlineVariant),
            shape = LocalComponentSpec.current.sheetShape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            Column(Modifier.padding(8.dp)) {
                Text(
                    selectedText.take(42) + if (selectedText.length > 42) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    if (showColorRow) {
                        HIGHLIGHT_COLORS.forEach { c ->
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .background(paper.highlightSolid(c), shape = CircleShape)
                                    .clickable { onPickColor(c) },
                            )
                        }
                        TextButton(onClick = onToggleColor) { Text("取消") }
                    } else {
                        TextButton(onClick = onToggleColor) { Text("高亮") }
                        TextButton(onClick = onAiExplain) { Text("AI 解读") }
                        TextButton(onClick = onInspiration) { Text("记为灵感") }
                        TextButton(onClick = onNote) { Text("存笔记") }
                        TextButton(onClick = onCopy) { Text("复制") }
                        TextButton(onClick = onSearch) { Text("搜索") }
                        TextButton(onClick = onClear) { Text("清除") }
                    }
                }
            }
        }
    }
}
