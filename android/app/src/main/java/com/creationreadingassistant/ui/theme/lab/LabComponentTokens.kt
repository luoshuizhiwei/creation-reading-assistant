package com.creationreadingassistant.ui.theme.lab

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.layout.CompactSettingsLayoutTokens
import com.creationreadingassistant.ui.layout.DefaultLayoutTokens
import com.creationreadingassistant.ui.layout.LayoutTokens
import com.creationreadingassistant.ui.theme.ComponentSpec
import com.creationreadingassistant.ui.theme.DefaultComponentSpec
import com.creationreadingassistant.ui.theme.DisplayFontFamily
import com.creationreadingassistant.ui.theme.ReaderPaperPalette

// ─────────────────────────────────────────────────────────────────────────────
// Component 层：方向 → 组件规格 / 空间密度 / 字阶 / 印章 / 阅读纸张 / 阅读器 chrome 形状。
// 只被三屏样板消费（ThemeLabScreenScope / 阅读器 chrome），不写回全局默认。
// ─────────────────────────────────────────────────────────────────────────────

/** 方向组件规格：在默认外壳规格上改形状 / 边框 / 投影 / 材质开关。 */
val ThemeLabDirection.componentSpec: ComponentSpec
    get() = when (this) {
        ThemeLabDirection.XUAN_GRAY -> DefaultComponentSpec.copy(
            cardRadius = 6.dp, sheetRadius = 12.dp, pillRadius = 4.dp, listItemRadius = 4.dp,
            cardShape = RoundedCornerShape(6.dp),
            sheetShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            listItemShape = RoundedCornerShape(4.dp),
            pillShape = RoundedCornerShape(4.dp),
            floatingBarShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
            cardElevation = 0.dp, cardElevationAmbient = 0.dp,
            glassEnabled = false,
            borderWidth = 0.6.dp, borderSubtle = true, dividerThickness = 0.6.dp,
            islandRadius = 6.dp, dockRadius = 6.dp, pedestalRadius = 4.dp, hintRadius = 6.dp,
            hairlineBorderWidth = 0.6.dp, hairlineAlpha = 0.5f,
        )
        ThemeLabDirection.YIN_SEAL -> DefaultComponentSpec.copy(
            cardRadius = 2.dp, sheetRadius = 4.dp, pillRadius = 2.dp, listItemRadius = 2.dp,
            cardShape = RoundedCornerShape(2.dp),
            sheetShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
            listItemShape = RoundedCornerShape(2.dp),
            pillShape = RoundedCornerShape(2.dp),
            floatingBarShape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 2.dp, bottomEnd = 2.dp),
            cardElevation = 0.dp, cardElevationAmbient = 0.dp,
            glassEnabled = false,
            borderWidth = 1.dp, borderSubtle = false, dividerThickness = 1.dp,
            islandRadius = 2.dp, dockRadius = 2.dp, pedestalRadius = 2.dp, hintRadius = 2.dp,
            hairlineBorderWidth = 1.dp, hairlineAlpha = 0.6f,
        )
        ThemeLabDirection.CHEN_GUANG -> DefaultComponentSpec.copy(
            cardRadius = 16.dp, sheetRadius = 24.dp, pillRadius = 999.dp, listItemRadius = 12.dp,
            cardShape = RoundedCornerShape(16.dp),
            sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            listItemShape = RoundedCornerShape(12.dp),
            pillShape = RoundedCornerShape(999.dp),
            floatingBarShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            cardElevation = 0.dp, cardElevationAmbient = 1.dp,
            glassEnabled = false,
            borderWidth = 0.6.dp, borderSubtle = true, dividerThickness = 0.6.dp,
            islandRadius = 14.dp, dockRadius = 18.dp, pedestalRadius = 10.dp, hintRadius = 12.dp,
            hairlineBorderWidth = 0.6.dp, hairlineAlpha = 0.35f,
        )
        ThemeLabDirection.QING_CI -> DefaultComponentSpec.copy(
            cardRadius = 10.dp, sheetRadius = 16.dp, pillRadius = 999.dp, listItemRadius = 8.dp,
            cardShape = RoundedCornerShape(10.dp),
            sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            listItemShape = RoundedCornerShape(8.dp),
            pillShape = RoundedCornerShape(999.dp),
            floatingBarShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 10.dp, bottomEnd = 10.dp),
            cardElevation = 0.dp, cardElevationAmbient = 0.dp,
            glassEnabled = false,
            borderWidth = 0.6.dp, borderSubtle = true, dividerThickness = 0.6.dp,
            islandRadius = 10.dp, dockRadius = 12.dp, pedestalRadius = 8.dp, hintRadius = 8.dp,
            hairlineBorderWidth = 0.6.dp, hairlineAlpha = 0.4f,
        )
        // 知识花园绿：圆角卡片（12dp 级）+ 全圆角胶囊，柔和灰绿；方角为阴刻独有，这里不用。
        ThemeLabDirection.GARDEN -> DefaultComponentSpec.copy(
            cardRadius = 12.dp, sheetRadius = 20.dp, pillRadius = 999.dp, listItemRadius = 10.dp,
            cardShape = RoundedCornerShape(12.dp),
            sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            listItemShape = RoundedCornerShape(10.dp),
            pillShape = RoundedCornerShape(999.dp),
            floatingBarShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
            cardElevation = 0.dp, cardElevationAmbient = 1.dp,
            glassEnabled = false,
            borderWidth = 0.6.dp, borderSubtle = true, dividerThickness = 0.6.dp,
            islandRadius = 12.dp, dockRadius = 16.dp, pedestalRadius = 10.dp, hintRadius = 12.dp,
            hairlineBorderWidth = 0.6.dp, hairlineAlpha = 0.35f,
        )
    }

