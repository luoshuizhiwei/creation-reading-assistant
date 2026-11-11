/**
 * 大纲编辑控件的焦点交接普查（WCAG 2.4.3 Focus Order，Level **A**）。批次 BD-251 立。
 *
 * SC 原文：「If a Web page can be navigated sequentially and the navigation sequences affect
 * meaning or operation, focusable components receive focus in an order that preserves meaning
 * and operability」（https://www.w3.org/WAI/WCAG22/Understanding/focus-order.html）
 * 立这条的理由：键盘用户把焦点停在大纲的重排钮／状态下拉上按下它，条目**没有走**，
 * 但那颗控件在事务里被 `disabled`（卡板的 busy）或因为到了边界而永久禁用（树的重排钮），
 * 浏览器当场把焦点吐回 body。事务结束后没人把焦点接回来——用户下一次 Tab 是从整页开头
 * 重新走，刚才在哪一行、还剩几颗钮全丢了。
 *
 * 与既有账的分工（都量过、都不管这件事）：
 *   · scripts/audit-removal-focus.mjs（2.4.3，BD-219/220/221）管的是**条目被删掉、整行卸载**
 *     之后的交接：它的判据是「焦点落在幸存的同类删除钮上」。本账正相反——**条目还在**，
 *     控件本身也还在 DOM 里（实测 document.contains(held)=true），只是被禁用/重挂而丢焦点。
 *     那本账的六个流程里没有一处是「按了就把自己禁用」的形状，所以它全绿而这里全红。
 *   · scripts/audit-keyboard-reach.mjs（2.1.1/2.1.2，BD-245）管 Tab 走得到、Esc 退得出；
 *     它不做「激活某颗控件之后焦点在哪」的测量。
 *   · scripts/audit-context-change.mjs（3.2.2，BD-246）管「一改值就弹影响预览」。那族弹层
 *     **接走**焦点是它自己的题，本账把它记成 ℹ️ 不判红（落点明确、不是 body）。
 *
 * 判据（任一不成立即红）：
 *   【①握得住才算测】先把焦点亲手放到目标控件上并确认 document.activeElement === 它；
 *     握不住（控件此刻 disabled）记 unfocused ⇒ 跳过，不算绿。
 *   【②动作要真发生】按钮：激活前后该层级的**排序签名**必须变；下拉：值必须变。
 *     没变记 unproven ⇒ 跳过 + 整本 exit 2。少了这道，「按下去什么也没发生 ⇒ 焦点当然没动」
 *     会是一整片假绿（BD-250 的牙 T1 就是被这种空跑蒙过去的）。
 *   【③焦点不许跌回 body】事务落定后（2.6s）activeElement 不能是 body。
 *     落在原控件、或同一容器内另一颗说得出的控件上 ⇒ 合规；落在弹层里 ⇒ ℹ️（归 3.2.2）。
 *   【④不许失明】一个站点都没测到 ⇒ 红。站点缺席（控件枚举为 0）记 absent ⇒ 跳过，
 *     **不算合规**——分母被悄悄削掉是本仓踩过的坑（BD-250 的 TXT 屏）。
 *
 * 为什么全程运行时：这是 React 提交 + 浏览器禁用行为叠加出来的 DOM 事实。静态读代码会把
 * 「有 effect 但没触发」「ref 没登记」「禁用时机在 effect 之后」一律看成已修。
 *
 * 清点口径：站点 10 个 = 大纲树 7（卷下移／章上移／章下移／场景上移／场景下移／章状态／章编号）
 * ＋ 场景卡板 3（章状态／场景上移／场景下移）。结构由本账在一次性 profile 里现建
 * （1 卷 + 3 章 + 3 场景，叠加新作品自带的「正文/第一章/默认场景」），绝不碰真实数据。
 *
 * 摸底（产品未改那轮，账 /tmp/bd251-red2.log；早期摸底 /tmp/bd251-probe4.log ＋ probe5.log）：
 * 站点 10 个｜实际测到 10 个｜焦点跌回 body 6 处｜留得住 3 处｜弹层接走 1 处 ⇒ EDIT_FOCUS_RED/exit 1。
 *   ❌ 树·下移卷「正文」／上移章节「普查章二」／上移场景「普查场景二」：焦点=body｜
 *      原控件还在 DOM=true｜disabled=true —— 挪到排序边界后那颗钮永久禁用，焦点没人接。
 *   ❌ 卡板·章节状态下拉／上移场景／下移场景：焦点=body｜原控件还在 DOM=true ——
 *      runEdit 的 busy 期间禁用，事务完成后禁用已解除，焦点却还留在 body。
 *   ✅ 树·下移章节／下移场景／章节状态下拉：焦点留住在原控件（负对照，证明量具不是
 *      「凡动作必红」——这三处按下去不会把自己禁用）。
 *   ℹ️ 树·章节编号下拉：焦点被影响预览弹层的「关闭」钮接走（归 3.2.2 那本账）。
 *
 * 干净那轮实测（产品接上交接钩子后重跑，账 /tmp/bd251-green3.log，与牙的 R0／R9 同形）：
 * 站点 10 个｜实际测到 10 个｜焦点跌回 body 0 处｜留得住 9 处｜弹层接走 1 处 ⇒ EDIT_FOCUS_OK/exit 0。
 * 六处原红各有明确落点：树·下移卷 → 同簇「在卷「正文」新建章节」；树·上移章节 → 同簇
 * 「在章节「普查章二」新建场景」；树·上移场景／卡板·上移场景 → 同簇「下移场景」（反方向那颗
 * 到了边界还可用）；卡板·章节状态下拉／下移场景 → 交回原控件。
 *
 * 修这个缺陷时实测逼出来的两件事（都写进了产品注释，别当成可有可无的装饰）：
 *   ① 只盯 busy 不够：busy 解除之后还有一次结构刷新，重排卡片时 DOM 节点被搬动，
 *     刚接回去的焦点会**再掉一次**（/tmp/bd251-green2.log：其余五处已绿，卡板·上移场景仍红）。
 *     落定信号必须同时含 outline 与 busy，且交接要在一个有限窗口内允许多次重试。
 *   ② 禁用没解除就把焦点交给邻居是错的：等 busy 散了原控件明明能聚焦却回不去了。
 *     故先试原控件、按 0/100/300/800/1500ms 阶梯重试，最后一档才认「真的永久禁用」。
 *
 * 普查自己踩的两件事（都不是产品缺陷，是量法缺陷；各有一条静态账钉着）：
 *   ① 按序号硬取控件 ⇒「握不住」被当成跳过：各层级的首项上移／末项下移本来就 disabled。
 *     首轮 probe4/probe5 把「上移场景/上移章节」读成握不住，分母悄悄少两站。改成按**可用**
 *     过滤取第 nth 个（pickEnabled），红基线这才把这两站量成真红。
 *   ② 上一站留下的影响预览弹层遮罩（z-[80]，盖住整块 outline-page-body）没清 ⇒ 下一站
 *     连视图页签都点不着。首轮红基线三个卡板站点因此被记成「缺席」，10 站只测到 7 站。
 *     每站进场前先 closeOverlays()（Esc 最多三次），10 站这才全测到。
 *
 * 牙（运行时账 /tmp/bd251-teeth-runtime.log；静态账 /tmp/bd251-teeth-static.log）：
 *   每档跑完立刻逐字节还原**并重建**，绝不让下一档踩在脏 out/ 上；R0／R9 都回到
 *   「站点 10 个｜测到 10 个｜跌回 body 0 处｜留得住 9 处｜弹层接走 1 处」。
 *   · T1 两处 focus 调用换成 void（交接整条失效）⇒ 六处跌回 body，与红基线逐条同形（exit 1）。
 *   · T2 判据反接（只在焦点不是 body 时动手）⇒ 同样六处红。
 *   · T3 卡板摘挂 ⇒ 只咬卡板三处；T4 树摘挂 ⇒ 只咬树三处（两档互不误伤，证明分站点是有效的）。
 *   · C1 摘掉「动作要真发生」对照 ⇒ 判决不变（本站点集合里编号那支先判 dialog）：
 *     这一档说明对照在本集合上不是判决的承重墙，它的价值由 C2 单独证。
 *   · C2 按钮改成空按 ⇒ 七个按钮站点全记 unproven、exit 2，绝不许「没反应⇒焦点没动⇒合规」。
 *   · C3 挑控件不过滤 disabled ⇒ 三站记「握不住」、exit 2。
 *   · C4 进站点前不清弹层 ⇒ 卡板三站被遮罩挡住记缺席、10 站只测到 7 站、exit 2。
 *   · C5 判定表把 body 归到 info ⇒ 自证针 A 当场没过，EDIT_FOCUS_NEEDLE_FAIL/exit 1。
 *   静态牙 17 档（S0…S16＋S99）各咬一条：命令登记、站点表条数、动作对照、可用过滤、
 *   清弹层、缺席归类、弹层与空跑的判序、判据反接、重试阶梯、交接窗口、类名外泄、
 *   两处摘挂、落定信号丢 outline、账本头数字、onChangeCapture。S0／S99 全绿。
 *
 * 本轮牙架自己踩的两件事（都不是产品缺陷，是牙架缺陷）：
 *   ① 第一轮 T1/T3/T4 的 splice 让 tsc 报错（不可达代码／TS6133），build exit≠0，
 *     普查跑在**上一档的产物**上，T1 因此出了一次假绿。牙架改成：重建失败即作废本档
 *     （BUILD_FAIL + census SKIPPED），splice 也换成能编译的形状（void / 条件铺开）。
 *     这是 BD-250 记过的同一个坑第二次踩，所以这次把闸门写进牙架而不是只写进注释。
 *   ② 静态牙第一轮 17 档全红，但全是哑的：账本测试里一处字符串引号写坏，整个文件
 *     根本没加载（0 test），每一档「红」都是同一个语法错。S0 基线必须先绿，
 *     否则整轮牙等于没跑——这条也钉在静态账的 S0／S99 上。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 1500 xvfb-run -a --server-args="-screen 0 1600x1000x24" node scripts/audit-edit-focus.mjs
 */import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-edit-focus-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", process.platform === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

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

