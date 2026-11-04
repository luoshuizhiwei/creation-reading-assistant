# 阶段 E — 122 行「保留 HOLD」中 16 行「仍待裁决」逐项裁决（2026-09-29，只读）

> **窗口**：E 阶段剩余 HOLD 只读核查（16 行仍待裁决项），与 B1 隔离验证窗口并行。
> **工作仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> **分支**：`codex/recover-worktree-20260920`；**HEAD 已核对 = `513891d6a66a9b78e072ba759d34ed580d377ecf`**（首批 A `209ec8a` → B `513891d` 之后）；`origin/main` 仍为 `5aed593`。
> **输入**：[AGENTS.md](../../../../AGENTS.md)、[当前交接](../../../handoff/current.md)、[剩余 HOLD 逐行归属](2026-09-29-stage-e-remaining-hold-mapping.md)、`E:\creation-reading-assistant\stage-e-incremental-20260929\hold-180-row-mapping-updated.csv`（SHA-256 `ffb5fc2309ccd39d52dcc0488e049c153b61a4a9802c7fad12abfa8e18ad7bf7`）。
> **性质**：只读核查。**未改** CSV、产品源码、测试、公共配置、其他窗口报告；**未** stage / commit / push / reset / checkout；**未**构建、**未**操作设备、**未**迁目录、**未**触碰原仓库与正式书库。仓库内唯一写入 = 本文件。

---

## 一、结论摘要

### 1.1 16 行确认

「仍待裁决」= `hold-180-row-mapping-updated.csv` 中 `mapping = 保留 HOLD` 的 122 行里，被[剩余 HOLD 逐行归属](2026-09-29-stage-e-remaining-hold-mapping.md) §4.2 逐行点名的那 16 行。ID、路径、Git 状态本轮已逐条回读核对（`git status --porcelain -uall`）：

| # | ID | 文件（仓库相对路径） | Git 状态 | `-U0` hunk | 行数 |
|---|---|---|---|---|---|
| 1 | H005 | `android/app/src/main/java/com/creationreadingassistant/App.kt` | ` M` | 10 | 206 |
| 2 | H015 | `android/app/src/main/java/com/creationreadingassistant/data/repository/SyncRepository.kt` | ` M` | 27 | 616 |
| 3 | H023 | `android/app/src/main/java/com/creationreadingassistant/feature/library/LibraryRootStore.kt` | ` M` | 2 | 156 |
| 4 | H024 | `android/app/src/main/java/com/creationreadingassistant/feature/library/LibrarySource.kt` | ` M` | 2 | 167 |
| 5 | H025 | `android/app/src/main/java/com/creationreadingassistant/feature/library/LibrarySourceIndex.kt` | ` M` | 2 | 267 |
| 6 | H026 | `android/app/src/main/java/com/creationreadingassistant/feature/library/ShelfImporter.kt` | ` M` | 31 | 1213 |
| 7 | H028 | `android/app/src/main/java/com/creationreadingassistant/feature/sync/WebDavBackup.kt` | ` M` | 33 | 471 |
| 8 | H103 | `android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/LibraryBrowserViewModel.kt` | ` M` | 16 | 587 |
| 9 | H122 | `android/app/src/test/java/com/creationreadingassistant/feature/library/SafLibrarySourceTest.kt` | ` M` | 1 | 137 |
| 10 | H138 | `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ShelfBookActionsTest.kt` | ` M` | 4 | 247 |
| 11 | H139 | `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ShelfImporterTest.kt` | ` M` | 18 | 1527 |
| 12 | H140 | `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ShelfViewModelTest.kt` | ` M` | 14 | 667 |
| 13 | H141 | `android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/readinghistory/MyReadingTimelineRowGestureTest.kt` | `??` | 新文件 | 262 |
| 14 | H174 | `android/app/src/test/java/com/creationreadingassistant/ui/screen/shelf/ShelfSourceRefreshCopyTest.kt` | `??` | 新文件 | 71 |
| 15 | H176 | `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/LibraryBrowserSelectionTest.kt` | `??` | 新文件 | 174 |
| 16 | H177 | `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/LibraryBrowserSortTest.kt` | `??` | 新文件 | 227 |

### 1.2 三类计数

| 类别 | 项数 | ID |
|---|---|---|
| **已裁决**（归属与 hunk 边界可直接判定并有证据，无开放产品问题） | **2** | H138、H176 |
| **仍待证据**（归属可指向，但必须先拆 hunk 和/或做隔离编译才能成立） | **8** | H005、H025、H026、H103、H139、H140、H141、H174 |
| **需用户决定**（存在只有用户能拍板的产品/范围问题，且决策会改变拆分方式） | **6** | H015、H023、H024、H028、H122、H177 |
| 合计 | **16** | |

**分类规则（本报告的判定口径，避免口径漂移）：**

1. 若必须先由用户回答一个产品/范围问题（新增能力是否纳入 E、两选项是否语义重复、门控语义为何），才能决定拆哪些 hunk → **需用户决定**；
2. 否则，若归属组可确定、但该组从未实际执行 hunk 拆分、或从未在隔离目录编译过 → **仍待证据**；
3. 只有「归属组 + 具体 hunk/测试边界都已由本轮实测确定，且不存在待答产品问题」才记 **已裁决**。

**重要限定：本轮零构建。** 「已裁决」只表示**归属与边界已可判定**，**不表示已可编译**。16 行**全部**仍缺隔离编译证据（见 §六）。

### 1.3 与 B1 窗口的关系

16 行中**无一行**属于 B1（S03 搜索索引并发）候选本身；但有 **3 行与 B1 窗口存在 hunk 或编译依赖重叠**，另有 1 行仅需登记。按纪律**只记 SEAM REQUEST，不改、不替 B1 裁决其补丁**（见 §五）。

---

## 二、方法与证据来源

### 2.1 如何精确锁定这 16 行

- 权威清单是[剩余 HOLD 逐行归属](2026-09-29-stage-e-remaining-hold-mapping.md) §4.2 的 16 行表；CSV 中这 16 行的 `mapping` 字段均为 `保留 HOLD`，`hunk_detail` 均为 `needs manual attribution`。
- **CSV 的 `rule_provenance` 不能作为选择器**：`rule_provenance` 以「缺证据」开头的行共 **53** 行，而这 53 行正是本轮处理的那批（含 §4.1 已归 §2.9 HOLD 桶、无需新裁决的 37 行）。因此「缺证据」前缀不区分「仍待裁决」与「已归桶」，本报告以 §4.2 的逐行点名为准，并用 `mapping = 保留 HOLD` 交叉核对（122 行）。
- 本轮另外核对了 122 行清单与 16 行的对应关系：16 行全部在 122 行内，且 122 行中再无第 17 行被 §4.2 点名。

### 2.2 判定只采用三类证据

| 证据类型 | 手段 | 说明 |
|---|---|---|
| **符号存在性** | `git grep -n <符号> HEAD -- android` | 区分「HEAD 已有」与「工作树新增」。凡判「新增依赖」必给出 HEAD 命中为 0 的实测结果。 |
| **调用关系** | 全仓 `grep` 穷举消费者（含 KDoc 引用） | 不按文件名/目录前缀归类。例：`LibraryRootStore.kt` 未一律归 R3，`ui/screen/stats/**` 未一律归 V5。 |
| **既有项目决定** | `docs/**` 逐文件检索并点名引用 | 只采已被本轮复核或交接引用的报告，且必须给出可回读的位置。 |

