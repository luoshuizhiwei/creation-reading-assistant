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
import androidx.compose.runtime.DisposableEffect
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
import java.util.concurrent.ConcurrentHashMap

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
 * 整页进入淡入（页面级转场）。
 *
 * navigation-compose 锁定 2.5.x、无 NavHost 转场 API（2.7+ 才有），故在统一页壳
 * （AppScreenScaffold）层做整页 fade，作为页面切换的过渡。**只 fade、不位移**：
 * 页面内区块已有各自的 stagger 上浮（[animateEnter]），整页再上浮会叠加出双重位移。
 * reducedMotion 时直接显示。
 */
fun Modifier.pageEnter(
    reducedMotion: Boolean = false,
): Modifier = composed {
    val alpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            alpha.animateTo(
                1f,
                animationSpec = tween(
                    durationMillis = MotionTokens.Fast,
                    easing = MotionTokens.StandardEasing,
                ),
            )
        }
    }
    this.then(Modifier.alpha(alpha.value))
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
 * 进程内记录每个 count-up 数字「上次展示出来的值」。
 *
 * 存在的原因：切换底部 Tab 时 NavHost 会销毁该 destination，Compose 的 `remember` 状态随之丢失，
 * 于是每次切回来数字都从 0 重新滚一遍——即使数值根本没变，还会先定住 120ms 再猛地跳到终值。
 * 记住上次值之后，动画只表达「数值真的变了」这一件事：值没变就完全静止，值变了才平滑过渡。
 *
 * 只活在当前进程；杀进程后首屏会重新滚一次，这是有意的（新会话重新展示一次不算打扰）。
 */
private object CountUpMemory {
    private val lastValues = ConcurrentHashMap<String, Int>()

    fun read(key: String): Int? = lastValues[key]

    fun write(key: String, value: Int) {
        lastValues[key] = value
    }
}

/**
 * count-up 的起始值决策。
 *
 * 抽成纯函数是为了在 JVM 测试里锁定语义——它决定了「切页面回来数字到底动不动」：
 * - 无障碍开启：直接等于目标值，永不滚动；
 * - 首次见到（无记忆）：从 0 滚，保留首屏动感；
 * - 有记忆：从上次展示值继续，于是「值没变」时起点即终点、一次动画都不会发生。
 */
internal fun countUpStartValue(remembered: Int?, target: Int, reducedMotion: Boolean): Float = when {
    reducedMotion -> target.toFloat()
    remembered == null -> 0f
    else -> remembered.toFloat()
}

/**
 * 数字滚动（count-up）。返回随动画推进的整数，调用方负责格式化显示。
 *
 * @param target 目标值；变化时从当前值平滑过渡到新值（如切换统计周期时重新滚动）。
 * @param key 同一数字的唯一标识，如 `stats.summary.minutes`。**必须给**：它是页面重建后恢复起始值的
 *            依据，也保证不同位置的数字不互相串值；不给会让数字退化成「每次进入都从 0 重播」。
 * @param reducedMotion 为 true 时直接落到目标值，不做任何滚动（无障碍口径，不因本改动改变）。
 */
@Composable
fun rememberCountUp(
    target: Int,
    key: String,
    reducedMotion: Boolean = false,
): Int {
    val startValue = countUpStartValue(CountUpMemory.read(key), target, reducedMotion)
    val anim = remember(key) { Animatable(startValue) }
    LaunchedEffect(key, target, reducedMotion) {
        when {
            reducedMotion -> anim.snapTo(target.toFloat())
            // 与上次展示值相同则完全不动：切页面回来不该有无意义的重播。
            anim.value == target.toFloat() -> Unit
            else -> anim.animateTo(
                target.toFloat(),
                animationSpec = tween(
                    durationMillis = MotionTokens.Slow,
                    easing = MotionTokens.StandardEasing,
                ),
            )
        }
        CountUpMemory.write(key, target)
    }
    // 动画没跑完就离开页面时记下中途值，下次从它继续，而不是退回 0 重新滚。
    DisposableEffect(key) {
        onDispose { CountUpMemory.write(key, anim.value.roundToInt()) }
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
