export const MOBILE_APP_VERSION = "0.1.16";

const RELEASE_API_URL = "https://api.github.com/repos/luoshuizhiwei/creation-reading-assistant/releases/latest";

export interface MobileUpdateInfo {
  currentVersion: string;
  latestVersion: string;
  hasUpdate: boolean;
  releaseUrl: string;
  notes: string;
  apkUrl?: string;
  apkName?: string;
}

interface GitHubReleaseAsset {
  name?: string;
  browser_download_url?: string;
}

interface GitHubRelease {
  tag_name?: string;
  html_url?: string;
  body?: string;
  assets?: GitHubReleaseAsset[];
}

function normalizeVersion(value: string): number[] {
  return value
    .replace(/^v/i, "")
    .split(".")
    .map((part) => Number.parseInt(part.replace(/[^\d].*$/, ""), 10))
    .map((part) => (Number.isFinite(part) ? part : 0));
}

function isNewerVersion(latest: string, current: string): boolean {
  const left = normalizeVersion(latest);
  const right = normalizeVersion(current);
  const length = Math.max(left.length, right.length, 3);
  for (let index = 0; index < length; index += 1) {
    const latestPart = left[index] ?? 0;
    const currentPart = right[index] ?? 0;
    if (latestPart > currentPart) return true;
    if (latestPart < currentPart) return false;
  }
  return false;
}

export async function checkForMobileUpdate(): Promise<MobileUpdateInfo> {
  const response = await fetch(RELEASE_API_URL, {
    headers: { Accept: "application/vnd.github+json" }
  });
  if (!response.ok) {
    throw new Error(`检查更新失败：GitHub 返回 ${response.status}`);
  }
  const release = (await response.json()) as GitHubRelease;
  const latestVersion = release.tag_name?.replace(/^v/i, "") || MOBILE_APP_VERSION;
  const apkAsset = release.assets?.find((asset) => {
    const name = asset.name?.toLowerCase() ?? "";
    return name.endsWith(".apk") || name.includes("android");
  });
  return {
    currentVersion: MOBILE_APP_VERSION,
    latestVersion,
    hasUpdate: isNewerVersion(latestVersion, MOBILE_APP_VERSION),
    releaseUrl: release.html_url || "https://github.com/luoshuizhiwei/creation-reading-assistant/releases",
    notes: release.body || "暂无更新说明。",
    apkUrl: apkAsset?.browser_download_url,
    apkName: apkAsset?.name
  };
}

export function openMobileUpdateUrl(info: MobileUpdateInfo): void {
  window.open(info.apkUrl || info.releaseUrl, "_blank", "noopener,noreferrer");
}
