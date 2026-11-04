# S07-F2 修复执行报告（Zcode）

> Agent：Zcode（模型 `deepseek v4.1Flash`）
> 日期：2026-09-20
> 恢复仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 工作分支：`codex/recover-worktree-20260920`
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 任务来源：`docs/code-review/2026-09/2026-09-20-android-s07-selection-review.md` Spec P1 第 3 项；`docs/coordination/reviews/undated/agent-review-s07-f1-qoder.md` SP-3.2
> 本轮范围：只修「页面或章节身份改变后必须完整清除旧选区状态」这一个垂直切片
> 最终状态：**等待 Qoder/Codex 复核**

---

## 1. 实际修改文件

只用了授权清单中 6 个源文件里的 **3 个**（另 3 个经核实无需改动）：

| 文件 | 类型 | 改动性质 |
|---|---|---|
| `ui/viewmodel/ReaderViewModel.kt` | M | 新增 `ReaderScreenState.withSelectionCleared()`；`ClearSelection` / `PageTurn` / `LoadChapter` / 中央点击 / 规则保存成功 五处收敛到它 |
| `ui/screen/reader/ReaderLayerBuilders.kt` | M | `onSelect` 空文本 → `ClearSelection`；TXT 滚动章导航与目录 `PlainOffset` 分支补清选区 |
| `ui/screen/reader/ReaderProgressActions.kt` | M | 新增纯函数 `txtScrollChapterTargetOffset()`（含 `DocChapter` import） |
| `src/test/.../ui/viewmodel/ReaderViewModelTest.kt` | M | 新增 6 个状态级用例 + 2 个辅助函数 |
| `src/test/.../ui/screen/reader/ReaderContentHostSelectionCallbacksTest.kt` | **新增** | 4 个接线用例 |
| `src/test/.../ui/screen/reader/TxtScrollChapterTargetTest.kt` | **新增** | 7 个纯函数用例 |
| `src/test/.../feature/reader/pager/ReaderTapActionTest.kt` | M | 新增 2 个边界契约用例 |
| `docs/coordination/agent-results/agent-result-s07-f2-zcode.md` | **新增** | 本报告 |

**在授权清单内但经核实无需改动**（已用 before/after patch 逐字节比对证明未触碰，见 §6）：

- `feature/reader/pager/PagedReaderPageSurface.kt` — 不需要在这里加 `onSelect("", -1, -1)`
- `feature/reader/pager/PagedTxtReaderHost.kt` — 同上
- `ui/screen/reader/ReaderContentHostPagedBranch.kt` — 同上

理由见 §3.2「为什么没有改分页表面」。

---

## 2. 翻页 / 切章事件矩阵

### 2.1 修复前的调用链（开工前逐条实读）

