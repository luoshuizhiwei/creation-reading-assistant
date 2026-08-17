package com.creationreadingassistant.feature.reader.pager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.settings.HeaderFooterItem

/** 页眉/页脚渲染所需的分页快照 */
internal data class PageInfo(
    val chapterIndex: Int = 0,
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val progressPercent: Float = 0f,
)

/**
 * 任务 #15 结构拆分：从 [PagedReaderHost] 抽出的页眉行。
 * 渲染体逐字搬运，快照值（锚点/分页信息/时间/电量）由调用方在组合期读取后传入。
 */
@Composable
internal fun PagedReaderHeader(
    showReaderInfo: Boolean,
    headerLeft: HeaderFooterItem,
    headerRight: HeaderFooterItem,
    source: PagedChapterSource,
    anchorValue: Int,
    pageInfo: PageInfo,
    currentTime: String,
    batteryLevel: Int,
    bookName: String,
    textColor: Color,
) {
    // 页眉
    if (showReaderInfo && (headerLeft != HeaderFooterItem.NONE || headerRight != HeaderFooterItem.NONE)) {
        Row(
            Modifier.fillMaxWidth().height(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val headerColor = textColor.copy(alpha = 0.45f)
            Text(
                resolveItemText(headerLeft, source, anchorValue, pageInfo, currentTime, batteryLevel, bookName),
                fontSize = 11.sp,
                color = headerColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                resolveItemText(headerRight, source, anchorValue, pageInfo, currentTime, batteryLevel, bookName),
                fontSize = 11.sp,
                color = headerColor,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/**
 * 任务 #15 结构拆分：从 [PagedReaderHost] 抽出的页脚行。
 * 渲染体逐字搬运；安静阅读信息关闭时只留空隙保持正文位置稳定。
 */
@Composable
internal fun PagedReaderFooter(
    showReaderInfo: Boolean,
    footerLeft: HeaderFooterItem,
    footerRight: HeaderFooterItem,
    source: PagedChapterSource,
    anchorValue: Int,
    pageInfo: PageInfo,
    currentTime: String,
    batteryLevel: Int,
    bookName: String,
    textColor: Color,
) {
    // 页脚：可配置内容。安静阅读信息关闭时只留空隙保持正文位置稳定。
    Row(
        Modifier.fillMaxWidth().height(28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val footerColor = textColor.copy(alpha = 0.45f)
        if (showReaderInfo) {
            Text(
                resolveItemText(footerLeft, source, anchorValue, pageInfo, currentTime, batteryLevel, bookName),
                fontSize = 11.sp,
                color = footerColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                resolveItemText(footerRight, source, anchorValue, pageInfo, currentTime, batteryLevel, bookName),
                fontSize = 11.sp,
                color = footerColor,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** 根据配置条目解析出对应的显示文本 */
private fun resolveItemText(
    item: HeaderFooterItem,
    source: PagedChapterSource,
    absOffset: Int,
    pageInfo: PageInfo,
    currentTime: String,
    batteryLevel: Int,
    bookName: String,
): String = when (item) {
    HeaderFooterItem.NONE -> ""
    HeaderFooterItem.CHAPTER_TITLE -> {
        if (source.chapterCount > 0) source.chapterTitle(source.chapterIndexFor(absOffset)) else ""
    }
    HeaderFooterItem.BOOK_NAME -> bookName
    HeaderFooterItem.TIME -> currentTime
    HeaderFooterItem.BATTERY -> if (batteryLevel >= 0) "$batteryLevel%" else ""
    HeaderFooterItem.PAGE_NUMBER -> if (pageInfo.pageCount > 0) "${pageInfo.pageIndex + 1}/${pageInfo.pageCount}" else ""
    HeaderFooterItem.PROGRESS -> {
        val total = source.totalChars
        val percent = if (total <= 0) 0f else (absOffset * 100f / total).coerceIn(0f, 100f)
        "%.1f%%".format(percent)
    }
}
