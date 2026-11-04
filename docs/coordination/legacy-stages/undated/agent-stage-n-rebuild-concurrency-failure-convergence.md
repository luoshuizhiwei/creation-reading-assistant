# S03-1 搜索索引全量重建并发失败收敛与串行化修复报告

**阶段代号**：`stage-n-rebuild-concurrency-failure-convergence`  
**基准分支**：`codex/recover-worktree-20260920`  
**基准提交**：`5aed5939600e491843e3c44453921bd6273a233c`  
**工作仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`  
**执行日期**：2026-09-23  

---

## 1. 任务背景与收敛目标

在 S03-1 搜索索引全量重建代次协议前几轮迭代中，统一原子状态机已解决了单次重建与增量扫描间的代次撕裂。但在 Codex 三次复核中，指出了最后一个关键边界缺口：
1. **并发重建失败导致占用被误清**：若重建任务 A 已经进入 `beginRebuild` 并推进代次、但尚未完成游标重置（当前仍为旧游标），此时后一个重建任务 B 随后开始并在 `stateDao.set` 抛错或被取消；若未对重建过程串行化且回滚缺少所有权匹配，B 的失败回滚会将 `isRebuilding` 置为 `false`，从而提前释放 A 的占用；此时扫描抢跑并读取旧游标，导致全库重建跳过第一本书；
2. **重建生命周期未串行化**：后一次 `beginRebuild` 容易覆盖尚未完成的前一次，失败回滚时容易清除别的重建任务的所有权；
3. **占权钩子释放保护范围**：`testHookAfterAcquireOwnership` 原置于 `try` 块之外，一旦钩子挂起、抛错或取消，所有权未进入 `finally` 释放流程，导致后续扫描被永久挂起。

本轮目标：
- 严格遵循测试先行，构造无锁双向屏障的失败复现；
- 使每次重建的【开始接管 → 游标重置 → 成功释放或失败恢复】按一致顺序串行化；
- 将 `testHookAfterAcquireOwnership` 移入 `try` 块内，确保抛错与取消均由 `finally` 可靠释放。

---

## 2. 测试先行（Red Phase）确证与失败日志

在 [`SearchIndexRepositoryRebuildConcurrencyTest.kt`](file:///android/app/src/test/java/com/creationreadingassistant/data/repository/SearchIndexRepositoryRebuildConcurrencyTest.kt) 中新增断言 8a、8b、9、10。在未修复前运行测试：
```bash
cmd /c "gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.data.repository.SearchIndexRepositoryRebuildConcurrencyTest --no-parallel --rerun-tasks"
```
**确证结果**：捕获到确定性红灯（退出码 1）：

### 失败日志 1：并发两次重建，后一次失败/取消清除前一次占用
```text
java.lang.AssertionError: 重建 B 失败后不得释放重建 A 的占用，扫描必须被拒绝 expected:<PROGRESSED> but was:<COMPLETED>
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.failNotEquals(Assert.java:835)
	at org.junit.Assert.assertEquals(Assert.java:118)
	at com.creationreadingassistant.data.repository.SearchIndexRepositoryRebuildConcurrencyTest$concurrent rebuilds where second fails cannot release first rebuild ownership and scan cannot read stale cursor$1.invokeSuspend(SearchIndexRepositoryRebuildConcurrencyTest.kt:930)
```
*确证*：重建 B 失败回滚直接重置了 `isRebuilding = false`，导致重建 A 暂停期间新扫描进入，读取旧游标并完成扫描，跳过了 `b1`。

### 失败日志 2：占权钩子抛错未释放所有权
```text
java.lang.AssertionError: 占权后抛错释放所有权后，后续扫描必须能够正常执行
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.assertTrue(Assert.java:42)
	at com.creationreadingassistant.data.repository.SearchIndexRepositoryRebuildConcurrencyTest$hook after acquire ownership failure releases ownership and allows subsequent sweep$1.invokeSuspend(SearchIndexRepositoryRebuildConcurrencyTest.kt:976)