| # | 事件 | 修复前调用链 | 修复前结果 |
|---|---|---|---|
| 1 | 普通点按撤选区 | `PagedReaderPageSurface:341-345` → `selRange=null` + `onSelect("",-1,-1)` → `ReaderContentHostPagedBranch:118` 适配 → `ReaderLayerBuilders:119` → **`SetSelectedText("", -1, -1, -1)`** | ❌ 只复位 4 字段，**`showColorRow` 留在打开态** |
| 2 | 手势翻页 | `PageTurner.onNext/onPrevious`（`:499-506`）→ 只 `selRange=null` + `controller.nextPage()` → `onPageTurned` → `ReaderContentHostPagedBranch:87` → `ReaderLayerBuilders:118` → `ReaderAction.PageTurn` → VM `:294-296` 只跑 chrome reducer | ❌ **VM 选区完全不清**，工具条滞留新页 |
| 3 | 硬件键翻页（分页） | `ReaderSessionEffects:236-247` → `readerVolumeKeyTurn` → `pagedHardwareTurnRequest` → `PagedReaderPageSurface` turnRequest → 同 #2 | ❌ 同 #2 |
| 4 | 自动翻页 | `PagedReaderControllerEffects:130-134` → `turnRequest=1`（或相邻章未预排时直接 `controller.nextPage()`）→ 同 #2 | ❌ 同 #2（且选区内自动翻页本就暂停，见 §2.4） |
| 5 | 外部翻页请求 | `pagedHardwareTurnRequest` → `PagedReaderControllerEffects:92-96` → `turnRequest` → 同 #2 | ❌ 同 #2 |
| 6 | 上一章 / 下一章（EPUB/MD） | `ReaderLayerBuilders:305/312` → `goToPrevChapter`/`goToChapter` → `ReaderProgressActions:53-98` → `ReaderAction.LoadChapter` → VM `:274` 只 `loadChapter()` | ❌ **VM 选区完全不清** |
| 7 | 上一章 / 下一章（纯 TXT 滚动） | `ReaderLayerBuilders:297-314` → `jumpToPlainOffset`（**绕过 LoadChapter**） | ❌ 完全不清 |
| 8 | 目录选择章节（EPUB/MD） | `ReaderLayerBuilders:566` → `goToChapter` → 同 #6 | ❌ 同 #6 |
| 9 | 目录选择章节（纯 TXT） | `ReaderLayerBuilders:567` → `jumpToPlainOffset`（**绕过 LoadChapter**） | ❌ 完全不清 |
| 10 | 中央控制栏点击 | `ReaderLayerBuilders:116` → `ReaderAction.ToggleControls()` → VM `:280-293` | ⚠️ 只清 `selectedText` + `selectedRangeStart`，**`selectedGlobalOffset` / `selectedSourceEnd` / `showColorRow` 保留** |
| 11 | 规则保存成功 | VM `:486-493` 手写 5 字段 copy | ✅ 语义正确，但与其它处重复实现 |

**#10 的后果**（Qoder SP-3.1 已指出）：半清状态下 `ReaderScaffold:594-600` 的 `selectedSourceLength` 由 `selectedSourceEnd` 减 `selectedRangeStart`/`selectedGlobalOffset` 混搭推出，可能算出跨字段错值（被 `?.takeIf { it >= 0 }` 兜成 null，进而静默写 `payload = "{}"`）；且下次长按会直接落在「选择高亮颜色」行。

### 2.2 修复后的调用链

核心是把「完整清除」收敛成**唯一语义**，页面/章节变化的调用方只发统一动作，不再各自手写字段清单。

```
ReaderScreenState.withSelectionCleared()        ← 字段清单只在这里维护（5 字段）
        ↑
        ├── ReaderAction.ClearSelection          （统一入口）
        ├── ReaderAction.PageTurn                ← 事件 2/3/4/5
        ├── ReaderAction.LoadChapter             ← 事件 6/8（及搜索跳转、高亮回源）
        ├── ToggleControls(CenterTap) 且有选区   ← 事件 10
        └── 规则保存成功                          ← 事件 11（原手写 copy 收敛）
```

| # | 事件 | 修复后路径 | 结果 |
|---|---|---|---|
| 1 | 普通点按撤选区 | 空文本 `onSelect` → **`ClearSelection`** | ✅ 5 字段 |
| 2-5 | 各类翻页 | `PageTurn` reducer → `withSelectionCleared()` | ✅ 5 字段 |
| 6/8 | 章跳转（经 LoadChapter） | `LoadChapter` reducer → `withSelectionCleared()` | ✅ 5 字段 |
| 7/9 | 章跳转（纯 TXT 绕过 LoadChapter） | 调用方在**确有目标时**显式发 `ClearSelection` | ✅ 5 字段 |
| 10 | 中央点击 | CenterTap 且有选区 → `withSelectionCleared()` | ✅ 5 字段 |
| 11 | 规则保存成功 | 收敛到 `withSelectionCleared()` | ✅ 5 字段 |

### 2.3 宿主侧本地 `selRange` 如何撤掉

VM 清除后 `selectedText` 变为空 → `ReaderContentHostPagedBranch:125` 的
`selectionCleared = selectedText.isBlank()` 变 true → `PagedReaderControllerEffects:91`
的 `LaunchedEffect(selectionCleared)` 把本地 `selRange` 置 null。

