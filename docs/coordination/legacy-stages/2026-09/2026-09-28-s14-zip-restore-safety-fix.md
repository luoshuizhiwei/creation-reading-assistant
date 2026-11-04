# S14 本地 ZIP 恢复：书源文件覆盖损坏与版本偏斜修复（2026-09-28，未编译/未运行）

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`
- 角色：S14 窗口（继续）；依据 [失败原子性调查](2026-09-28-s14-zip-restore-failure-atomicity.md)（含 Codex 复核注记）与本棒被授予的 `JsonBridge.kt` 最小 hunk 所有权实施
- 纪律：**本轮未运行 Gradle / ADB / MuMu**（窗口 1 是唯一 Gradle 所有者）；未接触真实手机、正式书库、qahost 既有数据；未 commit/push/reset/checkout；未清理他人未提交文件
- 修改文件（`git status` 核对）：`LocalZipBackup.kt`、`JsonBridge.kt`（仅授权 hunk）、`LocalZipBackupTest.kt`（JVM）、`LocalZipBackupPersistentRestoreTest.kt`（androidTest，追加用例）。同目录 `WebDavBackup.kt` / `WebDavBackupTest.kt` 的 M 状态系他人既有改动，本棒零触碰

## 1. 旧实现可复现失败（怎么复现，不需要磁盘满）

旧实现（LocalZipBackup.kt 修订前 ：104-123）：`JsonBridge.importFromString` 事务提交后，`tmpBooks.walkTopDown().forEach` 直接 `dst.outputStream()` 写真实目标——`FileOutputStream` 先截断既有文件。三个可复现失败，全部已被本轮新测试以旧行为必然失败的方式钉住：

| # | 失败 | 旧实现复现方式 | 新实现预期 | 钉住它的测试 |
|---|---|---|---|---|
| F1 | **唯一完好旧文件被截断/半写入** | 任何一次复制中途异常（ENOSPC/权限）都发生在 dst 上 | 写入只发生在同目录 staging；中途失败 → staging 被 finally 清理，旧目标逐字节保留 | androidTest 用例 4 + JVM `mid write failure via seam keeps old target intact`（用 `fileCopyOperation` 测试接缝在 staging 写入中途抛出，**不靠碰运气制造磁盘满**） |
| F2 | **rename 失败时无保护路径** | 旧行为没有 staging/rename 概念，直接写流 | staging 完整复制并校验长度后 rename 替换；rename 失败保留旧目标并上抛 `IllegalStateException`，绝不退回直接写 dst | androidTest 用例 5（目标位置预置同名目录——夹具自洽，该路径只有目录一个角色） |
| F3 | **成功路径版本偏斜**：本地行 revision/updated_at 较新、备份中同一书较旧时，DB 合并正确跳过行，文件却仍被旧备份字节覆盖 | 正常恢复即复现，无需故障 | `importFromString` 回报实际合并结果；被跳过书籍的 `local_content_path` 换算为 books 根相对路径后一律跳过复制 | androidTest 用例 6（行字段 + 文件 SHA-256 **两轴**对账；旧行为下文件轴必然失败）+ JVM `files of skipped newer books are not overwritten by backup bytes` |

Codex 注记要求的「夹具自洽」已落实：F1/F2 用**不同的书籍与不同性质的预置**（F1 目标路径只有旧文件、失败由接缝制造；F2 目标路径只有目录障碍），同一路径从不兼任两个角色。

## 2. 新实现（生产代码改动）

### 2.1 `JsonBridge.kt`（授权 hunk 内，未动其他逻辑与 schema）

- 新增顶层 `data class ImportMergeResult(acceptedBookIds: Set<String>, skippedBookContentPaths: List<String>)`（:243-256）。
- `importFromString` 增加第三个**带默认值**的参数 `onMergeResult: (ImportMergeResult) -> Unit = {}`（:376）：事务体内为每个书籍行记录「已接受（upsert）/ 被跳过（收集其 `books.local_content_path`）」（:384-397），**事务提交后**调用回调（:488-491）。返回值保持 Unit——JSON 导入（`importFrom`）、WebDAV 恢复（`ProfileWebDavManager`）与既有测试桩零改动。
- 关键设计：**版本感知复制只依据实际 DB 合并结果**；不因 ZIP 是否含文件、也不按路径模式猜测书 id。EPUB 无需特判——`replaceStoredEpub`/EpubRepository 的 EPUB 行同样写 `local_content_path`（EpubRepository.kt:189/519/532），与 TXT 走同一 DB 驱动通道。

### 2.2 `LocalZipBackup.kt`

- `importZip` 文件段收敛为 `copyBookFiles(tmpBooks, booksDir, mergeResult)`（:112-122）；合并结果经回调捕获。
- `copyBookFiles`（:149-166）：`skippedBookContentPaths` 经 `relativePathOrNull`（`relativePathOf` 的非抛出版）换算为保护相对路径集合；遍历解压目录，命中保护集的文件 `return@forEach` 跳过，其余逐文件走 `copyWithStaging`。
- `copyWithStaging`（:174-186）：先写 `<dst.name>.restore-<nanoTime>.staging` → `fileCopyOperation` 复制 → **长度校验** → `renameTo` 原子替换；任何失败保留旧目标并上抛，`finally` 清理 staging。
- `internal var fileCopyOperation: (InputStream, OutputStream) -> Unit`（:137-138）：**仅供测试注入**的复制接缝（模拟写中途失败），生产恒为默认 `copyTo`。
- 类 KDoc 与 ：107-109 旧注释同步改写：明确「DB 先提交、文件在后，**整包不是原子的**」，删除"中途失败整体回滚"的过度承诺。

## 3. 测试矩阵（先测试后修复，全部已写入，未运行）

| 文件 | 用例 | 判别力 |
|---|---|---|
| `LocalZipBackupTest.kt`（JVM） | `staged copy replaces target and leaves no staging residue` | 新实现成功路径不变量（无残留中间文件） |
| 同上 | `mid write failure via seam keeps old target intact` | 接缝注入写中途失败 → 旧目标逐字节保留、staging 清理（F1，JVM 层） |
| 同上 | `files of skipped newer books are not overwritten by backup bytes` | 合并结果→复制门控（F3 单元层：跳过书保新字节、接受书照常恢复） |
| `LocalZipBackupPersistentRestoreTest.kt`（androidTest，持久库） | 用例 4 `fileCopyMidWriteFailureKeepsExistingUniqueSourceFileIntact` | 持久库上 F1 端到端：DB 行已提交（rev2）+ 旧文件 SHA-256 不变 + 无 staging 残留 |
| 同上 | 用例 5 `replaceFailureKeepsTargetAndThrowsInsteadOfDirectOverwrite` | rename 失败 → 异常上抛、旧目标保留、DB 已提交（F2） |
| 同上 | 用例 6 `newerLocalRowKeepsNewerFileBytesOnSuccessfulRestore` | 持久库上 F3 两轴对账：行字段（revision/updated_at/标题）与文件 SHA-256 都保持本地新版；同备份全新书籍照常恢复（行+文件） |

既有用例兼容性：androidTest 用例 1–3（MuMu 3/3）在新实现下行为不变——用例 1 恢复侧全部书籍被接受（保护集为空，文件走 staging），用例 2 纯 DB 事务断言，用例 3 在入库前拒绝；**无需无故重跑**，待总指挥按 §5 串行验证时一并回归。JVM 既有两处 `importFromString` 桩已改为三参 `any()` 匹配（本文件所有权内）；`ProfileWebDavManagerTest` 的两参桩与 `JsonBridgeTest`/`WebDavBackupTest` 的两参实参调用依赖「带默认值新参数」的兼容性，编译语义上成立（返回类型仍 Unit、默认参数走 `$default` 桥），**未经编译验证**。

## 4. 如实保留的边界（不宣称"整包完全原子"）

1. **DB 先提交、文件后复制**的顺序未改：文件段失败时 DB 已是备份态（∪ 保留的旧书行，关联表已按备份重建），文件为新旧混合。这是设计选择（DB 原子 + 文件尽力 + "重新定位文件"修复路径兜底），本轮只消除其中的**旧文件损坏**与**版本偏斜**两档。
2. 失败后用户可见反馈仍是单条 snackbar（`ProfileViewModel.zipImport` → "整包导入失败：`<原始 message>`"），无"重新导入同一 ZIP 可收敛"的指引——UI 文案属后续小改（所有权 UI 侧），本轮未动。
3. 重导入同一 ZIP 收敛（DB 幂等 + staging 全量重写自愈）为源码推导，端到端待 §5 验证。
4. 进程在 staging 阶段被杀（非异常路径）会留下 `*.restore-*.staging` 残留文件：finally 只覆盖异常路径。残留文件下次导出会被 `walkTopDown` 打进备份——已知小残余，登记待后续窗口决定（如导出时过滤 `.staging` 或恢复前清理）。
5. **偏斜保护的完整性残余**：保护集合来自被跳过行的 `local_content_path`；该字段为 null 或指向 books 根之外的行（如 `ShelfImporter.importEpub` 新建的 EPUB 行在 :843-854 未写 `local_content_path`，仅在 `replaceStoredEpub` 后才补上）不受偏斜保护——staging 仍防其损坏。见 §6 SEAM REQUEST。

## 5. 预期验证命令（供总指挥串行执行，本棒未运行）

```powershell
# JVM 定向（android/ 目录）
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.sync.LocalZipBackupTest"
# 生产编译（确认 JsonBridge hunk 与调用方兼容）
.\gradlew.bat :app:compileDebugKotlin
# androidTest（沿用 09-28 MuMu 通道）
.\gradlew.bat -PcraAndroidTestBuildType=qahost :app:assembleQahost :app:assembleQahostAndroidTest
adb -s <serial> shell am instrument -w -r -e class `
  com.creationreadingassistant.feature.sync.LocalZipBackupPersistentRestoreTest `
  com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
