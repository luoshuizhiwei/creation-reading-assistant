# 搜索索引完整性修复（窗口 B）：C01 接口恢复 + coverage-only 孤儿回收

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 日期：2026-09-23
- 范围：仅搜索索引的 C01 接口恢复与 coverage-only 孤儿回收

---

## 1. 开始 / 结束时的分支、HEAD 与 git status

| 项 | 开始时 | 结束时 |
|---|---|---|
| 分支 | `codex/recover-worktree-20260920` | `codex/recover-worktree-20260920`（未变） |
| HEAD | `5aed5939600e491843e3c44453921bd6273a233c` | `5aed5939600e491843e3c44453921bd6273a233c`（未变） |
| `git status --short` 条目数 | 388 | 388 |

**未执行**：reset / checkout / clean / stash / stage / commit / push；未全仓格式化；
未回退或覆盖其他窗口的修改；未进入或修改原始仓库
`D:\develop\Code\Codex\creation-reading-assistant`。

三个授权文件在开始前即为 ` M`（工作区既有改动），本轮在其上追加本任务的改动。

---

## 2. 实际修改的文件

| 文件 | 归属 | 改动 |
|---|---|---|
| `data/local/dao/SearchTermDao.kt` | 授权 | `SearchIndexCoverageDao` 内：恢复 `rowsByStates`、`countByStates`；新增 `distinctBookIds()` |
| `data/repository/SearchIndexRepository.kt` | 授权 | 恢复 `incompleteBookIds()`；重写 `purgeOrphanIndexRows()` 候选集合 |
| `feature/search/SearchIndexRepositorySearchTest.kt` | 授权 | 测试假实现补 `rowsByStates` / `countByStates` / `distinctBookIds`；新增 5 条用例 |
| `docs/coordination/legacy-stages/undated/agent-stage-d-search-index-integrity-repair.md` | 授权（本报告） | 新建 |

**仅以上 4 个。** 无 SEAM REQUEST —— 本任务无需修改授权列表外的任何产品文件。

---

## 3. 任务一：两项 C01 接口如何按 HEAD 精确恢复

恢复前先用只读命令取 HEAD 原文
（`git show HEAD:…SearchTermDao.kt` / `…SearchIndexRepository.kt`），
**逐字照搬 HEAD 的签名、SQL、语义，未重新设计**。

### 3.1 `SearchIndexCoverageDao.rowsByStates`

```kotlin
@Query("SELECT * FROM search_index_coverage WHERE coverage IN (:states)")
suspend fun rowsByStates(states: List<String>): List<SearchIndexCoverageRow>
```

SQL、返回类型、参数名与 HEAD 完全一致（`SELECT *`、`WHERE coverage IN (:states)`、无 ORDER BY）。

同时恢复了 HEAD 中与它相邻、且在查询侧被一并删除的
`countByStates(states: List<String>): Long`
（`SELECT COUNT(*) FROM search_index_coverage WHERE coverage IN (:states)`）——
它是 `rowsByStates` 的同族接口，HEAD 中二者相邻定义，一并恢复以免留下半截接口面。

### 3.2 `SearchIndexRepository.incompleteBookIds`

完整照搬 HEAD（含 KDoc 与默认参数）：

```kotlin
suspend fun incompleteBookIds(
    basis: SearchTextBasis = SearchTextBasis.ORIGINAL,
): List<String> {
    val incompleteStates = SearchCoverageState.entries
        .filter { SearchCoveragePolicy.isIncomplete(it) }
        .map { it.wire }
    return coverageDao.rowsByStates(incompleteStates)
        .filter { it.text_basis == basis.wire }
        .map { it.book_id }
        .distinct()
        .sorted()
}
```

保留的 HEAD 原语义：

- 默认基准 `ORIGINAL`（HEAD KDoc 的理由：display 只对有生效替换规则的书产生行，
  其余记 `NOT_APPLICABLE`，由 `SearchCoveragePolicy.isIncomplete` 排除）；
- 未完成状态集由 `SearchCoveragePolicy.isIncomplete` 过滤
  （= `state != FULL && state != NOT_APPLICABLE`），非硬编码列表；
- **先按 `basis.wire` 过滤基准，再 `distinct()`，再 `sorted()`** —— 顺序与 HEAD 一致。

`SearchTextBasis` / `SearchCoverageState` / `SearchCoveragePolicy` 在本文件 HEAD 与工作树中
均已 import，无需新增 import。

### 3.3 测试假实现的同步

`SearchIndexCoverageDao` 在产品侧只有 Room 生成实现，测试侧只有
`SearchIndexRepositorySearchTest.FakeSearchIndexCoverageDao` 一个手写实现。
按 HEAD 原文补回：

```kotlin
override suspend fun rowsByStates(states: List<String>): List<SearchIndexCoverageRow> =
    rows.filter { it.coverage in states }
override suspend fun countByStates(states: List<String>): Long = rowsByStates(states).size.toLong()
```

