#!/usr/bin/env node
/**
 * 选中态可见性普查（WCAG 1.4.1 Use of Color，AA · **状态维度**）。批次 BD-249 立。
 *
 * 一句话：一把「按下哪档就显示哪档」的分段切换，如果**只有颜色**在动，色觉丧失者与灰阶
 * 屏幕就无从确认「现在选的是哪个」；如果连颜色都不动，任何人都不知道按下去有没有生效。
 * SC 原文：「Color is not used as the **only** visual means of conveying information,
 * indicating an action, prompting a response, or **distinguishing a visual element**」
 * （https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html）——最后一句管的就是控件
 * 的状态。Understanding 同时给了合格答案的样子：下划线、加粗、图案、字号……即**非颜色通道**。
 *
 * 与既有那两本的分工（都量过、都不管这件事）：
 *   · scripts/audit-non-text-contrast.mjs（1.4.11）量的是「指认控件/状态的那块视觉信息
 *     对比度够不够 3:1」——它的前提是**存在**这么一块信息。本普查管的前提：存不存在。
 *     实测印证这个缺口：全仓最坏的一处（字数指标预览）形状与颜色状态通道**全为 0**，
 *     1.4.11 那本照样绿——它对「根本没有状态色块」这种情况没有分母可算。
 *   · scripts/audit-consistent-labels.mjs（3.2.4 文字维度）量的是「名字」，本普查量的是
 *     「状态看得见」；同一把切换在两本账里各自判，互不代劳。
 *   · scripts/audit-consistent-identification.mjs（1.4.1 图案维度，BD-226）量的是
 *     「同一功能是否始终用同一张图」——它自己在头注写明只管图案**一致性**，不管颜色能不能
 *     单独承担状态指认。本普查是 1.4.1 的第二支脚，两支合起来才是完整的 1.4.1。
 *
 * 判据（任一不成立即红）：
 *   【状态要看得见】同一把切换里，按下档与**每一颗**未按下档比，至少有一条**形状通道**在动。
 *   【不许只靠颜色】形状通道为 0 而颜色通道 >0 ⇒ 红「选中态只靠颜色」；
 *      形状与颜色都为 0 ⇒ 红「按下去一个像素都不动」（更严重，单列措辞）。
 *   【不许失明】全程一把可测的切换都没量到 ⇒ 红（分母为 0 的 OK 不算通过）。
 *
 * 「形状 vs 颜色」这条线怎么划（判据的命门，写清楚免得后人随手挪通道）：
 *   · 形状通道 = font-weight / font-size / text-decoration-line / 四条 border-**width** /
 *     outline-**width** / opacity / display / visibility / box-shadow 的**几何段** /
 *     border-radius / letter-spacing / 伪元素自己的宽高（下划线常写成 ::after 的 height＋底色）/
 *     子元素**数量**（选中才出现的对勾、环）。
 *   · 颜色通道 = color / background-color / border-color（**必须有宽度才画得出来**）/
 *     outline-color（同上）/ box-shadow 的**颜色段** / text-decoration-color。
 *     这条「宽度 >0 才算数」是实测逼出来的：初版把 outline-color 的差别当颜色通道，而全仓
 *     未聚焦钮的 outline-width 恒 0px——「一个画不出来的颜色变了」被算成证据，
 *     探针①（/tmp/bd249-probe1.log）于是给几乎每把切换都记了一条 oColor 差值。
 *   · 真实元素的 width/height **不参与**：档名长短本来就不一样（「大纲树」与「场景卡板」
 *     宽度天生不等），拿它当状态证据等于把标签长度当选中态。伪元素的宽高要参与，因为那是
 *     画出来的记号，与文字长短无关。
 *   · 「状态通道」的判据条件是与**所有**未按下档都不同，而不是与其中一颗不同：主题色块那颗
 *     内圈填充色（.desktop-dock-swatch）本来就是每档各自的含义（浅色底／深色底／渐变底），
 *     那是档与档自己的差别，不是「谁被选中」的证据。探针①（/tmp/bd249-probe1.log）用
 *     「与任一颗不同」＋「取差值最多的那一对」，把这条记成了状态通道，方向正好反了。
 *   · 不点击、只读渲染：本账要的比较对象是「同一把切换里被选中与没被选中的样子」，
 *     今天屏上就有（每把切换都恰好一档按下），不需要按下去。这也是它能比 3.2.4 那本跑得快、
 *     并且不会把应用按进脏状态的原因。代价：主题那种「按下去全站换色」的钮在这里点不得——
 *     一旦真按下去，未选中签名是在另一个主题下抄的，差值全被主题换色污染（探针②实测：
 *     /tmp/bd249-probe2.log 里「应用主题」的颜色差值多出 #1.bg 一整条，正是主题底色被换）。
 *
 * 清点口径：
 *   · 候选 = role=group 且有 aria-label、组内 ≥2 颗**带 aria-pressed** 的可见未禁用钮——
 *     与 audit-consistent-labels 同一形状（Tabs.tsx:36-41 刻意不用 tablist）。
 *   · 恰好一颗按下才判；按下 0 颗 ⇒ ℹ️（没有状态要指认）；按下 >1 颗 ⇒ ⚠️ 跳过（多选形状，
 *     「未按下」的定义不成立，硬判会把「两颗都粗」这种合法形状算成无差值）。
 *   · 控件按「class＋组名」去重，出现过的屏一起记名——一把全站共用的切换（主题点在 12 屏都
 *     在）只算一条红，免得同一个缺陷刷 12 行把噪声埋掉（同 3.2.4 那本的族聚合口径）。
 *
 * 摸底实测（/tmp/bd249-probe1.mjs～probe4.log；13 次站点走查、21 个控件出现点、去重后 10 把）：
 *   真缺陷 5 把（动手前）：
 *     · 「字数指标预览」(.stats-metric-switch)——**根本没写过激活态声明**，形状 0 条、颜色 0 条，
 *       全仓唯一「按下去一个像素都不动」的切换；
 *     · 「应用主题」(.desktop-dock-themes)——三颗主题点没有任何文字，选中只由环的**颜色**指认
 *       （BD-210 当年把边界从 transparent 抬成 --border-field 时就只剩这条路）；
 *     · 「写作模式切换」(.writing-mode-switch)——岛壳三胞胎里唯一没拿到字重的一支，只有底色；
 *     · 「场景信息页签」(.writing-margin-tabs)——底色＋墨色；
 *     · 「按状态筛选收件箱」(.inbox-filter-row)——铜底＋反白字＋边框色，5 条全是颜色。
 *   天生合格 5 把：快捷分类（400→500）、大纲视图切换 ×2（550→600／500→600）、卡片视图、
 *     历史视图——都靠 font-weight 这一条形状通道过关。
 *   修法：给 5 把各补一条形状通道，与既有同族口径一致——文字档统一 font-weight: 600
 *     （岛内页签、快捷分类那一族本来就是 500/600），主题点补环宽 1px→2px（全站 box-sizing
 *     border-box，外框恒 20×20、内圈 12px 居中不动；灰阶下「粗一圈」看得见）。
 *   记档不当红：550→600 这种 50 档的字重差（接管态岛壳 4438 那条 !important）在今天所有
 *     形状通道判据里都算「有」，本账不判红——判据认的是通道种类，不是数值幅度；幅度太薄
 *     这件事 ℹ️ 另账记名，留给视觉档那一批（focus-rings／spacing 棘轮）一起议。
 *
 * 干净那轮实测（产品改好后重跑）：
 * 站点 12 个｜控件出现点 21 个｜去重后 10 把｜红 0 条 → SELECTED_STATE_OK/exit 0。
 * 牙齿（运行时的每档都先还原再重建，绝不让下一档踩在脏 out/ 上；账 /tmp/bd249-teeth-runtime.log
 * ＋静态账 /tmp/bd249-teeth-static.log／bd249-teeth-static-s5b.log）：
 *   运行时 T1～T5 各摘掉一处刚补上的形状声明，每档**确实只红在被摘的那一把**，例如 T1 摘掉
 *     「字数指标预览」的声明后报「按下『字』与其余 2 档**一个像素都不动**（形状 0／颜色 0）」、
 *     T2 只让「写作模式切换」变红（形状=无｜颜色=#0.bg）、T3 让「应用主题」红且 12 屏并成一条、
 *     T4/T5 同形。注意统计口径：harness 那句「红 N 条」是 grep -c '❌ '，把「每把控件的 ❌ 摘要行」
 *     与「❌ 红判据行」都算进去了，所以单缺陷档一律显示 红 2 条、真红各 1 条。
 *   运行时 C1 把 "fw" 从形状表划掉（判据换边）⇒ 9 把全红（摘要里显示 红 18 条＝9×2）。
 *   运行时 C2 是**伪绿实证**：摘掉「字数指标预览」声明的同时把真实元素宽高塞进形状通道，
 *     census exit=0／SELECTED_STATE_OK，缺陷明明在产品里——运行时判据救不了自己，这条只能由
 *     静态牙 S3（源码账钉死 cs.width/cs.height 不许进形状表）拦住。
 *   运行时 R0/R9：改好与全部还原后都回到「站点 12 个｜出现点 21｜去重 10 把｜红 0／ℹ️ 1／跳过 0」。
 *   静态 S1～S7 逐档退一根柱子（形状表划 fw／边框色不再要求宽度／元素宽高进形状表／状态通道
 *     every 漂成 some／package.json 摘掉常驻命令／产品退掉那条字重声明／针 D 喂自己变空判），
 *     每档**恰好红 1 条**且红在点名的那条测试，S0 与 S8 复核 0 红。
 *     其中 S5 第一版把命令串整段替换成空，留下 JSON 尾逗号——vitest 连配置都加载不了
 *     （package.json:123 trailing commas），量不出红也说明不了问题；补跑 S5b 连前导逗号一起删、
 *     断言 JSON 仍合法，才拿到那 1 条真红。教训写在这：拆常驻登记的牙也要保住可解析性。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 900 xvfb-run -a --server-args="-screen 0 1600x1000x24" node scripts/audit-selected-state.mjs
 * 退出码：有红 → SELECTED_STATE_RED / exit 1；有控件被跳过 → SELECTED_STATE_SKIP / exit 2；
 * 全绿 → SELECTED_STATE_OK / exit 0；自证针没过 → SELECTED_STATE_NEEDLE_FAIL / exit 1。
 * 自证针（文末 runNeedles）：喂进正式表的**副本**、跑同一个 findings()——A 只靠颜色该红、
 * B 一个像素都不动该红、C 有形状通道不许误伤、D 全程 0 控件该报失明、E 按下 0 颗只记 ℹ️
 * 不判红。抓不到／误伤即 SELECTED_STATE_NEEDLE_FAIL / exit 1。针不许污染正式表，也不许为针
 * 另写判定条件；先看 needleSelfTest.ran 再判红（BD-226 的牙齿自检 T-C 实测过：把针体整段
 * 短路也别想全绿）。
 */
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-selected-state-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

