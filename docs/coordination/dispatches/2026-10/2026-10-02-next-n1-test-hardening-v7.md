# N1-test-hardening-v7 — 保护纳入完整回归 + C4 实际导出验证报告

- 任务编号/版本：**N1-test-hardening-v7**（依据 Codex-review-N1v6-v1 §Spec 两项测试侧事项：F1/P2 accepted 冲突未入回归、F2/P3 C4 完整导出未证明）。
- 执行者：ZCode / GLM（account:zai-start-plan）；开始 2026-10-02T19:27+08:00。**零 ADB、零设备、零 APK 安装**；与 N1-repair-v5/其他窗口独立，未读取或集成其修复。
- 候选：`E:\creation-reading-assistant\next-round-n1-test-hardening-v7\android`（自 v6 派生，877 文件）。

## 1. 输入指纹

| 项 | 值 |
|---|---|
| 恢复仓库 HEAD | `e39a78da9c62f02614207cffb36969a32492eaea`（开工核对一致；全程未变，未暂存/commit/push/reset/checkout） |
| 父候选 | `E:\creation-reading-assistant\next-round-n1-repair-v6\android` |
| 父输入 SHA（实算一致） | `LocalZipBackup.kt` `f141941a628de19660d71875bb34faa5b4833f05f6db7d0025aba774165b298e`；`LocalZipBackupTest.kt` `4d6825b1a97da6e97d4575b4742c169681b6b6eccac7a6dddbbf35771701a707` |
| 资源占用 | 构建/缓存复用 `next-round-joint-j1-v2\_cache\gradle-home`（E 盘，本轮独占使用；未复制新缓存）；产物全部在候选自有 `app/build` |

## 2. 所有权与改动

**唯一改动文件**：`app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipBackupTest.kt`
- 最终 SHA：`000b7c811abc1ac013d301203d52f6a4ef9c8161a641f6d76ee782573f01e8f3`（父 `4d6825b1…`）
- 补丁：`freeze/evidence/n1v7-test-diff.patch`（163 行）

树差核验（`freeze/evidence/tree-diff-v6v7.txt`）：877/877 文件，**SINGLE_TEST_FILE_DIFF=True**（仅该测试文件变化，无新增/删除）。生产输入逐字节保持父版本：`LocalZipBackup.kt` `f141941a…`、`LocalZipRestoreStateStore.kt` `167b881a…`、`JsonBridge.kt` `f8e455a4…`、`ProfileRoute.kt` `89a793ba…`、`ProfileViewModel.kt` `124517d2…`。runner/依赖/构建配置未动；未新增依赖。

## 3. 两项补强实现

### 3.1 新增正式用例：真实 accepted 分类 + 继承冲突义务（C5）

`N1v7_C5_acceptedConflictingVersionKeepsObligationAndBlocksExport`：
- A（rev2，与本地 equal）正文复制中断 → 落盘 `hash("A CONTENT")` 义务；
- B 携带 **rev3 + 更晚 updatedAt**，经**真实 `JsonBridge.importFromString`** 走 **accepted** 分支；
- **分类证据（非注释）**：`coVerify { bookDao.upsert(withArg { it.revision == 3 }) }`——accepted ⇒ 真实合并把 B payload 写入 DAO；同用例断言失败信息直接打印实际 revision。
- 断言：`dst` 仍为 `ORIGINAL`（冲突正文不得写入）、义务逐字等于 A 的 hash（不得被 hash(B) 替换或剪除）、状态未完成、`unresolvedTargets/pendingTargets` 仍守护、导出明确因「上次整包恢复未完成」被阻止（`exportBlockedByUnfinishedState` 语义化判定，区分门禁原因与其它失败）。
- A/B 使用不同 revision（2/3），避免两者实际都是 equal（修复 v6 候选 C1–C4 固定 revision=2 的覆盖缺口）。

### 3.2 C4 完整导出成功（保留原链路，补齐真实导出流）

保留 A→B→B→重选匹配 A 的完整链路与正文字节/状态断言；追加：
- 真实 `exportZip` 直接断言成功（失败即 `AssertionError` 并携带原因，不吞异常、不 mock exportZip/buildExport）；
- 打开实际输出 ZIP：断言含 `backup.json` 与 `books/b1/content.txt`；
- 解析 `backup.json`：books 恰 1 本且 payload.id=`b1`；ZIP 内正文逐字节等于收敛后的 `A CONTENT`；
- 不再以「错误消息不含未完成状态」当成功。

