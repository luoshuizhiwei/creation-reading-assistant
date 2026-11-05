#!/usr/bin/env node
/**
 * 同一功能的说法普查（WCAG 3.2.4 Consistent Identification，AA · **文字维度**）。批次 BD-247 立。
 *
 * 与既有那本 3.2.4 账的分工：scripts/audit-consistent-identification.mjs（BD-226 立）量的是
 * **图案**——把每个可交互件里的 <svg> 求美术签名（几何属性＋CSS 朝向），判「同一功能是否始终
 * 用同一张图、同一张图是否始终表示同一功能」。它自己在头注里写明「纯文字钮（无图标）不计入
 * 图案数」，于是 role=group + aria-pressed 那一族纯文字分段控件的**描述文本**，一本账都没管过。
 * 这条 SC 的原文管的就是「identified with consistent descriptive text」
 * （https://www.w3.org/WAI/WCAG22/Understanding/consistent-identification.html），图案只是其中
 * 一种描述手段；读屏用户学到的往往是名字，不是几何。
 *
 * 为什么要在运行时量：「两处切换是不是同一个功能」纸面上看不出来——源码里是两个不同文件的两段
 * JSX，渲染出来切的是同一对组件。本普查把「同一功能」做成量出来的：每档真按下去，看**这一档
 * 独有**的组件根 class（语义词），两处的语义词族相交就是同一对目的地。判据全靠这份指纹。
 *
 * 判据（三条，任一不成立即红；红因按目的地族／控件点名）：
 *   【同一目的地只许一个名字】按「这档独有的语义词」聚合出来的目的地族，在全程走查里被叫成
 *      两个以上名字即红；只挂了一个名字但与登记的基准不符，也算红（基准不许悄悄过期）。
 *   【同一套切换只许一个组名与档序】两套分段控件如果有 ≥2 个**共同覆盖**的目的地族（同一功能
 *      重复出现），组的可及名与档的先后次序必须一致——用户学到一次就够用。身份只用共同族：
 *      一把切换所在的上下文会带来自身专属的族，拿全集合当身份就认不出这对重复（实测见下）。
 *   【说了要看得见】凡有可见文本的档，那几个字必须真在屏上读得到：承载文本的节点有盒子、
 *      不在屏外、没被 visibility:hidden／display:none 藏掉。测的是**文本节点自己**而不是按钮外壳，
 *      因为 visibility:hidden 藏在任何一层祖先都会继承到文本上——藏在哪一层都跑不掉。
 *      纯色块／纯图标的档（textContent 为空、只挂可及名）不判红，ℹ️ 另账记名：那是图案维度的
 *      事，归 scripts/audit-consistent-identification.mjs（BD-226 美术签名普查）管，本账只管文字。
 *      这条分工本身就是一颗牙：初版把「可见文本为空」也算红，「应用主题」三颗色块在 12 个站点
 *      各刷一条，36 条假红把真红埋了（/tmp/bd247-labels-1.log）；改口径后加了自证针 E，
 *      专门盯着「色块档不许再被这条判据变红」，口径漂回去当场就响。
 *
 * 清点口径（实测出来的取舍，不是纸面偏好）：
 *   · 候选 = role=group 且有 aria-label，组内 ≥2 颗**带 aria-pressed** 的可见未禁用钮——
 *     这就是本仓分段切换的唯一形状（Tabs.tsx:36-41 刻意不用 tablist，因为没有配套 tabpanel，
 *     读屏器会承诺方向键切页签的交互，实际不成立）。
 *   · 语义词 = 含连字符、长度 ≥5、纯小写中划线（不含 [ ] : ( ) ）、且不在噪声前缀表里的
 *     class 词。这条过滤器是一颗牙：初版没有它，普查把「批注与引用」和「版本快照」判成同一
 *     目的地——两档恰好都带 `bg-[color:var(--action-tint)]` 这种选中态工具类
 *     （/tmp/bd247-probe3b.log），通用工具类根本不指向任何功能。
 *   · 目的地族 = 语义词按前两级切片（card-board-card → card-board），一族一条红，免得同一个
 *     缺陷刷出 13 行把噪声埋掉／把真红淹掉。
 *   · 两套切换算不算「同一功能」看**共同覆盖**的族（≥2 族），不看各自的全部族：初版用全部族当
 *     身份，侧栏与大纲页的集合只差 font-semibold／shrink-0 两颗工具类就判成两套机制，判据②对
 *     真实缺陷一声不响（/tmp/bd247-probe4.mjs 转储＋/tmp/bd247-teeth-runtime.log 的 R1 只报①）。
 *     工具类漏进指纹是因为前缀表不全，两头都补：前缀表加上 font-/shrink-/… 那些，身份改用共同族。
 *   · 每档按完必须退回本来按下的那档（快照 aria-pressed="true" 的那颗），否则下一屏从脏状态开始。
 *   · 站点先开起来并验明正身（同 BD-245／BD-246 的口径：点了不等于切过去了，验不过就抛，
 *     空跑必须出声——BD-246 首跑漏了一句 site.open()，12 站全在启动屏空跑照样打 OK）。
 *   · 只有可及名、没有可见文本的档（「应用主题」那种色块钮）ℹ️ 另账记名、不判红；判据③只量
 *     真有文本的那些。这条分工见判据③，配了自证针 E 守着。
 *
 * 摸底实测（/tmp/bd247-probe1.mjs ~ probe3b.log，13 屏、64 条档记录、120 个语义词）先认出
 * 成对的那两处切换；正式走查的账记在下面「干净那轮实测」。
 *   真缺陷一对：工作台·写作的侧栏岛（.writing-outline-view-switch，组名「大纲视图」，档
 *   「大纲树」／「卡片板」）与工作台·大纲的岛（Tabs，组名「大纲视图切换」，档「大纲树」／
 *   「场景卡板」）切的是同一对组件——WritingDeskOutlineSidebar.tsx 180/191 行与
 *   OutlinePage.tsx 329/340 行都是
 *   OutlineTree ↔ CardBoard 二选一，指纹 .outline-tree 族与 .card-board 族完全相同。组名与
 *   第二档的名字都不同 ⇒ 前两条判据同时红。修法：统一到大屏那一套（「大纲视图切换」／
 *   「场景卡板」），措辞也更具体（板上装的是场景卡）。
 *   扫过判为干净的：应用主题、界面缩放、写作模式切换、快捷分类、按状态筛选收件箱、卡片视图、
 *   字数指标预览、历史视图、场景信息页签——各自在全程同名同序；「大纲树」这一档两处本来就同名。
 *   记档不当红：目的地**自己的题头**还留着第三种叫法（卡板工具条 .outline-toolbar-label 写
 *   「卡片板」、两处文案写「树与卡片板…」）——那是标题与散文，不是识别功能的控件；本普查只量
 *   控件，题头与散文的措辞统一留给 2.4.6 那一族一起理。
 *
 * 干净那轮实测（产品改好后重跑，/tmp/bd247-rt-r0-fr.log 与还原后的 /tmp/bd247-rt-r5b-fr.log）：
 * 站点 12 个｜档记录 64 条｜ℹ️ 另账 12 条（每屏一组「应用主题」的三颗色块）｜红 0 条 → LABELS_OK/exit 0。
 * 牙齿（每档跑完逐字节还原，RESTORE_IDENTICAL；数字出自 /tmp/bd247-teeth-runtime*.log 与
 * /tmp/bd247-teeth-static.log）：
 *   R1b 侧栏措辞退回旧叫法 → 红 2 条：判据①点名 .card-board-* 被叫成 ["卡片板","场景卡板"]，
 *      判据②点名同一套切换的组名漂移；
 *   R2 措辞不动、只把档名的可见文本藏掉 → 红 1 条（判据③：档「场景卡板」的识别文本看不见）；
 *   R3b 只把两颗档的先后次序对调 → 红 1 条（判据②的档序分支——判据①看不见这件事，
 *      每个目的地族仍然只挂一个名字）；
 *   C1 关掉噪声过滤器 → 红 1 条（.text-copper-* 把「场景卡板」与「批注与引用」并成同一目的地）；
 *   C2 把登记基准改成与产品不符 → 红 1 条「基准不许悄悄过期」；
 *   静态牙：S1 侧栏退回旧叫法红 5 条（本批两份新账＋WritingDesk 那一栏）、S2 只改普查基准红 1 条
 *   （单位账读 DOM、源码账读基准，各管一头）、S3 拆掉「按完退回原档」红 1 条、S4 判据③回到
 *   「可见文本为空即红」的旧口径红 1 条、S5 把常驻命令从 package.json 摘掉红 1 条。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 900 xvfb-run -a --server-args="-screen 0 1600x1000x24" node scripts/audit-consistent-labels.mjs
 * 退出码：有红 → LABELS_RED / exit 1；有站点或控件被跳过 → LABELS_SKIP / exit 2；全绿 → LABELS_OK / exit 0。
 * 自证针（文末 runNeedles）：喂进正式统计表的**副本**、跑同一个 findings()，三条判据各造一个
 * 红形状（A 异名／B 异组名／C 文本看不见／F 档序对调），再加两支口径针——D 证明「基准不许悄悄
 * 过期」真会响（先把该族的正式行摘掉再喂，否则族挂上两个名字就落进 A 的分支，D 空判过去：实测过），
 * E 证明纯色块档不会被判据③误伤。抓不到／误伤即 LABELS_NEEDLE_FAIL / exit 1。
 * 针不许污染正式表，也不许为针另写判定条件；先看 needleSelfTest.ran 再判红——
 * 把针体整段短路也别想全绿（BD-226 的牙齿自检 T-C 实测过）。
 */
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-consistent-labels-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