这条**回流**原本就存在且是单向的（VM 已清 → 撤本地）；本轮补的是缺失的上游 ——
此前 #2/#6 根本不发任何清选区的动作，所以回流永远不会被触发。

### 2.4 边界与「不得伪装成功翻页」

- **第 4 项要求（到达第一页/最后一页且页面没变时不得伪装成功翻页）**：
  `resolveReaderTapAction`（`ReaderTapAction.kt:52-56`）在 `canPrevious`/`canNext` 为 false
  时返回 `NONE`，调用方（`PagedReaderPageSurface:341-385`）在 `NONE` 分支不做任何事，
  因此**不会派发 `PageTurn`**。本轮没有改动这个既有契约，而是补了用例把它钉住
  （`ReaderTapActionTest.boundaryPagesReturnNoneSoNoTurnIsReported`）。
- **第 4 项的「是否清选区必须由现有交互契约明确」**：现有契约是「页面身份变化才清」。
  边界页没有换页 → 不清。这既符合 `PageTurn` 语义（`ReaderChromeEvent.PageTurn` 注释
  「翻页立即隐藏」），也避免「在第一页点空白就把用户刚选的字清掉」。
  同时我加了 `a page turn without a selection is a harmless no-op on selection state`，
  证明即便 `PageTurn` 被派发，也不会凭空造出选区或改动其它状态。
- **第 3 项要求（普通正文点击在已有选区时只取消选区、不额外翻页）**：
  `PagedReaderPageSurface:342-345` 的既有行为是「有选区时任何点按先撤选区，不翻页」。
  本轮未改这个分支，只是让它的清除语义变完整。
- **第 5 项（切书不重复修复）**：`openBook` 会建立新的 `ReaderUiState`，未触碰。

### 2.5 自动翻页为何在「有选区」时不会被触发

`autoPagingPaused`（`ReaderScreen.kt:450-456`）包含 `selectedText.isNotBlank()`，
选区非空时自动翻页暂停；且激活自动翻页时本来就发 `ClearSelection`
（`ReaderActions.kt:181`）。因此第 4 项的覆盖是「自动翻页推进时的换页」——
它同样走 `PageTurn` 通道，本轮修复对其生效。

---

## 3. 修复内容详解

### 3.1 统一语义（`ReaderViewModel.kt`）

```kotlin
internal fun ReaderScreenState.withSelectionCleared(): ReaderScreenState = copy(
    selectedText = "",
    selectedRangeStart = -1,
    selectedGlobalOffset = -1,
    selectedSourceEnd = -1,
    showColorRow = false,
)
```

KDoc 逐条说明为什么五个字段必须一起复位（工具条可见性门控 / 两种互斥起点口径 /
共用终点被 `selectedSourceLength` 消费 / 色板行是二级视图）。

**关于 `ReaderScreenState` 的导入**：函数定义在 `ReaderViewModel.kt` 内（与既有
`toReaderChromeState` / `readerChromeReducer` 同一模式），但它扩展的是
`ui.screen.reader.ReaderScreenState`。该文件已 import 这个类型（`:39`），无需新增 import。

### 3.2 为什么没有改分页表面（授权清单内的三个文件）

`PagedReaderPageSurface.onPrevious/onNext` 目前只做 `selRange.value = null` +
`controller.prevPage()/nextPage()`，**不**回调 `onSelect("", -1, -1)`。

我没有在宿主侧补 `onSelect("", -1, -1)`，理由：

1. **重复且更脆弱**：翻页提交后 `onPageTurned` → `PageTurn` 已经清 VM 选区，
   宿主再加一次 `onSelect` 会产生两条并行的清除路径，将来任一条被改动都可能分叉。
2. **时序更好**：`PageTurn` 在页码提交（`onPageTurned`）之后派发，此时新页已是当前页，
   清除的是「已被新页取代」的旧选区，语义精确。
3. **本地 `selRange` 已由回流覆盖**：`selectionCleared` 的 `LaunchedEffect` 会在
   VM 清空后撤掉本地选区（§2.3）。宿主自清 `selRange` 只是让撤除提前一帧，无功能差异。