/* ---------- 通道表（改这里等于改判据，台账会当场红） ---------- */

// 形状通道：非颜色就能认出来的属性。伪元素宽高单独算（画出来的记号，与标签长短无关）。
const SHAPE_PROPS = ["fw", "fs", "decor", "bw", "ow", "op", "disp", "vis", "shGeom", "br", "ls"];
// 颜色通道：只有眼睛认色才用得上的属性。bdc/oc 必须宽度 >0 才画得出来（见头注）。
const COLOR_PROPS = ["color", "bg", "bdc", "oc", "shCol", "tdc"];

/* ---------- 起 app ---------- */

const app = await electron.launch({
  executablePath: electronPath,
  args: [mainJs, "--disable-dev-shm-usage"],
  cwd: root,
  env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir }
});
const page = await app.firstWindow();
await page.waitForLoadState("domcontentloaded");
await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
await page.waitForTimeout(900);

const gotoSidebar = async (label) => {
  for (let attempt = 0; attempt < 3; attempt += 1) {
    await page.locator(".desktop-sidebar button", { hasText: label }).first().click();
    await page.waitForTimeout(600);
    const now = await page.evaluate(() => (document.querySelector("h1")?.textContent || "").trim());
    if (now.includes(label)) return;
    await page.waitForTimeout(700);
  }
  const now = await page.evaluate(() => (document.querySelector("h1")?.textContent || "").trim());
  throw new Error(`点侧栏「${label}」没能切到该屏（当前 h1=「${now}」）`);
};

