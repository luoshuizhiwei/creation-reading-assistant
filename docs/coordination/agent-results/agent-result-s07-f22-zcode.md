# S07-F2.2 修复执行报告（Zcode）

> Agent：Zcode（模型 `deepseek v4.1Flash`）
> 日期：2026-09-20
> 恢复仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 工作分支：`codex/recover-worktree-20260920`
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 任务来源：`docs/coordination/reviews/undated/agent-review-s07-f21-qoder.md` 的 ST-1 与 SP-2
> 本轮范围：只完成两个选区状态收尾问题（A 程序化跳页不得隐藏 chrome、B 滚动 ReadingUnit 回收不得留 VM 旧选区）
> 最终状态：**等待 Qoder/Codex 复核**

---

## 1. 实际修改文件

| 文件 | 类型 | 改动性质 |
|---|---|---|
| `feature/reader/pager/PagedReaderControllerEffects.kt` | M | 新增 `programmaticJumpInvalidation()`；更新 `onSelectionInvalidated` 的 KDoc（改为要求走选区通道） |
| `feature/reader/pager/PagedTxtReaderHost.kt` | M | 接线由 `onGesturePageTurn` 改为 `programmaticJumpInvalidation(onSelect)` |
| `ui/screen/reader/ReaderContentHostPlainTextBranch.kt` | M | 新增 `readingUnitOwnsSelection()` / `readingUnitShouldClearOnDispose()`；`ReaderUnitTextItem` 加 dispose 清除（经 `rememberUpdatedState`）；补 `rememberUpdatedState` import |
| `src/test/.../feature/reader/pager/ProgrammaticJumpInvalidationTest.kt` | **新增** | 4 用例：失效走选区通道、不发 PageTurn |
| `src/test/.../feature/reader/pager/ProgrammaticJumpWiringGuardTest.kt` | **新增** | 3 用例：**接线级**源码守卫（见 §4.3） |
| `src/test/.../ui/screen/reader/ReadingUnitSelectionOwnershipTest.kt` | **新增** | 10 用例：归属判定 + dispose 判定 + 多单元整组 |
| `docs/coordination/agent-results/agent-result-s07-f22-zcode.md` | **新增** | 本报告 |

**授权但经核实无需改动**（patch 段逐字节相同，见 §7.3）：

- `ui/screen/reader/ReaderLayerBuilders.kt` — 其 `onSelect` 空文本 → `ClearSelection` 的接线在 F2 已建立，本轮直接复用，无需改动。

**未修改**：选区五字段定义（`withSelectionCleared()` 一字未动）、Markdown、EPUB、持久高亮渲染、F1 的 edit/复制逻辑。

---

## 2. 两条修复前后的状态链

### 2.1 缺陷 A：程序化跳页顺带隐藏 chrome

**修复前**

```
底栏进度条 / 目录 / 搜索命中 / 高亮回源
        ↓
pagedJumpRequest.value = offset
        ↓
PagedReaderControllerEffects: applyProgrammaticJump(...)
        ↓ 身份确实变化
onSelectionInvalidated()  ==  onGesturePageTurn        ← PagedTxtReaderHost:308
        ↓
ReaderContentHostPagedBranch:87  onGesturePageTurn = { callbacks.onHideControls() }
        ↓
ReaderLayerBuilders:118  onHideControls = { onAction(ReaderAction.PageTurn) }
        ↓
ReaderViewModel: PageTurn 分支
        ├── readerChromeReducer(..., PageTurn)  →  controlsVisible = false   ← 隐藏顶栏/底栏
        └── withSelectionCleared()              →  五字段复位
```

问题：**程序化跳转 ≠ 翻页手势**。而跨章跳转走 `LoadChapter`，VM 里**只清选区、不动 chrome**。
于是「同章跳页收起菜单栏、跨章跳页不收起」成为用户可见的不一致（Qoder ST-1）。

**修复后**

```
applyProgrammaticJump(...)
        ↓ 身份确实变化
onSelectionInvalidated()  ==  programmaticJumpInvalidation(onSelect)   ← PagedTxtReaderHost:308
        ↓
onSelect("", -1, -1)
        ↓
ReaderContentHostPagedBranch:118 适配 → callbacks.onSelect(text="", -1, -1, absEnd)
        ↓
ReaderLayerBuilders:119  onSelect 空文本分支 → ReaderAction.ClearSelection
        ↓
ReaderViewModel: ClearSelection → withSelectionCleared()   ← 只清选区，chrome 不变
```

