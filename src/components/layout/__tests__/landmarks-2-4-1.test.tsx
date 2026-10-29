// @vitest-environment jsdom
/**
 * BD-212 账本：WCAG 1.3.1 / 2.4.1 / 4.1.2 的地标结构（Level A）。
 *
 * 主判据是运行时普查 scripts/audit-landmarks.mjs（14 个页面级视图活体点数）：
 *  · 每屏恰好一个 main——壳层 .desktop-canvas[role=main] 统一供给，页面内不再各自 <main>；
 *  · 至少一个有名 navigation（一级导航），complementary 的名字不许自称「导航」；
 *  · 每屏至少一个 h1——sr-only 标题算数（对 AT 完全暴露，视觉 0 像素）。
 *
 * 这里静态钉住判据骨架与产品锚，并用真实渲染证明：一屏一个 main、侧栏名字是「工作区侧栏」。
 *
 * 路径账（__tests__/ 起算）：../../../../ = 仓库根；../ = layout/；../../../features = src/features/。
 */
import React from "react";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { DesktopFrame } from "@/components/layout/DesktopFrame";
import { useAppStore } from "@/stores/app-store";
import { useSearchStore } from "@/stores/search-store";
import { useInspirationStore } from "@/stores/inspiration-store";
import { useCreationStore } from "@/stores/creation-store";
import { useLibraryStore } from "@/stores/library-store";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const census = read("../../../../scripts/audit-landmarks.mjs");
const frame = read("../DesktopFrame.tsx");
const cardsPage = read("../../../features/creation/cards/CardsPage.tsx");
const libraryPage = read("../../../features/creation/cards/GlobalCardLibraryPage.tsx");
const writingDesk = read("../../../features/creation/editor/WritingDesk.tsx");
const projectsPage = read("../../../features/creation/CreationProjectsPage.tsx");
const pkg = read("../../../../package.json");

function resetStores(): void {
  useAppStore.setState({ screen: "projects", previousScreen: undefined, errors: [], creationFocusMode: false });
  useSearchStore.setState({ open: false });
  useInspirationStore.setState({ items: [] });
  useCreationStore.setState({ projects: [], cards: [], selectedCardId: undefined, selectedId: undefined });
  useLibraryStore.setState({ books: [], progress: {} });
}

beforeEach(() => resetStores());
afterEach(() => cleanup());

describe("BD-212 普查判据：地标结构", () => {
  it("红/绿结论与退出码都在，条目各腿齐全", () => {
    expect(census).toContain("LANDMARK_RED");
    expect(census).toContain("LANDMARK_OK");
    expect(census).toContain("process.exit(1)");
    expect(census).toContain('if (r.main !== 1) why.push(`main=${r.main}`);');
    expect(census).toContain('if (r.nav === 0) why.push("无 navigation 地标");');
    expect(census).toContain('if (r.navUnnamed > 0) why.push(`有 ${r.navUnnamed} 个导航地标无名`);');
    expect(census).toContain("if (r.complMisnamed.length)");
    expect(census).toContain('if (r.h1 < 1) why.push("无 h1");');
  });

  it("complementary 自称「导航」是红判据（名字错报）", () => {
    expect(census).toContain("complMisnamed: compl.filter((el) => /导航/.test(nameOf(el)))");
  });

  it("地标只量暴露给 AT 的：aria-hidden 祖先与 <1px 矩形都不计", () => {
    expect(census).toContain("if (el.closest('[aria-hidden=\"true\"]')) return false;");
    expect(census).toContain("return r.width > 1 && r.height > 1;");
    expect(census).toContain('[role="main"], main');
    expect(census).toContain('nav, [role="navigation"]');
    expect(census).toContain('aside, [role="complementary"]');
  });

  it("h1 例外地不看矩形：sr-only 标题对 AT 有效，只排除 aria-hidden", () => {
    expect(census).toContain("const revealed = (el) => !el.closest('[aria-hidden=\"true\"]');");
    expect(census).toContain('const h1 = [...document.querySelectorAll("h1")].filter(revealed);');
    expect(census).toContain("h1 例外地不看矩形");
    expect(census).not.toContain('querySelectorAll("h1")].filter(exposed)');
  });

  it("14 个页面级范围一个不少", () => {
    for (const label of [
      "项目·首页",
      "项目·写作台",
      "项目·设定卡",
      "项目·设定卡（列表）",
      "项目·大纲",
      "项目·概览",
      "项目·全书预览",
      "项目·写作统计",
      "项目·版本历史",
      "书库",
      "卡片库",
      "收件箱",
      "资料阅读",
      "设置"
    ]) {
      expect(census).toContain(`["${label}"`);
    }
    expect(pkg).toContain('"audit:landmarks": "node scripts/audit-landmarks.mjs"');
  });
});

describe("BD-212 产品锚：一屏一个 main", () => {
  it("壳层 .desktop-canvas 供给唯一 main（真实渲染）", () => {
    const { container } = render(<DesktopFrame><div data-testid="content" /></DesktopFrame>);
    expect(container.querySelector(".desktop-canvas")?.getAttribute("role")).toBe("main");
    expect(screen.getAllByRole("main")).toHaveLength(1);
    expect(screen.getByTestId("content").closest('[role="main"]')).toBe(container.querySelector(".desktop-canvas"));
  });

  it("工作台沉浸态仍然只有一个 main，页面正文不另立地标", () => {
    useCreationStore.setState({ selectedId: "p1" });
    const { container } = render(<DesktopFrame><div data-testid="content" /></DesktopFrame>);
    expect(screen.getAllByRole("main")).toHaveLength(1);
    expect(container.querySelector(".desktop-stage--immersive")).not.toBeNull();
  });

  it("三个曾自带 <main> 的页面都退回 div（否则一屏两个 main）", () => {
    expect(cardsPage).not.toContain("<main");
    expect(libraryPage).not.toContain("<main");
    expect(writingDesk).not.toContain("<main");
    expect(cardsPage).toContain('<div className="cards-detail-pane">{detailPane}</div>');
    expect(libraryPage).toContain('<div className="cards-detail-pane">{detail}</div>');
    expect(writingDesk).toContain('<div className="writing-manuscript">');
  });

  it("工作台七个视图共用壳层 sr-only h1（项目名·视图名），随视图联动", () => {
    expect(projectsPage).toContain('<h1 className="sr-only">');
    expect(projectsPage).toContain('{selected.title} · {PROJECT_NAV_ITEMS.find((item) => item.view === view)?.label ?? "工作台"}');
  });
});

describe("BD-212 产品锚：地标名字", () => {
  it("侧栏 complementary 叫「工作区侧栏」，不再自称主导航", () => {
    expect(frame).toContain('aria-label="工作区侧栏"');
    expect(frame).not.toContain('aria-label="桌面端主导航"');
    expect(frame).toContain('<nav className="desktop-nav" aria-label="一级导航">');
  });

  it("真实渲染：complementary 的名字里没有「导航」", () => {
    render(<DesktopFrame><div /></DesktopFrame>);
    expect(screen.getByRole("complementary", { name: "工作区侧栏" })).toBeDefined();
    expect(screen.queryByRole("complementary", { name: /导航/ })).toBeNull();
    expect(screen.getByRole("navigation", { name: "一级导航" })).toBeDefined();
  });
});
