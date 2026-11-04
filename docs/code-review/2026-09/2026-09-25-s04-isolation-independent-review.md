# S04 隔离改动独立审查（挑战性复核，只读）

- **日期**：2026-09-25
- **审查员**：S04 独立只读审查员（非前两轮实现者）
- **仓库 / 分支 / HEAD**：`creation-reading-assistant-recovery-20260919` / `codex/recover-worktree-20260920` / `5aed593`（工作区含大量未提交改动，全部保留）
- **审查对象**：
  - `CraIsolatedTestRunner.kt` / `CraIsolatedTestApplication.kt`（新增）
  - `build.gradle.kts:29`（runner 切换，集成者）
  - `EpubParserInstrumentedTest.kt` + `IsolatedTestContext.kt`（清理修复）
  - `App.kt`、`AndroidManifest.xml`、`EpubParser.kt`、`EpubRepository.kt`、`SearchIndexScheduler.kt`、`SearchIndexWorker.kt`、`ReadingGoalScheduler.kt`、`DatabaseModule.kt`、`SecurePrefs.kt`、`JsonBridge.kt`
  - 代表性其他 androidTest：`AppDatabaseMigrationTest`、`EpubReplaceRoomIsolatedTest`、`ChapterReadRepositoryPersistenceTest`、`LocalExportRoomRollbackTest`、`ReadingSessionRecorderPersistenceTest`、`BookDeletionPersistenceTest`、`ReaderScreenTest`、`HomeScreenComposeTest`、`ReaderSettingsSheetTest`
- **本窗口未执行**：Gradle、adb、任何设备/书籍数据操作、commit/push/stage/reset/checkout/clean。全部结论来自静态阅读；合并清单结论读取的是 `build/intermediates/` 现存产物（无法证明与当前源码逐字节对应，见 §6 未知）。
- **唯一写入**：本报告。

---

## 0. 结论先行（三句）

1. **Runner 换 Application 的机制本身成立**：与 Hilt 官方 `CustomTestRunner` 完全同构，`App.attachBaseContext` / `App.onCreate` 都不会被调用，静态证据链完整。
2. **但"隔离"有一个两轮报告都没登记的残余缺口**：真实 App 此前入队、持久化在 `androidx.work.workdb` 里的后台任务（搜索索引一次性任务 + 每日任务 + 阅读目标提醒），在 instrumentation 进程启动时仍可能被 WorkManager 认领执行；worker 内部的 Hilt 入口在裸 Application 下会立刻抛异常、不会写到正式库——这是源码级推断，未经设备验证，且 workdb 本身会被改写。
3. **EPUB parser 测试的文件隔离成立**，写入路径核对无逃逸；发现两处小问题（@Before 先建目录后检查、@After 异常遮蔽），均不阻塞真机验收。

---

## 1. 问题一：Runner.newApplication 能否真正避开 App.attachBaseContext / onCreate

### 1.1 机制判定：**成立**（静态 + 打包清单双重证据）

- `CraIsolatedTestRunner.kt:32-36` 只覆写 `newApplication(ClassLoader, String, Context)`，委托 `super.newApplication(cl, CraIsolatedTestApplication::class.java.name, context)`。这与 Hilt 官方文档的 `HiltTestApplication` 接入写法逐字同构，是 AndroidX test 明确支持的覆写点。`attachBaseContext` / `onCreate` 只作用于被实例化出来的那个对象，替换实例后 `App.kt:61-86`（attachBaseContext）与 `App.kt:88-164`（onCreate 含 `cra-startup` 三项接线，`App.kt:141-162`）都不会执行。
- 打包清单证据：`build/intermediates/packaged_manifests/debugAndroidTest/processDebugAndroidTestManifest/AndroidManifest.xml:9-14` 中 `instrumentation android:name="com.creationreadingassistant.testing.CraIsolatedTestRunner"`、`targetPackage="com.creationreadingassistant"`，与 `build.gradle.kts:29` 一致。
- `App` 类即使被类加载，也只有 companion 静态方法与 `AppContextHolder`（`App.kt:178-181`，惰性 object，无静态副作用块），不会因类加载产生 I/O。
- 主 APK 合并清单（`build/intermediates/merged_manifest/debug/...:48`）仍是 `com.creationreadingassistant.App`，正式应用不受影响。
- `android/benchmark/build.gradle.kts:14` 仍用默认 runner——宏基准需要真实 App 启动，这是**正确**的差异化配置，不应"统一"。

