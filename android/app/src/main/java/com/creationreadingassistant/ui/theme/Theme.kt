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
        when (palette) {
            AppPalette.PAPER_INK -> if (darkTheme) PaperInkDarkColorScheme else PaperInkLightColorScheme
            AppPalette.CLEAR_BLUE -> if (darkTheme) ClearBlueDarkColorScheme else ClearBlueLightColorScheme
        }
    }
    return if (darkTheme && amoledPureBlack) base.asAmoledPureBlack() else base
}
