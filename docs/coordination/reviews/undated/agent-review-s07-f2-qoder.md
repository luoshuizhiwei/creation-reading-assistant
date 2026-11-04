# S07-F2 独立复核（Qoder）

- 复核 Agent：Qoder（模型 `qwen3.8Flash`）
- 复核对象：S07-F2「页面/章节身份变化后完整清除旧选区状态」
- 工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`；分支 `codex/recover-worktree-20260920`
- 已读输入：`AGENTS.md`、`docs/code-review/2026-09/2026-09-20-android-s07-selection-review.md`、`docs/coordination/agent-results/agent-result-s07-f2-zcode.md`、3 个改动源文件与 4 个测试文件的当前内容、Zcode 留在仓库外的 before/after patch（我独立重跑了它的 diff）
- 前置确认：Zcode 结果文件 mtime `10:48`，最后一个源码/测试写入 `10:42`，即其已明确完成；我在 `10:05` 的 before 快照与 `10:44` 的 after 快照之间未插任何改动
- 标注：**[事实]** 我亲自读到的代码或跑过的命令；**[推断]** 静态推导；**[未验证]** 需真机或 Compose 运行期

本轮的取证强度与上一轮不同：Zcode 按建议把 before/after 快照留在了仓库外，而 `after.patch` 与我现场重跑的 `git diff origin/main -- <6 files>` **逐字节相同**，所以我能给出"同文件内其它 hunk 未被改动"的**证明**而不是推断（见 Scope integrity）。

---

## Standards

### 目标逐条判定（对应任务 10 项检查）

| # | 检查项 | 判定 | 一手证据 |
|---:|---|---|---|
| 1 | 五个 VM 字段 + 本地 `selRange` 同时清除 | **成立** | `ReaderViewModel.kt:87-93 withSelectionCleared()` 一次复位 5 字段；字段清单在全仓**只有这一处**（见 ST-1 的 grep）；本地 `selRange` 由 `PagedReaderControllerEffects.kt:91` 的 `LaunchedEffect(selectionCleared) { if (selectionCleared) selRange.value = null }` 回流撤除，翻页提交处 `PagedReaderPageSurface.kt:500/504` 另有自清（提前一帧） |
| 2 | 手势 / 硬件键 / 自动翻页 / 外部请求进入同一清理语义 | **成立** | `PageTurner.kt:289-290` 与 `:355-356` 两处都是 `onPrevious()/onNext()` 之后紧跟 `onPageTurned()`；`onPageTurned = onGesturePageTurn`（`PagedReaderPageSurface.kt:507`）→ `ReaderContentHostPagedBranch.kt:87` → `ReaderLayerBuilders.kt:118 onHideControls = { onAction(ReaderAction.PageTurn) }` → `ReaderViewModel.kt:327-335` 清 5 字段。外部请求经 `PagedReaderControllerEffects.kt:92-96` 汇入同一 `turnRequest`。**[未验证]** 硬件键在真机上是否确实落到 `turnRequest`（静态链条完整，无设备证据） |
| 3 | 上一章 / 下一章 / 目录选择清旧选区与旧 source 坐标 | **成立** | 跨章：`LoadChapter` 分支 `ReaderViewModel.kt:300` 无条件清。纯 TXT 滚动绕过 `LoadChapter` 的两条路显式补清：`ReaderLayerBuilders.kt:301-305`（上一章）、`:311-315`（下一章）、`:566`（目录 `PlainOffset`），且都以"真的有目标"为前提 |
| 4 | 普通点按取消选区仍不翻页 | **成立** | `PagedReaderPageSurface.kt:341-345` 的"有选区时任何点按先撤选区，不翻页"分支**一字未改**（该文件本轮 delta = 0，见 Scope integrity）；只是 `onSelect("", -1, -1)` 现在收敛到 `ClearSelection`，所以色板行也跟着收起 —— 这是修复目标而非副作用 |
| 5 | 页/章没有实际变化时不得伪装"已换页" | **基本成立，一处行为与用例名相反** | 分页点按：`resolveReaderTapAction(canPrevious = frameAt(-1) != null, canNext = frameAt(1) != null, ...)`（`PagedReaderPageSurface.kt:355-359`）边界返回 `NONE`，`PREV/NEXT_PAGE` 分支不进、`onGesturePageTurn` 不派 → 不清；新用例 `boundaryPagesReturnNoneSoNoTurnIsReported` 钉住。旧 EPUB 边界热区 `clickable(enabled = canPrev/canNext)`（`PagedEpubContent.kt:253/282`）同理。**例外见 ST-1** |
| 6 | 不得存在只清文本不清坐标/色板行的半清状态 | **成立** | `grep "selectedText = \"\"\|selectedRangeStart = -1\|showColorRow = false"` 在整个 `src/main` 只命中 `ReaderViewModel.kt:88/89/92` 三行，即全部在 `withSelectionCleared()` 体内。原先的两处手写清单（`ClearSelection` 10 行、规则保存成功 9 行）已收敛为各 1 行调用 |
| 7 | 不得误清搜索 / 高亮 / 笔记等仍需消费选中文字的动作 | **成立** | `OpenSheet` / `CloseSheet` reducer 分支未加清除（`withSelectionCleared` 的 5 个调用点是 `:300 LoadChapter`、`:320 CenterTap`、`:334 PageTurn`、`:364 ClearSelection`、`:486 规则保存成功`）；书内搜索仍先快照再清（`ReaderSelectionToolbar.kt:127-131` = `SetSearchQuery` → `ClearSelection` → `OpenSheet`）；用例 `clearing on a page turn preserves the new reading position` 断言 chrome 可见性 / `searchQuery` / `sheet` 不受影响 |
| 8 | 不得夹带 Markdown / EPUB / 复制 / edit / AI / 替换净化 | **成立（已证明）** | 见 Scope integrity：patch-to-patch diff 的**全部**新增行只有选区清除、`txtScrollChapterTargetOffset()`、`DocChapter` import 三类 |
| 9 | before/after patch 是否足以证明未动同文件其它 hunk | **足以证明（本轮升级为可证）** | `after.patch` 与现场 `git diff` 逐字节相同（1345 行）；`before.patch` mtime `10:05` 早于首次源码写入 `10:35`，且其中 `withSelectionCleared` 出现 0 次（after 为 6 次）；三个"授权但未改"文件的 patch 段 delta 为 0 |
| 10 | 测试是否真正执行、类名与数量是否从 XML 核对 | **成立** | 见 Verification：`--rerun` 强制执行，6 类 119 tests 全绿，20 条新增/改写用例名逐条在 XML 中确认 |

### ST-1 [P2] 一条用例的名字与它自己的断言相反，会把复核者引向错误结论

`TxtScrollChapterTargetTest.kt:36-39`：

```kotlin
fun `the first chapter has no previous target`() {
    // 首章章首为 0 → 0-1 钳到 0，仍是可跳目标（回到本章开头）。
    assertEquals(0, txtScrollChapterTargetOffset(-1, chapters(0, 1_000), 0))
}
```

方法名说"首章没有上一章目标"，断言却是 `0`（**有**目标）。注释解释了原因：`ReaderProgressActions.kt` 的 `-1` 方向实现是 `(startOffset - 1).coerceAtLeast(0)`，首章 `startOffset = 0` 被钳回 `0` 而非 `null`。

真实用户后果 **[事实 + 推断]**：在 TXT 滚动模式停在第一章开头、且正有选区时点「上一章」，会先 `ClearSelection` 再 `jumpToPlainOffset(0)`。位置确实变化（滚回章首），所以清选区本身站得住；但任何只看用例清单的人都会以为这个边界被挡住了 —— 与检查项 5 的判断直接相关，所以它不是纯命名问题。

最小修正建议（二选一，都不需要碰产品逻辑）：

- 改名为 `the first chapter previous target falls back to the chapter start`；若产品契约认为"首章点上一章不该有动作"，则把实现改成 `startOffset == 0 -> null` 并加一条 `assertNull` 用例。
- 无论选哪个，都应在该用例里补一句显式断言当前行为对选区的影响（`ClearSelection` 是否被发），否则"名字与行为不符"会在 S07 复盘时再次被误读。

### ST-2 [P2] 非 reveal 点按路径的 `PageTurn` 发生在页码提交**之前**

`PagedReaderPageSurface.kt:364-377`：`ReaderTapAction.PREVIOUS_PAGE` / `NEXT_PAGE` 在 `pageTurnRenderer != OUTER_REVEAL` 时是

```kotlin
onGesturePageTurn()          // ← 先派 PageTurn（现在会清 5 字段）
turnRequest.intValue = -1    // ← 后请求翻页
```

而 `requestManualReveal` 的降级分支（`:200-202`）同样是 `controller.prevPage()` + `onGesturePageTurn()`。

后果 **[推断]**：如果 `turnRequest` 最终没有产生实际提交（例如请求被 `PageTurner` 内部条件挡下、或 `frameAt` 在判定与消费之间发生变化），选区已经先被清掉了 —— 用户白丢一次选择。这正是检查项 5 想防的"没换页却执行了已换页逻辑"的反方向变体。

同时要给 Zcode 的 §3.2 记一笔修正：那里的表述"清除发生在页码提交之后（`onPageTurned`）"只对 `OUTER_REVEAL` / `PageTurner` 路径成立，对上面这条点按路径**不成立**。

最小修正建议：把 `:366-367` / `:375-376` 的 `onGesturePageTurn()` 移到提交回调里（即与 `OUTER_REVEAL` 路径一致，统一由 `onPageTurned` 派发），或保留现状但补一条用例断言"turnRequest 未产生提交时不清选区"——若选择保留现状，则应在 `PageTurn` 分支 KDoc 里写明"点按路径为**乐观清除**"，别让后来的读者按 §3.2 的理解去改代码。

### ST-3 [P2] VM→宿主回流仍无运行期断言（Zcode 已在 §7 主动披露）

`selectionCleared = selectedText.isBlank()`（`ReaderContentHostPagedBranch.kt:126`）→ `PagedReaderControllerEffects.kt:91` 撤本地 `selRange` 这条链只有静态证据。它是本轮修复的必要一环（VM 清了、屏幕上旧色块还画着的话，用户看到的仍是残留），但整轮没有任何测试能发现它被改断。

最小可测化建议（不引入 Robolectric）：把这个判断抽成纯函数并在两个宿主分支共用，例如

```kotlin
internal fun shouldDropLocalSelection(vmText: String, local: IntRange?): IntRange? =
    if (vmText.isBlank()) null else local
