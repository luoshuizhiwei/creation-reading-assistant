package com.creationreadingassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes

/**
 * 根据当前 [VisualStyle] 解析出对应的 Material3 主题参数。
 *
 * - [VisualStyle.DEFAULT] → 返回 null，使用外层 AppTheme 已设定的「墨韵·素笺」
 * - [VisualStyle.APPLE] → 返回 Apple 风格参数
 * - [VisualStyle.WEB] → 返回 Web 版风格参数
 */
data class ThemeParams(
    val colorScheme: ColorScheme? = null,
    val typography: Typography? = null,
    val shapes: Shapes? = null,
)

@Composable
fun resolveThemeParams(style: VisualStyle, darkTheme: Boolean): ThemeParams {
    // T1：Apple / Web 视觉分支已收敛为单套默认外壳，全部回退到 DEFAULT（沿用外层 AppTheme 自带浅深）。
    return ThemeParams()
}

/**
 * 视觉样式包装器。根据传入的 [style] 提供对应主题，
 * 并通过 CompositionLocal 向子树广播当前样式。
 *
 * 用法：
 * ```kotlin
 * var style by remember { mutableStateOf(VisualStyle.DEFAULT) }
 * VisualStyleProvider(style = style) {
 *   // 这里的 MaterialTheme 会自动使用对应风格的参数
 *   MyContent()
 * }
 * ```
 */
@Composable
fun VisualStyleProvider(
    style: VisualStyle,
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val params = resolveThemeParams(style, darkTheme)
    val componentSpec = componentSpecForStyle(style)
    // 卡片容器色跟随主题：从当前 ColorScheme 动态派生玻璃底色，避免切换暖色主题
    // （纸墨 / 暖杏等）时卡片固定中性色与页面背景色相割裂。
    // glassTintColor 取 surfaceContainerLowest——浅色模式下最亮的表面容器（各主题各自的纸白），
    // 深色模式下最暗的容器；highlight / specular / blur 与色相无关，沿用 DefaultGlassPalette。
    // 注：params.colorScheme 恒为 null，故此处读到的 MaterialTheme.colorScheme 与子树内部一致。
    val scheme = MaterialTheme.colorScheme
    val dynamicGlassPalette = remember(scheme) {
        LiquidGlassPalette(
            light = GlassTokens(
                highlightColor = DefaultGlassPalette.light.highlightColor,
                glassTintColor = scheme.surfaceContainerLowest,
                specularAlpha = DefaultGlassPalette.light.specularAlpha,
                blurRadius = DefaultGlassPalette.light.blurRadius,
            ),
            dark = GlassTokens(
                highlightColor = DefaultGlassPalette.dark.highlightColor,
                glassTintColor = scheme.surfaceContainerLowest,
                specularAlpha = DefaultGlassPalette.dark.specularAlpha,
                blurRadius = DefaultGlassPalette.dark.blurRadius,
            ),
        )
    }
    CompositionLocalProvider(
        LocalVisualStyle provides style,
        LocalComponentSpec provides componentSpec,
        // 默认「墨韵·素笺」：提供随主题动态派生的纸感调色板（容器色跟随当前 ColorScheme）。
        // 其它视觉样式（APPLE/WEB 已收敛回退）维持 null，行为不变。
        LocalGlassPalette provides if (style == VisualStyle.DEFAULT) dynamicGlassPalette else null,
    ) {
        MaterialTheme(
            colorScheme = params.colorScheme ?: MaterialTheme.colorScheme,
            typography = params.typography ?: MaterialTheme.typography,
            shapes = params.shapes ?: MaterialTheme.shapes,
            content = content,
        )
    }
}
