/**
 * BD-212 常驻普查：WCAG 1.3.1 / 2.4.1 / 4.1.2 的地标结构（Level A），运行时活体实测。
 *
 * 判据口径（W3C Understanding 2.4.1 + ARIA Authoring Practices 的地标章）：
 *  · 每一屏**恰好一个** main 地标——「跳过重复区块」在键盘/读屏侧的落点就是它；
 *    零个等于没有可跳的目的地，两个等于读屏器的「主内容」跳转变成抛硬币。
 *  · 至少一个 navigation 地标且**有名字**：本仓壳层侧栏里的 nav[aria-label="一级导航"]
 *    是唯一入口，名字让地标列表能把它跟页面内的导航区分开。
 *  · complementary 地标的名字不许自称「导航」：aria-label 写什么，读屏器就念什么，
 *    「complementary 桌面端主导航 + navigation 一级导航」并列出现就是 4.1.2 的名字错报。
 *  · 每屏至少一个 h1（2.4.1 的另一条实现路径：标题跳转）。
 *
 * 地标只量**暴露给 AT 的**：自身或祖先 aria-hidden="true" 的不计，矩形 <1px 的不计
 * （沉浸态、弹层盖住整屏时，背景地标合法地退场）。故范围只取 14 个页面级视图；
 * 弹层/抽屉的地标随 BD-130 的模态键盘契约走，由焦点遮挡与模态账本另行咬住。
 *
 * h1 例外地不看矩形：sr-only 标题（clip 成 1×1）正是给读屏器的 h1，视觉 0 像素但对 AT
 * 完全暴露，用矩形过滤会把它误判成「无 h1」。故 h1 的口径是「在 DOM 里、且不在
 * aria-hidden 祖先内」。
 *
 * 判红证据链（本批摸底）：收件箱/书库/设置/项目/写作五屏 main=0；侧栏是
 * aside(complementary, aria-label="桌面端主导航")，真导航在里面（一级导航）。
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-landmark-audit-"));
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

// 首启建 schema → round4 种子播 TXT/EPUB/MD（资料阅读要真书）→ 重开。
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
  await page.locator("input[placeholder='例如：测试项目']").first().fill("地标普查项目");
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
  const revealed = (el) => !el.closest('[aria-hidden="true"]');
  const nameOf = (el) => (el.getAttribute("aria-label") || "").trim();
  const mains = [...document.querySelectorAll('[role="main"], main')].filter(exposed);
  const navs = [...document.querySelectorAll('nav, [role="navigation"]')].filter(exposed);
  const compl = [...document.querySelectorAll('aside, [role="complementary"]')].filter(exposed);
  const banners = [...document.querySelectorAll('header, [role="banner"]')].filter(exposed)
    .filter((el) => !el.closest("main, [role=main], article, section"));
  const h1 = [...document.querySelectorAll("h1")].filter(revealed);
  return {
    main: mains.length,
    mainNode: mains.map((el) => `${el.tagName.toLowerCase()}.${(el.getAttribute("class") || "").split(/\s+/)[0]}`).join("/"),
    nav: navs.length,
    navNames: navs.map((el) => nameOf(el) || "(无名)"),
    navUnnamed: navs.filter((el) => !nameOf(el)).length,
    complMisnamed: compl.filter((el) => /导航/.test(nameOf(el))).map((el) => `${el.tagName.toLowerCase()}[${nameOf(el)}]`),
    banner: banners.length,
    h1: h1.length
  };
};

const scopes = [
  // 种过项目后「项目」屏默认落在工作台，先点「项目列表」退回首页。
  ["项目·首页", async () => {
    await goto("项目");
    const back = page.locator(".project-nav-back").first();
    if (await back.isVisible().catch(() => false)) await back.click();
    await page.waitForSelector(".project-home", { timeout: 10000 });
  }],
  ["项目·写作台", async () => workbenchView("写作", ".writing-desk")],
  ["项目·设定卡", async () => workbenchView("设定卡", ".cards-page")],
  // 设定卡默认落在看板布局，详情栏是 aside；列表布局的详情栏曾自带 <main>，
  // 只有切到列表才会渲染——不单独走一趟就查不到那第二个 main。
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
for (const [label, prep] of scopes) {
  try { await prep(); } catch (e) { console.log(`跳过「${label}」：${String(e && e.message || e).split("\n")[0].slice(0, 100)}`); continue; }
  const r = await page.evaluate(collectScript).catch(() => null);
  if (!r) { console.log(`跳过「${label}」`); continue; }
  rows.push({ label, ...r });
}

const reds = [];
for (const r of rows) {
  const why = [];
  if (r.main !== 1) why.push(`main=${r.main}`);
  if (r.nav === 0) why.push("无 navigation 地标");
  if (r.navUnnamed > 0) why.push(`有 ${r.navUnnamed} 个导航地标无名`);
  if (r.complMisnamed.length) why.push(`complementary 自称导航：${r.complMisnamed.join("、")}`);
  if (r.h1 < 1) why.push("无 h1");
  if (why.length) reds.push({ label: r.label, why, detail: r });
}
console.log(`地标范围 ${rows.length} 个；每屏（main / nav / complementary 误名 / banner / h1）：`);
for (const r of rows) console.log(`  · ${r.label}：${r.main}${r.mainNode ? `(${r.mainNode})` : ""} / ${r.nav}(${r.navNames.join("、") || "无"}) / ${r.complMisnamed.length} / ${r.banner} / ${r.h1}`);
if (reds.length) {
  console.log(`LANDMARK_RED：${reds.length} 个范围的地标结构不达标：`);
  for (const r of reds) console.log(`  - [${r.label}] ${r.why.join("；")}`);
  await app.close();
  process.exit(1);
}
console.log(`LANDMARK_OK：${rows.length} 个范围都是「恰好一个 main + 有名 navigation + 不误名的 complementary + 有 h1」。`);
await app.close();
process.exit(0);
