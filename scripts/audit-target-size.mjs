/**
 * 目标尺寸普查（WCAG 2.5.8 Target Size (Minimum)，AA）。批次 BD-208 立。
 *
 * 判据（W3C Understanding 2.5.8 原文口径）：指针目标 ≥24×24 CSS px，
 * 或命中五条豁免之一：
 *  · Spacing（间距）：以目标外接矩形的中心画 24×24 圆，该圆不与其他目标矩形、
 *    不与其他小目标的 24 圆、不与自身外接矩形之外的他方几何相交 → 通过。
 *  · Equivalent（等效）：同一控件更大的目标完成同一动作（如整行按钮罩住小按钮）。
 *    运行时无法机械判定，红名单人工复核口径，本脚本不自动豁免。
 *  · Inline（行内）：目标在文本行内且该行高度 <24px（行高由文本定，放大会破版）。
 *  · User-agent control（用户代理控件）：checkbox/radio 原生绘制控件豁免。
 *  · Essential（本质必要）：尺寸是信息本身（地图缩放点等）。本仓暂无此形状。
 *
 * 遮挡过滤（BD-208 判据核心，取证见 /tmp/bd208-geo2.log）：抽屉/弹层打开时，
 * 背景目标被遮罩盖住、指针不可达，**不得**参与间距判定——TXT 设置抽屉的
 * `.paper-slider`（279×20）曾因把遮罩下的目录章按钮算进邻域而假红。每条目标
 * 用 elementFromPoint 取 3 个内侧采样点（中心 + 两个朝内偏移点），命中自身或
 * 其后代才算活体。被过滤的数量与活体数量一并报出，防止「扫了个寂寞」。
 *
 * 邻域同样只数活体，且 **disabled 控件不是判定对象**（Understanding 2.5.8 原文
 * 豁免禁用目标）——BD-208 早期 Tab 遍历摸底把 disabled 的上移/下移钮算进邻域、
 * 又拿大目标（199×18 行钮）的矩形高而非中心圆判相交，多报了 scene-main 与
 * uni-search-input 两个假红（牙齿取证 /tmp/bd208-teeth-dbg.log 逐条点名）。
 * 这两处最终按「预防性对齐 BD-48 地板 24」保留，注释据实标注，不冒充缺陷修复。
 *
 * 为什么在运行时量（BD-205 遗产，普查范围口径同 audit-focus-rings）：静态级联
 * 推演两头都有误判——只有实渲染矩形是权威。本脚本走**全域 DOM 普查**而非
 * Tab 遍历：2.5.8 管的是指针目标，不限于键盘停靠点。
 *
 * 跳过：display:none / visibility:hidden / opacity:0 / 矩形 <1px / :disabled /
 * [inert] / iframe（epub.js 书内 CSS 归书管）/ pointer-events:none（指针打不到）。
 * range 滑块的 thumb 是伪元素（computed 读取在 Electron 33.4.11/Chromium 130
 * 恒为 none，见 BD-205），不在元素矩形判据内——轨道宿主 input 按自身矩形判。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 900 xvfb-run -a --server-args="-screen 0 1600x1000x24" node scripts/audit-target-size.mjs
 *
 * 书库种子（首启建 schema → round4-seed.cjs 播 TXT/EPUB/MD → 重开）与阅读器/
 * 抽屉/目录/搜索浮层范围复用 audit-focus-rings（BD-206）套路。
 *
 * 台账：scripts/audit-target-size.test.mjs 钉住判据形状（间距圆规则、遮挡过滤、
 * 行内/UA 豁免、范围名册）。牙齿检查（BD-208）：把 padding/min-height 修复回退
 * 后必须恰好红出账上那批目标。
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-targetsize-"));
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

// 阅读器范围要真书——首启建 schema（workspace.sqlite），再用 round4 种子
// 播 TXT/EPUB/MD 三本资料，然后重开正式实例。（audit-focus-rings BD-206 同款）
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
  // 向导弹层也是指针面，先扫再走流程。
  await census("弹层·新建项目向导");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：测试项目']").first().fill("目标尺寸普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(1200);
}

/**
 * 页面内全域普查：收集所有交互目标的实渲染矩形，判 2.5.8。
 * 返回 { reds: [...], liveCount, occludedCount, exemptUa, exemptInline }。
 */
