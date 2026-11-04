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

/** 卷标里的编号：阿拉伯、全角、汉字大小写都有人用。 */
private const val VOL_NUM = "[0-9０-９零〇一二三四五六七八九十百千两]"

/** 卷标形态：整行是一则卷标，允许再跟一段不超过 10 字的副标题。 */
private val VOLUME_HEADER = Regex(
    "^(?:第$VOL_NUM{1,6}[卷部]|卷之?$VOL_NUM{1,6}|[上中下][卷部])" +
        "(?:[ \\t　:：，,、—·-].{0,10})?\$" +
        "|^(?:vol|volume|part)[ .．、:：_-]*[0-9ivxlcdm]{1,8}\$",
    RegexOption.IGNORE_CASE,
)

/** 卷标不可能长到这个数；先按长度挡一道，省掉逐标题的正则开销。 */
private const val VOLUME_HEADER_MAX_LENGTH = 24

/**
 * 整行**就是**一则卷标，才算分卷。
 *
 * 旧实现写作 `contains("卷") || contains("部")`，是子串判据：于是「举报试卷怎么样呢」
 * 「她没去拳击部」「系统的部分规则」「长谷部同学来了」（姓氏里的「部」）这类正文标题
 * 都会被判成卷名，而 [readerTocEntries] 对卷名返回 null——等于把这些章节从目录里整条
 * 删掉，并把后续章节挂到一个错误的分组名下。
 */
internal fun isVolumeHeader(title: String): Boolean {
    val t = title.trim()
    if (t.isEmpty() || t.length > VOLUME_HEADER_MAX_LENGTH) return false
    return VOLUME_HEADER.matches(t)
}
