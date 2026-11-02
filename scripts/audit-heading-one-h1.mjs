#!/usr/bin/env node
/**
 * BD-229 常驻普查：WCAG 1.3.1「信息与关系」（Level A，落到标题骨架这条技术上就是
 * G130/H13）——一屏只许有一个页级标题 h1，而且那个 h1 必须还是窗口标题的正身。
 *
 * 为什么值得常驻：读屏器的大纲视图按标题层级列目录，h1 就是「这一屏是什么」。此前实测
 * （/tmp/bd229-survey.log、/tmp/bd229-survey2.log）：
 *   [书库]   h1 出处 = 书库〔无 class，壳命令条〕 ＋ 书库〔paper-title，页面自己又摆了一个〕
 *   [阅读统计] 一屏两个页级标题、还是两套名字：「阅读统计」与「阅读与沉淀节律」
 * 这种回归肉眼完全看不出来——两个 h1 都长得像标题，只是大纲里多了一格、多了一套名字。
 * 而且它会把 BD-227 那条链拖坏：窗口标题抄的是「第一个 h1」，页面再藏一个自己的 h1，
 * 两边的名字迟早分叉。
 *
 * 方法（口径写死在这里，改口径就是改判据）：
 *   逐屏抓三样：document.title、壳侧 h1 清单、正文区 h1 清单。
 *   「正文区」= .reader-scroller / article 里的标题——那本书自己写的章标题是内容，
 *   不是应用的结构，不计入页级标题，但照记档（哪一屏带了几个，一眼能查）。
 *     ①壳侧 h1 恰好一个：0 个＝这一屏没有页级标题（大纲里这一屏是空的）；
 *       ≥2 个＝大纲出现平级的两个「页面」，读屏用户分不清自己在哪；
 *     ②那个 h1 的文案必须还在窗口标题里（忽略空白）——BD-227 定的口径就是「标题抄页面正身」，
 *       谁改了 h1 没改发布点、或反过来，这一条就咬；
 *     ③全程 h1 文案种类 ≥2：整轮只有一个名字等于这条 SC 根本没被满足。
 *   必到屏（MANDATORY）：项目、收件箱、卡片库、设置、书库、阅读统计——少一趟直接判红，
 *   因为本批修的正是最后两处；再加屏数与工作台视图数下限，防止走查被静默掏空。
 *   自证针三枚走真判据：多塞一个 h1（①该咬）、把 h1 全清光（①该咬）、
 *   把 h1 换成不相干的同一名（②③该咬）；针体跑完才置 selfTest.ran，没跑完必判红。
 *
 * 最终态：HEADING_OK——每屏一个页级标题，且它就是窗口标题里那句正身。
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { _electron as electron } from "playwright-core";

const root = "/data/creation-reading-assistant";
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-heading-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");
const APP_NAME = "创作阅读助手";

const norm = (s) => (s || "").replace(/\s+/g, "");

const selfTest = { ran: false, reds: [], caughtDup: false, caughtEmpty: false, caughtAlien: false };

/** 纯判据：正式行集与针的克隆行集都走这一个 findings() */
const findings = (rows) => {
  const reds = [];
  for (const r of rows) {
    if (r.chromeH1.length === 0) {
      reds.push(`①「${r.screen}」这一屏没有页级 h1——读屏大纲里这一屏是空的，用户只能靠逐屏的窗口标题认位置`);
      continue;
    }
    if (r.chromeH1.length > 1) {
      reds.push(`①「${r.screen}」有 ${r.chromeH1.length} 个页级 h1（${r.chromeH1.map((t) => `「${t}」`).join("＋")}）——一屏只许一个，多出来的那个把大纲多列了一格，还把两个名字摆成平级`);
      continue;
    }
    const body = r.doc.endsWith(` — ${APP_NAME}`) ? r.doc.slice(0, -(APP_NAME.length + 3)) : r.doc;
    if (!norm(body).includes(norm(r.chromeH1[0]))) {
      reds.push(`②「${r.screen}」页级 h1 是「${r.chromeH1[0]}」，窗口标题的正身却是「${body}」——BD-227 定的口径是「标题抄页面正身」，两套名字迟早漂移`);
    }
  }
  const kinds = new Set(rows.filter((r) => r.chromeH1.length === 1).map((r) => r.chromeH1[0]));
  if (kinds.size < 2) {
    reds.push(`③全程 ${rows.length} 屏的页级标题只有 ${kinds.size} 种——换屏不产生新的页级标题，等于没有这套骨架`);
  }
  return reds;
};

const launch = async () => {
  const app = await electron.launch({
    executablePath: electronPath, args: [mainJs, "--disable-dev-shm-usage"], cwd: root,
    env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir },
  });
  const page = await app.firstWindow();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
  await page.waitForTimeout(600);
  return { app, page };
};

