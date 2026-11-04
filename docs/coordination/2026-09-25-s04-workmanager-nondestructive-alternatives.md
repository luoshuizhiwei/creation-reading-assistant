# S04 WorkManager 非破坏性真机验收替代方案（只读设计审查）

- **日期**：2026-09-25
- **作者**：S04 独立离线设计审查者（与此前方案/审查会话相互独立）
- **性质**：只读审查 + 设计报告。**未执行任何 Gradle / adb / 设备操作 / 安装 / commit / stage / reset**；本文件是本窗口唯一写入。
- **输入**：
  - `docs/qa/2026-09/2026-09-25-s04-workmanager-safe-device-plan.md`（原安全方案）
  - `docs/code-review/2026-09/2026-09-25-s04-workmanager-device-plan-review.md`（对该方案的 P1 复核）
  - `docs/code-review/2026-09/2026-09-25-s04-isolation-independent-review.md`（隔离改动独立审查）
  - 源码只读核对：`AndroidManifest.xml`（主 + androidTest 打包清单）、`App.kt`、`CraIsolatedTestRunner.kt`、`CraIsolatedTestApplication.kt`、`IsolatedTestContext.kt`、`SearchIndexScheduler.kt`、`SearchIndexWorker.kt`、`SearchIndexWorkerPolicy.kt`、`ReadingGoalScheduler.kt`、`SecurePrefs.kt`、`DeviceInfoProvider.kt`、`DatabaseModule.kt`、`AppDatabase.kt`、`build.gradle.kts`、`libs.versions.toml`、全部 38 个 androidTest 源文件（grep + 抽读）、`install_with_confirm.ps1`。

---

## 0. 结论先行

1. **在"不重装主 APK、不改主 APK manifest、不取消用户队列"的约束下，同包（targetPackage = `com.creationreadingassistant`）instrumentation 无法做到零触碰 WorkManager 队列**。`InitializationProvider` 是主 APK 清单里的 ContentProvider，先于任何 Application 代码执行，测试 APK 无法从外部关闭它；它一跑就会打开用户真实 `androidx.work.workdb` 并重新向 JobScheduler 注册作业。原安全方案 §13-D2 承认的"固有副作用"不是方案瑕疵，而是**当前架构下同包路线的数学下界**。
2. **存在一条真正非破坏的替代路线（方案 A）：构建一个带 `applicationIdSuffix` 的沙箱测试宿主变体**，让 instrumentation 的 targetPackage 指向 `com.creationreadingassistant.qahost` 之类的独立包。该包有自己的私有目录、自己的 workdb、自己的 databases/shared_prefs——用户队列、用户书库、用户偏好全部不在其可达范围内。**但这是一项构建变更，本窗口未实现、未编译验证**，其可行性论证见 §3，遗留两个必须先行核实的构建问题（Compose test manifest 的变体作用域、baselineprofile 插件交互）。
3. **因此本报告的判定是：条件性 BLOCKED**。同包路线（方案 C）按约束直接 BLOCKED；方案 A 不被现有约束禁止，但在构建变更落地并编译验证之前，不应视为"已可行的方案"，只视为"唯一值得投入的工程路线"。若用户不接受任何构建变更，则 S04 真机验收在当前约束集内无解，只能等用户放宽某一条约束（如接受原方案 D2 的一次性任务消耗）。

---

## 1. 证据分级约定

本报告严格区分三类陈述：

- **【已证实·源码/清单】**：本窗口直接读到的源码、主 APK / androidTest APK 打包清单、构建脚本。
- **【源码推断】**：由源码逻辑推出、但未经设备运行验证的行为（含历史构建产物佐证但无法证明与当前源码逐字节对应的项）。
- **【设备未知】**：本窗口无任何设备查询，真机上的实际状态全部未知。

---

## 2. 核心判定：同包 instrumentation 为什么绕不开 WorkManager

### 2.1 启动链事实【已证实·源码/清单】

