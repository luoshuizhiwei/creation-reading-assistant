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
    "src/components/interaction.tsx": 1,
    "src/components/ui/FontPicker.tsx": 1,
    "src/components/ui/Tabs.tsx": 1,
    "src/components/ErrorBoundary.tsx": 2,
    "src/components/ui.tsx": 1,
    "src/features/creation/ai/scene-candidate.css": 2,
    "src/features/creation/cards/cards-local.css": 7,
    "src/features/creation/cards/relation-graph.css": 3,
    "src/features/creation/editor/scene-radar.css": 2,
    "src/features/creation/editor/writing-quick-reference.css": 5,
    "src/features/creation/editor/writing-reference.css": 4,
    "src/features/creation/history/history-local.css": 6,
    "src/features/creation/inbox/ai-send-confirm.tsx": 2,
    "src/features/creation/inbox/components/InboxConvertToCardDialog.tsx": 1,
    "src/features/creation/inbox/components/InboxItemDetail.tsx": 6,
    "src/features/creation/inbox/inbox-local.css": 1,
    "src/features/creation/operation/operation.css": 4,
    "src/features/creation/outline/outline-reorg.css": 1,
    "src/features/creation/replace/replace.css": 5,
    "src/features/search/search.css": 4,
    "src/features/settings/encryption/encryption.css": 1,
    "src/styles.css": 36,
    "src/styles/editorial-studio.css": 29
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
   * 冻结预算。数字由本判据扫出（CSS 声明 + @apply 92 + TSX 32，合计 124，30 个文件），和圆角棘轮一样：
   * 新债要红，还了债不降预算也要红。
   */
  const SHADOW_BUDGET = {
    "src/components/ErrorBoundary.tsx": 1,
    "src/components/interaction.tsx": 2,
    "src/components/ui/Dialog.tsx": 1,
    "src/components/ui/FontPicker.tsx": 1,
    "src/components/ui/Tabs.tsx": 1,
    "src/features/creation/cards/cards-local.css": 1,
    "src/features/creation/editor/scene-radar.css": 1,
    "src/features/creation/editor/writing-quick-reference.css": 1,
    "src/features/creation/editor/writing-reference.css": 2,
    "src/features/creation/history/history-local.css": 2,
    "src/features/creation/inbox/ai-send-confirm.tsx": 1,
    "src/features/creation/outline/OutlineTree.tsx": 1,
    "src/features/creation/replace/replace.css": 2,
    "src/features/inspiration/InspirationPage.tsx": 1,
    "src/features/library/LibraryPage.tsx": 1,
    "src/features/search/search.css": 1,
    "src/features/settings/SettingsSearch.tsx": 1,
    "src/features/settings/encryption/encryption.css": 2,
    "src/features/settings/sections/SectionWrapper.tsx": 1,
    "src/features/settings/settings-controls.css": 7,
    "src/styles.css": 38,
    "src/styles/editorial-studio.css": 35
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
        if (v === "none" || /^var\(--shadow/.test(v)) return;
        // 只有「至少一个非 inset 层」才是投影；纯 inset 是色条/内衬装饰，§5.3 四级不针对它
        if (!shadowLayers(v).some((L) => !/^inset\b/.test(L))) return;
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
          if (!/^var\(--(shadow|focus)/.test(mm[1].trim())) n += 1;
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
        `纯 inset 色条/内衬和 shadow-none/shadow-inner 不在此管。`
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

/* ------------------------------------------------ 幻影选择器的令牌债冻结预算（第 8 步） */

/**
 * 批次 N 和 O 是同一族缺陷的两种死法，前面各自只收了其中一种：
 *   · 批次 N 收的是「层叠上被压死」——宿主存在，但每条分支都输给更晚/更具体的规则。
 *   · 批次 O 收的是「宿主根本不存在」——选择器里的业务类名在渲染侧查无落点，整条规则
 *     一次都没画过。这类声明上面两条棘轮照样数它（它们不看宿主），所以光看「离刻度还剩
 *     多少处」分不清哪些是真能还的：幻影债务不迁移就永远还不完，只能删。
 * 判据要守的正是第二种，否则下一批人会继续给画不出来的元素设计令牌档位。
 *
 * 「宿主不存在」只能按谁能把类名挂上 DOM 来划定语料，两个方向都错过：
 *   · 只看源码字符串会把 `writing-quick-kind--${card.kind}` 这种 BEM 修饰类判死——
 *     完整串不在源码里（编译成 .concat 后才拼出来），所以必须额外收集「紧邻 ${ 的
 *     类名片段」当动态前缀。
 *   · 反过来把 electron/** 也当宿主语料，会把主进程拼的设备 ID / 记录 ID
 *     （`desktop-${hash}`、`card-${uuid}`）当成类名落点，于是整族 desktop-* 全部误判成活。
 *     主进程碰不到渲染 DOM，渲染侧语料只取 src/** + 根 index.html。
 * 产物 JS 是最硬的一条证据（JSX 的 className 原样进 bundle），但守卫可能在未构建时运行，
 * 所以产物只作补充证据、不作必要条件：out/ 存在就一起查，不存在就只按源码判。
 *
 * 判据形状沿用棘轮那套「先变成数据、再一页一页还」：
 *   每条无宿主规则里的离刻度圆角 + 非 var 的投影声明，按文件冻结。
 *   给幻影选择器新增令牌债要红；顺手删掉一块幻影 CSS 不降预算也要红。
 * 这里的数字不是「还要还多少」，而是「允许界面外的 CSS 再胖多少」——真还法只有删规则。
 */
{
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

  // Tailwind 工具类不进这里判：它们由 utilities 层发射，宿主判定不适用。
  const TW_PREFIX = /^(bg|text|border|rounded|shadow|p|px|py|pt|pb|m|mx|my|mt|mb|w|h|flex|grid|gap|items|justify|font|leading|tracking|opacity|translate|scale|rotate|z|inset|top|bottom|left|right|size|min|max|overflow|whitespace|truncate|cursor|select|space|divide|ring|outline|from|to|via|animate|transition|duration|delay|ease|order|col|row|self|place|hidden|inline|block|absolute|relative|fixed|sticky|hover|focus|active|group|peer)(-|$)/;

  /** 只取选择器的「正向」类名：:not(.x) 里的 x 是被排除的，它的宿主在别处。 */
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
    return [...new Set((s.match(/\.[A-Za-z_][A-Za-z0-9_-]*/g) || []).map((x) => x.slice(1)).filter((c) => !TW_PREFIX.test(c)))];
  };

  /**
   * 冻结预算。数字由本判据扫出。
   * ⚠ 这张表不是第三笔债，而是给上面两张棘轮的同一批数字加一个「宿主在哪」的透镜：
   *   幻影声明照样计入 RADIUS_BUDGET / SHADOW_BUDGET（那两条判据不看宿主，这是刻意的——
   *   一旦让棘轮也看宿主，判错方向的代价就从「多删一行死代码」变成「少盯住一处活债」）。
   *   所以删掉一条幻影规则会同时降两处的数字，两边都要跟着改；
   *   反过来，本表里的数字只能靠删规则还，没有迁移这条路。
   */
  const HOSTLESS_BUDGET = {
    "src/features/creation/cards/cards-local.css": 1,
    "src/features/settings/encryption/encryption.css": 2,
    "src/styles.css": 11,
    "src/styles/editorial-studio.css": 1
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
      if (per.flat().some(canRender)) return;
      hostlessRules += 1;
      for (const d of r.nodes) {
        if (d.type !== "decl") continue;
        let debt = false;
        if (/^border-radius/.test(d.prop)) {
          const toks = d.value.replace("!important", "").trim().split(/\s+/);
          debt = toks.some((t) => /^-?\d/.test(t) && !t.includes("%") && !SCALE_PX.has(parseFloat(t)));
        } else if (/^box-shadow$/.test(d.prop)) {
          debt = d.value !== "none" && !/var\(--(shadow|focus)/.test(d.value) && d.value.split(/,(?![^(]*\))/).some((L) => !/inset/.test(L));
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

/* ------------------------------------------------------------------ 结论 */

if (failures > 0) {
  console.error(`${TAG} 失败 ${failures} 项`);
  process.exit(1);
}
console.log(`${TAG} 全部通过（令牌定义、D-1/D-2 回归、Button 能力面、${hardPairs.length} 组对比度硬断言${pending > 0 ? `，另有 ${pending} 组待收敛` : ""}）`);