### 2.3 只读边界（本轮实际执行）

- 只读命令：`git status --porcelain -uall`、`git diff -U0/-U1/-U2/-U3`、`git diff --numstat`、`git show HEAD:<path>`、`git grep HEAD`、`git cat-file -e`、全仓 `grep`、`Import-Csv` 读 CSV。
- **未运行**任何 Gradle 命令（无 `compileDebugKotlin`、无 `testDebugUnitTest`、无 `lintDebug`）；**未**连接任何设备；**未**写 CSV 或任何其他文件。

---

## 三、16 行逐项总表

| ID | 文件 | 类别 | 归属结论 | 关键证据位置 | 尚缺验证 |
|---|---|---|---|---|---|
| H005 | `App.kt` | 仍待证据 | G3/S06 启动主题镜像 + S04 启动隔离（已定设计）+ 启动诊断增强 | `App.kt:11/82/126-128/165`；`SettingsStore.kt:369`；`OnboardingOverlay.kt:77`；`git grep HEAD` 对 `BootMirror`/`ONBOARDING_PREFS` 均 0 命中；`2026-09-25-s04-instrumentation-startup-isolation-design.md` §2.1/§3.2 | 拆 H021/H038 的 hunk；隔离编译 |
| H015 | `SyncRepository.kt` | **需用户决定** | 拆两族：(a) 本地列保留合并（数据安全，可独立）(b) `SyncOptions`/`ConflictStrategy` 门控（无批次） | `SyncRepository.kt:101/164/230/264/307/315`；`SyncOptions.kt:12/40`（未跟踪、**不在 180 行矩阵内**）；`SyncSubPage.kt`=H070、`ProfileSyncEngine.kt`=H105（均 §2.9 桶）；`2026-09-29-stage-e-hold-data-sync.md` §2.7/§3.3/§6.3 | Q2 决策；两族 hunk 拆分；隔离编译 |
| H023 | `LibraryRootStore.kt` | **需用户决定** | 库浏览器「最近添加」排序项（R3/库浏览器域） | `LibraryRootStore.kt:33/40`；`2026-09-20-android-s06-reader-startup-library-slider-review.md` §[P2] L123-129；`agent-stage-b-core-audit.md` L31（S06-⑧=产品决定）；`2026-09-19-android-worktree-product-inventory.md` L283 | Q1 决策；隔离编译 |
| H024 | `LibrarySource.kt` | **需用户决定** | 同上（`ADDED_DESC` 与 `MODIFIED_DESC` 同比较器） | `LibrarySource.kt:141/147/149`；同上三份报告 | Q1 决策；隔离编译 |
| H025 | `LibrarySourceIndex.kt` | 仍待证据 | **M-BUG-03「来源更新确认」组**（新组） | `LibrarySourceIndex.kt:61/84/108`；消费者穷举仅 `ShelfSourceUpdateChecker.kt:85/86` 与 `ShelfImporter.kt:279/354`；`snapshot()`/`markObserved()` 在 HEAD 已有（`git show HEAD:…:135/148`） | 该组 hunk 拆分（跨 6 个 B6 候选文件）；隔离编译 |
| H026 | `ShelfImporter.kt` | 仍待证据 | 跨 5 族：B1 + S04 回滚/残留 + M-BUG-03 + S04-3/4 + S05-④/NBB | `ShelfImporter.kt:18/96/287`（B1）；`2026-09-29-stage-e-hold-data-sync.md` §3.3（31 hunk 跨三族） | 手工 hunk 编辑（5 族）；隔离编译；**SR-1** |
| H028 | `WebDavBackup.kt` | **需用户决定** | 拆两族：WebDAV 客户端加固（新组）+ Legado ZIP（→B4） | `WebDavBackup.kt:80/163/250`；本轮复算 diff 内 `S14`=0、`library_source_refs`=0、`Legado`=1；`2026-09-29-stage-e-hold-data-sync.md` §2.5/§4-#4/§6.3 | Q3 决策；两族 hunk 拆分；`WebDavBackupTest.kt` 按用例归属；隔离编译 |
| H103 | `LibraryBrowserViewModel.kt` | 仍待证据 | 拆两族：(a) 排序切换滞后修复 (b) 搜索/选择边界修复 | `LibraryBrowserViewModel.kt:74/134/267-270/289-316/416`；`2026-09-20-…-slider-review.md` L99-105 §[P1] 与 L136 正向结论 | 两族 hunk 拆分；隔离编译 + 定向 JVM |
| H122 | `SafLibrarySourceTest.kt` | **需用户决定** | 随 `ADDED_DESC` | `SafLibrarySourceTest.kt:68-95`（两条新用例）；同上 Q1 证据 | Q1 决策；隔离编译 |
| H138 | `ShelfBookActionsTest.kt` | **已裁决** | **B2（S04 来源替换与残留清理组）** | `ShelfBookActionsTest.kt:6/8/149/158+`；`BookRepository.kt:971/1010`（`git grep HEAD` 0 命中）；`ShelfBookActions.kt:162` | 隔离编译 + 定向 JVM（7 条 cache 用例） |
| H139 | `ShelfImporterTest.kt` | 仍待证据 | 跨 5 族：S04 回滚/残留 + S04-3/4 + M-BUG-03 + R3 + **B1** + S05-④ | `ShelfImporterTest.kt:16/83/1059/1145/1252/1351`；`-U0` 三块 `+500,448`/`+1059,268`/`+1350,177` | 按段手工拆分；隔离编译；**SR-2** |
| H140 | `ShelfViewModelTest.kt` | 仍待证据 | 跨 M-BUG-03 + M-BUG-09 + S04-1 + **B1 依赖** | `ShelfViewModelTest.kt:11/16/141-142/165/178-179`；`ShelfViewModel.kt`=H112（B6 候选，19 hunks 6 特征） | `ShelfViewModel.kt` hunk 拆分；隔离编译；**SR-3** |
| H141 | `MyReadingTimelineRowGestureTest.kt` | 仍待证据 | 跨 B3（V5 时长）+ §2.9 桶内 `MyReadingScreen.kt`/`MyReadingRoute.kt` | `MyReadingTimelineRowGestureTest.kt:18/19/25/26/100/110/137/176-177/200/210`；`git grep HEAD` 对 `DurationUnit`/`LocalDurationUnit`/`LocalDurationUnitToggle` 0 命中；`MyReadingScreen.kt` diff 新增 `combinedClickable(onClickLabel="打开书籍", onLongClickLabel="打开管理操作")` | 拆 H072/H073；B3×B6 批次边界；androidTest 编译 + 设备执行（从未运行） |
| H174 | `ShelfSourceRefreshCopyTest.kt` | 仍待证据 | **M-BUG-03** 文案守门（生产符号在 `ShelfRoute.kt` H085 的 MIXED hunk 内） | `ShelfSourceRefreshCopyTest.kt` 全文；`ShelfRoute.kt:77/84`（`git grep HEAD` 0 命中）；`hold-ui` §3.3 hunk 3 判 MIXED；姊妹测试 `ShelfSourceRefreshFeedbackTest.kt`=H180（口径不一致） | 拆 `ShelfRoute.kt` hunk 3；与 H180 归组一致化；隔离编译 |
| H176 | `LibraryBrowserSelectionTest.kt` | **已裁决** | 与 H103 的 (b) 族同批（库浏览器搜索/选择边界） | `LibraryBrowserSelectionTest.kt:47/64/82/102/125`（只用 `LibrarySortMode.NAME`）；依赖符号全部在 HEAD 已有 | 隔离编译 + 定向 JVM（4 条） |
| H177 | `LibraryBrowserSortTest.kt` | **需用户决定** | 排序切换修复 + 搜索过滤目录；但**被 `ADDED_DESC` 门控** | `LibraryBrowserSortTest.kt:97/102/107` 显式用 `LibrarySortMode.ADDED_DESC`；`2026-09-20-…-slider-review.md` L146-147（该次执行在混合工作树上，不能作拆分证据） | Q1 决策；隔离编译 + 定向 JVM |