```

然后断言"VM 文本为空 + 本地有区间 → 区间被丢弃""VM 文本非空 → 区间保留"。这样回流被改坏时 JVM 门禁就能发现，而不是等真机。

### ST-4 [P3] `text.isBlank()` 把"纯空白选区"从可见工具条变成静默清除

`ReaderLayerBuilders.kt:122-126` 用 `isBlank()` 而非 `isEmpty()`。用户选中纯空白（段落间空格、`'\n'`）时，现在不发 `SetSelectedText` 而是直接 `ClearSelection`，若色板行正开着也会一并收起。 **[事实]** 这是行为变化，但方向合理（对空白文字做高亮/复制本来就是坏主意）。 **[未验证]** 4 条接线用例只断言"空白 → ClearSelection"，没有任何用例把"用户此刻看到什么"写成契约；滚动模式 `BasicTextField` 真的会报上空白选区还是根本选不到，需要真机确认。建议按现状保留，在正式报告里记一行"空白选区不可用"即可，不必回退。

---

## Spec

### SP-1 用户可见变化（我核对过的）

1. 选一句 → 滑动翻页 → 工具条消失、色板行收起、VM 里四个坐标全部回 `-1`；不会再出现"工具条浮在新页上、点高亮把上一页的字落到新页坐标"。 **[事实（VM 层）]** + **[未验证（屏幕层，见 SP-3）]**
2. 硬件键 / 音量键 / 自动翻页 / 外部翻页请求 → 与手势同一条 `PageTurn` 通道（`PageTurner.kt:289-290`、`:355-356` 两处都跟 `onPageTurned()`）。 **[事实（代码）]**
3. 目录跳章、上一章 / 下一章（EPUB / Markdown 经 `LoadChapter`，纯 TXT 经新纯函数判目标）→ 清选区。 **[事实]**
4. 有选区时点正文：撤选区且**不**翻页（该分支未动），但现在连色板行一起收起（以前会留在打开态）。 **[事实]**
5. 边界：分页点按在首/末页返回 `NONE` 不派 `PageTurn`（新增用例锁定）；旧 EPUB 边界热区 `clickable(enabled = ...)` 同样不触发。 **[事实]** 例外见 ST-1（TXT 首章点"上一章"仍会清并滚回章首）。

### SP-2 仍未闭合的同类缺口（Zcode 已披露，我独立确认，登记为开放项而非 F2 范围违规）

**同章内的程序化跳转不清选区**，而这按 F2 自己定的不变量（"页面/章节身份变化才清"）属于"页面身份变化"：

- `PagedReaderControllerEffects.kt:84-88`：`LaunchedEffect(jumpRequest.value) { controller.open(j) }` —— 目录/进度条在同章内跳页时走这里，既不发 `PageTurn` 也不发 `ClearSelection`；
- 结果：同章跳页后本地 `selRange` 仍在，`selRange` 是**章内 display 偏移**、与新页的可见区间无关，于是 `rectsForRange` 会在新页上画出无意义的色块与把手 **[推断]**；此时保存高亮用的仍是旧区间。

Zcode §7 第 5、6 条把"搜索跳转 / 高亮回源 / `pagedJumpRequest` 同章内跳转"列为未处理，方向正确；但 §2.2 的表把 `LoadChapter` 写成"及搜索跳转、高亮回源"，容易读成"这些也都清了"。精确说法应是：**只有跨章的那部分经 `LoadChapter` 清，同章内跳转一条都没清**。

最小补齐建议（下一片，不改本轮结论）：给 `jumpRequest` 分支补 `selectionCleared` 同源的清除（最省事是在 `controller.open(j)` 成功后发 `ReaderAction.ClearSelection`），并把不变量从"章身份变化"改写为"可见页/单元身份变化"，否则后续任何新增跳转入口都会继续漏。

### SP-3 明确不得当作已通过

- **一切屏幕层结论都未验证**：翻页后工具条是否真的不再滞留、跳章后是否真的没有残留色块、`selRange` 回流是否真的执行 —— 只有 JVM 状态级与 lambda 接线级证据。
- **Markdown 滚动仍无选区入口、旧 EPUB 仍是整块点选、两者仍不重渲染已存高亮**（正式报告 Spec P1 第 4 项 / P2）：本轮一字未动，状态未变。
- **真机小屏 5 槽拥挤、字体放大、触摸目标、TalkBack 焦点与手柄语义**：未验证，归 WorkBuddy。
- **`selectedSourceLength` 半清错值是否真的发生过**：本轮把产生它的 CenterTap 半清路径修成完整清除，但该错值本身没有独立复现（Zcode §7 第 7 条同样承认）。

---

## Verification

### 我亲自执行的命令

```bash
cd android && ./gradlew --console=plain :app:testDebugUnitTest --rerun \
 --tests "com.creationreadingassistant.ui.viewmodel.ReaderViewModelTest" \
 --tests "com.creationreadingassistant.ui.screen.reader.ReaderContentHostSelectionCallbacksTest" \
 --tests "com.creationreadingassistant.ui.screen.reader.TxtScrollChapterTargetTest" \
 --tests "com.creationreadingassistant.feature.reader.pager.ReaderTapActionTest" \
 --tests "com.creationreadingassistant.ui.screen.reader.ReaderCopyNoticeTest" \
 --tests "com.creationreadingassistant.data.settings.SelectionActionsTest" \
 :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

