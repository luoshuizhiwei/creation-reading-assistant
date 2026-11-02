// @vitest-environment jsdom
/**
 * BD-229 账本：WCAG 1.3.1「信息与关系」落到标题骨架（G130/H13）——一屏只许一个页级 h1。
 *
 * 主判据是运行时普查 scripts/audit-heading-one-h1.mjs（npm run audit:headings）：逐屏数壳侧 h1、
 * 比对它与窗口标题正身是否同一句话，正文区（书自己写的章标题）只记档不计入。
 * 本台账管运行时管不到的两件事：
 *   ① 三处改动逐处钉住——两处「页面自己又摆一个 h1」必须降成 h2、一处「这一屏根本没有标题元素」
 *      必须补上 h1；文案与档口（paper-title text-2xl / text-sm）一个字都不许动，
 *      因为这一批改的是语义不是像素（像素账见下）；
 *   ② 壳那个 h1 仍是唯一的页级正身来源（BD-227 的窗口标题抄的就是它），
 *      谁把它改了名字或删掉，②那条判据在运行时就会咬，但「不许再出现第二个」这件事得有人在源码里盯着。
 *
 * 实测基线（/tmp/bd229-survey.log、/tmp/bd229-survey2.log、/tmp/bd229-epub-before.log）：
 *   [书库] 壳侧 h1=2：书库〔无 class，命令条〕＋ 书库〔paper-title，页面自己的〕
 *   [阅读统计] 一屏两个页级标题、还是两套名字：阅读统计 ＋ 阅读与沉淀节律
 *   [资料阅读·EPUB] h1count=0——正文在 iframe 里，外层一个标题元素都没有
 * 像素账（/tmp/bd229-style-before.log ↔ -after.log、/tmp/bd229-epub-before.log ↔ -after.log）：
 *   去掉 tag 字段后逐字节相同（fs/lh/fw/margin/宽高/面板高/偏移全等）；
 *   EPUB 那处除 tag 与 h1count（0→1）外同样逐字段相同。Tailwind preflight 把所有标题档
 *   重置成 inherit/0，所以改 tag 不动样式——这是实测出来的，不是推断出来的。
 *
 * 路径账（__tests__/ 起算）：../ = src/app/；../../ = src/；../../../ = 仓库根。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const frame = read("../../components/layout/DesktopFrame.tsx");
const libraryPage = read("../../features/library/LibraryPage.tsx");
const statsPage = read("../../features/library/ReadingStatsPage.tsx");
const epubBar = read("../../features/library/epub-reader/EpubReaderToolbar.tsx");
const census = read("../../../scripts/audit-heading-one-h1.mjs");
const pkg = read("../../../package.json");

/** 取某一行（把要判的那行单独拿出来，别处的同字不误伤） */
const lineWith = (src: string, needle: string) => {
  const line = src.split("\n").find((l) => l.includes(needle));
  expect(line, `找不到含「${needle}」的行`).toBeTruthy();
  return line as string;
};

describe("BD-229 结构账：一屏一个页级标题", () => {
  it("书库：页面自己那个 h1 降成 h2，文案与档口原样", () => {
    expect(libraryPage).not.toMatch(/<h1[\s>]/);
    expect(libraryPage).toContain('<h2 className="paper-title text-2xl font-semibold">书库</h2>');
    expect(lineWith(libraryPage, 'text-2xl font-semibold">书库').trim().startsWith("<h2")).toBe(true);
    expect(libraryPage).toContain("BD-229（WCAG 1.3.1 / G130）");
  });

  it("阅读统计：编辑名留给 h2 档，页级名字只有壳那一个", () => {
    expect(statsPage).not.toMatch(/<h1[\s>]/);
    expect(statsPage).toContain('<h2 className="paper-title text-2xl font-semibold">阅读与沉淀节律</h2>');
    // 两套名字并存正是这一处最坏的地方：h2 里保留编辑名，h1 位置不许再有第二个
    expect(statsPage).not.toContain('<h1 className="paper-title text-2xl font-semibold">阅读与沉淀节律');
  });

  it("EPUB 工具条：这一屏的正身（书名）拿到标题语义", () => {
    expect(epubBar).toContain('<h1 className="truncate text-sm font-semibold text-paper-ink">{title}</h1>');
    expect(epubBar).not.toContain('<div className="truncate text-sm font-semibold text-paper-ink">{title}</div>');
  });

  it("壳那一条仍是唯一的页级正身（BD-227 的窗口标题抄的就是它）", () => {
    expect(frame).toContain("<h1>{title.title}</h1>");
  });
});

