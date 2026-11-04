package com.creationreadingassistant.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 阅读器独立纸张调色板（4 档 + 跟随外观映射）。
 *
 * 与外壳（应用框架）浅 / 深**解耦**：无论外壳浅或深，正文区按用户选的 paper 档渲染。
 * 进入阅读器后，正文、顶栏、底栏、进度条、弹层、系统栏共同跟随所选 paper 的
 * `light` / `dark` 属性（亮纸整屏亮、夜读整屏暗），禁止「浅色顶栏 + 夜读正文」明暗断层。
 *
 * 阅读器纸张 accent 已收编进「纸墨」品牌（依据见 docs/theme_decision_log.md）：白/暖纸 = 品牌墨绿，护眼绿纸 = 深青绿（与品牌绿拉开对比），夜读保留蓝。
 *
 * 5 色批注语义顺序固定为：黄(yellow) / 红(red) / 绿(green) / 蓝(blue) / 紫(purple)。
 * - [highlightColors]：随纸 5 色批注实色（用于高亮底 @0.18，正文对比度 ≥ 4.5:1，AA）。
 *
 * 注意：当前标注模型（HighlightEntity）仅有 color 字段、无标注类型字段，
 * 阅读器只支持「高亮底色」一种标注形态；§4.5 的下划线 / 引线暗化描边色
 * （annotationStrokeColors）**尚未实现**，其设计值保留在
 * `docs/WorkBuddy/theme_visual_plan.md` §4.5，待模型支持标注类型后再落地，
 * 本文件不保留未消费的 Token。
 */
data class ReaderPaperPalette(
    /** 内部 key：white / warm / green / night / sepia_dark / follow */
    val key: String,
    /** 纸张背景 */
    val bg: Color,
    /** 正文前景 */
    val fg: Color,
    /** 纸面降饱和灰（次级文字 / 副标题） */
    val fgMuted: Color,
    /** 纸张强调色（链接 / 选中句高亮底 / 进度条 / 顶栏图标） */
    val accent: Color,
    /** 发丝线 */
    val outlineVariant: Color,
    /** 实边 / 图标 */
    val outline: Color,
    /** 阅读器控制栏与弹层纸面 */
    val panel: Color,
    /** 当前项、按压态等更深一层纸面 */
    val panelStrong: Color,
    /** 强调色上的文字与图标 */
    val onAccent: Color,
    /** 明暗属性：true=亮纸，false=夜读 */
    val isLight: Boolean,
    /** 随纸 5 色批注实色 [黄, 红, 绿, 蓝, 紫] */
    val highlightColors: List<Color>,
) {
    /** 高亮底：随纸 5 色批注色 @0.18（正文对比度 ≥ 4.5:1，AA）。 */
    fun highlight(color: String): Color = highlightSolid(color).copy(alpha = 0.18f)

    /** 高亮底实色（5 色 @1.0，用于色点 / 选取指示，不作文本背景）。 */
    fun highlightSolid(color: String): Color = indexOf(color).let { highlightColors.getOrElse(it) { highlightColors[0] } }

    /** 长按选句底色：accent @0.25（收敛自原硬编码 0x40365B7E，随纸变化）。 */
    val selectionScrim: Color get() = accent.copy(alpha = 0.22f)

    /** TTS 朗读句高亮底：accent @0.20（收敛自原硬编码 0x33365B7E）。 */
    val ttsSentenceScrim: Color get() = accent.copy(alpha = 0.2f)

    /** 图片加载占位底：fg @0.07（收敛自原硬编码 0x12000000）。 */
    val imagePlaceholder: Color get() = fg.copy(alpha = 0.07f)

    /** 代码块行底色：fg @0.15（对齐 ReaderHelpers 的 paperFg 派生修法，夜读纸上不浑浊）。 */
    val codeBlockBg: Color get() = fg.copy(alpha = 0.15f)

    /** 分隔线（horizontal rule）：fg @0.3（对齐 ReaderHelpers 分隔线 alpha）。 */
    val horizontalRule: Color get() = fg.copy(alpha = 0.3f)

    // ── 阅读器 chrome / sheet 语义令牌（双轨关键：与外壳 ColorScheme 解耦，供 [ReaderPanelSurface] 等无装饰面板消费）──
    // 全部为 Color / Dp 值类型的派生属性，不参与 data class 相等性判定，保持 Compose stable。
    /** 面板投影高度：阅读器面板恒定 0dp —— 无装饰、无阴影，避免在翻页动画层侵蚀帧预算。 */
    val panelElevation: Dp get() = 0.dp
    /** chip / 分段选项底色：比 panel 深一档的 panelStrong。 */
    val chipBg: Color get() = panelStrong
    /** 发丝分割线色：outlineVariant（随纸变化）。 */
    val divider: Color get() = outlineVariant
    /** sheet 内区块底色：随纸 panel 纸面。 */
    val sheetSectionBg: Color get() = panel
    /** 更高一级突出容器底色（用于输入框底色、工具栏按压背景等，与 panelStrong 拉开色阶）。亮纸下深一阶，夜读下提亮一阶。 */
    val panelHighest: Color get() = if (isLight) {
        Color(
            red = (panelStrong.red * 0.93f).coerceIn(0f, 1f),
            green = (panelStrong.green * 0.93f).coerceIn(0f, 1f),
            blue = (panelStrong.blue * 0.93f).coerceIn(0f, 1f),
            alpha = panelStrong.alpha,
        )
    } else {
        Color(
            red = (panelStrong.red + 0.08f).coerceIn(0f, 1f),
            green = (panelStrong.green + 0.08f).coerceIn(0f, 1f),
            blue = (panelStrong.blue + 0.08f).coerceIn(0f, 1f),
            alpha = panelStrong.alpha,
        )
    }

    private fun indexOf(color: String): Int = when (color) {
        "yellow" -> 0
        "red" -> 1
        "green" -> 2
        "blue" -> 3
        "purple" -> 4
        else -> 0
    }
}

