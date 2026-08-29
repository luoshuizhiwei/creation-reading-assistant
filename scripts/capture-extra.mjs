/**
 * 补充截图：全局搜索弹层（Ctrl+K，light/dark）+ 灵感兼容入口（light/dark）。
 * 前置：npm run build。输出到 scripts/visual-evidence-r4-pages/。
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-extra-"));
const outDir = path.join(root, "scripts", "visual-evidence-r4-pages");
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

const setTheme = (page, theme) => page.evaluate((t) => { document.documentElement.dataset.appTheme = t; }, theme);
const shot = async (page, name) => {
  await page.screenshot({ path: path.join(outDir, `${name}.png`) });
  console.log(`[capture-extra] saved ${name}.png`);
};

const first = await launch();
await first.app.close();

const seed = spawnSync(electronPath, [path.join(__dirname, "round4-seed.cjs"), profileDir], {
  cwd: root, encoding: "utf8",
  env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") },
  windowsHide: true, timeout: 60000
});
if (seed.status !== 0) throw new Error(`seed failed: ${seed.stderr}`);

const { app, page } = await launch();

for (const theme of ["light", "dark"]) {
  await setTheme(page, theme);
  // 全局搜索弹层
  await page.keyboard.press("Control+k");
  await page.waitForTimeout(800);
  await shot(page, `1440x900-${theme}-search-overlay`);
  await page.keyboard.press("Escape");
  await page.waitForTimeout(400);
  // 灵感兼容入口（侧边栏若有「灵感」项则点击，否则跳过）
  const inspirationBtn = page.locator(".desktop-sidebar button", { hasText: "灵感" }).first();
  if (await inspirationBtn.isVisible().catch(() => false)) {
    await inspirationBtn.click();
    await page.waitForTimeout(700);
    await shot(page, `1440x900-${theme}-inspiration`);
  }
}

await app.close();
rmSync(profileDir, { recursive: true, force: true });
console.log("[capture-extra] done");