### 1.2 兼容缺口核对

| 项 | 结论 |
|---|---|
| ContentProvider（`InitializationProvider`：WorkManager/EmojiCompat/ProcessLifecycle/ProfileInstaller + `MlKitInitProvider`） | **仍执行**，且先于 Application。已在新 Application KDoc（`CraIsolatedTestApplication.kt:29-34`）与设计报告 §3.1 登记。判词准确。 |
| Hilt | 34 个 androidTest 文件 grep：`EntryPointAccessors` / `as App` / `HiltTestApplication` / `@HiltAndroidTest` 均 **0 命中**（仅 KDoc 文字提及）。`hilt-android-testing` 依赖（`build.gradle.kts:228`）仍无人使用。裸 Application 无兼容问题。 |
| Compose | 0 处 `createAndroidComposeRule<MainActivity>()`（grep 证实）；Compose 测试 Activity 由 `debugImplementation(libs.androidx.compose.ui.test.manifest)` 在主 APK 声明（合并清单 :235 `androidx.activity.ComponentActivity`），不需要 Hilt 宿主。抽读 `ReaderScreenTest`、`HomeScreenComposeTest`、`ReaderSettingsSheetTest` 均为纯 `createComposeRule()` + 手工组装状态，不依赖 Application 代码。 |
| `ApplicationProvider.getApplicationContext()` 的 Room 测试 | 裸 Application 下 `filesDir`/`cacheDir`/`getDatabasePath` 均可用（框架行为），抽读 6 个文件无 Application 类型强转。**推测**，需设备确认。 |
| `AppLog.debugEnabled` / `AppContextHolder` | grep 证实无测试引用；`writeFatal` 变 no-op（`AppContextHolder.cacheDir` 为 null 时直接 return，`App.kt:43`）。 |
| 手工命令 | `docs/handoff/current.md:304` 已同步新 runner 名，SR-2 已闭环。 |

### 1.3 新发现的缺口（P1-1）：workdb 持久化任务可能在测试进程内执行

设计报告 §3.1 与新文件 KDoc 只登记了「WorkManagerInitializer 会创建/打开 `androidx.work.workdb`」，**没有登记**：workdb 是持久化队列，真实 App 之前入队的任务（`SearchIndexScheduler.kt:98-99` 的 `search_index_once_v1`（10s 初始延迟）/ `search_index_daily_v1`，`ReadingGoalScheduler.kt:44` 的 `reading_goal_reminder`）会**跨进程存续**。instrumentation 与主应用同包同进程名，WorkManager 初始化后可以认领这些挂起任务。

执行后果（源码级推断）：`SearchIndexWorker.kt:18-21` 与 `ReadingGoalWorker`（`ReadingGoalScheduler.kt:199`）第一步就是 `EntryPointAccessors.fromApplication(...)`，在裸 Application 上会抛 IllegalStateException → 被各自 catch → `Result.retry()` / failure。**正式库写入发生在 `repository().ensureIndexedIncremental()` 之后，异常先于此发生，故预期不写正式库**——但这是推断，且会留下失败/重试记录、改写 workdb（run attempt、重排）。

影响：不构成用户数据风险，但 (a) 「跑后 workdb 无变化」不能写进验收预期；(b) 若 worker 失败重试与设备验收的对账窗口重叠，会产生噪声。设计报告 §7.7 已把 workdb 队列状态列入取证清单，但**预期结果描述需要补上「可能存在失败重试痕迹」**。

