import { describe, expect, it } from "vitest";
import { countSceneBodyStats } from "../../../../electron/main/creation-workspace/scene-stats";

function doc(text: string) {
  return JSON.stringify({
    type: "doc",
    content: [{ type: "paragraph", content: [{ type: "text", text }] }]
  });
}

describe("countSceneBodyStats 权威口径（workspace 与视觉 seed 共用）", () => {
  it("汉字与中文标点：han 只计汉字，punct 计标点，nonWhitespace 计两者", () => {
    const stats = countSceneBodyStats(doc("你好，世界！"));
    expect(stats).toEqual({ han: 4, punct: 2, nonWhitespace: 6 });
  });

  it("空白（半角/全角/换行/制表）全部排除且不计 punct", () => {
    const stats = countSceneBodyStats(doc(" 你好 \u3000\t\n世界！ "));
    expect(stats).toEqual({ han: 4, punct: 1, nonWhitespace: 5 });
  });

  it("ASCII 标点与拉丁字母计入 nonWhitespace；ASCII 标点计入 punct", () => {
    const stats = countSceneBodyStats(doc("Hello, World!"));
    expect(stats.han).toBe(0);
    expect(stats.punct).toBe(2);
    expect(stats.nonWhitespace).toBe(12);
  });

  it("符号（& # 等）计入 punct 与 nonWhitespace", () => {
    const stats = countSceneBodyStats(doc("第1章 & 第2章 #3"));
    expect(stats.punct).toBe(2);
    expect(stats.nonWhitespace).toBe(9);
  });

  it("emoji 计入 punct 与 nonWhitespace（按 code point 计数）", () => {
    const stats = countSceneBodyStats(doc("你好🎉"));
    expect(stats.han).toBe(2);
    expect(stats.punct).toBe(1);
    expect(stats.nonWhitespace).toBe(3);
  });

  it("嵌套 mark 的文本同样计入", () => {
    const bodyJson = JSON.stringify({
      type: "doc",
      content: [
        { type: "paragraph", content: [{ type: "text", text: "加粗" }, { type: "text", marks: [{ type: "bold" }], text: "内容。" }] }
      ]
    });
    expect(countSceneBodyStats(bodyJson)).toEqual({ han: 4, punct: 1, nonWhitespace: 5 });
  });

  it("非法 JSON 返回全零", () => {
    expect(countSceneBodyStats("not-json")).toEqual({ han: 0, punct: 0, nonWhitespace: 0 });
  });
});
