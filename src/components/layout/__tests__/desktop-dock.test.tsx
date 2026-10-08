// @vitest-environment jsdom
import { readFileSync } from "node:fs";
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { DesktopDock } from "@/components/layout/DesktopDock";
import { DesktopFrame } from "@/components/layout/DesktopFrame";
import { useAppStore } from "@/stores/app-store";
import { useSettingsStore } from "@/stores/settings-store";
import type { AppSettings, AppSettingsPatch } from "@/types/settings";

/**
 * 侧栏 dock（批次 BA，规格 §4.1 第 1 条）的测试。
 * 关注点不是「按钮能不能点」，而是 dock 存在的理由：它与设置页共用同一份设置，
 * 所以这里点出来的必须是**同一条写入通道、同一套取值范围**——如果 dock 自己存一套
 * 偏好、或把缩放范围记成另一组数字，它就是第二个设置页，§4.1 的「零层级可达」会
 * 变成「两处会不同步」。
 */

const updateSettings = vi.fn();
const resetSettingsSection = vi.fn();
const openDataDirectory = vi.fn();

vi.mock("@/services/settings-service", () => ({
  getSettings: vi.fn().mockResolvedValue(undefined),
  updateSettings: (...args: unknown[]) => updateSettings(...args),
  resetSettingsSection: (...args: unknown[]) => resetSettingsSection(...args),
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

vi.mock("@/services/maintenance-service", () => ({
  openDataDirectory: (...args: unknown[]) => openDataDirectory(...args)
}));

function fixture(overrides?: { theme?: AppSettings["appearance"]["theme"]; scale?: number }): AppSettings {
  return {
    version: 1,
    appearance: { theme: overrides?.theme ?? "system", appFontScale: overrides?.scale ?? 1, showRightPanel: true },
    reader: {} as AppSettings["reader"],
    ai: {} as AppSettings["ai"],
    storage: { dataDirectory: "D:/data", libraryDirectory: "D:/library", storageMode: "portable" },
    debug: { appVersion: "0.2.1", dataRoot: "D:/data", showStatsCards: false },
    updatedAt: "2026-10-08T00:00:00.000Z"
  };
}

beforeEach(() => {
  updateSettings.mockReset();
  // 真实服务返回合并后的整份设置，useSettingsActions 拿它回写 store；
  // 这里照做，连点两次「缩小」才会像界面上那样累进（0.95 → 0.9），
  // 否则组件每次都读到同一个 scale，测的就不是用户看到的行为。
  updateSettings.mockImplementation(async (patch: AppSettingsPatch) => {
    const current = useSettingsStore.getState().settings ?? fixture();
    return {
      ...current,
      appearance: { ...current.appearance, ...(patch.appearance ?? {}) },
      storage: { ...current.storage, ...(patch.storage ?? {}) }
    };
  });
  resetSettingsSection.mockReset();
  openDataDirectory.mockReset().mockResolvedValue(undefined);
  useSettingsStore.setState({ settings: fixture(), error: undefined, loading: false });
  useAppStore.setState({ screen: "projects", previousScreen: undefined, errors: [], creationFocusMode: false });
});

afterEach(() => cleanup());

function dotFor(label: string): HTMLButtonElement {
  return screen.getByRole("button", { name: `主题：${label}` });
}

describe("DesktopDock 常驻工具坞", () => {
  it("渲染四个入口：主题色点、缩放加减、数据目录、同步", () => {
    render(<DesktopDock />);
    expect(screen.getByRole("group", { name: "应用主题" })).toBeDefined();
    expect(screen.getByRole("group", { name: "界面缩放" })).toBeDefined();
    expect(screen.getByRole("button", { name: "打开数据目录：D:/data" })).toBeDefined();
    expect(screen.getByRole("button", { name: "同步设置" })).toBeDefined();
    // §4.1 列的第五项「保存状态」这一批刻意没做：保存态还在 SceneEditor 的局部 state 里，
    // 应用壳拿不到。这里钉住它缺席，是为了让下一批补上时**必须**改动本断言（而不是
    // 悄悄多一个入口），也免得后人把「dock 只有四条」当成漏做。
    expect(screen.queryByRole("button", { name: /保存状态/ })).toBeNull();
  });

  it("主题色点反映当前设置，点另一颗走 patchSettings 的同一条通道", async () => {
    render(<DesktopDock />);
    expect(dotFor("跟随系统").getAttribute("aria-pressed")).toBe("true");
    expect(dotFor("深色").getAttribute("aria-pressed")).toBe("false");

    fireEvent.click(dotFor("深色"));
    expect(updateSettings).toHaveBeenCalledTimes(1);
    const patch = updateSettings.mock.calls[0][0] as AppSettingsPatch;
    expect(patch).toEqual({ appearance: { theme: "dark" } });
    // 只带 appearance 一个键：dock 不得把整份 settings 回写（那会盖掉别处的并发修改）。
    expect(Object.keys(patch)).toEqual(["appearance"]);
  });

  it("缩放显示为百分比，加减按 0.05 一档走，且上下限夹住不外溢", async () => {
    useSettingsStore.setState({ settings: fixture({ scale: 1 }) });
    const { unmount } = render(<DesktopDock />);
    expect(screen.getByText("100%")).toBeDefined();

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "放大界面" }));
    });
    expect(updateSettings).toHaveBeenLastCalledWith({ appearance: { appFontScale: 1.05 } });
    expect(screen.getByText("105%")).toBeDefined();

    // 回写后再点才叫累进（真人连点之间 store 早就刷新了；塞进同一个 act 批次的话
    // 两次都读到同一个 1.05，测出来的是同值重复写入）。序列：1.05 → 1 → 0.95 → 0.9。
    // 第二档同时是浮点回归：1.05 - 0.05 在 JS 里是 1.0000000000000002，
    // clampScale 的取整把它收回 1，否则页头根字号会写成 "100.00000000000002%"。
    for (const expected of [1, 0.95, 0.9]) {
      await act(async () => {
        fireEvent.click(screen.getByRole("button", { name: "缩小界面" }));
      });
      expect(updateSettings).toHaveBeenLastCalledWith({ appearance: { appFontScale: expected } });
      expect(screen.getByText(`${Math.round(expected * 100)}%`)).toBeDefined();
    }
    expect(updateSettings).toHaveBeenCalledTimes(4);
    unmount();
    cleanup();

    // 已到上限再加：值不动，也不发写入（发出去就是把 1.45 写进设置，越界）。
    useSettingsStore.setState({ settings: fixture({ scale: 1.4 }) });
    render(<DesktopDock />);
    expect(screen.getByText("140%")).toBeDefined();
    updateSettings.mockClear();
    fireEvent.click(screen.getByRole("button", { name: "放大界面" }));
    expect(updateSettings).not.toHaveBeenCalled();

    cleanup();
    useSettingsStore.setState({ settings: fixture({ scale: 0.85 }) });
    render(<DesktopDock />);
    expect(screen.getByText("85%")).toBeDefined();
    updateSettings.mockClear();
    fireEvent.click(screen.getByRole("button", { name: "缩小界面" }));
    expect(updateSettings).not.toHaveBeenCalled();
  });

  it("浮点相加不写出 0.8999999999 这种值（缩放到两档边界要落在档位上）", async () => {
    // 0.85 + 0.05 在 JS 里是 0.9000000000000001；不取整就会把脏值写进设置并让
    // 页头根字号变成 "90.00000000000001%"。
    useSettingsStore.setState({ settings: fixture({ scale: 0.85 }) });
    render(<DesktopDock />);
    fireEvent.click(screen.getByRole("button", { name: "放大界面" }));
    expect(updateSettings).toHaveBeenCalledWith({ appearance: { appFontScale: 0.9 } });
  });

  it("数据目录入口把本机路径放进可访问名称，点击调用打开目录", () => {
    useSettingsStore.setState({ settings: fixture() });
    render(<DesktopDock />);
    const button = screen.getByRole("button", { name: "打开数据目录：D:/data" });
    expect(button.getAttribute("title")).toBe("D:/data");
    fireEvent.click(button);
    expect(openDataDirectory).toHaveBeenCalledTimes(1);
  });

  it("设置尚未就绪时不编造路径：入口仍可点，文案退回未设置", () => {
    useSettingsStore.setState({ settings: undefined });
    render(<DesktopDock />);
    const button = screen.getByRole("button", { name: "打开数据目录：未设置" });
    expect(button.getAttribute("title")).toBe("数据目录尚未就绪");
    // 主题退回「跟随系统」而不是没有选中态——dock 显示的必须与 App.tsx applyTheme
    // 的缺省一致，否则界面上会出现一个谁都没选中的主题。
    expect(dotFor("跟随系统").getAttribute("aria-pressed")).toBe("true");
    expect(screen.getByText("100%")).toBeDefined();
  });

  it("同步入口只做一件事：切到设置屏（不在 dock 里起停同步服务）", () => {
    render(<DesktopDock />);
    fireEvent.click(screen.getByRole("button", { name: "同步设置" }));
    expect(useAppStore.getState().screen).toBe("settings");
    expect(updateSettings).not.toHaveBeenCalled();
  });

  it("缩放范围与设置页那只滑杆同源：改一处必须同步改测试（防止两套数字悄悄分叉）", async () => {
    // AppearanceSection 的 Slider 写死 min=0.85 max=1.4 step=0.05。这里把它当契约读，
    // 而不是再抄一份常量——两处各记一套就是 §4.1 第 3 条反对的「多份真相」。
    const source = readFileSync("src/features/settings/sections/AppearanceSection.tsx", "utf8");
    const read = (name: string): number => {
      const hit = new RegExp(`${name}=\\{([0-9.]+)\\}`).exec(source);
      if (!hit) throw new Error(`AppearanceSection 里找不到 ${name}，本判据的读法需要跟着改`);
      return Number(hit[1]);
    };
    const [min, max, step] = [read("min"), read("max"), read("step")];
    useSettingsStore.setState({ settings: fixture({ scale: min }) });
    render(<DesktopDock />);
    fireEvent.click(screen.getByRole("button", { name: "缩小界面" }));
    expect(updateSettings).not.toHaveBeenCalled(); // dock 的下限 = 滑杆的 min
    updateSettings.mockClear();
    fireEvent.click(screen.getByRole("button", { name: "放大界面" }));
    expect(updateSettings).toHaveBeenCalledWith({ appearance: { appFontScale: Number((min + step).toFixed(2)) } });
    updateSettings.mockClear();
    useSettingsStore.setState({ settings: fixture({ scale: max }) });
    cleanup();
    render(<DesktopDock />);
    fireEvent.click(screen.getByRole("button", { name: "放大界面" }));
    expect(updateSettings).not.toHaveBeenCalled(); // dock 的上限 = 滑杆的 max
  });

  it("dock 是侧栏末尾追加的一节，不混进「一级导航」，也不改被断言的类名", () => {
    // 第 9 步的硬约束是「不得改被断言的类名」，dock 只能新增。这里两条都钉：
    //   · desktop-frame-nav.test.tsx 按 nav[aria-label="一级导航"] 数出恰好 5 个按钮，
    //     dock 若被塞进那个容器就变成 9 个；
    //   · .desktop-sidebar / .desktop-nav / .nav-spine-item / .desktop-sidebar-card
    //     这些类名必须还在原位（改名会让 visual-capture 与各验收脚本静默查无元素）。
    render(<DesktopFrame><div /></DesktopFrame>);
    const sidebar = document.querySelector(".desktop-sidebar");
    expect(sidebar).not.toBeNull();
    const nav = sidebar!.querySelector("nav.desktop-nav[aria-label='一级导航']");
    expect(nav?.querySelectorAll("button")).toHaveLength(5);
    const dock = sidebar!.querySelector("section.desktop-dock");
    expect(dock).not.toBeNull();
    expect(nav!.contains(dock!)).toBe(false);
    // dock 排在状态卡之后（侧栏最后一节）。
    const kids = [...sidebar!.children];
    expect(kids[kids.length - 1]).toBe(dock);
    expect(sidebar!.querySelector(".desktop-sidebar-card")).not.toBeNull();
  });

  it("dock 不携带 data-setting-id：设置项锚点唯一性归设置页独占", () => {
    // settings-search-dom-guard.test.tsx 断言注册表里每个 id 在设置页渲染出**唯一**一个
    // [data-setting-id] 锚点。dock 与设置页是同一份设置（主题、缩放）的两个入口，
    // 一旦这里也给控件挂上 appearance.theme / appearance.appFontScale，那条判据立刻红——
    // 而且搜索跳转的 scrollIntoView 会命中两个元素。dock 是入口，不是第二份设置表单。
    const { container } = render(<DesktopDock />);
    expect(container.querySelectorAll("[data-setting-id]")).toHaveLength(0);
  });
});
