import { getDesktopApi } from "@/services/ipc-client";
import type { AppSettings, AppSettingsPatch, SettingsSection, StorageLocations } from "@/types/settings";

export async function getSettings(): Promise<AppSettings> {
  return getDesktopApi().settings.get();
}

export async function updateSettings(patch: AppSettingsPatch): Promise<AppSettings> {
  return getDesktopApi().settings.update(patch);
}

export async function resetSettingsSection(section: SettingsSection): Promise<AppSettings> {
  return getDesktopApi().settings.resetSection(section);
}

export async function resetReaderSettings(): Promise<AppSettings> {
  return getDesktopApi().settings.resetReaderSettings();
}

export async function chooseDataDirectory(): Promise<string | null> {
  return getDesktopApi().settings.chooseDataDirectory();
}

export async function chooseLibraryDirectory(): Promise<string | null> {
  return getDesktopApi().settings.chooseLibraryDirectory();
}

export async function chooseBackupDirectory(): Promise<string | null> {
  return getDesktopApi().settings.chooseBackupDirectory();
}

export async function migrateDataDirectory(targetDirectory: string): Promise<AppSettings> {
  return getDesktopApi().settings.migrateDataDirectory(targetDirectory);
}

export async function migrateLibraryDirectory(targetDirectory: string): Promise<AppSettings> {
  return getDesktopApi().settings.migrateLibraryDirectory(targetDirectory);
}

export async function getStorageLocations(): Promise<StorageLocations> {
  return getDesktopApi().storage.getLocations();
}
