package com.creationreadingassistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import com.creationreadingassistant.ui.theme.CardContainer
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.LocalGlassPalette
import com.creationreadingassistant.ui.theme.LiquidGlassPalette
import com.creationreadingassistant.ui.theme.bounceable
import com.creationreadingassistant.ui.theme.resolve
import com.creationreadingassistant.ui.layout.LocalLayoutTokens

/**
 * Liquid Glass 基座表面（T3）。
 *
 * 视觉构成：
 * - 半透明表面：取 `spec.cardContainer` 底色，仅在 Apple + 有调色板时乘 `spec.glassTint`
 * - 发丝边：`spec.borderWidth` + outlineVariant / outline
 * - 环境阴影：`spec.cardElevationAmbient`
 * - 若 [LocalGlassPalette] 不为 null，在顶部叠加一条 `highlight→transparent` 的线性渐变 specular 高光层
 *   （alpha = `specularAlpha`，用 `Box` + `Brush.verticalGradient` + `clip(shape)`）
 *
 * 卡片本身恒定**不**调用真 Window 模糊（见 [com.creationreadingassistant.ui.theme.glassWindowBlur]）。
 * 所有 `onClick != null` 的交互组件会叠加 [bounceable] 弹性按压。
 *
 * @param modifier 外部修饰符
 * @param shape 表面形状（建议传 `spec.cardShape` 等）
 * @param onClick 传入则卡片可点击（带 ripple + 弹性），为 null 则静态
 * @param content 卡片内容（自动套主题无关的布局 padding）
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spec = LocalComponentSpec.current
    val scheme = MaterialTheme.colorScheme
    val palette = LocalGlassPalette.current

    val baseColor = spec.cardContainer.resolve()
    val containerColor = if (spec.glassEnabled && palette != null) {
        val tokens = if (isSystemInDarkTheme()) palette.dark else palette.light
        tokens.glassTintColor.copy(alpha = spec.glassTint)
    } else {
        baseColor
    }
    val borderColor = if (spec.borderSubtle) scheme.outlineVariant else scheme.outline
    val interactionSource = remember { MutableInteractionSource() }

    Box(modifier = if (onClick != null) modifier.bounceable(interactionSource) else modifier) {
        if (onClick != null) {
            Surface(
                onClick = onClick,
                shape = shape,
                color = containerColor,
                border = BorderStroke(spec.borderWidth, borderColor),
                tonalElevation = spec.cardElevation,
                shadowElevation = spec.cardElevationAmbient,
                interactionSource = interactionSource,
                content = { Column(Modifier.padding(LocalLayoutTokens.current.cardPadding), content = content) },
            )
        } else {
            Surface(
                shape = shape,
                color = containerColor,
                border = BorderStroke(spec.borderWidth, borderColor),
                tonalElevation = spec.cardElevation,
                shadowElevation = spec.cardElevationAmbient,
                content = { Column(Modifier.padding(LocalLayoutTokens.current.cardPadding), content = content) },
            )
        }

        // 玻璃叠加层：仅当 spec.glassEnabled 为真（当前三套主题均关闭）才绘制，
        // 避免 rim/sheen/specular 带来「不伦不类」的毛玻璃观感。
        if (palette != null && spec.glassEnabled) {
            GlassOverlays(shape = shape, palette = palette)
        }
    }
}

/**
 * 玻璃叠加层：在 GlassSurface 之上绘制 iOS 风格玻璃质感（仅 Apple / palette != null 时调用）。
 * - 顶部亮边 + 底部暗边（glass rim，赋予卡片"厚度"与玻璃边缘感）
 * - 斜向微光 sheen（高级反光）
 * - 顶部窄 specular 高光带
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
