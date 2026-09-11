# R2-S1.1 索引覆盖状态模型 + Room v11→v12 迁移

- 执行方：WorkBuddy（原生端接管）
- 日期：2026-09-11
- 状态：**Dev-verified 通过**（编译 + 1914 JVM 测试全绿）；**迁移的 instrumentation 验证降级为 TODO**（本机设备 offline，交 CI）
- 范围：仅 `android/**` + 本报告。未 commit / push / tag。

---

## 1. 背景与需求来源

README R2-S1 原文要求：

> TXT/EPUB/Markdown 分别记录索引覆盖，部分索引不是全文完成；可取消、续建、重试；
> 命中有书籍/章节/上下文/source位置。搜索原文与替换显示文的口径必须明确。

已知问题（README 同段）：

> 全局搜索主要消费元数据与正文预览；索引仓储不能据类名推断全覆盖。

R2 退出条件：**搜到内容可精确到达并返回**。

### 已确认的前置决策（本片之前与用户确认）

| 决策点 | 结论 |
|---|---|
| 搜索口径（文本基准） | **两者都搜并标注来源** —— 原文与替换显示文都建索引、都参与搜索，命中标注来源基准 |
| S1 起始片 | **① 覆盖状态模型优先** |

### 侦察结论（本片实施前的现状核查）

| 项 | 现状 |
|---|---|
| `search_terms` 主键 | `(term, book_id, chapter_index)` —— **无文本基准** |
| `search_index_state` | 单行「扫描游标」（tokenizer 版本 + 上次扫到的 book_id），**不是覆盖率** |
| 覆盖率持久化 | **不存在**。`indexOneBook` 有 4 条路径（元数据 / TXT 逐章 / EPUB 逐章 / preview 降级），降级路径对「本次到底覆盖了什么」**什么都不写** |
| `ContentHit` | 只有 `bookId / score / previewFirstOffset`，**无章节、无 source 位置**；`previewFirstOffset` 是预览串内偏移，非全书偏移 |
| 全文检索查询侧 | `SearchIndexRepository.searchContent` **零生产调用方**（死 seam） |
| `offsets` 列 | 写入侧一直在写，查询侧从未读过 |
| JVM 测试覆盖 | SearchIndexRepository / SearchTermDao 均为 **0** |

---

## 2. 本片做了什么

**核心：把「索引覆盖了多少」从「靠类名/靠 format 推断」改成「显式持久化的一等数据」，并把「双文本基准」固化进 schema。**

### 2.1 新增纯函数覆盖模型（可 JVM 单测）

`android/app/src/main/java/com/creationreadingassistant/feature/search/SearchCoverage.kt`（**新增**）

- `SearchTextBasis`：`ORIGINAL("original")` / `DISPLAY("display")`
- `SearchCoverageState`：`FULL` / `PARTIAL` / `PREVIEW_ONLY` / `METADATA_ONLY` / `FAILED` / `PENDING`
- `SearchCoverageReason`：稳定原因码（`file_too_large` / `epub_parse_failed` / `chapter_truncated` / `chapters_skipped` / `preview_only` / `display_channel_not_built` / …）
- `SearchCoveragePolicy.forChapterIndex(indexedChapters, totalChapters, truncated)`：
  - 无任何可纳入章节 → `METADATA_ONLY`
  - 有截断 **或** 有章节未贡献正文 → `PARTIAL`
  - 其余 → `FULL`

> wire 字符串一经落盘即冻结，改值等于全量索引失效 —— 文件内已写明约束，并用测试钉死。

### 2.2 Schema 升级（Room 11 → 12）

`AppDatabase.kt`：`APP_DATABASE_SCHEMA_VERSION = 12`，新增 `MIGRATION_11_12`。

两步，**只动派生索引表**，不碰 books / reading_progress / highlights 等任何用户内容表：

1. **`search_terms` 主键补 `text_basis`** —— SQLite 无法 ALTER 主键，整表重建：
   建新表 → `INSERT ... SELECT ... , 'original', ...` 原样搬迁 → 删旧表 → 改名 → 重建
   `index_search_terms_term` / `index_search_terms_book`。
   既有 v11 行一律归入 `original`（v11 索引的本来就是原文）。
2. **新增 `search_index_coverage`**，主键 `(book_id, text_basis)`：
   `book_id / text_basis / format / coverage / indexed_chapters / total_chapters /
   tokenizer_version / indexed_at / reason`，索引 `index_search_index_coverage_coverage`。

> 迁移 SQL 里显式写死 `'original'` 字面量，**不引用** wire 常量 —— 迁移是冻结的历史，
> 其行为不能因将来常量改名而改变。

`DatabaseModule.kt`：注册 `MIGRATION_11_12` + `provideSearchIndexCoverageDao`。

### 2.3 仓储写入真实覆盖结果

