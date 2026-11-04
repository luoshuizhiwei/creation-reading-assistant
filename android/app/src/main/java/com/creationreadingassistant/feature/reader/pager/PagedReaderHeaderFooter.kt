package com.creationreadingassistant.feature.reader.pager

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.data.settings.BatteryTipStyle
import com.creationreadingassistant.data.settings.HeaderFooterItem
import com.creationreadingassistant.ui.screen.reader.pagedReaderFooterHeightDp
import com.creationreadingassistant.ui.screen.reader.pagedReaderHeaderHeightDp

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
    modifier: Modifier = Modifier,
    customTipHeaderLeft: String = "",
    customTipHeaderCenter: String = "",
    customTipHeaderRight: String = "",
    batteryTipStyle: BatteryTipStyle = BatteryTipStyle.ICON_ONLY,
) {
    val templateLeft = customTipHeaderLeft.ifBlank { CustomTipPlaceholder.fromHeaderFooterItem(headerLeft) }
    val templateRight = customTipHeaderRight.ifBlank { CustomTipPlaceholder.fromHeaderFooterItem(headerRight) }
    if (showReaderInfo &&
        (templateLeft.isNotEmpty() || customTipHeaderCenter.isNotEmpty() || templateRight.isNotEmpty())
    ) {
        // 0.45 透明度在白纸下对比度仅 ~1.8:1，页眉页脚几乎看不清；提到 0.65（~2.4:1）
        // 保持弱化辅助信息的同时保证可读。
        TipRow(
            leftTemplate = templateLeft,
            centerTemplate = customTipHeaderCenter,
            rightTemplate = templateRight,
            source = source,
            absOffset = anchorValue,
            pageInfo = pageInfo,
            currentTime = currentTime,
            batteryLevel = batteryLevel,
            bookName = bookName,
            color = textColor.copy(alpha = 0.65f),
            batteryTipStyle = batteryTipStyle,
            modifier = modifier.height(pagedReaderHeaderHeightDp(LocalDensity.current.fontScale).dp),
        )
    }
}

/**
 * 任务 #15 结构拆分：从 [PagedReaderHost] 抽出的页脚行。
 * 安静阅读信息关闭时只留空隙保持正文位置稳定。
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
    modifier: Modifier = Modifier,
    customTipFooterLeft: String = "",
    customTipFooterCenter: String = "",
    customTipFooterRight: String = "",
    batteryTipStyle: BatteryTipStyle = BatteryTipStyle.ICON_ONLY,
) {
    val templateLeft = customTipFooterLeft.ifBlank { CustomTipPlaceholder.fromHeaderFooterItem(footerLeft) }
    val templateRight = customTipFooterRight.ifBlank { CustomTipPlaceholder.fromHeaderFooterItem(footerRight) }
    val footerHeight = pagedReaderFooterHeightDp(LocalDensity.current.fontScale).dp
    if (!showReaderInfo) {
        // 只留空隙，保持正文位置稳定。
        Spacer(modifier.fillMaxWidth().height(footerHeight))
        return
    }
    TipRow(
        leftTemplate = templateLeft,
        centerTemplate = customTipFooterCenter,
        rightTemplate = templateRight,
        source = source,
        absOffset = anchorValue,
        pageInfo = pageInfo,
        currentTime = currentTime,
        batteryLevel = batteryLevel,
        bookName = bookName,
        color = textColor.copy(alpha = 0.65f),
        batteryTipStyle = batteryTipStyle,
        modifier = modifier.height(footerHeight),
    )
}

/**
 * 页眉/页脚共用的三槽行。
 *
 * 三等分单元格：左格贴左、中格居中、右格贴右——中格占满中央三分之一，所以内容
 * **恰好落在屏幕正中**，且三格互不重叠（超出各自三分之一时省略号截断）。
 * 旧实现只有左右两格，中间位置放不了东西。
 */
@Composable
private fun TipRow(
    leftTemplate: String,
    centerTemplate: String,
    rightTemplate: String,
    source: PagedChapterSource,
    absOffset: Int,
    pageInfo: PageInfo,
    currentTime: String,
    batteryLevel: Int,
    bookName: String,
    color: Color,
    batteryTipStyle: BatteryTipStyle,
    modifier: Modifier = Modifier,
) {
    val slotArgs = TipSlotArgs(source, absOffset, pageInfo, currentTime, batteryLevel, bookName, color, batteryTipStyle)
    Row(modifier.fillMaxWidth()) {
        TipSlot(template = leftTemplate, alignment = Alignment.CenterStart, args = slotArgs, modifier = Modifier.weight(1f))
        TipSlot(template = centerTemplate, alignment = Alignment.Center, args = slotArgs, modifier = Modifier.weight(1f))
        TipSlot(template = rightTemplate, alignment = Alignment.CenterEnd, args = slotArgs, modifier = Modifier.weight(1f))
    }
}

/** 一个槽位渲染所需的全部输入。 */
private class TipSlotArgs(
    val source: PagedChapterSource,
    val absOffset: Int,
    val pageInfo: PageInfo,
    val currentTime: String,
    val batteryLevel: Int,
    val bookName: String,
    val color: Color,
    val batteryTipStyle: BatteryTipStyle,
)

