package com.creationreadingassistant.ui.screen.reader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.creationreadingassistant.data.settings.SelectionActions
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val TAG = "SelectionExternalActions"
private const val PROCESS_TEXT_CHOOSER_TITLE = "选择词典或文本工具"

internal enum class SelectionExternalLaunchResult {
    OPENED,
    OPENED_WEB_FALLBACK,
    NO_HANDLER,
}

private const val WEB_QUERY_LIMIT = SelectionActions.MIN_WEB_QUERY_CHARS
private const val DICTIONARY_QUERY_LIMIT = SelectionActions.MIN_DICT_QUERY_CHARS

internal const val NOTICE_BLANK_SELECTION = "请先选择要查询的文字"
internal const val NOTICE_OPENING_BROWSER = "正在打开浏览器…"
internal const val NOTICE_OPENING_SYSTEM_DICT = "正在调用系统文字工具…"
internal const val NOTICE_OPENING_ONLINE_DICT = "正在打开在线词典…"
internal const val NOTICE_NO_BROWSER = "未找到可用浏览器"
internal const val NOTICE_NO_DICT_OR_BROWSER = "未找到可用词典或浏览器"
internal const val NOTICE_DICT_FALLBACK = "未找到词典应用，已用在线词典查询"

internal enum class ExternalActionKind {
    BROWSER,
    SYSTEM_DICTIONARY,
    ONLINE_DICTIONARY,
}

internal fun selectionExternalPreJumpNotice(kind: ExternalActionKind): String = when (kind) {
    ExternalActionKind.BROWSER -> NOTICE_OPENING_BROWSER
    ExternalActionKind.SYSTEM_DICTIONARY -> NOTICE_OPENING_SYSTEM_DICT
    ExternalActionKind.ONLINE_DICTIONARY -> NOTICE_OPENING_ONLINE_DICT
}

internal fun selectionExternalFailureNotice(kind: ExternalActionKind): String = when (kind) {
    ExternalActionKind.BROWSER, ExternalActionKind.ONLINE_DICTIONARY -> NOTICE_NO_BROWSER
    ExternalActionKind.SYSTEM_DICTIONARY -> NOTICE_NO_DICT_OR_BROWSER
}

/**
 * 外部查询失败时给用户的可理解提示；选区为空时**先于**任何 Intent 判定。
 *
 * 为什么需要它：空选区走的是同一条 `NO_HANDLER` 分支，调用方如果只按返回值分支，
 * 会对一次「其实什么都没选」的操作报「未找到可用浏览器」——一个把用户引向错误方向的诊断。
 * 该函数是纯函数，供调用方在发起外部动作前先判空。
 */
internal fun blankSelectionNotice(text: String): String? =
    if (text.isBlank()) NOTICE_BLANK_SELECTION else null

/**
 * 外部查询的查询串：去首尾空白、折叠内部连续空白、按 [maxChars] 截断。
 *
 * 截断**不会切断 UTF-16 代理对**（emoji、部分生僻字）：截在半个代理对上的话，
 * URL 编码器会把非法序列静默替换成 `?`，用户看到的查询词就和选中的不一样了。
 */
internal fun normalizedSelectionQuery(text: String, maxChars: Int): String =
    text.trim()
        .replace(Regex("\\s+"), " ")
        .boundedByChars(maxChars)

private fun String.boundedByChars(maxChars: Int): String {
    if (maxChars <= 0) return ""
    if (length <= maxChars) return this
    val end = if (Character.isHighSurrogate(this[maxChars - 1])) maxChars - 1 else maxChars
    return substring(0, end)
}

/** R3-X1：查询地址由用户配置的模板决定（含 `{q}` 占位符）；模板非法时回退默认。 */
internal fun selectionBrowserUrl(
    text: String,
    template: String = SelectionActions.DEFAULT_BROWSER_TEMPLATE,
): String? =
    encodedSelectionQuery(text, WEB_QUERY_LIMIT)?.let { SelectionActions.buildUrl(template, it) }

