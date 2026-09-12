// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
import { WritingDesk } from "@/features/creation/editor/WritingDesk";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import { useAppStore } from "@/stores/app-store";
import type { SceneSelection } from "@/features/creation/editor/annotation-selection";
import type { Annotation, CreationProjectNavigation, CreationProjectSummary, SceneBodyView } from "@/types/creation";
import type { AIRunInput, AIRunResult } from "@/types/ai";

const actions = vi.hoisted(() => ({
  loadOutline: vi.fn(async () => undefined),
  loadScene: vi.fn(async () => undefined),
  runStructure: vi.fn(async () => true),
  saveSceneBody: vi.fn(async () => true),
  subscribeProject: vi.fn(() => () => undefined),
  reportSession: vi.fn(async () => undefined),
  loadAnnotations: vi.fn(async () => [] as Annotation[]),
  createAnnotation: vi.fn(async () => true),
  updateAnnotation: vi.fn(async () => true),
  reanchorAnnotation: vi.fn(async () => true),
  deleteAnnotation: vi.fn(async () => true),
  loadCards: vi.fn(async () => undefined),
  loadCardTypes: vi.fn(async () => undefined),
  loadProjectExport: vi.fn(async () => undefined)
}));

const aiService = vi.hoisted(() => ({
  getAISettings: vi.fn(async () => ({
    provider: "openai-compatible" as const,
    baseUrl: "https://example.invalid/v1",
    model: "acceptance-model",
    temperature: 0.2,
    hasApiKey: true,
    enabled: true
  })),
  runAIAction: vi.fn()
}));

const quickReferenceService = vi.hoisted(() => ({
  cardsList: vi.fn(async () => []),
  cardTypesList: vi.fn(async () => []),
  cardRead: vi.fn(async () => null),
  cardLink: vi.fn(async () => ({ linked: true }))
}));

vi.mock("@/hooks/useCreationActions", () => ({
  useCreationActions: () => ({
    loadOutline: actions.loadOutline,
    loadScene: actions.loadScene,
    runStructure: actions.runStructure,
    saveSceneBody: actions.saveSceneBody,
    subscribeProject: actions.subscribeProject,
    reportSession: actions.reportSession,
    loadAnnotations: actions.loadAnnotations,
    createAnnotation: actions.createAnnotation,
    updateAnnotation: actions.updateAnnotation,
    deleteAnnotation: actions.deleteAnnotation,
    loadCards: actions.loadCards,
    loadCardTypes: actions.loadCardTypes,
    loadProjectExport: actions.loadProjectExport
  })
}));

vi.mock("@/services/creation-service", () => ({
  annotationReanchor: actions.reanchorAnnotation,
  runStructure: actions.runStructure,
  cardsList: quickReferenceService.cardsList,
  cardTypesList: quickReferenceService.cardTypesList,
  cardRead: quickReferenceService.cardRead,
  cardLink: quickReferenceService.cardLink
}));

vi.mock("@/services/ai-service", () => ({
  getAISettings: aiService.getAISettings,
  runAIAction: aiService.runAIAction
}));

vi.mock("@/features/creation/editor/SceneEditor", () => ({
  SceneEditor: React.forwardRef<unknown, { view?: SceneBodyView }>((props, _ref) =>
    React.createElement("div", { "data-testid": "scene-editor" }, props.view?.sceneId ?? "")
  ),
  SceneEditorHandle: class {}
}));
vi.mock("@/features/creation/outline/OutlineTree", () => ({ OutlineTree: () => null }));
vi.mock("@/features/creation/outline/CardBoard", () => ({ CardBoard: () => null }));

function viewOf(sceneId: string, text: string): SceneBodyView {
  return {
    sceneId,
    revision: 1,
    body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text }] }] },
    loadedAt: Date.now()
  } as unknown as SceneBodyView;
}

const project: CreationProjectSummary = {
  id: "p1",
  title: "示例项目",
  setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["规划"] },
  updatedAt: "",
  revision: 1,
  chapterCount: 2,
  sceneCount: 4,
  currentChars: 0
};

const navigation: CreationProjectNavigation = {
  projectId: "p1",
  chapters: [
    {
      id: "ch1",
      title: "第一章",
      sortOrder: 0,
      scenes: [
        { id: "scene-a", title: "场景 A", revision: 1 },
        { id: "scene-b", title: "场景 B", revision: 1 }
      ]
    }
  ]
};

function card(id: string, projectId: string, title: string, aliases: string[] = []) {
  return {
    id,
    projectId,
    kind: "character",
    title,
    aliases,
    fields: { 性格: "克制" },
    tags: [],
    createdAt: "",
    updatedAt: "",
    revision: 1
  };
}

