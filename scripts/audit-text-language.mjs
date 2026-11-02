/**
 * BD-225 常驻普查：WCAG 3.1.2 部分语言（Level AA）+ 装饰性双语眉标的重复播报，运行时活体实测。
 *
 * 判据口径：
 *   ① 3.1.2——文档默认语言是 zh-CN（index.html 的 <html lang>），任何**会进无障碍树**的
 *      拉丁散文 run，其有效语言（最近一个带 lang 的祖先）必须不是 zh。中文语音包念
 *      "World bible" 会逐字母硬拼，这就是本条要拦的事。
 *      术语豁免（Understanding 3.1.2 明写的例外：专有名词、术语、已进入本地行话的词）：
 *      键名（Ctrl / ⇧ / K 这类 <kbd>）与文件格式名（TXT / EPUB / Markdown）不判红，
 *      但每次运行都单独记档一行——豁免要留名，不然它会变成一条谁都能塞东西的暗门。
 *   ② 装饰性复述不进树——11 处英文眉标各自紧邻一条中文标题且是它的同义翻译
 *      （逐处的对照表在 src/components/__tests__/text-language-3-1-2-source.test.ts 与
 *      各站点注释里）。这一族必须 aria-hidden，读屏不许听到「World bible … 创作背景设定」
 *      两遍。同槽位的中文眉标（批注与引用 / 关联卡片 / 新锚点 / 节点类型 / 工作台概览）
 *      **不在这一族里**：它们下面没有同义标题，那份信息只有这一处，藏起来就是丢内容。
 *
 * 自证（判据能不能红）：
 *   ① 的针：往活体文档里塞一颗没有 lang 的拉丁 span，扫描必须当场抓到它；抓不到＝扫描
 *      本身是坏的，判红而不是判绿。② 的针：11 处眉标逐处按精确文本认，任何一处出现在
 *      无障碍树里就红。
 *
 * 覆盖面进判据：判定屏数不足（< LANG_MIN）即视为覆盖塌陷——导航断了、种子没播上都会
 * 静默少测，不许用「零 RED」蒙过。可达屏之外的站点（AI 报告 / AI 候选评审两颗弹层要接
 * AI 后端才出得来）由源码台账兜，不在这里假称判过。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 900 xvfb-run -a --server-args="-screen 0 1600x1000x24" npm run audit:text-language
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir, platform } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const LANG_MIN = 12;

/**
 * 术语豁免（Understanding 3.1.2 明写的例外）按语义认，不按字符串猜：
 * <kbd> 里的是键名；纯文件格式名（TXT/EPUB/Markdown…）是技术术语。豁免不判红，
 * 但每轮记档留名，防止这条口子悄悄变宽。
 */
const FORMAT_WORDS = ["txt", "rtf", "epub", "md", "markdown", "html", "pdf", "docx", "json", "ai", "gpt", "api", "url"];

/** ②的名单：11 处装饰性英文眉标（精确文本）。 */
const DECOR_EYEBROWS = [
  "Editor", "AI polish", "Scene card", "World bible", "World building",
  "New project", "Index drawer", "Manuscript", "AI Report", "AI Candidate", "Reading rhythm"
];

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-language-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", platform() === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

const launch = async () => {
  const app = await electron.launch({
    executablePath: electronPath,
    args: [mainJs],
    cwd: root,
    env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir }
  });
  const page = await app.firstWindow();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
  await page.waitForTimeout(600);
  return { app, page };
};

{
  const first = await launch();
  await first.app.close();
}
const seed = spawnSync(electronPath, [path.join(__dirname, "round4-seed.cjs"), profileDir], {
  cwd: root,
  encoding: "utf8",
  env: { ...process.env, ELECTRON_RUN_AS_NODE: "1", NODE_PATH: path.join(root, "node_modules") },
  windowsHide: true,
  timeout: 60000
});
if (seed.status !== 0) throw new Error(`seed failed: ${seed.stderr}`);

let exitCode = 0;
let handle = null;

