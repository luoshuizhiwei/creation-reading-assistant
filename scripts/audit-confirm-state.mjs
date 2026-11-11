#!/usr/bin/env node
/**
 * 两步确认态可见性普查（WCAG 1.4.1 Use of Color，AA · **动作确认维度**）。批次 BD-252 立。
 *
 * 一句话：删除是「第一击进确认态、第二击真删」的两步钮。如果确认态**只有颜色**在动，
 * 色觉丧失者与灰阶屏幕就无从知道「这一颗已经armed，再按一下东西就没了」——他看到的
 * 只是一颗普通垃圾桶，下一次点击是不可逆删除。SC 原文：「Color is not used as the only
 * visual means of conveying information, **indicating an action**, prompting a response,
 * or distinguishing a visual element」（https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html）
 * ——「indicating an action / prompting a response」管的正是这种「等你再确认一次」的状态。
 *
 * 与既有三本的分工（都量过、都不管这件事）：
 *   · scripts/audit-selected-state.mjs（1.4.1 **状态维度**，BD-249）量的是分段切换
 *     「现在选的是哪一档」：它的分母是同一把切换里的**并列档**，判据是「按下档与每一颗
 *     未按下档比」。两步删除钮根本没有并列档——同一颗钮的前后两个时刻，那本账取不到
 *     这个分母（它按 aria-pressed / .active 的兄弟组枚举），所以它全绿而这里全红。
 *   · scripts/audit-consistent-identification.mjs（3.2.4 图案一致性，BD-226）量的是
 *     「同一功能始终用同一张图」。本账正是**因为**要守住那条，才不许用「确认态换一张
 *     图标」当修法：删除族的美术签名 2f9d4f59（Trash2）在那本账里登记为单一图案。
 *   · scripts/audit-non-text-contrast.mjs（1.4.11）量「指认状态的那块视觉信息对比度够
 *     不够 3:1」，前提是存在这么一块**非颜色**信息；本账管的是存不存在。
 *   · BD-172 只补了读屏腿（aria-label 改成「再次点击确认删除…」）。读屏器听得到，
 *     眼睛看不见——1.4.1 管的是视觉通道，故本账**不把 aria 变化算成线索**（针 E 钉着）。
 *
 * 判据（任一不成立即红）：
 *   【①画得出才算测】把祖先链上的 opacity/display/visibility 乘出来，绘制不透明度
 *     <0.99 记 invisible ⇒ 跳过，不算绿。大纲行的图标簇 `.outline-node-actions`
 *     平时 opacity:0（只有行 hover / focus-within 才显形，styles.css:2355/2376）——
 *     摸底首轮把鼠标移开再截图，三站前后两图**逐字节相同**（mean=0 max=0），
 *     差点被读成「按下去一个像素都不动」的另一种缺陷。量具必须把行悬停住再拍。
 *   【②确认态要真进去】类名／aria／文字／像素至少一样变；一样都没变记 unproven ⇒
 *     跳过 + 整本 exit 2。少了这道，「第一击什么都没发生 ⇒ 当然没有只靠颜色的问题」
 *     会是一整片假绿。第一击就把条目删掉（不是两步钮）同样记 unproven。
 *   【③至少一条形状通道】形状通道为 0 而颜色通道 >0 ⇒ 红「确认态只靠颜色」。
 *   【④不许失明】一个站点都没测到 ⇒ 红。站点枚举为 0 记 absent ⇒ 跳过，不算合规。
 *
 * 「形状 vs 颜色」这条线照抄 BD-249 的口径（同一本 SC 的两支脚，尺子必须一样）：
 *   · 形状通道 = 可见**文字内容** / 图标美术签名（svg 子几何）/ 子节点数 /
 *     font-weight / font-size / text-decoration-line / letter-spacing / border-**width**
 *     与线型 / outline-**width** / border-radius / 伪元素的 content 与宽高 /
 *     display / visibility / opacity。
 *   · 颜色通道 = color / background-color / border-color / outline-color。
 *   · 真实元素的 width/height **不参与**：文字长老了盒子自然变宽，拿它当独立证据
 *     等于把同一件事数两遍（BD-249 已在分段切换上踩过这条）。文字本身参与——
 *     「删除」→「确认删除」是 Understanding 明确认可的答案。
 *   · 灰度像素差只作**证据**不作判据：它记录「灰阶屏幕上到底还剩多少差别」。
 *     实测两种红各在一端：大纲三站 mean≈15/max≈17（12% 红淡底，灰阶几乎看不出）；
 *     卡片类型/关系类型两站 mean≈124/强差占比 0.88（实色红填充，灰阶差很大——
 *     但差的仍然只有颜色这一件事，针 I 专门钉住「灰度差大也不许当合规」）。
 *
 * 为什么全程运行时：确认态是 React 状态 + 层叠样式 + 祖先 opacity 合力画出来的事实。
 * 静态读代码会把「写了 .confirming 就算有提示」「aria 变了就算有提示」一律看成已修。
 *
 * 清点口径：站点 6 个 = 图标钮 5（大纲树 卷/章/场景 删除 ＋ 卡片类型编辑器 删除类型 ＋
 * 关系类型编辑器 删除关系类型）＋ 带文字负对照 1（背景卡详情 删除，同一个 .confirming
 * 家族里唯一会变文字的那支）。结构由本账在一次性 profile 里现建（1 卷 + 1 章 + 1 场景、
 * 1 个自定义卡片类型、1 个自定义关系类型、1 张地点卡），绝不碰真实数据。
 * 卡片/关系类型编辑器里内置类型的删除钮恒 disabled、被引用的类型也 disabled，
 * 所以必须现建自定义类型才有可测的钮——这是分母能不能立起来的前提。
 *
 * 摸底（产品未改那轮，账 /tmp/bd252-color2.log；首轮坏尺子 /tmp/bd252-color.log）：
 * 站点 6 个｜实际测到 6 个｜只靠颜色 5 处｜有非色彩线索 1 处 ⇒ CONFIRM_STATE_RED/exit 1。
 *   ❌ 树·删除卷／删除章节／删除场景：色 rgb(102,109,119)/透明 → rgb(173,68,54)/12% 红淡底；
 *      形状通道 0 条；灰度 mean 15.52/15.52/16.04、max 17.4/17.4/17.9、强差占比 0。
 *   ❌ 卡片类型·删除类型／关系类型·删除关系类型：底色 rgb(248,249,250) → rgb(173,68,54)
 *      实色填充；形状通道 0 条；灰度 mean 124.28、max 151、强差占比 0.8775。
 *   ✅ 背景卡·删除（负对照）：文字「删除」→「确认删除」，形状通道 2 条（文字＋伪元素无），
 *      证明量具不是「凡确认态必红」。
 *
 * 修法（只改样式，不动 DOM、不动图标）：给这五颗图标钮的确认态加一道**下划线杠**
 * （::after，高 2px，绝对定位不占流），大纲族用 --proof-mark 画在纸面上、卡片族用
 * --fg-on-solid 画在红填充上（各自与自己的底 ≥4.5:1，沿用两族既有的前景/底配对）。
 * 为什么不换图标：3.2.4 那本账把删除族钉成单一图案 2f9d4f59。为什么不加边框粗细：
 * 大纲图标钮 border:0 且 box-sizing:border-box，加 2px 边会把 12px 图标挤到 8px 内容盒里；
 * 卡片钮的边框色与填充色同为 --proof-mark，加粗了也看不见。为什么不加 outline/box-shadow：
 * 那两样是 BD-243/241 焦点环账的判据面，常驻态占上去会让「聚焦前后差分」失去基线。
 *
 * 干净那轮实测（产品加上记号后重跑，账 /tmp/bd252-green2.log）：
 * 站点 6 个｜实际测到 6 个｜只靠颜色 0 处｜有非色彩线索 6 处 ⇒ CONFIRM_STATE_OK/exit 0。
 * 五处原红各多出一条「伪元素记号 ::after:none → ""/16px/2px/0px/底色」；负对照仍是文字那一条。
 * 记号**真落在屏上**另有像素取证（/tmp/bd252-bar.log，pngjs 解码后数控件底部 5 行）：
 * 大纲三站命中 --proof-mark 的像素 0/120 → 32/120（16×2 的杠恰好 32 像素），
 * 灰度 max 从 17.4 抬到 139.6、强差占比 0 → 0.0533；卡片两站底部命中 --fg-on-solid
 * 44/130（杠 32 + 圆角外沿），灰度 mean 117.68。即「算得出的记号」与「画得出的记号」都核过。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 900 xvfb-run -a --server-args="-screen 0 1600x1000x24" node scripts/audit-confirm-state.mjs
 *
 * 牙（运行时账 /tmp/bd252-teeth-runtime.log ＋ 修正期望后的复跑 fix.log/fix2.log；
 *     静态账 /tmp/bd252-teeth-static.log）：每档跑完立刻逐字节还原**并重建**，
 *     绝不让下一档踩在脏 out/ 上；R0／R9 都回到「6 站｜测到 6｜只靠颜色 0｜有线索 6」。
 *   · T1 两族的 ::after 全摘 ⇒ 五处只靠颜色，与红基线逐条同形（exit 1）。
 *   · T2 只摘大纲族 ⇒ 只咬树三处；T3 只摘卡片族 ⇒ 只咬类型编辑器两处（分站点有效）。
 *   · T4 杠高 2px→0 ⇒ 五处红；T5 杠底色→transparent ⇒ 五处红。这两档是「算得出 ≠
 *     看得见」的承重墙：pseudoMark() 若只认 content 存在，这两档都会假绿（见 T11）。
 *   · T6 判据反接（形状通道 >0 才判红）⇒ 六处全红（连负对照一起）。
 *   · T7 摘掉行悬停锚点 ⇒ 树三站记 invisible、exit 2（不是「只靠颜色」——量具看不见
 *     的时候不许替产品定罪，也不许算它合规）。
 *   · T8 空按（不点那颗钮）⇒ 六站 unproven、测到 0 ⇒ 先报失明红 exit 1、跳过 6 条。
 *   · T9 空按 ＋ 拆掉「确认态要真进去」对照 ⇒ 六处假红「只靠颜色」：证明那道对照
 *     是承重的，没它就会把「什么都没发生」记成产品缺陷。
 *   · T10 反向牙：摘杠 ＋ 把 aria 变化算进形状通道 ⇒ 六个站点全判绿，但**自证针 E
 *     当场拦下**（CONFIRM_STATE_NEEDLE_FAIL，exit 1）。即这条口径的守卫是针，不是站点。
 *   · T11 反向牙：杠高 0 ＋ pseudoMark 只认 content ⇒ 站点同样全绿，针 K/L 拦下（exit 1）。
 *   · 静态 S1…S20 逐条改坏口径/记号（命令登记、站点表、闸门、对照、通道表、失明判据、
 *     种子校验、针、页内读数、两族记号、图标、文字族），源码账必须逐档红，S0/S99 绿。
 *
 * 牙架自己踩的两件事（都是期望值写错，不是判据错；如实记档）：
 *   ① T8 的期望写成「exit 2 ／ 红 0」——实际空按时测到 0 个，判据④的失明红先响，
 *     exit 1／跳过 6。改成 exit 1 ＋ 必须出现「普查失明」字样后复跑通过。
 *   ② T10／T11 的期望写成 exit 0（以为反向牙会让整本绿）——实际站点确实全绿，
 *     但自证针把坏尺子拦在门口，exit 1。改成 exit 1 ＋ 必须出现 NEEDLE_FAIL 后复跑通过。
 *     教训：反向牙的期望要盯「谁拦下的」，不能只盯站点计数。
 */
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-confirm-state-"));
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
  await wizard.locator("input[placeholder='例如：测试项目']").first().fill("确认态普查项目");
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