---

## 2. 问题二：EPUB parser 测试的写入与 @After 是否被限制在专属 root

### 2.1 写入路径核对：**成立**

对 `EpubParser.kt` 全量 grep `context.` 成员：仅 3 处——`context.filesDir`（:67，内部副本/staging 根）、`context.contentResolver.openInputStream`（:94，只读源）、`context.cacheDir`（:110，兜底读，测试中不可达因为源文件必可读）。三者全部被 `IsolatedTestContext` 覆盖或只读。图片抽取落点 `File(cachedEpubPath).parentFile/"<id>/img"`（`EpubParser.kt:242`）在 testRoot 内，且 `EpubParser.kt:929-931` 自带逃逸断言。`EpubParserInstrumentedTest` 的 `createEpub` 落在 `testRoot/src`（:260）。`loadChapterText` / `loadChapterBlocks` 不接收 Context。**结论：四条用例的全部写入均在本次唯一的 testRoot（UUID）之下。**

每条 JUnit4 用例新建一个测试类实例 ⇒ testRoot 每用例唯一，固定 id（`instrumented-empty-spine` 等）只作用于 testRoot 内部文件名，不再落正式目录。

### 2.2 发现的问题

**P2-1 @Before 先写后检查**（`EpubParserInstrumentedTest.kt:47-49`）：`testRoot.mkdirs()` 在 `assertRootDisjointFromRealStorage()` **之前**。若将来有人把 testRoot 构造改到 `filesDir` 下，目录会在防线执行前已被创建（先污染后报警）。当前构造在 `cacheDir` 下恒安全，故只是防线顺序瑕疵：应先 check 再 mkdirs。`@After` 的顺序是对的（先双防线后删除）。

**P2-2 异常遮蔽与静默残留**：测试体失败后 `@After` 里的 `check` 若再抛，会以防线异常取代原始断言失败（修复报告 risk#3 已自曝，如实）；`testRoot.deleteRecursively()`（:57）返回值被忽略，删除失败静默——残留只在自身 cacheDir 子树内，危害小，但建议 `check(deleteRecursively())` 或至少留日志。

**符号链接 / 路径绕过**：`assertInsideRoot`（`IsolatedTestContext.kt:72-81`）用 canonical 路径 + 分隔符前缀比较，能挡 `..` 与 symlink 逃逸及 `testRoot-evil` 兄弟目录前缀攻击；`assertRootDisjointFromRealStorage`（:64-74）用同一严格度，且双向检查（测试根不落入正式 filesDir、正式 books/epub 不落入测试根）。zip 内不可能构造 symlink 条目被该解析器落盘（只写文件内容），img 扩展名过滤（:924-931）独立设防。**未发现绕过路径。**

**cleanup 兜底**：@Before 失败时 JUnit 仍跑 @After；此时 testRoot 可能不存在，`canonicalFile` 对不存在路径照常工作、`check` 通过、`deleteRecursively` 返回 false——无异常，行为正确。

### 2.3 P2-3 `IsolatedTestContext` 覆盖审计漏了 `getApplicationContext()`

`IsolatedTestContext.kt:34-37` 的「未覆盖成员」清单列了 `getExternalFilesDir` / `getNoBackupFilesDir` / `getDir` / `databasePath`，**漏了 `getApplicationContext()`**：`ContextWrapper.getApplicationContext()` 返回 base 的真实 Application Context，其 `filesDir` 是**正式目录**。当前 `EpubParser` / `EpubRepository` 不经它取路径（grep 证实：`EpubRepository` 仅 `filesDir` :89,:297 与只读 `contentResolver.query/openAssetFileDescriptor` :366,:384），但 `context.applicationContext` 是生产代码常见写法，一旦被测链路改用它即静默逃逸出 testRoot，且 `assertInsideRoot` 只校验调用方传入路径、救不了这种间接取径。建议：登记为已知缺口（最小改动）或后续窗口补 override 委托 `this`。

