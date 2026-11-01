// @vitest-environment node
/**
 * BD-224 账本：文本间距（WCAG 1.4.12）普查判据 + 本批修复锚。
 *
 * 运行时普查 scripts/audit-text-spacing.mjs 是主判据（宽档 16 屏 + 窄档 9 屏，各跑
 * 基线/加距两态）；这里钉两侧：
 * ① 判据骨架不许被改松（四档间距的取值、注入自证、裁字/压字/二维滚动三条判定的口径、
 *    基线归因只记档不判红、两档覆盖下限、脚本登记）；
 * ② 本批产品修复的落点——写作台顶栏那 46px 的钉死行高，是实测把行距设到 1.5×字号后
 *    把项目名顶出 overflow:hidden 页框的那颗钉子；以及把钉子放开后在窄档炸出的第二颗
 *    （标题组被挤成一字一行的竖排、导航条跑出页框），两颗都得留在案上。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const editorial = read("../../styles/editorial-studio.css");
const census = read("../../../scripts/audit-text-spacing.mjs");
const pkg = read("../../../package.json");
const stripComments = (s: string) => s.replace(/\/\*[\s\S]*?\*\//g, "");

/** 取某条规则的声明体（注释已摘，避免把判据注释当成声明）。 */
function ruleBody(css: string, needle: string): string {
  const at = css.indexOf(needle);
  expect(at, `找不到规则选择器「${needle}」`).toBeGreaterThan(-1);
  const open = css.indexOf("{", at);
  const close = css.indexOf("}", open);
  return stripComments(css.slice(open + 1, close)).trim();
}

describe("BD-224 产品修复锚", () => {
  it("①写作台顶栏 46px 从钉死高度降为底高：行高/段距被用户拉长时这一行能长，不再把项目名顶出页框", () => {
    const body = ruleBody(editorial, ".creation-writing-page--active .creation-writing-hero {");
    expect(body).toContain("height: auto !important;");
    expect(body, "底高 46px 丢了：紧凑项目条会塌成一行文字那么高").toContain("min-height: 46px !important;");
    expect(body, "max-height 回潮＝把这一行重新钉死，1.4.12 的实测路径原样复活").not.toMatch(/max-height:/);
    expect(body, "又出现写死的 46px 高度（min-height 不算）").not.toMatch(/(?:^|[^-\w])height:\s*46px/);
    expect(body).toContain("flex-shrink: 0 !important;");
    expect(body, "wrap 丢了＝只放高度的那一版：540 档标题组被操作按钮挤到 20px、顶栏长到 939px，"
      + "导航条跑出 860px 的隐藏页框（回流普查六次点 .project-nav button 全部超时）")
      .toContain("flex-wrap: wrap !important;");
  });

  it("②操作按钮换行后仍贴右缘：靠 margin-left:auto，不靠 space-between 的第二行左对齐", () => {
    const body = ruleBody(editorial, ".creation-writing-page--active .creation-writing-hero .desktop-page-actions {");
    expect(body).toContain("margin-left: auto !important;");
    expect(body, "按钮组自己会折行＝窄档顶栏高度失控的第二个来源").toContain("flex-wrap: nowrap !important;");
  });

  it("③修复现场留着实测数：title-group 41→82px、标题行盒 18→24px、页框顶边 y=40，窄档那颗洞也留案", () => {
    const at = editorial.indexOf(".creation-writing-page--active .creation-writing-hero {");
    const noteStart = editorial.lastIndexOf("/* 顶部紧凑项目条", at);
    expect(noteStart, "顶栏注释的起手句变了：实测来龙去脉只在这段注释里").toBeGreaterThan(-1);
    const note = editorial.slice(noteStart, at);
    expect(note, "BD-224 的实测注释丢了（这颗钉子的来龙去脉只在这段注释里）").toContain("BD-224（WCAG 1.4.12 文本间距）");
    expect(note).toContain("41px 长到 82px");
    expect(note).toContain("overflow:hidden 的页框");
    expect(note, "窄档塌陷的实测数丢了：那是本批改出来的第二个洞，不许只留结论不留数")
      .toContain("一字一行");
    expect(note).toContain("939");
    expect(note).toContain("probe-hero-540-collapse.log");
    expect(note, "默认档的代价被写没了：1360 顶栏实测是两行 82px（455+820+12=1287 > 内宽 1256），"
      + "不是「几何一字不变」——这句假话先前就写在这段注释里")
      .not.toContain("几何一字不变");
    expect(note, "wrap 在默认档换行这件事没留实测数（自然宽逐档记录在 hero-wide-probe.log）")
      .toContain("1287");
    expect(note).toContain("hero-wide-probe.log");
    expect(note, "nowrap 变体的 24 态 A/B 台账丢了：那是「为什么非 wrap 不可」的全部证据")
      .toContain("hero-ab-probe.log");
  });

  it("④统计页行轨底线钉在内容高：overflow:hidden 把自动最小尺寸清成 0，加距后行被压到内容之下、越界无处可滚", () => {
    const body = ruleBody(editorial, ".stats-page {\n  grid-auto-rows");
    expect(body, "行轨最小尺寸回潮＝540 档写作统计屏的裁字复活（实测 h3 越出盒底 155px、格内 720/354 无处滚）")
      .toContain("grid-auto-rows: minmax(min-content, auto);");
    expect(body, "上限不是 auto 了＝宽档那两张整幅卡不再随余量拉伸（1360 实测两态逐位不变，靠的就是这个 auto）")
      .not.toMatch(/max-content|min-content\s*;/);
    const cell = ruleBody(editorial, ".stats-page .stats-grid {");
    expect(cell, "把 min-height:min-content 挂回格子上：格按 770 画、行轨仍 356，裁字只是变压字（普查实测 p.stats-note × p.stats-note 压字 175×12px）")
      .not.toContain("min-height: min-content");
    expect(cell).toContain("overflow: hidden;");
  });
});

