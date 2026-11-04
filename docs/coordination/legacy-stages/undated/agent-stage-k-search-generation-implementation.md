# S03-1 实现报告：全量重建搜索索引旧任务晚到写入与代次并发协议

> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`  
> 分支：`codex/recover-worktree-20260920`  
> 基准 HEAD：`5aed5939600e491843e3c44453921bd6273a233c`  
> 收工 HEAD：`5aed5939600e491843e3c44453921bd6273a233c`（严格遵守红线：未 commit/push/stage）  
> 报告文件：`docs/coordination/legacy-stages/undated/agent-stage-k-search-generation-implementation.md`  

---

## 1. 任务背景与核心修复目标

在原生 Android 搜索索引子系统（`SearchIndexRepository`）中，存在全量重建与增量扫描的严重时序缺陷（S03-1）：
1. **重置游标被旧扫描晚到覆写**：当后台增量扫描正在遍历书架（如处于书本 $B_1$）时，用户在界面触发全量重建（`triggerFullRebuildNow()` / `invalidateSweepForFullRebuild()`），游标被重置为 `null`（从头重建）；然而处于就绪或挂起态的旧扫描任务继续推进，会把重置后的游标写回为 $B_1$，导致重建直接跳过前半部书架；
2. **晚到写入破坏词条与覆盖率**：重建代次变化后，旧任务继续向 `search_terms` 和 `search_index_coverage` 分批落盘，覆盖新一轮重建的清空与重算状态；
3. **进度状态被旧任务 `finally` 抹除**：`ensureIndexedIncremental` 和 `indexSingleBook` 共享 `_progress.value`，旧任务退出时的 `finally` 块会粗暴将 `isRunning = false`，抹除新任务或单本导入任务的活跃运行状态。

本阶段完成了以下目标：
1. **测试先行（Red-Green-Refactor）**：建立确定性 JVM 并发测试，使用 `CompletableDeferred` 双向屏障握手消除任何 `sleep` 或调度运气，严格捕获红灯覆写与覆盖失败证据后，驱动产品代码修复达到全绿；
2. **代次协议与短临界区原子检查**：引入 `sweepLock = Mutex()`、单调递增代次 `currentGeneration = AtomicLong(1L)`、递增任务令牌 `jobIdSequence` 与原子持有引用 `currentSweepJobId`。通过 `runIfCurrentGeneration(expectedGeneration) { action() }` 消除 TOCTOU 竞争，临界区仅保护微秒级 Room 写入，零长耗时持锁；
3. **全覆盖写入保护**：全面保护 `indexOneBook` 与 `processChunked` 的全部 6 个写入点（首轮整删、显示文失效清理、TXT/EPUB 分批落盘、覆盖率落盘、尾批词条落盘、书级与章节游标更新）；
4. **进度状态与单本索引解耦**：引入 `singleBookRunningCount` 计数器，解耦 `indexSingleBook` 与全库增量扫描的运行状态竞争，`finally` 块采用非挂起原子 CAS 归还执行权，绝不误抹全局 `isRunning`。

---

## 2. 修改文件清单与 Diff 摘要

本次修改严格受限于授权文件清单，无任何越界修改：

| 文件路径 | 状态 | 职责与变更说明 |
|---|---|---|
| `android/app/src/main/java/com/creationreadingassistant/data/repository/SearchIndexRepository.kt` | `M` | 1. 增加 `sweepLock`、`currentGeneration`、`currentSweepJobId`、`jobIdSequence`、`singleBookRunningCount`；<br>2. 增加短临界区原子检查辅助 `runIfCurrentGeneration`；<br>3. 改造 `invalidateSweepForFullRebuild`：在互斥保护下自增代次、清空执行权、重置游标；<br>4. 改造 `ensureIndexedIncremental`：通过原子 CAS 抢占执行权并获取当前代次，在 `finally` 块通过原子 CAS 归还并安全刷新 `isRunning`；<br>5. 改造 `indexSingleBook`：通过 `singleBookRunningCount` 解耦，传入 `generation = null`（单本独立构建不受代次阻断）；<br>6. 改造 `processChunked` 与 `indexOneBook`：参数透传 `generation`，全部 6 个写入点加代次保护，循环间隙增加快速代次失效让出检测；<br>7. 暴露 `testHookAfterStep` 供确定性测试在临界区外拦截。 |
| `android/app/src/test/java/com/creationreadingassistant/data/repository/SearchIndexRepositoryRebuildConcurrencyTest.kt` | `新建` | 建立 5 个确定性并发与异常隔离测试用例，覆盖：游标覆写防御、词条与覆盖率晚到写入防御、新扫描从头开始、旧任务退出不抹除新任务运行态、取消异常透传与单本失败隔离。 |
| `docs/coordination/legacy-stages/undated/agent-stage-k-search-generation-implementation.md` | `新建` | 本次交付总结报告。 |

### 禁改文件确认
- 数据库 schema / Room DAO / Entity / Migration：**未触碰**（完全基于既有 DAO 接口实现）
- `SearchIndexWorker.kt` / `SearchIndexScheduler.kt`：**未触碰**
- `SearchIndexBookRefreshCoordinator.kt`：**未触碰**
- `ShelfImporter.kt` / `ShelfViewModel.kt`：**未触碰**
- `2026-09-22-current-task-ledger.md`：**未触碰**

---

## 3. 核心并发协议与机制设计

### 3.1 状态与所有权模型
```
                         ┌──────────────────────────────────────────────┐
                         │      currentGeneration: AtomicLong(1L)       │
                         │      currentSweepJobId: AtomicReference<Long?>│
                         │      singleBookRunningCount: AtomicInteger(0)│
                         │      sweepLock: Mutex()                      │
                         └──────────────────────────────────────────────┘
                                                  │
                 ┌────────────────────────────────┴───────────────────────────────┐
                 ▼                                                               ▼
    【ensureIndexedIncremental】                                    【invalidateSweepForFullRebuild】
 1. myJobId = jobIdSeq.inc()                                      1. sweepLock.withLock {
 2. CAS currentSweepJobId(null -> myJobId)                        2.    currentGeneration.incrementAndGet()
    └─ 成功: myGen = currentGen.get()                             3.    currentSweepJobId.set(null)
    └─ 失败: 放弃并返回 PROGRESSED                                4.    stateDao.set(last_scanned_book_id = null)
 3. processChunked(generation = myGen)                            5. }
 4. finally {                                                     6. wipeAllTermsForAllBooks()
       currentSweepJobId.compareAndSet(myJobId, null)             7. _progress.value = Progress(isRunning = ...)
       updateProgressRunningState()
    }
