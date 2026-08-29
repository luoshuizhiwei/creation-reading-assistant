/**
 * 第 7 轮一次性验证探针（模型看不了截图，改用几何/样式断言）：
 *  1. 阅读器顶栏「统计」按钮可见且可点击（TXT + EPUB）。
 *  2. 替换面板 fixed 定位：完全落在视口内、不遮写作台工具栏。
 *  3. 暗色下 replace/proof/export 面板文字与背景对比度。
 *  4. 1024x768 窄窗口：书库/收件箱/统计/设置无横向溢出。
 * 用法：node scripts/probe-round7.mjs
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-r7probe-"));
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

function setTheme(page, theme) {
  return page.evaluate((t) => { document.documentElement.dataset.appTheme = t; }, theme);
}

async function goto(page, label) {
  await page.locator(".desktop-sidebar button", { hasText: label }).first().click();
  await page.waitForTimeout(400);
}

async function enterProject(page) {
  await goto(page, "项目");
  if (await page.locator(".project-nav-back").isVisible().catch(() => false)) return;
  await page.waitForSelector(".project-home-item-main", { timeout: 10000 });
  await page.locator(".project-home-item-main").first().click();
  await page.waitForSelector(".overview-page", { timeout: 10000 });
}

/** 工作台内切换到指定视图（项目级导航，非侧边栏）。 */
async function openWorkbenchView(page, label, selector) {
  await page.locator(".project-nav button", { hasText: label }).first().click();
  await page.waitForSelector(selector, { timeout: 10000 });
  await page.waitForTimeout(300);
}

/** 通过向导创建「测试项目」（无项目时走 create-first）。 */
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

// ---------- 1. 阅读器顶栏统计按钮 ----------
async function probeReaderStats(page, theme, format) {
  await setTheme(page, theme);
  await goto(page, "资料阅读");
  await page.waitForSelector(".desktop-library-row", { timeout: 10000 });
  await page.locator(`.desktop-library-row[data-format="${format}"]`).first().click();
  await page.waitForSelector("header.paper-topbar", { timeout: 20000 });
  if (format === "epub") {
    await page.waitForTimeout(4000);
  } else {
    await page.waitForTimeout(1200);
  }
  const btn = page.getByRole("button", { name: "统计" }).first();
  const visible = await btn.isVisible().catch(() => false);
  const box = visible ? await btn.boundingBox() : null;
  check(`${theme}/${format} 顶栏「统计」按钮可见`, visible, box ? `at (${Math.round(box.x)},${Math.round(box.y)})` : "not found");
  if (visible) {
    await btn.click();
    await page.waitForTimeout(1200);
    // 统计屏在 DesktopFrame 内渲染（根为 desktop-page-stack），阅读器 topbar 应消失
    const onStats = await page.evaluate(() => ({
      inFrame: !!document.querySelector(".desktop-sidebar"),
      readerGone: !document.querySelector("header.paper-topbar"),
      stack: !!document.querySelector(".desktop-page-stack")
    }));
    check(`${theme}/${format} 点击统计跳转统计页`, onStats.inFrame && onStats.readerGone && onStats.stack,
      JSON.stringify(onStats));
    await page.getByRole("button", { name: /返回书库/ }).first().click().catch(() => {});
    await page.waitForTimeout(700);
  } else {
    await page.getByRole("button", { name: /返回书库/ }).click().catch(() => {});
    await page.waitForTimeout(500);
  }
}

