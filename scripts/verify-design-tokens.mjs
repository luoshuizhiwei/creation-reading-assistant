import { readFileSync, readdirSync } from "node:fs";
import path from "node:path";
import postcss from "postcss";

/**
 * 设计令牌与按钮不变量守卫（重设计规格 §5 / §6 第 5 步）。
 *
 * 背景：这个仓库的设计系统是「写了一半就停了」——语义令牌建好了，但间距、字号、
 * 行高、层级、动效五类压根没有定义，于是 6100 行样式表里长出 19 种 gap、25+ 种圆角、
 * 63 个唯一阴影值。更麻烦的是有两类缺陷不会被任何契约测试或单元测试发现：
 *
 *  1) 引用了从未定义的令牌（D-2：8 个样式表共 46 处用 --font-sans/serif/mono，
 *     tokens.css 里没有 → 浏览器静默回退，界面照常渲染，没人报错）；
 *  2) 颜色对比度不足（D-1 的同类：主按钮前景写死 text-white，夜校主题主色提亮成
 *     浅蓝 #7fa5d9 后，白字只有 2.53:1，肉眼在截图里也看不出错）。
 *
 * 因此这里既做「定义存在性」静态断言，也真的解析十六进制值算 WCAG 对比度。
 * 全部断言都做过注入验证：把 needle 删掉或改坏，本脚本必须变红。
 */

const root = path.resolve(import.meta.dirname, "..");
const TAG = "[verify-design-tokens]";

let failures = 0;
function fail(message) {
  console.error(`${TAG} ${message}`);
  failures += 1;
}

function read(...parts) {
  return readFileSync(path.join(root, ...parts), "utf8");
}

/**
 * 剥掉注释后的源码。
 * 本守卫靠匹配「代码里不该出现的写法」来防回归（如 shadow-[var(--focus-ring)]、Inter），
 * 而这些反例正写在我们的注释里解释为什么不能用它们。不剥注释，守卫会自己误报。
 */
function stripComments(source) {
  return source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^\s*\/\/.*$/gm, "");
}

/**
 * 同上，但把注释里的字符换成空格、保留换行。
 * 需要 postcss 解析并拿「源文件行号」的场合必须用这个：stripComments 会把
 * 块注释里的换行一起吃掉，之后所有规则的 position.line 整体往上漂，
 * 而报错的价值全在行号能直接跳过去。
 */
function preserveNewlines(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, " "))
    .replace(/^\s*\/\/.*$/gm, (m) => " ".repeat(m.length));
}

const tokensCss = stripComments(read("src", "styles", "tokens.css"));
const uiTsx = stripComments(read("src", "components", "ui.tsx"));
const tailwindConfig = stripComments(read("tailwind.config.ts"));
const editorialCss = stripComments(read("src", "styles", "editorial-studio.css"));

/* ---------------------------------------------------------------- 令牌定义 */

const tokenGroups = {
  "间距 §5.1": ["--sp-0", "--sp-1", "--sp-2", "--sp-3", "--sp-4", "--sp-5", "--sp-6", "--sp-7", "--sp-8", "--sp-9", "--sp-10", "--sp-11"],
  "圆角 §5.2": ["--radius-1", "--radius-2", "--radius-3", "--radius-full"],
  "阴影 §5.3": ["--shadow-1", "--shadow-2", "--shadow-3", "--shadow-4"],
  "层级 §5.4": ["--z-dock", "--z-sticky", "--z-overlay", "--z-dialog", "--z-menu", "--z-toast"],
  "动效 §5.4": ["--dur-1", "--dur-2", "--dur-3", "--ease-standard", "--ease-enter", "--ease-exit"],
  "字阶 §3.2": ["--text-11", "--text-12", "--text-13", "--text-14", "--text-16", "--text-20", "--text-24", "--text-32"],
  "行高 §3.2": ["--leading-11", "--leading-12", "--leading-13", "--leading-14", "--leading-16", "--leading-20", "--leading-24", "--leading-32"],
  "控件几何 §2": ["--control-h-sm", "--control-h-md", "--control-h-lg", "--focus-ring", "--fg-on-solid"]
};

const lightBlock = tokensCss.slice(0, tokensCss.indexOf(':root[data-app-theme="dark"]'));
for (const [label, names] of Object.entries(tokenGroups)) {
  const missing = names.filter((n) => !new RegExp(`${n}:\\s*\\S`).test(lightBlock));
  if (missing.length) fail(`${label} 缺少令牌定义：${missing.join(", ")}`);
}

// 行高必须无单位：px 行高在侧栏 dock 的界面缩放下会错位
const unitLeading = [...lightBlock.matchAll(/--leading-\d+:\s*([^;]+)/g)].filter((m) => /(px|pt|rem|em)$/.test(m[1].trim()));
if (unitLeading.length) {
  fail(`行高令牌必须无单位（缩放时固定 px 会错位）：${unitLeading.map((m) => m[0].trim()).join("、")}`);
}

// D-2 回归守卫：这三个名字被 8 个样式表引用，一旦定义消失就是静默失效
for (const [alias, authority] of [["--font-sans", "--font-ui"], ["--font-serif", "--font-content"], ["--font-mono", "--font-data"]]) {
  const rule = new RegExp(`${alias}:\\s*var\\(${authority}\\)`);
  if (!rule.test(lightBlock)) fail(`D-2 回归：${alias} 应定义为 var(${authority})，供 editorial-studio.css 等处的引用解析`);
}

/* ------------------------------------------------------- Tailwind 字体单一真相 */

if (/\bInter\b/.test(tailwindConfig)) {
  fail("tailwind.config.ts 仍硬写 Inter：包内不分发字体文件，Windows 上拿不到，会静默回退");
}
for (const [key, token] of [["sans", "--font-ui"], ["serif", "--font-content"], ["mono", "--font-data"]]) {
  const block = new RegExp(`fontFamily:[\\s\\S]*?${key}:\\s*\\[\\s*"var\\(${token}\\)"`);
  if (!block.test(tailwindConfig)) fail(`tailwind.config.ts 的 fontFamily.${key} 应指向 var(${token})，令牌是唯一真相`);
}

