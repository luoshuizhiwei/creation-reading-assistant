# S01/S02 父表安全与迁移证据调查（窗口 B）

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 分支：`codex/recover-worktree-20260920`；HEAD：`5aed5939600e491843e3c44453921bd6273a233c`
- 日期：2026-09-22（2026-09-22 修订一次：外键统计、验证结论口径、迁移用例设计）
- 只读调查；未改产品/测试源码，未跑 Gradle/ADB。
- 前置已读：`2026-09-22-current-task-ledger.md`、`../plans/2026-09-22-android-recovery-convergence-plan.md`

**结论（口径已收敛）**

| 项 | 结论 |
|---|---|
| S01 父表写入 | **当前实现静态核对符合预期**；运行验收待补 |
| S01 运行证据 | **未找到**该类执行记录（不写"从未执行"） |
| S02 迁移实现 | **当前实现静态核对符合预期**（schema 差异极干净）；运行验收待补 |
| S02 测试覆盖 | **确认缺口**：测试源零引用 `MIGRATION_14_15`，最长链路止于 v14 |
| 通用覆盖机制 | **本轮检查范围内未发现**（非"不存在"） |
| 数据库测试与 UI 阻塞 | 本轮静态判断：三类数据库用例均不依赖 Activity（历史设备成功不保证当前环境成功） |
| 附带发现 | `DatabaseSafetyNet` 类头 KDoc 与实现不一致（B-N1，P3，非产品故障） |

> 「静态核对符合预期」的边界：源码写法、生成代码与 Room 运行时语义一致，且 schema 差异符合设计意图。
> 这**不等于**已通过运行验收 —— 见 §2、§3 的证据缺口。
> 「连续注册 1→2 … 14→15」只证明**迁移路径齐全**，**不保证**任意历史数据库都能升级成功
> （真实库可能含未预期状态，如残留索引、被外部改写的 `user_version`、损坏的 WAL）。

---

## 1. 五类父表写入路径（任务 1）

父表由 `15.json` 反查外键得出，恰 **5 张**被引用：`books`、`inspirations`、`tags`、`categories`、`shelves`。

**全库外键统计（`15.json`，26 个实体）**：**18 条**，其中 **16 条 CASCADE + 2 条 SET NULL**。
按被引用方分布：`books` **13 条**、`inspirations` 2 条、`tags`/`categories`/`shelves` 各 1 条。
（两条 SET NULL 为 `inspirations.source_book_id`、`notes.inspiration_id`。）

| 父表 | 普通写入 | 批量 | 软删除 | 同步合并 | 判定 |
|---|---|---|---|---|---|
| books | `@Upsert` `BookDao.kt:38` | `@Upsert` `:50` | UPDATE `:108` | `applyBook`→`@Upsert` `SyncRepository.kt:180` | 静态符合 |
| inspirations | `@Upsert` `InspirationDao.kt:24` | 无此方法 | 无此方法 | `applyInspiration`→`@Upsert` `:204` | 静态符合 |
| tags | `@Upsert` `TaxonomyDao.kt:26` | 无 | UPDATE `:42` | 不经该 DAO | 静态符合 |
| categories | `@Upsert` `:60` | 无 | UPDATE `:79` | 不经该 DAO | 静态符合 |
| shelves | `@Upsert` `:97` | 无 | UPDATE `:113` | 不经该 DAO | 静态符合 |

**其余 REPLACE 不算缺陷**：DAO 中所有 `OnConflictStrategy.REPLACE` 写入的都是**叶表/关系表**，没有表引用它们 ——
`chapter_reads`、`notes`、`highlights`、`reading_sessions`、`search_index_coverage`、`book_tag`、
`book_category`、`shelf_book`、`book_content`、`book_files`、`inspiration_variants`、
`reader_text_rules`、`reader_text_corrections`。REPLACE 的"先删后插"对它们无级联后果。
**不能机械地把所有 REPLACE 判错。**