```
*确证*：钩子抛错位于 `try` 块外，未触发 `finally` 块中的 `releaseSweepOwnership`，后续扫描被阻断。

### 失败日志 3：占权钩子协程取消未释放所有权
```text
java.lang.AssertionError: 占权后取消释放所有权后，后续扫描必须能够正常执行
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.assertTrue(Assert.java:42)
	at com.creationreadingassistant.data.repository.SearchIndexRepositoryRebuildConcurrencyTest$hook after acquire ownership cancellation releases ownership and propagates$1.invokeSuspend(SearchIndexRepositoryRebuildConcurrencyTest.kt:1012)
```
*确证*：协程在占权钩子处被取消后，所有权未释放，后续扫描无法启动。

---

## 3. 产品代码修复（Green Phase）

针对上述缺口，在 [`SearchIndexRepository.kt`](file:///android/app/src/main/java/com/creationreadingassistant/data/repository/SearchIndexRepository.kt) 进行如下加固：

### 1. 全量重建生命周期串行化互斥锁 `rebuildLock`
新增 `rebuildLock = Mutex()`，将 `invalidateSweepForFullRebuild()` 的整个接管、重置与收尾生命周期完整受控在 `rebuildLock.withLock` 内：
```kotlin
private val rebuildLock = Mutex()

suspend fun invalidateSweepForFullRebuild() {
    rebuildLock.withLock {
        val rebuildJobId = jobIdSequence.incrementAndGet()
        // 接管全库执行权与推进代次，阻止任何新扫描在重置完成前启动并读取旧游标
        val oldGen = beginRebuild(rebuildJobId)
        try {
            testHookBeforeRebuildReset?.invoke(rebuildJobId)
            sweepLock.withLock {
                stateDao.set(
                    SearchIndexStateRow(
                        tokenizer_version = TOKENIZER_VERSION,
                        last_scanned_book_id = null,
                        last_scanned_chapter_index = 0,
                        built_at = 0,
                    ),
                )
            }
        } catch (e: Throwable) {
            // 失败恢复策略：回滚代次并释放接管锁，防止系统进入「新代次 + 旧游标」腐化状态
            rollbackRebuild(rebuildJobId, oldGen)
            updateProgressRunningState()
            throw e
        }
        // 重置完成，释放执行权供后续新扫描使用
        completeRebuild(rebuildJobId)
        updateProgressRunningState()
    }
}
```
**技术效果**：
- 任何后续重建调用必须等待前一个重建执行完毕（无论是成功释放还是失败回滚），绝不可能出现后一个重建覆盖前一个重建 `activeJobId` 的现象；
- 彻底避免测试与运行时代次混乱和状态重置时序撕裂。

### 2. 回滚与完成的持有权严格匹配守卫
在 `rollbackRebuild` 与 `completeRebuild` 中均加入原子 CAS 与持有权匹配检查：
```kotlin
private fun rollbackRebuild(rebuildJobId: Long, rollbackGen: Long) {
    while (true) {
        val current = coordinationState.get()
        if (current.activeJobId == rebuildJobId) {
            val next = SweepCoordinationState(
                generation = rollbackGen,
                activeJobId = null,
                isRebuilding = false,
            )
            if (coordinationState.compareAndSet(current, next)) {
                return
            }
        } else {
            return
        }
    }
}
```
仅当 `current.activeJobId == rebuildJobId` 时才允许重置 `isRebuilding`，双重防止跨任务误释放。

### 3. 将 `testHookAfterAcquireOwnership` 移入 `try` 块内
```kotlin
val acquired = tryAcquireIncrementalSweep() ?: return IndexSweepResult.PROGRESSED
val (myJobId, myGeneration) = acquired