`SearchIndexRepository.kt`

- 注入 `SearchIndexCoverageDao`；`indexOneBook` 开头同步 `deleteByBook`（term + coverage），结尾写两行覆盖：
  - **原文基准**：按本次实际走通的路径记录真实状态与原因
  - **显示文基准**：当前恒为 `PENDING` + 原因 `display_channel_not_built`
- 三条正文路径改为显式上报 `ChapterIndexRun(indexedChapters, totalChapters, truncated)`，
  不再用布尔量 `chapterIndexedOk` 一黑到底：
  - TXT：`TxtChapterSlice` 增加 `truncated`（`bodyRaw.length > MAX_CHARS_PER_CHAPTER`）
  - EPUB：同样统计 `indexedChapters / totalChapters / anyTruncated`
  - 逐章路径不可用时，区分 `FILE_TOO_LARGE` / `TXT_DECODE_FAILED` / `EPUB_FILE_MISSING` /
    `EPUB_PARSE_FAILED` / `EPUB_NO_CHAPTERS` / `UNSUPPORTED_FORMAT` / `NO_BODY_SOURCE`
- 覆盖率写入点移到 `pending.isEmpty()` 早退**之前**：即使一本书连元数据都没分词，也要留下「它是什么状态」的记录
- `wipeAllTermsForAllBooks()` 一并 `coverageDao.deleteAll()` —— 只清 terms 不清 coverage 会让「重建未完成索引」误判
- 新增读侧入口（供后续片与 UI）：`coverageOf(bookId)`、`coverageCountsByState()`、`incompleteBookIds(basis = ORIGINAL)`

#### 顺带修掉一个历史隐患（非功能变更）

`SearchTermRow` 由 5 列变 6 列，暴露出原批量 upsert 批次 **200 行 × 5 列 = 1000 个绑定参数**，
已越过旧版 SQLite 的 `SQLITE_MAX_VARIABLE_NUMBER = 999`（Android 11 / API 30，SQLite 3.28）。
**原值本就已经越界**，只是此前只在新版 SQLite（上限 32766）的设备上跑过而没暴露。
本次收紧为 `UPSERT_BATCH_ROWS = 150`（150 × 6 = 900），避免 API 30 上 `too many SQL variables`。

---

## 3. 行为不变性说明

`indexOneBook` 的**索引内容行为与改动前保持一致**，这是有意控制的：

- 逐章路径的进入条件仍是 `isTxt && size in 1..20MB && local_content_path 非空`；
  新增的 `else if (isTxt)` 分支**只写覆盖率原因码**，不改变走哪条路径、不新增/不减少任何索引行
- 旧逻辑「TXT 解码成功即认为逐章成立、不再降级 preview」继续保留
- EPUB 的「标题与正文皆空则跳过该章」继续保留
- 唯一的实质行为差异是：**覆盖率落库**（新增）和 **upsert 批次从 200 降到 150**（不影响结果，只影响分片边界）

---

## 4. 验证结果（真实计数，非 Gradle 摘要）

| 项目 | 命令 | 结果 |
|---|---|---|
| 主源编译 | `:app:compileDebugKotlin --no-daemon` | **EXIT=0** |
| instrumentation 测试源编译 | `:app:compileDebugAndroidTestKotlin --no-daemon` | **EXIT=0** |
| 新增 JVM 测试 | `:app:testDebugUnitTest --tests "…SearchCoveragePolicyTest" --rerun-tasks` | **tests=10, failures=0, errors=0, skipped=0**（读 JUnit XML） |
| 全量 JVM 测试 | `:app:testDebugUnitTest --no-daemon` | **229 个 suite / tests=1914, failures=0, errors=0, skipped=0** |

### v12 schema 与手写迁移 SQL 的一致性（关键）

KSP 导出的 `android/app/schemas/.../12.json` 与 `MIGRATION_11_12` 手写的 DDL **逐字一致**：

```
search_terms:
  CREATE TABLE IF NOT EXISTS `search_terms` (
    `term` TEXT NOT NULL, `book_id` TEXT NOT NULL, `chapter_index` INTEGER NOT NULL,
    `text_basis` TEXT NOT NULL, `hits` INTEGER NOT NULL DEFAULT 0, `offsets` TEXT,
    PRIMARY KEY(`term`, `book_id`, `chapter_index`, `text_basis`))
search_index_coverage:
  CREATE TABLE IF NOT EXISTS `search_index_coverage` (
    `book_id` TEXT NOT NULL, `text_basis` TEXT NOT NULL, `format` TEXT NOT NULL,
    `coverage` TEXT NOT NULL, `indexed_chapters` INTEGER NOT NULL DEFAULT 0,
    `total_chapters` INTEGER NOT NULL DEFAULT 0, `tokenizer_version` INTEGER NOT NULL DEFAULT 0,
    `indexed_at` INTEGER NOT NULL DEFAULT 0, `reason` TEXT,
    PRIMARY KEY(`book_id`, `text_basis`))
```