/* ---------- 判据表（改这里等于改判据，台账会当场红） ---------- */

// 噪声前缀：通用工具类不指向功能，拿它们当指纹会把两个不相干的档位并成「同一目的地」（实测）。
// 这份表是量出来的：探针转储（/tmp/bd247-probe4.mjs）里两把视图切换的族集合只差 font-semibold 与
// shrink-0 两颗——Tabs 的选中态字重、flex 收缩系数都会随「哪一档正在显示」变化，它们不是目的地。
const NOISE_PREFIX = ["lucide-", "bg-", "text-", "border-", "hover:", "focus:", "min-", "max-", "rounded-", "gap-", "p-", "px-", "py-", "m-", "mx-", "my-", "w-", "h-", "inline-", "flex-", "grid-", "appearance-", "whitespace-", "overflow-", "font-", "shrink-", "grow-", "basis-", "space-", "items-", "justify-", "self-", "order-", "content-", "z-", "ring-", "shadow-", "cursor-", "select-", "align-", "translate-", "scale-", "top-", "left-", "right-", "bottom-", "inset-", "leading-", "tracking-", "opacity-", "sr-"];

/** 目的地族：语义词取前两级（card-board-card → card-board）。 */
const familyOf = (token) => token.split("-").slice(0, 2).join("-");