（与 HEAD 测试文件第 64–66 行一致。）

> 附注（未越界处理）：本次 diff 显示工作树相对 HEAD 还删除了
> `deleteByChapter`、`countTerms`、`rowsByTerm`。经全仓 grep 确认**当前工作树无任何调用方**，
> 且不在本轮授权恢复范围，故**未恢复、未触碰**，仅在此登记供后续窗口判断。

---

## 4. 任务二：coverage-only 孤儿的原始根因

### 根因

修改前的候选集合只来自 `search_terms` 一个源头：

```kotlin
val indexed = termDao.distinctBookIds()   // ← 只有 search_terms
```

`purgeOrphanIndexRows()` 的语义是「回收已删书的索引残留」，而残留可能落在
**两张**表：`search_terms` 与 `search_index_coverage`。

若某本已删书的 `search_terms` 行已清空（例如按基准精确清理
`deleteByBookAndBasis` 逐基准删完，或索引重建时 term 侧先被清），
而 `search_index_coverage` 行还在，则：

1. 它的 `book_id` **不出现在** `termDao.distinctBookIds()` 的结果里；
2. ⇒ 它**不进入候选集合**；
3. ⇒ `purgeOrphanIndexRows()` **永远看不见它**；
4. ⇒ 孤儿 `coverage` 行**永久残留**。

**连带后果**：`countIndexedBooksCheap()` 数的是 `search_index_coverage` 里
original 基准的行数（见测试文件中既有注释），孤儿 coverage 会让
「已索引书籍数量」**统计虚高**——书已删除，却仍被计入已索引本数。

### 修复后的候选集合与去重逻辑

```kotlin
internal suspend fun purgeOrphanIndexRows(): Int {
    // 候选必须是两张表 book_id 的**并集**：只扫 search_terms 会漏掉「term 已空、
    // 只剩 coverage 行」的已删书，那类孤儿 coverage 会永久残留。
    val indexed = (termDao.distinctBookIds() + coverageDao.distinctBookIds()).toHashSet()
    if (indexed.isEmpty()) return 0
    val active = bookDao.activeIds().toHashSet()
    val orphans = indexed.filterNot { it in active }
    if (orphans.isEmpty()) return 0
    for (bookId in orphans) {
        termDao.deleteByBook(bookId)
        coverageDao.deleteByBook(bookId)
    }
    AppLog.i(...)   // 未改
    return orphans.size
}
```

逐条对应要求：

| # | 要求 | 落实 |
|---|---|---|
| 1 | 为 `SearchIndexCoverageDao` 增加只读 distinct book ID 查询 | 新增 `@Query("SELECT DISTINCT book_id FROM search_index_coverage") suspend fun distinctBookIds(): List<String>`；**只读 SELECT**，不改 schema |
| 2 | 候选 = 两表 distinct book ID 的去重并集 | `(termDao.distinctBookIds() + coverageDao.distinctBookIds()).toHashSet()`，`HashSet` 完成去重 |
| 3 | 只清理不在活跃集合中的 book ID | `indexed.filterNot { it in active }`，`active = bookDao.activeIds().toHashSet()`（沿用原判据） |
| 4 | 对每个孤儿继续同时清理 term 和 coverage | 循环内 `termDao.deleteByBook` + `coverageDao.deleteByBook`（原逻辑，未改） |
| 5 | 活跃书即使只有 coverage、无 term 也不得误删 | 它会出现在候选里（这正是修复的意义），但被 `filterNot { it in active }` 排除 ⇒ 保留 |
| 6 | 保持返回值含义 | 仍返回 `orphans.size`（**书籍数**）；KDoc 已明确写成「实际回收的**书籍数**（不是删除的数据库行数）」 |
| 7 | 不改 schema / 不新增 migration / 不改版本 | 已核实 `APP_DATABASE_SCHEMA_VERSION = 15` 未变，未新增任何 `Migration`；`AppDatabase.kt` / `DatabaseModule.kt` / `schemas/**` **本轮未触碰**（它们在工作区的既有 `M` 状态来自其他窗口，非本轮改动） |

**顺带未做**（按要求 8）：来源更新、全文重建、备份、UI 及其他搜索问题一律未触碰。

---

## 5. 新增 / 修改的测试

`SearchIndexRepositorySearchTest` 由 22 条增至 **27 条**，新增 5 条：

