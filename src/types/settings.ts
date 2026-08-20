import type { ReaderSettings } from "./library";
import type { AISettings } from "./ai";

export type SettingsSection = "appearance" | "reader" | "ai" | "storage" | "debug";

export interface AppearanceSettings {
  theme: "light" | "dark" | "system";
  appFontScale: number;
  showRightPanel: boolean;
}

export interface StorageSettings {
  dataDirectory: string;
  libraryDirectory: string;
  storageMode: "portable" | "custom" | "fallback";
  lastMigratedAt?: string;
  /** 自动备份保存目录（v2 校验备份的父目录）。 */
  backupDirectory?: string;
  /** 每日 / 升级前自动备份开关。 */
  autoBackupEnabled?: boolean;
  /** 最近一次自动备份成功时间（ISO）。 */
  lastAutoBackupAt?: string;
  /** 最近一次自动备份失败时间（ISO）。 */
  lastAutoBackupFailedAt?: string;
  /** 最近一次自动备份失败的可读原因（不含内部堆栈）。 */
  lastAutoBackupError?: string;
}

export interface StorageLocations {
  dataDirectory: string;
  libraryDirectory: string;
  portableDataDirectory: string;
  fallbackDataDirectory: string;
  storageMode: StorageSettings["storageMode"];
  dataDirectoryWritable: boolean;
  libraryDirectoryWritable: boolean;
}

export interface DebugSettings {
  appVersion: string;
  dataRoot: string;
  showStatsCards: boolean;
}

export interface AppSettings {
  version: number;
  appearance: AppearanceSettings;
  reader: ReaderSettings;
  ai: AISettings;
  storage: StorageSettings;
  debug: DebugSettings;
  updatedAt: string;
}

export interface AppSettingsPatch {
  appearance?: Partial<AppearanceSettings>;
  reader?: Partial<ReaderSettings>;
  ai?: Partial<AISettings>;
  storage?: Partial<StorageSettings>;
  debug?: Partial<DebugSettings>;
}
