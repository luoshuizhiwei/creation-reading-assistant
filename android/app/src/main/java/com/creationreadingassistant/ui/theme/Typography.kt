package com.creationreadingassistant.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 「墨韵·素笺」字阶。
 *
 * 为什么要覆盖 MD3 默认型录：
 *
 * 1. **默认字阶是按拉丁文调的。** MD3 给 body/label 加了 0.25~0.5sp 的字距，
 *    那是为 Roboto 的小写字母设计的；套在方块字上会让每个汉字之间浮出一条缝，
 *    正是"排版没调过"的第一眼观感。中文正文字距一律归零。
 *
 * 2. **默认行高对中文偏挤。** 拉丁文 1.5 倍行高够用，汉字字面率高、笔画满，
 *    正文需要 1.6~1.75 才透气。这里正文取 1.65，长文本取 1.75。
 *
 * 3. **标题用衬线。** FontFamily.Serif 在中文设备上会落到思源宋体一类的宋体，
 *    与正文黑体形成"宋标黑文"的传统中文出版对比。这是零成本拿到的文人气质——
 *    项目没有内置字体（assets/ 与 res/font 都不存在），也不该为此塞十几 MB 进包。
 *
 * 4. **恢复层级。** 改版前全项目 bodySmall 用了 129 处、bodyLarge 12 处、display 0 处，
 *    字阶整个塌在最小一端，所有文字一样小。这里把各档拉开真实差距。
 *
 * 行高裁剪统一用 Trim.None：中文首末行的 half-leading 被裁会让卡片内文字贴边。
 */

private val Serif = FontFamily.Serif
private val Sans = FontFamily.Default

/** 中文正文不裁首末行行距，避免文字贴住容器边缘。 */
private val CjkLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun cjk(
    family: FontFamily,
    size: Int,
    lineHeight: Double,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeightStyle = CjkLineHeightStyle,
)

val AppTypography = Typography(
    // ── Display：宋体，用于极少数需要"开篇"感的地方（统计大数、空状态标题）
    displayLarge = cjk(Serif, 36, 1.22, FontWeight.Normal, -0.4),
    displayMedium = cjk(Serif, 30, 1.27, FontWeight.Normal, -0.2),
    displaySmall = cjk(Serif, 26, 1.31, FontWeight.Normal),

    // ── Headline：宋体，页面级标题
    headlineLarge = cjk(Serif, 26, 1.30, FontWeight.Medium),
    headlineMedium = cjk(Serif, 20, 1.40, FontWeight.Medium),
    headlineSmall = cjk(Serif, 18, 1.44, FontWeight.Medium),

    // ── Title：黑体，卡片与区块标题。中文加粗靠字重而非 Bold，避免糊成一团
    titleLarge = cjk(Sans, 18, 1.44, FontWeight.Medium),
    titleMedium = cjk(Sans, 16, 1.50, FontWeight.Medium),
    titleSmall = cjk(Sans, 14, 1.43, FontWeight.Medium),

    // ── Body：黑体，正文与说明。字距归零、行高放宽
    bodyLarge = cjk(Sans, 16, 1.63),
    bodyMedium = cjk(Sans, 14, 1.57),
    bodySmall = cjk(Sans, 13, 1.54),

    // ── Label：黑体，按钮与标签。这里保留极小字距，纯拉丁的数字/英文会更清晰
    labelLarge = cjk(Sans, 14, 1.43, FontWeight.Medium),
    labelMedium = cjk(Sans, 12, 1.50, FontWeight.Medium, 0.1),
    labelSmall = cjk(Sans, 11, 1.45, FontWeight.Medium, 0.1),
)
