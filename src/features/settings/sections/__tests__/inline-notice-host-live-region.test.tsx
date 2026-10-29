// @vitest-environment jsdom
/**
 * BD-191：InlineNotice 的宿主必须自带常驻播报区。
 *
 * BD-173 给 InlineNotice 挂了 role="status"，但这条族的用法常年是
 * `{msg && <InlineNotice .../>}`——polite 区随内容一起插入是哑的
 * （BD-175/180/187 判据），消息清空再重发还要再哑一次。修复把播报职责
 * 上收到宿主：常驻 `span.sr-only[role="status"]` 先于消息存在、只换文本，
 * 可见块 announce={false} 退回纯视觉。错误仍走 alert（插入即播合法）。
 *
 * 钉三件行为：
 *  1) 播报区先于消息存在（点击前后是同一个节点，节点身份不变）；
 *  2) 消息落定后由区域换文本承载，可见 InlineNotice 不再带 role="status"；
 *  3) 出错清空后区域还在（不随消息卸载），重发时仍是同一节点。
 */
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { AISection } from "../AISection";
import { DebugSection } from "../DebugSection";
import type { AppSettings } from "@/types/settings";

const aiService = vi.hoisted(() => ({
  saveAIApiKey: vi.fn(),
  clearAIApiKey: vi.fn(),
  testAIConnection: vi.fn(),
  updateAISettings: vi.fn()
}));

const updatesService = vi.hoisted(() => ({
  checkForAppUpdates: vi.fn(),
  openAppUpdateDownload: vi.fn()
}));

const maintenanceService = vi.hoisted(() => ({
  exportDebugInfo: vi.fn(),
  openLogDirectory: vi.fn()
}));

vi.mock("@/services/ai-service", () => ({
  saveAIApiKey: (input: unknown) => aiService.saveAIApiKey(input),
  clearAIApiKey: () => aiService.clearAIApiKey(),
  testAIConnection: () => aiService.testAIConnection(),
  updateAISettings: (patch: unknown) => aiService.updateAISettings(patch)
}));

vi.mock("@/services/updates-service", () => ({
  checkForAppUpdates: () => updatesService.checkForAppUpdates(),
  openAppUpdateDownload: (url: string) => updatesService.openAppUpdateDownload(url)
}));

