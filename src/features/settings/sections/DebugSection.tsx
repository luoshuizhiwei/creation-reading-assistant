import { useState } from "react";
import { Download, RefreshCw, RotateCcw } from "lucide-react";
import { InlineNotice } from "@/components/interaction";
import { Button, Field, TextInput } from "@/components/ui";
import { exportDebugInfo, openLogDirectory } from "@/services/maintenance-service";
import { checkForAppUpdates, openAppUpdateDownload } from "@/services/updates-service";
import type { AppSettings, SettingsSection } from "@/types/settings";
import type { AppUpdateInfo } from "@/types/updates";
import { SettingsGroup, SettingsShell } from "./SectionWrapper";

interface DebugSectionProps {
  settings: AppSettings;
  resetSection: (section: SettingsSection) => void;
  showToast: (toast: { tone: "success" | "info" | "warning" | "error"; title: string; body?: string }) => void;
  setError: (error: string) => void;
}

export function DebugSection({ settings, resetSection, showToast, setError }: DebugSectionProps) {
  const [maintenanceMessage, setMaintenanceMessage] = useState("");
  const [updateInfo, setUpdateInfo] = useState<AppUpdateInfo>();
  const [updateBusy, setUpdateBusy] = useState(false);

  const runMaintenance = async (label: string, action: () => Promise<unknown>, success: (result: unknown) => string) => {
    setMaintenanceMessage(`${label}中...`);
    try {
      const result = await action();
      const message = result ? success(result) : `${label}已取消`;
      setMaintenanceMessage(message);
      if (result) showToast({ tone: "success", title: `${label}完成`, body: message });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      setMaintenanceMessage("");
      setError(message);
    }
  };

  const checkUpdates = async () => {
    setUpdateBusy(true);
    try {
      const info = await checkForAppUpdates();
      setUpdateInfo(info);
      showToast({
        tone: info.hasUpdate ? "success" : "info",
        title: info.hasUpdate ? `发现新版本 v${info.latestVersion}` : "当前已是最新版本",
        body: info.hasUpdate ? "可以从应用内打开安装包下载。" : `当前版本：${info.currentVersion}`
      });
    } catch (error) {
      setError(error instanceof Error ? error.message : String(error));
    } finally {
      setUpdateBusy(false);
    }
  };

  const openUpdate = async () => {
    if (!updateInfo) return;
    const url = updateInfo.desktopAssetUrl || updateInfo.releaseUrl;
    if (!url) {
      showToast({ tone: "info", title: "暂无可用下载链接", body: "当前更新信息里没有可打开的安装包或 Release 页面。" });
      return;
    }
    try {
      await openAppUpdateDownload(url);
    } catch (error) {
      setError(error instanceof Error ? error.message : String(error));
    }
  };

  return (
    <SettingsShell title="关于 / 调试">
      <SettingsGroup title="应用信息">
        <div className="grid gap-4 md:grid-cols-2">
          <div data-setting-id="debug.appVersion">
            <Field label="应用版本">
              <TextInput value={settings.debug.appVersion} readOnly />
            </Field>
          </div>
          <div data-setting-id="debug.dataRoot">
            <Field label="数据目录">
              <TextInput value={settings.debug.dataRoot} readOnly />
            </Field>
          </div>
        </div>
      </SettingsGroup>

      <SettingsGroup title="诊断工具">
        <div className="flex flex-wrap gap-2" data-setting-id="debug.diagnostics">
          <Button variant="secondary" onClick={() => void openLogDirectory()}>
            打开日志目录
          </Button>
          <Button
            variant="secondary"
            onClick={() =>
              void runMaintenance("导出调试信息", exportDebugInfo, (result) => {
                const debug = result as Awaited<ReturnType<typeof exportDebugInfo>>;
                return debug ? `调试信息已导出：${debug.outputRoot}` : "导出已取消";
              })
            }
          >
            导出调试信息
          </Button>
        </div>
        {maintenanceMessage && (
          <InlineNotice tone="info" className="mt-3 break-all p-2 text-xs">
            {maintenanceMessage}
          </InlineNotice>
        )}
      </SettingsGroup>

      <SettingsGroup
        title="应用更新"
        description="检查 GitHub Release 上的最新桌面端和 Android 端版本。桌面端会打开新版安装包或 Release 页面；安装仍由 Windows 安装器确认完成。"
        actions={
          <Button variant="quiet" onClick={() => resetSection("debug")}>
            <RotateCcw size={15} />
            重置本分区
          </Button>
        }
      >
        <div data-setting-id="debug.update">
          {updateInfo && (
            <InlineNotice tone={updateInfo.hasUpdate ? "success" : "info"} className="mb-3 p-2 text-xs">
              {updateInfo.hasUpdate
                ? `发现 v${updateInfo.latestVersion}，当前版本 ${updateInfo.currentVersion}。${updateInfo.desktopAssetName ? `安装包：${updateInfo.desktopAssetName}` : "未找到独立安装包，将打开 Release 页面。"}`
                : `当前版本 ${updateInfo.currentVersion} 已是最新。`}
            </InlineNotice>
          )}
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" disabled={updateBusy} onClick={() => void checkUpdates()}>
              <RefreshCw size={15} />
              {updateBusy ? "检查中..." : "检查更新"}
            </Button>
            <Button disabled={!updateInfo?.hasUpdate} onClick={() => void openUpdate()}>
              <Download size={15} />
              下载新版
            </Button>
          </div>
        </div>
      </SettingsGroup>
    </SettingsShell>
  );
}