/* ---------- 站点导航（与其余普查同一套约定） ---------- */

const gotoSidebar = async (label) => {
  // 阅读器/工作台里侧栏会收成图标轨（甚至整条不在），文案 hasText 就点不到——
  // 先按 aria-label 精确点，再退回文案；进任何屏前先把阅读器退干净（「返回书库」
  // 在 EPUB 工具条是文字钮、在 TXT 顶栏是 aria-label，两种都得认）。
  const leaveReader = async () => {
    const back = page.getByRole("button", { name: /返回书库/ }).first();
    if (await back.isVisible().catch(() => false)) { await back.click().catch(() => {}); await page.waitForTimeout(900); }
  };
  for (let attempt = 0; attempt < 3; attempt += 1) {
    await leaveReader();
    const byAria = page.locator(`.desktop-sidebar button[aria-label="${label}"]`).first();
    const target = (await byAria.count()) > 0 ? byAria : page.locator(".desktop-sidebar button", { hasText: label }).first();
    await target.click({ timeout: 8000 });
    await page.waitForTimeout(650);
    const now = await page.evaluate(() => (document.querySelector("h1")?.textContent || "").trim());
    if (now.includes(label)) return;
    if (label === "项目" && now.includes("创作项目")) return;
    await page.waitForTimeout(700);
  }
  const now = await page.evaluate(() => (document.querySelector("h1")?.textContent || "").trim());
  throw new Error(`点侧栏「${label}」没能切到该屏（当前 h1=「${now}」）`);
};