// PromptDialog 提交后不自关（addVolume/addChapter/addScene 那三支），补一次 Esc 收尾。
// 另账记档，本批不混进来。
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

const seedOutline = async () => {
  await viewSite("大纲");
  await page.getByRole("button", { name: "大纲树", exact: true }).first().click();
  await page.waitForTimeout(900);
  await addNode(/新建卷/, "普查卷");
  await addNode(/在卷「普查卷」新建章节/, "普查章");
  await addNode(/在章节「普查章」新建场景/, "普查场景");
  const volumes = await page.locator(".outline-volume-title").allTextContents();
  const chapters = await page.locator(".outline-chapter-title").allTextContents();
  const scenes = await page.locator(".outline-scene-title").allTextContents();
  if (!volumes.includes("普查卷") || !chapters.includes("普查章") || !scenes.includes("普查场景")) {
    throw new Error(`大纲结构没建齐（卷=${JSON.stringify(volumes)}｜章=${JSON.stringify(chapters)}｜场景=${JSON.stringify(scenes)}）`);
  }
  console.log(`  大纲种子：卷 ${volumes.length}｜章 ${chapters.length}｜场景 ${scenes.length}`);
};

const seedTypes = async () => {
  await viewSite("设定卡");
  await page.waitForTimeout(1200);
  await page.getByRole("button", { name: /卡片类型/ }).first().click();
  await page.waitForTimeout(900);
  let dialog = page.locator('[role="dialog"]').first();
  await dialog.locator('input[aria-label="类型名称"]').first().fill("普查功法");
  await dialog.getByRole("button", { name: "创建类型" }).click();
  await page.waitForTimeout(1800);
  await closeOverlays();
  await page.getByRole("button", { name: /关系类型/ }).first().click();
  await page.waitForTimeout(900);
  dialog = page.locator('[role="dialog"]').first();
  await dialog.locator('input[aria-label="正向名称"]').first().fill("普查师徒");
  await dialog.locator('input[aria-label="反向名称"]').first().fill("普查师父");
  await dialog.getByRole("button", { name: "创建关系类型" }).click();
  await page.waitForTimeout(1800);
  await closeOverlays();
  // 种子校验按「删除钮可用」来验，不按列表文案：内置类型的钮恒 disabled，
  // 只有自定义类型那行才是本账的分母。
  await page.getByRole("button", { name: /关系类型/ }).first().click();
  await page.waitForTimeout(1000);
  const usable = await page.evaluate(() => Array.from(document.querySelectorAll(".cards-type-list-item button.cards-danger")).filter((b) => !b.disabled).length);
  await closeOverlays();
  await page.getByRole("button", { name: /卡片类型/ }).first().click();
  await page.waitForTimeout(1000);
  const usableCard = await page.evaluate(() => Array.from(document.querySelectorAll(".cards-type-list-item button.cards-danger")).filter((b) => !b.disabled).length);
  await closeOverlays();
  if (usable === 0 || usableCard === 0) throw new Error(`自定义类型没建出来（可用的卡片类型删除钮 ${usableCard} 颗、关系类型 ${usable} 颗）`);
  console.log(`  类型种子：可用的卡片类型删除钮 ${usableCard} 颗｜关系类型 ${usable} 颗`);
};