/* -------------------------------------------------------------- D-1 回归守卫 */

for (const [label, source] of [["src/styles.css", stripComments(read("src", "styles.css"))], ["tokens.css", tokensCss], ["ui.tsx", uiTsx], ["editorial-studio.css", editorialCss]]) {
  if (/184,\s*64,\s*26/.test(source)) {
    fail(`${label} 残留旧铜色阴影 rgba(184,64,26,...)：D-1（蓝按钮打橙光）回归`);
  }
}

// 按钮一律无阴影、无位移、无缩放（规格 §2.4 / §2.5）
const variantsStart = uiTsx.indexOf("const variants");
const sizeClassStart = uiTsx.indexOf("sizeClass", variantsStart);
const buttonVariantBlock = variantsStart >= 0 && sizeClassStart > variantsStart ? uiTsx.slice(variantsStart, sizeClassStart) : "";
if (!buttonVariantBlock) {
  fail("找不到 Button 的 variants 定义块，守卫无法判定按钮样式");
} else {
  for (const bad of [/shadow-/, /translate-/, /scale-\[/]) {
    if (bad.test(buttonVariantBlock)) fail(`Button variant 含 ${bad.source}：按钮不做阴影与几何位移`);
  }
}

/* ------------------------------------------------- 焦点环：Tailwind 的两条坑 */

// shadow-[var(--focus-ring)] 会被 Tailwind 解析成阴影 *颜色*，
// 只产出 --tw-shadow-color 而没有 box-shadow 声明 → 焦点环根本不会出现。
if (/shadow-\[var\(--focus-ring\)\]/.test(uiTsx)) {
  fail("焦点环写成 shadow-[var(--focus-ring)] 会被 Tailwind 当成阴影颜色，不产出 box-shadow；应使用 [box-shadow:var(--focus-ring)]");
}
if (!/focus-visible:\[box-shadow:var\(--focus-ring\)\]/.test(uiTsx)) {
  fail("Button 缺少键盘焦点环 focus-visible:[box-shadow:var(--focus-ring)]");
}

// Tailwind 无法给 var() 套 alpha 修饰符，整条声明会被静默丢弃。
// 形如 border-[color:var(--x)]/45 —— 注意斜杠后没有闭合中括号。
const alphaOnVar = /(?:bg|text|border)-\[color:var\(--[\w-]+\)\]\/\d+/.test(uiTsx);
if (alphaOnVar) {
  fail("对 var() 色值使用 /alpha 修饰符：Tailwind 会整条丢弃该声明（边框/背景会消失），改用 color-mix()");
}

/* -------------------------------------------------------------- Button 能力面 */

for (const variant of ["primary", "tonal", "outline", "ghost", "icon", "danger-outline", "danger-filled"]) {
  if (!new RegExp(`"?'?${variant}"?'?\\s*:`).test(uiTsx)) fail(`Button 缺少 variant：${variant}`);
}
for (const size of ["sm", "md", "lg"]) {
  if (!new RegExp(`"?'?${size}"?'?\\s*:\\s*"h-\\d`).test(uiTsx)) fail(`Button 缺少尺寸：${size}`);
}
if (!/loading/.test(uiTsx) || !/aria-busy/.test(uiTsx)) fail("Button 缺少 loading 态（须同时置 aria-busy）");
if (!/disabled:opacity-55/.test(uiTsx)) fail("disabled 透明度应统一为 .55（旧代码有 0.45/0.5/0.4 三种）");

// 分层所有权：同一属性只能有一层拥有。Tailwind 把同类工具按「值从小到大」发射，
// 所以 base 里的 px-3 会盖掉尺寸层的 px-2.5 和 icon 的 p-0 —— 谁写在后面谁赢，
// 与类名字符串顺序无关。内边距必须只属于 sizes / icon 层。
const baseClassSource = /const base =\s*\n?\s*"([^"]+)"/.exec(uiTsx);
if (!baseClassSource) fail("读不到 Button 的 base 类串，分层检查无法执行");
else {
  const padding = (baseClassSource[1].match(/(?:^|\s)(?:p|px|py|pl|pr)-[\w.]+/g) ?? []).map((s) => s.trim());
  if (padding.length > 0) {
    fail(`Button 的 base 不得包含内边距 ${padding.join(", ")}：Tailwind 按值大小发射同类工具，` +
      `base 的 px-* 会压掉尺寸层（sm/lg）和 icon 的 p-0，导致 padding 静默失效`);
  }
}
// 每一档尺寸必须自带水平内边距，否则该档就继承了「无内边距」
for (const size of ["sm", "md", "lg"]) {
  const entry = new RegExp(`"?${size}"?\\s*:\\s*"([^"]+)"`).exec(uiTsx);
  if (!entry) fail(`读不到尺寸 ${size} 的类串`);
  else if (!/(?:^|\s)px-/.test(entry[1])) fail(`尺寸 ${size} 必须自己声明 px-*（base 已不再兜底内边距）`);
}

// 危险色必须走 --proof-mark，不能复用主操作色。
// 必须逐个 variant 检查：如果只看 danger-outline 到 danger-filled 这一整段，
// 把 danger-filled 换成 bg-copper 时 danger-outline 里的 --proof-mark 仍然存在，
// 整段检查会照样通过（注入测试实测出来的盲区）。
const VARIANT_NAMES = ["primary", "tonal", "outline", "ghost", "icon", "danger-outline", "danger-filled"];

/**
 * 取出某个 variant 的赋值文本（不含键名），切到下一个 variant 键或对象结束为止，
 * 这样每个 variant 单独成段——整段检查会被相邻键里的同类令牌蒙混过关。
 * 键名可能带引号（danger-outline 必须带），也可能是不带引号的简写（tonal）。
 */
function variantValue(name) {
  const keyRe = new RegExp(`^\\s*"?${name}"?\\s*:`, "m");
  const m = keyRe.exec(uiTsx.slice(variantsStart));
  if (!m) return null;
  const from = variantsStart + m.index + m[0].length;
  const rest = uiTsx.slice(from);
  const nextKey = rest.search(new RegExp(`^\\s*"?(?:${VARIANT_NAMES.join("|")})"?\\s*:`, "m"));
  const objectEnd = rest.search(/^\s*\};/m);
  const cuts = [nextKey, objectEnd].filter((i) => i > 0);
  return rest.slice(0, cuts.length ? Math.min(...cuts) : rest.length);
}

