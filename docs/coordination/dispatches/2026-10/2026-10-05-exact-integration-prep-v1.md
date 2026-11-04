# 精确集成清单准备：Codex 只读核查

## 任务编号/版本

CRA-EXACT-INTEGRATION-PREP-v1；2026-10-05。执行标识：ZCode CLI；模型 new-provider-5/ling-3.1-flash；工作目录 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`。按交接入口"并行第二任务可只读准备精确 D 集成清单"派发；本任务停止于清单准备完成，不执行应用。

## 输入指纹

- 恢复仓库 D：HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区空（实读核实，未变动）。
- 已验统一候选：`E:\creation-reading-assistant\unified-candidate-integration-v1\android`；冻结依据 `E:\creation-reading-assistant\unified-v1-codex-review-20261005\verification.json`（SHA256 `4f41571676a3df7cf747c0b99b3757604fee0e8baf487ee3a223ae7e3dff442f`），`source_sha256` 978 源码文件，junit 424/0/0/0，manifest_rows 980（含 2 非源码行），mismatch 空；候选目录与冻结清单逐文件对账 978/978 一致（CRA-UNIFIED-OFFLINE-BINDING-v1 离线已验）。
- TXT-v3 冻结：`E:\creation-reading-assistant\txt-reentry-fix-v3\freeze\source-sha256.json` 980 条 = 统一 978（其中 ReaderScrollPosition.kt、ReaderProgressEffects.kt 两文件 SHA 与统一候选不同）+ 新增 2 个 TxtReentry 测试。
- 当前 TXT 候选 `E:\creation-reading-assistant\txt-reentry-fix-v3\android` 只读引用；**TXT-v4 正由另一窗口修改，本任务未读取其进行中文件、不作为冻结输入、未修改其任何文件**。

## 所有权与改动

- 本任务写入：`E:\creation-reading-assistant\exact-integration-prep-v1\`（清单 JSON、差异工件、分析脚本）与本报告（恢复仓库唯一写入）。未修改 D 产品源码、未应用 patch、未暂存/提交/push、未 reset/checkout、未构建、未执行设备/模拟器操作、未改动全局交接/TODO（由 Codex 维护）、未读取其他应用私有记忆库。
- 不把旧提交授权（e39a78d / 142 文件 433 hunk，已执行完毕）当作本轮权限；精确应用/Git 范围需当轮单独授权。

## 环境

Windows 10、Python 3.13、git 只读子命令（`show`/`ls-files`）、difflib 三方合并与 opcode 分析。未运行 Gradle、ADB、模拟器；未触碰真实数据。

## 执行与结果

### 1. 基线与逐文件对账（978 清单）

| 类别 | 文件数 | 说明 |
|---|---|---|
| 逐字节一致 | 939 | D 当前 == 候选，无动作 |
| clean-apply-candidate-bytes | 9 | D 与 HEAD 仅行尾差异（CRLF/mixed），候选相对 HEAD 有内容差；按候选字节应用即匹配冻结 SHA |
| clean-merge-equals-candidate | 3 | D 共享 WIP 是候选变化子集，三方合并（base=HEAD/ours=D/theirs=候选）结果 == 候选字节 |
| conflict-hunk-resolution | 6 | 34 冲突块，需逐块决议，不整文件覆盖 |
| new-file | 18 | 3 生产 + 9 JVM 单测 + 6 仪器测试 |
| superseded-replace | 1 | D 未跟踪旧版（1310 行）被候选（1607 行）严格超越：候选独有 7 测试方法，D 无独有方法 |
| txt-pending-v4 | 4 | 见下 |

### 2. TXT 四路径（待 v4 冻结后替换指纹）

| 路径 | 现状 |
|---|---|
| `ui/screen/reader/ReaderScrollPosition.kt` | D 与统一候选逐字节一致；TXT-v4 按 P1（仅 `target==units.lastIndex` 允许 clamp 分支）修改；最终指纹待 v4 |
| `ui/screen/reader/ReaderProgressEffects.kt` | D 为 WIP 版，三方合并结果==候选；TXT-v4 按 P2（恢复 effect keys 加入 bid 与 plainListState 实际身份）再改；最终指纹待 v4 |
| `ui/screen/reader/TxtReentryRestoreFlowTest.kt` | D/统一候选均无；随 v4 新增（v3 初版 freeze SHA `5cf6233b…`） |
| `ui/screen/reader/TxtReentryRestoreTest.kt` | D/统一候选均无；随 v4 新增（v3 初版 freeze SHA `7d9e9ea3…`） |

ReaderScrollPositionTest.kt 在 978 清单内且与候选一致，不属于待定范围。

### 3. 共享 WIP 交集（三方合并，base=HEAD e39a78d）

10 个交集文件：4 个合并结果==候选（ProfileRoute.kt 23 hunk、ReaderProgressActions.kt 12 hunk、ReaderProgressEffects.kt、ProfileViewModel.kt 27 hunk——D 的 WIP 改动已被候选包含，应用候选字节即同时保留两侧意图）；6 个真冲突共 34 块：

- **EpubParser.kt（5 块）**：候选 `EpubBook = EpubSwapGate.withGate { parseUnderSwapGate(...) }` 包装与 `parseUnderSwapGate` 内部函数、`landingCopy` 局部 suspend lambda（CRA-ROLLBACK-WIP-RECONCILE-v3 折法，注释称行为逐字保留）vs D 旧 `if (!reuseExisting) {` 结构。
- **EpubRepository.kt（8 块）**、**SyncRepository.kt（10 块）**：候选 R1 显式取值辅助与统一安全解析路径 vs D 旧合并路径。
- **WebDavBackup.kt（6 块）**：D 的 `withRetry {` 包装（40 行）vs 候选直接请求构建（28 行）；D 的 PROPFIND 连接测试 vs 候选 `ensureCollection` MKCOL；候选新增 `import okhttp3.HttpUrl.Companion.toHttpUrlOrNull`。
- **ShelfImporter.kt（4 块）**、**ShelfImporterTest.kt（1 块）**：候选新增 257 行（M-BUG-03 第二阶段按来源一键刷新、CRA-RESELECT-ROLLBACK 阶段语义）。

逐块 ours（D 当前 WIP）/theirs（候选）对照提取见 `diffs/conflict-extracts/`。

### 4. 交付项 / 临时工件 / 既有 WIP 分类

- **交付项**：18 个新文件（生产 EpubSwapGate.kt、LocalZipRestoreStateStore.kt、ReaderAnnotationNavigation.kt；9 JVM 单测；6 仪器测试）、9 个 clean-apply 文件、3 个 clean-merge 文件、1 个被超越测试、build.gradle.kts 2 个集成 hunk（release 签名 debug 兜底改为 `craAllowDebugSigningFallback` 显式 opt-in，否则产出未签名 APK；androidTest 依赖新增 `libs.okhttp.mockwebserver` 与 `io.mockk:mockk-android:1.13.12`，排除 junit-jupiter/junit-platform 组）。
- **临时工件（不默认永久应用）**：muMu/批次后缀、一次性仪器测试、夹具与驱动引用——扫描 38 个 delta 文件，仅 1 处良性注释提及 MuMu（LocalZipBackupTest.kt:531"DB 侧幂等由既有 MuMu 用例 6 与 SyncMergePolicy 证据覆盖"）；无 E 盘机器路径、无设备 serial、无原驱动引用。名称含 Temporary/Fixture 的 12 个清单文件在 D 与候选逐字节一致，保持现状，不新增不删除。
- **既有 WIP/删除/HOLD**：D 工作树 3 个已删除测试（ProfileHomeScreenGestureTest.kt、ProfileHomeStatPlaceholderSemanticsGuardTest.kt、ReaderInspirationSnapshotSaveTest.kt）不在 978 清单内——保持删除态，不恢复；D-only 源码清单为 0（D 源码树恰为 978 集合 + 1 个未跟踪旧版测试）。

### 5. 行尾政策

候选 9 个文件为 CRLF（MarkdownDocument.kt、ReaderContentHostMarkdownBranch.kt、ReaderProgressActions.kt、SyncRepository.kt、WebDavBackup.kt、MarkdownDocumentTest.kt、SyncRepositoryLocalFactsTest.kt、WebDavCompatibilityTest.kt、MarkdownVisibleSourceOffsetTest.kt）。**应用必须按候选原始字节**，否则文件 SHA 不匹配冻结清单；`diffs/head-to-candidate/` 的统一 diff 为 LF 归一化可读版，仅供审阅。

### 6. 应用顺序、最小验证范围与停止点

顺序：① v4 冻结后替换 4 路径指纹 → ② 9 个 clean-apply → ③ 3 个 clean-merge → ④ 18 个新文件 → ⑤ 1 个被超越测试替换 → ⑥ 6 冲突文件逐块决议（建议 EpubParser → EpubRepository → SyncRepository → WebDavBackup → ShelfImporter → ShelfImporterTest）→ ⑦ v4 四路径按其清单应用。

最小验证：应用后逐文件 SHA 对账（含 v4 最终指纹）；冲突文件相关定向测试；构建（按当轮候选配置）；设备批次按当轮授权（MuMu 模拟器验收口径，本清单不含设备 PASS 声明）。不重跑已输入匹配的 424 JVM 与设备批次。

停止点：任一冲突块无法决议、SHA 对账失败即停止并退回 Codex 核查；TXT-v4 未冻结前不应用 4 个待定路径。

## 原始证据

- [integration-manifest.json](<E:/creation-reading-assistant/exact-integration-prep-v1/integration-manifest.json>) —— 980 条目逐文件清单（路径/D 当前 SHA/候选 SHA/类别/必要 hunk/依据/共享 WIP 交集与处理）
- [diffs/INDEX.md](<E:/creation-reading-assistant/exact-integration-prep-v1/diffs/INDEX.md>) —— 13 个 HEAD→候选统一 diff + 6 个冲突块提取
- 分析中间产物：`normalized-merge-analysis.json`、`line-ending-analysis.json`、`hunk-analysis.json`、`normalized-merges/`、`_scan_delta.py`、`_scan_donly.py`、`_gen_manifest.py`、`_gen_diffs.py`（均在本任务目录）
- 输入：[verification.json](<E:/creation-reading-assistant/unified-v1-codex-review-20261005/verification.json>)、[TXT-v3 freeze](<E:/creation-reading-assistant/txt-reentry-fix-v3/freeze/source-sha256.json>)、[统一候选](<E:/creation-reading-assistant/unified-candidate-integration-v1/android>)

## 恢复与副作用

无设备、构建、Git 或 D 产品副作用；仅新建 E 盘任务目录与本报告。未安装 APK、未执行 ADB、未提交。

## 未覆盖项

- TXT 四路径最终指纹待 v4 交付；**本清单当前不可完全执行**（976/980 条目可直接执行，4 条待 v4，6 冲突文件 34 块待逐块决议）。
- 冲突块的具体取舍（尤其 WebDavBackup 的 withRetry 包装 vs 候选直接构建、EpubParser 闸结构）需集成者结合验收证据逐块定，本任务不决断。
- 设备行为、R8/正式签名/升级链未验；MuMu 验收须按当轮设备决定执行并恢复约定基线。

## 交付状态与下一步

交付状态：**READY_FOR_REVIEW（清单准备完成，非完全可执行）**。下一步：等待 Codex 核查本清单与 TXT-v4 冻结 → 用 v4 最终 SHA 替换 4 个待定路径指纹 → 冲突逐块决议 → 当轮授权后由唯一 owner 应用并绑定 prep-v2 构建/运行 MuMu J1 及具体受影响邻接；J5 不重复派发。
