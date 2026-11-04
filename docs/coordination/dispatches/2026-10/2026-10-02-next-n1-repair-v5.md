# N1-repair-v5：修复 ZIP 恢复的正文身份与旧状态完成判定

> 候选独立工作目录：`E:\creation-reading-assistant\next-round-n1-repair-v5\android`
> 父输入：`E:\creation-reading-assistant\next-round-n1-repair-v4\android`
> 共享仓库产品代码只读；不合流、不 ADB、不安装 APK、不访问正式书库。

## 1. 任务编号

**N1-repair-v5** — 关闭 v4 review 提出的两个 P1 反例：

- **P1-1（equal 绕过正文身份）**：A 的 JSON 已提交、正文复制失败留下 hash(A正文) 义务；
  随后导入 JSON 相同、同路径但正文为 B 的 ZIP。v4 生产代码仅凭 `isEqualRel` 解除正文保护，
  放行混合导出。equal 只说明 DB 版本相等，不证明正文身份。
- **P1-2（旧长度判定完成）**：v2 旧状态目标值为长度 "11"，磁盘旧正文也为 11 字节但内容不同。
  v4 生产代码凭长度相同 clear 旧状态。源文件长度相同不能证明原正文身份。

## 2. 输入指纹

### 2.1 共享仓库基线

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 预期 HEAD：`e39a78da9c62f02614207cffb36969a32492eaea`
- 实际 HEAD（开工核实）：`e39a78da9c62f02614207cffb36969a32492eaea` ✓

### 2.2 父输入 SHA（verification.json n1_hashes 核查）

| 文件 | v4 SHA-256 | v5 SHA-256 | 状态 |
|------|-----------|-----------|------|
| `LocalZipBackup.kt` | `3D20CAA8...` | `409F3801...` | **CHANGED** |
| `LocalZipRestoreStateStore.kt` | `DF5EC13E...` | `167B881A...` | **CHANGED** |
| `JsonBridge.kt` | `F8E455A4...` | `F8E455A4...` | same |
| `LocalZipBackupTest.kt` | `50B1D1D5...` | `618534DC...` | **CHANGED** |
| `LocalZipRestoreStateStoreTest.kt` | `E8DF1ABD...` | `9EBA0A91...` | **CHANGED** |
| `JsonBridgeTest.kt` | `BCC27A4D...` | `BCC27A4D...` | same |
| `ProfileViewModel.kt` | `124517D2...` | `124517D2...` | same |
| `ProfileRoute.kt` | `89A793BA...` | `89A793BA...` | same |

v4 八文件 SHA 全部与 verification.json 匹配 ✓

## 3. 所有权与改动

### 3.1 所有权范围（六文件）

仅修改新候选中以下六个文件，实际改动四个：

1. `app/src/main/java/com/creationreadingassistant/feature/sync/LocalZipBackup.kt` — **改动**
2. `app/src/main/java/com/creationreadingassistant/feature/sync/LocalZipRestoreStateStore.kt` — **改动**
3. `app/src/main/java/com/creationreadingassistant/feature/sync/JsonBridge.kt` — 未改
4. `app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipBackupTest.kt` — **改动**
5. `app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipRestoreStateStoreTest.kt` — **改动**
6. `app/src/test/java/com/creationreadingassistant/feature/sync/JsonBridgeTest.kt` — 未改

Profile 两文件及其他源码保持父输入 ✓

### 3.2 改动清单

#### P1-1 修复 — `LocalZipBackup.kt`（补偿授权条件）

**位置**：`importZip` 补偿判定块（原 v4 第 276 行）

**v4 缺陷**：
```kotlin
val needCompensate = (isMatchedObligation || isEqualRel || isSameFingerprintCandidate || isStartedEmptyFingerprint) &&
    (!dstFile.isFile || computeSha256(dstFile) != srcHash)
```
`isEqualRel` 单独即可授权补偿，即使存在继承义务且源内容不匹配该义务。

**v5 修复**：
```kotlin
val hasConflictingObligation = expectedObligation != null && !isMatchedObligation
val equalAuthorized = isEqualRel && !hasConflictingObligation
val needCompensate = (isMatchedObligation || equalAuthorized || isSameFingerprintCandidate || isStartedEmptyFingerprint) &&
    (!dstFile.isFile || computeSha256(dstFile) != srcHash)
```
当存在继承义务且源内容不匹配该义务时，equal 不得单独授权补偿。

#### P1-2 修复 — `LocalZipRestoreStateStore.kt`（matchesTarget）

**位置**：`companion object` 内 `matchesTarget`（原 v4 第 333 行）

**v4 缺陷**：
```kotlin
return if (isLegacyLength(expected)) {
    val expectedLen = expected.toLongOrNull() ?: return false
    file.length() == expectedLen
} else {
    computeSha256(file) == expected
}
```
旧长度格式只比文件长度，长度相同即判完成。

