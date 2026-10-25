// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ProjectCardLinkDialog } from "@/features/creation/cards/ProjectCardLinkDialog";

/**
 * BD-189：对话框打开后才开始的异步忙态，此前挂在随内容出现的 role="status" 上＝不播报。
 * 播报必须由常驻区承载，busy→结果的每次换文本才被读出。
 */

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

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

function renderDialog() {
  return render(
    <ProjectCardLinkDialog
      projectId="p1"
      linkedCardIds={[]}
      cardTypes={[]}
      onClose={() => {}}
      onLink={async () => true}
    />
  );
}

function region(): HTMLElement {
  return document.querySelector('span.sr-only[role="status"]') as HTMLElement;
}

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => cleanup());

describe("关联全局卡片对话框 · 常驻播报区（BD-189）", () => {
  it("busy 换到空结果：同一条常驻区换文本，可见横幅不再自带 role=status", async () => {
    const gate = deferred<ReturnType<typeof makeCard>[]>();
    listGlobalCards.mockReturnValue(gate.promise);
    renderDialog();

    const initial = region();
    expect(initial).not.toBeNull();
    expect(initial.textContent).toBe("正在读取全局卡片…");
    expect(document.querySelector('.project-card-link-empty[role="status"]'), "回退：可见横幅又挂回 role=status").toBeNull();
    expect(document.querySelectorAll('[role="status"]').length, "只能有一条 status 区域").toBe(1);

    gate.resolve([]);
    await waitFor(() => expect(region().textContent).toBe("没有符合条件的全局卡片。"));
    expect(region()).toBe(initial);
  });

  it("读到有结果时区域清空，检索触发的第二轮 busy→结果仍被同一节点播报", async () => {
    const first = deferred<unknown[]>();
    listGlobalCards.mockReturnValueOnce(first.promise);
    renderDialog();
    first.resolve([makeCard("c1", "林墨")]);
    await waitFor(() => expect(region().textContent).toBe(""));

    const second = deferred<unknown[]>();
    listGlobalCards.mockReturnValue(second.promise);
    fireEvent.change(screen.getByLabelText("搜索全局卡片"), { target: { value: "墨" } });

    const nodeBefore = region();
    await waitFor(() => expect(nodeBefore.textContent).toBe("正在读取全局卡片…"));
    second.resolve([]);
    await waitFor(() => expect(region().textContent).toBe("没有符合条件的全局卡片。"));
    expect(region()).toBe(nodeBefore);
    expect(listGlobalCards).toHaveBeenLastCalledWith({ cardKind: undefined, search: "墨" });
  });

  it("阳性对照：有结果的检索不留下多余播报文本", async () => {
    const first = deferred<unknown[]>();
    listGlobalCards.mockReturnValue(first.promise);
    renderDialog();
    first.resolve([makeCard("c1", "林墨")]);
    await waitFor(() => expect(region().textContent).toBe(""));
    expect(screen.getByText("林墨")).toBeTruthy();
  });
});
