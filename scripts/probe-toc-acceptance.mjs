/**
 * 桌面阅读目录升级第二批——端到端验收探针。
 * 覆盖：进度按章锚定（A）/ TXT·MD 书签与高亮面板（B）/ TXT 目录修正（C）/
 * 目录已读标记（D）/ 阅读器代码分割（E）。
 * 前置：npm run build。用法：node scripts/probe-toc-acceptance.mjs
 * 无窗口模式（窗口移出屏幕外），不打扰前台。
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync, readdirSync, statSync, existsSync, writeFileSync, mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { _electron as electron } from "playwright-core";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const profileDir = mkdtempSync(path.join(tmpdir(), "creation-toc-accept-"));
const electronPath = path.join(root, "node_modules", "electron", "dist", "electron.exe");
const mainJs = path.join(root, "out", "main", "index.js");

const failures = [];
const check = (name, ok, detail = "") => {
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? `  — ${detail}` : ""}`);
  if (!ok) failures.push(name);
};

// ---------------------------------------------------------------------------
// E. 代码分割（静态产物检查，无需启动应用）
// ---------------------------------------------------------------------------
function checkChunks() {
  const assetsDir = path.join(root, "out", "renderer", "assets");
  if (!existsSync(assetsDir)) {
    check("E1 ReaderPage 分包产物存在", false, "assets 目录缺失，先 npm run build");
    return;
  }
  const files = readdirSync(assetsDir).filter((f) => f.endsWith(".js"));
  const reader = files.find((f) => /^ReaderPage-/.test(f));
  const epub = files.find((f) => /^EpubReaderPage-/.test(f));
  const readerSize = reader ? statSync(path.join(assetsDir, reader)).size : 0;
  const epubSize = epub ? statSync(path.join(assetsDir, epub)).size : 0;
  check("E1 epubjs 独立 chunk（EPUB 懒加载）", Boolean(reader && epub), `ReaderPage=${Math.round(readerSize / 1024)}kB, EpubReaderPage=${Math.round(epubSize / 1024)}kB`);
  check("E2 TXT/MD 主包 < 500kB（不再含 epubjs）", readerSize > 0 && readerSize < 500 * 1024, `${readerSize} bytes`);
}

// ---------------------------------------------------------------------------
// 播种书库（中性命名测试书）
// ---------------------------------------------------------------------------
const PARA = "雨下了一整夜，屋檐的水线在灯下织成一道细帘。他把信纸翻过来，背面只有一行被水洇开的小字，潮湿、闷热、蝉声在远处断续地响着。";

function paragraphs(start, count) {
  const lines = [];
  for (let i = 0; i < count; i += 1) {
    lines.push(`${PARA.slice(0, 50 + ((i * 13) % 50))}（${start + i}）`);
  }
  return lines.join("\n\n");
}

const CN = ["一", "二", "三", "四", "五", "六", "七", "八"];

function eightChapterTxt() {
  const parts = [];
  for (let c = 0; c < 8; c += 1) {
    parts.push(`第${CN[c]}章 验收章节${c + 1}\n\n${paragraphs(c * 20 + 1, 12)}`);
  }
  return parts.join("\n\n");
}

function editTxt() {
  const parts = [];
  const titles = ["第一章 起点", "第二章 转折", "第三章 风暴", "第四章 灯火", "第五章 归途", "第六章 尾声"];
  titles.forEach((title, c) => {
    parts.push(`${title}\n\n${paragraphs(100 + c * 20 + 1, 8)}`);
  });
  return parts.join("\n\n");
}

function plainTxt() {
  return paragraphs(500, 40);
}

function mdContent() {
  return [
    "# 验收笔记",
    "",
    paragraphs(600, 3),
    "",
    "## 开端",
    "",
    paragraphs(610, 3),
    "",
    "## 发展",
    "",
    paragraphs(620, 3),
    "",
    "## 讨论",
    "",
    paragraphs(630, 2),
    "",
    "## 讨论",
    "",
    paragraphs(640, 2),
    "",
    "## 结局",
    "",
    paragraphs(650, 2)
  ].join("\n");
}

function seed() {
  const dataRoot = path.join(profileDir, "NovelWorkbench");
  const libraryRoot = path.join(dataRoot, "AppLibrary");
  mkdirSync(libraryRoot, { recursive: true });
  const now = new Date().toISOString();
  const books = [
    { id: "acc-txt-8", title: "验收 TXT 八章", filePath: path.join(libraryRoot, "acc-txt-8.txt"), format: "txt", importedAt: now, updatedAt: now, size: 20000 },
    { id: "acc-txt-edit", title: "验收 TXT 修正", filePath: path.join(libraryRoot, "acc-txt-edit.txt"), format: "txt", importedAt: now, updatedAt: now, size: 12000 },
    { id: "acc-txt-plain", title: "验收 TXT 无章", filePath: path.join(libraryRoot, "acc-txt-plain.txt"), format: "txt", importedAt: now, updatedAt: now, size: 8000 },
    { id: "acc-md", title: "验收 MD 笔记", filePath: path.join(libraryRoot, "acc-md.md"), format: "md", importedAt: now, updatedAt: now, size: 6000 }
  ];
  writeFileSync(books[0].filePath, eightChapterTxt(), "utf8");
  writeFileSync(books[1].filePath, editTxt(), "utf8");
  writeFileSync(books[2].filePath, plainTxt(), "utf8");
  writeFileSync(books[3].filePath, mdContent(), "utf8");
  writeFileSync(path.join(libraryRoot, "library.json"), JSON.stringify({ books }), "utf8");
  writeFileSync(path.join(libraryRoot, "reading-progress.json"), JSON.stringify({ version: 2, updatedAt: now, items: [] }), "utf8");
  writeFileSync(path.join(libraryRoot, "reading-sessions.json"), JSON.stringify({ version: 1, updatedAt: now, sessions: [] }), "utf8");
}

// ---------------------------------------------------------------------------
// Electron 驱动
// ---------------------------------------------------------------------------
async function launch() {
  const app = await electron.launch({
    executablePath: electronPath,
    args: [mainJs],
    cwd: root,
    env: { ...process.env, CREATION_READER_CAPTURE_PROFILE: profileDir }
  });
  const page = await app.firstWindow();
  await page.waitForLoadState("domcontentloaded");
  await page.waitForSelector(".desktop-canvas", { timeout: 30000 });
  await page.waitForTimeout(500);
  return { app, page };
}

async function openBook(page, title) {
  await page.locator(".desktop-sidebar button", { hasText: "资料阅读" }).first().click();
  await page.waitForSelector(".desktop-library-row", { timeout: 10000 });
  await page.locator(".desktop-library-row", { hasText: title }).first().click();
  await page.waitForSelector("article", { timeout: 20000 });
  await page.waitForTimeout(600);
}

async function leaveReader(page) {
  await page.locator("header button", { hasText: "返回书库" }).first().click();
  await page.waitForSelector(".desktop-library-row", { timeout: 10000 });
  await page.waitForTimeout(400);
}

/** 内容坐标系工具（在页面内执行） */
const PAGE_HELPERS = `(() => {
  const scroller = document.querySelector("article").parentElement;
  const scrollerTop = scroller.getBoundingClientRect().top;
  const absTop = (el) => el.getBoundingClientRect().top - scrollerTop + scroller.scrollTop;
  return { scroller, absTop };
})()`;