- 退出码：**0**；`BUILD SUCCESSFUL in 38s`
- `> Task :app:testDebugUnitTest` 真实执行（`--rerun`，非 UP-TO-DATE）
- `:app:compileDebugKotlin`、`:app:compileDebugAndroidTestKotlin` 均 UP-TO-DATE —— 含义是"当前源码内容此前已被成功编译"，本轮没有产生新编译动作。**[事实]** 我特意跑了 androidTest 编译，因为 F2 改了 `ReaderLayerBuilders.kt`（仪器测试依赖的构造参数所在文件），结论：仪器测试面未被改坏。
- 后两个类（`ReaderCopyNoticeTest`、`SelectionActionsTest`）是我额外加的 F1 回归哨兵，理由是 F2 改动的 `ReaderLayerBuilders.kt` 正是 F1 复制修复的宿主文件；Zcode §5.4 也跑了同类回归，结论一致。

### XML 结果（`android/app/build/test-results/testDebugUnitTest/TEST-*.xml`）

| 测试类（XML `testsuite/@name` 原值） | tests | skipped | failures | errors |
|---|---:|---:|---:|---:|
| `com.creationreadingassistant.ui.viewmodel.ReaderViewModelTest` | 72 | 0 | 0 | 0 |
| `com.creationreadingassistant.ui.screen.reader.ReaderContentHostSelectionCallbacksTest` | 4 | 0 | 0 | 0 |
| `com.creationreadingassistant.ui.screen.reader.TxtScrollChapterTargetTest` | 7 | 0 | 0 | 0 |
| `com.creationreadingassistant.feature.reader.pager.ReaderTapActionTest` | 9 | 0 | 0 | 0 |
| `com.creationreadingassistant.ui.screen.reader.ReaderCopyNoticeTest` | 3 | 0 | 0 | 0 |
| `com.creationreadingassistant.data.settings.SelectionActionsTest` | 24 | 0 | 0 | 0 |
| **合计** | **119** | **0** | **0** | **0** |

