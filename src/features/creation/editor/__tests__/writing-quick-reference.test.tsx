// @vitest-environment jsdom
import React, { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { WritingQuickReferencePanel } from "@/features/creation/editor/WritingQuickReferencePanel";
import type { CardSummary } from "@/types/creation";

const cardsList = vi.fn();
const cardTypesList = vi.fn();
const cardRead = vi.fn();
const cardLink = vi.fn();
const runStructure = vi.fn();

vi.mock("@/services/creation-service", () => ({
  cardsList: (...args: unknown[]) => cardsList(...args),
  cardTypesList: (...args: unknown[]) => cardTypesList(...args),
  cardRead: (...args: unknown[]) => cardRead(...args),
  cardLink: (...args: unknown[]) => cardLink(...args),
  runStructure: (...args: unknown[]) => runStructure(...args)
}));

function card(id: string, title: string, linkedProjectIds: string[]): CardSummary {
  return {
    id,
    projectId: linkedProjectIds[0] ?? null,
    linkedProjectIds,
    usageCount: linkedProjectIds.length,
    kind: "character",
    title,
    aliases: [],
    fields: {},
    tags: [],
    createdAt: "",
    updatedAt: "",
    revision: 1
  };
}

let projectCard = card("card-a", "林晚", ["project-a"]);
let globalCard = card("card-b", "顾淮", []);

function Harness({ onPlanningSaved = vi.fn(), onRestoreEditorFocus = vi.fn() }) {
  const [contextCards, setContextCards] = useState<CardSummary[]>([]);
  return (
    <WritingQuickReferencePanel
      open
      projectId="project-a"
      selectedSceneId="scene-a"
      planning={{ castCardIds: ["card-a"] }}
      contextCards={contextCards}
      onContextCardsChange={setContextCards}
      onPlanningSaved={onPlanningSaved}
      onClose={vi.fn()}
      onRestoreEditorFocus={onRestoreEditorFocus}
    />
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  window.localStorage.clear();
  projectCard = card("card-a", "林晚", ["project-a"]);
  globalCard = card("card-b", "顾淮", []);
  cardsList.mockImplementation(async (query: { projectId?: string; search?: string }) => {
    if (query.projectId) return [projectCard];
    return query.search ? [globalCard] : [];
  });
  cardTypesList.mockResolvedValue([{ id: "type-character", builtIn: true, projectId: null, kind: "character", name: "角色", fields: [], sortOrder: 0, createdAt: "", updatedAt: "", revision: 1 }]);
  cardRead.mockImplementation(async (id: string) => id === projectCard.id ? projectCard : globalCard);
  cardLink.mockImplementation(async () => {
    globalCard = { ...globalCard, linkedProjectIds: ["project-a"], usageCount: 1 };
    return { linked: true };
  });
  runStructure.mockImplementation(async (command: { type: string; title?: string }) => {
    if (command.type === "card.update") {
      projectCard = { ...projectCard, title: command.title ?? projectCard.title, revision: projectCard.revision + 1 };
    }
    return { commandType: command.type };
  });
});

afterEach(() => cleanup());

describe("WritingQuickReferencePanel", () => {
  it("按本场景显示卡片，并在字段失焦 800ms 后自动保存且显示状态", async () => {
    render(<Harness />);
    const item = await screen.findByRole("button", { name: /林晚/ });
    fireEvent.click(item);
    const title = await screen.findByLabelText("名称");
    fireEvent.change(title, { target: { value: "林晚（修订）" } });
    expect(screen.getByText("等待失焦保存")).toBeTruthy();
    fireEvent.blur(title);
    await waitFor(() => expect(runStructure).toHaveBeenCalledWith(expect.objectContaining({
      type: "card.update",
      cardId: "card-a",
      title: "林晚（修订）",
      baseRevision: 1
    })), { timeout: 1600 });
    expect(await screen.findByText("已保存")).toBeTruthy();
  });

  it("自动保存失败时保留本地修改和错误状态，下一次失焦可以重试", async () => {
    render(<Harness />);
    fireEvent.click(await screen.findByRole("button", { name: /林晚/ }));
    const title = await screen.findByLabelText("名称");
    fireEvent.change(title, { target: { value: "未丢失的修改" } });
    runStructure.mockRejectedValueOnce(new Error("磁盘暂时不可写"));
    fireEvent.blur(title);

    expect(await screen.findByText("保存失败", {}, { timeout: 1600 })).toBeTruthy();
    expect((screen.getByLabelText("名称") as HTMLInputElement).value).toBe("未丢失的修改");
    expect(screen.getByText(/修改仍保留/)).toBeTruthy();

    fireEvent.focus(title);
    fireEvent.blur(title);
    await waitFor(() => expect(runStructure).toHaveBeenCalledTimes(2), { timeout: 1600 });
    expect(await screen.findByText("已保存")).toBeTruthy();
  });

  it("全局搜索卡片可关联项目、加入本场景，并把操作焦点还给正文", async () => {
    const onPlanningSaved = vi.fn(async () => undefined);
    const onRestoreEditorFocus = vi.fn();
    render(<Harness onPlanningSaved={onPlanningSaved} onRestoreEditorFocus={onRestoreEditorFocus} />);
    fireEvent.click(screen.getByRole("button", { name: "全局搜索" }));
    fireEvent.change(screen.getByPlaceholderText("搜索名称、别名或字段"), { target: { value: "顾淮" } });
    await waitFor(() => expect(cardsList).toHaveBeenCalledWith({ kind: "cards.list", search: "顾淮" }));
    fireEvent.click(await screen.findByRole("button", { name: /顾淮/ }));
    fireEvent.click(await screen.findByRole("button", { name: "关联到项目" }));
    await waitFor(() => expect(cardLink).toHaveBeenCalledWith("project-a", "card-b"));
    const add = await screen.findByRole("button", { name: "加入本场景" });
    await waitFor(() => expect((add as HTMLButtonElement).disabled).toBe(false));
    fireEvent.click(add);
    await waitFor(() => expect(runStructure).toHaveBeenCalledWith(expect.objectContaining({
      type: "scene.updatePlanning",
      sceneId: "scene-a",
      planning: expect.objectContaining({ castCardIds: ["card-a", "card-b"] })
    })));
    expect(onPlanningSaved).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(onRestoreEditorFocus).toHaveBeenCalled());
  });

  // BD-145：速查范围此前只靠 .active 类表达，读屏器不知道当前在哪一档。
  it("速查范围用 aria-pressed 暴露当前档位", () => {
    render(<Harness />);
    const scene = () => screen.getByRole("button", { name: /本场景/ });
    const project = () => screen.getByRole("button", { name: /本项目/ });

    expect(scene().getAttribute("aria-pressed")).toBe("true");
    expect(project().getAttribute("aria-pressed")).toBe("false");

    fireEvent.click(project());
    expect(project().getAttribute("aria-pressed")).toBe("true");
    expect(scene().getAttribute("aria-pressed")).toBe("false");
  });

  // BD-150：速查列表的选中卡片此前只靠 .active 类表达。
  it("用 aria-current 暴露当前选中的速查卡片", async () => {
    render(<Harness />);
    const item = await screen.findByRole("button", { name: /林晚/ });
    expect(item.getAttribute("aria-current")).toBeNull();

    fireEvent.click(item);
    await waitFor(() =>
      expect(screen.getByRole("button", { name: /林晚/ }).getAttribute("aria-current")).toBe("true")
    );
  });

  // BD-154：索引整理期间列表里只有一行文字，读屏器不会播报。
  it("索引整理中的提示以 status 暴露", async () => {
    cardsList.mockImplementation(() => new Promise(() => {}));
    render(<Harness />);

    const hint = await screen.findByText("正在整理索引…");
    expect(hint.getAttribute("role")).toBe("status");
  });

  // BD-175：全局搜索是异步的，结果数与空态只出现在列表里；防抖期间也不能拿旧结果播报。
  it("全局搜索结果通过常驻 status 播报（BD-175）", async () => {
    cardsList.mockImplementation(async (query: { projectId?: string; search?: string }) => {
      if (query.projectId) return [projectCard];
      return query.search === "顾淮" ? [globalCard] : [];
    });
    const { container } = render(<Harness />);
    fireEvent.click(screen.getByRole("button", { name: "全局搜索" }));
    const status = container.querySelector('span.sr-only[role="status"]') as HTMLElement;
    expect(status).toBeTruthy();
    expect(status.textContent).toBe("");

    const input = screen.getByLabelText("搜索卡片");
    fireEvent.change(input, { target: { value: "顾淮" } });
    expect(status.textContent).toBe("正在搜索…");
    await waitFor(() => expect(status.textContent).toBe("共 1 张卡片"), { timeout: 1600 });

    fireEvent.change(input, { target: { value: "查无此卡" } });
    expect(status.textContent).toBe("正在搜索…");
    await waitFor(() => expect(status.textContent).toBe("没有匹配的卡片"), { timeout: 1600 });
  });

  // BD-159：AI 上下文条此前 aria-label 挂在普通 div 上，没有角色，名字被丢弃。
  it("速查 AI 上下文条是带名字的 group", async () => {
    render(<Harness />);

    expect(await screen.findByRole("group", { name: "速查 AI 上下文" })).toBeTruthy();
  });
});
