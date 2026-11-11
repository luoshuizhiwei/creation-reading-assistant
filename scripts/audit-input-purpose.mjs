/**
 * 输入用途普查（WCAG 1.3.5 Identify Input Purpose，AA）。批次 BD-255 立。
 *
 * 一句话：**收集用户自身信息**的输入框，它的用途要能被程序认出来——这样密码管理器、
 * 自动填充、个性化辅助技术才认得「这一格要填我已有的那把口令」。SC 原文：
 * 「The purpose of each input field collecting information about the user can be
 * programmatically determined when: the input field serves a purpose identified in the
 * Input Purposes for User Interface Components section; and the content is implemented
 * using technologies with support for identifying the expected meaning for the form input data」
 * （https://www.w3.org/WAI/WCAG22/Understanding/identify-input-purpose.html）。
 * autocomplete 的合法用途词表见 https://www.w3.org/TR/WCAG22/#input-purposes。
 *
 * 与既有账的分工（都量过、都不管这件事）：
 *   · scripts/audit-form-labels.mjs（3.3.2 / 1.3.1 输入腿）管「这一格有没有可读的名字」；
 *     名字是给人听的，用途是给机器认的，两者互不替代：本批那个框名字清清楚楚
 *     （<label>「解锁口令」），4.1.2 / 3.3.2 全绿，可它的 autocomplete 写着 off。
 *   · scripts/audit-label-in-name.mjs（2.5.3）管可见标签是不是可访问名的子串。
 *   · scripts/audit-error-identification.mjs（3.3.1）管出错时有没有把错说清楚。
 *   · 3.3.8 无障碍认证那条腿本批也摸过：两个口令框都没有拦 paste（无 onPaste preventDefault），
 *     复制粘贴这条机制在，那条 SC 不成立缺陷 ⇒ 撤掉不立账，只在此记档。
 *
 * 判据（任一条不成立即红）：
 *   【①用途词合法】参与判定的框，autocomplete 必须是词表里的合法 token（ASCII 大小写不敏感，
 *     这是 HTML 规范的取值口径）。缺失、off、on、自造词（my-pwd）都不算「用途可确定」。
 *   【②新旧口令不许搞反】填**已有**凭据的框要 current-password；设**新**口令的框要 new-password。
 *     搞反的代价是实打实的：解锁框写 new-password，密码管理器会建议一把新口令盖上去；
 *     设置框写 current-password，管理器会把旧口令填进「新口令」里。
 *   【③只管用户自身信息】项目名、筛选词、灵感速记、目录路径这些收集的是**应用内容**，
 *     不在 1.3.5 范围里 ⇒ 记 ℹ️ 不判红。把它们一并要求 autocomplete 是纸面洁癖，不是 SC。
 *   【④画得出才算测】框矩形 <2px 或落在 aria-hidden 子树里 ⇒ invisible ⇒ 跳过，不算绿。
 *   【⑤站点里得读到框】某站连一个输入框都读不到 ⇒ unproven ⇒ 跳过 + 整本 exit 2。
 *     注意：读到了框、但全是应用内容（三个负对照站点就是这样）算**测到了**、记绿——
 *     「这一屏没有用户信息框」本身就是一个成立的结论，不是没测。
 *   【⑥不许失明】一个站点都没测到 ⇒ 红；站点枚举不到记 absent ⇒ 跳过，不算合规。
 *
 * 「已有 / 新」怎么判：不写死在站点表里，而是从**屏上可见的标签文字**推（「解锁口令」⇒ 已有；
 * 「口令」「确认口令」⇒ 新）。这样标签改了、期望跟着改，账不会对着一个过期的期望空转。
 *
 * 清点口径：站点 5 个 =
 *   · 设置·存储（负对照：搜索设置项、数据目录、书库目录，全是应用内容）
 *   · 弹层·设置加密备份口令（create：口令 ＋ 确认口令，都该是 new-password）
 *   · 弹层·恢复加密备份口令（confirm：解锁口令，该是 current-password —— 本批真实红处）
 *   · 项目·写作台（负对照：项目名与四个下拉，应用内容）
 *   · 收件箱（负对照：灵感速记与目标项目，应用内容）
 * 口令弹层是同一个组件 PassphraseDialog 的两个 mode，另一个挂载点（项目包加密导入/导出，
 * CreationProjectsPage.tsx:722）要先有一个加密 .crbundle 文件才走得到，本账**没有**活体走到它，
 * 如实记档：那处靠「同一个组件、同一支 mode 分支」继承本次修法，另由源码账钉住组件里的分支形状。
 *
 * 摸底（产品未改那轮，账 /tmp/bd255-red.log；摸底探针 /tmp/bd255c-probe.log）：
 * 站点 5 个｜实际测到 5 个｜用途问题 1 处｜说得清 4 处 ⇒ INPUT_PURPOSE_RED/exit 1。
 *   ❌ 弹层·恢复加密备份口令：解锁口令框 autocomplete="off" —— 不是合法用途词，
 *     密码管理器认不出「这里要填我已有的那把口令」，用户只能凭记忆逐字敲（源码
 *     PassphraseDialog.tsx:119 的 `isCreate ? "new-password" : "off"`）。
 *   ✅ create 两框 new-password；三个负对照站点共 11 个应用内容框，按③不判红。
 *
 * 修法（只改一个词）：confirm 分支从 "off" 改成 "current-password"。
 * 为什么不是 "on"：on 只说「可以填」，没说填哪一把，管理器仍可能建议新口令；
 * current-password 才是词表里那个准确的用途。为什么不保留 off：仓库里原有一条单测
 * 把 off 钉成契约，理由是「避免密码管理器建议新口令」——那个顾虑用 current-password
 * 正好解决，off 是把孩子和洗澡水一起倒了，本批把那条单测一并改成钉 current-password。
 *
 * 干净那轮实测（账 /tmp/bd255-green.log）：站点 5 个｜实际测到 5 个｜用途问题 0 处 ⇒ exit 0。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 900 xvfb-run -a --server-args="-screen 0 1600x1000x24" node scripts/audit-input-purpose.mjs
 *
 * 牙（运行时账 /tmp/bd255-teeth-runtime.log；静态账 /tmp/bd255-teeth-static.log）：
 * 每档跑完立刻逐字节还原**并重建**；R0／R9 都回到「5 站｜测到 5｜用途问题 0」。
 *   · T1 把 confirm 分支改回 "off" ⇒ 解锁那站红，与红基线同形（exit 1）。
 *   · T2 把 confirm 分支改成 "new-password" ⇒ 解锁那站红（②新旧搞反）。
 *   · T3 把 create 分支改成 "current-password" ⇒ create 那站红（两框都错，站点算 1 处）（②反向）。
 *   · T4 把三个口令框的 autocomplete 全摘掉 ⇒ 两个口令站都红（①缺失，问题 2 处）。
 *   · T5 把词表里的 current-password 删掉 ⇒ 解锁站红 ＋ 针拦下（口径不许被抽空）。
 *   · T6 判据反接（token 合法才判红）⇒ 两个口令站红（负对照没有判定框，不受影响），针 A 拦下。
 *   · T7 把页内读数改成「一个框都没有」⇒ 五站全 unproven、测到 0 ⇒ 先报失明红 exit 1、跳过 5。
 *   · T8 把应用内容框也算进判定 ⇒ **五站全红**：两个口令弹层里也各有 3 个应用内容框
 *     （设置页的搜索/目录框在弹层背后仍在 DOM 里），一并算进判定后它们也红。
 *     两处如实记档（账 /tmp/bd255-teeth-runtime2.log ／ -runtime3.log）：期望值首轮写成
 *     「只咬三个负对照」是错的，按实测改成 5；而且这一档**针拦不住**——分类发生在页内
 *     readFields() 里，针喂的是已分好类的合成框，所以咬它的是走查本身，不是针。
 *     针 H 守的是 levelOf() 拿到 content 之后不许判红，两件事不能互相替代。
 *   · T9 把大小写归一去掉 ＋ 把产品写成 "Current-Password" ⇒ 解锁站红 ＋ 针 G/M 拦下（口径比 HTML 严）。
 *   · 静态 S1…S18 逐条改坏命令名/站点表/词表/判据/针/基线串/修法，源码账逐档红，S0/S99 绿。
 */import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-input-purpose-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

