/**
 * BD-239 常驻普查：多途径（WCAG 2.4.5）——每一屏都要有两条及以上互不相同的到达途径。
 *
 * 这一族是量出来的。摸底（/tmp/bd238-245.log）把 8 个可切换屏幕的入口逐条数过；命令面板
 * （Ctrl+P）实测只列项目内命令（CreationProjectsPage 建的 view.* / action.* 命令表），
 * 不含应用级换屏，因此不算通道。摸底那一遍只按「左栏有没有这一项」粗看，得到「一屏真单入口、
 * 一屏是改道桩」；本普查改成逐屏记通道来源后，抓出的是**两**屏单入口：
 *  · **卡片库**：全仓除左栏那一项外没有任何一处指向它。搜索里 card 命中的落点是项目内
 *    「设定卡」视图（useSearchActions.ts 的 case "card" 把屏设成 projects），不是跨项目总库。
 *  · **资料阅读**：不在左栏的五项里，全仓唯一的门是书库页内换屏（useLibraryActions.ts）——
 *    「左栏没有」不等于「两条门都有」，这一屏是记账记出来的，不是摸底摸出来的。
 *  · **灵感中心**：实测是改道桩——页面上没有任何旧数据，只有一句「旧灵感中心已并入全局收件箱」
 *    （InspirationPage.tsx:28）和一颗「前往全局收件箱」按钮（同文件 :34）；aggregate.ts 还把
 *    旧灵感源从「全部」结果里摘了（点进去定位不到条目）。给它补第二条入口=把用户往死路上多送
 *    一次 ⇒ 不判红，走下面带退场条件的有意豁免。
 *
 * 修法是先量出来的、不是先想出来的。两个候选落点都被实测否了：
 *  ① 设定卡页头加「卡片库」按钮（真装上过，又撤了）：这一屏的工具条在**默认窗口宽度**下
 *    右余量 0px——改前那一排正好贴到容器右缘（x=88 w=1256，末子项右缘 1344，读数
 *    /tmp/bd239-geo-before.log）；装上 87px 的钮之后整组换行，页头 52px → 98px、右对齐
 *    掉成左对齐（/tmp/bd239-geo-after2.log），而 electron/main/index.ts:851 的默认窗口就是
 *    1360×860。用默认视图长样子的代价换一条途径，不划算。
 *  ② 桌面 dock 加一颗（同步→设置 那颗的同族）：dock 末行 x=14 w=187、末子项右缘 148，
 *    余 53px，而「卡片库」三字的钮实测要 ~63px（对照 数据目录 4 字 74px / 同步 2 字 52px，
 *    /tmp/bd239-dock.log）——1360 下这一行是 nowrap，装不下就是溢出。
 * 于是补的是**第三条本来就设计好、却没人接的路**：navigation/registry.ts 顶部写着「一条条目
 * 同时喂侧栏、全局搜索、页面提示」，而每条 screen 条目带的 keywords（"卡片 角色 地点 组织
 * 设定 世界观" 这类）实测谁都不消费——设置检索只取 setting 条目，统一搜索的源只有
 * 创作/收件箱/书库/旧灵感。本批把它接成全局搜索的「页面导航」源（WCAG G171 搜索接口）：
 * 零版式代价（结果只在搜索弹层里出现），并且一次给全部 8 屏补上第二条途径，不是只补一屏。
 *
 * 为什么按源码扫而不是在运行时数：可达性是静态属性，而运行时数出来的途径数会随种子内容变化
 * ——内容型搜索源（收件箱/书库/创作）命中与否取决于此刻库里有什么，今天 2 条明天 1 条，
 * 这种普查守不住任何东西。页面导航源不读盘，它命中的一定是注册表登记的屏幕，所以这一条通道
 * 反而是静态可证的；本普查因此只读源码，不拉运行时（不需要 build，也不需要显示环境）。
 *
 * 通道定义（三条，逐条有意）：
 *  A 左栏一级导航——registry.ts 的 SIDEBAR_IDS 登记项（来自注册表，不是猜渲染顺序）。
 *  B 全局搜索——两条腿都要真接上才算：
 *     B1 页面导航源：sources.ts 里 id 为 "screen" 的源存在，**且它确实出现在
 *        aggregate.ts 的 sourcesForFilter 返回值里**，此时按注册表登记的每个 screen 记一条
 *        （表写窄=通道变窄，这里逐屏记名，不抽样）。
 *     B2 逐意图落点：useSearchActions.ts 里 `case "<kind>" … setScreen("<screen>")` 的配对，
 *        且该 kind 的产生源确实参与 sourcesForFilter——旧灵感就是这么被摘掉的
 *        （inspirationSearchSource 不在任何一个 return 里），摘掉的落点不算途径。
 *  C 页内入口——其余任何文件里的 setScreen 目标，按来源文件去重计数：同一页里的两个按钮
 *    是同一条路，两个不同页面各有一颗按钮才是两条路。
 * 剔除（不是途径的形状，写死在这里免得下一个人重新判一遍）：
 *  · App.tsx：壳层接线。screenContent 的组件映射、ErrorBoundary 的 escapeLabel 兜底、
 *    RecoveryPrompt 的「回到书库」——只在崩溃/恢复态出现，正常用户走不到，不算明路。
 *  · 注释与 JSDoc：lazy-recovery.ts 的文档里写着 setScreen("projects")，那是说明不是调用。
 *  · 自己链自己：本文件正是这一屏的根组件时，那条换屏是屏内动作，不构成「从别处到达这一屏」
 *   （组件归属由 App.tsx 的 screenContent 判定）。
 * 有意豁免（纯改道桩屏，带退场条件）：屏上必须同时满足 ① 仍有那句改道原话 ② 仍有跳回
 *  「收件箱」的动作 ③ 可见按钮只有那一颗——三条任何一条不再成立（有人把这屏做回真功能页），
 *  豁免当场失效、该屏按 ≥2 途径重判。豁免逐屏写判据串，不接受「加个名字就免检」。
 * 定点锁：8 个屏幕的中文别名表必须与 app-store.ts 的 APP_SCREENS 一一对应——新增一屏而没登记
 *  别名，本普查当场红，防止「表被写窄」。
 * 红判据：任一（未豁免）屏幕通道 < 2 → MULTIPLE_WAYS_RED，exit 1。
 *
 * 实测基线（本普查落地时逐字记的，改前/改后各跑一遍：/tmp/bd239-census-before.log
 * 与 /tmp/bd239-census-after.log；改前那一遍是把五个产品文件 `git show HEAD:` 换回去后重跑，
 * 还原按 sha256 逐字节比对，五份全部 RESTORE_IDENTICAL ⇒ ALL_RESTORE_IDENTICAL）：
 *  · 改**前**：通道 = 左栏 5、全局搜索 2（B1 未接，只剩 B2 的两个逐意图落点：project→projects、
 *    inbox→inbox）、页内入口来源 6 ⇒ **两**屏单通道：
 *      RED 卡片库（1 条：左栏）；RED 资料阅读（1 条：页内入口←useLibraryActions.ts）
 *    资料阅读这一屏在改前同样只有一条门，而且那条门在书库页内——它不在左栏的 SIDEBAR_IDS 里，
 *    摸底时按「左栏没有＝缺」粗看过，逐屏记账才把它抓出来。灵感中心走豁免 ⇒
 *    MULTIPLE_WAYS_RED，exit 1。
 *  · 改**后**：通道 = 左栏 5、全局搜索 8（B1 页面导航源接通，逐屏记名）、页内入口来源 6；
 *    卡片库 2 条（左栏＋全局搜索）、资料阅读 2 条（全局搜索＋页内入口）、阅读统计 3 条、
 *    设置 3 条、项目 3 条、收件箱 4 条、书库 5 条；灵感中心豁免判据仍成立 ⇒
 *    8/8 屏要么 ≥2 条、要么豁免 ⇒ MULTIPLE_WAYS_OK，exit 0。
 *  · 活体复核（不是只信源码；探针 /tmp/bd239-search-live.cjs，读数
 *    /tmp/bd239-search-live.log，1360×900 真窗 + round4 种子档案）：
 *      Ctrl+K → 输入框出现 → 搜「卡片库」→ `.uni-search-group[aria-label="页面导航"]` 在场、
 *      命中 1 条「卡片库」→ 点它 → 弹层关闭、`.global-card-library` 在场、壳层 h1=「全局卡片库」、
 *      左栏选中项文本=「卡片库世界观」（label+hint 连读）。
 *      搜「资料阅读」→ 同组命中 2 条（资料阅读、书库——书库的 hint 就叫「资料阅读」）→ 点首条 →
 *      落在 reader 屏；未选书时该屏是空态「没有打开资料 / 从书库中选择一份…」。
 *    空态里没有页内出路（要回左栏书库），这属「进来的门有了、屋里还没东西」的另一族缺陷，
 *    不在 2.4.5 这一族里顺手修，已单独记账待后续批次。
 *  · 活体顺手量到的另一件事：跳走后再按 Ctrl+K，上次的关键词还留在框里且不选中
 *    （读数：重开后输入框残留=「卡片库」），接着打字会拼成「卡片库资料阅读」这种不存在的词、
 *    结果为空。本普查第一版探针就是这样误判成「页面导航组不在场」的——已排除是产品的组没出，
 *    改用整串替换复测为在场。这一条也另记一批，不在本批顺手改。
 *
 * 用法：npm run audit:multiple-ways（纯静态，不需要 build 与显示环境）
 */