internal fun selectionDictionaryUrl(
    text: String,
    template: String = SelectionActions.DEFAULT_DICTIONARY_TEMPLATE,
): String? =
    encodedSelectionQuery(text, DICTIONARY_QUERY_LIMIT)?.let { SelectionActions.buildUrl(template, it) }

private fun encodedSelectionQuery(text: String, maxChars: Int): String? {
    val query = normalizedSelectionQuery(text, maxChars)
    if (query.isBlank()) return null
    return URLEncoder.encode(query, StandardCharsets.UTF_8.name())
}

internal fun Context.openSelectionInBrowser(
    text: String,
    template: String = SelectionActions.DEFAULT_BROWSER_TEMPLATE,
): SelectionExternalLaunchResult {
    val url = selectionBrowserUrl(text, template) ?: return SelectionExternalLaunchResult.NO_HANDLER
    return if (startExternalActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))) {
        SelectionExternalLaunchResult.OPENED
    } else {
        SelectionExternalLaunchResult.NO_HANDLER
    }
}

/**
 * 系统词典通道：优先把文字交给其它应用的文本处理动作（ACTION_PROCESS_TEXT），
 * 没有处理器时回退到用户配置的在线词典 URL。
 *
 * 三段式判定，每一段都给出确定结果：
 * 1. `queryIntentActivities` 命中 → 弹系统选择器，用户能看到自己装了哪些文字工具；
 * 2. 查询为空 → 仍尝试**直接启动**：`startActivity` 的隐式解析不受
 *    `queryIntentActivities` 的包可见性过滤影响，兜住「装了词典却被判无处理器」的假阴性；
 * 3. 两段都失败 → 回退在线词典，返回 [SelectionExternalLaunchResult.OPENED_WEB_FALLBACK]，
 *    由调用方明确告知用户这是一次**外部跳转**。
 *
 * 任何一步都不写正文、不落库、不申请额外权限：只把用户显式选中的文字交给外部应用。
 */
internal fun Context.openSelectionInDictionary(
    text: String,
    template: String = SelectionActions.DEFAULT_DICTIONARY_TEMPLATE,
): SelectionExternalLaunchResult {
    val query = normalizedSelectionQuery(text, DICTIONARY_QUERY_LIMIT)
    if (query.isBlank()) return SelectionExternalLaunchResult.NO_HANDLER

    val processText = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_PROCESS_TEXT, query)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    }

    val handlers = runCatching { packageManager.queryIntentActivities(processText, 0) }
        .getOrDefault(emptyList())
    if (handlers.isNotEmpty() &&
        startExternalActivity(Intent.createChooser(processText, PROCESS_TEXT_CHOOSER_TITLE))
    ) {
        return SelectionExternalLaunchResult.OPENED
    }
    if (startExternalActivity(processText)) return SelectionExternalLaunchResult.OPENED

    val fallbackUrl = selectionDictionaryUrl(query, template)
        ?: return SelectionExternalLaunchResult.NO_HANDLER
    return if (startExternalActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)))) {
        SelectionExternalLaunchResult.OPENED_WEB_FALLBACK
    } else {
        SelectionExternalLaunchResult.NO_HANDLER
    }
}

/** 在线词典通道：直接按模板打开浏览器，不做 ACTION_PROCESS_TEXT 探测。 */
internal fun Context.openSelectionInOnlineDictionary(
    text: String,
    template: String = SelectionActions.DEFAULT_DICTIONARY_TEMPLATE,
): SelectionExternalLaunchResult {
    val url = selectionDictionaryUrl(text, template) ?: return SelectionExternalLaunchResult.NO_HANDLER
    return if (startExternalActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))) {
        SelectionExternalLaunchResult.OPENED_WEB_FALLBACK
    } else {
        SelectionExternalLaunchResult.NO_HANDLER
    }
}

