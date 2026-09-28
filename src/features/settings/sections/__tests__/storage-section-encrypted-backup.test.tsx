// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { StorageSection } from "../StorageSection";
import type { AppSettings } from "@/types/settings";
import type { UseOperationResult } from "@/hooks/useOperation";

vi.mock("@/services/settings-service", () => ({
  chooseBackupDirectory: vi.fn().mockResolvedValue(null),
  chooseDataDirectory: vi.fn().mockResolvedValue(null),
  chooseLibraryDirectory: vi.fn().mockResolvedValue(null),
  getStorageLocations: vi.fn().mockResolvedValue({
    dataDirectory: "D:/data",
    libraryDirectory: "D:/library",
    portableDataDirectory: "D:/portable",
    fallbackDataDirectory: "D:/fallback",
    storageMode: "portable",
    dataDirectoryWritable: true,
    libraryDirectoryWritable: true
  }),
  migrateDataDirectory: vi.fn(),
  migrateLibraryDirectory: vi.fn()
}));

vi.mock("@/services/sync-service", () => ({
  createPairingToken: vi.fn(),
  getSyncStatus: vi.fn().mockResolvedValue({ running: false, addresses: [], device: { deviceId: "d" }, pairingToken: undefined }),
  listSyncDevices: vi.fn().mockResolvedValue([]),
  removeSyncDevice: vi.fn().mockResolvedValue([]),
  startSyncServer: vi.fn(),
  stopSyncServer: vi.fn()
}));

vi.mock("@/services/maintenance-service", () => ({
  openDataDirectory: vi.fn(),
  runAutoBackup: vi.fn()
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

function makeOperation(start = vi.fn().mockResolvedValue(null)): UseOperationResult {
  return {
    state: null,
    operationId: null,
    isActive: false,
    isCommitting: false,
    error: null,
    start,
    cancel: vi.fn(),
    reset: vi.fn(),
    retry: vi.fn()
  } as unknown as UseOperationResult;
}

function renderSection(overrides: Partial<Parameters<typeof StorageSection>[0]> = {}) {
  const start = vi.fn().mockResolvedValue(null);
  const confirmAction = vi.fn().mockResolvedValue(true);
  const props = {
    settings: FIXTURE,
    patchSettings: vi.fn().mockResolvedValue(undefined),
    loadSettings: vi.fn().mockResolvedValue(undefined),
    resetSection: vi.fn(),
    operation: makeOperation(start),
    confirmAction,
    showToast: vi.fn(),
    setError: vi.fn(),
    ...overrides
  };
  render(<StorageSection {...props} />);
  return { ...props, start, confirmAction };
}

afterEach(cleanup);

describe("StorageSection 加密备份入口", () => {
  it("渲染导出/恢复加密备份按钮与算法说明", () => {
    renderSection();
    expect(screen.getByRole("button", { name: "导出加密备份" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "恢复加密备份" })).toBeTruthy();
    expect(screen.getByText(/AES-256-GCM/)).toBeTruthy();
    expect(screen.getByText(/\.crbackup/)).toBeTruthy();
  });

  it("导出流程：先设置口令（最短 8 位 + 二次确认 + 知晓勾选），提交后才启动加密导出", async () => {
    const { start } = renderSection();
    fireEvent.click(screen.getByRole("button", { name: "导出加密备份" }));
    fireEvent.change(screen.getByTestId("pe-passphrase"), { target: { value: "short" } });
    fireEvent.change(screen.getByTestId("pe-confirm"), { target: { value: "short" } });
    fireEvent.click(screen.getByTestId("pe-ack"));
    expect((screen.getByTestId("pe-submit") as HTMLButtonElement).disabled).toBe(true);
    fireEvent.change(screen.getByTestId("pe-passphrase"), { target: { value: "longenough" } });
    fireEvent.change(screen.getByTestId("pe-confirm"), { target: { value: "longenough" } });
    fireEvent.click(screen.getByTestId("pe-submit"));
    await waitFor(() => expect(start).toHaveBeenCalledWith({ kind: "backup.export-encrypted", passphrase: "longenough" }));
    await waitFor(() => expect(screen.queryByTestId("passphrase-dialog")).toBeNull());
  });

  it("恢复流程：未经危险确认不得打开口令输入框", async () => {
    const confirmAction = vi.fn().mockResolvedValue(false);
    renderSection({ confirmAction });
    fireEvent.click(screen.getByRole("button", { name: "恢复加密备份" }));
    await waitFor(() => expect(confirmAction).toHaveBeenCalledTimes(1));
    expect(screen.queryByTestId("passphrase-dialog")).toBeNull();
  });

  it("恢复流程：确认后以 confirm 模式输入口令，单次输入即可启动导入（口令不要求 8 位）", async () => {
    const { start } = renderSection();
    fireEvent.click(screen.getByRole("button", { name: "恢复加密备份" }));
    await waitFor(() => expect(screen.getByTestId("passphrase-dialog")).toBeTruthy());
    expect(screen.queryByTestId("pe-confirm")).toBeNull();
    expect(screen.getByTestId("pe-description").textContent).toContain("口令错误");
    fireEvent.change(screen.getByTestId("pe-passphrase"), { target: { value: "x" } });
    fireEvent.click(screen.getByTestId("pe-submit"));
    await waitFor(() => expect(start).toHaveBeenCalledWith({ kind: "backup.import-encrypted", passphrase: "x" }));
  });

  it("启动失败时错误显示在口令框内且不关闭对话框", async () => {
    const start = vi.fn().mockRejectedValue(new Error("口令错误，无法解密"));
    renderSection({ operation: makeOperation(start) });
    fireEvent.click(screen.getByRole("button", { name: "恢复加密备份" }));
    await waitFor(() => expect(screen.getByTestId("passphrase-dialog")).toBeTruthy());
    fireEvent.change(screen.getByTestId("pe-passphrase"), { target: { value: "wrong" } });
    fireEvent.click(screen.getByTestId("pe-submit"));
    await waitFor(() => expect(screen.getByTestId("pe-error").textContent).toContain("口令错误，无法解密"));
    expect(screen.getByTestId("passphrase-dialog")).toBeTruthy();
  });
});
