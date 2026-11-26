#!/usr/bin/env node
/**
 * 查找替换**合法输入**链路普查（计划式链路：面板 → creation:replacePlanCreate →
 * workspace.createReplacePlan → replace-store.ts 的场景枚举 SQL；应用之后再看提交事件
 * 能不能把正开着的编辑器刷新）。批次 BD-257 立。
 *
 * 一句话：这一本不问错误态长什么样（那是 scripts/audit-error-identification.mjs 的事），
 * 只问一件事——**用户规规矩矩输入的时候，查找替换到底能不能用**：
 * 合法输入点「预览替换」建不建得出计划、点「应用替换」落不落得下去、
 * 落下去之后正开着的编辑器换不换正文。
 *
 * 立这一本的来由：这条链路自 2026-08-20 写下起就没在真实 app 里成功过（两处真实缺陷，
 * 产品修复在上一棒已完成并验证，本批只把它们钉成常驻账，产品源码一行不动）：
 *   ① replace-store.ts 三条场景枚举 SQL 选了 s.project_id，而 scenes 表从来没有这一列
 *      （项目归属经 chapter_id → chapters.project_id）⇒ 任何合法输入点「预览替换」都报
 *      no such column（上一棒探针账 scratch/desktop-survey/probe-tooth-t1.log：站 A、B 的
 *      错误横幅实测 "no such column: s.project_id"）。
 *   ② 应用替换后发的提交事件把场景 revision 写成 0，而渲染层只在
 *      change.revision > 已加载 revision 时才重读正文（useSceneEditorActions.handleEvent）
 *      ⇒ 完成横幅出了，正开着的编辑器却停在替换前的旧正文。
 *   此前没被发现：契约脚本（npm run verify:creation-replace）用自造的假 schema，
 *   且没有任何常驻账在真实 app 里用合法输入走过这条链路。BD-256 的牙 T6 把坏输入换成
 *   合法正则 "abc" 时撞见过①，当时记为「另类线索」。
 *
 * 与既有账的分工（都量过、都不管这件事）：
 *   · scripts/audit-error-identification.mjs（3.3.1）只量**错误态**：坏输入被拦下之后，
 *     文案、指认、播报对不对。合法输入走通走不通不在它的分母里——它那站「空查找绿基线」
 *     在本账里反过来当负对照（站 C）。
 *   · npm run verify:creation-replace（replace-contract.ts）跑的是契约：假 schema 的内存 store
 *     加一个真实 schema 场景，量得到 SQL 与 revision 的数值，量不到「面板画没画出来」
 *     「编辑器正文换没换」这类渲染层事实。
 *   · scripts/audit-status-messages.mjs（4.1.3）只管保存灯**听不听得到**；本账站 B 的保存灯
 *     只当「保存真发生过」的证据用，不判播报。
 *   · src/features/creation/__tests__/replace-plan-flow-census-source.test.ts 是本账的源码账：
 *     钉命令登记、站点标签与预期、判据关键行、针数与接线、出口次序，以及两处产品修法的形状。
 *
 * 判据（逐条编号；正式走查与自证针共用同一支 levelOf()，任一条不成立该站就不绿）：
 *   【①画得出才算测】面板祖先链 opacity/display/visibility 乘出的绘制不透明度 <0.99
 *      记 invisible ⇒ 跳过，不算绿（BD-252 的教训：算得出 ≠ 看得见）。错误横幅、计划视图、
 *      完成横幅同理——节点在 DOM 里但画不出来，不算「出现了」。
 *   【②动作要真发生】站 B 的保存灯必须真走完一轮：观察者**先挂上再打字**，装上之后灯的文本
 *      一次都没变、或没走到「已保存」，记 unproven ⇒ 跳过，不算绿——灯本来就写着「已保存」
 *      （idle 与 saved 同文案），那不是保存发生过的证据。打进去的字没落进编辑器同理。
 *   【③负对照必须被拦下】站 C 空查找点「预览替换」必须出可见错误横幅，文案逐字
 *      「查找内容不能为空。」——证明本账不是「凡预览必绿」。这条期望写在站点表里
 *      （expect.caught）：牙 T3 只把它翻成 false，站 C 当场红，判据与针都不用动。
 *   【④合法输入不许报错】站 A、B 的预览出现可见错误横幅即红（缺陷①的形状）。
 *   【⑤预览必须出计划】计划视图必须画出来，且命中读数等于该站预期（A 空场景＝0、B＝2）。
 *   【⑥应用必须完成】站 B 点「应用替换」后必须出可见完成横幅。
 *   【⑦应用后编辑器必须刷新】编辑器正文必须含替换后的整句、且不再含目标词
 *      （缺陷②的形状：完成横幅出了，编辑器却停在旧正文）。
 *   【⑧不许失明】一个站点都没测到 ⇒ 红。分母为 0 的「绿」不算通过。
 *
 * 站点表 3 个（改这里等于改分母，静态账钉着条数与标签）：
 *   A 正则·合法正则「abc」预览（空场景）——复刻 BD-256 牙 T6 撞见缺陷①的输入形状。
 *   B 普通文本·打字保存后预览并应用（当前场景）——正文打一句含两处「回声塔」的话。
 *   C 空查找·负对照。
 * 结构在一次性 profile（CREATION_READER_CAPTURE_PROFILE）里现建一个项目，不碰真实用户数据。
 *
 * 摸底（上一棒的一次性探针 scratch/desktop-survey/probe-replace-valid.mjs，同形的前两站）：
 *   产品未改那轮 probe-tooth-t1.log：检查 6 条｜过 2｜红 4（站 A、B 的错误横幅
 *   "no such column: s.project_id"）；修后 probe-after-restore.log：检查 8 条｜过 8｜红 0。
 * 干净那轮（r0-green.log；定稿后的干净轮见门禁日志，输出逐字同形）：
 *   站点 3 个｜实际测到 3 个｜绿 3｜红 0｜16 根针全过 ⇒ exit 0。
 *   站 A 命中读数「共 0 处命中」；站 B 保存灯「装观察者时『已保存』→ 之后 未保存 → 正在保存
 *   → 已保存」、命中「共 2 处命中」、完成横幅「替换完成：应用 2 处命中，影响 1 个场景
 *   （已创建保护快照与变更记录）。」、应用后正文="钟鼓楼的灯照着钟鼓楼的街。"；
 *   站 C 横幅「查找内容不能为空。」。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 900 xvfb-run -a --server-args="-screen 0 1600x1000x24" node scripts/audit-replace-plan-flow.mjs
 *   （win32 直接 node 跑。）
 * 退出码：有红 → REPLACE_PLAN_FLOW_RED / exit 1；站点被跳过 → REPLACE_PLAN_FLOW_SKIP / exit 2；
 * 自证针不自洽 → REPLACE_PLAN_FLOW_NEEDLE_FAIL / exit 1（判针先于判红）。
 *
 * 牙（运行时账与静态账的日志都在 E:\AgentWork\CRA\desktop-bd257-replace-flow-v1\）：
 *   每档跑完立刻逐字节还原**并重建**，还原后核第 1 节哈希、复跑账与 R0 逐字同形
 *   （teeth-runtime-summary.txt）。跑牙时的本脚本留了快照 audit-replace-plan-flow.at-teeth.mjs，
 *   之后只补过本段注释，判定代码未动。
 *   R0 干净轮绿：exit 0｜REPLACE_PLAN_FLOW_OK。
 *   T1 replace-store.ts 三处 c.project_id 改回 s.project_id → 重建 → exit 1｜REPLACE_PLAN_FLOW_RED：
 *      站 A、B 都红在「合法输入预览报错（错误横幅「no such column: s.project_id」）」，站 C 仍绿，
 *      16 根针全过——红来自站点，不是针（t1-sql.log）。
 *   T2 index.ts 提交事件里的场景 revision 改回 0 → 重建 → exit 1｜REPLACE_PLAN_FLOW_RED：
 *      恰好一条红，只点名「应用后编辑器正文未刷新」（实测正文停在 "回声塔的灯照着回声塔的街。"），
 *      站 A、C 仍绿（t2-revision.log）。
 *   T3 站 C 的期望反过来（站点表 caught: true → false）→ exit 1｜REPLACE_PLAN_FLOW_RED：
 *      只红站 C，文案「负对照的期望接反了」——负对照承重，本账不是「凡预览必绿」
 *      （t3-negative-control.log）。针不读站点表（用针自己的字面量），所以这一档红在站点、不在针。
 *   T4 判据②的保存灯空跑守卫反接（lampOk 恒真）→ 三站走查仍全绿，针 B、C 拦下
 *      ⇒ exit 1｜REPLACE_PLAN_FLOW_NEEDLE_FAIL（t4-needle-fail.log）。
 *   静态 S1…S36（逐档改坏源码账钉住的对象：命令登记、站点标签与预期、判据①~⑧关键行、
 *      三站动作、针数与接线、出口次序、一次性 profile、账头来由与牙记录、三处产品修法形状）：
 *      源码账逐档红，改回后全绿（teeth-static-summary.txt）。
 */
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-replace-flow-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

