import { useCallback, useEffect, useState } from "react";
import { FolderOpen, QrCode, Smartphone, Wifi, WifiOff } from "lucide-react";
import QRCode from "qrcode";
import { AnimatedPanel, InlineNotice } from "@/components/interaction";
import { Button, Field, TextInput } from "@/components/ui";
import { openDataDirectory, runAutoBackup } from "@/services/maintenance-service";
import {
  chooseBackupDirectory,
  chooseDataDirectory,
  chooseLibraryDirectory,
  getStorageLocations,
  migrateDataDirectory,
  migrateLibraryDirectory
} from "@/services/settings-service";
import { createPairingToken, getSyncStatus, listSyncDevices, removeSyncDevice, startSyncServer, stopSyncServer } from "@/services/sync-service";
import type { AppSettings, AppSettingsPatch, SettingsSection, StorageLocations } from "@/types/settings";
import type { DeviceInfo, PairingTokenResult, SyncStatus } from "@/types/sync";
import type { UseOperationResult } from "@/hooks/useOperation";
import { Section } from "./SectionWrapper";

interface StorageSectionProps {
  settings: AppSettings;
  patchSettings: (patch: AppSettingsPatch) => Promise<unknown>;
  loadSettings: () => Promise<AppSettings | undefined>;
  resetSection: (section: SettingsSection) => void;
  operation: UseOperationResult;
  confirmAction: (options: { title: string; body: string; confirmLabel?: string; cancelLabel?: string; tone?: "default" | "warning" | "danger" }) => Promise<boolean>;
  showToast: (toast: { tone: "success" | "info" | "warning" | "error"; title: string; body?: string }) => void;
  setError: (error: string) => void;
}

export function StorageSection({
  settings,
  patchSettings,
  loadSettings,
  resetSection,
  operation,
  confirmAction,
  showToast,
  setError
}: StorageSectionProps) {
  const [storageMessage, setStorageMessage] = useState("");
  const [storageLocations, setStorageLocations] = useState<StorageLocations>();
  const [backupBusy, setBackupBusy] = useState(false);

  // 局域网同步状态
  const [syncStatus, setSyncStatus] = useState<SyncStatus>();
  const [syncDevices, setSyncDevices] = useState<DeviceInfo[]>([]);
  const [pairing, setPairing] = useState<PairingTokenResult>();
  const [pairingQrDataUrl, setPairingQrDataUrl] = useState("");
  const [syncMessage, setSyncMessage] = useState("");

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

  return (
    <>
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
            <Button variant="secondary" disabled={operation.isActive} onClick={() => void handleBackupCreate()}>
              备份数据
            </Button>
            <Button variant="secondary" disabled={operation.isActive} onClick={() => void handleBackupRestore()}>
              恢复数据
            </Button>
            <Button variant="secondary" disabled={operation.isActive} onClick={() => void handleResourceScan()}>
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

      {/* 手机同步面板 */}
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
    </>
  );
}
