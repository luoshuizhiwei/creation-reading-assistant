/**
 * 键盘可达性普查（WCAG 2.1.1 Keyboard + 2.1.2 No Keyboard Trap，A 级）。批次 BD-245 立。
 *
 * 为什么要在运行时量：本仓既有那张 focus-rings 网走的也是真键盘 Tab，但它判的是
 * 「走到的停靠点有没有可见环」——**没有任何一本账问过「该停的地方停到了没有」**。
 * 一个只挂 onClick 的行、一个 tabindex="-1" 的按钮、一个被 roving tabindex 忘掉初始位的
 * 组，都能让 focus-rings 保持全绿（它根本没走到那儿，自然也无从判环）。可达性这件事
 * 纸面推不出来：它取决于 Blink 真实的顺序焦点导航、shadow DOM 里的分段控件、以及各弹层
 * 自己实现的 Tab 环（use-modal-keyboard.ts:61 每次 keydown 重数一遍 items）。
 *
 * 判据（每个站点按它适用的几条，任一不成立即红）：
 *   【Tab 覆盖】屏上每个「可操作且可见、非禁用、非 aria-hidden、非组内成员」的控件，
 *      必须在这站的 Tab 序列里被走到。走不到的就是「只有鼠标能用」——2.1.1 的字面失败。
 *   【不打小圈】Tab 必须走满一圈回到**起点**；若在起点之外就撞见重复站，说明焦点被关在
 *      一个小圈里（2.1.2 陷阱）。同一站连吃 ≤10 跳不算陷阱（见下面那条日期框实测注记）。
 *   【弹层能键盘退出】开了 aria-modal 的站点：按 Esc 必须①关掉层 ②把焦点交还给打开它
 *      的那个控件（还回 body = 键盘用户下一次 Tab 从页首重走，本仓 use-modal-keyboard.ts:9
 *      自己把这条写成了契约，就该有账守着）。
 *   【键盘激活要有落点】用回车选中一条会「关层/跳屏」的结果：焦点不许跌回 body，必须落在
 *      新屏的着陆容器上（本仓 2.4.3 的落点是 PageTransition 的 .motion-page[tabindex='-1']，
 *      interaction.tsx:271-278）。跌回 body 同样是 2.1.1 的失败——操作完成了，人却丢了位置。
 *
 * 清点口径（都是实测出来的取舍，不是纸面偏好）：
 *   · 模态层打开时**只在层内清点**：Tab 在 aria-modal 层里合法成环，拿全页清点数比就会把
 *     层外正当停靠误报成走不到（BD-245 初跑就栽在向导第三步上）。
 *   · disabled、aria-hidden 子树、display:none／visibility:hidden、零尺寸、未选中的 radio、
 *     option／optgroup、type=file 一律不计——它们不是 Tab 停靠点。
 *   · tabindex="-1" **分两种**：容器/程序化聚焦目标（.motion-page 那种）不计；但一个原生
 *     控件（或 role=button/link/…）挂了 -1 就是「被摘出 Tab 序列的鼠标专用按钮」，必须计成
 *     候选并期待被走到。这条是牙自己咬出来的：BD-245 的运行时牙 R3 给「同步设置」钮挂 -1，
 *     普查照样全绿（443→432，只掉不报）——原判据「-1 一律不计」把 2.1.1 最典型的形状放过
 *     了，改成本注释这个分法之后 R3 才红得出来。roving 组内的 -1 属正常写法，交给方向键检查。
 *   · roving 组（tablist／menu／listbox／tree／radiogroup／grid）成员允许只停一站，
 *     但**必须**用方向键换得动：换不动就红（「走到」不等于「用得上」）。
 *   · `<input type="date">` 会连吃好几跳才出去：对照页（/tmp/bd245-control.html，纯 HTML
 *     无任何产品代码）实测从日期框连按 Tab 得到 `d→d→d→d→b`——Chromium 130 / Electron 33.4.11
 *     的平台行为，不是产品把焦点锁住。所以「同一站连续重复」不算陷阱，只有「回不到起点
 *     的重复」才算；普查若把这条当红，就会在向导第三步（有日期框）常年假红。
 *   · 走查前等 DOM 安静（MutationObserver 250ms 无变动，上限 1500ms），走完再数「还活着的
 *     清点项」：React 中途换掉的节点不算产品红（瞬态），但会单独打出来供核对。
 *
 * 摸底实测（/tmp/bd245-probe.log ~ probe6.log、/tmp/bd245-kb1~3.log）：Tab 覆盖与不打小圈
 * 这两条在 13 站上全绿——书库 20／20、卡片库 22／22、收件箱 31／31、设置 26／26、
 * 项目首页 25／25、工作台六视图 71／67／54／37／39／38、卡片类型编辑器层内 5／5、
 * 全局搜索层内 8／8，合计 443 个停靠点走满。红的是另外两条，且是实测出来的：
 *   ① 全局搜索用侧栏那颗钮打开时（Ctrl/⌘+K 那条路已经记了 opener，按钮这条没记），
 *      Esc 关层后「焦点落回 body，没交还给『打开全局搜索…』」（/tmp/bd245-kb2.log）。
 *      → SearchPanel 的开层 effect 改成通用捕获：只要打开时 activeElement 是元素就记下，
 *        快捷键那条仍然先到先得、不被覆盖。
 *   ② 在层里用回车选中一条要跳屏的结果：结果按钮整棵卸载，焦点直接跌回 body，下一次 Tab
 *      从「最小化窗口」重走（/tmp/bd245-probe8.log）。
 *      → useSearchActions.openResult 的 navigated 分支交给本仓既有的 2.4.3 落点
 *        （.motion-page[tabindex='-1']），不新造机制。
 * 所以本批**改了产品源码**，改的就是这两处。这一本常驻的价值：下次谁把某行改成只有
 * onClick、谁给某个按钮忘了 tabindex、谁新增一个不会交还焦点的弹层，当场就红。
 *
 * 扫过但判为干净的形状（记在这里免得下批重查）：静态那 6 处只有 onClick 的候选，运行时
 * 都能被 Tab 走到（原生 button/a 自带可聚焦），不是 2.1.1 红；hover 才显形那一族已由
 * BD-168 的 hover-only-reveal-source.test.ts 守着；搜索层的上下键选结果在
 * SearchPanel.tsx:192 已实现（配合 .active 高亮），不是「只有鼠标」。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 900 xvfb-run -a --server-args="-screen 0 1600x1000x24" node scripts/audit-keyboard-reach.mjs
 * 退出码：有红 → KEYBOARD_REACH_RED / exit 1；有站点被跳过 → KEYBOARD_REACH_SKIP / exit 2。
 */
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-kbdreach-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

