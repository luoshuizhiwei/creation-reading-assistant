#!/usr/bin/env node
// 守卫注入测试：逐条把 needle 改坏，verify-design-tokens 必须变红。
// 每个用例只改一处，测完立即从备份还原；任何一次“改坏了却还是绿的”都算守卫无效。
import { readFileSync, writeFileSync, copyFileSync, mkdirSync, rmSync } from "node:fs";
import { spawnSync } from "node:child_process";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");
const backup = path.join(root, ".inj-backup");
const files = [
  "src/styles/tokens.css",
  "src/components/ui.tsx",
  "tailwind.config.ts",
  "src/styles/editorial-studio.css",
  // 后代按钮规则用例改的这两份，必须一起备份：漏了就会把「故意改坏」的代码留在工作区。
  "src/styles.css",
  "src/features/creation/history/history-local.css",
  // 跨目录盲区用例改这份：scene-radar.css 由 editor/SceneRadar.tsx 引入，
  // 却被 editor/desk/WritingDeskMargin.tsx 复用，是守卫原本漏检的那类样式表。
  "src/features/creation/editor/scene-radar.css",
  // 第 7 步硬编码红的三条用例分别改这两份局部样式表。
  "src/features/creation/replace/replace.css",
  "src/features/search/search.css",
  // 第 8 步的 TSX 分支用例改这份：它是 4 处 bg-copper + text-[color:var(--fg-on-solid)] 之一。
  "src/features/library/LibraryPage.tsx",
  // 圆角棘轮用例改这两份：一份是「已有预算的文件」（search.css，预算 5），
  // 一份是「预算表里根本没有的文件」（settings-controls.css，视同 0）——
  // 后者测的是新增债务落进零预算文件时会不会漏判。
  "src/features/settings/settings-controls.css"
];

const backedUp = new Set(files);

