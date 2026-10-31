/**
 * 窄视口回流普查（WCAG 1.4.10 Reflow，AA）。批次 BD-222 立。
 *
 * 这条判据的分母不是「浏览器能开到多窄」，而是本 app 自己允许的最窄 CSS px 视口：
 * 窗口下限 minWidth 1080（electron/main/index.ts:853，采集档除外）÷ 页面缩放上限
 * 200%（BD-218，ZOOM_MIN 0.5 / ZOOM_MAX 2）= 540 CSS px。用户在「最小窗口 + 最大
 * 缩放」这一档拿到的就是这个宽度，1.4.10 要求内容在这里回流，而不是要求横向滚动。
 * 所以普查跑两个档：540（判红档）与 1360（常规对照档，防我为了窄档把宽档改坏）。
 *
 * 三档判定（对每个视图）：
 *   RED 页级横向溢出：documentElement.scrollWidth 越过 clientWidth。BD-222 修掉的老根
 *     就是这条——body{min-width:800px} 把 13 个视图全顶成越界；body 的 overflow 按 CSS
 *     传播规则顶到视口，所以它实测表现为「二维滚动或直接裁掉」二者之一，两种都违 1.4.10；
 *   RED 被切掉、摸不到：元素右缘越过视口，且沿祖先链碰到的第一个 overflow-x 非 visible
 *     的容器是 hidden/clip（或 auto/scroll 却没真溢出可滚）。.project-workbench-main
 *     一类 overflow:hidden 的壳会把越界内容直接裁掉——键盘焦点能走到屏外却看不见，
 *     故单列判红；
 *   记档不判红：内容越界但被一个「scrollWidth > clientWidth」的内层横滚容器接住
 *     （二维滚动只发生在局部组件里，1.4.10 允许）。
 *
 * 两个豁免（都写在扫描里，免得下次又来误判）：
 *   · SVG 内部图形（path/rect/circle…）不单独判——<svg> 按 UA 规则本带 overflow:hidden，
 *     viewBox 里的图形几何上越界是常态，要判就判 <svg> 这个替换元素自己的盒子；
 *   · 元素同名（tag + 前两个 class）只报第一条，避免一颗图标刷满一屏噪声；
 *   · hidden/auto 祖先要「真把内容切在它自己框外」才定罪——进度条 fill 这类整体仍在
 *     槽内的元素继续往外层找，否则一个 overflow:hidden 的小槽会把合法内容报成裁切。
 *
 * 为什么全程运行时：这一条是布局的 DOM 事实，CSS 源里读不出「哪条规则真的赢」——
 * styles.css 与 editorial-studio.css 同特异度靠发射序压（BD-29/34 记过），媒体查询
 * 不加特异度，静态读代码会把死副本当成生效档。普查直接读 computed 值与真实盒子。
 *
 * 覆盖面也进判据：判定视图数不足（两档各 < REFLOW_MIN=12）即视为覆盖塌陷——导航断了、
 * 种子没播上都会静默少测，不许用「零 RED」蒙过。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 900 xvfb-run -a --server-args="-screen 0 1600x1000x24" node scripts/audit-reflow.mjs
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const REFLOW_MIN = 12;

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-reflow-"));
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
const judged = [];
const notes = [];
const skipped = [];

const setTier = async (width) => {
  await app.evaluate(({ BrowserWindow }, nextWidth) => {
    BrowserWindow.getAllWindows()[0].setBounds({ x: 40, y: 40, width: nextWidth, height: 900 });
  }, width);
  await page.waitForTimeout(600);
};

/** 一个文档（顶层或同源 iframe）里的页级溢出与被裁元素，判据见文件头。 */
const measure = async (label, tier) => {
  const docs = await page.evaluate(() => {
    const one = (doc) => {
      const vw = doc.documentElement.clientWidth;
      const scrollW = doc.documentElement.scrollWidth;
      const clipped = [];
      const inner = [];
      const seen = new Set();
      for (const el of Array.from(doc.querySelectorAll("body *"))) {
        const r = el.getBoundingClientRect();
        if (r.width === 0 || r.height === 0) continue;
        if (r.right <= vw + 1) continue;
        // SVG 内部图形（path/rect/…）不算独立内容单元：svg 画布按 UA 规则自带 overflow:hidden，
        // viewBox 里的图形几何上常越过视口，真正该判的是 <svg> 这个替换元素自己的盒子。
        if (el instanceof doc.defaultView.SVGElement && el.tagName.toLowerCase() !== "svg") continue;
        const over = Math.round(r.right - vw);
        const name = typeof el.className === "string" && el.className
          ? `${el.tagName.toLowerCase()}.${el.className.split(" ").filter(Boolean).slice(0, 2).join(".")}`
          : el.tagName.toLowerCase();
        if (seen.has(name)) continue;
        seen.add(name);
        let host = null;
        for (let p = el.parentElement; p; p = p.parentElement) {
          // <svg> 的 intrinsic overflow:hidden 不是布局裁切意图，跳过它继续往 HTML 祖先找。
          if (p instanceof doc.defaultView.SVGElement) continue;
          const st = doc.defaultView.getComputedStyle(p);
          if (st.overflowX === "visible") continue;
          // 带 hidden/auto 的祖先只有「真的把这块内容切在框外」才算裁切；
          // 元素整体还在该祖先盒内时（进度条的 fill 之类），继续往外层找。
          if (r.right <= p.getBoundingClientRect().right + 1) continue;
          host = p;
          break;
        }
        if (!host) {
          clipped.push(`${name} 越界 ${over}px，祖先链上无裁剪/滚动容器`);
          continue;
        }
        const st = doc.defaultView.getComputedStyle(host);
        const hostName = typeof host.className === "string" && host.className
          ? `${host.tagName.toLowerCase()}.${host.className.split(" ").filter(Boolean)[0]}`
          : host.tagName.toLowerCase();
        if (/(auto|scroll)/.test(st.overflowX) && host.scrollWidth > host.clientWidth + 1) {
          inner.push(`${name}→${hostName}`);
        } else {
          clipped.push(`${name} 越界 ${over}px，被 ${hostName}（overflow-x:${st.overflowX}）裁掉`);
        }
      }
      return { vw, pageScrollBy: scrollW - vw, clipped: clipped.slice(0, 4), inner: inner.slice(0, 4) };
    };
    const out = [one(document)];
    for (const frame of Array.from(document.querySelectorAll("iframe"))) {
      let innerDoc = null;
      try {
        innerDoc = frame.contentDocument;
      } catch {
        innerDoc = null;
      }
      if (innerDoc && innerDoc.documentElement && innerDoc.body) out.push(one(innerDoc));
    }
    return out;
  });

  let clean = true;
  docs.forEach((d, i) => {
    const where = `${label} @${tier}${i > 0 ? `·iframe${i}` : ""}`;
    if (d.pageScrollBy > 1) {
      reds.push(`${where}：页级横向溢出，scrollWidth 越过视口 ${d.pageScrollBy}px（vw=${d.vw}）`);
      clean = false;
    }
    if (d.clipped.length > 0) {
      reds.push(`${where}：${d.clipped.length} 处内容越界却摸不到 —— ${d.clipped.join("；")}`);
      clean = false;
    }
    if (d.inner.length > 0) notes.push(`${where}：${d.inner.length} 处越界由内层横滚容器接住（局部二维滚动，记档）—— ${d.inner.join("；")}`);
  });
  judged.push(label);
  if (clean) console.log(`PASS ${label} @${tier}px 回流正常（无页级横向溢出、无被裁内容）`);
  return clean;
};

