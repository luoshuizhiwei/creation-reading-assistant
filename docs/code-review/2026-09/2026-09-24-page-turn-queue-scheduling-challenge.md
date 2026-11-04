# 翻页队列调度结论的独立挑战（2026-09-24 · 窗口 D · 只读）

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`
- HEAD：`5aed5939600e491843e3c44453921bd6273a233c`（本轮 `git rev-parse` 实测），staged **0**
- 被挑战命题（窗口 C 报告 §0）：**「`poll()` 之后、异步 `requeueFront()` 之前不可能插入新 `enqueue` ⇒ 既不越 `capacity=3`，也不违反反向契约」，判为「不可达」**
- 本轮性质：**只读挑战**。未修改任何源码、测试与他窗口报告；未运行 Gradle / ADB / 手机；未动容量策略。
- 阅读对象：`PageTurner.kt`、`PendingTurnQueue.kt`、`PagedReaderControllerEffects.kt`、
  `docs/coordination/reviews/2026-09/2026-09-24-page-turn-queue-boundary-review.md`（窗口 C）

## 0. 裁决

**结论我复现成立（当前代码下窗口不可插入 enqueue），但 C 给出的论证骨架不是承重的，而且比结论更强——强到经不起检查。** 三处必须更正：

1. C 把「必须先有一个帧」当承重前提；真正承重的是**「唯一 enqueue 站点 + 单线程 UI 派发器 + 同一双端队列的追加序」**。这个差别不是文字问题：它决定了未来哪种改动会让结论失效（见 §3.1 的反例类）。
2. C 说抢占/归还路径是「唯一的窄竞态窗口」。**它不需要任何跨帧竞态就能命中，是同一次排空里的常规分支**（§2 的 S3 序列，纯代码可达）。把它当罕见竞态会低估这条路径的被测需求。
3. C 的字节码取证对象**恰好**是对的，但项目 BOM 声明与解析结果不一致，而本轮**没有跑 `:app:dependencies`**，所以「编译期用的就是 1.9.0」仍是推断（§4 前提 P5）。

不擅自改队列容量策略；`requeueFront` 是否改成返回 `Boolean`，我同意 C 的「等真机通道打通再定」。

## 1. 我独立核对到的代码事实（本轮亲自读源 / grep）

| # | 事实 | 位置 |
|---|---|---|
| F1 | 全 main 源集对队列的 mutator 只有 4 处：`clear:354`、`enqueue:376`、`poll:379`、`requeueFront:389`；`enqueue` **唯一** | `grep -rn "pendingTurns\." --include=*.kt` 命中即全量 |
| F2 | `LaunchedEffect(turnEpoch, turnRequest)` 体内顺序是 `:373` 判 `turnRequest != 0` → `:375` **先消费** `onTurnRequestConsumed()` → `:376` 入队 → `:378` 三闸门 → `:379` poll → `:380` `pumping=true` → `:385` `scope.launch`。**`:373`–`:385` 之间无挂起点** | `PageTurner.kt:370-405` |
| F3 | `turnRequest` 是**捕获值参数 `Int`**（不是 `MutableIntState`），所以每个 effect 实例带自己那一次重组捕获的值；配合 F2 的「先归零再入队」，**同一次排空内重跑的 effect 体不会重复入队** | `PageTurner.kt:88`、`:373-376` |
| F4 | 外部点按路径要**两跳**才变成队列内容：`:185-189` `LaunchedEffect(externalTurnRequest.value)` 把值转发进 `turnRequest.intValue`，PageTurner 侧再等一次重组才 launch → 入队 | `PagedReaderControllerEffects.kt:185-189` |
| F5 | 自动翻页生产者 `:224 turnRequest.intValue = 1` 位于 `while(true) { val frame = withFrameNanos { … } }` 体内 ⇒ 帧驱动写状态，入队仍需再下一帧 | `PagedReaderControllerEffects.kt:194-231` |
| F6 | `scope = rememberCoroutineScope()`（UI 派发器）；`TurnGate` 是普通 `class`，两个 `Boolean` 字段，无锁 | `PageTurner.kt:114`、`:867-870` |
| F7 | `settle` 在 `:268` 判 `turning`、`:270` 判邻居；**`neighbor != null` 时 `:275 turning = true` 之前没有挂起点**；`neighbor == null` 时 `:271 dx.animateTo(0f, tween(120))` **先挂起** | `PageTurner.kt:267-275` |
| F8 | 这两个文件里**没有** `Dispatchers.IO/Default`、没有 `Snapshot.sendApplyNotifications`（后者是唯一能把「写状态→重组」变同步的入口） | grep 结果：0 命中 |
| F9 | C 的文件完整性自述可独立复现：`PendingTurnQueue.kt` sha256 `4e5c87a1765c2be83dc3d2d507a421347dd8098c02193a4d610d502414b5b3ff`、`PageTurner.kt` mtime `2026-09-24 02:57:35`、`PendingTurnQueueTest` 现有 **12** 个 `@Test` | 本轮 `sha256sum` / `stat` / `grep -c @Test` |

## 2. 为什么结论仍然成立（我认为的正确论证）

设 `T1` = `PageTurner.kt:370` 那个 effect 体的一次执行，`T2` = `:385 scope.launch { … }` 的协程体。
`poll()` 在 T1 内，`requeueFront()` 是 T2 的首段语句 ⇒ 争辩窗口 = 「T1 的 `:379` 之后、T2 首段之前」。

1. **单线程**：`T1`、`T2` 与所有可能调用 `enqueue` 的协程都跑在 `AndroidUiDispatcher`（主线程）。同一线程不会并发执行两个任务 ⇒ 破坏窗口只能表现为「另一个任务被排在 T2 **之前**」。
2. **追加序（FIFO）**：`T2` 由 `:385` 在 T1 的**最后一步**入队。因此在 T2 之前执行的，只能是**在 T1 运行时就已排在队列里**的任务——不可能是「T1 之后才产生的任何续体」。
3. **那些更早的任务能不能调用 `enqueue`？** 由 F1：`enqueue` 只出现在 F2 的 effect 体内。由 F3 + 组合语义：该 effect 节点**每帧最多重启一次**（值参数 key 的一次变化只产生一次重组 ⇒ 一次 `LaunchImpl` 投递）。于是「队列里比 T2 更早、且会执行 `enqueue` 的任务」必须来自**更早一帧的另一次重启**——而 T1 正是那一次（同一节点不可能有两个未启动的重启并存），并且它被 F2 的 `:375` 归零挡住 ⇒ 不会重复入队。
4. **`poll()` 之后不再有挂起点**（F2），所以第 3 步不需要拿「帧回调 vs trampoline 是否同路」当支柱。
5. **反向契约**：即使假想有 `enqueue` 落进窗口，`PendingTurnQueue.kt:34` 的「反向先 `clear()`」会清掉积压；只有「先 enqueue 反向、后 requeueFront 旧方向」这一序会违约（把过期方向顶到队首）。第 2 步的追加序正好排除了这一序。

⇒ **「不可达」成立，但它是「唯一站点 + 单线程 + 追加序」的推论；C 的「帧不能插进排空循环」只是这三条的一个副产品**。C 在 §5 表里也确实把这条标成「由静态推论得出、未经运行时验证」，这一点我认可，不重报。

**同时给出我认为真实可达、且被 C 说成「窄竞态」的序列 S3（零帧边界）**：
- 手指抬起 → `:446 scope.launch { settle(...) }` 产生任务 **L**（此时队列里 L 在 T1 之前）；
- 同一帧/同一排空里 T1 运行：`:376` 入队点按、`:378` 通过（此刻 `turning` 仍 false）、`:379` poll、`:385` 把 T2 追加到 L **之后**；
- L 执行：`settle` 走 F7 的 `neighbor != null` 分支，无挂起地把 `turning = true`；
- T2 执行：`:386` 见 `turning == true` ⇒ `requeueFront`。
⇒ 归还分支**不需要**跨帧竞态就会被命中（拖拽甩动 + 同帧点按即触发）。容量不因此被破坏（窗口内没有 enqueue），但它说明这条路径是热路径，`:388` 注释与 C §0 的「窄竞态窗口」措辞都应降级为「由排空顺序决定的常规分支」。反向（T2 先跑、L 后置 `turning`）由 F7 的 `neighbor == null` 分支同样可达。

## 3. 我尝试过的攻击面，以及什么会真的让结论翻掉

### 3.1 会翻掉的改动（这是本轮唯一有行动价值的产出）

**任何把 `enqueue` 从「effect 起始体」挪进「已排在 T2 之前的续体」的改动，都会让窗口成立，且不依赖任何新帧。** 具体形态（当前代码都不存在，逐条按 F1 可判定）：

- 把入队搬进 `scope.launch { … }` 的重试段（`:385` 之后），或 `onDragEnd`/`onDragCancel` 的 `scope.launch { … }`（`:430`、`:446`）里顺手 `enqueue`；
- 把入队搬进 `withFrameNanos` / `delay` 之后的续体（F5 那个循环体就是这类位置的现成例子）；
- 在非主线程调用 `enqueue`（C §6.5 已点出，我从 F6/F8 确认目前不发生）。

⇒ 这些都不是「换个 Compose 版本」才会出问题，而是**下一次重构顺手就会踩到**。所以真正的加固方向不是「证明调度安全」，而是**把不变式做成可判定的**（§5）。

**可证伪预测**：若未来 PR 使 `enqueue` 出现在上述任一位置，则 `poll`→`requeueFront` 之间可插入一次入队，越容幅度 **+1**（C §3 的上界我认可，理由相同：`pumping` 单泵 + 手上只握一个方向），并且过期方向可能被顶到队首。

### 3.2 不成立的攻击（列出来以免读者以为漏查）

| 假设 | 判定 | 依据 |
|---|---|---|
| 同一帧内两次重组让两个不同 `turnRequest` 值的 effect 体并存 | 不成立 | 一个组合节点每帧至多重组一次；F2 的归零挡住重跑 |
| 帧回调 / 输入事件插进 `performTrampolineDispatch` 的同步 while 排空 | 不成立 | 同线程串行，排空期间 looper 不会取下一条消息 |
| `Snapshot.sendApplyNotifications` 把「写状态→重组→launch」变同步、从而在 T1 内联出第二次入队 | 未发生 | F8 grep 0 命中；且 `onTurnRequestConsumed()` 在入队**之前** |
| 非 UI 线程写 `turnEpoch` / `turnRequest` 触发乱序 | 未见 | F5/F8，两处生产者都在 main 的 effect 内 |
| `requeueFront` 自身就能越容 | 不成立 | 归还的是刚 poll 走的那一个，poll 后 size ≤ capacity-1 |
| 反向契约可被「先 enqueue 反向、后 requeue」违约 | 当前不可达 | §2 第 2、5 步的追加序 |

## 4. 结论成立所依赖的前提清单（任一破，结论即需重评）

| 前提 | 当前证据等级 | 破坏方式 |
|---|---|---|
| P1 `enqueue` 站点唯一、且只在 `LaunchedEffect` 起始体 | **静态确证**（F1，本轮重做） | §3.1 任一改法 |
| P2 所有 mutator 只在主线程 UI 派发器 | 静态确证（F1/F6/F8） | 后台协程代发翻页 |
| P3 组合节点每帧至多重启该 effect 一次 | **库语义推断**（未运行验证） | Recomposer 实现变化 / `sendApplyNotifications` |
| P4 `AndroidUiDispatcher` 不内联 `scope.launch`，且 `toRunTrampolined` 为 FIFO | **库字节码**（C §2 的 javap；本轮未重做） | 版本变更即失效；注意即使内联也只会**加强**安全性，真正危险的是「插到 T2 之前」 |
| P5 编译期 compose-ui 版本就是被取证的那份 | **推断**：BOM 声明 `composeBom = 2024.12.01`（对应 1.7.x），但缓存中 `androidx.compose.ui/ui-android` **只有 1.9.0** ⇒ 解析被别的依赖顶高。**本轮未跑 `:app:dependencies`**，故「1.9.0 即编译期版本」未直接证实 | 换 BOM / 加约束都可能让取证对象错位 |
| P6 真机运行时确实按上述顺序投递 | **未验证**（本机 Compose instrumentation 跑不起来，见 `docs/qa/2026-09-24-c-high-speed-test-diagnosis.md`） | — |

**因此：本轮既不认可「已由运行时证明」，也不认可「不可达」是经验事实；它是「在当前代码形状 + 当前库版本下，静态可判定的结论」。** C 报告 §5 表已如此标注，我不重复计为缺陷。

## 5. 最小可证伪测试方案（不改容量策略、不改产品代码；标注所有权）

1. **T-A｜类侧越界哨兵（JVM，属主 C 的 `PendingTurnQueueTest`）**：显式断言「`poll` 之后立刻 `requeueFront` 不可能使 size 超过 `capacity`」——即在满队列上 poll→requeue 往返 50 次，每步断言 `size <= capacity`。它不假设任何调度，只把 §2 第 5 步的算术钉进测试。零风险、可当场红。
2. **T-B｜顺序模型测试（JVM，属主 C）**：用单线程 FIFO 假调度器建模 S3（L 在 T1 前入队、T2 在 T1 后入队），断言事件序列里不存在 `enqueue` 落在 `poll` 与 `requeueFront` 之间。**必须标注为模型测试**——它证明的是「若调度如 P3/P4 则安全」，不能证明真机投递顺序。
3. **T-C｜抢占分支可达性（真机 instrumentation，属主 C 的 `PageTurnerHighSpeedTest`）**：`performTouchInput` 造「fling 松手 + 同帧点按」，断言点按不丢（队列最终排空页数正确）。**受 P6 阻塞**：SR-ENV-1 未裁定前取不到结果。
4. **T-D｜站点约束的可执行化（SEAM REQUEST，属主 C，我不动手）**：把 §3.1 变成机器可查的约束——两条候选：
   (a) 给 `PendingTurnQueue` 增加 `fun requeueFrontIfRoom(direction: Int): Boolean`（或让 `requeueFront` 返回 `Boolean`）并在满时拒收，使「归还必有空位」从调用方时序升级为**类型不变式**；
   (b) 保持类不变，加一条源码级结构守卫用例，断言 `enqueue` 调用点仍唯一且仍位于 `LaunchedEffect(turnEpoch, turnRequest)` 体内（本仓已有同类先例：`ReadingUnitDisposeWiringGuardTest`）。
   我倾向 (b) 成本更低、且**不改变抢占语义**；(a) 会改变抢占路径行为，与 C §7 的顾虑一致，建议与真机通道一起决定。
5. **T-E｜独立缺陷登记（不属本命题，属上游单槽语义；owner 非我）**：`turnRequest`（`PagedReaderControllerEffects.kt:187` 与 `:224` 两个生产者写同一个 `Int`）与 `externalTurnRequest`（`:185-189`）都是单槽。两路生产者写**同值**时 key 不变 ⇒ 后到的请求被**静默丢弃**（是「少排」不是「越容」）。这与容量结论无关，但会被误当成同一件事，登记以免后续混判。C §6.4 已提及同一问题，我确认其存在、同样不越界修改。

## 6. 仍未验证的行为（明确不宣称）

- 真机上的实际投递顺序（P3/P4/P6 全部为静态/字节码依据）；
- 连点是否「一次一页」、停手后不再自翻；
- 在播动画不被打断、每轮动画使用换帧后的新帧；
- 拖拽 + 点按混用时 `dragging` / `turning` 翻转的真实观感（§2 的 S3 我只在代码上判定可达）；
- 反向请求在真实手指节奏下是否「像丢了一页」。

## 7. 边界与所有权

- 本轮**只写本报告**一份新文件。未修改 `PageTurner.kt`、`PendingTurnQueue.kt`、`PagedReaderControllerEffects.kt`、`PendingTurnQueueTest.kt`、`PageTurnerHighSpeedTest.kt`，也未修改窗口 B / C 的任何报告（包括被挑战的 `2026-09-24-page-turn-queue-boundary-review.md`）。
- 未运行 Gradle、未跑全量门禁、未接 ADB / 手机 / 模拟器、未触碰用户书库与设备设置。
- 未执行 `reset / checkout / clean / stash / stage / commit / push`；HEAD 仍 `5aed5939600e491843e3c44453921bd6273a233c`，staged 0。
- 未改队列容量策略（`PENDING_TURN_CAPACITY = 3` 保持原样）。
- 文中不含真实书名、私有目录名或日志原文。

## 8. 快照时效（写完前最后复验）

窗口 C 在我读源之后又写入过：`PendingTurnQueueTest.kt` mtime 12:57:05、`PendingTurnQueue.kt` 12:59:15、
C 的报告 13:00:48（`PageTurner.kt` 仍 02:57:35 未动）。我在 13:0x 重测本报告的全部承重事实：
`PendingTurnQueue.kt` sha256 仍为 `4e5c87a1765c2be83dc3d2d507a421347dd8098c02193a4d610d502414b5b3ff`（内容未变，与 F9 一致）、
`capacity` 口径未变、`pendingTurns.` 在 main 仍只有 F1 那 4 处、`PendingTurnQueueTest` 仍 12 个 `@Test`。
⇒ 本报告结论不受该次写入影响；但若 C 后续改动 `enqueue` 站点或队列类型签名，§2 的论证需重跑（重点看 §3.1）。

