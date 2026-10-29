// @vitest-environment jsdom
/**
 * BD-213 账本：WCAG 2.5.3 Label in Name（AA）。
 *
 * 主判据是运行时普查 scripts/audit-label-in-name.mjs：14 个页面级视图，逐控件比对
 * 「可见标签文本」是否是可访问名的子串（忽略大小写/空白）。可见标签剔除补充信息——
 * <small> 副标题、<em> 角标、印章字形、项目卡的时间/字数、收件箱摘要、纯符号字形——
 * 只留真正要念出来的字。
 *
 * 本批实测到的真实缺陷（普查先红后绿）：
 *  · 工作台四个包操作：可见「导出包 / 加密导出 / 导入包 / 加密导入」，名字却是
 *    「导出项目包 / 导出加密项目包 / 导入项目包 / 导入加密项目包」——中间插字，念不对；
 *  · 顶栏搜索：可见「搜索项目、收件箱、资料 Ctrl K」，名字只有「打开全局搜索（Ctrl K）」。
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
const census = read("../../../../scripts/audit-label-in-name.mjs");
const frame = read("../DesktopFrame.tsx");
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

describe("BD-213 普查判据：可见标签必须进入可访问名", () => {
  it("红/绿结论、退出码与判定腿都在", () => {
    expect(census).toContain("LABEL_RED");
    expect(census).toContain("LABEL_IN_NAME_OK");
    expect(census).toContain("process.exit(1)");
    expect(census).toContain("if (norm(name).includes(norm(label))) continue;");
    expect(census).toContain("judged += 1;");
  });

  it("可见标签剔除补充信息：副标题、角标、印章字形、元数据、纯符号", () => {
    expect(census).toContain("svg, small, em, .desktop-brand-mark, .project-home-meta, .project-home-progress, .inbox-item-meta, .inbox-item-body, [role=\"progressbar\"], [aria-hidden=\"true\"]");
    expect(census).toContain("const hasWord = (t) => /[\\p{L}\\p{N}]/u.test(t);");
  });

  it("14 个范围与 npm 脚本", () => {
    for (const label of ["项目·首页", "项目·写作台", "项目·设定卡", "项目·设定卡（列表）", "书库", "卡片库", "收件箱", "资料阅读", "设置"]) {
      expect(census).toContain(`["${label}"`);
    }
    expect(pkg).toContain('"audit:label-in-name": "node scripts/audit-label-in-name.mjs"');
  });
});

describe("BD-213 产品锚：可见字进了名字", () => {
  it("顶栏搜索的可见提示就是名字的一部分（真实渲染）", () => {
    render(<DesktopFrame><div /></DesktopFrame>);
    const search = screen.getByRole("button", { name: /搜索项目、收件箱、资料/ });
    expect(search.getAttribute("aria-label")).toBe("打开全局搜索：搜索项目、收件箱、资料 Ctrl K");
    const visible = Array.from(search.querySelectorAll("span, kbd")).map((el) => el.textContent?.trim()).join(" ");
    expect(search.getAttribute("aria-label")).toContain(visible);
  });

  it("四个包操作的名字与可见文字逐字对齐，全称留在 title 里", () => {
    for (const [visible, full] of [["导出包", "导出项目包"], ["加密导出", "导出加密项目包"], ["导入包", "导入项目包"], ["加密导入", "导入加密项目包"]]) {
      expect(projectsPage).toContain(`aria-label="${visible}" title="${full}"`);
    }
    expect(projectsPage).not.toContain('aria-label="导出项目包"');
    expect(projectsPage).not.toContain('aria-label="导入项目包"');
    expect(projectsPage).not.toContain('aria-label="导出加密项目包"');
    expect(projectsPage).not.toContain('aria-label="导入加密项目包"');
  });

  it("副标题/角标不进可见标签口径（否则导航与项目卡会被误判）", () => {
    expect(frame).toContain("<strong>{item.label}</strong>");
    expect(frame).toContain("<small>{item.hint}</small>");
  });
});
