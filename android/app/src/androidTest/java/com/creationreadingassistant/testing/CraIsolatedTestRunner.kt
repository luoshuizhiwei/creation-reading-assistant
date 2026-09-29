package com.creationreadingassistant.testing

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.test.runner.AndroidJUnitRunner

/**
 * androidTest 专用 InstrumentationRunner（S04 启动隔离，设计依据：
 * `docs/coordination/2026-09-25-s04-instrumentation-startup-isolation-design.md` §5.2；
 * 范围收窄依据：`docs/coordination/2026-09-29-stage-e-g2-runner-scope-review.md`）。
 *
 * ## 按目标包精确分支（E 阶段 runner 窄修，2026-09-29）
 *
 * [newApplication] 的 `context` 是 instrumentation 目标应用（被测 APK）的
 * Application Context——由框架 `LoadedApk.makeApplication` 以目标包的
 * ContextImpl 调入，`context.packageName` 即目标包名。据此：
 *
 * - 目标包 == `com.creationreadingassistant.qahost`（S04 沙箱宿主）：注入
 *   [CraIsolatedTestApplication]（裸 Application），不执行生产 `App` 的
 *   `attachBaseContext`/`onCreate`——三条 `cra-startup` 后台接线（EPUB 尺寸修复 /
 *   阅读目标调度 / 搜索索引调度）、Hilt 图谱、应用级夜间模式在测试进程中不运行，
 *   避免触碰沙箱数据与系统级设置；
 * - **其他任何目标包（含普通 debug 的正式包）一律透传** `className`（清单声明的
 *   生产 Application），恢复 pre-G2 的 debug 语义：Hilt（`MainActivity` 为
 *   `@AndroidEntryPoint`）、主题、启动接线在普通 debug 测试中照常执行。
 *
 * 每次决策写一行 `android.util.Log.i`（tag 见 [TAG]），记录 target 包、清单声明的
 * Application 类与实际选择的分支，供设备轮 logcat 直接核对；该日志不依赖
 * `AppLog`（隔离 Application 下 `AppLog.debugEnabled` 未经 `App.onCreate` 初始化）。
 *
 * ## 适用边界
 *
 * - 仅当 `build.gradle.kts` 的 `testInstrumentationRunner` 指向本类时生效；
 *   AGP 8.6 Kotlin DSL 的 `BuildType` 不暴露该属性（见 runner scope 审查 §4），
 *   故 defaultConfig 级设置 + 运行时分支是唯一可行的按变体区分手段。
 * - 若未来新增沙箱类 build type，必须同步 [QA_HOST_PACKAGE] 判断。
 * - 本类不做任何其他事：不改参数解析、不包 `Instrumentation` 代理、
 *   不加监听器——隔离手段只有"按目标包换 Application"这一个。
 * - 依赖 `AndroidJUnitRunner.newApplication(ClassLoader, String, Context)` 的
 *   框架行为（AndroidX test runner 允许覆写返回自定义 Application 实例）。
 *   **运行时已验证的分支**：qahost（见联合候选 MuMu 报告的 logcat 记录）；
 *   **普通 debug 透传分支：设备 NOT RUN**（正式包不安装到测试设备的约束），
 *   仅有编译、生成 manifest 与本源码证据。
 */
class CraIsolatedTestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application {
        val targetPackage = context?.packageName.orEmpty()
        val isolate = targetPackage == QA_HOST_PACKAGE
        Log.i(
            TAG,
            "newApplication target=$targetPackage " +
                "manifestApplication=$className -> " +
                if (isolate) "CraIsolatedTestApplication (isolated)" else "manifest Application (pass-through)",
        )
        return if (isolate) {
            super.newApplication(cl, CraIsolatedTestApplication::class.java.name, context)
        } else {
            super.newApplication(cl, className, context)
        }
    }

    private companion object {
        const val TAG = "CraIsolatedTestRunner"

        /** S04 沙箱宿主 applicationId（`build.gradle.kts` qahost `applicationIdSuffix = ".qahost"`）。 */
        const val QA_HOST_PACKAGE = "com.creationreadingassistant.qahost"
    }
}
