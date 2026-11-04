# S07-F4.1 独立复核（Qoder）

- 复核 Agent：Qoder　|　实际模型：`qwen3.8Flash`
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`
- 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`；我复核时 `git rev-parse --short HEAD` = `5aed593`（基线未推进）
- 已读输入：`AGENTS.md`、`docs/code-review/2026-09/2026-09-20-android-s07-selection-review.md`、`docs/coordination/agent-results/agent-result-s07-f4-zcode.md`、`agent-review-s07-f4-qoder.md`（我上一轮，FAIL）、`agent-result-s07-f41-zcode.md`（mtime `01:28`，11 节完整）
- Zcode 完成判定（我 01:26 曾误判为"进行中"，那是因为它仍在写；本轮开工前重新核）：报告 `01:28` 落盘，最后一次源码写入 `01:24`（`EpubSingleCorrectionReachabilityTest.kt`），`/tmp/s07f41/after.sha256` 与 `files.txt` 生成于 `01:25`，`01:28` 之后 `android/`、`docs/` 无任何新写入 **[事实]**
- 标注：**[事实]** 我读到的代码或跑过的命令；**[推断]** 静态推导；**[UNVERIFIED]** 需真机 / Compose 运行期

## 判定：**PASS**（三个阻断问题均关闭；仅余已登记的非阻断 P3 与真机项）

| 轴 | 总数 | P0 | P1 | P2 | P3 | 最严重 |
|---|---:|---:|---:|---:|---:|---|
| Standards | 5 | 0 | 0 | 0 | 5 | ST-1「同值断言」半循环（实质同源由生产接线保证） |
| Spec | 2 | 0 | 0 | 0 | 2 | SP-1 切书绕过 `SheetClose` reducer 写 `sheet`（功能正确，不变量分叉） |

我在 F4 判 FAIL 的那条 P1（分页 EPUB 单处纠错被 VM 误拒）**已真正关闭**，且不是靠放宽门，而是把两端接回同一个事实源；纵深防御反而比 F4 更强（未发布 / 书身份不符 / 切书后未重发一律默认拒绝，并有真实 DAO 行为用例）。

---

## 一、replacement capability 同源（**关闭**）

### 生产链路（我逐环核对）

```
生产唯一真源  pagerEngine.replacementAvailability
              ← ReaderPagerEngineState.kt:70-78 effectiveReplacementAvailability / :287
    ├── UI 消费（未改）  ReaderScaffold.kt:562 canCreateReplaceRule
    │                    ReaderSheetHost.kt:315,330 规则页门控
    └── 发布           ReaderScreen.kt:490-501
                       LaunchedEffect(pagerEngine.replacementAvailability, bid, documentUiState.requestedBookId)
                         → ReaderAction.SetReplacementAvailability(bookId = bid, availability)
    └── VM 接收        ReaderViewModel.kt:554-562  只采信 action.bookId == _uiState.requestedBookId
                       （身份不符 ⇒ 忽略、不清空：新书自己的发布会很快到达）
    └── VM 判定        ReaderViewModel.kt:616-626  published==null 或书身份不符 ⇒ SOURCE_UNAVAILABLE
                       → readerReplacementCapability(同一映射函数)
    └── 命令门         ReaderViewModel.kt:508-519  capability !is Available → NotAnchorable → command=null
    └── 写入           if (command == null) return@io  ← 在 rulesRepository.execute 之前
```

- 旧的按文档类型猜测的 helper `readerLoadedContentReplacementAvailability` 已彻底删除：全仓 `grep` 命中数 **0** **[事实]** → 不存在"两套按格式猜的 helper"。
- `PublishedReplacementAvailability(bookId, availability)`（`:107-110`）把书身份带进状态，是 F4 缺的那一块 **[事实]**。
- UI 侧仍读同一个引擎字段，未被改成第二套判据（`ReaderScaffold.kt:562` 本轮未改，文件哈希 `7955c1fe69e2b141` 与其自述一致、mtime `17:35` 早于本轮窗口）**[事实]**。

### 行为矩阵（Zcode 声称 + 我自己实跑）

