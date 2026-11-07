#!/usr/bin/env node
/**
 * 裸单键快捷键普查（WCAG 2.1.4 Character Key Shortcuts，Level **A**）。批次 BD-250 立。
 *
 * SC 原文：「If a keyboard shortcut is implemented in content using only letter (including
 * upper- and lower-case letters), punctuation, number, or space characters), then at least one
 * of the following is true: Turn Off, Remap, Active only when a component of that type has focus」
 * （https://www.w3.org/WAI/WCAG22/Understanding/character-key-shortcuts.html）
 * 立这条的理由很具体：只用一个字符键的快捷键，会让「只能按得住单键」的开关式输入者
 * （switch access、语音输入者的宏、粘性键用户）在打字、滚页、留白时**误触发**功能，
 * 也让「空格＝翻页」这种与浏览器原生行为重合的键无法回避。
 *
 * 与既有账的分工（都量过、都不管这件事）：
 *   · scripts/audit-keyboard-reach.mjs（2.1.1/2.1.2，BD-245）走的是 **Tab 与 Esc**——
 *     它管「停靠点走得到走不到、弹层退得出退不出」，不管「不按任何控件、单敲一个字母
 *     会发生什么」。本账量的是后者，两者方向正相反（一个是可达，一个是多余）。
 *   · scripts/audit-pointer-cancellation.mjs（2.5.2）管鼠标按下/抬手的时机；
 *   · scripts/audit-timing-adjustable.mjs（2.2.1）管超时；快捷键的「关不掉」这条腿它不碰。
 *   缺口不是纸面推理：EPUB 阅读器的空格翻页挂在 window 上，焦点在 body 时也照样翻——
 *   三条例外一条都不满足（详见下面判据②），而 2.1.1/2.1.2 那本全绿。
 *
 * 判据（任一不成立即红）：
 *   【①命中要算实】某屏上某个「免 Shift 就能打出的字符键」，在**焦点落在 body** 时
 *     静默基线下连按两次、两次都让界面发生变化（或两次都被应用自己 preventDefault），
 *     才算一把「裸单键快捷键」。
 *   【②必须有退路】每条命中的键，必须能在设置里找到**真的能把这条快捷键关掉**的开关：
 *     本账会亲手去 设置 页把那个开关拨到关、回到命中的屏复按两次——界面不再有任何变化
 *     才算「退路成立」。找不到开关 ⇒ 红「没有任何关断/改键机制」；拨到关后照样触发
 *     ⇒ 红「机制是假的」（这条最要紧：写着能关、其实没接到判断上，纸面合规而用户没退路）。
 *     拨关之前还要先做一次**正向对照**：同一屏、同一把键、开关还开着时必须真有反应。
 *     少了对照，「拨关后没变化」可能是空跑——书页翻到末尾时空格本来就翻不动，假机制
 *     照样绿（牙 T1 第一轮实测正是这样蒙过去的）。对照不成立记 unproven：不算绿也不算红，
 *     整本以 exit 2 收场，逼人先把测量条件修好。
 *   【③不许失明】一屏都没开到 ⇒ 红。注意口径：**扫到屏而零命中是合规**（全站确实没有裸单键
 *     快捷键，这本就是我们要的结果），只有分母为 0（0 屏）才叫失明——别把「没量到东西」
 *     一律算红，那会让判据被后人整段关掉。
 *
 * 「连按两次」是本账的命门，不是仪式感（实测逼出来的）：
 *   探针①（/tmp/bd250-probe1.log）与探针②（/tmp/bd250-probe2.log）都只按一次，结果抓到
 *   三个「反应」：大纲页的 k、项目列表的 i、EPUB 页的「.」（进度回算）。前两个在源码里
 *   **根本没有对应的监听**（全仓 `.key === "k"` 只有要求 Ctrl/⌘ 的那支，见 SearchPanel.tsx:143），
 *   第三个是上一次翻页后阅读进度延迟回算落到「.」这一按的账上。三条都是**界面自己在沉降**
 *   （toast 退场、进度回算、懒挂载补齐）撞上了单次比对窗口。探针③改成「静默基线＋连按两次、
 *   两次都变才算」（/tmp/bd250-probe3.log：14 屏 × 47 键，一次性漂移全部清零，只剩真空格
 *  翻页一处）。所以：谁把这层判据简化回「按一次看 diff」，本账就会开始报假红。
 *
 * 三条例外怎么落地（口径写死，免得后人随手放宽）：
 *   · 例外③「同类控件获得焦点时才生效」——本账**把焦点先 blur 到 body** 才按键；焦点不在
 *     body 的键一律记「挡住」不判红也不判绿。于是卡片看板（BoardView.tsx:84 的 Enter/空格）、
 *     关系图节点（RelationGraphView.tsx:235）这种「焦点在某颗钮上才激活」的原生激活键
 *     天然落在测量范围之外——那是例外③本来的形状，不是漏量。
 *   · 例外①②（关掉／改键）统一由判据②的「亲手拨开关复按」验证；本账不读源码、不看文案，
 *     只看拨了到底有没有用。
 *   · 带 Shift 的标点（? ! @ + :）不算：Playwright 按它们必然带 Shift，那是组合键不是单键；
 *     所以扫描表里只放免 Shift 打得出的 26 字母＋10 数字＋空格＋10 个免档标点。
 *
 * 清点口径：
 *   · 站点 = 5 张侧栏屏（含退出工作台后的项目列表）＋ 7 个工作台视图 ＋ 2 个阅读器
 *     （EPUB／TXT，书库数据由本账自带的一次性 profile 喂进去，绝不碰真实数据）。
 *   · 每屏 47 键 × 静默基线；命中后为验证退路要多跑「回该屏复按（正向对照）→进设置拨到关→回该屏复按→进设置拨回」，
 *     即命中一次至少再进 2 次该屏、2 次设置页。
 *   · 命中表与 KNOWN 登记表按「键」对齐（同一个键在不同屏命中算同一条快捷键机制）。
 *
 * 摸底（产品未改那轮，账 /tmp/bd250-ck-prefix2.log）：阅读器·EPUB「空格」两次都动 →
 * 拨开关去核验时设置页里根本没有这条开关 → ❌「这条退路在产品里还不存在」→
 * CHARACTER_KEYS_RED / exit 1。这一轮还顺带暴露了普查自己的两个洞（见下面「普查自己踩的」）。
 *
 * 干净那轮实测（产品改好后重跑，与牙的 R0／R9 同形）：
 * 站点 14 个｜扫描点 658 处（14×47）｜裸单键命中 1 处（阅读器·EPUB 的空格）→ 由
 * reader.spacePaging 那条开关关掉后复按不再变化 → CHARACTER_KEYS_OK/exit 0。数字回填见批次记录。
 *
 * 普查自己踩的三件事（都不是产品缺陷，是量法缺陷；各有一条静态账钉着）：
 *   ① 「阅读器·TXT」进不去却照样出绿：TXT/MD 宿主没有 .paper-topbar，它的挂载标志是
 *     .reader-host。只等前者的话分母少一个屏，而「扫到屏而零命中」本来就是合规——少一个屏
 *     不会被任何判据咬到。改成等两者之一（见 openReader）。
 *   ② 到了设置屏却读不到开关：设置是分区的，默认停在「外观」，reader.* 那一档整段不渲染。
 *     第一轮因此把「产品已经有退路」误判成「这条退路在产品里还不存在」——红得看起来完全合理，
 *     修不掉。必须先点「阅读器」页签（见 gotoReaderSettings）。
 *   ③ 开关「拨不动」的假象：那颗 input 恒 width:1px/opacity:0/pointer-events:none，点它必被
 *     外层 span 拦住到 30s 超时；可见替身 .paper-switch-track 只是个 span，不关联表单；真人
 *     能拨动它的是同组 <label htmlFor>。另外原生 checkbox 的 aria-checked 不落 DOM，
 *     getAttribute 恒 null——照它判就把「拨得动」永久读成「拨不动」。触点走 label、状态读 isChecked()。
 *
 * 牙（运行时账 /tmp/bd250-teeth-runtime.log ＋ 补牙 /tmp/bd250-teeth-t1c.log；静态账 /tmp/bd250-teeth-static.log）：
 *   每档跑完立刻逐字节还原 **并重建**，绝不让下一档踩在脏 out/ 上；R0／R9 都回到
 *   「站点 14 个｜扫描点 658 处｜命中 1｜红 0／ℹ️ 1／跳过 0」。
 *   · T2 删掉设置页那条开关 ⇒ 红「这条退路在产品里还不存在」（exit 1）。
 *   · T3 开关 onChange 改空函数（拨得动、什么也不干）⇒ 红「勾选状态没翻到 false——退路是装饰」。
 *   · T4 整条空格支删掉（产品没这把键）⇒ 不许误伤：命中 0、红 0、CHARACTER_KEYS_OK。
 *   · C2 把 KNOWN_SHORTCUTS 清空 ⇒ 红「却没有任何关断／改键机制登记」；同时针 B 也跟着没过
 *     （登记表是针的公共依赖），于是这轮以 NEEDLE_FAIL 收场——判定不可信优先于判定内容。
 *   · C3 反转针 F（零命中算失明）⇒ CHARACTER_KEYS_NEEDLE_FAIL / exit 1。
 *   · C1 撤掉瞬时件剔除＋撤回复按判据：探针①②当年回归过的三个假反应（大纲 k、项目列表 i、
 *     EPUB 的「.」）**一个都没回来**，实测零回归。也就是说这层防抖在今天的构建上是冗余的，
 *     唯一拦得住简化的是静态账 S3/S4——别把「牙没咬到」读成「这层可以删」。
 *   · 静态 S1～S11（package.json 命令、扫描表口径、连按两次、瞬时件剔除、blur 到 body、
 *     失明只看分母、分区页签、isChecked、产品那行键判断、登记表那行、类型那一档）每档恰好红 1 条，
 *     S0／S99 红 0；台账共 20 条，另加运行时 8 支自证针。
 *   · 两枚「牙自己的」教训，写在这里免得后人重踩：
 *     其一，T1（把空格那支的开关判断摘掉＝真·假机制）**第一轮是伪绿的**——空格翻到书页末尾
 *     本来就翻不动，「拨到关后复按没变化」于是空跑成立。补上「正向对照＋PageUp 倒回中段」
 *     这道闸（对照不成立记 unproven＝跳过、整本 exit 2，绝不给绿）之后，同一档才真的红
 *     「机制是假的：纸面上有开关，判断没接到键上」。
 *     其二，为了让对照有余量而把 fixture 从 3 章 × 12 段加到 8 章 × 30 段，结果反而更糟：
 *     epubjs 分页没完成时第一按算到"内容出现"、第二按才落到翻页上，真命中被自己的判据吞成
 *     「一次性漂移」，命中掉到 0＝整本空跑绿。尺寸因此钉回 3 章 × 12 段，并被静态账钉着。
 *     其三（harness 形状，不在本账里但同源）：`rebuild()` 若以 `[ $b -ne 0 ] && {...}` 收尾，
 *     构建成功（b=0）会让函数返回 1，`rebuild x && runcensus x` 整段被跳过——日志里只剩一句
 *     build exit=0，牙齿静默不跑。判据分支必须显式 return 0。
 *
 * 需要 Xvfb 与已构建的 out/：
 *   timeout 1500 xvfb-run -a --server-args="-screen 0 1600x1000x24" node scripts/audit-character-keys.mjs
 * 退出码：有红 → CHARACTER_KEYS_RED / exit 1；有屏没开到 → CHARACTER_KEYS_SKIP / exit 2；
 * 全绿 → CHARACTER_KEYS_OK / exit 0；自证针没过 → CHARACTER_KEYS_NEEDLE_FAIL / exit 1。
 * 自证针（文末 runNeedles）：跑同一个 verdicts()——A 命中且无登记该红、B 命中且登记且关断有效
 * 不许误伤、C 登记了但拨关后仍触发该红、C' 开关拨不动该红（不许混成「复测被挡住」）、
 * D 0 屏该报失明、E 带修饰不许判红、F 有屏零命中不算失明、G 核验跑空不许算绿。抓不到／误伤即
 * CHARACTER_KEYS_NEEDLE_FAIL / exit 1。针不许污染正式表，也不许为针另写判定条件；
 * 先看 needleSelfTest.ran 再判红（BD-226 的牙齿自检实测过：把针体整段短路也别想全绿）。
 */
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import JSZip from "jszip";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-character-keys-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", process.platform === "win32" ? "electron.exe" : "electron");
const mainJs = path.join(root, "out", "main", "index.js");