**机制核对（不只看注解）**：
- 生成代码 `BookDao_Impl.java:145` 的 INSERT 语句**不含** `OR REPLACE`/`OR IGNORE`；
- 从 `room-runtime-2.6.1.aar` 提取 `androidx/room/EntityUpsertionAdapter.class`，常量池为
  `insert` → `SQLiteConstraintException` → `checkUniquenessException` → `updateAdapter.handle`：
  **先 INSERT，冲突改走 UPDATE，全程不 DELETE**；
- 调用点包在 `beginTransaction/endTransaction`（`:386,405`）。

⇒ `books` 写入不会触发 `ON DELETE CASCADE`，与 KDoc 声称一致。

**同步/恢复**：`applyBook` 对已存在的书用 `local.copy(远端列…)` 保本机列；批次包在
`bookDao.runInTransaction {}`（`SyncRepository.kt:115`）；`JsonBridge.kt:190` 恢复同样在事务内用 `@Upsert`。

> 证据等级：生成代码 + Room 运行时字节码，属**静态强证据**。能证明写法正确，不能替代设备执行。

---

## 2. `ParentUpsertCascadeSafetyTest` 覆盖与执行证据（任务 2）

### 五个方法按断言核对

| # | 方法 | 覆盖 | 缺口 |
|---|---|---|---|
| 1 | `upsertingBookPreservesAllChildRows` | 单本 upsert 后 progress/content/file/session/note/highlight/book_tag/book_category/shelf_book/chapter_reads/library_source_refs 存活；灵感 `source_book_id` 未置空；变体存活；改名生效 | 未含 `reader_text_rules`、`reader_text_corrections` |
| 2 | `upsertingBooksInBatchPreservesChildRows` | `upsertAll` 后 progress/session/book_tag 存活 | 断言窄（仅 3 张子表） |
| 3 | `upsertingInspirationPreservesVariantsAndNoteLink` | 变体存活、note 的 `inspiration_id` 未置空 | — |
| 4 | `upsertingTaxonomyParentsPreservesJoinRows` | tag/category/shelf `upsert` 后关联表存活 | 未覆盖关联表 `upsertAll` |
| 5 | `softDeletePreservesChildRows` | books 软删除后子行存活、活跃查询看不到该书 | 只测 books，未测 tags/categories/shelves 软删除 |

**该测试确已针对 REPLACE 级联删除风险**：用例 1/2/5 直接断言 `books` 写入后进度、会话、笔记、高亮等
子数据保留 —— 若 `bookDao.upsert` 退化为 REPLACE 语义，这些断言会失败。**现有用例能拦住该整体退化。**

**覆盖完整性缺口**（不等于"拦不住"）：

1. `reader_text_rules`、`reader_text_corrections` 未进入任何断言 —— 二者是 `books` 的 CASCADE 子表
   （`books` 13 条外键中的 2 条），`seedChildren` 从未写入。这是**覆盖完整性缺口**：
   若这两张表将来被单独写坏（例如只有它们的写入路径被改），现有用例不报警。
2. tags/categories/shelves 的 `softDelete` 未测（机制上是 UPDATE，风险低但无断言）。
3. 父表 `upsertAll` 仅覆盖 books（其余四张无批量方法，属"不存在"而非"未测"）。
4. 同步合并路径无直接断言（`content_status`/`local_content_path` 的"保留本机列"语义未被钉死）。

> 说明：本轮**未做变异测试**。上述"现有用例能拦住整体退化"是**基于断言的静态判断**，
> 不是实测结果；缺口 1–4 同样按静态覆盖分析列出。

### 执行证据：未找到

| 检索位置 | 结果 |
|---|---|
| `docs/qa/**` | 无该类报告（`grep -rl` 空） |
| `docs/handoff/`、`docs/coordination/*.md` | 仅 9/22 账本与任务书称"用例存在"，**无执行结果** |
| `build/outputs/androidTest-results/` | 目录不存在 |
| `build/**/TEST-*.xml` | 无任何 androidTest 结果 XML |
| 9/19 产品切片盘点 `:184,:192` | 仅"新增该测试"，无运行数字 |

