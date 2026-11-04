# 阶段 E 候选 U+G2 隔离设备验证（MuMu / qahost）——父表 `@Upsert` 五项用例

- 执行者：Claude Code（GLM 5.3 会话），本轮唯一 MuMu/ADB 所有者
- 执行日期：2026-09-29（设备时钟 2026-09-29 04:00 前后）
- 恢复仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
  分支 `codex/recover-worktree-20260920`，HEAD `5aed5939600e491843e3c44453921bd6273a233c`（构建前后均为该值）
- 设备：MuMu 模拟器 `emulator-5556`（model SM-S9280，sdk 32 / Android 12），**所有命令显式 `-s emulator-5556`**
- 隔离目录：`E:\creation-reading-assistant\stage-e-ug2-mumu-20260929\`（`repo\` 候选、`apk\`、`logs\`、`device\`、`screens\`、`project-cache\`）
- 输入：[三项隔离复核](../../coordination/reviews/2026-09/2026-09-29-stage-e-three-isolation-review.md)、[U 隔离构建报告](../../coordination/legacy-stages/2026-09/2026-09-29-stage-e-upsert-isolated-build.md)、[G2 隔离构建报告](../../coordination/legacy-stages/2026-09/2026-09-29-stage-e-qahost-isolated-build.md)、[当前交接](../../handoff/current.md)
- 未执行：stage / commit / push / reset / checkout；未切换项目目录；未改产品源码与测试；未安装正式包

---

## 一、结论

| 项 | 判定 | 依据 |
|---|---|---|
| HEAD+U+G2 组合候选构造与双向校验 | **PASS** | 逐 hunk 核验，见 §三 |
| qahost 主 APK + androidTest APK 构建 | **PASS** | 两条 `assemble` 任务实际执行，EXIT 0，见 §四 |
| **`ParentUpsertCascadeSafetyTest` 5 个用例（设备）** | **PASS 5/5** | 逐用例 `STATUS_CODE=0` 实算，见 §五 |
| 版本号 0.5.1(3) 界面显示（U-C，上轮未验） | **PASS** | qahost 界面「应用版本 0.5.1」，见 §六 |
| 设备与数据恢复 | **PASS** | 77/77 文件 SHA 逐行相同、主 DB/APK/密度全部一致，见 §七 |

**唯一失败**：U+G2 候选**启动即崩**（§六.3）。已定性为 **v14 候选 vs v15 设备库的夹具前置不匹配**，不是 U 或 G2 的缺陷 —— 证据是同一候选在 v14 库上正常启动并显示 0.5.1。

---

## 二、口径：本报告说的是哪一种组合态

| 证据类别 | 覆盖范围 | 能否外推到 U-only |
|---|---|---|
| **U+G2 组合态**（本轮设备） | `@Upsert` 运行期 SQL 语义、版本号界面显示 | **不能**：候选同时含 G2 的 `testBuildType` / runner / qahost 变体 |
| **U-only 编译态**（上轮 U 报告） | HEAD+U 的 `compileDebugKotlin`、`assembleDebug`、11 套件 143 JVM 测试 | 上轮已验，本轮**未复跑** |
| **G2-only 编译态**（上轮 G2 报告） | debug / qahost 双链 androidTest 编译打包 | 上轮已验，本轮**未复跑** |

**本轮设备结果只对 U+G2 组合态成立。** 三项隔离复核已明确要求「不能直接把 U-only 的正式 debug APK 装入正式包」；本轮同样**没有**安装任何正式包（`pm list packages` 仅两个 qahost 包，见 §七）。

---

## 三、候选构造与逐文件差异

起点：`E:\creation-reading-assistant\stage-e-u-isolated-20260929\repo`（HEAD + U hunk 已应用，五个文件 SHA 已核验与 U 报告一致），tar 复制（排除 `build/`、`.gradle/`）后叠加 G2。

### 3.1 候选文件 SHA-256 与三方差异

| 文件 | 候选（U+G2） | HEAD | 工作树 | 差异性质 |
|---|---|---|---|---|
| `data/local/dao/BookDao.kt` | `70261818…395a1d46` | `b7500f8d…0979e4ed9` | `b344af67…dc7358cf` | 候选 = HEAD + **B1–B4**；相对工作树**只缺 B5**（`getAllActive` + `activeIds` 20 行） |
| `data/local/dao/InspirationDao.kt` | `241a8cc5…ca781105` | `9bc753ed…621fc319` | `241a8cc5…` | 候选 = HEAD + **I1–I2**；与工作树逐字节一致 |
| `data/local/dao/TaxonomyDao.kt` | `78c1b3da…e5c327a` | `78e1852b…5045eddc` | `9f638576…ae38d8e` | 候选 = HEAD + **T1–T4**；与工作树内容相同（工作树为 CRLF，候选为 LF 规范形） |
| `app/build.gradle.kts` | `94213a46…dae7917` | `2cb816d0…d49f479` | `aac93f05…63439606` | 候选 = HEAD + **U 版本号 hunk + G2 A/C/D/E**；与工作树一致（忽略行尾） |
| `androidTest/…/ParentUpsertCascadeSafetyTest.kt` | `8f26eebc…58cd8adbe` | （HEAD 无） | `8f26eebc…` | 与工作树逐字节一致 |
| `androidTest/…/testing/CraIsolatedTestRunner.kt` | `343b7fb1…91a6b074` | （HEAD 无） | 同 | G2 测试基建，与 G2 报告 SHA 一致 |
| `androidTest/…/testing/CraIsolatedTestApplication.kt` | `286b5250…93ac9856` | （HEAD 无） | 同 | 同上 |
| `androidTest/…/feature/reader/IsolatedTestContext.kt` | `7d97e13b…f099c038` | （HEAD 无） | 同 | 同上 |

完整记录：`candidate-construction.txt`（SHA-256 `dcbc560774fb34f09e10801d4f9e1f8071927c028af989c2fd6582fcc14907f6`）

### 3.2 双向校验（禁止整文件混入）

1. **候选 `build.gradle.kts` vs HEAD**（`logs/gradle-vs-HEAD.diff`）：差异**恰为** 5 处 ——
   G2 `testBuildType`、U `versionCode`/`versionName` + 4 行注释、G2 `testInstrumentationRunner`、
   G2 `create("qahost")`、G2 `qahostImplementation`。**无 `MIGRATION_14_15`、无 `getAllActive`/`activeIds`**。
2. **候选 `build.gradle.kts` vs 工作树**（忽略行尾）：**完全一致** —— 工作树的 gradle 本就只含这些 hunk。
3. **`BookDao.kt` 候选 vs 工作树**：差异**恰为 B5 的 20 行**（`getAllActive()` + `activeIds()`），已逐行打印确认。
4. `InspirationDao.kt` / `TaxonomyDao.kt` / `ParentUpsertCascadeSafetyTest.kt` 与工作树一致（行尾归一后）。

**U 组取舍**：`BookDao` 只取 B1–B4，**B5 被刻意排除**（B5 属 S12/S03 而非父表 `@Upsert`）。
**G2 组取舍**：只取 A/C/D/E 四个 hunk + 三个测试文件，**不含版本号 hunk B**。

---

## 四、构建

| # | 命令（工作目录 `E:\…\stage-e-ug2-mumu-20260929\repo\android`） | 退出码 | 结果 |
|---|---|---|---|
| 1 | `gradlew.bat :app:assembleQahost :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost --offline --console=plain --project-cache-dir E:\…\project-cache` | **0** | `BUILD SUCCESSFUL in 3m 37s`，72 tasks（47 executed, 25 from cache） |

日志：`logs/build-qahost-ug2.log`，SHA-256 `0654ce1014a40357cb84d5dbb695fa2eebb60aa3e5834a4df9be084f57da1345`

| 产物 | SHA-256 |
|---|---|
| `app-qahost.apk`（主） | `d14fde50b5be16d197e39f9b6a7e8f512890ede16fac6874b62f558a9603c88f` |
| `app-qahost-androidTest.apk` | `768fe5f9a218ee1c5ae4779906b6349336bfeeb4d314359e2c0d21ea29cfba12` |

**产物内容核对（`aapt`）**：
- 主 APK `package: name='com.creationreadingassistant.qahost' versionCode='3' versionName='0.5.1'` ⇒ **U 版本号 hunk 生效**；
- androidTest APK `instrumentation android:name="com.creationreadingassistant.testing.CraIsolatedTestRunner"` ⇒ **G2 hunk C 生效**。

---

## 五、设备运行 `ParentUpsertCascadeSafetyTest`（核心项）

### 5.1 命令与原始日志

```
adb -s emulator-5556 install -r apk/candidate-app-qahost.apk                    # Success
adb -s emulator-5556 install -r -t apk/candidate-app-qahost-androidTest.apk     # Success
adb -s emulator-5556 shell am instrument -w -r \
  -e class com.creationreadingassistant.data.local.ParentUpsertCascadeSafetyTest \
  com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