| 场景 | 发布值 | 期望 | 我这次 XML 里的证明位置 | 结果 |
|---|---|---|---|---|
| 分页 EPUB | `APPLIED` | 放行，落库 1 行，`source_start/end` = 锚点 | `a paged epub with applied replacement accepts a correction and writes one row` | **PASS** |
| 分页 EPUB | `NO_EFFECTIVE_RULES` | 放行（保留"可新增"契约） | `a paged epub with no effective rules still reaches the correction save path` | **PASS** |
| legacy EPUB | `ESTIMATED_COORDINATES` | 拒绝 + DAO **0 行** | `a legacy epub with estimated coordinates is rejected before any write` | **PASS** |
| legacy EPUB | `PAGER_ENGINE_DISABLED` | 拒绝 + DAO **0 行** | `a legacy epub with the pager engine disabled is rejected` | **PASS** |
| Markdown | `NON_SOURCE_COORDINATES` | 拒绝 + DAO **0 行** | `a bypassed correction on markdown is rejected at the capability gate` | **PASS** |
| 未发布 | — | 拒绝 + DAO **0 行** | `an unpublished availability rejects even a well formed correction` | **PASS** |
| 切书后未重发 | — | 拒绝 + DAO **0 行** | `a book switch invalidates the previous book availability` | **PASS** |
| 别书迟到发布 | 当前 `APPLIED` + 外书 `ESTIMATED` | 不覆盖，仍落库 | `a stale publish from another book is ignored` | **PASS** |
| TXT | `APPLIED` | 放行 + 四字段逐项 | `txt with an available capability accepts a correction anchored from the selection` | **PASS** |
| 能力放行但 findText≠选区 / 无锚点 | `APPLIED` | 拒绝 + DAO 0 行 | 2 条同族用例 | **PASS** |

`ReaderSingleCorrectionCapabilityTest` 我实测 **14 tests / 0 failures / 0 errors / 0 skipped**（与 Zcode 申报一致），且拒绝路径全部带 **DAO 行数**断言 ⇒ 证明"没写"而不是"写了再报错"**[事实]**。`rulesRepository.execute` 在 `command == null` 时根本不被调用，我按 `:517-522` 代码顺序核对成立 **[事实]**。

### ST-1 [P3] 那条"同值断言"仍是半循环，但不再是 F4 那种致命循环

`ReaderSingleCorrectionCapabilityTest.kt:431-447`：左值 = `readerReplacementCapability(PARTIALLY_APPLIED) is Available`（现算同一个映射），右值 = VM 是否落库。它证明的是"VM 与该映射一致"，而**没有**证明"UI 读的是同一个实例值"——那件事只在生产接线里（我上面已核对）。F4 的问题是把结论写进输入、从而对回归完全无感；F4.1 版本仍能抓住民A（变异 A 报告 5 FAILED：退回按类型猜会让这几条变红）。因此按 P3 记，不作阻断。建议：把 `uiSeesEntry` 改为从"UI 实际消费的可用性来源"取值（例如让测试断言 `ReaderScreen` 的发布 key 集合包含 `replacementAvailability`，或把 UI 判据提为一个 `fun replacementEntryAvailable(availability)` 由 UI 与测试共同调用）。

### ST-2 [P3] launcher「未调用」只在空选区一例断言

`ReaderSelectionExternalActionsTest.kt:85-98` 用布尔标志断言空选区时不调 launcher；其余通道靠 `error("…不应被调用")` 形式的 lambda 防止误调（该文件 `error(` 命中 15 处）**[事实]**。没有"恰好一次"的计数断言。可接受，登记为口径改进项。

---

## 二、切书完整清理（**关闭**）

### 状态矩阵（`ReaderViewModel.kt:679-719`，我逐行核对 + 用例名比对）

| 字段 | 切书（bookId 变化） | force 重载（同书） | 同书无操作重开 | 证据 |
|---|---|---|---|---|
| `selectedText` | `""` ✓ | `""` ✓ | 保留 ✓ | `switching books clears all five active selection fields` |
| `selectedRangeStart` | `-1` ✓ | `-1` ✓ | 保留 ✓ | 同上 |
| `selectedGlobalOffset` | `-1` ✓ | `-1` ✓ | 保留 ✓ | 同上 |
| `selectedSourceEnd` | `-1` ✓ | `-1` ✓ | 保留 ✓ | 同上 |
| `showColorRow` | `false` ✓ | `false` ✓ | 保留 ✓ | 同上（`withSelectionCleared()` 覆盖 5 字段，F2 起未变） |
| `selectionSnapshot` | `null` ✓ | `null` ✓ | 保留 ✓ | `switching books releases the consumer snapshot` / `a force reload rebuilds the document so it clears the selection too` / `reopening the same book does not release an in flight snapshot` |
| `sheet` / `noteOpen` / `noteBody` | 全部收回 ✓ | 收回 ✓ | 保留 ✓ | `the consumer UI state is closed on a book switch` |
| `ruleMutationResult` | `null` ✓ | `null` | 保留 | `a book switch clears the stale rule feedback too` |
| `_replacementAvailability` | `null` ✓ | **不清**（书身份不变、`LaunchedEffect` 不会重发，清了会永久不可用）| 保留 | `:691-694` 注释与 `:679` 判据 `documentIdentityRebuilt = bookChanged \|\| force` |

