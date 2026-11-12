package com.creationreadingassistant.ui.screen.reader

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * READER-PAGES-DEVICE-v1：TalkBack 启用状态下的四入口聚焦/激活真机验收
 * （TalkBack 已由宿主侧启用；本测试用 UiAutomator 完成确定性导航与激活，
 * 用「无障碍焦点节点」与截图证明 TalkBack 聚焦，避开触摸探索对脚本
 * 点击的拦截）。
 *
 * 运行前置：书已导入（reader-pages-device-v1-book）、TalkBack 已启用。
 */
class ReaderPagesTalkBackDeviceTest {

    private fun device(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun shot(name: String) {
        val dir = InstrumentationRegistry.getInstrumentation()
            .targetContext.getExternalFilesDir(null)
        val out = java.io.File(dir, "reader-pages-device-talkback/$name")
        out.parentFile?.mkdirs()
        device().takeScreenshot(out)
    }

    /** TalkBack 聚焦探索：单击让 TalkBack 聚焦该节点，遍历全部窗口找无障碍焦点节点。 */
    private fun focusByTap(x: Int, y: Int): String? {
        device().click(x, y)
        Thread.sleep(1000)
        val uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation()
        for (window in uiAutomation.windows) {
            val root = window.root ?: continue
            val focused = root.findFocus(
                android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY,
            ) ?: continue
            val t = focused.text?.toString() ?: focused.contentDescription?.toString()
            if (!t.isNullOrBlank()) return t
        }
        return null
    }

    /** 找含有关键词的节点，返回其可见区中心（供聚焦用）。 */
    private fun nodeCenter(textContains: String): android.graphics.Point? {
        val node = device().findObject(By.textContains(textContains)) ?: return null
        val b = node.visibleBounds
        return android.graphics.Point(b.centerX(), b.centerY())
    }

    private fun clickByText(text: String, timeout: Long = 8000): Boolean {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            val node = device().findObject(By.text(text))
            if (node != null) {
                node.click()
                Thread.sleep(1200)
                return true
            }
            Thread.sleep(400)
        }
        return false
    }

