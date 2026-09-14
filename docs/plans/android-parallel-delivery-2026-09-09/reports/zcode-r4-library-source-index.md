# zcode 交付报告：来源索引与增量更新（目录路线第 3 组）

日期：2026-09-13（构建完成）。执行目录：主仓库 `android/`。
依据：[`docs/plans/2026-09-12-android-library-folder-smart-recognition-roadmap.md`](../../2026-09-12-android-library-folder-smart-recognition-roadmap.md)（需求与方案 §4.5/§5.6/§7.5/§10 第 3 组）、
接手包 [`docs/plans/android-parallel-delivery-2026-09-09/R4-ZCODE.md`](../R4-ZCODE.md)、
前序报告 [`reports/workbuddy-r4-library-folder.md`](workbuddy-r4-library-folder.md)（第 1–2 组）。

起点：`main` @ `70ef712cb7d5fbb0d11cc3c85077d55f0b08d59c`，工作区 285 项未提交改动（全部视为用户资产，
未 reset / checkout / clean / stash / commit / push，未做全仓格式化，未触碰 `src/`、`electron/`、`package.json`）。

## 0. 结论速览

| 项 | 结果 |
| --- | --- |
| `library_source_refs` 表 + Room migration + schema 快照 + 迁移测试 | **完成**（v13→v14，`android/app/schemas/.../14.json` 已生成并与迁移 SQL 逐字段核对一致） |
| ShelfImporter 导入成功后写来源引用（系统选择器不伪造 rootId） | **完成**（best-effort 写入；rootId 仅在可证明归属授权树时写入，否则 null） |
| 弱判定升级为方案 §5.6 四级判定 + UI 文案区分疑似/确认 | **完成**（页签与行内文案随判据分层；确认档支持单击打开书籍） |
| >32 MiB 超大文件「大小 + 首尾分块哈希」候选指纹 | **完成**（导入与识别两条路径；仅用于去重，不作安全签名） |
| `:app:testDebugUnitTest` | **253 套件 / 2238 tests / 0 failures / 0 errors / 0 skipped**（基线 250/2200） |
| `:app:compileDebugAndroidTestKotlin` | **PASS**（本轮新增 13→14 迁移用例编译通过；未在设备上执行） |
| `:app:lintDebug` | **0 errors / 4 warnings**，4 条全部为既有告警（3× `ObsoleteLintCustomCheck` 依赖 jar、1× `UnusedResources` 指向 `strings_annotations.xml:37`），无一条出自本轮文件 |
| 真机验收 | **未做**（等用户给出设备时段；两项硬指标与覆盖清单见 §7） |
| commit / push | **未做**（除非用户明确要求） |

## 1. 数据层：`library_source_refs`（v13 → v14）

- 新增实体 `data/local/entity/LibrarySourceRefEntity.kt`，字段与方案 §4.5 一致：
  `book_id`（主键）/ `root_id` / `provider_authority` / `document_id` / `display_name` / `format` /
  `size` / `last_modified` / `content_hash` / `candidate_fingerprint`（超出方案字段清单的最小补充，见 §2）/ `last_seen_at` / `availability`（`available`/`missing`）。
- 外键 `book_id → books.id ON DELETE CASCADE`：删除书架项级联清除引用，不会阻止重新导入，
  也不触碰来源目录里的任何原文件（真机硬指标之一的数据层保证）。
- 索引：`(provider_authority, document_id)`、`content_hash`、`candidate_fingerprint`、`root_id`；
  名称与 Room 生成的 `index_library_source_refs_*` 完全一致，迁移后 schema 校验通过。
- `APP_DATABASE_SCHEMA_VERSION = 14`，`MIGRATION_13_14` 只建新表 + 索引，不 ALTER 任何既有表；
  开头按既有约定先 `dropPartialIndexes`。迁移 SQL 与 KSP 生成的 `14.json` `createSql`
  逐字段（列序、NOT NULL、主键、外键、索引名）核对一致。
- 该表不是正文事实源：来源不可用时阅读器仍只依赖内部稳定副本（方案 §4.5 末段）。

**比较基线语义（重要设计决定）**：`size` / `last_modified` / `content_hash` 固定为**导入时**的观测值；
日常观测刷新（目录浏览、识别扫描）只更新 `last_seen_at` 与 `availability`，绝不回写这三列——
否则「内容有更新」的比较基线会在第一次刷新后丢失。

