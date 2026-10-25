// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, cleanup, fireEvent, act } from "@testing-library/react";
import { WritingDesk } from "@/features/creation/editor/WritingDesk";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import { useAppStore } from "@/stores/app-store";
import type { CreationProjectNavigation, CreationProjectOutline, CreationProjectSummary, SceneBodyView } from "@/types/creation";

/**
 * BD-188：逐字/逐选择刷新的读数不得是 polite live region。
 * 专注 HUD 的字数每敲一键刷新、批注锚点每扩展一步选区刷新；挂 live region
 * 会在输入/选择过程中播报刷屏。正确形态：可见读数照常刷新，但节点不带
 * role="status"/aria-live。阳性对照：BD-187 侧栏常驻播报区不得被牵连删掉。
 */

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
  loadCardTypes: vi.fn(async () => undefined),
  loadProjectExport: vi.fn(async () => undefined)
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
    loadCardTypes: actions.loadCardTypes,
    loadProjectExport: actions.loadProjectExport
  })
}));

vi.mock("@/features/creation/editor/SceneEditor", async () => {
  const ReactActual = await vi.importActual<typeof import("react")>("react");
  interface StubProps {
    view?: SceneBodyView;
    onSave: (body: unknown) => Promise<unknown>;
    onReloadScene?: () => Promise<SceneBodyView | null | undefined>;
    onStatsChange?: (chars: number) => void;
    focusMode?: boolean;
    onToggleFocusMode?: () => void;
    typewriter?: boolean;
    onToggleTypewriter?: () => void;
  }
  const Stub = ReactActual.forwardRef<{ isDirty: () => boolean; saveNow: () => Promise<boolean> }, StubProps>(
    (props, ref) => {
      const [text, setText] = ReactActual.useState(() => props.view?.body.content?.[0]?.content?.[0]?.text ?? "");
      const dirty = ReactActual.useRef(false);
      ReactActual.useImperativeHandle(ref, () => ({
        isDirty: () => dirty.current,
        saveNow: async () => {
          if (!dirty.current) return true;
          const ok = await props.onSave(props.view?.sceneId ?? "scene", 1, { content: [{ type: "paragraph", content: [{ type: "text", text }] }] });
          dirty.current = false;
          return Boolean(ok);
        }
      }), [text]);
      return ReactActual.createElement(
        "div",
        { "data-testid": "scene-editor", "data-scene-id": props.view?.sceneId },
        ReactActual.createElement("textarea", {
          "aria-label": "正文",
          value: text,
          onChange: (event: { target: { value: string } }) => {
            setText(event.target.value);
            dirty.current = true;
            props.onStatsChange?.(event.target.value.length);
          }
        })
      );
    }
  );
  return { SceneEditor: Stub, SceneEditorHandle: class {} };
});

vi.mock("@/features/creation/outline/OutlineTree", () => ({
  OutlineTree: () => React.createElement("span", null, "大纲树桩")
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
  chapterCount: 1,
  sceneCount: 1,
  currentChars: 0
};

const navigation: CreationProjectNavigation = {
  projectId: "p1",
  chapters: [
    { id: "ch1", title: "第一章", sortOrder: 0, scenes: [{ id: "scene-a", title: "场景 A", revision: 1 }] }
  ]
};

const outline: CreationProjectOutline = {
  project: { id: "p1", title: "示例项目", revision: 1 },
  volumes: []
} as unknown as CreationProjectOutline;

function resetStores() {
  useCreationStore.setState({
    projects: [],
    selectedId: "p1",
    navigations: {},
    outlines: { p1: outline },
    cards: [],
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
  resetStores();
  actions.loadScene.mockImplementation(async (id: string) => {
    const v = viewOf(id, `${id} 初稿`);
    useCreationStore.setState((s) => ({ sceneViews: { ...s.sceneViews, [id]: v } }));
    return v;
  });
});

afterEach(() => cleanup());

function liveRegionNodes(root: ParentNode): number {
  return root.querySelectorAll('[role="status"], [aria-live]').length;
}

function liveSelfOrWithin(el: Element): boolean {
  return el.matches('[role="status"], [aria-live]') || el.querySelectorAll('[role="status"], [aria-live]').length > 0;
}

describe("BD-188 高频读数不做 live region", () => {
  it("专注模式 HUD 是可见读数：字数照常显示，节点不带 role=status/aria-live", async () => {
    let container!: HTMLElement;
    await act(async () => {
      ({ container } = render(
        <WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />
      ));
    });
    act(() => {
      useAppStore.getState().setCreationFocusMode(true);
    });

    const hud = container.querySelector(".writing-focus-status") as HTMLElement;
    expect(hud, "专注 HUD 应随 focusMode 出现").not.toBeNull();
    expect(hud.textContent).toContain("场景 A");
    expect(hud.textContent).toMatch(/[\d,]+ 字/);
    expect(hud.getAttribute("role"), "回退：HUD 又挂回 role=status，每敲一键刷屏").toBeNull();
    expect(hud.getAttribute("aria-live"), "回退：HUD 又挂回 aria-live").toBeNull();
    expect(liveRegionNodes(hud), "HUD 内任何子节点也不得是 live region").toBe(0);
  });

  it("输入后字数照常刷新，但刷新路径上没有 live region", async () => {
    let container!: HTMLElement;
    await act(async () => {
      ({ container } = render(
        <WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />
      ));
    });
    act(() => {
      useAppStore.getState().setCreationFocusMode(true);
    });
    fireEvent.change(screen.getByLabelText("正文"), { target: { value: "一二三四五" } });

    const hud = container.querySelector(".writing-focus-status") as HTMLElement;
    expect(hud.textContent).toContain("5 字");
    expect(liveSelfOrWithin(hud), "回退：字数每次刷新都坐在 live region 里，播报刷屏").toBe(false);
  });

  it("批注页签锚点读数是可见读数，不带 aria-live/role", async () => {
    let container!: HTMLElement;
    await act(async () => {
      ({ container } = render(
        <WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />
      ));
    });
    fireEvent.click(screen.getByRole("button", { name: "批注与引用" }));

    const anchor = container.querySelector(".writing-annotation-selection") as HTMLElement;
    expect(anchor, "批注表单的锚点读数应在页签切到批注后出现").not.toBeNull();
    expect(anchor.textContent).toContain("锚点");
    expect(anchor.textContent, "读数本身仍是可见的，只是不再被播报").toContain("尚未在正文中定位");
    expect(anchor.getAttribute("aria-live"), "回退：锚点读数又带 aria-live，拖选每步刷屏").toBeNull();
    expect(anchor.getAttribute("role")).toBeNull();
    expect(liveRegionNodes(anchor)).toBe(0);
  });

  it("阳性对照：侧栏 BD-187 常驻播报区未被牵连拆除", async () => {
    let container!: HTMLElement;
    await act(async () => {
      ({ container } = render(
        <WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />
      ));
    });
    const persistent = container.querySelector('span.sr-only[role="status"]');
    expect(persistent, "该摘的只是刷屏读数，常驻播报区必须还在").not.toBeNull();
    expect(persistent?.textContent).toBe("");
  });
});
