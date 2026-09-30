package com.creationreadingassistant.ui.screen.reader

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A01：目录与标记「笔记」Tab 的**生产接线结构守卫**。
 *
 * 为什么需要它：A01 的缺陷本质是**漏接线**，而漏接线之所以能长期存在，是因为
 * `TocSheet` 的 `notes` 参数带 `emptyList()` 默认值 —— 宿主不传也能编译通过，
 * 页面稳定显示空态。纯逻辑测试（`TocNoteEntriesTest`）证明「数据源应该怎么算」，
 * 但**证明不了宿主真的把 highlights 传进去了**。本类补这一跳。
 *
 * 覆盖的改法（这些改法都不会让纯逻辑测试变红）：
 * 1. 宿主重新不传 `highlights` → 页面回到空态（变异 A 的目标）；
 * 2. 宿主把书签一起塞进笔记 Tab 的数据源 → 书签重复出现；
 * 3. 普通笔记重新走 BOOKMARK typed 目标（变异 B 的目标）；
 * 4. 笔记 Tab 的条目 key 去掉类型前缀 → 跨表相同 UUID 冲突。
 *
 * 本类是**结构守卫**，不是 Compose 运行期测试：它证明接线形状，不证明渲染结果。
 * 渲染结果由 `ReaderTocSheetTest`（instrumentation）覆盖，本报告记为「已编译未执行」。
 */
class TocNotesWiringGuardTest {

    private val sheetHost =
        File("src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderSheetHost.kt")

    private fun source(): String {
        assertTrue(
            "源码路径必须存在（工作目录应为 app 模块根）：${sheetHost.absolutePath}",
            sheetHost.exists(),
        )
        return sheetHost.readText()
    }

    /** 从 `[header]` 起的首个 `(` 配平，返回含两端括号的实参文本。 */
    private fun callArgs(content: String, header: String): String {
        val headerIndex = content.indexOf(header)
        assertTrue("必须存在 `$header` 调用", headerIndex >= 0)
        val openIndex = content.indexOf('(', headerIndex + header.length - 1)
        assertTrue("`$header` 后必须有实参列表", openIndex >= 0)
        var depth = 0
        for (i in openIndex until content.length) {
            when (content[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return content.substring(openIndex, i + 1)
                }
            }
        }
        throw AssertionError("`$header` 调用括号不配平")
    }

    private fun tocSheetArgs(): String = callArgs(source(), "TocSheet(")

    private fun notesSheetArgs(): String =
        callArgs(source(), "ReaderSheet.NOTES -> NotesSheet(")

    // ── 1. 宿主必须把 highlights 传进 TocSheet（变异 A 的目标） ───────

    @Test
    fun `the host passes the book highlights into the toc sheet`() {
        val args = tocSheetArgs()

        assertTrue(
            "TocSheet 必须显式收到 inputs.highlights（A01 根因：高亮没有参数入口）",
            args.contains("highlights = inputs.highlights"),
        )
    }

    @Test
    fun `the host passes non bookmark notes into the toc sheet`() {
        val args = tocSheetArgs()

        assertTrue(
            "TocSheet 的 notes 必须由 tocNoteEntries(highlights, notes) 产出，不得继续依赖默认空列表",
            args.contains("notes = tocNoteEntries(") &&
                args.contains("highlights = inputs.highlights") &&
                args.contains("notes = inputs.notes"),
        )
    }

    @Test
    fun `the host keeps bookmarks separate from the notes tab source`() {
        val args = tocSheetArgs()

        assertTrue(
            "书签必须单独传 bookmarks =（kind == 书签常量），不得与笔记 Tab 合并",
            args.contains("bookmarks = inputs.notes.filter { it.kind == NOTE_KIND_BOOKMARK }"),
        )
        // 书签与笔记是两个不同实参：合并后书签会在两个 Tab 各出现一次。
        assertTrue("书签与笔记必须是两个独立实参", args.contains("onPickBookmark =") && args.contains("onPickNote ="))
    }

