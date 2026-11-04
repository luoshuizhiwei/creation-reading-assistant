# Stage E 独立候选：父表 `REPLACE`→`@Upsert` 数据丢失修复（候选 U）+ 版本号 0.5.1(3)

> 窗口：Stage E 独立数据安全候选整理（只读调查 + 单文件产出）。
> 分支 `codex/recover-worktree-20260920`，HEAD `5aed5939600e491843e3c44453921bd6273a233c`（复核时仍为此值）。
> **本文件只提出候选与边界，不宣称"已独立编译"、"已通过测试"或"已修复"。** 所有构建/真机结论均引用他人既有证据，并标注其时间与边界。

---

## 0. 一句话结论

1. **父表 `@Upsert` 修复（候选 U）** 的可分离边界是 **3 个文件的 10 个 hunk + 1 个新测试文件 + 1 个版本号 hunk**；其中 `BookDao.kt` 第 5 个 hunk（`getAllActive`/`activeIds`）**不属于**本组，必须用 `git add -p` 排除。
2. **版本号 0.5.1(3)** 与 `@Upsert` 修复同源（其注释逐字引用 `BookDao.upsert`），**属候选 U，不属 Room v15**；它在 `build.gradle.kts` 中与 QA-host / testBuildType / instrumentation runner 同文件，需单独取 hunk。
3. **Room v15（候选 G0）** 与本组**文件不相交、无硬依赖顺序**：G0 = `AppDatabase.kt` + `DatabaseModule.kt` + `SearchTermDao.kt` 的 1 个索引 hunk + 新 `15.json`。
4. `ParentUpsertCascadeSafetyTest.kt` 是 **androidTest 仪器测试**（`@RunWith(AndroidJUnit4::class)`，5 个 `@Test`，未跟踪新文件），**不计入 JVM 3208**；其**既有原始验证**是 2026-09-23 00:06 真机 `retry-01` 的 `OK (5 tests)`。
5. **尚未独立验证**：本候选未单独编译、未单独跑测试；`@Upsert` 与 v15 的既有证据来自**整棵工作树**（含其他窗口成果）的产物，不等于"本候选单独成立"。

---

## 1. 依据回读（只读）

| 文档 | 关键要求/事实（本候选据此） |
|---|---|
| `docs/coordination/reviews/2026-09/2026-09-29-stage-e-four-window-review.md` | Stage E 仍 4/5=80%；G0（Room v15）必须把 `SearchTermDao.kt` 的冗余索引删除放进**同一原子边界**；矩阵计数：数据 64 行 / UI 102 行 / reader 42&19 行 / asset 92 行 |
| `docs/coordination/legacy-stages/2026-09/2026-09-29-stage-e-hold-data-sync.md` | `BookDao.kt`/`InspirationDao.kt`/`TaxonomyDao.kt`/`SearchTermDao.kt`/`build.gradle.kts` 均列为 HOLD「需人工归属」 |
| `docs/plans/android/2026-09-22-android-recovery-convergence-plan.md` §E（line 90 `### E. 整理版本并选择最终目录`） | Stage E = 整理版本 + 选最终目录；本任务是其中"独立可提交候选"的细分 |
| `docs/coordination/reviews/undated/agent-stage-b-database-audit.md` | 记录本组各文件的**调查时点 SHA-256**（可用于判断是否有后续漂移）；并明确"能编译 ≠ 数据安全""无证据不推断" |
| `docs/qa/2026-09/2026-09-22-stage-c-database-device-acceptance.md` | retry-01 真机验收：`ParentUpsertCascadeSafetyTest` **5/5 PASS**、`AppDatabaseMigrationTest` **23/23 PASS**（2026-09-23 00:06） |
| `docs/coordination/reviews/2026-09/2026-09-28-stage-b-s01-s02-data-risk-closure-review.md` | S01/S02 数据风险收口复核，与"父表 REPLACE"同源 |

---

## 2. 候选 U —— 父表 `REPLACE`→`@Upsert` 的精确 hunks

### 2.1 根因（为什么必须改）

Room 打开库时执行 `PRAGMA foreign_keys = ON`。`@Insert(onConflict = REPLACE)` 生成 `INSERT OR REPLACE`，语义是**先 DELETE 冲突行再 INSERT**；当该表是外键**父表**时，DELETE 会触发子表 `ON DELETE CASCADE`/`SET NULL`，把子表数据一并物理删除且不可撤销。改为 `@Upsert` 生成 `INSERT ... ON CONFLICT DO UPDATE`，不删行、不触发级联，写入结果列值一致。

