// @vitest-environment node
/**
 * BD-222 账本：窄视口回流（WCAG 1.4.10）普查判据 + 本批修复锚。
 *
 * 运行时普查 scripts/audit-reflow.mjs 是主判据（两档 × 13 视图）；这里把两侧钉住：
 * ① 判据骨架不许被改松（页级溢出、被裁内容、两个豁免、覆盖下限、两档都要跑、脚本要登记）；
 * ② 本批五处产品修复的落点与发射序——CSS 的胜者只由「同键 + 更晚」决定，媒体查询不加
 *    特异度，把 override 写在被压那条之前就是死声明（BD-29/34 记过的发射序）。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const styles = read("../../styles.css");
const editorial = read("../../styles/editorial-studio.css");
const census = read("../../../scripts/audit-reflow.mjs");
const pkg = read("../../../package.json");
const stripComments = (s: string) => s.replace(/\/\*[\s\S]*?\*\//g, "");

/** 取某条规则的声明体（注释已摘，避免把判据注释当成声明）。 */
function ruleBody(css: string, needle: string, from = 0): string {
  const at = css.indexOf(needle, from);
  expect(at, `找不到规则选择器「${needle}」`).toBeGreaterThan(-1);
  const open = css.indexOf("{", at);
  const close = css.indexOf("}", open);
  return stripComments(css.slice(open + 1, close)).trim();
}