    // ── 2. 三种类型各自走 typed 回源（变异 B 的目标） ─────────────────

    @Test
    fun `the bookmark jump uses the bookmark typed target`() {
        val args = tocSheetArgs()

        assertTrue(
            "书签跳转必须经 noteNavigationTarget 生成 typed 目标（kind 决定 BOOKMARK）",
            args.contains("onPickBookmark = { bm -> sheetCallbacks.onJumpToTypedAnnotation(noteNavigationTarget(bm)) }"),
        )
    }

    @Test
    fun `the notes tab jump carries the entry's own typed target`() {
        val args = tocSheetArgs()

        assertTrue(
            "笔记 Tab 条目必须用条目自带的 navigationTarget（高亮/笔记类型由条目决定）",
            args.contains("sheetCallbacks.onJumpToTypedAnnotation(entry.navigationTarget)"),
        )
    }

    @Test
    fun `the plain note jump no longer borrows the bookmark callback`() {
        // A01 缺陷 6：普通笔记点击复用书签回调，跨表同 rawId 会跳错表。
        val args = tocSheetArgs()

        assertFalse(
            "TocSheet 的普通笔记不得再走 onJumpToBookmark",
            args.contains("onPickNote = { n -> sheetCallbacks.onJumpToBookmark(n.id) }"),
        )
        assertFalse(
            "TocSheet 不得再存在裸 rawId 跳转入口",
            args.contains("onJumpToHighlight(") || args.contains("onJumpToBookmark("),
        )
    }

    // ── 3. 完整笔记 Sheet 的普通笔记跳转也必须 typed（变异 B 的目标） ──

    @Test
    fun `the notes sheet routes plain notes through NOTE not BOOKMARK`() {
        val args = notesSheetArgs()

        assertTrue(
            "笔记 Sheet 必须把普通笔记走 noteNavigationTarget（按 kind 分派 NOTE/BOOKMARK）",
            args.contains("onJumpToNote = { n ->") &&
                args.contains("sheetCallbacks.onJumpToTypedAnnotation(noteNavigationTarget(n))"),
        )
        assertTrue(
            "笔记 Sheet 的高亮走 highlightNavigationTarget",
            args.contains("sheetCallbacks.onJumpToTypedAnnotation(highlightNavigationTarget(h))"),
        )
        assertFalse(
            "笔记 Sheet 的普通笔记不得再固定 BOOKMARK 类型",
            args.contains("AnnotationType.BOOKMARK, n.id"),
        )
    }

    @Test
    fun `the callback surface no longer exposes raw id jumps`() {
        // 结构性防线：宿主回调集合里不应再有收裸 rawId 的跳转入口，
        // 否则调用方仍可能传裸 id 走「先高亮、后笔记」的兼容回退。
        val content = source()

        assertFalse(
            "ReaderSheetHostCallbacks 不得再暴露 onJumpToHighlight(rawId)",
            content.contains("val onJumpToHighlight: (String) -> Unit"),
        )
        assertFalse(
            "ReaderSheetHostCallbacks 不得再暴露 onJumpToBookmark(rawId)",
            content.contains("val onJumpToBookmark: (String) -> Unit"),
        )
        assertTrue(
            "必须只保留 typed 入口 onJumpToTypedAnnotation",
            content.contains("val onJumpToTypedAnnotation: (String) -> Unit"),
        )
    }

    // ── 4. 列表 key 带类型前缀（防跨表同 UUID 冲突） ─────────────────

    @Test
    fun `the notes tab list key is the typed target so cross table ids never collide`() {
        val listFile = File(
            "src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderTocBookmarkList.kt",
        )
        assertTrue("列表文件必须存在", listFile.exists())
        val content = listFile.readText()

        assertTrue(
            "NotesTabContent 必须用 entry.listKey 作 LazyColumn key（类型前缀 + rawId）",
            content.contains("key = { it.listKey }"),
        )
        assertFalse(
            "不得退回裸 id 作 key（跨表同 UUID 会冲突）",
            content.contains("key = { \"note_\${it.id}\" }"),
        )
    }
}
