# P1-ENCRYPTION-REPORT：项目包与完整备份的可选口令加密

> **历史存档（2026-09-04 标注）**：本文为 2026-08 P1 轮次交付记录，结论仅对当时代码有效。
> 桌面端此后已演进（清样工作台视觉重设计、创作雷达/AI 上下文包、阅读器升级），
> 当前状态以 `docs/handoff/current.md` 为准。

> 范围：实现“可选口令加密”深模块、合同与最小 UI，**不接公共 seam、不修改 operation coordinator**。
> 与上一阶段 P1-F08 同理，加密深模块只通过 `EncryptionProgress` 接口对接 operation 进度/取消，
> 由最终集成者在公共 seam 中接线（见文末 SEAM REQUEST）。

## 一、交付文件

新建（深模块 + 集成层 + 合同 + UI）：

- `electron/main/portable-encryption/types.ts` — 对外类型（`EncryptionProgress`、`EncryptOptions`、`DecryptOptions`、`DecryptResult`、`EncryptionError` 等）。
- `electron/main/portable-encryption/archive.ts` — 流式二进制归档（帧格式）+ `ArchiveExtractor`（增量落盘、内存有界、强制关闭未结束流）。
- `electron/main/portable-encryption/index.ts` — 核心 API：`encryptDirectory` / `decryptContainerToStaging` / `withDecryptedStaging`，以及 `EncryptionError`。
- `electron/main/portable-encryption/contract.ts` — 11 项合同场景。
- `electron/main/portable-encryption/run-contract.mjs` — 合同运行器（tsc + esbuild + electron-as-node）。
- `electron/main/backup/encrypted-backup.ts` — 备份加密集成层（`encryptBackupSnapshotToContainer` / `withDecryptedBackupStaging`）。
- `electron/main/creation-bundle/encrypted-bundle.ts` — 项目包加密集成层（`encryptBundleToContainer` / `withDecryptedBundleStaging`）。
- `src/features/settings/encryption/PassphraseDialog.tsx` + `encryption.css` + `__tests__/passphrase-dialog.test.tsx` — 最小 UI（口令输入 + 二次确认 + 忘记口令不可恢复提示）。

未修改：android / archives / Obsidian / operation coordinator / 任何公共 seam 文件；未 commit / push / stage。

## 二、设计

加密是**可选的容器封装**，内层明文格式（备份 v1/v2、项目包 v1/v2）保持不变，因此明文继续兼容。

容器文件布局（字节序）：

```
[4B magic "CRPK"][1B major][1B minor][4B headerLen][headerLen 字节 JSON 头]
[AEAD 密文（内含二进制归档帧）]
[16B GCM 认证标签]
```

- 明文头部含 KDF（scrypt: N/r/p/keylen/salt）与 AEAD（aes-256-gcm: ivLen/tagLen/iv）参数及 salt/nonce，**每次随机**。
- 密文为“目录归档”的 AES-256-GCM 输出；GCM 认证标签提供完整性/防篡改。
- 解密先落到 `os.tmpdir()` 下 `cr-decrypt-*` 受控临时目录，验证通过后才交还调用方进入现有备份/包验证流程；任何失败都清理临时目录。

深模块不依赖 operation coordinator 实现，只接受 `EncryptionProgress`（其 `setProgress`/`throwIfCancelled` 与 `OperationController` 结构兼容），因此可在不修改 coordinator 的前提下接入进度与取消。

## 三、12 项要求对照

| # | 要求 | 落实 |
|---|------|------|
| 1 | 明文 v1/v2 备份与项目包继续兼容 | 加密仅作外层容器封装，内层格式不变；合同 `old-format-compatible-v1-v2` 验证 v1/v2 备份与项目包内层 `formatVersion` 原样保留；`plaintext-not-mistaken-for-container` 验证明文文件不会被误识别为容器 |
| 2 | 加密格式版本化，头部含 KDF/AEAD 参数、salt、元数据 | `CONTAINER_FORMAT_MAJOR/MINOR` + 明文 JSON 头含 `kdf`/`aead`（算法、参数、base64 salt/iv）与 `payload`（kind/innerVersion/条目数/字节数） |
| 3 | Node 官方 crypto：scrypt 派生、AES-256-GCM、随机 salt/nonce | `crypto.scryptSync` + `createCipheriv("aes-256-gcm")`；salt/iv 每次 `randomBytes` |
| 4 | 不保存口令、派生密钥或可恢复明文 | 口令仅作参数、密钥 `finally` 中 `fill(0)`；明文只在受控临时目录、用后清理 |
| 5 | 错误口令 / 篡改 / 截断在写入目标前失败 | GCM 认证失败映射到 `auth-failed`（错误口令、篡改）或 `truncated`（截断不足标签）；均先于最终目标提交，且临时目录被清理 |
| 6 | 不产生部分解压目录或半恢复数据 | 解密仅落临时目录，失败即 `rm`；`withDecryptedStaging` 保证无论成败都清理 |
| 7 | plaintext staging 限制在受控临时目录并保证清理 | `mkdtemp(os.tmpdir(), "cr-decrypt-")` + `safeRemoveDir` 带退避重试；`withDecryptedStaging` 兜底清理 |
| 8 | 加解密接入现有 operation 进度和取消 | 深模块通过 `EncryptionProgress` 上报 `bytesCompleted/bytesTotal` 并检查 `throwIfCancelled`；集成层把 `OperationController` 直接透传（结构兼容，未改 coordinator） |
| 9 | UI 明确提示“忘记口令无法恢复”并要求二次确认 | `PassphraseDialog` 显示警告文案，重复输入一致 + 勾选已知晓后才可提交 |
| 10 | 日志/错误不含口令、密钥或正文 | 仅记录计数/字节/错误码；`EncryptionError` 消息为通用中文，不泄露口令/密钥/正文 |
| 11 | 大文件流式处理，不整体载入内存 | 归档按 chunk 流式读写；`ArchiveExtractor` 将单文件内容跨 chunk 增量落盘，内存有界（合同 `large-file-streamed-boundary` 含 32 MiB 文件） |
| 12 | 密文完整性校验通过后才进入现有验证流程 | 解密成功才返回 staging；`integrity-then-existing-verify-flow` 场景在解密后调用真实 `parseBackupManifest` 验证 |