// 已审的目的地基准：族 → 全应用统一的档名。新增冲突先看它跟谁撞车，再写理由放行。
const DECLARED_FAMILIES = {
  "card-board": { name: "场景卡板", why: "大纲页 Tabs 与写作台侧栏共用的第二档；BD-247 统一到措辞更具体的那一套（板上装的是场景卡）。" },
  "outline-tree": { name: "大纲树", why: "两处切换的第一档本来就同名，登记成基准免得漂移。" }
};

// 已审的重复控件：目的地族集合 → 组名与档序。
const DECLARED_CONTROLS = [
  {
    families: ["card-board", "outline-tree"],
    group: "大纲视图切换",
    order: ["大纲树", "场景卡板"],
    why: "BD-247：大纲页（Tabs）与写作台侧栏（.writing-outline-view-switch）切的是同一对组件，必须同名同序。"
  }
];

/* ---------- 起 app ---------- */

const app = await electron.launch({
  executablePath: electronPath,
  args: [mainJs, "--disable-dev-shm-usage"],
  cwd: root,
  env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir }
});
const page = await app.firstWindow();
await page.waitForLoadState("domcontentloaded");
await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
await page.waitForTimeout(900);

const settle = () =>
  page.evaluate(() =>
    new Promise((resolve) => {
      let timer = setTimeout(done, 900);
      function done() {
        observer.disconnect();
        clearTimeout(timer);
        resolve();
      }
      const observer = new MutationObserver(() => {
        clearTimeout(timer);
        timer = setTimeout(done, 180);
      });
      observer.observe(document.body, { subtree: true, childList: true, attributes: true, characterData: true });
    })
  );

