# S14 实现报告：备份覆盖面补充（来源引用与规则）及文案对齐

> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`  
> 分支：`codex/recover-worktree-20260920`  
> 基准 HEAD：`5aed5939600e491843e3c44453921bd6273a233c`  
> 收工 HEAD：`5aed5939600e491843e3c44453921bd6273a233c`（严格遵守红线：未 commit/push/stage）  
> 报告文件：`docs/coordination/legacy-stages/undated/agent-stage-i-backup-coverage-extension.md`  

---

## 1. 任务背景与核心修复目标

在原有的本地 JSON 导出与 WebDAV 备份体系中存在数据覆盖遗漏与文案承诺偏差：
1. **S04-5 覆盖面扩展**：
   - 之前 `LocalExport` 仅覆盖书籍、灵感、进度、会话、笔记、高亮、标签、分类、书单、已读标记等；
   - 缺少了 `library_source_refs`（书库来源引用数据）、`reader_text_rules`（替换与净化规则）及 `reader_text_corrections`（阅读器正文纠错）；
   - 在新旧设备迁移或备份恢复时，来源绑定、排版净化和纠错记录会全部丢失，导致阅读器规则需重新配置。
2. **安全合并策略（旧不覆新）与门禁校验**：
   - 导入时需在单事务内安全落库；
   - 来源引用按 `last_seen_at` 比对，规则与纠错按 `updated_at` 比对，较旧的远端记录绝不能覆盖本地较新的记录；
   - 维持 `schemaVersion != 1` 的防御门禁（`IllegalStateException` 拦截并中止事务）。
3. **C2 & C3 文案纠正**：
   - `StorageSubPage.kt` 导出卡片文案中包含了不符实际的“同步账号信息”，且未列全实际导出的笔记、高亮、书单、正文规则与来源引用；
   - `ProfileRoute.kt` 恢复与导入确认弹窗提示原本只列出“书籍、灵感、进度与会话”，未列全实际恢复的全部数据实体类别。

---

## 2. 修改文件清单与变更说明

本次修改严格受限于授权文件清单，无任何越界修改：

| 文件路径 | 状态 | 职责与变更说明 |
|---|---|---|
| `android/app/src/main/java/com/creationreadingassistant/feature/sync/JsonBridge.kt` | `M` | 1. 声明 `@Serializable` 导出的 DTO 类：`SourceRefExport`、`TextRuleExport`、`CorrectionExport` 及其与 Room Entity 的双向映射；<br>2. 在 `LocalExport` 增加 `@SerialName("library_source_refs")`、`@SerialName("reader_text_rules")`、`@SerialName("reader_text_corrections")`；<br>3. 注入 `LibrarySourceRefDao?`、`ReaderTextRuleDao?`、`ReaderCorrectionDao?`（默认 `= null` 兼容历史测试）；<br>4. `buildExport` 完整打包这三类数据（纠错记录按书 ID 聚合）；<br>5. `importFromString` 在 `runInTransaction` 内落实“旧不覆新”合并策略。 |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/StorageSubPage.kt` | `M` | 纠正导出卡片文案：移除“同步账号信息”，补齐笔记、高亮、书单、正文规则与来源引用说明。 |
| `android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/ProfileRoute.kt` | `M` | 纠正恢复确认弹窗与导入确认弹窗文案，准确列出全部恢复合并的实体类别。 |
| `android/app/src/test/java/com/creationreadingassistant/feature/sync/WebDavBackupTest.kt` | `M` | 增加 4 个专项测试用例，覆盖字段完整性与反序列化、安全修订比对（旧不覆新）、非法 schemaVersion 防御拦截、WebDAV 全链路传输。 |
| `docs/coordination/legacy-stages/undated/agent-stage-i-backup-coverage-extension.md` | `新增` | 本次交接报告。 |

### 禁改文件确认
- `SearchIndexBookRefreshCoordinator.kt`：**未触碰**
- `ShelfImporter.kt`：**未触碰**
- `ShelfViewModel.kt`：**未触碰**
- `BookRepository.kt`：**未触碰**
- 数据库 schema / DAO / Entity / Migration：**未触碰**
- `2026-09-22-current-task-ledger.md`：**未触碰**

---

## 3. 核心机制设计

