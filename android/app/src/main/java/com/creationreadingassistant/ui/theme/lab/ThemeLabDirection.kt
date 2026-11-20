package com.creationreadingassistant.ui.theme.lab

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.creationreadingassistant.ui.theme.AppPalette

/**
 * Theme Lab v2 · P1 候选方向（打样，未铺开）。
 *
 * 四个方向共享「纸感 + 印章」产品气质：低饱和、留白、材质感；
 * 差异在色相家族 / 形状语言 / 信息密度 / 印章用法，可被并列比较、可整体回退。
 *
 * 回退依据：方向 = [AppPalette] 的四个新增 entry（storageId 前缀 `lab_`）。
 * 选回任一旧配色（纸墨 / 清爽蓝 / …）即完全回到旧视觉；旧 entry 的色值与映射分毫未动。
 * 方向只重做书架 / 阅读器正文 / 设置三屏样板，其余屏幕仅随 ColorScheme 换色（与旧配色切换同机制）。
 */
enum class ThemeLabDirection(
    val storageId: String,
    val displayName: String,
    /** 一句话设计意图（色板副标题，新增文案，不改既有文案）。 */
    val tagline: String,
) {
    /** 宋版书：冷灰宣纸 + 玄墨 + 朱砂小印；发丝线、零投影、方角小圆角、疏朗留白。 */
    XUAN_GRAY("lab_xuan_gray", "宣纸冷灰", "宋版冷灰 · 玄墨朱砂"),

    /** 雕版印刷：墨块作结构（选中 = 实心墨块反白），直角方章、实边、版刻分隔。 */
    YIN_SEAL("lab_yin_seal", "阴刻印章", "雕版墨块 · 白文反刻"),

    /** 晨光手账：暖米黄牛皮纸 + 茶褐 + 赭石印；软圆角、暖纸纹、温润中密度。 */
    CHEN_GUANG("lab_chen_guang", "晨光米黄", "晨光牛皮 · 茶褐赭石"),

    /** 宋瓷茶盏：青釉冷翠纸 + 黛青 + 朱砂印；釉面微渐变、紧凑密度、克制现代。 */
    QING_CI("lab_qing_ci", "青瓷冷翠", "宋瓷青釉 · 黛青冷玉"),

    /** 知识花园：暖米白纸 + 苔绿灰绿 + 圆角卡片；采样自「知识花园」桌面，柔和低饱和。P2 新用户默认。 */
    GARDEN("lab_garden", "知识花园绿", "暖纸苔绿 · 圆角花园");

    companion object {
        fun fromStorageId(id: String?): ThemeLabDirection? =
            entries.firstOrNull { it.storageId == id }
    }
}

/** 旧配色 → null（legacy，不进 lab 样板）；新配色 → 对应方向。 */
val AppPalette.labDirection: ThemeLabDirection?
    get() = ThemeLabDirection.fromStorageId(storageId)

/**
 * 当前候选方向。由 AppTheme 按所选 [AppPalette] 提供；旧配色 / 动态取色下为 null。
 * 三屏样板读它决定形状 / 密度 / 印章 / 阅读纸张；阅读器颜色仍只从 ReaderPaperPalette 取。
 */
val LocalThemeLabDirection: ProvidableCompositionLocal<ThemeLabDirection?> =
    staticCompositionLocalOf { null }

// ─────────────────────────────────────────────────────────────────────────────
// Primitive 层：每方向明 / 暗各一组原始色。命名 = 材质角色，不含用途语义。
// 全部色值经 contrast_calc.py 实算锁定（504 项断言全过），改动须重跑。
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 原始色板（primitive）。字段名是材质角色：
 * paper* = 纸面阶梯，ink* = 墨色阶梯，accent* = 主色家族，seal = 印泥，hairline* = 线。
 */
data class LabPrimitives(
    val paper0: Color,
    val paper1: Color,
    val paper2: Color,
    val paperLowest: Color,
    val paperLow: Color,
    val paperMid: Color,
    val paperHigh: Color,
    val paperHighest: Color,
    val ink0: Color,
    val ink1: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val hairline: Color,
    val hairlineSoft: Color,
    val seal: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
)

private fun c(hex: Long): Color = Color(hex)

