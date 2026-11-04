package com.creationreadingassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.theme.LiquidGlassPalette

/**
 * 玻璃叠加层：在 GlassSurface 之上绘制 iOS 风格玻璃质感（仅 Apple / palette != null 时调用）。
 * - 顶部亮边 + 底部暗边（glass rim，赋予卡片"厚度"与玻璃边缘感）
 * - 斜向微光 sheen（高级反光）
 * - 顶部窄 specular 高光带
 *
 * 渐变里的 Color.White / Color.Black 是「光」与「阴影」的物理视觉语言，不是 UI 主题色：
 * 顶部亮边=玻璃边缘反光、底部暗边=边缘阴影、sheen=斜向反光。改成主题色会让物理光泽
 * 变成色块，破坏 Liquid Glass 的光学仿真。specular 高光带用 tokens.highlightColor 是因为
 * 它需要跟随调色板色相，而 rim/sheen 是无色光学叠加，故保留纯白/纯黑。
 */
@Composable
internal fun BoxScope.GlassOverlays(
    shape: Shape,
    palette: LiquidGlassPalette,
) {
    val tokens = if (isSystemInDarkTheme()) palette.dark else palette.light

    // 顶部亮边（玻璃上沿细高光线）
    Box(
        Modifier
            .matchParentSize()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = 0.55f),
                        0.03f to Color.Transparent,
                    ),
                ),
            ),
    )
    // 底部暗边（玻璃下沿细阴影边）
    Box(
        Modifier
            .matchParentSize()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.97f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.10f),
                    ),
                ),
            ),
    )
    // 斜向微光 sheen
    Box(
        Modifier
            .matchParentSize()
            .clip(shape)
            .graphicsLayer { alpha = 0.5f }
            .background(
                Brush.linearGradient(
                    start = Offset(0f, 0f),
                    end = Offset(700f, 700f),
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = 0.12f),
                        0.45f to Color.Transparent,
                    ),
                ),
            ),
    )
    // 顶部 specular 高光带（窄，贴顶边）
    Box(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(shape)
            .graphicsLayer { alpha = tokens.specularAlpha * 0.7f }
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to tokens.highlightColor.copy(alpha = 0.6f),
                        1f to Color.Transparent,
                    ),
                ),
            ),
    )
}