### 3.1 DTO 桥接解耦设计
因不能触碰 Room Entity 代码（禁止添加 `@Serializable` 侵入实体定义），在 `JsonBridge.kt` 内部建立专用 DTO：
```kotlin
@Serializable
data class SourceRefExport(...) {
    fun toEntity(): LibrarySourceRefEntity = ...
    companion object { fun fromEntity(entity: LibrarySourceRefEntity): SourceRefExport = ... }
}

@Serializable
data class TextRuleExport(...) { ... }

@Serializable
data class CorrectionExport(...) { ... }
```
并在 `LocalExport` 结构中使用 `@SerialName` 确保 JSON 键名与契约完全对齐：
- `library_source_refs`
- `reader_text_rules`
- `reader_text_corrections`

### 3.2 增量合并策略（旧不覆新）
在导入落库时，所有操作置于同一事务中：
```kotlin
// 来源引用：比较 last_seen_at
val localRefs = dao.getAll().associateBy { it.book_id }
export.librarySourceRefs.forEach { item ->
    val local = localRefs[item.book_id]
    if (local == null || item.last_seen_at >= local.last_seen_at) {
        dao.upsert(item.toEntity())
    }
}

// 替换规则：比较 updated_at
export.textRules.forEach { item ->
    val local = dao.getById(item.id)
    if (local == null || item.updated_at >= local.updated_at) {
        dao.upsert(item.toEntity())
    }
}

// 正文纠错：比较 updated_at
export.textCorrections.forEach { item ->
    val local = dao.getById(item.id)
    if (local == null || item.updated_at >= local.updated_at) {
        dao.upsert(item.toEntity())
    }
}
```

### 3.3 文案纠正对齐
1. **`StorageSubPage.kt` 导出卡片**：
   - 修正前：“导出会保存灵感、书库元数据、同步账号信息、阅读记录与私有配置；不会导出 WebDAV 密码 / token 或设备私有路径。”
   - 修正后：“导出会保存灵感、书库元数据、进度、阅读记录、笔记、高亮、标签、分类、书单、正文规则与来源引用；不会导出 AI Key、WebDAV 密码 / token 或设备私有路径。”
2. **`ProfileRoute.kt` 确认弹窗**：
   - 修正后统一为：“恢复将按版本规则合并当前书籍、灵感、进度、会话、笔记、高亮、标签、分类、书单、正文规则与来源引用数据（旧版本记录不会覆盖新版本）。恢复前已自动备份数据库快照，若出问题可从快照找回。确认继续？”

---

## 4. 验证结果

### 4.1 Kotlin 代码编译检查
```powershell
cmd /c "gradlew.bat :app:compileDebugKotlin --no-parallel"
```
**结果**：`BUILD SUCCESSFUL in 13s`，零错误。

### 4.2 WebDAV 备份与导入单元测试
```powershell
cmd /c "gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.feature.sync.WebDavBackupTest --no-parallel --rerun-tasks"
```
**结果**：`BUILD SUCCESSFUL in 1m 43s`，包含新增用例在内的 23 项测试全部 PASS：
- `rejects public plain http with clear message` [PASS]
- `allows lan plain http` [PASS]
- `rejects unsupported scheme` [PASS]
- `test succeeds on 200` [PASS]
- `test succeeds on 207 Multi-Status` [PASS]
- `test fails on 401 unauthorized` [PASS]
- `put uploads body with Basic Auth and utf-8 encoding` [PASS]
- `put treats 201 Created and 204 No Content as success` [PASS]
- `put wraps IOException into failure` [PASS]
- `get downloads content on 200` [PASS]
- `get fails on 404 Not Found` [PASS]
- `list parses Multi-Status xml and ignores non-json files` [PASS]
- `list returns empty list when directory has no matching backups` [PASS]
- `list fails when server returns 500` [PASS]
- `network errors fail gracefully with IOException on unreachable host` [PASS]
- `export JSON contains library_source_refs and rule groups and deserializes successfully` [PASS]
- `importFromString merges library_source_refs and rules into database with safe revision policy` [PASS]
- `importFromString safely rejects unsupported schemaVersion` [PASS]
- `webdav full roundtrip with source refs and rules` [PASS]
...

### 4.3 JsonBridge 既有单测回归
```powershell
cmd /c "gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.feature.sync.JsonBridgeTest --no-parallel"
```
**结果**：`BUILD SUCCESSFUL in 13s`，全量用例全部 PASS，验证了向下兼容性无破坏。

---

## 5. 交付结论

S14 任务涉及的备份覆盖面扩展（S04-5 来源引用与规则数据）、安全单事务合并与旧不覆新机制、非法版本防御拒绝、以及 C2/C3 两处文案纠正已全部落实并通过完整的 JVM 单元测试验证。全程严格恪守所有纪律红线。
