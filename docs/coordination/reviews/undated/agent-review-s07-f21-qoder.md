# S07-F2.1 独立复核（Qoder）

- 复核 Agent：Qoder（模型 `qwen3.8Flash`）
- 复核对象：S07-F2.1 —— 关闭 F2 复核留下的 A（同章程序化跳转）、B（提前/重复派发 `PageTurn`）、C（用例名与回流断言）
- 工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`；分支 `codex/recover-worktree-20260920`
- 已读输入：`AGENTS.md`、`docs/coordination/agent-results/agent-result-s07-f21-zcode.md`、`docs/coordination/reviews/undated/agent-review-s07-f2-qoder.md`（我上一轮那份）、3 个改动源文件与 3 个测试文件的当前内容、Zcode 的 before/after patch（我独立重算哈希并自己做了 diff）
- 前置确认：Zcode 结果文件 mtime `14:25`；其源码/测试写入集中在 `14:05–14:18`；`14:00` 的 before 快照早于本轮第一次写入，且 `11:16–14:00` 之间无任何仓库文件被改动（mtime 扫描为空）
- 标注：**[事实]** 我读到的代码或跑过的命令；**[推断]** 静态推导；**[未验证]** 需真机或 Compose 运行期

---

## Standards

### 10 项检查逐条判定

| # | 检查项 | 判定 | 一手证据 |
|---:|---|---|---|
| 1 | `controller.open(j)` 前后是否比较真实页面/章节身份 | **成立** | `PagedReaderControllerEffects.kt:150-162`：在 `open` **之前**读 `controller.currentPageRangeAbs`，交给 `applyProgrammaticJump`。区间口径我独立核对过：`TxtPagedController.kt:104-110` 返回 `start until (end > start ? end : start + 1)`，即**半开区间**，与新用例 `a jump to the exact current page start reports no change`（:45-52，取 100 与 199）以及 `a jump just past the current page reports an identity change`（取 200 → true）一致 —— 判定与真实页边界同向，没有 off-by-one |
| 2 | 只有身份真正变化时才发送**一次**失效事件 | **成立** | `readerJumpClearsSelection:32-35` = `range != null && target !in range`；`applyProgrammaticJump:49-61` 结构上是"判定 → open → 条件通知"，单次调用最多通知一次；调用点 `:155-161` 每次 `jumpRequest` 只进一次并立即置 null（`:161`）→ 恰好一次由结构而非状态保证 |
| 3 | 目标仍在当前页 / 边界 / 失败 / 无变化时不清 | **成立** | 三种 false 情形都有用例：同页内（`:96-110`，`open` 仍执行 1 次、通知 0 次）、无可见页 `null`（`:113-127`）、目标恰为页首/页末（`:45-52`）。单位一致性另查：`jumpRequest` 的全部写入方（`ReaderProgressActions.kt:83/111/328/330/343/374/383`）传的都是全书绝对偏移，与 `currentPageRangeAbs` 同一空间 **[事实]** |
| 4 | 非 reveal 点按是否删掉提前 `PageTurn`，改由真实提交回调统一派发 | **成立** | `PagedReaderPageSurface.kt:405-424` 点按分支现在只产出 `readerTapTurnPlan(...)`，原 `onGesturePageTurn()` + `turnRequest = d` 的写法已删除（`:409` 注释记录了修复前形态）。全文件现存 `onGesturePageTurn` 调用只有 3 处：`:258`（跨章未预排的降级分支，`controller.prev/nextPage()` 之后）、`:271`（reveal 动画结束帧，`controller.nextPage()` 之后）、`:562`（`onPageTurned`）—— **全部在页码变更之后** |
| 5 | OUTER_REVEAL / 硬件键 / 自动翻页 / 跨章是否仍只有一次 `PageTurn` | **成立，并顺带修掉了我上轮没点明的"重复"** | 非 reveal 点按：修复前是"提前一次 + `onPageTurned` 再一次"= 2 次，现在是 1 次；reveal 点按：`ManualReveal(d)` → `requestManualReveal` 内部单点（`:271`），且 `manualRevealTarget != null` 挡重入（`:252`）；硬件键经 `externalTurnRequest → turnRequest`（`:166-170`）、自动翻页经 `turnRequest`（`:205`）→ 都由 `PageTurner` 提交后 `onPageTurned()` 派发 **[事实：代码]**。自动翻页另有一条 `controller.nextPage()` 直连分支（`:208`，相邻章未预排时）不发通知，但该分支只在自动翻页推进时到达，而自动翻页在 `selectedText.isNotBlank()` 时是暂停的（`ReaderScreen.kt:450-456`），因此不存在"有选区时走这条不清"的窗口 **[推断]** |
| 6 | 本地 `selRange` 的清除/保留是否有可执行测试 | **成立** | `shouldDropLocalSelection:70-73` 纯函数 + `PagedReaderSelectionInvalidationPolicyTest.kt:132-151` 四条（清除→丢弃、无本地区间→仍 null、未清除→原样保留、两边都空→保持空）。并且 `:165` 的 `LaunchedEffect(selectionCleared)` 现在**必须调用这个被测函数**才能编译 —— 接线与断言被编译期绑在一起，比我上轮设想的"再抽一层"更强 **[事实]** |
| 7 | 错误测试名是否修正且没偷改产品契约 | **成立** | `TxtScrollChapterTargetTest.kt:37-48`：改名为 `the first chapter previous target falls back to the chapter start`，断言仍是 `assertEquals(0, target)`（行为一字未变），并补 `assertNotNull` + 注释写明"首章点上一章仍有目标，调用方据此清选区，别误读成边界已挡住"。产品契约判断（对齐 `ReaderProgressActions.kt:79` 的 `i.coerceIn(0, maxIndex)`，两条路径同向）我核对为**成立**，属"只改名不改行为"的正确取舍 |
| 8 | F1、F2 回归是否仍通过 | **成立** | 我自己重跑：F2 三类 72+4+9、F1 四类 3+24+13+9 全绿（见 Verification） |
| 9 | before/after patch 哈希与增量能否证明未改同文件其它 hunk | **能，证明力比 F2 更强** | 见 Scope integrity：SHA-256 与申报值逐字相符，after 快照与我现场 `git diff` 逐字节相同，两个"授权但未用"文件的 patch 段我做独立提取比对为 `IDENTICAL` |
| 10 | 是否夹带 Markdown / EPUB / 复制 / edit / AI / 替换净化 | **无夹带** | 本轮 delta 的全部新增行里，与产品语义相关的标识符只出现 `PageTurn` / `ReaderTapTurnPlan` / `readerTapTurnPlan` / `onGesturePageTurn` / `readerJumpClearsSelection` / `applyProgrammaticJump` / `shouldDropLocalSelection` / `onSelectionInvalidated`；对 `markdown\|epub\|copyText\|DORMANT\|edit\|aiExplain\|replace\|tts\|quote\|han\|trad` 的匹配数为 **0**（我自己跑的 grep） |

### ST-1 [P2] 复用 `PageTurn` 当"选区失效"事件，把两件事耦合了：同章跳页现在还会顺手隐藏阅读页菜单栏

链路：`PagedTxtReaderHost.kt:308 onSelectionInvalidated = onGesturePageTurn` → `ReaderContentHostPagedBranch.kt:87` → `ReaderLayerBuilders.kt:118 onHideControls = { onAction(ReaderAction.PageTurn) }` → `ReaderViewModel.kt:327-335`（chrome reducer **和** `withSelectionCleared()`）。

**[事实]** `PageTurn` 一直都会隐藏控制栏 —— 这不是新写的逻辑，而是 F2 用例 `ReaderViewModelTest.kt:1151` 明确断言的行为（`assertFalse(controlsVisible)`）。所以：用户拖动底栏进度条在同章内跳页、或点同章的搜索命中/高亮回源，现在除了清选区还会立刻收起顶栏+底栏；而**跨章**跳转走 `LoadChapter`（`:296-301` 只清选区、不动 chrome）。两条"看起来都是跳转"的路径对菜单栏的处理从此不一致。

这不影响选区正确性（本轮目标已达成），所以我不据此判 FAIL；但它是本轮新引入的用户可见副作用，而 Zcode §9「未验证项」和 §4「为什么不会提前/重复/误清」两张表里**都没有列它** —— 属于披露遗漏，不是隐瞒动机问题，因为它就写在自己 §1 的"复用同一条语义"里。

最小修正建议（一处，不改任何判定逻辑）：把失效通知接到**只清选区**的通道上，让 `PageTurn` 继续只表示"真的换页了"：

```kotlin
// PagedTxtReaderHost.kt:308
onSelectionInvalidated = { onAction(ReaderAction.ClearSelection) },   // 与 PageTurn 共用 withSelectionCleared()（ReaderViewModel.kt:364）
```

`ClearSelection` 在 VM 侧调的就是同一个 `withSelectionCleared()`（`:87-93`、`:364`），所以选区语义完全不变，只去掉菜单栏副作用；需要宿主能拿到 `onAction`（`PagedReaderHost` 已有 `onSelect`/`onGesturePageTurn` 这类回调，加一个参数即可）。若 Codex 认为"跳页后收起菜单栏"本就是想要的行为，那就把它写进 `PageTurn` 分支的 KDoc 与产品盘点，别留给下一个人当惊喜。

### ST-2 [P3] 判定依赖 `open()` 的确定性（读的是 open 之前的区间）

`readerJumpClearsSelection` 用"跳转前的当前页区间"预测"跳转后身份是否变化"，成立的前提是 `open()` 一定会把目标落在**包含该偏移的那一页**上。Zcode §9 第 3 条自己披露了这一点，方向正确。我补一条 **[推断]**：即使 `open()` 因排版未就绪而没换页，最坏结果是"多清一次选区"而不是"漏清"，属安全侧偏移；但若将来 `open()` 变成异步或依赖未就绪状态，这个判定会静默失效 —— 建议在 `applyProgrammaticJump` 的 KDoc 里把这条前提写成断言性注释（现在写在 `readerJumpClearsSelection:20-22`，位置合理，够用）。

### ST-3 [P3] 变异测试我无法独立复现（需改产品代码，任务只读）

Zcode §5 用三次变异给出 RED 证据，并声明已全部回滚且 `diff` 确认逐字节相同。我没有重复变异（禁止修改产品源码），但**非空洞性我可以不依赖它的证据直接确认**：`a jump across pages... notifies exactly once` 断言 `invalidations == 1`，另外三条断言 `== 0`，同一组用例对"漏清"和"误清"两个方向同时有约束 —— 恒真断言不可能同时钉住两个方向。**[事实（读断言体）]**

至于"变异已回滚"：我用 patch 快照做了更强的等价验证 —— after 快照与我现场 `git diff` 逐字节相同，说明工作树里没有变异残留（若有残留，快照与现树会分叉）。**[事实]**

---

## Spec

### SP-1 用户可见变化（我核对过的）

1. 进度条 / 目录 / 搜索命中 / 高亮回源在**同章内跳页**后：旧选区、色板行、四个坐标全部清掉，本地 `selRange` 随之丢弃，不会再在新页上画旧色块。 **[事实（VM+纯函数层）]** / **[未验证（屏幕层）]**
2. 目标仍落在当前页、或排版未就绪没有可见页时：**不清**，用户刚选的字保留。 **[事实]**
3. 点按翻页：不再"提前清一次 + 提交后再清一次"，只在真正提交后清一次。 **[事实（代码路径）]**
4. 首章点「上一章」：仍会滚回章首并清选区（行为未变，用例名已纠正）。 **[事实]**
5. 边界页点按（首页点左缘 / 末页点右缘）：`resolveReaderTapAction → NONE` → `readerTapTurnPlan → None` → 不登记请求、不派发、不清。 **[事实]**，并由 `a boundary tap never produces a turn plan` + `ReaderTapActionTest.boundaryPagesReturnNoneSoNoTurnIsReported` 两条共同钉住。
6. **新增副作用**：同章跳页后阅读页顶栏/底栏会收起（ST-1），跨章跳页不会。 **[事实]**

### SP-2 仍未闭合的同类缺口（不是 F2.1 的范围违规，登记为开放项）

- 滚动模式的 ReadingUnit 身份变化（大 TXT 流式分段滚动时单元被回收/复用）不经过 `jumpRequest` 也不发 `PageTurn`；其选区去留仍依赖 `ReaderContentHostPlainTextBranch.kt` 那条带身份门的清除条件（我上一轮内部地图标为 H 项）。本轮授权范围是**分页**引擎的程序化跳转，未触碰它，因此该缺口状态未变。**[事实：本轮该文件不在改动清单内]**
- 「任何跳转都该清选区」还是「只有可见页/章身份变化才清」这条产品契约，目前实现选的是后者。若首版想选前者，需要另立切片。

### SP-3 明确不得当作已通过

- 真机面全未验证：翻页/跳页后是否真的没有残留色块、菜单栏收起是否符合预期、硬件键与自动翻页的真实行为、`selRange` 回流的运行期执行。
- Markdown 滚动仍无选区入口、旧 EPUB 仍是整块点选、两者仍不重渲染已存高亮（正式报告 Spec P1 第 4 项 / P2）：本轮一字未动。
- 小屏 5 槽拥挤、字体放大、触摸目标、TalkBack 焦点与手柄语义：未验证，归 WorkBuddy。
- `selectedSourceLength` 半清错值是否真的发生过：仍未独立复现（F2 遗留）。

---

## Verification

### 我亲自执行的命令

```bash
cd android && ./gradlew --console=plain :app:testDebugUnitTest --rerun \
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

