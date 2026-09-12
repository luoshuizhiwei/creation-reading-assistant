// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, cleanup, screen } from "@testing-library/react";
import { SceneEditor, resolveTypewriterScrollTop, type SceneEditorHandle } from "@/features/creation/editor/SceneEditor";
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

function sceneOf(sceneId: string, text: string): SceneBodyView {
  return { ...viewOf(text), sceneId, title: sceneId } as unknown as SceneBodyView;
}

const noop = () => undefined;

describe("打字机滚动边界", () => {
  it("光标位于首屏上方时不会产生负滚动位置", () => {
    expect(resolveTypewriterScrollTop({ scrollTop: 0, cursorTop: 20, containerTop: 0, containerHeight: 600, scrollHeight: 2400 })).toBe(0);
  });

  it("正文中段将光标定位到可视区中心", () => {
    expect(resolveTypewriterScrollTop({ scrollTop: 400, cursorTop: 500, containerTop: 100, containerHeight: 600, scrollHeight: 2400 })).toBe(500);
  });

  it("光标接近末尾时不会滚过正文底部", () => {
    expect(resolveTypewriterScrollTop({ scrollTop: 1600, cursorTop: 900, containerTop: 100, containerHeight: 600, scrollHeight: 2000 })).toBe(1400);
  });
});

beforeEach(() => {
  vi.clearAllMocks();
});
afterEach(() => cleanup());

describe("SceneEditor 真实选区暴露（Tiptap 渲染）", () => {
  it("底部显示场景目标字数与即时完成比例", async () => {
    await act(async () => {
      render(
        <SceneEditor
          view={viewOf("测试正文")}
          onSave={async () => undefined}
          targetWords={8}
          focusMode={false}
          onToggleFocusMode={noop}
          typewriter={false}
          onToggleTypewriter={noop}
        />
      );
    });
    const progress = screen.getByLabelText("场景目标进度");
    expect(progress.textContent).toContain("4 字");
    expect(progress.textContent).toContain("目标 8 · 50%");
  });

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

describe("SceneEditor 外部命令的 IME 状态", () => {
  it("compositionstart 到 compositionend 之间 handle.isComposing 为 true", async () => {
    let handle: SceneEditorHandle | null = null;
    let container: HTMLElement;
    await act(async () => {
      ({ container } = render(
        <SceneEditor
          ref={(value) => { handle = value; }}
          view={viewOf("测试正文")}
          onSave={async () => undefined}
          focusMode={false}
          onToggleFocusMode={noop}
          typewriter={false}
          onToggleTypewriter={noop}
        />
      ));
    });
    const editor = container!.querySelector(".scene-editor-content") as HTMLElement;

    expect(handle?.isComposing()).toBe(false);
    await act(async () => {
      editor.dispatchEvent(new CompositionEvent("compositionstart", { bubbles: true }));
    });
    expect(handle?.isComposing()).toBe(true);
    await act(async () => {
      editor.dispatchEvent(new CompositionEvent("compositionend", { bubbles: true }));
    });
    expect(handle?.isComposing()).toBe(false);
  });
});

describe("SceneEditor 切换场景（组件保持挂载）", () => {
  /**
   * 回归：写作台在两个「已缓存」场景之间切换时 SceneEditor 不会卸载，
   * 只更换 view.sceneId。此时 useEditor 会销毁旧实例并换入新实例，
   * 旧实例的 commands 已为 null——若同步 effect 仍按旧实例写正文会抛
   * "Cannot read properties of null (reading 'commands')" 并让整个写作台白屏。
   */
  it("同实例内切换 sceneId 不抛错，并把正文同步到新场景", async () => {
    const props = {
      onSave: async () => undefined,
      focusMode: false,
      onToggleFocusMode: noop,
      typewriter: false,
      onToggleTypewriter: noop
    };
    let view!: ReturnType<typeof render>;
    await act(async () => {
      view = render(<SceneEditor view={sceneOf("scene-a", "第一场正文")} {...props} />);
    });
    await act(async () => {});
    expect(view.container.querySelector(".scene-editor-content")?.textContent).toContain("第一场正文");

    // 场景切换：SceneEditor 不卸载，只换 view.sceneId（工作台已缓存两场正文时即如此）
    await act(async () => {
      view.rerender(<SceneEditor view={sceneOf("scene-b", "第三场正文")} {...props} />);
    });
    await act(async () => {});
    expect(view.container.querySelector(".scene-editor-content")?.textContent).toContain("第三场正文");
  });
});
