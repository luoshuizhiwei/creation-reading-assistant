# Stage D 独立只读审查（2026-09-28）

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`；HEAD `5aed5939600e491843e3c44453921bd6273a233c`。
- 性质：**独立只读审查**，与 DeepSeek 的构建 / MuMu 执行**并行**；本窗口**不是** Gradle 或设备所有者。唯一写入 = 本文件。未改产品源码、测试、配置、Gradle 或 DeepSeek 报告；**未运行 Gradle / ADB / MuMu**；未触碰原仓库、实体手机、正式书库；未 commit/push/reset/checkout。
- 输入：`AGENTS.md`、[交接入口](../../../handoff/current.md)、[C 退出复核](2026-09-28-stage-c-exit-review.md)、[C 剩余项对账](2026-09-28-stage-c-remaining-work-audit.md)、[V4/V5 决策证据](../../decisions/2026-09/2026-09-28-stage-c-v4-v5-decision-evidence.md)、[S12 连续链路补验](../../../qa/2026-09/2026-09-28-s12-mumu-continuous-chain-verification.md)、[S14 隔离设备核验](../../../qa/2026-09/2026-09-28-s14-mumu-isolated-residue-verification.md)、[S14 journal 返修](../../legacy-stages/2026-09/2026-09-28-s14-journal-kill-window-fix.md)、[S05 MuMu 验收](../../../qa/2026-09/2026-09-28-s05-mumu-cross-page-finished-acceptance.md)、当前源码与 E 盘工件。
- 口径：**不预判任何尚未交付的门禁结果**。DeepSeek 的 D 门禁产出未到时一律 **NOT RUN / 待复核**。用户已授权 MuMu 作为当前验证通道，旧文档「需再批准 MuMu」不作为阻塞（但 MuMu 结果只能称**模拟器范围**）。
- 哈希一律**本轮实算**，不抄报告自报值。

## 0. 结论先行

1. **退出复核引用的源码、测试入口、原始 XML 与 E 盘证据逐项对应**，其中 4 处关键哈希本轮实算复现（S14 设备 XML、S14 JVM 红/绿 XML、V4 EPUB XML、`LocalZipBackup.kt` 源码），无一项是「报告说有、E 盘没有」。发现的 2 处问题属**文档口径**而非证据缺失（§4）。
2. **V5 62 条核实成立**：`37+10+7+8=62`、四个真实 FQCN、runner、qahost 宿主条件全部与源码一致；**当前 NOT RUN**（E 盘与构建产物中只有 `.class`，无任何运行 XML/日志）。D 门禁有三个**可提前规避**的坑：① 不带 `-e class` 过滤的 connected 任务会跑**全仓 ~219 条** androidTest（36 个类），而非 62；② 默认 `connectedQahostAndroidTest` **跑完卸载应用**，会清掉 33 本 qahost 验收书库；③ `DurationUnitToggleEntryTest` 目录 `stats/` 与包 `stats.components` 不一致（F-6），按路径推 FQCN 必 `ClassNotFoundException`。
3. **五条结论边界全部成立且应保留**：V4 的 21/21 非原哈希工件（共享 junction 目录，易被并发覆盖）；S12 主 DB 只到**行级 + 文件级**（SHA 已变）；S14 **受控残留 ≠ 真实进程 kill**，且**整包 DB+文件非原子**；S05 三项 UI 子项 NOT RUN。
4. 给出**按风险排序的最小 D 验收矩阵**（§3）：R1 V5 62 条 → R2 S14 原子性/kill → R3 S12 逐字节 → R4 V4 重新锚定 → R5 S05 三项。

## 1. 证据核对表

| 退出复核引用 | 本轮核对 | 分类 |
|---|---|---|
| S05 八套件 JVM 40/40；MuMu 420 dpi 七站截图 + 同书 DB + 36 文件哈希 | 报告与 E 盘 `zcode-s05-mumu-crosspage-20260928\` 存在；报告 §三/§五 有 before/after 清单与 0 差异结论 | **已实跑**（JVM 数未复跑，引用）；七站为**模拟器限定** |
| S12 原始生产导入日志 `OK (1 test)` + 逐阶段 sqlite3 | E 盘 `s12/db/01..05`、`logs/01-02`、`seed/`（含 seed 源码 / 受控 ZIP / 5 000 002 B 正文）均在；实读 `01-after-import` = `reading / 99.4000015258789 / legado_v1`，`03-after-move-exit` = `locator_v2` | **已实跑**（本轮实读文件，未复算链路） |
| S14 源码 SHA `ab78a16e…`；W5/W6 JVM 红 34/2→绿 34/0；MuMu UTP XML 11/0/0/0 SHA `6de0c921…`；宿主/设备 APK 同轮一致 | 源码实算 `ab78a16e…` ✓；E 盘绿 `TEST-localzipbackup-green.xml` 实算 `8f891e4a…`（tests=34 / fail=0 / err=0）、红 `665bce4b…`（tests=34 / fail=2）；设备 XML 实算 `6de0c921…`（tests=11 / fail=0 / err=0）；`S14ResidueRecoveryDeviceTest` 5 @Test、`LocalZipBackupPersistentRestoreTest` 6 @Test（=11） | **已实跑**；XML 哈希**逐位复现** |
| S04 V4 后一次 XML 21/0/0/0 SHA `75fa140c…` | 实算 `75fa140c…` ✓，`tests=21 / fail=0 / err=0`，`timestamp=2026-09-27T07:16:30`（+08 15:16:30） | **已实跑**（另一次运行，**非**原门禁哈希） |
| V5 62 条在 qahost 测试产物范围内 | `qahostAndroidTest` 产物含四类 `.class`；源码 @Test = 37/10/7/8；**无运行 XML/日志** | **尚未运行（NOT RUN）** |
| S14 「Android 保护性分支不可达」 | 报告 §5.2 自述为 POSIX rename 覆盖语义；实为**本次正常路径未触发**，非「所有异常环境不可达」（EXDEV/ENOSPC/EACCES 仍可致 rename 失败） | **静态确认 + 1 次实测**，结论应收窄 |

**关键源码实算**（供 D 冻结锚定）：

| 文件 | SHA-256 | mtime |
|---|---|---|
| `.../feature/sync/LocalZipBackup.kt` | `ab78a16e8e34c50a59ada31040278569baaa013f2c1eba6054a675b89997514c` | 09-28 21:32 |
| `.../feature/sync/LocalZipBackupTest.kt` | `174cfbf6cbd3ff35e182efc4b956ff46d72b4ded20ae7cc910bfb01cc29a690a`（E 索引登记值一致） | 09-28 21:11 |
| `.../feature/reader/EpubRepository.kt` | `4adb16e3181eec2a957d19f6a8e0fc743acf0d9c4f2a11d3e261a5f9f438dbb8` | 09-28 00:59 |
| `.../feature/reader/EpubRepositoryReplaceTest.kt`（21 @Test） | `6b254a751589598f66836d3d4401c38d8532b296e06c04c9185e7ebb2da03dc0` | 09-27 11:20 |
| `androidTest/.../sync/LocalZipBackupPersistentRestoreTest.kt`（6 @Test） | `ede20f8022ab67bd67c74c5885ca57484c2bf67d4ee41007a2637fad6d078bf0` | 09-28 01:57 |

## 2. D 门禁方案逐项审查

### 2.1 V5 — 62 条 UI 自动化（最高风险）

**核实成立**：

| 类（真实 FQCN） | @Test | 宿主 |
|---|---|---|
| `com.creationreadingassistant.ui.screen.ReaderScreenTest` | 37 | `createComposeRule()`（源 `:75`） |
| `com.creationreadingassistant.ui.screen.stats.components.DurationUnitToggleEntryTest` | 10 | `createComposeRule()`（源 `:62`） |
| `com.creationreadingassistant.ui.screen.shelf.DurationPillGestureTest` | 7 | `createComposeRule()`（源 `:55`） |
| `com.creationreadingassistant.ui.screen.readinghistory.MyReadingTimelineRowGestureTest` | 8 | `createComposeRule()`（源 `:54`） |
| **合计** | **62** | 四类**均无** `createAndroidComposeRule<MainActivity>()` / Hilt 依赖 |

- runner：`app/build.gradle.kts:32 testInstrumentationRunner = "com.creationreadingassistant.testing.CraIsolatedTestRunner"`（**已是默认**）；qahost 变体 `applicationIdSuffix=".qahost"`（`:112`）⇒ 测试包 `com.creationreadingassistant.qahost.test`；Compose 宿主 Manifest 由 `add("qahostImplementation", …compose.ui.test.manifest)`（`:245`）单独补入。
- `CraIsolatedTestRunner` 只替换 `Application`（`CraIsolatedTestApplication`），**不重定向 `filesDir`**（源码已核）——因此 qahost 测试写的是 **qahost 包**私有目录，不触碰正式包；但**会写 qahost 自己的库**。

**三个必须规避的坑**：

1. **过滤是强制的**：`android/app/src/androidTest` 共 **36 个测试文件 / 219 个 @Test**。不带 `-e class` 直接跑 connected 任务，会执行全部 ~219 条（含未在设备上验证过的类），既超出 62 条口径，又大幅提高挂起/误红概率。
2. **卸载风险**：默认 `connectedQahostAndroidTest` 在跑完后**卸载应用**；[S14 报告](../../../qa/2026-09/2026-09-28-s14-mumu-isolated-residue-verification.md) §2 已实测「会毁掉 33 本验收书库」，其规避方式为 `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`。这是**真实数据风险**，D 门禁必须先冷备 qahost 库（DB/WAL/SHM + `files/books`）再跑，跑后回读哈希。
3. **F-6 FQCN**：`DurationUnitToggleEntryTest.kt` 位于 `stats/` 目录但 `package …ui.screen.stats.components`；命令里必须写**真实 FQCN**（含 `stats.components`），否则 `ClassNotFoundException`。这是**测试 owner 侧待办**，D 不应顺手改目录（会动他人所有权）。

**结论边界**：这 62 条即使全绿，也只是 **MuMu 模拟器 + 隔离夹具**范围；四类多为注入 fake 的组件级测试（如 `DurationUnitToggleEntryTest` 用替身 DataStore），**不等于生产 MainActivity 接线**，也不等于实体手机/MIUI。旁证的 12 项（S06 快翻/RTL）**类清单与 62 条零交集**，不得计入。

### 2.2 V4 — EPUB 回滚重新锚定

- 现状：唯一一份 `EpubRepositoryReplaceTest` XML 位于 **`D:\develop\Code\Codex\cra-s04-buildlink\…`（实为 JUNCTION → `E:\creation-reading-assistant\codex-s04-build-20260925\project-builds`）**，是**共享构建目录**，任何并发的定向运行都会覆盖它。当前 XML（`75fa140c…`，21/21）来自 09-27 15:16 的**另一次**运行，非门禁报告声明的 `3b0730ae…`。
- 源码形态与报告一致（`backupReady/promoteStarted` 分流 `:143-144`、`restoreBackupAfterFailedPromotion :254`、入口守卫 `:142/:206-211`）——**静态确认通过**。
- **D 重新锚定所需证据（必须同一批、同一构建目录内产生）**：① 冻结源码 SHA（`4adb16e3…`）；② 测试 SHA（`6b254a75…`）与 `@Test` 计数 21；③ 运行命令 + 退出码；④ XML **与** 运行日志的 SHA-256；⑤ 运行时刻与源码/测试 mtime 的先后关系。**建议用独立构建目录**（不要复用 `cra-s04-buildlink`），或运行后**立即**把 XML+日志复制到专属 E 盘目录并登记哈希。
- 未验范围照旧：真机/低存储/跨卷 `renameTo=false`/并发/`delete()` 返回 false。

### 2.3 S05 — 三项未覆盖 UI 子项

| 子项 | 状态 | 备注 |
|---|---|---|
| 真实读到书末由 merge 自动置 `finished` | **NOT RUN** | 需驱动 169 章翻到末页；JVM 侧已锁 merge 契约，设备侧可选 |
| 首页「已阅读完成」横排卡片**逐卡**目视 | **NOT RUN（卡级）** | 该区计数（8→7）已验证，仅横排卡位未逐张看 |
| 取消读完后再次真实阅读 `percent` **回落自愈** | **NOT RUN（UI）** | DB 语义已由 JVM 锁定，设备端未复开阅读器验证回落 |

三项都属**覆盖缺口（非确证故障）**；成本低（截图/一次翻页），适合并入 D 的 MuMu 批。实体手机与夜间模式下的这些页面**不在** MuMu 可证范围。

### 2.4 S12 — 主 DB 哈希变化与结论边界

- 基线 `d938c7b8…c71214b` → 清理后 `7bd076c6…8499a28`，**文件大小相同**（1 138 335 744 B）但 **SHA 不同**。根因合理：SQLite 删除行后页被标记可复用、内部布局改变，逻辑一致也不可字节还原。
- 因此本链路**只能**声称：行数还原（33/16/21/0）、`files/books` 36 个既有文件逐字节未变、`shared_prefs` 7 项与 `datastore` 6 项逐字节未变。**不得**写「完全恢复 / 逐字节还原」。
- **未逐行核对既有 33 本书的每一列**（只核计数与文件哈希）。若 D 需要更强结论，须在导入前 dump 全表、事后逐行 diff——这是一次额外的隔离批，成本中等。
- 覆盖范围仅「单章 TXT + 越界 Legado marker」；EPUB 书、多章可换算分支**未采**。

### 2.5 S14 — 受控残留 vs 真实 kill、整包原子性

- **受控残留 ≠ 真实进程 kill**：本轮 5 项残留是**人为植入状态 + 驱动 `importZip` 恢复侧**；无 `kill -9` 注入。W5/W6 那类状态在 Android 上**不可自然到达**（POSIX `renameTo` 原子覆盖，正常路径不进保护性 backup 分支）。因此正确口径是「**恢复侧对这些状态的收敛已验；其在 Android 上的自然可达性未证明**」。
- **整包 DB + 文件非原子**：`importZip` 先提交 DB 事务、后复制文件（`copyBookFiles`）。若在两者之间被杀，DB 比文件新。这是**已知边界、非本轮回归**，但**仍未修复也未验证**——D 不应把它写作已解决。
- 「Android 上不可达」应改述为「**本次正常路径未触发**」：`renameTo` 仍可因 EXDEV（跨卷）/ENOSPC/EACCES 失败。
- 真实 kill 若要验，需**故障注入接缝**强制进入保护性分支后再杀进程（见 §3 R2，属新增实现，需所有权与总指挥批准）。

## 3. 按风险排序的最小 D 验收矩阵

> 全部命令用 `adb -s <serial>` 显式指定；每批前后回读设备基线（density/size/rotation/夜间/`stay_on`）与 qahost DB/WAL/SHM + `files/books` 哈希。**任一原件未落盘即该条不成立。**

| # | 风险 | 项 | 前置 | 判据 | 证据要求 |
|---|---|---|---|---|---|
| **R1** | 高（未运行 + 卸载清库） | **V5 62 条** | qahost 库冷备；源码/测试 APK 新鲜度（无 `.kt` 晚于 APK） | `numtests=62`、`OK (62 tests)`、ADB exit 0；逐项 PASS/FAIL/BLOCKED | 原始 stdout 或 UTP XML + 哈希；跑后 qahost DB/WAL/SHM 与 36 文件哈希回读 |
| **R2** | 高（数据完整性未证） | **S14 整包原子性 + 真实 kill** | 决策：接受边界 or 建故障注入接缝 | 若接受：登记「DB 先于文件、中间态未覆盖」；若注入：kill 后重启导入收敛到一致 | 决策登记或注入日志 + 前后字节哈希 |
| **R3** | 中（结论边界） | **S12 主 DB 逐字节 / 逐行** | 决策：接受行级+文件级 or 另开全表 dump 批 | 逐行 diff = 0 或明确登记「只到行级」 | 全表 dump 前后件（若做）；否则登记边界 |
| **R4** | 中（证据可追溯） | **V4 EPUB 重新锚定** | 独立构建目录 | XML 21/0/0/0 且**当场**登记哈希 + 日志哈希 + 源码/测试 mtime | `EpubRepositoryReplaceTest` XML + 运行日志 + SHA 三件套 |
| **R5** | 低（覆盖缺口） | **S05 三项 UI** | 复用 R1 同批 MuMu | 每项截图/UI XML **且**关联同书 DB 前后值 | 截图 + XML + DB 查询输出 |

**具体命令建议**（本窗口未执行；DeepSeek 执行前须自行核对 serial 与任务名）：

V5 首选（不产 XML、**不触发卸载**）：
```bash
adb -s <serial> shell am instrument -w -r -e class \
"com.creationreadingassistant.ui.screen.ReaderScreenTest,com.creationreadingassistant.ui.screen.stats.components.DurationUnitToggleEntryTest,com.creationreadingassistant.ui.screen.shelf.DurationPillGestureTest,com.creationreadingassistant.ui.screen.readinghistory.MyReadingTimelineRowGestureTest" \
com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
```

V5 备选（产真实 JUnit XML；**必须**加 `leaveApksInstalledAfterRun`，否则卸载清库）：
```bash
./gradlew :app:connectedQahostAndroidTest -PcraAndroidTestBuildType=qahost \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.creationreadingassistant.ui.screen.ReaderScreenTest,com.creationreadingassistant.ui.screen.stats.components.DurationUnitToggleEntryTest,com.creationreadingassistant.ui.screen.shelf.DurationPillGestureTest,com.creationreadingassistant.ui.screen.readinghistory.MyReadingTimelineRowGestureTest \
  --console=plain
