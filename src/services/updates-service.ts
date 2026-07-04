import { getDesktopApi } from "@/services/ipc-client";
import type { AppUpdateInfo } from "@/types/updates";

export async function checkForAppUpdates(): Promise<AppUpdateInfo> {
  return getDesktopApi().updates.check();
}

export async function openAppUpdateDownload(url: string): Promise<void> {
  return getDesktopApi().updates.openDownload(url);
}
