# 阶段 E：冻结范围提案与资产账本勘误（2026-09-30）

> **窗口**：阶段 E 资产账本勘误 + 最终冻结范围提案（只读审计 + 可复算对账）。
> **共享恢复仓库（只读入口）**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`。
> **HEAD 核对**：`513891d6a66a9b78e072ba759d34ed580d377ecf`；`git diff --cached` 无输出（暂存区空）。
> **E 盘独占输出**：`E:\creation-reading-assistant\stage-e-freeze-ledger-20260930\`（账本 + 复算脚本，全部新建）。
> **唯一可写共享文件**：本报告。
> **纪律**：只读产品源码与原资产；**未**改源码 / 测试 / 配置 / CSV / 其他窗口报告；**未** stage / commit / push / reset / checkout；**未**迁目录；**未**操作设备 / MuMu / ADB / 正式书库 / 原仓库；**未**删除或搬移任何目录。既有候选与矩阵**全部原样保留**。
> **不重做证据**：本轮不重跑任何已完成构建/测试；结论只引用既有原始日志/XML/CSV 并做可复算核对；**不据模型或报告标题裁定通过**。

---

## 0. 结论先行（五条必须纠正的事实）

| # | 既有表述（需纠正） | 实算事实 | 证据 |
|---|---|---|---|
| **C1** | G6「16/22 生产 + 4/5 测试，余 6 文件待拆分」 | G6 已有**单一集成候选 = 22/22 生产 + 5/5 测试**；定向 JVM **49/49**（9 类，0 fail/error/skip）；补丁 SHA `57638361044764d55d2ee6bea681c47f72f3900d2afe9f1eb5a70af771b0b95a` | `stage-e-g6-integrated-20260930/_evidence/test-xml-targeted/` 9 份 XML 逐套件求和；`g6-integrated-review.md` |
| **C2** | hold-180「55 候选 + 122 HOLD + 3 已验 = 180」 | 按 **mapping 前缀**实算 **122 HOLD + 54 候选 + 4 已验 = 180**；存在 1 行**已验+候选混合行**（`H007` = `BookDao.kt`），不能整行/整文件标已验 | 本报告 §A.1；`ledgers/hold-180-bucket-summary.csv` |
| **C3** | `orig-65-to-seven-category-mapping.csv`「65 项」 | 该文件实际 **67 行 / 67 个不同 csv_path**；与 92 行对账表、与「65 项集合」的集合差已在 §A.2 逐项列明 | 本报告 §A.2；`ledgers/orig-65-set-reconciliation.csv`、`set-diff-67-92-65.csv` |
| **C4** | 「S14/R3 物理 hunk 可分离」即视作可交付 | 物理分离**成立**，但**设备测试依赖未闭合**：S14-only 候选下 androidTest 构建 **FAILED（25 个 `e:`）**，无测试 APK，设备 **NOT RUN**；且相关 androidTest 在 HEAD **未跟踪** | 本报告 §A.4；`build-qahost-apks.log`；`git ls-tree/ls-files` |
| **C5** | 资产报告「全部有意演进」「直接丢弃」「永久隔离」 | 这些是**处置建议措辞**，**不能**代替逐项证据或用户的删除授权；`orig-65` 67 行中 10 行明确标「七类摘要未覆盖」，需逐项证据 | 本报告 §A.2、§C、§E |

**阶段口径**：阶段 E 仍在 **4/5（80%）**。本轮**不**裁决阶段是否退出，只交付可复算账本与冻结范围提案；下一版本地提交仍需用户明确授权。

---

## A. 可复算的分类与集合对账（算法 + 混合行 + 表 SHA + 每项证据）

### A.0 复算算法与入口

- **脚本**：`E:\creation-reading-assistant\stage-e-freeze-ledger-20260930\build_ledgers.ps1`（pwsh 7 下重跑逐字节一致）。
- **桶判定算法（hold-180，按 `mapping` 前缀，自上而下）：**
  1. `mapping` 以 `保留` 开头 → `HOLD`；
  2. 否则以 `首批已验` 开头 → 若同时含 `候选分组` 则 `MIXED_VERIFIED_CANDIDATE`，否则 `VERIFIED`；
  3. 否则以 `候选分组` 开头 → `CANDIDATE`；
  4. 其余 → `UNCLASSIFIED`。
- **集合差算法：** 同一内核中按**文件名（basename）身份**对齐两张表（`orig-65` 用省略号缩写路径，`92 行` 用全路径）；65 集合 = 92 表中 `coverage ∈ {LIKELY_COVERED, NEED_REVIEW}` 的行。

### A.1 hold-180 分类实算（否定「55+122+3」）

对 `hold-180-row-mapping-updated.csv`（SHA-256 `FFB5FC2309CCD39D52DCC0488E049C153B61A4A9802C7FAD12ABFA8E18AD7BF7`，180 数据行）逐行判定：

| 桶 | 行数 | 说明 |
|---|---:|---|
| `HOLD`（前缀「保留 HOLD」） | **122** | 含 §2.9 已定义 HOLD 桶 37 + 待裁决 16 + 暂缓 69 |
| `CANDIDATE`（前缀「候选分组」，无已验） | **54** | B1/B2/B3/B4/B5/B6 各候选分组，尚未隔离编译 |
| `VERIFIED`（前缀「首批已验」，纯已验） | **3** | `H001`（`build.gradle.kts`）、`H008`（`InspirationDao.kt`）、`H010`（`TaxonomyDao.kt`） |
| `MIXED_VERIFIED_CANDIDATE`（已验+候选同行） | **1** | `H007`（`BookDao.kt`）= `首批已验（U B1–B4）+ 候选分组 B4（B5，未隔离编译）` |
| **合计** | **180** | `122 + 54 + 3 + 1 = 180` ✓ |

**混合行（不能整行/整文件标已验）：**

| matrix_id | 文件 | mapping 原文 | 风险 |
|---|---|---|---|
| `H007` | `app/src/main/java/.../data/local/dao/BookDao.kt` | `首批已验（U B1–B4）+ 候选分组 B4（B5，未隔离编译）` | 同一文件既含首批已验 hunk（B1–B4），又含未隔离候选 hunk（B5）。**整文件标已验 = 谎报**；提交时必须按 hunk 分段归属 |

**候选多组混合行（仍属候选桶，但一行跨两组）：** `H004`（`AndroidManifest.xml`，`B3+B4`）、`H011`（`BookRepository.kt`，`B1 + B2`）、`H113`（`BookRepositoryTest.kt`，`B6 + B1`）。

**与旧报告的差异（为何「55+122+3 不成立」）：** 旧报告按「是否**含有**候选分组」计数 = 55（把混合行 `H007` 并入候选），已验按「纯已验」计数 = 3（漏掉 `H007` 的已验部分）。前缀法把 `H007` 归入已验桶（因前缀为「首批已验」），于是候选 54 / 已验 4。两法总数都等于 180，但**桶大小各差 1，且旧法完全掩盖了「同一文件既是已验又是候选」这一事实**。逐行明细见 `ledgers/hold-180-recompute.csv`。

### A.2 `orig-65`（67 行） × 92 行对账表 × 65 集合 的三方集合差

源表（只读）：

| 表 | 行数 | SHA-256 |
|---|---:|---|
| `orig-65-to-seven-category-mapping.csv` | **67**（67 个不同 `csv_path`） | `B57CFC2BF28D43115E7CE39BEA618B8A374CB489DE512A80BCBB449BA934710E` |
| `orig-unique-full-reconciliation-corrected.csv` | **92** | `E223726489155547E49E872AD8E0E3A3E4087919139CB2EFF64BCA1A1A53F34B` |

**92 行表 coverage 分布：** `CONFIRMED_COVERED`=21、`CONFIRMED_COVERED_MIGRATED`=6（合计 27 = 已确认覆盖）、`LIKELY_COVERED`=23、`NEED_REVIEW`=42（合计 **65 = 待关注集合**）。27 + 65 = 92 ✓。

**集合关系（逐项见 `ledgers/set-diff-67-92-65.csv`）：**

| 方向 | 数量 | 逐项 |
|---|---:|---|
| `IN_67_NOT_IN_92` | **0** | 67 的文件身份全部落在 92 表内（`orig-65` 仅 2 个省略号缩写路径，归一后完全对齐） |
| `IN_92_NOT_IN_67` | **25** | 92 表多出 25 项，**全部为已确认覆盖**（`CONFIRMED_COVERED` / `CONFIRMED_COVERED_MIGRATED`），例如 `ParentUpsertCascadeSafetyTest.kt`、`DurationPrefs.kt`、`ReaderHardwareKeys.kt`、`MetricMicroCard.kt` 等 |
| `IN_67_NOT_IN_65` | **2** | `ProfileUtils.kt`（`item14`，`semantic_sub=B`，`in_seven=是`，92 表 `CONFIRMED_COVERED_MIGRATED`）、`DurationDisplay.kt`（`item63`，`semantic_sub=H`，`in_seven=否`，92 表 `CONFIRMED_COVERED`） |
| `IN_65_NOT_IN_67` | **0** | 65 集合无缺项 |

**结论（明确多出/缺少）：**
1. `orig-65` 文件名与实际不符：**实际 67 行**（多出 2 行：`ProfileUtils.kt`、`DurationDisplay.kt`，二者在 92 表中均已是**确认覆盖**，本不该计入「需关注」）。
2. **没有「缺少」项**：65 集合（`LIKELY_COVERED`+`NEED_REVIEW`）完全被 67 表覆盖。
3. 65 集合中 **55 项**对应 67 表标 `in_seven_summary=是`，**10 项**标 `否（七类摘要未覆盖）`——即看起来「七类摘要已覆盖」的条目里，仍混有 10 项未被七类摘要覆盖，**需逐项证据**。
4. 因此资产报告 §4.2「65 项**全部**为有意架构演进」缺乏逐项证据：既把「确认覆盖」与「需复核」混为一谈，又未对 10 项「摘要未覆盖」逐项举证。

### A.3 G6：22+5 与 49/49（纠正「余 6 文件待拆」）

| 项 | 实测 | 证据 |
|---|---|---|
| 生产文件 | **22/22** 存在（符号 `LocalDurationUnit|DurationPrefs|DurationUnit` 全树扫描命中恰 22） | `g6-integrated-review.md` §三 |
| 测试文件 | **5/5** | 同上 |
| 集成候选规模 | consumers3 底座 + settings(2) + stats(5) = 7 文件叠放；全树 `diff -rq` 恰 7 文件差异 | 同上 §三 |
| 编译 | `:app:compileDebugKotlin` + `:app:compileDebugUnitTestKotlin` EXIT 0 | `_evidence/compile-integrated.log` |
| 定向 JVM | **49/49**（9 类，0 fail/error/skip） | `_evidence/test-xml-targeted/` 9 份 XML 逐套件求和（见下） |

**49 项逐套件复算**（本轮直接对原始 XML 重新求和，非转述）：

```
DurationPrefsTest=8  DurationUnitTest=2  HomeReadingArchiveSectionTest=2
YearBillHeadlineTest=10  ComputeTodayReadingMsTest=6  HeatmapBuildTest=4
StatsComputationTest=1  StatsFileStructureTest=3  TimeUtilsTest=13
TOTAL = 49, failures=0, errors=0, skipped=0
```

**并集关系**：consumers3 的 25 ∪ stats 的 37（`TimeUtilsTest` 去重）= 49。

**仍未验（不得外推）**：UI 手势 / TalkBack / 持久化重启 / 视觉布局 / lint / assembleDebug / 与 S06 联合态 / 设备验收。`ProfileHomeScreen` 的 **F3-B 交互变化**（统计行不再触发卡片点击）是**真实行为变化**，需产品确认。

### A.4 S14/R3：物理 hunk 分离 ≠ 设备测试依赖闭合

| 检验 | 实测 | 证据 |
|---|---|---|
| JsonBridge S14 精确 4 hunk | 可分离（`ImportMergeResult` + 回调签名 + 事务内收集 + 事务后回调），**无** R3 DTO | `2026-09-30-stage-e-s14-isolation.md` |
| S14 隔离编译 + JVM | `:app:compileDebugKotlin`/`UnitTest` EXIT 0；`LocalZipBackupTest` **34/34** | 同上 §三/§四，XML SHA `45C6D936…` |
| **S14 androidTest 构建** | **FAILED in 3m 21s**，`e:` 行 **25** 条；**无测试 APK 产出；设备 NOT RUN** | `E:\...\stage-e-s14-isolated-20260930\build-qahost-apks.log` |
| 25 条错误根因 | 全部指向**被 S14 候选显式排除的 R3 API**：`Unresolved reference 'SourceRefExport'`(3)、`No parameter with name 'sourceRefDao'/'textRuleDao'/'correctionDao'`(各 2)、`librarySourceRefs`/`textCorrections`/`textRules`/`CorrectionExport`/`TextRuleExport` 等 | 同上（错误按文本聚合） |
| androidTest 文件在 HEAD 的状态 | `LocalZipBackupPersistentRestoreTest.kt`、`LocalExportRoomRollbackTest.kt`、`LegadoMarkerRealImportTemporaryTest.kt` 均 **`??` 未跟踪**，`git ls-tree HEAD` **不含** | `git ls-files`/`git ls-tree -r HEAD`（本轮实跑） |

**推论：** 把 S14 从 R3 里「物理切出」只证明**源码可编译**；它的**设备测试资产反向依赖 R3 的 `SourceRefExport` 等符号**，因此在 S14-only 候选下连测试 APK 都造不出来。故 S14 **不能**凭「hunk 可分离」宣称设备可交付；`LocalZipBackupPersistentRestoreTest` / `LocalExportRoomRollbackTest` 的旧 MuMu 结果只在其**原构建**下有效，且这两文件在 HEAD 未跟踪（旧报告称「HEAD 已跟踪」与实况不符，见 §E）。

### A.5 源表 SHA-256 清单（本报告全部计数所依据）

| 表 | SHA-256 | 字节 |
|---|---|---:|
| `hold-180-row-mapping-updated.csv` | `FFB5FC2309CCD39D52DCC0488E049C153B61A4A9802C7FAD12ABFA8E18AD7BF7` | 40,927 |
| `hold-180-row-mapping.csv`（初始） | `E9E523881ACCDBAC40C9856B2BBF8584C7B1133306472BC430E69D3E4C159E98` | 34,706 |
| `orig-65-to-seven-category-mapping.csv` | `B57CFC2BF28D43115E7CE39BEA618B8A374CB489DE512A80BCBB449BA934710E` | 7,031 |
| `orig-unique-full-reconciliation-corrected.csv` | `E223726489155547E49E872AD8E0E3A3E4087919139CB2EFF64BCA1A1A53F34B` | 85,846 |
| `matrix-corrections-20260930.csv` | `DD3597E33D61B0FFC51FA7CE4E93C21B413536CB12FD9D168645436FFA4AF8AD` | 1,023 |
| `hold-unmapped-list.txt` | `3BCA1E625E9CE6804419F325E6B936B433D11C0D1FC596DD1EDAD90654511103` | 8,038 |
| `incremental-manifest.csv` | `5C7E48E82D9392015C8BF55FAE2FEC0971FDD671B68928A14576B9BA3407652B` | 9,375 |
| `original-only-assets.csv` | `F7B6253DC910D8305DDABEC454DB4AB60FC42F67134C744364BE47B24115AD93` | 40,261 |

（可复算：`ledgers/source-table-sha256.csv`，由 `build_ledgers.ps1` 重算。）

---

## B. 阶段 E 冻结范围提案（四状态逐项）

状态定义：
- **`LOCAL_COMMITTED`（已本地提交）**：已在恢复仓库本地提交，构成当前 HEAD。
- **`PREPARE_INCLUDE`（准备纳入）**：证据已足够进入下一次联合候选，只待最终联合候选构造与用户提交授权。
- **`EVIDENCE_PENDING`（证据待补）**：隔离编译/JVM 已有，但缺设备/产品/联合态证据，**不得**先提交。
- **`RETAIN`（后续保留）**：本阶段维持 HOLD，不进入 E 的提交批次。

| 状态 | 项 | 范围 / 关键文件 | 现有证据 | 冻结判断 |
|---|---|---|---|---|
| **LOCAL_COMMITTED** | 首批 `G0+U+G2+runner` | Room v15 迁移 + 父表 `@Upsert` + qahost 测试基建 | `209ec8a` → `513891d`；MuMu qahost **8/8** | 保持 HEAD，不动 |
| **PREPARE_INCLUDE** | **B1**（S03 搜索索引并发/孤儿回收） | `SearchIndexRepository.kt`、`SearchIndexScheduler.kt`、`SearchIndexBookRefreshCoordinator.kt`、`BookRepository.kt`(S03)、`BookRepositorySearchIndexTest.kt` | 隔离编译 EXIT 0；JVM **75/75** | 与 B2/库浏览器**联合**交付 |
| **PREPARE_INCLUDE** | **B2**（S04 缓存残留安全） | `BookRepository.kt`(S04)、`BookResidueSafeCleanupTest.kt`、`ShelfBookActions.kt`、`BookRepositoryTest.kt`(filesDir 桩) | 隔离编译 EXIT 0；JVM **76/76** | 同上 |
| **PREPARE_INCLUDE** | **库浏览器选择边界** | `LibraryBrowserViewModel.kt`(搜索选择 hunk)、`LibraryBrowserSelectionTest.kt` | 隔离编译 EXIT 0；JVM **29/29** | 同上；严格排除 `ADDED_DESC` |
| **PREPARE_INCLUDE** | **B1+B2+库浏览器联合候选** | 上述 18 文件（11 改 + 7 新），`BookRepository.kt` 独占合流 | 联合编译 EXIT 0；JVM **167/167**；MuMu qahost **11/11**；53 文件前后 SHA **0 差异**；补丁 SHA `77FB1270…C3DC` | **本阶段首选纳入项**；待最终联合候选 + 授权 |
| **EVIDENCE_PENDING** | **S06**（启动主题防闪烁） | `SettingsStore.kt`(7 hunk)、`MainActivity.kt`、`App.kt`、`AndroidManifest.xml`(uiMode)、`themes.xml`×2、`colors.xml`、`SettingsStoreBootMirrorTest.kt` | 编译 EXIT 0；JVM **13/13**；补丁 SHA `238274ef…8df4d` | **缺冷启动第一帧设备观测**；且 qahost runner 用隔离 Application，**不能**冒充生产 `App.kt` 启动链路证据 |
| **EVIDENCE_PENDING** | **G6**（时长单位全局切换） | **单一集成候选 22 生产 + 5 测试**；`g6-integrated.patch` SHA `57638361…0b95a` | 编译 EXIT 0；G6 关联 JVM **49/49** | **缺**：UI 手势/TalkBack/持久化重启/**与 S06 联合态**/lint/assemble/**F3-B 产品确认** |
| **EVIDENCE_PENDING** | **S14**（ZIP 备份恢复安全） | `LocalZipBackup.kt`、`JsonBridge.kt`(仅 S14 4 hunk)、`LocalZipBackupTest.kt` | 编译 EXIT 0；JVM **34/34** | **缺**：androidTest 构建（当前 25 错误）、设备验收；androidTest 依赖 R3 API |
| **EVIDENCE_PENDING** | **S14 androidTest 资产** | `LocalZipBackupPersistentRestoreTest.kt`、`LocalExportRoomRollbackTest.kt`、`LegadoMarkerRealImportTemporaryTest.kt` | HEAD **未跟踪（`??`）**；S14-only 候选下构建 FAILED(25 `e:`) | 须先闭合对 R3 API 的依赖，方可构建测试 APK；**不得**当作已验 |
| **EVIDENCE_PENDING** | **M-BUG-03**（来源更新确认） | `ShelfSourceUpdateChecker.kt`(+Test)、`ShelfImporter.kt`(M-BUG-03 hunk)、`ShelfSourceRefreshFeedbackTest.kt`(H180)、`ShelfSourceRefreshCopyTest.kt`(H174) | 候选 A **68/68**、B **221/221**、C **68/68**（JVM） | **缺**：SAF/UI/**MuMu 端到端**；矩阵勘误未回写权威矩阵 |
| **RETAIN** | **R3 / S04-5**（来源索引与同步扩展） | `JsonBridge.kt` 的 R3 DTO + 3 DAO 注入、`LocalExport` 字段、`SafLibrarySourceTest.kt` 等 | 无（仅 S14 4 hunk 已分离） | 跨端同步方案未设计；**且 S14 androidTest 反向依赖此 API** |
| **RETAIN** | G8c 跨章 Locator 契约 | `LocatorBuilder.kt`、`LocatorCodec.kt`、`EpubRepository.kt`(H8) | 无 | `encode` 由 `Int`→`Int?` 破坏公共接口，波及 6 调用方 |
| **RETAIN** | G8e 阅读器通用 UI | 58 个 reader UI 文件 | 无 | 依赖 G8a/G8c 未固化 |
| **RETAIN** | 阅读器设置覆盖与选区动作 | `SettingsStore.kt`(余下 22 hunk)、`PerBookSettingsStore.kt`、`SelectionActionSettings.kt` | 无专项测试 | 测试覆盖缺失 |
| **RETAIN** | Profile/Stats 大重写 | `ProfileHomeScreen.kt`(44 hunk)、`GoalSubPage.kt`(73 hunk)、`StorageSubPage.kt` 等 39 UI 文件 | 改动量数千行 | 超出 P0 收口边界 |

**「不要求实现全部 HOLD 才退出 E」的口径说明：** 上面 `RETAIN` 各行是**明确保留**项，E 的退出**不依赖**它们实现；E 的退出只要求「`PREPARE_INCLUDE` + `EVIDENCE_PENDING` 两类被最终联合候选收口，并把 `RETAIN` 的边界写清」。

**「已可达功能缺陷不能当新功能延期」：** `M-BUG-03`（来源更新确认）与 `S14`（备份恢复安全）是**已在工作树可达的既有缺陷修复**，属 P0 收口范畴，**不得**降级为「新功能」无限期悬挂；二者当前卡在**设备证据**（M-BUG-03）与**测试依赖闭合**（S14），应优先补齐而非延期。（桌面端资产、Profile/Stats 视觉重写等才属真正的新功能/大改，可保留。）

---

## C. 保留资产：原位置 / 保全证据 / 后续依赖

### C.1 原目录独有 9 项资产（`docs/coordination/legacy-stages/2026-09/2026-09-30-stage-e-asset-hold-reconciliation.md` §4.1 复核）

| # | 原位置（`D:\develop\Code\Codex\creation-reading-assistant` 侧） | 原位置保全证据 | 后续依赖 / 处置 |
|---|---|---|---|
| 1–3 | `electron/main/creation-workspace/{bundle-merge,proof-engine,validate}.ts` | 原资产未改；已在 E 盘保全 | 桌面端集成时处理；**严禁**混入 Android 提交 |
| 4 | `docs/plans/2026-09-21-desktop-main-process-split-proposal.md` | 同上 | 不入 Android 分支 |
| 5 | `docs/qa/2026-09-26-s04-epub-image-cache-mumu-portrait-supplement.md` | 同上 | 历史档案 |
| 6 | `docs/release/ANDROID_RELEASE.md` | 同上 | 发版阶段独立使用 |
| 7 | `.claude/settings.local.json` | 仅计划归档，未入库 | **严禁入库**（含本机授权）；本阶段**不删除**，需用户明确授权方可处置 |
| 8 | `qa_pk4.xml` | 未入库 | **严禁入库**（设备 dump）；同上 |
| 9 | `C:\<U+F03A>WindowsTemp\index_backup.ts` | 未入库 | 报告建议「直接丢弃」——**本阶段不执行删除**；须用户授权；`<U+F03A>` 为非法路径字符，需先向用户确认真实路径 |

> **勘误**：报告 §4.1 对第 7/8/9 项的「永久隔离 / 直接丢弃」是**处置建议**，不是删除授权。本阶段**原样保留**，不改不删。

### C.2 `orig-65` 表（实际 67 行）的保留属性

- **原位置**：`E:\creation-reading-assistant\stage-e-incremental-20260929\orig-65-to-seven-category-mapping.csv`（SHA `B57CFC2B…`），**只读未改**。
- **保全证据**：67 行 / 67 个不同 `csv_path`；67 ⊆ 92；其中 2 项（`ProfileUtils.kt`、`DurationDisplay.kt`）已是**确认覆盖**，55/67 标七类摘要已覆盖、10 项标未覆盖。
- **后续依赖**：**严禁**从原目录逆向回合旧代码（恢复目录为演进后状态）；但「全部有意演进」需为 10 项「摘要未覆盖」逐项补证据后改写为「逐项已核对」，见 `ledgers/evidence-gaps.csv` GAP-10。

### C.3 hold-180 保留 HOLD 122 行的保留属性

- **原位置**：恢复仓库工作树（`android/**`），`hold-180-row-mapping-updated.csv`（SHA `FFB5FC23…`）逐行登记。
- **保全证据**：122 行 = HOLD 桶 37 + 待裁决 16 + 暂缓 69；**文件与 hunk 原样保留在工作树**，本阶段未改动、未删除。
- **后续依赖**：按功能路线逐项规划（G8c/G8e/阅读器设置/Profile-Stats/同步）；其中 `H007`（`BookDao.kt`）等混合行须按 hunk 分段归属后方可提交。

---

## D. 最终健康开发入口与「提交前仍缺证据」

### D.1 健康开发入口（优先沿用恢复仓库，不做无必要迁移）

- **建议**：**继续以恢复仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（分支 `codex/recover-worktree-20260920`）为唯一健康开发入口**。
- **理由**：HEAD 已含首批 A/B 两提交（`209ec8a`、`513891d`），工作树既有资产完整、暂存区空；继续沿用可保持「已提交基线 + 未提交工作树」两层结构清晰，并复用既有 E 盘候选与缓存。
- **不做迁移**：**避免**无必要的目录迁移/重建（迁移会打断候选 SHA 对齐、需要重建 Gradle 缓存、并可能触发对用户资产的搬移）。原仓库 `D:\develop\...\creation-reading-assistant` 与冻结归档 `archives/frozen-mobile/` **保持只读原位**。

### D.2 形成「最终联合候选 → 联合验证 → 精确本地提交」前仍缺的证据

> 逐项见 `ledgers/evidence-gaps.csv`（GAP-01 … GAP-12）。摘要：

**进入最终联合候选前：**

1. **G6 × S06 联合态**（GAP-01）：两候选在 `MainActivity.kt` 有相邻 hunk，须在同一棵树联合编译 + 跑定向 JVM。
2. **G6 F3-B 产品确认**（GAP-02）：`ProfileHomeScreen` 统计行不再触发卡片点击是否可接受；若不可接受需改 `DurationDisplay.durationUnitToggle`（跨所有权 seam）。
3. **S14 androidTest 依赖闭合**（GAP-05）：纳入 R3 API **或**改写 androidTest 以移除对 `SourceRefExport` 等符号的依赖，否则测试 APK 永远造不出。
4. **矩阵勘误回写**（GAP-08）：`matrix-corrections-20260930.csv` 的 3 行勘误（2 处 `EXCLUDED` 误排 + `H180` 错归属）尚未写入权威矩阵。
5. **混合行处置**（GAP-09）：`H007`/`H004`/`H011`/`H113` 须按 hunk 分段归属，不得整行/整文件标已验。

**联合验证前：**

6. **单一联合候选构建**（GAP-11）：一次性覆盖 B1/B2/库浏览器/S06/G6/S14/M-BUG-03 的联合树 + 全量编译 + `compileDebugAndroidTestKotlin` + 全量 JVM；由单一集成者串行执行（并行窗口只跑各自定向）。
7. **设备层证据**（GAP-03/GAP-04/GAP-06/GAP-07）：G6 手势/TalkBack/持久化、S06 冷启动第一帧、S14 进程 kill/原子性、M-BUG-03 SAF/UI/MuMu——须由持设备窗口在**真机**（非 MuMu）或明确限定 MuMu 范围执行，并如实标注范围。**注意** qahost runner 用隔离 Application，**不能**冒充生产 `App.kt` 启动链路证据。
8. **`orig-65` 逐项证据**（GAP-10）：为 67 行（含 10 项「七类摘要未覆盖」）补齐逐项证据，替换「全部有意演进」的笼统表述。

**精确本地提交前：**

9. **完整精确纳入/排除清单**：逐文件逐 hunk 列明纳入与排除，SHA 固化，并说明混合行处理。
10. **用户明确授权**（GAP-12）：未获授权**不得** stage/commit/push；唯一已验证可提交候选为 B1+B2+库浏览器联合候选（其余为 `EVIDENCE_PENDING`）。

---

## E. 对既有报告的具体勘误清单

| 出处 | 原文（需改） | 更正 |
|---|---|---|
| `2026-09-30-stage-e-asset-hold-reconciliation.md` §0.3/§2.2/§3.1 | G6「22+5，E 盘已分层 16/22 + 4/5，剩余 6 文件待拆分」 | 已被 `stage-e-g6-integrated-20260930` 取代：**22/22 + 5/5 集成候选建成，49/49 JVM**；「余 6 文件待拆」**不再成立** |
| 同上 §1.2 | 「180 行其余 58 = 候选分组（**55**）+ 首批已验（**3**）」；「**55 + 122 + 3 = 180**」 | 前缀实算为 **54 候选 + 4 已验**（混合行 `H007` 归已验）；旧法掩盖 `H007` 混合事实 |
| 同上 §1.2 | 「180 行保留 HOLD 122 = 37+16+69」 | 该拆分与谱系自洽，保留；但须补注 122 之外存在 1 行混合 |
| `2026-09-30-stage-e-asset-hold-reconciliation.md` §4.2 | 「原目录独有 **65 项**差异…**全部**为有意演进…如实注明为『有意的架构演化』」 | 物理表为 **67 行**；其中 2 项为**确认覆盖**（`ProfileUtils.kt`/`DurationDisplay.kt`），10 项标「七类摘要未覆盖」；「全部」需替换为**逐项证据** |
| 同上 §4.1 #7/#8/#9 | 「**永久隔离！严禁入库！**」「**直接丢弃**」 | 属处置建议；本阶段**原样保留**，第 9 项真实路径待用户确认，删除需授权 |
| `2026-09-30-stage-e-s14-isolation.md` §1.3 | androidTest 两文件「**HEAD 已跟踪**，无工作树修改」 | 实况：`LocalZipBackupPersistentRestoreTest.kt`、`LocalExportRoomRollbackTest.kt`、`LegadoMarkerRealImportTemporaryTest.kt` 在 HEAD **未跟踪**（`??`） |
| 同上 §0 / §7 | 「可进入后续联合候选，待 androidTest 设备验证」 | 补注：**S14-only 候选下 androidTest 构建 FAILED（25 错误）**，无测试 APK；「待设备验证」目前**不可达**，须先闭合 R3 依赖 |
| `2026-09-30-stage-e-replan-codex-review.md` | 已正确指出 122/54/4、67 行、S14 25 错误 | 与本轮实算一致；本报告为其补上**逐项集合差**与**表 SHA** |
| `2026-09-29-stage-e-mbug03-isolated-result.md` §归属 | H180 标 B2、两文件标 EXCLUDED | 已在 `matrix-corrections-20260930.csv` 登记（3 行）；**尚未回写**权威矩阵（GAP-08） |

---

## F. 纪律与自证

- 共享恢复仓库内**唯一写入 = 本文件**（`docs/coordination/decisions/2026-09/2026-09-30-stage-e-freeze-scope-and-asset-corrections.md`）。
- E 盘全部写入在 `E:\creation-reading-assistant\stage-e-freeze-ledger-20260930\`（账本 + 复算脚本，新建）。
- **未**改任何产品源码 / 测试 / 配置 / CSV / 其他窗口报告 / handoff；**未** stage / commit / push / reset / checkout / stash；**未**迁目录；**未**操作设备 / MuMu / ADB；**未**触碰原仓库、正式包、正式书库；**未**删除或搬移任何目录；原矩阵与资产**全部保留**。
- 本轮**未**重跑任何既有构建/测试；49/49、122/54/4、67/92/65、25 错误等均由**原始 XML/CSV/日志**重新求和或集合运算得出，算法与入口见 §A.0。
- 未据模型或报告标题裁定通过；`EVIDENCE_PENDING` 项一律标注「不得当作通过」。
- 全文只用中性标识，未记录任何真实书名。
