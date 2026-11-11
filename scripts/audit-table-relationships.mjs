#!/usr/bin/env node
/**
 * 表格关系普查（WCAG 1.3.1 Info and Relationships，A · **表格行列对应维度**）。批次 BD-254 立。
 *
 * 一句话：一张逐列对齐的记录表，读屏用户听到的应当是「本地路径：xxx」这种**列名带着值**，
 * 而不是一串平铺文字。SC 原文：「Information, structure, and relationships conveyed through
 * presentation can be programmatically determined or are available in text」
 * （https://www.w3.org/WAI/WCAG22/Understanding/info-and-relationships.html）。
 * 本账只管一件事：**每一行的单元格与表头列对得上**。对不上的时候，AT 会把值念到错误的
 * 列名底下——比不念更糟。
 *
 * 与既有三本的分工：
 *   · scripts/audit-list-structure.mjs（1.3.1 **列表腿**，BD-190 系列）管 ul/ol/li 的从属；
 *     表格不是列表，那本账的分母里没有 role=table。
 *   · scripts/audit-landmarks.mjs（1.3.1 区域腿）管页面级地标。
 *   · scripts/audit-heading-order.mjs（1.3.1 标题层级腿）管 h1…h6 的级差。
 *   · BD-178 把书库那张 div 拼接的记录表补上了 ARIA 表格角色（列头 6 个、数据行 6 格），
 *     但它没管**零结果那一行**：那一行只有 1 个单元格，却坐在 6 个列头底下。
 *
 * 量具自己踩的一件事（如实记档）：筛选词存在共享 store 里、跨屏不清，上一站筛成零结果后
 * 下一站「有数据」等不到数据行（/tmp/bd254-red.log 两站 absent）⇒ 每站进站先清筛选。
 *
 * 判据（任一条不成立即红）：
 *   【①有表头】整张表至少要有列头（th / role=columnheader）或行头（role=rowheader / tbody th）。
 *     一个都没有 ⇒ 红：这张「表」在 AT 里只是一堆格子，没有任何对应关系可言。
 *   【②列数对齐】每一行的**有效列数**（单元格数，按 aria-colspan / colspan 累加）必须等于
 *     表头列数。只有行头、没有列头的表（如恢复弹层的对象级差异表）以首行宽度为准。
 *   【③不许空行】某行一个单元格都没有 ⇒ 红（role=row 里空无一物，AT 报一个空行）。
 *   【④画得出才算测】表格矩形 <2px 记 invisible ⇒ 跳过，不算绿（BD-252/253 的老教训）。
 *   【⑤表里要有行】0 行记 unproven ⇒ 跳过 + 整本 exit 2：空表当然「对得上」，那是假绿。
 *   【⑥不许失明】一个站点都没测到 ⇒ 红。站点枚举为 0 记 absent ⇒ 跳过，不算合规。
 *   colspan 解析用「取不到/取到 0/取到 NaN 都当 1」的写法（针 J 钉着）：把 aria-colspan
 *   写成 "0" 或乱码时，如果 parseFloat 直接吃进去，一行会被算成 0 列而错判成空行，
 *   真正的错（列数不对）反而被掩盖。
 *
 * 清点口径：站点 5 个 = 书库那张 ARIA 表的两个挂载点 × 两种状态（有数据 / 筛选零结果）
 * ＋ 版本历史「从快照恢复」弹层里的对象级差异表（只有行头的负对照）。
 * 书库数据由 scripts/round4-seed.cjs 在一次性 profile 里现造（与 2.5.3 那本账同一套种子），
 * 绝不碰真实数据；零结果态用一个不可能匹配的书名筛出来，不删任何书。
 *
 * 摸底（产品未改那轮，账 /tmp/bd254-red2.log；摸底探针 /tmp/bd254d-probe.log ＋ /tmp/bd254e2.log）：
 * 站点 5 个｜实际测到 5 个｜关系问题 2 处｜对得上 3 处 ⇒ TABLE_REL_RED/exit 1。
 *   ❌ 书库·筛选零结果：表头 6 列，第 2 行只占 1 列
 *      （「没有匹配搜索结果的书。试试调整筛选条件。」）⇒ 这句话被念成「格式」列的值。
 *      同一支代码在「资料阅读」屏再挂一次，同形。源码：LibraryPage.tsx 的空态行。
 *   ✅ 书库·有数据 / 资料阅读·有数据：4 行、6 列头、每行 6 格。
 *   ✅ 版本历史·恢复弹层差异表：1 行、0 列头、1 行头、3 格 —— 只有行头的表按首行宽度
 *      为准，对得上；它证明本账不是「凡无列头必红」。
 *
 * 修法（只加一个属性）：给空态那个单元格补 aria-colspan={6}，让它横跨全部 6 列。
 * 为什么不把空态行搬出表格：那会改掉 display:grid 的列轨与整块面板的视觉（空态文案现在
 * 正好落在表格内居中），而 1.3.1 要的是关系正确、不是 DOM 重排。为什么不改 role：
 * 它确实是这张表的一行（表头仍在屏上），降成 presentation 会让 AT 少报一行、
 * 与屏上看到的行数不一致。为什么不写死 colspan 而是 aria-colspan：这是 ARIA 表格
 * （div + role），HTML 的 colspan 属性在 role=cell 上不生效。
 *
 * 干净那轮实测（账 /tmp/bd254-green.log）：站点 5 个｜实际测到 5 个｜关系问题 0 处 ⇒ exit 0。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 900 xvfb-run -a --server-args="-screen 0 1600x1000x24" node scripts/audit-table-relationships.mjs
 *
 * 牙（运行时账 /tmp/bd254-teeth-runtime.log；静态账 /tmp/bd254-teeth-static.log）：
 * 每档跑完立刻逐字节还原**并重建**；R0／R9 都回到「5 站｜测到 5｜关系问题 0」。
 *   · T1 摘掉 aria-colspan ⇒ 两处零结果站红，与红基线同形（exit 1）。
 *   · T2 aria-colspan 改成 5（跨不满）⇒ 两处仍红：证明判据比的是数，不是「有没有写」。
 *   · T3 把一个数据行的某格 role 摘掉 ⇒ 有数据两站红（分站点有效）。
 *   · T4 摘掉全部列头的 role ⇒ 书库/资料阅读四站红（①有表头）。
 *   · T5 把差异表的行头 role 摘掉 ⇒ 只咬差异表一站（①对只有行头的表同样成立）。
 *   · T6 判据反接（列数相等才判红）⇒ 五站全红。
 *   · T7 把页内读数改成「0 行」（空表）⇒ 五站全 unproven、测到 0 ⇒ 先报失明红 exit 1、跳过 5。
 *   · T8 把「有表头」那条删掉 ＋ 摘列头 ⇒ 站点仍红（列数对不上），但自证针 C 拦下。
 *   · T9 把 colspan 解析改成裸 parseFloat（0 就当 0）＋ aria-colspan={0} ⇒ 针 J 拦下（NEEDLE_FAIL）。
 *   · 静态 S1…S20 逐条改坏口径/站点表/判据/针/修法，源码账必须逐档红，S0/S99 绿。
 * 牙架自己的两处勘误（如实记档，账 /tmp/bd254-teeth-runtime.log 与 -runtime2.log）：
 * T3 首轮 splice 摘 role 时漏补了标签的 `>`、T9 首轮把 aria-colspan 写成字符串 "0"
 * （TSX 里这个属性只收 number），两档都在构建阶段就失败 ⇒ 按规矩**本档作废**、
 * 绝不让普查踩在脏 out/ 上；修好 splice 后补跑，T3 咬 2 站、T9 被针 J 拦下，均如期。
 */import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-table-rel-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