/**
 * 启动外部 Activity；**任何**解析/权限/厂商定制失败都收敛为 `false`，不向上抛。
 *
 * 这里只捕获 [Exception] 而非 `Throwable`：`ActivityNotFoundException`、
 * `SecurityException`（目标被策略禁用）与 OEM 定制的 `IllegalArgumentException`
 * 都属于「没有可用处理器」，可以降级；而 `OutOfMemoryError` 这类 Error 不该被静默吞掉。
 */
private fun Context.startExternalActivity(intent: Intent): Boolean {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: Exception) {
        Log.w(TAG, "外部 Intent 启动失败：${intent.action}", e)
        false
    }
}

/**
 * 选区浏览器查询调度器（包含空选区拦截、跳转前轻量提示与无 handler 明确失败提示）。
 */
internal fun executeSelectionBrowser(
    selectedText: String,
    template: String,
    showNotice: (String) -> Unit,
    launcher: (text: String, template: String) -> SelectionExternalLaunchResult,
) {
    val blank = blankSelectionNotice(selectedText)
    if (blank != null) {
        showNotice(blank)
        return
    }
    showNotice(selectionExternalPreJumpNotice(ExternalActionKind.BROWSER))
    if (launcher(selectedText, template) == SelectionExternalLaunchResult.NO_HANDLER) {
        showNotice(selectionExternalFailureNotice(ExternalActionKind.BROWSER))
    }
}

internal fun Context.executeSelectionBrowser(
    selectedText: String,
    template: String,
    showNotice: (String) -> Unit,
) = executeSelectionBrowser(
    selectedText = selectedText,
    template = template,
    showNotice = showNotice,
    launcher = { text, tmpl -> openSelectionInBrowser(text, tmpl) },
)

/**
 * 选区词典查询调度器（离线/系统/在线三模式调度，包含空选区拦截、跳转前轻量提示与回退/失败明确提示）。
 */
internal fun executeSelectionDictionary(
    selectedText: String,
    mode: String,
    template: String,
    onOpenOffline: (String) -> Unit,
    showNotice: (String) -> Unit,
    systemLauncher: (text: String, template: String) -> SelectionExternalLaunchResult,
    onlineLauncher: (text: String, template: String) -> SelectionExternalLaunchResult,
) {
    when (mode) {
        SelectionActions.MODE_OFFLINE -> onOpenOffline(selectedText)
        SelectionActions.MODE_SYSTEM -> {
            val blank = blankSelectionNotice(selectedText)
            if (blank != null) {
                showNotice(blank)
                return
            }
            showNotice(selectionExternalPreJumpNotice(ExternalActionKind.SYSTEM_DICTIONARY))
            when (systemLauncher(selectedText, template)) {
                SelectionExternalLaunchResult.OPENED -> Unit
                SelectionExternalLaunchResult.OPENED_WEB_FALLBACK -> showNotice(NOTICE_DICT_FALLBACK)
                SelectionExternalLaunchResult.NO_HANDLER ->
                    showNotice(selectionExternalFailureNotice(ExternalActionKind.SYSTEM_DICTIONARY))
            }
        }
        else -> {
            val blank = blankSelectionNotice(selectedText)
            if (blank != null) {
                showNotice(blank)
                return
            }
            showNotice(selectionExternalPreJumpNotice(ExternalActionKind.ONLINE_DICTIONARY))
            when (onlineLauncher(selectedText, template)) {
                SelectionExternalLaunchResult.NO_HANDLER ->
                    showNotice(selectionExternalFailureNotice(ExternalActionKind.ONLINE_DICTIONARY))
                else -> Unit
            }
        }
    }
}

internal fun Context.executeSelectionDictionary(
    selectedText: String,
    mode: String,
    template: String,
    onOpenOffline: (String) -> Unit,
    showNotice: (String) -> Unit,
) = executeSelectionDictionary(
    selectedText = selectedText,
    mode = mode,
    template = template,
    onOpenOffline = onOpenOffline,
    showNotice = showNotice,
    systemLauncher = { text, tmpl -> openSelectionInDictionary(text, tmpl) },
    onlineLauncher = { text, tmpl -> openSelectionInOnlineDictionary(text, tmpl) },
)
