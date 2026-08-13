// @vitest-environment jsdom
import { afterEach, describe, expect, it } from "vitest";
import { Editor } from "@tiptap/core";
import { createNovelEditorExtensions, emptyDocument } from "@/features/creation/editor/editor-schema";
import { resolveSceneSelection } from "@/features/creation/editor/annotation-selection";

function makeEditor(content: unknown) {
  return new Editor({
    extensions: createNovelEditorExtensions(),
    content
  });
}

afterEach(() => {
  // 清理所有编辑器实例（jsdom 下避免残留）
  for (const instance of [...window.editorInstances ?? []]) {
    instance.destroy();
  }
});

const docWithMarks = {
  type: "doc",
  content: [
    {
      type: "paragraph",
      content: [
        { type: "text", text: "你好" },
        { type: "text", text: "世界", marks: [{ type: "bold" }] },
        { type: "text", text: "，测试段落。" }
      ]
    },
    { type: "paragraph", content: [{ type: "text", text: "第二段内容" }] }
  ]
};

describe("resolveSceneSelection：从 ProseMirror 真实位置解析选区", () => {
  it("跨多个文字节点（bold mark 切分）的真实选区偏移", () => {
    const editor = makeEditor(docWithMarks);
    const { state } = editor;
    // PM 位置：块 0 从 pos 1 开始，块节点不占内容位置（"你"=1,"好"=2,"世"=3,"界"=4）。
    // 选中 "好世"（create 的 to 为 exclusive）。
    const { TextSelection } = require("@tiptap/pm/state");
    editor.view.updateState(editor.state.apply(state.tr.setSelection(TextSelection.create(state.doc, 2, 4))));

    const selection = resolveSceneSelection(editor, "scene-1");
    expect(selection).not.toBeNull();
    expect(selection?.sceneId).toBe("scene-1");
    expect(selection?.blockIndex).toBe(0);
    expect(selection?.textOffset).toBe(1);
    expect(selection?.textLength).toBe(2);
    expect(selection?.selectedText).toBe("好世");
    expect(selection?.collapsed).toBe(false);
    editor.destroy();
  });

  it("选区属于正确的 blockIndex（第二段）", () => {
    const editor = makeEditor(docWithMarks);
    const { state } = editor;
    // 第二段块 pos = 1 + 第一段 nodeSize(12) = 13；首字符 "第" 即 pos 13
    const secondBlockStart = 1 + state.doc.child(0).nodeSize;
    const { TextSelection } = require("@tiptap/pm/state");
    editor.view.updateState(editor.state.apply(state.tr.setSelection(TextSelection.create(state.doc, secondBlockStart, secondBlockStart + 3))));

    const selection = resolveSceneSelection(editor, "scene-2");
    expect(selection?.blockIndex).toBe(1);
    expect(selection?.textOffset).toBe(0);
    expect(selection?.textLength).toBe(3);
    expect(selection?.selectedText).toBe("第二段");
    editor.destroy();
  });

  it("折叠光标：collapsed=true、selectedText 为空、锚定当前段落", () => {
    const editor = makeEditor(docWithMarks);
    const { state } = editor;
    // 光标放在第一段中间（"好" 位置，pos 2）
    const { TextSelection } = require("@tiptap/pm/state");
    editor.view.updateState(editor.state.apply(state.tr.setSelection(TextSelection.create(state.doc, 2))));

    const selection = resolveSceneSelection(editor, "scene-3");
    expect(selection?.collapsed).toBe(true);
    expect(selection?.selectedText).toBe("");
    expect(selection?.blockIndex).toBe(0);
    expect(selection?.textOffset).toBe(1);
    expect(selection?.textLength).toBe(1);
    editor.destroy();
  });

  it("光标在段尾（块内容结束后）仍归属当前段，而不是悄悄锚到下一段", () => {
    const editor = makeEditor(docWithMarks);
    const { state } = editor;
    // 第一段内容 [1, 11)：段尾光标 pos = 11
    const boundaryPos = 11;
    const { TextSelection } = require("@tiptap/pm/state");
    editor.view.updateState(editor.state.apply(state.tr.setSelection(TextSelection.create(state.doc, boundaryPos))));

    const selection = resolveSceneSelection(editor, "scene-4");
    expect(selection?.blockIndex).toBe(0);
    expect(selection?.textOffset).toBe(10);
    editor.destroy();
  });

  it("跨块选区只保留起点块内的部分（不产生越界锚点）", () => {
    const editor = makeEditor(docWithMarks);
    const { state } = editor;
    // 从第一段 "好"（pos 2）跨到第二段末尾
    const to = state.doc.content.size - 1;
    const { TextSelection } = require("@tiptap/pm/state");
    editor.view.updateState(editor.state.apply(state.tr.setSelection(TextSelection.create(state.doc, 2, to))));

    const selection = resolveSceneSelection(editor, "scene-5");
    expect(selection?.blockIndex).toBe(0);
    // 只保留第一段内部分：从 pos 2 到第一段内容结束（exclusive 11）
    expect(selection?.textLength).toBe(9);
    expect(selection?.selectedText).toBe("好世界，测试段落。");
    editor.destroy();
  });

  it("空编辑器返回 null", () => {
    expect(resolveSceneSelection(null, "scene-x")).toBeNull();
  });

  it("空文档默认段落上折叠光标可解析", () => {
    const editor = makeEditor(emptyDocument());
    const { state } = editor;
    const { TextSelection } = require("@tiptap/pm/state");
    editor.view.updateState(editor.state.apply(state.tr.setSelection(TextSelection.create(state.doc, 1))));
    const selection = resolveSceneSelection(editor, "scene-empty");
    expect(selection?.collapsed).toBe(true);
    expect(selection?.blockIndex).toBe(0);
    editor.destroy();
  });
});
