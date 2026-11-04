# agent-stage-p：S04 EPUB 来源刷新书籍主记录哈希陈旧修复

报告时间：2026-09-23  
工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`  
分支：`codex/recover-worktree-20260920`  
基线 HEAD：`5aed5939600e491843e3c44453921bd6273a233c`

---

## 1. 根因分析与口径查证

### 1.1 根因分析
在原生 Android 来源刷新链路中，针对 EPUB 文件的正文替换由 `ShelfImporter.reselectEpub` 委托给 `EpubRepository.replaceStoredEpub` 执行。
原 `replaceStoredEpub` 的执行逻辑如下：
1. 校验并暂存所选文件，将原内部副本（`target`）安全重命名备份为 `backup`（`${stored.id}.backup`）；
2. 将暂存文件替换为内部正式副本 `target`（`context.filesDir/books/epub/${stored.id}.epub`）；
3. 重新解析 `target` 为 `replacement`；
4. 构造更新记录 `val updated = stored.copy(...)` 并持久化到 `bookDao`；
5. 清理备份并返回。

**缺陷所在**：在第 4 步构造 `updated` 时，`stored.copy(...)` 仅更新了 `title`、`author`、`original_file_name`、`size`、`local_uri`、`local_content_path`、`content_status` 和 `updated_at`，**未设置 `content_hash` 参数**。导致其直接继承入参 `stored.content_hash`（即刷新前旧文件的哈希值）。
随后 `ShelfImporter.refreshSourceBaseline` 从新落盘的内部副本中计算出了新 MD5 并写入 `library_source_refs.content_hash`，导致 `books.content_hash`（旧 MD5）与 `library_source_refs.content_hash`（新 MD5）产生持久状态分裂与陈旧哈希问题。

### 1.2 首次导入与来源刷新的哈希口径
经对齐 `ShelfImporter.kt` 与 `EpubRepository.kt` 的实现：
- **哈希事实源**：对应已校验、已成功原子替换落盘的内部正式副本字节（即 `File(context.filesDir, "books/epub/${stored.id}.epub")`）。
- **≤ 32 MiB（小文件）**：顺序读取该落盘文件的全部字节流计算全量 MD5 字符串（32 位小写十六进制）。该值同时由 `EpubRepository.replaceStoredEpub` 写入 `books.content_hash`，并由 `ShelfImporter.refreshSourceBaseline` 写入 `library_source_refs.content_hash`，两表同源同口径一致。
- **> 32 MiB（超大文件）**：`books.content_hash` 必须显式更新为 `null`，**彻底清除旧文件的陈旧哈希**（旧哈希代表旧内容，保留会导致导入去重和备份导出产生假阳性匹配）；超大文件由 `library_source_refs` 的首尾分块候选指纹（`candidate_fingerprint`）机制接管。
- **失败与拒绝保护**：若所选文件损坏、非合法 EPUB、或写入中断，流程在抛出异常前触发 `backup` 还原，绝不执行 `bookDao.update(updated)`，旧书籍 ID、元数据、旧哈希及依 ID 绑定的阅读记录（`reading_progress`）丝毫不受破坏，新哈希绝不会提前写入。

---

## 2. 授权文件所有权与改动

### 2.1 独占修改文件
- `android/app/src/main/java/com/creationreadingassistant/feature/reader/EpubRepository.kt`
- `android/app/src/test/java/com/creationreadingassistant/feature/reader/EpubRepositoryReplaceTest.kt`（新建定向测试）
- 报告文档：`docs/coordination/legacy-stages/undated/agent-stage-p-s04-epub-book-hash-fix.md`

### 2.2 具体改动
在 `EpubRepository.kt`：
1. 增加常量 `MAX_HASH_BYTES = 32 * 1024 * 1024`（32 MiB），与 `ShelfImporter` 保持完全一致；
2. 增加私有工具方法 `computeFileHash(file: File): String?`，支持全量小文件 MD5 计算以及超大文件安全返回 `null`；
3. 在 `replaceStoredEpub` 中，在 `target` 落盘且经 `EpubParser.parse` 校验成功后、`bookDao.update` 执行前，计算 `val newContentHash = computeFileHash(target)`，并传给 `stored.copy(..., content_hash = newContentHash, ...)`。

---

## 3. Red Phase 确定性失败证据

在修复产品代码前，先行执行新增的 3 项定向单元测试，复现结果如下：

- **命令**：
  ```bash
  cmd /c "gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest --no-parallel --rerun-tasks"
  ```
- **退出码**：`1`（BUILD FAILED in 5m 21s）
- **XML 证据**（`TEST-com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest.xml`）：
  - 总测试数：`tests="3" skipped="0" failures="2" errors="0"`
  - 失败 1（小文件保留旧哈希）：
    ```
    replaceStoredEpub updates books content_hash to match new file md5 for small files FAILED
    java.lang.AssertionError: 不得沿用旧文件的陈旧哈希. Actual: stale_v1_hash_99999
    ```
  - 失败 2（大文件未清除旧哈希）：
    ```
    replaceStoredEpub clears content_hash to null for large files over 32MiB FAILED
    java.lang.AssertionError: 超大文件绝不得沿用旧哈希. Actual: stale_hash_from_earlier
    ```
  - 通过项（受控失败不提前写哈希）：
    ```
    replaceStoredEpub preserves id and reading records and does not write new hash on failure PASSED
    ```

该红灯确证了旧实现无论在小文件还是大文件场景下均直接沿用旧哈希值，且证明了测试用例能够精准区分新旧行为。

---

## 4. Green Phase 验证结果

在完成 `EpubRepository.kt` 的最小修复后，重新执行全量重跑（`--rerun-tasks`）：

### 4.1 定向 EPUB 仓储测试（EpubRepositoryReplaceTest）
- **命令**：
  ```bash
  cmd /c "gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest --no-parallel --rerun-tasks"
  ```
- **退出码**：`0`（BUILD SUCCESSFUL in 5m 23s，30 actionable tasks: 30 executed）
- **XML 证据**（`TEST-com.creationreadingassistant.feature.reader.EpubRepositoryReplaceTest.xml`）：
  - 汇总：`tests="3" skipped="0" failures="0" errors="0" time="12.861"`
  - 详细用例全绿：
    - `replaceStoredEpub preserves id and reading records and does not write new hash on failure`（12.008s）
    - `replaceStoredEpub clears content_hash to null for large files over 32MiB`（0.690s）
    - `replaceStoredEpub updates books content_hash to match new file md5 for small files`（0.162s）

### 4.2 既有书架导入器回归测试（ShelfImporterTest）
- **命令**：
  ```bash
  cmd /c "gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.ui.viewmodel.ShelfImporterTest --no-parallel --rerun-tasks"
  ```
- **退出码**：`0`（BUILD SUCCESSFUL in 5m 28s，30 actionable tasks: 30 executed）
- **XML 证据**（`TEST-com.creationreadingassistant.ui.viewmodel.ShelfImporterTest.xml`）：
  - 汇总：`tests="36" skipped="0" failures="0" errors="0" time="21.712"`
  - 既有导入、去重、任务流、S04-1 前置确认、S04-2 协调器、S04-3/4 来源基线以及 Stage O 的 TXT/MD 书本哈希测试 36/36 全部保持绿灯，零回归缺陷。

## 5. 未覆盖项与边界声明

1. **崩溃/断电原子性**：本修复验证了文件替换异常时的异常回滚与 try-catch 保护，未模拟 OS 进程强杀、物理断电等极端硬件故障。
2. **真机端到端验收**：本轮仅覆盖 JVM 定向单元测试，真实 Android 设备上的 EPUB 来源刷新、角标清除及各表数据核对交由独立验收窗口（WorkBuddy）。
3. **EPUB 图文解析与阅读器渲染**：本轮仅关注正文替换后的 Book 主表哈希同步，不触及阅读器排版、分页及图片加载逻辑。