```

预期：JVM 8 用例全绿（5 既有 + 3 新增）；androidTest **6 用例**全绿（3 既有回归 + 3 新增），`INSTRUMENTATION_CODE: -1`。验证时建议沿用 09-28 报告的 qahost 库三件哈希前后对账。

## 6. SEAM REQUEST

- **现行：无。** 授权的 JsonBridge hunk（合并结果/接受集合）已覆盖版本感知所需的全部信息；ZIP 路径到书籍的归属完全由 DB 行的 `local_content_path` 反推，无猜测。
- **可选预告（闭合 §4.5 残余）**：若总指挥希望把"新建 EPUB 行"也纳入偏斜保护，需要 `ShelfImporter.importEpub`（`feature/library/`，S04 共享 owner）在创建 `BookEntity` 时写入 `local_content_path = books/epub/<id>.epub`（EpubRepository 存储路径语义在 `feature/reader/EpubRepository.kt:90`）。属他窗文件，本棒未动；是否立项请裁决。

## 7. 静态自检（已执行）

四文件括号/圆括号平衡（93/93、51/51、47/47、125/125 与 203/203、112/112、173/173、585/585）；`LocalZipBackup.kt` 的 `InputStream`/`OutputStream` 导入原有、新增符号闭环（`fileCopyOperation`/`copyBookFiles`/`copyWithStaging`/`relativePathOrNull` 定义与调用一一对应）；全文件无 `books/**` 式嵌套注释隐患（吸取 09-28 编译教训）；KDoc 内不再有"原子恢复"过度承诺；旧注释"再覆盖 books 目录"零残留。**未做**：编译、KSP、Lint、测试运行、设备操作。

落盘即停，交总指挥复核与串行构建验收。不 commit/push/reset/checkout；不清理他人未提交文件。

---

# 追加（2026-09-28 第二轮）：跨平台替换修复与串行复跑真实结果

## A1. Codex 首轮串行运行的真实失败（已被本棒核验）

- 证据：`E:\creation-reading-assistant\codex-s14-review-20260928\LocalZipBackupTest-failed.xml`，SHA-256 `F874A043407615584AE2D917E0F545B764AA4E2D954875D5D32AB90930F94582`（本棒独立重算，逐字一致）。
- 结果：**9 tests / 1 failure / EXIT 1**（生产与测试 Kotlin 编译成功）。失败用例 `staged copy replaces target and leaves no staging residue`，`IllegalStateException: 书源文件替换失败（旧目标已保留）：b1/content.txt` 于 `copyWithStaging` 的 `staging.renameTo(dst)`。
- 根因：`File.renameTo` 在 **Windows** 对已存在目标返回 false（POSIX 才原子替换既有目标）——首轮实现只在 Android/Linux 语义下成立，JVM 测试在本机 Windows 暴露。其余 8 项（含写中途失败、版本门控）通过，因为它们不经过 rename-over-existing 路径。
- 数量更正：上轮报告 §3 写"JVM 8 用例"，实际 9 项（6 既有 + 3 新增）；本轮再加 1 项目录目标用例，现为 **10**。

## A2. 跨平台替换修复（`LocalZipBackup.kt` `copyWithStaging` 重写）

替换链按序（写入仍只发生在 staging，**绝不以先删除旧目标再 rename 作为修复**，绝不退回直接对目标 outputStream 写入）：

1. **目录守卫**：目标为目录时直接抛 `IllegalStateException`（目录内容来历不明，不得被搬走后删除）——androidTest 用例 5 语义不变，仅触发点前移到 staging 之前（该用例 KDoc 已同步）。
2. **POSIX/Android 快路径**：`staging.renameTo(dst)` 对既有目标原子替换，设备全平台一次到位。
3. **保护性 backup 换入**（`renameTo` 失败且目标存在，即 Windows/异常场景）：旧目标 rename 为同目录 `*.restore-backup-<nano>`（**旧字节全程存在**）→ `staging.renameTo(dst)` 落位 → 失败则把 backup 恢复回目标，自动恢复也失败时**显式报错并保留 backup**（错误消息含 backup 绝对路径）→ 成功后清理 backup，清理失败同样**显式报错并保留**。
4. `finally` 清理 staging 残留（全路径）。
5. **资源泄漏顺手修复**：`src.inputStream()` 改为嵌套 `use { }`——旧写法在接缝抛出时输入流不关闭。

## A3. 测试变化

- `LocalZipBackupTest.kt`（现 **10** 项）：`staged copy replaces target...` 残留断言扩展到保护性 backup（Windows 上该用例真实走 backup 换入路径）；新增 `replace into directory target throws and preserves the obstacle`（目录目标拒绝 + 无任何残留）。
- androidTest 用例 4–6 未改断言（用例 5 KDoc 更新触发机制描述）；用例 5 在新实现下仍为 `IllegalStateException` + 目录原样保留。

## A4. 串行复跑真实结果（本棒为唯一 Gradle 所有者）

- 命令：`gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.feature.sync.LocalZipBackupTest`（android/ 目录）。
- **退出码 0**，`BUILD SUCCESSFUL in 57s`；XML：**10 tests / 0 failures / 0 errors / 0 skipped**，含首轮失败用例与新增目录目标用例。
- 证据（`E:\creation-reading-assistant\codex-s14-fix-20260928\`）：
  - `jvm-test-run.log`，SHA-256 `86983043A5872F0B091059C9C4938BBBB6AC0BCA2AED1C9258B58DA7B7068BE0`
  - `LocalZipBackupTest-passed.xml`，SHA-256 `0815C725D0A56A5C4650BBBD743C6B89D5FD472B4BF7AE9B2A9786942B889883`
  - `RESULT.txt`（命令/退出码/两轮对照/哈希索引）

## A5. 未验证边界（更新）

- 本轮只通过 **JVM 定向门**：生产/测试编译成功 + 10/10 JVM。androidTest 6 项（含既有 3 项回归与新增 3 项）**仍未运行**，由总指挥在代码门通过后按 MuMu 隔离通道排验；保护性 backup 换入路径在 Android(POSIX) 上实际不可达（renameTo 直接成功），其设备侧行为无意义也不需要验证。
- §4 的全部边界（DB 先提交文件后复制的非原子性、进程被杀的 staging 残留、`local_content_path` 为 null 行的偏斜残余、失败 snackbar 文案）继续成立；本轮同样**不宣称整包 DB/文件原子性**。
- JVM 结果 XML 位于共享 `app/build/test-results/`，按仓库惯例以 E 盘哈希副本为准。

本轮追加后停止，交总指挥复核。未 commit/push/reset/checkout；未触碰原始仓库、真实手机、正式书库。
