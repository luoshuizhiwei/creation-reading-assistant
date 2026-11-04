/**
 * 改值不换上下文普查（WCAG 3.2.1 On Focus + 3.2.2 On Input，A 级）。批次 BD-246 立。
 *
 * 为什么要在运行时量：这两条 SC 判的是「动一下控件，页面会不会自己换地方」——那是事件
 * 与渲染之间的因果，纸面推不出来。同一段 onChange，可能只 setState，也可能顺手挂出一个
 * aria-modal 层；同一个「.focus()」，可能是按钮命令的应有之义，也可能是把键盘用户请出
 * 控件。更关键的是原生 <select> 在键盘下**每按一次方向键就 change 一次**：这是 Blink 的
 * 平台行为，静态读代码看不出「一次改值」实际上是「浏览选项时每按一次都来一发」。本仓既有
 * 那两本账都没有这一条：focus-rings 判「停靠点有没有可见环」，BD-245 的 keyboard-reach 判
 * 「走不走得到、退不退得出」——「改值会不会把人搬走」一本账都没记过。
 *
 * 判据（每颗改值控件都要过，任一不成立即红）：
 *   【聚焦不换（3.2.1）】只把焦点交给控件、一个键都不按：不许多出模态层、不许换主标题。
 *      权威：Understanding 3.2.1 把「获得焦点就弹出帮助层」列为失败例子
 *      （https://www.w3.org/WAI/WCAG21/Understanding/on-focus.html）。
 *   【改值不换（3.2.2）】真按键改一次值：不许多出模态层、不许把焦点搬出这颗控件、不许换
 *      主标题。弹层就是上下文变更——层一挂，Tab 环整个换掉，用户正在做的事被打断。
 *      例外一条，分族记账：焦点没被搬去某个具体控件、而是**跌回 body**（节点被换掉、或改值
 *      期间控件被 disabled={busy} 掉），那是 2.4.3「落点」族（BD-202／220／245 一路管的），
 *      本闸门逐条 ℹ️ 记名并计入总数，但不混进 3.2 的账判红——一站一本账，别串味。
 *   【换了就必须事先说明】确实换了上下文的控件，只有一条活路：动手之前就把后果说清楚。
 *      硬要求三条，缺一即红：①该控件挂 aria-describedby；②指向的节点**在屏上真渲染**
 *      （有盒子、非 display:none／visibility:hidden——只写 sr-only 不算，眼睛用得着的人
 *      同样需要事先看到后果）；③那句话里说出后果（含「预览／确认／弹出／打开」之一）。
 *      权威：F36 把「改了值就提交表单、且事先没有任何警告」列为 3.2.2 失败，并指出键盘
 *      用户因此「无法越过菜单里的第一项」
 *      （https://www.w3.org/TR/WCAG20-TECHS/F36.html）；MDN 认知可达性指南要求
 *      「如果表单控件更改了上下文，请描述将会发生的情况，以便用户在引起上下文更改之前能够
 *      理解」（https://developer.mozilla.org/zh-CN/docs/Web/Accessibility/Guides/Cognitive_accessibility）；
 *      香港数字政策办公室 8.26 给的另一种还法是补一颗显式的「去」钮
 *      （https://www.digitalpolicy.gov.hk/tc/our_work/digital_government/digital_inclusion/accessibility/promulgating_resources/handbook/wcag2a/8_26_on_input.html）。
 *
 * 清点口径（实测出来的取舍，不是纸面偏好）：
 *   · 模态层打开时**只在层内清点**（与 BD-245 同一口径），否则会把层外控件误当本站候选。
 *   · 候选 = 可见、启用、不在 aria-hidden 子树里的 select / input[type=checkbox] /
 *     input[type=radio]。select 必须还有 ≥2 个「未 disabled」的选项，否则无从改值。
 *   · 改值一律用**真键盘**（page.keyboard.press）：合成 KeyboardEvent 不触发原生默认行为，
 *     拿 dispatchEvent 测 select 会得到「按了没反应」——那是探针错，不是产品错（BD-245 记档）。
 *   · 每颗测完必须还原，否则下一颗测的是被污染的状态：Escape 关层 → 反向键/回点原选中项
 *     把值退回 → blur → 等 DOM 安静。BD-246 初版探针没做这一步，弹层背景
 *     （backdrop 拦 pointer 事件）把后面整站的点击全拖死（/tmp/bd246-probe6.log 崩在这儿）。
 *   · 清点项在改值后从 DOM 消失 ⇒ 单独打 ⚠️ 并以 SKIP 退出，不许静默少测。
 *
 * 本闸门自己的实测账（/tmp/bd246-gate8.log，干净树上：站点 12 个／改值控件 11 颗／真改值
 * 11 次，其中 1 次换了上下文且已事先说明，另 ℹ️ 记名 1 条）：
 *   ① 唯一一处换上下文＝大纲树「章节编号模式」：一改值多出模态层 0→1、焦点从
 *      select.outline-numbering-select 搬到弹层「关闭」钮（第一次按方向键就被接走，后面的
 *      「番外／自定义」再也浏览不了）。弹层是产品的安全确认（不确认不写入），不能拆；
 *      按 F36 的另一条出路补「事先说明」：OutlineTree.tsx 工具条里常驻可见一行 + 三颗会弹层
 *      的 select（移卷／移章／编号）挂 aria-describedby。行内塞显式「应用」钮那条走不通：
 *      实拍章节行右侧只剩 4px 余量（/tmp/bd246-probe9.log）。CardBoard.tsx 同理一处。
 *   ② 写作页「块类型」下拉框原本是红：焦点从 select.scene-block-select 跳到
 *      div.tiptap.ProseMirror。→ SceneEditor.tsx 改成「只有按下这一刻正文本来有焦点」才把
 *      焦点交还正文：正打字的人选完接着打，纯键盘走到下拉框的人焦点留在原地。键盘分支由
 *      本闸门逐颗改值时验（红已消）；鼠标分支 jsdom 测不了（ProseMirror 在 vitest 里拿不到
 *      DOM 焦点，断言怎么写都不会失败＝假绿），改由 blockTypeMouseFlow() 在真 Blink 里测，
 *      实测输出「正文里有焦点时改值，焦点交还回 div.tiptap」。
 *   ③ ℹ️ 另族：卡片板「章节状态」select 改值期间被 disabled={busy} 换掉，焦点跌回 body、
 *      控件仍在（2.4.3 落点族）——本批只记名，已排进下批线索。
 *   3.2.1 全绿：先前探针（/tmp/bd246-probe5~8.log）9 屏 354 个停靠点，聚焦本身不触发任何
 *   上下文变更。
 *   扫过但判为干净的形状（记在这里免得下批重查）：就地更新内容区的下拉框——书库/卡片库
 *   「按类型筛选」、全书预览「跳转到章节」、设置「应用主题」——既不弹层也不搬焦点也不换标题，
 *   更新落在同一屏里具名的区域，本批不视为 3.2.x 的「换上下文」（实测全 ✔，/tmp/bd246-probe7.log）。
 *   卡片板「批量状态」那颗本来就有显式「应用」钮（CardBoard.tsx:331），是 F36/DPO 认可的合规
 *   写法，不动，也故意不挂 aria-describedby（留作对照项）。
 *
 * 普查自己也会撒谎——三处假信号，每处都配了一条账本断言（context-change-family-source.test.ts）：
 *   · 假绿：站点循环漏写 `await site.open()`，12 站全在启动屏空跑照样打 OK（首跑实录）；
 *   · 假 SKIP：受控 select 改值后会被 React 立刻重置，事后读 selectedIndex 判「改没改值」会把
 *     刚修好的编号框误判成「按键没能改值」；只许数 change 事件；
 *   · 假过：切屏／切页签的验证写成「点了就算」（或把 RingButton 的 aria-current 当成 Tabs 的
 *     aria-pressed）就永远通过。
 * 另有两条口径：重认脸用 (tag, 前两个 class) + 盒子中心 24px，不用 aria-label（改值会让文案
 * 跟着变，按名字认会得到 0 命中、静默少测）；写作站内嵌的那份大纲树／卡板整棵排除，一站只测
 * 本站自己的控件（否则同一棵树被两站各测一遍，第一站改完值把整棵换掉，第二站只剩静默跳过）。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 900 xvfb-run -a --server-args="-screen 0 1600x1000x24" node scripts/audit-context-change.mjs
 * 退出码：有红 → CONTEXT_CHANGE_RED / exit 1；有站点或控件被跳过 → CONTEXT_CHANGE_SKIP / exit 2。
 */
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-ctxchange-"));
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
await page.waitForTimeout(900);

