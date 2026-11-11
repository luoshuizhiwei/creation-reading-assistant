// @vitest-environment node
/**
 * BD-251 源码账：大纲编辑控件的焦点交接普查（WCAG 2.4.3）的口径与产品那条交接钩子。
 *
 * 这本账读文件，不读 DOM——DOM 那半边由 scripts/audit-edit-focus.mjs 在真 app 里量。
 * 两边各钉一件事：
 *   · 「握得住才算测」「动作要真发生」「按可用过滤挑控件」「进站点前先清弹层」这四条是
 *     本账的判据命门，每一条都是实测踩出来的（见普查账本头的「普查自己踩的两件事」）。
 *     谁把它们简化回去，本账当场红。
 *   · 产品那条交接（useEditFocusHandoff）不许悄悄退回去：普查会红，但红在每日账里；
 *     本账把「为什么与 BD-220 的删除交接是两件事、为什么要重试阶梯」钉在文件上。
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const at = (rel: string) => readFileSync(path.resolve(process.cwd(), rel), "utf8");
const census = at("scripts/audit-edit-focus.mjs");
const pkg = at("package.json");
const hook = at("src/features/creation/outline/use-edit-focus-handoff.ts");
const tree = at("src/features/creation/outline/OutlineTree.tsx");
const board = at("src/features/creation/outline/CardBoard.tsx");

/** 只取代码行：注释里要留下教训原文，不算「引用了那条写法」。 */
const codeOf = (text: string) =>
  text
    .split("\n")
    .filter((line) => !line.trim().startsWith("//") && !line.trim().startsWith("*") && !line.trim().startsWith("/*"))
    .join("\n");

