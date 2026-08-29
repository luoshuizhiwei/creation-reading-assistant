/**
 * 第 10 轮探针：演示项目端到端（Novalist demo_novel 对标）。
 *  1. 空态「载入演示项目」一键创建并打开项目。
 *  2. 写作台场景雷达展示任务卡字段（视角/目标）与字数进度。
 *  3. 伏笔生命周期可见：引用 chip 带「未回收/已回收」+ 全书待回收统计。
 * 前置：npm run build。
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-r10probe-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", "electron.exe");
const mainJs = path.join(root, "out", "main", "index.js");

const failures = [];
const check = (name, ok, detail) => {
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? `  — ${detail}` : ""}`);
  if (!ok) failures.push(name);
};

const first = await electron.launch({
  executablePath: electronPath,
  args: [mainJs],
  cwd: root,
  env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir }
}).then(async (app) => ({ app, page: await app.firstWindow() }));
await first.page.waitForLoadState("domcontentloaded");
await first.page.waitForSelector(".desktop-canvas", { timeout: 30000 });
await first.app.close();

const seed = spawnSync(electronPath, [path.join(__dirname, "round4-seed.cjs"), profileDir], {
  cwd: root, encoding: "utf8",
  env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") },
  windowsHide: true, timeout: 60000
});
if (seed.status !== 0) throw new Error(`seed failed: ${seed.stderr}`);

let consoleTail = [];
const { app, page } = await electron.launch({
  executablePath: electronPath,
  args: [mainJs],
  cwd: root,
  env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir }
}).then(async (a) => ({ app: a, page: await a.firstWindow() }));
page.on("console", (message) => {
  const text = message.text();
  if (text && !text.startsWith("[vite]")) consoleTail.push(text.slice(0, 200));
});
await page.waitForLoadState("domcontentloaded");
await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
await page.waitForTimeout(600);

// 1. 项目空态 → 载入演示项目
await page.locator(".desktop-sidebar button", { hasText: "项目" }).first().click();
await page.waitForTimeout(600);
const demoBtn = page.locator(".project-home-create-demo");
const demoVisible = await demoBtn.isVisible().catch(() => false);
check("空态提供「载入演示项目」按钮", demoVisible);
if (!demoVisible) {
  await app.close();
  rmSync(profileDir, { recursive: true, force: true });
  process.exit(1);
}
await demoBtn.click();
await page.waitForSelector(".overview-page", { timeout: 20000 });
await page.waitForTimeout(800);
check("一键创建并打开演示项目", true);

// 2. 写作台雷达
await page.locator(".project-nav button", { hasText: "写作" }).first().click();
await page.waitForSelector(".writing-desk", { timeout: 10000 });
await page.waitForTimeout(600);
// store 没有默认选中场景：先点左树第一个场景，再读雷达。
const firstScene = page.locator(".writing-outline .outline-scene-main").first();
await firstScene.click().catch(() => {});
await page.waitForTimeout(1200);
const radar = await page.evaluate(() => {
  const el = document.querySelector('[data-testid="scene-radar"]');
  return {
    exists: !!el,
    text: el?.textContent ?? "",
    hasProgress: !!el?.querySelector(".scene-radar-progressbar"),
    foreshadowOpenChip: !!el?.querySelector(".scene-radar-tag.foreshadow-open"),
    foreshadowResolvedChip: !!el?.querySelector(".scene-radar-tag.foreshadow-resolved")
  };
});
check("雷达已展示任务卡字段", radar.exists && radar.text.includes("查明借书卡的主人"), radar.text.slice(0, 60));
check("雷达有字数目标进度条", radar.hasProgress);
check("伏笔「未回收」chip 高亮（场景1）", radar.foreshadowOpenChip);
check("全书待回收统计出现", radar.text.includes("待回收伏笔"));

// AI 助手行（D-C2 切片 2）：未启用时按钮禁用并给引导
const aiRow = await page.evaluate(() => {
  const row = document.querySelector('[data-testid="scene-ai-row"]');
  const buttons = row ? [...row.querySelectorAll("button")] : [];
  return {
    exists: !!row,
    hint: row?.querySelector(".scene-radar-ai-hint")?.textContent ?? null,
    allDisabled: buttons.length === 3 && buttons.every((button) => button.disabled)
  };
});
check("AI 助手行渲染且未启用时禁用", aiRow.exists && aiRow.allDisabled && (aiRow.hint ?? "").includes("AI"), JSON.stringify(aiRow));

// 切到场景 3（已回收伏笔的批注所在场景）验证「已回收」chip（轮询等待 store 刷新）
await page.locator(".writing-outline .outline-scene-main").nth(2).click();
let resolvedSeen = false;
for (let attempt = 0; attempt < 10; attempt += 1) {
  resolvedSeen = await page.evaluate(() => !!document.querySelector('[data-testid="scene-radar"] .scene-radar-tag.foreshadow-resolved'));
  if (resolvedSeen) break;
  await page.waitForTimeout(500);
}
check("伏笔「已回收」chip 置灰（场景3）", resolvedSeen);

// 3. 大纲页任务卡已预填
await page.locator(".project-nav button", { hasText: "大纲" }).first().click();
await page.waitForSelector(".outline-page", { timeout: 10000 });
await page.waitForTimeout(400);
// 大纲页同样先选中场景，再读任务卡表单
const outlineScene = page.locator(".outline-page .outline-scene-main").first();
await outlineScene.click().catch(() => {});
await page.waitForTimeout(700);
const outlineState = await page.evaluate(() => {
  const form = document.querySelector(".scene-planning-form");
  return { formExists: !!form, time: form?.querySelector("input")?.value ?? null };
});
check("大纲任务卡已预填（时间字段）", outlineState.formExists && outlineState.time === "闭馆前的雨夜",
  String(outlineState.time));

// 4. 演示项目不出现在按钮所在空态（已有项目后按钮消失）
await page.locator(".project-nav-back").click();
await page.waitForTimeout(700);
const demoGone = !(await page.locator(".project-home-create-demo").isVisible().catch(() => false));
check("有项目后演示按钮不再出现", demoGone);

await app.close();
if (process.env.R10_KEEP) console.log(`[probe-round10] profile kept: ${profileDir}`);
else rmSync(profileDir, { recursive: true, force: true });

if (failures.length) {
  console.error(`\n${failures.length} 项未通过: ${failures.join(" | ")}`);
  process.exit(1);
}
console.log("\n[probe-round10] 全部通过");
