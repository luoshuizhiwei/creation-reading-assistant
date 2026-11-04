# S03-3 实现报告：撤销删除接入异步索引协调器（解除同步阻塞）

> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`  
> 分支：`codex/recover-worktree-20260920`  
> 基准 HEAD：`5aed5939600e491843e3c44453921bd6273a233c`  
> 收工 HEAD：`5aed5939600e491843e3c44453921bd6273a233c`（严格遵守红线：未 commit/push/stage）  
> 报告文件：`docs/coordination/legacy-stages/undated/agent-stage-h-undo-delete-async-reindex.md`  

---

## 1. 任务背景与核心修复目标

原撤销删除恢复书籍路径（`BookRepository.restoreDeletions` 与兜底恢复 `restoreBook`）中：
- 恢复书籍落库后，旧实现会通过 `withContext(Dispatchers.IO) { searchIndexRepository.reindexBookById(bookId) }` 同步等待整本书全文索引重建完成后才返回；
- 若恢复的书籍篇幅较长（例如数兆字节的 TXT 或大型 EPUB），同步扫描、分词并写入 Room 索引库将导致恢复操作卡顿甚至引发 ANR；
- 本任务将 S04-2 建立的 `SearchIndexBookRefreshCoordinator` 接缝复用到撤销删除路径中，实现解耦与异步化。

本阶段完成了以下目标：
1. **注入协调器单例**：
   - 在 `BookRepository` 构造函数中注入 `SearchIndexBookRefreshCoordinator`（单例，基于 `@ApplicationScope` 具备异步执行与异常安全日志能力，默认值 `= null` 兼容测试环境）。
2. **解除同步阻塞**：
   - 恢复书籍成功后，删除原有的同步阻塞等待 `repo.reindexBookById(bookId)`，改为调用 `indexRefreshCoordinator.request(bookId)`；
   - 撤销删除立即返回恢复报告，不阻塞等待整本重建完成。
3. **保持严格契约**：
   - 恢复失败（例如找不到被删记录、被删记录已无删除戳、或快照恢复未命中）时，绝不提交重建请求；
   - 恢复成功时，`indexRefreshCoordinator.request(bookId)` 恰好提交 1 次，且传入正确的 `bookId`；
   - 在未注入协调器的测试环境中优雅回退到旧同步逻辑，确保既有测试夹具的无缝兼容。

---

## 2. 修改文件清单与 Diff 摘要

本次修改严格受限于授权文件清单，无任何越界修改：

| 文件路径 | 状态 | 职责与变更说明 |
|---|---|---|
| `android/app/src/main/java/com/creationreadingassistant/data/repository/BookRepository.kt` | `M` | 1. 构造函数新增 `@Inject constructor(..., private val indexRefreshCoordinator: SearchIndexBookRefreshCoordinator? = null)`；<br>2. `restoreBook(bookId)` 成功恢复时调用 `indexRefreshCoordinator?.request(bookId)`，未恢复时不调用；<br>3. `restoreDeletions` 遍历成功恢复的书籍（`it.restoredBook`），通过 `indexRefreshCoordinator.request(bookId)` 异步提交请求，解除同步阻塞等待。 |
| `android/app/src/test/java/com/creationreadingassistant/data/repository/BookRepositoryTest.kt` | `M` | 1. 在 `setUp` 中注入 mock 的 `indexRefreshCoordinator`；<br>2. 新增 5 个针对 S03-3 的完整覆盖测试用例（覆盖成功异步请求、解除同步等待、找不到已删书、删除戳为空、快照未恢复等各种边界）。 |
| `docs/coordination/legacy-stages/undated/agent-stage-h-undo-delete-async-reindex.md` | `新增` | 本次交接报告。 |

### 禁改文件确认
- `SearchIndexBookRefreshCoordinator.kt`：**未触碰**（直接注入复用）
- `ShelfImporter.kt`：**未触碰**
- `ShelfViewModel.kt`：**未触碰**
- `ShelfRoute.kt`：**未触碰**
- `SearchIndexRepository.kt`：**未触碰**
- `SearchIndexScheduler.kt`：**未触碰**
- 数据库 schema / DAO / Entity / Migration：**未触碰**
- `2026-09-22-current-task-ledger.md`：**未触碰**

---

## 3. 核心机制设计

### 3.1 撤销恢复时序对比
```
【优化前】：
restoreDeletions()
       │
       ├─ Room Transaction: 恢复书籍、正文、会话等
       │
       └─ withContext(Dispatchers.IO) { repo.reindexBookById(bookId) } ──> [同步阻塞等待重扫/分词/写库 (数秒)]
               │
               ▼
       返回 List<BookRestoreReport>（用户界面卡顿）