即 `MigrationTestHelper.runMigrationsAndValidate(...)` 的表结构校验具备通过条件。

---

## 5. 改动文件清单

| 文件 | 类型 | 说明 |
|---|---|---|
| `feature/search/SearchCoverage.kt` | 新增 | 文本基准 / 覆盖率状态 / 原因码 / 判定策略（纯函数） |
| `data/local/dao/SearchTermDao.kt` | 修改 +89 | `SearchTermRow` 加 `text_basis` 并进复合主键；新增 `SearchIndexCoverageRow` + `SearchIndexCoverageDao` |
| `data/local/AppDatabase.kt` | 修改 +79 | 版本 11→12；注册实体与 DAO；新增 `MIGRATION_11_12` |
| `data/local/DatabaseModule.kt` | 修改 +8 | 注册 `MIGRATION_11_12`；提供 `searchIndexCoverageDao` |
| `data/repository/SearchIndexRepository.kt` | 修改 +340/-66 | 覆盖结果上报与落库；双基准 term 行；upsert 批次收紧；读侧入口 |
| `androidTest/…/AppDatabaseMigrationTest.kt` | 修改 +209 | 新增 `migrate_11_to_12_…` 与 `migrate_1_to_12_full_chain` |
| `test/…/feature/search/SearchCoveragePolicyTest.kt` | 新增 | 10 个纯函数契约测试 |
| `app/schemas/…/12.json` | 新增（未跟踪） | KSP 自动导出 |

未触碰：书库 / 进度 / 批注 / 同步相关表与代码；`docs/` 与 `archives/`。

---

## 6. 未覆盖项 / TODO

1. **instrumentation 迁移测试未在本机跑通** —— `adb devices` 显示 `c49ac6cf` 处于 **offline**，
   无法执行 `connectedAndroidTest`。新增的两个迁移测试已**编译通过**，实际执行交给 CI
   （`android-migration-tests`，API30 x86_64）。**这是本片唯一的验证缺口。**
2. **`app/schemas/…/12.json` 目前是未跟踪文件（untracked）。**
   CI 构建时 KSP 会自动重新导出，但 1..11 都是入库的，**建议提交时一并把 12.json 入库**，
   避免 schema 目录出现「历史版本在库里、最新版本不在」的不一致。
   （本片按交接约定不执行 commit。）
3. **显示文（`display`）通道本片只建模型、不建索引** —— 每本书的 display 覆盖行恒为
   `PENDING` / `display_channel_not_built`。真正构建该通道（调用 `RuleEngine.applyReplace`
   并写入 `text_basis='display'` 的 term 行）属于后续片。
   这样安排的理由：查询侧（`searchContent`）目前仍是死 seam，现在就把显示文写好也无从消费，
   且「查不到 = 没有」会被 UI 误读成「原文里没有」—— 显式的 `PENDING` 行正是为了避免这个歧义。
4. 未做：lint、真机/视觉验收、assembleDebug 全量打包（均非本片 Dev-verified 的必要项）。
5. `ContentHit` 载荷升级（章节 / source 位置 / 上下文 / 文本基准）、`searchContent` 接线、
   命中→精确到达、可返回 —— 分别为 S1.2 / S1.3 / S1.4 / S1.5。

---

## 7. SEAM REQUEST

**无。** 本片全部改动落在搜索索引这一条链上（`feature/search` + `data/local` 搜索表 +
`data/repository/SearchIndexRepository`），未越界进入阅读器、同步或 UI 层。
`DatabaseModule` 属于本链的数据层装配，已在改动前确认无人并发占用。

---

## 8. 结论

- 「TXT/EPUB/Markdown 分别记录索引覆盖」✅ —— 每本书 × 每种基准一行，`format` 落库，
  覆盖率与原因码由实际走通的路径上报，不再可推断。
- 「部分索引不是全文完成」✅ —— `PARTIAL` 由 `truncated` / `indexedChapters < totalChapters`
  两个硬判据产生，并由 `SearchCoveragePolicyTest` 钉死。
- 「索引仓储不能据类名推断全覆盖」✅ —— 覆盖率是持久化数据，缺行即代表「尚未得知」，
  UI 不得回退到按 format 猜测。
- 「可取消、续建、重试」—— 机制沿用既有的游标断点（`search_index_state`）+ `rebuildAll()`；
  **本片新增的是可观测性**：`incompleteBookIds()` 让「重建哪些书」从盲扫变成选点。
- 「搜索原文与替换显示文的口径必须明确」—— schema 层已明确（双基准进主键），
  **口径的 UI/文案落地在 S1.6**。
