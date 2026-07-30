package com.creationreadingassistant.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * 组件级设计规格（Component-level Design Spec）。
 *
 * 在 MaterialTheme 的 colorScheme / typography / shapes 之上，提供**组件视觉的精细化控制**：
 * 卡片圆角档位、阴影高度、毛玻璃（frosted glass 近似）、边框粗细、分割线、卡片底色偏好、间距节奏。
 *
 * 三套主题各持一套 [ComponentSpec]，通过 [LocalComponentSpec] 向子树广播。
 */
data class ComponentSpec(
    // ── 圆角档位（统一消灭 12/16/18/22 混用）──
    /** 主区块卡片圆角 */
    val cardRadius: Dp,
    /** 底部弹层顶部圆角（抽屉式大圆角） */
    val sheetRadius: Dp,
    /** 胶囊 / 筛选 chip 圆角（通常 999 = 全圆） */
    val pillRadius: Dp,
    /** 列表项 / 小卡片圆角 */
    val listItemRadius: Dp,

    // ── Liquid Glass 形状（连续圆角 / 胶囊，统一取代下方 *Radius 直接用于 shape）──
    /** 主区块卡片形状（Apple = SquircleShape，DEFAULT/WEB = RoundedCornerShape） */
    val cardShape: Shape,
    /** 底部弹层（抽屉）形状 */
    val sheetShape: Shape,
    /** 列表项 / 小卡片形状 */
    val listItemShape: Shape,
    /** 胶囊 / 筛选 chip 形状（通常全圆） */
    val pillShape: Shape,
    /** 是否使用 squircle 连续圆角（仅 Apple） */
    val useSquircle: Boolean,
    /** 是否允许真 Window 模糊（仅 Dialog 容器在 API31+ 启用；卡片恒 false） */
    val glassBlur: Boolean,

    // ── 弹性动效（按压回弹）──
    /** 按下时最小缩放比例 */
    val pressScale: Float,
    /** 回弹弹簧刚度 */
    val bounceStiffness: Float,
    /** 回弹弹簧阻尼比 */
    val bounceDamping: Float,

    // ── 阴影（MaterialTheme elevation 之外的组件级精细控制）──
    /** 卡片投影高度（key shadow，方向性） */
    val cardElevation: Dp,
    /** 环境光投影高度（ambient shadow，更柔和，Apple 常用） */
    val cardElevationAmbient: Dp,

    // ── 毛玻璃（frosted glass 近似，跨版本安全实现）──
    /** 是否启用毛玻璃观感（半透明表面 + 极细边 + 柔和阴影，非真实背景模糊） */
    val glassEnabled: Boolean,
    /** 毛玻璃表面底色透明度（0..1，越小越透） */
    val glassTint: Float,

    // ── 边框 ──
    /** 边框宽度 */
    val borderWidth: Dp,
    /** true 用 outlineVariant（更淡的发丝线），false 用 outline（稍重） */
    val borderSubtle: Boolean,

    // ── 分割线 ──
    /** 分割线厚度（iOS 用 0.5dp 极细，墨韵/Web 用 1dp） */
    val dividerThickness: Dp,

    // ── 卡片底色偏好 ──
    /** 区块卡片默认取哪个 surface 容器档 */
    val cardContainer: CardContainer,

)

/** 卡片底色档位，映射到 MaterialTheme.colorScheme 的具体角色 */
enum class CardContainer {
    /** surfaceContainerLowest（最亮，纯白类） */
    Lowest,
    /** surfaceContainerLow */
    Low,
    /** surfaceVariant（次级表面，带一点主色染色） */
    Variant,
}

/** 根据当前 [CardContainer] 与主题取实际底色 */
@Composable
fun CardContainer.resolve(): Color {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        CardContainer.Lowest -> scheme.surfaceContainerLowest
        CardContainer.Low -> scheme.surfaceContainerLow
        CardContainer.Variant -> scheme.surfaceVariant
    }
}

// ── 单套主题（默认外壳）的组件规格 ──

/**
 * 默认「墨韵·素笺」：扁平 + 1px 发丝线 + 无投影 + 克制小圆角。
 * 与现有视觉完全一致，不引入任何新观感。圆角刻意压到 14dp，明显区别于 Apple 的大圆角。
 */
val DefaultComponentSpec = ComponentSpec(
    cardRadius = 14.dp,
    sheetRadius = 20.dp,
    pillRadius = 999.dp,
    listItemRadius = 10.dp,
    cardShape = RoundedCornerShape(14.dp),
    sheetShape = RoundedCornerShape(20.dp),
    listItemShape = RoundedCornerShape(10.dp),
    pillShape = RoundedCornerShape(999.dp),
    useSquircle = false,
    glassBlur = false,
    pressScale = 0.96f,
    bounceStiffness = Spring.StiffnessMedium,
    bounceDamping = Spring.DampingRatioMediumBouncy,
    cardElevation = 0.dp,
    cardElevationAmbient = 0.dp,
    glassEnabled = false,
    glassTint = 1f,
    borderWidth = 1.dp,
    borderSubtle = true,
    dividerThickness = 1.dp,
    cardContainer = CardContainer.Lowest,
)

/** CompositionLocal：当前组件级规格，默认 DEFAULT */
val LocalComponentSpec = staticCompositionLocalOf { DefaultComponentSpec }

/** 根据 [VisualStyle] 解析出对应的组件规格（T1：Apple/Web 已收敛，统一返回默认外壳规格） */
fun componentSpecForStyle(style: VisualStyle): ComponentSpec = DefaultComponentSpec
