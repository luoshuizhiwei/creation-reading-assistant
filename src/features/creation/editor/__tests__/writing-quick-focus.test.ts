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