const openWorkbench = async () => {
  await gotoSidebar("项目");
  const back = page.locator(".project-nav-back").first();
  if (await back.isVisible().catch(() => false)) await back.click();
  await page.waitForSelector(".project-home", { timeout: 15000 });
  if (await page.locator(".project-workbench").isVisible().catch(() => false)) return;
  const createFirst = page.getByRole("button", { name: "新建第一个项目" }).first();
  if (await createFirst.isVisible().catch(() => false)) await createFirst.click();
  else await page.getByRole("button", { name: /新建项目/ }).first().click();
  const wizard = page.locator("[role=dialog]").first();
  await wizard.waitFor({ state: "visible", timeout: 15000 });
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await wizard.locator("input[placeholder='例如：测试项目']").first().fill("选中态普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(1500);
};

const viewSite = async (label) => {
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const b = page.locator(".project-nav button", { hasText: label }).first();
    if (await b.isVisible().catch(() => false)) {
      await b.click();
      await page.waitForTimeout(600);
    } else {
      await page.waitForTimeout(400);
      continue;
    }
    const on = await page.evaluate((l) => {
      const cur = document.querySelector('.project-nav button[aria-current="page"]');
      return !!cur && `${cur.getAttribute("aria-label") || ""}${cur.textContent || ""}`.includes(l);
    }, label);
    if (on) return;
    await page.waitForTimeout(700);
  }
  throw new Error(`点页签「${label}」没能切到该视图`);
};

