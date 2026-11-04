// @vitest-environment node
/**
 * BD-241 护栏（焦点可见 2.4.7）：写作速查面板两支输入框的键盘环，加上普查的覆盖面与判据。
 *
 * 缺陷本体（改前实测，/tmp/bd241-probe2.log、/tmp/bd241-probe4.log，1360×860 真窗、真键盘）：
 *   `.writing-quick-search input`（Tab 80 步到）与 `.writing-quick-field input`（Tab 2 步到，
 *   需先在本项目 tab 选中一张卡）聚焦时 computed outline="none 0px"、box-shadow="none"，
 *   父壳 `label.writing-quick-search` 聚焦前后 computed 逐字相同（s=none、b=1px rgb(102,109,119)）
 *   ——本体与容器两头都没有指示。成因与 BD-240 同一形状：本文件随写作台懒加载分块
 *   （WritingDesk-*.css #58）排在主包之后，`outline: 0`（0-1-1）与全局 `input:focus-visible`
 *   （index-*.css #1114，0-1-1）平手，平手看加载顺序 ⇒ 全局环被静默压掉。
 * 修后实测（/tmp/bd241-probe4-after.log）：两支都是 computed outline="solid 2px rgb(49,95,155)"，
 *   胜者为本文件新增的 :focus-visible（WritingDesk-*.css #59）；盒形改前改后逐字相同
 *   （搜索框 x1081 y351 w258 h24、字段 x1053 y618 w286 h32——outline 在流外，不入布局）。
 *
 * 另一半账在量具上：这一族全仓 16 处 `outline: none|0` 里本体无环的 9 处，此前只有 4 处真被
 * audit:focus-rings 判过，其余 5 面各卡一个可达性条件（视图 tab／另一屏／非默认分区／条件挂载／
 * 状态门且 blur 即卸载）。本文件同时钉住扩面后的范围名与收紧后的判据，防止下一批人把它们
 * 当成「已经判过」。
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

const here = path.dirname(fileURLToPath(import.meta.url));
const css = readFileSync(path.join(here, "../writing-quick-reference.css"), "utf8").replace(
  /\/\*[\s\S]*?\*\//g,
  ""
);
const census = readFileSync(path.join(here, "../../../../../scripts/audit-focus-rings.mjs"), "utf8");

const blockOf = (selector: string) => {
  const esc = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const m = new RegExp(`${esc}\\s*\\{([^}]*)\\}`).exec(css);
  return m ? m[1] : null;
};

describe("BD-241 写作速查输入框的键盘环必须自己画", () => {
  it("两支输入框的 :focus-visible 合写一条、给出实线环与外扩", () => {
    expect(css).toContain(".writing-quick-search input:focus-visible,\n.writing-quick-field input:focus-visible {");
    const focusBlock = blockOf(".writing-quick-search input:focus-visible,\n.writing-quick-field input:focus-visible");
    expect(focusBlock, "缺 :focus-visible 规则").not.toBeNull();
    expect(focusBlock).toContain("outline: 2px solid var(--accent-spine);");
    expect(focusBlock, "环外扩 2px，落在壳内衬 6px 8px 之内").toContain("outline-offset: 2px;");
  });

  it("基础块的 outline: 0 留在原处——它撤的是鼠标聚焦的默认环，意图不变", () => {
    const base = blockOf(".writing-quick-search input,\n.writing-quick-field input");
    expect(base, "缺基础声明块").not.toBeNull();
    expect(base).toContain("outline: 0;");
    expect(base).not.toContain(":focus-visible");
  });

  it("环只用设计令牌，不写死色", () => {
    const focusBlock = blockOf(".writing-quick-search input:focus-visible,\n.writing-quick-field input:focus-visible");
    expect(focusBlock).not.toMatch(/#[0-9a-fA-F]{3,6}\b|rgba?\(/);
  });

  it("环与本块同文件同分块——不再靠全局规则与加载顺序", () => {
    // 本文件是懒加载分块；环若写回全局那条，就会被这里的 outline: 0 按顺序压掉（BD-240 同源）。
    const idxBase = css.indexOf(".writing-quick-search input,");
    const idxFix = css.indexOf(".writing-quick-search input:focus-visible,");
    expect(idxFix).toBeGreaterThan(idxBase);
  });
});

describe("BD-241 焦点环普查的覆盖面与判据", () => {
  it("五个原先走不到的面已进范围名", () => {
    for (const label of ["项目·关系图", "弹层·写作速查·全局搜索", "卡片库", "项目·大纲行内改名态"]) {
      expect(census, `缺范围「${label}」`).toContain(`"${label}"`);
    }
    // 设置这一族按分区循环生成范围名，四个非默认分区都要在名单里
    expect(census).toContain("设置·${cat}");
    for (const cat of ["阅读器", "AI 助手", "数据与存储", "关于 / 调试"]) {
      expect(census, `设置分区「${cat}」没进名单`).toContain(`"${cat}"`);
    }
  });

  it("条件挂载与状态门各有解锁步骤：速查要切「全局搜索」tab，改名要键盘 Enter + 先判当前焦点", () => {
    expect(census).toContain('hasText: "全局搜索"');
    expect(census).toContain(".writing-quick-search input");
    // 鼠标点开后 autoFocus 进来的输入框不满足 :focus-visible 的键盘模态 ⇒ 必须键盘激活
    expect(census).toContain('button[aria-label^="改名"]');
    expect(census).toContain("judgeCurrentFirst: true");
    expect(census).toContain("resetFocus: false");
  });

  it("判据收紧：松的 anyShadow 已摘除，纯环正则兼容 computed 序列化", () => {
    const code = census.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^\s*\/\/.*$/gm, "");
    expect(code).not.toMatch(/hasIndicator:\s*outlineOk \|\| ringShadow \|\| anyShadow/);
    expect(code).not.toContain("const anyShadow");
    expect(code).toMatch(/0px 0px 0px \[1-9\]/); // computed 形状，作者写法那条是假牙
  });

  it("本体无确定环的一律走聚焦差分，再走容器代持；容器要几何包住且面积有限", () => {
    expect(census).toContain("data-ring-delta");
    expect(census).toContain("聚焦差分");
    expect(census).toContain("容器代持");
    expect(census).toMatch(/areaRatio <= 8/); // 页面级容器不得替全场背书
    expect(census).toMatch(/encloses/);
  });

  it("拿不到离焦基线的不静默判绿：单独点名并每次打印", () => {
    expect(census).toContain("info.unmeasurable = true");
    expect(census).toContain("不可测（blur 即卸载、拿不到离焦基线）");
  });
});

/**
 * BD-242 收尾账：上一批自记的三处欠账——命令面板搜索框、写作台引用卡搜索框、速查面板的
 * 卡片字段族——都量过（绿），但都没进常驻网。绿面不入网就等于没有牙齿：将来谁把全局
 * `input:focus-visible` 那条改窄或改特异度，这三处要静默变红而无人报警。
 * 三面各自的可达性条件也要钉住，否则下一批人会把「范围名还在、走查已静默跳过」当成判过。
 */
