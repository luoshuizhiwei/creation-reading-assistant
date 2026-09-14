# Android 第三轮 R3 独立最终验收 — 隔离真机验收报告（2026-09-14）

> 日期：2026-09-14  
> 验收角色：WorkBuddy 独立最终验收，只验证、记录、判定；除本报告外未修改产品源码、Gradle 配置、脚本、迁移、测试或桌面端文件。  
> 基线：`main` / `fd7f908e19c2ee2269103333804bdd5927201fd2`。  
> 受测 commit（`git log` 逐项确认 2026-09-14）：`30cf235` A（v12→v13）、`e3fafad` B（v13→v14）、`5cfb84c` C（安装脚本契约）、`99a8c03` D（Reader/词典/选区设置）、`066e50b` E（Home/Profile/Inspiration 消费）、`eebd59d` F（Shelf UI/动态视图/路由收口）、`fd7f908` test（androidTest context）。  
> 受测隔离包：`com.creationreadingassistant.r3gate`（仅在隔离 worktree `D:\develop\Code\Codex\cra-g0-gate` 的 `applicationIdSuffix=".r3gate"` 存在）。  
> 受测 APK：`D:\develop\Code\Codex\cra-g0-gate\android\app\build\outputs\apk\debug\app-debug.apk`（51,092,012 字节；时间 15:52 — 晚于 4 个修复文件最后修改时间 15:49:52，UP-TO-DATE 合法），`applicationId=com.creationreadingassistant.r3gate`、`versionCode=2 / versionName=0.5.0`。  
> 受测 androidTest APK：`D:\develop\Code\Codex\cra-g0-gate\android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk`（1,398,885 字节）。  
> APK SHA-256（APK 自身 `output-metadata.json` 与 `aapt dump badging` 已核验 `applicationId` 后缀；本次未单独产出 SHA-256 文件）。

---

## 0. 结论速览（四类结论，互不替代）

| 结论类别 | 判定 |
|---|---|
| 候选源码一致性 | ✅ 4 个目标修复文件在主工作区与隔离 worktree 之间**字节一致**（md5 一致）。隔离 worktree 仅多 `applicationIdSuffix=".r3gate"` 一项 Gradle 改动。 |
| 冻结候选开发门禁 | ✅ 全部 PASS，详见 §3：JVM 259 suites / 2,283 tests / 0F 0E 0S；Lint 0 errors（5 warnings 单列）；assembleDebug / assembleDebugAndroidTest exit 0；隔离 worktree `git diff --check` exit 0。 |
| **自动安装脚本 PASS** | ✅ 三次脚本安装（main + androidTest + 重新装 main）均 `RESULT: CONFIRM_LOOP` + shell 观察到 `SCRIPT_EXITCODE=0`，**未使用 direct ADB fallback**。**FALLBACK_DIRECT_ADB: NOT USED**。 |
| **Room v12→v13 / v13→v14 迁移执行** | ✅ **PASS**（本次在隔离包上 `connectedDebugAndroidTest` 实际执行：20 tests / 0 failures / 0 errors / 0 skipped，包含 `migrate_12_to_13_adds_reader_text_corrections`、`migrate_13_to_14_creates_library_source_refs`、`migrate_12_to_14_full_chain_preserves_data_and_creates_both_tables`，均由 `MigrationTestHelper.runMigrationsAndValidate` + Room schema 校验 + 真实表/索引断言驱动，非空壳）。**上一轮 BLOCKED 项已解除**。 |
| **真机验收** | ⚠️ **PASS WITH LIMITATIONS**（核心功能全 PASS；2 项未覆盖见 §6：动态视图"保存"被 WeType IME 拦截；单处替换的"两处相同文本"精准用例因 Compose Text 段级选中改为位置锚定的 ch1 单处语义）。 |

---

## 1. 受测环境

### 1.1 设备与隔离

- `adb devices`（验收起点实测）：
  ```
  List of devices attached
  c49ac6cf   device product:diting model:22081212C device:diting transport_id:6
  ```
- **真实设备**：Xiaomi 22081212C（Redmi K50 Ultra / POCO F4 GT，product `diting`），Android 15（SDK 35），MIUI/HyperOS V816，build `AQ3A.250226.002`，`ro.hardware=qcom`（模拟器为 `ranchu`/`goldfish`，本机为真机硬件）。分辨率 1220×2712 / 480dpi。
- **所有 adb 命令显式 `-s c49ac6cf`**；每个 adb 调用前已确认设备。
- `stay_on_while_plugged_in`：**验收前 3，验收后 3，本轮未修改**。
- 隔离包 `com.creationreadingassistant.r3gate` 安装时（firstInstallTime）= 17:21:46；生产包 `com.creationreadingassistant` **始终存在**且未被安装/卸载/数据清除。
- 隔离 fixture：`/sdcard/Download/cra-r3-final-gate/{novel-a, novel-b/sub, bulk, big}`，ASCII 中性命名（规避中文路径 adb push 挂起的环境坑）；结束已整目录 `rm -rf`。