**v5 修复**：
```kotlin
if (isLegacyLength(expected)) return false
return computeSha256(file) == expected
```
旧长度格式一律返回 false，阻断收敛直到可确认来源的重试建立 SHA 证明。

#### P1-2 修复 — `LocalZipBackup.kt`（matchesObligation）

**位置**：`matchesObligation` 私有方法（原 v4 第 401 行）

**v5 修复**：
```kotlin
if (LocalZipRestoreStateStore.isLegacyLength(expected)) return false
return srcHash == expected
```
义务匹配同样不接受旧长度格式，必须基于 SHA-256 内容指纹。

#### 测试改动

- `LocalZipBackupTest.kt`：新增反例 `N1v5_P1_equalClassificationDoesNotBypassContentIdentity`
  （使用真实 JsonBridge 合并分类 + 受控 DAO，非 mock ImportMergeResult）
- `LocalZipRestoreStateStoreTest.kt`：
  - 新增反例 `N1v5_P1_legacyLengthCannotProveContentIdentityAndBlocksClear`
  - 更新 v4 既有测试 `N1v4_P2_legacyLengthFormatStateMigrationDoesNotTreatLengthAsSha`
    期望从"clear 成功"改为"clear 拒绝并保留阻断"（v5 合同强化）

### 3.3 保留的既有修复

以下 v4 已成立的事务内计划、strictly-newer 保护、A→B→A 匹配义务补偿和 finally journal 修复全部保留，对应回归测试在绿跑中通过：

- `N1v4_P1_strictlyNewerProtectedAgainstEmptyFingerprintRetry` ✓
- `N1v4_P2_abaConvergence` ✓
- `N1v4_P2_copyWithStagingFinallyDeleteFailurePreservesJournal` ✓
- `N1v3_W3a_backupBLowerVersionDoesNotOverwriteAndPreservesAObligation` ✓
- `N1v3_W3b_sameBackupJsonButDifferentContentIsNotSameBackupRetry` ✓
- `N1v3_W4_tornStagingDeleteFailureKeepsJournalEntryAndExcludesFromExport` ✓
- `preCommitPlan failure aborts transaction and prevents upsert` ✓
- `mid-import failure propagates for transaction rollback` ✓
- 其余全部既有回归见绿跑清单

## 4. 环境

- OS：Windows（PowerShell）
- 仓库根：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- v5 候选：`E:\creation-reading-assistant\next-round-n1-repair-v5\android`
- Android SDK：`D:\develop\Android\Sdk`
- Gradle：`.\gradlew`（项目自带 wrapper）
- Kotlin 守护进程：`-Dfile.encoding=UTF-8`（gradle.properties，未改动）
- 路径无中文（已根治的类路径编码问题不适用）
- 日期：2026-10-02

## 5. 命令与结果

### 5.1 编译

```
.\gradlew :app:compileDebugUnitTestKotlin --console=plain
```
结果：**BUILD SUCCESSFUL** in 37s

### 5.2 红跑（v4 生产代码 + v5 反例测试）

```
.\gradlew :app:testDebugUnitTest --tests "...LocalZipBackupTest.N1v5_P1_equalClassificationDoesNotBypassContentIdentity"
.\gradlew :app:testDebugUnitTest --tests "...LocalZipRestoreStateStoreTest.N1v5_P1_legacyLengthCannotProveContentIdentityAndBlocksClear"
```

结果：**2 FAILED**（预期红）

| 测试 | 失败类型 | 失败行 |
|------|---------|--------|
| `N1v5_P1_equalClassificationDoesNotBypassContentIdentity` | `ComparisonFailure` | LocalZipBackupTest.kt:2064 |
| `N1v5_P1_legacyLengthCannotProveContentIdentityAndBlocksClear` | `AssertionError` | LocalZipRestoreStateStoreTest.kt:336 |

### 5.3 绿跑（v5 修复后，三个受影响 JVM 测试类）

```
.\gradlew :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.feature.sync.LocalZipBackupTest" \
  --tests "com.creationreadingassistant.feature.sync.LocalZipRestoreStateStoreTest" \
  --tests "com.creationreadingassistant.feature.sync.JsonBridgeTest" \
  --console=plain
```

结果：**BUILD SUCCESSFUL** in 31s

| 测试类 | tests | failures | errors | skipped |
|--------|-------|----------|--------|---------|
| `LocalZipRestoreStateStoreTest` | 16 | 0 | 0 | 0 |
| `LocalZipBackupTest` | 57 | 0 | 0 | 0 |
| `JsonBridgeTest` | 20 | 0 | 0 | 0 |
| **合计** | **93** | **0** | **0** | **0** |

未跑全量、Lint、Release 或设备 ✓

## 6. 原始证据

### 6.1 红日志/XML

