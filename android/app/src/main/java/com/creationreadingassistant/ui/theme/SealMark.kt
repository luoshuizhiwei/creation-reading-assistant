package com.creationreadingassistant.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 藏书印。
 *
 * 文人在读过、收藏的书上钤印，「读毕」印本身就承载"这本书我读完了"这一条信息——
 * 所以它不是花纹，是状态标记。**只用在已读完的书上**，别拿去当通用点缀：
 * 印一旦到处盖就不是印了，朱砂红也会从"印泥"退化成一个普通的暖红强调色。
 *
 * 三个刻意的细节：
 * - 轻微旋转：手盖的印不会和网格对齐，正了反而假。
 * - 不满不透明：印泥压进纸里，边缘会透出纸色。
 * - 方形小圆角：篆刻的边框是刻出来的，不是圆角矩形按钮。
 *
 * [animateStamp]：为 true 时播放一次「落印」动效（从 1.4 倍缩放 + 透明落到常态），
 * 受系统「减少动态效果」守卫——开启时直接显示常态印，无任何动画。
 */
@Composable
fun SealMark(
    modifier: Modifier = Modifier,
    text: String = "读毕",
    size: Dp = 34.dp,
    rotationDegrees: Float = -8f,
    animateStamp: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val stamped = animateStamp && !reducedMotion
    val alpha = remember { Animatable(if (stamped) 0f else 0.88f) }
    val scale = remember { Animatable(if (stamped) 1.4f else 1f) }
    LaunchedEffect(Unit) {
        if (stamped) {
            alpha.snapTo(0f)
            scale.snapTo(1.4f)
            alpha.animateTo(0.88f, tween(durationMillis = MotionTokens.Base))
            scale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
    }
    val box = size.value
    Box(
        modifier = modifier
            .size(size)
            .rotate(rotationDegrees)
            .alpha(alpha.value)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .border(width = 1.5.dp, color = AppCinnabar, shape = RoundedCornerShape(2.dp))
            .semantics { contentDescription = "已读完" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = AppCinnabar,
            // 品牌白名单例外：藏书印是装饰性状态标记，允许使用 Serif（非正文/标题，不受「大标题四档」约束）
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = (box * 0.34f).sp,
            lineHeight = (box * 0.36f).sp,
            letterSpacing = (-0.5).sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 状态印章徽标（已完成 / 已收藏）。
 *
 * 朱砂只用于「盖了印才算数」的语义：已读完、已收藏。这是 [SealMark] 的轻量变体，
 * 用于列表 / 卡片角落的状态标记，比整枚印章更克制、可密集排列。
 *
 * @param label 状态文字，如「读毕」「收藏」。
 * @param tint 印章色，默认朱砂 [AppCinnabar]；其余颜色仅限同语义的状态标记。
 */
@Composable
fun SealBadge(
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = AppCinnabar,
) {
    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = tint.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp),
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = tint,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}