/** 书架空间密度（基于默认外壳令牌）。 */
val ThemeLabDirection.shelfLayout: LayoutTokens
    get() = when (this) {
        ThemeLabDirection.XUAN_GRAY -> DefaultLayoutTokens.copy(
            pageHorizontal = 20.dp, pageVertical = 16.dp, contentGap = 16.dp, gridGap = 16.dp,
        )
        ThemeLabDirection.YIN_SEAL -> DefaultLayoutTokens.copy(
            pageHorizontal = 16.dp, pageVertical = 12.dp, contentGap = 12.dp, gridGap = 12.dp,
        )
        ThemeLabDirection.CHEN_GUANG -> DefaultLayoutTokens
        ThemeLabDirection.QING_CI -> DefaultLayoutTokens.copy(
            pageHorizontal = 14.dp, pageVertical = 10.dp, contentGap = 8.dp, gridGap = 10.dp,
        )
        ThemeLabDirection.GARDEN -> DefaultLayoutTokens
    }

/** 设置首页空间密度（基于「我的」紧凑令牌；行高不破 48dp 触控底线）。 */
val ThemeLabDirection.settingsLayout: LayoutTokens
    get() = when (this) {
        ThemeLabDirection.XUAN_GRAY -> CompactSettingsLayoutTokens.copy(
            pageHorizontal = 18.dp, contentGap = 12.dp, cardPadding = 16.dp,
            compactSingleLineRowHeight = 52.dp, compactSupportingRowHeight = 60.dp,
        )
        ThemeLabDirection.YIN_SEAL -> CompactSettingsLayoutTokens
        ThemeLabDirection.CHEN_GUANG -> CompactSettingsLayoutTokens
        ThemeLabDirection.QING_CI -> CompactSettingsLayoutTokens.copy(
            pageHorizontal = 12.dp, contentGap = 6.dp, cardPadding = 10.dp,
            compactSingleLineRowHeight = 48.dp, compactSupportingRowHeight = 52.dp,
        )
        ThemeLabDirection.GARDEN -> CompactSettingsLayoutTokens
    }

/**
 * 方向字阶：只改展示标题与书名两项的字体 / 字号 / 字距，保留基础字重
 * （暗色降档由 appTypography(dark) 先行处理，这里不覆盖 fontWeight）。
 */
fun ThemeLabDirection.shellTypography(base: Typography): Typography = when (this) {
    ThemeLabDirection.XUAN_GRAY -> base.copy(
        headlineLarge = base.headlineLarge.copy(
            fontFamily = DisplayFontFamily, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 1.2.sp,
        ),
        titleSmall = base.titleSmall.copy(fontFamily = DisplayFontFamily),
    )
    ThemeLabDirection.YIN_SEAL -> base.copy(
        headlineLarge = base.headlineLarge.copy(
            fontFamily = DisplayFontFamily, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = 0.6.sp,
        ),
        titleSmall = base.titleSmall.copy(fontFamily = DisplayFontFamily),
    )
    ThemeLabDirection.CHEN_GUANG -> base.copy(
        headlineLarge = base.headlineLarge.copy(
            fontFamily = DisplayFontFamily, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = 0.2.sp,
        ),
    )
    ThemeLabDirection.QING_CI -> base.copy(
        headlineLarge = base.headlineLarge.copy(
            fontSize = 22.sp, lineHeight = 30.sp, letterSpacing = 0.sp,
        ),
    )
    // 知识花园绿：圆润现代，展示标题回无衬线（区别于宋版/雕版的衬线签名）。
    ThemeLabDirection.GARDEN -> base.copy(
        headlineLarge = base.headlineLarge.copy(
            fontFamily = FontFamily.SansSerif, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp,
        ),
    )
}