1. 主 APK 合并清单（`build/intermediates/packaged_manifests/debug/...:95-99`，历史产物佐证）声明 `androidx.startup.InitializationProvider`，其下含 `androidx.work.WorkManagerInitializer`。WorkManager 版本 2.9.1（`libs.versions.toml:16`），无 on-demand 配置（全仓库无 `Configuration.Provider` 实现、无 `workManagerConfiguration`，grep 证实）→ 走默认初始化。
2. androidTest 打包清单（`build/intermediates/packaged_manifests/debugAndroidTest/...`）确认 `instrumentation android:name="...CraIsolatedTestRunner"`、`targetPackage="com.creationreadingassistant"`。instrumentation 进程 = 目标包进程，**ContentProvider 表以目标包清单为准**。
3. `CraIsolatedTestRunner.newApplication`（`CraIsolatedTestRunner.kt:34-38`）只换 Application 实例；`CraIsolatedTestApplication` 是裸 `Application()`。换 Application 发生在 ContentProvider 之后，**对 `WorkManagerInitializer` 无任何影响**——这一点三份前置报告一致，本窗口复核成立。
4. 队列内容【源码推断 + 设备未知】：`SearchIndexScheduler.kt:98-99` 的 `search_index_once_v1`（一次性）与 `search_index_daily_v1`（每日周期）、`ReadingGoalScheduler.kt:44` 的 `reading_goal_reminder`（每日周期，若用户开启提醒）都持久化在 workdb。**周期任务只要调度过就恒有 ENQUEUED 行**；一次性任务的当前状态（ENQUEUED/RUNNING/FAILED/SUCCEEDED）是设备未知。

### 2.2 三条"绕开"路径逐一审查（不凭空假定可行）

**路径一：测试专用进程 —— 不可行。**
Android 的数据沙箱（`/data/data/<pkg>/`、workdb、databases、shared_prefs）以 **package + uid** 为键，不以进程名为键。instrumentation 可以跑在 `:xxx` 子进程，但 uid 与目标包相同，`WorkManager.getInstance` 打开的仍是同一个 workdb。换进程只隔离内存，不隔离磁盘队列。【已证实·平台模型 + 源码】

**路径二：provider 初始化控制 —— 不可行（不改主 APK 的前提下）。**
测试 APK 的 manifest 无法移除或禁用目标包清单里的 provider；`tools:node="remove"` 只在合并进主 APK 的那份清单上生效，而改主 APK manifest 意味着重装主 APK（被约束禁止）或改生产清单（把生产启动链当实验品，且仍需重装）。从测试侧 `pm disable` provider 属于修改用户设备全局状态，同样越界。结论：**同包路线下 provider 时序不可控**。【已证实·平台模型】

**路径三：不同 target/package —— 可行，但需构建变更（方案 A，见 §3）。**
若被测应用本身以不同 applicationId 构建（如 `com.creationreadingassistant.qahost`），则其私有目录、workdb 与主包完全隔离。instrumentation targetPackage 指向该包后，`WorkManagerInitializer` 打开的是**空的新 workdb**，无用户任务可认领。这是唯一能在不改生产代码、不动用户数据的前提下满足全部约束的路线。【已证实·平台模型；构建可行性待验证】

**路径四：纯离线测试 —— 部分可行，覆盖不了"真机"目标。**
当前 JVM 单测配置只有 `unitTests.isReturnDefaultValues = true`（`build.gradle.kts:134-135`），**无 Robolectric 依赖**。`EpubParser` 依赖 `android.net.Uri` / `contentResolver`，纯 JVM 跑不了；引 Robolectric 是新依赖的构建变更，且它验证的是"逻辑正确性"，替代不了"真机隔离防线成立"的验收目标。作为方案 B 记录在 §4，不作为主路线。

### 2.3 判定

> **同包 + 不碰用户队列 = 矛盾。** 原安全方案的全部复杂取证（三窗口、workdb 对账、观察窗）都是在为这个矛盾打补丁；P1 复核已指出补丁不闭环。本报告不再修补同包路线，把它整体标 BLOCKED，将工程投入引向方案 A。

