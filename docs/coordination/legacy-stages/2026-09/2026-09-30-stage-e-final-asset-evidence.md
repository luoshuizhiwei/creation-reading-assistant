# 阶段 E：剩余资产语义证据与最终清单（2026-09-30）

> **窗口**：阶段 E 剩余资产语义证据 + 65 项逐行证据 + 最终冻结清单（只读审计 + 可复算对账）。
> **共享恢复仓库（只读入口）**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`。
> **HEAD 核对**：`513891d6a66a9b78e072ba759d34ed580d377ecf`；`git diff --cached --stat` 无输出（暂存区空）。
> **原目录（只读对照）**：`D:\develop\Code\Codex\creation-reading-assistant`（仅用于逐文件内容比较，未改动、未切换）。
> **E 盘独占输出**：`E:\creation-reading-assistant\stage-e-asset-evidence-final-20260930\`（脚本 + CSV + 逐文件 diff，全部新建）。
> **唯一可写共享文件**：本报告。
> **纪律**：**未**改任何产品源码 / 测试 / 配置 / CSV / 其他窗口报告 / READY；**未**构建（无 Gradle 调用）；**未**操作设备 / MuMu / ADB；**未** stage / commit / push / reset / checkout / stash；**未**搬移或删除目录；**未**触碰原仓库、正式包、正式书库、其他窗口目录（`stage-e-final-joint-*`、`stage-e-g6-s14-independent-acceptance-*` 等只读）。
> **不重做证据**：不重跑任何既有构建/测试；结论只引用既有原始 XML / CSV / 报告 / APK 哈希，并做可复算集合与哈希核对。

---

## 0. 结论先行

| # | 结论 |
|---|---|
| **1** | **65 待关注集合已定位并可复算**：92 行对账表 = 27 已确认覆盖 + **65 待关注**；`in_65_set=Y` 的 65 行与 92 表派生的 65 集合**完全相等**（脚本判定 `True`）。67 行映射表中的另 2 行（`ProfileUtils.kt`、`DurationDisplay.kt`）在 92 表已属**确认覆盖**，不进 65 集合。 |
| **2** | **10 项「七类摘要未覆盖」逐项核对完成：无一项功能丢失。** 逐文件 diff + 既有 JVM/设备证据显示：4 项为**既有缺陷修复并增强**（59/60/61/62，含 76 项 JVM 专项通过）、2 项为**测试增强**（64/65，无删除用例）、1 项为**构建/测试基建**（57，已随本地提交落地）、1 项为**文案正确性修复**（58）、2 项为**文档更新**（66/67）。 |
| **3** | **「65 项全部为有意架构演进」的笼统表述作废**，替换为 65 行逐行证据 CSV，四状态分布：**已证实保留 18 / 有意行为变化 27 / 待验证 19 / 需产品决定 1**。 |
| **4** | **冻结状态已更新**：G6 行为保留版 **READY + 49/49**；S14+R3 **READY + 52/52**；旧的「F3-B 点击收窄」与「S14-only 25 错误」**是历史状态**，不再派重复修复。设备结果引用窗口 1 报告（复核时**尚未更新**，故设备项保持**待验**，并登记其在飞原始工件）。 |
| **5** | **S06 冷启动证据是普通 Activity 启动路径，不是 instrumentation runner 路径。** 证据：qahost 合并清单 `android:name="com.creationreadingassistant.App"`、`package="com.creationreadingassistant.qahost"`；录屏接触图从 MuMu 桌面 → 应用启动图 → 深色首页。因此**不能**仅因包名 `qahost` 就否定生产 `App.kt` 启动链已被覆盖；残余缺口是 **release 变体与实体手机**，不是「生产 Application 未执行」。 |
| **6** | **仍阻塞 E 退出的最小清单**见 §7（6 条）。另发现一处**冻结范围未覆盖批次**：S05-①、S07、S12 三批既有设备证据的改动**既未列入冻结范围提案，也不在在飞联合候选的 72 文件内**——必须显式声明「纳入」或「延期」，不得无声丢失。 |

**阶段口径**：阶段 E 仍在 **4/5（80%）**。本报告**不**裁决阶段是否退出；后续本地提交仍需用户明确授权（GAP-12）。

---

## 1. 集合定位与路径解析（可复算）

### 1.1 三方集合关系

| 表 | 行数 | SHA-256 |
|---|---:|---|
| `orig-unique-full-reconciliation-corrected.csv`（92 行对账） | 92 | `E223726489155547E49E872AD8E0E3A3E4087919139CB2EFF64BCA1A1A53F34B` |
| `orig-65-to-seven-category-mapping.csv`（原表，实为 67 行） | 67 | `B57CFC2BF28D43115E7CE39BEA618B8A374CB489DE512A80BCBB449BA934710E` |
| `orig-65-set-reconciliation.csv`（冻结账本富化版，含 `in_65_set`） | 67 | `A7EF528F8130664BCCA2EDC73DF33A92C0FAD711F8ED06D8D1FCA9691A09F0A3` |

**实算结果**（`verify_sets.py` 输出，可复算）：

```
92 coverage = {LIKELY_COVERED:23, NEED_REVIEW:42, CONFIRMED_COVERED:21, CONFIRMED_COVERED_MIGRATED:6}
65 集合（LIKELY_COVERED + NEED_REVIEW） = 23 + 42 = 65
27 已确认覆盖（21 + 6）+ 65 待关注 = 92  ✓
富化 65 表 in_65_set=Y = 65 行；与 92 表派生 65 集合相等 = True
原表 67 行中 in_seven_summary=否（七类摘要未覆盖）= 11 行；
  其中落在 65 集合内 = 10 行 → item 57,58,59,60,61,62,64,65,66,67
  余 1 行 = item 63（DurationDisplay.kt，92 表 CONFIRMED_COVERED，不进 65 集合）