- `E:\creation-reading-assistant\next-round-n1-repair-v5\verification\red-logs\TEST-LocalZipBackupTest.xml`
  - `N1v5_P1_equalClassificationDoesNotBypassContentIdentity` FAILURE
  - message: `equal 不得单独授权不同正文身份写入：本地正文必须保持 ORIGINAL expected:<[ORIGINAL]> but was:<[B CONTENT]>`
- `E:\creation-reading-assistant\next-round-n1-repair-v5\verification\red-logs\TEST-LocalZipRestoreStateStoreTest.xml`
  - `N1v5_P1_legacyLengthCannotProveContentIdentityAndBlocksClear` FAILURE
  - message: `旧长度不能证明内容一致，状态必须保留阻断`

### 6.2 绿日志/XML

- `E:\creation-reading-assistant\next-round-n1-repair-v5\verification\green-xml\TEST-LocalZipBackupTest.xml`（57 tests, 0 failures）
- `E:\creation-reading-assistant\next-round-n1-repair-v5\verification\green-xml\TEST-LocalZipRestoreStateStoreTest.xml`（16 tests, 0 failures）
- `E:\creation-reading-assistant\next-round-n1-repair-v5\verification\green-xml\TEST-JsonBridgeTest.xml`（20 tests, 0 failures）
- `E:\creation-reading-assistant\next-round-n1-repair-v5\verification\green-logs\green-run-summary.txt`

### 6.3 增量补丁

- `E:\creation-reading-assistant\next-round-n1-repair-v5\verification\patches\n1-repair-v5-incremental.patch`（19131 bytes）
- 包含 4 个改动文件的 unified diff（相对 v4 基线）

### 6.4 关键测试使用真实 JsonBridge

反例 `N1v5_P1_equalClassificationDoesNotBypassContentIdentity` 使用真实 `JsonBridge` 实例和受控 DAO，
通过 `env.revision == local.revision && env.updatedAt == local.updated_at` 触发真实 equal 分类路径
（`equalBookContentPaths` + `skippedBookContentPaths`），非 mock `ImportMergeResult`。

### 6.5 证据分类声明

| 证据类型 | 范围 | 声明 |
|---------|------|------|
| **JVM 单元测试** | 三个受影响测试类，93 tests | 真实 JVM 执行，XML + 控制台日志已保留 |
| **静态事务结论** | `preCommitPlan failure`、`mid-import failure` | JVM 测试中的受控 DAO 验证事务回滚语义，**非真实 Room** |
| **真实 Room** | — | **未实测**。本任务不包含真实 Room 回滚实测 |
| **设备证据** | — | **未采集**。本任务不包含 ADB / 真机 / 模拟器验证 |

## 7. 未覆盖项

1. **真实 Room 事务回滚**：测试使用受控 DAO（FakeBookDao 等），验证事务语义但不等于真实 Room 数据库回滚实测。
2. **设备验证**：未跑 ADB、未安装 APK、未截图。UI 行为未验证。
3. **全量测试**：仅跑三个受影响 JVM 测试类，未跑全量 `:app:testDebugUnitTest`、Lint、Release。
4. **其他产品线**：桌面（`src/`、`electron/`）未涉及。
5. **存储架构扩展**：未扩展存储架构或其他功能。

## 8. 恢复/副作用

- **无共享仓库改动**：共享仓库产品代码只读，唯一写入为本报告文件。
- **无 Git 状态改动**：未暂存、commit、push、reset、checkout。
- **无设备状态改动**：未 ADB、未安装 APK、未访问正式书库。
- **无 Gradle 守护进程改动**：`gradle.properties` 未改，`kotlin.daemon.jvmargs` 保持 `-Dfile.encoding=UTF-8`。
- **v5 候选独立**：所有改动在 `E:\creation-reading-assistant\next-round-n1-repair-v5\android`，不影响其他窗口或 Codex 修改。
- **既有行为变更**：`N1v4_P2_legacyLengthFormatStateMigrationDoesNotTreatLengthAsSha` 测试期望从"clear 成功"改为"clear 拒绝"。这是 v5 合同的有意强化——旧长度格式不再被视为完成证明。依赖旧长度 clear 行为的下游代码（如有）需适配。

## 9. 交付状态

| 交付物 | 路径 | 状态 |
|--------|------|------|
| v5 候选树 | `E:\creation-reading-assistant\next-round-n1-repair-v5\android` | ✓ |
| 增量补丁 | `...\verification\patches\n1-repair-v5-incremental.patch` | ✓ 19131 bytes |
| 红日志/XML | `...\verification\red-logs\` | ✓ 2 files |
| 绿日志/XML | `...\verification\green-xml\` + `green-logs\` | ✓ 4 files |
| 改动 SHA | 见 §2.2 表 | ✓ 4 changed, 4 same |
| 仓库报告 | `docs/coordination/dispatches/2026-10/2026-10-02-next-n1-repair-v5.md` | ✓ 本文件 |
| 越界改动核查 | Profile 两文件 + JsonBridge 两文件 SHA same | ✓ 无越界 |

**完成。交 Codex 独立核查。**