// ── 宣纸冷灰：冷灰宣纸 / 玄墨 / 松烟青 / 朱砂 ──
private val XuanGrayLight = LabPrimitives(
    paper0 = c(0xFFF3F3F0), paper1 = c(0xFFFAFAF7), paper2 = c(0xFFE8E8E3),
    paperLowest = c(0xFFFDFDFB), paperLow = c(0xFFF7F7F3), paperMid = c(0xFFF1F1EC),
    paperHigh = c(0xFFE8E8E3), paperHighest = c(0xFFDFDFD8),
    ink0 = c(0xFF23272A), ink1 = c(0xFF565C60),
    accent = c(0xFF33544D), onAccent = c(0xFFFFFFFF),
    accentContainer = c(0xFFDCE7E1), onAccentContainer = c(0xFF1C352F),
    hairline = c(0xFFB9BDB7), hairlineSoft = c(0xFFE0E0DA),
    seal = c(0xFF9E3D32),
    error = c(0xFFC2413B), onError = c(0xFFFFFFFF),
    errorContainer = c(0xFFF9DEDC), onErrorContainer = c(0xFF410E0B),
)
private val XuanGrayDark = LabPrimitives(
    paper0 = c(0xFF16181B), paper1 = c(0xFF1E2124), paper2 = c(0xFF2A2E32),
    paperLowest = c(0xFF121417), paperLow = c(0xFF191C1F), paperMid = c(0xFF1E2124),
    paperHigh = c(0xFF272B2F), paperHighest = c(0xFF31363B),
    ink0 = c(0xFFE4E7E9), ink1 = c(0xFFA4ABB2),
    accent = c(0xFF9DBBB0), onAccent = c(0xFF12211C),
    accentContainer = c(0xFF2A423B), onAccentContainer = c(0xFFD3E4DC),
    hairline = c(0xFF5C6460), hairlineSoft = c(0xFF33383C),
    seal = c(0xFFC4705F),
    error = c(0xFFE57373), onError = c(0xFF410E0B),
    errorContainer = c(0xFF5C1A14), onErrorContainer = c(0xFFF9DEDC),
)

// ── 阴刻印章：纸白 / 浓墨块 / 朱砂（主色即墨，选中 = 墨块反白）──
private val YinSealLight = LabPrimitives(
    paper0 = c(0xFFF5F2EA), paper1 = c(0xFFFCFAF4), paper2 = c(0xFFEAE5D8),
    paperLowest = c(0xFFFDFCF8), paperLow = c(0xFFF8F5ED), paperMid = c(0xFFF1EDE2),
    paperHigh = c(0xFFEAE5D8), paperHighest = c(0xFFE0DACA),
    ink0 = c(0xFF211E19), ink1 = c(0xFF57514A),
    accent = c(0xFF26221C), onAccent = c(0xFFF5F2EA),
    accentContainer = c(0xFFE4DECF), onAccentContainer = c(0xFF26221C),
    hairline = c(0xFFBDB5A4), hairlineSoft = c(0xFFDED8C8),
    seal = c(0xFFA63A2E),
    error = c(0xFFC2413B), onError = c(0xFFFFFFFF),
    errorContainer = c(0xFFF9DEDC), onErrorContainer = c(0xFF410E0B),
)
private val YinSealDark = LabPrimitives(
    paper0 = c(0xFF141210), paper1 = c(0xFF1C1916), paper2 = c(0xFF2A2620),
    paperLowest = c(0xFF100E0C), paperLow = c(0xFF171411), paperMid = c(0xFF1C1916),
    paperHigh = c(0xFF25211B), paperHighest = c(0xFF2F2A23),
    ink0 = c(0xFFECE6D8), ink1 = c(0xFFB3AA99),
    accent = c(0xFFE6DECB), onAccent = c(0xFF1C1916),
    accentContainer = c(0xFF3A342A), onAccentContainer = c(0xFFECE6D8),
    hairline = c(0xFF6E6656), hairlineSoft = c(0xFF38322A),
    seal = c(0xFFCE7260),
    error = c(0xFFE57373), onError = c(0xFF410E0B),
    errorContainer = c(0xFF5C1A14), onErrorContainer = c(0xFFF9DEDC),
)

// ── 晨光米黄：暖米黄 / 茶褐 / 赭石 ──
private val ChenGuangLight = LabPrimitives(
    paper0 = c(0xFFF6EEDC), paper1 = c(0xFFFDFAF2), paper2 = c(0xFFEFE4CC),
    paperLowest = c(0xFFFEFCF7), paperLow = c(0xFFFAF5E9), paperMid = c(0xFFF4EDDB),
    paperHigh = c(0xFFEFE4CC), paperHighest = c(0xFFE7DABE),
    ink0 = c(0xFF33291D), ink1 = c(0xFF6B5C48),
    accent = c(0xFF7E542C), onAccent = c(0xFFFFFFFF),
    accentContainer = c(0xFFEFDFC4), onAccentContainer = c(0xFF4A3018),
    hairline = c(0xFFC4B49A), hairlineSoft = c(0xFFE3D7BE),
    seal = c(0xFF9C4A28),
    error = c(0xFFC2413B), onError = c(0xFFFFFFFF),
    errorContainer = c(0xFFF9DEDC), onErrorContainer = c(0xFF410E0B),
)
private val ChenGuangDark = LabPrimitives(
    paper0 = c(0xFF1B150E), paper1 = c(0xFF241D14), paper2 = c(0xFF322A1E),
    paperLowest = c(0xFF15100A), paperLow = c(0xFF1E1810), paperMid = c(0xFF241D14),
    paperHigh = c(0xFF2D2519), paperHighest = c(0xFF372E20),
    ink0 = c(0xFFF0E7D6), ink1 = c(0xFFC2B39D),
    accent = c(0xFFD9B48A), onAccent = c(0xFF33230F),
    accentContainer = c(0xFF54402A), onAccentContainer = c(0xFFF3E3C8),
    hairline = c(0xFF7A6C56), hairlineSoft = c(0xFF3E3527),
    seal = c(0xFFD08060),
    error = c(0xFFE57373), onError = c(0xFF410E0B),
    errorContainer = c(0xFF5C1A14), onErrorContainer = c(0xFFF9DEDC),
)