受影响父表：`books`（13 张子表）、`inspirations`、`tags`、`categories`、`shelves`。`library_source_refs` 在 **HEAD 已是 `@Upsert`**（无需改动）。

### 2.2 `BookDao.kt` — 5 个 hunk，本组只取前 4 个

`git diff` 身份：`index 11ea28b..f9db716`，numstat **46 / 3**。
当前工作树 SHA-256：`B344AF6752C9C0FE113CB589E0858D664D2D128A4E9B31462B48E748DC7358CF`
（与 `agent-stage-b-database-audit.md` §8 记录的 `b344af67…` **一致 → 该文件自调查以来无漂移**）

| # | hunk header | 变更内容 | 属本组？ |
|---|---|---|---|
| B1 | `@@ -7,0 +8 @@` | `+import androidx.room.Upsert`（+1） | ✅ |
| B2 | `@@ -16 +17,22 @@` | `@Insert(onConflict = OnConflictStrategy.REPLACE)` → **大段 KDoc + `@Upsert`**（`BookDao.upsert`，−1/+22） | ✅（核心） |
| B3 | `@@ -22 +44 @@` | KDoc 行 "与 SQLite 的 REPLACE 不同…" → "与 REPLACE 不同…"（`softDelete` 注释，−1/+1） | ✅（同源文案） |
| B4 | `@@ -27 +49,2 @@` | `@Insert(onConflict = REPLACE)` → 1 行 KDoc + `@Upsert`（`BookDao.upsertAll`，−1/+2） | ✅（核心） |
| B5 | `@@ -38,0 +62,20 @@` | 新增 `getAllActive()` 与 `activeIds()`（+20） | ❌ **不属本组** |

**B5 的归属证据（必须排除的理由）**：
- `getAllActive()` 的调用方是 `feature/sync/LegadoReadingProgressImporter.kt:82`（跨阅读器进度导入，未跟踪新文件）；
- `activeIds()` 的调用方是 `data/repository/SearchIndexRepository.kt:607`（搜索侧过滤已删书的残留索引行）；
- 两者与"父表写入改 `@Upsert`"无语义依赖，属**搜索孤儿回收 / 进度导入**两个其他组。

**编译安全核对**：`BookDao.kt` 内 `BookContentDao`（line 141）、`BookFileDao`（line 163）仍使用 `@Insert(OnConflictStrategy.REPLACE)`，故 `Insert`/`OnConflictStrategy` 两个 import 在取掉 B2/B4 后**仍被使用**，不会产生"未使用 import"。这两个 DAO 的子表 `book_content`/`book_files` 是**子表**（无下层外键），REPLACE 安全，不需要改。

### 2.3 `InspirationDao.kt` — 2 个 hunk，全部属本组

`index 8f94c5d..6a24c68`，numstat **10 / 1**；SHA-256 `241A8CC5E4B2B6ADE12A905FE648665CC3002F07765A7437415831FECA781105`

| # | hunk header | 变更内容 | 属本组？ |
|---|---|---|---|
| I1 | `@@ -6,0 +7 @@` | `+import androidx.room.Upsert` | ✅ |
| I2 | `@@ -15 +16,9 @@` | `InspirationDao.upsert`：REPLACE → KDoc + `@Upsert`（−1/+9） | ✅（核心） |

`InspirationDao.kt` 其余 `REPLACE` 落在 **子表** 的 DAO：`InspirationVariantDao`（52）、`NoteDao`（64/68）、`HighlightDao`（100/104）— 均为子表，REPLACE 安全，**不改**，且保证 `OnConflictStrategy` import 仍被使用。

### 2.4 `TaxonomyDao.kt` — 4 个 hunk，全部属本组

`index 703fc97..7bea3ca`，numstat **13 / 3**；SHA-256 `9F638576203C54A23815BD2D5D577FE2D4F45CA72DDAA4798877CCB20AE38D8E`
⚠️ 该文件工作树含 **CRLF（238 对）**，根 `.gitattributes` 为 `* text=auto eol=lf` → 提交时会被规范化为 LF（见 §6 风险）。

