// @vitest-environment jsdom
/**
 * BD-213 账本：WCAG 2.5.3 Label in Name（AA）。
 *
 * 主判据是运行时普查 scripts/audit-label-in-name.mjs：14 个页面级视图，逐控件比对
 * 「可见标签文本」是否是可访问名的子串（忽略大小写/空白）。可见标签剔除补充信息——
 * <small> 副标题、<em> 角标、印章字形、项目卡的时间/字数、收件箱摘要、纯符号字形——
 * 只留真正要念出来的字。
 *
 * 本批实测到的真实缺陷（普查先红后绿）：
 *  · 工作台四个包操作：可见「导出包 / 加密导出 / 导入包 / 加密导入」，名字却是
 *    「导出项目包 / 导出加密项目包 / 导入项目包 / 导入加密项目包」——中间插字，念不对；
 *  · 顶栏搜索：可见「搜索项目、收件箱、资料 Ctrl K」，名字只有「打开全局搜索（Ctrl K）」。
 *
 * BD-226（WCAG 3.2.4 一致识别）后来改了其中一颗的写法：头部的「导入包」可见字与名字都换成
 * 全称「导入项目包」，因为项目·首页那颗同名动作一直叫全称——3.2.4 要求同功能同名字，
 * 而 2.5.3 只要求可见字进名字，换成全称后依然成立。本账本里那两行锁已随之改写。
 *
 * BD-231（本账本第二段）给普查补上了第二条腿：老判据「逐个文本节点」对 <input>/<textarea>/
 * <select> 永远量不到——它们屏上的字在 placeholder 属性里，不在子节点里，labelText 返回空串
 * 就 continue，等于整族静默跳过。范围也从 14 涨到 18（多了命令面板、写作速查全局搜索、
 * 关联卡片弹层、卡片类型弹层四个输入框密集区）。
 *
 * 实测基线（改前 /tmp/bd231-label-after.log，1360 窗 xvfb）：输入腿 12 个控件里 8 处红——
 *  · 收件箱速记框：可见主干「记下灵感碎片」，名字「灵感速记」（同义改写）；
 *  · 书库筛选框：可见主干「筛选书名、作者或导入标签」，名字「筛选书籍」；
 *  · 命令面板：可见主干「输入命令或搜索项目 / 场景」，名字「筛选命令」；
 *  · 设定卡检索：可见「搜索名称或别名」，名字「搜索卡片名称或别名」（中间插字）；
 *  · 卡片库检索：可见「搜索名称、别名或字段」，名字「搜索卡片名称、别名或字段」（同上）；
 *  · 写作速查全局搜索：可见「搜索名称、别名或字段」，名字「搜索卡片」；
 *  · 关联卡片弹层：可见「搜索名称、别名或字段」，名字「搜索全局卡片」；
 *  · 还有一颗「搜索设置项...」起初也报红——占位字用的是三个英文句点而不是省略号，
 *    主干切分只认「…」不认「...」，属于判据自己的缺口，补进 stem 后转绿（不是产品缺陷）。
 * 改后（/tmp/bd231-label-after4.log）：18 范围、按钮腿 250 + 输入腿 14，红 0 处。
 *
 * 牙齿账（三颗，各自只造成点名那一处红，还原后 sha256 逐字节相同、重建复绿）：
 *  · T-A（/tmp/bd231-ta.log）把书库占位字退回旧写法 → 红 1：[书库] 可见「筛选书名、作者或导入标签」
 *    名「筛选书籍」——证明 ② 分支（placeholder 主干）真的在量；
 *  · T-B（/tmp/bd231-tb.log）把卡片类型那颗的可见 <label> 改成「作品类型」而名字不动 →
 *    红 1：[弹层·卡片类型] 可见标签取自 label——证明 ① 分支（同宿主可见 label）真的在量；
 *  · T-C（/tmp/bd231-tc.log）把速记框占位字改成「记下灵感碎片，灵感速记」（提示语挪到主干位）→
 *    红 1：[收件箱]——证明取的是「主干」，把名字藏到第二段照样抓。
 *
 * 路径账（__tests__/ 起算）：../../../../ = 仓库根；../ = layout/；../../../features = src/features/。
 */
import React from "react";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { DesktopFrame } from "@/components/layout/DesktopFrame";
import { useAppStore } from "@/stores/app-store";
import { useSearchStore } from "@/stores/search-store";
import { useInspirationStore } from "@/stores/inspiration-store";
import { useCreationStore } from "@/stores/creation-store";
import { useLibraryStore } from "@/stores/library-store";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const census = read("../../../../scripts/audit-label-in-name.mjs");
const frame = read("../DesktopFrame.tsx");
const projectsPage = read("../../../features/creation/CreationProjectsPage.tsx");
const pkg = read("../../../../package.json");

function resetStores(): void {
  useAppStore.setState({ screen: "projects", previousScreen: undefined, errors: [], creationFocusMode: false });
  useSearchStore.setState({ open: false });
  useInspirationStore.setState({ items: [] });
  useCreationStore.setState({ projects: [], cards: [], selectedCardId: undefined, selectedId: undefined });
  useLibraryStore.setState({ books: [], progress: {} });
}

