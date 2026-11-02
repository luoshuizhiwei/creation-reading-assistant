// @vitest-environment jsdom
/**
 * BD-227 账本：WCAG 2.4.2「页面标题」（Level A）。BD-228 把漏掉的资料阅读屏补进来。
 *
 * 主判据是运行时普查 scripts/audit-page-title.mjs（逐屏比对 document.title、系统窗口标题、
 * 这一屏 <h1> 的正身）。本台账管运行时管不到的两件事：
 *   ① 发布点逐处钉住：壳发「这一页是什么」、工作台发「项目名 · 视图」、阅读器发「书名 · 资料阅读」，
 *      三处都必须是 **抄页面正身那份文案**（screenTitles / PROJECT_NAV_ITEMS / activeBook.title），
 *      而不是又写一份新名字——两份标题迟早漂移，运行时判据就只能抓到其中一份；
 *   ② 应用名只许在 window-title.ts 里出现一次：谁再手抄一遍「创作阅读助手」，
 *      尾巴与主题的拼接就会分叉。
 *
 * 修之前实测（/tmp/bd227-baseline.log）：15 屏的 document.title 与系统窗口标题恒等于
 * 「创作阅读助手」，只有 1 种；页面 h1 明明写着「收件箱」「标题普查项目 · 大纲」。
 * BD-228 实测（/tmp/bd228-survey.log）：BD-227 修完之后，从书库点开一本书，doc 与 win
 * 又双双退回「创作阅读助手」——阅读器挂在 App.tsx 单独一条分支上，不经过 <DesktopFrame>，
 * 壳那个发布点根本挂不到它；EPUB 屏更彻底，外层连一个标题元素都没有（正文在 iframe 里）。
 *
 * 路径账（__tests__/ 起算）：../ = src/app/；../../ = src/；../../../ = 仓库根。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const titleModule = read("../window-title.ts");
const frame = read("../../components/layout/DesktopFrame.tsx");
const projectsPage = read("../../features/creation/CreationProjectsPage.tsx");
const txtReader = read("../../features/library/reader/TxtMarkdownReader.tsx");
const epubReader = read("../../features/library/EpubReaderPage.tsx");
const census = read("../../../scripts/audit-page-title.mjs");
const pkg = read("../../../package.json");
const html = read("../../../index.html");
const mainWin = read("../../../electron/main/index.ts");

/** 取某一行（用于把「发布点」这行单独拿出来判，别处的品牌字不误伤） */
const lineWith = (src: string, needle: string) => {
  const line = src.split("\n").find((l) => l.includes(needle));
  expect(line, `找不到含「${needle}」的行`).toBeTruthy();
  return line as string;
};