/* ---------- 正文种子句：一句里两处目标词，替换后的整句可逐字对账 ---------- */

const TYPED_SENTENCE = "回声塔的灯照着回声塔的街。";
const TARGET_WORD = "回声塔";
const REPLACEMENT = "钟鼓楼";

/* ---------- 站点表（改这里等于改分母，静态账钉着条数与标签） ---------- */

const SITES = [
  {
    id: "A",
    label: "正则·合法正则「abc」预览（空场景）",
    kind: "plan",
    measure: "regexPreview",
    expect: { hits: 0 }
  },
  {
    id: "B",
    label: "普通文本·打字保存后预览并应用（当前场景）",
    kind: "apply",
    measure: "typePreviewApply",
    expect: { hits: 2, word: TARGET_WORD }
  },
  {
    id: "C",
    label: "空查找·负对照",
    kind: "reject",
    measure: "emptyFind",
    expect: { caught: true, banner: "查找内容不能为空。" }
  }
];

/* ---------- 页内量具 ---------- */

let page = null;

const firstLine = (error) => String((error && error.message) || error).split("\n")[0].slice(0, 110);

// 以下三个导航辅助逐字照抄 scripts/audit-error-identification.mjs。
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
  await wizard.locator("input[placeholder='例如：测试项目']").first().fill("替换链路普查项目");
  await wizard.getByRole("button", { name: /下一步/ }).click();
  await page.locator("input[placeholder='例如：500000']").first().fill("50000");
  await wizard.getByRole("button", { name: /创建项目/ }).click();
  await page.waitForTimeout(1500);
};