| # | hunk header | 变更内容 | 属本组？ |
|---|---|---|---|
| T1 | `@@ -7,0 +8 @@` | `+import androidx.room.Upsert` | ✅ |
| T2 | `@@ -18 +19,8 @@` | `TagDao.upsert`：REPLACE → KDoc + `@Upsert`（−1/+8） | ✅（核心） |
| T3 | `@@ -51 +59,2 @@` | `CategoryDao.upsert`：REPLACE → 注释 + `@Upsert`（−1/+2） | ✅（核心） |
| T4 | `@@ -87 +96,2 @@` | `ShelfDao.upsert`：REPLACE → 注释 + `@Upsert`（−1/+2） | ✅（核心） |

`TaxonomyDao.kt` 其余 `REPLACE`（130/133 `BookTagDao`、165/168 `BookCategoryDao`、207/210 `ShelfBookDao`）落在**连接/子表**（`book_tag`/`book_category`/`shelf_book`），无下层外键，REPLACE 安全，**不改**。

### 2.5 `LibrarySourceRefDao.kt` — 0 个 hunk（已完成项）

HEAD 即为 `@Upsert`（`git show HEAD:…LibrarySourceRefDao.kt` line 14），工作树未修改（`git status` 无此文件）。故本组**不含该文件**，但它证明父表修复中"来源引用"一支已先行落地。

---

## 3. 候选 U 的版本号 hunk（`android/app/build.gradle.kts`）

`index 579bf24..cdbc4a2`，numstat **23 / 3**；SHA-256 `AAC93F05A51D801759BFD0A6678A1267F5D34DFB2B64132C0BF4FBEB63439606`
⚠️ 含 **CRLF（264 对）**，同样受 `text=auto eol=lf` 规范化影响。

该文件共 **5 个 hunk**，**只有第 2 个属本组**：

| # | hunk header | 变更内容 | 归属 |
|---|---|---|---|
| G1 | `@@ -13,0 +14,3 @@` | 新增 `testBuildType = providers.gradleProperty("craAndroidTestBuildType")…` | 其他功能（QA host 测试目标） |
| **G2** | `@@ -22,2 +25,6 @@` | **`versionCode` 2→3、`versionName` "0.5.0"→"0.5.1"** + 3 行注释（−2/+6） | ✅ **本组（版本号）** |
| G3 | `@@ -25 +32 @@` | `testInstrumentationRunner` → `…testing.CraIsolatedTestRunner` | 其他功能（沙箱 runner） |
| G4 | `@@ -97,0 +105,10 @@` | 新增 `create("qahost")` 构建类型 | 其他功能（S04 沙箱宿主） |
| G5 | `@@ -225,0 +243,3 @@` | `add("qahostImplementation", libs.androidx.compose.ui.test.manifest)` | 其他功能（qahost 依赖） |

**G2 精确内容**（原样）：

```diff
@@ -22,2 +25,6 @@ android {
-        versionCode = providers.gradleProperty("craVersionCode").orNull?.toInt() ?: 2
-        versionName = providers.gradleProperty("craVersionName").orNull ?: "0.5.0"
+        //
+        // 0.5.1(3)：数据丢失修复（父表 @Insert(REPLACE) → @Upsert，见 BookDao.upsert 注释）。
+        // 与 0.5.0(2) 的代码差异只在数据库写入层，界面完全一致，故必须靠版本号区分 ——
+        // 0.5.0(2) 在「我的 → 日志与诊断 → 应用版本」里显示为 0.5.0，本版显示 0.5.1。
+        versionCode = providers.gradleProperty("craVersionCode").orNull?.toInt() ?: 3
+        versionName = providers.gradleProperty("craVersionName").orNull ?: "0.5.1"
```

**为什么 G2 属候选 U 而不属 Room v15**：G2 的注释逐字写着"**父表 `@Insert(REPLACE)` → `@Upsert`**"，与 v15 的"删冗余索引"毫无关系。这是本报告要纠正的一处易错归属：**版本号 ≠ Room v15**。

**唯一零改动即可并存的边界**：G2 只改 `defaultConfig` 的两个常量，不引用任何新符号，可独立编译。

---

## 4. `ParentUpsertCascadeSafetyTest.kt` 的实际类型与既有原始验证

### 4.1 测试类型（关键）