const launch = async () => {
  const app = await electron.launch({ executablePath: electronPath, args: [mainJs], cwd: root, env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir } });
  const page = await app.firstWindow();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
  await page.waitForTimeout(600);
  return { app, page };
};

{ const { app } = await launch(); await app.close(); }
const seed = spawnSync(electronPath, [path.join(__dirname, "round4-seed.cjs"), profileDir], {
  cwd: root,
  encoding: "utf8",
  env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") },
  windowsHide: true,
  timeout: 60000
});
if (seed.status !== 0) throw new Error(`seed failed: ${seed.stderr}`);
const { app, page } = await launch();

const goto = async (label) => {
  // 浮层范围会把遮罩留在屏上，先 Esc 关掉再走侧栏（Esc 在页面级视图里是无害的）。
  await page.keyboard.press("Escape").catch(() => {});
  await page.locator(".desktop-sidebar button", { hasText: label }).first().click();
  await page.waitForTimeout(400);
};
async function createProject() {
  await goto("项目");
  const back = page.locator(".project-nav-back").first();
  if (await back.isVisible().catch(() => false)) await back.click();
  await page.waitForSelector(".project-home", { timeout: 10000 });
  const createFirst = page.getByRole("button", { name: "新建第一个项目" }).first();
  if (await createFirst.isVisible()) await createFirst.click();
  else await page.getByRole("button", { name: /新建项目/ }).first().click();
  const wizard = page.locator("[role=dialog]").first();
  await wizard.waitFor({ state: "visible", timeout: 15000 });
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：测试项目']").first().fill("表格关系普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(1200);
}
const inWorkbench = async () => {
  if (await page.locator(".project-workbench").isVisible().catch(() => false)) return;
  await createProject();
};
const workbenchView = async (navLabel, selector) => {
  await inWorkbench();
  await page.keyboard.press("Escape").catch(() => {});
  await page.locator(".project-nav button", { hasText: navLabel }).first().click();
  await page.waitForSelector(selector, { timeout: 15000 });
  await page.waitForTimeout(400);
};

