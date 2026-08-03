package com.creationreadingassistant.ui.theme

/**
 * 应用外壳的配色主题。
 *
 * 配色只决定 Material 颜色令牌；组件、布局、字阶和动效不随配色切换。
 * 阅读器纸张与夜读仍由 ReaderSettings 独立管理，不读取这里的选择。
 */
enum class AppPalette(
    val storageId: String,
    val displayName: String,
) {
    PAPER_INK(
        storageId = "paper_ink",
        displayName = "纸墨",
    );

    companion object {
        val default: AppPalette = PAPER_INK

        fun fromStored(value: String?): AppPalette =
            entries.firstOrNull { it.storageId == value } ?: default
    }
}