**唯一相关物证**：`build/intermediates/classes/debugAndroidTest/.../ParentUpsertCascadeSafetyTest*.class`
（6 个类文件）。仅证明**被编译过**，不证明被执行。

**结论：未找到运行证据。** 按任务书要求不写"从未执行" —— 可能在未留档的本地运行中通过过，
但本轮检索不到可引用证据。

### 对照：迁移测试有过真机执行（历史证据，不等于当前环境）

`docs/plans/android-parallel-delivery-2026-09-09/reports/room-migration-runtime-supplement-2026-09-14.md`：
设备 `c49ac6cf`（Xiaomi 22081212C / Android 15，非 MuMu），**冻结 commit `fd7f908e…`（≠ 当前 HEAD）**，
仅 3 条用例 `migrate_12_to_13…`、`migrate_13_to_14…`、`migrate_12_to_14_full_chain…`，
均 `OK (1 test)`、ADB 退出码 0，并记录了 fixture 隔离（`migration-test` 库名）与设备数据保护。

**意义**：① 该类测试在**该次环境**下可执行；② 最高只到 13→14，**不构成任何 14→15 证据**；
③ 历史成功**不保证当前工作树/设备/构建产物**能复现同样结果。

---

## 3. 14→15 实际迁移与 schema 差异（任务 3）

### 实现与注册

```kotlin
// AppDatabase.kt:543
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        dropPartialIndexes(db)
        db.execSQL("DROP INDEX IF EXISTS `index_search_terms_term`")
    }
}
```

- 版本唯一真源 `APP_DATABASE_SCHEMA_VERSION = 15`（`:70`），`@Database(version = APP_DATABASE_SCHEMA_VERSION)`（`:127`）。
- 注册：`DatabaseModule.kt:72` 已加入 `addMigrations`，共 14 个（1→2 … 14→15），**路径齐全无缺口**。
- **`fallbackToDestructiveMigration()` 未出现在产品源码**（仅 KDoc 写"已移除"）⇒ 迁移失败抛异常，不静默清库。

### schema 差异（14.json vs 15.json 结构化比较）

解析两份 JSON 后**全部差异仅 4 处**：删 `search_terms.index_search_terms_term`、`identityHash`
`008e3a91…`→`4dfc708f…`、`room_master_table` 随动、`version` 14→15。**实体数、列、主键、其余索引均无变化**。
v15 的 `search_terms` 主键为 `['term','book_id','chapter_index','text_basis']`，保留 `index_search_terms_book`。

合理性（`SearchTermDao.kt:70-81` 自证）：复合主键首列即 `term`，SQLite 自动建
`sqlite_autoindex_search_terms_1`；被删的索引只是重复存储（实测 ~25MB）。
**`index_search_terms_book` 是 `distinctBookIds` 与按书删除的依赖 —— 迁移确实保留了它。**
（注：该 ~25MB 为产品方测量，本轮未复核。）

### 手工索引回调

- `dropPartialIndexes(db)` 是每个迁移的**第一行惯例**，删 11 个 `idx_*` 手工部分索引
  （`PARTIAL_INDEX_NAMES` `:628`）；不先删会被 Room 判 schema 不一致。
- `CreateIndexCallback.onOpen` 随后重建（`PARTIAL_INDEX_SQL` `:637`），**只在 onOpen**，不干扰校验。
- ✅ 已确认 `PARTIAL_INDEX_SQL` **不含** `index_search_terms_term` ⇒ **onOpen 不会复活它**。
- ⚠️ 该索引仍在 `MIGRATION_10_11`（`:357`）与 `MIGRATION_11_12`（`:416`）内以
  `CREATE INDEX IF NOT EXISTS` 创建，这是**正确的**：历史迁移须复原当时 schema 才能过中间校验，
  14→15 依赖它存在才能 DROP。**不要顺手删这两行**，否则 10→11/11→12 校验失败。

### 支持的升级起点

`addMigrations` 覆盖 1→2 至 14→15，任何 v1..v14 的**结构正常**磁盘库都应能升至 v15。
`DatabaseSafetyNet.snapshotIfUpgrading` 在 `onDisk < codeVersion` 时先整库快照（含 `-wal`/`-shm`），属升级前兜底。
**路径齐全 ≠ 任意历史库必成功** —— 见 §8。