---

## 3. 方案 A（推荐工程路线）：沙箱测试宿主变体

### 3.1 思路

新增一个构建变体（如 buildType `qahost`，`initWith(debug)`），带 `applicationIdSuffix = ".qahost"`。该变体产出的 host APK 含全部生产类但独立包名；其 androidTest APK 的 targetPackage 指向 `com.creationreadingassistant.qahost`。安装两者后：

- 用户主包 `com.creationreadingassistant`：**不安装、不重装、不更新、不 force-stop**，其 workdb / databases / files / shared_prefs / Keystore 条目物理上不可达。
- 沙箱包有自己的 workdb：`WorkManagerInitializer` 首次初始化创建的是空队列，**没有任何用户任务可被认领**——原方案最难的 D2 问题在架构层面消失。
- `CraIsolatedTestRunner` 的换 Application 仍保留：它挡住的是 `App.onCreate` 的夜间模式系统级写入（跨包持久的 `settings put`）和 `cra-startup` 接线。沙箱内即使跑了生产启动链也不碰用户数据，但夜间模式是系统设置，防线保留是对的。

### 3.2 最小构建变更清单（仅建议，未实现）

1. `android/app/build.gradle.kts`：新增 buildType `qahost { initWith(getByName("debug")); applicationIdSuffix = ".qahost"; signingConfig = signingConfigs.getByName("debug") }`。**不要**动 `debug` / `release` / 两个 benchmark 变体。
2. **必须核实的遗漏项 1（Compose test manifest 的变体作用域）**：Compose 测试依赖 `debugImplementation(libs.androidx.compose.ui.test.manifest)` 提供 `ComponentActivity`（`build.gradle.kts:229`）。`debugImplementation` 只作用于 `debug` 变体，`initWith` **不继承** configuration。新变体若不补 `qahostImplementation(libs.androidx.compose.ui.test.manifest)`，全部 23 个 Compose 测试类会因缺 Activity 无法启动。这是"initWith 就够了"这一直觉的具体反例，必须写进变更评审。
3. **必须核实的遗漏项 2（baselineprofile 插件交互）**：该插件会为每个非 release 变体生成配套变体（参照 `build.gradle.kts:55-61` 对 benchmarkRelease 的显式声明与注释）。`qahost` 变体是否被它生成意外的 `qahostRelease`/benchmark 回退、是否影响 `matchingFallbacks`，需要一次 `tasks` 枚举核实，不能假定无影响。
4. runner 无需改：`testInstrumentationRunner` 在 `defaultConfig`（`build.gradle.kts:29`），全变体继承。
5. 上述变更落地后先离线编译验证：`assembleQahost` + `assembleQahostAndroidTest`，解包确认 androidTest 清单的 targetPackage = `com.creationreadingassistant.qahost`。**编译与清单核实通过之前，方案 A 不具备执行资格。**

### 3.3 安装纪律（含对原方案的正式修正）

- **未来任何安装（host APK 或 androidTest APK）必须先走 `android/scripts/install_with_confirm.ps1`**，并显式传 `-TestApk -PackageName <该变体测试包名>`（测试包名不可从主包推导，脚本对此有硬校验，`install_with_confirm.ps1:37-48`）。脚本自身的"确认循环超时后直接 adb install"回退（退出码 2）是否放行，应在当次执行时显式选择，不放行时传 `-NoDirectInstall`。
- **直接 `adb install` 只能作为标明状态的回退**：前提是确认循环真实失败（MIUI 拦截、超时）且原始输出已存证。不得以"省一步"为由直接 adb install。此条修正原安全方案 §8 的默认直装路径，与 P1 复核第 3 条一致。
- 安装的是**两个新包**（`com.creationreadingassistant.qahost` + 其 `.test` 包），主包全程不出现在任何安装/卸载命令里。测试结束后的清理只允许卸载这两个沙箱包（属用户可决事项，不自动执行）。
- 需要用户知情的事项：沙箱 host 是一个 debug 签名的完整 app，桌面会出现第二个同名图标（label 相同）、占用一份独立存储；这是方案 A 的固有外观代价。

