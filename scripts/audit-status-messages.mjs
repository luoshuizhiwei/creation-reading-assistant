#!/usr/bin/env node
/**
 * 行内状态消息可播报普查（WCAG 4.1.3 Status Messages，AA · **非错误状态维度**）。批次 BD-253 立。
 *
 * 一句话：保存灯从「未保存」跳到「保存中…」再跳到「已保存」，缩放读数从 105% 跳到 110%——
 * 这些都不是错误、也不抢焦点，SC 要求它们「能被程序化地确定」，也就是必须落在一个
 * **变化之前就已经存在**的 live 区里。SC 原文：「In content implemented using markup
 * languages, status messages can be programmatically determined through role or properties
 * such that they can be presented to the user by assistive technologies without receiving
 * focus」（https://www.w3.org/WAI/WCAG22/Understanding/status-messages.html）。
 * 保存灯哑掉的后果很实在：读屏用户敲完一段字，无从知道这段到底存下来了没有。
 *
 * 与既有播报类账的分工（都量过、都不管这件事）：
 *   · BD-175/180/187/189/192/193/195 那一系列盯的是**异步结果与流程反馈**（搜索条数、
 *     正在整理索引…、导出完成），修法是加常驻 sr-only 播报区；它们的分母是那些
 *     一次性消息节点，不含「常驻可见的状态灯」。
 *   · scripts/audit-error-identification.mjs（3.3.1）只管**错误**文本；4.1.3 的非错误腿
 *     不在它的分母里，所以保存灯全哑它照样绿。
 *   · BD-188（writing-desk-volatile-readouts-not-live.test.tsx）反向钉住「逐键刷新的读数
 *     不许挂 live」；BD-233 同理钉住 dock 保存灯「刻意不挂 live，因为编辑器行内那条已经
 *     在播报同一件事」。本账把这两条**豁免**收进站点表当负对照（kind:"mute"），
 *     而不是把它们当缺陷去「修」——修了会把播报刷屏，正是那两本账拦的事。
 *
 * 量具自己踩的两件事（如实记档，都是量具的错不是产品的错）：
 *   ① 收件箱那一站会把人带出工作台，后面五站直接点「写作」页签全部抛错
 *     （/tmp/bd253-red.log 五站 absent）⇒ 进写作前先 ensureWorkbench() 确认页签栏在。
 *   ② 底栏淡入有过渡：关掉速查面板后立刻读 opacity 得 0 ⇒ 绘制不透明度要等它稳定
 *     （最多 5s）再判（/tmp/bd253-red2.log）。
 *   ③ 等稳定之后缩放读数**仍然** invisible，这次不是量具的错：工作台里侧栏是轨栏，
 *     `.desktop-sidebar--rail .desktop-dock-value { display: none }`（editorial-studio.css:573）
 *     ——数值在工作台里根本不画。所以这一站改到整宽侧栏的屏（收件箱）上量
 *     （/tmp/bd253-red3.log）。顺带记一笔另账线索：轨栏里读屏用户能否听到缩放播报
 *     取决于 AT 对 display:none 子树 live 更新的处理，不在本批分母里，另账再查。
 *
 * 判据（对每一次徽标文本变化逐条判，任一不成立即这次听不到）：
 *   【①有 live 宿主】变化节点自身或祖先里有 role=status / role=alert / aria-live≠off。
 *     显式 aria-live="off" 的节点不算宿主，但继续往上找（静态账钉着）。
 *   【②宿主先在】那个宿主在变化**之前**就在 DOM 里（测量前给所有 live 区打标记）。
 *     「插入即带内容的第一帧是哑的」——这是 BD-175 系列反复踩出来的那条，针 B 钉着。
 *   【③没被藏起来】宿主不在 aria-hidden="true" 子树里（针 C）。
 *   【④画得出才算测】徽标祖先链 opacity/display/visibility 乘出的绘制不透明度 <0.99
 *     记 invisible ⇒ 跳过，不算绿（BD-252 的教训：算得出 ≠ 看得见）。
 *   【⑤变化要真发生】动作后徽标文本一次都没变记 unproven ⇒ 跳过 + 整本 exit 2。
 *     少了这道，「什么都没发生 ⇒ 当然没有哑掉的变化」会是一整片假绿（针 G）。
 *   【⑥不许失明】一个站点都没测到 ⇒ 红。站点枚举为 0 记 absent ⇒ 跳过，不算合规。
 *   一次哑就算红，不按多数票（针 E）：读屏用户漏掉的那一次正是「已保存」那一次。
 *
 * 豁免口径（kind:"mute"，两处，都是既有账明确要求的形态）：
 *   · 该读数**不许**有 live 宿主；有了记 flood ⇒ 红（重复播报/刷屏）。
 *   · 若声明了 twinSel（被复述的那条读数），那条必须仍是 live 区；否则记 excuse ⇒ 红
 *     ——「刻意静音」的理由是「别处已经在说同一件事」，别处不说了，这里就得说。
 *
 * 为什么全程运行时：宿主是不是「变化之前就在」、是不是被 aria-hidden 藏着、
 * 祖先有没有 opacity:0，都是 React 挂载时序 + 层叠样式合力的事实。静态读代码会把
 * 「写了 role=status 就算能播报」一律看成已修——速查面板那盏灯正是随内容一起挂载的，
 * 只有真跑一遍才知道它挂载后的每次变化都落在已存在的 polite 区里（所以它是绿的）。
 *
 * 清点口径：站点 6 个 = 该播报 4（收件箱保存灯 / 写作场景保存灯 / 速查面板保存灯 /
 * 底栏缩放百分比）＋ 刻意静音负对照 2（底栏正文保存灯[复述编辑器行内那条] /
 * 专注 HUD 字数[逐键刷新]）。结构在一次性 profile 里现建（1 条速记 + 1 张地点卡），
 * 绝不碰真实数据。速查面板必须先切到「本项目」范围再点列表里的卡片：默认的
 * 「本场景」范围没有引用卡片、「全局搜索」范围要输入关键词才有结果——摸底首轮
 * 就是踩在这里，站点被判 absent（账 /tmp/bd253-status2.log「选中后徽标= 0」）。
 *
 * 摸底（产品未改那轮，账 /tmp/bd253-status3.log；首轮站点缺席的坏轮次 /tmp/bd253-status2.log）：
 * 站点 4 个（当时只列该播报的四支）｜实际测到 4 个｜听不到 1 处 ⇒ 红。
 *   ❌ 收件箱·保存徽标：页内 live 容器 3 个，但这盏灯不在其中任何一个里。
 *      「未保存」「保存中…」「已保存」三次变化，live 宿主=无、变化前已在 DOM=false。
 *      源码对读：InboxItemDetail.tsx 的 span 只有 class，InboxPage 也没有替它播报的常驻区。
 *   ✅ 写作·场景保存徽标：宿主 span.scene-save-status（aria-live="polite"），先在 DOM=true。
 *   ✅ 速查面板·保存徽标：宿主 div.writing-quick-save（role="status"），
 *      「等待失焦保存」「保存中…」两次变化都在挂载后的 polite 区里 ⇒ 可播报。
 *   ✅ 底栏·缩放百分比：宿主 span.desktop-dock-value（aria-live="polite"），105%/110% 两次都在。
 *
 * 修法（只改一处 DOM 属性，不动样式、不动文案、不加常驻区）：给收件箱那盏灯挂
 * aria-live="polite"，与它的双胞胎 SceneEditor 的 .scene-save-status 逐字一致。
 * 为什么不另加 sr-only 常驻播报区：这盏灯本身常驻在条目详情头里（选中条目后一直在 DOM），
 * 就地挂 live 已经满足②；再加一个区会把同一句话说两遍（BD-233 拦的正是这种重复）。
 * 为什么不加 role="status"：同族两盏灯保持一种写法，静态账钉着这条一致性。
 *
 * 干净那轮实测（账 /tmp/bd253-green.log）：站点 6 个｜实际测到 6 个｜听不到 0 处｜
 * 可播报 4 处｜刻意静音 2 处 ⇒ STATUS_MESSAGES_OK/exit 0。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 900 xvfb-run -a --server-args="-screen 0 1600x1000x24" node scripts/audit-status-messages.mjs
 *
 * 牙（运行时账 /tmp/bd253-teeth-runtime.log；静态账 /tmp/bd253-teeth-static.log）：
 * 每档跑完立刻逐字节还原**并重建**，绝不让下一档踩在脏 out/ 上；R0／R9 都回到
 * 「6 站｜测到 6｜听不到 0｜可播报 4｜刻意静音 2」。
 *   · T1 摘掉收件箱那盏灯的 aria-live ⇒ 只咬它一处，与红基线同形（exit 1）。
 *   · T2 把 aria-live 改成 "off" ⇒ 同样只咬它一处（显式 off 不算宿主）。
 *   · T3 把 live 宿主改成随状态重挂的 keyed 包裹（<span key={saveStatus} aria-live>）
 *     ⇒ 每次变化宿主都是新的、首帧哑，咬「宿主先在」那条（exit 1）。
 *   · T4 给灯本身加 aria-hidden="true" ⇒ 宿主落在隐藏子树里，咬③（exit 1）。
 *   · T5 给专注 HUD 挂上 aria-live="polite" ⇒ 只咬那处 mute 站（flood，exit 1）：
 *     反向证明本账不是「凡 live 皆绿」，BD-188 的形态由运行时守住。
 *   · T6 摘掉编辑器行内那条的 aria-live ⇒ 两处红：场景保存灯自己哑了（silent），
 *     dock 保存灯的静音理由也不成立了（excuse）（exit 1）。
 *   · T7 豁免判据反接（没有宿主才算「挂上了 live」）⇒ 两处刻意静音全红（flood 2、exit 1），
 *     该播报的四站不受影响：证明 mute 那条判据也是承重的。
 *   · T8 空按（不动正文/不点保存）⇒ 六站 unproven、测到 0 ⇒ 先报失明红 exit 1、跳过 6。
 *   · T9 反向牙：摘掉收件箱的 aria-live ＋ 把「宿主先在」那条从判据里删掉 ⇒
 *     站点仍红（它连宿主都没有），但自证针 B 当场拦下（STATUS_MESSAGES_NEEDLE_FAIL）。
 *   · T10 反向牙：把 aria-hidden 那条删掉 ⇒ 站点全绿，针 C 拦下（exit 1）。
 *   · 静态 S1…S22 逐条改坏口径/站点表/豁免/针/修法，源码账必须逐档红，S0/S99 绿。
 *
 * 牙架自己踩的一件事（期望没错，是源码账的锚点写漏了，如实记档）：
 *   S21 给专注 HUD 塞 aria-live 那一档第一轮**没红**。原因：源码账取 HUD 段落时锚点写成
 *   `<div className="writing-focus-status">`（带尾部 ">"），牙齿恰恰往这个标签里加属性，
 *   锚点整段消失 ⇒ indexOf 得 -1 ⇒ 切片变空 ⇒ 「不许有 aria-live」假过。
 *   改成锚 `className="writing-focus-status"` 并断言锚点存在后复跑，S21 当场红、S99 回绿。
 *   教训：源码账的切片锚点不许包含牙齿要改的那部分字符。
 */import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-status-messages-"));
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
  await wizard.locator("input[placeholder='例如：测试项目']").first().fill("状态消息普查项目");
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