- 请求 6 个类 = 结果 6 个 XML，无缺类（零匹配防护）。
- 其中 F2 的 4 个类 = **92 tests**，与 Zcode §5.1 的 72/4/7/9 **逐类一致**。 **[事实]**
- F1 的 2 个类 = 27 tests 仍全绿 → F2 没有把 F1 的成果改坏。

### 用例名逐条核对（读自 XML `testcase/@name`，确认不是恒真占位）

F2 新增 20 条全部在场，包括 4 条接线用例（`an empty selection signal maps to the single full clear action`、`a blank but non empty string still counts as a clear`、`a real selection still maps to SetSelectedText with all four coordinates`、`an epub style selection keeps its global offset`）与 6 条 VM 状态用例（含 `clearing on a page turn preserves the new reading position`、`a page turn without a selection is a harmless no-op on selection state`）。

我另外读了断言体，确认其非空洞：`ReaderViewModelTest.kt:1116-1123 assertSelectionFullyCleared()` 对 5 个字段逐个断言（含 `assertFalse(showColorRow)`），`seedOpenSelectionWithColorRow()`（`:1126-1130`）先造出"四坐标 + 色板行全开"的前置态并自证前置成立；`ReaderContentHostSelectionCallbacksTest` 用 `buildReaderContentHostCallbacks(onAction = { recorded += it })` 记录真实发出的 action 列表后比对 —— 这就是我在 F1 报告 ST-3 里要求的"接线级"保护形态，本轮确实建立起来了。 **[事实]**

