package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.screen.reader.sheets.HIGHLIGHT_COLORS
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.ReaderPaperPalette

internal data class SelectionToolbarActionSpec(
    val id: String,
    val label: String,
)

internal val selectionPrimaryActions = listOf(
    SelectionToolbarActionSpec("highlight", "高亮"),
    SelectionToolbarActionSpec("note", "笔记"),
    SelectionToolbarActionSpec("copy", "复制"),
    SelectionToolbarActionSpec("more", "更多"),
)

internal val selectionMoreActions = listOf(
    SelectionToolbarActionSpec("ai", "AI 解读"),
    SelectionToolbarActionSpec("inspiration", "记为灵感"),
    SelectionToolbarActionSpec("search", "搜索"),
    SelectionToolbarActionSpec("cancel", "取消选择"),
)

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
    var moreExpanded by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = paper.bg,
            border = BorderStroke(1.dp, paper.outlineVariant),
            shape = LocalComponentSpec.current.sheetShape,
            modifier = Modifier
                .padding(8.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = selectedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (showColorRow) {
                    Text(
                        text = "选择高亮颜色",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        HIGHLIGHT_COLORS.forEach { colorName ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .semantics { contentDescription = "高亮颜色 $colorName" }
                                    .clickable { onPickColor(colorName) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .background(paper.highlightSolid(colorName), shape = CircleShape),
                                )
                            }
                        }
                        TextButton(
                            onClick = onToggleColor,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("返回")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onToggleColor,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Text(selectionPrimaryActions[0].label)
                        }
                        TextButton(
                            onClick = onNote,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Text(selectionPrimaryActions[1].label)
                        }
                        TextButton(
                            onClick = onCopy,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Text(selectionPrimaryActions[2].label)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            TextButton(
                                onClick = { moreExpanded = true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Text(selectionPrimaryActions[3].label)
                            }
                            DropdownMenu(
                                expanded = moreExpanded,
                                onDismissRequest = { moreExpanded = false },
                            ) {
                                selectionMoreActions.forEach { action ->
                                    DropdownMenuItem(
                                        text = { Text(action.label) },
                                        onClick = {
                                            moreExpanded = false
                                            when (action.id) {
                                                "ai" -> onAiExplain()
                                                "inspiration" -> onInspiration()
                                                "search" -> onSearch()
                                                "cancel" -> onClear()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
