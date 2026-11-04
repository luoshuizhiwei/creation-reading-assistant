# 阶段 E — G0 精确隔离候选（Room v15 索引迁移）（2026-09-29）

## 状态

G0 候选已在 E 盘隔离目录完成编译与 JVM 单元测试。候选边界精确到 hunk 级，
`SearchTermDao.kt` 仅取 `@Entity(indices)` 的索引删除 hunk，不混入该文件其余 5 个
DAO/coverage hunk。未修改恢复仓库的源码、Git index 或工作树；未 stage/commit/push/reset/checkout；
未使用 MuMu、真实手机或正式书库。

---

## 1. 候选定义

### 1.1 基线

| 项 | 值 |
|---|---|
| 恢复仓库 HEAD | `5aed5939600e491843e3c44453921bd6273a233c` |
| 分支 | `codex/recover-worktree-20260920` |
| 隔离目录 | `E:\creation-reading-assistant\stage-e-g0-isolated-20260929\` |
| 导出方式 | `git archive --format=tar HEAD` → tar 解包至隔离目录 |
| 版本号 | **不并入 G0**：`build.gradle.kts` 的 `versionCode 2→3` / `versionName "0.5.0"→"0.5.1"` 属 G1，不在本候选 |

### 1.2 精确文件/hunk 清单

| # | 文件 | Git 状态 | 选取 hunk | 说明 |
|---|---|---|---|---|
| 1 | `android/app/src/main/java/com/creationreadingassistant/data/local/AppDatabase.kt` | ` M`（已跟踪，工作树修改） | **全部 2 个 U0 hunk** | 见 §1.3 |
| 2 | `android/app/src/main/java/com/creationreadingassistant/data/local/DatabaseModule.kt` | ` M` | **全部 3 个 U0 hunk** | 见 §1.3 |
| 3 | `android/app/schemas/com.creationreadingassistant.data.local.AppDatabase/15.json` | `??`（未跟踪新文件） | **整文件** | Room schema 导出 v15 |
| 4 | `android/app/src/main/java/com/creationreadingassistant/data/local/dao/SearchTermDao.kt` | ` M` | **仅 1 个 U0 hunk**（6 个中的第 5 个） | 见 §1.3 — 其余 5 个 hunk 不属 G0 |
| 5 | `android/app/src/androidTest/java/com/creationreadingassistant/data/local/AppDatabaseMigrationTest.kt` | ` M` | **全部 8 个 U0 hunk** | 见 §1.3 |

### 1.3 逐文件 hunk 头

#### AppDatabase.kt — 2 hunk（全部 G0）

```
@@ -70 +70 @@     APP_DATABASE_SCHEMA_VERSION = 14 → 15
@@ -530,0 +531,19 @@   新增 MIGRATION_14_15（dropPartialIndexes + DROP INDEX IF EXISTS `index_search_terms_term`）
```

#### DatabaseModule.kt — 3 hunk（全部 G0）

```
@@ -50 +50 @@           注释 13→14 → …13→14、14→15
@@ -52 +52,2 @@         注释增「14→15 只删冗余索引」
@@ -70,0 +72 @@         注册 AppDatabase.MIGRATION_14_15,
```

#### SearchTermDao.kt — 6 个 U0 hunk 中仅取第 5 个

| hunk # | U0 头 | 内容 | 归属 |
|---|---|---|---|
| 1 | `@@ -28,3 +27,0 @@` | 删除 `deleteByChapter` 方法 | **非 G0**（DAO 方法删除） |
| 2 | `@@ -35,2 +32,2 @@` | `deleteByBook` 注释改写 | **非 G0**（随 hunk 1） |
| 3 | `@@ -41,3 +37,0 @@` | 删除 `countTerms` 方法 | **非 G0**（DAO 方法删除） |
| 4 | `@@ -47,3 +40,0 @@` | 删除 `rowsByTerm` 方法 | **非 G0**（DAO 方法删除） |
| **5** | **`@@ -82 +73,9 @@`** | **删除 `Index(value = ["term"], name = "index_search_terms_term")`，替换为 9 行注释** | **G0** |
| 6 | `@@ -179,0 +179,10 @@` | `SearchIndexCoverageDao` 新增 `distinctBookIds` | **非 G0**（coverage 查询新增） |

候选中 `SearchTermDao.kt` 保留 HEAD 的 `deleteByChapter` / `countTerms` / `rowsByTerm` 方法和
`SearchIndexCoverageDao` 原貌，仅删除 `index_search_terms_term` 索引声明。

#### AppDatabaseMigrationTest.kt — 8 hunk（全部 G0）

```
@@ -2,0 +3,2 @@          import Context, Room
@@ -3,0 +6 @@            import SupportSQLiteDatabase
@@ -4,0 +8 @@            import ApplicationProvider
@@ -7,0 +12 @@           import assertFalse
@@ -17 +22,2 @@          类注释增 14→15 / 13→15 / 开库回调
@@ -22 +28,2 @@          注释增 openingDatabaseRebuilds… 交叉引用
@@ -29,0 +37,2 @@        OPEN_CALLBACK_DB 常量
@@ -2019,0 +2029,311 @@  311 行新增：3 个 @Test + indexExists 辅助 + tearDownOpenCallbackDb
```

新增的 3 个 androidTest 用例：

1. `migrate_14_to_15_drops_redundant_term_index` — v14→v15 单段迁移验证
2. `migrate_13_to_15_full_chain_keeps_old_data_and_creates_source_refs` — v13→v15 历史链
3. `openingDatabaseRebuildsPartialIndexesAndDoesNotResurrectTermIndex` — 正式开库回调（守门测试）

---

## 2. 输入 SHA

### 2.1 Git blob SHA（HEAD）

| 文件 | blob SHA-1（HEAD） |
|---|---|
| `AppDatabase.kt` | `cae7edc192e6089b0239c0bbb9f964cf8f4c0bd9` |
| `DatabaseModule.kt` | `3c32cf2cbbafd181e93f047adb0d14754009dca3` |
| `SearchTermDao.kt` | `0aced601902eec7cdcf2eb86a454eea39b0e6724` |
| `AppDatabaseMigrationTest.kt` | `33f29fa3128cc4dbc475accf4cd72e0a966de95b` |
| `14.json`（参照） | `d6f3f00f0008714cb2d4caecca2521937d6126d0` |
| `15.json` | HEAD 不存在（未跟踪新文件） |

### 2.2 文件内容 SHA-256

| 文件 | HEAD（基线） | 工作树（恢复仓库） | 候选（隔离目录） |
|---|---|---|---|
| `AppDatabase.kt` | `BD20BDF6…CE4192` | `DABB1BE7…BAF1DD` | `DABB1BE7…BAF1DD` |
| `DatabaseModule.kt` | `1EBD970F…BCD0D9` | `EC18FD06…410CA1` | `EC18FD06…410CA1` |
| `SearchTermDao.kt` | `3354E1C0…14FD619` | `B1FA1D1E…2B76A0A` | `038ADC4E…158721CE` |
| `AppDatabaseMigrationTest.kt` | `4AE5CAA4…2EF8D823` | `9DD237E4…04B571F` | `9DD237E4…04B571F` |
| `15.json` | —（不存在） | `C94CA0E5…2C85B407` | `C94CA0E5…2C85B407` |

> `SearchTermDao.kt` 候选 SHA ≠ 工作树 SHA：候选仅含索引 hunk，工作树含全部 6 个 hunk。
> 其余 4 个文件候选 = 工作树（全部 hunk 均属 G0）。

---

## 3. 编译结果

### 3.1 命令

```powershell
# 隔离目录: E:\creation-reading-assistant\stage-e-g0-isolated-20260929\android
# JAVA_HOME = D:\develop\Java\jdk-17.0.14 (JDK 17.0.14 LTS)
# GRADLE_USER_HOME = 默认 (C:\Users\23254\.gradle) — 复用既有依赖缓存
# --project-cache-dir = E:\creation-reading-assistant\stage-e-g0-isolated-20260929\android\.gradle-project-cache

