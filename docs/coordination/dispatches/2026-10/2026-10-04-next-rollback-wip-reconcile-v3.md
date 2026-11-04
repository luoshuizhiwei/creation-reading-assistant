# 回滚 WIP 保全合并 v3：临时文件全程清理与同书互斥闸

- 任务编号/版本：`CRA-ROLLBACK-WIP-RECONCILE-v3`；2026-10-04 指定，实际执行 2026-10-04T17:59–18:47+08:00，应用 ZCode / GLM（account:zai-start-plan）。
- 输入指纹：HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（开工=收工，只读核实）；暂存区前后 0 条。固定输入 [v2 候选](<E:/creation-reading-assistant/next-round-rollback-wip-reconcile-v2/android>)（按 v2 报告 after-SHA 逐字节核对后复制，v2 原件未动）：EpubRepository `cb2a3cad…`、EpubParser `af223434…`、FileOps `904148cb…`、ShelfImporter `bfc37d71…`、ReplaceTest `3a30db8f…`、RollbackTest `6eab90bc…`、ShelfImporterTest `89e335be…`（见 [execution.json](<E:/creation-reading-assistant/next-round-rollback-wip-reconcile-v3/execution.json>)）。
- 所有权与改动：v3 候选内改动恰五路径 —— EpubRepository.kt、EpubParser.kt、EpubRepositoryReplaceTest.kt（改）+ **EpubSwapGate.kt（新增模块内锁）**、EpubRepositoryConcurrencyTest.kt（新增并发测试，均列入 manifest）。未触碰 ShelfImporter、同步/Profile/MD、公共配置、FileOps.kt、EpubRepositoryReplaceRollbackTest.kt（保持 v2 SHA）；未操作设备；零 Git 写操作；未回灌 D。
- 环境：Windows / Git Bash / Python 3.13；JDK 17.0.14；Gradle 8.9 `--offline --no-daemon`；独立缓存 `_cache\gradle-home`（3.3G，自 v2 缓存复制，自有可写）。

## 真实红绿证据

**红（v2 实现上运行，XML/日志已存 [freeze/evidence](<E:/creation-reading-assistant/next-round-rollback-wip-reconcile-v3/freeze/evidence>)）**：先补两条**真实 Parser**（不 mock EpubParser，真实 ZIP 来源经 contentResolver）失败路径测试：①备份建立失败（renameTo 失败 + 兜底复制中途中断注入）；②DAO 持久化失败。断言 fresh 解析落下的来源临时副本按后缀全目录清零。v2 上 **28 tests / 2 failures**，两条新用例精确红 —— Codex 核查指出的缺口成立（v2 的 validatedFile 仅成功路径删除；且 fresh 副本实际落在**派生 id** 的 `<epub_hash>.importing`，点名单一固定路径会漏检，红断言按 `*.importing` 后缀全目录断言）。

**绿（v3 修复后）**：7 套件 **147/147，0 failure/error/skipped**，EXIT=0（[green XML ×7](<E:/creation-reading-assistant/next-round-rollback-wip-reconcile-v3/freeze/evidence/green-TEST-com.creationreadingassistant.feature.reader.EpubRepositoryConcurrencyTest.xml>) + green-run.log）：EpubRepositoryReplaceTest 28、RollbackTest 11、**ConcurrencyTest 3（新）**、EpubParserTextTest 28、MalformedCorpusTest 11、MetadataTest 3、ShelfImporterTest 63。**v2 六套件 142 项测试名称逐一比对全部保留（0 missing）**，新增 5 项单列（2 清理回归 + 3 并发合同），未删除或放宽任何既有断言。

## 修复内容（不重做 v1/v2 已验修复）

1. **EpubSwapGate（新，模块内锁）**：进程级 Mutex + `waiters` 计数（供并发测试受控观察）。合同：`replaceStoredEpub` **全程**（守卫/fresh 解析/暂存/备份/提升/DAO/回滚）持闸；EpubParser 普通模式落位段与清理段持闸；reuse-existing 读路径与 fresh 模式不取闸（fresh 由调用方持闸，KDoc 契约注明，防再入自死锁；唯一 fresh 调用方经 grep 核实为 replaceStoredEpub）。闸内守卫按**进闸时现场**求值 —— 并发窗口注入的残留会被拒绝（C1 证明）。
2. **EpubRepository**：全程入闸；`validatedFile`（fresh 落在派生 id 的 `.importing`）清理从"仅成功路径"移入 finally —— 暂存、备份建立、DAO、回滚任何失败都清理本次独占的来源副本，防御 `!= target` 护栏保证绝不误删旧 target；AppLog 诊断按现场条件化（备份存在→"完整备份已保留"；不存在→"备份已丢失"），并用 runCatching 包裹 —— 日志失败不得顶替原始主异常。
3. **EpubParser**：落位段折为局部 suspend lambda；普通模式经 `EpubSwapGate.withGate`（防"打开未落盘的书/迁移"与替换互删 `.importing`）；两处解析失败清理路径同规则；行为逐字保留（fresh-source、普通缓存/早退/迁移兜底）。
4. **原样保留**：v2 的逐字节备份核对（verifyFilesEqual）、主异常/suppressed 链、content_hash、残留守卫、SWAP_UNTOUCHED/BACKED_UP/WRITTEN 状态机、v1 的 fileOps 注入与 restoreBackupOver。

