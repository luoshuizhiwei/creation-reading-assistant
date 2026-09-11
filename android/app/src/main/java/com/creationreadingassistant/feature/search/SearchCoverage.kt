package com.creationreadingassistant.feature.search

/**
 * 全文索引的「文本基准」：同一本书可以有两套互不相同的可搜索文本。
 *
 * - [ORIGINAL]：原文（导入落盘、应用替换规则之前的文本）。
 * - [DISPLAY]：替换显示文（应用 REPLACE 规则之后、用户实际读到的文本）。
 *
 * R2-S1 口径（已与产品确认）：**两者都索引、两者都搜，命中结果标注来源基准**。
 * 因此该值进入 search_terms 的复合主键之一，是「同一 term 在两种文本里各有一行」的前提。
 *
 * ⚠️ [wire] 字符串直接持久化进 `search_terms.text_basis` 与
 * `search_index_coverage.text_basis`。一旦有存量数据落盘即**不可再改**：
 * 改 wire 值等于把全部历史索引判为「另一种基准」，需要整表重建。
 */
enum class SearchTextBasis(val wire: String) {
    ORIGINAL("original"),
    DISPLAY("display"),
    ;

    companion object {
        fun fromWire(value: String?): SearchTextBasis? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * 单本书 × 单一文本基准的索引覆盖率状态。
 *
 * 设计约束（README R2-S1）：「TXT/EPUB/Markdown 分别记录索引覆盖，部分索引不是全文完成」
 * 「索引仓储不能据类名推断全覆盖」。所以状态必须**显式持久化**到
 * `search_index_coverage`，而不是由 format、章节数或行数反推。
 *
 * ⚠️ [wire] 字符串写入 `search_index_coverage.coverage`，同样一经落盘不可更改。
 *
 * 语义边界：
 *  - [FULL] / [PARTIAL] 只描述「逐章正文」路径的完成度；
 *  - [PREVIEW_ONLY] / [METADATA_ONLY] 是逐章路径不可用时的降级档；
 *  - [FAILED] 是「尝试过逐章但硬失败」；
 *  - [PENDING] 为历史 / wire 兼容占位：替换显示文通道现已真正构建，当前索引器不再写出该状态，
 *    仅当解析到旧落盘数据中的 "pending" wire 时才会出现，调用方不应再依赖它。
 */
enum class SearchCoverageState(val wire: String) {
    /** 全部（非空）章节正文均已纳入索引，且没有章节因超长被截断。 */
    FULL("full"),

    /** 部分章节纳入，或存在被截断的超长章节 —— 搜索会漏内容，不是全文完成。 */
    PARTIAL("partial"),

    /** 只索引了正文预览（正文前若干字），未逐章。 */
    PREVIEW_ONLY("preview_only"),

    /** 只有书名/作者/描述可搜，正文完全未纳入。 */
    METADATA_ONLY("metadata_only"),

    /** 尝试逐章索引但硬失败（解码失败 / EPUB 解析失败 / 找不到文件）。 */
    FAILED("failed"),

    /**
     * 历史 / wire 兼容占位。替换显示文通道（[SearchTextBasis.DISPLAY]）现已真正构建，
     * 当前索引器对任何基准都不再写出 [PENDING]（display 基准改记
     * [NOT_APPLICABLE] / [FAILED] / [FULL] 等真实状态）。保留该值仅为兼容旧落盘数据中的 "pending" wire。
     */
    PENDING("pending"),

    /**
     * 替换显示文通道对本书记不适用：没有任何生效替换规则，显示文与原文逐字一致，
     * 再建一套 display 基准索引只是逐字重复原文，故不建索引、不产生 display 行。
     * 这是构建完成后一个**稳定的终态**（不是「还没建」），不计入「未完成」。
     */
    NOT_APPLICABLE("not_applicable"),
    ;

    companion object {
        fun fromWire(value: String?): SearchCoverageState? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * `search_index_coverage.reason` 的稳定取值。
 *
 * 用于回答「为什么这本书不是全量覆盖」，是诊断与「重建未完成索引」入口的依据；
 * 与 [SearchCoverageState] 一样属于持久化的 wire 契约，不可随意改名。
 */
object SearchCoverageReason {
    /** 既非 TXT/MD 也非 EPUB，且没有可用的正文来源。 */
    const val UNSUPPORTED_FORMAT = "unsupported_format"

    /** TXT/MD 没有本地文件路径，无从逐章。 */
    const val NO_BODY_SOURCE = "no_body_source"

    /** TXT/MD 体积超过逐章索引阈值。 */
    const val FILE_TOO_LARGE = "file_too_large"

    /** TXT/MD 文件存在但解码失败。 */
    const val TXT_DECODE_FAILED = "txt_decode_failed"

    /** EPUB 在预期位置找不到本地缓存文件。 */
    const val EPUB_FILE_MISSING = "epub_file_missing"

    /** EPUB 文件存在但解析（OPF/spine/TOC）抛异常。 */
    const val EPUB_PARSE_FAILED = "epub_parse_failed"

    /** EPUB 解析成功但章节列表为空。 */
    const val EPUB_NO_CHAPTERS = "epub_no_chapters"

    /** 存在超过单章字数上限、被截断的章节。 */
    const val CHAPTER_TRUNCATED = "chapter_truncated"

    /** 存在内容为空的章节（标题为空且正文为空），未纳入索引。 */
    const val CHAPTERS_SKIPPED = "chapters_skipped"

    /** 降级为正文预览索引。 */
    const val PREVIEW_ONLY = "preview_only"

    /**
     * 历史 / wire 兼容占位：替换显示文通道尚未构建。该通道现已真正构建，
     * 当前索引器不再写出此 reason（display 基准改记 [NO_REPLACE_RULES] / [DISPLAY_RULE_FAILED] 等）。
     * 保留常量仅为兼容旧落盘数据中可能出现的 "display_channel_not_built" wire。
     */
    const val DISPLAY_CHANNEL_NOT_BUILT = "display_channel_not_built"

    /** 该书没有任何生效替换规则，显示文与原文一致，显示文通道不适用（不建索引）。 */
    const val NO_REPLACE_RULES = "no_replace_rules"

    /** 该书有替换规则，但索引时规则正则失效（不可编译 / 可空匹配），显示文通道构建失败。 */
    const val DISPLAY_RULE_FAILED = "display_rule_failed"
}

/**
 * 覆盖率判定策略 —— 纯函数、零 Android/Room 依赖，可直接 JVM 单测。
 *
 * 拆出来单独放，是为了让「什么算全量、什么算部分」的规则成为可断言的对象，
 * 而不是散落在仓储的分支里靠阅读推断。
 */
object SearchCoveragePolicy {

    /**
     * 逐章索引完成度判定。
     *
     * @param indexedChapters 实际把**正文**写入索引的章节数
     * @param totalChapters 本次逐章路径认定的章节总数（已排除「标题与正文均为空」的章节）
     * @param truncated 是否有章节因超过单章字数上限被截断
     *
     * 判定规则：
     *  1. 没有任何可纳入章节 → [SearchCoverageState.METADATA_ONLY]（防御，正常路径不该走到）；
     *  2. 有截断、或有章节没能贡献正文 → [SearchCoverageState.PARTIAL]；
     *  3. 其余 → [SearchCoverageState.FULL]。
     */
    fun forChapterIndex(
        indexedChapters: Int,
        totalChapters: Int,
        truncated: Boolean,
    ): SearchCoverageState {
        if (totalChapters <= 0) return SearchCoverageState.METADATA_ONLY
        if (truncated || indexedChapters < totalChapters) return SearchCoverageState.PARTIAL
        return SearchCoverageState.FULL
    }

    /**
     * 该书在**该基准上**是否仍有可索引内容未纳入 —— 「重建未完成索引」的候选判据。
     *
     * 注意：[PENDING] 现已不再由索引器写出（替换显示文通道已真正构建，display 基准改记
     * [NOT_APPLICABLE] / [FAILED] / [FULL] 等），故不再存在「某基准对每本书都是 PENDING」的情况。
     * 若未来新增「尚未构建」的基准，调用方仍需按基准过滤，避免把该基准的占位态算作整库未完成。
     */
    fun isIncomplete(state: SearchCoverageState): Boolean =
        state != SearchCoverageState.FULL && state != SearchCoverageState.NOT_APPLICABLE
}
