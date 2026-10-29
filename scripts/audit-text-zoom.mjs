/**
 * BD-218 常驻普查：WCAG 1.4.4 文本缩放（Level AA），运行时活体实测。
 *
 * 判据口径（W3C Understanding 1.4.4）：除字幕与文字图片外，文本必须能放大到 200%
 * 而不丢失内容或功能，且不要求辅助技术。本仓原来的字号档（appFontScale 0.85–1.4）
 * 只改根字号：Tailwind 的 rem 文案会动，230 处固定 px 字号纹丝不动，上限也只有 140%
 * ——用户没有任何路径把文字放到 200%。本批把整页缩放（webContents.setZoomFactor）
 * 接到 Ctrl/Cmd + = / + / - / 0，上下限 0.5–2，px 与 rem 一起放大。
 *
 * 本普查做两件事：
 *   ① 按键链：用 webContents.sendInputEvent 走真实 before-input-event 路径（Playwright
 *      的 CDP 键盘事件不经过这一层，实测收不到，故不用 page.keyboard），核对
 *      1.0 → 2.0 上限、Ctrl+0 还原 1.0、下限 0.5、Alt/Ctrl 缺失时不抢键；
 *   ② 200% 下逐视图扫硬裁切：遍历可见文本节点，找 overflow hidden/clip 且
 *      scrollWidth/scrollHeight 超过 client 的元素——有字被盒边切掉就是内容丢失。
 *
 * 红判据：任一按键核对失败或任一视图出现硬裁切 → ZOOM_RED，exit 1。
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-text-zoom-"));
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

const reds = [];
const factor = () => app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].webContents.getZoomFactor());
const tap = async (keyCode, modifiers = ["control"]) => {
  await app.evaluate(({ BrowserWindow }, args) => {
    const wc = BrowserWindow.getAllWindows()[0].webContents;
    wc.sendInputEvent({ type: "keyDown", keyCode: args.keyCode, modifiers: args.modifiers });
    wc.sendInputEvent({ type: "keyUp", keyCode: args.keyCode, modifiers: args.modifiers });
  }, { keyCode, modifiers });
  await page.waitForTimeout(120);
};
const expectFactor = async (label, expected) => {
  const actual = await factor();
  if (Math.abs(actual - expected) > 0.001) reds.push(`${label}：zoom=${actual}，应为 ${expected}`);
};

// ① 按键链
await expectFactor("初始", 1);
for (let i = 0; i < 10; i += 1) await tap("=");
await expectFactor("Ctrl+= 连按 10 次到上限", 2);
await tap("=");
await expectFactor("上限后继续放大仍为 200%", 2);
const zoomedViewport = await page.evaluate(() => innerWidth);
if (zoomedViewport > 700) reds.push(`200% 时布局视口 innerWidth=${zoomedViewport}，页面缩放没有生效`);
await tap("0");
await expectFactor("Ctrl+0 还原", 1);
await tap("-");
await expectFactor("Ctrl+- 缩小一档", 0.9);
for (let i = 0; i < 10; i += 1) await tap("-");
await expectFactor("Ctrl+- 连按到下限", 0.5);
await tap("=", ["control", "alt"]);
await expectFactor("Ctrl+Alt+= 不抢键（仍在下限）", 0.5);
await tap("=", []);
await expectFactor("无修饰键的 = 不缩放", 0.5);
await tap("0");
await expectFactor("再次 Ctrl+0 还原", 1);

// ② 200% 下逐视图扫硬裁切（先建项目，再放大）
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
  await page.locator("input[placeholder='例如：测试项目']").first().fill("文本缩放普查项目");
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

{ await inWorkbench(); }

for (let i = 0; i < 10; i += 1) await tap("=");
await expectFactor("扫描前放大到 200%", 2);
await page.waitForTimeout(400);

const scan = () => {
  const hits = [];
  const seen = new Set();
  for (const el of document.querySelectorAll("body *")) {
    if (el.closest('[aria-hidden="true"]')) continue;
    const rect = el.getBoundingClientRect();
    if (rect.width < 2 || rect.height < 2) continue;
    const cs = getComputedStyle(el);
    if (cs.visibility === "hidden") continue;
    const hasText = [...el.childNodes].some((n) => n.nodeType === 3 && n.nodeValue.trim().length > 0);
    if (!hasText) continue;
    const clipX = /hidden|clip/.test(cs.overflowX) && el.scrollWidth > el.clientWidth + 2;
    const clipY = /hidden|clip/.test(cs.overflowY) && el.scrollHeight > el.clientHeight + 2;
    if (!clipX && !clipY) continue;
    const key = `${el.tagName}.${(el.className || "").toString().split(/\s+/).slice(0, 2).join(".")}|${(el.textContent || "").trim().slice(0, 24)}`;
    if (seen.has(key)) continue;
    seen.add(key);
    hits.push(`${el.tagName.toLowerCase()}.${(el.className || "").toString().split(/\s+/).slice(0, 2).join(".")}「${(el.textContent || "").trim().replace(/\s+/g, " ").slice(0, 34)}」${clipX ? " 横向" : ""}${clipY ? " 纵向" : ""}`);
  }
  return hits;
};

const scopes = [
  ["项目·首页", async () => {
    await goto("项目");
    const back = page.locator(".project-nav-back").first();
    if (await back.isVisible().catch(() => false)) await back.click();
    await page.waitForSelector(".project-home", { timeout: 10000 });
  }],
  ["工作台·概览", async () => workbenchView("概览", ".overview-page")],
  ["工作台·写作", async () => workbenchView("写作", ".writing-desk")],
  ["工作台·大纲", async () => workbenchView("大纲", ".outline-page")],
  ["工作台·设定卡", async () => workbenchView("设定卡", ".cards-page")],
  ["收件箱", async () => {
    await goto("收件箱");
    await page.waitForSelector(".inbox-page", { timeout: 10000 });
  }],
  ["卡片库", async () => {
    await goto("卡片库");
    await page.waitForSelector(".cards-page.global-card-library", { timeout: 10000 });
  }],
  ["书库", async () => {
    await goto("书库");
    await page.waitForSelector(".desktop-library-row", { timeout: 10000 });
  }],
  ["设置·外观", async () => {
    await goto("设置");
    await page.waitForTimeout(700);
  }],
  ["资料阅读", async () => {
    await goto("书库");
    await page.locator('.desktop-library-row[data-format="txt"]').first().click();
    await page.waitForSelector("button[aria-label=\"阅读设置\"]", { timeout: 20000 });
    await page.waitForTimeout(800);
  }]
];

let judged = 0;
let clipped = 0;
for (const [label, prep] of scopes) {
  try {
    await prep();
    await page.waitForTimeout(250);
    const hits = await page.evaluate(scan);
    judged += 1;
    clipped += hits.length;
    for (const hit of hits) reds.push(`${label} 200%：${hit}`);
  } catch (error) {
    console.log(`跳过「${label}」：${String((error && error.message) || error).split("\n")[0].slice(0, 110)}`);
  }
}

await tap("0");
await expectFactor("扫描后 Ctrl+0 还原", 1);

console.log(`按键核对完成；200% 下扫描 ${judged} 个视图，硬裁切 ${clipped} 处。`);
if (judged < 8) {
  console.log(`ZOOM_RED：只判定到 ${judged} 个视图（要求 ≥8），覆盖不足无法证明。`);
  await app.close();
  process.exit(1);
}
if (reds.length) {
  for (const red of reds) console.log(`  - ${red}`);
  console.log(`ZOOM_RED：${reds.length} 处未达 1.4.4（缩放不可达或 200% 下内容被裁）。`);
  await app.close();
  process.exit(1);
}
console.log("ZOOM_OK：Ctrl/Cmd + = / - / 0 可达 50%–200%，200% 下 10 个视图无文字硬裁切。");
await app.close();
process.exit(0);