### 迁移用例设计（修订版）

按**三类优先**组织，不再凑五条；重复的查询/数据保留检查合并。

**A. 直接 14→15（必需，覆盖本次变更本体）**

`migrate_14_to_15_drops_redundant_term_index`：
`createDatabase(TEST_DB, 14)` 插 books + 若干 search_terms 行 → `runMigrationsAndValidate(…, 15, true, MIGRATION_14_15)` →
① `sqlite_master` 中**无** `index_search_terms_term`；② **有** `index_search_terms_book`；
③ 迁移前后 search_terms 逐行数据全等（term/book_id/chapter_index/text_basis/hits/offsets）；
④ books 数据保留；⑤ 迁移后 `SELECT … WHERE term=? AND text_basis=?` 仍返回同一行。

> 第 ⑤ 条的**准确含义**：只证明**删除该索引未改变查询结果**（正确性回归），
> **不证明**查询走了 `sqlite_autoindex_search_terms_1`。若要断言索引使用，
> 需另加 `EXPLAIN QUERY PLAN` 检查（属性能/计划断言，与正确性断言是两件事）；
> 本轮不要求，但不得把第 ⑤ 条写成"证明 autoindex 覆盖查询"。

**B. 代表性历史链（必需，覆盖跨段组合）**

`migrate_13_to_15_full_chain`：`createDatabase(TEST_DB, 13)` → 跑 `MIGRATION_13_14, MIGRATION_14_15` → 末端按 15.json 校验。

⚠️ **必须区分"旧数据保留"与"新表建立"**：v13 **尚无** `library_source_refs`（该表由 13→14 新建）。
因此不能在 v13 阶段往该表插数据、也不能断言它"存活"。正确断言：
- **旧数据保留**：v13 已有的 books / reading_progress / highlights 在两段迁移后仍在；
- **新表建立**：`library_source_refs`（13→14 新建）与 term 索引删除（14→15）在末端成立，
  且该表**可写可读**、外键 CASCADE 生效。

**最老版本链（1→15）是否需要独立覆盖 —— 判断而非机械添加**：
- 支持理由：`index_search_terms_term` 的完整生命周期是 **10→11 创建 → 11→12 重建 → 14→15 删除**，
  只有 1→15 一条链路能端到端走完；且这是"最老安装直升"的真实用户路径。现有最长链测试止于
  `migrate_1_to_12_full_chain`，**11→12 之后的段未被任何 1→N 链覆盖**。
- 反对理由：13→15 已覆盖 14→15 与 13→14 的组合；1→12 链已覆盖 10→11/11→12。
  边际收益主要是"中间态组合"与"分段器连续调用"，而非新 schema 差异。
- **建议**：若本轮只留一条历史链，选 **13→15**；`1→15` 作为**次优先**补强项登记，
  理由是它是唯一能验证 term 索引完整生命周期的链路 —— 但**不因此判 13→15 不足**。

**C. 正式开库回调（不可省）**

用 `Room.databaseBuilder(…).addMigrations(全部).addCallback(CreateIndexCallback())` **真开库**，
断言 11 个 `idx_*` 部分索引在 onOpen 后存在、且 `index_search_terms_term` **仍不存在**。

**为何 A/B 不能替代 C**：A、B 走 `MigrationTestHelper` 裸连接，**不触发 onOpen**，
无法证明"部分索引正常重建"与"term 索引不被回调复活"。这正是 S02 门禁中"正式开库回调"一项。

---

## 4. 是否存在通用机制覆盖 14→15（任务 4）

**本轮检查范围内未发现**（限于下列检索，非"不存在"）。

