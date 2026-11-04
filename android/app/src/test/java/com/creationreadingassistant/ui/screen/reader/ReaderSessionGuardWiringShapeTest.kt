package com.creationreadingassistant.ui.screen.reader

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CRA-READER-GUARD-RISK-CLOSURE-v4 —— 风险 D 的**生产接线形状守卫**（结构守卫，非运行期行为测试）。
 *
 * 为什么需要它：风险 D 的最小缺口是「生产链里不存在可供会话守卫区分『用户移动』与『布局重测量』
 * 的输入」。这一缺口能不能被后续改动补上，取决于**宿主 effect 到底往守卫里送了什么**。
 * 纯逻辑测试（`ReaderSessionRemeasureRiskTest`）证明「守卫拿到坐标差会怎么判」，
 * 但证明不了「宿主只送了坐标」——本类补这一跳，并在将来有人给守卫接上用户意图信号时
 * 立刻变红，提示同步更新风险结论。
 *
 * 本类是**结构守卫**：它证明接线形状，不证明 Compose 运行期行为。运行期行为需要
 * 设备/仪器证据（见任务16-v4 REPORT 的 D 未覆盖项）。
 */
class ReaderSessionGuardWiringShapeTest {

    private val effects =
        File("src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderProgressEffects.kt")
    private val actions =
        File("src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderProgressActions.kt")

    private fun read(file: File): String {
        assertTrue(
            "源码路径必须存在（工作目录应为 app 模块根）：${file.absolutePath}",
            file.exists(),
        )
        return file.readText()
    }

    /** 从 `header` 起的首个 `(` 配平，返回括号内的实参文本。 */
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
                    if (depth == 0) return content.substring(openIndex + 1, i)
                }
            }
        }
        throw AssertionError("`$header` 的实参列表未配平")
    }

    /** 顶层实参（按逗号切分，忽略嵌套括号内的逗号），去掉空白。 */
    private fun topLevelArgs(args: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        for (ch in args) {
            when (ch) {
                '(', '[', '{' -> { depth++; current.append(ch) }
                ')', ']', '}' -> { depth--; current.append(ch) }
                ',' -> if (depth == 0) { out.add(current.toString().trim()); current.clear() } else current.append(ch)
                else -> current.append(ch)
            }
        }
        if (current.isNotBlank()) out.add(current.toString().trim())
        return out.filter { it.isNotBlank() }
    }

    /** 取 `to` 之前最近的一次 `from` 到 `to` 之间的源码片段（不含两端标记）。 */
    private fun between(content: String, from: String, to: String): String {
        val end = content.indexOf(to)
        assertTrue("必须存在 `$to`", end >= 0)
        val start = content.lastIndexOf(from, end)
        assertTrue("`$to` 之前必须存在 `$from`", start >= 0)
        return content.substring(start + from.length, end)
    }

    @Test fun realInputChannelAndNavigationAreWiredSeparatelyFromCoordinates() {
        val source = read(effects)
        assertTrue(source.contains("plainListState.interactionSource.interactions.collect"))
        assertTrue(source.contains("is DragInteraction.Start -> sessionPositionGuard.beginUserScroll("))
        assertTrue(source.contains("is DragInteraction.Stop, is DragInteraction.Cancel -> sessionPositionGuard.endUserScroll("))
    }

    @Test
    fun `the observe call takes exactly the two list coordinates and nothing else`() {
        val args = topLevelArgs(callArgs(read(effects), "sessionPositionGuard.observe("))

        assertEquals(
            "生产宿主只把 (index, offsetPx) 两个列表坐标送进会话守卫；" +
                "用户手势已由独立 interaction 通道进入守卫，坐标流不冒充用户意图，" +
                "必须同步更新风险结论与定向测试",
            listOf("index", "offsetPx"),
            args,
        )
    }

    @Test
    fun `the anchor call takes exactly the two list coordinates read from the real list state`() {
        val args = topLevelArgs(callArgs(read(effects), "sessionPositionGuard.anchor("))

        assertEquals(
            "锚点取的是恢复落位后真实列表状态的 (firstVisibleItemIndex, firstVisibleItemScrollOffset)",
            listOf(
                "plainListState.firstVisibleItemIndex",
                "plainListState.firstVisibleItemScrollOffset",
            ),
            args,
        )
    }

    @Test
    fun `the observation source is the plain list scroll position`() {
        val body = between(
            read(effects),
            "snapshotFlow {",
            "}.collect { (index, offsetPx) ->",
        ).replace(Regex("\\s+"), " ")

        assertTrue(
            "观测源必须是真实列表坐标（首可见项 + 像素偏移），而不是任何手势/意图通道",
            body.contains("plainListState.firstVisibleItemIndex") &&
                body.contains("plainListState.firstVisibleItemScrollOffset"),
        )
    }

    @Test
    fun `the exit adjudication compares the live coordinates with the anchor`() {
        val args = topLevelArgs(
            callArgs(read(actions), "sessionPositionGuard?.resolveNoMovePosition("),
        )

        assertEquals(
            "退出裁决用的也是同源实时列表坐标（没有第二条意图通道）",
            listOf(
                "currentIndex = plainListState?.firstVisibleItemIndex ?: -1",
                "currentOffsetPx = plainListState?.firstVisibleItemScrollOffset ?: -1",
            ),
            args,
        )
    }
}