// 上一站的类型编辑器/命名弹层会留下遮罩，不清掉下一站连页签都点不着（BD-251 踩过）。
const closeOverlays = async () => {
  for (let i = 0; i < 4; i += 1) {
    if ((await page.locator('[role="dialog"]').count()) === 0) return;
    await page.keyboard.press("Escape");
    await page.waitForTimeout(600);
  }
};

/* ---------- 结构种子 ---------- */

const seedInboxItem = async () => {
  await gotoSidebar("收件箱");
  await page.waitForTimeout(1400);
  const quick = page.locator('input[aria-label="灵感速记"]').first();
  await quick.fill("普查灵感一条");
  await quick.press("Enter");
  await page.waitForTimeout(1800);
  const item = page.locator(".inbox-item").first();
  if ((await item.count()) === 0) throw new Error("速记没建出来（.inbox-item 枚举为 0）");
  await item.click();
  await page.waitForTimeout(1200);
  if ((await page.locator(".inbox-detail textarea").count()) === 0) throw new Error("条目详情没打开");
  console.log("  收件箱种子：条目 1 条，详情已打开");
};

const seedCard = async () => {
  await viewSite("设定卡");
  await page.waitForTimeout(1200);
  await page.getByRole("button", { name: /新建卡片/ }).first().click();
  await page.waitForTimeout(900);
  await page.locator('select[aria-label="类型"]').first().selectOption({ label: "地点" });
  await page.locator('input[aria-label="名称"]').first().fill("普查地点");
  await page.getByRole("button", { name: /^(创建卡片|保存修改)/ }).first().click();
  await page.waitForTimeout(2000);
  await closeOverlays();
};

