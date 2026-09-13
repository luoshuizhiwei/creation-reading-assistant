package com.creationreadingassistant.data.settings

/**
 * 可被「本书覆盖」的阅读器设置项（R3-P1）。
 *
 * 设计约束：
 * - **只有用户显式改过的项**才写入本书覆盖；未覆盖项一律跟随全局。
 * - 覆盖值与全局值同口径，以字符串落盘（DataStore Preferences 只支持原始类型）；
 *   **解码失败一律丢弃该覆盖并回退全局**，绝不把坏值当成有效设置。
 * - 数值项**不做区间收敛**，与 [SettingsStore.updateReader] 的既有语义保持一致（只挡 NaN/Inf）；
 *   收敛口径改动会影响用户已存设置，不属于本条任务。
 * - 未列入本枚举的字段（亮度、TTS、护眼、音量键翻页等）保持纯全局语义。
 */
enum class ReaderOverrideKey(val id: String, val label: String) {
    FONT_SIZE("fontSize", "字号"),
    LINE_HEIGHT("lineHeight", "行距"),
    PARAGRAPH_SPACING("paragraphSpacing", "段间距"),
    PAGE_MARGIN("pageMargin", "页边距"),
    BACKGROUND("background", "纸张"),
    FONT_BOLD("fontWeightBold", "粗体"),
    CHINESE_TYPOGRAPHY("chineseTypography", "中文排版"),
    TRADITIONAL_CHINESE("traditionalChinese", "繁体显示"),
    CUSTOM_FONT_PATH("customFontPath", "自定义字体"),
    READER_MODE("readerMode", "阅读模式"),
    PAGE_TURN_EFFECT("pageTurnEffect", "翻页效果"),
    IMMERSIVE("immersiveMode", "沉浸模式"),
    SHOW_READER_INFO("showReaderInfo", "页眉页脚"),
    SHOW_PROGRESS("showProgressBar", "底部进度条"),
    AUTO_HIDE_SECONDS("autoHideSeconds", "菜单自动隐藏"),
    HEADER_LEFT("headerLeft", "页眉左侧"),
    HEADER_RIGHT("headerRight", "页眉右侧"),
    FOOTER_LEFT("footerLeft", "页脚左侧"),
    FOOTER_RIGHT("footerRight", "页脚右侧"),
    ;

    /** 读取该项在当前设置里的落盘表示。 */
    fun read(settings: ReaderSettings): String = when (this) {
        FONT_SIZE -> settings.fontSize.toString()
        LINE_HEIGHT -> settings.lineHeight.toString()
        PARAGRAPH_SPACING -> settings.paragraphSpacing.toString()
        PAGE_MARGIN -> settings.pageMargin.toString()
        BACKGROUND -> settings.background
        FONT_BOLD -> settings.fontWeightBold.toString()
        CHINESE_TYPOGRAPHY -> settings.chineseTypography.toString()
        TRADITIONAL_CHINESE -> settings.traditionalChinese.toString()
        CUSTOM_FONT_PATH -> settings.customFontPath
        READER_MODE -> settings.readerMode
        PAGE_TURN_EFFECT -> settings.pageTurnEffect
        IMMERSIVE -> settings.immersiveMode.toString()
        SHOW_READER_INFO -> settings.showReaderInfo.toString()
        SHOW_PROGRESS -> settings.showProgressBar.toString()
        AUTO_HIDE_SECONDS -> settings.autoHideSeconds.toString()
        HEADER_LEFT -> settings.headerLeft.name
        HEADER_RIGHT -> settings.headerRight.name
        FOOTER_LEFT -> settings.footerLeft.name
        FOOTER_RIGHT -> settings.footerRight.name
    }

