# S07-F2.2 独立复核（Qoder）

- 复核 Agent：Qoder（模型 `qwen3.8Flash`）
- 复核对象：S07-F2.2 —— A 程序化跳页只清选区不动 chrome；B 滚动 ReadingUnit 回收只清真正属于它的选区
- 工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`；分支 `codex/recover-worktree-20260920`
- 已读输入：`AGENTS.md`、`docs/coordination/agent-results/agent-result-s07-f22-zcode.md`、我上一轮的 `docs/coordination/reviews/undated/agent-review-s07-f21-qoder.md`、3 个改动源文件与 3 个测试文件的当前内容、Zcode 的 before/after patch（我独立复算哈希、独立切分逐文件段并做了 diff）
- 前置确认：Zcode 结果文件 mtime `15:14`；其测试首次写入 `15:03`、源码写入 `15:04–15:09`；before 快照 mtime `15:01` 早于全部写入，after 快照 `15:12` 晚于全部写入
- 标注：**[事实]** 我读到的代码或跑过的命令；**[推断]** 静态推导；**[未验证]** 需真机或 Compose 运行期

---

## Standards

### 12 项检查逐条判定

| # | 检查项 | 判定 | 一手证据 |
|---:|---|---|---|
| 1 | jumpRequest 跨页是否发 `ClearSelection` 而不是 `PageTurn` | **成立** | `PagedReaderControllerEffects.kt:30-32 programmaticJumpInvalidation(onSelect) = { onSelect("", -1, -1) }`；`PagedTxtReaderHost.kt:310 onSelectionInvalidated = programmaticJumpInvalidation(onSelect)`；空文本沿 F2 接线落到 `ReaderLayerBuilders.kt:122-126` → `ReaderAction.ClearSelection` → `ReaderViewModel.kt:364` → `withSelectionCleared()`。整条链不含 `ReaderAction.PageTurn`，也不进 `readerChromeReducer` |
| 2 | 同页跳转是否不清选区、不改 chrome | **成立** | `applyProgrammaticJump:49-61` 仅在 `readerJumpClearsSelection` 为 true 时调用 `invalidateSelection`；三种 false 情形（同页内、目标恰为页末、无可见页）各自有用例（`PagedReaderSelectionInvalidationPolicyTest` 的 12 项在场） |
| 3 | 手势 / 硬件键 / 自动翻页是否仍走 `PageTurn` | **成立，行为未变** | `PagedTxtReaderHost.kt:110` 形参保留、`:338 onGesturePageTurn = onGesturePageTurn` 原样透传给分页表面；`grep onGesturePageTurn` 在本文件只剩这两处 + 参数声明。VM 的 `PageTurn` 分支（`ReaderViewModel.kt:327-335`）一字未改，F2 回归 `a committed page turn clears the whole selection` 仍断言 `controlsVisible == false`（`ReaderViewModelTest.kt:1151`）→ 真实翻页照旧收起菜单栏 |
| 4 | 程序化跳转是否保持 sheet / searchQuery / chrome / 新阅读位置 | **成立（VM 层）** | 新通道只产生 `ClearSelection`，而 `withSelectionCleared():87-93` 只写那 5 个选区字段，不碰 `sheet` / `searchQuery` / `controlsVisible`；跳转本身的位置由 `controller.open(j)` 完成，未被本轮改动。**[未验证]** 弹层与底栏在真机上的实际保持情况 |
| 5 | ReadingUnit ownership 是否半开区间 | **成立** | `ReaderContentHostPlainTextBranch.kt:224-231`：`selectedRangeStart >= start && selectedRangeStart < end`，外加 `end > start` 挡空/反区间；用例 `a unit does not own a selection that starts at its exclusive end` 与 `an empty or inverted unit range owns nothing` 在场 |
| 6 | 是否使用最新状态、避免 `DisposableEffect` 捕获陈旧 ownership | **代码成立，但无断言保护** | `:357-364` 归属值经 `rememberUpdatedState(...)`、回调经 `val latestClearSelection by rememberUpdatedState(callbacks.onSelect)`，`onDispose` 读的是这两个委托值 —— 写法正确 **[事实]**；但没有任何用例能发现它被改回 `remember(...)` 快照或直接捕获 `callbacks.onSelect`，见 ST-1 **[推断]** |
| 7 | 相邻可见单元 / 无选区单元 dispose 是否不误清 | **成立（纯函数层）** | `readingUnitShouldClearOnDispose:239` 只在 owns 为 true 时请求清除；`no selection means no unit owns it`（`selectedRangeStart == -1`）、`a unit does not own a selection from a neighbouring unit`、`only the owning unit is picked out of a set of composed units`、`an adjacent unit never claims the selection across the whole set`、`disposing a non owning unit must not clear` 五条共同钉住"三个同时组合的单元里只有 B 认领"。**[未验证]** LazyColumn 真实回收时序 |
| 8 | 拥有选区的单元被回收时是否恰好清一次 | **成立（结构层）** | 每个单元注册自己的 `DisposableEffect(content.sourceStartAbs)`，owner 判定互斥（半开区间不重叠）→ 同一次回收只有一个单元会发通知；`ClearSelection` 本身幂等，重复触发不会造成第二次状态变化。**[推断]** 依据：区间互斥 + reducer 幂等；未做运行期断言 |
| 9 | 是否存在多单元竞争清空同一 VM 状态 | **不存在** | 见 7、8：`[start, end)` 互斥使 A/B 不可能同时认领；边界点（A 的 end == B 的 start）由 `:231` 的严格小于归给 B，若改成 `<=` 才会双认领 —— 而这正是他们变异 B-1 试过的方向，且被 `a unit does not own a selection that starts at its exclusive end` 捕获（其 §4.2 记录该变异下 1 failed）。**[事实]** 该用例在场，我也读了断言体 |
| 10 | F1 / F2 / F2.1 回归 | **成立** | 我自己重跑：F1 四类 49、F2 三类 85、F2.1 三类 24，全绿（见 Verification） |
| 11 | before/after patch 能否证明未改其它 hunk | **能（三片里最强）** | 见 Scope integrity：两条 SHA-256 与申报值逐字符相同；after 与我现场 `git diff` 逐字节相同；逐文件段增量 +19 / +2 / +60 / **0**；`ReaderLayerBuilders.kt` 段 `IDENTICAL` |
| 12 | 是否夹带 Markdown / EPUB / 持久高亮 / edit / 复制 / AI / 替换净化 | **无夹带** | delta 新增行对 `markdown\|epub\|Epub\|EPUB\|copyText\|DORMANT\|aiExplain\|QuoteCard\|HanConvert\|Tts\|TTS` 的大小写敏感匹配只有 1 处 `TTS`，我把它连上下文一起打出来核对：那行是 `* 滚动 TXT 单条 ReadingUnit 的纯渲染（TTS 高亮 + 选区 + BasicTextField）`，行首在源码 diff 里是**上下文行标记（空格）**，不是新增行 —— 与 Zcode §7.2 的说明一致，属 hunk 边界移动导致的重复出现。 **[事实]** |

### ST-1 [P2] 接线门禁只补了 A 侧，B 侧仍是"接了但没门"——而这正是本轮自己证明会漏的那类缺口

本轮最值得记的一件事其实在 Zcode 自己的 §4.3：它做变异 A-1（把宿主接线改回 `onSelectionInvalidated = onGesturePageTurn`）后发现**纯函数测试仍然全绿**，于是补了 `ProgrammaticJumpWiringGuardTest` 三条源码守卫。这个判断是对的，而且正是我在 F1 复核 ST-3 提的同一类问题。

但同样的缺口对**缺陷 B 仍然开着**。B 的实际修复活在 `ReaderContentHostPlainTextBranch.kt:357-371` 那段 dispose 块里，而 10 条新用例测的是 `readingUnitOwnsSelection` / `readingUnitShouldClearOnDispose` 两个纯函数。以下三种改法都不会让任何用例变红：

1. 把整个 `DisposableEffect(content.sourceStartAbs) { ... }` 块删掉 → B 缺陷原样复活（单元回收后 VM 仍留着旧选区与工具条），测试全绿；
2. 把 `rememberUpdatedState` 换成 `remember`（或直接捕获 `callbacks.onSelect`）→ `onDispose` 读到陈旧的 owns 快照/旧回调 → 漏清或清错对象，测试全绿；
3. 把 `latestClearSelection("", -1, -1, -1)` 的参数写错（例如漏掉第 4 个参数改成走别的重载）→ 测试全绿。

也就是说：A 侧现在有"语义 + 接线"两层保护，B 侧只有"语义"一层。**[事实]**（我读了守卫用例只引用 `PagedTxtReaderHost.kt` 一个文件，`grep` 全测试目录无 `ReaderContentHostPlainTextBranch.kt` 的源码守卫。）

最小修正建议（与 A 侧对称，成本约 3 条用例）：在 `ProgrammaticJumpWiringGuardTest` 里加一个 `plainTextBranchSource = File("src/main/java/.../ReaderContentHostPlainTextBranch.kt")`，断言其中同时包含

```
"DisposableEffect(content.sourceStartAbs)"
"readingUnitShouldClearOnDispose(ownsCurrentSelection)"
"latestClearSelection(\"\", -1, -1, -1)"
"rememberUpdatedState(callbacks.onSelect)"
```

以及 `val ownsCurrentSelection by rememberUpdatedState(` （而不是 `remember(`）—— 第 4 条专门挡住上面第 2 种回归，因为它是这段代码里唯一"看起来等价、实际会漏清"的写法。

### ST-2 [P2] 同一条归属规则现在有两份实现，未来口径一改就分叉

新纯函数 `readingUnitOwnsSelection`（`:224-231`，半开区间、有测试）与同文件既有的内联判断 `:426`：

```kotlin
} else if (selectionState.selectedRangeStart in content.sourceStartAbs until content.sourceEndAbs) {
    callbacks.onSelect("", -1, -1, -1)
}
```

两者表达的是同一件事（"这个单元是否拥有 VM 里的选区"），但只有前者被测试覆盖，后者仍是手写的 `in start until end`。目前口径一致，所以不是 bug；一旦有人调整边界（例如改成"选区终点也要在单元内"），只会改到其中一处，而 JVM 门禁只会对 `readingUnitOwnsSelection` 的那 10 条用例负责，内联那处静默漂移。 **[推断]**

最小修正建议：`:426` 改为 `else if (readingUnitOwnsSelection(selectionState.selectedRangeStart, content.sourceStartAbs, content.sourceEndAbs))`。这是一行替换，行为完全等价（`in x until y` ≡ `x >= y_start && x < y_end`），却让两处共享同一个被断言过的判据。

### ST-3 [P3] `readingUnitShouldClearOnDispose(owns) = owns` 是恒等函数

它的价值仅在于给"相邻单元不得误清"一个可断言的名字。可以接受，但如果采纳 ST-1 的源码守卫（直接断言 dispose 块的写法），这个包装就可以内联掉，避免读者误以为 dispose 还有额外条件。 **[事实]**

### ST-4 [P3] 源码守卫依赖 JVM 单测的工作目录

`ProgrammaticJumpWiringGuardTest.kt:22-23` 用相对路径 `File("src/main/java/...")`，前提是 Gradle 把单测工作目录设为 app 模块根。它至少在第一断言里 `assertTrue(hostSource.exists())` 做了 fail-loud 保护（若工作目录变化会立刻红，而不是静默通过），这一点写得对。 **[事实]** 登记为约束：若将来给单测配置别的 `workingDir`，需要一起改这里。

---

## Spec

### SP-1 用户可见变化（我核对过的）

1. 底栏进度条拖动跳页（同章跨页）、目录同章跳转、搜索命中/高亮回源同章跳转：旧选区与色板行完整失效，**顶栏/底栏不再被顺手收起**，弹层与搜索词保持不动。 **[事实（VM/接线层）]** / **[未验证（真机）]**
2. 目标仍在当前页、或排版未就绪没有可见页：什么都不清，用户刚选的字保留。 **[事实]**
3. 真实翻页手势（点按区、拖动、硬件键、自动翻页）：仍然收起菜单栏（行为与 F2/F2.1 完全一致），并由 `the gesture channel is still wired for real page turns` 与 F2 回归共同保护。 **[事实]**
4. 大 TXT 滚动：选中某单元后把该单元滚出组合窗口，工具条与 VM 选区会一并失效（修复前会滞留一个"屏幕上已不存在"的选区，此时保存高亮/笔记会落到已经不显示的区间）。 **[事实（纯函数+结构层）]** / **[未验证（Compose 运行期）]**
5. 相邻单元不会因为自己"不包含选区"而在回收时把真选区清掉。 **[事实]**

### SP-2 新出现的边界风险（本轮引入，触达性未证明）

dispose 清除是**无条件于弹层状态**的：如果拥有选区的单元在"需要消费选区的弹层"打开期间被回收，VM 里的 `selectedText` 会被清空，而弹层读的是**实时状态**：

- 笔记：`ReaderScreen.kt:267` 取 `screenState.selectedText` → `:570-571` 传给 `ReaderNoteDialog(selectedText = selectedText…)` → `:576-577` 保存时仍传实时 `selectedText`。因此弹层开着时选区被 dispose 清掉，摘录框会**当场变空**，保存后 `excerpt` 落 `null`（`ReaderActions.kt:215` 的 `takeIf { it.isNotBlank() }` 兜底住了，不会崩、也不会写出错误摘录），但用户会看到"笔记没有原文摘录"且仍被提示"已保存笔记"。 **[事实（代码链）]**
- 规则（替换）：`ReaderSheetHost.kt:290-304` 用 `initialReplaceText = selectedText` 预填草稿，若草稿只在打开时取一次则不受影响；`RuleEditorDraft` 是否持续跟随实时状态我没有逐行读完。 **[未验证]**

**[推断]** 触发需要"弹层打开 + 底层 LazyColumn 在该单元上发生回收/身份替换"同时成立；我无法在不运行 Compose 的情况下证明它可达（也不排除它根本不会发生）。所以我不把它判成本轮缺陷，但它是一个**新引入的风险面**，而 F2.2 的披露表 §8 没有列它。

最小缓解建议（任选其一，都不需要动判定逻辑）：

1. dispose 清除加一个消费门：`if (readingUnitShouldClearOnDispose(ownsCurrentSelection) && !selectionIsBeingConsumed)`，后者由 `noteOpen || sheet in {RULES, AI_EXPLAIN, INSPIRATION, DICTIONARY}` 推出（这些字段宿主层都拿得到）；
2. 或在打开弹层的那一刻把 `selectedText` 快照进弹层自己的状态（笔记弹层当前完全依赖实时 VM，改一处即可）。

### SP-3 明确不得当作已通过

- **Compose 运行期**：`onDispose` 是否真在 LazyColumn 回收时触发、`rememberUpdatedState` 是否真的读到最新 owns、`content.sourceStartAbs` 作为 dispose key 是否唯一标识单元 —— 全部只有静态证据（Zcode §8 第 2、3 条自己列了）。
- **选区跨单元**：本轮判定假定"起点所在单元即选区宿主"；滚动 TXT 目前不存在跨单元选区，但这条依赖未写成断言。
- **真机一切**：菜单栏是否真的不再收起、滚出单元后工具条是否真的消失、相邻单元不误清 —— 归 WorkBuddy。
- **Markdown 滚动无选区入口、旧 EPUB 整块点选、两者不重渲染持久高亮**：一字未动，状态未变。
- **小屏 5 槽拥挤、字体放大、触摸目标、TalkBack 手柄语义与焦点顺序**：未验证。
- **`selectedSourceLength` 半清错值**：F2 遗留，仍未独立复现。

### SP-4 我自己额外做的一项前置核实（不来自 Zcode 的论证）

B 的判定读的是 VM 的 `selectedRangeStart`。我担心的是另一件事：如果滚动 TXT 路径实际写的是 `selectedGlobalOffset`，那么 `selectedRangeStart` 会恒为 `-1`，`readingUnitOwnsSelection` 恒 false，**整个 dispose 修复会变成一个永不误触发的空操作，而 10 条纯函数用例照样全绿**。核对结果：`ReaderContentHostPlainTextBranch.kt:420-425` 调用 `callbacks.onSelect(text, -1, localDisplayToGlobalSource(start), localDisplayToGlobalSource(end))`，而 `ReaderContentHost.kt:145` 的签名是 `onSelect(text, globalOffset, rangeStart, sourceEnd)` → 第 3 个实参确实落到 `rangeStart`，再经 `ReaderLayerBuilders.kt:121` 原序写入 `SetSelectedText(text, rangeStart, globalOffset, sourceEnd)`。**[事实]** 判定读对了字段，B 不是空修复。（这也是 ST-1 所指缺口的一个具体例子：这个正确性目前只能靠人读代码确认，没有任何用例能挡住参数换位。）

---

## Verification

### 我亲自执行的命令

```bash
cd android && ./gradlew --console=plain :app:testDebugUnitTest --rerun \
 --tests "...feature.reader.pager.ProgrammaticJumpInvalidationTest" \
 --tests "...feature.reader.pager.ProgrammaticJumpWiringGuardTest" \
 --tests "...ui.screen.reader.ReadingUnitSelectionOwnershipTest" \
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

- 退出码：**0**；`BUILD SUCCESSFUL in 37s`
- `> Task :app:testDebugUnitTest` 真实执行（`--rerun`）
- `:app:compileDebugKotlin`、`:app:compileDebugAndroidTestKotlin` 为 **UP-TO-DATE** —— 含义是"当前源码内容此前已被成功编译"（Zcode §5.3 编译过、§4.5 全量 `--rerun-tasks` 执行过 30 个任务）。本轮未产生新的编译动作，如实记为"编译面成立但非本轮重跑"。**[事实]**

### XML 结果（取自我自己这次运行）

| 测试类（XML `testsuite/@name` 原值） | 归属 | tests | skipped | failures | errors |
|---|---|---:|---:|---:|---:|
| `feature.reader.pager.ProgrammaticJumpInvalidationTest` | F2.2 新增 | 4 | 0 | 0 | 0 |
| `feature.reader.pager.ProgrammaticJumpWiringGuardTest` | F2.2 新增 | 3 | 0 | 0 | 0 |
| `ui.screen.reader.ReadingUnitSelectionOwnershipTest` | F2.2 新增 | 10 | 0 | 0 | 0 |
| `feature.reader.pager.PagedReaderSelectionInvalidationPolicyTest` | F2.1 回归 | 12 | 0 | 0 | 0 |
| `feature.reader.pager.ReaderTapTurnPlanTest` | F2.1 回归 | 5 | 0 | 0 | 0 |
| `ui.screen.reader.TxtScrollChapterTargetTest` | F2.1 回归 | 7 | 0 | 0 | 0 |
| `ui.viewmodel.ReaderViewModelTest` | F2 回归 | 72 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderContentHostSelectionCallbacksTest` | F2 回归 | 4 | 0 | 0 | 0 |
| `feature.reader.pager.ReaderTapActionTest` | F2 回归 | 9 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderCopyNoticeTest` | F1 哨兵 | 3 | 0 | 0 | 0 |
| `data.settings.SelectionActionsTest` | F1 哨兵 | 24 | 0 | 0 | 0 |
| `data.settings.SelectionActionStoreTest` | F1 哨兵 | 13 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionToolbarModelTest` | F1 哨兵 | 9 | 0 | 0 | 0 |
| **合计** | | **175** | **0** | **0** | **0** |

- 请求 13 个类 = 13 个 XML，无缺类（零匹配防护）。
- 总数与 Zcode §6.1 的 **175** 逐类一致（F2.2 17 项、F2.1 24 项、F2 85 项、F1 49 项）。 **[事实]**
- 17 条 F2.2 新增用例名全部在 XML `testcase/@name` 中确认在场（含 `disposing a non owning unit must not clear`、`only the owning unit is picked out of a set of composed units`、`the host never routes programmatic invalidation through the gesture channel`、`the gesture channel is still wired for real page turns`）。
- 源码守卫的相对路径在我这次运行里同样成立（3 条全绿）→ 它不是"永远通过的空守卫"。

### RED / 变异证据

我没有重复变异（任务禁止修改产品源码，且 `--rerun-tasks` 之外的改动都不允许）。替代验证两条：

1. after 快照与我现场 `git diff` 逐字节相同 → 工作树里没有变异残留（若有，两者会分叉）。 **[事实]**
2. 我读了 B 的断言体：`ownership` 用例同时钉住 true 与 false 两侧（含 `exclusive end`、`inverted range`、`neighbouring unit`），dispose 用例两条分别断言"拥有者请求清除"和"非拥有者不得清除" —— 恒真断言不可能同时约束两个方向。 **[事实（读断言体）]**

Zcode §4.2 的 A-1 结果（纯函数测试对回归全绿）我认为是可信且有价值的自我披露：它同时说明为什么 ST-1 值得补。

### 工作区完整性

```
git diff --cached --name-only   # 0 个文件（未 stage）
git stash list                  # 空
git status --porcelain -uall    # 我开工 344 → 收工见下（含本报告）
```

未修改任何产品源码/测试/正式报告/盘点/Obsidian；未 `reset`/`checkout`/`clean`/`stash`/`commit`/`push`；未运行全量 JVM、Lint、APK 或设备测试；未使用 `--rerun-tasks`。Gradle 只写 `android/app/build`、`android/.gradle`（`android/.gitignore:2-3` 已忽略）。

### 本报告写出后补记（实测）

```
git status --porcelain -uall    # 344 → 345，唯一新增是本报告
git status --porcelain -uall | cut -c4- 与 15:16 比对：
TOUCHED_BY_ME docs/coordination/reviews/undated/agent-review-s07-f22-qoder.md
```

---

## Scope integrity

### 独立复算结果（不依赖 Zcode 的自陈）

```
sha256(/tmp/s07f22-before.patch) = 58fc542f9fe010ea57c9fdc0bfa32271710e73d42ced4cdc775a70cafd2ef843
sha256(/tmp/s07f22-after.patch)  = 4566fe8552e426dbd33892a3a8ad580605536a2cca92e2312cfce09a5affc5b1
```

与报告 §7 的申报值**逐字符相同**；before 680 行、after 761 行也与申报一致。并且：

1. **after 快照真实**：我现场重跑 `git diff origin/main -- <4 授权文件>` 得 761 行，与 `after.patch` `diff -q` 相同。
2. **before 快照早于改动**：mtime `15:01`；测试首写 `15:03`、源码首写 `15:04`；`14:37–15:01` 之间仓库无文件被修改（mtime 扫描只见到 14:40 的 Codex 侧文档）。
3. **逐文件段增量**（我自己按 `diff --git` 切段统计）：

| 文件 | before 段 | after 段 | delta | 段内容 |
|---|---:|---:|---:|---|
| `PagedReaderControllerEffects.kt` | 110 | 129 | **+19** | CHANGED |
| `PagedTxtReaderHost.kt` | 158 | 160 | **+2** | CHANGED |
| `ReaderContentHostPlainTextBranch.kt` | 212 | 272 | **+60** | CHANGED |
| `ReaderLayerBuilders.kt` | 196 | 196 | **0** | **IDENTICAL** |

   与 §7.1 完全一致（我的段行数各少 1 行是切段口径差异，**增量值相同**）。
4. `ReaderLayerBuilders.kt` 段逐字节相同 —— 该文件同时承载 F1 的复制修复与 F2 的 `onSelect → ClearSelection` 接线，这条保护了两轮成果；本轮 A 修复正是**依赖**它而不改它。
5. **无夹带**：delta 新增行对 Markdown/EPUB/copyText/DORMANT/edit/aiExplain/QuoteCard/HanConvert/Tts/TTS 的匹配数 = 1，且该条经我逐字核对是源码 diff 的上下文行（TTS 出现在未改动的 KDoc 里）。真实新增内容只有三类：`programmaticJumpInvalidation` + KDoc 更新；宿主一行接线 + 2 行注释；`ReaderContentHostPlainTextBranch` 的 import + 两个纯函数 + dispose 块。
6. **五字段契约未被触碰**：`ReaderViewModel.kt` mtime 停在 `10:37`（F2 窗口），本轮三个源文件都在 `15:04–15:09`；F2 的 72 项回归（含 `assertSelectionFullyCleared`）仍全绿。 **[事实]**

### 唯一残留不确定性（与前两轮同源）

"before 是 15:01 生成的"依赖 mtime 可信。本轮的交叉约束仍然互相印证：after == 现树 + before 中 `programmaticJumpInvalidation` 与 `readingUnitOwnsSelection` 出现次数为 0 + `14:37→15:01` 空窗。**[推断]** 我上两轮提的"把 before 的 SHA-256 写进任务书（Zcode 无法事后编辑之处）"这一加固建议本轮仍未被采纳，特此再记一次。

### 我这一侧的边界自守

未读写 Zcode 的任何文件；未修改产品源码、测试、正式审查报告、产品盘点或 Obsidian；未 stage / commit / push；未创建分支或 worktree；未触碰设备。

---

## Verdict

# PASS

F2.2 的两个目标都成立，且证明链完整：

- **A**：失效通道由 `onGesturePageTurn` 改为 `programmaticJumpInvalidation(onSelect)`，只产生一个空文本 `onSelect`，沿 F2 既有接线收敛到 `ClearSelection` → 只走 `withSelectionCleared()`，不再进 `readerChromeReducer`。我核对到手势/硬件键/自动翻页三条路径的 `onGesturePageTurn` 透传与 VM 的 `PageTurn` 分支均一字未改，所以"程序化跳转不收起菜单栏、真实翻页仍收起"现在是有意区分而不是偶然。ST-1 关闭。
- **B**：归属判定用半开 source 区间、空/反区间一律不认领、多单元整组只挑一个、非拥有者 dispose 不清；字段口径我独立核对正确（见 SP-4，这是本轮我最担心的一项）；本地 `selection` 撤除仍复用既有 `selectionCleared` 回流，未建立第二份字段清单。
- 回归：F1 49、F2 85、F2.1 24、F2.2 17 = **175 项全绿**；范围污染为 0（SHA-256 + 段级 IDENTICAL + 增量语义三类，均可复算）。

### 必须在 S07 收口前登记的跟进项

1. **ST-1 [P2]**：给 B 侧补与 A 侧对称的接线守卫（含"`rememberUpdatedState` 不得退回快照式 `remember`"这一条）。当前三种改法都能让 dispose 修复静默失效而测试全绿 —— 这个缺口是 Zcode 本轮自己在 §4.3 证明过的类型，只是只补了一半。
2. **ST-2 [P2]**：`ReaderContentHostPlainTextBranch.kt:426` 的内联归属判断应改调 `readingUnitOwnsSelection(...)`，避免同一规则两份实现。
3. **SP-2 [P2]**：dispose 清除对"弹层正在消费选区"的状态无门，笔记摘录可能静默丢失（保存不崩但 `excerpt = null` 且仍提示"已保存笔记"）；触达性未证明，需要一次真机确认或加消费门。

### PASS 的边界（不代表以下内容通过）

1. 这只代表 **S07 选区状态清理的这两条收尾**，证据层级是 **JVM 纯函数 + 源码级接线守卫**；A 侧有守卫、B 侧没有（ST-1）。
2. **不代表 Markdown 滚动选区、旧 EPUB 整块点选、持久高亮重渲染已通过** —— 正式报告 Spec P1 第 4 项 / P2 状态未变。
3. **不代表小屏 5 槽拥挤、字体放大、触摸目标、TalkBack 手柄语义与焦点顺序已通过**。
4. **不代表真实设备上"跳页保留菜单栏""滚出单元清除工具条"已生效**：`onDispose` 触发时机、`rememberUpdatedState` 读值、`sourceStartAbs` 作 key 的唯一性均需 WorkBuddy 真机验收。
5. **不代表 `edit` 接线（S08）、复制失败真实触发、`selectedSourceLength` 错值、跨页选择能力已解决**。
6. 不代表整个 S07 可以发布，也不构成任何"切片完成 / 可以发布"的结论。

**未领取下一任务。** S08 及看板队列其余切片保持 `HOLD`，等待 Codex 主任务复核与放行。
