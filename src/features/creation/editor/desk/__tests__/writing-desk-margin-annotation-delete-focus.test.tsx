// @vitest-environment jsdom
/**
 * BD-221（BD-219/220 同族）：写作台批注删除后的焦点交接。
 *
 * 「删除批注」确认后整行卸载，握着焦点的按钮消失、焦点跌回 body（WCAG 2.4.3）。
 * 交接判据：原位置下一条优先，末尾退前一条；批注删空退空态文案。
 */
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { WritingDeskMargin } from "@/features/creation/editor/desk/WritingDeskMargin";
import { getAISettings } from "@/services/ai-service";
import type { Annotation, CreationProjectSummary } from "@/types/creation";

const actions = vi.hoisted(() => ({
  loadAnnotations: vi.fn(),
  createAnnotation: vi.fn(),
  updateAnnotation: vi.fn(),
  deleteAnnotation: vi.fn(),
  loadCards: vi.fn(),
  loadCardTypes: vi.fn(),
  saveSceneBody: vi.fn()
}));

vi.mock("@/hooks/useCreationActions", () => ({ useCreationActions: () => actions }));
vi.mock("@/services/ai-service", () => ({
  getAISettings: vi.fn(async () => ({ enabled: false })),
  runAIAction: vi.fn()
}));

const project: CreationProjectSummary = {
  id: "p1",
  title: "测试项目",
  setup: { genre: "", protagonist: "", premise: "" } as never,
  updatedAt: "2026-08-09T10:00:00.000Z",
  revision: 1,
  chapterCount: 1,
  sceneCount: 1
};

function makeAnnotation(id: string, note: string): Annotation {
  return {
    id,
    projectId: "p1",
    sceneId: "s1",
    cardId: null,
    anchor: { blockIndex: 0, textOffset: 0, textLength: note.length, text: note },
    anchorInvalid: false,
    note,
    status: "open",
    anchoredText: note,
    revision: 1,
    createdAt: "2026-08-09T10:00:00.000Z",
    updatedAt: "2026-08-09T10:00:00.000Z"
  };
}

const first = makeAnnotation("a-1", "第一条批注");
const second = makeAnnotation("a-2", "第二条批注");
const third = makeAnnotation("a-3", "第三条批注");

function renderMargin() {
  return render(
    <WritingDeskMargin
      project={project}
      outline={undefined}
      selectedSceneId="s1"
      selectedScene={{ id: "s1", title: "场景一", revision: 1 }}
      sceneView={undefined}
      characterCount={120}
      selection={null}
      referencePickerOpen={false}
      onOpenReferencePicker={vi.fn()}
      onCloseReferencePicker={vi.fn()}
      collapsed={false}
      onToggleCollapse={vi.fn()}
      focusMode={false}
      getActiveEditor={() => null}
      covered={false}
    />
  );
}

/** 打开「批注与引用」页签并返回该批注行的删除按钮（两步确认为「删除」→「删除批注」）。 */
async function openNotesAndDelete(note: string) {
  fireEvent.click(screen.getByRole("button", { name: "批注与引用" }));
  const noteNode = await screen.findByText(note, { selector: ".writing-annotation-note" });
  const row = noteNode.closest("li");
  const del = row!.querySelector("button:last-of-type") as HTMLButtonElement;
  del.focus();
  fireEvent.click(del);
  fireEvent.click(del);
}

beforeEach(() => {
  actions.loadAnnotations.mockReset();
  actions.deleteAnnotation.mockReset();
  vi.mocked(getAISettings).mockReset();
  vi.mocked(getAISettings).mockResolvedValue({ enabled: false } as never);
  actions.loadCards.mockResolvedValue(undefined);
  actions.loadCardTypes.mockResolvedValue(undefined);
  actions.createAnnotation.mockResolvedValue(true);
  actions.updateAnnotation.mockResolvedValue(true);
  actions.deleteAnnotation.mockResolvedValue(true);
});

afterEach(() => cleanup());

describe("BD-221：批注删除后的焦点交接", () => {
  it("删中间批注：焦点交给原位置下一条的删除钮", async () => {
    actions.loadAnnotations
      .mockResolvedValueOnce([first, second, third])
      .mockResolvedValue([first, third]);
    renderMargin();

    await openNotesAndDelete("第二条批注");

    await waitFor(() => expect(screen.queryByText("第二条批注")).toBeNull());
    await waitFor(() =>
      expect((document.activeElement as HTMLElement | null)?.closest("li")?.textContent ?? "").toContain("第三条批注")
    );
  });

  it("删空批注：焦点退到空态文案", async () => {
    actions.loadAnnotations.mockResolvedValueOnce([first]).mockResolvedValue([]);
    renderMargin();

    await openNotesAndDelete("第一条批注");

    await waitFor(() => expect(screen.queryByText("第一条批注")).toBeNull());
    await waitFor(() => {
      // 同上：判据必须是元素本身，不是 body 里的整页文本。
      expect(document.activeElement?.classList.contains("writing-annotation-empty")).toBe(true);
      expect((document.activeElement?.textContent ?? "")).toContain("暂无批注");
    });
  });
});