**迁移测试**：`AppDatabaseMigrationTest.migrate_13_to_14_creates_library_source_refs`——
建 v13 库并插入书籍 → 迁移 13→14 → 验证新表可写可读、可空列（root_id 等）语义、四个索引存在、
外键 CASCADE 生效（删书后引用清零）、既有表不受影响。该用例已通过 `compileDebugAndroidTestKotlin` 编译，
**尚未在真机上执行**（instrumentation 需要设备）。

## 2. 写入路径：ShelfImporter

- 构造注入 `LibrarySourceIndex` 与 `LibraryRootStore`（均在 `feature/library` 所有权内）。
  `LibraryRootStore` 新增 `suspend fun loadRoot()`：直接读 DataStore，
  规避 `root: StateFlow`（`WhileSubscribed`）在无订阅者时 `.value` 为过期 `null` 的问题。
  每个导入批次只读一次根配置。
- `importOne` 元数据阶段新增 `uriLastModified(uri)`（`last_modified` 列，缺失/为 0 返回 null）；
  `>32 MiB` 时调用 `SourceFingerprint.compute`（见 §4），不再依赖文件名+大小弱指纹兜底。
- 导入成功后 `recordSourceRef(...)`：upsert 一条 `availability=available`、`last_seen_at=now` 的引用。
  **best-effort**：写入包在 `runCatching` 内，来源索引不是阅读事实源，失败只损失判定基线，不让导入报错。
  重复导入（duplicate 结局）不写引用。
- **不伪造 rootId**：`sourceRootIdFor(uri, root)` 只在 authority 与配置根一致且 documentId 以树根 id
  为前缀并停在边界（`primary:Books` 不会误吞 `primary:Books2`）时返回树根 id；网盘/downloads 等
  不透明 id provider、系统选择器任意位置文件一律 `root_id = null`。JVM 测试锁定了
  「JVM 环境无根配置时 root_id/document_id 留空、provider_authority 不编造」。
- **指纹去重**：`duplicate` 条件新增「同候选指纹的引用仍挂在书架在册书籍上」——引用随书架删除级联清除，
  因此不会阻止重新导入已被移除的书；软删除书行的引用也不会误判（`booksProvider()` 过滤在册集合）。
  最终去重裁决仍在导入时完成，弱判定不跳过导入（契约 3）。
- `candidate_fingerprint` 列是方案 §4.5 清单之外的**最小补充**：`content_hash` 列继续只存全量 MD5
  （≤32 MiB），把候选指纹混入会造成「全量哈希 vs 首尾分块」两种语义互相误判。已在报告与代码注释中说明。

## 3. 判定升级（方案 §5.6）与 UI 文案

`LibraryBrowserPolicy` 重写（保持纯函数，参数只吃基本类型与 `LibrarySourceRef` 快照，JVM 可测）：

| 级别 | 判据 | UI 呈现 | bookId |
| --- | --- | --- | --- |
| ① | `authority + documentId` 与引用一致 | 「已在书架」（`AppSuccess`，确认口径） | 有 |
| ④ | 同位置但大小/修改时间相对导入基线变化 | 「内容有更新」（`AppWarning`） | 有 |
| ② | 候选指纹一致（仅 >32 MiB 识别路径可得） | 「已在书架」（确认口径） | 有 |
| ③ | 格式 + 文件名（忽略大小写）+ 大小一致 | 「疑似已在书架」（`HintTint`） | **无** |
| 弱兜底 | 文件名主体 vs 书架标题（原 `isWeakDuplicate`） | 「疑似已在书架」 | **无** |

- 多条引用命中时取最强证据（priority：① < ④ < ② < ③）；书架软删除书的引用不参与判定。
- UI 随判据同步：
  - 识别页签 `IN_SHELF` 由「疑似入架」改名「**已入架**」（方案 6.3 原名，行内文案继续区分确认/疑似）；
  - 识别行内提示按判据给出五条可解释文案（如「已在书架：来源位置与已入架记录一致」「内容指纹与已入架版本一致（可能是改名或移动后的同一文件）」「疑似已在书架：名称与大小一致，导入时以内容哈希为准」）；
  - 目录页文件行同样显示分级 MicroTag；
  - `matchesFilter` / `selectedByDefault` 改吃 `LibraryShelfMatch?`：推荐页签排除**一切**有入架证据的项，默认勾选口径不变（推荐且无任何证据）。
