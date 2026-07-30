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