4. 任务明确「尽量使用最少文件」「不要为了修复而大规模重构」。

`PagedTxtReaderHost.kt` / `ReaderContentHostPagedBranch.kt` 同理：它们的 `onGesturePageTurn`
→ `onHideControls` → `PageTurn` 通道已经是修复生效的路径，无需改动。

**已用 patch 逐字节证明这三个文件未被我触碰**（§6）。

### 3.3 `onSelect` 空文本 → 统一清除（`ReaderLayerBuilders.kt`）

```kotlin
onSelect = { text, globalOffset, rangeStart, sourceEnd ->
    if (text.isBlank()) {
        onAction(ReaderAction.ClearSelection)
    } else {
        onAction(ReaderAction.SetSelectedText(text, rangeStart, globalOffset, sourceEnd))
    }
},
```

覆盖事件 #1（单击/双击撤选、滚动单元回收）。用 `isBlank()` 而非 `isEmpty()`：
`BasicTextField` 上报的空选区可能是空白串。

### 3.4 纯 TXT 章导航（绕过 `LoadChapter` 的两条路）

`jumpToPlainOffset` 不经 `LoadChapter`，因此这两条路必须显式清。抽成纯函数后
「有没有可跳目标」变成可测的 `Int?`：

```kotlin
internal fun txtScrollChapterTargetOffset(
    direction: Int,
    txtChapters: List<DocChapter>,
    txtChapterIndex: Int,
): Int? = when {
    direction < 0 -> txtChapters.getOrNull(txtChapterIndex)?.startOffset?.let { (it - 1).coerceAtLeast(0) }
    direction > 0 -> txtChapters.getOrNull(txtChapterIndex + 1)?.startOffset
    else -> null
}
```

调用方只在返回非 null（**真的发生了章跳转**）时才清选区：

- 上一章/下一章：`ReaderLayerBuilders:onPrevChapter/onNextChapter`
- 目录 `PlainOffset` 分支：`ReaderLayerBuilders:onPickChapter`
- 目录 `Chapter` 分支与 `None` 分支**不清**（前者经 LoadChapter 已收敛；后者什么都没发生）

这样满足第 4 项要求：末章点「下一章」、目录未识别时不把「没换章」伪装成一次导航。

---

## 4. 修复前 RED 与修复后 GREEN

### 4.1 RED（修复前）

命令：

```
$ cd android && ./gradlew.bat :app:testDebugUnitTest \
    --tests "com.creationreadingassistant.ui.viewmodel.ReaderViewModelTest" \
    --tests "com.creationreadingassistant.ui.screen.reader.ReaderContentHostSelectionCallbacksTest"
```

退出码：**1**

```
ReaderContentHostSelectionCallbacksTest > an epub style selection keeps its global offset FAILED
ReaderContentHostSelectionCallbacksTest > an empty selection signal maps to the single full clear action FAILED
ReaderContentHostSelectionCallbacksTest > a blank but non empty string still counts as a clear FAILED
ReaderContentHostSelectionCallbacksTest > a real selection still maps to SetSelectedText with all four coordinates FAILED
ReaderViewModelTest > a committed page turn clears the whole selection FAILED
ReaderViewModelTest > clearing on a page turn preserves the new reading position FAILED
ReaderViewModelTest > a chapter change clears the whole selection FAILED
ReaderViewModelTest > a centre tap that dismisses an open selection clears the whole selection FAILED
76 tests completed, 8 failed
```

其中 4 个 VM 用例正是本轮要修的三条路径（PageTurn / LoadChapter / CenterTap）。

**说明（诚实登记）**：`ReaderContentHostSelectionCallbacksTest` 的 4 条中，有 2 条
（`a real selection...` / `an epub style selection...`）首次运行时失败是**我自己写错了
期望值**（把 `onSelect(text, globalOffset, rangeStart, sourceEnd)` 的中间两参写反了），
不是产品缺陷。我读源码确认真实顺序后修正了期望值，修正后这两条在**未修复的产品代码上
也通过** —— 它们锁定的是既有的选择分支契约，用来防止修复清除分支时把选择分支一起改错。
真正的 RED 证据是另外 2 条（`empty selection` / `blank but non empty`）加 4 条 VM 用例。

