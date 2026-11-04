# S04 androidTest 启动隔离设计审查（独立测试架构窗口）

- **日期**：2026-09-25
- **仓库 / 分支 / HEAD**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` / `codex/recover-worktree-20260920` / `5aed593`
- **窗口性质**：**只读审查 + 设计**。唯一写入文件 = 本报告。
- **未执行**：未跑 Gradle、未跑 adb、未装机、未改任何产品源码 / 测试源码 / 既有报告、未 commit/push/stage/reset/checkout/clean。
- **未确认设备行为**：本报告全部结论来自**静态阅读源码与清单**，**没有一条**经过设备运行验证。

## 判定图例

| 标记 | 含义 |
|---|---|
| **已证实** | 由本仓库当前源码 / 清单 / 合并清单直接可读出，可自行复验 |
| **推测** | 由框架行为推断，需一次构建或设备运行才能定论 |
| **未知** | 现有证据不足以下任何结论，不做默认假设 |

---

## 0. 开工核对

| 检查 | 结果 |
|---|---|
| 目标报告 `docs/coordination/2026-09-25-s04-instrumentation-startup-isolation-design.md` 是否已存在 | **否**（Glob 无命中）→ 首次撰写 ✓ |
| 是否有其他 agent 正在写同文件 | 未见；`docs/coordination/2026-09-2*.md` 中无同名/同题文件 ✓ |
| 生产文件 / 测试文件是否被我改动 | **否**，本窗口零写操作（本报告除外）✓ |
| 是否触碰真实书名 | **否**，全文只用中性标识 ✓ |

---

## 1. 结论先行（给非技术读者的三句话）

1. **「用 androidTest 的 Hilt `@TestInstallIn` 换掉 `DatabaseModule`」这条路在当前工程里行不通**——不是写法问题，是位置问题：没有任何 androidTest 用过 Hilt，也没有任何测试能换掉真机上那个 `App` 类。换模块换不到 `App.onCreate` 用的那张依赖图。
2. **真正能把启动副作用关掉的最小改动，是换掉 instrumentation 启动的那个 Application**（新增两个 androidTest 文件 + 改构建配置里的一行 runner 名）。
3. **除了已披露的「修 EPUB 尺寸」之外，还有一处更值得担心的副作用此前没被记录**：启动时会排一个 10 秒后跑的「全文索引」后台任务，它会**真的往正式书库里写索引行**，而且可以在测试跑完之后才执行。

---

## 2. 任务核心问题：`@TestInstallIn` 替换 `DatabaseModule` 是否可行

### 2.1 结论：**不可行**（在当前工程结构下）

| 必要条件 | 现状 | 判定 |
|---|---|---|
| androidTest 里存在使用 Hilt 的测试（`@HiltAndroidTest` / `HiltAndroidRule`） | **0 处**（34 个 androidTest 文件全量 grep 无命中） | ❌ **已证实** |
| 被替换的 Hilt 组件是「测试用的 SingletonComponent」 | 真机进程里 `App` 由主 APK manifest 声明为 `.App`，其 Hilt 组件来自**主 APK** 生成代码 | ❌ **已证实**（manifest 位置） |
| `App.onCreate` 里的 `EntryPointAccessors.fromApplication` 能拿到替换后的图 | 它拿的是 `App` 这个 `@HiltAndroidApp` 实例的组件 | ❌ **推测**（Hilt 运行时精确行为未在本窗口验证；但即使成立，前两条已使方案失效） |

关键代码事实（`App.kt:141-162`，**已证实**）：

```kotlin
Thread({
    EntryPointAccessors.fromApplication(this, EpubRepairEntryPoint::class.java)…startOnce()
    EntryPointAccessors.fromApplication(this, GoalSchedulerEntryPoint::class.java)…start()
    EntryPointAccessors.fromApplication(this, SearchIndexEntryPoint::class.java)…start()
}, "cra-startup").start()
```

这三行**无条件执行**，没有任何「测试开关 / BuildConfig 分支 / 进程名判断」。
`App.onCreate` 唯一的条件分支是 `AppLog.debugEnabled`（`App.kt:92`），它只控制日志与 StrictMode，**不控制这三项启动接线**。

补充事实（**已证实**）：`androidTestImplementation(libs.hilt.android.testing)` 已在 `build.gradle.kts:228` 就位。`hilt-android-testing` 这个依赖**当前完全没有被任何测试用到**——它能被编译进来，不代表它能影响 `App`。

### 2.2 对既有 SEAM REQUEST 的一处更正（重要）

`docs/coordination/legacy-stages/2026-09/2026-09-25-s04-epub-room-isolation-correction.md` §5 的方案 B 写的是：

> 允许新增 `androidTest/AndroidManifest.xml`，用 `tools:replace` 把目标 Application 换成不执行启动任务的测试 Application

**该方案不成立**：androidTest 源集的 manifest 只合并进**测试 APK**；被 instrumentation 启动的目标 Application 类由**主 APK** manifest 的 `<application android:name=".App">` 决定（`src/main/AndroidManifest.xml:25`）。测试 APK 的 manifest 改不了它。
判定：**已证实（基于 AGP 主 APK / 测试 APK 双产物模型与当前 manifest 文件位置）**；若实施者希望保险，可用 §6 的一次构建验证证伪。

> 另一佐证：当前仓库**不存在** `androidTest/AndroidManifest.xml`（`android/app/src/**/AndroidManifest.xml` 只有 `main` / `benchmark` / `benchmarkRelease` 三份）。

---

## 3. 仍未隔离的其他启动副作用（逐项）

按「进程启动 → 应用 onCreate → 后台线程」顺序排列。标注 **[新]** 的是既有 S04 两份报告**未披露**的项。

### 3.1 进程启动期（`App` 代码之前）

| 副作用 | 落点 | 判定 |
|---|---|---|
| `androidx.startup.InitializationProvider` 内的 `WorkManagerInitializer` | 创建 / 打开 `androidx.work.workdb`（**应用私有目录内**的独立 SQLite，**不是** Room 正式库） | **已证实**（`build/intermediates/merged_manifest/debug/.../AndroidManifest.xml:94-110`，ContentProvider 先于 Application.onCreate） |
| 同上 `EmojiCompatInitializer` / `ProcessLifecycleInitializer` / `ProfileInstallerInitializer` | 各自初始化 | **已证实**（同上） |
| `com.google.mlkit.common.internal.MlKitInitProvider`（`initOrder=99`） | ML Kit 组件发现 | **已证实**（同上 :219-223） |

### 3.2 `App.attachBaseContext`（`App.kt:61-86`）

| 副作用 | 说明 | 判定 |
|---|---|---|
| 预热 `getSharedPreferences("app_prefs")` | **会创建** `shared_prefs/app_prefs.xml`（首次） | **已证实**（`App.kt:82` + `ONBOARDING_PREFS = "app_prefs"`，`OnboardingOverlay.kt:77`） |
| `installCrashHandler()` | 未捕获异常时 `writeFatal` 追加写 `cacheDir/cra_fatal.log` | **已证实**（`App.kt:41-58, 166-174`） |
| `AppContextHolder.cacheDir` 赋值 | 仅内存 | **已证实** |

### 3.3 `App.onCreate`（`App.kt:88-164`）

| 副作用 | 落点 | 判定 |
|---|---|---|
| StrictMode（detectAll + penaltyLog，仅 debug） | 仅日志 | **已证实** |
| `Trace.forceEnableAppTracing`（反射） | 系统 trace | **已证实** |
| `AppLog.i("App","应用启动")` | **仅内存环形缓冲**（`AppLog.push` 不落盘） | **已证实**（`AppLog.kt:57-72`） |
| `BootMirror.applyApplicationNightMode` → `UiModeManager.setApplicationNightMode` | **系统级、跨进程持久设置**，在应用沙箱之外；测试结束不会自动还原 | **已证实**（`App.kt:124-128` + `SettingsStore.kt:378-391`）**[新]** |

### 3.4 后台线程 `cra-startup` 三项（核心）

| # | 任务 | 触碰正式库 | 其他落点 | 判定 |
|---|---|---|---|---|
| a | `EpubSizeRepairTask.startOnce()` → `repairMissingLocalFileSizes()` / `backfillMissingEpubCovers()` | **是**（SELECT + 选择性 UPDATE `books.size` / `books.cover_data_url`） | `EpubRepository` 的 `filesDir` 是**真机正式目录**（此路径未经 `IsolatedTestContext`） | **已证实**（`EpubSizeRepairTask.kt:28-36`；`EpubRepository.kt:269, 290`）。既有报告已披露，措辞准确 |
| b | `ReadingGoalScheduler.start()` | **间接是**：该类的 `@Inject` 依赖链含 `StatsRepository` → DAO → `AppDatabase` 单例，取实例即建库链 | 建 `reading_goal` 通知渠道（系统持久状态）；读 DataStore `goal_prefs`；`enqueueUniquePeriodicWork`（写 `workdb`） | **已证实**（`ReadingGoalScheduler.kt:54-72`；`StatsRepository.kt:64-68`）**[新]** |
| c | `SearchIndexScheduler.start()` | **是，且是写入**：`SearchIndexWorker` → `EntryPointAccessors` → `SearchIndexRepository.ensureIndexedIncremental()`，其中 `termDao.upsertAll(...)` / `coverageDao.upsertAll(...)` 并「先按书删旧行」 | 一次性任务 **10 秒初始延迟** + 每日周期任务，写入 `workdb`；**任务在测试结束后仍可能执行** | **已证实**（`SearchIndexScheduler.kt:22-48, 79-92`；`SearchIndexWorker.kt:16-22`；`SearchIndexRepository.kt:1315, 1454, 1591, 1629`）**[新·最重要]** |

> 对 (c) 的补充说明：这是**写**操作，不是 (a) 那种「选择性修复」。且因为 10 秒延迟 + WorkManager 持久化，它可以在 `am instrument` 已经返回之后才真正跑，写入 `search_terms` / `search_index_coverage`。目前的 S04 表述「打开正式库并可能写入封面/尺寸字段」**低估了这一项**。

### 3.5 条件触发

| 副作用 | 触发条件 | 判定 |
|---|---|---|
| `DatabaseSafetyNet.snapshotIfUpgrading` 把整个库（含 `-wal`/`-shm`）复制到 `noBackupFilesDir/db-snapshots` | 设备上正式库 `user_version` < 15。源码注释称本机实测库可达 **319MB** | **已证实**（`DatabaseModule.kt:55` + `DatabaseSafetyNet.kt:53-79`）**[新]** |
| `SettingsStore.init` → `migrateLegacyReaderDefaultsOnce()`（DataStore 写） | 仅当 `SettingsStore` 被注入。启动三线程未注入，MainActivity 未启动 → 当前**不触发** | **已证实（不触发）**（`SettingsStore.kt:408-411`；见 §3.6） |

### 3.6 一个正面发现：MainActivity 未启动

34 个 androidTest 文件中：

- 使用 `createComposeRule()` 的：26 处命中；
- 使用 `createAndroidComposeRule<MainActivity>()` 的：**0 处**；
- 引用 `MainActivity` 的：仅 2 处注释文字。

⇒ **不存在 Activity 级启动副作用**，`SettingsStore` / `ShelfViewModel` / 书架首帧副作用均不发生。**已证实**。

### 3.7 既有测试自身的副作用（非启动期，但同类问题，登记不修改）

`EpubParserInstrumentedTest.kt:21-28` 的 `@After` 直接删**正式目录**：

```kotlin
File(context.filesDir, "books/epub/$id.epub").delete()
File(context.filesDir, "books/epub/$id").deleteRecursively()
```

用例 id 为固定字符串（如 `instrumented-empty-spine`）。与 S04 已修正的「在正式目录按 id 删文件」属**同一类问题**，只是碰撞面更小。**已证实**；该文件不属本窗口所有权，仅登记。

---

## 4. 全局影响：换掉启动 Application 会波及哪些既有测试

前置事实（**已证实**）：

- 34 个 androidTest 文件中 **0 处**使用 `EntryPointAccessors` / `as App` / `HiltTestApplication` ⇒ 没有任何测试依赖「宿主是 `App` 类」。
- 所有 Compose 测试走 `createComposeRule()`，其 Activity 由 `debugImplementation(libs.androidx.compose.ui.test.manifest)` 在**主 APK** 声明（`androidx.activity.ComponentActivity`，见合并清单 :235-237）；Application 换掉不影响它。
- 主题来自 manifest 的 `android:theme`，不由 Application 代码设置 ⇒ **UI 测试外观不应发生变化**。

| 影响面 | 评估 | 判定 |
|---|---|---|
| 依赖 `ApplicationProvider.getApplicationContext()` 的 Room 测试（10 个文件） | 换成裸 `Application` 后 `filesDir`/`cacheDir`/`getDatabasePath` 仍可用，Room builder 不受影响 | **推测**（框架行为；建议按 §6 逐步验证） |
| 正式库不再被 `EpubSizeRepairTask` / `SearchIndexScheduler` 触碰 | ✅ 目标收益 | **推测** |
| `AppLog.debugEnabled` 恒 false、`AppContextHolder.cacheDir` 为 null | 无测试断言这些；写 fatal 日志变 no-op | **已证实**（无测试引用） |
| StrictMode 不再安装 | 仅少打日志 | **已证实** |
| 既有测试库名不受影响 | `migration-test` / `migration-open-callback-test` / `chapter-read-repository-persistence-test.db` 均与 `creation_reading_assistant_native` 不同 | **已证实** |
| 手工脚本 | `docs/handoff/current.md` §8 的示例命令把 runner 硬编码为 `androidx.test.runner.AndroidJUnitRunner`；改 runner 后该命令会失败 | **已证实**（需同步更新文档/脚本） |
| `connectedDebugAndroidTest` 结束卸载主应用 | 与本改动无关，但仍是设备前置条件（见 §7） | **已证实**（handoff §2/§8 既有记录） |

---

## 5. 最小安全设计

### 5.1 方案 C（任务书原方案）——**明确否决**

「仅在 androidTest 加 `@TestInstallIn(replaces=[DatabaseModule::class])` 指向 in-memory」：
不换 Application、不换 runner ⇒ `App.onCreate` 仍走主 APK 的依赖图，`EpubSizeRepairTask` / `SearchIndexScheduler` 仍拿正式库。**无效**，且会造成「以为已隔离」的假象，风险高于不做。

### 5.2 方案 A（**推荐，最小**）——测试专用 Runner + 无启动任务 Application

新增两个 androidTest 文件 + 改构建配置一行：

| 文件 | 内容要点 | 所有权 |
|---|---|---|
| `androidTest/java/com/creationreadingassistant/testing/CraIsolatedTestApplication.kt` | `class CraIsolatedTestApplication : Application()` —— 无 `@HiltAndroidApp`、无启动线程、无 StrictMode、不改系统夜间模式。**刻意不放任何业务逻辑**。 | 实施窗口 |
| `androidTest/java/com/creationreadingassistant/testing/CraIsolatedTestRunner.kt` | 继承 `androidx.test.runner.AndroidJUnitRunner`，覆写 `newApplication(...)` 返回 `CraIsolatedTestApplication` | 实施窗口 |
| `android/app/build.gradle.kts:29` | `testInstrumentationRunner` 由 `androidx.test.runner.AndroidJUnitRunner` 改为 `…CraIsolatedTestRunner` | **集成者**（共享构建配置，单行） |

设计约束（写入新文件 KDoc）：

1. **不得**让测试 Application 继承 `App`（否则 `App.onCreate` 照跑）；
2. **不得**在新 Application 里加任何「替代的启动逻辑」——目标是「什么都不做」，不是「做一份测试版」；
3. 若将来某个 androidTest 需要 Hilt，应另开窗口评估 `@CustomTestApplication(android.app.Application::class)`，**不要**用 `@CustomTestApplication(App::class)`（那样会继承启动任务）。

### 5.3 方案 B（备选，仅当将来需要 Hilt 装配）

`@CustomTestApplication(Application::class)` 生成测试 Application + `@TestInstallIn(SingletonComponent::class, replaces=[DatabaseModule::class])` 指向 `Room.inMemoryDatabaseBuilder`。
代价：多一层 Hilt 生成物；当前 0 个测试需要 Hilt，属**过度设计**，本窗口不建议。

### 5.4 方案 D（过渡，零代码改动）

在方案 A 落地前，维持现状 + 严格执行「跑前恢复点 / 跑后三表对账」（S04 已在做）。
**注意**：现有对账只覆盖 `books` / `reading_progress` / `library_source_refs` 三表（见 `docs/qa/2026-09/2026-09-25-s04-retest-independent-reconciliation.md` §3.4），**必须补上 `search_terms` / `search_index_coverage` 行数与内容**，否则 §3.4(c) 的写入会被漏检。

### 5.5 推荐路径

**先 D（补齐对账面）→ 再 A**。A 未落地前，任何「androidTest 不触碰正式库」的表述仍不成立。

---

## 6. 验证步骤（本窗口未执行任何一步）

实施窗口需按顺序完成，**每步留原始输出**（S04 复核 §7 已提此要求）：

| # | 步骤 | 期望 | 性质 |
|---|---|---|---|
| 1 | `./gradlew :app:compileDebugAndroidTestKotlin` | EXIT=0 | 静态 |
| 2 | `./gradlew :app:assembleDebugAndroidTest` | EXIT=0；记录 APK sha256 | 静态 |
| 3 | `./gradlew :app:lintDebug` | 0 errors；warning 数不增加 | 静态 |
| 4 | 解包测试 APK，确认 `CraIsolatedTestRunner` 在 dex 中 | 存在 | 静态 |
| 5 | 解包测试 APK 的 manifest，确认 `instrumentation android:name` = 新 runner | 一致 | 静态 |
| 6 | 设备定向跑 `EpubReplaceRoomIsolatedTest` + 一个 Compose 类（如 `ReaderRulesSheetTest`） | Compose 类不回归 | 设备 |
| 7 | 跑前后对比（见 §7 取证清单） | 正式 lib 与 `search_terms` 无新增变化 | 设备 |

> **证伪 §2.2 的方式**：若实施者怀疑「androidTest manifest 也能换 Application」，可在第 5 步之外额外检查**主 APK** 的合并 manifest 是否仍为 `com.creationreadingassistant.App`——若是，则方案 B 确定无效。

---

## 7. 真实手机测试前置条件

本窗口**未连接设备、未执行 adb**。以下为实施窗口必须满足的前置项：

1. **`adb devices` 核对当前在线设备 serial，所有命令显式 `adb -s <serial>`**；禁用 MuMu 模拟器（AGENTS.md / handoff §1）。历史设备 serial 见 handoff（中性标识处理，本报告不复述可能含隐私的内容）。
2. **测试前恢复点**：按 S04 既有做法对应用私有三目录（`databases/`、`files/`、`shared_prefs/`）做 tar 快照并验 `PRAGMA integrity_check`；**不做恢复演练**（恢复是写操作）。
3. **MIUI / HyperOS 授权**：「后台弹出界面 → 始终允许」（RUX-009，handoff §29）；重装 APK 会重置该权限。
4. **卸载风险**：`connectedDebugAndroidTest` / AGP 结束时会卸载主应用（handoff §2/§8 既有记录）⇒ 必须确认可重装，跑完核对 `pm path` 并重装。
5. **两个 APK 都要装**：改动后需重新安装主 APK 与 androidTest APK。
6. **常亮设置**：长测前记录 `stay_on_while_plugged_in` 原值，结束后恢复。
7. **取证清单（跑前 / 跑后各取一次，逐项落盘到仓库外证据目录）**：
   - 正式库三表 `books` / `reading_progress` / `library_source_refs` 全行关键字段；
   - **`search_terms` 与 `search_index_coverage` 行数 + 内容摘要**（对应 §3.4(c)，此前未纳入对账）；
   - `files/books/epub/` 目录清单与哈希；
   - `shared_prefs/` 清单（含 `app_prefs.xml`、`cra_boot_mirror.xml`）；
   - `noBackupFilesDir/db-snapshots/` 清单（对应 §3.5）；
   - `androidx.work.workdb` 的 work 队列状态（对应 §3.4 b/c 的跨测试残留）。
8. **隐私纪律**：报告与证据只用「测试 EPUB / 测试 TXT」等中性标识，不记录真实书名（AGENTS.md「Android 真机验证约束」）。

---

## 8. 文件所有权

| 文件 / 目录 | 归属 | 本窗口是否触碰 |
|---|---|---|
| `docs/coordination/2026-09-25-s04-instrumentation-startup-isolation-design.md` | **本窗口（唯一写入）** | ✅ 新建 |
| `android/app/src/androidTest/.../testing/CraIsolatedTestApplication.kt`（方案 A） | 实施窗口（未开工） | ❌ |
| `android/app/src/androidTest/.../testing/CraIsolatedTestRunner.kt`（方案 A） | 实施窗口（未开工） | ❌ |
| `android/app/build.gradle.kts` 的 `testInstrumentationRunner` | **集成者**（共享构建配置） | ❌ |
| `android/app/src/main/**`（含 `App.kt`、`DatabaseModule.kt`） | 产品源码 | ❌ **禁止** |
| 既有 androidTest 文件（34 个，含 `EpubReplaceRoomIsolatedTest.kt`、`EpubParserInstrumentedTest.kt`） | 各自原所有者 | ❌ **禁止** |
| `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-epub-room-isolation-correction.md`、`docs/qa/2026-09/2026-09-25-s04-retest-independent-reconciliation.md` 等既有报告 | 原作者 / 集成者 | ❌ **禁止**（§2.2 的更正以本报告记载，未回改原文） |

---

## 9. 阻塞项（BLOCKERS）

| # | 阻塞项 | 需要谁拍板 |
|---|---|---|
| **B1** | 方案 A 需要改 `build.gradle.kts` 的 `testInstrumentationRunner`（共享构建配置）——**本窗口无权** | 集成者 |
| **B2** | `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-epub-room-isolation-correction.md` §5 方案 B（androidTest manifest 换 Application）**不成立**，该 SEAM REQUEST 需据此修订 | 集成者 / 原作者 |
| **B3** | §3.4(c) 的 `SearchIndexWorker` **正式库写入 + 跨测试残留**此前未披露；S04 现行「进程级副作用」表述需按本报告收紧/补强 | 集成者 / 原作者 |
| **B4** | 方案 A 落地前，设备重跑 androidTest 仍会触碰正式库；若必须重跑，须先补齐 §7.7 的 `search_terms` 对账面 | 用户 / 集成者 |
| **B5** | `EpubParserInstrumentedTest` 的正式目录 `@After` 清理（§3.7）与本轮同属一类问题，是否纳入同一轮修需另开窗口 | 用户 / 集成者 |

---

## 10. 未知（诚实登记）

1. `@TestInstallIn` 在「测试 APK 与主 APK 组件生成」上的**运行时**精确行为，本窗口未用构建或设备验证；§2.1 的结论不依赖它（前两条已足以否决）。
2. 换成裸 `Application` 后，`androidx.startup` 的四个 Initializer（§3.1）是否仍全部执行——ContentProvider 由 manifest 驱动，**推测仍会执行**，需设备验证。
3. 换 Application 后 26 个 Compose 测试是否 **100% 无外观/行为变化**——仅由「主题来自 manifest、无测试依赖 App 类」推断，需设备逐类验证。
4. 设备上当前正式库的 `user_version`，故 §3.5 的条件是否成立未知。
5. 设备侧 `search_terms` 现状与 WorkManager 队列残留情况未知（本窗口未做任何设备查询）。
