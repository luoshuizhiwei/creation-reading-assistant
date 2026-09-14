# Android 共享设置 / 搜索 / 同步 / 安全 — 只读调用图审计与拆片设计

> 日期：2026-09-13
> 范围：仅 `android/app/src/main/java/com/creationreadingassistant` 下
> `data/settings/**`、`data/repository/**`、`data/local/**`（仅识别）、`feature/search/**`、
> `feature/sync/**`、`data/security/**` 及其与 Reader / Shelf / Home / Profile 的调用关系。
>
> **本文为只读审计产物。** 本次未修改任何产品源码、测试、Gradle、脚本、schema 或其他文档；
> 未执行 git add / commit / push / reset / checkout / clean / stash；未运行 Gradle；未做任何真机操作。
> 唯一写入文件即本文件。

---

## 0. 基线

| 项 | 值 |
|---|---|
| 仓库 HEAD | `6cd29670e54c3fc2915d876896ecbaae8d000aa4`（`main`） |
| 工作区 | 220 项脏文件，`android/` 188 项；审计范围内 28 项（12 改 + 16 未跟踪 + 测试） |
| Room schema | `APP_DATABASE_SCHEMA_VERSION = 14`（`data/local/AppDatabase.kt:70`） |
| 上游计划 | `docs/plans/2026-09-13-android-wip-integration-plan.md` 切片 5「未分组，禁止直接提交」 |

---

## 1. 调用方图

### 1.1 包级 → 消费层（按 import 边聚合，main source set）

| 被依赖包 | 消费层分布（文件数） | 独立性判断 |
|---|---|---|
| `data.settings` | **ui/screen/reader 25**、ui/viewmodel 11、ui/screen/profile 5、ui/screen/shelf 4、feature/reader 2、feature/goal 2、ui/components 1、feature/library 1、data/ai 1、MainActivity 1 | **不独立** —— 主体是阅读器设置，不是共享层 |
| `data.repository` | ui/viewmodel 16、feature/library 2、ui/screen/stats 1、feature/reader 1、feature/goal 1、App 1 | **不独立** —— 需按仓储逐个分配 |
| `feature.search` | ui/viewmodel 1、ui/screen/search 1、data/repository 1 | **高度内聚，可独立成片** |
| `feature.sync` | ui/viewmodel 2、ui/screen/profile 1、data/repository 1 | **可独立成片** |
| `data.security` | feature/sync 2、data/remote 2、data/settings 1 | **可独立成片，且无 UI 直连** |
| `data.local` | 130 个文件 | 基础层，**不可整包成片** |

**这张表直接否定了「data/settings + data/repository 是一个共享基础设施层」的直觉**：`data.settings` 有 25 个消费者落在 `ui/screen/reader`，它真正的主体归属是切片 2（Reader），而不是切片 5。

### 1.2 关键精确调用边（已逐条核对）

**搜索索引重建（4 个重叠 API）**

| API | 位置 | 语义 | 调用方 |
|---|---|---|---|
| `rebuildAll()` | `SearchIndexRepository.kt:370` | 同步清库 + 内联重建（阻塞） | **0**（代码注释自承「目前没有任何调用方」） |
| `invalidateSweepForFullRebuild()` | `:401` | 仅重置游标（惰性） | `ReaderViewModel:554`、`ProfileViewModel:470`、内部 `:419` |
| `triggerFullRebuildNow()` | `:418` | 重置游标 + 唤起 Worker | `SearchViewModel:240` |
| `reindexBookById()` | `:389` | 单本重索引 | `ReaderViewModel:557` |

> `ProfileViewModel:470–471` 手写的 `invalidateSweepForFullRebuild() + scheduler.triggerNow(force=true)`
> 与 `triggerFullRebuildNow()`（`:418–423`）**语义完全等价**，属重复实现。

**删除链路**

| API | 位置 | 调用方 |
|---|---|---|
| `deleteBooksScoped()` | `BookRepository:143` | `BookDeletionCoordinator:95`（唯一） |
| `deleteBookScoped()` | `BookRepository:126` | 经 `deleteBooks(listOf(id))` 间接 |
| `clearBookCache()` | `BookRepository:250` | `ShelfBookActions:118`（唯一） |
| `applyDeletion()`（public） | `BookRepository:226` | **0**（外部无调用） |

