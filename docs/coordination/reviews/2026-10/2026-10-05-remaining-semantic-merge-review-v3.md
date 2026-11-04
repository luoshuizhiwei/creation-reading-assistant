# 其余七文件语义复核（v2 清单"采用候选"建议核实）

- 任务编号/版本：`CRA-REMAINING-SEMANTIC-MERGE-REVIEW-v3`；2026-10-05 指定，实际执行 2026-10-05T00:35–01:20+08:00，应用 ZCode / GLM（account:zai-start-plan / GLM-5.3-Flash）。只读复核，不实施合流。
- 输入指纹：HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（开工=收工）；暂存区前后 0 条；**D 工作树零写入，七文件 SHA 开工/收工两次实算零漂移**。候选 `E:\creation-reading-assistant\next-round-joint-final-v3\android`。七文件三方 SHA（完整值见 [execution.json](<E:/creation-reading-assistant/remaining-semantic-merge-review-v3/execution.json>)）：

| 文件 | HEAD | 候选 final-v3 | D WIP |
|---|---|---|---|
| SyncRepository.kt | ad3a45d3… | b01cfda9… | 21802075… |
| ShelfImporter.kt | d6bd2396… | bfc37d71… | 5120e75c… |
| EpubParser.kt | 11912d49… | 27a4644d… | af223434… |
| EpubRepository.kt | 60e138dc… | 15a92883… | 4adb16e3… |
| WebDavBackup.kt | 6d4db3d8… | 1291b147… | dd200bb6… |
| ShelfImporterTest.kt | afc60d28… | 89e335be… | e70b1074… |
| EpubRepositoryReplaceTest.kt | 不在 HEAD | 39e7457d… | 6b254a75… |

- 所有权与改动：只读 D/候选/HEAD/v2 清单与 evidence/；写入仅 `E:\creation-reading-assistant\remaining-semantic-merge-review-v3`（execution.json、hunk-inventory.json、hunk-verdicts.json、diffs/×7、scripts/×3）与本唯一报告。不改 v1/v2 清单与旧报告、不写 D/E 源码、零 Git 写操作、不构建不跑测试不碰设备。
- 环境：Windows / Git Bash / Python 3.13（difflib 经 `git diff --no-index`，LF 归一后比较）；未启动 JDK/Gradle/设备。

## 执行与结果

**方向核实（要求 1）**：自建候选→D 统一 diff（'-'=仅候选，'+'=仅 D），并用双侧实际源码核两个代表 hunk——SyncRepository h4（候选侧 optString R1 辅助块 vs D 侧 naive applyBook，方向与 v2 报告一致）与 EpubRepository h5（D 侧 S12-① globalOffset，v2 报告**漏判**处）。Codex 核查对 Profile 两文件的"方向读反"结论未在本七文件复现；但 v2 对本组文件的结论有两处需修正（见下）。

**逐 hunk 分类（要求 2–7，完整逐 hunk 证据在 [hunk-verdicts.json](<E:/creation-reading-assistant/remaining-semantic-merge-review-v3/hunk-verdicts.json>)，diff 原文与 hunk 清单在 diffs/ 与 hunk-inventory.json）**。41 个 hunk 全部有结论：候选独有 15、D 独有（被替代旧实现）12、等价改写 6、no-op 2、真冲突 1、D 独有轻微（判弃留痕）5。

**逐文件处置（要求 8）**：

