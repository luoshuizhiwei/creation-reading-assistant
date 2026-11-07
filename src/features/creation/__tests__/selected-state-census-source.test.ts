// @vitest-environment node
/**
 * BD-249 源码账：选中态普查（1.4.1 状态维度）的口径与产品形状声明。
 *
 * 这本账读的是文件，不是 DOM——DOM 那半边由 scripts/audit-selected-state.mjs 在真 app 里量。
 * 两边各钉一件事：
 *   · 「形状 vs 颜色」这两张通道表是判据的命门。谁把 font-weight 挪进颜色表、或者把
 *     outline-color 挪进形状表，判据当场反向；这类改动纸面上「只是调表」，实际是把 1.4.1
 *     的判定权换了人，必须先动本账。
 *   · 五处产品声明里那条形状通道不许悄悄退回去（普查会红，但红在每日账里；本账把「当初
 *     为什么补这一笔」钉在文件上，改的人一眼看得见理由）。
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const census = readFileSync(path.resolve(process.cwd(), "scripts/audit-selected-state.mjs"), "utf8");
const pkg = readFileSync(path.resolve(process.cwd(), "package.json"), "utf8");
const styles = readFileSync(path.resolve(process.cwd(), "src/styles.css"), "utf8");
const editorial = readFileSync(path.resolve(process.cwd(), "src/styles/editorial-studio.css"), "utf8");
const radar = readFileSync(path.resolve(process.cwd(), "src/features/creation/editor/scene-radar.css"), "utf8");
const inbox = readFileSync(path.resolve(process.cwd(), "src/features/creation/inbox/inbox-local.css"), "utf8");

/** 取某条选择器的声明块（只看第一条命中；选择器写成 contains 以免被格式化弄漂）。 */
function ruleBlock(src: string, file: string, selector: string): string {
  const at = src.indexOf(selector);
  expect(at, `${file} 里找不到选择器「${selector}」——宿主漂了普查就量不到这条`).toBeGreaterThanOrEqual(0);
  const open = src.indexOf("{", at);
  const close = src.indexOf("}", open);
  expect(close, `${file} 里「${selector}」没有闭合的声明块`).toBeGreaterThan(open);
  return src.slice(open + 1, close);
}

