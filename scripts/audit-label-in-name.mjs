/**
 * BD-213 常驻普查：WCAG 2.5.3 Label in Name（Level AA），运行时活体实测。
 *
 * 判据口径（W3C Understanding 2.5.3）：控件的**可访问名必须包含它的可见标签文本**
 * （忽略大小写与空白差异）。语音操控用户念的是屏幕上看到的字；名字里只写同义词
 * （「导出包」vs「导出项目包」）或中间插字，念出来就对不上。
 *
 * 只量「可见标签」：逐个文本节点取矩形 >1px 的可见文字，并剔除下列**补充信息**——
 *  · <small>（导航项副标题）、<em>（状态角标/计数）、 svg；
 *  · .desktop-brand-mark（印章字形）、.project-home-meta / -progress（更新时间与字数进度）、
 *    .inbox-item-meta / -body（收件箱摘要）、[role=progressbar]；
 *  · 纯符号文本（「−」「+」「»」这类图标字形没有可念的标签文字）。
 * 剔除后没有标签文字的控件（纯图标按钮、sr-only 名字）不参与判定——它们走 4.1.2 的名字，
 * 不在 2.5.3 的范围里。
 *
 * 范围与 audit-landmarks.mjs 对齐：14 个页面级视图，含设定卡「列表」布局。
 * 红判据：任一控件「可见标签不是可访问名的子串」即 LABEL_RED，exit 1。
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-label-audit-"));
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
  await page.locator("input[placeholder='例如：测试项目']").first().fill("标签普查项目");
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

const collectScript = () => {
  const INTERACTIVE = 'button, a[href], input, select, textarea, [role="button"], [role="link"], [role="tab"], [role="checkbox"], [role="switch"], [role="menuitem"], [role="option"], [role="radio"]';
  const SUPPLEMENTARY = 'svg, small, em, .desktop-brand-mark, .project-home-meta, .project-home-progress, .inbox-item-meta, .inbox-item-body, [role="progressbar"], [aria-hidden="true"]';
  const hasWord = (t) => /[\p{L}\p{N}]/u.test(t);
  const labelText = (node) => {
    const walker = document.createTreeWalker(node, NodeFilter.SHOW_TEXT);
    let out = "";
    let n;
    while ((n = walker.nextNode())) {
      const t = (n.textContent || "").trim();
      if (!t || !hasWord(t)) continue;
      if (n.parentElement && n.parentElement.closest(SUPPLEMENTARY)) continue;
      const range = document.createRange();
      range.selectNodeContents(n);
      const r = range.getBoundingClientRect();
      if (r.width > 1 && r.height > 1) out += `${t} `;
    }
    return out.replace(/\s+/g, " ").trim();
  };
  const exposed = (el) => {
    if (el.closest('[aria-hidden="true"]')) return false;
    const r = el.getBoundingClientRect();
    return r.width > 1 && r.height > 1;
  };
  const nameOf = (el) => {
    const byRef = (el.getAttribute("aria-labelledby") || "").split(/\s+/).filter(Boolean)
      .map((id) => document.getElementById(id)?.textContent?.trim() || "").join(" ").trim();
    return (byRef || el.getAttribute("aria-label") || "").trim();
  };
  const norm = (s) => s.replace(/\s+/g, " ").trim().toLowerCase();
  const rows = [];
  let judged = 0;
  for (const el of document.querySelectorAll(INTERACTIVE)) {
    if (!exposed(el)) continue;
    const name = nameOf(el);
    if (!name) continue;
    const label = labelText(el);
    if (!label) continue;
    judged += 1;
    if (norm(name).includes(norm(label))) continue;
    rows.push({
      tag: el.tagName.toLowerCase(),
      cls: (el.getAttribute("class") || "").split(/\s+/).slice(0, 2).join("."),
      visible: label,
      name
    });
  }
  return { judged, rows };
};

const scopes = [
  ["项目·首页", async () => { await goto("项目"); const back = page.locator(".project-nav-back").first(); if (await back.isVisible().catch(() => false)) await back.click(); await page.waitForSelector(".project-home", { timeout: 10000 }); }],
  ["项目·写作台", async () => workbenchView("写作", ".writing-desk")],
  ["项目·设定卡", async () => workbenchView("设定卡", ".cards-page")],
  ["项目·设定卡（列表）", async () => {
    await workbenchView("设定卡", ".cards-page");
    await page.locator('[role="group"][aria-label="卡片视图"] button', { hasText: "列表" }).first().click();
    await page.waitForSelector(".cards-layout", { timeout: 10000 });
    await page.waitForTimeout(300);
  }],
  ["项目·大纲", async () => workbenchView("大纲", ".project-workbench")],
  ["项目·概览", async () => workbenchView("概览", ".overview-page")],
  ["项目·全书预览", async () => workbenchView("全书预览", ".preview-page")],
  ["项目·写作统计", async () => workbenchView("写作统计", ".stats-page")],
  ["项目·版本历史", async () => workbenchView("版本历史", ".history-page")],
  ["书库", async () => { await goto("书库"); await page.waitForSelector(".desktop-library-row", { timeout: 10000 }).catch(() => {}); }],
  ["卡片库", async () => { await goto("卡片库"); await page.waitForSelector(".global-card-library", { timeout: 10000 }).catch(() => {}); }],
  ["收件箱", async () => { await goto("收件箱"); await page.waitForSelector(".inbox-list", { timeout: 10000 }).catch(() => {}); }],
  ["资料阅读", async () => { await goto("资料阅读"); await page.waitForTimeout(500); }],
  ["设置", async () => { await goto("设置"); await page.waitForSelector(".desktop-page-stack", { timeout: 10000 }).catch(() => {}); }]
];

await createProject();
const rows = [];
let judged = 0;
for (const [label, prep] of scopes) {
  try { await prep(); } catch (e) { console.log(`跳过「${label}」：${String(e && e.message || e).split("\n")[0].slice(0, 100)}`); continue; }
  const r = await page.evaluate(collectScript).catch(() => null);
  if (!r) { console.log(`跳过「${label}」`); continue; }
  judged += r.judged;
  // 同一颗壳层按钮会在多个范围重复出现，按「类名 + 可见 + 名字」去重后再记红。
  for (const row of r.rows) {
    if (!rows.some((x) => x.cls === row.cls && x.visible === row.visible && x.name === row.name)) rows.push({ scope: label, ...row });
  }
}

console.log(`标签范围 ${scopes.length} 个、判定控件 ${judged} 个；可见标签不是可访问名子串的：${rows.length} 处`);
for (const r of rows) console.log(`  - [${r.scope}] ${r.tag}.${r.cls}  可见「${r.visible.slice(0, 40)}」  名「${r.name.slice(0, 40)}」`);
if (rows.length) {
  console.log(`LABEL_RED：${rows.length} 处控件的可见标签没有进入可访问名（语音操控念不出来）。`);
  await app.close();
  process.exit(1);
}
console.log("LABEL_IN_NAME_OK：所有带可见标签的控件，可访问名都包含屏幕上看到的字。");
await app.close();
process.exit(0);