/* ---------- 采集（纯读渲染，不点击） ---------- */

const collect = () =>
  page.evaluate(
    ([SHAPE, COLOR]) => {
      const px = (v) => parseFloat(v) || 0;
      const shadowSplit = (s) => {
        if (!s || s === "none") return { geom: "none", col: "none" };
        const parts = s.split(/,(?![^(]*\))/).map((p) => {
          const nums = (p.match(/-?[\d.]+px/g) || []).join("/");
          const col = p.replace(/-?[\d.]+px/g, "").trim();
          return `${nums}\u0000${col}`;
        });
        return { geom: parts.map((x) => x.split("\u0000")[0]).join(";"), col: parts.map((x) => x.split("\u0000")[1]).join(";") };
      };
      const chans = (btn) => {
        const nodes = [btn, ...btn.querySelectorAll("*")];
        const shape = {};
        const color = {};
        for (const pseudo of [null, "::before", "::after"]) {
          nodes.forEach((n, i) => {
            const cs = getComputedStyle(n, pseudo);
            if (pseudo && (cs.content === "none" || cs.content === "" || cs.content === "normal")) return;
            const tag = `${pseudo ? pseudo : ""}#${i}`;
            const sh = shadowSplit(cs.boxShadow);
            const hasB = [cs.borderTopWidth, cs.borderRightWidth, cs.borderBottomWidth, cs.borderLeftWidth].some((v) => px(v) > 0);
            const hasO = px(cs.outlineWidth) > 0;
            const S = {
              fw: cs.fontWeight, fs: cs.fontSize, decor: cs.textDecorationLine,
              bw: [cs.borderTopWidth, cs.borderRightWidth, cs.borderBottomWidth, cs.borderLeftWidth].join("/"),
              ow: cs.outlineWidth, op: cs.opacity, disp: cs.display, vis: cs.visibility,
              shGeom: sh.geom, br: cs.borderRadius, ls: cs.letterSpacing
            };
            if (pseudo) { S.pw = cs.width; S.ph = cs.height; } // 伪元素记号的几何
            const C = {
              color: cs.color, bg: cs.backgroundColor,
              bdc: hasB ? [cs.borderTopColor, cs.borderRightColor, cs.borderBottomColor, cs.borderLeftColor].join("/") : "n/a",
              oc: hasO ? cs.outlineColor : "n/a",
              shCol: sh.col, tdc: cs.textDecorationLine === "none" ? "n/a" : cs.textDecorationColor
            };
            for (const [k, v] of Object.entries(S)) shape[`${tag}.${k}`] = v;
            for (const [k, v] of Object.entries(C)) color[`${tag}.${k}`] = v;
          });
        }
        shape["@children"] = String(nodes.length); // 选中才多出来的记号节点
        return { shape, color };
      };
      const nameOf = (b) => (b.getAttribute("aria-label") || (b.textContent || "")).trim().replace(/\s+/g, " ");
      const out = [];
      for (const host of document.querySelectorAll('[role="group"][aria-label]')) {
        const r = host.getBoundingClientRect();
        if (r.width === 0 && r.height === 0) continue;
        const btns = [...host.querySelectorAll("button")].filter((b) => b.hasAttribute("aria-pressed") && !b.disabled);
        if (btns.length < 2) continue;
        const group = (host.getAttribute("aria-label") || "").trim();
        const cls = (host.getAttribute("class") || "").split(/\s+/).filter(Boolean).slice(0, 2).join(".");
        const on = btns.filter((b) => b.getAttribute("aria-pressed") === "true");
        const off = btns.filter((b) => b.getAttribute("aria-pressed") === "false");
        if (on.length === 0) { out.push({ group, cls, n: btns.length, kind: "noPressed", labels: btns.map(nameOf) }); continue; }
        if (on.length > 1) { out.push({ group, cls, n: btns.length, kind: "multiPressed", labels: btns.map(nameOf) }); continue; }
        const a = chans(on[0]);
        const bs = off.map(chans);
        // 状态通道 = 按下档与**每一颗**未按下档都不同（与某一颗不同可能只是档自己的样子）
        const stateOf = (side) => Object.keys(a[side]).filter((k) => bs.every((b) => b[side][k] !== undefined && b[side][k] !== a[side][k]));
        const shapeKeys = stateOf("shape").filter((k) => SHAPE.includes(k.split(".").pop().split("@").pop()) || k.startsWith("::") || k === "@children");
        const colorKeys = stateOf("color").filter((k) => COLOR.includes(k.split(".").pop()));
        const fwTo = parseInt(a.shape["#0.fw"], 10);
        const fwFrom = Math.max(...bs.map((b) => parseInt(b.shape["#0.fw"], 10)));
        out.push({
          group, cls, n: btns.length, kind: "measured",
          onLabel: nameOf(on[0]), offLabels: bs.length ? off.map(nameOf) : [],
          shape: shapeKeys, color: colorKeys, fwTo, fwFrom,
          colorVals: Object.fromEntries(colorKeys.map((k) => [k, a.color[k]]))
        });
      }
      return out;
    },
    [SHAPE_PROPS, COLOR_PROPS]
  );

