import { useCallback, useEffect, useRef, useState } from "react";
import {
  MOBILE_APP_VERSION,
  checkForMobileUpdate,
  openMobileUpdateUrl,
  type MobileUpdateInfo
} from "../../../services/mobile-updates";

const LAST_CHECK_KEY = "creation-reading-assistant-mobile-update-last-check";
const DISMISSED_KEY = "creation-reading-assistant-mobile-update-dismissed";
/** 自动检查间隔（6 小时），避免频繁请求 GitHub */
const AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000;

function loadLastCheckAt(): number {
  try {
    const raw = localStorage.getItem(LAST_CHECK_KEY);
    return raw ? Number.parseInt(raw, 10) || 0 : 0;
  } catch {
    return 0;
  }
}

function saveLastCheckAt(timestamp: number): void {
  try {
    localStorage.setItem(LAST_CHECK_KEY, String(timestamp));
  } catch {
    // 忽略
  }
}

function loadDismissedVersion(): string | undefined {
  try {
    return localStorage.getItem(DISMISSED_KEY) || undefined;
  } catch {
    return undefined;
  }
}

function saveDismissedVersion(version: string | undefined): void {
  try {
    if (version) localStorage.setItem(DISMISSED_KEY, version);
    else localStorage.removeItem(DISMISSED_KEY);
  } catch {
    // 忽略
  }
}

export function useUpdateCheck({ onMessage }: { onMessage: (value: string) => void }) {
  const [updateInfo, setUpdateInfo] = useState<MobileUpdateInfo>();
  const [updateBusy, setUpdateBusy] = useState(false);
  const [lastCheckAt, setLastCheckAt] = useState<number>(() => loadLastCheckAt());
  const [dismissedVersion, setDismissedVersion] = useState<string | undefined>(() => loadDismissedVersion());
  const [autoChecked, setAutoChecked] = useState(false);
  const silentRef = useRef(false);

  const runCheck = useCallback(async (silent: boolean) => {
    if (silent) silentRef.current = true;
    if (!silent) setUpdateBusy(true);
    try {
      const info = await checkForMobileUpdate();
      setUpdateInfo(info);
      const now = Date.now();
      setLastCheckAt(now);
      saveLastCheckAt(now);
      if (!silent) {
        onMessage(info.hasUpdate ? `发现新版本 v${info.latestVersion}。` : "当前已经是最新版本。");
      }
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      setUpdateInfo({
        currentVersion: MOBILE_APP_VERSION,
        latestVersion: MOBILE_APP_VERSION,
        hasUpdate: false,
        releaseUrl: "https://github.com/luoshuizhiwei/creation-reading-assistant/releases",
        notes: "更新源暂时不可访问。可以打开发布页手动查看最新安装包。",
        checkFailed: true
      });
      if (!silent) onMessage(detail);
    } finally {
      if (!silent) setUpdateBusy(false);
      silentRef.current = false;
    }
  }, [onMessage]);

  const checkUpdate = useCallback(() => runCheck(false), [runCheck]);

  // 应用启动后自动静默检查一次（距上次检查超过 6 小时）
  useEffect(() => {
    if (autoChecked) return;
    const elapsed = Date.now() - lastCheckAt;
    if (elapsed < AUTO_CHECK_INTERVAL_MS) {
      setAutoChecked(true);
      return;
    }
    setAutoChecked(true);
    void runCheck(true);
  }, [autoChecked, lastCheckAt, runCheck]);

  const openUpdate = useCallback(() => {
    if (!updateInfo) return;
    openMobileUpdateUrl(updateInfo);
    onMessage(updateInfo.apkUrl ? "已打开新版 APK 下载。下载完成后按系统提示安装。" : "已打开 GitHub Release 页面。");
  }, [updateInfo, onMessage]);

  const dismissUpdate = useCallback(() => {
    if (updateInfo?.latestVersion) {
      setDismissedVersion(updateInfo.latestVersion);
      saveDismissedVersion(updateInfo.latestVersion);
    }
  }, [updateInfo]);

  const restoreDismissed = useCallback(() => {
    setDismissedVersion(undefined);
    saveDismissedVersion(undefined);
  }, []);

  // 是否显示更新提示横幅：有更新 + 该版本未被忽略
  const showUpdateBanner = Boolean(
    updateInfo?.hasUpdate &&
      !updateInfo.checkFailed &&
      updateInfo.latestVersion !== dismissedVersion
  );

  return {
    updateInfo,
    updateBusy,
    lastCheckAt,
    showUpdateBanner,
    checkUpdate,
    openUpdate,
    dismissUpdate,
    restoreDismissed
  };
}
