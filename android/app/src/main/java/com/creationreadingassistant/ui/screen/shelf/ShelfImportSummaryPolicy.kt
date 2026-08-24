package com.creationreadingassistant.ui.screen.shelf

import com.creationreadingassistant.ui.viewmodel.ImportBatchUiState

internal fun importBatchSnackbarMessage(batch: ImportBatchUiState): String {
    val details = buildList {
        add("成功 ${batch.succeeded} 本")
        if (batch.duplicates > 0) add("重复 ${batch.duplicates} 本")
        if (batch.skipped > 0) add("跳过 ${batch.skipped} 项")
        if (batch.failed > 0 || batch.stopped == 0) add("失败 ${batch.failed} 本")
        if (batch.stopped > 0) add("未处理 ${batch.stopped} 本")
        if (batch.unreadableFolders > 0) add("不可读文件夹 ${batch.unreadableFolders} 个")
        if (batch.truncated) add("扫描结果已截断")
    }
    val title = if (batch.stopped > 0) "导入已停止" else "导入完成"
    return "$title：${details.joinToString("，")}"
}
