# S07-F2.1 修复执行报告（Zcode）

> Agent：Zcode（模型 `deepseek v4.1Flash`）
> 日期：2026-09-20
> 恢复仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 工作分支：`codex/recover-worktree-20260920`
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 任务来源：`docs/coordination/reviews/undated/agent-review-s07-f2-qoder.md` 的 ST-1 / ST-2 / ST-3 / SP-2
> 本轮范围：只关闭 F2 复核发现的三个遗漏（A 同章程序化跳转、B 提前派发 PageTurn、C 测试名与回流断言）
> 最终状态：**等待 Qoder/Codex 复核**

---

## 1. 实际修改文件

用了授权清单 5 个源文件里的 **3 个**：

| 文件 | 类型 | 改动性质 |
|---|---|---|
| `feature/reader/pager/PagedReaderControllerEffects.kt` | M | 新增 `readerJumpClearsSelection()` / `applyProgrammaticJump()` / `shouldDropLocalSelection()`；`jumpRequest` 分支接入；`selectionCleared` 分支改用纯函数；新增 `onSelectionInvalidated` 参数 |
| `feature/reader/pager/PagedReaderPageSurface.kt` | M | 新增 `ReaderTapTurnPlan` + `readerTapTurnPlan()`；点按分支改为「只产生计划」，删除提前的 `onGesturePageTurn()` |
| `feature/reader/pager/PagedTxtReaderHost.kt` | M | 把 `onGesturePageTurn` 接到新的 `onSelectionInvalidated` |
| `src/test/.../feature/reader/pager/PagedReaderSelectionInvalidationPolicyTest.kt` | **新增** | 12 用例：跳转判定 + 恰好一次 + 本地选区回流 |
| `src/test/.../feature/reader/pager/ReaderTapTurnPlanTest.kt` | **新增** | 5 用例：点按计划与方向 |
| `src/test/.../ui/screen/reader/TxtScrollChapterTargetTest.kt` | M | ST-1 改名 + 补选区影响断言 |
| `docs/coordination/agent-results/agent-result-s07-f21-zcode.md` | **新增** | 本报告 |

**授权但经核实无需改动**（before/after patch 段逐字节相同，见 §7）：

- `ui/screen/reader/ReaderContentHostPagedBranch.kt`
- `ui/screen/reader/ReaderLayerBuilders.kt`

原因：`onGesturePageTurn` 在 `ReaderContentHostPagedBranch.kt:87` 已经是
`callbacks.onHideControls()` → `ReaderLayerBuilders.kt:118` 的 `ReaderAction.PageTurn`。
程序化跳转复用同一个 `onGesturePageTurn` 入口即可获得 F2 的统一语义，无需改这两个文件。

**未使用 `ReaderViewModel.kt`**。任务允许「只复用现有统一清理函数」，但本切片不需要触碰它 ——
A 复用 `PageTurn`，B 只改派发时机，C 只加纯函数断言。因此五字段契约一字未动。

---

## 2. jumpRequest 修复前后的时序

### 2.1 修复前（`PagedReaderControllerEffects.kt:110-115`）

```
进度条拖动 / 目录点击 / 回源
        ↓
jumpRequest.value = offset
        ↓
LaunchedEffect(jumpRequest.value):
    controller.open(j)          ← 页面可能已经改变
    jumpRequest.value = null
    （没有任何选区通知）
```

**缺口**：同章内跳页时 `open(j)` 会真的换页，但既不发 `PageTurn` 也不发 `ClearSelection`。
本地 `selRange` 仍在 —— 而它是**章内 display 偏移**，与新页的可见区间无关，
于是 `rectsForRange` 会在新页上画出无意义的色块与把手；此时保存高亮用的仍是旧区间。
（Qoder SP-2 的机制描述，我独立核对属实。）

### 2.2 修复后