| 项 | 值 |
|---|---|
| 路径 | `android/app/src/androidTest/java/com/creationreadingassistant/data/local/ParentUpsertCascadeSafetyTest.kt` |
| Git 状态 | **未跟踪新文件（`??`）** — 仅存在于工作树 |
| 运行器 | `@RunWith(AndroidJUnit4::class)` |
| 类型 | **androidTest（设备仪器测试）** — 不是 JVM 单元测试 |
| `@Test` 数 | **5** |
| 用例 | `upsertingBookPreservesAllChildRows`、`upsertingBooksInBatchPreservesChildRows`、`upsertingInspirationPreservesVariantsAndNoteLink`、`upsertingTaxonomyParentsPreservesJoinRows`、`softDeletePreservesChildRows` |
| 字节数 / SHA-256 | 16732 B / `8F26EEBC8C4204D052BFE562575E3148E47510202A2351C6576C97758CD8ADBE` |
| mtime | 2026-09-22 22:24:10 |

> **确认：不计入 JVM 3208。** 它位于 `src/androidTest/` 且用 `AndroidJUnit4`，由设备 instrumentation 运行；`./gradlew :app:testDebugUnitTest` 不会加载它。同类地，`AppDatabaseMigrationTest.kt`（23 个 `@Test`）也是 androidTest，**同样不计入 JVM 3208**。

### 4.2 既有原始验证（他人已完成，非本窗口）

| 项 | 值 |
|---|---|
| 运行时间 | **2026-09-23 00:06:07 → 00:06:09**，`EXIT=0` |
| 设备 | serial `c49ac6cf`，model `22081212C`，Android 15 / SDK 35 / MIUI V816（**真机，非模拟器**） |
| 代码基线 | HEAD `5aed5939600e491843e3c44453921bd6273a233c`（与本次一致） |
| 命令 | `adb -s c49ac6cf shell am instrument -w -r -e class …ParentUpsertCascadeSafetyTest com.creationreadingassistant.test/androidx.test.runner.AndroidJUnitRunner` |
| 结果 | `numtests=5`、completed 5、**failures/errors 0**、`OK (5 tests)`、`Time: 0.214`、`INSTRUMENTATION_CODE: -1` |
| 证据文件 | `docs/qa/evidence/2026-09-22-stage-c-database-device-acceptance/retry-01/50-instr-ParentUpsertCascadeSafetyTest.txt`（3701 B，SHA-256 `E035288E2E05BF6D2C3566AF2A7751B1F2CBF8EF86DB73C9A587E70B0C192C0C`） |
| 计时文件 | 同目录 `…50-instr-ParentUpsertCascadeSafetyTest.time`（SHA-256 `35AF59C0619D561A9E44725D92433737F3EFAB818224720B0A98EE42AA763458`） |
| 报告 | `docs/qa/2026-09/2026-09-22-stage-c-database-device-acceptance.md` §R6 / §R9 |

**证据与当前文件是否同一版本**：R6 记载该文件 mtime 为 `2026-09-22 22:24:10` 且"本轮未改"；本报告复核 **mtime 仍为 2026-09-22 22:24:10**，SHA-256 为 `8F26EEBC…`。即真机运行时刻（09-23 00:06）文件已定稿于 09-22 22:24，**运行内容 = 当前离线内容**。
⚠️ **但**：`agent-stage-b-database-audit.md` §8 记录的是另一枚哈希 `4baa6ddb5d907cdb651ff89bd0d0500a632ccfcb8968dbd8eb1e0736bf3c653b` — 那是**更早的一次快照**（在 09-22 22:24 之前）。**不得用 `4baa6ddb` 描述当前文件**；当前以 `8F26EEBC…` 为准。

---

## 5. 候选 G0 —— Room v15 冗余索引（与本组的边界）

来源：`2026-09-29-stage-e-four-window-review.md`（G0 必须把索引删除放进同一原子边界）。

