// @vitest-environment jsdom
/**
 * BD-230 账本：WCAG 1.3.1「信息与关系」的**形状**那一半（G130/H13 的姊妹条）——
 * 标题层级不许跳级，卡片头这个角色的标签与样式必须成对钉住。
 *
 * 主判据是运行时普查 scripts/audit-heading-order.mjs（npm run audit:heading-order）：
 * 逐屏按 DOM 顺序抓整条标题序列，①上升 ≥2 判红、②空序列判红、③全程无深处判红、
 * ④卡片头档口判红。本台账管运行时管不到的三件事：
 *   ① 三处改动的**文案与档口一个字不许动**——这批只改语义标签，像素账是
 *      「抹掉 tag 后逐字节相同」（见下），任何顺手调字号的人都会撞这条；
 *   ② 概览页那六张卡的 h3 不许被顺手改成 h2（它们合法嵌在 <h2>项目概览</h2> 之下，
 *      运行时普查 ① 只看跳级、看不出「不该动却动了」，反向的回归必须源码盯着）；
 *   ③ CSS 选择器扩的是**直接子级**那一支，不是后代。这不是洁癖：收件箱详情卡
 *      （div.stats-card.inbox-detail）里本来就挂着两个**内容** h2（「条目正文」
 *      「AI 候选版本」），写成后代就会把它们凭空上色——运行时 ④ 会咬，但源码里
 *      先钉一道，免得有人把 `> h2` 当成冗余"简化"成 ` h2`。
 *
 * 实测基线（/tmp/bd230-survey.log，1360×900 活体）：四处 1→3 跳级——
 *   [项目]／[项目·列表]  h1「创作项目」› h3「最近项目」
 *   [工作台·大纲]        h1 sr-only › h3「场景任务卡」
 *   [工作台·写作统计]    h1 sr-only › h3 × 7（七张卡片头）
 *   （[工作台·概览] 的六个 h3 上面有 h2「项目概览」，是合法嵌套，本批不动）
 * 像素账（/tmp/bd230-probe-before.log ↔ /tmp/bd230-probe-after.log，同一条走查两趟，
 *   屏数 6、逐标题 computed 的 fs／lh／fw／字族／color／display／padding／margin／
 *   border-bottom／gap／盒宽高／在父内 y 全等）：**抹掉 tag 与计数行后两份日志逐字节相同**。
 *   另外两笔专项：概览六张卡与收件箱那两个内容 h2 的读数在两趟里完全一致
 *   （statsCardH2 计数改前改后都是 2，直接子级=n），也就是 `> h2` 没有连坐。
 *
 * 牙齿账（改完再当场造三次回归，见 /tmp/bd230-ta3.log、-tb.log、-tc.log；每次都还原到
 *   注入前的 sha，还原后重建再复验一遍绿）：
 *   T-A 把统计页七张卡里退回一张 h3 → 普查只红 1 项（④ 同屏混级）。这一颗把判据本身改了：
 *     原本 ① 只看**上升**跳级，单张卡退回 h3 在序列上是 h2→h3 的下降，① 与 axe 的
 *     heading-order 都看不见 ——「卡片头同级」这一半就是被这颗牙逼出来的（/tmp/bd230-ta.log
 *     第一次跑 T-A 时普查仍是 HEADING_ORDER_OK，是判据的洞不是产品的洞）。
 *   T-B 把 editorial 的 `.stats-card > h2` 放宽成后代 `.stats-card h2` → 只红 1 项（④），
 *     红在收件箱·详情，读数里「AI 候选版本」当场从 16px/600 变成 16px/650：肉眼看不出，
 *     但卡片内容被卡片头那族声明上色了。
 *   T-C 把 ① 的门槛从 >=2 抬到 >=3 → 自证针当场报「①没抓到」，判据退化不会安静放行。
 *
 * 路径账（__tests__/ 起算）：../ = src/app/；../../ = src/；../../../ = 仓库根。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const home = read("../../features/creation/home/ProjectHomePage.tsx");
const outline = read("../../features/creation/outline/OutlinePage.tsx");
const stats = read("../../features/creation/stats/StatsPage.tsx");
const overview = read("../../features/creation/overview/OverviewPage.tsx");
const inboxDetail = read("../../features/creation/inbox/components/InboxItemDetail.tsx");
const styles = read("../../../src/styles.css");
const editorial = read("../../../src/styles/editorial-studio.css");
const census = read("../../../scripts/audit-heading-order.mjs");
const battery = read("../../../scripts/inject-test-design-tokens.mjs");
const capture = read("../../../scripts/visual-capture.mjs");
const pkg = read("../../../package.json");

/** 选择器行的判定：只认**整行就是那条选择器**（行后跟 `,` 或 ` {`），注释里出现同样的字面量不算 */
const selectorLine = (src: string, sel: string) =>
  src.split("\n").some((l) => l === `${sel},` || l === `${sel} {` || l === sel);