describe("BD-242 三面欠账进网：范围名 + 各自的解锁步骤", () => {
  it("三个新范围名都在", () => {
    for (const label of ["弹层·写作速查·本项目字段", "弹层·命令面板", "写作台·引用卡"]) {
      expect(census, `缺范围「${label}」`).toContain(`"${label}"`);
    }
  });

  it("速查卡片字段族要先有卡：种子流程建项目之后再建一张真卡片", () => {
    expect(census).toContain("新建并关联");
    expect(census).toContain("input[placeholder='卡片主名称']");
    expect(census).toContain('"创建卡片"');
  });

  it("字段族还要在本项目页签里选中那张卡", () => {
    expect(census).toContain('hasText: "本项目"');
    expect(census).toContain(".writing-quick-list li > button");
    expect(census).toContain(".writing-quick-field input");
  });

  it("命令面板走 Ctrl+P，引用卡走正文 @ —— 都是状态门后的输入框", () => {
    expect(census).toContain('page.keyboard.press("Control+p")');
    expect(census).toContain(".creation-search-input");
    expect(census).toContain('page.locator(".scene-editor-content")');
    expect(census).toContain('page.keyboard.type("@", { delay: 60 })');
    expect(census).toContain(".card-reference-input");
  });

  it("三面都要收摊：弹层留着的 scrim 会挡住后面每一站的点击（BD-241 跑法教训）", () => {
    const tail = census.slice(census.indexOf('await tryScope("弹层·写作速查·本项目字段"'));
    const reader = tail.indexOf('await tryScope("阅读器·TXT"');
    expect(reader, "三面必须排在阅读器范围之前").toBeGreaterThan(0);
    const block = tail.slice(0, reader);
    for (const label of ["弹层·写作速查·本项目字段", "弹层·命令面板", "写作台·引用卡"]) {
      expect(block.indexOf(`await tryScope("${label}"`), `「${label}」不在三面块内`).toBeGreaterThanOrEqual(0);
    }
    // 三面各有关自己的收尾动作（按键或面板自带关闭钮）
    expect(block).toContain('page.keyboard.press("Control+Shift+k")');
    expect(block).toContain('page.keyboard.press("Escape")');
    expect(block).toContain('page.locator(".creation-search-close")');
    expect(block).toContain('page.locator(".card-reference-close")');
  });

  it("命令面板的 trap 只兜输入框↔关闭钮两点，sweep 必须不 blur 起步否则 Escape 落空、scrim 常驻", () => {
    const seg = census.slice(census.indexOf('await tryScope("弹层·命令面板"'));
    expect(seg, "命令面板必须 resetFocus:false 起步，把焦点留在 trap 里").toContain("resetFocus: false");
  });

  it("跳过不再是哑的：有范围没走到就非零退出", () => {
    const code = census.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^\s*\/\/.*$/gm, "");
    expect(code).toContain("FOCUS_RING_SKIP");
    // 跳过点名数组必须在 tryScope 的 catch 里落账，否则退出码没有依据
    expect(code).toMatch(/skipped\.push\(/);
    // 且退出判定排在 FOCUS_RING_OK 之前——先报绿再报跳过等于没说
    expect(code.indexOf("FOCUS_RING_SKIP")).toBeLessThan(code.indexOf("FOCUS_RING_OK"));
  });

  it("逐范围打印判定数：总停靠点看不出某一面走空", () => {
    expect(census).toContain("个停靠点${");
    expect(census).toMatch(/judged \+= 1;/);
  });

  /**
   * 上限不够深同样是静默的：本批引用卡一站按 maxStops 60 走，把写作台自己的 60 个元素走完
   * 就收工，而要点名的 `.card-reference-input` 排在真 Tab 序列第 76 位——范围名照打、整网照绿、
   * 站却没进网（实测 /tmp/bd242-three2.log 撞不到、/tmp/bd242-three3.log 上限 220 才撞到）。
   * 所以每站要能点名一个「必须判到」的停靠点，判不到就抛（抛进 tryScope ⇒ 记跳过 ⇒ 非零退出）。
   */
  it("每站可点名「必须判到」的停靠点，判不到就抛——上限太浅不算走到", () => {
    const code = census.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^\s*\/\/.*$/gm, "");
    expect(code).toMatch(/mustSel = null/);
    expect(code).toMatch(/if \(mustSel && !mustHit\) throw/);
    // 按 activeElement.matches(sel) 实配，不拿 id 字符串猜
    expect(code).toMatch(/document\.activeElement\?\.matches\?\.\(s\)/);
    // 三面各自点名（缺一条就等于那一面又回到「静默没走到」）
    expect(census).toContain('mustSel: ".writing-quick-field input"');
    expect(census).toContain('mustSel: ".creation-search-input"');
    expect(census).toContain('mustSel: ".card-reference-input"');
    // 引用卡一站的上限必须比停靠点位次深（实测第 76 位）
    expect(census).toMatch(/maxStops: 220, mustSel: "\.card-reference-input"/);
  });
});
