// @vitest-environment node
/**
 * BD-243 族账（WCAG 2.4.7 交叉类「键盘可达的控件不得靠 hover 才显形」）：
 * 「平时透明、鼠标路过才亮」是一种省电面的设计，但当被藏起来的那一层里有**能拿到键盘焦点的
 * 控件**时，它就变成「键盘用户聚焦了一个屏幕上看不见的东西」——opacity 既不摘 tabindex，
 * 也不影响 document.activeElement，所以这类控件确实在 Tab 序列里。
 *
 * 本批的缺陷本体（运行时实测 /tmp/bd243-p3.log）：大纲行内动作簇 `.outline-node-actions`
 * 基础规则 `opacity: 0`、原本只有三条 `:hover` 显形，簇内 15 枚钮 + 2 支下拉；
 * 键盘第 10 步落进去时本体算得出 `solid 2px` 环（audit:focus-rings 据此判绿——假绿），
 * 而祖先 opacity:0 让环一像素都不落在屏上（同一裁剪区「真键盘聚焦 vs 离焦」changed=0）。
 * 修法给同一族三条宿主补 `:focus-within`，产品侧的逐条钉法见
 * src/features/creation/outline/__tests__/outline-actions-keyboard-reveal.test.ts。
 *
 * 本文件管**族**：全仓 CSS 里凡是「基础规则把带焦点控件的一层藏起来 + :hover 显形」的宿主，
 * 必须有配对的 focus 显形；Tailwind 侧 `group-hover:opacity-100` 必须同行带
 * `focus-visible:opacity-100`（BD-168 立的口径）。两侧都只做静态点名，
 * 「聚焦到底看不看得见」的最终裁决在运行时网（audit-focus-rings.mjs 的 BD-243 闸门）。
 */
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

const here = path.dirname(fileURLToPath(import.meta.url));
const srcRoot = path.resolve(here, "../.."); // src/

const cssFiles = readdirSync(srcRoot, { recursive: true, encoding: "utf8" })
  .filter((f) => typeof f === "string" && f.endsWith(".css"))
  .map((f) => path.join(srcRoot, f));

type Rule = { selector: string; body: string };

