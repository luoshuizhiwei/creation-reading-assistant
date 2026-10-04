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
  // 阴影棘轮的 CSS 侧用例改这份：它不在 SHADOW_BUDGET 里（预算视同 0），
  // 测的是「新债落进零预算文件」。
  "src/features/creation/inbox/inbox-local.css",
  // 第 8 步的 TSX 分支用例改这份：它是 4 处 bg-copper + text-[color:var(--fg-on-solid)] 之一。
  "src/features/library/LibraryPage.tsx",
  // 圆角棘轮的「新债 / 还债不降预算」两条用例原先钉在 LibraryPage 的
  // `min-w-[160px] rounded-lg border` 上。批次 M 把阅读器族整片圆角收上刻度，
  // 那个锚点会被合法迁移掉——用例随即 SKIP、harness 计为无效（自愈设计，不静默失效）。
  // 之后改钉 InboxItemDetail；批次 Q 把这最后一页的离刻度圆角也清空了，于是再改钉
  // ui.tsx 的 Button base——它现在是圆角棘轮唯一还挂着活债的 TSX 宿主。
  // 圆角/阴影棘轮用例改这几份：「已有预算的文件」（LibraryPage：圆角 1 / 阴影 1）、
  // 「预算表里根本没有的文件」（settings-controls.css 圆角视同 0、inbox-local.css 阴影视同 0）
  // ——后者测的是新增债务落进零预算文件时会不会漏判。
  "src/features/settings/settings-controls.css",
  // 批次 O 的用例改这几份：encryption.css 的 .pe-modal 在「查无宿主」清单上，用它测
  // 「删掉幻影债务却忘了降第二本账」；WritingQuickReferencePanel 是唯一靠 BEM 动态前缀
  // 才活着的宿主，用它测「宿主判据退化」。styles.css 早已在清单里，不重复登记。
  "src/features/settings/encryption/encryption.css",
  "src/features/creation/editor/WritingQuickReferencePanel.tsx",
  // 批次 R 的用例改这份：它刚被整族收口（三本账同时降），用它证明幻影判据不只盯全局样式表，
  // 功能局部 CSS 里给画不出来的元素配新档位一样要红。
  "src/features/creation/cards/cards-local.css",
  // 批次 S 的用例改这几份：history-local.css 刚退役那条 .history-modal 裸类名接管，
  // 用它测「接管加回来必须红 / 带模式前缀与非几何属性合法」；WritingDesk 是把
  // .writing-recovery-notice 当面板钩子传给 <InlineNotice> 的唯一宿主，用它测「钩子扫描退化」。
  "src/features/creation/editor/WritingDesk.tsx"
  // 阴影棘轮的「还了债不降预算」用例原先钉在 EpubPageTurnButtons 的 shadow-lift 上，
  // 批次 K 把那份债迁走就把锚点拆了——用例随即 SKIP、harness 计为无效，不会静默失效。
  // K 之后还债用例统一改钉 LibraryPage（它同时是圆角/状态色/棘轮多条用例的宿主，
  // 已在清单里），不再为单个功能文件保留备份项。
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
  ["圆角棘轮①：在已有预算的文件里多加一处离刻度（Button base 的 rounded-lg 旁再挂 rounded-2xl，计数必须超预算）", () => mutate("src/components/ui.tsx", 'justify-center gap-2 rounded-lg text-sm', 'justify-center gap-2 rounded-lg rounded-2xl text-sm'), /圆角刻度/],
  ["圆角棘轮②：把离刻度值写进预算表里根本没有的文件（settings-controls.css 预算视同 0，4px→7px 必须红）", () => mutate("src/features/settings/settings-controls.css", "  border-radius: 4px;", "  border-radius: 7px;"), /圆角刻度/],
  ["圆角棘轮③：还了债却不降预算（Button base 的 rounded-lg→rounded-md 是合法收敛，但预算没跟着降，必须红并指名该文件）", () => mutate("src/components/ui.tsx", 'justify-center gap-2 rounded-lg text-sm', 'justify-center gap-2 rounded-md text-sm'), /预算没跟着降/],
  ["圆角棘轮·反向①：说明注释里提到旧类名 rounded-xl 不算违规（不剥注释的话守卫会自己咬自己）", () => mutate("src/features/library/LibraryPage.tsx", "export function LibraryPage() {", "export function LibraryPage() {\n  {/* 这里原来是 rounded-xl，批次 M 收到 rounded-md */}"), null],
  ["圆角棘轮·反向②：50% 是形状决定不是圆角档位，写进规则里不该变红", () => mutate("src/features/settings/settings-controls.css", "  border-radius: 4px;", "  border-radius: 50%;"), null],
  // 批次 M 把阅读器族整片收到刻度上，用的正是下面这三种写法。这条反向用例钉的是
  // 「本批的迁移目标写法确实合法」——判据若把它们误伤，下一批就会退回去写 rounded-lg。
  ["圆角棘轮·反向③：批次 M 的三种目标写法 rounded-md(6px) / rounded-[var(--radius-3)] / rounded-full 都在刻度上，不该变红", () => mutate("src/features/library/LibraryPage.tsx", "export function LibraryPage() {", "export function LibraryPage() {\n  // zz: rounded-md rounded-[var(--radius-3)] rounded-full"), null],
  // 第 8 步批次 F：那条把浮层圆角整体吃掉的 12px !important 已删，判据钉住它的「形状」。
  // ①故意用刻度上的 10px，让棘轮咬不到、只有浮层不变量能报警——否则这条用例测的是棘轮。
  ["批次 F 回归：把浮层圆角的 !important 全局接管加回来（裸类名 + !important，值 10px 在刻度上、棘轮咬不到，必须靠浮层不变量拦住）", () => mutate("src/styles/editorial-studio.css", ".motion-toast {\n  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.3) !important;", ".motion-toast {\n  border-radius: 10px !important;\n  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.3) !important;"), /浮层容器/],
  ["批次 F 回归·反向①：带模式前缀的 !important 压平是显式决定（专注模式把横幅压成方角），不是全局接管，不该变红", () => mutate("src/styles/editorial-studio.css", ".motion-toast {\n  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.3) !important;", ".desktop-root--focus .motion-toast {\n  border-radius: 0 !important;\n}\n\n.motion-toast {\n  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.3) !important;"), null],
  ["批次 F 回归·反向②：裸类名 + 非 !important 的自持圆角（.migration-banner 在 TSX 里不带 rounded 工具类，这条 CSS 是它圆角的唯一来源）不该变红", () => mutate("src/styles.css", ".migration-banner {\n  display: flex;", ".migration-banner {\n  border-radius: 10px;\n  display: flex;"), null],
  // 第 8 步批次 G：面板家族曾有两条都带 !important 的规则互相压制（同特异度靠加载顺序赢）。
  // 判据管的是「同一面板类名的 !important 圆角来源至多一条」，所以正向用例必须造出第二条。
  ["批次 G 回归：把面板 blanket 的 !important 圆角加回来（值用刻度上的 10px，棘轮咬不到；此时同一面板类名出现第二条 !important 来源，必须靠不变量拦住）", () => mutate("src/styles/editorial-studio.css", ".desktop-ai-card {\n  border-color: var(--border-subtle) !important;", ".desktop-ai-card {\n  border-color: var(--border-subtle) !important;\n  border-radius: 10px !important;"), /面板类名的圆角又出现多条/],
  ["批次 G 回归·反向①：同一条规则里 shorthand + 单角都带 !important 是同一来源，不该算两条", () => mutate("src/styles/editorial-studio.css", "  border-radius: var(--radius-panel) !important;\n  box-shadow: 0 1px 2px rgba(15, 20, 28, 0.04) !important;", "  border-radius: var(--radius-panel) !important;\n  border-top-left-radius: 10px !important;\n  box-shadow: 0 1px 2px rgba(15, 20, 28, 0.04) !important;"), null],
  ["批次 G 回归·反向②：面板另有一条非 !important 的自持圆角（各页面自己的面板规则，与家族 !important 共存时胜者明确）不该变红", () => mutate("src/styles.css", ".stats-card {\n  border: 1px solid var(--paper-line);", ".stats-card {\n  border: 1px solid var(--paper-line);\n  border-radius: 10px;"), null],
  // 第 8 步批次 H：`X > *` 的 !important 非零圆角 blanket（往透明包装器刷弧度）。
  // 正向值用刻度上的 10px，让棘轮咬不到、只有网格不变量拦得住；反向证明 0 压平合法。
  ["批次 H 回归：加一条 `X > * { border-radius: 10px !important }` 网格 blanket（10px 在刻度上、棘轮咬不到，必须靠网格不变量拦住）", () => mutate("src/styles/editorial-studio.css", "/* Buttons are physical: a lit crown, a pressed state. */", ".zz-grid-blanket > * {\n  border-radius: 10px !important;\n}\n\n/* Buttons are physical: a lit crown, a pressed state. */"), /网格子元素刷非零圆角/],
  ["批次 H 回归·反向：`X > * { border-radius: 0 !important }` 是压平（.stats-page .stats-grid > * 就这么用），不该变红", () => mutate("src/styles/editorial-studio.css", "/* Buttons are physical: a lit crown, a pressed state. */", ".zz-grid-flatten > * {\n  border-radius: 0 !important;\n}\n\n/* Buttons are physical: a lit crown, a pressed state. */"), null],
  // 第 8 步批次 I：阴影棘轮。这组测三件事——能不能咬新债（TSX 工具类 / CSS 声明 / @apply
  // 三条入口各一条）、还了债不降预算会不会红、以及四种豁免会不会误伤。
  ["阴影棘轮①：TSX 工具类新债——已有预算文件里同串再加一个 shadow-lift（1→2 必须红）", () => mutate("src/features/library/LibraryPage.tsx", 'bg-paper-panel shadow-paper p-1', 'bg-paper-panel shadow-paper shadow-lift p-1'), /阴影刻度/],
  ["阴影棘轮②：CSS 声明新债——把字面投影写进阴影预算表里根本没有的文件（inbox-local.css 视同 0，必须红）", () => mutate("src/features/creation/inbox/inbox-local.css", ".inbox-page {\n  display: flex;", ".inbox-page {\n  box-shadow: 0 10px 30px rgba(34, 38, 48, 0.05);\n  display: flex;"), /阴影刻度/],
  ["阴影棘轮③：@apply 分支——@apply 里的 shadow-lift 编译后就是一条 box-shadow 声明，和 TSX 挂工具类等价，必须同判", () => mutate("src/styles.css", "@apply border border-paper-line bg-paper-panel shadow-lift;", "@apply border border-paper-line bg-paper-panel shadow-lift shadow-xl;"), /阴影刻度/],
  ["阴影棘轮·反向①：伏笔是本产品的业务词（foreshadow），text-shadow 是属性名——都在代码里而非注释里，前后断言必须挡住，不该变红", () => mutate("src/features/library/LibraryPage.tsx", "export function LibraryPage() {", "export function LibraryPage() {\n  const zzProbe = \"foreshadow foreshadowResolved foreshadows text-shadow\";\n  void zzProbe;"), null],
  ["阴影棘轮·反向②：四种合法豁免——shadow-none / shadow-inner / 颜色档 shadow-white\\/20（只产 --tw-shadow-color）/ [box-shadow:var(--focus-ring)] 焦点环，都不该变红", () => mutate("src/features/library/LibraryPage.tsx", "export function LibraryPage() {", "export function LibraryPage() {\n  const zzExempt = \"shadow-none shadow-inner shadow-white/20 focus-visible:[box-shadow:var(--focus-ring)]\";\n  void zzExempt;"), null],
  ["阴影棘轮·还债不降预算：LibraryPage 把 shadow-paper 换成真正发射的 [box-shadow:var(--shadow-1)] 是合法收敛（预算 1→0），但预算仍是 1，必须红并指名该文件", () => mutate("src/features/library/LibraryPage.tsx", 'bg-paper-panel shadow-paper p-1', 'bg-paper-panel [box-shadow:var(--shadow-1)] p-1'), /预算没跟着降/],
  // 第 8 步批次 K：幻影写法硬红。shadow-[var(--shadow-N)] 在产物里只产 --tw-shadow-color、
  // 没有 box-shadow 声明——「改了画不出来」，且旧判据还给它记成功还债，所以单独 fail()。
  ["批次 K 回归：幻影写法 shadow-[var(--shadow-2)] 必须硬红（不进计数，因为它画的根本不是阴影）", () => mutate("src/features/library/LibraryPage.tsx", 'bg-paper-panel shadow-paper p-1', 'bg-paper-panel shadow-paper shadow-[var(--shadow-2)] p-1'), /幻影/],
  ["批次 K 回归·反向：属性形式 [box-shadow:var(--shadow-2)] 与其 hover: 变体是产物实测唯一能发射的任意值写法，既不被幻影判据咬、又走 var(--shadow) 豁免不计数，不该变红", () => mutate("src/features/library/LibraryPage.tsx", "export function LibraryPage() {", "export function LibraryPage() {\n  const zzReal = \"[box-shadow:var(--shadow-2)] hover:[box-shadow:var(--shadow-2)]\";\n  void zzReal;"), null],
  ["批次 K 回归：字面量任意值 shadow-[0_1px_0_rgba(...)] 确实发射投影（.paper-topbar 实测），所以不归幻影硬红管——但它是刻度外的手抄值，由棘轮计债（LibraryPage 预算 1，再加一处到 2 必须红，且红的是阴影刻度而非幻影）", () => mutate("src/features/library/LibraryPage.tsx", 'bg-paper-panel shadow-paper p-1', 'bg-paper-panel shadow-paper shadow-[0_1px_0_rgba(255,255,255,0.45)] p-1'), /阴影刻度/],
  // 第 8 步批次 L：别名归位。圆角棘轮豁免 var(--radius-*)，债从 CSS 声明搬进令牌定义后
  // 判据就瞎了——把 --radius-panel 改回 8px，面板家族整体无声改版、棘轮一个不响。
  // 这两条测的是「映射钉得够不够死」：字面量要红，指错档也要红。
  ["批次 L 回归：把 --radius-panel 改回字面量 8px（面板家族 9 个成员 + 预览区会整片变圆，而圆角棘轮看不见令牌里的值，必须靠别名映射拦住）", () => mutate("src/styles/tokens.css", "  --radius-panel: var(--radius-2);", "  --radius-panel: 8px;"), /别名归位|圆角刻度/],
  ["批次 L 回归：别名指向错档（--radius-control → var(--radius-3)，控件从 4px 变 10px），证明钉的是精确映射而不是「只要不是字面量就行」", () => mutate("src/styles/tokens.css", "  --radius-control: var(--radius-1);", "  --radius-control: var(--radius-3);"), /别名归位|圆角刻度/],
  ["批次 L 回归·反向：别名之间隔一个空格/换行仍是合法映射（棘轮与别名判据都不该因空白而红）", () => mutate("src/styles/tokens.css", "  --radius-panel: var(--radius-2);", "  --radius-panel:   var( --radius-2 );"), null],
  // 第 8 步批次 N：styles.css 退役了一批从不渲染的死圆角，预算随之降了一截。
  // 这两条测的是这次同步有没有把 styles.css 的咬合力一起删掉：
  // 拿批次 N 删过的同一个宿主、同一属性，离刻度值必须红、刻度值必须绿。
  ["第 8 步批次 N 回归：把退役掉的死圆角以离刻度值写回 .creation-wizard（这一页的预算已经降过一档，多一处必须红——否则这次退役等于给 styles.css 松了绑）", () => mutate("src/styles.css", ".creation-wizard {", ".creation-wizard {\n  border-radius: 22px;"), /圆角刻度/],
  ["第 8 步批次 N 回归·反向：同一个宿主写刻度上的 6px 不该红（styles.css 还剩一批活债，棘轮数的是离刻度，不是禁止字面量）", () => mutate("src/styles.css", ".creation-wizard {\n", ".creation-wizard {\n  border-radius: 6px;\n"), null],
  // 第 8 步批次 O：幻影选择器判据。它守的是「宿主不存在」这一类死法，方向性和前几条相反——
  // 判据唯一致命的错法是「把活 CSS 判成幻影」（那样删除就是无声改版），
  // 所以除了咬新债/咬不降预算，还要咬「判据自己退化」和证明「宿主靠动态拼接的活类不被误伤」。
  ["批次 O 回归①：给查无宿主的 .desktop-module-card 新增一处离刻度圆角（界面画不出来，令牌债却是真的，必须被幻影判据咬住）", () => mutate("src/styles.css", ".desktop-module-card {\n  position: relative;", ".desktop-module-card {\n  border-radius: 14px;\n  position: relative;"), /新增了令牌债/],
  ["批次 O 回归②：删掉幻影规则里的离刻度圆角却不降幻影预算（.pe-modal 的 14px 删掉，HOSTLESS_BUDGET 仍是 2，必须红）", () => mutate("src/features/settings/encryption/encryption.css", "  border-radius: 14px;\n", ""), /幻影选择器[\s\S]*预算没跟着降/],
  ["批次 O 回归③：把幻影类名接上宿主却不降预算（桌面工作台真接回 .desktop-module-card 时，这笔债从幻影表移到活债表，两本账都要改）", () => mutate("src/features/library/LibraryPage.tsx", "export function LibraryPage() {", "export function LibraryPage() {\n  const zzHost = \"desktop-module-card\";\n  void zzHost;"), /幻影选择器[\s\S]*预算没跟着降/],
  ["批次 O 回归④：宿主判据退化——把 BEM 修饰类的动态前缀写法拆掉（`writing-quick-kind--${card.kind}` 退回裸类名），自检必须红，否则活 CSS 会被安静判死", () => mutate("src/features/creation/editor/WritingQuickReferencePanel.tsx", "writing-quick-kind writing-quick-kind--${card.kind}", "writing-quick-kind"), /宿主判据把/],
  ["批次 O 回归·反向：幻影规则里写刻度上的 6px 不该红（判据咬的是令牌债，不是「这条规则没宿主」这件事本身——整族孤儿 CSS 的清理是另一笔账）", () => mutate("src/styles.css", ".desktop-module-card {\n  position: relative;", ".desktop-module-card {\n  border-radius: 6px;\n  position: relative;"), null],
  // 第 8 步批次 Q：删掉了 editorial-studio.css 里那条把 .inbox-detail 内四个元素整体
  // 接管的毯子（`.inbox-detail > .rounded-xl, … blockquote, … article
  // { border-radius: 10px !important; box-shadow: none !important }`）。它的形状和浮层/
  // 面板/网格三条不变量都不同：选择器里含 `.rounded-*` 工具类锚点，也就是 CSS 选中组件
  // 自己在 className 里挂的圆角类再压掉——圆角棘轮对这种锚点做了排除，所以 10px 在刻度上、
  // 棘轮咬不到，只有新加的「工具类锚点不变量」拦得住。反向两条证明合法写法不被误伤。
  ["批次 Q 回归：把删掉的 .inbox-detail 毯子原样加回来（含 .rounded-xl 锚点 + !important 10px，棘轮看不见，必须靠工具类锚点不变量拦住）", () => mutate("src/styles/editorial-studio.css", "/* AI digest reads as an editor's note pinned to the page. */", ".inbox-detail > .rounded-xl,\n.inbox-detail > .rounded-2xl,\n.inbox-detail blockquote,\n.inbox-detail article {\n  border-radius: 10px !important;\n  box-shadow: none !important;\n}\n\n/* AI digest reads as an editor's note pinned to the page. */"), /接管了 TSX 自己挂的圆角工具类/],
  ["批次 Q 回归·反向①：含 .rounded-* 锚点但不带 !important 是正常层叠比武（决定权仍在元素自己那侧），不该变红", () => mutate("src/styles/editorial-studio.css", "/* AI digest reads as an editor's note pinned to the page. */", ".inbox-detail > .rounded-xl {\n  border-radius: 6px;\n}\n\n/* AI digest reads as an editor's note pinned to the page. */"), null],
  ["批次 Q 回归·反向②：锚点 + !important 但值是 0（刻意压平，与 F/G/H 的「压平为 0」同族合法），不该变红", () => mutate("src/styles/editorial-studio.css", "/* AI digest reads as an editor's note pinned to the page. */", ".inbox-detail > .rounded-xl {\n  border-radius: 0 !important;\n}\n\n/* AI digest reads as an editor's note pinned to the page. */"), null],
  // 第 8 步批次 R：cards-local.css 整族收口——删掉查无宿主的 .cards-modal 家族（6 条规则），
  // 并把 7 处活圆角（8/9px）与 1 处活投影一起归到刻度上。三本账（圆角、阴影、幻影）在同一次
  // 退役里同时降，所以这两条测的是「幻影判据会不会只看全局样式表」——功能局部 CSS 里给画不出
  // 来的元素配档位，一样要红；写刻度值一样不该红（判据咬的是令牌债，不是「没宿主」本身）。
  ["批次 R 回归：在功能局部样式表里给查无宿主的类名新增离刻度圆角（.zz-cards-ghost 没有任何宿主，界面画不出来，但令牌债是真的）", () => mutate("src/features/creation/cards/cards-local.css", ".cards-hint {", ".zz-cards-ghost {\n  border-radius: 14px;\n}\n\n.cards-hint {"), /幻影选择器[\s\S]*新增了令牌债/],
  ["批次 R 回归·反向：同一个幻影类名写刻度上的 6px 不该红（幻影判据管的是令牌债，孤儿 CSS 整族清理是另一笔账）", () => mutate("src/features/creation/cards/cards-local.css", ".cards-hint {", ".zz-cards-ghost {\n  border-radius: 6px;\n}\n\n.cards-hint {"), null],
  // 第 8 步批次 S：两条「裸类名接管共享组件面板」已退役（.history-modal 的 14px + 手抄重投影、
  // .writing-recovery-notice 的 8px + box-shadow:none）。这一族的价值恰恰在于它是同特异度比武，
  // 局部 CSS 在懒加载 chunk 里发射更晚，所以组件挂的刻度工具类会被安静压掉——棘轮看不见
  // （14px 虽是离刻度，但这条测的是判据能不能认出「接管」这个形状，值故意用刻度上的 10px）。
  ["批次 S 回归：把 .history-modal 的裸类名接管加回来（值用刻度上的 10px 让棘轮咬不到，必须靠面板钩子不变量拦住；连 box-shadow 成对声明也一起测）", () => mutate("src/features/creation/history/history-local.css", ".history-modal {\n  width: min(520px, 100%);", ".history-modal {\n  border-radius: 10px;\n  box-shadow: none;\n  width: min(520px, 100%);"), /共享组件面板/],
  ["批次 S 回归·反向①：带祖先前缀的覆盖是显式决定（模式压平），不是全局接管，不该变红", () => mutate("src/features/creation/history/history-local.css", ".history-modal {\n  width: min(520px, 100%);", ".desktop-root--focus .history-modal {\n  border-radius: 0;\n}\n\n.history-modal {\n  width: min(520px, 100%);"), null],
  ["批次 S 回归·反向②：给面板钩子加非几何属性（padding）不该变红——判据只管圆角与投影这两条能被组件挂上的轴", () => mutate("src/features/creation/history/history-local.css", ".history-modal {\n  width: min(520px, 100%);", ".history-modal {\n  padding-left: 0;\n  width: min(520px, 100%);"), null],
  ["批次 S 回归：钩子扫描退化——把面板钩子从字面量改成常量引用（完整类名不再出现在开标签上），自检必须红，否则下一批人会把接管当合法写法", () => {
    mutate("src/features/creation/editor/WritingDesk.tsx", "<InlineNotice tone=\"warning\" className=\"writing-recovery-notice\">", "<InlineNotice tone=\"warning\" className={ZZ_NOTICE_PANEL}>");
    mutate("src/features/creation/editor/WritingDesk.tsx", "export function WritingDesk(", "const ZZ_NOTICE_PANEL = \"writing-recovery-notice\";\n\nexport function WritingDesk(");
  }, /钩子扫描退化/],
  // 第 8 步批次 V：全局搜索托盘那 5 条「特性页与主题样式表逐字同分支、值不同」的死声明已退役，
  // 判据⑧钉住这个形状。它的致命方向和幻影判据一样是「看不见」，所以除了咬新分叉，还要
  // 咬「两侧不再逐字可比」这种退化。正向用例故意用 var(--radius-*)：圆角棘轮对令牌写法豁免，
  // 红了只可能是判据⑧，不会冒充棘轮。
  ["批次 V 回归：把特性页与主题同分支的异值分叉加回来（search.css 给 .uni-search-group li button 写 --radius-3，主题那份是 --radius-2——同特异度只比发射顺序，特性页这条永远画不出来，却会被棘轮记成活债）", () => mutate("src/features/search/search.css", ".uni-search-group li button {\n  display: grid;", ".uni-search-group li button {\n  border-radius: var(--radius-3);\n  display: grid;"), /几何分支/],
  ["批次 V 回归·反向①：与主题同分支同值是冗余不是分叉（var(--radius-2) 两边一致，谁赢都一样），不该变红", () => mutate("src/features/search/search.css", ".uni-search-group li button {\n  display: grid;", ".uni-search-group li button {\n  border-radius: var(--radius-2);\n  display: grid;"), null],
  ["批次 V 回归·反向②：带模式前缀的覆盖是显式决定（分支文本不同，各管各的层叠），不该变红", () => mutate("src/features/search/search.css", ".uni-search-error {", ".desktop-root--focus .uni-search-error {\n  border-radius: 0;\n}\n\n.uni-search-error {"), null],
  ["批次 V 回归：分支归一化退化——把主题那份的选择器加上祖先前缀，两侧不再逐字同现，自检必须红（否则真分叉会被安静放行）", () => mutate("src/styles/editorial-studio.css", ".uni-search-shell {\n  overflow: hidden;", ".uni-search-overlay .uni-search-shell {\n  overflow: hidden;"), /判据退化/]
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
