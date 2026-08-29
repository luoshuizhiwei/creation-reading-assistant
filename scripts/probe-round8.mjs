/**
 * 第 8 轮探针：
 *  1. TXT/EPUB 阅读器无底部横向滚动条（滚动容器 scrollWidth <= clientWidth，水平滚动条不可见）。
 *  2. 写作台 scene-editor-toolbar 在 1360 / 1024 宽度下不被截断。
 * 前置：npm run build。
 * 用法：node scripts/probe-round8.mjs
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-r8probe-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", "electron.exe");
const mainJs = path.join(root, "out", "main", "index.js");

const failures = [];
const check = (name, ok, detail) => {
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? `  — ${detail}` : ""}`);
  if (!ok) failures.push(name);
};

async function launch() {
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
}

async function goto(page, label) {
  await page.locator(".desktop-sidebar button", { hasText: label }).first().click();
  await page.waitForTimeout(400);
}

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

async function openReader(page, format) {
  await goto(page, "资料阅读");
  await page.waitForSelector(".desktop-library-row", { timeout: 10000 });
  await page.locator(`.desktop-library-row[data-format="${format}"]`).first().click();
  await page.waitForSelector("header.paper-topbar", { timeout: 20000 });
  await page.waitForTimeout(format === "epub" ? 4000 : 1500);
}

const first = await launch();
await first.app.close();

const seed = spawnSync(electronPath, [path.join(__dirname, "round4-seed.cjs"), profileDir], {
  cwd: root, encoding: "utf8",
  env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") },
  windowsHide: true, timeout: 60000
});
if (seed.status !== 0) throw new Error(`seed failed: ${seed.stderr}`);

const { app, page } = await launch();

// ---------- 1. 阅读器横向溢出 ----------
for (const format of ["txt", "epub"]) {
  await openReader(page, format);
  const ovf = await page.evaluate(() => {
    // 找出所有可滚动容器，检查横向溢出与水平滚动条占用高度
    const scrollers = [document.querySelector(".reader-root [class*='overflow'], .reader-root article")];
    const results = [];
    const root = document.querySelector(".reader-root");
    if (root) {
      for (const el of [root, ...root.querySelectorAll("div, article, section")]) {
        if (el.scrollWidth > el.clientWidth + 1 && el.clientWidth > 0) {
          const s = getComputedStyle(el);
          if (s.overflowX === "visible") continue; // 不裁剪也不滚动的容器无所谓
          results.push({
            cls: String(el.className).slice(0, 70),
            scrollW: el.scrollWidth,
            clientW: el.clientWidth,
            barH: el.offsetHeight - el.clientHeight
          });
        }
      }
    }
    return results;
  });
  check(`${format} 阅读器无横向溢出容器`, ovf.length === 0, JSON.stringify(ovf.slice(0, 3)));
  await page.getByRole("button", { name: /返回书库/ }).click();
  await page.waitForTimeout(600);
}

// ---------- 2. 写作台工具栏不截断（1360 + 1024） ----------
await createProject(page);
const setWidth = async (width) => {
  await app.evaluate(({ BrowserWindow }, w) => {
    for (const window of BrowserWindow.getAllWindows()) window.setContentSize(w, 800);
  }, width);
  await page.waitForTimeout(500);
};

for (const width of [1360, 1024]) {
  await setWidth(width);
  await goto(page, "项目");
  const back = page.locator(".project-nav-back");
  if (await back.isVisible().catch(() => false)) await back.click().then(() => goto(page, "项目"));
  await page.locator(".project-home-item-main").first().click();
  await page.waitForSelector(".overview-page", { timeout: 10000 });
  await page.locator(".project-nav button", { hasText: "写作" }).first().click();
  await page.waitForSelector(".scene-editor-toolbar", { timeout: 10000 });
  await page.waitForTimeout(500);
  const tb = await page.evaluate(() => {
    const el = document.querySelector(".scene-editor-toolbar");
    const r = el.getBoundingClientRect();
    // 工具栏内部是否需要滚动/溢出（截断的信号）
    return {
      scrollW: el.scrollWidth,
      clientW: el.clientWidth,
      left: Math.round(r.left),
      right: Math.round(window.innerWidth - r.right),
      clipped: el.scrollWidth > el.clientWidth + 1
    };
  });
  check(`${width}px 写作台工具栏不截断`, !tb.clipped && tb.left >= 0 && tb.right >= 0,
    `scrollW=${tb.scrollW} clientW=${tb.clientW} left=${tb.left} right=${tb.right}`);
}

await app.close();
rmSync(profileDir, { recursive: true, force: true });

if (failures.length) {
  console.error(`\n${failures.length} 项未通过: ${failures.join(" | ")}`);
  process.exit(1);
}
console.log("\n[probe-round8] 全部通过");