/* ---------- 扫描表与登记表（改这里等于改判据，台账会当场红） ---------- */

// 免 Shift 打得出的字符键：26 字母＋10 数字＋空格＋10 个未档标点。
// 带 Shift 的标点（? ! @ + :）press 出来是组合键，不参与 2.1.4 的「单键」判定。
const KEYS = [
  ..."abcdefghijklmnopqrstuvwxyz".split(""),
  ..."0123456789".split(""),
  " ", "-", "=", "[", "]", "\\", ";", "'", ",", ".", "/"
];

// 已知「裸单键快捷键 → 关断开关」登记表。key=按下去的那个字符；setting=设置页里那条开关的
// data-setting-id。命中而不在登记表 ⇒ 红（没有任何关断机制）；在登记表但拨关后仍能触发
// ⇒ 红（机制是假的）。
const KNOWN_SHORTCUTS = [
  { key: " ", name: "空格翻页（EPUB 阅读器）", setting: "reader.spacePaging" }
];

/* ---------- 一次性 profile：把两本书喂进书库（绝不碰真实数据） ---------- */

const PARA = "雨夜的灯下，他把那封信摊在膝头，纸角被风掀起又落下。";

async function writeEpubFixture(outPath) {
  const zip = new JSZip();
  // fixture 大小是实测定的，不是随手写的：3 章 × 12 段能让空格「两次都反应」稳定命中；
  // 试过把它加到 8 章 × 30 段（想让退路核验多留些翻页余量），结果 epubjs 还在分页就把
  // 第一按算成了变化、第二按落在分页完成之后——真命中被吞成「一次性漂移」，命中数掉到 0，
  // 整本反而变成空跑的绿色（T1b/R0b 两轮实测同形）。书页翻到尽头的空跑风险改由
  // verifyEscapeRoute 的「正向对照＋PageUp 倒回」那一道拦，不靠加大 fixture。
  const titles = ["第一章 雨夜来信", "第二章 阁楼的锁", "第三章 旧运河"];
  zip.file("mimetype", "application/epub+zip", { compression: "STORE" });
  zip.file("META-INF/container.xml", `<?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>`);
  zip.file("OEBPS/content.opf", `<?xml version="1.0" encoding="utf-8"?><package xmlns="http://www.idpf.org/2007/opf" unique-identifier="uid" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>普查 EPUB</dc:title><dc:identifier id="uid">census-epub</dc:identifier><dc:language>zh</dc:language><meta property="dcterms:modified">2026-01-01T00:00:00Z</meta></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/><item id="c3" href="chapter3.xhtml" media-type="application/xhtml+xml"/></manifest><spine toc="ncx"><itemref idref="c1"/><itemref idref="c2"/><itemref idref="c3"/></spine></package>`);
  zip.file("OEBPS/nav.xhtml", `<?xml version="1.0"?><!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>目录</title></head><body><nav epub:type="toc"><ol>${titles.map((t, i) => `<li><a href="chapter${i + 1}.xhtml">${t}</a></li>`).join("")}</ol></nav></body></html>`);
  titles.forEach((title, i) => {
    const body = Array.from({ length: 12 }, (_, k) => `<p>${PARA}（P${i + 1}-${k + 1}）</p>`).join("");
    zip.file(`OEBPS/chapter${i + 1}.xhtml`, `<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>${title}</title></head><body><h1>${title}</h1>${body}</body></html>`);
  });
  writeFileSync(outPath, await zip.generateAsync({ type: "nodebuffer", mimeType: "application/epub+zip" }));
}