const gotoSidebar = async (label) => {
  for (let attempt = 0; attempt < 3; attempt += 1) {
    await page.locator(".desktop-sidebar button", { hasText: label }).first().click();
    await page.waitForTimeout(600);
    const now = await page.evaluate(() => (document.querySelector("h1")?.textContent || "").trim());
    if (now.includes(label)) return;
    await page.waitForTimeout(700);
  }
  const now = await page.evaluate(() => (document.querySelector("h1")?.textContent || "").trim());
  throw new Error(`点侧栏「${label}」没能切到该屏（当前 h1=「${now}」）`);
};

const openWorkbench = async () => {
  await gotoSidebar("项目");
  const back = page.locator(".project-nav-back").first();
  if (await back.isVisible().catch(() => false)) await back.click();
  await page.waitForSelector(".project-home", { timeout: 15000 });
  if (await page.locator(".project-workbench").isVisible().catch(() => false)) return;
  const createFirst = page.getByRole("button", { name: "新建第一个项目" }).first();
  if (await createFirst.isVisible().catch(() => false)) await createFirst.click();
  else await page.getByRole("button", { name: /新建项目/ }).first().click();
  const wizard = page.locator("[role=dialog]").first();
  await wizard.waitFor({ state: "visible", timeout: 15000 });
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await wizard.locator("input[placeholder='例如：测试项目']").first().fill("一致说法普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(1500);
};

const viewSite = async (label) => {
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const b = page.locator(".project-nav button", { hasText: label }).first();
    if (await b.isVisible().catch(() => false)) {
      await b.click();
      await page.waitForTimeout(600);
    } else {
      await page.waitForTimeout(400);
      continue;
    }
    const on = await page.evaluate((l) => {
      const cur = document.querySelector('.project-nav button[aria-current="page"]');
      return !!cur && `${cur.getAttribute("aria-label") || ""}${cur.textContent || ""}`.includes(l);
    }, label);
    if (on) return;
    await page.waitForTimeout(700);
  }
  throw new Error(`点页签「${label}」没能切到该视图`);
};

/* ---------- 采集 ---------- */

/** 屏上全部语义词（可见元素 class 里过了噪声过滤的那些；页面内的判定与表头 NOISE_PREFIX 同源）。 */
const tokensNow = () =>
  page.evaluate((noise) => {
    const ok = (t) => {
      if (t.length < 5 || !t.includes("-")) return false;
      if (!/^[a-z][a-z0-9-]*$/.test(t)) return false; // 带 [ ] : ( ) 的都是工具类
      return !noise.some((p) => t.startsWith(p));
    };
    const set = new Set();
    for (const el of document.querySelectorAll("[class]")) {
      const r = el.getBoundingClientRect();
      if (r.width === 0 && r.height === 0) continue;
      for (const t of el.classList) if (ok(t)) set.add(t);
    }
    return [...set].sort();
  }, NOISE_PREFIX);

/**
 * 找分段控件，并顺手量这颗档位「看不看得见」：按钮有没有盒子、文本节点有没有被
 * visibility:hidden／display:none 藏掉、有没有整块跑到屏外。textContent 为空但挂了 aria-label 的
 * （纯色块、纯图标那一族）只记名不判红——那是图案维度的账（见头注判据③的分工）。
 */
