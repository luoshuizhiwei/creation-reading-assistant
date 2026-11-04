# 联合候选 → 恢复仓库 精确集成清单 v2（机械分类纠正 + 真交集逐 hunk 语义分析）

## 任务编号/版本

`CRA-JOINT-INTEGRATION-MANIFEST-v2`，2026-10-04，**只读**。执行应用/模型：ZCode / GLM（account:zai-start-plan）。绑定候选不变：`E:\creation-reading-assistant\next-round-joint-final-v3\android`；分页候选 `E:\creation-reading-assistant\next-round-md-paged-chapter-fix-v1\android` 作为**单独增补输入**（不并入本清单的候选绑定）。本任务只输出清单与语义分析，不实施合并、不构建新候选。

## 输入指纹

- 恢复仓库 HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（开工=收工一致）；暂存区 0 条。**未执行任何 Git 写操作**（仅 rev-parse/status/ls-tree/cat-file 只读）。
- 候选 final-v3 七件输入 SHA 与 v1 清单一致（base-sha.json `e1083d4a…`、integrated-inputs.json `25f84114…`、build-binding.json `e06a4bd6…`、build-verification.json `6ac107c0…`、execution.json `f49bb8a2…`、宿主 APK `2a1473b4…`、测试 APK `98fc9cf4…`），本轮实读未变。
- 分页候选增补 SHA 见 §五；分页修复/验收状态按 Codex 核查口径引用：**功能已核 5/5、恢复待闭合**（夜间模式口径由唯一设备 owner 处理），不据此宣称统一候选验收完成。
- 旧清单工件 `E:\…\next-round-joint-integration-manifest-v1\`（只读，未运行其 compare 脚本，v1 工件零改动）。

## 所有权与改动

只写：`E:\creation-reading-assistant\next-round-joint-integration-manifest-v2\`（compare2_v2.py、build_manifest_v2.py、gen_semantic_diffs.py、manifest.csv/json、evidence/、execution.json）与本报告。D/E 产品树、旧清单、旧报告、旧证据全部只读。无 ADB/设备/构建/源码实施；未改 current.md/TODO/公共配置。

## 环境

Windows / Git Bash；Python 3.13（仅 stdlib：hashlib/csv/json/os/subprocess）；git 只读子命令。未启动 JDK/Gradle，未连接设备。

## 一、机械分类纠正（对 v1 六项）

### 1. 181 项 = 保留D/no-op，不是 181 个合并任务

v1 的 181 条（候选==HEAD、D 有 WIP）在 v2 manifest 中全部重标为 `保留D/no-op(候选==HEAD,合并无意义)`。语义：候选在这些路径上不携带任何 HEAD 之外的改动，集成时**什么都不做**即自动保全 D WIP；唯一风险是"整树覆盖"这种被禁止的操作方式本身。v2 不为其列任何合并任务；清单中该 181 行的推荐动作为空操作。计数：**181/181 重标完成**。

### 2. 674 项"相同" = 归一化 blob 相同 ≠ 逐字节

v2 独立重算（compare2_v2.py 同时记录**原始 SHA-256** 与**归一化 git blob**）：674 = **670 逐字节相同** + **4 个 CRLF-only 文件**（blob 相同、原始字节不同）。4 文件原始 SHA 双侧分开记录于 manifest（cand_sha256/dw_sha256）且**不统一行尾、不 renormalize**：

| 文件 | 候选 SHA-256（前16） | D SHA-256（前16） | 行尾 |
|---|---|---|---|
| `data/local/dao/TaxonomyDao.kt` | `78c1b3da1e8d29d9` | `9f638576203c54a2` | 候选 LF / D CRLF |
| `feature/log/AppLog.kt` | `c0f8c212f91b5b25` | `dd6831bde51d38de` | 候选 LF / D CRLF |
| `res/values/colors.xml` | `9d4e153e2e1b120e` | `001ea5f9910042f5` | 候选 LF / D CRLF |
| `test/…/ReaderConsumerSnapshotWiringGuardTest.kt` | `dd43f8d5fb8f6bf8` | `0d53b943cee4b6f9` | 候选 LF / D CRLF |

v2 分类将其单列 `重合-CRLF-only(内容同,行尾不同;不renormalize)`。处置建议：集成动作=no-op（D 内容语义不变）；若集成者未来统一行尾，属独立授权决定，本清单不执行。另：v1 所述第 4 文件名在 Codex 核查中记为 `ReaderConsumerSnapshotWiringGuardTest.kt`，实算确认无误（v1 报告文本 `ReaderConsumerSnapshotWiringGuardTest` 与 manifest 内路径一致）。

### 3. 142 vs 92/93 未跟踪统计差异：已复现并解释

- **命令与时间**：v1 于 2026-10-04 早间运行 `git status --porcelain -- android/`（默认 `--untracked-files=normal`，目录折叠）得 **92 条目**；展开为文件后 **142 = 93 产品文件 + 49 个 android/ 顶层 `*.log`**。本轮复算：`git status --porcelain --untracked-files=all -- android/` = **93 文件**，与 v1 的 93 逐项比对**0 漂移**（v1 有而当前无=0，当前有而 v1 无=0）。
- **49 的来源**：v1 的 `compare2.py is_excluded()` 只按**目录**（build/.gradle/…）与单文件名（local.properties）排除，**未排除 android/ 顶层的 49 个 `*.log`**（AT-COMPILE-*.log、F1/F2/F3-*.log、GREEN/RED/FINAL-*.log、S15F3-*.log 等，逐名清单在 `evidence/compare2-v2.json` 的 `android_top_level_logs.names`）。它们进入 v1 的 D 工作树文件集（1010=961+49），又全部落入"仅D存在"，把该类从 87 抬成 136。
- **纠正**：v2 排除规则新增"android/ 顶层 `*.log`"，并显式记录该排除；**仅D存在 136 → 87**（v2 manifest），49 个日志保持 D 原状、不入集成清单。当前 git 口径：M=198、D=3、??=93（uall），与 v1 对账的 M/D 完全一致。

### 4. delta12 ≠ semantic12（两组"12"分开命名）

- **delta12**（`integrated-inputs.json`，v1 已录）：4 D-WIP 冲突 + 1 双方新增 + 6 候选新增 + 1 逐字节相同（FileOps.kt）。它是"本轮交付的路径清单"，**不是**语义合并工作量。
- **semantic12**（v2 定义，`in_semantic12=true`）：**11 双方改变 + 1 双方新增不同 = 12 个需要语义判定的文件**（§二）。
- 关键区别：v1 delta12 中的 6 个 integration-ready 依赖文件（AppLayout/LayoutTokens/ProfileUiState/SyncSubPage/WebDavSubPage/ProfileSyncEngine）经实算 **D 现值与候选逐字节相同**（D-WIP 已收敛到候选同值）→ v2 归入 `重合(双方改至同值,无操作)`，**零合并工作**；FileOps.kt 双方新增且逐字节相同 → 同归重合类。semantic12 与 delta12 的交集仅 5 路径（ShelfImporter/EpubRepository/EpubParser/ShelfImporterTest + EpubRepositoryReplaceTest），另有 6 路径 semantic12 成员来自 integration-ready/sync-wip/md-accept 等早期交付。两组在 v2 manifest 以 `in_semantic12` 字段与分类名严格分开。

### 5. 6 HOLD 补证结果：3 项有交付链来源，3 项维持删除意图 HOLD

| 文件 | v2 结论 | 证据 |
|---|---|---|
| `ReaderAnnotationNavigation.kt`（候选新增） | **来源已核**：md-annotation-fix-v1 | `E:\…\mdfix-review-20261003\payload32-preparation\manifest.json` 明确命名（origin=md-annotation-fix-v1，candidate_sha256 在案） |
| `ReaderAnnotationNavigationTest.kt`（候选新增） | **来源已核**：md-annotation-fix-v1 | 同上 manifest 命名 |
| `LocalZipRestoreAcceptanceTest.kt`（候选新增） | **来源已核**：j3-n1-sandbox-build-v1 新增驱动类 | `2026-10-02-next-j3-n1-sandbox-build-v1.md` 记为"新增驱动类"并附 SHA `3bc29768…`；`2026-10-04-next-joint-acceptance-tests-v2.md` 引用其编译警告 |
| `ProfileHomeScreenGestureTest.kt` | **维持 HOLD**：D 已删、候选==HEAD，删除意图未证 | 无任何已读交付链记录该删除；v2 不推断归属 |
| `ProfileHomeStatPlaceholderSemanticsGuardTest.kt` | **维持 HOLD** | 同上 |
| `ReaderInspirationSnapshotSaveTest.kt` | **维持 HOLD** | 同上 |

以上均只按已有相关交付链补证，未向任何任务"推断归属"；3 个删除意图待核文件在 v2 manifest 保持 `删除意图待核`，集成动作=不恢复、不删除、等 Codex/用户裁决。

## 二、semantic12 真交集逐 hunk 语义分析（11 改 + 1 增）

方法：对每文件生成三份 diff（HEAD→候选 / HEAD→D / 候选→D，LF 归一后），存 `evidence/diffs/`（36 份）；行数摘要 `evidence/semantic12-hunks.json`；**函数/符号级结论** `evidence/semantic12-analysis.json|csv`（12 行 × 11 字段：关系/hunk 数/两侧需求/语义关系/推荐动作/需保留的 D 逻辑/定向验收/开放风险）。**全部结论基于函数与 hunk 证据，不以 diff 行数或"自动三方合并可成功"作语义安全依据。**

汇总（详细证据见 analysis 表，此处列结论）：

| # | 文件 | 关系 | 结论 |
|---|---|---|---|
| 1 | `SyncRepository.kt`（+341/-65 vs +151/-64） | 互补偏包含 | 候选 = R1 合同 + backfill 增强（`backfillBookPayload(e, deviceId)` 三函数带 deviceId 透传与 `skipReasonMissingFilePath` 失败跳过）；D = R1 + 旧签名 `backfillBookPayload(e)`。**采纳候选**；D pull 门控块与候选逐字一致已核 |
| 2 | `ShelfImporter.kt`（+160/-19 vs +139/-19） | 冲突 | 候选 v4 阶段敏感回滚（`backupMayHoldOurPartialBytes` + SWAP 三态）是 D v1 无条件 `internalFile.delete` 的直接升级 = three-deliveries P1 修复。**采纳候选** |
| 3 | `EpubParser.kt`（+116/-15 vs +90/-15） | 包含 | 候选 = D 超集（`requireFreshSource` 参数）。**采纳候选** |
| 4 | `EpubRepository.kt`（+173/-16 vs +203/-16） | 冲突 | 候选 v4 `EpubSwapGate.withGate` + `verifyFilesEqual` 逐字节核对 替代 D v3 的 `restoreBackupAfterFailedPromotion`/`contentEquals`；语义对偶、候选更强。**采纳候选**（`computeFileHash` 两侧同名同语义已核） |
| 5 | `WebDavBackup.kt`（+410/-166 vs +316/-159） | 互补偏冲突 | 候选 = integration-ready reviewer 两 P2 修复后（PUT 重试有界、不包整个 MKCOL）；D = 修复前。两版 `withRetry` 签名一致、`ensureCollection` 等六函数同名。**采纳候选** |
| 6 | `ProfileRoute.kt`（+147/-16 vs +70/-12） | **互补（真合并）** | 候选=同步链；D=N1-repair-v1 的 `ZipRestoreInterruptedBanner`+`zipRestoreState` 三段（cand2dw +4/-77 全为 D 独有）。**合并：保 D 横幅 + 取候选同步链**；覆盖 D 将回退整包恢复中断提示 |
| 7 | `ReaderProgressActions.kt`（+31/-0 vs +65/-3） | **互补（真合并）** | 候选加 `markdownVisibleSourceOffset`；D 加 `atChapterEnd` 分支 + `txtScrollChapterTargetOffset`。**两者都保留**；改动区域在不同函数（cand2dw 已证） |
| 8 | `ReaderProgressEffects.kt`（+131/-9 vs +25/-2） | 包含偏一险 | 候选 SE4 = 完整 MD 回源修复（`applyMarkdownAnnotationPlan`，D 无）；D +25 行为 `pendingInitialPosition` markdown 门控差异。**采纳候选**，但该门控行需人工确认非 D 独立修复 |
| 9 | `ProfileViewModel.kt`（+138/-35 vs +118/-35） | **互补（真合并）** | 候选=同步构造链；D=`zipRestoreState`/`refreshZipRestoreState` 纯增量。**合并：候选基座 + D zip 恢复状态块** |
| 10 | `ProfileViewModelTest.kt`（+4/-0 vs +40/-3） | 包含 | D 版 = 候选适配（写法不同、语义同）+ 1 条 `rebuild search index` 新测试。**以 D 版为基座** |
| 11 | `ShelfImporterTest.kt`（+1101/-3 vs +844/-3） | 包含 | 候选 39 用例 ⊃ D 26 用例（`comm -13` 为空）。**采纳候选** |
| 12 | `EpubRepositoryReplaceTest.kt`（双方新增：候选 28 vs D 21 用例） | 包含 | D ⊂ 候选（`comm -13` 为空；候选多 7 条 fresh-landing 清理/等长错字节拒绝/守卫前移）。**采纳候选** |

**结论分布：7 项"候选为超集/直接升级、可原样采纳候选"（1/2/3/4/5/8/11/12 中的包含与升级项），4 项"互补、需真正语义合并"（6/7/9/10），0 项"独立可保留不合并"，0 项"来源未知"。** 合并工作量集中在 4 个互补文件；其余 8 个文件的单向采纳仍须在合流时以定向 JVM 复核（见各文件 directed_verification 字段），不得以本清单替代合流后验证。

## 三、v2 分类汇总（并集 980，计数闭合）

| v2 分类 | 条数 | 说明 |
|---|---:|---|
| 保留D/no-op（双方逐字节相同且==HEAD 或双方同值==HEAD） | 659 | 含 v1"相同"主体 |
| 保留D/no-op（候选==HEAD、D 有 WIP） | **181** | **零合并任务**（纠正项 1） |
| 重合（双方改至同值，无操作） | 11 | 含 6 个 integration-ready 依赖文件 + FileOps 等 |
| 重合-CRLF-only（内容同行尾异） | 4 | 纠正项 2；不 renormalize |
| 仅D存在（保全） | 87 | 136−49 顶层 log（纠正项 3） |
| 可采用但未授权写入（候选改、D 干净） | 8 | build.gradle.kts + MarkdownDocument/JsonBridge/LocalZipBackup 及两测试等 |
| 候选新增（未授权写入） | 15 | 含 3 个 HOLD 来源已核文件 + 仪器测试等 |
| 删除意图待核 | 3 | 纠正项 5；不选边 |
| 需语义合并（双方改变） | 11 | semantic12 主体（§二） |
| 需语义合并（双方新增不同） | 1 | EpubRepositoryReplaceTest |
| **合计** | **980** | 闭合校验通过（build_manifest_v2.py 内 assert） |

与 v1 的映射：674=670+4；136=87+49(log 排除)；201 冲突=11+8+181+1；15 不变；3 不变；合计 1029−49(log)=980。**排除规则**：build/.gradle/.kotlin/.idea/.git/__pycache__ 目录、local.properties、android 顶层 `*.log`（49）、`scripts/ui_now.xml` 等；`app/build.gradle.kts` 的 `.qahostjoint20261004v3` 后缀为候选专用配置（集成必须改回 `.qahost`），在 manifest 中属"可采用但未授权写入"并带该警示。

## 四、验收资产与候选专用配置（在 15+8 内的进一步标注）

- **验收资产**（androidTest 仪器类，合同源码，永久纳入待决）：`EpubReplaceFreshSourceAcceptTest`、`MdTrueChapterAcceptV1Test`、`ReaderAnnotationPendingConsumptionTest`（v1 已录 joint-acceptance-tests-v2）+ `LocalZipRestoreAcceptanceTest`（本轮补证来源 j3-sandbox-build）。四者只影响 androidTest 变体，不进主 APK；是否长期保留由 Codex/集成者在合流时单独决定。
- **候选专用配置**：`app/build.gradle.kts` 后缀 hunk（8 类中唯一"可采用但需改值"的文件）。分页候选的 `.qahostmdpagedv1` 同理（§五）。
- 其余 8 类"可采用"与 15 类"新增"为常规产品/测试源码，合流授权由 Codex 单独给出；本清单**未获任何写入授权**。

## 五、分页增补（相对 final-v3，独立绑定）

实算 `next-round-md-paged-chapter-fix-v1\android` 相对 `next-round-joint-final-v3\android` 全树差异**恰为 4 路径**（无删除、无其他改动，compare2-v2.json 双树逐文件 SHA 比对）：

| 路径 | 关系 | final-v3 SHA | 分页候选 SHA | 判定 |
|---|---|---|---|---|
| `feature/reader/pager/MarkdownChapterSource.kt` | 产品修复 | `42934a08…` | `baab0fbc…` | D 树现值 == final-v3（`42934a08…` 实算一致）→ **修复与 D 当前逻辑相容**：改动只作用 `isWholeDocumentParse=true` 分支（整本解析章视图平移），流式路径与滚动端不经过该 source；D 树该文件无本地 WIP，可直接采纳候选版 |
| `app/build.gradle.kts` | 隔离后缀 | `.qahostjoint20261004v3` | `.qahostmdpagedv1` | **候选专用配置**：仅服务分页候选设备批次；永久产品不得带入任一任务后缀 |
| `test/…/pager/MarkdownChapterSourceTest.kt` | 新增 JVM 测试 | — | `6fd7b462…` | **可纳入**：2 用例（章内坐标契约/全局查找一致），修复前红后绿，随产品修复走 |
| `androidTest/…/acceptance/MdPagedChapterFixV1Test.kt` | 新增仪器测试 | — | `23da7005…` | **仅作验收资产**：mdpc_00..90 合同源码；永久纳入待决（同 §四口径） |

增补表单列于本报告，**不并入** manifest.csv 的候选绑定列（`in_paged` 字段仅作参考记录）；v1 清单不含分页修复——v1 报告与 Codex 核查均已声明，v2 不假装其已包含。分页状态引用：**功能已核（JVM 15 套件 111/111 + 五条功能旅程 5/5）、恢复待闭合**（夜间模式来源/口径由唯一设备 owner 按 Codex 核查要求处理）；本清单不据此宣称统一候选验收完成。

## 原始证据

- `E:\…\next-round-joint-integration-manifest-v2\`
  - `compare2_v2.py` / `evidence/compare2-v2.json`：独立三方比对（原始 SHA-256 + 归一化 blob 双指纹、CRLF-only 检测、49 log 排除、双树分页比对）。
  - `build_manifest_v2.py` / `manifest.csv` / `manifest.json`：980 行八类分类（含 `byte_relation`、`in_semantic12`、`in_paged` 字段）。
  - `gen_semantic_diffs.py` / `evidence/diffs/`（36 份）+ `evidence/semantic12-hunks.json`：semantic12 三向 diff。
  - `evidence/semantic12-analysis.json|csv`：12 行逐项结论（11 字段）。
  - `execution.json`：执行指纹、计数、HOLD 补证、口径说明。
- 只读引用：v1 全部工件、`docs/coordination/reviews/2026-10/2026-10-04-md-paged-and-manifest-codex-review.md`、final-v3/分页候选树、payload32 manifest、j3-sandbox-build/joint-acceptance-tests-v2 报告。

## 未覆盖项

- semantic12 中 8 个"单向采纳"文件的 D 侧最终核对（确认无清单外第四方补丁）属合流时动作，本清单只给出人工比对点（各 open_risk 字段）。
- 181 项的 D WIP 内容本身未逐文件阅读（仅记录 blob 关系与 +14571/−6967 行总量，沿用 v1）；其归属（用户/其他窗口）未核实。
- 分页修复的设备恢复闭合（夜间模式）在另一窗口处理；本清单不含设备操作。
- 未核对 `.codex/`、根文档、桌面端范围（超出 android/ 边界，沿 v1）。
- 不给全产品百分比；本清单为集成决策输入，不构成验收结论。

## 交付状态与下一步

**已交付待 Codex 审查。** 六项机械纠正全部落实（181 no-op 重标、674=670+4 拆分、142=93+49 解释、delta12/semantic12 分名、HOLD 3+3 补证/维持），12 真交集逐 hunk 语义分析完成（4 个互补文件给出精确合并动作与需保留的 D 逻辑）。下一步建议：① Codex 审查本清单与 semantic12 结论；② 分页恢复闭合后由 Codex 决定分页 4 路径增补的采用；③ 单一集成 owner 按八类分类执行（674+181+11+4 无操作、87+3 保全/待核、8+15 待授权、12 语义合并按 analysis 表逐项），`app/build.gradle.kts` 改回 `.qahost`。本任务就此停止，不实施、不授权、不提交。