const openWorkbench = async () => {
  await gotoSidebar("项目");
  const back = page.locator(".project-nav-back").first();
  if (await back.isVisible().catch(() => false)) { await back.click(); await page.waitForTimeout(700); }
  await page.waitForSelector(".project-home", { timeout: 15000 });
  if (await page.locator(".project-workbench").isVisible().catch(() => false)) return;
  const createFirst = page.getByRole("button", { name: "新建第一个项目" }).first();
  if (await createFirst.isVisible().catch(() => false)) await createFirst.click();
  else await page.getByRole("button", { name: /新建项目/ }).first().click();
  const wizard = page.locator("[role=dialog]").first();
  await wizard.waitFor({ state: "visible", timeout: 15000 });
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await wizard.locator("input[placeholder='例如：测试项目']").first().fill("编辑焦点普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(1800);
};

const viewSite = async (label) => {
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const b = page.locator(".project-nav button", { hasText: label }).first();
    if (await b.isVisible().catch(() => false)) { await b.click(); await page.waitForTimeout(700); }
    else { await page.waitForTimeout(400); continue; }
    const on = await page.evaluate((l) => {
      const cur = document.querySelector('.project-nav button[aria-current="page"]');
      return !!cur && `${cur.getAttribute("aria-label") || ""}${cur.textContent || ""}`.includes(l);
    }, label);
    if (on) return;
    await page.waitForTimeout(700);
  }
  throw new Error(`点页签「${label}」没能切到该视图`);
};