const findSwitches = () =>
  page.evaluate(() => {
    const out = [];
    let gi = 0;
    for (const host of document.querySelectorAll('[role="group"][aria-label]')) {
      const hr = host.getBoundingClientRect();
      if (hr.width === 0 && hr.height === 0) continue;
      const btns = [...host.querySelectorAll("button")].filter((b) => b.hasAttribute("aria-pressed") && !b.disabled);
      if (btns.length < 2) continue;
      host.setAttribute("data-cl", String(gi));
      btns.forEach((b, bi) => b.setAttribute("data-clopt", `${gi}:${bi}`));
      const opts = btns.map((b, bi) => {
        const r = b.getBoundingClientRect();
        const visibleText = (b.textContent || "").trim();
        // 承载文本的节点 = 按钮本身 + 自己挂着非空文本子节点的 descendants；判「看不见」要每一条都不在屏上。
        const bearers = [b, ...b.querySelectorAll("*")].filter((n) =>
          [...n.childNodes].some((c) => c.nodeType === 3 && c.textContent.trim() !== "")
        );
        const every = (pick) => bearers.length > 0 && bearers.every(pick);
        return {
          k: `${gi}:${bi}`,
          label: (b.getAttribute("aria-label") || visibleText).trim().replace(/\s+/g, " "),
          pressed: b.getAttribute("aria-pressed") === "true",
          vis: {
            box: r.width > 0 && r.height > 0,
            textMode: visibleText.length > 0 ? "text" : "ariaOnly",
            hidden: every((n) => {
              const cs = getComputedStyle(n);
              return cs.visibility === "hidden" || cs.display === "none";
            }),
            offscreen: every((n) => {
              const q = n.getBoundingClientRect();
              return q.right <= 0 || q.bottom <= 0 || q.left >= window.innerWidth || q.top >= window.innerHeight;
            }),
            noBox: every((n) => {
              const q = n.getBoundingClientRect();
              return q.width === 0 || q.height === 0;
            })
          }
        };
      });
      out.push({
        i: gi,
        group: (host.getAttribute("aria-label") || "").trim(),
        cls: (host.getAttribute("class") || "").split(/\s+/).filter(Boolean).slice(0, 2).join("."),
        opts
      });
      gi += 1;
    }
    return out;
  });

const clickOption = (key) =>
  page.evaluate((k) => {
    const el = document.querySelector(`[data-clopt="${k}"]`);
    if (!el) return false;
    el.click();
    return true;
  }, key);

const records = []; // {screen, group, cls, label, own:[语义词], vis:{box,textMode,hidden,offscreen,noBox}}

const sweepSite = async (screen) => {
  await settle();
  const switches = await findSwitches();
  const site = { total: switches.length, groups: 0, skips: [] };
  for (const sw of switches) {
    const snaps = [];
    for (const opt of sw.opts) {
      const hit = await clickOption(opt.k);
      if (!hit) { site.skips.push(`组「${sw.group}」档「${opt.label}」（按下去时这颗钮已经不在了）`); continue; }
      await settle();
      snaps.push({ label: opt.label, tokens: await tokensNow(), vis: opt.vis });
    }
    const origin = sw.opts.find((o) => o.pressed) || sw.opts[0];
    if (origin) { await clickOption(origin.k); await settle(); }
    if (snaps.length < 2) { site.skips.push(`组「${sw.group}」（能测的档不足两颗，无从判断「切换」）`); continue; }
    site.groups += 1;
    for (const snap of snaps) {
      const rest = new Set();
      for (const s of snaps) if (s !== snap) for (const t of s.tokens) rest.add(t);
      records.push({
        screen,
        group: sw.group,
        cls: sw.cls,
        label: snap.label,
        own: snap.tokens.filter((t) => !rest.has(t)),
        vis: snap.vis
      });
    }
  }
  await page.evaluate(() => document.querySelectorAll("[data-cl],[data-clopt]").forEach((n) => { n.removeAttribute("data-cl"); n.removeAttribute("data-clopt"); }));
  return site;
};

/* ---------- 判定（纯函数：正式表与自证针共用同一份） ---------- */

