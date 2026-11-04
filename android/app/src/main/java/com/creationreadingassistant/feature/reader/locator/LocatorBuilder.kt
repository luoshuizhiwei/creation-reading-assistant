package com.creationreadingassistant.feature.reader.locator

/**
 * 统一 locator 构造入口：TXT / Markdown / EPUB 三种格式都从这里产出同一个
 * v2 [ReaderLocator]（[LocatorCodec] 编码）。
 *
 * 这是「所有格式 locator 实现统一」的落点 —— 书签、高亮、搜索跳转、进度恢复
 * 全部调用本对象，不再各写一套偏移换算。坐标语义见 [EpubLocatorMapping]。
 */
object LocatorBuilder {

    /** TXT / Markdown：单章，章内偏移 == 全书偏移（chapterIndex 恒为 0）。 */
    fun forPlain(absOffset: Int, excerpt: String?): String =
        LocatorCodec.encode(absOffset, 0, absOffset, excerpt)

    /** EPUB：全书偏移 → (章节索引, 章内偏移) 后编码。 */
    fun forEpub(absOffset: Int, chapterStartOffsets: List<Int>, excerpt: String?): String {
        val (ci, co) = EpubLocatorMapping.toChapterOffset(absOffset, chapterStartOffsets)
        return LocatorCodec.encode(absOffset, ci, co, excerpt)
    }

    /** 已知章节/章内偏移时的通用构造（书签、SE4 解析结果回写等）。 */
    fun forPosition(absOffset: Int, chapterIndex: Int, charOffset: Int, excerpt: String?): String =
        LocatorCodec.encode(absOffset, chapterIndex, charOffset, excerpt)

    /**
     * 统一进度持久化 JSON。保留 legacy [legacyOffset]（及可选 [legacyChapter] / [space]）作为
     * 顶部字段，使旧版正则解析（[com.creationreadingassistant.feature.reader.EpubRepository]、
     * [com.creationreadingassistant.ui.viewmodel.ReaderDocumentLoader.parseStoredOffset]）零改动
     * 继续工作；并附带嵌套 v2 locator（"locator_v2"），restore 端优先读它以保持跨格式一致。
     *
     * @param legacyOffset 顶部 "offset" 值（旧版语义：TXT 全书偏移 / EPUB 章内偏移）
     * @param globalOffset v2 "offset"，**全书字符偏移**。默认等于 [legacyOffset]——TXT/Markdown
     *   是单一坐标空间（chapterStart[0]=0），两者本就相同。EPUB 调用方在**拿不到章起点表**时
     *   必须显式传 null：v2 将省略 "offset" 字段，读侧按 `chapterStart[ci] + co` 派生。
     *   绝不允许把章内偏移当全局写入（违反 `offset == chapterStart[ci] + co` 不变式，
     *   是 S12-① 导入 locator 被回源跳转一致性校验拒绝的根因）。
     * @param legacyChapter EPUB 顶部 "chapter" 字段；TXT / Markdown 传 null
     */
    fun progressJson(
        legacyOffset: Int,
        chapterIndex: Int,
        charOffset: Int,
        globalOffset: Int? = legacyOffset,
        space: String? = null,
        legacyChapter: Int? = null,
        excerpt: String? = null,
    ): String {
        val v2 = LocatorCodec.encode(globalOffset, chapterIndex, charOffset, excerpt)
            .removeSurrounding("{", "}")
        return buildString {
            append('{')
            if (legacyChapter != null) append("\"chapter\":").append(legacyChapter.coerceAtLeast(0)).append(',')
            append("\"offset\":").append(legacyOffset.coerceAtLeast(0))
            if (space != null) append(",\"space\":\"").append(space).append('"')
            append(",\"locator_v2\":{").append(v2).append('}')
            append('}')
        }
    }
}
