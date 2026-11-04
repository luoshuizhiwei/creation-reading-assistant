# 来源刷新异步逐书索引更新（S04-2）：SearchIndexBookRefreshCoordinator

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 日期：2026-09-23
- 范围：S04-2 来源正文刷新成功后异步请求重建单书搜索索引

---

## 1. 开始 / 结束时的分支、HEAD 与 git status

| 项 | 状态 |
|---|---|
| 分支 | `codex/recover-worktree-20260920`（未变） |
| HEAD | `5aed5939600e491843e3c44453921bd6273a233c`（未变） |
| 基准状态 | 基于 2026-09-23 阶段 D 之后的未提交工作树 |

**严格遵守红线**：
- 未执行：reset / checkout / clean / stash / stage / commit / push；
- 未全仓格式化；未覆盖其他窗口的修改；
- 未进入或修改原始仓库 `D:\develop\Code\Codex\creation-reading-assistant`；
- 未操作手机或运行 ADB。

---

## 2. 实际修改与新建的文件

| 文件 | 归属 | 改动性质 | 说明 |
|---|---|---|---|
| `android/app/src/main/java/com/creationreadingassistant/data/repository/SearchIndexBookRefreshCoordinator.kt` | 授权 | 新建 | 异步逐书搜索索引刷新协调器，单例，位于 data.repository 层 |
| `android/app/src/main/java/com/creationreadingassistant/feature/library/ShelfImporter.kt` | 授权 | 修改 | 构造注入协调器；在 `refreshFromSource` 正文替换成功且来源基线推进后调用 `request(bookId)` |
| `android/app/src/test/java/com/creationreadingassistant/data/repository/SearchIndexBookRefreshCoordinatorTest.kt` | 授权 | 新建 | 协调器 6 项 JVM 测试，覆盖立即返回、调度执行、异常日志、多次触发、取消传播 |
| `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ShelfImporterTest.kt` | 授权 | 修改 | 注入协调器 mock；新增 5 项针对索引请求提交时机（1/0/0/0）与返回值的断言 |
| `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ShelfViewModelTest.kt` | 授权 | 修改 | 仅因 `ShelfImporter` 构造参数变更调整测试中装配参数（传 mock），无业务逻辑修改 |
| `docs/coordination/legacy-stages/undated/agent-stage-e-per-book-index-refresh.md` | 授权（本报告） | 新建 | 记录 S04-2 实现细节、架构验证与测试证据 |

**文件所有权复核**：实际变动严格限定在上述授权范围内，无越界修改，无 `SEAM REQUEST`。
明确未触碰：`SearchIndexRepository.kt`、`SearchIndexScheduler.kt`、`ReaderSearchIndexCoordinator.kt`、`BookRepository.kt`、`ShelfViewModel.kt`、`ShelfUiState.kt`、数据库 schema/migration 与 Compose UI。

---

## 3. 架构设计与依赖方向

### 3.1 职责与依赖规则
- **单向依赖**：`SearchIndexBookRefreshCoordinator` 位于 `data.repository` 包内，仅依赖 `SearchIndexRepository`、`@ApplicationScope CoroutineScope`、`AppLog` 与标准协程库，**零** `ui` / `viewmodel` 依赖。
- **调用方**：`ShelfImporter`（位于 `feature.library`）依赖该协调器，属于高层依赖底层（feature -> data），完全符合既有架构单向依赖规则。
- **协程生命周期**：严格使用 `@ApplicationScope`（生命周期绑定 Application 进程，内含 SupervisorJob），不使用 `GlobalScope`，不自建无生命周期的 `CoroutineScope`。
- **委托而非重复实现**：不复制 `reindexBookById` 逻辑，直接委托注入的 `SearchIndexRepository.reindexBookById(bookId)`。
- **后续复用（S03-3）**：该协调器以单例方式提供统一的 `request(bookId: String)` 入口，未来 S03-3 撤销删除路径可直接注入复用，无需重造。

### 3.2 异常与取消模型
- **调用方立即返回**：`fun request(bookId: String)` 为同步非挂起函数，内部以 `applicationScope.launch` 触发，调用方提交后即时返回，正文刷新流程绝不等待整本索引。
- **普通失败隔离**：子协程内部 `try-catch` 捕获 `Exception`，仅调用 `AppLog.w("SearchIndexBookRefresh", ...)` 记录告警日志，静默消化，不向调用方抛出，不将成功的正文刷新逆转为失败。
- **取消透明**：显式捕获 `CancellationException` 并重新抛出（`throw e`），符合 Kotlin 协程最佳实践，不破坏结构化并发取消语义。

---

## 4. 来源正文刷新接缝接入（ShelfImporter）

在 `ShelfImporter.refreshFromSource(bookId: String)` 中严格遵循时序与 1/0/0/0 契约：
1. **未记录来源或无法解析 URI**（`NoSourceRef`）：直接 `return SourceRefreshResult.NoSourceRef`，**不提交**索引请求（0 次）。
2. **正文修复失败**（`Failed`）：`if (!outcome.ok) return SourceRefreshResult.Failed(outcome.message)`，**不提交**索引请求（0 次）。
3. **正文修复成功**：
   - 先调用 `refreshSourceBaseline(ref, uri)` 更新来源大小与修改时间基线；
   - 随后调用 `indexRefreshCoordinator.request(bookId)` 提交**恰好 1 次**异步重建请求；
   - 立即返回 `SourceRefreshResult.Success(outcome.message)`。