vi.mock("@/services/maintenance-service", () => ({
  exportDebugInfo: () => maintenanceService.exportDebugInfo(),
  openLogDirectory: () => maintenanceService.openLogDirectory()
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
  ai: { provider: "openai-compatible", baseUrl: "", model: "", temperature: 0.7, hasApiKey: false, enabled: true },
  storage: { dataDirectory: "D:/data", libraryDirectory: "D:/library", storageMode: "portable", autoBackupEnabled: true },
  debug: { appVersion: "0.2.1", dataRoot: "D:/data", showStatsCards: false },
  updatedAt: "2026-09-27T00:00:00.000Z"
};

function statusRegions(container: HTMLElement): HTMLSpanElement[] {
  return Array.from(container.querySelectorAll<HTMLSpanElement>('span.sr-only[role="status"]'));
}

/** 可见提示块（.motion-notice）里找承载该文本的那一块，返回它的 role 属性。 */
function visibleNoticeRole(container: HTMLElement, text: string): string | null {
  const notice = Array.from(container.querySelectorAll(".motion-notice")).find((el) => el.textContent?.includes(text));
  expect(notice, `可见提示块应仍在界面上：${text}`).toBeTruthy();
  return notice!.getAttribute("role");
}

beforeEach(() => {
  vi.clearAllMocks();
  aiService.updateAISettings.mockResolvedValue(FIXTURE.ai);
});
afterEach(() => cleanup());

describe("AISection 播报区常驻（BD-191）", () => {
  function renderAI(settings: AppSettings = { ...FIXTURE, ai: { ...FIXTURE.ai, enabled: true, hasApiKey: false } }) {
    const props = {
      settings,
      patchSettings: vi.fn().mockResolvedValue(undefined),
      loadSettings: vi.fn().mockResolvedValue(undefined),
      resetSection: vi.fn(),
      showToast: vi.fn(),
      setError: vi.fn()
    };
    const result = render(<AISection {...props} />);
    return { ...result, ...props };
  }

  it("保存 Key 的结果由常驻区换文本承载，可见块不再自带 status", async () => {
    const { container } = renderAI();
    // 播报区先于任何消息存在：一条开关警示区 + 一条消息区，此刻消息区为空。
    const before = statusRegions(container);
    expect(before.length, "AISection 应有两条常驻播报区（Key 警示 + 操作消息）").toBeGreaterThanOrEqual(2);
    const messageRegion = before[1];
    expect(messageRegion.textContent).toBe("");

    aiService.saveAIApiKey.mockResolvedValue({ ...FIXTURE.ai, hasApiKey: true });
    fireEvent.change(screen.getByPlaceholderText("sk-..."), { target: { value: "sk-test" } });
    fireEvent.click(screen.getByRole("button", { name: "保存 API Key" }));

    await waitFor(() => expect(messageRegion.textContent).toBe("API Key 已加密保存。"));
    // 同一节点换文本（播报区没随消息重挂载），可见块只是视觉。
    expect(statusRegions(container)[1]).toBe(messageRegion);
    expect(visibleNoticeRole(container, "API Key 已加密保存。"), "回退：可见块又挂回 role=status，随内容插入即哑").toBeNull();
  });

  it("出错清空后播报区还在，重发结果仍是同一节点换文本", async () => {
    const { container, setError } = renderAI();
    const messageRegion = statusRegions(container)[1];

    aiService.saveAIApiKey.mockRejectedValue(new Error("密钥保存失败"));
    fireEvent.change(screen.getByPlaceholderText("sk-..."), { target: { value: "sk-test" } });
    fireEvent.click(screen.getByRole("button", { name: "保存 API Key" }));
    await waitFor(() => expect(setError).toHaveBeenCalledWith("密钥保存失败"));
    expect(messageRegion.textContent).toBe("");

    aiService.saveAIApiKey.mockResolvedValue({ ...FIXTURE.ai, hasApiKey: true });
    fireEvent.click(screen.getByRole("button", { name: "保存 API Key" }));
    await waitFor(() => expect(messageRegion.textContent).toBe("API Key 已加密保存。"));
    expect(statusRegions(container)[1], "回退：区域随消息卸载又重挂，第二次播报同样是哑的").toBe(messageRegion);
  });

  it("Key 警示区常驻：关掉开关只是清空区域，节点不卸载", () => {
    const { container, rerender } = renderAI();
    const warningRegion = statusRegions(container)[0];
    expect(warningRegion.textContent).toContain("尚未配置 API Key");

    rerender(<AISection
      settings={{ ...FIXTURE, ai: { ...FIXTURE.ai, enabled: false, hasApiKey: false } }}
      patchSettings={vi.fn()} loadSettings={vi.fn()} resetSection={vi.fn()} showToast={vi.fn()} setError={vi.fn()}
    />);
    const after = statusRegions(container)[0];
    expect(after).toBe(warningRegion);
    expect(after.textContent).toBe("");
    expect(screen.queryByText(/请先填写「API Key」并保存/)).toBeNull();
  });
});

describe("DebugSection 播报区常驻（BD-191）", () => {
  function renderDebug() {
    const props = {
      settings: FIXTURE,
      resetSection: vi.fn(),
      showToast: vi.fn(),
      setError: vi.fn()
    };
    const result = render(<DebugSection {...props} />);
    return { ...result, ...props };
  }

  it("检查更新的结果由常驻区换文本承载，可见块不再自带 status", async () => {
    const { container } = renderDebug();
    const before = statusRegions(container);
    expect(before.length, "DebugSection 应有两条常驻播报区（诊断消息 + 更新结果）").toBeGreaterThanOrEqual(2);
    const updateRegion = before[1];
    expect(updateRegion.textContent).toBe("");

    updatesService.checkForAppUpdates.mockResolvedValue({
      currentVersion: "0.2.1",
      latestVersion: "0.3.0",
      hasUpdate: true,
      releaseUrl: "https://github.com/x/y/releases/tag/v0.3.0",
      notes: "",
      desktopAssetName: "app-0.3.0.exe",
      desktopAssetUrl: "https://example/app-0.3.0.exe"
    });
    fireEvent.click(screen.getByRole("button", { name: "检查更新" }));

    await waitFor(() => expect(updateRegion.textContent).toContain("发现 v0.3.0"));
    expect(statusRegions(container)[1]).toBe(updateRegion);
    expect(updateRegion.textContent).toBe("发现 v0.3.0，当前版本 0.2.1。安装包：app-0.3.0.exe");
    expect(visibleNoticeRole(container, "安装包：app-0.3.0.exe"), "回退：更新结果块又挂回 role=status").toBeNull();
  });

  it("诊断导出结果走常驻区，可见块退回纯视觉", async () => {
    const { container } = renderDebug();
    const maintenanceRegion = statusRegions(container)[0];
    expect(maintenanceRegion.textContent).toBe("");

    maintenanceService.exportDebugInfo.mockResolvedValue({ outputRoot: "D:/dbg" });
    fireEvent.click(screen.getByRole("button", { name: "导出调试信息" }));

    await waitFor(() => expect(maintenanceRegion.textContent).toBe("调试信息已导出：D:/dbg"));
    expect(statusRegions(container)[0]).toBe(maintenanceRegion);
    expect(visibleNoticeRole(container, "调试信息已导出"), "回退：诊断结果块又挂回 role=status").toBeNull();
  });
});