async function scrollToChapterTop(page, anchorId, offsetPx = 0) {
  await page.evaluate(
    ([id, offset]) => {
      const scroller = document.querySelector(".reader-root article").parentElement;
      const scrollerTop = scroller.getBoundingClientRect().top;
      const el = document.getElementById(id);
      if (el) {
        const absTop = el.getBoundingClientRect().top - scrollerTop + scroller.scrollTop;
        scroller.scrollTop = absTop + offset;
      }
      scroller.dispatchEvent(new Event("scroll"));
    },
    [anchorId, offsetPx]
  );
  await page.waitForTimeout(400);
}

async function readerState(page) {
  return page.evaluate(`(() => {
    const readerArticle = document.querySelector(".reader-root article");
    const scroller = readerArticle.parentElement;
    const scrollerTop = scroller.getBoundingClientRect().top;
    const absTop = (el) => el.getBoundingClientRect().top - scrollerTop + scroller.scrollTop;
    const currentRow = document.querySelector('[data-toc-row-id][aria-current="true"]');
    const h2s = Array.from(readerArticle.querySelectorAll("h2")).map((h) => h.textContent.trim());
    const dimRows = Array.from(document.querySelectorAll("[data-toc-row-id]")).filter((r) => r.className.includes("text-paper-muted/55")).length;
    const checkRows = Array.from(document.querySelectorAll("[data-toc-row-id] svg.lucide-check")).length;
    const tocHeader = Array.from(document.querySelectorAll("div")).map((d) => d.textContent).find((t) => /已读 \\d+\\//.test(t || ""));
    const marks = document.querySelectorAll("mark.txt-hl").length;
    return {
      scrollTop: scroller.scrollTop,
      scrollHeight: scroller.scrollHeight,
      clientHeight: scroller.clientHeight,
      currentId: currentRow ? currentRow.getAttribute("data-toc-row-id") : null,
      h2s,
      dimRows,
      checkRows,
      tocHeaderMatch: (tocHeader || "").match(/已读 \\d+\\/\\d+/)?.[0] || null,
      marks,
      articleFontSize: getComputedStyle(readerArticle).fontSize
    };
  })()`);
}

