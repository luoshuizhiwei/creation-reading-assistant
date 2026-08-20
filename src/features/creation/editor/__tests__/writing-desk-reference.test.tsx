// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, cleanup, fireEvent, waitFor, act } from "@testing-library/react";
import { WritingDesk } from "@/features/creation/editor/WritingDesk";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import type { SceneSelection } from "@/features/creation/editor/annotation-selection";
import type { Annotation, CreationProjectNavigation, CreationProjectSummary, SceneBodyView } from "@/types/creation";

const actions = vi.hoisted(() => ({
  loadOutline: vi.fn(async () => undefined),
  loadScene: vi.fn(async () => undefined),
  runStructure: vi.fn(async () => true),
  saveSceneBody: vi.fn(async () => true),
  subscribeProject: vi.fn(() => () => undefined),
  reportSession: vi.fn(async () => undefined),
  loadAnnotations: vi.fn(async () => []),
  createAnnotation: vi.fn(async () => true),
  updateAnnotation: vi.fn(async () => true),
  reanchorAnnotation: vi.fn(async () => true),
  deleteAnnotation: vi.fn(async () => true),
  loadCards: vi.fn(async () => undefined),
  loadProjectExport: vi.fn(async () => undefined)
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
    loadProjectExport: actions.loadProjectExport
  })
}));

// reanchorAnnotation 在 WritingDesk 内就地包装自服务的 annotationReanchor（useCreationActions 未导出该函数）。
vi.mock("@/services/creation-service", () => ({
  annotationReanchor: actions.reanchorAnnotation
}));

interface StubProps {
  view?: SceneBodyView;
  onSave: (sceneId: string, baseRevision: number, body: unknown) => Promise<unknown>;
  onReloadScene?: () => Promise<SceneBodyView | null | undefined>;
  onStatsChange?: (chars: number) => void;
  onSelectionChange?: (selection: SceneSelection | null) => void;
  onMentionTrigger?: (selection: SceneSelection) => void;
  focusMode?: boolean;
  onToggleFocusMode?: () => void;
  typewriter?: boolean;
  onToggleTypewriter?: () => void;
}

const editorControl = vi.hoisted(() => ({
  selection: null as SceneSelection | null,
  composing: false
}));

vi.mock("@/features/creation/editor/SceneEditor", async () => {
  const ReactActual = await vi.importActual<typeof import("react")>("react");
  const Stub = ReactActual.forwardRef<{ isDirty: () => boolean; saveNow: () => Promise<boolean>; getSelection: () => SceneSelection | null; isComposing: () => boolean }, StubProps>(
    (props, ref) => {
      const [text, setText] = ReactActual.useState(() => props.view?.body.content?.[0]?.content?.[0]?.text ?? "");
      const dirty = ReactActual.useRef(false);
      const selectionRef = ReactActual.useRef<SceneSelection | null>(null);
      ReactActual.useImperativeHandle(ref, () => ({
        isDirty: () => dirty.current,
        saveNow: async () => {
          if (!dirty.current) return true;
          const ok = await props.onSave(props.view?.sceneId ?? "scene", 1, { content: [{ type: "paragraph", content: [{ type: "text", text }] }] });
          dirty.current = false;
          return Boolean(ok);
        },
        getSelection: () => editorControl.selection ?? selectionRef.current,
        isComposing: () => editorControl.composing
      }));
      const reportSelection = (selection: SceneSelection | null) => {
        selectionRef.current = selection;
        props.onSelectionChange?.(selection);
      };
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
          },
          onFocus: () => {
            reportSelection({
              sceneId: props.view?.sceneId ?? "scene",
              blockIndex: 0,
              textOffset: 0,
              textLength: 1,
              selectedText: "",
              collapsed: true
            });
          }
        }),
        ReactActual.createElement("button", {
          "data-testid": "stub-mention",
          onClick: () =>
            props.onMentionTrigger?.({
              sceneId: props.view?.sceneId ?? "scene",
              blockIndex: 0,
              textOffset: 0,
              textLength: 4,
              selectedText: "选中文字",
              collapsed: false
            })
        })
      );
    }
  );
  return { SceneEditor: Stub, SceneEditorHandle: class {} };
});

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
    { id: "ch1", title: "第一章", sortOrder: 0, scenes: [
      { id: "scene-a", title: "场景 A", revision: 1 },
      { id: "scene-b", title: "场景 B", revision: 1 }
    ] },
    { id: "ch2", title: "第二章", sortOrder: 1, scenes: [
      { id: "scene-c", title: "场景 C", revision: 1 },
      { id: "scene-d", title: "场景 D", revision: 1 }
    ] }
  ]
};