---

## 5. 新增与修改的测试用例

### 5.1 `SearchIndexBookRefreshCoordinatorTest`（6 项）
1. `request returns immediately without waiting for reindexBookById`：断言 `request()` 结束时，调度器未推进前 `reindexCalled` 为 false（非阻塞）。
2. `after dispatch runs reindexBookById exactly once with correct bookId`：调度器空闲后，断言 `reindexBookById("book-42")` 恰好被调用一次。
3. `ordinary exception is silently logged without cancelling the scope`：`reindexBookById` 抛出 `RuntimeException` 时，`request()` 不抛出异常，且 scope 继续保持活跃可用。
4. `each request call triggers exactly one reindex for the given bookId`：多次调用 `request` 分别为各自 bookId 触发且仅触发一次重建。
5. `ordinary exception triggers AppLog warn entry`：异常时在 `AppLog.entries` 中产生对应 bookId 的 WARN 级别日志。
6. `CancellationException is rethrown and not treated as ordinary failure`：抛出 `CancellationException` 时不记入 AppLog 普通失败日志。

### 5.2 `ShelfImporterTest`（5 项新增覆盖）
1. `refreshFromSource success submits index refresh request exactly once with correct bookId`：断言刷新成功分支下 `indexRefreshCoordinator.request("b1")` 恰好执行 1 次。
2. `refreshFromSource failure does not submit index refresh request`：格式不匹配修复失败分支下，断言 `request(any())` 执行 0 次。
3. `refreshFromSource NoSourceRef does not submit index refresh request`：快照无引用分支下，断言 `request(any())` 执行 0 次。
4. `refreshFromSource missing documentId does not submit index refresh request`：无 documentId 分支下，断言 `request(any())` 执行 0 次。
5. `refreshFromSource success result is unchanged even when coordinator request is called`：断言协调器接入不改变返回的 Success 结果与提示文案。

### 5.3 `ShelfViewModelTest`（装配适配）
- `createVm` 中为 `ShelfImporter` 提供 `indexRefreshCoordinator = mockk(relaxed = true)`，确保构造注入契约兼容，用例全部保持绿灯。

---

## 6. Fresh 验证命令与测试证据

在 `android/` 目录执行 fresh 命令验证：

### 6.1 Kotlin 编译检查
```powershell
.\gradlew.bat :app:compileDebugKotlin --no-parallel
```
- **退出码**：`0`（`BUILD SUCCESSFUL in 3s`）
- 15 actionable tasks: 15 up-to-date

### 6.2 目标测试全量重跑（强制 --rerun-tasks）
```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.data.repository.SearchIndexBookRefreshCoordinatorTest" --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest" --tests "com.creationreadingassistant.ui.viewmodel.ShelfViewModelTest" --no-parallel --rerun-tasks
```
- **退出码**：`0`（`BUILD SUCCESSFUL in 2m 46s`）
- **Actionable tasks**：`30 actionable tasks: 30 executed`（无 UP-TO-DATE 伪成功，全任务 fresh 实际执行）
- **XML 证据核验**（`app/build/test-results/testDebugUnitTest/`）：
  - `TEST-com.creationreadingassistant.data.repository.SearchIndexBookRefreshCoordinatorTest.xml`：
    `tests="6"`、`failures="0"`、`errors="0"`、`skipped="0"`
  - `TEST-com.creationreadingassistant.ui.viewmodel.ShelfImporterTest.xml`：
    `tests="29"`、`failures="0"`、`errors="0"`、`skipped="0"`
  - `TEST-com.creationreadingassistant.ui.viewmodel.ShelfViewModelTest.xml`：
    `tests="16"`、`failures="0"`、`errors="0"`、`skipped="0"`
  - **总计**：**51 / 51 passed**，0 failures, 0 errors, 0 skipped。

---

## 7. 开放与未覆盖事项说明

1. **JVM 测试通过不等于真实搜索生效**：测试中对 `SearchIndexRepository` 进行了 mock/打桩，尚未在真实 SQLite / FTS / Room 上跑完“替换正文 -> 触发协调器 -> 倒排索引重算 -> 关键词重新命中”的端到端集成链路。
2. **S04-1 仍开放**：当前下拉刷新仍会直接批量替换正文，缺少前置用户确认弹窗；`ShelfUiState.kt` 文案与实际行为的漂移待后续 S04-1 统一修正。
3. **S03-3 待接入**：`SearchIndexBookRefreshCoordinator` 已就绪，但 `BookRepository.kt` 的撤销删除路径尚未接入该协调器，仍处于同步等待状态。
4. **真机未执行**：本轮未在真机上运行测试，未打包 APK，未操作手机。

---

## 8. 边界声明

- 未执行 commit、push、stage、reset、checkout、clean、stash。
- 未操作真机设备或运行 ADB。
- 未修改任何未授权文件。
