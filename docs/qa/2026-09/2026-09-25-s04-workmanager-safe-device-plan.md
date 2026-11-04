# S04 真机验收方案：不动 WorkManager 队列、不重装主 APK 的安全执行设计

- **日期**：2026-09-25
- **作者**：S04 真机验收方案独立只读设计者（与测试代码修复会话并行）
- **性质**：**只写本报告，未执行任何 adb / Gradle / 设备操作 / 安装**。本方案是给后续执行窗口的规格说明书。
- **输入依据**：
  - `docs/code-review/2026-09/2026-09-25-s04-isolation-independent-review.md`（P1-1 / P2-7 / P2-8 分级风险）
  - `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-isolated-runner-integration.md`（测试 APK 哈希 `F78904E6…` 为当时的版本）
  - `docs/qa/2026-09/2026-09-25-s04-retest-independent-reconciliation.md`（§5 版本失同步教训：**执行版本 ↔ 现存源码必须重新闭合**）
  - `docs/coordination/2026-09-25-s04-instrumentation-startup-isolation-design.md` §7（取证清单母版）
  - 源码：`SearchIndexWorker.kt`、`SearchIndexScheduler.kt`、`SearchIndexWorkerPolicy.kt`、`ReadingGoalScheduler.kt`、`App.kt:141-162`、`AppDatabase.kt:159`、`SecurePrefs.kt:60-71`、`CraIsolatedTestRunner.kt` / `CraIsolatedTestApplication.kt`

---

## 0. 结论先行（三句）

1. **可以设计出满足全部约束的验收流程**：只覆盖安装 androidTest APK、手工 `am instrument`（绕开 Gradle connected 任务的自动卸载）、跑前只读取证 + 可验证恢复点、跑后复原常亮并保证主 App 仍安装。
2. **"零变化"不可能整体成立，必须换成"预期变化白名单"**：workdb 会被改写（任务被 instrumentation 进程认领，worker 在裸 Application 上因无 Hilt 入口抛异常 → retry/failure，这是静态证实的行为），正式 Room 库的 `books` / `reading_progress` / `library_source_refs` / `search_terms` / `search_index_coverage` 预期零逻辑变化（行级对比验证，不假设）。
3. **有两个事项必须用户拍板才能执行**（见 §13）：跑前快照是否 force-stop（一致性 vs 自然推进的二选一），以及接受"一次性索引任务可能在验收窗口被消耗/留下失败记录（下次正常打开 App 自动重新入队恢复）"这一不可在不取消队列前提下避免的固有副作用。

---

## 1. 硬约束 → 方案落点对照

| 约束 | 方案落点 |
|---|---|
| 不取消/清空用户设备已有 WorkManager 任务 | 全程无 `cancelUniqueWork` / `workManager.cancelAllWork()` / 卸载主应用（卸载会清 workdb）。**不 force-stop 之外的任何方式触碰队列**；force-stop 本身是否允许见 §13 决策 1 |
| 不重装主 APK | 只 `adb install -r -t` androidTest APK；**禁用 Gradle `connectedDebugAndroidTest`**（AGP 结束时会卸载主应用，见隔离设计报告 §7.4），改手工 `am instrument` |
| 不碰原书字节 | `files/` 全树哈希清单跑前跑后对比；任何测试阶段不向 `files/books/` 注入、替换、删除文件 |
| 不恢复/删除用户数据 | 恢复点仅作对账基线，**不演练恢复**，失败时也不建议恢复（是否恢复由用户另行决定） |
| 区分生产自然推进 vs instrumentation 副作用 | §4 归因框架：三窗口取证 + WorkSpec 字段增量 + 观察窗归零校验 |
| 复原 stay-awake、主 App 仍安装 | §11 复原清单（无论成败必执行） |
| 竖屏 | §5 只读记录方向设置，不修改任何系统设置（stay-awake 除外） |
| 不记录真实书名 | §14 隐私纪律 |

---

## 2. 被验收对象的静态事实（决定预期变化白名单）

### 2.1 instrumentation 启动链（换 Application 后仍会发生的）

