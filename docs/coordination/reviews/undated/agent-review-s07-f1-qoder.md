# S07-F1 独立复核（Qoder）

- 复核 Agent：Qoder（模型 `qwen3.8Flash`）
- 复核对象：Zcode 的 S07-F1 修复（`edit` 死按钮 / 复制虚假成功）
- 工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`；分支 `codex/recover-worktree-20260920`
- 已读输入：`docs/code-review/2026-09/2026-09-20-android-s07-selection-review.md`、`docs/coordination/agent-results/agent-result-s07-f1-zcode.md`、`AGENTS.md`、5 个改动文件的当前内容与 `git diff origin/main`
- 前置确认：Zcode 结果文件 mtime `09:13`，正式报告 mtime `08:49`，两者均在盘上后才开工
- 标注：**[事实]** 读自代码或我亲自执行的命令；**[推断]** 静态推导；**[未验证]** 需真机

复核方式：所有结论都以我自己重跑的命令与重读的代码为准，未按 Zcode 报告的表述计分。下面每处它说对的、说得不全对的、以及我无法证实的，都分开写。

---

## Standards

### 目标逐条判定

| # | 复核目标 | 判定 | 一手证据 |
|---:|---|---|---|
| 1 | `edit` 在 S08 接线前无法从设置页启用、无法进工具栏 | **成立** | `SelectionActionSettings.kt:146 DORMANT_IDS = setOf("edit")`、`:149-150 pickableDefs()` 同时排除 FIXED 与 dormant；设置页两处清单唯一来源是它（`SelectionSubPage.kt:115`、`:162`，该文件本轮未被改动，mtime 不在改动窗口内）；渲染清单 `effectivePrimary:159` / `effectiveMore:171` 均经 `pickableDefs()`；读盘收敛 `sanitize:198 knownPickable = pickableDefs()` |
| 2 | 保留 S08 可重新启用的清晰边界，而非删定义 | **成立** | `ALL` 中 `SelectionActionDef("edit", "编辑", "编辑本章正文", MORE)` 原样保留在 `:85`，`:82-84` 注释解释为何保留定义 + 为何必须同时进 dormant；`DORMANT_IDS` KDoc `:143-144` 给出接线动作（"把 id 从这里移除并补上生产分发分支"）；`:108-117` 用例锁住声明落点（`replace` 与 `dictionary` 之间） |
| 3 | 所有当前可渲染动作都有生产处理方 | **当前成立，但没有门禁** | 可渲染集合 = `pickableDefs()` 9 个 id + 固定组 `cancel` = 10；分发 `ReaderSelectionToolbar.kt:165-174` 恰有 10 个 `when` 分支（highlight/browser/copy/dictionary/note/replace/search/ai/inspiration/cancel），一一对应。**注意**：`else -> Unit`（`:175`）仍然是静默兜底，见 ST-2 |
| 4 | `copyText()` 返回 true/false 时提示分别正确 | **成立** | `ReaderLayerBuilders.kt:143-144 readerCopyNotice()`；调用点 `:352-355` 把 `val copied = clipboard.copyText(selectedText, label = "reader_selection")` 的返回值喂给提示函数。关键点：这里**不传 `scope`** → 走 `ClipboardUtils.kt:42-45` 同步分支 → 返回值是 `setPrimaryClip` 的真实结果，而不是"任务已入队"。成功文案 `"已复制"` 与既有契约一致，失败文案 `"复制失败，请重试"` |
| 5 | 只触及两个授权问题 | **未发现夹带**，见 Scope integrity | — |
| 6 | 测试是否真实执行 | **真实执行** | 见 Verification，我从 XML 的 `testcase/@name` 逐条确认 7 个新增/改写用例都在本轮结果里，而非过滤零匹配后静默成功 |

### ST-1 [P2] 写路径没有和 dormant 收敛到同一个判据

`SelectionActionStore.kt:60-62` 只拒绝未知 id 与固定组：

```kotlin
val def = SelectionActions.byId(id) ?: return
if (def.group == SelectionActionGroup.FIXED) return
```

`byId("edit")` 仍返回定义（这是目标 2 要求的），所以 `setActionEnabled(..., "edit", true, PRIMARY)` **会真的写进 DataStore**。Zcode 在 §2.2 主动披露了这一点，我核实其结论成立：当前 `setActionEnabled` 的唯一生产调用链是 `ProfileRoute.kt:499-502` ← `SelectionSubPage.kt:122/165` 的 pill 点击 ← `pickableDefs()` 清单，因此**用户已无法触发这条写路径**，且读出口 `SelectionActionStore.settings:28-41` 一律 `sanitize`，UI 永远看不到 `edit`。另外 `:66/:72` 是整集写入，用户下次拨任意开关时会顺带把盘上残留的 `edit` 洗掉，属自愈。

残余风险是设计层面的一致性：同一约束在读侧、清单侧都生效，唯独写侧靠"没人调用"。最小修正建议（1 行，不改语义）：

```kotlin
if (id in SelectionActions.DORMANT_IDS) return
```

### ST-2 [P2] 这轮把 bug 变成"不可出现"，但没有建立"可渲染 ⇒ 必须有处理方"的不变量

修复后 `edit` 不再能渲染，因此分发表缺分支不再可见 —— 这是正确的止血。但同类缺陷的成因（新增动作忘了加分支）没有任何自动化保护，而 S08 恰恰要做这件事：把 `edit` 移出 `DORMANT_IDS` **并且**在 `onActionClick` 加分支。两步里漏掉任何一步都不会被现有测试发现：

- 只移出 dormant、忘加分支 → 死按钮复活（本轮修的同一个 bug）；
- 只加分支、忘移出 dormant → 动作永远出不来（静默失效，同样无测试）。

`ReaderSelectionToolbarModelTest.kt:133-147` 那条新用例是对 `edit` 的**点断言**，不是集合完整性断言。现有测试面无法直接读 `@Composable` 里的 `when`，所以完整性断言落不了地这件事本身是一个结构问题。**[推断]**

最小修正建议（保持 JVM 纯函数、不引入 Robolectric、不挂载 Reader）：把 id → handler 的映射从 `when` 里提出来，做成可被测试枚举的表，再断言覆盖关系。例如在 `ReaderSelectionToolbar.kt` 侧定义

```kotlin
internal sealed interface SelectionToolbarHandlers { /* 每个动作一个成员 */ }
// 或更轻：internal val selectionDispatchedIds: Set<String>，由 onActionClick 的表驱动实现派生
```

然后加一条用例：`assertEquals(SelectionActions.pickableDefs().map { it.id } + "cancel", selectionDispatchedIds)`。表驱动实现本身仍然只是把现有 `when` 换成 `Map<String, () -> Unit>`，不新增运行时风险。**[事实]** 本仓已有按文件路径断言 UI 文案的 `verify-*.mjs` 守卫先例，若不想动生产代码，用源码级守卫兜住"新增 pickable id 必须同时出现在分发实现里"也可接受（较弱，但比现在强）。

### ST-3 [P2] 复制修复的回归保护只覆盖决策函数，不覆盖接线

`ReaderCopyNoticeTest.kt:17-35` 三条用例全部只调用 `readerCopyNotice(Boolean)`。如果有人把 `ReaderLayerBuilders.kt:354` 改回 `showNotice("已复制")`（即丢掉 `copied`），**三条用例仍然全绿**。Zcode 在 §3.3 说明"刻意只测窄函数以避免挂载整个 Reader 页面"，这个取舍我认同（挂载 Reader 页在本仓成本极高，见我上一轮 S-1），但要如实记录：本轮修复的真实回归保护强度是"函数级"，不是"接线级"。

精确测试建议（不挂 Compose 树）：把这一行的决策再抽窄一层，使其本身可断言 —— 例如

```kotlin
internal fun readerSelectionCopyNotice(writeSucceeded: Boolean): String = readerCopyNotice(writeSucceeded)
```

没意义；有意义的是改成表驱动断言 `copyText` 的两个返回值 → 两个不同提示，并把 `onCopy` 写成"取返回值 → 交给 `readerCopyNotice` → 交 `showNotice`"三步中只余 `showNotice` 为注入项的窄函数（`showNotice` 已是注入项，成本约 6 行），这样"忽略返回值"就会编译期失败或用例失败。**[推断]**

### ST-4 [P3] 失败提示把两种成因合并成一句

`copyText` 返回 false 有两条语义不同的路径：空白选区拒写（`ClipboardUtils.kt:39` + `copyableSelection`）与系统异常降级（`:43-45`）。修复后两者共用"复制失败，请重试"。**[事实]** 但工具条本身以 `selectedText.isNotBlank()` 门控（`ReaderInteractionLayer.kt:353`），所以经工具条触发的空白选区分支实际不可达，用户在实践中只会因系统异常看到这句 —— 语义没错，只是不可诊断。Zcode 已把文案标为"未经产品确认"，我确认这是当前唯一的开放文案项。

### ST-5 [P3] dormant 是常量集合而不是动作属性

`DORMANT_IDS: Set<String>`（`:146`）与 `ALL` 里的定义分处两地。当前只有 1 个 dormant 动作，可维护性没问题；若 S08 之后同时有多个未接线动作，建议改为 `SelectionActionDef(dormant: Boolean)`，避免"加了定义忘了加集合"。`::136-137` 注释已经明确 dormant 与 FIXED 语义相反、不得合并，这一点写得很清楚，是给未来读者的有效护栏。**[事实]**

---

## Spec

### SP-1 用户可见路径复核：三条都闭合 **[事实]**

| 用户路径 | 修复前 | 修复后（我读到的当前代码） |
|---|---|---|
| 设置页 → 常用动作（第一屏） | `pickableDefs()` 含 `edit` → 可勾选 | `SelectionSubPage.kt:115` 取 `pickableDefs()`，`:149-150` 已排除 dormant → 「编辑」pill 不出现 |
| 设置页 → 「更多」菜单 | 同上 | `:162` 同源 → 不出现 |
| 选完字 → 工具栏 | 配置含 `edit` 即渲染，点击落 `else -> Unit` | `effectivePrimary:159` / `effectiveMore:171` → 拿不到 `edit`；`selectionToolbarActions` 的空槽兜底也走 `pickableDefs()`（`ReaderSelectionToolbar.kt:120`） |
| 老版本磁盘里已有 `edit` | 直接渲染成死按钮 | `sanitize:198-200` 丢弃；`resolveStoredPrimary` 不受影响（它只做默认值升级，不做 id 校验） |

复制侧的用户可见结果：真写入 → "已复制"（文案不变）；未写入 → "复制失败，请重试"。**[事实]** 且不再出现"内容没进剪贴板却报成功"。

### SP-2 与 S08 的产品契约没有被写坏 **[事实]**

`DEFAULT_PRIMARY` 仍是 4 项（`:101`），`MAX_PRIMARY_SLOTS` 仍是 5（`:58`，未动），注释 `:98-99` 说明"S08 接线后追加 edit 即占满 5 槽"。也就是说本轮没有顺手把槽位数改回 4 来"绕过"窄屏拥挤问题 —— 这一点值得记一笔：那是正式报告 U2/我上轮 S-4 的真机开放项，不属于 F1 范围。

### SP-3 未修的 P1 仍然在盘上（本轮范围外，如实登记）

我在复核时顺手确认了两处**不属于 F1、也未被 F1 改动**的既有问题仍然存在，供 Codex 排后续切片时对照：

1. `ReaderViewModel.kt:288-290` 中央点击只清 `selectedText` 与 `selectedRangeStart`，`showColorRow` / `selectedGlobalOffset` / `selectedSourceEnd` 保留；对照 `ClearSelection` 的 `:325-333` 清四字段 + `showColorRow`。**[事实]** 后果（**[推断]**）：中央点击收起后下一次长按会直接落在"选择高亮颜色"行而不是动作行；并且 `ReaderScaffold.kt:594-600` 的 `selectedSourceLength` 由 `selectedSourceEnd` 减 `selectedRangeStart`/`selectedGlobalOffset` 混搭推出，半清状态下可能算出跨字段错值（被 `?.takeIf { it >= 0 }` 兜成 null，进而静默写 `payload = "{}"`）。
2. `PagedReaderPageSurface.kt:499-506` 的 `onPrevious` / `onNext` 只做 `selRange.value = null` + `controller.prev/nextPage()`，**没有** `onSelect("", -1, -1)`，而 `ReaderContentHostPagedBranch.kt:126` 定义的 `selectionCleared = selectionState.selectedText.isBlank()` 只看 VM 文本。**[事实]** 这正是正式报告 Spec P1 第 3 项（拖动/硬件键翻页后工具栏引用旧页文字）的机制，F1 未触碰。

这两条**不构成 F1 的范围违规**（授权范围就是两个问题），我只登记为"未修 + 未回归"。

### SP-4 未验证项（不得当作已通过）

- 真机上「编辑」pill 是否真的消失、"复制失败，请重试"是否真的能看见 —— 只有 JVM 证据；
- 系统剪贴板异常路径（`SecurityException` 等）未在真机/受限用户下复现；
- 装过早期版本、盘里已有 `edit` 的升级场景未在真机验证（`sanitize` 有单测，但真机 DataStore 未查）；
- 窄屏 5 槽拥挤、TalkBack、触摸目标：F1 与本轮均未触及。

---

## Verification

### 我亲自执行的命令

```bash
cd android && ./gradlew --console=plain :app:testDebugUnitTest --rerun \
 --tests "com.creationreadingassistant.data.settings.SelectionActionsTest" \
 --tests "com.creationreadingassistant.data.settings.SelectionActionStoreTest" \
 --tests "com.creationreadingassistant.ui.screen.reader.ReaderSelectionToolbarModelTest" \
 --tests "com.creationreadingassistant.ui.screen.reader.ReaderCopyNoticeTest" \
 :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

