# S06-⑤/⑥ 连续点按/反向点按/队列边界 — 当前代码与证据核对（C 窗口回读，2026-09-27）

- 仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`｜分支 `codex/recover-worktree-20260920`｜本轮**只读**：未运行 Gradle/ADB/模拟器，未改任何源码/测试/账本；唯一写入 = 本报告。
- 核对对象：`PageTurner.kt`、`PendingTurnQueue.kt`、`PendingTurnQueueTest.kt`、`PageTurnerHighSpeedTest.kt` 四个文件的**当前内容**（不沿用旧行号/旧 API/历史测试数字），以及磁盘上可回读的工件。

## 1. 当前代码状态（逐字读码核实）

四个文件全部是**未提交**状态：`PageTurner.kt` 为 ` M`（mtime 2026-09-24 02:57），其余三个为 `??` 未跟踪（12:57–12:59 / 01:00）。HEAD 自 09-24 夜间对账后未动。

### 1.1 请求链（当前真实实现）

| 环节 | 位置 | 语义 |
|---|---|---|
| 单槽所有者 | `PagedTxtReaderHost.kt:195` `mutableIntStateOf(0)` | 全链只有一个 `Int` 槽；三个生产者写、两个互斥消费者读 |
| 生产者①点按 | `PagedReaderPageSurface.kt:428` `turnRequest.intValue = turnPlan.direction` | 点按只写计划，不直接翻页 |
| 生产者②按键/控制器转发 | `PagedReaderControllerEffects.kt:187`（`externalTurnRequest` → `turnRequest`，同样单槽转发） | 硬件键与外部请求经同一槽 |
| 生产者③自动翻页 | `PagedReaderControllerEffects.kt:224` `turnRequest.intValue = 1` | 自动翻页走同一槽 |
| 消费者A（reveal 渲染器） | `PagedReaderPageSurface.kt:282-289` 自消费并归零 | 与消费者B按 `pageTurnRenderer` 互斥 |
| 消费者B（其余渲染器） | `PagedReaderPageSurface.kt:552-553` 传给 `PageTurner`，`onTurnRequestConsumed` 归零 | 即本窗口的核心链 |
| **入队** | `PageTurner.kt:373-377`：先 `onTurnRequestConsumed()` 归零单槽，再 `pendingTurns.enqueue(requested)` | 两步之间无挂起点，原子；归零在前保证「消费契约」不被 pumping/turning 破坏 |
| **排空** | `PageTurner.kt:378-405`：`pumping/turning/dragging` 任一为真直接返回（请求留队列）；否则 poll 一个 → `scope.launch { settle }`，`finally { pumping=false; turnEpoch++ }` | **一次只排空一个**，靠 `turnEpoch++` 在换帧后的新闭包里重新装配下一轮（`:365-369` 注释说明为什么不能单协程循环排空——旧闭包会用过期帧冻结 TurnScene） |
| **反向请求** | 队列侧 `PendingTurnQueue.enqueue`：队尾异向 ⇒ `clear()` 后只留这一笔；出队后未启动被抢占 ⇒ `requeueFront` + `turnEpoch++` 重试（`PageTurner.kt:386-392`） | 反向 = 作废未开始积压，保留最新反向意图；不取消在播动画 |
| **容量上限** | `PENDING_TURN_CAPACITY = 3`（`PendingTurnQueue.kt:63`）；满 ⇒ 丢**最新** | 一次爆发 = 1 在播 + 3 排队 = 最多 4 页 |
| **拖拽** | 拖拽本身不入队（`onDragEnd` 直接 `settle`）；`dragging` 期间到达的点按先入队、拖拽结束 `turnEpoch++` 重排（`:434` / `:453`） | 拖拽不永久压住点按队列 |
| **效果切换清队** | `LaunchedEffect(normalizedEffect)` `PageTurner.kt:350-363`：`pendingTurns.clear()` + 全状态重置 | 效果切换作废全部积压 |
| **切书清队** | **未发现**：`:545` 的 `PageTurner(...)` 调用点未见 `key(bookId)` 包裹，仓库内也没有针对翻页队列的切书清除调用 | 见 §3 风险 4 |
| **末页边界** | 邻页 null ⇒ `settle` 走 `:271-273` 动画回位，或 `turnWithoutNeighborSnapshot`（`:335-347`）消费一次并照常 `turnEpoch++` | 队列**不卡死**，逐笔消费 |

### 1.2 队列类本体（`PendingTurnQueue.kt`，63 行）

`enqueue`（拒 0 / 异向清积压 / 满丢最新）→ `poll`（removeFirstOrNull）→ `requeueFront`（**不查容量**，契约=只归还刚 poll 出的那一个，`:43-50` 注释与测试均钉住）→ `clear`。文件 sha256 = `4e5c87a1…5b3ff`，与边界复核工件 `queue-sha.txt` 记录值、`.orig` 快照**逐位一致**（diff exit 0）——中午 12:57–12:59 的边界复核窗口之后队列实现未被再改动。

## 2. 可回读测试证据（分级）

| 证据 | 状态 | 依据 |
|---|---|---|
| JVM 队列策略测试（当前 **12** @Test） | **部分可回读** | 执行日志在：`build/tmp/c-s06-queue-boundary/green-run.log`（09-24 12:58，`testDebugUnitTest` 真实执行、BUILD SUCCESSFUL）；红证据 `mutation-D.log`（对变异实现跑同 12 项：2 failed，红的正是容量与积压两条 ⇒ 用例有牙）；`final-green.log`（12:59）为 **FROM-CACHE，非新鲜执行**。**Gradle XML 原始结果不可回读**：`build/test-results/testDebugUnitTest/` 现只剩 17 份 09-26 02:20 定向运行残留，账本引用的 11/11 XML（09-24 03:03）已被覆盖 |
| JVM 测试断言面 | 策略级，**不含**页号/帧/回调断言 | `PendingTurnQueueTest.kt` 12 项全是队列语义（容量/FIFO/丢最新/反向替换/requeueFront/不变式/clear）；它测不到最终页号——那不是它的职责 |
| instrumentation 队列用例（当前 **5** 项 + 3 像素项） | **从未运行，0 项结果** | `build/outputs/` 下无任何 connected XML；编译产物存在（debugAndroidTest classes 09-24 01:01、qahost 09-25 17:28；test APK 09-25 06:48/17:28），但运行证据为零。与账本「5 项 instrumentation 仍 0 项」一致 |
| instrumentation 断言面 | **确有牙**（源码级核实） | `PageTurnerHighSpeedTest.kt` 5 个队列用例均断言：最终页号 `s.page`、`onNext/onPrevious` 回调次数、通道消费次数 `consumedRequests`（区分「少投递」与「实现丢请求」）、**当前页槽渲染身份**（`lastRenderedCurrent == page` + `currentIdentities` 含 0/1/2 且末位=3 ⇒ 防「页号对但显示旧帧」）；末页用例断言不卡队列；上限用例断言停手后不再自翻 |
| 真机/MuMu | **0** | 本轮与既往窗口均未执行 |

## 3. 三个重点风险的当前状态

1. **「页号正确但显示旧帧」** — 设计上由 `turnEpoch` 逐轮重装配 + `TurnScene` 快照防守，且有 `currentIdentities` 断言等着抓；**但该 instrumentation 用例从未运行 ⇒ 防线未被执行验证**。JVM 侧无对应物（`PageTurner` 是 Compose 组件）。状态：**风险未消除，测试已备未跑**。
2. **「最后一页后队列卡住」** — 代码路径已闭合（`turnWithoutNeighborSnapshot` 消费 + `finally turnEpoch++`）；用例 `tapsAtLastPageStillConsumeRequestsWithoutStalling` 已写未跑。状态：**未验证**。
3. **「同帧单槽合并」** — **仍然存在且已知**：上游单槽 `mutableIntStateOf` 三生产者写入，同帧两笔同向合并为一笔；测试用 24ms 间隔规避（`TAP_INTERVAL_MS` 注释自认），`consumedRequests` 计数能把合并暴露出来但无法修复它。C 窗口报告将其登记为 S09/S06 交叉独立一片。状态：**未修复、未覆盖、已登记**。
4. **（本轮新发现）切书/程序化跳转不清队列** — 队列只在效果切换时 `clear()`；若切书时宿主组合位置不变，`remember { PendingTurnQueue() }` 存活，旧书积压会排到新书。未找到 keying 证据，也未找到任何清除调用。状态：**未覆盖，建议登记待决**。

## 4. 仍缺的验收与最小下一步

- **仍缺**：真机/MuMu 上 5 项队列 instrumentation + 3 项像素用例的**任何一次**运行结果（0/8）；JVM 12 项的 Gradle XML 原始产物（日志可回读、XML 不可）；切书/跳转不清队行为的任何验证。
- **最小下一步（交总指挥裁决，本轮不执行）**：在可用设备/CI 上跑一次 `PageTurnerHighSpeedTest` 全 8 项并留存 XML；同时把「切书清队」登记为待决项（一行 `clear()` 或 key 包裹，属产品语义选择，需 Codex 定夺）。
- 时间线瑕疵登记：边界复核报告 §8 自称执行窗口「00:5x–01:0x」，但其工件 mtime 均为 09-24 12:57–12:59，报告内部时序自相矛盾（不影响工件与当前文件的一致性结论，sha/diff 均已独立复核）。