```

原始日志：`logs/instrumentation-parent-upsert.log`，SHA-256 `5f6ce30ee178f95783b188d78f074b019c7dc03431ba322b2aaa4394ed706bff`

### 5.2 逐用例真实结果（**不依赖 adb 退出码**）

从原始日志逐用例解析 `test=` 与最终 `INSTRUMENTATION_STATUS_CODE`：

| # | 用例 | STATUS_CODE | 判定 |
|---|---|---|---|
| 1 | `upsertingBookPreservesAllChildRows` | 0 | **PASS** |
| 2 | `upsertingBooksInBatchPreservesChildRows` | 0 | **PASS** |
| 3 | `upsertingInspirationPreservesVariantsAndNoteLink` | 0 | **PASS** |
| 4 | `upsertingTaxonomyParentsPreservesJoinRows` | 0 | **PASS** |
| 5 | `softDeletePreservesChildRows` | 0 | **PASS** |

- `numtests=5`（日志声明），实跑用例数 **5**，逐用例最终码全为 `0`；
- 汇总行 `OK (5 tests)`，`Time: 0.212`，`INSTRUMENTATION_CODE: -1`（= 正常结束）；
- **无 `FAILURES`、无 `Error`、无 `initializationError`**（三项隔离复核曾记录首轮合并语法调用产生 2 个 `initializationError` 不算通过 —— 本轮日志中不存在）。

### 5.3 语义边界

这 5 个用例用 `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build()`（测试 `setUp` 第 60 行）建立**内存库**，
因此**与设备上既有库的版本无关** —— 这正是它们能在 v14 候选上通过、而 App 本身却因库版本不匹配而崩溃的原因（§六.3）。

覆盖到的运行期 SQL 语义：父表 `@Upsert` 不触发外键级联删除（books / inspirations / tags / categories / shelves），
含批量 `upsertAll`、`softDelete` 路径。**未覆盖**：`reader_text_rules`/`reader_text_corrections` 播种、
同步合并路径（沿用 U 报告 §6 U-E 的边界）。

---

## 六、U-C：版本号界面显示（上轮未验，本轮已验）

### 6.1 夹具处理

设备既有库 `PRAGMA user_version = 15`（上一轮 G0+G2 候选所建），而 U+G2 候选基于 HEAD = **v14**。
为在候选上打开界面，临时把 `databases/` 移出（原库保留在设备 `/data/local/tmp`，且 E 盘已有完整冷备），
让候选新建 v14 库，验证后整目录回写冷备（§七）。

### 6.2 结果：**PASS**

qahost「我的 → 帮助与关于 → 日志与诊断」页显示：

| 字段 | 值 |
|---|---|
| **应用版本** | **0.5.1** |
| 系统版本 | Android 12 (API 32) |
| 厂商型号 | Samsung SM-S9280 |

证据：`screens/U-C-02-diagnostics.png` + `screens/U-C-02-diagnostics.xml`
（XML SHA-256 `b4d8251875a0633dad99dc06942363d4539c7b6fc8ebf054a7df6ed912639ad1`）

### 6.3 启动崩溃：已定性为夹具前置，非候选缺陷

| 项 | 值 |
|---|---|
| 现象 | 首次在**未处理 v15 库**的情况下启动候选 App → 「屡次停止运行」 |
| 栈顶 | `FATAL EXCEPTION: DefaultDispatcher-worker-7` |
| 根因 | `IllegalStateException: A migration from 15 to 14 was required but not found`（`RoomOpenHelper.onDowngrade`） |
| 归因 | 设备库 = v15，候选 = v14 且无 `MIGRATION_14_15` ⇒ Room 走降级路径 |
| 反证 | 同一候选在**新 v14 库**上正常启动（§6.2 即在该状态下取得）；同一候选的 5 个用例用内存库、5/5 通过 |
| 结论 | **夹具前置不匹配，不是 U 或 G2 的缺陷**。任何后续要在设备上验 U（v14 基线）候选的人，都必须先把设备库降到 v14 或换一台 v14 夹具设备 |

崩溃原始日志：`logs/crash-launch.log`

---

## 七、恢复与对账

| 项 | 期望 | 实测 | 结论 |
|---|---|---|---|
| 数据目录逐文件 | 77 文件 SHA 逐行相同 | `diff` 无输出 | **一致** |
| 主 DB | 与基线同 | `7bd076c60763d9d9a65fa3df990bcd67f2911686cdbcbf4310d37f0588499a28` | **一致** |
| `-shm` | 与基线同 | `fd4c9fda9cd3f9ae7c962b0ddf37232294d55580e1aa165aa06129b8549389eb` | **一致** |
| `-wal` | 与基线同 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`（空） | **一致** |
| 冷备 tar | — | `95b1b2bef4bd9d09b6ea45289101b836c772ed465cb58c8d3b8fb3afd6737253`（214,476,493 B） | 已存 E 盘 |
| 主 APK（设备上） | `e82904cc…24ef8a` | `e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a` | **一致** |
| 测试 APK（设备上） | `fdbd0190…27038d` | `fdbd019011088505fa37d71565259a0d76f6927407af69148379e2ef7c27038d` | **一致** |
| `wm size` / `wm density` | 1080x1920 / 280 | 1080x1920 / 280 | **一致** |
| `accelerometer_rotation` / `user_rotation` | 1 / 0 | 1 / 0 | **一致** |
| 夜间模式 / `stay_on` / `screen_off_timeout` | no / 0 / 60000 | no / 0 / 60000 | **一致** |
| 已装包 | 仅 qahost + qahost.test | `pm list packages` 仅这两项，**无正式包** | **一致** |
| `/data/local/tmp` `cra-*` | 0 | 0 | **一致** |
| 恢复后启动 | 正常 | 正常启动、书架 11 本、无新崩溃（04:03 那条崩溃属 `com.google.android.gms`，与本应用无关） | **一致** |

