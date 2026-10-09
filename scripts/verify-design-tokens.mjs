import { readFileSync, readdirSync, existsSync } from "node:fs";
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
 * 去注释但保住行号：块注释/行注释都换成等长空白。
 * 注意行注释那条必须用 `[ \t]*` 而不是 `\s*`——\s 会吃掉换行，
 * 于是「空行 + // 注释」这种写法里，空行的换行会被一起替换成空格，
 * 行数直接缩水（实测 LibraryPage.tsx 少 3 行，报出来的行号比真实位置早 3 行）。
 * 这个函数的全部意义就是让行号可跳转，漂了就等于没有。
 */
function preserveNewlines(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, " "))
    .replace(/^[ \t]*\/\/.*$/gm, (m) => " ".repeat(m.length));
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

// 批次 L（规格 §5.2「保留旧名做别名」）：圆角两个旧名同样被多张样式表引用，但它们
// 多了一层 D-2 没有的风险——圆角棘轮豁免 var(--radius-*)，所以**把别名改回 5px / 8px
// 时棘轮一个都不会响**（债从 CSS 声明搬进了令牌定义，判据看的是声明）。这两个值正是
// 面板家族与 settings 控件的实际圆角，改回去等于一次无声的整页改版。故在此钉死映射：
for (const [alias, authority] of [["--radius-control", "--radius-1"], ["--radius-panel", "--radius-2"]]) {
  const rule = new RegExp(`${alias}:\\s*var\\(\\s*${authority}\\s*\\)`);
  if (!rule.test(lightBlock))
    fail(`第 8 步圆角刻度：${alias} 必须定义为 var(${authority})（规格 §5.2 的别名归位）。圆角棘轮看不见令牌里的值，这里不钉就等于没守。`);
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
// 实测口径差：这条注释原来写死了「放宽前 8 条 / 放宽后 18 组」，
// 但那两个数字是当时判据（把带值排除 :not([data-variant="quiet"]) 也算已排除）下测的，
// 批次 B-1 把排除判据收紧后它们就已经不成立了，而 8 这个数还被人拿去排过第 8 步的工期。
// 结论：条数不在源码里写死，守卫每次运行都会打印真实冻结数（当前 12 条），
// 需要数字的人看输出，不看注释。
// 为什么只看「直接父级」而不看整条选择器的祖先类名：祖先链上常见 mt-3 / flex / grid
// 这类工具类，任何已迁移文件只要某处 <Button> 放在 .mt-3 容器里就会命中，
// 会把大量根本碰不到迁移元素的规则报成违规。直接父级才是「这条规则会压到谁」的
// 准确判据，宿主类名也是按「该元素内部有 <Button>」记的，两者口径一致。
const BUTTON_COMBINATOR = String.raw`(?:[ \t]*[>+~][ \t]*|[ \t]+)`;
/**
 * 必须带 i 标志：HTML 里元素名匹配是大小写不敏感的，所以 CSS 选择器 `.x Button`
 * 真的会命中 <button>。用 jsdom 实测过（`.foo BUTTON` 的 background 生效在
 * <button> 上）。不带 i 就会整条漏掉——本仓 editorial-studio.css 的
 * `.migration-banner Button` 正是这种写法，守卫一直看不见它，
 * 于是它用 !important 把已迁移 <Button> 的 hover 底色钉死，也没人报。
 */
const BUTTON_PARENT = new RegExp(String.raw`([^\s>+~]+)` + BUTTON_COMBINATOR + String.raw`button\b`, "i");
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

/**
 * 已知盲区（批次 C 撞到，如实记录，不当场扩张判据）：
 * classesHostingButton 只在「同一个文件」里做 JSX 栈扫描，所以它看不见跨文件的
 * 组件边界。EpubReaderPage.tsx 自己一个 <Button> 都没有，但它渲染的
 * <EpubSidePanel> 里有——于是 `reader-root` 不在宿主类名集合里，
 * `.reader-root button { color: var(--text-main) }` 这条 (0,1,1) 规则一直躲着守卫，
 * 把 EPUB 页里组件 primary 按钮的白字强制成晨校墨色（压 #315f9b 只有 2.41:1）。
 * 本批是手工 + jsdom 加载真实产物才发现的。
 * 为什么不直接把宿主判据放宽到「类名在全仓任意 tsx 里出现过」：当时实测命中 59 条
 * 后代按钮规则（数字会随仓库变化，取当前判据重新数），绝大多数是 .desktop-nav /
 * .project-nav 这类本来就该接管裸按钮的导航样式，属于误报——把警告变成噪音，
 * 等于没有警告。真正的修法是解析 import 图、按组件边界递归求宿主类名闭包，
 * 那是独立一轮的活。在此之前：给阅读器/浮层容器写后代按钮规则的人，
 * 请另外用 jsdom 加载打包产物（out/renderer/assets/*.css）核对这些工具类
 * 的 computed color / background 是不是自己写的那条，而不是只读源码就交差。
 */

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

/**
 * 只承认「裸排除」`:not([data-variant])`——它排除的是所有 <Button>。
 *
 * 这里原本写成 `:not([data-variant`，把带值的形式也算已排除，理由是「怕 needle
 * 里的 ] 匹配不上导致检查失效」。那个担心是反的：带值排除恰恰不安全。
 * `.desktop-page-actions button:first-child:not([data-variant="quiet"]):not([data-variant="ghost"])`
 * 排掉了 quiet/ghost，却仍然命中首子是默认 primary 的 <Button>——ProjectHomePage
 * 的「新建项目」正是这种按钮，它的底色/圆角/内边距一直被这条 (0,1,1) 规则压着。
 * 按旧判据它算「已排除」，于是 12 条里只报 8 条，棘轮显示的债务比真实少 4 条，
 * 而第 8 步「迁移对应页面时收口」正是照着这个数字排工期的。
 * 改成精确串匹配后裸形式照样能匹配到（`.includes(":not([data-variant])")`），
 * 不存在匹配不上的问题。
 */
const isExcluded = (sel) => sel.includes(":not([data-variant])");

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

/* -------------------------------------------- Tailwind 状态色类名必须归零（批次 C） */

/**
 * 第 7 步把「硬编码状态色」的 grep 计数做到归零，扫的是 CSS 里的十六进制字面量。
 * 但同一件事在 TSX 里有一条完全绕开令牌的路：Tailwind 的默认调色板类名。
 * bg-red-500 / text-amber-800 里没有 #，也没有 var()，所以第 7 步的守卫看不见它们——
 * 批次 C 一查就是 15 处。它们的共同缺陷是「不随主题翻转」：夜校里那是把晨校的浅岛
 * 原样画在深色画布上，实测 AISection 的说明文字 text-paper-muted 压 bg-amber-50/60
 * 在夜校只有 1.14:1（整段不可读），ErrorBoundary 的详情条 3.14:1。
 * 所以这里把 red/rose/orange/amber/yellow/green/emerald/teal/lime 这几支
 * 「会被当成语义色用」的族钉成零命中；slate/gray/zinc/stone/neutral 是纯中性灰阶，
 * 本仓用它做固定深色的划词工具条（bg-stone-800，白字 15.17:1 是正确答案），
 * 不属于状态色，不封。
 * 只扫非测试源码，且必须先剥注释——本文件里就有多处「引用旧写法」的注释，
 * 不剥就会自己咬自己。
 */
{
  /**
   * 判据按「工具类」而不是子串，理由和批次 A 的 bg-copper 分支一样：
   * 类名贴在引号/空格后面，子串正则要么漏（引号粘连）要么误报。
   * 一条上可能挂任意 variant 前缀（hover:bg-red-50 / group-hover:text-amber-800），
   * 所以只看冒号切出的最后一段，前面是什么前缀不影响判定；段尾允许 , ; ) 等
   * 收尾标点（CSS 的 @apply 行就是这么写的）。
   * 族名单只列「会被当成语义色用」的九支，中性灰阶（stone/slate/zinc…）不在内：
   * bg-stone-800 那条划词工具条的白字 15.17:1 是正确答案，不该被误伤。
   */
  const STATE_FAMILIES = new Set(["red", "rose", "orange", "amber", "yellow", "green", "emerald", "teal", "lime"]);
  const STATE_PREFIX = /^(?:bg|text|border|ring|fill|stroke|divide|outline|shadow|from|via|to)$/;
  const isStatePaletteToken = (tok) => {
    const last = tok.split(":").pop().replace(/[,;)\]]+$/, "");
    const m = /^([a-z]+)-([a-z]+)-(\d{2,3})(?:\/\d+)?$/.exec(last);
    return !!m && STATE_PREFIX.test(m[1]) && STATE_FAMILIES.has(m[2]);
  };
  const hits = [];
  for (const p of allSource.filter((x) => /\.(tsx|ts|css)$/.test(x) && !x.includes(`${path.sep}__tests__${path.sep}`))) {
    const rel = path.relative(root, p).split(path.sep).join("/");
    // 必须整篇剥注释再按行切（preserveNewlines 用空格占位，行号不漂）：
    // 本批次留下的说明注释里写着「原先 bg-amber-50」这种旧类名，逐行 stripComments
    // 对跨行的 {/* … */} 无效，守卫会把自己的说明文字报成违规。
    preserveNewlines(readFileSync(p, "utf8"))
      .split("\n")
      .forEach((raw, i) => {
        const tokens = raw.replace(/["'`{}()]/g, " ").split(/\s+/).filter(Boolean);
        if (tokens.some(isStatePaletteToken)) hits.push(`${rel}:${i + 1}`);
      });
  }
  if (hits.length) {
    fail(
      `Tailwind 状态色类名回来了：${hits.length} 处。这批颜色不随主题翻转（夜校会把晨校的` +
        `浅岛画在深色底上）。危险/批注用 var(--proof-mark) / var(--proof-tint)，` +
        `警示用 var(--warning) / var(--warning-tint)，成功用 var(--success) / bg-moss-soft；` +
        `需要 alpha 就写 color-mix(in srgb, var(--x) N%, transparent)。` +
        `命中：${hits.slice(0, 6).join(" / ")}`
    );
  }
}

/* ---------------------------- 浮层容器样式表不得用 background/color 遮蔽 tone 工具类 */

/**
 * 批次 C 找到的最贵的一条：ToastCenter 的四条 tone 类（bg-moss-soft / bg-amber-50 …）
 * 全部是死的。editorial-studio.css 的 `.motion-toast { background; color; }` 与工具类
 * 同为 (0,1,0)，而 main.tsx 先加载 styles.css（含 @tailwind utilities）再加载
 * editorial-studio.css——后到的赢。于是四种提示在界面上从来没有颜色区别，
 * 只有 border 生效（那条不在同一规则里）。这比"某处对比度不够"更隐蔽：
 * 语义色令牌写全了、类名也挂上了，只是整族被一行老代码吃掉。
 * 判据不能靠肉眼数偏移，所以钉成不变量：凡给「浮层容器」写 background / color
 * 的样式表规则，如果同名工具类正被组件挂在 className 上，就是遮蔽。
 * 这里取最小可执行形式：直接封 .motion-toast 这类容器规则声明 background/color，
 * 因为它们唯一的作用就是压掉 tone 工具类。动画与阴影留着（那两个属性没有竞争者）。
 */
{
  const TOAST_SURFACE = /\.motion-toast\b/;
  const shadowed = [];
  for (const cssFile of cssFiles) {
    const rel = path.relative(root, cssFile).split(path.sep).join("/");
    let ast;
    try {
      ast = postcss.parse(readFileSync(cssFile, "utf8"), { from: cssFile });
    } catch {
      continue;
    }
    ast.walkRules((rule) => {
      if (!rule.selectors.some((s) => TOAST_SURFACE.test(s.replace(/\s+/g, " ").trim()))) return;
      for (const decl of rule.nodes) {
        if (decl.type !== "decl") continue;
        const prop = decl.prop.toLowerCase();
        if (prop === "background" || prop === "background-color" || prop === "color") {
          shadowed.push(`${rel}: ${rule.selector.replace(/\s+/g, " ").trim()} { ${prop} }`);
        }
      }
    });
  }
  if (shadowed.length) {
    fail(
      `.motion-toast 上又出现了 background/color 声明，它会把 ToastCenter 的 tone 工具类` +
        `（bg-moss-soft / bg-[color:var(--warning-tint)] …）整族遮蔽——特异度相同而` +
        `editorial-studio.css 在 utilities 之后加载，后到的赢。批次 C 之前四种提示` +
        `从来没有颜色区别就是这个原因。tone 底色只写在 className 里。命中：${shadowed.slice(0, 3).join(" / ")}`
    );
  }
}

/* ------------------------------------------------ 规格 §5.2 圆角刻度棘轮（第 8 步） */

/**
 * 规格 §5.2 把圆角收成 3 + 1 档：--radius-1(4px) / --radius-2(6px) / --radius-3(10px)
 * / --radius-full(999px)。令牌在批次 C 之前就定义好了，但 `tokens.css:84` 当时写着
 * 「--radius-control / --radius-panel 留待刻度收敛步骤再指向 --radius-1/2」——
 * 也就是这一步。实测离刻度的写法有 244 处、分布在 41 个文件里（25+ 种散写值），
 * 一次全改属于「🔴 面广 → 分页面批次，每批截图确认」，所以这里先按第 6 步按钮棘轮的
 * 同一手法把债务按文件冻结：
 *   ① 新增任何一处离刻度圆角 → 立刻红（预算是按文件给的，别处不能替它挡）；
 *   ② 某文件还掉了债却没把预算同步调低 → 也红。
 * 第 ② 条不是为了为难人：这一轮我之前就把「放宽前 8 条 / 放宽后 18 条」这种数字
 * 写在注释里过，结果判据一改数字就烂，还被人拿去排过工期。预算是数据、不是散文，
 * 每次运行都跟真实计数对账，数字就不可能说谎。
 *
 * 三条排除，各有各的理由，都不算「放宽判据」：
 *   · `var(--radius-*)`：跟着权威令牌走，正是我们要的方向（实测全仓 17 处别名消费者
 *     只用到 --radius-control / --radius-panel 两个名字，没有第三种 var 写法）。
 *   · `50%`：那是「画成圆形」的形状决定，不是圆角档位。非正方元素上 50% 是椭圆，
 *     换成 999px 会变成圆角矩形——两者不等价，所以不并入刻度（实测仅 4 处）。
 *   · 选择器里的 `.rounded-xl` 这类锚点：CSS 用类名去选中 TSX 挂上的工具类
 *     （editorial-studio.css 的 `.inbox-detail > .rounded-xl`），圆角决定在 TSX 那侧，
 *     在这里重复计数既冤枉合法写法又让预算虚高。用 postcss 解析而不是逐行正则，
 *     正是为了让「声明/atrule」和「选择器」天然分开。
 */
{
  const RADIUS_SCALE_PX = new Set([0, 4, 6, 10, 999]);
  const SIDES = new Set(["t", "b", "l", "r", "tl", "tr", "bl", "br"]);
  const TAILWIND_SIZE_PX = { sm: 2, md: 6, lg: 8, xl: 12, "2xl": 16, "3xl": 24 };
  const ROUNDED_TOKEN = /\brounded(?:-[a-zA-Z0-9[\]()_.:-]*[a-zA-Z0-9\]])?/g;

  /** 一个 rounded-* 工具类给出的角半径；null = 不判定（var/百分比/复合写法）。 */
  const radiusCorners = (tok) => {
    const body = tok.slice("rounded".length).replace(/^-/, "");
    if (body === "") return [4]; // 裸 rounded = 0.25rem = 4px，正好是 --radius-1
    if (body.startsWith("[")) {
      const inner = body.slice(1, -1);
      if (/var\(--radius/.test(inner) || inner.includes("%")) return null;
      const nums = inner.match(/-?\d+(?:\.\d+)?/g);
      return nums ? nums.map((n) => parseFloat(n)) : null;
    }
    if (body.endsWith("none") || body.endsWith("full")) return null; // 0 / 999px，都在刻度上
    const size = body.split("-").filter((p) => !SIDES.has(p));
    if (!size.length) return [4];
    if (size.length > 1) return null; // 一侧一档，档位由下面 px 判定，这里不猜
    const px = TAILWIND_SIZE_PX[size[0]];
    return px === undefined ? null : [px];
  };
  const isOffScale = (corners) => !!corners && corners.some((v) => !RADIUS_SCALE_PX.has(Math.round(Math.abs(v))));

  /**
   * 冻结预算：还掉一页就把对应数字调低（或删成 0）。
   * 数字由本文件自己扫出来的，改代码的人（和 AI）不需要重新数一遍。
   */
  const RADIUS_BUDGET = {
    // 批次 AV：最后一处离刻度归零——Button base 的 rounded-lg(8px，刻度上没有这一档) 改成
    // rounded-[var(--radius-1)]（4px）。表空 = 两侧圆角零容忍，任何文件新增一处都红。
    // ⚠ 清零后 stale 那条分支（还了债却不降预算）对圆角不再可达：表里没有条目，
    //   actual 不可能小于一个不存在的预算。它对应的注入用例随本批退役（和批次 AS 退役
    //   幻影预算的同类条目一样，留字在这里，别让下一批人以为那条判据坏了）。
    // 目标写法刻意用 rounded-[var(--radius-1)] 而不是裸 rounded（两者同值、都上刻度）：
    //   Tailwind 同类工具的发射顺序不是字母序，产物实测 .rounded → .rounded-2xl →
    //   .rounded-[var(--radius-N)] → .rounded-full → -lg → -md → -none → -sm → -xl，
    //   后者压前者。写裸 rounded 会让它成为所有变体里最弱的一条——正是要的效果；
    //   而旧写法 rounded-lg 压在 -full / -[var(--radius-2|3)] 上面，静默吃掉消费方覆盖
    //   （jsdom 实测 base+rounded-full = 0.5rem，不是 9999px）。
    //   这条只是选写法的原因，棘轮看不见它（同值都在刻度上）；反向用例钉的是
    //   「目标写法合法、不许误伤」，和批次 M 那条同一条规矩。
    // 批次 AR：encryption.css 那条 14px 随 .pe-modal 族整族下线（查无宿主，从未渲染），
    // 条目移除——从此这份表里任何「还了债不降预算」都要重新登记才能过关。
  };

  const actual = {};
  for (const p of allSource.filter((x) => /\.(css|tsx|ts)$/.test(x) && !x.includes(`${path.sep}__tests__${path.sep}`))) {
    const rel = path.relative(root, p).split(path.sep).join("/");
    const src = readFileSync(p, "utf8");
    let n = 0;
    if (rel.endsWith(".css")) {
      let ast;
      try {
        ast = postcss.parse(src, { from: p });
      } catch {
        fail(`圆角棘轮：postcss 解析失败，${rel} 没被数到（漏数 = 这笔债务不再被盯住）`);
        continue;
      }
      ast.walkDecls(/^border-radius/, (d) => {
        const toks = d.value.replace("!important", "").trim().split(/\s+/);
        if (toks.some((t) => /^-?\d/.test(t) && !t.includes("%") && !RADIUS_SCALE_PX.has(parseFloat(t)))) n += 1;
      });
      // @apply rounded-lg 编译出来就是一条 border-radius 声明，和 TSX 挂工具类等价，必须同判。
      ast.walkAtRules("apply", (a) => {
        for (const tok of a.params.match(ROUNDED_TOKEN) ?? []) if (isOffScale(radiusCorners(tok))) n += 1;
      });
    } else {
      // TSX/TS 侧必须剥注释（同状态色类名那条的理由）：本仓库的说明注释里
      // 会写「原来是 rounded-lg」这种旧类名，不剥就是守卫自己咬自己。
      for (const raw of preserveNewlines(src).split("\n")) {
        for (const tok of raw.match(ROUNDED_TOKEN) ?? []) if (isOffScale(radiusCorners(tok))) n += 1;
      }
    }
    if (n) actual[rel] = n;
  }

  const over = Object.keys(actual).filter((f) => actual[f] > (RADIUS_BUDGET[f] ?? 0));
  const stale = Object.keys(RADIUS_BUDGET).filter((f) => (RADIUS_BUDGET[f] ?? 0) > (actual[f] ?? 0));
  if (over.length) {
    fail(
      `第 8 步圆角刻度：${over.length} 个文件的离刻度圆角比冻结预算多——${over.slice(0, 5).map((f) => `${f}(${actual[f]}/${RADIUS_BUDGET[f] ?? 0})`).join("、")}。` +
        `界面元素请用 --radius-1(4px) / --radius-2(6px) / --radius-3(10px) / --radius-full，` +
        `或等价工具类 rounded / rounded-md / rounded-[10px] / rounded-full。`
    );
  }
  if (stale.length) {
    fail(
      `第 8 步圆角刻度：${stale.length} 个文件已经还了债但预算没跟着降——${stale.slice(0, 5).map((f) => `${f}(${actual[f] ?? 0}→应为预算 ${RADIUS_BUDGET[f]})`).join("、")}。` +
        `把 RADIUS_BUDGET 里对应数字改成当前计数（这就是这一批还掉的量，提交信息里写清楚是哪一页）。`
    );
  }
  const totalActual = Object.values(actual).reduce((a, b) => a + b, 0);
  console.log(`${TAG} [第 8 步待收敛] 圆角离刻度 ${totalActual} 处（${Object.keys(actual).length} 个文件，已按文件冻结预算）`);
}

/* ------------------------------------------------ 规格 §5.3 阴影刻度棘轮（第 8 步） */

/**
 * ⚠ 以下两个 helper 是模块作用域，被「阴影棘轮」和「幻影选择器」两块判据共用：
 * 同一个物理量在两副透镜（活债 / 无宿主债）下必须是同一个数，否则一处豁免了、
 * 另一处还在计债，就会永远对不上账。
 */

/** box-shadow 值按顶层逗号分层（括号内逗号不算分隔）。 */
const shadowLayers = (v) => {
  const out = [];
  let depth = 0;
  let cur = "";
  for (const ch of v) {
    if (ch === "(") depth += 1;
    if (ch === ")") depth -= 1;
    if (ch === "," && depth === 0) {
      out.push(cur.trim());
      cur = "";
    } else cur += ch;
  }
  if (cur.trim()) out.push(cur.trim());
  return out;
};

/**
 * 「描边式焦点环」判据（批次 AD）。
 *
 * §2.3 把 focus-visible 定义为 `box-shadow: 0 0 0 2px var(--surface-1), 0 0 0 4px var(--ring)`
 * ——偏移量与模糊半径全为 0、只剩 spread 的环。它在物理上不是投影层级，而是**描边**：
 * 用 box-shadow 只是为了不占布局、不吃 border 的合流。§5.3 那四级（shadow-1..4）
 * 是浮起层级，压根不为环准备。
 *
 * 批次 G 给 CSS 侧开了 inset 豁免（纯 inset 是色条/内衬，同一条理由），
 * 批次 I 给 TSX 侧豁免了 `[box-shadow:var(--focus-ring)]`——但 CSS 侧漏了
 * 「0 0 0 Npx」这种环，于是一批合法焦点环一直躺在阴影账里冒充投影债。
 * 判据的不对称比漏检更坏：它让人以为这些数字还得继续还，于是「还债」的正确方向
 * （把环收进 --focus-ring 族）反而没人做。具体几处别写在这儿——那是会漂移的数，
 * 要看在跑守卫时自己报的账。
 *
 * 只认**所有非 inset 层都是环**的值；环与真投影混排（`inset 0 1px 0 …, 0 12px 34px …`）
 * 照旧计债，别把豁免撑成逃生舱。
 * ⚠ 不作用于 shadow-[0_0_0_3px_…] 方括号工具类：批次 K 实测那种写法只产
 * --tw-shadow-color、不发射 box-shadow（幻影），必须继续算债、继续硬红。
 */
const RING_LAYER = /^0 0 0 [0-9.]+px(?:\s|$)/;
const isPureRingValue = (v) => {
  const nonInset = shadowLayers(v).filter((L) => !/^inset\b/.test(L));
  return nonInset.length > 0 && nonInset.every((L) => RING_LAYER.test(L));
};

/**
 * 和圆角棘轮同一批手法：先把债变成数据，再一页一页还。规格 §5.3 把阴影收成 4 级
 * （--shadow-1 静止卡片 / --shadow-2 下拉菜单 / --shadow-3 对话框 / --shadow-4 拖拽幽灵），
 * 实测 CSS 里曾有 63 个唯一 box-shadow 值，其中绝大多数是手抄的 rgba 组合。
 *
 * 与圆角不同，阴影不是「一个数字对不对」，所以判据形状也不同：
 *   · CSS 侧：一条 box-shadow 声明，只要含**至少一个非 inset 层**又不走 var(--shadow-*)，
 *     计一处债。全是 inset 的豁免——那 20 处是「inset 2px 0 0 var(--studio-seal)」这种
 *     色条/内衬装饰（左侧书脊线），规格 §5.3 的四级是投影层级，压根不是给它们准备的；
 *     把结构装饰逼成投影令牌才是判据的暴政。`none` 豁免。
 *   · TSX 侧：Tailwind 的 shadow 工具类。刻度外的档（shadow-sm/md/lg/xl/2xl/3xl 和
 *     仓库自定义的 shadow-paper/shadow-lift）都算债——paper/lift 是砚席主题早期手抄的
 *     两档投影，正是 §5.3 要取代的东西。裸 `shadow` 默认档同样算。
 *     shadow-none / shadow-inner 豁免（inner 与 CSS 侧 inset 同族）。
 *     颜色工具类（shadow-copper-300 这类不存在的键）不判定：只产 --tw-shadow-color、
 *     不改投影几何，管它是判据的傲慢。
 *     ⚠ shadow-[var(--x)] / shadow-[#任意值] 一律算债（批次 K 收紧）：批次 I 原本豁免
 *     「方括号里走 var(--shadow*)」，把它当成已经用上令牌。批次 K 在打包产物里实测：
 *       .shadow-\[var\(--shadow-2\)\] { --tw-shadow-color: var(--shadow-2);
 *                                       --tw-shadow: var(--tw-shadow-colored); }
 *     ——**一条 box-shadow 都没有**。Tailwind 把方括号里的值当「阴影颜色」，投影几何不变，
 *     界面画不出任何东西。豁免它等于给幻影写法发通行证：改的人看到棘轮计数下降，
 *     以为还了债，实际界面纹丝不动（正是批次 E 那种「改了画不出来」，而且更隐蔽——
 *     这次连守卫都替它背书）。唯一能真正发射阴影的任意值写法是属性形式
 *     [box-shadow:var(--shadow-2)]，产物实测发射 box-shadow: var(--shadow-2)。
 *     写法上的区别只有一个字符，界面结果差一条声明，所以这条必须钉住（见下面 phantom）。
 *     [box-shadow:...] 任意属性形式：走 var(--shadow*)/--focus-ring 的豁免仍然成立——
 *     焦点环 [box-shadow:var(--focus-ring)] 就在这里，它不是阴影层级，是描边，
 *     且 button-variants.test.tsx 已断言 Button 用的就是这个形式。
 *   · 正则前置 (?<![A-Za-z0-9_-]) 挡 `foreshadow`：伏笔是本产品的业务词（17 处），
 *     不是 shadow 工具类。这是判据最容易咬错的地方，故钉死。
 *   · TSX 侧必须先过 preserveNewlines 剥注释（同圆角棘轮的理由：说明注释里会写
 *     「不能写 shadow-[var(--focus-ring)]」这种旧类名，不剥就是守卫自己咬自己）。
 */
{
  const THEME_SHADOW_KEYS = new Set(["", "sm", "md", "lg", "xl", "2xl", "3xl", "inner", "paper", "lift"]);
  const SHADOW_TOKEN = /(?<![A-Za-z0-9_-])shadow(?:-\[[^\]]*\]|-[a-zA-Z0-9][a-zA-Z0-9_.:/-]*)?/g;
  const ARBITRARY_BOX = /\[box-shadow:([^\]]*)\]/g;
  /**
   * 幻影写法：方括号的**整个值**就是一个 var()，Tailwind 认不出投影几何，只会把它当
   * 阴影颜色，产物里不发射 box-shadow（批次 K 实测，见上面注释）。
   * 判窄不判宽：shadow-[0_1px_0_var(--x)] 这种「有几何、颜色走变量」的混合值
   * 确实能发射，只被 tsxShadowDebt 计成债（绕过刻度），不进这条硬红。
   */
  const PHANTOM_SHADOW = /(?<![A-Za-z0-9_-])shadow-\[\s*var\(/;

  /** 一个 shadow-* 工具类是否是刻度外的投影；false = 不判定（none/inner/颜色档/令牌）。 */
  const tsxShadowDebt = (tok) => {
    if (tok === "shadow") return true; // 裸 shadow = Tailwind 默认投影档
    const m = /^shadow-(.+)$/.exec(tok);
    if (!m) return false;
    const body = m[1];
    if (body.startsWith("[")) return true; // 见下方判据注释：shadow-[var(...)] 是幻影写法
    const key = body.split("-")[0];
    if (!THEME_SHADOW_KEYS.has(key)) return false; // 颜色工具类：只改 --tw-shadow-color，不判
    if (body === "none" || body === "inner") return false;
    return true;
  };

  /**
   * 冻结预算。数字由本判据扫出（CSS 声明 + @apply 92 + TSX 32，合计 124，30 个文件），和圆角棘轮一样：
   * 新债要红，还了债不降预算也要红。
   */
  const SHADOW_BUDGET = {
    "src/components/ErrorBoundary.tsx": 1,
    // 批次 AM：这三份宿主都挂 motion-dialog / motion-toast，家族那条
    // box-shadow: var(--shadow-3) / var(--shadow-4) !important 加载更晚且带 !important，
    // 一直压着手上的 shadow-paper 工具类——jsdom 读真实产物 computed 值实测「带它」和
    // 「不带它」完全同值，工具类从未画出一个像素，只挂着一笔真债。删工具类、留 border
    // （--shadow-3/4 是纯投影档、不含 hairline 环，AE 的双拼规矩对它们不成立）。
    // "src/components/interaction.tsx"、"src/features/creation/inbox/ai-send-confirm.tsx"、
    // "src/features/creation/outline/OutlineTree.tsx" 三条随退役归零、条目移除。
    // 批次 AN：这四处是**真改版**——没有家族 !important 接管，手上那条就是实渲值，
    // 按 §5.3 归级换成属性形式 [box-shadow:var(--shadow-3)]（对话框）/ var(--shadow-2)
    // （字体下拉、设置搜索下拉、书库排序下拉）。条目随债清零移除。
    // ⚠ 必须用属性形式：方括号工具类 shadow-[var(...)] 在产物里只产 --tw-shadow-color、
    // 不发射 box-shadow（批次 K 实测并钉成硬红），写它等于假装还债。
    // 刻意没动：Tabs 药丸的 shadow-sm 属「按钮一律无阴影」（§2.5）那条规矩的活，
    // 和静止卡片归级不是一件事，随按钮批收。
    // 批次 AP：上面那条「随按钮批收」兑现了——药丸是 <button role="tab">，§2.5 说按钮
    // 一律无阴影，所以它的还法不是换个令牌（§2.5 判据连 var(--shadow-*) 都不豁免），
    // 而是整条不画。选中态辨识度实测交给纯色彩反馈。条目随债清零移除。
    // 批次 AO：SectionWrapper 的分区卡归入 --shadow-1，同串的 border border-paper-line
    // 一起撤（AE 的规矩：环包办边框；颜色等价靠 styles.css 里那条
    // `.desktop-canvas .border-paper-line` 与 --paper-line 的别名关系）。条目随债清零移除。
    // 仍不动：ErrorBoundary 那条是全屏故障卡，它的 border 走校样红令牌族（语义色），
    // --shadow-1 的环是 separator 中性色，撤边等于把「出错了」这层语义擦掉——
    // 要收它得先决定故障卡的红边由谁承担，另批处理。
    "src/features/creation/replace/replace.css": 1,
    // 批次 AJ：灵感页那条 shadow-paper 工具类随面板归入 --shadow-1 一起删除，条目移除。
    // 批次 AR：encryption.css 那条三层手抄投影随 .pe-modal 族整族下线（查无宿主），条目移除。
    "src/features/settings/settings-controls.css": 1,
    // 批次 AK：styles.css 退役 4 处——侧栏卡那条被 editorial 同特异度 none 压死的手抄投影（层叠死），
    // 加 manuscript-paper / reader-paper / reader-glassbar 三条幻影规则整条删除（宿主零引用）。
    // 批次 AL：再退役 2 处，同一族「层叠压死」，胜者表实测从未画出一个像素——
    //   .paper-panel 的 @apply shadow-lift（被家族那条 0 1px 2px !important 压着）、
    //   .paper-topbar 的 @apply shadow-[0_1px_0_...]（被 .reader-root .paper-topbar 与
    //   本文件 L2755 的 box-shadow: none 双重压着）。border/background 一律保留。
    // 余下 8 处是 7 个夜读皮肤 + .paper-input:focus 的焦点柔光，都是**真在渲染**的值：
    // 皮肤那条是纸面浮影（收它=可感知改版，另批处理），focus 那条是 3px 描边柔光 +
    // ring-1 合成，属 §2.3 焦点机制而非 §5.3 投影层级。
    "src/styles.css": 8,
    // 批次 AS：settings-grid 开关岛整族下线，其中 L2371 那条
    // box-shadow: 0 1px 2px rgba(34, 38, 48, 0.28) 是幻影账里最后一笔真债（岛查无宿主、
    // 值又离刻度），随整族一起删除。16→15。
    "src/styles/editorial-studio.css": 15
  };

  const actual = {};
  for (const p of allSource.filter((x) => /\.(css|tsx|ts)$/.test(x) && !x.includes(`${path.sep}__tests__${path.sep}`))) {
    const rel = path.relative(root, p).split(path.sep).join("/");
    const src = readFileSync(p, "utf8");
    let n = 0;
    if (rel.endsWith(".css")) {
      let ast;
      try {
        ast = postcss.parse(src, { from: p });
      } catch {
        fail(`阴影棘轮：postcss 解析失败，${rel} 没被数到（漏数 = 这笔债务不再被盯住）`);
        continue;
      }
      ast.walkDecls(/^box-shadow$/, (d) => {
        const v = d.value.replace("!important", "").replace(/\s+/g, " ").trim();
        if (v === "none" || /^var\(--(shadow|focus)/.test(v)) return;
        // 只有「至少一个非 inset 层」才是投影；纯 inset 是色条/内衬装饰，§5.3 四级不针对它
        if (!shadowLayers(v).some((L) => !/^inset\b/.test(L))) return;
        // 批次 AD：纯描边环（0 0 0 Npx）同属「不是投影层级」——§2.3 的 focus-visible 机制，
        // 和上面 var(--focus-*) 的豁免是同一件事的两种写法，两侧对称才算修完。
        if (isPureRingValue(v)) return;
        n += 1;
      });
      ast.walkAtRules("apply", (a) => {
        if (PHANTOM_SHADOW.test(a.params)) {
          fail(`${rel} 里 @apply 写了 shadow-[var(...)] 幻影形式：Tailwind 产物实测只产 --tw-shadow-color、无 box-shadow 声明，界面画不出阴影但棘轮计数会降。改用 [box-shadow:var(--shadow-N)]。`);
        }
        for (const tok of a.params.match(SHADOW_TOKEN) ?? []) if (tsxShadowDebt(tok)) n += 1;
      });
    } else {
      // TSX/TS 侧剥注释后逐行扫（同圆角棘轮，防「不能写 shadow-[var(--focus-ring)]」
      // 这类说明注释咬自己）
      for (const line of preserveNewlines(src).split("\n")) {
        // 幻影写法单独硬红：它不进计数（计了反而让「改前 1 处、改后 0 处」的
        // 还债假象看起来正常），因为「改了画不出来」比数字超标更坏。
        if (PHANTOM_SHADOW.test(line)) {
          fail(`${rel} 里写了 shadow-[var(...)] 幻影形式：Tailwind 产物实测只产 --tw-shadow-color、无 box-shadow 声明，界面画不出阴影但棘轮计数会降。改用 [box-shadow:var(--shadow-N)]。`);
        }
        for (const tok of line.match(SHADOW_TOKEN) ?? []) if (tsxShadowDebt(tok)) n += 1;
        // 必须用 exec 取捕获组：line.match(ARB) 带 /g 返回的是**整串数组**，
        // 那种写法下 m[1] 是字符串的第 2 个字符 "["，豁免判断永远为真、永远误判
        // ——焦点环 [box-shadow:var(--focus-ring)] 会被数成债（批次 I 实测踩过）。
        ARBITRARY_BOX.lastIndex = 0;
        let mm;
        while ((mm = ARBITRARY_BOX.exec(line)) !== null) {
          const bv = mm[1].trim();
          if (/^var\(--(shadow|focus)/.test(bv)) continue;
          // 属性形式会真的发射 box-shadow，所以 CSS 侧那条描边环豁免在这儿同样成立；
          // 下划线是 Tailwind 的空格写法。⚠ 反过来说，方括号工具类 shadow-[0_0_0_…]
          // 不豁免：批次 K 实测它只产 --tw-shadow-color，画不出来。
          if (isPureRingValue(bv.replace(/_/g, " "))) continue;
          n += 1;
        }
      }
    }
    if (n) actual[rel] = (actual[rel] ?? 0) + n;
  }

  const over = Object.keys(actual).filter((f) => actual[f] > (SHADOW_BUDGET[f] ?? 0));
  const stale = Object.keys(SHADOW_BUDGET).filter((f) => (SHADOW_BUDGET[f] ?? 0) > (actual[f] ?? 0));
  if (over.length) {
    fail(
      `第 8 步阴影刻度：${over.length} 个文件的离刻度阴影比冻结预算多——${over.slice(0, 5).map((f) => `${f}(${actual[f]}/${SHADOW_BUDGET[f] ?? 0})`).join("、")}。` +
        `投影请用 --shadow-1(静止卡片)/--shadow-2(菜单)/--shadow-3(对话框)/--shadow-4(拖拽/toast)，` +
        `纯 inset 色条/内衬、描边式焦点环（0 0 0 Npx，§2.3 的 focus-visible）和 shadow-none/shadow-inner 不在此管。`
    );
  }
  if (stale.length) {
    fail(
      `第 8 步阴影刻度：${stale.length} 个文件已经还了债但预算没跟着降——${stale.slice(0, 5).map((f) => `${f}(${actual[f] ?? 0}→应为预算 ${SHADOW_BUDGET[f]})`).join("、")}。` +
        `把 SHADOW_BUDGET 里对应数字改成当前计数（这就是这一批还掉的量，提交信息里写清楚是哪一页）。`
    );
  }
  const totalActual = Object.values(actual).reduce((a, b) => a + b, 0);
  console.log(`${TAG} [第 8 步待收敛] 阴影离刻度 ${totalActual} 处（${Object.keys(actual).length} 个文件，已按文件冻结预算）`);
}

/* -------------------------------- 规格 §5.1 间距刻度棘轮（第 8 步批次 AW 立项） */

/**
 * §5.1 把间距定成 4px 基准的 11 档，令牌（--sp-0…--sp-11）早在批次 C 前就进了 tokens.css，
 * 而且第 1 步的令牌存在性判据一直在断言这 12 个名字有定义。实测：全仓 `var(--sp-*)`
 * 引用**零处**——令牌铺了没人用，散写的 gap/padding/margin 各数各的。这是规格开头那张
 * 「间距 19 种 gap、无刻度表」的账，也是第 8 步「字号/间距/圆角按刻度收敛」三本里
 * 唯一还没立判据的一本（圆角在批次 AV 清零，阴影台账 26 处仍冻结中）。
 *
 * 本批只立项、不还债：把当前实测值按文件冻结成预算，从此新增一处离刻度间距就红、
 * 还掉一处不降预算也红。465 处 / 28 个文件的量级按「🔴 面广 → 分页面批次，每批截图确认」
 * 排后续批次，一轮不现实，也不该由判据替人猜哪些能合并。
 *
 * 口径（三条排除都有实测依据，不是放宽判据）：
 *   · `var(--sp-*)` / `var(--radius-*)` 之类引用令牌的写法不计数——那正是收敛的方向，
 *     而且令牌值本身由 §5.1 定义，重复计数等于让同一笔债在两个地方各红一次。
 *   · em / % / calc / auto / 负值一律跳过。em 是排版相对单位（首行缩进 `text-indent`
 *     类写法本仓有 4 处，跟 4px 基准无关），% 与 auto 是布局语法；负值是光学位移
 *     （实测全仓仅 1 处 `margin-top: -6px`，那是补偿描边宽度，不是档位选择）。
 *   · 只认 px 与 rem 两种字面长度；rem 按 16px **名义基准**折算（0.25rem 与 4px 是同一档
 *     的两种写法，实测本仓 rem 间距仅 3 处、折算后都在刻度上）。⚠ 批次 AX 复核时纠正了
 *     本批初版的一句错误前提：当时写「桌面端没有代码改根字号、实测零命中」，实际
 *     App.tsx 的 applyTheme 一直在写 `root.style.fontSize = appFontScale×100%`（设置里
 *     「应用字体缩放」滑杆 85%–140%，实测存在）。折算依旧成立、且理由要换：刻度本就定义
 *     在 100% 基准上，rem 与基准同缩（用户放大界面时 rem 间距跟着放大，档位比例不变），
 *     所以 rem 是**随缩放走的合法写法**；反倒是 px 字面值无视该设置、缩放时比例失真——
 *     这条区别由批次 AX 的字号棘轮正面记账，本判据不在此展开。
 *
 * TSX 侧数的是 Tailwind 工具类（gap-N / p-N / m-N / space-x-N），步进 4px：
 * gap-4 = 16px、gap-5 = 20px 都在刻度上；非整数档（-2.5 = 10px、-3.5 = 14px）和
 * -px（=1px）离刻度。注意步进是 4px 不是 16px——第一版探针按 16px 折算，把
 * ReadingStatsPage 的 gap-4/gap-5 全误报成债（26 处假账），实测产物 CSS 后改回。
 * 两侧语料与圆角棘轮完全一致（src 下 .css/.tsx/.ts，排除 __tests__，先剥注释再逐行扫），
 * 注释里写 `p-4` 这种字面串不会被计数——但 Tailwind 的 content 扫描不剥注释、会把它
 * 发射进产物（批次 AV 实测），这条坑记在 ui.tsx 的注释里，不在这里重复。
 *
 * ⚠ 立项时撞出一处**规格自相冲突**，判据不替它选边：§2.2 把按钮 sm 档的水平内边距
 * 定为 10px，而 §5.1 的 11 档里没有 10px（…8 / 12…）。实测 10px 是全仓最重的离刻度值
 * ——gap 50 处 + padding 80 处 + margin 25 处 = 155 处，占这本账 465 处的三分之一。
 * 所以这本账真正的第一件事不是「逐页收敛」，而是先由人定 10px 的去留：
 *   · 若 §5.1 增补一档（10px），155 处债当场归零，代价是刻度从 11 档变 12 档；
 *   · 若 §2.2 的 sm 内边距改 8 或 12px，则 155 处要逐页改版（可感知，需截图确认）。
 * 在此之前 10px 一律按离刻度计数（保守方向：多算债不会导致误删，替设计提前追认才会），
 * ui.tsx 那条预算 1 就是这场冲突的现场——它是 sizes.sm 的 px-2.5，不是谁写错了数字。
 */
{
  const SP_SCALE_PX = new Set([0, 2, 4, 6, 8, 12, 16, 20, 24, 32, 40, 48]);
  const SP_PROPS = /^(?:row-|column-)?(?:gap|padding|margin)(?:-(?:top|right|bottom|left|block|block-start|block-end|inline|inline-start|inline-end))?$/;
  const SP_STEP = 4; // Tailwind 默认 spacing 步进：gap-4 = 16px
  const SP_FRACTIONS = { "0.5": 2, "1.5": 6, "2.5": 10, "3.5": 14 };
  const SP_UTILITY =
    /(?:^|[\s"'`{,(])(?:[a-z-]+:)*(?:p|px|py|ps|pe|pt|pr|pb|pl|pi|m|mx|my|ms|me|mt|mr|mb|ml|mi|gap|gap-x|gap-y|space-x|space-y)-((?:\d+(?:\.\d+)?|px))(?=$|[\s"'`,{}():])/g;

  /** 一个长度值折算成 px；null = 不判（var()/百分比/em/calc/auto/负值/非长度）。 */
  const spLengthPx = (tok) => {
    let m = /^(\d+(?:\.\d+)?)px$/.exec(tok);
    if (m) return parseFloat(m[1]);
    m = /^(\d+(?:\.\d+)?)rem$/.exec(tok);
    if (m) return parseFloat(m[1]) * 16;
    return null;
  };
  /** 一个 Tailwind 间距工具类给出的 px；null = 不判（任意值形式本仓实测零使用）。 */
  const spUtilityPx = (body) => {
    if (body === "px") return 1;
    if (SP_FRACTIONS[body] !== undefined) return SP_FRACTIONS[body];
    if (!/^\d+(?:\.\d+)?$/.test(body)) return null;
    return parseFloat(body) * SP_STEP;
  };

  /**
   * 立项账本，数字由本判据自己扫出（`node scripts/verify-design-tokens.mjs --show-spacing-ledger`
   * 重算，别信任何注释里的静态数字）。⚠ 与圆角/阴影那两本不同，这本是**新增判据**，
   * 表里的数字全是「当前实测散写值」，不是已经被追认的合法设计——下一批每收敛一个页面，
   * 就要把对应条目的数字降到该页新实测值；整页收敛完就删条目。
   * 之所以照登不误：本仓的规矩是「判据不能上线即红」，立项批必须先让全仓绿，
   * 才谈得上后续批次的红是新增债。
   */
  const SPACING_BUDGET = {
    "src/components/ErrorBoundary.tsx": 1,
    "src/components/ui.tsx": 1,
    "src/features/creation/ai/scene-candidate.css": 1,
    "src/features/creation/cards/cards-local.css": 31,
    "src/features/creation/cards/relation-graph.css": 5,
    "src/features/creation/editor/continuous-editor.css": 3,
    "src/features/creation/editor/scene-radar.css": 7,
    "src/features/creation/editor/writing-quick-reference.css": 20,
    "src/features/creation/editor/writing-reference.css": 9,
    "src/features/creation/history/history-local.css": 16,
    "src/features/creation/inbox/components/InboxQuickInput.tsx": 1,
    "src/features/creation/inbox/inbox-local.css": 2,
    "src/features/creation/operation/operation.css": 10,
    "src/features/creation/outline/outline-reorg.css": 5,
    "src/features/creation/overview/overview-local.css": 2,
    "src/features/creation/preview/preview-local.css": 23,
    "src/features/creation/replace/replace.css": 8,
    "src/features/library/ExcerptPicker.tsx": 1,
    "src/features/library/LibraryPage.tsx": 1,
    "src/features/library/ReaderSidePanel.tsx": 3,
    "src/features/library/reader/components/ReaderSearchOverlay.tsx": 1,
    "src/features/library/reader/components/ReaderTopNav.tsx": 1,
    "src/features/library/toc/TocList.tsx": 1,
    "src/features/search/search.css": 18,
    "src/features/settings/encryption/encryption.css": 3,
    "src/features/settings/sections/StorageSection.tsx": 1,
    "src/styles.css": 196,
    "src/styles/editorial-studio.css": 94
  };

  const actual = {};
  const ledger = [];
  for (const p of allSource.filter((x) => /\.(css|tsx|ts)$/.test(x) && !x.includes(`${path.sep}__tests__${path.sep}`))) {
    const rel = path.relative(root, p).split(path.sep).join("/");
    const src = readFileSync(p, "utf8");
    let n = 0;
    if (rel.endsWith(".css")) {
      let ast;
      try {
        ast = postcss.parse(preserveNewlines(src), { from: p });
      } catch {
        fail(`间距棘轮：postcss 解析失败，${rel} 没被数到（漏数 = 这笔债务不再被盯住）`);
        continue;
      }
      ast.walkDecls((d) => {
        if (!SP_PROPS.test(d.prop)) return;
        for (const t of d.value.replace("!important", "").trim().split(/\s+/)) {
          const px = spLengthPx(t);
          if (px !== null && !SP_SCALE_PX.has(px)) {
            n += 1;
            ledger.push(`${rel}:${d.source.start.line} ${d.prop}: ${t}`);
          }
        }
      });
      // @apply 编译出来就是一条声明，和 TSX 挂工具类同判（同圆角棘轮那条理由）。
      ast.walkAtRules("apply", (a) => {
        for (const m of a.params.matchAll(SP_UTILITY)) {
          const px = spUtilityPx(m[1]);
          if (px !== null && !SP_SCALE_PX.has(px)) {
            n += 1;
            ledger.push(`${rel}:${a.source.start.line} @apply …-${m[1]}`);
          }
        }
      });
    } else {
      for (const line of preserveNewlines(src).split("\n")) {
        for (const m of line.matchAll(SP_UTILITY)) {
          const px = spUtilityPx(m[1]);
          if (px !== null && !SP_SCALE_PX.has(px)) n += 1;
        }
      }
    }
    if (n) actual[rel] = n;
  }

  const over = Object.keys(actual).filter((f) => actual[f] > (SPACING_BUDGET[f] ?? 0));
  const stale = Object.keys(SPACING_BUDGET).filter((f) => (SPACING_BUDGET[f] ?? 0) > (actual[f] ?? 0));
  if (over.length) {
    fail(
      `第 8 步间距刻度：${over.length} 个文件的离刻度间距比冻结预算多——${over.slice(0, 5).map((f) => `${f}(${actual[f]}/${SPACING_BUDGET[f] ?? 0})`).join("、")}。` +
        `间距请用 §5.1 的 11 档（--sp-0…--sp-11 = 0/2/4/6/8/12/16/20/24/32/40/48px），` +
        `等价 Tailwind 写法是 gap-N/p-N/m-N 的 4px 步进（gap-4=16px、gap-5=20px）。` +
        `em/%/calc/auto/负值是排版与布局语法，不在本判据管辖。`
    );
  }
  if (stale.length) {
    fail(
      `第 8 步间距刻度：${stale.length} 个文件已经还了债但预算没跟着降——${stale.slice(0, 5).map((f) => `${f}(${actual[f] ?? 0}→应为预算 ${SPACING_BUDGET[f]})`).join("、")}。` +
        `把 SPACING_BUDGET 里对应数字改成当前计数（这就是这一批还掉的量，提交信息里写清楚是哪一页）。`
    );
  }
  const totalActual = Object.values(actual).reduce((a, b) => a + b, 0);
  console.log(`${TAG} [第 8 步待收敛] 间距离刻度 ${totalActual} 处（${Object.keys(actual).length} 个文件，已按文件冻结预算）`);
  if (process.argv.includes("--show-spacing-ledger")) {
    console.log(`${TAG} 间距账本 ${ledger.length} 行：`);
    for (const l of ledger) console.log("    " + l);
  }
}

/* -------------------------------- 规格 §3.2 排版刻度棘轮（第 8 步批次 AX 立项） */

/**
 * 第 8 步「字号/间距/圆角按刻度收敛」的最后一本：圆角（批次 AV 清零）、阴影（AM/AN 后
 * 剩 26 处冻结）、间距（批次 AW 465 处冻结）之后，字号与行高一直没有判据——§3.2 的八档
 * 令牌 --text-11…--text-32 与配套的 --leading-* 早在批次 C 就进了 tokens.css，第 1 步的
 * 存在性判据也一直在断言这 16 个名字，但实测全仓 `var(--text-<档>)` / `var(--leading-*)`
 * 引用**零处**（注意区分：--text-primary/secondary/tertiary 是颜色令牌，同名前缀不同族，
 * 那 186 处引用是颜色，不是字号）。
 *
 * 三本账分开立，因为它们各自的「合法」边界不同：
 *
 * ① 字号（FS）：§3.2 定 11/12/13/14/16/20/24/32 八档，立项时实测离刻度 114 处 / 21 文件
 *    （批次 BD-1 已还掉其中 40 处，见下方 FS_BUDGET 的注释）。
 *    值来源两侧都数：CSS 的 font-size 字面量（px 直接取、rem 按名义 16px 折算，见下），
 *    以及 TSX/@apply 的 text-* 工具类（text-xs=12 在刻度上；text-lg=18 不在；任意值
 *    字号按字面值判——立项时 SettingsSearch 的 text-[10.5px] 就是这一类，BD-1 已上到 11px）。
 *    ⚠ rem 折算在这里比间距那本更站不住，但仍按名义 16px 折：App.tsx 的 applyTheme 会
 *    写 `root.style.fontSize = appFontScale×100%`（设置里「应用字体缩放」滑杆 0.85–1.4，
 *    实测存在，批次 AX 复核时纠正了批次 AW 注释里「零命中」的错误声明）。也就是说
 *    **text-[1rem] 这类写法在不同用户机器上是不同的像素值**，而档位表是像素定义的。
 *    实测本仓 rem 字号仅 outline-reorg.css 一处，立项时是 `font-size: 0.78rem`——按 16
 *    折=12.48px 离刻度、按滑杆任何一档折也都不落在八档上，所以两种口径都得记它一笔，
 *    账不会因口径漂移而漏。批次 BD-3 把它收成 `0.75rem`（名义 12px，正落在 §3.2 的
 *    12 档上）；**保留 rem 写法**——这条是大纲撤销条，随「应用字体缩放」滑杆走是它的
 *    语义，换成 px 字面量反而无视滑杆。这里记的是「名义基准下的债」；滑杆让 px 字面量
 *    在缩放时比例失真，那是「px 写法本身」的问题，不是某个数字离刻度，本判据不重复记账
 *    （见下方说明）。
 *    §3.3 给阅读器正文定了 17px（可调 15–22）、稿纸 16px，但**不构成对本账这些字面值的
 *    豁免**，理由要分开说清：阅读器正文那个 17px 与「15–22」是**用户设置**的取值区间，
 *    经 inline style 落地（TxtMarkdownReader 的 style.fontSize、epub-engine 的
 *    themes.override），根本不经过 CSS 字面量——所以「15–22 可调」不能拿来给散写的
 *    `font-size: 15px` 发证。逐条核过落在这些数字上的选择器：.writing-manuscript-head h2
 *    22 / .creation-step legend 15 / .library-cover-glyph 15 是标题、图例与字形；
 *    .scene-editor-content 立项时主题表里还有一条 18px（批次 BD-2 已删——它从未画出来，
 *    胜者是 editorial-studio.css 的 17px），稿纸按 §3.3 是 16px，18 与 15 都离刻度。
 *    唯一确实是
 *    正文的是 .preview-block（15px，预览的书页正文，配套行高已随批次 BC 收成 §3.3 的 1.9）
 *    ——可 §3.3 给正文的两个值就是 17 与 16，没有 15，所以它照记不误。
 *    保守方向是债留在账上；由判据预先追认一批规格没给过的位置才是风险方向。
 *
 * ② 行高比率（LH）：§3.2 给的是八档**同名**行高（1.5/1.55/1.6/1.7/1.45/1.3/1.25/1.1），
 *    §3.3 又另给 1.9（阅读器正文）与 1.85（稿纸）。两处并集当合法集，立项时实测离刻度
 *    17 处 / 6 文件——多数是 1.65/1.4/1.28 这种「比八档多一档」的手抄值。
 *    批次 BC 还掉 15 处（就近取合法档，同值收敛不自创档位），剩 2 处是稿纸那一对
 *    `line-height: 2`：它和 background 里 34px 横线的节距是算出来的耦合（17×2=34），
 *    落 §3.3 的 16px / 1.85 必须字号、行高、节距三件一起重算，故留给专门的稿纸批次。
 *    ⚠ 这里刻意不判「行高是否跟字号配成同名一对」：本仓字号大量走继承与工具类，
 *    判配对需要可靠的级联计算，文本扫描做不到，硬猜会把正常写法咬成债（宁可漏判）。
 *
 * ③ 长度式行高（LHL）：§3.2 明文「行高一律无单位，禁止 line-height: 24px 这类写法」，
 *    理由是缩放时固定 px 会错位。这条本应零容忍——规格从未追认过任何带单位的行高，
 *    不存在「当前设计就是这么定的」那种辩护。但实测存量并不为零：作者手写的带单位
 *    行高 CSS 侧 0 处（规格这条在 CSS 里已经天然满足，值得如实记一句），而 Tailwind 的
 *    leading-N 编译出来就是长度式行高（产物实测 .leading-5{line-height:1.25rem}），
 *    TSX 里 43 处 / 22 文件。判据不能上线即红，所以这 43 处按文件冻结、新增即红；
 *    还法是把 leading-N 换成无单位比率（§3.2 八档同名值），不是把数值改小。
 *    leading-none（=1，无单位）与 tight/loose 等词形不算长度式，实测词形 0 处。
 *    ⚠ 刻意**不**把 text-* 具名工具类自带的行高计进这本：产物实测 .text-sm 同时输出
 *    font-size:.875rem 与 line-height:1.25rem（全仓 196 处 / 45 文件挂着具名 text-*），
 *    但那行 rem 行高是工具类的附属产物、不是作者写的数值——要消掉它只能改字号选择本身，
 *    而字号已经由①这本管着了。同一处挂两本账会让「还一笔债」变成两次改动的耦合，
 *    也违反判据不重复计数的老规矩（同批次 AW 对 var(--radius-*) 的处理）。
 *
 * 台账由本判据自己扫出：`--show-type-ledger` 重算并逐行打印（文件:行 值）。
 * 数字随还债下降，还完删条目——和间距那本同样的规矩。
 */
{
  const FS_SCALE_PX = new Set([11, 12, 13, 14, 16, 20, 24, 32]);
  const LH_ALLOWED = new Set(["1.1", "1.25", "1.3", "1.45", "1.5", "1.55", "1.6", "1.7", "1.85", "1.9"]); // §3.2 ∪ §3.3
  const TW_FS = { xs: 12, sm: 14, base: 16, lg: 18, xl: 20, "2xl": 24, "3xl": 30, "4xl": 36, "5xl": 48, "6xl": 60, "7xl": 72, "8xl": 96, "9xl": 128 };
  const FS_UTILITY =
    /(?:^|[\s"'`{,(])(?:[a-z-]+:)*text-(xs|sm|base|lg|xl|2xl|3xl|4xl|5xl|6xl|7xl|8xl|9xl|\[[\d.]+px\])(?=$|[\s"'`,{}():])/g;
  const LH_UTILITY = /(?:^|[\s"'`{,(])(?:[a-z-]+:)*leading-(\d+(?:\.\d+)?)/g;

  // 批次 BD-1：低于 §3.2 最小档（11px）的 9 / 10 / 10.5px 共 40 处一律上到 11px，
  // 本表 114→74。relation-graph / writing-reference / settings-controls.css / SettingsSearch
  // 四份清零，条目删除（空表=新增即红）。剩下的值全是 11.5 / 12.5 / 13.5 / 14.5 / 15 / 17 /
  // 18 / 19 / 22 / 23 / 25 / 30 这种「比八档多一档」或「就近另一档」的值——它们往哪边取
  // 是口味决定（11.5→11 还是 12？15→14 还是 16？），不像本族这样只有一个合法方向。
  // 所以后续按页面分批做，每批的取值口径由那一页的版式角色定，不在这张表里预先许诺。
  //
  // 批次 BD-2：再从 74 里还掉 10 处，这 10 处**一个像素都没变**——它们是「记在账上却从未
  // 画出来」的字号声明（同选择器文本、同特异度、在 bundle 里更靠后的规则才是胜者：
  // styles.css 那 9 条被 editorial-studio.css 压住，search.css 那 1 条也被它压住）。
  // 判据按「源码里的字面量」记账，看不见级联，所以死数值会一直占着预算、并且每批都要被
  // 重新调查一遍。这批删的就是「谁赢」已经有胜者图能机械证明的那些。
  // 剩下 64 处全是**活着的**离刻度字号——那才是真的需要一页一页定的部分。
  //
  // 批次 BD-3：从 64 里还掉 6 处，这一族的判据不需要任何口味——**离刻度值在八档上有
  // 唯一严格最近档**，就近取整没有第二种合法答案（与 BD-1 同族逻辑，只是数值在刻度
  // 中段而非下缘）：14.5→14 ×2（.desktop-brand strong / .uni-search-input，两处成对，
  // 同一侧栏-搜索行的标题与输入框）、19→20 ×1（.reading-stat-value，配 700 字重与
  // tabular-nums 的数字块，20 是 §3.2 现成的数字档）、17→16 ×2（.preview-toolbar-meta h2
  // 与 .cards-detail-head h3，都是面板小标题，§3.2 里 h3 级给 16）、0.78rem→0.75rem ×1
  // （名义 12px，见上方 ① 的 rem 说明；保留 rem 是为了跟随字体缩放滑杆）。
  // 剩下的 11.5 / 12.5 / 13.5 / 15 / 18 / 22 / 23 / 25 / 30 全是**两侧等距或跨档**的值，
  // 往哪边取仍是那一页版式角色的决定，按页面分批做。
  // ⚠ 本批**排除** .scene-editor-content 的 17px：它和 line-height:2 与 background 里
  // 34px 横线节距是算出来的耦合（17×2=34），单改字号就会让横线漂到字腰上——归稿纸批次
  // 与那 2 处行高一起三件同算，不混进「严格最近档」。
  //
  // 批次 BD-4：再从 58 里还掉 5 处。剩下的值**两侧等距**（11.5 在 11/12 正中、12.5 在
  // 12/13 正中、13.5 在 13/14 正中），单看字号确实选不出方向——但 §3.2 那张表是
  // **字号与行高同名配对**的（11↔1.5、12↔1.55、13↔1.6、14↔1.7、16↔1.45、20↔1.3、
  // 24↔1.25、32↔1.1），所以一条规则里**同时写着八档行高**时，作者已经把角色说出来了，
  // 只有字号手写成了半个档位。判据因此不看字号本身，而看「本条规则的无单位行高 == §3.2
  // 某档的配对行高」且「该档与现值相差 ≤0.5px」——两个条件同时成立才算，方向唯一：
  //   .scene-radar-item dd 12.5 + lh 1.6 → 13
  //   .creation-proof-item-snippet 12.5 + lh 1.55 → 12
  //   .migration-error 12.5 + lh 1.6 → 13
  //   .history-item-retention 11.5 + lh 1.5 → 11
  //   .background-card-fields em 11.5 + lh 1.5 → 11
  // 反例照旧留账，防止这张表被读成「配对行高万能」：.migration-note 12.5 + lh 1.7（→14，
  // 差 1.5px 超出半档）、.writing-annotation-empty 与 .scene-candidate-note 11.5 + lh 1.6
  // （→13，差 1.5px）、.preview-block 15 + lh 1.9（1.9 是 §3.3 阅读行高、不在 §3.2 八档
  // 配对表里）、.scene-candidate-line 12.5 + lh 1.7（差 1.5px）。这些仍是角色决定。
  //   （批次 BD-8 后来按**整页角色**还掉了 scene-candidate 那两处——注意那不是配对判据
  //     松了口：12.5+1.7 与 11.5+1.6 按配对法各该去 14/13，而按「本页 diff 正文 vs 本页
  //     summary 辅助说明」的角色关系定的是 13/12，两条判据给出的答案不同、依据也不同。
  //     留在账上的 .migration-note / .writing-annotation-empty / .preview-block 依旧欠着。）
  // 本批 5 处都是**当前胜者**（逐条对过字号胜者图），所以是真实的 ±0.5–1px 视觉变化。
  // ⚠ 两条棘轮牙的锚点随本批搬家：字号③原钉 scene-radar.css 12.5px（债已还、needle 失效），
  // 改钉 styles.css 那条唯一的 .creation-palette-command 13.5px；行高比率①原钉 history-local
  // 的「11.5px + line-height: 1.5」，同一条 .history-item-retention 字号变 11px，needle 同步
  // 重写。牙跟着债走——留在还掉的债上，这条就永远绿、永远不测任何东西。
  //
  // 批次 BD-5：再从 53 里还掉 6 处，这 6 处**全在同一个页面**（校对面板 .creation-proof-*，
  // styles.css 一段连续规则），这是「按页面分批」的第一批。取值口径不再靠行高配对
  // （BD-4 那条在本页只剩 .creation-proof-scope 也没配对上），而是 §3.2 表格的**用途列**
  // 加上**本页已有的同角色邻居**——邻居必须是同页、已刻度上的具名值，不借跨页先例：
  //   .creation-proof-item-meta    11.5→12  场景·章节名，§3.2「辅助说明」= 12
  //   .creation-proof-item-desc    11.5→12  规则说明整句，同上
  //   .creation-proof-ignore       11.5→12  chip 按钮，同页 .creation-proof-rules label(12)
  //                                     是同一形态的 chip（999px 圆角 + 同款 padding），控件档
  //   .creation-proof-ignored-count/truncated 11.5→11 「N 处已忽略」计数，§3.2「计数」= 11，
  //                                     同页 .creation-proof-item-rule(11) 是紧邻的兄弟 chip
  //   .creation-proof-section-title 11.5→11 「已忽略（N 组）」小标题，同页同类的
  //                                     .creation-palette-group h3 与本仓 .desktop-card-label
  //                                     都是 11 的微标签档，且这行本身带计数
  //   .creation-proof-scope        12.5→13  摘要引导行（650 字重 + ink 主色），压在 12px 的
  //                                     .creation-proof-totals 之上，必须比 totals 大才不塌成
  //                                     同一级——13 正是 §3.2 的界面正文默认档
  // 命令面板（.creation-palette-*）是另一页，本批不碰：那里 .creation-palette-group h3 11.5
  // 与 .creation-palette-command 13.5 仍留账——⚠ 字号③那颗牙此刻钉的正是同文件唯一的
  // 13.5px（command），本批一条都不碰它，动它就得再搬家。
  // 页内相对关系核对：改后 13(scope) > 12(totals/snippet/label/location-text/desc/meta/ignore)
  // > 11(item-rule/location-pos/section-title/ignored-count)，13px 的 message 与 14px 的 title
  // 不动——层级仍单调，没有出现「小标题和正文同档」这种倒挂。
  //
  // 批次 BD-6：再从 47 里还掉 2 处，仍按「一页一批」——命令面板（.creation-palette-\*，
  // BD-5 刻意留给这一页的那处）：
  //   .creation-palette-group h3  11.5→11  分组微标签。同页 .creation-palette-footer-keys
  //                                      与两处 kbd **已经全是 11**，这行只差半档就与
  //                                      同页同类同级；§3.2「角标」档就是 11。
  //   .creation-palette-command   13.5→13  命令行文本本体（label + 快捷键），§3.2 明写
  //                                      13 = 「控件与界面正文（默认）」，面板正文 14 是给
  //                                      说明段落的，命令列表是可点控件里的正文，取 13。
  // 页内改后：13(command) > 11(group h3 / footer / kbd)——命令文本仍明显高于分组标签，
  // 层级不塌。⚠ 字号③那颗牙原本钉的就是本批这条唯一的 13.5px，债一还 needle 就查无此串
  // （mutate() 抛错 → 记为 SKIP 且计入失败，所以不会安静失效，但也不会自己搬家）。牙这次
  // 搬到**全本书里最慢还**的那笔：editorial-studio.css 稿纸 .scene-editor-content 的
  // font-size: 17px。选它的理由是「不会再来一批顺手还掉」——17 虽是 §3.3 稿纸的目标邻近值，
  // 却和同条的 line-height: 2 与 background 里 34px 横线节距算死（17×2=34），任何字号批
  // 单独动它都会把横线挪到字腰上，只有专门的稿纸批能字号/行高/节距三件同算。
  // 同一条规则上「行高比率②」钉的是**另一半**（只动 line-height 1.85），两牙各测一本账：
  // 本批实测只动 font-size 17→16 时 FS_BUDGET 红而 LH_BUDGET 不动（反向也成立），
  // 所以两本判据仍是分开的，没有合并成一颗牙。稿纸批落地时两牙的 needle 同批失效、同批搬家。
  //
  // 批次 BD-7：再从 45 里还掉 2 处，第三个「一页一批」（写作速查面板
  // .writing-quick-\*，writing-quick-reference.css 整页 + WritingQuickReferencePanel.tsx）：
  //   .writing-quick-tabs button       11.5→11  页签是**可点控件**，同页同类控件
  //                                          .writing-quick-toggle(11)、
  //                                          .writing-quick-detail-head button(11)、
  //                                          .writing-quick-toggle kbd(11) 全是 11；
  //                                          §3.2「角标」之上没有第二档给小控件文字。
  //   .writing-quick-field input /
  //   .writing-quick-detail .cards-input
  //                                    11.5→12  输入框里是**用户正在编辑的正文**，本页正文
  //                                          具名档是 .writing-quick-list strong 的 12；
  //                                          字段标签已经 11，输入若同为 11 就与标签糊成
  //                                          一级（这条规则是双选择器，一次还掉两处预算）。
  // 页内改后单调性：14(detail--empty p) > 12(list strong / 输入) > 11(页签·标签·控件·计数)。
  // 原先 11.5 同时出现在「控件文字」和「可编辑正文」两种角色上，正是 §3.2 要取代的
  // 「20+ 种字号」的病灶；现在两种角色各自落到具名档。
  // ⚠ 本页**故意留下** .writing-quick-reference-head h3 的 18px：它不是本页能决定的——
  // §3.2 的标题阶梯是 16(h4) / 20(h3) / 24(h2)，18 卡在 16 与 20 中间，而本仓还有一族
  // 同角色面板头（replace.css .replace-panel-header h2 18、editorial-studio.css
  // .desktop-brand-mark 18 ×2）要一起按「面板头」这一档定，跨页一次定完才不会出现
  // 「两个面板的标题同级却不同档」。这批只收本页证据充分的 2 处，不做跨页许诺。
  // ⚠ 本批不动任何棘轮牙的锚点：字号①钉 inbox-local、字号②钉 LibraryPage、字号③钉稿纸
  // 17px、行高比率①钉 history-local 的 retention 行——都不在本页；wqr 里那条
  // line-height: 1.45 是**刻度上**的值，不欠债也不当锚点。
  //
  // 批次 BD-8：再从 43 里还掉 2 处，第四个「一页一批」，整页清零（AI 候选评审弹窗
  // .scene-candidate-\*，scene-candidate.css + SceneCandidateReview.tsx；条目随之删除，
  // 空表=新增即红。BD-4 曾把 scene-radar.css 清零，那是单值族顺手清完的，本批是
  // **按页口径**清完的第一例）：
  //   .scene-candidate-line  12.5→13   diff 里每一行是**被比较的正文本身**，§3.2 明写
  //                                   13 =「控件与界面正文（默认）」；同页 summary 已经
  //                                   是 12，正文若与它同档，「几段新增」的统计行和真正
  //                                   的候选文字就糊成一级。
  //   .scene-candidate-note  11.5→12   弹窗底部一句提示 = §3.2「辅助说明」，与同页
  //                                   summary(12) 同角色同档。
  // 页内改后单调性：13(diff 正文) > 12(summary·note) ——h4 标题无具名字号（走 UA 默认），
  // 不参与本账，所以本页的级差只看这三行。
  // ⚠ 这一页同时是 BD-4「配对行高」判据的**反例**（12.5+lh1.7、11.5+lh1.6 按配对法各该去
  // 14/13，差 1.5px 超出半档、当时不收）。本批收它靠的是整页角色，不是把配对判据松口——
  // 两条判据给的答案不同、依据也不同，别把这条读成「配对法后来也认 13/12」。
  // 本批不动任何棘轮牙锚点（字号①②③、行高比率①②、行高长度式①②、间距正反牙都不在这页；
  // 这页的 lh 1.7 / 1.6 都是刻度上的值）。
  //
  // 批次 BD-9：再从 41 里还掉 3 处，第五个「一页一批」——项目列表 / 项目工作台导航
  // （.project-nav \* 与 .project-home-chars，都在 editorial-studio.css，宿主
  // CreationProjectsPage.tsx + ProjectHomePage.tsx 同一屏）：
  //   .project-nav button      12.5→12  **同一批按钮**在写作页接管态里作者自己写的就是
  //                                   12px !important（:3520，.creation-writing-page--active
  //                                   .project-nav button:not(.project-nav-back)），
  //                                   紧邻的同族返回键 .project-nav-back 也是 12
  //                                   （styles.css:3866）。导航项彼此必须同档，
  //                                   非接管态 12.5 / 接管态 12 是同一行字两种高度。
  //   .project-nav button
  //     @media (≤1024px)       11.5→11  窄屏这条是**压缩档**，语义是「比基础档低一档」；
  //                                   基础档落到 12 之后，压缩档只能取 §3.2 的下一档 11。
  //                                   （⚠ 这是本页唯一一处「靠相对关系而非绝对角色」定的
  //                                   值——它成立的前提是基础档已定为 12，反过来若先动这条
  //                                   就会定错。两处同批做，顺序不改变结果。）
  //   .project-home-chars      11.5→11  项目卡上的字数（mono + tabular-nums），§3.2 明写
  //                                   11 =「角标、计数、时间戳」；它和 .project-home-meta
  //                                   (12)、.project-home-no-goal (12) 同组却是唯一的
  //                                   半档值，progress 数字本就该比「更新于… · N 章」低一档。
  // 页内改后：12(导航项·返回键·meta) > 11(窄屏导航·字数计数)；接管态与非接管态的导航文字
  // 从此同档，只剩高度/padding 由接管态压缩。
  // ⚠ editorial-studio.css 本批 17→14，剩下的 14 处大多是**跨页共用**的桌面壳层
  // （侧栏、命令栏、hero、稿纸、批注边栏）——它们不属于「一屏一页」，要按 §4 的骨架
  // 角色整族定，不能像前几批那样用同页邻居判。
  // 本批不动任何棘轮牙锚点：字号③与行高比率②钉的稿纸 17px/lh:2 在同文件 :1248，
  // 本批一条都没碰那六行（改前改后逐字验证 needle 仍唯一命中）。
  //
  // 批次 BD-10：再从 38 里还掉 3 处，口径换成**同角色配对**（BD-9 注释预告过：壳层
  // 不属于「一屏一页」，不能再拿同页邻居当证据）。这 3 处都是 22px，而 22 **正卡在
  // §3.2 的 20 与 24 正中间**——严格最近档判据在这里失效（两侧等距），必须逐处找
  // 「同一角色的另一处声明」替它投票，而且投票方必须是**作者自己写的意图**：
  //   .writing-manuscript-head h2  22→20   主题表 styles.css:1297 对**完全同一个选择器**
  //                                   写的就是 20px；22 是 editorial-studio.css 在后面
  //                                   抬上去的。两表同选择器异值本来就该收口，取基础表
  //                                   的原值，不是给稿纸页标题新发明一个档位。
  //                                   ⚠ 这条与「稿纸正文 .scene-editor-content 17px」
  //                                   无关——它没有 line-height/节距耦合（本条只有
  //                                   font-size + letter-spacing），所以可以单独动。
  //   .inbox-detail-head h2        22→20   该 h2 的 class 串里作者自己写了 text-xl，
  //                                   Tailwind 的 xl 就是 20px（InboxItemDetail.tsx:58），
  //                                   而 (0,1,1) 的 .inbox-detail-head h2 用 22 把它压住
  //                                   了——规则与标记意图长期互相矛盾。按意图收，
  //                                   且 20 正好是 §3.2 的 h3 页面标题档。
  //   .stats-streak                22→20   它是**统计大数字**（OverviewPage 待处理数、
  //                                   StatsPage 连续天数），同角色兄弟 .reading-stat-value
  //                                   在 BD-3 已定 20、.desktop-sidebar-stats strong 也是
  //                                   20。**注意证据来源**：§3.2 给 20 那档写的是「页面
  //                                   标题 h3」，而 32 才是「仪表盘大数字」——这条不是
  //                                   规格直接指认，是三处统计数字必须同档的配对证据
  //                                   （留着 22 会让三个统计数字三种大小）。
  // ⚠ **第 4 处 22px 刻意不还，因为它不是同一道题**：
  //   .overview-hero-title h2  22（overview-local.css:28）与
  //   .desktop-page-hero h2    clamp(20px, 1.8vw, 23px)（editorial-studio.css:2809）
  //   命中的是**同一个 h2**（OverviewPage 的 section 同时挂 desktop-page-hero 与
  //   overview-hero-title）。这不是「22 离刻度、该去 20 还是 24」，而是**流式标题 vs
  //   页面写死标题**的架构选择：收 20 就等于把 hero 标题改成固定值、让那条 clamp
  //   在概览页失效；改 clamp 的中值又与「§3.2 是像素档位表」冲突。两种答案对应两种
  //   设计意图，判据不能替人挑——留给用户（或与 §4 page-header 20px 一起整族定）。
  //   另注：注入脚本「反向③」那颗牙钉的正是 editorial-studio.css 的
  //   clamp(22px, 2vw, 27px)（:629，另一处 clamp，不是本批任何一条），本批一个字没碰。
  // 本批同样不动棘轮牙锚点：字号③/行高比率②钉稿纸 17px+lh:2（:1248，未动，改后逐字
  // 验证仍唯一命中）；行高比率①钉 history-local 的 11px+lh1.5（未动）。
  //
  // 批次 BD-11：再从 35 里还掉 2 处，口径是 BD-7 注释里挂的那笔跨页许诺——「面板头」
  // 这一档一次定完。收的 2 处都是 18px，走的是**已定判据**（不是本批新发明的答案）：
  //   .writing-quick-reference-head h3  18→16  BD-3 已对同角色规则定档：
  //                                   .cards-detail-head h3（styles.css:2272）与
  //                                   .preview-toolbar-meta h2（preview-local.css:66）
  //                                   都是「17→16」——面板里的小标题，§3.2 对应
  //                                   16「h4 小节」。本页正文具名档是 12（list
  //                                   strong），16 与 12 差 4px，层级还够。
  //   .replace-panel-header h2          18→16  同角色（浮动工具面板的头，不是页面标题）；
  //                                   本页其余文字只有 13(label·scope) / 12(hit·meta)。
  //                                   它是 position:fixed 的非模态浮层，不是 §4 那条
  //                                   56px page-header（那里标题 20），也不是 §3.2 的
  //                                   h2 分区档 24——取 BD-3 同档 16。
  // 两处判据与 BD-3 完全同一条（同角色邻居已定档），所以本批不存在「16 还是 20
  // 由我替设计挑边」的问题——邻居已经替它投过票了。
  // ⚠ 剩下两处 18px **本批刻意不还，因为它们不是「面板头」这一道题**：
  //   .desktop-brand-mark  18 ×2（editorial-studio.css:137 基础 + styles.css:4186
  //                                   @media≤920）它是标题栏那颗 38×38 印章里的
  //                                   「洛」字（font-serif + font-weight:700 + 固定
  //                                   宽高 + place-items:center）。字号是**为那个盒子
  //                                   选的**，不是标题阶梯上的一级：套 §3.2 的 16/20
  //                                   分别会让字撑不满盒子或溢出盒子。两条规则
  //                                   （基础与窄屏）同值也是「字形尺寸」而非「压缩档」
  //                                   的证据（对比 BD-9 的 project-nav：那里窄屏比基础
  //                                   低一档；这里窄屏与基础同值）。归品牌族单独定。
  //   6 处 TSX `text-lg`     =1.125rem（ErrorBoundary:57 / interaction:171 /
  //                                   ui:166 EmptyState / InboxItemDetail:183 /
  //                                   ReaderSettingsDrawer:62 / EpubEmptyState:14，
  //                                   全是 `paper-title text-lg font-semibold`）——
  //                                   ⚠ 它不是 18px 字面量：Tailwind 的 lg 是 rem，
  //                                   而 applyTheme 写 root.style.fontSize =
  //                                   appFontScale×100%（0.85–1.4 滑杆），这六处**本来
  //                                   就随界面缩放走**，换 px 字面量等于把它们从缩放里
  //                                   摘出去（BD-3 给 outline-reorg 保 rem 同理）。
  //                                   且六处跨 6 文件、角色各异（对话框标题、空状态、
  //                                   抽屉标题），要按「弹窗/抽屉标题」一族定，且多半
  //                                   保 rem 只换档（text-lg→text-xl=1.25rem=名义 20，
  //                                   与 InboxItemDetail.tsx:58 那处作者自写 text-xl
  //                                   同族）。留给那一批，不混进本批。
  // 本批不动棘轮牙锚点：字号③与行高比率②钉的稿纸 17px/lh:2（editorial-studio.css:1248）
  // 没动；replace.css 里那颗第 7 步的牙钉的是 .replace-hit-before 的 line-through
  // 那两行（inject:169 needle），与本批 :32 不同一条，改前改后逐字验证仍唯一命中。
  // ⚠ 另两笔仍在等人的账也不在本批：.overview-hero-title h2 22（流式 vs 写死的架构
  // 岔口，BD-10 已声明留给用户）与间距那 10px 冲突（465 处冻结中）。
  //
  // 批次 BD-12：再从 33 里还掉 6 处，收的是 BD-11 注释里预告的那一族——6 处
  // `paper-title text-lg`（ErrorBoundary:57 / interaction:171 / ui:166 EmptyState /
  // InboxItemDetail:183 / ReaderSettingsDrawer:62 / EpubEmptyState:14），text-lg =
  // 1.125rem ≈ 名义 18px。⚠ **改法是把档换掉、不碰单位**：`text-lg` → `text-base`
  // （1rem = 名义 16px），**保留 rem**，理由与 BD-3 给 outline-reorg 保 rem 是同一条——
  // applyTheme 写 root.style.fontSize = appFontScale×100%（0.85–1.4），rem 写法本来
  // 就跟着「界面缩放」滑杆走，换成 px 字面量等于把这六处从缩放里摘出去，那是行为变更、
  // 不是还债。本账按名义值记，所以 1rem 是刻度上的 16，判据绿；滑杆失真由「px 写法
  // 本身」那条已知问题承担，不重复记账。
  // 为什么是 16 不是 20（这次不需要我挑边，是**作者自己的阶梯**替它投的票）：
  //   同一族 `paper-title` 的尺寸声明实测只有三档——text-2xl ×2（LibraryPage:137、
  //   ReadingStatsPage:203 的页面 h1）、text-xl ×2（InboxItemDetail:58「条目正文」
  //   面板头、InspirationPage:28）、text-base ×7（Dialog:108、DialogTitle:135、
  //   ai-send-confirm:91、OutlineTree:124、ExcerptPicker:55、LibraryPage:278 书名、
  //   ReadingStatsPage 三个分区 h2）。**没有 18 这一档**，这 6 处是阶梯上的孤级。
  //   逐处按同角色邻居定，六处都落到 16：
  //   · 4 处对话框/确认标题（ErrorBoundary 故障卡、interaction 确认弹窗、
  //     ui EmptyState、EpubEmptyState）——同族对话框标题 Dialog.tsx:108 与
  //     DialogTitle:135、ai-send-confirm:91、OutlineTree:124、ExcerptPicker:55
  //     **全是 text-base**，本仓唯一成规格的「弹窗标题」声明就是 16。
  //   · ReaderSettingsDrawer:62（抽屉头）——它是 [box-shadow:var(--shadow-3)] 的
  //     浮层抽屉，同角色 CSS 声明 .preview-empty h2 / .preview-failure h2 在
  //     preview-local.css 里都是 16px，且 §4 的 56px page-header 才给 20，抽屉
  //     头不是页面头。
  //   · InboxItemDetail:183（「AI 候选版本」卡片内小节）——同一个组件 :58 那个
  //     面板主标题已经写 text-xl(20)，小节若也 20 就与主标题同级；CSS 侧同角色
  //     的 .cards-detail-head h3 在 BD-3 已定 16。
  // 改后 `.paper-title` 的阶梯变成 24 / 20 / 16 三档，与 §3.2 的 16/20/24 完全重合，
  // 孤级消失。
  // ⚠ 本批**没有**动棘轮牙：6 个文件都在 inject 的备份清单里（ui.tsx、interaction.tsx、
  // ErrorBoundary.tsx），它们的牙分别钉 Button variant 串、toast 容器类串、
  // `bg-paper-panel p-6 shadow-paper`——没有一条含 text-lg；改前改后用脚本逐条数过
  // 170 个 needle，除两条**本来就**是多命中（`.project-workbench {\n` 在文件里出现
  // 3 次，needle 靠 mutate 的首次替换定位，与本批无关）外全部唯一命中，0 例外。
  // 仍不动的两处 18px 见 BD-11（.desktop-brand-mark 字形 ×2），那是品牌族、不是标题族。
  //
  // 批次 BD-13：再从 27 里还掉 1 处，历史页整页清零（条目随之删除，空表=新增即红）。
  //   .snapshot-category-badge  11.5→11  快照的分类角标（自动 / 里程碑 / 保护），
  //                                   三条判据同向：① §3.2 用途列 11 =「角标、计数、
  //                                   时间戳」，这就是一个角标；② 全仓同角色声明实测
  //                                   全是 11——.background-card-kind、
  //                                   .cards-detail-kind、.cards-list-kind、
  //                                   .desktop-card-label、.creation-proof-ignored-count
  //                                   没有一个是 12，11.5 是这条族里唯一的半档；
  //                                   ③ 结构上它**嵌在** .history-item-type(12) 里面
  //                                   （HistoryPage.tsx:278→281），角标比宿主低一档才对，
  //                                   11.5 与宿主只差 0.5px、实际糊在一起。
  // 为什么不用「配对行高」判据：这条规则**没有** line-height（改前改后都是无行高声明），
  // 所以 BD-4 那条「§3.2 尺寸↔具名行高互相配对」在这里不适用——本处的判据是用途列 +
  // 同角色族 + 嵌套关系，三条同向，不存在挑边。
  // ⚠ 本批**动了行高比率①那颗牙所在的文件**，但没动锚点本身：牙钉的是
  // .history-item-retention 的 `font-size: 11px;\n  line-height: 1.5;`（:94-96），
  // 本批改的是 :70 那条 badge。改完逐字复验：该两行组合在文件里仍**唯一**命中
  // （badge 那条新写的 `  font-size: 11px;` 后面跟的是 font-weight，不构成 pair）。
  // 另 8 条 history-local 的牙 needle 全部复验唯一，与本批那行不相交。
  const FS_BUDGET = {
    "src/features/creation/overview/overview-local.css": 1,
    "src/features/creation/preview/preview-local.css": 2,
    "src/styles.css": 12,
    "src/styles/editorial-studio.css": 11
  };
  // 批次 BC 还掉 15 处（17→2）：其余四份文件的行高比率债清零，条目随之删除
  // （空表 = 新增即红，比留一个 0 更诚实）。剩下的 2 处是稿纸那一对 `line-height: 2`
  // （styles.css 的 `.scene-editor-content` 与 editorial-studio.css 的同名规则；后者在
  // bundle 里更靠后、是胜者，前者从未画出来——批次 BD-2 记下了这件事但**没有**顺手删，
  // 理由见下）——它们不是「多抄了一档」，而是和 background 里那条 34px 横线的节距
  // **算出来的耦合**（17px × 2 = 34px 恰好一格），单改行高就会让横线漂到字腰上。
  // §3.3 给稿纸定的是 16px / 1.85，要落它必须字号、行高、节距三件一起重算，所以这两处
  // 归下一批专门的稿纸批次，不混进「离刻度值就近收敛」，也不混进「删死声明」。
  const LH_BUDGET = {
    "src/styles.css": 1,
    "src/styles/editorial-studio.css": 1
  };
  const LHL_BUDGET = {
    "src/app/App.tsx": 1,
    "src/components/ErrorBoundary.tsx": 1,
    "src/components/interaction.tsx": 3,
    "src/components/ui.tsx": 2,
    "src/components/ui/NumberStepper.tsx": 1,
    "src/components/ui/Switch.tsx": 1,
    "src/features/creation/inbox/ai-send-confirm.tsx": 4,
    "src/features/creation/inbox/components/InboxConvertToCardDialog.tsx": 1,
    "src/features/creation/inbox/components/InboxItemDetail.tsx": 5,
    "src/features/inspiration/InspirationPage.tsx": 1,
    "src/features/library/ExcerptPicker.tsx": 2,
    "src/features/library/ReaderSidePanel.tsx": 1,
    "src/features/library/ReadingStatsPage.tsx": 1,
    "src/features/library/epub-reader/EpubEmptyState.tsx": 1,
    "src/features/library/epub-reader/EpubSidePanel.tsx": 1,
    "src/features/library/reader/TxtMarkdownReader.tsx": 2,
    "src/features/settings/SettingsPage.tsx": 1,
    "src/features/settings/reader/ReaderTrackingControls.tsx": 1,
    "src/features/settings/sections/AISection.tsx": 2,
    "src/features/settings/sections/SectionWrapper.tsx": 1,
    "src/features/settings/sections/StorageSection.tsx": 9,
    "src/styles.css": 1
  };

  const ledgers = { FS: [], LH: [], LHL: [] };
  const actual = { FS: {}, LH: {}, LHL: {} };
  const BUDGETS = { FS: FS_BUDGET, LH: LH_BUDGET, LHL: LHL_BUDGET };
  const bump = (key, rel, line, what) => {
    actual[key][rel] = (actual[key][rel] || 0) + 1;
    ledgers[key].push(`${rel}:${line} ${what}`);
  };

  for (const p of allSource.filter((x) => /\.(css|tsx|ts)$/.test(x) && !x.includes(`${path.sep}__tests__${path.sep}`))) {
    const rel = path.relative(root, p).split(path.sep).join("/");
    const stripped = preserveNewlines(readFileSync(p, "utf8"));
    if (rel.endsWith(".css")) {
      let ast;
      try {
        ast = postcss.parse(stripped, { from: p });
      } catch {
        fail(`排版棘轮：postcss 解析失败，${rel} 没被数到（漏数 = 这笔债务不再被盯住）`);
        continue;
      }
      ast.walkDecls((d) => {
        const ln = d.source.start.line;
        const v = d.value.replace("!important", "").trim();
        if (d.prop === "font-size") {
          if (v.startsWith("var(") || /^clamp\(/.test(v)) return; // 令牌引用是收敛方向；clamp 是流式排版语法
          let m = /^(\d+(?:\.\d+)?)px$/.exec(v);
          let px = null;
          if (m) px = parseFloat(m[1]);
          else {
            m = /^(\d+(?:\.\d+)?)rem$/.exec(v);
            if (m) px = Math.round(parseFloat(m[1]) * 1600) / 100;
          }
          if (px === null) return; // em/% 等相对写法不判
          if (!FS_SCALE_PX.has(px)) bump("FS", rel, ln, `font-size ${v}`);
        } else if (d.prop === "line-height") {
          if (v.startsWith("var(")) return;
          if (/^[\d.]+$/.test(v)) {
            if (!LH_ALLOWED.has(v)) bump("LH", rel, ln, `line-height ${v}`);
          } else if (/(?:px|pt|rem|em|%)$/.test(v)) {
            bump("LHL", rel, ln, `line-height ${v}`);
          }
        }
      });
      ast.walkAtRules("apply", (a) => {
        const ln = a.source.start.line;
        for (const m of a.params.matchAll(FS_UTILITY)) {
          const body = m[1];
          const px = body.startsWith("[") ? parseFloat(body.slice(1, -1)) : TW_FS[body];
          if (px !== undefined && !FS_SCALE_PX.has(px)) bump("FS", rel, ln, `@apply text-${body}`);
        }
        for (const m of a.params.matchAll(LH_UTILITY)) bump("LHL", rel, ln, `@apply leading-${m[1]}`);
      });
    } else {
      stripped.split("\n").forEach((L, i) => {
        const ln = i + 1;
        for (const m of L.matchAll(FS_UTILITY)) {
          const body = m[1];
          const px = body.startsWith("[") ? parseFloat(body.slice(1, -1)) : TW_FS[body];
          if (px !== undefined && !FS_SCALE_PX.has(px)) bump("FS", rel, ln, `text-${body}`);
        }
        for (const m of L.matchAll(LH_UTILITY)) bump("LHL", rel, ln, `leading-${m[1]}`);
      });
    }
  }

  const NAMES = { FS: "字号", LH: "行高比率", LHL: "行高长度式（§3.2 禁止单位化行高）" };
  for (const key of ["FS", "LH", "LHL"]) {
    const over = Object.keys(actual[key]).filter((f) => actual[key][f] > (BUDGETS[key][f] ?? 0));
    const stale = Object.keys(BUDGETS[key]).filter((f) => (BUDGETS[key][f] ?? 0) > (actual[key][f] ?? 0));
    if (over.length) {
      fail(
        `第 8 步${NAMES[key]}：${over.length} 个文件比冻结预算多——${over.slice(0, 5).map((f) => `${f}(${actual[key][f]}/${BUDGETS[key][f] ?? 0})`).join("、")}。` +
          (key === "FS"
            ? `字号请用 §3.2 八档（--text-11/12/13/14/16/20/24/32）；§3.3 的阅读器正文 17px 由用户设置经 inline style 落地，不是 CSS 字面量的通行证。`
            : key === "LH"
              ? `行高比率请用 §3.2 的八档同名值（1.5/1.55/1.6/1.7/1.45/1.3/1.25/1.1）或 §3.3 的 1.85/1.9。`
              : `§3.2 明令「行高一律无单位」：leading-N 与任何带 px/rem 的行高都在此记账（--show-type-ledger 看逐条）。`)
      );
    }
    if (stale.length) {
      fail(
        `第 8 步${NAMES[key]}：${stale.length} 个文件已经还了债但预算没跟着降——${stale.slice(0, 5).map((f) => `${f}(${actual[key][f] ?? 0}→应为预算 ${BUDGETS[key][f]})`).join("、")}。` +
          `把 ${key}_BUDGET 里对应数字改成当前计数（这就是这一批还掉的量，提交信息里写清楚是哪一页）。`
      );
    }
    const t = Object.values(actual[key]).reduce((a, b) => a + b, 0);
    console.log(`${TAG} [第 8 步待收敛] ${NAMES[key]}离刻度 ${t} 处（${Object.keys(actual[key]).length} 个文件，已按文件冻结预算）`);
  }
  if (process.argv.includes("--show-type-ledger")) {
    for (const key of ["FS", "LH", "LHL"]) {
      console.log(`${TAG} ${NAMES[key]}账本 ${ledgers[key].length} 行：`);
      for (const l of ledgers[key]) console.log("    " + l);
    }
  }
}

/* -------------------------------- 类名型按钮的角色台账（批次 AG 立，服务于 §2.5） */

/**
 * AF 把 §2.5 补进判据时只认「裸 button 元素型」选择器（`.x button`）。那只覆盖了一半：
 * 本仓还有一族按钮是靠**类名**被 CSS 命中的（`<RingButton className="desktop-search-command">`
 * 配 `.desktop-search-command:hover {}`），选择器里压根没有 button 这个词，元素型判据看不见它。
 *
 * 想补这条透镜，必须先解决一个反向风险：**不能靠挂载元素推角色**。
 * 本仓有两种都写成 `<button>` 的东西：
 *   · 控件按钮（顶部搜索、项目返回）——§2.5 要求它无阴影；
 *   · 可点卡片（背景卡、卡片列表项）——它只是把「整卡可点」实现成语义正确的
 *     `<button>`（里面装封面缩略图 + 标题 + 元信息，见 BackgroundPage / CardListSidebar）。
 *     §5.3 明明白白给静止卡片留了 --shadow-1 这一档。
 * 拿「只挂在 button 上」当判据去删后者的阴影，就是一次无声的界面改版——
 * 而这正是这一轮反复立誓不做的事（宁可漏判，不可误删活的视觉）。
 *
 * 所以角色必须是**登记出来的数据**，不是猜出来的：
 *   · CONTROL_BUTTON_CLASSES —— 判成控件：§2.5 生效，带投影层级 box-shadow 即红。
 *   · CARD_BUTTON_CLASSES —— 判成卡片：§2.5 不适用，投影由 §5.3 刻度棘轮管着（照旧计债）。
 *   · 两者都不在、却带着投影层级的纯按钮类名 —— 红，要求人先判角色再登记。
 *     这一条是判据的全部价值所在：新出现一个类名按钮，不能被静默归进任何一边。
 *
 * 「纯按钮类名」的判据刻意取窄（宁漏不误判）：
 *   · 只认 kebab 业务类名（含 -，且不是 Tailwind 工具类前缀）——active/confirm/csv 这类
 *     状态词与模板三元分支值也会只挂在 button 上，拿它们当按钮类名会大面积误伤；
 *   · 要求该类的**每一处**挂载都是按钮（button / RingButton / Button），混一处 div 就不判；
 *   · RingButton 与 Button 都实测把 className 原样落在原生 <button> 上
 *     （interaction.tsx 的 RingButton、ui.tsx 的 Button），所以算按钮。
 *
 * 挂载扫描与 classesHostingButton 不同源、也不同用途：那条找「内部渲染了 <Button> 的容器」，
 * 本条找「这个类名挂在什么元素上」。两者都是 JSX 文本扫描，都受同一个跨文件组件边界盲区
 * 限制（见上面批次 C 的记录），所以本条**只用于收窄判据**——判据错的方向只会是漏判，
 * 不会是误删。
 */
const CONTROL_BUTTON_CLASSES = ["desktop-search-command", "project-nav-back"];
const CARD_BUTTON_CLASSES = ["background-card", "cards-list-item"];

const buttonClassMounts = (() => {
  const TW_PREFIX = /^(bg|text|border|rounded|shadow|p|px|py|pt|pb|m|mx|my|mt|mb|w|h|flex|grid|gap|items|justify|font|leading|tracking|opacity|translate|scale|rotate|z|inset|top|bottom|left|right|size|min|max|overflow|whitespace|truncate|cursor|select|space|divide|ring|outline|from|to|via|animate|transition|duration|delay|ease|order|col|row|self|place|hidden|inline|block|absolute|relative|fixed|sticky|hover|focus|active|group|peer)(-|$)/;
  const BUTTONISH = new Set(["button", "RingButton", "Button"]);
  const mounts = new Map();
  const skipString = (s, from) => {
    const q = s[from];
    let j = from + 1;
    while (j < s.length && s[j] !== q) j += s[j] === "\\" ? 2 : 1;
    return j;
  };
  for (const p of allSource.filter((x) => /\.tsx$/.test(x) && !x.includes(`${path.sep}__tests__${path.sep}`))) {
    const src = stripComments(readFileSync(p, "utf8"));
    for (let i = 0; i + 1 < src.length; i++) {
      if (src[i] !== "<" || src[i + 1] === "/" || src[i + 1] === "!" || src[i + 1] === "?") continue;
      const m = /^<([A-Za-z][\w.-]*)/.exec(src.slice(i, i + 40));
      if (!m) continue;
      let depth = 0;
      let j = i + m[0].length;
      for (; j < src.length; j++) {
        const c = src[j];
        if (c === '"' || c === "'" || c === "`") { j = skipString(src, j); continue; }
        if (c === "{") depth += 1;
        else if (c === "}") depth -= 1;
        else if (c === ">" && depth === 0) break;
      }
      const open = src.slice(i, j + 1);
      // 必须用非贪婪的 \{[\s\S]*?\}：本仓大量写法是
      // className={`foo ${cond ? "bar" : ""}`}，若写成 \{[^{}]*\} 会被里面的 ${} 直接挡掉，
      // 于是这些类名一个都扫不到——而本透镜错的方向是「静默放行」，最坏的那种。
      for (const cm of open.matchAll(/className=(\{[\s\S]*?\}|"[^"]*"|'[^']*')/g)) {
        const raw = cm[1].replace(/\$\{[^}]*\}/g, " ").replace(/["'`{}]/g, " ");
        for (const c of raw.split(/[^A-Za-z0-9_-]+/)) {
          if (!/^[a-z][a-z0-9_-]*$/.test(c) || !c.includes("-") || TW_PREFIX.test(c)) continue;
          if (!mounts.has(c)) mounts.set(c, new Set());
          mounts.get(c).add(m[1]);
        }
      }
      i = j;
    }
  }
  const pure = new Set();
  for (const [c, tags] of mounts) {
    if ([...tags].length && [...tags].every((t) => BUTTONISH.has(t))) pure.add(c);
  }
  return pure;
})();

/* ---------------------------------- 规格 §2.5 按钮一律无阴影（第 8 步批次 AF 立） */

/**
 * 阴影棘轮有一道它自己看不见的水下裂缝：它豁免 var(--shadow-*)，因为「已经用上令牌」
 * 等于还了债。可 §2.5 说的是另一件事——**按钮一律无阴影**，`box-shadow` 只用于浮起层。
 * 于是把一个按钮的手抄投影「收敛」成 var(--shadow-1)，棘轮计数下降、守卫全绿，
 * 界面上按钮却照样带投影：一次看起来像还债的改动，实际是把违规写法升级成了
 * 看起来合规的违规写法。批次 AF 在产物胜者表里抓到这个形状正在发生
 * （某个页签按钮的 active 态胜者值就是 var(--shadow-1)），所以这条判据必须
 * **不豁免令牌**——它管的不是数值离不离刻度，而是这个元素该不该有投影。
 *
 * 「按钮角色」按 CSS 侧唯一可靠的信号认：选择器里出现裸 `button` 元素型
 * （`(^|[\s>+~(])button(?![\w-])`）。为什么不用 .btn 类名猜：本仓的按钮在 JSX 里是
 * `<button>` 元素（RingButton / 遗留容器规则接管的那一族），类名五花八门，
 * 而 CSS 要命中它们必须写 button —— 元素型就是这个物理事实本身。
 * `(?![\w-])` 挡 `buttons`，前缀组挡 `-button` 这种类名尾巴，别把 .foo-button 认成按钮元素。
 *
 * 豁免沿用它所属判据的同一条物理量口径（helper 复用棘轮那两个）：
 *   · `none`（明确关掉投影，正是 §2.5 要的结果）
 *   · 纯 inset（色条/内衬装饰，§5.3 四级本来就不针对它）
 *   · 纯描边环 0 0 0 Npx（§2.3 的 focus-visible 机制，批次 AD 立的对称豁免）
 * 唯一**不**豁免的是 var(--shadow-*)——那正是这道裂缝的入口。
 *
 * 冻结例外：`.desktop-page-actions button` 这一族。它是第 6 步遗留接管清单里
 * 已登记的同一批宿主（见上面 LEGACY_DESCENDANT_BUTTON），带 inset 冠光 + 真投影，
 * 收口它等于同时改掉 4 个未迁移页面的按钮外观，属于「迁移对应页面时收口」那一档，
 * 不在本批范围内。数字由本判据扫出，还掉一处不降清单也要红。
 */
{
  const BUTTON_ROLE = /(^|[\s>+~(])button(?![\w-])/;
  const FROZEN_BUTTON_SHADOW = [".desktop-page-actions"];
  // 数字由本判据扫出：这一族的声明形状是「hover 抬升 + 带 inset 冠光的组合投影」。
  // 它们和第 6 步 LEGACY_DESCENDANT_BUTTON 登记的是同一批宿主，随该页迁移一起收口。
  // 还掉一条却不降这个数字要红——逼着同一笔改动同时核对两本账，别让 §2.5 悄悄松绑。
  const FROZEN_BUTTON_SHADOW_COUNT = 3;
  const offenders = [];
  const frozenHits = [];
  const unregistered = [];
  // 判据自检用的命中台账：登记过的类名必须真的被本透镜看见
  const controlHits = new Set();
  const cardHits = new Set();
  const isProjection = (v) => {
    if (v === "none") return false;
    if (!shadowLayers(v).some((L) => !/^inset\b/.test(L))) return false;
    if (isPureRingValue(v)) return false;
    return true;
  };
  /** 选择器末位复合里的业务类名——决定「这条规则直接给谁画投影」。 */
  const lastCompoundClasses = (sel) => {
    const last = sel.split(/[\s>+~]/).pop() || "";
    return (last.match(/\.[A-Za-z][\w-]*/g) || []).map((x) => x.slice(1));
  };
  for (const cssFile of cssFiles) {
    const rel = path.relative(root, cssFile).split(path.sep).join("/");
    let ast;
    try {
      ast = postcss.parse(readFileSync(cssFile, "utf8"), { from: cssFile });
    } catch {
      continue;
    }
    ast.walkRules((rule) => {
      const sels = rule.selectors.map((s) => s.replace(/\s+/g, " ").trim());
      const elementRole = sels.some((s) => BUTTON_ROLE.test(s));
      const classRole = [...new Set(sels.flatMap((s) => lastCompoundClasses(s)))].filter((c) => buttonClassMounts.has(c));
      if (!elementRole && !classRole.length) return;
      rule.walkDecls(/^box-shadow$/, (d) => {
        const v = d.value.replace("!important", "").replace(/\s+/g, " ").trim();
        if (!isProjection(v)) return;
        const sel = rule.selector.replace(/\s+/g, " ").trim();
        for (const c of classRole) {
          if (CONTROL_BUTTON_CLASSES.includes(c)) controlHits.add(c);
          if (CARD_BUTTON_CLASSES.includes(c)) cardHits.add(c);
        }
        if (FROZEN_BUTTON_SHADOW.some((f) => sel.includes(f))) {
          frozenHits.push(`${rel}:${d.source.start.line} ${sel}`);
          return;
        }
        // 类名型按钮：卡片角色不归 §2.5 管（§5.3 给静止卡片留了档），控件角色才管。
        // 两边都没登记的，要求先判角色——不能静默归进任何一边。
        if (!elementRole && classRole.length) {
          const asControl = classRole.filter((c) => CONTROL_BUTTON_CLASSES.includes(c));
          const asCard = classRole.filter((c) => CARD_BUTTON_CLASSES.includes(c));
          if (!asControl.length && !asCard.length) {
            unregistered.push(`${rel}:${d.source.start.line} ${sel} { box-shadow: ${v} } [${classRole.join(",")}]`);
            return;
          }
          if (asCard.length && !asControl.length) return;
        }
        offenders.push(`${rel}:${d.source.start.line} ${sel} { box-shadow: ${v} }`);
      });
    });
  }
  // 透镜退化自检（不变量 #8 同族）：登记的类名若不再被认成纯按钮类名，说明
  // 要么挂载被挪到非按钮元素上（角色变了，该从台账里删），要么扫描口径被动窄
  // （危险方向：它会把手写的控件按钮投影静默放行）。
  const blindControl = CONTROL_BUTTON_CLASSES.filter((c) => !buttonClassMounts.has(c));
  if (blindControl.length) {
    fail(
      `第 8 步 §2.5 类名透镜退化：登记的控件按钮类名 ${blindControl.join("、")} 不再被认成「只挂在按钮上」——` +
        `要么它已被挪用到非按钮元素（角色变了，请从 CONTROL_BUTTON_CLASSES 删掉并复核那处样式），` +
        `要么挂载扫描口径被改窄（危险方向：会把控件按钮的投影静默放行）。`
    );
  }
  const blindCard = CARD_BUTTON_CLASSES.filter((c) => !buttonClassMounts.has(c));
  if (blindCard.length) {
    fail(
      `第 8 步 §2.5 卡片侧台账失配：登记的「按卡片算」类名 ${blindCard.length} 个已不再是纯按钮类名——${blindCard.join("、")}。` +
        `这些类名的宿主不再是 button/RingButton/Button，请复核它现在挂在哪、并把它从 CARD_BUTTON_CLASSES 移到合适的一侧；` +
        `留在台账里会让这条豁免继续挡掉本该由 §5.3 判定的东西。`
    );
  }
  if (unregistered.length) {
    fail(
      `第 8 步 §2.5 角色未登记：${unregistered.length} 条规则的宿主是「只挂在按钮上的类名」，却带着投影层级 box-shadow，` +
        `且不在控件/卡片任一台账里——${unregistered.slice(0, 3).join(" / ")}。` +
        `请先判角色再登记：控件按钮（§2.5 一律无阴影）进 CONTROL_BUTTON_CLASSES 并删掉投影；` +
        `可点卡片（§5.3 静止卡片有 --shadow-1）进 CARD_BUTTON_CLASSES。判据不替你猜——` +
        `猜错的方向是「无声删掉活着的界面」。`
    );
  }
  if (offenders.length) {
    fail(
      `规格 §2.5 按钮一律无阴影：${offenders.length} 条按钮角色规则仍带投影层级 box-shadow——${offenders.slice(0, 4).join(" / ")}。` +
        `本判据**不豁免 var(--shadow-*)**：把按钮投影「收敛成令牌」只是把违规写得像合规，棘轮计数会降而界面纹丝不动。` +
        `按钮的 active/selected 请用底色、边框、字重表达（§2.5 纯色彩反馈）；` +
        `焦点环 0 0 0 Npx（§2.3）与纯 inset 色条不在本管。`
    );
  }
  if (frozenHits.length !== FROZEN_BUTTON_SHADOW_COUNT) {
    fail(
      `规格 §2.5 冻结计数对不上：.desktop-page-actions 一族实测 ${frozenHits.length} 条、清单登记 ${FROZEN_BUTTON_SHADOW_COUNT} 条。` +
        `${frozenHits.slice(0, 3).join(" / ")}。要么这族按钮随第 6 步迁移又还掉/新增了一条（同步本数字，并与 LEGACY_DESCENDANT_BUTTON 一起收口），` +
        `要么判据的按钮元素型或 inset/环豁免被动过。`
    );
  }
  console.log(
    `${TAG} [第 8 步待收口] §2.5 按钮投影遗留 ${frozenHits.length} 条，全部属于 .desktop-page-actions 接管族（已冻结，迁移该页时随第 6 步清单一起删）；` +
      `类名型按钮台账：控件 ${CONTROL_BUTTON_CLASSES.length} 个（命中 ${controlHits.size}）、按卡片算 ${CARD_BUTTON_CLASSES.length} 个（命中 ${cardHits.size}）`
  );
}

/* ------------------------------------ --shadow-1 的 hairline 不得与同规则 border 共存（第 8 步） */

/**
 * 批次 AE 立的不变量。它不是审美条款，是几何条款，来自一次具体的迁移：
 *
 * §5.3 把 --shadow-1 定义为 `0 1px 2px rgba(15,20,28,.06), 0 0 0 1px var(--separator-subtle)`
 * ——第二层是偏移/模糊全为 0、spread 1px 的**四周 hairline 描边**。所以「静止卡片有边框」
 * 这件事在 --shadow-1 里已经包办了，不需要再写 border。
 *
 * 而 styles.css 把 --border-subtle 直接 alias 成 --separator-subtle（同一条线、同一个色）。
 * 于是一条规则里同时写 `border: 1px solid var(--border-subtle)` 和 `box-shadow: var(--shadow-1)`
 * 不是「更稳」，而是把同一条 1px 边**并排拼成 2px**：border 占盒子内侧 1px，
 * box-shadow 的描边画在盒子外侧 1px，肉眼看到的是双线/厚边，且 padding 被 border 吃掉 1px。
 * 这正是这一批 4 条工作台级容器（.project-workbench / .cards-board-layout+.outline-page-body /
 * .inbox-editor-grid / .desktop-inspiration-page）的老写法——手抄投影 + 手抄 border，
 * 归到 --shadow-1 时必须同时删掉那条 border，否则「用了令牌」反而比手抄更糟。
 *
 * ⚠ 判据只管 shadow-1，不管 shadow-2/3/4：那三级是纯投影（菜单/对话框/拖拽幽灵），
 * 不含 0 0 0 1px 层，和 border 共存是正常设计（对话框就常有 border + 浮起阴影）。
 * 所以这条不是「border 和 box-shadow 不能同时出现」，而是专门拦「hairline 双拼」。
 *
 * ⚠ 只管**同一条规则**内的共存。判跨规则的 border 需要完整的选择器→元素映射，
 * 代价远高于收益（同族教训见批次 X：跨规则层叠推断不做）。
 * 侧向描边（border-top / border-left 这类）**照样判**：它不是「不同向所以安全」——
 * 环在四条边都存在，任何一条边上再写可见 border，就是那条边拼成 2px，和整圈双拼同一个病，
 * 只是症状局部。真正放过的只有 border-color（宽度为 0 时它是哑的，不建几何）
 * 和 0 / none / hidden 这些不画线的值。
 */
{
  const COLLIDERS = [];
  const BORDER_GEOM = /^(border|border-width|border-style|border-(top|right|bottom|left)(-(width|style))?)$/;
  const INVISIBLE = /^(0(\s|px)?|none|hidden|initial)(\s|;|$)/;
  for (const cssFile of cssFiles) {
    const rel = path.relative(root, cssFile).split(path.sep).join("/");
    let ast;
    try {
      ast = postcss.parse(readFileSync(cssFile, "utf8"), { from: cssFile });
    } catch {
      continue;
    }
    ast.walkRules((rule) => {
      let shadow = null;
      const borders = [];
      rule.each((n) => {
        if (n.type !== "decl") return;
        if (n.prop.toLowerCase() === "box-shadow") shadow = n.value;
        const prop = n.prop.toLowerCase();
        if (!BORDER_GEOM.test(prop)) return;
        if (INVISIBLE.test(n.value.trim())) return;
        borders.push(`${prop}: ${n.value.trim()}`);
      });
      if (shadow === null || !/var\(--shadow-1\b/.test(shadow)) return;
      if (borders.length === 0) return;
      COLLIDERS.push(`${rel}:${rule.source.start.line} ${rule.selector.replace(/\s+/g, " ").trim()} { ${borders.join("; ")} } + box-shadow: ${shadow.trim()}`);
    });
  }
  /* 批次 AO 补的 TSX/TS 侧同判据。不补就是本批判据的裂缝：CSS 侧管不到的那半边
   * ——把静止卡收敛成 [box-shadow:var(--shadow-1)] 是棘轮豁免的合法写法（计数会降），
   * 可同串里若还挂着 border 工具类，AE 说的 2px 双线边照样成立，判据却看不见。
   * ⚠ 只判**无状态变体前缀**的裸宽度类（lookbehind 把 `:` `.` `/` `-` 都挡在外面）：
   *   hover:border-x-2 这类跨状态的组合需要「哪个状态下同时生效」的映射，判它等于猜，
   *   方向永远保守（宁可漏判不误删，同批次 X 的教训）。
   * ⚠ border-0 一族不判（宽度归零，哑的）；border-<颜色> 一族不判（只 --tw-border-opacity
   *   上色，不建几何）——两条豁免与 CSS 侧的 INVISIBLE / 只放过 border-color 完全同形。
   * ⚠ 只认能真发射的属性形式 [box-shadow:var(--shadow-1)]：方括号工具类 shadow-[var(...)]
   *   在产物里只产 --tw-shadow-color、压根没有 box-shadow 声明（批次 K 实测），
   *   它自己另有硬红判据，这里不去重复判一个画不出阴影的东西。
   */
  const TSX_SHADOW1 = /(?<![:A-Za-z0-9_-])\[box-shadow:var\(--shadow-1\)\]/;
  const TSX_BORDER_WIDTH = /(?<![A-Za-z0-9_:./-])border(?:-[trblxy])?(?:-0|-[248])?(?![\w[-])/g;
  for (const p of allSource.filter((x) => /\.(tsx|ts)$/.test(x) && !x.includes(`${path.sep}__tests__${path.sep}`))) {
    const rel = path.relative(root, p).split(path.sep).join("/");
    preserveNewlines(readFileSync(p, "utf8"))
      .split("\n")
      .forEach((line, i) => {
        if (!TSX_SHADOW1.test(line)) return;
        const bs = (line.match(TSX_BORDER_WIDTH) ?? []).filter((t) => !t.endsWith("-0"));
        if (!bs.length) return;
        COLLIDERS.push(`${rel}:${i + 1} 类名串 { ${bs.join(", ")} } + [box-shadow:var(--shadow-1)]`);
      });
  }
  if (COLLIDERS.length) {
    fail(
      `第 8 步 hairline 双拼：${COLLIDERS.length} 条规则在同一规则里把可见 border 和 var(--shadow-1) 叠在一起——` +
        `--shadow-1 自带的 0 0 0 1px var(--separator-subtle) 与 border（--border-subtle 就是它的别名）` +
        `一条画在盒内一条画在盒外，同色并排拼成 2px 双线边（只写某一侧也一样，那条边同样翻倍）。` +
        `删掉 border，让 --shadow-1 单独包办边框。命中：${COLLIDERS.slice(0, 5).join(" / ")}`
    );
  }
}

/* -------------------------------------------------- 宿主谓词（幻影账与死分支判据共用） */

/**
 * 「这个业务类名能不能被界面渲染出来」的唯一权威实现。
 * 两副透镜都靠它：幻影账按整条规则问（这条规则画的元素存在吗），
 * 批次 AI 的死分支判据按单个选择器分支问（这一支画的元素存在吗）。
 * 批次 AD 立的规矩：同一个物理量在两副透镜下必须是同一个数——所以这里只有一份，
 * 谁也不许自带副本。两副透镜也都沿用同一条保守边界：
 * 查不到业务类名的分支（宿主可能来自工具类/元素选择器）一律不判。
 * 危险方向永远是「把活 CSS 判成死的」，那会让一次删除变成无声改版。
 *
 * 语料的两个方向都错过（批次 O 的记录）：
 *   · 只看源码字符串会把 `writing-quick-kind--${card.kind}` 这类 BEM 修饰判死——
 *     完整串不在源码里，所以额外收集「紧邻 ${ 的类名片段」当动态前缀。
 *   · 把 electron/** 也算宿主语料，会把主进程拼的设备 ID / 记录 ID
 *     （`desktop-${hash}`）当成类名落点，整族 desktop-* 全部误判成活。
 *     主进程碰不到渲染 DOM，渲染侧语料只取 src/** + 根 index.html。
 * 产物 JS 是最硬的证据（JSX className 原样进 bundle），但守卫可能在未构建时运行，
 * 所以产物只作补充证据、不作必要条件。
 */
const hostPredicate = (() => {
  const hostFiles = allSource.filter((x) => /\.(tsx|ts|js|jsx|html|json)$/.test(x) && !x.includes(`${path.sep}__tests__${path.sep}`));
  if (existsSync(path.join(root, "index.html"))) hostFiles.push(path.join(root, "index.html"));
  const hostText = hostFiles.map((f) => readFileSync(f, "utf8")).join("\n");
  const dynPrefix = new Set();
  for (const f of hostFiles) {
    if (!/\.[jt]sx?$/.test(f)) continue;
    const t = readFileSync(f, "utf8");
    for (const m of t.matchAll(/([A-Za-z][A-Za-z0-9_-]*)\$\{/g)) dynPrefix.add(m[1]);
    for (const m of t.matchAll(/["'`]([A-Za-z][A-Za-z0-9_-]*-)["'`]\s*\+/g)) dynPrefix.add(m[1]);
  }
  const bundleText = (() => {
    const dir = path.join(root, "out", "renderer");
    if (!existsSync(dir)) return "";
    return readdirSync(dir)
      .filter((f) => f.endsWith(".html") || (f.endsWith(".js") && existsSync(path.join(dir, "assets", f))))
      .map((f) => { try { return readFileSync(path.join(dir, f), "utf8"); } catch { return ""; } })
      .join("\n") +
      (existsSync(path.join(dir, "assets"))
        ? readdirSync(path.join(dir, "assets")).filter((f) => f.endsWith(".js")).map((f) => readFileSync(path.join(dir, "assets", f), "utf8")).join("\n")
        : "");
  })();

  const canRender = (cls) =>
    hostText.includes(cls) || bundleText.includes(cls) || [...dynPrefix].some((p) => p.length >= 3 && cls.startsWith(p));

  /**
   * 只取选择器的「正向」类名：:not(.x) 里的 x 是被排除的，它的宿主在别处。
   *
   * 批次 AU：删掉了这里的 Tailwind 前缀滤网。旧写法把以 bg|text|outline|font…
   * 开头的类名滤掉，本意是跳过工具类（「宿主」对它们不适用），但业务类名照样能撞
   * 上这张表：.outline-scene / .outline-scene-meta / .font-mono / .bg-paper-bg
   * 全是业务类名，被滤成空集后触发「这一支查不到业务类名 → 整条保守不判」的豁免，
   * 而豁免是**整条规则连坐**的——editorial 那条
   * `.writing-chapter-button, .writing-scene-button, .outline-scene` 里前两支
   * 类名完好（writing- 不撞 w(-|$)），只因为第三支 .outline-scene 被吞，整条隐身，
   * 两支真死的类名照样发射进产物。AT 挖出这个形状时，死分支账与幻影账同时失明。
   *
   * 不滤之后，「宿主查无」的判断对工具类同样成立，而且成立得更干净：TSX 的
   * className 字面量写什么，hostText 里就是什么（.font-mono / .grid-cols-2 /
   * .mt-3 实测都在宿主文本里，判活）；动态拼接的工具类走 dynPrefix，和
   * MUST_BE_LIVE 那族 BEM 修饰类同一条路。只有「既不在源码、又不在产物 JS、
   * 又不撞动态前缀」的类名会翻成死——那正是查无宿主本身，与它叫不叫工具类无关。
   * 豁免仍然保留给**无类名分支**（button、.cards-toolbar button 这类元素选择器：
   * 宿主可能来自任何地方，保守不判，方向不变）。
   *
   * 收紧前后差集都算过（/tmp 模拟脚本，两副透镜逐规则逐分支对比）：
   *   · 滤空→回退判原始类名（第一层）：92 条规则转为可判、全部有活支兜底，
   *     幻影账 0 新增；死分支账暴露 3 条混死支（editorial:935×2 / 2872×1），
   *     属性 border-radius: var(--radius-1) / 纯 inset 描边 / font-family，
   *     令牌债 = 0——三条随本批同提交摘支。
   *   · 回退→彻底不滤（第二层）：全仓只差 1 条规则，styles.css:860
   *     `.desktop-canvas .bg-paper-bg`——活祖先 + 被吞的死后代，正是混支形状，
   *     background-color 无几何债，随本批整条删除。
   * 真把活 CSS 判死的方向由 MUST_BE_LIVE 与 MUST_BE_SEEN 两份哨兵夹住：
   * 前者守 canRender 变窄，后者守有人把前缀滤网加回来。
   */
  const positiveClasses = (sel) => {
    let s = sel;
    for (let g = 0; g < 40; g++) {
      const i = s.indexOf(":not(");
      if (i < 0) break;
      let depth = 0;
      let j = i + 4;
      for (; j < s.length; j++) {
        if (s[j] === "(") depth += 1;
        else if (s[j] === ")") { depth -= 1; if (!depth) { j += 1; break; } }
      }
      s = s.slice(0, i) + "" + s.slice(j);
    }
    return [...new Set((s.match(/\.[A-Za-z_][A-Za-z0-9_-]*/g) || []).map((x) => x.slice(1)))];
  };

  return { canRender, positiveClasses };
})();

/* ------------------------------------------------ 幻影选择器的令牌债冻结预算（第 8 步） */

/**
 * 批次 N 和 O 是同一族缺陷的两种死法，前面各自只收了其中一种：
 *   · 批次 N 收的是「层叠上被压死」——宿主存在，但每条分支都输给更晚/更具体的规则。
 *   · 批次 O 收的是「宿主根本不存在」——选择器里的业务类名在渲染侧查无落点，整条规则
 *     一次都没画过。这类声明上面两条棘轮照样数它（它们不看宿主），所以光看「离刻度还剩
 *     多少处」分不清哪些是真能还的：幻影债务不迁移就永远还不完，只能删。
 * 判据要守的正是第二种，否则下一批人会继续给画不出来的元素设计令牌档位。
 *
 * 「宿主不存在」的语料怎么划、两个方向各会怎么错过，写在上面 hostPredicate 那一份
 * 权威实现里（批次 O 的记录）。本判据和下面的死分支判据都只用它，谁也不带副本。
 *
 * 判据形状沿用棘轮那套「先变成数据、再一页一页还」：
 *   每条无宿主规则里的离刻度圆角 + 非 var 的投影声明，按文件冻结。
 *   给幻影选择器新增令牌债要红；顺手删掉一块幻影 CSS 不降预算也要红。
 * 这里的数字不是「还要还多少」，而是「允许界面外的 CSS 再胖多少」——真还法只有删规则。
 */
{
  const { canRender, positiveClasses } = hostPredicate;

  /**
   * 判据自检：本判据唯一的致命方向是「把活 CSS 判成幻影」——那样删除是无声的界面改版。
   * 所以钉一组已知必须由动态拼接才活着的类名：判据说它们查无宿主，就是判据自己退化了
   * （例如有人把「紧邻 ${ 的类名片段」这条正则简化回「引号紧跟 ${」）。
   * 这条清单同时是「孤儿 CSS 报警器」：产品真的把某个动态类名拆掉时，这里会变红，
   * 提醒那一族规则现在没人渲染了——而不是让判据安静地把它们划进幻影预算。
   */
  const MUST_BE_LIVE = [
    "writing-quick-kind--location",
    "writing-quick-save--dirty",
    "format-chip--epub",
    "inbox-save-status--saving",
    "card-board-card-status--planned",
    "scene-save-status--saved",
    "desktop-brand-mark"
  ];
  const blind = MUST_BE_LIVE.filter((c) => !canRender(c));
  if (blind.length) {
    fail(
      `第 8 步幻影选择器：宿主判据把 ${blind.length} 个正在渲染的类名看成了死代码——${blind.join("、")}。` +
        `这些类名靠动态拼接挂在 DOM 上（完整串不在源码里）。要么判据的动态前缀正则被改窄了（危险方向：会把活 CSS 判死），` +
        `要么对应组件真的不再挂这个类名了（那这族 CSS 已成孤儿，请删掉并同步本清单）。`
    );
  }

  /**
   * 批次 AU 的退化哨兵：上面那份自检守的是 canRender 变窄，守不到 positiveClasses——
   * 有人把前缀滤网加回来（哪怕只是悄悄补一行 `if (TW_PREFIX.test(c)) continue;`），
   * 这三个业务类名会重新滤成空集，连带整条规则躲进保守豁免；两本账安静地少看一片 CSS，
   * 而且少看的方向是「不再有新债被咬住」——正是最难事后发现的那类退化。
   * 钉的三个类名都是**真活**的业务类名且以 TW 前缀开头（outline- / font-）：滤网回来
   * 它们判空，但今天必须判出东西来。选活类名而不是已删的死类名（.outline-scene-meta、
   * .bg-paper-bg 也撞前缀，但宿主已清零），是因为死类名会在下一批被顺手删干净，
   * 导致这颗哨兵无故失效。
   */
  const MUST_BE_SEEN = ["outline-scene", "outline-page", "font-mono"];
  const eaten = MUST_BE_SEEN.filter((c) => positiveClasses(`.${c}`).length === 0);
  if (eaten.length) {
    fail(
      `第 8 步幻影选择器：positiveClasses 把 ${eaten.length} 个真实存在的业务类名滤成了空集——${eaten.join("、")}。` +
        `它们以 Tailwind 工具类前缀开头（outline- / font-），却和 .card-board 一样是挂在 DOM 上的业务类名；` +
        `批次 AU 的修法是把前缀滤网整个删掉（工具类的宿主照样在 hostText 的 className 字面量里，判活），` +
        `所以这条红意味着滤网被加了回来。滤网回来后这些类名所在的整条规则会躲进` +
        `「查不到业务类名 → 保守不判」的豁免——那是给 button 这类无类名分支留的，` +
        `不是给业务类名留的后门；死分支和幻影债都会随它安静隐身。`
    );
  }

  /**
   * 冻结预算。数字由本判据扫出。
   * ⚠ 这张表不是第三笔债，而是给上面两张棘轮的同一批数字加一个「宿主在哪」的透镜：
   *   幻影声明照样计入 RADIUS_BUDGET / SHADOW_BUDGET（那两条判据不看宿主，这是刻意的——
   *   一旦让棘轮也看宿主，判错方向的代价就从「多删一行死代码」变成「少盯住一处活债」）。
   *   所以删掉一条幻影规则会同时降两处的数字，两边都要跟着改；
   *   反过来，本表里的数字只能靠删规则还，没有迁移这条路。
   */
  const HOSTLESS_BUDGET = {
    // 债主迁移史：.pe-modal（批次 AR 删）→ .desktop-settings-grid 岛（批次 AS 删）→ 无。
    // 批次 AR：encryption.css 的 2 处随 .pe-modal 族整族下线（那族规则连宿主都没有，
    // 除了删规则没有第二条还法），条目移除。
    // 批次 AS：editorial-studio.css 最后 1 处随 settings-grid 开关岛整族下线。
    // 批次 AH 把 styles.css 桌面首页/创作索引遗留族的 7 处幻影债整族删干净，条目随之移除：
    // 这份全局表里任何「查无宿主又离刻度」的声明从此都是新增债，直接红。
    // 表空 = 幻影令牌债清零。任何文件新增一笔幻影债（或收紧透镜后暴露出旧债）都会
    // 立刻命中 hlOver——预算 0 是最紧的形态，不需要再留任何条目。
    // 批次 AT：查无宿主的规则本身也从 132 条清到 0 条（styles.css 104 / editorial 13 /
    // history-local 11 / inbox-local 2 / operation 1 / overview 1；连带 1 条孤儿
    // @keyframes scene-spin 与 3 条只罩着已删规则的悬空注释，另 1 条转成锚点墓碑）。
    // 但「条数=0」刻意**不**钉成不变量：
    //   · 这副透镜数的是令牌债，不是「没宿主」这件事本身——三条反向牙（O·反/AH·反/AR·反）
    //     钉的就是这个决定：孤儿规则写刻度上的 6px、var(--shadow-2) 一律合法。
    //   · 把条数钉成零容忍，等于让「宿主判据把活 CSS 误判死」从一次红变成整条构建红，
    //     误判方向的爆炸半径从「多删一行死代码」升级为「CI 瘫痪」；而无害孤儿 CSS 的
    //     清理本来就是人工按族做的另一笔账（AH/AN…AT 全是这个形状）。
    // 新增债仍即时红；新增纯布局孤儿只降不改账，留给下一族人工清。
  };

  const hostlessActual = {};
  let hostlessRules = 0;
  const SCALE_PX = new Set([0, 4, 6, 10, 999]); // 与上面圆角棘轮同一条刻度（§5.2）
  for (const p of cssFiles.filter((x) => !x.includes(`${path.sep}__tests__${path.sep}`))) {
    const rel = path.relative(root, p).split(path.sep).join("/");
    let ast;
    try {
      ast = postcss.parse(readFileSync(p, "utf8"), { from: p });
    } catch {
      continue;
    }
    ast.walkRules((r) => {
      const per = r.selectors.map((s) => positiveClasses(s.replace(/\s+/g, " ").trim()));
      if (!per.length || per.some((x) => !x.length)) return; // 有分支查不到业务类名 → 宿主可能来自工具类/元素选择器，不判
      /**
       * 批次 AS：口径从「整条规则」收到「逐分支」。
       * 旧写法 per.flat().some(canRender)——任一活类名就整条放过，于是漏掉
       * 「死祖先 + 活后代」的复合分支：`.desktop-settings-grid .settings-wide`
       * 里 .settings-wide 还在别处活着，但这条分支的祖先查无宿主，
       * CSS 上这条复合选择器永远匹配不到任何元素——它和整条死规则一样画不出像素。
       * 新口径：一条规则只有存在「每个类名都能渲染」的分支才算活着。
       * 收紧前先算了差集（/tmp/as-lensdelta.cjs）：146→153 条、新暴露 7 条，
       * 逐条审计（/tmp/as-branchaudit.cjs）全部是布局属性（margin-top/display/
       * grid-column/flex/min-width 一类），几何令牌债 = 0——
       * 也就是说收紧本身不还债、不红，只是把形状钉进判据；债在下面三条
       * 岛屿规则删除时一并归零（editorial L2371 那笔是真债，进 SHADOW_BUDGET）。
       */
      const branchAlive = (cs) => cs.every(canRender);
      if (per.some(branchAlive)) return;
      hostlessRules += 1;
      for (const d of r.nodes) {
        if (d.type !== "decl") continue;
        let debt = false;
        if (/^border-radius/.test(d.prop)) {
          const toks = d.value.replace("!important", "").trim().split(/\s+/);
          debt = toks.some((t) => /^-?\d/.test(t) && !t.includes("%") && !SCALE_PX.has(parseFloat(t)));
        } else if (/^box-shadow$/.test(d.prop)) {
          const v = d.value.replace("!important", "").replace(/\s+/g, " ").trim();
          // ⚠ 与上面阴影棘轮共用同一组 helper（shadowLayers / isPureRingValue）：
          // 同一个物理量在两副透镜下必须是同一个数。这里若各写各的层叠判断，
          // 就会出现「棘轮豁免了、幻影账还计着」的对不上账——批次 AD 修的就是这种不对称。
          if (v === "none" || /var\(--(shadow|focus)/.test(v)) debt = false;
          else if (!shadowLayers(v).some((L) => !/^inset\b/.test(L))) debt = false;
          else if (isPureRingValue(v)) debt = false;
          else debt = true;
        }
        if (debt) hostlessActual[rel] = (hostlessActual[rel] ?? 0) + 1;
      }
    });
  }
  const hlOver = Object.keys(hostlessActual).filter((f) => hostlessActual[f] > (HOSTLESS_BUDGET[f] ?? 0));
  const hlStale = Object.keys(HOSTLESS_BUDGET).filter((f) => HOSTLESS_BUDGET[f] > (hostlessActual[f] ?? 0));
  if (hlOver.length) {
    fail(
      `第 8 步幻影选择器：${hlOver.length} 个文件给画不出来的元素新增了令牌债——${hlOver.slice(0, 5).map((f) => `${f}(${hostlessActual[f]}/${HOSTLESS_BUDGET[f] ?? 0})`).join("、")}。` +
        `这些选择器的类名在渲染侧（src/** 与根 index.html）和产物里都查无落点，界面永远不会渲染。` +
        `要么这个类名是刚加进去、宿主还没接上（那请把宿主接上，本判据随即放过），要么就是死 CSS——删掉整条规则，别给它配令牌档位。`
    );
  }
  if (hlStale.length) {
    fail(
      `第 8 步幻影选择器：${hlStale.length} 个文件删了幻影规则但预算没跟着降——${hlStale.slice(0, 5).map((f) => `${f}(${hostlessActual[f] ?? 0}→应为预算 ${HOSTLESS_BUDGET[f]})`).join("、")}。` +
        `把 HOSTLESS_BUDGET 里对应数字改成当前计数。`
    );
  }
  const hlTotal = Object.values(hostlessActual).reduce((a, b) => a + b, 0);
  console.log(`${TAG} [第 8 步幻影选择器] 查无宿主的规则 ${hostlessRules} 条，其中令牌债 ${hlTotal} 处（${Object.keys(hostlessActual).length} 个文件，已冻结；真还法只有删规则）`);
}

/* --------------------------- 第 8 步批次 AI：活规则里不许藏死选择器分支（零容忍） */

/**
 * 批次 AI 立这条判据时，上面那副幻影透镜的口径是「整条规则」：
 * per.flat().some(canRender) 一命中就整条放过。
 * 于是有一类债它天生看不见——组选择器里死分支躲在活分支后面：
 *   `.desktop-page-actions button, .project-home-create-first { … }`
 * 前者还在渲染、后者早已零引用，整条被判「活着」，可它给一个画不出来的元素配着
 * 阴影/圆角，两本账都不记。批次 AH 删完整族之后，这个形状成了幻影债唯一的藏身处：
 * 实测本仓 4 份样式表里藏着 56 条这样的死分支（editorial 50 / styles.css 4 /
 * relation-graph 1 / history-local 1），其中 7 处带着离刻度投影——
 * 也就是说阴影账「editorial 16 处」里有 7 处的数字是真的，却有 7 处从来渲染不出来。
 *
 * ⚠ 批次 AS 把幻影透镜收到「逐分支」口径（存在一条全活分支才算规则活着），
 * 两副透镜从此共用同一个「活」的定义——那条口径变更的注释在上面的 HOSTLESS 块里，
 * 差集证据（新暴露 7 条、全部无几何债）也写在那里。这条判据的 dead 谓词同步改成
 * every(canRender)，两边数字才是同一个物理量（批次 AD 立的对称规矩）。
 *
 * 修法与还法都只有「摘掉那条选择器」：分支死了、规则还活着，声明本身可能仍在给
 * 活分支供值，所以只删选择器行、不碰声明。
 *
 * 判据与上面那副透镜共用同一个 canRender / positiveClasses（批次 AD 立的规矩：
 * 同一个物理量在两副透镜下必须是同一个数），并且沿用同一条保守边界——
 * 只要有任一分支查不到业务类名（宿主可能来自工具类/元素选择器），整条不判。
 * 危险方向仍然是「把活分支判死」，那会让一次摘分支变成无声改版；
 * 这条判据只负责把它变成一次红，删除动作永远由人做。
 * 宿主谓词整体退化（把一切都判死）不会让这里假绿：那条路会让上面 MUST_BE_LIVE
 * 自检先红，两侧共用同一个 canRender，所以共用同一个哨兵。
 */
{
  const { canRender, positiveClasses } = hostPredicate;

  const ghosts = [];
  for (const p of cssFiles.filter((x) => !x.includes(`${path.sep}__tests__${path.sep}`))) {
    const rel = path.relative(root, p).split(path.sep).join("/");
    let ast;
    try {
      ast = postcss.parse(readFileSync(p, "utf8"), { from: p });
    } catch {
      continue;
    }
    ast.walkRules((r) => {
      if (r.selectors.length < 2) return;
      const brs = r.selectors.map((s) => s.replace(/\s+/g, " ").trim());
      const cls = brs.map((s) => positiveClasses(s));
      if (cls.some((c) => !c.length)) return; // 有分支查不到业务类名 → 整条不判（同幻影账）
      // 批次 AS：与幻影账同步收到逐分支口径——分支「死」的定义是
      // 没有一个组合能同时匹配（每个类名都能渲染才算活），死祖先+活后代
      // 同样算死支。两副透镜共用同一个 canRender，也就必须共用同一个「活」的定义。
      const dead = brs.filter((s, i) => !cls[i].every(canRender));
      if (!dead.length || dead.length === brs.length) return; // 全活 / 全死（全死归幻影账）
      const props = r.nodes.filter((n) => n.type === "decl").map((n) => n.prop);
      ghosts.push(`${rel}:${r.source.start.line} 死分支=${dead.join(" / ")}（同规则活分支 ${brs.length - dead.length} 个，属性 ${props.join(",")}）`);
    });
  }
  if (ghosts.length) {
    fail(
      `第 8 步死分支：${ghosts.length} 条规则同时罩着活元素和查无宿主的元素——死的那支让声明在两本账里都不露面（幻影账看整条规则，一命中活分支就整条放过）。` +
        `批次 AI 已把这 56 条摘干净，从此这是零容忍不变量：要么把宿主接上，要么只删那条选择器（声明可能仍在为活分支供值，别顺手删声明）。命中：${ghosts.slice(0, 4).join(" ｜ ")}`
    );
  } else {
    console.log(`${TAG} [第 8 步死分支] 活规则里查无宿主的选择器分支 0 条（批次 AI 起零容忍）`);
  }
}

/* ---------------------------------- 浮层容器类名不得被裸选择器接管 border-radius */

/**
 * 批次 F 的教训，和上面那条 .motion-toast background/color 不变量同族：
 * editorial-studio.css 原先有一条
 *   `.migration-banner, .motion-dialog, .motion-toast, .motion-drawer, .confirm-dialog
 *    { border-radius: 12px !important }`
 * !important 让它必然赢，组件自己挂在 className 上的 rounded-[var(--radius-*)]
 * 一律画不出来（实测：确认框写 rounded-2xl、toast 写 rounded-xl，实际渲染的一直
 * 是这条 12px）。而且 .motion-drawer 本来没有自带圆角、全靠这条撑着，所以它不能
 * 只删不改——批次 F 是「删这条 + 每个浮层各自挂刻度圆角」一起做完的。
 *
 * 判据只封「一条裸浮层类名规则（无祖先/组合符/伪类）用 !important 声明 border-radius」，
 * 也就是 blanket 的确切形状。以下两种合法写法不误伤：
 *   · .migration-banner { border-radius: var(--radius-3) }（无 !important）：
 *     该元素在 TSX 里不带任何 rounded 工具类，这条 CSS 是它圆角的唯一来源，
 *     不是遮蔽，是「样式表自持」——和 motion-toast 那族「组件自带工具类」正好相反。
 *   · .creation-writing-page--active .migration-banner { border-radius: 0 !important }：
 *     带模式前缀，是专注模式刻意压平的显式决定，不是全局接管。
 * 残余风险（本判据不覆盖，靠人工 + jsdom 核对）：裸浮层类名 + 非 !important 的
 * border-radius，若写在 editorial-studio.css（utilities 之后加载）仍可能压掉组件工具类，
 * 属同族缺陷——新增浮层自持规则前先照批次 C/E 的 jsdom 产物核对法验一遍。
 */
{
  const OVERLAY_CLASSES = ["motion-toast", "motion-dialog", "motion-drawer", "motion-notice", "migration-banner", "confirm-dialog"];
  const isBareOverlaySelector = (sel) => {
    const one = sel.replace(/\s+/g, " ").trim();
    if (/[\s>+~]/.test(one)) return false;
    const m = /^\.([a-zA-Z][\w-]*)(?![\w-])/.exec(one);
    return !!m && OVERLAY_CLASSES.includes(m[1]) && !/[.:[(]/.test(one.slice(m[0].length));
  };
  const blanket = [];
  for (const cssFile of cssFiles) {
    const rel = path.relative(root, cssFile).split(path.sep).join("/");
    let ast;
    try {
      ast = postcss.parse(readFileSync(cssFile, "utf8"), { from: cssFile });
    } catch {
      continue;
    }
    ast.walkRules((rule) => {
      if (!rule.selectors.some(isBareOverlaySelector)) return;
      for (const decl of rule.nodes) {
        if (decl.type === "decl" && /^border-radius(-[a-z-]+)?$/.test(decl.prop) && decl.important) {
          blanket.push(`${rel}:${rule.source.start.line} ${rule.selector.replace(/\s+/g, " ").trim()} { ${decl.prop}: ${decl.value} !important }`);
        }
      }
    });
  }
  if (blanket.length) {
    fail(
      `浮层容器的圆角又被一条 !important 全局接管了：${blanket.length} 处。这会让组件自己挂的 ` +
        `rounded-[var(--radius-*)] 静默失效（批次 F 之前 .motion-toast / .motion-dialog / ` +
        `.motion-drawer 就是这样被一条 12px !important 统一吃掉的）。圆角请写在组件的 className 上；` +
        `确需按模式压平就用带前缀的选择器（如 .xxx-mode .migration-banner），不要用裸类名 + !important。` +
        `命中：${blanket.slice(0, 3).join(" / ")}`
    );
  }
}

/* ------------------------ 面板类名的 border-radius 不得有多条 !important 来源 */

/**
 * 批次 G 的教训，和上一条浮层 blanket 同族但形状不同（所以判据也必须不同）：
 * editorial-studio.css 里曾有两条都带 !important 的面板规则（以下为批次 G 之前的历史状态）：
 *   · blanket：`.stats-card, .desktop-panel-card, .paper-panel, .paper-panel-soft, …`
 *          { border-radius: 12px !important }
 *   · 家族规则：`.stats-card, .paper-panel, .settings-card, …`
 *          { border-radius: var(--radius-panel) !important }——名单唯独漏了 .desktop-panel-card
 * 两者特异度都是 (0,1,0)，同 !important 时后写的赢。结果是：家族规则覆盖到的 8 个成员
 * 实渲 8px、blanket 对它们而言是**死代码**；而漏出家族名单的 .desktop-panel-card
 * 被 blanket 吃掉——styles.css 给它的 20px 和 InspirationPage 自己挂的 rounded-2xl
 * 全都画不出来（探针实测）。styles.css 里 .stats-card 另有一条同样从没画出来的 14px。
 *
 * 这就是「一个属性有多条 !important 来源」的危害：谁能赢完全由谁写在后面决定，
 * 漏出某条名单的元素会静默掉到另一条上，改代码的人看不见自己在改什么。
 * 批次 G 收口成：面板家族名单是这族类名圆角的唯一来源，且名单必须覆盖所有活成员。
 *
 * 判据：同一个「面板类名」在整个 src 的 CSS 里，被 !important 声明 border-radius 的
 * 规则**至多一条**；且声明该属性的裸类名规则，其 !important 版本只能有一条。
 * 命中多于一条即红——因为那意味着其中至少一条是死代码或等着吃掉别人。
 *
 * 不误伤的写法：非 !important 的自持圆角（各页面自己的面板规则）；带前缀的模式压平；
 * 以及本批特意保留的网格 blanket（`.desktop-settings-grid > *` / `.desktop-stats-grid > *`
 * 的 12px !important）——它带组合符，不是裸类名，且删掉它的 radius 会让 box-shadow
 * 由圆变方，属阴影轴的另一批活。遗留：那两条 blanket 与面板家族名单不覆盖的容器。
 */
{
  const PANEL_CLASSES = ["stats-card", "desktop-panel-card", "paper-panel", "paper-panel-soft", "settings-card", "stats-panel", "desktop-library-panel", "desktop-editor-card", "desktop-source-card", "desktop-ai-card"];
  const isBarePanelSelector = (sel, cls) => {
    const one = sel.replace(/\s+/g, " ").trim();
    if (/[\s>+~]/.test(one)) return false; // 带祖先/组合符 → 模式压平，不算接管
    const m = /^\.([a-zA-Z][\w-]*)(?![\w-])/.exec(one);
    return !!m && m[1] === cls && !/[.:[(]/.test(one.slice(m[0].length));
  };
  const importantOwners = new Map(); // class -> [{loc}]
  for (const cssFile of cssFiles) {
    const rel = path.relative(root, cssFile).split(path.sep).join("/");
    let ast;
    try {
      ast = postcss.parse(readFileSync(cssFile, "utf8"), { from: cssFile });
    } catch {
      continue;
    }
    ast.walkRules((rule) => {
      const owned = PANEL_CLASSES.filter((c) => rule.selectors.some((s) => isBarePanelSelector(s, c)));
      if (!owned.length) return;
      // 一条规则只记一次（同一条里 shorthand + 单角都带 !important 是同一来源，不是两条）
      const vals = rule.nodes
        .filter((decl) => decl.type === "decl" && /^border-radius(-[a-z-]+)?$/.test(decl.prop) && decl.important)
        .map((decl) => `${decl.prop}: ${decl.value} !important`);
      if (!vals.length) return;
      for (const c of owned) {
        if (!importantOwners.has(c)) importantOwners.set(c, []);
        importantOwners.get(c).push(`${rel}:${rule.source.start.line} { ${vals.join("; ")} }`);
      }
    });
  }
  const dupes = [...importantOwners].filter(([, list]) => list.length > 1);
  if (dupes.length) {
    fail(
      `面板类名的圆角又出现多条 !important 来源（同特异度靠加载顺序决胜负，漏出名单的元素会被另一条静默吃掉，批次 G 的 .desktop-panel-card 就是这样）：` +
        dupes.map(([c, list]) => `.${c} ← ${list.join(" / ")}`).join("；") +
        `。请合并成一条面板家族规则并让名单覆盖全部活成员。`
    );
  }
}

/* ---------------- 网格 blanket：`X > *` 不得用 !important 给透明包装器刷非零圆角 */

/**
 * 批次 H 的教训，是上一条「面板」不变量的另一种形状（所以判据也不同）：
 * editorial-studio.css 曾有 `.desktop-settings-grid > *, .desktop-stats-grid > *
 * { border-radius: 12px !important; box-shadow: … !important }`。
 * 它和浮层/面板 blanket 的区别在于选择器是 `> *`——命中的是「某个容器的全部直接子元素」，
 * 而这些子元素往往是 <div class="grid gap-5"> 这类**没有自身底色的透明布局包装器**。
 * 给一块透明岛同时刷圆角 + 阴影，阴影会绕过圆角画成方盒子（12px 圆角在透明区上根本看不见，
 * 只有那圈投影落在卡片之间的缝隙上）。批次 H 把这条 radius+shadow 成对退役。
 *
 * 判据：凡末段是 `… > *`（子组合到通配）的规则，若用 !important 声明了一个**非零**
 * border-radius，变红。零值例外——`X > * { border-radius: 0 !important }` 是「压平」
 * （如 `.stats-page .stats-grid > *`），把子元素刻意收成方角，不是往透明岛上刷弧度，
 * 与 F/G 里「带模式前缀压平为 0」是同一族合法写法，不该误伤。
 *
 * 遗留：本判据只管 `> *`；`.desktop-settings-grid > *`（非 !important、var 值那条）
 * 是设置页把卡片网格压成分区面板的自持规则，其 .desktop-settings-grid 已确认全仓无 TSX
 * 消费者——整族死代码的清理留待设置页那一批，不在本判据射程内。
 */
{
  const gridBlanket = [];
  for (const cssFile of cssFiles) {
    const rel = path.relative(root, cssFile).split(path.sep).join("/");
    let ast;
    try {
      ast = postcss.parse(readFileSync(cssFile, "utf8"), { from: cssFile });
    } catch {
      continue;
    }
    ast.walkRules((rule) => {
      const hit = rule.selectors.find((s) => />\s*\*/.test(s.replace(/\s+/g, " ")));
      if (!hit) return;
      for (const decl of rule.nodes) {
        if (decl.type !== "decl" || !/^border-radius(-[a-z-]+)?$/.test(decl.prop) || !decl.important) continue;
        // 0 是压平，合法；非零才是往透明包装器上刷弧度
        if (/^0$/.test(decl.value.trim())) continue;
        gridBlanket.push(`${rel}:${rule.source.start.line} «${hit.replace(/\s+/g, " ").trim()}» { ${decl.prop}: ${decl.value} !important }`);
      }
    });
  }
  if (gridBlanket.length) {
    fail(
      `又出现给网格子元素刷非零圆角的 !important blanket：${gridBlanket.length} 处。` +
        `这类 ` + "`X > *`" + ` 命中的常是没有自身底色的透明布局包装器，圆角 + 阴影刷在透明岛上会画成方盒子` +
        `（批次 H 删掉的 .desktop-stats-grid > * 就是这样）。圆角请交给真正的卡片自己声明；` +
        `确需压平就用 border-radius: 0 !important。命中：${gridBlanket.slice(0, 3).join(" / ")}`
    );
  }
}

/* ------------ 工具类锚点：CSS 不得用 !important 覆盖 TSX 自己挂的 rounded-* 工具类 */

/**
 * 批次 Q 删掉的毯子长这样（editorial-studio.css 的历史状态）：
 *   `.inbox-detail > .rounded-xl, .inbox-detail > .rounded-2xl,
 *      .inbox-detail blockquote, .inbox-detail article
 *    { border-radius: 10px !important; box-shadow: none !important }`
 * 它和前面三条不变量（浮层裸类名 / 面板类名多来源 / `X > *` 网格）都不同形：
 * 选择器里出现了 `.rounded-xl` 这种**Tailwind 工具类锚点**——也就是 CSS 跑去选中
 * 「组件在 className 里自己挂的圆角类」，再用 !important 压掉它。危害有两层：
 *   ① 刻度决定权本来在 TSX 那侧（圆角棘轮因此对这类锚点做了排除，见上面三条排除的
 *      第三条）。毯子一盖，TSX 写 rounded-md 还是 rounded-2xl 都画不出来，改类名的人
 *      看不见自己改的是空气——批次 P 之后剩下的 4 处就是这么被冻在原地的。
 *   ② 它成对带 box-shadow: none，删掉 radius 而不核对 shadow，就会顺手把投影也放回
 *      去（批次 H 的反向坑）。所以本批的前后证据是两张胜者表，不是一张。
 *
 * 判据：一条规则的任一选择器分支含 `.rounded-…` 工具类锚点，且该规则用 !important
 * 声明非零 border-radius → 变红。合法写法不误伤：
 *   · `X > * { border-radius: 0 !important }` / 锚点上的 0：那是刻意压平，与 F/G/H
 *     里「带模式前缀压平为 0」同族，不是接管组件选的档位。
 *   · 非 !important 的工具类锚点规则：决定权仍在层叠里正常比武，属既有合法写法。
 * 残余（本判据不覆盖）：不含 `.rounded-` 锚点的纯元素选择器接管（`.foo article`、
 * `.foo blockquote`）——批次 Q 前它确实和锚点写在同一条规则里，所以判据顺带把它算进
 * 同一条规则的命中；若日后有人单开一条不带锚点的，仍需靠人工 + 胜者表核对。
 */
{
  const UTILITY_ANCHOR = /\.rounded-[a-zA-Z0-9[\]-]+/;
  const takeover = [];
  for (const cssFile of cssFiles) {
    const rel = path.relative(root, cssFile).split(path.sep).join("/");
    let ast;
    try {
      ast = postcss.parse(readFileSync(cssFile, "utf8"), { from: cssFile });
    } catch {
      continue;
    }
    ast.walkRules((rule) => {
      if (!rule.selectors.some((s) => UTILITY_ANCHOR.test(s.replace(/\s+/g, " ")))) return;
      for (const decl of rule.nodes) {
        if (decl.type !== "decl" || !/^border-radius(-[a-z-]+)?$/.test(decl.prop) || !decl.important) continue;
        if (/^0$/.test(decl.value.trim())) continue; // 压平合法
        takeover.push(`${rel}:${rule.source.start.line} «${rule.selectors[0].replace(/\s+/g, " ").trim()}» { ${decl.prop}: ${decl.value} !important }`);
      }
    });
  }
  if (takeover.length) {
    fail(
      `CSS 又用 !important 接管了 TSX 自己挂的圆角工具类（批次 Q 删掉的 .inbox-detail 毯子就是这个形状）：` +
        `${takeover.length} 处 —— ${takeover.slice(0, 3).join(" / ")}。` +
        `含 \`.rounded-*\` 锚点的规则会把组件 className 里选的档位整条压掉，` +
        `改类名的人看不见自己在改空气；圆角请交给元素自己声明。确需压平用 0，且删毯子时` +
        `必须连 box-shadow 一起核对前后两张胜者表。`
    );
  }
}

/* ---------- 共享组件面板钩子：裸类名规则不得给「传给 Dialog/InlineNotice 的类名」声明圆角/投影 */

/**
 * 批次 S 删掉两条同族接管（都夹带「改了画不出来」）：
 *   · history-local.css `.history-modal { border-radius: 14px; box-shadow: 0 40px 120px… }`
 *     —— .history-modal 是三个历史弹窗传给共享 <Dialog> 的面板钩子，组件根节点自己挂着
 *     rounded-[var(--radius-3)] + shadow-2xl。两侧特异度同为 (0,1,0)，而 history-local.css
 *     在 HistoryPage 的懒加载 chunk 里、发射晚于 index CSS——同特异度后来者赢，实渲的一直
 *     是 14px 和那条手抄重投影，组件挂的刻度工具类整条被压掉。
 *   · editorial-studio.css `.writing-recovery-notice { border-radius: 8px; box-shadow: none }`
 *     —— .writing-recovery-notice 是 <InlineNotice>（根挂 rounded-md）的面板钩子，同形状。
 *
 * 这与浮层/面板/网格/工具类锚点四条都不同形：那四条的锚点在选择器里（祖先、`> *`、
 * `.rounded-*`），这条的选择器就是一个裸类名——它之所以是接管，靠的是「这个类名是共享
 * 组件的面板钩子、组件根已自带刻度工具类」这个跨文件事实，判据必须自己把事实拼出来：
 *   ① 扫 TSX 里 `<Dialog` / `<InlineNotice` 开标签的**顶层** className/overlayClassName
 *      字面量，得到面板钩子类名集合（深度扫描保证不会把 footer 里子元素的 className 误当钩子）；
 *   ② src 任何 CSS 里，**整条选择器恰好等于 `.钩子名`** 的规则声明 border-radius /
 *      box-shadow → 红。
 * ②按属性一刀切（连 `box-shadow: none` 也拦），因为「压平」在裸钩子上的语义恰恰就是遮蔽：
 * 组件此刻没挂该属性不代表以后不挂（motion-notice 正是批次 P 才挂上 rounded-md 的）。
 * 确需覆盖请带模式前缀（`.some-mode .history-modal { … }`）或改组件本体。
 *
 * 合法不误伤：样式表自持的裸类名圆角（.writing-annotation——TSX 侧没有任何组件把这类
 * 元素当面板钩子）不命中集合；非几何属性的裸钩子规则（padding/display）不受影响。
 * 自检：钩子集合必须仍含那两个刚收口的类名——扫不出来就是判据退化（本文件改过 JSX
 * 扫描形状的代价，批次 C 用 jsdom 才发现的那条就是这个盲区的存量版本）。
 */
{
  const PANEL_HOOK_COMPONENTS = ["Dialog", "InlineNotice"];
  const TW_UTILITY = /^(bg|text|border|rounded|shadow|p|px|py|pt|pb|m|mx|my|mt|mb|w|h|flex|grid|gap|items|justify|font|leading|tracking|opacity|translate|scale|rotate|z|inset|top|bottom|left|right|size|min|max|overflow|whitespace|truncate|cursor|select|space|divide|ring|outline|from|to|via|animate|transition|duration|delay|ease|order|col|row|self|place|hidden|inline|block|absolute|relative|fixed|sticky|hover|focus|active|group|peer)(-|$)/;
  const hooks = new Map(); // class -> Set(host files)
  for (const p of allSource.filter((x) => x.endsWith(".tsx") && !x.includes(`${path.sep}__tests__${path.sep}`))) {
    const s = readFileSync(p, "utf8");
    for (const comp of PANEL_HOOK_COMPONENTS) {
      let i = -1;
      while ((i = s.indexOf(`<${comp}`, i + 1)) >= 0) {
        // 开标签区间：按引号/括号深度找到顶层的 `>`
        let j = i + comp.length + 1, depth = 0, str = null;
        for (; j < s.length; j++) {
          const c = s[j];
          if (str) { if (c === str && s[j - 1] !== "\\") str = null; continue; }
          if (c === '"' || c === "'" || c === "`") { str = c; continue; }
          if (c === "{" || c === "(" || c === "[") depth += 1;
          else if (c === "}" || c === ")" || c === "]") depth -= 1;
          else if (c === ">" && depth === 0 && s[j - 1] !== "/") break;
          else if (c === "/" && s[j + 1] === ">" && depth === 0) { j += 1; break; }
        }
        const region = s.slice(i, j);
        // 顶层下标表：花括号深度为 0 的位置（footer={<Button className=…>} 里的不算）
        let d2 = 0, s2 = null;
        const top = new Set();
        for (let k = 0; k < region.length; k++) {
          const c = region[k];
          if (s2) { if (c === s2 && region[k - 1] !== "\\") s2 = null; continue; }
          if (c === '"' || c === "'" || c === "`") { s2 = c; continue; }
          if (c === "{") d2 += 1;
          else if (c === "}") d2 -= 1;
          if (d2 === 0) top.add(k);
        }
        for (const m of region.matchAll(/(?:^|\s)(?:className|overlayClassName)\s*=\s*(?:"([^"]*)"|\{\s*["`]([^"`]*)["`]\s*\})/g)) {
          if (!top.has(m.index + (/\s/.test(m[0][0]) ? 1 : 0))) continue;
          for (const c of (m[1] || m[2] || "").trim().split(/\s+/)) {
            if (!/^[a-z][a-z0-9-]*$/.test(c) || TW_UTILITY.test(c) || !c.includes("-")) continue;
            if (!hooks.has(c)) hooks.set(c, new Set());
            hooks.get(c).add(path.relative(root, p).split(path.sep).join("/"));
          }
        }
      }
    }
  }
  const HOOKS_MUST_BE_SEEN = ["history-modal", "writing-recovery-notice"];
  const blindHooks = HOOKS_MUST_BE_SEEN.filter((c) => !hooks.has(c));
  if (blindHooks.length) {
    fail(
      `共享组件面板钩子扫描退化：${blindHooks.join("、")} 不再被识别为 <Dialog>/<InlineNotice> 的 className 钩子。` +
        `要么 JSX 扫描被改坏（判据失明 = 下一批人把接管当合法写法），要么钩子真的下线了（那请同步本清单）。`
    );
  }
  const takeover = [];
  for (const cssFile of cssFiles) {
    const rel = path.relative(root, cssFile).split(path.sep).join("/");
    let ast;
    try {
      ast = postcss.parse(readFileSync(cssFile, "utf8"), { from: cssFile });
    } catch {
      continue;
    }
    ast.walkRules((rule) => {
      for (const sel of rule.selectors) {
        const one = sel.replace(/\s+/g, " ").trim();
        const m = /^\.([A-Za-z][\w-]*)$/.exec(one);
        if (!m || !hooks.has(m[1])) continue;
        for (const decl of rule.nodes) {
          if (decl.type !== "decl" || !/^(border-radius|box-shadow)/.test(decl.prop)) continue;
          takeover.push(`${rel}:${rule.source.start.line} .${m[1]} { ${decl.prop}: ${String(decl.value).replace(/\s+/g, " ").slice(0, 36)}${decl.important ? " !important" : ""} } ← 钩子: ${[...hooks.get(m[1])].join(",")}`);
        }
      }
    });
  }
  if (takeover.length) {
    fail(
      `共享组件面板又被裸类名规则接管了圆角/投影（批次 S 的 .history-modal / .writing-recovery-notice 就是这个形状）：` +
        `${takeover.length} 处 —— ${takeover.slice(0, 3).join(" / ")}。` +
        `这些类名经顶层 className 传给 <Dialog>/<InlineNotice>，组件根已挂刻度工具类；` +
        `同特异度 (0,1,0) 下胜负只看发射顺序（局部 CSS 常在懒加载 chunk 里更晚），` +
        `组件的 rounded-[var(--radius-*)] 会被静默压成「改了画不出来」。请删掉这条声明，` +
        `圆角/投影交给组件本体；确需按模式覆盖请带祖先前缀。`
    );
  }
}

/* --- 同一条几何分支不得有两个不同取值的真源（批次 V） --- */

/**
 * 批次 V 查全局搜索托盘时撞上的形状，和前四条接管判据都不一样：
 *   search.css 给 .uni-search-filters button 写 9px、给 .uni-search-group li button 写 11px，
 *   editorial-studio.css 用**逐字相同的选择器分支**写 8px。两条分支同特异度，胜负只看
 *   谁在这份产物 CSS 里发射得晚——而这两个文件都进 index*.css（main.tsx 全局引
 *   editorial-studio.css），主题那份在后，于是特性页那 5 条声明一次都没画出来过。
 *
 * 危害不是「多一行死代码」，而是三件更贵的事同时发生：
 *   ① 圆角/阴影棘轮把死值当活债计数：search.css 预算 4，看着像「还差 4 处」，
 *      实际那 4 条里有 3 条渲染不出来，只有 .uni-search 命中的 3px 是真的。
 *   ② 下一批人照刻度「迁移」这些值——改完胜者表一条都不动，还以为自己还了债；
 *      更糟的是他可能顺手把主题那份也改了，于是**改死代码改出界面改版**。
 *   ③ 真正的档位决定权在主题文件里，特性页的声明是纯粹的误导文档。
 *
 * 判据形状：一条几何分支（prop + @媒体上下文 + 选择器文本）在「全局主题样式表」与
 * 非主题样式表里各有一个**不同取值**的声明 → 红。取值相同不报（那是冗余，不是分叉，
 * 而本仓还有 3 处同值重复是同一个类名被两个不同页面共用，删哪边都是改版，另账处理）。
 *
 * 两个方向，一条共同语义：只有「同特异度、靠发射顺序决胜」才算分叉。**一侧带 !important、
 * 另一侧不带**的配对在批次 AQ 起明确豁免——胜者由 !important 单独定，不存在「谁发射得晚」
 * 这件事，也就没有本批判据要防的那种假真源（批次 G 当年就是按这个理由放行
 * 「面板家族 !important + 页面自持非 !important 圆角」这个形状的；两侧都带 !important
 * 仍然比顺序，照旧算分叉）。
 *
 * 批次 AQ 起这条判据管**两个方向**。原先写明「只管主题 × 非主题」，理由是两份主题表
 * 之间「现存 16 处分叉」，怕第一天就靠预算放水。这 16 当时是真的——按本判据的同一套
 * 语义在批次 V 那份文件上复算得 16 条分支（逐对 23 对）。此后批次 W–AE 收掉 11 条，
 * 批次 AQ 收掉最后 5 条（侧栏卡圆角、看板卡圆角、搜索命令钮内投影、灵感卡与场景按钮
 * 的选中色条——五处全部经胜者表核实为「层叠上被压死」，删除零视觉）。分叉归零之后，
 * 放水的前提消失了，方向就不再豁免：两边同现异值即红，预算 0。
 */
{
  const THEME_SHEETS = new Set(["src/styles.css", "src/styles/editorial-studio.css"]);
  const themeBranch = new Map(); // key -> [{rel, line, value, imp}]
  const otherBranch = new Map();
  const sheetBranch = new Map(); // rel -> key -> [{line, value, imp}]（仅两份主题表）
  for (const rel of THEME_SHEETS) sheetBranch.set(rel, new Map());
  for (const cssFile of cssFiles) {
    const rel = path.relative(root, cssFile).split(path.sep).join("/");
    if (rel.includes("__tests__")) continue;
    let ast;
    try {
      ast = postcss.parse(readFileSync(cssFile, "utf8"), { from: cssFile });
    } catch {
      continue;
    }
    const bucket = THEME_SHEETS.has(rel) ? themeBranch : otherBranch;
    const perSheet = sheetBranch.get(rel);
    ast.walkRules((rule) => {
      const media = [];
      for (let p = rule.parent; p && p.type !== "root"; p = p.parent) if (p.type === "atrule") media.push(p.params);
      rule.walkDecls(/^(border-radius|box-shadow)/, (d) => {
        for (const sel of rule.selectors) {
          const one = sel.replace(/\s+/g, " ").trim();
          const key = `${d.prop}\t${media.join("|")} :: ${one}`;
          if (!bucket.has(key)) bucket.set(key, []);
          const decl = { rel, line: rule.source?.start?.line ?? 0, value: d.value.replace(/\s+/g, " ").trim(), imp: !!d.important };
          bucket.get(key).push(decl);
          if (perSheet) {
            if (!perSheet.has(key)) perSheet.set(key, []);
            perSheet.get(key).push(decl);
          }
        }
      });
    });
  }

  // 判据自检：这条判据唯一致命的错法是「看不见分叉」。批次 V 刚把 5 处分叉清零，
  // 而清零后的产物里同值分支仍然存在（.uni-search-shell 的 var(--radius-3) 两边各一份），
  // 用它证明「分支归一化 + 两侧都扫到」这两件事还在工作：扫不到就说明选择器/媒体
  // 上下文的处理被改坏了，那时分叉会被安静放行。
  const MUST_BE_SEEN = ["border-radius\t :: .uni-search-shell"];
  const seen = MUST_BE_SEEN.filter((k) => themeBranch.has(k) && otherBranch.has(k));
  if (seen.length !== MUST_BE_SEEN.length) {
    fail(
      `第 8 步几何分支判据退化：${MUST_BE_SEEN.filter((k) => !seen.includes(k)).join("、")} 本该在全局主题与特性样式表里各有一份，` +
        `现在扫不到两侧同现。要么分支归一化被改坏（判据失明 = 分叉会被安静放行），` +
        `要么这一族真的只剩一个主人了（那请同步本清单并确认胜者表没变）。`
    );
  }

  const forks = [];
  for (const [key, mine] of otherBranch) {
    const theirs = themeBranch.get(key);
    if (!theirs) continue;
    for (const a of mine) {
      for (const b of theirs) {
        if (a.value === b.value) continue;
        if (a.imp !== b.imp) continue; // 一侧 !important → 胜者已定，见上方语义说明
        forks.push(`${key.replace("\t", " ")}：${a.rel}:${a.line} 写 ${a.value}${a.imp ? " !important" : ""}，但 ${b.rel}:${b.line} 写 ${b.value}${b.imp ? " !important" : ""}`);
      }
    }
  }
  if (forks.length) {
    fail(
      `第 8 步几何分支：${forks.length} 条圆角/投影分支有两个不同取值的真源——${forks.slice(0, 3).join("；")}。` +
        `选择器逐字相同、特异度相同，谁生效只看谁在产物里发射得晚（批次 V 的 search.css 9px/11px 就是这么被 editorial-studio.css 的 8px 压死的：` +
        `棘轮把它记成活债，改它界面却不动）。请只留一个主人：特性页要覆盖就带祖先前缀或用不同的类名，` +
        `否则删掉不生效的那条，别让它继续冒充待收敛的债。`
    );
  }

  // 第二个方向（批次 AQ）：两份主题表之间同分支异值。判据语义与上面完全一致，
  // 只是两侧都来自 THEME_SHEETS。清零前这个方向靠「历史账」豁免了 16 条，现在预算 0。
  const themeForks = [];
  {
    const [stylesSheet, editorialSheet] = ["src/styles.css", "src/styles/editorial-studio.css"].map((rel) => sheetBranch.get(rel));
    for (const [key, mine] of stylesSheet) {
      const theirs = editorialSheet?.get(key);
      if (!theirs) continue;
      for (const a of mine) {
        for (const b of theirs) {
          if (a.value === b.value) continue;
          if (a.imp !== b.imp) continue; // 一侧 !important → 胜者已定，见上方语义说明
          themeForks.push(`${key.replace("\t", " ")}：${a.rel}:${a.line} 写 ${a.value}${a.imp ? " !important" : ""}，但 ${b.rel}:${b.line} 写 ${b.value}${b.imp ? " !important" : ""}`);
        }
      }
    }
  }
  // 自检（同上一条的道理，但盯的是新方向）：这三条分支在两份主题表里**同值**共存，
  // 是「两侧都扫到 + 同值不报」的活样本。扫不到两侧同现 = 新方向的桶挂了（分叉会
  // 被安静放行）；一旦有人把某条改成异值，它会立刻以 themeForks 的形式变红。
  const THEME_MUST_BE_SEEN = [
    "border-radius\t :: .cards-view-switch",
    "border-radius\t :: .desktop-home-inbox-count",
    "border-radius\t :: ::-webkit-scrollbar-thumb"
  ];
  {
    const [a, b] = ["src/styles.css", "src/styles/editorial-studio.css"].map((rel) => sheetBranch.get(rel));
    const unseen = THEME_MUST_BE_SEEN.filter((k) => !(a?.has(k) && b?.has(k)));
    if (unseen.length) {
      fail(
        `第 8 步几何分支（主题 × 主题）判据退化：${unseen.join("、")} 本该在两份主题表里各有一份同值声明，` +
          `现在扫不到两侧同现。要么分支归一化/分桶被改坏（这个方向会失明，分叉被安静放行），` +
          `要么这些分支真的只剩一个主人了（那请同步本清单并确认胜者表没变）。`
      );
    }
  }
  if (themeForks.length) {
    fail(
      `第 8 步几何分支（主题 × 主题）：${themeForks.length} 条圆角/投影分支在 styles.css 与 editorial-studio.css 里各有一个取值——${themeForks.slice(0, 3).join("；")}。` +
        `两份主题表同特异度，胜负只看谁发射得晚（editorial-studio.css 在 main.tsx 里排在 styles.css 之后），` +
        `于是前者那一条从未画出一个像素，却被圆角/阴影棘轮记成活债（批次 AQ 清掉的 5 处全是这个形状）。` +
        `请只留一个主人：删掉不生效的那条并留注释写明胜者是谁。`
    );
  }
  // 红了就别再打「0 处」——这条绿话和上面的红话同时出现，等于守卫自己撒谎。
  if (!forks.length && !themeForks.length) {
    console.log(`${TAG} [第 8 步几何分支] 主题样式表 × 特性样式表 同分支异值分叉 0 处；主题表 × 主题表 同分支异值分叉 0 处`);
  }
}

/* ------------------------------------------------------------------ 结论 */

if (failures > 0) {
  console.error(`${TAG} 失败 ${failures} 项`);
  process.exit(1);
}
console.log(`${TAG} 全部通过（令牌定义、D-1/D-2 回归、Button 能力面、${hardPairs.length} 组对比度硬断言${pending > 0 ? `，另有 ${pending} 组待收敛` : ""}）`);