### 4.2 GREEN（修复后，强制全新执行）

```
$ cd android && ./gradlew.bat :app:testDebugUnitTest --rerun-tasks \
    --tests "com.creationreadingassistant.ui.viewmodel.ReaderViewModelTest" \
    --tests "com.creationreadingassistant.ui.screen.reader.ReaderContentHostSelectionCallbacksTest" \
    --tests "com.creationreadingassistant.ui.screen.reader.TxtScrollChapterTargetTest" \
    --tests "com.creationreadingassistant.feature.reader.pager.ReaderTapActionTest"
```

退出码：**0**，`BUILD SUCCESSFUL in 2m 10s`，`30 actionable tasks: 30 executed`。

---

## 5. 完整命令、退出码与 XML 测试统计

### 5.1 本切片定向测试（`--rerun-tasks` 强制真实执行）

| 测试类（XML `testsuite/@name` 原值） | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `com.creationreadingassistant.ui.viewmodel.ReaderViewModelTest` | 72 | 0 | 0 | 0 |
| `com.creationreadingassistant.ui.screen.reader.ReaderContentHostSelectionCallbacksTest` | 4 | 0 | 0 | 0 |
| `com.creationreadingassistant.ui.screen.reader.TxtScrollChapterTargetTest` | 7 | 0 | 0 | 0 |
| `com.creationreadingassistant.feature.reader.pager.ReaderTapActionTest` | 9 | 0 | 0 | 0 |
| **合计** | **92** | **0** | **0** | **0** |

4 个请求的类 = 4 个 XML 文件，无缺类（零匹配防护）。

### 5.2 新增/改写用例执行确认（读自 XML `testcase/@name`，18 条）

```
ReaderViewModelTest :: a committed page turn clears the whole selection
ReaderViewModelTest :: a chapter change clears the whole selection
ReaderViewModelTest :: a centre tap that dismisses an open selection clears the whole selection
ReaderViewModelTest :: clear selection resets every field including the colour row
ReaderViewModelTest :: clearing on a page turn preserves the new reading position
ReaderViewModelTest :: a page turn without a selection is a harmless no-op on selection state

ReaderContentHostSelectionCallbacksTest :: an empty selection signal maps to the single full clear action
ReaderContentHostSelectionCallbacksTest :: a blank but non empty string still counts as a clear
ReaderContentHostSelectionCallbacksTest :: a real selection still maps to SetSelectedText with all four coordinates
ReaderContentHostSelectionCallbacksTest :: an epub style selection keeps its global offset

TxtScrollChapterTargetTest :: next chapter targets the following chapter start
TxtScrollChapterTargetTest :: previous chapter targets the end of the previous chapter
TxtScrollChapterTargetTest :: the first chapter has no previous target
TxtScrollChapterTargetTest :: the last chapter has no next target
TxtScrollChapterTargetTest :: an unidentified toc has no target in either direction
TxtScrollChapterTargetTest :: an out of range chapter index has no target
TxtScrollChapterTargetTest :: a zero direction never produces a target

ReaderTapActionTest :: boundaryPagesReturnNoneSoNoTurnIsReported
ReaderTapActionTest :: boundaryPagesStillToggleControlsInTheCentre
```

### 5.3 任务点 4 要求的断言覆盖对照

| 任务要求 | 对应用例 |
|---|---|
| 页面变化后五个 VM 选区字段全部复位 | `a committed page turn clears the whole selection`（经 `assertSelectionFullyCleared` 逐字段断言） |
| 章节变化后五个字段全部复位 | `a chapter change clears the whole selection` |
| 普通取消选择使用同一完整语义 | `clear selection resets every field including the colour row` + `ReaderContentHostSelectionCallbacksTest`（空文本 → `ClearSelection`） |
| 清选区不会破坏新的页面/章节位置 | `clearing on a page turn preserves the new reading position`（断言 chrome 可见性、searchQuery、sheet 不受影响） |
| 没有成功换页时不会误报页面变化 | `ReaderTapActionTest.boundaryPagesReturnNoneSoNoTurnIsReported` + `a page turn without a selection is a harmless no-op on selection state` |