function seedLibrary() {
  const libraryRoot = path.join(profileDir, "NovelWorkbench", "AppLibrary");
  mkdirSync(path.join(libraryRoot, "files"), { recursive: true });
  const now = new Date().toISOString();
  const txtPath = path.join(libraryRoot, "census-txt.txt");
  writeFileSync(txtPath, Array.from({ length: 9 }, (_, i) => `第${i + 1}章 标题${i + 1}\n` + Array.from({ length: 14 }, (_, k) => `${PARA}（T${i + 1}-${k + 1}）`).join("\n")).join("\n\n"), "utf8");
  const epubPath = path.join(libraryRoot, "files", "census-epub.epub");
  writeFileSync(path.join(libraryRoot, "library.json"), JSON.stringify({ books: [
    { id: "census-txt", title: "普查 TXT", filePath: txtPath, format: "txt", importedAt: now, updatedAt: now, size: 20000 },
    { id: "census-epub", title: "普查 EPUB", filePath: epubPath, format: "epub", importedAt: now, updatedAt: now, size: 30000 }
  ] }));
  writeFileSync(path.join(libraryRoot, "reading-progress.json"), JSON.stringify({ version: 2, updatedAt: now, items: [] }));
  writeFileSync(path.join(libraryRoot, "reading-sessions.json"), JSON.stringify({ version: 1, updatedAt: now, sessions: [] }));
  return epubPath;
}

