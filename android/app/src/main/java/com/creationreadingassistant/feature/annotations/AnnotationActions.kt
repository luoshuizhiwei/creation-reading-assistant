package com.creationreadingassistant.feature.annotations

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 「我的 → 阅读笔记」页的副作用动作集合：跳转定位、删除（可撤销）、
 * 编辑批注、改高亮颜色、导出 / 分享。
 *
 * 由 ProfileRoute（拥有导航 / Snackbar / SAF launcher / ViewModel 的层）实现，
 * 经 [LocalAnnotationActions] 提供给纯渲染的 ReadingNotesSubPage，
 * 与 LocalProfileSnackbar 的桥接模式一致 —— 不改 ProfileScreen 的调用签名。
 * 未提供时（预览 / 单测）ReadingNotesSubPage 降级为无副作用渲染。
 */
interface AnnotationActions {
    /** 跳到条目来源：有 locator 走精确跳转（reader/{bookId}?highlightId={rawId}），
     *  无 locator 降级为打开书籍，无书籍则由实现方提示。 */
    fun jumpToEntry(entry: AnnotationEntry)

    /**
     * 临时查阅条目来源（J1-I.2）：以**临时查阅模式**跳到条目记录的 source 位置，阅读后可用
     * 「返回阅读处」/ 顶栏 Back / 系统 Back 回到进入前的普通阅读位置（允许跨书）。
     *
     * 契约：
     * - 只有携带有效 source locator 的条目才有意义；实现方据 [AnnotationEntry.hasLocator]
     *   决定是否暴露入口，无有效坐标时不得伪造位置。
     * - 实现方**只负责导航**（`navigate(readerTemporaryRouteForSource(...))`）；进入临时查阅时的
     *   返回栈推进由 ReaderRoute 在解析到 `navigationMode=temporary` 时触发，实现方不得自行调用
     *   `beginTemporaryInspection`。
     * - 与 [jumpToEntry] 的区别：后者是普通跳转（改变/延续普通阅读位置），本方法是可回退的一次性查阅。
     */
    fun inspectSourceTemporarily(entry: AnnotationEntry)

    /** 删除一批条目（软删除）。实现方须支持**仅针对本次操作**的撤销。 */
    fun deleteEntries(entries: List<AnnotationEntry>)

    /** 编辑个人批注并保存（高亮的 note / 笔记的 body），保留既有定位信息。 */
    fun editAnnotation(entry: AnnotationEntry, newAnnotation: String)

    /** 修改高亮颜色（仅 HIGHLIGHT 类型有效）。 */
    fun changeHighlightColor(entry: AnnotationEntry, color: String)

    /** SAF 建档导出 Markdown。用户取消不得产生任何数据修改。 */
    fun exportMarkdown(markdown: String, suggestedFileName: String)

    /** 系统分享面板分享 Markdown 文本。 */
    fun shareMarkdown(markdown: String)
}

/** Route 层 → 阅读笔记子页的动作桥。默认 null：子页降级为纯展示。 */
val LocalAnnotationActions = staticCompositionLocalOf<AnnotationActions?> { null }