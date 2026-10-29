/**
 * BD-217 常驻普查：WCAG 1.3.3 感官特性（Level A），静态源码 + 运行时活体双判据。
 *
 * 判据口径（W3C Understanding 1.3.3）：说明、操作提示不能**只靠形状、大小、视觉位置、
 * 方位或声音**来指向控件——「在左侧选择」「点击上方按钮」「在下方保存」对看屏幕的人
 * 是坐标，对读屏器、屏幕放大、小屏重排、色觉/低视力用户则是指不到的方位；一旦布局
 * 随窗口宽度改列，句子还会直接说错。要求是指名控件或区域（按钮文字、字段标签、区块名）。
 *
 * 两条腿：
 *   ① 静态扫 src/**\/*.{ts,tsx}：用 TypeScript AST 取「用户看得见的文案」——
 *      JSX 文本、字符串字面量、无插值模板。注释天然不在 AST 里，所以「左侧列表……」
 *      这类代码注释不会被误判；import 路径与 CSS 类名也不会含中文方位词。
 *   ② 运行时扫 Electron 真窗口：逐个页面级视图遍历可见文本节点（跳过 aria-hidden、
 *      零尺寸、visibility:hidden），抓方位词。静态能覆盖条件分支里的文案，运行时覆盖
 *      「此刻真的画在屏幕上」的那部分，包括种子数据带出来的文字。
 *
 * 红判据：任一腿出现 左侧/右侧/上方/下方 即 SENSORY_RED，exit 1。
 */