const epubFixture = seedLibrary();
await writeEpubFixture(epubFixture);

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
  await wizard.locator("input[placeholder='例如：测试项目']").first().fill("裸单键普查项目");
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

const openReader = async (format) => {
  await gotoSidebar("书库");
  await page.waitForSelector(".desktop-library-row", { timeout: 15000 });
  await page.locator(`.desktop-library-row[data-format="${format}"]`).first().click({ timeout: 8000 });
  // 「进到阅读器里」的标志按格式分两种：EPUB 的工具条是 .paper-topbar（EpubReaderToolbar.tsx:36），
  // TXT/MD 走的是另一棵树（TxtMarkdownReader.tsx:969 的 .reader-host）。摸底那轮只等 .paper-topbar，
  // TXT 屏直接超时被记成「没开到的屏」——分母少一个屏，覆盖率是假的，所以这里等两者之一。
  await page.waitForFunction(() => Boolean(document.querySelector(".paper-topbar, .reader-host")), null, { timeout: 30000 });
  await page.waitForTimeout(2400);
};

const SITES = [
  { label: "工作台·概览", open: () => viewSite("概览") },
  { label: "工作台·写作", open: () => viewSite("写作") },
  { label: "工作台·大纲", open: () => viewSite("大纲") },
  { label: "工作台·设定卡", open: () => viewSite("设定卡") },
  { label: "工作台·写作统计", open: () => viewSite("写作统计") },
  { label: "工作台·版本历史", open: () => viewSite("版本历史") },
  { label: "工作台·全书预览", open: () => viewSite("全书预览") },
  { label: "侧栏·卡片库", open: () => gotoSidebar("卡片库") },
  { label: "侧栏·收件箱", open: () => gotoSidebar("收件箱") },
  { label: "侧栏·书库", open: () => gotoSidebar("书库") },
  { label: "侧栏·设置", open: () => gotoSidebar("设置") },
  { label: "项目列表", open: async () => { await gotoSidebar("项目"); const b = page.locator(".project-nav-back").first(); if (await b.isVisible().catch(() => false)) { await b.click(); await page.waitForTimeout(900); } await page.waitForSelector(".project-home", { timeout: 15000 }); } },
  { label: "阅读器·EPUB", open: () => openReader("epub") },
  { label: "阅读器·TXT", open: () => openReader("txt") }
];