/** 进写作台：三站都在这一屏上量，正文编辑器在才算进来了。 */
const enterWritingDesk = async () => {
  await openWorkbench();
  await page.keyboard.press("Escape").catch(() => {});
  await page.locator(".project-nav button", { hasText: "写作" }).first().click();
  await page.waitForSelector(".writing-desk", { timeout: 20000 });
  await page.waitForSelector('[aria-label="场景正文编辑区"]', { timeout: 15000 });
  await page.waitForTimeout(600);
};

const openPanel = async () => {
  await page.locator('button[aria-label="查找替换"]').first().click();
  await page.waitForSelector('[data-testid="replace-panel"]', { timeout: 15000 });
  await page.waitForTimeout(400);
};

const closePanel = async () => {
  await page.locator('[data-testid="replace-panel-close"]').first().click();
  await page.waitForTimeout(400);
};

const ensurePanelClosed = async () => {
  if ((await page.locator('[data-testid="replace-panel"]').count()) > 0) await closePanel();
};

const clickPreview = async () => {
  await page.locator('[data-testid="replace-preview"]').first().click();
};

const editorLocator = () => page.locator('[aria-label="场景正文编辑区"]').first();

const readEditorText = async () =>
  ((await editorLocator().textContent().catch(() => "")) || "").replace(/\s+/g, " ").trim();

/**
 * 面板此刻画出来的东西。只认画得出的节点：祖先链 opacity 乘出来的绘制不透明度 ≥0.99、
 * 且有面积（判据①）。页面里跑的函数不能引用 Node 侧的东西（页边序列化）。
 */
const readPanel = () =>
  page.evaluate(() => {
    const panel = document.querySelector('[data-testid="replace-panel"]');
    const paintedOf = (el) => {
      let painted = 1;
      for (let node = el; node && node !== document.documentElement; node = node.parentElement) {
        const style = getComputedStyle(node);
        painted *= Number(style.opacity);
        if (style.display === "none" || style.visibility === "hidden") painted = 0;
      }
      return +painted.toFixed(3);
    };
    const drawn = (el) => {
      if (!el) return false;
      const rect = el.getBoundingClientRect();
      return rect.width > 1 && rect.height > 1 && paintedOf(el) >= 0.99;
    };
    const text = (el) => ((el && el.textContent) || "").replace(/\s+/g, " ").trim();
    if (!panel) return { panel: false, painted: 0 };
    const banner = panel.querySelector('[data-testid="replace-error-banner"]');
    const plan = panel.querySelector('[data-testid="replace-plan-view"]');
    const done = panel.querySelector('[data-testid="replace-done-banner"]');
    return {
      panel: true,
      painted: paintedOf(panel),
      scopeText: text(panel.querySelector('[data-testid="replace-scope"]')),
      bannerVisible: drawn(banner),
      bannerText: banner ? text(banner.querySelector("p") || banner) : "",
      planVisible: drawn(plan),
      hitsText: plan ? text(panel.querySelector('[data-testid="replace-total-hits"]')) : "",
      hitRows: plan ? plan.querySelectorAll('[data-testid^="hit-row-"]').length : 0,
      doneVisible: drawn(done),
      doneText: done ? text(done.querySelector("p") || done) : ""
    };
  });

