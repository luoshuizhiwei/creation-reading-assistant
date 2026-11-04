/**
 * BD-239 账本：WCAG 2.4.5「多途径」——全局搜索的「页面导航」通道与普查口径的源码账。
 *
 * 主判据是静态普查 scripts/audit-multiple-ways.mjs（npm run audit:multiple-ways）。
 * 它按源码记通道，是因为可达性本身是静态属性：内容型搜索源命中与否取决于此刻库里有什么，
 * 运行时数出来的途径数今天 2 条明天 1 条，守不住任何东西（决定记在普查文件头）。
 * 本台账管普查管不到的三件事：
 *   ① 产品侧的接线不许散架——类型联合、源的 id/label、SOURCE_ORDER 尾项、sourcesForFilter
 *      的「全部」清单、组头文案、`case "screen"` 的守卫，这六处少一处，普查里那条通道就没了；
 *   ② 组头与数据源 label 必须同词（跨文件对拷一次）。「页面导航」这个词在 sources.ts 和
 *      SearchPanel.tsx 各写一遍，改一处就会让组头和「来源不可用」提示分家；
 *   ③ 两处**有意不做**的决定必须钉住：设定卡工具条不加「卡片库」按钮（默认 1360 窗右余量
 *      0px，装上 87px 的钮整组换行、页头 52px → 98px，读数 /tmp/bd239-geo-before.log ↔
 *      /tmp/bd239-geo-after2.log）；收件箱不加「旧灵感中心」按钮（那颗钮指向改道桩，桩上
 *      写着「已并入全局收件箱」——从收件箱把人送去一个让你回收件箱的页面）。
 *   普查自己的牙（别名表、剔除清单、B1/B2 判据、豁免三条件、红绿标记）也在这里逐条钉住：
 *   普查只在缺牙时才报得出来，所以牙本身要有账。
 *
 * 实测基线（/tmp/bd239-census-before.log ↔ /tmp/bd239-census-after.log，改前那一遍是五个
 *   产品文件 `git show HEAD:` 换回去重跑、还原按 sha256 逐字节比对全部 RESTORE_IDENTICAL）：
 *   改前 通道 = 左栏 5、全局搜索 2、页内入口来源 6 ⇒ RED 卡片库（1 条）、RED 资料阅读（1 条）；
 *   改后 通道 = 左栏 5、全局搜索 8、页内入口来源 6 ⇒ 8/8 屏要么 ≥2 条要么豁免，MULTIPLE_WAYS_OK。
 *   活体复核 /tmp/bd239-search-live.log（1360×900 真窗 + round4 种子）：Ctrl+K 搜「卡片库」→
 *   `.uni-search-group[aria-label="页面导航"]` 命中 1 条 → 点后 `.global-card-library` 在场、
 *   壳层 h1=「全局卡片库」；搜「资料阅读」→ 组命中 2 条（资料阅读、书库）→ 落在 reader 屏。
 *
 * 牙齿账（三趟，脚本 /tmp/bd239-teeth.sh；普查与账本都是纯静态，因此不需要 build。每趟都
 *   备份 → 计数断言切片 → 跑普查与账本 → 按 sha256 逐字节还原（三趟全部 RESTORE_IDENTICAL ⇒
 *   ALL_RESTORE_IDENTICAL）→ 复验普查 exit=0、账本 16 passed）：
 *   T-A 缺陷复发（sourcesForFilter 的「全部」清单里撤掉 screenSearchSource，源照旧定义着）⇒
 *     普查 exit=1，红话正是那句「页面导航源定义了却没挂进 sourcesForFilter——写好了不通电」；
 *     账本 1 败/15 通过（聚合那条接线锁）。
 *   T-B 摘掉 B1 那颗牙（navSourceWired 恒真）+ 同一处缺陷仍在 ⇒ 普查 **exit=0 假绿**，
 *     屏幕上照样写「全局搜索 8（B1 页面导航源已接）」、卡片库照样 2 条——那条通道此时产品里
 *     并不存在；账本 2 败/14 通过（缺牙锁 + 聚合接线锁）。这颗牙是判据在守，不是恰好显绿。
 *   T-C 表写窄（注册表里摘掉 card-library 那条 screen 条目）⇒ 普查 exit=1、
 *     「card-library 可切换却没在 registry 登记 screen 条目——页面导航源给不出它，B1 这条通道是假的」；
 *     账本 2 败/14 通过（八条条目逐条在账 + 别名表与注册表同词）。
 *
 * 顺带记下的三条待办（都不在本批顺手改）：registry.ts:91 给灵感中心写的 tip（「阅读摘录、
 *   灵感花和 AI 候选版本」）与那一屏正文（只有改道桩）互相矛盾；跳走后重开搜索时上次关键词
 *   残留且不选中，接着打字会拼成不存在的词、结果为空（活体读数「重开后输入框残留=「卡片库」」）；
 *   audit:focus-rings 在本批全套普查里报一红——项目·概览最近编辑那一行 `button.(无类名)
 *   [默认场景第一章刚刚]` outline=none 0px shadow=none（:focus-visible 只换背景与边框色）。
 *   归因已做：五个产品文件换回 HEAD、重建后重跑同一把枪，两份读数**逐字相同**
 *   （/tmp/bd239-fr-head.log ↔ /tmp/bd239-fr-mine.log，diff 无输出、停靠点同为 553），
 *   所以这一红不是本批带进来的，属焦点可见那一族，另立一批判。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const census = read("../../../scripts/audit-multiple-ways.mjs");
const pkg = read("../../../package.json");
const types = read("../../types/search.ts");
const sources = read("../../features/search/sources.ts");
const aggregate = read("../../features/search/aggregate.ts");
const panel = read("../../features/search/SearchPanel.tsx");
const actions = read("../../hooks/useSearchActions.ts");
const registry = read("../../features/navigation/registry.ts");
const cardsPage = read("../../features/creation/cards/CardsPage.tsx");
const inboxPage = read("../../features/creation/inbox/InboxPage.tsx");
const inboxQuick = read("../../features/creation/inbox/components/InboxQuickInput.tsx");

/** sourcesForFilter 的函数体（与普查同一取法，两边对的是同一段接线）。 */
const filterBodyOf = (src: string) => {
  const m = src.match(/export function sourcesForFilter[\s\S]*?\n\}/);
  return m ? m[0] : "";
};
const filterBody = filterBodyOf(aggregate);