---

## 3. 问题三：报告 / KDoc 超出证据的断言

| 断言 | 出处 | 复核结果 |
|---|---|---|
| 「34 个 androidTest 中 0 处 EntryPointAccessors / as App / HiltTestApplication / createAndroidComposeRule\<MainActivity\>」 | 设计报告 §2.1/§3.6；`CraIsolatedTestApplication.kt:38-39` | **成立**（本窗口独立 grep 复核一致） |
| 打包清单指向新 runner、targetPackage 正确 | 集成报告两处表格 | **成立**（读 intermediates 清单证实）；但 intermediates 是历史构建产物，无法证明与当前源码对应（见 §6） |
| `CraIsolatedTestRunner.kt:26-28`「该行为**尚未经编译**或设备验证」 | Runner KDoc | **已过时**：集成报告记录 `compileDebugAndroidTestKotlin` / `assembleDebugAndroidTest` 均 EXIT 0。KDoc 落后于事实，应随下次触碰更新（P2-4） |
| 设计报告 §7.5「改动后需重新安装主 APK 与 androidTest APK」 | 设计报告 | **过严**：本轮主 APK 源码零改动，仅需覆盖安装 androidTest APK。重装主 APK 反而会重置 MIUI「后台弹出界面」授权（设计报告 §7.3 自己列了这条），自相矛盾（P2-5） |
| `EpubReplaceRoomIsolatedTest.kt:45` 命题 C「消除它需改 test manifest / runner / build.gradle——超出本窗口所有权，已提交 SEAM REQUEST」 | 该测试 KDoc | 描述的是**历史状态**；runner 已落地后该表述过时，命题 C 现已可改判。未回改属合理（非其所有权），但引用该 KDoc 时须注意时点（P2-6） |
| 修复报告「`IsolatedTestContext` 覆盖范围已 grep 审查」 | 修复报告 §1/风险1 | 审查真实存在但**不完整**（漏 `getApplicationContext()`，见 P2-3） |
| 设计报告 §3.4(c)「SearchIndexWorker 会写 search_terms / search_index_coverage」 | 设计报告 | **成立**（`SearchIndexWorker.kt:22` → `ensureIndexedIncremental`，链路属实），但其措辞未覆盖 §1.3 的 workdb 残留任务路径 |

未发现把「可编译」偷换成「真机零写入」的断言——各报告的「未验证项」章节措辞普遍诚实。

---

## 4. 分级风险清单

### P0（真机验收阻塞）
**无。** 静态审查未发现会直接破坏测试运行或写入用户书籍数据的缺陷。

### P1
| # | 风险 | 位置 | 可复现方式 |
|---|---|---|---|
| P1-1 | workdb 持久化任务（search_index_once_v1 / search_index_daily_v1 / reading_goal_reminder）可能在 instrumentation 进程被认领；worker 首步 Hilt 入口抛异常 → retry/failure；workdb 被改写（attempt/重排）。正式库预期零写入，但未经设备验证 | `SearchIndexScheduler.kt:34-49,79-92`；`SearchIndexWorker.kt:18-21`；`ReadingGoalScheduler.kt:66-87,198-209` | 设备上存在历史入队任务时跑任意 androidTest（workdb 见 §7.7 取证）；验收前可先查 workdb 挂起任务并取消唯一任务名 |
| P1-2 | `IsolatedTestContext` 未覆盖 `getApplicationContext()`：被测代码若改用 `context.applicationContext.filesDir` 即静默逃逸到正式目录，防线无法拦截这种间接取径 | `IsolatedTestContext.kt:34-37,45-49` | 需要先有生产代码改动才触发；属「将来的静默缺口」，建议登记或补 override |

