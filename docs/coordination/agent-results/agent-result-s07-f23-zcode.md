# S07-F2.3 修复与边界确认报告（Zcode）

> Agent：Zcode（模型 `deepseek v4.1Flash`）
> 日期：2026-09-20
> 恢复仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 工作分支：`codex/recover-worktree-20260920`
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 任务来源：`docs/coordination/reviews/undated/agent-review-s07-f22-qoder.md` 的 SP-2（A）、ST-1（B）、ST-2（C）
> 最终状态：**等待 Qoder/Codex 复核**

---

## 1. 消费者矩阵（任务 A 第一步）

滚动 TXT 中，**拥有选区的 ReadingUnit 被回收时会发 `ClearSelection`**（F2.2 引入）。
下表逐个审计「谁在读选区数据」以及它们能否在弹层打开期间丢失选区。

判定列含义：
- **读取方式**：打开时快照 / 保存时读实时 VM
- **模态性**：是否阻止用户触摸滚动正文
- **回收可达性**：弹层打开期间，拥有者单元是否仍可能被 dispose

| # | 入口 | 读取的字段 | 读取方式 | 模态性 | 回收可达性 | 裁决 |
|---|---|---|---|---|---|---|
| 1 | **笔记**（`ReaderNoteDialog`） | `selectedText` | **保存时读实时 VM**（`ReaderScreen.kt:571` 传入、`:577` 保存时再传一次实时值） | `AppAlertDialog`（`ReaderNoteEditorDialog.kt:53`），阻止触摸 | **可达**（见 §2） | **静态可达，摘录会丢** |
| 2 | **替换规则**（`RulesSheet`） | `selectedText` | 打开时快照进草稿（`ReaderSheetHost.kt:295` → `ReaderRulesSheet.kt:31-39` 的 `remember(initialReplaceText, …)`）；但 `remember` key 含 `initialReplaceText`，**选区被清 → key 变 → 草稿被重建为 null** | `ModalBottomSheet`（`ReaderSheetHost.kt:222`），阻止触摸 | **可达** | **静态可达，预填丢失** |
| 3 | **AI 解读**（`AiExplainSheet`） | `selectedText` | **实时读取**（`:70/:74/:79` 直接读参数，无快照）；参数来自 `ReaderSheetHost.kt:375` 的实时 `state.ui.selectedText` | `ModalBottomSheet`，阻止触摸 | **可达** | **静态可达，解读对象丢失** |
| 4 | **灵感**（`InspirationSheet`） | `selectedText`（作 `excerpt`） | **实时读取**（`ReaderSheetHost.kt:389` → `ReaderInspirationSheet.kt:70` 的 `excerpt` 参数直接用于 UI 与 `canSave`） | `ModalBottomSheet`，阻止触摸 | **可达** | **静态可达，摘录丢失** |
| 5 | AI 伴读（`AiAssistSheet`） | `selectedText`（作 `contextText`） | 实时读取，但有 `ifBlank { contentText }` 回退（`ReaderSheetHost.kt:370`） | `ModalBottomSheet` | 可达 | 降级为「用整章正文」，非数据丢失 |
| 6 | 复制 / 浏览器 / 字典 | `selectedText` | 点按瞬间读一次（`ReaderLayerBuilders.kt:365/370/377`） | 非弹层（即时动作） | 不适用 | 不可达（无窗口） |
| 7 | 高亮（`onPickColor`） | `selectedText` | 点按瞬间先快照再清（`:339`） | 非弹层 | 不适用 | 不可达 |
| 8 | 书内搜索 | `selectedText` | 先 `SetSearchQuery` 快照再 `ClearSelection`（`ReaderSelectionToolbar.kt:127-131`） | 非弹层 | 不适用 | 不可达 |
| 9 | 工具条可见性 | `selectedText.isNotBlank()` | 实时 | — | 可达 | 选区失效后工具条消失 —— **期望行为** |
| 10 | 返回键优先级 | `selectedText.isNotBlank()` | 实时 | — | 可达 | 行为一致，无数据丢失 |
| 11 | `computeLocatorJson` | 三字段 | 保存瞬间读（`ReaderProgressActions.kt:36-41`） | 非弹层 | 不适用 | 不可达 |

**结论**：**#1 笔记、#2 替换规则、#3 AI 解读、#4 灵感** 四个入口是「实时读取 + 模态 + 拥有者单元仍可被回收」的组合。

