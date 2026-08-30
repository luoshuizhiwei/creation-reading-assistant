/**
 * 第四轮 UI 深化的一次性视觉证据：书库 / 阅读统计 / 收件箱。
 *
 * 流程：
 *   1. 启动应用初始化隔离 profile（建库、写默认设置），随后退出；
 *   2. 以 ELECTRON_RUN_AS_NODE 运行 round4-seed.cjs 注入种子数据；
 *   3. 重启应用，依次进入 资料阅读 / 收件箱 / 阅读统计，各截 light+dark。
 *
 * 用法：node scripts/round4-capture.mjs [--out=<dir>] [--keep-profile]
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync, mkdirSync, existsSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const outDirArg = process.argv.find((a) => a.startsWith("--out="));
const outDir = outDirArg ? path.resolve(outDirArg.split("=")[1]) : path.join(__dirname, "visual-evidence-r4-pages");
const keepProfile = process.argv.includes("--keep-profile");
mkdirSync(outDir, { recursive: true });

const profileDir = mkdtempSync(path.join(tmpdir(), "creation-round4-profile-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

if (!existsSync(electronPath)) throw new Error(`Electron binary not found: ${electronPath}`);
if (!existsSync(mainJs)) throw new Error(`Main bundle not found: ${mainJs}. Run electron-vite build first.`);

async function launchApp() {
  const app = await electron.launch({
    executablePath: electronPath,
    args: [mainJs],
    cwd: root,
    env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir }
  });
  const page = await app.firstWindow();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
  await page.waitForTimeout(800);
  return { app, page };
}

/** 通过真实点击切换屏幕；返回前等画布内容稳定。 */
async function goto(page, navLabel) {
  await page.locator(".desktop-sidebar button", { hasText: navLabel }).first().click();
  await page.waitForTimeout(700);
}

async function setTheme(page, theme) {
  await page.evaluate((value) => {
    document.documentElement.dataset.appTheme = value;
  }, theme);
  await page.waitForTimeout(400);
}

/** 无边框窗口 setContentSize 有 ±2 DIP 取整偏差；读回 clientHeight 修正。 */
async function setViewportSize(app, page, width, height) {
  let target = height;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    await app.evaluate(({ BrowserWindow }, size) => {
      for (const window of BrowserWindow.getAllWindows()) {
        window.setContentSize(size.width, size.height);
      }
    }, { width, height: target });
    await page.waitForTimeout(450);
    const actual = await page.evaluate(() => document.documentElement.clientHeight);
    if (actual === height) return;
    target += height - actual;
  }
}

async function shot(page, name) {
  await page.screenshot({ path: path.join(outDir, `${name}.png`) });
  console.log(`[round4-capture] saved ${name}.png`);
}

function seedProfile() {
  const result = spawnSync(
    electronPath,
    [path.join(__dirname, "round4-seed.cjs"), profileDir],
    {
      cwd: root,
      encoding: "utf8",
      env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") },
      windowsHide: true,
      timeout: 60_000
    }
  );
  if (result.status !== 0) {
    throw new Error(`seed failed:\n${result.stdout}\n${result.stderr}`);
  }
  console.log(`[round4-capture] seed output: ${result.stdout?.trim()}`);
}

async function captureStats(page, name) {
  await goto(page, "资料阅读");
  await page.getByRole("button", { name: /阅读统计/ }).click();
  await page.waitForSelector(".reading-stat-tile", { timeout: 10000 });
  await page.waitForTimeout(700);
  await shot(page, name);
}

async function captureSettings(page, name) {
  await goto(page, "设置");
  await page.waitForSelector(".settings-nav", { timeout: 10000 });
  await page.waitForTimeout(700);
  await shot(page, name);
}