### RED（修复前失败）我未独立复现

Zcode §4.1 报出 `76 tests completed, 8 failed`、退出码 1。我用静态方式验证其可信性：`before.patch` 中 `withSelectionCleared` 出现 **0 次**，因此 `assertSelectionFullyCleared` 类断言在修复前必然失败（`PageTurn` 分支当时只跑 chrome reducer）。**[推断]** 我没有回退源码去重跑 RED（任务默认只读）。

值得表扬的一点诚实登记：Zcode §4.1 自己说明那 8 条里有 2 条是它一开始把 `onSelect` 的中间两参写反、修好期望值后在未修复代码上也会通过，真正的 RED 只有 6 条。我核对了它修正后的期望值与真实签名（`ReaderLayerBuilders.kt:121 onSelect = { text, globalOffset, rangeStart, sourceEnd }` → `SetSelectedText(text, rangeStart, globalOffset, sourceEnd)`），换位描述属实，两条"选择分支"用例确实是在钉既有契约而非假装 RED。

### 工作区完整性

```
git status --porcelain          # 我开工前 318 → 收工后 318（未跟踪目录被折叠成一行，此级不精确）
git status --porcelain -uall    # 我开工前 335 → 收工后 336，唯一新增行是本报告
git diff --cached --name-only   # 0 个文件（未 stage）
git stash list                  # 空
```