.\gradlew.bat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin `
  --project-cache-dir="E:\creation-reading-assistant\stage-e-g0-isolated-20260929\android\.gradle-project-cache" `
  --no-daemon
```

### 3.2 结果

| 项 | 值 |
|---|---|
| 退出码 | **0** |
| 状态 | **BUILD SUCCESSFUL in 2m 46s** |
| `:app:compileDebugKotlin` | SUCCESS（1 条 warning：`Icons.Outlined.HelpOutline` deprecated — 非 G0 相关） |
| `:app:compileDebugAndroidTestKotlin` | SUCCESS |
| actionable tasks | 30（23 executed, 7 from cache） |
| 日志 | `evidence\compile-g0-candidate.log` |
| 日志 SHA-256 | `6D98B234E8B56CCA8246ABDB36D5B10DE8D7A6621028B09C1438D37E0FBEE4FD` |

**结论：G0 候选 app + androidTest 均编译通过。**

---

## 4. JVM 单元测试结果

### 4.1 命令

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --project-cache-dir="E:\creation-reading-assistant\stage-e-g0-isolated-20260929\android\.gradle-project-cache" `
  --no-daemon
```

### 4.2 结果

| 项 | 值 |
|---|---|
| 退出码 | **0** |
| 状态 | **BUILD SUCCESSFUL in 5m 2s** |
| XML 文件数 | 259 |
| 总测试数 | **2286** |
| failures | **0** |
| errors | **0** |
| skipped | **0** |
| 日志 | `evidence\test-g0-candidate-jvm.log` |
| 日志 SHA-256 | `86465ACA11D0EC5B6E488D5B0A657D4D74C6AC7641B6C8CF2DE346C7F9F5F84E` |
| XML 原件 | `evidence\jvm-test-results\*.xml`（259 个文件） |

> **2286 ≠ 3208**：3208 是恢复仓库**完整工作树**（HEAD + 全部未提交改动）的 JVM 测试数；
> 2286 是 **HEAD + G0 hunk** 的 JVM 测试数。G0 不新增 JVM 测试（3 个新用例均在 `src/androidTest/`），
> 故 2286 即 HEAD 基线的 JVM 测试数。全部通过 ⇒ G0 不破坏任何既有 JVM 测试。

### 4.3 测试不接触正式数据

JVM 单元测试使用 Robolectric/in-memory fixture，不读写设备正式书库。androidTest 迁移测试
使用独立库名（`migration-test`、`migration-open-callback-test`），与主应用库
`creation_reading_assistant_native` 隔离，且 `tearDownOpenCallbackDb` 在前后各删一次。

---

## 5. 仍需设备验证的测试

以下 3 个 androidTest 用例在 `AppDatabaseMigrationTest.kt` 中，需真实 Android 设备运行
（`MigrationTestHelper` + Room 真开库），**不在 JVM 范围内**，本轮未运行：

| # | 用例 | 验证内容 | 守门作用 |
|---|---|---|---|
| 1 | `migrate_14_to_15_drops_redundant_term_index` | v14→v15 单段：冗余索引删除、`index_search_terms_book` 保留、数据行不变 | 迁移本体 |
| 2 | `migrate_13_to_15_full_chain_keeps_old_data_and_creates_source_refs` | v13→v15 历史链：旧数据保留、`library_source_refs` 新表建立、CASCADE 外键 | 跨段链路 |
| 3 | `openingDatabaseRebuildsPartialIndexesAndDoesNotResurrectTermIndex` | 正式开库：`onUpgrade` + `CreateIndexCallback.onOpen`，部分索引重建、term 索引不复活 | **Schema 校验守门** — Room `TableInfo.equals` 对 `indices` 做集合相等，若实体保留 `index_search_terms_term` 而迁移 DROP 了它，开库即崩 |

> 用例 3 是 [hold-data-sync §2.1](2026-09-29-stage-e-hold-data-sync.md) 所述「升级后首次开库即崩」
> 约束的守门测试。它走 `Room.databaseBuilder(...).build().openHelper.writableDatabase` 真开库，
> 会触发 `onValidateSchema`。**必须在真实设备上验证后才可声明 G0 数据安全。**

---

## 6. 环境与基线说明

| 项 | 说明 |
|---|---|
| 基线导出 | `git archive --format=tar HEAD` 导出 5 个中文命名文档文件失败（`docs/archive/mobile-legacy/` 下），不影响 Android 构建 |
| Gradle distribution | 8.9，复用 `C:\Users\23254\.gradle\wrapper\dists\` 既有缓存 |
| Gradle 依赖缓存 | 复用 `C:\Users\23254\.gradle\caches\`（157 个模块目录）；项目级 cache 在 E 盘 |
| 构建输出 | `E:\creation-reading-assistant\stage-e-g0-isolated-20260929\android\app\build\`（E 盘） |
| `local.properties` | 从恢复仓库复制 `sdk.dir=D\:\\develop\\Android\\Sdk` |
| 编码 | `gradle.properties` 已带 `kotlin.daemon.jvmargs=-Dfile.encoding=UTF-8`；隔离目录路径全 ASCII，无编码问题 |
| 基线编译能力 | 候选编译通过 ⇒ 基线 + G0 自足；未单独编译裸基线（HEAD），因 G0 overlay 仅 5 文件且编译无缺符号报错 |

---

## 7. 候选精确边界总结

```
G0 = HEAD (5aed593)
  + AppDatabase.kt            [全 2 hunk: v15 + MIGRATION_14_15]
  + DatabaseModule.kt         [全 3 hunk: 注释 + 注册]
  + 15.json                   [新文件]
  + SearchTermDao.kt          [仅 hunk 5/6: 删 index_search_terms_term]
  + AppDatabaseMigrationTest.kt [全 8 hunk: 3 个 v15 androidTest]