/** 只保留基础态，去掉 hover: 前缀的子句 */
function baseOnly(value) {
  return value.split(/\s+/).filter((t) => t && !t.startsWith("hover:")).join(" ");
}

for (const name of ["danger-outline", "danger-filled"]) {
  const value = variantValue(name);
  if (value === null) {
    fail(`找不到 Button 的 ${name} variant 定义`);
    continue;
  }
  // 只看基础态：hover 子句里也带 --proof-mark，若整段匹配，
  // 把基础填充换成 bg-copper 时守卫仍会通过（注入测试实测出来的第二个盲区）。
  const base = baseOnly(value);
  if (!/--proof-mark/.test(base)) fail(`${name} 的基础态必须使用 --proof-mark（校样红），不得复用主操作色：${base}`);
  if (/bg-copper(?!-soft)/.test(base)) fail(`${name} 不得用 bg-copper 作填充：危险操作要区别于主操作`);
}

/**
 * tonal 是「主色的浅底」，必须取 --action-tint。
 * 这里曾经踩过一次：写成 bg-copper-soft，而别名层把 --copper-soft 映射到
 * --proof-tint（校样红的底），配上 text-copper（印刷蓝）就是红底蓝字。
 * 对比度算出来 5.48:1 照样达标，肉眼在截图里也只觉得「底色偏粉」——
 * 属于数字查不出、只有语义能查出的错误，所以按名字禁止。
 */
const tonalValue = variantValue("tonal");
if (tonalValue === null) {
  fail("找不到 Button 的 tonal variant 定义");
} else {
  const tonalBase = baseOnly(tonalValue);
  if (!/--action-tint/.test(tonalBase)) fail(`tonal 的基础底色必须是 --action-tint（主色浅底）：${tonalBase}`);
  if (/copper-soft|proof-tint/.test(tonalBase)) fail("tonal 底色取到了校样红一族（--copper-soft 别名 = --proof-tint），会是红底蓝字");
}

// 新组件代码一律不许再碰 --copper-soft 别名：它名字像主色，值是校样红。
if (/copper-soft/.test(uiTsx)) {
  fail("组件不应使用 copper-soft 别名：--copper-soft 在别名层映射到 --proof-tint（校样红），主色浅底请用 --action-tint");
}

// data-variant 保留调用方原值 + CSS 双排除：两者任一丢失都会让 quiet 首子按钮被套上主色渐变
if (!/data-variant=\{variant\}/.test(uiTsx)) {
  fail("data-variant 必须输出调用方传入的原始值（editorial-studio.css 的 :not([data-variant=...]) 依赖它）");
}
const quietExclusions = (editorialCss.match(/:not\(\[data-variant="quiet"\]\):not\(\[data-variant="ghost"\]\)/g) ?? []).length;
if (quietExclusions < 4) fail(`editorial-studio.css 的 quiet/ghost 双排除只剩 ${quietExclusions} 处，应为 4 处：ghost 别名会漏进主色渐变`);

/* ------------------------------------------------------------ WCAG 对比度实算 */

function hexToRgb(hex) {
  const c = hex.replace("#", "");
  const full = c.length === 3 ? c.split("").map((x) => x + x).join("") : c;
  if (!/^[0-9a-fA-F]{6}$/.test(full)) return null;
  return [0, 2, 4].map((i) => parseInt(full.slice(i, i + 2), 16));
}

