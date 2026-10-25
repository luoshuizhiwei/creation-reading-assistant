/**
 * BD-170 源码级护栏：TXT 目录编辑里的「从此处拆分」是开关态（选中一个拆分点），
 * 选中与否此前只有 bg-copper 底色。TxtMarkdownReader 依赖主进程文件读取，
 * 现有测试都没有渲染它，故在源码层钉住 aria-pressed 绑定。放在 node 环境。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

describe("TxtMarkdownReader 章节目录编辑的拆分开关", () => {
  it("「从此处拆分」按钮用 aria-pressed 表达选中的拆分点", () => {
    const raw = readFileSync(fileURLToPath(new URL("../TxtMarkdownReader.tsx", import.meta.url)), "utf8");
    const src = raw.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/[^\n]*/g, "");
    expect(src).toMatch(/<button\b[^>]*aria-pressed=\{splitIndex === i\}[\s\S]{0,400}?从此处拆分/);
  });
});
