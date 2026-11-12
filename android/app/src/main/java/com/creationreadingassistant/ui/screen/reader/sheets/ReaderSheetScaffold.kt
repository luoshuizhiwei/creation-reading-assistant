package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.creationreadingassistant.ui.layout.LocalLayoutTokens

/** A single visual contract for all reader sheets. */
@Composable
internal fun ReaderSheetScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    maxHeightFraction: Float = 0.90f,
    /**
     * READER-PAGES-v1：全屏附页能力（向后兼容）。true = 铺满剩余窗口高度
     * （不再按 [maxHeightFraction] 折算），供全屏页容器内的页面复用同一顶栏视觉；
     * 旧弹层调用保持默认 false，行为不变。
     */
    fullScreen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val layout = LocalLayoutTokens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fullScreen) Modifier.fillMaxHeight() else Modifier.fillMaxHeight(maxHeightFraction))
            // 系统导航栏已隐藏：只避开底部手势区（恒定，不随 transient swipe 跳动，
            // 三键导航下为 0，不产生空白系统栏）。
            .windowInsetsPadding(WindowInsets.mandatorySystemGestures)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = layout.relatedGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = if (onBack == null) layout.relatedGap else layout.microGap),
            )
            trailing?.invoke()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        content()
    }
}
