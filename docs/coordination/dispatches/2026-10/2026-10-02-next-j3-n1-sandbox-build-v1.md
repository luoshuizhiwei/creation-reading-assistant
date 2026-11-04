# J3-n1-sandbox-build-v1：N1-v6 联合候选 · 独立沙箱冻结 · 真实 Room/跨进程驱动准备

## 一、任务编号/版本

- **任务编号/版本**：`J3-n1-sandbox-build-v1`
- **性质**：从 J1-v3 基线带入 N1 八文件制作新 E 盘联合候选，冻结独立沙箱 APK，并准备真实 Room/跨进程恢复的单类仪器驱动。**不合并 D 盘共享 WIP，不执行设备测试。**
- **上游依据**：[N1-v6 审查/合流清单 Codex 核查](../../reviews/2026-10/2026-10-02-next-n1v6-review-inventory-codex-review.md)（§里程碑复盘：`J3-n1-sandbox-build-v1` 从 J1-v3 新 E 副本带入 N1 完整 8 路径，D 树 WIP 只登记不回灌；新 `.qahostn1` 独立 UID；**新后缀 runner 透传生产 Application，须如实记录启动副作用边界**）、[合流准备清单](2026-10-02-next-n1-v6-integration-inventory-v1.md)
- **状态**：**构建/冻结与驱动准备通过**，交 Codex。**不代表联合验收或 MuMu 通过。**

## 二、输入指纹

