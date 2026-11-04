# S04 隔离 Runner 实施报告（新文件部分）

- **日期**：2026-09-25
- **仓库 / 分支 / HEAD**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` / `codex/recover-worktree-20260920` / `5aed593`
- **窗口性质**：实施窗口（仅新文件）。设计依据：
  [`2026-09-25-s04-instrumentation-startup-isolation-design.md`](../../2026-09-25-s04-instrumentation-startup-isolation-design.md)（下称"设计报告"）。
- **未执行 / 未验证**：未跑 Gradle、未跑 adb、未装机、未 commit/push/stage/reset/checkout/clean。
  **本报告不做任何"已通过编译""已实现真实隔离"的宣称。**

## 1. 开工核对

| 检查 | 结果 |
|---|---|
| 目标新文件是否已存在 | 开工时 Glob 无命中 → 首次创建 ✓ |
| 是否改动 `build.gradle.kts` / Manifest / 产品源码 / 既有测试 / 既有报告 | **否**，全部未触碰 ✓ |
| 是否运行 Gradle / adb / 设备命令 | **否** ✓ |
| 是否触碰用户书籍数据 | **否** ✓ |

## 2. 交付物（仅 3 个新文件）

| 文件 | 内容 |
|---|---|
| `android/app/src/androidTest/java/com/creationreadingassistant/testing/CraIsolatedTestApplication.kt` | 最小测试 Application，直接继承 `android.app.Application`。**不继承生产 `App`**、无 Hilt、无任何启动线程 / StrictMode / 崩溃处理器 / 系统夜间模式写入。KDoc 记录了适用边界、刻意不做清单和 `androidx.startup` 残余副作用。 |
| `android/app/src/androidTest/java/com/creationreadingassistant/testing/CraIsolatedTestRunner.kt` | 继承 `androidx.test.runner.AndroidJUnitRunner`，仅覆写 `newApplication(ClassLoader, String, Context)`，委托 `super.newApplication(cl, CraIsolatedTestApplication::class.java.name, context)` 完成替换。无其他逻辑。 |
| `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-isolated-runner-implementation.md` | 本报告。 |

## 3. SEAM REQUEST（交集成者处理）

| # | 事项 |
|---|---|
| **SR-1** | `android/app/build.gradle.kts:29` 的 `testInstrumentationRunner` 由 `androidx.test.runner.AndroidJUnitRunner` 改为 `com.creationreadingassistant.testing.CraIsolatedTestRunner`。**在该行切换前，本切片的两个类不会被任何测试使用，是编译期死代码**；切换后所有 androidTest 的宿主 Application 才变为隔离版。切换属共享构建配置，本窗口无权修改（设计报告 BLOCKER B1）。 |
| **SR-2** | `docs/handoff/current.md` §8 的手工示例命令把 runner 硬编码为 `androidx.test.runner.AndroidJUnitRunner`；SR-1 落地后该命令会失败（设计报告 §4 已登记）。需要集成者同步。 |
| **SR-3** | 设计报告 §7.4 提醒：`connectedDebugAndroidTest` 结束时 AGP 可能卸载主应用；与 runner 切换无关，但跑设备验证前必须确认可重装。 |

## 4. 语法自洽性自查（静态目检，非编译验证）

- 两文件均无未解析符号：只引用 `android.app.Application` / `android.content.Context` / `androidx.test.runner.AndroidJUnitRunner`，其中 `androidx.test:runner` 已是既有 androidTest 依赖（当前 runner 就是它）。
- `newApplication(cl, className, context)` 的覆写签名与 `Instrumentation.newApplication(ClassLoader, String, Context)` 对齐；采用 `super.newApplication(cl, FQCN, context)` 委托而非手工反射，与 AndroidX 文档推荐写法一致。
- KDoc 中无未闭合注释、无与既有测试重名的类（`testing/` 子包为新建）。
- **以上仅为静态目检结论，不等价于编译通过。**

## 5. 未做的验证（诚实登记，设计报告 §6 的步骤全部未执行）

1. `:app:compileDebugAndroidTestKotlin` 未跑 —— 不排除签名、可见性或依赖上的意外编译错误。
2. 测试 APK 未构建、未解包核对 dex / manifest 的 `instrumentation android:name`。
3. 未做任何设备验证：隔离 Application 下 26 个 Compose 测试是否 100% 无回归、`androidx.startup` 四个 Initializer 是否仍执行、正式库与 `search_terms` 是否真的零写入，均**未知**。
4. `lintDebug` 未跑，未确认是否新增 warning。

## 6. 可能回归（设计报告 §4 / §10 推断，均未验证）

- 换 Application 后 `AppLog.debugEnabled` 恒 false、`AppContextHolder.cacheDir` 为 null —— 现无测试引用，属推测。
- 依赖 `ApplicationProvider.getApplicationContext()` 的 10 个 Room 测试文件预期不受影响（裸 `Application` 的 `filesDir` 等仍可用），但为框架行为推测，需设备逐类验证。
- WorkManager（`androidx.work.workdb`）仍会由 ContentProvider 初始化，隔离不覆盖该层。

## 7. 结论

新文件部分已完成：隔离 Application 与 Runner 就位，边界写进 KDoc，未越界改动任何既有文件。**在集成者完成 SR-1 并跑完设计报告 §6 的验证链之前，本切片不构成"androidTest 已隔离"的证据。**
