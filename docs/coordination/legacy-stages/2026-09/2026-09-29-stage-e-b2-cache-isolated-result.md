# 阶段 E — B2 cache-clear / S04 残留安全边界隔离验证结果（2026-09-29）

> **窗口**：B2 cache-clear / S04 残留安全边界隔离验证（与 B1 隔离验证窗口并行）。
> **工作仓库（只读）**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`。
> **HEAD 已核对 = `513891d6a66a9b78e072ba759d34ed580d377ecf`**（与任务给定值一致，**未发生变化**）；`origin/main` 仍为 `5aed5939600e491843e3c44453921bd6273a233c`；暂存区空。
> **隔离候选**：`E:\creation-reading-assistant\stage-e-b2-cache-isolated-20260929\`（E 盘，**非** git 仓库）。
> **输入**：[AGENTS.md](../../../../AGENTS.md)、[当前交接](../../../handoff/current.md)、[B1/HOLD16 回读](../../reviews/2026-09/2026-09-29-stage-e-b1-hold16-codex-review.md)、[HOLD 16 行逐项裁决](2026-09-29-stage-e-hold-16-adjudication.md)、[剩余 HOLD 逐行归属](2026-09-29-stage-e-remaining-hold-mapping.md)、[集成决策材料](../../decisions/2026-09/2026-09-29-stage-e-integration-decision-package.md)、[数据/同步 HOLD 判定](2026-09-29-stage-e-hold-data-sync.md)。
> **纪律**：共享恢复仓库内**唯一写入 = 本文件**。未改共享源码 / 测试 / 配置 / 其他窗口报告 / CSV；**未** stage / commit / push / reset / checkout；**未**操作设备、MuMu、ADB、正式书库或原始仓库；**未**迁移目录。

---

## 一、结论摘要

| 项 | 结果 |
|---|---|
| **隔离编译** `:app:compileDebugKotlin` + `:app:compileDebugUnitTestKotlin` | **PASS，EXIT 0**（`BUILD SUCCESSFUL in 3m 1s`；22 tasks: 10 executed / 12 from cache） |
| **定向 JVM（候选 v1 = 纯 B2 范围）** | 76 tests / **1 FAILED** / 0 error / 0 skip |
| **定向 JVM（候选 v2 = v1 + 最小夹具适配）** | **76 tests / 0 failures / 0 errors / 0 skipped** |
| **`clearBookCache` 返回合同** | 在隔离候选上**成立**：6 个状态分支均被独立用例断言 |
| **「残留存在时不得误删」边界** | 在隔离候选上**成立**：拒绝态断言「文件仍在 + 三张表 upsert 均为 0」 |
| **与 B1 窗口的重叠** | **零**。B2 候选中不含任何 B1（S03 搜索索引并发）文件或 hunk |
| **唯一未解决耦合** | HEAD 的 `BookRepositoryTest` 夹具与新的残留预检不兼容；修正在 **H113（B6+B1）** 文件内（详见 §六.3） |
| **范围外（未纳入，如实登记）** | `StorageSubPage.kt` 的「清空所有离线书籍」UI 接线 —— 跨批次耦合到 §2.9 Profile 桶（详见 §四.4） |

**一句话结论**：B2 的 cache-clear 与残留安全改动**可以在 HEAD 上独立编译并全部通过定向 JVM**，不需要 B1、S05 或 §2.9 的任何 hunk；但它有**两处跨批次接缝**必须由集成者一并处理——(1) `ShelfViewModelTest.kt` 的 2 行编译适配，(2) `BookRepositoryTest.kt` 的 `filesDir` 夹具适配；后者若不同批落地，B2 提交后**现有测试会变红**。

---

## 二、输入与基线核对

### 2.1 状态核对（任务要求）

| 项 | 任务给定 | 本轮实测 | 结论 |
|---|---|---|---|
| HEAD | `513891d6a66a9b78e072ba759d34ed580d377ecf` | `513891d6a66a9b78e072ba759d34ed580d377ecf` | **一致，无差异** |
| 分支 | — | `codex/recover-worktree-20260920` | — |
| `origin/main` | — | `5aed5939600e491843e3c44453921bd6273a233c` | 未变 |
| 暂存区 | — | 空（`git diff --cached --name-only` 无输出） | 未变 |
| B2 候选文件工作树状态 | — | `ShelfBookActions.kt` ` M`(+105/−3)、`BookRepository.kt` ` M`(+605/−18)、`ShelfBookActionsTest.kt` ` M`(+84/−1)、`StorageSubPage.kt` ` M`(+399/−372)、`BookResidueSafeCleanupTest.kt` `??`(832 行) | 与裁决报告一致 |

**未发现状态变化，无需报告差异。**

### 2.2 隔离环境

| 项 | 值 |
|---|---|
| 隔离根 | `E:\creation-reading-assistant\stage-e-b2-cache-isolated-20260929` |
| 基线构造 | `git archive --format=tar HEAD android \| tar -x`（只读导出 HEAD 的 `android` 子树；与 HEAD blob 逐字节一致，已用 `git hash-object` 抽核 4 个文件全部 MATCH） |
| `local.properties` | 未跟踪，从共享仓库复制（`sdk.dir=D\:\\develop\\Android\\Sdk`） |
| JAVA_HOME | `D:\develop\Java\jdk-17.0.14` |
| GRADLE_USER_HOME | `D:\develop\env\gradle`（复用依赖缓存） |
| Gradle / AGP | Gradle 8.9（wrapper）/ `core.autocrlf=true`（**见 §五.3 陷阱**） |

---

## 三、B2 hunk 边界判定（逐 hunk）

判定口径：**只看该 hunk 引入/消费的符号归属**，不按文件名或目录前缀归类。所有权依据取自[HOLD 16 行逐项裁决](2026-09-29-stage-e-hold-16-adjudication.md) §四 4.1（H138 = B2）与[集成决策材料](../../decisions/2026-09/2026-09-29-stage-e-integration-decision-package.md) §2.2。

### 3.1 `data/repository/BookRepository.kt`（`-U3` 共 10 个 hunk；取 2 个）

| # | hunk | 内容 | 归属 | 取舍 | 理由 |
|---|---|---|---|---|---|
| 1 | `@@ -26,13 +26,16 @@` | `import SearchIndexRepository` / `Dispatchers` / `withContext` | **B1** | **排除** | 仅服务 B1 的 `searchIndexRepository` 字段与 `restoreDeletions` 同步回退分支 |
| 2 | `@@ -60,6 +63,18 @@` | 构造参数 `searchIndexRepository` + `indexRefreshCoordinator` | **B1** | **排除** | S03 / S04-2 接缝；两者默认 `null`，B2 不需要 |
| 3 | `@@ -231,6 +246,11 @@` | `softDelete` 内 `searchIndexRepository?.deleteIndexForBook(id)` | **B1** | **排除** | M-BUG-10 索引孤儿清理 |
| **4** | **`@@ -246,34 +266,195 @@`** | **`clearBookCache` 返回 `BookCacheClearOutcome` + 残留预检 + `deleteBookContentFiles` 三位置重写** | **B2** | **纳入** | 本批核心 |
| 5 | `@@ -311,6 +492,12 @@` | `setReadingState` 的 S05-① 契约 KDoc | **S05** | **排除** | 完读状态机 |
| 6 | `@@ -329,6 +516,34 @@` | 新增 `markFinished()` | **S05** | **排除** | 完读状态机（B6 候选） |
| 7 | `@@ -340,10 +555,12 @@` | `restoreBook` 内 `restored` 标记 | **B1** | **排除** | 供 B1 的索引重建判定 |
| 8 | `@@ -366,6 +583,9 @@` | `indexRefreshCoordinator?.request(bookId)` | **B1** | **排除** | S04-2 接缝 |
| 9 | `@@ -436,6 +656,36 @@` | `restoreDeletions` 的 M-BUG-10/S03-3 异步重建分支 | **B1** | **排除** | S03-3 |
| **10** | **`@@ -667,3 +917,340 @@`** | **残留标记 / 结果类型 / 纯文件层删除器 / `OfflineBooksClearReport` / `OfflineBooksResidueClear`** | **B2** | **纳入** | 本批核心（纯文件层，无 DB 依赖） |

**关键判据**：hunk 1–3、7–9 引入或使用的符号（`SearchIndexRepository`、`SearchIndexBookRefreshCoordinator`、`deleteIndexForBook`、`reindexBookById`）**均不在 B2 的调用链上**——`clearBookCache` 与 `deleteBookContentFiles` 完全不引用它们（见 §四.1 的符号级核查）。因此排除后**不产生未解析符号**，已由实际编译证实。

### 3.2 `ui/viewmodel/ShelfBookActions.kt`（`-U3` 共 3 个 hunk；取 2 个）

| # | hunk | 内容 | 归属 | 取舍 |
|---|---|---|---|---|
| **1** | `@@ -4,7 +4,9 @@` | `import BookCacheClearOutcome` / `MidwayAbortCause` | **B2** | **纳入** |
| 2 | `@@ -75,6 +77,43 @@` | `markFinished` / `markReading` / `markFinishedBatch` / `markReadingBatch` | **S05/B6** | **排除** |
| **3** | `@@ -113,9 +152,72 @@` | `clearCacheForBooks` 按 `BookCacheClearOutcome` 六分档汇总 + `buildCacheClearMessage` | **B2** | **纳入** |

**排除 hunk 2 的安全性证据**：HEAD 的 `ShelfViewModel.kt` **不调用** `bookActions.markFinished` / `markReading`（`git show HEAD:…ShelfViewModel.kt` 中 `bookActions.*` 调用点仅 `deleteBook`/`deleteBooks`/`undoDeletion`/`dismissDeletionUndo`/`restoreBook`/`shelveBook`/`restoreReading`/`updateBookInfo`/`updateBookCover`/`setBookTextCover`/`resetBookCover`/`clearCacheForBooks`），故排除后无悬空调用方。

### 3.3 `test/ui/viewmodel/ShelfBookActionsTest.kt`（`-U3` 共 3 个 hunk；**全部纳入**）

| # | hunk | 内容 | 归属 |
|---|---|---|---|
| 1 | `@@ -3,7 +3,9 @@` | import `BookCacheClearOutcome` / `MidwayAbortCause` | B2 |
| 2 | `@@ -144,7 +146,7 @@` | `clear cache returns count message` 桩由 `returns Unit` 改为 `returns BookCacheClearOutcome.Cleared` | B2 |
| 3 | `@@ -153,6 +155,87 @@` | 新增 6 条分档文案用例 | B2 |

**整文件属 B2 的证据**：`git grep HEAD` 对 `BookCacheClearOutcome` / `MidwayAbortCause` **0 命中**；文件内 `ReadingCompletionState`（`:5`）是 HEAD 既有上下文行（diff 中未变更），不是新增依赖。⇒ 与裁决报告 H138「已裁决」一致。

### 3.4 `test/ui/viewmodel/ShelfViewModelTest.kt`（仅取 2 行编译适配）

| 内容 | 归属 | 取舍 | 理由 |
|---|---|---|---|
| `import …BookCacheClearOutcome`（1 行）+ `coEvery { clearBookCache(any()) } returns BookCacheClearOutcome.Cleared`（含 2 行既有注释） | **B2 编译闭包** | **纳入（2 行）** | HEAD 版为 `returns Unit`；`clearBookCache` 返回类型变为 `BookCacheClearOutcome` 后，该桩**类型不匹配、必然编译失败**。工作树原文在 `:82-84` 自带 SEAM 注释「SEAM（residue-safe-cleanup 窗口）：clearBookCache 返回类型由 Unit 改为 BookCacheClearOutcome，此处仅为编译适配，无断言语义变化」 |
| 其余 12 个 hunk（M-BUG-03 / M-BUG-09 / B1 用例、`LibrarySourceAvailability` import 等） | M-BUG-03 / M-BUG-09 / B1 | **排除** | 保持 HEAD 内容 |

**隔离候选与本文件工作树版本的差异经实测只有 1 行**（`LibrarySourceAvailability` import），即 B2 适配部分与工作树**逐字一致**。

### 3.5 未跟踪新文件

| 文件 | 归属 | 处置 |
|---|---|---|
| `test/data/repository/BookResidueSafeCleanupTest.kt`（832 行 / 31 用例） | **B2** | 整文件复制（`git hash-object` 与共享工作树一致） |

---

## 四、编译依赖逐一核查

### 4.1 `clearBookCache` 的全部调用方（全仓 `grep`，穷举）

| 调用方 | 位置 | 是否受返回类型变更影响 | 处置 |
|---|---|---|---|
| `ShelfBookActions.clearCacheForBooks` | `ShelfBookActions.kt:170`（`when (val outcome = repository.clearBookCache(id))`） | 是（消费 6 个分支） | **纳入**（B2） |
| `ShelfBookActionsTest` | 9 处桩 + 断言 | 是 | **纳入**（B2） |
| `BookResidueSafeCleanupTest` | 15 处断言 | 是 | **纳入**（B2，新文件） |
| `ShelfViewModelTest` | `:76` 桩 | 是（**编译失败**） | **纳入 2 行适配** |
| `BookRepositoryTest` | `:339`、`:366` 直接调用、**丢弃返回值** | 否（返回值被忽略，Kotlin 允许） | **保持 HEAD**（但夹具失效，见 §六.3） |

**结论**：`clearBookCache` 的调用面**只有 5 个文件**，其中 4 个在 B2 范围内；`BookRepositoryTest` 无需改动即可编译。

### 4.2 生产侧新增符号的消费者（隔离候选内实测）

| 符号 | 定义 | 隔离候选内的消费者 |
|---|---|---|
| `BookCacheClearOutcome`（5 态） | `BookRepository.kt:882` | `ShelfBookActions.kt`、`ShelfBookActionsTest.kt`、`BookResidueSafeCleanupTest.kt`、`ShelfViewModelTest.kt` |
| `MidwayAbortCause` | `BookRepository.kt:~920` | `ShelfBookActions.kt`、`ShelfBookActionsTest.kt`、`BookResidueSafeCleanupTest.kt` |
| `deleteTreeSparingResidue` / `TreeDeletionOutcome` / `TreeDeletionResult` / `containsResidueRecursively` / `RESIDUE_SUFFIXES` / `hasResidueSuffix` | `BookRepository.kt` 尾部 | `BookRepository.kt` 自身 + `BookResidueSafeCleanupTest.kt` |
| `OfflineBooksResidueClear` / `OfflineBooksClearReport` | `BookRepository.kt:1005/1078` | **仅** `BookResidueSafeCleanupTest.kt`（11 条用例） |

**必须如实登记的口径**：在隔离候选中，`OfflineBooksResidueClear`（「清空所有离线书籍」的文件层保护）**只有单元测试消费，没有生产 UI 接线**——因为 `StorageSubPage.kt` 被排除（§4.4）。即：该对象的**行为**已被 10 条用例验证，但其**用户可达路径**在本候选上**未验证**。

### 4.3 为什么不需要 B1 / S05 / §2.9

- **B1**：`SearchIndexRepository` / `SearchIndexBookRefreshCoordinator` / `SearchIndexScheduler` 在隔离候选中**零引用**（编译通过即为证据）。
- **S05**：`markFinished` / `ReadingCompletionState.FINISHED` 写入路径未纳入；`setReadingState` 保持 HEAD 版本。
- **§2.9 Profile/Stats 桶**：未纳入任何 Profile 页面文件。

### 4.4 `StorageSubPage.kt` —— 确有需要但**跨批次耦合，故排除**（按任务「无法安全拆分时停止」）

任务允许纳入「确有必要的 `StorageSubPage.kt` 文案 hunk」。本轮核查结论：**该文件既非编译必需，其调用点 hunk 又无法安全拆分**。

**证据链**：

1. **编译上不必要**：HEAD 的 `StorageSubPage.kt` 不引用 `OfflineBooksResidueClear` / `OfflineBooksClearReport`（`git grep HEAD` 0 命中），保持 HEAD 即可编译。
2. **行为上需要，但调用点耦合到 §2.9 桶**：工作树的调用点 hunk 为
   ```diff
   -                        runCatching { val booksDir = File(context.filesDir, "books"); if (booksDir.exists()) booksDir.deleteRecursively() }
   -                        onAction(ProfileAction.ClearReaderCache)
   +                        onAction(ProfileAction.ShowMessage(report.userNotice()))
   +                        onAction(ProfileAction.ClearReaderCacheNow)
   ```
   其中 `ProfileAction.ClearReaderCacheNow` **在 HEAD 不存在**（`git show HEAD:…ProfileUiState.kt` 只有 `ClearReaderCache`；`ClearReaderCacheNow` 由工作树 `ProfileUiState.kt:241` 新增），且需要 `ProfileRoute.kt:555` 的新处理分支。两者分别属 **H063 / H062（§2.9「Profile/Stats 大重写」HOLD 桶）**。
3. **纳入代价**：`StorageSubPage.kt` 本身在工作树是 **50 个 `-U0` hunk / +399−372** 的整页重写；连同 `ProfileUiState.kt`(+40/−1)、`ProfileRoute.kt`(+72/−4) 一起纳入，等于把 §2.9 Profile 桶拖进 B2 候选，违背「不扩大范围硬凑通过」。
4. **处置**：**整文件排除**，并在 §七登记为未验证项与建议的后续任务。

---

## 五、隔离候选构造与双向校验

### 5.1 产物

| 产物 | 路径（E 盘） | SHA-256 |
|---|---|---|
| **B2 范围补丁（v1）** | `…\stage-e-b2-cache-isolated-20260929\_evidence\stage-e-b2-cache-modified.patch`（84,881 B） | `2c0effec08f645455274b94e35241b9862cabc204f813c093c4799b6045f67b1` |
| **夹具适配补丁（v1→v2）** | `…\_evidence\stage-e-b2-fixture-seam.patch`（1,964 B，3 hunk） | `2676c84fff34869c83a2d1a82acb5e99ca82db7a1e07b6f2aa43ada620ac0165` |
| 纳入文件清单 | `…\_evidence\included-files.csv` | — |
| 排除文件清单 | `…\_evidence\excluded-files.csv` | — |
| 逐 hunk 取舍表 | `…\_evidence\hunk-selection-map.csv`（16 行：7 INCLUDE / 9 EXCLUDE） | — |
| 哈希清单 | `…\_evidence\sha256-manifest.txt` | — |
| 实际命令 | `…\_evidence\commands.txt` | — |
| Gradle 日志 ×3 | `…\_evidence\gradle-01-compile.log`、`gradle-02-tests.log`、`gradle-03-tests-after-fixture-seam.log` | 见 `sha256-manifest.txt` |
| 测试 XML | `…\_evidence\test-xml-v1\`（4 份）、`…\_evidence\test-xml-v2\`（4 份） | 见 `sha256-manifest.txt` |

**隔离候选文件哈希（v2）**：

| 文件 | 隔离 v2 SHA-256 | HEAD blob |
|---|---|---|
| `BookRepository.kt` | `c9f9cfa8cd9529c131263124d03af8ad162a61337bf3ff3b45af60f0b6457ea7` | `6ae0ec78` |
| `ShelfBookActions.kt` | `8e13f6ad362f9506c2d7f36242b85f8c342010533179e715576cee8e15a0483c` | `be1d445b` |
| `ShelfBookActionsTest.kt` | `520e126984d1341c39bb4b3e7cc70c9412cdb9548719b56ede122d2cdce5fbf1` | `07037747` |
| `ShelfViewModelTest.kt` | `2edffe84bd20b565dec2e37ea0d75146ff10e56983c072d40fd03681f450bc72` | `714f46d6` |
| `BookResidueSafeCleanupTest.kt` | `222f25f90559d1b36855365674bfecfe4b09a595e5d7216db2d664ad1f41f349` | （HEAD 无此文件） |
| `BookRepositoryTest.kt`（v2） | `sha256-manifest.txt` 内 | `922e2ae4` |

### 5.2 双向校验（两条独立证据，均已实测）

**A) 隔离候选 vs 共享工作树 —— 差异必须恰好等于被排除的非 B2 内容**

```
git -c core.autocrlf=false diff --no-index --unified=0 -- <隔离文件> <工作树文件>
```

| 文件 | 差异 hunk 数 | 是否等于排除集 |
|---|---|---|
| `BookRepository.kt` | **11** | ✅ = 9 个 B1（imports×3、构造参数、softDelete 索引、restored×2、coordinator request、restoreDeletions）+ 2 个 S05（`setReadingState` KDoc、`markFinished`） |
| `ShelfBookActions.kt` | **1** | ✅ = `markFinished` 族（S05） |
| `ShelfBookActionsTest.kt` | **0** | ✅ 整文件属 B2 |
| `ShelfViewModelTest.kt` | **13** | ✅ = 其余 12 个非 B2 hunk + 1 行 `LibrarySourceAvailability` import；B2 适配 2 行逐字一致 |
| `BookResidueSafeCleanupTest.kt` | **0** | ✅ 整文件属 B2 |

**B) `HEAD + 补丁` 必须与隔离候选逐字节一致**

```
git archive --format=tar HEAD android | tar -x -C <校验目录>
git -c core.autocrlf=false apply --whitespace=nowarn <v1 patch>     # EXIT 0
→ 5 个文件 SHA-256 全部 IDENTICAL
git -c core.autocrlf=false apply --whitespace=nowarn <fixture seam> # EXIT 0
→ BookRepositoryTest.kt 与隔离候选 v2 IDENTICAL
```

**两条证据合起来**：隔离候选 = HEAD + 恰好 7 个 B2 hunk + 1 个新测试文件 + 2 行编译适配（+ v2 的 3 个夹具 hunk），**没有夹带任何 B1 / S05 / §2.9 内容**。

### 5.3 环境陷阱（本轮实测发现，供后续窗口复用）

系统级 `core.autocrlf=true`。在**非 git 仓库目录**里执行 `git apply` 会把补丁的 LF **写成 CRLF**（实测 5 个文件全部被转换，`_verify` 目录出现 1167 个 CRLF）。这会让「HEAD+补丁 == 隔离候选」的字节校验假失败，也与仓库 `.gitattributes` 的 `* text=auto eol=lf` 不符。**必须显式 `git -c core.autocrlf=false apply`**（或在隔离目录 `git init` 后配 `core.autocrlf=false`）。本候选已按此重建，5 个文件 CRLF 计数均为 0。

---

## 六、编译与测试结果

### 6.1 编译（隔离目录内）

```powershell
Set-Location 'E:\creation-reading-assistant\stage-e-b2-cache-isolated-20260929\android'
$env:JAVA_HOME='D:\develop\Java\jdk-17.0.14'
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --console=plain
```

| 项 | 值 |
|---|---|
| 退出码 | **0** |
| 结果 | `BUILD SUCCESSFUL in 3m 1s` |
| 任务 | `22 actionable tasks: 10 executed, 12 from cache` |
| 警告 | 7 条，**无 error**。其中 1 条 `LibraryBrowserComponents.kt:498` 的 `Icons.Outlined.HelpOutline` deprecation 与 3 条 `ReaderDocumentLoaderProfileTest.kt` 的 opt-in 警告为 **HEAD 既有**（两文件均未在本候选改动）；3 条位于新测试文件 `BookResidueSafeCleanupTest.kt:238/261/280`（`System.getProperty(...)` 平台类型可空性提示），**非阻塞** |

**意义**：这直接回答了裁决报告 §六 的「隔离编译 16/16 未做」中的 B2 一项——B2 在 HEAD 上**可独立编译**，`BookRepository.kt` 只取 2 个 hunk 不会产生未解析符号。

### 6.2 定向 JVM 测试

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain `
  --tests "com.creationreadingassistant.data.repository.BookResidueSafeCleanupTest" `
  --tests "com.creationreadingassistant.ui.viewmodel.ShelfBookActionsTest" `
  --tests "com.creationreadingassistant.data.repository.BookRepositoryTest" `
  --tests "com.creationreadingassistant.ui.viewmodel.ShelfViewModelTest"
```

