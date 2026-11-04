# S04-3 / S04-4 实现报告：来源基线重算与元数据缺失保护

> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`  
> 分支：`codex/recover-worktree-20260920`  
> 基准 HEAD：`5aed5939600e491843e3c44453921bd6273a233c`  
> 收工 HEAD：`5aed5939600e491843e3c44453921bd6273a233c`（严格遵守红线：未 commit/push/stage）  
> 报告文件：`docs/coordination/legacy-stages/undated/agent-stage-g-source-baseline-integrity.md`  

---

## 1. 任务背景与核心修复目标

在书架来源刷新链路中，成功拉取外部文件替换内部正文副本后，`ShelfImporter.refreshSourceBaseline` 负责就地推进 `LibrarySourceRefEntity` 基线记录，以便后续检测文件是否有变动以及书架导入查重。

此前存在两处基线缺陷：
1. **S04-4 元数据缺失保护（同口径防御）**：
   - 旧实现对 `size` 做了防护（`observed.first ?: ref.sizeBytes`），但 `last_modified` 直接使用了 `observed.second`（若 provider 缺失则写入 `null`）。某些第三方 SAF DocumentProvider（如特定网盘或文件管理器）可能不提供或偶发返回空的 `last_modified` 列，导致原有基线时间戳被抹除，后续变动检测因时间戳为空出现误判。
   - 此外，若 provider 彻底不可读（`observed == null`，如临时断链或权限短暂受阻），必须保持旧基线不写库，不得用虚构/损坏数据破坏既有记录。
2. **S04-3 正文替换后基线指纹与哈希重算**：
   - 刷新正文成功后，原代码仅更新了 `size` 和 `last_modified`，`content_hash` 与 `candidate_fingerprint` 依然保留拉取前的旧值。
   - 这会导致后续重新扫描或导入查重时，去重引擎拿旧指纹与新源文件/内部副本比对，产生错误的重复判定或漏判。
   - 本任务要求在推进基线时，根据已成功落盘的内部正文副本，重新计算 `candidate_fingerprint` 与 `content_hash` 并写入 `LibrarySourceRefEntity`。

---

## 2. 修改文件清单与 Diff 摘要

本次修改严格受限于授权文件清单，无任何越界修改：

| 文件路径 | 状态 | 职责与变更说明 |
|---|---|---|
| `android/app/src/main/java/com/creationreadingassistant/feature/library/ShelfImporter.kt` | `M` | 1. **S04-4**：`newLastModified = observed.second ?: ref.lastModifiedMillis`，provider 返回 null 时保留旧时间戳；若 provider 完全不可读提前 return 不写库；<br>2. **S04-3**：新增 `findInternalFile`、`computeFileHash`、`computeCandidateFingerprint` 辅助方法；在推进基线时，基于内部副本文件（或回退 uri）重新计算 `newContentHash`（<=32MB 时）与 `newFingerprint`（>32MB 时），并写入基线实体。 |
| `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ShelfImporterTest.kt` | `M` | 1. 为 `bookDao`、`bookFileDao` 补充默认安全桩；为 `uriMock` 补充 `lastPathSegment` 默认值；<br>2. 新增 3 个专项测试用例：`refresh advances baseline with newly computed content hash instead of stale hash`、`refresh preserves old last_modified when provider returns null instead of overwriting`、`refresh does not update baseline when provider is completely unreadable`。 |
| `docs/coordination/legacy-stages/undated/agent-stage-g-source-baseline-integrity.md` | `新增` | 本次交接报告。 |

### 禁改文件确认
- `SearchIndexBookRefreshCoordinator.kt`：**未触碰**
- `ShelfViewModel.kt`：**未触碰**
- `ShelfRoute.kt`：**未触碰**
- `ShelfUiState.kt`：**未触碰**
- `BookRepository.kt`：**未触碰**
- 数据库 schema / DAO / Entity / Migration：**未触碰**
- 任何阅读器核心引擎文件：**未触碰**
- `2026-09-22-current-task-ledger.md`：**未触碰**

---

## 3. 核心机制设计

### 3.1 S04-4 元数据缺失保护机制
```kotlin
// 查询外部 provider 元数据
val observed = withContext(ioDispatcher) { querySourceMetadata(context.contentResolver, uri) }
    ?: return // 若 provider 完全不可读，保持既有基线，严禁虚构写库