/* ---------- 站点表（改这里等于改分母，静态账钉着条数与口径） ---------- */

// kind=button 用真键盘 Enter 激活（键盘用户的路径）；kind=select 用 selectOption 改值
// （原生下拉的方向键在 Electron 里走的是系统弹出菜单，脚本驱不动；改值这件事等价）。
// orderSel = 该层级的排序签名来源：动作前后必须变，否则记 unproven。
const SITES = [
  { label: "树·下移卷", view: "tree", kind: "button", sel: '.outline-tree button[aria-label^="下移卷"]', nth: 0, orderSel: ".outline-volume-title" },
  { label: "树·上移章节", view: "tree", kind: "button", sel: '.outline-tree button[aria-label^="上移章节"]', nth: 0, orderSel: ".outline-chapter-title" },
  { label: "树·下移章节", view: "tree", kind: "button", sel: '.outline-tree button[aria-label^="下移章节"]', nth: 0, orderSel: ".outline-chapter-title" },
  { label: "树·上移场景", view: "tree", kind: "button", sel: '.outline-tree button[aria-label^="上移场景"]', nth: 0, orderSel: ".outline-scene-title" },
  { label: "树·下移场景", view: "tree", kind: "button", sel: '.outline-tree button[aria-label^="下移场景"]', nth: 0, orderSel: ".outline-scene-title" },
  { label: "树·章节状态下拉", view: "tree", kind: "select", sel: ".outline-tree .outline-status-select", nth: 1 },
  { label: "树·章节编号下拉", view: "tree", kind: "select", sel: ".outline-tree .outline-numbering-select", nth: 0 },
  { label: "卡板·章节状态下拉", view: "board", kind: "select", sel: ".card-board .outline-status-select", nth: 0 },
  { label: "卡板·上移场景", view: "board", kind: "button", sel: '.card-board button[aria-label^="上移场景"]', nth: 0, orderSel: ".card-board-card-title" },
  { label: "卡板·下移场景", view: "board", kind: "button", sel: '.card-board button[aria-label^="下移场景"]', nth: 0, orderSel: ".card-board-card-title" }
];

/* ---------- 结构种子：现建 1 卷 + 3 章 + 3 场景 ---------- */

// PromptDialog 提交后不自关（addVolume/addChapter/addScene 的 submit 里没有 setPrompt(undefined)，
// 只有拆章/自定义编号那两支自己关），所以每次提交后补一次 Esc 收尾。另账记档，本批不混进来。
const addNode = async (buttonName, title) => {
  const btn = page.getByRole("button", { name: buttonName }).first();
  await btn.click({ timeout: 8000 });
  await page.waitForTimeout(400);
  const dialog = page.locator('[role="dialog"][aria-labelledby="outline-prompt-title"]').first();
  await dialog.waitFor({ state: "visible", timeout: 8000 });
  await dialog.locator("input").first().fill(title);
  await dialog.getByRole("button", { name: "确定", exact: true }).click();
  await page.waitForTimeout(1300);
  await page.keyboard.press("Escape");
  await page.waitForTimeout(600);
};

