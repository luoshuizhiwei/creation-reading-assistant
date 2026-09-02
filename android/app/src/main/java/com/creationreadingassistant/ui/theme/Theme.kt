package com.creationreadingassistant.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 「纸墨」配色：暖纸承载内容，墨绿只负责可操作与选中状态。
 * 阅读器纸张与夜读仍由 ReaderSettings 独立管理，不与应用配色绑定。
 */

// 浅色令牌（来自 md3-base.css :root）
private val Paper = Color(0xFFF5F1E8)
private val PaperRaised = Color(0xFFFCFBF7)
private val PaperLow = Color(0xFFF8F5EE)
private val PaperMid = Color(0xFFF0ECE3)
private val PaperHigh = Color(0xFFE8E3D9)
private val Ink = Color(0xFF202421)
private val Muted = Color(0xFF626862)
private val Hairline = Color(0xFFD8D2C7)
private val PaperInkGreen = Color(0xFF365C4A)
private val PaperInkGreenContainer = Color(0xFFDCE8DF)
private val PaperInkGreenOnContainer = Color(0xFF1D392B)

// AMOLED 纯黑专用令牌：把暗色的"纸面"整层收敛到绝对黑，仅表面层级差保留极细层次
private val AmoledBlack = Color(0xFF000000)
private val AmoledNearBlack = Color(0xFF070807)
private val AmoledSurfaceLow = Color(0xFF0B0C0B)
private val AmoledSurface = Color(0xFF0F100F)
private val AmoledSurfaceHigh = Color(0xFF161716)

// 「清爽蓝」专用令牌：冷白底 + 平静蓝，微信读书 / 起点一类主流阅读 App 的观感。
private val ClearBlue = Color(0xFF2762BF)
private val ClearBlueContainer = Color(0xFFD9E3FF)
private val ClearBlueOnContainer = Color(0xFF0C2A63)

// 「雾青」专用令牌（primary 不在此声明——单一真源在 AppPalette.SOFT_MIST.accentLight/accentDark）
private val SoftMistContainer = Color(0xFFD4E3EB)
private val SoftMistOnContainer = Color(0xFF1A3040)
private val SoftMistBg = Color(0xFFF5F6F4)
private val SoftMistSurface = Color(0xFFFAFBF9)
private val SoftMistLow = Color(0xFFF2F4F1)
private val SoftMistMid = Color(0xFFECEFEA)
private val SoftMistHigh = Color(0xFFE5E8E3)
private val SoftMistInk = Color(0xFF1A1F1C)
private val SoftMistMuted = Color(0xFF4A5550)
private val SoftMistHairline = Color(0xFFC5CCC7)
private val SoftMistHairlineSoft = Color(0xFFDDE2DC)

// 「暖杏」专用令牌（primary 不在此声明——单一真源在 AppPalette.WARM_APRICOT.accentLight/accentDark）
private val WarmApricotContainer = Color(0xFFF0DFC8)
private val WarmApricotOnContainer = Color(0xFF3A2A18)
private val WarmApricotBg = Color(0xFFFAF7F2)
private val WarmApricotSurface = Color(0xFFFFFCF8)
private val WarmApricotLow = Color(0xFFF6F2EB)
private val WarmApricotMid = Color(0xFFF0EBE3)
private val WarmApricotHigh = Color(0xFFE9E3D9)
private val WarmApricotInk = Color(0xFF1E1A15)
private val WarmApricotMuted = Color(0xFF5C5245)
private val WarmApricotHairline = Color(0xFFD4C9BA)
private val WarmApricotHairlineSoft = Color(0xFFE5DDD2)

// 语义强调色（来自 md3-base.css）
val AppSuccess = Color(0xFF4A6E3F)           // --md3-success
val AppWarning = Color(0xFFA05F12)           // --md3-warning
val AppError = Color(0xFFB3261E)             // --md3-error
val AppStreak = PaperInkGreen

/**
 * 朱砂。**只用于印章标记**，不作通用强调色。
 *
 * 印泥的红在文人物料里有确定含义——盖了印才算数。把它限定在"已读完"这类完成态标记上，
 * 它就是信息；一旦拿去当按钮色、当高亮色，就退化成一个普通的暖红点缀，
 * 整套配色也会滑向"米底 + 衬线 + 陶土红"那个到处都是的样子。
 */
