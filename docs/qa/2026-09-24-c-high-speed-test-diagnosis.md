# 窗口 C — 高速翻页 androidTest 离线诊断（2026-09-24）

范围：只诊断我自己那 5 个用例（`PageTurnerHighSpeedTest.kt` 中 C 批次新增部分）是否存在**自行挂起**的可能，并同时评估「测试自身等待条件」这一竞争解释。本轮**未运行真机、adb、模拟器、横屏**；只允许的所有权文件 = 该测试文件。

## 0. 结论摘要

1. **没有任何一个挂起来自"等到不出现的条件"未被消解**：改写后我的代码里**不再有 `waitForIdle`/`runOnIdle` 之类的无限等待**（3 处全部改成 `runOnUiThread` + 有界 `advanceTimeBy`），剩下的唯一非有界等待是 `setContent`，它依赖宿主 Activity 起来 —— 这属环境侧，不属测试体内。
2. **推翻了"MIUI 是本轮已证根因"**：本轮证据链里，能钉死的只有 09-23 的三件事 —— 用例确已发起、未产出任何结果、当时前台是 `com.miui.home`。`Abort background activity starts` 的关键字属于 09-22 轮（跨轮类比）。而且 09-23 那次用的是**不带 `-r` 的 `am instrument`**，其 plain 输出只在**测试结束时**打点 —— 所以"只有类名一行"**在原理上无法区分**"卡在 Activity 启动"与"卡在测试体内"。⇒ 两种解释都还活着，本轮不裁决。
3. **发现并修掉 3 个可证明的测试缺陷**（都不是挂起，而是会让断言失效或误红）：时序前提写成帧数而非毫秒、上限断言无法区分"实现丢请求"与"注入被合并"、以及 `runOnIdle` 自身会泵帧导致注入时刻不可控。
4. **"编译通过"与"5 项在设备上运行通过"是两件事**：前者本轮有直接证据（`:app:compileDebugAndroidTestKotlin` exit 0 + 反编译类里 8 个方法齐全）；后者**至今为零**，5 项一个都没有跑完过。

## 1. 输入

- `AGENTS.md`（87 行，仓库根）
- `docs/qa/2026-09/2026-09-23-bcd-evidence-correction.md`（382 行）—— 尤其 §5.1–5.5：原始输出仅 `PageTurnerHighSpeedTest:`（`task-1568.log`，76 字节）、20 秒计时器与 `Early Termination`、`23:12:37 am force-stop`（距发起约 130 秒）、`logcat -d -t 100 | grep` 结果为空、"30 秒硬超时"不成立
- `android/app/src/androidTest/.../PageTurnerHighSpeedTest.kt`（改前 340 行 / 改后 393 行）
- `android/app/src/main/.../feature/reader/pager/PageTurner.kt`（只读；mtime 仍为 2026-09-23 22:19:22，本轮未改）
- 库工件：`ui-test-android-1.9.0`、`ui-test-junit4-android-1.9.0`（Gradle 缓存内 AAR → classes.jar，解到系统临时目录，未落在仓库内）

## 2. 逐个挂点审计（"改前"= 我上一版；"改后"= 本轮修完）

