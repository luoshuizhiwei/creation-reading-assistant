# S04 QA host 沙箱「验机前独立审查」

- 日期：2026-09-25
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 性质：只读独立审查 + 验机前判定报告。本文件是本窗口唯一写入。
- 输入：
  - `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-qahost-offline-integration.md`（集成者离线复验）
  - `docs/coordination/2026-09-25-s04-workmanager-nondestructive-alternatives.md`（方案 A 设计审查）
  - `docs/code-review/2026-09/2026-09-25-s04-workmanager-device-plan-review.md`（原安全方案 P1 复核）
  - 本仓库 AGENTS.md
  - 源码/产物只读核对：`android/app/build.gradle.kts`、`src/main/AndroidManifest.xml`、两份**打包 manifest**（`build/intermediates/packaged_manifests/qahost{,AndroidTest}/`）、两个沙箱 APK 及其 `output-metadata.json`、`CraIsolatedTestRunner` / `CraIsolatedTestApplication` / `IsolatedTestContext`、`EpubParserInstrumentedTest`、`EpubReplaceRoomIsolatedTest`、`EpubParser.kt`、`EpubRepository.kt`、`FileOps.kt`、`App.kt`、`SettingsStore.kt`（BootMirror）、`DatabaseModule.kt`、`AppDatabase.kt`、`SecurePrefs.kt`、`OnboardingOverlay.kt`、`SearchIndexScheduler.kt`、`ReadingGoalScheduler.kt`、`res/xml/backup_rules.xml`、androidTest 源文件清单（37 个文件）。

---

## 0. 结论

**设备验收 BLOCKED；静态审查未发现所检查范围内的 P0/P1。** qahost 沙箱接线在静态证据层闭合（§2/§3），第一轮最小验收对象仍限定为 §4 的两个已逐行审查的测试类——但本窗口**不放行**执行。重新评估是否放行这两个最小用例（不扩大为全量 androidTest）必须同时满足：

1. §5 前置证据全部就绪——特别是**内容级、可重复**的前后对账方法已经落定（Room 逐表内容、workdb 按稳定任务身份、`files/books/epub/` 逐文件内容 SHA-256，见 §5-D），正式主 APK 的包路径/版本/SHA-256 对账项已加入清单（§5-D-10），且**已建立、已核验一致性并完成空间/位置/保留期确认的新恢复点**（§5-C、§6）；
2. 用户对"安装两个沙箱包"给出明确同意——安装本身是设备写操作，静态审查通过不构成预授权；
3. 独立验收窗口准备就绪（实施与审查分离，执行前对照本报告逐项勾验）。

在此之前维持 BLOCKED。两点明确的不可外推（保留）：**"沙箱包已构建成功"不等于"34 个 androidTest 类都可安全运行"**（本轮只有两个类经过本窗口逐行复核，其余 32 类仅沿袭方案 A 文档的判定，未独立复审）；**"打包 manifest 正确"不等于"真机行为正确"**（uid、workdb 真空、run-as、MIUI 安装策略均属设备未知）。

---

## 1. 证据分级约定

沿用三份前置文档：**【已证实·源码/清单】**（本窗口直接读到的源码与打包产物）、**【源码推断】**（源码逻辑 + 平台模型推出、未上机验证）、**【设备未知】**（必须上机才知道）。

---

## 2. 静态复核结果（构建接线与两份打包 manifest）

### 2.1 构建接线【已证实·源码】

| 项 | 证据 |
|---|---|
| `testBuildType` 由 `-PcraAndroidTestBuildType` 显式切换，默认 `debug`（默认行为不受影响） | `build.gradle.kts:16` |
| qahost buildType：`initWith(debug)` + `applicationIdSuffix=".qahost"` + debug 签名 | `build.gradle.kts:105-114` |
| qahost 变体单独补 Compose test manifest 依赖（`initWith` 不继承 `debugImplementation`） | `build.gradle.kts:243-245` |
| `testInstrumentationRunner = ...CraIsolatedTestRunner` 全变体继承 | `build.gradle.kts:32` |
| 无自定义 WorkManager 初始化（无 `Configuration.Provider`）→ 走默认 `InitializationProvider` 初始化 | grep 全 main 源集 0 命中 |

### 2.2 打包产物闭合【已证实·打包清单 + 产物】

