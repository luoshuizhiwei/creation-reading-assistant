package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.creationreadingassistant.ui.components.SelectablePill

/** 阅读设置/主题中的选项胶囊：统一委托 SelectablePill，自动跟随三套主题圆角/描边/底色。 */
@Composable
internal fun OptionPill(selected: Boolean, label: String, onClick: () -> Unit) {
    SelectablePill(
        text = label,
        selected = selected,
        onClick = onClick,
        selectedBorderTint = MaterialTheme.colorScheme.secondary,
    )
}

/** 高亮颜色常量，供 SelectionToolbar / NotesSheet 共享。 */
internal val HIGHLIGHT_COLORS = listOf("yellow", "red", "green", "blue", "purple")