| 文件 | 处置 | 关键证据 |
|---|---|---|
| SyncRepository.kt | **采用候选** | 候选=R1 安全解析（optString/optLong/optFloat 拒 null/非法类型/NaN）+ 逐字段合并 + backfill 合并非透传 + deviceId 透传 + filePath 缺失→failedItems；门控（SyncOptions/ConflictStrategy/关闭类发空列表）在候选 context 逐字保留。D 独有 105 行全部为 R1 前朴素实现：applyProgress/applySession 仅合并 payload/revision/meta（远端进度值完全不落提升列=同步不回传进度值）、push 无 filePath 校验照发。两者均为 R1 合同有意取代项，由 SyncRepositoryLocalFactsTest 25 项（含 4 条 merged*）钉住。本地事实保护已按候选 applyBook 的 copy 字段清单逐项核实（local_uri/local_content_path/size/content_status/cover_data_url/original_file_name/imported_at/description 均不在合并集） |
| ShelfImporter.kt | **采用候选** | D 独有=旧两标记回滚（copyTo 中途失败不清残缺 backup、恢复无 suppressed、无 backup 缺失处理）——被候选 SWAP 三态+restoreBackupOver 直接升级（three-deliveries P1）。唯一非替代差异：D 在守卫**前** `staging.delete()`（拒绝路径顺带清残留 .repairing）——判弃留痕：.repairing 非唯一副本、无数据安全影响、下次替换守卫通过后必清 |
| EpubParser.kt | **采用候选** | 5 hunk 全部为候选独有（公共 parse=全程持闸包装 + internal parseUnderSwapGate 供替换链再入安全调用 + 闸契约 KDoc）；**diff 全程无 '+' 行——D 零独有内容，候选为严格文本超集**。与 rollback-wip-reconcile-v3 的局部 lambda 实现为同一合同的两种实现 |
| EpubRepository.kt | **精确组合 = 候选全部 + D 的 saveProgress 一行** | **真冲突一处（v2 漏判）**：D `globalOffset = absoluteOffset.takeIf { it >= 0 }`（S12-①：无全书偏移时 locator v2 的 "offset" 键缺省，由读侧 chapterStart[ci]+co 派生；伪造值会让 SourceNavigationContract 一致性校验拒绝所有回源跳转）vs 候选 `if (absoluteOffset >= 0) absoluteOffset else legacyOffset`（把章内偏移冒充全局）。必须保 D 行（含注释）。其余：备份建立/回滚/恢复为对偶实现，候选含闸+逐字节核对+missing-backup suppressed+条件 AppLog，逐点覆盖 D；D fresh parse 传 expectedBookId=stored.id vs 候选派生 id 为实现差异（两侧 finally 无条件清理 validatedFile，无行为损失）；BUFFER_BYTES 常量=被候选内联值替代 |
| WebDavBackup.kt | **采用候选**（v2 待人工确认落实为结论） | 候选=P2 修复后：put 首发有界重试+401/403 快速失败+仅 404/409 触发 MKCOL（失败带上下文）+最多一次重试；ensureCollection 显式错误；HEAD→PROPFIND→OPTIONS 回退链（与 WebDavCompatibilityTest.mergedUnsupportedHeadAndPropfindUsesOptions 合同一致）；OkHttp 解析+userinfo 前置拒绝+严格 isLanHost+严格文件名白名单。D 独有=修复前实现（整流 withRetry 含 401 重试、ensureCollection 静默吞错、PROPFIND-first、放宽 host/文件名白名单）+ 两项轻微特性明确判弃留痕：连接测试错误消息含响应体（可观察性差异）、白名单额外放行 '@','#','+' 等字符与 DEL（安全合同优先）。429/5xx 两侧均不重试（withRetry 仅重试 isTransientNetworkError，已核 catch 与异常分类）；结果类型两侧一致（Result 包装） |
| ShelfImporterTest.kt | **采用候选** | **单 hunk 纯增量（+0/-257）**：D 全文逐字节包含于候选，D 的 50 用例断言零丢失（文本包含=比测试名集合更强的证明）；候选多出 257 行=已验回滚套件 13 用例+seedOldTxt。实际方法数 cand=63 / D=50（纠正 v2 报告的 39/26 计数口径，差值 13 一致） |
| EpubRepositoryReplaceTest.kt | **采用候选** | 3 hunk：h1 KDoc（D 的"等长错字节未覆盖"声明已被 v2 逐字节核对修复取代，进程被杀缺口声明两侧共同保留）；h2/h3 候选独有=v2 增量 5 用例+v3 清理回归 2 用例。D 的 21 用例逐字节包含于候选 28 用例（cand=28/D=21，与 v2 一致） |