beforeEach(() => resetStores());
afterEach(() => cleanup());

describe("BD-213 普查判据：可见标签必须进入可访问名", () => {
  it("红/绿结论、退出码与判定腿都在", () => {
    expect(census).toContain("LABEL_RED");
    expect(census).toContain("LABEL_IN_NAME_OK");
    expect(census).toContain("process.exit(1)");
    expect(census).toContain("if (norm(name).includes(norm(label))) continue;");
    expect(census).toContain("judged += 1;");
  });

  it("可见标签剔除补充信息：副标题、角标、印章字形、元数据、纯符号", () => {
    expect(census).toContain("svg, small, em, .desktop-brand-mark, .project-home-meta, .project-home-progress, .inbox-item-meta, .inbox-item-body, [role=\"progressbar\"], [aria-hidden=\"true\"]");
    expect(census).toContain("const hasWord = (t) => /[\\p{L}\\p{N}]/u.test(t);");
  });

  it("14 个范围与 npm 脚本", () => {
    for (const label of ["项目·首页", "项目·写作台", "项目·设定卡", "项目·设定卡（列表）", "书库", "卡片库", "收件箱", "资料阅读", "设置"]) {
      expect(census).toContain(`["${label}"`);
    }
    expect(pkg).toContain('"audit:label-in-name": "node scripts/audit-label-in-name.mjs"');
  });
});

describe("BD-213 产品锚：可见字进了名字", () => {
  it("顶栏搜索的可见提示就是名字的一部分（真实渲染）", () => {
    render(<DesktopFrame><div /></DesktopFrame>);
    const search = screen.getByRole("button", { name: /搜索项目、收件箱、资料/ });
    expect(search.getAttribute("aria-label")).toBe("打开全局搜索：搜索项目、收件箱、资料 Ctrl K");
    const visible = Array.from(search.querySelectorAll("span, kbd")).map((el) => el.textContent?.trim()).join(" ");
    expect(search.getAttribute("aria-label")).toContain(visible);
  });

  it("包操作的名字与可见文字逐字对齐，全称留在 title 里", () => {
    for (const [visible, full] of [["导出包", "导出项目包"], ["加密导出", "导出加密项目包"], ["加密导入", "导入加密项目包"]]) {
      expect(projectsPage).toContain(`aria-label="${visible}" title="${full}"`);
    }
    expect(projectsPage).not.toContain('aria-label="导出项目包"');
    expect(projectsPage).not.toContain('aria-label="导出加密项目包"');
    expect(projectsPage).not.toContain('aria-label="导入加密项目包"');
    // BD-226（WCAG 3.2.4）：导入这颗换了写法——同一个动作不许在项目·首页叫「导入项目包」、
    // 在头部叫「导入包」。短名退场后 2.5.3 仍然成立：可见字「导入项目包」就是可访问名本身。
    expect(projectsPage).toContain('aria-label="导入项目包" title="导入项目包"');
    expect(projectsPage).not.toContain('aria-label="导入包"');
    expect(projectsPage).not.toContain("<span>导入包</span>");
  });

  it("副标题/角标不进可见标签口径（否则导航与项目卡会被误判）", () => {
    expect(frame).toContain("<strong>{item.label}</strong>");
    expect(frame).toContain("<small>{item.hint}</small>");
  });
});