```
jumpRequest.value = offset
        ↓
LaunchedEffect(jumpRequest.value):
    applyProgrammaticJump(
        targetOffset = j,
        currentPageRangeAbs = controller.currentPageRangeAbs,   ← 先读「当前可见页区间」
        open = controller::open,
        invalidateSelection = onSelectionInvalidated,
    )
    jumpRequest.value = null
        ↓
    判定 true  → open(j) → onSelectionInvalidated() → onGesturePageTurn()
                                                        → PageTurn → withSelectionCleared()
    判定 false → open(j) → （不通知）
```

关键点：**判定发生在 `open` 之前**，而 `open` 是确定性的 ——
它把目标落在「包含该偏移的那一页」上（`chapterIndexFor` → `globalSourceToLocalDisplay`
→ `pageIndexFor`）。所以「目标是否落在当前可见页区间内」同步决定了这次跳转会不会改变
页面身份，不必等异步排版落定。

---

## 3. 普通翻页（非 OUTER_REVEAL 点按）修复前后的时序

### 3.1 修复前（`PagedReaderPageSurface.kt:361-378`）

```
onTap → resolveReaderTapAction == PREVIOUS_PAGE / NEXT_PAGE
        ↓
非 OUTER_REVEAL 分支：
    onGesturePageTurn()        ← ① 先派发 PageTurn（会清 5 字段）—— 乐观清除
    turnRequest.intValue = d   ← ② 后登记翻页请求
        ↓
PageTurner 消费 turnRequest → 动画 → 提交页码 → onPageTurned()  ← ③ 又派发一次 PageTurn
```

两个问题：**① 早于提交**（请求若未产生实际提交，用户白丢一次选择）、
**①+③ 重复派发**。

### 3.2 修复后

```
onTap → resolveReaderTapAction
        ↓
val turnPlan = readerTapTurnPlan(action, renderer)     ← 只产生计划，不派发
        ↓
RequestTurn(d)     → stopAutoPaging(); turnRequest = d   （无 PageTurn）
ManualReveal(d)    → stopAutoPaging(); requestManualReveal(d)（无 PageTurn）
ToggleControls     → stopAutoPaging(); onToggleControls()
None               → Unit
        ↓
PageTurner 提交 → onPageTurned = onGesturePageTurn → PageTurn   ← 唯一一次
外层 reveal 结束帧 → controller.nextPage() 之后 → onGesturePageTurn()  ← 唯一一次
```

`ReaderTapTurnPlan.RequestTurn` / `ManualReveal` **不携带任何立即派发的动作**，
因此「点按处提前通知」在结构上不再可能。

---

## 4. 为什么不会提前、重复或无变化清选区

| 风险 | 为什么被排除 |
|---|---|
| **提前**（点按路径） | 点按分支只返回计划；`onGesturePageTurn` 在该分支已被删除，只剩 `onPageTurned`（PageTurner 提交后）与 reveal 动画结束帧两处调用点（`PagedReaderPageSurface.kt:258`、`:271`、`:562`），三处都在页码变更之后。 |
| **重复** | 非 reveal 路径点按不再派发，只有 `onPageTurned` 派发一次；`PageTurner` 内部有 `turning` 门与 `onTurnRequestConsumed`（`PageTurner.kt:338-341`）保证一次请求只提交一次。reveal 路径由 `manualRevealTarget != null` 挡住重入（`:253`）。 |
| **无变化却清**（跳转路径） | `readerJumpClearsSelection` 只在「目标落在当前可见页区间之外」时为 true。三种无变化情形都返回 false：目标仍在当前页内、当前页区间为 null（排版未就绪 / 源为空 / open 被取消）、目标等于当前页。 |
| **恰好一次**（跳转路径） | 通知只在 `applyProgrammaticJump` 内发生，且该函数每次跳转只被调用一次（`LaunchedEffect` 消费后立刻把 `jumpRequest` 置 null）。不需要额外去重状态。 |
| **无变化却清**（边界页） | `resolveReaderTapAction` 在 `canPrevious`/`canNext` 为 false 时返回 `NONE` → `readerTapTurnPlan` 返回 `None` → 既不登记请求也不派发（既有契约，本轮未改，并新增用例钉住）。 |
| **破坏 OUTER_REVEAL** | reveal 分支的计划是 `ManualReveal(d)`，与修复前的 `requestManualReveal(d)` 完全等价；`requestManualReveal` 内部的派发点一字未改。 |
| **破坏硬件键 / 自动翻页** | 两者都不经过点按分支：硬件键经 `externalTurnRequest → turnRequest`（`PagedReaderControllerEffects.kt:119-123`），自动翻页直接写 `turnRequest`（`:150` 附近），最终都走 `PageTurner` → `onPageTurned`。本轮未改这些路径。 |
| **破坏跨章直接加载** | `requestManualReveal` 的降级分支（`target == null` 时 `controller.prev/nextPage()` + `onGesturePageTurn()`，`:255-260`）一字未改。 |