const openProject = async () => {
  await gotoSidebar("项目");
  await page.waitForTimeout(1200);
  const card = page.locator(".project-home-item").first();
  if (await card.count()) { await card.click(); await page.waitForTimeout(2000); }
};

// 收件箱那一站会把人带出工作台，后面每一站进写作前先确认页签栏在（否则点不到「写作」）。
const ensureWorkbench = async () => {
  if ((await page.locator(".project-nav").count()) > 0) return;
  await openProject();
  if ((await page.locator(".project-nav").count()) === 0) throw new Error("回不到项目工作台（.project-nav 缺席）");
};

const openWriting = async () => {
  await ensureWorkbench();
  await viewSite("写作");
  await page.waitForTimeout(2500);
  await page.waitForSelector('[aria-label="场景正文编辑区"]', { timeout: 15000 });
};

const openQuickCard = async () => {
  await openWriting();
  const toggle = page.getByRole("button", { name: /速查/ }).first();
  if ((await page.locator(".writing-quick-reference").count()) === 0) {
    await toggle.click();
    await page.waitForTimeout(1800);
  }
  // 必须切「本项目」：默认的「本场景」没有引用卡片，「全局搜索」要先输关键词。
  const scope = page.locator(".writing-quick-tabs button", { hasText: "本项目" }).first();
  await scope.click();
  await page.waitForTimeout(1600);
  const card = page.locator(".writing-quick-list li button").first();
  if ((await card.count()) === 0) throw new Error("速查「本项目」列表里没有卡片（种子没建出来）");
  await card.click();
  await page.waitForTimeout(1800);
  if ((await page.locator(".writing-quick-save").count()) === 0) throw new Error("速查卡片详情没打开");
};

