// @vitest-environment node
/**
 * BD-252 源码账：两步确认态可见性普查（WCAG 1.4.1 动作确认维度）的口径与那两条样式修法。
 *
 * 这本账读文件，不读 DOM——像素与 computed 那半边由 scripts/audit-confirm-state.mjs 在真 app 里量。
 * 两边各钉一件事：
 *   · 判据的命门（画得出才算测 / 确认态要真进去 / aria 不算视觉线索 / 真实元素宽高不算证据 /
 *     灰度差只作证据）每一条都是实测踩出来的，谁把它们简化回去，本账当场红。
 *   · 产品那两条 ::after 记号不许悄悄退回去，也不许改成「换图标 / 加边框 / 占 outline」——
 *     那三种改法各会撞坏另一本常驻账（3.2.4 图案一致性 / 指针目标与图标内容盒 / 焦点环差分）。
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const at = (rel: string) => readFileSync(path.resolve(process.cwd(), rel), "utf8");
const census = at("scripts/audit-confirm-state.mjs");
const pkg = at("package.json");
const styles = at("src/styles.css");
const cardsCss = at("src/features/creation/cards/cards-local.css");

/** 只取代码行：注释里要留下教训原文，不算「引用了那条写法」。 */
const codeOf = (text: string) =>
  text
    .split("\n")
    .filter((line) => !line.trim().startsWith("//") && !line.trim().startsWith("*") && !line.trim().startsWith("/*"))
    .join("\n");

/** 取一条 CSS 规则的声明体（到第一个右花括号为止，够本账用：这几条都没有嵌套）。 */
const ruleBody = (css: string, selector: string) => {
  const start = css.indexOf(selector);
  if (start < 0) return "";
  const open = css.indexOf("{", start);
  const close = css.indexOf("}", open);
  return css.slice(open + 1, close);
};