function luminance(hex) {
  const rgb = hexToRgb(hex);
  if (!rgb) return null;
  const [r, g, b] = rgb.map((v) => {
    const x = v / 255;
    return x <= 0.03928 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a, b) {
  const la = luminance(a);
  const lb = luminance(b);
  if (la === null || lb === null) return null;
  const hi = Math.max(la, lb);
  const lo = Math.min(la, lb);
  return (hi + 0.05) / (lo + 0.05);
}

function tokenValue(block, name) {
  const m = new RegExp(`${name}:\\s*(#[0-9a-fA-F]{3,6})`).exec(block);
  return m ? m[1] : null;
}

const darkBlock = tokensCss.slice(tokensCss.indexOf(':root[data-app-theme="dark"]'));

/**
 * Tailwind 的 alpha 通道别名（--rgb-*）必须与它对应的语义十六进制令牌逐字节相等。
 * 背景：tailwind.config.ts 把 text-paper-muted / bg-copper 等接到 rgb(var(--rgb-*) / alpha)，
 * 而 --text-secondary 等走 CSS 变量直接引用。同一个颜色有两条来源，改一处忘另一处
 * 就会静默分裂——本仓刚踩过：把 --text-secondary 从 #68707a 调到 #666d77（D-3），
 * 若 --rgb-muted 不跟着改，全站 144 处 text-paper-muted 仍是旧的欠对比色，
 * 而只有走 var(--text-muted) 的 52 处变了；对比度守卫拿的是十六进制令牌，
 * 只会报"达标"，完全看不出 Tailwind 那条路还没改。这条不变量把两路钉在一起。
 * 只列两主题都严格成对的名字。--rgb-moss↔--success 原本是「不是逐字节孪生」而排除，
 * 第 7 步（三）把晨校 --success 定成 #356f53（= 53 111 83）、夜校定成 #7fb899
 * （= 127 184 153），正是照 --rgb-moss 两主题既有值取的，于是这条从「不适用」变成
 * 「必须钉住」：绿也有两条来源了，改一处忘另一处就是上一段描述的那个 bug 重演。
 */
const RGB_TWINS = [
  ["--rgb-app", "--app-bg"],
  ["--rgb-surface", "--surface-1"],
  ["--rgb-subtle", "--surface-2"],
  ["--rgb-line", "--separator"],
  ["--rgb-ink", "--text-primary"],
  ["--rgb-muted", "--text-secondary"],
  ["--rgb-accent", "--action-primary"],
  ["--rgb-moss", "--success"]
];

function rgbTriplet(block, name) {
  const m = new RegExp(`${name}:\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)`).exec(block);
  return m ? [Number(m[1]), Number(m[2]), Number(m[3])] : null;
}

for (const [theme, block] of [["晨校", lightBlock], ["夜校", darkBlock]]) {
  for (const [rgbName, hexName] of RGB_TWINS) {
    const tri = rgbTriplet(block, rgbName);
    const hex = tokenValue(block, hexName);
    if (!tri || !hex) continue; // 该主题没定义其一，交由令牌存在性检查负责
    const fromHex = hexToRgb(hex);
    if (!fromHex) continue;
    if (tri.join(",") !== fromHex.join(",")) {
      fail(`${theme} ${rgbName}=(${tri.join(" ")}) 与 ${hexName}=${hex}=(${fromHex.join(" ")}) 不一致：Tailwind alpha 别名与语义令牌分裂，改色必须两处同步`);
    }
  }
}

/**
 * 实色色块上的前景必须达到 AA 4.5:1（13px 按钮文字属普通字号）。
 * 这些是本轮建立的不变量，未达标即失败。
 */
const hardPairs = [
  ["晨校 primary", lightBlock, "--action-primary", "--fg-on-solid"],
  ["晨校 danger-filled", lightBlock, "--proof-mark", "--fg-on-solid"],
  ["夜校 primary", darkBlock, "--action-primary", "--fg-on-solid"],
  ["夜校 danger-filled", darkBlock, "--proof-mark", "--fg-on-solid"],
  ["晨校 正文/画布", lightBlock, "--text-primary", "--app-bg"],
  ["夜校 正文/画布", darkBlock, "--text-primary", "--app-bg"]
];

/**
 * D-3 / D-4：仓库既有的文字对比度不足，属颜色收敛（§6 第 7 步）范围，
 * 且改色值需要视觉确认，本轮不夹带，因此只报告不失败（未达标算 pending）。
 * 已达标的组别留在表内继续看守，避免以后改色又退回去。
 *
 * 底线取法：
 *  --text-secondary 承载设置项描述等正文，按 AA 普通文字 4.5:1 要求；
 *  --text-tertiary 承载角标、计数、时间戳（11–12px 弱提示）。
 *    严格讲 AA 对小字号正文同样要求 4.5:1，把它拉到 4.5 会让三级与二级难以区分，
 *    这里先按 3:1（大字号/装饰性下限）报告，是否统一到 4.5 留给设计决策。
 */
const pendingPairs = [
  ["晨校 次级文字", lightBlock, "--text-secondary", "--app-bg", 4.5],
  ["夜校 次级文字", darkBlock, "--text-secondary", "--app-bg", 4.5],
  ["晨校 三级文字", lightBlock, "--text-tertiary", "--app-bg", 3.0],
  ["夜校 三级文字", darkBlock, "--text-tertiary", "--app-bg", 3.0]
];

function ratioOf(fg, bg) {
  return contrast(fg, bg);
}

for (const [label, block, fgName, bgName] of hardPairs) {
  const fg = tokenValue(block, fgName);
  const bg = tokenValue(block, bgName);
  if (!fg || !bg) {
    fail(`${label}：取不到 ${fgName} 或 ${bgName} 的十六进制值，无法核对对比度`);
    continue;
  }
  const ratio = ratioOf(fg, bg);
  if (ratio === null) {
    fail(`${label}：${fg} / ${bg} 不是合法十六进制色值`);
    continue;
  }
  if (ratio < 4.5) fail(`${label} 对比度 ${ratio.toFixed(2)}:1，低于 WCAG AA 的 4.5:1（${fg} on ${bg}）`);
  else console.log(`${TAG} ✓ ${label} ${ratio.toFixed(2)}:1`);
}

let pending = 0;
for (const [label, block, fgName, bgName, floor] of pendingPairs) {
  const fg = tokenValue(block, fgName);
  const bg = tokenValue(block, bgName);
  if (!fg || !bg) continue;
  const ratio = ratioOf(fg, bg);
  if (ratio === null) continue;
  if (ratio < floor) {
    pending += 1;
    console.warn(`${TAG} [D-3/D-4 待收敛] ${label} ${ratio.toFixed(2)}:1 < ${floor}:1（${fg} on ${bg}）`);
  } else {
    console.log(`${TAG} ✓ ${label} ${ratio.toFixed(2)}:1（已从待收敛清单达标，可移出 pendingPairs）`);
  }
}
if (pending > 0) console.warn(`${TAG} 提示：${pending} 组文字对比度未达 AA，属 §6 第 7 步颜色收敛，需视觉确认后修改`);

/* -------------------------------------------- 后代选择器 × 已迁移组件 冲突检测 */

/**
 * 规格 §6 第 6 步的专用守卫。
 *
 * 页面常用 `.history-item-actions button { … }` 这类后代选择器给按钮上样式。
 * <Button> 渲染出来仍然是 <button>，会被同一条规则再次命中；而后代选择器的
 * 特异性 (0,1,1) 高于 Tailwind 工具类 (0,1,0)，组件自带的焦点环与配色会被压掉。
 * 这属于「测试全绿、界面坏掉」的静默失效——vitest 只看 DOM 与文案，不看层叠。
 *
 * 约定：<Button> 恒输出 data-variant，页面规则一律写成 button:not([data-variant])。
 * 这里检查同一目录内「已使用 <Button> 的页面」是否还留着未排除的后代按钮规则。
 * 用同目录作为「这份 CSS 属于这个页面」的判据（本仓惯例是 feature 局部 CSS
 * 与页面同目录，如 HistoryPage.tsx / history-local.css）。跨目录借用样式的
 * 情况（StatsPage 借全局 .history-item-actions）由全局样式表自身负责排除。
 */
function walk(dir, depth = 0) {
  if (depth > 8) return [];
  const out = [];
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) out.push(...walk(p, depth + 1));
    else out.push(p);
  }
  return out;
}

const allSource = walk(path.join(root, "src"));
const cssFiles = allSource.filter((p) => p.endsWith(".css"));
const tsxByDir = {};
for (const p of allSource.filter((x) => x.endsWith(".tsx"))) {
  if (p.includes(`${path.sep}__tests__${path.sep}`)) continue;
  if (/<Button[\s/>]/.test(stripComments(readFileSync(p, "utf8")))) (tsxByDir[path.dirname(p)] ??= []).push(p);
}

