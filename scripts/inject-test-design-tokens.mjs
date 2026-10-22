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
  // 批次 BD-22 的 MEASURE 牙改这几份：行长 42em 是三份文件几组声明的合力（正文 cap+内边距、
  // 纸壳+边框补偿、通读页纸面+滚动条补偿），任何一处单独漂移都要当场红。
  "src/features/creation/preview/preview-local.css",
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
  // ✔ 批次 BD-19：ErrorBoundary 那笔（shadow-paper）按 §5.3 归成 [box-shadow:var(--shadow-3)]，
  // TSX 侧自此无债、条目移除——「已有预算文件里加新债」那颗牙没有 TSX 宿主可用了，
  // 于是①／还债不降预算两颗改钉 CSS 侧（LibraryPage 的浮层串、夜读皮肤串，两者都在
  // 备份清单里），K 幻影／K 字面量两颗留在 ErrorBoundary 的新串上（幻影判据与预算无关，
  // 字面量那颗正好测「空表=新增即红」）。
  "src/features/settings/sections/SectionWrapper.tsx",
  "src/components/ErrorBoundary.tsx",
  // 批次 AP 的还债用例改这份：药丸 Tabs 那条 shadow-sm 按 §2.5 整条不画了，条目随之移除
  // ——写回来必须红。⚠ 本仓 §2.5 判据只认 CSS 选择器里的按钮角色，TSX 侧的按钮投影
  // 由阴影棘轮单独盯（预算条目 0），这颗牙测的就是那条账还咬着。
  "src/components/ui/Tabs.tsx",
  // 批次 AN 的归级用例改这两份：Dialog / FontPicker 手上那条 Tailwind 刻度外档已按 §5.3
  // 换成属性形式令牌、预算条目随之移除——写回原工具类必须红，否则归级等于给这两处松绑。
  "src/components/ui/Dialog.tsx",
  "src/components/ui/FontPicker.tsx",
  // 批次 BD-30 的反向⑤改这块 lazy 表里的同键异值，必须进备份清单：不在清单里 mutate() 直接拒改。
  "src/features/creation/overview/overview-local.css",
  // 批次 BD-29 的发射顺序用例改这份：颜色分支判据的「谁压谁」整个建立在
  // styles.css → editorial-studio.css 这条 import 顺序上，把它换序是这条判据
  // 最根本的失效方向，所以 main.tsx 必须进备份清单（不在清单里的文件 mutate 会直接拒改）。
  "src/app/main.tsx",
  // 批次 BD-41：账清零之后，「还了债不改账」这颗牙只剩一条路可走——直接往账里塞一条
  // 幻影行，看判据认不认。这就要求能改守卫脚本自己，所以它必须进备份清单。
  // ⚠ 备份的是**磁盘上的那份**：电池每个用例都是 spawn 一次新进程读它，改完立刻还原，
  // 不会把故意改坏的内容留在工作区（cleanup 走同一套 restore）。
  "scripts/verify-design-tokens.mjs",
  // 批次 BD-43：属性级挂载覆盖那八颗牙里有两颗改这份表（新债超预算 / 还债不降预算），
  // 它原先不在备份清单里——mutate() 对未备份文件直接拒改，不补上那两颗当场 SKIP 计为无效。
  "src/features/creation/editor/writing-quick-reference.css",
  // 批次 BD-44 的⑨号牙（还债不许悄悄退化）改这份：那条 import cards-local.css 就是
  // 8 条缺席的正解，删掉它必须当场红——债还得掉，牙不能跟着哑。
  "src/features/creation/cards/components/CardDynamicFields.tsx",
  // 批次 BD-77：36px 桶清零后 LibraryPage 的 `pl-9` 成为刻度内的 pl-8，间距棘轮②③ 与
  // BD-46 的 TSX 值漂移三颗铆钉随之失去宿主（SKIP 计为无效）。三颗统一改钉现阶段**唯一**
  // 还挂着离刻度间距债的 TSX 文件 InboxQuickInput.tsx（`pr-14` = 56px；BD-81 判为
  // 浮钮让位槽记档，仍留在值账上，锚点因此长期有效），所以它进备份清单。
  "src/features/creation/inbox/components/InboxQuickInput.tsx"
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

// 「死账」两颗牙的自动选锚 deadAnchor()（批次 BD-34 立、BD-41 退役）——整段删掉的理由记在这里：
// 它挑锚的前提是「账上得有一条现成的债」，于是还债和保牙变成了同一件事：BD-36/37 两批连着
// 把自动锚正在用的那行还掉、它只能自动换人，到 BD-40 账上只剩 3710 一条时，那颗牙已经把这条
// 债锁成了 permanent 人质——判据本来能清零，却因为两颗哨兵牙而永远不能还最后一笔。这仍然是
// 人在伺候牙。现在 ③④ 各钉一个**永久锚**：③ 往 .project-workbench 注入一条账外新死声明
// （钉规则名 + 在画的 display: grid，值取 167px：既不是胜者 140、也不在八档上），④ 直接往守卫
// 的 DEAD_LEDGER 里塞一条幻影行（守卫脚本因此在 BD-41 进了备份清单，改完即还原）。账空不空都测
// 得出这两个方向，债还清了牙还在。