const side = async (label, tier) => {
  try {
    await page.locator(".desktop-sidebar button", { hasText: label }).first().click();
    await page.waitForTimeout(800);
    await measure(label, tier);
  } catch (error) {
    skipped.push(`${label} @${tier}：${String(error && error.message).split("\n")[0].slice(0, 140)}`);
  }
};

const workbenchView = async (label, tier) => {
  try {
    const btn = page.locator(".project-nav button", { hasText: label }).first();
    if (!(await btn.isVisible().catch(() => false))) {
      skipped.push(`工作台·${label} @${tier}：项目导航里没有这一项`);
      return;
    }
    await btn.click();
    await page.waitForTimeout(1000);
    await measure(`工作台·${label}`, tier);
  } catch (error) {
    skipped.push(`工作台·${label} @${tier}：${String(error && error.message).split("\n")[0].slice(0, 140)}`);
  }
};

/** 项目页 → 工作台。createIfNeeded 为真时走新建向导建「回流验收项目」（只在首档建一次）。 */
const enterWorkbench = async (tier, createIfNeeded) => {
  try {
    await page.locator(".desktop-sidebar button", { hasText: "项目" }).first().click();
    await page.waitForTimeout(900);
    if (createIfNeeded) {
      const create = page.getByRole("button", { name: /新建第一个项目|新建项目/ }).first();
      if (!(await create.isVisible().catch(() => false))) throw new Error("项目页没有「新建项目」入口");
      await create.click();
      const wizard = page.locator("[role=dialog]").first();
      await wizard.waitFor({ state: "visible", timeout: 15000 });
      await wizard.getByRole("button", { name: /下一步/ }).click();
      await page.locator("input[placeholder='例如：测试项目']").first().fill("回流验收项目");
      await wizard.getByRole("button", { name: /下一步/ }).click();
      await page.locator("input[placeholder='例如：500000']").first().fill("50000");
      await wizard.getByRole("button", { name: /创建项目/ }).click();
      await page.waitForTimeout(2400);
    } else {
      const open = page.getByRole("button", { name: "打开项目：回流验收项目" }).first();
      if (await open.isVisible().catch(() => false)) {
        await open.click();
        await page.waitForTimeout(1800);
      } else {
        const back = page.locator(".project-nav-back").first();
        if (await back.isVisible().catch(() => false)) {
          await back.click();
          await page.waitForTimeout(900);
        }
        await page.locator(".project-home-list button").first().click();
        await page.waitForTimeout(1600);
      }
    }
    await measure("工作台·概览", tier);
  } catch (error) {
    skipped.push(`工作台·概览 @${tier}：${String(error && error.message).split("\n")[0].slice(0, 140)}`);
  }
};