/* ---------- 站点表（改这里等于改分母，静态账钉着条数与口径） ---------- */

const LIBRARY_TABLE = '[role="table"][aria-label="书库书籍"]';
const DIFF_TABLE = ".history-diff-table";
const NO_MATCH = "普查不可能匹配的书名XYZ";

const SITES = [
  { label: "书库·有数据（负对照）", sel: LIBRARY_TABLE, enter: "library" },
  { label: "书库·筛选零结果", sel: LIBRARY_TABLE, enter: "libraryZero" },
  { label: "资料阅读·有数据（负对照）", sel: LIBRARY_TABLE, enter: "reading" },
  { label: "资料阅读·筛选零结果", sel: LIBRARY_TABLE, enter: "readingZero" },
  { label: "版本历史·恢复弹层差异表（只有行头的负对照）", sel: DIFF_TABLE, enter: "restoreDialog" }
];

const filterToZero = async () => {
  const input = page.locator('input[aria-label="筛选书籍"]').first();
  await input.fill(NO_MATCH);
  await page.waitForTimeout(1000);
  // 零结果态要真进去：数据行必须清空，否则这一站量到的还是「有数据」那张表。
  const rows = await page.locator(".desktop-library-row").count();
  if (rows !== 0) throw new Error(`筛选零结果没进去（还剩 ${rows} 行数据）`);
};

// 筛选词存在共享 store 里，跨屏不清：上一站筛成零结果后，下一站「有数据」会等不到
// 数据行（红基线首轮 /tmp/bd254-red.log 两站 absent 就是这个）。进站先清筛选。
const clearFilter = async () => {
  const input = page.locator('input[aria-label="筛选书籍"]').first();
  if (await input.count()) { await input.fill(""); await page.waitForTimeout(900); }
};