// ── 青瓷冷翠：青釉纸 / 黛青 / 朱砂 ──
private val QingCiLight = LabPrimitives(
    paper0 = c(0xFFECF1ED), paper1 = c(0xFFF8FBF8), paper2 = c(0xFFDFE9E2),
    paperLowest = c(0xFFFCFDFB), paperLow = c(0xFFF3F8F4), paperMid = c(0xFFEBF2ED),
    paperHigh = c(0xFFDFE9E2), paperHighest = c(0xFFD3E0D7),
    ink0 = c(0xFF1E2A26), ink1 = c(0xFF4F6159),
    accent = c(0xFF2F5D50), onAccent = c(0xFFFFFFFF),
    accentContainer = c(0xFFD5E6DD), onAccentContainer = c(0xFF173B31),
    hairline = c(0xFFA9BDB2), hairlineSoft = c(0xFFD3E0D7),
    seal = c(0xFF9E3D32),
    error = c(0xFFC2413B), onError = c(0xFFFFFFFF),
    errorContainer = c(0xFFF9DEDC), onErrorContainer = c(0xFF410E0B),
)
private val QingCiDark = LabPrimitives(
    paper0 = c(0xFF101917), paper1 = c(0xFF182220), paper2 = c(0xFF24312C),
    paperLowest = c(0xFF0C1312), paperLow = c(0xFF131C1A), paperMid = c(0xFF182220),
    paperHigh = c(0xFF1F2B27), paperHighest = c(0xFF283631),
    ink0 = c(0xFFE2EAE6), ink1 = c(0xFFA3B5AD),
    accent = c(0xFF93C0B0), onAccent = c(0xFF0F2420),
    accentContainer = c(0xFF274A40), onAccentContainer = c(0xFFD2E8DE),
    hairline = c(0xFF5F756C), hairlineSoft = c(0xFF2E3C36),
    seal = c(0xFFC4705F),
    error = c(0xFFE57373), onError = c(0xFF410E0B),
    errorContainer = c(0xFF5C1A14), onErrorContainer = c(0xFFF9DEDC),
)

// ── 知识花园绿：暖米白纸 / 苔绿 / 朱砂（采样 #4A6B4A / #759271 / #EAF1EA / #E8EFE8 / #FBF9F3 / #FCFAF4）──
// #759271 仅作非文本强调（progress/slider/icon，对纸底 3.3≥3.0）；文本-bearing 主色用采样 #4A6B4A（5.7≥4.5）。
private val GardenLight = LabPrimitives(
    paper0 = c(0xFFFBF9F3), paper1 = c(0xFFFCFAF4), paper2 = c(0xFFE8EFE8),
    paperLowest = c(0xFFFDFCF8), paperLow = c(0xFFF7F5EE), paperMid = c(0xFFF1EFE6),
    paperHigh = c(0xFFE8EFE8), paperHighest = c(0xFFDFE8DF),
    ink0 = c(0xFF4A6B4A), ink1 = c(0xFF5A6B57),
    accent = c(0xFF4A6B4A), onAccent = c(0xFFFFFFFF),
    accentContainer = c(0xFFEAF1EA), onAccentContainer = c(0xFF2C402C),
    hairline = c(0xFFB9C4B4), hairlineSoft = c(0xFFDCE5DA),
    seal = c(0xFF9E3D32),
    error = c(0xFFC2413B), onError = c(0xFFFFFFFF),
    errorContainer = c(0xFFF9DEDC), onErrorContainer = c(0xFF410E0B),
)
private val GardenDark = LabPrimitives(
    paper0 = c(0xFF161A14), paper1 = c(0xFF1E241C), paper2 = c(0xFF2A3226),
    paperLowest = c(0xFF12160F), paperLow = c(0xFF191E16), paperMid = c(0xFF1E241C),
    paperHigh = c(0xFF262E22), paperHighest = c(0xFF303A2A),
    ink0 = c(0xFFE4EAE0), ink1 = c(0xFFA8B4A2),
    accent = c(0xFFA9C4A0), onAccent = c(0xFF1A2416),
    accentContainer = c(0xFF33422E), onAccentContainer = c(0xFFD6E4CE),
    hairline = c(0xFF566252), hairlineSoft = c(0xFF333D2E),
    seal = c(0xFFC4705F),
    error = c(0xFFE57373), onError = c(0xFF410E0B),
    errorContainer = c(0xFF5C1A14), onErrorContainer = c(0xFFF9DEDC),
)

