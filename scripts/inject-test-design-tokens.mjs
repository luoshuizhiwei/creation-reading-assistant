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
  // 批次 O 的用例改这几份：幻影账的债主三度易位——批次 AH 清掉 styles.css 的 7 处后
  // 剩 encryption.css 的 .pe-modal（2 处），批次 AR 把它整族下线，债主换成
  // editorial-studio.css 的 .desktop-settings-grid 开关岛（1 处），批次 AS 又连岛带
  // 宿主一起删干净：HOSTLESS_BUDGET 从此是空表，幻影令牌债 0 处。所以这一族的新牙
  // 不再需要"带债的孤儿"当锚点——任何一笔新幻影债落在空表上都是即时红。
  // encryption.css 仍然备份：AR 那对复活/反向牙拿它当宿主（.pe-modal 回哪份文件
  // 才不会被别的判据先咬，测的是这一族）。WritingQuickReferencePanel 是
  // 唯一靠 BEM 动态前缀才活着的宿主，用它测「宿主判据退化」。
  "src/features/settings/encryption/encryption.css",
  "src/features/creation/editor/WritingQuickReferencePanel.tsx",
  // 批次 R 的用例改这份：它刚被整族收口（三本账同时降），用它证明幻影判据不只盯全局样式表，
  // 功能局部 CSS 里给画不出来的元素配新档位一样要红。
  "src/features/creation/cards/cards-local.css",
  // 批次 S 的用例改这几份：history-local.css 刚退役那条 .history-modal 裸类名接管，
  // 用它测「接管加回来必须红 / 带模式前缀与非几何属性合法」；WritingDesk 是把
  // .writing-recovery-notice 当面板钩子传给 <InlineNotice> 的唯一宿主，用它测「钩子扫描退化」。
  "src/features/creation/editor/WritingDesk.tsx",
  // 批次 AG 的「透镜退化」用例改这份：它是 .desktop-search-command 唯一的宿主（RingButton），
  // 把该类名挪到非按钮元素上，用来测「登记的控件类名不再被认成纯按钮类名」必须红。
  "src/components/layout/DesktopFrame.tsx",
  // 批次 AJ 的还债用例改这份：灵感页那条 shadow-paper 工具类一直被面板家族 !important 压着
  // （从没画出来过），随 .desktop-panel-card 归入 --shadow-1 一起删掉了——写回来必须红。
  "src/features/inspiration/InspirationPage.tsx",
  // 批次 AM 的还债用例改这三份：它们挂 motion-dialog / motion-toast，手上的 shadow-paper
  // 一直被家族 var(--shadow-3) / var(--shadow-4) !important 压着（jsdom 读产物实测带与
  // 不带同值），随本批删掉——写回来必须红，否则退役等于给这三个浮层松了绑。
  "src/components/interaction.tsx",
  "src/features/creation/inbox/ai-send-confirm.tsx",
  "src/features/creation/outline/OutlineTree.tsx",
  // 批次 AN 起，阴影棘轮那组入口用例改这份：LibraryPage 的浮层债按 §5.3 归级后清零，
  // 原本钉在它 `bg-paper-panel shadow-paper p-1` 上的 5 颗牙失去锚点，整体挪到这里——
  // 它是阴影台账上仍有活债（shadow-lift 1 处）的最简宿主，needle 只有一条。
  // 批次 AO 又把这批入口牙改钉 ErrorBoundary：分区卡归入 --shadow-1 后它才是台账上
  // 仍有活债、needle 唯一的最简 TSX 宿主；分区卡自己转而测本批新立的 TSX hairline 判据。
  "src/features/settings/sections/SectionWrapper.tsx",
  "src/components/ErrorBoundary.tsx",
  // 批次 AP 的还债用例改这份：药丸 Tabs 那条 shadow-sm 按 §2.5 整条不画了，条目随之移除
  // ——写回来必须红。⚠ 本仓 §2.5 判据只认 CSS 选择器里的按钮角色，TSX 侧的按钮投影
  // 由阴影棘轮单独盯（预算条目 0），这颗牙测的就是那条账还咬着。
  "src/components/ui/Tabs.tsx",
  // 批次 AN 的归级用例改这两份：Dialog / FontPicker 手上那条 Tailwind 刻度外档已按 §5.3
  // 换成属性形式令牌、预算条目随之移除——写回原工具类必须红，否则归级等于给这两处松绑。
  "src/components/ui/Dialog.tsx",
  "src/components/ui/FontPicker.tsx"
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
  // ⚠ 批次 AI 把这条分组规则里的 .project-home-create-first 死分支摘掉了，规则从此只剩
  // 一个选择器——needle 随之换成单选择器写法（测的还是同一条双排除不变量，不是死分支）。
  ["把 desktop-page-actions 首钮规则的 :not 排除退回单排除（ProjectHomePage 首钮是默认 primary，会被淡底样式压回 (0,1,1)）", () => mutate("src/styles/editorial-studio.css", '.desktop-page-actions button:first-child:not([data-variant="quiet"]):not([data-variant="ghost"]) {\n  border-color: color-mix(in srgb, var(--accent-spine) 92%', '.desktop-page-actions button:first-child:not([data-variant="ghost"]) {\n  border-color: color-mix(in srgb, var(--accent-spine) 92%'), /后代按钮规则未排除|editorial-studio\.css/],
  ["base 加回 px-3（会被 Tailwind 发射顺序压掉尺寸层与 icon 的 p-0）", () => mutate("src/components/ui.tsx", "justify-center gap-2 rounded-[var(--radius-1)] text-sm", "justify-center gap-2 rounded-[var(--radius-1)] px-3 text-sm"), /base 不得包含内边距/],
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
  ["批次 C 回归：把 .motion-toast 的 background 加回来——它与 tone 工具类同特异度而加载更晚，会整族遮蔽四种提示的颜色（批次 C 用 jsdom 实测过的真实缺陷）", () => mutate("src/styles/editorial-studio.css", ".motion-toast {\n  box-shadow: var(--shadow-4) !important;", ".motion-toast {\n  background: var(--studio-cloth);\n  box-shadow: var(--shadow-4) !important;"), /motion-toast|遮蔽/],
  ["批次 C 回归：--warning-tint 只写晨校值（成对性不变量必须管到新令牌，否则夜校把浅琥珀岛原样画在深色底上）", () => mutate("src/styles/tokens.css", "  --warning-tint: #2a2118;\n", ""), /颜色令牌必须在夜校/],
  // 第 8 步圆角棘轮：这五条测的是「棘轮能不能两头咬人」，以及「会不会咬到自己」。
  // expect 为 null 的是「必须保持绿」的反向用例——判据过严同样是缺陷。
  ["圆角棘轮①：Button base 的圆角旁再挂一处离刻度（批次 AV 起预算表为空，rounded-2xl 一出现就必须红；这条测的是 TSX 侧，棘轮②测 CSS 侧）", () => mutate("src/components/ui.tsx", "justify-center gap-2 rounded-[var(--radius-1)] text-sm", "justify-center gap-2 rounded-[var(--radius-1)] rounded-2xl text-sm"), /圆角刻度/],
  ["圆角棘轮②：把离刻度值写进预算表里根本没有的文件（settings-controls.css 预算视同 0，4px→7px 必须红）", () => mutate("src/features/settings/settings-controls.css", "  border-radius: 4px;", "  border-radius: 7px;"), /圆角刻度/],
  // 圆角棘轮③（还了债却不降预算）随批次 AV 退役：RADIUS_BUDGET 归零后表里没有条目，
  // `budget > actual` 对一个不存在的预算恒不成立，这条用例永远 SKIP（needle 已经不在了），
  // 硬留着只会变成「测不到任何东西还占一个名额」。stale 这条判据分支本身没有失去覆盖——
  // 阴影台账还剩 26 处，下面那条「阴影棘轮·还债不降预算」照旧咬得住它。
  ["圆角棘轮·反向①：说明注释里提到旧类名 rounded-xl 不算违规（不剥注释的话守卫会自己咬自己）", () => mutate("src/features/library/LibraryPage.tsx", "export function LibraryPage() {", "export function LibraryPage() {\n  {/* 这里原来是 rounded-xl，批次 M 收到 rounded-md */}"), null],
  ["圆角棘轮·反向②：50% 是形状决定不是圆角档位，写进规则里不该变红", () => mutate("src/features/settings/settings-controls.css", "  border-radius: 4px;", "  border-radius: 50%;"), null],
  // 批次 M 把阅读器族整片收到刻度上，用的正是下面这三种写法。这条反向用例钉的是
  // 「本批的迁移目标写法确实合法」——判据若把它们误伤，下一批就会退回去写 rounded-lg。
  ["圆角棘轮·反向③：批次 M 的三种目标写法 rounded-md(6px) / rounded-[var(--radius-3)] / rounded-full 都在刻度上，不该变红", () => mutate("src/features/library/LibraryPage.tsx", "export function LibraryPage() {", "export function LibraryPage() {\n  // zz: rounded-md rounded-[var(--radius-3)] rounded-full"), null],
  // 第 8 步批次 AW（间距棘轮立项）：五颗牙测的是「这本新账能不能两头咬人」。
  // 立项批的数字全部照登（465 处），所以正向用例必须造出**超出预算**的第 466 处，
  // 而不是随便加一个离刻度值——落在预算内的新增会被判据放过，这是按文件冻结的代价，
  // 也是它和「全仓一条总数」那种棘轮的差别（总数棘轮会被别处还的债挡刀）。
  ["间距棘轮①：CSS 侧在零预算文件里新增一处离刻度 padding（settings-controls.css 不在 SPACING_BUDGET 里，预算视同 0，2px 6px→2px 7px 必须红）", () => mutate("src/features/settings/settings-controls.css", "  padding: 2px 6px;", "  padding: 2px 7px;"), /间距刻度/],
  ["间距棘轮②：TSX 侧新增离刻度工具类（ui.tsx 预算 1 = sizes.sm 的 px-2.5（10px），再加一个 py-2.5 就是 2>1，必须红）", () => mutate("src/components/ui.tsx", 'sm: "h-7 px-2.5 text-xs"', 'sm: "h-7 px-2.5 py-2.5 text-xs"'), /间距刻度/],
  ["间距棘轮③：还了债却不降预算（LibraryPage 预算 1 = 搜索框 pl-9(36px)，改成 pl-8(32px) 是合法收敛但预算没跟着降，必须红并指名该文件）", () => mutate("src/features/library/LibraryPage.tsx", 'className="paper-input w-full pl-9 pr-8 text-sm"', 'className="paper-input w-full pl-8 pr-8 text-sm"'), /预算没跟着降/],
  // 反向三颗钉的是「别把合法写法咬进去」——判据过严的下一批人就会退回去散写数字，
  // 那才是这本账真正的失败模式。
  ["间距棘轮·反向①：rem 写法按 16px 基准折算后落在刻度上（padding: 2px 0.375rem = 2px 6px = --sp-1/--sp-3），不该被折算误伤成债", () => mutate("src/features/settings/settings-controls.css", "  padding: 2px 6px;", "  padding: 2px 0.375rem;"), null],
  ["间距棘轮·反向②：引用令牌的写法不在计数之列（padding-inline: var(--sp-6) 是收敛的目标形态，写它必须绿）", () => mutate("src/features/settings/settings-controls.css", "  padding: 2px 6px;", "  padding: 2px 6px;\n  padding-inline: var(--sp-6);"), null],
  ["间距棘轮·反向③：em/%/auto/负值是排版与布局语法不是档位（padding: 1.5em / margin-top: -6px / auto 三行都不该红）", () => mutate("src/features/settings/settings-controls.css", "  padding: 2px 6px;", "  padding: 2px 6px;\n  margin-top: -6px;\n  padding-left: 1.5em;\n  margin-inline: auto;"), null],
  // 批次 AX 的牙（字号 / 行高比率 / 行高长度式，规格 §3.2）。三本账各自成牙：
  // 同一处改动只能证明一本判据在咬，混在一颗牙里就分不清是哪本瞎了。
  // 立项批的牙必须能造出「超出预算的第 N+1 处」——所以正牙一律落在**该本账预算为 0 的文件**
  // （新增即红），或用**合法收敛不降预算**造 stale 红；两种形状都要有，缺一半就是半个判据。
  ["排版棘轮·字号①：CSS 侧在字号零预算文件里新增一处离刻度 font-size（inbox-local.css 不在 FS_BUDGET 里、视同 0，11px→11.5px 必须红）", () => mutate("src/features/creation/inbox/inbox-local.css", "  font-size: 11px;", "  font-size: 11.5px;"), /第 8 步字号：/],
  ["排版棘轮·字号②：TSX 侧把刻度上的 text-sm(14) 换成 text-lg(18)（LibraryPage 字号预算 0，§3.2 八档里没有 18px，必须红）", () => mutate("src/features/library/LibraryPage.tsx", '<div className="text-sm text-paper-muted">正在加载书籍...</div>', '<div className="text-lg text-paper-muted">正在加载书籍...</div>'), /第 8 步字号：/],
  ["排版棘轮·字号③：还了债却不降预算（批次 BD-4 按 §3.2 同名配对还掉 scene-radar.css 那条 12.5px→13，该文件 FS 预算清零、条目删除，牙随之搬家：styles.css 预算 20 = 里面 20 处活着的小数值，把唯一那条 .creation-palette-command 的 13.5px 收成 14px 是合法收敛，但 FS_BUDGET 没跟着降必须红。⚠ 原先钉的 scene-radar 12.5px 已随债还掉、needle 失效——牙跟着债走，不换锚点等于这条从此不测任何东西）", () => mutate("src/styles.css", "  font-size: 13.5px;", "  font-size: 14px;"), /FS_BUDGET 里对应数字/],
  ["排版棘轮·行高比率①：新增一个八档之外的比率（history-local.css 不在 LH_BUDGET 里视同 0，1.5→1.65 必须红——1.65 正是本仓最常见的手抄多一档。⚠ 批次 BD-4 把这条锚点规则的字号从 11.5px 收到 11px（§3.2 同名配对 11↔1.5），needle 跟着新字号重写，锚的还是同一条 .history-item-retention）", () => mutate("src/features/creation/history/history-local.css", "  font-size: 11px;\n  line-height: 1.5;", "  font-size: 11px;\n  line-height: 1.65;"), /第 8 步行高比率：/],
  ["排版棘轮·行高比率②：还了债却不降预算（批次 BC 把 17 处收成 2 处、BD-2 又删掉 styles.css 那条从未画出来的 18px，剩下的活债就是稿纸胜者那条；editorial-studio.css 预算 1 = .scene-editor-content 的 17px/line-height: 2，把它收成 §3.3 稿纸的 1.85 是合法收敛，但 LH_BUDGET 没跟着降必须红。⚠ 原先钉的 cards-local.css 1.65、再早的 styles.css 18px/2 都随债还掉、needle 失效——牙跟着债走，不换锚点等于这条从此不测任何东西）", () => mutate("src/styles/editorial-studio.css", "  font-size: 17px;\n  line-height: 2;", "  font-size: 17px;\n  line-height: 1.85;"), /LH_BUDGET 里对应数字/],
  ["排版棘轮·行高长度式①：leading-N 就是带单位的行高（产物实测 .leading-6{line-height:1.5rem}），LibraryPage 该行预算 0，加一个 leading-6 必须红", () => mutate("src/features/library/LibraryPage.tsx", '<div className="text-sm text-paper-muted">正在加载书籍...</div>', '<div className="text-sm leading-6 text-paper-muted">正在加载书籍...</div>'), /第 8 步行高长度式/],
  ["排版棘轮·行高长度式②：还了债却不降预算（ui.tsx 预算 2 = TextArea 与 EmptyState 各一处 leading-6，摘掉 EmptyState 那处后 LHL_BUDGET 没跟着降必须红）", () => mutate("src/components/ui.tsx", "mt-2 text-sm leading-6 text-paper-muted", "mt-2 text-sm text-paper-muted"), /LHL_BUDGET 里对应数字/],
  ["排版棘轮·反向①：引用令牌的写法是收敛目标形态（font-size: var(--text-13) 不该红——这本账数的是散写字面值，不是「有没有用令牌」）", () => mutate("src/features/settings/encryption/encryption.css", "  color: var(--text-muted, #5a6270);\n  font-size: 13px;", "  color: var(--text-muted, #5a6270);\n  font-size: var(--text-13);"), null],
  ["排版棘轮·反向②：§3.3 的阅读器行高 1.9 与稿纸 1.85 在合法集内（search.css 行高比率预算 0，1.6→1.9 不该红——判据把规格另一处明文允许的值咬成债，逼人绕过判据）", () => mutate("src/features/search/search.css", "  line-height: 1.6;", "  line-height: 1.9;"), null],
  ["排版棘轮·反向③：clamp() 是流式排版语法、不判（editorial-studio.css 那两处 clamp 里的 22/27/20/23 全都不是档位值，把它们逐个计数等于把「响应式」本身判成债）", () => mutate("src/styles/editorial-studio.css", "clamp(22px, 2vw, 27px)", "clamp(21px, 1.7vw, 26px)"), null],
  ["排版棘轮·反向④：任意值 text-[13px] 落在刻度上就合法，leading-none 是无单位词形不是长度式（两处同挂在一个类串上，都不该红）", () => mutate("src/features/library/LibraryPage.tsx", '<div className="text-sm text-paper-muted">正在加载书籍...</div>', '<div className="text-[13px] leading-none text-paper-muted">正在加载书籍...</div>'), null],
  // 第 8 步批次 F：那条把浮层圆角整体吃掉的 12px !important 已删，判据钉住它的「形状」。
  // ①故意用刻度上的 10px，让棘轮咬不到、只有浮层不变量能报警——否则这条用例测的是棘轮。
  ["批次 F 回归：把浮层圆角的 !important 全局接管加回来（裸类名 + !important，值 10px 在刻度上、棘轮咬不到，必须靠浮层不变量拦住）", () => mutate("src/styles/editorial-studio.css", ".motion-toast {\n  box-shadow: var(--shadow-4) !important;", ".motion-toast {\n  border-radius: 10px !important;\n  box-shadow: var(--shadow-4) !important;"), /浮层容器/],
  ["批次 F 回归·反向①：带模式前缀的 !important 压平是显式决定（专注模式把横幅压成方角），不是全局接管，不该变红", () => mutate("src/styles/editorial-studio.css", ".motion-toast {\n  box-shadow: var(--shadow-4) !important;", ".desktop-root--focus .motion-toast {\n  border-radius: 0 !important;\n}\n\n.motion-toast {\n  box-shadow: var(--shadow-4) !important;"), null],
  ["批次 F 回归·反向②：裸类名 + 非 !important 的自持圆角（.migration-banner 在 TSX 里不带 rounded 工具类，这条 CSS 是它圆角的唯一来源）不该变红", () => mutate("src/styles.css", ".migration-banner {\n  display: flex;", ".migration-banner {\n  border-radius: 10px;\n  display: flex;"), null],
  // 第 8 步批次 G：面板家族曾有两条都带 !important 的规则互相压制（同特异度靠加载顺序赢）。
  // 判据管的是「同一面板类名的 !important 圆角来源至多一条」，所以正向用例必须造出第二条。
  // ⚠ 批次 AI 把这条家族分组里的 .settings-card/.stats-panel/.desktop-editor-card/
  // .desktop-source-card/.desktop-ai-card 死分支摘掉了，`.desktop-ai-card` 的裸类名规则随之消失
  // ——needle 改钉幸存的 .desktop-library-panel，测的还是「同一面板类名第二条 !important 圆角来源」。
  ["批次 G 回归：把面板 blanket 的 !important 圆角加回来（值用刻度上的 10px，棘轮咬不到；此时同一面板类名出现第二条 !important 来源，必须靠不变量拦住）", () => mutate("src/styles/editorial-studio.css", ".desktop-library-panel {\n  border-color: var(--border-subtle) !important;", ".desktop-library-panel {\n  border-color: var(--border-subtle) !important;\n  border-radius: 10px !important;"), /面板类名的圆角又出现多条/],
  ["批次 G 回归·反向①：同一条规则里 shorthand + 单角都带 !important 是同一来源，不该算两条", () => mutate("src/styles/editorial-studio.css", "  border-radius: var(--radius-panel) !important;\n  box-shadow: 0 1px 2px rgba(15, 20, 28, 0.04) !important;", "  border-radius: var(--radius-panel) !important;\n  border-top-left-radius: 10px !important;\n  box-shadow: 0 1px 2px rgba(15, 20, 28, 0.04) !important;"), null],
  ["批次 G 回归·反向②：面板另有一条非 !important 的自持圆角（各页面自己的面板规则，与家族 !important 共存时胜者明确）不该变红", () => mutate("src/styles.css", ".stats-card {\n  border: 1px solid var(--paper-line);", ".stats-card {\n  border: 1px solid var(--paper-line);\n  border-radius: 10px;"), null],
  // 第 8 步批次 H：`X > *` 的 !important 非零圆角 blanket（往透明包装器刷弧度）。
  // 正向值用刻度上的 10px，让棘轮咬不到、只有网格不变量拦得住；反向证明 0 压平合法。
  ["批次 H 回归：加一条 `X > * { border-radius: 10px !important }` 网格 blanket（10px 在刻度上、棘轮咬不到，必须靠网格不变量拦住）", () => mutate("src/styles/editorial-studio.css", "/* Buttons are physical: a lit crown, a pressed state. */", ".zz-grid-blanket > * {\n  border-radius: 10px !important;\n}\n\n/* Buttons are physical: a lit crown, a pressed state. */"), /网格子元素刷非零圆角/],
  ["批次 H 回归·反向：`X > * { border-radius: 0 !important }` 是压平（.stats-page .stats-grid > * 就这么用），不该变红", () => mutate("src/styles/editorial-studio.css", "/* Buttons are physical: a lit crown, a pressed state. */", ".zz-grid-flatten > * {\n  border-radius: 0 !important;\n}\n\n/* Buttons are physical: a lit crown, a pressed state. */"), null],
  // 第 8 步批次 I：阴影棘轮。这组测三件事——能不能咬新债（TSX 工具类 / CSS 声明 / @apply
  // 三条入口各一条）、还了债不降预算会不会红、以及四种豁免会不会误伤。
  ["阴影棘轮①：TSX 工具类新债——已有预算文件里同串再加一个 shadow-lift（宿主批次 AO 起改为 ErrorBoundary：它是阴影台账上仍有活债、且 needle 唯一的最简 TSX 宿主；再加一处必须红）", () => mutate("src/components/ErrorBoundary.tsx", "bg-paper-panel p-6 shadow-paper", "bg-paper-panel p-6 shadow-paper shadow-lift"), /阴影刻度/],
  ["阴影棘轮②：CSS 声明新债——把字面投影写进阴影预算表里根本没有的文件（inbox-local.css 视同 0，必须红）", () => mutate("src/features/creation/inbox/inbox-local.css", ".inbox-page {\n  display: flex;", ".inbox-page {\n  box-shadow: 0 10px 30px rgba(34, 38, 48, 0.05);\n  display: flex;"), /阴影刻度/],
  ["阴影棘轮③：@apply 分支——@apply 里的 shadow-lift 编译后就是一条 box-shadow 声明，和 TSX 挂工具类等价，必须同判（批次 AL 把 .paper-panel 那条压死的 shadow-lift 退役后，这颗牙挪到仍活着的夜读皮肤宿主上）", () => mutate("src/styles.css", "@apply border border-stone-800 text-stone-100 shadow-paper;", "@apply border border-stone-800 text-stone-100 shadow-paper shadow-lift;"), /阴影刻度/],
  ["阴影棘轮·反向①：伏笔是本产品的业务词（foreshadow），text-shadow 是属性名——都在代码里而非注释里，前后断言必须挡住，不该变红", () => mutate("src/features/library/LibraryPage.tsx", "export function LibraryPage() {", "export function LibraryPage() {\n  const zzProbe = \"foreshadow foreshadowResolved foreshadows text-shadow\";\n  void zzProbe;"), null],
  ["阴影棘轮·反向②：四种合法豁免——shadow-none / shadow-inner / 颜色档 shadow-white\\/20（只产 --tw-shadow-color）/ [box-shadow:var(--focus-ring)] 焦点环，都不该变红", () => mutate("src/features/library/LibraryPage.tsx", "export function LibraryPage() {", "export function LibraryPage() {\n  const zzExempt = \"shadow-none shadow-inner shadow-white/20 focus-visible:[box-shadow:var(--focus-ring)]\";\n  void zzExempt;"), null],
  ["阴影棘轮·还债不降预算：把 ErrorBoundary 的 shadow-paper 换成真正发射的 [box-shadow:var(--shadow-2)] 是合法收敛（债 1→0），但预算仍是 1，必须红并指名该文件（用 shadow-2 而非 shadow-1：这条串还挂着校样红 border，shadow-1 会同时踩批次 AO 新立的 TSX hairline 判据，红因就不纯了）", () => mutate("src/components/ErrorBoundary.tsx", "bg-paper-panel p-6 shadow-paper", "bg-paper-panel p-6 [box-shadow:var(--shadow-2)]"), /预算没跟着降/],
  // 第 8 步批次 K：幻影写法硬红。shadow-[var(--shadow-N)] 在产物里只产 --tw-shadow-color、
  // 没有 box-shadow 声明——「改了画不出来」，且旧判据还给它记成功还债，所以单独 fail()。
  ["批次 K 回归：幻影写法 shadow-[var(--shadow-2)] 必须硬红（不进计数，因为它画的根本不是阴影）", () => mutate("src/components/ErrorBoundary.tsx", "bg-paper-panel p-6 shadow-paper", "bg-paper-panel p-6 shadow-paper shadow-[var(--shadow-2)]"), /幻影/],
  ["批次 K 回归·反向：属性形式 [box-shadow:var(--shadow-2)] 与其 hover: 变体是产物实测唯一能发射的任意值写法，既不被幻影判据咬、又走 var(--shadow) 豁免不计数，不该变红", () => mutate("src/features/library/LibraryPage.tsx", "export function LibraryPage() {", "export function LibraryPage() {\n  const zzReal = \"[box-shadow:var(--shadow-2)] hover:[box-shadow:var(--shadow-2)]\";\n  void zzReal;"), null],
  ["批次 K 回归：字面量任意值 shadow-[0_1px_0_rgba(...)] 确实发射投影（.paper-topbar 实测），所以不归幻影硬红管——但它是刻度外的手抄值，由棘轮计债（同宿主再加一处就超预算，红的是阴影刻度而非幻影）", () => mutate("src/components/ErrorBoundary.tsx", "bg-paper-panel p-6 shadow-paper", "bg-paper-panel p-6 shadow-paper shadow-[0_1px_0_rgba(255,255,255,0.45)]"), /阴影刻度/],
  // 第 8 步批次 L：别名归位。圆角棘轮豁免 var(--radius-*)，债从 CSS 声明搬进令牌定义后
  // 判据就瞎了——把 --radius-panel 改回 8px，面板家族整体无声改版、棘轮一个不响。
  // 这两条测的是「映射钉得够不够死」：字面量要红，指错档也要红。
  ["批次 L 回归：把 --radius-panel 改回字面量 8px（面板家族 9 个成员 + 预览区会整片变圆，而圆角棘轮看不见令牌里的值，必须靠别名映射拦住）", () => mutate("src/styles/tokens.css", "  --radius-panel: var(--radius-2);", "  --radius-panel: 8px;"), /别名归位|圆角刻度/],
  ["批次 L 回归：别名指向错档（--radius-control → var(--radius-3)，控件从 4px 变 10px），证明钉的是精确映射而不是「只要不是字面量就行」", () => mutate("src/styles/tokens.css", "  --radius-control: var(--radius-1);", "  --radius-control: var(--radius-3);"), /别名归位|圆角刻度/],
  ["批次 L 回归·反向：别名之间隔一个空格/换行仍是合法映射（棘轮与别名判据都不该因空白而红）", () => mutate("src/styles/tokens.css", "  --radius-panel: var(--radius-2);", "  --radius-panel:   var( --radius-2 );"), null],
  // 第 8 步批次 N：styles.css 退役了一批从不渲染的死圆角，预算随之降了一截。
  // 这两条测的是这次同步有没有把 styles.css 的咬合力一起删掉：
  // 拿批次 N 删过的同一个宿主、同一属性，离刻度值必须红、刻度值必须绿。
  // 批次 AQ 起 .creation-wizard 的圆角真源在 editorial（var(--radius-3)，同特异度更晚），
  // 在它身上写值必被压死 → 会同时惊动新补的「主题 × 主题」分叉判据，红因不再唯一。
  // 这对牙挪到 .creation-step：宿主真实存在、全仓没有第二处几何声明，棘轮是唯一开口的判据。
  ["第 8 步批次 N 回归：把退役掉的死圆角以离刻度值写回 .creation-step（这一页的预算已经降过一档，多一处必须红——否则这次退役等于给 styles.css 松了绑）", () => mutate("src/styles.css", ".creation-step {\n  margin: 0;", ".creation-step {\n  border-radius: 22px;\n  margin: 0;"), /圆角刻度/],
  ["第 8 步批次 N 回归·反向：同一个宿主写刻度上的 6px 不该红（styles.css 还剩一批活债，棘轮数的是离刻度，不是禁止字面量）", () => mutate("src/styles.css", ".creation-step {\n  margin: 0;", ".creation-step {\n  border-radius: 6px;\n  margin: 0;"), null],
  // 第 8 步批次 O：幻影选择器判据。它守的是「宿主不存在」这一类死法，方向性和前几条相反——
  // 判据唯一致命的错法是「把活 CSS 判成幻影」（那样删除就是无声改版），
  // 所以除了咬新债/咬不降预算，还要咬「判据自己退化」和证明「宿主靠动态拼接的活类不被误伤」。
  // ⚠ 批次 AH 把 .desktop-module-card 整族删掉了（幻影判据的示范孤儿终于被真还），
  // 这三条的锚点随之改钉到同一张表里同样查无宿主、但还活着的 .desktop-inspiration-card：
  // 用例语义一字不动，只是换了个仍然存在的幻影宿主。
  // 批次 AQ 之后 .desktop-inspiration-card 的两条几何分支真源全在 editorial（0 / none，
  // 同特异度更晚），在它身上写值会先被「主题 × 主题」分叉判据拦下——这对幻影牙的红因
  // 就不再是幻影判据了。挪到 .paste-preview-dialog：同样查无宿主（粘贴预览对话框在
  // 迁移后的 TSX 里不再挂这个类名），且全仓没有第二处几何声明。
  // ⚠ 批次 AT 把 132 条幻影规则整族清零，.paste-preview-dialog 这条规则本身也删了。
  // 这对牙不因此失效——幻影判据测的是「给查无宿主的类名配债」，不依赖那条规则还在文件里。
  // 六颗牙（O①/O反、AH 正/反、AS 正/反）的锚点统一迁到 .creation-step（活宿主、
  // styles.css 里唯一一处 `margin: 0` 的短锚），注入内容改为**整条复活**孤儿规则再写值：
  // 语义从「往仍在账上的孤儿写值」变为「复活已退役的孤儿并写值」，正是 AH 之后
  // 每一族整编退役都要防的形状（与 AH② 的 .desktop-start-hero 同一套路）。
  ["批次 O 回归①：给查无宿主的 .paste-preview-dialog 新增一处离刻度圆角（界面画不出来，令牌债却是真的，必须被幻影判据咬住）", () => mutate("src/styles.css", ".creation-step {\n  margin: 0;", ".paste-preview-dialog {\n  border-radius: 14px;\n  display: grid;\n}\n\n.creation-step {\n  margin: 0;"), /新增了令牌债/],
  // 批次 O 这两颗「预算 stale」牙的债主迁移史：.desktop-module-card（AH 删）→
  // .pe-modal（AR 删）→ .desktop-settings-grid 开关岛（AS 删）。岛下线后
  // HOSTLESS_BUDGET 是空表，「还了债不降预算」这条 stale 路径只有在有人重新登记
  // 预算时才可达，原牙随锚点一起失效（harness 计为无效，不静默放过）。
  // 顶上来的是 AS 真正修掉的盲区：旧幻影透镜 per.flat().some(canRender) 认
  // 「整条规则里有一个活类名」就算整条活着，于是
  //   `.desktop-settings-grid .settings-wide`（死祖先 + 活后代）
  // 这种 CSS 上永远匹配不到任何元素的分支，在两本账里都不露面。新口径逐分支
  // every(canRender)：一条规则只有存在「每个类名都能渲染」的分支才算活。
  // ⚠ 正向那条用 styles.css 当宿主表：它不在 HOSTLESS_BUDGET 里（预算 0），
  // 任何一笔幻影债落上去都是即时红——不需要再靠预算数字造 stale。
  ["第 8 步批次 AS 回归：把「死祖先 + 活后代」的复合分支写回 styles.css（旧整条口径会因为 .settings-wide 活着而整条放过，新的逐分支口径必须当场咬住；同时证明开关岛下线没有给 styles.css 松绑）", () => mutate("src/styles.css", ".creation-step {\n  margin: 0;", ".desktop-settings-grid .settings-wide {\n  box-shadow: 0 12px 30px rgba(59, 39, 24, 0.055);\n}\n\n.creation-step {\n  margin: 0;"), /新增了令牌债/],
  ["第 8 步批次 AS·反向：活祖先 + 活后代的复合分支写刻度上的 6px 不该红（收紧只能把「匹配不出来」的分支判死，不能把正常嵌套选择器一起判死——否则下次再出现死祖先形状没人敢信这条透镜）", () => mutate("src/styles.css", ".creation-step {\n  margin: 0;", ".desktop-page-scroll .settings-wide {\n  border-radius: 6px;\n}\n\n.creation-step {\n  margin: 0;"), null],
  ["批次 O 回归④：宿主判据退化——把 BEM 修饰类的动态前缀写法拆掉（`writing-quick-kind--${card.kind}` 退回裸类名），自检必须红，否则活 CSS 会被安静判死", () => mutate("src/features/creation/editor/WritingQuickReferencePanel.tsx", "writing-quick-kind writing-quick-kind--${card.kind}", "writing-quick-kind"), /宿主判据把/],
  ["批次 O 回归·反向：幻影规则里写刻度上的 6px 不该红（判据咬的是令牌债，不是「这条规则没宿主」这件事本身——整族孤儿 CSS 的清理是另一笔账）", () => mutate("src/styles.css", ".creation-step {\n  margin: 0;", ".paste-preview-dialog {\n  border-radius: 6px;\n  display: grid;\n}\n\n.creation-step {\n  margin: 0;"), null],
  // 第 8 步批次 AH：styles.css 桌面首页/创作索引遗留族整族下线（100 条幻影规则、
  // 阴影账 21→14、幻影账 7→0 同提交清账）。下面两条证明这次退役没有给 styles.css 松绑：
  // 把删掉的手抄投影写回仍然空转的规则、或把整族规则原样复活，两本账必须当场咬住。
  // 这对牙原先钉在 .desktop-inspiration-card 上；批次 AQ 之后它的两条几何分支真源都在
  // editorial（0 / none，同特异度更晚），往它身上写值会先被「主题 × 主题」分叉判据拦下。
  // 挪到同样查无宿主、且全仓无第二处几何声明的 .paste-preview-dialog，双红语义原样保留。
  ["第 8 步批次 AH 回归：把退役的手抄投影写回 .paste-preview-dialog（styles.css 阴影预算已降到 14、幻影条目已删，多一处必须双红）", () => mutate("src/styles.css", ".creation-step {\n  margin: 0;", ".paste-preview-dialog {\n  box-shadow: 0 12px 30px rgba(59, 39, 24, 0.055);\n  display: grid;\n}\n\n.creation-step {\n  margin: 0;"), /新增了令牌债/],
  // 这颗牙原先拿 `.desktop-settings-grid { display: grid;` 当插入锚点——批次 AS 把
  // 那座开关岛整族删了，锚点随之消失。换钉 .creation-step（活宿主、styles.css 里
  // 唯一一处 margin: 0 的短锚），语义一字未动：复活整族孤儿规则照样要被咬。
  ["第 8 步批次 AH 回归②：把整族删掉的 .desktop-start-hero 原样复活（宿主依旧查无，孤儿 CSS 回来了照样被幻影判据咬）", () => mutate("src/styles.css", ".creation-step {\n  margin: 0;", ".desktop-start-hero {\n  box-shadow: 0 18px 48px rgba(71, 46, 27, 0.07);\n}\n\n.creation-step {\n  margin: 0;"), /新增了令牌债/],
  ["第 8 步批次 AH·反向：幻影规则里写 var(--shadow-2) 不该红（幻影账管的是离刻度令牌债，删族不该变成「幻影规则禁止任何投影」）", () => mutate("src/styles.css", ".creation-step {\n  margin: 0;", ".paste-preview-dialog {\n  box-shadow: var(--shadow-2);\n  display: grid;\n}\n\n.creation-step {\n  margin: 0;"), null],
  // 第 8 步批次 AR：口令弹窗的 .pe-modal 族三条整族下线（宿主谓词实测三个类名在源码与
  // 产物 JS 里都查无落点——它背着三本账：圆角 14px、手抄三层投影、幻影债 2 处，同提交清）。
  // 正向把这族原样复活：文件已不在圆角/阴影/幻影任何一本预算表里，出现一处就得当场红——
  // 这证明「下线」不是给这个文件松绑。反向证明刻度内的孤儿写法仍合法。
  ["第 8 步批次 AR 回归：把退役的 .pe-modal 族原样复活（三条预算条目都已随债清零移除，幻影债回来了必须被当场咬住）", () => mutate("src/features/settings/encryption/encryption.css", ".pe-warning {", ".pe-modal {\n  border-radius: 14px;\n  box-shadow: 0 40px 110px rgba(0, 0, 0, 0.32);\n}\n\n.pe-warning {"), /新增了令牌债/],
  ["第 8 步批次 AR·反向：孤儿规则里写刻度上的 10px 不该红（幻影账数的是离刻度令牌债，不是「这条规则没宿主」本身——整族孤儿的清理是另一笔账，见 AH 那族反向用例同一条理由）", () => mutate("src/features/settings/encryption/encryption.css", ".pe-warning {", ".pe-modal {\n  border-radius: 10px;\n}\n\n.pe-warning {"), null],
  // 第 8 步批次 AI：死分支判据。幻影账的口径是整条规则——`per.flat().some(canRender)` 一命中
  // 活分支就整条放过，于是「组规则里藏一支查无宿主的分支」是幻影债唯一的隐身形状（本批摘掉 56 条）。
  // ①用批次 AH 之后仍然在账上的真实形状复现（history-tabs 早已是 <Button>，focus 支躲进活组）；
  // ②给活家族规则挂一支 AH 删过的孤儿类名，证明「复活孤儿并寄生在活规则里」这条新路一样被咬；
  // 反向那条钉判据别过严：全是活分支的组规则不该红，否则下一批人会把组规则拆成单选择器绕开它。
  ["第 8 步批次 AI 回归：把摘掉的 .history-tabs button:focus-visible 死分支接回活组（幻影账看整条规则会整条放过，死分支判据必须单独咬住）", () => mutate("src/features/creation/history/history-local.css", ".history-field select:focus-visible,\n.history-field input:focus-visible {", ".history-tabs button:focus-visible,\n.history-field select:focus-visible,\n.history-field input:focus-visible {"), /第 8 步死分支/],
  // ⚠ 批次 AJ 把这条家族规则按「能不能归 --shadow-1」拆成两半（.stats-card/.paper-panel 留手抄投影，
  // .desktop-panel-card/.desktop-library-panel 归令牌），needle 随之改钉拆分后的新家族半区。
  ["第 8 步批次 AI 回归②：给活的面板家族规则挂一支 AH 已整族下线的 .desktop-start-hero（宿主依旧查无——孤儿复活时寄生在活分支后面，两本账都不露面，必须靠死分支判据拦住）", () => mutate("src/styles/editorial-studio.css", ".desktop-panel-card,\n.desktop-library-panel {\n  border-radius: var(--radius-panel) !important;", ".desktop-panel-card,\n.desktop-library-panel,\n.desktop-start-hero {\n  border-radius: var(--radius-panel) !important;"), /第 8 步死分支/],
  ["第 8 步批次 AI·反向：给同一条活组再加一支宿主存在的分支（.library-toolbar 在 TSX 与产物里都有落点），全活不该红——判据过严会把组规则逼成单选择器", () => mutate("src/features/creation/history/history-local.css", ".history-field select:focus-visible,\n.history-field input:focus-visible {", ".history-field select:focus-visible,\n.history-field input:focus-visible,\n.library-toolbar input:focus-visible {"), null],
  // 第 8 步批次 AU：positiveClasses 的 Tailwind 前缀滤网会把**业务类名**也滤成空集
  // （.outline-scene / .outline-scene-meta / .font-mono 撞 outline- / font-），
  // 于是「这一支查不到业务类名 → 整条保守不判」的豁免被连坐触发：AU 前
  // `.writing-chapter-button, .writing-scene-button, .outline-scene` 整条隐身，
  // 两支真死的类名照样发射进产物。修法 = 把前缀滤网整个删掉：工具类的宿主照样写在
  // TSX 的 className 字面量里（hostText 判活），不需要一张表替它免检。
  // 下面四颗牙：①复活本批摘掉的死支（寄生在曾被吞的活支后面，正是盲区形状）；
  // ②给查无宿主的前缀类名配离刻度投影（幻影账此前对它完全失明——类名滤成空集就
  // 躲过保守豁免；去滤网后这笔债必须当场红）；
  // ③反向钉住「活前缀类名不许误判死」（判据过严会把 outline-* 一族业务类名
  // 全判死，那才是无声改版的方向）；④反向钉住「孤儿前缀类名写刻度上的 6px 合法」
  // （和 O·反/AH·反/AR·反同一条规矩——清的是令牌债，不是「没宿主」这件事本身）。
  // ⚠ 守卫内 MUST_BE_SEEN 那条退化自检（有人把前缀滤网加回来时变红）
  // 没有配套牙：它只能靠改守卫本身触发，而牙一律只动备份清单里的宿主文件——
  // 往守卫里注改是自我指涉（AR 那轮就犯过一次并回退）。它的红因是手工实验验证的：
  // 把滤网补回旧写法，守卫唯一红的就是 MUST_BE_SEEN，其余判据全部保持绿。
  ["第 8 步批次 AU 回归①：把摘掉的 .writing-scene-button 死支接回 .outline-scene 活组（这支业务类名本身没撞前缀表，但同组的 .outline-scene 撞；AU 前整条被连坐豁免，两副透镜都看不见它）", () => mutate("src/styles/editorial-studio.css", ".outline-scene {\n  border-radius: var(--radius-1);", ".outline-scene,\n.writing-scene-button {\n  border-radius: var(--radius-1);"), /第 8 步死分支/],
  ["第 8 步批次 AU 回归②：给查无宿主、且曾撞前缀表的 .outline-scene-meta 配一笔离刻度投影（幻影账此前对它整条失明——类名滤成空集就躲过保守豁免；去滤网后这笔债必须当场红）", () => mutate("src/styles.css", ".creation-step {\n  margin: 0;", ".outline-scene-meta {\n  box-shadow: 0 12px 30px rgba(59, 39, 24, 0.055);\n}\n\n.creation-step {\n  margin: 0;"), /新增了令牌债/],
  ["第 8 步批次 AU·反向③：活前缀类名 .outline-scene 单独写一条规则不该红（OutlineTree.tsx 就是宿主，去滤网只会把「既不在源码也不在产物又不撞动态前缀」的类名翻死；判据若把这个方向判严，outline-* 一族活 CSS 会集体被误杀）", () => mutate("src/styles.css", ".creation-step {\n  margin: 0;", ".outline-scene {\n  background: red;\n}\n\n.creation-step {\n  margin: 0;"), null],
  ["第 8 步批次 AU·反向④：孤儿前缀类名写刻度上的 6px 不该红（同 O·反/AH·反/AR·反那条规矩——幻影账数的是令牌债，AU 修的是「隐身」，不是给孤儿加禁令；条数本身也没钉零容忍）", () => mutate("src/styles.css", ".creation-step {\n  margin: 0;", ".outline-scene-meta {\n  border-radius: 6px;\n}\n\n.creation-step {\n  margin: 0;"), null],
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
  ["批次 V 回归：分支归一化退化——把主题那份的选择器加上祖先前缀，两侧不再逐字同现，自检必须红（否则真分叉会被安静放行）", () => mutate("src/styles/editorial-studio.css", ".uni-search-shell {\n  overflow: hidden;", ".uni-search-overlay .uni-search-shell {\n  overflow: hidden;"), /判据退化/],
  // 第 8 步批次 AQ：同一判据补上「主题表 × 主题表」方向（原先靠「历史账 16 条」豁免，
  // AQ 清零后豁免的前提没了）。四颗牙对着新方向逐条打：正向 = 复活一处刚删的死声明，
  // 反向两颗 = 证明同值冗余与模式前缀覆盖依旧合法（别把清零变成「styles.css 不许写几何」），
  // 退化 = 把自检锚点的选择器改掉，证明这个方向失明时守卫自己先红。
  ["批次 AQ 回归：把刚退役的侧栏卡圆角写回 styles.css（editorial 那份 border-radius: 0 同特异度更晚，它从未弯过一个角，却被圆角棘轮记成活债——现在这个方向零容忍）", () => mutate("src/styles.css", ".desktop-sidebar-card {\n  padding: 12px;\n}", ".desktop-sidebar-card {\n  border-radius: var(--radius-2);\n  padding: 12px;\n}"), /几何分支/],
  ["批次 AQ 回归·反向①：两份主题表同分支写同一个值是冗余不是分叉（谁赢都一样），不该变红", () => mutate("src/styles.css", ".desktop-sidebar-card {\n  padding: 12px;\n}", ".desktop-sidebar-card {\n  border-radius: 0;\n  padding: 12px;\n}"), null],
  ["批次 AQ 回归·反向②：带模式前缀的覆盖分支文本不同、各管各的层叠，是显式决定，不该变红", () => mutate("src/styles.css", ".desktop-sidebar-card {\n  padding: 12px;\n}", ".desktop-root--focus .desktop-sidebar-card {\n  border-radius: 4px;\n}\n\n.desktop-sidebar-card {\n  padding: 12px;\n}"), null],
  ["批次 AQ 回归·退化：把主题×主题自检锚点的一侧加上祖先前缀（.cards-view-switch 两边不再逐字同现），这个方向会失明、真分叉被安静放行，必须自己先红", () => mutate("src/styles/editorial-studio.css", ".writing-mode-switch,\n.writing-outline-view-switch,\n.cards-view-switch {", ".writing-mode-switch,\n.writing-outline-view-switch,\n.cards-page .cards-view-switch {"), /判据退化/],
  // 第 8 步批次 X：主题层自己压自己的 15 条离刻度投影声明已退役（每张胜者表逐字节不变）。
  // 沿用批次 N 那对「退役同一宿主同一属性」的形状：正向证明这一页的阴影预算确实降到了
  // 新值（写回一条离刻度必须红），反向证明刻度写法仍合法（别把退役变成「禁止投影」）。
  ["第 8 步批次 X 回归：把退役掉的死投影以离刻度值写回 .stats-card（styles.css 阴影预算已降过一档，多一处必须红——否则退役等于给它松了绑）", () => mutate("src/styles.css", ".stats-card {\n  border: 1px solid var(--paper-line);", ".stats-card {\n  box-shadow: 0 1px 4px rgba(56, 38, 25, 0.06);\n  border: 1px solid var(--paper-line);"), /阴影刻度/],
  // ⚠ 反向锚点从 var(--shadow-1) 挪到 var(--shadow-2)：这条用例的本意是证明「退役死投影
  // ≠ 禁止投影」，用哪个刻度档都能证。而批次 AE 立了 hairline 不变量——.stats-card 同规则
  // 里有 border: 1px solid var(--paper-line)，再写 shadow-1 会把同色描边拼成 2px 双线，
  // 正是该拦的写法（旧用例把它当合法放行，等于让判据替错误写法背书）。shadow-2 是纯投影档、
  // 不含 0 0 0 1px 环，与 border 共存合法，用例语义原样保留。
  ["第 8 步批次 X 回归·反向：同一宿主写 var(--shadow-2) 是合法收敛，不该变红（退役管的是「从不渲染的离刻度声明」，不是禁止投影；选 shadow-2 而非 shadow-1 是因为这条规则同规则带 border，见上）", () => mutate("src/styles.css", ".stats-card {\n  border: 1px solid var(--paper-line);", ".stats-card {\n  box-shadow: var(--shadow-2);\n  border: 1px solid var(--paper-line);"), null],
  // 第 8 步批次 Y：创作流页面族 15 处活圆角归 §5.2 刻度（styles.css 35→21、editorial 26→25）。
  // 正向证明这两页的圆角预算确实降到了新值（写回一条离刻度必须红），反向证明刻度写法仍合法。
  ["第 8 步批次 Y 回归：把迁移掉的离刻度圆角写回 .creation-wizard（editorial 预算已降到新值，多一处必须红——否则迁移等于给这一页松了绑）", () => mutate("src/styles/editorial-studio.css", ".creation-wizard {\n  border-radius: var(--radius-3);", ".creation-wizard {\n  border-radius: 16px;"), /圆角刻度/],
  ["第 8 步批次 Y 回归·反向：同一宿主写 var(--radius-3) 是合法收敛，不该变红", () => mutate("src/styles/editorial-studio.css", ".creation-wizard {\n  border-radius: var(--radius-3);", ".creation-wizard {\n  border-radius: var(--radius-2);"), null],
  // 第 8 步批次 Z：桌面壳/导航族 9 处活圆角归刻度（styles.css 21→15、editorial 25→22）。
  ["第 8 步批次 Z 回归：把迁移掉的离刻度圆角写回 .desktop-nav button（styles.css 圆角预算已降到新值，多一处必须红）", () => mutate("src/styles.css", "\n.desktop-nav button {\n  display: grid;", "\n.desktop-nav button {\n  border-radius: 15px;\n  display: grid;"), /圆角刻度/],
  ["第 8 步批次 Z 回归·反向：同一宿主写 var(--radius-1) 是合法收敛，不该变红", () => mutate("src/styles.css", "\n.desktop-nav button {\n  display: grid;", "\n.desktop-nav button {\n  border-radius: var(--radius-1);\n  display: grid;"), null],
  // 第 8 步批次 Z3：卡片柜/大纲行/统计/背景详情 14 处活圆角归刻度（styles.css 15→1，只剩 @apply）。
  ["第 8 步批次 Z3 回归：把迁移掉的离刻度圆角写回 .cards-search（styles.css 圆角预算已降到只剩 @apply 那一条，多一处必须红）", () => mutate("src/styles.css", ".cards-search {\n  display: inline-flex;", ".cards-search {\n  border-radius: 9px;\n  display: inline-flex;"), /圆角刻度/],
  ["第 8 步批次 Z3 回归·反向：同一宿主写 var(--radius-2) 是合法收敛，不该变红", () => mutate("src/styles.css", ".cards-search {\n  display: inline-flex;", ".cards-search {\n  border-radius: var(--radius-1);\n  display: inline-flex;"), null],
  // 第 8 步批次 AA：工作台控件/输入/标签族 13 处活圆角归刻度，styles.css 圆角债清零。
  ["第 8 步批次 AA 回归：把迁移掉的离刻度圆角写回 .cards-input（editorial 预算已降到 10，多一处必须红）", () => mutate("src/styles/editorial-studio.css", ".cards-input {\n  border-color: var(--border-strong);", ".cards-input {\n  border-radius: 8px;\n  border-color: var(--border-strong);"), /圆角刻度/],
  ["第 8 步批次 AA 回归·反向：styles.css 侧 .paper-input 写成显式 rounded-[6px] 同为刻度写法，不该变红（这条 @apply 刚从压死声明转为胜者，别把合法写法一起锁死）", () => mutate("src/styles.css", "@apply rounded-md border border-paper-line", "@apply rounded-[6px] border border-paper-line"), null],
  // 第 8 步批次 AB：editorial-studio.css 容器/卡片/装饰族最后 10 处归刻度，
  // 两份主题样式表的圆角预算条目双双删除 —— 从此这两页任何离刻度圆角都是新增债，必须红。
  ["第 8 步批次 AB 回归：往已清零的 .project-workbench 写回离刻度圆角必须红（该文件预算条目已删除，零容忍生效）", () => mutate("src/styles/editorial-studio.css", ".project-workbench {\n", ".project-workbench {\n  border-radius: 12px;\n"), /圆角刻度/],
  ["第 8 步批次 AB 回归·反向：同一宿主写 var(--radius-3) 不该变红（清零 ≠ 禁止圆角）", () => mutate("src/styles/editorial-studio.css", ".project-workbench {\n", ".project-workbench {\n  border-radius: var(--radius-3);\n"), null],
  // 第 8 步批次 AC：对话框/浮层/toast 档 7 条投影归 --shadow-3/--shadow-4（styles.css 29→26、
  // editorial 29→26、幻影账 9→8）。写回旧手抄值必须红，同宿主写刻度令牌不该红。
  ["第 8 步批次 AC 回归：把退役的手抄对话框投影写回 .creation-wizard（editorial 阴影预算已降到 26，多一处必须红）", () => mutate("src/styles/editorial-studio.css", ".creation-wizard {\n  border-radius: var(--radius-3);", ".creation-wizard {\n  border-radius: var(--radius-3);\n  box-shadow: 0 40px 120px rgba(0, 0, 0, 0.34), 0 6px 18px rgba(0, 0, 0, 0.14);"), /阴影刻度/],
  ["第 8 步批次 AC 回归·反向：同一宿主写 var(--shadow-4) 不该变红（刻度内换档是合法调整）", () => mutate("src/styles/editorial-studio.css", ".creation-wizard {\n  border-radius: var(--radius-3);", ".creation-wizard {\n  border-radius: var(--radius-3);\n  box-shadow: var(--shadow-4);"), null],
  // 第 8 步批次 AD：判据修正，不动一行 CSS（两张胜者表逐字节相同）。
  // §2.3 的描边式焦点环（0 0 0 Npx，偏移/模糊全 0）不是 §5.3 的投影层级；
  // CSS 侧此前漏了这条豁免，TSX 侧的 var(--focus-*) 早就豁免了——两侧不对称。
  ["第 8 步批次 AD·账本仍咬得住：删掉滑块拇指那条真投影（该文件唯一剩下的债，预算已降到 1），账本变小必须红——豁免环不等于对真投影放手", () => mutate("src/features/settings/settings-controls.css", "  box-shadow: 0 1px 4px rgba(15, 20, 28, 0.22);\n  transition: transform 120ms ease;", "  transition: transform 120ms ease;"), /阴影刻度/],
  ["第 8 步批次 AD·反向：描边环换另一种合法写法（color-mix 上色）不该变红——豁免看的是几何形状，不是颜色来源", () => mutate("src/features/creation/history/history-local.css", "  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent-spine) 16%, transparent);", "  box-shadow: 0 0 0 3px var(--action-tint);"), null],
  ["第 8 步批次 AD·逃生舱：环与真投影混排必须红——豁免只认「所有非 inset 层都是环」，别借环的名义夹带投影", () => mutate("src/features/settings/settings-controls.css", ".paper-stepper:focus-within {\n  border-color: var(--action-primary);\n  box-shadow: 0 0 0 3px var(--action-tint);", ".paper-stepper:focus-within {\n  border-color: var(--action-primary);\n  box-shadow: 0 0 0 3px var(--action-tint), 0 12px 34px rgba(34, 38, 48, 0.06);"), /阴影刻度/],
  ["第 8 步批次 AD·幻影写法仍硬管：Tailwind 方括号形式 shadow-[0_0_0_…] 不豁免（批次 K 实测只产 --tw-shadow-color、画不出来），加进 @apply 必须红", () => mutate("src/styles.css", ".paper-chip {\n    @apply inline-flex items-center rounded-full", ".paper-chip {\n    @apply focus:shadow-[0_0_0_3px_rgba(138,90,43,0.08)] inline-flex items-center rounded-full"), /阴影刻度/],
  ["第 8 步批次 AD·反向：同一族描边环写成属性形式 [box-shadow:0_0_0_…]（真的发射 box-shadow）不该变红——两侧对称才算修完（宿主批次 AO 起改为分区卡：它已经归入 --shadow-1，再加一条描边环属 §2.3 机制豁免，同时验证 hairline 判据不把描边环当 border）", () => mutate("src/features/settings/sections/SectionWrapper.tsx", "bg-paper-panel p-4 [box-shadow:var(--shadow-1)]", "bg-paper-panel p-4 [box-shadow:var(--shadow-1)] focus-within:[box-shadow:0_0_0_3px_rgba(138,90,43,0.08)]"), null],
  // 第 8 步批次 AE：4 条工作台级容器归 --shadow-1，同规则的 border 必须一起删——
  // --shadow-1 自带 0 0 0 1px var(--separator-subtle) 的四周 hairline，而 --border-subtle
  // 是它的别名（styles.css:18）。两条同色边并排 = 2px 双线。新立的 hairline 不变量盯住这点。
  ["第 8 步批次 AE 回归：把退役的手抄投影写回 .project-workbench（editorial 阴影预算已降到 22，多一处必须红）", () => mutate("src/styles/editorial-studio.css", "  background: var(--bg-surface);\n  box-shadow: var(--shadow-1);\n}\n\n.project-nav {", "  background: var(--bg-surface);\n  box-shadow: 0 1px 2px rgba(34, 38, 48, 0.04), 0 12px 34px rgba(34, 38, 48, 0.06);\n}\n\n.project-nav {"), /阴影刻度/],
  ["第 8 步批次 AE·hairline 不变量：给已归 var(--shadow-1) 的 .project-workbench 加回 border: 1px solid var(--border-subtle) 必须红（同色并排拼成 2px 双线边）", () => mutate("src/styles/editorial-studio.css", ".project-workbench {\n  display: grid;\n", ".project-workbench {\n  border: 1px solid var(--border-subtle);\n  display: grid;\n"), /hairline 双拼/],
  ["第 8 步批次 AE·hairline 不变量补侧向：只写 border-top 也红——环在四条边都在，任何一条边再叠 border 同样翻倍，别把侧描边当逃生舱", () => mutate("src/styles/editorial-studio.css", ".desktop-inspiration-page {\n  gap: 0;\n", ".desktop-inspiration-page {\n  border-top: 1px solid var(--border-subtle);\n  gap: 0;\n"), /hairline 双拼/],
  ["第 8 步批次 AE·hairline 反向：border-color 不建几何（宽度为 0 时它是哑的），给同一 var(--shadow-1) 规则加 border-color 不该红——判据不能误伤合法上色", () => mutate("src/styles/editorial-studio.css", ".desktop-inspiration-page {\n  gap: 0;\n", ".desktop-inspiration-page {\n  border-color: var(--copper);\n  gap: 0;\n"), null],
  ["第 8 步批次 AE·反向：同一宿主把 var(--shadow-1) 换成 var(--shadow-2) 不该红——刻度内换档是合法调整，shadow-2 不含 hairline 层所以没有双拼问题", () => mutate("src/styles/editorial-studio.css", "  background: var(--bg-surface);\n  box-shadow: var(--shadow-1);\n}\n\n.project-nav {", "  background: var(--bg-surface);\n  box-shadow: var(--shadow-2);\n}\n\n.project-nav {"), null],
  // 第 8 步批次 AJ：面板家族里 .desktop-panel-card / .desktop-library-panel 归 --shadow-1，
  // 三处边框来源（styles.css 的 1px solid、深色大投影盖掉浅环后补的 border、灵感页挂的
  // border/shadow-paper 工具类）同批处理。下面三条分别测这三个来源各自的账有没有咬合。
  ["第 8 步批次 AJ 回归：把退役的手抄投影写回新家族半区（editorial 预算仍是 16——拆出去的那条留着手抄债——多一处必须红）", () => mutate("src/styles/editorial-studio.css", "  box-shadow: var(--shadow-1) !important;", "  box-shadow: 0 1px 2px rgba(15, 20, 28, 0.04) !important;"), /阴影刻度/],
  ["第 8 步批次 AJ·hairline：给归入 var(--shadow-1) !important 的新家族规则加回同规则 border——AE 的 hairline 不变量对 !important 版本同样成立，必须红", () => mutate("src/styles/editorial-studio.css", "  box-shadow: var(--shadow-1) !important;", "  border: 1px solid var(--border-subtle);\n  box-shadow: var(--shadow-1) !important;"), /hairline 双拼/],
  ["第 8 步批次 AJ·TSX 侧还债自检：把灵感页那条 shadow-paper 工具类写回（InspirationPage 预算条目已随本批移除，出现 1 处必须红——否则这次退役等于给该页松了绑）", () => mutate("src/features/inspiration/InspirationPage.tsx", '"desktop-panel-card motion-panel bg-paper-panel p-6"', '"desktop-panel-card motion-panel border border-paper-line bg-paper-panel p-6 shadow-paper"'), /阴影刻度/],
  // 第 8 步批次 AK：styles.css 退役 4 处（1 处层叠压死的投影 + 3 条幻影规则整删）。
  // 这三条测的是「退役有没有顺手把 styles.css 的咬合力一起删掉」，并证明合法写法不误伤。
  ["第 8 步批次 AK 回归：把被压死的手抄投影写回 .desktop-sidebar-card（styles.css 阴影预算已随退役批次下调，多一处必须红——否则退役等于给该文件松绑）", () => mutate("src/styles.css", ".desktop-sidebar-card {\n  border: 1px solid color-mix(in srgb, var(--paper-line) 88%, transparent);", ".desktop-sidebar-card {\n  box-shadow: 0 12px 30px rgba(59, 39, 24, 0.055);\n  border: 1px solid color-mix(in srgb, var(--paper-line) 88%, transparent);"), /阴影刻度/],
  ["第 8 步批次 AK 回归②：把整条删掉的幻影 .reader-glassbar 原样复活（宿主依旧查无——它名下的 shadow-paper 债回来了，棘轮必须咬住）", () => mutate("src/styles.css", "  .motion-notice {\n    animation: paper-soft-in 180ms ease both;\n  }", "  .motion-notice {\n    animation: paper-soft-in 180ms ease both;\n  }\n\n  .reader-glassbar {\n    @apply border border-paper-line bg-paper-panel/86 shadow-paper backdrop-blur-md;\n  }"), /阴影刻度/],
  // 反向锚点原先与正向同宿主 .desktop-sidebar-card：批次 AQ 之后该分支的几何真源归
  // editorial（radius 0 / shadow none），往 styles.css 写 shadow-2 会被「主题 × 主题」
  // 分叉判据拦下——反向用例的意义是「合法写法不该红」，宿主不能再用了。挪到
  // .creation-template-card：宿主真实、几何只有 styles.css 这一个主人、同规则带 1px border。
  ["第 8 步批次 AK·反向：给 .creation-template-card 写 var(--shadow-2) 不该红——刻度内换档是合法收敛，且 shadow-2 是纯投影档、与这条规则保留的 border 共存合法（AE 的 hairline 只管 shadow-1 的环）", () => mutate("src/styles.css", ".creation-template-card {\n  display: grid;", ".creation-template-card {\n  box-shadow: var(--shadow-2);\n  display: grid;"), null],
  // 第 8 步批次 AL：styles.css 阅读器外壳族里两条「层叠压死」的 @apply 投影退役
  // （.paper-panel 的 shadow-lift、.paper-topbar 的 shadow-[0_1px_0_...]），border/background 都留着。
  // 这两条测的是退役后该文件的咬合力还在，第三条测 @apply 侧合法收敛写法不被误伤。
  ["第 8 步批次 AL 回归：把退役的 shadow-lift 写回 .paper-panel（它一直被家族那条 !important 压着、从未渲染，但债是真的——写回来必须红）", () => mutate("src/styles.css", "@apply border border-paper-line bg-paper-panel;", "@apply border border-paper-line bg-paper-panel shadow-lift;"), /阴影刻度/],
  ["第 8 步批次 AL 回归②：把退役的任意值投影写回 .paper-topbar（同上，被两条 box-shadow: none 压死，但棘轮必须认这笔债）", () => mutate("src/styles.css", "@apply border-b border-paper-line bg-paper-panel/90 backdrop-blur-xl;", "@apply border-b border-paper-line bg-paper-panel/90 shadow-[0_1px_0_rgba(255,255,255,0.45)] backdrop-blur-xl;"), /阴影刻度/],
  ["第 8 步批次 AL·反向：@apply 用属性形式 [box-shadow:var(--shadow-2)] 是**能真发射**的合法收敛写法，不该红——批次 K 钉的正是「方括号工具类只产 --tw-shadow-color、属性形式才产 box-shadow」这条区别，判据不能把自己的正解也咬掉", () => mutate("src/styles.css", "@apply border border-paper-line bg-paper-panel;", "@apply border border-paper-line bg-paper-panel [box-shadow:var(--shadow-2)];"), null],
  // 第 8 步批次 AM：四个浮层宿主（ToastCenter / ConfirmDialog / ai-send-confirm / OutlineTree 的
  // 重命名弹窗）手上的 shadow-paper 一直被家族 .motion-dialog / .motion-toast 的
  // var(--shadow-3) / var(--shadow-4) !important 压着——jsdom 读真实产物 computed 值实测
  // 带与不带同值，属零视觉退役，但债是真的，所以写回来必须红。第四条测 AE 的边界：
  // 双拼规矩只管 --shadow-1 的 hairline 环，--shadow-3 是纯投影档，同规则配 border 合法。
  ["第 8 步批次 AM 回归：把退役的 shadow-paper 写回 ToastCenter（motion-toast 预算条目已随退役移除，出现一处必须红）", () => mutate("src/components/interaction.tsx", "motion-toast pointer-events-auto rounded-[var(--radius-3)] border p-3 ${toastClass[toast.tone]}", "motion-toast pointer-events-auto rounded-[var(--radius-3)] border p-3 shadow-paper ${toastClass[toast.tone]}"), /阴影刻度/],
  ["第 8 步批次 AM 回归②：把退役的 shadow-paper 写回 AI 发送确认弹窗（同上，条目已移除）", () => mutate("src/features/creation/inbox/ai-send-confirm.tsx", '"motion-dialog w-[min(560px,100%)] rounded-[var(--radius-3)] border border-paper-line bg-paper-panel p-5"', '"motion-dialog w-[min(560px,100%)] rounded-[var(--radius-3)] border border-paper-line bg-paper-panel p-5 shadow-paper"'), /阴影刻度/],
  ["第 8 步批次 AM 回归③：把退役的 shadow-paper 写回大纲重命名弹窗（同上，条目已移除——三份宿主各自记账，缺一个就少一个锚）", () => mutate("src/features/creation/outline/OutlineTree.tsx", 'motion-dialog w-[min(420px,100%)] overflow-hidden rounded-[var(--radius-3)] border border-paper-line bg-paper-panel"', 'motion-dialog w-[min(420px,100%)] overflow-hidden rounded-[var(--radius-3)] border border-paper-line bg-paper-panel shadow-paper"'), /阴影刻度/],
  ["第 8 步批次 AM·反向：给 .motion-dialog 那条 var(--shadow-3) !important 加同规则 border 不该红——AE 的 hairline 双拼只管 shadow-1 的 0 0 0 1px 环，shadow-3 是纯投影档、对话框带边框是正常设计", () => mutate("src/styles/editorial-studio.css", ".motion-dialog,\n.confirm-dialog {\n  box-shadow: var(--shadow-3) !important;", ".motion-dialog,\n.confirm-dialog {\n  border: 1px solid var(--border-subtle);\n  box-shadow: var(--shadow-3) !important;"), null],
  // 第 8 步批次 AN：四处**真按 §5.3 归级**（Dialog 的 shadow-2xl→--shadow-3，FontPicker /
  // SettingsSearch / LibraryPage 的下拉 shadow-lift|shadow-paper→--shadow-2），一律用属性形式。
  // 下面测两件事：归级后原工具类写回来必须红（账还咬着），以及刻度外档清零后
  // 属性形式确实发射（写回 Tailwind 刻度外档不该红，但幻影写法必须红——见批次 K 那颗牙）。
  ["第 8 步批次 AN 回归：把 Tailwind 刻度外档 shadow-2xl 写回 Dialog（条目已随归级移除，出现一处必须红）", () => mutate("src/components/ui/Dialog.tsx", "bg-paper-panel [box-shadow:var(--shadow-3)]", "bg-paper-panel shadow-2xl"), /阴影刻度/],
  ["第 8 步批次 AN 回归②：把 shadow-lift 写回 FontPicker 的下拉（同上，条目已移除）", () => mutate("src/components/ui/FontPicker.tsx", "bg-paper-panel py-1 [box-shadow:var(--shadow-2)]", "bg-paper-panel py-1 shadow-lift"), /阴影刻度/],
  ["第 8 步批次 AN·幻影在归级现场同样成立：把 --shadow-2 写成方括号工具类 shadow-[var(--shadow-2)] 必须硬红——产物实测只产 --tw-shadow-color、画不出阴影，归级写成它等于假还债", () => mutate("src/components/ui/FontPicker.tsx", "bg-paper-panel py-1 [box-shadow:var(--shadow-2)]", "bg-paper-panel py-1 shadow-[var(--shadow-2)]"), /幻影/],
  // 第 8 步批次 AO：静止分区卡归 --shadow-1、同串 border 一起撤，并把 AE 的 hairline 判据
  // 铺到 TSX 侧（CSS 侧原本管不到 className 字符串——那半边正是「收敛成令牌」会让棘轮计数
  // 下降、双线边照样画出来的裂缝）。三条牙：退役的 border 写回来必须红；判据的三种豁免
  // 不能误伤（border-0 哑的、border-<颜色> 只上色、hover: 变体前缀不判）；反向证明
  // 「裸 border + shadow-2」这种合法共存不被咬——判据只盯 shadow-1 的环。
  ["第 8 步批次 AO 回归：把撤掉的 border border-paper-line 写回归入 --shadow-1 的分区卡——TSX 侧 hairline 判据必须咬住（CSS 侧的同判据早在批次 AE 立起，本批补的是它管不到的那半边）", () => mutate("src/features/settings/sections/SectionWrapper.tsx", "rounded-[var(--radius-2)] bg-paper-panel p-4 [box-shadow:var(--shadow-1)]", "rounded-[var(--radius-2)] border border-paper-line bg-paper-panel p-4 [box-shadow:var(--shadow-1)]"), /hairline 双拼/],
  ["第 8 步批次 AO·豁免①：border-0 / border-y-0 是宽度归零的哑类，写进同一串不该红——阅读器那族 ShellPanel 就靠它压掉组件层边框，误判等于逼人改回双线边", () => mutate("src/features/settings/sections/SectionWrapper.tsx", "rounded-[var(--radius-2)] bg-paper-panel p-4 [box-shadow:var(--shadow-1)]", "rounded-[var(--radius-2)] border-0 border-y-0 bg-paper-panel p-4 [box-shadow:var(--shadow-1)]"), null],
  ["第 8 步批次 AO·豁免②：border-[color:…] 只给已有的线上色、不建几何（与 CSS 侧只放过 border-color 同形），不该红", () => mutate("src/features/settings/sections/SectionWrapper.tsx", "rounded-[var(--radius-2)] bg-paper-panel p-4 [box-shadow:var(--shadow-1)]", "rounded-[var(--radius-2)] border-[color:var(--proof-mark)] bg-paper-panel p-4 [box-shadow:var(--shadow-1)]"), null],
  ["第 8 步批次 AO·豁免③：hover:border-2 带状态变体前缀，判它需要「哪个状态下同时生效」的映射，本判据保守不判——不该红（宁可漏判不误删）", () => mutate("src/features/settings/sections/SectionWrapper.tsx", "rounded-[var(--radius-2)] bg-paper-panel p-4 [box-shadow:var(--shadow-1)]", "rounded-[var(--radius-2)] hover:border-2 bg-paper-panel p-4 [box-shadow:var(--shadow-1)]"), null],
  ["第 8 步批次 AO·反向：同一串写 --shadow-2 加裸 border 不该红——shadow-2 是纯投影档不含环，边框仍需宿主供值；判据不能把 §5.3 另外三档的正常写法一起咬掉", () => mutate("src/features/settings/sections/SectionWrapper.tsx", "rounded-[var(--radius-2)] bg-paper-panel p-4 [box-shadow:var(--shadow-1)]", "rounded-[var(--radius-2)] border bg-paper-panel p-4 [box-shadow:var(--shadow-2)]"), null],
  // 第 8 步批次 AP：药丸 Tabs 的投影按 §2.5 整条不画（不是换令牌——§2.5 判据连
  // var(--shadow-*) 都不豁免，而本仓 §2.5 只认 CSS 选择器里的按钮角色，TSX 侧按钮
  // 投影靠阴影棘轮按文件记账）。两条牙：写回来必须红；反向证明「无投影」是终态，
  // 补个 shadow-none 之类的豁免写法也不该红——判据不该逼人把阴影换成阴影的否定式。
  ["第 8 步批次 AP 回归：把药丸选中态那条 shadow-sm 写回（Tabs 预算条目已随 §2.5 还债移除，出现一处必须红）", () => mutate("src/components/ui/Tabs.tsx", '"bg-paper-panel text-copper font-semibold"', '"bg-paper-panel text-copper shadow-sm font-semibold"'), /阴影刻度/],
  ["第 8 步批次 AP·反向：药丸写 shadow-none 不该红（棘轮本就豁免 none，§2.5 要的是「没有投影层级」而不是「不许出现 shadow 这个词」）", () => mutate("src/components/ui/Tabs.tsx", '"bg-paper-panel text-copper font-semibold"', '"bg-paper-panel text-copper shadow-none font-semibold"'), null],
  // 第 8 步批次 AF：§2.5「按钮一律无阴影」补进判据。阴影棘轮豁免 var(--shadow-*)，
  // 于是「把按钮手抄投影收敛成令牌」会让棘轮计数下降、守卫全绿，按钮却照样画着投影——
  // 判据自己看不见这类「把违规写得像合规」。§2.5 判据不豁免令牌，专门堵它。
  ["第 8 步批次 AF 回归：把退役的导航按钮投影写回 .desktop-nav button.active（styles.css 阴影预算已降到 21，§2.5 判据也必须咬住按钮角色）", () => mutate("src/styles.css", ".desktop-nav button.active {\n  border-color: color-mix(in srgb, var(--copper) 24%, var(--paper-line));", ".desktop-nav button.active {\n  box-shadow: 0 10px 24px rgba(74, 48, 26, 0.08);\n  border-color: color-mix(in srgb, var(--copper) 24%, var(--paper-line));"), /按钮一律无阴影/],
  ["第 8 步批次 AF·裂缝本体：按钮写 var(--shadow-1) 棘轮会豁免（计数降、看着像还了债），§2.5 判据必须不豁免令牌并红——这正是本批判据要堵的水下裂缝", () => mutate("src/styles.css", ".desktop-nav button.active {\n  border-color: color-mix(in srgb, var(--copper) 24%, var(--paper-line));", ".desktop-nav button.active {\n  box-shadow: var(--shadow-1);\n  border-color: color-mix(in srgb, var(--copper) 24%, var(--paper-line));"), /按钮一律无阴影/],
  ["第 8 步批次 AF·反向：按钮上的纯描边焦点环 0 0 0 Npx 不该红（§2.3 的 focus-visible 机制，批次 AD 的豁免在本判据内同样成立）", () => mutate("src/styles.css", ".desktop-nav button.active {\n  border-color: color-mix(in srgb, var(--copper) 24%, var(--paper-line));", ".desktop-nav button.active {\n  box-shadow: 0 0 0 2px var(--action-tint);\n  border-color: color-mix(in srgb, var(--copper) 24%, var(--paper-line));"), null],
  ["第 8 步批次 AF·反向：按钮上的纯 inset 色条不该红（与 .desktop-inspiration-card.active 那条 inset 3px 色条同族的结构装饰，§5.3 四级本就不针对它；批次 AH 删掉 .creation-spine 后换钉仍活着的这族）", () => mutate("src/styles.css", ".desktop-nav button.active {\n  border-color: color-mix(in srgb, var(--copper) 24%, var(--paper-line));", ".desktop-nav button.active {\n  box-shadow: inset 2px 0 0 var(--copper);\n  border-color: color-mix(in srgb, var(--copper) 24%, var(--paper-line));"), null],
  ["第 8 步批次 AF·冻结自检：删掉 .desktop-page-actions 族里那条 hover 投影却不降 FROZEN_BUTTON_SHADOW_COUNT，必须红——冻结名单也是账，悄悄松绑比漏检更坏", () => mutate("src/styles/editorial-studio.css", "  box-shadow: 0 5px 14px color-mix(in srgb, var(--accent-spine) 28%, transparent);\n", ""), /冻结计数对不上/],
  // 第 8 步批次 AG：§2.5 的「类名型按钮」透镜。AF 只认裸 button 元素型选择器，
  // 而本仓有一族按钮靠类名被 CSS 命中（选择器里没有 button 这个词）。
  // 关键设计：角色是登记出来的数据，不从挂载元素猜——因为「整卡可点」也实现成 <button>
  // （BackgroundPage / CardListSidebar），那是卡片、§5.3 明确给它留了静止阴影档；
  // 拿「只挂在 button 上」当判据去删它的阴影，就是一次无声的界面改版。
  ["第 8 步批次 AG 回归：把手抄投影写回类名型控件按钮 .project-nav-back（元素型判据看不见它，必须靠类名透镜咬住）", () => mutate("src/styles/editorial-studio.css", ".project-nav-back {\n  margin: 0 0 10px;\n  padding-left: 10px !important;\n  border: 1px solid var(--border-subtle) !important;\n  border-left: 1px solid var(--border-subtle) !important;\n  border-radius: var(--radius-1) !important;\n  background: var(--bg-surface) !important;\n}", ".project-nav-back {\n  margin: 0 0 10px;\n  padding-left: 10px !important;\n  border: 1px solid var(--border-subtle) !important;\n  border-left: 1px solid var(--border-subtle) !important;\n  border-radius: var(--radius-1) !important;\n  background: var(--bg-surface) !important;\n  box-shadow: 0 1px 2px rgba(34, 38, 48, 0.05);\n}"), /按钮一律无阴影/],
  ["第 8 步批次 AG·裂缝在类名侧同样成立：控件按钮写 var(--shadow-1) 棘轮豁免（计数不动），§2.5 必须不豁免令牌并红", () => mutate("src/styles/editorial-studio.css", ".project-nav-back {\n  margin: 0 0 10px;\n  padding-left: 10px !important;\n  border: 1px solid var(--border-subtle) !important;\n  border-left: 1px solid var(--border-subtle) !important;\n  border-radius: var(--radius-1) !important;\n  background: var(--bg-surface) !important;\n}", ".project-nav-back {\n  margin: 0 0 10px;\n  padding-left: 10px !important;\n  border: 1px solid var(--border-subtle) !important;\n  border-left: 1px solid var(--border-subtle) !important;\n  border-radius: var(--radius-1) !important;\n  background: var(--bg-surface) !important;\n  box-shadow: var(--shadow-1);\n}"), /按钮一律无阴影/],
  ["第 8 步批次 AG·台账正向：卡片角色（background-card 是「整卡可点」的 <button>）写 var(--shadow-1) 不该红——§5.3 给静止卡片留了档，误删就是无声改版", () => mutate("src/styles/editorial-studio.css", "/* Buttons are physical: a lit crown, a pressed state. */", ".background-card {\n  box-shadow: var(--shadow-1);\n}\n\n/* Buttons are physical: a lit crown, a pressed state. */"), null],
  ["第 8 步批次 AG·台账的牙：新出现的纯按钮类名（cards-icon-btn）带投影却不在控件/卡片任一台账里，必须红——判据不替人猜角色", () => mutate("src/styles/editorial-studio.css", "/* Buttons are physical: a lit crown, a pressed state. */", ".cards-icon-btn {\n  box-shadow: var(--shadow-1);\n}\n\n/* Buttons are physical: a lit crown, a pressed state. */"), /角色未登记/],
  ["第 8 步批次 AG·透镜退化自检：把登记的控件类名挪到非按钮元素（挂载扫描不再认它为纯按钮类名），必须红——危险方向是静默放行", () => mutate("src/components/layout/DesktopFrame.tsx", '<RingButton className="desktop-search-command"', '<span className="desktop-search-command"'), /类名透镜退化/],
  ["第 8 步批次 AG·反向：控件按钮上写纯描边焦点环不该红（§2.3 机制，元素型与类名型两侧豁免必须对称）", () => mutate("src/styles/editorial-studio.css", ".project-nav-back {\n  margin: 0 0 10px;\n  padding-left: 10px !important;\n  border: 1px solid var(--border-subtle) !important;\n  border-left: 1px solid var(--border-subtle) !important;\n  border-radius: var(--radius-1) !important;\n  background: var(--bg-surface) !important;\n}", ".project-nav-back {\n  margin: 0 0 10px;\n  padding-left: 10px !important;\n  border: 1px solid var(--border-subtle) !important;\n  border-left: 1px solid var(--border-subtle) !important;\n  border-radius: var(--radius-1) !important;\n  background: var(--bg-surface) !important;\n  box-shadow: 0 0 0 3px var(--action-tint);\n}"), null]
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
