/**
 * 以可见模式打开应用（供用户亲手查看「朱砚」效果），并截两张确认图。
 * 进程常驻：应用保持打开，直到用户自行关闭或外部终止。
 */
import { _electron as electron } from "playwright-core";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");

const app = await electron.launch({
  executablePath: path.join(root, "node_modules", "electron", "dist", "electron.exe"),
  args: [path.join(root, "out", "main", "index.js")],
  cwd: root
});
const page = await app.firstWindow();
await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
await page.waitForTimeout(1500);

await page.screenshot({ path: path.join(root, "scripts", "tmp-live-library.png") });

// 切到项目首页再截一张（覆盖 hero 与侧栏观感）
await page.locator(".desktop-sidebar button", { hasText: "项目" }).first().click();
await page.waitForTimeout(900);
await page.screenshot({ path: path.join(root, "scripts", "tmp-live-projects.png") });

console.log("[open-live] app is open; screenshots saved.");
// 常驻：让应用一直开着供用户查看
await new Promise(() => {});
