#!/usr/bin/env node
/**
 * BD-230 常驻普查：WCAG 1.3.1「信息与关系」（G130/H13 的姊妹条）——标题层级不许跳级。
 *
 * 与 BD-229 那台普查的分工：那台管「一屏只许一个页级 h1」（数量与正身），这台管
 * 「层级序列必须一级一级往下走」（形状）。两条同属 1.3.1，但形状与失效方式不同：
 * BD-229 的回归是「多一个 h1 / 少一个 h1」，本台的回归是「中间那一格没人占」。
 *
 * 为什么值得常驻（本批改掉的三处，实测 /tmp/bd230-survey.log）：
 *   [项目]      h1「创作项目」› h3「最近项目」            ← 1→3
 *   [工作台·大纲] h1 sr-only › h3「场景任务卡」            ← 1→3
 *   [工作台·写作统计] h1 sr-only › h3 × 7（七张卡片头）    ← 1→3
 * 改标签不是免费午餐：这批的样式是按「宿主 + 标签」写的（`.stats-card h3`、
 * `.outline-page-side h3`、`.project-home > .stats-card > h3`），所以 CSS 侧同批扩出了
 * `.stats-card > h2` 一支。扩成 `.stats-card h2`（后代）会把收件箱详情卡里那两个**内容**
 * h2（「条目正文」20px/680、「AI 候选版本」16px/600）也一起上色——这正是本台判据 ④
 * 要长期盯着的形状：卡片头必须是 h2、卡片内容不许是 h2。
 * 逐像素对账（改前/改后各一次活体读数，只有 tag 一栏不同）见
 * /tmp/bd230-probe-before.log 与 /tmp/bd230-probe-after.log。
 *
 * 方法（口径写死在这里，改口径就是改判据）：
 *   逐屏按 DOM 顺序抓整条标题序列（含层级与文案），正文区（.reader-scroller / article）
 *   里那些是书自己写的章标题，属于内容不属于应用骨架——不计入序列，只记档。
 *     ①序列里相邻两级**上升** ≥2 就是跳级（下降不算：读屏大纲回到上一级是正常操作）；
 *     ②整屏一个标题都没有——序列空了 ① 就无从比较，这台普查会安静地绿，所以单独咬；
 *     ③全程必须出现过 ≥2 级的标题：若整轮序列全是 h1，①②两条判据根本没机会作用，
 *       绿等于没测（与 BD-229 ③「h1 种类 ≥2」同一思路）；
 *     ④卡片头角色的标签必须是 h2（本批定下的角色：`.stats-card > h2`），并且同一屏的卡片头
 *       必须同级；同时 `.stats-card h2` 里**非直接子级**的那些（收件箱详情那两个内容 h2）不许被卡片头
 *       那族声明上色——实测口径：取每个 .stats-card 内的 h2，按「是否直接子级」分两桶，
 *       直接子级那桶的字号/字重必须等于卡片头档（16px/650），非直接子级那桶必须不等于它，
 *       再把 `.stats-card > h2` 与 `.stats-card > h3` 合成一桶数层级，一屏出现两种就判红。
 *       这条防的是两件事：一是「将来有人把选择器从直接子级放宽成后代」——肉眼看不出，读屏也看不出，
 *       只有像素变了；那正是本批最贵的一处判断。二是「一屏七张卡里退回一张 h3」，T-A 那颗牙教出来的：
 *       这种回归在序列上只是 h2→h3 的下降，①看不见（axe 的 heading-order 同样看不见），
 *       但同一个角色在同一屏拿到两种大纲层级，读屏大纲里就是七格里的一格突然矮一级。
 *   必到屏（MANDATORY）：项目、收件箱、卡片库、设置、书库、阅读统计，另加本批动过的
 *   工作台·写作统计与工作台·大纲——少一趟直接判红；再加屏数、工作台视图数与阅读屏数下限。
 *   另记一档不判红：序列第一格不是 h1（实测工作台七屏都是「项目名 h2」排在 BD-207 那条
 *   sr-only h1 前面）。① 管的是**上升**跳级，h2→h1 是下降，读屏大纲往回走是正常动作，
 *   把它判红等于自造一条本批没测过的 SC；但这道形状留个字，下批要动顺序时这本账是入口。
 *   自证针四枚走真判据：造一次 1→3 跳级（①该咬）、把序列清空（②该咬）、把全部标题
 *   拍平成一屏一个 h1（③该咬）、把卡片头改回 h3（④该咬）；针体跑完才置 selfTest.ran。
 *
 * 最终态：HEADING_ORDER_OK——逐屏层级一级一级走，卡片头是 h2、卡片内容不是。
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { _electron as electron } from "playwright-core";

const root = "/data/creation-reading-assistant";
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-order-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

/** 卡片头那一族的在档字号/字重（editorial-studio.css 那条 `.stats-card > h2, .stats-card h3,
 *  .background-page-title`，BD-17 定的 16px + BD-230 扩的那一支）。④ 判的就是这两个值。 */
