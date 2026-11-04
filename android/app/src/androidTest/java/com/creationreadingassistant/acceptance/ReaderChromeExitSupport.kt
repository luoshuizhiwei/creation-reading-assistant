package com.creationreadingassistant.acceptance

import android.accessibilityservice.AccessibilityService
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.test.platform.app.InstrumentationRegistry

/**
 * 任务72-v1-A / 73-v1-B：阅读器真实退出入口。
 *
 * 生产规则（ReaderChromeReducer）：`PageTurn` 会把 `controlsVisible` 置 false，
 * `ReaderTopChrome` 位于 `AnimatedVisibility(visible = controlsVisible)` 内，因此翻页后
 * `contentDescription = "返回"` 的节点会离开语义树。真实用户入口是中央轻点
 * （`readerCenterTapToToggle` → `CenterTap`）重新显示工具栏，再点击已确认的返回节点。
 *
 * 本工具只做两件事：
 * 1. 按上述生产规则唤出并通过返回节点退出；
 * 2. 节点确实不可用时，走生产系统返回入口（`GLOBAL_ACTION_BACK`，由阅读器 BackHandler /
 *    NavHost 处理），而不是关闭 Activity、重启进程或直接导航。
 *
 * 不调用保存 helper、不直写进度、不吞异常；失败返回原始 Throwable 与诊断。
 *
 * 73-v1-B：中央轻点、等待与返回点击**全部**位于同一个 try 内，任何交互异常都进入
 * [Attempt.failure]，调用方才能保存阶段诊断；不新增成功兜底，也不把异常降级为“未退出”。
 * Compose 1.9.0 的 `performClick` 经字节码证明委托 `performTouchInput`（真实触摸），
 * 不把它当成“仅语义点击”而另写一套触摸路径。
 */
internal object ReaderChromeExitSupport {

    const val BACK_DESCRIPTION = "返回"

    data class Attempt(
        val backVisibleBefore: Boolean,
        val backVisibleAfterWait: Boolean,
        val centerTapPerformed: Boolean,
        val backVisibleAfterTap: Boolean,
        val entryUsed: String,
        val systemBackUsed: Boolean,
        val failure: Throwable?,
        val semanticsExcerpt: String,
    ) {
        fun diagnostics(): Map<String, Any?> = linkedMapOf(
            "exitEntry" to entryUsed,
            "backVisibleBefore" to backVisibleBefore,
            "backVisibleAfterWait" to backVisibleAfterWait,
            "centerTapPerformed" to centerTapPerformed,
            "backVisibleAfterTap" to backVisibleAfterTap,
            "systemBackUsed" to systemBackUsed,
            "backNodeCountAtEnd" to if (backVisibleAfterTap) 1 else 0,
            "interactionCompleted" to (failure == null),
            "failureMessage" to failure?.toString(),
            "semanticsTreeHead" to semanticsExcerpt,
            "note" to "翻页后 controlsVisible=false；中央轻点唤出工具栏后点击生产返回；" +
                "仍不可见才走生产系统返回，未关闭 Activity/未直写进度",
        )
    }

    private fun backVisible(rule: ComposeTestRule): Boolean =
        rule.onAllNodesWithContentDescription(BACK_DESCRIPTION).fetchSemanticsNodes().isNotEmpty()

    private fun awaitBackVisible(rule: ComposeTestRule, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (backVisible(rule)) return true
            rule.waitForIdle()
            Thread.sleep(100L)
        }
        return backVisible(rule)
    }

    private fun semanticsExcerpt(rule: ComposeTestRule): String =
        runCatching { rule.onRoot().printToString(maxDepth = 6).take(4000) }
            .getOrElse { "<unavailable: $it>" }

    /**
     * 退出阅读器。调用方仍须自行严格断言 `readerGone()`、持久位置、重开与续读。
     *
     * 返回值只描述“退出动作”本身：即使 [Attempt.failure] 为空（动作已发出并完成），
     * 也不代表页面已离开——离开由调用方断言证实。
     */
    fun exitViaBack(rule: ComposeTestRule): Attempt {
        var before = false
        var afterWait = false
        var centerTap = false
        var afterTap = false
        var used = "toolbar-back"
        var systemBack = false

        // 73-v1-B：等待、中央轻点、等待返回节点、点击返回/系统返回全部在同一 try 内。
        // 任一步抛错都被原样放进 Attempt.failure（不吞、不 null 兜底），调用方据此落阶段诊断。
        val failure: Throwable? = try {
            before = backVisible(rule)
            // 顶栏显隐有弹簧动画；先给已存在的节点一个稳定窗口，避免把动画中的“暂时不可见”误判为隐藏。
            afterWait = before || awaitBackVisible(rule, 1_200L)
            afterTap = afterWait
            if (!afterWait) {
                // 生产入口：中央轻点切换 controlsVisible。
                centerTap = true
                rule.onRoot().performClick()
                rule.waitForIdle()
                afterTap = awaitBackVisible(rule, 5_000L)
            }
            used = if (afterTap) "toolbar-back" else "system-back"
            if (afterTap) {
                rule.onNodeWithContentDescription(BACK_DESCRIPTION).performClick()
            } else {
                systemBack = true
                used = "system-back"
                val ok = InstrumentationRegistry.getInstrumentation().uiAutomation
                    .performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                check(ok) { "生产系统返回入口不可用（performGlobalAction 返回 false）" }
            }
            rule.waitForIdle()
            null
        } catch (t: Throwable) {
            t
        }

        return Attempt(
            backVisibleBefore = before,
            backVisibleAfterWait = afterWait,
            centerTapPerformed = centerTap,
            backVisibleAfterTap = afterTap,
            entryUsed = used,
            systemBackUsed = systemBack,
            failure = failure,
            semanticsExcerpt = if (failure != null) semanticsExcerpt(rule) else "",
        )
    }
}
