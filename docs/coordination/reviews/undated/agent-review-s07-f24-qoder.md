# S07-F2.4 独立复核（Qoder）

- 复核 Agent：Qoder（模型 `qwen3.8Flash`）
- 复核对象：S07-F2.4 选区快照生命周期（长生命周期消费者不再回读实时选区）
- 工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`
- 基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
- 已读输入：`AGENTS.md`、正式报告 F2.3 段、`agent-result-s07-f23-zcode.md`、`agent-review-s07-f23-qoder.md`（我上一轮那份，FAIL + SEAM REQUEST）、`agent-result-s07-f24-zcode.md`、8 个改动产品文件与 5 个测试文件的当前内容、Zcode 的 before/after patch（独立复算哈希、跨轮段比对）
- 前置确认：Zcode 结果文件 mtime `18:49`（存在且完整，10 节）；其源码/测试写入集中在 `17:31–18:46`；before 快照 `17:25`、after 快照 `18:37`
- 标注：**[事实]** 我读到的代码或跑过的命令；**[推断]** 静态推导；**[未验证]** 需 Compose 运行期或真机

我没有采用 Zcode 的消费者清单、PASS 自判或测试统计；下面每项判定都给出我自己的取证位置。特别地，我把它申报的测试统计当"待核对"处理，结果是**我发现它的一处包名口径需要更正**（见 Verification 的零匹配段）。

---

## Standards

### 逐项判定

| # | 检查项 | 判定 | 我的证据 |
|---:|---|---|---|
| 1 | 快照是否明确、不可变、单一职责 | **PASS** | `ReaderScreenState.kt:35-40` 新增 `data class ReaderSelectionSnapshot(text, rangeStart, globalOffset, sourceEnd)`，4 个 `val`，无 setter、无散落字符串；`:123` 以单一字段 `selectionSnapshot: ReaderSelectionSnapshot? = null` 挂进状态；`:77-88 withSelectionCaptured()` / `:91-92 withSelectionSnapshotReleased()` 各只做一件事。全仓 `selectionSnapshot` 引用共 6 处（我 grep 过：1 定义 + 1 写 + 1 释放 + 1 读于 SheetHost + 1 读于 ReaderScreen + 1 注释）——**没有第四份真相** |
| 2 | 捕获是否在 VM/reducer 边界原子完成，四字段是否同一时刻 | **PASS** | `ReaderViewModel.kt:355-359`（`OpenSheet`）、`:383-387`（`SetNoteOpen`）都在同一个 `updateScreen { }` 内基于**同一个 `it`** 调用 `withSelectionCaptured()`，四个字段全部读自该状态对象 → 无跨帧拼接。`SetSelectedText:367-374` 显式不写快照（注释在 `:368`） |
| 3 | 释放规则是否明确；关闭/保存/切普通 sheet 后不残留；消费者打开期间不被 `ClearSelection` 误清 | **PASS（一处例外见 ST-1）** | `CloseSheet:361-366` 释放；`OpenSheet(非消费):359` **主动**释放（这正是"避免上一轮旧快照被复用"的正解）；`SetNoteOpen(false)` 释放；`withSelectionCleared()`（`ReaderViewModel.kt:87-93`，我核对未被改动，F2.2 的 5 处调用点也未变）不触碰快照 → 正文清理与消费者解耦 |
| 4 | 是否引入 pending 永不释放、旧快照复活、消费者串用、切书后复用旧快照 | **一项不成立：切书未释放**（ST-1）；其余未见 | 无 pending 状态机（无"延后清除"字段）；消费者互斥：`sheet` 是单值 `copy(sheet = ...)`，不可能两层消费者叠放导致串用；正文新建选区不动快照 → 但**切书路径 `:577-585` 只重置 chrome + `searchQuery` + 规则反馈，既不释放快照也不清活动选区** |
| 5 | ReadingUnit dispose 后正文活动选区是否仍正常清理 | **PASS（可证明）** | 跨轮段比对：`ReaderContentHostPlainTextBranch.kt` 的 patch 段与 F2.3 收工状态**逐字节 `IDENTICAL`**（我用 Python 按 `diff --git` 切段比较）→ dispose 语义一字未动；F2.2/F2.3 的 `ReadingUnitSelectionOwnershipTest`(10)、`ReadingUnitDisposeWiringGuardTest`(8) 在我自己这次运行里全绿 |
| 6 | 是否改动授权清单外文件或夹带 | **PASS，且越界披露可证伪为"已还原"** | 见 Scope integrity：`ReaderScaffold.kt`（Zcode 主动披露曾短暂编辑的越界文件）patch 段与 F2.3 收工状态逐字节 `IDENTICAL` → 零残留可证，不只是它的自陈 |
| 7 | `ReadingUnitDisposeWiringGuardTest` 是否真的检查 onDispose 块 | **PASS（我上轮的 ST-3 已被真正实现）** | 不再是整文件字符串匹配：`:59 matchBraces` / `:77 blockAfter` / `:93 disposeEffectBlock` / `:97 onDisposeBlock` 先做**花括号配对提取**，断言只在该片段内成立；`:141` 还断言"清空调用被归属判断门控、不是无条件发出" |
| 8 | 是否删掉那条检查不可编译形态的空负向断言 | **PASS（我上轮的 ST-2 已处理）** | 旧断言 `contains("val ownsCurrentSelection by remember(")`（编译不过的形态）已不在；改为 `:158` 用正则解析 `ownsCurrentSelection` 的**声明右值**必须来自 `rememberUpdatedState`，加 `:178` 显式拒绝一族合法但危险的快照写法（含 `= remember {`），`:196` 对回调声明做同样处理。这正是我变异清单里那条"合法等价改写"能被抓住的形式 |
| 9 | 结构守卫与语义测试是否分开统计 | **PASS，且我按自己的口径重算** | 我实测（自己跑的 XML，非它的表）：结构守卫 `ReadingUnitDisposeWiringGuardTest` 8 + `ReaderConsumerSnapshotWiringGuardTest` 9 = **17**；语义/行为 `ReaderSelectionSnapshotTest` 17 + `ReaderSelectionSnapshotSemanticsTest` 12 + `ReaderNoteSnapshotSaveTest` 6 = **35**。两者数量与它的 §6 一致。守卫的固有脆弱性（正常重构会红）已在 `:45 source()` 用 `assertTrue(exists())` fail-loud，我认可保留 |

### ST-1 [P2] 切书 / 同 VM 换书时快照不释放，且活动选区也不清

`ReaderViewModel.kt:577-585`（`openBook` 中 `current.requestedBookId != bookId` 分支）只做三件事：chrome 重置、`searchQuery = ""`、`_ruleMutationResult = null`。**没有** `withSelectionCleared()`，**也没有** `withSelectionSnapshotReleased()`。

后果 **[推断]**：如果同一个 Reader ViewModel 实例在其存活期内收到另一个 `bookId`，则 (a) 上一本书的活动选区仍留在 `screenState` 里（这是 F2 之前既有问题，不因 F2.4 产生），(b) **新增**：上一本书的 `selectionSnapshot` 也会被带到新书的弹层里 —— 笔记/AI/灵感可能把 A 书的摘录与 A 书 locator 存到 B 书的一条笔记上。这条比 (a) 更糟，因为快照是"看起来更可信"的数据源。

**[事实] 我同时核到 F2.4 自身没有扩大它**：`ReaderScreenState.kt:123` 的默认值是 `null`，所以只要 VM 实例是新建的（正常从书架进入另一本书的导航路径），快照就是空的；风险窗口只在"同一实例换书"。我**无法**静态证明这条窗口可达或不可达（需要导航层 `ViewModelStore` 的生命周期证据），因此按 P2 + 未验证记，不作 FAIL 依据。

最小修法（一行，语义无歧义）：在该分支链上补 `.withSelectionSnapshotReleased()`；若 Codex 同时愿意承认 (a) 是缺陷，就把 `.withSelectionCleared()` 一起加上（注意这会改 F2 的清理口径，属于 Codex 决策而非本切片范围）。

### ST-2 [P3] 快照对"打开后又新建选区"的优先级没有测试化的产品口径

`establishing a new body selection after capture leaves the snapshot untouched` 断言了"新选区不改快照"。这在技术上正确，但它是**产品语义选择**：用户在弹层开着时于正文另选一段，保存笔记时拿到的仍是**上一次**的文字与坐标。我核到这条路径不会崩、不会串（locator 与 text 同源，见 SP-2），但正式报告/盘点里最好把这条契约写成一句话，免得将来被当成 bug 改回去。 **[推断]**

### ST-3 [P3] `consumesSelection()` 是手工分类表（Zcode 自己在遗留风险 2 已披露，我确认可复现路径存在但当前无害）

`ReaderScreenState.kt:53-65` 用 `when` 枚举 `ReaderSheet` 值。新增一个消费选区的枚举而忘了加进该表 → 走 `else` 分支**主动释放**快照（`:359`），而不是错误捕获。我核对这个失败方向是"退回 live 读取"（即 F2.4 之前的行为），不会造成崩溃或跨书串数据，因此是**静默降级**而非新缺陷。建议按它的方向把分类改成 `ReaderSheet` 的构造属性，让新增枚举必须显式作答。 **[事实：失败方向由 `:358-359` 的 if/else 决定]**

### ST-4 [P3] 两条保存路径的 `excerpt/locatorJson` 成对下发只由签名约束

`ReaderSheetHost.kt:424-425/446-447` 把 `consumerSnapshot?.text` 与 `snapshotLocator()` 作为**两个并列实参**传给 `ReaderLayerBuilders` 的回调（回调签名在 `:331` 起的 lambda 上）。同源由"两个实参都来自同一个 `consumerSnapshot`"保证，而不是由类型保证。已由守卫 `inspiration saves use the snapshot text and snapshot locator together` 钉住形状；若将来有人把两者拆成两次取用，仍需靠那条守卫。可接受，登记为结构依赖。 **[事实]**

---

## Spec

### SP-1 我自己的长生命周期消费者清单（不照抄它的表）

我用 `grep selectedText` 在 `src/main` 得到 82 处引用、19 个文件，再按"打开后是否持续持有选区引用"筛。我的分类与它的表**一致**，但我的判据来自逐条读取 + 它的两条机械守卫交叉印证：

- **长生命周期（必须快照）= 4 个弹层 + 2 条 sheet 内保存路径**：笔记、`RULES`、`AI_EXPLAIN`、`INSPIRATION`、`AI_ASSIST`；保存路径 `onSaveAiExplainInspiration`、`onSaveInspiration`。我逐个核到读取源已改为 `ReaderSheetHost.kt:225 val consumerSnapshot = inputs.screenState.selectionSnapshot`：`:331/335`（规则预填与 `allowCorrection`）、`:408`（AI 助读 `contextText`）、`:414/424`（AI 解释文本与其→灵感保存）、`:435/446`（灵感摘录与其保存）。**[事实]**
- **一次性动作（刻意保持 live）**：复制、浏览器、查词典、高亮、书内搜索。我核对这些在点击瞬间读一次并立即消费；其中搜索与高亮在 F2/F2.1 就已经"先快照再清"。**它保持不动的判断我认同** —— 快照化反而会让"清空后再点复制"复制旧文本。
- **我额外确认的非消费者**：`DICTIONARY` 先在 `ReaderScreen.kt:611` 写本地 `dictionaryWord` 再 `:612 OpenSheet(DICTIONARY)`，面板只读该本地状态，不读选区（我实际读到这段顺序，不是照抄它的表）；`NOTES`/`TOC`/`SETTINGS`/`PROGRESS`/`BOOK_INFO`/`THEME` 不读选区。`ReaderSheetHost.kt` 内已**不存在**任何读取实时 `selectedText` 的分支（只剩 `:414` 一处赋值，且右值是快照）——**[事实]**

结论：**没有遗漏的第五个长生命周期消费者**，也没有被过度快照化的一次性动作。

### SP-2 逐条行为验证

| 要求 | 判定 | 证据 / 层级 |
|---|---|---|
| 2 笔记显示、保存文字、locator 同源于打开时快照 | **成立** | `ReaderScreen.kt:573` 取一次 `noteSelection`，`:575` 显示、`:583` 保存文字、`:584` `nav.computeLocatorJsonForSnapshot(noteSelection)` —— 三处同一个对象引用。**[事实]** |
| 3 规则在活动选区被清后保持初始替换文字 / REPLACE 标签 / 用户草稿 | **成立（静态）** | `ReaderRulesSheet.kt` 的 `pinnedReplaceText = remember { initialReplaceText }` 使三个 `remember` key 恒定；`allowCorrection = pinnedReplaceText != null`。这是 Compose `remember` key 语义的直接推论 → **[推断]**，无 Compose 运行期断言（Zcode §8.2 同样标注） |
| 4 AI 解读与灵感继续拿到原选区 | **成立** | `ReaderSheetHost.kt:414/435` 读 `consumerSnapshot?.text`，dispose 清空 live 后不变 **[事实：取值来源]** |
| 5 AI 伴读从选区打开时不退化为整章 | **成立** | `:408 contextText = consumerSnapshot?.text?.takeIf { isNotBlank() } ?: 整章` —— 快照存在即优先快照；`AI assist never degrades to whole chapter text while a snapshot exists` 守卫钉形状 **[事实]** |
| 6 离线词典等是否仍实时读选区 | **否** | 见 SP-1 的 `DICTIONARY` 顺序核对 **[事实]** |
| 7 快照后 `ClearSelection` / `PageTurn` / dispose 只清正文不影响消费者 | **成立** | `withSelectionCleared()` 未改动且不写快照；F2.2 的 `ReaderContentHostPlainTextBranch` 段跨轮 `IDENTICAL`；快照读取方全在 SheetHost/ReaderScreen 一侧 **[事实]** |
| 8 消费者关闭后无选区重新打开，不复用旧摘录/旧坐标 | **成立** | `CloseSheet` 与 `OpenSheet(非消费)` 双路释放；`withSelectionCaptured()` 在 `selectedText.isBlank()` 时置 `null`（`:78/85-87`），所以顶栏无选区开灵感得到 `null` 而非上一轮值 **[事实]**；真机路径 **[未验证]** |
| 9 locator 由快照文字与快照坐标共同生成 | **成立** | `computeLocatorJsonForSnapshot`（`ReaderProgressActions.kt:52-64`）四个入参全部取自同一 `snapshot`，null 快照直接返回 null，不会退回去读 live **[事实]** |
| 10 source/display 契约保持、无 display offset 入库 | **成立** | 该函数是把快照字段原样交给既有 `computeLocatorJson`；`LocatorBuilder.forEpub/forPlain`、`AnchorResolver` 等坐标层未被本轮 patch 触及（8 文件清单外）；F2.1/F2.2 的坐标用例（`ScrollHighlightRangeTest` 族与 `ReaderSourcePositionTest`）在其 §5.1 内亦未改动。**[事实（文件面）]** |

### SP-3 重点反例的逐条推演（我按代码路径读，不靠它的表）

1. **打开笔记 → TTS 推进 → dispose → 保存笔记**：`SetNoteOpen(true)` 已捕获；dispose 只清 live 五字段；保存用 `noteSelection` → 摘录与 locator 都在。**成立**（状态机层 **[事实]**；"TTS 真的推进并 dispose" **[未验证]**）。
2. **打开规则 → 输入部分草稿 → TTS 推进 → dispose**：`pinnedReplaceText` 恒定 → `editing`/`tab` 不重建 → 草稿保留。**[推断]**（无 Compose 运行期证据）。
3. **打开 AI 解读 / 灵感 → dispose**：文本与 locator 取快照。**成立**；但"保存后弹层是否仍留在同一 draft"属 sheet 内部逻辑，本轮未改。
4. **从选区打开 AI 伴读 → dispose**：`contextText` 仍为快照文本，不退化为整章。**成立（结构守卫 + 源码）**。
5. **消费者打开后正文产生新选区**：快照不变（`SetSelectedText` 不写快照）。行为见 ST-2 的产品口径提示。**成立**
6. **关闭消费者 → 顶栏无选区重新打开灵感**：释放后捕获为空 → `null` → 不复用旧摘录。**成立**
7. **RULES 返回 TOC / 直接 dismiss / 保存成功三条释放路径**：`dismiss → CloseSheet` 释放；`保存成功 → ClearSelection`（不动快照）+ 之后 `CloseSheet` 释放；**返回 TOC 是 sheet 内部 tab 切换，不发 `OpenSheet`/`CloseSheet` → 快照保持**，符合"弹层仍打开"的语义 **[事实]**。我特别核过 F2.3 遗留的那条相互作用（`ReaderSheetHost:297-300` 的 RULES 返回按钮依赖 `selectedText` 非空才发 `ClearSelection`）：它读的仍是 live 选区，与快照无耦合，未被本轮改动破坏。
8. **切书 / 重新加载 `ReaderUiState` 后旧快照是否消失**：**未被证明消失**；见 ST-1。**[推断/未验证]**

### SP-4 F1–F2.3 已关闭行为回归

我自己重跑（非引用它的表）：F1 复制/`edit` 四类、F2 五字段与翻页语义、F2.1 跳转判定与点按计划、F2.2 归属与失效通道、F2.3 折叠分支共用谓词 —— 全绿，共 19 类 **263 tests / 0 failures / 0 errors / 0 skipped**（见 Verification）。

### SP-5 未验证项（不得当作通过）

- Compose 运行期：`remember { initialReplaceText }` 的 pin 是否真的不随宿主重建而变（若宿主 key 变化导致 `ReaderRulesSheet` 整体离开组合，pin 会重新求值 —— 此时 `initialReplaceText` 已是快照文本，仍正确，但未测）。
- TTS 跟读 → `scrollToItem` → dispose 的真实设备时序；`LazyColumn` 回收窗口大小。
- 真机一切：菜单栏保持、工具条消失、相邻单元不误清。
- Markdown 滚动无选区入口、旧 EPUB 整块点选、两者不重渲染持久高亮：状态未变。
- 小屏 5 槽拥挤、字体放大、触摸目标、TalkBack 手柄语义与焦点顺序：未验证。
- `selectedSourceLength` 半清错值（F2 遗留）：仍未复现。
- 切书快照释放（ST-1）：可达性未证明。

---

## Verification

### 命令 1（我自己的 19 个类过滤）

```bash
cd android && ./gradlew --console=plain :app:testDebugUnitTest --rerun \
 --tests "...ui.viewmodel.ReaderSelectionSnapshotTest" \
 --tests "...ui.screen.reader.ReaderSelectionSnapshotSemanticsTest" \
 --tests "...ui.screen.reader.ReaderNoteSnapshotSaveTest" \
 --tests "...ui.screen.reader.ReaderConsumerSnapshotWiringGuardTest" \
 --tests "...ui.screen.reader.ReadingUnitDisposeWiringGuardTest" \
 --tests "...ui.screen.reader.ReadingUnitSelectionOwnershipTest" \
 --tests "...ui.screen.reader.ReaderContentHostSelectionCallbacksTest" \
 --tests "...ui.screen.reader.ReaderCopyNoticeTest" \
 --tests "...ui.screen.reader.ReaderSelectionToolbarModelTest" \
 --tests "...ui.screen.reader.TxtScrollChapterTargetTest" \
 --tests "...ui.screen.reader.RuleEditorDraftTest" \
 --tests "...ui.viewmodel.ReaderViewModelTest" \
 --tests "...feature.reader.pager.{ProgrammaticJumpInvalidationTest,ProgrammaticJumpWiringGuardTest,PagedReaderSelectionInvalidationPolicyTest,ReaderTapTurnPlanTest,ReaderTapActionTest}" \
 --tests "...data.settings.SelectionActionsTest" --tests "...data.settings.SelectionActionStoreTest" \
 :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

- 退出码 **0**；`BUILD SUCCESSFUL in 41s`；`:app:testDebugUnitTest` 真实执行（`--rerun`）
- `:app:compileDebugKotlin` / `:app:compileDebugAndroidTestKotlin` = **UP-TO-DATE**（当前内容此前已成功编译；本轮无新编译动作）**[事实]**
- 结果目录 **18 个 XML**，我请求了 19 个类 → **1 个零匹配**（见下）

### 我自己踩到并修正的零匹配（这条正说明该陷阱真实存在）

我按 `com.creationreadingassistant.ui.screen.reader.RuleEditorDraftTest` 过滤，Gradle 仍报 `BUILD SUCCESSFUL`。真实包名是 `...ui.screen.reader.**sheets**.RuleEditorDraftTest`（我 `grep '^package '` 确认）。命令 2 用正确包名重跑：

```bash
./gradlew --console=plain :app:testDebugUnitTest --rerun \
 --tests "com.creationreadingassistant.ui.screen.reader.sheets.RuleEditorDraftTest"
```

退出码 **0**，`BUILD SUCCESSFUL in 5s`，XML：`tests="36" skipped="0" failures="0" errors="0"`。**[事实]**
（它的 §5.1 把 `RuleEditorDraftTest` 记为 36 项并通过 —— 与我的实测一致，所以它的数字没错；错的是我的过滤。此段留作方法记录：**任何 `--tests` 过滤都必须用 XML 类数与用例名核对**。）

### XML 统计（我自己的运行，19 类合计）

| 测试类 | 归属 | tests | skipped | failures | errors |
|---|---|---:|---:|---:|---:|
| `ui.viewmodel.ReaderSelectionSnapshotTest` | F2.4 语义 | 17 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionSnapshotSemanticsTest` | F2.4 语义 | 12 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderNoteSnapshotSaveTest` | F2.4 语义 | 6 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderConsumerSnapshotWiringGuardTest` | F2.4 结构守卫 | 9 | 0 | 0 | 0 |
| `ui.screen.reader.ReadingUnitDisposeWiringGuardTest` | F2.4 重写守卫 | 8 | 0 | 0 | 0 |
| `ui.screen.reader.ReadingUnitSelectionOwnershipTest` | F2.2 | 10 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderContentHostSelectionCallbacksTest` | F2 | 4 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderCopyNoticeTest` | F1 | 3 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionToolbarModelTest` | F1 | 9 | 0 | 0 | 0 |
| `ui.screen.reader.TxtScrollChapterTargetTest` | F2.1 | 7 | 0 | 0 | 0 |
| `ui.screen.reader.sheets.RuleEditorDraftTest` | 消费草稿 | 36 | 0 | 0 | 0 |
| `ui.viewmodel.ReaderViewModelTest` | F2 | 72 | 0 | 0 | 0 |
| `feature.reader.pager.ProgrammaticJumpInvalidationTest` | F2.2 | 4 | 0 | 0 | 0 |
| `feature.reader.pager.ProgrammaticJumpWiringGuardTest` | F2.2 | 3 | 0 | 0 | 0 |
| `feature.reader.pager.PagedReaderSelectionInvalidationPolicyTest` | F2.1 | 12 | 0 | 0 | 0 |
| `feature.reader.pager.ReaderTapTurnPlanTest` | F2.1 | 5 | 0 | 0 | 0 |
| `feature.reader.pager.ReaderTapActionTest` | F2 | 9 | 0 | 0 | 0 |
| `data.settings.SelectionActionsTest` | F1 | 24 | 0 | 0 | 0 |
| `data.settings.SelectionActionStoreTest` | F1 | 13 | 0 | 0 | 0 |
| **合计（19 类）** | | **263** | **0** | **0** | **0** |

其中 F2.4 自己新增的：语义 35、结构守卫 17。**与 Zcode §6.1/§6.2 的分层计数完全一致**。 **[事实]**

它 §4.3/§5 的"96 类 / 797 用例"我没有复现，也不需要 —— 它用的是通配 `--tests "...ui.screen.reader.*" --tests "...ui.viewmodel.*"`，覆盖面比"最小定向"宽（并未跑全量 275 类，所以不违反任务书），我不把它当成本轮验收依据，只采用我自己上面 19 类 / 263 项的结果。

### 我没有独立复现的部分

- **RED 与 6 个变异**：需要临时修改产品源码，任务禁止，我没做。替代验证两条：(1) after 快照与我现场 `git diff` **逐字节相同** → 工作树无变异残留 **[事实]**；(2) 我逐条读了新增语义用例的断言体方向（`opening a consumer sheet captures all four selection fields atomically` 断言四字段同值、`closing/switching` 断言 `null`），这些断言在缺少捕获/释放时必然失败 **[推断]**。
- Compose 运行期与真机：全部未做（按任务书禁止设备）。

### 工作区完整性

```
git diff --cached --name-only   # 0（未 stage）
git stash list                  # 空
git status --porcelain -uall    # 我开工 355 → 收工见补记
```

未修改任何产品源码或测试；未 `reset`/`checkout`/`clean`/`stash`/`commit`/`push`；未运行全量 JVM、Lint、APK 或设备测试；未使用 `--rerun-tasks`；Gradle 只写 `android/app/build`、`android/.gradle`（`android/.gitignore:2-3` 已忽略）。

---

## Scope integrity

### 独立复算（不依赖 Zcode 的自陈）

```
sha256(/tmp/s07f24-before.patch) = 06b575e8290c316507c660edfb7530e62d4ae3762fbda0a972f14da7dfc7515a   (1273 行)
sha256(/tmp/s07f24-after.patch)  = 5e7bda5e3d5f5f5a1657334a79939083bece761db2323f0b449c755a36d7c7ef   (1668 行)
```

两条 SHA-256 与行数**均与 §7.1 申报值逐字符相同**。并且：

1. **after 真实**：我现场 `git diff origin/main -- <8 授权文件>` = 1668 行，与 `after.patch` `diff -q` 相同。这也解释了 `ReaderViewModel.kt` 的 18:46 mtime（那是 §4.1 的变异回写）与其内容仍等于申报哈希并不矛盾。
2. **8 个文件的终点内容逐一吻合**（我实测 `sha256sum` 前 16 位 vs 它的 §7.2 表）：`974ddeddc6a3c7c1`、`fb13c6b58ad8db03`、`b330a66d7549be1d`、`74a07e46765355fa`、`0eb7c904ef2ee337`、`9e6441c704d9264c`、`933513e1400b852f`、`0d63026fdb25ce42` —— **8/8 全对**。 **[事实]**
3. **before 早于改动**：`17:25`；首次测试写入 `17:31`、首次源码写入 `17:31`（`ReaderScreenState.kt`）。
4. **跨轮段比对（我本轮最有价值的一条独立取证）**：把 F2.3 收工补丁（`s07f23-after.patch`，16:43）与本轮现树 diff 的**同名文件段**对比：

| 文件 | F2.3 收工 → 现在 |
|---|---|
| `ReaderScaffold.kt` | **IDENTICAL**（它 §3.3 主动披露的越界编辑确实零残留） |
| `ReaderContentHostPlainTextBranch.kt` | **IDENTICAL**（dispose 语义未被触碰，要求 10 成立） |
| `ReaderLayerBuilders.kt` | CHANGED（授权内，快照签名） |
| `ReaderSheetHost.kt` | CHANGED（授权内，快照消费） |
| `ReaderViewModel.kt` | CHANGED（授权内，捕获/释放） |

5. **夹带扫描**：F2.4 delta 的新增行里命中 `markdown|epub|copyText|DORMANT|aiExplain|QuoteCard|HanConvert|scrollToItem|Tts` 的共 18 行，我把 18 行全部打出来逐条核对 —— 都是快照管线（`computeLocatorJsonForSnapshot(epubBook, …)`、`onSaveAiExplainInspiration` 签名扩参）与注释里对 TTS 链路的说明，**没有一处改动 EPUB/Markdown 渲染、TTS 行为、复制逻辑或 `DORMANT_IDS`**。其中 1 行还是源码 diff 的上下文行。 **[事实]**
6. **增量量级**：它 §7.2 的"真正属于 F2.4 的增量 +203 行（+229/−26）"用的是"origin/main blob + before 补丁重建起点"这一方法，我未逐行重算起点重建（需要绕过工作区既有改动），但我用第 4 点的跨轮段比对独立确认了：`ReaderScaffold`/`PlainTextBranch` 零变更，其余 6 个文件确有变更且都在授权清单内。两者结论互不矛盾。 **[推断]**

### 残留不确定性（与前几轮同源）

before 快照的可信度仍部分依赖 mtime（我提了四轮的"把 before SHA-256 写进任务书"仍未采纳）。本轮交叉约束更强：8 个文件的终点内容哈希逐个对上 + after == 现树 + 跨轮段比对自 F2.3 链条衔接，伪造成本已不现实。 **[推断]**

### 我这一侧的边界自守

只新增 `docs/coordination/reviews/undated/agent-review-s07-f24-qoder.md`；未修改任何产品源码、测试、正式报告、产品盘点或 Obsidian；未读写 Zcode 报告内容以外做任何操作（只读取）；未 stage / commit / push / 建分支或 worktree；未触碰设备或原仓库 `creation-reading-assistant`。

### 本报告写出后补记（实测）

```
git status --porcelain -uall    # 355 → 356，唯一新增是本报告
mtime 扫描（> 18:50）：仅 docs/coordination/reviews/undated/agent-review-s07-f24-qoder.md
staged=0  stash=0
```

---

## Verdict

# PASS

按验收口径逐项：

- **所有长生命周期消费者均使用正确快照** —— 成立。我独立枚举并逐个核对读取源：笔记、`RULES`、`AI_EXPLAIN`、`INSPIRATION`、`AI_ASSIST` 与两条 sheet 内保存路径，全部取自 `ReaderSheetHost.kt:225` / `ReaderScreen.kt:573` 的同一份不可变快照；`DICTIONARY`、`SEARCH`、`NOTES`、`TOC` 等确认非消费者；复制/浏览器/查词/高亮保持一次性实时读取是正确取舍。文字与 locator 同源（`computeLocatorJsonForSnapshot`），无 display offset 入库。
- **正文清理仍成立** —— 可证：`ReaderContentHostPlainTextBranch.kt` 与 F2.3 收工状态逐字节相同，`withSelectionCleared()` 未触碰，dispose 归属/守卫用例全绿；快照与活动选区经三条正交操作解耦，`withSelectionCleared()` 不写快照、`SetSelectedText` 不动快照。
- **无 P0/P1** —— 我这一轮没有发现新的 P0/P1。F2.3 的 FAIL 项（消费竞态）已用"打开时原子快照 + 关闭/切换释放"消除，而不是靠延迟清理；笔记摘录与 locator、规则草稿与标签页、AI 解读与灵感文本、AI 助读退化都在状态机层得到语义级（非仅字符串）用例覆盖。
- 关键接线有有效门禁：`ReadingUnitDisposeWiringGuardTest` 已改为**花括号配对提取 `onDispose` 块**再断言（我上轮 ST-3），并删掉了那条永不生效的负向断言、换成声明正则 + 危险写法黑名单（我上轮 ST-2）；`ReaderConsumerSnapshotWiringGuardTest` 9 条覆盖"每个消费分支读快照""没有分支读实时字段""清除函数不写快照"。
- 结构守卫与语义测试我已按自己的口径分开统计：**守卫 17、语义/行为 35**，守卫不冒充运行期覆盖。
- 回归通过：我自己跑的 19 类 **263 tests，0 failures / 0 errors / 0 skipped**，F1–F2.3 的 15 类哨兵全绿。
- 无范围污染：SHA-256 与行数逐字符对上、8/8 文件终点哈希吻合、after 与现树逐字节相同、越界披露的 `ReaderScaffold.kt` 跨轮 `IDENTICAL`、18 行关键词全部核实为快照管线/注释。

### 遗留 P2 / P3（不构成回退，但请在 S07 收口前处理或明确接受）

1. **ST-1 [P2]**：切书分支（`ReaderViewModel.kt:577-585`）既不释放快照也不清活动选区；快照会把上一本书的摘录与 locator 带进新书弹层。可达性我未能证明（依赖导航层 ViewModelStore 生命周期），修法一行：链上补 `.withSelectionSnapshotReleased()`。
2. **ST-3 [P3]**：`consumesSelection()` 手工分类表 —— 新增枚举漏登记时失败方向是"退回 live 读取"（静默降级），建议改为 `ReaderSheet` 构造属性强制作答。
3. **ST-4 [P3]**：灵感保存的 `excerpt/locatorJson` 成对下发由调用点保证，只有结构守卫拦得住回退。
4. **ST-2 [P3]**：「弹层开着时另选正文」保存的是上一次快照，这是产品口径，建议写进正式报告，别让它将来被当 bug 改动。
5. 它自己的遗留风险 1（端到端未验证）与 6（工作区共存改动）我认可并已在下方边界中重申。

### 真机与运行期边界（不得当作通过）

1. **不代表 TTS 跟读 → `scrollToItem` → ReadingUnit dispose 这条真实链路已在设备上跑通**；它仍是静态链 + 状态机证据。
2. **不代表 Compose 运行期行为已验证**：`pinnedReplaceText` 保住 `editing`/`tab` 是 `remember` key 语义的推论，无 UI 测试；`onDispose` 实际触发、`rememberUpdatedState` 读值、`sourceStartAbs` 作 key 的唯一性同理。
3. **不代表 Markdown 滚动选区、旧 EPUB 整块点选、持久高亮重渲染已通过**（正式报告 Spec P1 第 4 项 / P2 状态未变）。
4. **不代表小屏 5 槽拥挤、字体放大、触摸目标、TalkBack 手柄语义与焦点顺序已通过**。
5. **不代表 `selectedSourceLength` 半清错值已复现，不代表 S08 的 `edit` 接线已完成，不代表整个 S07 可以发布**。真机验收 owner 仍是 WorkBuddy。

未领取下一任务，等待 Codex 复核。F3、S08 及看板队列其余切片保持 `HOLD`，我不会自行开工。