| 候选机制 | 检查结果 |
|---|---|
| 测试源引用 `MIGRATION_14_15` | `grep -rn` 于 `src/test`、`src/androidTest` → **0 命中** |
| 测试引用 `APP_DATABASE_SCHEMA_VERSION` | 0 命中 |
| 到 v15 的 `runMigrationsAndValidate` | 唯一调用方是 `AppDatabaseMigrationTest`，最高目标 **14** |
| 反射/遍历自动跑全部 Migration | 未见；`runMigrationsAndValidate` 需**显式**传入迁移对象 |
| KSP 编译期 schema 校验 | `room.schemaLocation` 只**导出** JSON、校验 `@Database` 与实体一致，**不执行迁移** |
| `room-testing` 自动检测 | 只提供 `MigrationTestHelper`，本身不是测试 |
| 真开库测试 | 仅 `ChapterReadRepositoryPersistenceTest.kt:55` 用 `databaseBuilder`，**未加 migrations/callback** |

⇒ 现存测试全链最高覆盖 **v14**。**"已注册 + 编译通过"不构成迁移证据**：
Room 的 schema 校验只在真开库时发生。

> 未穷尽项：未经 build 脚本、CI 配置、Gradle 插件的全量排查（本轮范围外）。

---

## 5. 数据库测试与 UI 启动阻塞的区分（任务 5）

| 测试类 | 上下文获取 | Activity/Compose 依赖 |
|---|---|---|
| `ParentUpsertCascadeSafetyTest` | `ApplicationProvider.getApplicationContext()` + `inMemoryDatabaseBuilder` | **0** |
| `AppDatabaseMigrationTest` | `InstrumentationRegistry.getInstrumentation()` + `MigrationTestHelper` | **0** |
| `ChapterReadRepositoryPersistenceTest` | 应用上下文 + `databaseBuilder` | **0** |

**本轮静态判断**：这三类用例不获取 Activity/Compose 宿主，因此**就代码结构而言**，
D 记录的 S09-B/S15 UI 自动化 62 条 MIUI 启动阻塞**不构成它们的必然障碍**。

**限定**：这是**代码静态判断**，不等于"在当前环境一定能跑通"。
Instrumentation 运行仍受设备、MIUI、权限、测试 APK 安装状态等影响；
9/14 的历史成功**不保证当前工作树与设备**能复现。**本轮未执行任何设备命令**，
实际可运行性须由 D 阶段实测确认。

### 附带发现 B-N1（文档与实现不一致，P3，非产品故障）

`DatabaseSafetyNet.kt:12` 类头仍写"`[DatabaseModule]` 上挂着 `fallbackToDestructiveMigration()`"，
而该调用**已移除**（`AppDatabase.kt:184` 亦注"已移除"）。会让读者误判失败爆炸半径。

- 与旧问题关系：与 M-BUG-11 同源（同属该事故的防护说明），但不属 S01/S02 任一旧 ID，故新给 `B-N1`。
- 最小修复：改这一句 KDoc，说明"当前迁移失败即抛异常（已无破坏性回退），快照是最后兜底"。**本片不实施**。

---

## 6. 下一轮最小独占文件与共享 hunk（任务 6）

**S01（数据写入语义）**：产品代码**无需改动**。若补断言，只动
`androidTest/.../data/local/ParentUpsertCascadeSafetyTest.kt`（**独占**）：在 `seedChildren` 加
`reader_text_rules`/`reader_text_corrections` 写入并加对应断言；补 taxonomy 软删除与同步合并断言。
**S01 不夹带搜索、导入、来源更新。**

**S02（schema/迁移）**：

| 文件 | 归属 |
|---|---|
| `androidTest/.../data/local/AppDatabaseMigrationTest.kt` | **共享**，需 hunk 级分配：只在文件**末尾追加** §3 的 A/B/C，不动既有 20 个用例 |
| `main/.../data/local/AppDatabase.kt` | **只读**（14→15 已正确；`PARTIAL_INDEX_SQL` 与 `:357`/`:416` 两处 `CREATE INDEX` 均不得动） |
| `main/.../data/local/DatabaseModule.kt` | **只读**（注册已完整） |
| `app/schemas/.../15.json` | **只读**；当前 untracked（`?? 15.json`），提交时需显式纳入 |
| `main/.../data/local/DatabaseSafetyNet.kt` | 若采纳 B-N1 则**独占**（仅改类头一句 KDoc） |