const seedBackgroundCard = async () => {
  await viewSite("设定卡");
  await page.waitForTimeout(1000);
  await page.getByRole("button", { name: /新建卡片/ }).first().click();
  await page.waitForTimeout(900);
  await page.locator('select[aria-label="类型"]').first().selectOption({ label: "地点" });
  await page.locator('input[aria-label="名称"]').first().fill("普查地点");
  await page.getByRole("button", { name: /^(创建卡片|保存修改)/ }).first().click();
  await page.waitForTimeout(1800);
};

/* ---------- 站点表（改这里等于改分母，静态账钉着条数与口径） ---------- */

const SITES = [
  {
    label: "树·删除卷",
    sel: '.outline-tree button[aria-label*="删除卷「普查卷」"]',
    hoverSel: '.outline-volume-row:has-text("普查卷") .outline-volume-title',
    enter: "tree"
  },
  {
    label: "树·删除章节",
    sel: '.outline-tree button[aria-label*="删除章节「普查章」"]',
    hoverSel: '.outline-chapter-row:has-text("普查章") .outline-chapter-title',
    enter: "tree"
  },
  {
    label: "树·删除场景",
    sel: '.outline-tree button[aria-label*="删除场景「普查场景」"]',
    hoverSel: '.outline-scene:has-text("普查场景") .outline-scene-title',
    enter: "tree"
  },
  {
    label: "卡片类型·删除类型",
    sel: null,
    rowText: "普查功法",
    enter: "cardTypes"
  },
  {
    label: "关系类型·删除关系类型",
    sel: null,
    rowText: "普查师徒",
    enter: "relationTypes"
  },
  {
    label: "背景卡·删除（负对照）",
    sel: ".background-detail-actions button",
    enter: "background"
  }
];

