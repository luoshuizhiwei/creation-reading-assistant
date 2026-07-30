package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.layout.LocalLayoutTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
// R8：独立「主题外观」底部 sheet（对照 web theme-sheet，仅承载纸色背景选择）
internal fun ThemeSheet(
    background: String,
    onBackground: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(LocalLayoutTokens.current.cardPadding)) {
        Text("主题外观", style = MaterialTheme.typography.titleLarge)
        Text("纸张背景", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // 收敛为定稿 4 档 + 跟随外观（旧 7 色 key 已由 SettingsStore.migrateReaderBg 迁移，不再暴露入口）
            listOf(
                "follow" to "跟随外观",
                "white" to "白纸", "warm" to "暖纸", "green" to "护眼", "night" to "夜读",
            ).forEach { (v, label) ->
                OptionPill(selected = background == v, label = label, onClick = { onBackground(v) })
            }
        }
    }
}