const records = []; // {screen, group, cls, n, kind, ...}
const siteFailures = [];

const SITES = [
  { label: "侧栏·项目", open: () => gotoSidebar("项目") },
  { label: "侧栏·卡片库", open: () => gotoSidebar("卡片库") },
  { label: "侧栏·收件箱", open: () => gotoSidebar("收件箱") },
  { label: "侧栏·书库", open: () => gotoSidebar("书库") },
  { label: "侧栏·设置", open: () => gotoSidebar("设置") },
  { label: "工作台·概览", open: () => viewSite("概览"), workbench: true },
  { label: "工作台·写作", open: () => viewSite("写作"), workbench: true },
  { label: "工作台·大纲", open: () => viewSite("大纲"), workbench: true },
  { label: "工作台·设定卡", open: () => viewSite("设定卡"), workbench: true },
  { label: "工作台·写作统计", open: () => viewSite("写作统计"), workbench: true },
  { label: "工作台·版本历史", open: () => viewSite("版本历史"), workbench: true },
  { label: "工作台·全书预览", open: () => viewSite("全书预览"), workbench: true }
];

let entered = false;
for (const site of SITES) {
  try {
    if (site.workbench && !entered) { await openWorkbench(); entered = true; }
    await site.open();
    await page.waitForTimeout(500);
    for (const r of await collect()) records.push({ screen: site.label, ...r });
  } catch (error) {
    siteFailures.push(`${site.label}（${String(error && error.message ? error.message : error).slice(0, 60)}）`);
  }
}

