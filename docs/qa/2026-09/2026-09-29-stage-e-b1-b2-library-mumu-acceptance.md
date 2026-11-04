# 阶段 E — B1 + B2 + 库浏览器联合候选 MuMu 设备限定验收报告（2026-09-29）

> **验收执行人**：MuMu / ADB 本轮独占所有者（DeepSeek harness / WorkBuddy DeepSeek）。  
> **基线仓库（只读）**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`，HEAD `513891d6a66a9b78e072ba759d34ed580d377ecf`（暂存区空）。  
> **设备性质说明**：**本次验证运行于 MuMu 模拟器（`emulator-5556`），绝非物理真机实测**；未操作任何真实手机。  
> **测试目标限定**：仅操作隔离包 `com.creationreadingassistant.qahost` 与测试包 `com.creationreadingassistant.qahost.test`；**正式包 `com.creationreadingassistant` 未安装、未触碰，未触碰正式书库**。  
> **候选输入**：复制自已冻结的 `E:\creation-reading-assistant\stage-e-b1-b2-library-combined-20260929\` 候选，置于独占设备验收目录 `E:\creation-reading-assistant\stage-e-b1-b2-library-device-20260929\`。  
> **操作纪律**：共享恢复仓库内**唯一写入文件 = 本文件**；未修改共享源码、测试、配置或其他窗口报告；未执行 stage、commit、push、reset、checkout 或分支迁移；构建产物、依赖缓存、日志、截图与备份全部存放在 E 盘。

---

## 一、结论先行与边界判定

1. **冷备与事后对账 100% 严格一致（零字节漂移）**：
   - 测试前完成原装 APK 两枚提取及持久目录（`databases`、`files`、`shared_prefs`）53 个文件逐项 SHA-256 固化。
   - 验收结束后，经完全卸载候选、重新安装原装 APK、回写冷备 tar 包，**53/53 个持久文件前后 SHA-256 逐行完全匹配（`Compare-Object` 差异为 0）**；主数据库 `creation_reading_assistant_native` 保持为 `7bd076c60763d9d9a65fa3df990bcd67f2911686cdbcbf4310d37f0588499a28`，`-wal` 为 0 字节；两枚重装原装 APK 与备份哈希完全一致；设备密度保持 280；`/data/local/tmp` 临时文件完全清理。
2. **相关设备用例实跑全部通过**：
   - G0 的 3 项 Room v15 迁移用例：**3/3 PASS**（`stack=` 0，`OK (3 tests)`）；
   - U 的 5 项父表级联安全用例：**5/5 PASS**（`stack=` 0，`OK (5 tests)`）；
   - B1 / 合流 `BookRepository` 真实 SQLite 持久化测试（`BookDeletionPersistenceTest#scopedDeleteAndRestorePreserveOnlyThisOperationInRealRoom`）：**1/1 PASS**（`stack=` 0，`OK (1 test)`），在真实 Android SQLite 驱动下验证了合流后 `BookRepository` 的快照软删除、隔离性及撤销自愈。
   - 书架 Compose 顶栏替换用例（`ShelfScreenComposeTest#selectionModeReplacesTopBarContentWithoutSecondTopBar`、`normalStateShowsOneTopBar`）：**2/2 PASS**，验证了选择模式下顶栏内容替换且不创建重复顶栏的 UI 行为。
3. **冷启动与进程存活健康**：
   - 联合候选 qahost 主包冷启动成功（PID: 19888），`topResumedActivity` 为 `MainActivity`，前台保持 5 秒无 ANR，logcat 进程内 FATAL / RuntimeException 计数为 **0**，冷启动截图成功落盘。
4. **严格的未验边界（如实登记为 NOT RUN，绝不用 167/167 JVM 代替设备验收）**：
   - **B1 搜索索引**：S03-1 确定性并发屏障测试与复杂检索打分为纯 JVM 单测，在设备上无 androidTest 镜像；WorkManager 后台唤醒与多进程竞争在设备上为 **NOT RUN**。
   - **B2 缓存清理**：`BookResidueSafeCleanupTest` 为纯 JVM 单测；`StorageSubPage.kt`（清空离线书籍用户界面接线）未纳入联合候选，因此用户在 UI 界面点击“清空离线书籍”的完整用户可达路径**未打通**，如实登记为 **NOT RUN**。
   - **库浏览器**：`LibraryBrowserSelectionTest` 为纯 JVM 单测；排序项 `ADDED_DESC` 仍处于 HOLD；SAF 真实外部授权目录的端到端浏览与选择联动登记为 **NOT RUN**。

