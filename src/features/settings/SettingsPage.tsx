import { useCallback, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { Download, FolderOpen, QrCode, RefreshCw, RotateCcw, Smartphone, Wifi, WifiOff } from "lucide-react";
import QRCode from "qrcode";
import { AnimatedPanel, InlineNotice } from "@/components/interaction";
import { Button, Field, TextInput } from "@/components/ui";
import { useSettingsActions } from "@/hooks/useSettingsActions";
import { useOperation } from "@/hooks/useOperation";
import { clearAIApiKey, saveAIApiKey, testAIConnection, updateAISettings } from "@/services/ai-service";
import { exportDebugInfo, openDataDirectory, openLogDirectory, runAutoBackup } from "@/services/maintenance-service";
import { OperationProgressDialog } from "@/features/creation/operation/OperationProgressDialog";
import {
  chooseDataDirectory,
  chooseBackupDirectory,
  chooseLibraryDirectory,
  getStorageLocations,
  migrateDataDirectory,
  migrateLibraryDirectory,
  resetReaderSettings
} from "@/services/settings-service";
import { createPairingToken, getSyncStatus, listSyncDevices, removeSyncDevice, startSyncServer, stopSyncServer } from "@/services/sync-service";
import { checkForAppUpdates, openAppUpdateDownload } from "@/services/updates-service";
import { useSettingsStore } from "@/stores/settings-store";
import { useUIStore } from "@/stores/ui-store";
import { useAppStore } from "@/stores/app-store";
import type { ReaderPreset, ReaderTrackingSettings } from "@/types/library";
import { getDesktopApi } from "@/services/ipc-client";
import type { AppSettingsPatch, SettingsSection, StorageLocations } from "@/types/settings";
import type { DeviceInfo, PairingTokenResult, SyncStatus } from "@/types/sync";
import type { AppUpdateInfo } from "@/types/updates";

function Section({
  title,
  section,
  children,
  onReset
}: {
  title: string;
  section: SettingsSection;
  children: ReactNode;
  onReset: (section: SettingsSection) => void;
}) {
  return (
    <AnimatedPanel className="rounded-xl border border-paper-line bg-paper-panel p-4 shadow-lift">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-paper-ink">{title}</h2>
        <Button variant="quiet" onClick={() => onReset(section)}>
          <RotateCcw size={15} />
          重置
        </Button>
      </div>
      <div className="grid grid-cols-2 gap-4">{children}</div>
    </AnimatedPanel>
  );
}

export function SettingsPage() {
  const [activeCategory, setActiveCategory] = useState<SettingsSection>("appearance");
  const settings = useSettingsStore((state) => state.settings);
  const settingsLoading = useSettingsStore((state) => state.loading);
  const settingsError = useSettingsStore((state) => state.error);
  const [maintenanceMessage, setMaintenanceMessage] = useState("");
  const [storageMessage, setStorageMessage] = useState("");
  const [readerMessage, setReaderMessage] = useState("");
  const [readerPresetName, setReaderPresetName] = useState("");
  const [installedFonts, setInstalledFonts] = useState<string[]>([]);
  const [storageLocations, setStorageLocations] = useState<StorageLocations>();
  const [apiKey, setApiKey] = useState("");
  const [aiMessage, setAiMessage] = useState("");
  const [syncStatus, setSyncStatus] = useState<SyncStatus>();
  const [syncDevices, setSyncDevices] = useState<DeviceInfo[]>([]);
  const [pairing, setPairing] = useState<PairingTokenResult>();
  const [pairingQrDataUrl, setPairingQrDataUrl] = useState("");
  const [syncMessage, setSyncMessage] = useState("");
  const [updateInfo, setUpdateInfo] = useState<AppUpdateInfo>();
  const [updateBusy, setUpdateBusy] = useState(false);
  const [backupBusy, setBackupBusy] = useState(false);
  const setError = useAppStore((state) => state.setError);
  const confirmAction = useUIStore((state) => state.confirmAction);
  const showToast = useUIStore((state) => state.showToast);
  const { applySettings, loadSettings, patchSettings, resetSection } = useSettingsActions();
  const operation = useOperation();

  useEffect(() => {
    void loadSettings();
  }, [loadSettings]);

  useEffect(() => {
    void getDesktopApi().reader.getInstalledFonts().then(setInstalledFonts).catch(() => {});
  }, []);

  useEffect(() => {
    void getStorageLocations().then(setStorageLocations).catch((error) => setError(error instanceof Error ? error.message : String(error)));
  }, [settings?.storage.dataDirectory, settings?.storage.libraryDirectory, setError]);

  const refreshSync = useCallback(async () => {
    const [status, devices] = await Promise.all([getSyncStatus(), listSyncDevices()]);
    setSyncStatus(status);
    setSyncDevices(devices);
    setPairing(status.pairingToken);
  }, []);

  useEffect(() => {
    void refreshSync().catch((error) => setError(error instanceof Error ? error.message : String(error)));
  }, [refreshSync, setError]);

  const patchNumericSetting = (rawValue: string, min: number, max: number, buildPatch: (value: number) => AppSettingsPatch) => {
    if (rawValue.trim() === "") return;
    const value = Number(rawValue);
    if (!Number.isFinite(value)) return;
    void patchSettings(buildPatch(Math.min(max, Math.max(min, value))));
  };

  useEffect(() => {
    if (!pairing?.qrPayload) {
      setPairingQrDataUrl("");
      return;
    }
    void QRCode.toDataURL(pairing.qrPayload, {
      errorCorrectionLevel: "M",
      margin: 1,
      width: 220
    })
      .then(setPairingQrDataUrl)
      .catch(() => setPairingQrDataUrl(""));
  }, [pairing?.qrPayload]);

  if (!settings) {
    return (
      <div className="desktop-panel-card desktop-empty-wrap h-full">
        <div className="grid h-full place-items-center text-sm text-paper-muted">
          {settingsError ? (
            <div className="max-w-md rounded-2xl border border-red-200 bg-red-50/80 p-5 text-center text-red-700">
              <div className="text-base font-semibold">设置读取失败</div>
              <p className="mt-2 text-sm leading-6">{settingsError}</p>
              <Button className="mt-4" disabled={settingsLoading} onClick={() => void loadSettings()}>
                {settingsLoading ? "正在重试..." : "重试读取设置"}
              </Button>
            </div>
          ) : (
            <div>{settingsLoading ? "正在读取设置..." : "设置尚未加载。"}</div>
          )}
        </div>
      </div>
    );
  }

  const patchReaderTracking = (trackingPatch: Partial<ReaderTrackingSettings>) => {
    const patch: AppSettingsPatch = {
      reader: {
        tracking: {
          ...settings.reader.tracking,
          ...trackingPatch
        }
      }
    };
    void patchSettings(patch);
  };

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

  const refreshAISettings = async () => {
    await updateAISettings({});
    await loadSettings();
  };

  const saveKey = async () => {
    setAiMessage("正在保存 API Key...");
    try {
      const next = await saveAIApiKey({ apiKey });
      setApiKey("");
      await patchSettings({ ai: next });
      setAiMessage("API Key 已加密保存。");
      showToast({ tone: "success", title: "API Key 已保存", body: "密钥已由主进程加密保存，前端不会读取明文。" });
    } catch (error) {
      setAiMessage("");
      setError(error instanceof Error ? error.message : String(error));
    }
  };

  const clearKey = async () => {
    setAiMessage("正在清除 API Key...");
    try {
      const next = await clearAIApiKey();
      await patchSettings({ ai: next });
      setAiMessage("API Key 已清除。");
      showToast({ tone: "success", title: "API Key 已清除" });
    } catch (error) {
      setAiMessage("");
      setError(error instanceof Error ? error.message : String(error));
    }
  };

  const testAI = async () => {
    setAiMessage("正在测试 AI 连接...");
    try {
      const result = await testAIConnection();
      await refreshAISettings();
      setAiMessage(result.message);
      showToast({ tone: "success", title: "AI 连接正常", body: result.message });
    } catch (error) {
      setAiMessage("");
      setError(error instanceof Error ? error.message : String(error));
    }
  };

  const resetReaderDefaults = async () => {
    try {
      const next = await resetReaderSettings();
      applySettings(next);
      setReaderMessage("阅读设置已恢复默认。");
      showToast({ tone: "success", title: "阅读设置已恢复默认", body: "字号、行距、页边距和书籍背景已回到初始值。" });
    } catch (error) {
      setError(error instanceof Error ? error.message : String(error));
    }
  };

  const chooseAndMigrateData = async () => {
    try {
      const target = await chooseDataDirectory();
      if (!target) return;
      const confirmed = await confirmAction({
        title: "迁移数据目录？",
        body: "将复制当前数据到新目录，旧目录不会自动删除。迁移后建议重启应用，再继续测试。",
        confirmLabel: "开始迁移",
        tone: "warning"
      });
      if (!confirmed) return;
      setStorageMessage("正在迁移数据目录...");
      const next = await migrateDataDirectory(target);
      await patchSettings({ storage: next.storage, debug: next.debug });
      setStorageLocations(await getStorageLocations());
      setStorageMessage("数据目录已迁移，建议重启应用后继续测试。旧目录不会自动删除。");
      showToast({ tone: "success", title: "数据目录已迁移", body: "建议重启应用后继续测试；旧目录不会自动删除。" });
    } catch (error) {
      setStorageMessage("");
      setError(error instanceof Error ? error.message : String(error));
    }
  };

  const chooseAndMigrateLibrary = async () => {
    try {
      const target = await chooseLibraryDirectory();
      if (!target) return;
      const confirmed = await confirmAction({
        title: "迁移书籍目录？",
        body: "将复制当前书库文件到新目录，旧目录不会自动删除。确认新目录正常后，你可以手动清理旧目录。",
        confirmLabel: "开始迁移",
        tone: "warning"
      });
      if (!confirmed) return;
      setStorageMessage("正在迁移书籍目录...");
      const next = await migrateLibraryDirectory(target);
      await patchSettings({ storage: next.storage });
      setStorageLocations(await getStorageLocations());
      setStorageMessage("书籍目录已迁移。旧目录不会自动删除。");
      showToast({ tone: "success", title: "书籍目录已迁移", body: "书库索引已更新，旧目录不会自动删除。" });
    } catch (error) {
      setStorageMessage("");
      setError(error instanceof Error ? error.message : String(error));
    }
  };

  const handleBackupCreate = useCallback(async () => {
    try {
      await operation.start({ kind: "backup.create" });
    } catch (error) {
      showToast({ tone: "error", title: "创建备份失败", body: error instanceof Error ? error.message : String(error) });
    }
  }, [operation, showToast]);

  const handleBackupRestore = useCallback(async () => {
    try {
      await operation.start({ kind: "backup.restore" });
    } catch (error) {
      showToast({ tone: "error", title: "恢复数据失败", body: error instanceof Error ? error.message : String(error) });
    }
  }, [operation, showToast]);

  const handleResourceScan = useCallback(async () => {
    try {
      await operation.start({ kind: "resource.scan" });
    } catch (error) {
      showToast({ tone: "error", title: "资源扫描失败", body: error instanceof Error ? error.message : String(error) });
    }
  }, [operation, showToast]);

  const enableSync = async () => {
    setSyncMessage("正在开启手机同步服务...");
    try {
      const status = await startSyncServer();
      setSyncStatus(status);
      setSyncMessage("手机同步服务已开启。手机和电脑需要在同一 Wi‑Fi 下。");
      showToast({ tone: "success", title: "手机同步已开启", body: `端口：${status.port ?? "未知"}` });
    } catch (error) {
      setSyncMessage("");
      setError(error instanceof Error ? error.message : String(error));
    }
  };

  const disableSync = async () => {
    setSyncMessage("正在关闭手机同步服务...");
    try {
      const status = await stopSyncServer();
      setSyncStatus(status);
      setPairing(undefined);
      setSyncMessage("手机同步服务已关闭。");
    } catch (error) {
      setSyncMessage("");
      setError(error instanceof Error ? error.message : String(error));
    }
  };

  const generatePairing = async () => {
    setSyncMessage("正在生成一次性配对码...");
    try {
      const token = await createPairingToken();
      await refreshSync();
      setPairing(token);
      setSyncMessage("配对码已生成，10 分钟内有效。");
    } catch (error) {
      setSyncMessage("");
      setError(error instanceof Error ? error.message : String(error));
    }
  };

  const removeDevice = async (deviceId: string) => {
    try {
      const next = await removeSyncDevice(deviceId);
      setSyncDevices(next);
      showToast({ tone: "success", title: "已断开设备" });
    } catch (error) {
      setError(error instanceof Error ? error.message : String(error));
    }
  };

  const copyText = async (value: string, label: string) => {
    try {
      await navigator.clipboard.writeText(value);
      showToast({ tone: "success", title: `${label}已复制` });
    } catch {
      setError("复制失败，请手动选中文本复制。");
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

  const chooseAndSetBackupDirectory = async () => {
    try {
      const directory = await chooseBackupDirectory();
      if (!directory) return;
      await patchSettings({ storage: { backupDirectory: directory } });
      showToast({ tone: "success", title: "备份目录已设置", body: "自动备份将使用该目录。" });
    } catch (error) {
      setError(error instanceof Error ? error.message : String(error));
    }
  };

  const runAutoBackupNow = async () => {
    setBackupBusy(true);
    try {
      const result = await runAutoBackup();
      await loadSettings();
      showToast({ tone: "success", title: "自动备份完成", body: result.backupRoot });
    } catch (error) {
      await loadSettings();
      setError(error instanceof Error ? error.message : String(error));
    } finally {
      setBackupBusy(false);
    }
  };

  return (
    <div className="desktop-page-scroll paper-shell">
      <div className="desktop-page-stack">
        <div className="settings-layout">
        <nav className="settings-nav" aria-label="设置分类">
          {([
            ["appearance", "外观"],
            ["reader", "阅读器"],
            ["ai", "AI 助手"],
            ["storage", "数据与存储"],
            ["debug", "关于 / 调试"]
          ] as Array<[SettingsSection, string]>).map(([section, label]) => (
            <button
              key={section}
              type="button"
              className={activeCategory === section ? "active" : ""}
              aria-current={activeCategory === section ? "true" : undefined}
              onClick={() => setActiveCategory(section)}
            >
              {label}
            </button>
          ))}
        </nav>
        <div className="settings-forms">
          {activeCategory === "appearance" && (
          <Section title="外观" section="appearance" onReset={resetSection}>
            <label className="grid gap-1.5 text-sm text-paper-muted">
              <span className="font-medium text-paper-ink">应用主题</span>
              <select
                className="paper-input h-9"
                value={settings.appearance.theme}
                onChange={(event) => void patchSettings({ appearance: { theme: event.target.value as typeof settings.appearance.theme } })}
              >
                <option value="system">跟随系统</option>
                <option value="light">浅色</option>
                <option value="dark">深色</option>
              </select>
            </label>
            <Field label="应用字体缩放">
              <TextInput
                type="number"
                min={0.85}
                max={1.4}
                step={0.05}
                value={settings.appearance.appFontScale}
                onChange={(event) => patchNumericSetting(event.target.value, 0.85, 1.4, (appFontScale) => ({ appearance: { appFontScale } }))}
              />
            </Field>
            <label className="flex items-center gap-2 text-sm text-paper-muted">
              <input
                type="checkbox"
                checked={settings.appearance.showRightPanel}
                onChange={(event) => void patchSettings({ appearance: { showRightPanel: event.target.checked } })}
              />
              显示右侧属性栏
            </label>
          </Section>
          )}

          {activeCategory === "reader" && (
          <Section title="阅读器" section="reader" onReset={resetSection}>
            <div className="col-span-2 flex items-center justify-between rounded-xl border border-paper-line bg-paper-soft/35 p-3">
              <div>
                <div className="text-sm font-semibold text-paper-ink">阅读排版</div>
                <p className="mt-1 text-xs leading-5 text-paper-muted">字号、行距、页边距和书籍背景只影响阅读页，不影响应用整体主题。</p>
              </div>
              <Button variant="secondary" onClick={() => void resetReaderDefaults()}>
                <RotateCcw size={15} />
                恢复阅读默认
              </Button>
            </div>
            {readerMessage && (
              <InlineNotice tone="success" className="col-span-2 p-2 text-xs">
                {readerMessage}
              </InlineNotice>
            )}
            {/* 预设选择 */}
            <div className="col-span-2 flex items-center gap-2">
              <select
                value=""
                onChange={(e) => {
                  const preset = settings.reader.presets?.find((p: ReaderPreset) => p.id === e.target.value);
                  if (preset) {
                    void patchSettings({
                      reader: {
                        fontSize: preset.fontSize,
                        lineHeight: preset.lineHeight,
                        pageMargin: preset.pageMargin,
                        paragraphSpacing: preset.paragraphSpacing,
                        letterSpacing: preset.letterSpacing,
                        readerBackground: preset.readerBackground,
                        ...(preset.fontFamily ? { fontFamily: preset.fontFamily } : {}),
                      },
                    });
                  }
                  e.target.value = "";
                }}
                className="paper-input h-9 flex-1"
              >
                <option value="">选择预设...</option>
                {settings.reader.presets?.map((p: ReaderPreset) => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </select>
              <TextInput
                value={readerPresetName}
                onChange={(event) => setReaderPresetName(event.target.value)}
                placeholder="预设名称"
                className="w-32"
                aria-label="阅读预设名称"
              />
              <Button
                variant="secondary"
                onClick={() => {
                  const name = readerPresetName.trim();
                  if (!name) return;
                  const preset: ReaderPreset = {
                    id: `preset-${Date.now()}`,
                    name,
                    fontSize: settings.reader.fontSize,
                    lineHeight: settings.reader.lineHeight,
                    pageMargin: settings.reader.pageMargin,
                    paragraphSpacing: settings.reader.paragraphSpacing ?? 1.0,
                    letterSpacing: settings.reader.letterSpacing ?? 0,
                    readerBackground: settings.reader.readerBackground,
                    fontFamily: settings.reader.fontFamily,
                  };
                  void getDesktopApi().reader.savePreset(preset).then(() => {
                    setReaderPresetName("");
                    return loadSettings();
                  });
                }}
              >
                保存
              </Button>
            </div>
            <Field label="字号">
              <div className="flex items-center gap-3">
                <input
                  type="range"
                  min={12}
                  max={32}
                  step={1}
                  value={settings.reader.fontSize}
                  onChange={(e) => void patchSettings({ reader: { fontSize: Number(e.target.value) } })}
                  className="flex-1 accent-amber-700"
                />
                <span className="text-sm text-stone-600 w-8 text-center">{settings.reader.fontSize}</span>
              </div>
            </Field>
            {/* 字体选择 */}
            <div className="col-span-2 flex items-center gap-2">
              <label className="text-sm text-paper-muted shrink-0">字体</label>
              <select
                value={settings.reader.fontFamily || ""}
                onChange={(e) => void patchSettings({ reader: { fontFamily: e.target.value || undefined } })}
                className="paper-input h-9 flex-1"
              >
                <option value="">系统默认</option>
                {installedFonts.map((f) => {
                  const name = f.replace(/\.[^.]+$/, "");
                  return <option key={f} value={name}>{name}</option>;
                })}
              </select>
              <Button
                variant="secondary"
                onClick={async () => {
                  const result = await getDesktopApi().reader.chooseFont();
                  if (result) {
                    const fontName = result.fileName.replace(/\.[^.]+$/, "");
                    const styleId = `font-${fontName}`;
                    let styleEl = document.getElementById(styleId) as HTMLStyleElement;
                    if (!styleEl) {
                      styleEl = document.createElement("style");
                      styleEl.id = styleId;
                      document.head.appendChild(styleEl);
                    }
                    styleEl.textContent = `@font-face { font-family: '${fontName}'; src: url('file://${result.filePath}'); }`;
                    void patchSettings({ reader: { fontFamily: fontName } });
                    void getDesktopApi().reader.getInstalledFonts().then(setInstalledFonts).catch(() => {});
                  }
                }}
              >
                导入
              </Button>
            </div>
            <Field label="行距">
              <div className="flex items-center gap-3">
                <input
                  type="range"
                  min={1.2}
                  max={2.5}
                  step={0.1}
                  value={settings.reader.lineHeight}
                  onChange={(e) => void patchSettings({ reader: { lineHeight: Number(e.target.value) } })}
                  className="flex-1 accent-amber-700"
                />
                <span className="text-sm text-stone-600 w-10 text-center">{settings.reader.lineHeight.toFixed(1)}</span>
              </div>
            </Field>
            <Field label="段间距">
              <div className="flex items-center gap-3">
                <input
                  type="range"
                  min={0.5}
                  max={3.0}
                  step={0.1}
                  value={settings.reader.paragraphSpacing ?? 1.0}
                  onChange={(e) => void patchSettings({ reader: { paragraphSpacing: Number(e.target.value) } })}
                  className="flex-1 accent-amber-700"
                />
                <span className="text-sm text-stone-600 w-10 text-center">{(settings.reader.paragraphSpacing ?? 1.0).toFixed(1)}</span>
              </div>
            </Field>
            <Field label="字间距">
              <div className="flex items-center gap-3">
                <input
                  type="range"
                  min={0}
                  max={0.5}
                  step={0.01}
                  value={settings.reader.letterSpacing ?? 0}
                  onChange={(e) => void patchSettings({ reader: { letterSpacing: Number(e.target.value) } })}
                  className="flex-1 accent-amber-700"
                />
                <span className="text-sm text-stone-600 w-10 text-center">{(settings.reader.letterSpacing ?? 0).toFixed(2)}</span>
              </div>
            </Field>
            <Field label="页边距">
              <TextInput
                type="number"
                min={24}
                max={120}
                value={settings.reader.pageMargin}
                onChange={(event) => patchNumericSetting(event.target.value, 24, 120, (pageMargin) => ({ reader: { pageMargin } }))}
              />
            </Field>
            <label className="grid gap-1.5 text-sm text-paper-muted">
              <span className="font-medium text-paper-ink">书籍背景</span>
              <select
                className="paper-input h-9"
                value={settings.reader.readerBackground}
                onChange={(event) => void patchSettings({ reader: { readerBackground: event.target.value as typeof settings.reader.readerBackground } })}
              >
                <option value="white">白纸</option>
                <option value="warm">暖纸</option>
                <option value="green">护眼</option>
                <option value="night">夜间</option>
                <option value="amber">琥珀</option>
                <option value="parchment">羊皮纸</option>
                <option value="beans">绿豆沙</option>
              </select>
            </label>
            <label className="grid gap-1.5 text-sm text-paper-muted">
              <span className="font-medium text-paper-ink">EPUB 样式</span>
              <select
                className="paper-input h-9"
                value={settings.reader.epubStyleMode}
                onChange={(event) => void patchSettings({ reader: { epubStyleMode: event.target.value as typeof settings.reader.epubStyleMode } })}
              >
                <option value="publisher">保留原书样式</option>
                <option value="unified">统一阅读样式</option>
              </select>
            </label>
            <label className="grid gap-1.5 text-sm text-paper-muted">
              <span className="font-medium text-paper-ink">繁简转换</span>
              <select
                className="paper-input h-9"
                value={settings.reader.textConversion || "none"}
                onChange={(event) => void patchSettings({ reader: { textConversion: event.target.value as typeof settings.reader.textConversion } })}
              >
                <option value="none">不转换</option>
                <option value="s2t">简体 → 繁体</option>
                <option value="t2s">繁体 → 简体</option>
              </select>
            </label>
            <div className="col-span-2 rounded-xl border border-paper-line bg-paper-soft/35 p-3">
              <div className="text-sm font-semibold text-paper-ink">高级阅读记录</div>
              <p className="mt-1 text-xs leading-5 text-paper-muted">
                下面这些秒数只控制阅读计时和位置保存：多久没有翻页会暂停计时、多久保存一次阅读位置、多久把会话时长写入本地文件。
              </p>
            </div>
            <label className="flex items-center gap-2 text-sm text-paper-muted">
              <input
                type="checkbox"
                checked={settings.reader.restoreLastPosition}
                onChange={(event) => void patchSettings({ reader: { restoreLastPosition: event.target.checked } })}
              />
              自动恢复上次位置
            </label>
            <label className="flex items-center gap-2 text-sm text-paper-muted">
              <input
                type="checkbox"
                checked={settings.reader.tracking.trackReadingSessions}
                onChange={(event) => patchReaderTracking({ trackReadingSessions: event.target.checked })}
              />
              自动记录阅读会话
            </label>
            <label className="flex items-center gap-2 text-sm text-paper-muted">
              <input
                type="checkbox"
                checked={settings.reader.tracking.showReadingStatsCards}
                onChange={(event) => patchReaderTracking({ showReadingStatsCards: event.target.checked })}
              />
              显示阅读统计卡片
            </label>
            <Field label="空闲超时秒数">
              <p className="text-xs leading-5 text-paper-muted">多久没有翻页、滚动或点击后，暂停计入有效阅读时间。</p>
              <TextInput
                type="number"
                min={15}
                max={600}
                value={Math.round(settings.reader.tracking.idleTimeoutMs / 1000)}
                onChange={(event) => patchNumericSetting(event.target.value, 15, 600, (value) => ({ reader: { tracking: { ...settings.reader.tracking, idleTimeoutMs: value * 1000 } } }))}
              />
            </Field>
            <Field label="进度保存间隔秒数">
              <p className="text-xs leading-5 text-paper-muted">阅读时多久自动保存一次当前位置。</p>
              <TextInput
                type="number"
                min={1}
                max={60}
                value={Math.round(settings.reader.tracking.progressSaveIntervalMs / 1000)}
                onChange={(event) =>
                  patchNumericSetting(event.target.value, 1, 60, (value) => ({ reader: { tracking: { ...settings.reader.tracking, progressSaveIntervalMs: value * 1000 } } }))
                }
              />
            </Field>
            <Field label="会话心跳秒数">
              <p className="text-xs leading-5 text-paper-muted">阅读页多久同步一次“还在读”的状态。</p>
              <TextInput
                type="number"
                min={2}
                max={60}
                value={Math.round(settings.reader.tracking.sessionHeartbeatMs / 1000)}
                onChange={(event) =>
                  patchNumericSetting(event.target.value, 2, 60, (value) => ({ reader: { tracking: { ...settings.reader.tracking, sessionHeartbeatMs: value * 1000 } } }))
                }
              />
            </Field>
            <Field label="会话持久化间隔秒数">
              <p className="text-xs leading-5 text-paper-muted">阅读时长多久写入一次本地文件，异常退出时用于恢复统计。</p>
              <TextInput
                type="number"
                min={5}
                max={300}
                value={Math.round(settings.reader.tracking.sessionPersistIntervalMs / 1000)}
                onChange={(event) =>
                  patchNumericSetting(event.target.value, 5, 300, (value) => ({ reader: { tracking: { ...settings.reader.tracking, sessionPersistIntervalMs: value * 1000 } } }))
                }
              />
            </Field>
          </Section>
          )}

          {activeCategory === "ai" && (
          <Section title="AI 助手" section="ai" onReset={resetSection}>
            <label className="col-span-2 flex items-center gap-2 text-sm text-paper-muted">
              <input
                type="checkbox"
                checked={settings.ai.enabled}
                onChange={(event) => void patchSettings({ ai: { enabled: event.target.checked } })}
              />
              启用 AI 助手（关闭时不会向任何服务发送内容，也不会出现 AI 操作）
            </label>
            {!settings.ai.enabled && (
              <div className="col-span-2 rounded-md border border-paper-line bg-paper-soft/45 p-3 text-xs leading-6 text-paper-muted">
                AI 助手已关闭。开启后可用「润色 / 扩写 / 平台风格化」等本地灵感打磨，但开启前请先配置 API Key。
              </div>
            )}
            {settings.ai.enabled && !settings.ai.hasApiKey && (
              <InlineNotice tone="warning" className="col-span-2 p-2 text-xs">
                已启用 AI 助手，但尚未配置 API Key。请先在下方保存 API Key 才能使用 AI 打磨；在此之前不会产生任何网络请求。
              </InlineNotice>
            )}
            {settings.ai.enabled && (
              <div className="col-span-2 rounded-md border border-amber-200 bg-amber-50/60 p-3 text-xs leading-6 text-paper-muted">
                开启 AI 后，你输入的正文、灵感标题与平台标签会发送到你配置的 AI 服务（Base URL）。API Key 仅在主进程加密保存，前端不读取明文。
              </div>
            )}
            <label className="grid gap-1.5 text-sm text-paper-muted">
              <span className="font-medium text-paper-ink">Provider</span>
              <select
                className="paper-input h-9"
                value={settings.ai.provider}
                onChange={(event) => void patchSettings({ ai: { provider: event.target.value as typeof settings.ai.provider } })}
              >
                <option value="openai-compatible">OpenAI-compatible</option>
              </select>
            </label>
            <Field label="Base URL">
              <TextInput
                value={settings.ai.baseUrl}
                onChange={(event) => void patchSettings({ ai: { baseUrl: event.target.value } })}
                placeholder="https://api.openai.com/v1"
              />
            </Field>
            <Field label="模型名">
              <TextInput value={settings.ai.model} onChange={(event) => void patchSettings({ ai: { model: event.target.value } })} placeholder="gpt-4.1-mini" />
            </Field>
            <Field label="Temperature">
              <TextInput
                type="number"
                min={0}
                max={1.5}
                step={0.1}
                value={settings.ai.temperature}
                onChange={(event) => patchNumericSetting(event.target.value, 0, 1.5, (temperature) => ({ ai: { temperature } }))}
              />
            </Field>
            <Field label={settings.ai.hasApiKey ? "API Key（已保存，可留空）" : "API Key"}>
              <TextInput
                type="password"
                value={apiKey}
                onChange={(event) => setApiKey(event.target.value)}
                placeholder={settings.ai.hasApiKey ? "输入新 Key 可替换现有配置" : "sk-..."}
              />
            </Field>
            <div className="grid gap-2">
              <div className="text-xs text-paper-muted">{settings.ai.hasApiKey ? "状态：API Key 已加密保存" : "状态：尚未配置 API Key"}</div>
              <div className="flex flex-wrap gap-2">
                <Button variant="secondary" disabled={!apiKey.trim()} onClick={() => void saveKey()}>
                  保存 API Key
                </Button>
                <Button variant="secondary" disabled={!settings.ai.hasApiKey} onClick={() => void clearKey()}>
                  清除 Key
                </Button>
                <Button disabled={!settings.ai.hasApiKey} onClick={() => void testAI()}>
                  测试连接
                </Button>
              </div>
              {aiMessage && (
                <InlineNotice tone="info" className="p-2 text-xs">
                  {aiMessage}
                </InlineNotice>
              )}
            </div>
          </Section>
          )}

          {activeCategory === "storage" && (
          <Section title="数据与存储" section="storage" onReset={resetSection}>
            <Field label="数据目录">
              <TextInput value={settings.storage.dataDirectory} readOnly />
            </Field>
            <Field label="书库目录">
              <TextInput value={settings.storage.libraryDirectory} readOnly />
            </Field>
            <div className="col-span-2 rounded-md border border-paper-line bg-paper-soft/45 p-3 text-xs leading-6 text-paper-muted">
              <div className="mb-1 text-sm font-semibold text-paper-ink">存储位置</div>
              <div>当前模式：{settings.storage.storageMode === "portable" ? "便携目录" : settings.storage.storageMode === "custom" ? "自定义目录" : "系统回退目录"}</div>
              <div>安装目录旁数据目录：{storageLocations?.portableDataDirectory ?? "读取中..."}</div>
              <div>旧目录不会自动删除；迁移采用复制方式，确认新目录正常后你可以手动清理旧目录。</div>
              <div className="mt-3 flex flex-wrap gap-2">
                <Button variant="secondary" onClick={() => void chooseAndMigrateData()}>
                  <FolderOpen size={15} />
                  选择数据目录
                </Button>
                <Button variant="secondary" onClick={() => void chooseAndMigrateLibrary()}>
                  <FolderOpen size={15} />
                  选择书籍目录
                </Button>
              </div>
              {storageMessage && (
                <InlineNotice tone="info" className="mt-3 break-all p-2 text-xs">
                  {storageMessage}
                </InlineNotice>
              )}
            </div>
            <div className="col-span-2 rounded-md border border-paper-line bg-paper-soft/45 p-3">
              <div className="mb-3 text-sm font-semibold text-paper-ink">数据保护</div>
              <div className="flex flex-wrap gap-2">
                <Button variant="secondary" onClick={() => void openDataDirectory()}>
                  打开数据目录
                </Button>
                <Button
                  variant="secondary"
                  disabled={operation.isActive}
                  onClick={() => void handleBackupCreate()}
                >
                  备份数据
                </Button>
                <Button
                  variant="secondary"
                  disabled={operation.isActive}
                  onClick={() => void handleBackupRestore()}
                >
                  恢复数据
                </Button>
                <Button
                  variant="secondary"
                  disabled={operation.isActive}
                  onClick={() => void handleResourceScan()}
                >
                  扫描资源完整性
                </Button>
              </div>
              <div className="mt-3 rounded-md border border-paper-line bg-paper-panel/60 p-3">
                <div className="mb-2 text-xs font-semibold text-paper-ink">自动备份</div>
                <div className="grid gap-1 text-xs leading-5 text-paper-muted">
                  <div>备份目录：{settings.storage.backupDirectory ?? "未配置"}</div>
                  <div>
                    最近成功：
                    {settings.storage.lastAutoBackupAt ? new Date(settings.storage.lastAutoBackupAt).toLocaleString() : "暂无"}
                  </div>
                  <div>
                    最近失败：
                    {settings.storage.lastAutoBackupFailedAt ? new Date(settings.storage.lastAutoBackupFailedAt).toLocaleString() : "暂无"}
                  </div>
                </div>
                {settings.storage.lastAutoBackupError && (
                  <InlineNotice tone="error" className="mt-2 break-all p-2 text-xs">
                    {settings.storage.lastAutoBackupError}
                  </InlineNotice>
                )}
                <label className="mt-2 flex items-center gap-2 text-xs text-paper-muted">
                  <input
                    type="checkbox"
                    checked={settings.storage.autoBackupEnabled === true}
                    disabled={!settings.storage.backupDirectory}
                    onChange={(event) => {
                      if (!settings.storage.backupDirectory) {
                        setError("请先选择自动备份目录。");
                        return;
                      }
                      void patchSettings({ storage: { autoBackupEnabled: event.target.checked } });
                    }}
                  />
                  启用每日 / 升级前自动备份（间隔至少 24 小时；升级下载前会立即备份）
                </label>
                <div className="mt-2 flex flex-wrap gap-2">
                  <Button variant="secondary" onClick={() => void chooseAndSetBackupDirectory()}>
                    <FolderOpen size={15} />
                    选择备份目录
                  </Button>
                  <Button
                    variant="secondary"
                    disabled={!settings.storage.backupDirectory || !settings.storage.autoBackupEnabled || backupBusy}
                    onClick={() => void runAutoBackupNow()}
                  >
                    {backupBusy ? "备份中..." : "立即备份"}
                  </Button>
                </div>
              </div>
            </div>
          </Section>
          )}

          <AnimatedPanel className="settings-wide rounded-xl border border-paper-line bg-paper-panel p-4 shadow-lift">
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Smartphone size={17} className="text-copper" />
                <h2 className="text-sm font-semibold text-paper-ink">手机同步</h2>
              </div>
              <span className="inline-flex items-center gap-1 rounded-full border border-paper-line bg-paper-soft/50 px-2.5 py-1 text-xs text-paper-muted">
                {syncStatus?.running ? <Wifi size={13} /> : <WifiOff size={13} />}
                {syncStatus?.running ? "服务运行中" : "未开启"}
              </span>
            </div>

            <div className="grid gap-4 md:grid-cols-[1.1fr_0.9fr]">
              <div className="rounded-xl border border-paper-line bg-paper-soft/35 p-3">
                <div className="text-sm font-semibold text-paper-ink">局域网直连</div>
                <p className="mt-1 text-xs leading-5 text-paper-muted">
                  电脑端作为同步主机，手机端扫码或输入配对地址后，同一 Wi‑Fi 下同步灵感、书库、阅读进度和阅读时长。AI Key 不会同步到手机。
                </p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <Button variant="secondary" disabled={syncStatus?.running} onClick={() => void enableSync()}>
                    开启同步服务
                  </Button>
                  <Button variant="secondary" disabled={!syncStatus?.running} onClick={() => void disableSync()}>
                    关闭服务
                  </Button>
                  <Button disabled={!syncStatus?.running} onClick={() => void generatePairing()}>
                    <QrCode size={15} />
                    生成二维码
                  </Button>
                </div>
                <div className="mt-3 grid gap-1 text-xs leading-5 text-paper-muted">
                  <div>电脑设备 ID：{syncStatus?.device.deviceId ?? "读取中..."}</div>
                  <div>监听端口：{syncStatus?.port ?? "未开启"}</div>
                  <div>可用地址：{syncStatus?.addresses.join(" / ") || "未读取到局域网地址"}</div>
                </div>
                {syncMessage && (
                  <InlineNotice tone="info" className="mt-3 p-2 text-xs">
                    {syncMessage}
                  </InlineNotice>
                )}
              </div>

              <div className="rounded-xl border border-paper-line bg-paper-soft/35 p-3">
                <div className="text-sm font-semibold text-paper-ink">配对信息</div>
                {pairing ? (
                  <div className="mt-2 grid gap-2">
                    {pairingQrDataUrl ? (
                      <div className="grid place-items-center rounded-xl border border-paper-line bg-white p-3">
                        <img src={pairingQrDataUrl} alt="手机同步配对二维码" className="h-48 w-48" />
                      </div>
                    ) : (
                      <div className="rounded-lg border border-dashed border-copper/40 bg-paper-panel p-2 text-xs text-paper-muted">
                        二维码生成中；如果一直没有显示，请复制下面的配对 URL。
                      </div>
                    )}
                    <div className="rounded-lg border border-paper-line bg-paper-panel p-2 text-xs leading-5 text-paper-muted">
                      <div className="flex items-center justify-between gap-2">
                        <div className="font-semibold text-paper-ink">优先配对 URL</div>
                        <Button variant="quiet" onClick={() => void copyText(pairing.pairingUrl, "配对 URL")}>
                          复制
                        </Button>
                      </div>
                      <div className="mt-1 break-all">{pairing.pairingUrl}</div>
                    </div>
                    {(pairing.pairingUrls ?? []).length > 1 && (
                      <div className="rounded-lg border border-paper-line bg-paper-panel p-2 text-xs leading-5 text-paper-muted">
                        <div className="font-semibold text-paper-ink">连接失败时试这些地址</div>
                        <div className="mt-2 grid gap-1">
                          {pairing.pairingUrls.map((url) => (
                            <button
                              key={url}
                              type="button"
                              className="rounded-md bg-paper-soft/60 px-2 py-1 text-left text-xs text-paper-muted transition hover:text-paper-ink"
                              onClick={() => void copyText(url, "备用配对 URL")}
                            >
                              {url}
                            </button>
                          ))}
                        </div>
                      </div>
                    )}
                    <div className="rounded-lg border border-dashed border-copper/40 bg-paper-panel p-2 text-xs leading-5 text-paper-muted">
                      <div className="flex items-center justify-between gap-2">
                        <div className="font-semibold text-paper-ink">二维码载荷</div>
                        <Button variant="quiet" onClick={() => void copyText(pairing.qrPayload, "二维码载荷")}>
                          复制
                        </Button>
                      </div>
                      <div className="mt-1 max-h-24 overflow-auto break-all">{pairing.qrPayload}</div>
                    </div>
                    <div className="text-xs text-paper-muted">过期时间：{new Date(pairing.expiresAt).toLocaleString()}</div>
                  </div>
                ) : (
                  <p className="mt-2 text-xs leading-5 text-paper-muted">开启同步服务后生成一次性配对码。后续手机版会直接扫码读取这里的载荷。</p>
                )}
              </div>
            </div>

            <div className="mt-4 rounded-xl border border-paper-line bg-paper-soft/35 p-3">
              <div className="mb-2 text-sm font-semibold text-paper-ink">已配对设备</div>
              {syncDevices.length === 0 ? (
                <p className="text-xs text-paper-muted">还没有手机设备完成配对。</p>
              ) : (
                <div className="grid gap-2">
                  {syncDevices.map((device) => (
                    <div key={device.deviceId} className="flex items-center justify-between gap-3 rounded-lg border border-paper-line bg-paper-panel px-3 py-2">
                      <div className="min-w-0">
                        <div className="truncate text-sm font-medium text-paper-ink">{device.name}</div>
                        <div className="truncate text-xs text-paper-muted">
                          {device.platform} · {device.deviceId} · 最近：{new Date(device.lastSeenAt).toLocaleString()}
                        </div>
                      </div>
                      <Button variant="quiet" onClick={() => void removeDevice(device.deviceId)}>
                        断开
                      </Button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </AnimatedPanel>

          <div className="settings-wide">
          <AnimatedPanel className="mb-4 rounded-xl border border-paper-line bg-paper-panel p-4 shadow-lift">
            <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
              <div>
                <div className="desktop-card-label">Update</div>
                <h2 className="text-sm font-semibold text-paper-ink">应用更新</h2>
                <p className="mt-1 max-w-2xl text-xs leading-5 text-paper-muted">
                  检查 GitHub Release 上的最新桌面端和 Android 端版本。桌面端会打开新版安装包或 Release 页面；安装仍由 Windows 安装器确认完成。
                </p>
                {updateInfo && (
                  <InlineNotice tone={updateInfo.hasUpdate ? "success" : "info"} className="mt-3 p-2 text-xs">
                    {updateInfo.hasUpdate
                      ? `发现 v${updateInfo.latestVersion}，当前版本 ${updateInfo.currentVersion}。${updateInfo.desktopAssetName ? `安装包：${updateInfo.desktopAssetName}` : "未找到独立安装包，将打开 Release 页面。"}`
                      : `当前版本 ${updateInfo.currentVersion} 已是最新。`}
                  </InlineNotice>
                )}
              </div>
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
          </AnimatedPanel>

          {activeCategory === "debug" && (
          <Section title="关于 / 调试" section="debug" onReset={resetSection}>
            <Field label="应用版本">
              <TextInput value={settings.debug.appVersion} readOnly />
            </Field>
            <Field label="数据目录">
              <TextInput value={settings.debug.dataRoot} readOnly />
            </Field>
            <label className="flex items-center gap-2 text-sm text-paper-muted">
              <input
                type="checkbox"
                checked={settings.debug.showStatsCards}
                onChange={(event) => void patchSettings({ debug: { showStatsCards: event.target.checked } })}
              />
              显示调试卡片
            </label>
            <div className="col-span-2 rounded-md border border-paper-line bg-paper-soft/45 p-3">
              <div className="mb-3 text-sm font-semibold text-paper-ink">诊断工具</div>
              <div className="flex flex-wrap gap-2">
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
            </div>
          </Section>
          )}
          {operation.state && (
            <OperationProgressDialog
              state={operation.state}
              isCommitting={operation.isCommitting}
              onCancel={operation.cancel}
              onClose={operation.reset}
              onReset={operation.retry}
            />
          )}
          </div>
          </div>
        </div>
      </div>
    </div>
  );
}
