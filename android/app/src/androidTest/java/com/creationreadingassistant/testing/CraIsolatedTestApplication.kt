package com.creationreadingassistant.testing

import android.app.Application

/**
 * androidTest 专用的最小 Application。
 *
 * ## 适用边界（S04 启动隔离，设计依据：
 * `docs/coordination/2026-09-25-s04-instrumentation-startup-isolation-design.md` §5.2）
 *
 * 由 [CraIsolatedTestRunner] 通过 AndroidJUnitRunner.newApplication 注入，
 * 仅供 instrumentation 进程使用；主 APK 的正式 Application 仍是
 * `com.creationreadingassistant.App`，不受本文件影响。
 *
 * ### 刻意不做的（与本类等价于裸 `android.app.Application` 的一部分）
 *
 * - **不得继承生产 `App`**：一旦继承，`App.onCreate` / `attachBaseContext` 会照常执行，
 *   EPUB 修复、阅读目标调度、搜索索引调度三条 `cra-startup` 后台接线就会重新触碰
 *   真机正式库（`books` / `search_terms` / `search_index_coverage` 等）；
 * - **不安装 `@HiltAndroidApp` / Hilt 图**：当前 0 个 androidTest 使用 Hilt
 *   （设计报告 §2.1 / §4 已核实）；若将来某个测试需要 Hilt，应另开窗口评估
 *   `@CustomTestApplication(android.app.Application::class)`，
 *   **禁止** `@CustomTestApplication(App::class)`（会连带继承全部启动任务）；
 * - **不启动任何业务线程**、不注册 StrictMode / 崩溃处理器、不改系统夜间模式
 *   （`App.onCreate` 的 `applyApplicationNightMode` 是跨进程持久的系统级设置）、
 *   不预热 `app_prefs`、不写 `cra_fatal.log`；
 * - **不得在这里加「替代的启动逻辑」**：目标是"什么都不做"，不是"做一份测试版"。
 *
 * ### 已知残余（换 Application 不会消除，登记以防误判）
 *
 * `androidx.startup` 的 `WorkManagerInitializer` 等 Initializer 由主 APK 合并清单里的
 * `InitializationProvider`（ContentProvider）驱动，先于 Application.onCreate 执行，
 * 仍会创建应用私有目录内的 `androidx.work.workdb`（设计报告 §3.1 / §10.2）。
 * 该库不是 Room 正式库，且换 Application 无法影响 ContentProvider 时序。
 *
 * ## 为什么它足够
 *
 * 34 个既有 androidTest 文件中：0 处使用 `EntryPointAccessors` / `as App` /
 * `HiltTestApplication`，0 处 `createAndroidComposeRule<MainActivity>()`（设计报告 §4 / §3.6）；
 * Compose 测试的 Activity 由 `androidx.compose.ui.test.manifest` 在主 APK 声明，
 * 主题来自 manifest 的 `android:theme`，均不依赖宿主 Application 的代码。
 * 因此"什么都不做"的 Application 是当前测试集的安全最小集。
 */
class CraIsolatedTestApplication : Application()