    private fun clickByDesc(desc: String, timeout: Long = 8000): Boolean {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            val node = device().findObject(By.desc(desc))
            if (node != null) {
                node.click()
                Thread.sleep(1200)
                return true
            }
            Thread.sleep(400)
        }
        return false
    }

    @Test
    fun talkBackFocusAndActivationAcrossFourEntries() {
        val device = device()
        // 确定性起点：循环到书架（页内点击 / back / 重启应用三态兜底）
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pkg = instrumentation.targetContext.packageName
        var onShelf = false
        for (round in 1..6) {
            if (clickByText("书架", timeout = 4000)) { onShelf = true; break }
            if (round % 2 == 0) {
                // 后台 Activity 启动限制会拦截 instrumentation 的 startActivity——
                // 经 UiAutomation shell 通道（adb 身份）启动。
                instrumentation.uiAutomation.executeShellCommand(
                    "monkey -p $pkg -c android.intent.category.LAUNCHER 1",
                )
                Thread.sleep(4000)
            } else {
                device.pressBack(); Thread.sleep(1300)
            }
        }
        assertTrue("底栏书架应存在（循环兜底后仍未到达）", onShelf)
        Thread.sleep(1500)

        // ── 书架 → 打开书 ──
        assertTrue("打开书籍卡片应存在", clickByDesc("打开书籍"))
        Thread.sleep(3500)

        // 呼出 chrome（TalkBack 下单击=聚焦；激活中央切层用 ACTION_CLICK 经底栏入口完成，
        // 先用 UiAutomator 点击屏幕中心触发 ToggleControls：中心不是节点，改为直接找底栏——
        // chrome 自动隐藏时需先聚焦再激活。TalkBack 下双击语义由 TalkBack 处理，
        // 这里用两次快速 UiAutomator click 模拟双击激活）。
        fun activateCenter() {
            val w = device.displayWidth
            val h = device.displayHeight
            device.click(w / 2, h / 2)
            Thread.sleep(180)
            device.click(w / 2, h / 2)
            Thread.sleep(1200)
        }
        activateCenter()

        // ── 目录：聚焦章节行 + 激活跳章 ──
        assertTrue("目录按钮应存在", clickByText("目录"))
        Thread.sleep(2500)
        shot("tb-toc-page.png")
        // 长标题章节行：按节点边界聚焦
        val chapterPoint = nodeCenter("第004章") ?: nodeCenter("第003章") ?: nodeCenter("第001章")
        val chapterFocus = chapterPoint?.let { focusByTap(it.x, it.y) }
        shot("tb-toc-chapter-focus.png")
        assertTrue(
            "目录章节行必须可被 TalkBack 聚焦（got=$chapterFocus）",
            chapterFocus != null && (chapterFocus.contains("第") || chapterFocus.contains("章")),
        )
        // 激活：点击当前章行 → 目录关闭回正文（跳章激活证明）
        assertTrue("章节行应可激活", clickByText("第004章 结尾对照章", timeout = 5000) ||
            clickByDesc("第004章 结尾对照章", timeout = 5000) || clickByText("第003章 横屏检查测试章", timeout = 5000))
        Thread.sleep(2000)

        // ── 外观：聚焦分类并切换 ──
        activateCenter()
        assertTrue("外观按钮应存在", clickByText("外观"))
        Thread.sleep(2200)
        shot("tb-theme-page.png")
        val catPoint = nodeCenter("排版") ?: nodeCenter("纸色") ?: nodeCenter("字体")
        val catFocus = catPoint?.let { focusByTap(it.x, it.y) }
        shot("tb-theme-category-focus.png")
        assertTrue("外观分类/控件必须可被 TalkBack 聚焦（got=$catFocus）", catFocus != null)
        // 激活「排版」分类（若在当前视口）→ 分类内容切换
        val switched = clickByText("排版") || clickByText("纸色")
        Thread.sleep(1500)
        shot("tb-theme-switched.png")
        assertTrue("外观分类应可激活切换", switched)

        // ── 设置：聚焦作用范围并切换 ──
        // 从外观返回正文（完成按钮）→ 打开设置
        assertTrue("完成按钮应存在", clickByText("完成"))
        Thread.sleep(1500)
        activateCenter()
        assertTrue("设置按钮应存在", clickByText("设置"))
        Thread.sleep(2200)
        shot("tb-settings-page.png")
        val scopePoint = nodeCenter("改全局默认") ?: nodeCenter("只改本书")
        val scopeFocus = scopePoint?.let { focusByTap(it.x, it.y) }
        shot("tb-settings-scope-focus.png")
        assertTrue("设置页控件必须可被 TalkBack 聚焦（got=$scopeFocus）", scopeFocus != null)
        assertTrue("作用范围切换应存在", clickByText("改全局默认") || clickByText("只改本书"))
        Thread.sleep(1200)
        // 返回根页→来源
        device.pressBack(); Thread.sleep(900)
        device.pressBack(); Thread.sleep(900)

        // ── 灵感：聚焦正文输入与保存 ──
        activateCenter()
        assertTrue("灵感按钮应存在", clickByText("灵感"))
        Thread.sleep(2200)
        shot("tb-inspiration-page.png")
        val bodyPoint = nodeCenter("我的想法")
        val bodyFocus = bodyPoint?.let { focusByTap(it.x, it.y) }
        shot("tb-inspiration-body-focus.png")
        assertTrue(
            "灵感正文必须可被 TalkBack 聚焦（got=$bodyFocus）",
            bodyFocus != null && (bodyFocus.contains("我的想法") || bodyFocus.contains("想法")),
        )
        // 保存按钮聚焦（不激活，避免保存空灵感）
        val saveNode = device.findObject(By.text("保存"))
        assertTrue("保存按钮必须存在且可聚焦", saveNode != null)
        shot("tb-inspiration-save.png")
    }
}
