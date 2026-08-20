package com.creationreadingassistant.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal fun MacrobenchmarkScope.dismissOnboardingIfPresent() {
    device.wait(Until.findObject(By.text("跳过")), 1_000)?.click()
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.seedBenchmarkLibrary(bookCount: Int = 200) {
    device.executeShellCommand(
        "am start -W -n $PACKAGE_NAME/com.creationreadingassistant.benchmark.BenchmarkSeedActivity " +
            "--ei bookCount ${bookCount.coerceIn(200, 500)}"
    )
    // The activity performs Room writes off the main thread and exits when the transaction is
    // complete. Keep seed time outside every measured block.
    Thread.sleep(2_500)
}

internal fun MacrobenchmarkScope.openShelfForBenchmark() {
    pressHome()
    device.executeShellCommand("am force-stop $PACKAGE_NAME")
    startBenchmarkMainActivity()
    dismissOnboardingIfPresent()

    repeat(6) {
        if (device.wait(Until.hasObject(By.desc("打开书架整理")), 1_000)) return
        if (device.currentPackageName != PACKAGE_NAME) {
            startBenchmarkMainActivity()
            dismissOnboardingIfPresent()
        }
        val shelfNavigationItem = device.findObjects(By.text("书架"))
            .maxByOrNull { it.visibleBounds.top }
        if (shelfNavigationItem != null) {
            val bounds = shelfNavigationItem.visibleBounds
            device.click(bounds.centerX(), bounds.centerY())
            if (device.wait(Until.hasObject(By.desc("打开书架整理")), 2_000)) return
        } else {
            // 上一轮可能停在搜索、整理页或全屏阅读器，逐层返回后重新确认。
            device.pressBack()
        }
        device.waitForIdle()
    }

    checkNotNull(device.findObject(By.desc("打开书架整理"))) {
        "无法进入书架页面"
    }
}

/**
 * 按新交互链切换书架排序：书架页 → 打开书架整理（全屏页）→ 排序行 → 排序选择页 → 点击目标排序项
 * （触发 setSortMode + popBackStack）→ 返回书架页。排序交互已从弹层改版为全屏导航路由
 * （ShelfOrganizerRoute → ShelfSelectionRoute），不存在“弹层打开帧”这一旧场景。
 */
internal fun MacrobenchmarkScope.applyShelfSortMode(targetLabel: String) {
    val organizer = device.wait(Until.findObject(By.desc("打开书架整理")), 3_000)
    checkNotNull(organizer) { "找不到书架页排序/整理入口（标题区下拉图标）" }
    organizer.click()

    val sortRow = device.wait(Until.findObject(By.text("排序")), 3_000)
    checkNotNull(sortRow) { "找不到书架整理页的排序行" }
    sortRow.click()

    val sortOption = device.wait(Until.findObject(By.text(targetLabel)), 3_000)
    checkNotNull(sortOption) { "找不到排序选项：$targetLabel" }
    sortOption.click()
    device.waitForIdle()

    // 选择排序项后 popBackStack 回到书架整理页；再点顶栏“返回”按钮回到书架页。
    // 注意：不能用系统返回键——AppNavigation 的 BackHandler 在顶层路由（书架）上会
    // 拦截返回并导航到首页；书架整理页只能经顶栏返回按钮逐层退出。
    check(device.wait(Until.findObject(By.text("书架整理")), 3_000) != null) {
        "选择排序项后未返回书架整理页"
    }
    // 点顶栏“返回”逐层退回书架页。注意：不能用系统返回键（AppNavigation 的 BackHandler
    // 在顶层路由会拦截并导航到首页）；转场动画期间可能命中正在退场的同名按钮，故加重试。
    var reachedShelf = device.wait(Until.hasObject(By.desc("打开书架整理")), 1_000)
    var attempts = 0
    while (!reachedShelf && attempts < 3) {
        val back = device.wait(Until.findObject(By.desc("返回")), 2_000)
        checkNotNull(back) { "找不到顶栏返回按钮" }
        back.click()
        device.waitForIdle()
        reachedShelf = device.wait(Until.hasObject(By.desc("打开书架整理")), 2_000)
        attempts++
    }
    check(reachedShelf) { "排序切换后未能回到书架页" }
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.clickTopLevelNavigation(label: String) {
    val item = device.findObjects(By.text(label))
        .maxByOrNull { it.visibleBounds.top }
        ?: device.wait(Until.findObject(By.text(label)), 3_000)
    checkNotNull(item) { "找不到底部导航：$label" }
    val bounds = item.visibleBounds
    device.click(bounds.centerX(), bounds.centerY())
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.openTopLevelStart(label: String = "首页") {
    pressHome()
    startBenchmarkMainActivity()
    dismissOnboardingIfPresent()
    clickTopLevelNavigation(label)
}

private fun MacrobenchmarkScope.startBenchmarkMainActivity() {
    device.executeShellCommand(
        "am start -W -n $PACKAGE_NAME/com.creationreadingassistant.MainActivity"
    )
    check(device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), 5_000)) {
        "Benchmark 主界面未进入前台"
    }
}

internal fun MacrobenchmarkScope.filterShelfForBenchmark(query: String) {
    var field = device.findObject(By.clazz("android.widget.EditText"))
    if (field == null) {
        val search = device.wait(Until.findObject(By.desc("搜索")), 3_000)
        checkNotNull(search) { "找不到书架搜索入口" }
        search.click()
        field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 3_000)
    }
    checkNotNull(field) { "找不到书架搜索输入框" }
    field.text = query
    device.waitForIdle()
}