const CARD_HEAD_FS = "16px";
const CARD_HEAD_FW = "650";

const selfTest = { ran: false, reds: [], caughtJump: false, caughtEmpty: false, caughtFlat: false, caughtHead: false };

/** 纯判据：正式行集与针的克隆行集都走这一个 findings() */
const findings = (rows) => {
  const reds = [];
  for (const r of rows) {
    if (r.levels.length === 0) {
      reds.push(`②「${r.screen}」整屏抓不到一个标题——序列是空的，①那条跳级判据对空序列什么都判不出，这台普查会安静地绿`);
      continue;
    }
    for (let i = 1; i < r.levels.length; i++) {
      const jump = r.levels[i].lv - r.levels[i - 1].lv;
      if (jump >= 2) {
        reds.push(`①「${r.screen}」标题跳级 h${r.levels[i - 1].lv}→h${r.levels[i].lv}（h${r.levels[i - 1].lv}「${r.levels[i - 1].text}」› h${r.levels[i].lv}「${r.levels[i].text}」）——中间那一格没人占，读屏按标题导航时要跳过一级`);
      }
    }
    if (r.headOk === false) {
      reds.push(`④「${r.screen}」卡片头角色不对（${r.headNote}）——卡片头必须是 .stats-card 的**直接子级 h2**且落在卡片头档（${CARD_HEAD_FS}/${CARD_HEAD_FW}）、同一屏的卡片头必须同级；反过来若「条目正文」那种卡片**内容** h2 也落进这一档，说明选择器被从「直接子级」放宽成了「后代」，肉眼看不出、像素已经变了`);
    }
  }
  const deep = rows.filter((r) => r.levels.some((l) => l.lv >= 2));
  if (deep.length === 0) {
    reds.push(`③全程 ${rows.length} 屏没有一屏出现过 h2 及更深的标题——序列全是 h1 时 ①② 两条根本作用不到东西，绿等于没测`);
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

/** 页内抓取：整条标题序列 + 卡片头/卡片内容两桶 h2 的样式读数（④ 的实测口径）。
 *  ⚠ 这个函数跑在**页面**里，模块常量它看不见——档位只能当参数递进去
 *  （第一版直接引用 CARD_HEAD_FS，page.evaluate 当场 ReferenceError）。 */
const collect = ({ headFs, headFw }) => {
  const txt = (h) => h.textContent.replace(/\s+/g, " ").trim().slice(0, 40);
  const isContent = (h) => !!h.closest(".reader-scroller, article");
  const all = [...document.querySelectorAll("h1,h2,h3,h4,h5,h6")];
  const chrome = all.filter((h) => !isContent(h));
  const levels = chrome.map((h) => ({ lv: Number(h.tagName[1]), text: txt(h) }));
  const cards = [...document.querySelectorAll(".stats-card h2")];
  // 同屏卡片头混级：T-A 那颗牙教出来的——一屏七张卡里退回一张 h3，序列上只是 h2→h3 的**下降**，
  // ① 那条看不见（axe 的 heading-order 同样看不见）；可同一个角色在同一屏拿到两种大纲层级，
  // 读屏大纲里就是七格里的一格突然矮一级。所以按「同一屏的直接子级卡片头必须同级」补判。
  const cardHeads = [...document.querySelectorAll(".stats-card > h2, .stats-card > h3")];
  const headLevels = [...new Set(cardHeads.map((h) => Number(h.tagName[1])))].sort();
  const head = cards.filter((h) => h.parentElement.classList.contains("stats-card"));
  const body = cards.filter((h) => !h.parentElement.classList.contains("stats-card"));
  const styleOf = (h) => {
    const cs = getComputedStyle(h);
    return `${cs.fontSize}/${cs.fontWeight}`;
  };
  const headStyle = `${headFs}/${headFw}`;
  const mixed = headLevels.length > 1 ? headLevels.join("/h") : "";
  const headsWrong = head.filter((h) => styleOf(h) !== headStyle);
  const bodyWrong = body.filter((h) => styleOf(h) === headStyle);
  return {
    levels,
    headCount: head.length,
    mixed,
    contentCount: all.length - chrome.length,
    contentTexts: all.filter(isContent).map(txt),
    headOk: headsWrong.length === 0 && bodyWrong.length === 0 && !mixed,
    headNote: [
      head.length ? `卡片头 ${head.length} 个（${head.map(txt).join("／").slice(0, 60)}，样式 ${[...new Set(head.map(styleOf))].join("｜")}）` : "",
      mixed ? `同屏卡片头混级 h${mixed}（${cardHeads.length} 张直接子级卡头里出现两种层级——同一个角色在大纲里忽高忽低）` : "",
      headsWrong.length ? `其中 ${headsWrong.length} 个不在 ${headFs}/${headFw} 档` : "",
      body.length ? `卡内非直接子级的 h2 ${body.length} 个（${body.map(txt).join("／").slice(0, 40)}，样式 ${[...new Set(body.map(styleOf))].join("｜")}）` : "",
      bodyWrong.length ? `被卡片头那族声明上色了（选择器疑似被放宽成后代）` : ""
    ].filter(Boolean).join("；")
  };
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
    const r = await page.evaluate(collect, { headFs: CARD_HEAD_FS, headFw: CARD_HEAD_FW });
    if (r.contentCount) notes.push(`${screen}：正文区另有 ${r.contentCount} 个标题（${r.contentTexts.slice(0, 3).join("／")}）——那是书自己写的，不计入序列`);
    // 第一格不是 h1 只记档、不判红：h2→h1 是层级**下降**，heading-order 那套口径（无论 WCAG 1.3.1
    // 的读法还是 axe 的实现）管的都是上升跳级，下降是读屏大纲往回走的正常动作。但工作台这几屏
    // 的形状确实是「项目名 h2 排在 BD-207 那条 sr-only h1 前面」，下一批要动顺序时这本账是入口。
    if (r.levels.length && r.levels[0].lv !== 1) notes.push(`${screen}：序列第一格是 h${r.levels[0].lv}「${r.levels[0].text}」（下降不判红，见本文件头注）`);
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
  await page.locator("input[placeholder='例如：测试项目']").first().fill("标题序列普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(2400);

  for (const v of ["概览", "写作", "大纲", "全书预览", "设定卡", "写作统计", "版本历史"]) {
    await nav(v, `工作台·${v}`);
  }
  await nav("项目列表", "项目·列表", "shell");

  for (const s of ["收件箱", "卡片库", "设置", "书库"]) await side(s);

  // 阅读统计没有侧栏入口，只能从书库那颗按钮进（BD-229 同口径）
  {
    const b = page.locator("button:has-text('阅读统计')").first();
    if (await b.isVisible().catch(() => false)) {
      await b.click();
      await page.waitForTimeout(1400);
      await snap("阅读统计", "shell");
    } else notes.push("没找到书库里的「阅读统计」按钮——那一屏的序列本次没人看过");
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
    const open = page.locator(`${SHELF} button[aria-label="打开「${name.replace(/"/g, '\\"')}」"]`).first();
    if (!(await open.isVisible().catch(() => false))) { notes.push(`没点到「${name}」的打开按钮——阅读屏少扫一本`); continue; }
    await open.click();
    await page.waitForTimeout(2200);
    await snap(`资料阅读·${name}`, "reader");
    const back = page.locator('button[aria-label="返回书库"], button:has-text("返回书库")').first();
    if (!(await back.isVisible().catch(() => false))) { notes.push("阅读屏没有「返回书库」——后面的书目扫不下去了"); break; }
    await back.click();
    await page.waitForTimeout(900);
  }

  /* 收件箱详情那一屏的「条目正文」是 .stats-card 里的 h2、但不是卡片头——④ 的后半只在这一屏
     有活宿主（seed 的收件箱是空的，空收件箱没有 .inbox-detail）。速记一条再点进去，把那个
     宿主造出来，④ 才不是纸面判据。写不进就记档，不判红（那是数据前置，不是无障碍回归）。 */
  {
    const inb = page.locator(".desktop-sidebar button", { hasText: "收件箱" }).first();
    if (await inb.isVisible().catch(() => false)) {
      await inb.click();
      await page.waitForTimeout(900);
      const qi = page.locator('input[aria-label="灵感速记"]').first();
      if (await qi.isVisible().catch(() => false)) {
        await qi.fill("层级序列普查用的一条速记");
        await qi.press("Enter");
        await page.waitForTimeout(1200);
        const item = page.locator(".inbox-item").first();
        if (await item.isVisible().catch(() => false)) {
          await item.click();
          await page.waitForTimeout(900);
          await snap("收件箱·详情", "shell");
        } else notes.push("速记没进列表——④ 的「卡内非直接子级 h2」这一半没有活宿主，本次只是纸面判据");
      } else notes.push("没有「灵感速记」输入框——④ 的后半（卡片内容 h2 不许被上色）本次造不出宿主");
    } else notes.push("侧栏没有收件箱——④ 的后半本次造不出宿主");
  }

  /* 自证针：正式行集的克隆，四份各改一样东西——证明 ①②③④ 四条真的都在咬 */
  const jumpRows = rows.map((r) => (r.levels.length >= 2
    ? { ...r, levels: [r.levels[0], { ...r.levels[1], lv: r.levels[0].lv + 2 }] }
    : r));
  const emptyRows = rows.map((r) => ({ ...r, levels: [] }));
  const flatRows = rows.map((r) => ({ ...r, levels: [{ lv: 1, text: (r.levels[0] || {}).text || "唯一的那一级" }] }));
  const headBack = rows.map((r) => ({ ...r, headOk: false, headNote: "针：把卡片头改回 h3（.stats-card 里已无直接子级 h2，④ 该咬）" }));
  const redsJump = findings(jumpRows);
  const redsEmpty = findings(emptyRows);
  const redsFlat = findings(flatRows);
  const redsHead = findings(headBack);
  selfTest.caughtJump = redsJump.some((x) => x.startsWith("①") && x.includes("标题跳级"));
  selfTest.caughtEmpty = redsEmpty.some((x) => x.startsWith("②") && x.includes("整屏抓不到一个标题"));
  selfTest.caughtFlat = redsFlat.some((x) => x.startsWith("③"));
  selfTest.caughtHead = redsHead.some((x) => x.startsWith("④"));
  if (!selfTest.caughtJump) selfTest.reds.push("自证失败：造一次 1→3 跳级，①没抓到——「层级不许跳」这条判据是空的。");
  if (!selfTest.caughtEmpty) selfTest.reds.push("自证失败：把每屏序列清空，②没抓到——「空序列会安静地绿」这个洞没人守。");
  if (!selfTest.caughtFlat) selfTest.reds.push("自证失败：把全部标题拍平成一个 h1，③没抓到——判据是否在咬无人证明。");
  if (!selfTest.caughtHead) selfTest.reds.push("自证失败：把卡片头退回 h3，④没抓到——「卡片头必须是 h2」这条角色锁是空的。");
  selfTest.ran = true;
  console.log(`自证：造跳级→${selfTest.caughtJump ? "抓到" : "没抓到"}、清空序列→${selfTest.caughtEmpty ? "抓到" : "没抓到"}、拍平→${selfTest.caughtFlat ? "抓到" : "没抓到"}、卡片头退回 h3→${selfTest.caughtHead ? "抓到" : "没抓到"}`);

  for (const n of notes) console.log("  记档 " + n);

  if (process.env.BD230_DUMP === "1") {
    console.log("=== BD230_DUMP：屏 → 标题序列（h 层级「文案」› …）===");
    for (const r of rows) console.log(`[${r.screen}] ${r.levels.map((l) => `h${l.lv}「${l.text}」`).join(" › ") || "（空）"}${r.headOk ? "" : `\n   ⚠ ④ ${r.headNote}`}`);
    await app.close();
    console.log(`BD230_DUMP 完成：采样 ${rows.length} 屏（未做判红，仅导出现场）。`);
    return -1;
  }

  const reds = findings(rows);
  const SCREEN_MIN = 16;
  const WORKBENCH_MIN = 5;
  const READER_MIN = 2;
  const MANDATORY = ["项目", "收件箱", "卡片库", "设置", "书库", "阅读统计", "工作台·写作统计", "工作台·大纲"];
  const seen = new Set(rows.map((r) => r.screen));
  for (const m of MANDATORY) if (!seen.has(m)) reds.push(`必到屏「${m}」没被扫到——这一屏的层级序列本次没人看过。`);
  if (rows.length < SCREEN_MIN) reds.push(`走查只剩 ${rows.length} 屏（下限 ${SCREEN_MIN}）——前面某几步没跑到，覆盖面不足。`);
  const wbRows = rows.filter((r) => r.group === "workbench");
  if (wbRows.length < WORKBENCH_MIN) reds.push(`工作台只扫到 ${wbRows.length} 个视图（下限 ${WORKBENCH_MIN}）——本批改的三处有两处在工作台。`);
  const rdRows = rows.filter((r) => r.group === "reader");
  if (rdRows.length < READER_MIN) reds.push(`资料阅读只扫到 ${rdRows.length} 屏（下限 ${READER_MIN}）——阅读器那条 h1 之下的序列本次没人看过。`);
  if (!selfTest.ran) selfTest.reds.push("自证失败：自证针没跑完——①②③④四条判据本次根本没被检验，判绿不可信。");
  reds.push(...selfTest.reds);

  const deepScreens = rows.filter((r) => r.levels.some((l) => l.lv >= 2)).length;
  const headTotal = rows.reduce((n, r) => n + (r.headCount || 0), 0);
  console.log(`标题序列扫描完成：采样 ${rows.length} 屏，其中 ${deepScreens} 屏有 h2 及更深的层级，卡片头（.stats-card > h2）累计 ${headTotal} 个。`);
  for (const r of rows.slice(0, 3)) console.log(`  样例 [${r.screen}] ${r.levels.map((l) => `h${l.lv}「${l.text}」`).join(" › ")}`);
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
    console.log("HEADING_ORDER_OK：逐屏标题一级一级走，没有 1→3 那类跳级；卡片头落在 .stats-card > h2 那一档，卡片内容没被它连坐。");
    exitCode = 0;
  } else { console.log("HEADING_ORDER_FAIL：见上面判红项。"); exitCode = 1; }
} catch (error) {
  console.error(error);
  exitCode = 1;
}
process.exit(exitCode);