/* ---------- 判定（纯函数：正式表与自证针共用同一份） ---------- */

function findings(rows) {
  const reds = [];
  const infos = [];
  const skips = [];
  const byControl = new Map(); // `${cls}|${group}` → 最坏一次（形状通道最少的那次）
  for (const r of rows) {
    if (r.kind === "noPressed") { infos.push(`ℹ️ 「${r.group}」(.${r.cls}) ${r.n} 档没有任何一档被按下（${r.labels.join("／")}）——没有状态要指认，本账不判`); continue; }
    if (r.kind === "multiPressed") { skips.push(`「${r.group}」(.${r.cls}) ${r.n} 档里有几颗同时按下（${r.labels.join("／")}）——多选形状没有「未按下档」这个对照，硬判会把合法形状算成无差值`); continue; }
    const key = `${r.cls}|${r.group}`;
    const prev = byControl.get(key);
    if (!prev) byControl.set(key, { ...r, screens: [r.screen] });
    else if (r.shape.length < prev.shape.length) byControl.set(key, { ...r, screens: [...prev.screens, r.screen] });
    else prev.screens.push(r.screen);
  }
  if (byControl.size === 0) {
    reds.push(`全程一把可测的分段切换都没量到（记录 ${rows.length} 条）——本账就此失明：要么 role=group＋aria-pressed 那一族换了形状，要么走查再也没走到它们所在的屏。先跑探针确认，再决定改产品还是改口径。`);
  }
  for (const c of byControl.values()) {
    const where = `${c.screens.join("／")}·组「${c.group}」(.${c.cls})`;
    if (c.shape.length === 0) {
      if (c.color.length === 0) {
        reds.push(`${where}：按下「${c.onLabel}」与其余 ${c.n - 1} 档**一个像素都不动**（形状通道 0 条、颜色通道 0 条）——谁都不知道现在选的是哪个，按下有没有生效也看不出来`);
      } else {
        reds.push(`${where}：选中态**只靠颜色**（形状通道 0 条，只有 ${c.color.join("／")}）——1.4.1 不许颜色单独承担状态指认：色觉丧失者与灰阶屏幕分不出「${c.onLabel}」是被按下的那颗`);
      }
    } else if (c.fwTo - c.fwFrom < 100 && c.shape.length === 1 && c.shape[0].endsWith(".fw")) {
      infos.push(`ℹ️ 「${c.group}」(.${c.cls}) 唯一的形状通道是字重 ${c.fwFrom}→${c.fwTo}（差 ${c.fwTo - c.fwFrom} 档）——通道种类合格，幅度薄，记档给视觉档那一批`);
    }
  }
  return { reds, infos, skips, byControl };
}

const main = findings(records);
const { reds, infos, skips } = main;

/* ---------- 自证针 ---------- */