- 手势 / 硬件键 / 自动翻页**完全未改**：仍走 `onGesturePageTurn` → `PageTurn` → 隐藏 chrome。
- 目标仍在当前页 / 无可见页时，`applyProgrammaticJump` 的判定为 false，**不调用** `invalidateSelection`（F2.1 逻辑未动）。

**为什么复用 `onSelect` 而不是新增宿主参数**：任务明确要求「优先使用已有的 `onSelect` 回调……避免为了一个回调扩大宿主 API」。F2 已建立「空文本 `onSelect` → `ClearSelection`」的统一接线，因此宿主侧只需把失效通知接到同一个 `onSelect` 上，`PagedReaderHost` 的公开参数一个都没增加。

**是否引入重复状态真源**：否。选区字段清单仍然只有 `ReaderViewModel.withSelectionCleared()` 一处；本轮新增的 `programmaticJumpInvalidation` 只产出一个空文本 `onSelect`，不接触任何字段。

### 2.2 缺陷 B：滚动大 TXT 单元回收后残留 VM 选区

**修复前**

```
用户在单元 A（source [1000,2000)）长按选中
        ↓
onValueChange → callbacks.onSelect(text, -1, srcStart, srcEnd)
        ↓
VM: selectedText / selectedRangeStart 写入；工具条出现
        ↓
用户向下滚动 → 单元 A 离开 LazyColumn 组合窗口
        ↓
A 的本地 selection 随 remember(content.sourceStartAbs) 一起销毁
        ↓
VM 的 selectedText / selectedRangeStart **仍然存在**
        ↓
工具条继续显示；此时保存高亮/笔记 → 落到屏幕上已不存在的选区
```

**修复后**

```
ReaderUnitTextItem 组合期：
    val ownsCurrentSelection by rememberUpdatedState(
        readingUnitOwnsSelection(selectionState.selectedRangeStart, content.sourceStartAbs, content.sourceEndAbs)
    )
    val latestClearSelection by rememberUpdatedState(callbacks.onSelect)
    DisposableEffect(content.sourceStartAbs) {
        onDispose {
            if (readingUnitShouldClearOnDispose(ownsCurrentSelection)) {
                latestClearSelection("", -1, -1, -1)
            }
        }
    }
        ↓ 单元被回收 / 身份替换
onDispose → 只有「拥有者」才发空文本 onSelect → ClearSelection → withSelectionCleared()
        ↓
本地 selection 撤除仍复用已有 selectionCleared 回流（LaunchedEffect(selectionCleared)），
未建立第二份字段清理清单
```

---

## 3. 如何避免 LazyColumn 相邻单元误清

这是本缺陷最关键的设计约束。任务明确禁止「任一当前渲染单元不包含选区就清空」的实现，
因为 LazyColumn 会**同时组合**多个相邻单元。

采用的判定：**只有「拥有」VM 当前选区的那个单元被回收时才清**。

```kotlin
internal fun readingUnitOwnsSelection(
    selectedRangeStart: Int,
    unitSourceStartAbs: Int,
    unitSourceEndAbs: Int,
): Boolean =
    unitSourceEndAbs > unitSourceStartAbs &&
        selectedRangeStart >= unitSourceStartAbs &&
        selectedRangeStart < unitSourceEndAbs
```

四点保证：

1. **半开区间**：右端 `unitSourceEndAbs` **不**属于本单元。这正是「相邻单元起点」的位置 ——
   单元 A 的结束位置等于单元 B 的开始位置，若用 `<=` 则 A 与 B 会同时认领，B 被回收时会误清。
   有专门用例 `a unit does not own a selection that starts at its exclusive end` 钉住。
2. **只有起点在区间内才认领**：单元 A 不认领 B 的选区，反之亦然
   （`a unit does not own a selection from a neighbouring unit`）。
3. **无选区时无人认领**：`selectedRangeStart == -1`（`ClearSelection` 的复位值）对所有单元都是 false
   （`no selection means no unit owns it`）。
4. **多单元整组验证**：模拟 A/B/C 三个同时组合、选区在 B，断言只有 B 认领
   （`only the owning unit is picked out of a set of composed units`、`an adjacent unit never claims the selection across the whole set`）。