---

## 四、逐项详述

### 4.1 已裁决（2 项）

#### H138 `ShelfBookActionsTest.kt` — 归 B2（S04 来源替换与残留清理组）

**实际问题**：文件 `+84/-1`，4 个 `-U0` hunk。改动**全部**是 `clearCacheForBooks` 的新分档文案：把桩从 `returns Unit` 改为 `returns BookCacheClearOutcome.Cleared`（`:149`），并新增 6 条用例，逐条断言 `RefusedResidue` / `RefusedUnsafeScan` / `AbortedMidway(RESIDUE_APPEARED)` / `AbortedMidway(LOCATION_UNREADABLE)` / `ClearedWithRemains` / 混合场景的措辞，且都断言「部分执行不得报成功」。

**可直接判归属的证据**：

- 新增 import 只有 `BookCacheClearOutcome`、`MidwayAbortCause`（`:6`、`:8`）。二者定义在 `data/repository/BookRepository.kt:971`（`sealed interface BookCacheClearOutcome`）与 `:1010`（`enum class MidwayAbortCause`）；`git grep -n 'BookCacheClearOutcome\|MidwayAbortCause' HEAD -- android` **0 命中** ⇒ 确属工作树新增、且属 B2 的 `clearBookCache` 结果对象族（决策包 §2.2）。
- 全文件 diff 内**无** S05 / NBB / M-BUG-03 符号。`:5` 的 `import …ReadingCompletionState` 是 HEAD 既有上下文行（diff 中为未变更行），不是新增 ⇒ hold-ui §5.7-101 的「S05+cache-clear，需确认」可**收窄为纯 cache-clear**。
- 被测方法 `ShelfBookActions.clearCacheForBooks`（`ShelfBookActions.kt:162`）本身是 B2 hunk；同文件另两个 hunk 分别是 imports（B2）与 `markFinished` 族（B6）。

**尚缺**：隔离编译 + `ShelfBookActionsTest` 定向 JVM。
**不得写成已可编译**：`ShelfBookActions.kt` 整文件在 hold-ui §5.1-8 被标为「组合 A / B6 候选」，必须先按 hunk 把 cache-clear 部分拆出；H111 的 B6 归属对本文件是**不完整的**（该文件同时含 B2 的 2 个 hunk）——这是 E 内部 seam，不是与 B1 的重叠。

#### H176 `LibraryBrowserSelectionTest.kt` — 与 H103 的 (b) 族同批

**实际问题**：未跟踪新 JVM 测试（174 行），4 条用例断言搜索场景下的选择边界：全选只选当前结果、反选不选隐藏文件、换搜索词清空旧选择、导入边界剔除残留隐藏选择。

**可直接判归属的证据**：

- 被测对象是 `LibraryBrowserViewModel` 的 (b) 族 hunk：`selectAllVisible`（`:289`）、`invertSelection`（`:296`）、`selectedUris`（`:310`）、`visibleFileDocumentIds`（`:416`）、`setQuery` 换词清选择。
- 依赖符号**全部在 HEAD 已存在**：`LibrarySourceIndex.snapshot()` / `markObserved()`（`git show HEAD:…LibrarySourceIndex.kt:135/148`）、`LibrarySortMode.NAME`、`LibraryEntry` / `LibraryListing` / `LibraryRoot`。
- **不依赖 `ADDED_DESC`**（`:125` 只用 `LibrarySortMode.NAME`）⇒ 不被 H023 的产品决策门控。
- 与既有审查的要求逐字对应：`2026-09-20-…-slider-review.md` L103「测试必须断言最终 `selectedUris()`，不能只断言屏幕上剩几行」。

**尚缺**：隔离编译 + 定向 JVM（4 条）。

---

### 4.2 仍待证据（8 项）

#### H005 `App.kt`

**实际问题**：10 个 `-U0` hunk 混了四族：①启动诊断增强（`cra_fatal.log` 增写设备信息 / heap / 版本 + `AppContextHolder.appVersion`，`:52/:75/:183`）②`ONBOARDING_PREFS` 预热（`:82`）③三项启动接线移入 `cra-startup` 后台线程（`:149/:155/:164/:165`）④S06 主题镜像写入（`:126-128`）。

**证据与调用方**：

- `git grep -n 'ONBOARDING_PREFS' HEAD -- android` 与 `git grep -n 'BootMirror' HEAD -- android` **均 0 命中** ⇒ 两个符号都是工作树新增：
  - `OnboardingOverlay.kt:77 internal const val ONBOARDING_PREFS = "app_prefs"` —— H038，属 §2.9「Profile/Stats 大重写」HOLD 桶；
  - `SettingsStore.kt:369 internal object BootMirror` —— H021，属 §2.9「Reader 设置覆盖与选区动作」HOLD 桶。
- `MainActivity.kt` 亦为 ` M` 且新增使用 `BootMirror`（HEAD 版无该符号）⇒ 同一族改动还有第三个文件未在本 16 行内。
- ③④属**已定设计**，不是新提案：`docs/coordination/2026-09-25-s04-instrumentation-startup-isolation-design.md` §2.1 逐字引用 `App.kt:141-162` 的 `cra-startup` 三接线、§3.2 逐字引用 `App.kt:82` 的 `app_prefs` 预热，并判定「已证实」。
- 决策包 §2.1–2.8 的文件清单逐一核对：**B1–B8 均未列 `App.kt`**。

**尚缺**：

1. 把 `SettingsStore.kt` 的 `BootMirror` 族与 `OnboardingOverlay.kt` 的 `ONBOARDING_PREFS` 从各自「整文件 HOLD」中拆出——两文件分别 29 / 3 hunks，**至今没有任何 hunk 级归属**；`SettingsStore.kt` 本身还跨「reader 设置键」与「S06 boot mirror」两族（hold-data-sync §3.3），且引用被判 `EXCLUDED` 的 `ReaderDayNightToggle`。
2. `App.kt` 在 HEAD 上的隔离编译。**当前若只取 `App.kt`，编译必然失败**（`unresolved reference: ONBOARDING_PREFS` / `BootMirror`）。

**不得写成**：「App.kt 属启动诊断域」⇒ 已可编译。缺 H021/H038 的 hunk 即编译失败。

#### H025 `LibrarySourceIndex.kt`

**实际问题**：`+66/-0`，只新增两个 `internal` 顶层纯函数加一个 projection：`sourceDocumentUriOf(ref, treeUri, rootDocumentId)`（`:61`，按树 URI / 按 `authority + documentId` 两条授权路径构造文档 URI）与 `querySourceMetadata(resolver, uri)`（`:84`，只 stat `size`/`last_modified`，不读内容），以及 `SOURCE_METADATA_PROJECTION`（`:108`）。