describe("BD-222 产品修复锚", () => {
  it("①body 地板改成按视口条件挂回：视口够宽/高才有 800×680，裸硬地板与百分比写法都不许回潮", () => {
    // 竖向那条同批改的理由（实测）：窗口下限 minHeight 720 ÷ 缩放上限 200% = 360 CSS px 高，
    // 而 body{overflow:hidden} 的 overflow 会按 CSS 传播规则顶到视口——硬地板 680 在这一档
    // 让文档比窗口高出 320px 且用户滚不动（只有程序能滚），下面三屏直接不可达。
    // 撤掉地板后 .desktop-page-scroll（overflow-y:auto）接手：实测 680×430 档它的
    // scrollHeight 602 / clientHeight 322，内容回到页面自己的滚动宿主里可达。
    // 写法为什么不用 min(800px, 100%)：功能等价，但 1.4.4 普查里 200% 档路由 chunk 的
    // file:// 请求 6/6 红（net::ERR_FAILED → 整页落到错误边界），换成下面两条媒体档后
    // 同一条普查 2/2 绿——条件留在媒体档里，不给根元素引百分比循环尺寸。
    const body = ruleBody(styles, "body {");
    expect(body, "body 又出现裸 800px 硬地板（1.4.10 老根）").not.toMatch(/min-width:/);
    expect(body, "body 又出现裸 680px 硬地板（短窗竖向不可达）").not.toMatch(/min-height:/);
    expect(body, "body 不许回潮成 min()/百分比地板（见上面的 1.4.4 记录）").not.toMatch(/min\(/);
    expect(body, "overflow:hidden 仍在，竖向可达性由 .desktop-page-scroll 接手（见上面注释）").toContain("overflow: hidden;");
    const wide = styles.indexOf("@media (min-width: 800px) {");
    const tall = styles.indexOf("@media (min-height: 680px) {");
    expect(wide, "视口够宽时的 800px 地板档丢了").toBeGreaterThan(styles.indexOf("body {"));
    expect(tall, "视口够高时的 680px 地板档丢了").toBeGreaterThan(wide);
    expect(styles.slice(wide, wide + 120)).toContain("min-width: 800px;");
    expect(styles.slice(tall, tall + 120)).toContain("min-height: 680px;");
  });

  it("②布脊两行与设定卡工具组都允许换行", () => {
    expect(ruleBody(styles, ".writing-manuscript-head {")).toContain("flex-wrap: wrap;");
    expect(ruleBody(styles, ".writing-head-actions {")).toContain("flex-wrap: wrap;");
    expect(ruleBody(styles, ".cards-toolbar-left,\n.cards-toolbar-right {")).toContain("flex-wrap: wrap;");
  });

  it("③写作台折叠右栏档：470px 地板仍在，≤1024 的收缩档必须写在它之后才压得住", () => {
    const floor = editorial.indexOf("grid-template-columns: 192px minmax(470px, 1fr);");
    expect(floor, "宽档的 192px／470px 两列档被改动").toBeGreaterThan(-1);
    const override = editorial.lastIndexOf("grid-template-columns: 166px minmax(0, 1fr);");
    expect(override, "折叠态窄档收缩声明丢了").toBeGreaterThan(floor);
    expect(editorial.slice(override - 260, override), "折叠态窄档没写在 @media ≤1024 里").toContain("@media (max-width: 1024px)");
  });

  it("④收件箱 ≤760 改上下堆叠，列表分隔线随向换边", () => {
    const stack = editorial.indexOf("@media (max-width: 760px)");
    expect(stack, "收件箱窄档堆叠块丢了").toBeGreaterThan(-1);
    const grid = editorial.indexOf(".inbox-editor-grid {", stack);
    expect(grid, "堆叠块里没有 .inbox-editor-grid").toBeGreaterThan(stack);
    expect(editorial.slice(grid, grid + 200)).toContain("grid-template-columns: minmax(0, 1fr);");
    expect(editorial.slice(grid, grid + 200)).toContain("grid-template-rows: minmax(0, 1fr) minmax(0, 1.4fr);");
    const list = editorial.indexOf(".inbox-list {", grid);
    expect(list, "堆叠块里没有 .inbox-list 的换边").toBeGreaterThan(grid);
    expect(editorial.slice(list, list + 200)).toContain("border-right: 0;");
    expect(editorial.slice(list, list + 200)).toContain("border-bottom: 1px solid var(--border-subtle);");
  });

  it("⑤书库表面板横向可滚：控件不许被 overflow:hidden 裁掉", () => {
    const panel = ruleBody(styles, ".desktop-library-panel {");
    expect(panel).toContain("overflow-x: auto;");
    expect(panel).toContain("overflow-y: hidden;");
    expect(panel, "面板整条 overflow 又合回一个 hidden").not.toMatch(/overflow:\s*hidden\s*;/);
  });
});

describe("BD-222 普查判据骨架", () => {
  it("①两档都跑，540 的来源（最小窗口 ÷ 缩放上限）写死在判据里", () => {
    expect(census).toContain("await setTier(540);");
    expect(census).toContain("await setTier(1360);");
    expect(census).toContain("minWidth 1080");
    expect(census).toContain("ZOOM_MAX 2");
    expect(census).toContain("WCAG 1.4.10");
  });
  it("②页级溢出与被裁内容各判一条红，覆盖下限 12×2", () => {
    expect(census).toContain("if (d.pageScrollBy > 1) {");
    expect(census).toContain("if (d.clipped.length > 0) {");
    expect(census).toMatch(/if \(judged\.length < REFLOW_MIN \* 2\)/);
    expect(census).toMatch(/const REFLOW_MIN = 12;/);
  });
  it("③三个豁免都在：SVG 内部图形、hidden 祖先要真切在框外、同名只报一条", () => {
    expect(census).toContain('el.tagName.toLowerCase() !== "svg"');
    expect(census).toContain("if (r.right <= p.getBoundingClientRect().right + 1) continue;");
    expect(census).toContain("if (seen.has(name)) continue;");
  });
  it("④内层横滚只记档不判红", () => {
    expect(census).toContain('if (/(auto|scroll)/.test(st.overflowX) && host.scrollWidth > host.clientWidth + 1) {');
    expect(census).toContain("notes.push(`");
  });
  it("⑤脚本登记进 package.json，守卫/构建之外还能单独跑", () => {
    expect(JSON.parse(pkg).scripts["audit:reflow"]).toBe("node scripts/audit-reflow.mjs");
  });
});

describe("BD-222 记档：1.4.4 普查遇到路由 chunk 加载失败时的容错口径", () => {
  const zoom = read("../../../scripts/audit-text-zoom.mjs");
  it("⑥重试后必须把缩放按回 200%，判定数下限与实报数都不许放松", () => {
    // 沙箱现场：200% 档里 file:// 的懒加载 chunk 请求偶发 net::ERR_FAILED，整页落到错误边界。
    // 普查允许用用户自己的「重试当前页面」再走一次，但重试后必须重新按回 200%——否则判定的
    // 就不是 200% 那一档；两次都不成才算跳过，跳过仍由 judged < 8 这条覆盖下限判红。
    expect(zoom).toContain('name: "重试当前页面"');
    expect(zoom).toContain("重试后缩放为");
    expect(zoom).toContain("if (judged < 8)");
    expect(zoom).toContain("200% 下判定 ${judged} 个视图");
    expect(zoom, "结论文案不许回潮成写死的 10 个视图").not.toContain("200% 下 10 个视图");
  });
});