```

### 3.2 短临界区原子检查与写入（消除 TOCTOU）
为防止“检查代次时有效、写入时代次已被更新”的竞争窗口（TOCTOU），写操作与代次校验在微秒级短临界区内原子执行：
```kotlin
private suspend inline fun <T> runIfCurrentGeneration(
    expectedGeneration: Long?,
    crossinline action: suspend () -> T,
): Boolean {
    if (expectedGeneration == null) {
        action()
        return true
    }
    return sweepLock.withLock {
        if (expectedGeneration == currentGeneration.get()) {
            action()
            true
        } else {
            false
        }
    }
}
```
若 `expectedGeneration != currentGeneration.get()`，说明全量重建已触发，直接返回 `false` 且**绝不执行任何 DAO 写入**，旧任务立即清空批次并让出退出。

### 3.3 写入点覆盖清单
在 `indexOneBook` 与 `processChunked` 中保护了全部 6 个写入点：
1. **W1（首轮整删）**：`fresh` 状态下 `termDao.deleteByBook(book.id)` 与 `coverageDao.deleteByBook(book.id)`；
2. **W2（显示文失效清理）**：`abandonDisplay` 触发的 `discardDisplayRows`；
3. **W3（章节循环分批落盘）**：TXT 与 EPUB 在 `pending.size >= UPSERT_BATCH_ROWS` 时的 `termDao.upsertAll(pending.toList())`；
4. **W4（覆盖率写入）**：全书完成或让出时的 `coverageDao.upsertAll(...)`；
5. **W5（尾批词条落盘）**：全书最后残留的 `termDao.upsertAll(slice)`；
6. **W6（书级与章节游标更新）**：`stateDao.set(SearchIndexStateRow(...))`（包括成功完成、章节断点续建、单本失败落锚）。

### 3.4 运行态与单本构建解耦
`_progress.value.isRunning` 计算统一收敛至纯判定函数：
```kotlin
private fun isAnyIndexingRunning(): Boolean =
    currentSweepJobId.get() != null || singleBookRunningCount.get() > 0
