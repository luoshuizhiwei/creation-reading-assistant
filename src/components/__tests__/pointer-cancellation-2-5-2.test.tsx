// @vitest-environment jsdom
/**
 * BD-216 账本：WCAG 2.5.2 指针取消（Level A）。
 *
 * 主判据是运行时普查 scripts/audit-pointer-cancellation.mjs：对全局搜索、阅读设置
 * 抽屉、资料摘录选择器三个浮层各做「遮罩上按下 / 按下并松开 / 面板内按下拖到遮罩
 * 松开」三次指针动作，要求只在「按下且松开都在遮罩上」时关闭。
 *
 * 本批真实缺陷：三个遮罩都挂 onMouseDown 直接关闭——按下去浮层立刻消失，松手前
 * 已无法反悔；拖选、误触都算数（2.5.2 要求功能在 up 事件完成，或按下后能撤销）。
 * 修复：按下只记「起点是否在遮罩上」，pointerup 若落在遮罩外即撤销标记，松开后的
 * click 再核对目标仍是遮罩才关闭。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import React from "react";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { ExcerptPicker } from "@/features/library/ExcerptPicker";
import type { ExcerptSourceSnapshot } from "@/types/library";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const census = read("../../../scripts/audit-pointer-cancellation.mjs");
const search = read("../../features/search/SearchPanel.tsx");
const drawer = read("../../features/library/ReaderSettingsDrawer.tsx");
const picker = read("../../features/library/ExcerptPicker.tsx");
const pkg = read("../../../package.json");

beforeAll(() => {
  if (typeof requestAnimationFrame !== "function") {
    (globalThis as unknown as { requestAnimationFrame: (cb: FrameRequestCallback) => number }).requestAnimationFrame =
      (cb) => setTimeout(() => cb(0), 0) as unknown as number;
    (globalThis as unknown as { cancelAnimationFrame: (id: number) => void }).cancelAnimationFrame = (id) =>
      clearTimeout(id as unknown as ReturnType<typeof setTimeout>);
  }
});

const source: ExcerptSourceSnapshot = {
  bookId: "b1",
  bookTitle: "夜雨书",
  format: "txt",
  locationLabel: "第三章 · 42% 附近",
  excerpt: "窗外正下着雨。"
};

const renderPicker = (onClose: () => void) => {
  const view = render(
    <ExcerptPicker
      source={source}
      projects={[{ id: "p1", title: "项目甲" }]}
      isSubmitting={false}
      onClose={onClose}
      onSubmit={async () => ({ success: true })}
      onResult={() => {}}
    />
  );
  const dialog = screen.getByRole("dialog", { name: "资料摘录" });
  return { view, dialog, scrim: dialog.parentElement as HTMLElement };
};

describe("BD-216 行为：遮罩只在按下与松开都在遮罩上时关闭", () => {
  it("只在遮罩上按下不关闭", () => {
    const onClose = vi.fn();
    const { scrim } = renderPicker(onClose);
    fireEvent.pointerDown(scrim);
    expect(onClose).not.toHaveBeenCalled();
  });

  it("按下并松开（click 落在遮罩上）才关闭", () => {
    const onClose = vi.fn();
    const { scrim } = renderPicker(onClose);
    fireEvent.pointerDown(scrim);
    fireEvent.pointerUp(scrim);
    fireEvent.click(scrim);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("从面板内按下、拖到遮罩松开不关闭", () => {
    const onClose = vi.fn();
    const { dialog, scrim } = renderPicker(onClose);
    fireEvent.pointerDown(dialog);
    fireEvent.pointerUp(scrim);
    fireEvent.click(scrim);
    expect(onClose).not.toHaveBeenCalled();
  });

  it("在遮罩上按下、松到面板里也不关闭（up 事件撤销）", () => {
    const onClose = vi.fn();
    const { dialog, scrim } = renderPicker(onClose);
    fireEvent.pointerDown(scrim);
    fireEvent.pointerUp(dialog);
    fireEvent.click(scrim);
    expect(onClose).not.toHaveBeenCalled();
  });
});

describe("BD-216 产品锚：三个遮罩都不再在 mouse down 上直接关闭", () => {
  it("全局搜索", () => {
    expect(search).not.toContain("onMouseDown={closeSearch}");
    expect(search).toContain("scrimDownRef.current = event.target === event.currentTarget;");
    expect(search).toContain("onPointerUp={(event) => {");
  });

  it("阅读设置抽屉", () => {
    expect(drawer).not.toContain("onMouseDown={onClose}");
    expect(drawer).toContain("scrimDownRef.current = event.target === event.currentTarget;");
  });

  it("资料摘录选择器", () => {
    expect(picker).not.toContain("onMouseDown={onClose}");
    expect(picker).toContain("scrimDownRef.current = event.target === event.currentTarget;");
  });
});

describe("BD-216 普查口径", () => {
  it("红/绿结论、退出码与三次指针动作", () => {
    expect(census).toContain("POINTER_CANCEL_RED");
    expect(census).toContain("POINTER_CANCEL_OK");
    expect(census).toContain("process.exit(1)");
    expect(census).toContain("只在遮罩上按下就关闭了");
    expect(census).toContain("从浮层内部拖到遮罩松开也关闭了");
    expect(census).toContain("2.5.2");
  });

  it("三个运行时范围与 npm 脚本", () => {
    for (const label of ["全局搜索", "阅读设置抽屉", "资料摘录选择器"]) expect(census).toContain(label);
    expect(pkg).toContain('"audit:pointer-cancellation": "node scripts/audit-pointer-cancellation.mjs"');
  });
});
