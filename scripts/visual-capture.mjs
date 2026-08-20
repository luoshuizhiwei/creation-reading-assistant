/**
 * 桌面创作工作台视觉截图 + DOM 布局审计脚本。
 *
 * 用法：node scripts/visual-capture.mjs [--out=<dir>] [--keep-profile]
 *
 * - 隔离 Electron profile（CREATION_READER_CAPTURE_PROFILE），绝不触碰真实数据。
 * - 通过真实 UI 创建测试项目（测试项目）并写入场景正文构造进度数据。
 * - 每个状态同时输出截图（最终代码渲染）与同帧 DOM 审计数据（evidence.json）：
 *   横向溢出、越界/遮挡按钮、正文区宽度、重复标题、明暗对比度。
 */
import { mkdtempSync, existsSync, writeFileSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");

const argv = process.argv.slice(2);
const outDir = argv.find((a) => a.startsWith("--out="))?.split("=")[1] ?? path.join(__dirname, "visual-evidence");
const keepProfile = argv.includes("--keep-profile");

const SIZES = [
  { width: 1440, height: 900 },
  { width: 1024, height: 768 }
];
const THEMES = ["light", "dark"];
const FIXTURES = ["empty", "plain", "progress"];

function expectedMatrix() {
  const names = [];
  for (const { width, height } of SIZES) {
    for (const theme of THEMES) {
      for (const fixture of FIXTURES) names.push(`${width}x${height}-${theme}-${fixture}.png`);
    }
  }
  return names;
}

const profileDir = mkdtempSync(path.join(os.tmpdir(), "creation-visual-profile-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", "electron.exe");
const mainJs = path.join(root, "out", "main", "index.js");

if (!existsSync(electronPath)) throw new Error(`Electron binary not found: ${electronPath}`);
if (!existsSync(mainJs)) throw new Error(`Main bundle not found: ${mainJs}. Run electron-vite build first.`);

function luminance(color) {
  let channels = null;
  const rgb = /rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*[\d.]+)?\)/.exec(color);
  if (rgb) {
    channels = [rgb[1], rgb[2], rgb[3]].map(Number);
  } else {
    const srgb = /color\(srgb\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)/.exec(color);
    if (srgb) {
      channels = [srgb[1], srgb[2], srgb[3]].map((value) => Math.round(Number(value) * 255));
    }
  }
  if (!channels) return null;
  const linear = channels.map((value) => {
    const channel = value / 255;
    return channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
}

function contrastRatio(a, b) {
  const la = luminance(a);
  const lb = luminance(b);
  if (la === null || lb === null) return null;
  const lighter = Math.max(la, lb);
  const darker = Math.min(la, lb);
  return (lighter + 0.05) / (darker + 0.05);
}

/** 在页面内收集布局审计数据（与截图同帧）。 */
async function auditLayout(page) {
  return page.evaluate(() => {
    const toLuminance = (color) => {
      let channels = null;
      const rgb = /rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*[\d.]+)?\)/.exec(color);
      if (rgb) {
        channels = [rgb[1], rgb[2], rgb[3]].map(Number);
      } else {
        const srgb = /color\(srgb\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)/.exec(color);
        if (srgb) {
          channels = [srgb[1], srgb[2], srgb[3]].map((value) => Math.round(Number(value) * 255));
        }
      }
      if (!channels) return null;
      const linear = channels.map((value) => {
        const channel = value / 255;
        return channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
      });
      return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
    };
    const contrast = (a, b) => {
      const la = toLuminance(a);
      const lb = toLuminance(b);
      if (la === null || lb === null) return null;
      const lighter = Math.max(la, lb);
      const darker = Math.min(la, lb);
      return (lighter + 0.05) / (darker + 0.05);
    };
    const doc = document.documentElement;
    const viewportW = doc.clientWidth;
    const viewportH = doc.clientHeight;
    const overflowX = doc.scrollWidth > viewportW || document.body.scrollWidth > viewportW;

    const offenders = [];
    for (const el of document.querySelectorAll("body *")) {
      const style = getComputedStyle(el);
      if (style.position === "fixed" || style.display === "none" || style.visibility === "hidden") continue;
      const rect = el.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0) continue;
      const exceedsRight = rect.right > viewportW + 1;
      const exceedsLeft = rect.left < -1;
      const exceedsBottom = rect.bottom > viewportH + 1;
      if (exceedsRight || exceedsLeft || exceedsBottom) {
        const tag = el.tagName.toLowerCase();
        if (tag === "svg" || tag === "path" || tag === "em") continue;
        if (!el.textContent?.trim() && tag !== "input" && tag !== "select" && tag !== "button" && tag !== "img") continue;
        offenders.push({
          tag,
          cls: String(el.className).slice(0, 70),
          left: Math.round(rect.left),
          right: Math.round(rect.right),
          top: Math.round(rect.top),
          bottom: Math.round(rect.bottom),
          text: el.textContent?.trim().slice(0, 26) ?? ""
        });
      }
      if (offenders.length >= 24) break;
    }

    const motionPage = document.querySelector(".desktop-canvas > .motion-page");
    const canvasContent = motionPage ?? document.querySelector(".desktop-canvas");
    const contentRect = canvasContent?.getBoundingClientRect();
    const sidebar = document.querySelector(".desktop-sidebar")?.getBoundingClientRect();
    const contextPanel = document.querySelector(".desktop-context-panel");
    const contextVisible = contextPanel && getComputedStyle(contextPanel).display !== "none";
    const contextRect = contextVisible ? contextPanel.getBoundingClientRect() : null;

    const headings = [...document.querySelectorAll("h1, h2")].map((h) => h.textContent?.trim() ?? "").filter(Boolean);
    const duplicateHeadings = headings.filter((text, index) => headings.indexOf(text) !== index);

    const hero = document.querySelector(".project-home-hero, .desktop-page-hero, .creation-writing-hero");
    const heroRect = hero?.getBoundingClientRect();

    const bodyStyle = getComputedStyle(document.body);

    const sample = (selector, fallbackBg) => {
      const el = document.querySelector(selector);
      if (!el) return null;
      const style = getComputedStyle(el);
      const bg = style.backgroundColor === "rgba(0, 0, 0, 0)" ? (fallbackBg ?? style.backgroundColor) : style.backgroundColor;
      return { text: el.textContent?.trim().slice(0, 30) ?? "", color: style.color, bg, ratio: null };
    };

    const heroBg = hero ? getComputedStyle(hero).backgroundColor : null;
    const itemPanel = document.querySelector(".project-home-item");
    const itemBg = itemPanel ? getComputedStyle(itemPanel).backgroundColor : null;
    const commandbar = document.querySelector(".desktop-commandbar");
    const commandbarBg = commandbar ? getComputedStyle(commandbar).backgroundColor : null;

    const inkSamples = [
      sample(".desktop-commandbar h1", commandbarBg),
      sample(".project-home-item-main strong", itemBg),
      sample(".project-home-hero-copy, .creation-writing-hero h2", heroBg)
    ].filter(Boolean);
    const mutedSamples = [
      sample(".desktop-commandbar span", commandbarBg),
      sample(".project-home-meta", itemBg),
      sample(".project-home-hero-copy", heroBg)
    ].filter(Boolean);

    for (const item of [...inkSamples, ...mutedSamples]) {
      const bg = item.bg;
      const bgIsTransparent = /rgba\(0, 0, 0, 0\)/.test(bg) || /\/ 0\)/.test(bg);
      item.ratio = bgIsTransparent ? null : contrast(bg, item.color);
    }

    return {
      viewport: { width: viewportW, height: viewportH },
      theme: doc.dataset.appTheme ?? null,
      overflowX,
      mainContent: contentRect
        ? { width: Math.round(contentRect.width), height: Math.round(contentRect.height) }
        : null,
      sidebar: sidebar ? { width: Math.round(sidebar.width) } : null,
      contextPanelVisible: contextVisible,
      contextPanelWidth: contextRect ? Math.round(contextRect.width) : 0,
      offenders,
      headings,
      duplicateHeadings: [...new Set(duplicateHeadings)],
      hero: heroRect ? { width: Math.round(heroRect.width), height: Math.round(heroRect.height) } : null,
      colors: {
        bodyBg: bodyStyle.backgroundColor,
        bodyColor: bodyStyle.color,
        heroBg,
        itemBg,
        commandbarBg,
        inkSamples,
        mutedSamples
      }
    };
  });
}

