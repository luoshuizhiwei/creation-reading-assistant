package com.creationreadingassistant.feature.reader.layout

/**
 * 当前页的可访问性正文（P1：TalkBack 按页粒度朗读）。
 *
 * 与 [com.creationreadingassistant.feature.reader.pager.PagedTxtReaderHost] 中
 * PageCanvas 的绘制切片共用同一套换算：每行取 `lineParaOffsets[li]` 定位段首，
 * 再用 `clusterStarts` 切章文本。**语义文本与视觉文本由此天然一致**，且本函数
 * 是纯 JVM 函数（本包禁止 `import android.*`），可在单测里毫秒级验证。
 *
 * 粒度约定：
 * - 一页一个字符串，行间以 `\n` 分隔（TalkBack 的行粒度导航可用）；
 * - 不把整章/全书拼进去，长度以页为界；
 * - 列表标记只在段落首行出现一次，续行不重复；
 * - 分隔线（HORIZONTAL_RULE）只画线不产生文本，这里同样跳过；
 * - 空白页返回空串，由调用方决定提示文案。
 */
fun pageAccessibleText(page: ChapterPaginator.Page, chapterText: String): String {
    if (page.lines.isEmpty()) return ""
    val sb = StringBuilder()
    page.lines.forEachIndexed { li, line ->
        if (line.role == BlockRole.HORIZONTAL_RULE) return@forEachIndexed
        val paraOff = page.lineParaOffsets.getOrElse(li) { 0 }
        val start = paraOff + line.clusterStarts.getOrElse(0) { line.startInText }
        val end = paraOff + line.clusterStarts.getOrElse(line.clusterCount) { line.endInText }
        if (end <= start) return@forEachIndexed
        if (sb.isNotEmpty()) sb.append('\n')
        if (line.isParagraphStart) line.listMarker?.let { sb.append(it) }
        sb.append(chapterText, start, end)
    }
    return sb.toString()
}
