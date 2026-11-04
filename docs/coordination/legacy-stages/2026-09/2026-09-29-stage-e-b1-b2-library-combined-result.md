# 阶段 E — B1 + B2 v2 + 库浏览器联合隔离候选验证报告（2026-09-29）

> **窗口**：阶段 E 联合隔离候选验证（B1 搜索索引并发/孤儿回收 + B2 cache-clear 残留安全边界 + 库浏览器搜索/选择边界）。  
> **执行环境**：WorkBuddy DeepSeek / DeepSeek harness。  
> **工作仓库（只读）**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`。  
> **HEAD 基线核对**：`513891d6a66a9b78e072ba759d34ed580d377ecf`（暂存区空，工作树既有资产完好）。  
> **隔离候选目录**：`E:\creation-reading-assistant\stage-e-b1-b2-library-combined-20260929\`（E 盘独立目录，非 git 共享工作区）。  
> **输入凭证**：  
> - [AGENTS.md](../../../../AGENTS.md) 与 [当前交接](../../../handoff/current.md)  
> - [B1/HOLD16 回读](../../reviews/2026-09/2026-09-29-stage-e-b1-hold16-codex-review.md) 与 [B1 隔离验证结果](2026-09-29-stage-e-b1-isolated-candidate-result.md)  
> - [B2/库浏览器回读](../../reviews/2026-09/2026-09-29-stage-e-b2-library-codex-review.md)、[B2 隔离验证结果](2026-09-29-stage-e-b2-cache-isolated-result.md) 与 [库浏览器隔离验证结果](2026-09-29-stage-e-library-selection-isolated-result.md)  
> - [HOLD 16 行逐项裁决](2026-09-29-stage-e-hold-16-adjudication.md) 与 [集成决策材料](../../decisions/2026-09/2026-09-29-stage-e-integration-decision-package.md)  
> **纪律承诺**：共享恢复仓库内**唯一写入 = 本报告文件**。未修改共享源码 / 测试 / 配置 / 其他窗口报告 / 原始 CSV；**未** stage / commit / push / reset / checkout；**未**操作手机、MuMu、ADB、正式书库或原始仓库；**未**跨目录篡改已有证据。

---

## 一、结论摘要

| 检验维度 | 目标与指标 | 本轮实测结果 | 判定 |
|---|---|---|---|
| **主源码编译** | `:app:compileDebugKotlin` | **BUILD SUCCESSFUL in 1m 39s**（退出码 0） | **PASS** |
| **单元测试编译** | `:app:compileDebugUnitTestKotlin` | **BUILD SUCCESSFUL in 1m 18s**（退出码 0） | **PASS** |
| **联合定向 JVM 回归** | 11 套件去重测试实跑 | **BUILD SUCCESSFUL in 1m 18s**（退出码 0）<br>合计 **167 tests / 0 failures / 0 errors / 0 skipped** | **PASS** |
| **去重计算核验** | B1(75) + B2(76) + 库(29) 去重 | 理论去重总数：180 − 13 (`BookRepositoryTest`) = **167**；11 份 XML 实算总数 = **167** | **100% 吻合** |
| **`BookRepository.kt` 独占合流** | 整合 B1(4 hunk) + B2(2 hunk) | 逐行排查无物理冲突，两方语义完整保留，零编译未解析符号 | **PASS** |
| **严格排除非本批改动** | 严禁带入 S05 / `ADDED_DESC` 等 | `markFinished`、`sortModeOverride`、`ADDED_DESC` 关键词匹配均为 0 | **PASS** |
| **夹具接缝完整性** | `BookRepositoryTest` 夹具对齐 | 包含 `TemporaryFolder` 与 `filesDir` 桩，13 条既有用例全绿 | **PASS** |
| **联合补丁度量** | `stage-e-b1-b2-library-combined.patch` | **121,757 字节**，SHA-256：`77FB12704C3B7B7B64A5CE0736D6434E61AD46589BB5855BFF3ECA821B6FC3DC` | **已固化** |

---

## 二、基线与隔离工作区构造

### 2.1 基线核对

在开始前对共享工作仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` 进行了严格审查：
- `git rev-parse HEAD`：`513891d6a66a9b78e072ba759d34ed580d377ecf`（包含 G0+U `209ec8a` 与 G2+runner `513891d`）。
- `git diff --cached`：输出为空（暂存区干净）。
- 本轮验证不读写原始仓库 `D:\develop\Code\Codex\creation-reading-assistant`。

