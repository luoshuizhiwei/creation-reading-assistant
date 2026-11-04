# CRA-RESELECT-ROLLBACK-FIX-v2：既有 backup 残留保护的补齐与精确交付

## 一、任务编号/版本

- **任务编号/版本**：`CRA-RESELECT-ROLLBACK-FIX-v2`
- **性质**：在 v1 已成立的「暂存失败不删原目标」修复之上，补齐**既有 backup 残留**保护；先红后绿，**零设备、零 ADB、零安装、零全量门禁**。
- **上游缺陷依据**：[回滚 v1/MuMu v2/阅读 v2 核查](../../reviews/2026-10/2026-10-03-next-rollback-v1-mumu-v2-reader-v2-codex-review.md) §Spec：`ShelfImporter` 入口 879–880、`EpubRepository` 94–95 在 `try` 之前**无条件** `backup.delete()`；上次中断后 target 可能缺失、backup 可能是唯一旧正文，再次执行会先毁掉它。
- **下游关系**：本报告的交付件成文晚于 Root 本轮核查（[核查报告](../../reviews/2026-10/2026-10-03-next-reader-rollback-delivery-codex-review.md)）与 v3 交付（[v3 报告](2026-10-03-next-reselect-rollback-fix-v3.md)）；v3 以**本 v2 候选**为只读输入，其基线四文件 SHA 与本报告 §三.1 的「改后」值逐字一致。
- **状态**：**已交付待 Codex 核查**

## 二、输入指纹