function annotationOf(): Annotation {
  return {
    id: "an1",
    sceneId: "scene-a",
    projectId: "p1",
    cardId: "c1",
    note: "这里的动机要更隐晦。",
    anchoredText: "A 初稿",
    anchor: { blockIndex: 0, start: 0, end: 3 },
    anchorInvalid: false,
    status: "open",
    createdAt: "",
    updatedAt: "",
    revision: 1
  } as unknown as Annotation;
}

function resetStores(options: { cards?: unknown[] } = {}) {
  useCreationStore.setState({
    projects: [],
    selectedId: "p1",
    navigations: {},
    outlines: {},
    cards: options.cards ?? [card("c1", "p1", "苏青")],
    loading: false,
    watchConnected: true,
    abnormalExit: false,
    recoveryNoticeDismissed: true,
    sceneViews: { "scene-a": viewOf("scene-a", "A 初稿") },
    selectedSceneId: "scene-a",
    selectScene: vi.fn(),
    dismissRecoveryNotice: vi.fn(),
    setLeaveGuard: vi.fn()
  });
  useUIStore.setState({ toasts: [], showToast: vi.fn() });
  useAppStore.setState({ creationFocusMode: false });
}

beforeEach(() => {
  if (!window.matchMedia) {
    Object.defineProperty(window, "matchMedia", {
      writable: true,
      value: () => ({
        matches: false,
        media: "",
        onchange: null,
        addEventListener: () => {},
        removeEventListener: () => {},
        addListener: () => {},
        removeListener: () => {},
        dispatchEvent: () => false
      })
    });
  }
  vi.clearAllMocks();
  resetStores();
  // 写作台挂载后会重新拉取当前场景：已有场景视图时原样返回，避免把预置正文冲掉。
  actions.loadScene.mockImplementation(async (id: string) => {
    const existing = useCreationStore.getState().sceneViews[id];
    const v = existing ?? viewOf(id, `${id} 初稿`);
    useCreationStore.setState((s) => ({ sceneViews: { ...s.sceneViews, [id]: v } }));
    return v;
  });
  actions.loadAnnotations.mockImplementation(async () => [annotationOf()]);
  aiService.runAIAction.mockImplementation(
    async (input: AIRunInput): Promise<AIRunResult> => ({
      kind: input.action,
      content: "AI 输出内容。",
      prompt: "",
      model: "acceptance-model"
    })
  );
});
afterEach(() => cleanup());

function mountDesk() {
  render(<WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />);
}

/** AI 设置是异步读取的，按钮在就绪前处于禁用态；必须等到可用再点击。 */
async function sendAction(testId: string): Promise<void> {
  const button = (await screen.findByTestId(testId)) as HTMLButtonElement;
  await waitFor(() => expect(button.disabled).toBe(false));
  fireEvent.click(button);
  await screen.findByTestId("ai-send-confirm");
  fireEvent.click(screen.getByTestId("ai-send-confirm-go"));
}