describe("BD-229 普查骨架：判据没了就是恒真", () => {
  it("npm 脚本已挂上", () => {
    expect(pkg).toContain('"audit:headings": "node scripts/audit-heading-one-h1.mjs"');
  });

  it("三条判据都在，并且把正文区与壳侧分开数", () => {
    for (const marker of ["①「", "②「", "③全程"]) {
      expect(census, `判据「${marker}」丢了`).toContain(marker);
    }
    expect(census).toContain("const findings = (rows) => {");
    expect(census).toContain("if (r.chromeH1.length === 0)");
    expect(census).toContain("if (r.chromeH1.length > 1)");
    expect(census).toContain("!norm(body).includes(norm(r.chromeH1[0]))");
    // 正文区（书自己的章标题）不计入页级，但要记档
    expect(census).toContain('const isContent = (h) => !!h.closest(".reader-scroller, article");');
    expect(census).toContain("正文区另有 ${r.contentH1.length} 个 h1");
  });

  it("自证针三枚走真判据，针体跑完才置 ran", () => {
    expect(census).toContain('const dupH1 = rows.map((r) => ({ ...r, chromeH1: [...r.chromeH1, "再来一个同名页级标题"] }));');
    expect(census).toContain("const emptyH1 = rows.map((r) => ({ ...r, chromeH1: [] }));");
    expect(census).toContain('const alienH1 = rows.map((r) => ({ ...r, chromeH1: ["与本屏无关的一个名字"] }));');
    expect(census).toContain("selfTest.caughtDup = redsDup.some((x) => x.startsWith(\"①\") && x.includes(\"再来一个同名页级标题\"));");
    expect(census).toContain('selfTest.caughtEmpty = redsEmpty.some((x) => x.startsWith("①") && x.includes("没有页级 h1"));');
    expect(census).toContain('selfTest.caughtAlien = redsAlien.some((x) => x.startsWith("②")) && redsAlien.some((x) => x.startsWith("③"));');
    expect(census).toContain("selfTest.ran = true;");
    expect(census).toContain("reds.push(...selfTest.reds);");
    expect(census).toContain('if (!selfTest.ran) selfTest.reds.push("自证失败：自证针没跑完');
  });

  it("必到屏与下限：本批改到的屏必须真的被扫到（EPUB 那处靠书目那条腿）", () => {
    expect(census).toContain('const MANDATORY = ["项目", "收件箱", "卡片库", "设置", "书库", "阅读统计"];');
    expect(census).toContain('if (!seen.has(m)) reds.push(`必到屏「${m}」没被扫到');
    expect(census).toContain("const SCREEN_MIN = 15;");
    expect(census).toContain("const WORKBENCH_MIN = 5;");
    // 阅读统计没有侧栏入口，只能从书库那颗按钮进——这一条腿是它唯一的覆盖
    expect(census).toContain("page.locator(\"button:has-text('阅读统计')\")");
    expect(census).toContain('await snap("阅读统计", "shell");');
    // 回书库只是为了打开书，不重复记一行：判红要按屏数，同一屏扫两趟就会把一条缺陷报成两条
    expect(census).toContain('await goto("书库");');
    expect(census).toContain("const goto = async (name) => {");
  });

  it("书目不写死、退出码与导出模式", () => {
    expect(census).toContain("const titleLoc = page.locator(`${SHELF} h2.paper-title`);");
    for (const nm of ["测试 Markdown 笔记", "测试 TXT 资料", "测试 EPUB 资料"]) {
      expect(census, `普查里写死了书目「${nm}」`).not.toContain(nm);
    }
    expect(census).toContain("process.exit(exitCode);");
    expect(census).toContain("return reds.length ? 1 : 0;");
    expect(census).toContain("HEADING_OK");
    expect(census).toContain("HEADING_FAIL");
    expect(census).toContain('if (process.env.BD229_DUMP === "1") {');
    expect(census).toContain("BD229_DUMP 完成：采样 ${rows.length} 屏（未做判红，仅导出现场）。");
    expect(census).toContain("for (const n of notes) console.log(\"  记档 \" + n);");
  });

  it("走查含工作台七个视图与退出后的项目列表（与 BD-227 同一张地图）", () => {
    expect(census).toContain('for (const v of ["概览", "写作", "大纲", "全书预览", "设定卡", "写作统计", "版本历史"])');
    expect(census).toContain('await nav("项目列表", "项目·列表", "shell");');
  });
});