/** 非文本强调色（进度条 / 滑杆 / 图标，门槛 ≥3.0）。花园用采样按钮常态绿 #759271；其余方向用主色。 */
fun ThemeLabDirection.progressAccent(dark: Boolean): Color = when (this) {
    ThemeLabDirection.GARDEN -> if (dark) Color(0xFF8FAE8A) else Color(0xFF759271)
    else -> (if (dark) primitivesDark else primitivesLight).accent
}

/** 印章色（明 / 暗）。只用于「读毕」藏书印与方向签名标记，不作通用强调。 */
fun ThemeLabDirection.sealColor(dark: Boolean): Color =
    (if (dark) primitivesDark else primitivesLight).seal

// ── 阅读纸张（仅 remap「跟随外观」；显式纸档 white/warm/… 保持 legacy 分毫不动）──
// 正文 / chrome 颜色仍只从 ReaderPaperPalette 取，App 主题不渗入阅读区。
// 5 色批注沿用 legacy 白纸 / 夜读批注实色（语义顺序黄红绿蓝紫不变）。

private val LabLightHighlights = listOf(
    Color(0xFFE6C95A), Color(0xFFD08B7A), Color(0xFF7FA86B), Color(0xFF6E8FC0), Color(0xFFA884B0),
)
private val LabDarkHighlights = listOf(
    Color(0xFFE6C95A), Color(0xFFD08B7A), Color(0xFF8FA86B), Color(0xFF8EA3D0), Color(0xFFC0A0C8),
)

private fun labPaper(
    key: String,
    bg: Color, fg: Color, fgMuted: Color, accent: Color,
    outlineVariant: Color, outline: Color, panel: Color, panelStrong: Color,
    onAccent: Color, isLight: Boolean,
): ReaderPaperPalette = ReaderPaperPalette(
    key = key, bg = bg, fg = fg, fgMuted = fgMuted, accent = accent,
    outlineVariant = outlineVariant, outline = outline, panel = panel, panelStrong = panelStrong,
    onAccent = onAccent, isLight = isLight,
    highlightColors = if (isLight) LabLightHighlights else LabDarkHighlights,
)

val ThemeLabDirection.paperLight: ReaderPaperPalette
    get() = when (this) {
        ThemeLabDirection.XUAN_GRAY -> labPaper(
            "lab_xuan_gray_light",
            bg = Color(0xFFF7F7F4), fg = Color(0xFF22262A), fgMuted = Color(0xFF555B62), accent = Color(0xFF33544D),
            outlineVariant = Color(0xFFE2E2DC), outline = Color(0xFF7A807A),
            panel = Color(0xFFEFEFEB), panelStrong = Color(0xFFE3E3DD), onAccent = Color(0xFFFFFFFF), isLight = true,
        )
        ThemeLabDirection.YIN_SEAL -> labPaper(
            "lab_yin_seal_light",
            bg = Color(0xFFF8F5EE), fg = Color(0xFF211E19), fgMuted = Color(0xFF57514A), accent = Color(0xFF26221C),
            outlineVariant = Color(0xFFE4DECF), outline = Color(0xFF857D6E),
            panel = Color(0xFFF0EBE0), panelStrong = Color(0xFFE5DFD0), onAccent = Color(0xFFF5F2EA), isLight = true,
        )
        ThemeLabDirection.CHEN_GUANG -> labPaper(
            "lab_chen_guang_light",
            bg = Color(0xFFF8F0DE), fg = Color(0xFF32281C), fgMuted = Color(0xFF5F5240), accent = Color(0xFF7E542C),
            outlineVariant = Color(0xFFE7DCC2), outline = Color(0xFF8A7960),
            panel = Color(0xFFF1E7D0), panelStrong = Color(0xFFE7DABE), onAccent = Color(0xFFFFFFFF), isLight = true,
        )
        ThemeLabDirection.QING_CI -> labPaper(
            "lab_qing_ci_light",
            bg = Color(0xFFEFF4F0), fg = Color(0xFF1F2A26), fgMuted = Color(0xFF4F6159), accent = Color(0xFF2F5D50),
            outlineVariant = Color(0xFFDAE5DD), outline = Color(0xFF6E8579),
            panel = Color(0xFFE5EDE8), panelStrong = Color(0xFFD8E3DB), onAccent = Color(0xFFFFFFFF), isLight = true,
        )
        ThemeLabDirection.GARDEN -> labPaper(
            "lab_garden_light",
            bg = Color(0xFFFBF9F3), fg = Color(0xFF3A4A38), fgMuted = Color(0xFF5A6B57), accent = Color(0xFF4A6B4A),
            outlineVariant = Color(0xFFDCE5DA), outline = Color(0xFF75826F),
            panel = Color(0xFFF1EFE6), panelStrong = Color(0xFFE8EFE8), onAccent = Color(0xFFFFFFFF), isLight = true,
        )
    }

