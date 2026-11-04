# agent-stage-q：S04 EPUB Book hash 异常语义与测试严密性修正

报告时间：2026-09-23  
工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`  
分支：`codex/recover-worktree-20260920`  
基线 HEAD：`5aed5939600e491843e3c44453921bd6273a233c`

---

## 1. 问题根因与修正概述

针对 `docs/qa/2026-09/2026-09-23-s04-epub-book-hash-codex-review.md` 指出的两项 P2 问题，本轮进行了精确修正：

### 1.1 问题 1（产品代码 Spec P2）：小文件哈希异常与大文件策略混淆
- **原缺陷**：原 `computeFileHash` 对小文件（≤32 MiB）的读取与计算过程使用 `runCatching(...).getOrNull()`，导致若落盘小文件在读取流时发生 I/O 故障，该方法吞掉异常返回 `null`。随后 `replaceStoredEpub` 将该 `null` 视同“大文件策略”写入 `books.content_hash`，并删除了 `backup` 宣布替换成功。
- **修正措施**：
  - 严格区分策略与故障：大文件（`len > MAX_HASH_BYTES`）按既定策略安全返回 `null`；
  - 小文件（`len <= MAX_HASH_BYTES`）要求合法非空（`require(len > 0L)`），在读取流计算 MD5 时移除 `runCatching` 掩盖，让 I/O 异常自然向外抛出；
  - 抛出的异常被 `replaceStoredEpub` 外层捕获，中断流程，触发 `target.delete()` 和 `backup.renameTo(target)`（旧副本完整回滚），绝不调用 `bookDao.update`，绝不删除备份，绝不以 `content_hash=null` 误导写库。

### 1.2 问题 2（测试代码 Standards P2）：失败测试误通过漏洞与后置回滚覆盖不足
- **原缺陷 1**：原失败测试使用 `try { ... fail(...) } catch (expected: Throwable)`，若待测方法意外成功返回，`fail(...)` 抛出的 `AssertionError` 同样会被 `catch (Throwable)` 捕获并误判为通过。
- **原缺陷 2**：旧副本断言仅比对字节数组大小（`.size`），未严格逐字节校验。
- **原缺陷 3**：原失败用例使用损坏文件，在最初校验时即报错退出，未执行到 `target` 已被新文件覆盖后的关键路径，未能证明“替换后失败时的 backup 回滚保护”。
- **原缺陷 4**：测试名声称“preserves reading records”，但实际未做针对性断言。
- **修正措施**：
  - 将异常断言规范化，显式断言 `caught is Exception && caught !is AssertionError`，消除测试假阳性风险；
  - 改为严格的全量字节数组比对：`assertArrayEquals(oldTargetBytes, target.readBytes())`；
  - 新增定向用例 `replaceStoredEpub restores original target bytes from backup when post-swap update fails`：使用合法新 EPUB，模拟新文件移入 `target`、旧文件移入 `backup` 后的持久化调用抛错，确证旧 `target` 完整逐字节从 `backup` 恢复，且 `staging` 和 `backup` 临时文件完全清理，错误正确向上传播；
  - 补齐 `readingProgressDao.upsert` 零写入断言，并在测试命名和报告中诚实反映断言范围。

---

## 2. 授权文件所有权与改动

### 2.1 独占修改文件
- `android/app/src/main/java/com/creationreadingassistant/feature/reader/EpubRepository.kt`
- `android/app/src/test/java/com/creationreadingassistant/feature/reader/EpubRepositoryReplaceTest.kt`
- 交付报告：`docs/coordination/legacy-stages/undated/agent-stage-q-s04-epub-hash-failure-correction.md`

### 2.2 具体代码变动
1. 在 `EpubRepository.kt` 中：
   - 将 `computeFileHash` 设为 `internal` 以便同模块定向测试；
   - 增加 `require(len > 0L) { "EPUB 本地副本为空，无法计算内容哈希" }`；
   - 去除 `runCatching(...).getOrNull()`，小文件 I/O 异常直接抛出。
2. 在 `EpubRepositoryReplaceTest.kt` 中：
   - 修复用例 3 的异常断言并使用 `assertArrayEquals` 逐字节比对，断言 `readingProgressDao` 零写入；
   - 新增用例 4：验证 target 替换后发生持久化失败时的完整 backup 逐字节回滚及清理；
   - 新增用例 5：验证 `computeFileHash` 对大文件返回 `null`、合法小文件返回 MD5、非法/0 字节文件抛出异常。

---

## 3. Red Phase 失败证据（旧行为确定性复现）

在保留旧版 `computeFileHash`（吞异常返回 `null`）逻辑下，执行包含新增严密断言的定向测试：

- **命令**：
  ```bash
  cmd /c "gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest --no-parallel --rerun-tasks"
  ```
- **退出码**：`1`（BUILD FAILED in 4m 47s）
- **XML 证据**（已留档至仓库外私有目录 `.../scratch/TEST-EpubRepositoryReplaceTest-red-phase.xml`）：
  - 汇总：`tests="5" skipped="0" failures="1" errors="0"`
  - 失败用例：
    ```
    computeFileHash fails on invalid small file instead of returning null FAILED
    java.lang.AssertionError: 0 字节文件必须抛出 IllegalArgumentException，绝不能返回 null: expected java.lang.IllegalArgumentException to be thrown, but nothing was thrown
    at org.junit.Assert.assertThrows(Assert.java:1028)
    at com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest.computeFileHash fails on invalid small file instead of returning null(EpubRepositoryReplaceTest.kt:392)
    ```
  - 通过用例（包括新增的真实后置失败 backup 回滚用例）：
    - `replaceStoredEpub restores original target bytes from backup when post-swap update fails` **PASSED**（0.155s）
    - `replaceStoredEpub rejects corrupt file without writing book and preserves original target bytes` **PASSED**（0.130s）
    - `replaceStoredEpub clears content_hash to null for large files over 32MiB` **PASSED**（10.804s）
    - `replaceStoredEpub updates books content_hash to match new file md5 for small files` **PASSED**（0.152s）

该红灯确证了旧版 `computeFileHash` 会把小文件异常隐蔽为 `null` 而不抛错，确立了本次重构的测试有效性。

---

## 4. Green Phase 验证结果

在实施 `EpubRepository.kt` 最小修复后，重新执行定向与回归验证：

### 4.1 定向测试（EpubRepositoryReplaceTest）
- **命令**：
  ```bash
  cmd /c "gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest --no-parallel --rerun-tasks"
  ```
- **退出码**：`0`（BUILD SUCCESSFUL in 4m 51s，30 actionable tasks: 30 executed）
- **XML 证据**（已留档至私有目录 `.../scratch/TEST-EpubRepositoryReplaceTest-green-phase.xml`）：
  - 汇总：`tests="5" skipped="0" failures="0" errors="0" time="11.695"`
  - 用例清单全绿：
    1. `replaceStoredEpub clears content_hash to null for large files over 32MiB`（11.109s）
    2. `replaceStoredEpub updates books content_hash to match new file md5 for small files`（0.152s）
    3. `replaceStoredEpub rejects corrupt file without writing book and preserves original target bytes`（0.144s）
    4. `computeFileHash fails on invalid small file instead of returning null`（0.125s，红转绿！）
    5. `replaceStoredEpub restores original target bytes from backup when post-swap update fails`（0.165s）

### 4.2 既有导入器回归测试（ShelfImporterTest）
- **命令**：
  ```bash
  cmd /c "gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.ui.viewmodel.ShelfImporterTest --no-parallel --rerun-tasks"
  ```
- **退出码**：`0`（BUILD SUCCESSFUL in 4m 47s，30 actionable tasks: 30 executed）
- **XML 证据**（已留档至私有目录 `.../scratch/TEST-ShelfImporterTest-stage-q.xml`）：
  - 汇总：`tests="36" skipped="0" failures="0" errors="0" time="18.072"`
  - 既有导入、去重、任务流、S04-1 前置确认、S04-2 协调器、S04-3/4 来源基线以及 TXT/MD 书本哈希测试 36/36 全部保持通过，零回归破坏。

---

## 5. 未覆盖项与边界声明

1. **断电/强杀级别物理原子性**：测试覆盖了应用级和 I/O 级的受控异常回滚（try-catch 保证 backup 还原与临时文件清理），但操作系统进程 SIGKILL 或设备物理掉电引起的未同步写入不属于本片可证明范围。
2. **真实 Room 与设备端到端**：本轮在 JVM mock 与本地真实临时文件系统下验证了逻辑与字节一致性，真实 Android 设备上的 SQLite 事务及 UI 表现留待后续真机窗口。
3. **S04 整体状态**：本修复仅收敛 EPUB 替换哈希异常与单元测试严密性两项 P2，不代表整个 S04 父项全部关闭。