**为什么必须用 `rememberUpdatedState`**：`DisposableEffect` 的 `onDispose` 在单元离开组合时执行，
此时若闭包捕获的是**首次组合**的快照，就会拿着过期的「当时还没有选区」结论而漏清；
回调也可能指向旧的 book。因此归属与回调都经 `rememberUpdatedState` 读取最新值。
这一点写在代码注释里（`ReaderContentHostPlainTextBranch.kt` 的 dispose 段落）。

**是否建立第二份字段清单**：否。dispose 只发空文本 `onSelect`，字段清理仍归 `withSelectionCleared()`；
本地 `selection` 的撤除仍复用既有的 `selectionCleared` 回流。

---

## 4. RED / GREEN

### 4.1 说明：本轮两个修复都引入新 seam，直接回退源码不可行

因此 RED 证据用**变异测试**取得（把产品代码改成修复前的行为，确认测试失败）。

### 4.2 变异 A/B（纯函数层）

| 变异 | 改动 | 结果 |
|---|---|---|
| A-1 | `PagedTxtReaderHost` 接线改回 `onSelectionInvalidated = onGesturePageTurn`（即 ST-1 原缺陷） | `ProgrammaticJumpInvalidationTest` **仍全绿** ← 见 §4.3 |
| B-1 | `readingUnitOwnsSelection` 右端改 `<=`（off-by-one，相邻单元会误认领） | `ReadingUnitSelectionOwnershipTest` **1 failed**（`a unit does not own a selection that starts at its exclusive end`） |
| B-2 | `readingUnitShouldClearOnDispose` 改为恒 `true`（任何单元 dispose 都清 → 相邻误清） | `ReadingUnitSelectionOwnershipTest` **1 failed**（`disposing a non owning unit must not clear`） |

B 的两个方向（漏清 / 误清）都被捕获。

### 4.3 重要发现与补强：纯函数测试无法发现接线回归

变异 A-1 暴露了一个真实门禁缺口：**把宿主接线改回 ST-1 缺陷，纯函数测试仍然全绿**。
这正是 Qoder 在 F1 复核 ST-3 里指出的「回归保护只覆盖决策函数、不覆盖接线」的同一类问题，
而且这次它落在本轮**正在修的那个缺陷**上 —— 若只留纯函数测试，ST-1 可以无声回归。

因此我补了**接线级源码守卫**（本仓已有同类先例：`StatsFileStructureTest`、
`InspirationFileStructureTest`；JVM 单测工作目录 = app 模块根，相对路径真实可执行）：

```kotlin
class ProgrammaticJumpWiringGuardTest {
    private val hostSource = File("src/main/java/.../PagedTxtReaderHost.kt")

    @Test fun `the host wires programmatic invalidation to the selection channel`()      // 必须含 programmaticJumpInvalidation(onSelect)
    @Test fun `the host never routes programmatic invalidation through the gesture channel`() // 不得含 onSelectionInvalidated = onGesturePageTurn
    @Test fun `the gesture channel is still wired for real page turns`()                  // 反向：手势翻页仍接 onGesturePageTurn
}
```

**验证该守卫确实有效**（重新引入 ST-1 缺陷后运行）：

```
退出码：1
ProgrammaticJumpWiringGuardTest > the host never routes programmatic invalidation through the gesture channel FAILED
ProgrammaticJumpWiringGuardTest > the host wires programmatic invalidation to the selection channel FAILED
3 tests completed, 2 failed
```

即：这个缺口现在被门禁堵住了，而纯函数测试仍留在原位保证语义正确。两条一起覆盖「语义」与「接线」。

### 4.4 变异已回滚并验证

三个变异全部回滚，`diff` 确认工作树文件与变异前**逐字节相同**：

```
host restored (byte-identical)
PlainTextBranch OK
Host OK
```

### 4.5 GREEN

```
$ ./gradlew.bat :app:testDebugUnitTest --rerun-tasks --tests <13 类>
退出码：0；BUILD SUCCESSFUL in 2m 21s；30 actionable tasks: 30 executed
```

---

## 5. 命令、退出码和 XML 统计

### 5.1 一次 `--rerun-tasks` 强制执行（13 类）

| 测试类（XML `testsuite/@name` 原值） | 归属 | tests | failures | errors | skipped |
|---|---|---:|---:|---:|---:|
| `...feature.reader.pager.ProgrammaticJumpInvalidationTest` | **F2.2 新增** | 4 | 0 | 0 | 0 |
| `...feature.reader.pager.ProgrammaticJumpWiringGuardTest` | **F2.2 新增** | 3 | 0 | 0 | 0 |
| `...ui.screen.reader.ReadingUnitSelectionOwnershipTest` | **F2.2 新增** | 10 | 0 | 0 | 0 |
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
| **合计** | | **175** | **0** | **0** | **0** |

