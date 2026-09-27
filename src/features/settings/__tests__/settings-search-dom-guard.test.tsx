// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render } from "@testing-library/react";
import type { AppSettings } from "@/types/settings";
import { SETTINGS_SEARCH_REGISTRY } from "../search-registry";
import { SettingsPage } from "../SettingsPage";
import { useSettingsStore } from "@/stores/settings-store";

vi.mock("@/services/settings-service", () => ({
  getSettings: vi.fn().mockResolvedValue(undefined),
  updateSettings: vi.fn().mockResolvedValue(undefined),
  resetSettingsSection: vi.fn().mockResolvedValue(undefined),
  resetReaderSettings: vi.fn().mockResolvedValue(undefined),
  chooseDataDirectory: vi.fn().mockResolvedValue(null),
  chooseLibraryDirectory: vi.fn().mockResolvedValue(null),
  chooseBackupDirectory: vi.fn().mockResolvedValue(null),
  migrateDataDirectory: vi.fn().mockResolvedValue(undefined),
  migrateLibraryDirectory: vi.fn().mockResolvedValue(undefined),
  getStorageLocations: vi.fn().mockResolvedValue({
    dataDirectory: "D:/data",
    libraryDirectory: "D:/library",
    portableDataDirectory: "D:/portable",
    fallbackDataDirectory: "D:/fallback",
    storageMode: "portable",
    dataDirectoryWritable: true,
    libraryDirectoryWritable: true
  })
}));

vi.mock("@/services/reader-service", () => ({
  getInstalledFonts: vi.fn().mockResolvedValue(["思源宋体"]),
  chooseFont: vi.fn().mockResolvedValue(null),
  savePreset: vi.fn().mockResolvedValue(undefined)
}));

vi.mock("@/services/sync-service", () => {
  const device = {
    deviceId: "desktop-test",
    name: "测试电脑",
    platform: "windows",
    pairedAt: "2026-09-01T00:00:00.000Z",
    lastSeenAt: "2026-09-27T00:00:00.000Z"
  };
  const status = { running: false, addresses: [], device, pairingToken: undefined };
  return {
    getSyncStatus: vi.fn().mockResolvedValue(status),
    startSyncServer: vi.fn().mockResolvedValue(status),
    stopSyncServer: vi.fn().mockResolvedValue(status),
    createPairingToken: vi.fn().mockResolvedValue(undefined),
    listSyncDevices: vi.fn().mockResolvedValue([device]),
    removeSyncDevice: vi.fn().mockResolvedValue([])
  };
});

vi.mock("@/services/maintenance-service", () => ({
  getBuildInfo: vi.fn().mockResolvedValue({ version: "0.2.1" }),
  openDataDirectory: vi.fn().mockResolvedValue(undefined),
  openLogDirectory: vi.fn().mockResolvedValue(undefined),
  createBackup: vi.fn().mockResolvedValue(null),
  runAutoBackup: vi.fn().mockResolvedValue(undefined),
  restoreBackup: vi.fn().mockResolvedValue(null),
  exportDebugInfo: vi.fn().mockResolvedValue(null),
  writeRendererLog: vi.fn().mockResolvedValue(undefined),
  getStartupRecovery: vi.fn().mockResolvedValue({ seen: true }),
  markStartupRecoverySeen: vi.fn().mockResolvedValue(undefined)
}));

vi.mock("@/services/updates-service", () => ({
  checkForAppUpdates: vi.fn().mockResolvedValue(undefined),
  openAppUpdateDownload: vi.fn().mockResolvedValue(undefined)
}));

vi.mock("@/services/ai-service", () => ({
  getAISettings: vi.fn().mockResolvedValue(undefined),
  updateAISettings: vi.fn().mockResolvedValue(undefined),
  saveAIApiKey: vi.fn().mockResolvedValue(undefined),
  clearAIApiKey: vi.fn().mockResolvedValue(undefined),
  testAIConnection: vi.fn().mockResolvedValue({ ok: false, message: "" }),
  runAIAction: vi.fn().mockResolvedValue(undefined)
}));