function findings(rows) {
  const reds = [];
  const infos = [];
  const fam = new Map(); // 目的地族 → {names:Set, where:[]}
  for (const r of rows) {
    for (const t of r.own) {
      const f = familyOf(t);
      if (!fam.has(f)) fam.set(f, { names: new Set(), where: [] });
      fam.get(f).names.add(r.label);
      fam.get(f).where.push(r);
    }
  }
  // 【同一目的地只许一个名字】＋基准不许悄悄过期
  for (const [f, info] of fam) {
    const names = [...info.names];
    const declared = DECLARED_FAMILIES[f];
    const src = [...new Set(info.where.map((r) => `${r.screen}·「${r.group}」→「${r.label}」`))].join("；");
    if (names.length >= 2) {
      reds.push(`目的地族 .${f}-* 被叫成 ${JSON.stringify(names)}${declared ? `，登记基准是「${declared.name}」（${declared.why}）` : "（未登记基准）"}｜出处 ${src}`);
    } else if (declared && names[0] !== declared.name) {
      reds.push(`目的地族 .${f}-* 实测只叫「${names[0]}」，与登记基准「${declared.name}」不符（${declared.why}）——基准不许悄悄过期｜出处 ${src}`);
    }
  }
  // 【同一套切换只许一个组名与档序】
  // 控件的「身份证」不用它覆盖的全部族，而用**被两处以上共同覆盖**的那几族：一把切换所在的
  // 上下文会带来自身专属的族（侧栏与大屏周边 UI 不同），拿全集合当身份就认不出「同一功能重复
  // 出现」——probe4 实测：两把视图切换的族集合只差 font-semibold／shrink-0 两颗工具类，
  // 判据②一声不响（/tmp/bd247-teeth-runtime.log 的 R1 只报出判据①那条）。
  const controls = new Map();
  for (const r of rows) {
    const key = `${r.screen}|${r.cls}`;
    if (!controls.has(key)) controls.set(key, { screen: r.screen, cls: r.cls, group: r.group, fams: new Set(), order: [], perOption: [] });
    const c = controls.get(key);
    c.order.push(r.label);
    c.perOption.push({ label: r.label, fams: [...new Set(r.own.map(familyOf))] });
    for (const t of r.own) c.fams.add(familyOf(t));
  }
  const famControls = new Map(); // 目的地族 → 覆盖它的控件集合
  for (const c of controls.values()) {
    const key = `${c.screen}|${c.cls}`;
    for (const f of c.fams) {
      if (!famControls.has(f)) famControls.set(f, new Set());
      famControls.get(f).add(key);
    }
  }
  const byIdentity = new Map();
  for (const c of controls.values()) {
    // 档序签名：按档位先后列出「这一档共有的目的地族」——改名字归判据①管，调换次序这里管。
    c.shared = [...c.fams].filter((f) => (famControls.get(f) || new Set()).size >= 2).sort();
    if (c.shared.length < 2) continue; // 共同覆盖不足两族，说明没量出「同一套切换」这件事
    c.seq = c.perOption.map((o) => o.fams.filter((f) => c.shared.includes(f)).sort().join("+")).join("|");
    const id = c.shared.join(","); // 身份只看共同族；次序是另一笔账（桶内比 seq）
    if (!byIdentity.has(id)) byIdentity.set(id, []);
    byIdentity.get(id).push(c);
  }
  for (const [id, occ] of byIdentity) {
    if (occ.length < 2) continue; // 这套切换全程只出现一次，没有「一致」可判
    const groups = [...new Set(occ.map((o) => o.group))];
    const seqs = [...new Set(occ.map((o) => o.seq))];
    if (groups.length === 1 && seqs.length === 1) continue;
    const declared = DECLARED_CONTROLS.find((d) => d.families.every((f) => id.split(",").includes(f)));
    const detail = occ.map((o) => `${o.screen}「${o.group}」(.${o.cls}) 档序=${JSON.stringify(o.order)}`).join("；");
    reds.push(`同一套切换（共同目的地族 ${id}）说法漂移：组名 ${JSON.stringify(groups)}／档序（按共有族）${JSON.stringify(seqs)}${declared ? `，登记基准是「${declared.group}」+ ${JSON.stringify(declared.order)}（${declared.why}）` : "（未登记基准）"}｜${detail}`);
  }
  // 【说了要看得见】只量有可见文本的档；纯色块／纯图标另账记名（图案维度归 BD-226 那本账）
  const ariaOnly = new Map(); // `${screen}|${group}` → {names:[], n:0}
  for (const r of rows) {
    const v = r.vis || {};
    if (v.textMode === "ariaOnly") {
      const key = `${r.screen}|${r.group}`;
      if (!ariaOnly.has(key)) ariaOnly.set(key, { names: [], n: 0 });
      const a = ariaOnly.get(key);
      a.names.push(r.label);
      a.n += 1;
      continue;
    }
    const why = [];
    if (v.hidden) why.push("文字节点被 visibility:hidden／display:none 藏掉");
    if (v.offscreen) why.push("文字整块在屏外");
    if (v.noBox || !v.box) why.push("文字没有盒子");
    if (why.length > 0) reds.push(`档「${r.label}」（${r.screen}·组「${r.group}」）的识别文本看不见：${why.join("、")}——眼睛用得着的人读不到这几个字`);
  }
  for (const [key, a] of ariaOnly) {
    const [screen, group] = key.split("|");
    infos.push(`ℹ️ 另账（图案维度）：${screen}·组「${group}」的 ${a.n} 档只有可及名、没有可见文本（${a.names.join("／")}）——色块／图标的一致性由 audit-consistent-identification（BD-226）管，本账不判红`);
  }
  return { reds, infos, fam, controls, byIdentity };
}