**其它**

- `NoteRepository` → `ProfileViewModel`、`ReaderViewModel`、`SearchViewModel`（仅此 3 个 ViewModel，无 UI 直连）
- `SecurePrefs.open()` → `SettingsStore:254`、`WebDavConfigStore:17`、`SyncConfigStore:18`
- `SecurePrefs.getOrCreateAppInstanceId()` → `DeviceInfoProvider:20`、`JsonBridge:102`（后者为 provider 缺省回退）
- `SecurePrefs.hasDegraded / degraded` → **0 个 UI 消费者**
- `ReadingNotesSubPage` → `ProfileScreen.kt:121`（已接线）
- `AppNavigation.kt` → **0 个 note/笔记 路由**

---

## 2. 四个重点问题的结论

### 2.1 搜索索引 `rebuildAll()` 缺用户入口 —— 最小实现位置

**先纠正前提：用户级「全库重建」能力并不缺失。**

- 现有入口：`DiagnosticsSubPage.kt:296–303`「重建搜索索引」按钮
  → `ProfileRoute.kt:386` → `ProfileViewModel.rebuildSearchIndex()`（`:467`）
  → `invalidateSweepForFullRebuild() + triggerNow(force=true)`（惰性后台）。
- UI 文案已写明「重建在后台分窗口进行，不阻塞使用」，与 R6-B7 设计一致。
- `rebuildAll()` 缺的是**调用方**，不是用户入口：它是同步阻塞式重建（清库后直接 `ensureIndexedIncremental()`），
  与 R6-B7 明确写下的「**绝不在交互路径同步全库重建**（全库可达数 GB / 数十分钟）」直接冲突。

**最小实现位置（若最终决定要接）：**
`DiagnosticsSubPage.kt:281–306` 的「搜索索引」SectionCard 内加**次级**动作
→ `ProfileViewModel` 新增函数 → `ProfileRoute` 接线。这是唯一不引入新导航、不与既有入口打架的落点。

**但审计建议是不要把它做成常规用户入口**，三选一：

| 选项 | 说明 | 代价 |
|---|---|---|
| A（推荐）删除 | 死代码，能力已被惰性路径覆盖 | 需确认无将来同步重建诉求 |
| B 降级测试专用 | 加 `@VisibleForTesting`，由 JVM 测试使用 | 保留一个仅测试可达的 API |
| C 排障专用入口 | DiagnosticsSubPage 次级按钮 + 明确「阻塞、仅供排障」警示 | 与既有按钮语义重叠，易误导 |

> **需要用户做产品/架构决定**：`rebuildAll()` 取 A / B / C。
> 附带应一并决定的：`ProfileViewModel:470` 是否改为直接调用 `triggerFullRebuildNow()` 消除重复实现。

### 2.2 Profile / notes 入口缺失该由谁承担 —— **Profile 已经承担，不应交给导航或灵感页**

事实三条：

1. `ReadingNotesSubPage.kt` **已存在**，且在 `ProfileScreen.kt:121` **已接线**（`ProfileSubPage` 子页可达）。
2. `AppNavigation.kt` 中**没有任何 note 路由** —— 所以笔记只在 Profile 子页纵深内可达，无全局/深链入口。
3. `ProfileHomeScreen.kt:164` 有明确产品注释：「**产品规划无独立笔记概念：原「笔记」磁贴改为灵感中心直达**」。

结论：

- 不是「入口缺失」，而是**已有产品决策把一级磁贴改道到灵感中心**，笔记降级为 Profile 子页。
- **灵感页不应承担**：灵感是创作域，笔记/批注是阅读产出域；把笔记并入灵感会重演「无独立笔记概念」的语义混淆，
  且 `NoteRepository` 当前只被 Profile / Reader / Search 三个 ViewModel 消费，与灵感页无调用关系。
