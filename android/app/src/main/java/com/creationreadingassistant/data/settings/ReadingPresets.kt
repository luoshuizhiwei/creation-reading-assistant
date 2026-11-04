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
     * 不属于视觉预设的兼容性开关键。
     *
     * 这些键仍可被本书覆盖（见 [ReaderOverrideKey]），但不参与预设重置、
     * 也不被「套用默认预设」清除 —— 清除它们要用
     * [PerBookSettingsStore.clearAllOverrides]（「全部跟随全局」）
     * 或 [resetAllOverridableKeys]（「恢复全局默认」）。
     *
     * 声明在 [PRESET_OWNED_KEYS] **之前**：后者由本集合派生，Kotlin 对象属性
     * 按声明顺序初始化。
     */
    val ENGINE_KEYS: Set<ReaderOverrideKey> = setOf(
        ReaderOverrideKey.PAGER_ENGINE_MODE,
        ReaderOverrideKey.EPUB_PAGER_ENGINE_MODE,
    )

    /**
     * **视觉预设负责的覆盖键集合**（D-2-A）。
     *
     * 语义边界：预设负责「排版与显示」观感（字号、行距、纸张、页眉页脚……），
     * **不负责**兼容性开关 —— 分页引擎是「某本书分页异常时的救急开关」，
     * 把它混进视觉预设会导致「套用一次大字号，用户为某本书设的引擎回退被静默抹掉」。
     *
     * 为什么必须是**显式集合**而不是 `ReaderOverrideKey.entries`：
     * 后者是「全部可覆盖项」，会随枚举增长而扩张。D-2-A 新增两个引擎键后，
     * 若继续用 `entries` 做预设清理范围，预设就会开始重置引擎模式 ——
     * 这正是本任务要防的静默副作用。
     *
     * 由 [ReaderOverrideKey.entries] 减去 [ENGINE_KEYS] 派生，因此：
     * - 新增**视觉**项时自动纳入（保持既有「预设负责全部视觉项」语义）；
     * - 新增**引擎/兼容**项时必须显式加进 [ENGINE_KEYS]，否则会被测试
     *   `the preset owned key set covers every non engine overridable key` 拦下。
     */
    val PRESET_OWNED_KEYS: Set<ReaderOverrideKey> = ReaderOverrideKey.entries.toSet() - ENGINE_KEYS

    /**
     * 把预设套用到给定设置上（纯函数）。
     *
     * 「默认」预设会**清除预设负责键的改动**：先把这些键恢复为结构默认，再叠加预设值，
     * 保证套用「默认」之后确实是基线，而不是残留上一个预设。
     *
     * **不触碰** [ENGINE_KEYS]：分页引擎模式保持调用前的值（D-2-A）。
     */
    fun applyTo(current: ReaderSettings, preset: ReadingPreset): ReaderSettings {
        val baseline = resetKeys(current, PRESET_OWNED_KEYS)
        return ReaderSettingsOverlay.apply(baseline, preset.values)
    }

    /**
     * 「恢复全局默认」用的显式重置：把**全部可覆盖项**（含两个引擎键）恢复为结构默认。
     *
     * 与 [applyTo] 的区别（D-2-A 的核心拆分）：
     * - [applyTo] 是**视觉预设**，只管 [PRESET_OWNED_KEYS]；
     * - 本函数是**显式全局恢复**，覆盖 [ReaderOverrideKey.entries] 全部项。
     *
     * 不复用 [DEFAULT] 的隐式路径，是为了让「恢复默认会不会重置引擎」
     * 成为一个显式、可测试的决定，而不是「碰巧取决于预设键集合怎么算」。
     *
     * 非可覆盖字段（亮度、TTS、护眼、点击区域等设备级偏好）原样保留。
     */
    fun resetAllOverridableKeys(current: ReaderSettings): ReaderSettings =
        resetKeys(current, ReaderOverrideKey.entries.toSet())

    /** 只把 [keys] 指定的字段恢复为结构默认，其余字段原样保留。 */
    private fun resetKeys(current: ReaderSettings, keys: Set<ReaderOverrideKey>): ReaderSettings {
        val defaults = ReaderSettings()
        var next = current
        keys.forEach { key ->
            val value = key.read(defaults)
            next = key.write(next, value) ?: next
        }
        return next
    }
}
