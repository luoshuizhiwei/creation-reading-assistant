// @vitest-environment jsdom
/**
 * WritingDesk ↔ writing-session-tracker 接线测试：
 *  - 输入（字符数变化）触发活动并结算上报；
 *  - 有意义选择（非折叠选区）触发活动；
 *  - 切场景结算旧段（sceneId 归属正确）；
 *  - 卸载 / 窗口隐藏触发结算；
 *  - 上报载荷只含数字与场景 ID，不含任何内容。
 *
 * 活动时长口径 = 最后活动时刻 - 段开始时刻（与主进程统计口径一致）：
 * 单次活动事件段结算时长为 0 会被模块按最短时长丢弃，因此结算前需要
 * 第二个活动事件推进 lastActivity。空闲 5 分钟边界在
 * writing-session-tracker 单元测试中覆盖。
 */
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, cleanup, fireEvent, act } from "@testing-library/react";
import { WritingDesk } from "@/features/creation/editor/WritingDesk";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import type { CreationProjectNavigation, CreationProjectSummary, SceneBodyView } from "@/types/creation";

const actions = vi.hoisted(() => ({
  loadOutline: vi.fn(async () => undefined),
  loadScene: vi.fn(async () => undefined),
  runStructure: vi.fn(async () => true),
  previewStructure: vi.fn(async () => null),
  applyStructureWithProtection: vi.fn(async () => null),
  revertStructure: vi.fn(async () => null),
  saveSceneBody: vi.fn(async () => true),
  subscribeProject: vi.fn(() => () => undefined),
  reportSession: vi.fn(async () => undefined),
  loadAnnotations: vi.fn(async () => []),
  createAnnotation: vi.fn(async () => true),
  updateAnnotation: vi.fn(async () => true),
  deleteAnnotation: vi.fn(async () => true),
  loadCards: vi.fn(async () => undefined),
  loadCardTypes: vi.fn(async () => undefined)
}));

vi.mock("@/hooks/useCreationActions", () => ({
  useCreationActions: () => ({
    loadOutline: actions.loadOutline,
    loadScene: actions.loadScene,
    runStructure: actions.runStructure,
    previewStructure: actions.previewStructure,
    applyStructureWithProtection: actions.applyStructureWithProtection,
    revertStructure: actions.revertStructure,
    saveSceneBody: actions.saveSceneBody,
    subscribeProject: actions.subscribeProject,
    reportSession: actions.reportSession,
    loadAnnotations: actions.loadAnnotations,
    createAnnotation: actions.createAnnotation,
    updateAnnotation: actions.updateAnnotation,
    deleteAnnotation: actions.deleteAnnotation,
    loadCards: actions.loadCards,
    loadCardTypes: actions.loadCardTypes
  })
}));

vi.mock("@/features/creation/editor/SceneEditor", async () => {
  const ReactActual = await vi.importActual<typeof import("react")>("react");
  interface StubProps {
    view?: SceneBodyView;
    onSave: (body: unknown) => Promise<unknown>;
    onReloadScene?: () => Promise<SceneBodyView | null | undefined>;
    onStatsChange?: (chars: number) => void;
    onSelectionChange?: (selection: unknown) => void;
    focusMode?: boolean;
    onToggleFocusMode?: () => void;
    typewriter?: boolean;
    onToggleTypewriter?: () => void;
  }
  const Stub = ReactActual.forwardRef<unknown, StubProps>(
    (props, ref) => {
      const [text, setText] = ReactActual.useState(() => props.view?.body.content?.[0]?.content?.[0]?.text ?? "");
      ReactActual.useImperativeHandle(ref, () => ({
        isDirty: () => false,
        saveNow: async () => true
      }));
      return ReactActual.createElement(
        "div",
        { "data-testid": "scene-editor", "data-scene-id": props.view?.sceneId },
        ReactActual.createElement("textarea", {
          "aria-label": "正文",
          value: text,
          onChange: (event: { target: { value: string } }) => {
            setText(event.target.value);
            props.onStatsChange?.(event.target.value.length);
          },
          onMouseUp: () => {
            props.onSelectionChange?.({
              sceneId: props.view?.sceneId,
              blockIndex: 0,
              textOffset: 0,
              textLength: 3,
              selectedText: text.slice(0, 3),
              collapsed: false
            });
          }
        })
      );
    }
  );
  return { SceneEditor: Stub, SceneEditorHandle: class {} };
});

vi.mock("@/features/creation/outline/OutlineTree", () => ({
  OutlineTree: () => null
}));
vi.mock("@/features/creation/outline/CardBoard", () => ({
  CardBoard: () => null
}));

function viewOf(sceneId: string, text: string): SceneBodyView {
  return {
    sceneId,
    revision: 1,
    body: { content: [{ type: "paragraph", content: [{ type: "text", text }] }] },
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
    { id: "ch1", title: "第一章", sortOrder: 0, scenes: [
      { id: "scene-a", title: "场景 A", revision: 1 },
      { id: "scene-b", title: "场景 B", revision: 1 }
    ] }
  ]
};