【优化后】：
restoreDeletions()
       │
       ├─ Room Transaction: 恢复书籍、正文、会话等
       │
       ├─ indexRefreshCoordinator.request(bookId) ──> [非阻塞提交给 ApplicationScope 协程后台处理]
       │
       ▼
立即返回 List<BookRestoreReport>（界面丝滑无感知）
```

### 3.2 兼容与降级设计
```kotlin
if (indexRefreshCoordinator != null) {
    reports
        .filter { it.restoredBook }
        .map { it.bookId }
        .forEach { bookId ->
            indexRefreshCoordinator.request(bookId)
        }
} else {
    // 降级回退：支持历史未 mock coordinator 的测试夹具
    searchIndexRepository?.let { repo ->
        reports
            .filter { it.restoredBook }
            .map { it.bookId }
            .forEach { bookId ->
                runCatching {
                    withContext(Dispatchers.IO) { repo.reindexBookById(bookId) }
                }.onFailure { e ->
                    AppLog.w("BookRepository", "...")
                }
            }
    }
}
```

---

## 4. 验证命令与测试执行输出

### 4.1 编译检查
**命令**：
```powershell
.\gradlew.bat :app:compileDebugKotlin --no-parallel
```
- **退出码**：`0`
- **结果**：BUILD SUCCESSFUL in 16s

### 4.2 单元测试（强制 fresh 执行）
**命令**：
```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.data.repository.BookRepositoryTest" --no-parallel --rerun-tasks
```
- **退出码**：`0`
- **执行时间**：1m 44s
- **测试结果**：`23 tests completed, 0 failed, 0 skipped`（既有 18 项 + S03-3 新增 5 项全绿）。
- **新增用例清单**：
  1. `restoreBook calls indexRefreshCoordinator request once on successful restore without synchronous reindex`：成功恢复时调用 `request("b1")` 恰好 1 次，断言 `searchIndexRepository.reindexBookById` 调用 0 次（解除同步阻塞）。
  2. `restoreBook does not call indexRefreshCoordinator request when book not found in deleted`：未在已删列表中找到该书时，绝不调用 `request`。
  3. `restoreBook does not call indexRefreshCoordinator request when deleted_at is null`：被删记录的 `deleted_at` 戳为 null 时，绝不调用 `request`。
  4. `restoreDeletion delegates to async coordinator and does not synchronously wait for reindex`：快照恢复成功时，调用协调器恰好 1 次，且不进行同步调用。
  5. `restoreDeletion does not call coordinator request when book is not restored`：快照未命中已删书籍（`restoredBook = false`）时，绝不调用 `request`。

### 4.3 联合回归验证
**命令**：
```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.data.repository.SearchIndexBookRefreshCoordinatorTest" --tests "com.creationreadingassistant.data.repository.BookRepositoryTest" --no-parallel
```
- **退出码**：`0`
- **测试结果**：
  - `SearchIndexBookRefreshCoordinatorTest`：6 tests PASS
  - `BookRepositoryTest`：23 tests PASS
  - **总计：29 项测试全部通过，0 失败**。

### 4.4 历史测试夹具兼容性回归
**命令**：
```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.data.repository.BookRepositorySearchIndexTest" --no-parallel
```
- **退出码**：`0`
- **测试结果**：全部通过，证明引入协调器并未破坏任何已有未迁移测试夹具。

---

## 5. 未覆盖与未验证事项

1. **极端海量书籍批量撤销时的后台任务排队**：当前由 `ApplicationScope` 针对单本恢复派发子协程，若批量撤销恢复数十本超大书籍，后台重建在 IO/CPU 受限下的排队影响需在真机测试阶段综合观测。
2. **真机撤销删除 Snackbar 与交互响应**：受纪律红线约束未连接 ADB / 真机，在真机界面上触发撤销删除后的帧率提升与无卡顿体感由集成阶段统一验收。

---

## 6. 安全与规范声明

- [x] 严格遵守单一工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- [x] 未执行 `git commit`、`git push`、`git stage`、`git reset`、`git checkout`、`git clean` 或 `git stash`
- [x] 未全仓格式化，未全仓 renormalize
- [x] 未触碰或修改原始仓库
- [x] 未运行任何 ADB 命令，未操作真实手机，未使用 MuMu 模拟器
- [x] 未触碰禁止文件，无越界修改（SEAM REQUEST：无）
- [x] 完整保留工作区中其他并发 Agent 的既有未提交代码