import { readFileSync, readdirSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const srcDir = path.join(root, "src");

/** 定点锁：屏幕 → 界面中文主名（与 registry.ts 的 label / 页头标题同词，别处不要另起名字）。 */
const SCREEN_ALIASES = {
  projects: "项目",
  "card-library": "卡片库",
  inbox: "收件箱",
  inspiration: "灵感中心",
  library: "书库",
  reader: "资料阅读",
  stats: "阅读统计",
  settings: "设置"
};

/** 剔除清单：这些文件里的 setScreen 不是「用户从别处走过来」的途径。 */
const EXCLUDED_FILES = new Set(["App.tsx", "useSearchActions.ts", "app-store.ts"]);
const SEARCH_FILE = "useSearchActions.ts";

/** B2 的对照表：搜索意图 kind → 产生它的源符号名（源不参与 sourcesForFilter 就不算途径）。 */
const INTENT_SOURCE_SYMBOL = {
  project: "creationSearchSource",
  chapter: "creationSearchSource",
  scene: "creationSearchSource",
  card: "creationSearchSource",
  inbox: "inboxSearchSource",
  book: "librarySearchSource",
  inspiration: "inspirationSearchSource"
};

const read = (rel) => readFileSync(path.join(root, rel), "utf8");

/** 去注释：块注释与行注释整体摘掉，避免把 JSDoc 里的示例当调用。 */
const stripComments = (text) =>
  text.replace(/\/\*[\s\S]*?\*\//g, "").replace(/(^|[^\S\n])\/\/[^\n]*/g, "$1");

/** 递归列出 src 下全部 ts/tsx，测试目录与测试文件不参与（不是产品源码）。 */
const listSources = (dir, acc = []) => {
  for (const name of readdirSync(dir)) {
    const full = path.join(dir, name);
    if (statSync(full).isDirectory()) {
      if (name === "__tests__" || name === "node_modules") continue;
      listSources(full, acc);
      continue;
    }
    if (!/\.(ts|tsx)$/.test(name)) continue;
    if (/\.test\.(ts|tsx)$/.test(name)) continue;
    acc.push(full);
  }
  return acc;
};

/* ---------------------------------------------------------------- 通道 A：左栏 */

const registrySrc = read("src/features/navigation/registry.ts");
const sidebarIdsLine = registrySrc.match(/const SIDEBAR_IDS\s*=\s*\[([^\]]*)\]/);
if (!sidebarIdsLine) {
  console.log("MULTIPLE_WAYS_RED：registry.ts 里找不到 SIDEBAR_IDS——左栏名单换了形状，本普查的判据已失效。");
  process.exit(1);
}
const sidebarScreens = new Set([...sidebarIdsLine[1].matchAll(/"screen:([a-z-]+)"/g)].map((m) => m[1]));

/** 注册表登记的全部 screen 条目（B1 覆盖的就是这些；顺序即登记顺序）。 */
const registryScreens = [...registrySrc.matchAll(/type: "screen", screen: "([a-z-]+)"/g)].map((m) => m[1]);
if (registryScreens.length === 0) {
  console.log("MULTIPLE_WAYS_RED：registry.ts 里取不到任何 screen 条目——注册表换了形状，B1 的账没法记。");
  process.exit(1);
}

/* ------------------------------------------------- 屏幕 → 根组件（判「自己链自己」） */

const appSrc = read("src/app/App.tsx");
const screenRootComponent = {};
for (const m of appSrc.matchAll(/"([a-z-]+)"\s*:\s*<([A-Z][A-Za-z0-9]*)\s*\/>/g)) {
  screenRootComponent[m[1]] = `${m[2]}.tsx`;
}

/* ------------------------------------------------------ 通道 B / C：源码里的换屏 */

const channels = {};
const note = (screen, kind, detail) => {
  (channels[screen] ||= []).push({ kind, detail });
};
for (const screen of sidebarScreens) note(screen, "左栏", "registry SIDEBAR_IDS 登记项");

const aggregateSrc = stripComments(read("src/features/search/aggregate.ts"));
const sourcesSrc = stripComments(read("src/features/search/sources.ts"));
const filterBodyMatch = aggregateSrc.match(/export function sourcesForFilter[\s\S]*?\n\}/);
const filterBody = filterBodyMatch ? filterBodyMatch[0] : "";
if (!filterBody) {
  console.log("MULTIPLE_WAYS_RED：aggregate.ts 里取不到 sourcesForFilter——搜索作用域的接线形状变了。");
  process.exit(1);
}