/** 等面板落定：accept 里任一条件成立即返回；超时如实返回当下状态（红不红留给判据）。 */
const settlePanel = async (accept, timeoutMs = 10000) => {
  await page.waitForTimeout(400);
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const state = await readPanel();
    if (!state.panel || accept(state) || Date.now() >= deadline) return state;
    await page.waitForTimeout(200);
  }
};

/**
 * 保存灯观察者：**先挂上再打字**（判据②）。只记「装上之后」灯离开初始文案的变化——
 * 装的时候灯本来就写着「已保存」（idle 与 saved 同文案），那不是保存发生过的证据。
 * 挂在 body 上而不是灯节点上：灯若随状态重挂，挂在节点上的观察者会跟着断。
 */
const installLampWatcher = () =>
  page.evaluate(() => {
    const lampText = () => (document.querySelector(".scene-save-status")?.textContent || "").trim();
    if (!document.querySelector(".scene-save-status")) return false;
    const at = lampText();
    window.__bd257lamp = { at, trail: [] };
    window.__bd257lampObs = new MutationObserver(() => {
      const current = lampText();
      const trail = window.__bd257lamp.trail;
      if (trail.length === 0 && current === at) return;
      if (trail[trail.length - 1] !== current) trail.push(current);
    });
    window.__bd257lampObs.observe(document.body, { childList: true, characterData: true, subtree: true });
    return true;
  });

const waitForSavedLamp = async (timeoutMs = 15000) =>
  page
    .waitForFunction(
      () => {
        const lamp = window.__bd257lamp;
        const current = (document.querySelector(".scene-save-status")?.textContent || "").trim();
        return Boolean(lamp && lamp.trail.length > 0 && current.includes("已保存"));
      },
      null,
      { timeout: timeoutMs }
    )
    .then(() => true)
    .catch(() => false);

const readLamp = () =>
  page.evaluate(() => {
    if (window.__bd257lampObs) window.__bd257lampObs.disconnect();
    return { at: window.__bd257lamp?.at ?? "", trail: [...(window.__bd257lamp?.trail ?? [])] };
  });

/* ---------- 逐站测量：每站交回一份原始读数，红不红由 levelOf() 判 ---------- */