const collectScript = (dbgOn) => {
  const dbg = [];
  const TARGETS = document.querySelectorAll(
    'a[href],button,input,select,textarea,[role=button],[role=menuitem],[role=tab],[role=checkbox],[role=radio],[role=switch],[tabindex="0"]'
  );
  const live = [];
  let occluded = 0;
  for (const el of TARGETS) {
    const tag = el.tagName.toLowerCase();
    if (tag === "iframe") continue;
    if (el.disabled || el.closest("[inert]")) continue;
    const cs = getComputedStyle(el);
    if (cs.display === "none" || cs.visibility === "hidden" || Number(cs.opacity) === 0) continue;
    if (cs.pointerEvents === "none") continue;
    const r = el.getBoundingClientRect();
    if (r.width < 1 || r.height < 1) continue;
    // 遮挡过滤：3 个内侧采样点，任一命中自身或后代即指针可达。
    const inx = Math.min(4, r.width / 2);
    const iny = Math.min(4, r.height / 2);
    const pts = [
      [r.x + r.width / 2, r.y + r.height / 2],
      [r.x + inx, r.y + iny],
      [r.right - inx, r.bottom - iny]
    ];
    let reachable = false;
    for (const [px, py] of pts) {
      // 命中自身、或命中自身的后代（内容层盖在宿主上）才算可达；
      // 命中共祖先 = 被别的东西垫着，不算。
      const hit = document.elementFromPoint(px, py);
      if (hit && (hit === el || el.contains(hit))) { reachable = true; break; }
    }
    if (!reachable) { occluded += 1; continue; }
    const cls = (el.getAttribute("class") || "").split(/\s+/).filter(Boolean);
    const id = `${tag}.${cls.join(".") || "(无类名)"}[${el.getAttribute("aria-label") || el.textContent?.trim().slice(0, 12) || ""}]`;
    live.push({ id, tag, type: el.type || "", w: r.width, h: r.height, x: r.x, y: r.y, el });
  }
  const rectOf = (t) => ({ left: t.x, top: t.y, right: t.x + t.w, bottom: t.y + t.h });
  // Inline 豁免：行内显示、身处文本行、所在行高 <24（放大破版）。
  const isInline = (t) => {
    if (t.tag !== "a" && t.tag !== "button" && !(t.tag === "span" && t.el.matches("[tabindex='0']"))) return false;
    const cs = getComputedStyle(t.el);
    if (cs.display !== "inline" && cs.display !== "inline-block") return false;
    const parent = t.el.parentElement;
    if (!parent) return false;
    const pr = parent.getBoundingClientRect();
    if (pr.height >= 24) return false;
    const hasText = [...parent.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim());
    return hasText;
  };
  const reds = [];
  let exemptUa = 0;
  let exemptInline = 0;
  let passSpacing = 0;
  for (const t of live) {
    const sizeOk = t.w >= 24 && t.h >= 24;
    if (sizeOk) continue;
    // User-agent control 豁免：原生 checkbox/radio 由浏览器绘制。
    if (t.tag === "input" && (t.type === "checkbox" || t.type === "radio")) { exemptUa += 1; continue; }
    if (t.el.matches("[role=checkbox],[role=radio],[role=switch]")) { /* 自绘控件不豁免 */ }
    if (isInline(t)) { exemptInline += 1; if (dbgOn) dbg.push({ id: t.id, wh: `${Math.round(t.w)}×${Math.round(t.h)}`, verdict: "行内豁免" }); continue; }
    // Spacing 豁免：中心 24 圆不与他方矩形（点到矩形距离 <12）
    // 也不与其他小目标圆（中心距 <24）相交。邻域只含活体目标。
    const cx = t.x + t.w / 2;
    const cy = t.y + t.h / 2;
    let hit = false;
    let hitBy = "";
    for (const o of live) {
      if (o === t) continue;
      const or = rectOf(o);
      const dx = Math.max(or.left - cx, 0, cx - or.right);
      const dy = Math.max(or.top - cy, 0, cy - or.bottom);
      if (Math.hypot(dx, dy) < 12) { hit = true; hitBy = `矩形@(${Math.round(o.x)},${Math.round(o.y)})${Math.round(o.w)}×${Math.round(o.h)} pt=${Math.round(Math.hypot(dx, dy))}`; break; }
      if (o.w < 24 || o.h < 24) {
        const ox = o.x + o.w / 2;
        const oy = o.y + o.h / 2;
        if (Math.hypot(cx - ox, cy - oy) < 24) { hit = true; hitBy = `小钮圆@(${Math.round(ox)},${Math.round(oy)}) cd=${Math.round(Math.hypot(cx - ox, cy - oy))}`; break; }
      }
    }
    if (hit) {
      reds.push({ id: t.id, w: Math.round(t.w), h: Math.round(t.h), x: Math.round(t.x), y: Math.round(t.y) });
      if (dbgOn) dbg.push({ id: t.id, wh: `${Math.round(t.w)}×${Math.round(t.h)}@(${Math.round(t.x)},${Math.round(t.y)})`, verdict: `违规 ${hitBy}` });
    } else {
      passSpacing += 1;
      if (dbgOn) dbg.push({ id: t.id, wh: `${Math.round(t.w)}×${Math.round(t.h)}@(${Math.round(t.x)},${Math.round(t.y)})`, verdict: "间距豁免通过" });
    }
  }
  return { reds, liveCount: live.length, occluded, exemptUa, exemptInline, passSpacing, dbg };
};

