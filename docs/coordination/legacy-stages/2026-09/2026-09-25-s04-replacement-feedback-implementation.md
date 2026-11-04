# S04 来源替换失败的用户反馈链：实现报告

- 日期：2026-09-25
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
  （分支 `codex/recover-worktree-20260920`，HEAD `5aed593`）
- 依据：`docs/code-review/2026-09-25-s04-epub-residue-ui-flow-audit.md`（P2 入口 B 丢原因、
  P3 两归档页零反馈）
- 性质：**实现 + 定向离线测试**。未操作真机、原书库、E 盘备份、原始仓库；
  未 commit/push/stage/reset/checkout/clean。
- 文件所有权遵守：只改了 ShelfViewModel、MyReadingRoute、HomeCompletedScreen 及其测试；
  **ShelfRoute 本轮零改动**；未触碰 BookRepository / ShelfBookActions / StorageSubPage（C 窗口所有）。

---

## 1. 结论摘要

审计的两项可达缺陷（P2、P3）已实现修复并有定向测试覆盖：

| 审计问题 | 修复 | 测试 |
|---|---|---|
| **P2** 入口 B（来源更新确认）丢弃 `Failed(message)`，用户只看到「N 本刷新失败：书名」 | `ShelfViewModel.confirmSourceRefresh` 改为收集失败原因，汇总由新的纯函数 `buildSourceRefreshSummary` 构造：书名折叠 + 首条原因 + 多原因时指向导入记录 | 14 条纯函数测试（新文件）+ 1 条 VM 接线测试 |
| **P3** 已读完成归档页 / 我的阅读页把 `reselectFile` 结果传 `{}`，成败全静默 | 两页补 Snackbar 反馈（`MyReadingRoute` 复用既有宿主；`HomeCompletedScreen` 补宿主），时长用 `Long`（残留文案约 90 字，Short 约 4 秒读不完） | 编译验证；**真机 UI 效果未验证**（见 §6） |

**残留备份语义（任务红线）**：入口 A 的完整守卫文案（含「本次替换已停止」「残留备份已保留」
「重复重试不会自动恢复该备份」）原样透传，未改动守卫与其测试；入口 B 的压缩短句
**逐字保留这三个关键事实**，且测试断言**不得**出现「删除」「清空」「清理」「重新下载」
「可以恢复」等引导或虚假承诺。

定向测试：**35 tests（14 新 + 21 既有），0 failures / 0 errors / 0 skipped，离线 EXIT 0**。

---

## 2. 实际改动（仅列本会话改动；各文件另有前序窗口的既有 WIP，不属于本报告）

### 2.1 `ui/viewmodel/ShelfViewModel.kt`（入口 B 修复）

**新增文件级纯函数与常量**（置于 class 之前，供 JVM 测试直接调用）：

- `SOURCE_REFRESH_SUMMARY_MAX_TITLES = 3`：汇总最多展开 3 个书名，其余折叠为「等 N 本」；
- `SOURCE_REFRESH_REASON_MAX_CHARS = 60`：非残留类原因截断上限；
- `SOURCE_REFRESH_TITLE_MAX_CHARS = 30`：单个书名截断上限；
- `SourceRefreshFailureUi(title, reason)`：失败书的展示信息；
- `truncateForSummary(text, max)`：通用截断（超限加「…」）；
- `summarizeSourceRefreshFailureReason(reason)`：
  - 含「替换残留」标记（EPUB `EpubRepository` 与 TXT/MD `ShelfImporter` 守卫同句式）→
    压缩为固定短句 **「检测到未完成的替换残留，本次替换已停止；残留备份已保留，重复重试不会自动恢复该备份」**
    —— 去掉的只有括号内的备份文件名与「旧正文若仍存在…」中间从句；
  - 其他原因截断到 60 字；空原因显式写「原因未知」；