const sweep = async (tier, createIfNeeded) => {
  await side("书库", tier);
  try {
    await page.locator(".desktop-library-row").first().click();
    await page.waitForTimeout(2500);
    await measure("阅读器", tier);
    const back = page.getByRole("button", { name: "返回书库" });
    if (await back.isVisible().catch(() => false)) {
      await back.click();
      await page.waitForTimeout(800);
    }
  } catch (error) {
    skipped.push(`阅读器 @${tier}：${String(error && error.message).split("\n")[0].slice(0, 140)}`);
  }
  await side("卡片库", tier);
  await side("收件箱", tier);
  await side("设置", tier);
  // 弹层：新建向导第 2 步（表单最满的一屏）
  try {
    await page.locator(".desktop-sidebar button", { hasText: "项目" }).first().click();
    await page.waitForTimeout(800);
    const create = page.getByRole("button", { name: /新建第一个项目|新建项目/ }).first();
    if (await create.isVisible().catch(() => false)) await create.click();
    const wizard = page.locator("[role=dialog]").first();
    await wizard.waitFor({ state: "visible", timeout: 15000 });
    await wizard.getByRole("button", { name: /下一步/ }).click();
    await page.waitForTimeout(500);
    await measure("弹层·新建向导", tier);
    await page.keyboard.press("Escape");
    await page.waitForTimeout(700);
  } catch (error) {
    skipped.push(`弹层·新建向导 @${tier}：${String(error && error.message).split("\n")[0].slice(0, 140)}`);
  }
  await enterWorkbench(tier, createIfNeeded);
  for (const label of ["写作", "大纲", "全书预览", "设定卡", "写作统计", "版本历史"]) {
    await workbenchView(label, tier);
  }
};

// —— 540 CSS px：本 app 可达的最窄档，判红档 ——
await setTier(540);
await sweep(540, true);

// —— 1360：常规对照档，确认窄档改动没把宽档改坏（这一档不再建项目）——
await setTier(1360);
await page.reload();
await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
await page.waitForTimeout(800);
await sweep(1360, false);

await app.close();

for (const item of skipped) console.log(`跳过 ${item}`);
for (const item of notes) console.log(`记档 ${item}`);
if (judged.length < REFLOW_MIN * 2) {
  console.log(`REFLOW_RED：只判定到 ${judged.length} 个视图（要求 ≥${REFLOW_MIN * 2}，两档各 ${REFLOW_MIN} 个），覆盖不足无法证明。`);
  process.exit(1);
}
if (reds.length > 0) {
  for (const red of reds) console.log(`  - ${red}`);
  console.log(`REFLOW_RED：${reds.length} 处窄视口回流破坏（WCAG 1.4.10）。`);
  process.exit(1);
}
console.log(`REFLOW_OK：${judged.length} 个视图在 540 与 1360 两档都无页级横向溢出、无被裁掉的内容。`);
