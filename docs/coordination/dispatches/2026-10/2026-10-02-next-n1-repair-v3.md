# N1-repair-v3：ZIP 恢复中断重试与文件归因安全修复

> **候选轮次**：v3（基于 v2 基线）
> **日期**：2026-10-02
> **起始**：2026-10-02T09:07:22+08:00
> **完成**：2026-10-02T10:07:44+08:00
> **Git HEAD**：`e39a78da9c62f02614207cffb36969a32492eaea`（工作树 dirty）
> **状态**：✅ 全部定向测试通过

---

## 1. 任务边界

在六文件授权范围内实施统一修复合同，建立四类失败反例并修复：

| # | 反例 | 路径 | v2 缺陷 | v3 修复 |
|---|------|------|---------|---------|
| W1a | 同 ZIP 重试，旧正文长度不同 | `LocalZipBackup.kt` | 重试只查 `!dstFile.isFile`，文件存在就跳过 | 重试校验 SHA-256 内容指纹 |
| W1b | 同 ZIP 重试，旧正文长度相同内容不同 | 同上 | 验证只比长度，内容错误不可观察 | 验证用 SHA-256 内容指纹 |
| W2 | DB 已提交、markDbCommitted 未落盘 | `LocalZipBackup.kt` | `isSameBackupRetry` 要求 `pendingTargets` 非空，空 pending 不触发补偿 | 指纹为空时退化为全部源目标 |
| W3a | 备份 A 中断后导入备份 B（低版本） | `LocalZipBackup.kt` + `LocalZipRestoreStateStore.kt` | `isSameBackupRetry` 因 pending 非空对任何备份为真；`clear` 只查 isFile | `isSameBackupRetry` 只按指纹判定；`clear` 校验内容指纹 |
| W3b | 同 backup.json 不同正文 | 同上 | `backupFingerprint` 只 hash backup.json | `backupFingerprint` 覆盖正文内容指纹 |
| W4 | 半写 staging + delete 返回 false | `LocalZipBackup.kt` | `recoverCrashResidues` 不检查 `delete()` 返回值 | delete 失败保留当轮条目，staging 留在过滤集 |

## 2. 六文件指纹

### v2 基线（起点）

| 文件 | SHA-256 |
|------|---------|
| `LocalZipBackup.kt` | `2365b1ce23c60b265301a9df0d9a6f47772971bc85f6134e4e23e6faebcec100` |
| `LocalZipRestoreStateStore.kt` | `d5b3863dc2826d115512a95ccc63592874e2e26af6ddb1a8f47a1cd469567915` |
| `ProfileViewModel.kt` | `124517d2800853c80ea24c9af24bf945f7f988f003f8f3168b0cae1e6bb35231` |
| `ProfileRoute.kt` | `89a793ba258cd6cd4269adaf523cbfbaef027f7ff8a2844307ae09dc21482519` |
| `LocalZipBackupTest.kt` | `39e0a38cc0a2927253c25a7f4193b63d929d0ff56d362f25436d43735e39696e` |
| `LocalZipRestoreStateStoreTest.kt` | `76b0264f7a6eac504b564eb2cb1f9084670af9a264b88be739b2e1b145d2a8dd` |

### v3 候选（终点）

| 文件 | SHA-256 | 变更 |
|------|---------|------|
| `LocalZipBackup.kt` | `5b981e8f1e8d6239f78c7ff2968ef8d6055d40c4c99899a74b5cc67489bb4227` | **CHANGED** |
| `LocalZipRestoreStateStore.kt` | `f428e947e93223116af5922256129184175574d1a08bc8c946d8bff6eb23b8ce` | **CHANGED** |
| `ProfileViewModel.kt` | `124517d2800853c80ea24c9af24bf945f7f988f003f8f3168b0cae1e6bb35231` | same |
| `ProfileRoute.kt` | `89a793ba258cd6cd4269adaf523cbfbaef027f7ff8a2844307ae09dc21482519` | same |
| `LocalZipBackupTest.kt` | `f26bec0f0f60d8f0e5d3528547d728b4fb3d03bfd702b7c2e3591644b1275591` | **CHANGED** |
| `LocalZipRestoreStateStoreTest.kt` | `74782c0cbd8f3617e537a8c3845e8de3a4b576471462655a43cd9f6625047c58` | **CHANGED** |

