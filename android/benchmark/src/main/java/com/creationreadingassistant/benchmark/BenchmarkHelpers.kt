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
        if (device.wait(Until.hasObject(By.desc("搜索书架")), 1_000)) return
        if (device.currentPackageName != PACKAGE_NAME) {
            startBenchmarkMainActivity()
            dismissOnboardingIfPresent()
        }
        val shelfNavigationItem = device.findObjects(By.text("书架"))
            .maxByOrNull { it.visibleBounds.top }
        if (shelfNavigationItem != null) {
            val bounds = shelfNavigationItem.visibleBounds
            device.click(bounds.centerX(), bounds.centerY())
            if (device.wait(Until.hasObject(By.desc("搜索书架")), 2_000)) return
        } else {
            // 上一轮可能停在搜索、弹层或全屏阅读器，逐层返回后重新确认。
            device.pressBack()
        }
        device.waitForIdle()
    }

    checkNotNull(device.findObject(By.desc("搜索书架"))) {
        "无法进入书架页面"
    }
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
        val search = device.wait(Until.findObject(By.desc("搜索书架")), 3_000)
        checkNotNull(search) { "找不到书架搜索入口" }
        search.click()
        field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 3_000)
    }
    checkNotNull(field) { "找不到书架搜索输入框" }
    field.text = query
    device.waitForIdle()
}