| 项 | 值 | 证据 |
|---|---|---|
| host 包名 | `com.creationreadingassistant.qahost`，versionCode 3 / versionName 0.5.1 | `packaged_manifests/qahost/processQahostManifestForPackage/AndroidManifest.xml:3`；`outputs/apk/qahost/output-metadata.json` |
| 测试包名 | `com.creationreadingassistant.qahost.test` | `packaged_manifests/qahostAndroidTest/...:3`；`outputs/apk/androidTest/qahost/output-metadata.json` |
| instrumentation | `com.creationreadingassistant.testing.CraIsolatedTestRunner`，**`targetPackage="com.creationreadingassistant.qahost"`** | `packaged_manifests/qahostAndroidTest/...:9-14` |
| 权威 authority 全部按 applicationId 命名空间 | `.qahost.fileprovider` / `.qahost.androidx-startup`（含 `WorkManagerInitializer`）/ `.qahost.mlkitinitprovider` | host 打包 manifest :77 / :94-110 / :219-223 |
| APK SHA-256（本窗口现场重算，与集成者报告逐字一致） | host `064F53341475CCFD…BC7DC054`；test `FA48A6BC6DA850C3…9A21B54` | `sha256sum` 实测 = `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-qahost-offline-integration.md` §产物清单 |
| host 的 `androidx.activity.ComponentActivity`（Compose test manifest 注入）为 exported=true | 仅存在于 debug 签名的 qahost 包内，标准 androidx 测试组件，可接受 | host 打包 manifest :234-237 |

注意：以上打包清单是**既有构建产物**；执行窗口仍须按三份前置文档的要求做"新鲜构建 + APK sha256 + aapt 解包"的版本闭合（§5-A），不能以本报告替代。

### 2.3 启动隔离三件套【已证实·源码】

- `CraIsolatedTestRunner.newApplication` 把目标 Application 换成裸 `CraIsolatedTestApplication`（`CraIsolatedTestRunner.kt:34-38`），除此之外不做任何事。
- 因此 instrumentation 进程内 **`App.onCreate` / `App.attachBaseContext` 均不执行**，被挡住的启动副作用为【已证实·源码】：
  1. **系统级夜间模式写入**：`BootMirror.applyApplicationNightMode` → `UiModeManager.setApplicationNightMode`（API 31+，系统侧持久写入，`SettingsStore.kt:378-391`；调用点 `App.kt:124-128`）；
  2. **cra-startup 三接线**（`App.kt:141-162`）：`EpubSizeRepairTask.startOnce()`（触碰 `files/books/epub`）、`ReadingGoalScheduler.start()`（WorkManager 周期任务 `reading_goal_reminder` + 通知渠道）、`SearchIndexScheduler.start()`（`search_index_once_v1` / `search_index_daily_v1`，`SearchIndexScheduler.kt:98-99`）；
  3. 正式 Room 库打开与 `DatabaseSafetyNet` 升级快照（`DatabaseModule.kt:55-56`）、`app_prefs` 预热、`cra_fatal.log`。
- 残余（换 Application 不能消除）：`InitializationProvider` 先于 Application 执行，`WorkManagerInitializer` 仍会初始化 WorkManager——但 targetPackage 是 qahost，打开的是**沙箱私有** workdb【源码推断：空库；设备未知：待对账】。

---

## 3. 问题一：独立包名实际隔离了什么，还有什么共享面

### 3.1 已隔离【已证实·源码/清单】

| 维度 | 结论 |
|---|---|
| 应用私有数据（databases / files / cache / shared_prefs / code_cache） | qahost 拥有独立 applicationId ⇒ 独立 uid 与独立 `/data/data/com.creationreadingassistant.qahost/`。正式包 `com.creationreadingassistant` 的 Room 库（`creation_reading_assistant_native`）、书库文件（`files/books/epub/`）、`shared_prefs`、workdb 不在任何被测代码可达路径上（无 `sharedUserId`、无 root、跨 uid 无文件访问） |
| ContentProvider 权威 | 三个 provider authority 全部带 `.qahost` 命名空间（§2.2），与正式包权威不冲突；FileProvider `exported=false` |
| WorkManager / JobScheduler 命名空间 | 按包隔离：`SystemJobService` 注册、workdb 均属 qahost；`search_index_*` / `reading_goal_reminder` 这些**用户队列任务只存在于正式包 workdb**，沙箱 workdb 初始化后没有任何用户任务可认领【源码推断：空库新建】 |
| 被测代码写入面 | 两个目标测试的写入全部被 `IsolatedTestContext` 收敛到沙箱 cacheDir 下 UUID 根目录内（§4 逐项），并带 canonical 路径防线（`IsolatedTestContext.kt:85-94`）与"根 ≠ 正式 filesDir"双防线（`EpubParserInstrumentedTest.kt:76-86`） |
| 系统夜间模式 | instrumentation 期间被 Runner 挡住（§2.3-1） |

