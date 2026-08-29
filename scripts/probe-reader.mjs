/**
 * 一次性诊断：暗色主题下阅读器各层的计算背景/文字色。
 * 用法：node scripts/probe-reader.mjs
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-probe-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", "electron.exe");
const mainJs = path.join(root, "out", "main", "index.js");

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

const first = await launch();
await first.app.close();

const seed = spawnSync(electronPath, [path.join(__dirname, "round4-seed.cjs"), profileDir], {
  cwd: root, encoding: "utf8",
  env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") },
  windowsHide: true, timeout: 60000
});
if (seed.status !== 0) throw new Error(`seed failed: ${seed.stderr}`);

const { app, page } = await launch();
await page.evaluate(() => { document.documentElement.dataset.appTheme = "dark"; });
await page.locator(".desktop-sidebar button", { hasText: "资料阅读" }).first().click();
await page.waitForSelector(".desktop-library-row", { timeout: 10000 });
await page.locator('.desktop-library-row[data-format="txt"]').first().click();
await page.waitForSelector("header.paper-topbar", { timeout: 20000 });
await page.waitForTimeout(1200);

const probe = await page.evaluate(() => {
  const pick = (el) => {
    if (!el) return null;
    const s = getComputedStyle(el);
    return { cls: String(el.className).slice(0, 90), bg: s.backgroundColor, color: s.color };
  };
  const root = document.querySelector(".reader-root");
  const shellEl = root?.querySelector('[class*="reader-shell-"]');
  const paperEl = root?.querySelector('[class*="reader-bg-"]');
  const article = root?.querySelector("article");
  return {
    rootBg: root ? getComputedStyle(root).backgroundColor : null,
    shell: pick(shellEl),
    paper: pick(paperEl),
    article: pick(article),
    shellClassList: shellEl ? [...shellEl.classList] : null
  };
});
console.log(JSON.stringify(probe, null, 2));

await app.close();
rmSync(profileDir, { recursive: true, force: true });