async function setFontSize(page, value) {
  await page.locator("header button", { hasText: "设置" }).first().click();
  await page.waitForSelector("aside input[type=range]", { timeout: 5000 });
  await page.evaluate((v) => {
    const input = document.querySelector("aside input[type=range]");
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
    setter.call(input, String(v));
    input.dispatchEvent(new Event("input", { bubbles: true }));
    input.dispatchEvent(new Event("change", { bubbles: true }));
  }, value);
  await page.waitForTimeout(700);
  await page.locator("aside button[title], aside button:has(svg)").first().click();
  await page.waitForTimeout(300);
}

async function selectTextNear(page, matchText) {
  const ok = await page.evaluate((needle) => {
    const article = document.querySelector(".reader-root article");
    const walker = document.createTreeWalker(article, NodeFilter.SHOW_TEXT);
    let node;
    while ((node = walker.nextNode())) {
      const idx = (node.textContent || "").indexOf(needle);
      if (idx >= 0 && node.parentElement?.tagName !== "MARK") {
        const range = document.createRange();
        range.setStart(node, idx);
        range.setEnd(node, Math.min(node.textContent.length, idx + 20));
        const sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
        const scroller = article.parentElement;
        scroller.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));
        return true;
      }
    }
    return false;
  }, matchText);
  await page.waitForTimeout(400);
  return ok;
}

// ---------------------------------------------------------------------------
// 主流程
// ---------------------------------------------------------------------------
checkChunks();

let first = await launch();
await first.app.close();
seed();
const { app, page } = await launch();

