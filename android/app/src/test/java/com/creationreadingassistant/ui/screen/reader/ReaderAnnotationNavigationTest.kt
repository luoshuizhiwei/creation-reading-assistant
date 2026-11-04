package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CRA-MD-ANNOTATION-RETURN-FIX-v1：书内高亮/笔记「跳转」分派的生产消费路径回归。
 *
 * 生产事实（journey v2 J3 实测）：Markdown 书高亮 locator `offset=1270` 保存/冷读一致，
 * 但两次点「跳转」停在 8.7%、无提示、无位移——SE4 effect 只分派 EPUB/plain，Markdown
 * 的 `plainContent` 为空（ReaderScreen 语义），两支皆不命中，pending 被静默清除。
 *
 * 修复方式：分派决策提取为 [annotationNavigationPlan]（穷尽 sealed 计划），SE4 effect
 * 实际构造并 `when` 消费；Markdown 支的动作面由 [applyMarkdownAnnotationPlan] 执行——
 * 本测试断言的正是这两个生产 effect 调用的函数，不是平行镜像实现。
 * EPUB/plain 分支保持原内部逻辑与优先级（EpubBranch / PlainBranch 时 effect 不走
 * apply 路径，由 `seekToPercent`/既有分支处理，见本类 TXT/EPUB 路由守卫用例）。
 */
class ReaderAnnotationNavigationTest {

    // journey 实测形态：canonical 全局偏移 + locator_v2 同值
    private val canonicalLocator = """{"offset":1270,"space":"canonical","locator_v2":{"v":2,"ci":1,"co":270}}"""

    // ── 1. 分派计划：Markdown + 可解码 locator → SE3 canonical 通道，偏移原文保留 ──

    @Test
    fun `canonical markdown locator defers to source navigation with offset intact`() {
        val decoded = LocatorCodec.decode(canonicalLocator)
        assertTrue("canonical locator 必须可解码", decoded != null)
        val plan = annotationNavigationPlan(
            epubBookPresent = false,
            markdownDocumentPresent = true,
            plainUsable = false, // ReaderScreen：Markdown 的 plainContent 为空
            decodedLocatorPresent = decoded != null,
            locatorJson = canonicalLocator,
            targetProgress = 8.7f,
            markdownTotalChars = 2000,
        )
        val expected = AnnotationNavigationPlan.MarkdownSourceNavigationDeferred(canonicalLocator)
        assertEquals("Markdown 高亮必须进入 canonical 定位通道且原 locator（含偏移）不丢失", expected, plan)
        assertEquals(
            "交给延迟导航通道的 locator 必须与保存值逐字一致",
            1270,
            LocatorCodec.decode((plan as AnnotationNavigationPlan.MarkdownSourceNavigationDeferred).locatorJson)?.legacyOffset,
        )
    }

    @Test
    fun `legacy chaptered locator also routes to markdown channel`() {
        val legacy = """{"chapter":1,"offset":25}"""
        val decoded = LocatorCodec.decode(legacy)
        assertTrue("legacy 形态必须可解码（兼容合同）", decoded != null)
        val plan = annotationNavigationPlan(
            epubBookPresent = false,
            markdownDocumentPresent = true,
            plainUsable = false,
            decodedLocatorPresent = true,
            locatorJson = legacy,
            targetProgress = null,
            markdownTotalChars = 2000,
        )
        assertEquals(AnnotationNavigationPlan.MarkdownSourceNavigationDeferred(legacy), plan)
    }

    // ── 2. 无 locator 的进度兜底与失败反馈 ────────────────────────────────

    @Test
    fun `markdown without locator but with progress computes canonical offset`() {
        val plan = annotationNavigationPlan(
            epubBookPresent = false,
            markdownDocumentPresent = true,
            plainUsable = false,
            decodedLocatorPresent = false,
            locatorJson = null,
            targetProgress = 63.5f,
            markdownTotalChars = 2000,
        )
        assertEquals(AnnotationNavigationPlan.MarkdownProgressJump(1270), plan)
    }

    @Test
    fun `markdown with neither locator nor progress fails loudly not silently`() {
        val plan = annotationNavigationPlan(
            epubBookPresent = false,
            markdownDocumentPresent = true,
            plainUsable = false,
            decodedLocatorPresent = false,
            locatorJson = "not-a-locator",
            targetProgress = null,
            markdownTotalChars = 2000,
        )
        assertTrue("必须给出明确失败反馈面", plan is AnnotationNavigationPlan.MarkdownFailed)
    }

    // ── 3. 消费动作：deferred→SE3 通道；progress→jumpToMarkdownOffset；失败→notice ──