| # | 测试名 | 覆盖点 |
|---|---|---|
| 1 | `purgeOrphanIndexRows reclaims a coverage-only orphan book` | 已删书只剩 coverage、无 term：purge 后 coverage 被删；返回值 `1`（按「本」计） |
| 2 | `purgeOrphanIndexRows keeps a coverage-only book that is still active` | 活跃书只有 coverage、无 term：purge 后仍保留；返回值 `0`（不含它） |
| 3 | `purgeOrphanIndexRows counts a book with both term and coverage rows once` | 同一孤儿书同时有 term + coverage：只计 **1** 本；两类行全删（term 2 行 + coverage 2 行） |
| 4 | `incompleteBookIds returns only incomplete states on the requested basis` | 恢复的 `rowsByStates` 语义：`FULL` / `NOT_APPLICABLE` 排除；默认只看 original；结果 `sorted()` |
| 5 | `incompleteBookIds filters by basis and returns each book once sorted` | 按基准过滤 + `distinct()` + 排序；display 基准下两本都命中且各只出现一次 |

要求中的第 4 项「所有索引书都活跃 → 返回 0、term 与 coverage 均不变」由**既有**
`purgeOrphanIndexRows is a no-op when every indexed book is active` 覆盖，本轮未重复添加。

### 过程中修正的一处自身错误

第 5 条初稿断言 `original` 基准下 `b2`（`PREVIEW_ONLY`）不算未完成 —— **断言写错**：
`isIncomplete` 的定义是 `!= FULL && != NOT_APPLICABLE`，`PREVIEW_ONLY` **属于**未完成。
已按真实语义修正夹具（把 `b2` 的 original 改为 `FULL`、display 改为 `PARTIAL`），
改正后测试通过。这是**测试期望**的错误，非产品代码问题，未为此改动任何产品文件。

---

## 6. 验证命令、退出码与测试数量

在 `android` 目录串行执行：

### ①

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.search.SearchIndexRepositorySearchTest" --no-parallel
```

- **退出码：`0`**（`BUILD SUCCESSFUL in 24s`）
- **测试数量：27**（`tests="27"`，`skipped="0"`、`failures="0"`、`errors="0"`，
  取自 `app/build/test-results/testDebugUnitTest/TEST-…SearchIndexRepositorySearchTest.xml`）
- 中途一次失败（1 failed / 27 completed）由上述**自身测试期望错误**造成，
  已在授权文件内修正后重跑通过。

### ②

```bash
.\gradlew.bat :app:compileDebugKotlin --no-parallel
```

- **退出码：`0`**（`BUILD SUCCESSFUL in 4s`）

**未运行**真机测试、未运行 ADB、未安装 APK、未跑 `assemble`、未跑 `lint`。

---

## 7. 是否出现范围外文件变化

**没有。** 本轮只写入第 2 节列出的 4 个文件。

按 mtime 复核（2026-09-23 内被修改的 `src` 文件）恰好只有授权的 3 个代码文件；
`docs/coordination/decisions/2026-09/2026-09-22-current-task-ledger.md` 的 mtime 变化来自**其他窗口**
（本轮对其 `git diff` 为空，未写入）。

明确未触碰：数据库 schema / 版本 / migration（`AppDatabase.kt`、`DatabaseModule.kt`、
`schemas/**` 本轮零改动，其工作区既有 `M` 状态属其他窗口）；`BookRepository`、
`SearchIndexScheduler`、`BookDao`；UI、备份、来源更新、全文重建。

---

## 8. 未覆盖事项

- **真机未验证。** 全部结论来自 JVM 单元测试与 Kotlin 编译；
  未验证真实 Room 生成的 `SearchIndexCoverageDao` 实现（测试走的是手写假 DAO），
  也未在设备上验证孤儿回收的实际效果与「已索引本数」是否回落。
- **新增 DAO 方法仅经 KSP 编译校验**：`distinctBookIds()` 的 SQL 由 Room 在编译期解析
  （`compileDebugKotlin` 通过，KSP 未报错），但**未在真机上执行过该查询**。
- **`incompleteBookIds` 的调用方未追踪**：本轮只恢复接口本体与语义，
  未排查其上游调用链是否完整（工作树中未见调用方）。
- **未做变异测试**：未验证「把候选集合改回只扫 term 表」时新增用例确实会失败。
  保护强度为静态判断 + 用例断言。
- **`countIndexedBooksCheap` 虚高现象未实测复现**：孤儿 coverage 导致统计虚高是
  代码阅读得出的结论（该函数数 coverage 表 original 基准行数），未经真机计数验证。
- **未恢复的其余删除项**：`deleteByChapter` / `countTerms` / `rowsByTerm`
  仍处删除状态（见 §3.3 附注），当前无调用方，不在本轮范围。

---

## 9. 边界声明

- **未** commit、push、stage、reset、checkout、clean、stash。
- **未**进行真机验收；**未**使用 MuMu 模拟器；**未**操作手机或运行 ADB。
- **未**进入或修改原始仓库 `D:\develop\Code\Codex\creation-reading-assistant`。
- **未**进行全仓格式化；**未**回退或覆盖其他窗口的修改。
- 无 SEAM REQUEST：本任务无需修改授权列表外的产品文件。

完成后停止，不领取下一任务。
