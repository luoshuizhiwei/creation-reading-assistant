# 数据库测试补齐（窗口 C / S01+S02 第一轮实现）

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 分支：`codex/recover-worktree-20260920`；HEAD：`5aed5939600e491843e3c44453921bd6273a233c`
- 日期：2026-09-22
- 前置：`agent-stage-b-database-audit.md`（B 窗口调查结论，本轮按其 §3 的 A/B/C 与 §6 的文件归属实施）

> **本轮状态：尚未编译、尚未执行。**
> 因 B/C/D 多窗口共享工作区，本轮**未运行 Gradle、未运行 ADB、未安装或启动 App、未做真机测试**，
> 也未连接设备或修改任何 MIUI 设置。下文所有"应/必须"均为**设计与静态断言意图**，
> **不是**测试结果。是否通过须由统一构建阶段实测确认。
> 未 stage / commit / push / checkout / reset / clean，工作区其他窗口的改动未被覆盖。

---

## 1. 改动清单

| 文件 | 性质 | 改动 |
|---|---|---|
| `androidTest/.../data/local/ParentUpsertCascadeSafetyTest.kt` | **独占**；**用户既有文件，非本轮新建**（详见 §2 开头说明） | 在既有内容上**追加 46 行**：种子扩展 + 断言扩展 + 新增 1 个私有 helper |
| `androidTest/.../data/local/AppDatabaseMigrationTest.kt` | **共享**，严格**只追加到文件末尾** | +322 / −2；新增 3 个 `@Test` + 2 个私有 helper + 1 个常量 |

**未修改任何范围外文件。** 特别地：`AppDatabase.kt`、`DatabaseModule.kt`、
`DatabaseSafetyNet.kt`、`android/app/schemas/**`（含 `15.json`）、任何生产代码、
其他窗口的报告与任务账本，本轮**一律只读或未触碰**。
迁移测试文件中既有的 20 个用例**一行未改**（`git diff` 显示为纯末尾追加）。

---

## 2. 一、父表安全测试（S01）

### 该文件的归属：**不是本轮新建**

`ParentUpsertCascadeSafetyTest.kt` **不在 HEAD（`5aed5939…`）中**，因此 `git status` 把它记为
未跟踪（`??`）—— 但**未跟踪不等于本轮新建**。该文件在 **2026-09-22 阶段 A 快照**
（`D:\develop\Code\Codex\recovery-snapshots\20260922-stage-a`，16:53:33 +08:00）中**已经存在**，
是用户既有的一份未跟踪源码文件。

本轮是**在该既有文件上追加 46 行**（种子、断言、一个私有 helper）。

**该文件原有的测试内容未被覆盖、未被改写**：原有 5 个 `@Test` 方法与其中全部断言
**一条未删、一条未改**；本轮只做**纯追加**式的种子扩展与断言扩展。
（另可参：9/19 产品切片盘点已记录"新增 `ParentUpsertCascadeSafetyTest`"，
说明它属阶段 A/S01 的既有产物。）

### 改了哪些方法

**`seedChildren(bookId)`（种子扩展）** —— 之前从未写入的两张 `books` CASCADE 子表：

- `reader_text_rules`：写 2 行。`rule-book`（`scope="PER_BOOK"`，`book_id=bookId`）是受级联影响的行；
  `rule-global`（`scope="GLOBAL"`，`book_id=null`）是**对照组** —— 它不受 `books` 外键约束，
  若整张表被误清空，只有靠它才能区分「按书行被级联删」与「整表被清空」这两种截然不同的故障。
- `reader_text_corrections`：写 1 行 `corr-1`（v13 表，同样是 `ON DELETE CASCADE`）。

**`upsertingBookPreservesAllChildRows`（断言扩展）** —— 前置条件块与事故触发点后各加一组：

