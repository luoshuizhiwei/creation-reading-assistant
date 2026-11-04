# S07-F2.3 独立复核（Qoder）

- 复核 Agent：Qoder（模型 `qwen3.8Flash`）
- 复核对象：S07-F2.3 —— 选区消费竞态裁决（A）、dispose 接线守卫（B）、ownership 单一判据（C）
- 工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`；分支 `codex/recover-worktree-20260920`
- 已读输入：`AGENTS.md`、`docs/coordination/agent-results/agent-result-s07-f23-zcode.md`、我上一轮的 `agent-review-s07-f22-qoder.md`、本轮改动源文件与新增守卫的当前内容、Zcode 的 before/after patch（独立复算哈希 + 独立切段比对）
- 前置确认：Zcode 结果文件 mtime `17:02`；测试写入 `16:35`、源码写入 `16:39`；before 快照 `16:06` 早于全部写入，after 快照 `16:43` 晚于全部写入
- 标注：**[事实]** 我读到的代码或跑过的命令；**[推断]** 静态推导；**[未验证]** 需真机/Compose 运行期

我按要求没有采用 Zcode 的"可达/不可达"结论，独立走了一遍链路。**结论方向一致：静态可达**；但我在两处与它的表述不同，一处比它更严重（见 ST-1），一处是它的守卫其实有一条永不生效的断言（ST-2）。

---

## Standards

### 11 项检查逐条判定

| # | 检查项 | 判定 | 一手证据 |
|---:|---|---|---|
| 1 | 是否独立列出所有依赖选区文字/坐标的弹层与动作 | **清单可用** | 我抽查其 11 行矩阵中的 6 行并逐条对到源码：笔记 `ReaderScreen.kt:571/577`（实时）、规则 `ReaderSheetHost.kt:295 → ReaderRulesSheet.kt:31-39`、AI 解读 `ReaderSheetHost.kt:375`、复制/浏览器 `ReaderLayerBuilders.kt:365/370`（点按一次性读）、高亮 `:339`（先快照）、搜索 `ReaderSelectionToolbar.kt:127-131`（先 `SetSearchQuery` 再清）。矩阵没有漏掉我能想到的消费者；工具栏可见性(#9) 与返回键(#10) 归为"期望行为"的判断我也同意 **[事实]** |
| 2 | 每个入口是打开时快照还是执行时读实时 VM | **判定成立** | 快照类：高亮（`:339` 局部快照后再清）、搜索（先写 `searchQuery`）；实时类：笔记、AI 解读、灵感；**规则介于两者之间且比 Zcode 写得更糟** —— 见 ST-1 |
| 3 | 模态层是否阻止正文滚动；不阻止滚动时是否仍会 dispose 单元 | **确认：模态层挡不住这条链** | `TtsReaderSyncEffect`（`ui/screen/reader/tts/ReaderTtsEffect.kt:104-105`）的守卫是 `if (showTts && tts.status != "idle")` —— **不含任何 sheet/noteOpen 条件**；`:120-122` 对 TXT 调 `jumpToPlainOffset(target)`；`ReaderProgressActions.kt:110-113` 在 `pagerEngineOn == false`（滚动模式）时 `scope.launch { plainListState.scrollToItem(...) }`。`LaunchedEffect` 不在弹层的输入作用域内，模态层的触摸拦截对它无效 **[事实]**；滚动一旦发生，LazyColumn 视口移动 → 拥有选区的单元离开组合窗口 → `ReaderContentHostPlainTextBranch.kt:365-371` 的 `onDispose` 触发 **[推断：静态链完整，未运行 Compose]** |
| 4 | 若判不可达，证据是否足够 | **不适用** | Zcode 判的是"可达"，并给出了逐环行号，方向与我独立核对一致。它没有拿"UI 名称/注释"当证据 —— 三个前提都落到调用点：① `SetSelectedText`（`ReaderViewModel.kt:356-362`）不碰 `showTts`；② `tts.stop()` 全部 5 个调用点（`ReaderActions.kt:157/179`、`ReaderInteractionLayer.kt:318`、`ReaderLayerBuilders.kt:333`、`ReaderProgressActions.kt:92`）我也逐个看过，**没有一个由选区或弹层触发**；③ 滚动分支拿不到 `sheet`/`noteOpen`（该文件内这两个名字出现次数 0） |
| 5 | 若可达且已修复：消费者是否始终拿到原选区、消费完不留不可见选区 | **未修复** | 本轮 A 项**没有实施**，以 SEAM REQUEST 上交（§4.1）。所以这一项无法判定为"已安全修复" |
| 6 | ownership 折叠清除与 dispose 是否共用同一判据 | **成立** | `ReaderContentHostPlainTextBranch.kt:426-433` 折叠分支已改为调用 `readingUnitOwnsSelection(...)`；我实测该文件中 `readingUnitOwnsSelection(` 出现 **3** 次（1 定义 + 2 调用），旧的内联 `in content.sourceStartAbs until content.sourceEndAbs` 残留 **0** 次 **[事实]** |
| 7 | 接线守卫能否发现四种回归 | **三种能，一种有一半** | 逐条见 ST-2/ST-3：删 DisposableEffect ✓、删 ownership 判断 ✓、清除回调不再走空文本 onSelect ✓（锚定字面量 `latestClearSelection("", -1, -1, -1)`）、`rememberUpdatedState` 被替换 —— **正向锚定有效，负向断言永不生效**（它自己也在 §9 承认该变异编译不过） |
| 8 | 是否引入 pending 无法释放、重复清除、关闭弹层后工具栏复活等新问题 | **没有** | 本轮源码 delta 只有 7 行（C 项的表达式替换），**未新增任何状态字段**；`ReaderViewModel.kt`、`ReaderInteractionLayer.kt`、`ReaderSheetHost.kt`、`ReaderScaffold.kt`、`ReaderLayerBuilders.kt` 五个文件 patch 段逐字节 `IDENTICAL`（我独立比过）。没有 pending、没有延迟清理、因此不存在"释放不掉/工具栏复活"的新面 |
| 9 | F1/F2/F2.1/F2.2 回归 | **成立** | 我自己重跑 14 类 **180 项全绿**（见 Verification） |
| 10 | patch 是否证明未改其它 hunk | **能（本轮最干净）** | delta 的全部有效变更就是 C 那一处表达式替换；其余 5 个文件段 `IDENTICAL` |
| 11 | 是否夹带 | **无** | delta 新增行对 `markdown\|epub\|copyText\|DORMANT\|aiExplain\|QuoteCard\|HanConvert` 的大小写不敏感匹配数 = **0**（我实测）；`readingUnitOwnsSelection` 的调用参数只读 3 个已有量，没有触碰渲染/持久化 |

### ST-1 [P1] 规则弹层的后果比申报矩阵更重：不止"预填丢失"，而是**用户当前草稿态与所在标签页被回退**

Zcode §1 第 #2 行把规则入口的裁决写成"静态可达，**预填丢失**"。我读 `ReaderRulesSheet.kt:31-40`：

```kotlin
val initialReplaceDraft = remember(initialReplaceText, replacementAvailable) { ... }
var tab by remember(initialReplaceText, replacementAvailable) { mutableStateOf(if (initialReplaceDraft != null) RuleKind.REPLACE else RuleKind.TOC) }
var editing by remember(initialReplaceText, replacementAvailable) { mutableStateOf(initialReplaceDraft) }
```

`initialReplaceText` 是 `remember` 的 key，而它来自实时 `selectedText`。所以拥有选区的单元被 TTS 滚出组合 → `ClearSelection` → `initialReplaceText` 由"选中文字"变 `null` → **三个状态一起重建**：`editing` 回到 `null`（用户正在编辑的草稿态被丢弃）、`tab` 从 `REPLACE` 弹回 `TOC`。也就是说用户在这个弹层里做的事会被整体重置，而不只是"少了预填"。 **[事实：key 与状态源都在同一段代码；推断：重建后果覆盖用户已输入部分，取决于编辑写回 `editing` 的时机，我未逐行读完编辑子组件]**

这直接改变修复方案的评价：Zcode 提的 S2（笔记打开时快照）**不足以**修规则入口 —— 规则需要的是让 `remember` 不再依赖 `initialReplaceText`（或把它只当一次性 seed）。因此若采纳 S1 消费门，它同时覆盖四个入口，反而比逐处快照更省。这一点建议在 Codex 决策时一并考虑。

我同时记录：`selectionReplaceDraft`（`RuleEditorDraft.kt:112-113`）对空串返回 `null`，所以重建结果是"干净的空草稿"，不会写出半条规则 —— 数据完整性未被破坏，损失的是用户输入。

### ST-2 [P2] 守卫里那条负向断言永远不会生效；真正起作用的是正向锚定

`ReadingUnitDisposeWiringGuardTest.kt:68-71` 断言源码**不含** `"val ownsCurrentSelection by remember("`。但这种写法在 Kotlin 里根本编译不过（`by` 只能委托 `State`，`remember { Boolean }` 不是 `State`）—— Zcode 自己在 §5.1 变异 2 与 §9 第 4 条也承认这点。所以这条 `assertFalse` 检查的是一个不可能存在的字符串，属**空断言**。 **[事实]**

不过该用例仍然有效，因为 `:64-67` 的**正向锚定** `contains("val ownsCurrentSelection by rememberUpdatedState(")` 会在任何改写时失败（包括合法的等价快照写法 `val ownsCurrentSelection = remember { ... }`，因为它不再包含那串字面量）。所以"记成快照"这一回归是被挡住的，只是挡住它的不是他们展示的那条断言。

最小修正建议：把负向断言改成检查"等价但可编译"的写法，使其真正有牙齿，例如

```kotlin
assertFalse(content.contains("val ownsCurrentSelection = remember("))
assertFalse(Regex("onDispose\\s*\\{[^}]*callbacks\\.onSelect").containsMatchIn(source()))  // 直接捕获旧回调而非 latestClearSelection
```

第二条尤其有价值：把 `latestClearSelection` 换回 `callbacks.onSelect`（捕获首次组合的回调，可能指向旧 book/旧闭包）在语义上就是危险的，但当前守卫只锚定了 `rememberUpdatedState(callbacks.onSelect)` 存在，不阻止旁边再出现一个直接调用。

### ST-3 [P2] 守卫只证明"字符串在文件里出现过"，不证明它在 `onDispose` 里

`:52-55` 断言 `content.contains("readingUnitShouldClearOnDispose(ownsCurrentSelection)")`，`:81-84` 断言 `contains("latestClearSelection(\"\", -1, -1, -1)")`。如果有人把 `onDispose { ... }` 里的判断挪到组合期（例如写成 `val shouldClear = readingUnitShouldClearOnDispose(...)` 然后在别处用），或把清除调用移出 dispose 块，文件里字符串仍在 → **5 条全绿，行为已坏** **[推断]**。考虑到 F2.2/F2.3 刚做过一次"把判断挪位置"式改动（C 项就是改写表达式），这类搬移是最可能发生的回归形态。

最小修正建议：先把 `onDispose {` 到与之配对的 `}` 之间的片段截出来，再对该片段做 `contains` 断言（一个 8 行的辅助函数即可，仍然是源码级守卫，不需要 Compose 运行期）。

### ST-4 [P3] 源码级守卫是"结构资产"，不该计入语义覆盖率

这类断言在正常重构（换文件名、把块提成函数、格式化改行内空白）时会红，这是它的设计取舍。本仓已有三个同类先例，我认可保留；但建议在正式报告的测试统计里把它单列一类（"结构守卫"），别与语义用例混算，避免"180 项"被误读成"180 项行为验证"。 **[推断]**

---

## Spec

### SP-1 风险裁决（我自己的，独立于 Zcode）：**静态可达，未修复**

必要环节与我逐个核对到的位置：

1. TTS 播放中可与选区共存 —— `ReaderViewModel.kt:356-362` 的 `SetSelectedText` 不触碰 `showTts`；`tts.stop()` 的 5 个调用点无一由选区/弹层触发。**[事实]**
2. 朗读推进 → 程序化滚动 —— `ReaderTtsEffect.kt:104`（`LaunchedEffect(tts.status, tts.currentSentenceRange)`，守卫只看 `showTts && status != "idle"`）→ `:122 jumpToPlainOffset(target)` → `ReaderProgressActions.kt:113 plainListState.scrollToItem(...)`。**[事实]**
3. 滚动 → 拥有者单元 dispose → 清选区 —— `ReaderContentHostPlainTextBranch.kt:365-371`。**[事实（代码）]** / **[推断（dispose 真在视口移动时触发，未跑 Compose）]**
4. 消费者读实时值 —— 笔记 `ReaderScreen.kt:571/577`；AI 解读/灵感 `ReaderSheetHost.kt:375/389`；规则以 `initialReplaceText` 为 `remember` key（ST-1）。**[事实]**

作用域一致性值得特别记一笔：`jumpToPlainOffset` 的程序化滚动只在 `pagerEngineOn == false`（**滚动模式**）走 `scrollToItem`，而 F2.2 的 dispose 清除块也**只存在于滚动分支**。两者作用域完全重合 —— 说明这不是偶发碰撞，而是"把清理挂到单元生命周期上"这个设计的必然后果。因此**不能**用"分页模式没这个问题"来降低它的等级。 **[推断]**

后果分级（我核到的）：不崩溃、不写坏数据（`saveReaderNote` 的 `excerpt = takeIf { isNotBlank() }` 与 `selectionReplaceDraft` 的空串→null 都兜住了），但会**静默丢失用户已经在处理的内容/上下文**，且仍提示"已保存笔记"。属任务明确要求"不得以真机未验证为由忽略"的那一类。

### SP-2 流程面：Zcode 停下来是对的

它没有为了"看起来修完了"去改 3 个未授权文件，而是按任务书给出的停止条件提交 SEAM REQUEST，并留下可决策的四个方案与代价表（S1/S2/S3/S4），还点出 `ReaderSheetHost.kt:297-300` 的 RULES 返回按钮依赖 `selectedText` 非空这一相互作用。我复核过那条依赖确实存在。这部分我判定为**执行质量合格**；下面的 FAIL 只针对"缺口是否关闭"这一验收门槛。 **[事实]**

### SP-3 未验证 / 不得当作通过

- Compose 运行期未验证：`onDispose` 是否真在视口移动时触发、`scrollToItem` 是否真的把该单元推出组合窗口、`content.sourceStartAbs` 作 dispose key 的唯一性、以及"用户打开弹层时 TTS 恰好推进到下一句"的真实时序碰撞概率。
- 真机一切：菜单栏保持、滚出单元后工具条消失、相邻单元不误清。
- Markdown 滚动无选区入口、旧 EPUB 整块点选、两者不重渲染持久高亮：状态未变。
- 小屏 5 槽拥挤、字体放大、触摸目标、TalkBack：未验证。
- `selectedSourceLength` 半清错值（F2 遗留）：仍未复现。

---

## Verification

### 我亲自执行的命令

```bash
cd android && ./gradlew --console=plain :app:testDebugUnitTest --rerun \
 --tests "...ui.screen.reader.ReadingUnitDisposeWiringGuardTest" \
 --tests "...ui.screen.reader.ReadingUnitSelectionOwnershipTest" \
 --tests "...feature.reader.pager.ProgrammaticJumpInvalidationTest" \
 --tests "...feature.reader.pager.ProgrammaticJumpWiringGuardTest" \
 --tests "...feature.reader.pager.PagedReaderSelectionInvalidationPolicyTest" \
 --tests "...feature.reader.pager.ReaderTapTurnPlanTest" \
 --tests "...ui.screen.reader.TxtScrollChapterTargetTest" \
 --tests "...ui.viewmodel.ReaderViewModelTest" \
 --tests "...ui.screen.reader.ReaderContentHostSelectionCallbacksTest" \
 --tests "...feature.reader.pager.ReaderTapActionTest" \
 --tests "...ui.screen.reader.ReaderCopyNoticeTest" \
 --tests "...data.settings.SelectionActionsTest" \
 --tests "...data.settings.SelectionActionStoreTest" \
 --tests "...ui.screen.reader.ReaderSelectionToolbarModelTest" \
 :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

- 退出码：**0**；`BUILD SUCCESSFUL in 50s`；`:app:testDebugUnitTest` 真实执行（`--rerun`）
- `:app:compileDebugKotlin` / `:app:compileDebugAndroidTestKotlin` = **UP-TO-DATE**（当前内容此前已成功编译；本轮未产生新编译动作）**[事实]**

### XML 结果（我自己的运行）

| 测试类 | 归属 | tests | skipped | failures | errors |
|---|---|---:|---:|---:|---:|
| `ui.screen.reader.ReadingUnitDisposeWiringGuardTest` | **F2.3 新增** | 5 | 0 | 0 | 0 |
| `ui.screen.reader.ReadingUnitSelectionOwnershipTest` | F2.2 | 10 | 0 | 0 | 0 |
| `feature.reader.pager.ProgrammaticJumpInvalidationTest` | F2.2 | 4 | 0 | 0 | 0 |
| `feature.reader.pager.ProgrammaticJumpWiringGuardTest` | F2.2 | 3 | 0 | 0 | 0 |
| `feature.reader.pager.PagedReaderSelectionInvalidationPolicyTest` | F2.1 | 12 | 0 | 0 | 0 |
| `feature.reader.pager.ReaderTapTurnPlanTest` | F2.1 | 5 | 0 | 0 | 0 |
| `ui.screen.reader.TxtScrollChapterTargetTest` | F2.1 | 7 | 0 | 0 | 0 |
| `ui.viewmodel.ReaderViewModelTest` | F2 | 72 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderContentHostSelectionCallbacksTest` | F2 | 4 | 0 | 0 | 0 |
| `feature.reader.pager.ReaderTapActionTest` | F2 | 9 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderCopyNoticeTest` | F1 | 3 | 0 | 0 | 0 |
| `data.settings.SelectionActionsTest` | F1 | 24 | 0 | 0 | 0 |
| `data.settings.SelectionActionStoreTest` | F1 | 13 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionToolbarModelTest` | F1 | 9 | 0 | 0 | 0 |
| **合计** | | **180** | **0** | **0** | **0** |

- 14 个请求类 = 14 个 XML，无缺类；总数与 Zcode §6 的 **180** 逐类一致。 **[事实]**
- F2.3 的 5 条守卫用例名全部在 XML `testcase/@name` 中确认在场；F2.2 的 2 条 `disposing ...` 用例仍在场并通过（C 项改写没有把 dispose 语义带偏）。
- 守卫的相对路径 `src/main/java/.../ReaderContentHostPlainTextBranch.kt` 在我这次运行里同样通过（`assertTrue(exists())` fail-loud 未触发）→ 不是空守卫。

### 变异/RED 证据

我没有重复变异（禁止修改产品源码）。替代：`after.patch` 与我现场 `git diff` 逐字节相同 → 工作树无变异残留 **[事实]**；Zcode §5.1 记录的 4 个变异结果我按其断言语义复核了逻辑可能性（其中"变异 2 编译期失败"这条与我 ST-2 的判断一致：`by remember(` 形态不可能存在）。

### 工作区完整性

```
git diff --cached --name-only   # 0
git stash list                  # 空
git status --porcelain -uall    # 我开工 347 → 收工见补记
```

未修改产品源码/测试/正式报告/盘点/Obsidian；未 `reset`/`checkout`/`clean`/`stash`/`commit`/`push`；未运行全量 JVM、Lint、APK 或设备测试；未使用 `--rerun-tasks`。

### 本报告写出后补记（实测）

```
git status --porcelain -uall    # 347 → 348，唯一新增是本报告
mtime 扫描（> 17:05）：TOUCHED_BY_ME docs/coordination/reviews/undated/agent-review-s07-f23-qoder.md
staged=0  stash=0
```

---

## Scope integrity

### 独立复算结果

```
sha256(/tmp/s07f23-before.patch) = 93ca2b523f581d5dcccb23f530c1bf4039f53d99a54c8e0103bcade9ae760d63
sha256(/tmp/s07f23-after.patch)  = ae589cbccfccb73f9b6ed65466b2bea19ea5ff80bac12906c3831f63ffa25fdf
```

与报告 §8 的申报值**逐字符相同**；before 1748 行 / after 1755 行也一致。并且：

1. **after 真实**：我现场 `git diff origin/main -- <6 授权文件>` = 1755 行，与 `after.patch` `diff -q` 相同。
2. **before 早于改动**：mtime `16:06`；测试首写 `16:35`、源码首写 `16:39`；`15:25→16:06` 仅 Codex 侧文档变化（16:04）。
3. **逐文件段增量**（我自己切段统计）：

| 文件 | before | after | delta | 段内容 |
|---|---:|---:|---:|---|
| `ReaderContentHostPlainTextBranch.kt` | 272 | 279 | **+7** | CHANGED |
| `ReaderInteractionLayer.kt` | 266 | 266 | 0 | **IDENTICAL** |
| `ReaderLayerBuilders.kt` | 196 | 196 | 0 | **IDENTICAL** |
| `ReaderScaffold.kt` | 206 | 206 | 0 | **IDENTICAL** |
| `ReaderSheetHost.kt` | 128 | 128 | 0 | **IDENTICAL** |
| `ReaderViewModel.kt` | 674 | 674 | 0 | **IDENTICAL** |

4. **全部变更就一处**：`diff -u before after` 的有效改动行只有 C 项那 8 行表达式替换（旧内联判断 → `readingUnitOwnsSelection(...)` + 1 行注释）。这是三片以来最小的一次源码增量。
5. **无夹带**：夹带关键词（大小写不敏感）命中 **0**。
6. 我另外核对了 C 的等价性：`in x until y` ≡ `x >= y_start && x < y_end`（`readingUnitOwnsSelection` 另加 `end > start` 守卫，只会把原本"空/反区间"时的 false 保持 false），所以折叠分支语义未被改变 —— 这与 `ReaderContentHostSelectionCallbacksTest`(4) + `ReadingUnitSelectionOwnershipTest`(10) 全绿相互印证。 **[事实 + 推断]**

### 残留不确定性（与前几轮同源，未变）

before 快照的可信度依赖 mtime。我这轮的交叉约束：after == 现树、before 中不含 `readingUnitOwnsSelection(` 的第二次调用（C 前只有 1 定义 + 1 调用 = 2 次）、`15:25→16:06` 空窗。 **[推断]** 我前三轮提的"把 before 的 SHA-256 写进任务书"这条加固，本轮仍未采纳 —— 再记一次，不再重复展开。

### 我这一侧的边界自守

未读写 Zcode 的任何文件；未修改产品源码、测试、正式报告、盘点或 Obsidian；未 stage / commit / push；未建分支或 worktree；未触碰设备。

---

## Verdict

# FAIL

不是执行质量 FAIL，而是**验收门槛未满足**：本轮的 PASS 前置条件第一条要求"消费竞态被证明不可达，**或**已安全修复"，而事实是——我独立核对后同意 Zcode 的裁决——**竞态静态可达，且本轮未修复**（已按停止条件上交 SEAM REQUEST）。因此 S07 的"选区状态清理"现在**不能签字**：F2.2 引入的 dispose 清除带着一条已知的静默用户内容丢失路径，且其触发作用域（滚动 TXT + TTS 播放）与清除逻辑本身的作用域完全一致，不能用"只有分页才有问题"降级。

门槛逐项：

| 条件 | 状态 |
|---|---|
| 消费竞态被证明不可达，或已安全修复 | **未满足**（判为可达且未修） |
| 消费者不会得到空摘录或错误坐标 | **未满足**（笔记 `excerpt = null`、AI/灵感拿到空文本、规则草稿态被重置） |
| dispose 接线有有效门禁 | 满足（守卫能挡住删除/移走清除信号/改写 ownership；ST-2、ST-3 是加固项，不阻断） |
| ownership 只有一个判据 | 满足（实测 3 处引用、0 处内联残留） |
| 回归通过 | 满足（14 类 180 项全绿，含 F1/F2/F2.1/F2.2） |
| 没有范围污染 | 满足（源码增量 7 行、5 个文件逐字节未动、夹带 0） |

### 交给 Codex 的决策要点（我不自行选择）

1. 采纳 **S1 消费门**（`owns && !selectionIsBeingConsumed`，`selectionIsBeingConsumed = noteOpen || sheet in {RULES, AI_EXPLAIN, INSPIRATION}`）。它是唯一能一次覆盖四个入口的方案；我核到 `sheet`/`noteOpen` 已在 `ReaderScreenState.kt:35/39`，只是没被传进滚动分支，因此需要授权的是"把该标记穿过 `ReaderContentHostState`"这一条线，而不是弹层重构。
2. **不要用 S2 当作规则入口的修法**：规则的问题不是"没快照"，而是 `remember(initialReplaceText, …)` 把 `tab` 与 `editing` 一起绑到了实时选区上（ST-1）。快照式读取仍要额外稳定这两个 key。
3. S1 需与 `ReaderSheetHost.kt:297-300`（RULES 返回按钮依赖 `selectedText` 非空才发 `ClearSelection`）一起设计，避免"弹层关闭后该清的没清"。Zcode 的提示我复核成立。
4. 若 Codex 决定本周期不修，那么**更保守的选择是先撤掉 F2.2 的 dispose 自动清除**（回到"回收后残留不可见选区"），并在盘点里把这条明确记为已知限制 —— 两个选项各有代价，但"保留清除 + 无消费门"是四个组合里唯一会静默丢用户内容的那个，我不建议保持现状。
5. 建议的验收证据沿用本轮同构做法：`selectionClearAllowedOnDispose(owns, consumerOpen)` 纯函数 + 把守卫改成"截出 `onDispose` 片段再断言"（ST-3）。

### 本判定覆盖范围说明

- 这只否定"选区状态清理"的收口，不否定 F1/F2/F2.1/F2.2 各自已复核通过的结论。
- 与本轮无关但仍未完成、不得当作通过：Markdown 滚动无选区入口、旧 EPUB 整块点选、持久高亮不重渲染、小屏 5 槽与字体放大、触摸目标、TalkBack、以及一切真机运行期行为（含本 FAIL 所依据的 dispose/scrollToItem 实际触发）。

**未领取下一任务。** S08 及看板队列其余切片保持 `HOLD`，等待 Codex 主任务对 SEAM REQUEST 的授权决定与放行。
