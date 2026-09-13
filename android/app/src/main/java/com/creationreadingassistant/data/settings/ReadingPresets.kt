package com.creationreadingassistant.data.settings

/**
 * 阅读设置预设（R3-P1）。
 *
 * 语义：预设只写**显式项**，且与本书覆盖共用同一 [ReaderOverrideKey] 口径，
 * 因此同一份 preset 既可以套用到**全局**，也可以套用到**某一本书**。
 * 「默认」预设是空覆盖 —— 套用它等于清除覆盖、回到结构默认。
 *
 * 数值必须与 [ReaderOverrideKey.read] 的落盘格式一致（Float 用 `toString()`，例如 `"32.0"`），
 * 否则会被 [ReaderOverrideKey.write] 判为非法而静默失效。
 */
data class ReadingPreset(
    val id: String,
    val label: String,
    /** 展示给用户的作用说明，必须写清会改动哪些项。 */
    val description: String,
    val values: PerBookOverrides,
)

object ReadingPresets {

    val DEFAULT = ReadingPreset(
        id = "default",
        label = "默认",
        description = "清除改动，回到全局 / 结构默认：25sp 字号、1.85 行距、22dp 页边距，纸张跟随外观。",
        values = emptyMap(),
    )

    val LARGE_FONT = ReadingPreset(
        id = "large_font",
        label = "大字号",
        description = "字号 32sp、行距 2.0；适合小屏或长时间阅读。",
        values = mapOf(
            ReaderOverrideKey.FONT_SIZE to "32.0",
            ReaderOverrideKey.LINE_HEIGHT to "2.0",
        ),
    )

    val EYE_CARE = ReadingPreset(
        id = "eye_care",
        label = "护眼",
        description = "暖纸背景、字号 28sp、行距 1.95；弱化对比度。",
        values = mapOf(
            ReaderOverrideKey.FONT_SIZE to "28.0",
            ReaderOverrideKey.LINE_HEIGHT to "1.95",
            ReaderOverrideKey.BACKGROUND to "warm",
        ),
    )

    val ALL: List<ReadingPreset> = listOf(DEFAULT, LARGE_FONT, EYE_CARE)

    fun fromId(id: String): ReadingPreset? = ALL.firstOrNull { it.id == id }

    /**
     * 把预设套用到给定设置上（纯函数）。
     * 「默认」预设会**清除这些键的改动**：先把可覆盖项恢复为结构默认，再叠加预设值，
     * 保证套用「默认」之后确实是基线，而不是残留上一个预设。
     */
    fun applyTo(current: ReaderSettings, preset: ReadingPreset): ReaderSettings {
        val baseline = resetOverridableKeys(current)
        return ReaderSettingsOverlay.apply(baseline, preset.values)
    }

    /** 只把**可覆盖项**恢复为结构默认，非可覆盖字段（亮度/TTS/护眼等）原样保留。 */
    private fun resetOverridableKeys(current: ReaderSettings): ReaderSettings {
        val defaults = ReaderSettings()
        var next = current
        ReaderOverrideKey.entries.forEach { key ->
            val value = key.read(defaults)
            next = key.write(next, value) ?: next
        }
        return next
    }
}