const openSite = async (kind) => {
  await closeOverlays();
  if (kind === "tree") {
    await viewSite("大纲");
    await page.getByRole("button", { name: "大纲树", exact: true }).first().click();
    await page.waitForTimeout(900);
    await page.waitForSelector(".outline-tree", { timeout: 10000 });
    return;
  }
  await viewSite("设定卡");
  await page.waitForTimeout(1000);
  if (kind === "cardTypes" || kind === "relationTypes") {
    await page.getByRole("button", { name: kind === "cardTypes" ? /卡片类型/ : /关系类型/ }).first().click();
    await page.waitForTimeout(1000);
    return;
  }
  await page.getByRole("button", { name: /背景设定/ }).first().click();
  await page.waitForTimeout(1200);
  const card = page.locator(".background-card").first();
  if (await card.count()) { await card.click(); await page.waitForTimeout(800); }
};

const locateSite = (site) => (site.sel
  ? page.locator(site.sel)
  : page.locator(".cards-type-list-item", { hasText: site.rowText }).locator("button.cards-danger"));

/* ---------- 页内快照：形状通道与颜色通道分开记 ---------- */

const snapshot = (loc) => loc.evaluate((el) => {
  const cs = getComputedStyle(el);
  // 画不画得出：祖先链上的 opacity/display/visibility 乘出来（判据①）。
  let painted = 1;
  for (let node = el; node && node !== document.documentElement; node = node.parentElement) {
    const s = getComputedStyle(node);
    painted *= Number(s.opacity);
    if (s.display === "none" || s.visibility === "hidden") painted = 0;
  }
  // 伪元素记号的原始读数（判定在 node 侧的 pseudoMark()，好让自证针喂同一把尺子）。
  const pseudos = ["::before", "::after"].map((p) => {
    const s = getComputedStyle(el, p);
    return { p, content: s.content, width: s.width, height: s.height, border: s.borderTopWidth, bg: s.backgroundColor, display: s.display, visibility: s.visibility };
  });
  const icons = Array.from(el.querySelectorAll("svg")).map((s) => Array.from(s.querySelectorAll("path, circle, rect, line, polyline, polygon"))
    .map((p) => p.getAttribute("d") || `${p.tagName}@${p.getAttribute("points") || ""}${p.getAttribute("x1") || ""}${p.getAttribute("cx") || ""}${p.getAttribute("x") || ""}`)
    .join(";")).join("|");
  return {
    painted: +painted.toFixed(3),
    text: (el.innerText || el.textContent || "").trim(),
    aria: el.getAttribute("aria-label") || "",
    cls: el.getAttribute("class") || "",
    icons,
    childCount: el.querySelectorAll("*").length,
    shape: {
      fontWeight: cs.fontWeight,
      fontSize: cs.fontSize,
      decoration: cs.textDecorationLine,
      letterSpacing: cs.letterSpacing,
      borderWidth: `${cs.borderTopWidth}/${cs.borderRightWidth}/${cs.borderBottomWidth}/${cs.borderLeftWidth}`,
      borderStyle: cs.borderTopStyle,
      outlineWidth: cs.outlineWidth,
      radius: cs.borderTopLeftRadius,
      display: cs.display,
      visibility: cs.visibility,
      opacity: cs.opacity,
      pseudos
    },
    color: {
      color: cs.color,
      background: cs.backgroundColor,
      borderColor: cs.borderTopColor,
      outlineColor: cs.outlineColor
    }
  };
});