| 项 | 值 |
|---|---|
| 恢复仓库 / HEAD | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` / **`e39a78da9c62f02614207cffb36969a32492eaea`**（开工实读=收工复核，未变） |
| 只读输入 | `E:\creation-reading-assistant\next-round-reselect-rollback-fix-v1\android`（v1 候选，883 个源文件） |
| 唯一工作目录 | `E:\creation-reading-assistant\next-round-reselect-rollback-fix-v2` |
| 唯一仓库报告 | `docs/coordination/dispatches/2026-10/2026-10-03-next-reselect-rollback-fix-v2.md` |
| 重复执行检查 | 本任务目录与目标报告**开工时均不存在** → **首次执行** |
| 执行占用核查 | 未调用 `adb`/MuMu/安装；宿主已存在 `adb`/`MuMuNxDevice`/`MuMuNxMain` 进程，归**另一窗口独占设备**，本轮未连接、未使用、未启动 |

### 2.1 只读输入四文件 SHA-256（实测 = 任务书 = v1 报告 after 值，无漂移）

| 文件（相对 `android/`） | SHA-256 |
|---|---|
| `app/src/main/java/…/feature/library/ShelfImporter.kt` | `b0afec7619bb648ecfa06474c12a4888c6d7c9480c1d6e2359c5417f3dba3c80` |
| `app/src/main/java/…/feature/reader/EpubRepository.kt` | `d239050bbd41f6ff54d71e6d02461b880412a71f0a6ed7559ccd7005f1a4a984` |
| `app/src/test/java/…/ui/viewmodel/ShelfImporterTest.kt` | `d1291888bbfb4a2f21e6d034c9827f5b165de6bfe10d35b63791c97f79811508` |
| `app/src/test/java/…/feature/reader/EpubRepositoryReplaceRollbackTest.kt` | `ae8ccbd0349650568186c4777c11cd7d7e31a87b3dd0bf3cd183d6773bda0bae` |

### 2.2 v1 上游交付复核（10/10 实算一致，故直接采信其红绿结论、不重跑）

v1 的 `execution.json`、`patch/reselect-rollback-fix.patch`、`scope-check.json`、`verify_scope.py` 及 6 份 XML/2 份日志的 SHA-256 全部与 v1 报告逐字一致；v1 修复前后两文件 SHA 一致。

## 三、所有权与改动

**唯一仓库写入 = 本文件。唯一工作目录 = `E:\…\next-round-reselect-rollback-fix-v2`。**

### 3.1 改动范围（`scope-check.json`；与只读输入逐文件比对，排除 `build`/`.gradle`/`.kotlin`/`local.properties`）

| 项 | 值 |
|---|---|
| 输入 / 候选文件数 | **883 / 883** |
| 内容不同 | **4**（全部在授权四路径内） |
| 新增 / 仅输入有 | **0 / 0** |
| 相同 | **879** |
| `changed_is_allowed` / `new_is_allowed` | `true` / `true` |

| 文件 | v1 候选（改前）SHA-256 | v2 候选（改后）SHA-256 |
|---|---|---|
| `app/src/main/java/…/feature/library/ShelfImporter.kt` | `b0afec7619bb648ecfa06474c12a4888c6d7c9480c1d6e2359c5417f3dba3c80` | `e8155f3c3f9184997a9ce25b4baba5587f1ea823afc04a0803119d0762595cc3` |
| `app/src/main/java/…/feature/reader/EpubRepository.kt` | `d239050bbd41f6ff54d71e6d02461b880412a71f0a6ed7559ccd7005f1a4a984` | `b8c921f6ecf0b70bdc200df2abe5c31f77a31bbf7e20e3233178320e711684f3` |
| `app/src/test/java/…/ui/viewmodel/ShelfImporterTest.kt` | `d1291888bbfb4a2f21e6d034c9827f5b165de6bfe10d35b63791c97f79811508` | `3dfcd757be302a8cce01c9201158bc3bbfc6a498b60a3d1a7cb6e08d5fafdb58` |
| `app/src/test/java/…/feature/reader/EpubRepositoryReplaceRollbackTest.kt` | `ae8ccbd0349650568186c4777c11cd7d7e31a87b3dd0bf3cd183d6773bda0bae` | `5f0016b056339a874b8e32762c1465a8a51f61f1c56c0e3f52c54887a3d4deb2` |

### 3.2 精确 hunk 清单（补丁 `-p1`，共 **11 hunks**；numstat 生产 +77/−26、测试 +218/−0）

**`ShelfImporter.kt`（4 hunks）**

| hunk | 内容 |
|---|---|
| `@@ -876,12 +876,24 @@` | 移除入口无条件 `backup.delete()`；新增入口守卫 `if (backup.isFile) throw IllegalStateException(…)`（在 `staging.delete()` 之前）；新增 `backupMayHoldOurPartialBytes` / `oldBytesParkedInBackup` 两个归属标志 |
| `@@ -890,9 +902,12 @@` | 备份步骤置位：`copyTo` 兜底前置 `backupMayHoldOurPartialBytes = true`；备份建立成功后置 `oldBytesParkedInBackup = true` |
| `@@ -943,14 +958,14 @@` | 成功清理 `backup.delete()` 不变；`catch` 三阶段改为「按归属清理」：`SWAP_UNTOUCHED -> if (backupMayHoldOurPartialBytes) backup.delete()`；`else -> internalFile.delete(); if (oldBytesParkedInBackup) restoreBackupOver(internalFile, backup, error)` |
| `@@ -963,14 +978,25 @@` | `restoreBackupOver` 增加 `primary: Throwable` 形参：backup 缺失记为恢复失败；恢复异常挂 `primary.addSuppressed(...)` 而非替换主异常 |

**`EpubRepository.kt`（4 hunks，与 TXT 对称）**

| hunk | 内容 |
|---|---|
| `@@ -91,12 +91,23 @@` | 同上入口守卫（`backup.isFile`）＋移除无条件 `backup.delete()`＋归属标志 |
| `@@ -106,9 +117,12 @@` | 备份步骤置位（`target` 侧同构） |
| `@@ -144,14 +158,14 @@` | `memoryCache.remove` 保持先执行；`catch` 按归属清理＋`restoreBackupOver(..., error)` |
| `@@ -161,14 +175,25 @@` | `restoreBackupOver` 同上（`primary` + suppressed） |

**`ShelfImporterTest.kt`（1 hunk）**：`@@ -588,6 +588,109 @@` 新增 5 条 TXT 用例（§五.3）。
**`EpubRepositoryReplaceRollbackTest.kt`（2 hunks）**：`@@ -7,6 +7,7 @@` 增加 `coVerify` import；`@@ -163,4 +164,118 @@` 新增 5 条 EPUB 用例。

### 3.3 生产语义（两格式一致）

1. **入口守卫**：检测到**普通文件形态**的同名 backup 时，在 `staging.delete()`、改写 target、写 DB **之前**抛 `IllegalStateException`，消息含「备份」与文件名，说明「可能是上次中断后唯一保存旧正文的文件…已保留该文件并停止本次替换」。**不猜测归属、不盲删、不自动覆盖恢复**。
2. **归属受限清理**：入口守卫（配合补丁）保证操作开始时 backup 位无普通文件；此后 backup 位出现的**文件**只可能来自本次写入。`SWAP_UNTOUCHED` 仅在 `copyTo` 兜底可能留下半成品时清理；`SWAP_TARGET_WRITTEN` 仅删除**本次安装**的新字节。
3. **恢复失败可观察**：`restoreBackupOver(target, backup, primary)` 恢复失败时**保留 backup 字节**，把恢复异常 `addSuppressed` 到主异常；主异常原样上抛，**不得提示恢复成功**。

### 3.4 明确未改动

`books`/`book_content`/`book_file` 写入字段与顺序、`existing.copy(...)`/`stored.copy(...)` 保留语义、`bookId`、`memoryCache` 处理、`takePersistableUriPermission`、`finally { staging.delete() }`、`SWAP_*` 阶段常量、`refreshSourceBaseline`、内容身份判定、元数据/导航/确认页；未引入 journal、未新增依赖、未新增生产接缝。

## 四、环境

Windows 11 / PowerShell 7 + Python 3.13.2；JDK `17.0.14`（`JAVA_HOME=D:\develop\Java\jdk-17.0.14`）；Gradle wrapper **8.9**；`GRADLE_USER_HOME = E:\…\next-round-reselect-rollback-fix-v2\_cache\gradle-home`（由 v1 的 E 盘缓存整体复制，**未共享写入**）；命令 `:app:testDebugUnitTest --rerun --offline --console=plain --max-workers=1`（`--rerun` 确保测试任务实际执行）。**零 ADB、零 MuMu、零安装、零正式书库。**

## 五、执行与结果

### 5.1 红测（v1 候选生产代码 + v2 最终测试集）

退出码 **1**；**43 tests / 6 failures / 0 errors / 0 skipped**（Shelf 35/3、Epub 8/3）。失败项恰为 6 条新增缺陷复现用例：

| 用例 | 红测失败断言 | 证明 |
|---|---|---|
| `reselect txt refuses replacement when a leftover backup is the only copy of the old text` | `残留 backup 必须保留` | v1 入口无条件 `backup.delete()` 删掉了唯一旧正文（随后替换照常成功） |
| `reselect txt refuses when both the target and a leftover backup exist` | `残留 backup 必须保留` | 同上；且 target 被覆盖、DB 被写 |
| `reselect txt keeps the backup and reports the original failure when the rollback itself fails` | `必须报告主异常而不是恢复异常` | v1 的 `copyTo` 恢复异常替换了主异常 |
| `replaceStoredEpub refuses replacement when a leftover backup is the only copy of the old bytes` | `残留 backup 必须保留` | 同 TXT |
| `replaceStoredEpub refuses when both the target and a leftover backup exist` | `残留 backup 必须保留` | 同上 |
| `replaceStoredEpub keeps the backup and attaches the rollback failure when the restore fails` | `必须保留主异常`（ComparisonFailure） | v1 恢复异常替换主异常、无 suppressed |

**另外 4 条新增「首次安装」用例在红测即通过**（37 通过 = 33 原始 + 4），正是「保护逻辑不得误伤 target 原先不存在」的反向守卫（§五.3）。

### 5.2 绿测（修复后 + 同一测试集）

退出码 **0**；`BUILD SUCCESSFUL in 1m 43s`；**43 tests / 0 failures / 0 errors / 0 skipped**（Shelf 35/0、Epub 8/0）。

**任务实际执行**：两份日志的 `> Task :app:testDebugUnitTest` **均无** `FROM-CACHE`/`UP-TO-DATE` 后缀（红为 `> Task :app:testDebugUnitTest FAILED`，绿为执行行 + SUCCESSFUL）。

### 5.3 新增 10 条用例与合同映射（**仅按具体风险补必要用例**）

| # | 用例（格式） | 覆盖合同 |
|---|---|---|
| 1 | leftover backup 是唯一旧正文（TXT） | 合同 1：保留字节 + 停止替换 + 明确错误；断言 DB 零写入（`runInTransaction`/`upsert`/`bookContentDao`/`bookFileDao` 各 0） |
| 2 | leftover backup 是唯一旧副本（EPUB） | 同上 |
| 3 | target 与 backup 同时存在（TXT） | 合同 1：两者字节均不得被覆盖/删除 |
| 4 | target 与 backup 同时存在（EPUB） | 同上 |
| 5 | 回滚恢复失败（TXT） | 合同 4：保留 backup、报告主异常、不提示成功 |
| 6 | 回滚恢复失败（EPUB） | 合同 4：`assertEquals` 主异常消息 + `suppressed` 非空 + backup 字节保留 |
| 7 | target 原先不存在 → 正常成功（TXT） | 合同 3/首装不误伤：写入新字节、无残留 |
| 8 | target 原先不存在 → 正常成功（EPUB） | 同上 |
| 9 | target 原先不存在 → DB 失败（TXT） | 首装失败路径：主异常文本保留、只删本次写入、无残留 |
| 10 | target 原先不存在 → DB 失败（EPUB） | 同上；并断言 `suppressed` **为空**（`oldBytesParkedInBackup` 门控未伪造回滚失败） |

**失败注入**（不新增生产接缝、不用反射）：`resolver.openInputStream` 抛错／自定义 `InputStream` 半途抛错／`backup` 预置为非空目录（v1 既有用例，语义保持）／`staging` 预置为非空目录＋`mockkObject(EpubParser)`／`bookDao.runInTransaction`（TXT）或 `bookDao.update`（EPUB）抛错；**回滚失败**用例在 DB 抛错的 `answers` 内把目标位改成非空目录，使 `target.delete()` 与 `backup.renameTo/copyTo` 同时失败，从而走到 suppressed 分支。

### 5.4 v1 的 33 条回归

v1 的 33 条（Shelf 30 + Epub 3）**全部保留、未删除、未改夹具与预期**，在绿测中 33/33 通过。特别是：`reselect txt keeps old content when the backup cannot be established` 仍走原来的「非空目录占据 backup 位 → `renameTo` 失败 → `copyTo` 抛错」注入路径（因为 v2 守卫只拒绝**文件**形态残留），未被改道。

### 5.5 断言强度

全部新用例断言**真实文件字节**（`assertArrayEquals`）与 **DB 交互**（`coVerify(exactly = 0)`），不只断言消息文本；成功路径额外断言无 `.backup`/`.repairing` 残留。用例不触碰 `reading_progress`/`notes`/`highlights`/`tags`——本次改动也未触碰。

## 六、原始证据

工件根：`E:\creation-reading-assistant\next-round-reselect-rollback-fix-v2\`

| 路径 | 说明 | SHA-256 |
|---|---|---|
| `logs/red-v2-tests.log` | 红测完整 Gradle 控制台原日志（EXITCODE=1） | `7522b7d29c9d7d4e3dfceb38537157f0685015a3fce53ef0dda3d47755e6c557` |
| `logs/green-v2-tests.log` | 绿测完整 Gradle 控制台原日志（EXITCODE=0） | `cbed80c43968b0b6591f816bb5a2ea052375184c569ab921cded03d278332053` |
| `results/TEST-red-ShelfImporterTest.xml` | 红测原始 XML（35/3/0/0） | `38d63a27fdcc409b148cbe49c6990758f5b938d852e453ebc1c24ab09af52963` |
| `results/TEST-red-EpubRepositoryReplaceRollbackTest.xml` | 红测原始 XML（8/3/0/0） | `9a5cb4e93f7cd684fb31d94759032067329539a6a25ee3b37868ad0f7f567218` |
| `results/TEST-green-ShelfImporterTest.xml` | 绿测原始 XML（35/0/0/0） | `2790abdf33dd5aa662f1673e952e663bee25a5f7febd16a2f033b1debf0e9ddd` |
| `results/TEST-green-EpubRepositoryReplaceRollbackTest.xml` | 绿测原始 XML（8/0/0/0） | `5ee7aec8c928a8dc2d427233de12cdd56a52afd48aca805c92856432229c0ae2` |
| `patch/reselect-rollback-backup-guard-v2.patch` | 4 文件补丁（`a/`…`b/` 前缀，LF，无 BOM，`-p1`） | `939b729c2af4257fa56bd5701edca729773a90a8fea364676e688f1df9323e6c` |
| `scope-check.json` | 改动范围核对结果 | `855447ebee9d3fbfc084dd2a440c7661f42c8fdac68a103214a8fafe0239ffd8` |
| `execution.json` | 执行记录 | `08d9661ce063ee98e9c30a27caa9f58d38351bef684fb6d1ce783734b00deaa3` |
| `verify_scope.py` | 范围核对脚本（v2 版） | `3380ec3db064a7e0e3b0fe9d8f8101dc626c0247d12960ddb5a22709f44ec857` |

**补丁验证**：在 v1 候选的**四文件纯净副本**上 `git -c core.autocrlf=false apply --check -p1` → 退出码 0；`apply -p1` 后 4 个文件与 v2 候选**逐字节相同**（4/4 OK，`mismatches=0`）。

**与 Root 核查、v3 的时间关系**：Root 核查（13:01）已就本 v2 候选给出「限定采纳」并指出「v2 报告/execution.json/patch 交付尚缺、入口为 `backup.isFile` 漏判目录残留」；另一窗口的 v3（13:35 启动）以本 v2 候选为只读输入完成目录残留拒绝（`backup.isFile`→`backup.exists()`，47/0 绿）。**本报告只补 v2 的交付件，不改变 v2 候选字节**，以免破坏 v3 记录的四文件基线 SHA。

## 七、未覆盖项

| # | 项 | 说明 |
|---|---|---|
| 1 | **同名 backup 的目录形态残留** | v2 入口守卫是 `backup.isFile`：**目录**残留不进守卫，替换会照常进行（非空目录会让 `copyTo` 兜底抛裸文件系统异常；空目录会被兜底 `copyTo` 消费掉）。v2 不会删除目录内的既有字节（非空目录 `delete()` 失败即 no-op），但**没有**「明确拒绝」的可观察错误。**该项已由 v3 承担并交付**（`backup.exists()`，47/0），本报告不据此宣称已覆盖。 |
| 2 | **TXT 侧 suppressed 的直接断言** | TXT 路径经 `repairFileTyped` 收敛为 `RepairOutcome(message)`，测试只能证明**主异常未被替换**（消息含 `注入：DB 写入失败`）；suppressed 链本身在 TXT 侧不可从 API 直接观测，**与 EPUB 的 `Throwable.suppressed` 断言不等价**。EPUB 侧为直接断言（§五.3 #6）。 |
| 3 | 目标安装（覆盖兜底半写）中途失败 | 与 v1 相同的未注入边界（v1 §七-1），本轮未扩大注入面。 |
| 4 | DB 已成功提交后抛错的边界 | 未测；`bookDao.runInTransaction` 桩在提交语义上不真实，本轮无证据据此新开修改。 |
| 5 | 真实 SAF provider / 真实磁盘满 / 真实权限故障 | 全部为 JVM 注入（MockK DAO 桩 + `TemporaryFolder` 真实文件系统），非真实设备故障。 |
| 6 | 真实进程强杀（kill）恢复 | 未验；不宣称强杀恢复成立。 |
| 7 | 跨文件系统与 DB 的原子性 | **未成立、也未宣称**：文件替换与 Room 写入仍是两步，无 journal、无事务边界。 |
| 8 | 设备 / UI / SAF 用户可见路径 | 未运行（本任务不使用 ADB/MuMu）。 |
| 9 | 全量 JVM / 153 或整库门禁 / Lint / Release | 未跑（按任务边界只跑两类定向测试）。 |
| 10 | 内容身份判定、`content_hash`、`library_source_refs` 基线 | **本轮刻意不做**（属 relink 预查的其他缺口，不夹带）。 |

## 八、恢复与副作用

- **仓库**：HEAD `e39a78da…` 未变；暂存区为空；未 stage/commit/push/reset/checkout；未搬目录。**唯一仓库写入 = 本报告**。
- **D 工作树既有 WIP**：`git status` 显示的 `M`/`GONE` 条目全部为**开工前既有 WIP**（mtime ≤ 2026-10-01/02，非本次写入）。另观察到 `docs/handoff/current.md` 在 13:01 被**另一窗口**更新（本轮未读写该文件，未触碰 `TODO`）。
- **只读输入**：`next-round-reselect-rollback-fix-v1\android` 四文件 SHA 收工复核与开工一致，883 文件原样；v1 工件未改。
- **下游 v3**：本 v2 候选四文件 SHA 收工复核与 §三.1「改后」值一致，v3 的输入基线仍成立。
- **冻结输入 / D 产品源码 / 其他候选 / 其他窗口工件**：未触碰。
- **设备**：零 ADB、零 MuMu、零安装、零正式书库。
- **原损坏仓库 / HOLD / 其他应用私有库**：未操作、未读取。
- **构建与缓存**：全部在自有 E 目录；`_cache/gradle-home` 为独立复制（未共享写入 v1 缓存；复制时仅 6 个 Gradle `.lock` 文件因被 v1 常驻 daemon 持有而略过，Gradle 已自行重建）；临时目录 `_patchcheck` 已删除。

## 九、交付状态与下一步

**已交付待 Codex 核查。**

- **修复**：两格式入口新增「普通文件 backup 残留 → 保留 + 拒绝新替换 + 明确错误」守卫；`catch` 清理改为**归属受限**（仅清本次 `copyTo` 兜底可能留下的半成品、仅删本次安装的新字节）；`restoreBackupOver` 恢复失败时**保留 backup** 并把恢复异常挂为主异常的 `suppressed`，主异常原样上抛。v1 的「暂存失败不删原目标」修复原样保留。
- **结果**：红 43/6 → 绿 43/0；改动严格限于授权四路径（883/883，changed=4、only=0）；补丁可 `-p1` 干净应用并逐字节复现候选。
- **建议下一步（对齐 Root 核查 §规划复核）**：
  1. Codex 核本报告 + `execution.json` + patch + 四份 XML/两份日志；
  2. 目录残留边界**归 v3**（已交付 47/0），本 v2 候选与 v3 候选应一并进入下一轮精确清单；
  3. 合流需**新精确范围**（原 25 路径冻结输入不变；拟纳入四路径后需实际去重/对账，当前无已冻结的 29 路径候选；本报告写于 13:01 核查之后，是否将 v2 与 v3 合并为同一合流单元由集成者决定）；
  4. 仍**未**成立且不得宣称：跨文件系统与 DB 原子性、强杀恢复、真实 SAF/磁盘故障、设备/UI 验收。