**与 S03/S04 的重叠**：`BookRepository.kt`、`SearchTermDao.kt`、`ShelfImporter.kt`、`ShelfViewModel.kt`
在 9/19 盘点中已标记**跨切片 hunk**，S01/S02 **不应触碰**（本调查确认也不需要）。
`DatabaseSafetyNet.kt` 的 B-N1 是纯注释，与 C 的备份数据流无逻辑交集，可安全并行。

**并行建议**：S01 与 S02 改的是**两个不同测试文件**，可并行；但两者都需独占构建目录/设备，
按计划 §5「Gradle 与手机分别单一所有者」串行更稳。

---

## 7. 分类汇总与明确排除的推导

| ID | 分类 | 证据等级 |
|---|---|---|
| S01 | **当前实现静态核对符合预期，运行验收待补** | 源码 + 生成代码 + Room 字节码（静态强） |
| S01-测试 | **运行证据未找到**；覆盖完整性缺口：reader_text_rules/corrections、taxonomy 软删除、同步合并 | 检索穷尽 + 静态覆盖分析 |
| S02 | **当前实现静态核对符合预期，运行验收待补** | schema 结构化比较 + 源码（静态强） |
| S02-测试 | **确认缺口**：零引用 `MIGRATION_14_15`，最高覆盖 v14，无正式开库回调验证 | grep 穷尽 + 无 androidTest 结果 XML |
| B-N1 | **文档不一致（P3，非产品故障）** | 源码对照 |

**不成立的推导（明确排除）**：❌"能编译 ⇒ 数据安全"（编译不执行迁移、不验 `@Upsert` 运行语义）；
❌"历史测试数字 ⇒ 本任务新跑结果"（9/14 属 `fd7f908` 且最高 13→14）；
❌"已有 20 个迁移用例 ⇒ 14→15 被覆盖"（最高目标版本为 14）；
❌"注册路径齐全 ⇒ 任意历史库升级成功"。

---

## 8. 未验证项与越界声明

**未验证**：① 未运行任何测试、未开库、未跑 Gradle/ADB；② `ParentUpsertCascadeSafetyTest`
是否在设备通过 —— **无证据，不推断**；③ 14→15 运行行为 —— 无证据；
④ 迁移后**实际体积下降**未测（~25MB 为产品方测量，本轮未复核）；
⑤ **未做变异测试**（§2 的覆盖结论均为静态分析）；
⑥ 未验证任意真实历史库（含残留索引、异常 `user_version`、损坏 WAL 等）能否升级成功；
⑦ 未访问设备、远端或用户数据。

**越界修改：无。** 仅修改任务书指定的唯一可写文件
`docs/coordination/reviews/undated/agent-stage-b-database-audit.md`。未触碰产品源码、测试、Gradle、安装脚本、
账本、其他报告、窗口 C/D 文件或任何 Agent 记忆库；未 stage/commit/push/切分支；
未运行 Gradle/ADB。其他窗口的并发修改未被覆盖。

**关键文件 SHA-256（调查时点，本轮未再改动）**：

```
b344af6752c9c0fe113cb589e0858d664d2d128a4e9b31462b48e748dc7358cf  .../data/local/dao/BookDao.kt
dabb1be7576071df25b4181dc70a44f43967a7a9b9c0f993c805e09619baf1dd  .../data/local/AppDatabase.kt
4baa6ddb5d907cdb651ff89bd0d0500a632ccfcb8968dbd8eb1e0736bf3c653b  .../androidTest/.../ParentUpsertCascadeSafetyTest.kt
1e65872c9fe8d755ba88fa4274ebfbc85c29b99ef854300065487cf756d6acf5  .../androidTest/.../AppDatabaseMigrationTest.kt
c94ca0e506bfd4317039f37983b12668dd4be50487248b7f7782ac732c85b407  .../schemas/.../15.json
```

**退出条件自检**：可直接据此派工数据库测试补齐（§3 的 A/B/C + S01 断言扩展）；
全篇无"已过构建所以数据安全"一类推导。
