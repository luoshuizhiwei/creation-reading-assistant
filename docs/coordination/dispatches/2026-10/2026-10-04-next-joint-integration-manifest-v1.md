# 联合候选 → 恢复仓库 精确集成清单

## 任务编号/版本

`CRA-JOINT-INTEGRATION-MANIFEST-v1`，2026-10-04，**只读**。绑定候选 `E:\creation-reading-assistant\next-round-joint-final-v3`（即 `CRA-JOINT-FINAL-v3`）。本清单只整理「候选 → 当前 D 工作树」的精确差异与 WIP 保全判断，不应用文件、不暂存/提交、不覆盖共享 WIP。

## 输入指纹

- 恢复仓库 HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（开工=收工一致）；暂存区 0 条（开工=收工）。
- 候选输入（sha256，只读）：

| 文件 | sha256 |
|---|---|
| `freeze/base-sha.json`（基树 886 文件清单） | `e1083d4a230a48b61ab2e7d19721239e2a4d052711c649e36dceb488021ee57a` |
| `freeze/integrated-inputs.json`（本轮 12 路径 delta） | `25f84114def07ef03720a78edaead6adf47724ed405d72778b0913a46cba39c7` |
| `freeze/build-binding.json` | `e06a4bd6fcd947a9b72a63e59230f205cf8abc1c61563c8123a80fa79c1bec68` |
| `freeze/build-verification.json` | `6ac107c06c5f9723238109f1c110f75f1321775e913c6ba7b53f1298c76efa0c` |
| `execution.json` | `f49bb8a2736fb5404245754bde049b77ef70fbab5a7f4ce67844594ee0110d2b` |
| `freeze/app-qahost.apk` | `2a1473b47c26998398a57b1fa34a55c5e30aaff302a024493136e4ad0711a990` |
| `freeze/app-qahost-androidTest.apk` | `98fc9cf4e44fb9e1b0437a18e82559bfe21fabe2b721897ff170b7325979c0d7` |

- 方法：三方逐文件 **归一化 git blob** 比对（`sha1("blob <len>\0"+LF 归一化内容)`，匹配仓库 `.gitattributes` 的 `* text=auto eol=lf`；`.bat/.cmd/.ps1` 的 blob 亦为 LF）。归一化实现已用 `git hash-object --path=` 对 `ReaderChrome.kt`/`EpubRepository.kt`/`gradlew.bat`/`app/build.gradle.kts` 四文件 4/4 校验一致。
- 对账：D 改 **198**（= `git status` M 198）、D 删 **3**（= git D 3）、D 未跟踪 **142 文件**（= git `??` 92 个目录条目展开）。
- 规模：候选树 892 / D 工作树 1010 / HEAD(android) 872 / 并集 1029。

## 只读范围