**归属可直接判定的证据（消费者穷举）**：

- `sourceDocumentUriOf` 全仓仅 2 处调用：`ShelfSourceUpdateChecker.kt:85`、`ShelfImporter.kt:279`（另有 KDoc 引用 `ShelfImporter.kt:250`）。
- `querySourceMetadata` 全仓仅 2 处调用：`ShelfSourceUpdateChecker.kt:86`、`ShelfImporter.kt:354`（另有 KDoc 引用 `ShelfImporter.kt:346`）。
- 两者同属 **M-BUG-03「来源更新确认」** 链路；`ShelfSourceUpdateChecker.kt` 与其测试 `ShelfSourceUpdateCheckerTest.kt` 均为未跟踪新文件（`git status` = `??`），并被 hold-data-sync §2.7 判为「`EXCLUDED` 不成立」（排除则 `ShelfImporter` / `ShelfViewModel` 无法编译）。
- 新增部分对 HEAD 的**唯一**缺口就是这两个函数自身的消费者：`snapshot()` / `markObserved()` 在 HEAD 已有（`git show HEAD:…:135/148`），文件其余内容未变。
- ⇒ 归属结论：**「M-BUG-03 来源更新确认组」（新组，无现成批次号）**。同组必带：`ShelfSourceUpdateChecker.kt` + `ShelfSourceUpdateCheckerTest.kt`（两个未跟踪文件）+ `ShelfImporter.kt` 的 M-BUG-03 hunks（`SourceRefreshResult` / `refreshFromSource` / `repairFileTyped` / `refreshSourceBaseline`）+ `ShelfViewModel.kt` 的 M-BUG-03 hunks（hold-ui §3.7 已逐条给出 hunks 1,2,4,5,6,13,14,15,16,19）+ `ShelfRoute.kt` M-BUG-03 hunks（1、3-M03、7、8、10、14、20）+ `ShelfAction.kt` hunk 3 + `ShelfUiState.kt` M-BUG-03 字段 + `BookGrid.kt` hunks 13/19 + `ShelfScreen.kt` hunk 16。

**尚缺**：

1. 该组的 hunk 级边界虽已由 hold-ui §2.6/§3.x 给出，但**从未实际执行过拆分**；`ShelfUiState.kt` hunk 1、`BookGrid.kt` hunk 8/13、`ShelfRoute.kt` hunk 1/3/8、`ShelfScreen.kt` hunk 16 都是 MIXED hunk，必须**手工编辑**（hold-ui §4.1/§4.2 已列）。
2. 隔离编译 + `ShelfSourceUpdateCheckerTest`（6 例）+ M-BUG-03 定向 JVM —— 本轮、以及任何既有候选上**均未执行**。

#### H026 `ShelfImporter.kt`

**实际问题**：31 个 `-U0` hunk / `+387/-24`，跨 **5 族**：

| 族 | 内容 | 关键位置 |
|---|---|---|
| **B1（S04-2）** | `SearchIndexBookRefreshCoordinator` import、构造注入、`request(bookId)` | `:18`、`:96`、`:287` |
| S04 回滚/残留 | `oldCopyMovedToBackup` / `newCopyPromoted` 阶段分流、同名 backup 残留守卫 | 替换路径内 |
| **M-BUG-03** | `SourceRefreshResult`、`refreshFromSource`、`repairFileTyped` | `SourceRefreshResult` 定义 + `:236-296` 一带 |
| S04-3/S04-4 | `refreshSourceBaseline`、`findInternalFile`、`computeFileHash`、`computeCandidateFingerprint` | `:337+`、`:409+` |
| S05-④/NBB | `ImportBatchSuccesses`、`onBookImported`、`batchSuccesses`、`BATCH_EMIT_THROTTLE_MS`、`ImportOutcome.Success(bookId)` | `:41+`、`:99`、`:106`、`:121` |

**证据**：

- 上述符号逐一定位（见总表）。
- hold-data-sync §3.3 已记本文件「31 hunk 跨 S04/S05/R3 三族，且引用两个被判 EXCLUDED 的新文件」；本轮细化为 **5 族**，并新增 B1 一族的精确定位。
- **与 B1 的 hunk 冲突见 §五 SR-1**：`indexRefreshCoordinator.request(bookId)`（`:287`）落在 `git diff -U0` 的 hunk `@@ -168,2 +236,61 @@` 内，与 M-BUG-03 的 `SourceRefreshResult` + `refreshFromSource` **同一 hunk**。

**尚缺**：①手工 hunk 编辑把 5 族切开（`git add -p` 不足，见 SR-1）；②M-BUG-03 组与 B6 组的边界确认——本文件同时是 `ShelfImportFocusTargetTest.kt`（H173，B6 候选）的被测类；③隔离编译。

#### H028 `WebDavBackup.kt`

**实际问题**：33 个 `-U0` hunk / `+324/-77`，实际是**两个工作流**：

- **WebDAV 客户端加固**：`put` 增 MKCOL（409/404 后建目录并重试）、新增 `ensureCollection`（`:80`）、`test` 改 PROPFIND + OPTIONS 回退、`get` 增 `withRetry`、`list` 增 `D:` 命名空间感知与 collection 过滤、`resolveTarget` 改 `HttpUrl`、`basicAuth` 显式 UTF-8、`isPermittedHttpHost`、`requireSafeFilename` 放宽 Unicode、`parsePropFind` 命名空间剥离。
- **Legado ZIP 支持**：新增 `getBytes(…, maxBytes)`（`:163`）与 `listLegadoBackups`（`:250`，只列 `backup*.zip`）。

**证据**：

- 本轮复算：`git diff -U0` 中 `S14` 命中 **0** 次、`library_source_refs` 命中 **0** 次、`Legado` 命中 **1** 次（仅 `listLegadoBackups`）⇒ 决策包 §2.5 所写「`WebDavBackup.kt` 8 hunks（S14 + R3 混合）」在**内容上不成立**（与 hold-data-sync §2.5 一致）。
- 消费方：`ProfileWebDavManager.kt:34/36/76`（H107，§2.9 Profile HOLD 桶）、`ProfileViewModel.kt:607/633`（H106，B4 候选；`legadoBackups` 属 S12/Legado 族）、`WebDavSubPage.kt`（H071，§2.9 桶，`+471/-347`）、`ProfileUiState.kt:135/136`（H063，§2.9 桶）、`ProfileRoute.kt:454`（H062，§2.9 桶）。
- `WebDavBackupTest.kt` 为 ` M`、`+472/-0`，**不在 180 行 HOLD 矩阵内**；hold-data-sync §4 修正 #3 建议其归「WebDAV 升级组 + R3 用例组」。
- hold-data-sync §6.3 已把「WebDAV 升级」列为需总指挥裁决是否纳入 E 的 5 类无组工作流之一。

**需用户决定的问题**：见 §四 4.3 的 Q3。

**尚缺**：①加固族与 Legado 族的 hunk 边界（按方法切分，本轮已定位但未执行）；②`WebDavBackupTest.kt` 按用例拆分的归属表；③隔离编译。

#### H103 `LibraryBrowserViewModel.kt`

**实际问题**：16 个 `-U0` hunk，两族：