const ENTER = {
  library: async () => {
    await goto("书库");
    await clearFilter();
    await page.waitForSelector(".desktop-library-row", { timeout: 15000 });
    await page.waitForTimeout(600);
  },
  libraryZero: async () => {
    await goto("书库");
    await clearFilter();
    await page.waitForSelector(".desktop-library-row", { timeout: 15000 });
    await filterToZero();
  },
  reading: async () => {
    await goto("资料阅读");
    await clearFilter();
    await page.waitForSelector(".desktop-library-row", { timeout: 15000 });
    await page.waitForTimeout(600);
  },
  readingZero: async () => {
    await goto("资料阅读");
    await clearFilter();
    await page.waitForSelector(".desktop-library-row", { timeout: 15000 });
    await filterToZero();
  },
  restoreDialog: async () => {
    await workbenchView("版本历史", ".history-page");
    const tab = page.locator(".history-page button", { hasText: "版本快照" }).first();
    if (await tab.count()) { await tab.click(); await page.waitForTimeout(900); }
    // 现造一个命名里程碑，再打开「从快照恢复」——差异表只在那个弹层里存在。
    const milestone = page.locator("button", { hasText: "创建里程碑" }).first();
    if ((await milestone.count()) === 0 || !(await milestone.isEnabled().catch(() => false))) {
      throw new Error("「创建里程碑」不可用，差异表站点建不起来");
    }
    await milestone.click();
    await page.waitForTimeout(1000);
    const dialog = page.locator("[role=dialog]").first();
    await dialog.locator("#history-subject-type").selectOption("scene");
    await page.waitForTimeout(800);
    const options = await dialog.locator("#history-subject-id option").allTextContents();
    if (options.length < 2) throw new Error("里程碑没有可选对象（场景种子没建出来）");
    await dialog.locator("#history-subject-id").selectOption({ index: 1 });
    await dialog.locator("#history-reason").fill("表格关系普查里程碑");
    await dialog.locator("button", { hasText: "创建里程碑" }).first().click();
    await page.waitForTimeout(2200);
    await page.keyboard.press("Escape");
    await page.waitForTimeout(900);
    const restore = page.locator(".history-page button", { hasText: "恢复" }).first();
    if ((await restore.count()) === 0) throw new Error("快照列表里没有「恢复」钮");
    await restore.click();
    await page.waitForTimeout(2400);
    if ((await page.locator(DIFF_TABLE).count()) === 0) throw new Error("恢复弹层里没有对象级差异表");
  }
};

/* ---------- 页内量具：只取原始读数，判定全在 node 侧（好让针喂同一把尺子） ---------- */

const readTable = (sel) => page.evaluate((selector) => {
  const t = document.querySelector(selector);
  if (!t) return null;
  const rect = t.getBoundingClientRect();
  const painted = rect.width > 1 && rect.height > 1 && !t.closest('[aria-hidden="true"]');
  const owns = (row) => row.closest('table, [role="table"], [role="grid"], [role="treegrid"]') === t;
  const rows = Array.from(t.querySelectorAll('[role="row"], tr')).filter(owns);
  const CELL = ':scope > [role="cell"], :scope > [role="gridcell"], :scope > td, :scope > th, :scope > [role="columnheader"], :scope > [role="rowheader"]';
  return {
    painted,
    colHeaders: t.querySelectorAll('th, [role="columnheader"]').length,
    rowHeaders: t.querySelectorAll('[role="rowheader"], tbody th').length,
    rows: rows.map((row) => ({
      text: (row.textContent || "").replace(/\s+/g, " ").trim().slice(0, 40),
      spans: Array.from(row.querySelectorAll(CELL)).map((c) => c.getAttribute("aria-colspan") || c.getAttribute("colspan") || "")
    }))
  };
}, sel);

/* ---------- 判据（正式表与针共用同一个 spanOf()/widthOf()/levelOf()/verdicts()） ---------- */

// 取不到 / 取到 0 / 取到乱码都当 1 列：裸 parseFloat 会把 "0" 吃成 0 列，
// 于是「列数不对」被错报成「空行」，真正的缺陷被掩盖（针 J）。
function spanOf(raw) {
  const n = Number.parseInt(String(raw ?? ""), 10);
  return Number.isFinite(n) && n > 0 ? n : 1;
}

function widthOf(row) {
  return (row.spans ?? []).reduce((sum, raw) => sum + spanOf(raw), 0);
}

function levelOf(site, obs) {
  if (!obs) return { level: "absent", why: "表格枚举为 0（站点缺席）" };
  if (!obs.painted) return { level: "invisible", why: "表格没画出来（矩形 ≤1px 或被 aria-hidden 藏着）" };
  const rows = obs.rows ?? [];
  if (rows.length === 0) return { level: "unproven", why: "表里一行都没有，无从判断行列对应" };
  if (!obs.colHeaders && !obs.rowHeaders) {
    return { level: "noheader", why: "整张表没有任何表头单元格（列头 0、行头 0）" };
  }
  const expect = obs.colHeaders || widthOf(rows[0]);
  const bad = [];
  rows.forEach((row, index) => {
    const width = widthOf(row);
    if (width === 0) bad.push(`第${index + 1}行一个单元格都没有：「${row.text}」`);
    else if (width !== expect) bad.push(`第${index + 1}行占 ${width} 列，表头 ${expect} 列：「${row.text}」`);
  });
  if (bad.length) return { level: "mismatch", why: bad.join("；"), bad, expect };
  return {
    level: "ok",
    why: `${rows.length} 行全部对得上（${obs.colHeaders ? `列头 ${obs.colHeaders} 列` : `只有行头 ${obs.rowHeaders} 个，按首行 ${expect} 格为准`}）`,
    expect
  };
}