/** 方向 → 明 / 暗原始色板。 */
val ThemeLabDirection.primitivesLight: LabPrimitives
    get() = when (this) {
        ThemeLabDirection.XUAN_GRAY -> XuanGrayLight
        ThemeLabDirection.YIN_SEAL -> YinSealLight
        ThemeLabDirection.CHEN_GUANG -> ChenGuangLight
        ThemeLabDirection.QING_CI -> QingCiLight
        ThemeLabDirection.GARDEN -> GardenLight
    }

val ThemeLabDirection.primitivesDark: LabPrimitives
    get() = when (this) {
        ThemeLabDirection.XUAN_GRAY -> XuanGrayDark
        ThemeLabDirection.YIN_SEAL -> YinSealDark
        ThemeLabDirection.CHEN_GUANG -> ChenGuangDark
        ThemeLabDirection.QING_CI -> QingCiDark
        ThemeLabDirection.GARDEN -> GardenDark
    }

// ─────────────────────────────────────────────────────────────────────────────
// Semantic 层：primitive → Material ColorScheme 槽位（与 Theme.kt 既有 scheme 同构）。
// secondary / tertiary 沿用既有约定镜像主色；印章是独立语义色，不进 Material 槽位。
// ─────────────────────────────────────────────────────────────────────────────

fun LabPrimitives.toColorScheme(dark: Boolean, inversePrimary: Color = accent): ColorScheme {
    return if (dark) {
        darkColorScheme(
            primary = accent, onPrimary = onAccent,
            primaryContainer = accentContainer, onPrimaryContainer = onAccentContainer,
            secondary = accent, onSecondary = onAccent,
            secondaryContainer = accentContainer, onSecondaryContainer = onAccentContainer,
            tertiary = accent, onTertiary = onAccent,
            tertiaryContainer = accentContainer, onTertiaryContainer = onAccentContainer,
            background = paper0, onBackground = ink0,
            surface = paper1, onSurface = ink0,
            surfaceVariant = paper2, onSurfaceVariant = ink1,
            surfaceTint = accent,
            surfaceBright = paperHighest, surfaceDim = paper0,
            surfaceContainerLowest = paperLowest, surfaceContainerLow = paperLow,
            surfaceContainer = paperMid, surfaceContainerHigh = paperHigh,
            surfaceContainerHighest = paperHighest,
            inverseSurface = ink0, inverseOnSurface = paper0,
            inversePrimary = inversePrimary,
            outline = hairline, outlineVariant = hairlineSoft,
            scrim = c(0xFF000000),
            error = error, onError = onError,
            errorContainer = errorContainer, onErrorContainer = onErrorContainer,
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = onAccent,
            primaryContainer = accentContainer, onPrimaryContainer = onAccentContainer,
            secondary = accent, onSecondary = onAccent,
            secondaryContainer = accentContainer, onSecondaryContainer = onAccentContainer,
            tertiary = accent, onTertiary = onAccent,
            tertiaryContainer = accentContainer, onTertiaryContainer = onAccentContainer,
            background = paper0, onBackground = ink0,
            surface = paper1, onSurface = ink0,
            surfaceVariant = paper2, onSurfaceVariant = ink1,
            surfaceTint = accent,
            surfaceBright = paper1, surfaceDim = paperHighest,
            surfaceContainerLowest = paperLowest, surfaceContainerLow = paperLow,
            surfaceContainer = paperMid, surfaceContainerHigh = paperHigh,
            surfaceContainerHighest = paperHighest,
            inverseSurface = ink0, inverseOnSurface = paper0,
            inversePrimary = inversePrimary,
            outline = hairline, outlineVariant = hairlineSoft,
            scrim = c(0xFF000000),
            error = error, onError = onError,
            errorContainer = errorContainer, onErrorContainer = onErrorContainer,
        )
    }
}

/** 方向 × 明暗 → 完整 ColorScheme（paletteScheme 的新 entry 分支消费）。 */
fun ThemeLabDirection.colorScheme(dark: Boolean): ColorScheme {
    val primitives = if (dark) primitivesDark else primitivesLight
    val oppositeAccent = if (dark) primitivesLight.accent else primitivesDark.accent
    return primitives.toColorScheme(dark, inversePrimary = oppositeAccent)
}