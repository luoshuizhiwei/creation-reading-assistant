package com.creationreadingassistant.ui.components

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 零依赖真背景模糊（backdrop blur）实现，复刻 Cloudy 的 sky/cloudy 思路：
 * 1) [backdropSource] —— 贴在背景内容上，把其内容录制进共享 [GraphicsLayer]（"天空"）。
 * 2) [BackdropBlurSurface] —— 贴在玻璃面上，把"天空"里自己背后的那块区域取出来，
 *    用 [android.graphics.RenderEffect] 真虚化（API 31+），再叠半透明 tint 与玻璃边/微光。
 *
 * 为什么不直接接 skydoves:cloudy：其带 backdrop blur 的 0.6.x 要求 Kotlin 2.3 + Compose 1.10
 * + compileSdk 36，与本工程工具链（Kotlin 2.0.21 / Compose 1.7 / compileSdk 34）不兼容，
 * 且本机无 SDK 平台 36。故自实现，仅依赖 Android 原生 RenderEffect。
 *
 * 实现要点：背景用 [androidx.compose.ui.graphics.layer.drawLayer] 渲染进共享层；
 * 玻璃面在自己的 graphicsLayer(renderEffect=blur) 里 [drawLayer] 同一共享层（平移对齐到自身位置），
 * 由该层级的 RenderEffect 完成真虚化——绕开 [GraphicsLayer.toImageBitmap] 的 suspend 限制。
 * 每帧重绘会重新抓取背景，滚动时若玻璃面未重绘则会略滞后；静态/模态场景观感最佳。
 */

/** 共享的"天空"状态：持有捕获背景用的 [GraphicsLayer]，并记录玻璃面的根坐标。 */
class BackdropState(val layer: GraphicsLayer) {
    var overlayPos: Offset = Offset.Zero
}

@Composable
fun rememberBackdropState(): BackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { BackdropState(layer) }
}

/** 贴在背景内容上，将其录制进共享 [GraphicsLayer]，供玻璃面采样。 */
fun Modifier.backdropSource(state: BackdropState): Modifier =
    this.drawWithContent {
        state.layer.record { this@drawWithContent.drawContent() }
        drawLayer(state.layer)
    }

/**
 * 玻璃面容器：在 [content] 之下绘制被虚化的背景（仅限玻璃面背后的那块区域），
 * 再叠 [tint]，最后绘制 [content]（图标/文字保持清晰）。
 * API < 31 时退化为纯 [tint]（无模糊）。
 */
@Composable
fun BackdropBlurSurface(
    state: BackdropState,
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    tint: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val blurEffect: RenderEffect? = remember(density, radius) {
        if (Build.VERSION.SDK_INT >= 31) {
            val rPx = with(density) { radius.toPx() }
            AndroidRenderEffect.createBlurEffect(rPx, rPx, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        } else {
            null
        }
    }

    Box(
        modifier = modifier.onGloballyPositioned { coords ->
            state.overlayPos = coords.positionInRoot()
        },
    ) {
        // 1) 真虚化的背景层（仅 API 31+）：把共享层（已含背景）平移对齐到本玻璃面位置后绘制，
        //    本 Box 自己的 graphicsLayer(renderEffect) 负责真虚化。
        if (blurEffect != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { renderEffect = blurEffect }
                    .drawWithContent {
                        translate(-state.overlayPos.x, -state.overlayPos.y) {
                            drawLayer(state.layer)
                        }
                    },
            )
        } else if (tint.alpha > 0f) {
            Box(Modifier.matchParentSize().background(tint))
        }

        // 2) tint 叠加（在模糊背景之上、内容之下）
        if (tint.alpha > 0f) {
            Box(Modifier.matchParentSize().background(tint))
        }

        // 3) 玻璃内容（图标/文字，保持清晰）
        content()
    }
}