### P2
| # | 项 | 位置 |
|---|---|---|
| P2-1 | @Before 先 `mkdirs()` 后防线（先写后检查顺序颠倒） | `EpubParserInstrumentedTest.kt:47-49` |
| P2-2 | @After 防线异常会遮蔽原始测试失败；`deleteRecursively()` 返回值被忽略 | `EpubParserInstrumentedTest.kt:52-58` |
| P2-3 | 覆盖审计漏 `getApplicationContext()`（与 P1-2 同源，文档面） | `IsolatedTestContext.kt:34-37` |
| P2-4 | Runner KDoc「未经编译」已过时 | `CraIsolatedTestRunner.kt:26-28` |
| P2-5 | 设计报告 §7.5「两个 APK 都要装」过严且与其 §7.3 MIUI 授权提醒矛盾 | 设计报告 |
| P2-6 | `EpubReplaceRoomIsolatedTest` 命题 C 表述停留在此前状态 | `EpubReplaceRoomIsolatedTest.kt:45` |
| P2-7 | 文件库测试在**正式 databases/ 目录**写/删独立命名 DB（`migration-test`、`migration-open-callback-test`、`chapter-read-repository-persistence-test.db`）；MigrationTestHelper 在用例崩溃时可能留下残留文件。与正式库 `creation_reading_assistant_native`（`AppDatabase.kt:159`）不同名，非用户数据风险，但 §7.7 对账应含 databases/ 目录文件清单，而不只三表 | `AppDatabaseMigrationTest.kt:36-38,2269,2329`；`ChapterReadRepositoryPersistenceTest.kt:20,24-29` |
| P2-8 | `ReadingSessionRecorderPersistenceTest` 经 `DeviceInfoProvider` → `SecurePrefs.getOrCreateAppInstanceId` 在**真实 shared_prefs** 创建/读取 `cra_device_identity` 加密 prefs（含 Keystore MasterKey）。getOrCreate 语义下不覆盖已有 id，但属真实偏好项副作用；§7.7 的 shared_prefs 清单需含该文件名 | `ReadingSessionRecorderPersistenceTest.kt:72`；`SecurePrefs.kt:67-71` |
| P2-9 | benchmark 模块刻意保留默认 runner，建议在文档注明，防后续误「统一」 | `android/benchmark/build.gradle.kts:14` |

---

## 5. 真机验收前阻塞项 / 前置要求汇总

1. **无 P0 静态阻塞**，可进入真机验收准备，但以下必须写入验收方案：
2. P1-1：跑前查 workdb 挂起任务；验收预期写明「workdb 可能出现 worker 失败重试痕迹，正式三表 + search_terms / search_index_coverage 预期零变化」。
3. 只覆盖安装 androidTest APK；**不要**重装主 APK（避免重置 MIUI 后台弹出授权）。
4. §7.7 取证清单增补：`databases/` 目录**文件清单**（P2-7 的三个测试库名）、`shared_prefs/cra_device_identity*`（P2-8）。
5. `adb devices` 核对 serial、`stay_on_while_plugged_in` 记录/恢复、恢复点 tar + integrity_check——沿用设计报告 §7 既有条目。
6. 隐私纪律：报告与证据只用中性标识（本轮全部源码与文档核查未发现真实书名）。

---

## 6. 剩余未知（诚实登记）

1. 本窗口禁止 Gradle/设备，**未独立复跑编译**；「可编译」的依据是集成报告文字 + `build/intermediates` 现存清单，无法排除「报告后源码又改动」的窗口期。
2. WorkManager 残留任务在 instrumentation 进程的**实际**认领行为与失败形态（P1-1）——源码推断，需一次设备运行定论。
3. 26 个 Compose 测试在新 Application 下是否 100% 无回归——静态证据（主题来自 manifest、无 Hilt/Activity 依赖、无 Application 强转）支持，仍需设备逐类跑。
4. 设备当前 workdb 队列、`search_terms` 现状、正式库 `user_version`（决定 DatabaseSafetyNet 快照是否触发）——本窗口未做任何设备查询，未知。
5. 裸 Application 下 `androidx.startup` 四个 Initializer 是否全部成功执行（推测会，未验证）。