const closeQuick = async () => {
  const close = page.locator('button[aria-label="关闭写作速查"]').first();
  if (await close.count()) { await close.click(); await page.waitForTimeout(1000); }
};

const typeInScene = async (text) => {
  const editor = page.locator('[aria-label="场景正文编辑区"]').first();
  await editor.click();
  await page.keyboard.type(text, { delay: 60 });
};

/* ---------- 站点表（改这里等于改分母，静态账钉着条数与口径） ---------- */

const SITES = [
  {
    label: "收件箱·保存徽标",
    sel: ".inbox-save-status",
    kind: "announce",
    enter: "inbox",
    act: "inboxSave"
  },
  {
    label: "写作·场景保存徽标",
    sel: ".scene-save-status",
    kind: "announce",
    enter: "writing",
    act: "sceneType"
  },
  {
    label: "速查面板·保存徽标",
    sel: ".writing-quick-save",
    kind: "announce",
    enter: "quickCard",
    act: "quickType"
  },
  {
    label: "底栏·缩放百分比",
    sel: ".desktop-dock-value",
    kind: "announce",
    enter: "wideSidebar",
    act: "zoom"
  },
  {
    label: "底栏·正文保存灯（刻意静音·复述编辑器行内那条）",
    sel: ".desktop-dock-status",
    kind: "mute",
    twinSel: ".scene-save-status",
    enter: "writingPlain",
    act: "sceneType"
  },
  {
    label: "专注 HUD 字数（刻意静音·逐键刷新）",
    sel: ".writing-focus-status",
    kind: "mute",
    enter: "focusMode",
    act: "sceneType"
  }
];

