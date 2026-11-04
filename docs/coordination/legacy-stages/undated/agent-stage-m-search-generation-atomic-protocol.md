# S03-1 搜索索引全量重建统一原子协议重构与二次复核修复报告

**阶段代号**：`stage-m-search-generation-atomic-protocol`  
**基准分支**：`codex/recover-worktree-20260920`  
**基准提交**：`5aed5939600e491843e3c44453921bd6273a233c`  
**工作仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`  
**执行日期**：2026-09-23  

---

## 1. 任务背景与二次复核缺口分析

在 Codex 对 S03-1 进行的二次复核中，虽然既有 11/11 并发测试通过，但复核指出当前实现仍然存在关键的时序与协议缺陷，不能宣布竞态已关闭：

1. **时序撕裂缺陷**：`ensureIndexedIncremental` 在 CAS 取得 `currentSweepJobId` 后，在锁外才去读取 `currentGeneration.get()`；若在占权后、读代次前重建触发，旧扫描会以新代次读取旧游标，导致全量重建跳过第 0 本书。占权、读代次与重建接管必须构成同一个原子协议；
2. **清理路径裸奔缺陷**：tokenizer 版本不匹配时，`wipeAllTermsForAllBooks` 未服从代次与互斥协议，旧任务会删掉并发重建写入的新代次 term 和 coverage；
3. **测试断言虚假与假绿灯**：
   - 存在 `nextResult != null` 的恒真断言（非空类型判定）；
   - 旧词条断言使用 `it.term.contains("正文预览")`，因分词器切分成 Bigram 双字而永远为 false（假绿灯）；
   - 缺失 coverage 写入前与 display 清理前的有效交错拦截断言。

---

## 2. 测试先行（Red Phase）确证与失败日志

在修改产品代码前，重构并扩充了 [`SearchIndexRepositoryRebuildConcurrencyTest.kt`](file:///android/app/src/test/java/com/creationreadingassistant/data/repository/SearchIndexRepositoryRebuildConcurrencyTest.kt)。运行命令：
```bash
cmd /c "gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.data.repository.SearchIndexRepositoryRebuildConcurrencyTest --no-parallel --rerun-tasks"
```
**执行结果**：`15 tests completed, 3 failed`，退出码 `1`。

### 失败用例 1：`old sweep discard display intercepted before lock does not wipe new display rows`
```text
java.lang.AssertionError: 新代次 display 词条必须完好保留
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.assertTrue(Assert.java:42)
	at com.creationreadingassistant.data.repository.SearchIndexRepositoryRebuildConcurrencyTest$old sweep discard display intercepted before lock does not wipe new display rows$1.invokeSuspend(SearchIndexRepositoryRebuildConcurrencyTest.kt:872)
```
*确证*：旧扫描在 display 失败清理前拦截，新代次写入 display 数据后放行，旧任务的 `discardDisplayRows` 破坏了新代次数据。

### 失败用例 2：`wipe all terms on tokenizer mismatch respects generation and does not delete new generation data`
```text
kotlinx.coroutines.test.UncompletedCoroutinesError: After waiting for 1m, the test body did not run to completion
	at app//kotlinx.coroutines.test.TestBuildersKt__TestBuildersKt$runTest$2$1$2.invokeSuspend$lambda$0(TestBuilders.kt:353)
```
*确证*：现有 `wipeAllTermsForAllBooks` 没有任何代次与写前拦截机制，且未受 `runIfCurrentGeneration` 保护。

### 失败用例 3：`sweep acquires ownership but rebuild starts before generation read must not read stale cursor with new gen`
```text
kotlinx.coroutines.test.UncompletedCoroutinesError: After waiting for 1m, the test body did not run to completion
	at app//kotlinx.coroutines.test.TestBuildersKt__TestBuildersKt$runTest$2$1$2.invokeSuspend$lambda$0(TestBuilders.kt:353)