const MEASURE = {
  // 站 A：复刻 BD-256 牙 T6 撞见缺陷①的输入形状——正则模式、合法正则、空场景。
  regexPreview: async () => {
    const sceneText = await readEditorText();
    await openPanel();
    await page.locator('[data-testid="replace-mode-regex"]').first().click();
    await page.locator('[data-testid="replace-replace"]').first().fill("X");
    await page.locator('[data-testid="replace-find"]').first().fill("abc");
    await clickPreview();
    const state = await settlePanel((s) => s.bannerVisible || s.planVisible);
    await closePanel();
    return {
      ...state,
      trace: [
        `${state.scopeText}｜打字前正文=${JSON.stringify(sceneText)}`,
        `预览：错误横幅=${JSON.stringify(state.bannerText)}｜计划视图=${state.planVisible ? "已出" : "没出"}｜命中读数=${JSON.stringify(state.hitsText)}`
      ]
    };
  },

  // 站 B：打一句含两处目标词的话 → 等保存灯真走完一轮 → 普通文本预览 → 应用 → 读编辑器正文。
  typePreviewApply: async () => {
    if (!(await installLampWatcher())) throw new Error("保存灯枚举为 0（.scene-save-status 缺席），保存无从取证");
    await editorLocator().click();
    await page.keyboard.type(TYPED_SENTENCE, { delay: 60 });
    await waitForSavedLamp(15000);
    const lamp = await readLamp();
    const bodyBefore = await readEditorText();

    await openPanel();
    await page.locator('[data-testid="replace-mode-plain"]').first().click();
    await page.locator('[data-testid="replace-replace"]').first().fill(REPLACEMENT);
    await page.locator('[data-testid="replace-find"]').first().fill(TARGET_WORD);
    await clickPreview();
    const preview = await settlePanel((s) => s.bannerVisible || s.planVisible);

    let applied = { doneVisible: false, doneText: "", bannerVisible: false, bannerText: "" };
    let bodyAfter = "";
    if (preview.planVisible) {
      await page.locator('[data-testid="replace-apply"]').first().click();
      applied = await settlePanel((s) => s.bannerVisible || s.doneVisible);
      // 提交事件 → 渲染层重读正文是异步的：给刷新一个有界等待，等不到就读屏上实际留着什么。
      const expected = TYPED_SENTENCE.split(TARGET_WORD).join(REPLACEMENT);
      await page
        .waitForFunction(
          (want) => (document.querySelector('[aria-label="场景正文编辑区"]')?.textContent || "").includes(want),
          expected,
          { timeout: 5000 }
        )
        .catch(() => {});
      bodyAfter = await readEditorText();
    }
    await closePanel();
    return {
      panel: preview.panel,
      painted: preview.painted,
      bannerVisible: preview.bannerVisible,
      bannerText: preview.bannerText,
      planVisible: preview.planVisible,
      hitsText: preview.hitsText,
      hitRows: preview.hitRows,
      doneVisible: applied.doneVisible,
      doneText: applied.doneText,
      applyBannerVisible: applied.bannerVisible,
      applyBannerText: applied.bannerText,
      lampAtInstall: lamp.at,
      lampTrail: lamp.trail,
      typedLanded: bodyBefore.includes(TYPED_SENTENCE),
      expectedBody: TYPED_SENTENCE.split(TARGET_WORD).join(REPLACEMENT),
      bodyBefore,
      bodyAfter,
      trace: [
        `保存灯：装观察者时「${lamp.at}」→ 之后 ${lamp.trail.length ? lamp.trail.join(" → ") : "（一次都没变）"}`,
        `打字后正文=${JSON.stringify(bodyBefore)}｜${preview.scopeText}`,
        `预览：错误横幅=${JSON.stringify(preview.bannerText)}｜命中读数=${JSON.stringify(preview.hitsText)}｜命中行 ${preview.hitRows}`,
        `应用：完成横幅=${JSON.stringify(applied.doneText)}｜错误横幅=${JSON.stringify(applied.bannerText)}`,
        `应用后正文=${JSON.stringify(bodyAfter)}｜应为=${JSON.stringify(TYPED_SENTENCE.split(TARGET_WORD).join(REPLACEMENT))}`
      ]
    };
  },

  // 站 C：负对照。上一站关面板即卸载，重开是干净状态；查找留空点「预览替换」。
  emptyFind: async () => {
    await ensurePanelClosed();
    await openPanel();
    await page.locator('[data-testid="replace-mode-plain"]').first().click();
    await page.locator('[data-testid="replace-replace"]').first().fill("");
    await page.locator('[data-testid="replace-find"]').first().fill("");
    await clickPreview();
    const state = await settlePanel((s) => s.bannerVisible || s.planVisible);
    await closePanel();
    return {
      ...state,
      trace: [
        state.scopeText,
        `预览：错误横幅=${JSON.stringify(state.bannerText)}｜计划视图=${state.planVisible ? "已出" : "没出"}`
      ]
    };
  }
};

/* ---------- 判据（正式走查与自证针共用同一支 hitsOf()/levelOf()/verdicts()） ---------- */

/** 命中读数：「共 N 处命中」取 N；取不到记 null（坏读数不许被当成 0 放过）。 */
function hitsOf(text) {
  const matched = /共\s*(\d+)\s*处命中/.exec(String(text || ""));
  return matched ? Number(matched[1]) : null;
}

