// @vitest-environment node
/**
 * BD-246（WCAG 3.2.1／3.2.2）·「改值换上下文」这一族的静态账本
 *
 * 运行时普查（scripts/audit-context-change.mjs）量的是真按键之后的因果；这本账守形状：
 * 三处修好的东西不许悄悄退回「一改值就把人搬走、而且什么都没说」，也不许把普查自己
 * 写成哑牙（首版就漏了一句 `await site.open()`，12 站空跑还打 OK——见 census 头注）。
 * 每条都要求非空命中：找不到形状就红。
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const srcRoot = path.resolve(import.meta.dirname, "../../../..");
const read = (rel: string) => readFileSync(path.join(srcRoot, rel), "utf8");
const census = readFileSync(path.join(srcRoot, "../scripts/audit-context-change.mjs"), "utf8");
const pkg = JSON.parse(read("../package.json")) as { scripts: Record<string, string> };

describe("BD-246 改值换上下文的点名账（3.2.1／3.2.2 静态形状）", () => {
  it("大纲树：三颗会弹影响预览的 select 都挂着事先说明，而且提示里说清了后果", () => {
    const tree = read("features/creation/outline/OutlineTree.tsx");
    expect((tree.match(/aria-describedby=\{adviceId\}/g) || []).length, "大纲树里挂说明的 select 数量变了（应当是移卷／移章／编号三颗）").toBe(3);
    const advice = tree.match(/<p className="outline-page-hint" id=\{adviceId\}>([^<]*)<\/p>/);
    expect(advice, "大纲树的事先提示不在了（弹层重新变成「没说就换」）").toBeTruthy();
    for (const word of ["预览", "确认", "写入"]) {
      expect(advice![1], `提示里没说清「${word}」——F36 要求的是把后果讲明白`).toContain(word);
    }
    expect(/const adviceId = useId\(\);/.test(tree), "提示的 id 不再由 useId 生成（改成写死就会多处重 id）").toBe(true);
  });

  it("场景卡板：移章那颗同样挂着说明；有「应用」钮的批量那颗不在这族里", () => {
    const board = read("features/creation/outline/CardBoard.tsx");
    expect((board.match(/aria-describedby=\{adviceId\}/g) || []).length, "卡板挂说明的下拉框数量变了（应当只有「移章」一颗）").toBe(1);
    const advice = board.match(/<p className="outline-page-hint" id=\{adviceId\}>([^<]*)<\/p>/);
    expect(advice, "卡板的事先提示不在了").toBeTruthy();
    expect(advice![1]).toContain("移章");
    expect(advice![1]).toContain("预览");
    expect(advice![1]).toContain("确认");
    // 非空断言：批量状态那颗本来就是合规写法（显式「应用」钮），不该被顺手也挂上说明
    const batch = board.match(/<select aria-label="批量状态"[^>]*>/);
    expect(batch, "批量状态那颗下拉框不见了（这一族的对照项）").toBeTruthy();
    expect(batch![0]).not.toContain("aria-describedby");
  });

  it("写作台块类型：焦点只在「正文原本有焦点」时才交还，而按钮命令的 .focus() 一个都不许少", () => {
    const editor = read("features/creation/editor/SceneEditor.tsx");
    const handler = editor.match(/<select[\s\S]{0,900}className="scene-block-select"[\s\S]{0,1200}aria-label="块类型"/);
    expect(handler, "「块类型」下拉框不见了（这本账要跟着核对，不能默认它还在）").toBeTruthy();
    const body = handler![0];
    expect(body.includes('onMouseDown={() => { blockWasEditingRef.current = editor.isFocused; }}'), "select 不再记「按下这一刻正文有没有焦点」").toBe(true);
    expect(body.includes('onKeyDown={() => { blockWasEditingRef.current = false; }}'), "键盘浏览时不再作废快照（旧快照会把焦点抢走）").toBe(true);
    expect(/if \(blockWasEditingRef\.current\) chain\.focus\(\);/.test(body), "改值又无条件 .focus() 进正文了——缺陷②回来了").toBe(true);
    expect(body.includes(".chain().focus().toggleNode"), "select 路径上残留无条件 focus").toBe(false);
    // 非空断言：按钮-initiated 的命令照旧把焦点交还正文（撤销/加粗/插入分隔那一族，不是 3.2.2 的形状）
    expect((editor.match(/editor\.chain\(\)\.focus\(\)/g) || []).length, "工具条按钮的 .focus() 被一起删了（写作流会断）").toBeGreaterThanOrEqual(3);
  });

  it("普查自身不许变哑：判据里必须留着这些实测出来的口径（逐条都是一跤换来的）", () => {
    // ① 站点必须先开起来——首版漏了这一句，12 站全在启动屏空跑还打 OK
    expect(census.includes("await site.open();"), "普查不再调用 site.open()（会整屏空跑假绿）").toBe(true);
    // ② 点了不等于切过去了：导航要验明正身，验不过要出声（SKIP／exit 2），不许静默少测
    expect(census.includes("没能切到该屏") && census.includes("没能切到该视图"), "普查不再验导航有没有生效").toBe(true);
    // ③ 模态层打开时只在层内清点（与 BD-245 同口径）
    expect(census.includes('[role="dialog"][aria-modal="true"]'), "普查不再按模态层收窄清点范围").toBe(true);
    // ④ 改值必须用真键盘（合成事件不触发原生 change）
    expect(/page\.keyboard\.press\(key\)/.test(census), "普查改用合成 KeyboardEvent 测改值了").toBe(true);
    // ④b 「改没改值」只能问 change 事件：受控 select 改完会被 React 立刻重置回原值，
    //     读 selectedIndex 会把刚修好的编号下拉框误判成「按键没能改值」而静默跳过（gate3 实测）
    expect(census.includes('addEventListener("change"'), "普查不再数 change 事件").toBe(true);
    expect(census.includes('String(el.selectedIndex) !== (el.getAttribute("data-cc-idx") || "")'), "普查又拿 selectedIndex 当「改没改值」的判据了").toBe(false);
    // ④c 上一颗改完值把列表整块换掉时要重新认脸（按位置，误差 >24px 就不认），不许直接跳过
    expect(census.includes("重认脸"), "普查不再在节点被换掉后重新认脸").toBe(true);
    // ④d 焦点「掉回 body」与「被搬到某个控件」是两族：前者是 2.4.3 落点族，本批不判红但必须出声
    expect(census.includes("2.4.3 落点族") && census.includes("ℹ️ 另族"), "普查不再把「焦点跌回 body」单列成另族（要么混进 3.2 的账，要么静默）").toBe(true);
    // ⑤ 「事先说明」的豁免必须要求看得见的后果说明：sr-only 蒙混不过去
    expect(/\/预览\|确认\|弹出\|打开\//.test(census), "普查不再要求说明里讲出后果").toBe(true);
    expect(census.includes("r.width > 0 && r.height > 0"), "普查不再检查说明节点是否真在屏上渲染").toBe(true);
    // ⑥ 三种结局都要有标记，CI 才分得清红／跳过／绿
    for (const marker of ["CONTEXT_CHANGE_RED", "CONTEXT_CHANGE_SKIP", "CONTEXT_CHANGE_OK"]) {
      expect(census.includes(marker), `普查少了 ${marker} 标记`).toBe(true);
    }
  });

  it("账本要挂在 npm script 上，否则没人跑它", () => {
    expect(pkg.scripts["audit:context-change"], "package.json 没注册 audit:context-change").toBe("node scripts/audit-context-change.mjs");
  });
});