const ENTER = {
  inbox: async () => {
    await gotoSidebar("收件箱");
    await page.waitForTimeout(1400);
    const item = page.locator(".inbox-item").first();
    if ((await item.count()) === 0) { await seedInboxItem(); return; }
    await item.click();
    await page.waitForTimeout(1200);
  },
  writing: async () => { await openWriting(); },
  quickCard: async () => { await openQuickCard(); },
  writingPlain: async () => { await closeQuick(); await openWriting(); },
  // 工作台里侧栏是轨栏（.desktop-sidebar--rail），缩放数值 display:none（editorial-studio.css:573）
  // ⇒ 必须回到整宽侧栏的屏上量，否则这站恒 invisible。
  wideSidebar: async () => { await gotoSidebar("收件箱"); await page.waitForTimeout(1200); },
  focusMode: async () => {
    await closeQuick();
    await openWriting();
    if ((await page.locator(".writing-focus-status").count()) === 0) {
      await page.locator('button[aria-label="专注模式"]').first().click();
      await page.waitForTimeout(1400);
    }
  }
};

const ACT = {
  inboxSave: async () => {
    const body = page.locator(".inbox-detail textarea").first();
    await body.click();
    await body.type("追加一句。", { delay: 60 });
    await page.waitForTimeout(600);
    const save = page.locator(".inbox-detail button", { hasText: "保存" }).first();
    if (await save.count()) await save.click();
  },
  sceneType: async () => { await typeInScene("普查正文一句。"); },
  quickType: async () => {
    const input = page.locator(".writing-quick-field input").first();
    await input.click();
    await page.keyboard.type("改", { delay: 60 });
    await page.keyboard.press("Tab");
  },
  zoom: async () => {
    const plus = page.locator('button[aria-label="放大界面"]').first();
    await plus.click();
    await page.waitForTimeout(700);
    await plus.click();
  }
};

/* ---------- 页内量具：给 live 区打「先在」标记，再监听徽标文本变化 ---------- */

// 测量前把当下所有 live 区打上标记：变化发生时若宿主没有这个标记，
// 说明它是随内容一起插进来的 —— 那一帧 polite 区是哑的（BD-175）。
const markLive = () => page.evaluate(() => {
  const live = Array.from(document.querySelectorAll('[role="status"], [role="alert"], [aria-live]'))
    .filter((el) => (el.getAttribute("aria-live") || "polite") !== "off");
  for (const el of live) el.setAttribute("data-bd253-pre", "1");
  return live.length;
});

const installWatcher = (sel) => page.evaluate((selector) => {
  window.__bd253 = [];
  const chips = Array.from(document.querySelectorAll(selector));
  const describe = (el) => {
    for (let node = el; node; node = node.parentElement) {
      if (!node.getAttribute) continue;
      const role = node.getAttribute("role");
      const live = node.getAttribute("aria-live");
      // 显式 off 的节点不算宿主，但继续往上找（静态账钉着这条 continue）。
      if (live === "off") continue;
      if (role === "status" || role === "alert" || (live && live !== "off")) {
        return {
          host: `${node.tagName.toLowerCase()}.${node.getAttribute("class") || ""}`,
          pre: node.getAttribute("data-bd253-pre") === "1",
          hidden: node.closest('[aria-hidden="true"]') !== null
        };
      }
    }
    return { host: null, pre: false, hidden: false };
  };
  const push = (el, text) => {
    window.__bd253.push({ text: String(text).trim().slice(0, 30), ...describe(el) });
  };
  window.__bd253obs = new MutationObserver((records) => {
    for (const r of records) {
      const target = r.target.nodeType === 3 ? r.target.parentElement : r.target;
      if (!target || !target.closest) continue;
      const chip = target.closest(selector);
      if (!chip) continue;
      push(chip, chip.textContent);
    }
  });
  for (const chip of chips) {
    window.__bd253obs.observe(chip, { childList: true, characterData: true, subtree: true });
    push(chip, chip.textContent);
  }
  return chips.length;
}, sel);

const readWatcher = () => page.evaluate(() => {
  if (window.__bd253obs) window.__bd253obs.disconnect();
  const all = window.__bd253 || [];
  const seen = new Set();
  const uniq = [];
  for (const r of all) {
    const key = `${r.text}|${r.host}|${r.pre}|${r.hidden}`;
    if (!seen.has(key)) { seen.add(key); uniq.push(r); }
  }
  return uniq;
});

