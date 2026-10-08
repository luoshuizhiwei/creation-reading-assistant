// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { act, render, screen, cleanup, fireEvent } from "@testing-library/react";
import { DesktopFrame } from "@/components/layout/DesktopFrame";
import { useAppStore } from "@/stores/app-store";
import { useSearchStore } from "@/stores/search-store";
import { useInspirationStore } from "@/stores/inspiration-store";
import { useCreationStore } from "@/stores/creation-store";
import { useLibraryStore } from "@/stores/library-store";

function resetStores(): void {
  useAppStore.setState({ screen: "projects", previousScreen: undefined, errors: [], creationFocusMode: false });
  useSearchStore.setState({ open: false });
  useInspirationStore.setState({ items: [] });
  useCreationStore.setState({ projects: [], cards: [], selectedCardId: undefined });
  useLibraryStore.setState({ books: [], progress: {} });
}

function navButtons(): HTMLButtonElement[] {
  const nav = screen.getByRole("navigation", { name: "一级导航" });
  return Array.from(nav.querySelectorAll<HTMLButtonElement>("button"));
}

function labelOf(button: HTMLButtonElement): string {
  return button.querySelector("strong")?.textContent?.trim() ?? "";
}

beforeEach(() => resetStores());
afterEach(() => cleanup());

describe("DesktopFrame 应用级导航（真实渲染）", () => {
  it("渲染恰好五个一级导航项，顺序正确；全局搜索由顶部搜索框与 Ctrl+K 承担，不再重复", () => {
    render(<DesktopFrame><div data-testid="content" /></DesktopFrame>);
    const buttons = navButtons();
    expect(buttons.length).toBe(5);
    expect(buttons.map(labelOf)).toEqual(["项目", "卡片库", "收件箱", "书库", "设置"]);
    expect(navButtons()[0].closest("nav")!.textContent).not.toContain("阅读统计");
    expect(navButtons()[0].closest("nav")!.textContent).not.toContain("全局搜索");
    expect(screen.getByRole("button", { name: /创作阅读助手/ })).toBeDefined();
  });

  it("当前 screen 标记 aria-current=page，其余无 aria-current", () => {
    useAppStore.setState({ screen: "inbox" });
    render(<DesktopFrame><div /></DesktopFrame>);
    const activeButtons = navButtons().filter((button) => button.getAttribute("aria-current") === "page");
    expect(activeButtons.length).toBe(1);
    expect(activeButtons[0]?.querySelector("strong")?.textContent).toBe("收件箱");
  });

  it("品牌按钮是回到项目首页的当前页标记", () => {
    render(<DesktopFrame><div /></DesktopFrame>);
    const brand = screen.getByRole("button", { name: /创作阅读助手/ });
    expect(brand.getAttribute("aria-current")).toBe("page");

    act(() => useAppStore.setState({ screen: "library" }));
    expect(brand.getAttribute("aria-current")).toBeNull();
  });

  it("点击品牌按钮回到项目首页", () => {
    useAppStore.setState({ screen: "settings" });
    render(<DesktopFrame><div /></DesktopFrame>);
    fireEvent.click(screen.getByRole("button", { name: /创作阅读助手/ }));
    expect(useAppStore.getState().screen).toBe("projects");
  });

  it("点击其他屏幕切换 screen", () => {
    useAppStore.setState({ screen: "projects" });
    render(<DesktopFrame><div /></DesktopFrame>);
    const libraryButton = navButtons().find((button) => labelOf(button) === "书库");
    fireEvent.click(libraryButton!);
    expect(useAppStore.getState().screen).toBe("library");
  });

  it("所有一级导航按钮都有明确的名称（aria-label 或文本）", () => {
    render(<DesktopFrame><div /></DesktopFrame>);
    for (const button of navButtons()) {
      const name = button.getAttribute("aria-label") ?? button.textContent?.trim() ?? "";
      expect(name.length).toBeGreaterThan(0);
    }
  });

  it("页头标题下那行「这一页的规矩」来自注册表 tip（批次 AZ：不再由 DesktopFrame 自带的第二份文案供给）", () => {
    // 三个屏幕各验一次：左栏项、未进左栏的屏幕、以及 tip 与原 body 措辞不同的屏幕。
    const cases: Array<["inbox" | "library" | "stats", string]> = [
      ["inbox", "旧灵感迁移与手动收集的内容；可转为创作项目的资料卡。"],
      ["library", "导入、筛选和打开本地 TXT / Markdown / EPUB，阅读时摘录到项目。"],
      // stats 不在左栏（从书库页进入），正好验到「注册表里登记但侧栏不显示」的那三个屏幕。
      ["stats", "查看阅读时长、书籍进度和节律总结。"]
    ];
    for (const [target, expected] of cases) {
      useAppStore.setState({ screen: target });
      const { unmount } = render(<DesktopFrame><div /></DesktopFrame>);
      const line = document.querySelector(".desktop-commandbar > div > span");
      expect(line?.textContent).toBe(expected);
      expect(line?.getAttribute("title")).toBe(expected);
      // 期望值不是从注册表现取的值复制的字符串，而是本批逐条抄下的文案：
      // 注册表被改坏（漏字、换序、拿错 hint 当 tip）在这里红，而不是界面上静默变一句话。
      unmount();
      cleanup();
    }
  });

  it("写作专注状态在应用壳级隐藏标题栏与左导航", () => {
    useCreationStore.setState({ selectedId: "project-1" });
    useAppStore.setState({ creationFocusMode: true });
    const { container } = render(<DesktopFrame><div>正文</div></DesktopFrame>);
    expect(container.querySelector(".desktop-root")?.classList.contains("desktop-root--focus")).toBe(true);
    expect(screen.queryByRole("navigation", { name: "一级导航" })).toBeNull();
    expect(screen.queryByRole("banner", { name: "窗口标题栏" })).toBeNull();
  });

  it("键盘聚焦显示可见焦点环，鼠标按下聚焦不显示", () => {
    render(<DesktopFrame><div /></DesktopFrame>);
    const projectsButton = navButtons()[0]!;

    fireEvent.focus(projectsButton);
    expect(projectsButton.style.outline).toContain("2px solid");

    fireEvent.blur(projectsButton);
    expect(projectsButton.style.outline).toBe("");

    fireEvent.pointerDown(projectsButton);
    fireEvent.focus(projectsButton);
    expect(projectsButton.style.outline).toBe("");
  });
});