- 退出码：**0**；`BUILD SUCCESSFUL in 12s`
- `> Task :app:testDebugUnitTest` —— 用 `--rerun` 强制真实执行，**不是** UP-TO-DATE
- `> Task :app:compileDebugKotlin UP-TO-DATE`、`> Task :app:compileDebugAndroidTestKotlin UP-TO-DATE` —— UP-TO-DATE 的含义是"当前源码内容已被成功编译过"（Gradle 按输入哈希判定，Zcode 在 09:05 之后跑过），所以编译面成立，但**本轮没有产生新的编译动作**。**[事实]**

### XML 结果（`android/app/build/test-results/testDebugUnitTest/TEST-*.xml`）

| 测试类（XML `testsuite/@name` 原值） | tests | skipped | failures | errors |
|---|---:|---:|---:|---:|
| `com.creationreadingassistant.data.settings.SelectionActionsTest` | 24 | 0 | 0 | 0 |
| `com.creationreadingassistant.data.settings.SelectionActionStoreTest` | 13 | 0 | 0 | 0 |
| `com.creationreadingassistant.ui.screen.reader.ReaderSelectionToolbarModelTest` | 9 | 0 | 0 | 0 |
| `com.creationreadingassistant.ui.screen.reader.ReaderCopyNoticeTest` | 3 | 0 | 0 | 0 |
| **合计** | **49** | **0** | **0** | **0** |

