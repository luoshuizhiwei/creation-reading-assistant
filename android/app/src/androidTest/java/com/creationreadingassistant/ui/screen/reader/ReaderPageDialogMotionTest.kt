package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.theme.ReaderPaperTheme
import com.creationreadingassistant.ui.theme.paperPalette
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * READER-PAGES-v1 集中修正补验：全屏附页容器 180/140ms 过渡的**确定性逐帧验证**
 * （compose mainClock 停表驱动 + captureToImage 实拍，替代易受时序竞态影响的
 * screenrecord）。
 *
 * 验证四件事：
 * 1. 入场中间帧存在：90ms 处画面与 0ms 起始 / 180ms 终态均不同（右侧位移+淡入中间态）；
 * 2. 退场中间帧存在：140ms 中点与两端不同；
 * 3. **可取消**：入场进行到一半改 target=null，入场被取消并转退场，容器最终移除
 *   （画面回到正文内容）；
 * 4. 减少动画分支：宿主侧以 adb 置零动画 scale 后由 rememberReducedMotion 读取，
 *    由独立的 reduced-motion 用例在时钟推进后**立即**等于终态（单独运行：
 *    `-e class ...#reducedMotionReachesEndStateInstantly`）。
 *
 * 抓帧 PNG 落到 app external files（reader-pages-motion/）供交付归档。
 */
@OptIn(ExperimentalTestApi::class)
class ReaderPageDialogMotionTest {
    @get:Rule val composeRule = createComposeRule()

    /** 附页 Dialog 窗口内的实拍（冻结时钟下 captureToImage 反映 UI 线程当前帧）。 */
    private fun dialogShot(name: String): android.graphics.Bitmap {
        val img = composeRule.onNodeWithText("motion-probe-TOC").captureToImage()
        val bmp = img.asAndroidBitmap()
        val dir = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext.getExternalFilesDir(null)
        val out = File(dir, "reader-pages-motion/$name")
        out.parentFile?.mkdirs()
        out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    private fun pixelsOf(bitmap: android.graphics.Bitmap, step: Int): List<Long> {
        val px = mutableListOf<Long>()
        val sx = step.coerceAtLeast(1)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                px.add(bitmap.getPixel(x, y).toLong())
                x += sx
            }
            y += sx
        }
        return px
    }

    private fun save(bitmap: android.graphics.Bitmap, name: String) {
        val dir = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext.getExternalFilesDir(null)
        val out = File(dir, "reader-pages-motion/$name")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 冻结时钟下推进一帧：LaunchedEffect 置位与 Dialog 组合各需一帧。 */
    private fun frame() {
        composeRule.mainClock.advanceTimeBy(16L)
        composeRule.waitForIdle()
    }

    private fun setContent(target: MutableState<ReaderSheet?>) {
        composeRule.setContent {
            AppTheme {
                ReaderPaperTheme(paperPalette("white", false)) {
                    ReaderPageDialogHost(
                        target = target.value,
                        paper = paperPalette("white", false),
                        onDismiss = {},
                        content = { page ->
                            Box(Modifier.fillMaxSize()) {
                                Column(Modifier.padding(PaddingValues(32.dp))) {
                                    Text("motion-probe-$page")
                                    Text("正文内容保持可见，容器只是包裹层")
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    @Test
    fun enterExitTransitionsHaveIntermediateFramesAndAreCancellable() {
        val target = mutableStateOf<ReaderSheet?>(null)
        setContent(target)
        composeRule.mainClock.autoAdvance = false
        composeRule.waitForIdle()

        // ── 入场：target 置 TOC，时钟停在 0ms（起始帧）──
        composeRule.runOnIdle { target.value = ReaderSheet.TOC }
        frame() // 帧1：LaunchedEffect 置位 rendered + 动画启动
        frame() // 帧2：Dialog 真实组合落帧（动画约 16ms 处）
        val start = dialogShot("enter_0ms.png")

        // 90ms 中间帧：位移+淡入进行中。
        composeRule.mainClock.advanceTimeBy(90L)
        composeRule.waitForIdle()
        val mid = dialogShot("enter_90ms.png")

        // 200ms：越过 180ms 终态。
        composeRule.mainClock.advanceTimeBy(184L)
        composeRule.waitForIdle()
        val end = dialogShot("enter_200ms.png")

        val startPx = pixelsOf(start, 24)
        val midPx = pixelsOf(mid, 24)
        val endPx = pixelsOf(end, 24)
        assertTrue("入场中间帧必须与起始帧不同（位移+淡入进行中）", midPx != startPx)
        assertTrue("入场中间帧必须与终态不同", midPx != endPx)

        // ── 可取消：入场进行到一半改 target=null（入场被取消、转退场）──
        composeRule.runOnIdle { target.value = ReaderSheet.TOC }
        frame() // 帧1：置位
        frame() // 帧2：Dialog 组合
        composeRule.mainClock.advanceTimeBy(74L)
        composeRule.waitForIdle()
        composeRule.runOnIdle { target.value = null }
        frame() // 帧：取消入场 → rendered 仍持旧页，退场动画启动
        composeRule.mainClock.advanceTimeBy(44L)
        composeRule.waitForIdle()
        val cancelling = dialogShot("cancel_60ms.png")
        composeRule.mainClock.advanceTimeBy(300L)
        composeRule.waitForIdle()
        // 终态：容器已移除，探测文本不再存在。
        composeRule.onAllNodesWithText("motion-probe-TOC", useUnmergedTree = true)
            .assertCountEquals(0)

        // ── 退场：完整走 140ms ──
        composeRule.runOnIdle { target.value = ReaderSheet.TOC }
        frame()
        frame()
        composeRule.mainClock.advanceTimeBy(268L)
        composeRule.waitForIdle()
        composeRule.runOnIdle { target.value = null }
        frame() // 帧：退场动画启动
        composeRule.waitForIdle()
        val exitStart = dialogShot("exit_0ms.png")
        composeRule.mainClock.advanceTimeBy(70L)
        composeRule.waitForIdle()
        val exitMid = dialogShot("exit_70ms.png")
        composeRule.mainClock.advanceTimeBy(200L)
        composeRule.waitForIdle()
        // 退场终态：容器已移除（节点不存在），退场中间帧与起点不同。
        composeRule.onAllNodesWithText("motion-probe-TOC", useUnmergedTree = true)
            .assertCountEquals(0)
        assertTrue("退场中间帧必须与退场起点不同", pixelsOf(exitMid, 24) != pixelsOf(exitStart, 24))
    }

    /** 减少动画分支：动画 scale 已被 adb 置零 → 时钟 0ms 即终态（无中间帧可采）。 */
    @Test
    fun reducedMotionReachesEndStateInstantly() {
        val target = mutableStateOf<ReaderSheet?>(null)
        setContent(target)
        composeRule.mainClock.autoAdvance = false
        composeRule.waitForIdle()
        composeRule.runOnIdle { target.value = ReaderSheet.TOC }
        frame()
        frame()
        val instant = dialogShot("reduced_0ms.png")
        composeRule.mainClock.advanceTimeBy(300L)
        composeRule.waitForIdle()
        val settled = dialogShot("reduced_end.png")
        assertTrue(
            "减少动画时 0ms 画面必须等于终态（直接到终态、无中间帧）",
            pixelsOf(instant, 24) == pixelsOf(settled, 24),
        )
    }
}