const seedStructure = async () => {
  await addNode(/新建卷/, "普查卷");
  for (const name of ["普查章一", "普查章二", "普查章三"]) await addNode(new RegExp(`在卷「普查卷」新建章节`), name);
  for (const name of ["普查场景一", "普查场景二", "普查场景三"]) await addNode(/在章节「普查章一」新建场景/, name);
  const volumes = await page.locator(".outline-volume-title").allTextContents();
  const chapters = await page.locator(".outline-chapter-title").allTextContents();
  const scenes = await page.locator(".outline-scene-title").allTextContents();
  if (!volumes.includes("普查卷") || chapters.length < 4 || scenes.length < 4) {
    throw new Error(`结构没建齐（卷=${JSON.stringify(volumes)}｜章=${JSON.stringify(chapters)}｜场景=${JSON.stringify(scenes)}）`);
  }
  console.log(`  结构种子：卷 ${volumes.length}｜章 ${chapters.length}｜场景 ${scenes.length}`);
};

// 上一站可能留下影响预览弹层（编号那支就是），它的遮罩 z-[80] 盖住整块 outline-page-body，
// 不清掉的话下一站连视图页签都点不着（红基线首轮三个卡板站点就是这样被记成缺席的）。
const closeOverlays = async () => {
  for (let i = 0; i < 3; i += 1) {
    if ((await page.locator('[role="dialog"]').count()) === 0) return;
    await page.keyboard.press("Escape");
    await page.waitForTimeout(600);
  }
};

const openView = async (view) => {
  await closeOverlays();
  await page.getByRole("button", { name: view === "tree" ? "大纲树" : "场景卡板", exact: true }).first().click({ timeout: 8000 });
  await page.waitForTimeout(900);
  await page.waitForSelector(view === "tree" ? ".outline-tree" : ".card-board", { timeout: 10000 });
  await settle();
};

// 事务落定：卡板的 busy 会给整片控件挂 disabled，等它散干净再握下一颗，
// 否则「握不住」会被记成跳过，分母悄悄变小。
const settle = async () => {
  await page.waitForTimeout(2600);
};

/* ---------- 页内探针：握住控件 → 激活 → 看焦点去哪了 ---------- */

const holdControl = (loc) => loc.evaluate((el) => {
  window.__held = el;
  el.focus();
  return {
    tag: el.tagName.toLowerCase(),
    label: el.getAttribute("aria-label") ?? "",
    focused: document.activeElement === el,
    disabled: el.disabled === true
  };
});

const focusState = () => page.evaluate(() => {
  const held = window.__held;
  const active = document.activeElement;
  const describe = (el) => el ? `${el.tagName.toLowerCase()}「${el.getAttribute("aria-label") ?? (el.textContent ?? "").trim().slice(0, 20)}」` : "null";
  const container = held ? held.closest(".outline-tree, .card-board") : null;
  return {
    atBody: !active || active === document.body || active === document.documentElement,
    active: describe(active),
    same: active === held,
    inContainer: Boolean(container && active && container.contains(active) && active !== document.body),
    inDialog: Boolean(active && active.closest && active.closest('[role="dialog"]')),
    heldInDom: held ? document.contains(held) : null,
    heldDisabled: held ? held.disabled === true : null
  };
});

// 按「可用」过滤挑第 nth 个：首项的上移／末项的下移本来就 disabled，
// 按序号硬取会把它们读成「握不住」（摸底首轮实测），分母被削掉。
const pickEnabled = async (site) => {
  const all = page.locator(site.sel);
  const total = await all.count();
  let seen = 0;
  for (let i = 0; i < total; i += 1) {
    const el = all.nth(i);
    if (await el.evaluate((node) => node.disabled === true)) continue;
    if (seen === site.nth) return { loc: el, total, index: i };
    seen += 1;
  }
  return { loc: null, total, index: -1 };
};

const orderSignature = (sel) => page.locator(sel).allTextContents();