### 3.2 合理推断（平台模型成立，未上机验证）

- uid 级隔离在真机上的实际生效（数据目录路径、`dumpsys package` 的 uid 分配）；
- 沙箱 workdb 首次初始化为空库（无 `Configuration.Provider`，空目录新建默认库）；
- Android Keystore 按 uid 命名空间隔离：`SecurePrefs` 用默认 `MasterKey` 别名（`SecurePrefs.kt:44-46`），沙箱 uid 下是另一把键，不触碰主包密钥条目。两个目标测试**均不触及** SecurePrefs / Keystore（EpubRepository 构造只注入 DAO 与 FileOps，无 DeviceInfoProvider 路径）。

### 3.3 仍存在的共享/全局面（逐项列明）

| 共享面 | 状态 | 说明 |
|---|---|---|
| 系统设置（settings 数据库） | 【已证实·源码 + 平台模型】 | 唯一确认的系统设置写入点是 `UiModeManager.setApplicationNightMode`（API 31+，按调用包持久）。instrumentation 期间不执行；**若验收期间有人手动点开沙箱应用图标，`App.onCreate` 会完整执行并发生该写入**（写入的是沙箱包自己的 night mode 条目，但仍是 settings 侧持久写入）。执行纪律：验收窗口内不手动启动沙箱应用 |
| 共享存储（/sdcard、MediaStore、SAF） | 不触碰【已证实·源码】 | 主 manifest 无任何存储权限（仅 INTERNET/CAMERA/POST_NOTIFICATIONS，`src/main/AndroidManifest.xml:6-9`）；两个目标测试只用 `Uri.fromFile` + 沙箱私有目录，可达代码中 `getExternalFilesDir`/`Environment`/`MediaStore`/`DocumentsContract` 零命中（grep 复核）；`contentResolver` 仅 `openInputStream`（`EpubParser.kt:94`）、`query` / `openAssetFileDescriptor`（`EpubRepository.kt:366,384`，对 file:// Uri，只读） |
| 跨应用 ContentProvider | 不触碰【已证实·源码】 | 可达链路上无任何对系统或第三方 provider 的 insert/update/delete/call；测试读源走 file:// 直读，不经 provider |
| 网络 | 权限存在、路径不存在【已证实·源码】 | qahost 带 INTERNET（为 LAN 同步预留）；两个目标测试无任何网络调用。前提仍是"不手动启动沙箱应用"——手动启动后完整生产 App 在沙箱内运行，理论上可发起 LAN 同步扫描 |
| 相机 / 通知权限 | 不使用【已证实·源码】 | 声明了 CAMERA / POST_NOTIFICATIONS，两个目标测试不申请、不使用 |
| 云备份 / adb backup | 沙箱数据可入备份【已证实·manifest】 | qahost `allowBackup="true"`（`backup_rules.xml` 排除三个凭据 prefs，但书库数据参与备份）。首轮沙箱内只有测试自身产生的数据，无用户内容；属知情事项，非风险 |
| 桌面图标 | 外观代价 | 第二个同 label 启动图标（方案 A §3.3 已列的用户知情事项） |
| MIUI 安装策略、设备端一切行为 | 【设备未知】 | 见 §5 |

---

## 4. 问题二：第一轮最小验收的精确测试清单与逐项写入路径

第一轮**只运行以下两个类**（本窗口逐行复核完毕；其余 32 个测试类不因"沙箱包构建成功"而获得放行推定）：

### 4.1 `com.creationreadingassistant.feature.reader.EpubParserInstrumentedTest`

方法（4 个）：`emptySpineFallsBackToReadableManifestItemsAndSkipsNav`、`epub3Nav文档作为目录源`、`epub2Ncx作为目录源`、`无目录时回退到spine并过滤封面`。

