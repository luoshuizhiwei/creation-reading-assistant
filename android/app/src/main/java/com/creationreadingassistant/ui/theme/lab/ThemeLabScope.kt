package com.creationreadingassistant.ui.theme.lab

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.theme.AppCinnabar
import com.creationreadingassistant.ui.theme.LocalComponentSpec
import com.creationreadingassistant.ui.theme.LocalGlassPalette
import com.creationreadingassistant.ui.theme.ReaderPaperPalette
import com.creationreadingassistant.ui.theme.paperPalette
import com.creationreadingassistant.ui.theme.rememberCategoryTintDark

/**
 * 三屏样板的方向作用域：把方向的组件规格 / 空间密度 / 字阶 / 形状广播进子树。
 *
 * - 旧配色（[LocalThemeLabDirection] = null）下原样透传，零行为变化。
 * - 只供书架 / 设置首页包裹；阅读器不包裹（阅读区颜色只从 ReaderPaperPalette 取）。
 * - 玻璃高光层在样板内关闭（[LocalGlassPalette] = null）：纸感方向不叠玻璃拟态。
 *
 * @param settingsDensity true = 设置首页（基于「我的」紧凑令牌）；false = 书架（基于默认外壳令牌）。
 */
@Composable
fun ThemeLabScreenScope(
    settingsDensity: Boolean = false,
    content: @Composable () -> Unit,
) {
    val direction = LocalThemeLabDirection.current
    if (direction == null) {
        content()
        return
    }
    val spec = direction.componentSpec
    val layout = if (settingsDensity) direction.settingsLayout else direction.shelfLayout
    val typography = direction.shellTypography(MaterialTheme.typography)
    val shapes = remember(spec) {
        Shapes(
            extraSmall = RoundedCornerShape(spec.pedestalRadius),
            small = RoundedCornerShape(spec.pedestalRadius),
            medium = RoundedCornerShape(spec.listItemRadius),
            large = RoundedCornerShape(spec.cardRadius),
            extraLarge = RoundedCornerShape(topStart = spec.sheetRadius, topEnd = spec.sheetRadius),
        )
    }
    CompositionLocalProvider(
        LocalComponentSpec provides spec,
        LocalLayoutTokens provides layout,
        LocalGlassPalette provides null,
    ) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}

/**
 * 阅读器纸张解析（lab 感知）：仅「跟随外观」(follow) 被方向 remap 为方向纸；
 * 显式纸档（white / warm / green / night / sepia_dark / parchment / celadon / ink_night）
 * 与旧配色下一律走 legacy [paperPalette]，分毫不动 —— 这是回退与零回归的边界。
 */
@Composable
fun rememberLabPaperPalette(key: String, darkTheme: Boolean): ReaderPaperPalette {
    val direction = LocalThemeLabDirection.current
    return remember(direction, key, darkTheme) {
        if (direction != null && key == "follow") {
            if (darkTheme) direction.paperDark else direction.paperLight
        } else {
            paperPalette(key, darkTheme)
        }
    }
}

/** 当前方向的印章色（按卡面明暗取浅 / 深）；旧配色回退既有朱砂 [AppCinnabar]。 */
@Composable
fun labSealColor(): Color {
    val direction = LocalThemeLabDirection.current ?: return AppCinnabar
    val dark = rememberCategoryTintDark()
    return direction.sealColor(dark)
}