13 个请求的类 = 13 个 XML 文件，无缺类（零匹配防护）。

### 5.2 任务点「必须建立的测试」逐条对照

| # | 要求 | 对应用例（读自 XML `testcase/@name`） |
|---:|---|---|
| 1 | 程序化跨页跳转发送一次 ClearSelection 语义，不发送 PageTurn | `a programmatic jump invalidates the selection through the selection channel`（断言恰为 `[ClearSelection]`）+ `a programmatic jump never emits a page turn` |
| 2 | 程序化同页跳转不清选区，也不改变 chrome | `a jump within the current page opens but never notifies`（F2.1，`invalidations == 0`）+ `the invalidation never routes through the gesture channel`（chrome 唯一来源未被触碰） |
| 3 | 手势/硬件/自动翻页仍发送 PageTurn | `the gesture channel is still wired for real page turns` + F2 回归 `a committed page turn clears the whole selection`（断言 `controlsVisible == false`） |
| 4 | ReadingUnit ownership：起点在半开区间内为 true | `a unit owns the selection when its start falls inside the unit source range` + `a unit owns the selection when the start equals its first character` |
| 5 | 边界起点、无选区、其它单元均为 false | `a unit does not own a selection that starts at its exclusive end` + `no selection means no unit owns it` + `a unit does not own a selection from a neighbouring unit` + `an empty or inverted unit range owns nothing` |
| 6 | 只有拥有选区的单元 dispose 才请求清除 | `disposing the owning unit requests a clear` |
| 7 | 相邻单元 dispose 不得清除 | `disposing a non owning unit must not clear` + `an adjacent unit never claims the selection across the whole set` |
| 8 | F1、F2、F2.1 核心回归继续通过 | 见上表：F1 哨兵 4 类 49 项、F2 回归 3 类 85 项、F2.1 回归 3 类 24 项，全绿 |

### 5.3 编译检查

| 命令 | 退出码 | 结果 |
|---|---|---|
| `./gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` | 0 | `BUILD SUCCESSFUL` |

androidTest 一并编译，因为本轮改了 `ReaderContentHostPlainTextBranch.kt` 与 `PagedTxtReaderHost.kt`。

---

## 6. 收尾检查

```
$ git diff --check -- <本轮实际修改的 6 个文件>
退出码：0   （仅一条 CRLF 提示，无空白错误）

$ git status --short
（本轮 7 个文件见 §1；其余 300+ 项为工作区既有用户资产，未触碰）
```

---

## 7. before/after patch 哈希与精确增量

```
git diff origin/main -- <4 个拥有的源文件> > /tmp/s07f22-before.patch   # 680 行
git diff origin/main -- <4 个拥有的源文件> > /tmp/s07f22-after.patch    # 761 行
```

| 快照 | SHA-256 |
|---|---|
| before | `58fc542f9fe010ea57c9fdc0bfa32271710e73d42ced4cdc775a70cafd2ef843` |
| after | `4566fe8552e426dbd33892a3a8ad580605536a2cca92e2312cfce09a5affc5b1` |

**after 快照真实性**：重新执行一次 `git diff` 到 `/tmp/verify22.patch`，与 `after.patch`
`diff -q` 结果为 **IDENTICAL** → 快照未被事后改写，工作树里也没有 §4 的变异残留。

### 7.1 逐文件增量

| 文件 | before | after | delta |
|---|---:|---:|---:|
| `PagedReaderControllerEffects.kt` | 111 | 130 | **+19** |
| `PagedTxtReaderHost.kt` | 159 | 161 | **+2** |
| `ReaderContentHostPlainTextBranch.kt` | 213 | 273 | **+60** |
| `ReaderLayerBuilders.kt` | 197 | 197 | **0** |

### 7.2 增量内容（`diff -u before after` 的全部有效 `+` 行）

只有三类：

1. `PagedReaderControllerEffects.kt`：`programmaticJumpInvalidation()`（含 KDoc）+ `onSelectionInvalidated` 的 KDoc 更新。
2. `PagedTxtReaderHost.kt`：接线改为 `programmaticJumpInvalidation(onSelect)`（+ 2 行注释）。
3. `ReaderContentHostPlainTextBranch.kt`：`rememberUpdatedState` import + 两个纯函数（含 KDoc）+ `ReaderUnitTextItem` 内的 dispose 块（含 6 行注释）。