/* ---------- 判据：形状/颜色通道（正式表与针共用同一个 channels()/levelOf()） ---------- */

// 伪元素记号算不算「画得出来」：有可见文字，或者有面积（宽高都 >0）且有底色/边框。
// 这一条是牙齿逼出来的：只记 content 存在的话，把杠的 height 改成 0、或把 background
// 改成 transparent，尺子照样报「有形状记号」，而屏幕上一像素都没有
// （同 BD-243 那条「算得出 ≠ 看得见」）。针 K/L 分别钉这两种破坏。
function pseudoMark(raw) {
  const px = (v) => Number.parseFloat(v) || 0;
  const hasText = raw.content !== "none" && raw.content !== "normal" && raw.content !== '""' && raw.content !== "''";
  const hasArea = px(raw.width) > 0 && px(raw.height) > 0;
  const hasPaint = (raw.bg !== "rgba(0, 0, 0, 0)" && raw.bg !== "transparent") || px(raw.border) > 0;
  const live = raw.display !== "none" && raw.visibility !== "hidden" && (hasText || (hasArea && hasPaint));
  return `${raw.p}:${live ? `${raw.content}/${raw.width}/${raw.height}/${raw.border}/${raw.bg}` : "none"}`;
}

const pseudoSignature = (list) => (list ?? []).map(pseudoMark).join("|");