- **确认档单击打开书籍**（方案 4.2 由第 3 组解锁的遗留项）：`LibraryShelfMatch.bookId` 非空（①④②）时，
  浏览态单击文件行 `navigate("reader/$bookId")`——复用 AppNavigation 既有路由，**未改动 AppNavigation.kt**；
  多选态单击仍是切换选择；③弱判定永远拿不到 bookId，不猜。
- 判定升级不改变导入行为：弱判定不作为跳过导入的依据，最终去重仍由 `ShelfImporter` 裁决（契约 3）。

## 4. 超大文件候选指纹（`SourceFingerprint`）

- 触发条件：`size > 32 MiB`（与既有全量 MD5 上限同界，两个分块永不重叠）。
- 指纹格式 `fp1|<size>|<head Md5>|<tail Md5>`（256 KiB 首尾分块），带版本号——未来换参数时旧值自然失配，
  不会跨版本误判「同内容」。**只用于去重候选提示与来源索引比对，不是安全签名**；
  真正覆盖原书内容前仍需完整导入校验（本实现中指纹只参与 duplicate 判定与提示，不参与任何内容覆盖）。
- 读取量上界明确：头尾各 256 KiB，两次独立流，任一流读不满一个完整块（provider 上报大小不可信）即放弃指纹
  ——宁可没有指纹，不基于残缺数据生成同内容结论。全部调用可随协程取消停止（`ensureActive`）。
- 两条接入路径：
  - 导入（`ShelfImporter`）：元数据阶段计算并存入引用 `candidate_fingerprint`；
  - 识别（`SafSmartBookRecognizer.probe`）：大文件把头部读取扩大到一个指纹块（一次流同时产出 64 KiB
    分类样本与指纹头块），再开第二流跳读尾块；指纹随候选下发，用于识别结果的②级判定。
    识别仍不做全量哈希（方案 §8.1），并发仍为顺序 ≤2，样本上限语义不变。

## 5. 来源观测与「来源失效」（轻量刷新，无轮询）

- **观测刷新（单目录）**：目录列成功后把本目录看到的引用 `markObserved`——更新 `last_seen_at`、
  翻回 `available`、并在能证明归属时回填缺失的 `root_id`。**绝不标记 missing**（单目录列表无法证明
  文件不在树的其他位置）。
- **扫描对账（识别完成）**：`reconcileAfterScan(allowMissing = !summary.truncated)`——只有**完整未截断**
  扫描才允许把「同根、此前 available、本次未出现、documentId 非空」的引用标记 `missing`（方案 §8.3
  来源状态展示）；截断扫描只做观测刷新。判定计划是纯函数（`SourceReconciliation.plan`），JVM 测试锁定：
  其它根、null rootId、null documentId、已 missing 的引用都不参与失效结论。
- 刷新时机仅：进入/刷新目录（显式刷新按钮）、识别开始、识别完成——符合「重新进入页面后轻量校验」，
  **零新增定时轮询**（契约 6）。
- 观测/对账全部 `runCatching`：观测失败不影响浏览与识别主流程。

## 6. 门禁结果（真实数字，非 grep 推断）

命令（Bash 直调 wrapper，`--no-daemon`）：

```bash
cd android
GRADLE_USER_HOME=D:/develop/env/gradle JAVA_HOME=D:/develop/Java/jdk-17.0.14 \
  "$JAVA_HOME/bin/java" -Dorg.gradle.appname=gradlew \
  -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
  :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug --no-daemon
```

- **`:app:testDebugUnitTest`：253 套件 / 2238 tests / 0 failures / 0 errors / 0 skipped**
  （解析 `app/build/test-results/testDebugUnitTest/*.xml` 汇总；基线 250 套件 / 2200 tests，
  本轮净增 3 套件 / 38 用例）
- **`:app:compileDebugAndroidTestKotlin`：PASS**（含新增 13→14 迁移用例；本轮第 4 次运行实际执行成功）
- **`:app:lintDebug`：0 errors / 4 warnings**（解析 `lint-results-debug.xml` 逐条核对：
  3× `ObsoleteLintCustomCheck` 来自依赖 jar（Compose runtime / Compose ui / lifecycle-livedata-core）、
  1× `UnusedResources` 指向 `strings_annotations.xml:37`——与基线相同的既有告警，无一条出自本轮文件）