/* ---------- 走查 ---------- */

const SITES = [
  { label: "侧栏·项目", open: () => gotoSidebar("项目") },
  { label: "侧栏·卡片库", open: () => gotoSidebar("卡片库") },
  { label: "侧栏·收件箱", open: () => gotoSidebar("收件箱") },
  { label: "侧栏·书库", open: () => gotoSidebar("书库") },
  { label: "侧栏·设置", open: () => gotoSidebar("设置") },
  { label: "工作台·概览", open: () => viewSite("概览"), workbench: true },
  { label: "工作台·写作", open: () => viewSite("写作"), workbench: true },
  { label: "工作台·大纲", open: () => viewSite("大纲"), workbench: true },
  { label: "工作台·设定卡", open: () => viewSite("设定卡"), workbench: true },
  { label: "工作台·写作统计", open: () => viewSite("写作统计"), workbench: true },
  { label: "工作台·版本历史", open: () => viewSite("版本历史"), workbench: true },
  { label: "工作台·全书预览", open: () => viewSite("全书预览"), workbench: true }
];

const allSkips = [];
let entered = false;
for (const site of SITES) {
  try {
    if (site.workbench && !entered) { await openWorkbench(); entered = true; }
    await site.open();
    const r = await sweepSite(site.label);
    for (const s of r.skips) allSkips.push(`${site.label} ${s}`);
    console.log(`  站点 ${site.label}：分段控件 ${r.total} 组（可测 ${r.groups} 组）`);
  } catch (error) {
    allSkips.push(`${site.label}（${String(error && error.message ? error.message : error).slice(0, 60)}）`);
  }
}

const { reds, infos } = findings(records);

/* ---------- 自证针 ---------- */