val AppCinnabar = Color(0xFF9E3D32)

private val PaperInkLightColorScheme = lightColorScheme(
    primary = PaperInkGreen,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = PaperInkGreenContainer,
    onPrimaryContainer = PaperInkGreenOnContainer,
    secondary = PaperInkGreen,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = PaperInkGreenContainer,
    onSecondaryContainer = PaperInkGreenOnContainer,
    tertiary = PaperInkGreen,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = PaperInkGreenContainer,
    onTertiaryContainer = PaperInkGreenOnContainer,
    background = Paper,
    onBackground = Ink,
    surface = PaperRaised,
    onSurface = Ink,
    surfaceVariant = PaperMid,
    onSurfaceVariant = Muted,
    surfaceTint = PaperInkGreen,
    surfaceBright = PaperRaised,
    surfaceDim = PaperHigh,
    surfaceContainerLowest = PaperRaised,
    surfaceContainerLow = PaperRaised,
    surfaceContainer = PaperLow,
    surfaceContainerHigh = PaperMid,
    surfaceContainerHighest = PaperHigh,
    inverseSurface = Ink,
    inverseOnSurface = Color(0xFFF2F5F2),
    inversePrimary = Color(0xFF8FAF9D),
    outline = Hairline,
    outlineVariant = Color(0xFFE7E1D6),
    scrim = Color(0xFF000000),
    error = Color(0xFFC2413B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val PaperInkDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8FAF9D),
    onPrimary = Color(0xFF10251A),
    primaryContainer = Color(0xFF294637),
    onPrimaryContainer = Color(0xFFD5E8DC),
    secondary = Color(0xFF8FAF9D),
    onSecondary = Color(0xFF10251A),
    secondaryContainer = Color(0xFF294637),
    onSecondaryContainer = Color(0xFFD5E8DC),
    tertiary = Color(0xFF8FAF9D),
    onTertiary = Color(0xFF10251A),
    tertiaryContainer = Color(0xFF294637),
    onTertiaryContainer = Color(0xFFD5E8DC),
    background = Color(0xFF151815),
    onBackground = Color(0xFFE8ECE8),
    surface = Color(0xFF1E221F),
    onSurface = Color(0xFFE8ECE8),
    surfaceVariant = Color(0xFF292E2A),
    onSurfaceVariant = Color(0xFFAEB6B0),
    surfaceTint = Color(0xFF8FAF9D),
    surfaceBright = Color(0xFF343A35),
    surfaceDim = Color(0xFF151815),
    surfaceContainerLowest = Color(0xFF111411),
    surfaceContainerLow = Color(0xFF1A1E1B),
    surfaceContainer = Color(0xFF1E221F),
    surfaceContainerHigh = Color(0xFF292E2A),
    surfaceContainerHighest = Color(0xFF343A35),
    inverseSurface = Color(0xFFE8ECE8),
    inverseOnSurface = Color(0xFF202421),
    inversePrimary = PaperInkGreen,
    outline = Color(0xFF566057),
    outlineVariant = Color(0xFF353B36),
    scrim = Color(0xFF000000),
    error = Color(0xFFE57373),
    onError = Color(0xFF410E0B),
    errorContainer = Color(0xFF5C1A14),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** 「清爽蓝」浅色：冷白页面 + 纯白卡片，蓝只负责可操作与选中状态。 */
private val ClearBlueLightColorScheme = lightColorScheme(
    primary = ClearBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = ClearBlueContainer,
    onPrimaryContainer = ClearBlueOnContainer,
    secondary = ClearBlue,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = ClearBlueContainer,
    onSecondaryContainer = ClearBlueOnContainer,
    tertiary = ClearBlue,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = ClearBlueContainer,
    onTertiaryContainer = ClearBlueOnContainer,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF191D24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191D24),
    surfaceVariant = Color(0xFFEDF0F5),
    onSurfaceVariant = Color(0xFF5B6472),
    surfaceTint = ClearBlue,
    surfaceBright = Color(0xFFF9F9FD),
    surfaceDim = Color(0xFFD9DAE0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F7FB),
    surfaceContainer = Color(0xFFF1F3F8),
    surfaceContainerHigh = Color(0xFFEBEEF3),
    surfaceContainerHighest = Color(0xFFE5E9EF),
    inverseSurface = Color(0xFF2E3138),
    inverseOnSurface = Color(0xFFF0F1F7),
    inversePrimary = Color(0xFFA8C6FF),
    outline = Color(0xFFD3D8E0),
    outlineVariant = Color(0xFFE3E7ED),
    scrim = Color(0xFF000000),
    error = Color(0xFFC2413B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

/** 「清爽蓝」深色：冷灰蓝暗面，避免纸墨深色方案的绿相残留。 */
private val ClearBlueDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C6FF),
    onPrimary = Color(0xFF0B2D6B),
    primaryContainer = Color(0xFF24478F),
    onPrimaryContainer = Color(0xFFD9E3FF),
    secondary = Color(0xFFA8C6FF),
    onSecondary = Color(0xFF0B2D6B),
    secondaryContainer = Color(0xFF24478F),
    onSecondaryContainer = Color(0xFFD9E3FF),
    tertiary = Color(0xFFA8C6FF),
    onTertiary = Color(0xFF0B2D6B),
    tertiaryContainer = Color(0xFF24478F),
    onTertiaryContainer = Color(0xFFD9E3FF),
    background = Color(0xFF101419),
    onBackground = Color(0xFFE2E6ED),
    surface = Color(0xFF161A20),
    onSurface = Color(0xFFE2E6ED),
    surfaceVariant = Color(0xFF22272F),
    onSurfaceVariant = Color(0xFFA9B2C0),
    surfaceTint = Color(0xFFA8C6FF),
    surfaceBright = Color(0xFF363A42),
    surfaceDim = Color(0xFF101419),
    surfaceContainerLowest = Color(0xFF0B0E13),
    surfaceContainerLow = Color(0xFF14181E),
    surfaceContainer = Color(0xFF181C23),
    surfaceContainerHigh = Color(0xFF22262D),
    surfaceContainerHighest = Color(0xFF2C3138),
    inverseSurface = Color(0xFFE2E6ED),
    inverseOnSurface = Color(0xFF2E3138),
    inversePrimary = ClearBlue,
    outline = Color(0xFF59616E),
    outlineVariant = Color(0xFF333945),
    scrim = Color(0xFF000000),
    error = Color(0xFFE57373),
    onError = Color(0xFF410E0B),
    errorContainer = Color(0xFF5C1A14),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** 「雾青」浅色：冷灰绿纸底 + 低饱和青灰蓝，微信读书式素雅观感。 */
private val SoftMistLightColorScheme = lightColorScheme(
    primary = AppPalette.SOFT_MIST.accentLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = SoftMistContainer,
    onPrimaryContainer = SoftMistOnContainer,
    secondary = AppPalette.SOFT_MIST.accentLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = SoftMistContainer,
    onSecondaryContainer = SoftMistOnContainer,
    tertiary = AppPalette.SOFT_MIST.accentLight,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = SoftMistContainer,
    onTertiaryContainer = SoftMistOnContainer,
    background = SoftMistBg,
    onBackground = SoftMistInk,
    surface = SoftMistSurface,
    onSurface = SoftMistInk,
    surfaceVariant = SoftMistMid,
    onSurfaceVariant = SoftMistMuted,
    surfaceTint = AppPalette.SOFT_MIST.accentLight,
    surfaceBright = SoftMistSurface,
    surfaceDim = SoftMistHigh,
    surfaceContainerLowest = SoftMistSurface,
    surfaceContainerLow = Color(0xFFF7F8F6),
    surfaceContainer = SoftMistLow,
    surfaceContainerHigh = SoftMistMid,
    surfaceContainerHighest = SoftMistHigh,
    inverseSurface = SoftMistInk,
    inverseOnSurface = Color(0xFFEFF2EE),
    inversePrimary = AppPalette.SOFT_MIST.accentDark,
    outline = SoftMistHairline,
    outlineVariant = SoftMistHairlineSoft,
    scrim = Color(0xFF000000),
    error = Color(0xFFC2413B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

/** 「雾青」深色：冷灰绿暗面，5 级 surface 台阶。 */
private val SoftMistDarkColorScheme = darkColorScheme(
    primary = AppPalette.SOFT_MIST.accentDark,
    onPrimary = Color(0xFF0F2530),
    primaryContainer = Color(0xFF1F4050),
    onPrimaryContainer = Color(0xFFD0E4EE),
    secondary = AppPalette.SOFT_MIST.accentDark,
    onSecondary = Color(0xFF0F2530),
    secondaryContainer = Color(0xFF1F4050),
    onSecondaryContainer = Color(0xFFD0E4EE),
    tertiary = AppPalette.SOFT_MIST.accentDark,
    onTertiary = Color(0xFF0F2530),
    tertiaryContainer = Color(0xFF1F4050),
    onTertiaryContainer = Color(0xFFD0E4EE),
    background = Color(0xFF121614),
    onBackground = Color(0xFFE2E8E4),
    surface = Color(0xFF181D1A),
    onSurface = Color(0xFFE2E8E4),
    surfaceVariant = Color(0xFF2A302C),
    onSurfaceVariant = Color(0xFFA8B4AC),
    surfaceTint = AppPalette.SOFT_MIST.accentDark,
    surfaceBright = Color(0xFF2E3430),
    surfaceDim = Color(0xFF121614),
    surfaceContainerLowest = Color(0xFF0E1210),
    surfaceContainerLow = Color(0xFF151A17),
    surfaceContainer = Color(0xFF1A1F1C),
    surfaceContainerHigh = Color(0xFF242A26),
    surfaceContainerHighest = Color(0xFF2E3430),
    inverseSurface = Color(0xFFE2E8E4),
    inverseOnSurface = Color(0xFF1A1F1C),
    inversePrimary = AppPalette.SOFT_MIST.accentLight,
    outline = Color(0xFF4E5A54),
    outlineVariant = Color(0xFF2E3830),
    scrim = Color(0xFF000000),
    error = Color(0xFFE57373),
    onError = Color(0xFF410E0B),
    errorContainer = Color(0xFF5C1A14),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** 「暖杏」浅色：暖米白底 + 柔和杏棕强调，番茄小说式温暖纸感。 */
private val WarmApricotLightColorScheme = lightColorScheme(
    primary = AppPalette.WARM_APRICOT.accentLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = WarmApricotContainer,
    onPrimaryContainer = WarmApricotOnContainer,
    secondary = AppPalette.WARM_APRICOT.accentLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = WarmApricotContainer,
    onSecondaryContainer = WarmApricotOnContainer,
    tertiary = AppPalette.WARM_APRICOT.accentLight,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = WarmApricotContainer,
    onTertiaryContainer = WarmApricotOnContainer,
    background = WarmApricotBg,
    onBackground = WarmApricotInk,
    surface = WarmApricotSurface,
    onSurface = WarmApricotInk,
    surfaceVariant = WarmApricotMid,
    onSurfaceVariant = WarmApricotMuted,
    surfaceTint = AppPalette.WARM_APRICOT.accentLight,
    surfaceBright = WarmApricotSurface,
    surfaceDim = WarmApricotHigh,
    surfaceContainerLowest = WarmApricotSurface,
    surfaceContainerLow = Color(0xFFFBF8F3),
    surfaceContainer = WarmApricotLow,
    surfaceContainerHigh = WarmApricotMid,
    surfaceContainerHighest = WarmApricotHigh,
    inverseSurface = WarmApricotInk,
    inverseOnSurface = Color(0xFFF5F0E8),
    inversePrimary = AppPalette.WARM_APRICOT.accentDark,
    outline = WarmApricotHairline,
    outlineVariant = WarmApricotHairlineSoft,
    scrim = Color(0xFF000000),
    error = Color(0xFFC2413B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

/** 「暖杏」深色：暖灰棕暗面，5 级 surface 台阶。 */
private val WarmApricotDarkColorScheme = darkColorScheme(
    primary = AppPalette.WARM_APRICOT.accentDark,
    onPrimary = Color(0xFF3A2A18),
    primaryContainer = Color(0xFF503A22),
    onPrimaryContainer = Color(0xFFF0DFC8),
    secondary = AppPalette.WARM_APRICOT.accentDark,
    onSecondary = Color(0xFF3A2A18),
    secondaryContainer = Color(0xFF503A22),
    onSecondaryContainer = Color(0xFFF0DFC8),
    tertiary = AppPalette.WARM_APRICOT.accentDark,
    onTertiary = Color(0xFF3A2A18),
    tertiaryContainer = Color(0xFF503A22),
    onTertiaryContainer = Color(0xFFF0DFC8),
    background = Color(0xFF16120E),
    onBackground = Color(0xFFEDE6DC),
    surface = Color(0xFF1C1814),
    onSurface = Color(0xFFEDE6DC),
    surfaceVariant = Color(0xFF2A231B),
    onSurfaceVariant = Color(0xFFBEB0A0),
    surfaceTint = AppPalette.WARM_APRICOT.accentDark,
    surfaceBright = Color(0xFF322B22),
    surfaceDim = Color(0xFF16120E),
    surfaceContainerLowest = Color(0xFF110F0B),
    surfaceContainerLow = Color(0xFF191510),
    surfaceContainer = Color(0xFF1E1A15),
    surfaceContainerHigh = Color(0xFF282219),
    surfaceContainerHighest = Color(0xFF322B22),
    inverseSurface = Color(0xFFEDE6DC),
    inverseOnSurface = Color(0xFF1E1A15),
    inversePrimary = AppPalette.WARM_APRICOT.accentLight,
    outline = Color(0xFF5C5040),
    outlineVariant = Color(0xFF362E24),
    scrim = Color(0xFF000000),
    error = Color(0xFFE57373),
    onError = Color(0xFF410E0B),
    errorContainer = Color(0xFF5C1A14),
    onErrorContainer = Color(0xFFF9DEDC),
)

/**
 * 把暗色方案转成 AMOLED 纯黑：只压暗容器色阶，主色/强调色/语义色全部保留，
 * 保证可读性和可交互元素的对比度。
 */
private fun ColorScheme.asAmoledPureBlack(): ColorScheme = copy(
    background = AmoledBlack,
    surface = AmoledSurface,
    surfaceBright = AmoledSurfaceHigh,
    surfaceDim = AmoledNearBlack,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledNearBlack,
    surfaceContainer = AmoledSurface,
    surfaceContainerHigh = AmoledSurfaceHigh,
    surfaceContainerHighest = AmoledSurfaceHigh,
)

/** palette → scheme 映射（不依赖 Context，可单元测试）。仅供 [buildColorScheme] 内部调用 + 测试入口。 */
internal fun paletteScheme(palette: AppPalette, darkTheme: Boolean): ColorScheme = when (palette) {
    AppPalette.PAPER_INK -> if (darkTheme) PaperInkDarkColorScheme else PaperInkLightColorScheme
    AppPalette.CLEAR_BLUE -> if (darkTheme) ClearBlueDarkColorScheme else ClearBlueLightColorScheme
    AppPalette.SOFT_MIST -> if (darkTheme) SoftMistDarkColorScheme else SoftMistLightColorScheme
    AppPalette.WARM_APRICOT -> if (darkTheme) WarmApricotDarkColorScheme else WarmApricotLightColorScheme
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: AppPalette = AppPalette.default,
    useDynamicColor: Boolean = false,
    amoledPureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = remember(darkTheme, palette, useDynamicColor, amoledPureBlack, context) {
        buildColorScheme(
            context = context,
            darkTheme = darkTheme,
            palette = palette,
            useDynamicColor = useDynamicColor,
            amoledPureBlack = amoledPureBlack,
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

private fun buildColorScheme(
    context: Context,
    darkTheme: Boolean,
    palette: AppPalette,
    useDynamicColor: Boolean,
    amoledPureBlack: Boolean,
): ColorScheme {
    val base = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        paletteScheme(palette, darkTheme)
    }
    return if (darkTheme && amoledPureBlack) base.asAmoledPureBlack() else base
}
