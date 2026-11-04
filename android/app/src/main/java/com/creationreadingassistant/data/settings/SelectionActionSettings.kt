package com.creationreadingassistant.data.settings

/**
 * 选区工具条动作定义（R3-X1）。
 *
 * 分组语义（**只是默认落位提示，不是硬约束**）：
 * - [Group.PRIMARY] 出厂时铺在工具条上的高频动作（受屏幕宽度限制，最多 [MAX_PRIMARY_SLOTS] 个）；
 * - [Group.MORE] 出厂时收在「更多」溢出菜单里的上下文动作；
 * - [Group.FIXED] 「取消选择」这类必须常在的动作，不可隐藏、固定排在溢出菜单末尾。
 *
 * 为什么分组不是约束：用户完全可能想把「书内搜索」放到工具条第一屏 —— 这正是
 * 「搜索提供方切换（书内搜索 vs 浏览器）」的实现方式。因此可配置集合取
 * [SelectionActions.pickableDefs]（全部非固定动作），分组只决定**初始值**。
 */
data class SelectionActionDef(
    val id: String,
    val label: String,
    val description: String,
    val group: SelectionActionGroup,
)

enum class SelectionActionGroup { PRIMARY, MORE, FIXED }

/** 选区工具条的动作与查询目标配置（R3-X1）。 */
data class SelectionActionSettings(
    /** 铺在工具条上的高频动作 id（保持定义顺序渲染），最多 [SelectionActions.MAX_PRIMARY_SLOTS] 个。 */
    val enabledPrimary: Set<String> = SelectionActions.DEFAULT_PRIMARY,
    /** 收在「更多」溢出菜单里的动作 id。 */
    val enabledMore: Set<String> = SelectionActions.DEFAULT_MORE,
    /** 浏览器查询 URL 模板，必须含 `{q}`。 */
    val browserUrlTemplate: String = SelectionActions.DEFAULT_BROWSER_TEMPLATE,
    /** 在线词典 URL 模板，必须含 `{q}`。 */
    val dictionaryUrlTemplate: String = SelectionActions.DEFAULT_DICTIONARY_TEMPLATE,
    /** 首选词典：offline（内置离线词库）| system（系统/其它应用的文本工具）| online（在线词典）。 */
    val dictionaryMode: String = SelectionActions.MODE_OFFLINE,
)

/**
 * 选区动作清单与配置校验（纯函数）。
 *
 * 不变量：
 * - 工具条最少保留 [MIN_PRIMARY_SLOTS] 个高频动作 —— 全关掉会让用户「选了字却没有任何入口」；
 * - 高频槽位不超过 [MAX_PRIMARY_SLOTS] 个 —— 超出会被挤成不可点的小块，宁可拒绝写入；
 * - URL 模板**必须含 `{q}`**，否则查询会发出去一个固定地址（比禁用更糟：静默错误结果）；
 * - 配置从磁盘读回时一律 [sanitize]，未知 id / 非法模板 / 非法模式都回退默认。
 */
object SelectionActions {

    const val MIN_PRIMARY_SLOTS = 1

    /**
     * 高频槽上限：3 → 5（2026-09-15）。
     *
     * 放开到 5 是为了容纳默认顺序「复制 / 浏览器 / 替换 / 编辑 / 字典」。
     * 代价：小屏（如 c49ac6cf）上 5 个胶囊会变窄，必须真机确认仍可点；
     * 若确认挤到不可点，退回 4 并把「字典」留在溢出菜单。
     */
    const val MAX_PRIMARY_SLOTS = 5

    const val MODE_OFFLINE = "offline"
    const val MODE_SYSTEM = "system"
    const val MODE_ONLINE = "online"

    val MODES = listOf(MODE_OFFLINE, MODE_SYSTEM, MODE_ONLINE)

    const val DEFAULT_BROWSER_TEMPLATE = "https://www.bing.com/search?q={q}"
    const val DEFAULT_DICTIONARY_TEMPLATE = "https://dict.youdao.com/result?word={q}&lang=auto"

    const val MIN_WEB_QUERY_CHARS = 500
    const val MIN_DICT_QUERY_CHARS = 120