1. `InitializationProvider`（ContentProvider，先于 Application）→ `WorkManagerInitializer` 创建/打开应用私有目录的 `androidx.work.workdb`。**这不依赖换 runner，无法避免。**
2. `CraIsolatedTestRunner.newApplication` 注入裸 `CraIsolatedTestApplication`：`App.attachBaseContext` / `App.onCreate`（含 `cra-startup` 三接线、夜间模式设置、StrictMode）**不执行**。
3. workdb 是持久化队列：真实 App 此前入队的 `search_index_once_v1`（一次性，10s 初始延迟）、`search_index_daily_v1`（每日周期）、`reading_goal_reminder`（每日周期，若用户开启提醒）在 instrumentation 进程内**可被认领执行**。

### 2.2 worker 在裸 Application 下的预期行为（源码推断，验收正是要验证）

| Worker | 首步 | 预期结果 | 对设备的影响 |
|---|---|---|---|
| `SearchIndexWorker`（`SearchIndexWorker.kt:18-21`） | `EntryPointAccessors.fromApplication` | 抛 IllegalStateException → 被 catch（`:43`）→ `searchIndexWorkerFailureAction`：前 2 次 retry，第 3 次 failure（`SearchIndexWorkerPolicy.kt:9`，共 3 次） | 不写正式库（异常先于 `ensureIndexedIncremental`）；workdb 写入 attempt/重排/终态 |
| `ReadingGoalWorker`（`ReadingGoalScheduler.kt:198-208`） | 同上 | 抛异常 → catch → `Result.retry()`，**无 attempts 上限** | 不写正式库；workdb 可能反复重试直到进程结束 |

**推论**：一次性任务 `search_index_once_v1` 若在验收窗口被认领，最多 3 次尝试后进入 FAILED 终态——队列没被取消，但该唯一名任务被消耗；下次用户正常打开 App 时 `App.onCreate → SearchIndexScheduler.start() → enqueueOnce()` 用 KEEP 策略对已结束 work 会**插入新任务**（`SearchIndexScheduler.kt:32` 注释），即自动恢复。这是"固有副作用"的核心内容，需用户确认（§13 决策 2）。

### 2.3 各 androidTest 的设备写入面（P2-7 / P2-8，登记进白名单）

| 测试类 | 对真实设备目录的写入 |
|---|---|
| `AppDatabaseMigrationTest` | 在正式 `databases/` 创建/删除 `migration-test`、`migration-open-callback-test`（+wal/shm）；用例崩溃时可能残留 |
| `ChapterReadRepositoryPersistenceTest` | 同上，库名 `chapter-read-repository-persistence-test.db`；@Before/@After 各 `deleteDatabase` |
| `ReadingSessionRecorderPersistenceTest` | 经 `DeviceInfoProvider` → `SecurePrefs.getOrCreateAppInstanceId` 在真实 `shared_prefs/` 创建 `cra_device_identity.xml`（若不存在；**getOrCreate 语义，不覆盖已有值**），并触发 Keystore MasterKey 相关条目 |
| `EpubParserInstrumentedTest` / `EpubReplaceRoomIsolatedTest` | 隔离修复后写入均在 `cacheDir` 下的临时 testRoot / 内存库，预期不落正式 `files/` / `databases/`（**本验收要验证的正是这一点**） |
| 其余 Compose / inMemory Room 测试 | 预期无设备持久写入 |

正式 Room 库名：`creation_reading_assistant_native`（`AppDatabase.kt:159`），另有 `search_index_state` 表（游标）虽不在任务点名清单，一并纳入只读对账（成本为零，能锁定"worker 没推进过游标"）。

---

## 3. 总体流程（七个阶段）

```
阶段 0  执行前核对（serial / 主APK基线 / 竖屏 / 常亮原值 / APK版本闭合）
阶段 1  跑前只读取证（五表行级导出 + databases文件清单 + files哈希 + shared_prefs + workdb队列 + dumpsys）
阶段 2  可验证恢复点（三目录 tar + sha256 + integrity_check，不演练恢复）
阶段 3  安装 androidTest APK（含失败回退证据）
阶段 4  分阶段执行 am instrument（每阶段之间做增量取证）
阶段 5  跑后取证 + 30 分钟观察窗 + 观察窗后补取证
阶段 6  复原清单（stay-awake、主 App 完整性、可选卸载测试包、logcat 收尾）
```