- 最终运行 `BUILD SUCCESSFUL in 4m 38s`。

**迭代过程如实记录**（共 6 次运行）：run1 主代码 3 处编译错误（suspend 上下文/扩展函数调用语法/重复 import，
本人引入，当轮修复）；run2 KSP 因文件头损坏失败（上轮编辑误伤，修复）；run3 KSP
`failed to make parent directories`（基础设施错误，与代码无关，清理 `app/build/generated/ksp` 与
`app/build/kspCaches` 后恢复）；run4 测试编译错误（测试参数类型，修复）；run5 1 条断言失败
（测试数据写错：③级判据要求文件名一致，本人用例数据笔误，修复）；run6 全绿。
**这是开发检查点，不是发布结论。**

## 7. 真机验收（未执行）

等用户给出设备时段后按方案 §11.2 执行：`adb devices` 先核对 serial，所有命令 `adb -s <serial>`；
不用 MuMu；长测前记录 `stay_on_while_plugged_in` 原值并恢复；报告只用「测试 TXT」「测试 EPUB」。
除前两组既有清单外，本轮重点补验两项硬指标与新增能力：

1. **删除书架项不删除来源原文件**（引用级联清除 + 来源文件零触碰）；
2. **来源撤销后内部副本仍可阅读 / 搜索 / TTS**（索引不参与阅读事实源）；
3. 重启后：导入过的来源文件在目录页显示「已在书架」，改名后识别显示「已在书架（内容指纹一致）」
   （>32 MiB 用测试大 TXT 验证指纹路径），同位置覆盖新文件显示「内容有更新」；
4. 新导入完成后目录页/识别页的精确档位与弱「疑似」文案区分；
5. 确认档单击打开对应书籍（reader 路由往返）；
6. 13→14 迁移在真实升级路径上（旧版本覆盖安装）不崩、书架数据完整。

## 8. 未完成与下一步

1. **真机验收**（§7），未执行任何设备命令。
2. 手势级下拉刷新（方案 4.2）仍是显式刷新按钮（前组降级项，未动）。
3. 识别结果预览页（方案 4.4，P1）未做。
4. `missing` 来源目前只在识别结果/目录行的判据里体现（精确档才会带出），方案 §4.4 的
   「来源状态」专列展示属 P1 范畴。
5. 引用快照按书架规模上界（一书一条）内存匹配；书架数千级时如需优化可改 DAO 定向查询（未做，当前规模足够）。

## 9. SEAM 与越界情况

- **无 SEAM REQUEST**：全部改动落在任务包 §5 授权区域内。
- 未改动 `ui/navigation/AppNavigation.kt`（单击打开复用既有 `reader/{bookId}` 路由常量）、
  未改动 `feature/reader/**`、`feature/search/**`、`data/repository/**`（仅继续注入既有 `BookRepository`）、
  `ui/screen/reader/**`、`ui/screen/profile/**`、`*.gradle.kts`、根 `strings.xml`。
- 未触碰 `src/`、`electron/`、`package.json`、`package-lock.json`（桌面端）。
- `ui/screen/shelf/{ShelfImportRoute,ShelfRoute,ImportSourceSheet,ShelfSharedComponents}.kt` 本轮**零改动**。
- 触碰过的共享文件只有 `ui/viewmodel/ShelfViewModelTest.kt`（仅因 `ShelfImporter` 构造新增参数而补两个
  mock 参数，不断言新行为）与 `AppDatabaseMigrationTest.kt`（追加用例，未改既有用例）——均在任务包
  授权的测试范围与 `AppDatabase.kt`/`DatabaseModule.kt` 连带范围内。

## 10. 逐文件改动清单

**新增（6）**

