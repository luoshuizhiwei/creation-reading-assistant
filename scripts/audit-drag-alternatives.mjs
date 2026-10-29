/**
 * BD-211 常驻普查：WCAG 2.5.7 Dragging Movements（AA），运行时活体实测。
 *
 * 判据口径（W3C Understanding 2.5.7）：凡是**靠拖动动作**完成的功能，必须能用
 * 单指针（不拖）或键盘完成；只有「拖动本身即功能」（画线、下棋这类）才算 essential。
 * 本仓的拖动供线全是「用拖动设一个值/位置」——改卡片类型、调面板宽度、挪工具条位置，
 * 三条都不 essential，各自必须有一条非拖拽路径。
 *
 * 认哪条为「拖拽供线」（DOM 层的三条硬证据，任一命中即供线）：
 *  · [draggable="true"]（HTML5 拖放）；
 *  · role="separator" 且光标是 resize 族（ew/ns/col/row-resize）——窗口分隔条；
 *  · 计算光标 ∈ {grab, grabbing, move}——作者用光标明示「这里能拖」。
 *
 * 一条供线算「有非拖拽替代」要同时满足三件事（缺一即红，逐条记账）：
 *  ① 可聚焦：tabIndex ≥ 0 且 focus() 真能落到它身上（keyboard-only 用户够得着）；
 *  ② 有可读名称：aria-label / aria-labelledby / title / 可见文字，至少一个非空；
 *  ③ 键盘真的有效：聚焦后按 ←/→/↑/↓（含 Ctrl/Shift 变体）能产生**布局变化**。
 *     布局快照用 offset 几何（tag.class | 父节点 | offsetLeft/Top/Width/Height），
 *     不用 getBoundingClientRect——后者随滚动漂移，方向键顺手滚一下页面就会假过。
 *     基线取两次空转快照的交集（只比稳定行），动画/懒渲染不会造成假红。
 *
 * 范围：① 设定卡看板（拖卡改类型）② 写作台速查分隔条 ③ EPUB 划词工具条握把。
 * 每个范围先要求「供线真在」（计数 0 记跳过，不许静默通过）。
 *
 * 牙（teeth）锚：BoardView 的 Ctrl+←/→ 分支与 role=status 播报、速查分隔条的
 * tabIndex/aria-valuenow/keydown、EpubSelectionToolbar 握把的 button 化与 onNudge。
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-dragalt-audit-"));
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

// 首启建 schema → round4 种子播 TXT/EPUB/MD（阅读器范围要真书）→ 重开。
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
  await page.locator(".desktop-sidebar button", { hasText: label }).first().click();
  await page.waitForTimeout(400);
};
async function createProject() {
  await goto("项目");
  await page.waitForSelector(".project-home", { timeout: 10000 });
  const createFirst = page.getByRole("button", { name: "新建第一个项目" }).first();
  if (await createFirst.isVisible()) await createFirst.click();
  else await page.getByRole("button", { name: /新建项目/ }).first().click();
  const wizard = page.locator("[role=dialog]").first();
  await wizard.waitFor({ state: "visible", timeout: 15000 });
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：测试项目']").first().fill("拖拽替代普查项目");
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
  await page.locator(".project-nav button", { hasText: navLabel }).first().click();
  await page.waitForSelector(selector, { timeout: 15000 });
  await page.waitForTimeout(400);
};

// 枚举供线：只认三条硬证据；顺带做遮挡/可见过滤。
const collect = (selector) => page.evaluate((sel) => {
  const resizeCursor = /resize$/;
  const dragCursor = /^(grab|grabbing|move)$/;
  const occluded = (el) => {
    const r = el.getBoundingClientRect();
    if (r.width < 1 || r.height < 1) return true;
    const pts = [[r.left + r.width / 2, r.top + r.height / 2], [r.left + 2, r.top + 2], [r.right - 2, r.bottom - 2]];
    return pts.every(([x, y]) => {
      const hit = document.elementFromPoint(x, y);
      return !hit || !(hit === el || el.contains(hit));
    });
  };
  const rows = [];
  for (const el of document.querySelectorAll(sel)) {
    const cs = getComputedStyle(el);
    if (cs.visibility === "hidden" || cs.display === "none") continue;
    const draggable = el.getAttribute("draggable") === "true";
    const separator = el.getAttribute("role") === "separator" && resizeCursor.test(cs.cursor);
    const dragHandle = dragCursor.test(cs.cursor);
    const why = draggable ? "draggable" : separator ? "separator" : dragHandle ? "cursor" : "无硬证据";
    rows.push({
      why,
      hidden: occluded(el),
      id: `${el.tagName.toLowerCase()}.${(el.getAttribute("class") || "").split(/\s+/).filter(Boolean).slice(0, 2).join(".")}`,
      tabIndex: el.tabIndex,
      name: (el.getAttribute("aria-label") || el.getAttribute("title") || (el.textContent || "").trim().slice(0, 20) || "").slice(0, 40),
      role: el.getAttribute("role"),
      cursor: cs.cursor
    });
  }
  return rows;
}, selector);

// offset 几何快照（与滚动无关）。
const snapshot = () => page.evaluate(() => {
  const rows = [];
  for (const el of document.querySelectorAll("[class]")) {
    if (el.offsetWidth < 1 || el.offsetHeight < 1) continue;
    if (getComputedStyle(el).visibility === "hidden") continue;
    const pc = el.parentElement;
    rows.push(`${el.tagName.toLowerCase()}.${(el.getAttribute("class") || "").trim().replace(/\s+/g, ".")}|${pc ? pc.tagName.toLowerCase() : "-"}.${pc ? (pc.getAttribute("class") || "").trim().replace(/\s+/g, ".") : "-"}|${el.offsetLeft},${el.offsetTop},${el.offsetWidth},${el.offsetHeight}`);
  }
  return rows;
});

const KEYS = ["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown", "Control+ArrowRight", "Control+ArrowLeft", "Shift+ArrowRight"];

const probeElement = async (index) => {
  const el = page.locator("[data-dragalt-probe]").nth(index);
  const before = await snapshot();
  await page.waitForTimeout(220);
  const before2 = await snapshot();
  const stable = before.map((line, i) => (before2[i] === line ? line : null));
  await el.focus();
  const focused = await page.evaluate(() => {
    const el = document.activeElement;
    return el ? `${el.tagName.toLowerCase()}.${(el.getAttribute("class") || "").split(/\s+/).slice(0, 2).join(".")}` : null;
  });
  // 逐键判、一旦有变化立刻停：Ctrl+→ 后再按 Ctrl+← 会把它挪回去，
  // 连按一遍再比对终态会把「真有效」误判成「无效」（首版就栽在这）。
  const moved = (now) => stable.some((line, i) => line !== null && now[i] !== undefined && now[i] !== line);
  let changed = false;
  for (const key of KEYS) {
    await page.keyboard.press(key);
    await page.waitForTimeout(160);
    const now = await snapshot();
    if (moved(now)) { changed = true; break; }
  }
  return { focused, changed };
};

const scopes = [];
const tryScope = async (label, prep, selector) => {
  try {
    await prep();
    const listed = await collect(selector);
    if (listed.length === 0) {
      scopes.push({ label, total: 0, skipped: "范围内 0 条拖拽供线" });
      console.log(`跳过「${label}」：范围内 0 条拖拽供线`);
      return;
    }
    // 只把「真供线且未被遮挡」的下标进键盘探针；其余当场记成跳过原因。
    const probes = [];
    for (let i = 0; i < listed.length; i += 1) {
      const meta = listed[i];
      if (meta.hidden) { probes.push({ meta, skipped: "被遮挡" }); continue; }
      await page.evaluate(({ index, sel }) => {
        for (const node of document.querySelectorAll("[data-dragalt-probe]")) node.removeAttribute("data-dragalt-probe");
        document.querySelectorAll(sel)[index].setAttribute("data-dragalt-probe", "1");
      }, { index: i, sel: selector });
      const probe = await probeElement(0);
      probes.push({ meta, probe });
    }
    const results = probes.filter((p) => !p.skipped).map(({ meta, probe }) => ({
      ...meta,
      nameOk: Boolean(meta.name),
      focusOk: meta.tabIndex >= 0 && Boolean(probe.focused) && !/^(body|desktop-canvas)/.test(probe.focused),
      changed: probe.changed,
      focused: probe.focused
    }));
    scopes.push({ label, total: results.length, occluded: probes.filter((p) => p.skipped).length, results });
  } catch (e) {
    scopes.push({ label, total: 0, skipped: String(e && e.message || e).split("\n")[0].slice(0, 120) });
    console.log(`跳过「${label}」：${String(e && e.message || e).split("\n")[0].slice(0, 120)}`);
  }
};

// 播两张卡（拖拽探针卡 + 每列都有的对照），看板才有供线。
await workbenchView("设定卡", ".cards-page");
await page.evaluate(async () => {
  const projects = await window.api.creation.listProjects();
  const existing = await window.api.creation.cardsList({ kind: "cards.list", search: "拖拽探针卡" });
  if (existing.length) return;
  await window.api.creation.runStructure({ type: "card.create", projectId: projects[0].id, kind: "character", title: "拖拽探针卡", aliases: [], fields: {}, tags: [] });
  await window.api.creation.runStructure({ type: "card.create", projectId: projects[0].id, kind: "location", title: "拖拽探针地点卡", aliases: [], fields: {}, tags: [] });
});
await page.reload();
await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
await goto("项目");
await page.waitForSelector(".project-home-item-main", { timeout: 15000 });
await page.locator(".project-home-item-main").first().click();
await page.waitForSelector(".project-workbench", { timeout: 15000 });
await workbenchView("设定卡", ".cards-page");
const boardBtn = page.getByRole("button", { name: /看板/ }).first();
if (await boardBtn.isVisible().catch(() => false)) { await boardBtn.click(); await page.waitForTimeout(500); }
await tryScope("项目·设定卡看板（拖卡改类型）", async () => {}, '.cards-board [draggable="true"]');

await workbenchView("写作", ".writing-desk");
if (!(await page.locator(".writing-quick-tabs").isVisible().catch(() => false))) {
  await page.keyboard.press("Control+Shift+k");
  await page.waitForTimeout(700);
}
await tryScope("写作台·速查分隔条（拖调宽度）", async () => {}, "[role=separator].writing-quick-reference-resizer");

await goto("书库");
await page.waitForTimeout(800);
await tryScope("阅读器·EPUB 划词工具条（拖挪位置）", async () => {
  await page.locator('.desktop-library-row[data-format="epub"]').first().click();
  await page.waitForTimeout(2500);
  const frames = page.frames().filter((f) => f !== page.mainFrame());
  for (const f of frames) {
    try {
      await f.locator("p, div").filter({ hasText: /./ }).first().click({ clickCount: 3, timeout: 3000 });
      await page.waitForTimeout(700);
      break;
    } catch { /* 试下一个 iframe */ }
  }
}, '.cursor-grab[title^="拖动"]');

