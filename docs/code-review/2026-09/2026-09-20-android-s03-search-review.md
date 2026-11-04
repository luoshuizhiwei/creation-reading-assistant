# Android S03 搜索与索引切片审查

> 日期：2026-09-20  
> 固定基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`  
> 审查对象：恢复分支 `codex/recover-worktree-20260920` 的未提交工作区差异  
> 比较命令：`git diff origin/main -- <S03 paths>`（未提交改动不能使用只比较提交的三点式）  
> 性质：只读审查；未修改产品源码，未 stage、commit 或 push

## 规格来源

- `docs/handoff/current.md` 中 R2 搜索双通道、索引续建、规则变更重建与真机证据；
- `docs/architecture/native-android-reader.md` 中搜索坐标、全文索引与大 TXT 边界；
- `docs/plans/android-parallel-delivery-2026-09-09/reports/workbuddy-r2-s1-7-index-sweep-fix.md`；
- `docs/plans/android-parallel-delivery-2026-09-09/reports/workbuddy-r2-s1-9-p1-rule-reindex-and-checkpoint.md`；
- 当前产品盘点 `docs/research/2026-09-19-android-worktree-product-inventory.md`。

## 精确切片边界

S03 搜索数据正确性候选包括：

- `BookDao.kt` 的 `activeIds()`；
- `SearchIndexRepository.kt` 的活跃书过滤、孤儿索引回收、单书删除/恢复索引、自助重建入口；
- `SearchIndexScheduler.kt` 的显式 `triggerNow(force)` 契约；
- `BookRepository.kt` 的删除清索引和撤销后恢复索引 hunk；
- `SearchHitRanker.kt`；
- `TxtChapterSplitter.kt`；
- `ReaderSearchIndexCoordinator.kt`；
- 对应 repository / ViewModel / reader JVM 测试。

不得夹带：

- `SearchTermDao.kt` 删除 term 索引的 entity 声明属于 S02 Room v15；
- `BookRepository.markFinished()` 属于 S05 书架状态；
- `SearchScreen.kt` 抽共享卡片与 `contentType`、`SearchResultComponents.kt` 属于 S17 公共 UI / 丢失本地提交重建；
- `TxtFileScanner.fastEncodedByteLength()` 属于阅读器扫描性能切片，不是搜索业务；
- 设置搜索、来源更新、Legado 进度导入分别属于 S13、S04、S12。

## Standards

### [P1] `BookRepository.kt` 是跨切片共享文件，不能整文件进入 S03

同一 diff 同时包含搜索索引删除/恢复与 `markFinished()` 书架状态行为。若按文件暂存，会把 S05 一并带入，破坏可归因测试和回滚边界。S03 必须只取构造注入、`applyDeletionLocked`、`restoreDeletions` 的搜索相关 hunk；`markFinished()` 留给 S05。

证据：`android/app/src/main/java/com/creationreadingassistant/data/repository/BookRepository.kt:358`。

### [P2] 两个新纯算法组件没有独立契约测试

`SearchHitRanker` 和 `TxtChapterSplitter` 被从仓储中抽出，但没有同名或直接测试类。当前仓储测试间接覆盖部分排名行为，却没有独立钉死 `limit <= 0`、coverage provider、稳定排序、无章节回退、40,000 字截断、偏移边界及异常 `maxCharsPerChapter`。这降低了后续继续拆分仓储时的安全性。

证据：`SearchHitRanker.kt:14`、`TxtChapterSplitter.kt:27`，测试树中无直接引用测试。

## Spec

### [P1] 全库置脏仍存在“旧 worker 在重置后回写游标”的竞态

全局规则变更路径先执行 `invalidateSweepForFullRebuild()`，再调用 `triggerNow(force = true)` 以 REPLACE 旧 work。若旧 worker 正在运行，它可能在游标重置后、取消真正生效前把较后的 `last_scanned_book_id` 写回；新 worker 随后从该位置继续，前面的书仍保留旧规则索引。当前测试只验证传了 `force = true`，没有并发 worker / generation 级证据。

证据：`ReaderSearchIndexCoordinator.kt:95-96`；同样顺序也出现在 `SearchIndexRepository.kt:383,393`。

建议在进入候选提交前引入可判旧的 rebuild generation，或用可等待的取消/串行协议保证“旧 worker 已停止 → 重置游标 → 启动新 worker”。

### [P1] 孤儿清理会漏掉“只有 coverage、没有 term”的已删书

`purgeOrphanIndexRows()` 仅用 `termDao.distinctBookIds()` 枚举候选，再同时删除 term 与 coverage。合法状态中可以存在 coverage 行但没有 token 行，例如空正文、无可分词元数据或失败/不适用覆盖状态；这类已删书不会进入 `indexed`，残留 coverage 仍会让覆盖统计虚高。现有测试的每个孤儿都同时有 term，因此没有覆盖该场景。

证据：`SearchIndexRepository.kt:415-424`。

建议候选集合取 `term bookIds ∪ coverage bookIds`，并补 coverage-only orphan 回归。

### [P2] 撤销删除会同步等待整本索引重建

`restoreDeletions()` 在数据库恢复完成后，对每本恢复成功的书执行 `withContext(Dispatchers.IO) { reindexBookById(...) }` 并等待结束。它虽然不占主线程，但调用撤销的协程在大 TXT/EPUB 建完前不会返回，撤销凭证也不会被消费；大书可能造成明显等待。规则变更路径已经采用应用级异步调度，这里的行为不一致。

证据：`BookRepository.kt:480-499`，核心等待位于 `:491`。

建议把“书籍恢复成功”与“索引后台自愈”拆开：事务完成后立即返回恢复结果，再以应用级作用域或 WorkManager 触发单书重建，并提供失败可观察状态。

## 已确认的正向结果

- 搜索结果在分组与 `limit` 前过滤非活跃书，避免孤儿高分行占掉有效名额；
- 删除书同时删除 term 和 coverage，撤销成功后会尝试恢复索引；
- `triggerNow(force)` 取消默认参数，减少调用方无意使用 KEEP 的概率；
- `TxtFileScanner.fastEncodedByteLength()` 的多编码定向测试包含 ASCII、中文、全角标点、补充平面汉字和 emoji；
- 主源码、Hilt 注入和测试源码实际编译通过。

## 验证证据

首次运行因新 clone 缺少被 Git 忽略的 `android/local.properties` 而在任务解析前失败；随后只复制旧目录的 `sdk.dir` 本地配置，新文件仍被 Git 忽略。

重跑命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.creationreadingassistant.feature.search.SearchIndexRepositorySearchTest" `
  --tests "com.creationreadingassistant.data.repository.BookRepositorySearchIndexTest" `
  --tests "com.creationreadingassistant.feature.reader.doc.TxtFileScannerTest" `
  --tests "com.creationreadingassistant.ui.viewmodel.ProfileViewModelTest" `
  --tests "com.creationreadingassistant.ui.viewmodel.ReaderViewModelTest"
```

结果：`BUILD SUCCESSFUL`，5 个测试类共 126 tests，0 failures、0 errors、0 skipped。该结果是混合恢复工作区的开发门禁，不是独立切片证明，也不替代 WorkBuddy 真机验收。

汇总：Standards 2 项（最严重 P1：共享文件跨切片）；Spec 3 项（最严重 P1：全库重建竞态与 coverage-only 孤儿遗漏）。