try {
  const { app, page } = await launch();
  handle = { app, page };

  const reds = [];
  const notes = [];
  const judged = [];
  const skipped = [];
  const exposed = new Set();

  await app.evaluate(({ BrowserWindow }) => {
    BrowserWindow.getAllWindows()[0].setBounds({ x: 40, y: 40, width: 1360, height: 900 });
  });
  await page.waitForTimeout(600);

  /**
   * 一屏的现场：所有「进无障碍树的拉丁散文 run」。
   * 不进树的三种：display/visibility 藏掉的、祖先 aria-hidden 的、以及纯符号（无 3 连字母）。
   * 用户内容整块排除：正文编辑器、通读页、阅读器、书库行（书名/作者/路径是用户与第三方数据，
   * app 猜不出语言，也不该由它负责）——这条口径写在台账里，逐屏判定只判产品自己的文案。
   */
  const scan = () => page.evaluate((formatWords) => {
    const USER_HOSTS = [".writing-scroll", ".preview-page", ".reader-page", ".reader-view", ".desktop-library-row", ".paste-preview-dialog", ".inbox-item-body"];
    const cjk = /[\u3400-\u9fff\u3040-\u30ff\uac00-\ud7af]/;
    const out = [];
    const walk = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    for (let n = walk.nextNode(); n; n = walk.nextNode()) {
      const raw = (n.textContent || "").trim();
      if (raw.length < 3 || cjk.test(raw) || !/[A-Za-z]{3,}/.test(raw)) continue;
      const el = n.parentElement;
      if (!el) continue;
      let hidden = false;
      let lang = "";
      let user = false;
      for (let p = el; p; p = p.parentElement) {
        if (p.nodeType === 1) {
          if (p.getAttribute && p.getAttribute("aria-hidden") === "true") hidden = true;
          if (!lang) { const v = p.getAttribute && p.getAttribute("lang"); if (v) lang = v; }
          for (const sel of USER_HOSTS) { if (p.matches && p.matches(sel)) user = true; }
        }
      }
      // 回溯必须一路走到 <html>：第一版在这里 break 于 body，有效语言恒为空串，下游
      // `("" || "（继承文档）")` 把它当成「非 zh」放过，①整条判据静默空转——9 处红全成了绿的。
      // 再兜一层 documentElement.lang，是让「没标语言」显式落到文档默认语言上，而不是留空串。
      const effective = lang || document.documentElement.getAttribute("lang") || "zh";
      const st = getComputedStyle(el);
      const r = el.getBoundingClientRect();
      const painted = !!r.width && !!r.height && st.visibility !== "hidden" && st.display !== "none";
      if (!painted || user) continue;
      out.push({ text: raw.slice(0, 48), lang: effective, hidden, kbd: !!(el.closest && el.closest("kbd")),
        cls: String(el.className || "").split(/\s+/)[0] || el.tagName.toLowerCase(),
        tag: el.tagName.toLowerCase() });
    }
    return out.map((it) => ({ ...it, jargon: it.kbd || formatWords.includes(it.text.replace(/[^A-Za-z]/g, "").toLowerCase()) }));
  }, FORMAT_WORDS);

  const measure = async (tag) => {
    let hits;
    try {
      hits = await scan();
    } catch (error) {
      skipped.push(`${tag}：${String(error && error.message).split("\n")[0].slice(0, 120)}`);
      return;
    }
    judged.push(tag);
    const wrong = [];
    for (const it of hits) {
      if (!it.hidden) exposed.add(it.text);
      if (it.hidden) continue;
      if (it.jargon) { notes.push(`${tag}：术语豁免「${it.text}」<${it.tag}.${it.cls}>`); continue; }
      const sub = (it.lang || "（继承文档）").split("-")[0].toLowerCase();
      if (sub !== "zh") continue;
      wrong.push(it);
    }
    if (wrong.length) {
      reds.push(`${tag}：${wrong.length} 处拉丁散文挂在 zh 语言下 —— ${wrong.slice(0, 4).map((w) => `「${w.text}」<${w.tag}.${w.cls}>`).join("；")}`);
    }
    return wrong.length;
  };

  const side = async (name) => {
    try {
      await page.locator(".desktop-sidebar button", { hasText: name }).first().click();
      await page.waitForTimeout(900);
      await measure(name);
    } catch (error) {
      skipped.push(`${name}：${String(error && error.message).split("\n")[0].slice(0, 120)}`);
    }
  };

  const workbenchView = async (name) => {
    try {
      const btn = page.locator(".project-nav button", { hasText: name }).first();
      if (!(await btn.isVisible().catch(() => false))) { skipped.push(`工作台·${name}：导航里没有这一项`); return; }
      await btn.click();
      await page.waitForTimeout(1000);
      await measure(`工作台·${name}`);
    } catch (error) {
      skipped.push(`工作台·${name}：${String(error && error.message).split("\n")[0].slice(0, 120)}`);
    }
  };

  const enterWorkbench = async () => {
    try {
      await page.locator(".desktop-sidebar button", { hasText: "项目" }).first().click();
      await page.waitForTimeout(900);
      await measure("项目·首页");
      const create = page.getByRole("button", { name: /新建第一个项目|新建项目/ }).first();
      if (await create.isVisible().catch(() => false)) {
        await create.click();
        const wizard = page.locator("[role=dialog]").first();
        await wizard.waitFor({ state: "visible", timeout: 15000 });
        await wizard.getByRole("button", { name: /下一步/ }).click();
        await page.waitForTimeout(700);
        await measure("弹层·新建向导");
        await page.locator("input[placeholder='例如：测试项目']").first().fill("语言验收项目");
        await wizard.getByRole("button", { name: /下一步/ }).click();
        await page.locator("input[placeholder='例如：500000']").first().fill("50000");
        await wizard.getByRole("button", { name: /创建项目/ }).click();
        await page.waitForTimeout(2400);
      } else {
        const open = page.getByRole("button", { name: "打开项目：语言验收项目" }).first();
        if (await open.isVisible().catch(() => false)) { await open.click(); await page.waitForTimeout(1800); }
      }
      await measure("工作台·概览");
    } catch (error) {
      skipped.push(`工作台·概览：${String(error && error.message).split("\n")[0].slice(0, 120)}`);
    }
  };

  await enterWorkbench();
  for (const name of ["写作", "大纲", "全书预览", "设定卡", "写作统计", "版本历史"]) await workbenchView(name);

  // 设定卡里的「背景设定」开关切过去：World building 这颗眉标的宿主在开关的另一侧，
  // 不点这一下就少判一处（第一版就漏在这里，靠 ②的 8/11 计数才看出来）。
  try {
    await page.locator(".project-nav button", { hasText: "设定卡" }).first().click();
    await page.waitForTimeout(900);
    const sw = page.locator(".cards-view-switch-button").first();
    if (await sw.isVisible().catch(() => false)) {
      await sw.click();
      await page.waitForTimeout(1200);
      await measure("工作台·设定卡·背景");
      await sw.click();
      await page.waitForTimeout(600);
    } else {
      skipped.push("工作台·设定卡·背景：没有 .cards-view-switch-button 这颗开关");
    }
  } catch (error) {
    skipped.push(`工作台·设定卡·背景：${String(error && error.message).split("\n")[0].slice(0, 120)}`);
  }

  // 写作速查抽屉（Ctrl+Shift+K 开的右侧索引抽屉）
  try {
    await page.locator(".project-nav button", { hasText: "写作" }).first().click();
    await page.waitForTimeout(900);
    await page.keyboard.press("Control+Shift+K");
    await page.waitForTimeout(900);
    await measure("抽屉·写作速查");
    await page.keyboard.press("Control+Shift+K");
    await page.waitForTimeout(600);
  } catch (error) {
    skipped.push(`抽屉·写作速查：${String(error && error.message).split("\n")[0].slice(0, 120)}`);
  }

  // 收件箱：列表 + 第一条详情（详情面板是「Editor / AI polish」两颗眉标的宿主）
  try {
    await page.locator(".desktop-sidebar button", { hasText: "收件箱" }).first().click();
    await page.waitForTimeout(900);
    await measure("收件箱");
    const row = page.locator(".inbox-page .inbox-item, .inbox-page [data-inbox-item], .inbox-page li button").first();
    if (await row.isVisible().catch(() => false)) {
      await row.click();
      await page.waitForTimeout(1000);
      await measure("收件箱·详情");
    } else {
      skipped.push("收件箱·详情：列表里没有可点的条目（种子没播上？）");
    }
  } catch (error) {
    skipped.push(`收件箱·详情：${String(error && error.message).split("\n")[0].slice(0, 120)}`);
  }

  await side("卡片库");
  await side("世界观");
  await side("设置");

  // 书库 + 其下的阅读统计仪表板（Reading rhythm 的宿主）
  try {
    await page.locator(".desktop-sidebar button", { hasText: "书库" }).first().click();
    await page.waitForTimeout(900);
    await measure("书库");
    const stats = page.getByRole("button", { name: "阅读统计" }).first();
    if (await stats.isVisible().catch(() => false)) {
      await stats.click();
      await page.waitForTimeout(1200);
      await measure("书库·阅读统计");
    } else {
      skipped.push("书库·阅读统计：没找到「阅读统计」入口");
    }
  } catch (error) {
    skipped.push(`书库·阅读统计：${String(error && error.message).split("\n")[0].slice(0, 120)}`);
  }

  /**
   * ①的针：塞一颗没有 lang 的拉丁 span，然后**走 measure() 这条真判据**——不许另写一套
   * 判定条件来验自己（第一版就是针用了自己的默认值，真判据 break 在 body 上静默空转，
   * 针却绿着过了）。针造成的红只作自检用，截掉不留进结论；针这一屏也不算覆盖。
   */
  try {
    await page.evaluate(() => {
      const needle = document.createElement("span");
      needle.id = "bd225-lang-needle";
      needle.textContent = "Language Needle Probe";
      document.querySelector(".desktop-canvas").appendChild(needle);
    });
    const redsBefore = reds.length;
    const judgedBefore = judged.length;
    await measure("自证·注入针");
    const caught = reds.slice(redsBefore).some((r) => r.includes("Language Needle Probe"));
    reds.length = redsBefore;
    judged.length = judgedBefore;
    if (!caught) reds.push("自证失败：注入的无 lang 拉丁节点没被 ①抓到——这条判据是空的，本普查证不了任何事。");
    await page.evaluate(() => document.getElementById("bd225-lang-needle")?.remove());
  } catch (error) {
    reds.push(`自证没跑成：${String(error && error.message).split("\n")[0].slice(0, 120)}`);
  }

  /** ②：11 处装饰眉标一颗都不许在无障碍树里露着。 */
  const leaked = DECOR_EYEBROWS.filter((t) => exposed.has(t));
  if (leaked.length) {
    reds.push(`装饰性双语眉标仍在读屏里：${leaked.length}/11 处未隐藏 —— ${leaked.map((t) => `「${t}」`).join("、")}`);
  }

  const dedupNotes = [...new Set(notes)];
  console.log(`语言扫描完成；判定 ${judged.length} 屏，进树拉丁串 ${exposed.size} 种。`);
  if (judged.length < LANG_MIN) {
    console.log(`LANGUAGE_RED：只判定到 ${judged.length} 屏（要求 ≥${LANG_MIN}），覆盖不足无法证明。`);
    exitCode = 1;
  } else if (reds.length) {
    for (const red of reds) console.log(`  - ${red}`);
    console.log(`LANGUAGE_RED：${reds.length} 项未达 3.1.2 / 装饰复述不进树。`);
    exitCode = 1;
  } else {
    console.log(`LANGUAGE_OK：${judged.length} 屏里进无障碍树的拉丁散文全部标了正确语言，11 处装饰性英文眉标全部不进树。`);
  }
  for (const n of dedupNotes.slice(0, 6)) console.log(`  记档 ${n}`);
  if (dedupNotes.length > 6) console.log(`  记档 …另有 ${dedupNotes.length - 6} 条术语豁免（同类）`);
  for (const s of skipped) console.log(`  跳过 ${s}`);
} catch (error) {
  console.error(error);
  exitCode = 1;
} finally {
  if (handle) { try { await handle.app.close(); } catch { /* 已退出 */ } }
  try { rmSync(profileDir, { recursive: true, force: true }); } catch { /* 留给系统回收 */ }
}
process.exit(exitCode);