/** 白纸（默认，light）——偏冷的纸张白，与暖纸/护眼拉开色相距离 */
private val WHITE = ReaderPaperPalette(
    key = "white",
    bg = Color(0xFFFBFAF7),
    fg = Color(0xFF1B1E23),
    fgMuted = Color(0xFF5C606A),
    accent = Color(0xFF365C4A),
    outlineVariant = Color(0xFFE4E6EA),
    outline = Color(0xFFC7CBD2),
    panel = Color(0xFFF4F1E9),
    panelStrong = Color(0xFFE8E2D7),
    onAccent = Color.White,
    isLight = true,
    highlightColors = listOf(
        Color(0xFFE6C95A), // 黄
        Color(0xFFD08B7A), // 红
        Color(0xFF7FA86B), // 绿
        Color(0xFF6E8FC0), // 蓝
        Color(0xFFA884B0), // 紫
    ),
)

/** 暖纸（light）——明显偏牛皮纸黄，一眼能看出和白纸的色温差 */
private val WARM = ReaderPaperPalette(
    key = "warm",
    bg = Color(0xFFF5E6C8),
    fg = Color(0xFF2B231A),
    fgMuted = Color(0xFF867255),
    accent = Color(0xFF8B5E3C),
    outlineVariant = Color(0xFFE6D4AC),
    outline = Color(0xFFCDB17E),
    panel = Color(0xFFEEDFB8),
    panelStrong = Color(0xFFE3CE9A),
    onAccent = Color.White,
    isLight = true,
    highlightColors = listOf(
        Color(0xFFC9A24B), // 黄
        Color(0xFFB5705A), // 红
        Color(0xFF7C8A5A), // 绿
        Color(0xFF6E84A8), // 蓝
        Color(0xFF9A7C92), // 紫
    ),
)

/** 护眼（light）——淡豆绿，hue≈120° 的浅冷绿，长时间阅读更放松（豆绿色 = 国人熟悉的护眼色） */
private val GREEN = ReaderPaperPalette(
    key = "green",
    bg = Color(0xFFCFE5D0),
    fg = Color(0xFF1F291A),
    fgMuted = Color(0xFF5D7352),
    accent = Color(0xFF2E6B57),
    outlineVariant = Color(0xFFBEDDBF),
    outline = Color(0xFF9FBFA1),
    panel = Color(0xFFC3DCC4),
    panelStrong = Color(0xFFB3D1B4),
    onAccent = Color.White,
    isLight = true,
    highlightColors = listOf(
        Color(0xFFC7B65A), // 黄
        Color(0xFFB5705A), // 红
        Color(0xFF6E8A55), // 绿
        Color(0xFF6E84A8), // 蓝
        Color(0xFF9A7C92), // 紫
    ),
)

