// @vitest-environment jsdom
/**
 * BD-173：阅读预设保存成功后会自己消失，此前是一个条件渲染的普通 div，
 * 读屏器既不会播报、消失也没有交代。改为常驻的 sr-only live region
 * （可见行仍按原样在 2 秒后移除，且对辅助技术隐藏以免重复）。
 */
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ReaderSettingsMorePanel } from "@/features/library/ReaderSettingsMorePanel";
import type { ReaderSettings } from "@/types/library";

vi.mock("@/services/reader-service", () => ({
  savePreset: vi.fn().mockResolvedValue(undefined)
}));

const settings: ReaderSettings = {
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
};

afterEach(() => cleanup());

describe("阅读预设保存提示（BD-173）", () => {
  it("保存成功后把提示写进常驻 status 区，可见行对辅助技术隐藏", async () => {
    render(<ReaderSettingsMorePanel settings={settings} onChange={vi.fn()} />);
    const region = screen.getByRole("status");
    expect(region.textContent).toBe("");

    fireEvent.change(screen.getByLabelText("阅读预设名称"), { target: { value: "夜间小字" } });
    fireEvent.click(screen.getByRole("button", { name: "保存" }));

    await waitFor(() => expect(screen.getByRole("status").textContent).toBe("已保存预设「夜间小字」"));
    const visible = screen.getByText("已保存预设「夜间小字」", { selector: "div" });
    expect(visible.getAttribute("aria-hidden")).toBe("true");
  });
});