## 并发合同（受控时序，非延时）

- **C1 闸串行 + 残留现场重估**：测试持闸 → 启动替换（waiters≥2 证明其停在闸外，守卫未跑）→ 窗口期注入 backup 残留 → 放闸 → 替换按进闸时现场以残留守卫拒绝；DB 零写、残留字节原样、零 `.importing`。
- **C2 普通落位等待**：持闸期间启动"打开未落盘的书"——落位段等闸（waiters≥2），期间不落任何 `<id>.epub`、不删替换流程占用的 `.importing`；放闸后正常落位。
- **C3 双替换收敛**：同书两路替换并发（真实 Parser、全程持闸）双双成功，最终 target 为两路来源之一的完整字节，零 `.importing`/`.backup`/`.repairing` 残留，DB 至少两次 update。
- 已核实际调用可达性：替换入口（ShelfImporter.reselectEpub→repairFileTyped、直接调用）均可被多协程先后触发，for-loop 不构成互斥 —— 与 Codex 核查结论一致，保护为必需而非冗余。

## 清理归属

`.importing`（fresh 派生 id 路径）＝替换操作独占，全程 finally 清理；普通 parse 的落盘失败由 Parser 自身清理（持闸）；`.repairing`/`.backup` 归替换状态机（v1/v2 已验逻辑不变）；旧 target/完整 backup 任何路径不删（守卫+状态机+finally 护栏三层）。

## 最终集成清单（分层，防遗漏依赖）

| 层 | 内容 | 来源 |
|---|---|---|
| v1 | ShelfImporter.kt `bfc37d71…`、ShelfImporterTest.kt `89e335be…` | v1 合并交付（v2/v3 未改） |
| 依赖 | **FileOps.kt `904148cb…`**（D 盘 untracked 资产，不在 HEAD）、**EpubParser.kt**（v1 冻结 fresh-source + v3 闸接缝，`d68be1f5…`） | 集成必须随行落盘，否则编译失败 |
| v2 基线 | EpubRepository/EpubRepositoryReplaceTest/EpubRepositoryReplaceRollbackTest 的 v2 版已含于本候选起点 | v2 交付 |
| v3 增量 | payload 五路径（上表 CH+ADD，patch 见 freeze） | 本轮 |

只拷三路径增量会遗漏 FileOps.kt/EpubSwapGate.kt 依赖 —— 必须按本清单整层合流。

## 未覆盖项

- 进程被杀（无 finally 可运行）后的孤儿 `.importing` 仍存在（确定性路径自愈：下次 fresh parse 开头 `staging.delete()` 清掉同路径旧残留；崩溃清扫不在本轮合同）。
- 仪器化/设备并发（进程级闸只覆盖单进程内协程）；跨进程并发无保护。
- `requireFreshSource` 契约靠 KDoc+唯一调用方核实，无编译期强制。
- 全量 JVM 未跑（7 套件定向）；MockK 批次状态累积问题未复测（v2 已诊断，本轮批次运行正常）。

## 恢复与副作用

D 仓库/Git 未动；v2 及全部输入目录只读；设备未用；共享缓存只读复制；E 盘新增 v3 目录与报告。红测试曾因点名单一 `.importing` 路径得到假绿 —— 已当轮改为按后缀全目录断言并重跑取得真红，假绿结果未进入任何交付物。

## 交付状态与下一步

payload 五路径 + 分层 manifest + 红/绿/XML/日志全部冻结于 `E:\creation-reading-assistant\next-round-rollback-wip-reconcile-v3`。本轮到此停止：未回灌 D、未操作设备、未 stage/commit/push/reset/checkout、未改 current.md/Obsidian/其他窗口候选。最终合流（含 EPUB 接入与联合验收）仍由 Codex/集成者按分层清单执行。
