/**
 * 焦点遮挡普查（WCAG 2.4.11 Focus Not Obscured (Minimum)，AA）。批次 BD-209 立。
 *
 * 判据（W3C Understanding 2.4.11 原文口径）：键盘焦点指示**整块**被其他内容挡住
 * 才红——部分被盖不违规（AA 只禁 fully obscured）；「用户自己移动焦点落点时
 * 带出的遮挡」（如滚动跟随）不判，本脚本的 Tab 停靠点都是页面自己的布局遮挡。
 * 每个停靠点判两件事：
 *  ① 视口裁剪后焦点矩形 <1px（完全在视口外）；
 *  ② 可见矩形上 6 个采样点（横向 0.15/0.5/0.85 × 纵向 0.3/0.7）**全部**被
 *    「与自身矩形相交的外来元素」命中——命中自身或自身后代即算可见点。
 * 红名单打印遮挡源 CSS 路径供人工裁定；采样可能漏掉细长的一条可见缝，
 * 所以方向性上「有可见点就放行」是对的，「全被盖才报」宁缺毋滥。
 *
 * 隐形代理豁免（BD-209 摸底裁决）：自绘控件常把可见焦点环画在包装层上
 * （向导模板卡 `input.sr-only` + `.creation-template-card:focus-within` 轮廓），
 * input 自身矩形 1×1，量它的矩形毫无意义——2.4.11 管的是**焦点指示**不被遮挡，
 * 指示在包装层。矩形任一边 <2px 的停靠点豁免计数，不参与红名单。
 *
 * 遍历起点：模态弹层（向导、全局搜索、TXT/EPUB 设置抽屉）打开时焦点由
 * BD-130 契约落进弹层内部，sweep 用 noBlur 从当前焦点起步——若先 blur 再 Tab，
 * 会从 body 走进弹层背后的页面停靠点，测的是「没弹层时的世界」，与场景不符。
 * 非模态的速查面板（正文输入必须自由）与目录/浮层从 blur 起步走全页。
 *
 * 跳过：iframe / :disabled / [inert] / tabindex=-1 / display:none /
 * visibility:hidden / opacity:0——与 audit-target-size（BD-208）同一族口径。
 * 为什么全程运行时量（BD-205 遗产）：遮挡是渲染事实，静态级联推演两头误判。
 * 2.5.8（指针可达）用全域 DOM + 遮挡过滤；2.4.11 管的是**键盘停靠点**，
 * 所以这里走真 Tab 遍历——不在 Tab 序列里的被盖元素不是本条款的事。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 900 xvfb-run -a --server-args="-screen 0 1600x1000x24" node scripts/audit-focus-obscured.mjs
 *
 * 书库种子与范围名册复用 audit-target-size / audit-focus-rings 套路；
 * BD209_DEBUG=1 打印有遮挡源参与判定的停靠点逐条明细。
 *
 * 台账：scripts/audit-focus-obscured.test.mjs 钉住判据形状与修复锚点。
 * 牙齿检查（BD-209）：撤抽屉的 BD-130 接线或速查面板的 margin inert，
 * 必须恰好红回摸底那批停靠点（取证 /tmp/bd209-probe.json：抽屉 14、速查 4）。
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-focusobscured-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

const launch = async () => {
  const app = await electron.launch({
    executablePath: electronPath,
    args: [mainJs],
    cwd: root,
    env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir }
  });
  const page = await app.firstWindow();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
  await page.waitForTimeout(600);
  return { app, page };
};

// 阅读器范围要真书——首启建 schema，再用 round4 种子播 TXT/EPUB/MD，然后重开。
{
  const { app } = await launch();
  await app.close();
}
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
const inWorkbench = async () => {
  if (await page.locator(".project-workbench").isVisible().catch(() => false)) return;
  await goto("项目");
  if (await page.locator(".project-workbench").isVisible().catch(() => false)) return;
  await page.locator(".project-home-item-main").first().click();
  await page.waitForSelector(".project-workbench", { timeout: 15000 });
};
const workbenchView = async (navLabel, selector) => {
  await inWorkbench();
  await page.locator(".project-nav button", { hasText: navLabel }).first().click();
  await page.waitForSelector(selector, { timeout: 15000 });
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
  // 向导是模态弹层：BD-130 已把焦点落进弹层，noBlur 从弹层内起步。
  await sweep("弹层·新建项目向导", { maxStops: 30, noBlur: true });
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：测试项目']").first().fill("焦点遮挡普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(1200);
}

/** 页面内单停靠点判定：视口裁剪 + 6 点采样 + 外来相交遮挡源归因。 */
const inspectObscure = () => {
  const el = document.activeElement;
  if (!el || el === document.body) return null;
  const tag = el.tagName.toLowerCase();
  if (tag === "iframe" || el.matches(":disabled") || el.closest("[inert]") || el.matches("[tabindex='-1']")) return null;
  const cs = getComputedStyle(el);
  if (cs.display === "none" || cs.visibility === "hidden" || Number(cs.opacity) === 0) return null;
  const r = el.getBoundingClientRect();
  const cls = (el.getAttribute("class") || "").split(/\s+/).filter(Boolean);
  const id = `${tag}.${cls.join(".") || "(无类名)"}[${el.getAttribute("aria-label") || el.textContent?.trim().slice(0, 12) || ""}]`;
  // 隐形代理豁免：1×1 级 sr-only input 的焦点指示画在包装层上，量它自己没意义。
  if (r.width < 2 || r.height < 2) return { id, exemptTiny: true };
  const vw = window.innerWidth, vh = window.innerHeight;
  const x2 = Math.min(r.right, vw), y2 = Math.min(r.bottom, vh);
  const vis = { x: Math.max(r.left, 0), y: Math.max(r.top, 0), w: x2 - Math.max(r.left, 0), h: y2 - Math.max(r.top, 0) };
  const outOfViewport = r.width < 1 || r.height < 1 || vis.w < 1 || vis.h < 1 ||
    r.bottom <= 0 || r.top >= vh || r.right <= 0 || r.left >= vw;
  const pathOf = (n) => {
    const bits = [];
    let cur = n;
    for (let i = 0; i < 4 && cur && cur !== document.body; i += 1, cur = cur.parentElement) {
      const c = (cur.getAttribute("class") || "").trim().split(/\s+/).slice(0, 3).join(".");
      bits.unshift(`${cur.tagName.toLowerCase()}${c ? "." + c : ""}`);
    }
    return bits.join(">");
  };
  const pts = [];
  if (vis.w >= 1 && vis.h >= 1) {
    for (const fx of [0.15, 0.5, 0.85]) for (const fy of [0.3, 0.7]) pts.push([vis.x + vis.w * fx, vis.y + vis.h * fy]);
  }
  const occ = new Map();
  let clearHits = 0;
  for (const [px, py] of pts) {
    const h = document.elementFromPoint(px, py);
    if (!h) continue;
    if (h === el || el.contains(h)) { clearHits += 1; continue; }
    const hr = h.getBoundingClientRect();
    const ix = Math.min(hr.right, r.right) - Math.max(hr.left, r.left);
    const iy = Math.min(hr.bottom, r.bottom) - Math.max(hr.top, r.top);
    if (ix > 0.5 && iy > 0.5) occ.set(pathOf(h), (occ.get(pathOf(h)) || 0) + 1);
  }
  return { id, w: Math.round(r.width), h: Math.round(r.height), x: Math.round(r.x), y: Math.round(r.y), outOfViewport, samples: pts.length, clearHits, occluders: [...occ.entries()].sort((a, b) => b[1] - a[1]).slice(0, 3) };
};

