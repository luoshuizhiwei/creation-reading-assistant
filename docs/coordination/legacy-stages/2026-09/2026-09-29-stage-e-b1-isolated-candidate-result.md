# 阶段 E 候选 B1（S03 搜索索引并发）隔离候选与验证报告

> **执行时间**：2026-09-29T21:18+08:00  
> **基线仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`  
> **分支**：`codex/recover-worktree-20260920`  
> **HEAD 基线**：`513891d6a66a9b78e072ba759d34ed580d377ecf`（包含首批 G0+U `209ec8a` 与 G2+runner `513891d`）  
> **隔离工作目录**：`E:\creation-reading-assistant\stage-e-b1-isolated-20260929\`  
> **操作性质**：独立目录隔离构建与定向 JVM 验证；共享恢复仓库未暂存、未提交、未修改任何已有工作树文件；未操作 ADB、MuMu、真实手机或原始仓库。

---

## 一、旧审查报告勘误（重要）

针对先前旧审查（`docs/coordination/reviews/2026-09/2026-09-29-stage-e-b1-search-concurrency-claude-review.md`）中的事实错误与分析缺口，经源码级对账与实际隔离构建核实，修正如下：

| 编号 | 涉及项 | 旧审查说法 | 本次实测真相与纠正 |
|---|---|---|---|
| **勘误 1** | `SearchTermDao.kt` S1–S4 状态 | 称 G0 提交已包含 S1–S4（删除 `deleteByChapter`、`countTerms`、`rowsByTerm`），工作树仅剩 S6 | **严重事实错误**。G0（`209ec8a`）仅包含 S5（删除冗余索引），S1–S4 仍为工作树相对 HEAD 的未提交改动。B1 候选严格按指令**仅提取 S6 hunk**（`SearchIndexCoverageDao.distinctBookIds()`），不带入 S1–S4 死方法删除。 |
| **勘误 2** | `SearchIndexRepositoryRebuildConcurrencyTest.kt` | 称该文件 `Test-Path = False`，工作树不存在，标记为缺失不可验证 | **严重事实错误**。文件实际位于 `android/app/src/test/java/com/creationreadingassistant/data/repository/SearchIndexRepositoryRebuildConcurrencyTest.kt`（1083 行），状态为未跟踪（`??`）。内含 19 个基于双向屏障握手的 S03-1 确定性并发测试，本次已完整纳入验证。 |
| **勘误 3** | 未跟踪纯算法依赖 | 未列入候选清单 | `SearchIndexRepository.kt` 在重构时拆分出的 `SearchHitRanker.kt` 与 `TxtChapterSplitter.kt` 均为未跟踪文件，属于 B1 的**强制编译闭包成员**，若漏掉会导致 `compileDebugKotlin` 报 unresolved reference。本次已补齐纳入。 |
| **勘误 4** | `BookRepository.kt` 删除清索引护栏 | 称仅需 4 个 hunk（构造器注入 + restoreBook/restoreDeletions），漏掉删除方法 | `applyDeletionLocked` 中的 `searchIndexRepository?.deleteIndexForBook(id)` 是 M-BUG-10 的核心写入点。若漏带，删除书籍时不会清理孤儿索引，`BookRepositorySearchIndexTest` 必红。本次已精确纳入该 hunk。 |
| **勘误 5** | 测试 Fake DAO 抽象实现 | 称测试文件可直接通过编译 | 由于 `SearchTermDao.kt` 严格保留了未清理的 S1–S4 抽象方法，工作树中原本移除了这些方法的测试 `FakeSearchTermDao` 会报 `does not implement abstract member`。测试必须实现这些接口成员（保留 dummy/原有实现）以满足 Kotlin 编译期对齐。 |

---

## 二、B1 完整编译闭包与精确候选清单

B1 候选题材为 **S03 搜索索引并发原子协调、孤儿回收与撤销恢复**。在隔离目录中，以 HEAD（`513891d`）为基线，精确应用以下 11 个文件，其余文件严格保持 HEAD 原样：

### 2.1 纳入清单（6 个修改文件 + 5 个新增文件）

| 状态 | 文件路径 | 对应修改 / hunk 内容 | 纳入理由与编译关系 |
|---|---|---|---|
| **M** | `android/app/src/main/java/.../data/local/dao/SearchTermDao.kt` | **仅取 S6 hunk**：在 `SearchIndexCoverageDao` 新增 `distinctBookIds()` | 孤儿回收 `purgeOrphanIndexRows()` 必要接口；严格排除 S1–S4 |
| **M** | `android/app/src/main/java/.../data/local/dao/BookDao.kt` | **仅取 B5 hunk**：新增 `getAllActive()` 与 `activeIds()` | `SearchIndexRepository` 过滤已删孤儿行（`bookDao.activeIds()`）的强制硬依赖 |
| **M** | `android/app/src/main/java/.../data/repository/SearchIndexRepository.kt` | 整文件（全部 17 个 S03 hunk） | S03-1 并发原子协调状态机、互斥锁、孤儿回收与打分过滤 |
| **M** | `android/app/src/main/java/.../data/repository/SearchIndexScheduler.kt` | 移除 `triggerNow(force: Boolean)` 默认值 | 编译期防止静默丢弃置脏重扫任务 |
| **M** | `android/app/src/main/java/.../data/repository/BookRepository.kt` | **精确 4 处 B1 hunk**：① 构造器注入 `searchIndexRepository` 与 `indexRefreshCoordinator`；② `applyDeletionLocked` 调 `deleteIndexForBook`；③ `restoreBook` 调 `coordinator.request`；④ `restoreDeletions` 调 `coordinator.request`（回退 `repo.reindexBookById`） | M-BUG-10 / S03-3 护栏：删书清索引，撤销恢复重建索引 |
| **M** | `android/app/src/test/java/.../feature/search/SearchIndexRepositorySearchTest.kt` | 5 个 S03 hunk + Fake DAO 适配 S6 | 孤儿过滤、打分聚合及 Fake DAO 接口对齐 |
| **??** | `android/app/src/main/java/.../data/repository/SearchIndexBookRefreshCoordinator.kt` | 新文件（整文件，60 行） | 异步逐书搜索索引刷新协调器（ApplicationScope 解耦） |
| **??** | `android/app/src/main/java/.../feature/search/SearchHitRanker.kt` | 新文件（整文件，58 行） | 搜索命中打分聚合纯内存算法（`SearchIndexRepository` 拆分依赖） |
| **??** | `android/app/src/main/java/.../feature/search/TxtChapterSplitter.kt` | 新文件（整文件，73 行，含 `TxtChapterSlice`） | 纯文本章节切片与防截断算法（`SearchIndexRepository` 拆分依赖） |
| **??** | `android/app/src/test/java/.../data/repository/BookRepositorySearchIndexTest.kt` | 新文件（整文件，193 行） | M-BUG-10 删书清索引与恢复自愈 JVM 测试（4 用例） |
| **??** | `android/app/src/test/java/.../data/repository/SearchIndexRepositoryRebuildConcurrencyTest.kt` | 新文件（整文件，1083 行） | S03-1 确定性并发屏障测试（19 用例） |

### 2.2 明确排除与未带入证明（证明零跨批次污染）

1. **严格排除 `ShelfImporter.kt`**：
   - 现状：保持 HEAD 原样，**0 差异**。
   - 证明：虽然工作树中 `ShelfImporter.kt` 也注入了 `SearchIndexBookRefreshCoordinator`（用于 S04 来源替换成功后异步触发重建），但该文件同时交织了 31 个 hunk（跨 S04 残留崩溃守卫、S05 批量导入事件发射、来源分层判定等），并引入多个未决新文件。由于 HEAD 里的 `ShelfImporter.kt` 不调用 coordinator，**保持其不动对 B1 编译与运行 0 影响**，成功与 S04/S05 解耦。
2. **严格排除 `BookRepository.kt` 中的 B2 与 S05 改动**：
   - 排除 `clearBookCache` 返回值改造为 `BookCacheClearOutcome`（B2 专属）。
   - 排除 `OfflineBooksResidueClear` 文件层残留保护实现与全部相关 sealed class（B2 专属）。
   - 排除 `markFinished`、`cancelFinish`（S05 专属）。
   - 隔离目录中的 `BookRepository.kt` diff 仅有上述 4 处 B1 hunk，B2 与 S05 关键词匹配数为 0。
3. **严格排除 `SearchTermDao.kt` 的 S1–S4**：
   - `deleteByChapter`、`countTerms`、`rowsByTerm` 方法在主源码中原样保留，仅对 `SearchIndexCoverageDao` 追加 S6 `distinctBookIds()`。

---

## 三、隔离候选文件度量与 SHA-256 清单

隔离工作目录：`E:\creation-reading-assistant\stage-e-b1-isolated-20260929\`

| 相对路径 | 字节大小 | SHA-256 校验和 |
|---|---|---|
| `android/app/src/main/java/.../data/local/dao/BookDao.kt` | 7,905 | `E41B154243071D3CA91549DD43202B9BA7596347AB21284C8A2FBF06C4EFBA99` |
| `android/app/src/main/java/.../data/local/dao/SearchTermDao.kt` | 8,937 | `3181DC9B78E8B42E16591BFB0BEBEAB20630181ADD91C98C338C29BCBA0E30E6` |
| `android/app/src/main/java/.../data/repository/BookRepository.kt` | 36,349 | `B6BB09C74E2E48853EBA7BBC8B357274A61DB48DEC527D51A6ED45374D5A9447` |
| `android/app/src/main/java/.../data/repository/SearchIndexRepository.kt` | 86,422 | `8AC41F136FDF4BF05CC110777A00A3137BD95404A65E79FFA998460CEDE35B5F` |
| `android/app/src/main/java/.../data/repository/SearchIndexScheduler.kt` | 4,383 | `337254759E52D481902967771515C6FF997AF0B30C089849E99FD9560B330BA4` |
| `android/app/src/main/java/.../data/repository/SearchIndexBookRefreshCoordinator.kt` | 2,718 | `83887755AA19CABA20347DE506DE2F1BA1D829050B099C750F4370463433B081` |
| `android/app/src/main/java/.../feature/search/SearchHitRanker.kt` | 2,504 | `B9BD3D20A889006FC7F1F03015A8668DBB8357B60D9221BCC16443317F8425AF` |
| `android/app/src/main/java/.../feature/search/TxtChapterSplitter.kt` | 2,851 | `4CDE1B55AA359FD8DD66A6474F4D2DC57307D1085B2C832AB35C6619AFD3208E` |
| `android/app/src/test/java/.../data/repository/BookRepositorySearchIndexTest.kt` | 8,821 | `D63DC6FB28169A1F28AEC5EA28609C481EADB7AC7B6E7D33065DF0EAD47D62D4` |
| `android/app/src/test/java/.../data/repository/SearchIndexRepositoryRebuildConcurrencyTest.kt` | 47,208 | `E2E22CE8324646C3C6320F4BF9F887589693799C1AA1C517459AB0AE058594BA` |
| `android/app/src/test/java/.../feature/search/SearchIndexRepositorySearchTest.kt` | 24,793 | `2A177D0F18A2B870F1529530A0544F8086722792748D6EE83BB2F9313B48A728` |

- **修改文件补丁包**：`stage-e-b1-modified.patch`（72,623 字节）  
  SHA-256：`9D0C272FB9F114F09B85EA9867D678049244EFFE743685CBA7760C3420DE20CB`

---

## 四、隔离验证执行记录与实际测试结果

所有构建与测试命令均在隔离目录 `E:\creation-reading-assistant\stage-e-b1-isolated-20260929\android\` 下执行。

### 4.1 编译阶段验证

| 验证阶段 | 执行命令 | 耗时 | 退出码 | 结果判定 | 任务日志路径 |
|---|---|---|---|---|---|
| **基线编译** | `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --no-daemon` | 1m 59s | 0 | **SUCCESS** | `task-357.log` |
| **B1 主源码编译** | `.\gradlew.bat :app:compileDebugKotlin --no-daemon` | 1m 6s | 0 | **SUCCESS**（闭包完整） | `task-461.log` |
| **B1 单元测试编译** | `.\gradlew.bat :app:compileDebugUnitTestKotlin --no-daemon` | 1m 1s | 0 | **SUCCESS**（Fake DAO 接口对齐） | `task-479.log` |

### 4.2 定向 JVM 测试执行

#### 第 1 轮：B1 核心 3 套件定向测试
- **命令**：`.\gradlew.bat :app:testDebugUnitTest --tests "*SearchIndexRepositoryRebuildConcurrencyTest*" --tests "*SearchIndexRepositorySearchTest*" --tests "*BookRepositorySearchIndexTest*" --no-daemon`
- **耗时**：58s
- **退出码**：0（`BUILD SUCCESSFUL`）
- **日志**：`task-483.log`
- **测试结果明细**：

| 测试类 / 套件 | 测试数 | PASS | FAIL | ERR | 覆盖与判别目标 |
|---|---|---|---|---|---|
| `BookRepositorySearchIndexTest` | 4 | **4** | 0 | 0 | 删除书/批量删书清索引（M-BUG-10）；撤销恢复后重建索引；重建失败不崩溃 |
| `SearchIndexRepositoryRebuildConcurrencyTest` | 19 | **19** | 0 | 0 | S03-1 确定性并发：旧扫描任务晚到写入屏蔽、代次失效、所有权协作无死锁 |
| `SearchIndexRepositorySearchTest` | 27 | **27** | 0 | 0 | 孤儿索引过滤（依赖 `activeIds` 与 `distinctBookIds`）、多文本基准打分聚合 |
| **小计** | **50** | **50** | **0** | **0** | **100% 通过** |

#### 第 2 轮：B1 相关仓储 5 套件全量扩展回归
- **命令**：`.\gradlew.bat :app:testDebugUnitTest --tests "*SearchIndex*" --tests "*BookRepository*" --no-daemon`
- **耗时**：36s
- **退出码**：0（`BUILD SUCCESSFUL`）
- **日志**：`task-491.log`
- **测试结果明细**：
  - `BookRepositorySearchIndexTest`: 4 / 4 PASS
  - `BookRepositoryTest`: 13 / 13 PASS（覆盖 `BookRepository` 既有业务行为）
  - `SearchIndexRepositoryRebuildConcurrencyTest`: 19 / 19 PASS
  - `SearchIndexWorkerPolicyTest`: 12 / 12 PASS（覆盖调度策略）
  - `SearchIndexRepositorySearchTest`: 27 / 27 PASS
  - **总计**：**75 / 75 PASS，0 FAIL，0 ERROR**。

---

## 五、未验证边界与已知局限

1. **未执行设备 Instrumentation / AndroidTest**：
   - 本次验证严格遵循不操作 MuMu、不占用 ADB 的纪律，所有验证均为 JVM 单元测试与字节码编译层。
   - `SearchIndexWorker` 在 Android 系统真机上的 WorkManager 唤醒、后台执行耗电及多进程竞争未在本轮执行。
2. **`ShelfImporter.kt` 的异步索引接缝暂未合流**：
   - `ShelfImporter` 在正文替换成功后调用 `coordinator.request(bookId)` 的接缝被有意保留在后续 S04 批次，以确保 B1 的最小性与原子性。
3. **`SearchTermDao.kt` 的 S1–S4 死方法留待清理批次**：
   - 生产代码已无任何 caller，但接口仍保留在 `SearchTermDao` 中，不构成运行时风险，待后续统一定期死代码清理。

---

## 六、下一步建议

1. **集成决策建议**：
   - B1 候选已经过严格隔离验证，证明其编译闭包完整、无多余依赖溢出，具备**独立交付能力**。
   - B1 可作为阶段 E 后续批次中的独立提交落盘，建议提交信息规范为：
     `feat(search): atomic coordination, orphan purge and restore reindexing (S03)`
2. **授权约束重申**：
   - 依据收口计划 §E，当前共享恢复仓库暂存区与工作树保持原样，未经用户明确授权不执行 stage、commit 或 push。
