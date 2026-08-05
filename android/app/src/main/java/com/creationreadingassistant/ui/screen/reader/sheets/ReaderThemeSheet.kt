package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.paperPalette
import com.creationreadingassistant.ui.theme.rememberHaptic
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.ReaderPaperOptions

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeSheet(
    background: String,
    appDark: Boolean,
    onBackground: (String) -> Unit,
) {
    val layout = LocalLayoutTokens.current
    val reducedMotion = rememberReducedMotion()
    val haptic = rememberHaptic(reducedMotion)
    ReaderSheetScaffold(title = "主题外观") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(layout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(layout.contentGap),
        ) {
            Text(
                "选择只影响阅读页，不改变应用其他页面。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 3,
                horizontalArrangement = Arrangement.spacedBy(layout.relatedGap),
                verticalArrangement = Arrangement.spacedBy(layout.relatedGap),
            ) {
                ReaderPaperOptions.forEach { option ->
                    val palette = paperPalette(option.key, appDark)
                    val selected = option.key == background
                    Surface(
                        onClick = { haptic(HapticFeedbackType.TextHandleMove); onBackground(option.key) },
                        modifier = Modifier.weight(1f),
                        shape = LocalComponentSpec.current.cardShape,
                        color = palette.bg,
                        contentColor = palette.fg,
                        border = BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            if (selected) palette.accent else palette.outlineVariant,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.fillMaxWidth()) {
                                Text("Aa", style = MaterialTheme.typography.titleLarge, color = palette.fg)
                                if (selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "已选择",
                                        tint = palette.accent,
                                        modifier = Modifier.align(Alignment.TopEnd),
                                    )
                                }
                            }
                            Text(option.label, style = MaterialTheme.typography.labelMedium, color = palette.fg)
                            Text("正文 · 注释", style = MaterialTheme.typography.labelSmall, color = palette.fgMuted)
                        }
                    }
                }
            }
        }
    }
}