// ---------- 2. 替换面板几何 ----------
async function probeReplaceGeometry(page, theme) {
  await setTheme(page, theme);
  await enterProject(page);
  await openWorkbenchView(page, "写作", ".writing-desk");
  const toolbarBox = await page.locator(".desktop-page-hero").first().boundingBox();
  const before = await page.evaluate(() => {
    const panel = document.querySelector('[data-testid="replace-panel"]');
    const hero = document.querySelector(".desktop-page-hero")?.getBoundingClientRect();
    const btn = [...document.querySelectorAll("button")].find((b) => b.getAttribute("aria-label") === "查找替换");
    const r = btn?.getBoundingClientRect();
    return {
      panelExists: !!panel,
      heroBottom: hero ? Math.round(hero.bottom) : null,
      btnRect: r ? [Math.round(r.x), Math.round(r.y), Math.round(r.width), Math.round(r.height)] : null
    };
  });
  console.log(`[diag] before click: ${JSON.stringify(before)}`);
  await page.getByRole("button", { name: /查找替换/ }).first().click();
  await page.waitForSelector('[data-testid="replace-panel"]', { timeout: 8000 });
  await page.waitForTimeout(300);
  const geo = await page.evaluate(() => {
    const el = document.querySelector('[data-testid="replace-panel"]');
    const r = el.getBoundingClientRect();
    return { x: r.x, y: r.y, w: r.width, h: r.height, vw: innerWidth, vh: innerHeight,
      pos: getComputedStyle(el).position };
  });
  const inside = geo.x >= 0 && geo.y >= 0 && geo.x + geo.w <= geo.vw + 1 && geo.y + geo.h <= geo.vh + 1;
  check(`${theme} 替换面板 fixed 且在视口内`, geo.pos === "fixed" && inside,
    `pos=${geo.pos} rect=(${Math.round(geo.x)},${Math.round(geo.y)} ${Math.round(geo.w)}x${Math.round(geo.h)}) viewport=${geo.vw}x${geo.vh}`);
  const noOverlap = !toolbarBox || geo.y >= toolbarBox.y + toolbarBox.height - 8;
  check(`${theme} 替换面板不遮 hero 操作行`, noOverlap,
    toolbarBox ? `panelTop=${Math.round(geo.y)} heroBottom=${Math.round(toolbarBox.y + toolbarBox.height)}` : "no hero box");
  // 面板内的文字对比
  const contrast = await page.evaluate(() => {
    const el = document.querySelector('[data-testid="replace-panel"]');
    const cs = getComputedStyle(el);
    const txt = document.querySelector('[data-testid="replace-panel"] input, [data-testid="replace-panel"] h2');
    const ts = txt ? getComputedStyle(txt) : null;
    return { bg: cs.backgroundColor, color: cs.color, title: ts ? ts.color : null };
  });
  // 关闭面板，避免带到下一轮
  await page.getByTestId("replace-panel-close").click().catch(() => {});
  await page.waitForTimeout(300);
  return contrast;
}

// ---------- 3. 暗色对话框对比 ----------
async function probeDialogContrast(page, theme) {
  await setTheme(page, theme);
  await enterProject(page);
  await openWorkbenchView(page, "写作", ".writing-desk");

  await page.getByRole("button", { name: /校对/ }).first().click();
  await page.waitForTimeout(600);
  const proof = await page.evaluate(() => {
    const el = document.querySelector('[role="dialog"][aria-label="本地校对"] .creation-search-shell');
    if (!el) return null;
    const cs = getComputedStyle(el);
    const head = el.querySelector("h2, [class*='title']");
    return { bg: cs.backgroundColor, color: cs.color, titleColor: head ? getComputedStyle(head).color : null, cls: el.className };
  });
  await page.getByRole("button", { name: "关闭校对" }).click();
  await page.waitForTimeout(400);

  await page.getByRole("button", { name: /导出成稿/ }).first().click();
  await page.waitForTimeout(600);
  const exportBox = await page.evaluate(() => {
    const el = document.querySelector('[role="dialog"][aria-label="导出成稿"] .creation-search-shell');
    if (!el) return null;
    const cs = getComputedStyle(el);
    return { bg: cs.backgroundColor, color: cs.color, cls: String(el.className).slice(0, 60) };
  });
  await page.getByRole("button", { name: /关闭|取消/ }).last().click().catch(() => {});
  await page.waitForTimeout(300);

  const contrastOf = (name, obj) => {
    if (!obj) { check(`${theme} ${name} 面板可定位`, false, "not found"); return; }
    check(`${theme} ${name} 面板背景为实色`, obj.bg && obj.bg !== "rgba(0, 0, 0, 0)", obj.bg);
    check(`${theme} ${name} 面板文字对比`, obj.titleColor || obj.color,
      `bg=${obj.bg} color=${obj.color} title=${obj.titleColor ?? "-"}`);
  };
  contrastOf("校对", proof);
  contrastOf("导出", exportBox);
}

