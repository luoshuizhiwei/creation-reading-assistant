package com.creationreadingassistant.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import com.creationreadingassistant.ui.theme.DialogShape
import com.creationreadingassistant.ui.theme.LocalGlassPalette
import com.creationreadingassistant.ui.theme.glassWindowBlur
import com.creationreadingassistant.ui.window.LocalDialogSystemBarsEffect

/**
 * 应用统一居中对话框。
 *
 * 对话框使用四角圆角；底部弹层仍使用只圆顶部两角的 sheet shape。阅读器等性能敏感路径
 * 直接使用此入口，不启用整窗模糊。
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = DialogShape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        // M-BUG-11：对话框是**独立 Window**，不继承 Activity 窗口的系统栏策略——沉浸阅读时
        // 弹出任何对话框（笔记编辑、删除确认…）都会让被隐藏的系统状态栏重新露出。
        // 这里挂在 confirmButton 槽内：M3 AlertDialog 的按钮行恒定参与组合，且位于 Dialog
        // 自己的窗口内，是能触达该窗口的稳定挂载点（title/text/icon 都可为空，不能用）。
        // 未提供弹层策略时（非沉浸场景）该调用不做任何事，全应用其余弹层行为不变。
        confirmButton = {
            LocalDialogSystemBarsEffect()
            confirmButton()
        },
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

/**
 * Liquid Glass 版 [ModalBottomSheet]（T8）。
 *
 * 签名与 Material3 `ModalBottomSheet` 对齐，可直接替换业务调用；
 * 在 Dialog 宿主窗口上叠加真 Window 模糊（`glassWindowBlur`），**仅 APPLE 且 API31+ 生效**，
 * DEFAULT / WEB 下行为与 Material3 原版完全一致（不影响回归）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = androidx.compose.material3.rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = Dp.Unspecified,
    shape: androidx.compose.ui.graphics.Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = BottomSheetDefaults.Elevation,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { WindowInsets(0, 0, 0, 0) },
    properties: androidx.compose.material3.ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.glassWindowBlur(LocalGlassPalette.current != null),
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        scrimColor = scrimColor,
        dragHandle = dragHandle,
        contentWindowInsets = contentWindowInsets,
        properties = properties,
        content = content,
    )
}

/**
 * Liquid Glass 版 [AlertDialog]（T8）。
 *
 * 签名与 Material3 `AlertDialog`（confirmButton 重载）对齐，可直接替换业务调用；
 * 在 Dialog 宿主窗口上叠加真 Window 模糊（`glassWindowBlur`），**仅 APPLE 且 API31+ 生效**，
 * DEFAULT / WEB 下行为与 Material3 原版完全一致（不影响回归）。
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = DialogShape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.glassWindowBlur(LocalGlassPalette.current != null),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}