### 2.2 联合隔离环境

1. 路径：`E:\creation-reading-assistant\stage-e-b1-b2-library-combined-20260929\`。
2. 基础底座：由 HEAD `513891d` 解包构建，配置未跟踪的 `local.properties` 指向本地 SDK（`sdk.dir=D\:\\develop\\Android\\Sdk`）。
3. 依赖缓存复用：使用本地 `GRADLE_USER_HOME=D:\develop\env\gradle` 与 JDK 17（`D:\develop\Java\jdk-17.0.14`）。

---

## 三、文件纳入清单与独占整合分析

联合候选共包含 **18 个文件**（11 个修改文件 + 7 个新增文件），未引入任何未声明或越界文件。

### 3.1 独占整合文件处理证据

#### 1. `BookRepository.kt`（独占合流核心）
- **输入来源**：以 B2 隔离版本为底座，精确合入 B1 的 4 处 hunk。
- **B1 纳入内容**：
  1. 构造函数注入：
     ```kotlin
     private val searchIndexRepository: SearchIndexRepository? = null,
     private val indexRefreshCoordinator: SearchIndexBookRefreshCoordinator? = null,
     ```
  2. `applyDeletionLocked` 孤儿索引清理（M-BUG-10 核心）：
     ```kotlin
     bookDao.softDelete(id, now)
     searchIndexRepository?.deleteIndexForBook(id)
     ```
  3. `restoreBook` 异步索引重建触发：
     ```kotlin
     var restored = false
     bookDao.runInTransaction {
         // ...
         restored = true
     }
     if (restored) {
         indexRefreshCoordinator?.request(bookId)
     }
     ```
  4. `restoreDeletions` 异步自愈与同步回退：
     ```kotlin
     if (indexRefreshCoordinator != null) {
         reports.filter { it.restoredBook }.map { it.bookId }.forEach { indexRefreshCoordinator.request(it) }
     } else {
         searchIndexRepository?.let { repo ->
             reports.filter { it.restoredBook }.map { it.bookId }.forEach {
                 try { repo.reindexBookById(it) } catch (e: Exception) { AppLog.w("BookRepository", "...") }
             }
         }
     }
     ```
- **B2 纳入内容**：
  1. `clearBookCache(id: String): BookCacheClearOutcome`：带残留预检（`scanBookContentResidue`）的返回合同改造与三处正文路径清理。
  2. 文件尾部：`RESIDUE_SUFFIXES`、`BookCacheClearOutcome`、`deleteTreeSparingResidue`、`OfflineBooksClearReport`、`OfflineBooksResidueClear` 纯文件层残留保护结构。
- **排除内容**：
  - 严禁包含 S05 的 `markFinished()`、`cancelFinish()`、`setReadingState` 的 KDoc 变更。
  - 严禁包含 `mergeReaderProgress` 等未决跨批改动。
- **冲突核验结果**：B1 的改动集中在软删除、单本/批量撤销恢复流程与构造器；B2 的改动集中在缓存清理方法与文件层工具类。两方在代码拓扑上无重叠，逐 hunk 审查确认语义完美兼容。

#### 2. `BookRepositoryTest.kt`（测试夹具接缝）
- **问题根源**：HEAD 中 `BookRepositoryTest` 使用 relaxed mock `Context`，在调用 `clearBookCache` 时，由于没有 mock `context.filesDir`，导致底层 `File(context.filesDir, "books/$id")` 抛出 NPE，被捕获后触发安全预检 `RefusedUnsafeScan`，阻断了后续的数据库 upsert 验证（导致原有测试红标）。
- **接缝处理**：应用 B2 v2 的 3 处夹具补丁：
  1. `import org.junit.Rule` 与 `import org.junit.rules.TemporaryFolder`
  2. 添加测试规则：`@get:Rule val tmp = TemporaryFolder()`
  3. 桩注入：`context = mockk { every { filesDir } returns tmp.newFolder("files") }`
- **核验结果**：B1 在该文件中未做任何修改（B1 新建了独立的 `BookRepositorySearchIndexTest.kt`）；由于 B1 的构造器参数带有默认值 `= null`，`BookRepositoryTest` 既有测试用例自然走默认分支，13 条用例全部 100% PASS。

---

## 四、18 个纳入文件度量与 SHA-256 清单

所有哈希均在隔离目录 `E:\creation-reading-assistant\stage-e-b1-b2-library-combined-20260929\` 实算生成：

| 状态 | 相对路径 | 大小 (Bytes) | SHA-256 校验和 | 归属与对照核实 |
|---|---|---|---|---|
| **M** | `android/app/src/main/java/.../data/local/dao/BookDao.kt` | 7,905 | `E41B154243071D3CA91549DD43202B9BA7596347AB21284C8A2FBF06C4EFBA99` | 与 B1 报告一致（仅 S6/B5） |
| **M** | `android/app/src/main/java/.../data/local/dao/SearchTermDao.kt` | 8,937 | `3181DC9B78E8B42E16591BFB0BEBEAB20630181ADD91C98C338C29BCBA0E30E6` | 与 B1 报告一致（排除 S1–S4） |
| **M** | `android/app/src/main/java/.../data/repository/SearchIndexRepository.kt` | 86,422 | `8AC41F136FDF4BF05CC110777A00A3137BD95404A65E79FFA998460CEDE35B5F` | 与 B1 报告一致（S03 状态机） |
| **M** | `android/app/src/main/java/.../data/repository/SearchIndexScheduler.kt` | 4,383 | `337254759E52D481902967771515C6FF997AF0B30C089849E99FD9560B330BA4` | 与 B1 报告一致 |
| **??** | `android/app/src/main/java/.../data/repository/SearchIndexBookRefreshCoordinator.kt` | 2,718 | `83887755AA19CABA20347DE506DE2F1BA1D829050B099C750F4370463433B081` | 与 B1 报告一致 |
| **??** | `android/app/src/main/java/.../feature/search/SearchHitRanker.kt` | 2,504 | `B9BD3D20A889006FC7F1F03015A8668DBB8357B60D9221BCC16443317F8425AF` | 与 B1 报告一致 |
| **??** | `android/app/src/main/java/.../feature/search/TxtChapterSplitter.kt` | 2,851 | `4CDE1B55AA359FD8DD66A6474F4D2DC57307D1085B2C832AB35C6619AFD3208E` | 与 B1 报告一致 |
| **??** | `android/app/src/test/java/.../data/repository/BookRepositorySearchIndexTest.kt` | 8,821 | `D63DC6FB28169A1F28AEC5EA28609C481EADB7AC7B6E7D33065DF0EAD47D62D4` | 与 B1 报告一致（4 用例） |
| **??** | `android/app/src/test/java/.../data/repository/SearchIndexRepositoryRebuildConcurrencyTest.kt` | 47,208 | `E2E22CE8324646C3C6320F4BF9F887589693799C1AA1C517459AB0AE058594BA` | 与 B1 报告一致（19 用例） |
| **M** | `android/app/src/test/java/.../feature/search/SearchIndexRepositorySearchTest.kt` | 24,793 | `2A177D0F18A2B870F1529530A0544F8086722792748D6EE83BB2F9313B48A728` | 与 B1 报告一致（27 用例） |
| **M** | `android/app/src/main/java/.../ui/viewmodel/ShelfBookActions.kt` | 8,580 | `8E13F6AD362F9506C2D7F36242B85F8C342010533179E715576CEE8E15A0483C` | 与 B2 报告一致（排除 markFinished） |
| **M** | `android/app/src/test/java/.../ui/viewmodel/ShelfBookActionsTest.kt` | 9,568 | `520E126984D1341C39BB4B3E7CC70C9412CDB9548719B56EDE122D2CDCE5FBF1` | 与 B2 报告一致（18 用例） |
| **M** | `android/app/src/test/java/.../ui/viewmodel/ShelfViewModelTest.kt` | 16,179 | `2EDFFE84BD20B565DEC2E37EA0D75146FF10E56983C072D40FD03681F450BC72` | 与 B2 报告一致（2 行编译适配） |
| **??** | `android/app/src/test/java/.../data/repository/BookResidueSafeCleanupTest.kt` | 39,075 | `222F25F90559D1B36855365674BFECFE4B09A595E5D7216DB2D664AD1F41F349` | 与 B2 报告一致（31 用例） |
| **M** | `android/app/src/main/java/.../ui/viewmodel/LibraryBrowserViewModel.kt` | 23,150 | `5B1349BF258134F0A13FDA220ECAE59A2FDC51C637DAD7DE7CA348FF4A6A3D1D` | 与库浏览器报告一致（排除 ADDED_DESC） |
| **??** | `android/app/src/test/java/.../ui/viewmodel/LibraryBrowserSelectionTest.kt` | 6,589 | `3626B156F460CE4E04050BD3E4FEB17CDC917E645B7934D5C25A45676AD8A5AE` | 与库浏览器报告一致（4 用例） |
| **M** | `android/app/src/main/java/.../data/repository/BookRepository.kt` | **63,458** | `7B79A6BF0090DAE24CB808A191F6B33437A33FEE5894DBC4337F9672D9BD8CA1` | **本轮独占合流（B1 4 处 + B2 2 处）** |
| **M** | `android/app/src/test/java/.../data/repository/BookRepositoryTest.kt` | **18,651** | `E7892F7A37B1DB1C5D68A01393809C2DE3E02F16748DDA27EAA627F1EEB62338` | 与 B2 v2 报告一致（夹具适配） |

- **统一修改补丁**：`stage-e-b1-b2-library-combined.patch`  
  - 大小：**121,757 字节**  
  - SHA-256：`77FB12704C3B7B7B64A5CE0736D6434E61AD46589BB5855BFF3ECA821B6FC3DC`  
  - 覆盖文件数：11 个修改文件（新增文件已列入清单并在证据目录归档）

---

## 五、构建与测试执行记录

### 5.1 编译阶段验证

| 验证项 | Gradle 命令 | 耗时 | 退出码 | 结果判定 |
|---|---|---|---|---|
| **主源码编译** | `.\gradlew.bat :app:compileDebugKotlin --no-daemon` | 1m 39s | 0 | **SUCCESS** |
| **单元测试编译** | `.\gradlew.bat :app:compileDebugUnitTestKotlin --no-daemon` | 1m 18s | 0 | **SUCCESS** |

### 5.2 联合定向测试结果（去重实测）

执行命令：
```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "*BookRepositorySearchIndexTest*" `
  --tests "*BookRepositoryTest*" `
  --tests "*SearchIndexRepositoryRebuildConcurrencyTest*" `
  --tests "*SearchIndexWorkerPolicyTest*" `
  --tests "*SearchIndexRepositorySearchTest*" `
  --tests "*BookResidueSafeCleanupTest*" `
  --tests "*ShelfBookActionsTest*" `
  --tests "*ShelfViewModelTest*" `
  --tests "*LibraryRootStoreTest*" `
  --tests "*LibraryBrowserPolicyTest*" `
  --tests "*LibraryBrowserSelectionTest*" `
  --no-daemon
```