    /**
     * 动作清单。**声明顺序即渲染顺序** —— [effectivePrimary] 与 [effectiveMore] 都是按本列表
     * 顺序过滤，所以「改默认顺序」= 改这里的声明顺序，不需要额外持久化一份顺序。
     *
     * 目标第一屏顺序：复制 → 浏览器 → 替换 → 编辑 → 字典。
     */
    val ALL: List<SelectionActionDef> = listOf(
        SelectionActionDef("copy", "复制", "复制选中文字到剪贴板", SelectionActionGroup.PRIMARY),
        SelectionActionDef("browser", "浏览器", "用浏览器搜索选中的文字", SelectionActionGroup.PRIMARY),
        SelectionActionDef("replace", "替换", "把选中文字设为替换规则", SelectionActionGroup.PRIMARY),
        // 编辑：S08（章节编辑器）尚未接线。定义保留在 ALL 里（声明顺序已锁定最终落点，
        // 见下方 `edit keeps its declared landing slot`），但 id 同时在 [DORMANT_IDS] 中，
        // 因此**既不可配置也不可渲染** —— 只移出默认集合挡不住用户主动勾选它。
        SelectionActionDef("edit", "编辑", "编辑本章正文", SelectionActionGroup.MORE),
        SelectionActionDef("dictionary", "字典", "查词：离线词库 / 系统工具 / 在线词典", SelectionActionGroup.PRIMARY),
        SelectionActionDef("highlight", "高亮", "选中后直接标记颜色", SelectionActionGroup.MORE),
        SelectionActionDef("note", "添加批注", "为选中的文字写一条批注", SelectionActionGroup.MORE),
        SelectionActionDef("search", "书内搜索", "在本书内搜索选中的文字", SelectionActionGroup.MORE),
        SelectionActionDef("ai", "AI 解读", "让 AI 解释选中的文字", SelectionActionGroup.MORE),
        SelectionActionDef("inspiration", "记为灵感", "把选中文字保存为灵感卡片", SelectionActionGroup.MORE),
        SelectionActionDef("cancel", "取消选择", "收起选区", SelectionActionGroup.FIXED),
    )

    /**
     * 出厂第一屏：复制 → 浏览器 → 替换 → 字典（占 4 槽）。
     *
     * 「编辑」在 S08 接线后追加进来即占满 5 槽，其落点由 [ALL] 声明顺序决定
     * （在「替换」与「字典」之间），无需再动顺序；接线前它由 [DORMANT_IDS] 挡住。
     */
    val DEFAULT_PRIMARY: Set<String> = setOf("copy", "browser", "replace", "dictionary")

    /** 出厂溢出菜单；「编辑」未接线前不出现在任何默认集合，也由 [DORMANT_IDS] 挡在可配置清单外。 */
    val DEFAULT_MORE: Set<String> = setOf("highlight", "note", "search", "ai", "inspiration")

    /**
     * 3 槽时代的历史出厂值。仅用于识别「从未改过设置」的老用户并自动升级；
     * 任何自定义过的配置一律原样保留。
     */
    val LEGACY_DEFAULT_PRIMARY: Set<String> = setOf("highlight", "browser", "copy")

    fun byId(id: String): SelectionActionDef? = ALL.firstOrNull { it.id == id }

    /**
     * 解析磁盘上的高频槽配置，并对老用户做一次性自动升级。
     *
     * - 没存过 → 新默认；
     * - 存的正好等于 3 槽时代的历史默认 → 判定为「从未改过」，升级为新默认；
     * - 其它（用户自定义过）→ 原样保留，哪怕它只有 3 个。
     *
     * 只升级"从未改过"的那批：给自定义用户凭空补 2 个动作，等于替用户做选择。
     */
    fun resolveStoredPrimary(stored: Set<String>?): Set<String> = when {
        stored == null -> DEFAULT_PRIMARY
        stored == LEGACY_DEFAULT_PRIMARY -> DEFAULT_PRIMARY
        else -> stored
    }

    fun primaryDefs(): List<SelectionActionDef> = ALL.filter { it.group == SelectionActionGroup.PRIMARY }

    fun moreDefs(): List<SelectionActionDef> = ALL.filter { it.group == SelectionActionGroup.MORE }

