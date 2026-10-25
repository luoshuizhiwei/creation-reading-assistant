// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { ProjectCardLinkDialog } from "@/features/creation/cards/ProjectCardLinkDialog";

/**
 * BD-190：关联成功的行把按钮换成「已关联」span，握着焦点的节点被卸载——
 * 焦点不得跌回 body（2.4.3）。交接给最近的未关联邻行按钮；无处可交接回搜索框。
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

beforeEach(() => {
  vi.clearAllMocks();
  listGlobalCards.mockResolvedValue([makeCard("c1", "林墨"), makeCard("c2", "北岸灯塔")]);
});

afterEach(() => cleanup());

describe("关联全局卡片：按钮换 span 后的焦点交接（BD-190）", () => {
  it("关联第一行成功后，焦点交给下一未关联行的按钮，原行显示已关联", async () => {
    renderDialog();
    await screen.findAllByRole("button", { name: /关联到当前项目/ });
    const clicked = within(rowOf("林墨")).getByRole("button", { name: /关联到当前项目/ });
    clicked.focus();
    fireEvent.click(clicked);

    await waitFor(() => expect(within(rowOf("林墨")).getByText("已关联")).toBeTruthy());
    expect(document.activeElement).toBe(within(rowOf("北岸灯塔")).getByRole("button", { name: /关联到当前项目/ }));
  });

  it("最后一张未关联行完成后没有可交邻行，焦点退回搜索框", async () => {
    renderDialog({ linkedCardIds: ["c1"] });
    await waitFor(() => expect(screen.queryByRole("button", { name: /关联到当前项目/ })).not.toBeNull());
    const clicked = within(rowOf("北岸灯塔")).getByRole("button", { name: /关联到当前项目/ });
    clicked.focus();
    fireEvent.click(clicked);

    await waitFor(() => expect(screen.queryByRole("button", { name: /关联到当前项目/ })).toBeNull());
    expect(document.activeElement).toBe(screen.getByLabelText("搜索全局卡片"));
  });

  it("对照：关联失败不换行、不抢焦点", async () => {
    renderDialog({ onLink: async () => false });
    await screen.findAllByRole("button", { name: /关联到当前项目/ });
    const clicked = within(rowOf("林墨")).getByRole("button", { name: /关联到当前项目/ });
    clicked.focus();
    fireEvent.click(clicked);

    // 按钮先转「关联中…」，onLink 返回 false 后回到原名——回到原名即处理链走完
    await waitFor(() => expect(within(rowOf("林墨")).getByRole("button", { name: /关联到当前项目/ })).toBe(clicked));
    expect(within(rowOf("林墨")).queryByText("已关联")).toBeNull();
    expect(document.activeElement).toBe(clicked);
  });
});