---

## 2. 风险裁决：**静态可达**（不是「需要真机确认」）

模态层只挡**触摸**，挡不住**程序化滚动**。滚动 TXT 存在一条不依赖用户触摸的自动滚动链：

```
TtsReaderSyncEffect（ReaderTtsEffect.kt:88，LaunchedEffect(tts.status, tts.currentSentenceRange)）
        ↓  朗读推进到下一句
jumpToPlainOffset(target)                    （ReaderTtsEffect.kt:122）
        ↓
scope.launch { plainListState.scrollToItem(...) }   （ReaderProgressActions.kt:113）
        ↓  程序化滚动，模态层不拦截
拥有者 ReadingUnit 滚出组合窗口 → onDispose
        ↓
latestClearSelection("", -1, -1, -1)         （ReaderContentHostPlainTextBranch.kt:367-369）
        ↓
ClearSelection → withSelectionCleared()       ← 选区被清空
```

**三个必要前提全部成立**：

1. **TTS 与选区可共存**：没有任何代码在建立选区时停止朗读 —— `SetSelectedText`（`ReaderViewModel.kt:356-362`）不碰 `showTts`；`tts.stop()` 的全部调用点（`ReaderActions.kt:157/179`、`ReaderLayerBuilders.kt:333`、`ReaderInteractionLayer.kt:318`、`ReaderProgressActions.kt:92`）都只由「用户主动关 TTS / 切章 / 开自动翻页」触发，**没有一个由选区或弹层触发**。
2. **TTS 跟读会程序化滚动列表**：`TtsReaderSyncEffect` 的 `LaunchedEffect(tts.status, tts.currentSentenceRange)` 在每句推进时调用 `jumpToPlainOffset` → `scrollToItem`。这条链**不经过触摸**，因此 `ModalBottomSheet` / `AlertDialog` 的模态拦截对它无效。
3. **弹层不会让拥有者停止被回收**：`sheet` / `noteOpen` 都不影响 `ReaderContentHost` 的 LazyColumn 组合（`ReaderContentHost.kt` 里 `sheet`/`noteOpen` 出现次数为 **0**，滚动分支拿不到这两个字段）。

**用户可见后果**（以笔记为例，`ReaderScreen.kt:571/577` 读实时值）：

- 弹层开着时选区被清 → 摘录框**当场变空**；
- 保存时 `saveReaderNote` 收到空串 → `excerpt = snapshotText.takeIf { it.isNotBlank() }`（`ReaderActions.kt:215`）落 `null`；
- 用户仍看到「已保存笔记」（`ReaderScreen.kt:580`），但**摘录静默丢失**。

这不崩溃、不写错数据，但属于任务明确要求「不得以真机未验证为理由忽略」的**数据/摘录丢失路径**。

---

## 3. 实际修改文件

| 文件 | 类型 | 改动性质 |
|---|---|---|
| `ui/screen/reader/ReaderContentHostPlainTextBranch.kt` | M | **仅 C 项**：内联归属判断改为复用 `readingUnitOwnsSelection(...)`（+7 行） |
| `src/test/.../ui/screen/reader/ReadingUnitDisposeWiringGuardTest.kt` | **新增** | **B 项**：5 条源码级接线守卫 |
| `docs/coordination/agent-results/agent-result-s07-f23-zcode.md` | **新增** | 本报告 |

**未修改**：`ReaderInteractionLayer.kt`、`ReaderLayerBuilders.kt`、`ReaderSheetHost.kt`、`ReaderScaffold.kt`、`ReaderViewModel.kt`（后五个虽在授权清单内，但本轮 A 项不实施、B/C 项不需要它们）。已用 patch 段逐字节比对证明未触碰（§8.3）。

---

## 4. 采用方案与未采用方案

### 4.1 A 项：**未实施，改为 SEAM REQUEST**

**为什么不在授权范围内硬修**：正确的修法需要在「选区数据」与「弹层是否正在消费」之间建立联系，而这两个事实分处不同层：

| 需要的改动 | 所需文件 | 是否在授权清单 |
|---|---|---|
| 消费态标记（如 `sheet`/`noteOpen` 派生） | `ReaderScreenState.kt`（`:35 val sheet`、`:39 val noteOpen`） | ❌ **不在** |
| 把该标记传进滚动分支 | `ReaderContentHost.kt`（`ReaderContentHostState` / `ReaderContentSourceState`） | ❌ **不在** |
| 笔记弹层改为快照式读取 | `ReaderScreen.kt`（`:571` 传入、`:577` 保存） | ❌ **不在** |

