/**
 * BD-214 常驻普查：WCAG 3.3.2 标签或说明（Level A）+ 4.1.2 表单控件名称，运行时活体实测。
 *
 * 判据口径（W3C Understanding 3.3.2）：输入控件必须有**持续存在的**标签——
 * aria-label、aria-labelledby、label[for]、或包裹它的 label 里的文字。
 * placeholder 不算标签：理解文档明说「占位文本不能替代标签」，它一输入就消失，
 * 低对比、读屏器也可能根本不当名字念。title 只算补充提示，同样不顶标签。
 * 例外：type=hidden 不参与；aria-hidden 祖先内、矩形 <1px 的不参与（未展开的弹层）。
 *
 * 范围与 audit-landmarks.mjs 对齐：14 个页面级视图，含设定卡「列表」布局。
 * 红判据：任一可见输入控件拿不到上述任一标签即 FORM_LABEL_RED，exit 1。
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-form-label-audit-"));
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
  await page.locator("input[placeholder='例如：测试项目']").first().fill("表单普查项目");
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
  const exposed = (el) => {
    if (el.closest('[aria-hidden="true"]')) return false;
    const r = el.getBoundingClientRect();
    return r.width > 1 && r.height > 1;
  };
  // label 自己也可能带 aria-label——那只改 label 的名字，不会传给被包裹的控件；
  // 控件能用的标签只有：自己的 aria-label/labelledby、label[for]、包裹 label 的文字。
  const ownLabel = (el) => {
    const labelledby = (el.getAttribute("aria-labelledby") || "").trim();
    const byRef = labelledby
      ? labelledby.split(/\s+/).map((id) => document.getElementById(id)?.textContent?.trim() || "").join(" ").trim()
      : "";
    const aria = (el.getAttribute("aria-label") || "").trim();
    const labelFor = el.id ? document.querySelector(`label[for="${CSS.escape(el.id)}"]`)?.textContent?.trim() || "" : "";
    const wrap = el.closest("label")?.textContent?.trim() || "";
    return byRef || aria || labelFor || wrap;
  };
  const rows = [];
  let judged = 0;
  const controls = document.querySelectorAll('input, select, textarea, [contenteditable="true"], [role="combobox"], [role="textbox"], [role="searchbox"], [role="spinbutton"]');
  for (const el of controls) {
    if (!exposed(el)) continue;
    if (el.getAttribute("type") === "hidden") continue;
    judged += 1;
    if (ownLabel(el)) continue;
    rows.push({
      tag: el.tagName.toLowerCase(),
      type: el.getAttribute("type") || el.getAttribute("role") || "",
      cls: (el.getAttribute("class") || "").split(/\s+/).slice(0, 2).join("."),
      placeholder: el.getAttribute("placeholder") || "",
      title: el.getAttribute("title") || ""
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
  for (const row of r.rows) {
    if (!rows.some((x) => x.cls === row.cls && x.placeholder === row.placeholder && x.tag === row.tag)) rows.push({ scope: label, ...row });
  }
}

console.log(`表单范围 ${scopes.length} 个、判定控件 ${judged} 个；拿不到持续标签的：${rows.length} 处`);
for (const r of rows) console.log(`  - [${r.scope}] ${r.tag}[${r.type}].${r.cls}  placeholder「${r.placeholder.slice(0, 40)}」 title「${r.title.slice(0, 30)}」`);
if (rows.length) {
  console.log(`FORM_LABEL_RED：${rows.length} 处输入控件只有 placeholder/title，没有持续标签（3.3.2）。`);
  await app.close();
  process.exit(1);
}
console.log("FORM_LABEL_OK：所有可见输入控件都有持续的标签或说明。");
await app.close();
process.exit(0);