- **(a) 排序切换滞后修复**：`BrowserCore.sortModeOverride`（`:74`）、`uiState` 优先读覆盖值（`:134`）、`setSortMode` 先乐观写本地再**显式传参**重列（`:267-270`）、`loadDirectory(documentId, sortMode)`。修的是「写完 DataStore 立刻回读 `rootStore.root.value`，而该 StateFlow 由 ApplicationScope（IO）异步发射，重列仍用旧排序」。
- **(b) 搜索与选择边界修复**：搜索同时过滤 `directories`（此前只过滤 `files`）、`setQuery` 换词时清空选择、`selectAllVisible` / `invertSelection` / `selectedUris` 全部收敛到 `visibleFileDocumentIds()`（`:289`、`:296`、`:310`、`:416`）。

**证据（两族均被既有审查确认，且均为修复而非新功能）**：

- `2026-09-20-android-s06-reader-startup-library-slider-review.md` L136「已确认的正向结果：目录排序切换不再回读滞后的 DataStore StateFlow；搜索已同时过滤文件夹和文件」。
- 同文 L99-105 §[P1] 给出修复前的真实缺陷：「用户搜索到 1 个文件后点『全选』，实际会选中整个目录……最终却把隐藏文件一起交给导入器」，并要求「应以当前可见 documentId 集合执行全选/反选，并明确搜索词变化后隐藏选择是保留、提示还是清除；测试必须断言最终 `selectedUris()`」——(b) 族正是该要求的实现，H176 正是其守门测试。
- (a) 对**任意** `LibrarySortMode` 成立，不依赖 `ADDED_DESC`；(b) 不依赖任何新增枚举或新增文件。

**尚缺**：①把 (a)/(b) 两族 hunk 拆开——(a) 的字段声明（`:70-74`）与读取点（`:134`）、写入点（`:270`）分散，(b) 的 hunk 分散在 6 处；②隔离编译 + `LibraryBrowserSortTest` / `LibraryBrowserSelectionTest` 定向 JVM。
**注意**：H103 **本身**不被 Q1（`ADDED_DESC`）门控；被门控的是 H177（见 4.3）。

#### H139 `ShelfImporterTest.kt`

**实际问题**：`+939/-3`，18 个 `-U0` hunk，含 **9 个特征段**（文件内 `// =====` 标记实测）：

| 段 | 文件行 | 族 |
|---|---|---|
| TXT 重新选文件回滚阶段分流 | L500 | S04 |
| 崩溃残留守卫 | L611 | S04 |
| `computeFileHash` 策略性 null vs 故障异常 | L753 | S04-3 |
| 按来源一键刷新 | L880 | **M-BUG-03** |
| 来源引用（路线第 3 组） | L948 | R3 |
| **S04-2 异步索引重建接缝** | **L1059** | **B1** |
| S04-3 / S04-4 基线重算 | L1145 | S04 |
| S05-④ 本批成功 id | L1252 | NBB / B6 |
| `books.content_hash` 同步 | L1351 | S04 |

**证据**：

- `git diff -U0` 实测三个大块：`@@ -456,0 +500,448 @@`、`@@ -567,0 +1059,268 @@`、`@@ -590,0 +1350,177 @@`。**第二块（新文件行 1059–1326 全部是新增行，中间无未变更行）连续包含 B1 的 S04-2 五条用例 + S04-3/4 + S05-④ 三段** ⇒ B1 的五条用例**无法**用 `git add -p` 单独取出（见 SR-2）。
- 导入面亦跨域：`import …SearchIndexBookRefreshCoordinator`（`:16`，B1）、`import …SourceFingerprint`（`:24`，S04）、字段 `indexRefreshCoordinator`（`:83`）等。
- hold-ui §5.5-28 判本文件为「M-BUG-03+NBB」并自注「需确认是否全 M-BUG-03/NBB」；**本轮实测该判定不成立**——还含 S04 回滚/残留、S04-3/4、R3、B1 共五族。

**尚缺**：①按段手工拆分并给出每段归属表；②B1 五条用例与 S04-3/4、S05-④ 的分离（SR-2）；③隔离编译 + 定向 JVM。

#### H140 `ShelfViewModelTest.kt`

**实际问题**：`+276/-6`，14 个 `-U0` hunk。新增导入含 `BookCacheClearOutcome`（B2）、**`SearchIndexBookRefreshCoordinator`（B1）**、`LibrarySourceIndex` / `LibrarySourceRef` / `ShelfSourceUpdateChecker` / `SourceRefreshResult`（M-BUG-03）、`LibrarySourceAvailability`；新增用例覆盖 M-BUG-09 骨架门（`library loaded stays false until room emits`）、S04-1 前置确认 4 条、`shelfBooks` 无收集者加载等。

**证据**：

- **硬依赖 B1 的未跟踪新文件**：`:165` 在构造真实 `ShelfImporter` 时传 `indexRefreshCoordinator = mockk(relaxed = true)`（`:16` 为 import）⇒ `SearchIndexBookRefreshCoordinator.kt` 不存在则本测试**编译失败**。该文件的提交状态与 B1 窗口绑定（见 SR-3）。
- 被测类 `ShelfViewModel.kt` 为 H112（B6 候选），但其构造参数同时含 `sourceIndex`（H025 / M-BUG-03）与 `indexRefreshCoordinator`（B1）⇒ 本测试的编译闭包**同时跨 B1、M-BUG-03、B6**。
- hold-ui §5.1-7 记 `ShelfViewModel.kt` 19 hunks 跨 S05 / M-BUG-03 / NBB / M-BUG-09 / FEAT / prefs 六特征「需手动编辑」。

**尚缺**：①`ShelfViewModel.kt` 的 hunk 级拆分（6 族）与对应测试拆分；②B1 依赖的处置（SR-3）；③隔离编译。

#### H141 `MyReadingTimelineRowGestureTest.kt`

**实际问题**：未跟踪新 androidTest（262 行），渲染**生产入口** `MyReadingScreen`，断言「整行长按 = 打开管理操作；行内时长 pill 不得有 OnClick / OnLongClick」。

**证据**：

- 依赖 **B3/V5**：`:19` `DurationUnit`、`:25` `LocalDurationUnit`、`:26` `LocalDurationUnitToggle`；`git grep HEAD` 对这三个符号 **0 命中** ⇒ 由 `ui/util/TimeUtils.kt`（H099，B3 候选）新增。
- 依赖 **§2.9 HOLD 桶内文件**：`MyReadingScreen.kt`（H073，§2.9「Profile/Stats 大重写」桶）的 diff 正是新增 `combinedClickable(onClickLabel = "打开书籍", onLongClickLabel = "打开管理操作")` 与 `LocalDurationUnit` 使用；测试的语义断言（`:176` `assertEquals("打开管理操作", longClick?.label)`、`:177` `assertEquals("打开书籍", click?.label)`）**逐字对应**这些新增标签。
- 依赖链还含 `MyReadingRoute.kt`（H072，§2.9 桶，新增 `SnackbarDuration.Long`）与 `MyReadingViewModel.kt`（H104，B6 候选，移除 `percent >= 99.5` 覆盖）。
- **口径更正**：`ReadingCompletionState`（`:18`）与 `MyReadingItem.state` 在 HEAD **已存在**（`data/local/entity/ReadingEntities.kt:13`、`ui/viewmodel/MyReadingViewModel.kt:38`）⇒ mapping 文档所写「新文件同时 import `ReadingCompletionState`(S05→B6) 与 `DurationUnit`(V5→B3)，跨两批次」**只对后者成立**；`ReadingCompletionState` 的 import 不构成新增依赖。