关键更正：F4 用 `switching books keeps the active selection untouched but drops the snapshot` 把错误产品契约**锁成测试**。该用例已删除，替换为"切书清五字段"一族（5 条）—— 这正是我 F4 报告 SP-2 判"若仍存在则 FAIL"的那一条 **[事实]**。

`ReaderSelectionSnapshotTest` 我实测 **24 tests / 0/0/0**，与申报一致 **[事实]**。

「新书重新捕获旧书选区」的反向证明：`a fresh selection cannot be recaptured from the previous book after a switch`（切书后开 RULES，活动选区与快照均为空）**[事实]**。配合消费路径全部读这 5 个字段 + `selectionSnapshot`（F2.4/F3.1 我逐个核对过：笔记、RULES、AI 解读、灵感、AI 助读），高亮/笔记/灵感/纠错都不可能拿旧书坐标配新书 bookId **[事实 + 推断]**。

### SP-1 [P3] 切书用 `copy(sheet = null)` 绕过了 `SheetClose` reducer

`:713-717` 直接复制 `sheet = null`，而正常路径（`OpenSheet` / `CloseSheet`）都会先过 `readerChromeReducer`。功能上无害（`BookSwitched` 事件已把菜单置可见），但它让"`sheet` 只由这两个 reducer 改写"这条不变量多了一个例外。建议：改走 `ReaderAction.CloseSheet` 序列，或在该处注释里写明"切书不需要 SheetClose 的 chrome 副作用"**[事实 + 推断]**。

---

## 三、外部动作提示（**关闭**，真机文案除外）

### 提示矩阵（`ReaderSelectionExternalActions.kt:24-82`，我逐条读源码 + 8 条序列断言）

| 通道 | launcher 结果 | 提示（单条，结果确定后） | 测试断言 |
|---|---|---|---|
| 浏览器 | OPENED / OPENED_WEB_FALLBACK | 已打开浏览器 | `:118` |
| 浏览器 | NO_HANDLER | 未找到可用浏览器 | `:135` |
| 系统词典 | OPENED | 已调用系统文字工具 | `:191` |
| 系统词典 | OPENED_WEB_FALLBACK | 未找到词典应用，已用在线词典查询 | `:174` |
| 系统词典 | NO_HANDLER | 未找到可用词典或浏览器 | `:208` |
| 在线词典 | OPENED / OPENED_WEB_FALLBACK | 已打开在线词典 | `:223` |
| 在线词典 | NO_HANDLER | 无法打开在线词典，请确认系统浏览器可用 | `:240` |
| 任意 | 空选区 | 请先选择要查询的文字，**且不调用 launcher** | `:97-98` |

- 「正在打开…」三条常量与 `ExternalActionKind`、两个旧函数已整体删除（我核对：`NOTICE_OPENING_*` 常量在 `main` 中已不存在，`grep` 无命中）**[事实]** ⇒ launcher 返回前不可能出现任何成功/进行中标示。
- 在线词典失败有专属文案，不再复用"未找到可用浏览器"**[事实]**。
- `selectionBrowserResultNotice` / `selectionSystemDictionaryResultNotice` / `selectionOnlineDictionaryResultNotice` 都是穷举 `when`（无 `else`）→ 新增 `SelectionExternalLaunchResult` 值会编译失败 **[事实]**。
- 真实 `Context` 重载与纯调度函数共用同一个 `launcher` 结果类型与同一个映射函数（`:242-250` 只是注入 `openSelectionInBrowser`）**[事实]**；真机上"系统确实没有浏览器 / 没有 PROCESS_TEXT handler"时的实际文案与行为 **[UNVERIFIED]**。
- 我实测 `ReaderSelectionExternalActionsTest` **18 tests / 0/0/0**，与申报一致 **[事实]**。