const main = async () => {
  { const first = await launch(); await first.app.close(); }
  const seed = spawnSync(electronPath, [path.join(root, "scripts", "round4-seed.cjs"), profileDir], {
    cwd: root, encoding: "utf8",
    env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") },
    timeout: 60000 });
  if (seed.status !== 0) throw new Error(`seed failed: ${seed.stderr}`);

  const { app, page } = await launch();
  await app.evaluate(({ BrowserWindow }) => {
    BrowserWindow.getAllWindows()[0].setBounds({ x: 40, y: 40, width: 1360, height: 900 });
  });
  await page.waitForTimeout(600);

  const rows = [];
  const notes = [];

  const snap = async (screen, group) => {
    const r = await page.evaluate(() => {
      const isContent = (h) => !!h.closest(".reader-scroller, article");
      const txt = (h) => h.textContent.replace(/\s+/g, " ").trim().slice(0, 60);
      const all = [...document.querySelectorAll("h1")];
      return {
        doc: document.title,
        chromeH1: all.filter((h) => !isContent(h)).map(txt),
        contentH1: all.filter(isContent).map(txt)
      };
    });
    if (r.contentH1.length) notes.push(`${screen}：正文区另有 ${r.contentH1.length} 个 h1（${r.contentH1.join("／")}）——那是书自己的标题，不计入页级`);
    rows.push({ screen, group, ...r });
  };

  const goto = async (name) => {
    const b = page.locator(".desktop-sidebar button", { hasText: name }).first();
    await b.click();
    await page.waitForTimeout(900);
  };

  const side = async (name) => {
    const b = page.locator(".desktop-sidebar button", { hasText: name }).first();
    if (!(await b.isVisible().catch(() => false))) { notes.push(`侧栏没有可见的「${name}」——这一屏没扫到`); return false; }
    await b.click();
    await page.waitForTimeout(900);
    await snap(name, "shell");
    return true;
  };

  const nav = async (name, screen, group = "workbench") => {
    const btn = page.locator(".project-nav button", { hasText: name }).first();
    if (await btn.isVisible().catch(() => false)) {
      await btn.click();
      await page.waitForTimeout(900);
      await snap(screen, group);
      return true;
    }
    notes.push(`没点到工作台导航「${name}」——这个视图没被扫到`);
    return false;
  };

  await side("项目");
  await page.getByRole("button", { name: /新建第一个项目|新建项目/ }).first().click();
  const wizard = page.locator("[role=dialog]").first();
  await wizard.waitFor({ state: "visible", timeout: 15000 });
  await snap("弹层·新建向导", "shell");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.waitForTimeout(600);
  await page.locator("input[placeholder='例如：测试项目']").first().fill("标题骨架普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(2400);

  for (const v of ["概览", "写作", "大纲", "全书预览", "设定卡", "写作统计", "版本历史"]) {
    await nav(v, `工作台·${v}`);
  }
  await nav("项目列表", "项目·列表", "shell");

  for (const s of ["收件箱", "卡片库", "设置", "书库"]) await side(s);

  // 阅读统计没有侧栏入口，只能从书库那颗按钮进（/tmp/bd229-survey2.log 实测侧栏只有 5 个屏）
  {
    const b = page.locator("button:has-text('阅读统计')").first();
    if (await b.isVisible().catch(() => false)) {
      await b.click();
      await page.waitForTimeout(1400);
      await snap("阅读统计", "shell");
    } else notes.push("没找到书库里的「阅读统计」按钮——本批修的第二处没被扫到");
  }

  // 资料阅读：书目不写死，书名就地读自那张表（开书会改「最近阅读」，先把名字收齐）
  const SHELF = '[role="table"][aria-label="书库书籍"]';
  await goto("书库");
  const titleLoc = page.locator(`${SHELF} h2.paper-title`);
  const shelfNames = [];
  for (let i = 0, n = Math.min(await titleLoc.count(), 3); i < n; i++) {
    const name = ((await titleLoc.nth(i).textContent().catch(() => "")) || "").replace(/\s+/g, " ").trim();
    if (name && !shelfNames.includes(name)) shelfNames.push(name);
  }
  for (const name of shelfNames) {
    const open = page.locator(`${SHELF} button[aria-label="打开「${name}」"]`).first();
    if (!(await open.isVisible().catch(() => false))) { notes.push(`没点到「${name}」的打开按钮——阅读屏少扫一本`); continue; }
    await open.click();
    await page.waitForTimeout(2200);
    await snap(`资料阅读·${name}`, "reader");
    const back = page.locator('button[aria-label="返回书库"], button:has-text("返回书库")').first();
    if (!(await back.isVisible().catch(() => false))) { notes.push("阅读屏没有「返回书库」——后面的书目扫不下去了"); break; }
    await back.click();
    await page.waitForTimeout(900);
  }

  /* 自证针：正式行集的克隆，三份各改一样东西——证明 ①（两个方向）与 ②③ 真的在咬 */
  const dupH1 = rows.map((r) => ({ ...r, chromeH1: [...r.chromeH1, "再来一个同名页级标题"] }));
  const emptyH1 = rows.map((r) => ({ ...r, chromeH1: [] }));
  const alienH1 = rows.map((r) => ({ ...r, chromeH1: ["与本屏无关的一个名字"] }));
  const redsDup = findings(dupH1);
  const redsEmpty = findings(emptyH1);
  const redsAlien = findings(alienH1);
  selfTest.caughtDup = redsDup.some((x) => x.startsWith("①") && x.includes("再来一个同名页级标题"));
  selfTest.caughtEmpty = redsEmpty.some((x) => x.startsWith("①") && x.includes("没有页级 h1"));
  selfTest.caughtAlien = redsAlien.some((x) => x.startsWith("②")) && redsAlien.some((x) => x.startsWith("③"));
  if (!selfTest.caughtDup) selfTest.reds.push("自证失败：给每屏多塞一个 h1，①没抓到——「一屏只许一个」这条判据是空的。");
  if (!selfTest.caughtEmpty) selfTest.reds.push("自证失败：把每屏的 h1 清空，①没抓到——「这一屏得有页级标题」这条判据是空的。");
  if (!selfTest.caughtAlien) selfTest.reds.push("自证失败：把每屏 h1 换成不相干的同一个名字，②③没抓到——「h1 必须还是窗口标题那句正身」这条判据是空的。");
  selfTest.ran = true;
  console.log(`自证：多塞一个→${selfTest.caughtDup ? "抓到" : "没抓到"}、全清空→${selfTest.caughtEmpty ? "抓到" : "没抓到"}、换成不相干→${selfTest.caughtAlien ? "抓到" : "没抓到"}`);

  for (const n of notes) console.log("  记档 " + n);

  if (process.env.BD229_DUMP === "1") {
    console.log("=== BD229_DUMP：屏 → 窗口标题 / 壳侧 h1 / 正文区 h1 ===");
    for (const r of rows) console.log(`[${r.screen}]\n   doc: ${r.doc}\n   壳侧: ${r.chromeH1.join(" ＋ ") || "（无）"}\n   正文: ${r.contentH1.join(" ／ ") || "（无）"}`);
    await app.close();
    console.log(`BD229_DUMP 完成：采样 ${rows.length} 屏（未做判红，仅导出现场）。`);
    return -1;
  }

  const reds = findings(rows);
  const SCREEN_MIN = 15;
  const WORKBENCH_MIN = 5;
  const MANDATORY = ["项目", "收件箱", "卡片库", "设置", "书库", "阅读统计"];
  const seen = new Set(rows.map((r) => r.screen));
  for (const m of MANDATORY) if (!seen.has(m)) reds.push(`必到屏「${m}」没被扫到——这一屏的标题骨架本次没人看过。`);
  if (rows.length < SCREEN_MIN) reds.push(`走查只剩 ${rows.length} 屏（下限 ${SCREEN_MIN}）——前面某几步没跑到，覆盖面不足。`);
  const wbRows = rows.filter((r) => r.group === "workbench");
  if (wbRows.length < WORKBENCH_MIN) reds.push(`工作台只扫到 ${wbRows.length} 个视图（下限 ${WORKBENCH_MIN}）——沉浸态那条 sr-only h1 几乎没被检验。`);
  if (!selfTest.ran) selfTest.reds.push("自证失败：自证针没跑完——①②③三条判据本次根本没被检验，判绿不可信。");
  reds.push(...selfTest.reds);

  console.log(`标题骨架扫描完成：采样 ${rows.length} 屏，页级标题 ${new Set(rows.filter((r) => r.chromeH1.length === 1).map((r) => r.chromeH1[0])).size} 种。`);
  for (const r of rows.slice(0, 3)) console.log(`  样例 [${r.screen}] ${r.chromeH1.join("＋")}`);
  if (reds.length) {
    console.log(`判红 ${reds.length} 项：`);
    for (const r of reds) console.log("  ✗ " + r);
  }
  await app.close();
  return reds.length ? 1 : 0;
};

let exitCode = 0;
try {
  const code = await main();
  if (code === -1) exitCode = 0;
  else if (code === 0) {
    console.log("HEADING_OK：每一屏恰好一个页级 h1，且它就是窗口标题里那句正身；书自己带的章标题只记档、不计入。");
    exitCode = 0;
  } else { console.log("HEADING_FAIL：见上面判红项。"); exitCode = 1; }
} catch (error) {
  console.error(error);
  exitCode = 1;
}
process.exit(exitCode);
