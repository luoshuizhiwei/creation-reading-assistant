// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { ProjectCardLinkDialog } from "@/features/creation/cards/ProjectCardLinkDialog";

/**
 * BD-190：关联成功的行把按钮换成「已关联」span，握着焦点的节点被卸载——
 * 焦点不得跌回 body（2.4.3）。交接给最近的未关联邻行按钮；无处可交接回搜索框。
 *
 * BD-260：四条用例都先等两帧再聚焦（原因见 flushFrames）；另加一条慢失败用例，
 * 把「onLink 跨了一帧以上才返回」这条真实路径也钉住。
 */

const listGlobalCards = vi.fn();

vi.mock("@/hooks/creation/useCardActions", () => ({
  useCardActions: () => ({ listGlobalCards })
}));

function makeCard(id: string, title: string) {
  return {
    id,
    projectId: null,
    linkedProjectIds: [],
    usageCount: 0,
    kind: "character",
    title,
    aliases: [],
    fields: {},
    tags: [],
    createdAt: "2026-09-11T00:00:00.000Z",
    updatedAt: "2026-09-11T00:00:00.000Z",
    revision: 1
  };
}

function renderDialog(props: Partial<React.ComponentProps<typeof ProjectCardLinkDialog>> = {}) {
  return render(
    <ProjectCardLinkDialog
      projectId="p1"
      linkedCardIds={[]}
      cardTypes={[]}
      onClose={() => {}}
      onLink={async () => true}
      {...props}
    />
  );
}

function rowOf(title: string): HTMLElement {
  return screen.getByText(title).closest("li") as HTMLElement;
}

/** 断言失败时把焦点实际落在哪写进说明（vitest 默认把按钮内容折成「…(1)」，看不出是谁抢的焦点）。 */
function activeLabel(): string {
  const el = document.activeElement;
  if (!(el instanceof HTMLElement)) return String(el);
  return `${el.tagName.toLowerCase()}「${(el.getAttribute("aria-label") ?? el.textContent ?? "").trim().slice(0, 24)}」`;
}

/** Dialog 打开时用 requestAnimationFrame 把焦点移到首个可聚焦元素「关闭」按钮（BD-127）。jsdom 的 rAF 是
 * 定时器驱动的：实测那一帧在渲染后几百毫秒才到，且与「列表就绪」只差一两毫秒、先后随卡顿翻转
 * （BD-260：整套 npm test 里偶发红，就是它落在了手动 focus 之后、断言之前，把焦点抢到了「关闭」上）。
 * 先冲刷两帧再布防——之后 Dialog 不会再挂帧（那个 effect 只依赖 [open]），断言才确定。
 * 写法同 card-type-editor-delete-focus.test.tsx。 */
const flushFrames = () =>
  new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));

beforeEach(() => {
  vi.clearAllMocks();
  listGlobalCards.mockResolvedValue([makeCard("c1", "林墨"), makeCard("c2", "北岸灯塔")]);
});

afterEach(() => cleanup());

describe("关联全局卡片：按钮换 span 后的焦点交接（BD-190）", () => {
  it("关联第一行成功后，焦点交给下一未关联行的按钮，原行显示已关联", async () => {
    renderDialog();
    await screen.findAllByRole("button", { name: /关联到当前项目/ });
    await flushFrames(); // 先让对话框开帧的 rAF 落定
    const clicked = within(rowOf("林墨")).getByRole("button", { name: /关联到当前项目/ });
    clicked.focus();
    fireEvent.click(clicked);

    await waitFor(() => expect(within(rowOf("林墨")).getByText("已关联")).toBeTruthy());
    expect(document.activeElement, `焦点跑到了 ${activeLabel()}`).toBe(within(rowOf("北岸灯塔")).getByRole("button", { name: /关联到当前项目/ }));
  });

  it("最后一张未关联行完成后没有可交邻行，焦点退回搜索框", async () => {
    renderDialog({ linkedCardIds: ["c1"] });
    await waitFor(() => expect(screen.queryByRole("button", { name: /关联到当前项目/ })).not.toBeNull());
    await flushFrames(); // 先让对话框开帧的 rAF 落定
    const clicked = within(rowOf("北岸灯塔")).getByRole("button", { name: /关联到当前项目/ });
    clicked.focus();
    fireEvent.click(clicked);

    await waitFor(() => expect(screen.queryByRole("button", { name: /关联到当前项目/ })).toBeNull());
    expect(document.activeElement, `焦点跑到了 ${activeLabel()}`).toBe(screen.getByLabelText("搜索全局卡片"));
  });

  it("对照：关联失败不换行、不抢焦点", async () => {
    renderDialog({ onLink: async () => false });
    await screen.findAllByRole("button", { name: /关联到当前项目/ });
    await flushFrames(); // 先让对话框开帧的 rAF 落定
    const clicked = within(rowOf("林墨")).getByRole("button", { name: /关联到当前项目/ });
    clicked.focus();
    fireEvent.click(clicked);

    // 按钮先转「关联中…」，onLink 返回 false 后回到原名——回到原名即处理链走完
    await waitFor(() => expect(within(rowOf("林墨")).getByRole("button", { name: /关联到当前项目/ })).toBe(clicked));
    expect(within(rowOf("林墨")).queryByText("已关联")).toBeNull();
    expect(document.activeElement, `焦点跑到了 ${activeLabel()}`).toBe(clicked);
  });

  it("关联失败且跨了一帧以上才返回：焦点仍留在原按钮，行不变（BD-260）", async () => {
    // 慢失败：onLink 要 40 毫秒后才返回 false（真实使用里是 IPC 往返）——这条路径以前没有用例钉。
    renderDialog({ onLink: async () => { await new Promise((resolve) => setTimeout(resolve, 40)); return false; } });
    await screen.findAllByRole("button", { name: /关联到当前项目/ });
    await flushFrames(); // 先让对话框开帧的 rAF 落定
    const clicked = within(rowOf("林墨")).getByRole("button", { name: /关联到当前项目/ });
    clicked.focus();
    fireEvent.click(clicked);

    await waitFor(() => expect(within(rowOf("林墨")).getByRole("button", { name: /关联到当前项目/ })).toBe(clicked));
    expect(within(rowOf("林墨")).queryByText("已关联")).toBeNull();
    expect(document.activeElement, `焦点跑到了 ${activeLabel()}`).toBe(clicked);
  });
});
