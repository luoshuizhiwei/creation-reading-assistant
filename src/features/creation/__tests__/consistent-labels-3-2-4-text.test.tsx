// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render } from "@testing-library/react";
import { OutlinePage } from "../outline/OutlinePage";
import { WritingDeskOutlineSidebar } from "../editor/desk/WritingDeskOutlineSidebar";
import { makeOutline } from "../outline/__tests__/fixtures";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";

/**
 * BD-247（WCAG 3.2.4 一致地识别 · **文字维度**）：同一对目的地，两处切换必须同名同序。
 *
 * 运行时那本账（scripts/audit-consistent-labels.mjs）量的是「按下去之后屏上剩下哪些组件」，
 * 用它自己的指纹口径证明两处切换是同一个功能；这里补的是便宜的那一半——把两处**真的渲染出来**，
 * 从无障碍树上读组名与档名，逐个字比对，并和普查登记的基准对一遍：只改 DOM 或只改基准，
 * 另一本账当场红。
 *
 * 为什么不在这里断言「切过去渲染的是同一对组件」：那要点下去看 DOM 换没换，jsdom 里
 * OutlineTree/CardBoard 的宿主条件与运行时差太多，容易写成「怎么改都过」的假牙（BD-246 那条
 * jsdom 拿不到 ProseMirror 焦点的教训同一类）。指纹归运行时账，字归这里。
 *
 * 这边也不读源码文件：jsdom 环境下 import.meta.url 不是 file: scheme，
 * readFileSync(new URL(rel, import.meta.url)) 当场抛「The URL must be of scheme file」（实测）。
 * 基准与源码怎么钉成同一份，交给同目录的 consistent-labels-census-source.test.ts（node 环境）。
 */

const actions = vi.hoisted(() => ({
  loadOutline: vi.fn(async () => undefined),
  loadScene: vi.fn(async () => undefined),
  runStructure: vi.fn(async () => true),
  previewStructure: vi.fn(async () => null),
  applyStructureWithProtection: vi.fn(async () => null),
  revertStructure: vi.fn(async () => null),
  loadCards: vi.fn(async () => undefined),
  loadCardTypes: vi.fn(async () => undefined),
  exportDraft: vi.fn(async () => ({ canceled: false, filePath: "/tmp/outline.md" }))
}));

vi.mock("@/hooks/useCreationActions", () => ({
  useCreationActions: () => actions
}));

const project = { id: "p1", title: "P1", setup: { chapterWorkflow: ["草稿", "已完成"] } };

function primeStores() {
  useUIStore.setState({ toasts: [], showToast: vi.fn() });
  useCreationStore.setState({ outlines: { p1: makeOutline() }, selectedSceneId: "s1", cards: [] });
}

/**
 * 认领那把视图切换：按「有一颗档的可见文本是『大纲树』」找，不预设组名——
 * 预设组名等于用结论找证据，两处都改名就什么都测不到。
 */
function viewSwitch(container: HTMLElement, where: string) {
  const groups = Array.from(container.querySelectorAll('[role="group"][aria-label]'));
  const hits = groups.filter((g) =>
    Array.from(g.querySelectorAll("button")).some((b) => (b.textContent || "").trim() === "大纲树")
  );
  expect(hits.length, `${where}：认领到的视图切换应恰好 1 组（0 组＝测不到，>1 组＝认领口径漂了）`).toBe(1);
  const g = hits[0] as HTMLElement;
  const buttons = Array.from(g.querySelectorAll("button"));
  return {
    group: (g.getAttribute("aria-label") || "").trim(),
    options: buttons.map((b) => (b.getAttribute("aria-label") || b.textContent || "").trim().replace(/\s+/g, " ")),
    pressedCount: buttons.filter((b) => b.getAttribute("aria-pressed") === "true").length,
    pressedNames: buttons.filter((b) => b.getAttribute("aria-pressed") === "true").map((b) => (b.textContent || "").trim()),
    visibleTexts: buttons.map((b) => (b.textContent || "").trim())
  };
}

function outlinePageSwitch() {
  primeStores();
  const { container } = render(<OutlinePage project={project} />);
  return viewSwitch(container, "大纲页");
}

function deskSidebarSwitch() {
  primeStores();
  const { container } = render(
    <WritingDeskOutlineSidebar
      projects={[project]}
      project={project}
      outline={makeOutline()}
      workflow={["草稿", "已完成"]}
      selectedSceneId="s1"
      onSelectProject={vi.fn()}
      onSelectScene={vi.fn()}
    />
  );
  return viewSwitch(container, "写作台侧栏");
}

/** 全应用统一的那一套说法（大屏为主入口）。运行时普查登记的基准与两处源码由
 *  consistent-labels-census-source.test.ts 钉成同一份，这边只认字面。 */
const BASELINE = { group: "大纲视图切换", options: ["大纲树", "场景卡板"] };

describe("BD-247 同一对目的地全程同名同序（3.2.4 文字维度）", () => {
  afterEach(cleanup);

  it("大纲页与写作台侧栏的视图切换：组名一致、档序一致", () => {
    const page = outlinePageSwitch();
    cleanup();
    const desk = deskSidebarSwitch();
    // 认领成立才算数：两处都得是「两颗档、静止态恰有一颗按下」的切换，否则下面的相等是自说自话。
    expect(page.pressedCount, "大纲页：静止态应恰有 1 档按下").toBe(1);
    expect(desk.pressedCount, "写作台侧栏：静止态应恰有 1 档按下").toBe(1);
    expect(page.options.length, "两处都应是两颗档的切换").toBe(2);
    expect(desk.options.length, "两处都应是两颗档的切换").toBe(2);
    expect(desk.group, "同一功能在两处挂了两个组名（用户要学两遍）").toBe(page.group);
    expect(desk.options, "同一对目的地在两处叫法或先后次序不同").toEqual(page.options);
  });

  it("两处都统一到大屏那一套说法（基准不许悄悄过期）", () => {
    const page = outlinePageSwitch();
    cleanup();
    const desk = deskSidebarSwitch();
    expect(page.group, "大纲页组名与统一说法不符").toBe(BASELINE.group);
    expect(page.options, "大纲页档序与统一说法不符").toEqual(BASELINE.options);
    expect(desk.group, "写作台侧栏组名与统一说法不符").toBe(BASELINE.group);
    expect(desk.options, "写作台侧栏档序与统一说法不符").toEqual(BASELINE.options);
  });

  it("这两颗档的识别文本真在 DOM 里，默认档也是同一个", () => {
    const page = outlinePageSwitch();
    cleanup();
    const desk = deskSidebarSwitch();
    // 可见文本（textContent）逐项相等——只挂 aria-label 的话这里就空了，普查那边会记 ℹ️ 另账。
    expect(page.visibleTexts, "大纲页：档名得是看得见的字").toEqual(["大纲树", "场景卡板"]);
    expect(desk.visibleTexts, "写作台侧栏：档名得是看得见的字").toEqual(["大纲树", "场景卡板"]);
    // 静止态同名还不够，默认档也得是同一个，否则「同名同序」之后仍是两套手感。
    expect(page.pressedNames, "大纲页默认档").toEqual(["大纲树"]);
    expect(desk.pressedNames, "写作台侧栏默认档").toEqual(["大纲树"]);
  });
});