val ThemeLabDirection.paperDark: ReaderPaperPalette
    get() = when (this) {
        ThemeLabDirection.XUAN_GRAY -> labPaper(
            "lab_xuan_gray_dark",
            bg = Color(0xFF15171A), fg = Color(0xFFDADDE0), fgMuted = Color(0xFF9BA2A9), accent = Color(0xFF9DBBB0),
            outlineVariant = Color(0xFF26292D), outline = Color(0xFF6E7672),
            panel = Color(0xFF1C1F22), panelStrong = Color(0xFF25282C), onAccent = Color(0xFF12211C), isLight = false,
        )
        ThemeLabDirection.YIN_SEAL -> labPaper(
            "lab_yin_seal_dark",
            bg = Color(0xFF131109), fg = Color(0xFFE9E3D5), fgMuted = Color(0xFFAEA695), accent = Color(0xFFE6DECB),
            outlineVariant = Color(0xFF2A251D), outline = Color(0xFF6E6656),
            panel = Color(0xFF1B1813), panelStrong = Color(0xFF25211A), onAccent = Color(0xFF1C1916), isLight = false,
        )
        ThemeLabDirection.CHEN_GUANG -> labPaper(
            "lab_chen_guang_dark",
            bg = Color(0xFF1A140D), fg = Color(0xFFEFE5D3), fgMuted = Color(0xFFC0B098), accent = Color(0xFFD9B48A),
            outlineVariant = Color(0xFF2C251A), outline = Color(0xFF7A6C56),
            panel = Color(0xFF221C13), panelStrong = Color(0xFF2C2519), onAccent = Color(0xFF33230F), isLight = false,
        )
        ThemeLabDirection.QING_CI -> labPaper(
            "lab_qing_ci_dark",
            bg = Color(0xFF0F1716), fg = Color(0xFFDDE6E1), fgMuted = Color(0xFF9EB1A9), accent = Color(0xFF93C0B0),
            outlineVariant = Color(0xFF22302B), outline = Color(0xFF5F756C),
            panel = Color(0xFF16201E), panelStrong = Color(0xFF1F2B27), onAccent = Color(0xFF0F2420), isLight = false,
        )
        ThemeLabDirection.GARDEN -> labPaper(
            "lab_garden_dark",
            bg = Color(0xFF161A14), fg = Color(0xFFE4EAE0), fgMuted = Color(0xFFA8B4A2), accent = Color(0xFFA9C4A0),
            outlineVariant = Color(0xFF333D2E), outline = Color(0xFF6E7A68),
            panel = Color(0xFF1E241C), panelStrong = Color(0xFF2A3226), onAccent = Color(0xFF1A2416), isLight = false,
        )
    }

// ── 阅读器 chrome 形状语言（颜色恒从 paper 取，这里只给形状 / 密度 / 墨块开关）──

data class ReaderChromeStyle(
    val pedestalRadius: Dp,
    val panelRadius: Dp,
    val floatingBarShape: Shape,
    /** true = 顶栏按钮用「墨块反白」（paper.fg 底 + paper.bg 图标），阴刻方向签名。 */
    val inkBlockButtons: Boolean,
    val hairlineAlpha: Float,
    val borderWidth: Dp,
)

val ThemeLabDirection.readerChromeStyle: ReaderChromeStyle
    get() = when (this) {
        ThemeLabDirection.XUAN_GRAY -> ReaderChromeStyle(
            pedestalRadius = 4.dp, panelRadius = 6.dp,
            floatingBarShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
            inkBlockButtons = false, hairlineAlpha = 0.5f, borderWidth = 0.6.dp,
        )
        ThemeLabDirection.YIN_SEAL -> ReaderChromeStyle(
            pedestalRadius = 2.dp, panelRadius = 2.dp,
            floatingBarShape = RoundedCornerShape(2.dp),
            inkBlockButtons = true, hairlineAlpha = 0.6f, borderWidth = 1.dp,
        )
        ThemeLabDirection.CHEN_GUANG -> ReaderChromeStyle(
            pedestalRadius = 12.dp, panelRadius = 18.dp,
            floatingBarShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            inkBlockButtons = false, hairlineAlpha = 0.35f, borderWidth = 0.6.dp,
        )
        ThemeLabDirection.QING_CI -> ReaderChromeStyle(
            pedestalRadius = 8.dp, panelRadius = 12.dp,
            floatingBarShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 10.dp, bottomEnd = 10.dp),
            inkBlockButtons = false, hairlineAlpha = 0.4f, borderWidth = 0.6.dp,
        )
        ThemeLabDirection.GARDEN -> ReaderChromeStyle(
            pedestalRadius = 10.dp, panelRadius = 16.dp,
            floatingBarShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
            inkBlockButtons = false, hairlineAlpha = 0.35f, borderWidth = 0.6.dp,
        )
    }