任务明确规定：「如果安全修复需要新增跨 ViewModel 的 pending-clear 状态、数据库字段或大范围弹层重构，**停止并写 SEAM REQUEST**」。本修复需要新增一个跨层传递的消费态字段并改动 3 个未授权文件，**命中该停止条件**。

**已评估但未采用的方案**（供 Codex 决策时参考，我不自行选择）：

| 方案 | 做法 | 代价 | 我未采用的原因 |
|---|---|---|---|
| **S1 消费门** | `if (readingUnitShouldClearOnDispose(owns) && !selectionIsBeingConsumed)`，后者由 `sheet in {RULES, AI_EXPLAIN, INSPIRATION} \|\| noteOpen` 推出 | 需把该标记传进滚动分支（`ReaderContentHost.kt`） | **越界**（未授权文件） |
| **S2 打开时快照** | 笔记弹层改为打开时快照 `selectedText` | 只改 `ReaderScreen.kt`（未授权）；且需为 #2/#3/#4 各改一处才一致 | **越界**；且不是「一处修全部」 |
| **S3 打开弹层时停 TTS** | 消费入口触发 `tts.stop()` | 改动产品行为（用户可能想边听边记）；且只堵住 TTS 这一条滚动来源 | 行为变更需产品决定 |
| **S4 延迟清理直到消费者结束** | pending-clear 状态机 | 任务点名禁止（「不允许简单写成 `consumerOpen -> 永远不清`」） | **明确禁止** |

**特别提示给 Codex**：`ReaderSheetHost.kt:297-300` 的 RULES 返回按钮**依赖** `selectedText` 非空来发 `ClearSelection`。若采用「弹层打开期间全局延迟清理」这类方案，会与这条既有逻辑相互作用，需一并设计。

### 4.2 B 项：**已实施**（源码守卫）

新增 `ReadingUnitDisposeWiringGuardTest`，5 条断言，覆盖任务点名的四种回归：

| 回归 | 对应断言 |
|---|---|
| DisposableEffect 被删除 | `the dispose effect still exists and is keyed on the unit identity` |
| `rememberUpdatedState` 改回快照 `remember` | `ownership is read through rememberUpdatedState not a stale snapshot`（含反向 `assertFalse(… by remember(`) |
| 清除回调不再发空文本 `onSelect` | `dispose sends a blank selection signal so it converges on ClearSelection` |
| 归属判断被删除 | `dispose still consults the ownership decision` |

每条先 `assertTrue(branchSource.exists())` 做 **fail-loud** 路径检查（沿用本仓 `StatsFileStructureTest` / `ProgrammaticJumpWiringGuardTest` 的既有先例）。

### 4.3 C 项：**已实施**

`ReaderContentHostPlainTextBranch.kt:426` 的内联判断改为复用同一个被断言过的纯函数：

```kotlin
// 改前
} else if (selectionState.selectedRangeStart in content.sourceStartAbs until content.sourceEndAbs) {
// 改后
} else if (
    readingUnitOwnsSelection(
        selectedRangeStart = selectionState.selectedRangeStart,
        unitSourceStartAbs = content.sourceStartAbs,
        unitSourceEndAbs = content.sourceEndAbs,
    )
) {
```

行为完全等价（`in x until y` ≡ `x >= y_start && x < y_end`），但归属规则现在**只有一份实现**。新增守卫 `both ownership checks share the single tested predicate` 同时钉住「不再有手写内联判断」与「该函数至少 3 次出现（1 定义 + 2 调用）」。

---

## 5. RED / GREEN 与不可达证明

### 5.1 B 项：四种回归的变异验证（全部被捕获）

| 变异 | 改动 | 结果 |
|---|---|---|
| **1. 删除 DisposableEffect** | 整块删除 | ✅ **3 failed**（`the dispose effect still exists…`、`dispose still consults…`、`dispose sends a blank selection signal…`） |
| **2. `rememberUpdatedState` → `remember`** | `val ownsCurrentSelection by remember(` | ✅ **编译期失败**（Kotlin 拒绝在非 `State` 上 `by remember`）—— 比测试更强 |
| **3. 清除回调参数写错** | `latestClearSelection("", -1, -1, 0)` | ✅ **1 failed**（`dispose sends a blank selection signal…`） |
| **4. C 项回退** | 内联判断恢复 | ✅ **1 failed**（`both ownership checks share the single tested predicate`） |