```
*确证*：占权后读代次撕裂，且旧任务在重建重置暂停期间未能通过原子协议立刻感知作废，造成时序死锁与以新代次读旧游标缺陷。

---

## 3. 产品代码修复（Green Phase）设计与实现

在 [`SearchIndexRepository.kt`](file:///android/app/src/main/java/com/creationreadingassistant/data/repository/SearchIndexRepository.kt) 中实现统一原子协议：

### 1. 统一原子协调状态机 `SweepCoordinationState`
彻底废弃原先分离的 `currentGeneration: AtomicLong` 与 `currentSweepJobId: AtomicReference<Long?>`，封装为单一不可变数据类：
```kotlin
private data class SweepCoordinationState(
    val generation: Long,
    val activeJobId: Long?,
    val isRebuilding: Boolean,
)

private val coordinationState = AtomicReference(
    SweepCoordinationState(generation = 1L, activeJobId = null, isRebuilding = false)
)
```

### 2. 占权与代次原子绑定（协议 1）
增量扫描启动时通过 CAS 操作原子完成：未在重建且无活跃扫描时，占用 `myJobId` 并**在同一个 CAS 中锁定代次**：
```kotlin
private fun tryAcquireIncrementalSweep(): Pair<Long, Long>? {
    val myJobId = jobIdSequence.incrementAndGet()
    while (true) {
        val current = coordinationState.get()
        if (current.activeJobId != null || current.isRebuilding) {
            return null
        }
        val next = current.copy(activeJobId = myJobId)
        if (coordinationState.compareAndSet(current, next)) {
            return Pair(myJobId, next.generation)
        }
    }
}
```

### 3. 多重有效性校验与抢占感知（协议 2）
增量扫描在占权后、读取 DB 游标前、执行批处理前，均调用：
```kotlin
private fun isSweepActiveAndValid(myJobId: Long, myGeneration: Long): Boolean {
    val current = coordinationState.get()
    return current.activeJobId == myJobId && current.generation == myGeneration && !current.isRebuilding
}
```
一旦检测到重建接管（`activeJobId != myJobId` 或 `generation != myGeneration` 或 `isRebuilding`），扫描立刻判定自身失效，**安全返回 `PROGRESSED` 退出，绝不读取旧游标，绝不以新代次跳过第 0 本书**。

### 4. 全量重建原子接管与回滚协议（协议 3）
全量重建在 `beginRebuild` 中原子将代次加 1、设置 `activeJobId = rebuildJobId` 并标记 `isRebuilding = true`；
在 `sweepLock.withLock` 内将游标置为 `null`；
若 `stateDao.set` 抛错，在 `catch` 块中调用 `rollbackRebuild(rebuildJobId, oldGen)` 原子回滚代次并清除接管标记；
重建成功后调用 `completeRebuild(rebuildJobId)` 归还执行权。

### 5. 安全释放与防误删协议（协议 4）
`releaseSweepOwnership(myJobId)` 保证旧任务在 `finally` 退出时，仅当当前仍是自己持有时才释放，绝不误将重建任务或新任务的所有权抹除。

### 6. tokenizer 版本清理路径协议（协议 5）
```kotlin
private suspend fun wipeAllTermsForAllBooks(generation: Long?) {
    testHookBeforeWrite?.invoke("wipe_all_terms", "")
    runIfCurrentGeneration(generation) {
        val allTermBooks = distinctBookIdsInTerms()
        for (bid in allTermBooks) termDao.deleteByBook(bid)
        coverageDao.deleteAll()
    }
}
```
调用前触发写前拦截点，并在 `runIfCurrentGeneration(generation)` 保护下执行。若代次已失效，放弃清理，新代次数据完好保留。

---

## 4. 单元测试修正与断言增强

在 [`SearchIndexRepositoryRebuildConcurrencyTest.kt`](file:///android/app/src/test/java/com/creationreadingassistant/data/repository/SearchIndexRepositoryRebuildConcurrencyTest.kt) 中：
1. **替换虚假断言**：在 A2 和 B2 测试中，用真实的 `harness.stateDao.get()` 游标非空、`termDao.rows` 非空、`coverageDao.rows` 非空替换了原有的 `nextResult != null`；
2. **修正旧词条断言**：使用 `SearchTokenizer().tokenize("正文预览内容：书本一")` 实际产生的 Bigram token 列表过滤，断言旧词条集合为空，且精确匹配 `listOf("NEW_GEN_EXCLUSIVE_TERM")`；
3. **新增 4 个确定性交错测试**：
   - `sweep acquires ownership but rebuild starts before generation read must not read stale cursor with new gen`
   - `wipe all terms on tokenizer mismatch respects generation and does not delete new generation data`
   - `old sweep coverage write intercepted before lock does not overwrite new coverage`
   - `old sweep discard display intercepted before lock does not wipe new display rows`

---

## 5. 验证结果与 XML 对账

### 1. 定向并发测试套件（15/15 全绿）
执行命令：
```bash
cmd /c "gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.data.repository.SearchIndexRepositoryRebuildConcurrencyTest --no-parallel --rerun-tasks"
```
**结果**：`BUILD SUCCESSFUL in 1m 51s`，退出码 `0`。

根据 `build/test-results/testDebugUnitTest/TEST-com.creationreadingassistant.data.repository.SearchIndexRepositoryRebuildConcurrencyTest.xml` 报告核对，共 15 个用例，0 失败，0 错误，0 跳过：
1. `pre-scan cancellation propagates and releases sweep ownership` (time: 3.349s)
2. `full rebuild stateDao failure rolls back generation and releases lock` (time: 0.016s)
3. `old sweep finally does not clear new sweep isRunning progress state` (time: 0.021s)
4. `old sweep cannot overwrite reset cursor after full rebuild triggered` (time: 0.017s)
5. `indexSingleBook pre-DAO failure releases single book running count` (time: 0.012s)
6. `old sweep terms write intercepted before lock does not overwrite new generation terms` (time: 0.012s)
7. `old sweep cannot overwrite terms and coverage after new generation rebuild` (time: 0.012s)
8. `pre-scan DAO failure releases sweep ownership and allows subsequent sweep` (time: 0.016s)
9. `wipe all terms on tokenizer mismatch respects generation and does not delete new generation data` (time: 0.012s)
10. `old sweep coverage write intercepted before lock does not overwrite new coverage` (time: 0.012s)
11. `full rebuild reset pause prevents new sweep from reading stale cursor` (time: 0.013s)
12. `old sweep discard display intercepted before lock does not wipe new display rows` (time: 0.025s)
13. `new scan starts from the first book after full rebuild reset` (time: 0.009s)
14. `bad book content DAO failure isolates cleanly and good book indexed` (time: 0.044s)
15. `sweep acquires ownership but rebuild starts before generation read must not read stale cursor with new gen` (time: 0.012s)

### 2. 既有模块单测回归
执行命令：
```bash
cmd /c "gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.feature.search.SearchIndexRepositorySearchTest --tests com.creationreadingassistant.data.repository.SearchIndexBookRefreshCoordinatorTest --tests com.creationreadingassistant.ui.viewmodel.ShelfImporterTest --no-parallel"
```
**结果**：`BUILD SUCCESSFUL in 17s`，退出码 `0`。

### 3. Kotlin 编译检查
执行命令：
```bash
cmd /c "gradlew.bat :app:compileDebugKotlin --no-parallel"
```
**结果**：`BUILD SUCCESSFUL in 2s`，退出码 `0`。

---

## 6. 仍未覆盖的风险边界与纪律声明

### 风险边界说明
1. **多进程/进程死亡边界**：本修复通过 `SweepCoordinationState` 保证了单进程内存状态内代次、所有权与重建接管的绝对原子一致性。若系统在重建中途强行终止应用进程，DB 中由于事务/游标已落盘为 `null`，下次启动冷扫描会从第 0 本书自然重建；
2. **超大书库分表扩展**：`wipeAllTermsForAllBooks` 仍为逐本遍历删除；若书库规模达到上万本，建议在未来规划 TRUNCATE 级底层 SQL 支持。

### 纪律执行核对
- 仅修改授权文件：`SearchIndexRepository.kt`、`SearchIndexRepositoryRebuildConcurrencyTest.kt` 及本报告；
- 未修改任何数据库 schema、版本或 migration；
- 未修改 `SearchIndexWorker.kt`、`SearchIndexScheduler.kt` 等未授权文件；
- 未执行任何 git commit、push、stage、reset、clean、stash；
- 未连接真实手机或模拟器，未运行 ADB。
