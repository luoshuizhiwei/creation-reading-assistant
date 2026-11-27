// @vitest-environment node
/**
 * BD-210 账本：非文本对比（WCAG 1.4.11）普查判据 + 本批修复锚。
 * 运行时普查（audit-non-text-contrast.mjs）是主判据；这里静态钉住判据的骨架、
 * 滑杆「作者规则定罪」的取色路、21 个范围的点名，以及产品侧六处修复的落点，
 * 防止任何一侧漂走。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
// 路径账（对照本目录 placeholder-contrast.test.ts 先例，__tests__/ 起算）：
// ../../../scripts = 仓库根 scripts；../../styles.css = src/styles.css；
// ../../features = src/features；../../../package.json = 仓库根 package.json。
const census = read("../../../scripts/audit-non-text-contrast.mjs");
const styles = read("../../styles.css");
const editorial = read("../../styles/editorial-studio.css");
const quickRef = read("../../features/library/reader/components/ReaderBottomBar.tsx");
const quickCss = read("../../features/creation/editor/writing-quick-reference.css");
const pkg = read("../../../package.json");
const stripComments = (s: string) => s.replace(/\/\*[\s\S]*?\*\//g, "");

describe("BD-210 非文本对比普查判据骨架", () => {
  it("①地板 ≥3 两分支都在：字段/表面/滑杆走 best，图标钮走 rBack", () => {
    expect(census).toMatch(/row\.pass = row\.best >= 3;/);
    expect(census).toMatch(/row\.pass = b >= 3;/);
  });
  it("②豁免：disabled/inert 子树整族跳过；UA 原生三票齐", () => {
    expect(census).toMatch(/el\.disabled \|\| el\.closest\("\[inert\]"\)/);
    expect(census).toMatch(/el\.type === "checkbox" \|\| el\.type === "radio"/);
    expect(census).toMatch(/tag === "select" && cs\.appearance !== "none"/);
    expect(census).toMatch(/el\.type === "range" && cs\.appearance !== "none"/);
  });
  it("③纯字段桶排除非字段 input 型全集（文本框即便 appearance:auto 也不进 UA 豁免）", () => {
    expect(census).toMatch(/\["button", "checkbox", "radio", "submit", "reset", "image", "range", "file", "color"\]/);
  });
  it("④文字标签即强识别：labeled 豁免在 C/B2 判定之前 continue", () => {
    const iLab = census.indexOf('out.counts.labeled += 1; continue;');
    const iC = census.indexOf('else if (hasSvg) bucket = "C";');
    expect(iLab).toBeGreaterThan(0);
    expect(iC).toBeGreaterThan(iLab);
  });
  it("⑤滑杆取色走作者规则定罪：thumb/track 两条伪元素选择器 + 交互态剔除 + var 解链", () => {
    expect(census).toMatch(/authorPseudo\(el, "::-webkit-slider-thumb"\)/);
    expect(census).toMatch(/authorPseudo\(el, "::-webkit-slider-runnable-track"\)/);
    expect(census).toMatch(/:hover\|:focus\|:active\|:disabled/);
    expect(census).toMatch(/var\\\(\(--\[A-Za-z0-9-\]\+\)/);
  });
  it("⑥作者没画 thumb ⇒ thumbBlind 计数、不投票；细轨只作候选", () => {
    expect(census).toMatch(/out\.counts\.thumbBlind \+= 1;/);
    expect(census).toMatch(/row\.thumb = "查无作者规则"/);
    expect(census).toMatch(/if \(tr\.colors\.length\) \{/);
  });
  it("⑦复合控件壳候选恒开且不止收一层：框按「祖先 × 四条边」全收、填色取首个（首跑 uni-search 误判记账）", () => {
    expect(census).toContain("shellBorderColors");
    expect(census).toContain("shellFill");
    expect(census).toMatch(/if \(!shellFill && pBg && pBg\.a > 0\) \{/);
    expect(census).toMatch(/row\.shellNode = /);
    // 逐边收：宽度门 + 对应边的颜色，同色去重。
    expect(census).toMatch(/parseFloat\(pcs\[`border\$\{side\}Width`\]\)/);
    expect(census).toMatch(/parse\(pcs\[`border\$\{side\}Color`\]\)/);
    expect(census).toMatch(/!shellBorderColors\.some\(/);
    const iShell = census.indexOf("const shellBorderColors = [];");
    const iRow = census.indexOf("row.best = Math.max");
    expect(iShell).toBeGreaterThan(0);
    expect(iShell).toBeLessThan(iRow);
    // 两条旧判据都不许复活：① 首个有底/有框祖先就 break；② 只收「首个有框祖先」的
    // borderTopColor。两者都会让 .uni-search-head（只有 border-bottom-color 有值）挡住
    // 真壳边，把已修复的全局搜索输入测成 1.17 假红。
    expect(census).not.toContain("if (pSides || (pBg && pBg.a > 0)) { shell =");
    expect(census).not.toContain("shellBorderColors.length === 0");
  });
  it("⑧Chromium 33 现代色语法解析在场：color(srgb …) 分支与乘 255", () => {
    // 钉普查脚本里的正则字面量原文（toContain 用字符串，免双重转义打架）。
    expect(census).toContain("color\\(\\s*srgb");
    expect(census).toMatch(/Number\.parseFloat\(x\) \* 255/);
  });
});

describe("BD-210 普查点名与序列", () => {
  const labels = [
    "弹层·新建项目向导", "项目·写作台", "项目·设定卡", "项目·大纲", "项目·概览",
    "项目·全书预览", "项目·写作统计", "项目·版本历史", "书库", "收件箱", "资料阅读",
    "设置", "弹层·全局搜索", "弹层·写作速查", "弹层·写作速查·条目详情",
    "阅读器·TXT", "阅读器·TXT设置抽屉", "阅读器·TXT目录面板", "阅读器·TXT搜索浮层",
    "阅读器·EPUB", "弹层·EPUB设置抽屉"
  ];
  it("⑨21 个范围逐点名齐", () => {
    for (const l of labels) expect(census).toContain(l);
    expect((census.match(/probe\("弹层·EPUB设置抽屉"\)|tryScope\("弹层·EPUB设置抽屉"/g) || []).length).toBe(1);
  });
  it("⑩速查条目详情：幂等播卡 → 切「全局搜索」tab 检索 → 点第一项（跨项目检索才不受当前项目空列表限制）", () => {
    expect(census).toContain('type: "card.create"');
    // 幂等：先查后建，多跑普查不堆重复卡。
    expect(census).toMatch(/cardsList\(\{ kind: "cards\.list", search: "普查卡片" \}\)/);
    expect(census).toMatch(/\.writing-quick-tabs[\s\S]{0,400}全局搜索[\s\S]{0,400}\.writing-quick-search input[\s\S]{0,400}\.writing-quick-list li > button/);
    const i = census.indexOf("弹层·写作速查·条目详情");
    expect(census.slice(i, i + 1600)).toMatch(/\.writing-quick-list li > button/);
    // toggle 防误关：面板开着时不许再按 Ctrl+Shift+K。
    expect(census.slice(i, i + 1600)).toMatch(/isVisible\(\)[\s\S]{0,120}Control\+Shift\+k/);
  });
  it("⑪先播种后重开、项目名独占", () => {
    const iSeed = census.indexOf("round4-seed.cjs");
    const iFinal = census.lastIndexOf("const { app, page } = await launch();");
    expect(iSeed).toBeGreaterThan(0);
    expect(iSeed).toBeLessThan(iFinal);
    expect(census).toContain("非文本对比普查项目");
  });
  it("⑫红/绿两条机器标记与 exit(1)", () => {
    expect(census).toMatch(/NONTEXTCONTRAST_RED：\$\{fails\.length\}/);
    expect(census).toMatch(/NONTEXTCONTRAST_OK：\$\{scopeCount\}/);
    const iRed = census.indexOf("NONTEXTCONTRAST_RED");
    expect(census.slice(iRed)).toMatch(/process\.exit\(1\)/);
  });
});

describe("BD-210 产品侧修复锚", () => {
  it("⑬styles.css 立 --border-field = var(--text-secondary)（主题联动，深浅两档都 ≥3）", () => {
    expect(styles).toMatch(/--border-field: var\(--text-secondary\);/);
    expect(styles).toContain("BD-210");
  });
  it("⑭desktop-canvas 字段重映射有压制条：.desktop-canvas .paper-input/.cards-input → --border-field", () => {
    const body = stripComments(styles) + stripComments(editorial);
    expect(body).toMatch(/\.desktop-canvas \.paper-input,\s*\n\.desktop-canvas \.cards-input \{\s*\n\s*border-color: var\(--border-field\);/);
  });
  it("⑮字段族整族抬色：组规则与 reader-root 覆盖都走 --border-field", () => {
    const e = stripComments(editorial);
    expect(e).toMatch(/\.paper-input,\s*\n\.cards-input \{\s*\n\s*border-color: var\(--border-field\);/);
    expect(e).toMatch(/\.reader-root \.paper-input \{\s*\n\s*border-color: var\(--border-field\);/);
  });
  it("⑯全局搜索壳边与卡片库搜索壳都抬到 --border-field", () => {
    const e = stripComments(editorial);
    expect(e).toMatch(/\.uni-search-shell \{\s*\n\s*overflow: hidden;\s*\n\s*border: 1px solid var\(--border-field\);/);
    const s = stripComments(styles);
    expect(s).toMatch(/\.cards-search \{[\s\S]{0,200}?border: 1px solid var\(--border-field\);/);
  });
  it("⑰TXT 进度滑杆升语义类 + 作者规则画强 thumb（2px 主色描边），旧半透明工具类撤干净", () => {
    expect(quickRef).toMatch(/className="reader-progress-slider h-1\.5 w-full cursor-pointer"/);
    expect(quickRef).not.toContain("bg-paper-line/60");
    expect(quickRef).not.toContain("appearance-none");
    const e = stripComments(editorial);
    expect(e).toMatch(/\.reader-progress-slider::-webkit-slider-thumb \{[\s\S]{0,260}?border: 2px solid var\(--action-primary\);[\s\S]{0,80}?background: var\(--surface-1\);/);
    expect(e).toMatch(/\.reader-progress-slider::-moz-range-thumb \{/);
    expect(e).toMatch(/box-shadow: var\(--shadow-control-thumb\);/);
  });
  it("⑱package.json 挂上常驻脚本", () => {
    expect(pkg).toMatch(/"audit:non-text-contrast": "node scripts\/audit-non-text-contrast\.mjs"/);
  });
  it("⑲dock 主题钮首跑红补账：常态 1px solid transparent 整族无边界，升 --border-field 环、选中仍由 seal 环指认", () => {
    const e = stripComments(editorial);
    expect(e).toMatch(/\.desktop-dock-dot \{[\s\S]{0,400}?border: 1px solid var\(--border-field\);/);
    expect(e).not.toMatch(/\.desktop-dock-dot \{[\s\S]{0,400}?border: 1px solid transparent;/);
    expect(e).toMatch(/\.desktop-dock-dot\.active \{\s*\n\s*border-color: var\(--studio-seal\);/);
  });
  it("⑳速查字段族同批升档：搜索壳边与详情字段边都走 --border-field", () => {
    const q = stripComments(quickCss);
    expect(q).toMatch(/\.writing-quick-search \{[\s\S]{0,220}?border: 1px solid var\(--border-field\);/);
    expect(q).toMatch(/\.writing-quick-field input,\s*\n\.writing-quick-detail \.cards-input \{[^}]*border: 1px solid var\(--border-field\);/);
    // 速查文件里带边框的字段/壳不许再留 --border-subtle 档（行按钮 hairline、列表底分隔线是文本行装饰，不在本批判据内）。
    expect(q).not.toMatch(/\.writing-quick-search \{[\s\S]{0,220}?border: 1px solid var\(--border-subtle\);/);
  });
  it("㉑普查不许把透明边当投票者：a>=1 门与「透明」如实显示都在", () => {
    expect(census).toMatch(/if \(bc && bc\.a >= 1\) \{ row\.border = /);
    expect(census).toMatch(/row\.border = "透明"/);
    expect(census).toMatch(/if \(sbc && sbc\.a >= 1 && !shellBorderColors\.some\(/);
  });
});

describe("BD-258 判边门槛按设备像素比折算", () => {
  const dprLine = "const dpr = window.devicePixelRatio || 1;";
  const helper = "const visibleBorder = (w) => w * dpr >= 1 - 1e-4;";
  const own = '.some((w) => visibleBorder(w) && cs.borderStyle !== "none" && cs.borderStyle !== "hidden");';
  const shell = "if (!visibleBorder(parseFloat(pcs[`border${side}Width`]) || 0)) continue;";
  const count = (hay: string, needle: string) => hay.split(needle).length - 1;
  it("㉒像素比读数在场且缺读回 1，并落在 collectScript 里、TARGETS 之前（在页内读 window）", () => {
    expect(count(census, dprLine)).toBe(1);
    const iOpen = census.indexOf("const collectScript = () => {");
    const iDpr = census.indexOf(dprLine);
    const iHelper = census.indexOf(helper);
    const iTargets = census.indexOf("const TARGETS = ");
    expect(iOpen).toBeGreaterThan(0);
    expect(iDpr).toBeGreaterThan(iOpen);
    expect(iHelper).toBeGreaterThan(iDpr);
    expect(iTargets).toBeGreaterThan(iHelper);
  });
  it("㉓折算助手原文在场：换算成设备像素后 ≥1，留 1e-4 吃 computed 值只留 6 位小数的舍入", () => {
    expect(count(census, helper)).toBe(1);
  });
  it("㉔两处判边门槛都改走 visibleBorder（自身四边 some、复合控件壳逐边收），调用恰 2 处", () => {
    expect(count(census, own)).toBe(1);
    expect(count(census, shell)).toBe(1);
    expect(count(census, "visibleBorder(")).toBe(2);
  });
  it("㉕旧判据不许复活：自身四边写死 w >= 1、复合控件壳写死「宽度 || 0 < 1 就跳过」", () => {
    expect(census).not.toMatch(/\.some\(\(w\) => w >= 1 /);
    expect(census).not.toMatch(/\|\| 0\) < 1\) continue;/);
  });
});