describe("BD-251 普查常驻与口径", () => {
  it("命令登记在 package.json（想当一次性脚本？不算常驻）", () => {
    expect(pkg).toContain('"audit:edit-focus": "node scripts/audit-edit-focus.mjs"');
  });

  it("站点 10 个：大纲树 7 ＋ 场景卡板 3，两个视图都要量", () => {
    const block = census.slice(census.indexOf("const SITES = ["), census.indexOf("/* ---------- 结构种子"));
    const labels = [...block.matchAll(/label: "([^"]+)"/g)].map((m) => m[1]);
    expect(labels).toEqual([
      "树·下移卷",
      "树·上移章节",
      "树·下移章节",
      "树·上移场景",
      "树·下移场景",
      "树·章节状态下拉",
      "树·章节编号下拉",
      "卡板·章节状态下拉",
      "卡板·上移场景",
      "卡板·下移场景"
    ]);
    expect(labels.filter((l) => l.startsWith("树·")).length).toBe(7);
    expect(labels.filter((l) => l.startsWith("卡板·")).length).toBe(3);
  });

  it("判据①：握住之后必须确认 activeElement 就是它，握不住记 unfocused（不算绿）", () => {
    expect(census).toContain("focused: document.activeElement === el");
    expect(census).toContain('if (!held.focused) return { site: site.label, level: "unfocused"');
  });

  it("判据②：动作要真发生——按钮比排序签名、下拉比值，没变记 unproven", () => {
    expect(census).toContain("const acted = after !== before;");
    expect(census).toContain('level: "unproven", why: "激活前后排序／值都没变');
    // 每个按钮站点都得有排序签名来源，否则「按了没反应」会被读成「焦点没动＝合规」
    const block = census.slice(census.indexOf("const SITES = ["), census.indexOf("/* ---------- 结构种子"));
    const buttonRows = block.split("\n").filter((line) => line.includes('kind: "button"'));
    expect(buttonRows.length).toBe(7);
    for (const row of buttonRows) expect(row, `按钮站点缺 orderSel：${row}`).toContain("orderSel:");
  });

  it("弹层接走先判、不判红：编号那族「先弹预览、确认才写入」，值当然还没变", () => {
    const body = codeOf(census.slice(census.indexOf("const measureSite"), census.indexOf("/* ---------- 判定表")));
    expect(body.indexOf('level: "dialog"')).toBeLessThan(body.indexOf('level: "unproven", why: "激活前后'));
  });

  it("挑控件按「可用」过滤，不许按序号硬取（首项上移／末项下移本来就 disabled）", () => {
    expect(census).toContain("const pickEnabled = async (site) => {");
    expect(census).toContain("if (await el.evaluate((node) => node.disabled === true)) continue;");
    const body = codeOf(census.slice(census.indexOf("const measureSite"), census.indexOf("/* ---------- 判定表")));
    expect(body, "不许绕过 pickEnabled 直接 nth 取控件").toContain("const picked = await pickEnabled(site);");
  });

  it("进站点前先清弹层：影响预览的 z-[80] 遮罩会把下一站的页签整个挡住", () => {
    expect(census).toContain("const closeOverlays = async () => {");
    const openView = census.slice(census.indexOf("const openView ="), census.indexOf("// 事务落定"));
    expect(openView.indexOf("await closeOverlays();")).toBeGreaterThanOrEqual(0);
    expect(openView.indexOf("await closeOverlays();")).toBeLessThan(openView.indexOf(".click({ timeout: 8000 })"));
  });

  it("判据③：落定后 activeElement 是 body ⇒ 红；同容器内另一颗控件也算合规", () => {
    expect(census).toContain('if (late.atBody) return { site: site.label, level: "body"');
    expect(census).toContain('if (late.inContainer) return { site: site.label, level: "ok"');
    expect(census).toContain('if (r.level === "body") rows.push({ level: "red"');
  });

  it("判据④：0 站点 ⇒ 失明红；缺席／握不住／空跑一律跳过，绝不算绿", () => {
    expect(census).toContain('if (!measured) rows.push({ level: "red"');
    const verdictBody = census.slice(census.indexOf("function verdicts("), census.indexOf("/* ---------- 走查"));
    expect(verdictBody).toContain('else rows.push({ level: "skip"');
    expect(verdictBody, "不许把 absent/unfocused/unproven 归到 info").not.toContain('"absent") rows.push({ level: "info"');
  });

  it("自证针 9 条，且与正式判定共用同一个 verdicts()", () => {
    const needles = census.slice(census.indexOf("function runNeedles()"));
    expect((needles.match(/results\.push\(\["/g) ?? []).length).toBe(9);
    expect((needles.match(/verdicts\(/g) ?? []).length).toBeGreaterThanOrEqual(9);
    expect(census).toContain("EDIT_FOCUS_NEEDLE_FAIL");
    expect(census).toContain("EDIT_FOCUS_RED");
    expect(census).toContain("EDIT_FOCUS_SKIP");
    expect(census).toContain("EDIT_FOCUS_OK");
  });

  it("账本头留着红基线数字与两条量法教训（后人别把它们抹平成「一路绿」）", () => {
    expect(census).toContain("站点 10 个｜实际测到 10 个｜焦点跌回 body 6 处｜留得住 3 处｜弹层接走 1 处");
    expect(census).toContain("普查自己踩的两件事");
    expect(census).toContain("按序号硬取控件");
    expect(census).toContain("z-[80]");
    expect(census).toContain("站点 10 个｜实际测到 10 个｜焦点跌回 body 0 处｜留得住 9 处｜弹层接走 1 处");
    expect(census).toContain("只盯 busy 不够");
  });

  it("账本头留着牙的记录与两条牙架教训（假绿是怎么来的，不许抹掉）", () => {
    expect(census).toContain("· T1 两处 focus 调用换成 void");
    expect(census).toContain("· C2 按钮改成空按");
    expect(census).toContain("· C5 判定表把 body 归到 info");
    expect(census).toContain("本轮牙架自己踩的两件事");
    expect(census).toContain("BUILD_FAIL + census SKIPPED");
    expect(census).toContain("S0 基线必须先绿");
  });
});

describe("BD-251 产品侧：编辑控件的焦点交接", () => {
  it("交接钩子只在焦点真的落在 body 时才动手（不抢用户挪走的焦点、不碰弹层）", () => {
    expect(hook).toContain("const atBody = () => {");
    expect(hook).toContain("return !active || active === document.body || active === document.documentElement;");
    expect(hook).toContain("if (!atBody()) {");
  });

  it("交接口径：原控件 → 同簇可用控件 → 本行主控件 → 容器", () => {
    expect(hook).toContain("if (focusable(held)) {");
    expect(hook).toContain('Array.from(cluster.querySelectorAll<HTMLElement>("button, select, input")).find(focusable)');
    expect(hook).toContain("const row = held.closest(rowSelector);");
    // 类名不许抄进钩子文件：守卫按「提到类名的模块」登记宿主，凭空多一个够不着
    // outline-reorg.css 的宿主会把挂载覆盖前提判失效（注入电池 BD-40 那颗牙实测哑掉）。
    expect(hook, "钩子文件里不许出现块级表的类名").not.toContain(".card-board-card");
    expect(hook).toContain("rowSelector: string");
    expect(hook).toContain("const target = sibling ?? (focusable(rowMain) ? rowMain : null) ?? container;");
  });

  it("重试阶梯还在：busy 与结构刷新两条异步线谁先落地不定，禁用没解除就不交给邻居", () => {
    expect(hook).toContain("const HANDOFF_ATTEMPTS_MS = [0, 100, 300, 800, 1500];");
    expect(hook).toContain("if (index === HANDOFF_ATTEMPTS_MS.length - 1) handOff();");
    expect(hook).toContain("return () => timers.forEach(clearTimeout);");
    // 只接一次不够：busy 之后还有一次结构刷新会把刚接回去的焦点再撞掉（green2 实测）。
    expect(hook).toContain("const HANDOFF_WINDOW_MS = 4000;");
    expect(hook).toContain("if (Date.now() - pending.at > HANDOFF_WINDOW_MS) {");
    expect(hook).toContain("settled: readonly unknown[],");
  });

  it("三条输入路都要登记：键盘、指针、以及只发 change 的 select 改值", () => {
    expect(hook).toContain("onChangeCapture: capture");
    expect(hook).toContain("onKeyDownCapture: capture");
    expect(hook).toContain("onPointerDownCapture: capture");
    expect(hook).toContain('if (!active.matches("button, select, input, textarea")) return;');
  });

  it("大纲树与卡板都接上了钩子，且落在各自的容器上", () => {
    expect(tree).toContain('import { useEditFocusHandoff } from "./use-edit-focus-handoff";');
    expect(tree).toContain('const editFocusHandoff = useEditFocusHandoff(treeRef, [outline], "li");');
    expect(tree).toContain('<div className="outline-tree" ref={treeRef} tabIndex={-1} {...editFocusHandoff}>');
    expect(board).toContain('import { useEditFocusHandoff } from "./use-edit-focus-handoff";');
    expect(board).toContain('const editFocusHandoff = useEditFocusHandoff(boardRef, [outline, busy], ".card-board-card, .card-board-group");');
    expect(board).toContain('<div className="card-board" ref={boardRef} {...editFocusHandoff}>');
  });

  it("BD-220 的删除交接没被本批顶掉（两件事各管一头）", () => {
    expect(tree).toContain("pendingDeleteFocusRef");
    expect(tree).toContain('button[data-outline-node]');
    expect(hook).toContain("与 BD-220 的删除交接正相反");
  });
});