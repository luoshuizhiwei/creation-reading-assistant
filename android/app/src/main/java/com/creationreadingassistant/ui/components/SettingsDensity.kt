package com.creationreadingassistant.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.creationreadingassistant.ui.layout.LocalLayoutTokens

/**
 * 设置控件密度：紧凑（看书面板 / 微岛）与普通（外壳设置子页）。
 *
 * 统一组件库用同一套实现，通过密度参数切换行高、内边距、字号与图标底座尺寸，
 * 消除过去 Settings / Compact / Micro 三套并存、风格靠人维护的漂移。
 */
enum class SettingsDensity { COMPACT, REGULAR }

val LocalSettingsDensity = staticCompositionLocalOf { SettingsDensity.REGULAR }

/**
 * 密度度量：所有统一设置组件的尺寸/字号单一来源。
 * COMPACT 复用 LayoutTokens 里的 compact* 行高；REGULAR 复用常规行高。
 */
data class SettingsMetrics(
    val singleLineRowHeight: Dp,
    val supportingRowHeight: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val titleSize: TextUnit,
    /** 行标题行盒高度。必须与 titleSize 一起给：只改字号不改行高，行盒高度不变，观感也就没变。 */
    val titleLineHeight: TextUnit,
    val subtitleSize: TextUnit,
    val subtitleLineHeight: TextUnit,
    val iconPedestalSize: Dp,
    val iconSize: Dp,
    val showIconPedestal: Boolean,
)

@Composable
@ReadOnlyComposable
fun settingsMetrics(density: SettingsDensity = LocalSettingsDensity.current): SettingsMetrics {
    val layout = LocalLayoutTokens.current
    return if (density == SettingsDensity.COMPACT) {
        SettingsMetrics(
            singleLineRowHeight = layout.compactSingleLineRowHeight,
            supportingRowHeight = layout.compactSupportingRowHeight,
            horizontalPadding = 14.dp,
            verticalPadding = 6.dp,
            // 14.5sp 而非 13.5sp：与 CompactSettingRow 当前实际渲染的字号对齐，
            // 这样「我的」首页（Compact 族）与子页（SettingRow 族）取到同一个行标题字号。
            titleSize = 14.5.sp,
            // 22sp / 20sp：等于 CompactSettingRow 当前的 bodyMedium(14.5sp) 与 bodySmall(11.5sp) 行盒，
            // 这样 SettingRow 族在 COMPACT 下与 Compact 族逐项对齐。
            titleLineHeight = 22.sp,
            subtitleSize = 11.5.sp,
            subtitleLineHeight = 20.sp,
            iconPedestalSize = 26.dp,
            iconSize = 14.dp,
            showIconPedestal = true,
        )
    } else {
        SettingsMetrics(
            singleLineRowHeight = layout.singleLineRowHeight,
            supportingRowHeight = layout.supportingRowHeight,
            horizontalPadding = layout.cardPadding,
            verticalPadding = layout.relatedGap,
            // 16sp / 26sp / 13sp / 20sp：逐项等于 bodyLarge 与 bodySmall 的现值，
            // 保证未 provide 密度的调用方（阅读器面板、灵感页、统计页）观感不变。
            titleSize = 16.sp,
            titleLineHeight = 26.sp,
            subtitleSize = 13.sp,
            subtitleLineHeight = 20.sp,
            iconPedestalSize = 32.dp,
            iconSize = 17.dp,
            showIconPedestal = false,
        )
    }
}