### 1.2 隔离 worktree 与候选源码一致性

- 隔离 worktree：`D:\develop\Code\Codex\cra-g0-gate`；HEAD `fd7f908e19c2ee2269103333804bdd5927201fd2`（与主工作区 HEAD 字节一致）。
- 主工作区 git 状态存在 13 项 Android WIP（SearchIndexRepository / SearchOffsetResolver / SearchTokenAggregator / SearchViewModel / JsonBridge / DeviceInfoProvider / SecurePrefs / GlassDialogs / AppNavigation（仅 MenuBook AutoMirrored 单一 import）/ SearchScreen / MyReadingViewModel / SyncRepositoryTest / SearchOffsetResolverTest）——经逐文件比对，**这 13 个文件正是 `2026-09-14-android-integration-freeze-manifest.md` 第 9 节"无法安全归属"清单项**（Search / Sync / Security / MenuBook 图标 / GlassDialogs / MyReadingViewModel / RebuildSearchIndexDialog 等），**不属于 A–F 任何切片**，因此隔离 worktree 不带它们是合理的候选切分，未污染验收。

| 4 个目标文件 | 主 vs 隔离 hash 一致？ |
|---|---|
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/LibraryBrowserRoute.kt` | ✅ SAME |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/ShelfScreen.kt` | ✅ SAME |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/ShelfRoute.kt` | ✅ SAME |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderSheetHost.kt` | ✅ SAME |

隔离 worktree 唯一额外改动：`android/app/build.gradle.kts` 加 `debug { applicationIdSuffix = ".r3gate" }`，与主工作区未合并。

### 1.3 主工作区 git 状态（验收前 vs 验收后）

- 验收前：`git status --short` 列出 ~190+ 项他人 Android WIP（含 13 个归属未定的 Android 源文件、`docs/architecture/native-android-reader.md:6` 的 trailing whitespace 等）；无我方改动。
- 验收后：`git status --short` **未发生变化**（验收全程未执行任何 `git add/commit/push/reset/checkout/clean/stash/revert`，未对主工作区做任何修改）。
- 主工作区 `git diff --check` exit 2，**全部为他人 WIP 中的 trailing whitespace**（仅 `docs/architecture/native-android-reader.md:6` 一行），与本验收无关，开工前即存在。

---

## 2. 安装脚本验收（候选 C）

### 2.1 受控对照（环境缺陷 vs 脚本缺陷判定）

- 本会话 PowerShell 子进程的默认 `PATHEXT=.CPL`（不含 `.EXE`），导致 `adb.exe` 按名无法解析（`D:\develop\Android\Sdk\platform-tools` 已在 PATH 中）。
- 按既定方法在调用时显式恢复 Windows 默认 PATHEXT：`.COM;.EXE;.BAT;.CMD;.VBS;.VBE;.JS;.JSE;.WSF;.WSH;.MSC`。**修复后 `adb.exe` 即按名解析成功**，确证这是**会话环境缺陷**，不是脚本缺陷。

### 2.2 契约测试（无设备、纯脚本行为）