import { readFileSync, readdirSync, statSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";
import ts from "typescript";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const srcRoot = path.join(root, "src");
const TOKENS = ["左侧", "右侧", "上方", "下方"];

/* ------------------------------------------------ ① 静态：可见文案里的方位词 */

const walk = (dir) => {
  const out = [];
  for (const name of readdirSync(dir)) {
    const full = path.join(dir, name);
    if (statSync(full).isDirectory()) {
      if (name === "__tests__" || name === "node_modules") continue;
      out.push(...walk(full));
      continue;
    }
    if (!/\.(ts|tsx)$/.test(name)) continue;
    if (/\.(test|spec)\.(ts|tsx)$/.test(name) || /\.d\.ts$/.test(name)) continue;
    out.push(full);
  }
  return out;
};

const files = walk(srcRoot);
const staticHits = [];
for (const file of files) {
  const text = readFileSync(file, "utf8");
  const sf = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true, file.endsWith(".tsx") ? ts.ScriptKind.TSX : ts.ScriptKind.TS);
  const visit = (node) => {
    let value = null;
    let at = node;
    if (ts.isJsxText(node)) value = node.getText(sf);
    else if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) value = node.text;
    if (typeof value === "string") {
      for (const token of TOKENS) {
        if (!value.includes(token)) continue;
        const { line } = sf.getLineAndCharacterOfPosition(at.getStart(sf));
        staticHits.push({
          file: path.relative(root, file).split(path.sep).join("/"),
          line: line + 1,
          token,
          snippet: value.replace(/\s+/g, " ").trim().slice(0, 70)
        });
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(sf);
}

console.log(`静态扫描 ${files.length} 个源文件（AST 只取 JSX 文本 / 字符串字面量 / 无插值模板）：方位词 ${staticHits.length} 处`);
for (const hit of staticHits) console.log(`  · ${hit.file}:${hit.line} [${hit.token}] ${hit.snippet}`);

/* ------------------------------------------------ ② 运行时：真窗口可见文本 */

const profileDir = mkdtempSync(path.join(tmpdir(), "creation-sensory-audit-"));
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

const goto = async (label) => {
  await page.locator(".desktop-sidebar button", { hasText: label }).first().click();
  await page.waitForTimeout(400);
};

async function createProject() {
  await goto("项目");
  await page.waitForSelector(".project-home", { timeout: 10000 });
  const createFirst = page.getByRole("button", { name: "新建第一个项目" }).first();
  if (await createFirst.isVisible()) await createFirst.click();
  else await page.getByRole("button", { name: /新建项目/ }).first().click();
  const wizard = page.locator("[role=dialog]").first();
  await wizard.waitFor({ state: "visible", timeout: 15000 });
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：测试项目']").first().fill("感官说明普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(1200);
}

const inWorkbench = async () => {
  if (await page.locator(".project-workbench").isVisible().catch(() => false)) return;
  await createProject();
};

const workbenchView = async (navLabel, selector) => {
  await inWorkbench();
  await page.locator(".project-nav button", { hasText: navLabel }).first().click();
  await page.waitForSelector(selector, { timeout: 15000 });
  await page.waitForTimeout(400);
};

const collect = () => {
  const tokens = ["左侧", "右侧", "上方", "下方"];
  const hits = [];
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  while (walker.nextNode()) {
    const node = walker.currentNode;
    const text = (node.nodeValue || "").trim();
    if (!text) continue;
    const token = tokens.find((t) => text.includes(t));
    if (!token) continue;
    const el = node.parentElement;
    if (!el || el.closest('[aria-hidden="true"]')) continue;
    const rect = el.getBoundingClientRect();
    if (rect.width < 1 || rect.height < 1) continue;
    if (getComputedStyle(el).visibility === "hidden") continue;
    hits.push(`${token}｜${text.replace(/\s+/g, " ").slice(0, 80)}`);
  }
  return [...new Set(hits)];
};

const runtimeHits = [];
let judged = 0;
const scan = async (label) => {
  judged += 1;
  let hits;
  try {
    hits = await page.evaluate(collect);
  } catch (error) {
    runtimeHits.push(`${label}：扫描失败 ${String((error && error.message) || error).split("\n")[0].slice(0, 90)}`);
    return;
  }
  for (const hit of hits) runtimeHits.push(`${label}：${hit}`);
};

const scopes = [
  ["项目·首页", async () => {
    await goto("项目");
    const back = page.locator(".project-nav-back").first();
    if (await back.isVisible().catch(() => false)) await back.click();
    await page.waitForSelector(".project-home", { timeout: 10000 });
  }],
  ["工作台·概览", async () => workbenchView("概览", ".overview-page")],
  ["工作台·写作", async () => workbenchView("写作", ".writing-desk")],
  ["工作台·大纲", async () => workbenchView("大纲", ".outline-page")],
  ["工作台·设定卡", async () => workbenchView("设定卡", ".cards-page")],
  ["收件箱", async () => {
    await goto("收件箱");
    await page.waitForSelector(".inbox-page", { timeout: 10000 });
  }],
  ["卡片库", async () => {
    await goto("卡片库");
    await page.waitForSelector(".cards-page.global-card-library", { timeout: 10000 });
  }],
  ["书库", async () => {
    await goto("书库");
    await page.waitForSelector(".desktop-library-row", { timeout: 10000 });
  }],
  ["设置·AI（启用但未配 Key）", async () => {
    await goto("设置");
    await page.getByRole("button", { name: "AI 助手" }).first().click();
    const sw = page.getByRole("switch", { name: "启用 AI 助手" }).first();
    await sw.waitFor({ state: "attached", timeout: 10000 });
    // 原生 checkbox 被样式压成 1×1 的 sr-only，指针命中的是视觉轨道；
    // 用 DOM click 触发 change，等价于点标签，且不依赖命中测试。
    if (!(await sw.isChecked())) await sw.evaluate((el) => el.click());
    await page.waitForTimeout(800);
  }],
  ["资料阅读", async () => {
    await goto("书库");
    await page.locator('.desktop-library-row[data-format="txt"]').first().click();
    await page.waitForSelector("button[aria-label=\"阅读设置\"]", { timeout: 20000 });
    await page.waitForTimeout(800);
  }]
];

for (const [label, prep] of scopes) {
  try {
    await prep();
    await scan(label);
  } catch (error) {
    console.log(`跳过「${label}」：${String((error && error.message) || error).split("\n")[0].slice(0, 110)}`);
  }
}

console.log(`运行时扫描 ${judged} 个视图；方位词 ${runtimeHits.length} 处`);
for (const hit of runtimeHits) console.log(`  · ${hit}`);

const reds = staticHits.length + runtimeHits.length;
if (judged < 8) {
  console.log(`SENSORY_RED：只判定到 ${judged} 个视图（要求 ≥8），覆盖不足无法证明。`);
  await app.close();
  process.exit(1);
}
if (reds > 0) {
  console.log(`SENSORY_RED：${reds} 处文案仍在用方位指代控件（1.3.3）。`);
  await app.close();
  process.exit(1);
}
console.log("SENSORY_OK：静态与运行时的可见文案都不再用「左侧/右侧/上方/下方」指代控件或区域。");
await app.close();
process.exit(0);