**尚缺**：①`MyReadingScreen.kt` / `MyReadingRoute.kt` 从 §2.9 桶中拆出（或明确本轮不纳入）；②B3 与 B6 的批次边界（该测试跨两批）；③androidTest 编译（本轮零构建）+ 设备执行（该测试**从未运行过**，无任何设备证据）。

#### H174 `ShelfSourceRefreshCopyTest.kt`

**实际问题**：未跟踪新 JVM 测试（71 行），断言来源刷新确认弹窗文案：不得声称「重置 / 清空 / 清除阅读进度」，必须含「不会主动清除已有阅读记录」「章节会重新划分」「阅读位置也可能随之变化」，且不得把位置变化说成必然。

**证据**：

- 被测符号 `SOURCE_REFRESH_PROGRESS_NOTE` 与 `sourceRefreshConfirmBody(count, titleText)` 定义在 `ShelfRoute.kt:77` / `:84`；`git grep HEAD` 对二者 **0 命中** ⇒ 由 `ShelfRoute.kt`（H085，B6 候选）的 **M-BUG-03 hunk** 新增。
- hold-ui §3.3 hunk 3 明载该 hunk 是 **MIXED**：M-BUG-09 删除（`delay`/`SkeletonMinDisplayMillis`）+ S05 `focusIdsForBatch` + M-BUG-03 `SOURCE_REFRESH_PROGRESS_NOTE` / `sourceRefreshConfirmBody`，且三者是**独立顶层声明**（可手工编辑拆分，但 `git add -p` 不可）。
- 同族姊妹测试 `ShelfSourceRefreshFeedbackTest.kt`（H180）测 `ShelfViewModel.kt:98/117` 的 `summarizeSourceRefreshFailureReason` / `buildSourceRefreshSummary`，同样落在 B6 候选文件内、同属 M-BUG-03。**mapping 文档把 H180 归 B2、把本文件留空，二者口径不一致**——本轮建议二者同组（M-BUG-03），但**不代另一窗口裁决**。
- 文案依据已有独立证据：`docs/coordination/legacy-stages/2026-09/2026-09-24-s04-progress-dialog-copy.md`（L47 表）与 `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-replacement-feedback-implementation.md` 记录了「替换链路不引用 `ReadingProgressDao`、该 DAO 无 delete / clear 方法、全仓无重置调用方」的源码事实。

**尚缺**：①`ShelfRoute.kt` hunk 3 的手工拆分；②与 H180 的归组一致化；③隔离编译 + 定向 JVM。

---

### 4.3 需用户决定（6 项）

#### Q1 —— 「最近添加」排序项是否保留（H023、H024、H122、H177）

**实际问题**：新增 `LibrarySortMode.ADDED_DESC("added_desc")`「最近添加」（`LibraryRootStore.kt:40`），并在 `sortedFor` 中把 `ADDED_DESC` 与 `MODIFIED_DESC` 指向**同一个比较器** `timeDesc`（`LibrarySource.kt:141/147/149`）。KDoc 自述理由：「SAF 只提供 `COLUMN_LAST_MODIFIED`，系统层面没有『文件加入该文件夹的时间』。文件被拷进目录时其修改时间就是拷贝时间。」

**这已是第二次被判为产品决策，且至今未决**：

- `docs/code-review/2026-09/2026-09-20-android-s06-reader-startup-library-slider-review.md` §[P2]（L123-129）：「代码注释把『拷进目录时修改时间就是拷贝时间』当成普遍事实，但**文件提供器、MTP/同步工具或保留时间戳的复制操作不保证这一点**……若产品确实要『最近添加』，需要由应用在首次观察文件时记录时间；若不建立这份数据，应只保留诚实的『修改时间』文案，避免制造不存在的能力。」证据列 `LibraryRootStore.kt:30-42`、`LibrarySource.kt:140-151`。
- `docs/coordination/reviews/undated/agent-stage-b-core-audit.md` L31：S06-⑧ 判定 = **产品决定（残留 P3 事实风险）**，并明确指出 `LibraryBrowserComponents.kt:313-316` 的注释前提「对 MTP/同步盘/保留时间戳的复制不成立」。
- `docs/research/2026-09-19-android-worktree-product-inventory.md` L283 已把「『最近添加』与『修改时间』实际完全同序」列为该批**不能整体进入候选**的理由之一。

**问题（三选一）**：

1. 保留两个菜单项 + 现有解释性 KDoc（接受「同序」这一事实，靠文案说明）；或
2. 撤掉「最近添加」，只留「修改时间」（诚实但功能变少）；或
3. 立项做应用侧「首次观察时间」记录（真正的「最近添加」，属新功能、需新 schema/存储）。

**影响面（本轮实测）**：

| 行 | 影响 |
|---|---|
| H023 `LibraryRootStore.kt` | 直接决定枚举是否保留 |
| H024 `LibrarySource.kt` | 直接决定 `sortedFor` 是否保留 `ADDED_DESC` 分支 |
| H122 `SafLibrarySourceTest.kt` | 两条新用例（`added desc sorts newest first`、`added desc keeps directories first and sinks unknown timestamps last`）随枚举存在与否 |
| H177 `LibraryBrowserSortTest.kt` | `:97/:102/:107` 显式用 `LibrarySortMode.ADDED_DESC` ⇒ **本文件当前无法在不含该枚举的候选上编译**。若选方案 2，把 3 处换成既有 `MODIFIED_DESC` 即可解耦（一处枚举值替换，非重写） |
| `LibraryBrowserComponents.kt`（H081，§2.9 桶） | `:294` 的 `sortLabel` 与 `:302` 的枚举列表同步受影响 |

#### Q2 —— 「我的 · 同步」子页开关与冲突策略是否属 E 恢复项（H015）

**实际问题**：`SyncRepository.kt` 27 个 `-U0` hunk 同时承载两个可分离的工作流：

- **(a) 本地列保留合并（数据安全修复）**：`applyBook`（`:164`）、`applyProgress`（`:230`）、`applySession`（`:264`）由「用默认值补齐的新实体整体覆盖」改为「基于 `local.copy(...)` 合并」。代码 KDoc 明写旧实现的后果：「`content_status` 默认 `"available"`、`local_content_path` 默认 null，覆盖后这本书在本机立刻变成『正文指向空路径』，表现为书架上的书打不开、读了一半的进度丢失」；进度侧「同步一次，所有书的阅读进度全部回到开头」。
- **(b) 同步项门控 + 冲突策略**：`pull(options: SyncOptions = SyncOptions())`（`:101`）、`push(options)`（`:315`）、`shouldAcceptRemote(..., strategy)`（`:307`），按 `syncBooks` / `syncProgress` / `syncInspirations` 门控，并把**等 revision** 的裁决交给 `ConflictStrategy.REMOTE_WINS`（`remoteRevision >= localRevision`）/ `LOCAL_WINS`（`remoteRevision > localRevision`）。

**证据**：