- **真正的最小缺口有两个**，且都不需要新建页面：
  - (a) **导航**：搜索结果命中笔记后无法跳转（`SearchViewModel` 消费 `NoteRepository`，但没有落地页路由）；
  - (b) 若产品要恢复一级入口，应在 Profile 首页加回磁贴，而非改造导航图。

> **需要用户做产品决定**：笔记是否恢复一级入口 / 是否补「搜索命中笔记 → Profile 笔记子页」路由。
> 在决定前，**不建议动任何代码**——现状是自洽的（有页面、有接线），只是入口层级是产品选择。

### 2.3 app 私有正文副本：软删除 / 移除正文 / 恢复 / 空间回收 —— 当前实际契约

唯一真源是 `feature/library/deletion/DeletionScope.kt`（枚举 + `retention()`，文案与撤销可用性均由此派生）。

| 作用范围 | 入口链路 | 对 DB | 对磁盘 `filesDir/books/` | 可撤销性 |
|---|---|---|---|---|
| `SHELVE` 搁置 | 改 `reading_progress.completion_state` | 仅改状态 | **不动** | 不适用 |
| `REMOVE_CONTENT` 移除正文 | `ShelfBookActions:118` → `BookRepository.clearBookCache:250` → `deleteBookContentFiles:267` | 清指针：`content_status="missing"`、`local_uri=null`、`local_content_path=null`，软删 `book_files` | **删除** `books/<id>/`（递归）与 `books/epub/<id>.epub` | **不可撤销**，且未发布 undo offer。源码注释：「这不是备份，也不构成可靠恢复」 |
| `DELETE_BOOK` 删除整本 | `BookDeletionCoordinator:95` → `deleteBooksScoped:143` → `applyDeletionLocked:230` | 软删书 + 进度/会话/笔记/高亮，清空标签/分类/书单/已读章节关联 | **不删文件** → **孤儿永久残留** | 可撤销，但**仅 12 秒**（`DeletionUndoPolicy.undoWindowMillis = 12_000L`）、**仅会话内**（进程重启不再提供）、凭证制（`BookDeletionCredential`） |

**空间回收的实际契约（这是最大的缺口）：**

- **没有孤儿回收**。软删除只动 DB，磁盘副本永久残留；用户直接删书则空间永不释放。
- 唯一的两个回收入口：
  1. 逐本「移除正文」（精确，但需用户在删书**前**主动执行，且不可逆）；
  2. `StorageSubPage.kt:598–641` 全局「清空所有离线书籍与资源」——直接
     `File(filesDir,"books").deleteRecursively()`，**核弹式清空全部书籍正文**（含仍在使用的书），
     代价与「回收软删除孤儿」完全不成比例。
- 因此**软删除书籍的磁盘空间目前实际无法安全回收**。

> **需要用户做产品决定**（对应上游计划 §6「私有正文副本回收」已挂起的议题）：
> 是否需要回收站 / 保留期 / 孤儿扫描清理 / 空间统计 / 恢复入口。
> **本次审计不建议顺手改任何删除行为**——现有语义由 `DeletionScope` 明确定义且有撤销窗口，
> 贸然让 `DELETE_BOOK` 连带删文件会直接摧毁 12 秒撤销能力。

### 2.4 安全配置 / ANDROID_ID 迁移能否独立于其他数据层变更 —— **可以，且已经完成**

- **`ANDROID_ID` 零读取**：全仓仅 `SecurePrefs.kt:65` 注释提及（说明为何不用）。
  现实现为 `cra_device_identity` 加密 prefs 中的应用级 UUID（`getOrCreateAppInstanceId`，`:67`）。
  即**迁移实际已完成**，不存在待迁移的遗留读取点。
- **`SecurePrefs` 无数据层耦合**：仅依赖 `Context` + `EncryptedSharedPreferences`，
  不 import Room / DataStore / 导航。三级降级（创建 → 清库重试 → 明文并记录 `degraded`）。
- **消费者仅 5 处且全经其 API**：`WebDavConfigStore:17`、`SyncConfigStore:18`、`SettingsStore:254`、
  `DeviceInfoProvider:20`、`JsonBridge:102`。