/* ---------- 页内探针：静默基线 + 复按两次 ---------- */

await page.evaluate(() => {
  window.__ck = { events: [] };
  // 注册在 window 冒泡阶段、晚于应用自己的监听 ⇒ 读得到应用有没有 preventDefault
  window.addEventListener("keydown", (e) => window.__ck.events.push({
    key: e.key, shift: e.shiftKey, ctrl: e.ctrlKey, meta: e.metaKey, alt: e.altKey, prevented: e.defaultPrevented
  }), false);
  const hash = (s) => { let h = 2166136261; for (let i = 0; i < s.length; i += 1) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return (h >>> 0).toString(16); };
  window.__sig = () => {
    // 瞬时件（toast、role=status/alert 的播报区、aria-live）不进签名——它们会自己变，
    // 这正是探针①②把异步沉降误当快捷键反应的原因。
    const clone = document.body.cloneNode(true);
    clone.querySelectorAll('[class*="toast" i],[role="alert"],[role="status"],[aria-live]').forEach((n) => n.remove());
    const main = clone.querySelector(".motion-page") || clone;
    const frames = [...document.querySelectorAll("iframe")].map((f) => {
      try { return f.contentDocument ? hash(f.contentDocument.body.innerText) : "nodoc"; } catch { return "cors"; }
    }).join(",");
    const cur = document.querySelector('.project-nav button[aria-current="page"], .desktop-sidebar [aria-current="page"]');
    return {
      h1: (document.querySelector("h1")?.textContent || "").trim().slice(0, 26),
      nav: cur ? (cur.getAttribute("aria-label") || cur.textContent || "").trim().slice(0, 20) : "",
      dialogs: document.querySelectorAll('[role="dialog"]').length,
      nodes: main.querySelectorAll("*").length,
      text: hash((main.innerText || "").slice(0, 9000)),
      frames,
      focus: document.activeElement === document.body ? "body" : `${document.activeElement.tagName.toLowerCase()}.${String(document.activeElement.className || "").split(" ")[0]}`
    };
  };
  window.__same = (a, b) => JSON.stringify({ ...a, focus: "" }) === JSON.stringify({ ...b, focus: "" });
  window.__whatChanged = (b, a) => {
    const d = [];
    if (a.nodes !== b.nodes) d.push(`节点 ${b.nodes}→${a.nodes}`);
    if (a.dialogs !== b.dialogs) d.push(`弹层 ${b.dialogs}→${a.dialogs}`);
    if (a.h1 !== b.h1) d.push(`标题「${b.h1}」→「${a.h1}」`);
    if (a.nav !== b.nav) d.push(`当前页签「${b.nav}」→「${a.nav}」`);
    if (a.frames !== b.frames) d.push("正文（iframe）换了");
    if (a.text !== b.text) d.push("正文变了");
    return d;
  };
});

const quiesce = async () => {
  let prev = await page.evaluate(() => window.__sig());
  for (let i = 0; i < 6; i += 1) {
    await page.waitForTimeout(220);
    const cur = await page.evaluate(() => window.__sig());
    if (await page.evaluate(([a, b]) => window.__same(a, b), [prev, cur])) return true;
    prev = cur;
  }
  return false;
};

const pressOnce = async (key) => {
  await page.evaluate(() => { document.activeElement?.blur?.(); window.__ck.events = []; });
  const before = await page.evaluate(() => window.__sig());
  if (before.focus !== "body") return { blocked: before.focus };
  await page.keyboard.press(key === " " ? "Space" : key).catch(() => {});
  await page.waitForTimeout(180);
  const after = await page.evaluate(() => window.__sig());
  const evs = await page.evaluate(() => window.__ck.events.slice());
  const changed = await page.evaluate(([a, b]) => !window.__same(a, b), [before, after]);
  return {
    changed,
    bare: evs.length > 0 && evs.every((e) => !e.shift && !e.ctrl && !e.meta && !e.alt),
    prevented: evs.filter((e) => e.prevented).length,
    what: await page.evaluate(([a, b]) => window.__whatChanged(a, b), [before, after])
  };
};