/**
 * 切侧栏屏，并且**验明正身**（点了不等于切过去了）。这条是本批自己栽的一跤换来的：
 * 首版站点循环漏写 `await site.open()`，12 站全在启动屏「创作项目」上空跑，改值控件
 * 0 颗却照样打 CONTEXT_CHANGE_OK——普查自己也会假绿（调试见 /tmp/ctxdbg.log）。
 * 现在每站先验 h1、点不动就重试，三次还不成直接抛：站点记成 SKIP 并以 exit 2 退出，
 * 空跑从此必须出声。
 */
const gotoSidebar = async (label) => {
  for (let attempt = 0; attempt < 3; attempt += 1) {
    await page.locator(".desktop-sidebar button", { hasText: label }).first().click();
    await page.waitForTimeout(500);
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
  const workbench = page.locator(".project-workbench");
  if (await workbench.isVisible().catch(() => false)) return;
  const createFirst = page.getByRole("button", { name: "新建第一个项目" }).first();
  if (await createFirst.isVisible().catch(() => false)) await createFirst.click();
  else await page.getByRole("button", { name: /新建项目/ }).first().click();
  const wizard = page.locator("[role=dialog]").first();
  await wizard.waitFor({ state: "visible", timeout: 15000 });
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await wizard.locator("input[placeholder='例如：测试项目']").first().fill("改值换上下文普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(1500);
};

const viewSite = async (label) => {
  await openWorkbench();
  for (let attempt = 0; attempt < 3; attempt += 1) {
    await page.locator(".project-nav button", { hasText: label }).first().click();
    await page.waitForTimeout(500);
    // 这套视图页签不是 Tabs 而是 RingButton：选中态写的是 aria-current="page" + .active
    // （CreationProjectsPage.tsx:576-580），按「有没有真的按下」验明正身（同 gotoSidebar）
    const on = await page.evaluate((l) => {
      const b = document.querySelector('.project-nav button[aria-current="page"]');
      return !!b && `${b.getAttribute("aria-label") || ""}${b.textContent || ""}`.includes(l);
    }, label);
    if (on) return;
    await page.waitForTimeout(700);
  }
  throw new Error(`点页签「${label}」没能切到该视图`);
};

/**
 * 切「大纲树／场景卡板」这类组内页签，同样验明正身：要在那一组里真有一枚
 * aria-pressed="true" 的钮，其文字就是目标页签。写成「点了就算」的判据会自己造假绿。
 */
const clickGroupTab = async (groupLabel, label) => {
  for (let attempt = 0; attempt < 3; attempt += 1) {
    await page.locator(`[role="group"][aria-label="${groupLabel}"] button`, { hasText: label }).first().click();
    await page.waitForTimeout(500);
    const on = await page.evaluate(({ g, l }) => {
      const host = document.querySelector(`[role="group"][aria-label="${g}"]`);
      if (!host) return false;
      return [...host.querySelectorAll('button[aria-pressed="true"]')].some((b) => b.textContent.trim().includes(l));
    }, { g: groupLabel, l: label });
    if (on) return;
    await page.waitForTimeout(700);
  }
  throw new Error(`点「${groupLabel}·${label}」没能切过去`);
};

/** 等 DOM 安静：别在重渲染中途比快照（判据会跟着抖）。 */
const settle = () =>
  page.evaluate(() =>
    new Promise((resolve) => {
      let timer = setTimeout(done, 1200);
      function done() {
        observer.disconnect();
        clearTimeout(timer);
        resolve();
      }
      const observer = new MutationObserver(() => {
        clearTimeout(timer);
        timer = setTimeout(done, 200);
      });
      observer.observe(document.body, { subtree: true, childList: true, attributes: true, characterData: true });
    })
  );

/** 一页一清：给每颗改值控件打标，返回清单（页面里的函数全部自包含）。 */
const enumerate = (exclude) =>
  page.evaluate((skipSel) => {
    const modal = [...document.querySelectorAll('[role="dialog"][aria-modal="true"]')].pop();
    const scope = modal || document;
    const rows = [];
    let i = 0;
    for (const el of scope.querySelectorAll("select,input[type=checkbox],input[type=radio]")) {
      if (el.disabled || el.closest('[aria-hidden="true"]')) continue;
      // 一站只测这站自己的控件：写作页右侧那份大纲树／卡板与大纲视图是同一棵树、同一份形状，
      // 在大纲那站已经逐颗测过；这里再测一遍会把同一棵树改值两次，第一站改完值把整棵换掉，
      // 第二站就只剩「重认脸找不到同一颗」的静默跳过（BD-246 gate7 实测）。
      if (skipSel && el.closest(skipSel)) continue;
      const cs = getComputedStyle(el);
      if (cs.display === "none" || cs.visibility === "hidden") continue;
      const r = el.getBoundingClientRect();
      if (r.width === 0 && r.height === 0) continue;
      const tag = el.tagName.toLowerCase();
      if (tag === "select" && [...el.options].filter((o) => !o.disabled).length < 2) continue;
      el.setAttribute("data-cc", String(i));
      rows.push({
        i,
        tag,
        type: tag === "input" ? el.type : "select",
        cls: (el.getAttribute("class") || "").split(/\s+/).filter(Boolean).slice(0, 2).join("."),
        name: (el.getAttribute("aria-label") || el.name || el.id || "").trim().slice(0, 30),
        cx: Math.round(r.left + r.width / 2),
        cy: Math.round(r.top + r.height / 2)
      });
      i += 1;
    }
    return { rows, modal: !!modal };
  }, exclude);

/**
 * 上膛：给这颗控件打标记、装 change 计数器，然后把焦点交给它。
 * 「改没改值」必须问 change 事件本身，不能事后读 selectedIndex：受控 select 在没有写入时
 * 会被 React 立刻重置回原值，读 selectedIndex 会报「没改过」——BD-246 首跑就是这样把刚修好的
 * 编号下拉框误判成「按键没能改值」而静默跳过的（/tmp/bd246-gate3.log）。
 */
const arm = (cc) =>
  page.evaluate((id) => {
    const el = document.querySelector(`[data-cc="${id}"]`);
    if (!el) return false;
    window.__cc = window.__cc || {};
    window.__cc[id] = 0;
    el.addEventListener("change", () => { window.__cc[id] += 1; });
    el.focus();
    return true;
  }, cc);

const changeCount = (cc) =>
  page.evaluate((id) => (window.__cc && window.__cc[id]) || 0, cc);

/**
 * 重新认脸：上一颗改完值把整块列表换掉了，得在新 DOM 里找回**同一颗**。
 * 脸 = (tag, 前两个 class)，位置 = 清点时记下的盒子中心，误差 24px 内算同一颗。
 * 不用 aria-label 当脸：改值会让文案跟着变（实测：卡片板「第1章 第一章」这种带编号的
 * aria-label 在重渲染前后就不一致了，按名字认会得到 0 命中、普查静默少测）。
 * 命中 0 张或几张照片都落在 24px 内 ⇒ 明说认不出，绝不猜。
 */
const reArm = (row) =>
  page.evaluate(({ r, r0 }) => {
    const modal = [...document.querySelectorAll('[role="dialog"][aria-modal="true"]')].pop();
    const scope = modal || document;
    const near = [...scope.querySelectorAll("select,input[type=checkbox],input[type=radio]")]
      .filter((el) => {
        if (el.disabled) return false;
        const tag = el.tagName.toLowerCase();
        const cls = (el.getAttribute("class") || "").split(/\s+/).filter(Boolean).slice(0, 2).join(".");
        if (tag !== r.tag || cls !== r.cls) return false;
        const box = el.getBoundingClientRect();
        const d = Math.hypot(box.left + box.width / 2 - r0.cx, box.top + box.height / 2 - r0.cy);
        return d <= 24;
      });
    if (near.length === 0) return "该位置没有同一张脸";
    if (near.length > 1) return `${near.length} 张脸都落在 24px 内`;
    near[0].setAttribute("data-cc", String(r.i));
    window.__cc = window.__cc || {};
    window.__cc[r.i] = 0;
    near[0].addEventListener("change", () => { window.__cc[r.i] += 1; });
    near[0].focus();
    return true;
  }, { r: row, r0: { cx: row.cx, cy: row.cy } });

/** 快照：模态层数 / 主标题 / 焦点落点 / DOM 规模（后者只作记录，不当判据，见头注）。 */
const snap = () =>
  page.evaluate(() => {
    const a = document.activeElement;
    const cc = a && a.getAttribute ? a.getAttribute("data-cc") : null;
    const heading = [...document.querySelectorAll("h1")].map((n) => n.textContent.trim()).filter(Boolean)[0] || "";
    return {
      modals: document.querySelectorAll('[role="dialog"][aria-modal="true"]').length,
      heading,
      atCc: cc === null || cc === undefined ? null : String(cc),
      at: a && a !== document.body ? `${a.tagName.toLowerCase()}${(a.getAttribute("class") || "").split(/\s+/)[0] ? "." + (a.getAttribute("class") || "").split(/\s+/)[0] : ""}「${((a.getAttribute("aria-label") || a.textContent || a.value || "").trim()).slice(0, 24)}」` : "body",
      nodes: document.getElementsByTagName("*").length
    };
  });

/** 「事先说明」豁免检查：aria-describedby 必须指向在屏上真渲染、且说出后果的节点。 */
const advised = (index) =>
  page.evaluate((cc) => {
    const el = document.querySelector(`[data-cc="${cc}"]`);
    if (!el) return { found: false };
    const ids = (el.getAttribute("aria-describedby") || "").split(/\s+/).filter(Boolean);
    if (ids.length === 0) return { found: false, why: "没挂 aria-describedby" };
    const hits = [];
    for (const id of ids) {
      const node = document.getElementById(id);
      if (!node) { hits.push({ text: "", visible: false, why: `指向的 #${id} 不存在` }); continue; }
      const cs = getComputedStyle(node);
      const r = node.getBoundingClientRect();
      const text = node.textContent.trim();
      hits.push({
        text: text.slice(0, 40),
        visible: cs.display !== "none" && cs.visibility !== "hidden" && r.width > 0 && r.height > 0,
        says: /预览|确认|弹出|打开/.test(text)
      });
    }
    const ok = hits.some((h) => h.visible && h.text.length >= 6 && h.says);
    return { found: true, ok, hits };
  }, index);

const cleanUp = async (row) => {
  await page.keyboard.press("Escape").catch(() => {});
  await page.waitForTimeout(180);
  // 把值退回：select 用反向键（退不回就认了——它是受控值，取消弹层后本来就会退回），
  // checkbox 再按一次 Space，radio 回点原来那颗。
  if (row.tag === "input" && row.type === "checkbox") {
    const still = await page.evaluate((cc) => {
      const el = document.querySelector(`[data-cc="${cc}"]`);
      if (!el) return false;
      el.focus();
      return true;
    }, row.i);
    if (still) await page.keyboard.press("Space").catch(() => {});
  } else if (row.tag === "input" && row.type === "radio") {
    await page.evaluate((r) => {
      const cur = document.querySelector(`[data-cc="${r.i}"]`);
      if (!cur) return;
      const group = cur.form ? [...cur.form.elements].filter((n) => n.type === "radio" && n.name === cur.name) : [...document.querySelectorAll(`input[type=radio][name="${CSS.escape(cur.name)}"]`)];
      const origin = group.find((n) => n.checked);
      if (origin) origin.click();
      else if (cur.checked) { cur.checked = false; }
    }, row);
  } else if (row.tag === "select") {
    await page.evaluate((cc) => {
      const el = document.querySelector(`[data-cc="${cc}"]`);
      if (!el) return;
      el.focus();
      if (el.selectedIndex !== Number(el.getAttribute("data-cc-idx"))) el.selectedIndex = Number(el.getAttribute("data-cc-idx"));
    }, row.i);
  }
  await page.keyboard.press("Escape").catch(() => {});
  await page.evaluate(() => document.activeElement?.blur?.());
  await settle();
};

const sweepSite = async (exclude) => {
  await settle();
  await page.evaluate(() => document.activeElement?.blur?.());
  const listed = await enumerate(exclude);
  const reds = [];
  const skips = [];
  const notes = [];
  let changed = 0;
  let advisedOk = 0;
  for (const row of listed.rows) {
    if (row.tag === "select") {
      await page.evaluate((r) => {
        const el = document.querySelector(`[data-cc="${r.i}"]`);
        if (el) el.setAttribute("data-cc-idx", String(el.selectedIndex));
      }, row);
    }
    const s0 = await snap();
    const got = await arm(row.i);
    if (!got) {
      // 上一颗改完值把这一棵列表整块换掉了（React 换了节点）——按 (tag, cls, name) 重新认脸再来一次
      const again = await reArm(row);
      if (again !== true) {
        skips.push(`<${row.tag}> .${row.cls}「${row.name}」（清点后节点没了，重认脸没过：${typeof again === "string" ? again : "没命中"}）`);
        continue;
      }
    }
    await page.waitForTimeout(120);
    const s1 = await snap();
    // 3.2.1：只给焦点、一个键都不按
    if (s1.modals > s0.modals) reds.push(`<${row.tag}> .${row.cls}「${row.name}」→ 只给它焦点就多出模态层（${s0.modals}→${s1.modals}），3.2.1 字面失败`);
    else if (s1.heading && s0.heading && s1.heading !== s0.heading) reds.push(`<${row.tag}> .${row.cls}「${row.name}」→ 只给它焦点就换了主标题「${s0.heading}」→「${s1.heading}」`);

    // 3.2.2：真按键改值
    const key = row.tag === "select" ? "ArrowDown" : "Space";
    await page.keyboard.press(key);
    await page.waitForTimeout(180);
    let s2 = await snap();
    let fired = await changeCount(row.i);
    if (!fired && row.tag === "select") {
      await page.keyboard.press("ArrowUp");
      await page.waitForTimeout(180);
      s2 = await snap();
      fired = await changeCount(row.i);
    }
    if (!fired) {
      skips.push(`<${row.tag}> .${row.cls}「${row.name}」（按键没能触发 change，无从测 3.2.2）`);
      await cleanUp(row);
      continue;
    }
    changed += 1;

    const why = [];
    if (s2.modals > s1.modals) why.push(`多出模态层 ${s1.modals}→${s2.modals}`);
    if (s2.atCc !== String(row.i)) {
      if (s2.at === "body") {
        // 焦点没被搬到某个具体控件，而是**掉在地上没人接**（节点被换掉、或像卡板「章节状态」
        // 那样改值期间把控件 disabled={busy} 掉）：那是 2.4.3 落点族（BD-202／BD-220／BD-245
        // 一路管的就是「操作完成了、人却丢了位置」），不是 3.2.x 的「改值主动换上下文」。
        // 本批不判红，但必须逐条打出来并计入总数——不许静默吃掉，也不许顺手混进 3.2 的账。
        notes.push(`<${row.tag}> .${row.cls}「${row.name}」→ 改值后焦点跌回 body（2.4.3 落点族，另批处理；改值前焦点 ${s1.at}，控件仍在=${await page.evaluate((cc) => { const el = document.querySelector(`[data-cc="${cc}"]`); return !!el && el.isConnected; }, row.i) ? "是" : "否"}）`);
      } else {
        why.push(`焦点被搬走 ${s1.at} → ${s2.at}`);
      }
    }
    if (s2.heading && s1.heading && s2.heading !== s1.heading) why.push(`换主标题「${s1.heading}」→「${s2.heading}」`);
    if (why.length > 0) {
      const adv = await advised(row.i);
      if (adv.found && adv.ok) {
        advisedOk += 1;
        console.log(`  · <${row.tag}> .${row.cls}「${row.name}」改了值换上下文（${why.join("、")}）——已事先说明：「${adv.hits.find((h) => h.visible && h.says)?.text ?? ""}」`);
      } else {
        const detail = adv.found ? (adv.hits.map((h) => (h.why || `可见=${h.visible} 说后果=${h.says} 「${h.text}」`)).join("；")) : adv.why;
        reds.push(`<${row.tag}> .${row.cls}「${row.name}」→ 一改值就换上下文（${why.join("、")}），且没有事先说明：${detail}`);
      }
    }
    await cleanUp(row);
  }
  await page.evaluate(() => document.querySelectorAll("[data-cc],[data-cc-idx]").forEach((n) => { n.removeAttribute("data-cc"); n.removeAttribute("data-cc-idx"); }));
  return { total: listed.rows.length, modal: listed.modal, changed, advisedOk, reds, skips, notes };
};

/**
 * 「块类型」下拉框的**鼠标分支**（缺陷②的另一半）：正文里正打字时选块类型，焦点必须交还给
 * 正文——这条只能真浏览器测。jsdom 里 ProseMirror 拿不到 DOM 焦点（实测 handle.focus() 之后
 * activeElement 仍是 body），断言怎么写都不会失败，那是假绿。
 * mousedown 用合成事件打：React 的 onMouseDown 挂在根容器上，原生 mousedown 冒泡就听得到，
 * 这样走的就是「按下那一刻读 editor.isFocused」那条真分支，而不必真去展开原生选框。
 */
const blockTypeMouseFlow = async () => {
  const select = page.locator(".scene-block-select").first();
  if (!(await select.isVisible().catch(() => false))) return { skip: "这一屏没有块类型下拉框" };
  await page.locator(".tiptap.ProseMirror").first().click();
  await page.waitForTimeout(250);
  const inPaper = await page.evaluate(() => {
    const el = document.querySelector(".tiptap.ProseMirror");
    return !!el && (document.activeElement === el || el.contains(document.activeElement));
  });
  if (!inPaper) return { skip: "点正文没能把焦点交给 ProseMirror，这条无从测" };
  const moved = await page.evaluate(() => {
    const sel = document.querySelector(".scene-block-select");
    if (!sel) return null;
    const at = sel.selectedIndex === sel.options.length - 1 ? 0 : sel.selectedIndex + 1;
    sel.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
    sel.selectedIndex = at;
    sel.dispatchEvent(new Event("change", { bubbles: true }));
    return true;
  });
  if (!moved) return { skip: "改值时下拉框已经不在了" };
  await page.waitForTimeout(300);
  const after = await page.evaluate(() => {
    const a = document.activeElement;
    const paper = document.querySelector(".tiptap.ProseMirror");
    const sel = document.querySelector(".scene-block-select");
    return {
      inPaper: !!paper && (a === paper || paper.contains(a)),
      onSelect: a === sel,
      at: a && a !== document.body ? `${a.tagName.toLowerCase()}.${(a.getAttribute("class") || "").split(/\s+/)[0]}` : "body"
    };
  });
  return { inPaper: after.inPaper, onSelect: after.onSelect, at: after.at };
};

const SITES = [
  { label: "书库", open: () => gotoSidebar("书库") },
  { label: "卡片库", open: () => gotoSidebar("卡片库") },
  { label: "收件箱", open: () => gotoSidebar("收件箱") },
  { label: "设置", open: () => gotoSidebar("设置") },
  { label: "项目首页", open: async () => { await openWorkbench(); const b = page.locator(".project-nav-back").first(); if (await b.isVisible().catch(() => false)) await b.click(); await page.waitForTimeout(500); } },
  // 写作站把内嵌的大纲树／卡板排除在外（同一棵树在大纲两站已逐颗测过），只测写作台自己的控件
  { label: "工作台·写作", open: () => viewSite("写作"), writing: true, exclude: ".outline-tree,.card-board" },
  { label: "工作台·大纲树", open: () => viewSite("大纲") },
  {
    label: "工作台·卡片板",
    open: async () => {
      await viewSite("大纲");
      await clickGroupTab("大纲视图切换", "场景卡板");
    }
  },
  { label: "工作台·设定卡", open: () => viewSite("设定卡") },
  { label: "工作台·版本历史", open: () => viewSite("版本历史") },
  { label: "工作台·全书预览", open: () => viewSite("全书预览") },
  {
    label: "弹层·卡片类型编辑器",
    open: async () => {
      await viewSite("设定卡");
      const opener = page.getByRole("button", { name: /卡片类型/ }).first();
      await opener.click();
      await page.locator("[role=dialog]").first().waitFor({ state: "visible", timeout: 15000 });
    }
  }
];

const allReds = [];
const allSkips = [];
const allNotes = [];
let ctrlTotal = 0;
let changedTotal = 0;
let advisedTotal = 0;

for (const site of SITES) {
  try {
    await site.open();
    const r = await sweepSite(site.exclude);
    ctrlTotal += r.total;
    changedTotal += r.changed;
    advisedTotal += r.advisedOk;
    for (const x of r.reds) allReds.push(`${site.label} ${x}`);
    for (const x of r.skips) allSkips.push(`${site.label} ${x}`);
    for (const x of r.notes) allNotes.push(`${site.label} ${x}`);
    console.log(`  站点 ${site.label}：改值控件 ${r.total} 颗｜真改了值 ${r.changed} 颗｜换上下文且已事先说明 ${r.advisedOk} 颗${r.modal ? " ｜模态层内" : ""}`);
    if (site.writing) {
      const m = await blockTypeMouseFlow();
      if (m.skip) allSkips.push(`${site.label} 块类型·鼠标分支（${m.skip}）`);
      else if (m.inPaper) console.log(`  · ${site.label} 块类型·鼠标分支：正文里有焦点时改值，焦点交还回 ${m.at}（写作流不断，3.2.2 的另一半）`);
      else if (m.onSelect) allReds.push(`${site.label} 块类型·鼠标分支 → 正文原本有焦点，改完值却没把焦点交还（焦点仍在控件 ${m.at}）——打字的人要重新点回正文`);
      else allReds.push(`${site.label} 块类型·鼠标分支 → 改完值焦点跑到别处（${m.at}），既不在正文也不在控件`);
    }
    if (site.label === "工作台·卡片板") {
      await clickGroupTab("大纲视图切换", "大纲树");
    }
  } catch (error) {
    allSkips.push(`${site.label}（${String(error && error.message ? error.message : error).slice(0, 60)}）`);
  }
}

console.log(`改值换上下文普查：站点 ${SITES.length} 个、改值控件 ${ctrlTotal} 颗（改值 ${changedTotal} 次，其中 ${advisedTotal} 次换了上下文但已事先说明）`);
for (const r of allReds) console.log(`  ❌ ${r}`);
for (const s of allSkips) console.log(`  ⚠️ 跳过 ${s}`);
for (const n of allNotes) console.log(`  ℹ️ 另族 ${n}`);
if (allReds.length > 0) {
  console.log("CONTEXT_CHANGE_RED 动一下控件，页面自己换了地方");
  await app.close();
  process.exit(1);
}
if (allSkips.length > 0) {
  console.log(`CONTEXT_CHANGE_SKIP 有 ${allSkips.length} 项被跳过`);
  await app.close();
  process.exit(2);
}
console.log("CONTEXT_CHANGE_OK 改值不换上下文，换了都事先说明白");
await app.close();