与 Zcode §4.3 报告的 24/13/9/3 = 49 **逐类一致**。**[事实]**

### 零匹配防护（任务点 6 的针对性核查）

我上一轮踩过这个坑：`--tests` 模式匹配 0 个类时 Gradle 仍报 `BUILD SUCCESSFUL`。本轮按类名（而非文件名）过滤，并从 XML 逐条确认新增用例真的存在：

```
SelectionActionsTest            :: dormant edit is neither configurable nor renderable
SelectionActionsTest            :: sanitize drops a stored dormant action id
SelectionActionsTest            :: edit keeps its declared landing slot for the chapter editor slice
SelectionActionsTest            :: edit is dormant until the chapter editor is wired
ReaderSelectionToolbarModelTest :: pickable set excludes fixed and dormant actions
ReaderSelectionToolbarModelTest :: toolbar never renders a dormant action even when it is configured
ReaderCopyNoticeTest            :: a successful clipboard write reports success
ReaderCopyNoticeTest            :: a failed clipboard write never claims success
ReaderCopyNoticeTest            :: success and failure notices are distinguishable
```

4 个请求的类 = 4 个 XML 文件，无缺类。**[事实]** 结论：目标测试确实执行，且新增用例确实在跑。

### 用例是否"恒真"（非空断言核查）**[事实]**

