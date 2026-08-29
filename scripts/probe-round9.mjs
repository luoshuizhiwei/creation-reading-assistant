/**
 * 第 9 轮探针：场景雷达（D-C1 v1）端到端行为。
 *  1. 写作台右栏有「场景雷达/批注与引用」双页签，默认雷达。
 *  2. 无任务卡时空态引导可见，点「前往大纲填写」跳转大纲页。
 *  3. 在大纲页填写任务卡后回到写作台，雷达展示字段与目标进度。
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
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-r9probe-"));
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
  await page.locator(".creation-field input").first().fill("雷达测试项目");
  await page.getByRole("button", { name: /下一步/ }).click();
  await page.waitForTimeout(250);
  await page.locator("input[placeholder='例如：500000']").fill("50000");
  await page.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForSelector(".project-nav-back", { timeout: 15000 });
  await page.waitForTimeout(700);
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
await createProject(page);

// 1. 默认雷达页签 + 空态
await page.locator(".project-nav button", { hasText: "写作" }).first().click();
await page.waitForSelector(".writing-desk", { timeout: 10000 });
await page.waitForTimeout(600);
const tabs = await page.evaluate(() => {
  const tabButtons = [...document.querySelectorAll(".writing-margin-tabs button")].map((b) => b.textContent?.trim());
  const radar = document.querySelector('[data-testid="scene-radar"]');
  const empty = radar?.querySelector(".scene-radar-empty");
  return {
    tabButtons,
    radarDefault: !!radar,
    emptyVisible: !!empty,
    emptyText: empty?.textContent?.slice(0, 40) ?? null,
    gotoBtn: !!radar?.querySelector(".scene-radar-empty button")
  };
});
check("右栏双页签（默认场景雷达）", tabs.tabButtons?.join(",") === "场景雷达,批注与引用" && tabs.radarDefault,
  JSON.stringify(tabs.tabButtons));
check("无任务卡时空态引导可见", tabs.emptyVisible && tabs.emptyVisible !== null, tabs.emptyText ?? undefined);
check("空态有「前往大纲填写」按钮", tabs.gotoBtn === true);

// 2. 空态按钮跳大纲
await page.locator(".scene-radar-empty button").click();
await page.waitForTimeout(700);
const onOutline = await page.locator(".outline-page").isVisible().catch(() => false);
check("空态按钮跳转大纲页", onOutline);

// 3. 大纲页填写任务卡 → 回写作台看雷达
await page.locator(".outline-page").waitFor({ timeout: 8000 });
// 选中第一个场景（大纲树中的场景按钮）
const sceneBtn = page.locator(".outline-page .outline-scene, .outline-page [class*='scene']").first();
if (await sceneBtn.isVisible().catch(() => false)) await sceneBtn.click();
await page.waitForTimeout(500);
const form = page.locator(".scene-planning-form");
const hasForm = await form.isVisible().catch(() => false);
if (hasForm) {
  // 文本输入顺序：时间、目标、冲突、结果、情绪（数字输入为目标字数）。
  const textInputs = page.locator(".scene-planning-form input:not([type='number'])");
  await textInputs.nth(0).fill("入夜后");
  await textInputs.nth(1).fill("拿到钥匙");
  await page.locator(".scene-planning-form input[type='number']").first().fill("2000");
  await page.locator(".scene-planning-form button", { hasText: /保存任务卡/ }).first().click();
  await page.waitForTimeout(900);
} else {
  console.log("[probe-round9] 大纲页未找到场景任务卡表单（选择器需更新）");
}

await page.locator(".project-nav button", { hasText: "写作" }).first().click();
await page.waitForSelector(".writing-desk", { timeout: 10000 });
await page.waitForTimeout(600);
const radarAfter = await page.evaluate(() => {
  const radar = document.querySelector('[data-testid="scene-radar"]');
  return {
    exists: !!radar,
    emptyGone: !radar?.querySelector(".scene-radar-empty"),
    text: radar?.textContent ?? "",
    hasProgress: !!radar?.querySelector(".scene-radar-progressbar")
  };
});
check("填写任务卡后雷达空态消失", radarAfter.exists && radarAfter.emptyGone);
check("雷达展示任务卡字段", radarAfter.text.includes("入夜后") || radarAfter.text.includes("拿到钥匙"),
  radarAfter.text.slice(0, 80));
check("雷达有目标进度条", radarAfter.hasProgress);

// 4. 页签切换回批注
await page.locator(".writing-margin-tabs button", { hasText: "批注与引用" }).click();
await page.waitForTimeout(400);
const notesVisible = await page.locator(".writing-annotations").isVisible().catch(() => false);
check("切回批注与引用页签", notesVisible);

await app.close();
rmSync(profileDir, { recursive: true, force: true });

if (failures.length) {
  console.error(`\n${failures.length} 项未通过: ${failures.join(" | ")}`);
  process.exit(1);
}
console.log("\n[probe-round9] 全部通过");
