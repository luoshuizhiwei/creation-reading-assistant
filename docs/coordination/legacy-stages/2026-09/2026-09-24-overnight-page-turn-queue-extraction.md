# 2026-09-24 夜间：待处理翻页队列策略抽取（窗口 C）

工作仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`｜分支 `codex/recover-worktree-20260920`｜HEAD `5aed5939600e491843e3c44453921bd6273a233c`（本轮未变）。
本轮**零设备操作**：未运行 adb、真机、模拟器、系统设置；未跑全量 build / lint。

## 0. 结论

把 `PageTurner.kt` 里"待处理翻页方向"的纯策略抽成 `PendingTurnQueue`（普通 Kotlin 类，不 import Compose），配上 11 项 JVM 用例，并用**三次变异**证明这些用例真的能区分错误策略。动画、`settle`、`turnEpoch` 排空时序、帧快照、位图轮换、手势全部未改；`最多 3 个 pending` 语义原样保留，且**正在播放的那一个请求不计入 pending**（容量 3 = 「1 在播 + 3 排队」= 一次爆发最多 4 页）。

## 1. 输入

`AGENTS.md`、`PageTurner.kt`（866 行，本轮改前状态）、`PageTurnerHighSpeedTest.kt`（393 行，**本轮一字未改**，mtime 仍为 01:00:31）、`docs/qa/2026-09-24-c-high-speed-test-diagnosis.md`。抽取动机正是该诊断的结论：这台机器的 Compose instrumentation 现在跑不起来（根因尚未实证），所以 S06-⑤ 的策略必须有能脱机跑的防线。

## 2. 新对象契约（`PendingTurnQueue.kt`，`internal`）

| 成员 | 语义 | 对应原代码 |
|---|---|---|
| `capacity: Int = PENDING_TURN_CAPACITY`（=3，`internal const` 移到本文件） | 未开始请求的上限；**不含在播的那一个** | 原 `PageTurner.kt:888` 的 `private const` |
| `enqueue(direction: Int): Boolean` | 0 拒收；与队尾反向 ⇒ 先清空未开始积压再只留这一笔；已满 ⇒ **丢最新**并返回 false（不挤掉已排队项）；否则入队尾返回 true | 原 `:357-363` 局部 `enqueueTurnRequest`（原来无返回值） |
| `poll(): Int?` | 取队首，空则 null | 原 `:374` `removeFirstOrNull()` |
| `requeueFront(direction)` | 把刚 poll 出的方向放回**队首**（抢占重试） | 原 `:384` `addFirst` |
| `clear()` / `size` / `isEmpty` | 丢弃全部积压 | 原 `:364`、`:359-361` 读取 |

## 3. 精确接线（只此 6 处，其余字节未动）

| 位置 | 改前 | 改后 |
|---|---|---|
| `:120` | `val pendingTurns = remember { ArrayDeque<Int>() }` | `remember { PendingTurnQueue() }` |
| `:349-363` | 局部 `fun enqueueTurnRequest(direction)`（含 15 行策略与 KDoc） | **删除**，策略归入新对象 |
| `:364`（R2 重置） | `pendingTurns.clear()` | 不变（现在是方法调用，签名同名） |
| `:371` | `enqueueTurnRequest(requested)` | `pendingTurns.enqueue(requested)` |
| `:374` | `pendingTurns.removeFirstOrNull()` | `pendingTurns.poll()` |
| `:384` | `pendingTurns.addFirst(direction)` | `pendingTurns.requeueFront(direction)` |
| `:365` 上方 | 悬挂的排空 KDoc（原指向已不存在的函数） | 改为 effect 上方的 `//` 注释，并指明策略在哪、为何不能"一个协程循环 settle" |
| 文件尾 | `private const val PENDING_TURN_CAPACITY = 3` | 删除（避免与新文件的 `internal const` 形成双事实源） |

**未触碰**：`settle`、`turnWithoutNeighborSnapshot`、`turnEpoch`/`pumping`/`dragging` 闸门、拖拽回调、渲染分支、位图与帧快照逻辑。改完后 `grep pendingTurns` 只剩 5 个调用点，全部经由新对象 API。

## 4. 定向测试（`PendingTurnQueueTest.kt`，11 项，纯 JVM）

每条都点名一种会**假装通过**的错误实现：

| 用例 | 钉死的错误策略 |
|---|---|
| `capacityIsThreeUnstartedRequests` | 容量被改成 2/4；以及把"在播的那个"也算进 pending（断言 3 + 与 `PENDING_TURN_CAPACITY` 同源） |
| `sameDirectionTapsAllQueueUpWithinCapacity` | 同向被去重（只留 1 个）⇒ 连点不再一页一点 |
| `overflowDropsTheNewestRequestAndKeepsTheBacklogIntact` | 满时丢最旧 / 无限扩容 |
| `oppositeTapReplacesUnstartedBacklogWithItselfOnly` | 反向=取消（size 会 0）、反向=追加（size 会 3、队首仍是旧方向） |
| `oppositeTapOnEmptyQueueIsAccepted` | 「反向清空」逻辑把空队列下的第一笔反向也吞掉 |
| `reversalAfterBacklogWasDrainedStillReplacesWhatRemains` | 出队一次后反向判断失效 |
| `requeueFrontRestoresTheRequestAtTheHead` | 重试时放回队尾 ⇒ 该请求被后来者插队 |
| `requeueFrontAtCapacityStillAcceptsBecauseItReplacesAPop` | 归还时被容量挡住而静默丢失 |
| `zeroDirectionIsNeverAccepted` | 把外层空槽值 0 当真实方向入队 ⇒ 翻"第 0 页" |
| `pollOnEmptyQueueReturnsNull` | 空队列抛异常（`ArrayDeque.removeFirst` 语义） |
| `clearDropsTheWholeUnstartedBacklog` | 效果切换后残留半程请求 |

