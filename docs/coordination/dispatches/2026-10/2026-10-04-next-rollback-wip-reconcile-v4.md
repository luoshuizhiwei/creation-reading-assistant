# CRA-ROLLBACK-WIP-RECONCILE-v4

## Scope and input

- Candidate: `E:\creation-reading-assistant\next-round-rollback-wip-reconcile-v4\android` (copied from v3 without `build`, `.gradle`, or `.kotlin`).
- Reviewed prior findings: `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\coordination\reviews\2026-10\2026-10-04-three-followup-deliveries-codex-review.md`.
- Input/output SHA-256 for the three owned production files, two test files, and test XML: `E:\creation-reading-assistant\next-round-rollback-wip-reconcile-v4\freeze\evidence\sha256.txt`.
- Build used JDK `D:\develop\Java\jdk-17.0.14`; Gradle cache reused serially from the v3 E-drive cache. No other Java/Gradle process was present before starting.

## Change

`EpubParser.parse` now takes `EpubSwapGate` across decision, landing, ZIP parsing, and failure cleanup (`EpubParser.kt:58-72`). The `reuseExisting` and `landing` snapshot is consequently evaluated only after acquiring the gate (`:89-97`), and an ordinary parse cannot hold stale “missing target” state while waiting behind a replace. The lock also protects the entire landing→parse→cleanup ownership window, so stale parse failure cleanup cannot race and remove another operation's installed target.

`parseUnderSwapGate` is the explicit internal API for callers already holding the non-reentrant gate (`EpubParser.kt:69-77`). `replaceStoredEpub` already owns the gate for the replacement transaction (`EpubRepository.kt:93`); both fresh-source validation and post-promotion reparse now call the held-gate API (`:117`, `:159-164`). The second parse can therefore fail normally and reach repository rollback instead of self-waiting on the same Mutex. Fresh-source still disables old-source fallback; normal parse retains existing fallback behavior and parser cleanup. Repository backup/rollback and primary/suppressed exception code were not changed.

Trade-off: the process-wide gate now serializes cached ordinary EPUB parses too, including reads of different books, for the duration of ZIP parsing. This is a correctness-first scope choice; no throughput benchmark was run.

## Deterministic regressions

Added to `EpubRepositoryConcurrencyTest.kt`:

- `plain parse rechecks target after queued replace and preserves installed bytes` (`:265-295`): hold gate, start ordinary parse for a missing derived target, inject a valid replacement target while parse is queued, then assert the replacement title and exact bytes remain. The former implementation failed by using the pre-gate miss and overwriting the newly installed target.
- `replacement reparse failure exits gate and restores old target` (`:298-328`): real parser sees an invalid promoted target through the file URI, must exit without a timeout/deadlock, and repository rollback must restore the original bytes. It also verifies the gate remains available after failure. This does not mock parser failure.

The first old-behavior run failed both new tests: timeout in post-promotion reparse and title mismatch in queued ordinary parse. Red evidence: `logs\red-concurrency.txt`, exit 1; retained XML: `logs\red-concurrency.xml`.

## Green verification

Ran only the seven assigned suites with `:app:testDebugUnitTest` and seven `--tests` filters. Green log: `logs\green-7-suites.txt`, exit 0, `BUILD SUCCESSFUL`. Retained XML files are under `logs\green-xml\`.

| Suite | Tests | Failures / errors / skipped |
|---|---:|---:|
| EpubParserMalformedCorpusTest | 11 | 0 / 0 / 0 |
| EpubParserTextTest | 28 | 0 / 0 / 0 |
| EpubRepositoryConcurrencyTest | 5 | 0 / 0 / 0 |
| EpubRepositoryMetadataTest | 3 | 0 / 0 / 0 |
| EpubRepositoryReplaceRollbackTest | 11 | 0 / 0 / 0 |
| EpubRepositoryReplaceTest | 28 | 0 / 0 / 0 |
| ShelfImporterTest | 63 | 0 / 0 / 0 |
| **Total** | **149** | **0 / 0 / 0** |

The v3 baseline had 147 methods in these same seven suites. The two additions account for the total increase to 149; existing test methods were retained. The rollback test's parser stub was redirected to the new explicit API without deleting test cases.

## Limits and stop

No full unit suite, lint, APK build, instrumentation, device, or performance test was run. The tests establish deterministic JVM-level gate ordering and rollback behavior, not Android filesystem behavior on every provider/device. No D product source, Git state, device, Obsidian, or current handoff was changed. Candidate work and evidence are complete; stop here for root integration/review.

## 执行账本（中文字段）

### 任务编号/版本

- `CRA-ROLLBACK-WIP-RECONCILE-v4`，2026-10-04。

### 输入指纹

- 产品基线：`E:\creation-reading-assistant\next-round-rollback-wip-reconcile-v3\android`；仅本任务拥有的五个输入文件列于下方完整 SHA-256 清单中的 `v3/` 项。
- 审查依据：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\coordination\reviews\2026-10\2026-10-04-three-followup-deliveries-codex-review.md`，SHA-256 `d02b0eac9fadf063b4045edd90d9865981bd64f33cd604f8c6906edf75fcee30`。
- 候选输出：`E:\creation-reading-assistant\next-round-rollback-wip-reconcile-v4\android`；输入与输出逐文件 SHA-256 完整清单见本节末。