| 文件 | 需要的部分 | 证据/说明 |
|---|---|---|
| `data/local/AppDatabase.kt` | 2 个 hunk：`@@ -70 +70 @@` `14→15`；`@@ -530,0 +531,19 @@` 新增 `MIGRATION_14_15`（`dropPartialIndexes` + `DROP INDEX IF EXISTS index_search_terms_term`） | SHA-256 `DABB1BE7576071DF25B4181DC70A44F43967A7A9B9C0F993C805E09619BAF1DD`（与 stage-B 审计一致） |
| `data/local/DatabaseModule.kt` | 3 个 hunk：两处注释更新 + `+AppDatabase.MIGRATION_14_15,` 注册 | SHA-256 `EC18FD068FF90D225993D5560A7DC8AFFB444383EB6E4CDF4062126FEC410CA1` |
| `data/local/dao/SearchTermDao.kt` | **仅第 5 个 hunk** `@@ -82 +73,9 @@`：删 `@Index(value=["term"], name="index_search_terms_term")` 改为解释性注释 | 见 §5.1 |
| `app/schemas/…AppDatabase/15.json` | 新文件（导出 schema） | 66574 B，mtime 2026-09-16 21:54:44，SHA-256 `C94CA0E506BFD4317039F37983B12668DD4BE50487248B7F7782AC732C85B407`（与 stage-B 审计一致，无漂移） |
| `androidTest/…/AppDatabaseMigrationTest.kt` | 新增的 311 行大 hunk（含 `migrate_14_to_15_drops_redundant_term_index`、`migrate_13_to_15_full_chain…`）+ 5 个小 import hunk；numstat 322/2 | 23 个 `@Test`；androidTest，**不计入 JVM 3208** |

> **G0 的迁移测试确已存在**：`AppDatabaseMigrationTest.kt:2043 migrate_14_to_15_drops_redundant_term_index` 与 `:2133 migrate_13_to_15_full_chain_keeps_old_data_and_creates_source_refs` 覆盖 14→15；retry-01 真机 `OK (23 tests)`（SHA-256 `B32A862603293ED6D5DC87C1FE2382C72E178E577B82C5B46141BC2EB1BCACBC`）。

### 5.1 `SearchTermDao.kt` 是**混合文件** —— 本组（U）完全不取它

`index 0aced60..54f9717`，numstat **21 / 12**；SHA-256 `B1FA1D1EBFA2BEB3875FAB71D2924E09869160E4EA724454836C660512B76A0A`。共 6 个 hunk：

| # | hunk header | 内容 | 归属 |
|---|---|---|---|
| S1 | `@@ -28,3 +27,0 @@` | 删 `deleteByChapter()` | 死代码清理（全仓无调用方）→ 非本组、非 G0 必需 |
| S2 | `@@ -35,2 +32,2 @@` | KDoc 改写（去掉对 `deleteByChapter` 的引用） | 注释，松耦合 |
| S3 | `@@ -41,3 +37,0 @@` | 删 `countTerms()` | 死代码清理 |
| S4 | `@@ -47,3 +40,0 @@` | 删 `rowsByTerm()` | 死代码清理 |
| **S5** | `@@ -82 +73,9 @@` | 删 `@Index(index_search_terms_term)` → 注释 | ✅ **G0（Room v15）** |
| S6 | `@@ -179,0 +179,10 @@` | 新增 `SearchIndexCoverageDao.distinctBookIds()` | 搜索孤儿回收功能 → 非本组、非 G0 必需 |

**调用方核对（支持"死代码/非本组"判断）**：
- `deleteByChapter`、`countTerms`、`rowsByTerm` 在 `android/**/*.kt` 中**无任何引用**（`grep` 仅命中 `rowsByTermForBasis`，是另一个方法）→ 删除属死代码清理，与数据安全无关。
- `distinctBookIds()`：`termDao.distinctBookIds()` 在 **HEAD 已存在**（HEAD `SearchTermDao.kt:53`），S6 新增的是 **`coverageDao.distinctBookIds()`**；调用方 `SearchIndexRepository.kt:605`（`termDao.distinctBookIds() + coverageDao.distinctBookIds()`）；两个 JVM 测试的 fake DAO 已实现该 override（`SearchIndexRepositoryRebuildConcurrencyTest.kt:86/117`、`SearchIndexRepositorySearchTest.kt:43/63`，均为**未跟踪新文件**）。

---

## 6. 本组与 G0 的依赖顺序

**结论：文件不相交，无硬依赖顺序。**

| 关系 | 判定 | 依据 |
|---|---|---|
| U 依赖 G0？ | **否** | U 只改 DAO 注解，不新增/删除 schema 列或索引；`@Upsert` 不改变 Room identity hash，不需要版本号迁移 |
| G0 依赖 U？ | **否** | G0 只改 schema 索引 + 版本常量 + 迁移注册；`SearchTermDao` 的写入方法本就是 `REPLACE`（子/派生表），与父表修复无关 |
| 文件交集 | **无** | U = {BookDao, InspirationDao, TaxonomyDao, ParentUpsert…Test, build.gradle.kts}；G0 = {AppDatabase, DatabaseModule, SearchTermDao, 15.json, AppDatabaseMigrationTest} |
| 版本号归属 | **U** | G2 注释逐字引用 `BookDao.upsert`；**不属 v15** |