```

**不包含**：
- `build.gradle.kts` 版本号 hunk（G1，`versionCode`/`versionName`）
- `SearchTermDao.kt` 其余 5 个 hunk（`deleteByChapter`/`countTerms`/`rowsByTerm` 删除、注释改写、`distinctBookIds` 新增）
- 任何其他 DAO、repository、UI、配置或文档改动

---

## 8. 原始证据路径

| 证据 | 路径 |
|---|---|
| 隔离目录 | `E:\creation-reading-assistant\stage-e-g0-isolated-20260929\` |
| 编译日志 | `E:\creation-reading-assistant\stage-e-g0-isolated-20260929\evidence\compile-g0-candidate.log` |
| 测试日志 | `E:\creation-reading-assistant\stage-e-g0-isolated-20260929\evidence\test-g0-candidate-jvm.log` |
| 测试 XML | `E:\creation-reading-assistant\stage-e-g0-isolated-20260929\evidence\jvm-test-results\*.xml`（259 文件） |
| Gradle 项目缓存 | `E:\creation-reading-assistant\stage-e-g0-isolated-20260929\android\.gradle-project-cache\` |
| 构建产物 | `E:\creation-reading-assistant\stage-e-g0-isolated-20260929\android\app\build\` |

---

## 9. 只读边界声明

- 未修改恢复仓库的源码、Git index 或工作树。
- 未 stage、commit、push、reset、checkout。
- 未使用 MuMu 模拟器、真实手机或正式书库。
- 唯一写入的仓库文件：本文件。
- E 盘隔离目录和证据由本轮独占。
- 其他窗口同时分析不同候选，本文件不覆盖其报告。