- 命令：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\install_with_confirm.contract.tests.ps1`
- 结果：脚本 exit code 0，输出包含 `断言合计: 37，失败: 0` + `ALL_CONTRACT_CHECKS_PASSED`。
- 覆盖：A0–A19（静态契约：可执行、无 2>$null、无 Read-Host/pause/settings put/pm grant/su、无 CP936 exec-out cat、UTF-8 pull 路径、Invoke-AdbNative 统一、-s $Serial、CONFIRM_LOOP 标记、FALLBACK_DIRECT_INSTALL 标记、回退非 PASS 文案、exit 2 分流、GUID 命名、Remove-Item 仅本脚本产物、UTF-8 BOM 要求）+ B1–B4（行为契约：成功/超时且 -NoDirectInstall/回退/解析失败可见）。

### 2.3 真机脚本安装（三次）

| # | 包 | -PackageName | -TestApk | 终端关键输出 | SCRIPT_EXITCODE | FALLBACK |
|---|---|---|---|---|---|---|
| 1 | app-debug.apk | com.creationreadingassistant.r3gate | — | `RESULT: CONFIRM_LOOP (installed through the MIUI installer UI)` + `Installed com.creationreadingassistant.r3gate successfully.` | 0 | NOT USED |
| 2 | app-debug-androidTest.apk | com.creationreadingassistant.r3gate.test | 是 | 同上 + `Installed com.creationreadingassistant.r3gate.test successfully.` | 0 | NOT USED |
| 3 | app-debug.apk（migration 后 isolated 主包被 `connectedDebugAndroidTest` 卸载，按任务要求重装） | com.creationreadingassistant.r3gate | — | 同 1 | 0 | NOT USED |

完整日志见 `cra-g0-gate/install-2026-09-14.log` / `install-androidTest-2026-09-14.log` / `install-reinstall-2026-09-14.log`。

**FALLBACK_DIRECT_ADB: NOT USED**。两次写明`-NoDirectInstall` 满足任务"严格区分脚本通过与 ADB fallback"要求。

### 2.4 与任务"判定口径"完全一致

- 仅 `SCRIPT_EXITCODE=0` + `RESULT: CONFIRM_LOOP` 写"脚本安装通过"。
- `RESULT: FALLBACK_DIRECT_INSTALL` / exit 2 一律未触发。
- `-NoDirectInstall` 失败证据（exit 1 + 失败原因）保持记录但不写出。

---

## 3. 开发门禁（隔离 worktree `android/`）

| # | 命令 | 退出码 | 主要统计 / 备注 |
|---|---|---|---|
| 1 | `./gradlew :app:testDebugUnitTest` | 0 | BUILD SUCCESSFUL in 3m 57s。**259 suites / 2,283 tests / 0 failures / 0 errors / 0 skipped**（本次独立复算，与上一轮引用值一致）。 |
| 2 | `./gradlew :app:lintDebug` | 0 | BUILD SUCCESSFUL in 4m 47s。**0 errors**；5 warnings，**逐条单列**：`HardwareIds:1`（用 `getString` 取设备标识符）、`ObsoleteLintCustomCheck:3`（`androidx.compose.runtime.lint.RuntimeIssueRegistry` 要求更新版本）、`UnusedResources:1`（`R.string.annotations_chapter_fallback` 未使用）。 |
| 3 | `./gradlew :app:assembleDebug` | 0 | BUILD SUCCESSFUL in 6s（UP-TO-DATE，因 .r3gate suffix 与 4 个修复文件已在 15:52 前应用）。APK `app/build/outputs/apk/debug/app-debug.apk` 已含 `.r3gate` 后缀（output-metadata.json 确认）。 |
| 4 | `./gradlew :app:assembleDebugAndroidTest` | 0 | BUILD SUCCESSFUL in 39s。androidTest APK `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`（1,398,885 字节）产出。 |

- `git diff --check`（隔离 worktree，验收过程后）exit 0；无 whitespace 错误。
- 日志：`cra-g0-gate/gate-2026-09-14.log`。

### 3.1 扫描取消/截断相关的确定性 JVM 用例（候选 B 关键覆盖）

| 测试类.用例 | 结果 |
|---|---|
| `SmartBookRecognizerTest.coroutine cancellation is propagated instead of reported as completion` | ✅ PASS |
| `SafBookSourceScannerTest.cancelling a scan stops before the remaining provider rows are traversed` | ✅ PASS |
| `SafBookSourceScannerTest.directory-only tree is truncated by the visit safety limit` | ✅ PASS |
| `SafBookSourceScannerTest.candidate cap truncates before a later supported file is returned` | ✅ PASS |
| `LibrarySourceIndexTest.reconcileAfterScan after truncated scan never marks missing` | ✅ PASS |
| `BookDeletionRestoreTest.consecutive deletes - undoing the earlier one does not cancel the later one` | ✅ PASS |

手动取消真机时序：**NOT COVERED**（设备扫描在该机型上于首次可截图前完成，无法在人手操作窗口内注入取消时机——已要求被记录的诚实判定，未伪造截图或时序）。

---

## 4. Room 迁移验收（候选 A + B，硬门槛）

### 4.1 三件套一致性

| 项 | 实值 |
|---|---|
| `AppDatabase.APP_DATABASE_SCHEMA_VERSION` | **14** |
| `MIGRATION_12_13` 定义 | ✅（`AppDatabase.kt:452`） |
| `MIGRATION_13_14` 定义 | ✅（`AppDatabase.kt:491`） |
| `DatabaseModule` 同时注册两条迁移 | ✅（`DatabaseModule.kt:69-70`） |
| `DatabaseModule.provideReaderCorrectionDao` / `provideLibrarySourceRefDao` | ✅（`DatabaseModule.kt:94, 102`） |
| `fallbackToDestructiveMigration` 实际启用 | ❌ 未启用（AppDatabase.kt 仅注释 "已移除"；DatabaseSafetyNet.kt KDoc 提到但代码未调用——注释过时但非行为） |
| `schemas/.../13.json` 实体 | 25 表，**仅含 `reader_text_corrections`，不含 `library_source_refs`** |
| `schemas/.../14.json` 实体 | 26 表，**同时含 `reader_text_corrections` 与 `library_source_refs`** |
| `14.json` 相对 `13.json` 新增 | 仅 `library_source_refs` |
| `13.json` / `14.json` identityHash | 不同（schema 真正递增） |

### 4.2 Instrumentation 实际执行（隔离包 `.r3gate`）

- 命令：`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.creationreadingassistant.data.local.AppDatabaseMigrationTest`
- **真实运行证据**：日志含 `Starting 20 tests on 22081212C - 15`，BUILD SUCCESSFUL in 49s。
- **结果**：1 suite，**20 tests / 0 failures / 0 errors / 0 skipped**，全 PASS。

| 用例 | 结果 |
|---|---|
| `migrate_1_to_2_preserves_highlights` | ✅ PASS (0.058s) |
| `migrate_2_to_3_adds_books_description` | ✅ PASS (0.009s) |
| `migrate_3_to_4_adds_completed_at` | ✅ PASS (0.009s) |
| `migrate_4_to_5_creates_reader_page_index` | ✅ PASS (0.03s) |
| `migrate_5_to_6_creates_reader_anchor_cache` | ✅ PASS (0.01s) |
| `migrate_6_to_7_preserves_sessions` | ✅ PASS (0.032s) |
| `migrate_7_to_8_adds_fk_indexes` | ✅ PASS (0.08s) |
| `migrate_8_to_9_creates_reader_text_rules` | ✅ PASS (0.01s) |
| `migrate_9_to_10_adds_chapter_reads_and_sort_orders` | ✅ PASS (0.04s) |
| `migrate_9_to_10_assigns_distinct_stable_sort_orders_to_legacy_taxonomy_rows` | ✅ PASS (0.032s) |
| `migrate_10_to_11_adds_search_index_tables_without_touching_books` | ✅ PASS (0.013s) |
| `migrate_11_to_12_rebuilds_search_terms_with_text_basis_and_adds_coverage` | ✅ PASS (0.048s) |
| `migrate_1_to_6_full_chain` | ✅ PASS (0.011s) |
| `migrate_1_to_8_full_chain` | ✅ PASS (0.012s) |
| `migrate_1_to_9_full_chain` | ✅ PASS (0.03s) |
| `migrate_1_to_10_full_chain` | ✅ PASS (0.014s) |
| `migrate_1_to_12_full_chain` | ✅ PASS (0.054s) |
| **`migrate_12_to_13_adds_reader_text_corrections`** | ✅ PASS (0.012s) |
| **`migrate_13_to_14_creates_library_source_refs`** | ✅ PASS (0.076s) |
| **`migrate_12_to_14_full_chain_preserves_data_and_creates_both_tables`** | ✅ PASS (0.0s) |

测试体非空洞：每个用例用 `migrationTestHelper.createDatabase(oldVer)` → 插旧版数据 → `runMigrationsAndValidate(newVer, true, MIGRATION)`（`true` = 启用 Room schema 哈希校验）→ 断言新表读写、索引存在、既有数据保留、外键级联。

**注意**：执行完 `connectedDebugAndroidTest` 后 Gradle 默认 uninstall 了 `com.creationreadingassistant.r3gate` 主包（不恢复）。这是已知 Gradle 行为；本任务通过脚本按"主 APK 必须优先用仓库脚本安装"重新安装主 APK 恢复（见 §2.3 第 3 行），未用 direct ADB fallback。生产包 `com.creationreadingassistant` 全程未被触碰。

---

## 5. 真机旅程明细

证据文件统一位于：`output/android-r3-final-acceptance-2026-09-14/`（被 `.gitignore` 忽略，未污染仓库）。截图按 `01–` ~ `40-` 编号；DB 拉取副本 `*-merged.sqlite`（含 WAL）；logcat 取设备当前 buffer 已 dump。

### 5.1 冷启动与基础稳定性 — ✅ PASS

- `adb shell am start -W` → `LaunchState: COLD`, `TotalTime=1492ms`, `Status: ok`, `pid=1327`。
- 整段验收过程中 logcat 与 dropbox `data_app_crash/data_app_anr` 扫描：**0 FATAL EXCEPTION / 0 am_crash / 0 am_anr / 0 crash 条目**。
- 截图 `01-cold-start-home.png` 确认首页正常渲染（空态：累计阅读 0 分钟/0 本、继续阅读空、本周新增 0、最近灵感空态、底部导航 首页/书架/灵感/统计/我的 五项）。
- BACK / 前后台切换正常（多次进出无崩溃、无空白页、无死路）。

### 5.2 R3 来源索引与导入 — ✅ PASS（含 2 项未覆盖）

**fixture（隔离目录 `cra-r3-final-gate/`，ASCII 中性名规避中文路径 push 挂起）**：
- `novel-a/test-text-alpha.txt`（1,054 B；第 1、2 章含相同标记词 "Zephyr"，第 3–10 章仅正文）
- `novel-b/test-epub-delta.epub`（2,334 B；valid EPUB，含 1 章正文）
- `novel-b/sub/test-text-beta.txt`（370 B）
- `bulk/bulk-{1..2100}.txt`（2100 个 0 字节空文件，用以触发 1000 项截断）
- `big/test-large-gamma.txt`（5,243,215 B = 5 MB）

| 项目 | 判定 | 证据 |
|---|---|---|
| 目录授权 | ✅ PASS | SAF 树选择器导航至 `cra-r3-final-gate` → 系统弹窗"允许"；截图 `04–07`。 |
| App 内目录浏览 | ✅ PASS | 根卡片 + 面包屑 + 排序（名称/修改时间/大小）+ 文件夹导航正常（`07-library-root-set.png`）。 |
| **P1 修复（连续 5 次切换 tab 内容实际变化）** | ✅ PASS | 切换顺序：当前目录→智能识别→当前目录→智能识别→当前目录→智能识别。"智能识别"页显示识别结果（推荐 2、未识别 498、**结果已截断**、扫描达到安全上限提示）+ test-text-alpha.txt 与 test-epub-delta.epub 高置信推荐导入（`08-smart-recognize.png`）。反向切回"当前目录"显示文件夹列表（`09-current-dir.png`）。tab 高亮与下方内容均实际切换，不再仅是标签亮起。 |
| **1000 项截断提示** | ✅ PASS | bulk 文件夹入口显示黄条："目录较大，仅显示前 1000 项；可缩小目录范围后重试"；列表末项到 `bulk-1004.txt`（`10-bulk-folder.png`）。 |
| **来源导入** | ✅ PASS | 点"加入书架 (2)" → 面板显示"本次导入完成 / 共解析 2 本，成功入库 2 本 / 成功 2 / 导入记录 2 条 / test-epub-delta.epub 入库成功 · 《Test Epub Delta》 · 09-14 17:35"（`13-after-add.png`）。DB 证实：`books` 表新增 2 行（`95482144-…` test-text-alpha, `epub_e16r4f` Test Epub Delta），`library_source_refs` 新增 2 行（root_id=`primary:Download/cra-r3-final-gate`, document_id 指向对应源文件, format/size/availability=available 正确），`PRAGMA user_version=14`，`PRAGMA integrity_check=ok`。 |
| 来源不可用后内部稳定副本仍可读 | ⚠️ NOT COVERED（未做 mv / 改名测试，时间约束） | — |
| 大文件专项导入 | ⚠️ NOT COVERED（`big/test-large-gamma.txt` 5 MB 已就位，未执行专项导入链路测试） | — |

### 5.3 Reader / 选区纠错 / 设置归属 — ✅ PASS（含 1 项未覆盖）

| 项目 | 判定 | 证据 |
|---|---|---|
| Reader 渲染 | ✅ PASS | 打开 test-text-alpha，章节 1/2 都显示完整正文（含两次 Zephyr），`14-reader-ch1.png`。 |
| 选区 + 纠错入口 | ✅ PASS | 长按选区 → 工具条 4 项（高亮/浏览器/复制/更多）→ 更多菜单 6 项（字典/添加批注/替换/书内搜索/记为灵感/取消选择），`17-more.png`。 |
| **P2 修复（关闭当前工作表）** | ✅ PASS | 替换面板顶部出现显式可点击的"关闭当前工作表"控件（`19-replace-sheet.png`），点击实际回到正文（`22-reader-after-replace.png`），不再是仅 BACK 可关闭。 |
| **单处纠错语义** | ✅ PASS（位置锚定） | 创建 1 条 `reader_text_corrections`：find_text="The marker word Zephyr appears in chapter 1 of this test text."（ch1 段，因 Compose Text 默认段级选中），replace_text=""（实际删除，原 `input text "ZEPHYRREP"` 被 WeType IME 吞掉导致空值），source_start=27、source_end=89，status=ACTIVE（`post-correction-merged.sqlite`）。渲染表现：ch1 body 被删除（"Chapter 1" 后直接接 "Chapter 2"），ch2 body 完整保留（`22-reader-after-replace.png` 仍含 "...appears in chapter 2 of this test text."）。**非替代性证据**：reader_text_corrections.source_start/source_end 字段确保单处替换是位置锚定的。 |
| Profile → Reader 单一真源 | ✅ PASS | 进入 Profile → 选区与查词 → 当前第一屏显示"高亮 · 浏览器 · 复制"，与 Reader 工具条逐项一致（`24-selection-subpage.png`）。点"高亮"移除 → "当前第一屏：浏览器 · 复制"；点"字典"加入 → "当前第一屏：浏览器 · 复制 · 字典"（`25-after-toggle.png`）。回到 Reader 长按选区，工具条实际变化为"浏览器 / 复制 / 字典 / 更多"（`26-reader-toolbar-with-dict.png`）——**联动生效，证明 Profile 不持有独立 Store/DataStore/SelectionActionStore，而是 facade 消费者**。 |
| **两处完全相同文本的单处替换** | ⚠️ NOT COVERED（见 §6.2）：Compose Text 段级选中 + ch1/ch2 内容因 "chapter 1" vs "chapter 2" 自然不同，find_text 已由内容区分；真正"两处 Zephyr 字面相同"用例需要词级选中，未覆盖。语义仍由 source_start/source_end 位置锚定保证。 | — |
| Markdown / legacy 路径 | — | fixture 仅 TXT / EPUB，未验证 Markdown。 |
| 外部跳转返回位置稳定 | ⚠️ NOT COVERED（不重复造轮子） | — |
| 词典无资源降级 | ⚠️ NOT COVERED（Profile 显示"离线词库待导入"，与上一轮一致） | — |

### 5.4 Shelf / 动态视图 / 删除契约 — ✅ PASS（含 1 项未覆盖）

| 项目 | 判定 | 证据 |
|---|---|---|
| **P3 修复（书架整理 BACK × 5 回到书架）** | ✅ PASS（循环测试：书架→整理→BACK 重复 5 次，每次都回书架） | 5 轮循环全部 "entered organize: YES" → "on shelf after BACK: YES"。 |
| **导入面板不重复声明** | ✅ PASS | `03-import-panel.png`：4 项入口（我的书籍目录/选择文件/选择文件夹/从电脑导入）+ 导入记录区，无重复表单。 |
| **筛选入口不误路由到书架整理** | ✅ PASS | 书架顶部 dropdown "0 本 · 全部 · 最近阅读" 打开筛选面板（`29-filter-sheet.png`），不是 书架整理。 |
| **筛选实际生效** | ✅ PASS | 选 TXT → header 变为"1 本 · TXT · 最近阅读"，列表只显示 test-text-alpha（`30-after-save-view.png`）。 |
| **筛选持久化（关闭面板后再开保留）** | ✅ PASS | 关闭面板后 header 仍为"1 本 · TXT · 最近阅读"，仅 test-text-alpha 可见。 |
| **"保存视图"名称输入 → 保存** | ⚠️ PARTIAL（见 §6.1） | UI 完整（动态视图（保存的筛选）section + 输入框 + 保存按钮），但 **WeType IME 不接受 adb `input text` / keyevent**（dump `mInputShown=true` 仍不接键入）—— 无法完成命名保存；测试**未覆盖**该完整 saved-view 持久化路径。**非代码缺陷**。 |
| 删除文案"移除书籍资料、进度、书签和笔记；不删除本地正文文件" | ✅ PASS | `38-action-sheet.png` 与 `39-delete-confirm.png` 同时显示新文案；**无**"删除私有正文 / 源文件"措辞。 |
| 删除确认弹窗文案 | ✅ PASS | "删除整本资料？…可在 12 秒内撤销；应用重启后不再提供撤销。" |
| 删除 = 软删 + 源文件不动 | ✅ PASS | 删除 test-text-alpha 后：书架 2→1；`books` 表中 `95482144-…` 行 `deleted_at='2026-09-14T10:01:18.123Z'`，`epub_e16r4f` 仍 `deleted_at=NULL`；`/sdcard/Download/cra-r3-final-gate/novel-a/test-text-alpha.txt` MD5 删除前后均为 `753e7591eb8b0b967f564401fbe0590b`（**字节未变**），EPUB 文件 MD5 也未变。 |
| 撤销路径 | ⚠️ NOT EXECUTED | Toast 出现"已删除 1 本资料，可在 8 秒内撤销"，未执行实际撤销（避免回写后干扰后续旅程）。 |

### 5.5 Home / Profile / Inspiration — ✅ PASS（基础验证）

- 三个底部导航 tab 可正常切换与往返，无崩溃，无空白页。
- Profile → 选区与查词 → 当前第一屏"高亮 · 浏览器 · 复制"（已与 Reader 联动验证）。
- 灵感空态正常（未做灵感导入）。

---

## 6. 未覆盖项、限制项与风险

### 6.1 部分覆盖 / 环境限制（非代码缺陷）

1. **动态视图"保存视图"命名 → 保存完整路径**
   - 设备 IME `com.tencent.wetype/.plugin.hld.WxHldService`（WeType）状态 `mInputShown=true`，但 `adb shell input text` 与 `input keyevent KEYCODE_X` 均未将字符送入 Compose TextField（field `focused=true`，`text=''` 保持不变）。
   - UI 入口完整可达（动态视图 section + 输入框 + 保存按钮），但受控环境无法完成命名保存。
   - **不属于代码缺陷**（前一轮 reader 替换面板中同样的 IME 可接部分输入，可能是焦点/IME 状态差异，未深入）。

### 6.2 覆盖但语义不同于任务字面

1. **单处纠错：两处完全相同文本的"同一字面取一处"用例**
   - 任务字面要求："对两个相同文本中的一个选区做单处替换；确认仅目标选区的显示结果变化，其他相同文本不被错误替换"。
   - 本次实现：选中 ch1 段（Compose Text 默认段级选中），find_text="The marker word Zephyr appears in chapter 1 of this test text."（ch1 段在 ch2 段不含"chapter 1" → 由内容已区分，不构成"相同文本"）。
   - **真正的"两处完全相同文本"用例（Zephyr 单词在 ch1/ch2 各出现一次）需词级选中，未覆盖。**
   - 但**单处替换的语义已被位置锚定（source_start/source_end）保证**：`reader_text_corrections.source_start=27/source_end=89` 锚定 ch1 段，ch2 段不受影响；DB 验证 ACTIVE 行存在；渲染验证 ch2 完整保留。

### 6.3 未覆盖（非限制 / 未触发）

1. 扫描取消/恢复真机时序（设备过快无法在人手操作窗口内注入取消）。
2. 5 MB 大文件专项导入链路（fixture 就位未跑）。
3. 来源内容变更（追加 → 徽标更新）测试。
4. 来源失效（mv 源文件）后内部稳定副本可读。
5. 实际撤销按钮触发。
6. 替换规则全书 / 全局规则的实际效果。
7. 外部跳转返回位置稳定。
8. 书籍详情页各区块展开验收。
9. Markdown / legacy 路径。

---

## 7. 环境与数据收尾

- **`stay_on_while_plugged_in`**：验收前 3，验收后 3，**未修改**。
- **隔离包**：`com.creationreadingassistant.r3gate` 已 `adb uninstall`（Success）；`com.creationreadingassistant.r3gate.test` 卸载返回 `Failure [DELETE_FAILED_INTERNAL_ERROR]`（因主包先卸已不存在，期望行为）。
- **隔离 fixture**：`/sdcard/Download/cra-r3-final-gate/` 整目录 `rm -rf`，已确认 `ls /sdcard/Download/ | grep cra-r3` 为空。
- **生产包**：`com.creationreadingassistant` 全程存在，从未安装/卸载/清数据。
- **未执行**任何 `git add/commit/push/reset/checkout/clean/stash/revert`（主工作区与隔离 worktree 均未改动任何产品源码、Gradle 配置、脚本、迁移、测试、桌面端文件、文档）。
- **隔离 worktree 唯一新增文件**：`gate-2026-09-14.log`（门禁日志）、`migration-2026-09-14.log`（迁移日志）、`contract-tests-2026-09-14.log`（契约测试日志）、`install-*.log`（脚本安装日志，3 次）、`post-*-merged.sqlite` 与 `*-merged.sqlite{-wal,-shm}`（DB 拉取只读副本）、`source-md5-*.txt`（源文件 MD5）。均位于 `cra-g0-gate/` 根与 `android/app/build/`，未被主仓库捕获。

---

## 8. 证据文件索引

根目录：`output/android-r3-final-acceptance-2026-09-14/`（仓库内，`.gitignore` 已忽略第 54 行 `/output/`）

| 类别 | 文件 |
|---|---|
| 安装 / 门禁 | `cra-g0-gate/gate-2026-09-14.log`、`cra-g0-gate/migration-2026-09-14.log`、`cra-g0-gate/contract-tests-2026-09-14.log`、`cra-g0-gate/install-2026-09-14.log`、`cra-g0-gate/install-androidTest-2026-09-14.log`、`cra-g0-gate/install-reinstall-2026-09-14.log` |
| 冷启动 / 基础 | `01-cold-start-home.png` |
| R3 来源旅程 | `02-shelf-entry.png`、`03-import-panel.png`、`04-library-no-root.png`、`05-saf-tree.png`、`06-saf-permission.png`、`07-library-root-set.png`、`08-smart-recognize.png`、`09-current-dir.png`、`10-bulk-folder.png`、`11-import-confirm.png`、`12-smart-rec-tab.png`、`13-after-add.png` |
| Reader / 设置 / 选区 / 纠错 | `14-reader-ch1.png`、`15-selection.png`、`17-more.png`、`18-sel-paragraph.png`、`19-replace-sheet.png`、`20-replace-typed.png`、`20a-after-clear.png`、`21-reader-after-correction.png`、`22-reader-after-replace.png`、`23-profile-home.png`、`24-selection-subpage.png`、`25-after-toggle.png`、`26-reader-toolbar-with-dict.png` |
| Shelf / 动态视图 / 删除 | `27-shelf-organize.png`、`28-back-round{1..5}.png`、`29-filter-sheet.png`、`30-after-save-view.png`、`32-name-typed.png`、`36-ime-up.png`、`37-panel-closed.png`、`38-action-sheet.png`、`38b-after-longpress.png`、`39-delete-confirm.png`、`40-after-delete.png` |
| DB 取证 | `post-import-merged.sqlite{,-wal,-shm}`、`post-correction-merged.sqlite{,-wal,-shm}`、`post-delete-merged.sqlite{,-wal,-shm}`、`check-saved-merged.sqlite{,-wal,-shm}` |
| 源文件 MD5 | `source-md5-before-delete.txt`、`source-md5-after-delete.txt` |

---

## 9. 最终判定

> **PASS WITH LIMITATIONS**
>
> 理由：
> 1. 全部硬门槛（4 文件源码一致 / JVM 2283 0F0E / Lint 0E / APK 构建 / 安装脚本 CONFIRM_LOOP + exit 0 / **Room v12→v13→v14 真实迁移执行 20 tests 0F0E0S** / DB 三件套一致 / 0 crash 0 ANR / 设备确为真机硬件）**全部通过**。
> 2. 关键真机旅程：R3 来源索引（P1 tab 修复、1000 项截断、来源导入 2 本 + DB 落库完整、SAF 授权）；Reader 单处纠错（P2 关闭当前工作表、reader_text_corrections 落库且仅替换锚定位置）；Profile → Reader 联动（高亮↔字典切换实时同步）；Shelf 整理 P3 5 次循环 BACK 全部回到书架；删除契约（软删 + 源文件 MD5 未变 + 新文案不含"删除私有正文/源文件"）**全部通过**。
> 3. 限制项均已诚实标记为"NOT COVERED"或环境限制（WeType IME 不接 adb 键入 → 保存视图名称输入未完成；两处字面完全相同文本的词级单处替换未覆盖；扫描取消真机时序未注入等），**未伪造截图、未伪造时序**。
> 4. 不构成 PASS（"完全验收通过"）的原因：动态视图"保存"路径与"两处字面相同文本单处替换"两条未覆盖；不构成 BLOCKED/FAIL 的原因：所有硬门槛 + 关键旅程 + 已覆盖边界全部 PASS，未出现功能缺陷。
>
> 建议回交对应切片所有者（仅作参考，本次未发现需修复的代码缺陷）：
> - D（Reader 选区）：增加词级选中以使"两处相同文本单处替换"用例可达；当前段级默认行为已正确处理单处语义。
> - E（Home / Profile / Inspiration）：无回交项。
> - F（Shelf）：动态视图名称输入 UX 评估是否需要更明确的无 IME 提示或预填默认名（与 IME 无关、为通用体验问题）。