// 一屏一键：静默→按→静默→复按，两次都动才算命中
const probeKey = async (key) => {
  if (!(await quiesce())) return { unstable: true };
  const first = await pressOnce(key);
  if (first.blocked) return { blocked: first.blocked };
  if (!first.changed && !first.prevented) return { quiet: true };
  if (!(await quiesce())) return { unstable: true };
  const second = await pressOnce(key);
  if (!(second.changed || second.prevented)) return { drift: first.what };
  return { hit: { key, bare: first.bare && second.bare, prevented: first.prevented + second.prevented, evidence: `${first.what.join(" ")}｜复按｜${second.what.join(" ")}` } };
};

const sweepSite = async (label) => {
  const hits = [];
  let blocked = 0, drift = 0, unstable = 0, quiet = 0;
  for (const key of KEYS) {
    const r = await probeKey(key);
    if (r.hit) hits.push({ site: label, ...r.hit });
    else if (r.blocked) blocked += 1;
    else if (r.drift) drift += 1;
    else if (r.unstable) unstable += 1;
    else quiet += 1;
    if (r.hit) { await page.keyboard.press("Escape").catch(() => {}); await page.waitForTimeout(220); }
  }
  console.log(`  · ${label}：命中 ${hits.length}｜无反应 ${quiet}｜一次性漂移 ${drift}｜基线不稳 ${unstable}｜焦点不在 body ${blocked}`);
  hits.forEach((h) => console.log(`      ⚡「${h.key === " " ? "空格" : h.key}」${h.bare ? "裸单键" : "带修饰"} ${h.evidence}`));
  return hits;
};

/* ---------- 退路核验：亲手把开关拨到关，回命中的屏复按 ---------- */

const readSwitch = async (settingId) => {
  const box = page.locator(`[data-setting-id="${settingId}"]`).first();
  if ((await box.count()) === 0) return null;
  await box.scrollIntoViewIfNeeded().catch(() => {});
  const input = box.locator('[role="switch"]').first();
  if ((await input.count()) === 0) return null;
  // 触点不能是 input 本体：settings-controls.css:94–100 给它 `width:1px; opacity:0; pointer-events:none`
  //（audit-focus-rings.mjs:103 早就记过这颗「隐形本体＋可见替身」的形状），点它必被外层 span 拦住并
  // 30s 超时；点可见替身 .paper-switch-track 也不换手——它只是个 span，不关联表单。真人能拨动它的
  // 地方是同组的 <label htmlFor>（Switch.tsx:23），所以判据走 label，本体状态照读。
  // 状态也不许读 getAttribute("aria-checked")：原生 checkbox 的 aria-checked 只在无障碍树里反射，
  // DOM 上取到 null，于是「拨得动」会被永久判成「拨不动」。读 isChecked()。
  const label = box.locator("label").first();
  if ((await label.count()) === 0) return null;
  const on = await input.isChecked();
  return { state: on ? "true" : "false", sw: label };
};

// 设置页是分区的，默认停在「外观」，其余分区整段不渲染（SettingsPage.tsx:27 的 useState 初值）——
// 只走到「设置」这一屏摸不到 reader.* 那条开关。第一轮就栽在这儿：明明产品里有，却被判成
// 「这条退路在产品里还不存在」。所以要再点一次「阅读器」页签，量法跟着产品的形状走。
const gotoReaderSettings = async () => {
  await gotoSidebar("设置");
  await page.waitForSelector(".settings-nav", { timeout: 10000 });
  await page.locator(".settings-nav button", { hasText: "阅读器" }).first().click();
  await page.waitForTimeout(600);
};

// 复测前先把阅读器倒回中段。PageUp 不属单字符键、也不受 spacePaging 约束
// （EpubReaderPage.tsx 里那支 ArrowLeft/PageUp 一直是全局的），拿它退几步就能保证
// 「这一刻这把键还翻得动」——对照才有意义。别的屏按 PageUp 不改签名（签名看文本哈希与
// 节点数，滚动本身不动它们），所以这一手对非阅读器屏无害。
const rewindInReader = async () => {
  for (let i = 0; i < 6; i += 1) { await page.keyboard.press("PageUp").catch(() => {}); await page.waitForTimeout(220); }
  await page.waitForTimeout(400);
};