每个阶段的原始命令输出**逐条落盘到仓库外私有证据目录**（复核报告 §7-2 的教训：不留原始输出就只能"仅报告自述"）。所有命令显式 `adb -s <serial>`。

---

## 4. 归因框架：自然推进 vs instrumentation 副作用

**总原则：不假设"零变化"，用时间窗 + 增量 + 归零校验三重证据归因。**

### 4.1 三个时间窗

| 窗口 | 定义 | 用途 |
|---|---|---|
| W0 | 阶段 1~3（取证→恢复点→安装） | 记录"无 instrumentation 时"的基线漂移：若生产任务在此窗口自己推进，说明设备本来就有活跃后台任务 |
| W1 | 阶段 4（instrumentation 执行期，起止时刻取 `am instrument` 前后的宿主时间戳 + `adb shell date`） | 所有 W1 内出现的 workdb attempt 增量 / worker 失败，**默认归因 instrumentation**（进程此时由 instrumentation 主导） |
| W2 | 阶段 5 观察窗（测试结束、不打开主 App、等待 ≥30 分钟） | 归零校验：W2 内不应再有任何 workdb 增量（无进程执行任务）；若 W2 仍推进 → 存在未识别的执行者，全部结论降级为 BLOCKED |

时钟以 `adb shell date '+%s'` 为准（宿主与设备可能有时差，全程只用设备时钟做对账轴）。

### 4.2 归因信号与局限（诚实登记）

- **可靠信号**：WorkSpec 行的 `run_attempt_count` / `state` / `last_enqueue_time` / `period_start_time` 在 W0/W1/W2 的增量分布；`am instrument` 起止时刻。
- **弱信号（不可单独依赖）**：logcat。`AppLog` 在裸 Application 下 `debugEnabled=false`（`App.onCreate` 才置 true），worker 失败日志进内存环不进 logcat；WorkManager 自身的 `WM-` 前缀日志可能出现但级别/内容不保证。logcat 仍全程流式抓取存档，作为辅助证据。
- **无法从行级区分的情形**：某生产任务恰好在 W1 内、由"假设主 App 正常运行"的场景自然执行。但由于 instrumentation 本身会接管/重启目标进程，主 App 在 W1 内并不独立运行，此类"自然执行"在 W1 内**不存在发生场所**——因此 W1 内的 worker 执行归因 instrumentation 是可靠的，此判断写死进判定规则。
- **残余不确定**：W0 内的基线漂移只记录不归因（属生产自然推进，本验收不评判）。

---

## 5. 阶段 0：执行前核对（全部只读）

1. `adb devices` → 记录 serial；后续全部命令 `adb -s <serial>`。**发现 MuMu/模拟器 → 立即停止。**
2. 设备指纹：`adb shell getprop ro.product.model` / `ro.build.version.release` / `ro.build.version.sdk`。
3. 主 APK 完整性基线：`adb shell pm path com.creationreadingassistant` → 对返回路径 `adb shell sha256sum <path>` 存档（跑后比对证明主 APK 未被动过）。
4. 版本闭合（复核报告 §5 的教训，**必做**）：
   - 本地 freshly built `app-debug-androidTest.apk` 的 sha256（执行窗口现场构建，不得沿用仓库里可能过期的产物）；
   - 解包 APK 确认 manifest 的 `instrumentation android:name="com.creationreadingassistant.testing.CraIsolatedTestRunner"` 与 `targetPackage="com.creationreadingassistant"`；
   - 记录本轮测试源码文件清单 sha256（至少：Runner、Application、`EpubParserInstrumentedTest`、`EpubReplaceRoomIsolatedTest`、`AppDatabaseMigrationTest`）。
5. 竖屏与方向（只读记录）：`adb shell settings get system user_rotation`、`accelerometer_rotation`、`wm size`。不修改。
6. **stay-awake 原值**：`adb shell settings get global stay_on_while_plugged_in` → 原值逐字记入证据文件（复原凭据）。
7. 设备 sqlite3 可用性探测（决定 §7 导出通道）：`adb shell which sqlite3`。
8. 确认主 App 当前未处于用户正在使用的状态（与用户口头确认即可；instrumentation 会接管目标进程）。

