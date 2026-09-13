package com.creationreadingassistant.ui.screen.profile

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.creationreadingassistant.ui.components.GlassAlertDialog

/**
 * 「重建全库搜索索引」的二次确认弹窗（R6-B7）。
 *
 * 这是一个**破坏性动作**：会重置增量索引游标并在后台分窗口重扫全书，
 * 重建期间搜索结果可能暂时不全。
 *
 * 目前有两个入口可触发：
 * - 「日志与诊断」子页的「重建搜索索引」按钮
 * - 「存储」子页搜索索引卡片的「重建索引」按钮
 *
 * 两者必须共用同一份文案与同一条确认流。此前这两处各实现了一遍完全相同的
 * [GlassAlertDialog]（约 27 行 × 2），任一处后续改文案或改动作都会造成
 * **同一破坏性动作在两个入口行为漂移**，因此收口为单一组件。
 *
 * 调用方仍负责持有 `showConfirm` 状态并在 [onConfirm] 里关闭弹窗 + 派发
 * `ProfileAction.RebuildSearchIndex`，本组件不持有任何状态。
 */
@Composable
internal fun RebuildSearchIndexConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重建搜索索引？") },
        text = {
            Text(
                "将重置索引进度并重新扫描全部书籍（后台分窗口进行，不阻塞使用）。" +
                    "重建期间搜索结果可能暂时不全。适用于搜索结果异常或覆盖状态不一致时。",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("开始重建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
