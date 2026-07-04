export interface AppUpdateInfo {
  currentVersion: string;
  latestVersion: string;
  hasUpdate: boolean;
  releaseUrl: string;
  notes: string;
  desktopAssetName?: string;
  desktopAssetUrl?: string;
}
