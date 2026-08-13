import type { Editor } from "@tiptap/react";

/**
 * 只读选区 interface：从 Tiptap/ProseMirror 真实位置解析，不做 DOM 文本猜测。
 *
 * - blockIndex：选区起点所属的顶层块索引（doc.content 下标）；
 * - textOffset：块内字符偏移（与 AnnotationAnchor 的 textOffset 单位一致：
 *   0 = 块内第一个字符）；
 * - textLength：选区长度（跨多个 text node 时按块内拼接文本累计；
 *   跨块选区只保留起点块内的部分）；
 * - selectedText：选中的真实文本；
 * - collapsed：折叠光标（未选中文字）时为 true。
 */
export interface SceneSelection {
  sceneId: string;
  blockIndex: number;
  textOffset: number;
  textLength: number;
  selectedText: string;
  collapsed: boolean;
}

/** 光标位置（ProseMirror pos）归属的块：块尾边界（内容结束后）归当前块（段尾光标仍属于该段）。 */
function blockAtPosition(doc: { childCount: number; child(index: number): { nodeSize: number; textContent: string } }, pos: number): { blockIndex: number; textOffset: number } {
  // ProseMirror 顶层：doc 头占 pos 0..1；块节点本身不占内容位置，块内首字符即块 pos。
  let cursor = 1;
  for (let index = 0; index < doc.childCount; index += 1) {
    const node = doc.child(index);
    const contentStart = cursor;
    const contentEnd = cursor + node.textContent.length;
    const nodeEnd = cursor + node.nodeSize;
    if (pos >= contentStart && pos < contentEnd) {
      return { blockIndex: index, textOffset: pos - contentStart };
    }
    if (pos >= contentStart && pos < nodeEnd) {
      // 块内容结束后的块尾边界（折叠光标在段尾）。
      return { blockIndex: index, textOffset: node.textContent.length };
    }
    cursor = nodeEnd;
  }
  const last = Math.max(0, doc.childCount - 1);
  return { blockIndex: last, textOffset: doc.child(last)?.textContent.length ?? 0 };
}

/**
 * 从编辑器当前选区解析只读选区。
 * - 折叠光标：collapsed=true、textLength=1（锚定当前段落）、selectedText=""；
 *   调用方（批注表单）必须明确展示「锚定当前段落」，不得悄悄锚到第一段。
 * - 非空选区：按起点块内偏移解析；跨块时只保留起点块内的部分（textLength 至少 1）。
 */
export function resolveSceneSelection(editor: Editor | null, sceneId: string): SceneSelection | null {
  if (!editor || editor.isDestroyed) return null;
  const { state } = editor;
  const { selection } = state;
  const doc = state.doc;
  const from = selection.$from.pos;
  const to = selection.$to.pos;

  if (selection.empty) {
    const { blockIndex, textOffset } = blockAtPosition(doc, from);
    return {
      sceneId,
      blockIndex,
      textOffset,
      textLength: 1,
      selectedText: "",
      collapsed: true
    };
  }

  const { blockIndex, textOffset } = blockAtPosition(doc, from);
  // 块内容 exclusive 结束位置 = 块 pos + 文本长度（块节点尾不占内容位置）。
  const contentEnd = from - textOffset + doc.child(blockIndex).textContent.length;
  // ProseMirror 非空选区：selection.to（$to.pos）是 exclusive 结束位置。
  const clampedTo = Math.min(to, contentEnd);
  const textLength = Math.max(1, clampedTo - from);
  const selectedText = doc.textBetween(from, clampedTo, "\n");
  return {
    sceneId,
    blockIndex,
    textOffset,
    textLength,
    selectedText,
    collapsed: false
  };
}

/** 选区摘要：批注表单展示用。 */
export function describeSelection(selection: SceneSelection | null): string {
  if (!selection) return "尚未在正文中定位：请先把光标放进正文或选中文字。";
  if (selection.collapsed) {
    return `未选中文字：将锚定当前段落（第 ${selection.blockIndex + 1} 段）。`;
  }
  const preview = selection.selectedText.length > 30 ? `${selection.selectedText.slice(0, 30)}…` : selection.selectedText;
  return `选中「${preview}」共 ${selection.selectedText.length} 字（第 ${selection.blockIndex + 1} 段）。`;
}

/**
 * 是否触发 @ 卡片引用命令。
 * IME 组合输入期间返回 false（不弹面板、不破坏组合输入）；
 * 非 IME 状态下输入文本含 @ 才触发（由 handleTextInput 拦截，@ 不写入正文）。
 */
export function shouldTriggerMention(inputText: string, composing: boolean): boolean {
  if (composing) return false;
  return inputText.includes("@");
}
