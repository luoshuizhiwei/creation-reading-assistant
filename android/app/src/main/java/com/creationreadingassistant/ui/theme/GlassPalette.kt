package com.creationreadingassistant.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 单套（浅 / 深）Liquid Glass 令牌。
 *
 * @param highlightColor 顶部 specular 高光颜色
 * @param glassTintColor 玻璃表面底色（用于卡片半透明叠加）
 * @param specularAlpha specular 高光层整体透明度
 * @param blurRadius 真 Window 模糊半径（仅 API31+ 生效）
 */
data class GlassTokens(
    val highlightColor: Color,
    val glassTintColor: Color,
    val specularAlpha: Float,
    val blurRadius: Dp,
)

/**
 * Apple 主题专属的 Liquid Glass 调色板（浅色 + 深色各一套）。
 */
data class LiquidGlassPalette(
    val light: GlassTokens,
    val dark: GlassTokens,
)

/**
 * 当前视觉样式下的 Liquid Glass 调色板。
 * 仅 APPLE 提供；DEFAULT / WEB 为 null，组件据此跳过玻璃观感与高光层。
 */
val LocalGlassPalette = staticCompositionLocalOf<LiquidGlassPalette?> { null }

/** Apple 的 Liquid Glass 调色板实例（鲜活 iOS 蓝调玻璃）。 */
val AppleGlassPalette = LiquidGlassPalette(
    light = GlassTokens(
        highlightColor = Color(0xFFAEDCFF),   // 冰蓝高光，玻璃边缘更 crisp
        glassTintColor = Color(0xFFCFE2FB),    // 明显的蓝调玻璃底色（非灰白）
        specularAlpha = 0.18f,
        blurRadius = 24.dp,
    ),
    dark = GlassTokens(
        highlightColor = Color(0xFF5E5CE6),    // 靛蓝高光
        glassTintColor = Color(0xFF2A2A3E),     // 蓝调暗玻璃
        specularAlpha = 0.12f,
        blurRadius = 28.dp,
    ),
)

/**
 * 默认主题的中性玻璃调色板（纸墨 / 清爽蓝共用）。
 *
 * 与 [AppleGlassPalette] 不同：这里用中性白底 + 极低 specularAlpha，只给卡片一层
 * 极淡的顶部光泽与底部厚度边（"纸"的材质感），不引入任何色相偏移的玻璃观感。
 * tint 取中性色而非暖纸色，保证切换配色主题时卡片底色不与页面色相冲突。
 * `blurRadius = 0.dp`：不触发 Dialog 真实窗口模糊（[GlassWindow.glassWindowBlur] 据此设 0），
 * 保持弹层现状与流畅度。
 */
val DefaultGlassPalette = LiquidGlassPalette(
    light = GlassTokens(
        highlightColor = Color(0xFFFFFFFF),    // 中性高光（反光，无色相）
        glassTintColor = Color(0xFFF7F8FA),       // 中性白，纸墨/清爽蓝均不冲突
        specularAlpha = 0.10f,
        blurRadius = 0.dp,
    ),
    dark = GlassTokens(
        highlightColor = Color(0xFFE4E8EF),     // 中性白高光（暗面反光）
        glassTintColor = Color(0xFF1A1D22),     // 中性黑
        specularAlpha = 0.08f,
        blurRadius = 0.dp,
    ),
)