/* ---------- 逐站测量 ---------- */

const measureSite = async (site) => {
  await ENTER[site.enter]();
  const obs = await readTable(site.sel);
  const verdict = levelOf(site, obs);
  return {
    site: site.label,
    ...verdict,
    trace: obs ? `行 ${obs.rows.length}｜列头 ${obs.colHeaders}｜行头 ${obs.rowHeaders}` : "表缺席"
  };
};

/* ---------- 判定表（正式表与针共用同一个 verdicts()） ---------- */

function verdicts(records, measured) {
  const rows = [];
  if (!measured) rows.push({ level: "red", text: "一个站点都没测到（测到 0 个）——普查失明，分母为 0 的「绿」不算通过" });
  for (const r of records) {
    if (r.level === "mismatch") rows.push({ level: "red", text: `${r.site}：${r.why}（1.3.1：值会被念到错误的列名底下）` });
    else if (r.level === "noheader") rows.push({ level: "red", text: `${r.site}：${r.why}（1.3.1：这张表在 AT 里没有任何对应关系）` });
    else if (r.level === "ok") rows.push({ level: "info", text: `${r.site}：${r.why}` });
    else rows.push({ level: "skip", text: `${r.site}：${r.why}——不计绿也不计红` });
  }
  return rows;
}

/* ---------- 走查 ---------- */

console.log("表格关系普查（1.3.1 表格行列对应维度）——每一行的格子与表头列对不对得上");
const records = [];
let measured = 0;
let seeded = false;
try {
  await createProject();
  seeded = true;
} catch (error) {
  console.log(`  ❌ 种子没建齐：${error.message.split("\n")[0]}`);
}
if (seeded) {
  for (const site of SITES) {
    try {
      const record = await measureSite(site);
      records.push(record);
      if (["ok", "mismatch", "noheader"].includes(record.level)) measured += 1;
      const mark = record.level === "ok" ? "✅" : ["mismatch", "noheader"].includes(record.level) ? "❌" : "⚠️";
      console.log(`  ${mark} ${record.site}：${record.why}`);
      console.log(`      ${record.trace}`);
    } catch (error) {
      records.push({ site: site.label, level: "absent", why: `测量抛错：${error.message.split("\n")[0].slice(0, 80)}` });
      console.log(`  ⚠️ ${site.label}：测量抛错 ${error.message.split("\n")[0].slice(0, 80)}`);
    }
  }
}
await app.close();
rmSync(profileDir, { recursive: true, force: true });

const main = verdicts(records, measured);
const reds = main.filter((r) => r.level === "red");
const infos = main.filter((r) => r.level === "info");
const skips = main.filter((r) => r.level === "skip");
const count = (level) => records.filter((r) => r.level === level).length;
console.log(`\n普查站点 ${SITES.length} 个｜实际测到 ${measured} 个｜关系问题 ${count("mismatch") + count("noheader")} 处｜对得上 ${count("ok")} 处`);
console.log(`红 ${reds.length} 条／ℹ️ ${infos.length} 条／跳过 ${skips.length} 条`);
for (const r of reds) console.log(`  ❌ ${r.text}`);
for (const r of infos) console.log(`  ℹ️ ${r.text}`);
for (const r of skips) console.log(`  ⚠️ ${r.text}`);

/* ---------- 自证针（喂进同一个 levelOf() + verdicts()，不另写判定条件） ---------- */