describe("BD-249 普查常驻与口径", () => {
  it("命令登记在 package.json（想当一次性脚本？不算常驻）", () => {
    expect(pkg).toContain('"audit:selected-state": "node scripts/audit-selected-state.mjs"');
  });

  it("两张通道表钉死：形状认这些属性，颜色认那些属性", () => {
    expect(census).toContain('const SHAPE_PROPS = ["fw", "fs", "decor", "bw", "ow", "op", "disp", "vis", "shGeom", "br", "ls"];');
    expect(census).toContain('const COLOR_PROPS = ["color", "bg", "bdc", "oc", "shCol", "tdc"];');
  });

  it("画不出来的颜色不算证据：边框色要有宽度、outline 色要有宽度", () => {
    expect(census).toContain(
      "const hasB = [cs.borderTopWidth, cs.borderRightWidth, cs.borderBottomWidth, cs.borderLeftWidth].some((v) => px(v) > 0);"
    );
    expect(census).toContain("const hasO = px(cs.outlineWidth) > 0;");
    expect(census).toContain("bdc: hasB ?");
    expect(census).toContain("oc: hasO ?");
  });

  it("真实元素的宽高不参与形状通道，伪元素的记号几何才参与", () => {
    expect(census).toContain("if (pseudo) { S.pw = cs.width; S.ph = cs.height; }");
    const shapeBlock = census.slice(census.indexOf("const S = {"), census.indexOf("if (pseudo) { S.pw"));
    expect(shapeBlock, "形状通道里冒出了元素宽高——档名长短会被当成选中态证据").not.toMatch(/cs\.width|cs\.height/);
  });

  it("状态通道的定义是「与每一颗未按下档都不同」", () => {
    expect(census).toContain(
      "Object.keys(a[side]).filter((k) => bs.every((b) => b[side][k] !== undefined && b[side][k] !== a[side][k]))"
    );
  });

  it("三种红与一条失明、一条薄差档都要按名字钉住", () => {
    expect(census).toContain("选中态**只靠颜色**");
    expect(census).toContain("**一个像素都不动**");
    expect(census).toContain("全程一把可测的分段切换都没量到");
    expect(census).toContain("唯一的形状通道是字重");
    expect(census).toContain("SELECTED_STATE_OK");
    expect(census).toContain("SELECTED_STATE_RED");
    expect(census).toContain("SELECTED_STATE_SKIP");
    expect(census).toContain("SELECTED_STATE_NEEDLE_FAIL");
  });

  it("按下 0 颗只记 ℹ️、按下多颗算跳过：两种非标准形状不许混进红", () => {
    expect(census).toContain('kind: "noPressed"');
    expect(census).toContain('kind: "multiPressed"');
    expect(census).toContain("没有状态要指认，本账不判");
    expect(census).toContain("多选形状没有「未按下档」这个对照");
  });

  it("去重按「class＋组名」，一把全站共用的切换只算一条", () => {
    expect(census).toContain("const key = `${r.cls}|${r.group}`;");
    expect(census).toContain("byControl.set(key, { ...r, screens: [r.screen] });");
  });

  it("自证针五支各管一件事，短路任何一支都别想全绿", () => {
    expect(census).toContain('needleSelfTest.ok = a && b && c && d && e;');
    expect(census).toContain('const a = found.reds.some((x) => x.includes("只靠颜色") && x.includes("针A切换"));');
    expect(census).toContain('const b = found.reds.some((x) => x.includes("一个像素都不动") && x.includes("针B切换"));');
    expect(census).toContain('const c = !found.reds.some((x) => x.includes("针C切换"));');
    expect(census).toContain("const dfound = findings([]);");
    expect(census).toContain('const e = !found.reds.some((x) => x.includes("针E切换")) && found.infos.some((x) => x.includes("针E切换"));');
  });

  it("头注留下的干净那轮数字要跟着站点表走（文档漂了就当没测过）", () => {
    const sites = (census.match(/\{ label: "/g) ?? []).length;
    expect(sites, "SITES 表条数看着不对").toBeGreaterThanOrEqual(10);
    expect(census, "头注的站点数与 SITES 表不符").toContain(`站点 ${sites} 个｜控件出现点`);
    expect(census, "头注要留下干净那轮的出处").toContain("干净那轮实测");
  });
});

describe("BD-249 产品形状声明（五处补上的非颜色通道）", () => {
  it("主题点：环宽 1px→2px——全仓唯一没有文字的切换，只能靠几何", () => {
    const block = ruleBlock(editorial, "editorial-studio.css", ".desktop-dock-dot.active");
    expect(block).toContain("border-width: 2px");
  });

  it("岛壳三胞胎：激活档字重 600（写作模式切换先前一条形状通道都没有）", () => {
    const block = ruleBlock(editorial, "editorial-studio.css", ".writing-mode-switch button.active");
    expect(block).toContain("font-weight: 600");
  });

  it("场景信息页签：激活档字重 600", () => {
    const block = ruleBlock(radar, "scene-radar.css", ".writing-margin-tabs button.active");
    expect(block).toContain("font-weight: 600");
  });

  it("收件箱状态药丸：激活档字重 600（原先只有铜底＋反白）", () => {
    const block = ruleBlock(inbox, "inbox-local.css", ".inbox-filter-chip.active");
    expect(block).toContain("font-weight: 600");
  });

  it("字数指标预览：这条激活态声明 BD-248 之前根本不存在", () => {
    const block = ruleBlock(styles, "styles.css", ".stats-metric-switch button.active");
    expect(block).toContain("font-weight: 600");
    expect(block).toContain("var(--bg-surface)");
  });
});