/** 跟随深色（dark）——温和深灰，不 OLED 黑，适合跟随系统深色外壳的常规夜读 */
private val DARK_FOLLOW = ReaderPaperPalette(
    key = "follow",
    bg = Color(0xFF1C1E23),
    fg = Color(0xFFD8DCE3),
    fgMuted = Color(0xFF9AA0AA),
    accent = Color(0xFF7D98CB),
    outlineVariant = Color(0xFF2A2D33),
    outline = Color(0xFF3E4148),
    panel = Color(0xFF24272D),
    panelStrong = Color(0xFF30333A),
    onAccent = Color(0xFF111318),
    isLight = false,
    highlightColors = listOf(
        Color(0xFFD8BD56),
        Color(0xFFC38172),
        Color(0xFF86A064),
        Color(0xFF869BC4),
        Color(0xFFB496BC),
    ),
)

/** 夜读（dark）——接近 OLED 纯黑，顶栏状态栏整屏统一漆黑 */
private val NIGHT = ReaderPaperPalette(
    key = "night",
    bg = Color(0xFF0F1014),
    fg = Color(0xFFDEE2E9),
    fgMuted = Color(0xFF9AA0AA),
    accent = Color(0xFF8AA6D8),
    outlineVariant = Color(0xFF1E2127),
    outline = Color(0xFF30333A),
    panel = Color(0xFF16191F),
    panelStrong = Color(0xFF202329),
    onAccent = Color(0xFF0B0C0F),
    isLight = false,
    highlightColors = listOf(
        Color(0xFFE6C95A), // 黄
        Color(0xFFD08B7A), // 红
        Color(0xFF8FA86B), // 绿
        Color(0xFF8EA3D0), // 蓝
        Color(0xFFC0A0C8), // 紫
    ),
)

/** 暮棕（dark）——暖色深色纸张，比 DARK_FOLLOW 更有"旧书"纸感、比 night 更柔和不刺眼 */
private val SEPIA_DARK = ReaderPaperPalette(
    key = "sepia_dark",
    bg = Color(0xFF2A2118),
    fg = Color(0xFFDDD4C8),
    fgMuted = Color(0xFFA89880),
    accent = Color(0xFFC89060),
    outlineVariant = Color(0xFF362D22),
    outline = Color(0xFF4E4235),
    panel = Color(0xFF352B20),
    panelStrong = Color(0xFF403528),
    onAccent = Color(0xFF1A1008),
    isLight = false,
    highlightColors = listOf(
        Color(0xFFD8B850),  // 黄（暖调适配）
        Color(0xFFC88070),  // 红
        Color(0xFF90A868),  // 绿（橄榄绿）
        Color(0xFF8898B8),  // 蓝（灰蓝）
        Color(0xFFB090A0),  // 紫（暖紫）
    ),
)

/** 羊皮纸（light）——典雅仿古纸感，温润金黄与深沉古墨，具有极佳护眼与厚重书卷感 */
private val PARCHMENT = ReaderPaperPalette(
    key = "parchment",
    bg = Color(0xFFF4ECD8),
    fg = Color(0xFF2C2218),
    fgMuted = Color(0xFF7C6E5A),
    accent = Color(0xFF8C5E28),
    outlineVariant = Color(0xFFE5DAC0),
    outline = Color(0xFFC7B99A),
    panel = Color(0xFFEDE3CC),
    panelStrong = Color(0xFFE2D6BC),
    onAccent = Color.White,
    isLight = true,
    highlightColors = listOf(
        Color(0xFFD4AC3B), // 黄
        Color(0xFFBA6954), // 红
        Color(0xFF768F55), // 绿
        Color(0xFF5F82A8), // 蓝
        Color(0xFF9E7498), // 紫
    ),
)

/** 水墨青瓷（light）——清冷淡青灰，黛墨青字，宛如宋瓷茶盏般文雅素净 */
private val CELADON = ReaderPaperPalette(
    key = "celadon",
    bg = Color(0xFFE5ECE8),
    fg = Color(0xFF1B2421),
    fgMuted = Color(0xFF5D6B66),
    accent = Color(0xFF2D5D4E),
    outlineVariant = Color(0xFFD3DED8),
    outline = Color(0xFFB5C4BD),
    panel = Color(0xFFDAE4DF),
    panelStrong = Color(0xFFCCD8D2),
    onAccent = Color.White,
    isLight = true,
    highlightColors = listOf(
        Color(0xFFC8B350), // 黄
        Color(0xFFB86D60), // 红
        Color(0xFF5B8869), // 绿（青翠）
        Color(0xFF5B7E98), // 蓝（霁蓝）
        Color(0xFF8C7392), // 紫
    ),
)

