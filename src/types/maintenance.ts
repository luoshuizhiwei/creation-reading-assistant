export interface BuildInfo {
  appName: string;
  appVersion: string;
  isPackaged: boolean;
  platform: string;
  arch: string;
  electronVersion: string;
  chromeVersion: string;
  nodeVersion: string;
  appPath: string;
  dataRoot: string;
  logsRoot: string;
  buildMode: "development" | "production";
}

export interface StartupRecoveryInfo {
  abnormalExit: boolean;
  previousStartedAt?: string;
  previousShutdownAt?: string;
  recoveredSessionsCount: number;
  checkedAt: string;
}

export interface BackupResult {
  backupRoot: string;
  manifestPath: string;
  createdAt: string;
  appDataCopied: boolean;
}

export interface AutoBackupRunResult {
  backupRoot: string;
  manifestPath: string;
  createdAt: string;
}

export interface RestoreResult {
  backupRoot: string;
  restoredAt: string;
  checkpointPath?: string;
  restartRecommended: boolean;
}

export interface DebugExportResult {
  outputRoot: string;
  infoPath: string;
  logsCopied: boolean;
  createdAt: string;
}

export interface RendererLogInput {
  level: "info" | "warn" | "error";
  message: string;
  detail?: string;
  source?: string;
}