- **切分注意**：`device_id` 同时是 Room 多表的同步协议列（`SyncRepository:156/177/194/211` 写入 `env.deviceId`）。
  「本机标识从哪来」属安全配置（可独立），「`device_id` 列如何参与合并」属同步协议（属 S5-B）。
  **不要把实体 `device_id` 列与安全存储绑在同一片。**
- **现存缺口**：`hasDegraded` / `degraded` **无任何 UI 消费者**。注释写「供设置页向用户明确告警」，
  但设置页从未读取 → 设备加密不可用时凭据会静默明文落盘且用户无感；
  `res/xml/backup_rules.xml:12` 亦自承该情形下备份会带走明文凭据。

> **需要用户做产品决定**：是否把「加密存储已降级为明文」暴露给用户；备份是否排除明文凭据。

---

## 3. 当前被其他切片占用的文件（切片 5 不得触碰）

| 文件 / 符号 | 被谁占用 | 依据 |
|---|---|---|
| `data/local/AppDatabase.kt` | **切片 1 + 切片 2 双占用** | v12→v13 `reader_text_corrections`（R4/切片 2），v13→v14 `library_source_refs`（R3/切片 1） |
| `LibrarySourceRefDao/Entity` | 切片 1（R3） | v14 迁移主体 |
| `ReaderCorrectionDao/Entity` | 切片 2（R4） | v13 迁移主体 |
| `ReaderOverride` / `ReadingPresets` / `ReaderSettingsRouter` / `PerBookSettingsStore` | 切片 2（Reader） | 消费者为 `ReaderScreen`/`ReaderScaffold`/`ReaderLayerBuilders`/`ReaderSheetHost`/`ReaderSettingsCommon` |
| `ShelfSavedViewsStore` | 切片 4（Shelf） | 唯一消费者 `ShelfViewModel` |
| `SelectionActionSettings` / `SelectionActionStore` | **跨切片 2 / 3，未定** | `Settings` 侧：profile 的 `ProfileHomeScreen`/`ProfileUiState`/`SelectionSubPage`；reader 侧：`ReaderInteractionLayer`/`ReaderLayerBuilders` |
| `BookRepository`（`applyDeletionLocked`/`deleteBookScoped`/`clearBookCache`） | 删除链路（切片 4 或 1） | 唯一调用方 `BookDeletionCoordinator` / `ShelfBookActions` |
| `feature/library/deletion/**` | 删除链路 | 与书架长压操作、撤销条同生命周期 |
| `StorageSubPage.kt`（全局清空） | Profile（切片 3）承载，但语义属删除契约 | 见 §2.3 |
| `SettingsViewModel` | 跨 S5-C 与切片 2 | 同时依赖 `ReaderSettingsRouter`/`PerBookSettingsStore`/`SelectionActionStore` |
| `ProfileHomeScreen:164`（笔记磁贴） | 切片 3 | 已有产品注释改道灵感中心 |

---

## 4. 候选切片

> 明确拒绝的方案：**「把 data/settings + data/repository + data/local 合成一个共享基础设施提交」**。
> 依据：`data.local` 有 130 个消费者、`data.settings` 有 25 个消费者落在 reader —— 整包提交会把
> 切片 1/2/3/4 的改动全部卷入，且 `AppDatabase` 已被双占用，必然产生迁移冲突。

### S5-A 搜索索引与搜索体验

