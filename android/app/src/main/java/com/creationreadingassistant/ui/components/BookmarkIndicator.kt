package com.creationreadingassistant.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/**
 * 书签形选中态（全 App 唯一品牌特征，见设计实施稿 §6.1）。
 *
 * - **仅**锚定在底部导航栏顶部的发丝线（顶部分隔 Divider）上，向底栏内部垂下；
 *   不悬浮、不探出底栏上沿；与 24dp 导航图标垂直间隔 ≥4dp、不替代/遮挡图标。
 * - 填充主色（靛青）；底部 V 形缺口（旗标剪影）。
 * - 先实现 **8×9dp**；预留 **6×7dp** 回退尺寸（真机验收阶段按需切换，不改整体设计方向）。
 */
// 先实现尺寸（真机显重则缩至回退尺寸）
private val BOOKMARK_W = 8.dp
private val BOOKMARK_H = 9.dp
// 回退尺寸（仅真机微调用，见 §6.1）
private val BOOKMARK_W_SMALL = 6.dp
private val BOOKMARK_H_SMALL = 7.dp

/** 对外暴露的书签宽度（AppNavigation 做跨标签滑动定位用），与内部 BOOKMARK_W 同源。 */
val BookmarkIndicatorWidth: androidx.compose.ui.unit.Dp = BOOKMARK_W

/**
 * V 形缺口路径（以左上角为原点，单位 dp）。
 * - 8×9：`M0,0 H8 V9 L4,5 L0,9 Z`
 * - 6×7：`M0,0 H6 V7 L3,4 L0,7 Z`
 */
private fun bookmarkPath(w: Float, h: Float, notchY: Float): Path = Path().apply {
    moveTo(0f, 0f)
    lineTo(w, 0f)
    lineTo(w, h)
    lineTo(w / 2f, notchY)
    lineTo(0f, h)
    close()
}

@Composable
fun BookmarkIndicator(
    modifier: Modifier = Modifier,
    useSmall: Boolean = false,
) {
    val color = MaterialTheme.colorScheme.primary
    val w = if (useSmall) BOOKMARK_W_SMALL else BOOKMARK_W
    val h = if (useSmall) BOOKMARK_H_SMALL else BOOKMARK_H
    val notchY = if (useSmall) h.value * 4f / 7f else h.value * 5f / 9f
    Box(
        modifier = modifier
            .size(width = w, height = h)
            .drawBehind {
                drawPath(bookmarkPath(size.width, size.height, notchY), color)
            },
    )
}