return try {
    testHookAfterAcquireOwnership?.invoke()
    if (!isSweepActiveAndValid(myJobId, myGeneration)) {
        return IndexSweepResult.PROGRESSED
    }
    // ... 扫描逻辑 ...
} finally {
    releaseSweepOwnership(myJobId)
    updateProgressRunningState()
}
```
确保无论钩子发生普通异常、系统错误还是协程取消，执行权与运行状态必定在 `finally` 中被安全归还。

---

## 4. 验证与测试结果

### 1. 定向并发测试（19/19 绿灯）
执行命令：
```bash
cmd /c "gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.data.repository.SearchIndexRepositoryRebuildConcurrencyTest --no-parallel --rerun-tasks"
```
测试报告 XML（`TEST-com.creationreadingassistant.data.repository.SearchIndexRepositoryRebuildConcurrencyTest.xml`）：
```xml
<testsuite name="com.creationreadingassistant.data.repository.SearchIndexRepositoryRebuildConcurrencyTest" tests="19" skipped="0" failures="0" errors="0" timestamp="2026-09-23T06:12:30" hostname="LUOSHUIZHIWEI" time="6.403">
```
所有 19 个并发用例全数通过，无死锁、无超时（总耗时 6.4 秒）：
1. `concurrent rebuilds where second is cancelled cannot release first rebuild ownership and scan cannot read stale cursor` (0.019s)
2. `concurrent rebuilds where second fails stateDao set rolls back cleanly and leaves system consistent` (0.033s)
3. `hook after acquire ownership failure releases ownership and allows subsequent sweep` (0.014s)
4. `hook after acquire ownership cancellation releases ownership and propagates` (5.935s)
5. `full rebuild reset pause prevents new sweep from reading stale cursor` (0.017s)
6. `full rebuild stateDao failure rolls back generation and releases lock` (0.022s)
7. `old sweep terms write intercepted before lock does not overwrite new generation terms` (0.024s)
8. `old sweep coverage write intercepted before lock does not overwrite new coverage` (0.014s)
9. `old sweep discard display intercepted before lock does not wipe new display rows` (0.060s)
10. `old sweep cannot overwrite terms and coverage after new generation rebuild` (0.019s)
11. `old sweep cannot overwrite reset cursor after full rebuild triggered` (0.017s)
12. `old sweep finally does not clear new sweep isRunning progress state` (0.028s)
13. `new scan starts from the first book after full rebuild reset` (0.012s)
14. `pre-scan cancellation propagates and releases sweep ownership` (0.059s)
15. `pre-scan DAO failure releases sweep ownership and allows subsequent sweep` (0.020s)
16. `indexSingleBook pre-DAO failure releases single book running count` (0.016s)
17. `sweep acquires ownership but rebuild starts before generation read must not read stale cursor with new gen` (0.014s)
18. `wipe all terms on tokenizer mismatch respects generation and does not delete new generation data` (0.017s)
19. `bad book content DAO failure isolates cleanly and good book indexed` (0.060s)

### 2. 既有模块回归测试
- `ShelfImporterTest`：**32/32 通过**；
- `SearchIndexBookRefreshCoordinatorTest`：**6/6 通过**；
- `SearchIndexRepositorySearchTest`：**通过**。

### 3. Kotlin 编译检查
```bash
cmd /c "gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --no-parallel"
```
**结果**：`BUILD SUCCESSFUL in 4s`，22 actionable tasks up-to-date。

---

## 5. 文件变更清单（严格遵守所有权）

本轮仅修改授权文件：
1. `android/app/src/main/java/com/creationreadingassistant/data/repository/SearchIndexRepository.kt`
   - 增加 `rebuildLock` 并对 `invalidateSweepForFullRebuild` 加互斥保护；
   - 将 `testHookAfterAcquireOwnership?.invoke()` 移入 `try/finally` 块内；
2. `android/app/src/test/java/com/creationreadingassistant/data/repository/SearchIndexRepositoryRebuildConcurrencyTest.kt`
   - 新增断言 8a（取消不影响占用）、8b（失败回滚不破坏前序成果）、9（抛错释放）、10（取消释放）；
3. `docs/coordination/legacy-stages/undated/agent-stage-n-rebuild-concurrency-failure-convergence.md`（本报告）。

未修改任何 DAO、Schema、数据库版本、Worker、Scheduler、桌面端代码或原始仓库。