// 返回 { level: "none" | "fake" | "stuck" | "blocked" | "unproven" | "ok", why } —— level 直接进判定表，
// 不靠措辞去猜（判据与文案解耦，牙齿才拆得动）。
const verifyEscapeRoute = async (site, hit) => {
  const keyName = hit.key === " " ? "空格" : hit.key;
  const known = KNOWN_SHORTCUTS.find((k) => k.key === hit.key);
  if (!known) {
    return { level: "none", why: `「${keyName}」在 ${site.label} 上按两次都动，可设置里没有任何关断／改键机制登记——2.1.4 三条例外一条都不成立（焦点在 body 也生效 ⇒ 例外③出局）` };
  }
  // ① 正向对照（牙 T1 第一轮实测教出来的）：开关还开着时，先确认这把键在此刻的位置上真的会反应。
  //    少了这一步，「拨到关后什么都没发生」很可能是空跑出来的绿——空格翻到书页末尾本来就翻不动，
  //    那种状态下把开关判断整条摘掉（真·假机制）也照样判「退路成立」。对照不成立 ⇒ unproven，
  //    记跳过并让整本以 exit 2 收场：既不算绿也不算红，逼人先把测量条件修好。
  await site.open();
  await rewindInReader();
  const control = await probeKey(hit.key);
  if (!control.hit) {
    const why = control.blocked ? `焦点落在 ${control.blocked}` : control.unstable ? "基线一直不稳" : control.drift ? "只抓到一次性漂移" : "按下去界面不动";
    return { level: "unproven", why: `退路核验跑空：开关还开着时 ${site.label} 按「${keyName}」已经没有反应（${why}）——先修测量条件，这条不许算绿` };
  }
  // ② 拨到关，复按
  await gotoReaderSettings();
  const cur = await readSwitch(known.setting);
  if (!cur) {
    return { level: "none", why: `登记表说「${known.name}」由设置项 ${known.setting} 关掉，可设置页里找不到那条开关——产品里这条退路还不存在` };
  }
  if (cur.state !== "false") { await cur.sw.click(); await page.waitForTimeout(420); }
  const off = await readSwitch(known.setting);
  if (!off || off.state !== "false") {
    return { level: "stuck", why: `「${known.name}」的开关拨不动（点下去读到的勾选状态仍是 ${off ? off.state : "读不到"}）` };
  }
  await site.open();
  await rewindInReader();
  const r = await probeKey(hit.key);
  const back = await (async () => { await gotoReaderSettings(); return readSwitch(known.setting); })();
  if (back && back.state !== "true") { await back.sw.click(); await page.waitForTimeout(320); }
  if (r.hit) {
    return { level: "fake", why: `「${known.name}」拨到「关」之后，${site.label} 里按「${keyName}」照样触发（${r.hit.evidence}）——机制是假的：纸面上有开关，判断没接到键上` };
  }
  if (r.blocked || r.unstable) {
    return { level: "blocked", why: `「${known.name}」关断复测被挡住（${r.blocked ? `焦点落在 ${r.blocked}` : "基线一直不稳"}），这条不计绿也不计红` };
  }
  return { level: "ok", why: `「${known.name}」拨到「关」后 ${site.label} 复按「${keyName}」界面不再变化（开关 ${known.setting} 接得住，且拨关前对照确认过这把键确实会反应）` };
};

/* ---------- 判定表（正式表与针共用同一个 verdicts()） ---------- */

// sweptSites=0 ⇒ 失明红（一把屏都没开到，谈不上合规）；有屏而零命中 ⇒ 合规，不判红。
function verdicts(records, sweptSites) {
  const rows = [];
  const bare = records.filter((r) => r.bare);
  if (!sweptSites) {
    rows.push({ level: "red", text: "一屏都没扫到（扫过的屏 0 个）——普查失明，分母为 0 的「绿」不算通过" });
  }
  for (const r of bare) {
    const known = KNOWN_SHORTCUTS.find((k) => k.key === r.key);
    const name = known ? known.name : `「${r.key === " " ? "空格" : r.key}」`;
    if (!known) {
      rows.push({ level: "red", text: `${name} 在 ${r.site} 上按两次都动，却没有任何关断／改键机制登记（2.1.4 例外①②③全不成立）` });
      continue;
    }
    if (r.escape === "none") rows.push({ level: "red", text: `${name} 在设置页里找不到开关 ${known.setting}——这条退路在产品里还不存在（登记表先立、产品后补，补上之前一直红）` });
    else if (r.escape === "stuck") rows.push({ level: "red", text: `${name} 的开关拨不动（勾选状态没翻到 false）——退路是装饰` });
    else if (r.escape === "fake") rows.push({ level: "red", text: `${name} 拨到关后照样触发——机制是假的，用户没有真退路` });
    else if (r.escape === "blocked") rows.push({ level: "skip", text: `${name} 的关断复测被挡住，不计绿也不计红` });
    else if (r.escape === "unproven") rows.push({ level: "skip", text: `${name} 的退路核验跑空（拨关前连「开着会反应」的对照都没成立），不计绿也不计红` });
    else rows.push({ level: "info", text: `${name} 有真退路：关掉 ${known.setting} 后复按不再变化` });
  }
  const withMod = records.filter((r) => !r.bare);
  if (withMod.length) rows.push({ level: "info", text: `${withMod.length} 处按键带修饰（Ctrl/⌘/Shift/Alt），不属 2.1.4 的「单键」，记一笔不判` });
  return rows;
}

/* ---------- 走查 ---------- */

