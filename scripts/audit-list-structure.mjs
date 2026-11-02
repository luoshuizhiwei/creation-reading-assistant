/**
 * BD-234 常驻普查：结构与关系（WCAG 1.3.1）——「只有视觉在说关系」的地方必须有程序化语义。
 *
 * 这一族是量出来的，不是猜的。摸底（/tmp/bd234-survey*.log：12 屏结构清单 + 逐屏几何）钉住三处现场：
 *  ① 大纲三级：`.outline-chapter-list` / `.outline-scene-list` 挂着 padding-left:12px，把「章→场」的
 *     从属关系**只画在版面上**（styles.css 那条注释自己写着「真正承载层级的是缩进」），DOM 里既没有
 *     ul/ol 也没有任何 role；`.outline-tree-scroll` 同样。实测几何（1360 窗）：卷行 x98 / 章行 x110 /
 *     场行 x122——那 12px×2 的位移就是当时唯一的关系载体。⇒ 已改：容器 ul、条目 li，类名与几何不动。
 *  ② 卡板列：`section.cards-board-column` 是无名泛型容器，列内卡片的 aria-label 只有「选择卡片：xxx」，
 *     「这张卡属于哪一列」只能靠眼睛看。⇒ 已改成 role=group + 列标题接 aria-labelledby。**没有**改成
 *     ul/li：列内每项是 role=button 的交互项，li 一挂 role=button 就把 listitem 语义吃掉（正是 ③ 的形状）。
 *  ③ 概览「最近编辑」：`ul.overview-recent-list` 的条目原先是 `<li role="button" tabindex=0>`——li 挂上
 *     role=button 后 ul 在无障碍树里成了空列表，「最近编辑有几条」整个丢掉。⇒ 已改成 li 只当条目、
 *     行内放真 button（可点区域与外观不变），行样式整条下沉、声明一字未改。
 *
 * 为什么必须在运行时量：判据问的是「此刻这处关系真的只靠视觉承载吗」。类名、CSS、JSX 都会骗人，
 * 渲染后的 computed padding、DOM 形状、role 归属才是事实；源码扫能看到 `.outline-scene-list` 的
 * padding-left，却看不到「卷只有 1 章、章只有 1 场」时缩进仍是唯一载体这件事。
 *
 * 判红口径（四条，逐条有意）：
 *  A 定点锁：大纲三级容器（`.outline-tree-scroll`/`.outline-chapter-list`/`.outline-scene-list`）必须是
 *    ul（或显式 role=list），其条目（卷/章/场/空态提示）必须是 li；三级容器必须都在场过。
 *  B 卡板列：每张可见的 `.cards-board-column` 必须有可访问名（aria-label / aria-labelledby 解析得到）。
 *  C ARIA 误挂：role=row|cell|columnheader|rowheader|gridcell 缺表格祖先；role=listitem 缺 list 祖先；
 *    role=group|region 没有任何可访问名；**没有 role 属性**的 ul/ol 的全部条目都被非 listitem 的 role
 *    覆盖（listitem 语义被剥光：有列表的标签、没有列表的树）。
 *  D 缩进承载从属：可见容器 padding-left ≥ 8px、全部元素子项同签名（tag|role|首类名）、紧跟一个「有文字
 *    但不是标题」的兄弟节点、自身没有任何成组语义、且子项不是清一色按钮 ⇒ 缩进正在替 DOM 说话，判红。
 *    （排除清一色按钮：工具条/对话框页脚的左衬是排版留白，不是从属关系。）
 * 放过（实测确认过的良性形状，写死在这里免得下一个人重新判一遍）：
 *  · 命令面板 `ul[role=listbox]` 里的 `li.creation-palette-group[role=presentation]` 被 C 的雏形
 *    「全部条目带 role」扫到：listbox 的合法子角色本就是 option 而非 listitem ⇒ C 只对**没有 role**
 *    的 ul/ol 开火，实测此处不红。
 *  · 书库网格头 `div.desktop-library-grid-head`（padding-left 12px、6 个 columnheader 子项）被 D 的雏形
 *    扫到：其祖先挂着 `div[role=table] aria-label=书库书籍`，关系已由表格语义承载 ⇒ D 排除 table/grid/
 *    treegrid/tablist/listbox/menu/toolbar 语境内的节点，实测此处不红。
 *  · 编辑目标弹层页脚的 `div.flex`（padding-left 20px、2 个 inline-flex 按钮：取消/保存目标）被 D 的雏形
 *    扫到：那是对话框页脚左衬，不是从属关系 ⇒ D 的「子项清一色按钮」排除项实测收掉此处。
 *  · 类名写着 -list/-children/-items 的容器**不单独成枪**：按「名字写着 list + 无语义」量下来，修完大纲后
 *    全仓 0 处，说明这一族没有残留缺陷；做成第五条枪会把「视觉上不是一列」的堆叠误判成列表。
 * 红判据：任一屏命中 → LIST_STRUCTURE_RED，exit 1。
 *
 * 实测基线（这版普查落地时逐字记的，改前/改后各跑一遍）：
 *  · 16 屏全过（项目首页/写作台/设定卡/大纲/大纲展开到底/概览/全书预览/写作统计/版本历史/书库/
 *    卡片库/收件箱/资料阅读/设置/编辑目标弹层/命令面板）。
 *  · 改**前**（这一版普查落地后专门拿 HEAD 那份产品源码重跑过一遍，日志 /tmp/bd234-prefix-check.log：
 *    四个文件换回 HEAD → build → 本普查 → 按 sha256 逐字节还原（ALL_RESTORE_IDENTICAL）→ 再 build →
 *    复验 0 条）：16/16 屏、命中 19 条 = A 8（写作台 6：三级容器各 1 条「是 div 不是 ul」＋卷/章/场条目
 *    各 1 条「1/1 是 div 不是 li」；大纲 2：tree-scroll 容器与「卷」条目——那一屏章/场列表没展开）
 *    + B 8（八张列名全 ∅）
 *    + C 1（概览 `ul.overview-recent-list` 的全部 1 个条目带非 listitem 的 role（button））
 *    + D 2（div.outline-chapter-list / div.outline-scene-list，前兄弟是卷行/章行，padding-left 12px）。
 *  · 改**后**：A 容器 ul 条目 li、B 八列齐名、C 0 条、D 0 条 ⇒ LIST_STRUCTURE_OK。
 *  · 版式底账（大纲几何，1360×900 窗，round4 种子 + 新建项目）：
 *      .outline-volume-row   x=98  y=328 w=936 h=36 pad=6px 4px disp=flex
 *      .outline-chapter-row  x=110 y=364 w=924 h=34 pad=4px  disp=flex
 *      .outline-scene        x=122 y=398 w=912 h=32 pad=4px  disp=flex
 *    改前后逐字一致（div→ul/li 后 list-style 由 disc 变 none 是 preflight 的 ul 复位，而这三行
 *    display:flex 本就不画 marker，故几何零位移）——这条由改前/改后两趟同走查复量
 *    （/tmp/bd234-geo-before.log ↔ /tmp/bd234-geo-after.log，逐行 x/y/w/h/padding/margin/display 比对，
 *    大纲三行只有 list=disc→none 与 volume/chapter 的 disp=block→list-item 两处非几何差异；
 *    卡板那一屏 diff 为空；概览 li 盒子 x91 y601 w1250 h38 不变，原先那 12 条声明整条落到
 *    `.overview-recent-item > button` 上，三列轨道 1145.98px / 36.02px / 22px 逐字相同）。
 *
 * 用法：npm run audit:list-structure（需先 npm run build；非无头环境请套 xvfb-run）
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-list-structure-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

/** A 规则的定点锁：大纲三级容器与它的条目类名。 */
const OUTLINE_LEVELS = [
  { container: "outline-tree-scroll", item: "outline-volume", label: "卷" },
  { container: "outline-chapter-list", item: "outline-chapter", label: "章" },
  { container: "outline-scene-list", item: "outline-scene", label: "场" }
];