逐条读过断言体：`SelectionActionSettingsTest.kt:73-88`（`pickableDefs` 不含 edit + 强行塞进两个集合后 `effectivePrimary`/`effectiveMore` 仍不渲染）、`:91-105`（`sanitize` 丢 edit 且不误伤 `copy`/`note`）、`:108-117`（`ALL` 顺序落点）、`ReaderSelectionToolbarModelTest.kt:118-130`（清单等值 + 双向排除）、`:133-147`（`selectionToolbarActions` 输出不含 edit）。这些断言在缺少 `DORMANT_IDS` 的代码上必然失败，因此 Zcode §4.2 报告的"修复前 4 failed / 33 tests"与我的静态判断一致。

**但我没有独立复现 RED**：要复现必须回退产品源码，任务默认只读，我没做。**[未验证]** 该项按"与代码逻辑一致 + 由断言体可推出"采信，而非"我亲自跑过"。

### 工作区完整性

```
git status --porcelain              # 我的验证前 315 → 验证后 315，逐行 diff 为空
git status --porcelain -uall        # 331（315 是把整个未跟踪目录折叠成一行，故需 -uall 才精确）
```

单看行数会被目录折叠骗过，所以我另做了一次 mtime 取证：把 `-uall` 列出的所有仓库内文件与 `09:13`（Zcode 收工时刻）比对，**我这段验证期间唯一变化的仓库文件就是我自己的报告**：