function resetStores(sceneViews: Record<string, SceneBodyView> = {}) {
  useCreationStore.setState({
    projects: [],
    selectedId: "p1",
    navigations: {},
    outlines: {},
    cards: [],
    loading: false,
    watchConnected: true,
    abnormalExit: false,
    recoveryNoticeDismissed: true,
    sceneViews,
    selectedSceneId: "scene-a",
    selectScene: vi.fn(),
    dismissRecoveryNotice: vi.fn(),
    setLeaveGuard: vi.fn()
  });
  useUIStore.setState({ toasts: [], showToast: vi.fn() });
}

beforeEach(() => {
  if (!window.matchMedia) {
    Object.defineProperty(window, "matchMedia", {
      writable: true,
      value: (query: string) => ({
        matches: false,
        media: query,
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
  resetStores({ "scene-a": viewOf("scene-a", "A 初稿"), "scene-b": viewOf("scene-b", "B 初稿") });
  actions.loadScene.mockImplementation(async (id: string) => {
    const v = viewOf(id, `${id} 初稿`);
    useCreationStore.setState((s) => ({ sceneViews: { ...s.sceneViews, [id]: v } }));
    return v;
  });
});

afterEach(() => {
  cleanup();
});

async function renderDesk() {
  await act(async () => {
    render(<WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />);
  });
}

/** 等待真实时钟跨过 tracker 最短可结算时长（1 秒）。 */
async function waitReal(ms: number) {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, ms));
  });
}

describe("WritingDesk 会话跟踪接线", () => {
  it("输入触发会话结算：上报含 sceneId/时长/净增，不含内容", async () => {
    await renderDesk();
    const input = screen.getByLabelText("正文") as HTMLTextAreaElement;
    fireEvent.change(input, { target: { value: "A 初稿扩展" } });
    await waitReal(1100);
    fireEvent.change(input, { target: { value: "A 初稿扩展二" } });
    cleanup();
    expect(actions.reportSession).toHaveBeenCalled();
    const payload = actions.reportSession.mock.calls.at(-1)?.[0] as {
      projectId: string;
      sceneId?: string;
      startedAt: string;
      activeSeconds: number;
      netChars: number;
    };
    expect(payload.projectId).toBe("p1");
    expect(payload.sceneId).toBe("scene-a");
    expect(typeof payload.startedAt).toBe("string");
    // 段起始字符数为首次活动（第一次 change）时的字符数。
    expect(payload.netChars).toBe("A 初稿扩展二".length - "A 初稿扩展".length);
    expect(payload.activeSeconds).toBeGreaterThanOrEqual(1);
    expect(payload).not.toHaveProperty("text");
    expect(payload).not.toHaveProperty("keys");
    expect(payload).not.toHaveProperty("content");
  });

  it("有意义选择（非折叠选区）触发活动并计时", async () => {
    await renderDesk();
    const input = screen.getByLabelText("正文") as HTMLTextAreaElement;
    fireEvent.mouseUp(input);
    await waitReal(1100);
    fireEvent.mouseUp(input);
    cleanup();
    expect(actions.reportSession).toHaveBeenCalledTimes(1);
    const payload = actions.reportSession.mock.calls[0][0] as { sceneId?: string; activeSeconds: number };
    expect(payload.sceneId).toBe("scene-a");
    expect(payload.activeSeconds).toBeGreaterThanOrEqual(1);
  });

  it("切场景结算旧段：场景 A 的段在切换时上报", async () => {
    await renderDesk();
    const input = screen.getByLabelText("正文") as HTMLTextAreaElement;
    fireEvent.change(input, { target: { value: "A 初稿 2" } });
    await waitReal(1100);
    fireEvent.change(input, { target: { value: "A 初稿 23" } });
    await act(async () => {
      useCreationStore.setState({ selectedSceneId: "scene-b" });
      await actions.loadScene("scene-b");
    });
    await waitReal(1100);
    cleanup();
    const reports = actions.reportSession.mock.calls.map((call) => call[0] as { sceneId?: string });
    expect(reports.some((r) => r.sceneId === "scene-a")).toBe(true);
  });

  it("窗口隐藏触发结算（visibilitychange → hidden）", async () => {
    await renderDesk();
    const input = screen.getByLabelText("正文") as HTMLTextAreaElement;
    fireEvent.change(input, { target: { value: "A 初稿扩展" } });
    await waitReal(1100);
    fireEvent.change(input, { target: { value: "A 初稿扩展二" } });
    Object.defineProperty(document, "visibilityState", { writable: true, configurable: true, value: "hidden" });
    document.dispatchEvent(new Event("visibilitychange"));
    expect(actions.reportSession).toHaveBeenCalled();
  });
});