---

## 6. 阶段 1：跑前只读取证

### 6.1 正式 Room 五表（+search_index_state）行级导出

通道按 §5-7 探测结果二选一：

- **通道 A（设备有 sqlite3，首选）**：
  `adb shell "run-as com.creationreadingassistant sqlite3 -readonly databases/creation_reading_assistant_native '<SQL>'"`
  SQL：对 `books` / `reading_progress` / `library_source_refs` 全行全列 `SELECT *`；对 `search_terms` / `search_index_coverage` / `search_index_state` 导出行数 + 聚合摘要（如 `COUNT(*)`、按 book_id 的 MIN/MAX、`search_index_state` 全行）。
- **通道 B（无 sqlite3）**：`adb shell "run-as com.creationreadingassistant tar -c databases/creation_reading_assistant_native databases/creation_reading_assistant_native-wal databases/creation_reading_assistant_native-shm"` 拉到宿主后本地打开导出。**三件套必须同一条命令内打包**（避免 WAL 拆猫），导出后跑 `PRAGMA integrity_check`；若失败则重拍一次，仍失败 → 该表对账标 UNVERIFIED，不得宣称零变化。

### 6.2 databases/ 文件清单

`adb shell "run-as com.creationreadingassistant ls -la databases/"` → 逐文件名/大小存档。既有清单中若已存在 `migration-test*` 等 P2-7 测试库残留，先记下来（跑后区分"新增"与"遗留"）。

### 6.3 files/ 全树哈希（原书字节防线）

`adb shell "run-as com.creationreadingassistant find files -type f -exec sha256sum {} \;"` → 清单（路径+哈希+大小）存档。**注意**：cache 下大文件可能多，限定 `files/` 即可（不含 cache）。跑后重跑同命令 diff，**任何差异 = 硬 FAIL**（原书字节约束）。

### 6.4 shared_prefs 清单

`adb shell "run-as com.creationreadingassistant ls -la shared_prefs/"` + 每个文件的大小与 sha256（内容不导出，避免敏感信息入证据；cra_device_identity 若已存在只记"存在"与哈希，不展开内容）。

### 6.5 workdb 队列快照

- 通道 A：`sqlite3 -readonly databases/androidx.work.workdb "SELECT id, state, run_attempt_count, last_enqueue_time, period_start_time, interval_duration FROM WorkSpec ORDER BY id;"`（表名以实际 schema 为准：先 `.tables`，核心是 WorkSpec）。
- 通道 B：tar 三件套拉回宿主查询。
- 辅助（无需 run-as）：`adb shell dumpsys jobscheduler | grep -B2 -A15 com.creationreadingassistant`，存档 JobScheduler 侧作业视图。

### 6.6 logcat 起始

不 clear 缓冲区（避免写状态）。开始流式：`adb logcat -v time -T 1 > evidence-logcat-w0.txt`（宿主落盘），贯穿 W0→W2 全程；`-T 1` 后续按时间戳切窗。

---

## 7. 阶段 2：可验证恢复点

1. `adb shell "run-as com.creationreadingassistant tar -c databases files shared_prefs"` → 宿主存 `cra-s04-wm-safe-pre.tar`。
2. 可验证性三件事：
   - 宿主 `sha256sum` 存档；
   - `tar -t` 清单（文件数、顶层恰为三目录）；
   - 从 tar 解出全部 `.db`（含 wal/shm 三件套）逐一 `PRAGMA integrity_check`，要求全 `ok`。
3. **不做恢复演练**（恢复是写操作）；本 tar 仅作对账基线。失败时是否恢复由用户决定，方案不包含恢复步骤。
4. 若 §13 决策 1 选了"快照前 force-stop"：force-stop 命令 `adb shell am force-stop com.creationreadingassistant` 在本阶段 tar **之前**执行并记录时刻（它同时会暂停生产队列的自然推进，影响已在该决策里评估）。

---

## 8. 阶段 3：安装 androidTest APK（唯一允许的写安装）