```

### 1.2 路径解析与 basename 重名

- **67 行全部解析到唯一真实文件**（`resolved = 67/67`，`N/A = 0`）。
- **缩写路径 2 行**（均为「不在 65 集合」的 extras），用「前缀 + 后缀唯一命中」解析：
  - item 14 `android/app/src/main/java/.../ui/screen/profile/ProfileUtils.kt` → `android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/ProfileUtils.kt`
  - item 63 `android/app/src/main/java/.../ui/util/DurationDisplay.kt` → `android/app/src/main/java/com/creationreadingassistant/ui/util/DurationDisplay.kt`
- **basename 重名 2 行（不得凭同名判同一资产）**：

| item | basename | 恢复目录同名数 | 同名路径 | 本行归属 |
|---|---|---:|---|---|
| 53 | `ShelfScreen.kt` | 2 | `ui/screen/ShelfScreen.kt`；`ui/screen/shelf/ShelfScreen.kt` | 仅 `ui/screen/shelf/ShelfScreen.kt` |
| 57 | `build.gradle.kts` | 3 | `android/build.gradle.kts`；`android/app/build.gradle.kts`；`android/benchmark/build.gradle.kts` | 仅 `android/app/build.gradle.kts` |

  两处重名在两棵树中同名数一致（2/2、3/3），解析后按**完整路径**定位，未按 basename 合并。
- **67 行逐行 SHA 全部与恢复目录不同**（`same_sha=YES` 计 0 行）——与本表「原目录 vs 恢复目录内容差异」的定义自洽。

### 1.3 产出与复算入口（`E:\creation-reading-assistant\stage-e-asset-evidence-final-20260930\`）

| 文件 | 说明 | SHA-256 |
|---|---|---|
| `resolve_paths.py` | 解析 67 行完整路径 + basename 重名检测 + 实算 SHA | `26FDA9E403D6453C4A85117F5874505BD32E5BB4AAFD86073E086E8FA072273B` |
| `resolve-67-paths.csv` | 67 行解析结果（完整路径/两树 SHA/重名） | `BB4F39B9B9454BADF6882202630F2BA07AB342933D978A0ED9B0B61F338DDA2B` |
| `build_evidence_csv.py` | 生成 65 行证据 CSV（含分类与证据引用） | `C34B93A39BC47354F358D114E761F4189E30954FAED849477DF66BD353FC7F3C` |
| **`orig-65-evidence.csv`** | **65 项逐行证据（主交付）** | `DBF3E780AA3BF4A325F3C302EE576D8A56C0167C07DE46BB65D3CA4A22F96150` |
| `orig-67-extras.csv` | 67 行中不在 65 集合的 2 行（含缩写路径解析） | `93C73C2799D1E81593890124071E363FFEC8726BB1079649DA97BFBF4DF789FF` |
| `evidence-xml-counts.csv` | D 阶段门禁 333 份 JVM XML 的逐套件计数 | `608F4C9165145A2ED6CC586B761E45470344353710422A2E954B855E3D587292` |
| `uncovered-10-diff-stats.csv` | 10 项 diff 的 +/- 行数与 SHA | `4098FE167A5432A39C979696DE6F915AFD7B37FC4E79C1A7DF0589B451699572` |
| `diffs/*.diff`（11 份） | 原目录 vs 恢复目录逐文件 unified diff（含 item 63） | 见 `uncovered-10-diff-stats.csv` |
| `verification.txt` | 集合/重名/状态/全部 SHA 的复算输出 | `5FBAAE136CC6595D93F6DFB94CAB66C1041B4A290BE37902FA0A52708113513E` |

复算：`python resolve_paths.py && python build_evidence_csv.py && python verify_sets.py`（只读原目录与共享仓库；输出全部落在本目录）。

---

## 2. 10 项「七类摘要未覆盖」逐项语义证据

### 2.1 汇总表

| item | 文件 | diff(+/-) | 原目录 → 恢复目录的真实差异 | 功能是否保留 | 状态 | 判断 |
|---:|---|---:|---|---|---|---|
| 57 | `android/app/build.gradle.kts` | +20/−4 | 新增 `testBuildType`（按 `craAndroidTestBuildType` 属性）、runner 改 `CraIsolatedTestRunner`、新增 `qahost` 变体与 `qahostImplementation`；版本号 0.5.1/3 | **保留**（产品行为不变；只改测试宿主与版本号） | 有意行为变化 | **已纳入**（本地提交 `209ec8a` + `513891d`） |
| 58 | `ui/screen/home/components/HomeContinueSection.kt` | +2/−1 | 继续阅读卡文案调用由 `formatBookProgressForCard(pct)` 改为 `formatBookProgressForCard(pct, progress?.completion_state)` | **保留**（修「取消读完后仍显示『已读完』」） | 有意行为变化 | **纳入**（S05 批次） |
| 59 | `ui/screen/reader/ReaderContentHostEpubBranch.kt` | +126/−2 | 新增 `epubTextBlockSourceRange`/`epubBlockSourceRange`/`epubBlockHighlightLocalRange` 纯函数 + 持久高亮投影；整块选择提交**同源**映射（原 `onSelect(..., -1, -1)` → 真实源终点） | **保留并增强**（原 `sourceEnd 恒 −1` 缺陷被修） | 已证实保留 | **纳入** |
| 60 | `ui/screen/reader/ReaderContentHostPlainTextBranch.kt` | +218/−1 | 新增 `ScrollSelectionInvalidation` 枚举 + `scrollSelectionInvalidation` 纯函数 + `ScrollSelectionInvalidationGate` 去重门 + 宿主层 `visibleItemsInfo` 观察；dispose 清除改共用 `readingUnitOwnsSelection` 半开区间判据 | **保留并增强**（原「仅 onDispose 失效」缺陷被修） | 已证实保留 | **纳入** |
| 61 | `ui/screen/reader/ReaderProgressEffects.kt` | +215/−2 | 新增 `LegadoMarkerSaveGuard` 武装/锚定/抑制（两处 500ms 防抖与退出落库统一守卫）+ 未换算 marker 首开近似位置提示 | **保留并增强**（S12 进度保护） | 已证实保留 | **纳入** |
| 62 | `ui/screen/reader/sheets/ReaderNotesSheet.kt` | +10/−2 | 回调 `onJumpToBookmark` → `onJumpToNote`，由调用方按 `noteAnnotationType` 分派；修「普通笔记走 BOOKMARK 跳转、同 rawId 跨表跳错记录」 | **保留**（语义修正） | 有意行为变化 | **纳入** |
| 64 | `ui/viewmodel/ShelfImporterTest.kt` | +845/−3 | 新增 26 个用例（txt 修复回滚/残留、内容哈希推进、`refreshFromSource` 索引请求、批量成功集）；**无删除用例** | **保留**（测试增强） | 已证实保留 | **纳入** |
| 65 | `ui/viewmodel/ShelfViewModelTest.kt` | +217/−8 | 新增 5 个用例（来源刷新确认/取消/失败原因保留/无更新直通）；**无删除用例** | **保留**（测试增强） | 已证实保留 | **纳入** |
| 66 | `docs/handoff/current.md` | +80/−1 | 新增 2026-09-28/29/30 各轮复核条目；唯一被替换行是 `am instrument` 的 runner 类名（`AndroidJUnitRunner` → `CraIsolatedTestRunner`） | **保留**（文档） | 已证实保留 | **纳入** |
| 67 | `docs/research/2026-09-19-android-worktree-product-inventory.md` | +53/−11 | 文档由「只读盘点」改为「盘点与旁路恢复」；删除「第一阻塞是 Git 对象不完整 / G0 阻断是网络代理不可用」等已作废结论，改为恢复完成记录 + S01/v15 真实 diff 边界 | **保留**（文档勘误） | 已证实保留 | **纳入**（历史研究文档） |

### 2.2 逐项说明（关键 5 项）

- **57 `build.gradle.kts`（H）**：`git show --stat` 证实该文件同时出现在 `209ec8a`（版本号 `craVersionCode=3` / `craVersionName=0.5.1`）与 `513891d`（`testBuildType`、`CraIsolatedTestRunner`、`qahost` 变体、`qahostImplementation`）两次本地提交中。原目录的 `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` 被**有意**替换为按目标包分支的 `CraIsolatedTestRunner`（`qahost` → 隔离 Application；其他目标包 → 清单声明的生产 `App`）。**残余未验**：普通 debug 透传分支设备 NOT RUN（正式包不安装到测试设备）。
- **59 / 60（H）**：新增符号均有**生产调用点 + 专项 JVM 测试**：`EpubBlockSourceRangeTest` 9/9、`EpubBlockPersistentHighlightTest` 15/15、`ScrollSelectionVisibilityPolicyTest` 20/20、`ReadingUnitSelectionOwnershipTest` 10/10（D 阶段门禁 G1 原始 XML，`failures=0 errors=0 skipped=0`）。这些测试文件在 HEAD **未跟踪**（工作树新增），故 D 阶段全量门禁是本组唯一已执行证据。
- **61（H）**：`ReaderLegadoMarkerSaveGuardTest` 13/13（同上 XML）；另配 androidTest `LegadoMarkerRealImportTemporaryTest`，且有 S12 MuMu 限定设备报告（`docs/qa/2026-09/2026-09-28-s12-mumu-actual-temporary-route-acceptance.md`）。
- **62（H）**：`TocNotesWiringGuardTest` 9/9（同上 XML）；`onJumpToNote` 在 `ReaderSheetHost.kt` 有真实调用点，证明不是孤立重命名。
- **64 / 65（H）**：新增用例**只增不删**（`grep '^-.*fun \`'` 为 0 命中），对应生产 API 均存在（`ShelfImporter` txt 修复链、`refreshFromSource`、`ShelfViewModel` 来源刷新确认）。D 阶段门禁 XML：`ShelfImporterTest` 50/50、`ShelfViewModelTest` 21/21。

> **口径**：10 项中 **0 项功能丢失**；59/60/61/62 是**已可达功能的既有缺陷修复**，按「不得改称新功能延期」的约束必须纳入或明确延期，不得静默 HOLD。

---

## 3. 其余 55 项：引用既有充分证据的准确位置

**证据 token（下表 `evidence_ref` 列）**

| token | 准确位置 |
|---|---|
| `92` | `E:\creation-reading-assistant\codex-e-preflight-20260929\stage-e-recon\orig-unique-full-reconciliation-corrected.csv`（逐行 `orig_only_lines`/`rec_only_lines`/`coverage`/`handling` + 行样本） |
| `D` | `E:\creation-reading-assistant\codex-d-stage-gates-20260928\xml\G1\*.xml`（333 份 JVM XML，2026-09-28 工作树；逐套件计数见 `evidence-xml-counts.csv`） |
| `S05A/B/C` | `docs/coordination/legacy-stages/2026-09/2026-09-28-s05-cancel-finished-state-result.md`；`docs/coordination/legacy-stages/2026-09/2026-09-28-s05-cross-page-finished-consumers-result.md`；`docs/qa/2026-09/2026-09-28-s05-mumu-cross-page-finished-acceptance.md` |
| `B1B2` | `docs/coordination/legacy-stages/2026-09/2026-09-29-stage-e-b1-b2-library-combined-result.md` + `docs/qa/2026-09/2026-09-29-stage-e-b1-b2-library-mumu-acceptance.md`（JVM 167/167；MuMu 11/11；53 文件前后 0 差异） |
| `G6` | `E:\creation-reading-assistant\stage-e-g6-behavior-preserved-20260930\READY.json`（JVM 49/49、host/test APK SHA）+ `docs/coordination/decisions/2026-09/2026-09-30-stage-e-g6-scope-correction.md`（22 生产文件清单） |
| `S14` | `E:\creation-reading-assistant\stage-e-s14-r3-backup-closed-20260930\READY.json`（JVM 52/52、APK SHA）+ `docs/coordination/legacy-stages/2026-09/2026-09-30-stage-e-s14-r3-backup-closure.md` |
| `S12` | `docs/coordination/legacy-stages/2026-09/2026-09-28-s12-import-finished-state-result.md` + `docs/qa/2026-09/2026-09-28-s12-mumu-actual-temporary-route-acceptance.md` |
| `S06` | `docs/qa/2026-09/2026-09-30-stage-e-s06-isolated-mumu-boot.md` + `stage-e-s06-boot-isolated-20260929\_evidence\` |
| `MB03` | `docs/coordination/legacy-stages/2026-09/2026-09-29-stage-e-mbug03-isolated-result.md`（候选 A 68/68、B 221/221、C 68/68） |
| `COMMIT` | `git show --stat 209ec8a` / `513891d`（恢复仓库本地提交） |
| `DIFF` | 本目录 `diffs\<item>-<file>.diff` |

**逐行（55 项，`item | 文件 | 批次 | 状态 | 证据`）**

| item | 文件（恢复目录内完整相对路径） | 批次 | 状态 | 证据位置 |
|---:|---|---|---|---|
| 1 | `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/BookActionSheet.kt` | S05 | 有意行为变化 | S05A（删除 `readingState == FINISHED \|\| percent >= 99.5f` 的百分比兜底）+ S05C 七站 |
| 2 | `…/ui/screen/shelf/BookGrid.kt` | S05 | 有意行为变化 | S05A + S05C（`displayFinished` 驱动标签/进度条/封条） |
| 3 | `…/ui/screen/shelf/ShelfModels.kt` | S05 | 有意行为变化 | S05A（`bookStatus/readingStatus` 契约改显式状态） |
| 4 | `…/ui/screen/shelf/ShelfSearchRoute.kt` | S05 | 有意行为变化 | S05A |
| 5 | `…/ui/screen/shelf/ShelfSelectionRoute.kt` | S05 | 有意行为变化 | S05A（原 `percent >= 99.5f → 已读完`） |
| 6 | `…/ui/viewmodel/HomeViewModel.kt` | S05 | 有意行为变化 | S05B + S05C |
| 7 | `…/ui/viewmodel/ProfileViewModel.kt` | S05 | 有意行为变化 | S05B + S05C（完读数） |
| 8 | `…/test/…/data/repository/BookRepositoryTest.kt` | JOINT-B1B2LIB | 已证实保留 | B1B2（联合 JVM 167/167；`D` XML 25/25） |
| 9 | `…/test/…/ui/screen/ShelfStatusFilterTest.kt` | S05 | 有意行为变化 | S05A（重写 3 条旧契约 + 新增判别用例） |
| 10 | `…/test/…/ui/screen/shelf/ShelfStatusCountsTest.kt` | S05 | 有意行为变化 | S05A（取消读完计为在读） |
| 11 | `…/test/…/ui/viewmodel/ProfileViewModelTest.kt` | S05 | 有意行为变化 | S05B（100% + `reading` 判别） |
| 12 | `…/ui/screen/home/components/HomeMetricsSection.kt` | G6 | 已证实保留 | G6（49/49）+ `D` `ComputeTodayReadingMsTest` 6/6 |
| 13 | `…/ui/screen/profile/ProfileHomeScreen.kt` | G6 | 有意行为变化 | G6 READY（手势收口）+ 窗口 1 在飞设备工件（见 §5.3） |
| 15 | `…/ui/screen/readinghistory/MyReadingScreen.kt` | S05+G6 | 有意行为变化 | S05B + G6 |
| 16 | `…/ui/screen/shelf/BookDetailStatsSection.kt` | G6 | 已证实保留 | G6 |
| 17 | `…/ui/screen/stats/components/CreationSection.kt` | G6 | 已证实保留 | G6 + `D` `StatsComputationTest` 2/2 |
| 18 | `…/ui/screen/stats/components/HeatmapSection.kt` | G6 | 已证实保留 | G6 + `D` `HeatmapBuildTest` 4/4 |
| 19 | `…/ui/screen/stats/components/SummaryGroup.kt` | G6 | 已证实保留 | G6 |
| 20 | `…/ui/screen/stats/components/YearBillHeroCard.kt` | G6 | 已证实保留 | G6 + `D` `YearBillHeadlineTest` 10/10 |
| 21 | `…/ui/util/TimeUtils.kt` | G6 | 已证实保留 | G6 + `D` `TimeUtilsTest` 16/16 |
| 22 | `…/src/main/java/…/App.kt` | S06 | 已证实保留 | S06（JVM 13/13；MuMu 冷启动）+ 本报告 §6 启动路径判定 |
| 23 | `…/src/main/java/…/MainActivity.kt` | S06+G6 | 待验证 | `92`（13 行原目录独有）+ S06 + G6：**G6×S06 联合态未编译** |
| 24 | `…/data/settings/SettingsStore.kt` | S06+保留 | 待验证 | S06（7 hunk 已隔离 JVM 13/13）+ 余下 22 hunk 属 RETAIN-SETTINGS；混合行需 hunk 分段归属 |
| 25 | `…/feature/reader/pager/PagedReaderPageSurface.kt` | RETAIN-READER | 待验证 | `92`（14 行原目录独有）；无定向验证 |
| 26 | `…/feature/reader/pager/ReaderTapAction.kt` | RETAIN-READER | 待验证 | `92`（4 行）；无定向验证 |
| 27 | `…/ui/screen/reader/content/PagedEpubContent.kt` | RETAIN-READER | 待验证 | `92`（41 行）；无定向验证 |
| 28 | `…/ui/screen/reader/sheets/TapZonePreviewCard.kt` | RETAIN-READER | 待验证 | `92`（13 行）；无定向验证 |
| 29 | `…/data/settings/SelectionActionSettings.kt` | RETAIN-SETTINGS | **需产品决定** | 源码常量证据：`SelectionActionDef("edit", …)`（:85）与 `DORMANT_IDS = setOf("edit")`（:146）；无专项测试 |
| 30 | `…/test/…/data/settings/SelectionActionSettingsTest.kt` | RETAIN-SETTINGS | 待验证 | `D` 全量门禁 333 套件中**无该类 XML** |
| 31 | `…/test/…/ui/screen/reader/ReaderSelectionToolbarModelTest.kt` | S07 | 有意行为变化 | `D` `ReaderSelectionToolbarModelTest` 9/9 |
| 32 | `…/data/local/dao/SearchTermDao.kt` | BATCH-0+B1 | 有意行为变化 | COMMIT（`@Entity` 索引删除 hunk 随 `209ec8a`）+ B1B2（`deleteByChapter`/`countTerms` 属 S03） |
| 33 | `…/data/repository/BookRepository.kt` | JOINT-B1B2LIB | 有意行为变化 | B1B2（S03 索引并发/孤儿回收 + S04 残留安全清理；JVM 167/167、MuMu 11/11） |
| 34 | `…/data/repository/SearchIndexRepository.kt` | JOINT-B1B2LIB | 有意行为变化 | B1B2（68 行原目录独有；并发/代数重构） |
| 35 | `…/feature/library/ShelfImporter.kt` | JOINT-B1B2LIB+M-BUG-03 | 有意行为变化 | B1B2 + MB03（设备 SAF/UI **NOT RUN**） |
| 36 | `…/ui/viewmodel/ShelfBookActions.kt` | JOINT-B1B2LIB | 有意行为变化 | B1B2（残留安全清理 + 摘要文案） |
| 37 | `…/ui/viewmodel/ShelfViewModel.kt` | M-BUG-03 | 有意行为变化 | MB03（JVM 68/68、221/221；设备 SAF/UI **NOT RUN**） |
| 38 | `…/test/…/feature/search/SearchIndexRepositorySearchTest.kt` | JOINT-B1B2LIB | 已证实保留 | B1B2 + `D` XML |
| 39 | `…/feature/reader/pager/PageTurner.kt` | RETAIN-READER | 待验证 | `92`（16 行）；无定向验证 |
| 40 | `…/feature/sync/LegadoReadingProgressImporter.kt` | S12 | 有意行为变化 | S12（导入边界收敛 99.4% + 保留未完成/搁置） |
| 41 | `…/ui/components/CraSlider.kt` | RETAIN-READER | 待验证 | `92`（8 行）；无定向验证 |
| 42 | `…/ui/screen/homearchive/HomeCompletedScreen.kt` | G6 | 已证实保留 | G6 + `D` `HomeReadingArchiveSectionTest` 2/2 |
| 43 | `…/ui/screen/profile/ProfileRoute.kt` | S14/R3 备份语义 | 有意行为变化 | S14（导入/恢复确认文案改「按版本规则合并覆写」；JVM 52/52） |
| 44 | `…/ui/screen/profile/StorageSubPage.kt` | RETAIN-PROFILE | 待验证 | `92`（5 行）；超出 P0 收口边界 |
| 45 | `…/ui/screen/reader/ReaderLayerBuilders.kt` | RETAIN-READER | 待验证 | `92`（15 行）；无定向验证 |
| 46 | `…/ui/screen/reader/ReaderScreen.kt` | S12 | 有意行为变化 | S12（`persistOnLeave` + 守卫）+ `D`/androidTest |
| 47 | `…/ui/screen/reader/ReaderSelectionExternalActions.kt` | RETAIN-READER | 待验证 | `92`（29 行）；无定向验证 |
| 48 | `…/ui/screen/reader/ReaderSheetHost.kt` | RETAIN-READER | 待验证 | `92`（31 行）；无定向验证 |
| 49 | `…/ui/screen/reader/sheets/ReaderDisplaySettings.kt` | RETAIN-SETTINGS | 待验证 | `92`（6 行）；无定向验证 |
| 50 | `…/ui/screen/reader/sheets/ReaderSettingsSheet.kt` | RETAIN-READER | 待验证 | `92`（20 行）；无同源 JVM XML |
| 51 | `…/ui/screen/reader/sheets/ReaderTocSheet.kt` | RETAIN-READER | 待验证 | `92`（4 行）；无定向验证 |
| 52 | `…/ui/screen/shelf/ShelfRoute.kt` | RETAIN-UI | 待验证 | `92`（4 行）；L1 动态视图（codex-r5） |
| 53 | `…/ui/screen/shelf/ShelfScreen.kt` | RETAIN-UI | 待验证 | `92`（6 行）；**basename 2 处同名，本行仅对应 `ui/screen/shelf/` 下者** |
| 54 | `…/ui/viewmodel/LibraryBrowserViewModel.kt` | JOINT-B1B2LIB | 有意行为变化 | B1B2（隔离 JVM 29/29；MuMu 11/11；严格排除 `ADDED_DESC`） |
| 55 | `…/ui/viewmodel/ReaderDocumentLoader.kt` | S12 | 有意行为变化 | S12（`unconvertedLegadoPosition` seam） |
| 56 | `…/ui/viewmodel/ReaderViewModel.kt` | RETAIN-READER | 待验证 | `92`（35 行）+ `D` `ReaderViewModelTest` XML 在案但覆盖不足 |

> 上表 55 行 + §2 的 10 行 = 65 行，与 `orig-65-evidence.csv` 逐行一致（脚本生成，无人工转录）。

---

## 4. 冻结状态更新（取代历史表述）

### 4.1 当前有效状态

| 批次 | 冻结状态 | 本轮应引用的证据 | 说明 |
|---|---|---|---|
| 首批 G0+U+G2+runner | `LOCAL_COMMITTED` | `209ec8a` → `513891d`；MuMu qahost 8/8 | 保持 HEAD，不动 |
| B1+B2+库浏览器 | `PREPARE_INCLUDE` | 隔离编译 EXIT 0；JVM 167/167；MuMu 11/11；53 文件前后 0 差异 | 已并入在飞联合候选 |
| **G6（行为保留版）** | **READY** | `stage-e-g6-behavior-preserved-20260930\READY.json`：9 类 **JVM 49/49**（0 fail/err/skip）；host `7d39b0a0…` / test `428d58f4…` | 整卡点击**已恢复**、长按归容器；设备手势 6 run/3 fail（在飞，见 §5.3） |
| **S14+R3（最小备份联合）** | **READY** | `stage-e-s14-r3-backup-closed-20260930\READY.json`：**JVM 52/52**；host `b7ce65df…` / test `97609ba0…`；`compileQahostAndroidTestKotlin` 0 错误 | 原 25 错误已闭合 |
| S06 | `EVIDENCE_PENDING` | 隔离编译 EXIT 0；JVM 13/13；MuMu 冷启动录屏 + 53/53 恢复 | 见 §6；缺口收窄为 release/真机与联合态 |
| M-BUG-03 | `EVIDENCE_PENDING` | 候选 A 68/68、B 221/221、C 68/68 | 设备 SAF/UI/MuMu 端到端 NOT RUN |
| R3 / S04-5 同步扩展 | `RETAIN` | — | 跨端同步方案未设计；**S14 androidTest 的反向依赖已由联合候选闭合** |
| G8c / G8e / 阅读器设置 / Profile-Stats 视觉重写 | `RETAIN` | — | 按原位置保留，不阻塞 E 退出 |

### 4.2 历史状态（**不再派重复修复**）

| 历史表述 | 现状 |
|---|---|
| 「ProfileHomeScreen 档案卡点击范围收窄至身份行，需产品确认」（F3-B） | **已被取代**：`stage-e-g6-behavior-preserved-20260930` 恢复整卡点击为唯一手势层，并把长按归给代表容器；新增 `ProfileHomeScreenGestureTest`（androidTest）锁定契约。**不得**再按「点击收窄」派修复。 |
| 「S14-only 候选 androidTest 编译 25 错误、设备 NOT RUN」 | **已闭合**：`stage-e-s14-r3-backup-closed-20260930` 的 `compileQahostAndroidTestKotlin` 0 错误、JVM 52/52、双 APK 产出；窗口 1 在飞原始工件显示 `LocalZipBackupPersistentRestoreTest` 6/6、`LocalExportRoomRollbackTest` 2/2。**不得**再按 25 错误派修复。 |
| 「G6 余 6 文件待拆分 / 16-22」 | 已被 `g6-integrated`（22 生产 + 5 测试）与行为保留版候选取代。 |

### 4.3 冻结范围**未覆盖批次**（本轮新发现，必须显式声明）

**S05-①、S07、S12 三批既有的、已有设备证据的改动，既未出现在 `freeze-scope.csv` 的批次清单中，也不在在飞联合候选（`stage-e-final-joint-20260930`，72 文件 delta）内。**

- 在飞联合候选的 72 文件 delta 按 `group` 为 `b1b2(17) / s14r3(5) / g6(35) / mb03(14)`（+`local.properties`）；**不含** `ShelfModels.kt`、`BookActionSheet.kt`、`ShelfSearchRoute.kt`、`ShelfSelectionRoute.kt`、`HomeViewModel.kt`、`ProfileViewModel.kt`、`HomeContinueSection.kt`（S05-①），也**不含** `ReaderContentHostEpubBranch.kt`、`ReaderContentHostPlainTextBranch.kt`、`ReaderProgressEffects.kt`、`ReaderNotesSheet.kt`（S07/S12）。
- 实测佐证：联合树 `joint/android/.../shelf/BookGrid.kt` 中 `completionState|isDisplayFinished` 命中 **0**（即 S05 的完成态契约改动未进入联合树）；而 HEAD 的 `ShelfModels.kt:77` 仍是 `p >= 99.5f -> ShelfStatusFilter.COMPLETED`，证明 **S05-① 的修复不在 HEAD**。
- 风险：S05-①、S07、S12 都是**已可达功能的既有缺陷修复**（不是新功能），若既不纳入又不显式延期，就会无声丢失。**冻结清单必须逐批给出「纳入」或「延期（含理由与保留位置）」的显式声明。**

---

## 5. 设备结果引用口径

### 5.1 引用规则

设备结论**只引用窗口 1 的独立验收报告**：`docs/qa/2026-09/2026-09-30-stage-e-g6-s14-independent-mumu-acceptance.md`。复核时该报告 **mtime = 2026-09-30 12:45，仍停在 WAITING**（两 READY 均记为「不存在」，未安装候选），因此本报告对**所有设备项保持「待验」**。

### 5.2 在飞原始工件（**非权威，仅登记缺口**）

窗口 1 的证据目录 `E:\creation-reading-assistant\stage-e-g6-s14-independent-acceptance-20260930\` 已有 15:42–15:53 的原始工件：

| 工件 | 内容 | 说明 |
|---|---|---|
| `logs/A1-installed-g6-shas.txt` | 安装的 host/test APK SHA 与 READY 的 `7d39b0a0…`/`428d58f4…` 逐位一致 | 证明装的是**本候选** |
| `logs/A2-gesture-summary.txt` + `A2-gesture-instrument.log` | `ProfileHomeScreenGestureTest` **6 run / 3 passed / 3 failed** | 3 项失败原因均为「期望恰好 1 个文本节点，实得 2 个**同坐标同文本**节点」（`72 小时 0 分钟` @ `l=152,t=324,r=521,b=406`），属**测试选择器/语义重复**判定问题，尚不能断定为产品缺陷 |
| `logs/B1-installed-s14-shas.txt` | host `b7ce65df…` / test `97609ba0…` | 与 S14+R3 READY 一致 |
| `logs/B2-persistent-restore.log` / `B3-room-rollback.log` | `OK (6 tests)` / `OK (2 tests)` | 8/8 |
| `logs/D5-verdict.txt` | 53 文件前后清单 `VERDICT: IDENTICAL` | 恢复对账 |

> **归属**：以上结论与最终 PASS/FAIL 归窗口 1，本报告不代其裁决；本窗口只登记「已存在在飞原始工件、报告未更新」这一事实与其中的失败项。

### 5.3 因此保持「待验」的设备项

G6 手势与 TalkBack、S06 冷启动（release/真机）、S14 进程 kill 与整包原子性、M-BUG-03 SAF/UI。**旧 MuMu 结果（09-27/09-28 的 6/6、62/62、8/8）对应旧 APK 构建，不得作为本候选证据。**

---

## 6. S06 冷启动证据：普通 Activity 启动 vs instrumentation runner

**结论：该证据是普通 Activity 启动路径（launcher / `am start`），不是 instrumentation runner 路径。**

**证据链（三条互相独立）**

1. **录屏接触图**：`stage-e-s06-boot-isolated-20260929\_evidence\s06-cold-phone-contact.png` 逐帧为「MuMu 桌面（应用图标）→ 应用启动图（蓝底书本图标，即主题 `windowBackground`）→ 深色首页加载 → 完整首页」。**instrumentation 不会先显示桌面**；该序列只能是 launcher 冷启动。
2. **合并清单**：候选 `android/app/build/intermediates/packaged_manifests/qahost/processQahostManifestForPackage/AndroidManifest.xml` 为 `package="com.creationreadingassistant.qahost"` + `android:name="com.creationreadingassistant.App"` + `com.creationreadingassistant.MainActivity`。qahost **没有独立 source set/manifest**（`app/src/` 下只有 `main/test/androidTest/benchmark*/release`），因此普通启动执行的是**生产 Application 与生产 Activity**。
3. **隔离只在 instrumentation 生效**：`CraIsolatedTestRunner.newApplication` 仅由 `AndroidJUnitRunner` 在 `am instrument` 时调用，且仅当目标包 == `com.creationreadingassistant.qahost` 才替换为 `CraIsolatedTestApplication`；普通启动不经过该路径。旁证：`E:\creation-reading-assistant\codex-s06-mumu-20260927\06-am-start.txt` 记录的是 `Starting: Intent { cmp=com.creationreadingassistant.qahost/com.creationreadingassistant.MainActivity }`（普通 Activity 启动）。

**因此**：`docs/coordination/decisions/2026-09/2026-09-30-stage-e-freeze-scope-and-asset-corrections.md` §D.2 第 7 条「qahost runner 用隔离 Application，不能冒充生产 `App.kt` 启动链路证据」的**适用范围应收窄为「instrumentation 观测」**；对**普通冷启动录屏**不成立。

**仍然成立的残余缺口（须如实登记）**

1. **release 变体与正式 `applicationId`**：证据是 `qahost`/`debug`（`initWith(debug)`、`.qahost` 后缀、debug 签名）构建，非 release。
2. **实体手机与真实 ROM**：仅 MuMu API 32。
3. **帧间无更短闪烁**：报告自陈「录屏无法证明两帧之间绝无更短闪烁」，且最大亮像素比例来自启动前桌面帧。
4. **与 G6 的联合态**：S06 与 G6 在 `MainActivity.kt` 有相邻 hunk，联合编译未跑。

---

## 7. 仍阻塞阶段 E 退出的最小清单

| # | 阻塞点 | 现状与证据 | 归属 | 解除判据 |
|---|---|---|---|---|
| **B1** | **最终联合候选未成型**：`stage-e-final-joint-20260930`（HEAD→72 文件 delta / 71 patch 文件）仍在构建中；S05-①/S07/S12 未声明去向（§4.3） | 在飞；`manifest/joint-selection.csv`（4 组 71 文件）、`patch-meta.json` | 集成者窗口 | 联合树全量编译 + `compileDebugAndroidTestKotlin` + 全量 JVM 一次通过；**并显式声明 S05-①/S07/S12 纳入或延期** |
| **B2** | **G6 设备手势验收未过**：`ProfileHomeScreenGestureTest` 6 run / 3 fail（同坐标 2 个同文本语义节点） | 窗口 1 在飞原始工件（§5.2）；权威报告未更新 | 设备验收窗口（窗口 1） | 归因（测试选择器 vs 产品语义重复）并修复后重跑；报告更新为 PASS/FAIL |
| **B3** | **S06 冷启动与 G6 联合态**：G6×S06 相邻 hunk 未联合编译；冷启动证据未覆盖 release/真机 | `MainActivity.kt` 混合 hunk（§3 item 23）；S06 报告自陈边界（§6） | 集成者 + 设备窗口 | 联合树编译通过；release/真机或明确限定 MuMu 范围并如实标注 |
| **B4** | **S14 设备层（进程 kill / 整包原子性）与 M-BUG-03 SAF/UI 未验** | S14+R3 已 READY（JVM 52/52、APK 就绪、持久恢复/回滚在飞 8/8）；M-BUG-03 仅 JVM 68/68/221/221 | 设备验收窗口 | 真实进程 kill / 整包原子性观测；M-BUG-03 SAF 授权 + 来源文件实际更新 + UI 确认 |
| **B5** | **权威矩阵勘误未回写 + 混合行未按 hunk 归属** | `matrix-corrections-20260930.csv` 3 行（2 处 `EXCLUDED` 误排 + H180 错归属）未入权威矩阵；`H007`(BookDao)/`H004`/`H011`/`H113` 混合行未分段 | 集成者 | 勘误回写；混合行逐 hunk 判定归属 |
| **B6** | **用户提交授权** | 计划 §E 要求用户明确授权 | 用户 | 明确授权后方可 stage/commit |

**明确不在阻塞清单内**（保留原位置与依赖，不要求清空 HOLD）：`RETAIN-READER`（12 项 reader UI + G8c/G8e）、`RETAIN-SETTINGS`、`RETAIN-PROFILE`、原目录独有 9 项资产、桌面端资产。

**「已可达功能缺陷不得改称新功能延期」的落地**：`M-BUG-03`（来源更新确认）与 `S14`（备份恢复安全）是工作树可达的既有缺陷修复 → 分别落在 B4；S05-①/S07/S12 的缺陷修复 → 落在 B1 的显式声明要求。四者均**不得**以「新功能」名义无限期悬挂。

---

## 8. 延期资产：保留位置与依赖

| 延期资产 | 原位置 | 依赖 / 后续 |
|---|---|---|
| `RETAIN-READER` **12 项**（item 25 `PagedReaderPageSurface.kt`、26 `ReaderTapAction.kt`、27 `PagedEpubContent.kt`、28 `TapZonePreviewCard.kt`、39 `PageTurner.kt`、41 `CraSlider.kt`、45 `ReaderLayerBuilders.kt`、47 `ReaderSelectionExternalActions.kt`、48 `ReaderSheetHost.kt`、50 `ReaderSettingsSheet.kt`、51 `ReaderTocSheet.kt`、56 `ReaderViewModel.kt`） | 恢复仓库工作树 `android/app/src/main/**`（**原位未动**） | G8c 跨章 Locator 契约（`encode: Int→Int?` 破坏公共接口）与 G8e 阅读器 UI 块未固化 |
| `RETAIN-SETTINGS`（`SettingsStore.kt` 余 22 hunk、`SelectionActionSettings.kt` 等） | 同上 | 单书设置覆写与选区动作接线未完成；item 29 需产品决定 |
| `RETAIN-PROFILE`（`StorageSubPage.kt` 等 39 UI 文件） | 同上 | 超出 P0 收口边界 |
| 原目录独有 9 项资产（桌面 3 源码 + 3 文档 + 2 隔离项 + 1 待确认真实路径项） | `D:\develop\Code\Codex\creation-reading-assistant\`（**原位未动**） | 桌面端集成时处理；隔离项严禁入库；第 9 项路径含非法字符需用户确认真实路径；**删除需用户授权** |
| `R3/S04-5` 跨端同步扩展 | 工作树 `JsonBridge.kt`（R3 闭包已并入 S14+R3 候选） | 跨端同步方案未设计 |

---

## 9. 勘误与纪律自证

### 9.1 对既有表述的具体勘误

| 出处 | 原文（需改） | 更正 |
|---|---|---|
| `2026-09-30-stage-e-asset-hold-reconciliation.md` §4.2 | 「原目录独有 **65 项**差异…**全部**为有意演进」 | 物理表 **67 行**；65 待关注集合 **逐行证据**见 §2/§3 与 `orig-65-evidence.csv`（已证实保留 18 / 有意行为变化 27 / 待验证 19 / 需产品决定 1）。「全部有意演进」作废。 |
| `2026-09-30-stage-e-freeze-scope-and-asset-corrections.md` §D.2 第 7 条 | 「qahost runner 用隔离 Application，不能冒充生产 `App.kt` 启动链路证据」 | **适用范围收窄为 instrumentation 观测**；普通冷启动录屏（§6）已覆盖生产 Application/Activity/主题链，残余缺口是 release/真机。 |
| `2026-09-30-stage-e-freeze-scope-and-asset-corrections.md` §B | 批次清单缺 S05-① / S07 / S12 | 三批均为**已可达功能的既有缺陷修复**且有设备证据，须显式声明纳入或延期（§4.3 / B1）。 |
| `2026-09-30-stage-e-s14-isolation.md` §1.3 | androidTest 两文件「HEAD 已跟踪」 | 实况 **HEAD 未跟踪**；该错误已由 S14+R3 联合候选关闭（§4.2）。 |
| 本轮 65 表 | `in_seven_summary=否` 共 11 行 | 其中 **10 行**在 65 集合内（57,58,59,60,61,62,64,65,66,67）；第 11 行 item 63 `DurationDisplay.kt` 在 92 表属 `CONFIRMED_COVERED`，**不在** 65 集合。 |

### 9.2 纪律

- 共享恢复仓库内**唯一写入 = 本文件**；E 盘全部写入在 `E:\creation-reading-assistant\stage-e-asset-evidence-final-20260930\`（新建）。
- **未**改任何产品源码 / 测试 / 配置 / CSV / 其他窗口报告 / READY / handoff；**未** stage / commit / push / reset / checkout / stash；**未**切换工作目录；**未**构建；**未**操作设备 / MuMu / ADB / 正式书库；**未**删除或搬移任何目录。
- 其他窗口目录（`stage-e-final-joint-20260930`、`stage-e-g6-s14-independent-acceptance-20260930`、`stage-e-g6-behavior-preserved-20260930`、`stage-e-s14-r3-backup-closed-20260930`、`stage-e-freeze-ledger-20260930`、`stage-e-incremental-20260929`、`codex-e-preflight-20260929\stage-e-recon`）**只读**，仅读取 CSV / READY / 日志 / XML 与源码，未写入、未修改。
- 未据模型或报告标题裁定通过；`待验证` 项一律标注「不得当作通过」；设备项保持「待验」并如实登记在飞工件。
- 全文只用中性标识，未记录任何真实书名。
