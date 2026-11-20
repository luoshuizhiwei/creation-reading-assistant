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
    ),
    DUSK_PLUM(
        storageId = "dusk_plum",
        displayName = "黛檀",
        accentLight = Color(0xFF5E4868),
        accentDark = Color(0xFFD2BDDC),
    ),
    PINE_MIST(
        storageId = "pine_mist",
        displayName = "松玉",
        accentLight = Color(0xFF2C5E53),
        accentDark = Color(0xFF90C7BA),
    ),

    // ── Theme Lab v2 · P1 候选方向（打样）──
    // 色值镜像 ui/theme/lab/ThemeLabDirection.kt 的 primitive accent（单一真源在 lab，
    // 这里仅供色卡预览与 buildColorScheme primary 槽位）；选回任一旧 entry 即整体回退。
    LAB_XUAN_GRAY(
        storageId = "lab_xuan_gray",
        displayName = "宣纸冷灰",
        accentLight = Color(0xFF33544D),
        accentDark = Color(0xFF9DBBB0),
    ),
    LAB_YIN_SEAL(
        storageId = "lab_yin_seal",
        displayName = "阴刻印章",
        accentLight = Color(0xFF26221C),
        accentDark = Color(0xFFE6DECB),
    ),
    LAB_CHEN_GUANG(
        storageId = "lab_chen_guang",
        displayName = "晨光米黄",
        accentLight = Color(0xFF7E542C),
        accentDark = Color(0xFFD9B48A),
    ),
    LAB_QING_CI(
        storageId = "lab_qing_ci",
        displayName = "青瓷冷翠",
        accentLight = Color(0xFF2F5D50),
        accentDark = Color(0xFF93C0B0),
    ),

    // ── Theme Lab v2 · P2 新增：知识花园绿（新用户默认）──
    // 采样自「知识花园」桌面真机截图：主绿 #4A6B4A / 按钮常态绿 #759271（非文本强调）/
    // 选中浅绿 #EAF1EA·#E8EFE8 / 暖米白纸 #FBF9F3·#FCFAF4。圆角卡片语言（区别于阴刻方角）。
    LAB_GARDEN(
        storageId = "lab_garden",
        displayName = "知识花园绿",
        accentLight = Color(0xFF4A6B4A),
        accentDark = Color(0xFFA9C4A0),
    );

    companion object {
        // P2：新用户默认改为知识花园绿。fromStored 仅在从未存值时用默认；
        // 已存值老用户原样保留（不强制迁移）。
        val default: AppPalette = LAB_GARDEN

        fun fromStored(value: String?): AppPalette =
            entries.firstOrNull { it.storageId == value } ?: default
    }
}