**无夹带**：delta 新增行对 `markdown|epub|copyText|DORMANT|edit|aiExplain|replace|tts|quote|han|trad`
的**大小写敏感**匹配数为 **0**。
（附：大小写不敏感匹配为 2，逐条核对均为误报 —— 一处是 hunk 头里的 `UnitTextFailedItem` 含子串 `edIt`，
一处是**未改动的上下文行** `滚动 TXT 单条 ReadingUnit 的纯渲染（TTS 高亮 + 选区 + BasicTextField）`。
两条都不在产品改动行上。）

### 7.3 `ReaderLayerBuilders.kt` 未改动

独立提取其 patch 段做等值比较，结果 `IDENTICAL`。该文件是 F1 复制修复与 F2 `onSelect` 接线所在文件，
这条同时保护了 F1 与 F2 的成果。

---

## 8. 未验证项

- **未做真机验证**。全部证据是 JVM 纯函数、接线级（含源码守卫）。「同章跳页后菜单栏不再收起」「滚动到下一单元后工具条消失」没有真机复现。按任务约束（不使用 MuMu、不操作真实手机）归 WorkBuddy。
- **未挂载 Compose 运行期**。`DisposableEffect` 的 `onDispose` 在 LazyColumn 真实回收时是否按预期触发、`rememberUpdatedState` 是否确实读到最新值，只有静态与纯函数证据，没有运行期断言。
- **未验证 `content.sourceStartAbs` 作为 `DisposableEffect` key 的正确性**。该 key 与既有 `remember(content.sourceStartAbs)` 一致；若同一 `sourceStartAbs` 在不同投影下对应不同单元，可能不会触发 dispose 重建 —— 静态看 `sourceStartAbs` 就是单元的全书 source 起点，唯一标识单元，但未做运行期验证。
- **未覆盖「选区跨单元」情形**。当前实现假定选区起点所属单元即选区宿主。滚动 TXT 的 `BasicTextField` 按单元独立，跨单元选区本就不存在（F2 报告已登记），但若未来支持，本判定需重审。
- **未验证 `clearSelection` 经 `callbacks.onSelect("", -1, -1, -1)` 在 EPUB/分页路径的语义**。本轮的 dispose 块只存在于滚动纯文本分支（`ReaderUnitTextItem`），不涉及分页宿主。
- **`selectedSourceLength` 半清错值未独立复现**（F2 遗留）。
- **Markdown / 旧 EPUB 未处理**（见 §10）。
- **小屏 5 槽拥挤、字体放大、触摸目标、TalkBack**：未验证，归 WorkBuddy。

---

## 9. 合规声明

- **是否越界**：**否**。改动只落在授权清单的 3 个源文件 + 3 个 JVM 测试文件 + 本报告；授权清单内 `ReaderLayerBuilders.kt` 经核实无需改动，且已逐字节证明未触碰。
- **是否修改禁止项**：**否**。未改 Markdown、EPUB、持久高亮渲染、F1 的 edit/复制逻辑、选区五字段定义、Gradle、Room、桌面端、正式报告、产品盘点、Obsidian。
- **是否 stage / commit / push**：**否**。`git diff --cached --stat` 为空，未创建分支或 worktree。
- **是否 reset / checkout / clean / stash / 全仓格式化**：**否**。`git stash list` 为空；工作区既有 300+ 项修改原样保留。
- **是否使用 MuMu / 真实手机**：**否**。
- **变异测试是否残留**：**否**。3 个变异全部回滚，`diff` 确认工作树文件与变异前逐字节相同（§4.4）。
- **未处理 Markdown / EPUB**：**是，明确未处理**。Markdown 滚动无选区入口、旧 EPUB 整块点选、两者不重渲染已存高亮，均属正式报告 Spec P1 第 4 项 / P2，本轮一字未动，状态未变。
- **SEAM REQUEST**：**无**。本切片在授权文件内闭合；缺陷 A 按要求复用已有 `onSelect`，未扩大宿主 API。

---

## 10. 最终状态

**等待 Qoder/Codex 复核。**

本报告不宣称 S07 已完成或可发布。剩余 P1（Markdown/旧 EPUB 选区能力不完整）与 P2 项未在本轮处理；
真机验收仍归 WorkBuddy。S08 及看板队列其余切片保持 `HOLD`。

**未领取下一任务。**