```
docs/coordination/agent-results/agent-result-s07-f1-zcode.md   09:13:02   ← Zcode 的收工写入，非我所为
docs/coordination/reviews/undated/agent-review-s07-f1-qoder.md   09:19:52   ← 本报告
```

同时确认一个我原本担心的副作用没有发生：`android/app/schemas/.../AppDatabase/15.json`（未跟踪）mtime 是 `2026-09-16 21:54`，属工作区既有资产，**不是**我这两次 Gradle 生成的。Gradle 只写了 `android/app/build`、`android/.gradle`（`android/.gitignore:2-3` 已忽略）。未使用 `--rerun-tasks`（避免全仓重建），未跑全量 JVM / Lint / APK / 设备测试。

---

## Scope integrity

### 我能证实的部分 **[事实]**

1. **mtime 取证**：`android/` 与 `docs/` 下 mtime 落在 `08:40` 之后的源码/测试/文档只有 9 个文件，其中代码类正好是 Zcode 申报的 5 个：

```
09:02 src/test/.../data/settings/SelectionActionSettingsTest.kt
09:03 src/test/.../ui/screen/reader/ReaderSelectionToolbarModelTest.kt
09:04 src/main/.../ui/screen/reader/ReaderLayerBuilders.kt
09:04 src/test/.../ui/screen/reader/ReaderCopyNoticeTest.kt      (新增文件)
09:05 src/main/.../data/settings/SelectionActionSettings.kt
09:13 docs/coordination/agent-results/agent-result-s07-f1-zcode.md
```

同窗口内的 `08:49/08:50` 三个 md 是 Codex 侧的正式报告、看板与产品盘点，不属于 Zcode 的 F1 改动。**`ReaderSelectionToolbar.kt` 不在改动清单里** —— 与它"授权范围内但经核实无需改动"的申报一致。

2. **新符号影响面**：`DORMANT_IDS` 只出现在 `SelectionActionSettings.kt`（定义 `:146` + 消费 `:150` + 3 处注释）；`readerCopyNotice` 只有定义 `:143` 与唯一调用 `:354`。没有任何 UI 层散落判断、没有第三处副作用。
3. **其它剪贴板调用点未被顺带动**：`grep copyText(` 在主源码共 9 处，除 `ReaderLayerBuilders.kt:353` 外，`InspirationRoute.kt:108,316`、`HomeInspirationDetailScreen.kt:206`、`AiExplainResultSection.kt:159,235`、`ReaderAiAssistSheet.kt:550,580,611` 仍是旧写法（且都走异步 `scope`）—— 与"另立切片"的申报一致。
4. **无 bypass 路径**：`primaryDefs()` / `moreDefs()`（`:129`/`:131`，绕过 dormant 的 `ALL` 过滤器）在全仓**没有任何生产调用点**，只被 `SelectionActionSettingsTest.kt:30-31` 用作分组计数。所以不存在"某处 UI 用 moreDefs() 又把 edit 放出来"的暗路。
5. **翻页 / 切章 / Markdown / EPUB / AI 未被夹带**：授权 4 个文件的 diff 里含 `epub|markdown|切章|tts` 等关键词的新增行共 11 处，逐条看均为工作区既有 hunk（例如 `ReaderLayerBuilders.kt` 里 M-BUG-08 的 `usesTxtChapterNav = epubBook == null && !isMarkdown`），而 SP-3 列出的 `PagedReaderPageSurface.kt` / `ReaderViewModel.kt` 两个文件 mtime 根本不在本轮窗口内 —— 它们"未被改动"这一点同时证明了没修、也证明了没夹带。

