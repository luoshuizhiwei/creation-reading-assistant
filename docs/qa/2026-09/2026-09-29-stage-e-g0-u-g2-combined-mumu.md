# 阶段 E G0+U+G2+runner 窄修联合候选 · MuMu 设备验证（2026-09-29）

> **（Codex 复核位：留空待总指挥转交复核）**

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`；HEAD `5aed5939600e491843e3c44453921bd6273a233c`（开工前后一致；已核对未误入 `D:\develop\Code\Codex\creation-reading-assistant`）。
- 性质：**runner 窄修（唯一所有权文件）+ 四组联合候选 E 盘隔离构建 + MuMu 联合验证与恢复对账**。
- 仓库源码改动 = **仅** `android/app/src/androidTest/java/com/creationreadingassistant/testing/CraIsolatedTestRunner.kt`（SHA `343b7fb1…`→`cc5e1fd6…`）；未修改 Antigravity 的报告、handoff、公共配置或其他源码；未 stage/commit/push/reset/checkout；未切换项目目录。
- 设备：MuMu `emulator-5556`（本轮独占；开工 `adb devices` 核对唯一在线，API 32）；只安装 `com.creationreadingassistant.qahost` 与 `.qahost.test`，**正式包未安装**（`pm list packages` 前后核对）；未触碰真实手机或正式书库。
- 输入：[三项隔离验证复核](../../coordination/reviews/2026-09/2026-09-29-stage-e-three-isolation-review.md)、[U 设备验收与 runner 审查复核](../../coordination/reviews/2026-09/2026-09-29-stage-e-u-device-runner-review.md)、[G2 runner 范围审查](../../coordination/2026-09-29-stage-e-g2-runner-scope-review.md)、[G0 精确候选](../../coordination/legacy-stages/2026-09/2026-09-29-stage-e-g0-isolated-candidate.md)、[U 精确候选](../../coordination/legacy-stages/2026-09/2026-09-29-stage-e-upsert-version-candidate.md)、[G2 候选](../../coordination/legacy-stages/2026-09/2026-09-29-stage-e-qahost-candidate.md)。

## 0. 结论先行

1. **8 项设备测试全部通过**（G0 的 3 项 v15 迁移 + U 的 5 项父表级联），逐项计数取自原始 instrumentation 日志（每项 `INSTRUMENTATION_STATUS` 就绪、`stack=` 0 条、`OK (3 tests)` / `OK (5 tests)`、ADB EXIT 0）。
2. **runner 窄修的 context 依据在 qahost 分支获运行时实证**：两次 instrumentation 的 logcat 各出现一行 `newApplication target=com.creationreadingassistant.qahost manifestApplication=com.creationreadingassistant.App -> CraIsolatedTestApplication (isolated)`——`newApplication` 收到的 `context.packageName` 就是目标包名，分支判断可靠（§1）。**普通 debug 透传分支设备 NOT RUN**（正式包不安装约束），仅有编译、生成 manifest 与源码证据。
3. **现有 v15 库冷启动联合候选无 15→14 降级崩溃**：U 窗口在 v14 候选上复现的 `A migration from 15 to 14 was required but not found` 未再出现，进程存活且 `topResumedActivity` 为 qahost `MainActivity`。
4. **恢复对账全部一致**：53/53 持久文件 SHA 清单逐行相同（含主 DB `7bd076c6…` 与既有跨窗口记录一致）、重装的两枚 APK 与原装字节一致（`e82904cc…` / `fdbd0190…`）、密度 280、包清单干净、`/data/local/tmp` 临时文件已清理。
5. 过程事故如实登记（§8）：基线稀疏导致首次构建失败、`$G` 未定义 + MSYS 路径改写、`adb shell` 引号折叠——均已当场定位并修正，未重复安装、未覆盖备份。

## 1. runner 窄修（唯一所有权文件）

`CraIsolatedTestRunner.kt` 的 `newApplication` 按目标包精确分支：

- `context.packageName == "com.creationreadingassistant.qahost"` → 注入 `CraIsolatedTestApplication`（裸 Application，跳过生产 `App` 的 Hilt 图谱、应用级夜间模式与三条 `cra-startup` 接线——S04 隔离语义不变）；
- **其他任何目标包（含普通 debug 正式包）透传 `className`**（清单声明的生产 `Application`）；
- 每次决策写一行 `android.util.Log.i`（tag `CraIsolatedTestRunner`，记录 target 包、清单 Application、所选分支）——不依赖 `AppLog`（隔离 Application 下其 `debugEnabled` 未初始化，`AppLog.i` 也不进 logcat）。

**context 可靠性核实（先核实后依赖）**：

- 框架链：`Instrumentation.newApplication(ClassLoader, String, Context)` 由 `LoadedApk.makeApplication` 以**目标包**的 Application Context 调入，`context.packageName` 即被测包名；
- 运行时实证（qahost 分支）：两次 instrumentation 的 logcat 行（§4 末）显示 `target=com.creationreadingassistant.qahost`、`manifestApplication=com.creationreadingassistant.App`（即透传分支将使用的正是清单声明的生产 App 类）；
- **透传分支（debug）的运行态 = NOT RUN**，不宣称"普通 debug 语义已恢复"——只有编译、生成 manifest（runner 名与 targetPackage 不变）与本源码证据。

## 2. 联合候选构造（HEAD + G0 + U + G2 + runner 修正）

基线 `git archive HEAD android` → 13 文件 hunk 级 overlay；清单与全部 SHA 在 `E:\creation-reading-assistant\stage-e-g0-u-g2-runner-20260929\combined-candidate-manifest.txt`（SHA `d1421476…`）：

| 文件 | 组/选取 | 候选 SHA-256（前 16 位） | 排除面验证 |
|---|---|---|---|
| `build.gradle.kts` | G2 A/C/D/E + U 版本 hunk（= 全部 5 个 U0 hunk = 工作树） | `aac93f05a51d8017` | 候选==工作树 |
| `AppDatabase.kt` | G0 全 2 hunk | `dabb1be7576071df` | 候选==工作树 |
| `DatabaseModule.kt` | G0 全 3 hunk | `ec18fd068ff90d22` | 候选==工作树 |
| `SearchTermDao.kt` | **仅 S5**（索引删除） | `038adc4eea8aa8b3` | **与 G0 报告发布的候选 SHA 逐字节一致**；vs 工作树差异 = 仅被排除的 S1–S4/S6 |
| `BookDao.kt` | **仅 B1–B4** | `66c37e27560b760d` | vs 工作树差异 = 仅被排除的 B5（`getAllActive`/`activeIds` 仅在工作树侧出现） |
| `InspirationDao.kt` / `TaxonomyDao.kt` | U 全部 | `241a8cc5…` / `9f638576…` | 候选==工作树 |
| `schemas/…/15.json` | G0 新文件 | `c94ca0e506bfd431` | — |
| `AppDatabaseMigrationTest.kt` | G0 全 8 hunk | `9dd237e4591e3f58` | 候选==工作树 |
| `ParentUpsertCascadeSafetyTest.kt` | U 新文件 | `8f26eebc8c4204d0` | — |
| `CraIsolatedTestRunner.kt` | **RF（本轮窄修）** | `cc5e1fd62fe3e2e2` | 旧版 `343b7fb1…` |
| `CraIsolatedTestApplication.kt` / `IsolatedTestContext.kt` | G2 | `286b5250…` / `7d97e13b…` | 候选==工作树 |

hunk 应用器以 SearchTermDao 命中 G0 报告发布 SHA 完成跨窗口自证；`git apply` 因 CRLF 不可用于本流程（改用 Python 行级应用）。

## 3. 构建（E 盘隔离目录 `E:\creation-reading-assistant\stage-e-g0-u-g2-runner-20260929\baseline\android`）

| # | 命令 | 退出码 |
|---|---|---|
| 1 | `gradlew.bat :app:assembleDebugAndroidTest --offline --console=plain`（不带属性 → testBuildType=debug） | **0**（3m32s，52 tasks） |
| 2 | `gradlew.bat :app:assembleQahostAndroidTest :app:assembleQahost -PcraAndroidTestBuildType=qahost --offline --console=plain` | **0**（3m23s，72 tasks） |

| APK | SHA-256（前 16 位） |
|---|---|
| `app-debug-androidTest.apk` | `630b7fe7f20cf3d7` |
| `app-qahost-androidTest.apk` | `848878049fe5535f` |
| `app-qahost.apk` | `be75513e2e5054d7` |

生成配置（aapt）：debug androidTest instrumentation = `CraIsolatedTestRunner` + targetPackage `com.creationreadingassistant`；qahost androidTest = 同 runner + `.qahost`；**qahost 主 APK badging `versionCode='3' versionName='0.5.1'`**——U 的版本 hunk 在联合候选中生效。

## 4. 设备验证（8 项逐项）

设备前核对：`adb devices` 唯一 `emulator-5556`；`pm list packages` 仅 qahost+`.test`；冷备先行（§6）。安装：`adb -s emulator-5556 install -r -t` 两 APK（v3 覆盖 v2，数据保留），设备侧 `dumpsys` 复读 `versionCode=3 versionName=0.5.1`。

| # | 组 | 测试 | 计数来源 | 结果 |
|---|---|---|---|---|
| 1 | G0 | `migrate_14_to_15_drops_redundant_term_index` | 原始日志 current=1/numtests=3 | 通过（无 stack） |
| 2 | G0 | `openingDatabaseRebuildsPartialIndexesAndDoesNotResurrectTermIndex` | current=2/numtests=3 | 通过 |
| 3 | G0 | `migrate_13_to_15_full_chain_keeps_old_data_and_creates_source_refs` | current=3/numtests=3 | 通过 |
| 4 | U | `upsertingBookPreservesAllChildRows` | numtests=5 | 通过 |
| 5 | U | `upsertingBooksInBatchPreservesChildRows` | numtests=5 | 通过 |
| 6 | U | `upsertingInspirationPreservesVariantsAndNoteLink` | numtests=5 | 通过 |
| 7 | U | `upsertingTaxonomyParentsPreservesJoinRows` | numtests=5 | 通过 |
| 8 | U | `softDeletePreservesChildRows` | numtests=5 | 通过 |

原始日志：`mumu/instr-g0-3-migration.log`（`OK (3 tests)`，SHA `3344ef12…`）与 `mumu/instr-u-5-parent-cascade.log`（`OK (5 tests)`，SHA `f79b906a…`），两份 `stack=` 计数均为 0、`INSTRUMENTATION_CODE: -1`。

**runner 分支运行时实证（logcat，两次独立进程）**：

```
09-29 04:56:07.618 I CraIsolatedTestRunner: newApplication target=com.creationreadingassistant.qahost manifestApplication=com.creationreadingassistant.App -> CraIsolatedTestApplication (isolated)
09-29 04:56:35.369 I CraIsolatedTestRunner: newApplication target=com.creationreadingassistant.qahost manifestApplication=com.creationreadingassistant.App -> CraIsolatedTestApplication (isolated)
```

## 5. 现有 v15 库冷启动（15→14 降级崩溃不再复现）

`am start -n com.creationreadingassistant.qahost/com.creationreadingassistant.MainActivity` 后 10 秒：进程存活（pid=16471）、`topResumedActivity=…qahost/…MainActivity`、logcat 中 `FATAL EXCEPTION` / `A migration from 15 to 14 was required` 计数 **0**。U 窗口在 HEAD+U+G2（v14）候选上观察到的降级崩溃在 HEAD+G0+U+G2+runner（v15）候选上**未复现**。冷启动后随即 `force-stop`，未做其他 UI 操作。

## 6. 冷备、恢复与对账

- **冷备（改动前）**：`run-as` 整目录 tar（1,181,988,864 B，SHA `7a35d9bd…e09b9a`）+ 持久状态逐文件 SHA 清单（`./databases ./files ./shared_prefs`，53 行；主 DB `7bd076c6…`、`-shm` 在列、`-wal` 为 0 字节 SHA `e3b0c442…`，与跨窗口 checkpointed 状态一致）+ 两枚已装原 APK 拉取（SHA `e82904cc…` / `fdbd0190…`）+ 密度 280。
- **恢复序列**：force-stop → `pm uninstall` `.test` 与主包（清除本候选数据）→ 安装原 APK 两枚 → tar push 至 `/data/local/tmp` → `run-as … tar -xf` 回写 → 删除临时 tar → **未再启动应用**（保持 checkpointed 状态直至对账完成）。
- **对账结果**：after 清单 **53/53 与 before 逐行相同**（`diff` 为空）；重装 APK 设备侧 `sha256sum` = `e82904cc…` / `fdbd0190…`（与原装一致）；`wm density` 280；`pm list packages` 仅 qahost+`.test`；`/data/local/tmp` 无本轮残留。
- 说明：本轮 tar SHA 与 U 窗口记录（`95b1b2be…`）不同——tar 流包含非持久目录（cache/code_cache）的时变内容与元数据，**内容一致性以 53 行文件 SHA 清单与主 DB SHA 为准**。

## 7. 未验项（NOT RUN，不外推）

1. **普通 debug 设备行为 = NOT RUN**：透传分支的运行态（生产 `App`/Hilt/主题/`cra-startup` 在 debug instrumentation 进程内执行）没有任何设备证据；本候选的 debug androidTest APK（`630b7fe7…`）仅完成编译打包与 manifest/dex 级证据。不得以 qahost 的 8 项结果外推。
2. 本轮 8 项覆盖 G0 的迁移三件套与 U 的父表五件套；`AppDatabaseMigrationTest` 的既有 23 项与 U 报告 §9 H4 所列未播种路径未在本轮重跑。
3. 联合候选的 JVM 全量（对照 G0 的 2286）未在本目录重跑——本轮任务边界为联合构建 + 受影响设备测试。

## 8. 过程记录（如实）

1. 首次构建失败（EXIT=1）：基线目录误建为稀疏 overlay（`git archive -C "$G"` 落在 `$G/android`，而 overlay 写入自建 `$G/baseline/android`），cmd 找不到 `gradlew.bat`；定位后以完整 HEAD 树重建 baseline 并重放 overlay（13 SHA 复核后重跑，成功）。未盲目重试。
2. 首次 `adb install` 失败一次：shell 变量 `$G` 未在当条命令定义，Git Bash 把设备侧路径改写为宿主路径；改用绝对 `/e/...` 路径成功（`Success`），无重复安装副作用。
3. `adb push` 远端路径被 MSYS 改写（`/data/local/tmp/...` → `D:/develop/Git/Git/...`）与 `adb shell` 引号折叠（`sh -c 'for …'` 被截断）两次失败；改用 `MSYS_NO_PATHCONV=1` + Windows 风格本地路径、`run-as … tar -xf` 直执行、SHA 清单统一 `exec-out` 后成功。设备上无误写残留（`secure_mkdirs` 失败即止）。
4. 恢复阶段的 after 清单首取失败（引号折叠导致 0 行）后立即用 exec-out 重取，before 清单未被触碰。

## 9. 边界声明

- 仓库改动仅 `CraIsolatedTestRunner.kt`（所有权内）；全部构建/备份/日志/清单在 `E:\creation-reading-assistant\stage-e-g0-u-g2-runner-20260929\`。
- 未触碰其他窗口的 E 盘目录（G0/UG2/g0g2-mumu）、MuMu 上窗口 1 的遗留状态（`/data/local/tmp` 原有 51 项未动）；未操作真实手机、正式书库；未安装正式包；未 stage/commit/push/reset/checkout。
- 报告与设备文件清单不含真实书名（清单中 `zz-s05-import-*`/`sample-*` 为既有隔离夹具中性名）。
- 落盘即停，交总指挥复核。
