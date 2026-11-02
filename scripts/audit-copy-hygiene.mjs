/**
 * BD-233 常驻普查：用户可见文案里不许出现内部口径（规格文档编号 / 工程依赖说明 / 内部代号）。
 *
 * 这一族不是 WCAG 条款，而是**摸底时撞出来的真缺陷**：BD-232 量焦点顺序、BD-233 量悬停提示时，
 * 运行时属性通道连着命中同一形状——把写代码时的依据直接抄进了给用户的气泡：
 *  · 侧栏 dock 两个 label 的 title 括号里是设计文档的段落编号（「主题（…4.1：…）」式）；
 *  · 写作统计页一排字数口径页签的 title 向用户交代「这条写入还缺后端依赖」；
 *  · 编辑目标弹层里，同一句依赖说明是**屏上正文**（stats-note），并且是唯一能到达用户的
 *    通道——同一颗页签在 disabled 状态下 Chromium 根本不弹原生 title（实测：锁住的 chip
 *    悬停无气泡）。
 * 用户读到这些既看不懂也做不了什么；写代码的人换一份文档编号，屏幕上的话就跟着过期。
 *
 * 为什么只在运行时量：判据要的是「用户真的看得见吗」。源码里的注释、JSDoc、类型注释里
 * 出现设计文档编号是对的（那是本仓的账本规矩），扫源码必然误伤；而渲染后的文本节点与
 * 属性通道（title / aria-label / aria-describedby 指向的节点 / placeholder）就是用户所见。
 *
 * 判红口径（INTERNAL_TALK，逐条有意，宁可漏判不误伤用户词汇）：
 *  ① 设计文档引用：`§` 紧跟数字、「规格」+ 编号数字、「蓝图」、「批次」+ 字母编号；
 *  ② 依赖没接上的工程口径：「待集成 / 待接入 / 尚未接入 / 尚未就绪 / 未接入主进程 /
 *     接口接入 / 需…接口 / 写入接口」——这些词组必须整体出现才算，单独一个「接口」不算
 *     （AI 设置里用户真会搜「接口」找 Base URL）；
 *  ③ 内部代号：SEAM / seam / TODO / FIXME。
 * 放过（实测确认过的良性形状，写死在这里免得下一个人重新判一遍）：
 *  · 「编辑器尚未就绪」这条 toast 只说状态、不交代实现，属人话；
 *  · AI 设置里「API Key 仅在主进程加密保存」是刻意的隐私说明，「主进程」单独出现不判红；
 *  · 服务层兜底 throw（数据接口缺失 = 装坏的机器才走到的路）屏上没有，本普查量不到，
 *    由账本的静态锁钉住不复发。
 * 红判据：任一屏上任一通道命中 → COPY_HYGIENE_RED，exit 1。
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-copy-hygiene-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

/** 判红词组（见文件头「判红口径」）。整串在页内以 new RegExp 使用，避免页内再抄一份。 */
const INTERNAL_TALK = "(?:§\\s*\\d|规格\\s*§?\\s*\\d|蓝图|批次\\s*[A-Za-z]{1,3}\\s*-?\\s*[0-9]|SEAM|seam|TODO|FIXME|待集成|待接入|尚未接入|未接入主进程|接口接入|写入接口|需目标更新)";

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
  await page.locator("input[placeholder='例如：测试项目']").first().fill("文案口吻普查项目");
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

/**
 * 页内探针：把「用户所见」的两类通道都过一遍判红词组。
 *  · 屏上文字：逐文本节点（跳过 aria-hidden 子树、script/style，且要求宿主真的有尺寸）；
 *  · 属性通道：可见元素的 title / aria-label / placeholder，以及 aria-describedby
 *    真正指向的那个节点的文字（描述节点本身就是给用户读的话）。
 */
const collectScript = (patternSrc) => {
  const re = new RegExp(patternSrc, "u");
  const shown = (el) => {
    const r = el.getBoundingClientRect();
    const cs = getComputedStyle(el);
    return r.width > 1 && r.height > 1 && cs.visibility !== "hidden" && cs.opacity !== "0";
  };
  const hits = [];
  const push = (channel, el, text) => {
    const t = (text || "").replace(/\s+/g, " ").trim();
    if (!t || !re.test(t)) return;
    hits.push({
      channel,
      host: `${el && el.tagName ? el.tagName.toLowerCase() : "?"}.${(el && (el.getAttribute && el.getAttribute("class")) || "—").toString().split(/\s+/)[0]}`,
      text: t.slice(0, 64)
    });
  };
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  let node;
  while ((node = walker.nextNode())) {
    const el = node.parentElement;
    if (!el || el.closest('[aria-hidden="true"], template, script, style')) continue;
    if (!shown(el)) continue;
    push("屏上文字", el, node.textContent);
  }
  for (const el of document.querySelectorAll("[title],[aria-label],[aria-describedby],[placeholder]")) {
    if (!shown(el) || el.closest('[aria-hidden="true"]')) continue;
    push("title", el, el.getAttribute("title"));
    push("aria-label", el, el.getAttribute("aria-label"));
    push("placeholder", el, el.getAttribute("placeholder"));
    for (const id of (el.getAttribute("aria-describedby") || "").split(/\s+/).filter(Boolean)) {
      const target = document.getElementById(id);
      if (target) push("describedby", target, target.textContent);
    }
  }
  return hits;
};