```

**V5 停止条件（命中即停，不重复同一命令）**：① logcat 出现 `Abort background activity starts` / `result code=102` ⇒ 通道回归，停并上报；② `ClassNotFoundException` ⇒ 先核 F-6 FQCN，最多再试 1 次；③ 单类超 300 s 即停，抓**未截断** logcat 记 BLOCKED；④ 断言红保留原始栈，按 R3/R4/产品候选分类，**不并入「未运行」**。

## 4. 需要更正的文档/证据问题（交总指挥定所有权）

| # | 文件 | 触发条件 | 证据 | 建议所有权 |
|---|---|---|---|---|
| C1 | [`2026-09-28-s14-mumu-isolated-residue-verification.md`](../../../qa/2026-09/2026-09-28-s14-mumu-isolated-residue-verification.md) 标题与 §2 标题仍写「**真机**」 | 任何人引用该报告时会把 MuMu 结果误当实体手机 | 报告 §0 表 `设备 = MuMu emulator-5556`；顶部 Codex 复核已注明应读作模拟器 | 报告作者 / 总指挥 |
| C2 | `AGENTS.md:84`「不使用 MuMu 模拟器」 | 与用户当前授权冲突，易被当作「需再批准 MuMu」的阻塞 | 用户已多次授权 MuMu 为当前验证通道 | AGENTS.md 集成者（共享文档） |
| C3 | V4 XML 位于共享 junction 构建目录 | 并发定向运行会覆盖，哈希不可追溯 | `cra-s04-buildlink` = JUNCTION → E 盘；当前 XML 非原门禁哈希 | D 冻结流程（用独立目录 / 立即固化） |
| C4 | [`S12 补验`](../../../qa/2026-09/2026-09-28-s12-mumu-continuous-chain-verification.md) 证据 `db/01-after-import.txt` 含一行 `Error: unrecognized token: "" content"` | 引号转义错误致 `book_content` 计数查询失败（其后重查成功） | 本轮实读该文件 | 报告作者（重采时修正，非阻塞） |
| C5 | F-6：`DurationUnitToggleEntryTest` 目录 `stats/` ≠ 包 `stats.components` | 按路径推 FQCN 会 `ClassNotFoundException` | 源码 `package` 声明与文件路径 | 测试 owner（D 不顺手改） |

> 以上均为**口径/可追溯性**问题，不改变任何已验结论；C1/C2 建议在 D 冻结前处理，C3 已纳入 R4，C4/C5 记入待办。

## 5. 边界声明

- 本窗口**未运行** Gradle / ADB / MuMu；所有「已实跑」判断均来自**可回读的 E 盘 XML/日志/DB 输出**，关键哈希本轮**实算复现**；「未运行/未交付」一律不写 PASS。
- **不预判** DeepSeek 尚未产出的 D 门禁结果：V5 62 条、S14 注入/原子性、S12 逐字节、V4 重新锚定、S05 三项均为 **NOT RUN / 待复核**。
- 未触碰原仓库、实体手机、正式书库；未改任何产品源码、测试、配置或 DeepSeek 报告；未 commit/push/reset/checkout。
- MuMu 结论一律标**模拟器范围**；实体手机/MIUI 行为保留为残余边界。
- 落盘即停，交总指挥。