- `buildSourceRefreshSummary(succeeded, unlocatable, failures)`：
  - 成功数如实（0 本也写「已同步 0 本」，不误报成功）；
  - 失败段 = 数量 + 前 3 书名（超限折叠）+ 首条压缩原因；
  - 失败原因不止一种时追加「其余失败原因详见导入记录」（导入记录 `entry.error` 是完整原因的既有出口）。

**`confirmSourceRefresh` 改动**：`failedTitles: MutableList<String>` →
`failures: MutableList<SourceRefreshFailureUi>`（`Failed.message` 不再丢弃）；
汇总字符串改由 `buildSourceRefreshSummary` 生成；KDoc 补充口径说明。

**不变项**：`refreshSourceUpdates()` 仍在汇总前调用（失败书继续打「可更新」标，
与既有行为一致）；成功/失败不互混、基线不因失败推进（审计 §2.4 的正确行为原样保留）。

### 2.2 `ui/screen/readinghistory/MyReadingRoute.kt`（P3 之一）

- `repairLauncher` 回调：`bookOps.reselectFile(id, uri) {}` →
  `scope.launch { snackbar.showSnackbar(msg, duration = SnackbarDuration.Long) }`（复用既有
  `snackbar`/`scope`/`SnackbarHost`，模式与该文件 ：159 的删除失败提示一致）；
- 新增 import `SnackbarDuration`。

### 2.3 `ui/screen/homearchive/HomeCompletedScreen.kt`（P3 之一，改动最大）

- **补 Snackbar 宿主**（此前 grep 计数为 0）：仿照 `MyReadingRoute` 的结构，把
  `CompletedArchiveScreen(...)` 包进 `Box(Modifier.fillMaxSize())`，底部中央放
  `SnackbarHost`；对话框仍留在 Box 外（模态覆盖层，与 MyReadingRoute 相同）；
- `repairLauncher` 回调接同一 Snackbar，`duration = SnackbarDuration.Long`；
- 新增 import：`fillMaxSize`、`SnackbarDuration`、`SnackbarHost`、`SnackbarHostState`、
  `rememberCoroutineScope`、`kotlinx.coroutines.launch`。

**范围说明**：该页 `onDownload` 的 `{}` 静默是审计未列入本链路的另一个点，本轮**未动**，
避免扩大审查面。

### 2.4 `ui/screen/shelf/ShelfRoute.kt`

**本轮零改动。** 两处既有接线（`:141` launcher、`:910/:911` 动作分发）本就把消息交给
`showMessage`；入口 B 的文案升级全部发生在 ViewModel 层，Route 无需变化。

### 2.5 测试

- **新增** `ui/viewmodel/ShelfSourceRefreshFeedbackTest.kt`（纯函数，14 tests）：
  残留语义完整性（EPUB/TXT 压缩同句）、**禁止引导词断言**（删除/清空/清理/重新下载/重试即可/可以恢复）、
  长原因截断、空原因兜底、0 本成功如实、单失败带原因、5 失败折叠（第 4 名不展开、原因只出现一次）、
  多种原因指向导入记录、超长书名截断、最坏情况总长 ≤ 260；
- **扩展** `ui/viewmodel/ShelfViewModelTest.kt`：新增
  `confirming source refresh keeps failure reason instead of dropping it`（VM 接线）——
  1 成功 + 2 残留失败 → 汇总含成功数、失败书名、三个残留关键事实，且无删书/清空/能恢复字样；
  断言失败消息内嵌实际汇总文本（便于日后排障）。

---

## 3. 命令与退出码（离线，全部在 `android/` 执行）

```
.\gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.ui.viewmodel.ShelfSourceRefreshFeedbackTest --tests com.creationreadingassistant.ui.viewmodel.ShelfViewModelTest --offline --console=plain
```