/** 粗解析：跳过注释、把嵌套 at-rule（media / keyframes）往里走一层，只收「有选择器」的规则。 */
const rulesIn = (text: string): Rule[] => {
  const stripped = text.replace(/\/\*[\s\S]*?\*\//g, "");
  const out: Rule[] = [];
  const walk = (s: string) => {
    let i = 0;
    while (i < s.length) {
      const brace = s.indexOf("{", i);
      if (brace < 0) break;
      const head = s.slice(i, brace).trim();
      let depth = 1;
      let j = brace + 1;
      while (j < s.length && depth > 0) {
        const ch = s[j];
        if (ch === "{") depth += 1;
        else if (ch === "}") depth -= 1;
        j += 1;
      }
      const inner = s.slice(brace + 1, j - 1);
      if (head.startsWith("@")) {
        // keyframes 里的 from/to/0% 不是选择器宿主，但仍往里走一层（子规则里没有类名，自然不命中）
        if (!/@(import|charset|font-face|keyframes)/i.test(head)) walk(inner);
      } else if (head) {
        out.push({ selector: head, body: inner });
      }
      i = j;
    }
  };
  walk(stripped);
  return out;
};

const allRules = cssFiles.flatMap((f) => rulesIn(readFileSync(f, "utf8")).map((r) => ({ file: path.relative(srcRoot, f), ...r })));

/** 基础规则里把整层写成 opacity:0 的宿主类（排除 :hover／:focus 自身参与的规则）。 */
const hiddenHosts = new Map<string, { file: string; selector: string }>();
for (const r of allRules) {
  if (!/(^|[;{\s])opacity:\s*0\s*(;|$)/.test(r.body)) continue;
  if (/:(hover|focus)/.test(r.selector)) continue;
  for (const sel of r.selector.split(",").map((s) => s.trim())) {
    const cls = sel.match(/\.[A-Za-z0-9_-]+/g);
    if (!cls) continue; // keyframes 的 from/to、纯元素选择器不进族账
    for (const c of cls) hiddenHosts.set(c, { file: r.file, selector: sel });
  }
}

/** 「本体透明、可见替身另有其人」的合法形状：原生框被 sr-only 化，焦点态画在兄弟／父上。 */
const SURROGATE_HOSTS = new Set([
  ".paper-switch-input", // settings-controls.css:136 :focus-visible + .paper-switch-track 画 3px 环
  ".export-preset-option" // 导出预设卡片：input 透明，选中／聚焦态由卡片本体画
]);

describe("BD-243 hover-only 显形族账", () => {
  it("CSS 侧族账能看见本批的宿主（看不见就说明解析器漂了）", () => {
    expect(hiddenHosts.has(".outline-node-actions")).toBe(true);
    expect(hiddenHosts.has(".paper-switch-input")).toBe(true);
  });

  it("凡「:hover 才显形」的透明宿主，必须有配对的 focus 显形或可见替身", () => {
    const bad: string[] = [];
    for (const [cls, host] of hiddenHosts) {
      const reveals = allRules.filter(
        (r) => r.selector.includes(cls) && /:hover/.test(r.selector)
      );
      if (!reveals.length) continue; // 没有 hover 显形路，不进这一族（可能是纯禁用态/动画）
      const focus = allRules.some(
        (r) => r.selector.includes(cls) && /:focus(-within|-visible)?/.test(r.selector)
      );
      if (!focus && !SURROGATE_HOSTS.has(cls)) {
        bad.push(`${cls}（${host.file}｜hover 显形 ${reveals.length} 条、focus 显形 0 条）`);
      }
    }
    expect(bad).toEqual([]);
  });

  it("hover 与 focus 显形条数成对（本批 3 宿主 × 2 = 6 条，少一条就有一行回到看不见）", () => {
    // 只数「显形」那一条：选择器以 .outline-node-actions 结尾的（`.outline-node-actions button:hover`
    // 是钮自己的悬停底色、不是整簇显形路，混进来账就数歪了）；声明体还得真改了 opacity
    const fam = allRules
      .filter((r) => /opacity:\s*1/.test(r.body))
      .flatMap((r) => r.selector.split(",").map((s) => s.trim()))
      .filter((s) => s.endsWith(".outline-node-actions"));
    const hoverHosts = fam.filter((s) => /:hover/.test(s));
    const focusHosts = fam.filter((s) => /:focus-within/.test(s));
    expect(hoverHosts).toHaveLength(3);
    expect(focusHosts).toHaveLength(3);
    for (const h of [".outline-volume-row", ".outline-chapter-row", ".outline-scene"]) {
      expect(hoverHosts.some((s) => s.startsWith(`${h}:hover`))).toBe(true);
      expect(focusHosts.some((s) => s.startsWith(`${h}:focus-within`))).toBe(true);
    }
  });

  it("Tailwind 侧同族口径：group-hover:opacity-100 必须同行配 focus-visible:opacity-100（BD-168 立的规矩）", () => {
    const tsx = readdirSync(srcRoot, { recursive: true, encoding: "utf8" })
      .filter((f) => typeof f === "string" && /\.tsx$/.test(f) && !f.includes("__tests__"))
      .map((f) => path.join(srcRoot, f));
    const lines: string[] = [];
    for (const f of tsx) {
      readFileSync(f, "utf8")
        .split("\n")
        .forEach((l, idx) => {
          if (l.includes("group-hover:opacity-100")) lines.push(`${path.relative(srcRoot, f)}:${idx + 1}`);
        });
    }
    // 当前在册 2 处，都在阅读侧面板的删除钮上（少一处=族被搬走，账要跟着改；多一处=新面进网）
    expect(lines.length).toBeGreaterThanOrEqual(2);
    const unpaired: string[] = [];
    for (const f of tsx) {
      readFileSync(f, "utf8")
        .split("\n")
        .forEach((l, idx) => {
          if (l.includes("group-hover:opacity-100") && !l.includes("focus-visible:opacity-100")) {
            unpaired.push(`${path.relative(srcRoot, f)}:${idx + 1}`);
          }
        });
    }
    expect(unpaired).toEqual([]);
  });

  it("对照面自己也得在账上：sr-only 原生框的焦点态由替身画，别被「凡透明即红」误伤", () => {
    const ctrl = readFileSync(path.join(srcRoot, "features/settings/settings-controls.css"), "utf8");
    expect(ctrl).toMatch(/\.paper-switch-input:focus-visible \+ \.paper-switch-track \{[^}]*box-shadow/);
  });
});