- 前置：`assertEquals(1, db.readerCorrectionDao().listForBook(id).size)`、
  `assertEquals(Pair(1, 2), ruleCounts(db, id))` —— 断言只在种子确实铺成功时才有意义；
- 后置：纠错行数 + `find_text` 内容未被改写；按书规则数仍为 1；全局规则总数仍为 2。

**新增 `ruleCounts(db, bookId): Pair<Int, Int>`（私有 helper）**

`ReaderTextRuleDao` 只暴露 `observeAll()` / `observeByKind` / `observeById` / `getById`，
**没有按 `book_id` 查询的方法**。按任务书"不得为此修改生产 DAO"，
这里在测试内对 `observeAll().first()` 做 `book_id` 分流，**未新增任何生产 API**。
（`ReaderCorrectionDao` 本身有 `listForBook`，直接用。）

### 保护的行为

父表（`books`）的普通写入不得让 `reader_text_rules` / `reader_text_corrections` 消失或变形 ——
即 M-BUG-11 事故路径在这两张此前**零覆盖**的子表上同样被钉死。
原有 5 个用例的断言一条未删，覆盖面只扩不缩。

### 未做的部分（有意不扩大范围）

按 B 报告 §2 列出的其余缺口（taxonomy 软删除、同步合并"保留本机列"）**本轮未动**，
留待后续轮次；本轮聚焦任务书指定的两张表。

---

## 3. 二、v14→v15 迁移测试（S02）

新增 **3 个** `@Test`，全部追加在文件末尾（既有用例零改动）。

### 3.1 `migrate_14_to_15_drops_redundant_term_index`

`createDatabase(TEST_DB, 14)` → 插 1 本书 + 2 行 `search_terms` →
`runMigrationsAndValidate(TEST_DB, 15, true, AppDatabase.MIGRATION_14_15)`。

| # | 断言 | 保护的行为 |
|---|---|---|
| 1 | `sqlite_master` 中**无** `index_search_terms_term` | 本次变更本体：冗余索引被删 |
| 2 | **有** `index_search_terms_book` | `distinctBookIds` 与按书删除的依赖，误删即功能退化 |
| 3 | 2 行 `search_terms` 逐列全等且无第三行 | 纯 DDL 不得动任何数据行 |
| 4 | `books` 数据保留 | 同上 |
| 5 | `WHERE term=? AND text_basis=?` 仍返回 `hits=3` | 删索引未改变查询结果（正确性回归） |

> **第 5 条的口径**：只证明**结果没变**，**不证明** SQLite 走了 `sqlite_autoindex_search_terms_1`。
> 要断言索引使用需另做 `EXPLAIN QUERY PLAN`（属查询计划断言，与正确性断言是两件事），本轮不做。
> 代码注释里已写明这一限定。

### 3.2 `migrate_13_to_15_full_chain_keeps_old_data_and_creates_source_refs`

`createDatabase(TEST_DB, 13)` → 插 books / reading_progress / highlights / 1 行 `search_terms` →
跑 `MIGRATION_13_14` + `MIGRATION_14_15` 到 v15。

**刻意区分两类事实**（B 报告重点提醒）：v13 **尚无** `library_source_refs`（该表由 13→14 新建），
因此**不在 v13 阶段向该表插数据**、也**不断言它"存活"**，只断言末端「已建立 + 可写可读」。

| 断言组 | 内容 |
|---|---|
| 旧数据保留 | books / reading_progress（含 `total_reading_time_ms=3_600_000`）/ highlights（含 `chapter_title` `progress_percent`）在两段迁移后原样存活 |
| 新表建立 | `library_source_refs` 表存在；INSERT 后可读回 `display_name` / `availability` |
| 索引 | 末端无 `index_search_terms_term`；有 `index_search_terms_book` |
| 外键 | `PRAGMA foreign_keys = ON` 后 `DELETE FROM books` ⇒ `library_source_refs` 0 行残留（CASCADE 生效） |