try {
  // ================= B/D/A1：验收 TXT 八章 =================
  await openBook(page, "验收 TXT 八章");

  // B4 书签（先滚入第一章区间，锚点激活后书签带章名）
  await scrollToChapterTop(page, "txt-chapter-0", 120);
  await page.locator("header button", { hasText: "加书签" }).first().click();
  await page.waitForTimeout(500);
  await page.locator("button:has-text('书签')").nth(1).click();
  await page.waitForTimeout(300);
  const bookmarkItems = await page.locator("button:has-text('验收章节1')").count();
  check("B4.1 顶栏加书签 → 面板出现书签项", bookmarkItems >= 1, `count=${bookmarkItems}`);

  // B5 高亮：跳到第三章（段落号 41-52）选一段文字加高亮
  await scrollToChapterTop(page, "txt-chapter-2", 80);
  const selOk = await selectTextNear(page, "（45）");
  check("B5.1 选区工具条出现", selOk && (await page.locator('button[title="高亮"]').count()) > 0);
  await page.locator('button[title="高亮"]').first().click();
  await page.waitForTimeout(300);
  await page.locator('button[title="黄色"]').first().click();
  await page.waitForTimeout(600);
  let state = await readerState(page);
  check("B5.2 高亮 mark 注入正文", state.marks >= 1, `marks=${state.marks}`);
  // 跳走再通过面板条目跳回
  await scrollToChapterTop(page, "txt-chapter-0", 0);
  await page.locator("button", { hasText: "高亮" }).first().click();
  await page.waitForTimeout(300);
  await page.locator("button", { hasText: "（45）" }).first().click();
  await page.waitForTimeout(600);
  const jumpBack = await page.evaluate(() => {
    const scroller = document.querySelector(".reader-root article").parentElement;
    const scrollerTop = scroller.getBoundingClientRect().top;
    const el = document.getElementById("txt-chapter-2");
    const elNext = document.getElementById("txt-chapter-3");
    const absTop = (el) => el.getBoundingClientRect().top - scrollerTop + scroller.scrollTop;
    return { t2: absTop(el), t3: elNext ? absTop(elNext) : Infinity, scrollTop: scroller.scrollTop };
  });
  check("B5.3 高亮跳转回到第三章附近", jumpBack.scrollTop >= jumpBack.t2 - 100 && jumpBack.scrollTop < jumpBack.t3, JSON.stringify(jumpBack));

  // D 已读标记：切回目录 Tab，滚到第六章，前五章应已读
  await page.locator("button:has-text('目录')").nth(1).click();
  await page.waitForTimeout(300);
  await scrollToChapterTop(page, "txt-chapter-5", 200);
  state = await readerState(page);
  check("D1 当前章高亮（第六章）", state.currentId === "txt-chapter-5", `current=${state.currentId}`);
  check("D2 已读行弱化 5 行", state.dimRows === 5, `dimRows=${state.dimRows}`);
  check("D3 已读行带对勾 5 个", state.checkRows === 5, `checkRows=${state.checkRows}`);
  check("D4 头部统计「已读 5/8」", state.tocHeaderMatch === "已读 5/8", state.tocHeaderMatch);

  // A1 保存进度：滚到第五章（idx 4）中部后离开（字号默认）
  await scrollToChapterTop(page, "txt-chapter-4", 200);
  await leaveReader(page);

  // ================= A3：无章节 TXT（50% 后离开，字号默认） =================
  await openBook(page, "验收 TXT 无章");
  await page.evaluate(() => {
    const scroller = document.querySelector(".reader-root article").parentElement;
    scroller.scrollTop = Math.round((scroller.scrollHeight - scroller.clientHeight) * 0.5);
    scroller.dispatchEvent(new Event("scroll"));
  });
  await page.waitForTimeout(400);
  await leaveReader(page);

  // ================= 字号变更（制造布局漂移条件） =================
  await openBook(page, "验收 TXT 八章");
  await setFontSize(page, 26);
  const fontSizeNow = (await readerState(page)).articleFontSize;
  check("A0 字号已改为 26px", fontSizeNow === "26px", fontSizeNow);
  await leaveReader(page);

  // ================= A2：MD 保存位置（新字号下） =================
  await openBook(page, "验收 MD 笔记");
  const mdDup = await page.evaluate(() => ({
    d1: Boolean(document.querySelector("h2#讨论")),
    d2: Boolean(document.querySelector("h2#讨论-2"))
  }));
  check("A2.1 MD 重复标题 slug 唯一", mdDup.d1 && mdDup.d2, JSON.stringify(mdDup));
  await scrollToChapterTop(page, "发展", 150);
  await leaveReader(page);

  // ================= C：TXT 目录修正（单次会话内） =================
  await openBook(page, "验收 TXT 修正");
  await page.locator("button", { hasText: "编辑章节" }).first().click();
  await page.waitForTimeout(300);
  // 重命名第一章
  await page.locator("button", { hasText: "重命名" }).first().click();
  await page.locator('input[aria-label="章节标题"]').fill("新标题甲");
  await page.locator("button", { hasText: "确定" }).first().click();
  await page.waitForTimeout(200);
  // 拆分第二章：点其正文第二行
  await page.locator("button", { hasText: "拆分" }).nth(1).click();
  await page.waitForTimeout(200);
  await page.locator("div.grid button:has-text('（122）')").first().click();
  await page.waitForTimeout(200);
  // 合并最后一章
  const rowsBefore = await page.locator("button", { hasText: "合并到上一章" }).count();
  await page.locator("button", { hasText: "合并到上一章" }).last().click();
  await page.waitForTimeout(200);
  const rowsAfter = await page.locator("button", { hasText: "合并到上一章" }).count();
  check("C1 合并到上一章使章节 -1", rowsBefore - rowsAfter === 1, `${rowsBefore}→${rowsAfter}`);
  // 保存
  await page.locator("button", { hasText: "保存并完成" }).first().click();
  await page.waitForTimeout(800);
  state = await readerState(page);
  check("C2 保存后第一章标题已更新", state.h2s[0] === "新标题甲", state.h2s.slice(0, 2).join(" / "));
  check("C3 拆分生效（章数 6-1+1=6）", state.h2s.length === 6, `h2s=${state.h2s.length}`);
  await leaveReader(page);

  // ================= 第二轮：重启验证持久化与锚定恢复 =================
  await app.close();
  const second = await launch();

  // A1 恢复：八章书应恢复到第五章内（布局已变大）
  await openBook(second.page, "验收 TXT 八章");
  await second.page.waitForTimeout(3500);
  state = await readerState(second.page);
  const span = await second.page.evaluate(() => {
    const scroller = document.querySelector(".reader-root article").parentElement;
    const scrollerTop = scroller.getBoundingClientRect().top;
    const absTop = (el) => el.getBoundingClientRect().top - scrollerTop + scroller.scrollTop;
    return { t4: absTop(document.getElementById("txt-chapter-4")), t5: absTop(document.getElementById("txt-chapter-5")) };
  });
  check("A1.1 重启后恢复到第五章（当前章高亮）", state.currentId === "txt-chapter-4", `current=${state.currentId}`);
  check("A1.2 恢复位置落在第五章区间内", state.scrollTop >= span.t4 - 80 && state.scrollTop < span.t5 - 40, `scrollTop=${Math.round(state.scrollTop)} t4=${Math.round(span.t4)} t5=${Math.round(span.t5)}`);

  // A2 MD 恢复到「发展」
  await leaveReader(second.page);
  await openBook(second.page, "验收 MD 笔记");
  await second.page.waitForTimeout(2500);
  const mdState = await second.page.evaluate(() => {
    const readerArticle = document.querySelector(".reader-root article");
    const scroller = readerArticle.parentElement;
    const scrollerTop = scroller.getBoundingClientRect().top;
    const absTop = (el) => el.getBoundingClientRect().top - scrollerTop + scroller.scrollTop;
    const currentRow = document.querySelector('[data-toc-row-id][aria-current="true"]');
    const dev = document.getElementById("发展");
    const talk = document.getElementById("讨论");
    return { currentId: currentRow?.getAttribute("data-toc-row-id"), scrollTop: scroller.scrollTop, tDev: dev ? absTop(dev) : -1, tTalk: talk ? absTop(talk) : Infinity };
  });
  check(
    "A2.2 MD 恢复到「发展」区间",
    mdState.currentId === "发展" || (mdState.scrollTop >= mdState.tDev - 120 && mdState.scrollTop < mdState.tTalk - 40),
    JSON.stringify(mdState)
  );

  // A3 无章节书按比例恢复（±15%）
  await leaveReader(second.page);
  await openBook(second.page, "验收 TXT 无章");
  await second.page.waitForTimeout(1200);
  const plain = await second.page.evaluate(() => {
    const scroller = document.querySelector(".reader-root article").parentElement;
    return (scroller.scrollTop + scroller.clientHeight / 2) / scroller.scrollHeight;
  });
  check("A3.3 无章节书按全局比例恢复（0.5±0.15）", Math.abs(plain - 0.5) <= 0.15, `fraction=${plain.toFixed(3)}`);

  // C 持久化：修正后的标题在重启后仍在
  await leaveReader(second.page);
  await openBook(second.page, "验收 TXT 修正");
  await second.page.waitForTimeout(800);
  const persisted = await readerState(second.page);
  check("C4 重启后目录修正保持", persisted.h2s[0] === "新标题甲", persisted.h2s.slice(0, 2).join(" / "));
  // 恢复自动识别
  await second.page.locator("button", { hasText: "编辑章节" }).first().click();
  await second.page.waitForTimeout(300);
  await second.page.locator("button", { hasText: "恢复自动识别" }).first().click();
  await second.page.waitForTimeout(800);
  const reverted = await readerState(second.page);
  check("C5 恢复自动识别回到启发式目录", reverted.h2s[0] === "第一章 起点", reverted.h2s.slice(0, 2).join(" / "));
  // C6 选区设为章节起点（剪刀）
  await scrollToChapterTop(second.page, "txt-chapter-1", 60);
  const scissorSel = await selectTextNear(second.page, "（121）");
  await second.page.locator('button[title="设为章节起点（目录编辑）"]').first().click();
  await second.page.waitForTimeout(400);
  const editorOpen = await second.page.locator("button", { hasText: "保存并完成" }).count();
  check("C6 选区剪刀进入编辑模式", scissorSel && editorOpen > 0);
  await second.page.locator("button", { hasText: "取消" }).first().click();
  await second.page.waitForTimeout(300);
  const afterCancel = await readerState(second.page);
  check("C7 取消不保存（目录未变）", afterCancel.h2s[0] === "第一章 起点", afterCancel.h2s.slice(0, 2).join(" / "));

  await second.app.close();
} finally {
  await app.close().catch(() => {});
  rmSync(profileDir, { recursive: true, force: true });
}

console.log(`\n[probe-toc-acceptance] ${failures.length === 0 ? "全部通过" : `失败 ${failures.length} 项：${failures.join("；")}`}`);
process.exit(failures.length === 0 ? 0 : 1);