### 5.4 回归检查（相邻 11 个测试类）

```
$ ./gradlew.bat :app:testDebugUnitTest \
   --tests "...ReaderChromeReducerTest" --tests "...ReaderProgressNavigationTest" \
   --tests "...ReaderSelectionSearchPolicyTest" --tests "...TxtRuleRestorePolicyTest" \
   --tests "...PageSelectionHandleDragTest" --tests "...PagedReaderPageTurnRendererTest" \
   --tests "...ReaderChapterNavigationTest" --tests "...ReaderTocStateTest" \
   --tests "...ReaderVolumeKeyTurnPolicyTest" --tests "...ReaderSelectionToolbarModelTest" \
   --tests "...ReaderCopyNoticeTest"
```

退出码 **0**；XML 确认 **11 类 / 81 tests / 0 failures / 0 errors / 0 skipped**。
其中 `ReaderSelectionToolbarModelTest`(9) 与 `ReaderCopyNoticeTest`(3) 是 F1 的产物，
一并通过 —— 本轮未破坏 F1 已通过的逻辑。

### 5.5 编译检查

| 命令 | 退出码 | 结果 |
|---|---|---|
| `./gradlew.bat :app:compileDebugKotlin` | 0 | `BUILD SUCCESSFUL` |
| `./gradlew.bat :app:compileDebugAndroidTestKotlin` | 0 | `BUILD SUCCESSFUL` |

> 过程中出现并已修复 1 个我引入的编译错误：`ReaderProgressActions.kt` 使用
> `DocChapter` 但缺 import，已补 `import com.creationreadingassistant.feature.reader.doc.DocChapter`。

---

## 6. before/after patch 对比得到的精确增量

按任务要求，开工前与收工后各导出一次快照到仓库外：

```
git diff origin/main -- <6 个拥有的源文件> > /tmp/s07f2-before.patch   # 1187 行
git diff origin/main -- <6 个拥有的源文件> > /tmp/s07f2-after.patch    # 1345 行
```

### 6.1 逐文件增量

| 文件 | before | after | delta | 判定 |
|---|---:|---:|---:|---|
| `ReaderViewModel.kt` | 590 | 675 | **+85** | 本轮修改 |
| `ReaderLayerBuilders.kt` | 159 | 197 | **+38** | 本轮修改 |
| `ReaderProgressActions.kt` | 96 | 131 | **+35** | 本轮修改 |
| `PagedReaderPageSurface.kt` | 156 | 156 | **0** | 逐字节相同，未触碰 |
| `PagedTxtReaderHost.kt` | 150 | 150 | **0** | 逐字节相同，未触碰 |
| `ReaderContentHostPagedBranch.kt` | 36 | 36 | **0** | 逐字节相同，未触碰 |

delta=0 的三个文件用 `diff` 单独比对了它们的 patch 段，结果 `IDENTICAL`。

### 6.2 增量内容（`diff -u before after` 的全部 `+` 行）

- `ReaderViewModel.kt`：新增 `withSelectionCleared()`（含 14 行 KDoc）；`LoadChapter` 分支包成块并加清选区；`ToggleControls` 的 CenterTap 分支改为条件调 `withSelectionCleared()`；`PageTurn` 分支链式加 `withSelectionCleared()`；`ClearSelection` 分支由 10 行手写 copy 收敛为 1 行；规则保存成功路径由 9 行手写 copy 收敛为 1 行。
- `ReaderLayerBuilders.kt`：`onSelect` 由 1 行改为 6 行（含 3 行注释）；`onPrevChapter`/`onNextChapter` 的 TXT 分支改用 `txtScrollChapterTargetOffset(...)?.let { ... }`；`onPickChapter` 的 `PlainOffset` 分支加清选区 + 3 行注释。
- `ReaderProgressActions.kt`：新增 `DocChapter` import 与 `txtScrollChapterTargetOffset()`（含 13 行 KDoc）。