- **执行耗时**：1m 18s
- **Gradle 退出码**：0（`BUILD SUCCESSFUL`）
- **XML 逐套件解析汇总**：

| # | 测试类 / 套件名称 | XML 文件名称 | 测试数 | FAIL | ERR | SKIP | 来源归属与功能验证重点 |
|---|---|---|---|---|---|---|---|
| 1 | `BookRepositorySearchIndexTest` | `TEST-com...BookRepositorySearchIndexTest.xml` | 4 | 0 | 0 | 0 | B1（M-BUG-10 删书清索引、撤销自愈） |
| 2 | `BookRepositoryTest` | `TEST-com...BookRepositoryTest.xml` | 13 | 0 | 0 | 0 | B1/B2 共有（既有业务逻辑 + B2 夹具适配） |
| 3 | `BookResidueSafeCleanupTest` | `TEST-com...BookResidueSafeCleanupTest.xml` | 31 | 0 | 0 | 0 | B2（残留保护删除器、`clearBookCache` 6 态） |
| 4 | `SearchIndexRepositoryRebuildConcurrencyTest` | `TEST-com...SearchIndexRepositoryRebuildConcurrencyTest.xml` | 19 | 0 | 0 | 0 | B1（S03-1 确定性并发屏障握手） |
| 5 | `SearchIndexWorkerPolicyTest` | `TEST-com...SearchIndexWorkerPolicyTest.xml` | 12 | 0 | 0 | 0 | B1（后台调度与延迟策略） |
| 6 | `LibraryRootStoreTest` | `TEST-com...LibraryRootStoreTest.xml` | 3 | 0 | 0 | 0 | 库浏览器（根目录扫描基础状态） |
| 7 | `SearchIndexRepositorySearchTest` | `TEST-com...SearchIndexRepositorySearchTest.xml` | 27 | 0 | 0 | 0 | B1（孤儿过滤、双表对齐打分） |
| 8 | `LibraryBrowserPolicyTest` | `TEST-com...LibraryBrowserPolicyTest.xml` | 22 | 0 | 0 | 0 | 库浏览器（导航、重命名与路径安全） |
| 9 | `LibraryBrowserSelectionTest` | `TEST-com...LibraryBrowserSelectionTest.xml` | 4 | 0 | 0 | 0 | 库浏览器（搜索过滤、选择状态同步） |
| 10 | `ShelfBookActionsTest` | `TEST-com...ShelfBookActionsTest.xml` | 18 | 0 | 0 | 0 | B2（缓存清理 6 分档文案、批处理） |
| 11 | `ShelfViewModelTest` | `TEST-com...ShelfViewModelTest.xml` | 14 | 0 | 0 | 0 | B2 编译适配（桩返回值兼容验证） |
| **合计** | **11 个测试套件** | **11 份 XML** | **167** | **0** | **0** | **0** | **100% PASS** |