| # | 位置（改前行语义 / 改后行号） | 它在等什么 | 能否自行挂死 | 支持 / 反对证据 |
|---|---|---|---|---|
| H1 | rule 层：`createComposeRule()` 启动 `ComponentActivity` | Activity 进入可组合状态 | **能**（不受测试代码控制） | 09-23 前台为 `com.miui.home`；09-22 有 `Abort background activity starts` + `result code=102` 的原始 logcat（**跨轮**）。⇒ 只能算"与之一致"，不是本轮实证 |
| H2 | `setPageTurner()` → `setContent`（改后 :196-215） | 首帧组合 + compose root 注册 | **能**，但原因同 H1（Activity 没起来就到不了这一步） | 反编译：`awaitIdle` = `runOnUiThread-ish` + `awaitComposeRoots` + `withContext(synchronization)`，链上**没有 `withTimeout`**；`androidx.compose.ui.test.GlobalTimeout` 类不存在。存在一个 `ComposeNotIdleException`，但我在 awaitIdle 相关的 5 个类（`IdlingResourceRegistry`、`ComposeIdlingResource`、`MainTestClockImpl`、`AndroidComposeUiTestEnvironment` 及其 Impl）里都查不到对它的引用，`ComposeIdlingResource` 也没有任何 timeout 常量或 `nanoTime/currentTimeMillis` 调用（只有一句提到 timeout 的**诊断文案**）⇒ **不能依赖框架把挂起自动变成失败**。（措辞限定在"我查过的范围"，不等于"全库无超时"） |
| H3 | 改前 `tap()` 的 `runOnIdle { turnRequest = … }`（每用例 3–6 次） | 「Compose 空闲」 | **理论上能，实践上不是死锁而是失控** | 反编译 `ComposeIdlingResource.isIdleNow`：忙时会**自己调用** `MainTestClockImpl.advanceTimeByFrame()` ⇒ 它会泵帧而不是干等；其 busy 条件枚举为 pending recompositions / pending setContent / pending measure-layout / busy-attaching / created-roots，**不含"动画在播"** ⇒ 我原先担心的"`autoAdvance=false` + 动画在播 ⇒ runOnIdle 永不返回"**被推翻**（支持：字节码两条；反对：无） |
| H4 | 改前 `drainThenSnapshot()` 的 `repeat(120) { advanceTimeByFrame() }` + `runOnIdle` | 同上 | 帧循环有界；`runOnIdle` 同 H3 | 反编译 `TestMonotonicFrameClock`：有 `awaiters` 列表与 `getHasAwaiters()`，但**没有**"无 awaiters 就抛"的 check（唯一的 `IllegalStateException` 文案是内部 `frame dispatch not scheduled`）⇒ 空转推进帧不会异常也不会挂 |
| H5 | 生产侧 `prerendering` 循环 | `withFrameNanos` | 不能 | `while (attempts < 5)`（`PageTurner.kt:204`）⇒ 至多 5 帧 |
| H6 | 我上一版新增的 `turnEpoch` 排空链 | 重组 | 不能（但有界性依赖审计） | 写 `turnEpoch` 只发生在 `finally`；effect 重跑后若队列为空即 `removeFirstOrNull() == null` 返回，不再写 ⇒ 收敛。抢占分支（`turning` 仍 true）会"放回队首 + `turnEpoch++`"，而 effect 的 `if (turning …) return` 在**出队之前** ⇒ 每帧至多一次，不形成同帧自激。`TurnGate.dragging/pumping`、`pendingTurns` 都是普通容器（非 snapshot state）⇒ 不产生额外重组 |
| H7 | 断言执行位置（非挂起点，假绿风险） | — | — | 改前断言已在测试线程，快照经 `runOnIdle` 取；改后改由 `runOnUiThread` 取，且 `checkNotNull(snapshot)` 显式判定"块没执行=测试失效" |

改后我代码里的**全部**剩余等待：`setContent`（H1/H2，环境侧）+ 有界 `advanceTimeBy`。**0 处**无限 idle 等待。

## 3. 可证明缺陷与最小修复（都在测试文件内）

**D1 时序前提不成立（会让 `assertEquals(4, page)` 误红）**
改前把"6 次点按落在同一轮动画内"写成帧数：`repeat(6) { tap(h, 1, duringFrames = 1) }`，隐含假设每笔恰好 1 帧。两个事实打破它：(a) `runOnIdle` 会自己泵帧（H3 字节码），每笔消耗帧数不由测试决定；(b) 请求通道是**单槽**，一笔要「写入 → 某帧应用 → effect 消费复位」才能承载下一笔，即 ≥1 帧（16ms，见 `TestMonotonicFrameClock` 构造默认 `ldc2_w 16000000`）；6 笔 ≈ 96–192ms，而动画窗口只有 150ms ⇒ 后几笔可能落在**提交之后**，那时"应当被上限截断"的请求本来就该被排进第二轮，页号变成 5 或 6，断言假红。
修复：注入与推进一律用毫秒；排队类用例把动画放大到 600ms（`:381 QUEUE_TEST_SPEED_MS`、`:378 TAP_INTERVAL_MS = 24`）⇒ 6×24=144ms ≪ 600ms，前提变成可保证的。