/** 通过向导创建「测试项目」（无项目时走 create-first，否则走新建项目）。 */
async function createProject(page) {
  await goto(page, "项目");
  await page.waitForSelector(".project-home-create-first, .desktop-page-actions", { timeout: 10000 });
  const createFirst = page.locator(".project-home-create-first").first();
  if (await createFirst.isVisible()) await createFirst.click();
  else await page.getByRole("button", { name: /新建项目/ }).first().click();
  await page.waitForSelector(".creation-wizard", { timeout: 15000 });
  await page.getByRole("button", { name: /下一步/ }).click();
  await page.waitForTimeout(250);
  await page.locator(".creation-field input").first().fill("测试项目");
  await page.getByRole("button", { name: /下一步/ }).click();
  await page.waitForTimeout(250);
  await page.locator("input[placeholder='例如：500000']").fill("50000");
  await page.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForSelector(".project-nav-back", { timeout: 15000 });
  await page.waitForTimeout(700);
}

/** 从任意状态进入项目工作台：已在工作台则直接返回，否则从项目列表打开第一个。 */
async function enterProject(page) {
  await goto(page, "项目");
  const navBack = page.locator(".project-nav-back");
  if (await navBack.isVisible().catch(() => false)) return;
  await page.waitForSelector(".project-home-item-main", { timeout: 10000 });
  await page.locator(".project-home-item-main").first().click();
  await page.waitForSelector(".project-nav-back", { timeout: 15000 });
  await page.waitForTimeout(600);
}

/** 切到项目子视图并截图。 */
async function tourView(page, label, selector, name) {
  await page.locator(".project-nav button", { hasText: label }).first().click();
  await page.waitForSelector(selector, { timeout: 10000 });
  await page.waitForTimeout(600);
  await shot(page, name);
}

/** 打开书库中指定格式的书 → 阅读器（全屏）→ 截图 → 返回书库。 */
async function captureReader(page, theme, format) {
  await goto(page, "资料阅读");
  await page.waitForSelector(".desktop-library-row", { timeout: 10000 });
  await page.locator(`.desktop-library-row[data-format="${format}"]`).first().click();
  await page.waitForSelector("header.paper-topbar", { timeout: 20000 });
  if (format === "epub") {
    await page.waitForSelector("iframe", { timeout: 30000 }).catch(() => {});
    await page.waitForTimeout(4000);
  } else {
    await page.waitForSelector(".markdown-reader", { timeout: 15000 }).catch(() => {});
    await page.waitForTimeout(1200);
  }
  await shot(page, `1440x900-${theme}-reader-${format}`);
  await page.getByRole("button", { name: /返回书库/ }).click();
  await page.waitForTimeout(900);
}

/** 弹窗巡检：在项目工作台内依次打开常驻弹窗并截图。 */
async function tourDialogs(page, theme) {
  const t = (name) => `1440x900-${theme}-${name}`;
  await enterProject(page);

  // 命令面板 Ctrl+P
  await page.keyboard.press("Control+p");
  await page.waitForSelector(".creation-search-overlay", { timeout: 8000 }).catch(() => {});
  await page.waitForTimeout(500);
  await shot(page, t("dialog-palette"));
  await page.keyboard.press("Escape");
  await page.waitForTimeout(400);

  // 查找替换
  await page.getByRole("button", { name: /查找替换/ }).first().click();
  await page.waitForTimeout(700);
  await shot(page, t("dialog-replace"));
  await page.getByTestId("replace-panel-close").click();
  await page.waitForTimeout(400);

  // 校对（该面板不响应 Escape，用其自带关闭按钮）
  await page.getByRole("button", { name: /校对/ }).first().click();
  await page.waitForTimeout(700);
  await shot(page, t("dialog-proof"));
  await page.getByRole("button", { name: "关闭校对" }).click();
  await page.waitForTimeout(400);

  // 导出成稿
  await page.getByRole("button", { name: /导出成稿/ }).first().click();
  await page.waitForTimeout(700);
  await shot(page, t("dialog-export"));
  const closeExport = page.getByRole("button", { name: /关闭|取消/ }).last();
  if (await closeExport.isVisible().catch(() => false)) {
    await closeExport.click();
    await page.waitForTimeout(400);
  }
}