// 徽标自己画不画得出（判据④）：祖先链 opacity 乘积，display:none/visibility:hidden 直接 0。
const paintedOf = (sel) => page.evaluate((selector) => {
  const el = document.querySelector(selector);
  if (!el) return 0;
  let painted = 1;
  for (let node = el; node && node !== document.documentElement; node = node.parentElement) {
    const s = getComputedStyle(node);
    painted *= Number(s.opacity);
    if (s.display === "none" || s.visibility === "hidden") painted = 0;
  }
  return +painted.toFixed(3);
}, sel);

// 「刻意静音」的理由还在不在：被复述的那条读数必须仍是 live 区。
const twinLiveOf = (sel) => page.evaluate((selector) => Array.from(document.querySelectorAll(selector))
  .filter((el) => {
    const live = el.getAttribute("aria-live");
    const role = el.getAttribute("role");
    return (live && live !== "off") || role === "status" || role === "alert";
  }).length, sel);

/* ---------- 判据（正式表与针共用同一个 audible()/levelOf()/verdicts()） ---------- */

function audible(row) {
  return Boolean(row.host) && row.pre === true && row.hidden !== true;
}

function levelOf(site, obs) {
  if (obs.painted < 0.99) return { level: "invisible", why: `徽标没画出来（绘制不透明度 ${obs.painted}）` };
  const changes = obs.changes ?? [];
  if (changes.length === 0) return { level: "unproven", why: "动作后徽标文本一次都没变，没测到状态消息" };
  if (site.kind === "mute") {
    const live = changes.filter((row) => row.host);
    if (live.length) {
      return { level: "flood", why: `刻意静音的读数挂上了 live 宿主（${live[0].host}）——会重复播报／刷屏` };
    }
    if (site.twinSel && !obs.twinLive) {
      return { level: "excuse", why: `静音的理由不成立：被复述的那条读数（${site.twinSel}）已不是 live 区` };
    }
    return { level: "muteok", why: `刻意静音 ${changes.length} 次变化都没有 live 宿主，符合既有豁免口径` };
  }
  const silent = changes.filter((row) => !audible(row));
  if (silent.length) {
    return {
      level: "silent",
      why: `${changes.length} 次状态变化里有 ${silent.length} 次读屏器听不到`,
      silent
    };
  }
  return { level: "ok", why: `${changes.length} 次状态变化都落在变化前就存在的 live 宿主里（${changes[0].host}）` };
}

/* ---------- 逐站测量 ---------- */

const measureSite = async (site) => {
  await ENTER[site.enter]();
  await page.waitForTimeout(1200);
  if ((await page.locator(site.sel).count()) === 0) {
    return { site: site.label, level: "absent", why: "徽标枚举为 0（站点缺席）" };
  }
  // 底栏淡入是有过渡的：关掉速查面板后立刻取一次 opacity 会读到 0，
  // 把个能播报的站点误记成 invisible（红基线第二轮 /tmp/bd253-red2.log 踩过）。
  // 等它画出来再判，等不到才记 invisible。
  let painted = await paintedOf(site.sel);
  for (let i = 0; i < 10 && painted < 0.99; i += 1) {
    await page.waitForTimeout(500);
    painted = await paintedOf(site.sel);
  }
  const liveCount = await markLive();
  const chips = await installWatcher(site.sel);
  if (chips === 0) return { site: site.label, level: "absent", why: "监听器装不上（徽标枚举为 0）" };
  const twinLive = site.twinSel ? await twinLiveOf(site.twinSel) : null;
  await ACT[site.act]();
  await page.waitForTimeout(3200);
  const rows = await readWatcher();
  const changes = rows.slice(1);
  const verdict = levelOf(site, { painted, changes, twinLive });
  return {
    site: site.label,
    ...verdict,
    trace: changes.map((r) => `「${r.text}」｜宿主=${r.host || "无"}｜先在=${r.pre}｜aria-hidden=${r.hidden}`),
    liveCount,
    twinLive
  };
};

/* ---------- 判定表（正式表与针共用同一个 verdicts()） ---------- */