describe("BD-239 多途径：普查接线与产品六处接线的源码账", () => {
  it("普查已接入 package.json（npm run audit:multiple-ways）", () => {
    expect(pkg).toContain('"audit:multiple-ways": "node scripts/audit-multiple-ways.mjs"');
  });

  /* ------------------------------------------------ 产品侧：那条通道需要的六处接线 */

  it("类型：源 id 与意图 kind 都含 screen，意图带 screen 字段", () => {
    expect(types).toContain('export type UnifiedSearchSourceId = "project" | "chapter" | "scene" | "card" | "inbox" | "library" | "inspiration" | "screen";');
    expect(types).toContain('kind: "project" | "chapter" | "scene" | "card" | "inbox" | "book" | "inspiration" | "screen";');
    expect(types).toContain("screen?: AppScreen;");
    // screen 字段的类型来自 app-store，不是又抄一份屏幕名单
    expect(types).toContain('import type { AppScreen } from "@/stores/app-store";');
  });

  it("数据源：id=screen、label=页面导航、只在「全部」里参与、全部词都要命中", () => {
    expect(sources).toContain('export const screenSearchSource: SearchSource = {');
    expect(sources).toContain('  id: "screen",');
    expect(sources).toContain('  label: "页面导航",');
    // 作用域守卫：scoped 搜索是找内容，不是找页面
    expect(sources).toContain('if (filter !== "all") return [];');
    // 分词与「全部词都要命中」——缺这一条，搜「卡片 库」这种带空格的词会半命中
    expect(sources).toContain('const terms = keyword.trim().toLowerCase().split(/\\s+/).filter(Boolean);');
    expect(sources).toContain("if (terms.length === 0) return [];");
    expect(sources).toContain('          matchedAll = false;');
    // 打分档：项名 3 / 副名与检索词 2 / 说明 1（与 searchSettings 同口径）
    expect(sources).toContain('        [entry.label.toLowerCase(), 3],');
    expect(sources).toContain('        [(entry.hint ?? "").toLowerCase(), 2],');
    expect(sources).toContain('        [(entry.keywords ?? "").toLowerCase(), 2],');
    expect(sources).toContain('        [(entry.tip ?? "").toLowerCase(), 1]');
    // 同分保持注册表顺序（Array.prototype.sort 稳定）
    expect(sources).toContain("matched.sort((a, b) => b.score - a.score);");
    // 条目形状：id 带 screen: 前缀、落点是注册表那个屏幕
    expect(sources).toContain("      id: `screen:${item.screen}`,");
    expect(sources).toContain('      source: "screen" as UnifiedSearchSourceId,');
    expect(sources).toContain('      originLabel: "一级导航",');
    expect(sources).toContain('      intent: { kind: "screen" as const, screen: item.screen }');
  });

  it("聚合：SOURCE_ORDER 尾项是 screen，「全部」清单里有 screenSearchSource", () => {
    expect(filterBody).toContain('return [creationSearchSource, inboxSearchSource, librarySearchSource, screenSearchSource];');
    expect(aggregate).toContain('  "library",\n  "screen"\n];');
    // 反向锁：被摘掉的旧灵感源不许悄悄回到「全部」里（它回来了，普查 B2 的账就得重算）
    expect(filterBody).not.toContain("inspirationSearchSource");
    expect(aggregate).not.toContain("import { inspirationSearchSource");
  });

  it("组头与数据源 label 同词（跨文件对拷，改一处就会分家）", () => {
    const sourceLabel = sources.match(/screenSearchSource: SearchSource = \{[\s\S]{0,200}label: "([^"]+)"/)?.[1];
    const groupLabel = panel.match(/const GROUP_LABELS: Record<UnifiedSearchSourceId, string> = \{[\s\S]*?\n\};/)?.[0].match(/screen: "([^"]+)"/)?.[1];
    expect(sourceLabel).toBe("页面导航");
    expect(groupLabel).toBe("页面导航");
    expect(groupLabel).toBe(sourceLabel);
    // 组头是真的渲染进 aria-label，不是只写个可视文字
    expect(panel).toContain('className="uni-search-group" aria-label={GROUP_LABELS[source]}');
    expect(panel).toContain("  screen: Compass");
    expect(panel).toMatch(/import \{[^}]*Compass[^}]*\} from "lucide-react";/);
  });

  it("落点解释器：case \"screen\" 有守卫，缺 screen 就 invalid 不换屏", () => {
    expect(actions).toContain('        case "screen": {');
    expect(actions).toContain("          if (!intent.screen) return \"invalid\";");
    expect(actions).toContain("          useAppStore.getState().setScreen(intent.screen);");
    expect(actions).toContain('          return "navigated";');
  });

  /* ------------------------------------------------ 普查侧：判据的牙 */

  it("定点锁：八屏别名逐条在账，一条不多一条不少", () => {
    const block = census.match(/const SCREEN_ALIASES = \{[\s\S]*?\n\};/)?.[0] ?? "";
    for (const pair of [
      'projects: "项目"',
      '"card-library": "卡片库"',
      'inbox: "收件箱"',
      'inspiration: "灵感中心"',
      'library: "书库"',
      'reader: "资料阅读"',
      'stats: "阅读统计"',
      'settings: "设置"'
    ]) expect(block).toContain(pair);
    expect(block.split("\n").filter((l) => l.includes(": \"")).length).toBe(8);
    // 别名表与注册表 label 同词（两张表一旦分家，普查记的名字就不是界面上的名字）
    for (const pair of block.split("\n").filter((l) => l.includes(": \""))) {
      const label = pair.match(/: "([^"]+)"/)?.[1];
      expect(registry).toContain(`label: "${label}"`);
    }
  });

  it("剔除清单钉死：壳层、搜索解释器、store 自己不算页内入口", () => {
    expect(census).toContain('const EXCLUDED_FILES = new Set(["App.tsx", "useSearchActions.ts", "app-store.ts"]);');
    // App.tsx 的 screenContent / ErrorBoundary 兜底 / RecoveryPrompt 都是崩溃恢复形状
    expect(census).toContain("只在崩溃/恢复态出现");
    // 注释里的 setScreen 不算调用：去注释这一步是这条排除的全部意义
    expect(census).toContain("const stripComments = (text) =>");
    expect(census).toContain(".replace(/\\/\\*[\\s\\S]*?\\*\\//g, \"\")");
  });

  it("B1 的两颗牙：定义了**且**挂进 sourcesForFilter 才算一条通道", () => {
    expect(census).toContain('const navSourceDefined = /screenSearchSource: SearchSource = \\{[\\s\\S]{0,200}id: "screen"/.test(sourcesSrc);');
    expect(census).toContain('const navSourceWired = /\\bscreenSearchSource\\b/.test(filterBody);');
    expect(census).toContain("if (navSourceDefined && !navSourceWired) {");
    expect(census).toContain("写好了不通电，等于没有这条通道");
    expect(census).toContain('if (navSourceDefined && navSourceWired) {');
    // 逐屏记名，不抽样：注册表登记几个 screen 条目就记几条
    expect(census).toContain('for (const screen of registryScreens) note(screen, "全局搜索", "页面导航源（注册表 screen 条目）");');
    expect(census).toContain('/type: "screen", screen: "([a-z-]+)"/g');
    expect(census).toContain("可切换却没在 registry 登记 screen 条目");
    // 缺牙锁：wired 那颗牙与「≥2 就红」这条判据本身都不许被稀释——
    // 牙齿账 T-B 就是把 navSourceWired 改成恒真、看这处会不会当场红
    expect(census).not.toContain("const navSourceWired = true;");
    expect(census).toContain("if (bad.length) {");
    expect(census).toContain('if (total < 2) bad.push({ screen, total, detail });');
  });

  it("B2 的对照表：摘除的落点不算途径（旧灵感那一支钉在表里）", () => {
    const table = census.match(/const INTENT_SOURCE_SYMBOL = \{[\s\S]*?\n\};/)?.[0] ?? "";
    for (const pair of [
      'project: "creationSearchSource"',
      'chapter: "creationSearchSource"',
      'scene: "creationSearchSource"',
      'card: "creationSearchSource"',
      'inbox: "inboxSearchSource"',
      'book: "librarySearchSource"',
      'inspiration: "inspirationSearchSource"'
    ]) expect(table).toContain(pair);
    // 产生源不参与 sourcesForFilter → 该 kind 的落点不计（这条 if 是 B2 唯一的牙）
    expect(census).toContain("if (!new RegExp(`\\\\b${symbol}\\\\b`).test(filterBody)) continue;");
  });

  it("页内入口按来源文件去重：同页两钮是一条路", () => {
    expect(census).toContain("同一页里的两个按钮");
    expect(census).toContain("const base = path.basename(full);");
    expect(census).toContain('if (EXCLUDED_FILES.has(base)) continue;');
    // 自己链自己不算「从别处到达这一屏」，组件归属由 App.tsx 的 screenContent 判定
    expect(census).toContain('for (const m of appSrc.matchAll(/"([a-z-]+)"\\s*:\\s*<([A-Z][A-Za-z0-9]*)\\s*\\/>/g)) {');
    expect(census).toContain('if (screenRootComponent[screen] === base) continue; // 屏内动作，不是「从别处到达」');
  });

  it("豁免只有灵感中心一屏，且三条判据齐全（少一条就失去豁免）", () => {
    const block = census.match(/const STUB_EXEMPTIONS = \{[\s\S]*?\n\};/)?.[0] ?? "";
    expect(block.split("\n").filter((l) => /^\s{2}\w+: \(report\)/.test(l)).length).toBe(1);
    expect(block).toContain("  inspiration: (report) => {");
    expect(block).toContain('if (!src.includes("旧灵感中心已并入全局收件箱")) reasons.push("屏上已没有那句改道原话");');
    expect(block).toContain('if (!/setScreen\\(\\s*"inbox"\\s*\\)/.test(src)) reasons.push("屏上已没有「跳回收件箱」的动作");');
    expect(block).toContain('const buttons = src.split("<Button").length - 1;');
    expect(block).toContain('if (buttons !== 1) reasons.push(`屏上可见按钮 ${buttons} 颗（改道桩应当只有 1 颗）`);');
    // 豁免不成立时按 ≥2 重判，输出里要看得见「豁免失效」
    expect(census).toContain("豁免失效：");
    // 桩屏本体三条判据仍在原位
    const stub = read("../../features/inspiration/InspirationPage.tsx");
    expect(stub).toContain("旧灵感中心已并入全局收件箱");
    expect(stub).toMatch(/setScreen\("inbox"\)/);
    expect(stub.split("<Button").length - 1).toBe(1);
  });

  it("红绿标记与退出码：判据失效也是红，不许静默跳过", () => {
    expect(census).toContain("MULTIPLE_WAYS_RED");
    expect(census).toContain("MULTIPLE_WAYS_OK");
    // 七处形状失配（SIDEBAR_IDS / 注册表 screen 条目 / sourcesForFilter / 定义了没接线 /
    // 别名表缺项 / 别名表陈旧 / 屏幕未登记）+ 最终红判据 = 八处 exit(1)
    expect(census.split("process.exit(1);").length - 1).toBe(8);
    expect(census).toContain("本普查的判据已失效");
    expect(census).toContain("搜索作用域的接线形状变了");
    expect(census).toContain("表与屏幕名单脱钩了");
  });

  /* ------------------------------------------------ 有意不做的两处 */

  it("反向锁·设定卡工具条不加「卡片库」按钮：默认 1360 窗右余量 0px，装了就换行", () => {
    // 实测：改前那一排正好贴到容器右缘（x=88 w=1256，末子项右缘 1344，/tmp/bd239-geo-before.log）
    // 装上 87px 的钮 → 整组换行、页头 52px 变 98px、右对齐掉成左对齐（/tmp/bd239-geo-after2.log）
    // 而 electron/main/index.ts 的默认窗口就是 1360×860。
    expect(cardsPage).not.toContain('setScreen("card-library")');
    // 强一档：这一屏根本没有任何换屏调用——被否掉的那颗按钮不许换个写法回来
    expect(cardsPage).not.toContain("setScreen");
    // 而界面上确实有「叫你去卡片库、当场又没路」的字样，这正是要补通道的位置
    expect(cardsPage).toContain("请在卡片库管理");
    // 全局库那屏自己没有回路入口（有就变成自己链自己的假账）
    const lib = read("../../features/creation/cards/GlobalCardLibraryPage.tsx");
    expect(lib).not.toContain('setScreen("card-library")');
  });

  it("反向锁·收件箱不加「旧灵感中心」按钮：那颗钮指向一个让用户回收件箱的桩", () => {
    expect(inboxPage).not.toContain('setScreen("inspiration")');
    expect(inboxQuick).not.toContain('setScreen("inspiration")');
  });

  it("注册表那份「同时喂侧栏、全局搜索、页面提示」的名单：八条 screen 条目都带 keywords", () => {
    const entries = [...registry.matchAll(/type: "screen", screen: "([a-z-]+)", group: "一级导航", label: "([^"]+)", hint: "([^"]+)", keywords: "([^"]+)"/g)];
    expect(entries).toHaveLength(8);
    for (const m of entries) expect(m[4].trim().length).toBeGreaterThan(0);
    expect(registry).toContain('const SIDEBAR_IDS = ["screen:projects", "screen:card-library", "screen:inbox", "screen:library", "screen:settings"] as const;');
  });
});
