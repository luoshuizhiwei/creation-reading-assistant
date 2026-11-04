# 窗口 C — S06-⑤/⑥ 动画期间连续点按被吞：修复实现报告

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`｜分支 `codex/recover-worktree-20260920`｜HEAD `5aed5939600e491843e3c44453921bd6273a233c`（本轮未改变）
- 执行时间：2026-09-23，收尾 22:29（两个被改文件的 mtime 见 §5）
- 独占文件：`android/app/src/main/java/com/creationreadingassistant/feature/reader/pager/PageTurner.kt`（原为 ` M` 脏文件，在其既有改动之上追加）与 `android/app/src/androidTest/java/com/creationreadingassistant/feature/reader/pager/PageTurnerHighSpeedTest.kt`（原为 `??` 未跟踪，保留其 3 个既有用例，只追加）
- 未运行 Gradle / ADB / 真机（按要求）。未 stage/commit/push/checkout/reset/clean。未进入原始仓库。
- 输入已读：`AGENTS.md`、`docs/coordination/decisions/2026-09/2026-09-22-current-task-ledger.md`（S06 行 50）、`docs/coordination/reviews/undated/agent-stage-b-core-audit.md`（S06-⑤ 行 28、S06-⑥ 行 29）

## 1. 旧行为的代码证据（修复前，静态确认）

改前工作树（与 HEAD 同构）：

- `settle` 开头的静默丢弃闸门：HEAD `PageTurner.kt:225` / 改前工作树 `:261` —— `if (turning || direction == 0) return`，动画在播时**直接返回，不留任何痕迹**。
- 点按入口：HEAD `:286` 的 `LaunchedEffect(turnRequest)` 先 `onTurnRequestConsumed()` 把请求槽归零，再 `scope.launch { settle(...) }`。⇒ 第二次点按落到上一条那个 `return` 上：既不排队、也不生效，而且**请求已被消费掉**，外层再也看不到它。
- 边界分支同样带 `if (!turning)` 闸门（改前 `:352`）⇒ 邻页缺失时并发点按也被吞。
- `git show HEAD:…PageTurner.kt | grep -ci "pending\|queue"` = **0**：全文件没有任何排队概念（与窗口 D 审计一致）。
- 上游通道 `PagedReaderControllerEffects.kt:185-189` 是「方向值 + 消费归零」的单槽，`PagedReaderPageSurface.kt:552-553` 把它接到 PageTurner。⇒ 只要消费发生在动画之前，后到的点按就彻底消失。

## 2. 确定的交互语义（先声明，再实现）

- **待处理上限 = 3**（`PENDING_TURN_CAPACITY`，`PageTurner.kt:888`）。一次爆发最多「1 个在播动画 + 3 个排队」= 4 页；第 5 次及以后的点按被**合并丢弃**（丢最新的，绝不改动已在播的那一帧动画）。选 3 而非 1：上限 1 会让「三连点」仍丢一次（仍是无声丢失）；选更大则停手后机器还在自翻页，失控时间线性变长。
- **反方向点按 = 作废尚未开始的积压，只保留这次反向意图**（`:357-363`）。不是取消、也不是相加：用户最新方向优先，不先把过期的同向页翻完再回头。已经**在播**的那一次永远完整落位，绝不打断（打断会撕开 `scene`/位图轮换，正是历史闪屏与 tap 死锁的根因）。
- 同方向重复点按各自入队 ⇒ **一次点按推进一页**（在上限内）。
- **拖拽不入队**（`onDragEnd` 仍直接 `settle`）：拖拽天然有 `dragTotal`/`velocity` 现场，排队语义（"松手后稍后再翻"）与跟手手感冲突，且本轮用户报告只针对点按。改后拖拽结束时补一次 `turnEpoch++`，因此**点按队列不会被拖拽永久压住**。
- 已知残余边界（**不属本片**）：同一帧内的两次点按在上游单槽 `externalTurnRequest` 处就会被合并（值 1→1 不构成新 key）。本修复覆盖的是动画窗口（70–250ms）内的连点，即 S06-⑤ 描述的场景。

## 3. 精确改动（全部在 PageTurner.kt）

| 位置 | 改动 |
|---|---|
| `:119-123` | 新增 `pendingTurns = remember { ArrayDeque<Int>() }`、`turnGate = remember { TurnGate() }`、`turnEpoch` 排空计数器 |
| `:331-347` | 从原 `LaunchedEffect` 内联代码**原样抽出** `suspend fun turnWithoutNeighborSnapshot(direction)`（邻页缺失语义不变） |
| `:349-364` | 新增 `fun enqueueTurnRequest(direction)`：0 方向丢弃 / 反方向清空积压 / 满 3 丢弃最新 / 否则入队 |
| `:366-374` | 说明为什么不能在单个协程里循环排空（见下"设计取舍"） |
| `:380` | `LaunchedEffect(normalizedEffect)` 的 R2 重置里加 `pendingTurns.clear()` |
| `:391-427` | `LaunchedEffect(turnRequest)` → `LaunchedEffect(turnEpoch, turnRequest)`：**无条件消费并入队**，随后在 `!pumping && !turning && !dragging` 时取队首，交给 `scope.launch` 跑一次 settle，`finally` 里递增 `turnEpoch` 重新装配下一轮 |
| `:441`、`:450-455`、`:459`、`:474` | 手势侧：`turnGate.dragging` 在 onDragStart/End/Cancel 置位；onDragEnd 与 onDragCancel 之后 `turnEpoch++` 补排空 |
| `:885-900` | 新增 `private const val PENDING_TURN_CAPACITY = 3` 与 `private class TurnGate { var pumping; var dragging }` |

**设计取舍（我自己推翻的第一版）**：最初写成「一个协程里 `while (pendingTurns.isNotEmpty()) settle(...)`」。这是错的 —— 局部 `settle` 捕获的是它被创建那次重组的 `currentFrame/previousFrame/nextFrame`，第一次提交后外层已换帧，同一协程继续循环会用**旧帧**冻结 `TurnScene`，第 2..N 次动画播的是过期内容（页号对、画面错）。因此改成「一次只排空一个 + 用 `turnEpoch` 让 effect 在换帧后的新闭包里重新装配」，并把理由写进 `:366-374` 注释。`pumping` 在出队的同一非挂起时刻置位，防同帧多次装配并发启动两个动画。

**没有改动**：`settle` 本体一行未动（其 `if (turning …) return` 作为防御保留，但现在不是唯一的丢弃防线）；`onTurnRequestConsumed` 的「消费即归零」契约不变；`PagedReaderPageSurface` / `PagedReaderControllerEffects` / 阅读进度 / 书签 / 搜索 / 导航 / 公共组件均未触碰 ⇒ **无需 SEAM REQUEST**（`PagedReaderPageSurface.kt:282-289` 也消费 turnRequest，但只在 `pageTurnRenderer == OUTER_REVEAL` 分支，与 PageTurner 驱动的滑动/覆盖模式互斥，实测读码确认不冲突）。

## 4. 能证伪的测试（`PageTurnerHighSpeedTest.kt` 追加 5 个 @Test，原 3 个像素用例保留）

夹具 `TurnHarness`/`setPageTurner`/`tap`/`drainThenSnapshot`：manual clock 下按帧注入点按，排空后**在主线程取快照、在测试线程断言**（避免断言写进 `runOnIdle` 却因块未执行而假绿；快照为 null 时直接判测试自身失效）。断言面 = **最终页号 + 当前页槽渲染的帧身份 + 外层翻页回调次数**，没有一处只断言「画面没有空白」。

| 用例 | 断言 | 在旧实现上如何变红（证伪对象） |
|---|---|---|
| `singleTapAdvancesExactlyOnePage` | page=1、next 调用=1、静止后当前页身份=1 | 防「排队导致同一次点按翻两页」的过冲回归 |
| `threeRapidTapsDuringAnimationAdvanceThreePages` | page=3、next=3、身份序列含 0/1/2 且末位=3 | **S06-⑤ 本体**：旧实现只翻 1 页 ⇒ page=1 直接红。身份序列还排除「页号对但每轮都播同一份过期快照」 |
| `oppositeTapReplacesUnstartedBacklog` | next、next、prev ⇒ page 回到 0、next=1、prev=1 | 若把反方向实现成「先翻完积压」⇒ next=2 且 page=1 会红；实现成「取消」⇒ prev=0 会红 |
| `burstBeyondCapacityIsBounded` | 6 连点 ⇒ page 恰为 4（=1+3），再推 60 帧页号不变 | 无上限队列（page>4）与「停手后还在自己翻」会红；上限=1 的实现（page=2）也会红 |
| `tapsAtLastPageStillConsumeRequestsWithoutStalling` | lastPage=1 时 3 次点按 ⇒ page=1、next 回调=3 | 锁住边界语义（邻页缺失仍交给外层一次）并证伪「边界请求把队列卡死」 |

## 5. 静态检查结果（本轮唯一能做的检查）

- 两个文件分隔符平衡：`PageTurner.kt` braces/parens/brackets 差值均为 0（899 行）；测试文件同样为 0（341 行）。
- 无悬空引用：`grep pumpTurnRequests|PendingTurn` 在生产文件为 0 命中；测试文件无 `drainAndAssert` 残留、不引用生产侧私有的 `PENDING_TURN_CAPACITY`（只在注释里提及）。
- 新符号全部有使用点：`turnWithoutNeighborSnapshot`(`:418`)、`enqueueTurnRequest`(`:397`)、`PENDING_TURN_CAPACITY`(`:361`)、`TurnGate`(`:121`)。
- 用到的 stdlib API 均为 `kotlin.collections.ArrayDeque` 成员：`lastOrNull/addLast/addFirst/removeFirstOrNull/first/isNotEmpty`。
- **写入范围核对（准确表述）**：本轮**我**只写了两个文件 —— `PageTurner.kt`（mtime 2026-09-23 22:19:22）与 `PageTurnerHighSpeedTest.kt`（22:27:17），外加本报告。HEAD 与分支未变。
  同期工作区**另有其他窗口的新增/修改**（与本片无关、我未触碰，登记给总指挥做集成参考）：`feature/reader/EpubRepository.kt`、`feature/sync/JsonBridge.kt` 转为已修改，并新增 `LocalExportRoomRollbackTest`、`SearchIndexBookRefreshCoordinator(.kt/+Test)`、`SearchIndexRepositoryRebuildConcurrencyTest`、`EpubRepositoryReplaceTest`、`CraSliderTest`、`ShelfImportFocusTargetTest`、`LibraryBrowserSelectionTest` 等。也就是说 `git status` 条目数增长**不能**归因于本片。
  我在 `PageTurner.kt` 既有的未提交改动之上追加，未回退、未整文件格式化。

## 6. 尚未运行的验证（不得当作已完成）

1. **未编译**：`:app:compileDebugKotlin` / `:app:compileDebugAndroidTestKotlin` 未运行 ⇒ 类型推断层面的错误（尤其 `ArrayDeque<Int>` 与 `Int?` 比较、泛型 `Frame` 推断）只是我静态推演，需共享构建确认。
2. **未跑任何测试**：新增 5 个用例全部是 `androidTest`（instrumentation），JVM 侧无对应物 —— `PageTurner` 是 Compose 组件，脱机跑不了。
3. **真机执行风险（重要）**：按本项目既有记录，这台 MIUI 测试机上 `createComposeRule` 类 instrumented 测试会**无限挂起**（`Abort background activity starts`，前台化也救不回）。⇒ 这套用例即便编译通过，也**可能无法在该设备取得运行证据**；需要总指挥安排可用设备或在 CI（模拟器不受该限制）上跑，否则 S06-⑥ 的「有牙门禁」仍然落不了地。
4. **未做真机手感验收**：连点是否真的「跟手」、上限 3 在高速连点下是否观感合适，只能等共享构建 + 真机。
5. **未验证 `none`/`fade` 效果下的排队路径**（新用例只覆盖 slide/cover）。

## 7. 剩余风险

- **R1 时序耦合**：排空依赖「settle 完成 → `turnEpoch++` → 重组 → effect 重跑」。若某条 settle 路径提前 return（例如被拖拽抢占），`finally` 仍会递增 epoch，因此不会停摆；但我在 `:407-414` 加的「抢占则把请求放回队首」路径只被拖拽场景触发，未经验证。
- **R2 上限取值是判断而非测量**：3 是权衡值。真机上若仍出现「连点像少翻一页」，应先测实际 settle 时长分布，而不是直接放大上限（放大只是延长失控窗口）。
- **R3 测试的时序敏感度**：`burstBeyondCapacityIsBounded` 假设 6 次点按全部落在第一轮 150ms 动画内（每 16ms 一次）。若设备/CI 帧预算不同，页号可能落在 2..4 之间；此时应把它降级为 `page <= 4 且停手后不增长`，而不是删断言。
- **R4 与上游单槽的边界**：同帧两次点按仍会被 `PagedReaderControllerEffects.kt:185-189` 合并 —— 本片不动别人的文件，如要彻底解决需把上游改成带序号的请求（**属于 S09/S06 交叉的独立一片**）。
- **R5 拖拽点按竞争**：拖拽本身不入队（见 §2），若用户「拖一半松手同时点一下」，点按仍可能被拖拽的 `turning` 窗口挡一次；这是既有行为，未在本片改变。
- **R6 效果切换清空队列**：`:380` 在切换翻页效果时丢弃未开始的请求。我认为「切换效果即作废积压」比「用新效果的动画重放旧请求」更可预期，但这是一处产品语义选择，需 Codex 复核。

完成后停止，等待总指挥复核与下一条任务；不领取修复、不提交、不运行共享构建。