/** 幽夜墨青（dark）——水墨调沉浸夜读，深青墨色底与淡玉冷光正文，比纯黑更柔和舒缓 */
private val INK_NIGHT = ReaderPaperPalette(
    key = "ink_night",
    bg = Color(0xFF141918),
    fg = Color(0xFFD2DAD6),
    fgMuted = Color(0xFF8A9691),
    accent = Color(0xFF7FA89B),
    outlineVariant = Color(0xFF222B29),
    outline = Color(0xFF35423F),
    panel = Color(0xFF1A211F),
    panelStrong = Color(0xFF232B29),
    onAccent = Color(0xFF0C100F),
    isLight = false,
    highlightColors = listOf(
        Color(0xFFD4B858), // 黄
        Color(0xFFC5786C), // 红
        Color(0xFF769E85), // 绿
        Color(0xFF7B98B6), // 蓝
        Color(0xFFA88EB0), // 紫
    ),
)

/**
 * 按 key 解析阅读器纸张调色板。
 *
 * - `white` / `warm` / `green` / `night` / `sepia_dark` / `parchment` / `celadon` / `ink_night`：固定档。
 * - `follow`（跟随应用外观）：浅色外壳 → 白纸；深色外壳 → **独立 DARK_FOLLOW 温和深灰**。
 * - 未知值：按 [darkTheme] 回退到白纸 / 夜读。
 */
fun paperPalette(key: String, darkTheme: Boolean): ReaderPaperPalette = when (key) {
    "white" -> WHITE
    "warm" -> WARM
    "green" -> GREEN
    "night" -> NIGHT
    "sepia_dark" -> SEPIA_DARK
    "parchment" -> PARCHMENT
    "celadon" -> CELADON
    "ink_night" -> INK_NIGHT
    "follow" -> if (darkTheme) DARK_FOLLOW else WHITE
    else -> if (darkTheme) NIGHT else WHITE
}

/**
 * 阅读器纸张色彩水墨平滑过渡动画：
 * 在用户拉动灯绳或切换阅读纸张时，将调色板的核心语义色（背景、正文文字、次级文字、面板底色、强调色等）
 * 进行 240ms 水墨平滑插值过渡，彻底根除高对比度硬切导致的屏幕刺眼爆闪或骤暗。
 * 当系统开启减弱动效（enabled = false）时，直接返回目标调色板，完全尊重无障碍偏好。
 */
@Composable
fun animateReaderPaperPaletteAsState(
    target: ReaderPaperPalette,
    enabled: Boolean = true,
): State<ReaderPaperPalette> {
    if (!enabled) return rememberUpdatedState(target)

    val duration = 240
    val animSpec = tween<Color>(durationMillis = duration, easing = FastOutSlowInEasing)

    val animBg by animateColorAsState(target.bg, animSpec, label = "paper_bg")
    val animFg by animateColorAsState(target.fg, animSpec, label = "paper_fg")
    val animFgMuted by animateColorAsState(target.fgMuted, animSpec, label = "paper_fg_muted")
    val animAccent by animateColorAsState(target.accent, animSpec, label = "paper_accent")
    val animOutlineVariant by animateColorAsState(target.outlineVariant, animSpec, label = "paper_outline_var")
    val animOutline by animateColorAsState(target.outline, animSpec, label = "paper_outline")
    val animPanel by animateColorAsState(target.panel, animSpec, label = "paper_panel")
    val animPanelStrong by animateColorAsState(target.panelStrong, animSpec, label = "paper_panel_strong")
    val animOnAccent by animateColorAsState(target.onAccent, animSpec, label = "paper_on_accent")

    val animatedPalette = remember(target.key, target.isLight, animBg, animFg, animPanel) {
        target.copy(
            bg = animBg,
            fg = animFg,
            fgMuted = animFgMuted,
            accent = animAccent,
            outlineVariant = animOutlineVariant,
            outline = animOutline,
            panel = animPanel,
            panelStrong = animPanelStrong,
            onAccent = animOnAccent,
        )
    }
    return rememberUpdatedState(animatedPalette)
}