| 项 | 值 |
|---|---|
| 恢复仓库 | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` |
| 分支 / HEAD | `codex/recover-worktree-20260920` / **`e39a78da9c62f02614207cffb36969a32492eaea`**（开工实读一致） |
| 只读联合基线 | `E:\creation-reading-assistant\next-round-joint-j1-v3\android`（875 文件） |
| 只读 N1 输入 | `E:\creation-reading-assistant\next-round-n1-repair-v6\android` |
| 四方指纹来源 | `E:\creation-reading-assistant\n1v6-review-inventory-codex-20261002\verification.json` |
| 新候选 | `E:\creation-reading-assistant\next-round-j3-n1-sandbox-build-v1\android` |
| D 盘脏工作树 | **未**作为构建输入；仅登记指纹（§3.3），**未回灌** |

## 三、所有权与改动

**唯一仓库写入**：本文件。`current.md`、项目页、TODO 由 Codex 更新。

### 3.1 候选树差异（实算，`verify_candidate.py`）

| 项 | 值 |
|---|---|
| J1-v3 / 候选文件数 | **875 / 878** |
| 仅 J1-v3 有 | **0** |
| 仅候选有 | **3**（StateStore、StateStoreTest、**新增驱动类**） |
| 内容不同 | **7**（6 个 N1 继承文件 + `app/build.gradle.kts`） |
| 相同 | 868 |
| 触碰路径合计 | **10** = 8（清单）+ 1（后缀 hunk）+ 1（新增仪器类） |

**八文件继承 SHA 全部等于清单 `v6_sha`**（`inherited_8_all_ok = true`）：

| 文件 | SHA-256（= v6） |
|---|---|
| `feature/sync/JsonBridge.kt` | `f8e455a452e0182d8bad50349b26e58dbdd0dd7a942e2ea09d3d8223570ece37` |
| `feature/sync/LocalZipBackup.kt` | `f141941a628de19660d71875bb34faa5b4833f05f6db7d0025aba774165b298e` |
| `feature/sync/LocalZipRestoreStateStore.kt` | `167b881a8d5c65fb416af4820fedbf118820762c523d8c298c36e17ac7dfcca4` |
| `ui/screen/profile/ProfileRoute.kt` | `89a793ba258cd6cd4269adaf523cbfbaef027f7ff8a2844307ae09dc21482519` |
| `ui/viewmodel/ProfileViewModel.kt` | `124517d2800853c80ea24c9af24bf945f7f988f003f8f3168b0cae1e6bb35231` |
| `test/…/JsonBridgeTest.kt` | `bcc27a4d0635071f96b05c341119293a8e076248469168782bd8795bb90c412b` |
| `test/…/LocalZipBackupTest.kt` | `4d6825b1a97da6e97d4575b4742c169681b6b6eccac7a6dddbbf35771701a707` |
| `test/…/LocalZipRestoreStateStoreTest.kt` | `9eba0a91ac8c6ca57898c55b57d2db97c617cfcb200790f222703fc3f10383ca` |

### 3.2 后缀单 hunk

`app/build.gradle.kts` 与 J1-v3 的差异**恰为 1 行**（第 119 行）：

```
-            applicationIdSuffix = ".qahost"
+            applicationIdSuffix = ".qahostn1"
```

候选 `d4366e3f877b4e9d08116dc4a2e43ff30994ff9928bbcac7438b0dd9cc6446f1`；基线 `ae078ff087627125b3e373fa0c9c4936a14dbca8f237441d23f9904c2dac5bb4`。行尾保持 LF（`CRLF=0`）。

**未复制 G1 的 `.qahostlibrary` 后缀，也未复制 `LibraryLifecycleAcceptanceTest.kt`**（已 grep 确认 `qahostlibrary` 计数为 0）。

### 3.3 D 盘共享 WIP：只登记，不回灌

| 文件 | D-WIP SHA-256 | 候选采用 |
|---|---|---|
| `ui/screen/profile/ProfileRoute.kt` | `a1cad5557edb853e5a0e72fb4e476afeed92dc239146c0d3a32a3e49afe482c6` | **v6 形态** `89a793ba…` |
| `ui/viewmodel/ProfileViewModel.kt` | `f538cbb385c8e369cb819f324d8ba82cb3878e4b2e27340370eb675143368f47` | **v6 形态** `124517d2…` |

两处指纹与清单 `WIP_sha` 一致；候选**未**包含 D-WIP 的设置搜索/同步偏好依赖。D-WIP 两文件的归属与先后仍待 Codex/用户定性。

### 3.4 新增仪器类

`app/src/androidTest/java/com/creationreadingassistant/feature/sync/LocalZipRestoreAcceptanceTest.kt`
SHA-256 `3bc297688bf4e98a81dfa67c4180eeb9afc9fe4e906a2e9d05edd5272baaaa4c`（855 行，21 个 `@Test` 方法）。
**已编入测试 APK**：`classes12.dex` 含 `LocalZipRestoreAcceptanceTest`（类名字符串出现 213 次）。

**未改**产品算法、runner、依赖、迁移、签名策略或其他配置；未使用反射绕过接缝。

## 四、环境

| 项 | 值 |
|---|---|
| 平台 | Windows 11；Git Bash（MSYS）；Python 3.13；JDK 17；Gradle wrapper 8.9 |
| 自有缓存 | `E:\…\next-round-j3-n1-sandbox-build-v1\_cache\gradle-home`（2.9 GB，由 `next-round-joint-j1-v2` 缓存整体复制） |
| 构建输出 | 全部在候选自有 `android/app/build`；**未与其他窗口共用可写输出** |
| 设备 | **零 ADB、零安装、零 MuMu 启动** |
| 执行者/模型 | 本会话由 Claude Code（GLM 5.3 会话）执行；未使用子代理 |

**依赖获取过程（如实登记）**：首次尝试复用 `next-round-joint-20261001` 缓存时，`io.mockk:mockk-android:1.13.12` 不在其中（该缓存只服务过产品侧构建，未构建含 mockk-android 的 androidTest）。随后从 `next-round-joint-j1-v2` 缓存做**文件级合并**仍失败——Gradle 的 `caches/modules-2/metadata-*` 二进制索引无法用文件复制合并。最终**整体重建**自有缓存（来源 j1-v2，含 mockk-android），依赖解析通过。

## 五、执行与结果

### 5.1 构建

```
./gradlew.bat :app:assembleQahost :app:assembleQahostAndroidTest \
  -PcraAndroidTestBuildType=qahost --offline --console=plain --max-workers=1
