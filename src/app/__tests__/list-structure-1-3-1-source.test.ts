// @vitest-environment jsdom
/**
 * BD-234 账本：WCAG 1.3.1「结构与关系」里**缩进与容器在替 DOM 说话**的那一族。
 *
 * 主判据是运行时普查 scripts/audit-list-structure.mjs（npm run audit:list-structure），
 * 16 屏逐个问「此刻这处关系真的只靠视觉承载吗」。本台账管运行时管不到的三件事：
 *   ① 三处改动的**类名与声明一个字不许动**——这批只换语义标签，任何顺手"整理"版式的人
 *      都会撞这里的像素账（改前/改后几何逐字相同的读数记在下面「实测基线」）；
 *   ② 两处**有意不做**的决定必须钉住：卡板列不改成 ul/li（列内每项是 role=button 的交互
 *      项，li 一挂 role=button 就把 listitem 语义吃掉，正是 ③ 那处缺陷的形状；而且
 *      editorial-studio.css:2881 那族 `.cards-board-card:nth-child(n)` 入场动画依赖卡片的
 *      子位置）；类名写着 -list/-children/-items 的容器不单独成枪（量下来全仓 0 处残留，
 *      做成第五条枪会把「视觉上不是一列」的堆叠误判成列表）；
 *   ③ 四条判据的**阈值本身**不许被稀释（D 的 8px 门槛与「盒子左缘齐平」条件、C 只对
 *      没有 role 属性的 ul/ol 开火、按钮行排除）——这些是判据的牙，运行时普查只在缺牙时
 *      才报得出来。
 *
 * 实测基线（改前：/tmp/bd234-survey6-before.log 12 屏清单 + 逐屏几何；改后：
 *   /tmp/bd234-survey6-after.log 与 /tmp/bd234-census2.log，1360×900 活体、16 屏全过）：
 *   改前三处现场——
 *     · 大纲三级：`.outline-tree-scroll`/`.outline-chapter-list`/``.outline-scene-list` 全是 div，
 *       条目 div.outline-volume / div.outline-chapter / div.outline-scene，空态是 span；
 *       几何：卷行 x=98 / 章行 x=110 / 场行 x=122（那 12px×2 的位移 = 当时唯一的关系载体，
 *       styles.css 自己的注释写着「真正承载层级的是缩进」）；
 *     · 卡板列：8 张 `section.cards-board-column` 列名全 ∅（角色/地点/组织/物品/世界规则/
 *       情节事件/伏笔线索/资料）；
 *     · 概览：`ul.overview-recent-list` 的条目是 `<li role="button" tabindex=0>` ⇒
 *       listitem 语义被覆盖，列表在无障碍树里是空的。
 *   改后：A 三条容器 ul/条目 li、B 八列齐名（labelledby=cards-board-column-title-<kind>）、
 *     C 0 条、D 0 条 ⇒ LIST_STRUCTURE_OK。
 *   立项证据复量（这版普查落地后拿 HEAD 那份产品源码重跑，日志 /tmp/bd234-prefix-check.log）：
 *     四个文件换回改前 → build → 16/16 屏命中 19 条（A 8 = 写作台三级容器+卷/章/场条目 6、大纲
 *     tree-scroll 容器+卷条目 2；B 8 = 八列列名全 ∅；C 1 = 概览 ul 条目全被 role=button 覆盖；
 *     D 2 = 章/场列表的 padding-left:12px 对着卷行/章行）⇒ LIST_STRUCTURE_RED；按 sha256 逐字节
 *     还原（ALL_RESTORE_IDENTICAL）→ 重建 → 复验 0 条。四条枪对**原始缺陷形状**全部咬得住。
 *   像素账（同一走查两趟：/tmp/bd234-geo-before.log ↔ /tmp/bd234-geo-after.log）：
 *     div→ul / div→li 之后三行几何逐字相同（x/y/w/h/padding/margin/display 全等，只有
 *     list-style 由 disc 变 none——preflight 对 ul 的复位，而这三行 display:flex 本就不画
 *     marker）；概览那行由 `li[role=button]` 改成 `li > button` 后盒宽高与三列轨道逐字相同
 *     （声明一字未改，整条从 `.overview-recent-item` 下沉到 `.overview-recent-item > button`）。
 *
 * 牙齿账（落地后当场造回归，实测见 /tmp/bd234-ta.log、/tmp/bd234-teeth.log；每次都还原到
 *   注入前的 sha（RESTORE_IDENTICAL），还原后重建再复验一遍绿——四趟全部复验通过）：
 *   T-A 只让缺陷复发（把「场」条目 li 换回 div）不摘判据 ⇒ 实测 census exit=1、16/16 屏全扫、
 *     红 1 条正好落在 A（`.outline-scene-list 里 1/1 个 场 条目是 div，不是 li`），
 *     账本 2 败/13 通过（「三级容器全是 ul」正向锁 + 「div 条目不许回来」反向锁）。
 *   T-B 摘掉 A 的条目判据（items.filter(() => false)）+ 让同一缺陷复发 ⇒ 实测 census exit=0、
 *     16/16 屏、0 条 LIST_STRUCTURE_OK —— 那一枪确实是判据在守，不是「别处也咬到所以显绿」；
 *     账本 3 败/12 通过（两条大纲锁 + 「四条枪都带得上手的判据串」的缺牙锁）。
 *   T-C 卡板列摘名（role=group + aria-labelledby 两行撤掉）⇒ 实测 census exit=1、红 8 条全是 B
 *     （角色/地点/组织/物品/世界规则/情节事件/伏笔线索/资料 八列各一条"没有可访问名"），
 *     账本 1 败/14 通过。
 *   T-D 概览条目挂回 tabIndex=0 + role="button" ⇒ 实测 census exit=1、红 1 条落在 C
 *     （`ul.overview-recent-list` 的全部 1 个条目都带非 listitem 的 role（button）），
 *     账本 1 败/14 通过（「li 上不许有 role=button」反向锁）。
 *
 * 路径账（__tests__/ 起算）：../ = src/app/；../../ = src/；../../../ = 仓库根。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const census = read("../../../scripts/audit-list-structure.mjs");
const pkg = read("../../../package.json");
const outline = read("../../features/creation/outline/OutlineTree.tsx");
const board = read("../../features/creation/cards/BoardView.tsx");
const overview = read("../../features/creation/overview/OverviewPage.tsx");
const overviewCss = read("../../features/creation/overview/overview-local.css");
const styles = read("../../../src/styles.css");
const editorial = read("../../../src/styles/editorial-studio.css");

const count = (hay: string, needle: string) => hay.split(needle).length - 1;
/** 取某条选择器的声明块正文（第一处命中），注释会被留下，故调用方只锁含关键字的行 */
const blockOf = (src: string, sel: string) => {
  const i = src.indexOf(`\n${sel} {`);
  if (i < 0) return "";
  const start = src.indexOf("{", i);
  let depth = 0;
  for (let k = start; k < src.length; k += 1) {
    if (src[k] === "{") depth += 1;
    else if (src[k] === "}") { depth -= 1; if (depth === 0) return src.slice(start + 1, k); }
  }
  return "";
};

