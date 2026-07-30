package com.creationreadingassistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 「墨韵·素笺」主题 —— 严格对齐 mobile/src/md3-base.css 的设计令牌。
 * 纸(paper) 作背景、墨(ink) 作前景、靛青印章(indigo-seal) 作主色。
 */

// 浅色令牌（来自 md3-base.css :root）
private val Paper = Color(0xFFFAF8F2)        // --paper 宣纸底（主表面）
private val Paper2 = Color(0xFFF3EFE6)       // --paper-2 次级纸面
private val Paper3 = Color(0xFFE8E2D7)       // --paper-3 三级纸面
private val Ink = Color(0xFF1A1917)          // --ink 墨色正文
private val InkSoft = Color(0xFF4A4743)      // --ink-soft 次级文字
private val Muted = Color(0xFF76726A)        // --muted 弱化/说明文字
private val Line = Color(0x121A1917)         // --line rgba(26,25,23,0.07) 发丝线
private val IndigoSeal = Color(0xFF3A5670)   // --indigo-seal 靛青印章（主色）
private val IndigoSealLight = Color(0xFF5A7A94) // --indigo-seal-light 浅靛青
private val IndigoSealDark = Color(0xFF2A4054)  // --indigo-seal-dark 深靛青

// 语义强调色（来自 md3-base.css）
val AppSuccess = Color(0xFF4A6E3F)           // --md3-success
val AppWarning = Color(0xFFA05F12)           // --md3-warning
val AppError = Color(0xFFB3261E)             // --md3-error
val AppStreak = IndigoSeal                   // --md3-streak 连续阅读

/**
 * 朱砂。**只用于印章标记**，不作通用强调色。
 *
 * 印泥的红在文人物料里有确定含义——盖了印才算数。把它限定在"已读完"这类完成态标记上，
 * 它就是信息；一旦拿去当按钮色、当高亮色，就退化成一个普通的暖红点缀，
 * 整套配色也会滑向"米底 + 衬线 + 陶土红"那个到处都是的样子。
 */
val AppCinnabar = Color(0xFF9E3D32)

// 暗色令牌（来自 md3-base.css :root[data-mobile-theme="dark"]）
private val PaperDark = Color(0xFF141311)     // --paper
private val InkDark = Color(0xFFF3EFE6)      // --ink
private val MutedDark = Color(0xFF8A847A)    // --muted
private val LineDark = Color(0x14F3EFE6)     // --line rgba(243,239,230,0.08)
private val IndigoSealDarkFg = Color(0xFF7C8FD6) // 提亮靛青（暗色主色，保对比度）

// 纸面层级。MD3 的 surfaceContainer* 是一整组「容器台阶」，NavigationBar、Card、
// BottomSheet 等组件各自取其中一档做底色。**只设 surface / surfaceVariant 是不够的**：
// 未覆盖的角色会回落到 MD3 基线值（surfaceContainer 基线是 #F3EDF7 的淡紫），
// 于是底部导航栏会突兀地泛紫，和整套纸墨配色完全脱节。这里按纸的明度排成阶梯。
private val PaperBright = Color(0xFFFDFCF8)  // 最亮：浮起的面板
private val PaperLowest = Color(0xFFFFFFFF)  // 纯白：需要与纸底拉开的卡片
private val PaperHigh = Color(0xFFEDE8DD)    // 偏深纸面
private val PaperDim = Color(0xFFE5E0D6)     // 最深：压暗的底
private val IndigoContainer = Color(0xFFDDE3EA)      // 靛青在纸上的淡染，不透明
private val IndigoContainerSoft = Color(0xFFE6EAEF)

// 单套外壳（T1 收敛后唯一外壳）：冷调低彩度纸白底。颜色严格取自设计实施稿 §2.1（已冻结）。
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3D5A80),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE4EE),
    onPrimaryContainer = Color(0xFF26354A),
    // §2.1：secondary 已移除，统一回落到主色（靛青），避免赭石副色残留
    secondary = Color(0xFF3D5A80),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE4EE),
    onSecondaryContainer = Color(0xFF26354A),
    tertiary = Color(0xFF3D5A80),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE4EE),
    onTertiaryContainer = Color(0xFF26354A),
    background = Color(0xFFF2F4F7),
    onBackground = Color(0xFF1B1E23),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1E23),
    surfaceVariant = Color(0xFFEAEDEF),
    onSurfaceVariant = Color(0xFF5A606A),
    surfaceTint = Color(0xFF3D5A80),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFEAEDEF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F4F7),
    surfaceContainer = Color(0xFFEAEDEF),
    surfaceContainerHigh = Color(0xFFE2E5EA),
    surfaceContainerHighest = Color(0xFFDCE4EE),
    inverseSurface = Color(0xFF1B1D22),
    inverseOnSurface = Color(0xFFE8EAEE),
    inversePrimary = Color(0xFF8AA6D8),
    outline = Color(0xFFC3C8D0),
    outlineVariant = Color(0xFFE2E5EA),
    scrim = Color(0xFF000000),
    error = Color(0xFFC2413B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

// 夜间同样要排满容器台阶，否则暗色下组件会回落到基线深紫。
private val InkLowest = Color(0xFF0E0D0C)
private val InkContainer = Color(0xFF1C1A18)
private val InkHigh = Color(0xFF24221F)
private val InkHighest = Color(0xFF2E2B27)
private val InkBright = Color(0xFF3A3733)
private val IndigoContainerDark = Color(0xFF2A3446)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AA6D8),
    onPrimary = Color(0xFF121316),
    primaryContainer = Color(0xFF26354A),
    onPrimaryContainer = Color(0xFFDCE4EE),
    secondary = Color(0xFF8AA6D8),
    onSecondary = Color(0xFF121316),
    secondaryContainer = Color(0xFF26354A),
    onSecondaryContainer = Color(0xFFDCE4EE),
    tertiary = Color(0xFF8AA6D8),
    onTertiary = Color(0xFF121316),
    tertiaryContainer = Color(0xFF26354A),
    onTertiaryContainer = Color(0xFFDCE4EE),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE8EAEE),
    surface = Color(0xFF1B1D22),
    onSurface = Color(0xFFE8EAEE),
    surfaceVariant = Color(0xFF25282E),
    onSurfaceVariant = Color(0xFF9AA0AA),
    surfaceTint = Color(0xFF8AA6D8),
    surfaceBright = Color(0xFF25282E),
    surfaceDim = Color(0xFF121316),
    surfaceContainerLowest = Color(0xFF1B1D22),
    surfaceContainerLow = Color(0xFF16181C),
    surfaceContainer = Color(0xFF1B1D22),
    surfaceContainerHigh = Color(0xFF25282E),
    surfaceContainerHighest = Color(0xFF2A2D33),
    inverseSurface = Color(0xFFE8EAEE),
    inverseOnSurface = Color(0xFF121316),
    inversePrimary = Color(0xFF3D5A80),
    outline = Color(0xFF3A3E45),
    outlineVariant = Color(0xFF2A2D33),
    scrim = Color(0xFF000000),
    error = Color(0xFFE57373),
    onError = Color(0xFF410E0B),
    errorContainer = Color(0xFF5C1A14),
    onErrorContainer = Color(0xFFF9DEDC),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