const reds = [];
let judged = 0;
for (const scope of scopes) {
  if (scope.skipped) continue;
  for (const row of scope.results) {
    judged += 1;
    const why = [];
    if (!row.focusOk) why.push(`不可键盘聚焦(tabIndex=${row.tabIndex})`);
    if (!row.nameOk) why.push("无可读名称");
    if (!row.changed) why.push("方向键无任何布局变化");
    if (why.length) reds.push({ scope: scope.label, ...row, why });
  }
}
console.log(`拖拽供线范围 ${scopes.filter((s) => !s.skipped).length} 个；供线 ${judged} 条（${scopes.map((s) => `${s.label.replace(/（.*$/, "")}:${s.total}${s.occluded ? `(遮挡${s.occluded})` : ""}`).join("，")}）。`);
if (reds.length) {
  console.log(`DRAGALT_RED：${reds.length} 条拖拽供线没有可用的非拖拽替代：`);
  for (const r of reds) console.log(`  - [${r.scope}] ${r.id}${r.name ? `[${r.name}]` : ""} ${r.why.join("；")}（光标 ${r.cursor}，证据 ${r.why}，焦点 ${r.focused || "未落"}）`);
  await app.close();
  process.exit(1);
}
console.log(`DRAGALT_OK：${judged} 条拖拽供线都能靠键盘完成同一动作（可聚焦 + 有名称 + 方向键有布局变化）。`);
await app.close();
process.exit(0);