**v2 结论修正两点**：① v2 称 SyncRepository "D = R1 + 旧签名 backfill"——实际 D **无** R1 逐字段拉取合并（payload-only），采用候选是有意的行为取代而非等价换签名，需在合流说明中明示；② v2 未发现 EpubRepository saveProgress 的 S12-① 真冲突（其 hunk 表有该 hunk 但结论表漏判）——本复核升级为精确组合。

## 原始证据

`E:\creation-reading-assistant\remaining-semantic-merge-review-v3\`：`execution.json`（应用/模型/HEAD/七文件三方 SHA/收工零漂移）；`hunk-inventory.json`（41 hunk 行区间/函数归属/±行数）；`hunk-verdicts.json`（逐 hunk 结论+D 独有行为去向+最小验证建议）；`diffs/*.cand2dw.diff`（七文件候选→D 原文）；`scripts/`×3 可复跑。

## 最小合流后验证建议（要求 9，复用条件已写实）

- **可直接复用旧证据**（条件=合流树对应文件 SHA 与已验交付一致，本候选已满足前三项）：SyncRepository 三套件 55/55（SyncRepository b01cfda9/ProfileViewModel 18b52513/ProfileViewModelTest cefea0a3 == sync-wip-reconcile 冻结）；WebDavCompatibilityTest 27/27（WebDavBackup 1291b147 一致）；ShelfImporterTest 63/63（ShelfImporter bfc37d71 + 测试 89e335be == rollback-wip-reconcile-v1 冻结，即其 74 项证据中的 63 项）。
- **须新跑**：EpubParser/EpubRepository/ReplaceTest 五套件（ReplaceTest 28+RollbackTest 11+ParserText 28+MalformedCorpus 11+Metadata 3）——候选 EpubParser 27a4644d/EpubRepository 15a92883 与 rollback-wip-reconcile-v3 冻结 SHA 不同（闸包装 vs 局部 lambda 两种实现），旧 147/147 不可直接移植；且 RollbackTest 若仍 stub `EpubParser.parse` 需按 `parseUnderSwapGate` 适配（候选替换链走 internal 入口）。
- **精确组合后新增**：EpubRepository saveProgress 的 S12-① 行为需一条定向回归（章切换落库→locator v2 无 offset 键→SourceNavigationContract 按 chapterStart[ci]+co 派生成功），或核实既有 SourceNavigationContractTest 已覆盖该路径。

## 恢复与副作用

D 仓库/Git/候选零写入；七文件 SHA 开工/收工一致（零漂移）；无设备、无构建、无测试运行；共享树仅新增本报告。

## 未覆盖项

- Profile 三文件与 ReaderProgress 两文件不在本复核范围（其他 owner 负责）；Codex 已指出其方向错误与漏判接缝，本报告不重复其结论。
- FileOps.kt/EpubSwapGate.kt 仅按依赖需要只读确认存在与被引用（双侧 FileOps 逐字节相同 per v2 机械清单），未做独立逐行语义复核。
- withRetry 的 isTransientNetworkError 完整异常分类未逐行展开（已确认排除 IllegalArgumentException/CancellationException 且 HTTP 业务错误走 IllegalStateException 不重试；IOException 细分清单属既有已验范围）。
- 不给全产品百分比；本报告为集成决策输入，不构成合流授权。

## 交付状态与下一步

七文件 41 hunk 全部有处理结论，D 独有行为全部有明确去向（取代/判弃留痕/必须保留各归其位）；唯一需组合项 = EpubRepository saveProgress 的 S12-① globalOffset 行。本轮到此停止：未应用 patch、未开启新任务、未回灌 D、未操作 Git/设备。合流实施由单一集成 owner 按本结论与分层清单执行。
