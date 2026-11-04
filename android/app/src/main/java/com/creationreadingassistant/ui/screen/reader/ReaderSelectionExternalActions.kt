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
internal const val NOTICE_BROWSER_OPENED = "已打开浏览器"
internal const val NOTICE_BROWSER_UNAVAILABLE = "未找到可用浏览器"
internal const val NOTICE_SYSTEM_DICT_OPENED = "已调用系统文字工具"
internal const val NOTICE_ONLINE_DICT_OPENED = "已打开在线词典"
internal const val NOTICE_DICT_FALLBACK = "未找到词典应用，已用在线词典查询"
internal const val NOTICE_DICT_OR_BROWSER_UNAVAILABLE = "未找到可用词典或浏览器"
internal const val NOTICE_ONLINE_DICT_UNAVAILABLE = "无法打开在线词典，请确认系统浏览器可用"

/**
 * 浏览器通道的**最终结果**文案（S07-F4.1）。
 *
 * 旧实现先无条件提示「正在打开浏览器…」，拿到 `NO_HANDLER` 后再补一条失败 —— 用户在
 * 一次什么都没发生的操作里连收两条互相矛盾的提示。现在提示只在结果确定后产生一条：
 * 成功就是成功，失败就是失败，不存在「预报成功」的中间态。
 */
internal fun selectionBrowserResultNotice(result: SelectionExternalLaunchResult): String =
    when (result) {
        SelectionExternalLaunchResult.OPENED,
        SelectionExternalLaunchResult.OPENED_WEB_FALLBACK,
        -> NOTICE_BROWSER_OPENED

        SelectionExternalLaunchResult.NO_HANDLER -> NOTICE_BROWSER_UNAVAILABLE
    }

/**
 * 系统词典通道的最终结果文案：三段式判定的每一段都有**各自准确**的说法。
 *
 * 旧实现把「完全不可用」写成「未找到可用词典或浏览器」尚可接受，但同一套失败文案
 * 也被在线词典通道复用 —— 后者会让用户去排查浏览器，而真正失败的是在线词典这一步。
 */
internal fun selectionSystemDictionaryResultNotice(
    result: SelectionExternalLaunchResult,
): String = when (result) {
    SelectionExternalLaunchResult.OPENED -> NOTICE_SYSTEM_DICT_OPENED
    SelectionExternalLaunchResult.OPENED_WEB_FALLBACK -> NOTICE_DICT_FALLBACK
    SelectionExternalLaunchResult.NO_HANDLER -> NOTICE_DICT_OR_BROWSER_UNAVAILABLE
}

/** 在线词典通道的最终结果文案：失败必须说「在线词典打不开」，不得借用浏览器文案。 */
internal fun selectionOnlineDictionaryResultNotice(
    result: SelectionExternalLaunchResult,
): String = when (result) {
    SelectionExternalLaunchResult.OPENED,
    SelectionExternalLaunchResult.OPENED_WEB_FALLBACK,
    -> NOTICE_ONLINE_DICT_OPENED

    SelectionExternalLaunchResult.NO_HANDLER -> NOTICE_ONLINE_DICT_UNAVAILABLE
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
    return if (openHttpUrlWithBrowserChoice(url)) {
        SelectionExternalLaunchResult.OPENED
    } else {
        SelectionExternalLaunchResult.NO_HANDLER
    }
}

/**
 * 以「可反悔」的方式打开 http(s) 链接。
 *
 * 为什么不能直接 `startActivity(ACTION_VIEW)`：部分国产 ROM（Mate70/HarmonyOS 实测）
 * 会把 https 链接**静默解析**给系统「搜应用」，用户装了浏览器也轮不到弹选择框。
 * `createChooser` 由系统自己枚举全部可解析应用（不受本包 `<queries>` 可见性过滤），
 * 把选择权还给用户；个别 ROM 上 chooser 起不来时退回裸 ACTION_VIEW 兜底。
 */
internal fun Context.openHttpUrlWithBrowserChoice(url: String): Boolean =
    startExternalActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "选择浏览器")) ||
        startExternalActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

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
    return if (openHttpUrlWithBrowserChoice(fallbackUrl)) {
        SelectionExternalLaunchResult.OPENED_WEB_FALLBACK
    } else {
        SelectionExternalLaunchResult.NO_HANDLER
    }
}

/** 在线词典通道：按模板打开网页词典，浏览器选择方式与「浏览器」入口一致。 */
internal fun Context.openSelectionInOnlineDictionary(
    text: String,
    template: String = SelectionActions.DEFAULT_DICTIONARY_TEMPLATE,
): SelectionExternalLaunchResult {
    val url = selectionDictionaryUrl(text, template) ?: return SelectionExternalLaunchResult.NO_HANDLER
    return if (openHttpUrlWithBrowserChoice(url)) {
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
 * 选区浏览器查询调度器（S07-F4.1：空选区拦截 + **结果确定后单条**提示）。
 *
 * 先启动再提示的旧形态会在 `NO_HANDLER` 时连发「正在打开浏览器…」和失败两条互相
 * 矛盾的提示；现在 launcher 调用的结果决定**唯一**一条文案，绝不在结果未知时预报成功。
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
    showNotice(selectionBrowserResultNotice(launcher(selectedText, template)))
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
 * 选区词典查询调度器（离线/系统/在线三模式调度；S07-F4.1：提示只在结果确定后发一条）。
 *
 * 三种模式各自的结果文案由 [selectionSystemDictionaryResultNotice] /
 * [selectionOnlineDictionaryResultNotice] 决定：系统词典回退到在线词典要说清是回退，
 * 在线词典失败不得借用浏览器文案。离线模式直接交给面板，不产生提示。
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
            showNotice(selectionSystemDictionaryResultNotice(systemLauncher(selectedText, template)))
        }
        else -> {
            val blank = blankSelectionNotice(selectedText)
            if (blank != null) {
                showNotice(blank)
                return
            }
            showNotice(selectionOnlineDictionaryResultNotice(onlineLauncher(selectedText, template)))
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
