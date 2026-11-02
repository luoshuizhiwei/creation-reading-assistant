#!/usr/bin/env node
/**
 * BD-227 常驻普查：WCAG 2.4.2「页面标题」（Level A）——每一屏的窗口标题得说出这一屏在干什么。
 *
 * 为什么值得常驻：标题是唯一一份「不看页面内容也能认出这个窗口在做什么」的信息——
 * 读屏器的窗口清单、系统任务栏、Alt+Tab、多窗口对账全靠它。本仓此前的实测是
 * 15 屏里 document.title 与系统窗口标题恒等于「创作阅读助手」（/tmp/bd227-baseline.log），
 * 页面里的 <h1> 明明写着「收件箱」「标题普查项目 · 大纲」，窗口层面一概看不见。
 * 这类回归（新增一屏忘了发标题、或把发布点改回常量）肉眼完全看不出来。
 *
 * 方法（口径写死在这里，改口径就是改判据）：
 *   逐屏同时抓三样：document.title、系统窗口标题（BrowserWindow.getTitle()）、
 *   这一屏页头的 <h1> 正身。判据全部对着 h1 算——h1 是页面上真正写给用户看的那句任务名，
 *   窗口标题不许另立一套文案（两套名字迟早漂移）。
 *     ①标题非空，且带应用名「创作阅读助手」（多个应用同开时得能认出是谁家的窗口）；
 *     ②标题必须包含这一屏 h1 的正身（忽略空白）；h1 取不到时只记档不判；
 *     ③系统窗口标题必须与 document.title 一致——只改网页标题、任务栏里还是旧名字，
 *        对用键盘换窗口的人等于没改；
 *     ④工作台各视图必须产出互不相同的标题（同一条侧栏入口下七个视图共用一个名字，
 *        就是本批基线红 ① 的形状）；
 *     ⑤全程标题种类 ≥2（整轮只有一个名字＝这条 SC 根本没被满足）。
 *   覆盖下限：屏数、工作台视图数——某一步没跑到就判红，防止判据被静默掏空。
 *   自证针走真判据：把正式行集克隆两份，一份冻成常量（应被 ②⑤ 抓到）、
 *   一份只冻系统窗口标题（应被 ③ 抓到）；针体跑完才置 selfTest.ran，
 *   没跑完必判红——上一批实测过「把自证函数首行 return 掉照样全绿」这个坑。
 *
 * 最终态：TITLE_OK——壳按 screenTitles 发「这一页是什么」，工作台在壳之上细化成
 * 「项目名 · 视图」，尾巴统一接应用名（「大纲 — 创作阅读助手」这一层由发布点拼）。
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { _electron as electron } from "playwright-core";

const root = "/data/creation-reading-assistant";
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-pagetitle-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");
const APP_NAME = "创作阅读助手";

const norm = (s) => (s || "").replace(/\s+/g, "");

const selfTest = { ran: false, reds: [], caughtConstant: false, caughtWindow: false };

/** 纯判据：正式行集与针的克隆行集都走这一个 findings() */
const findings = (rows) => {
  const reds = [];
  for (const r of rows) {
    if (!r.doc || !r.doc.trim()) { reds.push(`①「${r.screen}」document.title 是空的——窗口层没有任何任务识别`); continue; }
    if (!r.doc.includes(APP_NAME)) reds.push(`①「${r.screen}」标题「${r.doc}」没带应用名——同开几个应用时分不出谁是谁`);
    if (r.h1 && !norm(r.doc).includes(norm(r.h1))) {
      reds.push(`②「${r.screen}」标题「${r.doc}」里没有这一屏 h1 的正身「${r.h1}」——窗口里认不出当前任务，或标题与页头两套文案已经漂移`);
    }
    if (r.win !== r.doc) reds.push(`③「${r.screen}」网页标题是「${r.doc}」而系统窗口标题是「${r.win}」——任务栏/Alt+Tab 里还是旧名字`);
  }
  const kinds = new Set(rows.map((r) => r.doc));
  if (kinds.size < 2) {
    reds.push(`⑤全程 ${rows.length} 屏的标题只有 ${kinds.size} 种（「${[...kinds][0] || ""}」）——换屏不产生识别，等于没有页面标题`);
  }
  const wb = rows.filter((r) => r.group === "workbench");
  const wbKinds = new Set(wb.map((r) => r.doc));
  if (wb.length >= 3 && wbKinds.size < wb.length) {
    reds.push(`④工作台走了 ${wb.length} 个视图，标题只 ${wbKinds.size} 种——同一条入口下的视图切换没反映到窗口标题`);
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
    const doc = await page.evaluate(() => {
      const h1 = document.querySelector("h1");
      return { docTitle: document.title, h1: h1 ? h1.textContent.replace(/\s+/g, " ").trim().slice(0, 60) : "" };
    });
    // 系统窗口标题与网页标题各读一次：Electron 默认由 document.title 驱动，
    // 一旦有人显式 setTitle 或监听 page-title-updated 拦住，两者就会分叉——那正是③要抓的。
    const winTitle = await app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].getTitle());
    if (!doc.h1) notes.push(`${screen}：这一屏取不到 h1，②只能记档`);
    rows.push({ screen, group, doc: doc.docTitle, win: winTitle, h1: doc.h1 });
  };

  const side = async (name) => {
    await page.locator(".desktop-sidebar button", { hasText: name }).first().click();
    await page.waitForTimeout(800);
    await snap(name, "shell");
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
  // 弹层不算独立一屏的标题（2.4.2 管的是页面主题；向导只是「创作项目」这一页上的浮层），
  // 但它的标题必须仍是底下那一屏的——所以照样扫，判据一致。
  await snap("弹层·新建向导", "shell");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.waitForTimeout(600);
  await page.locator("input[placeholder='例如：测试项目']").first().fill("标题普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(2400);

  for (const v of ["概览", "写作", "大纲", "全书预览", "设定卡", "写作统计", "版本历史"]) {
    await nav(v, `工作台·${v}`);
  }
  // 退回项目列表不算工作台视图：④这条判据要数的是「七个视图换七个名字」
  await nav("项目列表", "项目·列表", "shell");

  for (const s of ["收件箱", "卡片库", "设置", "书库"]) await side(s);

  /* 自证针：正式行集的克隆，两份各冻一样东西——证明 ②⑤ 与 ③ 这两条腿真的在咬 */
  const frozenConstant = rows.map((r) => ({ ...r, doc: APP_NAME, win: APP_NAME }));
  const frozenWindow = rows.map((r) => ({ ...r, win: APP_NAME }));
  const redsA = findings(frozenConstant);
  const redsB = findings(frozenWindow);
  selfTest.caughtConstant = redsA.some((r) => r.startsWith("②")) && redsA.some((r) => r.startsWith("⑤"));
  selfTest.caughtWindow = redsB.some((r) => r.startsWith("③"));
  if (!selfTest.caughtConstant) selfTest.reds.push("自证失败：把整轮标题冻成应用名，②⑤没抓到——「标题必须说出这一屏在干什么」这条判据是空的。");
  if (!selfTest.caughtWindow) selfTest.reds.push("自证失败：只冻系统窗口标题，③没抓到——「任务栏里也得是这个名字」这条判据是空的。");
  selfTest.ran = true;
  console.log(`自证：冻成常量→${selfTest.caughtConstant ? "抓到" : "没抓到"}、只冻窗口标题→${selfTest.caughtWindow ? "抓到" : "没抓到"}`);

  if (process.env.BD227_DUMP === "1") {
    console.log("=== BD227_DUMP：屏 → 网页标题 / 系统窗口标题 / h1 ===");
    for (const r of rows) console.log(`[${r.screen}]\n   doc: ${r.doc}\n   win: ${r.win}\n   h1 : ${r.h1}`);
    await app.close();
    console.log(`BD227_DUMP 完成：采样 ${rows.length} 屏（未做判红，仅导出现场）。`);
    return -1;
  }

  for (const n of notes) console.log("  记档 " + n);

  const reds = findings(rows);
  const SCREEN_MIN = 13;
  const WORKBENCH_MIN = 5;
  if (rows.length < SCREEN_MIN) reds.push(`走查只剩 ${rows.length} 屏（下限 ${SCREEN_MIN}）——前面某几步没跑到，覆盖面不足。`);
  const wbRows = rows.filter((r) => r.group === "workbench");
  if (wbRows.length < WORKBENCH_MIN) reds.push(`工作台只扫到 ${wbRows.length} 个视图（下限 ${WORKBENCH_MIN}）——④这条判据几乎没被检验。`);
  if (!selfTest.ran) selfTest.reds.push("自证失败：自证针没跑完——②③⑤三条判据本次根本没被检验，判绿不可信。");
  reds.push(...selfTest.reds);

  const kinds = new Set(rows.map((r) => r.doc));
  console.log(`页面标题扫描完成：采样 ${rows.length} 屏，标题 ${kinds.size} 种。`);
  for (const r of rows.slice(0, 3)) console.log(`  样例 [${r.screen}] ${r.doc}`);
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
    console.log("TITLE_OK：每一屏的窗口标题都带着这一屏 h1 的正身与应用名，系统窗口标题与网页标题一致，工作台各视图互不相同。");
    exitCode = 0;
  } else { console.log("TITLE_FAIL：见上面判红项。"); exitCode = 1; }
} catch (error) {
  console.error(error);
  exitCode = 1;
}
process.exit(exitCode);