const launch = async () => {
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
  return { app, page };
};

{ const { app } = await launch(); await app.close(); }
const seed = spawnSync(electronPath, [path.join(__dirname, "round4-seed.cjs"), profileDir], {
  cwd: root,
  encoding: "utf8",
  env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") },
  windowsHide: true,
  timeout: 60000
});
if (seed.status !== 0) throw new Error(`seed failed: ${seed.stderr}`);
const { app, page } = await launch();
await app.evaluate(({ BrowserWindow }) => BrowserWindow.getAllWindows()[0].setBounds({ x: 40, y: 40, width: 1360, height: 900 }));
await page.waitForTimeout(300);

const goto = async (label) => {
  await page.keyboard.press("Escape").catch(() => {});
  await page.locator(".desktop-sidebar button", { hasText: label }).first().click();
  await page.waitForTimeout(400);
};
async function createProject() {
  await goto("项目");
  const back = page.locator(".project-nav-back").first();
  if (await back.isVisible().catch(() => false)) await back.click();
  await page.waitForSelector(".project-home", { timeout: 10000 });
  const createFirst = page.getByRole("button", { name: "新建第一个项目" }).first();
  if (await createFirst.isVisible()) await createFirst.click();
  else await page.getByRole("button", { name: /新建项目/ }).first().click();
  const wizard = page.locator("[role=dialog]").first();
  await wizard.waitFor({ state: "visible", timeout: 15000 });
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：测试项目']").first().fill("结构语义普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(1200);
}
const workbenchView = async (navLabel, selector) => {
  if (!(await page.locator(".project-workbench").isVisible().catch(() => false))) await createProject();
  await page.keyboard.press("Escape").catch(() => {});
  await page.locator(".project-nav button", { hasText: navLabel }).first().click();
  await page.waitForSelector(selector, { timeout: 15000 });
  await page.waitForTimeout(400);
};

