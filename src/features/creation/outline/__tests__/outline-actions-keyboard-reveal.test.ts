// @vitest-environment node
/**
 * BD-243 护栏（焦点可见 2.4.7 交叉类「键盘可达的控件不得靠 hover 才显形」）：
 * 大纲行内动作簇（卷/章/场景三处 `.outline-node-actions`）平时整簇 `opacity: 0`，
 * 原本**只有 :hover 一条显形路**，而簇里的控件全在 Tab 序列内——opacity 不摘 tabindex，
 * 祖先透明也不影响 document.activeElement。
 *
 * 运行时实测（/tmp/bd243-p3.log，1360×860 真窗、真键盘、鼠标停在 2,2 谁都没碰）：
 *   大纲第 10 步落进 button[aria-label="在卷「正文」新建章节"]，本体 computed
 *   outline="solid 2px"（常驻网据此判绿，是它的头一个假绿），而祖先
 *   span.outline-node-actions=op0，链上乘出来的绘制不透明度 0.000；
 *   「本体∪父」裁剪区在真键盘聚焦 vs 离焦之间 changed=0 / maxdelta=0、离焦基线逐字节稳定，
 *   同一区域 hover vs 离焦 changed=328 / maxdelta=189 —— 即只有鼠标路过才画得出东西。
 *   簇内共 15 枚钮 + 2 支下拉（本批清点 {clusters:3, clusterBtns:15, clusterSelects:2}）。
 *
 * 修法：给同一族的三条宿主补 `:focus-within` 显形，与 `:hover` 平级——行内任一控件拿到
 * 键盘焦点就把整簇亮起来，键盘用户与鼠标用户看到同一排工具。
 *
 * 同形状但**不该修**的对照（同批实测，写在这里防下一批人把闸门改成「凡透明即红」时误伤）：
 * 设置页 `input.paper-switch-input` 本体恒 `opacity: 0`（sr-only 原生框），可见替身是同父兄弟
 * `.paper-switch-track` 的 `:focus-visible` 3px 环——真键盘聚焦时同一裁剪区 changed=323，
 * 聚焦看得见。它的账在常驻网那边（audit-focus-rings.mjs 的 BD-243 段），本文件只管产品侧。
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

const here = path.dirname(fileURLToPath(import.meta.url));
const raw = readFileSync(path.join(here, "../../../../styles.css"), "utf8");
const css = raw.replace(/\/\*[\s\S]*?\*\//g, "");

/** 取「以某条选择器开头的那一条规则」——返回选择器清单与声明体。 */
const ruleOf = (head: string) => {
  const i = css.indexOf(head);
  if (i < 0) return null;
  const brace = css.indexOf("{", i);
  if (brace < 0) return null;
  const end = css.indexOf("}", brace);
  if (end < 0) return null;
  return {
    selectors: css
      .slice(i, brace)
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean),
    body: css.slice(brace + 1, end)
  };
};

const HOSTS = [".outline-volume-row", ".outline-chapter-row", ".outline-scene"];

describe("BD-243 大纲行内动作簇的键盘显形通路", () => {
  it("基础规则仍然藏着这一簇（修的是通路，不是把 hover 设计删掉）", () => {
    const base = ruleOf(".outline-node-actions {");
    expect(base).not.toBeNull();
    expect(base!.body).toMatch(/opacity:\s*0;/);
  });

  it("三个宿主各有一条 :hover 显形，且每条都要有配对的 :focus-within", () => {
    const reveal = ruleOf(".outline-volume-row:hover .outline-node-actions");
    expect(reveal).not.toBeNull();
    for (const host of HOSTS) {
      // 逐宿主点名：少一条就是那一行的键盘用户又回到「看不见控件」
      expect(reveal!.selectors).toContain(`${host}:hover .outline-node-actions`);
      expect(reveal!.selectors).toContain(`${host}:focus-within .outline-node-actions`);
    }
  });

  it("显形规则只有 6 条选择器（3 宿主 × hover／focus-within），声明体只改 opacity", () => {
    const reveal = ruleOf(".outline-volume-row:hover .outline-node-actions")!;
    expect(reveal.selectors).toHaveLength(6);
    expect(reveal.selectors.filter((s) => /:hover/.test(s))).toHaveLength(3);
    expect(reveal.selectors.filter((s) => /:focus-within/.test(s))).toHaveLength(3);
    expect(reveal.body.trim()).toBe("opacity: 1;");
  });

  it("账要留在 CSS 里：实测依据、对照形状、以及「程序 focus() 不算聚焦态」这条教训", () => {
    expect(raw).toContain("bd243-p3.log");
    expect(raw).toContain("paper-switch-track");
    expect(raw).toContain("focus()");
  });
});
