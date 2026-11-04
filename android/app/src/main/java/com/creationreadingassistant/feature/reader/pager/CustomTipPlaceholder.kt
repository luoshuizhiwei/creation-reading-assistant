package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.data.settings.HeaderFooterItem

/**
 * 内置自定义页眉/页脚模板占位符（对标 Legado MD3 的 CustomTipPlaceholder）。
 *
 * - [token]: 完整形式（含花括号），如 "{BookName}"。
 * - [key]: 占位符纯键名，如 "BookName"。
 * - [label]: 界面 Chip 标签展示的中文名。
 */
enum class CustomTipPlaceholder(
    val token: String,
    val key: String,
    val label: String,
) {
    BOOK_NAME("{BookName}", "BookName", "书名"),
    CHAPTER_TITLE("{ChapterTitle}", "ChapterTitle", "章节名"),
    TIME("{Time}", "Time", "当前时间"),
    BATTERY_PERCENT("{BatteryPercent}", "BatteryPercent", "电量"),
    CHAPTER_INDEX("{ChapterIndex}", "ChapterIndex", "本章序"),
    CHAPTER_SIZE("{ChapterSize}", "ChapterSize", "总章数"),
    PAGE_INDEX("{PageIndex}", "PageIndex", "当前页"),
    PAGE_SIZE("{PageSize}", "PageSize", "本章总页"),
    PAGE_REMAINING("{PageRemaining}", "PageRemaining", "本章余页"),
    READ_PROGRESS("{ReadProgress}", "ReadProgress", "总进度%");

    companion object {
        private val validKeys: Set<String> = entries.mapTo(hashSetOf()) { it.key }
        private val byKey: Map<String, CustomTipPlaceholder> = entries.associateBy { it.key }

        private val placeholderRegex = Regex("\\{([^{}]+)\\}")

        /**
         * 从模板中提取所有 `{Xxx}` 内部的 key。
         */
        fun extractPlaceholders(template: String): List<String> {
            if (template.isEmpty()) return emptyList()
            return placeholderRegex.findAll(template).map { it.groupValues[1] }.toList()
        }

        /**
         * 校验模板中的所有 `{Xxx}` 是否均属于有效占位符。
         */
        fun isValid(template: String): Boolean {
            return extractPlaceholders(template).all { it in validKeys }
        }

        /**
         * 把模板翻成人话，供设置页折叠行回显：`{ChapterTitle} · {Time}` → `章节名 · 当前时间`。
         *
         * 无法识别的占位符**原样保留**——回显不该吞掉用户写下的内容，
         * 校验与拒绝是 [isValid] 的职责，不是这里的。
         */
        fun describeTemplate(template: String): String {
            if (template.isEmpty()) return template
            return placeholderRegex.replace(template) { match ->
                byKey[match.groupValues[1]]?.label ?: match.value
            }
        }

        /**
         * 向后兼容：将旧版 HeaderFooterItem 枚举无缝转换为模板字符串。
         */
        fun fromHeaderFooterItem(item: HeaderFooterItem): String = when (item) {
            HeaderFooterItem.NONE -> ""
            HeaderFooterItem.CHAPTER_TITLE -> "{ChapterTitle}"
            HeaderFooterItem.BOOK_NAME -> "{BookName}"
            HeaderFooterItem.TIME -> "{Time}"
            HeaderFooterItem.BATTERY -> "{BatteryPercent}"
            HeaderFooterItem.PAGE_NUMBER -> "{PageIndex}/{PageSize}"
            HeaderFooterItem.PROGRESS -> "{ReadProgress}"
        }

        /**
         * 渲染自定义页眉/页脚模板。
         */
        internal fun render(
            template: String,
            source: PagedChapterSource?,
            absOffset: Int,
            pageInfo: PageInfo,
            currentTime: String,
            batteryLevel: Int,
            bookName: String,
        ): String {
            if (template.isEmpty()) return ""
            var result = template

            // 1. 书名
            if (result.contains("{BookName}")) {
                result = result.replace("{BookName}", bookName)
            }

            // 2. 章节名
            if (result.contains("{ChapterTitle}")) {
                val title = if (source != null && source.chapterCount > 0) {
                    source.chapterTitle(source.chapterIndexFor(absOffset))
                } else ""
                result = result.replace("{ChapterTitle}", title)
            }

            // 3. 时间
            if (result.contains("{Time}")) {
                result = result.replace("{Time}", currentTime)
            }

            // 4. 电量
            if (result.contains("{BatteryPercent}")) {
                val batteryText = if (batteryLevel >= 0) "$batteryLevel%" else ""
                result = result.replace("{BatteryPercent}", batteryText)
            }

            // 5. 章节序号
            if (result.contains("{ChapterIndex}")) {
                val idx = if (source != null && source.chapterCount > 0) {
                    (source.chapterIndexFor(absOffset) + 1).toString()
                } else "1"
                result = result.replace("{ChapterIndex}", idx)
            }

            // 6. 总章节数
            if (result.contains("{ChapterSize}")) {
                val count = source?.chapterCount ?: 0
                result = result.replace("{ChapterSize}", count.toString())
            }

            // 7. 当前页码 (1-based)
            if (result.contains("{PageIndex}")) {
                val pIndex = if (pageInfo.pageCount > 0) (pageInfo.pageIndex + 1).toString() else ""
                result = result.replace("{PageIndex}", pIndex)
            }

            // 8. 本章总页数
            if (result.contains("{PageSize}")) {
                val pCount = if (pageInfo.pageCount > 0) pageInfo.pageCount.toString() else ""
                result = result.replace("{PageSize}", pCount)
            }

            // 9. 本章剩余页数
            if (result.contains("{PageRemaining}")) {
                val remaining = if (pageInfo.pageCount > 0) {
                    (pageInfo.pageCount - (pageInfo.pageIndex + 1)).coerceAtLeast(0).toString()
                } else "0"
                result = result.replace("{PageRemaining}", remaining)
            }

            // 10. 总进度%
            if (result.contains("{ReadProgress}")) {
                // 任务90-v1：EPUB 的 absOffset 是估算章长的混合坐标，而分页控制器已把
                // 校准后的百分比放进 PageInfo。页脚直接消费同一口径，避免与落库/书架
                // 百分比分叉；TXT/MD 章节长度非估算，保留原有显示计算逐字不变。
                val percent = if (source?.chapterLengthsAreEstimated == true) {
                    pageInfo.progressPercent.coerceIn(0f, 100f)
                } else {
                    val total = source?.totalChars ?: 0
                    if (total <= 0) 0f else (absOffset * 100f / total).coerceIn(0f, 100f)
                }
                result = result.replace("{ReadProgress}", "%.1f%%".format(percent))
            }

            return result
        }

        /**
         * F4: 使用中性样例数据（符合阅读器规范）快速求值模板，用于设置弹窗或预览条的实时联动。
         */
        fun renderSample(template: String): String {
            if (template.isBlank()) return ""
            var result = template
            result = result.replace("{BookName}", "测试书籍")
            result = result.replace("{ChapterTitle}", "第3章 序幕")
            result = result.replace("{Time}", "12:30")
            result = result.replace("{BatteryPercent}", "85%")
            result = result.replace("{ChapterIndex}", "3")
            result = result.replace("{ChapterSize}", "120")
            result = result.replace("{PageIndex}", "1")
            result = result.replace("{PageSize}", "18")
            result = result.replace("{PageRemaining}", "17")
            result = result.replace("{ReadProgress}", "24.5%")
            return result
        }
    }
}