describe("BD-252 普查常驻与口径", () => {
  it("命令登记在 package.json（想当一次性脚本？不算常驻）", () => {
    expect(pkg).toContain('"audit:confirm-state": "node scripts/audit-confirm-state.mjs"');
  });

  it("站点 6 个：图标钮 5（树 3 ＋ 类型编辑器 2）＋ 带文字负对照 1", () => {
    const block = census.slice(census.indexOf("const SITES = ["), census.indexOf("const openSite ="));
    const labels = [...block.matchAll(/label: "([^"]+)"/g)].map((m) => m[1]);
    expect(labels).toEqual([
      "树·删除卷",
      "树·删除章节",
      "树·删除场景",
      "卡片类型·删除类型",
      "关系类型·删除关系类型",
      "背景卡·删除（负对照）"
    ]);
    expect(labels.filter((l) => l.startsWith("树·")).length).toBe(3);
    expect(labels.filter((l) => l.includes("负对照")).length).toBe(1);
  });

  it("判据①：祖先链 opacity 乘出来的绘制不透明度 <0.99 记 invisible，不算绿", () => {
    expect(census).toContain("painted *= Number(s.opacity);");
    expect(census).toContain('if (s.display === "none" || s.visibility === "hidden") painted = 0;');
    expect(census).toContain('if (before.painted < 0.99) return { level: "invisible"');
  });

  it("判据①配套：大纲三站必须把行悬停住再拍（图标簇平时 opacity:0）", () => {
    const block = census.slice(census.indexOf("const SITES = ["), census.indexOf("const openSite ="));
    expect([...block.matchAll(/hoverSel: '([^']+)'/g)].map((m) => m[1])).toEqual([
      '.outline-volume-row:has-text("普查卷") .outline-volume-title',
      '.outline-chapter-row:has-text("普查章") .outline-chapter-title',
      '.outline-scene:has-text("普查场景") .outline-scene-title'
    ]);
    // 悬停锚点是行标题，不是那颗钮自己：hover 会改钮自己的底色，混进前后对照。
    expect(census).toContain("if (await anchor.count()) await anchor.hover().catch(() => {});");
    expect(census).toContain("await page.mouse.move(4, 4);");
  });

  it("判据②：确认态要真进去——类名/aria/文字/像素一样都没变记 unproven", () => {
    expect(census).toContain("function armed(before, after, pixelChanged)");
    expect(census).toContain("return before.cls !== after.cls || before.aria !== after.aria || before.text !== after.text || pixelChanged;");
    expect(census).toContain('if (!armed(before, after, pixelChanged)) return { level: "unproven"');
    expect(census).toContain('why: "第一击就把条目删掉了——这颗不是两步确认钮"');
  });

  it("判据③：形状通道为 0 才判红，形状/颜色两张表分开记", () => {
    expect(census).toContain("function channels(before, after)");
    expect(census).toContain("if (shape.length === 0) {");
    expect(census).toContain('return { level: "coloronly"');
  });

  it("形状通道收文字/图标/子节点/伪元素，颜色通道只收四个颜色属性", () => {
    const fn = census.slice(census.indexOf("function channels("), census.indexOf("// 确认态到底进去了没有"));
    expect(fn).toContain("if (before.text !== after.text)");
    expect(fn).toContain("if (before.icons !== after.icons)");
    expect(fn).toContain("if (before.childCount !== after.childCount)");
    expect(fn).toContain("if (pseudoB !== pseudoA) shape.push(");
    expect(fn).toContain('for (const key of ["color", "background", "borderColor", "outlineColor"])');
    // aria 与真实元素宽高都不许进形状表（前者是读屏腿，后者是文字长度的影子）。
    expect(fn).not.toContain("before.aria");
    expect(fn).not.toContain("getBoundingClientRect");
    expect(fn).not.toContain("before.w ");
  });

  it("伪元素记号必须真画得出：有文字，或有面积且有底色/边框（零高度/透明底都不算）", () => {
    expect(census).toContain("function pseudoMark(raw)");
    expect(census).toContain("const hasArea = px(raw.width) > 0 && px(raw.height) > 0;");
    expect(census).toContain('const hasPaint = (raw.bg !== "rgba(0, 0, 0, 0)" && raw.bg !== "transparent") || px(raw.border) > 0;');
    expect(census).toContain('const live = raw.display !== "none" && raw.visibility !== "hidden" && (hasText || (hasArea && hasPaint));');
    // 判定必须在 node 侧（自证针才喂得到同一把尺子），页内只取原始读数。
    const inPage = census.slice(census.indexOf("const snapshot ="), census.indexOf("/* ---------- 判据"));
    expect(inPage).toContain("return { p, content: s.content, width: s.width, height: s.height");
    expect(inPage).not.toContain("hasArea");
  });

  it("灰度/像素只作证据：像素比对不参与判红，只喂给「确认态进去了没有」", () => {
    expect(census).toContain("const pixelChanged = Buffer.compare(shotA, shotB) !== 0;");
    const fn = census.slice(census.indexOf("function levelOf("), census.indexOf("/* ---------- 逐站测量"));
    expect(fn).not.toContain("shotA");
    expect(fn).not.toContain("Buffer.compare");
  });

  it("判据④：一个站点都没测到判失明；缺席记 absent，不算合规", () => {
    expect(census).toContain('if (!measured) rows.push({ level: "red"');
    expect(census).toContain('return { site: site.label, level: "absent", why: "控件枚举为 0（站点缺席）" };');
    expect(census).toContain('else rows.push({ level: "skip"');
  });

  it("种子必须现建自定义类型：内置类型的删除钮恒 disabled，分校准按「可用的钮」验", () => {
    expect(census).toContain('fill("普查功法")');
    expect(census).toContain('fill("普查师徒")');
    expect(census).toContain('button.cards-danger")).filter((b) => !b.disabled).length');
    expect(census).toContain("if (usable === 0 || usableCard === 0) throw new Error(");
  });

  it("十二枚自证针都在，且喂的是同一个 levelOf() / verdicts()", () => {
    for (const name of ["A 只换颜色该红", "B 文字变了不许误伤", "C 伪元素杠出现不许误伤", "D 边框加粗不许误伤", "E 只改 aria 该红", "F 空跑不许算绿", "G 画不出来不许算绿", "H 测到 0 个站点该报失明", "I 像素差大也不许当合规", "J 全有线索该全绿", "K 零高度的杠该红", "L 透明底色的杠该红"]) {
      expect(census).toContain(name);
    }
    const needles = census.slice(census.indexOf("function runNeedles()"));
    expect(needles).toContain("const v = levelOf(before, after, pixelChanged);");
    expect(needles).toContain("return verdicts([{ site: \"针\", ...v }], 1);");
    expect(census).toContain("if (!needleOk) {");
    expect(census).toContain("CONFIRM_STATE_NEEDLE_FAIL");
  });

  it("红基线与三个退出码都写在账上", () => {
    expect(census).toContain("站点 6 个｜实际测到 6 个｜只靠颜色 5 处｜有非色彩线索 1 处");
    expect(census).toContain("CONFIRM_STATE_RED");
    expect(census).toContain("CONFIRM_STATE_SKIP");
    expect(census).toContain("CONFIRM_STATE_OK");
    expect(census).toContain("process.exit(2);");
  });

  it("头注写明与 BD-249 / BD-226 / 1.4.11 / BD-172 的分工（不许两支脚互相代劳）", () => {
    expect(census).toContain("audit-selected-state.mjs");
    expect(census).toContain("audit-consistent-identification.mjs");
    expect(census).toContain("audit-non-text-contrast.mjs");
    expect(census).toContain("BD-172");
  });

  it("牙的记录写在账上：13 档运行时牙 ＋ 20 档静态牙，含两枚反向牙与两条期望值勘误", () => {
    for (const mark of ["T1 两族的 ::after 全摘", "T4 杠高 2px→0", "T5 杠底色→transparent", "T7 摘掉行悬停锚点", "T8 空按", "T9 空按 ＋ 拆掉", "T10 反向牙", "T11 反向牙", "牙架自己踩的两件事"]) {
      expect(census).toContain(mark);
    }
    expect(census).toContain("每档跑完立刻逐字节还原**并重建**");
    expect(census).toContain("反向牙的期望要盯「谁拦下的」");
  });

  it("头注记下量具自己踩过的那件事：鼠标移开再拍 ⇒ 前后两图逐字节相同", () => {
    expect(census).toContain("逐字节相同");
    expect(census).toContain("styles.css:2355");
  });
});