- `data/repository/SyncOptions.kt`（`:12` `enum class ConflictStrategy`、`:40` `data class SyncOptions`）与 `data/settings/SyncPrefsStore.kt` **均为未跟踪新文件，且都不在 180 行 HOLD 矩阵内**（CSV 无对应行）⇒ 排除它们则 `SyncRepository` 27 个 hunk 全部无法编译（hold-data-sync §2.7 已证，本轮复核 CSV 无此二行）。
- (b) 的 UI 消费方：`SyncSubPage.kt` = H070（§2.9 Profile/Stats HOLD 桶，`+432/-410`）、`ProfileSyncEngine.kt` = H105（§2.9 桶）、`ProfileViewModel.kt` = H106（B4 候选，但决策包 §2.4 只取 S12 hunks，`ProfileViewModel.kt:530` 的 `SyncOptions(...)` 构造**不在 B4 内**）。
- hold-data-sync §3.3 已把本文件列为 HOLD，理由逐字为「27 hunk 全部围绕 `SyncOptions` 形参；`SyncOptions.kt` 被判 EXCLUDED ⇒ 归属需先裁决『同步选项算恢复必需还是新功能』」；§6.3 把「同步选项」列为需总指挥裁决的 5 类无组工作流之一。

**问题（三个子问，建议一起拍板）**：

- **Q2a**：「我的 · 同步」子页的三个同步项开关与冲突策略，算 **E 恢复收口项**还是**新功能**？若不纳入，`SyncRepository.kt` 必须按 hunk 拆出 (a) 单独提交，(b) 整族连 `SyncOptions.kt` / `SyncPrefsStore.kt` / `SyncSubPage.kt` 一并延后。
- **Q2b**：**关闭某同步项时「该类既不上传、也不传播删除」是否是期望合同？** 现实现 `push` 侧把关闭类别推空列表（`:329-368`），代码注释自述「删除也不传播」。这决定了「用户在手机上关掉书架同步后，电脑上的删书是否永远不会同步到手机」。
- **Q2c**：**`REMOTE_WINS` 在 revision 相等时无条件接受远端是否会静默丢弃手机侧同 revision 的编辑？** 代码注释承认「等 revision 的裁决改由策略决定」，而 `REMOTE_WINS` 用的是 `>=`。

**尚缺**：(a) 的隔离编译与 `SyncRepositoryTest` 定向回归；(b) 的 hunk 级拆分证明。

#### Q3 —— WebDAV 客户端加固与 Legado ZIP 是否纳入 E（H028）

见 §4.2 H028 的问题与证据。问题：

- **Q3**：WebDAV 客户端加固（MKCOL / PROPFIND / OPTIONS / 重试 / `HttpUrl` / Unicode 文件名 / 命名空间剥离）与 **Legado ZIP 列表**（`getBytes` / `listLegadoBackups`），是否纳入阶段 E 恢复提交？
  - 若纳入，建议拆为「**WebDAV 升级组**」（`WebDavBackup.kt` 加固 hunks + `WebDavBackupTest.kt` 对应用例）与**并入 B4**（G4 / S12 Legado 导入）的 `getBytes` / `listLegadoBackups` 子集 + `ProfileWebDavManager.kt:34/36` 的接线；
  - 若不纳入，需把 `ProfileWebDavManager.kt`、`ProfileUiState.kt:135/136`、`WebDavSubPage.kt` 中引用新方法的 hunk **一并排除**，否则 `ProfileViewModel` / `ProfileWebDavManager` 编译失败。

---

## 五、SEAM REQUEST（与 B1 隔离验证窗口的重叠，只登记、不裁决）

> 依据：[B1 只读审查](../../reviews/2026-09/2026-09-29-stage-e-b1-search-concurrency-claude-review.md) §5.1 的 B1 精确候选清单，以及[该报告的复核](../../reviews/2026-09/2026-09-29-stage-e-hold-b1-report-review.md)。
> **本窗口不修改任何 B1 文件，也不替 B1 决定其补丁边界。** 以下只提供实测事实，供 B1 窗口与集成者对齐。

### SR-1 `ShelfImporter.kt`：B1 的 `request(bookId)` 调用**不能**用 `git add -p` 隔离 ★

- B1 报告 §5.1 对本文件的处理方式写为「`git add -p`，仅取包含 `indexRefreshCoordinator` 注入及 `request(bookId)` 调用的相关 hunk」。
- **实测**：`indexRefreshCoordinator.request(bookId)`（工作树 `:287`）落在 `git diff -U0` 的 hunk `@@ -168,2 +236,61 @@` 内，该 hunk 同时新增 M-BUG-03 的 `SourceRefreshResult` 数据类与整个 `refreshFromSource(...)` 方法体（`runCatching`/`sourceDocumentUriOf`/`repairFileTyped`/`refreshSourceBaseline` 全在同一块）。**该 hunk 内没有任何未变更行可供 `git add -p` 的 `s`（split）切开。**
- **结论（事实，非裁决）**：本文件若要只取 B1 部分，**必须手工编辑 hunk**；若整 hunk 取用，则会把 M-BUG-03 的 `refreshFromSource` 拉进 B1 范围。
- 另两处 B1 部分（`import` 于工作树 `:18`、构造参数于 `:96`）各自是单行新增 hunk，**可独立取用**；取构造参数即引入 `SearchIndexBookRefreshCoordinator` 类型，与 B1 已计划纳入的该未跟踪文件一致。
- 登记为 **SEAM REQUEST：`ShelfImporter.kt` 的 hunk 边界由 B1 与 M-BUG-03 共享，`git add -p` 不适用。**

### SR-2 `ShelfImporterTest.kt`：内含 5 条 B1（S04-2）用例，但 B1 清单未列本文件

- B1 报告 §5.1 未列 `ShelfImporterTest.kt`；**实测本文件确有 5 条 B1 用例**：`ShelfImporterTest.kt:1059` 起的 `// ===== S04-2：异步索引重建接缝（SearchIndexBookRefreshCoordinator） =====` 段，含 `refreshFromSource success submits index refresh request exactly once with correct bookId` 等 5 例。
- **实测**：该段与 `S04-3 / S04-4`（`:1145`）、`S05-④`（`:1252`）同处一个**连续新增块**（`@@ -567,0 +1059,268 @@`，新文件行 1059–1326 全为新增行）⇒ 同样**无法**用 `git add -p` 单独取出。
- 登记为 **SEAM REQUEST：B1 若要这 5 条用例的覆盖，需手工编辑；本窗口不代为取用，也不主张这些用例属谁。**

### SR-3 `ShelfViewModelTest.kt`：硬依赖 B1 的未跟踪新文件

- `:16` `import …SearchIndexBookRefreshCoordinator`、`:165` 在构造真实 `ShelfImporter` 时传 `indexRefreshCoordinator = mockk(relaxed = true)`。
- ⇒ 本文件（H140）的**编译状态与 `SearchIndexBookRefreshCoordinator.kt` 的提交状态绑定**：该未跟踪文件不落地，本测试编译失败。
- 登记为 **SEAM REQUEST：`ShelfViewModelTest.kt` 的编译闭包含 B1 的未跟踪新文件；本窗口不裁决该文件归谁。**

### SR-4 `App.kt`：仅登记，**当前无 hunk 冲突**

- `App.kt` 的 diff 重写了包含 `EntryPointAccessors.fromApplication(this, SearchIndexEntryPoint::class.java).searchIndexScheduler().start()` 的整块（工作树 `:157-164`）。
- B1 的候选清单**不含** `App.kt`；B1 只改 `SearchIndexScheduler.triggerNow` 的签名（移除默认值），**不改 `start()`** ⇒ **当前无 hunk 冲突**。
- 登记目的仅为：B1 若后续需要改 `App.kt`（例如统一启动接线的日志 tag），须先与本窗口/集成者对齐，因为 `App.kt` 的同一 hunk 已被本窗口的启动诊断改动占用。

