/**
 * BD-216 常驻普查：WCAG 2.5.2 指针取消（Level A），运行时活体实测。
 *
 * 判据口径（W3C Understanding 2.5.2）：单指针操作不能在**按下**那一刻就执行功能——
 * 用户必须能在松开前撤销（up 事件完成、或按下后把指针移开再松开不生效）。此前
 * 三个全屏浮层的遮罩都挂 onMouseDown 关闭：按下去搜索/抽屉/摘录选择器立刻消失，
 * 松手前已无法反悔，拖选误触全算数。
 *
 * 本普查对三个可达浮层各做三件事：
 *   ① 只在遮罩上按下 → 浮层必须还在（按下不算完成）；
 *   ② 随后松开 → 浮层关闭（松开完成，正常路径仍可用）；
 *   ③ 从浮层内部按下、拖到遮罩上松开 → 浮层必须还在（up 事件可撤销）。
 *
 * 红判据：任一场景不符合即 POINTER_CANCEL_RED，exit 1。
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-pointer-cancel-"));
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

await page.locator(".desktop-sidebar button", { hasText: "资料阅读" }).first().click();
await page.waitForSelector('.desktop-library-row[data-format="txt"]', { timeout: 15000 });
await page.locator('.desktop-library-row[data-format="txt"]').first().click();
await page.waitForSelector('button[aria-label="阅读设置"]', { timeout: 20000 });
await page.waitForTimeout(800);

const scopes = 3;
const reds = [];
let judged = 0;

// 遮罩上的三次核对：按下不关 / 松开才关 / 从面板拖出松开不关。
const checkScrim = async (label, openScope, scrim, panelPoint, scrimPoint) => {
  judged += 3;
  await openScope();
  if (!(await scrim.isVisible().catch(() => false))) {
    reds.push(`${label}：打开后找不到浮层（场景失效）`);
    return;
  }
  await page.mouse.move(scrimPoint.x, scrimPoint.y);
  await page.mouse.down();
  await page.waitForTimeout(200);
  if (!(await scrim.isVisible().catch(() => false))) reds.push(`${label}：只在遮罩上按下就关闭了（2.5.2 下压即触发）`);
  await page.mouse.up();
  await page.waitForTimeout(300);
  if (await scrim.isVisible().catch(() => false)) reds.push(`${label}：在遮罩上按下并松开后没有关闭（正常关闭路径失效）`);

  await openScope();
  if (!(await scrim.isVisible().catch(() => false))) {
    reds.push(`${label}：第二次打开失败（场景失效）`);
    return;
  }
  await page.mouse.move(panelPoint.x, panelPoint.y);
  await page.mouse.down();
  await page.mouse.move(scrimPoint.x, scrimPoint.y, { steps: 6 });
  await page.mouse.up();
  await page.waitForTimeout(300);
  if (!(await scrim.isVisible().catch(() => false))) reds.push(`${label}：从浮层内部拖到遮罩松开也关闭了（up 事件无法撤销）`);
  await page.keyboard.press("Escape");
  await page.waitForTimeout(350);
};

// ① 全局搜索（Ctrl+Shift+F）
{
  const openSearch = async () => {
    if (await page.locator(".uni-search-overlay").count()) {
      await page.keyboard.press("Escape");
      await page.waitForTimeout(250);
    }
    await page.keyboard.press("Control+Shift+f");
    await page.waitForSelector(".uni-search-overlay", { timeout: 8000 });
    await page.waitForTimeout(250);
  };
  await checkScrim(
    "全局搜索",
    openSearch,
    page.locator(".uni-search-overlay"),
    await (async () => {
      await openSearch();
      const b = await page.locator(".uni-search-input").boundingBox();
      await page.keyboard.press("Escape");
      await page.waitForTimeout(250);
      return { x: b.x + b.width / 2, y: b.y + b.height / 2 };
    })(),
    { x: 20, y: 830 }
  );
}

// ② 阅读设置抽屉
{
  const drawer = page.locator('[role="dialog"][aria-labelledby="reader-settings-drawer-title"]');
  const openDrawer = async () => {
    if (await drawer.isVisible().catch(() => false)) {
      await page.keyboard.press("Escape");
      await page.waitForTimeout(250);
    }
    await page.locator('button[aria-label="阅读设置"]').first().click();
    await drawer.waitFor({ state: "visible", timeout: 8000 });
    await page.waitForTimeout(250);
  };
  await openDrawer();
  const d = await drawer.boundingBox();
  await page.keyboard.press("Escape");
  await page.waitForTimeout(250);
  await checkScrim("阅读设置抽屉", openDrawer, drawer, { x: d.x + d.width / 2, y: d.y + 120 }, { x: 60, y: 500 });
}

// ③ 资料摘录选择器（三击选中正文 → 工具条「摘录到资料」）
{
  const picker = page.locator('[role="dialog"][aria-label="资料摘录"]');
  const openPicker = async () => {
    if (await picker.isVisible().catch(() => false)) {
      await page.keyboard.press("Escape");
      await page.waitForTimeout(250);
    }
    const para = page.locator(".reader-scroller article p").first();
    const b = await para.boundingBox();
    if (!b) throw new Error("找不到正文段落");
    await page.mouse.click(b.x + 10, b.y + b.height / 2, { clickCount: 3 });
    await page.waitForTimeout(350);
    const button = page.locator('button[title="摘录到资料"]');
    await button.waitFor({ state: "visible", timeout: 5000 });
    await button.first().click();
    await picker.waitFor({ state: "visible", timeout: 8000 });
    await page.waitForTimeout(250);
  };
  await openPicker();
  const p = await picker.boundingBox();
  await page.keyboard.press("Escape");
  await page.waitForTimeout(250);
  await checkScrim("资料摘录选择器", openPicker, picker, { x: p.x + p.width / 2, y: p.y + 100 }, { x: 20, y: 840 });
}

console.log(`浮层 ${scopes} 个、指针动作 ${judged} 次：遮罩关闭全部走「松开后 click + 起点核对」`);
if (reds.length) {
  for (const red of reds) console.log(`  - ${red}`);
  console.log(`POINTER_CANCEL_RED：${reds.length} 处指针动作在按下即生效或无法撤销（2.5.2）。`);
  await app.close();
  process.exit(1);
}
console.log("POINTER_CANCEL_OK：三个浮层都只在「按下且松开都在遮罩上」时关闭，拖出松开不会误关。");
await app.close();
process.exit(0);