---

## 四、附带检查

| 项 | 结论 | 证据 |
|---|---|---|
| dormant `edit` 写侧 | **已关闭**：`SelectionActionStore.setActionEnabled` 新增 `if (id in DORMANT_IDS) return`；**且断言打在落盘原始集合**（`SelectionActionSettingsTest.kt:304-305` `preferencesDataStore.data.first()[stringSetPreferencesKey(...)]`，绕过读侧 sanitize），并有对照组 `a non dormant action still writes to the raw set`（`:423-`）证明该断言非恒真。这一点值得肯定：Zcode 自己在变异 E 首次运行时发现"读侧 sanitize 把写侧漏检救绿"，随即改测原始集合 **[事实]**。**未实现 S08**（仅拒绝写入）[**[事实]** |
| `RenderMarkdownUnit` 裸 Int 默认值 | 未改（`ReaderMarkdownRender.kt:247-248` 仍是 `blockGlobalOffset: Int = -1` / `chapterBase: Int = 0`）；该文件不在本轮边界内，按任务书保留为**非阻断 P3**，未越界 **[事实]** |
| `addStyle` 性能 / 小屏 / 大字体 / 触摸目标 / TalkBack / 真实无 handler / 真实 TTS→滚动→dispose / 分页 EPUB「保存→重开→回显」 | 全部**未测**，转 WorkBuddy **[UNVERIFIED]** |
| S14 WebDAV | 我实跑 `ui.viewmodel.ProfileWebDavManagerTest` = **11 tests / 0/0/0**（该类确实落在 `ui.viewmodel` 包，故 Zcode"本轮汇总集包含它并通过"的说法**准确**，我核到 `package com.creationreadingassistant.ui.viewmodel`）**[事实]**。它本轮通过、F4 那次失败，说明该项对环境/顺序敏感；本轮未修改它及其产品文件（mtime 早于窗口），归属与稳定性仍由 S14 owner / Codex 裁决，**不作为 S07 证据**，也不被用来掩盖 S07 结果 |

---

## Verification（我自己跑的）

先解析真实 FQCN（`SelectionActionsTest` / `SelectionActionStoreTest` 声明在 `SelectionActionSettingsTest.kt` 内，按文件名解析会 MISSING；`ProfileWebDavManagerTest` 的包是 `ui.viewmodel` 而不是 `ui.screen.profile`）后运行：

```bash
cd android && ./gradlew --console=plain :app:testDebugUnitTest --rerun \
  --tests <29 个已解析 FQCN> :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

- 退出码：**0**；`BUILD SUCCESSFUL in 50s`
- `:app:testDebugUnitTest` **真实执行**（`--rerun`，非 UP-TO-DATE）**[事实]**
- `:app:compileDebugKotlin` / `:app:compileDebugUnitTestKotlin`：UP-TO-DATE（本轮内容此前已编译）；`:app:compileDebugAndroidTestKotlin`：**本次实际执行并成功** **[事实]**
- **请求 29 类 == XML 29 个文件；缺失类 0；零匹配 0** **[事实]**

| 测试类 | tests | skipped | failures | errors |
|---|---:|---:|---:|---:|
| `ui.viewmodel.ReaderSingleCorrectionCapabilityTest`（F4.1） | 14 | 0 | 0 | 0 |
| `ui.viewmodel.ReaderSelectionSnapshotTest`（F4.1） | 24 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionExternalActionsTest`（F4.1） | 18 | 0 | 0 | 0 |
| `data.settings.SelectionActionStoreTest`（F4.1 +3） | 16 | 0 | 0 | 0 |
| `data.settings.SelectionActionsTest` | 24 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderReplacementCapabilityTest` | 15 | 0 | 0 | 0 |
| `ui.screen.reader.EpubSingleCorrectionReachabilityTest` | 8 | 0 | 0 | 0 |
| `ui.viewmodel.ReaderViewModelTest` | 72 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionSnapshotSemanticsTest` | 12 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownBlockSelectionLocatorTest` | 10 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownHighlightChapterSpanTest` | 10 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownPersistentHighlightProjectionTest` | 11 | 0 | 0 | 0 |
| `ui.screen.reader.MarkdownUnknownChapterLengthTest` | 8 | 0 | 0 | 0 |
| `ui.screen.reader.EpubBlockPersistentHighlightTest` | 15 | 0 | 0 | 0 |
| `ui.screen.reader.ReadingUnitSelectionOwnershipTest` | 10 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderContentHostSelectionCallbacksTest` | 4 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderSelectionToolbarModelTest` | 9 | 0 | 0 | 0 |
| `ui.screen.reader.ReaderCopyNoticeTest` | 3 | 0 | 0 | 0 |
| `ui.screen.reader.TxtScrollChapterTargetTest` | 7 | 0 | 0 | 0 |
| `feature.reader.pager.PagedReaderSelectionInvalidationPolicyTest` | 12 | 0 | 0 | 0 |
| `feature.reader.pager.ProgrammaticJumpInvalidationTest` | 4 | 0 | 0 | 0 |
| `feature.reader.pager.ReaderTapActionTest` | 9 | 0 | 0 | 0 |
| `feature.reader.pager.ReaderTapTurnPlanTest` | 5 | 0 | 0 | 0 |
| `ui.viewmodel.ProfileWebDavManagerTest`（S14，仅核实归属） | 11 | 0 | 0 | 0 |
| 结构守卫 5 类（见下） | 35 | 0 | 0 | 0 |
| **合计（29 类）** | **366** | **0** | **0** | **0** |

### 行为测试与结构守卫分列（我的口径）

| 类别 | 类 | tests |
|---|---|---:|
| **结构 / 源码守卫** | `ReaderConsumerAndActionGateTest` 6、`ReaderConsumerSnapshotWiringGuardTest` 9、`ReadingUnitDisposeWiringGuardTest` 8、`ReaderSelectionRangeWiringGuardTest` 9、`ProgrammaticJumpWiringGuardTest` 3 | **35** |
| **行为 / 语义测试** | 其余 24 类（含 F4.1 的 14 + 24 + 18 + 16） | **331** |

F4.1 本轮新增/改写的 4 个测试类共 **72 项，全部是行为断言**（真实 loader / 真实命令派发 / 真实 `FakeReaderCorrectionDao` 行数 / 真实 `screenState` 字段），无一条源码字符串守卫 —— 与 Zcode §7.4 的说法一致，我逐类读过断言体确认 **[事实]**。

未运行（按任务书）：全量 JVM、`:app:lintDebug`、APK 打包、`androidTest` 执行、任何设备命令。

---

## Scope integrity

本轮证据方法**恢复到 F2.x/F3.1 的水准**（F4 那轮只有 after 哈希）：`/tmp/s07f41/files.txt` 固定清单 + before/after 两份 `sha256sum` 同命令输出。我独立复算：

1. **after 清单 11 条，`sha256sum -c /tmp/s07f41/after.sha256` → 11 OK / 0 FAILED** ⇒ 快照等于当前工作树，无事后改写、无变异残留 **[事实]**。
2. **before 清单 10 条**，与 after 的唯一集合差是 `EpubSingleCorrectionReachabilityTest.kt`（它在 after 阶段追加）—— 与 §6 的披露一致；副作用是该文件的"只改 KDoc、方法体未变"**没有 before 基线可证**，我改用它自报哈希（`a3164a04…`，与我无关的独立值）+ XML `tests=8`（我上一轮实测同为 8）间接确认，方法学缺口记为 ST-3 **[事实 + 推断]**。
3. **起点无并发覆盖**：`before.sha256` 里 `ReaderViewModel.kt = 1e83dd39ba89b068…` **与我在 F4 复核时实测并登记的值逐字符相同** ⇒ F4.1 的 before 就是 F4 的收工状态，中间没有第三方写入（`01:28 → 00:48` 空窗我也用 mtime 扫过）**[事实]**。
4. **改动范围**：`before.sha256` 对当前树 9 处不匹配、1 处匹配 —— 匹配的那个正是 `ReaderScreenState.kt`（`974ddeddc6a3c7c1`，我 F4 也测过同值），与它"本轮未改"的说法一致 **[事实]**。
5. **边界外文件（我用自己前几轮的登记值交叉验证）**：`ReaderSheetHost.kt b330a66d7549be1d`（= 我 F2.4 测量值）、`ReaderLayerBuilders.kt 74a07e46765355fa`（= 我 F2.4 值）、`MarkdownScrollHighlight.kt 89bd78e2c77d637c`（= 我 F4 值），三者 mtime 分别 `18:28 / 18:03 / 23:32`，全部早于本轮 `00:48` 窗口 → 未触碰 **[事实]**。
6. **一处出处错误需更正（ST-4 [P3]）**：§6 表把 `ReaderSelectionToolbar.kt` 的哈希说明写成"与 **F4 登记值**一致"，但 F4 报告并未登记该文件（是我在 F4 复核里指出"只有间接证据"）。值本身 `04301c010537c41a` 我复算一致、mtime `23:26` 早于本轮窗口，结论不受影响，但**登记出处标错**，应改为"与 Qoder F4 复核期间的观察一致/本轮新增登记"。
7. **未跟踪文件**：`ReaderSingleCorrectionCapabilityTest.kt`、`ReaderSelectionSnapshotTest.kt` 是 untracked，本轮用 `git diff --no-index` 出具 insertions（580 / 555 行）+ SHA256 登记，未使用 `git add -N`；`git diff --cached --name-only` 我实测 = **0**，`git stash list` = **0**，HEAD 仍 `5aed593` **[事实]**。
8. **夹带**：本轮产品侧改动集中在 5 个文件（capability 状态与门、发布 action、发布 LaunchedEffect、提示矩阵重写、dormant 写侧拒绝），未见 S08–S20 语义改动；`SelectionActionStore` 只加"拒绝"，未实现编辑 **[事实]**。
9. **我自己的足迹**：`git status --porcelain -uall` = 379（我未新增任何文件；本报告写出后为 380，唯一差异即本报告）。

---

## 结论与四个必答

1. **F4.1 是否关闭？** **是，关闭。** 三个阻断问题各有行为级证据（capability 9+ 行 DAO 矩阵、切书 7 条含反向与 force 判据、提示 8 条单条序列矩阵），且我独立实跑 29 类 366 项全绿、快照 11/11 复算命中。
2. **S07 是否可以交 WorkBuddy 真机验收？** **可以交。** 本轮无 P0/P1/P2 遗留，剩余项均为已登记的 P3（不阻断）与"本质上只能真机判定"的项。交之前提（务必写进验收单）：本轮所有证据止于 JVM 状态层与真实 DAO，**没有任何一条** Compose 运行期或真机证据。
3. **是否还需要开发修复？** **S07 范围内不需要。** 建议（非阻断）留给 Codex 排期：ST-1 把"UI↔VM 同源"从接线事实变成可断言契约；SP-1 让切书走 `CloseSheet` reducer 以保住 `sheet` 单一写入口；SEAM 的 `RenderMarkdownUnit` 两个裸 Int 默认值（越界项，建议与其它切片合并处理）；S14 `ProfileWebDavManagerTest` 稳定性归属裁决。
4. **哪些仍是 WorkBuddy 未验证项（绝不能写成已通过）：**
   1. **分页 EPUB 单处纠错全链**：真机「保存 → 关闭 → 重开 → 正文回显」——本轮刚恢复的能力，从未跑过一次真机。
   2. **legacy / 非分页 EPUB**：整块选择、`gOff` 混合量纲下的持久高亮回显正确性；纠错被拒时真机提示是否只有一条且准确。
   3. **外部动作真机文案**：无浏览器、无 `ACTION_PROCESS_TEXT` handler、Web fallback 实际路径。
   4. **切书真机确认**：工具条不带旧书文字、旧摘抄/草稿不进新书、`force` 重试后不残留。
   5. **小屏 / 横屏 / 大字体下五个动作的拥挤与截断、触摸目标、TalkBack 焦点顺序与选区手柄语义。**
   6. **真实 TTS 跟读 → `scrollToItem` → ReadingUnit dispose → 消费者仍持快照** 的运行期链。
   7. **`addStyle` 额外遍历的滚动流畅度**（长章 + 多高亮 + TTS 同开）。
   8. **Markdown 表格/图片节点**：不可选节点的降级观感与"无伪坐标"的真机表现。
   9. 跨页选择、`selectedSourceLength` 半清错值复现、`edit`/S08 接线、S14 WebDAV —— 均不属于本轮，也均未通过。

**未领取下一任务，等待 Codex 主任务复核。S08 及其余队列保持 HOLD；本报告未修改任何产品源码或测试，未 stage / commit / push，未操作设备。**