// 同口径兜底保护：缺失时沿用旧基线，严禁抹除已有有效值
val newSize = observed.first ?: ref.sizeBytes
val newLastModified = observed.second ?: ref.lastModifiedMillis
```

### 3.2 S04-3 指纹与哈希动态重算机制
- **正文文件定位**：
  - 首先通过 `bookDao.getById(bookId).local_content_path` 寻找内部持久化副本；
  - 若路径未记录，则在应用私有目录 `context.filesDir/books/$bookId` 中查找已落盘的正文文件（排除 `.repairing`、`.backup` 等临时文件）。
- **指纹/哈希计算**：
  - 遵循本工程标准分界 `SourceFingerprint.isLargeFile(newSize)`（32MB）：
    - **小/中文件（<= 32MB）**：计算全量 MD5 作为 `content_hash`，`candidate_fingerprint` 置 null；
    - **超大文件（> 32MB）**：计算文件大小 + 首块 64KB MD5 + 尾块 64KB MD5 作为 `candidate_fingerprint`，`content_hash` 置 null；
  - 若因沙箱权限无法直接读取 File，优雅回退至 `SourceFingerprint.compute(contentResolver, uri, newSize)` / `contentHashOrNull(uri, ...)`；
  - 将最新计算的指纹与时间戳写入 `LibrarySourceRefEntity` 并调用 `sourceIndex.record`。

---

## 4. 验证命令与测试执行输出

### 4.1 编译检查
**命令**：
```powershell
.\gradlew.bat :app:compileDebugKotlin --no-parallel
```
- **退出码**：`0`
- **结果**：BUILD SUCCESSFUL（0 错误，0 新增警告）

### 4.2 单元测试（强制 fresh 执行）
**命令**：
```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest" --no-parallel --rerun-tasks
```
- **退出码**：`0`
- **测试结果**：`32 tests completed, 0 failed, 0 skipped`（既有 29 项 + S04-3/S04-4 新增 3 项全绿）。
- **新增用例清单**：
  1. `refresh advances baseline with newly computed content hash instead of stale hash`：验证正文刷新后基线写入重新计算的内容哈希，断言新写入的哈希不是旧基线的旧哈希。
  2. `refresh preserves old last_modified when provider returns null instead of overwriting`：验证 provider 返回 null last_modified 时，旧基线时间戳得到完整保留，未被抹为 null。
  3. `refresh does not update baseline when provider is completely unreadable`：验证 provider 完全无法查询（query 为 null）时，正文替换本身成功，但保持旧基线不写库（`sourceIndex.record` 调用 0 次）。

### 4.3 整体回归与交叉校验
**命令**：
```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.data.repository.SearchIndexBookRefreshCoordinatorTest" --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest" --tests "com.creationreadingassistant.ui.viewmodel.ShelfViewModelTest" --no-parallel --rerun-tasks
```
- **退出码**：`0`
- **执行时间**：1m 57s
- **测试结果汇总**：
  - `SearchIndexBookRefreshCoordinatorTest`：6 tests, 0 failures, 0 errors, 0 skipped
  - `ShelfImporterTest`：32 tests, 0 failures, 0 errors, 0 skipped
  - `ShelfViewModelTest`：20 tests, 0 failures, 0 errors, 0 skipped
  - **总计：58 项测试全部 PASS，0 失败，0 告警**。

---

## 5. 未覆盖与未验证事项

1. **真实 SAF ContentProvider 的边界行为**：受纪律红线限制未连接 ADB / 真机，实际 Android 系统中各厂商 SAF DocumentProvider（如各家云盘、文件管理器）的列返回完整性在真机测试阶段由集成者统一验收。
2. **极大文件（如百兆级 TXT）计算耗时**：JVM 测试采用流式与虚拟数据验证逻辑正确性，大文件在真机上的 IO 与散列开销已由 `SourceFingerprint` 分块策略予以限制（不超过 128KB 读取）。

---

## 6. 安全与规范声明

- [x] 严格遵守单一工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- [x] 未执行 `git commit`、`git push`、`git stage`、`git reset`、`git checkout`、`git clean` 或 `git stash`
- [x] 未全仓格式化，未全仓 renormalize
- [x] 未触碰或修改原始仓库
- [x] 未运行任何 ADB 命令，未操作真实手机，未使用 MuMu 模拟器
- [x] 未触碰禁止文件，无越界修改（SEAM REQUEST：无）
- [x] 完整保留工作区中其他并发 Agent 的既有未提交代码