const needleSelfTest = { ran: false, ok: false, why: "" };
function runNeedles() {
  // 喂进正式表的**副本**、跑同一个 findings()——不许为针另写判定条件。
  const measured = records.filter((r) => r.kind === "measured");
  const copy = measured.slice();
  const base = { n: 3, offLabels: ["针一", "针二"], fwTo: 700, fwFrom: 400, colorVals: {} };
  // 针 A：只有颜色在动 ⇒ 该红（1.4.1）
  copy.push({ screen: "针A", ...base, group: "针A切换", cls: "needle-a", kind: "measured", onLabel: "针A档", shape: [], color: ["#0.color", "#0.bg"] });
  // 针 B：什么都在动吗？没有——形状与颜色都 0 条 ⇒ 该红（更严重的措辞）
  copy.push({ screen: "针B", ...base, group: "针B切换", cls: "needle-b", kind: "measured", onLabel: "针B档", shape: [], color: [] });
  // 针 C：有形状通道 ⇒ 不许误伤
  copy.push({ screen: "针C", ...base, group: "针C切换", cls: "needle-c", kind: "measured", onLabel: "针C档", shape: ["#0.fw"], color: ["#0.color"] });
  // 针 E：没有任何一档被按下 ⇒ 只记 ℹ️，不判红也不跳过
  copy.push({ screen: "针E", group: "针E切换", cls: "needle-e", n: 2, kind: "noPressed", labels: ["针E一", "针E二"] });
  const found = findings(copy);
  const a = found.reds.some((x) => x.includes("只靠颜色") && x.includes("针A切换"));
  const b = found.reds.some((x) => x.includes("一个像素都不动") && x.includes("针B切换"));
  const c = !found.reds.some((x) => x.includes("针C切换"));
  const e = !found.reds.some((x) => x.includes("针E切换")) && found.infos.some((x) => x.includes("针E切换"));
  // 针 D：失明——把正式表整张清空再喂，「一把都没量到」该响（不许直接看正式表：
  // 产品改好后正式表本来就 0 红，既证不了针会响，也证不了没失明）
  const dfound = findings([]);
  const d = dfound.reds.some((x) => x.includes("都没量到"));
  needleSelfTest.ran = true;
  needleSelfTest.ok = a && b && c && d && e;
  needleSelfTest.why = `A(只靠颜色)=${a} B(完全无差值)=${b} C(有形状不误伤)=${c} D(失明出声)=${d} E(无按下只记档)=${e}`;
}
runNeedles();

/* ---------- 结账 ---------- */

const uniq = main.byControl.size;
console.log(`选中态普查：站点 ${SITES.length} 个｜控件出现点 ${records.filter((r) => r.kind === "measured").length} 个｜去重后 ${uniq} 把`);
for (const c of main.byControl.values()) {
  console.log(`  ${c.shape.length > 0 ? "✅" : "❌"} 「${c.group}」(.${c.cls}) ${c.n} 档 ｜ ${c.screens.length} 屏 ｜ 形状=${c.shape.length ? c.shape.join(",") : "无"} ｜ 颜色=${c.color.length ? c.color.join(",") : "无"}`);
}
for (const r of reds) console.log(`  ❌ ${r}`);
for (const i of infos) console.log(`  ${i}`);
for (const s of skips) console.log(`  ⚠️ 跳过 ${s}`);
for (const s of siteFailures) console.log(`  ⚠️ 站点没走成 ${s}`);

if (!needleSelfTest.ran || !needleSelfTest.ok) {
  console.log(`SELECTED_STATE_NEEDLE_FAIL 自证针没过：${needleSelfTest.why || "针体没跑起来（把针体短路也算没过）"}`);
  await app.close();
  process.exit(1);
}
if (reds.length > 0) {
  console.log("SELECTED_STATE_RED 选中态只靠颜色，或者根本看不见");
  await app.close();
  process.exit(1);
}
if (skips.length > 0 || siteFailures.length > 0) {
  console.log(`SELECTED_STATE_SKIP 有 ${skips.length + siteFailures.length} 项被跳过`);
  await app.close();
  process.exit(2);
}
console.log("SELECTED_STATE_OK 每把切换的选中态都有不靠颜色也认得出的形状");
await app.close();