> `PRAGMA foreign_keys = ON` 是必需的：`MigrationTestHelper` 的裸连接不开外键，
> 与文件内既有 4 处同类用法一致。

### 3.3 `openingDatabaseRebuildsPartialIndexesAndDoesNotResurrectTermIndex`

**为何不可省**：3.1 / 3.2 走 `MigrationTestHelper` 的裸连接，**不触发 `onOpen`**，
无法证明「部分索引在真实开库路径上被重建」与「term 索引不被回调复活」。

做法：`tearDownOpenCallbackDb` → `createDatabase(OPEN_CALLBACK_DB, 14)` 插 1 本书 →
`Room.databaseBuilder(...).addCallback(AppDatabase.CreateIndexCallback()).addMigrations(全部 14 个).build()`
→ 取 `db.openHelper.writableDatabase` 触发开库。

| 断言 | 内容 |
|---|---|
| 版本 | `PRAGMA user_version == 15` |
| 数据 | 书籍行保留 |
| 部分索引 | `AppDatabase.PARTIAL_INDEX_NAMES` 全部 11 个在 onOpen 后存在 |
| term 索引 | **未**被回调复活；`index_search_terms_book` 仍存在 |

**隔离**：库名 `migration-open-callback-test`，既独立于 `TEST_DB`，
也独立于主应用库 `creation_reading_assistant_native`；`tearDownOpenCallbackDb`
在**前后各删一次**（`finally` 中也有），不接触任何用户数据。

### 未做的部分

**1→15 超长链本轮不增加** —— 按任务书「先保留为次优先增强，避免扩大任务」。
（B 报告 §3.B 已记录：它是唯一能端到端验证 `index_search_terms_term`
「10→11 创建 → 11→12 重建 → 14→15 删除」完整生命周期的链路，建议后续补。）

---

## 4. 静态自查内容与结论

本轮只做了静态核对（**不替代编译**）：

1. **schema 列名逐一比对**：用脚本解析 v13/v14/v15 的 `schemas/*.json`，
   核对三个新用例里的所有 `INSERT` 列清单与 NOT NULL 列。
   已确认 `books`(18 列) / `reading_progress`(12 列) / `highlights`(14 列) /
   `search_terms`(6 列) / `library_source_refs`(12 列) 与对应版本 schema **完全一致**，
   且 NOT NULL 列（`updated_at`、`completion_state` 等）均已赋值。
   修正了一处初稿错误：`reading_progress` 的 `updated_at` 被写成 `NULL`。
2. **Room API 可见性**：从 Gradle 缓存 AAR 提取字节码 `javap` 确认
   `RoomDatabase.getOpenHelper()` 为 public 且无 `@RestrictTo`；
   `SupportSQLiteOpenHelper.getWritableDatabase()` 为接口方法 ⇒ Kotlin 中
   `db.openHelper.writableDatabase` 属性访问形式成立。
   `RoomDatabase.Builder` 的 `autoCloseTimeout` 默认 `-1` ⇒ 不会套
   `AutoClosingRoomOpenHelper`，持有 `writableDatabase` 句柄是安全的。
3. **被测符号存在性**：`PARTIAL_INDEX_NAMES`(11) / `PARTIAL_INDEX_SQL`(11) /
   `CreateIndexCallback` 均为 `AppDatabase` companion object 的 public 成员；
   `MIGRATION_1_2 … MIGRATION_14_15` 14 个迁移对象名逐一核对存在；
   `readerTextRuleDao()` / `readerCorrectionDao()` 在 `AppDatabase` 中已声明。
4. **实体/DAO 签名**：`ReaderTextRuleEntity` 的 `created_at`/`updated_at` 为 `Long`（传 `1L` 正确）；
   `ReaderCorrectionEntity.STATUS_ACTIVE` 常量存在；`ReaderCorrectionDao.listForBook` 为 `suspend`。