**建议的先后（非强制，但风险最低）**：
1. **先 U**：它是数据丢失（不可逆）的最高危修复，且 schema 中性，可先独立落地；
2. **后 G0**：涉及 schema 版本与迁移，必须三件套（版本常量 + `MIGRATION_14_15` 注册 + 导出 schema）同时进，否则升级路径会崩。

**唯一需要同时满足的耦合**（都发生**在 G0 内部**，与 U 无关）：
- `AppDatabase.kt` 升 `version = 15`、`DatabaseModule.kt` 注册 `MIGRATION_14_15`、`schemas/15.json` 三者必须同批；缺一即"升级到 15 找不到迁移"或 schema 导出不一致。
- `SearchTermDao.kt` 的 **S5** 必须与上面三件套同批（否则 schema 哈希与实体不符）。

---

## 7. 混有其他功能的 hunk 清单（必须 `git add -p` 排除）

| 文件 | 需保留 | 必须排除的 hunk | 排除理由 |
|---|---|---|---|
| `BookDao.kt` | B1–B4 | **B5**（`getAllActive`/`activeIds`，+20） | 属"进度导入 / 搜索孤儿回收"两组 |
| `build.gradle.kts` | **G2** | **G1、G3、G4、G5** | QA-host / testBuildType / 沙箱 runner / qahost 依赖，属 S04 沙箱测试功能 |
| `SearchTermDao.kt` | （U 不取） | **S1、S2、S3、S4、S6** | 死代码清理 + 搜索孤儿回收；只有 S5 属 G0 |
| `InspirationDao.kt` | 全部 | 无 | 文件内其余 REPLACE 皆子表，未改 |
| `TaxonomyDao.kt` | 全部 | 无 | 文件内其余 REPLACE 皆连接/子表，未改 |

---

## 8. 最小可编译边界

> 下述为**静态推导**（注解/常量级改动 + 现有 import 保留核验），**非编译结论**。

| 边界 | 内容 | 编制理由 |
|---|---|---|
| **U-min** | `BookDao.kt`（B1–B4）+ `InspirationDao.kt`（I1–I2）+ `TaxonomyDao.kt`（T1–T4） | 纯注解替换；三个文件的 `Insert`/`OnConflictStrategy` import 仍被同文件子表 DAO 使用 → 无未使用 import；不引用任何新增符号 |
| **U-full** | U-min + `ParentUpsertCascadeSafetyTest.kt`（新文件） | 该测试只依赖 entity/`AppDatabase`，是无副作用的验证件（须整文件 `git add`） |
| **U-release** | U-full + `build.gradle.kts` G2 | 版本号是发版标识；G2 独立可编译 |
| **G0-min** | `AppDatabase.kt` + `DatabaseModule.kt` + `SearchTermDao.kt`(S5) + `schemas/15.json` | 缺任一件则升级/导出不一致 |
| **G0-full** | G0-min + `AppDatabaseMigrationTest.kt`（新增 311 行 hunk + import） | 迁移的三件套验证件 |

**U 与 G0 的并集**（U-release ∪ G0-full）同样自洽——因为文件不相交、无符号交叉引用（BookDao/Inspiration/Taxonomy 不引用 SearchTerm；AppDatabase 不引用上述 DAO 的新方法）。

**严格必要性分级**（回答"哪些是修复必需"）：
- 直接消除已复现"书/进度/灵感消失"症状：**`BookDao` B2/B4**（`books` 父表）为必需；`InspirationDao` I2 覆盖"灵感"子链（`inspirations.source_book_id` SET NULL / `inspiration_variants` CASCADE）；
- 同类预防：**`TaxonomyDao` T2/T3/T4**（标签/分类/书单被 REPLACE 抹掉是最易触发的同类数据丢失）；
- 版本号 G2 与测试文件不是"运行时修复必需"，但是**发布标识**与**验证件**，应随 U 一起提交。

---

## 9. 尚无证据的项目（HOLD）

