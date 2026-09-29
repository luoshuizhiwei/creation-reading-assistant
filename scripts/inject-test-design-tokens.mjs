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
  "src/features/creation/editor/scene-radar.css"
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
  ["正文色调到不可读", () => mutate("src/styles/tokens.css", "  --text-primary: #20242a;", "  --text-primary: #9aa2ac;"), /正文\/画布 对比度/]
];

let bad = 0;
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
console.log(`\n注入测试：${cases.length} 个用例，${bad === 0 ? "全部使守卫变红" : bad + " 个无效"}`);
process.exit(bad === 0 ? 0 : 1);