| 轮次 | 退出码 | 说明 |
|---|---|---|
| 1 | **EXIT 1** | `ShelfViewModelTest` 新用例失败：「失败书名保留」——暴露 `uiState` 是 `WhileSubscribed(5s)`，测试没订阅导致书名回退「未知书籍」（见 §4） |
| 2 | **EXIT 1** | `:app:compileDebugKotlin FAILED`——**C 窗口正在实编辑 `BookRepository.kt`**（残留保护链），`scanDirForResidue` 未定义、`hasResidueSuffix` 收据类型不匹配（`String` vs `File`）。等待其收敛后重试 |
| 3 | **EXIT 1** | 主源码已过；`compileDebugUnitTestKotlin` 失败——C 窗口把 `clearBookCache` 返回类型改成 `BookCacheClearOutcome`，本测试文件既有 stub 不匹配 |
| 4 | **EXIT 0** | `BUILD SUCCESSFUL in 37s`；**35 tests，0 failures / 0 errors / 0 skipped**（14 新 + 21 既有） |

XML 证据（`android/app/build/test-results/testDebugUnitTest/`）：

```
ShelfSourceRefreshFeedbackTest: tests=14 skipped=0 failures=0 errors=0
ShelfViewModelTest:            tests=21 skipped=0 failures=0 errors=0
```

`:app:testDebugUnitTest` 先执行 `:app:compileDebugKotlin`，因此本轮 Compose/主源码改动
（HomeCompletedScreen、MyReadingRoute、ShelfViewModel）**均经真实编译验证**。

---

## 4. 测试过程中发现并修正的两个非显而易见问题

1. **`uiState` 订阅依赖**：`confirmSourceRefresh` 从 `uiState.value.library.books` 反查书名，
   而 `uiState` 是 `SharingStarted.WhileSubscribed(5_000)`。生产中书架 UI 恒订阅它，行为正确；
   但 JVM 测试若不订阅，`uiState.value` 永远是初值（空书名 →「未知书籍」）。
   测试已补 `backgroundScope.launch { vm.uiState.collect {} }` 复现生产订阅态，并把
   `whileSubscribed` 这一隐含依赖写进用例注释。**这是测试侧修正，VM 生产代码未改。**
2. **relaxed mock 的 StateFlow 毒化 combine**：`createVm(shelfImporter = mockk(relaxed))` 时，
   `importer.importTasks/importBatch` 是 mock 出的假 StateFlow，`uiState` 的 combine 永不发射。
   测试已显式把三个流钉成真实空 `MutableStateFlow`。

---

## 5. 跨窗口事件记录（不归因、不越界）

| 事件 | 处置 |
|---|---|
| C 窗口实编辑 `BookRepository.kt`（残留保护链），其间两次使共享的 `compileDebugKotlin`/`compileDebugUnitTestKotlin` 短暂失败（`scanDirForResidue` 未定义、`String.hasResidueSuffix()` 收据不匹配） | 本窗口**不修改该文件**，等待其收敛后重试，最终 EXIT 0。失败轮次的错误行号随其编辑漂移（351→358→…），证明是进行中状态 |
| C 窗口把 `clearBookCache` 返回类型改为 `BookCacheClearOutcome`，并在**我的** `ShelfViewModelTest.kt` 的 setUp stub 里留下适配（注释自述「SEAM（residue-safe-cleanup 窗口）：…仅为编译适配，无断言语义变化」） | **保留该适配，未回滚**——它是我的文件因 C 的接口变更所必需的最小编译适配，且 C 已留下 SEAM 说明。计入 §7 的 SEAM REQUEST 台账 |

---

## 6. 未覆盖项（真机 UI 效果，全部为静态推断）

1. **Snackbar 实际呈现**：两归档页与书架页的 Snackbar 在真机上的换行、截断、可读性——
   `SnackbarDuration.Long`（约 10 秒）是否足够读完约 90 字残留文案——**未验证**。
