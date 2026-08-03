package com.creationreadingassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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

    private fun indexOf(color: String): Int = when (color) {
        "yellow" -> 0
        "red" -> 1
        "green" -> 2
        "blue" -> 3
        "purple" -> 4
        else -> 0
    }
}

/** 白纸（默认，light） */
private val WHITE = ReaderPaperPalette(
    key = "white",
    bg = Color(0xFFFAF8F2),
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

/** 暖纸（light） */
private val WARM = ReaderPaperPalette(
    key = "warm",
    bg = Color(0xFFF3ECDC),
    fg = Color(0xFF2B231A),
    fgMuted = Color(0xFF5C606A),
    accent = Color(0xFF365C4A),
    outlineVariant = Color(0xFFE6D8C4),
    outline = Color(0xFFCDBBA0),
    panel = Color(0xFFEDE3D2),
    panelStrong = Color(0xFFE0D1BA),
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

/** 护眼（light） */
private val GREEN = ReaderPaperPalette(
    key = "green",
    bg = Color(0xFFE8F0DF),
    fg = Color(0xFF1F291A),
    fgMuted = Color(0xFF5C606A),
    accent = Color(0xFF2E6B57),
    outlineVariant = Color(0xFFD8E4CC),
    outline = Color(0xFFBCD0AC),
    panel = Color(0xFFE0E9D6),
    panelStrong = Color(0xFFD1DFC4),
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

/** 夜读（dark） */
private val NIGHT = ReaderPaperPalette(
    key = "night",
    bg = Color(0xFF15171C),
    fg = Color(0xFFDEE2E9),
    fgMuted = Color(0xFF9AA0AA),
    accent = Color(0xFF8AA6D8),
    outlineVariant = Color(0xFF24262C),
    outline = Color(0xFF383B42),
    panel = Color(0xFF1D2026),
    panelStrong = Color(0xFF292D35),
    onAccent = Color(0xFF111318),
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
 * - `follow`（跟随应用外观）：浅色外壳 → 白纸，深色外壳 → 夜读（仅默认映射，不撕裂）。
 * - 未知值（理论上 migration 后不会出现）：按 [darkTheme] 回退到白纸 / 夜读。
 */
fun paperPalette(key: String, darkTheme: Boolean): ReaderPaperPalette = when (key) {
    "white" -> WHITE
    "warm" -> WARM
    "green" -> GREEN
    "night" -> NIGHT
    "follow" -> if (darkTheme) NIGHT else WHITE
    else -> if (darkTheme) NIGHT else WHITE
}

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
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}