@Composable
private fun TipSlot(
    template: String,
    alignment: Alignment,
    args: TipSlotArgs,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxHeight(), contentAlignment = alignment) {
        if (template.isBlank()) return@Box
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.wrapContentWidth(),
        ) {
            splitOnBattery(template, args).forEach { segment ->
                when (segment) {
                    is TipSegment.Text -> if (segment.value.isNotEmpty()) {
                        Text(
                            text = segment.value,
                            fontSize = 11.sp,
                            color = args.color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    is TipSegment.Battery -> BatteryTip(level = segment.level, style = args.batteryTipStyle, color = args.color)
                }
            }
        }
    }
}

private sealed interface TipSegment {
    data class Text(val value: String) : TipSegment
    data class Battery(val level: Int) : TipSegment
}

/**
 * 按 `{BatteryPercent}` 在**模板中的位置**切段。
 *
 * 旧实现把电池图标无条件提到整行最前面，于是 `"第3页 {BatteryPercent}"` 渲染成
 * `"🔋 第3页 85%"`——图标和数字各去各处。切段后图标就落在 token 原本的位置上。
 * 电量不可用时不切段，token 交给 [CustomTipPlaceholder.render] 留空。
 */
private fun splitOnBattery(template: String, args: TipSlotArgs): List<TipSegment> {
    val batteryToken = CustomTipPlaceholder.BATTERY_PERCENT.token
    if (args.batteryLevel < 0 || !template.contains(batteryToken)) {
        return listOf(TipSegment.Text(renderPiece(template, args)))
    }
    val out = ArrayList<TipSegment>(4)
    var cursor = 0
    while (true) {
        val hit = template.indexOf(batteryToken, cursor)
        if (hit < 0) {
            out += TipSegment.Text(renderPiece(template.substring(cursor), args))
            break
        }
        if (hit > cursor) out += TipSegment.Text(renderPiece(template.substring(cursor, hit), args))
        out += TipSegment.Battery(args.batteryLevel)
        cursor = hit + batteryToken.length
    }
    return out
}

private fun renderPiece(template: String, args: TipSlotArgs): String =
    CustomTipPlaceholder.render(
        template = template,
        source = args.source,
        absOffset = args.absOffset,
        pageInfo = args.pageInfo,
        currentTime = args.currentTime,
        batteryLevel = args.batteryLevel,
        bookName = args.bookName,
    )

/** 电量的三种呈现方式。 */
@Composable
private fun BatteryTip(
    level: Int,
    style: BatteryTipStyle,
    color: Color,
) {
    when (style) {
        BatteryTipStyle.TEXT_ONLY -> Text(
            text = "$level%",
            fontSize = 11.sp,
            color = color,
            maxLines = 1,
        )
        BatteryTipStyle.ICON_WITH_TEXT -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = batteryLevelIcon(level),
                contentDescription = "电量",
                tint = color,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(text = "$level%", fontSize = 11.sp, color = color, maxLines = 1)
        }
        BatteryTipStyle.ICON_ONLY -> BatteryInlineLabel(level = level, color = color)
    }
}

/**
 * 「电量内显」：电池图标 + 百分比并排。
 *
 * 原版把 7sp 数字压进 21dp 电池腔体里，真机（Mate 70 Pro）上数字与轮廓互相重叠、
 * 大半被盖住 —— 用户 2026-09-18 反馈「数字和图标是两个东西，还重叠了」。
 * 改为图标与数字各占其位：图标只画电量格，数字 10sp 并排，间距 3dp。
 * 页眉页脚仍是低对比辅助信息，无障碍播报保持完整的「电量 N%」。
 */
@Composable
private fun BatteryInlineLabel(
    level: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics { contentDescription = "电量 $level%" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(width = 21.dp, height = 11.dp)) {
            val stroke = 1.dp.toPx()
            val bodyW = size.width - 3.5.dp.toPx()
            val bodyH = size.height - stroke
            val top = stroke / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, top),
                size = Size(bodyW, bodyH),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = Stroke(width = stroke),
            )
            // 正极凸点
            drawRoundRect(
                color = color,
                topLeft = Offset(bodyW + 1.2.dp.toPx(), top + bodyH * 0.3f),
                size = Size(1.8.dp.toPx(), bodyH * 0.4f),
                cornerRadius = CornerRadius(0.8.dp.toPx(), 0.8.dp.toPx()),
            )
            // 电量格：腔体内按当前电量填充，一眼可读，不与任何文字抢位。
            val inset = 1.6.dp.toPx()
            val fillW = (bodyW - inset * 2f) * (level.coerceIn(0, 100) / 100f)
            if (fillW > 0f) {
                drawRoundRect(
                    color = color.copy(alpha = 0.5f),
                    topLeft = Offset(inset, top + inset / 2f),
                    size = Size(fillW, bodyH - inset),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.width(3.dp))
        Text(
            text = "$level%",
            fontSize = 10.sp,
            color = color,
            maxLines = 1,
        )
    }
}

/** 三档电池图标：低电量用告警图标，中间档用空心电池，高电量用满格电池。 */
private fun batteryLevelIcon(level: Int): ImageVector = when {
    level <= 15 -> Icons.Rounded.BatteryAlert
    level <= 60 -> Icons.Rounded.BatteryStd
    else -> Icons.Rounded.BatteryFull
}