**真实导出夹具**（仅测试侧）：`realJsonBridge(..., exportBooks=...)` 补齐 `JsonBridge.buildExport` 所需的全部 DAO Flow/查询（bookDao.observeAllActive→exportBooks，其余 observe 集合为空，tag/category/shelf/bookTag/bookCategory/shelfBook/chapterRead 的 getAll(Active) 为空；suspend 方法用 `coEvery`）。生产导出门禁零改动。

## 4. 执行与结果（红→绿，按具体风险运行）

| 阶段 | 命令（候选 android 目录） | 结果 |
|---|---|---|
| **C4 红**（补强断言 + 未补导出流夹具） | `:app:testDebugUnitTest --offline --console=plain --tests …LocalZipBackupTest` | **62 tests / 1 failed**：仅 C4 失败 `收敛后真实导出必须成功：Expected at least one element`（真实 buildExport 因缺 DAO Flow 夹具而失败）——证明新导出断言有效，非空断言。`freeze/evidence/stage1-red.{log,xml}` |
| 过程修复 | stage-2 首跑编译失败：4 个 suspend DAO 的 `getAll(Active)` 误用非 suspend `every{}`（另有临时探针位于非 suspend lambda） | 改为 `coEvery{}` 并移除探针后重跑（测试侧夹具迭代，非产品问题） |
| **全类绿（终跑）** | 同上（全类一次） | **62 tests / 0 failures / 0 errors / 0 skipped**（`freeze/evidence/stage2-green.{log,xml}`）；原 61 条全部保留，新增 1 条 = 62（XML 实算，未硬改计数） |
| **v5 负向对照**（独立副本 `_negative-v5-control`，仅替换生产 `LocalZipBackup.kt` 为 v5 `409f3801…`） | `--tests …N1v7_C5_acceptedConflictingVersionKeepsObligationAndBlocksExport` | **1 test / 1 failure**：`ComparisonFailure: accepted 分类不得写入冲突正文 expected:<ORIGINAL> but was:<B CONTENT>`——精确指向 v5 经 accepted 授权复制冲突正文的缺陷；v6 生产同用例通过。`freeze/evidence/negative-v5-c5.{log,xml}` |

只跑必要用例与最终全类一次；未跑全项目测试、StateStore/JsonBridge 未变输入的旧验证未重跑（无回归证据）。

## 5. 原始证据

`E:\creation-reading-assistant\next-round-n1-test-hardening-v7\freeze\`：
- `evidence/n1v7-test-diff.patch`（163 行）、`evidence/tree-diff-v6v7.txt`（SINGLE_TEST_FILE_DIFF=True + 五生产文件 SHA 对照）
- `evidence/stage1-red.log|xml`（62/1，C4 导出失败）、`evidence/stage2-green.log|xml`（62/0）
- `evidence/negative-v5-c5.log|xml`（1/1，ComparisonFailure 正文）
- `source-sha256.txt`（测试文件 `000b7c81…`、生产 `f141941a…`）
- 负向副本保留于 `_negative-v5-control/`（**在正式候选 `android/` 之外**，不进入 v7 候选）

## 6. 未覆盖项

- 真实 Room、进程 kill、存储故障、设备重试与恢复对账：**未执行**（v7 全部证据为 JVM 定向；`fileCopyOperation` 接缝与 mock DAO 属 JVM 语义）。
- accepted 用例仅覆盖"B 被接受且与义务冲突"单路径；v6 已测的 equal/空指纹/同指纹/legacy 组合不在本轮重复。
- 设备/合流/发布边界不变（MuMu 授权不等于实体硬件；D2/legacy/跨端/F 维持原登记）。

## 7. 恢复与副作用

零设备操作、零共享仓库源码写入（仅新增本报告）；HEAD 未变；原 v6 候选与既有候选只读未动。E 盘新增：v7 候选、freeze 证据、负向对照副本、`_work` 脚本。

## 8. 交付状态与下一步

**交付成立：唯一测试文件补强（C5 accepted 回归 + C4 真实导出与 ZIP 内容），红→绿证据完整，v5 负向对照精确失败。**
- 最终测试文件 SHA：`000b7c811abc1ac013d301203d52f6a4ef9c8161a641f6d76ee782573f01e8f3`；实际测试计数 **62**（61 保留 + 1 新增，XML 实算）。
- 下一步（交 Codex）：核过本补强后，把该测试文件并入联合候选唯一 JVM 测试位并跑定向门禁；真实 Room/设备与恢复对账仍由唯一 MuMu 所有者另批执行。生产问题不在本轮范围；如审查发现需改产品，另立任务。
