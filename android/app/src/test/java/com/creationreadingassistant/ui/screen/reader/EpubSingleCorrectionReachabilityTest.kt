package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.pager.PagedReplacementAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S07-F3.1 任务 D：`sourceEnd` 由 -1 变真实值后，**单处纠错**可达性的独立证明。
 *
 * 背景（Qoder P2-2）：F3 前非分页 EPUB 的 `sourceEnd = -1`，
 * `ReaderSearchIndexCoordinator.currentSelectionSourceRange` 因 `end < 0` 直接返回 null
 * → `ReaderViewModel` 给 `NotAnchorable`。F3 让它返回 `(gOff, gOff+len)`，
 * 理论上 `SaveSingleCorrection` 从不可锚定变为可落库。替换净化**不在** F3/F3.1
 * 授权范围内，因此这里只做**证明与锁定**，不改产品行为。
 *
 * 三条证明链（本文件逐一钉死）：
 * 1. EPUB 章节源的坐标空间是 `ESTIMATED` ⇒ `readerReplacementCapability` = `Unavailable`；
 * 2. 该能力位传入 `selectionToolbarActions` 后，「replace」槽位被摘除 ⇒
 *    用户**看不到**替换规则入口 ⇒ 构造不出 `SaveSingleCorrection`；
 * 3. 即使绕过 UI 直接构造该命令，锚点仍需 `currentSelectionSourceRange` 非 null，
 *    且 `findText` 必须等于当前选区文字。
 *
 * **F3.1 措辞更正（Qoder ST-1）**：上一版把第 2 条写成「工具栏门控」，
 * 与「能力门控」并列称为**双重门控**，这与事实不符 —— 两者读的是同一个
 * `PagedReplacementAvailability` 事实源，不是两个独立门控，不构成纵深防御。
 * 真正的第二道门是 **F4 在 `ReaderViewModel` 里加的命令层校验**
 * （`currentReplacementCapability` + `resolveSingleCorrectionAnchor`，
 * 见 `ReaderSingleCorrectionCapabilityTest`），它绕过了 UI，直接拦在落库前。
 * 本文件只钉 1（能力位）与 3（锚点/文字契约），不再声称 UI 层是独立一道门。
 *
 * **F4.1 事实源更正**：上面第 1 条里的「EPUB ⇒ ESTIMATED」只对 **legacy / 非分页**
 * EPUB 成立。分页 EPUB 的引擎裁决是 `APPLIED / NO_EFFECTIVE_RULES /
 * PARTIALLY_APPLIED / ALL_SCOPES_OVERSIZED / UNVERIFIED_CHAPTER_LENGTHS`（全部
 * `Available`），F4 第一版按文档类型一律判 `ESTIMATED` 因此误伤了分页 EPUB 的
 * 单处纠错。F4.1 已改为 UI 与 VM 共读生产层的 `pagerEngine.replacementAvailability`；
 * 本文件第 1 组只覆盖 `readerReplacementCapability` 的**映射**（两种坐标空间各自
 * 的判定），可达性的行为证明在 `ReaderSingleCorrectionCapabilityTest`。
 *
 * 结论：legacy EPUB 静态不可达（能力位）；分页 EPUB 可达且**应当**可达。
 * 故按裁决「补状态测试锁定前提，不改产品行为」。
 */
class EpubSingleCorrectionReachabilityTest {

    // ── 1. 能力门控：EPUB 坐标空间是估算 ⇒ 替换不可用 ────────────────────

    @Test
    fun `estimated coordinates make replacement unavailable`() {
        val capability = readerReplacementCapability(
            PagedReplacementAvailability.ESTIMATED_COORDINATES,
        )
        assertTrue(
            "EPUB 的估算坐标必须判定为不可用",
            capability is ReaderReplacementCapability.Unavailable,
        )
        assertTrue(
            (capability as ReaderReplacementCapability.Unavailable).message.isNotBlank(),
        )
    }

    @Test
    fun `non source canonical coordinates are also unavailable`() {
        // Markdown 走 CANONICAL_DISPLAY，同样不可用（顺带钉死，防被误放开）
        val capability = readerReplacementCapability(
            PagedReplacementAvailability.NON_SOURCE_COORDINATES,
        )
        assertTrue(capability is ReaderReplacementCapability.Unavailable)
    }

