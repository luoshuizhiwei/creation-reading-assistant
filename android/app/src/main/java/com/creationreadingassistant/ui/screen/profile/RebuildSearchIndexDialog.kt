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

/**
 * 「停止索引构建」的二次确认弹窗（CRA-SEARCH-INDEX-CANCEL-CONTROL-v1）。
 *
 * 与 [RebuildSearchIndexConfirmDialog] 配对使用，但语义**相反**：
 * - 重建 = 重置游标、从第 0 本重扫（破坏性，见上）；
 * - 停止 = 只中止**当前**这次后台构建，**保留已提交的章节与扫描断点**，
 *   下次触发时按既有续建语义从断点继续 —— **不是**清空后重建。
 *
 * 文案刻意写明「不是把索引清空/重来」与「不会显示为已完成」，避免用户把
 * 「停止」误读成「索引已建好」。调用方（存储页搜索索引卡片 / 日志与诊断页）
 * 负责在**在跑或已排队**（`Progress.canCancel == true`）时渲染停止入口，
 * 并在 [onConfirm] 里派发取消动作；本组件不持有任何状态。
 *
 * @param queued 为 true 表示这次要取消的是**已排队、尚未开始扫书**的构建
 *               （不是正在跑的那个）：措辞相应改成「撤下排队」，不说「停止当前构建」。
 */
@Composable
internal fun SearchIndexCancelConfirmDialog(
    queued: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (queued) "撤下排队的索引构建？" else "停止索引构建？") },
        text = {
            Text(
                (if (queued) "将撤下这次已排队、尚未开始的后台索引构建。" else "将停止当前正在进行的后台索引构建。") +
                    "已经建好的章节与扫描断点会保留，" +
                    "下次冷启动或再次触发时从断点继续，不会清空重来；" +
                    "在重新构建完成前，搜索覆盖度仍显示为当前实际进度（不会显示为已完成）。",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(if (queued) "撤下排队" else "停止构建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (queued) "保留排队" else "继续构建") }
        },
    )
}
