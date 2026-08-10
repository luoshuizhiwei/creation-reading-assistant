import { describe, expect, it } from "vitest";
import {
  inspectScenePaste,
  plainTextToCreationDocument,
  SCENE_PASTE_HTML_CHAR_LIMIT,
  SCENE_PASTE_TEXT_CHAR_LIMIT
} from "@/features/creation/editor/paste-clean";
import { validateCreationDocument } from "@/features/creation/editor/editor-schema";

describe("inspectScenePaste", () => {
  it("普通短 HTML 直接通过，无原因且提供纯文本回退", () => {
    const result = inspectScenePaste({
      html: "<p>普通<strong>加粗</strong>段落</p>",
      text: "普通加粗段落"
    });
    expect(result.verdict).toBe("direct");
    expect(result.reason).toBeNull();
    expect(result.hasForbiddenContent).toBe(false);
    expect(result.oversized).toBe(false);
    expect(result.cleanedText).toBe("普通加粗段落");
  });

  it.each([
    ["table", "<table><tr><td>x</td></tr></table>"],
    ["img", "<img src='x.png'>"],
    ["picture", "<picture><source></picture>"],
    ["pre", "<pre>code</pre>"],
    ["code", "<p><code>inline</code></p>"],
    ["iframe", "<iframe src='https://example.com'></iframe>"],
    ["video", "<video src='a.mp4'></video>"],
    ["audio", "<audio src='a.mp3'></audio>"],
    ["object", "<object data='x'></object>"],
    ["embed", "<embed src='x'>"]
  ])("包含 %s 标签时进入 preview 并给出原因", (_, html) => {
    const result = inspectScenePaste({ html, text: "纯文本" });
    expect(result.verdict).toBe("preview");
    expect(result.reason).toContain("不支持的格式");
    expect(result.hasForbiddenContent).toBe(true);
  });

  it("闭合标签也触发 preview", () => {
    const result = inspectScenePaste({ html: "x</table>", text: "x" });
    expect(result.verdict).toBe("preview");
  });

  it("HTML 或文本超过阈值时进入 preview", () => {
    const bigHtml = "<p>" + "字".repeat(SCENE_PASTE_HTML_CHAR_LIMIT) + "</p>";
    const htmlResult = inspectScenePaste({ html: bigHtml, text: "短" });
    expect(htmlResult.verdict).toBe("preview");
    expect(htmlResult.oversized).toBe(true);

    const bigText = "字".repeat(SCENE_PASTE_TEXT_CHAR_LIMIT + 1);
    const textResult = inspectScenePaste({ html: "", text: bigText });
    expect(textResult.verdict).toBe("preview");
    expect(textResult.oversized).toBe(true);
  });

  it("清洗预览给出纯文本 cleanedText", () => {
    const result = inspectScenePaste({
      html: "<table><tr><td>甲</td><td>乙</td></tr></table>",
      text: "甲 乙"
    });
    expect(result.cleanedText).toContain("甲");
    expect(result.cleanedText).toContain("乙");
  });
});

describe("plainTextToCreationDocument", () => {
  it("按空行切段，每段成为一个 paragraph", () => {
    const document = plainTextToCreationDocument("第一段\n第二行\n\n第二段");
    expect(document).toEqual({
      type: "doc",
      content: [
        { type: "paragraph", content: [{ type: "text", text: "第一段 第二行" }] },
        { type: "paragraph", content: [{ type: "text", text: "第二段" }] }
      ]
    });
    expect(validateCreationDocument(document)).toBe(true);
  });

  it("空输入也永远产出至少一个空 paragraph", () => {
    const empty = plainTextToCreationDocument("");
    expect(empty.content).toHaveLength(1);
    expect(empty.content[0]).toEqual({ type: "paragraph" });

    const blanks = plainTextToCreationDocument("\n\n  \n\n");
    expect(blanks.content).toHaveLength(1);
  });

  it("单个换行不切段，只折叠为空格", () => {
    const document = plainTextToCreationDocument("甲\n乙");
    expect(document.content).toHaveLength(1);
    expect(document.content[0]).toEqual({ type: "paragraph", content: [{ type: "text", text: "甲 乙" }] });
  });

  it("兼容 CRLF 换行", () => {
    const document = plainTextToCreationDocument("甲\r\n\r\n乙");
    expect(document.content).toHaveLength(2);
  });
});