describe("BD-230 结构账：跳级那三处的标签", () => {
  it("项目首页：最近项目降到 h2，文案与那一条卡片头档口原样", () => {
    expect(home).toContain("<h2>最近项目</h2>");
    expect(home).not.toContain("<h3>最近项目</h3>");
    // 卡片头仍是 .stats-card 的直接子级——④ 那档口判的就是这个形状
    expect(home).toContain('<section className="stats-card">\n          {/* BD-230');
    expect(home).toContain("BD-230（WCAG 1.3.1 跳级）");
  });

  it("大纲侧栏：场景任务卡降到 h2，眉标那条 BD-225 装饰元素不动", () => {
    expect(outline).toContain("<h2>场景任务卡</h2>");
    expect(outline).not.toContain("<h3>场景任务卡</h3>");
    expect(outline).toContain('<p className="desktop-card-label" lang="en" aria-hidden="true">Scene card</p>');
    expect(outline).toContain("BD-230（WCAG 1.3.1 跳级）");
  });

  it("写作统计：七张卡片头全批到位，一条不许漏（漏一条就还留着一处 1→3）", () => {
    const heads = [
      "<h2><Clock3 size={15} /> 写作时长</h2>",
      "<h2><Flame size={15} /> 连续写作</h2>",
      "<h2><Library size={15} /> 修订与结构</h2>",
      "<h2><Layers size={15} /> 场景状态</h2>",
      "<h2>最近 30 天净增字数与写作时长</h2>",
      "<h2><PencilLine size={15} /> 写作会话（可修正）</h2>"
    ];
    for (const h of heads) expect(stats, `卡片头丢了：${h}`).toContain(h);
    // 目标进度那张跨行（里面还嵌着一颗「编辑目标」按钮），单独钉开合两端
    expect(stats).toContain("<h2>\n        <Target size={15} /> 目标进度");
    expect(stats).toContain("      </h2>\n      <div className=\"stats-metric-switch\"");
    for (const old of heads.map((h) => h.replace(/h2/g, "h3"))) {
      expect(stats, `还在用旧标签：${old}`).not.toContain(old);
    }
    expect(stats).not.toContain("<h3>\n        <Target size={15} /> 目标进度");
    expect(stats).toContain("BD-230（WCAG 1.3.1 跳级）");
  });
});

describe("BD-230 反向账：合法嵌套的那六张不许一起改", () => {
  it("概览页六张卡仍是 h3——它们上面有 h2「项目概览」，改了就变成 1→2→2→3 的另一种错", () => {
    for (const h of [
      "<h3><Target size={15} /> 字数目标</h3>",
      "<h3><Clock3 size={15} /> 写作时长</h3>",
      "<h3><Layers size={15} /> 章节状态</h3>",
      "<h3><Inbox size={15} /> 待处理</h3>",
      "<h3><Flame size={15} /> 最近 30 天净增字数</h3>",
      "<h3>\n          <FileText size={15} /> 最近编辑"
    ]) {
      expect(overview, `概览卡片头被顺手改了：${h}`).toContain(h);
    }
    expect(overview).toContain("<h2>项目概览</h2>");
  });

  it("收件箱详情那两个 h2 是卡片**内容**、不是卡片头——`> h2` 就是为了不碰它们", () => {
    // 结构：h2 挂在 .inbox-detail-head / 另一块容器里，不是 .stats-card 的直接子级
    expect(inboxDetail).toContain('<div className="stats-card inbox-detail">\n      <div className="inbox-detail-head">');
    expect(inboxDetail).toContain('<h2 className="paper-title mt-1 text-xl font-semibold">条目正文</h2>');
    expect(inboxDetail).toContain('<h2 className="paper-title mt-1 text-base font-semibold">AI 候选版本</h2>');
  });
});

