import { getDesktopApi } from "@/services/ipc-client";
import type { DeviceInfo, PairingTokenResult, SyncStatus } from "@/types/sync";

export async function getSyncStatus(): Promise<SyncStatus> {
  return getDesktopApi().sync.getStatus();
}

export async function startSyncServer(): Promise<SyncStatus> {
  return getDesktopApi().sync.startServer();
}

export async function stopSyncServer(): Promise<SyncStatus> {
  return getDesktopApi().sync.stopServer();
}

export async function createPairingToken(): Promise<PairingTokenResult> {
  return getDesktopApi().sync.createPairingToken();
}

export async function listSyncDevices(): Promise<DeviceInfo[]> {
  return getDesktopApi().sync.listDevices();
}

export async function removeSyncDevice(deviceId: string): Promise<DeviceInfo[]> {
  return getDesktopApi().sync.removeDevice(deviceId);
}