四个变异全部回滚，`diff` 确认工作树文件与变异前**逐字节相同**（§8.4）。

> 说明：变异 2 之所以在编译期就失败，是因为 `rememberUpdatedState` 返回 `State<T>`（可 `by` 委托），而 `remember` 返回裸值。这是比断言更强的保护 —— 任务担心的「改回捕获首次快照的 `remember`」在本仓根本编译不过；守卫的 `assertFalse` 是对「换一种等价写法绕过」的额外防线。

### 5.2 A 项：**不可达性无法证明 —— 因为它是可达的**

任务允许「若静态证明不可达，补能锁定不可达前提的测试」。我按 §2 的链路逐环核对，结论是**可达**：

- 唯一能挡住它的机制是「模态层阻止正文滚动」，而 TTS 跟读走 `scrollToItem` 程序化滚动，**不经过触摸输入**，模态层对它无效；
- 三个前提（TTS 与选区共存、TTS 会程序化滚动、弹层不影响 LazyColumn 组合）都在源码中成立且已附行号。

因此**不能**写「不可达证明」；按任务要求，改为 §4.1 的 SEAM REQUEST。

### 5.3 GREEN

```
$ ./gradlew.bat :app:testDebugUnitTest --rerun-tasks --tests <14 类>
退出码：0；BUILD SUCCESSFUL in 2m 55s；30 actionable tasks: 30 executed
```

---

## 6. 命令、退出码和 XML 统计

| 测试类（XML `testsuite/@name` 原值） | 归属 | tests | failures | errors | skipped |
|---|---|---:|---:|---:|---:|
| `...ui.screen.reader.ReadingUnitDisposeWiringGuardTest` | **F2.3 新增（B）** | 5 | 0 | 0 | 0 |
| `...ui.screen.reader.ReadingUnitSelectionOwnershipTest` | F2.2 回归 | 10 | 0 | 0 | 0 |
| `...feature.reader.pager.ProgrammaticJumpInvalidationTest` | F2.2 回归 | 4 | 0 | 0 | 0 |
| `...feature.reader.pager.ProgrammaticJumpWiringGuardTest` | F2.2 回归 | 3 | 0 | 0 | 0 |
| `...feature.reader.pager.PagedReaderSelectionInvalidationPolicyTest` | F2.1 回归 | 12 | 0 | 0 | 0 |
| `...feature.reader.pager.ReaderTapTurnPlanTest` | F2.1 回归 | 5 | 0 | 0 | 0 |
| `...ui.screen.reader.TxtScrollChapterTargetTest` | F2.1 回归 | 7 | 0 | 0 | 0 |
| `...ui.viewmodel.ReaderViewModelTest` | F2 回归 | 72 | 0 | 0 | 0 |
| `...ui.screen.reader.ReaderContentHostSelectionCallbacksTest` | F2 回归 | 4 | 0 | 0 | 0 |
| `...feature.reader.pager.ReaderTapActionTest` | F2 回归 | 9 | 0 | 0 | 0 |
| `...ui.screen.reader.ReaderCopyNoticeTest` | **F1 哨兵** | 3 | 0 | 0 | 0 |
| `...data.settings.SelectionActionsTest` | **F1 哨兵** | 24 | 0 | 0 | 0 |
| `...data.settings.SelectionActionStoreTest` | **F1 哨兵** | 13 | 0 | 0 | 0 |
| `...ui.screen.reader.ReaderSelectionToolbarModelTest` | **F1 哨兵** | 9 | 0 | 0 | 0 |
| **合计** | | **180** | **0** | **0** | **0** |

14 个请求的类 = 14 个 XML 文件，无缺类（零匹配防护）。

### 6.1 任务「必须建立的证据」逐条对照

