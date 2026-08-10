import { describe, expect, it } from "vitest";
import { getSchema } from "@tiptap/core";
import type { CreationDocument } from "@/types/creation";
import {
  createNovelEditorExtensions,
  emptyDocument,
  NOVEL_MARK_NAMES,
  NOVEL_NODE_NAMES,
  validateCreationDocument
} from "@/features/creation/editor/editor-schema";

function validDoc(): CreationDocument {
  return {
    type: "doc",
    content: [
      { type: "paragraph", content: [{ type: "text", text: "第一段" }] },
      { type: "sceneBreak" },
      {
        type: "quoteLetter",
        content: [{ type: "text", text: "引文", marks: [{ type: "bold" }] }]
      },
      { type: "centeredText", content: [{ type: "text", text: "居中" }] },
      {
        type: "authorNote",
        content: [{ type: "text", text: "作者注", marks: [{ type: "bold" }, { type: "italic" }] }]
      },
      { type: "paragraph" }
    ]
  };
}

describe("createNovelEditorExtensions", () => {
  it("严格限定节点与标记集合", () => {
    const schema = getSchema(createNovelEditorExtensions());
    expect(Object.keys(schema.nodes)).toEqual([...NOVEL_NODE_NAMES]);
    expect(Object.keys(schema.marks)).toEqual([...NOVEL_MARK_NAMES]);
  });

  it("sceneBreak 是 atom 叶子节点", () => {
    const schema = getSchema(createNovelEditorExtensions());
    expect(schema.nodes.sceneBreak.isAtom).toBe(true);
    expect(schema.nodes.sceneBreak.isLeaf).toBe(true);
  });

  it("JSON 文档经 schema 往返保持一致", () => {
    const schema = getSchema(createNovelEditorExtensions());
    const node = schema.nodeFromJSON(validDoc());
    expect(node.toJSON()).toEqual(validDoc());
  });

  it("拒绝 schema 外的表格节点", () => {
    const schema = getSchema(createNovelEditorExtensions());
    expect(() =>
      schema.nodeFromJSON({ type: "doc", content: [{ type: "table", content: [] }] })
    ).toThrow();
  });

  it("history 扩展携带撤销/重做快捷键与 prosemirror 插件", () => {
    const extensions = createNovelEditorExtensions();
    const history = extensions.find((extension) => extension.name === "novelHistory");
    expect(history).toBeDefined();

    const config = (history as { config: Record<string, unknown> }).config;
    const shortcuts = (
      config.addKeyboardShortcuts as () => Record<string, unknown>
    )();
    expect(Object.keys(shortcuts)).toEqual(["Mod-z", "Mod-y", "Shift-Mod-z"]);

    const plugins = (config.addProseMirrorPlugins as () => unknown[])();
    expect(plugins).toHaveLength(1);
  });
});

describe("validateCreationDocument", () => {
  it("接受五种块、sceneBreak 与 bold/italic 标记", () => {
    expect(validateCreationDocument(validDoc())).toBe(true);
    expect(validateCreationDocument(emptyDocument())).toBe(true);
  });

  it.each([
    ["table", { type: "table", content: [] }],
    ["image", { type: "image", attrs: { src: "x.png" } }],
    ["codeBlock", { type: "codeBlock", content: [{ type: "text", text: "x" }] }],
    ["iframe", { type: "iframe", attrs: { src: "https://example.com" } }],
    ["heading", { type: "heading", attrs: { level: 2 }, content: [{ type: "text", text: "x" }] }]
  ])("拒绝不支持的块节点 %s", (_name, block) => {
    expect(validateCreationDocument({ type: "doc", content: [block] })).toBe(false);
  });

  it("拒绝不支持的标记", () => {
    const withUnknownMark: CreationDocument = {
      type: "doc",
      content: [{ type: "paragraph", content: [{ type: "text", text: "x", marks: [{ type: "link" }] }] }]
    };
    expect(validateCreationDocument(withUnknownMark)).toBe(false);
  });

  it("拒绝非对象、非 doc、非数组 content", () => {
    expect(validateCreationDocument(null)).toBe(false);
    expect(validateCreationDocument("doc")).toBe(false);
    expect(validateCreationDocument({ type: "doc" })).toBe(false);
    expect(validateCreationDocument({ type: "doc", content: "nope" })).toBe(false);
    expect(validateCreationDocument({ type: "paragraph" })).toBe(false);
  });
});