因为折叠计数会掩盖真实变化，我另做一次 mtime 取证：把 `-uall` 列出的所有仓库内文件与 Zcode 收工时刻（`10:48`）比对，**我这段验证期间唯一变化的仓库文件就是我自己的报告**：

```
TOUCHED docs/coordination/reviews/undated/agent-review-s07-f2-qoder.md  10:58:46
```

Gradle 只写 `android/app/build`、`android/.gradle`（`android/.gitignore:2-3` 已忽略，故不在此列）。未跑全量 JVM / Lint / APK / 设备测试，未使用 `--rerun-tasks`（避免全仓重建）。

---

## Scope integrity

### 本轮可证明（不再只是推断）

Zcode 把开工/收工快照留在仓库外，我直接对两份 patch 做了我自己的 diff：

```
/tmp/s07f2-before.patch   66446 bytes  mtime 10:05
/tmp/s07f2-after.patch    75820 bytes  mtime 10:44  = 1345 行
我现场重跑 git diff origin/main -- <6 个授权文件>   = 1345 行，与 after.patch `diff -q` 相同
```

1. **after 快照是真实的**：与当前工作树的 `git diff` 逐字节一致 → 它没有事后被改写过。
2. **before 快照早于任何改动**：mtime `10:05`，而本轮第一次测试文件写入 `10:35`、第一次源码写入 `10:37`；`before.patch` 中 `withSelectionCleared` 计数为 0（after 为 6）。
3. **F2 的真实增量 = `diff before after`，其全部 `+` 行只有三类内容**：
   - `ReaderViewModel.kt`：`withSelectionCleared()` + 5 个调用点收敛（`LoadChapter` / `ToggleControls-CenterTap` / `PageTurn` / `ClearSelection` / 规则保存成功）；
   - `ReaderLayerBuilders.kt`：`onSelect` 空文本 → `ClearSelection`；TXT 上一章 / 下一章 / 目录 `PlainOffset` 三处按需补清；
   - `ReaderProgressActions.kt`：`txtScrollChapterTargetOffset()` 纯函数 + `DocChapter` import。
   
   我在 delta 里**没有看到**任何与选区清除无关的新增行：没有 Markdown / EPUB 渲染改动，没有复制提示（F1 的 `readerCopyNotice` 只作为未变上下文出现），没有 `edit` / `DORMANT_IDS`（F1 产物）改动，没有 AI、替换净化、TTS、页眉页脚、日夜灯绳的改动。