| 路径 | 读/写 | 说明 |
|---|---|---|
| `/data/data/com.creationreadingassistant.qahost/cache/epub-parser-isolated-test/<UUID>/` | 写→读→删 | testRoot（`EpubParserInstrumentedTest.kt:39-42`）。合成 EPUB 源（`<testRoot>/src/`）、`EpubParser` 内部副本（经隔离 `filesDir` → `<testRoot>/files/books/epub/`，`EpubParser.kt:67`）、章节图片缓存（`<testRoot>/files/books/epub/<parent>/<id>/img/`，`EpubParser.kt:242`）、旧 cacheDir 兜底路径（`<testRoot>/cache/epub/<id>.epub`，`EpubParser.kt:110`）全部收敛于此；@After 经 `assertInsideRoot` 复核后整根删除（:64-69） |
| 源 Uri 读取 | 只读 | `Uri.fromFile` 直读（`EpubParser.kt:94`），不经任何 provider |
| 其他 | 无 | 无 Room、无 prefs、无 Keystore、无网络、无系统设置。崩溃残留仅限沙箱 cache 子树，卸载沙箱包或清其缓存即消失 |

### 4.2 `com.creationreadingassistant.feature.reader.EpubReplaceRoomIsolatedTest`

方法（6 个）：`sameUriV1ToV2ReplaceReadsFreshBytesAndKeepsBookIdAndProgress`、`invalidEpubReplaceIsRejectedAndPreservesEverything`、`unreadableSourceIsRejectedWithoutTouchingTarget`、`postSwapDatabaseWriteFailureRollsBackFileAndKeepsOldRow`、`databaseUnderTestIsInMemoryAndNeverTheProductionDatabase`、`replaceNeverWritesIntoProductionFilesDirectory`。

| 路径 | 读/写 | 说明 |
|---|---|---|
| `/data/data/com.creationreadingassistant.qahost/cache/s04-epub-room-iso-<UUID>/` | 写→读→删 | testRoot（`EpubReplaceRoomIsolatedTest.kt:99`）。来源 EPUB（`<testRoot>/sources/`）与仓储内部副本（隔离 `filesDir` → `<testRoot>/files/books/epub/`，含 `.importing`/`.backup` 瞬态）都在其下；tearDown 整根删除（:112-118） |
| Room | 仅内存 | `Room.inMemoryDatabaseBuilder`（:106-109）；结构性断言（文件内无 `databaseBuilder`，:392-393）+ 运行期断言库名 ≠ `creation_reading_assistant_native`（:395-402）。**不创建、不打开任何 DB 文件** |
| `appContext.filesDir/books/epub` | 只读探针 | `replaceNeverWritesIntoProductionFilesDirectory` 反向断言正式目录无本次 bookId 文件（:431-440）。在 qahost 下该"正式目录"本身就是沙箱私有目录 |
| 其他 | 无 | saveProgress 只写内存库；无 prefs / Keystore / 网络 / 系统设置 |

### 4.3 每次 instrumentation 运行的全局前置代价（与测试类无关，两类均承担）

| 项 | 路径 | 分级 |
|---|---|---|
| `InitializationProvider` → `WorkManagerInitializer` 初始化 | 在沙箱内创建 `databases/androidx.work.workdb`（+wal/shm），预期空库 | 【源码推断】上机用 run-as 核实"从空到测试自身产生" |
| WorkManager 向 JobScheduler 注册内部作业 | 系统侧，qahost 命名空间 | 【源码推断】`dumpsys jobscheduler` 可见 |
| emoji2 / profileinstaller 初始化缓存 | 沙箱 code_cache 等 | 【源码推断】沙箱私有 |
| `App.onCreate` 三类副作用（夜间模式 / cra-startup / 正式库打开） | **不执行** | 【已证实·源码】Runner 换 Application |

### 4.4 执行方式约束

- 用 `am instrument -e class <FQCN>` 定向运行（建议两类分两次跑，便于归因），组件为 `com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner`；
- **禁止** `connectedQahostAndroidTest` / `connectedDebugAndroidTest` 等 Gradle connected 任务——那会无差别运行全部 34 个测试类；
- 安装走 `android/scripts/install_with_confirm.ps1`，显式 `-TestApk -PackageName com.creationreadingassistant.qahost.test`；确认循环真实失败并留证后才允许标注状态的 adb install 回退（P1 复核第 3 条）。

---

## 5. 问题三：上机前必须核对的条件

