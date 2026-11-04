# S05-④「导入完成后查看」当前证据核对（窗口 B，2026-09-27）

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 分支：`codex/recover-worktree-20260920`
- HEAD：`git rev-parse HEAD` 实际输出 40 位十六进制，前缀 `5aed5939`（与任务账本记载一致；按本仓 §8.7 教训，不做手抄字符串逐字符比对）
- 性质：**只读核对**。未运行 Gradle / ADB / MuMu，未改任何源码、测试或共享文档；本文件是本轮唯一写入。
- 核对对象：`ShelfImporter.kt`、`ShelfRoute.kt`、`ShelfScreen.kt`、`ShelfModels.kt`、`ShelfImportFocusTargetTest.kt`、`ShelfImporterTest.kt`（批次用例区段），以及跨引用 grep。
- 上游报告：[`agent-next-b-s05-import-focus.md`](../../dispatches/undated/agent-next-b-s05-import-focus.md)（只作线索，结论以当前源码为准）。

## 0. 结论速览

| 问题 | 当前结论 |
|---|---|
| Q1 上一批未查看的书还会被当成本批「查看」目标吗 | **不会**（目标唯一来源是本批 `batchSuccesses`，按 batchId 核对） |
| Q2 筛选排除时有无反馈、状态是否清除 | **有反馈、必清除**（Screen 回执 `reached=false` → Route 清空 + 提示） |
| Q3 测试证明到哪一层 | 纯 JVM 契约 15+3 条成立；**设备端 UI 证据仍缺** |
| Q4 最小设备验收 | 3 个场景（正例定位 / 跨批护栏 / 筛选排除反馈+无过期滚动），见 §4 |

代码与 JVM 逻辑层面 S05-④ 两条缺陷（跨批定位、筛掉静默残留）在当前工作树中**已修**；但修复的全部证据止于静态源码 + JVM 测试设计，**无任何已执行的测试结果或真机证据**，不能据此关闭 S05-④。

## 1. 当前已证实（源码位置 + 证据）

### 1.1 定位目标只来自本批成功 id（Q1 前半）

- 唯一来源：`ShelfRoute.kt:228` 在**点按那一刻同步读** `viewModel.importer.batchSuccesses.value`，经 `focusIdsForBatch(successes, importBatch.id)`（`ShelfRoute.kt:229` → 定义 `:53-56`）核对批次；`successes.batchId != importBatch.id` 时返回**空列表**，不得定位。
- 导入器侧原子发布：`ShelfImporter.kt:121-137` 定义 `ImportBatchSuccesses`（batchId 与 bookIds 绑成一个不可变对象）；`runImportBatch` 开头整体重置为新 batchId（`ShelfImporter.kt:517`），只有 `ImportOutcome.Success` 分支追加（`ShelfImporter.kt:545-549`），与 `succeeded` 计数**同一分支**——重复 / 跳过 / 失败都不写。
- 接缝可见性：`ShelfViewModel.kt:164` `val importer: ShelfImporter` 为 public，Route 可直达，无需改 ViewModel。
- 跨批次持久集合已退出定位链：`ShelfPrefs.newBookIds` 在 Route 中只喂「新」角标（`ShelfRoute.kt:195` 收集 → `:271` 传入 uiState → `ShelfScreen.kt:540` `isNew`）。全仓 `grep -rn "focusNewBookId\b"`（排除复数）**0 命中**——旧符号无残留。
- 批次换代双向安全：
  - 新批次开始 → `importBatch.id` 变化 → Route 的 snackbar effect（key 含 `importBatch.id, importBatch.isRunning`，`ShelfRoute.kt:205`）被取消，旧提示随之失效；
  - 若用户恰在换代窗口点旧提示，`focusIdsForBatch` 返回空 → 走 `:231-241` 的明确反馈（「这一批已不是最近一次导入…」），不发生任何滚动。

### 1.2 筛选排除时反馈 + 清状态（Q2）

- Screen 侧：`ShelfScreen.kt:139-158` `LaunchedEffect(focusBookIds, state.showSkeleton, state.viewMode)`——候选全不可见时 `onFocusHandled(false)`（`:153`），不再静默 `return`；滚动成功才 `onFocusHandled(true)`（`:157`）。
- Route 侧：`ShelfRoute.kt:312-319` `onFocusHandled` **无论 reached 真假都清空** `focusNewBookIds`；`!reached` 时提示「新导入的书不在当前筛选结果里，可调整筛选后再查看」。
- 骨架期防误报：`ShelfScreen.kt:141` 骨架期早退（不判定、不清理），`showSkeleton` 在 effect key 中（`:139`），翻转后重跑。
- 落库→列表流延迟防误报：`awaitFocusTarget` 有界轮询（默认 12 × 50ms ≈ 600ms，`ShelfScreen.kt:637-640`、`:658-670`），每次轮询经 `rememberUpdatedState` 读**最新** state（`:128`、`:145-148`），等满才判不可达。
- 滚动落点防错位：`shelfGridIndexOfBook`（`ShelfModels.kt:166-171`）对骨架 / 空列表 / 不在列表三种情况都返回 null，`shelfLeadingItemCount`（`:155-159`）与 `ShelfScreen.kt:454-477` 的头部 item 结构一一对应。
- effect key **刻意不含** `filtered.size`（`ShelfScreen.kt:133-136` 注释）：避免按列表长度重启 effect 清零轮询计数造成变相无限等待。

### 1.3 测试与跨引用（Q3 前半）

