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
 * 为什么比 MD3 默认（4/8/12/16/28）更紧：
 * 笺是裁切出来的纸，边角是利落的。圆角一大就成了 App 气泡，纸感就没了。
 * 收紧一档是从概念推出来的选择，不是随手调的数值。
 *
 * 配套约定（不由 Shapes 表达，但属同一套语言）：
 * 卡片一律 elevation 0 + 发丝线描边，不用阴影。纸是叠放的，不是浮起来的。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),   // 标签、小色块、角标
    small = RoundedCornerShape(6.dp),        // 小按钮、输入框
    medium = RoundedCornerShape(10.dp),      // 列表项、次级卡片
    large = RoundedCornerShape(14.dp),       // 主卡片、书籍封面
    extraLarge = RoundedCornerShape(20.dp),  // 底部弹层、大面板
)

/** 胶囊形。用于筛选 chip 与圆形按钮，替代散落各处的 RoundedCornerShape(999.dp)。 */
val PillShape = RoundedCornerShape(percent = 50)

/** 细进度条圆角（比 extraSmall 更小一档）。
 *  统一取代进度条/滑条 clip 里散落的 `RoundedCornerShape(2.dp)` 字面量。*/
val ProgressBarShape = RoundedCornerShape(2.dp)