---

## 5. RED / GREEN 证据

本轮的 A、B 两个修复引入的是**新 seam**，因此「修复前的测试失败」无法用回退源码之外的方式
直接观察到（测试本身依赖新函数）。为给出**可执行**的 RED 证据，我用了**变异测试**：
把产品代码改成修复前的行为，确认新测试确实失败。

### 5.1 变异 1 — 模拟修复前的 jumpRequest（通知被丢弃）

```python
# applyProgrammaticJump 内删掉 if (clearsSelection) invalidateSelection()
```

```
$ ./gradlew.bat :app:testDebugUnitTest --tests "...PagedReaderSelectionInvalidationPolicyTest"
退出码：1
PagedReaderSelectionInvalidationPolicyTest > a jump across pages opens the target and notifies exactly once FAILED
12 tests completed, 1 failed
```

### 5.2 变异 2 — 反向：无条件清除（模拟「误清」）

```python
# readerJumpClearsSelection 改为恒 true
```

```
退出码：1
  a jump to the exact current page start reports no change FAILED
  a jump with no visible page yet reports no change FAILED
  a jump that lands on the current page reports no change FAILED
  a jump with no visible page opens but never notifies FAILED
  a jump within the current page opens but never notifies FAILED
12 tests completed, 5 failed
```

**两个方向都被捕获** —— 既证明测试能发现「漏清」，也证明能发现「误清」。

### 5.3 变异 3 — 模拟修复前的点按行为（不再登记翻页请求）

```python
# readerTapTurnPlan 的 PREVIOUS_PAGE 非 reveal 分支改为 ToggleControls
```

```
退出码：1
ReaderTapTurnPlanTest > only a real turn plan carries a direction FAILED
ReaderTapTurnPlanTest > a non reveal tap only registers a turn request FAILED
5 tests completed, 2 failed
```

### 5.4 变异已回滚并验证

三个变异全部回滚，并用 `diff` 确认工作树文件与变异前**逐字节相同**：

```
restored both
PageSurface OK
ControllerEffects OK
```

### 5.5 GREEN

```
$ ./gradlew.bat :app:testDebugUnitTest --rerun-tasks \
   --tests "...PagedReaderSelectionInvalidationPolicyTest" \
   --tests "...ReaderTapTurnPlanTest" \
   --tests "...TxtScrollChapterTargetTest" \
   --tests "...ReaderViewModelTest" \
   --tests "...ReaderContentHostSelectionCallbacksTest" \
   --tests "...ReaderTapActionTest" \
   --tests "...ReaderCopyNoticeTest" \
   --tests "...SelectionActionsTest" \
   --tests "...SelectionActionStoreTest" \
   --tests "...ReaderSelectionToolbarModelTest"
退出码：0；BUILD SUCCESSFUL in 4m 21s；30 actionable tasks: 30 executed
```

---

## 6. 命令、退出码和 XML 统计

### 6.1 一次 `--rerun-tasks` 强制执行（10 类）