- `ShelfImportFocusTargetTest.kt`（241 行，15 条，纯 JVM / `runTest`）：
  - `focusImportTarget` 6 条：取第一本可见、跳过被筛的退到下一本、全被筛掉返回 null、空候选、无可见书、**绝不选候选外的可见书**（直接编码旧失败模式的回归护栏）；
  - `focusIdsForBatch` 4 条：批次一致返回 id、**批次换代返回空**（核心判别）、批次一致但列表空、从未跑过批次；
  - `awaitFocusTarget` 5 条：等到出现、**有界放弃且轮询数恰好等于上限**（可证伪防挂起护栏）、已可见只轮询 1 次、空候选 0 轮询、每次重读可见集合不吃快照。
- `ShelfImporterTest.kt`（1527 行）3 条批次用例（`:1262` / `:1285` / `:1310`）：本批成功 id == 真实落库 id 且长度 == `succeeded`；**成功 id 与批次 id 一起换代**（第二批全重复 → 列表必须为空）；同批判重不计入。
- 跨引用干净（grep，main + test）：`batchSuccesses` 仅 `ShelfImporter.kt` / `ShelfRoute.kt` / `ShelfImporterTest.kt`；`focusIdsForBatch` 仅 Route（定义+调用）与 FocusTargetTest；`focusImportTarget` / `awaitFocusTarget` 仅 Screen 与 FocusTargetTest；`ImportBatchSuccesses` 仅上述三处。androidTest 目录 `focusBookIds|focusImportTarget|awaitFocusTarget|onFocusHandled` **0 命中**——androidTest 侧签名兼容、无 UI 用例引用。

## 2. 未证实（本轮无运行证据，不得写为通过）

1. **全部验证未执行**（本棒纪律：不跑 Gradle / ADB / MuMu）。Kotlin 编译、`:app:testDebugUnitTest`（两个测试类）、`lintDebug`、androidTest 编译、Debug APK——均无本轮结果。此前账本中任何「N/N 通过」都属历史批次口径，不能沿用为当前结论。
2. 真机 / MuMu 上「查看」的端到端链路（snackbar 点按 → effect 轮询 → `animateScrollToItem` 落点 → 反馈提示）无任何 UI 证据。
3. 600ms 轮询上界（`FOCUS_VISIBILITY_ATTEMPTS=12 × 50ms`）在真实 Room→Flow 延迟下是否够用，未在任何设备测过；若落库到列表发射超过 600ms，会误报一次「不在筛选结果里」。
4. 骨架翻转与 focus 处理的先后、两条 snackbar（批次完成提示与「不在筛选结果」提示）的叠加观感，未在设备验证。
5. 变异测试未做：「若实现改回旧写法新用例会红」是基于断言的静态判断，不是执行结果。

## 3. 真实缺陷

静态核对**未发现**新的产品缺陷。两条如实登记的边界取舍（非缺陷，但验收时应知情）：

- `ShelfRoute.kt:213` `markImportBatchSummaryNotified(importBatch.id)` 在 `showSnackbar` **之前**调用：批次提示若被新导入打断（effect 取消），旧批次摘要不会重显。这是防重复提示的取舍，不在 S05-④ 判定面内，但复核时应知晓。
- focus 请求设置后若用户在骨架翻转前离开书架组合，`remember` 状态随组合销毁——请求静默消失（安全方向：无过期残留，但也无反馈）。

## 4. 最小下一步（设备验收协议，交 Codex 排期）

目标：区分「正确定位本批」与「只是按最近导入排序显示在前」。默认「最近导入」排序下新书本来就在最前，滚到顶无法区分两者，所以**必须改变排序使本批新书不落在列表首位**。

前置：真机（A 独占），同一 APK 哈希 + 设备序列号记录；书架已有若干旧书；中性书名（只写「测试 EPUB」「测试 TXT」）。

| # | 场景 | 动作 | 期望 | 截图/状态 |
|---|---|---|---|---|
| 1 | 正例定位 | 排序切到「书名」→ 导入批次②（1 本测试书）→ 完成提示出现 → 点「查看」 | 滚动落点在**该书**（位置不在顶部，证明非排序产物） | 点按前首屏（含排序标签可见）、点按后该书可见（含「新」角标） |
| 2 | 跨批护栏 | 批次②提示仍在显示时启动批次③（另一本测试书）→ 验证旧提示被替换/失效；批次③完成后点其「查看」 | 不滚到批次②的书；滚到批次③的书 | 两批各自的提示与落点；若旧提示仍可点，记录其反馈文案 |
| 3 | 筛选排除反馈 + 无过期滚动 | 施加一个排除目标书的筛选（如状态=已完成或格式筛选）→ 导入 → 点「查看」→ **不重新导入**，把筛选改回包含该书 | 出现「新导入的书不在当前筛选结果里…」提示且列表不滚；筛选变化后列表**不得**自动滚到该书（证明 focus 已清） | 反馈提示截图；筛选变化后的静止列表截图 |

判定红线：任一场景只凭「列表顶部出现了新书」不能记 PASS（与排序不可区分）；场景 3 的「筛选变化后不滚动」是「状态已清」的关键判别点。历史报告中未实际点击「查看」的记录一律不得当作本项 PASS 证据。

## 5. SEAM REQUEST

无。当前源码与测试在 S05-④ 范围内自洽，无需改动；若 §4 设备验收发现问题，届时按现象另开精确文件与边界的 SEAM REQUEST，交 Codex 分配。

---

完成后停止，等 Codex 复核。不自行领取后续任务。