```
- `indexSingleBook` 开始时 `singleBookRunningCount.incrementAndGet()`，结束时 `decrementAndGet()`，传入 `generation = null` 免于代次约束；
- `ensureIndexedIncremental` 结束时通过 `currentSweepJobId.compareAndSet(myJobId, null)` 原子归还；若已被新任务或重建接管，CAS 失败，绝不把全局 `isRunning` 误抹为 `false`。

---

## 4. 测试先行验证证据（Red 到 Green）

### 4.1 红灯阶段（Red Phase）确证证据
在实现产品代码前，运行 `SearchIndexRepositoryRebuildConcurrencyTest`，捕获到 3 个确定性并发失败：
```
> Task :app:testDebugUnitTest

SearchIndexRepositoryRebuildConcurrencyTest > old sweep cannot overwrite reset cursor after full rebuild triggered FAILED
    java.lang.AssertionError: 旧任务不得将重置后的游标覆盖为 b1 expected: null but was: "b1"
        at org.junit.Assert.fail(Assert.java:89)
        at org.junit.Assert.failNotEquals(Assert.java:835)
        at org.junit.Assert.assertEquals(Assert.java:120)
        at com.creationreadingassistant.data.repository.SearchIndexRepositoryRebuildConcurrencyTest...

SearchIndexRepositoryRebuildConcurrencyTest > old sweep cannot overwrite terms and coverage after new generation rebuild FAILED
    java.lang.AssertionError: 旧任务在代次失效后不得继续扫描后续书籍 (b2) 写入词条 expected:<1> but was:<3>
        at org.junit.Assert.fail(Assert.java:89)
        at org.junit.Assert.failNotEquals(Assert.java:835)
        at org.junit.Assert.assertEquals(Assert.java:647)
        at com.creationreadingassistant.data.repository.SearchIndexRepositoryRebuildConcurrencyTest...

SearchIndexRepositoryRebuildConcurrencyTest > old sweep finally does not clear new sweep isRunning progress state FAILED
    java.lang.AssertionError: 新任务运行状态不得被旧任务 finally 抹除为 false
        at org.junit.Assert.fail(Assert.java:89)
        at org.junit.Assert.assertTrue(Assert.java:42)
        at com.creationreadingassistant.data.repository.SearchIndexRepositoryRebuildConcurrencyTest...

5 tests completed, 3 failed
```

### 4.2 绿灯阶段（Green Phase）验证证据
应用短临界区互斥、代次校验及非持锁协作屏障后，执行定向单测：
```bash
cmd /c "gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.data.repository.SearchIndexRepositoryRebuildConcurrencyTest --no-parallel --rerun-tasks"
```
测试执行结果（退出码 0）：
```
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 1m 37s
30 actionable tasks: 30 executed
```
测试报告 HTML 详情（`SearchIndexRepositoryRebuildConcurrencyTest.html`）：
- `cancellation exception propagates and single book failure isolates properly`: **passed** (0.039s)
- `new scan starts from the first book after full rebuild reset`: **passed** (0.021s)
- `old sweep cannot overwrite reset cursor after full rebuild triggered`: **passed** (0.017s)
- `old sweep cannot overwrite terms and coverage after new generation rebuild`: **passed** (0.016s)
- `old sweep finally does not clear new sweep isRunning progress state`: **passed** (4.495s)
- **总计**：5 tests completed, 0 failures, 100% successful.

---

## 5. 回归测试与编译检查

### 5.1 相关仓储单测回归
```bash
cmd /c "gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.feature.search.SearchIndexRepositorySearchTest --tests com.creationreadingassistant.data.repository.SearchIndexBookRefreshCoordinatorTest --tests com.creationreadingassistant.ui.viewmodel.ShelfImporterTest --no-parallel"
```
- **输出**：`BUILD SUCCESSFUL in 16s`（所有既有搜索、协调器、导入测试 100% PASS）。

### 5.2 全量 Kotlin 编译检查
```bash
cmd /c "gradlew.bat :app:compileDebugKotlin --no-parallel"
```
- **输出**：`BUILD SUCCESSFUL in 2s`（0 错误，无编译警告新增）。

---

## 6. 纪律红线核对清单

- [x] 唯一工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- [x] 当前分支：`codex/recover-worktree-20260920`，HEAD 未变动
- [x] 未执行 `git commit`、`git push`、`git stage`、`git reset`、`git checkout`
- [x] 未全仓格式化，未清理未追踪文件
- [x] 未修改或进入原始仓库
- [x] 未连接或操作真实 Android 手机/模拟器，未运行 ADB
- [x] 未触碰 Room 数据库 schema、版本号或 migration 脚本
- [x] 仅修改与新建授权清单内的 3 个文件