mkdirSync(backup, { recursive: true });
for (const f of files) copyFileSync(path.join(root, f), path.join(backup, f.replace(/\//g, "__")));

function restore() {
  for (const f of files) copyFileSync(path.join(backup, f.replace(/\//g, "__")), path.join(root, f));
}

// 本脚本会临时改写真实源文件。若中途异常退出（含 Ctrl-C），
// 必须把文件还原，否则工作区会留下一份被故意改坏的代码。
let cleaned = false;
function cleanup() {
  if (cleaned) return;
  cleaned = true;
  restore();
  rmSync(backup, { recursive: true, force: true });
}
process.on("exit", cleanup);
for (const signal of ["SIGINT", "SIGTERM", "uncaughtException", "unhandledRejection"]) {
  process.on(signal, () => {
    console.error(`\n[inject-test] 中断（${signal}），已还原被临时改写的源文件`);
    cleanup();
    process.exit(signal.startsWith("SIG") ? 130 : 1);
  });
}

function mutate(file, from, to) {
  // 没进备份清单的文件一律拒绝改写：改坏了还原不了，等于往工作区里植入故意损坏的代码。
  if (!backedUp.has(file)) throw new Error(`文件未备份，拒绝改写：${file}`);
  const p = path.join(root, file);
  const s = readFileSync(p, "utf8");
  if (!s.includes(from)) throw new Error(`needle not found in ${file}: ${from.slice(0, 60)}`);
  writeFileSync(p, s.replace(from, to));
}

function runGuard() {
  const r = spawnSync(process.execPath, [path.join(root, "scripts", "verify-design-tokens.mjs")], { encoding: "utf8" });
  return { code: r.status, out: (r.stdout || "") + (r.stderr || "") };
}

const cases = [
  ["删掉间距令牌 --sp-7", () => mutate("src/styles/tokens.css", "  --sp-7: 20px;\n", ""), /缺少令牌定义/],
  ["删掉 --focus-ring 定义", () => mutate("src/styles/tokens.css", "  --focus-ring: 0 0 0 2px", "  --zz-focus-ring: 0 0 0 2px"), /控件几何/],
  ["行高写成 px（缩放错位）", () => mutate("src/styles/tokens.css", "--leading-13: 1.6;", "--leading-13: 24px;"), /行高令牌必须无单位/],
  ["D-2 回归：删掉 --font-sans 别名", () => mutate("src/styles/tokens.css", "  --font-sans: var(--font-ui);\n", ""), /D-2 回归/],
  ["别名指向错乱的权威令牌", () => mutate("src/styles/tokens.css", "--font-serif: var(--font-content);", "--font-serif: var(--font-data);"), /D-2 回归/],
  ["Tailwind 恢复硬写 Inter", () => mutate("tailwind.config.ts", "sans: [\"var(--font-ui)\"]", "sans: [\"Inter\", \"system-ui\"]"), /Inter|fontFamily/],
  ["D-1 回归：主按钮加回铜色阴影", () => mutate("src/components/ui.tsx", "primary: \"bg-copper", "primary: \"shadow-[0_6px_16px_rgba(184,64,26,0.22)] bg-copper"), /184,64,26|阴影/],
  ["按钮加回 hover 位移", () => mutate("src/components/ui.tsx", "primary: \"bg-copper", "primary: \"hover:-translate-y-0.5 bg-copper"), /几何位移/],
  ["按钮加回 active 缩放", () => mutate("src/components/ui.tsx", "outline: \"border border-paper-line", "outline: \"active:scale-[0.98] border border-paper-line"), /几何位移/],
  ["焦点环退回会被 Tailwind 吃掉的写法", () => mutate("src/components/ui.tsx", "focus-visible:[box-shadow:var(--focus-ring)]", "shadow-[var(--focus-ring)]"), /Tailwind|焦点环/],
  ["对 var() 色值套 alpha（整条会被丢弃）", () => mutate("src/components/ui.tsx", "border-[color:color-mix(in_srgb,var(--proof-mark)_45%,transparent)]", "border-[color:var(--proof-mark)]/45"), /alpha/],
  ["少一个 variant（删 tonal）", () => mutate("src/components/ui.tsx", "    tonal:\n", "    // tonal 已删除\n    goneTonal:\n"), /缺少 variant|tonal/],
  ["少一档尺寸（删 lg）", () => mutate("src/components/ui.tsx", "    lg: \"h-10 px-4\"", "    // lg 已删除"), /缺少尺寸/],
  ["去掉 loading 的 aria-busy", () => mutate("src/components/ui.tsx", "aria-busy={loading || undefined}", "data-busy={loading || undefined}"), /loading 态/],
  ["disabled 透明度改成 0.45", () => mutate("src/components/ui.tsx", "disabled:opacity-55", "disabled:opacity-45"), /disabled 透明度/],
  ["danger 改用主操作色", () => mutate("src/components/ui.tsx", "bg-[color:var(--proof-mark)] text-[color:var(--fg-on-solid)]", "bg-copper text-[color:var(--fg-on-solid)]"), /proof-mark/],
  ["data-variant 输出归一后的值（CSS 排除会失效）", () => mutate("src/components/ui.tsx", "data-variant={variant}", "data-variant={resolved}"), /data-variant/],
  ["CSS 双排除退回单排除（ghost 漏进主色）", () => mutate("src/styles/editorial-studio.css", ':not([data-variant="quiet"]):not([data-variant="ghost"]):hover', ':not([data-variant="quiet"]):hover'), /双排除/],
  ["tonal 底色退回 copper-soft 别名（实为校样红）", () => mutate("src/components/ui.tsx", "bg-[color:var(--action-tint)] text-copper", "bg-copper-soft text-copper"), /copper-soft|action-tint/],
  ["tonal 底色换成校样红", () => mutate("src/components/ui.tsx", "bg-[color:var(--action-tint)] text-copper", "bg-[color:var(--proof-tint)] text-copper"), /action-tint|校样红|proof-tint/],
  ["danger-filled 基础填充改用主操作色（hover 仍带 proof-mark，考验逐 variant 切片）", () => mutate("src/components/ui.tsx", '"danger-filled": "bg-[color:var(--proof-mark)]', '"danger-filled": "bg-copper'), /proof-mark|主操作色/],
  ["把带引号的 danger-outline 整条删除", () => mutate("src/components/ui.tsx", '"danger-outline":\n', '"gone-outline":\n'), /danger-outline|variant/],
  ["把已整族下线的 .history-item-actions 后代规则加回来（未排除必须变红：容器里现在全是迁移后的 <Button>）", () => mutate("src/features/creation/history/history-local.css", ".history-item-actions {\n  display: flex;", ".history-item-actions button {\n  border-radius: 999px;\n}\n\n.history-item-actions {\n  display: flex;"), /后代按钮规则未排除|history-local\.css/],
  ["删掉全局分组规则里 cards-toolbar 成员的排除（迁移过的工具栏按钮会被圆角压掉）", () => mutate("src/styles/editorial-studio.css", ".cards-toolbar button:not([data-variant]),", ".cards-toolbar button,"), /后代按钮规则未排除|editorial-studio\.css/],
  ["新增一条清单外的后代按钮规则（棘轮必须变红，遗留冻结不能放过新违规）", () => mutate("src/styles/editorial-studio.css", "/* Buttons are physical: a lit crown, a pressed state. */", ".history-item-actions button {\n  border-radius: 999px;\n}\n\n/* Buttons are physical: a lit crown, a pressed state. */"), /后代按钮规则未排除/],
  ["把违规规则藏到后代层级（父级类名在选择器中段，考验 TAIL 锚点）", () => mutate("src/styles/editorial-studio.css", "/* Buttons are physical: a lit crown, a pressed state. */", ".desktop-page-stack .history-item-actions button {\n  border-radius: 999px;\n}\n\n/* Buttons are physical: a lit crown, a pressed state. */"), /后代按钮规则未排除/],
  ["把已收口的 history-page 排除改回去（棘轮必须继续盯住它）", () => mutate("src/styles/editorial-studio.css", ".history-page button:not([data-variant]) {", ".history-page button {"), /后代按钮规则未排除/],
  ["跨目录借用样式：在 scene-radar.css 里加一条未排除的后代按钮规则（守卫原本按「同目录」判归属会漏检，desk 页面复用了它）", () => mutate("src/features/creation/editor/scene-radar.css", ".scene-radar-ai-buttons {\n  display: flex;", ".scene-radar-ai-buttons button {\n  border-radius: 999px;\n}\n\n.scene-radar-ai-buttons {\n  display: flex;"), /后代按钮规则未排除|scene-radar\.css/],
  ["把 desktop-page-actions 分组规则的 :not 排除退回单排除（ProjectHomePage 首钮是默认 primary，会被淡底样式压回 (0,1,1)）", () => mutate("src/styles/editorial-studio.css", '.desktop-page-actions button:first-child:not([data-variant="quiet"]):not([data-variant="ghost"]),\n.project-home-create-first {\n  border-color: color-mix(in srgb, var(--accent-spine) 92%', '.desktop-page-actions button:first-child:not([data-variant="ghost"]),\n.project-home-create-first {\n  border-color: color-mix(in srgb, var(--accent-spine) 92%'), /后代按钮规则未排除|editorial-studio\.css/],
  ["base 加回 px-3（会被 Tailwind 发射顺序压掉尺寸层与 icon 的 p-0）", () => mutate("src/components/ui.tsx", 'justify-center gap-2 rounded-lg text-sm', 'justify-center gap-2 rounded-lg px-3 text-sm'), /base 不得包含内边距/],
  ["sm 档丢掉自带内边距（base 已不再兜底，会渲染成 0 内边距）", () => mutate("src/components/ui.tsx", 'sm: "h-7 px-2.5 text-xs"', 'sm: "h-7 text-xs"'), /尺寸 sm 必须自己声明/],
  ["夜校前景改回白字（对比度 2.53）", () => mutate("src/styles/tokens.css", "  --fg-on-solid: #15181c;", "  --fg-on-solid: #ffffff;"), /夜校 primary 对比度/],
  ["晨校主色调浅到不合格", () => mutate("src/styles/tokens.css", "  --action-primary: #315f9b;", "  --action-primary: #a8c4e4;"), /晨校 primary 对比度/],
  ["正文色调到不可读", () => mutate("src/styles/tokens.css", "  --text-primary: #20242a;", "  --text-primary: #9aa2ac;"), /正文\/画布 对比度/],
  ["盲区二：把已整族下线的 .inbox-detail div.mt-3.grid button 原样加回来（父级类名挂在类型选择器上，「必须以 . 开头」的窄锚点会整条漏掉，放宽后必须变红）", () => mutate("src/styles/editorial-studio.css", ".inbox-detail-head {\n  display: flex;", ".inbox-detail div.mt-3.grid button {\n  border-radius: 999px;\n}\n\n.inbox-detail-head {\n  display: flex;"), /后代按钮规则未排除|editorial-studio\.css/],
  ["alpha 孪生失同步：改了 --text-secondary 却忘了 --rgb-muted（Tailwind 的 text-paper-muted/* 走的是后者，144 处用例会悄悄跟着旧色）", () => mutate("src/styles/tokens.css", "  --rgb-muted: 102 109 119;", "  --rgb-muted: 104 112 122;"), /alpha 孪生|不一致/],
  ["第 7 步回归：把硬编码校样红 #c0392b 写回样式表（计数必须仍为 0）", () => mutate("src/styles.css", ".migration-error-inline {\n  color: var(--proof-mark);", ".migration-error-inline {\n  color: #c0392b;"), /第 7 步未收口/],
  ["第 7 步回归：换个同族旧红来抄（#b42318），证明只封 #c0392b 挡不住", () => mutate("src/features/creation/replace/replace.css", ".replace-hit-before {\n  text-decoration: line-through;", ".replace-hit-before {\n  color: #b42318;\n  text-decoration: line-through;"), /第 7 步未收口/],
  ["第 7 步回归：用 var(--danger, 字面量) 冒充走令牌（--danger 从未定义，实际渲染的是字面量）", () => mutate("src/features/search/search.css", ".uni-search-error {", ".search-error-ghost {\n  color: var(--danger, #b42318);\n}\n\n.uni-search-error {"), /第 7 步未收口/],
  ["新颜色令牌只写晨校值（夜校会拿浅色画到深色底上，实测 --success 因此掉到 3.52:1）", () => mutate("src/styles/tokens.css", "  --warning: #8a5a16;", "  --warning: #8a5a16;\n  --zz-new-color: #123456;"), /颜色令牌必须在夜校/],
  ["第 7 步回归：警示琥珀写回规则里（#8a5a16 手抄 5 处正是「令牌只写晨校」长期没人发现的根因）", () => mutate("src/features/creation/history/history-local.css", ".history-field-error {\n  font-size: 12px;", ".history-field-error {\n  color: #8a5a16;\n  font-size: 12px;"), /第 7 步未收口/],
  ["第 7 步回归：只改语义绿忘了 alpha 孪生 --rgb-moss（Tailwind 的 text-moss/bg-moss 走的是后者）", () => mutate("src/styles/tokens.css", "  --rgb-moss: 53 111 83;", "  --rgb-moss: 62 122 94;"), /alpha 孪生|不一致/],
  ["第 7 步回归：琥珀只补了晨校、夜校留空（同一条成对性不变量，防止 --success 的剧本重演）", () => mutate("src/styles/tokens.css", "  --warning: #bd8637;\n", ""), /颜色令牌必须在夜校/],
  ["第 8 步回归：主题色实底上写死 #fff（走的是 --copper 别名，必须沿别名链查到它两主题取值不同）", () => mutate("src/styles.css", "  background: var(--copper);\n  /* --copper = --action-primary，夜校是浅蓝 #7fa5d9，写死 #fff 只有 2.53:1 */\n  color: var(--fg-on-solid);", "  background: var(--copper);\n  color: #fff;"), /写死白墨/],
  ["第 8 步回归：TSX 分支——bg-copper 选中态配回写死的 text-white（类名串在引号里，子串正则查不出，必须按工具类切分）", () => mutate("src/features/library/LibraryPage.tsx", `"bg-copper text-[color:var(--fg-on-solid)]"`, `"bg-copper text-white"`), /TSX 里 bg-copper/],
  ["第 8 步回归：白墨与翻转底色拆成同选择器的两条规则（逐规则配对看不见对方，必须按选择器取每个属性的胜者）", () => mutate("src/styles.css", ".desktop-brand strong,", ".zz-split-cascade {\n  color: #fff;\n}\n\n.zz-split-cascade {\n  background: var(--copper);\n}\n\n.desktop-brand strong,"), /写死白墨/],
  // 下面两条锁住批次 B 修掉的两个守卫盲区。它们不是「新增检查」，而是
  // 「原本在查、但因为判据写错而看不见」——这类缺陷最容易在下次改动时被顺手改回去。
  ["盲区三：把已删除的 `.migration-banner Button` 原样加回来（PascalCase 选择器命中真实 <button>，BUTTON_PARENT 少了 i 标志就整条漏检）", () => mutate("src/styles/editorial-studio.css", ".desktop-sidebar::before {\n  display: none;", ".migration-banner Button {\n  background: var(--action-primary) !important;\n}\n\n.desktop-sidebar::before {\n  display: none;"), /后代按钮规则未排除/],
  ["盲区四：把裸排除改成带值排除（:not([data-variant]) → :not([data-variant=\"quiet\"])）。带值排除仍然命中其它 variant 的 <Button>，不能算已排除", () => mutate("src/styles/editorial-studio.css", ".cards-toolbar button:not([data-variant]),", '.cards-toolbar button:not([data-variant="quiet"]),'), /后代按钮规则未排除/],
  // 批次 C 新增的四条。前两条测「新写的代码」，第三条测的是「老代码能不能悄悄
  // 吃掉一整族语义色」——那种缺陷 vitest 全绿、界面却坏掉，最贵。
  ["批次 C 回归：TSX 里把危险底色写回 Tailwind 调色板（bg-red-500 不随主题翻转，第 7 步的 CSS hex 计数看不见类名）", () => mutate("src/features/library/LibraryPage.tsx", 'transition hover:bg-[color:var(--proof-tint)] hover:text-[color:var(--proof-mark)]"', 'transition hover:bg-red-500 hover:text-red-700"'), /状态色类名/],
  ["批次 C 回归：CSS 的 @apply 里藏 Tailwind 状态色类名，证明这条扫描不止管 TSX", () => mutate("src/styles.css", "border: 1px solid rgb(253 230 138 / 0.6);\n    @apply shadow-paper;", "border: 1px solid rgb(253 230 138 / 0.6);\n    @apply border-amber-200/60 shadow-paper;"), /状态色类名/],
  ["批次 C 回归：把 .motion-toast 的 background 加回来——它与 tone 工具类同特异度而加载更晚，会整族遮蔽四种提示的颜色（批次 C 用 jsdom 实测过的真实缺陷）", () => mutate("src/styles/editorial-studio.css", ".motion-toast {\n  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.3) !important;", ".motion-toast {\n  background: var(--studio-cloth);\n  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.3) !important;"), /motion-toast|遮蔽/],
  ["批次 C 回归：--warning-tint 只写晨校值（成对性不变量必须管到新令牌，否则夜校把浅琥珀岛原样画在深色底上）", () => mutate("src/styles/tokens.css", "  --warning-tint: #2a2118;\n", ""), /颜色令牌必须在夜校/],
  // 第 8 步圆角棘轮：这五条测的是「棘轮能不能两头咬人」，以及「会不会咬到自己」。
  // 最后两条 expect 为 null，是「必须保持绿」的反向用例——判据过严同样是缺陷。
  ["圆角棘轮①：在已有预算的文件里多加一处离刻度（同串里再加 rounded-xl，计数 1→2 必须红）", () => mutate("src/features/library/LibraryPage.tsx", 'min-w-[160px] rounded-lg border', 'min-w-[160px] rounded-lg rounded-xl border'), /圆角刻度/],
  ["圆角棘轮②：把离刻度值写进预算表里根本没有的文件（settings-controls.css 预算视同 0，4px→7px 必须红）", () => mutate("src/features/settings/settings-controls.css", "  border-radius: 4px;", "  border-radius: 7px;"), /圆角刻度/],
  ["圆角棘轮③：还了债却不降预算（LibraryPage 的 rounded-lg→rounded 是合法收敛，但预算仍是 1，必须红并指名该文件）", () => mutate("src/features/library/LibraryPage.tsx", 'min-w-[160px] rounded-lg border', 'min-w-[160px] rounded border'), /预算没跟着降/],
  ["圆角棘轮·反向①：说明注释里提到旧类名 rounded-xl 不算违规（不剥注释的话守卫会自己咬自己）", () => mutate("src/features/library/LibraryPage.tsx", "export function LibraryPage() {", "export function LibraryPage() {\n  {/* 这里原来是 rounded-xl，批次 D 收到 rounded-lg */}"), null],
  ["圆角棘轮·反向②：50% 是形状决定不是圆角档位，写进规则里不该变红", () => mutate("src/features/settings/settings-controls.css", "  border-radius: 4px;", "  border-radius: 50%;"), null],
  // 第 8 步批次 F：那条把浮层圆角整体吃掉的 12px !important 已删，判据钉住它的「形状」。
  // ①故意用刻度上的 10px，让棘轮咬不到、只有浮层不变量能报警——否则这条用例测的是棘轮。
  ["批次 F 回归：把浮层圆角的 !important 全局接管加回来（裸类名 + !important，值 10px 在刻度上、棘轮咬不到，必须靠浮层不变量拦住）", () => mutate("src/styles/editorial-studio.css", ".motion-toast {\n  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.3) !important;", ".motion-toast {\n  border-radius: 10px !important;\n  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.3) !important;"), /浮层容器/],
  ["批次 F 回归·反向①：带模式前缀的 !important 压平是显式决定（专注模式把横幅压成方角），不是全局接管，不该变红", () => mutate("src/styles/editorial-studio.css", ".motion-toast {\n  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.3) !important;", ".desktop-root--focus .motion-toast {\n  border-radius: 0 !important;\n}\n\n.motion-toast {\n  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.3) !important;"), null],
  ["批次 F 回归·反向②：裸类名 + 非 !important 的自持圆角（.migration-banner 在 TSX 里不带 rounded 工具类，这条 CSS 是它圆角的唯一来源）不该变红", () => mutate("src/styles.css", ".migration-banner {\n  display: flex;", ".migration-banner {\n  border-radius: 10px;\n  display: flex;"), null]
];

let bad = 0;
let mustGreen = 0;
for (const [label, apply, expect] of cases) {
  restore();
  let result;
  try {
    apply();
    result = runGuard();
  } catch (err) {
    console.log(`SKIP  ${label}\n      ${err.message}`);
    bad += 1;
    continue;
  }
  // expect === null：这条测的是「不该变红」——防的是守卫过度灵敏把好写法判成违规。
  // 误报的危害不比漏检小：警告一旦可以被正当写法触发，人就会开始绕过它。
  if (expect === null) {
    mustGreen += 1;
    const green = result.code === 0;
    console.log(`${green ? "OK   " : "FAIL "} ${label}`);
    if (!green) {
      console.log("       期望保持绿，实际红了：");
      console.log(result.out.split("\n").filter((l) => l.includes("verify-design-tokens")).slice(0, 3).map((l) => "         " + l).join("\n"));
      bad += 1;
    }
    continue;
  }
  const red = result.code !== 0 && expect.test(result.out);
  console.log(`${red ? "OK   " : "FAIL "} ${label}`);
  if (!red) {
    console.log(`       exit=${result.code} 期望命中 ${expect} 实际输出：`);
    console.log(result.out.split("\n").filter((l) => l.includes("verify-design-tokens")).slice(0, 4).map((l) => "         " + l).join("\n"));
    bad += 1;
  }
}

restore();
const green = runGuard();
console.log(`\n还原后守卫: exit=${green.code} ${green.code === 0 ? "（绿，符合预期）" : "（仍红，异常）"}`);
if (green.code !== 0) bad += 1;

cleanup();
console.log(`\n注入测试：${cases.length} 个用例（其中 ${mustGreen} 条要求守卫保持绿），${bad === 0 ? "全部符合预期" : bad + " 个无效"}`);
process.exit(bad === 0 ? 0 : 1);