vi.mock("@/hooks/useOperation", () => ({
  useOperation: () => ({
    state: null,
    isCommitting: false,
    cancel: vi.fn(),
    reset: vi.fn(),
    retry: vi.fn(),
    start: vi.fn()
  })
}));

const FIXTURE: AppSettings = {
  version: 1,
  appearance: { theme: "system", appFontScale: 100, showRightPanel: true },
  reader: {
    fontSize: 18,
    lineHeight: 1.8,
    paragraphSpacing: 1,
    letterSpacing: 0,
    pageMargin: 56,
    appTheme: "system",
    readerBackground: "warm",
    epubStyleMode: "publisher",
    textConversion: "none",
    restoreLastPosition: true,
    readingMode: "scroll",
    tracking: {
      trackReadingSessions: true,
      idleTimeoutMs: 60000,
      progressSaveIntervalMs: 5000,
      sessionHeartbeatMs: 15000,
      sessionPersistIntervalMs: 30000,
      maxPausedBeforeNewSessionMs: 300000,
      endSessionOnBookSwitch: true,
      recordRecentReads: true,
      showReadingStatsCards: true
    },
    presets: []
  },
  ai: { provider: "openai-compatible", baseUrl: "", model: "", temperature: 0.7, hasApiKey: false, enabled: false },
  storage: { dataDirectory: "D:/data", libraryDirectory: "D:/library", storageMode: "portable", autoBackupEnabled: true },
  debug: { appVersion: "0.2.1", dataRoot: "D:/data", showStatsCards: false },
  updatedAt: "2026-09-27T00:00:00.000Z"
};

function renderedSettingIds(container: HTMLElement): string[] {
  return Array.from(container.querySelectorAll<HTMLElement>("[data-setting-id]")).map((el) => el.dataset.settingId ?? "");
}

async function collectAllSectionIds(): Promise<string[]> {
  const { container, getAllByRole } = render(<SettingsPage />);
  const nav = container.querySelector("nav.settings-nav");
  const categoryButtons = Array.from(nav?.querySelectorAll("button") ?? []);
  const all: string[] = [];
  for (const button of categoryButtons) {
    fireEvent.click(button);
    all.push(...renderedSettingIds(container));
  }
  return all;
}

describe("设置搜索 DOM 守卫（真实渲染五个分区）", () => {
  afterEach(() => {
    cleanup();
    useSettingsStore.setState({ settings: undefined, error: undefined, loading: false });
  });

  it("注册表中的每个 id 都能在对应分区渲染出唯一锚点", async () => {
    useSettingsStore.setState({ settings: FIXTURE });
    const ids = await collectAllSectionIds();
    const registryIds = SETTINGS_SEARCH_REGISTRY.map((entry) => entry.id);

    for (const id of registryIds) {
      expect(ids.filter((rendered) => rendered === id), `缺少锚点：${id}`).toHaveLength(1);
    }
  });

  it("渲染出的锚点全部已登记注册表（新增控件必须登记）", async () => {
    useSettingsStore.setState({ settings: FIXTURE });
    const ids = await collectAllSectionIds();
    const registryIds = new Set(SETTINGS_SEARCH_REGISTRY.map((entry) => entry.id));

    const orphans = [...new Set(ids)].filter((id) => !registryIds.has(id));
    expect(orphans, `有控件渲染了 data-setting-id 但未登记：${orphans.join(", ")}`).toEqual([]);
  });

  it("同一分区内不出现重复锚点（重复会让搜索跳错控件）", async () => {
    useSettingsStore.setState({ settings: FIXTURE });
    const { container } = render(<SettingsPage />);
    const nav = container.querySelector("nav.settings-nav");
    const categoryButtons = Array.from(nav?.querySelectorAll("button") ?? []);
    expect(categoryButtons.length).toBeGreaterThanOrEqual(5);

    for (const button of categoryButtons) {
      fireEvent.click(button);
      const sectionIds = renderedSettingIds(container);
      const duplicated = sectionIds.filter((id, index) => sectionIds.indexOf(id) !== index);
      expect(duplicated, `分区「${button.textContent}」内重复锚点`).toEqual([]);
    }
  });
});