- 退出码：**0**；`BUILD SUCCESSFUL in 39s`
- `> Task :app:testDebugUnitTest` 真实执行（`--rerun`），不是 UP-TO-DATE
- `:app:compileDebugKotlin`、`:app:compileDebugUnitTestKotlin`、`:app:compileDebugAndroidTestKotlin` 均 **UP-TO-DATE** —— 含义是"当前源码内容此前已被成功编译"（Zcode §5.5 的 `--rerun-tasks` 全量执行过 30 个任务）。本轮我没有产生新的编译动作，这一点如实记为"编译面成立但非本轮重跑"。**[事实]**

### XML 结果（`android/app/build/test-results/testDebugUnitTest/TEST-*.xml`，取自我自己这次运行）

| 测试类（XML `testsuite/@name` 原值） | 归属 | tests | skipped | failures | errors |
|---|---|---:|---:|---:|---:|
| `feature.reader.pager.PagedReaderSelectionInvalidationPolicyTest` | F2.1 新增 | 12 | 0 | 0 | 0 |
| `feature.reader.pager.ReaderTapTurnPlanTest` | F2.1 新增 | 5 | 0 | 0 | 0 |
| `ui.screen.reader.TxtScrollChapterTargetTest` | F2.1 改写 | 7 | 0 | 0 | 0 |
| `ui.viewmodel.ReaderViewModelTest` | F2 回归 | 72 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderContentHostSelectionCallbacksTest` | F2 回归 | 4 | 0 | 0 | 0 |
| `feature.reader.pager.ReaderTapActionTest` | F2 回归 | 9 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderCopyNoticeTest` | F1 哨兵 | 3 | 0 | 0 | 0 |
| `data.settings.SelectionActionsTest` | F1 哨兵 | 24 | 0 | 0 | 0 |
| `data.settings.SelectionActionStoreTest` | F1 哨兵 | 13 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionToolbarModelTest` | F1 哨兵 | 9 | 0 | 0 | 0 |
| **合计** | | **158** | **0** | **0** | **0** |

- 请求 10 个类 = 结果 10 个 XML 文件，无缺类（零匹配防护）。
- 总数与 Zcode §6.1 的 **158** 逐类一致，含 F2 的 85 项与 F1 的 49 项。 **[事实]**

### 用例名核对（读自 XML `testcase/@name`）

F2.1 新增/改写在场的代表项：

```
PagedReaderSelectionInvalidationPolicyTest :: a jump across pages opens the target and notifies exactly once
                                            :: a jump within the current page opens but never notifies
                                            :: a jump with no visible page opens but never notifies
                                            :: a jump to the exact current page start reports no change
                                            :: a jump just past the current page reports an identity change
                                            :: a cleared vm selection drops the local range
                                            :: a live vm selection keeps the local range untouched
