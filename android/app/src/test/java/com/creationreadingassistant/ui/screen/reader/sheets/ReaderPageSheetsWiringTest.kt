package com.creationreadingassistant.ui.screen.reader.sheets

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.creationreadingassistant.ui.screen.reader.READER_PAGE_SHEETS
import com.creationreadingassistant.ui.screen.reader.ReaderSheet
import com.creationreadingassistant.ui.viewmodel.InspirationSaveOutcome

/**
 * READER-PAGES-v1：全屏附页改造的**纯逻辑 + 源码结构守卫**（JVM 可测部分）。
 *
 * 页面重组/Back 链/键盘等运行时行为由设备验收覆盖；本测试锁三件不会被
 * 编译器拦住、但回归风险高的形状：
 * 1. 分派集合：只有 TOC/SETTINGS/THEME/INSPIRATION 进全屏容器，其余保持弹层；
 * 2. 容器属性：Dialog 禁止系统 Back/外部点击直接关闭（关闭只能走页面返回链）；
 * 3. 灵感保存：派发处不得再「立即关页/报成功」，VM 必须发布完成信号且取消≠成功。
 */
class ReaderPageSheetsWiringTest {

    private val moduleRoot = "src/main/java/com/creationreadingassistant"

    private fun read(relative: String): String {
        val file = File("$moduleRoot/$relative")
        assertTrue("源码路径必须存在（工作目录应为 app 模块根）：${file.absolutePath}", file.exists())
        return file.readText()
    }

    // ── 1. 分派集合 ──────────────────────────────────────────────
    @Test
    fun `exactly the four contracted entries are dispatched to the page container`() {
        assertEquals(
            setOf(ReaderSheet.TOC, ReaderSheet.SETTINGS, ReaderSheet.THEME, ReaderSheet.INSPIRATION),
            READER_PAGE_SHEETS,
        )
    }

    @Test
    fun `short sheets stay in the bottom sheet dispatcher`() {
        // 其余弹层分支不得被顺手全局替换：宿主里保留 `if (type in READER_PAGE_SHEETS)` 门。
        val host = read("ui/screen/reader/ReaderSheetHost.kt")
        assertTrue(
            "分派门缺失：其他短 sheet 必须继续走 ModalBottomSheet",
            host.contains("if (type in READER_PAGE_SHEETS) return@let"),
        )
        assertTrue(host.contains("ModalBottomSheet("))
        assertTrue(host.contains("ReaderPageDialogHost("))
    }

    // ── 2. 容器属性 ──────────────────────────────────────────────
    @Test
    fun `page dialog forbids system back and outside-click dismissal`() {
        val dialog = read("ui/screen/reader/ReaderPageDialog.kt")
        assertTrue(dialog.contains("usePlatformDefaultWidth = false"))
        assertTrue(dialog.contains("dismissOnBackPress = false"))
        assertTrue(dialog.contains("dismissOnClickOutside = false"))
        // Back 优先级第一层：有键盘先收键盘。
        assertTrue(dialog.contains("WindowInsets.isImeVisible"))
        assertTrue(dialog.contains("keyboard?.hide()"))
    }

    // ── 3. 灵感保存信号 ──────────────────────────────────────────
    @Test
    fun `inspiration save dispatch no longer reports success eagerly`() {
        val builders = read("ui/screen/reader/ReaderLayerBuilders.kt")
        val start = builders.indexOf("onSaveInspiration = {")
        assertTrue("定位不到 onSaveInspiration 派发块", start >= 0)
        val block = builders.substring(start).let {
            val end = it.indexOf("onCreateCategory =")
            if (end > 0) it.substring(0, end) else it
        }
        assertFalse(
            "onSaveInspiration 不得再派发后立即关页（成功必须等落库信号）",
            block.contains("ReaderAction.CloseSheet"),
        )
        assertFalse(
            "onSaveInspiration 不得再派发后立即提示成功",
            block.contains("showNotice("),
        )
        assertFalse(
            "onSaveInspiration 不得再派发后立即清选区",
            block.contains("ReaderAction.ClearSelection"),
        )
        assertTrue(
            "实体 id 必须来自附页传入的稳定 entityId（重试复用）",
            block.contains("id = entityId"),
        )
        assertTrue(
            "locator 必须原样传给 payload（快照同源，参照 S07-F2.4 要求 7）",
            block.contains("locatorJson = locatorJson"),
        )
    }

    @Test
    fun `view model publishes real save outcome and never treats cancellation as success`() {
        val vm = read("ui/viewmodel/ReaderViewModel.kt")
        assertTrue(
            "落库成功必须发布 Saved 信号",
            vm.contains("InspirationSaveOutcome.Saved("),
        )
        assertTrue(
            "落库失败必须发布 Failed 信号（不吞异常）",
            vm.contains("InspirationSaveOutcome.Failed("),
        )
        assertTrue(
            "取消协程必须原样抛出（取消≠成功）",
            vm.contains("throw ce"),
        )
    }

    @Test
    fun `outcome instances stay distinct across retries`() {
        // 同 id 同错误的重试：seq 单调递增，StateFlow 不会把重试结果当重复值吞掉。
        val first = InspirationSaveOutcome.Failed(seq = 1, requestId = "id-a", message = "boom")
        val retry = InspirationSaveOutcome.Failed(seq = 2, requestId = "id-a", message = "boom")
        assertNotEquals(first, retry)
        assertEquals(InspirationSaveOutcome.Saved(seq = 3, requestId = "id-a"), InspirationSaveOutcome.Saved(seq = 3, requestId = "id-a"))
    }

    // ── 4. 灵感草稿修改判据（Back 确认弹窗口径）──────────────────
    @Test
    fun `draft change detection treats defaults as unchanged`() {
        val initialTags = listOf("书名", "阅读灵感")
        assertFalse(
            inspirationDraftHasChanges(
                initialTitle = "阅读灵感：书名",
                initialTags = initialTags,
                title = "阅读灵感：书名",
                body = "",
                tags = initialTags,
                categoryIds = emptyList(),
            ),
        )
        assertTrue(
            inspirationDraftHasChanges(
                initialTitle = "阅读灵感：书名",
                initialTags = initialTags,
                title = "我的标题",
                body = "",
                tags = initialTags,
                categoryIds = emptyList(),
            ),
        )
        assertTrue(
            inspirationDraftHasChanges(
                initialTitle = "阅读灵感：书名",
                initialTags = initialTags,
                title = "阅读灵感：书名",
                body = "写了一点想法",
                tags = initialTags,
                categoryIds = emptyList(),
            ),
        )
        // 只删掉默认标签也算修改（离开需确认）。
        assertTrue(
            inspirationDraftHasChanges(
                initialTitle = "阅读灵感：书名",
                initialTags = initialTags,
                title = "阅读灵感：书名",
                body = "",
                tags = emptyList(),
                categoryIds = emptyList(),
            ),
        )
        // 纯空格正文不算修改。
        assertFalse(
            inspirationDraftHasChanges(
                initialTitle = "阅读灵感：书名",
                initialTags = initialTags,
                title = "阅读灵感：书名",
                body = "   ",
                tags = initialTags,
                categoryIds = emptyList(),
            ),
        )
    }
}
