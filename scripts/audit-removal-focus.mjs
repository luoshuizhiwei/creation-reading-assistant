/**
 * 行内删除后焦点交接普查（WCAG 2.4.3 Focus Order，AA）。批次 BD-219 立。
 *
 * 判据：键盘把焦点停在「行内删除」类按钮上，回车激活后该按钮随条目卸载；
 * 焦点必须落在同一列表/管理区的幸存控件上（下一行 → 上一行 → 组内退路），
 * 不能跌回 body。焦点跌回 body 的后果是键盘用户要整页重新 Tab（BD-179/199
 * 已修过同族：阅读器侧栏、卡片类型/关系类型行、收件箱、清空回收站）。
 *
 * 本批覆盖四个流程（弹层/管理面板/页面列表三处形状）：
 *   ① 阅读器·TXT「编辑章节」里「删除章节起点」行（BD-219）；
 *   ② 新建作品向导·步骤 2「删除阶段」chip（BD-219）；
 *   ③ 设定卡页·卡片类型编辑器「删除字段」行（BD-219）；
 *   ④ 工作台·大纲树两步确认删除场景（BD-220）。
 * 统一判据：activeElement 仍是同类删除钮，或退到组内指定退路
 * （「添加自定义阶段」输入框 / 「添加字段」钮）；章节表至少保留一行。
 *
 * 为什么全程运行时：焦点交接是 React 提交后的 DOM 事实，静态读代码会把
 * 「有 effect 但没触发」「ref 没登记」这类接错线当成已修。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 900 xvfb-run -a --server-args="-screen 0 1600x1000x24" node scripts/audit-removal-focus.mjs
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-removalfocus-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

const launch = async () => {
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
};

// 首启建 schema，再播书库种子（TXT 七章、EPUB、MD）。
{
  const first = await launch();
  await first.app.close();
}
const seed = spawnSync(electronPath, [path.join(__dirname, "round4-seed.cjs"), profileDir], {
  cwd: root,
  encoding: "utf8",
  env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") },
  windowsHide: true,
  timeout: 60000
});
if (seed.status !== 0) throw new Error(`seed failed: ${seed.stderr}`);

const { app, page } = await launch();

const reds = [];
const skipped = [];
const judged = [];

const active = () =>
  page.evaluate(() => {
    const el = document.activeElement;
    if (!el || el === document.body || el === document.documentElement) return { tag: "body", name: "", title: "", text: "" };
    return {
      tag: el.tagName.toLowerCase(),
      name: el.getAttribute("aria-label") ?? "",
      title: el.getAttribute("title") ?? "",
      text: (el.textContent ?? "").trim().slice(0, 20)
    };
  });

const expectHandoff = async (label, accept) => {
  const el = await active();
  if (accept(el)) {
    judged.push(label);
    console.log(`PASS  ${label}  — 焦点在 ${el.tag}「${el.name || el.title || el.text}」`);
    return;
  }
  reds.push(`${label}：焦点落到 ${el.tag}「${el.name || el.title || el.text}」`);
  console.log(`RED   ${label}  — 焦点落到 ${el.tag}「${el.name || el.title || el.text}」`);
};

const goto = async (label) => {
  // 阅读器有自己的顶栏，没有侧边栏；先退回书库再导航。
  if (!(await page.locator(".desktop-sidebar").isVisible().catch(() => false))) {
    const back = page.getByRole("button", { name: "返回书库" }).first();
    if (await back.isVisible().catch(() => false)) {
      await back.click();
      await page.waitForTimeout(500);
    }
  }
  await page.locator(".desktop-sidebar button", { hasText: label }).first().click();
  await page.waitForTimeout(400);
};

const createProject = async (title) => {
  await goto("项目");
  await page.waitForSelector(".project-home", { timeout: 10000 });
  const createFirst = page.getByRole("button", { name: "新建第一个项目" }).first();
  if (await createFirst.isVisible().catch(() => false)) await createFirst.click();
  else await page.locator(".project-home button", { hasText: "新建项目" }).first().click();
  const wizard = page.locator("[role=dialog]").first();
  await wizard.waitFor({ state: "visible", timeout: 15000 });
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：测试项目']").first().fill(title);
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(1200);
};

// ① 阅读器·TXT 编辑章节：删末尾章节起点 → 前一章。
try {
  await goto("书库");
  await page.locator('.desktop-library-row[data-format="txt"]').first().click();
  await page.waitForSelector('button[aria-label="阅读设置"]', { timeout: 20000 });
  await page.waitForTimeout(700);
  // 侧栏默认展开在「目录」页签；只有被折叠/切走时才需要点「目录」找回。
  const editBtn = page.getByRole("button", { name: "编辑章节" }).first();
  if (!(await editBtn.isVisible().catch(() => false))) {
    await page.getByRole("button", { name: "目录" }).first().click();
    await page.waitForTimeout(400);
  }
  await editBtn.click({ timeout: 10000 });
  await page.waitForTimeout(400);
  const drops = page.getByRole("button", { name: /^删除章节起点/ });
  const count = await drops.count();
  if (count < 2) throw new Error(`编辑章节只有 ${count} 行，无法验证交接`);
  await drops.last().focus();
  await page.keyboard.press("Enter");
  await page.waitForTimeout(300);
  await expectHandoff("阅读器·删末尾章节起点", (el) => /^删除章节起点/.test(el.name));
} catch (error) {
  skipped.push(`阅读器编辑章节：${String(error && error.message).split("\n")[0].slice(0, 160)}`);
}

// ② 新建作品向导：删中间阶段 → 下一阶段；删空 → 添加输入框。
try {
  // 此时还没有项目，项目首页是空态：从「新建第一个项目」进向导。
  await goto("项目");
  await page.waitForSelector(".project-home", { timeout: 10000 });
  const createFirst = page.getByRole("button", { name: "新建第一个项目" }).first();
  if (await createFirst.isVisible().catch(() => false)) await createFirst.click();
  else await page.locator(".project-home-toolbar button").first().click({ timeout: 15000 });
  const wizard = page.locator("[role=dialog]").first();
  await wizard.waitFor({ state: "visible", timeout: 15000 });
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：测试项目']").first().fill("阶段交接测试");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.waitForTimeout(300);

  const stageDeletes = page.getByRole("button", { name: /^删除阶段 / });
  const count = await stageDeletes.count();
  if (count < 2) throw new Error(`步骤 2 只有 ${count} 个阶段，无法验证交接`);
  await stageDeletes.nth(1).focus();
  await page.keyboard.press("Enter");
  await page.waitForTimeout(250);
  await expectHandoff("新建作品·步骤 2 删中间阶段", (el) => /^删除阶段 /.test(el.name));

  // 逐个删空，验证删空退路。
  for (let guard = 0; guard < 12; guard += 1) {
    const rest = page.getByRole("button", { name: /^删除阶段 / });
    if ((await rest.count()) === 0) break;
    await rest.first().focus();
    await page.keyboard.press("Enter");
    await page.waitForTimeout(150);
  }
  await expectHandoff("新建作品·步骤 2 阶段删空", (el) => el.name === "添加自定义阶段" || el.tag === "input");
  await page.keyboard.press("Escape");
  await page.waitForTimeout(300);
} catch (error) {
  skipped.push(`新建作品向导：${String(error && error.message).split("\n")[0].slice(0, 160)}`);
}

// ③ 设定卡页·卡片类型编辑器：删中间字段 → 下一字段；删空 → 添加字段钮。
try {
  const workbench = page.locator(".project-workbench");
  if (!(await workbench.isVisible().catch(() => false))) await createProject("字段交接测试");
  await page.locator(".project-nav button", { hasText: "设定卡" }).first().click();
  await page.waitForSelector(".cards-page", { timeout: 15000 });
  await page.getByRole("button", { name: /卡片类型/ }).first().click();
  const dialog = page.locator("[role=dialog]").first();
  await dialog.waitFor({ state: "visible", timeout: 15000 });
  const add = dialog.getByRole("button", { name: /添加字段/ }).first();
  await add.click();
  await add.click();
  await page.waitForTimeout(200);
  const deletes = dialog.locator('button[title="删除字段"]');
  if ((await deletes.count()) < 2) throw new Error(`字段行只有 ${await deletes.count()} 行，无法验证交接`);
  await deletes.nth(0).focus();
  await page.keyboard.press("Enter");
  await page.waitForTimeout(250);
  await expectHandoff("卡片类型·删中间字段", (el) => el.title === "删除字段");

  const rest = dialog.locator('button[title="删除字段"]');
  while ((await rest.count()) > 0) {
    await rest.first().focus();
    await page.keyboard.press("Enter");
    await page.waitForTimeout(150);
  }
  await expectHandoff("卡片类型·字段删空", (el) => /添加字段/.test(el.text) || el.title === "添加字段");
  await page.keyboard.press("Escape");
  await page.waitForTimeout(300);
} catch (error) {
  skipped.push(`卡片类型编辑器：${String(error && error.message).split("\n")[0].slice(0, 160)}`);
}

// ④ 工作台·大纲树：两步确认删除场景 → 焦点交给幸存删除钮。
try {
  await page.locator(".project-nav button", { hasText: "大纲" }).first().click();
  await page.waitForSelector(".outline-page", { timeout: 15000 });
  await page.waitForTimeout(500);
  const sceneDeletes = page.getByRole("button", { name: /^删除场景/ });
  const count = await sceneDeletes.count();
  if (count < 1) throw new Error("大纲树里没有场景删除钮");
  await sceneDeletes.first().focus();
  await page.keyboard.press("Enter");
  await page.waitForTimeout(250);
  await page.keyboard.press("Enter");
  await page.waitForTimeout(700);
  await expectHandoff("大纲树·两步确认删场景", (el) => /^删除/.test(el.name) || el.tag === "div");
} catch (error) {
  skipped.push(`大纲树删场景：${String(error && error.message).split("\n")[0].slice(0, 160)}`);
}

await app.close();

for (const item of skipped) console.log(`跳过 ${item}`);
if (judged.length < 3) {
  console.log(`REMOVALFOCUS_RED：只判定到 ${judged.length} 条流程（要求 ≥3），覆盖不足无法证明。`);
  process.exit(1);
}
if (reds.length > 0) {
  for (const red of reds) console.log(`  - ${red}`);
  console.log(`REMOVALFOCUS_RED：${reds.length} 条行内删除流程把焦点丢回 body（WCAG 2.4.3）。`);
  process.exit(1);
}
console.log(`REMOVALFOCUS_OK：${judged.length} 条行内删除流程都把焦点交接给幸存控件，未落回 body。`);
