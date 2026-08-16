package com.creationreadingassistant.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.R

/**
 * 应用外壳字阶。大标题（display/headlineLarge）用展示衬线（[DisplayFontFamily]），其余用无衬线；阅读正文使用独立 ReaderSettings。
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
 * 3. **恢复层级。** 改版前全项目 bodySmall 用了 129 处、bodyLarge 12 处、display 0 处，
 *    字阶整个塌在最小一端，所有文字一样小。这里把各档拉开真实差距。
 *
 * 行高裁剪统一用 Trim.None：中文首末行的 half-leading 被裁会让卡片内文字贴边。
 */

/**
 * 展示字体（Display Font）——子集化后的思源宋体 Noto Serif SC（SIL OFL 授权，可随 APK 再分发）。
 *
 * 由 `android/scripts/build_display_font.py` 从系统可变字体实例化 Regular/Medium 两档、
 * 转为 TrueType 轮廓并子集化到应用真实字符集，落地于 `res/font/`。标题与统计数字统一走它，
 * 形成可被记住的「纸墨签名」。缺失字形由 Android 字体回退兜底，绝不出现豆腐块。
 *
 * 如需重新生成或扩充字符集：改脚本里的字符收集逻辑后重跑即可，[DisplayFontFamily]
 * 与下方所有 typography 调用**无需改动**。
 */
val DisplayFontFamily: FontFamily =
    FontFamily(
        Font(R.font.noto_serif_sc_regular),
        Font(R.font.noto_serif_sc_medium, FontWeight.Medium),
    )

private val Sans = FontFamily.Default
private val Serif = DisplayFontFamily

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
    displayLarge = cjk(Serif, 36, 1.22, FontWeight.Normal, -0.3),
    displayMedium = cjk(Serif, 30, 1.27, FontWeight.Normal, -0.2),
    displaySmall = cjk(Serif, 26, 1.31, FontWeight.Normal, -0.1),

    headlineLarge = cjk(Serif, 26, 1.30, FontWeight.Medium, -0.1),
    headlineMedium = cjk(Sans, 20, 1.40, FontWeight.SemiBold),
    headlineSmall = cjk(Sans, 18, 1.44, FontWeight.SemiBold),

    // ── Title：黑体，卡片与区块标题。中文加粗靠字重而非 Bold，避免糊成一团
    titleLarge = cjk(Sans, 18, 1.44, FontWeight.SemiBold),
    titleMedium = cjk(Sans, 16, 1.50, FontWeight.SemiBold),
    titleSmall = cjk(Sans, 14, 1.43, FontWeight.SemiBold),

    // ── Body：黑体，正文与说明。字距归零、行高放宽
    bodyLarge = cjk(Sans, 16, 1.63),
    bodyMedium = cjk(Sans, 14, 1.57),
    bodySmall = cjk(Sans, 13, 1.54),

    labelLarge = cjk(Sans, 14, 1.43, FontWeight.Medium),
    labelMedium = cjk(Sans, 12, 1.50, FontWeight.Medium),
    labelSmall = cjk(Sans, 11, 1.45, FontWeight.Medium),
)
