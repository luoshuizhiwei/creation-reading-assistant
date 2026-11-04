# 2026-09-24 翻页队列边界复核（窗口 C）

仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`｜分支 `codex/recover-worktree-20260920`｜HEAD `5aed5939600e491843e3c44453921bd6273a233c`（未变）。
复核命题：**`poll()` 之后、异步 `requeueFront()` 之前**是否可能插入新请求入队，从而（1）突破 `capacity=3`；（2）在期间发生反向请求时把旧方向放回队首，违反「反向作废未开始积压」的契约。
本轮未运行真机 / adb / 模拟器 / 全量门禁。

## 0. 结论

**不可达。** 在 Android 组合运行时下，`enqueue` 只有一个生产调用点，且它所在的那次 `LaunchedEffect` 体重跑必须先经过一次重组（=一个 Choreographer 帧），而帧回调与协程 trampoline 队列是两条不同的投递路径，无法插进 `poll()` 与 `requeueFront()` 之间。因此两条隐患都依赖同一个不成立的交错前提。

按指令，**产品代码一字未改**（`PendingTurnQueue.kt` 还原后 sha256 与基线逐位一致，`PageTurner.kt` mtime 仍 02:57:35 未动）。唯一新增是 1 条只钉「类侧容量不变式」的 JVM 用例，并用变异 D 证明它确实能红。

## 1. 代码事实（先固定讨论对象）

| 位置 | 语句 | 说明 |
|---|---|---|
| `PageTurner.kt:371-379` | `LaunchedEffect(turnEpoch, turnRequest)` 体内：`if (turnRequest != 0) { … pendingTurns.enqueue(requested) }` → `if (turnGate.pumping || turning || turnGate.dragging) return` → `val direction = pendingTurns.poll()` | 入队与出队在**同一次** effect 体内、同一段无挂起代码里 |
| `:380-393` | `turnGate.pumping = true` → `scope.launch { if (turning || turnGate.dragging) { pendingTurns.requeueFront(direction); … } }` | 归还发生在被 `rememberCoroutineScope()` 投递出去的协程体首段 |
| `:354` | `LaunchedEffect(normalizedEffect)` 里 `pendingTurns.clear()` | 另一个 mutator，同样只在 effect 体内 |

生产码里对队列的全部 mutator 调用点（`grep -rn "pendingTurns\." android/app/src/main`）：`enqueue` **1 处**（`:376`）、`poll` 1 处（`:379`）、`requeueFront` 1 处（`:389`）、`clear` 2 处（`:354` 与 `enqueue` 内部反向分支）。**没有任何别的模块能往队列里塞请求。**

`turnRequest` 的生产者有两个（都在 UI 线程、都由帧驱动）：外部点按/按键经 `PagedReaderControllerEffects.kt:185-189` 转发；自动翻页经 `PagedReaderControllerEffects.kt:224` `turnRequest.intValue = 1`。二者都只是**写状态**，要变成队列内容必须等重组 → 下一帧。

## 2. 调度证据（为什么插不进来）

对 `androidx.compose.ui.ui-release` (1.9.0) 的 `androidx.compose.ui.platform.AndroidUiDispatcher` 做 `javap` 取证：

1. `AndroidUiDispatcher` **没有覆写** `isDispatchNeeded`（全量方法表内 0 命中）⇒ 走 `CoroutineDispatcher` 默认实现（返回 true）⇒ `scope.launch { … }` 不会内联执行，**会被投递**。这一点是**支持**命题成立的（确实存在 T1→T2 的窗口），我没有反过来用它糊过去。
2. `dispatch()` 字节码：`synchronized(lock) { toRunTrampolined.addLast(runnable); if (scheduledTrampolineDispatch) return; scheduledTrampolineDispatch = true; handler.post(dispatchCallback) }`，另有一条**独立**的 `if (!scheduledFrameDispatch) choreographer.postFrameCallback(dispatchCallback)`。⇒ 在已经处于 trampoline 派发期间，新任务只是**追加到同一个双端队列**，不会另起投递。
3. `performTrampolineDispatch()` 字节码是 `while ((task = nextTask()) != null) task.run()`，`nextTask()` 只从 `toRunTrampolined` 取；帧回调走 `performFrameDispatch(long)` 里遍历 `toRunOnFrame` 并调 `Choreographer$FrameCallback.doFrame` —— 与 trampoline 队列**不是一条路**，且 `nextTask()` 里没有把帧任务插进来的分支。
4. 组合侧：重组、`applyChanges`、`LaunchedEffect` 体的启动都在帧派发路径里（`withFrameNanos` 的续体由 frameDeferring 拦截器交给帧回调）。⇒ **要让第二次 `enqueue` 跑起来，必须先有一个帧；而帧不能插进第 3 点那个同步 while 排空循环中间。**

把 1-4 合起来：`poll()` 所在的 T1 与 `requeueFront()` 所在的 T2 之间，最多只会被同一个 trampoline 队列里的其他**协程续体**隔开，而那些续体里没有第二个 `enqueue` 入口。⇒ 交错不成立。

**反向契约部分同理**：要让反向请求先落到队列再把旧方向顶到队首，需要同一个不成立的交错。若反向请求是在 `poll` **之前**到达（真实可达），那它和旧方向本来就在同一 FIFO 里，顺序就是用户点按顺序，不违反契约；若旧方向已经在播（被 `settle` 拿走），那它是「在播的那一个」，按定义不属于可被作废的积压 —— 与 `docs/coordination/legacy-stages/2026-09/2026-09-24-overnight-page-turn-queue-extraction.md` §2 的容量口径一致。

## 3. 反方假设与最坏情况（不粉饰）

- 若未来 Compose 改调度（例如让 effect 体内的 `launch` 内联/或把帧任务并进同一队列），归还路径确实**会**越容：`poll` 后 size ≤ 2，一次交错 `enqueue` 回到 3，再 `requeueFront` ⇒ **4**，越界幅度上界 +1（因为"手上最多只握一个方向"，`pumping` 闸门保证不会有两个泵并发）。崩溃不会发生（`ArrayDeque` 无界），后果是多翻一页 / 反向时被插入一次过期翻页。
- `requeueFront` 刻意不查容量，契约写在 `PendingTurnQueue.kt` 的 KDoc：「它的契约就是归还自己刚取走的那一个，此时必有空位」。`PageTurner.kt:387-388` 的注释也写明只在被抢占时归还。这个不变式目前由**调用方时序**保证，不是由类型保证。
- 因此我加的那条用例是把"类侧兜不住、越容只能来自归还"这件事钉进测试，而不是伪装成类型保证。

## 4. 测试与运行记录（全部 `--offline --no-parallel`）

新增用例 `enqueueAloneCanNeverExceedCapacity`（`PendingTurnQueueTest.kt`）：21 个方向的序列（含同向连击、反复反向、超出容量的连点、`0` 空槽值、±3/±7/±9 等任意方向值），**每一步之后**断言 `size <= capacity`。它只钉类侧不变式，不假设调用方时序。

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | `:app:testDebugUnitTest --tests com.creationreadingassistant.feature.reader.pager.PendingTurnQueueTest --offline --no-parallel` | **0** | BUILD SUCCESSFUL 45s；XML：`tests=12 failures=0 errors=0 skipped=0` |
| 2 | 变异 D：删掉 `enqueue` 里 `if (directions.size >= capacity) return false`（使队列无界） | 1 | `12 tests completed, 2 failed` → 变红的正是 `enqueueAloneCanNeverExceedCapacity` 与 `overflowDropsTheNewestRequestAndKeepsTheBacklogIntact`；其余 10 项仍绿 ⇒ 新用例有牙且不空转 |
| 3 | 还原后同 1 | **0** | BUILD SUCCESSFUL 26s；XML：`tests=12 failures=0 errors=0 skipped=0` |

产品文件完整性：`PendingTurnQueue.kt` sha256 = `4e5c87a1765c2be83dc3d2d507a421347dd8098c02193a4d610d502414b5b3ff`（变异前基线与还原后逐位一致）；`PageTurner.kt` 本轮未被写入。
日志与 XML/备份：`android/app/build/tmp/c-s06-queue-boundary/`（`green-run.log`、`mutation-D.log`、`final-green.log`、`queue-sha.txt`、`PendingTurnQueue.kt.orig`），在 gitignore 范围内。
定位修正轮次：0。

## 5. 本轮结论对应的证据等级

| 判断 | 等级 | 依据 |
|---|---|---|
| `enqueue` 生产调用点唯一 | 静态确证 | grep 全仓 main 源集 |
| `AndroidUiDispatcher` 不覆写 `isDispatchNeeded`、trampoline 与帧回调两条投递路径分离 | 静态确证（库字节码，非运行） | `javap` 方法表 + `dispatch`/`nextTask`/`performTrampolineDispatch`/`performFrameDispatch` 字节码 |
| 交错窗口不可达 | **由上述两项推出的结论，未经运行时验证** | 无法在 JVM 侧复现 Compose 调度；`PageTurnerHighSpeedTest` 那套 instrumentation 目前在本机跑不起来 |
| 越容最坏幅度 +1 | 静态推演 | `pumping` 单泵 + 手上只握一个方向 |

## 6. 真机仍未覆盖的行为（明确不宣称）

1. 连点是否真的「一次一页」、停手后是否不再自翻页 —— 只有 instrumentation/真机可证，仍是 0 结果。
2. 在播动画不被打断、每轮动画使用换帧后的新帧（`currentIdentities` 那条断言）—— 同上。
3. 反向请求在真实手指节奏下的观感延迟（是否"像丢了一页"）。
4. 自动翻页与手动点按**混用**时谁抢到单槽：本轮分析只说明"不会越容、不会复活已作废方向"，并**不**说明同帧内两路生产者谁赢；那是 `PagedReaderControllerEffects.kt:185-189/224` 的单槽语义，属别的窗口所有权。
5. 若将来有人在非 UI 协程里调用 `enqueue`（例如后台任务代发翻页）：本文件全部时序论证的前提是「三个 mutator 都只在 UI 线程的 effect 体内跑」，前提一破，容量与反向结论同时失效。这是我认为最值得盯住的一条真实风险，但它现在**没有**发生。

## 7. 边界声明

本轮写入：`PendingTurnQueueTest.kt`（新增 1 条用例）+ 本报告。`PageTurner.kt`、`PendingTurnQueue.kt`、`PageTurnerHighSpeedTest.kt` 及其他窗口文件均未修改 —— 因此**不需要 SEAM REQUEST**（没有跨所有权需求）。未 reset / checkout / clean / stash / stage / commit / push；未运行全量 build / lint / 设备测试。

建议给总指挥的裁决点（我不自行执行）：是否要把「归还必有空位」从调用方时序不变式升级为类型不变式（`requeueFront` 改为 `Boolean requeueFront()`，满则拒收并由调用方决定丢弃谁）。那会改变抢占路径的行为语义，且现有设备侧证据为零，我倾向**等真机通道打通后再定**。

完成后停止，不领任务。
