package com.creationreadingassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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
    /** 内部 key：white / warm / green / night / follow */
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
    val selectionScrim: Color get() = accent.copy(alpha = 0.25f)

    /** TTS 朗读句高亮底：accent @0.20（收敛自原硬编码 0x33365B7E）。 */
    val ttsSentenceScrim: Color get() = accent.copy(alpha = 0.2f)

    /** 图片加载占位底：fg @0.07（收敛自原硬编码 0x12000000）。 */
    val imagePlaceholder: Color get() = fg.copy(alpha = 0.07f)

    /** 代码块行底色：fg @0.15（对齐 ReaderHelpers 的 paperFg 派生修法，夜读纸上不浑浊）。 */
    val codeBlockBg: Color get() = fg.copy(alpha = 0.15f)

    /** 分隔线（horizontal rule）：fg @0.3（对齐 ReaderHelpers 分隔线 alpha）。 */
    val horizontalRule: Color get() = fg.copy(alpha = 0.3f)

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

/**
 * 按 key 解析阅读器纸张调色板。
 *
 * - `white` / `warm` / `green` / `night`：固定档。
 * - `follow`（跟随应用外观）：浅色外壳 → 白纸；深色外壳 → **独立 DARK_FOLLOW 温和深灰**（比 night 更柔和，用户选 night 才切到 OLED 近纯黑）。
 * - 未知值（理论上 migration 后不会出现）：按 [darkTheme] 回退到白纸 / 夜读。
 */
fun paperPalette(key: String, darkTheme: Boolean): ReaderPaperPalette = when (key) {
    "white" -> WHITE
    "warm" -> WARM
    "green" -> GREEN
    "night" -> NIGHT
    "follow" -> if (darkTheme) DARK_FOLLOW else WHITE
    else -> if (darkTheme) NIGHT else WHITE
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
    ReaderPaperOption("green", "护眼"),
    ReaderPaperOption("night", "夜读"),
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
    val scheme = MaterialTheme.colorScheme.copy(
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
        surfaceContainerHighest = palette.panelStrong,
        outline = palette.outline,
        outlineVariant = palette.outlineVariant,
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalReaderPaperPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}