**A. 版本闭合（源码 ↔ 产物）**
1. 执行窗口新鲜构建（或核验现有产物未被重构建）：重算两个 APK SHA-256 = §2.2 所列值；
2. `aapt dump badging`（或解包）直接对**APK 文件**确认：host 包名 `com.creationreadingassistant.qahost`、test 包名 `com.creationreadingassistant.qahost.test`、`targetPackage=com.creationreadingassistant.qahost`、Runner `CraIsolatedTestRunner`——本窗口核对的是 packaged_manifests 中间产物，APK 级复核是必做项；
3. 记录源码锚点：本窗口 HEAD = `5aed5939600e491843e3c44453921bd6273a233c`（2026-09-14），工作区 478 个未提交条目，`build.gradle.kts` 差异 +23/−3（qahost 接线）。执行时确认构建接线自本审查后未被改动；
4. 两个 APK 均为 debug 签名；包名与正式包不同 ⇒ 不存在签名冲突替换正式包的可能。

**B. 设备与纪律**
5. 真实手机，先 `adb devices` 核对并记录唯一 serial，所有命令 `adb -s <serial>`；竖屏；不用 MuMu；如临时启用 `stay_on_while_plugged_in` 必须记录原值并恢复；
6. 安装前确认正式包未在运行/前台（降低对账归因难度），但不 force-stop、不卸载、不重装正式包；
7. 安装后核对：`pm list packages | grep qahost` 恰好新增两包；`pm list instrumentation` 显示 test 包的 instrumentation 且 target 为 qahost；`dumpsys package` 确认 qahost 与正式包 uid 不同。

**C. 恢复点 / 基线（先于一切设备写操作，含安装；不可满足则维持 BLOCKED）**
8. 旧恢复点 `20260925-s04-retest`（约 18 GB）已被用户删除以释放 D 盘空间，**不得引用，也不得默认按原体量再生成**。新恢复点目前不存在——在按 §6 完成"所需空间实测估算 + 仓库外保存位置 + 保留期限与销毁方式"三项并获用户确认、恢复点实际建成且一致性核验通过之前，**维持 BLOCKED**。允许向用户提供缩小体量的分档方案（例如"对账基线"与"完整恢复点"两档）由用户选择，不默认执行大体量备份；
9. 明确声明：`PRAGMA integrity_check` 通过只证明 SQLite 结构可读，**不**单独证明业务状态一致，也**不**证明恢复过程已验证；恢复能力在流程未被实际验证或用户明确接受"未经演练"之前一律标 UNVERIFIED。

**D. 正式包前后对账（安装前基线 → 测试后复测；必须内容级、可重复）**
10. **正式主 APK 本体**：安装前记录 `pm path com.creationreadingassistant` 返回的包路径、`dumpsys package` 的 versionName/versionCode、base APK 文件的 SHA-256；测试后逐项复测必须完全一致——防止安装沙箱包的过程以任何方式触碰正式包本体；
11. **沙箱包预检**：安装前用 `pm list packages` 核实设备是否已存在 `com.creationreadingassistant.qahost` / `com.creationreadingassistant.qahost.test`。若已存在：**不得默认它们是全新空包，不得静默覆盖、卸载或重装**；其既有数据与处置决定（保留/清理/放弃本轮）属用户决策，先取证（数据目录大小级摘要）并报告，再等用户决定；
12. Room 正式库 `databases/creation_reading_assistant_native`（+wal/shm，`DatabaseModule.kt:56`、`AppDatabase.kt:159`）：**内容级、可重复的逐表对账**——对每张表（含全文索引表 search_terms / search_index_coverage，以现场 schema 为准）按固定排序键逐行做规范化序列化并生成哈希清单，两次生成必须可重放；必须能发现**同一行内容变化**，而不只是行数增减。逐行哈希清单与敏感行内容只留**仓库外本地私有证据**，仓库报告只写方法、表清单与比对结果摘要（一致/差异计数，不含字段值）。一致快照或逐表内容比较任一无法可靠取得时，该表标 **UNVERIFIED → 整体 BLOCKED**；**不得以行数相同替代通过**；
13. workdb `databases/androidx.work.workdb`（+wal/shm）：按**稳定任务身份**逐项对账——以 WorkSpec id + unique name 标识每条任务（预期：`search_index_once_v1` / `search_index_daily_v1`，`SearchIndexScheduler.kt:98-99`；`reading_goal_reminder`，`ReadingGoalScheduler.kt:44`），逐条比对关键字段：state、run_attempt、period_start_time / interval / flex、last_enqueue_time 等调度时间与约束字段。WorkSpec 总数与状态分布只作摘要呈现，**不构成通过判据**；任务身份消失、新增或任一关键字段变化且无法归因到测试自身产物的，一律标 **UNATTRIBUTED → BLOCKED**，不用时间窗猜测原因（P1 复核第 2 条）；
14. `files/books/epub/`：**逐文件**比较相对路径 + 字节数 + **内容 SHA-256**（全量清单，两次生成逐字节可重放）；新增、删除、内容改变都必须能被发现。文件名以 bookId（派生 id）记录，内容哈希清单属仓库外私有证据；仓库报告只报差异计数与中性结论，**不得出现真实书名**；
15. `shared_prefs/`：文件清单 + 逐文件内容哈希（`app_prefs`、`cra_device_identity.xml`、boot 镜像 prefs、`sync_config`/`webdav_config`/`ai_secrets` 等），仓库只报摘要；
16. 复测与基线逐项零差异是唯一通过标准；任何差异先标 **UNATTRIBUTED → 停**。对账通道（`run-as` + `sqlite3`）在正式包上是否可用属【设备未知】，stage 0 先证可用性；不可用时升级为用户决策，**不得改用破坏性通道，也不得因此降级为行数/文件名级对账**。