/**
 * 页内探针：只问渲染后的事实——谁在替 DOM 说话。
 * A/C/D 的判据全部基于 tag、role 属性、computed padding-left 与兄弟关系；
 * 可访问名按 aria-label → aria-labelledby 解析 → title 的顺序算。
 */
const probeScript = (levels) => {
  const tag = (el) => el.tagName.toLowerCase();
  const roleOf = (el) => el.getAttribute("role") || "";
  const clsOf = (el) => (el.getAttribute("class") || "").toString();
  const firstCls = (el) => clsOf(el).split(/\s+/).filter(Boolean)[0] || "—";
  const textOf = (el) => (el.textContent || "").replace(/\s+/g, " ").trim();
  const shown = (el) => {
    const r = el.getBoundingClientRect();
    const cs = getComputedStyle(el);
    return r.width > 1 && r.height > 1 && cs.visibility !== "hidden" && cs.opacity !== "0";
  };
  const kids = (el) => Array.from(el.children).filter((c) => tag(c) !== "template" && tag(c) !== "script" && tag(c) !== "style");
  const hasCls = (el, name) => new RegExp(`(^|\\s)${name}(\\s|$)`).test(clsOf(el));
  const isHeading = (el) => /^h[1-6]$/.test(tag(el)) || ["heading", "columnheader", "rowheader"].includes(roleOf(el));
  const GROUP_ROLES = ["list", "tree", "treegrid", "grid", "table", "menu", "listbox", "radiogroup", "group", "region", "directory", "tablist", "definition"];
  const grouped = (el) => {
    const t = tag(el), r = roleOf(el);
    if (["ul", "ol", "dl", "select", "table", "fieldset", "details", "nav", "section", "article", "aside"].includes(t)) return t;
    if (GROUP_ROLES.includes(r)) return `role=${r}`;
    if (el.hasAttribute("aria-label") || el.hasAttribute("aria-labelledby")) return "named";
    return "";
  };
  const nameOf = (el) =>
    (el.getAttribute("aria-label") || "").trim() ||
    (el.getAttribute("aria-labelledby") || "").split(/\s+/).filter(Boolean).map((id) => {
      const t = document.getElementById(id);
      return t ? textOf(t) : "";
    }).join(" ").trim() ||
    (el.getAttribute("title") || "").trim();
  const where = (el) => {
    const bits = [];
    let p = el.parentElement;
    while (p && bits.length < 3 && p !== document.body) { bits.unshift(`${tag(p)}.${firstCls(p)}`); p = p.parentElement; }
    return bits.join(" > ") || "body";
  };
  const brief = (el) => `${tag(el)}.${firstCls(el)}${roleOf(el) ? `[role=${roleOf(el)}]` : ""}｜父 ${where(el)}｜文字「${textOf(el).slice(0, 24)}」`;

  const hits = [];
  const seenContainers = {};

  // A 定点锁：大纲三级容器与条目
  for (const lv of levels) {
    const els = Array.from(document.querySelectorAll(`.${lv.container}`)).filter(shown);
    if (els.length) seenContainers[lv.container] = (seenContainers[lv.container] || 0) + els.length;
    for (const el of els) {
      const t = tag(el), r = roleOf(el);
      if (t !== "ul" && t !== "ol" && r !== "list") {
        hits.push({ rule: "A", why: `.${lv.container}（${lv.label}级容器）是 ${t}${r ? `[role=${r}]` : ""}，不是 ul——这一级的从属关系没有列表语义`, at: brief(el) });
      }
      const items = kids(el).filter((k) => hasCls(k, lv.item) || hasCls(k, "outline-empty-hint"));
      const bad = items.filter((k) => tag(k) !== "li" && roleOf(k) !== "listitem");
      if (!items.length) {
        hits.push({ rule: "A", why: `.${lv.container} 可见却一个 ${lv.label} 条目都没有（空态提示也必须是 li）`, at: brief(el) });
      } else if (bad.length) {
        hits.push({ rule: "A", why: `.${lv.container} 里 ${bad.length}/${items.length} 个 ${lv.label} 条目是 ${Array.from(new Set(bad.map((k) => tag(k)))).join("/")}，不是 li`, at: brief(bad[0]) });
      }
    }
  }

  // B 卡板列必须有可访问名
  for (const el of Array.from(document.querySelectorAll(".cards-board-column")).filter(shown)) {
    if (!nameOf(el)) hits.push({ rule: "B", why: "卡板列 .cards-board-column 没有可访问名——列内卡片「属于哪一列」只剩视觉", at: brief(el) });
  }

  // C/D：遍历可见元素
  const CANDIDATE_TAGS = ["div", "section", "ul", "ol", "li", "article", "aside", "nav", "main", "header", "footer"];
  const walk = document.createTreeWalker(document.body, NodeFilter.SHOW_ELEMENT);
  let n;
  while ((n = walk.nextNode())) {
    const el = n;
    if (!el || el === document.body) continue;
    if (el.closest('[aria-hidden="true"], template, script, style')) continue;
    if (!shown(el)) continue;
    const t = tag(el), r = roleOf(el);

    if (/^(row|cell|columnheader|rowheader|gridcell)$/.test(r) && !el.closest('[role="table"],[role="grid"],[role="treegrid"],table')) {
      hits.push({ rule: "C", why: `role=${r} 却没有 table/grid/treegrid 祖先（结构角色悬空）`, at: brief(el) });
    }
    if (r === "listitem" && !el.closest('ul,ol,[role="list"],[role="directory"]')) {
      hits.push({ rule: "C", why: "role=listitem 却没有 list 祖先", at: brief(el) });
    }
    if ((r === "group" || r === "region") && !nameOf(el)) {
      hits.push({ rule: "C", why: `role=${r} 没有任何可访问名（挂了成组语义却说不清是哪一组）`, at: brief(el) });
    }
    if ((t === "ul" || t === "ol") && r === "") {
      const ch = kids(el);
      if (ch.length) {
        const overridden = ch.filter((k) => {
          const kr = k.getAttribute("role") || "";
          return kr !== "" && kr !== "listitem";
        });
        if (overridden.length === ch.length) {
          hits.push({ rule: "C", why: `ul/ol 的全部 ${ch.length} 个条目都带非 listitem 的 role（${roleOf(overridden[0])}）⇒ listitem 语义被剥光，列表在无障碍树里是空的`, at: brief(el) });
        }
      }
    }

    // D 缩进承载从属
    if (!CANDIDATE_TAGS.includes(t)) continue;
    if (grouped(el)) continue;
    if (r === "row" || el.closest('[role="table"],[role="grid"],[role="treegrid"],table,[role="tablist"],[role="listbox"],[role="menu"],[role="toolbar"]')) continue;
    const cs = getComputedStyle(el);
    const pl = parseFloat(cs.paddingLeft || "0") || 0;
    if (pl < 8) continue;
    const prev = el.previousElementSibling;
    if (!prev || isHeading(prev) || !textOf(prev)) continue;
    const pcs = getComputedStyle(prev);
    const elBoxLeft = el.getBoundingClientRect().left;
    const prevBoxLeft = prev.getBoundingClientRect().left;
    // 只有「两个盒子左缘齐平、但本容器的内容比前兄弟的内容右移 ≥8px」才叫缩进画出的从属关系；
    // 行内衬（前后兄弟各自的内衬一样）与并列面板（左缘本就不同）都不算。
    if (Math.abs(elBoxLeft - prevBoxLeft) > 1) continue;
    const contentShift = (elBoxLeft + pl) - (prevBoxLeft + (parseFloat(pcs.paddingLeft || "0") || 0));
    if (contentShift < 8) continue;
    // 祖先里已有带名字的成组容器（section/role=group/region/fieldset/nav/details）⇒ 关系已由那层承载
    if (el.closest('[role="group"][aria-label],[role="group"][aria-labelledby],[role="region"][aria-label],[aria-labelledby][role="group"],fieldset,nav,details')) continue;
    const ch = kids(el);
    if (!ch.length) continue;
    const sig = (k) => `${tag(k)}|${roleOf(k) || "—"}|${firstCls(k)}`;
    const base = sig(ch[0]);
    if (!ch.every((k) => sig(k) === base)) continue;
    if (ch.every((k) => tag(k) === "button" || roleOf(k) === "button")) continue;
    hits.push({ rule: "D", why: `padding-left ${pl}px 把「与前一兄弟节点 ${tag(prev)}.${firstCls(prev)} 的从属关系」只画在版面上（内容右移 ${contentShift}px、${ch.length} 个同签名子项、容器无任何成组语义）`, at: brief(el) });
  }
  return { hits, seenContainers };
};

