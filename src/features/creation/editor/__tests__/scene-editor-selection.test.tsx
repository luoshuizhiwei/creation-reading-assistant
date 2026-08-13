// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, cleanup } from "@testing-library/react";
import { SceneEditor, type SceneEditorHandle } from "@/features/creation/editor/SceneEditor";
import { shouldTriggerMention, type SceneSelection } from "@/features/creation/editor/annotation-selection";
import type { SceneBodyView } from "@/types/creation";

function viewOf(text: string): SceneBodyView {
  return {
    sceneId: "scene-a",
    projectId: "p1",
    chapterId: "ch1",
    title: "场景 A",
    body: { type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text }] }] },
    revision: 1,
    updatedAt: ""
  } as unknown as SceneBodyView;
}

const noop = () => undefined;

beforeEach(() => {
  vi.clearAllMocks();
});
afterEach(() => cleanup());

describe("SceneEditor 真实选区暴露（Tiptap 渲染）", () => {
  it("onSelectionChange 上报真实选区（sceneId/块归属/折叠状态正确）", async () => {
    const onSelectionChange = vi.fn();
    await act(async () => {
      render(
        <SceneEditor
          view={viewOf("测试正文")}
          onSave={async () => undefined}
          onSelectionChange={onSelectionChange}
          focusMode={false}
          onToggleFocusMode={noop}
          typewriter={false}
          onToggleTypewriter={noop}
        />
      );
    });
    await act(async () => {});
    // 编辑器初始化后会通过 onSelectionChange 上报一次（view effect）
    expect(onSelectionChange).toHaveBeenCalled();
    const selection = onSelectionChange.mock.calls.at(-1)?.[0] as SceneSelection;
    expect(selection.sceneId).toBe("scene-a");
    expect(selection.collapsed).toBe(true);
    expect(selection.blockIndex).toBe(0);
  });

  it("handle.getSelection 返回与当前编辑器状态一致的选区", async () => {
    let handle: SceneEditorHandle | null = null;
    await act(async () => {
      render(
        <SceneEditor
          ref={(value) => {
            handle = value;
          }}
          view={viewOf("测试正文")}
          onSave={async () => undefined}
          focusMode={false}
          onToggleFocusMode={noop}
          typewriter={false}
          onToggleTypewriter={noop}
        />
      );
    });
    await act(async () => {});
    expect(handle).not.toBeNull();
    const selection = handle?.getSelection();
    expect(selection?.sceneId).toBe("scene-a");
    expect(selection?.collapsed).toBe(true);
  });

  it("未提供 view 时 getSelection 返回 null（不猜测选区）", async () => {
    let handle: SceneEditorHandle | null = null;
    await act(async () => {
      render(
        <SceneEditor
          ref={(value) => {
            handle = value;
          }}
          view={undefined}
          onSave={async () => undefined}
          focusMode={false}
          onToggleFocusMode={noop}
          typewriter={false}
          onToggleTypewriter={noop}
        />
      );
    });
    await act(async () => {});
    expect(handle).not.toBeNull();
    expect(handle?.getSelection()).toBeNull();
  });
});

describe("@ 卡片引用触发（IME 保护）", () => {
  it("非 IME 状态输入含 @ 的文本触发引用命令", () => {
    expect(shouldTriggerMention("@", false)).toBe(true);
    expect(shouldTriggerMention("输入@", false)).toBe(true);
  });

  it("IME composition 期间按 @ 不触发引用面板", () => {
    expect(shouldTriggerMention("@", true)).toBe(false);
    expect(shouldTriggerMention("拼音@", true)).toBe(false);
  });

  it("无 @ 的普通输入不触发", () => {
    expect(shouldTriggerMention("普通文字", false)).toBe(false);
    expect(shouldTriggerMention("", false)).toBe(false);
  });
});
