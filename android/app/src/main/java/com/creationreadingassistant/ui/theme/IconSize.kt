package com.creationreadingassistant.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 图标尺寸令牌。
 *
 * 统一取代散点硬编码的 `modifier.size(N.dp)`，仅覆盖功能性 Material 图标常用档位（16~24dp）。
 * 特殊尺寸保留原值不纳入令牌：空态 LineArt 插画（44dp+）、引导页 hero 图标（36dp）、
 * 书架/个人页特征图标（28/30/32dp）等有意为之的差异化尺寸。
 *
 * 映射约定（来自统一走查）：
 * - 16dp（密集列表行内）→ [Compact]
 * - 18dp（列表尾图标 / 行内）→ [Small]
 * - 20/22dp（标准控件）→ [Medium]
 * - 24dp（顶栏 / 默认图标按钮）→ [Large]
 */
object AppIconSize {
    /** 密集列表行内图标 */
    val Compact = 16.dp

    /** 列表尾图标 / 行内图标 */
    val Small = 18.dp

    /** 标准控件图标 */
    val Medium = 20.dp

    /** 顶栏 / 默认图标按钮 */
    val Large = 24.dp
}