**变更文件数**：4/6（两个生产文件 + 两个测试文件）；`ProfileViewModel.kt` 与 `ProfileRoute.kt` 原样保留。

## 3. 修复合同摘要

### 3.1 `LocalZipRestoreStateStore.kt`

- **类型变更**：`pendingTargets` 和 `unresolvedTargets` 从 `Map<String, Long>`（长度）改为 `Map<String, String>`（SHA-256 内容哈希）。
- **`clear()` 内容校验**：不再只查 `!f.isFile`，改为 `!f.isFile || computeSha256(f) != expectedHash`——内容不符不清状态，保留义务。
- **`computeSha256` 助手**：新增 companion object 方法，64KB 缓冲流式 SHA-256。

### 3.2 `LocalZipBackup.kt`

- **备份身份覆盖正文内容**：`backupFingerprint` 从 `computeSha256(jsonFile)` 改为 `"$backupJsonHash|$contentFingerprint"`，其中 `contentFingerprint` 包含全部书籍正文哈希。同 backup.json 不同正文判为不同备份（W3b）。
- **`isSameBackupRetry` 收窄**：移除 `previousState.pendingTargets.isNotEmpty()` 条件——只按指纹判定（W3a）。
- **重试补偿精确化**：
  - 指纹非空（markDbCommitted 已落盘）：只对 `prev.pendingTargets` 中的目标强制重做，merge 保护（本地较新）的文件不在其中。
  - 指纹为空（DB 已提交但 markDbCommitted 未落盘，W2 现场）：待修集合未知，退化为全部源目标。
- **重试与验证用内容哈希**：`!dstFile.isFile || computeSha256(dstFile) != srcHash` 替代 `!dstFile.isFile` 和长度比较（W1a/W1b）。
- **`recoverCrashResidues` delete 返回值检查**：新增 `fileDeleteOperation` 测试接缝；delete 返回 false 时保留当轮条目并 `continue`，staging 留在 `stagingRels` 过滤集，不混入导出（W4）。

### 3.3 测试文件

- `LocalZipBackupTest.kt`：新增 6 个反例测试（W1a/W1b/W2/W3a/W3b/W4）。
- `LocalZipRestoreStateStoreTest.kt`：`Map<String, Long>` → `Map<String, String>` 适配。

## 4. 测试证据

### 红日志（v2 基线反例）

| 测试类 | tests | failures | errors |
|--------|-------|----------|--------|
| `LocalZipBackupTest`（6 反例子集） | 6 | 6 | 0 |

路径：`E:\creation-reading-assistant\next-round-n1-repair-v3\verification\red-logs\`

### 绿日志（v3 修复后全量）

| 测试类 | tests | failures | errors | skipped |
|--------|-------|----------|--------|---------|
| `LocalZipBackupTest` | 53 | 0 | 0 | 0 |
| `LocalZipRestoreStateStoreTest` | 14 | 0 | 0 | 0 |
| **合计** | **67** | **0** | **0** | **0** |

路径：`E:\creation-reading-assistant\next-round-n1-repair-v3\green-logs\`

**回归检查**：v2 既有测试 `reimportAfterPartialFailureConvergesToBackupBytesAndKeepsProtectedFiles` 在首次修复后一度回归失败（重试循环遍历 `allSrcTargets` 覆盖了受保护文件），经精确化重试补偿范围后修复。最终 67 测试全绿。

## 5. 增量补丁

- **路径**：`E:\creation-reading-assistant\next-round-n1-repair-v3\patch\n1-repair-v3.patch`
- **大小**：28547 字节
- **格式**：`git diff --no-index --text`（UTF-8，unified diff）
- **覆盖**：4 个变更文件的完整 hunks

## 6. 候选目录

```
E:\creation-reading-assistant\next-round-n1-repair-v3\
├── android\          # 独立可构建的 v3 候选
├── green-logs\       # v3 全量绿日志 + XML
├── patch\            # 相对 v2 的增量补丁
└── verification\
    └── red-logs\     # v2 基线反例红日志
```

## 7. 未验证项

- **真机设备验证**：本轮仅 JVM 单元测试，未执行 adb 设备批次。
- **kill -9 不可由 JVM 证明**：W4 证明的是残留的后验后果（delete 返回 false 时当轮条目保留），非进程被杀现场。
- **DB 侧幂等**：由既有 MuMu 用例 6 与 SyncMergePolicy 证据覆盖，本轮不重复。