```bash
adb -s <serial> install -r -t android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <serial> shell pm list instrumentation com.creationreadingassistant.test
adb -s <serial> shell pm path com.creationreadingassistant.test   # 对该路径 sha256sum，确认设备上=本地 freshly built
adb -s <serial> shell pm path com.creationreadingassistant        # 主 APK 路径+sha256 复核，证明未被动
```

**失败回退证据表**（每种都先存原始输出再行动）：

| 失败 | 回退 | 边界 |
|---|---|---|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE`（测试包签名冲突） | `adb uninstall com.creationreadingassistant.test` 后重装。**只允许卸测试包**，主包一律不碰 | 证据记录卸载前后 `pm path` 主包哈希不变 |
| `INSTALL_PARSE_FAILED_*` / INSTALL_FAILED_INSUFFICIENT_STORAGE | 停止，不重试非 `-r` 的换路径安装；登记 BLOCKED | — |
| 设备上已有旧测试包且哈希 ≠ freshly built | 这正是"覆盖安装"的对象，`-r` 覆盖即可；覆盖前后各记一次 `pm path …test` 的 sha256，证明旧版本被替换 | 版本闭合由这一步完成 |
| 任何时刻主 APK sha256 与 §5-3 基线不符 | **硬停**，BLOCKED | — |

---

## 9. 阶段 4：分阶段执行（手工 am instrument）

**禁用 Gradle connected 任务。** 命令模板：

```bash
adb -s <serial> shell am instrument -w \
  -e class <FQCN>#<method 或省略> \
  com.creationreadingassistant.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