function channels(before, after) {
  const shape = [];
  const color = [];
  if (before.text !== after.text) shape.push(`文字「${before.text}」→「${after.text}」`);
  if (before.icons !== after.icons) shape.push("图标美术签名变了");
  if (before.childCount !== after.childCount) shape.push(`子节点数 ${before.childCount}→${after.childCount}`);
  const B = before.shape ?? {};
  const A = after.shape ?? {};
  const pseudoB = pseudoSignature(B.pseudos);
  const pseudoA = pseudoSignature(A.pseudos);
  if (pseudoB !== pseudoA) shape.push(`伪元素记号 ${pseudoB}→${pseudoA}`);
  const NAMES = {
    fontWeight: "字重", fontSize: "字号", decoration: "下划线", letterSpacing: "字距",
    borderWidth: "边框粗细", borderStyle: "边框线型", outlineWidth: "外环粗细",
    radius: "圆角", display: "display", visibility: "visibility", opacity: "不透明度"
  };
  for (const key of Object.keys(NAMES)) {
    if (B[key] !== A[key]) shape.push(`${NAMES[key]} ${B[key]}→${A[key]}`);
  }
  const CB = before.color ?? {};
  const CA = after.color ?? {};
  for (const key of ["color", "background", "borderColor", "outlineColor"]) {
    if (CB[key] !== CA[key]) color.push(`${key} ${CB[key]}→${CA[key]}`);
  }
  return { shape, color };
}

// 确认态到底进去了没有（判据②）。aria 只用来证明「这一按有反应」，不当视觉线索。
function armed(before, after, pixelChanged) {
  return before.cls !== after.cls || before.aria !== after.aria || before.text !== after.text || pixelChanged;
}

function levelOf(before, after, pixelChanged) {
  if (before.painted < 0.99) return { level: "invisible", why: `控件没画出来（绘制不透明度 ${before.painted}）` };
  if (!armed(before, after, pixelChanged)) return { level: "unproven", why: "第一击后类名/aria/文字/像素一样都没变，确认态没进去" };
  const { shape, color } = channels(before, after);
  if (shape.length === 0) {
    return { level: "coloronly", why: `确认态只靠颜色（颜色通道 ${color.length} 条、形状通道 0 条）`, shape, color };
  }
  return { level: "ok", why: `非色彩线索 ${shape.length} 条：${shape.join("；")}`, shape, color };
}

/* ---------- 逐站测量 ---------- */

const measureSite = async (site) => {
  await openSite(site.enter);
  const loc = locateSite(site);
  if ((await loc.count()) === 0) return { site: site.label, level: "absent", why: "控件枚举为 0（站点缺席）" };
  const el = loc.first();
  await el.scrollIntoViewIfNeeded().catch(() => {});
  await page.waitForTimeout(400);
  // 让控件真画出来，但**别悬停在它自己身上**：hover 会改它自己的底色，混进前后对照。
  // 大纲行把鼠标停在行标题上 ⇒ 整簇 opacity:1，而这颗钮不是 :hover。
  const park = async () => {
    if (site.hoverSel) {
      const anchor = page.locator(site.hoverSel).first();
      if (await anchor.count()) await anchor.hover().catch(() => {});
    } else {
      await page.mouse.move(4, 4);
    }
    await page.evaluate(() => document.activeElement?.blur?.());
    await page.waitForTimeout(400);
  };
  await park();
  const before = await snapshot(el);
  const shotA = await el.screenshot();
  await el.click();
  await page.waitForTimeout(900);
  if ((await loc.count()) === 0) {
    return { site: site.label, level: "unproven", why: "第一击就把条目删掉了——这颗不是两步确认钮" };
  }
  const el2 = loc.first();
  await park();
  const after = await snapshot(el2);
  const shotB = await el2.screenshot();
  // 像素只作证据：本仓其余普查（audit-focus-rings）也按 PNG 字节比对，不解码。
  // 尺寸不同 ⇒ 必然有变化；尺寸相同时字节不同即为有变化。
  const pixelChanged = Buffer.compare(shotA, shotB) !== 0;
  const verdict = levelOf(before, after, pixelChanged);
  return {
    site: site.label,
    ...verdict,
    pixels: `${shotA.length}B→${shotB.length}B｜字节${pixelChanged ? "有变化" : "逐字节相同"}`,
    colorTrace: `${before.color.color}/${before.color.background} → ${after.color.color}/${after.color.background}`
  };
};