| # | 要求 | 状态 |
|---:|---|---|
| 1 | 每个选区消费者的「快照/实时读取/模态性/回收可达性」矩阵 | ✅ §1（11 个入口） |
| 2 | ownership 两处判断共用同一纯函数 | ✅ §4.3 + 守卫 `both ownership checks share the single tested predicate` |
| 3 | 拥有者 dispose 会清；非拥有者不会清 | ✅ F2.2 的 `disposing the owning unit requests a clear` / `disposing a non owning unit must not clear` 仍在场并通过 |
| 4 | 源码守卫能捕获删除 DisposableEffect、删除 rememberUpdatedState 和错误接线 | ✅ §5.1（四个变异全被捕获） |
| 5 | 若发现消费竞态并修复：快照/不留旧选区/不影响普通关闭 | ⚠️ **发现竞态但未修复 → SEAM REQUEST**（§4.1）；不得以「真机未验证」为由忽略，已在 §2 给出可达链 |
| 6 | F1、F2、F2.1、F2.2 核心回归继续通过 | ✅ §6（180 项全绿） |

### 6.2 编译检查

| 命令 | 退出码 | 结果 |
|---|---|---|
| `./gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` | 0 | `BUILD SUCCESSFUL` |

---

## 7. 收尾检查

```
$ git diff --check -- <本轮实际修改的 2 个文件>
退出码：0

$ git status --short
（本轮 3 个文件见 §3；其余 300+ 项为工作区既有用户资产，未触碰）
```

---

## 8. before/after patch 哈希与精确增量

```
git diff origin/main -- <6 个拥有的源文件> > /tmp/s07f23-before.patch   # 1748 行
git diff origin/main -- <6 个拥有的源文件> > /tmp/s07f23-after.patch    # 1755 行
```

| 快照 | SHA-256 |
|---|---|
| before | `93ca2b523f581d5dcccb23f530c1bf4039f53d99a54c8e0103bcade9ae760d63` |
| after | `ae589cbccfccb73f9b6ed65466b2bea19ea5ff80bac12906c3831f63ffa25fdf` |

**after 快照真实性**：重新执行 `git diff` 到 `/tmp/v23.patch`，与 `after.patch` `diff -q` 结果为 **IDENTICAL** → 无变异残留。

### 8.1 逐文件增量

| 文件 | before | after | delta |
|---|---:|---:|---:|
| `ReaderContentHostPlainTextBranch.kt` | 273 | 280 | **+7** |
| `ReaderInteractionLayer.kt` | 267 | 267 | **0** |
| `ReaderLayerBuilders.kt` | 197 | 197 | **0** |
| `ReaderSheetHost.kt` | 129 | 129 | **0** |
| `ReaderScaffold.kt` | 207 | 207 | **0** |
| `ReaderViewModel.kt` | 675 | 675 | **0** |

### 8.2 增量内容

`diff -u before after` 的全部有效变更只有一处（C 项）：

```diff
-                    } else if (selectionState.selectedRangeStart in content.sourceStartAbs until content.sourceEndAbs) {
+                    } else if (
+                        // S07-F2.3：与 dispose 清除共用同一个半开区间判据，避免同一规则两份实现。
+                        readingUnitOwnsSelection(
+                            selectedRangeStart = selectionState.selectedRangeStart,
+                            unitSourceStartAbs = content.sourceStartAbs,
+                            unitSourceEndAbs = content.sourceEndAbs,
+                        )
+                    ) {
```

**无夹带**：delta 新增行对 `markdown|epub|copyText|DORMANT|edit|aiExplain|replace|tts|quote|han|trad`
的**大小写敏感**匹配数为 **0**。

### 8.3 五个授权但未使用的文件逐字节未动

`ReaderInteractionLayer.kt`、`ReaderLayerBuilders.kt`、`ReaderSheetHost.kt`、`ReaderScaffold.kt`、`ReaderViewModel.kt`
的 patch 段做等值比较，全部 `IDENTICAL`。

### 8.4 变异已回滚

四个变异全部回滚，`diff` 确认 `ReaderContentHostPlainTextBranch.kt` 与变异前逐字节相同；`readingUnitOwnsSelection(` 计数为 3（1 定义 + 2 调用）。

---

## 9. 未验证项