const app = await electron.launch({
  executablePath: electronPath,
  args: [mainJs, "--disable-dev-shm-usage"],
  cwd: root,
  env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir }
});
const page = await app.firstWindow();
await page.waitForLoadState("domcontentloaded");
await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
await page.waitForTimeout(600);

const gotoSidebar = async (label) => {
  await page.locator(".desktop-sidebar button", { hasText: label }).first().click();
  await page.waitForTimeout(400);
};

const openWorkbench = async () => {
  await gotoSidebar("项目");
  const back = page.locator(".project-nav-back").first();
  if (await back.isVisible().catch(() => false)) await back.click();
  await page.waitForSelector(".project-home", { timeout: 15000 });
  const workbench = page.locator(".project-workbench");
  if (await workbench.isVisible().catch(() => false)) return;
  const createFirst = page.getByRole("button", { name: "新建第一个项目" }).first();
  if (await createFirst.isVisible().catch(() => false)) await createFirst.click();
  else await page.getByRole("button", { name: /新建项目/ }).first().click();
  const wizard = page.locator("[role=dialog]").first();
  await wizard.waitFor({ state: "visible", timeout: 15000 });
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await wizard.locator("input[placeholder='例如：测试项目']").first().fill("键盘可达普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(1500);
};

const viewSite = async (label) => {
  await openWorkbench();
  await page.locator(".project-nav button", { hasText: label }).first().click();
  await page.waitForTimeout(500);
};

/** 等 DOM 安静：别在重渲染中途清点（判据会跟着抖）。 */
const settle = () =>
  page.evaluate(() =>
    new Promise((resolve) => {
      let timer = setTimeout(done, 1500);
      function done() {
        observer.disconnect();
        clearTimeout(timer);
        resolve();
      }
      const observer = new MutationObserver(() => {
        clearTimeout(timer);
        timer = setTimeout(done, 250);
      });
      observer.observe(document.body, { subtree: true, childList: true, attributes: true, characterData: true });
    })
  );

/**
 * 一站走查：清点 → 真键盘 Tab → 数「活着的清点项」里谁没被走到。
 * 页面里的函数全部自包含（页边序列化，不能引用 Node 侧变量）。
 */
const sweep = async () => {
  await settle();
  await page.evaluate(() => document.activeElement?.blur?.());
  const listed = await page.evaluate(() => {
    const modal = [...document.querySelectorAll('[role="dialog"][aria-modal="true"]')].pop();
    const scope = modal || document;
    const GROUP = '[role="tablist"],[role="menu"],[role="menubar"],[role="listbox"],[role="tree"],[role="radiogroup"],[role="grid"]';
    // tabindex="-1" 有两种身份，不能一笔豁免：
    //   · 容器/程序化聚焦目标（PageTransition 的 .motion-page 那种）——本来就不该进 Tab 序列；
    //   · **能操作却被摘出 Tab 序列的控件**——这就是 2.1.1 的字面形状（鼠标专用），必须算候选。
    // roving 组里的 -1 是那个模式的正常写法（只有当前员可 Tab 进来），交给方向键那条检查。
    const NATIVE = /^(a|button|input|select|textarea|summary)$/;
    const OPERABLE_ROLE = new Set(["button", "link", "checkbox", "radio", "switch", "tab", "menuitem", "menuitemcheckbox", "menuitemradio", "treeitem", "combobox", "slider"]);
    const rows = [];
    let i = 0;
    for (const el of scope.querySelectorAll('a[href],button,input,select,textarea,summary,[tabindex]')) {
      const tag0 = el.tagName.toLowerCase();
      const neg = el.getAttribute("tabindex") === "-1";
      if (neg && (!NATIVE.test(tag0) && !OPERABLE_ROLE.has((el.getAttribute("role") || "").toLowerCase()))) continue;
      if (neg && el.closest(GROUP)) continue;
      const t = tag0;
      if (t === "option" || t === "optgroup") continue;
      if (el.disabled || el.closest('[aria-hidden="true"]')) continue;
      const cs = getComputedStyle(el);
      if (cs.display === "none" || cs.visibility === "hidden") continue;
      const r = el.getBoundingClientRect();
      if (r.width === 0 && r.height === 0) continue;
      if (t === "input" && el.type === "radio" && !el.checked) continue;
      if (t === "input" && el.type === "file") continue;
      const g = el.closest(GROUP);
      el.setAttribute("data-kb", String(i));
      rows.push({
        i,
        t,
        neg,
        cls: (el.getAttribute("class") || "").split(/\s+/).filter(Boolean).slice(0, 2).join("."),
        name: (el.getAttribute("aria-label") || el.textContent || el.placeholder || el.type || "").trim().slice(0, 30),
        group: g ? `${g.getAttribute("role")}` : null
      });
      i += 1;
    }
    return { rows, modal: !!modal };
  });

  const hits = new Set();
  let first = null;
  let last = null;
  let sameStreak = 0;
  let presses = 0;
  let miniCycle = "";
  const cap = listed.rows.length * 3 + 40;
  for (; presses < cap; presses += 1) {
    await page.keyboard.press("Tab");
    await page.waitForTimeout(28);
    const id = await page.evaluate(() => {
      const el = document.activeElement;
      if (!el || el === document.body) return null;
      return el.getAttribute("data-kb");
    });
    if (id === null) {
      sameStreak = 0;
      continue;
    }
    if (first === null) first = id;
    if (last !== null && id === last) {
      sameStreak += 1;
      // 日期框那类分段控件会连吃几跳（对照页实测 d→d→d→d→b），连吃十跳以内不算陷阱
      if (sameStreak > 10) {
        miniCycle = `同一站连吃 ${sameStreak} 跳没出来（站 ${id}）`;
        break;
      }
      continue;
    }
    sameStreak = 0;
    if (id !== first && hits.has(id)) {
      miniCycle = `Tab 在起点（站 ${first}）之外撞回老站 ${id}，共 ${hits.size} 站在打小圈`;
      break;
    }
    hits.add(id);
    if (id === first && hits.size > 1) break;
    last = id;
  }

  const alive = await page.evaluate(() => [...document.querySelectorAll("[data-kb]")].map((n) => n.getAttribute("data-kb")));
  const aliveSet = new Set(alive);
  const rows = listed.rows.filter((r) => aliveSet.has(String(r.i)));
  const plain = rows.filter((r) => !r.group);
  const missed = plain.filter((r) => !hits.has(String(r.i)));
  const groups = [...new Set(rows.filter((r) => r.group).map((r) => r.group))];

  // 组内方向键：roving tabindex 允许只停一站，但方向键必须在组内换员。
  // 必须用**真键盘**：合成 KeyboardEvent 不触发原生行为（radio 的箭头换选正是原生管的），
  // 用 dispatchEvent 测会得到「按键没反应」——那是探针错，不是产品错（BD-245 记档）。
  const groupReds = [];
  const stampOf = (r) => page.evaluate((role) => {
    const host = [...document.querySelectorAll(`[role="${role}"]`)].filter((n) => n.querySelectorAll("[data-kb]").length > 1).pop();
    if (!host) return null;
    const a = document.activeElement;
    const pick = host.querySelector("input:checked,[aria-checked=true],[aria-selected=true]");
    const aid = a && a.hasAttribute("data-kb") ? a.getAttribute("data-kb") : "组外";
    const pid = pick ? (pick.hasAttribute("data-kb") ? pick.getAttribute("data-kb") : (pick.getAttribute("aria-label") || pick.textContent || pick.value || "").trim().slice(0, 12)) : "";
    return `${aid}|${pid}`;
  }, r);
  for (const role of groups) {
    const ready = await page.evaluate((r) => {
      const host = [...document.querySelectorAll(`[role="${r}"]`)].filter((n) => n.querySelectorAll("[data-kb]").length > 1).pop();
      if (!host) return null;
      const item = [...host.querySelectorAll("[data-kb]")][0];
      item.focus();
      return { size: host.querySelectorAll("[data-kb]").length };
    }, role).catch(() => "err");
    if (ready === "err") {
      groupReds.push(`组 ${role}：方向键检查没跑成（探针异常）`);
      continue;
    }
    if (!ready) continue; // 这种 role 的组里只有 ≤1 个停靠点，方向键无从可测
    const before = await stampOf(role);
    await page.keyboard.press("ArrowDown");
    await page.waitForTimeout(140);
    let after = await stampOf(role);
    if (after === before) {
      await page.keyboard.press("ArrowRight");
      await page.waitForTimeout(140);
      after = await stampOf(role);
    }
    if (after === before) {
      groupReds.push(`组 ${role}（${ready.size} 员）：焦点进得去、方向键换不动——组内其余成员键盘够不着`);
    } else if (after && after.startsWith("组外")) {
      groupReds.push(`组 ${role}：方向键把焦点甩出了组外（${before} → ${after}）`);
    }
  }

  await page.evaluate(() => document.querySelectorAll("[data-kb]").forEach((n) => n.removeAttribute("data-kb")));
  return { modal: listed.modal, total: rows.length, plain: plain.length, missed, groups, groupCount: groups.length, presses, miniCycle, groupReds };
};

/** 弹层的键盘退路：Esc 关层并把焦点还给开门的那个控件。 */
const escapeCheck = async () => {
  return page.evaluate(() => {
    const opener = document.querySelector("[data-kb-opener]");
    if (!opener) return { skip: "没标记开门控件" };
    const still = opener.isConnected;
    const a = document.activeElement;
    const back = a === opener;
    const gone = !document.querySelector('[role="dialog"][aria-modal="true"]');
    return { gone, back, body: !a || a === document.body, still, opener: (opener.getAttribute("aria-label") || opener.textContent || "").trim().slice(0, 20) };
  });
};

const SITES = [
  { label: "书库", open: () => gotoSidebar("书库") },
  { label: "卡片库", open: () => gotoSidebar("卡片库") },
  { label: "收件箱", open: () => gotoSidebar("收件箱") },
  { label: "设置", open: () => gotoSidebar("设置") },
  { label: "项目首页", open: async () => { await openWorkbench(); const b = page.locator(".project-nav-back").first(); if (await b.isVisible().catch(() => false)) await b.click(); await page.waitForTimeout(500); } },
  { label: "工作台·写作", open: () => viewSite("写作") },
  { label: "工作台·大纲", open: () => viewSite("大纲") },
  { label: "工作台·设定卡", open: () => viewSite("设定卡") },
  { label: "工作台·版本历史", open: () => viewSite("版本历史") },
  { label: "工作台·写作统计", open: () => viewSite("写作统计") },
  { label: "工作台·全书预览", open: () => viewSite("全书预览") },
  {
    label: "弹层·卡片类型编辑器",
    dialog: true,
    open: async () => {
      await viewSite("设定卡");
      const opener = page.getByRole("button", { name: /卡片类型/ }).first();
      await page.evaluate(() => document.querySelectorAll("[data-kb-opener]").forEach((n) => n.removeAttribute("data-kb-opener")));
      await opener.evaluate((el) => el.setAttribute("data-kb-opener", "1"));
      await opener.click();
      await page.locator("[role=dialog]").first().waitFor({ state: "visible", timeout: 15000 });
    }
  },
  {
    label: "弹层·全局搜索",
    dialog: true,
    // 第二条键盘出路：打字 → ↓ → 回车选中一条结果。选中即跳屏，层整棵卸载——
    // 这条判的是「跳转之后焦点还有没有落点」（BD-245 实测缺陷②：落回 body）。
    activate: { keyword: "键盘可达" },
    open: async () => {
      // 搜索那颗钮只在侧栏展开时在场（工作台里侧栏收成轨道，它就没了）——先回项目首页再点。
      await openWorkbench();
      const back = page.locator(".project-nav-back").first();
      if (await back.isVisible().catch(() => false)) await back.click();
      await page.waitForSelector(".project-home", { timeout: 15000 });
      await page.evaluate(() => document.querySelectorAll("[data-kb-opener]").forEach((n) => n.removeAttribute("data-kb-opener")));
      const opener = page.locator(".desktop-search-command").first();
      await opener.evaluate((el) => el.setAttribute("data-kb-opener", "1"));
      await opener.click();
      await page.waitForTimeout(600);
    }
  }
];

const reds = [];
const skipped = [];
let stopTotal = 0;

for (const site of SITES) {
  try {
    await site.open();
    const r = await sweep();
    stopTotal += r.plain - r.missed.length;
    const where = site.label;
    if (r.miniCycle) reds.push(`${where} → ${r.miniCycle}`);
    for (const g of r.groupReds) reds.push(`${where} → ${g}`);
    for (const m of r.missed) reds.push(`${where} → Tab 走不到 <${m.t}> .${m.cls}「${m.name}」`);
    if (site.dialog) {
      await page.keyboard.press("Escape");
      await page.waitForTimeout(400);
      const e = await escapeCheck();
      if (e.skip) skipped.push(`${where}（${e.skip}）`);
      else if (!e.gone) reds.push(`${where} → 按 Esc 层没关`);
      else if (e.body) reds.push(`${where} → Esc 后焦点落回 body，没交还给「${e.opener}」`);
      else if (!e.back && e.still) reds.push(`${where} → Esc 后焦点没回到开门控件「${e.opener}」（还在层外别处）`);
    }
    if (site.activate) {
      const kw = site.activate.keyword;
      await page.locator(".desktop-search-command").first().click();
      await page.waitForTimeout(500);
      await page.locator(".uni-search-input").first().fill(kw);
      await page.waitForTimeout(1200);
      const found = await page.evaluate(() => document.querySelectorAll(".uni-search-results button").length);
      if (found === 0) {
        skipped.push(`${where}·选中结果（搜「${kw}」零命中，这条出路无从测）`);
        await page.keyboard.press("Escape").catch(() => {});
      } else {
        await page.keyboard.press("ArrowDown");
        await page.waitForTimeout(200);
        const hl = await page.evaluate(() => (document.querySelector(".uni-search-results button.active")?.textContent || "").trim().slice(0, 20));
        await page.keyboard.press("Enter");
        await page.waitForTimeout(1200);
        const land = await page.evaluate(() => {
          const a = document.activeElement;
          if (!a || a === document.body) return { body: true };
          return { body: false, tag: a.tagName.toLowerCase(), cls: (a.getAttribute("class") || "").split(/\s+/)[0], inLanding: !!a.closest(".motion-page") };
        });
        if (land.body) reds.push(`${where}·选中结果「${hl}」→ 跳屏后焦点落回 body，键盘用户下一次 Tab 从页首重走`);
        else if (!land.inLanding && land.tag !== "button") reds.push(`${where}·选中结果「${hl}」→ 焦点落在 <${land.tag}> .${land.cls}，既不是着陆容器也不是可操作控件`);
        console.log(`  站点 ${where}·选中结果：命中 ${found} 条，高亮「${hl}」，跳屏后焦点=${land.body ? "body ❌" : `<${land.tag}> .${land.cls}${land.inLanding ? "（着陆容器）" : ""}`}`);
      }
    }
    console.log(`  站点 ${where}：可操作 ${r.total}（独立 ${r.plain}、组 ${r.groupCount} 类）｜ 走到 ${r.plain - r.missed.length} ｜ Tab ${r.presses + 1} 跳${r.modal ? " ｜模态层内" : ""}`);
  } catch (error) {
    skipped.push(`${site.label}（${String(error && error.message ? error.message : error).slice(0, 60)}）`);
  }
}

console.log(`键盘可达普查：站点 ${SITES.length} 个、停靠点 ${stopTotal} 个走满`);
for (const r of reds) console.log(`  ❌ ${r}`);
for (const s of skipped) console.log(`  ⚠️ 跳过 ${s}`);
if (reds.length > 0) {
  console.log("KEYBOARD_REACH_RED 有功能只有鼠标能用");
  await app.close();
  process.exit(1);
}
if (skipped.length > 0) {
  console.log(`KEYBOARD_REACH_SKIP 有 ${skipped.length} 项被跳过`);
  await app.close();
  process.exit(2);
}
console.log("KEYBOARD_REACH_OK 每个可操作控件都在 Tab 序列里、弹层都能用键盘退出");
await app.close();