```

| 轮次 | 结果 | 退出码 |
|---|---|---|
| 1 | 失败：`No cached version of io.mockk:mockk-android:1.13.12 available for offline mode` | 1 |
| 2 | 失败：同上（文件级合并缓存无法修复二进制索引） | 1 |
| 3 | 失败：`compileQahostAndroidTestKotlin` 2 处实参类型错误（`writeZip` 签名） | 1 |
| 4 | **`BUILD SUCCESSFUL in 47s`**，74 tasks（17 executed / 7 from cache / 50 up-to-date） | **0** |

> **证据完整性说明**：第 1–3 轮的原始日志被同一文件名覆盖，已不可回读；此处如实记录当时观察到的错误文本。最终日志 `logs/build-qahostn1.log`（`e65f3090d6ae9736ba057fa8a925ac8933c8f415acefff2110da43296e090ee4`）为第 4 轮原始日志。

**任务实际执行**：`> Task :app:compileQahostAndroidTestKotlin`、`> Task :app:assembleQahostAndroidTest` 均实际执行（无 `FROM-CACHE`/`UP-TO-DATE` 后缀）；`> Task :app:assembleQahost UP-TO-DATE` —— 宿主 APK 在第 1 轮已产出，其后主源码与 `app/build.gradle.kts` 未再变动。**已用 `find … -newer <宿主APK>` 复核：0 个主源码/构建脚本比宿主 APK 更新**，故 UP-TO-DATE 成立。

### 5.2 冻结产物

| 产物 | 路径 | SHA-256 | 大小 |
|---|---|---|---|
| 宿主 | `freeze/apk/app-qahostn1.apk` | `65003c06f4759fcc05f70bd2d52323f0f438ac1505b8162299a1385ac5dcea5b` | 51,338,932 B |
| 仪器 | `freeze/apk/app-qahostn1-androidTest.apk` | `5f2a1edc10ecbe93876ac31c53b3c45713daef0796da4567db4ce5e8275e3a39` | 9,133,863 B |

**核验（aapt / apksigner）**：

| 项 | 宿主 | 仪器 |
|---|---|---|
| package | `com.creationreadingassistant.qahostn1` | — |
| versionCode / versionName | `3` / `0.5.1` | — |
| minSdk / targetSdk | 24 / 35 | — |
| **`application android:name`** | **`com.creationreadingassistant.App`（生产 Application）** | — |
| instrumentation name | — | `com.creationreadingassistant.testing.CraIsolatedTestRunner` |
| **targetPackage** | — | **`com.creationreadingassistant.qahostn1`** |
| 签名证书 SHA-256 | `05249d786e3d3d117bb5bd7b1f83512a0c9b8f1ee4e502112c4b2d9ccbd7b9a1`（Android Debug） | 同上 |
| apksigner verify | exit 0 | exit 0 |

### 5.3 ⚠ 启动副作用边界（必须如实理解）

`CraIsolatedTestRunner.newApplication` 的分支条件是**精确字符串相等**：

```kotlin
val isolate = targetPackage == QA_HOST_PACKAGE   // QA_HOST_PACKAGE = "com.creationreadingassistant.qahost"
```

新沙箱包名是 `com.creationreadingassistant.qahostn1`，**不等于** `…qahost` ⇒ `isolate = false` ⇒ 走 **pass-through 分支** `super.newApplication(cl, className, context)`，`className` 即清单声明的 **`com.creationreadingassistant.App`**。

**因此：**
- 新沙箱进程内会运行**生产 Application**：`App.attachBaseContext`/`onCreate`、**Hilt 图谱**、三条 `cra-startup` 后台接线（EPUB 尺寸修复 / 阅读目标调度 / 搜索索引调度）、应用级夜间模式处理、`androidx.startup` 的 `androidx.work.workdb` 创建等**全部照常执行**。
- 对比：旧 `.qahost` 沙箱走 isolate 分支，注入裸 `CraIsolatedTestApplication`，**不执行**上述任何一项。
- **新 UID 隔离 ≠ 无启动副作用**：独立 applicationId 只保证独立 UID 与独立私有数据目录（副作用落在新沙箱自己的目录内，不碰正式包），但**副作用本身真实存在**。
- 运行时会写一行 `Log.i("CraIsolatedTestRunner", "newApplication target=… manifestApplication=… -> manifest Application (pass-through)")`，设备批应抓取该行**直接证明**走的是透传分支。
- **后续设备批必须**：安装前冷备新沙箱 UID 的数据目录（首次安装应为空）、记录系统设置与转发，运行后逐项对账并证明恢复。**不得**把「新 UID」当作「无副作用」而跳过对账。

### 5.4 驱动方法清单（21 项，全部 **NOT RUN**）

| # | 方法 | 性质 | 依赖 |
|---|---|---|---|
| 0 | `n1_00_cleanupBeforeSeed` | 准备（幂等清空沙箱+测试库） | — |
| 1 | `n1_01_seedScenario1_equal` | 夹具：本地 rev=2 与远端相等 → **equal** | — |
| 2 | `n1_02_armKillScenario1` | **产品调用**：真实 `importZip(A)` + 信号 + 有上限等待 | **执行者须在此 force-stop** |
| 3 | `n1_03_readOnlyAfterKillScenario1` | 新进程纯读（DB/正文/state/journal） | 上一步被终止 |
| 4 | `n1_04_retryBScenario1` | 产品调用 + 断言（第一次 B 被阻断） | 3 |
| 5 | `n1_05_retryBAgainScenario1` | 同上（第二次 B；v5 曾在此绕过） | 4 |
| 6 | `n1_06_reselectAScenario1` | 产品调用 + 断言（重选 A 收敛 + **真实导出 ZIP**） | 5 |
| 7–11 | `n1_10_*` … `n1_14_retryBScenario2` | 场景 2：A→**坏 ZIP**→B（空指纹不得解除保护） | 同 1–5 |
| 12–14 | `n1_20_*` … `n1_22_importBAgainScenario3` | 场景 3：**legacy 长度义务**→B→B（不得替成 B 的 SHA） | — |
| 15–16 | `n1_30_seedScenario4_accepted`、`n1_31_importBScenario4` | 场景 4：**accepted** + 冲突义务（v6 新增分支） | 15 后须 force-stop |
| 17–18 | `n1_40_seedScenario5_strictlyNewer`、`n1_41_importBScenario5` | 场景 5：**strictlyNewer** 本地正文不得被覆盖 | — |
| 19 | `n1_50_exportGateAndRealZip` | 导出：按状态分支断言「门禁阻止」或「真实产出 ZIP」 | — |
| 20 | `n1_99_cleanupSandbox` | 收尾：删沙箱+测试库并证明 | — |

**接缝说明**：`armKill…` 用既有 `LocalZipBackup.fileCopyOperation`（`internal var`，androidTest 可合法访问——J1-v3 的 `LocalZipBackupPersistentRestoreTest` 已有先例），在「DB 计划已持久化、正文尚未完成」处写信号后**有上限等待**（120 s），**不抛 IOException 冒充真实终止**、不改生产代码、不延长产品窗口。等待超时则继续完成复制，证据记 `killObserved=false`。

**信号内容**：阶段、`Process.myPid()`、时间戳、rel/dst/dstSha、state 文件路径与阶段/指纹/pending/unresolved、journal 文件与原始内容。同时写内部沙箱与 `getExternalFilesDir(null)/n1-sandbox/`（shell 可读）。

**夹具与隔离**：沙箱根 `<filesDir>/n1-sandbox/`（`n1-` 前缀，跨仪器调用稳定）；测试库 `n1_sandbox_restore_test.db`（唯一名）；`SandboxContext` 重定向 `filesDir`/`cacheDir` 并把 SharedPreferences 换成内存实现、`deleteSharedPreferences` 置 no-op（防止 `SecurePrefs` 降级重试删掉沙箱包真实的 `cra_device_identity`）。

## 六、原始证据

工件根：`E:\creation-reading-assistant\next-round-j3-n1-sandbox-build-v1\`

| 路径 | 说明 | SHA-256 |
|---|---|---|
| `logs/build-qahostn1.log` | 最终构建原始日志（EXIT 0） | `e65f3090d6ae9736ba057fa8a925ac8933c8f415acefff2110da43296e090ee4` |
| `freeze/freeze-manifest.json` | 冻结清单（双 APK、八文件、后缀 hunk、runner 边界） | `6664565dd56bb65b8e6972d0e1d5a4100da2d8e70dedf90cbc82a91ef0d42c24` |
| `freeze/apk/app-qahostn1.apk` | 宿主 | `65003c06f4759fcc05f70bd2d52323f0f438ac1505b8162299a1385ac5dcea5b` |
| `freeze/apk/app-qahostn1-androidTest.apk` | 仪器 | `5f2a1edc10ecbe93876ac31c53b3c45713daef0796da4567db4ce5e8275e3a39` |
| `verify_candidate.py` / `.json` | 候选差异核对脚本与结果 | `bf68113475921c13deab782d8bbc9fb3e7a133586f273a1fc7d24f06f103d99a` / `b436ca891f67449d75a94cea3dc130b1f0d98a5fe8aa7b694819b8f27db3d4ea` |
| `android/…/LocalZipRestoreAcceptanceTest.kt` | 新增驱动类 | `3bc297688bf4e98a81dfa67c4180eeb9afc9fe4e906a2e9d05edd5272baaaa4c` |

## 七、未覆盖项

| # | 项 | 状态 |
|---|---|---|
| 1 | **全部 21 个驱动方法** | **NOT RUN**（本任务只准备并编译） |
| 2 | 真实进程终止 / 跨进程恢复 / 真实 Room 落盘行为 | NOT RUN |
| 3 | 设备/UI/SAF 用户可见重试路径 | NOT RUN |
| 4 | N1 联合门禁、永久候选合流 | 未做 |
| 5 | D-WIP 两 Profile 文件的归属定性 | 未做（只登记指纹） |
| 6 | `N1-test-hardening-v7` 的 JVM 测试补强 | 未做（并行窗口负责；本候选生产 SHA 固定为 v6） |
| 7 | 桌面线 / 正式签名 / 升级 / F | 未涉及 |

**边界声明**：本次通过**只能称「构建/冻结与驱动准备通过」**，**不能**称联合验收或 MuMu 通过。

## 八、恢复与副作用

- **仓库**：HEAD `e39a78da…` 未变；未暂存/commit/push/reset/checkout；未搬目录；未清理旧资产。**唯一仓库写入 = 本文件**。
- **D 盘共享 WIP**：只读登记指纹，**未回灌、未覆盖、未合并**。
- **旧候选**：J1-v3 与 N1-v6 均只读，未修改。
- **设备**：零 ADB、零安装、零 MuMu 启动、零正式书库访问。
- **其他窗口**：未覆盖他人修改；构建与缓存全部在自有 E 目录。
- **过程事件**：一次缓存整体重建（`rm -rf` 时 2 个 wrapper jar 因 Gradle 守护进程占用未删除，随后 robocopy 覆盖为同版本内容，不影响结果）；一次构建日志被同名覆盖（§5.1 已如实登记）。

## 九、交付状态与下一步

### 9.1 交付状态

**构建/冻结与驱动准备通过，交 Codex。** 双 APK 已冻结并核验（包名/目标包/runner/Application/签名/SHA/大小）；候选差异恰为 8 清单路径 + 1 后缀 hunk + 1 新增仪器类；D-WIP 未回灌。

**返回摘要**：
- 报告：`docs/coordination/dispatches/2026-10/2026-10-02-next-j3-n1-sandbox-build-v1.md`（本文件）
- 双 APK SHA：宿主 `65003c06f4759fcc05f70bd2d52323f0f438ac1505b8162299a1385ac5dcea5b`、仪器 `5f2a1edc10ecbe93876ac31c53b3c45713daef0796da4567db4ce5e8275e3a39`
- 驱动方法：21 项（§5.4），**全部 NOT RUN**
- 未运行边界：§七

### 9.2 后续设备运行协议（供唯一 MuMu 所有者执行；本任务不执行）

**A. 识别本轮设备（不得沿用旧 serial）**
1. `adb devices` 列出当前连接；对每个候选 serial 执行 `adb -s <serial> shell getprop ro.product.model` 与 `getprop ro.build.version.sdk`，与当轮交接记录核对。
2. **不沿用任何历史 serial**；以本轮实读为准，并在报告中写明 serial + model + sdk。

**B. 安装前基线与冷备（新沙箱 UID 首次安装应为空）**
1. 确认 `pm list packages | grep com.creationreadingassistant.qahostn1` **原先不存在**；同时确认正式包 `com.creationreadingassistant` **未安装**且不安装。
2. 记录：`wm size`、`wm density`、`settings get system accelerometer_rotation`、`user_rotation`、`cmd uimode night`、`settings get global stay_on_while_plugged_in`、`screen_off_timeout`。
3. 记录 `settings get global http_proxy` / `adb reverse --list` / `adb forward --list`（若测试涉及网络转发则一并备份）。
4. 若沙箱包已存在（重复批次），冷备其数据目录 `su 0 tar -czf … /data/data/com.creationreadingassistant.qahostn1`，并逐文件 SHA 留档。

**C. 安装与冻结核对**
1. `adb -s <serial> install -r freeze/apk/app-qahostn1.apk`、`adb -s <serial> install -r -t freeze/apk/app-qahostn1-androidTest.apk`。
2. 回读设备上两个 `base.apk` 的 SHA-256，必须等于 §5.2 的两个值。

**D. 逐阶段运行（每步单独 `am instrument`）**

```
adb -s <serial> shell am instrument -w -r \
  -e class com.creationreadingassistant.feature.sync.LocalZipRestoreAcceptanceTest#<方法名> \
  com.creationreadingassistant.qahostn1.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