// 后代按钮规则：`.panel button` / `.actions > button` 这类命中裸 button 的选择器。
// 用 postcss 解析而不是拼正则，避免在 :not([data-variant]) 这种带括号的选择器上
// 做脆弱的字符串匹配。
//
// 判据有两层，缺一不可：
//   ① button 的「直接父级复合选择器」（PARENT）——不是选择器开头的类名。
//      `.creation-writing-hero .desktop-page-actions button` 的泄漏来自后者，
//      只看行首会整条漏掉。
//   ② 该父级里确实有「内部渲染过 <Button> 的类名」——只看「类名在已迁移文件里出现过」会
//      大量误报：`.history-tabs button` 服务的是共享 Tabs 内部的裸
//      <button role="tab">，它们本来就不该被组件样式接管。
//
// 盲区二（page 17 发现）：父级类名挂在类型选择器上时，`.inbox-detail div.mt-3.grid button`
// 这种写法会被「必须以 . 开头」的锚点整条漏掉。该族规则当时正好压着 InboxItemDetail
// 里的 4 组按钮，而它的图标 × 也被 (0,3,2) 捕获、自带工具类全部失效——即这条盲区
// 不是理论风险，是已经发生过的静默失效。
// 修法：不再要求父级以 `.` 开头，改成取 button 前面那一段复合选择器，
// 把里面所有类名都当候选锚点（div.mt-3.grid → mt-3 / grid）。
// 实测口径差：放宽前命中 8 条（全是冻结的 .desktop-page-actions 一族），
// 放宽后 18 条，新增 10 条全部落在 .inbox-detail div.mt-3.grid / .flex 这一族，
// 也就是本次迁移下线的那批规则——放宽后零误报，且这批规则从此被盯住。
// 为什么只看「直接父级」而不看整条选择器的祖先类名：祖先链上常见 mt-3 / flex / grid
// 这类工具类，任何已迁移文件只要某处 <Button> 放在 .mt-3 容器里就会命中，
// 会把大量根本碰不到迁移元素的规则报成违规。直接父级才是「这条规则会压到谁」的
// 准确判据，宿主类名也是按「该元素内部有 <Button>」记的，两者口径一致。
const BUTTON_COMBINATOR = String.raw`(?:[ \t]*[>+~][ \t]*|[ \t]+)`;
const BUTTON_PARENT = new RegExp(String.raw`([^\s>+~]+)` + BUTTON_COMBINATOR + String.raw`button\b`);
const CLASS_IN_COMPOUND = /\.[\w-]+/g;

/**
 * 结构化解析 JSX，返回「内部真的渲染了 <Button> 的那些元素的类名集合」。
 *
 * 只用「类名在文件里出现过」当判据会大量误报：一条 `.foo button` 规则只有在
 * foo 这个容器里确实出现了 <Button> 时才会压到组件样式。这里做一次带栈的
 * 标签扫描：逐个元素记录它的类名与内部区间，再判断区间里有没有 <Button。
 */