function runNeedles() {
  const SITE = { label: "针" };
  const row = (n, span = "") => ({ text: `第 ${n} 格行`, spans: Array.from({ length: n }, () => span) });
  const table = (patch = {}) => ({ painted: true, colHeaders: 6, rowHeaders: 0, rows: [row(6), row(6)], ...patch });
  const judge = (obs) => verdicts([{ site: "针", ...levelOf(SITE, obs) }], 1);
  const has = (rows, level) => rows.some((r) => r.level === level);
  const results = [];

  // A 零结果行只占 1 列（本批的真实红）⇒ 该红
  const a = judge(table({ rows: [row(6), row(1)] }));
  results.push(["A 列数对不上该红", has(a, "red")]);
  // B 空态行横跨 6 列（本批的修法）⇒ 不许红
  const b = judge(table({ rows: [row(6), { text: "没有匹配搜索结果的书。", spans: ["6"] }] }));
  results.push(["B colspan 跨满不许误伤", !has(b, "red") && has(b, "info")]);
  // C 一个表头都没有 ⇒ 该红
  const c = judge(table({ colHeaders: 0, rowHeaders: 0 }));
  results.push(["C 没有任何表头该红", has(c, "red")]);
  // D 只有行头、各行格数一致（差异表那支）⇒ 不许红
  const d = judge(table({ colHeaders: 0, rowHeaders: 1, rows: [row(3), row(3)] }));
  results.push(["D 只有行头且一致不许误伤", !has(d, "red") && has(d, "info")]);
  // E 某行一个格子都没有 ⇒ 该红
  const e = judge(table({ rows: [row(6), row(0)] }));
  results.push(["E 空行该红", has(e, "red")]);
  // F 表画不出来 ⇒ 不许算绿，也不许当成关系问题
  const f = judge(table({ painted: false, rows: [row(6), row(1)] }));
  results.push(["F 画不出来不许算绿", !has(f, "info") && !has(f, "red") && has(f, "skip")]);
  // G 表里 0 行 ⇒ 不许算绿（空表当然对得上）
  const g = judge(table({ rows: [] }));
  results.push(["G 空表不许算绿", !has(g, "info") && !has(g, "red") && has(g, "skip")]);
  // H 一个站点都没测到 ⇒ 失明该红
  results.push(["H 测到 0 个站点该报失明", has(verdicts([], 0), "red")]);
  // I 全部对得上 ⇒ 全绿
  const i = verdicts(SITES.map((s) => ({ site: s.label, level: "ok", why: "对得上" })), SITES.length);
  results.push(["I 全部对得上该全绿", !has(i, "red") && !has(i, "skip") && i.length === SITES.length]);
  // J colspan 写成 "0" / 乱码 ⇒ 当 1 列，于是「列数不对」照旧红（不许被算成空行而掩盖）
  const j = judge(table({ rows: [row(6), { text: "零结果", spans: ["0"] }] }));
  const j2 = judge(table({ rows: [row(6), { text: "零结果", spans: ["乱码"] }] }));
  results.push(["J 坏 colspan 当 1 列且仍报列数不对", has(j, "red") && has(j2, "red") && spanOf("0") === 1 && spanOf("乱码") === 1 && spanOf("") === 1]);
  // K 只有行头但各行格数不一致 ⇒ 该红（②对行头表同样成立）
  const k = judge(table({ colHeaders: 0, rowHeaders: 1, rows: [row(3), row(2)] }));
  results.push(["K 行头表格数不一致该红", has(k, "red")]);
  // L 表缺席 ⇒ 跳过，不算合规
  const l = judge(null);
  results.push(["L 表缺席不许算绿", !has(l, "info") && has(l, "skip")]);
  return results;
}

const needleResults = runNeedles();
const needleOk = needleResults.every(([, ok]) => ok);
needleResults.forEach(([name, ok]) => console.log(`  针 ${name}：${ok ? "过" : "**没过**"}`));
console.log(`needleSelfTest.ran=${needleResults.length} passed=${needleOk}`);

if (!needleOk) {
  console.log("TABLE_REL_NEEDLE_FAIL 自证针没过，本轮判定不可信");
  process.exit(1);
}
if (reds.length) {
  console.log("TABLE_REL_RED 表格行列对应不上，或者普查失明");
  process.exit(1);
}
if (skips.length) {
  console.log("TABLE_REL_SKIP 有站点没测成（画不出来／空表／缺席）");
  process.exit(2);
}
console.log("TABLE_REL_OK 每一张表的每一行都与表头列对得上");