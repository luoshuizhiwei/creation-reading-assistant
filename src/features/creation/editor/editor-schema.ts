import { Extension, Mark, Node, type Extensions } from "@tiptap/core";
import { history, redo, undo } from "@tiptap/pm/history";
import type { DOMOutputSpec } from "@tiptap/pm/model";
import type { CreationDocument } from "@/types/creation";

export const NOVEL_BLOCK_NODE_NAMES = [
  "paragraph",
  "quoteLetter",
  "centeredText",
  "authorNote",
  "sceneBreak"
] as const;

export type NovelBlockNodeName = (typeof NOVEL_BLOCK_NODE_NAMES)[number];

export const NOVEL_NODE_NAMES = ["doc", ...NOVEL_BLOCK_NODE_NAMES, "text"] as const;

export type NovelNodeName = (typeof NOVEL_NODE_NAMES)[number];

export const NOVEL_MARK_NAMES = ["bold", "italic"] as const;

export type NovelMarkName = (typeof NOVEL_MARK_NAMES)[number];

const INLINE_CONTAINING_BLOCKS = new Set<string>([
  "paragraph",
  "quoteLetter",
  "centeredText",
  "authorNote"
]);

const Doc = Node.create({
  name: "doc",
  topNode: true,
  content: "block+",
  parseHTML: () => [{ tag: "div[data-novel-document]" }],
  renderHTML: (): DOMOutputSpec => ["div", { "data-novel-document": "" }, 0]
});

const Paragraph = Node.create({
  name: "paragraph",
  group: "block",
  content: "inline*",
  parseHTML: () => [{ tag: "p" }],
  renderHTML: (): DOMOutputSpec => ["p", 0]
});

const QuoteLetter = Node.create({
  name: "quoteLetter",
  group: "block",
  content: "inline*",
  parseHTML: () => [{ tag: "blockquote" }],
  renderHTML: (): DOMOutputSpec => ["blockquote", { class: "quote-letter" }, 0]
});

const CenteredText = Node.create({
  name: "centeredText",
  group: "block",
  content: "inline*",
  parseHTML: () => [{ tag: "div.centered-text" }],
  renderHTML: (): DOMOutputSpec => ["div", { class: "centered-text" }, 0]
});

const AuthorNote = Node.create({
  name: "authorNote",
  group: "block",
  content: "inline*",
  parseHTML: () => [{ tag: "div.author-note" }],
  renderHTML: (): DOMOutputSpec => ["div", { class: "author-note" }, 0]
});

const SceneBreak = Node.create({
  name: "sceneBreak",
  group: "block",
  atom: true,
  selectable: true,
  parseHTML: () => [{ tag: "hr.scene-break" }],
  renderHTML: (): DOMOutputSpec => ["hr", { class: "scene-break" }]
});

const Text = Node.create({
  name: "text",
  group: "inline"
});

const Bold = Mark.create({
  name: "bold",
  parseHTML: () => [{ tag: "strong" }, { tag: "b" }],
  renderHTML: (): DOMOutputSpec => ["strong", 0]
});

const Italic = Mark.create({
  name: "italic",
  parseHTML: () => [{ tag: "em" }, { tag: "i" }],
  renderHTML: (): DOMOutputSpec => ["em", 0]
});

const NovelHistory = Extension.create({
  name: "novelHistory",
  addProseMirrorPlugins() {
    return [history()];
  },
  addKeyboardShortcuts() {
    return {
      "Mod-z": () => undo(this.editor.state, this.editor.view.dispatch),
      "Mod-y": () => redo(this.editor.state, this.editor.view.dispatch),
      "Shift-Mod-z": () => redo(this.editor.state, this.editor.view.dispatch)
    };
  }
});

export function createNovelEditorExtensions(): Extensions {
  return [
    Doc,
    Paragraph,
    QuoteLetter,
    CenteredText,
    AuthorNote,
    SceneBreak,
    Text,
    Bold,
    Italic,
    NovelHistory
  ];
}

export function emptyDocument(): CreationDocument {
  return { type: "doc", content: [{ type: "paragraph" }] };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function validateCreationDocument(value: unknown): value is CreationDocument {
  if (!isRecord(value) || value.type !== "doc" || !Array.isArray(value.content)) {
    return false;
  }
  for (const block of value.content) {
    if (!isRecord(block) || typeof block.type !== "string") {
      return false;
    }
    if (block.type === "sceneBreak") {
      if (block.content !== undefined) {
        return false;
      }
      continue;
    }
    if (!INLINE_CONTAINING_BLOCKS.has(block.type)) {
      return false;
    }
    if (block.content === undefined) {
      continue;
    }
    if (!Array.isArray(block.content)) {
      return false;
    }
    for (const inline of block.content) {
      if (!isRecord(inline) || inline.type !== "text" || typeof inline.text !== "string") {
        return false;
      }
      if (inline.marks === undefined) {
        continue;
      }
      if (!Array.isArray(inline.marks)) {
        return false;
      }
      for (const mark of inline.marks) {
        if (!isRecord(mark) || typeof mark.type !== "string") {
          return false;
        }
        if (!NOVEL_MARK_NAMES.includes(mark.type as NovelMarkName)) {
          return false;
        }
      }
    }
  }
  return true;
}