/**
 * 阅读器纸张调色板的 CompositionLocal：[ReaderPaperTheme] 子树内可直接读取，
 * 供不方便透传 palette 参数的排版/绘制层（如分页引擎 host）消费语义令牌。
 * 默认值为白纸，避免子树外访问时 null 处理。
 */
val LocalReaderPaperPalette: ProvidableCompositionLocal<ReaderPaperPalette> =
    staticCompositionLocalOf { paperPalette("white", darkTheme = false) }

data class ReaderPaperOption(val key: String, val label: String)

val ReaderPaperOptions = listOf(
    ReaderPaperOption("follow", "跟随外观"),
    ReaderPaperOption("white", "白纸"),
    ReaderPaperOption("warm", "暖纸"),
    ReaderPaperOption("parchment", "羊皮纸"),
    ReaderPaperOption("celadon", "青瓷灰"),
    ReaderPaperOption("green", "护眼"),
    ReaderPaperOption("sepia_dark", "暮棕"),
    ReaderPaperOption("ink_night", "墨青夜读"),
    ReaderPaperOption("night", "极黑夜读"),
)

/**
 * 把阅读纸张映射到阅读器内部的 Material 语义色，避免 BottomSheet、按钮和设置行
 * 意外读取应用外壳颜色。这里只改变阅读器子树，不反向修改应用主题。
 */
@Composable
fun ReaderPaperTheme(
    palette: ReaderPaperPalette,
    content: @Composable () -> Unit,
) {
    val baseScheme = MaterialTheme.colorScheme
    val scheme = remember(palette, baseScheme) { baseScheme.copy(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        primaryContainer = palette.panelStrong,
        onPrimaryContainer = palette.fg,
        background = palette.bg,
        onBackground = palette.fg,
        surface = palette.panel,
        onSurface = palette.fg,
        surfaceVariant = palette.panelStrong,
        onSurfaceVariant = palette.fgMuted,
        surfaceContainerLowest = palette.bg,
        surfaceContainerLow = palette.panel,
        surfaceContainer = palette.panel,
        surfaceContainerHigh = palette.panelStrong,
        surfaceContainerHighest = palette.panelHighest,
        outline = palette.outline,
        outlineVariant = palette.outlineVariant,
    ) }
    androidx.compose.runtime.CompositionLocalProvider(LocalReaderPaperPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}

/**
 * 阅读器专属「无装饰面板」薄封装（双轨关键）。
 *
 * 与外壳的 [com.creationreadingassistant.ui.components.SectionCard] 分道扬镳：后者叠加了
 * 纸墨渐变（verticalGradient）+ 噪点（PaperNoise）+ 玻璃高光（GlassOverlays）三层装饰，
 * 在阅读器翻页动画层会侵蚀帧预算；**阅读器路径禁止引用 SectionCard**。
 *
 * 本组件仅渲染：`Surface(shape, color = paper.panel, border = paper.outlineVariant)` —— 无任何
 * 渐变 / 噪点 / 玻璃叠加层，恒定 [ReaderPaperPalette.panelElevation]（0dp）无阴影，
 * 维持与外壳 ColorScheme 解耦（颜色只从 [LocalReaderPaperPalette] 取，不读 MaterialTheme.colorScheme）。
 *
 * @param shape 面板形状，默认取 [com.creationreadingassistant.ui.theme.ComponentSpec.floatingBarShape]
 *              （阅读器底部 chrome / 悬浮条形状）；sheet 场景可传 sheetShape。
 * @param content 面板内容（ColumnScope 插槽）。
 */
@Composable
fun ReaderPanelSurface(
    modifier: Modifier = Modifier,
    shape: Shape = LocalComponentSpec.current.floatingBarShape,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val paper = LocalReaderPaperPalette.current
    Surface(
        modifier = modifier,
        shape = shape,
        color = paper.panel,
        contentColor = paper.fg,
        border = BorderStroke(0.5.dp, paper.outlineVariant),
        shadowElevation = paper.panelElevation,
        tonalElevation = paper.panelElevation,
    ) {
        // Material3 Surface 的 content 为 () -> Unit；此处包一层 Column 向调用方暴露 ColumnScope 插槽。
        Column(content = content)
    }
}
