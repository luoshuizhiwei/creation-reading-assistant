# 阶段 E 候选 B1（S03 搜索索引并发）只读审查（Claude，2026-09-29）

> **性质**：只读审查，不执行构建，不修改源码、测试、公共配置或其他窗口文件，不 stage/commit/push/reset/checkout，不操作 MuMu。  
> 工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`，HEAD **`513891d6a66a9b78e072ba759d34ed580d377ecf`**（已核对，与要求一致）。

---

## 一、B1 属于的 hunk 精确清单

### 1.1 `SearchTermDao.kt` — 仅 S6（hunk 6）

- **HEAD 基线状态（`513891d`）**：G0 提交已包含 hunk S5（删除 `@Index index_search_terms_term`）、S1–S4（`deleteByChapter`、`countTerms`、`rowsByTerm` 删除）。  
- **工作树剩余 diff（`git diff HEAD`）**：**仅 S6 hunk**（`@@ -185,6 +176,16 @@`）：在 `SearchIndexCoverageDao` 接口新增 `distinctBookIds()` 方法，返回 `SELECT DISTINCT book_id FROM search_index_coverage`。
- **归属 B1 的理由**：`purgeOrphanIndexRows()` 在 `SearchIndexRepository` 中同时枚举 `termDao.distinctBookIds()` 与 `coverageDao.distinctBookIds()`（第 605 行），KDoc 说明："只扫 term 表会漏掉仅剩 coverage 行的已删书"——这是 S03 孤儿回收逻辑的必要接口。  
- **缺失时的后果**：`SearchIndexRepository.purgeOrphanIndexRows()` 调用 `coverageDao.distinctBookIds()`，若该方法不存在则编译错误（`SearchIndexCoverageDao` 接口无此成员，`FakeSearchIndexCoverageDao` 测试假体也需要实现它）。

### 1.2 `SearchIndexRepository.kt` — 全部 17 个工作树 hunk

- **hunk 范围**（`git diff HEAD` 实测）：
  - `@@ -33 @@`：import `SearchHitRanker`、`TxtChapterSlice`、`TxtChapterSplitter`
  - `@@ -41 @@`：import `Mutex`、`withLock`、`AtomicInteger`、`AtomicLong`、`AtomicReference`
  - `@@ -120 @@`（+175 行）：新增 `sweepLock`、`rebuildLock`、`SweepCoordinationState`、`coordinationState`（AtomicReference）、`jobIdSequence`、`singleBookRunningCount`，以及 `tryAcquireIncrementalSweep`、`isSweepActiveAndValid`、`releaseSweepOwnership`、`beginRebuild` 等并发协调方法（S03-1 原子协议）
  - `@@ -204 @@`、`@@ -349 @@`、`@@ -391 @@`、`@@ -564 @@`（孤儿回收 `purgeOrphanIndexRows()`）、`@@ -807 @@`、`@@ -856 @@`、`@@ -884 @@`、`@@ -949 @@`、`@@ -974 @@`、`@@ -1087 @@`、`@@ -1137 @@`、`@@ -1197 @@`、`@@ -1265 @@`、`@@ -1395 @@`：重构 `ensureIndexedIncremental`、`triggerFullRebuildNow`、`reindexBookById`、`purgeOrphanIndexRows` 等核心方法以纳入原子协调状态机
- **归属 B1 的理由**：整文件修改均属 S03-1 搜索索引并发原子协调重构；无来源替换（S04）或 S05/S12 业务逻辑。
- **全部 17 个 hunk 属 B1，无需拆分。**

### 1.3 `SearchIndexScheduler.kt` — 1 个 hunk

- **hunk**（`@@ -52,9 +52,16 @@`）：`triggerNow(force: Boolean = false)` → `triggerNow(force: Boolean)`（**移除默认值**）。
- **归属 B1 的理由**：KDoc 明确说明：S03-1 发现 `force=false` 对应 `KEEP` policy 会静默丢弃重建请求，导致「置空后重扫根本不发生」；移除默认值在编译期强制所有调用点显式传参。
- **缺失时的后果**：`SearchIndexRepository.triggerFullRebuildNow()` 内调用 `(overrideScheduler ?: scheduler)?.triggerNow(force = true)` 仍可编译（有命名参数），但 `triggerNow(true)` 调用方若依赖旧签名不传参则编译失败——调用方分析见问题 2。

### 1.4 `BookRepository.kt` — B1/B2 混合，见下节

### 1.5 `SearchIndexBookRefreshCoordinator.kt` — 未跟踪新文件，必须纳入

- **git status**：`??`（未跟踪，非 HEAD 存在文件）。
- **调用方依赖**（已确认）：
  - `BookRepository.kt` 第 77 行：`private val indexRefreshCoordinator: SearchIndexBookRefreshCoordinator? = null` — 构造器参数，Hilt 生产注入。
  - `ShelfImporter.kt` 第 96 行：`private val indexRefreshCoordinator: SearchIndexBookRefreshCoordinator` — **非可选注入**，无 `?` 修饰。
  - `restoreBook()` 第 587 行、`restoreDeletions()` 第 665-670 行：通过 `indexRefreshCoordinator?.request(bookId)` 触发异步索引重建。
- **缺失时的后果**：`ShelfImporter.kt` 的 import（第 18 行）和构造器（第 96 行）直接引用该类，若文件不存在则 `compileDebugKotlin` 立即失败（unresolved reference）。`BookRepository.kt` 同理。  
- **结论**：`SearchIndexBookRefreshCoordinator.kt` 是 B1 编译闭包的**强制成员**，不可省略。

---

## 二、`BookRepository.kt` 的 B1/B2 混合 hunk 能否安全拆分

### 2.1 hunk 归属矩阵（`git diff HEAD` 实测 10 个 hunk）

| hunk | 位置（HEAD 旧行） | 主要内容 | 归属 |
|------|------|------|------|
| `@@ -26 @@` | 第 26 行 import | import `BookCacheClearOutcome`、`ContentFileDeletionOutcome` 等（+3） | **B2** |
| `@@ -60 @@` | 构造器参数（+18） | 新增 `indexRefreshCoordinator: SearchIndexBookRefreshCoordinator?` | **B1** |
| `@@ -231 @@` | `clearBookCache` 附近（+11） | `scanBookContentResidue`、`ResidueScan` 相关类引入 | **B2** |
| `@@ -246 @@` | `clearBookCache` 实现（+195，-34） | `clearBookCache` 改返回 `BookCacheClearOutcome`、`deleteBookContentFiles` 重写（含三处删除位置） | **B2** |
| `@@ -311 @@` | `cancelFinish`（+12） | S05-① 取消读完 KDoc | **其他（S05）** |
| `@@ -329 @@` | `markFinished`（+34） | 新增 `markFinished()` 函数 | **其他（S05）** |
| `@@ -340 @@` | `restoreBook`（+12） | 添加 `restored` 标记变量 | **B1 前置**（索引重建需要知道是否实际恢复） |
| `@@ -366 @@` | `restoreBook` 末尾（+9） | `if (restored) indexRefreshCoordinator?.request(bookId)` | **B1** |
| `@@ -436 @@` | `restoreDeletions`（+36） | M-BUG-10/S03-3：`if (indexRefreshCoordinator != null)` 异步重建分支 | **B1** |
| `@@ -667 @@` | 文件末尾（+340） | `BookCacheClearOutcome`、`ContentFileDeletionOutcome`、`ResidueScan` 等密封类定义 + `deleteTreeSparingResidue`、`scanBookContentResidue` 等 B2 实现 | **B2** |

### 2.2 拆分可行性与最小必要代码

**B1 与 B2 在 `BookRepository.kt` 中可以安全 hunk 级拆分**，物理上不相交：

- **B1 必须取的 3 个 hunk**（最小边界）：
  1. `@@ -60 @@`（构造器注入 `indexRefreshCoordinator`）
  2. `@@ -340 @@`（`restoreBook` 里的 `restored` 标记）
  3. `@@ -366 @@`（`if (restored) indexRefreshCoordinator?.request(bookId)`）
  4. `@@ -436 @@`（`restoreDeletions` 里的 M-BUG-10/S03-3 异步重建分支）

> [!IMPORTANT]
> **B1 不得整文件照搬 `BookRepository.kt`**，必须用 `git add -p` 精确取上述 4 个 hunk，严格排除 `@@ -26 @@`、`@@ -231 @@`、`@@ -246 @@`、`@@ -329 @@`、`@@ -667 @@`（B2/S05 改动）。

- **hunk `@@ -311 @@`**（S05 取消读完 KDoc）：属 S05 范畴，B1 不取。
- **hunk `@@ -329 @@`**（`markFinished`）：属 S05，B1 不取。

**拆分后的编译安全性**：

- 取 B1 的 4 个 hunk 后，`BookRepository` 构造器多了一个可选参数（默认 `null`），生产 Hilt 会按类型注入真实 `SearchIndexBookRefreshCoordinator`；旧测试夹具不传此参数则走同步 `searchIndexRepository` 回退路径，**已有 `BookRepositorySearchIndexTest` 的 3 个用例设计为不传 coordinator，回退路径已覆盖**，B1 单独落地测试不需改动。
- `@@ -246 @@` 的 `clearBookCache` 改造（B2）**不在 B1 范围**。HEAD 基线中 `clearBookCache` 签名为 `suspend fun clearBookCache(id: String)` 无返回值，B1 若排除 `@@ -246 @@` 则该签名保持不变，其调用方 `ShelfBookActions.kt` 等仍以 `Unit` 接收返回，不会破坏编译。

---

## 三、B1 与已提交 G0+U、G2+runner 的接口吻合性

### 3.1 G0+U（`209ec8a`）

- **G0**：提升 Room 至 v15，`SearchTermDao` 仅包含 S1–S5（S5 删除冗余索引）。B1 的 S6 hunk 新增 `SearchIndexCoverageDao.distinctBookIds()`，与 G0 的 `SearchTermDao` 改动文件不同、接口不重叠，**不冲突**。
- **U**：`BookDao` 仅含 B1–B4（`@Upsert` 改造）。**B1 的 `SearchIndexRepository.purgeOrphanIndexRows()` 需要 `bookDao.activeIds()`，而该方法属 `BookDao` 的 B5 hunk，尚未提交。** 这是本次审查发现的**最关键硬依赖问题**（见下方 §四）。

### 3.2 G2+runner（`513891d`）

- `CraIsolatedTestRunner`、`CraIsolatedTestApplication`、`IsolatedTestContext` 均与 B1 无接口交集。
- `build.gradle.kts` A/C/D/E hunk 与 B1 无依赖，B1 不改 `build.gradle.kts`。
- **吻合**：无冲突。

---

## 四、数据安全与编译闭包问题（仅影响候选的问题）

### 问题 1：`BookDao.activeIds()` 是 B1 编译闭包的**硬依赖**，当前未在 HEAD 中 ★最小阻塞★

**已证（源码级）**：

- `SearchIndexRepository.purgeOrphanIndexRows()`（第 607 行）：`val active = bookDao.activeIds().toHashSet()`
- `SearchIndexRepository`（第 729 行）：`val activeBookIds = bookDao.activeIds().toHashSet()`（扫描侧读取侧防御）
- **HEAD 基线 `BookDao`**（`git show HEAD:…/BookDao.kt | grep activeIds`）：**空输出**——`activeIds()` 不在 HEAD 中。
- **工作树 `BookDao.kt`** 第 80 行：`suspend fun activeIds(): List<String>` — 属 B5 hunk（`@@ -59,6 +59,26 @@`），与 U 候选的 B5 排除项完全一致。

**后果**：若在 HEAD + 仅 B1（不含 B5）的隔离目录构建，`SearchIndexRepository.kt` 调用 `bookDao.activeIds()` 将报 **`unresolved reference: activeIds`**，编译立即失败。

**解决方案（两种，供 ZCode 选择）**：

| 方案 | 操作 | 优缺点 |
|------|------|------|
| **方案 A（推荐）：B1 同批包含 BookDao B5** | 在 B1 候选中额外取 `BookDao.kt` 的 B5 hunk（`@@ -59,6 +59,26 @@`，`getAllActive`/`activeIds`） | 最简洁；B5 纯新增接口，不影响现有逻辑；Legado 导入器的 `LegadoReadingProgressImporter.kt`（未跟踪）也依赖 `getAllActive()`，但该文件属于批次 4，可不随 B1 纳入——B5 接口提前落地不造成 dangling caller |
| **方案 B：B1 拆解出 `purgeOrphanIndexRows`** | 将 `purgeOrphanIndexRows` 及其调用（`triggerFullRebuildNow` 第 576 行）暂时剔除，等 B5 落地后再补入 | 工程复杂，需要二次 hunk 拆分，且破坏 S03 孤儿回收的原子语义 |

> [!WARNING]
> **不可绕过**：这是编译级硬依赖。若 ZCode 按原决策包的 B1 文件列表（不含 BookDao B5）构造隔离候选，`:app:compileDebugKotlin` 必然失败。

### 问题 2：`SearchIndexScheduler.triggerNow` 签名变更需核查调用方兼容性

**已证**：

- HEAD 基线签名：`fun triggerNow(force: Boolean = false)` — 有默认值
- 工作树签名：`fun triggerNow(force: Boolean)` — 无默认值
- **工作树调用方**（`git grep` 实测）：
  - `SearchIndexRepository.kt:580`：`triggerNow(force = true)` — 命名参数，兼容
  - `ReaderSearchIndexCoordinator.kt:96`：`triggerNow(force = true)` — 命名参数，兼容
  - `ProfileViewModelTest.kt:178`：`triggerNow(any())` — 使用 mockk `any()`，兼容
  - `ReaderViewModelTest.kt:1433`：`triggerNow(force = true)` — 命名参数，兼容
- **结论**：所有现有调用方均显式传 `force = true`（命名参数），签名移除默认值后**编译兼容**。`ProfileViewModel.kt:506` 的注释提及 `triggerNow(force = true)` 但非调用行，不影响。

### 问题 3：`SearchIndexRepositoryRebuildConcurrencyTest.kt` 不存在，但 `SearchIndexRepositorySearchTest.kt` 已有关键改动

- 决策包 §2.1 提到 3 个测试文件：`BookRepositorySearchIndexTest.kt`、`SearchIndexRepositoryRebuildConcurrencyTest.kt`、`SearchIndexRepositorySearchTest.kt`。
- **`SearchIndexRepositoryRebuildConcurrencyTest.kt`**：`Test-Path` = **`False`**，当前工作树**不存在此文件**。这是决策包中被列为 B1 测试的文件之一，**候选清单中需标明此测试缺失**，不能作为已验证项。
- **`SearchIndexRepositorySearchTest.kt`**：状态 `M`（已修改），含 5 个 hunk（含 S6 孤儿回收相关新测试 `searchContent drops hits whose book is no longer active` 等 +208 行）。该文件的 Fake DAO 实现新增 `distinctBookIds()` 方法以适配 S6 hunk。**必须随 B1 纳入**，否则该文件现有 `FakeSearchTermDao.distinctBookIds()` 实现与 HEAD 基线中无此接口的 `SearchTermDao` 不匹配，androidTest/JVM 编译均失败。
- **`BookRepositorySearchIndexTest.kt`**：状态 `??`（未跟踪），3 个 JVM 用例覆盖 `deleteBook 清索引`、`批量删除 清索引`、`restoreDeletion 重建索引`（M-BUG-10 护栏）。**必须随 B1 纳入**（依赖 `SearchIndexBookRefreshCoordinator` 和 `BookRepository` 的 B1 hunk）。

---

## 五、B1 精确候选清单（供 ZCode E 盘构造隔离候选）

### 5.1 纳入文件/hunk

| 文件 | 处理方式 | 内容摘要 |
|------|------|------|
| `android/app/src/main/java/.../data/local/dao/SearchTermDao.kt` | `git add -p`，仅取 S6 hunk（`@@ -185,6 +176,16 @@`） | `SearchIndexCoverageDao.distinctBookIds()` 新增 |
| `android/app/src/main/java/.../data/local/dao/BookDao.kt` | `git add -p`，**仅取 B5 hunk**（`@@ -59,6 +59,26 @@`）| `getAllActive()`、`activeIds()` — 编译闭包硬依赖，必须随 B1 纳入 |
| `android/app/src/main/java/.../data/repository/SearchIndexRepository.kt` | `git add`（整文件，全 17 hunk 均属 B1） | S03-1 并发原子协调重构 |
| `android/app/src/main/java/.../data/repository/SearchIndexScheduler.kt` | `git add -p`，仅取 `@@ -52 @@`（移除 `triggerNow` 默认值） | 防漏参数编译护栏 |
| `android/app/src/main/java/.../data/repository/BookRepository.kt` | `git add -p`，仅取 4 个 B1 hunk（`@@ -60 @@`、`@@ -340 @@`、`@@ -366 @@`、`@@ -436 @@`） | 构造器注入 coordinator + restoreBook/restoreDeletions 异步重建 |
| `android/app/src/main/java/.../data/repository/SearchIndexBookRefreshCoordinator.kt` | `git add`（未跟踪新文件，整文件） | 异步索引刷新协调器，ShelfImporter 非可选依赖 |
| `android/app/src/main/java/.../feature/library/ShelfImporter.kt` | `git add -p`，仅取包含 `indexRefreshCoordinator` 注入及 `request(bookId)` 调用的相关 hunk | 来源替换成功后异步触发索引重建（S04-2 接缝） |
| `android/app/src/test/java/.../data/repository/BookRepositorySearchIndexTest.kt` | `git add`（未跟踪新文件，整文件） | M-BUG-10 护栏：删除/撤销的索引生命周期 JVM 测试（3 用例） |
| `android/app/src/test/java/.../feature/search/SearchIndexRepositorySearchTest.kt` | `git add -p`，全部 5 个 hunk | 孤儿不冒搜索结果 + Fake DAO 适配 S6 接口 |

### 5.2 排除项（B1 候选严格不取）

| 文件/hunk | 归属 | 排除理由 |
|------|------|------|
| `BookRepository.kt` `@@ -26 @@`（+3 import） | B2 | 引入 `BookCacheClearOutcome` 等 B2 密封类 import |
| `BookRepository.kt` `@@ -231 @@`、`@@ -246 @@`、`@@ -667 @@` | B2 | `clearBookCache` 重写及残留安全密封类定义 |
| `BookRepository.kt` `@@ -311 @@`、`@@ -329 @@` | S05 | `cancelFinish` KDoc、`markFinished()` — 属批次 6 |
| `SearchIndexRepositoryRebuildConcurrencyTest.kt` | 缺失 | 工作树不存在，**候选中标"缺失，NOT INCLUDED"** |
| `ShelfImporter.kt` 中的 B2/S04 专属 hunk | B2/S04 | 仅取协调器注入及 `request()` 调用 hunk，排除 S04 来源替换/残留清理的其他 hunk |

### 5.3 最有判别力的 3 个测试

| 测试 | 类型 | 判别什么 |
|------|------|------|
| `BookRepositorySearchIndexTest.restoreDeletion_reindexes_a_successfully_restored_book` | JVM | 撤销删除后 `reindexBookById` 被调用 1 次；若 B1 的 `@@ -436 @@` hunk 漏带，该断言 FAIL（`coordinator=null` 走同步路径）——此测试固有回归护栏语义 |
| `SearchIndexRepositorySearchTest.searchContent_drops_hits_whose_book_is_no_longer_active` | JVM | 已删书的残留索引行不冒进搜索结果（M-BUG-10 读取侧防御）；依赖 `bookDao.activeIds()`，若 B5 hunk 缺失则此测试本身编译失败 |
| `SearchIndexRepositorySearchTest.searchContent_returns_nothing_when_no_book_is_active` | JVM | 极端情况——所有书均已删时搜索返回空；同上依赖 B5 |

---

## 六、未经实际构建的声明

本审查未运行任何 Gradle 命令，所有编译/行为后果均来自源码静态分析和 `git diff HEAD` 实测。**不宣称 B1 已可编译**；以上候选清单供 ZCode 在 E 盘隔离目录实际验证。

---

## 七、结论

| 问题 | 结论 |
|------|------|
| B1 能否单独构造候选 | **可以**，但必须同时包含 `BookDao` B5 hunk（`activeIds`/`getAllActive`） |
| 最小阻塞 | `BookDao.activeIds()` 尚在工作树（B5 hunk）、未提交；不含 B5 则 `compileDebugKotlin` 必然失败 |
| `SearchIndexRepositoryRebuildConcurrencyTest.kt` | 工作树**不存在**此文件，决策包 §2.1 对此的列举有误，候选中应标"缺失 NOT INCLUDED" |
| B1/B2 混合 hunk | 可安全拆分；B1 仅需 `BookRepository.kt` 的 4 个 hunk，B2 的 `clearBookCache` 重写及密封类全部排除 |
| `triggerNow` 签名 | 所有调用方均传命名参数 `force = true`，签名变更兼容 |
| G0+U/G2+runner 接口吻合 | 吻合；唯一跨批次硬依赖为 B5（`BookDao.activeIds`），不影响已提交的首批 |

---

*报告生成时间：2026-09-29T19:21~19:30（只读，无构建，无设备操作）*  
*源码分析依据：`git diff HEAD`、`git show HEAD:…`、工作树直接读取；7 份关键文件逐行确认。*