const findings = [];
const seen = new Set();
let totalStops = 0;
let totalTiny = 0;

async function sweep(label, { maxStops = 120, noBlur = false } = {}) {
  if (!noBlur) {
    await page.evaluate(() => document.activeElement?.blur?.());
    await page.waitForTimeout(120);
  }
  const seenL = new Map();
  let stuck = 0, last = null;
  for (let i = 0; i < maxStops; i += 1) {
    await page.keyboard.press("Tab");
    await page.waitForTimeout(40);
    const info = await page.evaluate(inspectObscure);
    if (!info) { stuck += 1; if (stuck > 5) break; continue; }
    if (info.exemptTiny) {
      totalTiny += 1;
      const key = `${label}|${info.id}`;
      if (!seen.has(key)) { seen.add(key); totalStops += 1; }
      continue;
    }
    if (info.id === last) { stuck += 1; if (stuck > 10) break; } else { stuck = 0; last = info.id; }
    if (seenL.has(info.id) && seenL.get(info.id) === "done") break;
    seenL.set(info.id, seenL.has(info.id) ? "done" : "first");
    const key = `${label}|${info.id}`;
    if (!seen.has(key)) { seen.add(key); totalStops += 1; }
    if (seen.has(`${key}#judged`)) continue;
    seen.add(`${key}#judged`);
    if (process.env.BD209_DEBUG && (info.occluders.length || info.outOfViewport)) {
      console.log(`[dbg][${label}] ${info.outOfViewport ? "视口外" : info.samples > 0 && info.clearHits === 0 ? "全遮" : "部分遮挡"} :: ${info.w}×${info.h}@(${info.x},${info.y}) :: ${info.id} :: ${info.occluders.map((o) => `${o[0]}×${o[1]}`).join(" | ")}`);
    }
    if (info.outOfViewport || (info.samples > 0 && info.clearHits === 0)) findings.push({ label, ...info });
  }
}

await createProject();