### 3.4 方案 A 下的数据路径审计（沙箱内）

沙箱包所有写入都落在 `/data/data/com.creationreadingassistant.qahost/`（测试包为 `...qahost.test`，但其组件跑在 host 进程内，写入以 host uid 落盘）。逐类路径见 §6 表格，所有"真实 databases/shared_prefs"字样在该方案下自动降级为"沙箱 databases/shared_prefs"。唯一超出沙箱的已知副作用仍是：夜间模式（已被 runner 防住）与 Android Keystore——`MasterKey` 条目按 uid 命名空间隔离（`SecurePrefs.kt:44-46` 用默认 alias，沙箱 uid 下是另一把键），【源码推断】不触碰主包密钥条目。

### 3.5 方案 A 未证实点（诚实登记）

- 构建变更本身未实现、未编译、未解包验证（§3.2-5 是前置门槛）。
- `run-as`、`sqlite3`、安装确认脚本在该变体上的行为沿用既有 S04 实践记录，属【设备未知】。
- 沙箱包首次启动 WorkManager 时 workdb 为空是【源码推断】（空目录新建库）；严格说验收时仍应做一次 workdb 快照对账，但预期是"从空到测试自身产生"，不存在用户行。
- MIUI 对同签名不同包的安装是否有额外限制（如"后台弹出界面"授权是按包授予的，沙箱包需要重新授权）——【设备未知】，执行窗口首装时观察。

---

## 4. 方案 B（补充，不替代）：纯离线 JVM 补强

引入 Robolectric（新依赖，构建变更）可把 `EpubParser` 的纯解析逻辑、Room DAO 行为搬到 JVM。**它回答不了**"真机上 `IsolatedTestContext` 防线、Compose 渲染、WorkManager 时序"这类问题，因此只能减少需要上设备的测试量，不能取消真机验收。若方案 A 构建变更被否决，方案 B 是唯一还能推进的离线工程，但 S04 的"真机验收"目标本身仍将处于 BLOCKED。本窗口不建议立即引入——先看用户对方案 A 的取舍。

---

## 5. 方案 C（同包直跑，BLOCKED，仅存档）

即原安全方案路线。按本窗口约束（不消费/不改写用户 workdb）直接 BLOCKED，理由见 §2。若用户将来显式接受原方案 D2（一次性索引任务可能被认领、3 次失败进 FAILED 终态，【源码推断】下次打开 App 由 KEEP 策略重新入队），则该路线在"用户知情接受副作用"前提下可恢复——但那是放宽约束，不是本报告的交付物。原方案 §9 第 4 级"全量 androidTest"按 P1 复核维持否决：未隔离的持久写入类（§6 标红三类）必须先单独处置。

---

## 6. 逐类测试可跑性 / 数据路径清单

按本窗口 grep + 逐文件核对的 38 个 androidTest 文件（34 个测试类 + Runner + Application + IsolatedTestContext + 1 个非测试类文件）：

**路径列的适用前提**：`真实` = 同包方案 C 下（正式应用私有目录 `/data/data/com.creationreadingassistant/`）；`沙箱` = 方案 A 下（`/data/data/com.creationreadingassistant.qahost/`）。方案 A 下所有行全部可跑；方案 C 下仅"可跑（白名单）"行可考虑且仍带 workdb 全局副作用。

### 6.1 每一类 instrumentation 运行的全局前置代价（与测试类无关）

| 项 | 路径 | 性质 |
|---|---|---|
| `WorkManagerInitializer` 打开/创建 workdb | `真实|沙箱 databases/androidx.work.workdb`（+wal/shm） | 方案 C 下触碰**用户队列**（核心阻塞）；方案 A 下为空库 |
| WorkManager 初始化向 JobScheduler 重注册作业 | 系统侧（`dumpsys jobscheduler` 可见） | 【源码推断】非持久文件写入 |
| instrumentation 接管目标进程 | — | 方案 C 下会中断主 App 后台运行（不开 App 时无感） |