console.log("裸单键快捷键普查（2.1.4）——每屏 47 个免 Shift 字符键 × 静默基线 × 连按两次");
await openWorkbench();
const records = [];
const unopened = [];
let sweptSites = 0;
for (const site of SITES) {
  try { await site.open(); }
  catch (e) { unopened.push(`${site.label}（${e.message.split("\n")[0].slice(0, 40)}）`); continue; }
  sweptSites += 1;
  const hits = await sweepSite(site.label);
  for (const h of hits) {
    if (!h.bare) {
      console.log(`      🚩「${h.key}」带修饰，不算 2.1.4 的单键——记一笔不判`);
      records.push({ ...h, bare: false });
      continue;
    }
    const v = await verifyEscapeRoute(site, h);
    h.escape = v.level;
    records.push(h);
    console.log(`      ${v.level === "ok" ? "✅" : v.level === "blocked" ? "⚠️" : "❌"} ${v.why}`);
  }
}
await app.close();
rmSync(profileDir, { recursive: true, force: true });

const main = verdicts(records, sweptSites);
const reds = main.filter((r) => r.level === "red");
const infos = main.filter((r) => r.level === "info");
const skips = main.filter((r) => r.level === "skip");
console.log(`\n普查站点 ${SITES.length} 个｜实际扫到 ${sweptSites} 个｜扫描点 ${sweptSites * KEYS.length} 处（每屏 ${KEYS.length} 键）｜裸单键命中 ${records.filter((r) => r.bare).length} 处${unopened.length ? `｜没开到的屏 ${unopened.length} 个` : ""}`);
if (unopened.length) console.log(`  ⚠️ 未开到的屏：${unopened.join("、")}`);
console.log(`红 ${reds.length} 条／ℹ️ ${infos.length} 条／跳过 ${skips.length} 条`);
for (const r of reds) console.log(`  ❌ ${r.text}`);
for (const r of infos) console.log(`  ℹ️ ${r.text}`);
for (const r of skips) console.log(`  ⚠️ ${r.text}`);

/* ---------- 自证针 ---------- */

function runNeedles() {
  const results = [];
  // A 命中且无登记 ⇒ 该红
  const a = verdicts([{ site: "针A屏", key: "q", bare: true, escape: "none" }], 3);
  results.push(["A 裸单键无关断机制该红", a.some((r) => r.level === "red")]);
  // B 命中且登记且关断有效 ⇒ 不许红
  const b = verdicts([{ site: "针B屏", key: " ", bare: true, escape: "ok" }], 3);
  results.push(["B 有真退路不许误伤", !b.some((r) => r.level === "red") && b.some((r) => r.level === "info")]);
  // C 登记了但拨关仍触发 ⇒ 该红
  const c = verdicts([{ site: "针C屏", key: " ", bare: true, escape: "fake" }], 3);
  results.push(["C 假机制该红", c.some((r) => r.level === "red")]);
  // C' 开关拨不动 ⇒ 该红（不许把「拨不动」算成「关断复测被挡住」而蒙过去）
  const cp = verdicts([{ site: "针C'屏", key: " ", bare: true, escape: "stuck" }], 3);
  results.push(["C' 开关拨不动该红", cp.some((r) => r.level === "red")]);
  // D 一屏都没开到 ⇒ 失明该红
  const d = verdicts([], 0);
  results.push(["D 扫到 0 屏该报失明", d.some((r) => r.level === "red")]);
  // E 一次性漂移/带修饰不算命中 ⇒ 不许红
  const e = verdicts([{ site: "针E屏", key: "k", bare: false, escape: "ok" }], 3);
  results.push(["E 带修饰不许判红", !e.some((r) => r.level === "red")]);
  // F 全站零命中但有屏 ⇒ 合规，不许拿「没量到」当失明
  const f = verdicts([], 14);
  results.push(["F 零命中不算失明", !f.some((r) => r.level === "red")]);
  // G 对照跑空（unproven）⇒ 不许算绿：牙 T1 第一轮就是被这种形状蒙过去的
  const g = verdicts([{ site: "针G屏", key: " ", bare: true, escape: "unproven" }], 3);
  results.push(["G 核验跑空不许算绿", !g.some((r) => r.level === "red") && !g.some((r) => r.level === "info") && g.some((r) => r.level === "skip")]);
  return results;
}

const needleResults = runNeedles();
const needleOk = needleResults.every(([, ok]) => ok);
needleResults.forEach(([n, ok]) => console.log(`  针 ${n}：${ok ? "过" : "**没过**"}`));
console.log(`needleSelfTest.ran=${needleResults.length} passed=${needleOk}`);

if (!needleOk) {
  console.log("CHARACTER_KEYS_NEEDLE_FAIL 自证针没过，本轮判定不可信");
  process.exit(1);
}
if (reds.length) {
  console.log(`CHARACTER_KEYS_RED 裸单键快捷键没有真退路，或者普查失明`);
  process.exit(1);
}
if (unopened.length || skips.length) {
  console.log("CHARACTER_KEYS_SKIP 有屏没开到或有关断复测被挡住");
  process.exit(2);
}
console.log("CHARACTER_KEYS_OK 每条裸单键快捷键都有拨得动的退路");
process.exit(0);