### 非本 16 行的相关观察（仅记录，不展开）

- `BookRepositoryTest.kt`（H113）的 `mapping` 为「候选分组 B6 + B1」，其实测新增用例同时含 B1 族（`restoreBook calls indexRefreshCoordinator request once…`、`restoreDeletion delegates to async coordinator…`，共 5 条）与 S05 族（`markFinished …` 7 条）——与 SR-2 同型的「同一连续新增块跨两批」结构。该项不在本 16 行内，故只在此外登记。

---

## 六、尚缺的验证（汇总）

| 验证项 | 覆盖 16 行中的 | 状态 |
|---|---|---|
| **隔离编译**（HEAD + 该行候选 hunk 的 `:app:compileDebugKotlin`） | **16 / 16** | **全部未做**。本轮零构建。 |
| **定向 JVM**（各行自带测试） | H122、H138、H139、H140、H174、H176、H177（7 行有对应 JVM 测试） | 全部未在拆分后子集上执行 |
| **androidTest 编译** | H141 | 未做（本轮零构建） |
| **设备执行** | H141 | **从未运行过**，无任何设备证据 |
| **hunk 级拆分实际执行** | H005、H015、H025、H026、H028、H103、H139、H140、H141、H174（10 行） | 全部只到「清单/边界」阶段，未实际拆分 |
| **产品决策** | H015、H023、H024、H028、H122、H177（6 行） | 3 个问题待用户回答（§四 4.3） |

**外推禁止声明**：全量工作树 JVM 3208/3208 与 D 阶段 build 成功**不能**证明任一拆分后子集可编译。本轮已找到反例：`App.kt` 单独取用时因 `ONBOARDING_PREFS`（H038）与 `BootMirror`（H021）不在 HEAD 而**必然编译失败**；`LibraryBrowserSortTest.kt` 在不含 `ADDED_DESC`（H023）的候选上**必然编译失败**；`ShelfViewModelTest.kt` / `ShelfImporterTest.kt` 在不含 `SearchIndexBookRefreshCoordinator.kt`（B1 未跟踪）的候选上**必然编译失败**。

---

## 七、建议的下一批互不重叠任务

> 目标：让多个窗口可以并行推进而**文件所有权与 hunk 边界互不重叠**。每条都标注与 B1 窗口的关系。**本轮不执行其中任何一条。**

| # | 任务 | 独占文件 / hunk | 与 B1 窗口的关系 | 交付物 |
|---|---|---|---|---|
| **N1** | **B2 / S04 cache-clear 组隔离编译**（可立即做，最高确定性） | `ShelfBookActions.kt` 的 cache-clear hunk + `BookRepository.kt` 的 B2 hunks + `BookResidueSafeCleanupTest.kt` + `ShelfBookActionsTest.kt` + `StorageSubPage.kt` 文案 hunk | **无重叠**：B1 不取 `BookRepository.kt` 的 B2 hunks（B1 报告 §5.2 已明确排除） | E 盘隔离候选 + `compileDebugKotlin` EXIT 0 + `ShelfBookActionsTest` / `BookResidueSafeCleanupTest` 定向 JVM 的 XML + SHA |
| **N2** | **库浏览器搜索/选择边界修复组隔离编译** | `LibraryBrowserViewModel.kt` 的 **(b) 族 hunk 仅**（`:289-316`、`:416`、`setQuery`）+ `LibraryBrowserSelectionTest.kt`；**不含** `ADDED_DESC` 相关任何行 | **无重叠** | 同上，定向 JVM `LibraryBrowserSelectionTest` 4 条 |
| **N3** | **M-BUG-03 组边界固化（只读，不构建）** | 只读；产出 hunk 级候选清单：`LibrarySourceIndex.kt`（H025）+ `ShelfSourceUpdateChecker.kt` + `ShelfSourceUpdateCheckerTest.kt`（两未跟踪）+ `ShelfImporter.kt` M-BUG-03 hunks + `ShelfViewModel.kt` M-BUG-03 hunks + `ShelfRoute.kt` M-BUG-03 hunks + `ShelfAction.kt` hunk 3 + `ShelfUiState.kt` M-BUG-03 字段 + `BookGrid.kt` 13/19 + `ShelfScreen.kt` 16 | **重叠处必须标注 SR-1/SR-2/SR-3**，产出只作清单、**不取用** B1 部分 | 一份 hunk 级清单 + 与 B6 的 MIXED hunk 手工编辑说明，交总指挥决定是否立批 |
| **N4** | **`App.kt` 依赖拆解（只读）** | 只读给出 `SettingsStore.kt` 的 `BootMirror` 族与 `OnboardingOverlay.kt` 的 `ONBOARDING_PREFS` 最小 hunk 边界（供 G3 / S06 组使用） | 无重叠 | hunk 边界清单（含 `MainActivity.kt` 的 S06 hunk） |
| **N5** | **`WebDavBackup.kt` 族拆分（只读）** | 只读给出加固族 / Legado ZIP 族的按方法 hunk 边界 + `WebDavBackupTest.kt` 按用例归属表 | 无重叠 | 边界清单 + 用例归属表（供 Q3 决策后执行） |
| **N6** | **B1 窗口专属修正**（不在本窗口） | `ShelfImporter.kt` / `ShelfImporterTest.kt` 的 B1 部分按 SR-1 / SR-2 改为「需手工 hunk 编辑」 | 属 B1 窗口 | B1 窗口自行更新其候选清单 |

**建议执行顺序**：N1 与 N2 可**立即并行**（互不重叠、且都不被产品决策门控）；N3 / N4 / N5 是纯只读清单工作，可与 N1/N2 同时进行；N6 由 B1 窗口处理。Q1 / Q2 / Q3 三个决策可以**随时**提出，但 Q2 的答案会改变 H015 的拆分方式，Q3 会改变 H028 的归属，故建议在 N1/N2 完成后、动 `SyncRepository.kt` / `WebDavBackup.kt` 之前先取得。

**明确不做（本轮与下一批均不得顺手做）**：不按文件名前缀归类；不把候选归属写成已可编译；不用 JVM 或整树结果外推拆分后子集；不替 B1 窗口裁决其补丁；不修改 CSV、源码、测试、公共配置或其他窗口报告；不构建、不操作设备、不 stage / commit / push / reset / checkout、不迁目录、不触碰原仓库。

---

## 八、纪律声明

- 仓库内**唯一写入 = 本文件**（`docs/coordination/legacy-stages/2026-09/2026-09-29-stage-e-hold-16-adjudication.md`）。未改 `hold-180-row-mapping-updated.csv`、未改任何产品源码 / 测试 / 公共配置 / 其他窗口报告。
- 未 stage / commit / push / reset / checkout；未切换项目目录；未触碰原仓库、MuMu / ADB、正式书库或用户资产。
- 未运行任何 Gradle 命令、未构建、未做设备操作。全部「可编译 / 编译失败」表述均来自 `git grep HEAD` 的符号存在性实测与 `git diff` 的 hunk 边界实测，**不宣称任何候选已可编译**。
- 本报告不裁决产品行为：§四 4.3 的三组问题只指出「缺什么决策、决策会改变什么」，不给结论。
