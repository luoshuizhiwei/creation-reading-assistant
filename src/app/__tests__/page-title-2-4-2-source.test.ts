// @vitest-environment jsdom
/**
 * BD-227 账本：WCAG 2.4.2「页面标题」（Level A）。
 *
 * 主判据是运行时普查 scripts/audit-page-title.mjs（逐屏比对 document.title、系统窗口标题、
 * 这一屏 <h1> 的正身）。本台账管运行时管不到的两件事：
 *   ① 发布点逐处钉住：壳发「这一页是什么」、工作台发「项目名 · 视图」，两处都必须是
 *      **抄页面正身那份文案**（screenTitles / PROJECT_NAV_ITEMS），而不是又写一份新名字——
 *      两份标题迟早漂移，运行时判据就只能抓到其中一份；
 *   ② 应用名只许在 window-title.ts 里出现一次：谁再手抄一遍「创作阅读助手」，
 *      尾巴与主题的拼接就会分叉。
 *
 * 修之前实测（/tmp/bd227-baseline.log）：15 屏的 document.title 与系统窗口标题恒等于
 * 「创作阅读助手」，只有 1 种；页面 h1 明明写着「收件箱」「标题普查项目 · 大纲」。
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

  it("自证针走真判据：两份克隆各冻一样东西，冻不住就自证失败", () => {
    expect(census).toContain("const frozenConstant = rows.map((r) => ({ ...r, doc: APP_NAME, win: APP_NAME }));");
    expect(census).toContain("const frozenWindow = rows.map((r) => ({ ...r, win: APP_NAME }));");
    expect(census).toContain("const redsA = findings(frozenConstant);");
    expect(census).toContain("const redsB = findings(frozenWindow);");
    expect(census).toContain("selfTest.caughtConstant = redsA.some((r) => r.startsWith(\"②\")) && redsA.some((r) => r.startsWith(\"⑤\"));");
    expect(census).toContain("selfTest.caughtWindow = redsB.some((r) => r.startsWith(\"③\"));");
    expect(census).toContain("selfTest.ran = true;");
    expect(census).toContain("reds.push(...selfTest.reds);");
  });

  it("覆盖下限与退出码；导出模式只导现场", () => {
    expect(census).toContain("const SCREEN_MIN = 13;");
    expect(census).toContain("const WORKBENCH_MIN = 5;");
    expect(census).toContain("process.exit(exitCode);");
    expect(census).toContain("return reds.length ? 1 : 0;");
    expect(census).toContain("TITLE_OK");
    expect(census).toContain("TITLE_FAIL");
    expect(census).toContain('if (process.env.BD227_DUMP === "1") {');
    expect(census).toContain("BD227_DUMP 完成：采样 ${rows.length} 屏（未做判红，仅导出现场）。");
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

  it("弹层那一屏仍按底下页面的标题判（浮层不另立主题）", () => {
    expect(census).toContain('await snap("弹层·新建向导", "shell");');
  });
});