function classesHostingButton(source) {
  const tokens = new Set();
  const src = source;
  let i = 0;
  /** 栈元素：{ name, classes, start } */
  const stack = [];

  const skipString = (from) => {
    const q = src[from];
    let j = from + 1;
    while (j < src.length && src[j] !== q) j += src[j] === "\\" ? 2 : 1;
    return j;
  };

  while (i < src.length) {
    if (src[i] !== "<") {
      i += 1;
      continue;
    }
    if (src.startsWith("<!--", i)) {
      i = src.indexOf("-->", i) + 3;
      continue;
    }
    if (src.startsWith("</", i)) {
      const gt = src.indexOf(">", i);
      const name = src.slice(i + 2, gt).trim();
      for (let k = stack.length - 1; k >= 0; k--) {
        if (stack[k].name === name) {
          const el = stack.splice(k)[0];
          if (/<Button[\s/>]/.test(src.slice(el.start, gt))) for (const c of el.classes) tokens.add(c);
          break;
        }
      }
      i = gt + 1;
      continue;
    }
    // 开标签：扫到配对的 >，尊重 {} 与字符串
    let depth = 0;
    let j = i + 1;
    for (; j < src.length; j++) {
      const c = src[j];
      if (c === '"' || c === "'" || c === "`") {
        j = skipString(j);
        continue;
      }
      if (c === "{") depth++;
      else if (c === "}") depth--;
      else if (c === ">" && depth === 0) break;
    }
    const tag = src.slice(i, j + 1);
    const name = (/^<([A-Za-z][\w.-]*)/.exec(tag) || [])[1] ?? "";
    const cn = /className=(\{[\s\S]*?\}|"[^"]*"|'[^']*')/.exec(tag);
    const classes = cn
      ? cn[1]
          .replace(/^\{|^\^|"|'|\}`/g, " ")
          .replace(/\$\{[^}]*\}/g, " ")
          .split(/[^A-Za-z0-9_-]+/)
          .filter((x) => /^[a-z][\w-]*$/.test(x))
      : [];
    if (!tag.endsWith("/>")) stack.push({ name, classes, start: j + 1 });
    i = j + 1;
  }
  return tokens;
}

const allMigrated = Object.values(tsxByDir).flat();
const globalHostTokens = new Set();
for (const p of allMigrated) for (const c of classesHostingButton(readFileSync(p, "utf8"))) globalHostTokens.add(c);

// 全局样式表（main.tsx 里无条件加载，会漏到每个页面）与 feature 局部样式表同等对待：
// 见下面棘轮检查的说明，两者都用全量宿主类名做作用域。

/**
 * 遗留债务冻结清单（棘轮）：<Button> 系统落地前，全局「按钮容器」类规则本来就在
 * 用后代选择器接管页面里的按钮（.desktop-page-actions button 这一族）。给它们统一
 * 加 :not([data-variant]) 会当场改掉 4 个尚未迁移页面的外观，违背「一页一提交、
 * 每页零回归」的推进方式，所以先冻结为警告，迁移到哪个页面就把对应锚点删掉。
 *
 * 只有在清单之外的新增违规才会变红。
 */
const LEGACY_DESCENDANT_BUTTON = new Set([
  "src/styles/editorial-studio.css :: desktop-page-actions"
]);

// 排除判据必须是 :not([data-variant —— 带引号值的形式（:not([data-variant="quiet"])）
// 也算已排除；把 ] 写进 needle 会一条都匹配不上，等于检查失效。
const isExcluded = (sel) => sel.includes(":not([data-variant");

let legacyHits = 0;
const newHits = [];
// 作用域一律用全量宿主类名，不再按「CSS 与页面同目录」判归属。
// 本仓存在跨目录借用样式：scene-radar.css 由 editor/SceneRadar.tsx 引入，却被
// editor/desk/WritingDeskMargin.tsx 复用。按同目录判归属会漏检这类规则，而它一旦
// 命中已迁移页面，就会用 (0,1,1) 的特异性静默压掉组件自带的 (0,1,0) 样式——
// vitest 全绿、界面却坏掉。宁可扩大检查面，也不能留这个盲区。
for (const cssFile of cssFiles) {
  const rel = path.relative(root, cssFile).split(path.sep).join("/");
  const ast = postcss.parse(readFileSync(cssFile, "utf8"), { from: cssFile });
  ast.walkRules((rule) => {
    for (const part of rule.selector.split(",")) {
      const sel = part.trim();
      const parent = BUTTON_PARENT.exec(sel);
      if (!parent) continue;
      if (isExcluded(sel)) continue; // 已排除，安全
      // 直接父级复合选择器里的候选类名（div.mt-3.grid -> mt-3、grid）。
      // 没有任何「内部渲染了 <Button> 的元素」带这些类名 → 规则碰不到迁移后的元素。
      const candidates = parent[1].match(CLASS_IN_COMPOUND) ?? [];
      const anchor = candidates.map((c) => c.slice(1)).find((c) => globalHostTokens.has(c));
      if (!anchor) continue;
      if (LEGACY_DESCENDANT_BUTTON.has(`${rel} :: ${anchor}`)) legacyHits += 1;
      else newHits.push(`${rel}: ${sel}`);
    }
  });
}

if (newHits.length > 0) {
  fail(
    `后代按钮规则未排除已迁移元素：${newHits.length} 条，这些类名内部渲染了 <Button>，` +
      `组件样式会被后代选择器压掉（特异性 (0,1,1) 高于工具类 (0,1,0)）。` +
      `请写成 button:not([data-variant])。命中：${newHits.slice(0, 4).join(" / ")}`
  );
}
if (legacyHits > 0) {
  console.log(
    `${TAG} [第 6 步待迁移] 遗留按钮容器规则 ${legacyHits} 条仍在接管 <Button>（已冻结，迁移对应页面时收口）`
  );
}

/* ------------------------------------------------- 第 7 步：硬编码状态色必须归零 */

/**
 * 规格 §6 第 7 步的验收口径是「grep 计数断言归零」。这里比 grep 严四点：
 *   ① 注释不算违规（用 stripComments），但字面量一旦残留在规则里就红；
 *   ② 同族旧红一起封：#b42318/#a5281b/#e0917d/#a33a33/#bc3f2b 与 rgba(180,35,24,…)
 *      是同一支「校样红」在不同时期手抄出来的变体，只封 #c0392b 挡不住下一个人
 *      从这些里挑一个抄（注入用例里有专门一条验证这点）；
 *   ③ 警示琥珀同族（#b9770e/#8a5a16/#84672f/#9a641c）：第 7 步（三）补夜校
 *      --warning 时发现，本仓其实早选定了 #8a5a16 当警示文字色（editorial-studio.css
 *      里定义了 --warning-text），但 var(--warning-text) 引用数为 0，5 处一律手抄字面量。
 *      「令牌只写了晨校值」之所以长期没人发现，根因就在这批手抄上；
 *   ④ 封 var(--danger, #…) / var(--studio-seal, #…) 这种「假令牌名 + 字面量兜底」：
 *      --danger 全站从未定义，实际渲染的一直是字面量，看着走了令牌其实没有
 *      ——本轮要收的东西最隐蔽的形态（与 D-2 的未定义 --font-sans 同类）。
 * 唯一豁免：令牌自己的定义行。字面量允许出现在定义处（每支一次，由孪生不变量与
 * 主题成对不变量看着），散进选择器里就是债。
 */
const LEGACY_STATE_HEX = [
  // 校样红
  "c0392b", "b42318", "a5281b", "e0917d", "a33a33", "8a3a28", "9f342d", "bc3f2b",
  // 警示琥珀
  "b9770e", "8a5a16", "84672f", "9a641c"
];
// 形如 `--proof-mark: #ad4436` 的定义行先摘掉，避免把令牌自身的出处报成违规。
const DEFINITION_LINE = new RegExp(String.raw`--[a-zA-Z0-9-]+\s*:\s*#(?:${LEGACY_STATE_HEX.join("|")})\b`, "gi");
let legacyRed = 0;
const redWhere = [];
for (const cssFile of cssFiles) {
  const rel = path.relative(root, cssFile).split(path.sep).join("/");
  const src = stripComments(readFileSync(cssFile, "utf8")).replace(DEFINITION_LINE, "--token-def:");
  for (const m of src.matchAll(new RegExp(`#(?:${LEGACY_STATE_HEX.join("|")})\\b`, "gi"))) {
    legacyRed += 1;
    redWhere.push(`${rel}:#${m[1]}`);
  }
  if (/rgba\(\s*180\s*,\s*35\s*,\s*24/i.test(src)) {
    legacyRed += 1;
    redWhere.push(`${rel}:rgba(180,35,24)`);
  }
  // 只报「引用了未定义令牌且带字面量兜底」的假令牌写法，定义行本身不算。
  for (const m of src.matchAll(/var\(\s*(--(?:danger|studio-copper|studio-seal)(?:-soft)?)\s*,\s*#[0-9a-fA-F]{3,8}\s*\)/g)) {
    legacyRed += 1;
    redWhere.push(`${rel}:var(${m[1]},#字面量)`);
  }
}
if (legacyRed > 0) {
  fail(
    `第 7 步未收口：仍有 ${legacyRed} 处硬编码状态色。校样红用 var(--proof-mark) /` +
      ` color-mix(in srgb, var(--proof-mark) N%, …)，警示琥珀用 var(--warning)，` +
      `成功绿用 var(--success)，实色底上的文字用 var(--fg-on-solid)。` +
      `命中：${redWhere.slice(0, 6).join(" / ")}`
  );
}

/* ------------------------------------------------------ 主题色令牌成对性不变量 */

/**
 * 颜色类令牌必须在两个主题里各自定义。少一边不会报错，浏览器直接拿晨校的值画在
 * 夜校底上——本仓实测：--success #3e7a5e 在晨校画布 4.38:1，压到夜校画布只剩 3.52:1，
 * 越改越糊。第 7 步扫红时就撞见 --studio-copper 被引用两次却从未定义，全靠
 * var(…, #b4531f) 的字面量在渲染，所以把这条钉成不变量。
 * 名单取自令牌表本身（晨校有、夜校无的那些名字），只盯真正会分主题变的东西：
 * 几何/动效/字体/层级这类天生两主题通用的，用 THEME_SHARED 明确豁免。
 */
const THEME_SHARED = /^--(sp-|radius-|shadow-|z-|dur-|ease-|leading-|text-\d|font-|control-h-|focus-ring)/;
{
  const definedIn = (block) => {
    const out = new Map();
    for (const m of block.matchAll(/(--[a-z0-9-]+)\s*:\s*([^;]+);/g)) out.set(m[1], m[2].trim());
    return out;
  };
  const lightDefs = definedIn(lightBlock);
  const darkDefs = definedIn(darkBlock);
  // 「颜色值」= 十六进制 / rgb / hsl 字面量开头的定义。指向别的令牌的别名（var(--x)）
  // 跟着权威令牌走，不单独要求两主题各定义一次。
  const isColorValue = (v) => /^(#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\()/.test(v);
  /**
   * 棘轮：先冻结已知欠债，新增的立刻变红。还掉一个就从名单删一个。
   * （--success / --warning 曾在此列，第 7 步（三）补齐了夜校值后已移出；
   *  它们当年欠的账实测是：#3e7a5e 压到夜校画布 3.52:1、#a8742c 4.41:1。）
   */
  const FROZEN_SOLO_THEME = new Set([]);
  const missing = [...lightDefs]
    .filter(([, v]) => isColorValue(v))
    .map(([n]) => n)
    .filter((n) => !darkDefs.has(n) && !THEME_SHARED.test(n) && !FROZEN_SOLO_THEME.has(n));
  const frozen = [...lightDefs]
    .filter(([, v]) => isColorValue(v))
    .map(([n]) => n)
    .filter((n) => !darkDefs.has(n) && !THEME_SHARED.test(n) && FROZEN_SOLO_THEME.has(n));
  if (frozen.length) {
    console.log(`${TAG} [第 7 步待补] ${frozen.length} 个颜色令牌只有晨校值（已冻结）：${frozen.join("、")}`);
  }
  if (missing.length) {
    fail(
      `颜色令牌必须在夜校（data-app-theme="dark"）里也定义，否则深色主题会直接拿晨校值画在深色底上` +
        `（对比度崩掉且不报错）。缺：${missing.join("、")}`
    );
  }
}

/* --------------------------------------- 写死白墨不能压在会随主题翻转的实色底上 */

/**
 * 第 8 步批次 A 立的不变量。写死 color:#fff 本身不算错——错的是「底色会随主题翻转，
 * 而前景钉死白色」这一对组合：夜校把 --action-primary 提亮成 #7fa5d9、
 * --proof-mark 提亮成 #e07965、--success 提亮成 #7fb899，白字压上去分别只剩
 * 2.53 / 2.96 / 2.28:1。本仓曾有 14 处这种写法（第 7 步补夜校 --success 时
 * 撞出其中一处：向导完成圆点从 5.06 掉到 2.28）。
 * 判据必须成对看：只看「有没有 #fff」会漏，只看「底色令牌」会误报固定深色面
 * （bg-stone-800 的浮层工具条白字 17.49:1，本来就该是白的）。
 * 所以：同一条规则里既写死白墨、又把背景画成「两主题取值不同」的令牌色 → 红。
 */
const WHITE_INK = /^#(fff|ffffff|white)\b$/i;
{
  /**
   * 底色的真实值要把别名链算到底：本仓的别名层在 styles.css 的 `:root` 里
   * （--copper: var(--action-primary)、--studio-seal: var(--proof-mark) …），
   * 若只比对 tokens.css 里同名令牌的两侧取值，这批别名一律"看不见差异"，
   * 守卫就会对最常见的写法放行。所以先收集全站 `:root` / `:root[dark]` 的
   * 自定义属性定义，再逐层展开 var()。
   */
  const base = new Map();
  const dark = new Map();
  for (const cssFile of cssFiles) {
    let ast;
    try {
      ast = postcss.parse(stripComments(readFileSync(cssFile, "utf8")), { from: cssFile });
    } catch {
      continue;
    }
    ast.walkRules((rule) => {
      const sel = rule.selector.replace(/\s+/g, "");
      const isBase = sel === ":root";
      const isDark = sel === ':root[data-app-theme="dark"]';
      if (!isBase && !isDark) return;
      for (const decl of rule.nodes) {
        if (decl.type !== "decl" || !decl.prop.startsWith("--")) continue;
        (isDark ? dark : base).set(decl.prop, decl.value.trim());
      }
    });
  }
  const expand = (name, theme, depth = 0) => {
    const raw = (theme === "dark" ? dark.get(name) : undefined) ?? base.get(name);
    if (raw === undefined || depth > 6) return raw ?? null;
    return raw.replace(/var\(\s*(--[a-z0-9-]+)\s*(?:,[^)]*)?\)/g, (_, inner) => expand(inner, theme, depth + 1) ?? "");
  };
  const flipsByTheme = (value) => {
    const names = [...value.matchAll(/var\(\s*(--[a-z0-9-]+)/g)].map((m) => m[1]);
    return names.some((n) => {
      const l = expand(n, "light");
      const d = expand(n, "dark");
      return l !== null && d !== null && l !== d;
    });
  };
  /**
   * 判「会不会翻」还不够，还得判「这条声明到底生不生效」——批次 A 就栽过一次：
   * .desktop-brand-mark 顶部把 color:#fff 与渐变底配在一起，看起来正是本条不变量
   * 要抓的组合，可它的 background 在文件末尾被 `.desktop-titlebar-mark,
   * .desktop-brand-mark { background: var(--action-primary) }` 同特异性后置覆盖了，
   * 于是「把渐变端点从 82% 调到 88% 以救白字对比度」的那组数字算的是死声明，
   * 对渲染结果毫无影响。守卫若继续只看规则内部，就会把这类假修复判成合规，
   * 诱使人在死代码上返工。
   *
   * 因此不能按「规则」配对，要按「选择器 + 条件上下文」取每个属性的胜者再配对：
   * 同一个 .x 的 color 可能来自第 10 行的规则、background 来自第 900 行的规则，
   * 逐规则检查两边都看不见对方（真实漏报），反过来也可能把已被覆写的那条当数。
   * 胜者口径：先比 !important、再比源序；`background` 简写与 `background-color`
   * 归到同一个槽位，否则后写的 background-color 会压掉前面简写的底色却仍被算进来。
   * 局限：只同选择器文本内比较，跨选择器（`.a .b` 覆盖 `.b`）与跨文件载入顺序
   * 不建模——那需要完整的元素→选择器映射，代价远高于本条不变量的收益。
   */
  function effectiveInkBySelector(ast) {
    const slots = new Map();
    let rank = 0;
    ast.walkRules((rule) => {
      let p = rule.parent;
      const at = [];
      while (p && p.type !== "root") {
        if (p.name) at.push(p.name === "media" ? `@media ${p.params}` : `@${p.name}`);
        p = p.parent;
      }
      // 覆盖关系只在同一条件上下文内成立：@media 里的后置声明不算覆盖了外层，
      // 否则 @media(max-width:920px) 的窄屏规则会把外层声明全判成死的。
      const scope = at.reverse().join(" < ");
      for (const selRaw of rule.selectors) {
        const sel = selRaw.replace(/\s+/g, " ").trim();
        for (const decl of rule.nodes) {
          if (decl.type !== "decl") continue;
          const prop = decl.prop.toLowerCase();
          const slot = /^color$/.test(prop) ? "color" : /^(background|background-color)$/.test(prop) ? "background" : null;
          if (!slot) continue;
          const key = `${scope}|${sel}`;
          const win = slots.get(key) ?? {};
          const r = (decl.important ? 1 : 0) * 1e9 + rank++;
          if (!win[slot] || win[slot].r < r) win[slot] = { r, value: decl.value.trim() };
          slots.set(key, win);
        }
      }
    });
    return slots;
  }

  const offenders = [];
  for (const cssFile of cssFiles) {
    const rel = path.relative(root, cssFile).split(path.sep).join("/");
    let ast;
    try {
      ast = postcss.parse(preserveNewlines(readFileSync(cssFile, "utf8")), { from: cssFile });
    } catch {
      continue;
    }
    for (const [key, win] of effectiveInkBySelector(ast)) {
      const sel = key.slice(key.indexOf("|") + 1);
      if (win.color && WHITE_INK.test(win.color.value) && win.background && flipsByTheme(win.background.value)) {
        offenders.push(`${rel}: ${sel.slice(0, 46)}`);
      }
    }
  }
  if (offenders.length) {
    fail(
      `写死白墨压在会随主题翻转的实色底上（夜校提亮底色后白字会掉到 AA 以下）。` +
        `请改用 var(--fg-on-solid)。命中 ${offenders.length} 处：${offenders.slice(0, 4).join(" / ")}`
    );
  }

  // 同一类缺陷在 TSX 里的写法：Tailwind 的 bg-copper（= --action-primary）配 text-white。
  // 组件早已改成 text-[color:var(--fg-on-solid)]，但页面里的选中态 chip 是手拼类名，
  // 第 8 步批次 A 就在这儿找到 4 处。固定深色面（bg-stone-800 的浮层工具条）不算：
  // 那种底不随主题翻转，白字是对的，所以只盯 bg-copper 这一族。
  const tsxOffenders = [];
  for (const p of allSource.filter((x) => x.endsWith(".tsx") && !x.includes(`${path.sep}__tests__${path.sep}`))) {
    const rel = path.relative(root, p).split(path.sep).join("/");
    // 逐行单独去注释，而不是整篇 stripComments 后再 split：
    // 后者会把 /* … */ 里的换行一起吃掉，报出来的行号会往上漂（实测漂 3 行），
    // 而这条不变量的全部价值就在于人能照着行号直接跳过去。
    readFileSync(p, "utf8")
      .split("\n")
      .forEach((raw, i) => {
        const line = stripComments(raw);
        // 按「工具类」而不是子串判：直接搜 text-white 会被引号粘住漏报，
        // 搜 bg-copper 又会把 bg-copper/5 这种半透明底（本来就该用主题墨）误判成实色底。
        const tokens = line.replace(/["'`{}()]/g, " ").split(/\s+/).filter(Boolean);
        if (tokens.includes("bg-copper") && tokens.includes("text-white")) {
          tsxOffenders.push(`${rel}:${i + 1}`);
        }
      });
  }
  if (tsxOffenders.length) {
    fail(
      `TSX 里 bg-copper（随主题翻转的主色底）配了写死的 text-white，请改 ` +
        `text-[color:var(--fg-on-solid)]。命中 ${tsxOffenders.length} 处：${tsxOffenders.slice(0, 4).join(" / ")}`
    );
  }
}

/* ------------------------------------------------------------------ 结论 */

if (failures > 0) {
  console.error(`${TAG} 失败 ${failures} 项`);
  process.exit(1);
}
console.log(`${TAG} 全部通过（令牌定义、D-1/D-2 回归、Button 能力面、${hardPairs.length} 组对比度硬断言${pending > 0 ? `，另有 ${pending} 组待收敛` : ""}）`);
