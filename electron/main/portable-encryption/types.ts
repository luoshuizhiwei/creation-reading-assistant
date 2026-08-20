/**
 * 可移植加密（深模块）对外类型。
 *
 * 该模块刻意不依赖任何公共 seam（不导入 operation coordinator 的实现、不导入
 * creation-workspace 的数据库实现），只通过 `EncryptionProgress` 接口与调用方的
 * operation 进度/取消机制对接——`OperationController` 在结构上满足该接口。
 */

export type PayloadKind = "bundle" | "backup" | "generic";

export type EncryptionErrorCode =
  | "invalid-input"
  | "unsupported-format"
  | "auth-failed"
  | "truncated"
  | "cancelled"
  | "staging-failed"
  | "cleanup-failed";

export interface EncryptionProgress {
  /** 调用方在已请求取消时抛出取消错误（真实实现抛出 OperationCancelledError）。 */
  throwIfCancelled(): void;
  /**
   * 报告进度。深模块只上报 `bytesCompleted`/`bytesTotal`（单调递增），
   * 满足 operation coordinator 的单调性与不变量。
   */
  setProgress(progress: {
    bytesCompleted?: number;
    bytesTotal?: number | null;
    completed?: number;
    total?: number | null;
    indeterminate?: boolean;
  }): void;
}

export type LogLevel = "info" | "warn" | "error";

export type Logger = (level: LogLevel, message: string, meta?: Record<string, unknown>) => void;

export interface EncryptOptions {
  /** 待加密的明文目录（备份快照或项目包目录）。 */
  sourceDir: string;
  /** 输出的加密容器文件路径。 */
  targetFile: string;
  /** 用户口令。深模块不会保存口令、派生密钥或可恢复明文。 */
  passphrase: string;
  payloadKind?: PayloadKind;
  /** 内部明文格式版本（如备份 v1/v2、包 v1/v2），仅用于元数据与日志。 */
  innerVersion?: number;
  operation?: EncryptionProgress;
  logger?: Logger;
}

export interface DecryptOptions {
  /** 加密容器文件路径。 */
  containerFile: string;
  passphrase: string;
  operation?: EncryptionProgress;
  logger?: Logger;
}

export interface DecryptResult {
  stagingDir: string;
  payloadKind: PayloadKind;
  innerVersion?: number;
  fileCount: number;
  bytesTotal: number;
}