function levelOf(site, obs) {
  // ①【画得出才算测】
  if (!obs.panel) return { level: "absent", why: "面板枚举为 0（站点缺席）" };
  if (obs.painted < 0.99) return { level: "invisible", why: `面板没画出来（绘制不透明度 ${obs.painted}）` };
  // ②【动作要真发生】（只有站 B 有「打字 → 保存」这条前置动作）
  if (site.kind === "apply") {
    const lampOk = obs.lampTrail.length > 0 && obs.lampTrail.includes("已保存");
    if (!lampOk) {
      return {
        level: "unproven",
        why:
          obs.lampTrail.length === 0
            ? "保存灯一次都没变（观察者装上之后没出现过变化）——空跑，没测到保存"
            : `保存灯没走到「已保存」（轨迹：${obs.lampTrail.join(" → ")}）`
      };
    }
    if (!obs.typedLanded) {
      return { level: "unproven", why: `打进去的字没落进编辑器正文（实测正文=${JSON.stringify(obs.bodyBefore)}）` };
    }
  }
  // ③【负对照必须被拦下】
  if (site.kind === "reject") {
    // 期望写在站点表里（expect.caught）：牙 T3 把它翻成 false，本站当场红——
    // 证明负对照的判定跟着站点表走，不是写死的绿。
    if (!site.expect.caught) {
      if (obs.bannerVisible) {
        return {
          level: "red",
          why: `站点表期望空查找放行，实际却被拦下（错误横幅「${obs.bannerText}」）——负对照的期望接反了`
        };
      }
      return { level: "ok", why: "站点表期望空查找放行，实测也没出错误横幅" };
    }
    if (!obs.bannerVisible) {
      return {
        level: "red",
        why: `空查找没被拦下：没有可见错误横幅（横幅节点里的文字=${JSON.stringify(obs.bannerText)}）`
      };
    }
    if (obs.bannerText !== site.expect.banner) {
      return {
        level: "red",
        why: `空查找的错误文案不对：实测「${obs.bannerText}」，应为「${site.expect.banner}」`
      };
    }
    return { level: "ok", why: `空查找被拦下（「${obs.bannerText}」）——本账不是「凡预览必绿」` };
  }
  // ④【合法输入不许报错】
  if (obs.bannerVisible) return { level: "red", why: `合法输入预览报错（错误横幅「${obs.bannerText}」）` };
  // ⑤【预览必须出计划】
  if (!obs.planVisible) {
    return { level: "red", why: "合法输入预览既没报错、也没出计划视图（[data-testid=\"replace-plan-view\"] 没画出来）" };
  }
  if (hitsOf(obs.hitsText) !== site.expect.hits) {
    return {
      level: "red",
      why: `命中读数不对：实测「${obs.hitsText}」，应为「共 ${site.expect.hits} 处命中」`
    };
  }
  if (site.kind === "plan") {
    return { level: "ok", why: `合法输入没报错，计划视图已出（命中读数「${obs.hitsText}」）` };
  }
  // ⑥【应用必须完成】
  if (!obs.doneVisible) {
    return {
      level: "red",
      why: `点「应用替换」后没出完成横幅（完成横幅=${JSON.stringify(obs.doneText)}｜错误横幅=${JSON.stringify(obs.applyBannerText)}）`
    };
  }
  // ⑦【应用后编辑器必须刷新】
  const refreshed = obs.bodyAfter.includes(obs.expectedBody) && !obs.bodyAfter.includes(site.expect.word);
  if (!refreshed) {
    return {
      level: "red",
      why: `应用后编辑器正文未刷新（完成横幅出了，正开着的编辑器却停在旧正文：实测正文=${JSON.stringify(obs.bodyAfter)}）`
    };
  }
  return {
    level: "ok",
    why: `保存灯走完一轮到「已保存」｜命中读数「${obs.hitsText}」｜完成横幅已出｜编辑器正文已替换（${JSON.stringify(obs.bodyAfter)}）`
  };
}

/* ---------- 判定表（正式走查与针共用同一个 verdicts()） ---------- */

function verdicts(records, measured) {
  const rows = [];
  if (!measured) {
    rows.push({ level: "red", text: "一个站点都没测到（测到 0 个）——普查失明，分母为 0 的「绿」不算通过" });
  }
  for (const record of records) {
    if (record.level === "red") rows.push({ level: "red", text: `${record.site}：${record.why}` });
    else if (record.level === "ok") rows.push({ level: "info", text: `${record.site}：${record.why}` });
    else rows.push({ level: "skip", text: `${record.site}：${record.why}——不计绿也不计红` });
  }
  return rows;
}

/* ---------- 走查 ---------- */

console.log("查找替换合法输入链路普查——合法输入建得出计划吗、应用得下去吗、正开着的编辑器换不换正文");
const records = [];
let measured = 0;
try {
  const app = await electron.launch({
    executablePath: electronPath,
    args: [mainJs, "--disable-dev-shm-usage"],
    cwd: root,
    env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir }
  });
  try {
    page = await app.firstWindow();
    await page.waitForLoadState("domcontentloaded");
    await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
    await page.waitForTimeout(600);
    await enterWritingDesk();
    for (const site of SITES) {
      try {
        const obs = await MEASURE[site.measure]();
        const verdict = levelOf(site, obs);
        records.push({ site: site.label, ...verdict });
        if (verdict.level === "ok" || verdict.level === "red") measured += 1;
        const mark = verdict.level === "ok" ? "✅" : verdict.level === "red" ? "❌" : "⚠️";
        console.log(`  ${mark} 站 ${site.id}｜${site.label}：${verdict.why}`);
        for (const line of obs.trace ?? []) console.log(`      ${line}`);
      } catch (error) {
        records.push({ site: site.label, level: "absent", why: `进不去／动作没做成（${firstLine(error)}）` });
        console.log(`  ⚠️ 站 ${site.id}｜${site.label}：进不去／动作没做成（${firstLine(error)}）`);
      }
    }
  } finally {
    await app.close().catch(() => {});
  }
} catch (error) {
  console.log(`  ❌ 没进到写作台：${firstLine(error)}`);
} finally {
  try {
    rmSync(profileDir, { recursive: true, force: true });
  } catch {
    console.log("  （一次性 profile 没删干净）");
  }
}
for (const site of SITES) {
  if (!records.some((record) => record.site === site.label)) {
    records.push({ site: site.label, level: "absent", why: "进不去（没进到写作台，站点没测）" });
  }
}