**未出现**任何与选区清除无关的新增行。工作区既有的其它 hunk（M-BUG-08 章导航、一键日/夜、TTS 语速、页眉页脚、替换投影等）在 before 与 after 中逐字节一致。

### 6.3 收尾检查

```
$ git diff --check -- <本轮实际修改的 7 个文件>
退出码：0    （仅一条 CRLF 提示，无空白错误）

$ git status --short
（我的 10 个文件见 §1；其余 300+ 项为工作区既有用户资产，未触碰）
```

---

## 7. 未验证项

- **未做真机验证**。本轮全部证据是 JVM 状态级与接线级测试；「翻页后工具条不再滞留」「切章后不会用旧坐标存高亮」没有真机复现。按任务约束（不使用 MuMu、不操作真实手机）归 WorkBuddy。
- **未覆盖 Compose 运行期**。`PageTurn`/`LoadChapter` 的清选区在 VM 层有状态级证据；但「宿主 `selRange` 是否确实随 `selectionCleared` 撤掉」只有静态链条（`ReaderContentHostPagedBranch:125` → `PagedReaderControllerEffects:91`），没有挂载 Compose 的运行期断言。
- **未覆盖 `PageTurner` 的 `onPageTurned` 时序**。我依赖「`onPageTurned` 在页码提交后调用」这一既有事实（`PageTurner.kt:289`），未做运行时验证；若某条动画路径在提交前回调，清除会早一帧（功能上仍正确，但未验证）。
- **未验证硬件键/自动翻页的真实设备行为**。两者都走 `PageTurn` 通道（静态确认），未在设备上触发。
- **未处理搜索跳转 / 高亮回源 / TTS 跳转的「同章内跳转」**。这些路径若**不改变章节**（`ReaderProgressEffects:379` 的 `chapterIndex != targetChapter` 才 `goToChapter`），不会经过 `LoadChapter`，因此**不清选区**。这符合「页面/章节身份变化才清」的既定不变量，但若产品希望「任何跳转都清」，需要另立切片明确契约。
- **`ReaderProgressEffects` 的 `pagedJumpRequest` 路径**（同章内分页跳转，`:379-396`）同理不清选区，未在本轮范围内。
- **未验证 `ReaderScaffold` 的 `selectedSourceLength` 半清错值是否真的发生**。Qoder SP-3.1 是静态推断，本轮修掉了产生它的 CenterTap 半清路径，但没有独立复现该错值。

---

## 8. 合规声明

- **是否越界**：**否**。改动只落在授权清单的 3 个源文件 + 4 个 JVM 测试文件 + 本报告；授权清单内另外 3 个文件经核实无需改动，且已用 patch 逐字节证明未触碰。
- **是否修改禁止项**：**否**。未改 `SelectionActionSettings`、复制提示或 F1 已通过的逻辑；未改 Markdown/旧 EPUB 内容渲染；未改 Gradle、Room、桌面端、正式报告、产品盘点或 Obsidian。
- **是否 stage / commit / push**：**否**。`git diff --cached --stat` 为空，未创建分支或 worktree。
- **是否 reset / checkout / clean / stash / 全仓格式化**：**否**。`git stash list` 为空；工作区既有 300+ 项修改原样保留。
- **是否使用 MuMu / 真实手机**：**否**。
- **未处理 Markdown / 旧 EPUB**：**是，明确未处理**。这两条路径的选区入口缺失（Markdown 滚动无入口、旧 EPUB 整块点选）与持久高亮不重渲染，均属正式报告 Spec P1 第 4 项 / P2，不在本轮范围，状态未变。
- **SEAM REQUEST**：**无**。本切片能在授权文件内闭合，未遇到需要越界的情况。

---

## 9. 最终状态

**等待 Qoder/Codex 复核。**

本报告不宣称 S07 已完成或可发布。剩余 P1（Markdown/旧 EPUB 选区能力不完整）与 P2 项未在本轮处理；真机验收仍归 WorkBuddy。S08 及看板队列其余切片保持 `HOLD`。

**未领取下一任务。**
