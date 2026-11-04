# 回滚 WIP 保全合并 v2：fresh-source 与复制路径逐字节核对

- 任务编号/版本：`CRA-ROLLBACK-WIP-RECONCILE-v2`，2026-10-04 指定，实际执行 2026-10-04T13:30–17:10+08:00，应用 ZCode / GLM（model: new-provider/glm-5.2）。
- 输入指纹：HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（开工=收工，未变更）；暂存区前后均 0 条。续接候选 `E:\creation-reading-assistant\next-round-rollback-wip-reconcile-v2\android`，由 v1 创建并复制冻结的 `EpubParser.kt`/`EpubRepositoryReplaceTest.kt`，尚未修改 `EpubRepository.kt`。三方输入 SHA 与 v1 [source-sha256.txt](<E:/creation-reading-assistant/next-round-rollback-wip-reconcile-v1/freeze/source-sha256.txt>) 一致；本轮再核 D 现场三路径与快照零漂移。
- 所有权与改动：只读 D 源码/快照/基线/文档；写入仅新目录 `E:\creation-reading-assistant\next-round-rollback-wip-reconcile-v2`（android 候选、before、freeze、logs、payload、execution.json）与唯一报告 `docs/coordination/dispatches/2026-10/2026-10-04-next-rollback-wip-reconcile-v2.md`。改动恰为三路径（见 payload），未触碰 EpubParser.kt / FileOps.kt / ShelfImporter.kt / ShelfImporterTest.kt（SHA 与 before 一致）；未操作设备；未执行任何 Git 写操作；未回灌 D。

## 红阶段（真实失败集 = 4 项，非 1 项）

按任务要求先核失败原因、保存原 XML、不重做已有红阶段。v1 已跑的红阶段日志 `logs/red-fresh-source.log` 退出码 1。本轮在 v2 独立缓存复跑红测试，确认**真实红集为 4 项**（不止 v1 报告的 fresh-source 1 项）：

1. `replaceStoredEpub reads fresh bytes when source uri is unchanged but content changed`（`EpubRepositoryReplaceTest.kt:947`）—— `EpubParser` 的「文件已存在就直接沿用」早退分支命中旧 target，来源新字节根本没被读过，替换退化为「用 V1 覆盖 V1」的 no-op。
2. `replaceStoredEpub rejects truncated fallback backup via length check`（`:1170`）—— `require(backup.length() == target.length())` 只比长度，无法拦下「等长但错字节」。
3. `replaceStoredEpub keeps full backup when restore copy returns truncated`—— 恢复复制无逐字节核对，截断后 `backup.delete()` 删掉唯一完整旧副本。
4. `replaceStoredEpub surfaces restore failure through the caught exception chain`—— 恢复失败未写入 AppLog 诊断记录。

红 XML 原件已保存：`freeze/evidence/red-fresh-source-TEST-EpubRepositoryReplaceTest.xml`（SHA256 `9398bf54…`）。

## 实施垂直切片（红→绿）

按「先修 fresh-source，再修复制校验」推进。改动集中在 `EpubRepository.kt`，`EpubParser.kt`/`FileOps.kt` 未改（v1 已就位的 fresh-source 模式与接缝直接复用）。