| 文件 | 说明 |
| --- | --- |
| `data/local/entity/LibrarySourceRefEntity.kt` | 实体 + `LibrarySourceAvailability` |
| `data/local/dao/LibrarySourceRefDao.kt` | upsert / getAll / getByFingerprint / 观测与失效更新 / rootId 回填 |
| `feature/library/LibrarySourceIndex.kt` | 索引 Module + 领域模型 + `sourceKeyOf`/`sourceRootIdFor` + 对账纯函数 |
| `feature/library/SourceFingerprint.kt` | 大小+首尾分块候选指纹（fp1 格式） |
| `app/src/test/.../feature/library/SourceFingerprintTest.kt` | 8 用例：MD5 向量、阈值边界、格式稳定性、双流读取、失败容错 |
| `app/src/test/.../feature/library/LibrarySourceIndexTest.kt` | 9 用例：对账计划（seen/missing/backfill 边界）+ 索引执行 |

**修改（16）**

| 文件 | 改动 |
| --- | --- |
| `data/local/AppDatabase.kt` | v13→v14、实体/DAO 注册、`MIGRATION_13_14`、版本历史注释 |
| `data/local/DatabaseModule.kt` | 注册迁移 + `LibrarySourceRefDao` provider |
| `app/schemas/...AppDatabase/14.json` | KSP 自动生成（已核对与迁移 SQL 一致） |
| `feature/library/ShelfImporter.kt` | 注入索引/根存储、批次级根解析、`uriLastModified`、大文件指纹、指纹去重、成功路径 `recordSourceRef` |
| `feature/library/LibraryRootStore.kt` | 新增 `loadRoot()`（DataStore 直读） |
| `feature/library/SmartBookRecognizer.kt` | probe/候选新增 `fingerprint` 字段（默认 null，向后兼容） |
| `feature/library/SafSmartBookRecognizer.kt` | 大文件头块扩读 + 尾块跳读，产出指纹；样本语义不变 |
| `ui/viewmodel/LibraryBrowserPolicy.kt` | 重写为四级判定 + `formatOf`；过滤/默认勾选改吃 `LibraryShelfMatch?` |
| `ui/viewmodel/LibraryBrowserModels.kt` | `LibraryShelfMatchKind`/`LibraryShelfMatch`；行模型 `duplicateHint → shelfMatch`；页签改名 |
| `ui/viewmodel/LibraryBrowserViewModel.kt` | 注入索引、书架快照（id+标题）、引用快照刷新、行判定、目录观测、识别对账 |
| `ui/screen/shelf/LibraryBrowserComponents.kt` | 文件行/识别行分级文案与配色；确认档单击打开（多选态保留切换） |
| `ui/screen/shelf/LibraryBrowserRoute.kt` | 行判定参数、页签空文案、「已入架」过滤、打开书籍回调 |
| `app/src/test/.../ui/viewmodel/LibraryBrowserPolicyTest.kt` | 重写：四级判定 12 例 + 过滤/勾选/格式化（共 20 例） |
| `app/src/test/.../ui/viewmodel/ShelfImporterTest.kt` | 构造适配 + 4 新用例（引用写入/不伪造 root、重复不写引用、大文件指纹去重、已删书指纹不阻断） |
| `app/src/test/.../ui/viewmodel/ShelfViewModelTest.kt` | 构造参数适配（两个 mock） |
| `app/src/androidTest/.../AppDatabaseMigrationTest.kt` | 追加 13→14 迁移用例（含 CASCADE 验证） |

## 11. 契约自查（对应任务包 §4）

1. 导入仍只有一个 Module；未给 `LibraryBrowserViewModel` 注入 `ShelfImporter`（目录页仍把 URI 交给
   graph 作用域共享的 `ShelfViewModel`）。✓
2. 写根目录前先 `takePersistableUriPermission`（前组实现，未改）。✓
3. 弱判定不跳过导入；最终去重由 `ShelfImporter` 内容哈希/指纹裁决；指纹去重只针对在册书籍的引用
   （删书即清引用）。✓
4. 未申请 `MANAGE_EXTERNAL_STORAGE`；不扫描未授权目录；不上传文件名/目录/正文样本。✓
5. 浏览列表不删除/移动/重命名来源文件；新增观测只更新数据库观测列。✓
6. 零新增定时轮询；刷新只在下拉/点击识别/重新进入（显式动作）。✓
7. 识别并发仍为顺序 ≤2；读取量上界（样本 64 KiB + 指纹头尾各 256 KiB）；截断提示沿用
   「扫描达到安全上限，结果已截断」且截断扫描不下失效结论。✓
8. 零新增 PDF 宣称；诊断与报告只用中性名称。✓