| # | 缺口 | 状态 |
|---|---|---|
| H1 | **本候选未单独编译** | 未做（本窗口禁止构建）；§8 为静态推导 |
| H2 | 本候选**单独**的 `@Upsert` 运行语义 | 无——retry-01 的 5/5 跑的是**整棵工作树**产物，不能外推为"仅本候选即成立" |
| H3 | 本候选**单独**重跑 `ParentUpsertCascadeSafetyTest` | 未做；既有证据仅 2026-09-23 retry-01 一次；且本窗口禁止操作设备 |
| H4 | 5 个用例覆盖不到的路径 | stage-B 审计指出：`reader_text_rules`/`reader_text_corrections` 未播种、tags/categories/shelves 的 `softDelete` 未测、**同步合并路径未测** |
| H5 | v15（14→15）**单独**验证 | 无独立证据；retry-01 的 23/23 属整树基线 |
| H6 | 版本号 0.5.1(3) 的**界面显示**实测 | 无证据（G2 注释自述"我的→日志与诊断→应用版本"显示 0.5.1，未复核） |
| H7 | 行尾规范化影响 | `TaxonomyDao.kt`(238 CRLF)、`build.gradle.kts`(264 CRLF) 工作树为 CRLF，`text=auto eol=lf` 会在入库时改写；**尚无证据**表明该改写不影响 diff 归属，提交时需留意 |
| H8 | `BookDao` B5 / `SearchTermDao` S6 等**排除面**的功能 | 属其他组；本组不评 |
| H9 | 全仓首个 `@Upsert` 是否真为"唯一充分的父表修复" | 静态核对为"父表已全覆盖、其余 REPLACE 皆子/派生表"；**未做变异测试** |

**不做/不识别**：`AppDatabase.kt` 中 `dropPartialIndexes` 为既有辅助函数，本组不含它——`MIGRATION_14_15` 引用它，故 G0 提交时须确认该函数在 HEAD 已存在（否则会编译失败）。**未核**（超出本任务范围，登记为 G0 的待办）。

---

## 10. 只读性与越界声明

- **唯一写入的仓库文件**：本文件 `docs/coordination/legacy-stages/2026-09/2026-09-29-stage-e-upsert-version-candidate.md`。
- 未修改任何产品源码、测试、Gradle、schema、脚本、账本或其他窗口报告。
- 未 `stage`/`commit`/`push`/`reset`/`checkout`/`clean`；未构建；未连接或操作设备；未进入原始仓库。
- 大清单（逐 hunk 台账）写入 E 盘，见文末。
- **未宣称"已独立编译"或"已独立通过测试"。**

---

## 11. 大清单（E 盘）

- 路径：`E:\creation-reading-assistant\codex-e-preflight-20260929\stage-e-upsert-version-candidate.csv`
- 列：`group,file,hunk_index,hunk_header,change,in_candidate,reason,source_sha256,evidence,note`
- 行数：**32 行 = 1 表头 + 31 数据行**（U 18 行、G0 13 行）
- SHA-256：`FCCEBDE2E769F6712C60C13246E9BEC3503B7EFE1496740EB65BA958879C39FF`
- 生成时刻即本报告定稿时刻；CSV 在 E 盘、**不在仓库内**（不进 git 状态）

---

## 12. 交接回执

- 报告路径：`docs/coordination/legacy-stages/2026-09/2026-09-29-stage-e-upsert-version-candidate.md`
- E 盘台账：`E:\creation-reading-assistant\codex-e-preflight-20260929\stage-e-upsert-version-candidate.csv`（32 行，SHA-256 `FCCEBDE2E769F6712C60C13246E9BEC3503B7EFE1496740EB65BA958879C39FF`）
- 精确文件/hunk：见 §2（U：BookDao B1–B4、InspirationDao I1–I2、TaxonomyDao T1–T4）、§3（build.gradle.kts G2）、§5（G0：AppDatabase 2 hunk、DatabaseModule 3 hunk、SearchTermDao S5、15.json、MigrationTest 大 hunk）
- 对应验证原件：`docs/qa/evidence/2026-09-22-stage-c-database-device-acceptance/retry-01/50-instr-ParentUpsertCascadeSafetyTest.txt`（`OK (5 tests)`）、`51-instr-AppDatabaseMigrationTest.txt`（`OK (23 tests)`）、`docs/qa/2026-09/2026-09-22-stage-c-database-device-acceptance.md`
- HOLD 项：§9 H1–H9
