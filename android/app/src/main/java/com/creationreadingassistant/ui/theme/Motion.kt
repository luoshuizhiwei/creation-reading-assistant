package com.creationreadingassistant.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import kotlin.math.roundToInt

/**
 * 动效基础（A 档打磨 · 遵守 2026-07-28 冻结设计：不引入新颜色 / 字号 / 圆角 / 阴影）。
 *
 * 全部动效均尊重系统「减少动态效果」无障碍设置：开启时退化为瞬时（无位移、无时长）。
 */

/**
 * 动效时长与缓动基准（D 档统一规范）。
 *
 * 所有新增强化动效应优先引用这些常量，避免在散落处写魔数导致时长漂移、各页面手感不一致。
 * 既有动效（A/B/C 档已验收）不强制回改，仅作为后续统一基准。
 */
object MotionTokens {
    const val Fast = 220      // 轻微提示 / 局部入场
    const val Base = 340      // 标准面板 / 卡片入场
    const val Slow = 650      // 数字滚动 / 强调过渡
    const val Shimmer = 1100
    const val CountUpDelay = 120

    /** 标准过渡缓动：MD3 的 FastOutSlowIn（先冲后停）。入场/出场统一用这一条。 */
    val StandardEasing: Easing = FastOutSlowInEasing
    /** 线性缓动：仅用于 Shimmer 这类周期性扫光。 */
    val LinearMotionEasing: Easing = LinearEasing
}

/**
 * 读取系统「减少动态效果」无障碍设置。
 * 开启时所有入场 / 过渡动效应退化为瞬时，避免对前庭 / 注意力障碍用户造成不适。
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    // 尊重系统「减少动态效果」：Android 14+ 的无障碍开关（key=reduce_motion，常量为 @hide 故用字面量），
    // 以及旧版「移除动画」开发者选项（TRANSITION_ANIMATION_SCALE == 0）。
    val reduceMotion = Settings.Secure.getString(context.contentResolver, "reduce_motion") == "1"
    val removeAnimations = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        1f,
    ) == 0f
    return reduceMotion || removeAnimations
}

/**
 * 触感反馈封装（B3 打磨 · 遵守冻结设计：仅用系统原生触感，不引入任何视觉 / 颜色变化）。
 *
 * 返回 `(HapticFeedbackType) -> Unit`，调用方在关键动作处触发。
 * 尊重系统「减少动态效果」：开启时完全不触发，避免对敏感用户造成不适。
 *
 * 推荐用法：
 * - 底部 Tab 切换等离散选择：HapticFeedbackType.TextHandleMove（克制轻点）
 * - 标记已读 / 导入完成 / 批量确认等结果性动作：HapticFeedbackType.LongPress（确认感）
 */
typealias HapticTrigger = (HapticFeedbackType) -> Unit

@Composable
fun rememberHaptic(reducedMotion: Boolean = false): HapticTrigger {
    val haptic = LocalHapticFeedback.current
    return remember(reducedMotion) {
        { type -> if (!reducedMotion) haptic.performHapticFeedback(type) }
    }
}

/**
 * 入场错落：淡入 + 轻微上移（最多 10dp）。
 *
 * @param delayMillis 错落延迟，让多个区块依次入场。
 * @param reducedMotion 为 true 时直接显示、无任何动画。
 */
fun Modifier.animateEnter(
    delayMillis: Int = 0,
    reducedMotion: Boolean = false,
): Modifier = composed {
    val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            progress.animateTo(
                1f,
                animationSpec = tween(
                    durationMillis = MotionTokens.Base,
                    delayMillis = delayMillis,
                    easing = MotionTokens.StandardEasing,
                ),
            )
        }
    }
    val alpha = progress.value
    val ty = (1f - progress.value) * 10f
    this.then(Modifier.alpha(alpha).graphicsLayer { translationY = ty })
}

/**
 * 列表项错落入场：基于序号的渐进延迟（封顶 12 项，避免长列表末尾延迟过久）。
 * 用于 LazyColumn/LazyRow/LazyVerticalGrid 的 itemsIndexed，消除"整块啪出现"的生硬感。
 * 尊重 reducedMotion。
 */
fun Modifier.listItemEnter(
    index: Int,
    reducedMotion: Boolean = false,
): Modifier {
    val delay = (index.coerceAtMost(12) * 40).coerceAtLeast(0)
    return animateEnter(delayMillis = delay, reducedMotion = reducedMotion)
}

/**
 * 错落入场（基于序号的渐进延迟）。语义同 [listItemEnter]，仅以「错落」命名表达意图，便于阅读。
 * 用于区块 / 卡片的依次淡入，消除"整块啪出现"的生硬感。尊重 reducedMotion。
 */
fun Modifier.staggerEnter(
    index: Int,
    reducedMotion: Boolean = false,
): Modifier = listItemEnter(index, reducedMotion)

/**
 * 数字滚动（count-up）。返回随动画推进的整数，调用方负责格式化显示。
 *
 * @param target 目标值；变化时从上一个值平滑过渡到新值（如切换统计周期时重新滚动）。
 * @param reducedMotion 为 true 时直接落到目标值。
 */
@Composable
fun rememberCountUp(target: Int, reducedMotion: Boolean = false): Int {
    val anim = remember { Animatable(if (reducedMotion) target.toFloat() else 0f) }
    LaunchedEffect(target, reducedMotion) {
        if (reducedMotion) {
            anim.snapTo(target.toFloat())
        } else {
            anim.animateTo(
                target.toFloat(),
                animationSpec = tween(
                    durationMillis = MotionTokens.Slow,
                    delayMillis = MotionTokens.CountUpDelay,
                    easing = MotionTokens.StandardEasing,
                ),
            )
        }
    }
    return anim.value.roundToInt()
}

/**
 * 微光骨架块。数据未就绪时显示 `surfaceVariant` 底 + 缓慢移动的浅色高光带；
 * 深色态同样安全（白色低透明度叠加，不刺眼）。reduced-motion 时退化为静态 `surfaceVariant`。
 *
 * 仅消费 Material 既有 Token，不引入新颜色。
 */
@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    radius: Dp = 10.dp,
    reducedMotion: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val offset by if (reducedMotion) {
        remember { mutableFloatStateOf(0.5f) }
    } else {
        val transition = rememberInfiniteTransition(label = "shimmer")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = MotionTokens.Shimmer, easing = MotionTokens.LinearMotionEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "shimmerOffset",
        )
    }
    val pos = offset
    val a = (pos - 0.2f).coerceIn(0f, 0.999f)
    val b = pos.coerceIn(0.001f, 1f)
    val c = (pos + 0.2f).coerceIn(0.001f, 1f)
    val brush = Brush.horizontalGradient(
        colorStops = arrayOf(
            0.0f to Color.Transparent,
            a to Color.Transparent,
            b to Color.White.copy(alpha = 0.16f),
            c to Color.Transparent,
            1.0f to Color.Transparent,
        ),
    )
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(scheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxSize().background(brush))
    }
}

/** 列表占位骨架：若干等宽微光条，用于首屏加载。消费既有间距 / 圆角 Token。 */
@Composable
fun ListSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 4,
    radius: Dp = 10.dp,
    reducedMotion: Boolean = false,
) {
    val layout = LocalLayoutTokens.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(layout.contentGap),
    ) {
        repeat(count) {
            ShimmerBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                radius = radius,
                reducedMotion = reducedMotion,
            )
        }
    }
}
