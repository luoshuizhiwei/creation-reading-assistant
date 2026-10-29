/**
 * BD-215 常驻普查：WCAG 2.2.1 时限可调（Level A），运行时活体实测。
 *
 * 判据口径（W3C Understanding 2.2.1）：由界面设定的时限——这里指提示（toast）
 * 到点自动消失——必须允许用户关停、调节或**延长**。toast 是错误原因的唯一落点
 * （导出失败、导入失败、保存失败……），6.5 秒后连同读屏播报一起消失，用户既不能
 * 暂停也不能延长，就是一条实打实的 2.2.1 缺口。关闭键只解决「缩短」，不算延长。
 *
 * 本普查用卡片库的确定性失败路径造出真实 error toast（新建全局卡片 → 空名点
 * 「创建卡片」），实测三件事：
 *   ① 不干预时按默认时长消失（倒计时仍然有效，修复不能把它冻住）；
 *   ② 指针悬停在提示上超过默认时长仍不消失（延长）；
 *   ③ 键盘焦点停在提示内超过默认时长仍不消失（键盘用户的延长路径），
 *      且指针/焦点离开后倒计时接回、提示正常消失。
 *
 * 红判据：任一场景不符合即 TIME_LIMIT_RED，exit 1。
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-time-limit-audit-"));
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

await page.locator(".desktop-sidebar button", { hasText: "卡片库" }).first().click();
await page.waitForSelector(".global-card-library", { timeout: 15000 });
await page.getByRole("button", { name: /新建全局卡片/ }).first().click();
await page.waitForSelector(".cards-form-actions", { timeout: 10000 });

// 空名保存 → 真实 error toast（6.5 秒档），不写任何数据。
const clearToasts = async () => {
  for (let guard = 0; guard < 4; guard += 1) {
    const close = page.locator(".motion-toast button").first();
    if (!(await close.isVisible().catch(() => false))) return;
    await close.click().catch(() => {});
    await page.waitForTimeout(200);
  }
};
const showErrorToast = async () => {
  await clearToasts();
  await page.getByRole("button", { name: "创建卡片" }).first().click();
  const toast = page.locator(".motion-toast").first();
  await toast.waitFor({ state: "visible", timeout: 5000 });
  return toast;
};
const staysVisible = async (toast, ms) => {
  await page.waitForTimeout(ms);
  return toast.isVisible().catch(() => false);
};
const disappears = async (toast, ms) => {
  try {
    await toast.waitFor({ state: "detached", timeout: ms });
    return true;
  } catch {
    return false;
  }
};

const reds = [];
let judged = 0;

// ① 默认时长仍然生效：不悬停、不聚焦时，error toast 应在 6.5 秒后消失。
judged += 1;
{
  const toast = await showErrorToast();
  if (!(await disappears(toast, 9000))) reds.push("默认情况下 6.5 秒后仍未消失（倒计时被冻住了）");
}

// ② 悬停延长：指针停在提示上 9 秒（> 6.5 秒）仍须可见；离开后按剩余时长续计并消失。
judged += 1;
{
  const toast = await showErrorToast();
  await toast.hover();
  if (!(await staysVisible(toast, 9000))) reds.push("指针悬停时提示仍在 6.5 秒内消失（2.2.1 无法延长）");
  else {
    await page.mouse.move(12, 500);
    if (!(await disappears(toast, 9000))) reds.push("指针离开提示后倒计时没有接回（提示永不消失）");
  }
}

// ③ 焦点延长：键盘焦点停在关闭键上 9 秒仍须可见；失焦后按剩余时长续计并消失。
judged += 1;
{
  const toast = await showErrorToast();
  const close = toast.locator("button").first();
  await close.evaluate((el) => el.focus());
  if (!(await staysVisible(toast, 9000))) reds.push("键盘焦点停在提示内时仍在 6.5 秒内消失（键盘无延长路径）");
  else {
    await close.evaluate((el) => el.blur());
    if (!(await disappears(toast, 9000))) reds.push("焦点离开提示后倒计时没有接回（提示永不消失）");
  }
}

console.log(`计时场景 ${judged} 个：默认消失 / 悬停延长 / 焦点延长`);
if (reds.length) {
  for (const red of reds) console.log(`  - ${red}`);
  console.log(`TIME_LIMIT_RED：${reds.length} 处提示的时限无法暂停或延长（2.2.1）。`);
  await app.close();
  process.exit(1);
}
console.log("TIME_LIMIT_OK：提示可悬停/聚焦暂停并延长，离开后倒计时正常接回。");
await app.close();
process.exit(0);