| 测试类（XML `testsuite/@name` 原值） | 归属 | tests | failures | errors | skipped |
|---|---|---:|---:|---:|---:|
| `...feature.reader.pager.PagedReaderSelectionInvalidationPolicyTest` | **F2.1 新增** | 12 | 0 | 0 | 0 |
| `...feature.reader.pager.ReaderTapTurnPlanTest` | **F2.1 新增** | 5 | 0 | 0 | 0 |
| `...ui.screen.reader.TxtScrollChapterTargetTest` | **F2.1 改写** | 7 | 0 | 0 | 0 |
| `...ui.viewmodel.ReaderViewModelTest` | F2 回归 | 72 | 0 | 0 | 0 |
| `...ui.screen.reader.ReaderContentHostSelectionCallbacksTest` | F2 回归 | 4 | 0 | 0 | 0 |
| `...feature.reader.pager.ReaderTapActionTest` | F2 回归 | 9 | 0 | 0 | 0 |
| `...ui.screen.reader.ReaderCopyNoticeTest` | **F1 哨兵** | 3 | 0 | 0 | 0 |
| `...data.settings.SelectionActionsTest` | **F1 哨兵** | 24 | 0 | 0 | 0 |
| `...data.settings.SelectionActionStoreTest` | **F1 哨兵** | 13 | 0 | 0 | 0 |
| `...ui.screen.reader.ReaderSelectionToolbarModelTest` | **F1 哨兵** | 9 | 0 | 0 | 0 |
| **合计** | | **158** | **0** | **0** | **0** |

10 个请求的类 = 10 个 XML 文件，无缺类（零匹配防护）。

### 6.2 任务点 8「必须建立的测试」逐条对照

| # | 要求 | 对应用例（读自 XML `testcase/@name`） |
|---:|---|---|
| 1 | jumpRequest 跨到另一可见页后，恰好发送一次选区失效事件 | `a jump across pages opens the target and notifies exactly once`（断言 `invalidations == 1`） |
| 2 | jumpRequest 仍落在当前页时，不发送 | `a jump within the current page opens but never notifies`（`open` 仍执行 1 次、`invalidations == 0`）+ `a jump that lands on the current page reports no change` |
| 3 | controller.open 没有改变页面身份时不清选区 | `a jump with no visible page yet reports no change` + `a jump with no visible page opens but never notifies`（`currentPageRangeAbs == null`） |
| 4 | 非 reveal 点按只在真实提交后发送一次 PageTurn | `a non reveal tap only registers a turn request`（计划是 `RequestTurn`，不含派发）+ `only a real turn plan carries a direction` |
| 5 | 边界页没有提交时不发送 PageTurn | `a boundary tap never produces a turn plan` + `ReaderTapActionTest.boundaryPagesReturnNoneSoNoTurnIsReported` |
| 6 | selectionCleared=true 时本地 selRange 被清除 | `a cleared vm selection drops the local range` |
| 7 | selectionCleared=false 时本地 selRange 保留 | `a live vm selection keeps the local range untouched` |
| 8 | F1 的 edit/复制测试与 F2 的五字段清理测试继续通过 | 见上表 F1 哨兵 4 类（49 tests）与 F2 回归 3 类（85 tests），全绿 |

### 6.3 编译检查

| 命令 | 退出码 | 结果 |
|---|---|---|
| `./gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` | 0 | `BUILD SUCCESSFUL` |

androidTest 一并编译，因为本轮改了 `PagedReaderPageSurface.kt`（仪器测试依赖所在文件）。

---

## 7. before/after patch 哈希与精确增量

```
git diff origin/main -- <5 个拥有的源文件> > /tmp/s07f21-before.patch   # 539 行
git diff origin/main -- <5 个拥有的源文件> > /tmp/s07f21-after.patch    # 768 行
```

| 快照 | SHA-256 |
|---|---|
| before | `20aeb97c8b64cfa909edd57e2b89e544482c65cf8dff1f6c12987cf8610c0b8f` |
| after | `4fcc5220dbdf1c1133fb9c2a70362edb3ccfa14f0da11343f29a017ac0864f94` |