describe("BD-227 发布点账：标题抄的是页面正身", () => {
  it("壳发这一页的正身名（与 <h1> 同一份文案，不是第二套叫法）", () => {
    expect(frame).toContain('useWindowTitle("screen", title.title);');
    expect(frame).toContain("<h1>{title.title}</h1>");
    // 发布行里不许出现应用名——尾巴只由 window-title.ts 拼
    expect(lineWith(frame, 'useWindowTitle("screen"')).not.toContain("创作阅读助手");
  });

  it("工作台在壳之上细化成「项目名 · 视图」，视图名取导航正身", () => {
    const line = lineWith(projectsPage, 'useWindowTitle("workbench"');
    expect(line).toContain("selected.title");
    expect(line).toContain("PROJECT_NAV_ITEMS.find((item) => item.view === view)?.label");
    // 顺序照工作台页头那行 h1：项目名在前、视图在后；没选中项目时发 null
    expect(line).toContain("`");
    expect(line).not.toContain("创作阅读助手");
    expect(projectsPage).toContain("} · ${PROJECT_NAV_ITEMS.find((item) => item.view === view)?.label ?? \"工作台\"}` : null)");
  });

  it("阅读器自己发（BD-228）：这一屏不经过壳，正身抄页面上那份书名", () => {
    // TXT/MD 侧页头是 <h1>{title}</h1>，title 传的就是 activeBook.title；EPUB 侧工具条是同一份书名
    for (const [src, name] of [[txtReader, "TxtMarkdownReader"], [epubReader, "EpubReaderPage"]] as const) {
      const line = lineWith(src, 'useWindowTitle("reader"');
      expect(line, `${name} 的发布行没带书名`).toContain("activeBook.title");
      expect(line).toContain("资料阅读");
      expect(line).not.toContain("创作阅读助手");
      expect(src).toContain("title={activeBook.title}");
      expect(src).toContain('import { useWindowTitle } from "@/app/window-title";');
    }
    // 没选中书时不许编一句假书名，退回屏名本身（这一屏这时显示的是「没有打开资料」）
    expect(lineWith(txtReader, 'useWindowTitle("reader"')).toContain(': "资料阅读")');
  });

  it("应用名全仓只在 window-title.ts 里拼一次（页头品牌字与初始标题不算发布点）", () => {
    expect(titleModule).toContain('const APP_NAME = "创作阅读助手";');
    expect(titleModule).toContain("document.title = top ? `${top} — ${APP_NAME}` : APP_NAME;");
    // 无 JS / 主进程初始值保持原样：这条 SC 要的是「换屏时标题跟着变」，不是改品牌
    expect(html).toContain("<title>创作阅读助手</title>");
    expect(mainWin).toContain('title: "创作阅读助手",');
  });

  it("owner 语义：后发布者赢、撤回自己就回到上一层（不是「恢复默认」那种手工记账）", () => {
    expect(titleModule).toContain("const subjects = new Map<string, string>();");
    expect(titleModule).toContain("subjects.delete(owner);");
    expect(titleModule).toContain("subjects.set(owner, subject);");
    expect(titleModule).toContain("const top = [...subjects.values()].pop();");
    expect(titleModule).toContain("return () => publishWindowTitle(owner, null);");
  });
});