function card(id: string, projectId: string, title: string, aliases: string[] = []) {
  return {
    id,
    projectId,
    kind: "character",
    title,
    aliases,
    fields: {},
    tags: [],
    createdAt: "",
    updatedAt: "",
    revision: 1
  };
}

function resetStores(options: { cards?: unknown[]; sceneViews?: Record<string, SceneBodyView> } = {}) {
  useCreationStore.setState({
    projects: [],
    selectedId: "p1",
    navigations: {},
    outlines: {},
    cards: options.cards ?? [],
    loading: false,
    watchConnected: true,
    abnormalExit: false,
    recoveryNoticeDismissed: true,
    sceneViews: options.sceneViews ?? {},
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
  editorControl.selection = null;
  editorControl.composing = false;
  resetStores({ sceneViews: { "scene-a": viewOf("scene-a", "A 初稿") } });
  actions.loadScene.mockImplementation(async (id: string) => {
    const v = viewOf(id, `${id} 初稿`);
    useCreationStore.setState((s) => ({ sceneViews: { ...s.sceneViews, [id]: v } }));
    return v;
  });
});
afterEach(() => cleanup());

describe("WritingDesk 真实选区与 @ 卡片引用", () => {
  it("@ 触发打开当前项目卡片搜索；主名称命中", async () => {
    resetStores({ cards: [card("c1", "p1", "苏青"), card("c2", "p1", "顾淮")], sceneViews: { "scene-a": viewOf("scene-a", "正文") } });
    render(<WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />);

    fireEvent.click(screen.getByTestId("stub-mention"));

    const dialog = await screen.findByRole("dialog", { name: "引用卡片搜索" });
    expect(dialog).toBeDefined();
    expect(dialog.textContent).toContain("苏青");
    expect(dialog.textContent).toContain("顾淮");
  });

  it("卡片别名也可命中；异项目卡片不可见", async () => {
    resetStores({
      cards: [
        card("c1", "p1", "苏青", ["小苏", "青姐"]),
        card("foreign", "p9", "异项目角色")
      ],
      sceneViews: { "scene-a": viewOf("scene-a", "正文") }
    });
    render(<WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />);

    fireEvent.click(screen.getByTestId("stub-mention"));
    const input = await screen.findByPlaceholderText("搜索卡片（标题或别名）…");

    // 异项目卡片不可见
    expect(screen.queryByText("异项目角色")).toBeNull();
    // 主名称命中
    fireEvent.change(input, { target: { value: "苏青" } });
    const dialog = screen.getByRole("dialog", { name: "引用卡片搜索" });
    expect(dialog.textContent).toContain("苏青");
    // 别名命中
    fireEvent.change(input, { target: { value: "青姐" } });
    expect(dialog.textContent).toContain("苏青");
    expect(dialog.textContent).toContain("别名：小苏、青姐");
  });

  it("选择卡片后提交批注：正文 JSON 完全一致（引用不写正文）", async () => {
    const createdAnnotations: Array<Parameters<typeof actions.createAnnotation>[0]> = [];
    actions.createAnnotation.mockImplementation(async (cmd) => {
      createdAnnotations.push(cmd);
      return true;
    });
    let savedBody: unknown;
    actions.saveSceneBody.mockImplementation(async (_sceneId, _rev, body) => {
      savedBody = body;
      return true;
    });
    resetStores({ cards: [card("c1", "p1", "苏青")], sceneViews: { "scene-a": viewOf("scene-a", "正文内容") } });
    render(<WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />);

    // 触发 @ 并选择卡片（picker 内的按钮）
    fireEvent.click(screen.getByTestId("stub-mention"));
    const dialog = await screen.findByRole("dialog", { name: "引用卡片搜索" });
    const pickerButton = [...dialog.querySelectorAll("button")].find((button) => button.textContent?.includes("苏青")) as Element;
    fireEvent.click(pickerButton);

    // 填写批注并提交
    const textarea = screen.getByPlaceholderText(/批注内容/);
    fireEvent.change(textarea, { target: { value: "这是一个引用批注" } });
    fireEvent.click(screen.getByText("添加批注"));

    await waitFor(() => expect(actions.createAnnotation).toHaveBeenCalledTimes(1));
    const command = createdAnnotations[0] as { cardId?: string; anchor?: { blockIndex: number; textOffset: number; textLength: number; text?: string } };
    expect(command.cardId).toBe("c1");
    expect(command.anchor?.blockIndex).toBe(0);
    // 快照文本 = 触发 @ 时的真实选区文本（供工作区校验锚点保持/失效）
    expect(command.anchor?.text).toBe("选中文字");

    // 保存正文（触发自动保存路径）验证正文无 @、无卡片名、内容与编辑一致
    const input = screen.getByLabelText("正文") as HTMLTextAreaElement;
    fireEvent.change(input, { target: { value: "正文内容二" } });
    fireEvent.click(screen.getByRole("button", { name: "整章连续" }));
    await waitFor(() => expect(actions.saveSceneBody).toHaveBeenCalled());
    expect(JSON.stringify(savedBody)).toBe(JSON.stringify({ content: [{ type: "paragraph", content: [{ type: "text", text: "正文内容二" }] }] }));
    expect(JSON.stringify(savedBody)).not.toContain("@");
    expect(JSON.stringify(savedBody)).not.toContain("苏青");
  });

  it("无真实选区时提交批注被阻止（不悄悄锚到第一段）", async () => {
    const showToastSpy = vi.fn();
    resetStores({ sceneViews: { "scene-a": viewOf("scene-a", "正文") } });
    useUIStore.setState({ toasts: [], showToast: showToastSpy });
    render(<WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />);

    // 不聚焦正文（无选区上报）直接填写批注提交
    const textarea = screen.getByPlaceholderText(/批注内容/);
    fireEvent.change(textarea, { target: { value: "没有选区的批注" } });
    fireEvent.click(screen.getByText("添加批注"));

    await waitFor(() => {
      const warningCalls = showToastSpy.mock.calls.filter(([t]) => t.tone === "warning");
      expect(warningCalls.length).toBeGreaterThan(0);
    });
    expect(actions.createAnnotation).not.toHaveBeenCalled();
  });

  it("批注表单显示当前选区摘要（选中文字）", async () => {
    resetStores({ sceneViews: { "scene-a": viewOf("scene-a", "正文") } });
    render(<WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />);

    // 点击 @ 按钮模拟选区（Stub onMentionTrigger 带 "选中文字"）
    fireEvent.click(screen.getByTestId("stub-mention"));
    expect(await screen.findByText(/选中「选中文字」/)).toBeDefined();
  });

  it("连续模式下选区正确关联所属 sceneId", async () => {
    resetStores({ sceneViews: { "scene-a": viewOf("scene-a", "A"), "scene-b": viewOf("scene-b", "B") } });
    render(<WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />);
    fireEvent.click(screen.getByRole("button", { name: "整章连续" }));
    await waitFor(() => expect(screen.getAllByTestId("scene-editor")).toHaveLength(2));

    // 聚焦第二个场景的正文 → selection.sceneId 应为 scene-b
    const editors = screen.getAllByTestId("scene-editor");
    expect(editors[1].getAttribute("data-scene-id")).toBe("scene-b");
    const secondInput = screen.getAllByLabelText("正文")[1] as HTMLTextAreaElement;
    fireEvent.focus(secondInput);
    // 选区摘要应该出现在表单（折叠光标描述）
    expect(await screen.findByText(/未选中文字：将锚定当前段落/)).toBeDefined();
  });

  it("批注关联卡片下拉只显示当前项目卡片", async () => {
    resetStores({ cards: [card("c1", "p1", "苏青"), card("foreign", "p9", "异项目角色")], sceneViews: { "scene-a": viewOf("scene-a", "正文") } });
    render(<WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />);
    const select = screen.getByLabelText("关联卡片") as HTMLSelectElement;
    const options = Array.from(select.options).map((option) => option.textContent);
    expect(options).toContain("苏青");
    expect(options.some((text) => text?.includes("异项目角色"))).toBe(false);
  });

  it("批注列表显示锚点文本与待重新定位状态（不改动 workspace 契约）", async () => {
    const annotation: Annotation = {
      id: "a1",
      projectId: "p1",
      sceneId: "scene-a",
      cardId: null,
      anchor: { blockIndex: 0, textOffset: 0, textLength: 2 },
      anchorInvalid: true,
      note: "旧批注",
      status: "open",
      anchoredText: "正文",
      revision: 3,
      createdAt: "",
      updatedAt: ""
    };
    actions.loadAnnotations.mockResolvedValue([annotation]);
    resetStores({ sceneViews: { "scene-a": viewOf("scene-a", "正文") } });
    render(<WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />);

    expect(await screen.findByText("待重新定位")).toBeDefined();
    expect(screen.getByText("旧批注")).toBeDefined();
  });

  function invalidAnnotation(overrides: Partial<Annotation> = {}): Annotation {
    return {
      id: "a1",
      projectId: "p1",
      sceneId: "scene-a",
      cardId: "card-1",
      anchor: { blockIndex: 0, textOffset: 0, textLength: 2 },
      anchorInvalid: true,
      note: "原批注内容",
      status: "open",
      anchoredText: "旧锚点",
      revision: 7,
      createdAt: "",
      updatedAt: "",
      ...overrides
    };
  }

  it("失效批注用当前真实选区二次确认，并携带 revision command 成功重定位", async () => {
    const annotation = invalidAnnotation();
    actions.loadAnnotations.mockResolvedValue([annotation]);
    editorControl.selection = {
      sceneId: "scene-a",
      blockIndex: 2,
      textOffset: 4,
      textLength: 5,
      selectedText: "新的锚点",
      collapsed: false
    };
    render(<WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />);

    fireEvent.click(await screen.findByRole("button", { name: "重新定位" }));
    const dialog = screen.getByRole("dialog", { name: "确认重新定位批注" });
    expect(dialog.textContent).toContain("选中「新的锚点」");
    expect(dialog.textContent).toContain("原锚点：旧锚点");
    expect(actions.reanchorAnnotation).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "确认新锚点" }));
    await waitFor(() => expect(actions.reanchorAnnotation).toHaveBeenCalledWith({
      type: "annotation.reanchor",
      annotationId: "a1",
      baseRevision: 7,
      anchor: { blockIndex: 2, textOffset: 4, textLength: 5, text: "新的锚点" }
    }));
    await waitFor(() => expect(actions.loadAnnotations).toHaveBeenCalledTimes(2));
  });

  it("无非空真实选区时禁止重新定位", async () => {
    actions.loadAnnotations.mockResolvedValue([invalidAnnotation()]);
    const showToast = vi.fn();
    useUIStore.setState({ toasts: [], showToast });
    render(<WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />);

    fireEvent.click(await screen.findByRole("button", { name: "重新定位" }));
    expect(actions.reanchorAnnotation).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog", { name: "确认重新定位批注" })).toBeNull();
    expect(showToast).toHaveBeenCalledWith(expect.objectContaining({ title: "请先选择新锚点" }));
  });

  it("跨场景选区禁止重新定位", async () => {
    actions.loadAnnotations.mockResolvedValue([invalidAnnotation()]);
    editorControl.selection = {
      sceneId: "scene-b", blockIndex: 0, textOffset: 0, textLength: 2, selectedText: "别处", collapsed: false
    };
    const showToast = vi.fn();
    useUIStore.setState({ toasts: [], showToast });
    render(<WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />);

    fireEvent.click(await screen.findByRole("button", { name: "重新定位" }));
    expect(actions.reanchorAnnotation).not.toHaveBeenCalled();
    expect(showToast).toHaveBeenCalledWith(expect.objectContaining({ title: "选区不在当前场景" }));
  });

  it("IME composing 期间禁止重新定位", async () => {
    actions.loadAnnotations.mockResolvedValue([invalidAnnotation()]);
    editorControl.selection = {
      sceneId: "scene-a", blockIndex: 0, textOffset: 0, textLength: 2, selectedText: "新址", collapsed: false
    };
    editorControl.composing = true;
    const showToast = vi.fn();
    useUIStore.setState({ toasts: [], showToast });
    render(<WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />);

    fireEvent.click(await screen.findByRole("button", { name: "重新定位" }));
    expect(actions.reanchorAnnotation).not.toHaveBeenCalled();
    expect(showToast).toHaveBeenCalledWith(expect.objectContaining({ title: "正在输入文字" }));
  });

  it("重定位失败保留确认弹层和已采样的新锚点", async () => {
    actions.loadAnnotations.mockResolvedValue([invalidAnnotation()]);
    actions.reanchorAnnotation.mockResolvedValue(false);
    editorControl.selection = {
      sceneId: "scene-a", blockIndex: 1, textOffset: 3, textLength: 4, selectedText: "保留选择", collapsed: false
    };
    render(<WritingDesk projects={[project]} project={project} navigation={navigation} onSelectProject={() => {}} />);

    fireEvent.click(await screen.findByRole("button", { name: "重新定位" }));
    fireEvent.click(screen.getByRole("button", { name: "确认新锚点" }));

    await waitFor(() => expect(actions.reanchorAnnotation).toHaveBeenCalledTimes(1));
    const dialog = screen.getByRole("dialog", { name: "确认重新定位批注" });
    expect(dialog.textContent).toContain("选中「保留选择」");
    expect(dialog.textContent).toContain("批注可能已被其他操作修改");
    expect(actions.loadAnnotations).toHaveBeenCalledTimes(1);
  });
});