    @Test
    fun `apply plan hands deferred locator to source channel without touching plain or markdown jump`() {
        var handedOff: String? = null
        val markdownJumps = mutableListOf<Int>()
        val plainJumps = mutableListOf<Int>()
        val notices = mutableListOf<String>()
        val consumed = applyMarkdownAnnotationPlan(
            plan = AnnotationNavigationPlan.MarkdownSourceNavigationDeferred(canonicalLocator),
            handOffToSourceNavigation = { handedOff = it },
            markdownJump = { markdownJumps += it },
            plainJump = { plainJumps += it },
            notice = { notices += it },
        )
        assertTrue("Markdown 计划必须由 apply 消费", consumed)
        assertEquals("偏移经 SE3 通道原文移交", canonicalLocator, handedOff)
        assertTrue("不得误走 Markdown 直接跳转", markdownJumps.isEmpty())
        assertTrue("绝不得误走 plain/TXT 跳转", plainJumps.isEmpty())
        assertTrue("成功路径不得伪造失败提示", notices.isEmpty())
    }

    @Test
    fun `apply progress plan jumps markdown offset with consumed flag`() {
        val markdownJumps = mutableListOf<Int>()
        var plainCalled = false
        val consumed = applyMarkdownAnnotationPlan(
            plan = AnnotationNavigationPlan.MarkdownProgressJump(1270),
            handOffToSourceNavigation = { error("不应移交 SE3") },
            markdownJump = { markdownJumps += it },
            plainJump = { plainCalled = true },
            notice = { error("不应有失败提示") },
        )
        assertTrue(consumed)
        assertEquals(listOf(1270), markdownJumps)
        assertTrue("Markdown 分派不得调用 plain 导航", !plainCalled)
    }

    @Test
    fun `apply failed plan shows notice exactly once`() {
        val notices = mutableListOf<String>()
        val consumed = applyMarkdownAnnotationPlan(
            plan = AnnotationNavigationPlan.MarkdownFailed("无法定位该阅读笔记的正文位置"),
            handOffToSourceNavigation = { error("不应移交") },
            markdownJump = { error("不应跳转") },
            plainJump = { error("不应 plain") },
            notice = { notices += it },
        )
        assertTrue(consumed)
        assertEquals(listOf("无法定位该阅读笔记的正文位置"), notices)
    }

    // ── 4. TXT/EPUB 分支未被误路由（apply 对两分支必须返回未消费）──────────

    @Test
    fun `epub and plain plans are not consumed by markdown apply`() {
        var anyCallback = false
        val planCalls = listOf(
            AnnotationNavigationPlan.EpubBranch,
            AnnotationNavigationPlan.PlainBranch,
        )
        planCalls.forEach { plan ->
            val consumed = applyMarkdownAnnotationPlan(
                plan = plan,
                handOffToSourceNavigation = { anyCallback = true },
                markdownJump = { anyCallback = true },
                plainJump = { anyCallback = true },
                notice = { anyCallback = true },
            )
            assertEquals("EPUB/plain 必须回到 effect 既有内部逻辑（apply 不消费、不回调）", false, consumed)
        }
        assertTrue("守卫失效自检", !anyCallback)
        // SilentClear：消费但无动作（原行为保持）
        val silentConsumed = applyMarkdownAnnotationPlan(
            plan = AnnotationNavigationPlan.SilentClear,
            handOffToSourceNavigation = { error("不应有动作") },
            markdownJump = { error("不应有动作") },
            plainJump = { error("不应有动作") },
            notice = { error("不应有提示") },
        )
        assertTrue(silentConsumed)
    }

    @Test
    fun `plan keeps existing priority epub before markdown before plain`() {
        // 三种形态同时给出时，优先级与既有分派一致（EPUB 最前；本轮只新增 Markdown 支）
        assertEquals(
            AnnotationNavigationPlan.EpubBranch,
            annotationNavigationPlan(
                epubBookPresent = true, markdownDocumentPresent = true, plainUsable = true,
                decodedLocatorPresent = true, locatorJson = canonicalLocator,
                targetProgress = null, markdownTotalChars = 1,
            ),
        )
        // plain 可用且无 markdown：保持 PlainBranch（TXT 行为不变）
        assertEquals(
            AnnotationNavigationPlan.PlainBranch,
            annotationNavigationPlan(
                epubBookPresent = false, markdownDocumentPresent = false, plainUsable = true,
                decodedLocatorPresent = true, locatorJson = canonicalLocator,
                targetProgress = null, markdownTotalChars = 1,
            ),
        )
    }

    @Test
    fun `progress offset contract clamps percent and range`() {
        assertEquals(0, offsetFromProgress(-5f, 1000))
        assertEquals(1000, offsetFromProgress(105f, 1000))
        assertEquals(1270, offsetFromProgress(63.5f, 2000))
        assertEquals(0, offsetFromProgress(50f, 0))
        assertNull("totalChars=0 不抛异常", LocatorCodec.decode(null))
    }
}
