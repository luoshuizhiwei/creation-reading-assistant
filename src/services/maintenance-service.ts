import { getDesktopApi } from "@/services/ipc-client";
import type { AutoBackupRunResult, BackupResult, BuildInfo, DebugExportResult, RendererLogInput, RestoreResult, StartupRecoveryInfo } from "@/types/maintenance";

export async function getBuildInfo(): Promise<BuildInfo> {
  return getDesktopApi().app.getBuildInfo();
}

export async function getStartupRecovery(): Promise<StartupRecoveryInfo> {
  return getDesktopApi().app.getStartupRecovery();
}

export async function markStartupRecoverySeen(): Promise<void> {
  return getDesktopApi().app.markStartupRecoverySeen();
}

export async function openDataDirectory(): Promise<void> {
  return getDesktopApi().app.openDataDirectory();
}

export async function openLogDirectory(): Promise<void> {
  return getDesktopApi().app.openLogDirectory();
}

export async function writeRendererLog(input: RendererLogInput): Promise<void> {
  return getDesktopApi().app.writeRendererLog(input);
}

export async function createBackup(): Promise<BackupResult | null> {
  return getDesktopApi().backup.create();
}

export async function runAutoBackup(): Promise<AutoBackupRunResult> {
  return getDesktopApi().backup.runAuto();
}

export async function restoreBackup(): Promise<RestoreResult | null> {
  return getDesktopApi().backup.restore();
}

export async function exportDebugInfo(): Promise<DebugExportResult | null> {
  return getDesktopApi().diagnostics.exportDebugInfo();
}