**恢复口径**：主库与全部数据文件逐字节一致（回写自冷备）；`-shm`/`-wal` 随应用启动可能重建，本轮两者哈希与基线相同。

---

## 八、失败与未验边界

| # | 项 | 状态 |
|---|---|---|
| 1 | U+G2 候选在 v15 设备库上启动 | **FAIL（夹具前置）**，见 §6.3；**非产品缺陷** |
| 2 | 本轮 5 个用例是 **U+G2 组合态**结果 | 不能外推为 U-only 的设备结论（U-only 本轮未构建、未装、未跑） |
| 3 | G2 普通 debug 变体的设备行为 | **NOT RUN**（本轮只装 qahost；三项隔离复核 §下一执行边界第 2 条仍开放） |
| 4 | G0（Room v15）与 U 的联合 | **未做**。本轮刻意排除 G0；且已证 v14/v15 夹具互斥（§6.3） |
| 5 | `@Upsert` 的其余路径 | `reader_text_rules`/`reader_text_corrections` 未播种、tags/categories/shelves 的 `softDelete` 未单独测、同步合并路径未测 |
| 6 | 实体手机 / MIUI / 正式书库 | 未触碰。全程 MuMu 模拟器 + qahost 隔离包 |
| 7 | U-only JVM 11 套件 143 测试 | 本轮**未复跑**（沿用 U 报告；其日志 SHA `4e029a49…` 已被三项隔离复核独立复算一致） |
| 8 | 行尾规范化（U 报告 H7） | 未在本轮处理；候选按 LF 规范形构造 |

