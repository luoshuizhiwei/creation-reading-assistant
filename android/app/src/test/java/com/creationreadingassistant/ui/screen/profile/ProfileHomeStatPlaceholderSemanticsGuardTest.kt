package com.creationreadingassistant.ui.screen.profile

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `HomeStat` 隐形定宽占位的**源码级结构守卫**（JVM 可跑）。
 *
 * 为什么需要它：占位文本的行为语义由 androidTest
 * `ProfileHomeScreenGestureTest` 在设备上验证（唯一节点、合并语义只朗读一次），
 * 但那是仪器测试，本窗口只编译不运行。若有人把 `clearAndSetSemantics` 删掉：
 * - 透明占位会带着与可见值**同文本同坐标**的节点回到语义树；
 * - 读屏在卡片合并语义里把同一个时长朗读两遍；
 * - `onNodeWithText(时长文本)` 又会命中两个节点，G6 手势用例再次红。
 * 这些都不会被 JVM 纯函数测试发现，故用本类把接线形状钉住。
 *
 * **边界**：本类证明源码形状，不证明 Compose 运行期语义树。运行期结论以设备上的
 * `ProfileHomeScreenGestureTest` 为准（本窗口不操作设备）。
 */
class ProfileHomeStatPlaceholderSemanticsGuardTest {

    private val screenSource =
        File("src/main/java/com/creationreadingassistant/ui/screen/profile/ProfileHomeScreen.kt")

    private fun source(): String {
        assertTrue(
            "源码路径必须存在（工作目录应为 app 模块根）：${screenSource.absolutePath}",
            screenSource.exists(),
        )
        return screenSource.readText()
    }

    /** 从 [openIndex] 处的 [open] 起配平到对应 [close]，返回其下标；不闭合返回 -1。 */
    private fun matchIndex(text: String, openIndex: Int, open: Char, close: Char): Int {
        var depth = 0
        for (i in openIndex until text.length) {
            when (text[i]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    /**
     * `private fun HomeStat(...) { ... }` 的**函数体**原文。
     *
     * 注意：必须跳过参数列表再找 `{` —— 参数默认值里就有 `{ "$it" }`，
     * 直接取首个 `{` 会拿到那个默认值 lambda 而不是函数体。
     */
    private fun homeStatBody(): String {
        val text = source()
        val header = text.indexOf("private fun HomeStat(")
        assertTrue("HomeStat 必须定义在 ProfileHomeScreen.kt", header >= 0)
        val openParen = text.indexOf('(', header)
        val closeParen = matchIndex(text, openParen, '(', ')')
        assertTrue("HomeStat 参数列表必须闭合", closeParen > openParen)
        val openBrace = text.indexOf('{', closeParen)
        assertTrue("HomeStat 必须有函数体", openBrace > closeParen)
        val closeBrace = matchIndex(text, openBrace, '{', '}')
        assertTrue("HomeStat 函数体大括号必须闭合", closeBrace > openBrace)
        return text.substring(openBrace, closeBrace + 1)
    }

    /** 抓取 [body] 内每一段 `Text(...)` 调用原文（按圆括号配平）。 */
    private fun textCalls(body: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (true) {
            val k = body.indexOf("Text(", i)
            if (k < 0) break
            val prev = body.getOrNull(k - 1)
            if (prev == null || !(prev.isLetterOrDigit() || prev == '_')) {
                val open = k + "Text".length
                val close = matchIndex(body, open, '(', ')')
                if (close > open) out.add(body.substring(open, close + 1))
            }
            i = k + 1
        }
        return out
    }

    @Test
    fun invisible_width_placeholder_leaves_semantics_tree() {
        val calls = textCalls(homeStatBody())

        val placeholder = calls.filter { it.contains("Color.Transparent") }
        assertEquals(
            "HomeStat 应恰好有一个透明占位文本（定宽用）；实际 Text 调用：" + calls.size,
            1,
            placeholder.size,
        )
        assertTrue(
            "透明占位必须带 clearAndSetSemantics 退出无障碍语义树，否则与可见值同文本同坐标重复朗读：" +
                placeholder.single(),
            placeholder.single().contains("clearAndSetSemantics"),
        )

        val visible = calls.filter { it.contains("rememberCountUp(") }
        assertEquals("HomeStat 应恰好有一个 count-up 可见值", 1, visible.size)
        assertFalse(
            "可见值必须留在语义树里，不得 clearAndSetSemantics：" + visible.single(),
            visible.single().contains("clearAndSetSemantics"),
        )

        // 占位与可见值必须走同一个 format 结果，宽度预留才成立。
        assertTrue(
            "占位文本必须用最终值 format(value) 定宽：" + placeholder.single(),
            placeholder.single().contains("format(value)"),
        )
        assertTrue(
            "可见值必须由同一个 format 渲染：" + visible.single(),
            visible.single().contains("format("),
        )
    }

    @Test
    fun card_remains_the_single_gesture_layer() {
        // 行为保留版契约：整卡唯一手势层（点击=打开阅读档案，长按=切换时长单位），
        // 子文本不挂任何手势。占位文本的语义修正不得顺手改动这层归属。
        val text = source()
        assertTrue(
            "档案卡必须挂带 label 的长按入口",
            text.contains("onLongClickLabel = DURATION_UNIT_TOGGLE_LABEL"),
        )
        assertTrue(
            "档案卡必须保留整卡点击（打开阅读档案）",
            text.contains("onClickLabel = \"查看阅读档案\""),
        )
        assertFalse(
            "HomeStat 内不得再挂 durationUnitToggle：手势必须由整卡唯一拥有",
            homeStatBody().contains("durationUnitToggle"),
        )
    }
}