5. **重复定义检查**：全文件函数名录重，无重名；新增的 `indexExists` 未与既有
   `assertStableDenseOrder` 等 helper 冲突。
6. **import 检查**：新增的 `Context` / `Room` / `SupportSQLiteDatabase` /
   `ApplicationProvider` / `assertFalse` 均确认有实际使用点。
   `assertNotNull` 是本文件**原有**的未使用 import（HEAD 中已存在），本轮未引入。
7. **命名冲突**：三个新用例的方法名在全文件中唯一。

---

## 5. 尚未验证的编译与运行风险

以下均为**未验证项**，须由统一构建阶段处理：

1. **编译未验证**。未跑 `gradlew`、未跑 KSP、未做 kotlinc 类型检查。
   存在签名/类型/import 层面翻车的可能（静态比对降低了概率，不等于零）。
2. **lint 阻断**：`android/app/build.gradle.kts:138-141` 设了 `lint { abortOnError = true }`。
   若本轮任何 import 实际未被使用，或触发其他 lint 规则，可能在 lint 阶段报错 —— 静态已查，未实测。
3. **迁移运行时行为未验证**。`runMigrationsAndValidate` 的 Room schema 校验是否对
   v15 通过，仅有 JSON 层面的比对（差异仅删 1 个索引 + identityHash），未实际跑过。
4. **`MigrationTestHelper` 与 `Room.databaseBuilder` 的库路径一致性**是静态推断：
   `ApplicationProvider.getApplicationContext()` 与
   `migrationTestHelper` 内部 `instrumentation.getTargetContext()` 应解析到同一
   `getDatabasePath`，故 3.3 先 `createDatabase` 再真开库能命中同一文件。**未实测**。
5. **`PRAGMA user_version` 读取方式**：3.3 走的是 `handle.query("PRAGMA user_version")`。
   该文件内此前无同类用法（其余用例多用 `sqlite_master` 查表/索引），
   该写法在 `SupportSQLiteDatabase` 上能否返回结果列，**未实测**。
6. **真机可运行性未验证**。设备、MIUI、测试 APK 安装状态等因素均未接触；
   9/14 的历史成功（`fd7f908`，且只到 13→14）**不保证**当前工作树可复现。
7. **未做变异测试**：未验证「把 upsert 改回 REPLACE」或「把 14→15 的 DROP 删掉」
   时这些新断言**确实会失败**。保护强度是静态判断。
8. **v13→v15 只覆盖代表性链路**，不代表任意历史库（残留索引、异常 `user_version`、
   损坏 WAL）都能升级成功 —— 与 B 报告 §8 同一限定。
9. **`ParentUpsertCascadeSafetyTest.kt` 在 HEAD 中不存在**，git 记为 `??` 未跟踪
   （该文件在阶段 A 快照中已存在，属用户既有文件，非本轮新建 —— 见 §2 开头）。
   因此提交时须**显式纳入**，避免它连同本轮的 46 行追加一起被漏掉；本轮**未** stage。

---

## 6. 越界声明与收尾状态

- **越界修改：无。** 仅改动任务书允许的两个测试文件与本报告；其中
  `ParentUpsertCascadeSafetyTest.kt` 为用户既有未跟踪文件，本轮只追加、未覆盖其原有内容。
- **生产代码：未改**（无 DAO 新增方法、无 `@Database` / 迁移 / schema 改动）。
- **其他窗口文件、任务账本、Agent 记忆库：未触碰。**
- **未运行**：Gradle / KSP / ADB / App / 真机测试 / 设备设置修改 / MuMu。
- **Git**：分支 `codex/recover-worktree-20260920`、HEAD `5aed5939…` 未变；
  **未** stage、commit、push、checkout、reset、clean。

**下一步**：等待统一构建阶段执行编译与真机验收。若编译报错，
优先看 §5 第 1、2、5 条（lint、未用 import、`PRAGMA user_version` 读取）。