- **A 项的可达性未在真机复现**。我给出的是**静态可达链**（TTS 程序化滚动 → 拥有者 dispose → 清选区 → 摘录丢失），每一环都有行号；但「TTS 跟读速度与用户打开笔记的时序是否实际撞上」没有设备证据。按任务要求，这**不**构成忽略该路径的理由 —— 修复建议已作为 SEAM REQUEST 提交。
- **未挂载 Compose 运行期**。`onDispose` 是否真在 LazyColumn 回收时触发、`rememberUpdatedState` 是否确实读到最新值、`content.sourceStartAbs` 作为 dispose key 是否唯一标识单元，仍只有静态证据。
- **B 项守卫依赖 JVM 单测工作目录**（app 模块根）。已用 `assertTrue(exists())` fail-loud 保护；若将来改 `workingDir`，需同步调整。
- **`rememberUpdatedState` → `remember` 的变异在编译期失败**，因此该守卫的 `assertFalse` 分支未被运行时证据覆盖（但编译期拦截本身更强）。
- **选区跨单元**：判定假定「起点所在单元即选区宿主」；滚动 TXT 目前不存在跨单元选区，该前提未写成断言。
- **真机一切**：菜单栏保持、滚出单元后工具条消失、相邻单元不误清 —— 归 WorkBuddy。
- **Markdown / 旧 EPUB 未处理**（见 §11）。
- **`selectedSourceLength` 半清错值未独立复现**（F2 遗留）。

---

## 10. 合规声明

- **是否越界**：**否**。只改了 1 个授权源文件（C 项）+ 1 个新增测试 + 本报告。A 项经评估需要 3 个**未授权**文件，已按任务要求停止并写 SEAM REQUEST，未自行扩张。
- **是否修改禁止项**：**否**。未改 Markdown/EPUB 渲染、持久高亮、F1 的 edit/复制、分页翻页时序、Gradle、Room、桌面端、正式报告、产品盘点、Obsidian。
- **是否 stage / commit / push**：**否**。`git diff --cached --stat` 为空，未创建分支或 worktree。
- **是否 reset / checkout / clean / stash / 全仓格式化**：**否**。`git stash list` 为空；工作区既有 300+ 项修改原样保留。
- **是否使用 MuMu / 真实手机**：**否**。
- **变异测试是否残留**：**否**（§8.4）。
- **未处理 Markdown / EPUB**：**是，明确未处理**。状态未变。
- **SEAM REQUEST**：**有**（§11）。

---

## 11. SEAM REQUEST

**主题**：滚动 TXT 的 dispose 清除对「弹层正在消费选区」无门，导致笔记摘录 / 替换预填 / AI 解读对象 / 灵感摘录可能静默丢失。

**裁决**：**静态可达**（§2，含三个前提的逐环行号）。

**为什么需要越界**：正确修法需在「选区数据」与「消费态」之间建立联系，涉及 3 个**不在本轮授权清单**的文件：

1. `ui/screen/reader/ReaderScreenState.kt` —— 消费态标记的来源（`sheet` / `noteOpen` 已在 VM 状态里）；
2. `ui/screen/reader/ReaderContentHost.kt` —— 把该标记传进滚动分支（`ReaderContentHostState` / `ReaderContentSourceState`）；
3. `ui/screen/reader/ReaderScreen.kt` —— 笔记弹层改为打开时快照（若选 S2 方案）。

**建议方案（供 Codex 决策，我未实施）**：

- **S1 消费门**（推荐）：`if (readingUnitShouldClearOnDispose(ownsCurrentSelection) && !selectionIsBeingConsumed)`，其中 `selectionIsBeingConsumed = noteOpen || sheet in {RULES, AI_EXPLAIN, INSPIRATION}`。覆盖全部四个入口，且**不改变**「弹层关闭后选区仍会被正常清除」的既有语义（避免任务点名禁止的「consumerOpen → 永远不清」）。
- **S2 快照**：各弹层打开时快照 `selectedText`。更彻底但要改 4 处，且 RULES 的 `remember(initialReplaceText, …)` 需同步调整。

**决策时必须一并考虑**：`ReaderSheetHost.kt:297-300` 的 RULES 返回按钮依赖 `selectedText` 非空来发 `ClearSelection`；任何延迟清理方案都要与这条既有逻辑对齐。

**建议的验收证据**（供后续切片）：S1 可纯函数化（`selectionClearAllowedOnDispose(owns, consumerOpen)`）+ 源码守卫，与 B 项同构，成本约 6 条用例。

---

## 12. 最终状态

**等待 Qoder/Codex 复核。**

本报告不宣称 S07 已完成或可发布。A 项以 SEAM REQUEST 形式上交，未自行越界修复；
真机验收仍归 WorkBuddy。S08 及看板队列其余切片保持 `HOLD`。

**未领取下一任务。**