ReaderTapTurnPlanTest                      :: a non reveal tap only registers a turn request
                                            :: only a real turn plan carries a direction
                                            :: an outer reveal tap delegates to the manual reveal path
                                            :: a boundary tap never produces a turn plan
                                            :: a centre tap only toggles controls
TxtScrollChapterTargetTest                 :: the first chapter previous target falls back to the chapter start   ← 旧名 ...has no previous target 已不在场
```

`grep` 已确认改名后的用例在场、旧名不存在（旧名会在 XML 里以 `the first chapter has no previous target` 出现，实测无此条）。

### 我对关键前提做的独立验证（不是照抄 Zcode）

1. **半开区间前提**：`TxtPagedController.kt:104-110` 亲自读到 `start until (if (end > start) end else start + 1)` → 用例的 100/199/200 三点判定与真实页边界一致。
2. **跳转目标单位前提**：`grep jumpRequest / pagedJumpRequest` 的 7 个写入方（`ReaderProgressActions.kt:83/111/328/330/343/374/383`）全部写绝对偏移 → 与 `currentPageRangeAbs` 同空间，判定不会因量纲错配而恒真/恒假。
3. **派发点唯一性**：`grep -n onGesturePageTurn PagedReaderPageSurface.kt` → 仅 `:169`（形参）+ `:258`、`:271`、`:562` 三处调用，逐一读过上下文，均在页码变更之后。
4. **`PageTurn` 的菜单栏副作用**：`ReaderViewModel.kt:327-335` 与 F2 用例 `ReaderViewModelTest.kt:1151` 交叉确认（ST-1 的依据）。

### 工作区完整性

```
git diff --cached --name-only   # 0 个文件（未 stage）
git stash list                  # 空
git status --porcelain -uall    # 我开工时 339，收工时见下
```

我未修改任何产品源码/测试/正式报告/盘点/Obsidian；未 `reset`/`checkout`/`clean`/`stash`/`commit`/`push`；未使用 `--rerun-tasks`（避免全仓重建）；未跑全量 JVM、Lint、APK 或设备测试。Gradle 只写 `android/app/build`、`android/.gradle`（`android/.gitignore:2-3` 已忽略）。收工后的 mtime 取证与最终计数见下面「本报告写出后补记」段（因为本报告本身就是那次写入）。

---

## Scope integrity

### 我能证明的（本轮全部为独立复算，不依赖 Zcode 的自陈）

```
sha256(/tmp/s07f21-before.patch) = 20aeb97c8b64cfa909edd57e2b89e544482c65cf8dff1f6c12987cf8610c0b8f
sha256(/tmp/s07f21-after.patch)  = 4fcc5220dbdf1c1133fb9c2a70362edb3ccfa14f0da11343f29a017ac0864f94
```

两条都与其报告 §7 的申报值**逐字符相同**；`before` 539 行、`after` 768 行也相同。并且：

1. **after 快照真实**：我现场重跑 `git diff origin/main -- <5 授权文件>` 得 768 行，与 `after.patch` `diff -q` 相同 → 快照没有事后被改写，工作树里也没有其 §5 提到的变异残留。
2. **before 快照早于改动**：mtime `14:00`；本轮第一次测试写入 `14:05`、第一次源码写入 `14:11`；`11:16–14:00` 区间无任何仓库文件被修改（mtime 扫描为空）→ before 代表的就是 F2 收工后的状态。
3. **逐文件增量（我自己按 `diff --git` 段落切分统计）**：

| 文件 | before 段行数 | after 段行数 | delta |
|---|---:|---:|---:|
| `PagedReaderControllerEffects.kt` | 0（该文件本轮首次出现 diff） | 110 | **+110** |
| `PagedReaderPageSurface.kt` | 155 | 264 | **+109** |
| `PagedTxtReaderHost.kt` | 149 | 158 | **+9** |
| `ReaderContentHostPagedBranch.kt` | 35 | 35 | 段内容 `IDENTICAL` |
| `ReaderLayerBuilders.kt` | 196 | 196 | 段内容 `IDENTICAL` |

   与 §7.1 的申报一致（其 `+111` 与我的 `+110` 只差一行文件头段落，属计数口径差异，不影响判定）。
4. **两个"授权但未使用"的文件确实一字未动**：我独立提取 patch 段落做等值比较，结果 `IDENTICAL`；`ReaderLayerBuilders.kt` 是 F1/F2 产物所在文件，这条同时保护了 F1 的复制修复与 F2 的 `onSelect` 接线。
5. **同文件内其它 hunk 未被改动**：这是 patch-to-patch diff 的强结论 —— `diff -u before after` 的新增行只涉及本轮三个新函数 + `ReaderTapTurnPlan` 密封接口 + 一个参数 + 一处接线，其余上下文行（含 `PageSurface` 的 reveal 绘制、提交帧保护、`PagedTxtReaderHost` 的 295 行以下既有内容）逐字未变。
6. **无夹带**：delta 新增行对 `markdown|epub|copyText|DORMANT|edit|aiExplain|replace|tts|quote|han|trad` 的匹配数为 **0**。
7. **未用 `ReaderViewModel.kt`**：五字段契约（`withSelectionCleared`）确实在本轮未被改动 —— 其 mtime 停在 `10:37`（F2 窗口），且 F2 的 72 项回归全绿。**[事实]**

### 唯一残留不确定性

"before 快照是 14:00 生成的"依赖 mtime。我上一轮提的加固建议（把 before 的 SHA-256 写进本轮任务书，即 Zcode 无法事后编辑的文件）这次**未被采用** —— 任务书里没有该字段。不过本轮的伪造空间比上轮更小：after 与现树逐字节相同 + before 中三个新函数出现次数为 0 + `11:16→14:00` 空窗，三条独立约束同时成立才能解释现状。 **[推断]**

### 本报告写出后补记（实测数字）

```
git status --porcelain -uall    # 开工 339 → 收工 340，唯一新增行是本报告
git diff --cached --name-only   # 0 个文件
git stash list                  # 空
# 把 -uall 列出的文件与 14:26 比对（14:26 = Zcode 收工后我开工取证的分界）：
TOUCHED_BY_ME docs/coordination/reviews/undated/agent-review-s07-f21-qoder.md  14:34:28
```

即：我这段验证期间，仓库内唯一发生变化的文件就是我自己的这份报告；Gradle 产物全部落在 `android/app/build`、`android/.gradle`（`android/.gitignore:2-3` 已忽略）。

---

## Verdict

# PASS

F2.1 的三个缺口都真正闭合，且证明链是本轮三片里最完整的一次：

- **A 同章程序化跳转**：`readerJumpClearsSelection` + `applyProgrammaticJump` 以"跳转前可见页半开区间 vs 目标绝对偏移"判定身份变化，变化则恰好一次失效、未变化/无可见页则明确不清；判定前提（区间口径、目标单位）我独立核对成立。
- **B 提前与重复派发**：点按分支改为"只产出计划"，`onGesturePageTurn` 仅剩 3 处且全部在页码提交之后；非 reveal 路径从 2 次派发降为 1 次。
- **C 测试名与回流断言**：用例改名且行为未变（附跨格式契约证据）；`shouldDropLocalSelection` 抽出后 `LaunchedEffect(selectionCleared)` 必须调用它，回流从"只有注释"变成"编译期绑定 + 4 条断言"。
- 回归：F1 49 项、F2 85 项仍全绿；范围污染为 0（patch 级可证）。

### 必须一并登记的跟进项

1. **ST-1 [P2]**：`onSelectionInvalidated` 复用 `PageTurn` 带来新副作用 —— 同章跳页会隐藏阅读页顶栏/底栏，而跨章跳页不会，两条路径不一致；Zcode §4/§9 的披露表未列此项。建议在 S07 收尾前把它改接到 `ClearSelection`（同一个 `withSelectionCleared()`），或明确把"跳页后收起菜单栏"写成产品契约。
2. **SP-2**：滚动模式 ReadingUnit 身份变化的选区去留仍走另一条带身份门的清除路径，本轮未触碰，状态未变。
3. **ST-2 [P3]**：判定依赖 `controller.open()` 的确定性；失效方向是"多清不误漏"，可接受，但前提变化时需重审。

### PASS 的边界（不代表以下内容通过）

1. 这只关闭了 **S07 的"页面/章节/跳转身份变化后选区状态清理"** 这一件事，且证据层级是 **JVM 纯函数 + 接线级**。
2. **不代表翻页/跳页残留在真机上已修好**：残留色块是否消失、菜单栏收起是否符合预期、硬件键与自动翻页真实行为、`selRange` 回流的运行期执行 —— 全部未验证 → WorkBuddy。
3. **不代表 Markdown 滚动、旧 EPUB 整块点选、持久高亮重渲染已通过**（正式报告 Spec P1 第 4 项 / P2 状态未变）。
4. **不代表小屏 5 槽拥挤、字体放大、触摸目标、TalkBack 手柄语义与焦点顺序已通过**。
5. **不代表 `edit` / 复制 / 替换净化 / AI / TTS / 页码硬件键等其它 S07 项已复核**。
6. 不代表整个 S07 可以发布，也不构成任何"切片完成 / 可以发布"的结论。

**未领取下一任务。** S08 及看板队列其余切片保持 `HOLD`，等待 Codex 主任务复核与放行。