### 所有权与改动

- 独占候选：`E:\creation-reading-assistant\next-round-rollback-wip-reconcile-v4`，从 v3 复制，不含 `build/`、`.gradle/`、`.kotlin/`。
- 修改仅限 `EpubParser.kt`、`EpubRepository.kt`、`EpubSwapGate.kt`、`EpubRepositoryConcurrencyTest.kt`、`EpubRepositoryReplaceRollbackTest.kt`。未改其他窗口成果。
- D 盘仅新增本交付报告；没有修改 D 产品源码、Git、设备或 Obsidian。

### 环境

- Windows；JDK 17：`D:\develop\Java\jdk-17.0.14`。
- Gradle cache：串行复用 `E:\creation-reading-assistant\next-round-rollback-wip-reconcile-v3\_cache\gradle-home`。
- 开始前检查未发现其他 Java/Gradle/Kotlin 构建进程。

### 执行与结果

- 红阶段：以复原的 v3 锁行为运行两个新增并发回归测试，测试均按预期失败；退出码 1。二次解析进入 10 秒 `TimeoutCancellationException`；排队普通解析断言标题不匹配，证实其覆盖了闸等待期间安装的 target。红日志和 XML 都已保留。
- 绿阶段：` :app:testDebugUnitTest` 加七个 `--tests` 类过滤，退出码 0，`BUILD SUCCESSFUL`。7 XML 共 149 tests、0 failures、0 errors、0 skipped；v3 的原147个测试方法保留，另新增2个。
- 没有额外全量测试、lint、APK、instrumentation 或设备运行。

### 原始证据