## 5. 变异检验（证明用例有牙，不是自点自测）

对 `PendingTurnQueue.kt` 逐条施加**语义相反**的改动，每次先还原并核对 sha256 一致：

| 变异 | 改成什么 | 期望 | 实际 |
|---|---|---|---|
| A | 满时 `removeFirstOrNull()` + 继续入队（丢最旧） | `overflowDrops…` 变红 | exit 1，`11 tests completed, 1 failed`，红的正是 `overflowDropsTheNewestRequestAndKeepsTheBacklogIntact`，其余 10 项仍绿 |
| B | 反向时 `clear()` 后 `return false`（反向=取消） | 两条反向语义用例变红 | exit 1，`11 tests completed, 2 failed`：`oppositeTapReplacesUnstartedBacklogWithItselfOnly`、`reversalAfterBacklogWasDrainedStillReplacesWhatRemains` |
| C | `requeueFront` 写成 `addLast`（放回队尾） | 队首归还用例变红 | exit 1，`11 tests completed, 1 failed`：`requeueFrontRestoresTheRequestAtTheHead` |

还原后 sha256 = `4e5c87a1765c2be83dc3d2d507a421347dd8098c02193a4d610d502414b5b3ff`（与施加变异前逐位一致）。

## 6. 实际命令与退出码（均 `--offline --no-parallel`）

| # | 命令 | 起止 | 退出码 | 结果 / XML |
|---|---|---|---|---|
| 1 | `:app:testDebugUnitTest --tests com.creationreadingassistant.feature.reader.pager.PendingTurnQueueTest` | 02:57:46→02:58:57 | **0** | BUILD SUCCESSFUL 1m10s；`30 actionable tasks: 10 executed`（含 `:app:compileDebugKotlin`、`:app:compileDebugUnitTestKotlin`、`:app:testDebugUnitTest`）；结果目录 1 个 XML：`tests=11 failures=0 errors=0 skipped=0` |
| 2 | 同上（变异 A） | — | 1 | `11 tests completed, 1 failed` |
| 3 | 同上（变异 B） | — | 1 | `11 tests completed, 2 failed` |
| 4 | 同上（变异 C） | — | 1 | `11 tests completed, 1 failed` |
| 5 | `:app:testDebugUnitTest --tests …PendingTurnQueueTest :app:compileDebugAndroidTestKotlin` | 03:0x→+33s | **0** | BUILD SUCCESSFUL 33s；`tests=11 failures=0 errors=0 skipped=0`；androidTest 源集编译通过（证明 `PageTurnerHighSpeedTest.kt` 对 `PageTurner` 的调用未被接线破坏） |

日志与 XML 快照：`android/app/build/tmp/c-s06-queue-extraction/`（`jvm-pendingturnqueue.log`、`mutation-{A,B,C}.log`、`final-verify.log`、`.time`、`queue-sha-before.txt`、`PendingTurnQueue.kt.orig`、XML 副本）——全部在 gitignore 范围内的构建目录，未进版本控制。
修正轮次：**0**（首轮即通过，无重跑刷绿；上述 1 退出码均为刻意变异所致）。

## 7. 边界声明

- 只写了 4 个文件：`PageTurner.kt`（接线）、新建 `PendingTurnQueue.kt`、新建 `PendingTurnQueueTest.kt`、本报告。**未**改 `PageTurnerHighSpeedTest.kt`，未改其他窗口文件（同目录 `PageSelection.kt`、`PagedReaderClock.kt`、`PagedReaderPageSurface.kt`、`ReaderHardwareKeys.kt` 等仍是他人既有脏状态，mtime 与内容未动；`CustomTipPlaceholder.kt` 等未跟踪新文件也不是我建的）。
- 未执行 reset / checkout / clean / stash / stage / commit / push；未进入原始仓库。
- 未运行全量 JVM、lint、assemble、connectedAndroidTest、设备命令；**不宣称真机翻页已通过**。

## 8. 剩余风险

1. `enqueue` 现在返回 Boolean，但 `PageTurner` 里**丢弃返回值**（保持原行为）。要把"被上限合并掉"变成可观测信号（例如 UI 侧统计），需要另一个决策点，不在本轮范围。
2. 队列策略与"排空时序"仍然分家：JVM 测试证明不了 `turnEpoch` 链一定会把积压排完、也证明不了"每轮动画用的是换帧后的新闭包"。那部分仍只有 `PageTurnerHighSpeedTest` 的 instrumentation 覆盖 —— 而它现在跑不了。这条是 S06-⑤ 验收的真缺口，未因本次抽取而关闭。
3. 容量 3 仍是判断而非测量（诊断报告 R2 同一条）。
4. 反向替换与"丢最新"在纯同向爆发下不可区分，我按可变异的返回值断言补上了区分面，但真机手感（停手后是否还在翻页）仍未观测。

完成后停止，不领取下一任务。
