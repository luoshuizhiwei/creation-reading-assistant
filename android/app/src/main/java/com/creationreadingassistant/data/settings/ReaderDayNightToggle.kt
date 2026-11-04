package com.creationreadingassistant.data.settings

/**
 * 一键日/夜切换（对齐 QQ 阅读阅读页右上角的「灯绳」）。
 *
 * ## 为什么是「同时改两处」
 *
 * QQ 阅读的灯绳是**纸张与外壳同源**的：实测拉一下灯绳，阅读正文纸张变深的同时，
 * 退出阅读器回到书架，书架外壳也已经是深色。只改其一会出现「深色正文 + 浅色外壳」
 * 的明暗断层，这不是要复刻的行为。因此一次切换同时落两处：
 *
 * - 阅读纸张：[ReaderSettings.background]
 * - 应用外壳：[AppearanceSettings.themeMode]
 *
 * ## 为什么要记住「各侧的纸色」
 *
 * 纸张有 5 档（白纸 / 暖纸 / 护眼 / 夜读 / 暮棕）+ 跟随外观。若切换时简单地写死
 * `white` ↔ `night`，用户精心选的「暖纸」在切到夜间再切回来后就变成了「白纸」——
 * 这是一次静默的偏好丢失。所以两侧各记一个「上次用的纸」，切回时还原用户自己的选择。
 *
 * 记忆值只在用户**主动选了该侧的纸**时更新（见 [rememberPaper]），不由切换动作本身改写。
 */
object ReaderDayNightToggle {

    /** 日间态纸张回退值：用户从未选过亮纸时用白纸。 */
    const val DEFAULT_LIGHT_PAPER: String = "white"

    /** 夜间态纸张回退值：用户从未选过暗纸时用夜读。 */
    const val DEFAULT_DARK_PAPER: String = "night"

    /** 明确属于亮纸的纸张 key（不含 `follow`——它随外壳明暗，两种状态都成立）。 */
    val LIGHT_PAPERS: Set<String> = setOf("white", "warm", "green")

    /** 明确属于暗纸的纸张 key（不含 `follow`，理由同上）。 */
    val DARK_PAPERS: Set<String> = setOf("night", "sepia_dark")

    /**
     * 当前是否处于夜间态。
     *
     * 判定依据是**当前纸张的明暗**，而不是外壳的 themeMode：纸张是阅读器里用户直接看到的
     * 那层，且 `follow` 纸张的明暗要由 [appDark] 解析后才成立。这样「跟随外观 + 深色外壳」
     * 会被正确认作夜间态，灯绳图标才不会与用户看到的内容矛盾。
     *
     * @param background 当前纸张 key。
     * @param appDark 外壳当前是否深色（用于解析 `follow`）。
     */
    fun isNight(background: String, appDark: Boolean): Boolean = when (background) {
        in LIGHT_PAPERS -> false
        in DARK_PAPERS -> true
        // follow / 未知值：跟随外壳明暗
        else -> appDark
    }

    /**
     * 计算一次切换后的落点。
     *
     * @param background 当前纸张。
     * @param appDark 外壳当前是否深色。
     * @param lastLightPaper 上次用过的亮纸（记忆值，可能非法/为空）。
     * @param lastDarkPaper 上次用过的暗纸（记忆值，可能非法/为空）。
     * @return 切换后的目标：纸张 key + 外壳是否深色。
     */
    fun next(
        background: String,
        appDark: Boolean,
        lastLightPaper: String,
        lastDarkPaper: String,
    ): Target {
        val goingDark = !isNight(background, appDark)
        return if (goingDark) {
            Target(paper = validOrDefault(lastDarkPaper, DARK_PAPERS, DEFAULT_DARK_PAPER), dark = true)
        } else {
            Target(paper = validOrDefault(lastLightPaper, LIGHT_PAPERS, DEFAULT_LIGHT_PAPER), dark = false)
        }
    }

    /**
     * 用户主动选纸后应记下的记忆值。
     *
     * 只在选中的纸**明确属于某一侧**时更新该侧；选 `follow` 或未知值时两侧都不动——
     * 它没有表达「用户偏好哪种亮/暗纸」，拿去当记忆值会把上一次的真实偏好抹掉。
     *
     * @return 该更新哪一侧；`null` 表示本次选择不构成记忆。
     */
    fun rememberPaper(paper: String): Remembered? = when (paper) {
        in LIGHT_PAPERS -> Remembered(light = paper)
        in DARK_PAPERS -> Remembered(dark = paper)
        else -> null
    }

    /**
     * 把 `follow` 纸张落到具体纸色上。
     *
     * 切换动作必须写一个**具体**的纸色：若写成 `follow`，纸张明暗就仍由外壳决定，
     * 而外壳也在同一时刻被改写，两者互相依赖会出现「切了但没变」或来回抖动。
     * 用户此前选了 `follow`、现在拉灯绳时，用记忆值（或默认值）把它落成具体纸色。
     */
    private fun validOrDefault(candidate: String, allowed: Set<String>, fallback: String): String =
        candidate.takeIf { it in allowed } ?: fallback

    /** 切换落点。 */
    data class Target(val paper: String, val dark: Boolean)

    /** 一次「记忆」更新：两侧各自独立，互不牵连。 */
    data class Remembered(val light: String? = null, val dark: String? = null)
}
