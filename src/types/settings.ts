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