## 四、合同覆盖

`electron/main/portable-encryption/contract.ts` 覆盖全部要求场景：

- round-trip-plaintext-preserved（含空口令拒绝）
- wrong-passphrase-fails-before-staging
- tampered-ciphertext-fails
- truncated-container-fails（短截断→`truncated`，半截断→`auth-failed`）
- cancel-aborts-and-cleans（解密取消，临时目录清理）
- old-format-compatible-v1-v2（备份/包 v1/v2 内层保留）
- integrity-then-existing-verify-flow（密文通过后再跑现有备份验证）
- large-file-streamed-boundary（32 MiB + 边界尺寸文件）
- plaintext-not-mistaken-for-container
- no-staging-leak-at-end（本次运行无临时目录泄漏）

运行：`node electron/main/portable-encryption/run-contract.mjs`

结果：**11/11 合同通过**（tsc 对本模块新增文件 0 错误；工作区中 `creation-card-io` 的既有类型错误属其他所有者，不在本定向合同范围）。

UI 测试：`npx vitest run src/features/settings/encryption` → **8/8 通过**。

## 五、公共 SEAM REQUEST（交由最终集成者接线）

深模块已 seam-ready（接受 `OperationController` 作为 `EncryptionProgress`），集成层 `encrypted-backup.ts` / `encrypted-bundle.ts` 已就绪。需要在公共 seam 中完成以下接线：

1. **`electron/main/operation/types.ts`**：在 `OperationKind` 联合类型中新增
   `"backup.export-encrypted"` / `"backup.import-encrypted"` / `"bundle.export-encrypted"` / `"bundle.import-encrypted"`。
   （coordinator 无需改逻辑：深模块在 run 内调用 `throwIfCancelled`/`setProgress` 即已协作取消与进度。）

2. **`electron/main/creation-workspace/creation-ipc.ts`**（或备份 IPC 所在文件）：新增 4 个 handler：
   - `creation:backupExportEncrypted({ backupRoot, targetFile, passphrase })` → `coordinator.start("backup.export-encrypted", () => encryptBackupSnapshotToContainer({ ..., operation: op }))`。
   - `creation:backupImportEncrypted({ containerFile, passphrase })` → `coordinator.start("backup.import-encrypted", () => withDecryptedBackupStaging({...}, (staging) => restoreBackupFromDirectory({ backupRoot: staging, ... })))`。
   - `creation:bundleExportEncrypted` / `creation:bundleImportEncrypted` 类比，导入回调内调用 `importProjectBundleDirectory({ bundleDirectory: staging, ... })`。
   - 错误映射：`EncryptionError.code` → 用户态消息（`auth-failed`/`truncated` → “口令错误或文件已损坏”；`cancelled` → “已取消”）。**切勿**在错误中回显口令/密钥。

3. **`electron/preload/index.ts`**：暴露 `backupExportEncrypted` / `backupImportEncrypted` / `bundleExportEncrypted` / `bundleImportEncrypted`，签名带 `onProgress`/`onCancel`（与现有 creation IPC 一致）。

4. **`src/types/creation.ts` + `src/types/api.ts`**：新增请求/响应类型与 IPC 通道签名（导出/导入参数、进度载荷）。

5. **`src/services/creation-service.ts` + `src/hooks/useCreationActions.ts`**：新增对应方法，调用 preload 并向上暴露进度与取消。

6. **UI 接线**：在导出/备份对话框（如 `CreationProjectsPage` / `src/features/creation/export`）中，当用户勾选“加密”时弹出已实现好的 `PassphraseDialog`（`src/features/settings/encryption/PassphraseDialog.tsx`）采集口令，确认后再调用上述 service 方法；长任务使用现有 operation 进度/取消 UI。深模块与集成层无需改动。

> 备注：`run-contract.mjs` 对整体 `tsc -p tsconfig.main.json` 仅在本模块新增文件出现类型错误时才失败，避免阻塞其他所有者正在进行的并行改动。