describe("BD-227 普查骨架：判据没了就是恒真", () => {
  it("npm 脚本已挂上", () => {
    expect(pkg).toContain('"audit:page-title": "node scripts/audit-page-title.mjs"');
  });

  it("五条判据都在，且对着 h1 算（不是对着硬写的期望文案）", () => {
    for (const marker of ["①「", "②「", "③「", "④工作台", "⑤全程"]) {
      expect(census, `判据「${marker}」丢了`).toContain(marker);
    }
    expect(census).toContain("const findings = (rows) => {");
    expect(census).toContain("if (r.h1 && !norm(r.doc).includes(norm(r.h1)))");
    expect(census).toContain("if (r.win !== r.doc)");
    expect(census).toContain("const norm = (s) => (s || \"\").replace(/\\s+/g, \"\");");
  });

  it("三样东西分开取：网页标题、系统窗口标题、页头 h1", () => {
    expect(census).toContain("docTitle: document.title");
    expect(census).toContain("BrowserWindow.getAllWindows()[0].getTitle()");
    expect(census).toContain("document.querySelector(\"h1\")");
  });

  it("自证针走真判据：三份克隆各冻一样东西，冻不住就自证失败", () => {
    expect(census).toContain("const frozenConstant = rows.map((r) => ({ ...r, doc: APP_NAME, win: APP_NAME }));");
    expect(census).toContain("const frozenWindow = rows.map((r) => ({ ...r, win: APP_NAME }));");
    expect(census).toContain('const shelfDoc = (rows.find((r) => r.screen === "书库") || {}).doc || APP_NAME;');
    expect(census).toContain('const readerStale = rows.map((r) => (r.group === "reader" ? { ...r, doc: shelfDoc, win: shelfDoc } : { ...r }));');
    expect(census).toContain("const redsA = findings(frozenConstant);");
    expect(census).toContain("const redsB = findings(frozenWindow);");
    expect(census).toContain("const redsC = findings(readerStale);");
    expect(census).toContain("selfTest.caughtConstant = redsA.some((r) => r.startsWith(\"②\")) && redsA.some((r) => r.startsWith(\"⑤\"));");
    expect(census).toContain("selfTest.caughtWindow = redsB.some((r) => r.startsWith(\"③\"));");
    expect(census).toContain('selfTest.caughtReader = redsC.some((r) => r.startsWith("②") && r.includes("资料阅读"));');
    expect(census).toContain("selfTest.ran = true;");
    expect(census).toContain("reds.push(...selfTest.reds);");
  });

  it("覆盖下限与退出码；导出模式只导现场", () => {
    expect(census).toContain("const SCREEN_MIN = 16;");
    expect(census).toContain("const WORKBENCH_MIN = 5;");
    expect(census).toContain("const READER_MIN = 2;");
    expect(census).toContain('const rdRows = rows.filter((r) => r.group === "reader");');
    expect(census).toContain("process.exit(exitCode);");
    expect(census).toContain("return reds.length ? 1 : 0;");
    expect(census).toContain("TITLE_OK");
    expect(census).toContain("TITLE_FAIL");
    expect(census).toContain('if (process.env.BD227_DUMP === "1") {');
    expect(census).toContain("BD227_DUMP 完成：采样 ${rows.length} 屏（未做判红，仅导出现场）。");
    // 记档要在导出模式之前就打印：上一版把它放在 DUMP 之后，针没咬到东西时现场一片安静
    expect(census.indexOf("for (const n of notes)")).toBeLessThan(census.indexOf('if (process.env.BD227_DUMP === "1") {'));
  });

  it("走查含工作台七个视图与退出后的项目列表——④这条判据全靠这几步", () => {
    for (const v of ["概览", "写作", "大纲", "全书预览", "设定卡", "写作统计", "版本历史"]) {
      expect(census, `视图「${v}」不在走查名单里`).toContain(v);
    }
    expect(census).toContain('for (const v of ["概览", "写作", "大纲", "全书预览", "设定卡", "写作统计", "版本历史"])');
    expect(census).toContain('await nav("项目列表", "项目·列表", "shell");');
    expect(census).toContain('const nav = async (name, screen, group = "workbench") => {');
    expect(census).toContain("没点到工作台导航");
  });

  it("资料阅读那一腿（BD-228）：书目从书库那张表上读，不写死", () => {
    expect(census).toContain('const SHELF = \'[role="table"][aria-label="书库书籍"]\';');
    expect(census).toContain("const titleLoc = page.locator(`${SHELF} h2.paper-title`);");
    // 书名必须在点开任何一本之前收齐——开书会改「最近阅读」，表的排序跟着变
    expect(census).toContain("for (let i = 0, n = Math.min(await titleLoc.count(), 3); i < n; i++)");
    expect(census).toContain("if (name && !shelfNames.includes(name)) shelfNames.push(name);");
    expect(census).toContain("await snap(`资料阅读·${name}`, \"reader\", name);");
    // 打开按钮那行的书名必须用「」包起来——本批第一版手滑打成 ””，选择器静默失配，
    // 整条腿没跑、现场只报「采样 14 屏」；现在下限 16、记档先打印、自证针第三枚一起盯着。
    expect(lineWith(census, "打开「")).toMatch(/打开「\$\{name[^}]*\}」"]/);
    expect(census).toContain("没点到「${name}」的打开按钮——资料阅读屏少扫一本");
    expect(census).toContain('const back = page.locator(\'button[aria-label="返回书库"], button:has-text("返回书库")\').first();');
    // 退出阅读后回到壳那一层，标题不许还挂着书名——owner 撤回这条语义只有这一处能实测到
    expect(census).toContain("await snap(`书库·退出「${name}」后`, \"shell\");");
    for (const nm of ["测试 Markdown 笔记", "测试 TXT 资料", "测试 EPUB 资料"]) {
      expect(census, `普查里写死了书目「${nm}」——换一份种子就整条腿空转`).not.toContain(nm);
    }
  });

  it("取不到 h1 的屏：正身退回书页上的书名，并且记档说明它不是 h1", () => {
    expect(census).toContain("const snap = async (screen, group, subject) => {");
    expect(census).toContain('rows.push({ screen, group, doc: doc.docTitle, win: winTitle, h1: doc.h1 || subject || "" });');
    expect(census).toContain("正身改用书页上读到的书名");
  });

  it("弹层那一屏仍按底下页面的标题判（浮层不另立主题）", () => {
    expect(census).toContain('await snap("弹层·新建向导", "shell");');
  });
});