describe("写作台场景 AI（Stage 4-D）", () => {
  it("场景雷达提供六个 AI 动作入口，且全部可用", async () => {
    mountDesk();
    for (const action of ["polish", "expand", "continuation", "condensing", "consistency", "character-consistency"]) {
      const button = (await screen.findByTestId(`scene-ai-${action}`)) as HTMLButtonElement;
      await waitFor(() => expect(button.disabled).toBe(false));
    }
    expect(screen.getByTestId("scene-ai-continuation").textContent).toContain("续写场景");
    expect(screen.getByTestId("scene-ai-condensing").textContent).toContain("精简场景");
    expect(screen.getByTestId("scene-ai-character-consistency").textContent).toContain("角色一致性");
  });

  it("续写：携带场景上下文发送，结果进候选且标记为追加", async () => {
    mountDesk();
    await sendAction("scene-ai-continuation");

    await waitFor(() => expect(aiService.runAIAction).toHaveBeenCalledTimes(1));
    const input = aiService.runAIAction.mock.calls[0][0] as AIRunInput;
    expect(input.action).toBe("continuation");
    expect(input.sceneContext?.sceneTitle).toBe("场景 A");
    expect(input.sceneContext?.cardsText).toContain("苏青");
    expect(input.sceneContext?.annotationsText).toContain("动机要更隐晦");

    const review = await screen.findByTestId("scene-candidate-review");
    expect(review.textContent).toContain("续写场景");
    expect(screen.getByTestId("scene-candidate-append-hint")).toBeTruthy();
  });

  it("续写采纳：先建保护快照，再保存「原正文 + 续写」合并结果", async () => {
    aiService.runAIAction.mockImplementation(
      async (input: AIRunInput): Promise<AIRunResult> => ({
        kind: input.action,
        content: "续写新段落。",
        prompt: "",
        model: "acceptance-model"
      })
    );
    mountDesk();
    await sendAction("scene-ai-continuation");
    await screen.findByTestId("scene-candidate-review");

    fireEvent.click(screen.getByTestId("scene-candidate-accept"));

    await waitFor(() => expect(actions.saveSceneBody).toHaveBeenCalledTimes(1));
    const snapshotCall = actions.runStructure.mock.calls.find((call) => (call[0] as { type?: string })?.type === "snapshot.create");
    expect(snapshotCall).toBeTruthy();
    const [, , body] = actions.saveSceneBody.mock.calls[0] as [string, number, { content: unknown[] }];
    expect(JSON.stringify(body)).toContain("A 初稿");
    expect(JSON.stringify(body)).toContain("续写新段落。");
    // 文档层追加：原段落独立保留，不会被并成一段
    expect(body.content).toHaveLength(2);
    expect(JSON.stringify(body.content[0])).toContain("A 初稿");
    expect(JSON.stringify(body.content[1])).toContain("续写新段落。");
  });

  it("精简：候选为替换语义，采纳后正文不含原段落", async () => {
    aiService.runAIAction.mockImplementation(
      async (input: AIRunInput): Promise<AIRunResult> => ({
        kind: input.action,
        content: "精简后的唯一段落。",
        prompt: "",
        model: "acceptance-model"
      })
    );
    mountDesk();
    await sendAction("scene-ai-condensing");

    const review = await screen.findByTestId("scene-candidate-review");
    expect(review.textContent).toContain("精简场景");
    expect(screen.queryByTestId("scene-candidate-append-hint")).toBeNull();

    fireEvent.click(screen.getByTestId("scene-candidate-accept"));
    await waitFor(() => expect(actions.saveSceneBody).toHaveBeenCalledTimes(1));
    const [, , body] = actions.saveSceneBody.mock.calls[0] as [string, number, unknown];
    const serialized = JSON.stringify(body);
    expect(serialized).toContain("精简后的唯一段落。");
    expect(serialized).not.toContain("A 初稿");
  });

  it("角色一致性：只出只读报告，不提供采纳入口", async () => {
    mountDesk();
    await sendAction("scene-ai-character-consistency");

    const report = await screen.findByTestId("scene-ai-report");
    expect(report.textContent).toContain("角色一致性");
    expect(screen.queryByTestId("scene-candidate-review")).toBeNull();
    expect(screen.queryByTestId("scene-candidate-accept")).toBeNull();
    expect(actions.saveSceneBody).not.toHaveBeenCalled();
  });

  it("一致性检查仍是只读报告", async () => {
    mountDesk();
    await sendAction("scene-ai-consistency");
    const report = await screen.findByTestId("scene-ai-report");
    expect(report.textContent).toContain("一致性检查");
    expect(actions.saveSceneBody).not.toHaveBeenCalled();
  });

  it("被排除的上下文组不会进入 sceneContext，边界与用户勾选一致", async () => {
    mountDesk();
    const button = (await screen.findByTestId("scene-ai-continuation")) as HTMLButtonElement;
    await waitFor(() => expect(button.disabled).toBe(false));
    fireEvent.click(button);
    await screen.findByTestId("ai-send-confirm");
    fireEvent.click(screen.getByLabelText("包含 关联卡片"));
    fireEvent.click(screen.getByTestId("ai-send-confirm-go"));

    await waitFor(() => expect(aiService.runAIAction).toHaveBeenCalledTimes(1));
    const input = aiService.runAIAction.mock.calls[0][0] as AIRunInput;
    expect(input.sceneContext?.cardsText).toBeUndefined();
    expect(input.sceneContext?.annotationsText).toContain("动机要更隐晦");
  });

  it("AI 失败时只提示，不改写正文", async () => {
    aiService.runAIAction.mockRejectedValue(new Error("AI 请求失败：网络不可达"));
    mountDesk();
    await sendAction("scene-ai-condensing");

    await waitFor(() => expect(screen.queryByTestId("ai-send-confirm")).toBeNull());
    expect(screen.queryByTestId("scene-candidate-review")).toBeNull();
    expect(actions.saveSceneBody).not.toHaveBeenCalled();
    expect(useUIStore.getState().showToast).toHaveBeenCalled();
  });
});