// B1：页面导航源。定义在 sources.ts、并且真挂进 sourcesForFilter 的返回值，才算一条通道。
const navSourceDefined = /screenSearchSource: SearchSource = \{[\s\S]{0,200}id: "screen"/.test(sourcesSrc);
const navSourceWired = /\bscreenSearchSource\b/.test(filterBody);
if (navSourceDefined && !navSourceWired) {
  console.log("MULTIPLE_WAYS_RED：页面导航源定义了却没挂进 sourcesForFilter——写好了不通电，等于没有这条通道。");
  process.exit(1);
}
const searchScreens = new Set();
if (navSourceDefined && navSourceWired) {
  for (const screen of registryScreens) note(screen, "全局搜索", "页面导航源（注册表 screen 条目）");
  registryScreens.forEach((screen) => searchScreens.add(screen));
}

// B2：逐意图落点（源必须真参与搜索；被摘掉的源不算途径）。
const searchActionsSrc = stripComments(read(`src/hooks/${SEARCH_FILE}`));
for (const [, kind, screen] of searchActionsSrc.matchAll(/case "([a-z]+)":(?:\s*\/\/[^\n]*)?[\s\S]{0,420}?setScreen\(\s*"([a-z-]+)"\s*\)/g)) {
  const symbol = INTENT_SOURCE_SYMBOL[kind];
  if (!symbol) continue; // 未知意图：没有对照表就不敢替它记通道
  if (!new RegExp(`\\b${symbol}\\b`).test(filterBody)) continue;
  if (searchScreens.has(screen)) continue;
  searchScreens.add(screen);
  note(screen, "全局搜索", `${SEARCH_FILE} 的 ${kind} 落点`);
}

// 通道 C：其余文件的页内入口，按来源文件去重。
const inPageSources = {};
for (const full of listSources(srcDir)) {
  const base = path.basename(full);
  if (EXCLUDED_FILES.has(base)) continue;
  const text = stripComments(read(path.relative(root, full)));
  for (const m of text.matchAll(/setScreen\(\s*"([a-z-]+)"/g)) {
    const screen = m[1];
    if (screenRootComponent[screen] === base) continue; // 屏内动作，不是「从别处到达」
    (inPageSources[screen] ||= new Set()).add(base);
  }
}
for (const [screen, set] of Object.entries(inPageSources)) {
  for (const base of set) note(screen, "页内入口", base);
}

/* ---------------------------------------------------------- 有意豁免：纯改道桩屏 */

/** 豁免判据（逐条实测，不成立就失去豁免）。灵感中心屏的形状写在文件头，这里只核三件事。 */
const STUB_EXEMPTIONS = {
  inspiration: (report) => {
    const src = stripComments(read("src/features/inspiration/InspirationPage.tsx"));
    const reasons = [];
    if (!src.includes("旧灵感中心已并入全局收件箱")) reasons.push("屏上已没有那句改道原话");
    if (!/setScreen\(\s*"inbox"\s*\)/.test(src)) reasons.push("屏上已没有「跳回收件箱」的动作");
    const buttons = src.split("<Button").length - 1;
    if (buttons !== 1) reasons.push(`屏上可见按钮 ${buttons} 颗（改道桩应当只有 1 颗）`);
    report.exemptEvidence = `原话在、跳回 inbox 在、按钮 ${buttons} 颗`;
    return reasons;
  }
};

/* ---------------------------------------------------------------------- 判定与输出 */

const storeScreens = [
  ...read("src/stores/app-store.ts").match(/APP_SCREENS\s*=\s*\[([^\]]*)\]/)[1].matchAll(/"([a-z-]+)"/g)
].map((m) => m[1]);

const unknown = storeScreens.filter((s) => !SCREEN_ALIASES[s]);
if (unknown.length) {
  console.log(`MULTIPLE_WAYS_RED：APP_SCREENS 新增 ${unknown.join("、")} 而定点锁的别名表没有登记——先补表再谈途径。`);
  process.exit(1);
}
const stale = Object.keys(SCREEN_ALIASES).filter((s) => !storeScreens.includes(s));
if (stale.length) {
  console.log(`MULTIPLE_WAYS_RED：别名表里的 ${stale.join("、")} 已不在 APP_SCREENS——表与屏幕名单脱钩了。`);
  process.exit(1);
}
// 注册表登记的屏幕必须就是 APP_SCREENS 全集：漏登记的那一屏在搜索里根本搜不到。
const notRegistered = storeScreens.filter((s) => !registryScreens.includes(s));
if (notRegistered.length) {
  console.log(`MULTIPLE_WAYS_RED：${notRegistered.join("、")} 可切换却没在 registry 登记 screen 条目——页面导航源给不出它，B1 这条通道是假的。`);
  process.exit(1);
}

const bad = [];
const report = {};
console.log(
  `屏幕范围 ${storeScreens.length} 个；通道 = 左栏 ${sidebarScreens.size}、全局搜索 ${searchScreens.size}（B1 页面导航源${navSourceWired ? "已接" : "未接"}、B2 逐意图落点）、页内入口来源 ${Object.keys(inPageSources).length}`
);
for (const screen of storeScreens) {
  const list = channels[screen] || [];
  const total = (sidebarScreens.has(screen) ? 1 : 0) + (searchScreens.has(screen) ? 1 : 0) + (inPageSources[screen]?.size || 0);
  const detail = list.map((c) => `${c.kind}←${c.detail}`).join("、") || "（无）";
  const exempt = STUB_EXEMPTIONS[screen];
  if (exempt) {
    const why = exempt(report);
    if (why.length === 0) {
      console.log(`  skip ${SCREEN_ALIASES[screen]}（${screen}）${total} 条：${detail}｜豁免判据成立（${report.exemptEvidence}）`);
      continue;
    }
    console.log(`  ${total >= 2 ? "ok" : "RED"} ${SCREEN_ALIASES[screen]}（${screen}）${total} 条：${detail}｜豁免失效：${why.join("、")}`);
    if (total < 2) bad.push({ screen, total, detail });
    continue;
  }
  console.log(`  ${total >= 2 ? "ok" : "RED"} ${SCREEN_ALIASES[screen]}（${screen}）${total} 条：${detail}`);
  if (total < 2) bad.push({ screen, total, detail });
}

if (bad.length) {
  console.log(
    `MULTIPLE_WAYS_RED：${bad.length} 屏只有一条途径——${bad.map((b) => `${SCREEN_ALIASES[b.screen]}（${b.total} 条：${b.detail}）`).join("；")}（WCAG 2.4.5）。`
  );
  process.exit(1);
}
console.log(`MULTIPLE_WAYS_OK：${storeScreens.length} 屏每屏要么有 ≥2 条互不相同的到达途径（左栏 / 全局搜索 / 页内入口），要么改道桩豁免判据仍成立。`);