```

顺序按 §5.4。**终止步骤**（`n1_02_armKillScenario1`、`n1_11_armKillScenario2`、`n1_31_importBScenario4` 内的 arm 阶段）：
1. 另开一个终端**轮询**信号（无需 root，外部目录 shell 可读）：
   `adb -s <serial> shell ls /sdcard/Android/data/com.creationreadingassistant.qahostn1/files/n1-sandbox/n1-kill-signal.json`
2. 出现后立即读取内容（`adb -s <serial> shell cat <同上路径>`）——须含 `phase=after_db_plan_before_content_copy`、`pid`、`stateStage`、`journalRaw`。
3. **立刻** `adb -s <serial> shell am force-stop com.creationreadingassistant.qahostn1`（须在 120 s 窗口内）。
4. 确认进程确实消失：`adb -s <serial> shell pidof com.creationreadingassistant.qahostn1` 返回空；并核对信号里的 `pid` 与终止前的 PID 一致。
5. 抓取 logcat 中 `CraIsolatedTestRunner` 的 `newApplication … -> manifest Application (pass-through)` 行，**证明走的是透传分支**。
6. 若未能在窗口内终止：该次**不得**记为真实中断，按 `killObserved=false` 记录并重跑该场景。

**E. 回读证据**
- 仪器 XML：`adb -s <serial> shell ls /sdcard/Android/data/com.creationreadingassistant.qahostn1/files/n1-sandbox/evidence/`，逐个 `cat` 保存。
- 内部沙箱（root）：`su 0 cat /data/data/com.creationreadingassistant.qahostn1/files/n1-sandbox/n1-kill-signal.json` 与 `…/evidence/*.json`。
- 逐项核对：正文实际字节 SHA、原义务身份、状态阶段/是否完成、导出被门禁阻止的**失败信息**或真实 ZIP 的条目集合与 SHA。

**F. 清理与恢复对账**
1. 运行 `n1_99_cleanupSandbox`（删沙箱 + 测试库，并断言已删除）。
2. 卸载沙箱包：`adb -s <serial> uninstall com.creationreadingassistant.qahostn1.test`、`… uninstall com.creationreadingassistant.qahostn1`。
3. 复核 `/data/data/com.creationreadingassistant.qahostn1` 与 `/sdcard/Android/data/com.creationreadingassistant.qahostn1` **均不存在**；`pm list packages` 中无 `qahostn1`；正式包仍未安装。
4. 逐项恢复并复核 B 步记录的设备参数；`adb reverse --remove-all` / `--remove` 恢复转发；清理 `/data/local/tmp` 与本轮中转文件。
5. **对账「启动副作用」**：新沙箱 UID 已被卸载，其私有目录应随卸载消失；仍需在报告中说明生产 Application 与 `androidx.startup` 在本轮**确实运行过**（§5.3），并给出 `pm list packages` / 目录不存在的证据。

### 9.3 下一步（Codex）

1. 核本报告与冻结清单；确认 `.qahostn1` 的**透传分支**副作用边界已被接受为设备批前提。
2. 待 `N1-test-hardening-v7` 交付核过后，更新联合候选的唯一 JVM 测试并跑定向门禁（**不得**把本次先冻结的宿主当完整门禁通过）。
3. 由唯一 MuMu 所有者按 §9.2 执行；完成后交 Codex 独立核查，再决定是否合流。

**不自动修复、不合流、不暂存/提交/推送；报告与冻结落盘后停止。**