describe("BD-224 普查判据骨架", () => {
  it("①装的就是 1.4.12 那四档用户设置，且只动这四个性质", () => {
    const at = census.indexOf("const SPACING_CSS = `");
    expect(at, "注入常量的落点变了").toBeGreaterThan(-1);
    const block = census.slice(at, census.indexOf("`;", at) + 2);
    expect(block).toContain("line-height: 1.5 !important;");
    expect(block).toContain("letter-spacing: 0.12em !important;");
    expect(block).toContain("word-spacing: 0.16em !important;");
    expect(block).toContain("html[data-bd224-spacing] p { margin-bottom: 2em !important; }");
    expect(block, "注入不许顺手改宽高/overflow——那不再是 1.4.12 的用户设置")
      .not.toMatch(/[^-\w](?:min-|max-)?(?:width|height|overflow)\s*:/);
  });

  it("②注入自证：加距后必须读出 1.5×字号与 0.12×字号两条不变量，读不出判红而不是判绿", () => {
    expect(census).toContain('if (Math.abs(lh - fs * 1.5) < 0.6 && Math.abs(ls - fs * 0.12) < 0.06');
    expect(census).toContain("注入未咬到");
    expect(census).toContain("间距样式没落地");
  });

  it("③裁字判定只认 hidden/clip，auto/scroll 那一轴用户滚得到；读屏节点与 1×1 盒子不参与", () => {
    expect(census).toContain('if (v === "hidden" || v === "clip") return { kind: "cut", host: p };');
    expect(census).toContain('if (v === "auto" || v === "scroll") return { kind: "scroll" };');
    expect(census).toContain('if (own.width <= 2 || own.height <= 2) continue;');
    expect(census).toContain('if (y.kind === "sr") continue;');
  });

  it("④压字先按祖先链裁成真被画出来的那块，且父子行盒不互判", () => {
    expect(census).toContain("const painted = (el, r) => {");
    expect(census).toContain("const inkRows = rows.filter((it) => it.ink);");
    expect(census).toContain('if (ea.el === eb.el || ea.el.contains(eb.el) || eb.el.contains(ea.el)) continue;');
    expect(census).toContain("if (hasTextChild.has(el)) continue;");
  });

  it("⑤二维滚动只判阅读流：容器里要有 p/li，nowrap 的控件行不算正文", () => {
    expect(census).toContain('if (!el.querySelector("p, li")) continue;');
    expect(census).toContain('if (st.whiteSpace === "nowrap") continue;');
  });

  it("⑥基线就存在的问题只记档归给别的 SC，加距才冒出来的才判红（三条判定一律先跟基线对表）", () => {
    expect(census).toContain("const freshCut = spaced.cut.filter((c) => !baseCut.has(c.key));");
    expect(census).toContain("const freshOver = spaced.over.filter((c) => !baseOver.has(c.key));");
    expect(census, "二维滚动也要归因：窄档 aside.outline-page-side 基线就得横滚 22px，那是 1.4.10 的局部滚动容器")
      .toContain("const freshTwod = spaced.twod.filter((c) => !baseTwod.has(c.key));");
    expect(census).toContain("if (freshTwod.length) {");
    expect(census).toContain("const clean = !freshCut.length && !freshOver.length && !freshTwod.length;");
    expect(census).toContain("本批不重复判红");
  });

  it("⑦两档覆盖下限都在案（宽 20 / 窄 8），判定数不足直接判红；普查不许用 scrollIntoViewIfNeeded 冒充可达", () => {
    expect(census).toContain("const SPACING_MIN = 20;");
    expect(census).toContain("const NARROW_MIN = 8;");
    expect(census).toContain("if (judged.length < SPACING_MIN) {");
    expect(census).toContain("if (tiers[540] < NARROW_MIN) {");
    expect(census, "程序能滚 ≠ 用户滚得到（BD-223 记过的同一颗坑）").not.toContain("scrollIntoViewIfNeeded(");
    expect(census).toContain("await tierPass(1360, true);");
    expect(census).toContain("await tierPass(540, false);");
    expect(census).toContain("await enterWorkbench(tier, createIfNeeded);");
    expect(census, "窄档不是顺手加的一档：没有 540 就没有顶栏那条横排的哨位").toContain("const tierPass = async (tier, createIfNeeded) => {");
    expect(census).toContain("阅读器·侧栏");
  });

  it("⑧结论只走 SPACING_OK / SPACING_RED，退出码来自判定而不是写死", () => {
    expect(census).toContain("SPACING_OK：");
    expect(census).toContain("WCAG 1.4.12");
    expect(census).toContain("process.exit(exitCode);");
    expect(census, "异常分支不许把退出码写死成 1 掩盖判定数").not.toMatch(/process\.exit\(1\)/);
  });

  it("⑨脚本登记进 package.json，守卫/构建之外还能单独跑", () => {
    expect(JSON.parse(pkg).scripts["audit:text-spacing"]).toBe("node scripts/audit-text-spacing.mjs");
  });
});
