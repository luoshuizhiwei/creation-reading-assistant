package com.creationreadingassistant.acceptance

/**
 * 94-v14：导出 Markdown 中“本条精确灵感的标签行”解析与精确集合比较。
 *
 * 背景：v13 的断言 `exportedText.contains("标签：$tagName")` 错误地要求目标标签排在行首；
 * 实际导出按持久化顺序用“、”连接多个标签（书名标签、阅读灵感、目标标签），目标标签可以排在
 * 任意位置。简单改成 `contains(tagName)` 又会漏掉“缺标签/重复/相似前缀/来自别的条目”等问题。
 *
 * 本对象只做纯文本处理，不依赖 Android，因此同一份源码可在宿主上直接编译执行做离线验证。
 * 判定规则：
 *  - 先按精确条目标题（如 `### T94A-<runId>`）定位唯一小节，标签行必须属于该小节；
 *  - 小节内必须恰好一行以“标签：”开头，值以“、”分隔且不含空值；
 *  - 与该精确行持久化的 tags 做多重集合比较（顺序无关、值逐字符相等、数量一致）。
 */
internal object ExportTagLine {

    private const val PREFIX = "标签："
    private const val SEPARATOR = '、'

    data class Parsed(val heading: String, val line: String, val values: List<String>)

    /** 解析或比较失败时抛出，消息包含实际文本，便于宿主保留原始证据。 */
    class FormatException(message: String) : AssertionError(message)

    /** 目标条目小节的正文行范围；标题必须全文唯一。 */
    private fun sectionRange(exportedText: String, heading: String): Pair<List<String>, IntRange> {
        val lines = exportedText.split('\n').map { it.trimEnd('\r') }
        val wanted = heading.trim()
        val headingIndexes = lines.indices.filter { lines[it].trim() == wanted }
        if (headingIndexes.size != 1) {
            throw FormatException("导出中目标条目标题必须唯一：$wanted（实得 ${headingIndexes.size} 处）")
        }
        val start = headingIndexes.first() + 1
        var end = lines.size
        for (index in start until lines.size) {
            val line = lines[index]
            if (line.startsWith("## ") || line.startsWith("### ")) {
                end = index
                break
            }
        }
        return lines to (start until end)
    }

    /** 只返回目标条目小节正文；个人想法/来源/摘录断言必须限定在此范围内。 */
    fun sectionText(exportedText: String, heading: String): String {
        val (lines, range) = sectionRange(exportedText, heading)
        return range.map { lines[it] }.joinToString("\n")
    }

    fun parse(exportedText: String, heading: String): Parsed {
        val (lines, range) = sectionRange(exportedText, heading)
        val wanted = heading.trim()
        val start = range.first
        val end = range.last + 1
        val tagLineIndexes = (start until end).filter { lines[it].startsWith(PREFIX) }
        if (tagLineIndexes.size != 1) {
            throw FormatException("目标条目小节必须恰好一行标签行：$wanted（实得 ${tagLineIndexes.size} 行）")
        }
        val line = lines[tagLineIndexes.first()]
        val remainder = line.removePrefix(PREFIX)
        if (remainder.isEmpty()) {
            throw FormatException("目标条目标签行不得为空：$wanted")
        }
        val values = remainder.split(SEPARATOR)
        if (values.any { it.isEmpty() }) {
            throw FormatException("标签行不得包含空标签：$line")
        }
        return Parsed(wanted, line, values)
    }

    /**
     * 与精确行持久化的 tags 比较。顺序无关，但值必须逐字符相等且数量一致；
     * 多重集合比较可同时暴露缺失、重复、相似前缀和串行（取到别的条目）。
     */
    fun assertMatchesPersisted(parsed: Parsed, persistedTags: List<String>) {
        val actual = parsed.values
        if (actual.size != persistedTags.size) {
            throw AssertionError(
                "标签数量必须与该精确行持久化一致：expected=${persistedTags.size} actual=${actual.size}；line=${parsed.line}",
            )
        }
        if (actual.sorted() != persistedTags.sorted()) {
            throw AssertionError(
                "标签多重集合必须与该精确行持久化一致（顺序无关、不可子串、不可重复）：" +
                    "expected=${persistedTags.sorted()} actual=${actual.sorted()}；line=${parsed.line}",
            )
        }
    }
}