async function main() {
  // 第一步：启动一次以初始化 profile 与数据库结构
  const first = await launchApp();
  await first.app.close();
  seedProfile();

  // 第二步：创建项目骨架（章/场景结构），随后退出
  const second = await launchApp();
  await createProject(second.page);
  await second.app.close();

  // 第三步：向场景写入正文（复用权威口径的 visual-seed-body）
  const seedBody = spawnSync(
    electronPath,
    [path.join(__dirname, "visual-seed-body.cjs"), profileDir, "3000"],
    {
      cwd: root,
      encoding: "utf8",
      env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") },
      windowsHide: true,
      timeout: 60_000
    }
  );
  if (seedBody.status !== 0) {
    throw new Error(`seed body failed:\n${seedBody.stdout}\n${seedBody.stderr}`);
  }
  console.log(`[round4-capture] seed body output: ${seedBody.stdout?.trim()}`);

  // 第四步：重启，全页面巡检（light/dark 各一遍）
  const third = await launchApp();
  const { page } = third;

  const tourProjectViews = async (theme) => {
    await enterProject(page);
    await tourView(page, "概览", ".overview-page", `1440x900-${theme}-overview`);
    await tourView(page, "写作", ".writing-desk", `1440x900-${theme}-writing`);
    await tourView(page, "大纲", ".outline-page", `1440x900-${theme}-outline`);
    await tourView(page, "卡片", ".cards-page", `1440x900-${theme}-cards`);
    await tourView(page, "统计", ".stats-page", `1440x900-${theme}-project-stats`);
    await tourView(page, "版本历史", ".history-page", `1440x900-${theme}-history`);
  };

  await setTheme(page, "light");
  await goto(page, "资料阅读");
  await shot(page, "1440x900-light-library");
  await captureReader(page, "light", "txt");
  await captureReader(page, "light", "epub");
  await goto(page, "资料阅读");
  await shot(page, "1440x900-light-library");
  await goto(page, "收件箱");
  await page.waitForSelector(".inbox-list li", { timeout: 10000 });
  await page.locator(".inbox-list .inbox-item").first().click();
  await page.waitForTimeout(500);
  await shot(page, "1440x900-light-inbox");
  await captureStats(page, "1440x900-light-stats");
  await captureSettings(page, "1440x900-light-settings");
  await tourProjectViews("light");
  await tourDialogs(page, "light");

  await setTheme(page, "dark");
  await goto(page, "资料阅读");
  await shot(page, "1440x900-dark-library");
  await captureReader(page, "dark", "txt");
  await captureReader(page, "dark", "epub");
  await goto(page, "资料阅读");
  await shot(page, "1440x900-dark-library");
  await goto(page, "收件箱");
  await page.waitForSelector(".inbox-list li", { timeout: 10000 });
  await page.locator(".inbox-list .inbox-item").first().click();
  await page.waitForTimeout(500);
  await shot(page, "1440x900-dark-inbox");
  await captureStats(page, "1440x900-dark-stats");
  await captureSettings(page, "1440x900-dark-settings");
  await tourProjectViews("dark");
  await tourDialogs(page, "dark");

  // 第五步：1024x768 窄窗口抽查（浅色，四个新页面）
  await setTheme(page, "light");
  await setViewportSize(third.app, page, 1024, 768);
  await goto(page, "资料阅读");
  await shot(page, "1024x768-light-library");
  await goto(page, "收件箱");
  await page.waitForSelector(".inbox-list li", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(500);
  await shot(page, "1024x768-light-inbox");
  await captureStats(page, "1024x768-light-stats");
  await captureSettings(page, "1024x768-light-settings");

  await third.app.close();

  if (!keepProfile) rmSync(profileDir, { recursive: true, force: true });
  console.log(`[round4-capture] done. Output: ${outDir}`);
}

main().catch((error) => {
  console.error(error);
  // 异常路径强制退出：未关闭的 Electron 实例会挂住事件循环
  process.exit(1);
});
