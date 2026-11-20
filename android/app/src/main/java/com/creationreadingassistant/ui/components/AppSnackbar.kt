package com.creationreadingassistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 全局统一的主题化 SnackbarHost。
 *
 * 替换默认纯黑硬编码背景，使提示小弹窗完全遵循当前应用/阅读主题配色，
 * 具备精致圆角、柔和边框与阴影，文字与背景对比度满足 WCAG AA 4.5:1 要求。
 */
@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
        snackbar = { snackbarData ->
            AppSnackbar(snackbarData = snackbarData)
        },
    )
}

/**
 * 契合项目视觉风格的主题化 Snackbar 容器。
 */
@Composable
fun AppSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
    actionOnNewLine: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = MaterialTheme.colorScheme.onSurface
    val actionColor = MaterialTheme.colorScheme.primary
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(
                elevation = 4.dp,
                shape = shape,
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Snackbar(
            snackbarData = snackbarData,
            actionOnNewLine = actionOnNewLine,
            shape = shape,
            containerColor = Color.Transparent,
            contentColor = contentColor,
            actionColor = actionColor,
            actionContentColor = actionColor,
            dismissActionContentColor = contentColor,
        )
    }
}