function verdicts(records, measured) {
  const rows = [];
  if (!measured) rows.push({ level: "red", text: "一个站点都没测到（测到 0 个）——普查失明，分母为 0 的「绿」不算通过" });
  for (const r of records) {
    if (r.level === "silent") rows.push({ level: "red", text: `${r.site}：${r.why}（4.1.3：保存到哪一步了，读屏用户无从知道）` });
    else if (r.level === "flood") rows.push({ level: "red", text: `${r.site}：${r.why}（BD-188/BD-233 的豁免形态被破坏）` });
    else if (r.level === "excuse") rows.push({ level: "red", text: `${r.site}：${r.why}（别处不说了，这里就得说）` });
    else if (r.level === "ok" || r.level === "muteok") rows.push({ level: "info", text: `${r.site}：${r.why}` });
    else rows.push({ level: "skip", text: `${r.site}：${r.why}——不计绿也不计红` });
  }
  return rows;
}

/* ---------- 走查 ---------- */

console.log("行内状态消息可播报普查（4.1.3 非错误状态维度）——保存灯与读数跳动时，读屏器听不听得见");
const records = [];
let measured = 0;
let seeded = false;
try {
  await openWorkbench();
  await seedInboxItem();
  await openProject();
  await seedCard();
  await openWriting();
  seeded = true;
} catch (error) {
  console.log(`  ❌ 种子没建齐：${error.message.split("\n")[0]}`);
}
if (seeded) {
  for (const site of SITES) {
    try {
      const record = await measureSite(site);
      records.push(record);
      if (["ok", "silent", "muteok", "flood", "excuse"].includes(record.level)) measured += 1;
      const mark = record.level === "ok" || record.level === "muteok" ? "✅" : ["silent", "flood", "excuse"].includes(record.level) ? "❌" : "⚠️";
      console.log(`  ${mark} ${record.site}：${record.why}`);
      if (record.liveCount !== undefined) console.log(`      页内 live 容器 ${record.liveCount} 个${record.twinLive === null ? "" : `｜被复述那条的 live 宿主 ${record.twinLive} 个`}`);
      for (const line of record.trace ?? []) console.log(`      ${line}`);
    } catch (error) {
      records.push({ site: site.label, level: "absent", why: `测量抛错：${error.message.split("\n")[0].slice(0, 80)}` });
      console.log(`  ⚠️ ${site.label}：测量抛错 ${error.message.split("\n")[0].slice(0, 80)}`);
    }
  }
}
await app.close();
rmSync(profileDir, { recursive: true, force: true });

const main = verdicts(records, measured);
const reds = main.filter((r) => r.level === "red");
const infos = main.filter((r) => r.level === "info");
const skips = main.filter((r) => r.level === "skip");
const count = (level) => records.filter((r) => r.level === level).length;
console.log(`\n普查站点 ${SITES.length} 个｜实际测到 ${measured} 个｜听不到 ${count("silent")} 处｜可播报 ${count("ok")} 处｜刻意静音 ${count("muteok")} 处`);
console.log(`红 ${reds.length} 条／ℹ️ ${infos.length} 条／跳过 ${skips.length} 条`);
for (const r of reds) console.log(`  ❌ ${r.text}`);
for (const r of infos) console.log(`  ℹ️ ${r.text}`);
for (const r of skips) console.log(`  ⚠️ ${r.text}`);

/* ---------- 自证针（喂进同一个 levelOf() + verdicts()，不另写判定条件） ---------- */