---

## 6. 问题四：新恢复点的要求（旧快照约 18 GB 已删除释放空间，不得引用也不得默认复刻）

旧恢复点 `D:\develop\Code\Codex\recovery-snapshots\20260925-s04-retest`（约 18 GB）已由用户删除以释放 D 盘空间。新恢复点目前不存在；**本方案不默认任何"重新生成大备份"**。建立前必须先向用户提交并获确认：所需空间的实测估算、仓库外的具体保存位置、保留期限与到期销毁方式；允许分档（例如仅"对账基线"或完整恢复点）由用户选择。命名建议如 `<日期>-s04-qahost-round1`，路径由用户/执行窗口登记进执行报告。新恢复点必须同时满足以下各组要求：

**空间与位置（先于一致性的可行性前提）**
- 实测估算占用量（按快照文件 + §5-D 对账清单逐项估算并汇总），连同目标盘剩余空间核对后写进执行报告；
- 只存仓库外用户本地目录；不进入 git 仓库、docs/ 或任何同步/上传渠道；
- 保留期限与到期销毁方式由用户确认，记入快照 manifest。

**一致性（可信优先于齐全）**
- 捕获时机：在正式包处于静默期（无前台活动、近期无 WorkManager 执行迹象）时捕获，且先于任何设备写操作；
- 优先用 SQLite 在线一致手段（`run-as` + `sqlite3 .backup` / `VACUUM INTO`）；退而求其次复制 db+wal+shm 三件套时，必须对**副本**跑 `PRAGMA integrity_check`——但该检查通过只证明 SQLite 结构可读，**不**单独证明业务状态一致，也**不**证明恢复过程已验证；在此之前只能称"临时副本"，不得称恢复点（P1 复核第 5 条）；
- 附业务级基线：**内容级**对账清单（Room 逐表逐行哈希、workdb 逐任务关键字段、files/books/epub 逐文件内容 SHA-256，方法同 §5-D）——恢复点的价值不只是能还原，还要能**证明验收前后零变化**；
- 记录捕获时刻的设备 serial、包版本、源码锚点。