2. **`HomeCompletedScreen` 新增 Box 包裹的布局影响**：`CompletedArchiveScreen` 内部使用
   `AppScreenScaffold`；外包一层 `fillMaxSize` Box 与 `MyReadingRoute` 同构，理论无副作用，
   但滚动、底栏遮挡、对话框层级**未在真机确认**。
3. **入口 B 汇总在真机上的长度观感**：最坏约 260 字符（10 本全失败 + 长书名），静态有界，
   但「等 N 本」折叠在 UI 上的观感未验证。
4. **入口 A 在两归档页的新反馈**：成功路径（「《书》正文已修复」）与失败路径现在都会出
   Snackbar，未真机走查。
5. **Compose 仪器测试未写未跑**：两处修复是 Compose 层接线，纯 JVM 只能覆盖策略与 VM；
   按任务约束本轮无设备，不声称任何真机通过。

---

## 7. SEAM REQUEST

| # | 事由 | 状态 |
|---|---|---|
| SEAM-1 | C 窗口（residue-safe-cleanup）因 `clearBookCache` 返回类型变更，直接编辑了本窗口所有的 `ShelfViewModelTest.kt` setUp stub（已留 SEAM 注释）。该适配已保留；**后续若 C 再改 `BookRepository` 公开接口，请按同一模式处理或提前知会**，否则共享编译单元会反复互断 | 已发生，已接纳 |
| SEAM-2 | `ShelfSourceRefreshFeedbackTest` 断言的压缩短句（「检测到未完成的替换残留，本次替换已停止；残留备份已保留，重复重试不会自动恢复该备份」）与 `ShelfImporter`/`EpubRepository` 守卫文案**语义绑定**（通过「替换残留」标记识别）。若 C 窗口后续改守卫文案，需同步知会本窗口更新压缩句与测试 | 提请知会 |
| SEAM-3 | 审计 P2 提到的批量「清缓存」无确认弹窗（`ShelfRoute.kt:390` 一带调 `clearCacheForBooks`）属于我所有文件里的行为，但其确认文案资产（`DeletionScope.REMOVE_CONTENT`）与底层保护链都在 C 窗口范围。**本轮未动**；若产品裁决要加确认弹窗，需要 C 先冻结 `ShelfBookActions.clearCacheForBooks` 的签名与 `BookCacheClearOutcome` 语义 | 提请裁决 |

**未触碰**：`BookRepository`、`ShelfBookActions`、`StorageSubPage`、协调看板、他人报告。

---

## 8. 操作自证

| 动作 | 是否执行 |
|---|---|
| 读审计报告 / 相关源码 / 既有测试 | ✅ |
| 修改本窗口所有文件（ShelfViewModel、MyReadingRoute、HomeCompletedScreen + 测试） | ✅ |
| 新增测试文件 `ShelfSourceRefreshFeedbackTest.kt` | ✅ |
| 离线运行定向 `testDebugUnitTest` | ✅ EXIT 0（35 tests 全绿） |
| 修改 ShelfRoute / BookRepository / ShelfBookActions / StorageSubPage | ❌ |
| adb / 安装 / instrumentation / 操作手机 / MuMu | ❌ |
| 触碰原书库 / E 盘备份 / 原始仓库 / Claude 记忆库 | ❌ |
| commit / push / stage / reset / checkout / clean | ❌ |
| 删除或改写他人报告 | ❌ |

本会话新增/修改的文件全集：

- 修改：`android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/ShelfViewModel.kt`
- 修改：`android/app/src/main/java/com/creationreadingassistant/ui/screen/readinghistory/MyReadingRoute.kt`
- 修改：`android/app/src/main/java/com/creationreadingassistant/ui/screen/homearchive/HomeCompletedScreen.kt`
- 修改：`android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ShelfViewModelTest.kt`
  （+ C 窗口的 1 处 stub 编译适配，见 §5）
- 新增：`android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ShelfSourceRefreshFeedbackTest.kt`
- 新增：本报告