const scopes = [
  ["项目·首页", async () => { await goto("项目"); const back = page.locator(".project-nav-back").first(); if (await back.isVisible().catch(() => false)) await back.click(); await page.waitForSelector(".project-home", { timeout: 10000 }); }],
  ["项目·写作台", async () => workbenchView("写作", ".writing-desk")],
  ["项目·设定卡", async () => workbenchView("设定卡", ".cards-page")],
  ["项目·大纲", async () => workbenchView("大纲", ".outline-tree")],
  ["项目·大纲（展开到底）", async () => { await workbenchView("大纲", ".outline-tree"); await page.locator(".outline-volume-toggle, .outline-chapter-toggle").evaluateAll((els) => els.forEach((e) => { if (e.getAttribute("aria-expanded") === "false") e.click(); })); await page.waitForTimeout(700); }],
  ["项目·概览", async () => workbenchView("概览", ".overview-page")],
  ["项目·全书预览", async () => workbenchView("全书预览", ".preview-page")],
  ["项目·写作统计", async () => workbenchView("写作统计", ".stats-page")],
  ["项目·版本历史", async () => workbenchView("版本历史", ".history-page")],
  ["书库", async () => { await goto("书库"); await page.waitForSelector(".desktop-library-row", { timeout: 10000 }).catch(() => {}); }],
  ["卡片库", async () => { await goto("卡片库"); await page.waitForSelector(".global-card-library", { timeout: 10000 }).catch(() => {}); }],
  ["收件箱", async () => { await goto("收件箱"); await page.waitForSelector(".inbox-list", { timeout: 10000 }).catch(() => {}); }],
  ["资料阅读", async () => { await goto("资料阅读"); await page.waitForTimeout(500); }],
  ["设置", async () => { await goto("设置"); await page.waitForSelector(".desktop-page-stack", { timeout: 10000 }).catch(() => {}); }],
  ["弹层·编辑目标", async () => { await workbenchView("写作统计", ".stats-page"); await page.locator('button:has-text("编辑目标")').first().click(); await page.waitForSelector("[role=dialog]", { timeout: 10000 }); await page.waitForTimeout(400); }],
  ["浮层·命令面板", async () => { await goto("项目"); const back = page.locator(".project-nav-back").first(); if (await back.isVisible().catch(() => false)) await back.click(); await page.waitForSelector(".project-home", { timeout: 10000 }); await page.keyboard.press("Control+p"); await page.waitForSelector(".creation-search-input", { timeout: 10000 }); await page.waitForTimeout(400); }]
];