| 项 | 内容 |
|---|---|
| **文件边界** | `feature/search/*`（6 个，其中 `SearchOffsetResolver.kt`、`SearchTokenAggregator.kt` 本轮已改）；`data/repository/SearchIndexRepository.kt`、`SearchIndexScheduler.kt`、`SearchIndexWorker.kt`、`SearchIndexWorkerPolicy.kt`；`data/local/dao/SearchTermDao.kt` 及 `SearchTermRow`/`SearchIndexStateRow`/`SearchIndexCoverageRow`；`ui/viewmodel/SearchViewModel.kt`；`ui/screen/search/**`；`data/settings/SearchHistoryStore.kt`；测试 `SearchOffsetResolverTest.kt` 等 |
| **依赖顺序** | `feature/search` 纯函数（已被 `SearchIndexRepository` 依赖）→ 仓储/调度 → `SearchViewModel` → UI。**不依赖任何新迁移**（`search_terms`/`search_index_state`/coverage 自 v10–v12 已存在） |
| **seam** | Room：`search_terms`、`search_index_state`、`search_index_coverage`（只读，不改 schema）；DataStore：`SearchHistoryStore`；导航：搜索路由；外部 Intent：**无**；同步协议：**无** |
| **风险** | ① 4 个重叠重建 API + `ProfileViewModel:470` 与 `triggerFullRebuildNow()` 重复实现；② `SearchIndexRepository:352` 注释记载的坑——`finally` 中绝不能出现 suspend 调用，否则 JobScheduler 超时后 `isRunning` 永久为 true；③ 本轮已改动两个搜索核心文件，需确认作者与切片 2 无重叠 |
| **最低测试** | `SearchOffsetResolverTest`（已存在）；`SearchTokenAggregator` 单测；新增「游标重置 → 全库重扫」「单本重索引幂等」JVM 用例 |
| **真机验收** | 搜索命中与覆盖率徽标正确；Profile「重建搜索索引」后台分批完成且期间搜索可用；规则变更后命中来源标注正确；大书分批无 ANR |
| **需产品决定** | **是** —— `rebuildAll()` 去留（A 删 / B 测试专用 / C 排障入口）；是否消除 `ProfileViewModel` 与 `triggerFullRebuildNow` 的重复 |

### S5-B 同步协议与凭据安全

| 项 | 内容 |
|---|---|
| **文件边界** | `feature/sync/*`（`JsonBridge.kt` 本轮已改）；`data/remote/` 同步相关（`SyncContract`、`SyncConfigStore`、`DeviceInfoProvider`、`AuthInterceptor`）；`data/security/SecurePrefs.kt`；`data/repository/SyncRepository.kt`；`data/local/dao/SyncDao.kt`、`entity/SyncEntities.kt`；`ui/screen/profile/SyncSubPage.kt`/`WebDavSubPage.kt`/`QrPairingScreen.kt`；测试 `SyncRepositoryTest.kt`、`JsonBridgeTest.kt`、`SyncConfigStoreTest.kt` |
| **依赖顺序** | `SecurePrefs`（零依赖，最底层）→ `data/remote` 存储 → `feature/sync` → `SyncRepository` → Profile 三个子页 |
| **seam** | Room：`sync_accounts`、`sync_state`；DataStore/加密 prefs：`SyncConfigStore`、`WebDavConfigStore`；导航：`QrPairingScreen` 路由；外部 Intent：**相机（CAMERA 权限，QR 配对）+ SAF 导出（`LocalZipBackup`）**；同步协议：`SyncEnvelope`（`schemaVersion`/`deviceId`）、`SyncMergePolicy` |
| **风险** | ① `hasDegraded` 无消费者 → 明文降级静默；② `backup_rules.xml` 自承备份可能带走明文凭据；③ `JsonBridge:102` 的 deviceId 双来源（provider 优先 / SecurePrefs 回退）语义需在测试中钉死；④ 相机与 SAF 均为外部依赖，模拟器/无相机设备无法覆盖 |
| **最低测试** | `SyncRepositoryTest`（已存在且本轮改动）；`JsonBridgeTest`（含 deviceId 与未知字段）；`SyncConfigStoreTest`；**新增** `SecurePrefs` 三级降级路径单测（明文降级被正确记录） |
| **真机验收** | 扫码配对；同步往返（上传/下载/冲突合并）；WebDAV 备份与恢复；断网失败重试与失败项逐项重试；`degraded` 告警（若决定实现） |
| **需产品决定** | **是** —— 是否向用户暴露「加密存储已降级为明文」；备份是否排除明文凭据 |

### S5-C 全局设置存储（剥离 reader / shelf 专属后）