### 我无法证实的部分（如实声明）

- **"其它 hunk 一字未动"无法用 git 证明**。F1 的改动与工作区既有的恢复期改动落在同一批文件里，仓库里没有 F1 开工前的快照（Zcode 报告 §4.1 只把基线数字写进文档，没落成 patch 文件）。间接佐证：两个源文件的 `git diff origin/main` 现为 274 行，而 Zcode 申报的三文件基线为 275 行、未动的 `ReaderSelectionToolbar.kt` 现为 56 行 → 反推其基线两文件约 219 行，本轮净增约 55 行，与其申报的内容级增量（`DORMANT_IDS` + KDoc ≈ 17、3 处注释、`readerCopyNotice` 13 行、`onCopy` +3）在同一量级。**[推断]** 量级吻合，但不是证明。
- 我给 Codex 的**流程建议**（不属本轮判定）：每轮开工前把 `git diff origin/main -- <owned paths> > /tmp/<slice>-<agent>-before.patch` 留在仓库外，收工后 `diff` 一次，这样"同文件其它 hunk 是否被动过"就是可证明的，而不是靠 mtime + 行数反推。

---

## Verdict

# PASS

两个授权问题都已修复，且没有发现范围污染：

- **问题 A（`edit` 死按钮）**：门控落在唯一可配置清单出口 `pickableDefs()`，设置页两个入口、工具栏两个清单、读盘 `sanitize`、空槽兜底全部自动收敛；`ALL` 中的定义与落点保留，并有专门用例锁定；S08 的重启用路径在 `DORMANT_IDS` KDoc 里写清了动作。目标 1、2、3 成立。
- **问题 B（复制虚假成功）**：提示改由 `copyText` 的**同步**真实返回值驱动，成功文案未动，失败文案可区分。目标 4 成立。
- 目标 5：未发现夹带（5 项正面证据 + 1 项无法完全证明，已如实登记）。
- 目标 6：测试真实执行，49 项全绿，且我用 XML 用例名做了零匹配防护。

### 即使 PASS，以下内容不代表通过

1. 这只代表 **S07-F1** 这一小片（两个问题），**不代表整个 S07 已完成或可发布**。
2. **翻页 / 切章 / 重排后的旧选区与坐标残留**仍未修（正式报告 Spec P1 第 3 项；机制见本文 SP-3.2，我本轮一手确认 `PagedReaderPageSurface.kt:499-506` 与 `ReaderViewModel.kt:288-290` 的现状未变）。
3. **Markdown 滚动无选区入口、旧 EPUB 滚动整块点选、两者不重渲染已存高亮**（正式报告 Spec P1 第 4 项 / P2）未在本轮范围内，状态未变。
4. **真机项全部未验证**：小屏 5 槽拥挤与字体放大、触摸目标、TalkBack 焦点与手柄语义、"编辑"pill 实际消失、复制失败提示实际可见 —— 归 WorkBuddy。
5. 我在 RED（修复前测试确实失败）一项上未独立复现，需要回退源码才能复现；该项按静态一致性采信。
6. 随附 3 条 P2 跟进项（ST-1 写路径判据、ST-2 分发表完整性门禁、ST-3 接线级回归保护）建议在 S08 接线**之前**处理，尤其 ST-2：S08 一旦把 `edit` 移出 dormant 就会重新暴露在同一类缺陷下。

**未领取下一任务。** S08 及看板队列其余切片保持 `HOLD`，等待 Codex 主任务复核与放行。