### 5.3 去重总数实算推导
- B1 独立验证运行了 5 套件共 **75 条**测试。
- B2 独立验证运行了 4 套件共 **76 条**测试。
- 库浏览器独立验证运行了 3 套件共 **29 条**测试。
- 若直接累加：75 + 76 + 29 = 180 条。
- **去重项分析**：`BookRepositoryTest`（13 条测试）在 B1 和 B2 两次运行中均被作为仓储回归套件拉起。
- **去重实算**：180 − 13 = **167 条测试**。与本次 11 份 XML 逐项提取相加的 167 条完全吻合，证明没有漏跑或重跑任何无关用例。

---

## 六、证据材料归档目录

证据已全部保存在隔离工作区内：
`E:\creation-reading-assistant\stage-e-b1-b2-library-combined-20260929\_evidence\`

- `test-results/`：包含上述全部 11 份 `TEST-*.xml` 原始测试结果。
- `gradle-compile-main.log`：主源码编译完整日志（Task ID: `task-676`）。
- `gradle-compile-test.log`：单元测试编译完整日志（Task ID: `task-680`）。
- `gradle-test-11suites.log`：11 套件联合回归测试完整日志（Task ID: `task-684`）。
- `sha256-manifest.txt`：18 个纳入文件的字节数与 SHA-256 清单。
- `stage-e-b1-b2-library-combined.patch`：统一修改补丁（121,757 字节）。

---

## 七、未验证边界与已知局限

1. **未在设备 / MuMu 运行 Instrumentation / UI 测试**：
   - 本轮验证严格遵守多窗口隔离纪律，未占用 ADB，未启动 MuMu 或连接真机。
   - 所有验证均在 JVM 单元测试与字节码编译层面闭环。
2. **`StorageSubPage.kt` 生产 UI 入口未接入**：
   - 离线书籍清理底层核心（`OfflineBooksResidueClear`）已被 `BookResidueSafeCleanupTest` 深度验证，但因 `StorageSubPage.kt` 交织了 Profile 桶与设置项重构，按规则排除，用户在 UI 界面点击“清空离线书籍”的完整链路尚未贯通。
3. **`ADDED_DESC` 排序项暂未合流**：
   - 库浏览器“最近添加”排序（`ADDED_DESC`）与 `LibraryBrowserSortTest.kt` 仍保留在待决产品决策列表中，本轮仅交付了搜索过滤与选择状态修复。

---

## 八、集成建议与后续安排

1. **联合交付可行性**：
   - 本联合候选已在物理和语义上证明了 B1、B2、库浏览器三大功能块在 HEAD 之上的**零冲突整合**。
   - 18 个文件构成一个高度内聚、完整编译闭包的交付单元。
2. **提交方案备选**：
   - **方案 A（单批联合提交）**：将联合补丁一次性合入，以保证 `BookRepository` 与 `BookRepositoryTest` 夹具接缝的原子同步落地，避免中间态测试短暂变红。建议提交说明：
     `feat(core): combine search concurrency, cache residue protection, and library selection (Stage E)`
   - **方案 B（拆分三步提交）**：若需遵循严格的专题粒度，可由集成者先落 B1，再落 B2（带入 `BookRepository` 整合与夹具补丁），最后落库浏览器。
3. **授权纪律**：
   - 共享恢复仓库当前暂存区保持干净，未经用户明确授权不执行 stage、commit 或 push。
