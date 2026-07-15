import mobilePackage from "../../package.json";

export const MOBILE_APP_VERSION = mobilePackage.version;

// 源码仓库保持私有；安装包和无凭证更新清单发布到独立的公开仓库。
// APK 内绝不能嵌入 GitHub token，否则任何安装者都能提取凭证。
const PUBLIC_RELEASE_REPOSITORY = "luoshuizhiwei/creation-reading-assistant-releases";
const RELEASE_API_URL = `https://api.github.com/repos/${PUBLIC_RELEASE_REPOSITORY}/releases/latest`;
const RELEASES_URL = `https://github.com/${PUBLIC_RELEASE_REPOSITORY}/releases`;
const RELEASE_MANIFEST_URL = `https://github.com/${PUBLIC_RELEASE_REPOSITORY}/releases/latest/download/mobile-update.json`;

export interface MobileUpdateInfo {
  currentVersion: string;
  latestVersion: string;
  hasUpdate: boolean;
  releaseUrl: string;
  notes: string;
  apkUrl?: string;
  apkName?: string;
  checkFailed?: boolean;
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

interface MobileUpdateManifest {
  version?: string;
  releaseUrl?: string;
  notes?: string;
  apkUrl?: string;
  apkName?: string;
}

async function fetchWithTimeout(input: RequestInfo | URL, init: RequestInit = {}, timeoutMs = 10_000): Promise<Response> {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(input, { ...init, signal: controller.signal });
  } finally {
    window.clearTimeout(timer);
  }
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
  try {
    const manifestResponse = await fetchWithTimeout(`${RELEASE_MANIFEST_URL}?t=${Date.now()}`, {
      cache: "no-store"
    });
    if (manifestResponse.ok) {
      const manifest = (await manifestResponse.json()) as MobileUpdateManifest;
      const latestVersion = manifest.version?.replace(/^v/i, "") || MOBILE_APP_VERSION;
      return {
        currentVersion: MOBILE_APP_VERSION,
        latestVersion,
        hasUpdate: isNewerVersion(latestVersion, MOBILE_APP_VERSION),
        releaseUrl: manifest.releaseUrl || RELEASES_URL,
        notes: manifest.notes || "暂无更新说明。",
        apkUrl: manifest.apkUrl,
        apkName: manifest.apkName
      };
    }
  } catch {
    // The static manifest is the preferred path, but it may not exist before the next release.
  }

  try {
    const response = await fetchWithTimeout(RELEASE_API_URL, {
      headers: { Accept: "application/vnd.github+json" },
      cache: "no-store"
    });
    if (!response.ok) {
      throw new Error(String(response.status));
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
      releaseUrl: release.html_url || RELEASES_URL,
      notes: release.body || "暂无更新说明。",
      apkUrl: apkAsset?.browser_download_url,
      apkName: apkAsset?.name
    };
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`更新源暂时不可访问，可能是 GitHub 限流或网络限制。你仍然可以打开发布页手动下载。${detail ? `（${detail}）` : ""}`);
  }
}

export function openMobileUpdateUrl(info: MobileUpdateInfo): void {
  window.open(info.apkUrl || info.releaseUrl || RELEASES_URL, "_blank", "noopener,noreferrer");
}