let tests = 0;
const evidence = [];

async function launchApp() {
  const app = await electron.launch({
    executablePath: electronPath,
    args: [mainJs],
    cwd: root,
    env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir }
  });
  const page = await app.firstWindow();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
  await page.waitForFunction(() => {
    const rootEl = document.querySelector(".desktop-canvas");
    return Boolean(rootEl?.querySelector(".project-home-hero, .project-nav-back, .creation-writing-hero"));
  }, null, { timeout: 30000 });
  await page.waitForTimeout(900);
  return { app, page };
}

async function main() {
  const { app, page } = await launchApp();

  const capture = async (fixture) => {
    const { width, height, dpr } = await page.evaluate(() => ({
      width: document.documentElement.clientWidth,
      height: document.documentElement.clientHeight,
      dpr: window.devicePixelRatio
    }));
    const theme = await page.evaluate(() => document.documentElement.dataset.appTheme ?? "light");
    const audit = await auditLayout(page);
    const name = `${width}x${height}-${theme}-${fixture}`;
    const file = path.join(outDir, `${name}.png`);
    await page.screenshot({ path: file });
    evidence.push({
      name,
      file: `${name}.png`,
      fixture,
      theme,
      logicalViewport: { width, height },
      screenshotPixels: { width: Math.round(width * dpr), height: Math.round(height * dpr) },
      deviceScaleFactor: dpr,
      ...audit
    });
    console.log(`[visual-capture] saved ${name}.png`);
    tests += 1;
  };

  let currentWidth = 1440;
  let currentHeight = 900;
  const setViewport = async (width, height) => {
    // 无边框窗口在部分缩放下 setContentSize 会有 ±2 DIP 的取整偏差；
    // 读回 clientHeight 并修正一次，保证截图命名与矩阵一致。
    currentWidth = width;
    currentHeight = height;
    for (let attempt = 0; attempt < 3; attempt += 1) {
      await app.evaluate(({ BrowserWindow }, size) => {
        for (const window of BrowserWindow.getAllWindows()) {
          window.setContentSize(size.width, size.height);
        }
      }, { width: currentWidth, height: currentHeight });
      await page.waitForTimeout(500);
      const actual = await page.evaluate(() => document.documentElement.clientHeight);
      if (actual === height) return;
      currentHeight += height - actual;
    }
  };

  const setTheme = async (theme) => {
    await page.evaluate((value) => {
      document.documentElement.dataset.appTheme = value;
    }, theme);
    await page.waitForTimeout(350);
  };

  const forEachViewportAndTheme = async (fixture) => {
    for (const { width, height } of SIZES) {
      await setViewport(width, height);
      for (const theme of THEMES) {
        await setTheme(theme);
        const state = await page.evaluate(() => {
          const bar = document.querySelector(".project-home-progressbar");
          const chars = document.querySelector(".project-home-chars");
          const item = document.querySelector(".project-home-item");
          const noGoal = document.querySelector(".project-home-no-goal");
          const empty = document.querySelector(".project-home-empty");
          const createFirst = document.querySelector(".project-home-create-first");
          return {
            hasBar: Boolean(bar),
            hasChars: Boolean(chars),
            hasItem: Boolean(item),
            hasNoGoal: Boolean(noGoal),
            hasEmpty: Boolean(empty),
            hasCreateFirst: Boolean(createFirst),
            charsText: chars?.textContent ?? null
          };
        });
        if (fixture === "empty" && (!state.hasEmpty || !state.hasCreateFirst)) {
          throw new Error(`empty fixture incomplete: ${JSON.stringify(state)}`);
        }
        if (fixture === "plain" && (!state.hasItem || !state.hasChars || !state.hasBar)) {
          throw new Error(`plain fixture incomplete: ${JSON.stringify(state)}`);
        }
        await capture(fixture);
      }
    }
  };

  // ---------- 零项目 ----------
  await forEachViewportAndTheme("empty");

  // ---------- 创建测试项目 ----------
  await setTheme("light");
  await setViewport(1440, 900);
  const createFirst = page.locator(".project-home-create-first").first();
  if (await createFirst.isVisible()) {
    await createFirst.click();
  } else {
    await page.getByRole("button", { name: /新建项目/ }).first().click();
  }
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

  // 返回项目首页（普通项目：有项目、无进度）
  await page.locator(".project-nav-back").click();
  await page.waitForSelector(".project-home-list", { timeout: 15000 });
  await page.waitForTimeout(600);
  await forEachViewportAndTheme("plain");

  // ---------- 写入正文 → 有进度项目（关闭应用后独立进程写库，再重启截图） ----------
  await app.close();
  const seedResult = spawnSync(
    electronPath,
    [path.join(__dirname, "visual-seed-body.cjs"), profileDir, "3000"],
    {
      cwd: root,
      encoding: "utf8",
      env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") },
      windowsHide: true,
      timeout: 60_000
    }
  );
  if (seedResult.status !== 0) {
    throw new Error(`seed body failed:\n${seedResult.stdout}\n${seedResult.stderr}`);
  }
  console.log(`[visual-capture] seed body output: ${seedResult.stdout?.trim()} ${seedResult.stderr?.trim()}`);

  const second = await launchApp();
  const page2 = second.page;
  for (const { width, height } of SIZES) {
    let resizeHeight = height;
    for (let attempt = 0; attempt < 3; attempt += 1) {
      await second.app.evaluate(({ BrowserWindow }, size) => {
        for (const window of BrowserWindow.getAllWindows()) {
          window.setContentSize(size.width, size.height);
        }
      }, { width, height: resizeHeight });
      await page2.waitForTimeout(500);
      const actual = await page2.evaluate(() => document.documentElement.clientHeight);
      if (actual === height) break;
      resizeHeight += height - actual;
    }
    for (const theme of THEMES) {
      await page2.evaluate((value) => {
        document.documentElement.dataset.appTheme = value;
      }, theme);
      await page2.waitForTimeout(350);
      await (async () => {
        const state = await page2.evaluate(() => {
          const bar = document.querySelector(".project-home-progressbar");
          const chars = document.querySelector(".project-home-chars");
          const item = document.querySelector(".project-home-item");
          const noGoal = document.querySelector(".project-home-no-goal");
          return {
            hasBar: Boolean(bar),
            hasChars: Boolean(chars),
            hasItem: Boolean(item),
            hasNoGoal: Boolean(noGoal),
            charsText: chars?.textContent ?? null,
            barWidth: bar ? getComputedStyle(bar.querySelector(".project-home-progressbar-fill") ?? bar).width : null
          };
        });
        if (!state.hasItem || !state.hasChars || !state.hasBar || state.hasNoGoal || !state.charsText?.includes("k 字")) {
          throw new Error(`progress fixture incomplete: ${JSON.stringify(state)}`);
        }
        const { width: w, height: h, dpr } = await page2.evaluate(() => ({
          width: document.documentElement.clientWidth,
          height: document.documentElement.clientHeight,
          dpr: window.devicePixelRatio
        }));
        const themeName = await page2.evaluate(() => document.documentElement.dataset.appTheme ?? "light");
        const audit = await auditLayout(page2);
        const name = `${w}x${h}-${themeName}-progress`;
        await page2.screenshot({ path: path.join(outDir, `${name}.png`) });
        evidence.push({
          name,
          file: `${name}.png`,
          fixture: "progress",
          theme: themeName,
          logicalViewport: { width: w, height: h },
          screenshotPixels: { width: Math.round(w * dpr), height: Math.round(h * dpr) },
          deviceScaleFactor: dpr,
          ...audit
        });
        console.log(`[visual-capture] saved ${name}.png`);
        tests += 1;
      })();
    }
  }
  await second.app.close();

  writeFileSync(
    path.join(outDir, "evidence.json"),
    `${JSON.stringify({ generatedAt: new Date().toISOString(), matrix: expectedMatrix(), captures: evidence }, null, 2)}\n`,
    "utf8"
  );
  console.log(`\n[visual-capture] ${tests} screenshots + evidence.json captured. Output: ${outDir}`);
  process.exit(0);
}

main().catch((error) => {
  console.error(`[visual-capture] FAILED: ${error instanceof Error ? error.stack ?? error.message : String(error)}`);
  try {
    writeFileSync(
      path.join(outDir, "evidence.json"),
      `${JSON.stringify({ generatedAt: new Date().toISOString(), error: String(error), captures: evidence }, null, 2)}\n`,
      "utf8"
    );
  } catch {
    // evidence write is best-effort
  }
  process.exit(1);
}).finally(() => {
  if (!keepProfile) {
    try {
      rmSync(profileDir, { recursive: true, force: true });
    } catch {
      // profile cleanup is best-effort
    }
  } else {
    console.log(`[visual-capture] profile kept at ${profileDir}`);
  }
});