---

## 九、工件清单（`E:\creation-reading-assistant\stage-e-ug2-mumu-20260929\`）

| 路径 | 说明 | SHA-256 |
|---|---|---|
| `candidate-construction.txt` | 候选构造记录 + 全部文件 SHA + 双向校验 | `dcbc560774fb34f09e10801d4f9e1f8071927c028af989c2fd6582fcc14907f6` |
| `logs/gradle-vs-HEAD.diff` | 候选 gradle vs HEAD 的完整差异 | — |
| `logs/build-qahost-ug2.log` | 构建原始日志 | `0654ce1014a40357cb84d5dbb695fa2eebb60aa3e5834a4df9be084f57da1345` |
| `logs/instrumentation-parent-upsert.log` | 5 用例原始 instrumentation 日志 | `5f6ce30ee178f95783b188d78f074b019c7dc03431ba322b2aaa4394ed706bff` |
| `logs/crash-launch.log` | v14/v15 夹具不匹配的崩溃栈 | — |
| `apk/candidate-app-qahost.apk` | 候选主 APK | `d14fde50b5be16d197e39f9b6a7e8f512890ede16fac6874b62f558a9603c88f` |
| `apk/candidate-app-qahost-androidTest.apk` | 候选测试 APK | `768fe5f9a218ee1c5ae4779906b6349336bfeeb4d314359e2c0d21ea29cfba12` |
| `apk/orig-qahost-base.apk` / `orig-qahost-test.apk` | 恢复用原 APK | `e82904cc…24ef8a` / `fdbd0190…27038d` |
| `device/qahost-cold-backup-before.tar.gz` | 冷备 | `95b1b2bef4bd9d09b6ea45289101b836c772ed465cb58c8d3b8fb3afd6737253` |
| `device/files-before.sha256.txt` / `files-after.sha256.txt` | 恢复前后逐文件 SHA（77 行） | 逐行相同 |
| `device/baseline.txt` | 设备参数基线 | — |
| `screens/U-C-02-diagnostics.png` / `.xml` | 版本号界面证据 | XML `b4d8251875a0633dad99dc06942363d4539c7b6fc8ebf054a7df6ed912639ad1` |
| `repo/` | HEAD+U+G2 候选树（含构建产物） | — |

## 十、纪律声明

- 唯一写入的仓库文件 = **本文件**；未改产品源码、测试、配置或其他窗口文件；未 stage/commit/push/reset/checkout；未切换项目目录。
- 设备侧只安装 `com.creationreadingassistant.qahost` 与 `com.creationreadingassistant.qahost.test`；**未安装正式包**、未触碰实体手机与正式书库。
- 操作前已冷备 qahost 整个数据目录与两个 APK 并记录逐文件 SHA / 主 DB / WAL / SHM / 密度；结束后逐项对账一致（§七）。
- 未把 U+G2 组合态结果写成 U-only 或 G2-only 结论；未用 JVM 结果替代设备语义。