describe("BD-231 输入腿判据：placeholder 是可见标签时，主干必须就是名字", () => {
  it("第二条腿的取法在：可见 label 优先，其次 placeholder 主干，两者都无就不参与", () => {
    expect(census).toContain('for (const el of document.querySelectorAll("input, textarea, select"))');
    expect(census).toContain('el.closest("label") || (el.parentElement ? el.parentElement.querySelector(":scope > label") : null)');
    // ① 分支读 label 自己的可见文字时要跳过控件本身的文本（包裹式 label 里控件与文字同树）
    expect(census).toContain("const labText = lab ? labelText(lab, el) : \"\";");
    expect(census).toContain("if (skip && skip.contains(n)) continue;");
    // ② 分支：主干切分认中文标点、省略号（「…」与三个英文句点都算），以及「空格+左括号」的键帽提示
    expect(census).toContain('const stem = (s) => ((s || "").split(/(?:\\.\\.\\.|…|[，。；：（(]| \\()/)[0] || "").trim();');
    expect(census).toContain("const source = labText || stem(ph);");
    // ③ 分支：没有 aria-label/labelledby 就不参与（名字本就由 placeholder 算出来，天然一致）
    expect(census).toContain('if (!name) continue;\n    const lab = el.closest("label")');
    // 红的行要写清可见标签是从哪一支取来的，否则 T-B 那种「label 改了名字没改」没人能复核
    expect(census).toContain('from: labText ? "label" : "placeholder"');
    expect(census).toContain("可见标签取自");
  });

  it("输入腿的账进总结行，且判据不靠任何一条产品字面量", () => {
    expect(census).toContain("输入腿 BD-231");
    expect(census).toContain("sourceJudged += r.sourceJudged;");
    // 普查里不许把某颗框的占位字硬编码成判据（头注里记的那几处只是案底）
    expect(census).not.toContain("筛选书籍：");
    expect(census).not.toContain("灵感速记：");
    expect(census).not.toContain("搜索全局卡片：");
  });

  it("四个新范围在（输入框密集的浮层/弹层）", () => {
    for (const label of ["浮层·命令面板", "浮层·写作速查全局搜索", "弹层·关联卡片", "弹层·卡片类型"]) {
      expect(census).toContain(`["${label}"`);
    }
    // 三个入口各自的打开方式（浮层会留遮罩，goto/workbenchView 里那句 Esc 收尾见普查头注）
    expect(census).toContain('await page.keyboard.press("Control+p");');
    expect(census).toContain('await page.keyboard.press("Control+Shift+k");');
    expect(census).toContain('button:has-text("关联卡片")');
    expect(census).toContain('button:has-text("卡片类型")');
  });

  it("七颗真缺陷的产品锚：占位字的主干就是可访问名本身", () => {
    const pairs: [string, string, string][] = [
      ["../../../features/creation/inbox/components/InboxQuickInput.tsx", "灵感速记", "灵感速记：记下灵感碎片，回车速记…"],
      ["../../../features/library/LibraryPage.tsx", "筛选书籍", "筛选书籍：书名、作者或导入标签…"],
      ["../../../features/creation/command/CommandPalette.tsx", "筛选命令", "筛选命令：输入命令或搜索项目 / 场景…"],
      ["../../../features/creation/cards/CardsPage.tsx", "搜索卡片名称或别名", "搜索卡片名称或别名"],
      ["../../../features/creation/cards/GlobalCardLibraryPage.tsx", "搜索卡片名称、别名或字段", "搜索卡片名称、别名或字段"],
      ["../../../features/creation/editor/WritingQuickReferencePanel.tsx", "搜索卡片", "搜索卡片：名称、别名或字段"],
      ["../../../features/creation/cards/ProjectCardLinkDialog.tsx", "搜索全局卡片", "搜索全局卡片"],
    ];
    for (const [rel, name, ph] of pairs) {
      const src = read(rel);
      expect(src).toContain(`aria-label="${name}"`);
      expect(src).toContain(`placeholder="${ph}"`);
      // 主干 == 名字：切到第一个分隔标点之前正好是可访问名，语音操控照屏念必然命中
      expect(ph.startsWith(name)).toBe(true);
    }
  });

  it("关联卡片那颗为什么收短到只剩名字：180px 内宽塞不下「名字：范围」两段式", () => {
    // 实拍（/tmp/bd231-adjudicate-after.log）：「搜索全局卡片：名称、别名或字段」195px > 可用 180px
    // ⇒ 占位字被裁。同族的速查框有 258px，两段式放得下；这颗只能收短。
    const dialog = read("../../../features/creation/cards/ProjectCardLinkDialog.tsx");
    expect(dialog).toContain('placeholder="搜索全局卡片" aria-label="搜索全局卡片"');
    expect(dialog).not.toContain('placeholder="搜索全局卡片：');
  });

  it("反向锁：三族合法形状不许被「顺手改名」", () => {
    // ① 分支的活体宿主——可见 label 与名字逐字相同，placeholder 只是示例（T-B 就是在这颗上打的牙）
    const typeEditor = read("../../../features/creation/cards/CardTypeEditor.tsx");
    expect(typeEditor).toContain('<label className="cards-field-label">类型名称');
    expect(typeEditor).toContain('aria-label="类型名称"');
    expect(typeEditor).toContain('placeholder="例如：功法、神兵"');
    // ③ 分支：列表侧栏那颗检索框没有 aria-label，名字由 placeholder 自己算出——改它等于凭空造分叉
    const sidebar = read("../../../features/creation/cards/components/CardListSidebar.tsx");
    expect(sidebar).toContain('placeholder="搜索名称或别名"');
    expect(sidebar).not.toContain('aria-label="搜索名称或别名"');
    // 动态 placeholder 是「要照着打的值」不是标签（回收站确认框把待删对象名当占位字）
    const purge = read("../../../features/creation/history/PurgeTrashDialog.tsx");
    expect(purge).toContain("placeholder={item.title}");
    // 主干只是名字前缀的合法形状：可见「搜索正文」+ 键帽提示，名字「搜索正文关键词」把它包住就行
    const readerSearch = read("../../../features/library/reader/components/ReaderSearchOverlay.tsx");
    expect(readerSearch).toContain('placeholder="搜索正文 (Enter 下一处)..."');
    expect(readerSearch).toContain('aria-label="搜索正文关键词"');
  });

  it("速记框的名字不许改：BD-230 的标题普查按它定位宿主", () => {
    // scripts/audit-heading-order.mjs 用 input[aria-label="灵感速记"] 造「条目正文」h2 宿主；
    // BD-231 只动可见占位字，名字保持「灵感速记」——两边才都不用跟着改。
    expect(read("../../../features/creation/inbox/components/InboxQuickInput.tsx")).toContain('aria-label="灵感速记"');
    expect(census).toContain('["收件箱"');
  });
});