// ---------- 4. 1024px 无横向溢出 ----------
async function probeNarrowOverflow(page) {
  const win = page.evaluate(() => {
    window.resizeTo(1024, 768);
    window.dispatchEvent(new Event("resize"));
  });
  await win;
  await page.waitForTimeout(600);
  for (const [label, selector, extra, skipGoto] of [
    ["书库", ".desktop-library-row", null, false],
    ["收件箱", ".inbox-list", null, false],
    ["统计", ".desktop-page-stack", async () => {
      await goto(page, "资料阅读");
      await page.waitForSelector(".desktop-library-row", { timeout: 8000 }).catch(() => {});
      await page.getByRole("button", { name: /阅读统计/ }).click();
      await page.waitForTimeout(500);
    }, true],
    ["设置", null, null, false]
  ]) {
    if (!skipGoto) await goto(page, label);
    if (selector) await page.waitForSelector(selector, { timeout: 8000 }).catch(() => {});
    if (extra) await extra(page);
    await page.waitForTimeout(400);
    const ovf = await page.evaluate(() => {
      const scrollW = Math.max(document.documentElement.scrollWidth, document.body.scrollWidth);
      return { scrollW, clientW: document.documentElement.clientWidth };
    });
    check(`1024px ${label} 无横向溢出`, ovf.scrollW <= ovf.clientW + 1,
      `scrollW=${ovf.scrollW} clientW=${ovf.clientW}`);
  }
  // 1024px 下写作台 hero 高度与替换面板重叠
  await enterProject(page);
  await openWorkbenchView(page, "写作", ".writing-desk");
  await page.getByRole("button", { name: /查找替换/ }).first().click();
  await page.waitForSelector('[data-testid="replace-panel"]', { timeout: 8000 });
  await page.waitForTimeout(300);
  const narrow = await page.evaluate(() => {
    const hero = document.querySelector(".desktop-page-hero")?.getBoundingClientRect();
    const panel = document.querySelector('[data-testid="replace-panel"]')?.getBoundingClientRect();
    return { heroBottom: hero ? Math.round(hero.bottom) : null, panelTop: panel ? Math.round(panel.top) : null };
  });
  check("1024px 替换面板不遮 hero 操作行", narrow.panelTop !== null && narrow.heroBottom !== null && narrow.panelTop >= narrow.heroBottom - 8,
    `panelTop=${narrow.panelTop} heroBottom=${narrow.heroBottom}`);
  await page.getByTestId("replace-panel-close").click().catch(() => {});
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

// 创建项目骨架（替换/校对/导出面板在写作台内）
await createProject(page);

// 阅读器统计按钮（浅色 TXT + 深色 EPUB 即可覆盖两条路径）
await probeReaderStats(page, "light", "txt");
await probeReaderStats(page, "dark", "epub");

// 替换面板几何（浅色），暗色对话框（深色）
const lightReplace = await probeReplaceGeometry(page, "light");
const darkReplace = await probeReplaceGeometry(page, "dark");
check("替换面板浅色背景实色", lightReplace && lightReplace.bg !== "rgba(0, 0, 0, 0)", lightReplace?.bg);
check("替换面板深色文字对比", darkReplace && (darkReplace.color || darkReplace.title), `bg=${darkReplace?.bg} title=${darkReplace?.title}`);

await probeDialogContrast(page, "dark");

// 1024px 窄窗口
await probeNarrowOverflow(page);

await app.close();
rmSync(profileDir, { recursive: true, force: true });

if (failures.length) {
  console.error(`\n${failures.length} 项未通过: ${failures.join(" | ")}`);
  process.exit(1);
}
console.log("\n[probe-round7] 全部通过");