#### 候选 v1（纯 B2 范围，`stage-e-b2-cache-modified.patch`）

| 项 | 值 |
|---|---|
| 退出码 | **1** |
| 结果 | `BUILD FAILED in 39s`；`76 tests completed, 1 failed` |
| 日志 | `_evidence\gradle-02-tests.log` |
| XML | `_evidence\test-xml-v1\`（4 份） |

| 测试类 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `BookResidueSafeCleanupTest` | 31 | **0** | 0 | 0 |
| `ShelfBookActionsTest` | 18 | **0** | 0 | 0 |
| `ShelfViewModelTest` | 14 | **0** | 0 | 0 |
| `BookRepositoryTest` | 13 | **1** | 0 | 0 |
| **合计** | **76** | **1** | **0** | **0** |

失败项（`test-xml-v1` 原文）：

```
CASE : com.creationreadingassistant.data.repository.BookRepositoryTest
       > clearBookCache resets content status and soft deletes file
MSG  : java.lang.AssertionError: Verification failed:
       call 1 of 1: BookDao(#17).upsert(matcher<BookEntity>(), any())) was not called
AT   : BookRepositoryTest.kt:341
```

#### 候选 v2（v1 + `stage-e-b2-fixture-seam.patch`）

| 项 | 值 |
|---|---|
| 退出码 | **0** |
| 结果 | `BUILD SUCCESSFUL in 25s`；`30 actionable tasks: 4 executed, 26 up-to-date` |
| 日志 | `_evidence\gradle-03-tests-after-fixture-seam.log` |
| XML | `_evidence\test-xml-v2\`（4 份） |

| 测试类 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `BookResidueSafeCleanupTest` | 31 | **0** | 0 | 0 |
| `ShelfBookActionsTest` | 18 | **0** | 0 | 0 |
| `ShelfViewModelTest` | 14 | **0** | 0 | 0 |
| `BookRepositoryTest` | 13 | **0** | 0 | 0 |
| **合计** | **76** | **0** | **0** | **0** |

> `Assume.assumeTrue(os.name contains "Windows")` 的 3 条用例（`clearBookCache reports remains when epub image cache deletion fails`、`… keeps files when txt deletion fails`、`… when epub deletion fails`）在本机（Windows 11）**实际执行**，`skipped=0`，非跳过。

### 6.3 v1 唯一失败的根因（本轮的关键发现）

**现象**：HEAD 未修改的 `BookRepositoryTest.clearBookCache resets content status and soft deletes file` 在 B2 候选上失败，`BookDao.upsert` **0 次调用**。

**根因（源码级确定）**：HEAD 的该测试用 `context = mockk(relaxed = true)`（`git show HEAD:…BookRepositoryTest.kt:92`）。新的 `clearBookCache` 第一步调用 `scanBookContentResidue(id)` → `File(context.filesDir, "books/$id")`；`java.io.File(File parent, String child)` 直接读取 `parent.path` **私有字段**，而 relaxed mock 的 `path` 字段为 `null` ⇒ 构造抛 NPE ⇒ 被 `scanBookContentResidue` 的 `catch (e: Exception)` 吞掉 ⇒ 返回 `ResidueScan.Unsafe` ⇒ `clearBookCache` **在触碰数据库之前**返回 `RefusedUnsafeScan` ⇒ 测试的 `coVerify { bookDao.upsert(...) }` 失败。

**这不是 B2 的行为缺陷，而是夹具与新预检不兼容**。证据是工作树里**同一窗口已经写下这条结论与修法**（`BookRepositoryTest.kt:53-58`）：

```kotlin
/**
 * clearBookCache 的残留预检会真实读取 filesDir 下的目录；Context 不能用 relaxed mock
 * （File(relaxed mock) 会因 mock 内部 path 字段为 null 而抛 NPE），必须给真实临时目录。
 */
@get:Rule
val tmp = TemporaryFolder()
```
以及 `:113-115` 的 `context = mockk { every { filesDir } returns tmp.newFolder("files") }`。

**处置（本轮实际执行）**：把该夹具适配抽成**独立、最小、可复核**的 3-hunk 补丁 `stage-e-b2-fixture-seam.patch`（仅 `import org.junit.Rule` / `import org.junit.rules.TemporaryFolder` / `@get:Rule val tmp` + KDoc / `filesDir` 桩；**不含** B1 的 `searchIndexRepository` / `indexRefreshCoordinator` 构造实参），单独应用后重跑 → **76/76 全绿**。两个变体的日志与 XML 均留档，未用 v2 结果覆盖 v1。

**为什么这属于 B2 的接缝而非「硬凑」**：
- 失败由 B2 的改动**直接引起**（预检新增了真实文件系统读取）；
- 修法**只做夹具适配**，不改任何断言语义（`BookRepositoryTest` 的 13 条用例内容一字未改）；
- 修法所在文件 `BookRepositoryTest.kt` 是 **H113（候选分组 B6+B1）**，其工作树 diff 还含 B1 用例（5 条 `indexRefreshCoordinator`）与 S05 用例（7 条 `markFinished`）——**本轮全部排除**，只取夹具 3 hunk。

⇒ **给集成者的硬要求**：B2 提交**必须**与 `BookRepositoryTest.kt` 的这 3 个夹具 hunk 同批，否则 B2 一落地，仓库既有测试即变红。

### 6.4 `clearBookCache` 返回合同与「不得误删」边界的验证覆盖

`BookResidueSafeCleanupTest`（31/31 通过）对 6 个返回态与保护边界逐条断言：

| 返回态 | 语义 | 代表用例 | 断言要点 |
|---|---|---|---|
| `RefusedResidue(count)` | 预检发现残留，**完全未动** | `clearBookCache refuses and preserves txt residue backup`、`… epub residue backup`、`… epub repairing and importing markers` | 返回态 + **残留文件仍在** + 原正文仍在 + 目录仍在 + `bookDao/bookContentDao/bookFileDao.upsert` **各 0 次** |
| `RefusedUnsafeScan` | 位置不可列举，**完全未动** | `clearBookCache refuses unreliably scannable content location` | 返回态 + 文件仍在 + 两张表 0 次 |
| `AbortedMidway(RESIDUE_APPEARED)` | 预检后**新出现**残留，部分执行，DB 拦截 | `clearBookCache aborts midway when residue appears after clean precheck` | 返回态 + 目录与内容仍在 + 三张表 **0 次**（`verifyDbUntouched()`） |
| `AbortedMidway(LOCATION_UNREADABLE)` | 删除中位置不可判定，部分执行，DB 拦截 | `clearBookCache aborts midway when content location becomes unreadable` | 同上 |
| `ClearedWithRemains` | 指针已清、正文未删净 | `… reports remains and keeps files when txt deletion fails`、`… when epub deletion fails`、`… when epub image cache deletion fails` | 返回态 + 残留文件仍在 + 指针已清 |
| `Cleared` | 全部删净 | `… keeps original cleanup for clean txt/epub book`、`… reclaims clean epub image cache directory`、`… only touches the target book image cache directory`、`… is no-op for unknown book without files` | 返回态 + 目录已消失 + `verifyDbPointersCleared()` |

另 5 条覆盖删除器兜底（`sparing deleter aborts on marker and keeps it`、`… keeps later subtree untouched when marker appears mid-list`、`… reports untouched abort when nothing was deleted before abort`、`… reports completed with remains when file delete fails`、`… fully removes clean tree`），11 条覆盖 `OfflineBooksResidueClear` + `userNotice()` 文案（含「不得把部分执行说成未执行」）。

**「残留存在时不得误删」的核心不变量**已被两条独立断言方式锁定：**文件系统侧**（`File.exists()`）与**数据库侧**（`coVerify(exactly = 0)`）。这正是该边界要求的双向证明。

---

## 七、尚缺的验证与风险

| # | 缺口 | 说明 | 影响 |
|---|---|---|---|
| 1 | **`StorageSubPage.kt` 的 UI 接线未纳入、未验证** | `OfflineBooksResidueClear` / `OfflineBooksClearReport` 在隔离候选中**只被单元测试消费**；「清空所有离线书籍」的用户可达路径未验证 | 中：对象行为已验，接线未验；且接线依赖 §2.9 桶的 `ProfileAction.ClearReaderCacheNow` |
| 2 | **`BookRepositoryTest.kt` 夹具适配未纳入 v1 权威补丁** | 已抽为独立 `stage-e-b2-fixture-seam.patch`（3 hunk）并验证 76/76；但它属 H113（B6+B1）文件 | **高**：不同批落地则既有测试变红 |
| 3 | **`ShelfViewModelTest.kt` 的 2 行适配属 H140（16 行待裁决项之一）** | 已纳入 B2 候选并验证行为中性（14/14 通过） | 中：需集成者确认 H140 的 B2 部分可先行拆出 |
| 4 | **未跑 `lintDebug` / `assembleDebug` / `compileDebugAndroidTestKotlin`** | 按任务只要求两个 compile + 定向 JVM | 低 |
| 5 | **未跑整树 `testDebugUnitTest`** | 本轮是隔离候选，**不以整树旧结果替代**（任务明确要求） | 低 |
| 6 | **无任何设备 / MuMu 证据** | 本窗口不做设备操作 | 低（B2 为纯数据/文件层改动，无 UI 交互） |
| 7 | **未验证 `BookRepository` 在生产 Hilt 图中的注入** | 隔离候选不含 B1 的构造参数，Hilt 图与联合态不同 | 低（B2 未改构造签名） |
| 8 | **未验证 `books/epub/<id>/` 图片缓存目录的实机存在性** | 用例用合成目录 | 低 |

---

## 八、建议下一步（互不重叠）

| # | 任务 | 独占范围 | 与 B1 窗口的关系 |
|---|---|---|---|
| **N1** | **把 B2 提交与两个接缝绑定**：`stage-e-b2-cache-modified.patch` + `stage-e-b2-fixture-seam.patch` + `ShelfViewModelTest.kt` 2 行，作为一个原子批次申请授权 | 本报告 §5.1 的三个产物 | **无重叠**（B1 候选不含 `BookRepository.kt` 的 B2 hunk） |
| **N2** | **`StorageSubPage.kt` 文案 hunk 的归属裁决**：需总指挥先决定 §2.9 Profile 桶是否纳入 E；若纳入，可把 `OfflineBooksResidueClear.clear()` 调用点 + `ProfileAction.ClearReaderCacheNow` + `ProfileRoute` 分支作为「清空离线保护」子片单独拆 | `StorageSubPage.kt`、`ProfileUiState.kt`、`ProfileRoute.kt` | 无重叠 |
| **N3** | **库浏览器搜索/选择边界组隔离编译**（裁决报告 N2） | `LibraryBrowserViewModel.kt` 的 (b) 族 + `LibraryBrowserSelectionTest.kt` | 无重叠 |
| **N4** | **B1 窗口**：按 SEAM REQUEST 修正 `ShelfImporter.kt` / `ShelfImporterTest.kt` 的手工 hunk 编辑 | 属 B1 窗口 | — |

**明确不做**：不把 `StorageSubPage.kt` / `ProfileUiState.kt` / `ProfileRoute.kt` 顺手纳入 B2；不把 `BookRepository.kt` 的 B1 hunks 带入；不修改共享仓库的任何文件（除本报告）。

---

## 九、纪律声明

- 共享恢复仓库内**唯一写入 = 本文件**（`docs/coordination/legacy-stages/2026-09/2026-09-29-stage-e-b2-cache-isolated-result.md`）。所有构建、补丁、日志、XML、清单均落在 `E:\creation-reading-assistant\stage-e-b2-cache-isolated-20260929\`。
- **未**修改共享源码 / 测试 / 公共配置 / 其他窗口报告 / `hold-180-row-mapping-updated.csv`；**未** stage / commit / push / reset / checkout；**未**迁移目录；**未**操作设备、MuMu、ADB、正式书库或原始仓库。
- 本轮在隔离目录内实际运行：`git archive`（只读导出）、`git apply`（仅作用于 E 盘隔离目录）、`:app:compileDebugKotlin`、`:app:compileDebugUnitTestKotlin`、`:app:testDebugUnitTest`（4 个定向类）。**未**运行 `lintDebug` / `assembleDebug` / `connectedAndroidTest`。
- **未以整棵工作树旧测试结果替代隔离候选结果**：本报告全部测试数字均来自隔离目录 `android\app\build\test-results\testDebugUnitTest\` 的 XML（v1 与 v2 各 4 份，均已归档并留 SHA-256）。
- v1 的 1 项失败**如实保留**，未用 v2 结果覆盖；v2 的适用范围已在 §6.3 明确限定为「v1 + 最小夹具适配」。
- 未把「已通过定向 JVM」表述为「已可提交」：§七 的 8 项缺口与 §八 N1 的接缝绑定要求必须先满足。