```

推荐分四级推进，**每级之间做 §6.1/6.2/6.5 的增量取证**（级别越低越先验证隔离假设，出问题能定位到最小阶段）：

| 级 | 内容 | 验证目标 |
|---|---|---|
| 1 | `EpubReplaceRoomIsolatedTest` | 最小隔离用例能跑通；workdb 是否出现认领痕迹 |
| 2 | `EpubParserInstrumentedTest` | cacheDir 隔离在真机成立；`files/` 哈希无差异 |
| 3 | 一个 Compose 类（如 `ReaderRulesSheetTest`）+ `ReadingSessionRecorderPersistenceTest`（ cra_device_identity 副作用按 §13 决策 3 取舍） | 裸 Application 下 Compose 无回归；prefs 副作用与白名单一致 |
| 4 | 全量 androidTest（含 `AppDatabaseMigrationTest` 等写真实 databases/ 测试名的类） | 白名单全量校验 |

失败处理：某级出现白名单外的写入（正式五表任何变化 / `files/` 哈希差异）→ **立即停止后续级别**，补齐取证，标 BLOCKED，等用户裁决。

---

## 10. 阶段 5：跑后取证与观察窗

1. 跑后立即重复 §6.1~6.5 全部取证（同通道、同 SQL、同 find 命令），命名 `post-*`。
2. 观察窗 W2：不打开主 App、不做任何设备操作，≥30 分钟；结束时重复 §6.5（workdb）+ dumpsys。判定 W2 归零（§4.1）。
3. 判定矩阵（对照 §2.2/§2.3 的白名单）：

| 观察项 | 预期 | 否则 |
|---|---|---|
| `files/` 全树哈希 | 与跑前逐字节一致 | 硬 FAIL / BLOCKED |
| 正式库 books / reading_progress / library_source_refs | 行级零差异 | FAIL / BLOCKED |
| search_terms / search_index_coverage / search_index_state | 行数与内容摘要零差异（worker 在写库前已抛异常） | FAIL / BLOCKED |
| workdb | W1 内允许出现认领/attempt/终态变化（记录逐条）；W2 归零 | W2 仍推进 → BLOCKED |
| databases/ | 白名单内测试库名可新增/消失；正式库名三件套的 mtime 变化不判零（WAL 正常），以 6.1 行级为准 | 白名单外新增 → 记录并归因分析 |
| shared_prefs | 仅 `cra_device_identity.xml` 可"从无到有"（决策 3 允许时）；其余零差异 | 白名单外 → FAIL |
| 系统设置（夜间模式等） | 零变化（裸 Application 不执行相关代码） | FAIL |

---

## 11. 阶段 6：复原清单（**无论成败、哪怕中途硬停都必须执行**）

1. `adb shell settings put global stay_on_while_plugged_in <§5-6 记录的原值>` → `settings get` 复读，与原值逐字一致才算完成。
2. 主 App 仍安装且未被改动：`pm path com.creationreadingassistant` 非空 + sha256 与基线一致 + `dumpsys package com.creationreadingassistant | grep versionName`。**不启动主 App**（启动会触发 App.onCreate 的生产写入链，何时打开由用户决定；§13 决策 2 已说明下次打开会自动补回被消耗的一次性索引任务）。
3. 测试包处置：默认**保留**（减少一次写操作）；如用户要求清理，仅允许 `adb uninstall com.creationreadingassistant.test`，前后各复核主包哈希。
4. logcat 流停止，全部证据文件清单（含各自 sha256）写入证据目录 README。

---

## 12. 与既有报告的衔接

- 本方案把隔离设计报告 §7.7 取证清单落地为可执行命令，并吸收独立审查 §5 的增补（databases 文件清单、cra_device_identity）。
- 明确修正隔离设计报告 §7-5"两个 APK 都要装"为**只装 androidTest APK**（P2-5 同款结论：重装主 APK 会重置 MIUI"后台弹出界面"授权且本轮主源码零改动）。
- 复核报告 §5 的"执行版本 ↔ 现存源码失同步"由 §5-4/§8 的版本闭合步骤制度化堵住。

## 13. BLOCKED 项与最小用户决策

| # | 事项 | 为什么绕不开 | 需要用户选择 |
|---|---|---|---|
| D1 | 跑前快照是否 force-stop 主 App | 不 force-stop：快照可能撞上生产写入（integrity_check 失败重拍兜底，但基线一致性弱）；force-stop：快照一致，但会**暂停生产后台任务的自然推进**（直到用户下次打开 App），与"观察自然推进"的目标冲突 | **A. 不 force-stop**（保护自然推进，快照弱一致）或 **B. force-stop**（快照强一致，接受暂停）。推荐 A：本验收重点是 instrumentation 副作用，通道 B 的 integrity_check 兜底已够用 |
| D2 | 接受一次性索引任务 `search_index_once_v1` 可能在验收窗口被认领、3 次失败后进 FAILED 终态 | 运行 androidTest 必然启动 WorkManager（ContentProvider），队列持久化使已入队任务可被认领；**避免它的唯一手段是取消任务，被约束禁止**。后果有界：不写正式库，下次正常打开 App 自动重新入队 | 确认"接受该固有副作用"后方可执行；不接受则本验收无法进行 |
| D3 | `ReadingSessionRecorderPersistenceTest` 会在真实 shared_prefs 创建 `cra_device_identity.xml`（仅当此前不存在；不覆盖已有值） | 该测试按现源码必然走 `SecurePrefs.getOrCreateAppInstanceId` | **允许运行并接受新建** / **从全量轮次中排除该类**（第九级第 3 级相应调整） |

除上述三项外无其他阻塞。三项均获得答复前，执行窗口不得开始阶段 3 之后的任何写操作（阶段 0~2 的只读取证可以先做）。

## 14. 隐私纪律

报告与全部证据使用中性标识（"测试 EPUB""测试 TXT"、book_id 的 UUID 或哈希前缀）；不记录真实书名、不展开 shared_prefs 与库内容原文、不截图书库界面可读区。与 AGENTS.md"Android 真机验证约束"一致。

## 15. 本窗口限制声明

- 本窗口未执行 Gradle、adb、任何设备查询或安装；方案中所有命令均为规格，未验证其在目标设备上的当次可用性（如 `run-as` 在 MIUI debug 包上可用性沿用既有 S04 实践记录）。
- §2.2 的 worker 行为是源码级推断（P1-1 性质），验收窗口的真实数据正是对本方案的检验；若实测与白名单冲突，以实测为准并按 §9/§10 降级处理。
- 未 commit/push/stage/reset/checkout/clean；本窗口唯一写入即本文件。