**D2 上限断言分不清"实现丢请求"和"注入被合并"**
若通道只投进 4 笔而实现正好翻 4 页，旧断言 `assertEquals(4, page)` 会通过 —— 它没有钉住"6 笔都进去了"。
修复：harness 增加 `consumedRequests`（`:181`，在 `onTurnRequestConsumed` 里 `:206` 计数），burst 用例前置断言 `consumedRequests == 6`，其余用例同样断言 3/3/1。这样"页号少于点按数"只能归因于上限或实现，而不是注入丢失。

**D3 无限等待面没有收敛到零**
改前 3 处 `runOnIdle`（`tap()`、`drainThenSnapshot()` 两处）。虽然 H3 表明它不太可能死锁，但在一个"挂起 130 秒无人判定"的环境里，保留任何无超时约定的等待都是负债；且 `awaitIdle` 无 timeout（H2 证据）意味着真挂起会表现成完全一样的现象。
修复：改用 `ComposeTestRule.runOnUiThread`（已用 javap 确认它就在那个 junit4 接口上，签名 `<T> T runOnUiThread(Function0<? extends T>)`）—— 只投递到 UI 线程、不等 idle；时间推进全交给有界 `advanceTimeBy`。顺带把"停手后不得继续自己翻页"的第二观察窗从 150ms 提到 `DRAIN_MILLIS`(6000ms)，150ms 内本来就观察不到 600ms 动画的任何进展，那条断言原本是空转。

**未改的东西（明确记录）**：原 3 个像素用例（`slide…`/`cover…`/`advancing…`）一字未动，包括它们在 :79/:138 的 `runOnIdle`（那两处发生在动画开始前，是空闲态，且不属于我这批）；`PageTurner.kt` 产品代码一字未动（mtime 仍 09-23 22:19:22）；未新增辅助文件（`queueHarness` 那版草案最终没用上，避免留下未使用抽象）。

## 4. 实际运行命令与退出码

| 命令 | 退出码 | 结果 |
|---|---|---|
| `adb …`／`am instrument`／真机 | — | **本轮一条设备命令都没执行** |
| `mktemp -d; unzip ui-test-release.aar classes.jar; unzip -l \| grep -iE "Idling\|Idle"` | 0 | 列出 `ComposeIdlingResource`、`IdlingResourceRegistry`、`ComposeNotIdleException` 等 |
| `javap -p -c -classpath classes.jar androidx.compose.ui.test.ComposeIdlingResource` | 0 | 命中 `MainTestClockImpl.advanceTimeByFrame()` 与 busy 条件文案（pending recompositions / setContent / measure-layout） |
| `javap -p -c … 'AndroidComposeUiTestEnvironment$AndroidComposeUiTestImpl'`（`awaitIdle` 段） | 0 | 无 `withTimeout`；`androidx.compose.ui.test.GlobalTimeout` **类不存在**（`未找到类`） |
| `javap -p -c … TestMonotonicFrameClock`（构造默认值） | 0 | `ldc2_w long 16000000l` ⇒ 默认帧间隔 16ms |
| `javap -p … androidx.compose.ui.test.junit4.ComposeTestRule` | 0 | 确认 `runOnUiThread` 与 `waitUntil(timeoutMillis)` 均在接口上 |
| `javap -p -classpath app/build/tmp/kotlin-classes/debugAndroidTest …HighSpeedTest`（09-23 那批产物） | 0 | 8 个方法齐全（22:38:48 编译，晚于我 22:27:17 的源码）⇒ 上一轮共享构建确实编过了我这 5 个用例 |
| `cmd //c "gradlew.bat :app:compileDebugAndroidTestKotlin --no-parallel --console=plain"` | **0** | BUILD SUCCESSFUL 48s（01:00:48→01:01:38），`30 actionable tasks: 2 executed`；改后类的 `.class` mtime 2026-09-24 01:01:28，`javap` 复核 8 个方法仍在 |

日志留在 gitignore 范围内：`android/app/build/tmp/c-s06-test-diagnosis/compile-debug-androidtest.log`（+ `.time`）；解包分析在系统临时目录，未写入仓库、未写入原始仓库。

## 5. 「编译通过」与「设备上运行通过」的严格分界