const count = (level) => records.filter((record) => record.level === level).length;
const main = verdicts(records, measured);
const reds = main.filter((row) => row.level === "red");
const skips = main.filter((row) => row.level === "skip");
console.log(`\n普查站点 ${SITES.length} 个｜实际测到 ${measured} 个｜绿 ${count("ok")}｜红 ${count("red")}`);
for (const row of reds) console.log(`  ❌ ${row.text}`);
for (const row of skips) console.log(`  ⚠️ ${row.text}`);

/* ---------- 自证针（喂进走查同一支 levelOf() + verdicts()，不另写判定条件） ---------- */

function runNeedles() {
  // 针只喂判定函数，站点描述用针自己的字面量（同 BD-253 的写法）：站点表本身由 P 钉条数、
  // 由源码账钉标签与期望。针若去读站点表，牙 T3 翻负对照期望时针会先响，站点红就看不到了。
  const PLAN = { label: "针·合法正则预览", kind: "plan", expect: { hits: 0 } };
  const APPLY = { label: "针·打字保存后预览并应用", kind: "apply", expect: { hits: 2, word: TARGET_WORD } };
  const REJECT = { label: "针·空查找负对照", kind: "reject", expect: { caught: true, banner: "查找内容不能为空。" } };
  const REVERSED = { label: "针·负对照期望接反", kind: "reject", expect: { caught: false, banner: "查找内容不能为空。" } };
  const judge = (site, obs) =>
    verdicts([{ site: site.label, ...levelOf(site, { panel: true, painted: 1, ...obs }) }], 1);
  const has = (rows, level) => rows.some((row) => row.level === level);
  const planOk = { bannerVisible: false, bannerText: "", planVisible: true, hitsText: "共 0 处命中", hitRows: 0 };
  const applyOk = {
    bannerVisible: false,
    bannerText: "",
    planVisible: true,
    hitsText: "共 2 处命中",
    hitRows: 2,
    doneVisible: true,
    doneText: "替换完成：应用 2 处命中，影响 1 个场景（已创建保护快照与变更记录）。 继续",
    lampTrail: ["未保存", "正在保存", "已保存"],
    typedLanded: true,
    expectedBody: "钟鼓楼的灯照着钟鼓楼的街。",
    bodyBefore: "回声塔的灯照着回声塔的街。",
    bodyAfter: "钟鼓楼的灯照着钟鼓楼的街。"
  };
  const rejectOk = { bannerVisible: true, bannerText: "查找内容不能为空。", planVisible: false };
  const results = [];

  // A 面板画不出来 ⇒ 不许算绿，也不许当成红（①）
  const a = judge(PLAN, { ...planOk, painted: 0 });
  results.push(["A 面板画不出来不许算绿", has(a, "skip") && !has(a, "info") && !has(a, "red")]);
  // B 保存灯一次都没变（「本来就写着已保存」）⇒ 空跑，不许算绿（②）
  const b = judge(APPLY, { ...applyOk, lampTrail: [] });
  results.push(["B 保存灯一次都没变不许算绿", has(b, "skip") && !has(b, "info")]);
  // C 保存灯变了但没走到「已保存」⇒ 同样是空跑（②）
  const c = judge(APPLY, { ...applyOk, lampTrail: ["未保存", "正在保存"] });
  results.push(["C 保存灯没走到已保存不许算绿", has(c, "skip") && !has(c, "info")]);
  // D 打进去的字没落进编辑器 ⇒ 预览无从测（②）
  const d = judge(APPLY, { ...applyOk, typedLanded: false, bodyBefore: "" });
  results.push(["D 打进去的字没落进编辑器不许算绿", has(d, "skip") && !has(d, "info")]);
  // E 空查找没被拦下 ⇒ 该红（③；少了这条，本账就是「凡预览必绿」）
  const e = judge(REJECT, { bannerVisible: false, bannerText: "", planVisible: false });
  results.push(["E 空查找没被拦下该红", has(e, "red")]);
  // F 空查找被拦下但文案不对 ⇒ 该红（③）
  const f = judge(REJECT, { bannerVisible: true, bannerText: "查找不能为空。" });
  results.push(["F 空查找文案不对该红", has(f, "red")]);
  // G 负对照的期望接反（站点表要它放行，实际被拦下）⇒ 该红（③；牙 T3 的形状）
  const g = judge(REVERSED, rejectOk);
  results.push(["G 负对照期望接反该红", has(g, "red")]);
  // H 合法输入却报错（缺陷①的实测形状）⇒ 该红（④）
  const h = judge(PLAN, { ...planOk, planVisible: false, bannerVisible: true, bannerText: "no such column: s.project_id" });
  results.push(["H 合法输入报错该红", has(h, "red")]);
  // I 既没报错也没出计划视图 ⇒ 该红（⑤）
  const i = judge(PLAN, { ...planOk, planVisible: false });
  results.push(["I 预览没出计划视图该红", has(i, "red")]);
  // J 计划视图出了但命中读数不对（或读不出数）⇒ 该红（⑤）
  const j = judge(APPLY, { ...applyOk, hitsText: "共 0 处命中" });
  const j2 = judge(APPLY, { ...applyOk, hitsText: "" });
  results.push(["J 命中读数不对该红", has(j, "red") && has(j2, "red")]);
  // K 应用后没出完成横幅 ⇒ 该红（⑥）
  const k = judge(APPLY, { ...applyOk, doneVisible: false, doneText: "" });
  results.push(["K 应用后没出完成横幅该红", has(k, "red")]);
  // L 完成横幅出了、编辑器却停在旧正文（缺陷②的实测形状）⇒ 该红，且点名「未刷新」（⑦）
  const l = judge(APPLY, { ...applyOk, bodyAfter: "回声塔的灯照着回声塔的街。" });
  results.push(["L 编辑器停在旧正文该红并点名", has(l, "red") && l.some((row) => row.text.includes("应用后编辑器正文未刷新"))]);
  // M 一个站点都没测到 ⇒ 失明该红（⑧）
  results.push(["M 测到 0 个站点该报失明", has(verdicts([], 0), "red")]);
  // N 横幅只在 DOM 里、没画出来 ⇒ 不算「出现了」：合法预览不许误伤，负对照照样红（①）
  const n1 = judge(PLAN, { ...planOk, bannerVisible: false, bannerText: "no such column: s.project_id" });
  const n2 = judge(REJECT, { ...rejectOk, bannerVisible: false });
  results.push(["N 横幅没画出来不算出现", has(n1, "info") && !has(n1, "red") && has(n2, "red")]);
  // O 三站读数都合口径 ⇒ 全绿，一条跳过都没有（①~⑦不许误伤）
  const o = verdicts(
    [
      { site: PLAN.label, ...levelOf(PLAN, { panel: true, painted: 1, ...planOk }) },
      { site: APPLY.label, ...levelOf(APPLY, { panel: true, painted: 1, ...applyOk }) },
      { site: REJECT.label, ...levelOf(REJECT, { panel: true, painted: 1, ...rejectOk }) }
    ],
    3
  );
  results.push(["O 三站读数合口径该全绿", !has(o, "red") && !has(o, "skip") && o.length === 3]);
  // P 站点表三站齐备、各归其类：少一站（分母缩水）针先拦下（⑧）
  const kinds = ["plan", "apply", "reject"];
  results.push([
    "P 站点表三站齐备少一站不许算绿",
    SITES.length === 3 && kinds.every((kind) => SITES.filter((site) => site.kind === kind).length === 1)
  ]);
  return results;
}

const needleResults = runNeedles();
const needleOk = needleResults.every(([, ok]) => ok);
console.log("\n自证针（喂进走查同一支 levelOf() + verdicts()）：");
for (const [name, ok] of needleResults) console.log(`  ${ok ? "✅" : "❌"} 针 ${name}：${ok ? "过" : "**没过**"}`);

if (!needleOk) {
  console.log("REPLACE_PLAN_FLOW_NEEDLE_FAIL 自证针没过，本轮判定不可信");
  process.exit(1);
}
if (reds.length > 0) {
  console.log("REPLACE_PLAN_FLOW_RED 合法输入建不出计划、应用后编辑器不刷新，或者负对照失效、普查失明");
  process.exit(1);
}
if (skips.length > 0) {
  console.log("REPLACE_PLAN_FLOW_SKIP 有站点没测成（画不出来／空跑／缺席／进不去）");
  process.exit(2);
}
console.log("REPLACE_PLAN_FLOW_OK 合法输入建得出计划、应用得下去、编辑器跟着换正文，空查找也照旧被拦下");
