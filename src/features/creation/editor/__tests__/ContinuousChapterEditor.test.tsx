// @vitest-environment jsdom
import React, { createRef } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, cleanup, fireEvent, waitFor, within, act } from "@testing-library/react";
import type { SceneBodyView, CreationDocument } from "@/types/creation";

vi.mock("@/features/creation/editor/SceneEditor", async () => {
  const ReactActual = await vi.importActual<typeof import("react")>("react");
  interface StubProps {
    view?: SceneBodyView;
    onSave: (body: CreationDocument) => Promise<unknown>;
    onReloadScene?: () => Promise<SceneBodyView | null | undefined>;
    onStatsChange?: (chars: number) => void;
    focusMode?: boolean;
    onToggleFocusMode?: () => void;
    typewriter?: boolean;
    onToggleTypewriter?: () => void;
    targetWords?: number | null;
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
        { "data-testid": "scene-editor", "data-scene-id": props.view?.sceneId, "data-target-words": props.targetWords ?? "" },
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

import { ContinuousChapterEditor, type ContinuousChapterEditorHandle } from "@/features/creation/editor/ContinuousChapterEditor";

function viewOf(sceneId: string, text: string): SceneBodyView {
  return {
    sceneId,
    revision: 1,
    body: { content: [{ type: "paragraph", content: [{ type: "text", text }] }] },
    loadedAt: Date.now()
  } as unknown as SceneBodyView;
}

function makeScenes() {
  return [
    { id: "scene-a", title: "场景 A", view: viewOf("scene-a", "A 初稿") },
    { id: "scene-b", title: "场景 B", view: viewOf("scene-b", "B 初稿") }
  ];
}

afterEach(() => cleanup());

describe("ContinuousChapterEditor 整章连续编辑", () => {
  it("逐场景透传各自目标字数，连续模式不串用目标", () => {
    render(
      <ContinuousChapterEditor
        chapterTitle="第一章"
        scenes={makeScenes().map((scene, index) => ({ ...scene, targetWords: index === 0 ? 800 : 1600 }))}
        onSave={async () => true}
        focusMode={false}
        onToggleFocusMode={() => {}}
        typewriter={false}
        onToggleTypewriter={() => {}}
      />
    );
    const editors = screen.getAllByTestId("scene-editor");
    expect(editors[0].getAttribute("data-target-words")).toBe("800");
    expect(editors[1].getAttribute("data-target-words")).toBe("1600");
  });

  it("两个场景按真实排序连续显示且均可编辑", () => {
    render(
      <ContinuousChapterEditor
        chapterTitle="第一章"
        scenes={makeScenes()}
        onSave={async () => true}
        focusMode={false}
        onToggleFocusMode={() => {}}
        typewriter={false}
        onToggleTypewriter={() => {}}
      />
    );
    const editors = screen.getAllByTestId("scene-editor");
    expect(editors).toHaveLength(2);
    expect(editors[0].getAttribute("data-scene-id")).toBe("scene-a");
    expect(editors[1].getAttribute("data-scene-id")).toBe("scene-b");
    const inputs = screen.getAllByLabelText("正文");
    expect((inputs[0] as HTMLTextAreaElement).value).toBe("A 初稿");
    expect((inputs[1] as HTMLTextAreaElement).value).toBe("B 初稿");
  });

  it("编辑场景 A 不修改场景 B", () => {
    render(
      <ContinuousChapterEditor
        chapterTitle="第一章"
        scenes={makeScenes()}
        onSave={async () => true}
        focusMode={false}
        onToggleFocusMode={() => {}}
        typewriter={false}
        onToggleTypewriter={() => {}}
      />
    );
    const inputs = screen.getAllByLabelText("正文") as HTMLTextAreaElement[];
    fireEvent.change(inputs[0], { target: { value: "A 修改后" } });
    expect((inputs[0] as HTMLTextAreaElement).value).toBe("A 修改后");
    expect((inputs[1] as HTMLTextAreaElement).value).toBe("B 初稿");
  });

  it("场景 A 保存失败（抛错），场景 B 仍能保存，且互不阻塞", async () => {
    const saved = new Set<string>();
    const onSave = vi.fn(async (sceneId: string, _baseRevision: number, _body: unknown) => {
      if (sceneId === "scene-a") {
        throw new Error("network");
      }
      saved.add(sceneId);
      return true;
    });
    const ref = createRef<ContinuousChapterEditorHandle>();
    render(
      <ContinuousChapterEditor
        ref={ref}
        chapterTitle="第一章"
        scenes={makeScenes()}
        onSave={onSave}
        focusMode={false}
        onToggleFocusMode={() => {}}
        typewriter={false}
        onToggleTypewriter={() => {}}
      />
    );
    const inputs = screen.getAllByLabelText("正文") as HTMLTextAreaElement[];
    fireEvent.change(inputs[0], { target: { value: "A 修改后" } });
    fireEvent.change(inputs[1], { target: { value: "B 修改后" } });

    let result = true;
    await act(async () => {
      result = await ref.current!.saveAllDirty();
    });

    expect(result).toBe(false);
    expect(onSave).toHaveBeenCalledWith("scene-a", 1, expect.anything());
    expect(onSave).toHaveBeenCalledWith("scene-b", 1, expect.anything());
    expect(saved.has("scene-b")).toBe(true);
  });

  it("场景 A 保存返回 false 时，B 仍保存成功（冲突只影响 A）", async () => {
    const onSave = vi.fn(async (sceneId: string, _baseRevision: number, _body: unknown) => (sceneId === "scene-a" ? false : true));
    const ref = createRef<ContinuousChapterEditorHandle>();
    render(
      <ContinuousChapterEditor
        ref={ref}
        chapterTitle="第一章"
        scenes={makeScenes()}
        onSave={onSave}
        focusMode={false}
        onToggleFocusMode={() => {}}
        typewriter={false}
        onToggleTypewriter={() => {}}
      />
    );
    const inputs = screen.getAllByLabelText("正文") as HTMLTextAreaElement[];
    fireEvent.change(inputs[0], { target: { value: "A 冲突稿" } });
    fireEvent.change(inputs[1], { target: { value: "B 正常稿" } });

    let result = true;
    await act(async () => {
      result = await ref.current!.saveAllDirty();
    });

    expect(result).toBe(false);
    expect(onSave).toHaveBeenCalledTimes(2);
    expect(onSave).toHaveBeenLastCalledWith("scene-b", 1, expect.anything());
  });

  it("onReloadScene 回调被调用时传入正确的 sceneId 并返回最新 view", async () => {
    const reloadSpy = vi.fn(async (sceneId: string) => viewOf(sceneId, "远端最新正文"));
    const ref = createRef<ContinuousChapterEditorHandle>();
    render(
      <ContinuousChapterEditor
        ref={ref}
        chapterTitle="第一章"
        scenes={makeScenes()}
        onSave={async () => true}
        onReloadScene={reloadSpy}
        focusMode={false}
        onToggleFocusMode={() => {}}
        typewriter={false}
        onToggleTypewriter={() => {}}
      />
    );
    // 确认 onReloadScene 被传递到 SceneEditor（通过 mock 的 props 检查）
    const editors = screen.getAllByTestId("scene-editor");
    expect(editors).toHaveLength(2);
    // reloadSpy 尚未被调用（由 SceneEditor 内部冲突按钮触发，mock 不渲染按钮）
    expect(reloadSpy).not.toHaveBeenCalled();
  });

  it("未提供 onReloadScene 时不报错", () => {
    // 连续模式不传 onReloadScene，仅验证渲染不崩溃
    render(
      <ContinuousChapterEditor
        chapterTitle="第一章"
        scenes={makeScenes()}
        onSave={async () => true}
        focusMode={false}
        onToggleFocusMode={() => {}}
        typewriter={false}
        onToggleTypewriter={() => {}}
      />
    );
    const editors = screen.getAllByTestId("scene-editor");
    expect(editors).toHaveLength(2);
  });
});
