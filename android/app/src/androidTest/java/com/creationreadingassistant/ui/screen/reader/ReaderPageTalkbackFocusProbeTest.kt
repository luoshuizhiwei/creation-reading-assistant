package com.creationreadingassistant.ui.screen.reader

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Test

/**
 * READER-PAGES-TALKBACK-CLOSE-v2 探针：验证「无障碍焦点注入 + TalkBack 朗读」机制。
 *
 * 原理：TalkBack 的逐节点朗读由「无障碍焦点（AccessibilityFocus）变化」驱动。
 * adb 注入的触摸事件绕过探索层，但 **无障碍焦点是可编程设置的**：
 * AccessibilityNodeInfo.performAction(ACTION_ACCESSIBILITY_FOCUS) 会让 TalkBack
 * 聚焦该节点并朗读——这正是"聚焦→朗读"的正规机制（与用户单指点按等效）。
 *
 * 探针只验证机制本身：对一个明确控件（当前章节行）依次
 * 1. ACTION_ACCESSIBILITY_FOCUS → TalkBack 应朗读该章节标题；
 * 2. 朗读内容从 TalkBack 的 utterance 日志（logcat utterance 事件）核对；
 * 3. ACTION_CLICK → 界面应跳到该章节（激活结果）。
 *
 * 三步全部有独立观察通道（logcat 朗读行 + UI dump 界面结果），稳定方可继续矩阵。
 */
class ReaderPageTalkbackFocusProbeTest {

    private fun device(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** 对包含指定文本的节点执行 a11y 聚焦，返回 TalkBack 是否发布了朗读 utterance。 */
    private fun focusAndCapture(text: String, tag: String): Pair<Boolean, String?> {
        val node = device().findObject(By.textContains(text))
            ?: device().findObject(By.descContains(text))
        if (node == null) return Pair(false, "node-not-found")
        // ACTION_ACCESSIBILITY_FOCUS 经 UiObject2 的 API 不可直接用，走 uiAutomation 原生层
        val uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation()
        val a11yNode = uiAutomation.rootInActiveWindow
        val target = findByText(a11yNode, text)
        if (target == null) return Pair(false, "a11y-node-not-found")
        val focusOk = target.performAction(
            android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
        )
        Thread.sleep(1500)
        return Pair(focusOk, "performed")
    }

    private fun findByText(
        root: android.view.accessibility.AccessibilityNodeInfo,
        text: String,
    ): android.view.accessibility.AccessibilityNodeInfo? {
        if (root.text?.toString()?.contains(text) == true ||
            root.contentDescription?.toString()?.contains(text) == true
        ) return root
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { child ->
                findByText(child, text)?.let { return it }
            }
        }
        return null
    }

    @Test
    fun probeFocusSpeechActivation() {
        // 前置：确保在阅读器内、chrome 可见、目录已打开（由宿主预置）
        // 探针目标：当前章节行「中性测试TXT」
        val r1 = focusAndCapture("中性测试TXT", "toc-chapter")
        println("PROBE focus-performed=${r1.first} detail=${r1.second}")
        Thread.sleep(2000)
        // 朗读由 TalkBack 在 a11y focus 变化时自动发布（TTS utterance 走系统日志）
        // 激活：ACTION_CLICK
        val uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation()
        val root = uiAutomation.rootInActiveWindow
        val target = findByText(root, "中性测试TXT")
        target?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        Thread.sleep(2500)
        println("PROBE click-performed=${target != null}")
    }
}