/* ---------- 判定表（正式表与针共用同一个 verdicts()） ---------- */

function verdicts(records, measured) {
  const rows = [];
  if (!measured) rows.push({ level: "red", text: "一个站点都没测到（测到 0 个）——普查失明，分母为 0 的「绿」不算通过" });
  for (const r of records) {
    if (r.level === "coloronly") rows.push({ level: "red", text: `${r.site}：${r.why}（1.4.1：再按一下就是不可逆删除，屏幕上却只有颜色在说这件事）` });
    else if (r.level === "ok") rows.push({ level: "info", text: `${r.site}：${r.why}` });
    else rows.push({ level: "skip", text: `${r.site}：${r.why}——不计绿也不计红` });
  }
  return rows;
}

/* ---------- 走查 ---------- */

console.log("两步确认态可见性普查（1.4.1 动作确认维度）——第一击进确认态，看屏幕上除了颜色还剩什么");
const records = [];
let measured = 0;
let seeded = false;
try {
  await openWorkbench();
  await seedOutline();
  await seedTypes();
  await seedBackgroundCard();
  seeded = true;
} catch (error) {
  console.log(`  ❌ 种子没建齐：${error.message.split("\n")[0]}`);
}
if (seeded) {
  for (const site of SITES) {
    try {
      const record = await measureSite(site);
      records.push(record);
      if (record.level === "ok" || record.level === "coloronly") measured += 1;
      console.log(`  ${record.level === "ok" ? "✅" : record.level === "coloronly" ? "❌" : "⚠️"} ${record.site}：${record.why}`);
      if (record.colorTrace) console.log(`      色：${record.colorTrace}｜像素：${record.pixels}`);
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
console.log(`\n普查站点 ${SITES.length} 个｜实际测到 ${measured} 个｜只靠颜色 ${records.filter((r) => r.level === "coloronly").length} 处｜有非色彩线索 ${records.filter((r) => r.level === "ok").length} 处`);
console.log(`红 ${reds.length} 条／ℹ️ ${infos.length} 条／跳过 ${skips.length} 条`);
for (const r of reds) console.log(`  ❌ ${r.text}`);
for (const r of infos) console.log(`  ℹ️ ${r.text}`);
for (const r of skips) console.log(`  ⚠️ ${r.text}`);

/* ---------- 自证针（喂进同一个 levelOf() + verdicts()，不另写判定条件） ---------- */

function runNeedles() {
  const deadPseudo = { p: "::after", content: "none", width: "auto", height: "auto", border: "0px", bg: "rgba(0, 0, 0, 0)", display: "inline", visibility: "visible" };
  const bar = (patch = {}) => ({ ...deadPseudo, content: '""', width: "16px", height: "2px", bg: "rgb(173, 68, 54)", display: "block", ...patch });
  const base = {
    painted: 1, text: "", aria: "删除卷「甲」", cls: "", icons: "M3 6h18", childCount: 1,
    shape: { fontWeight: "400", fontSize: "13px", decoration: "none", letterSpacing: "normal", borderWidth: "0px/0px/0px/0px", borderStyle: "solid", outlineWidth: "0px", radius: "4px", display: "inline-flex", visibility: "visible", opacity: "1", pseudos: [{ ...deadPseudo, p: "::before" }, { ...deadPseudo }] },
    color: { color: "rgb(102, 109, 119)", background: "rgba(0, 0, 0, 0)", borderColor: "rgb(102, 109, 119)", outlineColor: "rgb(102, 109, 119)" }
  };
  const clone = (patch = {}) => ({
    ...base,
    ...patch,
    shape: { ...base.shape, ...(patch.shape ?? {}) },
    color: { ...base.color, ...(patch.color ?? {}) }
  });
  const judge = (before, after, pixelChanged = true) => {
    const v = levelOf(before, after, pixelChanged);
    return verdicts([{ site: "针", ...v }], 1);
  };
  const has = (rows, level) => rows.some((r) => r.level === level);
  const results = [];

  // A 只有颜色变（大纲那三站的真实形状）⇒ 该红
  const a = judge(clone(), clone({ cls: "confirming", color: { color: "rgb(173, 68, 54)", background: "rgba(173, 68, 54, 0.12)" } }));
  results.push(["A 只换颜色该红", has(a, "red")]);
  // B 文字变（背景卡那支的真实形状）⇒ 不许红
  const b = judge(clone({ text: "删除" }), clone({ text: "确认删除", cls: "confirming", color: { color: "rgb(173, 68, 54)" } }));
  results.push(["B 文字变了不许误伤", !has(b, "red") && has(b, "info")]);
  // C 伪元素记号出现（本批的修法）⇒ 不许红
  const c = judge(clone(), clone({ cls: "confirming", shape: { pseudos: [{ ...deadPseudo, p: "::before" }, bar()] }, color: { color: "rgb(173, 68, 54)" } }));
  results.push(["C 伪元素杠出现不许误伤", !has(c, "red") && has(c, "info")]);
  // D 边框粗细 1→2（BD-249 用过的几何档）⇒ 不许红
  const d = judge(clone({ shape: { borderWidth: "1px/1px/1px/1px" } }), clone({ cls: "confirming", shape: { borderWidth: "2px/2px/2px/2px" } }));
  results.push(["D 边框加粗不许误伤", !has(d, "red") && has(d, "info")]);
  // E 只有 aria 变（BD-172 的读屏腿）⇒ 该红：耳朵听得到不算眼睛看得见
  const e = judge(clone(), clone({ aria: "再次点击确认删除卷「甲」", cls: "confirming", color: { color: "rgb(173, 68, 54)" } }), false);
  results.push(["E 只改 aria 该红", has(e, "red")]);
  // F 什么都没变 ⇒ 不许算绿（空跑）
  const f = judge(clone(), clone(), false);
  results.push(["F 空跑不许算绿", !has(f, "red") && !has(f, "info") && has(f, "skip")]);
  // G 画不出来（祖先 opacity:0）⇒ 不许算绿，也不许当成「只靠颜色」
  const g = judge(clone({ painted: 0 }), clone({ cls: "confirming", color: { color: "rgb(173, 68, 54)" } }));
  results.push(["G 画不出来不许算绿", !has(g, "info") && has(g, "skip")]);
  // H 一个站点都没测到 ⇒ 失明该红
  results.push(["H 测到 0 个站点该报失明", has(verdicts([], 0), "red")]);
  // I 灰度差很大但形状通道仍为 0（卡片类型那两站：实色红填充）⇒ 照样红
  const i = judge(clone(), clone({ cls: "confirming", color: { background: "rgb(173, 68, 54)" } }), true);
  results.push(["I 像素差大也不许当合规", has(i, "red")]);
  // J 全部有非色彩线索 ⇒ 全绿
  const j = verdicts(SITES.map((s) => ({ site: s.label, level: "ok", why: "有非色彩线索" })), SITES.length);
  results.push(["J 全有线索该全绿", !has(j, "red") && !has(j, "skip") && j.length === SITES.length]);
  // K 杠还在但高度 0（画不出的记号）⇒ 该红：算得出 ≠ 看得见
  const k = judge(clone(), clone({ cls: "confirming", shape: { pseudos: [{ ...deadPseudo, p: "::before" }, bar({ height: "0px" })] }, color: { color: "rgb(173, 68, 54)" } }));
  results.push(["K 零高度的杠该红", has(k, "red")]);
  // L 杠有面积但底色透明（同样画不出）⇒ 该红
  const l = judge(clone(), clone({ cls: "confirming", shape: { pseudos: [{ ...deadPseudo, p: "::before" }, bar({ bg: "rgba(0, 0, 0, 0)" })] }, color: { color: "rgb(173, 68, 54)" } }));
  results.push(["L 透明底色的杠该红", has(l, "red")]);
  return results;
}

const needleResults = runNeedles();
const needleOk = needleResults.every(([, ok]) => ok);
needleResults.forEach(([name, ok]) => console.log(`  针 ${name}：${ok ? "过" : "**没过**"}`));
console.log(`needleSelfTest.ran=${needleResults.length} passed=${needleOk}`);

if (!needleOk) {
  console.log("CONFIRM_STATE_NEEDLE_FAIL 自证针没过，本轮判定不可信");
  process.exit(1);
}
if (reds.length) {
  console.log("CONFIRM_STATE_RED 两步确认态只靠颜色，或者普查失明");
  process.exit(1);
}
if (skips.length) {
  console.log("CONFIRM_STATE_SKIP 有站点没测成（画不出来／空跑／缺席）");
  process.exit(2);
}
console.log("CONFIRM_STATE_OK 每一颗两步确认钮都有非色彩线索");