const measureSite = async (site) => {
  await openView(site.view);
  const picked = await pickEnabled(site);
  if (!picked.loc) return { site: site.label, level: "absent", why: `枚举到 ${picked.total} 颗，但没有一颗可用（第 ${site.nth} 个可用的取不到）` };
  const held = await holdControl(picked.loc);
  if (!held.focused) return { site: site.label, level: "unfocused", why: `焦点放不上去（disabled=${held.disabled}）` };

  const before = site.kind === "select" ? await picked.loc.inputValue() : (await orderSignature(site.orderSel)).join("|");
  if (site.kind === "select") {
    const current = await picked.loc.locator("option:checked").textContent();
    const options = await picked.loc.locator("option").allTextContents();
    const next = options.find((option) => option !== current);
    if (!next) return { site: site.label, level: "unproven", why: "下拉只有一个选项，改不动值" };
    await picked.loc.selectOption({ label: next });
  } else {
    await page.keyboard.press("Enter");
  }
  await page.waitForTimeout(120);
  const early = await focusState();
  await settle();
  const late = await focusState();
  const after = site.kind === "select" ? await picked.loc.inputValue().catch(() => "") : (await orderSignature(site.orderSel)).join("|");
  const acted = after !== before;

  console.log(`    ${site.label}：握住 ${held.tag}「${held.label}」｜120ms 焦点=${early.atBody ? "body" : early.active}｜落定焦点=${late.atBody ? "body" : late.active}｜原控件还在DOM=${late.heldInDom} disabled=${late.heldDisabled}｜动作真发生=${acted}`);

  // 弹层先判：编号／移章那族改值后是「先弹影响预览、确认才写入」，值当然还没变，
  // 拿 acted 去卡它会把这一族误记成空跑。焦点被弹层接走本身就是动作真发生的证据。
  if (late.inDialog) return { site: site.label, level: "dialog", why: `焦点被弹层接走（${late.active}），归 3.2.2 那本账`, early, late };
  if (!acted) return { site: site.label, level: "unproven", why: "激活前后排序／值都没变——这一按什么也没发生，焦点没动不算合规", early, late };
  if (late.atBody) return { site: site.label, level: "body", why: `焦点跌回 body（原控件还在 DOM=${late.heldInDom}、disabled=${late.heldDisabled}）`, early, late };
  if (late.same) return { site: site.label, level: "ok", why: `焦点留住在原控件 ${late.active}`, early, late };
  if (late.inContainer) return { site: site.label, level: "ok", why: `焦点交给了同容器内的 ${late.active}`, early, late };
  return { site: site.label, level: "elsewhere", why: `焦点跑到容器外的 ${late.active}`, early, late };
};

/* ---------- 判定表（正式表与针共用同一个 verdicts()） ---------- */

function verdicts(records, measured) {
  const rows = [];
  if (!measured) rows.push({ level: "red", text: "一个站点都没测到（测到 0 个）——普查失明，分母为 0 的「绿」不算通过" });
  for (const r of records) {
    if (r.level === "body") rows.push({ level: "red", text: `${r.site}：${r.why}（2.4.3：条目没走，焦点却回到了整页开头）` });
    else if (r.level === "elsewhere") rows.push({ level: "red", text: `${r.site}：${r.why}——落点不在本编辑区，说不清意义` });
    else if (r.level === "ok") rows.push({ level: "info", text: `${r.site}：${r.why}` });
    else if (r.level === "dialog") rows.push({ level: "info", text: `${r.site}：${r.why}` });
    else rows.push({ level: "skip", text: `${r.site}：${r.why}——不计绿也不计红` });
  }
  return rows;
}

/* ---------- 走查 ---------- */

console.log("大纲编辑控件的焦点交接普查（2.4.3）——握住控件 → 激活 → 事务落定后看焦点在哪");
const records = [];
let measured = 0;
let seeded = false;
try {
  await openWorkbench();
  await viewSite("大纲");
  await seedStructure();
  seeded = true;
} catch (error) {
  console.log(`  ❌ 进不到大纲页或结构没建齐：${error.message.split("\n")[0]}`);
}
if (seeded) {
  for (const site of SITES) {
    try {
      const record = await measureSite(site);
      records.push(record);
      measured += 1;
    } catch (error) {
      records.push({ site: site.label, level: "absent", why: `测量抛错：${error.message.split("\n")[0].slice(0, 60)}` });
    }
  }
}
await app.close();
rmSync(profileDir, { recursive: true, force: true });