const scopes = [
  ["项目·首页", async () => { await goto("项目"); const back = page.locator(".project-nav-back").first(); if (await back.isVisible().catch(() => false)) await back.click(); await page.waitForSelector(".project-home", { timeout: 10000 }); }],
  ["项目·写作台", async () => workbenchView("写作", ".writing-desk")],
  ["项目·设定卡", async () => workbenchView("设定卡", ".cards-page")],
  ["项目·大纲", async () => workbenchView("大纲", ".project-workbench")],
  ["项目·概览", async () => workbenchView("概览", ".overview-page")],
  ["项目·全书预览", async () => workbenchView("全书预览", ".preview-page")],
  ["项目·写作统计", async () => workbenchView("写作统计", ".stats-page")],
  ["项目·版本历史", async () => workbenchView("版本历史", ".history-page")],
  ["书库", async () => { await goto("书库"); await page.waitForSelector(".desktop-library-row", { timeout: 10000 }).catch(() => {}); }],
  ["卡片库", async () => { await goto("卡片库"); await page.waitForSelector(".global-card-library", { timeout: 10000 }).catch(() => {}); }],
  ["收件箱", async () => { await goto("收件箱"); await page.waitForSelector(".inbox-list", { timeout: 10000 }).catch(() => {}); }],
  ["资料阅读", async () => { await goto("资料阅读"); await page.waitForTimeout(500); }],
  ["设置", async () => { await goto("设置"); await page.waitForSelector(".desktop-page-stack", { timeout: 10000 }).catch(() => {}); }],
  // BD-233 的两条实测漏口都在弹层/页签上：编辑目标那句是屏上正文，命令面板是属性通道密集处。
  ["弹层·编辑目标", async () => {
    await workbenchView("写作统计", ".stats-page");
    await page.locator('button:has-text("编辑目标")').first().click();
    await page.waitForSelector("[role=dialog]", { timeout: 10000 });
    await page.waitForTimeout(400);
  }],
  ["浮层·命令面板", async () => {
    await goto("项目");
    const back = page.locator(".project-nav-back").first();
    if (await back.isVisible().catch(() => false)) await back.click();
    await page.waitForSelector(".project-home", { timeout: 10000 });
    await page.keyboard.press("Control+p");
    await page.waitForSelector(".creation-search-input", { timeout: 10000 });
    await page.waitForTimeout(400);
  }]
];

await createProject();
const rows = [];
let scanned = 0;
for (const [label, prep] of scopes) {
  try { await prep(); } catch (e) { console.log(`跳过「${label}」：${String(e && e.message || e).split("\n")[0].slice(0, 100)}`); continue; }
  const found = await page.evaluate(collectScript, INTERNAL_TALK).catch(() => null);
  if (!found) { console.log(`跳过「${label}」`); continue; }
  scanned += 1;
  // 常驻 dock 的每一条会在所有屏重复命中，按「通道 + 宿主 + 文字」去重后再记红。
  for (const hit of found) {
    if (!rows.some((x) => x.channel === hit.channel && x.host === hit.host && x.text === hit.text)) {
      rows.push({ scope: label, ...hit });
    }
  }
}

console.log(`文案范围 ${scanned} / ${scopes.length} 个；命中内部口径的独特文案：${rows.length} 条`);
for (const r of rows) console.log(`  - [${r.scope}] ${r.channel} @ ${r.host} 「${r.text}」`);
if (rows.length) {
  console.log(`COPY_HYGIENE_RED：${rows.length} 条用户可见文案里写着内部口径（设计文档编号 / 依赖说明 / 代号）。`);
  await app.close();
  process.exit(1);
}
console.log("COPY_HYGIENE_OK：屏上文字与 title/aria-label/placeholder/描述通道里没有内部口径。");
await app.close();
process.exit(0);
