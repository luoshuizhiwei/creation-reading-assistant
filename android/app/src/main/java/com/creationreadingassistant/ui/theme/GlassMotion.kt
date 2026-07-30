package com.creationreadingassistant.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/**
 * 弹性动效集中默认参数（Apple 风格：中等回弹）。
 *
 * 所有可点击组件的按压缩放都从这里取默认，确保全应用手感一致。
 */
object LiquidGlassMotion {
    /** 按下时缩放到的最小比例。 */
    val PressScale = 0.96f

    /** 回弹弹簧：中等刚度 + 中等弹性（bouncy）。 */
    val BounceSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}

/**
 * 按压弹性缩放修饰符。
 *
 * 按下时缩放到 [scaleMin]，释放时以 [springSpec] 回弹。
 * 需传入与可点击组件**共享**的 [interactionSource]，才能正确跟随按压状态
 * （自己内部不再创建 clickable，避免吞掉外层真实点击）。
 *
 * @param interactionSource 与承载点击的 Surface / clickable 共享的交互源
 * @param scaleMin 按下时的最小缩放比例
 * @param springSpec 回弹弹簧参数
 */
@Composable
fun Modifier.bounceable(
    interactionSource: InteractionSource,
    scaleMin: Float = LiquidGlassMotion.PressScale,
    springSpec: SpringSpec<Float> = LiquidGlassMotion.BounceSpring,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) scaleMin else 1f,
        animationSpec = springSpec,
        label = "bounceable_scale",
    )
    return this.scale(scale)
}