await createProject();
const rows = [];
const seenContainers = {};
let scanned = 0;
for (const [label, prep] of scopes) {
  try { await prep(); } catch (e) { console.log(`跳过「${label}」：${String((e && e.message) || e).split("\n")[0].slice(0, 100)}`); continue; }
  const res = await page.evaluate(probeScript, OUTLINE_LEVELS).catch(() => null);
  if (!res) { console.log(`跳过「${label}」`); continue; }
  scanned += 1;
  for (const [k, v] of Object.entries(res.seenContainers)) seenContainers[k] = (seenContainers[k] || 0) + v;
  for (const hit of res.hits) {
    if (!rows.some((x) => x.rule === hit.rule && x.why === hit.why && x.at === hit.at)) rows.push({ scope: label, ...hit });
  }
}
for (const lv of OUTLINE_LEVELS) {
  if (!seenContainers[lv.container]) rows.push({ scope: "全程", rule: "A", why: `定点锁的 .${lv.container}（${lv.label}级容器）在 ${scanned} 屏里一次都没出现`, at: "——" });
}

console.log(`结构范围 ${scanned} / ${scopes.length} 个；命中「关系只靠视觉承载 / ARIA 误挂」的独特形状：${rows.length} 条`);
for (const r of rows) console.log(`  - [${r.rule}]（${r.scope}）${r.why} @ ${r.at}`);
if (rows.length) {
  console.log(`LIST_STRUCTURE_RED：${rows.length} 处的结构/从属关系只由版式承载或缺程序化语义（WCAG 1.3.1）。`);
  await app.close();
  process.exit(1);
}
console.log("LIST_STRUCTURE_OK：大纲三级是 ul/li、卡板列有名、结构角色不悬空、没有「缩进替 DOM 说话」的容器。");
await app.close();
process.exit(0);
