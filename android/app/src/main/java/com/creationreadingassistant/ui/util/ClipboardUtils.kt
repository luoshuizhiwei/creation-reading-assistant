package com.creationreadingassistant.ui.util

import android.content.ClipData
import android.util.Log
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "ClipboardUtils"

/**
 * 真正可以写进剪贴板的内容；空白选区返回 null。
 *
 * 空文本必须**拒绝**而不是照抄：写入空串会把用户原有的剪贴板内容清掉，
 * 而调用方仍然弹「已复制」——一次静默的数据破坏叠加一次虚假成功。
 */
internal fun copyableSelection(text: String): String? = text.takeIf { it.isNotBlank() }

/**
 * 剪贴板复制工具，封装 Compose 1.7+ [Clipboard]（替代已废弃的 LocalClipboardManager）。
 *
 * 返回值语义：**是否成功提交**。
 * - 空白文本 → `false`，且**不触碰**剪贴板（保留用户原有内容）；
 * - [scope] 为 null（同步路径）→ 返回 `setPrimaryClip` 的真实结果；
 * - 传入 [scope]（异步路径）→ 返回 `true` 表示已提交任务；Compose 的 `setClipEntry`
 *   是挂起函数，异常只会在协程内部抛出，因此**必须在协程里捕获**，
 *   否则会以未捕获异常直接崩掉进程。
 *
 * Clipboard 服务在部分 ROM / 受限用户 / 系统服务重启窗口会抛
 * `SecurityException`、`IllegalStateException` 或 `RuntimeException`；
 * 这里一律降级为 `false` 并记日志，由调用方给用户可理解的提示，绝不静默假装成功。
 */
fun Clipboard.copyText(
    text: String,
    scope: CoroutineScope? = null,
    label: String = "text",
): Boolean {
    val payload = copyableSelection(text) ?: return false
    val clipData = ClipData.newPlainText(label, payload)

    if (scope == null) {
        return runCatching { nativeClipboard.setPrimaryClip(clipData) }
            .onFailure { Log.w(TAG, "同步写剪贴板失败（label=$label）", it) }
            .isSuccess
    }

    scope.launch {
        runCatching { setClipEntry(ClipEntry(clipData)) }
            .onFailure { Log.w(TAG, "异步写剪贴板失败（label=$label）", it) }
    }
    return true
}
