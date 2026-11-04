# Stage C · V4/V5 决策证据（2026-09-28，只读核查）

> **Codex 2026-09-28 独立复核：V4 证据分类接受，V5 改用 MuMu 的方向接受，62 条结果仍 NOT RUN。** 当前 E 盘 `EpubRepositoryReplaceTest` XML 实读 21/0/0/0、SHA-256 `75FA140CD505EF6CB62B9606F8F54204A1A113439BB873F27F876C5895C9DF29`，确与报告原哈希不同，不能冒充同次门禁。V5 的 MIUI 实体机启动阻塞不外推 MuMu，先按真实 FQCN 在 qahost 跑 62 条再逐项判定。用户已在本对话多次明确授权 MuMu 作为当前设备验证通道，无须另索“书面认可”；报告 §0/§2.5 这一审批前提作废。MuMu 结果只标模拟器范围，实体机边界如实保留。批次 1 需核对最新源码/测试 APK 输入，不能无条件复用 09-27 旧 APK。

- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`。未进入/未修改原仓库（`creation-reading-assistant`）。
- **性质**：**只读核查 + 证据对账**。唯一写入 = 本文件。未改源码、测试、Gradle、账本或他人报告；**未运行 Gradle / ADB / MuMu**；未触碰实体手机、正式书库、设备设置。
- **输入**：`AGENTS.md`、[交接入口](../../../handoff/current.md)、[C 阶段剩余项对账](../../reviews/2026-09/2026-09-28-stage-c-remaining-work-audit.md)（含顶部 Codex 更正与 V4/V5）、[S09 分诊](../../legacy-stages/2026-09/2026-09-28-s09-ui-suite-triage.md)、[9/22 S09-B+S15 联合验收](../../../qa/2026-09/2026-09-22-s09b-s15-duration-gesture-device-acceptance.md)、[S06 MuMu 验收 + Codex 复核](../../../qa/2026-09/2026-09-27-s06-mumu-page-turn-rtl-acceptance.md)、[S04 MuMu 隔离 instrumentation](../../../qa/2026-09/2026-09-27-s04-mumu-qahost-instrumentation.md)、[EPUB 回滚终版门禁](../../legacy-stages/2026-09/2026-09-27-s04-epub-rollback-final-code-gate.md)、[09-24 夜间复核](../../legacy-stages/2026-09/2026-09-24-overnight-results-corrigendum.md)、[Compose 宿主调查](../../../qa/2026-09/2026-09-24-compose-test-host-investigation.md)、当前源码与 E 盘工件。
- **口径**：只提供**事实、证据路径、建议、未验证项**；**不调整任何全局百分比**（A–E 维持对账报告口径 `2/5=40%`，C 未退出，由总指挥统一调整）。凡未实际运行/未复跑的项，一律不写 PASS。
- **引用约定**：E 盘工件为仓库外证据；本轮对 XML/日志/APK 做**逐字节 SHA-256 实算**，不抄报告自报值。测试样本一律中性命名（「测试 EPUB」等）。

---

## 0. 结论先行

1. **V4 — S04 EPUB 回滚终版**：当前源码的回滚**形态**与门禁报告描述一致（入口残留守卫 + `backupReady/promoteStarted` 分流 + `restoreBackupAfterFailedPromotion`），四个争议点**均有源码级证据**。**但**：门禁报告 §4.2 自报的 XML 哈希 `3b0730ae…` 在当前构建目录**不可复现**——该路径下的 XML 已被一次**更晚的运行**覆盖（当前 XML 哈希 `75FA140c…`，内部时间戳 `2026-09-27T07:16:30Z`＝+08 `15:16:30`）；**不过**，覆盖后的 XML **仍可回读**且仍为 `tests="21" failures="0" errors="0"`。因此对账报告「其 XML 已被后续定向运行覆盖（**不可回读**）」应更正为：**「报告哈希对应的那次 XML 已被覆盖，但同类 21/21 结果可回读（来自更晚一次运行）」**。结论：**21/21 不能仅凭报告数字标本轮通过**；应登记为「有可回读的 21/21 结果、但非报告哈希那次工件」，并在 **D 冻结构建**时**当场重跑并当场登记哈希**。
2. **V5 — S09-B/S15 共 62 条 UI 自动化**：62 条 = `37+10+7+8`，四类**均已编译进 qahost androidTest 变体**，且**承载它们的测试 APK 正是 09-27 在 MuMu 上跑出 `OK (12 tests)` 的那一个**（同为 `createComposeRule()`→`ComponentActivity` 的 Compose 宿主路径）。**「所有 Compose 测试无法运行」不是当前事实**。历史阻塞（`result code=102` / `Abort background activity starts`）是 **MIUI 实体手机策略层**行为，**不可直接套到当前 MuMu（AOSP Android 12 x86_64）**。据此给总指挥的裁决建议：**MuMu 通道可承载这 62 条**，先跑「批次 1」取得逐项结果；**实体手机/MIUI 行为**降级为 SR-ENV-1 的**残余边界**（用户无法用实体手机，需总指挥/用户书面认可「MuMu 限定」不等于「真机验收」）；**在未实际运行前，62 条一律不写 PASS、也不写 FAIL**。
3. 本轮**未新增任何运行**，V4 的 21/21、V5 的 62 条均为**未运行/不可回读原始工件**状态；下列建议均为**给总指挥的建议，未执行**。

---

## 1. V4 — S04 EPUB 回滚终版独立复核

### 1.1 当前源码（本轮直接核对，非引用报告）

| 对象 | 事实 |
|---|---|
| `android/app/src/main/java/com/creationreadingassistant/feature/reader/EpubRepository.kt` | 大小 27 350 B，mtime **2026-09-28 00:59:42** |
| 回滚相关符号（本轮 grep 实核） | `:85 suspend fun replaceStoredEpub(...)`；`:83` KDoc 明示「无残留时保持下方 `backupReady` / `promoteStarted` 的进程内回滚语义不变」；`:143-144` 定义 `var backupReady`/`var promoteStarted`；`:142` 注释「只有 backupReady 时才允许删除 / 覆盖 target」；`:206-211 if (backupReady) { if (promoteStarted) target.delete() … }`；`:254 private fun restoreBackupAfterFailedPromotion(...)`；`:380 suspend fun saveProgress(...)` |
| 入口残留守卫 | 以 `backupReady/promoteStarted` 双布尔分流 + 残留守卫实现，形态与门禁报告 §3 表述一致（本轮为**符号级核对**，未逐行 diff） |
| 测试 `android/app/src/test/java/…/EpubRepositoryReplaceTest.kt` | 大小 70 559 B，mtime **2026-09-27 11:20:53**，`@Test` 计数 **21**（与门禁报告「20 既有 + 1 新增」一致） |
| 三个关键用例（本轮 grep 实核） | `:694 replaceStoredEpub keeps existing target when staging copy fails before backup`；`:1069 replaceStoredEpub keeps target untouched until backup is established`；`:1252 replaceStoredEpub surfaces restore failure through the caught exception chain` |

> 关于「mtime 09-28 00:59 的改动来自同文件 `saveProgress` 的 S12 locator 修正、非回滚函数」：此为**对账报告已述**，本轮**未做行级 diff 复核**，不作独立断言（列为未验证项 §1.5-3）。

### 1.2 门禁报告自报 vs 本轮可回读证据（对账表）

| 项 | 门禁报告自报（[链接](../../legacy-stages/2026-09/2026-09-27-s04-epub-rollback-final-code-gate.md) §4.2） | 本轮实算/实读 | 判定 |
|---|---|---|---|
| 测试数 | `tests="21" skipped="0" failures="0" errors="0"` | 当前 XML 仍为 `tests="21" skipped="0" failures="0" errors="0"` | **一致** |
| XML 路径 | `D:\…\cra-s04-buildlink\app\test-results\testDebugUnitTest\TEST-com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest.xml`（junction → E 盘） | 同路径存在，4448 B，mtime **2026-09-27 15:16:43**，SHA-256 **`75FA140CD505EF6CB62B9606F8F54204A1A113439BB873F27F876C5895C9DF29`**，内部 `timestamp="2026-09-27T07:16:30"`（＝+08 15:16:30），`hostname="LUOSHUIZHIWEI"`，`time="4.431"` | **哈希不符** |
| XML SHA-256（报告声明） | `3b0730aec38d21514dad7f57ec1340184faa4a4d7bc8cacc18123f844456d833` | 该哈希在当前目录**无可复现文件** | **已被覆盖** |
| 运行日志 | `E:\creation-reading-assistant\codex-s04-build-20260925\test-final-code-gate.log`，SHA `d4d1854015b94268d9cea4463453ecac6064d71a6f80802c0b06d7e58215c053` | 存在，3045 B，mtime **2026-09-27 11:22:45**，SHA-256 **`D4D1854015B94268D9CEA4463453ECAC6064D71A6F80802C0B06D7E58215C053`** | **完全一致** |
| HTML 报告（报告未列哈希） | — | `…\reports\tests\testDebugUnitTest\classes\…EpubRepositoryReplaceTest.html`，5933 B，mtime **15:16:43**（与 XML 同刻），SHA-256 `747C849F0C890B534BDA7144BED077268FA3CE0202E2AD284296E0B297946C32` | 与 XML 同属**更晚一次运行** |

本轮全 E 盘检索 `TEST-*.xml`：`EpubRepositoryReplaceTest` 的 XML **仅此一份**（无保留副本）。

### 1.3 可回读 / 已覆盖 / 结论口径

- **可回读**：① 门禁**运行日志**（哈希与报告逐位一致，`BUILD SUCCESSFUL in 1m 27s`）；② 当前构建目录下的 **21/21 XML（来自更晚一次运行）** 与其 HTML；③ 源码与测试形态（§1.1）。
- **已覆盖（不可回读原始字节）**：门禁报告 §4.2 **声明的** XML（SHA `3b0730ae…`）——同一共享构建目录内被 15:16 的运行覆盖。
- **口径修正**：对账报告 V4 的「**XML 已被后续定向运行覆盖（共享构建目录，不可回读）**」应精确为：**「报告哈希那次 XML 已被覆盖；但同类 21/21 结果在覆盖后的 XML 中仍可回读」**。这不等于「本轮 21/21 通过」——覆盖后的 XML 是**另一次运行**的产物，其源码/夹具新鲜度**未在本轮绑定核对**。
- **不因 mtime 重跑**：遵循对账报告 V4 最小任务，不在 C 阶段为追哈希重跑设备/构建。

### 1.4 建议归类（C 内 / D 冻结重跑）

| 项 | 本轮建议 |
|---|---|
| EPUB 回滚代码级复核登记 | **可在 C 内完成**（零构建）：以本报告 §1.1–§1.3 作为登记基线，结论 = 「形态与报告一致；21/21 有可回读结果但非报告哈希工件」。 |
| `EpubRepositoryReplaceTest` XML 哈希锚定 | **归 D 冻结批**：D 冻结构建时**当场重跑、当场登记** XML + 日志哈希（避免共享目录二次覆盖，见 09-24 夜间报告 §4 留证纪律）。 |
| 09-24 E3「P1 候选」（staging 复制失败于 backup 建立前 ⇒ 误删完好旧 target） | **已被当前测试覆盖**：XML 用例清单含 `keeps target untouched until backup is established`、`keeps existing target when staging copy fails before backup`；对应源码 `:142/:206-211` 的 `backupReady` 门控。判为**代码/夹具层已闭合**（前提：XML 为同夹具新鲜度——见 §1.5-1）。 |
| 设备路径（隔离测试书 EPUB 替换/中断的真机表现） | **归 D 冻结批**（B 退出复核已如此归类）：真机/低存储/并发路径**仍未验证**（门禁报告 §5 自述）。 |
| 低存储 / 并发 / `delete()` 返回 false 路径 | **维持未验证**，不在 C 内扩范围（门禁报告 §5.2–§5.5 自述）。 |

### 1.5 V4 未验证项

1. 覆盖后 XML 那次运行（15:16）所对应的**源码/夹具新鲜度未绑定核对**——不能据此断言「当前源码 21/21」。需 D 冻结时**同批**跑并登记（源码 mtime、APK/测试 class mtime、XML/日志哈希三件套）。
2. `EpubRepository.kt` mtime 09-28 00:59 的**改动行归属**（是否纯 `saveProgress` 的 S12 locator 修正）本轮未做行级 diff，未独立断言。
3. 门禁报告 §1 的 AppLog 接缝、异常链 suppressed 形态（协程 1.10.2 恢复开启时的复制语义）——本轮仅转述，未复跑未复核字节码。
4. 真机低存储/跨卷 `renameTo=false` 频率、诊断页用户可见效果、`.backup` 残留现场——**未验证**（报告自述，维持）。

---

## 2. V5 — S09-B/S15 共 62 条 UI 自动化

### 2.1 62 条构成与产物核对（本轮源码/产物级实核）

| 类（当前 FQCN） | `@Test` | 源文件包声明（本轮实核） | Compose 宿主 |
|---|---|---|---|
| `com.creationreadingassistant.ui.screen.ReaderScreenTest` | 37 | `package com.creationreadingassistant.ui.screen` | `createComposeRule()`（源 `:75`） |
| `com.creationreadingassistant.ui.screen.stats.components.DurationUnitToggleEntryTest` | 10 | `package …ui.screen.stats.components`（**文件目录却在 `stats/`**，F-6 未修） | `createComposeRule()`（源 `:62`） |
| `com.creationreadingassistant.ui.screen.shelf.DurationPillGestureTest` | 7 | `package …ui.screen.shelf` | `createComposeRule()`（源 `:55`） |
| `com.creationreadingassistant.ui.screen.readinghistory.MyReadingTimelineRowGestureTest` | 8 | `package …ui.screen.readinghistory` | `createComposeRule()`（源 `:54`） |
| **合计** | **62** | — | — |

- 四个类**均已编译进 qahost androidTest 产物**：`E:\creation-reading-assistant\codex-s04-build-20260925\project-builds\app\intermediates\classes\qahostAndroidTest\transformQahostAndroidTestClassesWithAsm\dirs\…` 下存在 `ReaderScreenTest.class`、`…\stats\components\DurationUnitToggleEntryTest.class`、`…\shelf\DurationPillGestureTest.class`、`…\readinghistory\MyReadingTimelineRowGestureTest.class`，四个顶层 class 的 mtime **均为 2026-09-27 15:47:46**。
- 与 9/22 报告「62 条（37+10+7+8）0 完成」的**用例数逐类一致**。

### 2.2 历史启动阻塞的真实范围（仅 MIUI 实体手机，勿外推）

- 出处：[9/22 S09-B+S15 联合验收](../../../qa/2026-09/2026-09-22-s09b-s15-duration-gesture-device-acceptance.md) §1、§4；[Compose 宿主调查](../../../qa/2026-09/2026-09-24-compose-test-host-investigation.md) §0。
- 事实：设备 `c49ac6cf`（实体机，Android 15 / SDK 35 / **MIUI V816**，非模拟器）；四类各 `started` 后 0 完成（`124` 超时）；logcat 原始证据为 `ActivityTaskManager: Abort background activity starts from 10949` + `… cmp=com.creationreadingassistant/androidx.activity.ComponentActivity … result code=102`；宿主调查 §0 =「**暂无可信绕过方案**」，且该报告的适用对象是**该台 MIUI 实体机**。
- **边界澄清（本轮明确）**：该阻塞**属 MIUI 实体机策略层**；报告 §0 亦自述「**不断言** `result code=102` 是 AOSP 标准码还是厂商自定义码（记为 P1）」。**故不得把「MIUI 实机启动失败」直接套到当前 MuMu**。
- **另需注意**：9/22 用的通道是**主包 `com.creationreadingassistant` + `androidx.test.runner.AndroidJUnitRunner`**；当前 qahost 通道的**包名与 runner 都已不同**（见 §2.3），因此 9/22 的失败**不可直接外推为「这 62 条在 qahost 上也跑不了」**。

### 2.3 MuMu qahost 通道与包名 / runner 条件（本轮实核）

| 维度 | 值 | 证据 |
|---|---|---|
| 设备 | MuMu 模拟器，`127.0.0.1:16416`；重启后枚举名 `emulator-5556`（同一实例）；Android 12 / **x86_64**（非 MIUI） | S06 MuMu 验收 §1；S04 MuMu 隔离报告 |
| 宿主包 | `com.creationreadingassistant.qahost` | `android/app/build.gradle.kts:112 applicationIdSuffix = ".qahost"` |
| 测试包 | `com.creationreadingassistant.qahost.test` | 同上 + 09-27 运行命令实载 |
| InstrumentationRunner | `com.creationreadingassistant.testing.CraIsolatedTestRunner` | `build.gradle.kts:32 testInstrumentationRunner = "…CraIsolatedTestRunner"`（**已是默认 runner**） |
| Compose 宿主 Manifest 条件 | qahost 变体**单独**补 `androidx.compose.ui.test.manifest`（缺它 Compose 测试无法在沙箱宿主内启动 `ComponentActivity`） | `build.gradle.kts:243-245 add("qahostImplementation", libs.androidx.compose.ui.test.manifest)` |
| 运行命令模板（三处 package/runner 必需） | `adb -s <serial> shell am instrument -w -r -e class "<真实 FQCN>" com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner` | S04 MuMu 隔离报告 §本轮实测 2；S06 MuMu 验收 §3 |

### 2.4 关键佐证：**同一 APK** 已承载 12 项 Compose 测试在 MuMu 上通过

这是把「62 条可在 MuMu 跑」从推测变为**有可回读锚点**的证据链：

1. 门禁 APK：`E:\creation-reading-assistant\codex-s04-gate-20260927\app-qahost-20260927-gate.apk`（host，SHA-256 `A4669F8532055656BE6341444F29EAF3A972678533E5CEB351FF34E40021A638`）与 `app-qahost-androidTest-20260927-gate.apk`（test，SHA-256 `6D19C24449D75F893C20FEF947F165FD005F08733187AD1F3E5C5662C1456EC7`，1 521 472 B，mtime 2026-09-27 15:47:59）。
2. 该 **test APK 与 4 个目标类同源同批**：目标类 class 文件 mtime **15:47:46**，test APK mtime **15:47:59**；`project-builds` 内 `app-debug.apk`（SHA `762A6F681B4D82A7167C77DA7DCB8F13ABFEB9B716388FAE2AF2EE15D3A082D5`）与门禁目录 `app-debug-20260927-gate.apk` **逐位一致**，证明门禁目录 APK 即由该构建产出。
3. S06 MuMu 验收用**同一 test APK**（`6d19c244…`）+ qahost runner 跑 `PageTurnerHighSpeedTest`（8）+ `CraSliderRtlRenderTest`（4）⇒ **`OK (12 tests)`、ADB exit 0、Time 7.317、无失败栈**。原始日志：`E:\creation-reading-assistant\codex-s06-mumu-20260927\04-instrument-12.log`（8 739 B，mtime 2026-09-27 23:42:31），退出码 `04-instrument-exit.txt`。该结论已由 Codex 总指挥复核（S06 MuMu 验收 §9，独立回读日志与 APK 哈希）。
4. 这 12 项与目标 62 条**共用同一 Compose 宿主机制**：`createComposeRule()` → `androidx.activity.ComponentActivity`（[宿主调查](../../../qa/2026-09/2026-09-24-compose-test-host-investigation.md) §1.1）。
5. 另有非 UI 的 `EpubReplaceRoomIsolatedTest` **8/8** 在 MuMu qahost 通道通过：`E:\creation-reading-assistant\codex-s04-mumu-20260927\epub-room-instrumentation.log`（6 126 B，mtime 2026-09-27 16:48:29），`OK (8 tests)` / `INSTRUMENTATION_CODE: -1` / ADB exit 0（S04 MuMu 隔离报告 §本轮实测 2）。

⇒ **「MuMu 无法运行 Compose instrumentation」已被证伪**；对**这 4 个类**仍属**未运行**（需批次 1 实跑才可写结果）。

### 2.5 给总指挥的裁决建议（三选一映射到当前约束）

对账报告 V5 给的三选项是「换真机 / CI / 接受本机不可验并改 JVM 替代门禁」。**结合用户明确「无法使用实体手机、已允许 MuMu」**，本轮建议如下（**仅为建议，未执行**）：

| 选项 | 当前可行性 | 本轮建议 |
|---|---|---|
| A. 换一台不受策略约束的**实体手机** | **当前不可用**（用户无实体手机） | **暂不选**；列为 SR-ENV-1 残余，若将来有设备再执行。 |
| B. **CI**（Gradle Managed Device / Firebase Test Lab）作替代门禁 | 可作**补充**，但**联网/新建设施**，且**不等于真机** | 暂不选为主路径；可作为后续 D 阶段影子门禁备选。 |
| C. **接受本机（MuMu）为设备通道**，JVM/Robolectric 作补充 | **最贴合当前约束** | **建议选 C 的限定版**：把 62 条的**执行**放到 **MuMu qahost 通道**（限定 PASS，不称真机）；**实体手机/MIUI 行为**作为 SR-ENV-1 残余边界，需总指挥/用户**书面认可**「MuMu 限定 ≠ 真机验收」。 |

**关键纪律**：无论选哪支，**62 条在未实际运行前不得写 PASS，也不得写 FAIL**；已 PASS 的 S06 12 项**不重跑**（类清单与 62 条零交集）。

### 2.6 最小设备批次 / 唯一所有者 / 停止条件（建议，未执行）

> 复用 [S09 分诊](../../legacy-stages/2026-09/2026-09-28-s09-ui-suite-triage.md) §4 的批次划分；此处只补「所有者 + 停止条件」。

**批次 1（必做，一次定向运行）——现有 62 条原样跑**

- 前置：qahost + qahost.test 在装、正式包不存在；复用 09-27 gate APK（host `a4669f85…` / test `6d19c244…`）或按需重建；运行前复核源码新鲜度（`android/app/src` 无 `.kt` 晚于 test APK 构建时刻）。
- 命令（**注意真实 FQCN**，`DurationUnitToggleEntryTest` 含 `stats.components`）：
  ```bash
  adb -s <serial> shell am instrument -w -r -e class \
  "com.creationreadingassistant.ui.screen.ReaderScreenTest,\
com.creationreadingassistant.ui.screen.stats.components.DurationUnitToggleEntryTest,\
com.creationreadingassistant.ui.screen.shelf.DurationPillGestureTest,\
com.creationreadingassistant.ui.screen.readinghistory.MyReadingTimelineRowGestureTest" \
  com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
  ```
- 成功判据：`numtests=62`、`OK (62 tests)`、ADB exit 0；**逐项落盘**（原始 log 复制到 E 盘并登记哈希）。
- **停止条件（命中即停，不盲跑）**：
  1. logcat 出现 `Abort background activity starts` / `result code=102` ⇒ **通道回归**，立即停止并回退 §2.5 裁决（不再重复同一命令）；把 MuMu 与真机行为差异记入未验证项。
  2. `ClassNotFoundException` ⇒ FQCN/目录-包不一致（F-6）问题，改用真实 FQCN 后**最多再试 1 次**；仍失败则按环境问题登记。
  3. 出现**有界超时**（建议 `am instrument` 外层加超时，单类不超过 300 s），超时即停，抓**未截断** logcat，记 BLOCKED。
  4. 断言红（AssertionError）⇒ **保留原始栈**，按分诊 §3 归 R3（分层错位）/R4（陈旧覆盖）/产品候选，**不并入「未运行」**。
  5. 全程不打开任何正式藏书；结束时恢复设备基线（密度/设置）并逐项回读（沿用 S06 §5 恢复口径）。

**批次 2（硬件键→队列接线）**：设备动作、**无需新测试代码**；纯手工 + uiautomator dump（分诊 §4 批次 2）。

**批次 3（点按全链 + F-1 复核）**：设备动作；生产渲染路径 + F-1 候选归档（分诊 §4 批次 3）。滚轮无 adb 注入通道，记 **UNPROVEN**。

**唯一所有者**

| 资产 | 所有者 |
|---|---|
| MuMu / ADB（批次 1/2/3 的设备动作） | **唯一 MuMu 设备窗口**（与 V1/V2 同批**串行**，不并行；不与在途书架筛选窗口争文件） |
| Gradle（如需重建 qahost/androidTest APK） | **唯一 Gradle 所有者**（Round 1 前置串行构建；MuMu 动作不跑 Gradle） |
| 产物与报告（E 盘工件 + qa 报告） | 设备窗口独占写；他人只读 |

**批次 1 之后不应继续追加同一命令的重复轮次**；若需批次 2/3，须由总指挥在批次 1 结果基础上另行发放。

### 2.7 V5 未验证项

1. **62 条从未运行**（历史 0 完成；本窗口未运行）——结果未知，**不写 PASS/FAIL**。
2. **这 4 个类在 MuMu qahost 通道上的实际可跑性未实测**（§2.4 的 12 项/8 项为**同机制旁证**，非这 4 类的运行结果）。
3. **实体手机 / MIUI 行为**：`result code=102` 语义（P1）、该拦截是 MIUI 专有还是 AOSP Android 14+ BAL（P2）—— 均未裁决；MuMu 镜像结论不可外推其他 ROM（S06 §9 亦如此声明）。
4. **320dp 受控宽度自动化证据**（9/22 §7 记为「未获得」）：落在 `ReaderScreenTest` 组 B，**须批次 1 实跑**方可产生。
5. **F-6（目录/包不一致）未修**：`DurationUnitToggleEntryTest` 目录 `stats/` 与包 `…stats.components` 不一致，按路径推 FQCN 会 `ClassNotFoundException`；**测试 owner 侧待办，本窗口不动**。
6. **F-2（完读卡时长 pill 取数口径）**：产品决策点；`DurationPillGestureTest` 用夹具（分诊 §1），**不等于真机该字段有值**。
7. 批次 2（硬件键接线）/批次 3（生产点按全链）**均无任何设备证据**，属验证缺口。
8. 滚轮注入**无 adb 通道** ⇒ UNPROVEN。

---

## 3. 本轮汇总：未验证项清单（供总指挥归类）

| 编号 | 项 | 状态 |
|---|---|---|
| U1 | S04 `EpubRepositoryReplaceTest` 21/21「当前源码」绑定（报告哈希那次 XML 已被覆盖） | **不可回读原始工件**；待 D 冻结构建当场重跑+登记 |
| U2 | S04 回滚设备路径（真机/低存储/并发/`delete()` false） | 未验证（报告自述） |
| U3 | 62 条 UI 自动化（4 类）运行结果 | **未运行** |
| U4 | `result code=102` 语义 & 拦截归属（MIUI vs AOSP BAL） | 未裁决 |
| U5 | 320dp 受控宽度自动化 | 未获得（待批次 1） |
| U6 | 硬件键→队列接线、生产点按全链 | 无设备证据 |
| U7 | 滚轮注入 | UNPROVEN（无通道） |

---

## 4. 边界声明

- **未修改**任何 `.kt` / Manifest / Gradle / `libs.versions.toml` / 脚本 / 账本 / 他人报告正文；**唯一写入 = 本文件**。
- **未运行** Gradle、ADB、MuMu；**未接**实体手机；**未改**设备设置、系统语言与密度；**未触碰**正式书库与在途窗口资产（书架筛选测试、`EpubRepository.kt`/`ShelfImporter.kt` 共享 owner、`ProfileViewModel.kt` 等）。
- **未执行** `reset / checkout / clean / stash / stage / commit / push`；未进入原仓库。
- 本文只引用**引用的证据路径与脱敏摘要**（时间戳、哈希、字节数、类名/包名、中性样本名），**不含真实书名、私有目录名或日志原文**。
- **百分比**：本文**不调整**任何全局口径（A–E 维持 `2/5=40%`，S04 五关 `4/5=80%`），C 是否退出由总指挥按退出检查单判定。
- 本文 §1.4、§2.5、§2.6 的批次/裁决/所有者均为**给总指挥的建议，未执行**。落盘即停，交总指挥复核。
