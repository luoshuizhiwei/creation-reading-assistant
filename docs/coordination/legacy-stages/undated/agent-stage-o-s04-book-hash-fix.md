# agent-stage-o：S04 书籍主记录哈希陈旧修复

报告时间：2026-09-23  
工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`  
分支：`codex/recover-worktree-20260920`  
HEAD（未变）：`5aed5939600e491843e3c44453921bd6273a233c`

## 1. 问题描述

已复现的事实（来自 `docs/qa/2026-09/2026-09-23-s04-isolated-source-refresh-codex-review.md`）：

- 测试 TXT 从 V1 刷新到 V3 后：
  - `books.size = 419`，`books.content_hash` **仍为 V1 MD5**（陈旧）
  - `library_source_refs.size = 419`，`content_hash` 已为 V3 MD5（正确）
- 根本原因：`ShelfImporter.reselectPlainText` 的 `existing.copy(...)` 更新了 `size`、`local_uri`、`local_content_path`，**却沿用旧的 `content_hash`**
- `books.content_hash` 参与导入去重及同步导出，不可视为无关缓存

## 2. 授权范围

本轮独占文件：
- `android/app/src/main/java/com/creationreadingassistant/feature/library/ShelfImporter.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ShelfImporterTest.kt`

不触碰：`EpubRepository`、`SearchIndexBookRefreshCoordinator`、`ShelfViewModel`、DAO/schema、桌面端、原始仓库。

## 3. 修复实现

### 3.1 产品代码修改（ShelfImporter.kt）

**位置**：`reselectPlainText` 方法（第 881 行起），在 `bookDao.runInTransaction` 块之前新增：

```kotlin
// S04 修复：刷新后同步计算新文件的 content_hash，写入 books 表。
// 与 refreshSourceBaseline 采用相同的 MAX_HASH_BYTES 阈值：
//   - 小文件（≤32MB）：计算全量 MD5，与 library_source_refs.content_hash 同口径。
//   - 大文件（>32MB）：置为 null，旧哈希必须清除（旧哈希对应旧内容，不能保留）。
val newBookHash = computeFileHash(internalFile)
bookDao.runInTransaction {
    bookDao.upsert(
        existing.copy(
            original_file_name = fileName,
            size = size,
            local_uri = storedUri,
            local_content_path = internalFile.absolutePath,
            content_status = "available",
            content_hash = newBookHash,   // ← 新增，修复陈旧哈希
            updated_at = now,
        )
    )
    ...
}
```

**设计决策**：
- `computeFileHash(internalFile)` 已存在于 `ShelfImporter`（S04-3 阶段引入），逻辑与 `refreshSourceBaseline` 中的哈希路径同口径。
- 对 `> MAX_HASH_BYTES`（32MB）的大文件，`computeFileHash` 返回 `null`，正确清除陈旧哈希（旧哈希对应旧内容，任何情况下都不能保留）。
- `computeFileHash` 在 `reselectPlainText` 的 try 块内调用，该方法整体通过 `repairFileTyped` 在 `withContext(ioDispatcher)` 中调用，符合注释要求（IO 线程）。
- 失败路径：`computeFileHash` 返回 `null` 时（文件不存在或 `> 32MB`），`books.content_hash` 被置为 `null`，后续导入去重会退回到文件名/大小匹配，不引入新的错误状态。

### 3.2 测试修改（ShelfImporterTest.kt）

新增 4 个测试，位于 `// ===== S04 书籍主记录哈希同步 =====` 区块：

| 测试名 | 断言目标 |
|--------|---------|
| `txt source refresh updates books content_hash to match new file md5` | TXT 刷新成功后 `books.content_hash == expectedMD5 && != oldHash` |
| `md source refresh updates books content_hash to match new file md5` | MD 刷新同路径，哈希规则相同 |
| `books content_hash is consistent with source ref content_hash after small file refresh` | `books.content_hash == library_source_refs.content_hash`（两表口径一致） |
| `refresh failure does not update books content_hash` | 刷新失败时 `bookDao.upsert` 调用 0 次（哈希不写入） |

新增辅助方法 `stubRunInTransactionExecuting()`：让 `bookDao.runInTransaction` mock 真正执行回调块（而不是返回 Unit），使 `bookDao.upsert` 实际被调用且参数可被 `coVerify` 捕获。