---

## 二、测试前设备基线与冷备存证

### 2.1 模拟器与环境状态
- **设备 Serial**：`emulator-5556`（`adb devices` 核对唯一在线设备）。
- **设备类型**：MuMu 模拟器（Android API 32，x86_64）。
- **物理屏幕密度**：`wm density` 输出 `Physical density: 280`。
- **已安装包**：`com.creationreadingassistant.qahost`、`com.creationreadingassistant.qahost.test`。正式包 `com.creationreadingassistant` **未安装**。

### 2.2 原装 APK 提取与哈希
备份存放于：`E:\creation-reading-assistant\stage-e-b1-b2-library-device-20260929\backup\`

| 文件名 | 大小 (Bytes) | SHA-256 校验和 | 来源路径 |
|---|---|---|---|
| `original-app-qahost.apk` | 52,601,998 | `E82904CC9C1315466ED5C521693F93F541560FEEF038BF074CE7F7CF8324EF8A` | `/data/app/.../com.creationreadingassistant.qahost.../base.apk` |
| `original-app-qahost-test.apk` | 1,664,403 | `FDBD019011088505FA37D71565259A0D76F6927407AF69148379E2EF7C27038D` | `/data/app/.../com.creationreadingassistant.qahost.test.../base.apk` |

> 该两枚原装 APK 哈希与前序窗口报告（`2026-09-29-stage-e-g0-u-g2-combined-mumu.md` 第 16 行）完全一致。

### 2.3 持久状态清单与整包冷备
- **持久文件清单**：通过 `run-as com.creationreadingassistant.qahost` 扫描 `./databases ./files ./shared_prefs` 共 53 个持久文件，保存于 `backup/before-sha256-manifest.txt`。
  - 主数据库 `creation_reading_assistant_native`：`7bd076c60763d9d9a65fa3df990bcd67f2911686cdbcbf4310d37f0588499a28`
  - `-shm`：`fd4c9fda9cd3f9ae7c962b0ddf37232294d55580e1aa165aa06129b8549389eb`
  - `-wal`：`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`（0 字节）
  - 书籍与配置共 50 个文件在列。
- **整包冷备 tar**：
  - 文件：`backup/qahost_backup_before.tar`
  - 大小：**1,181,988,864 字节**
  - SHA-256：`CAE16E820BCAFB1E269FAD7682F44C832581BFB986E5CCE6FA81084B388026F0`

---

## 三、隔离副本构建与产物度量

在独占目录 `E:\creation-reading-assistant\stage-e-b1-b2-library-device-20260929\` 中，配置专用 Gradle 缓存目录 `gradle-cache` 并完成构建：

- **执行命令**：
  ```powershell
  $env:GRADLE_USER_HOME = "E:\creation-reading-assistant\stage-e-b1-b2-library-device-20260929\gradle-cache"
  .\gradlew.bat :app:assembleQahostAndroidTest :app:assembleQahost -PcraAndroidTestBuildType=qahost --offline --console=plain
  ```
- **构建结果**：`BUILD SUCCESSFUL in 8m 25s`（72 actionable tasks: 68 executed, 4 from cache，退出码 0）。
- **日志归档**：`logs/build-qahost.log`。
- **生成 APK 度量**：

| 产物名称 | 大小 (Bytes) | SHA-256 校验和 | aapt badging / manifest 关键属性 |
|---|---|---|---|
| `app-qahost.apk` | 51,141,976 | `2B25D8F14D9890E7546F7A86F14AE52C87E43F7FB02A7711825EA636F179EA13` | `packageName='com.creationreadingassistant.qahost'`<br>`versionCode='3' versionName='0.5.1'` |
| `app-qahost-androidTest.apk` | 1,422,641 | `C475D354C8CF8785662486A9BA31A24766E01A68D8928B2C9D342DF93CF06699` | `targetPackage='com.creationreadingassistant.qahost'`<br>`runner='com.creationreadingassistant.testing.CraIsolatedTestRunner'` |

- **安装结果**：
  ```
  adb -s emulator-5556 install -r -t app-qahost.apk -> Success
  adb -s emulator-5556 install -r -t app-qahost-androidTest.apk -> Success
  ```

---

## 四、设备验证执行记录（逐项明细）

### 4.1 测试组 1：G0 数据库迁移基础验证（3 项）
- **命令**：
  ```powershell
  adb -s emulator-5556 shell am instrument -w -r `
    -e class com.creationreadingassistant.data.local.AppDatabaseMigrationTest#migrate_14_to_15_drops_redundant_term_index,com.creationreadingassistant.data.local.AppDatabaseMigrationTest#openingDatabaseRebuildsPartialIndexesAndDoesNotResurrectTermIndex,com.creationreadingassistant.data.local.AppDatabaseMigrationTest#migrate_13_to_15_full_chain_keeps_old_data_and_creates_source_refs `
    com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
  ```
- **耗时**：0.203s，`OK (3 tests)`，`stack=` 0。
- **日志归档**：`logs/instr-g0-3-migration.log`。
- **结果**：**PASS**。

### 4.2 测试组 2：U 父表级联安全性验证（5 项）
- **命令**：
  ```powershell
  adb -s emulator-5556 shell am instrument -w -r `
    -e class com.creationreadingassistant.data.local.ParentUpsertCascadeSafetyTest `
    com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
  ```
- **耗时**：0.176s，`OK (5 tests)`，`stack=` 0。
- **覆盖用例**：
  1. `upsertingInspirationPreservesVariantsAndNoteLink` -> PASS
  2. `upsertingBooksInBatchPreservesChildRows` -> PASS
  3. `upsertingBookPreservesAllChildRows` -> PASS
  4. `upsertingTaxonomyParentsPreservesJoinRows` -> PASS
  5. `softDeletePreservesChildRows` -> PASS
- **日志归档**：`logs/instr-u-5-cascade.log`。
- **结果**：**PASS**。

### 4.3 测试组 3：B1 / 合流 `BookRepository` 真实 SQLite 持久化验证（1 项）
- **测试类**：`com.creationreadingassistant.data.repository.BookDeletionPersistenceTest`
- **执行方法**：`scopedDeleteAndRestorePreserveOnlyThisOperationInRealRoom`
- **意义**：直接使用真实 SQLite 引擎验证了我们合流的 `BookRepository`（包含 B1 的 `applyDeletionLocked` 与 `restoreDeletions`）在事务中软删除书籍及其全部级联行、隔离未受影响书籍、以及根据快照精确恢复资料的能力。
- **命令**：
  ```powershell
  adb -s emulator-5556 shell am instrument -w -r `
    -e class com.creationreadingassistant.data.repository.BookDeletionPersistenceTest `
    com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
  ```
- **耗时**：0.103s，`OK (1 test)`，`stack=` 0。
- **日志归档**：`logs/instr-book-deletion-persistence.log`。
- **结果**：**PASS**。

### 4.4 测试组 4：书架选择模式顶栏替换 Compose 验收（2 项）
- **测试类**：`com.creationreadingassistant.ui.screen.shelf.ShelfScreenComposeTest`
- **执行方法**：`selectionModeReplacesTopBarContentWithoutSecondTopBar`、`normalStateShowsOneTopBar`
- **意义**：验证了在选择书籍模式下，书架顶栏内容切换为“选择书籍”，且不创建第二个重复顶栏的 UI 渲染行为。
- **命令**：
  ```powershell
  adb -s emulator-5556 shell am instrument -w -r `
    -e class com.creationreadingassistant.ui.screen.shelf.ShelfScreenComposeTest#normalStateShowsOneTopBar,com.creationreadingassistant.ui.screen.shelf.ShelfScreenComposeTest#selectionModeReplacesTopBarContentWithoutSecondTopBar `
    com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
  ```
- **耗时**：1.146s，`OK (2 tests)`，`stack=` 0。
- **日志归档**：`logs/instr-shelf-screen-selection.log`。
- **结果**：**PASS**。

### 4.5 冷启动与存活稳定性验收
- **启动命令**：`adb -s emulator-5556 shell am start -n com.creationreadingassistant.qahost/com.creationreadingassistant.MainActivity`
- **存活状态**：PID 19888，前台保持 5 秒，`dumpsys` 确认 `topResumedActivity=ActivityRecord{... com.creationreadingassistant.qahost/com.creationreadingassistant.MainActivity}`。
- **异常审计**：针对 PID 19888 的 `logcat` 进行异常筛选（`FATAL|AndroidRuntime|Exception`），结果输出为空（0 错误，零崩溃）。
- **截图留存**：`screenshots/cold_start.png`（242,751 字节）。
- **退出命令**：`adb -s emulator-5556 shell am force-stop com.creationreadingassistant.qahost`。
- **结果**：**PASS**。

---

## 五、恢复与事后对账凭证（核心自证）

测试完成后，按安全规范完整执行现场恢复序列：

1. **卸载清理**：
   ```powershell
   adb -s emulator-5556 shell am force-stop com.creationreadingassistant.qahost
   adb -s emulator-5556 shell pm uninstall com.creationreadingassistant.qahost.test
   adb -s emulator-5556 shell pm uninstall com.creationreadingassistant.qahost
   ```
2. **重装原装 APK**：
   ```powershell
   adb -s emulator-5556 install -r -t backup/original-app-qahost.apk -> Success
   adb -s emulator-5556 install -r -t backup/original-app-qahost-test.apk -> Success
   ```
3. **冷备数据解压回写**：
   ```powershell
   adb -s emulator-5556 shell "run-as com.creationreadingassistant.qahost tar -xf /data/local/tmp/qahost_backup.tar"
   adb -s emulator-5556 shell "rm -f /data/local/tmp/qahost_backup.tar"
   ```
4. **事后逐文件哈希重算与比对**：
   - 保持未启动应用状态（确保数据处于静止 checkpoint 态）。
   - 设备端重新运行 `find ./databases ./files ./shared_prefs -type f | sort | xargs sha256sum` 并输出至 `backup/after-sha256-manifest.txt`。
   - 执行比对命令：
     ```powershell
     Compare-Object (Get-Content backup/before-sha256-manifest.txt) (Get-Content backup/after-sha256-manifest.txt)
     ```
   - **比对结果**：`MATCH 100%`，输出为空（0 差异），**53/53 个文件逐行一致**！
     - 主数据库 `creation_reading_assistant_native`：`7bd076c60763d9d9a65fa3df990bcd67f2911686cdbcbf4310d37f0588499a28`（完全一致）
     - `-shm`：`fd4c9fda9cd3f9ae7c962b0ddf37232294d55580e1aa165aa06129b8549389eb`（完全一致）
     - `-wal`：`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`（0 字节，完全一致）
5. **已装 APK 哈希核对**：
   - 从设备 pull 重装后的 base.apk 进行复核：
     - 主 APK：`E82904CC9C1315466ED5C521693F93F541560FEEF038BF074CE7F7CF8324EF8A`（完全一致）
     - 测试 APK：`FDBD019011088505FA37D71565259A0D76F6927407AF69148379E2EF7C27038D`（完全一致）
6. **设备参数核对**：
   - `wm density`：`Physical density: 280`。
   - `pm list packages`：仅 `com.creationreadingassistant.qahost` 与 `.qahost.test`（正式包未安装）。
   - `/data/local/tmp`：无本轮临时文件残留。

---

## 六、未验边界与已知局限（严谨定义）

1. **非实体真机实测**：
   - 全程在 MuMu 模拟器 `emulator-5556` 上执行，虽验证了 x86_64 上的 ART 运行时、SQLite 与 Compose 行为，但真实 ARM 芯片及 MIUI / HyperOS 定制系统的后台冻结/权限弹窗未在此轮覆盖。
2. **B1 / B2 / 库浏览器的未验设备项（NOT RUN）**：
   - S03 搜索索引并发与孤儿回收的大规模压力测试未在设备上建立长周期 WorkManager 观测（本用例由 19 条 JVM 并发测试闭环）；
   - B2 的 `StorageSubPage.kt` 用户界面未纳入候选，用户从应用 UI 触发“清空所有离线书籍”的可达路径**未打通**；
   - 库浏览器搜索过滤与多选状态目前仅有 ViewModel 层 29 条 JVM 测试及书架顶栏 Compose 证据，无 SAF 真实授权系统目录下的深度目录树自动化浏览用例。
   - **以上项如实声明为设备 NOT RUN，绝不用 JVM 167 项冒充设备通过**。

---

## 七、共享仓库状态审查

- 当前工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`。
- HEAD：`513891d6a66a9b78e072ba759d34ed580d377ecf`。
- 暂存区：`git diff --cached` 零输出（完全干净）。
- 仓库内唯一变更：创建本报告文件 [`docs/qa/2026-09/2026-09-29-stage-e-b1-b2-library-mumu-acceptance.md`](file:///D:/develop/Code/Codex/creation-reading-assistant-recovery-20260919/docs/qa/2026-09/2026-09-29-stage-e-b1-b2-library-mumu-acceptance.md)，未触碰任何源码与配置。