const needleSelfTest = { ran: false, ok: false, why: "" };
function runNeedles() {
  // 喂进正式统计表的**副本**，跑同一个 findings()——不许为针另写判定条件。
  const vis = { box: true, textMode: "text", hidden: false, offscreen: false, noBox: false };
  const copy = records.slice();
  // 针 A：同一个目的地族在两屏被叫成两个名字
  copy.push({ screen: "针A·甲屏", group: "针A切换", cls: "needle-a", label: "针甲名", own: ["needle-dest-root"], vis });
  copy.push({ screen: "针A·乙屏", group: "针A切换", cls: "needle-b", label: "针乙名", own: ["needle-dest-card"], vis });
  // 针 B：目的地族集合相同的两套切换，组名不同
  copy.push({ screen: "针B·甲屏", group: "针B一组", cls: "needle-c", label: "针一", own: ["needlePair-left", "needle-shared-1"], vis });
  copy.push({ screen: "针B·甲屏", group: "针B一组", cls: "needle-c", label: "针二", own: ["needlePair-right", "needle-shared-2"], vis });
  copy.push({ screen: "针B·乙屏", group: "针B二组", cls: "needle-d", label: "针一", own: ["needlePair-left", "needle-shared-1"], vis });
  copy.push({ screen: "针B·乙屏", group: "针B二组", cls: "needle-d", label: "针二", own: ["needlePair-right", "needle-shared-2"], vis });
  // 针 C：档名在可及名里有、可见文本被藏掉（文本节点全在屏外／全被 hidden）
  copy.push({ screen: "针C·甲屏", group: "针C切换", cls: "needle-e", label: "藏起来的档名", own: ["needleClip-left", "needleClip-right-2"], vis: { ...vis, hidden: true } });
  copy.push({ screen: "针C·甲屏", group: "针C切换", cls: "needle-e", label: "另一档", own: ["needleClip-right"], vis });
  // 针 E：纯色块那一族（只有可及名、没有可见文本）——不许被判据③误伤成红。
  // 两档的语义词必须互不相交：共用一个词等于「同一目的地两个名字」，那是判据①该抓的形状
  // （初版就共用了一个 token，E 当场把它抓到——针与判据是同一份，误伤与真红都骗不过去）。
  copy.push({ screen: "针E·色块屏", group: "针E切换", cls: "needle-g", label: "针浅色", own: ["needleSwatch-a", "needleSwatch-chip"], vis: { ...vis, textMode: "ariaOnly" } });
  copy.push({ screen: "针E·色块屏", group: "针E切换", cls: "needle-g", label: "针深色", own: ["needleSwatch-b", "needleSwatch-tint"], vis: { ...vis, textMode: "ariaOnly" } });
  // 针 F：组名与档名都一致，但两处把档位次序对调了——判据②的「档序」分支该响
  // （判据①看不见这件事：每个目的地族仍然只挂一个名字）。
  copy.push({ screen: "针F·甲屏", group: "针F切换", cls: "needle-h", label: "针正一", own: ["needleOrd-a"], vis });
  copy.push({ screen: "针F·甲屏", group: "针F切换", cls: "needle-h", label: "针正二", own: ["needleOrd-b"], vis });
  copy.push({ screen: "针F·乙屏", group: "针F切换", cls: "needle-i", label: "针正二", own: ["needleOrd-b"], vis });
  copy.push({ screen: "针F·乙屏", group: "针F切换", cls: "needle-i", label: "针正一", own: ["needleOrd-a"], vis });
  const found = findings(copy);
  const a = found.reds.some((x) => x.includes("目的地族 .needle-dest") && x.includes("针甲名") && x.includes("针乙名"));  const b = found.reds.some((x) => x.includes("同一套切换") && x.includes("needlePair") && x.includes("针B一组") && x.includes("针B二组"));
  const c = found.reds.some((x) => x.includes("识别文本看不见") && x.includes("藏起来的档名"));
  const e = found.reds.some((x) => x.includes("识别文本看不见") && (x.includes("针浅色") || x.includes("针深色")));
  // 针 D：基准悄悄过期——族只挂一个名字、且与登记的不是同一个。
  // 必须先把该族在正式表里的那几档摘掉再喂：留着它们，族就挂上两个名字，落进判据①的「多名字」
  // 分支，「基准不许悄悄过期」那条永远不响（初版就是这么空判过去的，/tmp/bd247-labels-1.log）。
  const dcopy = records.filter((r) => !r.own.some((t) => familyOf(t) === "outline-tree"));
  dcopy.push({ screen: "针D·甲屏", group: "针D切换", cls: "needle-f", label: "旧称呼", own: ["outline-tree-only"], vis });
  const dfound = findings(dcopy);
  const d = dfound.reds.some((x) => x.includes("基准不许悄悄过期") && x.includes(".outline-tree") && x.includes("旧称呼"));
  const f = found.reds.some((x) => x.includes("同一套切换") && x.includes("needleOrd") && x.includes("针正一") && x.includes("档序"));
  needleSelfTest.ran = true;
  needleSelfTest.ok = a && b && c && d && f && !e;
  needleSelfTest.why = `A(同目的地异名)=${a} B(同控件异组名)=${b} C(说了看不见)=${c} D(基准过期)=${d} E(色块不误伤)=${!e}${e ? "（判据③又把纯图标算红了）" : ""} F(档序对调)=${f}`;
}
runNeedles();

/* ---------- 结账 ---------- */

console.log(`同一说法普查：站点 ${SITES.length} 个｜档记录 ${records.length} 条`);
for (const r of reds) console.log(`  ❌ ${r}`);
for (const i of infos) console.log(`  ${i}`);
for (const s of allSkips) console.log(`  ⚠️ 跳过 ${s}`);

if (!needleSelfTest.ran || !needleSelfTest.ok) {
  console.log(`LABELS_NEEDLE_FAIL 自证针没过：${needleSelfTest.why || "针体没跑起来（把针体短路也算没过）"}`);
  await app.close();
  process.exit(1);
}
if (reds.length > 0) {
  console.log("LABELS_RED 同一个功能在别处是另一个名字");
  await app.close();
  process.exit(1);
}
if (allSkips.length > 0) {
  console.log(`LABELS_SKIP 有 ${allSkips.length} 项被跳过`);
  await app.close();
  process.exit(2);
}
console.log("LABELS_OK 同一功能全程同名同序，而且看得见");
await app.close();