## 4. Red Phase 说明

本轮 Gradle 启动耗时约 5m40s，Red Phase 测试启动期间同步完成了产品代码修改，因此实际编译的是修复后的代码而不是旧代码。**无法提供"旧代码下测试失败"的独立运行证据**。

以下是 Red Phase 如果在旧代码下运行时必然失败的理由：

1. `txt source refresh ...`：旧 `existing.copy(...)` 没有 `content_hash = newBookHash`，因此 `bookDao.upsert` 携带的是旧 `content_hash`（`"old_stale_hash_from_v1"`），`coVerify { match { entity.content_hash == expectedHash } }` 断言失败。
2. `md source refresh ...`：同理。
3. `books content_hash is consistent ...`：`capturedBooks.first().content_hash` 为旧哈希，不等于 `expectedHash`，第一个 `assertEquals` 失败。
4. `refresh failure does not update books content_hash`：`bookDao.upsert` 从不被 `runInTransaction` 的修复路径调用（格式不一致时进入 catch），本测试在旧代码下也会通过，不产生额外红灯。

## 5. Green Phase 验证

### 5.1 ShelfImporterTest 定向 JVM（修复后，fresh --rerun-tasks）

命令：
```bash
./gradlew :app:testDebugUnitTest \
  --tests com.creationreadingassistant.ui.viewmodel.ShelfImporterTest \
  --no-parallel --rerun-tasks
```

结果（Green Phase，task-1717，BUILD SUCCESSFUL in 5m15s，30 tasks executed）：

```
tests="36" skipped="0" failures="0" errors="0"
```

- 旧 32 项全部保留通过
- 新增 4 项（content_hash 相关）全部通过
- 总计 **36/36**，0 failures，0 errors，0 skipped

### 5.2 编译检查

`compileDebugKotlin` + `compileDebugUnitTestKotlin`：**BUILD SUCCESSFUL**（task-1683、task-1717 双次均通过）

## 6. EPUB 只读检查（replaceStoredEpub 同类风险）

检查了 `reselectEpub`（第 857 行）和 `EpubRepository.replaceStoredEpub`（第 74 行）：

**风险存在**：`reselectEpub` 调用 `epubRepository.replaceStoredEpub(existing, uri, fileName)` 替换内部副本后，只更新了 `bookFileDao`（`book_files.size/local_uri`），**没有更新 `books.content_hash`**。EPUB 刷新后，`books.content_hash` 同样会保持旧值（陈旧），与 TXT/MD 的 bug 同质。

**本轮不修复**：EPUB 路径依赖 `EpubRepository.replaceStoredEpub`，其输出格式（ZIP 哈希与纯文本哈希语义不同），且 `EpubRepository` 不在本轮授权范围，需要独立评估。

> [!WARNING]
> EPUB 来源刷新后 `books.content_hash` 陈旧：同类 P2 问题，需在后续窗口独立修复，不随本轮 TXT/MD 收口一起关闭。

## 7. 未覆盖项

| 项目 | 状态 |
|------|------|
| Red Phase 独立失败证据 | 未取得（产品修复与 Red Phase 构建同步进行） |
| 大文件（`> MAX_HASH_BYTES`）刷新后 `content_hash = null` 的 JVM 断言 | 未覆盖（大文件 JVM 测试需要 33MB 内容，但 PatternStream 可支持；后续可补） |
| EPUB 刷新后 `books.content_hash` 同类修复 | 超出本轮授权，标记未覆盖 |
| 真机端到端复验（隔离测试书 V1→V3 刷新后查询 `books.content_hash`） | 交独立验收窗口 |

## 8. 结论

S04 TXT/MD 来源刷新后 `books.content_hash` 陈旧的 **P2 元数据一致性问题已在代码与 JVM 范围内修复**：

- 修复最小（2 处改动：1 行计算 + 1 字段）
- 与 `refreshSourceBaseline` 的哈希路径同口径
- 36/36 定向 JVM 通过（含 4 项新增专项断言）
- 不引入新的 DAO/schema/Worker 改动

**不宣称整个 S04 已关闭**：EPUB 同类风险、真机复验均待独立处理。