1. **守卫前移 + fresh-source 强制读当前来源（任务目标 1/2/3）**：把「既有 backup 残留即拒绝」的守卫从 `EpubParser.parse` 之后移到之前；并将 parse 调用改为 `EpubParser.parse(context, selectedUri, requireFreshSource = true)`。EpubParser 的 fresh-source 模式（v1 已验但未嫁接）此时被启用：禁用早退分支、一律落 `.importing` staging、禁用本地副本兜底（避免把旧内部副本伪装成新来源）、失败时清理本次产生的 `.importing` 与 landing。
2. **backup 建立复制逐字节核对（目标 5/7）**：`target.renameTo(backup)` 失败退回 `fileOps.copyTo(target, backup)` 后，新增 `verifyFilesEqual(target, backup)` 闸门；通过后才置 `oldBytesParkedInBackup = true`。短写（长度不等）与等长错字节（长度同、内容异）都抛 `IllegalArgumentException("备份旧副本时文件不完整")`，旧 target 逐字节存活。
3. **恢复复制逐字节核对（目标 6/7）**：`backup.renameTo(target)` 失败退回 `fileOps.copyTo(backup, target)` 后，新增 `verifyFilesEqual(backup, target)` 闸门；通过后才 `backup.delete()`。核对失败抛 `IOException("回滚恢复不完整：旧副本未逐字节复位")`，backup 保留，失败挂 `primary.addSuppressed`。
4. **AppLog 中性诊断（目标 9）**：恢复失败路径写 `AppLog.e("EpubRepository", "EPUB 回滚恢复未完成，完整备份已保留，旧正文可能部分损坏")`，不含书名/文件名/完整路径/隐私内容。
5. **异常链保留（目标 8）**：主异常经 `when(swapStage)` 重抛，恢复异常经 `addSuppressed` 挂在原实例；既有用例的 cause 链与 suppressed 断言合同不变。
6. **verifyFilesEqual 实现（目标：有界流式，不载入全文件，无 Android API 不兼容）**：64 KiB 双缓冲逐块比较，长度不同先失败，同长逐字节核对，仅用 `java.io` 基础 API。
7. **临时文件清理（目标 4）**：fresh-source 失败时 `.importing` 由 EpubParser 的 `discardLanding` 清理；EpubRepository 的 `finally { staging.delete() }` 与既有 `if (backupMayHoldOurPartialBytes) backup.delete()` 不变，失败时不误删旧 target。

## 根因诊断：批次测试失败的 MockK stub 签名不匹配

首次绿阶段验证时，`EpubRepositoryReplaceRollbackTest` 整批 11 项在批次运行下全部抛 `io.mockk.MockKException at EpubRepositoryReplaceRollbackTest.kt:70`，但**单个测试方法在隔离下 BUILD SUCCESSFUL**。jstack 诊断（`logs/hung-worker-jstack-*.txt`）显示测试 worker 在 `MessageHub.stop` 的 `awaitTermination` 处 TIMED_WAITING，伴随 GC 风暴（18 个 GC 线程各 ~12s CPU）。

根因：`stubParsedNew` 只 stub 了 4 参 `EpubParser.parse(any(), any(), any(), any())`，而本轮新增的 `parse(context, selectedUri, requireFreshSource = true)` 调用经 Kotlin 编译为 5 参 JVM 签名（含 `Boolean`）。MockK 找不到匹配的 5 参记录，抛「no answer found」。隔离下单测通过是因为某些用例的 `parse(context, uri)` 2 参调用被 relaxed object 默认行为兜住，但批次下 mockkObject 状态累积导致崩溃。

修复：`stubParsedNew` 同时 stub 5 参与 4 参两个签名（见 `EpubRepositoryReplaceRollbackTest.kt:104-117`），保留既有 4 参调用（`openEpub` 等）的桩。修复后整批 11/11 绿。

## 验证

定向测试集 142/142 绿（0 failure/error/skipped），构成与原始证据：