| 项 | 内容 |
|---|---|
| **文件边界** | `data/settings/SettingsStore.kt`、`ReaderDefaultsMigration.kt`；`ui/viewmodel/SettingsViewModel.kt` 的**全局部分**（AI 密钥、外观等）。**注意**：`SettingsViewModel` 同时被 `ReaderSettingsRouter`/`PerBookSettingsStore`/`SelectionActionStore` 依赖，**必须按功能面切，不能整文件搬走** |
| **依赖顺序** | `SecurePrefs`（S5-B 先落地）→ `SettingsStore` → `SettingsViewModel` 全局部分 |
| **seam** | DataStore/加密 prefs：`SettingsStore` 的 AI secrets 走 `SecurePrefs.open(AI_SECRETS_PREFS_NAME)`（`:254`）；Room：**无**；导航：外观/主题跨页；外部 Intent：无；同步协议：无 |
| **风险** | ① 与切片 2「阅读设置」共用 `SettingsViewModel` 与 `PerBookSettingsStore`，文件级重叠；② `SelectionActionSettings/Store` 横跨 reader 与 profile，归属未定 |
| **最低测试** | `SettingsStoreTest`、`SettingsStoreTxtTocMigrationMarkerTest`（均已存在，均用 `mockkObject(SecurePrefs)` 拦截加密存储） |
| **真机验收** | 改设置后冷启动保持；AI 密钥写入/读取；外观切换即时生效且重启保持 |
| **需产品决定** | **是** —— `SelectionActionSettings` / `SelectionActionStore` 归切片 2（Reader）还是切片 3（Profile） |

### 明确不成片的部分（按消费者分配，不进切片 5）

| 文件 | 归属 |
|---|---|
| `data/local/**`（130 消费者） | 基础层，不整包成片；`LibrarySourceRef*` → 切片 1，`ReaderCorrection*` → 切片 2，搜索三表 → S5-A，同步两表 → S5-B |
| `BookRepository` / `feature/library/deletion/**` | 删除链路 → 切片 4（或 1） |
| `InspirationRepository` / `NoteRepository` / `StatsRepository` / `TaxonomyRepository` | → 切片 3（Home / Profile / Stats） |
| `ChapterReadRepository` | → 切片 2（Reader） |
| `ContinueReadingStore` / `GoalStore` | → 切片 3（Home / 目标） |
| `ShelfPrefs` / `ImportHistoryStore` / `ShelfSavedViewsStore` | → 切片 4（Shelf） |

---

## 5. 建议的串行顺序与前置条件

1. **S5-C 之前**：S5-B 的 `SecurePrefs` 部分必须已冻结（S5-C 依赖它）。
2. **S5-A 可最先独立提交** —— 它是唯一既不碰 `AppDatabase` 新迁移、又不与切片 1/2/4 争文件的片。
3. **S5-B 需在切片 1（R3）冻结后**：同属外部存储/URI 域（SAF 导出），避免与目录授权的 grant 语义交叉。
4. **S5-C 必须在切片 2 之后**：`SettingsViewModel` / `PerBookSettingsStore` 与阅读设置有文件级重叠。
5. 任一片提交前仍需按上游计划 §5 出最小证据包（`git diff --name-only HEAD -- android`、
   未跟踪清单、`git diff --check -- <本片路径>`），并在**不与其他构建抢占时**串行跑门禁。

---

## 6. 本次审计未做的事（避免误读）

- 未运行任何 Gradle 任务，未做任何真机/adb 操作 —— 所有结论均为静态证据。
- 未修改任何源码、测试、Gradle、脚本、schema 或既有文档；未执行任何 git 写操作。
- 调用方图基于 import 边与逐条符号核对，**不排除**存在反射、字符串路由或 `koin/hilt` 图间接引用导致的漏边；
  但本文引用的每一条精确边（含「0 调用方」结论）均已用符号级 grep + 源码注释双重确认。
- §2.3 的删除契约来自源码与 `DeletionScope` 注释，**未做真机空间/文件取证**；
  孤儿残留属静态推断（`DELETE_BOOK` 路径中确无文件删除调用），建议后续以真机 `run-as ls` 复核。