const main = verdicts(records, measured);
const reds = main.filter((r) => r.level === "red");
const infos = main.filter((r) => r.level === "info");
const skips = main.filter((r) => r.level === "skip");
console.log(`\n普查站点 ${SITES.length} 个｜实际测到 ${measured} 个｜焦点跌回 body ${records.filter((r) => r.level === "body").length} 处｜留得住 ${records.filter((r) => r.level === "ok").length} 处｜弹层接走 ${records.filter((r) => r.level === "dialog").length} 处`);
console.log(`红 ${reds.length} 条／ℹ️ ${infos.length} 条／跳过 ${skips.length} 条`);
for (const r of reds) console.log(`  ❌ ${r.text}`);
for (const r of infos) console.log(`  ℹ️ ${r.text}`);
for (const r of skips) console.log(`  ⚠️ ${r.text}`);

/* ---------- 自证针 ---------- */

function runNeedles() {
  const results = [];
  const has = (rows, level) => rows.some((r) => r.level === level);
  // A 焦点跌回 body ⇒ 该红
  const a = verdicts([{ site: "针A", level: "body", why: "焦点跌回 body" }], 3);
  results.push(["A 跌回 body 该红", has(a, "red")]);
  // B 焦点留住 ⇒ 不许红
  const b = verdicts([{ site: "针B", level: "ok", why: "焦点留住在原控件" }], 3);
  results.push(["B 留得住不许误伤", !has(b, "red") && has(b, "info")]);
  // C 弹层接走 ⇒ 不许红（那是 3.2.2 的题）
  const c = verdicts([{ site: "针C", level: "dialog", why: "焦点被弹层接走" }], 3);
  results.push(["C 弹层接走不许判红", !has(c, "red") && has(c, "info")]);
  // D 一个站点都没测到 ⇒ 失明该红
  const d = verdicts([], 0);
  results.push(["D 测到 0 个站点该报失明", has(d, "red")]);
  // E 动作没真发生（unproven）⇒ 不许算绿
  const e = verdicts([{ site: "针E", level: "unproven", why: "这一按什么也没发生" }], 3);
  results.push(["E 空跑不许算绿", !has(e, "red") && !has(e, "info") && has(e, "skip")]);
  // F 握不住（unfocused）⇒ 不许算绿
  const f = verdicts([{ site: "针F", level: "unfocused", why: "焦点放不上去" }], 3);
  results.push(["F 握不住不许算绿", !has(f, "info") && has(f, "skip")]);
  // G 站点缺席（absent）⇒ 不许算绿（分母被削掉不能当合规）
  const g = verdicts([{ site: "针G", level: "absent", why: "枚举到 0 颗" }], 3);
  results.push(["G 站点缺席不许算绿", !has(g, "info") && has(g, "skip")]);
  // H 焦点跑到容器外 ⇒ 该红（别把「不是 body」一律当合规）
  const h = verdicts([{ site: "针H", level: "elsewhere", why: "焦点跑到容器外" }], 3);
  results.push(["H 落到容器外该红", has(h, "red")]);
  // I 全部留得住 ⇒ 全绿
  const i = verdicts(SITES.map((s) => ({ site: s.label, level: "ok", why: "留住" })), SITES.length);
  results.push(["I 全留住该全绿", !has(i, "red") && !has(i, "skip") && i.length === SITES.length]);
  return results;
}

const needleResults = runNeedles();
const needleOk = needleResults.every(([, ok]) => ok);
needleResults.forEach(([name, ok]) => console.log(`  针 ${name}：${ok ? "过" : "**没过**"}`));
console.log(`needleSelfTest.ran=${needleResults.length} passed=${needleOk}`);

if (!needleOk) {
  console.log("EDIT_FOCUS_NEEDLE_FAIL 自证针没过，本轮判定不可信");
  process.exit(1);
}
if (reds.length) {
  console.log("EDIT_FOCUS_RED 编辑控件按下后焦点跌回 body，或者普查失明");
  process.exit(1);
}
if (skips.length) {
  console.log("EDIT_FOCUS_SKIP 有站点没测成（握不住／空跑／缺席）");
  process.exit(2);
}
console.log("EDIT_FOCUS_OK 每个编辑控件按下后焦点都还在说得出的地方");
process.exit(0);