| 测试类 | tests | fail | err | skip | XML |
|---|---|---|---|---|---|
| EpubRepositoryReplaceTest | 26 | 0 | 0 | 0 | [green-TEST-…EpubRepositoryReplaceTest.xml](<E:/creation-reading-assistant/next-round-rollback-wip-reconcile-v2/freeze/evidence/green-TEST-com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest.xml>) |
| EpubRepositoryReplaceRollbackTest | 11 | 0 | 0 | 0 | [green-TEST-…EpubRepositoryReplaceRollbackTest.xml](<E:/creation-reading-assistant/next-round-rollback-wip-reconcile-v2/freeze/evidence/green-TEST-com.creationreadingassistant.feature.reader.EpubRepositoryReplaceRollbackTest.xml>) |
| ShelfImporterTest | 63 | 0 | 0 | 0 | [green-TEST-…ShelfImporterTest.xml](<E:/creation-reading-assistant/next-round-rollback-wip-reconcile-v2/freeze/evidence/green-TEST-com.creationreadingassistant.ui.viewmodel.ShelfImporterTest.xml>) |
| EpubParserTextTest | 28 | 0 | 0 | 0 | [green-TEST-…EpubParserTextTest.xml](<E:/creation-reading-assistant/next-round-rollback-wip-reconcile-v2/freeze/evidence/green-TEST-com.creationreadingassistant.feature.reader.EpubParserTextTest.xml>) |
| EpubParserMalformedCorpusTest | 11 | 0 | 0 | 0 | [green-TEST-…EpubParserMalformedCorpusTest.xml](<E:/creation-reading-assistant/next-round-rollback-wip-reconcile-v2/freeze/evidence/green-TEST-com.creationreadingassistant.feature.reader.EpubParserMalformedCorpusTest.xml>) |
| EpubRepositoryMetadataTest | 3 | 0 | 0 | 0 | [green-TEST-…EpubRepositoryMetadataTest.xml](<E:/creation-reading-assistant/next-round-rollback-wip-reconcile-v2/freeze/evidence/green-TEST-com.creationreadingassistant.feature.reader.EpubRepositoryMetadataTest.xml>) |
| **合计** | **142** | **0** | **0** | **0** | |

- v1 的 74 项（`EpubRepositoryReplaceRollbackTest` 11 + `ShelfImporterTest` 63）全部保留通过。
- 5 个新增用例：①守卫前移不落 `.importing`；②fresh-source 不可读不退回旧副本；③建立备份等长错字节；④恢复复制等长错字节；⑤正常路径核对通过后才删 target。
- 运行命令：`./gradlew --offline --no-daemon :app:testDebugUnitTest --tests <6 类> --console=plain`，日志 `freeze/evidence/green-all-run.log`，退出码 `freeze/evidence/green-all-run-exit.txt`（=0）。

## 原始证据

- `freeze/evidence/`：8 个 green XML + 1 个 red XML + green-all-run.log + exit + 两份 hung-worker-jstack（MockK 批次崩溃诊断）。
- `before/sha256.txt`：开工 7 路径 SHA；`freeze/after-sha256.txt`：收工 7 路径 SHA。
- `payload/android/`：恰 3 路径改动（EpubRepository.kt / EpubRepositoryReplaceTest.kt / EpubRepositoryReplaceRollbackTest.kt），`payload/manifest.json` 含 SHA 与 size。
- `execution.json`：任务台账、红/绿阶段、并发发现、未覆盖项、副作用、停止条件。

## 未覆盖项与边界

- `verifyFilesEqual` 通过后、`target.delete()`/`backup.delete()` 之前进程被杀（无 fsync）——既有覆盖缺口，未覆盖。
- 真实 Room/设备、`EpubParserInstrumentedTest`（androidTest）不在本轮。
- 全量 JVM 未跑（按影响选择 6 个定向测试类）。
- **同书 `.importing` 并发入口串行性未证明**：唯一生产入口 `ShelfImporter.reselectEpub → EpubRepository.replaceStoredEpub`；`BookOperationsViewModel.reselectFile` 与 `ShelfViewModel.confirmSourceRefresh` 均在 `viewModelScope.launch` 内串行 for-loop 调用，但 `EpubRepository.replaceStoredEpub` 本身无 Mutex/synchronized；同书并发调用会让两次各自走到 fresh-source 复制 + backup 守卫，`backup.exists()` 判据非线程安全。本任务**未扩大重构**（无 Mutex 注入），该风险为既有边界，不虚称已解决。
- `ShelfImporter.kt` / `ShelfImporterTest.kt` 本任务未修改（SHA 与 before 一致），保留 v1 已验状态。

## 副作用与停止条件

无设备、无系统设置、无正式书库触碰；共享仓库仅新增本报告；HEAD/暂存区/其他 WIP 全程未动；v1 `_cache/gradle-home` 仅读复制为 v2 独立缓存（不与其他窗口共享可写缓存）。完成 fresh-source 修复与复制路径双向逐字节核对、5 新测试 + 既有 74 项保留通过后停止。未提交/push/reset/checkout，未修改 current.md/TODO，未修改原仓库或正式书库。
