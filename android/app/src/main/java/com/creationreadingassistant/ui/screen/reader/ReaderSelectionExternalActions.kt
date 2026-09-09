package com.creationreadingassistant.ui.screen.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal enum class SelectionExternalLaunchResult {
    OPENED,
    OPENED_WEB_FALLBACK,
    NO_HANDLER,
}

private const val WEB_QUERY_LIMIT = 500
private const val DICTIONARY_QUERY_LIMIT = 120

/**
 * External lookups are explicit user actions. Keep the query bounded so a very long selection does
 * not produce an invalid URL or hand an unexpectedly large payload to another application.
 */
internal fun normalizedSelectionQuery(text: String, maxChars: Int): String =
    text.trim()
        .replace(Regex("\\s+"), " ")
        .take(maxChars)

internal fun selectionBrowserUrl(text: String): String? =
    encodedSelectionQuery(text, WEB_QUERY_LIMIT)?.let { "https://www.bing.com/search?q=$it" }

internal fun selectionDictionaryUrl(text: String): String? =
    encodedSelectionQuery(text, DICTIONARY_QUERY_LIMIT)?.let {
        "https://dict.youdao.com/result?word=$it&lang=auto"
    }

private fun encodedSelectionQuery(text: String, maxChars: Int): String? {
    val query = normalizedSelectionQuery(text, maxChars)
    if (query.isBlank()) return null
    return URLEncoder.encode(query, StandardCharsets.UTF_8.name())
}

internal fun Context.openSelectionInBrowser(text: String): SelectionExternalLaunchResult {
    val url = selectionBrowserUrl(text) ?: return SelectionExternalLaunchResult.NO_HANDLER
    return if (startExternalActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))) {
        SelectionExternalLaunchResult.OPENED
    } else {
        SelectionExternalLaunchResult.NO_HANDLER
    }
}

internal fun Context.openSelectionInDictionary(text: String): SelectionExternalLaunchResult {
    val query = normalizedSelectionQuery(text, DICTIONARY_QUERY_LIMIT)
    if (query.isBlank()) return SelectionExternalLaunchResult.NO_HANDLER

    val processText = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_PROCESS_TEXT, query)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    }
    val handlers = packageManager.queryIntentActivities(processText, 0)
    if (handlers.isNotEmpty()) {
        val chooser = Intent.createChooser(processText, "选择词典或文本工具")
        if (startExternalActivity(chooser)) return SelectionExternalLaunchResult.OPENED
    }

    val fallbackUrl = selectionDictionaryUrl(query)
        ?: return SelectionExternalLaunchResult.NO_HANDLER
    return if (startExternalActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)))) {
        SelectionExternalLaunchResult.OPENED_WEB_FALLBACK
    } else {
        SelectionExternalLaunchResult.NO_HANDLER
    }
}

private fun Context.startExternalActivity(intent: Intent): Boolean = runCatching {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}.isSuccess