function runGuard(env) {
  const r = spawnSync(process.execPath, [path.join(root, "scripts", "verify-design-tokens.mjs")], {
    encoding: "utf8",
    env: env ? { ...process.env, ...env } : process.env,
  });
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
  ["少一档尺寸（删 lg）", () => mutate("src/components/ui.tsx", "    lg: \"h-10 px-4 text-sm\"", "    // lg 已删除"), /缺少尺寸/],
  ["去掉 loading 的 aria-busy", () => mutate("src/components/ui.tsx", "aria-busy={loading || undefined}", "data-busy={loading || undefined}"), /loading 态/],
  ["disabled 透明度改成 0.45", () => mutate("src/components/ui.tsx", "disabled:opacity-55", "disabled:opacity-45"), /disabled 透明度/],
  ["danger 改用主操作色", () => mutate("src/components/ui.tsx", "bg-[color:var(--proof-mark)] text-[color:var(--fg-on-solid)]", "bg-copper text-[color:var(--fg-on-solid)]"), /proof-mark/],
  ["data-variant 输出归一后的值（CSS 排除会失效）", () => mutate("src/components/ui.tsx", "data-variant={variant}", "data-variant={resolved}"), /data-variant/],
  // ⚠ 批次 BD-26 退役一颗牙，留字在此，别让下一批人以为那条判据坏了：
  // 「CSS 双排除退回单排除」测的是守卫里 `quietExclusions < 4` 那条计数断言，而它数的
  // 四处全部长在 `.desktop-page-actions button:first-child:not(…)` 这一族上。本批按
  // 第 6 步的收口清单把 12 条整族删除，四处随之归零、断言本体也一起删掉了（守卫
  // verify-design-tokens.mjs:259 段的 BD-26 注释记录了删除理由与替代判据）。
  // 拿一条已经不存在的判据当牙，等于给自己发一张「通过」的空头支票——所以是删除，
  // 不是改成「期望不红」：needle 已不在源码里，留着只会变成 SKIP，而 SKIP 计为无效。
  // 同一风险方向（quiet/ghost 首子被套主色）现在由两道别的东西看着：
  //   ① 上面那颗 `data-variant={variant}` 的牙（输出归一值必须红）——排除机制的另一半；
  //   ② 本批新增的「接管规则不许复活」那颗牙：想给首子上主色就得先写回 .desktop-page-actions
  //      button 那条规则，而写回它当场撞 LEGACY 空清单 → 硬红。
  // 也就是说：断言删了，但它挡的那个失败形状没有变得无人看管。
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
  // ✔ 批次 BD-26：这颗牙随宿主规则一起退役（上一行是批次 AI 的搬家记录，一并留在原位可读）。
  //   它测的形状是「接管规则存在、但 :not 排除被削弱」；本批把那 12 条整族删除后，这个形状
  //   在本仓已无实例可钉——留着只会 SKIP（needle 不在源码里），而 SKIP 计为无效、不是通过。
  //   它守的方向由下面 BD-26·复活① 接替，而且接替后更严：旧牙只挡「排除写坏」，新牙直接挡
  //   「规则本身写回来」。
  ["base 加回 px-3（会被 Tailwind 发射顺序压掉尺寸层与 icon 的 p-0）", () => mutate("src/components/ui.tsx", "justify-center gap-2 rounded-[var(--radius-1)] font-medium", "justify-center gap-2 rounded-[var(--radius-1)] px-3 font-medium"), /base 不得包含内边距/],
  ["批次 BD-21 的牙：base 兜回字号（具名档按字典序发在尺寸层任意值档之后，14 会静默赢掉 md 的 13）", () => mutate("src/components/ui.tsx", "justify-center gap-2 rounded-[var(--radius-1)] font-medium", "justify-center gap-2 rounded-[var(--radius-1)] text-sm font-medium"), /base 不得包含字号/],
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
  ["圆角棘轮①：Button base 的圆角旁再挂一处离刻度（批次 AV 起预算表为空，rounded-2xl 一出现就必须红；这条测的是 TSX 侧，棘轮②测 CSS 侧）", () => mutate("src/components/ui.tsx", "justify-center gap-2 rounded-[var(--radius-1)] font-medium", "justify-center gap-2 rounded-[var(--radius-1)] rounded-2xl font-medium"), /圆角刻度/],
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
  // ⚠ 间距棘轮② 的锚点随批次 BD-25 搬家：原 needle 是 ui.tsx sizes.sm 的 px-2.5（=10px），
  //   它曾是「§2.2 要 10px / §5.1 没这档」这场规格自相冲突的现场；用户裁定 §5.1 增补
  //   --sp-12: 10px 之后，px-2.5 合规、ui.tsx 条目从 SPACING_BUDGET 移除——这颗牙的 needle
  //   虽然还在源码里，但「再加一个 py-2.5 就是 2>1」这个算术前提已经塌了（两个都合法 → 0>0 恒假）。
  //   留着它就是一颗永远测不到东西的假牙，比缺牙更坏。改钉仍有活债的 TSX 宿主：
  //   LibraryPage 预算 1 = 搜索框 pl-9(36px)，再加一个 m-7(28px) 就是 2>1，必须红。
  ["间距棘轮②（BD-25 搬家）：TSX 侧新增离刻度工具类（InboxQuickInput 预算 1 = pr-14(56px)，再加一个 m-7(28px) 就是 2>1，必须红）⚠ BD-77 换锚：原钉的 LibraryPage `pl-9` 随 36px 桶清零消失（pl-9→pl-8），改钉现存的最后一处 TSX 离刻度债", () => mutate("src/features/creation/inbox/components/InboxQuickInput.tsx", 'className="paper-input h-8 w-full pr-14 text-xs placeholder:text-paper-muted"', 'className="paper-input h-8 w-full pr-14 m-7 text-xs placeholder:text-paper-muted"'), /间距刻度/],
  // ⚠ 这三颗锁牙刻意只动 tokens.css，不动判据自己：拿 mutate 改 verify-design-tokens.mjs
  //   是自我指涉（被测脚本改的是它自己的合法集），而且它不在备份清单里、改坏了还原不了。
  //   三颗盖住锁的三个失配形状：改值（①）、删档（②）、加野档（③）——
  //   「谁单方面变了都算事故」这句锁词的两半（判据表多一档 / 令牌多一档）各有牙。
  ["间距刻度·双向锁①（BD-25 新牙·只有令牌侧变）：把 --sp-12 的值从 10px 改成 7px——判据表里没有 7，令牌侧多出一档 → 锁必须红。这条守的是「往令牌塞一档野值就等于给全仓散写的 7px 发证」这条路（批次 L 的债从声明搬进定义、判据当场瞎掉）", () => mutate("src/styles/tokens.css", "  --sp-12: 10px;", "  --sp-12: 7px;"), /SP_SCALE_PX 与 tokens\.css/],
  ["间距刻度·双向锁②（BD-25 新牙·少一档）：删掉 --sp-12 定义、判据表里还留着 10 → 锁必须红。这条守的是本批自己刚踩过的顺序问题：先改令牌再改表，漏一处就是 165 处当场全红；反过来说明加档/删档必须两处同改才走得动", () => mutate("src/styles/tokens.css", "  --sp-12: 10px; /* BD-25 裁定档；命名追加、不参与值序 */\n", ""), /SP_SCALE_PX 与 tokens\.css/],
  ["间距刻度·双向锁③（BD-25 新牙·多一档）：往 tokens.css 塞一个判据表里没有的 --sp-13: 7px 必须红。这条守的是另一头的失败：合法集若纯派生自令牌，「往令牌加一档野值」就等于给全仓散写的 7px 发证（批次 L 的「债从声明搬进定义、判据当场瞎掉」）", () => mutate("src/styles/tokens.css", "  --sp-12: 10px;", "  --sp-13: 7px;\n  --sp-12: 10px;"), /SP_SCALE_PX 与 tokens\.css/],
  ["间距棘轮③：还了债却不降预算（InboxQuickInput 预算 1 = pr-14(56px)，改成 pr-12(48px) 是合法收敛但预算没跟着降，必须红并指名该文件）⚠ BD-77 换锚：原钉的 LibraryPage pl-9→pl-8 已随 36px 桶清零成为正式改动", () => mutate("src/features/creation/inbox/components/InboxQuickInput.tsx", 'className="paper-input h-8 w-full pr-14 text-xs placeholder:text-paper-muted"', 'className="paper-input h-8 w-full pr-12 text-xs placeholder:text-paper-muted"'), /预算没跟着降/],
  // 反向三颗（AW 立项）钉的是「别把合法写法咬进去」——判据过严的下一批人就会退回去散写数字，
  // 那才是这本账真正的失败模式。批次 BD-25 的裁定牙另加两颗排在它们后面（④⑤）：
  // 一条裁定落地后必须有「新合法写法实测为绿」的牙，否则判据与设计的分歧只能靠人记住。
  ["间距棘轮·反向①：rem 写法按 16px 基准折算后落在刻度上（padding: 2px 0.375rem = 2px 6px = --sp-1/--sp-3），不该被折算误伤成债", () => mutate("src/features/settings/settings-controls.css", "  padding: 2px 6px;", "  padding: 2px 0.375rem;"), null],
  ["间距棘轮·反向②：引用令牌的写法不在计数之列（padding-inline: var(--sp-6) 是收敛的目标形态，写它必须绿）", () => mutate("src/features/settings/settings-controls.css", "  padding: 2px 6px;", "  padding: 2px 6px;\n  padding-inline: var(--sp-6);"), null],
  ["间距棘轮·反向③：em/%/auto/负值是排版与布局语法不是档位（padding: 1.5em / margin-top: -6px / auto 三行都不该红）", () => mutate("src/features/settings/settings-controls.css", "  padding: 2px 6px;", "  padding: 2px 6px;\n  margin-top: -6px;\n  padding-left: 1.5em;\n  margin-inline: auto;"), null],
  ["间距棘轮·反向④（BD-25 裁定牙）：10px 现在是合法档位——同一条规则里 padding: 2px 10px 与 gap: 10px 两种 CSS 写法都不该红（裁定落地后还咬它，等于判据没跟上设计决定，会把人推回绕过判据）。工具类那一侧由⑤测", () => mutate("src/features/settings/settings-controls.css", "  padding: 2px 6px;", "  padding: 2px 10px;\n  gap: 10px;"), null],
  ["间距棘轮·反向⑤（BD-25 裁定牙）：Tailwind 的 2.5 步进折算成 10px 后同样合法（sizes.sm 的 px-2.5 是这条裁定的原始起因，它必须不再计债）", () => mutate("src/components/ui.tsx", 'sm: "h-7 px-2.5 text-xs"', 'sm: "h-7 px-2.5 py-2.5 text-xs"'), null],
  // 批次 AX 的牙（字号 / 行高比率 / 行高长度式，规格 §3.2）。三本账各自成牙：
  // 同一处改动只能证明一本判据在咬，混在一颗牙里就分不清是哪本瞎了。
  // 立项批的牙必须能造出「超出预算的第 N+1 处」——所以正牙一律落在**该本账预算为 0 的文件**
  // （新增即红），或用**合法收敛不降预算**造 stale 红；两种形状都要有，缺一半就是半个判据。
  ["排版棘轮·字号①：CSS 侧在字号零预算文件里新增一处离刻度 font-size（inbox-local.css 不在 FS_BUDGET 里、视同 0，11px→11.5px 必须红）", () => mutate("src/features/creation/inbox/inbox-local.css", "  font-size: 11px;", "  font-size: 11.5px;"), /第 8 步字号：/],
  ["排版棘轮·字号②：TSX 侧把刻度上的 text-sm(14) 换成 text-lg(18)（LibraryPage 字号预算 0，§3.2 八档里没有 18px，必须红）", () => mutate("src/features/library/LibraryPage.tsx", '<div className="text-sm text-paper-muted">正在加载书籍...</div>', '<div className="text-lg text-paper-muted">正在加载书籍...</div>'), /第 8 步字号：/],
  // ⚠ 批次 BD-26：「排版棘轮·字号③」退役，留字在此。这颗牙的历史就是本账的搬迁史，
  //   所以它的终点也写在搬迁史里：它测的是 stale 分支——「还了债却不降预算」。本批把最后一
  //   笔字号债（editorial-studio.css hero 接管态的 font-size: 11.5px !important）随第 6 步 12 条
  //   容器接管规则整族删除，FS_BUDGET 清空 → 表里没有条目，`budget > actual` 对一个不存在的
  //   预算恒不成立，这条用例从此永远 SKIP（needle 已不在源码里）。与批次 AV 圆角③、AS 幻影
  //   预算、BD-20 行高比率② 同类处理：删除 + 留字，别让下一批人以为那条判据坏了。
  //   没有失守的部分：「新增即红」那一半仍由上面「排版棘轮·字号①／②」守着，空表下任何一处
  //   离刻度字号都是新增债，正牙照咬。
  ["排版棘轮·行高比率①：新增一个八档之外的比率（history-local.css 不在 LH_BUDGET 里视同 0，1.5→1.65 必须红——1.65 正是本仓最常见的手抄多一档。⚠ 批次 BD-4 把这条锚点规则的字号从 11.5px 收到 11px（§3.2 同名配对 11↔1.5），needle 跟着新字号重写，锚的还是同一条 .history-item-retention）", () => mutate("src/features/creation/history/history-local.css", "  font-size: 11px;\n  line-height: 1.5;", "  font-size: 11px;\n  line-height: 1.65;"), /第 8 步行高比率：/],
  // 排版棘轮·行高比率②（还了债却不降预算）随批次 BD-20 退役：稿纸那一对 line-height: 2
  // 是这本账最后 2 处债，BD-20 把字号/行高/节距三件一起重算掉之后 LH_BUDGET 成为空表，
  // `budget > actual` 对一个不存在的预算恒不成立——这条用例永远 SKIP（needle 已经不在了）。
  // 与批次 AV 退役圆角③、AS 退役幻影预算同类，留字在此，别让下一批人以为那条判据坏了。
  // 「新增即红」那一半仍由上一条「行高比率①」守着（它钉的正是零预算文件），没有失守。
  // ⚠ 搬家时如实记一句：这颗牙原本要搬去「styles.css 稿纸那条同名规则的 line-height: 2」，
  // 但那条是**级联已死**的声明（BD-2 已证明、BD-20 已删），拿死声明当锚点等于测一条
  // 永远不会有人再写的字符串；而 LH 侧此刻唯一的活债就是稿纸那一条，还掉即空表。
  // 批次 BD-20 补的三颗「写回必须红」替代它，并且各测一件事：
  ["排版棘轮·稿纸写回①：把稿纸字号从 §3.3 的 16px 写回改前的 17px（editorial-studio.css 字号预算 3，多一处必须红；行高留 1.85 不动，所以咬人的只有字号这本）", () => mutate("src/styles/editorial-studio.css", "  font-size: 16px;\n  line-height: 1.85;", "  font-size: 17px;\n  line-height: 1.85;"), /第 8 步字号：/],
  ["排版棘轮·稿纸写回②：把稿纸行高从 §3.3 的 1.85 写回改前的 2（LH_BUDGET 已空表=新增即红，必须红；字号留 16 在刻度上，所以咬人的只有行高这本——两本判据仍然分开，没合并成一颗牙）", () => mutate("src/styles/editorial-studio.css", "  font-size: 16px;\n  line-height: 1.85;", "  font-size: 16px;\n  line-height: 2;"), /第 8 步行高比率：/],
  ["排版棘轮·稿纸写回③：把 styles.css 那条**级联已死**的 line-height: 2 写回原处（BD-20 删的就是它，同特异度下 editorial 永远赢，所以它既不画像素也不该再占账；写回来必须被行高比率这本当场咬住——否则「删死声明」这条路等于没人守）", () => mutate("src/styles.css", "  caret-color: var(--copper);", "  line-height: 2;\n  caret-color: var(--copper);"), /第 8 步行高比率：/],
  ["排版棘轮·行高长度式①：leading-N 就是带单位的行高（产物实测 .leading-6{line-height:1.5rem}），LibraryPage 该行预算 0，加一个 leading-6 必须红", () => mutate("src/features/library/LibraryPage.tsx", '<div className="text-sm text-paper-muted">正在加载书籍...</div>', '<div className="text-sm leading-6 text-paper-muted">正在加载书籍...</div>'), /第 8 步行高长度式/],
  // 排版棘轮·行高长度式②（还了债却不降预算）随批次 BD-24 退役：立项那 43 处已全部换成
  // leading-[var(--leading-K)]，LHL_BUDGET 成为空表，`budget > actual` 对不存在的预算恒不成立，
  // 而且 needle（ui.tsx 的 `mt-2 text-sm leading-6 text-paper-muted`）本身已经不在源码里。
  // 与批次 AV 圆角、AS 幻影预算、BD-20 行高比率②同规矩，留字在此，别让下一批人以为判据坏了。
  // ⚠ 这一族没有失守，反而在本批**变强**：下面 BD-24 补的 10 颗新牙（7 正向红 + 3 反向绿，
  //   以脚本实测条数为准，不按注释许诺）守的正是「换写法」这条路——
  //   旧 LH_UTILITY 只数 `leading-` 紧跟数字，方括号形态一个都不数，所以只还源码不补判据
  //   等于把 43 处债从看得见搬进看不见（批次 L 记过的失效形态）。牙分两侧：长度式方括号／
  //   自创档位／配对错档／散落字面量／CSS 侧非八档 var 各一颗正向红，合法令牌形态一颗反向绿。
  ["排版棘轮·行高长度式③（BD-24 补盲区）：把带长度单位的任意值 leading-[24px] 写进零预算文件——旧正则只数 leading-N，这条正是当年漏掉的那个口袋，必须红", () => mutate("src/features/library/LibraryPage.tsx", '<div className="text-sm text-paper-muted">正在加载书籍...</div>', '<div className="text-sm leading-[24px] text-paper-muted">正在加载书籍...</div>'), /第 8 步行高长度式/],
  ["排版棘轮·行高长度式④（BD-24 补盲区）：leading-[var(--leading-18)] 里 18 不是八档之一，tokens.css 无定义 → 浏览器静默回退（与 D-2 那族同形态），必须红", () => mutate("src/features/library/LibraryPage.tsx", '<div className="text-sm text-paper-muted">正在加载书籍...</div>', '<div className="text-sm leading-[var(--leading-18)] text-paper-muted">正在加载书籍...</div>'), /不是 §3\.2 的八档之一/],
  ["排版棘轮·行高长度式⑤（BD-24 补盲区）：档位存在但与同行字号不同名——text-sm(14) 挂 var(--leading-12)，正是「只换一半」那种洗法，必须红", () => mutate("src/features/library/LibraryPage.tsx", '<div className="text-sm text-paper-muted">正在加载书籍...</div>', '<div className="text-sm leading-[var(--leading-12)] text-paper-muted">正在加载书籍...</div>'), /与同行字号[\s\S]*?不同名|第 8 步行高长度式/],
  ["排版棘轮·行高长度式⑥（BD-24 补盲区）：leading-[1.7] 是无单位散写值——§3.2 禁的是「带单位」，但八档同名值早已在令牌里，放它过去等于给「不走令牌的散落比率」开后门（与②那本无单位账不重复计数），必须红", () => mutate("src/features/library/LibraryPage.tsx", '<div className="text-sm text-paper-muted">正在加载书籍...</div>', '<div className="text-sm leading-[1.7] text-paper-muted">正在加载书籍...</div>'), /散写的无单位比率|第 8 步行高长度式/],
  ["排版棘轮·行高长度式⑦（BD-24 补盲区·CSS 侧）：line-height: var(--studio-lead) 这类自创名字让这本账对 CSS 声明彻底失明（旧判据对 var( 无条件 return）。实仓 line-height 的 var 写法 0 处，所以这条严格化不需要预算，必须红", () => mutate("src/features/search/search.css", "  line-height: 1.6;", "  line-height: var(--studio-lead);"), /八档令牌的引用形式|第 8 步行高长度式/],
  ["排版棘轮·行高长度式⑧（BD-24 补盲区·@apply 分支）：CSS 里 @apply 是工具类的另一条入口，产物实测 .library-path{line-height:var(--leading-13)} 就是这么烘进去的。把 leading-[24px] 写进 @apply，走的是 walkAtRules 那条循环，必须红（旧正则在这里同样瞎）", () => mutate("src/styles.css", "@apply truncate font-mono text-[13px] leading-[var(--leading-13)]", "@apply truncate font-mono text-[13px] leading-[24px]"), /第 8 步行高长度式/],
  ["排版棘轮·行高长度式⑨（BD-24 补盲区·@apply 分支的配对）：@apply 里把同名对拆成一半——text-[13px] 配 var(--leading-12)，必须红（配对校验在 CSS 分支同样生效，不能只在 TSX 那侧有牙）", () => mutate("src/styles.css", "@apply truncate font-mono text-[13px] leading-[var(--leading-13)]", "@apply truncate font-mono text-[13px] leading-[var(--leading-12)]"), /与同行字号[\s\S]*?不同名|第 8 步行高长度式/],
  ["排版棘轮·反向⑤（BD-24）：换成 leading-[var(--leading-14)] 是这本账的收敛目标形态，配 text-sm 同名，绝不该红——把还债写法判成债，下一批人就会绕过判据（本仓 43 处此刻正是这个形态，红了整套源码就绿不回来）", () => mutate("src/features/library/LibraryPage.tsx", '<div className="text-sm text-paper-muted">正在加载书籍...</div>', '<div className="text-sm leading-[var(--leading-14)] text-paper-muted">正在加载书籍...</div>'), null],
  ["排版棘轮·反向⑥（BD-24）：CSS 侧 line-height: var(--leading-12) 是八档引用的合法形态（①那本账数的是散写字面值，不是「有没有用令牌」，同反向①的规矩），不该红", () => mutate("src/features/search/search.css", "  line-height: 1.6;", "  line-height: var(--leading-12);"), null],
  ["排版棘轮·反向⑦（BD-24）：字号走继承时配对校验**刻意不猜**（ui.tsx:184 的 TextArea 身上没有 text-*，14 由 .paper-input 继承来；换成 var(--leading-12) 保持绿）。这条绿就是本批如实记下的豁免代价：纯继承处能绕开配对——③b 注释里写着，别把它当成判据坏了", () => mutate("src/components/ui.tsx", "paper-input px-3 py-2 leading-[var(--leading-14)]", "paper-input px-3 py-2 leading-[var(--leading-12)]"), null],
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
  ["阴影棘轮①：TSX 工具类新债——阴影台账上 TSX 侧条目已随批次 BD-19 清零，此刻**空表=新增即红**：给 LibraryPage 那个已归 var(--shadow-2) 的下拉再挂一个 shadow-lift 必须红（宿主变迁：批次 AO 起这批牙钉 ErrorBoundary，BD-19 还清它最后那笔后 TSX 侧无债，牙改回本仓唯一还挂着浮层工具类串的 LibraryPage——它同时在间距/状态色几组里当宿主，早已在备份清单里）", () => mutate("src/features/library/LibraryPage.tsx", "bg-paper-panel [box-shadow:var(--shadow-2)] p-1", "bg-paper-panel [box-shadow:var(--shadow-2)] p-1 shadow-lift"), /阴影刻度/],
  ["阴影棘轮②：CSS 声明新债——把字面投影写进阴影预算表里根本没有的文件（inbox-local.css 视同 0，必须红）", () => mutate("src/features/creation/inbox/inbox-local.css", ".inbox-page {\n  display: flex;", ".inbox-page {\n  box-shadow: 0 10px 30px rgba(34, 38, 48, 0.05);\n  display: flex;"), /阴影刻度/],
  ["阴影棘轮③：@apply 分支——@apply 里的 shadow-lift 编译后就是一条 box-shadow 声明，和 TSX 挂工具类等价，必须同判（批次 AL 把 .paper-panel 那条压死的 shadow-lift 退役后，这颗牙挪到仍活着的夜读皮肤宿主上）", () => mutate("src/styles.css", "@apply border border-stone-800 text-stone-100 shadow-paper;", "@apply border border-stone-800 text-stone-100 shadow-paper shadow-lift;"), /阴影刻度/],
  // 批次 BD-25（用户第二项裁定「材质移出投影账」）的四颗牙：三红 + 一绿对照。
  // DECOR_SHADOWS 的 key 是 文件+选择器+值串 三者全等（三者各自先做空白归一化），
  // 所以「改一点值」「整条删掉」「把选择器扩到没登记的类」三种顺手写法都必须红——
  // 这三条合起来才让那张登记表不至于变成一个可以躲进豁免的口袋；
  // 第四颗是绿对照，它证明红来自内容失配，不是这张表按排版乱咬人。
  ["阴影棘轮·材质登记①（BD-25）：把书封那条的投影值改强一档（0 2px 5px → 0 2px 9px）——三者全等失配，登记不再豁免，它重新被数成债，必须红。这条牙守的是「顺手把材质阴影改强一点就躲过了棘轮」。⚠ needle 是这条声明在源码里的**换行原样**（值串跨三行书写）：扁平成一行的写法源码里不存在，needle 找不到就 SKIP，而 SKIP 只是计为无效、不会假装通过——这批牙里最容易写错的就是这种假牙", () => mutate("src/styles/editorial-studio.css", "    inset 0 1px 0 rgba(255, 255, 255, 0.28),\n    0 2px 5px rgba(34, 38, 48, 0.18);", "    inset 0 1px 0 rgba(255, 255, 255, 0.28),\n    0 2px 9px rgba(34, 38, 48, 0.18);"), /材质\/装饰登记表有 1 条没在源码里扫到|第 8 步阴影刻度/],
  ["阴影棘轮·材质登记②（BD-25）：整条删掉键帽那条台阶线声明（登记条目变成扫不到的僵尸 → 必须红）。删一条被豁免的声明比改一条更危险：它会让那张表悄悄变成「谁也不知道还剩下的空壳」", () => mutate("src/styles/editorial-studio.css", "\n  box-shadow: 0 1px 0 var(--border-strong);", ""), /材质\/装饰登记表有 1 条没在源码里扫到|第 8 步阴影刻度/],
  ["阴影棘轮·材质登记③（BD-25）：把那条已登记的键帽规则的**选择器扩**到一个没登记的类（.stats-card，它是活的卡片宿主，InboxItemList 就在用）——选择器文本一变，登记条目扫不到（僵尸豁免红），同时这条真发射的 box-shadow 重新数成 editorial-studio.css 的债（7→8），两头都红。这条牙钉的是表的可扩展性边界：新增一族材质要人批并登记，判据不自动认同类", () => mutate("src/styles/editorial-studio.css", ".desktop-search-command kbd,\n.uni-search-shell kbd {", ".desktop-search-command kbd,\n.uni-search-shell kbd,\n.stats-card {"), /第 8 步阴影刻度/],
  ["阴影棘轮·材质登记④（BD-25 反向）：把那条已登记的键帽声明的**选择器并成一行**（`.a kbd, .b kbd {`），值与结构一字不动——不该红。登记 key 比的是空白归一化后的字符串，换行与缩进不是设计事实。这条绿是①②③的对照组：它证明那三颗的红来自内容失配，不是这张表按排版乱咬；它同时也证明 ①③ 那两条 needle 是真的（同一处声明换个排版就还是扫得到）", () => mutate("src/styles/editorial-studio.css", ".desktop-search-command kbd,\n.uni-search-shell kbd {\n  box-shadow: 0 1px 0 var(--border-strong);\n}", ".desktop-search-command kbd, .uni-search-shell kbd {\n  box-shadow: 0 1px 0 var(--border-strong);\n}"), null],
  ["阴影棘轮·反向①：伏笔是本产品的业务词（foreshadow），text-shadow 是属性名——都在代码里而非注释里，前后断言必须挡住，不该变红", () => mutate("src/features/library/LibraryPage.tsx", "export function LibraryPage() {", "export function LibraryPage() {\n  const zzProbe = \"foreshadow foreshadowResolved foreshadows text-shadow\";\n  void zzProbe;"), null],
  ["阴影棘轮·反向②：四种合法豁免——shadow-none / shadow-inner / 颜色档 shadow-white\\/20（只产 --tw-shadow-color）/ [box-shadow:var(--focus-ring)] 焦点环，都不该变红", () => mutate("src/features/library/LibraryPage.tsx", "export function LibraryPage() {", "export function LibraryPage() {\n  const zzExempt = \"shadow-none shadow-inner shadow-white/20 focus-visible:[box-shadow:var(--focus-ring)]\";\n  void zzExempt;"), null],
  ["阴影棘轮·还债不降预算：把卡族分组那条离刻度投影换成棘轮认的 var(--shadow-1) 是合法收敛（editorial 债 4→3），但预算仍是 4，必须红并指名该文件（⚠ BD-88 换锚：滑块拇指已随 BD-87/88 立成材质令牌离账、settings-controls.css 预算条目移除；改钉 editorial 的卡族分组——A 族那条分组声明仍在账上，文件预算 4）", () => mutate("src/styles/editorial-studio.css", "  box-shadow: 0 1px 2px rgba(34, 38, 48, 0.05);", "  box-shadow: var(--shadow-1);"), /预算没跟着降/],
  // 第 8 步批次 K：幻影写法硬红。shadow-[var(--shadow-N)] 在产物里只产 --tw-shadow-color、
  // 没有 box-shadow 声明——「改了画不出来」，且旧判据还给它记成功还债，所以单独 fail()。
  ["批次 K 回归：幻影写法 shadow-[var(--shadow-2)] 必须硬红（不进计数，因为它画的根本不是阴影；宿主随批次 BD-19 搬家——ErrorBoundary 那条串归成 [box-shadow:var(--shadow-3)] 后旧 needle 消失，幻影判据与预算无关，钉在任何活着的 TSX 串上都测同一件事，这里选它本尊，备份清单照旧）", () => mutate("src/components/ErrorBoundary.tsx", "bg-paper-panel p-6 [box-shadow:var(--shadow-3)]", "bg-paper-panel p-6 [box-shadow:var(--shadow-3)] shadow-[var(--shadow-2)]"), /幻影/],
  ["批次 K 回归·反向：属性形式 [box-shadow:var(--shadow-2)] 与其 hover: 变体是产物实测唯一能发射的任意值写法，既不被幻影判据咬、又走 var(--shadow) 豁免不计数，不该变红", () => mutate("src/features/library/LibraryPage.tsx", "export function LibraryPage() {", "export function LibraryPage() {\n  const zzReal = \"[box-shadow:var(--shadow-2)] hover:[box-shadow:var(--shadow-2)]\";\n  void zzReal;"), null],
  ["批次 K 回归：字面量任意值 shadow-[0_1px_0_rgba(...)] 确实发射投影（.paper-topbar 实测），所以不归幻影硬红管——但它是刻度外的手抄值，由棘轮计债（宿主批次 BD-19 起换回 ErrorBoundary 的新串：它已还进 var(--shadow-3)、TSX 侧预算条目移除，空表上多一处任意值投影必须当场红，红的是阴影刻度而非幻影）", () => mutate("src/components/ErrorBoundary.tsx", "bg-paper-panel p-6 [box-shadow:var(--shadow-3)]", "bg-paper-panel p-6 [box-shadow:var(--shadow-3)] shadow-[0_1px_0_rgba(255,255,255,0.45)]"), /阴影刻度/],
  // 第 8 步批次 BD-19（阴影分级）：五笔债分别走归级、删死声明、撤冠光、纯环豁免四条路，
  // 下面四条测的就是「还掉的方向不能被写回去」——每一条都指名它当初为什么能清零。
  ["批次 BD-19 回归：把 .replace-panel 那条 inset 冠光写回 var(--shadow-3) 前面（混排值整串不再命中 var(--shadow-*) 豁免，replace.css 预算条目已移除，多一处必须红——否则撤冠光等于给这个浮层松了绑）", () => mutate("src/features/creation/replace/replace.css", "  box-shadow: var(--shadow-3);\n}\n\n.replace-panel-header {", "  box-shadow:\n    inset 0 1px 0 rgba(255, 255, 255, 0.5),\n    var(--shadow-3);\n}\n\n.replace-panel-header {"), /阴影刻度/],
  ["批次 BD-19 回归：把那条**层叠压死**的选中行投影写回 .inbox-item--selected（editorial 预算已降到 13，多一处必须红。它的价值在方向：退役判据认「从未渲染」这条理由，如果哪天有人觉得补回投影是免费的，账必须当场咬住——补回来不等于恢复原状，而是给选中行新画一圈浮起）", () => mutate("src/styles/editorial-studio.css", "  background: var(--studio-paper) !important;\n", "  background: var(--studio-paper) !important;\n  box-shadow: 0 4px 14px rgba(34, 38, 48, 0.07);\n"), /阴影刻度/],
  ["批次 BD-19 回归·反向：焦点柔光现在写成显式声明，按批次 AD 的纯环豁免**不该计数**——把它换成一条真投影（带偏移与模糊的 0 6px 18px）才叫把豁免撑成逃生舱，styles.css 7→8 必须红。这条测的是「豁免认的是几何形状，不是宿主身份」：同一个 .paper-input:focus、同一个棕铜色，写成环就绿、写成投影就红", () => mutate("src/styles.css", "    box-shadow: 0 0 0 1px rgb(var(--rgb-accent) / 0.2), 0 0 0 3px rgba(138, 90, 43, 0.08);", "    box-shadow: 0 0 0 1px rgb(var(--rgb-accent) / 0.2), 0 6px 18px rgba(138, 90, 43, 0.08);"), /阴影刻度/],
  ["批次 BD-19 回归·反向②：纯环声明换另一种合法上色（color-mix 混主色）不该变红——AD 说的豁免看的是几何形状，不是颜色来源（这条同时守住 .paper-input:focus 不会被「因为颜色是遗留棕铜」而误计成投影债）", () => mutate("src/styles.css", "    box-shadow: 0 0 0 1px rgb(var(--rgb-accent) / 0.2), 0 0 0 3px rgba(138, 90, 43, 0.08);", "    box-shadow: 0 0 0 1px color-mix(in srgb, var(--copper) 20%, transparent), 0 0 0 3px color-mix(in srgb, var(--copper) 8%, transparent);"), null],
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
  ["批次 AQ 回归：把刚退役的侧栏卡圆角写回 styles.css（editorial 那份 border-radius: 0 同特异度更晚，它从未弯过一个角，却被圆角棘轮记成活债——现在这个方向零容忍）", () => mutate("src/styles.css", ".desktop-sidebar-card {\n  /* BD-39 摘死声明：padding: 12px", ".desktop-sidebar-card {\n  border-radius: var(--radius-2);\n  /* BD-39 摘死声明：padding: 12px"), /几何分支/],
  ["批次 AQ 回归·反向①：两份主题表同分支写同一个值是冗余不是分叉（谁赢都一样），不该变红", () => mutate("src/styles.css", ".desktop-sidebar-card {\n  /* BD-39 摘死声明：padding: 12px", ".desktop-sidebar-card {\n  border-radius: 0;\n  /* BD-39 摘死声明：padding: 12px"), null],
  ["批次 AQ 回归·反向②：带模式前缀的覆盖分支文本不同、各管各的层叠，是显式决定，不该变红", () => mutate("src/styles.css", ".desktop-sidebar-card {\n  /* BD-39 摘死声明：padding: 12px", ".desktop-root--focus .desktop-sidebar-card {\n  border-radius: 4px;\n}\n\n.desktop-sidebar-card {\n  /* BD-39 摘死声明：padding: 12px"), null],
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
  // ⚠ 批次 BD-27 搬家：这对牙原本钉在 editorial 的 `.creation-wizard { border-radius: var(--radius-3)` 上，
  // 而宿主判据收紧成词边界后实测该类名查无落点（向导真身是 Dialog 原语的 rounded-[var(--radius-3)]
  // 工具类，src/components/ui/Dialog.tsx:100）——needle 挂在一个从未渲染的规则上，删规则即拆牙。
  // 新宿主选 .inbox-editor-grid：活、规则头全文件唯一、radius+shadow 成对且在 editorial 独家
  // （不撞「主题×特性」分叉透镜），这对牙测的形状原样不变——离刻度写回必红、刻度写法不红。
  ["第 8 步批次 Y 回归：把迁移掉的离刻度圆角写回 .inbox-editor-grid（editorial 圆角预算空表=零容忍，多一处必须红——否则迁移等于给这一页松了绑）", () => mutate("src/styles/editorial-studio.css", "  border-radius: var(--radius-2);\n  background: var(--studio-paper);\n  box-shadow: var(--shadow-1);", "  border-radius: 16px;\n  background: var(--studio-paper);\n  box-shadow: var(--shadow-1);"), /圆角刻度/],
  ["第 8 步批次 Y 回归·反向：同一宿主写 var(--radius-3) 是合法收敛，不该变红", () => mutate("src/styles/editorial-studio.css", "  border-radius: var(--radius-2);\n  background: var(--studio-paper);\n  box-shadow: var(--shadow-1);", "  border-radius: var(--radius-3);\n  background: var(--studio-paper);\n  box-shadow: var(--shadow-1);"), null],
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
  ["第 8 步批次 AC 回归：把退役的手抄对话框投影写回 .inbox-editor-grid（editorial 阴影预算 4，多一处必须红）——宿主原为 .creation-wizard，批次 BD-27 查明那条规则从未渲染，这对牙随规则一起搬家", () => mutate("src/styles/editorial-studio.css", "  box-shadow: var(--shadow-1);\n}\n\n.inbox-list {", "  box-shadow: 0 40px 120px rgba(0, 0, 0, 0.34), 0 6px 18px rgba(0, 0, 0, 0.14);\n}\n\n.inbox-list {"), /阴影刻度/],
  ["第 8 步批次 AC 回归·反向：同一宿主写 var(--shadow-4) 不该变红（刻度内换档是合法调整）", () => mutate("src/styles/editorial-studio.css", "  box-shadow: var(--shadow-1);\n}\n\n.inbox-list {", "  box-shadow: var(--shadow-4);\n}\n\n.inbox-list {"), null],
  // 第 8 步批次 AD：判据修正，不动一行 CSS（两张胜者表逐字节相同）。
  // §2.3 的描边式焦点环（0 0 0 Npx，偏移/模糊全 0）不是 §5.3 的投影层级；
  // CSS 侧此前漏了这条豁免，TSX 侧的 var(--focus-*) 早就豁免了——两侧不对称。
  ["第 8 步批次 AD·账本仍咬得住：删掉 editorial 卡族分组那条真投影（该组 1 处债，文件预算 4），账本变小必须红——豁免环不等于对真投影放手（⚠ BD-88 换锚：滑块拇指已随材质令牌离账，改钉 editorial 卡族分组；删掉声明后预算 4→3 未降）", () => mutate("src/styles/editorial-studio.css", "  box-shadow: 0 1px 2px rgba(34, 38, 48, 0.05);\n", ""), /阴影刻度/],
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
  ["第 8 步批次 AK 回归②：把整条删掉的幻影 .reader-glassbar 原样复活（宿主依旧查无——它名下的离刻度投影债回来了，棘轮必须咬住）。⚠ BD-87 换锚：原条挂 shadow-paper，纸面浮影立成材质令牌后已合法豁免，改用仍在账上的 shadow-lift 来证明「复活一条查无宿主的规则」本身必须被数成债", () => mutate("src/styles.css", "  .motion-notice {\n    animation: paper-soft-in 180ms ease both;\n  }", "  .motion-notice {\n    animation: paper-soft-in 180ms ease both;\n  }\n\n  .reader-glassbar {\n    @apply border border-paper-line bg-paper-panel/86 shadow-lift backdrop-blur-md;\n  }"), /阴影刻度/],
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
  ["第 8 步批次 AM·反向：给 .motion-dialog 那条 var(--shadow-3) !important 加同规则 border 不该红——AE 的 hairline 双拼只管 shadow-1 的 0 0 0 1px 环，shadow-3 是纯投影档、对话框带边框是正常设计（needle 里原本并列的 .confirm-dialog 死支已由批次 BD-27 摘掉，本条同步只剩活支）", () => mutate("src/styles/editorial-studio.css", ".motion-dialog {\n  box-shadow: var(--shadow-3) !important;\n}", ".motion-dialog {\n  border: 1px solid var(--border-subtle);\n  box-shadow: var(--shadow-3) !important;\n}"), null],
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
  // ⚠ 批次 BD-26：这颗牙的 needle 是 `.desktop-page-actions button:first-child…:hover` 里那条
  //   box-shadow: 0 5px 14px color-mix(…28%)，它随 12 条接管规则整族删除，needle 已不在源码里
  //   → 搬走。原牙钉的是「删掉族内一条投影却不降 FROZEN_BUTTON_SHADOW_COUNT 要红」，而本批把
  //   那个数字**合法地**降到 0（名单与计数同时清零，理由见守卫 BD-26 注）。搬家的方向不是
  //   「换个宿主继续测相等」：空名单下 frozenHits 恒为 0，那条相等判据已经不可达，拿不可达的
  //   分支做牙就是假绿。所以换成两颗**可达且更严**的牙，挡的正是这条旧牙最怕的那件事——
  //   把投影写回按钮、账面却全绿。两颗都用 :not([data-variant]) 之外的形状区分：
  ["批次 BD-26·复活①（接管规则不许复活）：把删掉的 .desktop-page-actions button 原样写回一条（声明刻意只放 background，让红只能来自第 6 步判据本身）——LEGACY 清单已空，从此是硬红，不再有「已冻结」这档", () => mutate("src/styles/editorial-studio.css", "/* Keycaps rise off the surface. */", ".desktop-page-actions button {\n  background: var(--bg-surface);\n}\n\n/* Keycaps rise off the surface. */"), /后代按钮规则未排除/],
  ["批次 BD-26·复活②（§2.5 冻结名单清零）：把族里那条 hover 铜影写回一条**带 :not([data-variant]) 排除**的规则——排除合法、第 6 步判据不咬，此时必须只剩 §2.5 咬住它。这条证明「豁免名单清空」真生效，而不是被别的红盖住", () => mutate("src/styles/editorial-studio.css", "/* Keycaps rise off the surface. */", ".desktop-page-actions button:not([data-variant]) {\n  box-shadow: 0 5px 14px color-mix(in srgb, var(--accent-spine) 28%, transparent);\n}\n\n/* Keycaps rise off the surface. */"), /按钮一律无阴影/],
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
  ["第 8 步批次 AG·反向：控件按钮上写纯描边焦点环不该红（§2.3 机制，元素型与类名型两侧豁免必须对称）", () => mutate("src/styles/editorial-studio.css", ".project-nav-back {\n  margin: 0 0 10px;\n  padding-left: 10px !important;\n  border: 1px solid var(--border-subtle) !important;\n  border-left: 1px solid var(--border-subtle) !important;\n  border-radius: var(--radius-1) !important;\n  background: var(--bg-surface) !important;\n}", ".project-nav-back {\n  margin: 0 0 10px;\n  padding-left: 10px !important;\n  border: 1px solid var(--border-subtle) !important;\n  border-left: 1px solid var(--border-subtle) !important;\n  border-radius: var(--radius-1) !important;\n  background: var(--bg-surface) !important;\n  box-shadow: 0 0 0 3px var(--action-tint);\n}"), null],
  // 批次 BD-22：MEASURE 不变量的牙。42em 行长由三份文件的几组声明合力成立，
  // 每条牙只改**一处**——真实失效剧本正是「改了一个数字、忘了它的搭档」，vitest 与截图都看不见。
  ["批次 BD-22 MEASURE①：稿纸正文 cap 的内边距补偿项 64→48（padding 仍是 32×2），正文会只剩 656px=41 字，必须红", () => mutate("src/styles/editorial-studio.css", "  max-width: calc(42em + 64px);", "  max-width: calc(42em + 48px);"), /MEASURE 不变量：/],
  ["批次 BD-22 MEASURE①·搭档在另一侧：只把 padding-inline 32→34（cap 不动）同样要红——判据是关系式，两侧任一漂移都掉字", () => mutate("src/styles/editorial-studio.css", "  padding-inline: 32px;", "  padding-inline: 34px;"), /MEASURE 不变量：/],
  ["批次 BD-22 MEASURE②：把 ≤1180 纸壳从 738 改回「看起来更整齐」的 736（本批纠正的那个错觉），正文被边框挤成 734，必须红", () => mutate("src/styles.css", "    width: min(738px, calc(100% - 30px));", "    width: min(736px, calc(100% - 30px));"), /MEASURE 不变量：/],
  ["批次 BD-22 MEASURE②·宽屏那档同样在算：min(820px,…) 收到 700px 时纸壳内宽 698 < 正文边框盒 736，必须红（旧判据只管窄屏的话，宽屏就是盲区）", () => mutate("src/styles.css", "  width: min(820px, calc(100% - 52px));", "  width: min(700px, calc(100% - 52px));"), /MEASURE 不变量：/],
  ["批次 BD-22 MEASURE③·跨文件：把 editorial 的 ::-webkit-scrollbar width 9→10（styles.css 那份本就被后置盖掉，胜者是 editorial），通读页那笔 +9px 补偿立刻漂，必须红", () => mutate("src/styles/editorial-studio.css", "  width: 9px;", "  width: 10px;"), /MEASURE 不变量：/],
  ["批次 BD-22 MEASURE③·纸面 cap 少算一项：68+2+9 去掉 +9（滚动容器溢出时列被挤成 663px=41 字），必须红（BD-76 换锚：横向内衬 34→32 让补偿项 68→64，锚串随之 42em+64px+2px+9px；算术不变：738 − 64 − 2 − 9 = 663）", () => mutate("src/features/creation/preview/preview-local.css", "  max-width: calc(42em + 64px + 2px + 9px);", "  max-width: calc(42em + 64px + 2px);"), /MEASURE 不变量：/],
  ["批次 BD-22 MEASURE③·列自己那半：.preview-chapter 的 42em 写成 40em（规格值从声明里消失），必须红", () => mutate("src/features/creation/preview/preview-local.css", ".preview-chapter {\n  max-width: 42em;", ".preview-chapter {\n  max-width: 40em;"), /MEASURE 不变量：/],
  // ⚠ 反向用例的取值刻意挑**同在刻度外/刻度内**的组合（34→36、16→12）：换成 40/10 这类
  // 会挪动间距台账计数的值，先被间距棘轮咬红，就测不到「MEASURE 只咬挤字的方向」这件事了。
  ["批次 BD-22 MEASURE·反向：≤900 档内边距 16→12（刻度内换刻度内），可用宽 751−24−2−9=696 更富余，不该红——判据只咬「挤掉字」的方向。⚠ 批次 BD-70 重钉锚点：该条底槽同批 30→32（30px 桶收 32），锚串随之变成 `20px 16px 32px`；变体只动横向 16→12，底槽保留现值，否则测试会同时触发间距值账的像素漂移", () => mutate("src/features/creation/preview/preview-local.css", "    padding: 20px 16px 32px;", "    padding: 20px 12px 32px;"), null],
  ["批次 BD-22 立、BD-46 换锚·MEASURE 反向：三处**一起**同挪（正文 padding-inline 32→40、cap 补偿 64→80、纸壳 738→754）——42em 仍是 42em，①②两条算术关系全保持，不该红。⚠ 这颗原先钉的是通读页 ③ 那对（preview padding 34→36 + 补偿 68→72），BD-46 的间距值账把那个形状咬红了，而且咬得对：34px 是**已登记的离刻度债**，改它就是像素漂移，值账上线后那个形状不再是「零像素合法迁移」的样本。判据不放宽，牙换锚——①②那对挪到刻度上的另一对（32/64 与 40/80 全在 §5.1 档上、738/754 不是间距属性），实测唯一变化的是 MEASURE 读的算术，间距/阴影两本账一个字不动，所以「守的是关系、不是三个数字」这条语义原样保住了。⚠ 同批实测的对照：只挪 padding 一侧（32→40、补偿留着 64）当场红在 MEASURE——半边挪不许绿，这条没有因为换锚变松", () => { mutate("src/styles/editorial-studio.css", "  padding-inline: 32px;", "  padding-inline: 40px;"); mutate("src/styles/editorial-studio.css", "  max-width: calc(42em + 64px);", "  max-width: calc(42em + 80px);"); mutate("src/styles.css", "min(738px,", "min(754px,"); }, null],
  // 批次 BD-23：SEAL 不变量的牙。印章一族这四处刚被收回 §3.2 八档（18→20 ×2、15→16），
  // 本批新造的风险不是「字号离刻度」（那本账已经绿了），而是**有人顺手挪那只盒子或那圈环**
  // ——值在刻度上、盒子变小，字就压到内环上，而这在 vitest 与截图里都看不出来。
  // 每颗牙同样只改一处：真实失效剧本就是「改了一个数字、忘了它的搭档」。
  ["批次 BD-23 SEAL①：只把 ≤920 那条印章字号改成 16（基础档留 20）必须红——窄屏那条不是压缩档（盒子宽高逐字重复基础档），单边改等于窄屏印面突然换尺寸。⚠ 16 刻意是刻度上的值：字号棘轮咬不到它，红只能来自不变量", () => mutate("src/styles.css", "    font-size: 20px;", "    font-size: 16px;"), /SEAL 不变量：/],
  ["批次 BD-23 SEAL①·半锚：把 ≤920 那条 font-size 整条删掉、盒宽留着，必须红——这条测的是判据自己的失明方向：删干净（字号与盒宽一起没）等于继承基础档，合法；只读到一半就是选择器或属性名写错的症状，而它与真删掉在①上表现一模一样，所以单独判（读不懂不许绿）", () => mutate("src/styles.css", "    font-size: 20px;\n", ""), /SEAL 不变量：/],
  ["批次 BD-23 SEAL②：把印章盒子 38×38 缩成 26×26（字号 20 不动）必须红——净空 26−1×2−3×2=18 < 20，汉字 advance 恒等于 1em，字就压到内环上。这正是「值已经落进刻度、下一批人最顺手挪的是盒子」那条剧本", () => mutate("src/styles/editorial-studio.css", "  width: 38px;\n  height: 38px;", "  width: 26px;\n  height: 26px;"), /SEAL 不变量：/],
  ["批次 BD-23 SEAL②·环也算：只把外圈 inset 环 3px 画粗到 9px（盒子与字号都不动）必须红——净空 38−1×2−9×2=18 < 20。印面的净空是三处声明算出来的，不是盒宽本身；⚠ 这条同时证明它没被阴影棘轮误伤：那条声明本来就带一层非 inset 投影、早就在账上，改 spread 不挪计数", () => mutate("src/styles/editorial-studio.css", "inset 0 0 0 3px", "inset 0 0 0 9px"), /SEAL 不变量：/],
  // 反向两颗：证明判据咬的是**关系**，不是这三个数字本身——合法的同幅迁移不许红，
  // 否则下一批人只会绕过判据（BD-22 已经把这条教训写进 MEASURE 的注释）。
  ["批次 BD-23 SEAL·反向：盒子两侧一起放大 38→42（字号仍是 20）不该红——净空 34 ≥ 20，装得下的盒子改尺寸不是债。它守住「挪盒子必红」的过度灵敏版本：判据只咬挤字的方向", () => { mutate("src/styles/editorial-studio.css", "  width: 38px;\n  height: 38px;", "  width: 42px;\n  height: 42px;"); mutate("src/styles.css", "    width: 38px;\n    height: 38px;", "    width: 42px;\n    height: 42px;"); }, null],
  ["批次 BD-23 SEAL·反向：两档**一起**降到 16 不该红——16 同样在八档上、同样装得下（净空 30），BD-23 选 20 靠的是同族比例与邻居层级这两条**不判红**的证据（见守卫本节 ⚠：把它们写成法律就是循环论证）。这条牙钉住的是：将来带新证据改回 16 的人，只需两边一起改，不必先拆判据", () => { mutate("src/styles/editorial-studio.css", "  font-size: 20px;\n  font-weight: 700;\n  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.22);", "  font-size: 16px;\n  font-weight: 700;\n  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.22);"); mutate("src/styles.css", "    font-size: 20px;", "    font-size: 16px;"); }, null],
  // 批次 BD-28：自定义属性引用环（守卫「环不变量」一节）。这批真正修掉的是
  // styles.css:13 --text-main: var(--text-primary) ⇄ editorial-studio.css:50 --text-primary: var(--text-main)
  // 这一对跨文件别名环——晨校因此全站正文渲染成继承黑 rgb(0,0,0)，而对比度断言只正则读
  // tokens.css 的 #20242a 字面量，永远看不见。四颗正牙各挡一种复发形状，两颗反牙挡过严。
  ["批次 BD-28 环①（原样复活）：把删掉的 editorial 桥接行 --text-primary: var(--text-main) 写回 :root（styles.css:13 那条反向别名还活着）——这就是本批修掉的那个环，必须红", () => mutate("src/styles/editorial-studio.css", "  --text: var(--text-main);", "  --text: var(--text-main);\n  --text-primary: var(--text-main);"), /自定义属性引用环/],
  ["批次 BD-28 环②（新环，不是复活动作）：给桥接块里合法的 --text-dim: var(--text-muted) 补一条反向 --text-muted: var(--text-dim)。名字是新的、形状与①一模一样，必须红——判据盯的是「环」这个形状，不是那两个特定名字", () => mutate("src/styles/editorial-studio.css", "  --text-dim: var(--text-muted);", "  --text-dim: var(--text-muted);\n  --text-muted: var(--text-dim);"), /自定义属性引用环/],
  ["批次 BD-28 环③（自环）：--zz-self: var(--zz-self) 一行就成环，必须红。这条测的是检测器最容易漏的形状——图上只有一条边、DFS 若从「长度 ≥2 的环」起步就放过它", () => mutate("src/styles/tokens.css", "  --text-secondary: #666d77;", "  --text-secondary: #666d77;\n  --zz-self: var(--zz-self);"), /自定义属性引用环/],
  ["批次 BD-28 环④（跨文件成环）：环的两半分居 tokens.css 与 styles.css，证明检测器把全站 CSS 当一张图，而不是逐文件查（当年那两个名字也正是跨文件才成环的）", () => { mutate("src/styles/tokens.css", "  --text-secondary: #666d77;", "  --text-secondary: #666d77;\n  --zz-x: var(--zz-y);"); mutate("src/styles.css", "  --text-main: var(--text-primary);", "  --text-main: var(--text-primary);\n  --zz-y: var(--zz-x);"); }, /自定义属性引用环/],
  // 反向两颗：判据过严的代价是下一批人直接绕过它（BD-22/BD-23 同一条教训）。
  ["批次 BD-28 环·反向①：三跳合法别名链 --zz-a → --zz-b → --zz-c → 令牌，无环不该红——本仓别名层大量是这种形状（--copper → --action-primary），误伤等于把判据判死刑", () => mutate("src/styles/tokens.css", "  --text-secondary: #666d77;", "  --text-secondary: #666d77;\n  --zz-a: var(--zz-b);\n  --zz-b: var(--zz-c);\n  --zz-c: var(--app-bg);"), null],
  ["批次 BD-28 环·反向②：把环的**文字**写进注释（本批两处断环墓碑就是这么写的：注释里原样抄着旧声明），不该红——守卫匹配的是声明，不剥注释就会自己咬自己", () => mutate("src/styles/editorial-studio.css", "  --text: var(--text-main);", "  /* 墓碑：--zz-p: var(--zz-q); --zz-q: var(--zz-p); */\n  --text: var(--text-main);"), null],
  // 批次 BD-29 + BD-30：颜色分支（守卫「颜色分支不得有两个真源」一节）。BD-29 摘掉
  // styles.css 里 95 条被 editorial-studio.css 同键压死的颜色声明，BD-30 再摘掉跨
  // 主题表 × 特性表两向的 20 条，判据全站归零、预算档取消。几何那族（圆角/投影）批次
  // V/AQ 早有同名判据，颜色族一直没人管。
  // ⚠ BD-30 重钉了三颗原先钉在「预算档 / 方向措辞」上的牙（③④⑥），并按 BD-30 查出的
  // 真实方向改写——顺序真源是 main.tsx 的 import 图，不是「文件叫什么名字」。
  ["批次 BD-29 死色①（原样复活）：把摘掉的 .desktop-brand small 的 color 写回原位，editorial-studio.css:179 同键那条还在后面压着它，必须红——BD-29 清掉的就是这个形状", () => mutate("src/styles.css", ".desktop-brand small {\n  /* BD-39 摘死声明：margin-top: 2px", ".desktop-brand small {\n  color: var(--paper-muted);\n  /* BD-39 摘死声明：margin-top: 2px"), /第 8 步颜色分支/],
  ["批次 BD-29 死色②（新宿主，不是复活动作）：给 .desktop-brand strong 补一条 color，editorial-studio.css:170 对同选择器早有异值——名字新、宿主新、形状与①一致，必须红。判据盯的是「同键异值 + 靠发射顺序决胜」这个形状，不是那 95 条特定声明", () => mutate("src/styles.css", ".desktop-brand strong {\n  font-size: 14px;", ".desktop-brand strong {\n  color: var(--paper-muted);\n  font-size: 14px;"), /第 8 步颜色分支/],
  ["批次 BD-29 死色③（方向反过来才对，BD-30 重钉）：给 editorial 的 .uni-search-group h3 补一条 color，search.css 同键异值——但 search.css 在 import 图里排第 2、editorial 第 4，所以**死的是 search.css 那条**，不是主题侧。必须红，且红名里点出的死侧是 search.css。这颗牙就是 BD-29 记反方向的那一处：原判据把它当成「主题侧被特性表压死」冻进预算，照那份账还债会删掉在画的声明", () => mutate("src/styles/editorial-studio.css", ".uni-search-group h3 {\n  font-family: var(--font-sans);", ".uni-search-group h3 {\n  color: var(--studio-cloth-muted);\n  font-family: var(--font-sans);"), /search\.css:\d+ \{color/],
  ["批次 BD-30 死色④（lazy 块方向）：把摘掉的 .history-item small 的 color 写回 styles.css，宿主页面挂载的 history-local.css 同键那条是**运行时注入**的、排在全部静态表之后，写回来立刻又被压死，必须红——这条测的是 BD-29 那版没有的方向：块级样式表与全局表之间也有一层顺序", () => mutate("src/styles.css", ".history-item small {\n", ".history-item small {\n  color: var(--paper-muted);\n"), /第 8 步颜色分支/],
  ["批次 BD-30 死色⑤（BD-29 记反的那 10 条的形状）：把 search.css 里摘掉的 .uni-search-head-icon color 写回原位，editorial-studio.css 在同一个主 bundle 里发射得更晚，又把它压死，必须红——原判据却说这是「主题侧死色」；这颗牙钉住纠正后的方向（死侧仍报 search.css）", () => mutate("src/features/search/search.css", ".uni-search-head-icon {\n  flex-shrink: 0;", ".uni-search-head-icon {\n  flex-shrink: 0;\n  color: var(--copper);"), /search\.css:\d+ \{color/],
  ["批次 BD-29 死色⑥（发射顺序前提·表间）：把 main.tsx 里两份主题表的 import 换序——本判据的「谁压谁」整个建立在 import 图推出的顺序上，两份主题表的行序一反，死侧会整体换位、摘行即改版，必须红（MEASURE 那条同样依赖它，这里验的是颜色分支自己有没有守住前提）", () => mutate("src/app/main.tsx", 'import "@/styles.css";\nimport "@/styles/editorial-studio.css";', 'import "@/styles/editorial-studio.css";\nimport "@/styles.css";'), /第 8 步颜色分支/],
  ["批次 BD-30 死色⑦（发射顺序前提·图本身）：把 main.tsx 里 `import App` 挪到两份主题表之后——特性表的 CSS 就从「比主题表早」变成「比主题表晚」，死侧整体换位。这条测的是判据有没有真在走 import 图，而不是只认 main.tsx 那两行：只查两行的话，这一改它完全看不见，而产物里的顺序确实变了", () => mutate("src/app/main.tsx", 'import App from "@/app/App";\nimport "@/styles.css";', 'import "@/styles.css";\nimport App from "@/app/App";'), /颜色分支判据退化/],
  ["批次 BD-30 死色⑧（判据退化·归一化）：把 styles.css 里自检锚点 .project-nav button 的选择器改成 `.project-nav > button`——两侧同键声明的共存扫不到了，必须以「判据退化」而不是「分叉」的形式红。这条测最阴的失效模式：键归一化被改坏时，真正的分叉会被安静放行，而账上已经归零、没有任何债能提醒人", () => mutate("src/styles.css", ".project-nav button {\n  display: flex;", ".project-nav > button {\n  display: flex;"), /颜色分支判据退化/],
  // 反向四颗：判据过严的代价和过松一样贵——它会把正当写法也判成违规，下一批人就开始绕过它。
  ["批次 BD-29 死色·反向①：给 styles.css 的 .desktop-brand strong 写一条与 editorial-studio.css:170 **同值**的 color，不该红——同值重复是冗余不是分叉（本仓还有几处这个形状，删哪边都是改版），把它判红等于逼人多删一条", () => mutate("src/styles.css", ".desktop-brand strong {\n  font-size: 14px;", ".desktop-brand strong {\n  color: var(--studio-cloth-text);\n  font-size: 14px;"), null],
  ["批次 BD-29 死色·反向②（两批各没摘的那条的形状）：一条双选择器规则里只压住一半——.desktop-nav button 在 editorial 里没有同键 color 对手、.desktop-brand small 有（:179），整条声明仍在画，**不该红**。两批正是靠这条判断把 .background-card:hover/.active 与 .uni-search-group li button:hover/.active 拆开而不是整条留下；判据若按「任一选择器被压」就判死，下一批就会把在画的半边删掉（两个选择器都取真宿主，免得红在幻影判据上）", () => mutate("src/styles.css", ".desktop-brand small {\n  /* BD-39 摘死声明：margin-top: 2px", ".desktop-nav button,\n.desktop-brand small {\n  color: var(--paper-muted);\n}\n\n.desktop-brand small {\n  /* BD-39 摘死声明：margin-top: 2px"), null],
  ["批次 BD-29 死色·反向③：!important 单独定胜者——在 styles.css 给 .desktop-brand small 写一条带 !important 的 color，editorial 那条同键不带，胜者就是它自己，不该红（与几何分支同一豁免：不存在「谁发射得晚」这件事）。⚠ 这条在本仓不是纸面的：.stats-card 的 background 就是这个形状", () => mutate("src/styles.css", ".desktop-brand small {\n  /* BD-39 摘死声明：margin-top: 2px", ".desktop-brand small {\n  color: var(--paper-muted) !important;\n  /* BD-39 摘死声明：margin-top: 2px"), null],
  ["批次 BD-30 死色·反向④（判据的边界·表内）：在 styles.css 里给 .outline-page-side h3 追加**第二条**同键异值 color（同表内后条压前条），不该红——同一张表内部的先后由这份表自己负责，那是重复声明、不是本判据防的「跨表没人管的分叉」。判据只比**不同文件**的表（代码里 `if (o === rel) continue`）；把它扩到表内会立刻把大量合法的「后条覆盖前条」判红，等于给判据判死刑", () => mutate("src/styles.css", ".outline-page-side h3 {", ".outline-page-side h3 {\n  color: rgb(1, 2, 3);\n}\n\n.outline-page-side h3 {"), null],
  ["批次 BD-30 死色·反向⑤（判据的边界·块级表之间）：给两块 lazy 块级表写同键异值的 color，不该红——往 history-local.css 的 .history-item-actions 加一条 color，再往 overview-local.css 追加一条同选择器异值的 color（锚在本表的 .overview-status-name 前）。两份块级表的相对顺序取决于用户先点进哪个界面（实测 16 张 lazy 表都排在全部静态表之后、彼此之间没有固定先后），静态读不出来，**不可判**就不许判：硬按「谁写在后面」排会造出假死侧，而假死侧正是 BD-29 记反方向那 10 条的病根。⚠ 第二条刻意复用另一块的真实类名：判据比的是选择器文本，宿主归哪块页面渲染不影响这块的顺序不可判性；两边都走令牌，免得红因落到别本账上", () => { mutate("src/features/creation/history/history-local.css", ".history-item-actions {\n  display: flex;", ".history-item-actions {\n  color: var(--copper);\n  display: flex;"); mutate("src/features/creation/overview/overview-local.css", ".overview-status-name {", ".history-item-actions {\n  color: var(--proof-mark);\n}\n\n.overview-status-name {"); }, null],
  // ---- 批次 BD-31：挂载覆盖前提 + 判据扩到全属性后钉的那份账 ----
  // BD-30 shipped 的判据默认「lazy 胜者总会挂载」。这条前提在本批的活体实验里被打穿：
  // .history-item-actions 由 HistoryPage.tsx（挂 history-local.css）**和** StatsPage.tsx
  // （不挂）两处渲染，所以主题侧那条在统计页是活的——判它死就是给「删掉一条活样式」开许可证。
  ["批次 BD-31 挂载覆盖①（BD-30 那个洞的形状，必须绿；BD-42 换锚）：往 styles.css 注入一条 .history-modal { color }，再往 history-local.css 同选择器注入异值那条。胜者在块级表，而 .history-modal 的三个宿主（CreateMilestoneDialog / PurgeTrashDialog / RestoreSnapshotDialog）都不 import 那份表——被压那条在那些页面上是**唯一在画的**声明，判据必须闭嘴。这颗牙守「别把活 CSS 判死」：把覆盖前提删掉，它当场红。⚠ BD-42 换锚说明：原钉的是 styles.css 的 .history-item-actions（宿主 StatsPage.tsx 借这个类名却不挂那份表）；BD-42 把借用改成各自类名，那条分叉**合法消失**、needle 随之消失（锚冲突审计报「命中 0 次 = 哑牙」），于是改钉本仓现存的同一形状：三个对话框只在 HistoryPage 里渲染，运行时表其实早已注入，但 import 图只沿模块**往外**找、够不着就保守不判——这层前提的本意正是「证不了就别判死」", () => { mutate("src/styles.css", ".history-item small {\n", ".history-modal {\n  color: rgb(11, 22, 33);\n}\n\n.history-item small {\n"); mutate("src/features/creation/history/history-local.css", ".history-modal {\n  width", ".history-modal {\n  color: rgb(44, 55, 66);\n  width"); }, null],
  ["批次 BD-31 挂载覆盖②（覆盖前提不是整体豁免·静态候选回落；BD-42 换锚）：上面那对注入照做（块级胜者盖不住那三个子对话框宿主），再往 editorial-studio.css 追加一条同选择器异值 color——那是一张**总会发射**的静态表，它压得住，必须红。这颗牙守反方向：块级表盖不住时判据若直接放弃整条判决（而不是回落到更晚的静态候选），主题表之间的分叉就从此隐身。⚠ BD-31 原锚 editorial 的 .overview-grid grid-template-columns 是 BD-32 还掉的账上条目、已变墓碑，needle 消失即 SKIP 计为无效（自愈设计），本批改钉文件末那条与本判据无关的规则。⚠ BD-42 换锚：前两处原钉 styles.css / history-local.css 的 .history-item-actions，那次借用改成各自类名后 needle 消失，两颗牙同批改钉 .history-modal", () => { mutate("src/styles.css", ".history-item small {\n", ".history-modal {\n  color: rgb(11, 22, 33);\n}\n\n.history-item small {\n"); mutate("src/features/creation/history/history-local.css", ".history-modal {\n  width", ".history-modal {\n  color: rgb(44, 55, 66);\n  width"); mutate("src/styles/editorial-studio.css", ".writing-outline-view-switch button.active {", ".history-modal {\n  color: rgb(55, 66, 77);\n}\n\n.writing-outline-view-switch button.active {"); }, /第 8 步颜色分支/],
  ["批次 BD-31 死账③（账外新增要红；BD-41 起固定锚）：往 styles.css 的 .project-workbench 注入一条 grid-template-columns: **167px** minmax(0, 1fr)——它被更晚发射的 editorial-studio.css:944（140px）同键整条压住，是一条**从不出现在账上**的新死声明。必须红，红因是「账外新增」。这颗牙守「债只许按实测逐条记」：允许账外新增等于把 BD-29 那种按文件冻结的合法额度请回来。⚠ BD-41 之前这颗靠 deadAnchor() 从账上现成的条目里挑锚，那等于**账上必须一直留一条债牙才有处下**——BD-37~40 三批连着被这件事绑架（第 3990 行、然后 3710），最后一次账上只剩它一条，牙和债变成了同一件事。现在改成永久固定锚：钉的是规则名 + 一条在画的声明（display: grid，不在任何账上），值刻意选 167（既不是胜者的 140、也不在 §5.1 八档上，而 grid-template-columns 本就不在间距/字号棘轮的属性集里），红因不会串到别的账。账清零之后这颗牙照样下得去", () => mutate("src/styles.css", ".project-workbench {\n  display: grid;\n", ".project-workbench {\n  display: grid;\n  grid-template-columns: 167px minmax(0, 1fr);\n"), /非颜色死声明账与实测不符/],
  ["批次 BD-31 死账③·反向（与胜者逐字同值不该红）：给同一条 .project-workbench 写 **grid-template-columns: 140px minmax(0, 1fr)**——照抄胜者 editorial-studio.css:944 的原值。同键同值是冗余不是分叉，判红等于逼人在两份静态表里删掉一条在画的声明。这颗的值是从胜者那一行读出来的（BD-34 那条教训之后第八次这么做），它同时钉住账本键的含值性：③ 换一档就红、这条逐字同值就绿，红绿之间差的只有那个值", () => mutate("src/styles.css", ".project-workbench {\n  display: grid;\n", ".project-workbench {\n  display: grid;\n  grid-template-columns: 140px minmax(0, 1fr);\n"), null],
  ["批次 BD-31 死账④（还了债不改账也要红；BD-41 起固定锚）：往守卫自己的 DEAD_LEDGER 里塞一条幻影行（src/styles.css 的 .bd41-phantom-host {bd41-phantom-prop: 7px}）——实测清单里没有它，账上却多了一条。必须红，红话是「账上 N 条已经扫不到了」。留在账上的死条目会让下一批人照一笔不存在的债去找，还会顺手把「账外新增」当合法额度。⚠ 这颗牙 BD-41 之前同样靠自动锚（把账上真条目注释掉），前提还是「账上得有条目」；现在改成直接改判据的账——账空了它照样测得出「账与实测必须逐字相同」这一头。为此守卫脚本进了电池的备份清单（改完即还原，每个用例都是新进程重读磁盘）", () => mutate("scripts/verify-design-tokens.mjs", "  const DEAD_LEDGER = new Set([\n", "  const DEAD_LEDGER = new Set([\n    \"src/styles.css\\\\t\\\\t.bd41-phantom-host\\\\tbd41-phantom-prop\\\\t7px\", // 电池注入的幻影行，验完立刻还原\n"), /已经扫不到/],
  // ---- 批次 BD-32：还掉 DEAD_LEDGER 的 10 条（search.css 4 + editorial 6）之后补的牙 ----
  ["批次 BD-32 还债①（摘掉的死声明写回原位，必须红）：把本批摘掉的 editorial .overview-status-list 那条 display: grid 写回墓碑位置——胜者 overview-local.css:107 的 display: flex 仍在更晚的块级表里压着它，而这条已不在 120 条的账上。必须红，且红因是「账外新增」而不是「颜色分叉」。这颗牙守的是还债的方向：写回死声明 = 又造一条从未渲染的声明", () => mutate("src/styles/editorial-studio.css", ".overview-status-list {\n", ".overview-status-list {\n  display: grid;\n"), /非颜色死声明账与实测不符/],
  ["批次 BD-32 还债·反向（同值共存不该红）：给刚还清的 editorial .overview-status-list 写一条 **gap: 6px**——它与胜者 overview-local.css:109 的 gap: 6px 同键同值。同值重复是冗余不是分叉（判红会逼人多删一条在画的），6px 又正好在 §5.1 刻度上（离刻度棘轮也不该跟着红），不该红。这颗牙是 BD-31 反向③在**非颜色族**的对应物", () => mutate("src/styles/editorial-studio.css", ".overview-status-list {\n", ".overview-status-list {\n  gap: 6px;\n"), null],
  // ---- 批次 BD-33：还掉 styles.css 桌面壳三族 22 条之后补的牙 ----
  // ⚠ 这两颗刻意钉在**规则名**（.desktop-commandbar { display: grid;）而不是任何一条具体声明上：
  //   BD-32 的教训是牙钉在账上条目、还债当天就哑。规则名不属于账，除非有人删整条规则它才会消失。
  ["批次 BD-33 还债②（本批摘掉的静态副本写回原位，必须红）：把 styles.css .desktop-commandbar 那条 gap: 18px 写回墓碑位置——胜者 editorial-studio.css:538 的 gap: 20px 是**更晚发射的静态表**，照样整条压住它，而这条已不在 98 条的账上。必须红，红因仍是「账外新增」。这颗牙与 BD-32 还债①同形，区别在胜者是静态表：静态胜者这一族占了账的绝大多数，它得有自己的哨兵", () => mutate("src/styles.css", ".desktop-commandbar {\n  display: grid;\n", ".desktop-commandbar {\n  display: grid;\n  gap: 18px;\n"), /非颜色死声明账与实测不符/],
  ["批次 BD-33 还债·反向②（与静态胜者同值不该红）：给 styles.css .desktop-commandbar 写一条 **gap: 20px**——与胜者 editorial-studio.css:538 同键同值。同值是冗余不是分叉，20px 又正好在 §5.1 刻度上，两条棘轮都不该红。这颗牙守的是「还债不许顺手把冗余也判死」：判红等于逼人在两份静态表里删掉一条在画的声明", () => mutate("src/styles.css", ".desktop-commandbar {\n  display: grid;\n", ".desktop-commandbar {\n  display: grid;\n  gap: 20px;\n"), null],
  // ---- 批次 BD-34：还掉写作页 .writing* 七族 19 条之后补的牙（同样钉规则名，不钉声明） ----
  ["批次 BD-34 还债③（写作页那一族也要有自己的哨兵）：把本批摘掉的 styles.css .writing-margin 那条 padding: 20px 18px 写回墓碑位置——胜者 editorial-studio.css:1159 在更晚发射的静态表里压着它，而这条已不在 79 条的账上。必须红，红因是「账外新增」。这颗与 BD-33 还债②同形，换的是族：证明那颗哨兵不是只对桌面壳生效。⚠ 锚带上了墓碑注释的头一截：`.writing-margin {` 在文件里有 4 处（还有 `.writing-desk--focus .writing-margin {` 与窄屏档），mutate 只换第一处，光用规则名会写到别的规则上、测的就不是这条了", () => mutate("src/styles.css", ".writing-margin {\n  /* BD-34 摘死声明：padding: 20px 18px", ".writing-margin {\n  padding: 20px 18px;\n  /* BD-34 摘死声明：padding: 20px 18px"), /非颜色死声明账与实测不符/],
  ["批次 BD-34 还债·反向（与静态胜者同值冗余不该红）：给 styles.css .writing-project-switcher 写一条 **padding: 12px 12px 10px**——与胜者 editorial-studio.css:1163 同键同值。同值是冗余不是分叉（判红等于逼人在两份静态表里删掉一条在画的），而这三个数 12/12/10 全在 §5.1 八档上，间距棘轮也不该跟着红。这颗牙是 BD-33 反向②在写作页的对应物，换的是族。⚠ 值刻意选胜者的原值而不是随手一个 20px：随手写异值就变成「账外新增」，那是另一颗牙（还债③）测的方向", () => mutate("src/styles.css", ".writing-project-switcher {\n", ".writing-project-switcher {\n  padding: 12px 12px 10px;\n"), null],
  // ---- 批次 BD-35：还掉桌面壳零散六族 18 条之后补的牙 ----
  // ⚠ 这批开始，还债前先跑锚冲突审计（见账本头注释）：styles.css 里 `.desktop-brand small {`、
  // `.desktop-sidebar-card {` 两族的行 507/584 本身是 BD-29/AQ 那些牙的 needle，本批刻意避开、
  // 一条没动。锚仍钉规则名 + 一条**在画的**声明（display: grid 不在账上），不钉任何债。
  ["批次 BD-35 还债（桌面壳零散族也要有哨兵）：把本批摘掉的 styles.css .desktop-sidebar 那条 grid-template-rows: auto minmax(0, 1fr) auto 写回原位——胜者 editorial-studio.css:79 是 auto minmax(0, 1fr) auto **auto**（更晚发射的静态表整条压住它），而这条已不在 61 条的账上。必须红，红因是「账外新增」。这颗与 BD-33 还债②、BD-34 还债③同形，换的是族。⚠ grid-template-rows 不在间距棘轮的属性集里，红因不会串到预算上", () => mutate("src/styles.css", ".desktop-sidebar {\n  display: grid;\n", ".desktop-sidebar {\n  display: grid;\n  grid-template-rows: auto minmax(0, 1fr) auto;\n"), /非颜色死声明账与实测不符/],
  ["批次 BD-35 还债·反向（与静态胜者逐字同值不该红）：给同一条 .desktop-sidebar 写 **grid-template-rows: auto minmax(0, 1fr) auto auto**——照抄胜者 editorial-studio.css:79 的原值。同键同值是冗余不是分叉，判红等于逼人在两份静态表里删掉一条在画的声明。⚠ 这颗牙的值是**从胜者那一行读出来的**，不是手凑的：BD-34 第一稿随手写异值，把 must-green 做成了 must-red", () => mutate("src/styles.css", ".desktop-sidebar {\n  display: grid;\n", ".desktop-sidebar {\n  display: grid;\n  grid-template-rows: auto minmax(0, 1fr) auto auto;\n"), null],
  // ---- 批次 BD-36：还掉 .project-home* 一族 11 条之后补的牙 ----
  // 还之前先跑锚冲突审计（BD-35 立的固定动作）：本批 11 条候选与电池 69 条 needle **零重叠**，
  // 摘完再跑一次仍零重叠。另：这批摘掉的五条间距值 8/10/12/16/0 **全在 §5.1 八档上**，
  // 所以间距预算 102 不动（274 处不变）——「还死声明」与「还刻度债」是两本账，不是每条死声明都兼有。
  ["批次 BD-36 还债（项目首页那一族也要有哨兵）：把本批摘掉的 styles.css .project-home-list 那条 gap: 8px 写回原位——胜者 editorial-studio.css:817 是 gap: 0（更晚发射的静态表整条压住它），而这条已不在 50 条的账上。必须红，红因是「账外新增」。这颗与 BD-33/34/35 的同形牙换的是族。⚠ 8px 在八档上，所以红因只会来自账、不会来自间距预算——归因干净", () => mutate("src/styles.css", ".project-home-list {\n  display: grid;\n", ".project-home-list {\n  display: grid;\n  gap: 8px;\n"), /非颜色死声明账与实测不符/],
  ["批次 BD-36 还债·反向（与静态胜者逐字同值不该红）：给 .project-home-list 写 **gap: 0**——照抄胜者 editorial-studio.css:817 的原值。同键同值是冗余不是分叉，0 也在 §5.1 刻度上，两本账都不该红。这颗牙顺带钉住一个容易被误判的形状：**把在画的 gap: 0 当成「一条可以顺手删掉的冗余」删掉**，项目列表就会掉回 gap: 8px 那条死副本曾经想要的间距", () => mutate("src/styles.css", ".project-home-list {\n  display: grid;\n", ".project-home-list {\n  display: grid;\n  gap: 0;\n"), null],
  // ---- 批次 BD-37：改按**宿主页面**凑整屏（卡片板 5 + 大纲页 6 + 背景设定 3 = 14 条），补的牙 ----
  // 本批锚冲突审计：摘之前 14 条候选 × 71 条 needle 零重叠，摘之后再跑一次仍零重叠。
  // ⚠ 有一处要记下来：本批把 styles.css 第 3990 行那条 {max-height: calc(100vh - 260px)} 摘掉了，
  // 而那正是 deadAnchor() 连续两批挑中的自动锚。审计的「覆盖行」口径看不到这点（needle 是运行时
  // 生成的，不在源码里），靠的是摘完立刻重跑一次候选测试：合格候选 6→3（176、177、3710），
  // 电池实跑自动挑中的是 3710 {grid-template-columns: 168px minmax(0, 1fr)}，仍然可用。
  // 自动锚的意义就在这里——被还掉的锚会自动换，不用人重钉。
  ["批次 BD-37 还债（卡片板那一族也要有哨兵）：把本批摘掉的 styles.css .cards-board-column 那条 min-width: 220px 写回墓碑位置——胜者 editorial-studio.css:2825 是 min-width: 196px（更晚发射的静态表整条压住它），而这条已不在 36 条的账上。必须红，红因是「账外新增」。⚠ 选 min-width 而不是本批别条：它不在间距棘轮的属性集里（SP_PROPS 只认 gap/padding/margin 族），红因不会串到预算上；而锚用「规则名 + 墓碑注释头」，因为这条规则的**全部**声明都被摘过，没有一条在画的声明可以当锚", () => mutate("src/styles.css", ".cards-board-column {\n  /* BD-37 摘死声明：min-width: 220px", ".cards-board-column {\n  min-width: 220px;\n  /* BD-37 摘死声明：min-width: 220px"), /非颜色死声明账与实测不符/],
  ["批次 BD-37 还债·反向（与静态胜者逐字同值不该红）：给 .cards-board-column 写 **min-width: 196px**——照抄胜者 editorial-studio.css:2825 的原值。同键同值是冗余不是分叉，不该红。这颗牙的值同样是从胜者那一行读出来的（BD-34 那条教训之后已是第三次这么做）。⚠ 顺带钉住一个假红的形状：幻影选择器判据与死分支判据都不该因为这条冗余而红——.cards-board-column 有宿主、这条也匹配得上", () => mutate("src/styles.css", ".cards-board-column {\n  /* BD-37 摘死声明：min-width: 220px", ".cards-board-column {\n  min-width: 196px;\n  /* BD-37 摘死声明：min-width: 220px"), null],
  // ---- 批次 BD-38：还掉桌面壳/灵感页/统计/收件箱零散 19 条之后补的牙 ----
  // 这批的锚冲突审计第一次**真的拦下了东西**：候选 24 条里 507/510（.desktop-brand small，
  // BD-29 死色四颗牙的 needle）与 584（.desktop-sidebar-card，AQ 三颗牙）被 needle 覆盖，
  // 3710/3711（.project-workbench）里 3710 是 deadAnchor() 当前自动挑中的锚、3711 与它同规则
  // ——这 5 条本批刻意没动，账上就剩它们。摘完复跑：73 条 needle 全部仍命中且唯一。
  // ⚠ 还有一处只有重跑候选测试才看得见：摘完之后 deadAnchor 的合格候选 3 → 1，只剩
  // 3710 那一条。也就是说那颗自动锚现在**没有备胎**了——下一批若还掉 3710/3711 或账上再少
  // 一条 px 声明，它就得抛错记 SKIP。这是判据变严的正常结果，但值得先记下来。
  ["批次 BD-38 还债（桌面壳这一族也要有哨兵）：把本批摘掉的 styles.css .desktop-workbench 那条 grid-template-columns: 236px minmax(0, 1fr) 写回原位——胜者 editorial-studio.css:64 是 216px minmax(0, 1fr)（更晚发射的静态表整条压住它），而这条已不在 17 条的账上。必须红，红因是「账外新增」。⚠ 选 grid-template-columns 是因为它不在间距/字号棘轮的属性集里，红因不会串到预算上；锚仍钉「规则名 + 一条在画的声明（display: grid）」，不钉任何债", () => mutate("src/styles.css", ".desktop-workbench {\n  display: grid;\n", ".desktop-workbench {\n  display: grid;\n  grid-template-columns: 236px minmax(0, 1fr);\n"), /非颜色死声明账与实测不符/],
  ["批次 BD-38 还债·反向（与静态胜者逐字同值不该红）：给同一条 .desktop-workbench 写 **grid-template-columns: 216px minmax(0, 1fr)**——照抄胜者 editorial-studio.css:64 的原值。同键同值是冗余不是分叉，判红等于逼人在两份静态表里删掉一条在画的声明。这颗的值仍是从胜者那一行读出来的（BD-34 那条教训之后第五次这么做）。⚠ 顺带钉住一个本批真实发生过的形状：.desktop-workbench 的两条声明都被摘过，规则里只剩注释——多选择器规则拆半、规则被摘空都会改账本键，所以「摘到规则只剩注释」这件事本身是判据管得到的，别以为留注释就等于没动过", () => mutate("src/styles.css", ".desktop-workbench {\n  display: grid;\n", ".desktop-workbench {\n  display: grid;\n  grid-template-columns: 216px minmax(0, 1fr);\n"), null],

  // ---- 批次 BD-39：人工锚批——还掉账上最后 4 条「绕不开的人工锚」静态胜者 ----
  // 这批动手前必须先做重锚手术（BD-38 的账本头预告过）：6 颗既有牙钉在本批要摘的死行上——
  // L544/545/546 钉 styles.css 第 584 行那条 `.desktop-sidebar-card` 的 padding: 12px（AQ 三颗），
  // L722/732/733 钉第 507 行 `.desktop-brand small` 的 margin-top: 2px（BD-29 死色四颗里的三颗）。
  // 全部改钉「规则名 + 本批墓碑注释头」（BD-37 立的模式），且插回的声明必须放在注释头
  // **之前**——写在头之后会落进注释体内，牙当场哑掉（本批手术第一版就踩了这个，逐颗
  // mutate→守卫→还原单测才抓住）。摘完复跑：75 条 needle 全部命中且唯一；6 颗重锚牙
  // 逐颗单测方向不变（2 正 4 反）。另记两笔只有重跑才看得见的账：
  // ① 3711（.project-workbench gap: 14px）虽与自动锚 3710 同规则，但 gap 属间距族、
  //    本就被 deadAnchor() 的候选条件排除，摘它不伤自动锚——候选测试实测摘后仍 1 条 = 3710。
  // ② 14px 离 §5.1 八档，这是 BD-38 之后第二笔连带的真刻度债（styles.css 101→100、全站 273→272）。
  ["批次 BD-39 还债（人工锚批也要有哨兵）：把本批摘掉的 styles.css .desktop-brand small 那条 letter-spacing: 0.06em 写回墓碑位置——胜者 editorial-studio.css:181 是 letter-spacing: 0.05em（更晚发射的静态表整条压住它），而这条已不在 13 条的账上。必须红，红因是「账外新增」。⚠ 选 letter-spacing 是因为它既不在间距棘轮也不在字号棘轮的属性集里，红因不会串到预算上；锚用「规则名 + 本批墓碑注释头」，因为 :507 那条 margin-top 也摘了，这条规则里能当锚的只剩注释头", () => mutate("src/styles.css", "  /* BD-39 摘死声明：letter-spacing: 0.06em", "  letter-spacing: 0.06em;\n  /* BD-39 摘死声明：letter-spacing: 0.06em"), /非颜色死声明账与实测不符/],
  ["批次 BD-39 还债·反向（与静态胜者逐字同值不该红）：给同一条 .desktop-brand small 写 **letter-spacing: 0.05em**——照抄胜者 editorial-studio.css:181 的原值。同键同值是冗余不是分叉，判红等于逼人在两份静态表里删掉一条在画的声明。这颗的值仍是从胜者那一行读出来的（BD-34 那条教训之后第六次这么做）。⚠ 顺带钉住本批真实发生的形状：.desktop-sidebar-card 的第二条规则被摘到**只剩注释**（padding: 12px 是它唯一的声明）——摘空一条规则不会绊倒任何判据，判据数的是声明不是规则", () => mutate("src/styles.css", "  /* BD-39 摘死声明：letter-spacing: 0.06em", "  letter-spacing: 0.05em;\n  /* BD-39 摘死声明：letter-spacing: 0.06em"), null],

  // ---- 批次 BD-40：块级胜者批——还掉 11 条 history-local.css + 1 条 outline-reorg.css ----
  // 这批的关键不是摘，是**先证反方向前提**：块级表只在宿主页面挂载时注入，判死要证
  // 「每个宿主都挂得上胜者表」，还债就是同一层前提反过来用。摘之前按判据同一张 import
  // 图逐条复核过：.card-board-card 的宿主只有 CardBoard.tsx（import outline-reorg.css）、
  // .history-page/-empty/-list/-item/-item-type/-item strong/small 的宿主只有 HistoryPage.tsx
  // （import history-local.css）。⚠ 这里有个差点踩空的形状：StatsPage.tsx 也渲染
  // .history-item-actions，词面 grep 会把它算进 .history-item 的宿主——但判据用的是
  // clsTokenRe（前后都有负向断言），history-item 后面紧跟 -a 就不匹配，那个类名是**另一个**
  // 选择器。它正是 BD-30 那次「判据默认 lazy 胜者总会挂载」被打穿、把判据改错的现场，
  // 所以这颗牙特意选 .card-board-card：宿主单一、够得着块级表，红因干净。
  // 锚冲突审计：摘前摘后各跑一次，77 条 needle 与 12 条候选零重叠。
  ["批次 BD-40 还债（块级胜者也要有哨兵）：把本批摘掉的 styles.css .card-board-card 那条 display: grid 写回墓碑位置——胜者 src/features/creation/outline/outline-reorg.css:103 是 display: flex（块级表在宿主挂载时运行时注入、排在全部静态表之后），而这条已不在 1 条的账上。必须红，红因是「账外新增」。⚠ 选 .card-board-card 是因为它的宿主只有 CardBoard.tsx 且该模块确实 import 了胜者表——挂载覆盖成立，判死才谈得上；换成 .history-item 就会撞上 StatsPage 那个词面陷阱（见上）。锚仍用「规则名 + 本批墓碑注释头」，display 不在间距/字号棘轮属性集里，红因不会串到预算", () => mutate("src/styles.css", ".card-board-card {\n  /* BD-40 摘死声明：display: grid", ".card-board-card {\n  display: grid;\n  /* BD-40 摘死声明：display: grid"), /非颜色死声明账与实测不符/],
  ["批次 BD-40 还债·反向（与块级胜者逐字同值不该红）：给 .card-board-card 写 **display: flex**——照抄胜者 outline-reorg.css:103 的原值。同键同值是冗余不是分叉，判红等于逼人在静态表与块级表之间删掉一条在画的声明。这颗的值是从胜者那一行读出来的（BD-34 那条教训之后第七次这么做）。⚠ 它顺带钉住挂载覆盖前提的另一头：这条冗余写在这里判据不会红，因为「谁发射得晚」对同值没有意义——前提失效时判据是**放过**而不是误杀，这正是 BD-30 修完之后该有的形状", () => mutate("src/styles.css", ".card-board-card {\n  /* BD-40 摘死声明：display: grid", ".card-board-card {\n  display: flex;\n  /* BD-40 摘死声明：display: grid"), null],

  // ---- 批次 BD-43：属性级挂载覆盖判据（新立的一条，八颗牙）----
  // 本批判据的形状是「属性只在块级表声明、而存在一条载得起宿主的载入路整条不带那张表」，
  // 账上 8 条全是 .cards-attachment-note 一族。它和 BD-31 那条并集口径是**两个口径**，
  // 区别恰好钉在 .history-modal 上（见下 ④／⑤ 两颗）——所以这颗新判据自己的退化也得有牙。
  // 备份清单已覆盖这几份：cards-local.css（R 批）、history-local.css（BD-30 批）、
  // writing-quick-reference.css（本批新加，⑦⑧ 两颗的宿主）、verify-design-tokens.mjs
  // （BD-41 批，①③④⑤ 四颗拿判据自己当宿主）。
  // ⚠ BD-44 换过三颗锚（①②③）：BD-43 那版把它们钉在**账上那条活缺陷**上，而 BD-44 把缺陷
  // 合法还掉了——钉在债上的牙，还债当天就哑（BD-41 为这件事把两颗死账牙从账上解绑，同一课）。
  // 新形状仍然逐条手工预演过：① 账里塞幻影行 → 失败 1 项「账上 1 条现在扫不到了」；
  // ② 单文件合成缺席 → 失败 1 项「新增 1 条…现存 1 条」，红因只在缺席账；
  // ③ 豁免改成永不放行 → 缺席暴涨 1584 条 + 自检⑤ + 自检⑥·加强 双双红。
  // BD-44 另加第九颗（还债不许悄悄退化）：钉的是**修法本身**而不是账——删掉宿主那条
  // import 就回到缺陷态，实测只有 1 项失败、「新增 8 条…（现存 8 条）」。账还清了它
  // 照样有牙，这正是①②③换锚时想要的性质；代价是它多备份了一个业务组件源文件。
  // 本批合计 9 颗，电池 276 → 277 用例（必须绿 89 不变——第九颗要求红）。
  ["批次 BD-43 立、BD-44 换锚·还债不改账（本账的 aStale 侧）：往守卫自己的 ABSENCE_LEDGER 里塞一条幻影行（bd44-phantom-host）——实测清单里没有它，账上却多了一条。必须红，红话是「账上 1 条现在扫不到了」，而且实测**只有这一项**失败（别的判据一概不跟着叫）。⚠ BD-43 原版这颗钉在「给 WritingDesk 加一条 import cards-local.css」上，那正好是 BD-44 的真修法，还完债账清空、这条注入就不再改变任何东西（牙哑）；现在改成直接改账，账空不空都测得出「账与实测必须逐字相同」这一头，同 BD-41 那两颗死账牙的手法", () => mutate("scripts/verify-design-tokens.mjs", "      const ABSENCE_LEDGER = new Set([\n      ]);", "      const ABSENCE_LEDGER = new Set([\n        \"src/styles.css\\t\\t.bd44-phantom-host\\tcolor\\t—\", // 电池注入的幻影行，验完立刻还原\n      ]);"), /账与实测不符/],
  ["批次 BD-43 立、BD-44 换锚·账外新增（本账的 aMiss 侧）：往 writing-quick-reference.css 注入一条 `.cards-attachment-note { text-decoration: underline; }`。那张表只有 WritingDesk 一条载入路带，而宿主 CardDynamicFields.tsx 有三条路（CardsPage / GlobalCardLibraryPage 都不带这张表）⇒ 判出一条**新的**真缺席、账上没有。必须红，实测「新增 1 条…（现存 1 条）」且只有这一项失败。⚠ 这颗比 BD-43 原版（往 cards-local 加 text-decoration）更强：它还顺带证明本判据真的在查「哪些载入路带这张表」，而不是只会盯 BD-43 那一处；BD-44 还清账之后，这条正是「新缺陷落进零账 = 即时红」的证据，同 BD-25 之后那些空表预算的立场", () => mutate("src/features/creation/editor/writing-quick-reference.css", ".writing-quick-context {", ".cards-attachment-note { text-decoration: underline; }\n\n.writing-quick-context {"), /账与实测不符/],
  ["批次 BD-43 立、BD-44 换锚·豁免塌了（误报侧最坏形状）：把「每条路都带表就放过」那行判据改成 `if (false)`——「存在一条不带表的路」当场变成「只要有一条不带就报」，本仓惯例（子组件的样式由挂载它的页面带进来）那几十处良性全被记成缺陷，实测缺席从 0 暴涨到 **1584 条**（账清零之后全是账外新增）。必须红，而且实测三处一起咬：账不符 + 自检⑤（.history-modal 不在「判过但放过」名单里了）+ 自检⑥·加强（那份名单归零）。这颗守的方向不是漏检而是「把良性全报出来」——报出来的东西没人看，等于判据自己把自己淹死", () => mutate("scripts/verify-design-tokens.mjs", "              if (!lacking.length) {\n", "              if (false) {\n"), /BD-44 自检⑤/],
  ["批次 BD-43 桶边断掉（**唯一一颗测「不表现为任何东西」的牙**）：删掉 RELRE 那条 `sj.add(r)`（桶文件的 `export * from` 不再算静态边）。⚠ 这颗的形状是先踩出来才写进牙的：断边之后本批 8 条一条不少、判决完全一样、守卫**全绿**——hostEntries 却从 231 个模块掉到 204 个（掉 27 个，全是只经 components/ui、stores/creation、inbox/components 三个桶文件被载起的模块），少掉那批宿主走的是 `if (!es.length) continue`「无人载得起 ⇒ 另判」那一支，判据对这 27 个模块整体闭嘴。所以这颗不能拿判决结果测，只能拿图上的事实测——自检⑦ 钉的正是三个「只能经桶边载起」的模块。必须红，三条各咬一次", () => mutate("scripts/verify-design-tokens.mjs", "        if (r && modSet.has(r)) sj.add(r);\n", ""), /BD-43 自检⑦/],
  ["批次 BD-43 动态边解析坏掉（入口枚举侧·自检⑥）：把「静态进 sj、动态进 dj」那行改成都进 sj——import() 目标从此不再算入口，载入入口只剩 main.tsx 一个（实测「只枚举出 1 个载入入口」），块级表被静态闭包全盘认下。这条坏掉时自检⑤⑥是**跟着一起瞎**的（k1、k2 都判不出来，两个都不判正是⑤⑥自己防的事），所以必须有独立的一道入口数量闸——判据退化不一定表现为错判，最坏的是不表现为任何东西，这就是 BD-34 那条教训在新判据上的重演", () => mutate("scripts/verify-design-tokens.mjs", "          (m[1] ? sj : dj).add(r);", "          sj.add(r);"), /BD-43 自检⑥/],
  ["批次 BD-43·反向（每条路都带表就是良性，不该红）：往 history-local.css 的 .history-modal 加一条 `caret-color: var(--text-muted);`——这条属性只在这张**块级表**声明、静态表里没有，按本批判据的形状它必须过「宿主有哪些载入路」这一关：三个对话框宿主只能经 HistoryPage 那条路进来，而 HistoryPage 自己 import 了这张表 ⇒ 每条路都带表 ⇒ 合法缺席为零、不许进账。守卫必须保持绿。这颗钉的是本判据与 BD-31 并集口径**唯一的分野**（那条口径下 .history-modal 是不覆盖的），口径要是漂回去，这颗当场红", () => mutate("src/features/creation/history/history-local.css", ".history-modal {\n", ".history-modal {\n  caret-color: var(--text-muted);\n"), null],
  ["批次 BD-43 新债落进有账文件（两本账不许互吞）：往 writing-quick-reference.css（间距预算 18 处那份）的 .writing-quick-context 注入一条 margin: 7px——7px 离刻度，预算 18→19 必须红，红因是 /间距刻度/ 那条超预算。⚠ 关键在「红因不许串」：这个文件同时是 BD-44 准备下手的宿主（那 8 条缺席的正解之一就在这张表里加后代规则），本批判据上线后旧棘轮的咬合力一点都不能少——缺席账把间距新债盖住，等于立一条新判据拆掉一条旧判据", () => mutate("src/features/creation/editor/writing-quick-reference.css", ".writing-quick-context {\n  display: flex;\n", ".writing-quick-context {\n  display: flex;\n  margin: 7px;\n"), /间距刻度/],
  ["批次 BD-43 还了债不降预算（上一颗的对称面）：把 editorial 那条离刻度 `padding-left: 14px;`（.desktop-titlebar 左槽，BD-55 永久记档）改成刻度上的 12px——离刻度从 4 掉到 3、缺席账一条不动，必须红（⚠ BD-78 换锚：原钉 writing-quick-reference 的 padding 15px 随 15px 桶清零消失，改钉这条永不搬家的记档；值账也会同时报差，那是同一笔债的两面），红因只能是「预算没跟着降」（实测全部失败项里就这一条），绝不许是缺席账。这颗和上一颗一起把「新判据上线不许把旧棘轮的咬合力吸走」钉死：两本账各数各的，谁也不许替谁沉默。⚠ BD-59 换锚：原钉 `gap: 7px;` 随 gap 7px 桶清零而消失（needle 找不到即 SKIP），BD-64 换锚 padding 11px、BD-67 再换锚（11px 桶整族清零）：改钉同文件仍挂在账上的 padding 15px（.writing-quick-context 右槽），改成刻度上的 16px", () => mutate("src/styles/editorial-studio.css", "  padding-left: 14px;", "  padding-left: 12px;"), /预算没跟着降/],
  ["批次 BD-44·还债不许悄悄退化（本账的 aMiss 侧反向）：删掉 CardDynamicFields.tsx 里那条 `import \"@/features/creation/cards/cards-local.css\"`——它就是 BD-44 还掉那 8 条缺席的正解，删掉之后「写作台那条路整路不带表」立刻复活。必须红，实测**只有 1 项**失败、红因正是「新增 8 条…（现存 8 条）」那一行。⚠ 这颗和①②③不同：那三颗换过锚（钉在活债上的牙，还债当天就哑），这颗钉的是**修法本身**——它测的不是账，是「这条 import 还在不在」，所以债还得越干净它越有牙：注入后判据回到 BD-44 前的缺陷态，账空 ⇒ 8 条全是账外新增。它同时是「样式归组件自己带，单一真相」这个决定的执行机制——以后谁把这行当冗余删了，守卫当场叫", () => mutate("src/features/creation/cards/components/CardDynamicFields.tsx", "import \"@/features/creation/cards/cards-local.css\";\n", ""), /新增 8 条/],

  // ---- 批次 BD-45：阴影**值**账（计数棘轮只会数数量，本批把口径补成逐条相等）----
  // 立项依据是三种实测的「数量不变的改版」，本批之前全都全绿：
  //   ① 已登记的债 alpha 改强 8 倍（.05→.40）：文件计数 4/4 不动 → EXIT=0；
  //   ② @apply 的 shadow-paper 换成 shadow-lift：计数 7/7 不动 → EXIT=0；
  //   ③ tailwind.config.ts 里 boxShadow.paper 的几何被改：**名下 7 条皮肤债同时改版**，
  //      而守卫压根没读过这份配置 → EXIT=0。③ 最坏——它是「一次改动、全站夜读皮肤改版、
  //      判据沉默」。下面五颗牙的形状全部先手工对守卫跑过：①②③④ 各自 HIT，⑤ 必须绿。
  // 备份清单里这三份（editorial-studio.css:14 / tailwind.config.ts:13 /
  // verify-design-tokens.mjs:100）早就在，本批不用补条目。
  // 本批 +5 颗（⑤ 要绿）：电池 277 → 282 用例，必须绿 89 → 90。
  ["批次 BD-45·值账还债不改账（aStale 侧）：往守卫自己的 SHADOW_VALUE_LEDGER 顶部塞一条幻影行（.bd45-phantom-host）——实测 12 条里没有它。必须红，红话是「账上 1 条已经扫不到」，实测**只有这一项**失败。手法同 BD-41 那两颗死账牙与 BD-43 的 ABSENCE 幻影牙：账由实测生成，改账不许绕过 dump 入口", () => mutate("scripts/verify-design-tokens.mjs", "  const SHADOW_VALUE_LEDGER = [\n", "  const SHADOW_VALUE_LEDGER = [\n        \"src/styles.css\\t.bd45-phantom-host\\t0 1px 2px rgba(9, 9, 9, 0.5)\", // 电池注入的幻影行，验完立刻还原\n"), /账上 1 条已经扫不到/],
  ["批次 BD-45·几何漂移（计数抓不到的那一类，本批判据的正面）：把 editorial-studio.css:1459 那条已登记的卡族投影 alpha 从 .05 改成 .40——投影强了 8 倍，**文件计数一个字没动**（4/4），旧棘轮在本批之前是 EXIT=0 的。必须红，实测「新增 1 条不在账上（现存 12 条）」且只有这一项失败。这颗就是本批立项的理由本身", () => mutate("src/styles/editorial-studio.css", "  box-shadow: 0 1px 2px rgba(34, 38, 48, 0.05);", "  box-shadow: 0 1px 2px rgba(34, 38, 48, 0.40);"), /新增 1 条不在账上/],
  ["批次 BD-45·爆炸半径最大的一支：改 tailwind.config.ts 里 boxShadow.paper 的几何（把令牌引用换回字面值、alpha .1→.45）。它一处都不碰 CSS 声明，改的却是**全部 7 个夜读皮肤**的实渲值——本批之前守卫压根没读这份配置，实测全绿。现在几何被读回来当 key 的一部分，必须红，实测「新增 7 条不在账上」（⚠ BD-87 换锚：paper 已指向 var(--shadow-paper)，原 needle 不存在；换成「指向改为字面值」仍钉同一件事——一处配置改全部皮肤）", () => mutate("tailwind.config.ts", "paper: \"var(--shadow-paper)\",", "paper: \"0 18px 50px rgba(34, 38, 48, 0.45)\","), /新增 7 条不在账上/],
  ["批次 BD-87·材质令牌值漂移（令牌化之后最隐蔽的一支）：把 tokens.css 的 --shadow-paper 从 alpha .1 改成 .45。7 个 @apply 的解析几何仍是 var(--shadow-paper)，计数账一处不动，但名下全部夜读皮肤同时改版——必须红，红因是材质令牌登记不一致（SHADOW_MATERIAL_TOKENS 逐字钉住值）。这颗把 BD-45 ③ 的咬合力从值账搬到令牌上：令牌是唯一真相，所以漂移只能在令牌定义处当场咬住", () => mutate("src/styles/tokens.css", "  --shadow-paper: 0 18px 50px rgba(34, 38, 48, 0.1);", "  --shadow-paper: 0 18px 50px rgba(34, 38, 48, 0.45);"), /材质令牌/],
  ["批次 BD-87·反向（令牌指向不存在的变量必须红，不能静默清零）：把 tailwind.config.ts 的 paper 指向 --shadow-paper-typo（tokens.css 无此定义）——产物会发射一条无效 var，浏览器算值阶段回落到无阴影，界面上影子直接消失而账却清零。这是批次 K 的幻影族在新判据上的形状，必须红且红因是「tokens.css 的晨校 :root 没有定义」", () => mutate("tailwind.config.ts", "paper: \"var(--shadow-paper)\",", "paper: \"var(--shadow-paper-typo)\","), /tokens\.css 的晨校 :root 没有定义/],
  ["批次 BD-88·声明侧材质令牌值漂移：把 tokens.css 的 --shadow-control-thumb 从 alpha .22 改成 .45。滑块拇指的声明已是 var(--shadow-control-thumb)，计数账不动，但控件接触影变强一倍——必须红，红因是材质令牌登记不一致。这颗与 BD-87 的 @apply 值钉子对称：两条消费路径（@apply / 声明）都必须在同一张登记表上被逐字核对", () => mutate("src/styles/tokens.css", "  --shadow-control-thumb: 0 1px 4px rgba(15, 20, 28, 0.22);", "  --shadow-control-thumb: 0 1px 4px rgba(15, 20, 28, 0.45);"), /材质令牌/],
  ["批次 BD-88·声明侧幻影：把 settings-controls.css 的拇指声明指向 --shadow-control-thumb-typo（tokens.css 无此定义）——浏览器算值阶段回落无阴影，旋钮的接触影直接消失而账却清零。必须红，红因是「tokens.css 的晨校 :root 没有定义」", () => mutate("src/features/settings/settings-controls.css", "  box-shadow: var(--shadow-control-thumb);", "  box-shadow: var(--shadow-control-thumb-typo);"), /tokens\.css 的晨校 :root 没有定义/],
  ["批次 BD-45·两条口径必须同源（值账自己的退化）：删掉 CSS 分支那行 debtVals.push（`n += 1` 留着）——计数照常、值账少记 4 条。必须红，而且实测**两项**一起咬：先「计数账认出 12 处债，值账只记到 8 条——两条口径不同源」，再「账上 4 条已经扫不到」。这道条数闸是本批判据的 BD-43 自检⑦ 那一档：以后谁新加一条判债分支忘了记账，那条债在值账上就隐形了，而那正是「不表现为任何东西」的退化形状", () => mutate("scripts/verify-design-tokens.mjs", "        debtVals.push(`${rel}\\t${d.parent.selector.replace(/\\s+/g, \" \").trim()}\\t${v}`);\n", ""), /两条口径不同源/],
  ["批次 BD-45·反向（值账不许变成行序账）：把账里相邻两行（卡族分组投影 / .cards-board-card:hover）的顺序颠倒——两条都在实测里、一条不多一条不少。守卫必须保持**绿**：账与实测各自动 sort 后逐字比，本判据只核「集合相同」，不核「谁写在前面」。这颗防的是判据长出一条没人要的「行序约定」——重钉时 dump 顺序一变就逼人改源码，那属于判据自己给自己加戏。⚠ BD-87 换锚：原钉的 reader-bg-amber/beans 两行已随材质令牌离账，改钉仍留在值账上的两条 editorial 行", () => mutate("scripts/verify-design-tokens.mjs", "        \"src/styles/editorial-studio.css\\t.cards-board-card, .background-card, .cards-detail-card, .history-empty\\t0 1px 2px rgba(34, 38, 48, 0.05)\",\n        \"src/styles/editorial-studio.css\\t.cards-board-card:hover\\t0 6px 16px rgba(34, 38, 48, 0.09)\",\n", "        \"src/styles/editorial-studio.css\\t.cards-board-card:hover\\t0 6px 16px rgba(34, 38, 48, 0.09)\",\n        \"src/styles/editorial-studio.css\\t.cards-board-card, .background-card, .cards-detail-card, .history-empty\\t0 1px 2px rgba(34, 38, 48, 0.05)\",\n"), null],

  // ---- 批次 BD-46：间距**值**账（BD-45 那套手法里唯一没被间距直接照搬的是账的形状）----
  // 立项依据同样是实测的「数量不变的改版」，本批之前三种漂移在间距棘轮上全绿：
  //   ① writing-quick-reference.css 的 `gap: 7px` → 15px：文件计数 18/18 不动 → EXIT=0；
  //   ② 同一处 → 11px：同上 → EXIT=0；
  //   ③ 反向对照：→ 8px（真还债）当场红——说明旧的计数账一直管用的是「数量」这一头，
  //      而「这一处债长什么样」从头到尾没人认。
  // ⚠ 形状没有照抄 BD-45 的逐声明账：间距债在 BD-46 立项时实测 271 处 / 20 文件，逐条钉进守卫会把判据
  //   撑成没人读的文件；实测压缩成「文件 ⑉ 原属性 ⑉ 折算 px ⑉ 处数」是 136 桶（此后每还一批债就降：BD-47 → 133、BD-48 → 131、BD-49 → 124、BD-50 → 121、BD-51 → 117、BD-52 → 114、BD-53 → 110、BD-54 → 108、BD-55 → 105、BD-56 → 104、BD-57 → 99、BD-58 → 95、BD-59 → 90）。压缩后
  //   判据与逐条账在「桶成员」「桶内计数」两个方向等价（下面 ②③ 两颗分别咬这两个方向），
  //   多出来的性质是**换写法不换像素不再红**（⑦ 那颗必须绿是这条设计的验收，不是附赠）。
  // 备份清单里这三份（writing-quick-reference.css:103 / LibraryPage.tsx:31 /
  // verify-design-tokens.mjs:100）早就在，本批不用补条目。
  // 本批 +8 颗（⑦⑧ 要绿）：电池 282 → 290 用例，必须绿 90 → 92。
  ["批次 BD-46·还债不改账（aStale 侧）：往守卫自己的 SPACING_VALUE_LEDGER 顶部塞一条幻影桶（padding 99px）——实测账上没有它。必须红，红话是「账上 1 桶已经扫不到」，实测**只有这一项**失败。手法同 BD-45 阴影那颗与 BD-41/BD-43 的死账牙：账由实测生成（唯一入口 DESIGN_TOKEN_DUMP_SPACING_VALUES=1），改账不许绕过它", () => mutate("scripts/verify-design-tokens.mjs", "  const SPACING_VALUE_LEDGER = [\n", "  const SPACING_VALUE_LEDGER = [\n        \"src/styles.css\\tpadding\\t99px\\t1\", // 电池注入的幻影桶，验完立刻还原\n"), /账上 1 桶已经扫不到/],
  ["批次 BD-46·像素漂移（计数抓不到的那一类，本批立项理由本身）：把 editorial 已登记的 `padding-left: 14px` 改成 `padding-left: 19px`——内衬翻了 3 倍，**文件计数一个字没动**（3/3），本批之前实测 EXIT=0。必须红，实测「新增 1 桶不在账上」（19px 新桶）且只有值账这一项失败。⚠ BD-59 换锚 gap 7px、BD-64 换锚 padding 11px、BD-67 再换锚（11px 桶清零）：改钉仍是离档债的 padding 15px", () => mutate("src/styles/editorial-studio.css", "  padding-left: 14px;", "  padding-left: 19px;"), /新增 \d+ 桶不在账上/],
  ["批次 BD-46·总数也不变的桶漂移（值账独有价值，比计数账更窄的一格）：把 .desktop-titlebar 那条 `padding-left: 14px` 的 14 改成 13——两条都离刻度（14 与 13），所以**文件计数 3/3、全站总数也不动**，BD-45 之前的判据连「有东西变了」都感觉不到。现在 15px 桶少一笔、13px 桶多一笔，必须红，实测**只有这一项**失败。这颗和上一颗分工不同：② 换的是值，这颗换的是**桶的精确组成**（直方图把计数写进 key，就是为了这里不脱敏）", () => mutate("src/styles/editorial-studio.css", "  padding-left: 14px;", "  padding-left: 13px;"), /间距值账与实测不符/],
  ["批次 BD-46·属性漂移（同文件同像素换个属性也是改版）：把那条 `padding-left: 14px;` 写成 `margin-left: 14px;`——像素一个没变、总数没变，但内衬变成了外边距，版面必然重排。BD-46 裁定 key 用**原属性**而非家族归一（padding-top 不折成 padding），这颗就是那条裁定的验收：必须红。⚠ 实测报「新增 1 桶（margin 15px ⑉ 1）」+「账上 1 桶扫不到（padding 15px ⑉ 1）」——桶计数与桶成员同时漂，判据照样一次抓全。⚠ BD-59 换锚：原钉 `gap: 7px;` 随 gap 7px 桶清零而消失（SKIP）；⚠ BD-72 再换锚：第二轮钉的 `margin: 9px 12px 0;` 随 9px 桶清零而消失（SKIP，电池计 1 个无效），改钉本文件仍在债上的 `.writing-quick-context` 那条 padding 15px——它与上一颗、下一颗共用同一根 needle，但换的是属性而不是值，三颗各自咬不同的缝", () => mutate("src/styles/editorial-studio.css", "  padding-left: 14px;", "  margin-left: 14px;"), /间距值账与实测不符/],
  ["批次 BD-46·TSX 侧值漂移（工具类那一支不许只数不认值）：把 InboxQuickInput 那条 `pr-14`（56px，离刻度、已登记）改成 `pr-13`（52px，同样离刻度）——文件计数 1/1 不动、桶总数不动，只有缩进从 56px 变成 52px。本批之前实测 EXIT=0；现在必须红，实测「新增 1 桶不在账上」且只有这一项失败。这颗钉的是 key 里那个缩写属性名（pr 而非 padding-right）：工具类侧的属性口径与 CSS 侧不同，一旦哪天有人把它归一成 padding-right，这颗会红在「桶成员」上", () => mutate("src/features/creation/inbox/components/InboxQuickInput.tsx", "w-full pr-14", "w-full pr-13"), /新增 1 桶不在账上/],
  ["批次 BD-46·两条口径必须同源（值账自己的退化）：删掉 CSS 分支那行 spVal（`n += 1` 留着）——计数照常 162、值账只剩 TSX 那 5 处。必须红，而且实测**两项**一起咬：先「计数账认出 162 处债，值账只记到 5 处——两条口径不同源」，再「账上 85 桶已经扫不到」（BD-59 后实测，数字随还债漂移，本批 162/85 由本批变异复跑坐实）。这把尺子量的正是它自己：以后谁新加一条判债分支忘了记账，那笔债在值账上就隐形了，而「不表现为任何东西」是 BD-34/BD-43 反复付过学费的那类退化", () => mutate("scripts/verify-design-tokens.mjs", "            n += 1;\n            spVal(rel, d.prop, px);", "            n += 1;"), /两条口径不同源/],
  ["批次 BD-46·反向（换写法不换像素必须保持绿，本批判据的形状不是「只准写 px」）：把 .desktop-titlebar 那条 `padding-left: 14px` 改成 `0.875rem`——折算同为 14px15px、桶计数不变，守卫必须**绿**。⚠ 这颗不是「顺手不该红」：本仓 rem 折算是名义 16px 口径（见间距棘轮 ② 与字号账里 appFontScale 那段），判据拿折算后的 px 当 key 的一部分，才换来这一条零像素合法迁移不被咬。BD-45 那种带选择器/几何的逐条账同样绿，但**带行号的**逐条账会当场把它咬红——这颗就是当初没照抄那条形状的第二条理由", () => mutate("src/styles/editorial-studio.css", "  padding-left: 14px;", "  padding-left: 0.875rem;"), null],
  ["批次 BD-46·反向（值账不许变成行序账，同 BD-45 那颗）：把账里相邻两桶（cards-local.css margin 9px ⑉ 2 / padding 13px ⑉ 2）顺序颠倒——两桶都在实测里、计数一字不差。守卫必须保持**绿**：dump 的输出顺序不是约定，重钉时顺序一变不许逼人改源码。⚠ BD-58 换锚到 gap 7px / margin 14px；BD-59 换锚到 margin 14px / margin 9px；BD-62 再换锚——margin 14px 桶随本批清零（那一笔消失即 SKIP），改钉现存相邻两桶 margin 9px ⑉ 2 / padding 13px ⑉ 2（两条都是本文件里长期不动的离刻度债，不随间距桶批次搬家）；BD-68 再换锚——padding 13px 桶随 13px 桶整族清零而消失（那一笔消失即 SKIP），改钉 editorial-studio 的两条 `padding-left`（14px ⑉ 1 是 BD-55 立下的**永久记档**条、与左栏印章列逐像素绑定，44px ⑉ 1 是它的紧邻行）；BD-75 再换锚——44px 桶整桶清零（那一笔消失即 SKIP），改钉排序后紧邻的 `padding-left 14px`（BD-55 记档）与 `padding-top 28px`（BD-71 记档）两条：都是永久记档，不随任何间距桶批次搬家", () => mutate("scripts/verify-design-tokens.mjs", "        \"src/styles/editorial-studio.css\\tpadding-left\\t14px\\t1\",\n        \"src/styles/editorial-studio.css\\tpadding-top\\t28px\\t1\",\n", "        \"src/styles/editorial-studio.css\\tpadding-top\\t28px\\t1\",\n        \"src/styles/editorial-studio.css\\tpadding-left\\t14px\\t1\",\n"), null],

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