**隐私**
- 存放于仓库之外的用户本地目录；**绝不进入 git 仓库或 docs/**；仓库报告只写方法、摘要、差异计数、哈希与中性名称（"测试 EPUB"）；
- 敏感行内容、逐行哈希清单、逐文件内容 SHA-256 清单只留仓库外本地私有证据；shared_prefs 值、真实书名/书封一律不出现；文件名以 bookId（派生 id）记录；
- 定义保留期限与销毁方式（验收报告被用户接受后删除），目录不进入任何同步/上传渠道；
- 明确不备份项：Android Keystore 条目（设备绑定、不可还原），凭据 prefs（`sync_config`/`webdav_config`/`ai_secrets`）可备但不入仓库。

**可恢复性**
- 快照自带 manifest：每个文件的相对路径 + SHA-256 + 字节数 + 捕获时间；
- 附**书面恢复步骤**（目标包名、目标路径、run-as 恢复命令、恢复后完整性核验方法）；恢复演练不做实机演练（那本身是设备写操作），步骤必须写清并经复核；**在流程被实际验证或用户明确接受"未经演练"之前，恢复能力一律标 UNVERIFIED，不得称为"已验证可恢复"**；
- 恢复点只保护正式包数据；沙箱两包的处置以 §5-D-11 预检结果为准（设备若已存在这两包，其保留/清理/卸载由用户决定，不自动执行）。

---

## 7. 问题五：风险与 BLOCKED 判定

**静态：所检查范围内未发现 P0/P1。** 两个目标测试的写入面、ContentResolver 使用、系统设置可达性均与三份前置文档的声明一致，且本窗口独立复核成立；未发现任何可达正式包数据、共享存储、跨应用 provider 或 instrumentation 期间系统设置写入的路径。该结论仅限静态可检范围，不涵盖设备侧行为，也**不构成对设备阶段的放行**——设备验收当前为 BLOCKED（§0）。

**BLOCKED 触发条件（任一出现即停，不得进入或继续设备阶段）：**
- B1：§5-A 版本闭合失败（APK 重算哈希不符 / aapt 包名或 targetPackage 不符 / 接线被改）；
- B2：新恢复点未建立或一致性核验未通过（§6）；
- B3：设备侧 stage 0 发现 qahost 与正式包 uid 相同、authority 冲突、或沙箱 workdb 非空且含非测试产生任务；
- B4：对账出现任何差异且无法归因为测试自身产物（UNATTRIBUTED）；
- B5：安装确认脚本失败后的直接 adb install 回退未留证据文件；
- B6：试图扩大到两个类之外的任何测试（包括"顺手跑全量"）；
- B7：正式主 APK 的包路径 / 版本 / SHA-256 在测试前后不一致（§5-D-10）；
- B8：设备上已存在 qahost 主包或测试包，且未获用户对既有沙箱数据的明确处置决定（§5-D-11）；
- B9：恢复点的空间估算、仓库外位置、保留期限未获用户确认，或恢复点未建立 / 一致性未核验 / 恢复能力仍为 UNVERIFIED（§5-C、§6）。

**未验证项（诚实登记，均不构成本窗口的静态阻塞，但都是上机门槛或后续工作）：**
1. 一切设备侧行为：uid/数据目录实际形态、沙箱 workdb 是否真空、`run-as`/`sqlite3` 可用性、MIUI 安装确认策略、serial 与竖屏——【设备未知】；
2. APK 与当前工作区源码的逐字节闭合需执行窗口新鲜构建复核（本窗口核对的打包清单与哈希属既有产物，且 HEAD 早于全部未提交改动）；
3. Runner 换 Application 在真机上实际生效（App.onCreate 未执行）需 logcat/行为观察证实；
4. 新恢复点尚不存在（旧约 18 GB 快照已被用户删除释放空间）；内容级对账与逐文件内容哈希方法的可重复性尚未验证；空间估算、仓库外位置、保留期限待用户确认；恢复能力 UNVERIFIED；
5. 其余 32 个测试类未做本窗口独立复审（方案 A 文档的"方案 A 下 34 类可跑"结论未被独立复核，不得作为放行依据）；
6. `EpubParserInstrumentedTest` 未显式标注 `@RunWith(AndroidJUnit4)`，按 AndroidJUnitRunner 默认 runner builder 解析运行【源码推断】，与该测试的历史可运行性一致；
7. `EpubReplaceRoomIsolatedTest` 文件头 KDoc 的"命题 C"（instrumentation 会执行 `App.onCreate`）是**debug 同包时代**的旧陈述，在 qahost + 隔离 Runner 下不再成立；属文档陈旧，不影响行为，留给后续窗口顺手修订。
8. 设备是否已存在 qahost 主包 / 测试包未知（预检 §5-D-11）；正式主 APK 的包路径 / 版本 / SHA-256 基线尚未捕获（§5-D-10）。

---

## 8. 窗口限制与隐私声明

- 本窗口未运行 Gradle、adb、任何设备查询或安装；未读取原始仓库、用户真实书籍内容或任何 Agent 记忆库；未 commit / push / stage / reset / checkout / clean；除本报告外零写入。
- 打包 manifest 与 APK 哈希读取的是 `build/` 现存产物；其与当前源码的逐字节对应关系留待执行窗口新鲜构建复核（§5-A）。
- 本报告仅使用"测试 EPUB"、UUID/哈希前缀等中性标识；不记录真实书名、书封、库内容或设备 serial。
