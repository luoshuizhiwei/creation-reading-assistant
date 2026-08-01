package com.creationreadingassistant.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 「墨韵·素笺」形状系统。
 *
 * 改版前全项目散着 13 种圆角（2/4/6/8/10/11/12/14/16/18/20/22/999），每个调用点各写各的，
 * 同一层级的卡片在不同页面圆角能差一倍——这是"潦草"最直接的来源。这里收敛成 5 档。
 *
 * 第二轮视觉收敛改用更柔和的纸页圆角：控件 10、列表 14、卡片 18、弹层 28。
 *
 * 配套约定（不由 Shapes 表达，但属同一套语言）：
 * 卡片一律 elevation 0 + 发丝线描边，不用阴影。纸是叠放的，不是浮起来的。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
)

/** 胶囊形。用于筛选 chip 与圆形按钮，替代散落各处的 RoundedCornerShape(999.dp)。 */
val PillShape = RoundedCornerShape(percent = 50)

/** 细进度条圆角（比 extraSmall 更小一档）。
 *  统一取代进度条/滑条 clip 里散落的 `RoundedCornerShape(2.dp)` 字面量。*/
val ProgressBarShape = RoundedCornerShape(2.dp)
