// @vitest-environment jsdom
/**
 * BD-251（WCAG 2.4.3 焦点顺序）：大纲编辑控件按下之后焦点不许跌回 body。
 *
 * 与 BD-220（outline-delete-focus.test.tsx）正相反：那支是条目被删掉、整行卸载；
 * 这支是**条目还在**，控件也还在 DOM 里，只是它到了排序边界而变 disabled，
 * 浏览器把焦点吐回 body。运行时实测见 scripts/audit-edit-focus.mjs 的红基线
 * （/tmp/bd251-red2.log：树·下移卷／上移章节／上移场景，卡板·状态下拉／上移／下移场景）。
 *
 * jsdom 不会像 Chromium 那样「控件变 disabled 就自动 blur」，所以这里手动 blur 到 body
 * 来复现那一拍——这一步是模拟浏览器行为，不是绕过判据：真实浏览器里的那一次 blur
 * 由运行时普查负责证明。
 */
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { OutlineTree } from "../OutlineTree";
import { CardBoard } from "../CardBoard";
import { makeOutline } from "./fixtures";

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

const workflow = ["草稿", "已完成"];

/** 把焦点交给某颗控件并「按下」它，随后复现浏览器对 disabled 控件的 blur。 */
function pressAndBlur(label: string) {
  const button = screen.getByLabelText(label);
  button.focus();
  fireEvent.keyDown(button, { key: "Enter" });
  fireEvent.click(button);
  act(() => {
    (document.activeElement as HTMLElement | null)?.blur?.();
  });
}

describe("BD-251：大纲树重排到边界后的焦点交接", () => {
  it("下移首卷：原钮到末尾变禁用，焦点交给同簇里还能用的那颗，不许留在 body", () => {
    vi.useFakeTimers();
    const outline = makeOutline();
    const props = {
      workflow,
      selectedSceneId: undefined,
      onSelectScene: vi.fn(),
      runStructure: vi.fn().mockResolvedValue(true)
    };
    const { rerender } = render(<OutlineTree {...props} outline={outline} />);
    const label = `下移卷「${outline.volumes[0].title}」`;
    pressAndBlur(label);

    // 结构刷新落地：卷一挪到末尾，那颗「下移卷」从此禁用（产品里由 reorder 写回触发）。
    const moved = { ...outline, volumes: [...outline.volumes].reverse() };
    rerender(<OutlineTree {...props} outline={moved} />);
    const held = screen.getByLabelText(label);
    expect((held as HTMLButtonElement).disabled).toBe(true);
    act(() => {
      vi.advanceTimersByTime(1600);
    });
    expect(document.activeElement).not.toBe(document.body);
    expect(document.querySelector(".outline-tree")?.contains(document.activeElement)).toBe(true);
  });

  it("没动过手就不许抢焦点：焦点在 body 上静默刷新，钩子不许自己跳出来", () => {
    vi.useFakeTimers();
    const outline = makeOutline();
    const props = {
      outline,
      workflow,
      selectedSceneId: undefined,
      onSelectScene: vi.fn(),
      runStructure: vi.fn().mockResolvedValue(true)
    };
    const { rerender } = render(<OutlineTree {...props} />);
    act(() => {
      (document.activeElement as HTMLElement | null)?.blur?.();
    });
    rerender(<OutlineTree {...props} outline={{ ...outline }} />);
    act(() => {
      vi.advanceTimersByTime(1600);
    });
    expect(document.activeElement).toBe(document.body);
  });
});

describe("BD-251：场景卡板 busy 期间的焦点交接", () => {
  it("改章节状态：busy 把下拉禁用、焦点掉到 body，事务落定后交回原下拉", async () => {
    vi.useFakeTimers();
    const outline = makeOutline();
    let release: (ok: boolean) => void = () => {};
    const onChapterSetStatus = vi.fn(() => new Promise<boolean>((resolve) => { release = resolve; }));
    render(
      <CardBoard
        outline={outline}
        workflow={workflow}
        onSelectScene={vi.fn()}
        onChapterSetStatus={onChapterSetStatus}
      />
    );
    const select = document.querySelector<HTMLSelectElement>(".card-board .outline-status-select")!;
    select.focus();
    fireEvent.change(select, { target: { value: workflow[1] } });
    await act(async () => {
      await Promise.resolve();
    });
    // busy 落地：产品把下拉禁用，Chromium 随之 blur（jsdom 里手动复现这一拍）。
    expect(select.disabled).toBe(true);
    act(() => {
      select.blur();
      document.body.focus();
    });

    await act(async () => {
      release(true);
      await Promise.resolve();
    });
    act(() => {
      vi.advanceTimersByTime(1600);
    });
    expect(document.activeElement).toBe(select);
  });
});
