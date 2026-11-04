// @vitest-environment node
/**
 * BD-245（WCAG 2.1.1／2.1.2／2.4.3）· 键盘出路的静态账本
 *
 * 运行时普查（scripts/audit-keyboard-reach.mjs）量的是「焦点真去了哪儿」，
 * 这本账守的是**形状**：那两处修好的交接不许悄悄退回「谁也没接住焦点」。
 * 两条都要求非空命中——找不到形状就红，不然账本自己变哑牙。
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const srcRoot = path.resolve(import.meta.dirname, "../../..");
const read = (rel: string) => readFileSync(path.join(srcRoot, rel), "utf8");

const panel = read("features/search/SearchPanel.tsx");
const actions = read("hooks/useSearchActions.ts");
const transition = read("components/interaction.tsx");

describe("BD-245 键盘出路的点名账（2.1.1／2.1.2／2.4.3 静态形状）", () => {
  it("搜索层：opener 的补记必须在「层刚开、焦点没被输入框抢走」那一刻，而不是只挂在快捷键上", () => {
    // 缺陷①的形状是「只有 Ctrl/⌘+K 那条路记 opener」——所以判据看的是：
    // open 的 effect 里必须自己补记一次，且带「没记过才补」的条件（不覆盖快捷键那条更准的落点）。
    const effect = panel.match(/useEffect\(\(\) => \{\s*if \(!open\) return;[\s\S]{0,900}?\}, \[open\]\)/);
    expect(effect, "找不到 open 的 effect（BD-245 那条统一补记被拆了）").toBeTruthy();
    const body = effect![0];
    expect(/if \(!restoreFocusRef\.current[\s\S]{0,120}document\.activeElement/.test(body), "open 的 effect 里没有『没记过就补记 activeElement』这一句").toBe(true);
    expect(body.includes("restoreFocusRef.current = document.activeElement") || /restoreFocusRef\.current = (document\.activeElement|el)/.test(body)).toBe(true);
    // 关层那条契约不许断：setOpen(false) 之后仍要把焦点交回去
    expect(/const target = restoreFocusRef\.current;[\s\S]{0,160}target[\s\S]{0,60}\.focus\(/.test(panel), "closeSearch 不再把焦点交还给 opener").toBe(true);
  });

  it("键盘选中结果跳屏后：必须把焦点交给 PageTransition 的着陆容器，且那个容器确实存在", () => {
    const branch = actions.match(/if \(result === "navigated"\) \{[\s\S]{0,1200}?\n {8}\}/);
    expect(branch, "navigated 分支找不到了（关层逻辑被重写，这本账要跟着核对）").toBeTruthy();
    const body = branch![0];
    expect(body.includes("setOpen(false)"), "navigated 不再关层——若是有意的，先改普查再改这里").toBe(true);
    expect(/requestAnimationFrame\([\s\S]{0,400}\.motion-page[\s\S]{0,160}\.focus\(/.test(body), "navigated 后没把焦点交给 .motion-page 着陆容器（缺陷②回来了）").toBe(true);

    // 非空断言：那个选择器不许指向一个不存在的容器
    const page = transition.match(/export function PageTransition[\s\S]{0,1400}/);
    expect(page, "PageTransition 不见了").toBeTruthy();
    expect(/className="motion-page[^"]*"[^>]*tabIndex=\{-1\}|tabIndex=\{-1\}[^>]*className="motion-page/.test(page![0]) ||
      /className="motion-page h-full"[\s\S]{0,60}tabIndex=\{-1\}|tabIndex=\{-1\}[\s\S]{0,60}className="motion-page/.test(page![0]), "PageTransition 不再是那个 .motion-page[tabindex=-1] 着陆容器").toBe(true);
    expect(/boxRef\.current\?\.focus\(/.test(page![0]), "PageTransition 自己的接焦 effect 被拆了（BD-202 那条）").toBe(true);
  });

  it("普查自身不许变哑：判据里必须留着那五条实测出来的口径", () => {
    const census = readFileSync(path.join(srcRoot, "../scripts/audit-keyboard-reach.mjs"), "utf8");
    // ① 模态层内清点（否则会把层外正当停靠误报成走不到）
    expect(census.includes('[role="dialog"][aria-modal="true"]'), "普查不再按模态层收窄清点范围").toBe(true);
    // ② 同一站连续重复不算陷阱（日期框实测连吃三跳：对照页 d→d→d→d→b）
    expect(/sameStreak > 10/.test(census), "普查把日期框的分段跳当成陷阱了（对照页实测 d→d→d→d→b）").toBe(true);
    // ③ 走完要数「还活着的清点项」，React 中途换掉的节点不算产品红
    expect(census.includes('[data-kb]') && /aliveSet/.test(census), "普查不再剔除走查中消失的瞬态节点").toBe(true);
    // ④ 组内方向键必须用真键盘（合成事件不触发原生换选）
    expect(census.includes('page.keyboard.press("ArrowDown")'), "普查改用合成 KeyboardEvent 测方向键了").toBe(true);
    // ⑤ tabindex="-1" 不许一笔豁免：原生控件/可操作 role 挂了 -1 就是「只有鼠标能用」，
    //    必须仍算候选。BD-245 的运行时牙 R3（给「同步设置」钮挂 -1）实测：写成「-1 一律不计」
    //    时普查 443→432 照样全绿，这条改完才咬得住。
    expect(/const OPERABLE_ROLE = new Set\(/.test(census), "普查又回到「tabindex=-1 一律不计」——鼠标专用按钮没人管了").toBe(true);
    expect(/if \(neg && el\.closest\(GROUP\)\) continue;/.test(census), "普查不再区分「容器/程序化聚焦目标」和「被摘出 Tab 序列的控件」").toBe(true);
  });
});
