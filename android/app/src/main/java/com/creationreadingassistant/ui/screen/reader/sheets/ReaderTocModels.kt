package com.creationreadingassistant.ui.screen.reader.sheets

import com.creationreadingassistant.ui.viewmodel.TxtRuleScanStatus

internal enum class ReaderTocTab(val label: String) {
    TOC("目录"),
    BOOKMARKS("书签"),
    NOTES("笔记"),
}

/**
 * 规则 pill 后缀：扫描中 →「扫描中…」，完成 →「N章」，取消/失败 → 可解释文案；
 * 其他规则回退到预览章数；无预览时不显示「0章」（大型流式 TXT 未扫描前的错误展示）。
 */
internal fun txtRulePillSuffix(
    status: TxtRuleScanStatus?,
    ruleId: String,
    previewCount: Int,
): String? = when (status) {
    is TxtRuleScanStatus.Running ->
        if (status.ruleId == ruleId) "扫描中…" else previewSuffix(previewCount)
    is TxtRuleScanStatus.Completed ->
        if (status.ruleId == ruleId) "${status.chapterCount}章" else previewSuffix(previewCount)
    is TxtRuleScanStatus.Cancelled ->
        if (status.ruleId == ruleId) "已取消" else previewSuffix(previewCount)
    is TxtRuleScanStatus.Failed ->
        if (status.ruleId == ruleId) "扫描失败" else previewSuffix(previewCount)
    TxtRuleScanStatus.Idle, null -> previewSuffix(previewCount)
}

private fun previewSuffix(previewCount: Int): String? =
    if (previewCount > 0) "${previewCount}章" else null

internal data class ReaderTocEntry(
    val index: Int,
    val title: String,
    val volume: String,
    val isCurrent: Boolean,
    val isRecent: Boolean,
    val isRead: Boolean = false,
    val wordCountLabel: String? = null,
)

internal fun readerTocEntries(
    titles: List<String>,
    current: Int,
    recent: List<Int>,
    read: Set<Int> = emptySet(),
    wordCountLabels: List<String> = emptyList(),
): List<ReaderTocEntry> {
    var volume = "正文"
    return titles.mapIndexedNotNull { index, title ->
        if (isVolumeHeader(title)) {
            volume = title
            null
        } else {
            ReaderTocEntry(
                index = index,
                title = title.ifBlank { "未命名章节" },
                volume = volume,
                isCurrent = index == current,
                isRecent = index in recent,
                isRead = index in read,
                wordCountLabel = wordCountLabels.getOrNull(index),
            )
        }
    }
}

/** 按「卷/部」标题聚合章节；保留给单元测试和兼容调用。 */
internal fun groupChaptersByVolume(titles: List<String>): List<Pair<String, List<Int>>> {
    val result = mutableListOf<Pair<String, MutableList<Int>>>()
    for ((i, title) in titles.withIndex()) {
        if (isVolumeHeader(title)) result.add(title to mutableListOf())
        else {
            if (result.isEmpty()) result.add("正文" to mutableListOf())
            result.last().second.add(i)
        }
    }
    return result
}

internal fun isVolumeHeader(title: String): Boolean {
    if (title.isBlank()) return false
    return title.contains("卷") || title.contains("部") ||
        title.contains("Part", ignoreCase = true) || title.contains("Volume", ignoreCase = true) ||
        Regex("^第[一二三四五六七八九十\\d]+[卷部]").containsMatchIn(title)
}