    /**
     * 尚未接线、因而不允许配置或渲染的动作 id。
     *
     * 与「固定组」的区别：固定组是**不可隐藏**，dormant 是**不可出现**。两者都从
     * [pickableDefs] 里排除，但语义相反，不要合并。
     *
     * 为什么需要它：只把 `edit` 从 [DEFAULT_PRIMARY] / [DEFAULT_MORE] 移除**不够** ——
     * 设置页仍会让用户把它勾进第一屏或溢出菜单，而工具条的分发 `when` 没有对应分支，
     * 点击落入 `else -> Unit`，表现为一个点了没反应的死按钮。可配置清单必须自己排除它。
     *
     * S08（章节编辑器）接线时，把对应 id 从这里移除并补上生产分发分支即可；
     * [ALL] 中的定义始终保留，不需要重新发明动作声明。
     */
    val DORMANT_IDS: Set<String> = setOf("edit")

    /** 可自由配置落位的动作（固定组与 dormant 组除外）——高频槽与「更多」都从这里取。 */
    fun pickableDefs(): List<SelectionActionDef> =
        ALL.filter { it.group != SelectionActionGroup.FIXED && it.id !in DORMANT_IDS }

    /**
     * 按配置渲染的高频动作（定义顺序，1..[MAX_PRIMARY_SLOTS] 个）。
     *
     * 全关 / 空集合时兜底到定义顺序最前面的 [MIN_PRIMARY_SLOTS] 个 —— 已选中的文字
     * 必须至少有一个可点入口，否则用户会以为选区坏了。
     */
    fun effectivePrimary(settings: SelectionActionSettings): List<SelectionActionDef> {
        val picked = pickableDefs().filter { it.id in settings.enabledPrimary }
        return (picked.ifEmpty { pickableDefs().take(MIN_PRIMARY_SLOTS) }).take(MAX_PRIMARY_SLOTS)
    }

    /**
     * 按配置渲染的溢出菜单动作。
     *
     * 已被放进高频槽的动作**不再重复出现**在溢出菜单里（同一个动作两个入口会让
     * 「顺序」这件事失去意义）；固定组永远追加在末尾、不可隐藏。
     */
    fun effectiveMore(settings: SelectionActionSettings): List<SelectionActionDef> {
        val taken = effectivePrimary(settings).map { it.id }.toSet()
        return pickableDefs().filter { it.id in settings.enabledMore && it.id !in taken } +
            ALL.filter { it.group == SelectionActionGroup.FIXED }
    }

    /**
     * 模板校验：必须同时满足三条，缺一不可。
     *
     * 1. 非空白；
     * 2. 含 `{q}` 占位符 —— 否则查询会发出去一个**固定地址**，比禁用更糟（静默给出错误结果）；
     * 3. 以 `http://` 或 `https://` 开头（scheme 大小写不敏感）—— 这个模板最终会被塞进
     *    `Intent.ACTION_VIEW` 交给系统解析，所以前缀必须收到具体 scheme，
     *    不能让 `httpfoo://…` 这类畸形 scheme 通过。
     */
    fun isValidTemplate(template: String): Boolean {
        if (template.isBlank() || !template.contains("{q}")) return false
        val scheme = template.lowercase()
        return scheme.startsWith("https://") || scheme.startsWith("http://")
    }

    /** 用查询串套用模板；模板非法返回 null（调用方回退默认或提示，绝不发静默错误 URL）。 */
    fun buildUrl(template: String, encodedQuery: String): String? {
        if (!isValidTemplate(template)) return null
        return template.replace("{q}", encodedQuery)
    }

    /** 磁盘脏数据兜底：未知 id 丢弃、空集合回默认、非法模板/模式回默认、超出槽位上限截断。 */
    fun sanitize(raw: SelectionActionSettings): SelectionActionSettings {
        val knownPickable = pickableDefs().map { it.id }.toSet()
        val primary = raw.enabledPrimary.intersect(knownPickable).take(MAX_PRIMARY_SLOTS).toSet()
        val more = raw.enabledMore.intersect(knownPickable)
        return raw.copy(
            enabledPrimary = primary.ifEmpty { DEFAULT_PRIMARY },
            enabledMore = if (raw.enabledMore.isEmpty()) DEFAULT_MORE else more,
            browserUrlTemplate = raw.browserUrlTemplate
                .takeIf { isValidTemplate(it) } ?: DEFAULT_BROWSER_TEMPLATE,
            dictionaryUrlTemplate = raw.dictionaryUrlTemplate
                .takeIf { isValidTemplate(it) } ?: DEFAULT_DICTIONARY_TEMPLATE,
            dictionaryMode = raw.dictionaryMode.takeIf { it in MODES } ?: MODE_OFFLINE,
        )
    }
}