describe("BD-234 结构与关系：大纲三级 ul/li、卡板列有名、概览条目与按钮分层（源码账）", () => {
  it("普查已接入 package.json（npm run audit:list-structure）", () => {
    expect(pkg).toContain('"audit:list-structure": "node scripts/audit-list-structure.mjs"');
  });

  it("大纲：三级容器全是 ul，条目全是 li，类名一个没动", () => {
    expect(outline).toContain('<ul className="outline-tree-scroll">');
    expect(outline).toContain('<li key={volume.id} className="outline-volume">');
    expect(outline).toContain('<li key={chapter.id} className="outline-chapter">');
    expect(outline).toContain("className={`outline-scene ${scene.id === selectedSceneId ? \"active\" : \"\"}`}");
    expect(outline).toContain("<li\n      key={scene.id}\n      className={`outline-scene");
    // 「章/场」两级容器在源码里各出现两次（renderVolume 与未分卷兜底 / renderChapter）
    expect(count(outline, '<ul className="outline-chapter-list">')).toBe(2);
    expect(count(outline, '<ul className="outline-scene-list">')).toBe(1);
    // 未分卷兜底那一支也是 li（兜底最容易漏，实测基线里就是它漏过一轮）
    expect(outline).toContain('<li className="outline-volume">');
    // 空态提示也在列表里 ⇒ 必须是 li，不许退回 span
    expect(outline).toContain('<li className="outline-empty-hint">暂无场景</li>');
    expect(outline).toContain('<li className="outline-empty-hint">暂无章节</li>');
  });

  it("大纲反向锁：div 容器 / div 条目 / span 空态一律不许回来", () => {
    expect(outline).not.toMatch(/<div className="outline-(tree-scroll|chapter-list|scene-list)">/);
    expect(outline).not.toContain('<div key={volume.id} className="outline-volume">');
    expect(outline).not.toContain('<div key={chapter.id} className="outline-chapter">');
    expect(outline).not.toContain('<div\n      key={scene.id}');
    expect(outline).not.toMatch(/<span className="outline-empty-hint">/);
  });

  it("大纲版式没撤：缩进声明仍在（撤的是「只靠它」，不是缩进本身）", () => {
    expect(styles).toContain(".outline-chapter-list,\n.outline-scene-list {");
    expect(blockOf(styles, ".outline-chapter-list,\n.outline-scene-list")).toContain("padding-left: 12px;");
    expect(blockOf(styles, ".outline-tree-scroll")).toContain("overflow: auto;");
    expect(blockOf(styles, ".outline-volume + .outline-volume")).toContain("margin-top: 6px;");
    expect(blockOf(styles, ".outline-volume-row,\n.outline-chapter-row,\n.outline-scene")).toContain("display: flex;");
  });

  it("卡板列：section 挂 role=group 且用列标题接 aria-labelledby", () => {
    expect(board).toContain('const titleId = `cards-board-column-title-${type.kind}`;');
    expect(board).toContain('className="cards-board-column"\n            role="group"\n            aria-labelledby={titleId}');
    expect(board).toContain('<span className="cards-board-column-title" id={titleId}>{type.name}</span>');
  });

  it("卡板列反向锁：卡片仍是 role=button + 原 aria-label，列体仍是 div（有意不改 ul）", () => {
    expect(board).toContain('role="button"');
    expect(board).toContain("aria-label={`选择卡片：${card.title}`}");
    expect(board).toContain('<div className="cards-board-column-body">');
    expect(board).not.toMatch(/<ul className="cards-board-column-body">/);
    expect(board).not.toMatch(/<li\s+key=\{card\.id\}/);
    // 这条「有意不改」依赖的证据也得钉住：入场动画按卡片的子位置取档
    expect(editorial).toContain(".cards-board-column .cards-board-card:nth-child(2),");
  });

  it("概览最近编辑：li 只当条目、行内放真 button，li 上不许有 role=button", () => {
    expect(overview).toContain('<li key={scene.sceneId} className="overview-recent-item">');
    expect(overview).toMatch(/<li key=\{scene\.sceneId\} className="overview-recent-item">\s+<button\n\s+type="button"/);
    expect(overview).not.toMatch(/className="overview-recent-item"\s+tabIndex=\{0\}\s+role="button"/);
    expect(overview).toContain('title={`跳到场景「${scene.sceneTitle}」并进入写作`}');
    expect(overview).toContain('onClick={() => handleJumpScene(scene.sceneId)}');
  });

  it("概览像素账：行的声明整条下沉到 `> button`，一字未改也不留残值", () => {
    const liBlock = blockOf(overviewCss, ".overview-recent-item");
    const btnBlock = blockOf(overviewCss, ".overview-recent-item > button");
    // li 块里除注释之外不许有任何声明（否则就是又加了一层视觉，像素账作废）
    expect(liBlock.replace(/\/\*[\s\S]*?\*\//g, "").trim()).toBe("");
    for (const d of [
      "display: grid;",
      "grid-template-columns: 1fr auto auto;",
      "align-items: center;",
      "gap: 12px;",
      "width: 100%;",
      "padding: 8px 10px;",
      "border-radius: 10px;",
      "border: 1px solid transparent;",
      "cursor: pointer;",
      "outline: none;",
      "text-align: left;",
      "transition: background 0.15s ease, border-color 0.15s ease;"
    ]) expect(btnBlock).toContain(d);
    // BD-240 把这一对 hover/focus 拆成两条：`:focus-visible` 那条要自己把键盘环画出来。
    // 原来合在一起时，本块 `outline: none`（0-1-1）与全局 `button:focus-visible`（0-1-1）平手，
    // 平手看加载顺序——概览是懒加载分块、排在 editorial-studio 之后，于是键盘环被静默压掉
    // （现场 /tmp/bd240-probe.log：computed outline="none 0px" box-shadow="none"）。
    // 填充与边框色的声明逐条保留在两支里，所以 hover 与 focus 的像素观感与拆之前一致。
    expect(overviewCss).toContain(".overview-recent-item > button:hover {");
    const focusBlock = blockOf(overviewCss, ".overview-recent-item > button:focus-visible");
    expect(focusBlock).toContain("outline: 2px solid var(--accent-spine);");
    expect(focusBlock).toContain("outline-offset: 2px;");
    expect(focusBlock).toContain("background: var(--paper-panel, rgba(250, 245, 235, 0.85));");
    expect(focusBlock).toContain("border-color: var(--copper-soft, rgba(138, 90, 43, 0.35));");
    expect(overviewCss).toContain("  .overview-recent-item > button {\n    grid-template-columns: 1fr auto;");
  });

  it("普查四条枪都带得上手的判据串（缺一条就是缺牙）", () => {
    expect(census).toContain('A 定点锁');
    expect(census).toContain('B 卡板列');
    expect(census).toContain('C ARIA 误挂');
    expect(census).toContain('D 缩进承载从属');
    expect(census).toContain("LIST_STRUCTURE_RED");
    expect(census).toContain("LIST_STRUCTURE_OK");
    // A：条目必须是 li（这颗牙被摘掉时 T-B 的缺陷就会安静溜走）
    expect(census).toContain('const bad = items.filter((k) => tag(k) !== "li" && roleOf(k) !== "listitem");');
    // B：卡板列必须有可访问名
    expect(census).toContain('if (!nameOf(el)) hits.push({ rule: "B"');
    // C：listbox/menu/tablist 的合法子角色不是 listitem ⇒ 只对没有 role 属性的 ul/ol 开火
    expect(census).toContain('if ((t === "ul" || t === "ol") && r === "") {');
    // D：8px 门槛 + 盒子左缘齐平 + 内容右移量 + 按钮行排除
    expect(census).toContain("if (pl < 8) continue;");
    expect(census).toContain("if (Math.abs(elBoxLeft - prevBoxLeft) > 1) continue;");
    expect(census).toContain("if (contentShift < 8) continue;");
    expect(census).toContain('if (ch.every((k) => tag(k) === "button" || roleOf(k) === "button")) continue;');
  });

  it("普查定点锁的三级容器与条目成对钉住", () => {
    expect(census).toContain('{ container: "outline-tree-scroll", item: "outline-volume", label: "卷" }');
    expect(census).toContain('{ container: "outline-chapter-list", item: "outline-chapter", label: "章" }');
    expect(census).toContain('{ container: "outline-scene-list", item: "outline-scene", label: "场" }');
    expect(count(census, "{ container: ")).toBe(3);
    expect(census).toContain('hasCls(k, "outline-empty-hint")');
  });

  it("普查的 table 语境排除与「容器缺席也判红」两条守着已知良性形状", () => {
    expect(census).toContain('[role="table"],[role="grid"],[role="treegrid"],table,[role="tablist"],[role="listbox"],[role="menu"],[role="toolbar"]');
    expect(census).toContain("一次都没出现");
    expect(census).toContain("for (const lv of OUTLINE_LEVELS) {");
  });

  it("普查的 16 屏清单逐条在账（缺屏＝判据变窄）", () => {
    const lines = census.split("\n").filter((l) => l.trim().startsWith('["') && l.trim().endsWith("}],") || l.trim().startsWith('["'));
    expect(lines).toHaveLength(16);
    for (const label of ["项目·首页", "项目·写作台", "项目·设定卡", "项目·大纲", "项目·大纲（展开到底）", "项目·概览", "项目·全书预览", "项目·写作统计", "项目·版本历史", "书库", "卡片库", "收件箱", "资料阅读", "设置", "弹层·编辑目标", "浮层·命令面板"]) {
      expect(census).toContain(`["${label}"`);
    }
  });

  it("普查按渲染后的事实判：computed padding、兄弟关系、role 归属", () => {
    expect(census).toContain("const cs = getComputedStyle(el);\n    const pl = parseFloat(cs.paddingLeft || \"0\") || 0;");
    expect(census).toContain("el.previousElementSibling");
    expect(census).toContain('el.closest(\'[aria-hidden="true"], template, script, style\')');
    expect(census).toContain('if (r === "listitem" && !el.closest(\'ul,ol,[role="list"],[role="directory"]\'))');
    expect(census).toContain('/^(row|cell|columnheader|rowheader|gridcell)$/.test(r)');
    expect(count(census, "seenContainers")).toBeGreaterThanOrEqual(4);
  });

  it("大纲那处的账本注释把「为什么源码扫不出来」写死", () => {
    expect(outline).toContain("BD-234（结构与关系 1.3.1）");
    expect(outline).toContain("真正承载层级的是缩进");
    expect(board).toContain("BD-234（结构与关系 1.3.1）");
    expect(overview).toContain("BD-234（结构与关系 1.3.1）");
    expect(census).toContain("padding-left:12px");
  });

  it("未被这一批碰过的同族入口不顺手改：三处旧锁仍在原位", () => {
    // ①「选择卡片：xxx」这条 aria-label 有专账（drag-alternatives-2-5-7），本批只加了列名
    expect(board).toContain("draggable");
    // ② 大纲的空态提示文案没改字
    expect(outline).toContain("暂无场景");
    expect(outline).toContain("暂无章节");
    // ③ 概览跳转的 title 口径没改字
    expect(overview).toContain("跳到场景");
  });
});