const reds = new Map();
let totalLive = 0;
let totalOccluded = 0;
let totalUa = 0;
let totalInline = 0;
let totalSpacing = 0;

async function census(label) {
  const dbgOn = !!process.env.BD208_DEBUG;
  const r = await page.evaluate(collectScript, dbgOn).catch(() => null);
  if (!r) { console.log(`跳过「${label}」：普查 evaluate 失败`); return; }
  if (dbgOn && r.dbg.length) {
    for (const d of r.dbg) console.log(`[dbg][${label}] ${d.verdict} :: ${d.wh} :: ${d.id}`);
  }
  totalLive += r.liveCount;
  totalOccluded += r.occluded;
  totalUa += r.exemptUa;
  totalInline += r.exemptInline;
  totalSpacing += r.passSpacing;
  for (const red of r.reds) {
    const key = red.id;
    if (!reds.has(key)) reds.set(key, { where: [label], ...red });
    else if (!reds.get(key).where.includes(label)) reds.get(key).where.push(label);
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

for (const [label, prep] of scopes) {
  await prep().catch(() => {});
  await census(label);
}

// 弹层：全局搜索（Ctrl+Shift+F）与写作速查面板（写作台内 Ctrl+Shift+K）
await workbenchView("写作", ".writing-desk").catch(() => {});
await page.keyboard.press("Control+Shift+f");
await page.waitForTimeout(500);
await census("弹层·全局搜索");
await page.keyboard.press("Escape");
await page.waitForTimeout(300);
await page.keyboard.press("Control+Shift+k");
await page.waitForTimeout(600);
await census("弹层·写作速查");
await page.keyboard.press("Control+Shift+k");
await page.waitForTimeout(300);

// 阅读器与抽屉。开书失败的面逐个记跳过、不拖垮整网。
const tryScope = async (label, prep) => {
  try {
    await prep();
    await census(label);
  } catch (e) {
    console.log(`跳过「${label}」：${String(e && e.message || e).split("\n")[0].slice(0, 120)}`);
  }
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
});
await page.keyboard.press("Escape").catch(() => {}); // BD-203 族：抽屉 Escape 先关自己
await page.waitForTimeout(350);
await tryScope("阅读器·TXT目录面板", async () => {
  // 目录面板默认就是展开的——只在收起态才点「展开目录」，别把开着的关掉。
  const expand = page.locator('button[aria-label="展开目录"]');
  if (await expand.count()) await expand.first().click();
  await page.waitForTimeout(500);
});
await page.locator('button[aria-label="收起目录"]').first().click().catch(() => {});
await page.waitForTimeout(300);
await tryScope("阅读器·TXT搜索浮层", async () => {
  await page.keyboard.press("Control+f");
  await page.waitForTimeout(500);
});
await page.keyboard.press("Escape").catch(() => {});
await page.waitForTimeout(300);

await tryScope("阅读器·EPUB", async () => {
  await page.locator('button[aria-label="返回书库"]').first().click();
  await page.waitForSelector('.desktop-library-row[data-format="epub"]', { timeout: 15000 });
  await page.locator('.desktop-library-row[data-format="epub"]').first().click();
  await page.waitForSelector(".paper-topbar", { timeout: 30000 });
  await page.waitForTimeout(1800); // epub.js 渲染正文
});
await tryScope("弹层·EPUB设置抽屉", async () => {
  await page.locator(".paper-topbar button", { hasText: "设置" }).first().click();
  await page.waitForTimeout(500);
});
await page.keyboard.press("Escape").catch(() => {});
await page.waitForTimeout(300);

console.log(
  `普查活体目标 ${totalLive} 个/范围累计；遮挡过滤 ${totalOccluded}、UA 控件豁免 ${totalUa}、` +
  `行内豁免 ${totalInline}、间距豁免通过 ${totalSpacing}（按范围×元素去重红名单 ${reds.size} 个）。`
);
if (reds.size > 0) {
  console.log(`TARGETSIZE_RED：${reds.size} 个指针目标不足 24×24 且不满足间距豁免：`);
  for (const r of reds.values()) {
    console.log(`  - ${r.id}  [${r.where.join("、")}]  尺寸=${r.w}×${r.h} @(${r.x},${r.y})`);
  }
  await app.close();
  process.exit(1);
}
console.log(`TARGETSIZE_OK：${totalLive} 个活体目标全部通过 2.5.8（含豁免）。`);
await app.close();