- **编译**：`androidTest` 源集在我的改动后编译通过（exit 0，产物类含 8 个方法）。这只证明类型正确、可打包。
- **运行**：5 项（`singleTapAdvancesExactlyOnePage`、`threeRapidTapsDuringAnimationAdvanceThreePages`、`oppositeTapReplacesUnstartedBacklog`、`burstBeyondCapacityIsBounded`、`tapsAtLastPageStillConsumeRequestsWithoutStalling`）**没有任何一项在设备上产出过结果**。09-23 那次只发起了 `singleTapAdvancesExactlyOnePage`，输出 76 字节（仅类名），130 秒后 `am force-stop` ⇒ 0 completed / 0 failed / 0 结果。
- 因此本文件对 S06-⑤ 的"有牙门禁"目前**尚未生效**，只是具备了生效的条件之一（能编译）。

## 6. 交给下一轮设备验收的二分判据（本轮不执行）

要区分"环境拦截 Activity"和"测试自己等不到"，最小成本是这三步，且都在既有权限边界内（不改 MIUI 设置、不要求人工点击）：

1. **必须带 `-r`**：`am instrument -w -r -e class …HighSpeedTest#singleTapAdvancesExactlyOnePage …`。`-r` 模式会打 `INSTRUMENTATION_STATUS_CODE: 1`（started）与 `numtests`。**有 started 无 finished ⇒ 卡在测试体内；连 started 都没有 ⇒ 卡在 runner/Activity 层**。09-23 用的是不带 `-r` 的调用，所以那条 76 字节记录不具备这个判别力 —— 这是本轮最可执行的发现。
2. **对照组**：同一次会话里先跑一个不启动 Activity 的 instrumented 类（本项目历史上 `EpubParserInstrumentedTest` 曾 `OK (4 tests)`），再跑 HighSpeedTest。前者通过而后者挂起 ⇒ 阻塞特定于 Compose/Activity 路径，而不是 instrumentation 通道。
3. **抓全量日志**：挂起发生的当下 `logcat -d`（**不要** `-t 100`，不要先 grep）+ 每 5 秒 `dumpsys activity activities` 采样，看 `ComponentActivity` 是否进过 `RESUMED`。09-23 的失败在于窗口已滚过、且检索带过滤 ⇒ "没抓到"不等于"不存在"。

## 7. 剩余风险（不粉饰）

- **R1** 我对 `runOnIdle` 的判断来自反编译字节码的行为推断，**不是运行验证**。若它实际还会泵帧，改后我用 `runOnUiThread` 已不再依赖这个判断，风险被绕开而非被证明。
- **R2** `advanceTimeBy` 按 16ms 帧格推进，副作用是"帧数取整"，但我的时间余量（144ms 对 600ms、6000ms 对约 3.2s）都是 4 倍以上，不再是临界值。
- **R3** `burstBeyondCapacityIsBounded` 只钉数量，不区分"丢最新/丢最旧"；纯同向爆发下二者不可分辨，我没有假装覆盖，策略差异留在 `PageTurner.kt:357-363` 的显式注释与评审。
- **R4** 单用例耗时上升（每用例最多约 12s 的有界推进），5 个用例合计 <1 分钟；若下一轮环境本就挂起，这些数字不会改变结论。
- **R5** 本轮没有 JVM 侧替代测试可跑：`PageTurner` 是 Compose 组件，其排空链依赖帧驱动，脱机跑不了。若要让 S06-⑤ 具备"可在 CI 之外验证"的防线，需要把入队/上限/反向替换逻辑抽成纯函数（那是产品代码改动，越出本轮所有权，未做）。

## 8. 边界声明

- 只修改了 `PageTurnerHighSpeedTest.kt`（我所有权内）+ 新建本报告；`PageTurner.kt` 与其他窗口文件（`EpubRepository.kt`、`JsonBridge.kt`、SearchIndex 协调器等）未触碰，未覆盖他人改动。
- **无需 SEAM REQUEST**：所有修复都落在测试文件内。
- 未执行 reset/checkout/clean/stash/stage/commit/push；未运行 Gradle 之外的构建任务（只跑了 androidTest 编译，未跑 lint/assemble/JVM/connectedAndroidTest）；未使用真机、adb、MuMu 或任何模拟器；未做横屏。
- HEAD 复核：`5aed5939600e491843e3c44453921bd6273a233c`（未变）。本报告文件名日期为 2026-09-24，是因为本轮跨过零点：实际执行 2026-09-24 00:5x–01:0x。

完成后停止，等总指挥复核。不领取下一任务。