### 6.2 文件 / Room 持久化类（方案 C 下默认排除的三类）

| 测试类 | 触及路径（方案 C = 真实；方案 A = 沙箱） | 方案 C 处置 |
|---|---|---|
| `AppDatabaseMigrationTest` | `databases/migration-test*`、`databases/migration-open-callback-test*`（含 wal/shm；`AppDatabaseMigrationTest.kt:36-38,2258,2329`）；用例崩溃可残留 | **排除**（写真实 databases/，P2-7） |
| `ChapterReadRepositoryPersistenceTest` | `databases/chapter-read-repository-persistence-test.db`（+wal/shm），@Before/@After `deleteDatabase`（`ChapterReadRepositoryPersistenceTest.kt:20,24-29,55`） | **排除**（同上） |
| `ReadingSessionRecorderPersistenceTest` | `shared_prefs/cra_device_identity.xml`（经 `DeviceInfoProvider` → `SecurePrefs.getOrCreateAppInstanceId`，getOrCreate 语义不覆盖已有值，`SecurePrefs.kt:60-77`）+ Android Keystore MasterKey 条目；Room 为 inMemory | **排除**（真实 shared_prefs + Keystore，P2-8） |

### 6.3 文件隔离类（本轮验收的核心对象）

| 测试类 | 触及路径 | 方案 C 处置 |
|---|---|---|
| `EpubParserInstrumentedTest` | `cacheDir/epub-parser-isolated-test/<UUID>/`（testRoot，`EpubParserInstrumentedTest.kt:39-42`），双防线 + `assertInsideRoot`；@After 校验后删除 | 可跑；预期零正式目录写入（防线本身待真机证实） |
| `EpubReplaceRoomIsolatedTest` | `cacheDir/s04-epub-room-iso-<UUID>/`（`EpubReplaceRoomIsolatedTest.kt:99`）+ inMemory Room；KDoc 自带"只用 inMemoryDatabaseBuilder"结构性断言（:392-393） | 可跑；同上 |

### 6.4 inMemory Room / 仓储类

| 测试类 | 触及路径 | 方案 C 处置 |
|---|---|---|
| `ChapterReadDaoTest`、`TaxonomyOrderingDaoTest`、`ParentUpsertCascadeSafetyTest`、`BookDeletionPersistenceTest`、`ReadingStatsAggregationPersistenceTest`、`LocalExportRoomRollbackTest` | 全部 `Room.inMemoryDatabaseBuilder`（grep 逐一核实），无设备持久写入；`LocalExportRoomRollbackTest` 的 `importFromString` 只写内存库，contentResolver 分支未被这些用例走到【源码读证】 | 可跑（白名单） |

### 6.5 Compose UI 类（23 个）

`LayoutComponentsTest`、`CraSliderRtlRenderTest`、`HomeScreenComposeTest`、`InspirationComposeTest`、`LibrarySortControlsTest`、`ReaderScreenTest`、`ReaderAccessibilityLayoutTest`、`ReaderSelectionToolbarTest`、`ReaderBottomActionsTest`、`ReaderRulesSheetTest`、`ReaderSearchSheetTest`、`ReaderSettingsSheetTest`、`ReaderTocSheetTest`、`ShelfScreenComposeTest`、`StatsComposeTest`、`MyReadingTimelineRowGestureTest`、`DurationPillGestureTest`、`DurationUnitToggleEntryTest`、`PagedEpubContentTest`、`PageTurnerTest`、`PageTurnerGestureTest`、`PageTurnerHighSpeedTest`、`PageTurnerStaticSurfaceTest`