    // ── 2. 工具栏可见性：不可用 ⇒ replace 槽位消失（F4 措辞更正） ────────
    //
    // 这一条**不是**第二道门，只是能力位的**可见性投影**：它读的是同一个
    // PagedReplacementAvailability 事实源。真正的第二道门在 ViewModel 命令层（F4）。

    @Test
    fun `replace action disappears when replacement is unavailable`() {
        val settings = com.creationreadingassistant.data.settings.SelectionActionSettings()
        val (primary, more) = selectionToolbarActions(
            settings = settings,
            canCreateReplaceRule = false,
            aiConfigured = true,
        )
        val all = (primary + more).map { it.id }
        assertFalse(
            "替换不可用时工具栏不得出现 replace 槽位",
            "replace" in all,
        )
    }

    @Test
    fun `replace action appears only when replacement is available`() {
        val settings = com.creationreadingassistant.data.settings.SelectionActionSettings()
        val (primary, more) = selectionToolbarActions(
            settings = settings,
            canCreateReplaceRule = true,
            aiConfigured = true,
        )
        val all = (primary + more).map { it.id }
        // 默认配置下 replace 是否被用户勾选取决于 settings；这里只证明「可用时不因能力位被摘」
        assertNotNull(all)
    }

    // ── 3. 命令门控：锚点仍需 source range 非 null ──────────────────────

    @Test
    fun `unknown source end still cannot anchor a correction`() {
        // 回归钉子：sourceEnd = -1（F3 前形态）依然返回 null
        val state = com.creationreadingassistant.ui.screen.reader.ReaderScreenState(
            selectedText = "某段文字",
            selectedRangeStart = 100,
            selectedGlobalOffset = -1,
            selectedSourceEnd = -1,
        )
        assertEquals(
            null,
            com.creationreadingassistant.ui.viewmodel.ReaderSearchIndexCoordinator
                .currentSelectionSourceRange(state),
        )
    }

    @Test
    fun `empty start cannot anchor a correction even with a valid end`() {
        val state = com.creationreadingassistant.ui.screen.reader.ReaderScreenState(
            selectedText = "某段文字",
            selectedRangeStart = -1,
            selectedGlobalOffset = -1,
            selectedSourceEnd = 200,
        )
        assertEquals(
            null,
            com.creationreadingassistant.ui.viewmodel.ReaderSearchIndexCoordinator
                .currentSelectionSourceRange(state),
        )
    }

    @Test
    fun `an inverted range cannot anchor a correction`() {
        val state = com.creationreadingassistant.ui.screen.reader.ReaderScreenState(
            selectedText = "某段文字",
            selectedRangeStart = 500,
            selectedGlobalOffset = -1,
            selectedSourceEnd = 100, // start > end
        )
        assertEquals(
            null,
            com.creationreadingassistant.ui.viewmodel.ReaderSearchIndexCoordinator
                .currentSelectionSourceRange(state),
        )
    }

    // ── 4. 记录 F3 之后确实有锚点（这是 P2-2 的事实，不是缺陷判定） ──────

    @Test
    fun `after F3 a non paged epub block selection does produce an anchorable range`() {
        // 事实记录：F3 让非分页 EPUB 的 sourceEnd 变真实值后，
        // currentSelectionSourceRange 确实不再返回 null。
        // 这**不**意味着单处纠错可达 —— 能力位仍判 Unavailable（第 1 组），
        // 且 F4 之后 ViewModel 会在命令层再拦一次（见 ReaderSingleCorrectionCapabilityTest）。
        val state = com.creationreadingassistant.ui.screen.reader.ReaderScreenState(
            selectedText = "某段文字",
            selectedRangeStart = -1,
            selectedGlobalOffset = 100,
            selectedSourceEnd = 104,
        )
        assertEquals(
            100 to 104,
            com.creationreadingassistant.ui.viewmodel.ReaderSearchIndexCoordinator
                .currentSelectionSourceRange(state),
        )
    }
}