4. **三个"授权但声称无需改动"的文件 delta 为 0**：`PagedReaderPageSurface.kt`、`PagedTxtReaderHost.kt`、`ReaderContentHostPagedBranch.kt` 在 patch 里逐字节相同；这条与独立的 mtime 取证一致 —— 这 3 个文件都不在 `10:35` 之后被修改的文件集合里。
5. **mtime 取证与申报清单吻合**：`10:20` 之后被写入的仓库文件恰好是 3 个源文件 + 4 个测试文件 + 1 份报告，无第 8 个。

### 唯一残留的不确定性（如实说明）

"before 快照是 10:05 生成的"这一点本身依赖文件 mtime，而 mtime 理论上可被改。若要不依赖 mtime，可让 before 快照的 SHA-256 出现在**该轮任务书**（Zcode 无法事后编辑的文件）里。 **[推断]** 但这只是流程加固建议：我已用 `after == 现场 git diff` 与"before 中 0 次出现 withSelectionCleared"两条独立约束交叉，伪造的成本与收益不成立，因此我按"可信"判定。

### staged / 提交面

`git diff --cached --stat` 为空（我自己跑过），未 stage、未 commit、未 push、未建分支或 worktree；未 `reset` / `checkout` / `clean` / `stash`；`git stash list` 为空。我也未读写 Zcode 的报告内容（只读取，未修改）。

---

## Verdict

# PASS

S07-F2 的 10 项检查全部成立：五个选区字段被收敛到唯一一处 `withSelectionCleared()`（全仓再无第二处字段清单，半清状态在源码层面已不可能重新出现）；四类翻页入口经 `PageTurner` 的 `onPageTurned()` 汇入同一 `PageTurn` 语义；跨章与 TXT 绕过 `LoadChapter` 的两条路都补齐；普通点按撤选区仍不翻页；分页与旧 EPUB 边界不会伪装成换页；仍需消费选区的弹层动作没有被误清；夹带项为 0（本轮有 patch 级证明）；测试真实执行且断言不空洞。

随附 4 条跟进项，均不构成回退理由：

- **ST-1 [P2]**：用例名 `the first chapter has no previous target` 与断言相反（实际有目标 = 0），必须改名或改实现，否则下个复核者会误判边界已守住。
- **ST-2 [P2]**：非 `OUTER_REVEAL` 点按路径的 `PageTurn` 早于页码提交，属"乐观清除"；建议统一到 `onPageTurned`，或在 KDoc 里写明现状以免被误改。
- **ST-3 [P2]**：VM→宿主 `selRange` 回流仍无运行期/纯函数断言（Zcode 已披露）。
- **SP-2 [下一片]**：同章内的程序化跳转（`jumpRequest` / `controller.open(j)`）仍不清选区，按 F2 自定的"页面身份变化"不变量属于未闭合缺口；Zcode 已披露但 §2.2 的表述容易误读。

### PASS 的边界（不代表以下内容通过）

1. 这只代表 **S07-F2 的选区状态清理**这一件事，且只在 **JVM 状态层与 lambda 接线层**成立。
2. **不代表翻页/切章残留已在真机上修好**：屏幕层滞留、旧色块、`selRange` 回流执行、硬件键真实行为，全部未验证 → WorkBuddy。
3. **不代表 Markdown、旧 EPUB 选区能力已通过**（滚动无入口 / 整块点选）——本轮一字未动。
4. **不代表持久高亮重渲染、`selectedSourceLength` 错值、窄屏 5 槽拥挤、字体放大、触摸目标、TalkBack 已通过**。
5. **不代表同章内跳转的选区清理已完成**（SP-2）。
6. 不代表整个 S07 可以发布，也不构成任何"切片完成 / 可以发布"的结论；正式报告 Spec P1 第 4 项与全部 P2 仍在清单内。

**未领取下一任务。** S08 及看板队列其余切片保持 `HOLD`，等待 Codex 主任务复核与放行。