**after 快照真实性**：重新执行一次 `git diff` 到 `/tmp/verify.patch`，与 `after.patch`
`diff -q` 结果为**逐字节相同**。

### 7.1 逐文件增量

| 文件 | before | after | delta |
|---|---:|---:|---:|
| `PagedReaderControllerEffects.kt` | 0 | 111 | **+111** |
| `PagedReaderPageSurface.kt` | 156 | 265 | **+109** |
| `PagedTxtReaderHost.kt` | 150 | 159 | **+9** |
| `ReaderContentHostPagedBranch.kt` | 36 | 36 | **0** |
| `ReaderLayerBuilders.kt` | 197 | 197 | **0** |

> `PagedReaderControllerEffects.kt` 的 before 为 0，说明该文件在我开工前与 `origin/main` 无内容差异
> （`git status` 的 `M` 仅来自 CRLF 归一化提示）；本轮全部 111 行都是我的新增。

delta=0 的两个文件已用 `diff` 单独比对 patch 段，结果为 `IDENTICAL`。

### 7.2 增量内容（`diff -u before after` 的全部有效 `+` 行）

只有四类，无任何夹带：

1. `PagedReaderControllerEffects.kt`：`readerJumpClearsSelection()` / `applyProgrammaticJump()` / `shouldDropLocalSelection()` 三个纯函数（含 KDoc）；`onSelectionInvalidated` 参数；`jumpRequest` 分支接入；`selectionCleared` 分支改用纯函数。
2. `PagedReaderPageSurface.kt`：`ReaderTapTurnPlan` 密封接口 + `readerTapTurnPlan()`；点按 `when` 改为按计划分派（删除 `onGesturePageTurn()` 提前调用）。
3. `PagedTxtReaderHost.kt`：`onSelectionInvalidated = onGesturePageTurn` 一行接线（+ 2 行注释）。
4. 测试文件（不在 patch 范围内，单独新增/改写）。

**未出现**任何 Markdown / EPUB 渲染、持久高亮、`edit` / `DORMANT_IDS`、复制提示、
AI、替换净化、TTS、页眉页脚、日夜灯绳的改动。

### 7.3 收尾检查

```
$ git diff --check -- <本轮实际修改的 6 个文件>
退出码：0   （仅一条 CRLF 提示，无空白错误）

$ git status --short
（本轮 8 个文件见 §1；其余 300+ 项为工作区既有用户资产，未触碰）
```

---

## 8. ST-1 / ST-2 / ST-3 / SP-2 逐条关闭

| 项 | 处理 | 证据 |
|---|---|---|
| **SP-2 / A**：同章程序化跳转不清选区 | ✅ 已修 | `jumpRequest` 分支接入 `applyProgrammaticJump`，身份变化时发一次通知 |
| **ST-2 / B**：非 reveal 点按提前派发 PageTurn | ✅ 已修 | 点按改为「只产生计划」，`onGesturePageTurn` 从该分支移除 |
| **ST-1 / C-1**：用例名与断言相反 | ✅ 改名 | `the first chapter has no previous target` → `the first chapter previous target falls back to the chapter start`，并补 `assertNotNull` 显式记录「首章仍有目标、调用方据此清选区」 |
| **ST-3 / C-2**：回流无断言 | ✅ 已加 | `shouldDropLocalSelection()` 纯函数 + 4 条断言，并在 `LaunchedEffect(selectionCleared)` 里实际使用 |

**关于 ST-1 的产品契约判断（任务要求「除非代码证据证明产品契约应该改，否则只修名称」）**：
我核对了 EPUB/Markdown 路径的同类边界 —— `goToChapter` 用 `i.coerceIn(0, maxIndex)`
（`ReaderProgressActions.kt:79`），首章同样**钳到章首**而非无动作。两条路径同向，
说明「首章点上一章 = 回到本章开头」是既定的跨格式契约，不是 TXT 独有的缺陷。
因此本轮**只改名称与注释**，未改行为。已在用例注释里写明这一点。

