package com.creationreadingassistant.data.settings

/** 书内改设置时用户选择的**作用范围**（R3-P1）。 */
enum class ReaderSettingsScope {
    /** 只写本书覆盖；未覆盖项继续跟随全局。 */
    BOOK,

    /** 写全局默认（其它书籍与「我的 → 阅读设置」同步可见）。 */
    GLOBAL,
    ;

    val label: String
        get() = when (this) {
            BOOK -> "本书"
            GLOBAL -> "全局"
        }
}

/**
 * 一次书内编辑的落盘计划。
 *
 * @param global 要写回全局的**完整**设置（已剔除不该进全局的可覆盖项）。
 * @param overridesToSet 要新建/更新的本书覆盖项。
 * @param overridesToClear 要删除的本书覆盖项（作用范围选「全局」时，显式选中的项要让位给新全局值）。
 */
data class ReaderSettingsEditPlan(
    val global: ReaderSettings,
    val overridesToSet: PerBookOverrides,
    val overridesToClear: Set<ReaderOverrideKey>,
) {
    /** 本次编辑是否真的动了全局（决定要不要发写请求，避免无谓的 DataStore 写入）。 */
    fun globalDiffersFrom(previous: ReaderSettings): Boolean = global != previous
}

/**
 * 书内设置编辑 → 全局/本书两级落盘的路由规则（纯函数，R3-P1）。
 *
 * 背景：书内设置面板展示与操作的是**有效设置**（全局叠加本书覆盖后的结果），
 * 而每次改动只应落到用户选定的那一层。难点在于 `ReaderSettings` 有 40+ 个字段，
 * 其中只有 [ReaderOverrideKey.entries] 覆盖的那部分**可被本书覆盖**，其余（亮度、TTS、
 * 点击区域、翻页速度、护眼、兼容引擎等）永远只属于全局。
 *
 * 规则（不变量）：
 * 1. **不可覆盖项永远进全局**，无论作用范围选了什么。
 * 2. 作用范围为 [ReaderSettingsScope.BOOK] 时，**所有**可覆盖项都不进全局 ——
 *    否则本书的覆盖值会写进全局，其它书被静默污染。
 * 3. 作用范围为 [ReaderSettingsScope.GLOBAL] 时，只有**用户本次真正改动**的可覆盖项进全局；
 *    未被改动的可覆盖项即便当前由本书覆盖，也一律还原为全局原值。
 * 4. 作用范围选「全局」时，用户本次改动的可覆盖项会**同时清除本书覆盖**：这是用户的
 *    显式选择（不是静默覆盖），否则新全局值会被本书覆盖遮住、表现为「改了没反应」。
 * 5. 未被本次编辑触碰的本书覆盖**一律保留**（对应「全局改动不得掀掉本书显式选择」）。
 */
object ReaderSettingsRouter {

    private val OVERRIDABLE: Set<ReaderOverrideKey> = ReaderOverrideKey.entries.toSet()

    /** 本次编辑相对**展示值**真正发生变化的可覆盖项。 */
    fun changedOverridableKeys(displayed: ReaderSettings, edited: ReaderSettings): Set<ReaderOverrideKey> =
        ReaderOverrideKey.entries.filterTo(linkedSetOf()) { key -> key.read(displayed) != key.read(edited) }

    /**
     * 计算落盘计划。
     *
     * @param scope 用户选择的作用范围。
     * @param global 当前**全局**设置（不是有效设置）。
     * @param displayed 编辑前展示给用户的有效设置。
     * @param edited 编辑后的设置（由 UI 基于 [displayed] 派生）。
     */
    fun plan(
        scope: ReaderSettingsScope,
        global: ReaderSettings,
        displayed: ReaderSettings,
        edited: ReaderSettings,
    ): ReaderSettingsEditPlan {
        val touched = changedOverridableKeys(displayed, edited)
        // BOOK：可覆盖项一律不进全局；GLOBAL：只把本次改动的项放进全局。
        val mustStayGlobalBacked = when (scope) {
            ReaderSettingsScope.BOOK -> OVERRIDABLE
            ReaderSettingsScope.GLOBAL -> OVERRIDABLE - touched
        }
        val nextGlobal = ReaderOverrideKey.mask(base = edited, maskFrom = global, keys = mustStayGlobalBacked)
        return when (scope) {
            ReaderSettingsScope.BOOK -> ReaderSettingsEditPlan(
                global = nextGlobal,
                overridesToSet = touched.associateWith { key -> key.read(edited) },
                overridesToClear = emptySet(),
            )

            ReaderSettingsScope.GLOBAL -> ReaderSettingsEditPlan(
                global = nextGlobal,
                overridesToSet = emptyMap(),
                overridesToClear = touched,
            )
        }
    }
}