    /**
     * 把落盘字符串写回设置。**返回 null 表示该值非法**（调用方保留原值），
     * 不抛异常、不做近似替换 —— 坏值静默变成别的值比保留全局更容易误导用户。
     */
    fun write(settings: ReaderSettings, raw: String): ReaderSettings? = when (this) {
        FONT_SIZE -> raw.toFloatOrNull()?.takeIf { it.isFinite() }?.let { settings.copy(fontSize = it) }
        LINE_HEIGHT -> raw.toFloatOrNull()?.takeIf { it.isFinite() }?.let { settings.copy(lineHeight = it) }
        PARAGRAPH_SPACING -> raw.toFloatOrNull()?.takeIf { it.isFinite() }?.let { settings.copy(paragraphSpacing = it) }
        PAGE_MARGIN -> raw.toFloatOrNull()?.takeIf { it.isFinite() }?.let { settings.copy(pageMargin = it) }
        BACKGROUND -> raw.takeIf { it in BACKGROUND_VALUES }?.let { settings.copy(background = it) }
        FONT_BOLD -> raw.toBooleanStrictOrNull()?.let { settings.copy(fontWeightBold = it) }
        CHINESE_TYPOGRAPHY -> raw.toBooleanStrictOrNull()?.let { settings.copy(chineseTypography = it) }
        TRADITIONAL_CHINESE -> raw.toBooleanStrictOrNull()?.let { settings.copy(traditionalChinese = it) }
        CUSTOM_FONT_PATH -> settings.copy(customFontPath = raw)
        READER_MODE -> raw.takeIf { it in READER_MODE_VALUES }?.let { settings.copy(readerMode = it) }
        PAGE_TURN_EFFECT -> raw.takeIf { it in PAGE_TURN_EFFECT_VALUES }?.let { settings.copy(pageTurnEffect = it) }
        IMMERSIVE -> raw.toBooleanStrictOrNull()?.let { settings.copy(immersiveMode = it) }
        SHOW_READER_INFO -> raw.toBooleanStrictOrNull()?.let { settings.copy(showReaderInfo = it) }
        SHOW_PROGRESS -> raw.toBooleanStrictOrNull()?.let { settings.copy(showProgressBar = it) }
        AUTO_HIDE_SECONDS -> raw.toIntOrNull()?.let { settings.copy(autoHideSeconds = it) }
        HEADER_LEFT -> headerFooter(raw)?.let { settings.copy(headerLeft = it) }
        HEADER_RIGHT -> headerFooter(raw)?.let { settings.copy(headerRight = it) }
        FOOTER_LEFT -> headerFooter(raw)?.let { settings.copy(footerLeft = it) }
        FOOTER_RIGHT -> headerFooter(raw)?.let { settings.copy(footerRight = it) }
    }

    private fun headerFooter(raw: String): HeaderFooterItem? =
        HeaderFooterItem.entries.firstOrNull { it.name == raw }

    companion object {
        /** 纸张体系：与 `migrateReaderBg` 收敛后的 4 档 + 跟随外观一致。 */
        val BACKGROUND_VALUES = setOf("white", "warm", "green", "night", "follow")
        val READER_MODE_VALUES = setOf("paged", "scroll")
        val PAGE_TURN_EFFECT_VALUES = setOf("none", "fade", "slide", "cover", "reveal")

        fun fromId(id: String): ReaderOverrideKey? = entries.firstOrNull { it.id == id }

        /**
         * 把 [base] 里 [keys] 指定的字段替换成 [maskFrom] 的对应值，其余字段保持 [base]。
         *
         * 用途：书内编辑作用于**有效设置**（全局叠加本书覆盖后的结果），写回全局时必须先把
         * 「可覆盖项」还原成全局原值，否则本书的覆盖值会泄漏进全局设置，污染其它书籍。
         */
        fun mask(base: ReaderSettings, maskFrom: ReaderSettings, keys: Set<ReaderOverrideKey>): ReaderSettings =
            keys.fold(base) { acc, key -> key.write(acc, key.read(maskFrom)) ?: acc }
    }
}

/** 一本书的覆盖集合：`key -> 覆盖值`，只含用户显式改过的项。 */
typealias PerBookOverrides = Map<ReaderOverrideKey, String>

/**
 * 「全局 + 本书覆盖」的叠加规则（纯函数，R3-P1 的核心语义）。
 *
 * 不变量：
 * - 叠加只读取 [PerBookOverrides] 里存在的项，**绝不因为某项缺失而写回默认值**。
 * - 单项解码失败只跳过该项，不影响其他项。
 * - 应用顺序按枚举声明顺序，保证同一输入必得同一输出（可测）。
 */
object ReaderSettingsOverlay {

    fun apply(global: ReaderSettings, overrides: PerBookOverrides): ReaderSettings =
        ReaderOverrideKey.entries.fold(global) { acc, key ->
            val raw = overrides[key]
            if (raw == null) acc else key.write(acc, raw) ?: acc
        }

    fun isOverridden(overrides: PerBookOverrides, key: ReaderOverrideKey): Boolean =
        overrides.containsKey(key)

    /**
     * 规范化从磁盘读到的原始 map：丢弃未知 id 与非法值。
     * 用于抵御旧版本/手改/未来版本写下的脏数据。
     */
    fun sanitize(raw: Map<String, String>): PerBookOverrides = buildMap {
        raw.forEach { (id, value) ->
            val key = ReaderOverrideKey.fromId(id) ?: return@forEach
            if (key.write(ReaderSettings(), value) != null) put(key, value)
        }
    }
}