const launch = async () => {
  const app = await electron.launch({ executablePath: electronPath, args: [mainJs], cwd: root, env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir } });
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

const goto = async (label) => {
  // 浮层范围会把遮罩留在屏上，先 Esc 关掉再走侧栏（Esc 在页面级视图里是无害的）。
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
  await page.locator("input[placeholder='例如：测试项目']").first().fill("输入用途普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(1200);
}
const inWorkbench = async () => {
  if (await page.locator(".project-workbench").isVisible().catch(() => false)) return;
  await createProject();
};
const workbenchView = async (navLabel, selector) => {
  await inWorkbench();
  await page.keyboard.press("Escape").catch(() => {});
  await page.locator(".project-nav button", { hasText: navLabel }).first().click();
  await page.waitForSelector(selector, { timeout: 15000 });
  await page.waitForTimeout(400);
};

/* ---------- 口径（针与走查共用同一套判定，绝不另写一份条件） ---------- */

// WCAG 2.2 Input Purposes for User Interface Components 的合法 token（ASCII 大小写不敏感）。
const PURPOSES = [
  "name", "honorific-prefix", "given-name", "additional-name", "family-name", "honorific-suffix",
  "nickname", "username", "new-password", "current-password", "one-time-code",
  "organization-title", "organization", "street-address", "address-line1", "address-line2",
  "address-line3", "address-level1", "address-level2", "address-level3", "country", "country-name",
  "postal-code", "cc-name", "cc-given-name", "cc-additional-name", "cc-family-name", "cc-number",
  "cc-exp", "cc-exp-month", "cc-exp-year", "cc-csc", "cc-type", "transaction-currency",
  "transaction-amount", "language", "bday", "bday-day", "bday-month", "bday-year", "sex",
  "url", "photo", "tel", "tel-country-code", "tel-national", "tel-area-code", "tel-local",
  "tel-extension", "email", "impp"
];

const STORAGE_ACTIONS = '[data-setting-id="storage.encryptedBackupActions"]';

const SITES = [
  { label: "设置·存储（负对照：应用内容框）", enter: "storage" },
  { label: "弹层·设置加密备份口令（新口令两框）", enter: "createDialog" },
  { label: "弹层·恢复加密备份口令（已有口令）", enter: "confirmDialog" },
  { label: "项目·写作台（负对照：应用内容框）", enter: "writing" },
  { label: "收件箱（负对照：应用内容框）", enter: "inbox" }
];

/* 页内读数：把这一屏所有可见文本类输入框读出来，并当场分类。
   分类只看屏上能看到的东西（type / 可见标签 / placeholder），不看源码里的意图。 */
function readFields() {
  const norm = (s) => String(s || "").replace(/\s+/g, " ").trim();
  const out = [];
  for (const el of document.querySelectorAll("input, textarea, select")) {
    const r = el.getBoundingClientRect();
    const hidden = Boolean(el.closest('[aria-hidden="true"]'));
    const type = (el.getAttribute("type") || "text").toLowerCase();
    if (["hidden", "checkbox", "radio", "range", "color", "file", "submit", "button", "reset"].includes(type)) continue;
    const lab = el.closest("label");
    const labelText = norm(lab ? lab.textContent : "");
    const ph = norm(el.getAttribute("placeholder"));
    const ac = el.getAttribute("autocomplete");
    // 收集「用户自身信息」的形状：口令/凭据，以及姓名/邮箱/电话/用户名这类个人字段。
    const credential = type === "password";
    const personal = /姓名|昵称|邮箱|电子邮件|手机号|电话|用户名|账号/.test(`${labelText} ${ph}`);
    const kind = credential || personal ? "user" : "content";
    // 已有 vs 新：从可见标签推，标签改了期望跟着改。
    const existing = /解锁|当前口令|已有口令|原口令/.test(labelText);
    const expect = credential ? (existing ? "current-password" : "new-password") : "";
    out.push({
      painted: r.width >= 2 && r.height >= 2 && !hidden,
      testid: el.getAttribute("data-testid") || "",
      cls: (el.getAttribute("class") || "").split(/\s+/).slice(0, 2).join("."),
      type, kind, expect, labelText, ph,
      ac: ac === null ? null : String(ac)
    });
  }
  return out;
}

function tokenOf(raw) {
  // HTML 的 autocomplete 取值 ASCII 大小写不敏感；词表比对前先归一，否则口径比规范还严。
  return String(raw === null || raw === undefined ? "" : raw).trim().toLowerCase();
}

function levelOf(site, obs) {
  if (!obs || !Array.isArray(obs.fields)) return { level: "absent", why: "站点没读到（弹层没开／视图没切过去）", trace: "fields=∅" };
  const fields = obs.fields;
  if (fields.length === 0) return { level: "unproven", why: "这一屏一个输入框都没读到", trace: "fields=0" };
  const painted = fields.filter((f) => f.painted);
  if (painted.length === 0) return { level: "invisible", why: "框都画不出来（尺寸 <2px 或 aria-hidden）", trace: `fields=${fields.length} painted=0` };
  const judged = painted.filter((f) => f.kind === "user");
  const content = painted.length - judged.length;
  if (judged.length === 0) {
    return { level: "ok", why: `只有应用内容框 ${content} 个，按③不在 1.3.5 范围里（负对照成立）`, trace: `fields=${painted.length} user=0 content=${content}` };
  }
  const bad = [];
  for (const f of judged) {
    const token = tokenOf(f.ac);
    const who = f.testid || f.cls || f.type;
    if (!PURPOSES.includes(token)) {
      bad.push(`${who} autocomplete=${JSON.stringify(f.ac)} 不是合法用途词（①）`);
    } else if (f.expect && token !== f.expect) {
      bad.push(`${who} 该是 ${f.expect}，实际 ${token}（②新旧口令搞反）`);
    }
  }
  const trace = `fields=${painted.length} user=${judged.length} content=${content}｜${judged.map((f) => `${f.testid || f.cls}=${JSON.stringify(f.ac)}→期望 ${f.expect}`).join("，")}`;
  if (bad.length) return { level: "bad", why: bad.join("；"), trace };
  return { level: "ok", why: `${judged.length} 个用户信息框用途都说得清（应用内容框 ${content} 个不判）`, trace };
}

function verdicts(records, measured) {
  const rows = [];
  if (!measured) rows.push({ level: "red", text: "一个站点都没测到（测到 0 个）——普查失明，分母为 0 的「绿」不算通过" });
  for (const r of records) {
    if (r.level === "bad") rows.push({ level: "red", text: `${r.site}：${r.why}（1.3.5：机器认不出这一格要填什么）` });
    else if (r.level === "ok") rows.push({ level: "info", text: `${r.site}：${r.why}` });
    else rows.push({ level: "skip", text: `${r.site}：${r.why}——不计绿也不计红` });
  }
  return rows;
}

/* ---------- 走查 ---------- */

const gotoStorage = async () => {
  await goto("设置");
  await page.waitForSelector(".desktop-page-stack", { timeout: 15000 });
  await page.locator('button:has-text("存储")').first().click();
  await page.waitForSelector(STORAGE_ACTIONS, { timeout: 15000 });
  await page.waitForTimeout(400);
};

const ENTER = {
  storage: gotoStorage,
  createDialog: async () => {
    await page.keyboard.press("Escape").catch(() => {});
    await gotoStorage();
    await page.locator(`${STORAGE_ACTIONS} button`, { hasText: "导出加密备份" }).first().click();
    await page.waitForSelector('[data-testid="pe-confirm"]', { timeout: 15000 });
    await page.waitForTimeout(300);
  },
  confirmDialog: async () => {
    await page.keyboard.press("Escape").catch(() => {});
    await gotoStorage();
    await page.locator(`${STORAGE_ACTIONS} button`, { hasText: "恢复加密备份" }).first().click();
    await page.waitForTimeout(500);
    const choose = page.locator('button:has-text("选择备份文件")').first();
    if (await choose.isVisible().catch(() => false)) await choose.click();
    await page.waitForSelector('[data-testid="pe-passphrase"]', { timeout: 15000 });
    // create 模式也有 pe-passphrase；确认框在场说明进的是 create，走错了就抛错，绝不含糊测。
    if (await page.locator('[data-testid="pe-confirm"]').isVisible().catch(() => false)) {
      throw new Error("进的是设置新口令弹层，不是解锁弹层");
    }
    await page.waitForTimeout(300);
  },
  writing: async () => {
    await page.keyboard.press("Escape").catch(() => {});
    await workbenchView("写作", ".writing-desk");
  },
  inbox: async () => {
    await page.keyboard.press("Escape").catch(() => {});
    await goto("收件箱");
    await page.waitForSelector(".inbox-list", { timeout: 15000 });
    await page.waitForTimeout(400);
  }
};

async function measureSite(site) {
  await ENTER[site.enter]();
  const fields = await page.evaluate(readFields);
  return { site: site.label, ...levelOf(site, { fields }) };
}

console.log("输入用途普查（1.3.5 Identify Input Purpose）——收集用户自身信息的框，用途能不能被程序认出来");
const records = [];
let measured = 0;
let seeded = false;
try {
  await createProject();
  seeded = true;
} catch (error) {
  console.log(`  ❌ 种子没建齐：${error.message.split("\n")[0]}`);
}
if (seeded) {
  for (const site of SITES) {
    try {
      const record = await measureSite(site);
      records.push(record);
      if (["ok", "bad"].includes(record.level)) measured += 1;
      const mark = record.level === "ok" ? "✅" : record.level === "bad" ? "❌" : "⚠️";
      console.log(`  ${mark} ${record.site}：${record.why}`);
      console.log(`      ${record.trace}`);
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
console.log(`\n普查站点 ${SITES.length} 个｜实际测到 ${measured} 个｜用途问题 ${count("bad")} 处｜说得清 ${count("ok")} 处`);
console.log(`红 ${reds.length} 条／ℹ️ ${infos.length} 条／跳过 ${skips.length} 条`);
for (const r of reds) console.log(`  ❌ ${r.text}`);
for (const r of infos) console.log(`  ℹ️ ${r.text}`);
for (const r of skips) console.log(`  ⚠️ ${r.text}`);

/* ---------- 自证针（喂进同一个 levelOf() + verdicts()，不另写判定条件） ---------- */

function runNeedles() {
  const SITE = { label: "针" };
  const field = (patch = {}) => ({ painted: true, testid: "pe-passphrase", cls: "pe-input", type: "password", kind: "user", expect: "current-password", labelText: "解锁口令", ph: "", ac: "current-password", ...patch });
  const contentField = (patch = {}) => ({ painted: true, testid: "", cls: "paper-input", type: "text", kind: "content", expect: "", labelText: "数据目录", ph: "", ac: null, ...patch });
  const judge = (fields) => verdicts([{ site: "针", ...levelOf(SITE, { fields }) }], 1);
  const has = (rows, level) => rows.some((r) => r.level === level);
  const results = [];

  // A 解锁框写 off（本批真实红）⇒ 该红
  const a = judge([field({ ac: "off" })]);
  results.push(["A off 不算用途该红", has(a, "red")]);
  // B 解锁框写 current-password（本批修法）⇒ 不许红
  const b = judge([field()]);
  results.push(["B current-password 不许误伤", !has(b, "red") && has(b, "info")]);
  // C 解锁框写 new-password ⇒ 该红（②搞反）
  const c = judge([field({ ac: "new-password" })]);
  results.push(["C 解锁框写新口令该红", has(c, "red")]);
  // D 新口令框写 current-password ⇒ 该红（②反向）
  const d = judge([field({ expect: "new-password", labelText: "口令", ac: "current-password" })]);
  results.push(["D 新口令框写已有口令该红", has(d, "red")]);
  // E autocomplete 缺失 ⇒ 该红
  const e = judge([field({ ac: null })]);
  results.push(["E 缺 autocomplete 该红", has(e, "red")]);
  // F 自造词 ⇒ 该红
  const f = judge([field({ ac: "my-pwd" })]);
  results.push(["F 自造词该红", has(f, "red")]);
  // G 大小写变体 ⇒ 不许红（HTML 取值 ASCII 大小写不敏感）
  const g = judge([field({ ac: "Current-Password" })]);
  results.push(["G 大小写变体不许误伤", !has(g, "red") && has(g, "info")]);
  // H 只有应用内容框 ⇒ 记绿（③负对照），且不算跳过
  const h = judge([contentField(), contentField({ cls: "paper-input2" })]);
  results.push(["H 应用内容框不判红且算测到", !has(h, "red") && !has(h, "skip") && has(h, "info")]);
  // I 画不出来 ⇒ 跳过，不算绿也不算红
  const i = judge([field({ painted: false, ac: "off" })]);
  results.push(["I 画不出来只跳过", has(i, "skip") && !has(i, "red") && !has(i, "info")]);
  // J 一个框都没有 ⇒ 跳过（unproven）
  const j = judge([]);
  results.push(["J 空屏只跳过", has(j, "skip") && !has(j, "info")]);
  // K 词表不许被抽空：current-password / new-password 必须都在
  results.push(["K 两个口令用途词都在词表里", PURPOSES.includes("current-password") && PURPOSES.includes("new-password") && !PURPOSES.includes("off") && !PURPOSES.includes("on")]);
  // L 失明 ⇒ 红（分母为 0 的绿不算通过）
  const l = verdicts([], 0);
  results.push(["L 测到 0 该红", has(l, "red") && l.length === 1]);
  // M 大小写归一函数不许被改成原样返回
  results.push(["M tokenOf 归一大小写与空白", tokenOf(" Current-Password ") === "current-password" && tokenOf(null) === ""]);
  // N 站点表形状：5 站、三个负对照 ＋ 两个口令弹层
  results.push(["N 站点表 5 站且含两个口令弹层", SITES.length === 5 && SITES.filter((s) => /口令/.test(s.label)).length === 2 && SITES.filter((s) => /负对照/.test(s.label)).length === 3]);

  let ok = true;
  console.log("\n自证针（喂进同一个 levelOf() + verdicts()）：");
  for (const [name, pass] of results) {
    console.log(`  ${pass ? "✅" : "❌"} ${name}`);
    if (!pass) ok = false;
  }
  return ok;
}

const needlesOk = runNeedles();
if (!needlesOk) {
  console.log("\nINPUT_PURPOSE_NEEDLE_FAIL 判据自己先不自洽，本轮结论不可信");
  process.exit(1);
}
if (reds.length) {
  console.log("\nINPUT_PURPOSE_RED 有输入框的用途程序认不出来");
  process.exit(1);
}
if (skips.length) {
  console.log("\nINPUT_PURPOSE_SKIP 有站点没测成（画不出来／空屏／缺席）");
  process.exit(2);
}
console.log("\nINPUT_PURPOSE_OK 每个收集用户自身信息的框，用途都说得清");
process.exit(0);