const scopes = [
  ["项目·写作台", async () => workbenchView("写作", ".writing-desk")],
  ["项目·设定卡", async () => workbenchView("设定卡", ".cards-page")],
  ["项目·大纲", async () => workbenchView("大纲", ".project-workbench")],
  ["项目·概览", async () => workbenchView("概览", ".overview-page")],
  ["项目·全书预览", async () => workbenchView("全书预览", ".preview-page")],
  ["项目·写作统计", async () => workbenchView("写作统计", ".stats-page")],
  ["项目·版本历史", async () => workbenchView("版本历史", ".history-page")],
  ["书库", async () => { await goto("书库"); await page.waitForSelector(".desktop-library-row", { timeout: 10000 }).catch(() => {}); }],
  ["收件箱", async () => { await goto("收件箱"); await page.waitForSelector(".inbox-list", { timeout: 10000 }).catch(() => {}); }],
  ["资料阅读", async () => { await goto("资料阅读"); await page.waitForTimeout(500); }],
  ["设置", async () => { await goto("设置"); await page.waitForSelector(".desktop-page-stack", { timeout: 10000 }).catch(() => {}); }]
];
for (const [label, prep] of scopes) { await prep().catch(() => {}); await sweep(label); }

// 弹层：全局搜索是模态（noBlur）；速查面板非模态，从 blur 走全页验 inert 离场。
await workbenchView("写作", ".writing-desk").catch(() => {});
await page.keyboard.press("Control+Shift+f");
await page.waitForTimeout(500);
await sweep("弹层·全局搜索", { maxStops: 40, noBlur: true });
await page.keyboard.press("Escape");
await page.waitForTimeout(300);
await page.keyboard.press("Control+Shift+k");
await page.waitForTimeout(600);
await sweep("弹层·写作速查", { maxStops: 60 });
await page.keyboard.press("Control+Shift+k");
await page.waitForTimeout(300);

// 阅读器与抽屉。开书失败的面逐个记跳过、不拖垮整网。
const tryScope = async (label, prep, opts) => {
  try { await prep(); await sweep(label, opts); } catch (e) { console.log(`跳过「${label}」：${String(e && e.message || e).split("\n")[0].slice(0, 120)}`); }
};
await tryScope("阅读器·TXT", async () => {
  await goto("资料阅读");
  await page.waitForSelector('.desktop-library-row[data-format="txt"]', { timeout: 15000 });
  await page.locator('.desktop-library-row[data-format="txt"]').first().click();
  await page.waitForSelector('button[aria-label="阅读设置"]', { timeout: 20000 });
  await page.waitForTimeout(900);
});
await tryScope("阅读器·TXT设置抽屉", async () => {
  await page.locator('button[aria-label="阅读设置"]').first().click();
  await page.waitForTimeout(500);
}, { maxStops: 70, noBlur: true });
await page.keyboard.press("Escape").catch(() => {});
await page.waitForTimeout(350);
await tryScope("阅读器·TXT目录面板", async () => {
  // 目录面板默认就是展开的——只在收起态才点「展开目录」，别把开着的关掉。
  const expand = page.locator('button[aria-label="展开目录"]');
  if (await expand.count()) await expand.first().click();
  await page.waitForTimeout(500);
}, { maxStops: 80 });
await page.locator('button[aria-label="收起目录"]').first().click().catch(() => {});
await page.waitForTimeout(300);
await tryScope("阅读器·TXT搜索浮层", async () => {
  await page.keyboard.press("Control+f");
  await page.waitForTimeout(500);
}, { maxStops: 40 });
await page.keyboard.press("Escape").catch(() => {});
await page.waitForTimeout(300);
await tryScope("阅读器·EPUB", async () => {
  await page.locator('button[aria-label="返回书库"]').first().click();
  await page.waitForSelector('.desktop-library-row[data-format="epub"]', { timeout: 15000 });
  await page.locator('.desktop-library-row[data-format="epub"]').first().click();
  await page.waitForSelector(".paper-topbar", { timeout: 30000 });
  await page.waitForTimeout(1800);
}, { maxStops: 60 });
await tryScope("弹层·EPUB设置抽屉", async () => {
  await page.locator(".paper-topbar button", { hasText: "设置" }).first().click();
  await page.waitForTimeout(500);
}, { maxStops: 70, noBlur: true });
await page.keyboard.press("Escape").catch(() => {});
await page.waitForTimeout(300);

console.log(`普查键盘停靠点 ${totalStops} 个（去重）；隐形代理豁免 ${totalTiny} 个；完全遮挡红 ${findings.length} 个。`);
if (findings.length > 0) {
  console.log(`FOCUSOBSCURED_RED：${findings.length} 个停靠点的焦点矩形被完全遮挡：`);
  for (const f of findings) {
    console.log(`  - [${f.label}] ${f.id}  尺寸=${f.w}×${f.h} @(${f.x},${f.y})  ${f.outOfViewport ? "视口外" : `采样 ${f.samples} 全被盖`}  遮挡源=${f.occluders.map((o) => `${o[0]}×${o[1]}`).join(" | ") || "?"}`);
  }
  await app.close();
  process.exit(1);
}
console.log(`FOCUSOBSCURED_OK：${totalStops} 个键盘停靠点的焦点均未被完全遮挡（含隐形代理豁免 ${totalTiny}）。`);
await app.close();