describe("BD-252 产品修法：确认态的形状记号", () => {
  it("大纲族：确认态有 position:relative 与 ::after 横杠，杠色用 --proof-mark", () => {
    const base = ruleBody(styles, ".outline-node-actions button.confirming {");
    expect(base).toContain("position: relative;");
    const bar = ruleBody(styles, ".outline-node-actions button.confirming::after {");
    expect(bar).toContain('content: "";');
    expect(bar).toContain("position: absolute;");
    expect(bar).toContain("height: 2px;");
    expect(bar).toContain("background: var(--proof-mark);");
  });

  it("卡片族：同一套语言，杠色用 --fg-on-solid（画在实色红填充上）", () => {
    const base = ruleBody(cardsCss, ".cards-icon-btn.confirming {");
    expect(base).toContain("position: relative;");
    const bar = ruleBody(cardsCss, ".cards-icon-btn.confirming::after {");
    expect(bar).toContain('content: "";');
    expect(bar).toContain("position: absolute;");
    expect(bar).toContain("height: 2px;");
    expect(bar).toContain("background: var(--fg-on-solid);");
  });

  it("两族的杠都不占流、不动间距刻度（不写 padding/margin/gap）", () => {
    for (const [css, sel] of [[styles, ".outline-node-actions button.confirming::after {"], [cardsCss, ".cards-icon-btn.confirming::after {"]] as const) {
      const bar = ruleBody(css, sel);
      expect(bar).not.toMatch(/(^|\s)(padding|margin|gap)\s*:/);
      expect(bar).toContain("left: 4px;");
      expect(bar).toContain("right: 4px;");
      expect(bar).toContain("bottom: 2px;");
    }
  });

  it("不许改成占 outline / box-shadow（那两样是焦点环账的判据面）", () => {
    for (const [css, sel] of [[styles, ".outline-node-actions button.confirming"], [cardsCss, ".cards-icon-btn.confirming"]] as const) {
      const base = ruleBody(css, `${sel} {`);
      const bar = ruleBody(css, `${sel}::after {`);
      for (const body of [base, bar]) {
        expect(body).not.toMatch(/outline\s*:/);
        expect(body).not.toMatch(/box-shadow\s*:/);
      }
    }
  });

  it("图标不许换：删除族的单一图案（3.2.4）由始至终是 Trash2", () => {
    const tree = at("src/features/creation/outline/OutlineTree.tsx");
    const cardTypes = at("src/features/creation/cards/CardTypeEditor.tsx");
    const relationTypes = at("src/features/creation/cards/RelationTypeEditor.tsx");
    for (const src of [tree, cardTypes, relationTypes]) {
      expect(src).toContain("<Trash2 size={");
      expect(src).not.toContain("confirming ? <");
    }
    expect(codeOf(tree)).toContain('className={confirm?.kind === "volume" && confirm.id === volume.id ? "confirming" : ""}');
  });

  it("带文字的那三支不动（它们本来就有非色彩线索，负对照靠它们立）", () => {
    const background = at("src/features/creation/background/BackgroundPage.tsx");
    const relations = at("src/features/creation/cards/components/CardRelationsManager.tsx");
    expect(background).toContain('{confirmDelete ? "确认删除" : "删除"}');
    expect(relations).toContain('{confirmingRelationId === relation.id ? "确认删除" : "删除"}');
    // 文字族不该被顺手加上杠：它们的记号是文字，两套语言混用会让同一族出现两种说法。
    expect(styles).not.toContain(".background-detail-actions button.confirming::after");
    expect(cardsCss).not.toContain(".cards-relation-delete.confirming::after");
  });
});