- 红：`E:\creation-reading-assistant\next-round-rollback-wip-reconcile-v4\logs\red-concurrency.txt`，SHA-256 `0704b12c43a52b1db952531a483d41fb4d2798ed210177e7555ca98a1614a439`；XML `logs\red-concurrency.xml`，SHA-256 `492b037da39c6fb2ef31273a5e4c33cd523179249ed047987183e316ee0d6b76`。
- 绿：`logs\green-7-suites.txt`，SHA-256 `798ca2292d687f0591035cf454fd30056049135218f673bf0dac83ff295b1b8c`；七个原始 XML 位于 `logs\green-xml\`，其 SHA 均列入 `freeze\evidence\sha256.txt`。
- SHA 清单文件：`freeze\evidence\sha256.txt`，SHA-256 `5b1707c824df12ca345262795fa93d83d0cd096ed90c3ce4e35cf5ca5791001a`。
- 以下路径相对 v3/v4 候选根；`v3/` 为输入，`v4/` 为最终产品/测试输出。

| 文件 | 输入 v3 SHA-256 | 输出 v4 SHA-256 |
|---|---|---|
| `android/app/src/main/java/com/creationreadingassistant/feature/reader/EpubParser.kt` | `d68be1f5f8e73a43c8be5d9e28841e3992af58e40d0835a86dd3f43f91d9da43` | `27a4644d32ec2b8c4e0bd9e3738265ca8164883cdc317dba069645ed9a78307b` |
| `android/app/src/main/java/com/creationreadingassistant/feature/reader/EpubRepository.kt` | `a27bcdc019ff7a73742fa723d4f3d64909e38469afa7e8dab12898fbd42f9bf7` | `15a92883019d4d2fb5c200664f5036dc9c8d572d1216e14d689faa733ff5f1d1` |
| `android/app/src/main/java/com/creationreadingassistant/feature/reader/EpubSwapGate.kt` | `6c53b91bd8e7d2335f2ac11f6ab57a4b48ed0630911944a4a3329b8098a58d6a` | `7068b879ee06f3b3d6176918eb5f1a8e5e588c84ceab265a22681104a47a7399` |
| `android/app/src/test/java/com/creationreadingassistant/feature/reader/EpubRepositoryConcurrencyTest.kt` | `53d64cce99170d840cb6b076da8a24a339191d8699e6bfaa4b771908a9ab17a7` | `2c3ddbfa63607bbd321177c34109090da35d4683093c59fabf4b4b546723dd72` |
| `android/app/src/test/java/com/creationreadingassistant/feature/reader/EpubRepositoryReplaceRollbackTest.kt` | `6eab90bc53e611bd9be463e9d3811f8363458b09b6b247fb4c05a7b5a6e065d5` | `a0352146e54530913dbb0c0e4a4da5216df3d1ff9a6c17742d0d10c620633a10` |

### 未覆盖项

- 未验证全局进程级 Mutex 对不同 EPUB 长解析的性能影响，也未做 instrumentation/真实 Android provider/设备验证。
- 未跑全量 JVM、lint、APK 或仪器门禁；本次绿证据只覆盖上述七套件。

### 恢复与副作用

- 未操作设备和用户数据；没有 Git 暂存、提交或分支操作；没有清理或迁移任何既有资产。
- 候选仅在任务专属 E 目录；D 盘唯一写入为本报告。

### 交付状态与下一步

- 本任务交付完成：1 个隔离候选、2 个红阶段失败回归、149/149 七套件绿、原始 XML/日志和 SHA 指纹齐全。
- 下一步由 Root 按其联合构建计划合流并验证；本执行者到此停止，不再重跑。


- 每个测试 XML 的完整输出 SHA-256（相对 `E:\creation-reading-assistant\next-round-rollback-wip-reconcile-v4\logs\`）：
  - `red-concurrency.xml`: `492b037da39c6fb2ef31273a5e4c33cd523179249ed047987183e316ee0d6b76`
  - `green-xml/TEST-com.creationreadingassistant.feature.reader.EpubParserMalformedCorpusTest.xml`: `cca8e15f61f6b09d1094e5f5195af6c7e9de19f0261ed58f565368907129b74e`
  - `green-xml/TEST-com.creationreadingassistant.feature.reader.EpubParserTextTest.xml`: `2d8f4db050066c12604b8327d5082ca23d9e997264daf9b1f03364bbf291cc31`
  - `green-xml/TEST-com.creationreadingassistant.feature.reader.EpubRepositoryConcurrencyTest.xml`: `1c8ffcdb6d5c29ebb7648fc67002f8a40dd14b715f478aa455747b31c0b69a4e`
  - `green-xml/TEST-com.creationreadingassistant.feature.reader.EpubRepositoryMetadataTest.xml`: `37f6c564ae3cdd41a6ed6237be031eba985e87b78546fc9581d014e80a9c6cda`
  - `green-xml/TEST-com.creationreadingassistant.feature.reader.EpubRepositoryReplaceRollbackTest.xml`: `afbcc92f25fc12769cbd47bdf4fadd15ad42705cceb3fc37f1fcb186644b7b6a`
  - `green-xml/TEST-com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest.xml`: `ba347be8ac268b22913425402893deb41c0e68e14f29c9119b709e5bb3f3c624`
  - `green-xml/TEST-com.creationreadingassistant.ui.viewmodel.ShelfImporterTest.xml`: `d788af4ffaf752da5212a92e07174aa1e8757dbc8bf19eea845a2e43b9ecaebd`
- 原始日志 SHA-256：`red-concurrency.txt` `0704b12c43a52b1db952531a483d41fb4d2798ed210177e7555ca98a1614a439`；`green-7-suites.txt` `798ca2292d687f0591035cf454fd30056049135218f673bf0dac83ff295b1b8c`；输入审查报告 `2026-10-04-three-followup-deliveries-codex-review.md` `d02b0eac9fadf063b4045edd90d9865981bd64f33cd604f8c6906edf75fcee30`。