describe("BD-230 样式账：扩的是直接子级那一支，不是后代", () => {
  it("卡片头两族（styles.css + editorial-studio.css）都扩出 `> h2`，h3 那一支原样留着", () => {
    for (const src of [styles, editorial]) {
      expect(selectorLine(src, ".stats-card > h2"), "缺 .stats-card > h2 这条选择器").toBe(true);
      expect(selectorLine(src, ".stats-card h3"), ".stats-card h3 被整条换掉了").toBe(true);
      expect(selectorLine(src, ".stats-card > h2 svg"), "缺 .stats-card > h2 svg").toBe(true);
      expect(selectorLine(src, ".stats-card h3 svg"), "缺 .stats-card h3 svg").toBe(true);
    }
    // editorial 那条是「一条规则三个宿主」的老形状（BD-17 记的），.background-page-title 不能掉
    expect(editorial).toContain(".stats-card h3,\n.background-page-title {");
    // 档口本身不许被这批顺手调（16px + 650 是 BD-17 定的卡片头档）
    expect(editorial).toContain("  font-size: 16px;\n  font-weight: 650;");
  });

  it("绝不允许写成后代 `.stats-card h2`——那会连坐收件箱那两个内容 h2（本批最贵的一处判断）", () => {
    for (const src of [styles, editorial]) {
      expect(src, "选择器被放宽成后代了").not.toMatch(/^\.stats-card h2[,{ ]/m);
      expect(src, "svg 那条被放宽成后代了").not.toMatch(/^\.stats-card h2 svg[,{ ]/m);
    }
  });

  it("大纲侧栏同形状，并且注入电池那颗牙的字面量锚还活着（needle 761）", () => {
    expect(selectorLine(styles, ".outline-page-side > h2"), "缺 .outline-page-side > h2").toBe(true);
    // 电池 needle 钉的是 styles.css 里 `.outline-page-side h3 {` 这段字面量；h2 支必须写在**前面**
    expect(styles).toContain(".outline-page-side h3 {");
    expect(battery).toContain('mutate("src/styles.css", ".outline-page-side h3 {",');
  });

  it("项目首页那条是改名（原宿主只有一个），不是留一条零宿主的 h3 幻影并联", () => {
    expect(selectorLine(editorial, ".project-home > .stats-card > h2"), "缺 .project-home > .stats-card > h2").toBe(true);
    expect(editorial, "旧的选择器没改名，成了零宿主幻影").not.toMatch(/^\.project-home > \.stats-card > h3 \{/m);
    expect(editorial).toContain("BD-230（WCAG 1.3.1 跳级）");
  });

  it("取样的两处选择器跟着并联（对比度账别只扫到一半宿主）", () => {
    expect(capture).toContain('sample(".stats-card > h2, .stats-card h3", heroBg)');
  });
});

describe("BD-230 普查骨架：判据没了就是恒真", () => {
  it("npm 脚本已挂上", () => {
    expect(pkg).toContain('"audit:heading-order": "node scripts/audit-heading-order.mjs"');
  });

  it("四条判据都在，并且把正文区与序列分开", () => {
    for (const marker of ["①「", "②「", "③全程", "④「"]) {
      expect(census, `判据「${marker}」丢了`).toContain(marker);
    }
    expect(census).toContain("const findings = (rows) => {");
    expect(census).toContain("if (r.levels.length === 0)");
    expect(census).toContain("const jump = r.levels[i].lv - r.levels[i - 1].lv;");
    expect(census).toContain("if (jump >= 2)");
    expect(census).toContain("if (r.headOk === false)");
    expect(census).toContain("r.levels.some((l) => l.lv >= 2)");
    // ④ 的三件事：卡片头档口、同屏不许混级、卡片内容不许被上色
    expect(census).toContain('const cardHeads = [...document.querySelectorAll(".stats-card > h2, .stats-card > h3")];');
    expect(census).toContain('const mixed = headLevels.length > 1 ? headLevels.join("/h") : "";');
    expect(census).toContain("headOk: headsWrong.length === 0 && bodyWrong.length === 0 && !mixed,");
    expect(census).toContain("同一屏的卡片头必须同级");
    // 正文区（书自己写的章标题）不计入序列，但要记档
    expect(census).toContain('const isContent = (h) => !!h.closest(".reader-scroller, article");');
    expect(census).toContain("正文区另有 ${r.contentCount} 个标题");
    // 下降不判红，但要留字（工作台那几屏的第一格是项目名 h2）
    expect(census).toContain("if (r.levels.length && r.levels[0].lv !== 1)");
    expect(census).toContain("下降不判红，见本文件头注");
    // 混级这一半为什么要在 ④ 里：① 只看上升，单张卡退回 h3 在序列上是下降（T-A 那颗牙实测）
    expect(census).toContain("T-A 那颗牙教出来的");
  });

  it("页内函数看不见模块常量——档位是当参数递进去的（第一版踩的坑）", () => {
    expect(census).toContain("const collect = ({ headFs, headFw }) => {");
    expect(census).toContain("page.evaluate(collect, { headFs: CARD_HEAD_FS, headFw: CARD_HEAD_FW })");
    expect(census).toContain("const CARD_HEAD_FS = \"16px\";");
    expect(census).toContain("const CARD_HEAD_FW = \"650\";");
  });

  it("自证针四枚走真判据，针体跑完才置 ran", () => {
    expect(census).toContain("const jumpRows = rows.map((r) => (r.levels.length >= 2");
    expect(census).toContain("lv: r.levels[0].lv + 2 }]");
    expect(census).toContain("const emptyRows = rows.map((r) => ({ ...r, levels: [] }));");
    expect(census).toContain("const flatRows = rows.map((r) => ({ ...r, levels: [{ lv: 1,");
    expect(census).toContain("const headBack = rows.map((r) => ({ ...r, headOk: false,");
    expect(census).toContain('selfTest.caughtJump = redsJump.some((x) => x.startsWith("①") && x.includes("标题跳级"));');
    expect(census).toContain('selfTest.caughtEmpty = redsEmpty.some((x) => x.startsWith("②") && x.includes("整屏抓不到一个标题"));');
    expect(census).toContain('selfTest.caughtFlat = redsFlat.some((x) => x.startsWith("③"));');
    expect(census).toContain('selfTest.caughtHead = redsHead.some((x) => x.startsWith("④"));');
    expect(census).toContain("selfTest.ran = true;");
    expect(census).toContain("reds.push(...selfTest.reds);");
    expect(census).toContain('if (!selfTest.ran) selfTest.reds.push("自证失败：自证针没跑完');
  });

  it("必到屏与三条下限：本批改到的屏必须真的被扫到", () => {
    expect(census).toContain('const MANDATORY = ["项目", "收件箱", "卡片库", "设置", "书库", "阅读统计", "工作台·写作统计", "工作台·大纲"];');
    expect(census).toContain('if (!seen.has(m)) reds.push(`必到屏「${m}」没被扫到');
    expect(census).toContain("const SCREEN_MIN = 16;");
    expect(census).toContain("const WORKBENCH_MIN = 5;");
    expect(census).toContain("const READER_MIN = 2;");
    expect(census).toContain("page.locator(\"button:has-text('阅读统计')\")");
    expect(census).toContain('await snap("阅读统计", "shell");');
    expect(census).toContain('await nav(v, `工作台·${v}`);');
    // 回书库只为开书，不重复记一行——判红按屏数，同一屏扫两趟会把一条缺陷报成两条
    expect(census).toContain('await goto("书库");');
    expect(census).toContain("const goto = async (name) => {");
  });

  it("④ 的后半要有活宿主：seed 的收件箱是空的，速记一条再点进详情", () => {
    expect(census).toContain('input[aria-label="灵感速记"]');
    expect(census).toContain('await qi.press("Enter");');
    expect(census).toContain('await snap("收件箱·详情", "shell");');
    // 造不出宿主时只记档、不判红：那是数据前置，不是无障碍回归
    expect(census).toContain("④ 的「卡内非直接子级 h2」这一半没有活宿主");
  });

  it("书目不写死、退出码与导出模式、记档排在 DUMP 之前", () => {
    expect(census).toContain("const titleLoc = page.locator(`${SHELF} h2.paper-title`);");
    for (const nm of ["测试 Markdown 笔记", "测试 TXT 资料", "测试 EPUB 资料"]) {
      expect(census, `普查里写死了书目「${nm}」`).not.toContain(nm);
    }
    expect(census).toContain("process.exit(exitCode);");
    expect(census).toContain("return reds.length ? 1 : 0;");
    expect(census).toContain("HEADING_ORDER_OK");
    expect(census).toContain("HEADING_ORDER_FAIL");
    expect(census).toContain('if (process.env.BD230_DUMP === "1") {');
    expect(census).toContain("BD230_DUMP 完成：采样 ${rows.length} 屏（未做判红，仅导出现场）。");
    expect(census.indexOf('for (const n of notes) console.log("  记档 \" + n);'))
      .toBeLessThan(census.indexOf('if (process.env.BD230_DUMP === "1") {'));
  });
});