- 触及路径：均为 `createComposeRule()` + 手工组装状态（grep 证实 0 处 `createAndroidComposeRule<MainActivity>`），无设备持久写入。
- 方案 C：可跑（白名单），但每一类都逃不掉 §6.1 的 workdb 前置代价——这正是方案 C 整体 BLOCKED 的原因。
- 方案 A：可跑，前提是 §3.2-2 的变体 manifest 依赖已补。

### 6.6 汇总

- **方案 A**：§6.2 三类在沙箱内解除排除，全部 34 个测试类可跑；仍建议分批（先隔离类、后 Compose、最后持久化类）以保留定位能力。
- **方案 C**：最多 31 类"白名单可跑"，但全局 workdb 副作用无法豁免 → 整体 BLOCKED；三类持久写入类无条件排除。

---

## 7. 原方案的安装步骤修正（正式记录）

1. 未来执行窗口的**第一步安装操作是 `android/scripts/install_with_confirm.ps1`**，不是 `adb install`。传参必须显式给 `-PackageName`（测试包名不可推导）；沙箱方案下包名换成本变体的测试包。
2. 确认循环失败（超时 / MIUI 拦截）→ **保存原始输出为证据** → 此后才允许直接 adb install，且在验收报告中明确标注"回退安装 + 失败证据文件名"。
3. 该修正同时适用于主 APK（如有朝一日需要重装）与任何测试/host APK。原安全方案 §8 的失败回退表中"签名冲突 → 自动卸载测试包重装"维持 P1 复核的否决：卸载是独立设备写操作，必须单独征得同意。

---

## 8. 默认禁止事项（本方案全文遵守，执行窗口同）

不自动取消/清空 WorkManager 任务、不卸载主包、不 force-stop 主 App、不运行未逐类审计的全量 androidTest、不删除/修改用户书籍数据、不使用 MuMu 模拟器、`stay_on_while_plugged_in` 改动必须恢复原值。

---

## 9. BLOCKED 汇总

| # | 事项 | 状态 | 出路 |
|---|---|---|---|
| B1 | 同包路线零触碰 workdb | **BLOCKED（架构性）** | 放弃该路线，转方案 A；或用户显式接受原方案 D2 副作用 |
| B2 | 方案 A 构建变更 | 未实现、未编译验证 | 需一个实现窗口完成 §3.2 五项并离线编译 + 清单核实 |
| B3 | Compose test manifest 变体作用域 / baselineprofile 交互 | 未核实（方案 A 的两个具体技术前提） | 并入 B2 的实现窗口 |
| B4 | 设备侧一切状态（serial、workdb 现状、run-as/sqlite3 可用性、MIUI 安装策略） | 【设备未知】，本窗口未查询 | 执行窗口阶段 0 只读取证 |

三项用户决策（原方案 D1/D2/D3）在本方案下不再需要：D1（force-stop 取舍）随同包路线废弃而消失；D2（接受任务消耗）在方案 A 下无此问题；D3（cra_device_identity 新建）在方案 A 下落在沙箱内，自动消解。

---

## 10. 隐私纪律

本报告与未来全部证据使用中性标识（"测试 EPUB""测试 TXT"、UUID/哈希前缀）；不记录真实书名、不展开 shared_prefs 与库内容原文。源码核查中出现的字面量（如"测试 EPUB"）均为测试代码内置的中性固定字符串，非用户数据。

## 11. 本窗口限制声明

- 未运行 Gradle、adb、任何设备查询或安装；未读取原始仓库（原 worktree 之外）或 Claude 独立记忆库；未 commit/push/stage/reset/checkout/clean。
- 打包清单结论读取的是 `build/intermediates/` 现存产物（历史构建），无法证明与当前工作区源码逐字节对应；此不确定性沿袭隔离审查 §6-1，执行窗口的版本闭合步骤（freshly build + APK sha256 + 清单解包）仍是必做项。
- §3.2 的构建变更建议**未实现**；在实现并验证前，方案 A 是"设计上成立、工程上待证"的路线，不是可立即执行的方案。