---

## 9. 未验证项

- **未做真机验证**。全部证据是 JVM 纯函数与接线级；「翻页后工具条不再滞留」「同章跳页后无残留色块」没有真机复现。按任务约束（不使用 MuMu、不操作真实手机）归 WorkBuddy。
- **未挂载 Compose 运行期**。`LaunchedEffect(jumpRequest.value)` 里 `applyProgrammaticJump` 的真实调用、`selectionCleared` 回流 effect 的执行，都只有纯函数层证据；`shouldDropLocalSelection` 已可测，但它在 `LaunchedEffect` 中的实际执行未做运行期断言。
- **未验证 `controller.currentPageRangeAbs` 在跳转瞬间的时效性**。判定读的是 `open` **之前**的区间，依赖 `open` 是确定性的（已从 `chapterIndexFor` / `globalSourceToLocalDisplay` / `pageIndexFor` 三条链静态确认）。若未来 `open` 引入随机化或依赖未就绪状态，本判定需要重新审视。
- **未覆盖「跳转与用户翻页并发」**。若 `jumpRequest` 与 `turnRequest` 同时挂起，两者的清除语义叠加（都清，幂等），但并发时序未做测试。
- **未验证 `PageTurner` 的 `onPageTurned` 时序**。我依赖「提交后回调」这一既有事实（`PageTurner.kt:289-290`、`:355-356` 均为 `onNext/onPrevious()` 之后紧跟 `onPageTurned()`），未做运行时验证。
- **硬件键 / 自动翻页未在设备上触发**。静态确认它们走 `PageTurner` → `onPageTurned`，无设备证据。
- **TXT 首章「上一章」的交互取舍未由产品确认**。我按代码证据（两条路径同向钳到章首）判定应保留行为，但「首章点上一章是否该清选区」最终是产品判断。
- **Markdown / 旧 EPUB 选区能力未处理**（见 §11）。
- **`selectedSourceLength` 半清错值未独立复现**（F2 遗留）。

---

## 10. 合规声明

- **是否越界**：**否**。改动只落在授权清单的 3 个源文件 + 3 个 JVM 测试文件 + 本报告；授权清单内另 2 个文件经核实无需改动且已逐字节证明未触碰。
- **是否使用 `ReaderViewModel.kt`**：**否**。本切片不需要它 —— A 复用 `PageTurn`，B 只改派发时机，C 只加纯函数断言，因此五字段契约一字未动。
- **是否修改禁止项**：**否**。未改 Markdown、旧 EPUB、持久高亮、`edit`、复制、AI、替换净化；未改 Gradle、Room、桌面端、正式报告、产品盘点、Obsidian。
- **是否 stage / commit / push**：**否**。`git diff --cached --stat` 为空，未创建分支或 worktree。
- **是否 reset / checkout / clean / stash / 全仓格式化**：**否**。`git stash list` 为空；工作区既有 300+ 项修改原样保留。
- **是否使用 MuMu / 真实手机**：**否**。
- **变异测试是否残留**：**否**。3 个变异全部回滚，并用 `diff` 确认工作树文件与变异前逐字节相同（§5.4）。
- **未处理 Markdown / 旧 EPUB**：**是，明确未处理**。滚动模式无选区入口、旧 EPUB 整块点选、两者不重渲染已存高亮，均属正式报告 Spec P1 第 4 项 / P2，本轮一字未动，状态未变。
- **SEAM REQUEST**：**无**。本切片在授权文件内闭合。

---

## 11. 最终状态

**等待 Qoder/Codex 复核。**

本报告不宣称 S07 已完成或可发布。剩余 P1（Markdown/旧 EPUB 选区能力不完整）与 P2 项未在本轮处理；
真机验收仍归 WorkBuddy。S08 及看板队列其余切片保持 `HOLD`。

**未领取下一任务。**
