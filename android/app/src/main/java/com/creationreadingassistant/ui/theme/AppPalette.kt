package com.creationreadingassistant.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 应用外壳的配色主题。
 *
 * 配色只决定 Material 颜色令牌；组件、布局、字阶和动效不随配色切换。
 * 阅读器纸张与夜读仍由 ReaderSettings 独立管理，不读取这里的选择。
 */
enum class AppPalette(
    val storageId: String,
    val displayName: String,
    /** 该配色在浅色模式下的主色（用于色卡预览 + buildColorScheme primary 槽位） */
    val accentLight: Color,
    /** 该配色在深色模式下的主色 */
    val accentDark: Color,
) {
    PAPER_INK(
        storageId = "paper_ink",
        displayName = "纸墨",
        accentLight = Color(0xFF365C4A),
        accentDark = Color(0xFF8FAF9D),
    ),
    CLEAR_BLUE(
        storageId = "clear_blue",
        displayName = "清爽蓝",
        accentLight = Color(0xFF2762BF),
        accentDark = Color(0xFFA8C6FF),
    ),
    SOFT_MIST(
        storageId = "soft_mist",
        displayName = "雾青",
        accentLight = Color(0xFF4A6878),
        accentDark = Color(0xFFA3C4D4),
    ),
    WARM_APRICOT(
        storageId = "warm_apricot",
        displayName = "暖杏",
        accentLight = Color(0xFF806245),
        accentDark = Color(0xFFD4B896),
    );

    companion object {
        val default: AppPalette = CLEAR_BLUE

        fun fromStored(value: String?): AppPalette =
            entries.firstOrNull { it.storageId == value } ?: default
    }
}
