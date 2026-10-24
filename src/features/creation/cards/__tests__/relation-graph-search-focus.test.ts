// @vitest-environment node
/**
 * BD-126 护栏：关系图搜索框的可见焦点指示。
 *
 * 该输入框自身 `outline: none`，而这条规则落在按需加载的 CardsPage 样式块里，文档序
 * 晚于全局 `input:focus-visible`（同特异度），实测键盘 Tab 聚焦时 outline-width 为 0、
 * 无 box-shadow、边框 0——WCAG 2.4.7 焦点不可见。修法是把指示挂到容器 `.relation-graph-search`
 * 的 `:focus-within` 上（同 .paper-stepper）。此测试钉住这条规则存在且只用令牌着色，
 * 顺带钉住容器/输入框的配对关系，防止将来改回「只给输入框 outline:none」。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

const path = fileURLToPath(new URL("../relation-graph.css", import.meta.url));
const raw = readFileSync(path, "utf8");
const css = raw.replace(/\/\*[\s\S]*?\*\//g, "");

const blockOf = (selector: string) => {
  const m = new RegExp(`${selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\s*\\{([^}]*)\\}`).exec(css);
  return m ? m[1] : null;
};

describe("BD-126 关系图搜索框聚焦必须可见", () => {
  it("容器 :focus-within 给出边框色 + 3px 纯环", () => {
    const block = blockOf(".relation-graph-search:focus-within");
    expect(block, "缺 .relation-graph-search:focus-within 规则").not.toBeNull();
    expect(block).toMatch(/border-color:\s*var\(--accent-spine\)/);
    expect(block).toMatch(/box-shadow:\s*0 0 0 3px var\(--action-tint\)/);
  });

  it("焦点环只用令牌，不写死色", () => {
    const block = blockOf(".relation-graph-search:focus-within");
    expect(block).not.toMatch(/#[0-9a-fA-F]{3,6}\b|rgba?\(/);
  });

  it("输入框仍是无边框透明框（指示由容器承担）", () => {
    const block = blockOf(".relation-graph-search input");
    expect(block).toMatch(/outline:\s*none/);
    expect(block).toMatch(/border:\s*0/);
  });
});