- 只读：候选树、D 工作树、HEAD、既有报告与证据。
- 未执行：**未调用 ADB / MuMu / Gradle，未构建，未重跑** 303 JVM / 12 设备方法 / 55 驱动离线测试（复用既有匹配输入证据）；未修改产品源码、未做任何 Git 写操作。
- 唯一可写：`E:\creation-reading-assistant\next-round-joint-integration-manifest-v1\` 与本报告。

## 精确清单

### 总览（并集 1029）

| 关系 | 条数 | 含义 |
|---|---:|---|
| 相同（候选==D 逐字节） | 674 | 无操作 |
| 仅 D 存在（候选不含） | 136 | 保全，候选不动 |
| 冲突（候选≠D，双方都有） | 201 | 见下拆分 |
| 候选新增（D 无此文件） | 15 | 新增 |
| 候选恢复（HEAD 有、D 已删） | 3 | 需确认删除意图 |

**集成面（候选会改变 D）= 冲突 201 + 候选新增 15 + 候选恢复 3 = 219**；无操作 674。

### 冲突 201 拆分

| 子类 | 条数 | 判断 |
|---|---:|---|
| 候选改 & D==HEAD（干净） | 8 | **可原样采用** |
| 候选改 & D 有 WIP | 11 | **必须保全合并**（双向改动） |
| 候选新增 & D 新增（均不在 HEAD） | 1 | 双向新建，需决策 |
| 候选==HEAD & D 有 WIP | **181** | **回退风险**：整树覆盖会用 HEAD 版覆盖 D WIP（D 相对 HEAD 合计 **+14571/−6967 行**） |

### 本轮 delta 12 路径（`integrated-inputs.json`）判定

| 路径（android/ 前缀省略） | 关系 | 候选vsHEAD | DvsHEAD | 建议 |
|---|---|---|---|---|
| `feature/library/ShelfImporter.kt` | 冲突 | 候选改 | D已改(WIP) | 必须保全合并 |
| `feature/reader/EpubRepository.kt` | 冲突 | 候选改 | D已改(WIP) | 必须保全合并 |
| `feature/reader/EpubParser.kt` | 冲突 | 候选改 | D已改(WIP) | 必须保全合并 |
| `feature/reader/FileOps.kt` | 相同 | 候选新增 | D新增(未跟踪) | 可原样采用（候选==D） |
| `feature/reader/EpubSwapGate.kt` | 候选新增 | 候选新增 | D无此文件 | 可原样采用 |
| `ui/viewmodel/ShelfImporterTest.kt` | 冲突 | 候选改 | D已改(WIP) | 必须保全合并 |
| `feature/reader/EpubRepositoryReplaceTest.kt` | 冲突 | 候选新增 | D新增(未跟踪) | 双向新建，需决策 |
| `feature/reader/EpubRepositoryReplaceRollbackTest.kt` | 候选新增 | 候选新增 | D无此文件 | 可原样采用 |
| `feature/reader/EpubRepositoryConcurrencyTest.kt` | 候选新增 | 候选新增 | D无此文件 | 可原样采用 |
| `acceptance/EpubReplaceFreshSourceAcceptTest.kt` | 候选新增 | 候选新增 | D无此文件 | 可原样采用（仪器） |
| `acceptance/MdTrueChapterAcceptV1Test.kt` | 候选新增 | 候选新增 | D无此文件 | 可原样采用（仪器） |
| `ui/screen/reader/ReaderAnnotationPendingConsumptionTest.kt` | 候选新增 | 候选新增 | D无此文件 | 可原样采用（仪器） |

**本轮 delta 的关键事实**：`FileOps.kt` 在 D 中已存在（未跟踪）且与候选**逐字节相同**；`EpubSwapGate.kt` 及 3 个回滚/并发/验收测试为 D 所无的新文件；`ShelfImporter/EpubRepository/EpubParser/ShelfImporterTest` 四路径 **D 已有独立 WIP 改动**，候选版与 D 版不同，不能直接覆盖。

### 候选相对 HEAD 的改动面（46 路径）

候选改 25 + 候选新增 21 = 46。按来源交付归属（见「证据绑定」）：
- `integration-ready-v1 (25)`：18
- `sync-wip-reconcile-v1 (7)`：7
- `rollback-wip-reconcile-v4`：9
- `md-true-chapter-accept-v1 (6)`：6
- `joint-acceptance-tests-v2`：3
- 基树继承、具体交付待核：6（见「未解决边界」HOLD）

### 建议分类汇总

| 建议 | 条数 |
|---|---:|
| 可原样采用（候选==D，no-op） | 674 |
| 可原样采用（D 干净，候选改） | 8 |
| 可原样采用（候选新增） | 15 |
| **必须保全合并（候选覆盖会回退 D WIP）** | **193** |
| HOLD（D 已删、候选要恢复，需确认删除意图） | 3 |
| 排除/保全（D 独有，候选不含） | 136 |

> 说明：193 = 冲突中 D 有 WIP 的 181（候选==HEAD）+ 11（候选改）+ 1（候选新增 vs D 新增）。

## WIP 冲突

**核心结论：候选不是 D 工作树的超集。** D 工作树存在大规模未提交 WIP（198 改 / 3 删 / 142 新文件），而候选基于独立整理树构建，**不含其中 181 个已改文件的 D 内容**——整树覆盖会用 HEAD 版回退这 181 文件（+14571/−6967 行）。因此集成**必须逐路径处理，禁止整树覆盖**。

- 真双向合并（候选改 & D 有 WIP，11 条）：`SyncRepository.kt`、`ShelfImporter.kt`、`EpubParser.kt`、`EpubRepository.kt`、`WebDavBackup.kt`、`ProfileRoute.kt`、`ReaderProgressActions.kt`、`ReaderProgressEffects.kt`、`ProfileViewModel.kt`、`ProfileViewModelTest.kt`、`ShelfImporterTest.kt`。逐路径 `候选 vs D` hunk 已导出到 `patches/cand_vs_D/`（例如 `SyncRepository.kt +294/−105`、`EpubRepository.kt +108/−138`、`ReaderProgressEffects.kt +106/−7`、`ShelfImporter.kt +56/−35`）。
- 双向新建决策（1 条）：`EpubRepositoryReplaceTest.kt`——候选与 D 各自新建且内容不同（候选 +299/−2 相对 D）。
- 回退风险（181 条）：候选版==HEAD，D 已改。按目录集中于 `ui/screen/reader`（20）、`ui/screen/reader/sheets`（17）、`ui/screen/profile`（14）、`ui/screen/shelf`（13）、`feature/reader/pager`（10）等；全量清单见 `revert_risk_numstat.txt`。
- D 已删、候选要恢复（3 条）：`ProfileHomeScreenGestureTest.kt`、`ProfileHomeStatPlaceholderSemanticsGuardTest.kt`、`ReaderInspirationSnapshotSaveTest.kt`——候选侧存在且==HEAD，D 已删除；**需确认删除是否为有意**，未确认前 **HOLD**，不擅自选边。
- 语义核对边界：本轮只做**字节级**三方比对，未对 11 条真合并路径逐 hunk 判定「D 是否包含候选语义」。**不得以「候选测试绿」推断可覆盖 D WIP**；合并前需逐 hunk 语义核对（`patches/cand_vs_D/` 已备）。

## 排除项

| 类别 | 对象 | 处置 |
|---|---|---|
| 任务后缀 | `app/build.gradle.kts` 的 `applicationIdSuffix=.qahostjoint20261004v3` | 仅 E 候选设备批次专用；集成到 D **必须改回** `.qahost`（D 现值）。该文件其余差异为 qahost 隔离注释 |
| 构建本地 | `local.properties`（`sdk.dir=…`） | 排除，非产品 |
| 构建产物/缓存 | `.gradle/`、`.kotlin/`、`app/build/`、`build/` | 候选树内含，排除，不纳入集成 |
| 设备/构建日志 | D 工作树顶层 **49 个 `*.log`**（未跟踪） | 排除，保持原状 |
| 缓存/临时诊断 | `scripts/__pycache__/*.pyc`、`scripts/ui_now.xml`、`scripts/diag_usb_confirm.py`、`scripts/tap_usb_install_confirm.py` | 排除 |
| 二进制/资源 | 未变的 `res/`、字体、图标 | 无操作 |

**仪器测试是否必要交付**：`androidTest` 3 文件（`EpubReplaceFreshSourceAcceptTest`、`MdTrueChapterAcceptV1Test`、`ReaderAnnotationPendingConsumptionTest`）属 `joint-acceptance-tests-v2` 交付，编码联合验收合同（真实 ZIP 解压校验、真实表名清理、SE4 待消费断言）。**判定为候选的必要交付之一**，理由：它们是 final-v3 设备批次（MuMu 12 方法 / 10 行为）的合同源码，且 `joint-acceptance-tests-v2` 报告明确其「源码/编译/离线合同」已接受、`device_methods=ALL NOT RUN`。但三者绑定 E 候选设备批次，**在 D 集成后是否长期保留需单独决定**（它们不进主 APK，只影响 androidTest 变体）。

## 证据绑定

本清单复用既有匹配输入证据，**未重跑**：

| 交付 | 路径数 | 既有证据（未重跑） |
|---|---|---|
| `integration-ready-v1` | 25 | 6 套定向 JUnit 153/153，新接缝 7/7；`E:\…\next-round-integration-ready-v1\final-manifest.json`（`device_NOT_RUN_on_merged_input=true`） |
| `sync-wip-reconcile-v1` | 7 | 55/55（SyncRepositoryLocalFacts 25 + WebDavCompatibility 27 + ProfileViewModel 3） |
| `md-true-chapter-accept-v1` | 6 | 90/90 JVM + 真实跨章 5/5（滚动模式） |
| `rollback-wip-reconcile-v4` | 9 | 149/149（7 套件） |
| `joint-acceptance-tests-v2` | 3 | 源码/编译/离线 ZIP+SQLite 合同；设备 ALL NOT RUN |
| final-v3 联合 | — | 20 套件 **303/303** JVM；MuMu 12 方法 / 10 行为（EPUB2+SE4 3+MD 滚动5）；恢复独立核验 |

- final-v3 绑定证据：`freeze/build-verification.json`（303/303）、`freeze/device-contract-result.json`、`device-md-r4/summary.json`、`freeze/root-final-device-verification.json`。
- **口径限制**：以上为**限定**证据（EPUB/SE4/MD 滚动），**不代表** UI/SAF、实体手机、手机窄布局、分页、全产品。**不得**将「实现交付 100%」「A–E 恢复 100%」「限定 MuMu 10/10」写成全产品 100%。

## 未解决边界

1. **MD 分页调用链接缝——等待 `CRA-MD-PAGED-CHAPTER-FIX-v1` 最终冻结与 Codex 审查。** final-v3 报告记录：MD 默认左右翻页下点 TOC 第 2 章时 toolbar 显示第 2 章但正文/底栏仍第 1 章（失败批 `device-md-r3`）。**本清单绑定 final-v3，不覆盖该未来修复**；分页修复冻结后，需对其改动路径（`ReaderScreenDerivedState`/`PagedReaderController`/TOC 消费链等）做**增量刷新**。
2. **HOLD：6 条基树继承路径归属待核。** `ReaderAnnotationNavigation.kt`、`ReaderAnnotationNavigationTest.kt`、`ProfileHomeScreenGestureTest.kt`、`ProfileHomeStatPlaceholderSemanticsGuardTest.kt`、`ReaderInspirationSnapshotSaveTest.kt`、`LocalZipRestoreAcceptanceTest.kt` 存在于候选基树（`base-sha.json`）但不属于本轮 delta，也未见任何已读交付清单命名。**所需证据**：`next-round-integration-build-accept-v2` 基树（885 文件）及其上游交付 manifest，用以确认来源与是否含 WIP。
3. **HOLD：3 条 D 删除文件的删除意图未证。** 见「WIP 冲突」末条。
4. 11 条真合并路径**未做逐 hunk 语义判定**，仅备 diff；未证明「D ⊇ 候选语义」。
5. D 工作树 WIP 的**最终归属（用户本人 / 其他窗口）未核实**；本清单只做「D 相对 HEAD 已改」的事实记录，不推断作者与意图。
6. 未核对 `.codex/`、根文档、桌面端 `src/`/`electron/` 等候选未覆盖范围（超出候选与 android/ 边界）。

## 下一步

1. **交 Codex 审查本清单**（`manifest.csv`/`manifest.json`/`summary.json`/`delta12.csv`/`patches/cand_vs_D/`/`revert_risk_numstat.txt`）。
2. 审查后由**单一集成 owner**按建议逐路径处理：8 条安全采用 + 15 条新增 + 674 无操作可批量；**193 条必须保全合并**逐条核对；3 条恢复 HOLD 待确认；136 条仅 D 保全。
3. `app/build.gradle.kts` 集成时**改回 `.qahost`**，不得带入 `.qahostjoint20261004v3`。
4. 先窄定位 MD 分页接缝（源码分析可独立进行），**分页修复冻结并审查后**，再对变化路径做本清单的**增量刷新**，然后才决定永久精确集成范围与操作授权。**本清单不继承任何旧提交授权。**

---

### 工件（`E:\creation-reading-assistant\next-round-joint-integration-manifest-v1\`）

- `manifest.csv` / `manifest.json`：1029 行逐路径清单（HEAD blob / 候选 blob / D blob、关系、双方 vs HEAD、来源交付、建议）。
- `summary.json`：计数汇总。
- `delta12.csv`：本轮 delta 12 路径判定。
- `patches/cand_vs_D/*.diff`：20 条「候选带来改动」路径的 `候选 vs D` hunk。
- `revert_risk_numstat.txt`：181 条回退风险路径的 D vs HEAD 行数。
- `exclusions.txt`、`execution.txt`：排除项与执行指纹。
- `compare2.py` / `finalize.py` 等：可复跑的只读比对脚本。