function runNeedles() {
  const ANNOUNCE = { kind: "announce", label: "针·该播报" };
  const MUTE = { kind: "mute", label: "针·刻意静音", twinSel: ".scene-save-status" };
  const row = (patch = {}) => ({ text: "已保存", host: "span.inbox-save-status", pre: true, hidden: false, ...patch });
  const judge = (site, obs) => {
    const v = levelOf(site, { painted: 1, twinLive: 1, ...obs });
    return verdicts([{ site: site.label, ...v }], 1);
  };
  const has = (rows, level) => rows.some((r) => r.level === level);
  const results = [];

  // A 没有 live 宿主（收件箱那盏灯的真实形状）⇒ 该红
  const a = judge(ANNOUNCE, { changes: [row({ host: null, pre: false })] });
  results.push(["A 没有 live 宿主该红", has(a, "red")]);
  // B 宿主随内容一起插进来（首帧哑）⇒ 该红
  const b = judge(ANNOUNCE, { changes: [row({ pre: false })] });
  results.push(["B 宿主不在先该红", has(b, "red")]);
  // C 宿主被 aria-hidden 藏着 ⇒ 该红
  const c = judge(ANNOUNCE, { changes: [row({ hidden: true })] });
  results.push(["C 宿主被藏起来该红", has(c, "red")]);
  // D 宿主先在且没藏 ⇒ 不许红
  const d = judge(ANNOUNCE, { changes: [row(), row({ text: "保存中…" })] });
  results.push(["D 宿主先在不许误伤", !has(d, "red") && has(d, "info")]);
  // E 三次里只哑一次 ⇒ 照样红（不按多数票）
  const e = judge(ANNOUNCE, { changes: [row(), row({ text: "保存中…" }), row({ text: "已保存", host: null, pre: false })] });
  results.push(["E 只哑一次也该红", has(e, "red")]);
  // F 刻意静音但没声明被复述的那条（专注 HUD）⇒ 不查「理由」，只查有没有挂 live
  const f = judge({ kind: "mute", label: "针·无复述对象" }, { twinLive: 0, changes: [row({ host: null, pre: false })] });
  results.push(["F 无复述对象的静音不许误伤", !has(f, "red") && has(f, "info")]);
  // G 一次变化都没有 ⇒ 不许算绿（空跑）
  const g = judge(ANNOUNCE, { changes: [] });
  results.push(["G 空跑不许算绿", !has(g, "red") && !has(g, "info") && has(g, "skip")]);
  // H 徽标画不出来 ⇒ 不许算绿，也不许当成「听不到」
  const h = judge(ANNOUNCE, { painted: 0, changes: [row({ host: null, pre: false })] });
  results.push(["H 画不出来不许算绿", !has(h, "info") && !has(h, "red") && has(h, "skip")]);
  // I 一个站点都没测到 ⇒ 失明该红
  results.push(["I 测到 0 个站点该报失明", has(verdicts([], 0), "red")]);
  // J 全部可播报／刻意静音 ⇒ 全绿
  const j = verdicts(SITES.map((s) => ({ site: s.label, level: s.kind === "mute" ? "muteok" : "ok", why: "符合口径" })), SITES.length);
  results.push(["J 全部符合口径该全绿", !has(j, "red") && !has(j, "skip") && j.length === SITES.length]);
  // K 刻意静音的读数挂上了 live ⇒ 该红（刷屏）
  const k = judge(MUTE, { changes: [row({ host: "div.writing-focus-status" })] });
  results.push(["K 静音读数挂 live 该红", has(k, "red")]);
  // L 刻意静音且无宿主 ⇒ 不许红
  const l = judge(MUTE, { changes: [row({ host: null, pre: false })] });
  results.push(["L 刻意静音不许误伤", !has(l, "red") && has(l, "info")]);
  // M 静音的理由没了（被复述那条不再 live）⇒ 该红
  const m = judge(MUTE, { twinLive: 0, changes: [row({ host: null, pre: false })] });
  results.push(["M 静音理由不成立该红", has(m, "red")]);
  // N role=alert 也算宿主（SC 认 role 或属性两条腿）⇒ 不许红
  const n = judge(ANNOUNCE, { changes: [row({ host: "div.some-alert" })] });
  results.push(["N role 宿主不许误伤", !has(n, "red") && has(n, "info")]);
  return results;
}

const needleResults = runNeedles();
const needleOk = needleResults.every(([, ok]) => ok);
needleResults.forEach(([name, ok]) => console.log(`  针 ${name}：${ok ? "过" : "**没过**"}`));
console.log(`needleSelfTest.ran=${needleResults.length} passed=${needleOk}`);

if (!needleOk) {
  console.log("STATUS_MESSAGES_NEEDLE_FAIL 自证针没过，本轮判定不可信");
  process.exit(1);
}
if (reds.length) {
  console.log("STATUS_MESSAGES_RED 状态消息读屏器听不到，或者豁免形态被破坏，或者普查失明");
  process.exit(1);
}
if (skips.length) {
  console.log("STATUS_MESSAGES_SKIP 有站点没测成（画不出来／空跑／缺席）");
  process.exit(2);
}
console.log("STATUS_MESSAGES_OK 每一条状态消息都能被播报，刻意静音的两处也守住了豁免口径");