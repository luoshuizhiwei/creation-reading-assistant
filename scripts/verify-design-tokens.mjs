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
  "间距 §5.1": ["--sp-0", "--sp-1", "--sp-2", "--sp-3", "--sp-4", "--sp-5", "--sp-6", "--sp-7", "--sp-8", "--sp-9", "--sp-10", "--sp-11", "--sp-12"],
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

// 分层所有权：同一属性只能有一层拥有。⚠ 批次 BD-21 纠正本文件与 ui.tsx 的旧说法
// 「同类工具按值从小到大发射」——真实规律是**按类名字符串字典序**发射，与值大小无关，
// 重扫产物实测的三族序列（后者压前者）：
//   内边距 px-0 → px-0.5 → px-1.5 → px-10 → px-2 → px-2.5 → px-3 → px-4 → px-5 → px-6 → px-8
//   高度   h-1 → h-10 → h-12 → h-28 → h-3 → h-4 → h-5 → h-6 → h-7 → h-8 → h-9
//   字号   text-2xl → text-[任意值] → text-base → text-sm → text-xl → text-xs
//   圆角   rounded → rounded-2xl → rounded-[任意值] → rounded-full → rounded-lg → rounded-md
//          → rounded-none → rounded-sm → rounded-xl（与批次 AV 当年实测那条序逐位相同，
//          它本来就是字典序，只是当时没认出规律）
// 旧结论之所以一直「看起来对」：px-2.5 < px-3 < px-4 恰好值序=字序；露馅的是同族里的
// 两位数档——px-10 排在 px-2 之前、h-10/h-12/h-28 排在 h-3 之前。字号族里方括号（0x5B）
// 排在数字之后、小写字母之前，所以 text-[13px] 发在 text-sm **之前**——同挂时 14 赢 13。
// 本条判据不受影响：base 的 px-* 仍发在尺寸层 px-2.5 之后，照样压掉它。
// 受影响的是「谁该拥有字号」——base 兜一个 text-sm 就等于永远赢掉尺寸层的任意值档，
// 所以 BD-21 把字号交回 sizes/icon 层，base 不再参与。内边距必须只属于 sizes / icon 层。
const baseClassSource = /const base =\s*\n?\s*"([^"]+)"/.exec(uiTsx);
if (!baseClassSource) fail("读不到 Button 的 base 类串，分层检查无法执行");
else {
  const padding = (baseClassSource[1].match(/(?:^|\s)(?:p|px|py|pl|pr)-[\w.]+/g) ?? []).map((s) => s.trim());
  if (padding.length > 0) {
    fail(`Button 的 base 不得包含内边距 ${padding.join(", ")}：Tailwind 按类名字典序发射同类工具，` +
      `base 的 px-* 会压掉尺寸层（sm/lg）和 icon 的 p-0，导致 padding 静默失效`);
  }
  // 批次 BD-21 立的不变量，用一颗牙钉住（见上方发射顺序）：base 不得兜底字号。
  // 写进 base 的那一档发在尺寸层的任意值档**之后**，谁都不知道自己最后吃到几 px，
  // 而账面（类串里确实有 text-\[\d+px\]）看着像已经按 §2.2 分档了。
  const baseFont = (baseClassSource[1].match(/(?:^|\s)text-[\w.[\]]+/g) ?? []).map((s) => s.trim());
  if (baseFont.length > 0) {
    fail(`Button 的 base 不得包含字号 ${baseFont.join(", ")}：§2.2 的三档字号归 sizes / icon 层，` +
      `且 base 的具名档按字典序发在尺寸层任意值档之后，会静默压掉它（14 赢掉 13）`);
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

// data-variant 保留调用方原值。
// ⚠ 批次 BD-26 改动本条的理由与措辞：原先这里除了断言 `data-variant={variant}`，还钉着
// 一句「editorial-studio.css 的 quiet/ghost 双排除应为 4 处」，并把 `quietExclusions < 4`
// 判红。那 4 处全部长在 `.desktop-page-actions button:first-child:not(…)` 这一族上，
// 本批按第 6 步的收口清单把 12 条整族删除，双排除随之归零——继续要求 4 处等于要求
// 「删掉的接管规则复活」。但断言本体一个字没动，因为它的理由换了一个、而且更硬：
// button-variants.test.tsx:47 钉的是 **DOM 属性值**（quiet→"secondary" 原值、
// primary→"primary"），第 6 步已收口的页面靠 `[data-variant]` 选择器做豁免
// （`button:not([data-variant])` 一族，:1512 起）与测试可读的标记，输出归一后的
// resolved 值会让这些消费方静默失配。也就是说：调用方原值这条不变量今天仍然活着，
// 只是不再需要 CSS 里必须有 4 处副本来证明它。
// 现在唯一的例外方向是「有人为了绕过 :not 排除而改 data-variant 的语义」，那由测试钉。
if (!/data-variant=\{variant\}/.test(uiTsx)) {
  fail("data-variant 必须输出调用方传入的原始值（第 6 步收口的 button:not([data-variant]) 排除与 button-variants 测试依赖它）");
}

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
 * 情况（原先 StatsPage 借全局 .history-item-actions）由全局样式表自身负责排除。
 * ⚠ 批次 BD-42 起那个跨目录借用已经取消（统计页改用自己的 .stats-session-actions）：
 * 借来的类名让统计页的钮间距随「这次会话里开没开过版本历史」在 4px/6px 之间漂，
 * 实测过（同一元素、同一构造，仅导航顺序不同）。这条约定仍然适用于以后的跨目录借用。
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
 *
 * ✔ 批次 BD-26（写作页迁移批）：清单**清零并移除条目**。上面那句「迁移到哪个页面
 *   就把对应锚点删掉」到今天为止全部兑现——唯一剩下的锚点 `editorial-studio.css ::
 *   desktop-page-actions` 那 12 条按钮级规则随四个页面（写作页 hero / 项目首页 /
 *   书库 / 阅读统计）一起交回共享 <Button>，删净并就地留墓碑。
 *   空表不是「这条判据没用了」，恰恰相反：它是这条判据最紧的形态。从今天起任何
 *   `.容器类 button { … }` 形式的接管规则（含把 .desktop-page-actions 一族写回来）
 *   都落进 newHits → 硬红，不再有「已冻结」这层缓冲。inject 里 BD-26 新牙钉的就是
 *   这个方向：把删掉的接管规则原样写回来，必须红。
 *   ⚠ 清单里的锚点靠 CSS 选择器文本与 JSX 宿主类名两边对撞得到，本批同时确认过
 *   这四个容器里 0 个裸 <button>（/tmp/bd26-hosts.cjs：JSX 栈扫描，只有 <Button>），
 *   所以删除是「交回组件」而不是「同时改变未被接管的元素」。
 */
const LEGACY_DESCENDANT_BUTTON = new Set();

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
} else {
  // 批次 BD-26：清单清零后这条判据进入「无缓冲」状态，打一行确认它还在跑，
  // 而不是安静地什么都不说——判据沉默和判据通过长得很像，这里要能分辨。
  console.log(
    `${TAG} [第 6 步已收口] 遗留按钮容器接管规则 0 条（批次 BD-26 起 LEGACY 清单为空：再写回一条就是硬红，不再有「已冻结」这档）`
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

/* ------------------------------ 自定义属性引用环必须为零（第 8 步批次 BD-28 立） */

/**
 * BD-28 立的不变量：全站 CSS 里，自定义属性之间的 var() 引用不得成环。
 *
 * 这条是本批真正修掉的东西。形状长这样（都是历史别名层的产物，两处分居两个文件）：
 *   src/styles.css:13                 --text-main:     var(--text-primary)
 *   src/styles/editorial-studio.css:50（已删）--text-primary: var(--text-main)
 * 两个名字都发在 :root 上，于是同一个元素同时持有这两条声明——按 CSS Variables 的
 * 循环定义规则，环上每个属性在计算值阶段一律「guaranteed-invalid」，等价于没写；
 * 于是 body { color: var(--paper-ink) }（--paper-ink 又正是 var(--text-primary)）
 * 在算值时失败，元素回落到**初始黑 rgb(0,0,0)**，tokens.css 的 #20242a 从未生效。
 *
 * ⚠ 影响面实测比这句话小，别按「全站文字都是黑的」理解它（我第一版就是这么说过头了）：
 *   同页开关环、逐索引对照的普查结果是 项目首页 6 个元素、写作稿纸 57、收件箱 5、
 *   资料阅读 3 的解析色改变，另每屏 1 个 dock 深色样本块（background: var(--text-primary)
 *   从透明画成设计墨）。稿纸正文本身幸存——它走 .paper-shell 的 rgb(var(--rgb-ink))
 *   那条**字面量孪生**（--rgb-ink: 32 36 42 从不经过环），所以最显眼的那块文字一直是对的。
 *   这恰好是 RGB_TWINS 那条不变量的另一面：一个字面量救活了另一条已经坏掉的路。
 *
 * 为什么前面那套对比度守卫（上面 hardPairs 一族）永远看不见它：那些断言用
 * tokenValue() 拿**正则**从 tokens.css 里读十六进制字面量，读到的确实是 #20242a，
 * 算出来 12.6:1 漂亮达标——它查的是「令牌定义得对不对」，从来没查「浏览器实际
 * 把这个名字解析成了什么」。定义正确、解析坏掉，是这一类缺陷的定义性特征。
 *
 * 为什么只有晨校中枪：夜校有 :root[data-app-theme="dark"]（特异度 (0,2,0)）用
 * **字面量**覆盖 --text-primary/--text-tertiary，把链从环上摘了出去。所以切到深色
 * 一切正常、切到浅色才是黑的——「换个主题就好」正是这类缺陷最难被看见的形状。
 *
 * 判据口径（刻意比"当前是否在同一个元素上生效"更宽）：
 *  · 不看选择器、不看覆盖顺序，把全站所有 --x: var(--y) 当作一张图，环上任何节点即红。
 *    理由：被打断的那个主题只是侥幸，没被打断的那个已经在画错颜色；
 *    用「可能有别处字面量盖住」当豁免条件，等于把这条重新降级成需要人工判断——
 *    而"需要人工判断"正是本仓所有隐形缺陷的共同出身。
 *  · 边只看 var(--name) 的第一个参数，带字面量回退的 var(--x, #fff) 同样算边——
 *    成环时回退值也救不了（环上属性在计算值阶段无效，不是"取不到值"）。
 *  · 用 postcss 解析 + 保留行号的去注释（preserveNewlines）：本批留下的两处断环
 *    墓碑注释里原样抄着 --text-primary: var(--text-main) 这类字符串，
 *    不剥注释就会让守卫自己咬自己（同文件顶部 stripComments 的注释讲的是一回事）。
 *
 * 全部新牙见 inject-test-design-tokens.mjs「批次 BD-28 环」一族：把环写回必须红，
 * 合法的非环别名不许红。
 */
{
  const relOf = (p) => path.relative(root, p).split(path.sep).join("/");
  const edges = new Map(); // 属性名 -> Set(它引用的属性名)
  const sites = new Map(); // 属性名 -> 声明处（file:line --a: var(--b)）
  for (const cssFile of cssFiles) {
    let ast;
    try {
      ast = postcss.parse(preserveNewlines(readFileSync(cssFile, "utf8")), { from: cssFile });
    } catch {
      continue;
    }
    ast.walkDecls(/^--/, (decl) => {
      const value = decl.value.trim();
      const refs = [...new Set((value.match(/var\(\s*(--[A-Za-z0-9-]+)/g) || []).map((m) => m.slice(4).trim()))];
      if (!refs.length) return;
      if (!edges.has(decl.prop)) edges.set(decl.prop, new Set());
      if (!sites.has(decl.prop)) sites.set(decl.prop, []);
      for (const r of refs) {
        edges.get(decl.prop).add(r);
        sites.get(decl.prop).push(`${relOf(cssFile)}:${decl.source?.start?.line ?? 0}  ${decl.prop}: ${value}`);
      }
    });
  }
  // 从 from 出发沿引用边走，能否回到 from —— 能就说明 from 在某个环上。
  const closedPath = (from) => {
    const stack = [[from, [from]]];
    const seen = new Set();
    while (stack.length) {
      const [node, path_] = stack.pop();
      for (const next of edges.get(node) || []) {
        if (next === from) return [...path_, from];
        if (seen.has(next)) continue;
        seen.add(next);
        stack.push([next, [...path_, next]]);
      }
    }
    return null;
  };
  const onCycle = [...edges.keys()].map((n) => [n, closedPath(n)]).filter(([, p]) => p);
  if (onCycle.length) {
    const detail = onCycle
      .map(([name, p]) => `${p.join(" → ")}【${(sites.get(name) || []).slice(0, 2).join(" ｜ ")}】`)
      .join("；");
    fail(
      `自定义属性引用环 ${onCycle.length} 个节点：${detail.slice(0, 400)}。` +
        `环上的令牌在计算值阶段一律 guaranteed-invalid（等价于没定义），消费它的 color/background ` +
        `会静默回落到继承值——界面照常渲染、vitest 全绿、对比度断言（只读 tokens.css 字面量）也全绿。` +
        `请只留一个主人：删掉别名其中一侧并留注释写明权威令牌在哪。`
    );
  } else {
    console.log(`${TAG} [环不变量] 全站 ${edges.size} 个带 var() 引用的自定义属性，引用环 0 个——晨校墨色链 --text-main → --text-primary → #20242a 实算可达（BD-28 立）`);
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
    // ✔ 后续（批次 BD-19，本批就是那个「另批」，守卫把裁定权交给了这一批）：
    //   红边由 border 自己承担——校样红是「出错了」的唯一语义载体，不动；
    //   投影归 --shadow-3。选档依据：这张卡在结构上是接管整个视图区域的兜底面板
    //   （max-w-md、居中、压在正文之上），§5.3 里唯一为「接管面板」准备的档就是 3；
    //   1 档带环、和保留的红边拼成 AE 说的 2px 双线，2 档（0 4px 12px）是给菜单那种
    //   小控件的，48px 见方的卡用它会飘。原值 shadow-paper = 0 18px 50px rgba(34,38,48,.1)
    //   的第一层偏移/模糊本来就落在 3 档附近，观感连续。
    //   ⚠ 写法必须是属性形式 [box-shadow:var(--shadow-3)]：见上面批次 K 的幻影教训。
    //   这笔清零后条目移除，TSX 侧台账自此为空表——钉在旧串上的四颗牙同批搬家，
    //   见 inject 里「阴影棘轮①／还债不降预算／批次 K 回归」那几条的 BD-19 注释。
    // 批次 AJ：灵感页那条 shadow-paper 工具类随面板归入 --shadow-1 一起删除，条目移除。
    // 批次 AR：encryption.css 那条三层手抄投影随 .pe-modal 族整族下线（查无宿主），条目移除。
    // ✔ 后续（批次 BD-19）：replace.css 条目随债清零移除。那条是「真投影 + inset 冠光」
    //   的混排（inset 0 1px 0 rgba(255,255,255,.5), var(--shadow-3)），棘轮因此一直记它一笔债。
    //   §5.3 四级全是外投层级，压根没有「内衬高光」这一档；这条白色冠光是拟物装饰，
    //   同族先例是第 6 步 inbox 按钮的 inset 冠光与 §2.5 的去冠光裁定——整条下线，
    //   面板本体早就写着 var(--shadow-3)（z-60 浮层、对话框量级，档位无需重判），
    //   删掉混排前缀后整串命中 var(--shadow-*) 豁免。border 保留：3 档不含环，AE 管不到。
    "src/features/settings/settings-controls.css": 1,
    // ✔ 后续（批次 BD-19 裁定，本批**保留**这一笔）：滑块拇指 `0 1px 4px rgba(15,20,28,.22)`
    //   不归四级里的任何一档——1 档自带 0 0 0 1px 环，会和这条 2px solid --action-primary
    //   拇指描边拼成 AE 说的双线（且 16px 的圆钮上一圈 separator 环毫无意义）；2 档是
    //   0 4px 12px，比拇指本身还大，用它等于让一颗旋钮浮到半空。它的作用是「旋钮离开轨道」
    //   的微接触影，性质同上面 C 族的材质厚度，待「控件触感和材质」专项一起判。
    //   （另一层现实约束：批次 AD 那颗「删掉这条真投影必须红」的牙就钉在这两行串上，
    //   付它得先把牙迁到别的活债宿主——本批 TSX 侧清零、CSS 侧还有 20 处，不缺锚点，
    //   但那是「为了还债搬家」而不是「判完再还」，方向不对，随专项批一起做。）
    // 批次 AK：styles.css 退役 4 处——侧栏卡那条被 editorial 同特异度 none 压死的手抄投影（层叠死），
    // 加 manuscript-paper / reader-paper / reader-glassbar 三条幻影规则整条删除（宿主零引用）。
    // 批次 AL：再退役 2 处，同一族「层叠压死」，胜者表实测从未画出一个像素——
    //   .paper-panel 的 @apply shadow-lift（被家族那条 0 1px 2px !important 压着）、
    //   .paper-topbar 的 @apply shadow-[0_1px_0_...]（被 .reader-root .paper-topbar 与
    //   本文件 L2755 的 box-shadow: none 双重压着）。border/background 一律保留。
    // 余下 8 处是 7 个夜读皮肤 + .paper-input:focus 的焦点柔光，都是**真在渲染**的值：
    // 皮肤那条是纸面浮影（收它=可感知改版，另批处理），focus 那条是 3px 描边柔光 +
    // ring-1 合成，属 §2.3 焦点机制而非 §5.3 投影层级。
    // ✔ 后续（批次 BD-19）：上面那句「8 处」里的 focus 那条判对了性质、记错了账——
    //   产物实测它的两层**全是 0 0 0 描边环**（0 0 0 1px 主色 20% 来自 ring-1，
    //   0 0 0 3px 棕 8% 来自 shadow-[…]），批次 AD 的纯环豁免看的是几何形状，
    //   所以写成显式 box-shadow 声明就依法豁免；原先它在 @apply 里被计成债，
    //   恰恰是 AD 批评过的「判据不对称」的另一种表现。改法逐像素等价（环在上、光在下，
    //   顺序照抄 Tailwind 自己拼 box-shadow 的顺序），零改版，8→7。
    //   剩下的 7 处是 7 个夜读皮肤，仍按上面那条裁定保留：那条 `0 18px 50px rgba(34,38,48,.1)`
    //   是整页纸浮在桌面上的影，归 1 档会同时踩三个坑（环与皮肤自带的 border 双拼、
    //   24px 偏移撑不起整页、深色下纸色写死不归 --separator 管），归 2/3/4 档都是可感知改版。
    //   「纸张浮影该有自己的档还是并入 1 档深色特例」是皮肤批的题，不在本批。
    "src/styles.css": 7,
    // 批次 AS：settings-grid 开关岛整族下线，其中 L2371 那条
    // box-shadow: 0 1px 2px rgba(34, 38, 48, 0.28) 是幻影账里最后一笔真债（岛查无宿主、
    // 值又离刻度），随整族一起删除。16→15。
    // ✔ 后续（批次 BD-19）：15→13，两笔，各走一条不同的合法还法——
    //   · .writing-focus-status `0 4px 16px rgba(20,24,32,.08)` → var(--shadow-2)：
    //     绝对定位、z-35、带 backdrop blur 的浮起药丸，§5.3 的 2 档原文正是「下拉、菜单、
    //     popover」。与它的 border 共存合法（2 档纯投影、不含环）。刻度替换的已知代价：
    //     模糊 16→12、alpha .08→.10，浮感收紧——这正是刻度的作用，不是事故。
    //   · .inbox-item--selected `0 4px 14px rgba(34,38,48,.07)` **整条删除**（层叠压死）：
    //     这个 modifier 全仓唯一挂载是 InboxItemList.tsx 的 `stats-card inbox-item …`，
    //     而本文件末尾 .stats-card/.paper-panel 那条 !important 投影永远赢非 !important 的它
    //     （jsdom 读真实产物 computed 值实测：选中行实渲就是那条 0 1px 2px .04，
    //     带与不带这条 4px 14px 同值）。投影从未画出像素，选中态的真实信号一直是
    //     ::before 那道朱砂书脊 + 纸色底。同 AK/AL 口径删声明、不迁移——
    //     把它「补成 var(--shadow-N)」等于给选中行凭空画一圈从没存在过的浮起，是改版。
    // ⚠ 本批同时把**剩下这 13 处为什么现在不能还**定成台账，免得下一轮当成「还没轮到」反复重判。
    //   它们是已判不归级，各自的收口批次不同（数字按判据扫出的实测行号登记；
    //   ⚠ 批次 BD-20 往 editorial-studio.css 稿纸那条规则里插了 16 行注释，本表引用的行号
    //   已按判据重扫逐条更新——这张表是**活引用**、不是历史，漂了就当场重编。
    //   ⚠ 批次 BD-22 又往同一条规则里净插了 20 行（行长那段实测注释）：本表 ≥1431 的 9 条整体 +20、
    //   在稿纸那条之前的 4 条（:134/:516/:740/:927）不变——逐条按**内容**重扫回填。
    //   ⚠ 这句 +20 是逐条按**内容**重扫得到的：本批先前写过一版「+13 / +11」的算术平移，
    //   那是拿注释的半成品行数估的，13 条里 8 条搬错了位置。活引用只能按内容重扫——
    //   「预测的数字不许写进注释」这条教训在本批出现了三次（行长墨迹、纸壳 738、这张表）。
    //   A) 卡族双拼 2 处（:1451 四宿主手抄 + :1463 hover）：归 --shadow-1 的前提按 AE
    //      是撤掉同元素的 border，可这些卡的边框还兼任 hover/.active 的**变色反馈**
    //      （styles.css 里 .cards-board-card:hover/.active、.background-card:hover/.active
    //      四条边色规则；editorial 的 .active 更用 border-left: 3px 画书脊）。撤边等于
    //      把悬停与选中语义一起擦掉，必须先由「卡片描边批」决定反馈换成什么。
    //   B) 环关不掉 2 处（:2914 .stats-card/.paper-panel 的 !important；:2247 深色四宿主）：
    //      AJ 已写明原因——.stats-card 复合收件箱行与翻页条（它们的 border: 0 !important
    //      只关得住 border，关不住 shadow-1 的环，每行会凭空多出一整圈盒框），
    //      .paper-panel 有靠工具类 border-0 要无边面板的阅读器消费方，而 :2247 那条
    //      (0,2,1) 的深色规则还会吃掉那层刻意豁免。随「收件箱列表版式」「阅读器外壳」批收。
    //   C) 非 §5.3 层级 3 处：.desktop-brand-mark :134 的后层 `0 3px 10px rgba(0,0,0,.28)`
    //      与 .library-cover :1895 的 `0 2px 5px rgba(34,38,48,.18)` 是**材质厚度**
    //      （布面印章、书封实体，两者都还叠着 inset 冠光）；kbd :2289 的
    //      `0 1px 0 var(--border-strong)` 是键帽台阶线——1px、零模糊，几何上是描边不是投影，
    //      但 AD 的环判据按形状（0 0 0 Npx）放过不了它。三处待「材质/装饰」专项裁定。
    //   ✔ 后续（批次 BD-25 用户裁定落地）：这三处即裁定所说的「材质」——已按逐条登记
    //     移出投影账（见下面 DECOR_SHADOWS），**一条像素都没改**，判的是归类不是取值。
    //   D) 真改版候选 2 处：.desktop-commandbar :516（吸顶栏双层柔影，1 档撑不起横贯整行的
    //      栏、2 档会让工具条看起来浮起来，层级归属需要视觉裁定）；
    //      .desktop-home-inbox-count :927（朱砂计数徽标的**彩色光晕** `0 2px 6px color-mix(--studio-seal 35%)`，
    //      四级里没有彩色列，归任何一档都等于擦掉那层强调色）。这两处不由判据猜。
    //   ✔ 后续（批次 BD-25 用户裁定落地）：这两处与 C 族同批登记移出（徽标彩光 + 吸顶栏柔影）。
    //   E) 按钮族遗留 3 处（:740 hover 铜影、:2268 全族冠光组合、:2278 first-child 组合）：
    //      归 §2.5「按钮一律无阴影」那条账，与 FROZEN_BUTTON_SHADOW_COUNT=3 登记的是同一批
    //      宿主，随该页 LEGACY 迁移整族下线。**本批一条都不能动**：动了就是拿阴影批
    //      改按钮外观，而且会让那个必须相等的冻结计数与判据同时失配。
    //   F) 稿纸 1 处：.scene-editor :2237 的三层大投影（1px 接触 + 24px 60px
    //      var(--studio-paper-shadow) 大 penumbra + 2px 6px 柔影）是整页稿纸压在桌面上的
    //      纸张厚度，与 --studio-paper-shadow 令牌、稿纸 34px 节距是同一族耦合改动，
    //      随专门的稿纸批一起算（那批本来就要同批动 FS③/LH② 两颗牙）。
    //      ✔ 后续（批次 BD-20 稿纸批落地时改判）：稿纸批**没有**动这一处，理由要写清，
    //      不能让下一批人以为那条许诺已经兑了。这条注释当时把两件不同的事捆在一起：
    //      「三件同算」指的是 字号 × 行高 = 节距 那条**算术**（17×2=34 → 16×1.85=29.6），
    //      而本条投影与那个乘积没有任何关系——节距改了它一个像素都不必变（改前后逐字节
    //      相同，阴影胜者图已复验）。它归不了级的真正原因是那条 penumbra 用的是自己的
    //      令牌 --studio-paper-shadow（rgba(15,20,28,.08)），§5.3 四档里没有一档含它，
    //      归任何一档都等于换掉整页纸壳的投影颜色，那是「桌面材质族」的一次改版，不是
    //      阴影分级。所以它继续挂账，宿主批从「稿纸批」改名为「桌面材质 / 纸张族」。
    //   ✔ 后续（批次 BD-25 用户裁定落地）：这条也登记移出——它就是「桌面材质族」里唯一
    //     与纸张厚度有关的那一条，裁定把它和 C/D 两族一起判成「不是投影层级」。
    //     ⚠ 移出**不等于**这笔账有了答案：它从此不进棘轮计数，也就再没有一处判据在盯它；
    //     将来若有人动这条声明，只有 DECOR_SHADOWS 的失配硬红会响（值/选择器一改就红）。
    // ✔ 后续（批次 BD-26 写作页迁移批）：E 族 3 处随第 6 步 12 条容器接管规则
    //   **整族删除**，本文件 7→4。这里要把它和 C/D/F 那 6 处划清：那 6 处是
    //   「判据归类判错了、重新归到材质族」，一条像素没改；E 族这 3 处是**真还债**
    //   ——按钮从此不再画 hover 铜影与 inset 冠光，外观变了，所以它必须配一次
    //   可感知改版验收（前后胜者图差集 + 截图 + 逐钮实测），不是又一次零像素批。
    //   上面 BD-19 那句「本批一条都不能动」的理由（动了会让 FROZEN_BUTTON_SHADOW_COUNT
    //   与判据同时失配）在 BD-26 是**同时兑现**的：删声明与降那个 3→0 在同一次提交里，
    //   两本账一起对平，这也是当初把两个数字都钉成必须相等的原因。
    "src/styles/editorial-studio.css": 4
  };

  // 批次 BD-25（用户第二项裁定）：材质 / 装饰族**移出投影层级这本账**。
  // 实测收口：登记 6 条（C 族 3 + D 族 2 + F 族 1，就是上面 A–F 家族注释里那三条 ✔ 后续），
  // 全部在 editorial-studio.css 里，所以台账 21 处 → 15 处：该文件 13→7，
  // settings-controls.css 的 1（滑块拇指，BD-19 裁定保留、不在本次 6 条内）与
  // styles.css 的 7 一个都没动；文件数仍是 3。一次像素未改，改的只是归类。
  // 裁定判的是**归类**，不是取值：这 6 条从没被要求归进 §5.3 四级，因为四级描述的是
  // 「浮起多高」这个层级语义，而下面这些描述的是「这块布有多厚 / 这本封面是实体 /
  // 键帽的台阶 / 朱砂自己发光」这类材质与光学。强归任何一档都是可感知改版
  // （BD-19 的 A–F 家族注释里逐条记过为什么；C/D/F 三族就是这 6 条）。
  //
  // 判据形态照批次 AG 的按钮角色：**角色是登记出来的数据，不是形状猜出来的**。
  // 三条硬规矩，缺一条这条豁免就变成洗债的口袋：
  //   ① key = 文件 + 选择器文本 + 值串三者一起算。选择器或值一改，登记失配 →
  //      这条重新被数成债 → 红。所以「顺手把这层阴影改强一点」躲不进豁免。
  //   ② 未命中的条目当场红（僵尸豁免 = 有人删了材质却在别处新建同类却没登记）。
  //   ③ 只收这 6 条、不允许通配与前缀匹配；新增一族材质要人批，不由判据自动认。
  // ⚠ 与批次 AD 那两条豁免的区别要写清：那两条按**几何形状**豁免（纯 inset 不是层级、
  //   0 0 0 Npx 是描边），形状判据可推广；本表按**逐条登记**豁免，因为「材质厚度」在
  //   几何上就是一层真投影，形状判据分不开它——这也正是它必须带②的失配硬红的原因。
  const DECOR_SHADOWS = {
    // C 族·布面印章的后层托影（还叠着两圈 inset 环）：BD-23 品牌字形批量过这颗印的盒子几何。
    "src/styles/editorial-studio.css|.desktop-brand-mark|inset 0 0 0 2px color-mix(in srgb, var(--studio-cloth) 88%, transparent), inset 0 0 0 3px color-mix(in srgb, var(--studio-seal) 55%, transparent), 0 3px 10px rgba(0, 0, 0, 0.28)": "印章布面的材质厚度（C 族）",
    // C 族·书封实体：白冠光是纸面受光，0 2px 5px 是书的厚度，两者都不是「浮起层级」。
    "src/styles/editorial-studio.css|.library-cover|inset 0 1px 0 rgba(255, 255, 255, 0.28), 0 2px 5px rgba(34, 38, 48, 0.18)": "书封厚度与受光（C 族）",
    // C 族·键帽台阶线：1px、零模糊，几何上是描边，但 AD 的环判据按 0 0 0 Npx 放过不了它。
    "src/styles/editorial-studio.css|.desktop-search-command kbd, .uni-search-shell kbd|0 1px 0 var(--border-strong)": "键帽台阶线（C 族）",
    // D 族·吸顶栏双层柔影：横贯整行的栏，1 档撑不起、2 档会让工具条看起来浮起来。
    "src/styles/editorial-studio.css|.desktop-commandbar|0 1px 3px rgba(34, 38, 48, 0.05), 0 6px 18px rgba(34, 38, 48, 0.04)": "吸顶栏的贴附柔影（D 族）",
    // D 族·朱砂计数徽标的彩光：四级里没有彩色投影档，归任何一档都等于擦掉那层强调色。
    "src/styles/editorial-studio.css|.desktop-home-inbox-count|0 2px 6px color-mix(in srgb, var(--studio-seal) 35%, transparent)": "徽标彩光（D 族）",
    // F 族·稿纸纸张厚度：penumbra 走自己的令牌 --studio-paper-shadow，四档没有一档含它。
    "src/styles/editorial-studio.css|.scene-editor|0 1px 2px rgba(34, 38, 48, 0.08), 0 24px 60px var(--studio-paper-shadow), 0 2px 6px rgba(34, 38, 48, 0.06)": "整页纸壳的纸张厚度（F 族）",
  };
  const decorHits = new Set();

  /**
   * 批次 BD-45·仓库自定义的 shadow 工具类几何（从 tailwind.config.ts 现读，不在守卫里抄一份）。
   * 为什么要读它：下面那本值账把「这条债长什么样」钉成 key，而 shadow-paper 的**样子**住在
   * 配置文件里——只 key 上令牌名的话，把 boxShadow.paper 从 0 18px 50px 改成 0 40px 120px
   * 依旧全绿（实测：改配置 alpha .1→.45 守卫毫无反应）。读回来之后，改几何等于改了
   * 名下全部 7 条 @apply 债的 key ⇒ 账外新增 + 账上扫不到 双向即时红。
   * Tailwind 自带档（sm/md/lg/…）不在这里：实测 12 处债全是纸面自定义档或字面 CSS 值，
   * 一个内置档都没用到，所以给它们记 `@default:<令牌>`，等真有用到再按 §5.3 归级。
   */
  const SHADOW_UTIL_GEOMETRY = (() => {
    const m = /boxShadow:\s*\{([\s\S]*?)\}/.exec(tailwindConfig);
    const out = {};
    if (m) for (const e of m[1].matchAll(/(\w+):\s*"([^"]*)"/g)) out[e[1]] = e[2].replace(/\s+/g, " ").trim();
    return out;
  })();
  const utilGeometry = (tok) => {
    const key = /^shadow-(.+)$/.exec(tok)?.[1];
    return key && SHADOW_UTIL_GEOMETRY[key] ? SHADOW_UTIL_GEOMETRY[key] : `@default:${tok}`;
  };

  // 值账：判据认定「一处债」的同一刻，把它解析后的几何值记一条 key。
  // 用**多重集**（数组 + 排序后逐字比对）而不是 Set：同一文件里「同选择器同值」的两条
  // 本来就该是两条债，Set 会把它们并成一条，于是「删掉其中一条」在值账上看不出来。
  // 它不改变 n（计数口径一个字没动），只是把计数已经看过的事实留下来比对。
  const debtVals = [];
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
        // 批次 BD-25 的材质 / 装饰登记：文件 + 选择器文本 + 值串三者全等才豁免。
        const decorKey = `${rel}|${d.parent.selector.replace(/\s+/g, " ").trim()}|${v}`;
        if (decorKey in DECOR_SHADOWS) {
          decorHits.add(decorKey);
          return;
        }
        if (v === "none" || /^var\(--(shadow|focus)/.test(v)) return;
        // 只有「至少一个非 inset 层」才是投影；纯 inset 是色条/内衬装饰，§5.3 四级不针对它
        if (!shadowLayers(v).some((L) => !/^inset\b/.test(L))) return;
        // 批次 AD：纯描边环（0 0 0 Npx）同属「不是投影层级」——§2.3 的 focus-visible 机制，
        // 和上面 var(--focus-*) 的豁免是同一件事的两种写法，两侧对称才算修完。
        if (isPureRingValue(v)) return;
        // BD-45 值账：CSS 字面声明的 key 用「文件 ⑉ 选择器文本 ⑉ 值串」。
        // 同选择器同值的两条会记成两条一样的 key——多重集正是为了这件事（Set 会并成一条，
        // 「删掉其中一条」就看不见了），下面的条数闸核的是「值账条数 == 计数总债数」。
        debtVals.push(`${rel}\t${d.parent.selector.replace(/\s+/g, " ").trim()}\t${v}`);
        n += 1;
      });
      ast.walkAtRules("apply", (a) => {
        if (PHANTOM_SHADOW.test(a.params)) {
          fail(`${rel} 里 @apply 写了 shadow-[var(...)] 幻影形式：Tailwind 产物实测只产 --tw-shadow-color、无 box-shadow 声明，界面画不出阴影但棘轮计数会降。改用 [box-shadow:var(--shadow-N)]。`);
        }
        for (const tok of a.params.match(SHADOW_TOKEN) ?? []) {
          if (!tsxShadowDebt(tok)) continue;
          // BD-45 值账：@apply 的债长什么样由工具类**解析后的几何**决定，令牌名本身不算信息
          debtVals.push(`${rel}\t${a.parent.selector.replace(/\s+/g, " ").trim()}\t${utilGeometry(tok)}`);
          n += 1;
        }
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
        for (const tok of line.match(SHADOW_TOKEN) ?? []) {
          if (!tsxShadowDebt(tok)) continue;
          // TSX 侧没有「选择器文本」这个概念（工具类挂在 JSX 字符串里），key 记
          // 「文件 ⑉ 令牌 ⑉ 解析后的几何」。同一文件同令牌的两处仍是两条，多重集分得开。
          debtVals.push(`${rel}\t${tok}\t${utilGeometry(tok)}`);
          n += 1;
        }
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
          // 属性形式带字面几何：值串归一化后直接进 key（下划线是 Tailwind 的空格写法，
          // 归一成空格才能让 `shadow-[0_1px_2px_rgba(...)]` 与 CSS 里同值串可比）。
          debtVals.push(`${rel}\t[box-shadow]\t${bv.replace(/_/g, " ").replace(/\s+/g, " ").trim()}`);
          n += 1;
        }
      }
    }
    if (n) actual[rel] = (actual[rel] ?? 0) + n;
  }

  // 登记表里的条目没被扫到 = 那条声明被改了或删了却没同步登记（僵尸豁免）。
  const decorMiss = Object.keys(DECOR_SHADOWS).filter((k) => !decorHits.has(k));
  if (decorMiss.length) {
    fail(
      `第 8 步阴影刻度：材质/装饰登记表里有 ${decorMiss.length} 条没在源码里扫到——` +
        decorMiss.slice(0, 3).map((k) => `${k.split("|").slice(0, 2).join(" ")}（${DECOR_SHADOWS[k]}）`).join("、") +
        `。要么那条声明改了值/换了选择器（登记按三者全等算，必须跟着重登），要么被删了（那就把条目一起删）。` +
        `拿一条扫不到的豁免当豁免，等于给以后同类装饰开无登记的口子。`
    );
  }
  const over = Object.keys(actual).filter((f) => actual[f] > (SHADOW_BUDGET[f] ?? 0));
  const stale = Object.keys(SHADOW_BUDGET).filter((f) => (SHADOW_BUDGET[f] ?? 0) > (actual[f] ?? 0));

  /* ---- 批次 BD-45·阴影**值**账（把计数棘轮的口径补成逐条相等）----
   *
   * 为什么要有这一本：SHADOW_BUDGET 数的是**每个文件几处债**，它只认数量。实测三种漂移
   * 全都全绿（都是本批先跑再写牙的，不是推断）：
   *   ① editorial-studio.css:1459 那条 `0 1px 2px rgba(34,38,48,.05)` 的 alpha 改成 .40
   *      ——投影强了 8 倍，计数还是 4/4，守卫 EXIT=0；
   *   ② styles.css:271 的 `@apply … shadow-paper` 换成 `shadow-lift`
   *      ——两处几何不同（0 18px 50px ↔ 0 6px 18px），计数还是 7/7，EXIT=0；
   *   ③ tailwind.config.ts 的 boxShadow.paper 从 `0 18px 50px rgba(34,38,48,.1)`
   *      改成 alpha .45——**全部 7 条皮肤债同时改版**，守卫压根没读这个文件，EXIT=0。
   * ③ 最坏：它是「一次改动、全站夜读皮肤改版、判据沉默」的形状。计数棘轮能防住「多加一处」
   * 和「还了不降预算」，防不住「把已登记的那处改强/改弱/换成另一档」。这正是 BD-41 那句话在
   * 新判据上的重演——判据退化最坏的形式不是错判，是什么都不表现。
   *
   * 判据形状：每认定一处债，就把它**解析后的几何值**记一条 key（多重集，见上面注释），
   * 排序后与账逐字比对；另加一道条数闸——值账条数必须等于计数总债数，否则说明某条
   * `n += 1` 加了而 key 没记（两条口径必须同源，同 BD-43 的 aMiss/aStale 双锁思路）。
   * 只登记**离刻度的债**：走 var(--shadow-*) 的、纯 inset 的、描边环的、DECOR 登记的一律不进
   * 这本账——那些已经有各自的口径（令牌唯一真相、§5.3 几何豁免、BD-25 三者全等登记）。
   *
   * 立场沿用 BD-25：只登记、不判死，也不许拿它当「这些值就该这样」的设计背书。
   * 重钉的唯一入口是 DESIGN_TOKEN_DUMP_SHADOW_VALUES=1，不许手写。
   */
  const SHADOW_VALUE_LEDGER = [
        "src/features/settings/settings-controls.css\t.paper-slider::-webkit-slider-thumb\t0 1px 4px rgba(15, 20, 28, 0.22)",
        "src/styles.css\t.reader-bg-amber\t0 18px 50px rgba(34, 38, 48, 0.1)",
        "src/styles.css\t.reader-bg-beans\t0 18px 50px rgba(34, 38, 48, 0.1)",
        "src/styles.css\t.reader-bg-green\t0 18px 50px rgba(34, 38, 48, 0.1)",
        "src/styles.css\t.reader-bg-night\t0 18px 50px rgba(34, 38, 48, 0.1)",
        "src/styles.css\t.reader-bg-parchment\t0 18px 50px rgba(34, 38, 48, 0.1)",
        "src/styles.css\t.reader-bg-warm\t0 18px 50px rgba(34, 38, 48, 0.1)",
        "src/styles.css\t.reader-bg-white\t0 18px 50px rgba(34, 38, 48, 0.1)",
        "src/styles/editorial-studio.css\t.cards-board-card, .background-card, .cards-detail-card, .history-empty\t0 1px 2px rgba(34, 38, 48, 0.05)",
        "src/styles/editorial-studio.css\t.cards-board-card:hover\t0 6px 16px rgba(34, 38, 48, 0.09)",
        "src/styles/editorial-studio.css\t.stats-card, .paper-panel\t0 1px 2px rgba(15, 20, 28, 0.04)",
        "src/styles/editorial-studio.css\t:root[data-app-theme=\"dark\"] .stats-card, :root[data-app-theme=\"dark\"] .desktop-panel-card, :root[data-app-theme=\"dark\"] .paper-panel, :root[data-app-theme=\"dark\"] .desktop-library-panel\tinset 0 1px 0 rgba(255, 255, 255, 0.045), 0 10px 30px rgba(0, 0, 0, 0.38)",
  ].sort();
  const measured = debtVals.slice().sort();
  // 重钉账本的唯一入口（同 DEAD / ABSENCE 两本的手法）：账由实测生成，不许手写。
  if (process.env.DESIGN_TOKEN_DUMP_SHADOW_VALUES === "1") {
    console.log(`${TAG} [DESIGN_TOKEN_DUMP_SHADOW_VALUES] 阴影值账 ${measured.length} 条（逐字粘进 SHADOW_VALUE_LEDGER）：`);
    for (const k of measured) console.log(`    ${JSON.stringify(k)},`);
  }
  const vMiss = measured.filter((k) => !SHADOW_VALUE_LEDGER.includes(k));
  const vStale = SHADOW_VALUE_LEDGER.filter((k) => !measured.includes(k));
  const totalActual = Object.values(actual).reduce((a, b) => a + b, 0);
  // 条数闸：值账与计数账必须同源同量。哪天新加一条 `n += 1` 忘了配 debtVals.push，
  // 那条债就从值账上隐形了——这比少一个 key 严重得多，因为它让「这条债长什么样」无人认。
  if (measured.length !== totalActual) {
    fail(
      `第 8 步阴影值账：计数账认出 ${totalActual} 处债，值账只记到 ${measured.length} 条——两条口径不同源。` +
        `要么某处 ` + "`n += 1`" + ` 旁边漏了记值（那条债从此在值账上隐形），要么值账多记了不判债的分支。` +
        `先核是哪一侧的分支数错了，别改数字凑平。`
    );
  }
  if (vMiss.length || vStale.length) {
    const parts = [];
    if (vMiss.length) {
      parts.push(
        `新增 ${vMiss.length} 条不在账上（现存 ${SHADOW_VALUE_LEDGER.length} 条）：` +
          vMiss.slice(0, 6).map((k) => k.replace(/\t/g, " ⑉ ")).join(" ｜ ")
      );
    }
    if (vStale.length) {
      parts.push(
        `账上 ${vStale.length} 条已经扫不到：` + vStale.slice(0, 6).map((k) => k.replace(/\t/g, " ⑉ ")).join(" ｜ ")
      );
    }
    fail(
      `第 8 步阴影值账与实测不符（${parts.join("；")}）。\n` +
        `  这条的语义是**几何漂移**而不是数量变化：同一处债换了值（改强、改弱、换成另一个自定义档、` +
        `或 tailwind.config.ts 里 boxShadow 的几何被改）——文件计数一个字没动，界面上却改版了。\n` +
        `  改法只有两种：\n` +
        `  · 这处值漂移是本批有意的设计改动 ⇒ 重跑 DESIGN_TOKEN_DUMP_SHADOW_VALUES=1 拿新账整块粘回来，` +
        `并在提交信息里写清是哪一页、观感怎么变（不许顺手「同步一下账」）。\n` +
        `  · 不是有意的 ⇒ 把值改回去。特别注意 tailwind.config.ts 那一支：它一改就是名下**全部**债同时改版，` +
        `本账把它的几何读回来当 key 的一部分，就是为了这件事。\n` +
        `  ⚠ 别拿「计数没超预算」当理由：这本账就是为了让数量不变的改版也红。`
    );
  }
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
  console.log(`${TAG} [第 8 步待收敛] 阴影离刻度 ${totalActual} 处（${Object.keys(actual).length} 个文件，已按文件冻结预算；值账 ${SHADOW_VALUE_LEDGER.length} 条逐条相等，BD-45）`);
}

/* -------------------------------- 规格 §5.1 间距刻度棘轮（第 8 步批次 AW 立项） */

/**
 * §5.1 把间距定成 4px 基准的 11 档，令牌（--sp-0…--sp-11）早在批次 C 前就进了 tokens.css，
 * 而且第 1 步的令牌存在性判据一直在断言这些名字有定义（批次 BD-25 裁定加档后是 13 个名字，
 * 见下面 ③ 的 ✔ 后续：新增的 --sp-12 也已进存在性名单）。实测：全仓 `var(--sp-*)`
 * 引用**零处**——令牌铺了没人用，散写的 gap/padding/margin 各数各的。这是规格开头那张
 * 「间距 19 种 gap、无刻度表」的账，也是第 8 步「字号/间距/圆角按刻度收敛」三本里
 * 唯一还没立判据的一本（圆角在批次 AV 清零；阴影那本在批次 BD-19 之后剩 21 处 / 3 个文件，
 * 已经逐族判过「为什么现在不能还」，见 SHADOW_BUDGET 里 A–F 六个家族——它冻结着，但不再是
 * 「还没轮到」）。⚠ 这句「剩 21 处」是本段立项时的口径，批次 BD-25 把其中 6 条材质/装饰
 * 声明登记移出投影账后实测 15 处 / 3 个文件（见上面阴影一节的 DECOR_SHADOWS 与本节末的账）。
 *
 * 本批只立项、不还债：把当前实测值按文件冻结成预算，从此新增一处离刻度间距就红、
 * 还掉一处不降预算也红。立项时 465 处 / 28 个文件（批次 BD-22 删掉 5 条级联已死的间距声明后
 * 实测 460 处），量级按「🔴 面广 → 分页面批次，每批截图确认」
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
 * 定为 10px，而 §5.1 当时那 11 档里没有 10px（…8 / 12…）。实测 10px 是全仓最重的离刻度值
 * ——gap 50 处 + padding 80 处 + margin 25 处 = 155 处，占这本账 465 处的三分之一。
 * 所以这本账真正的第一件事不是「逐页收敛」，而是先由人定 10px 的去留：
 *   · 若 §5.1 增补一档（10px），155 处债当场归零，代价是刻度从 11 档变 12 档；
 *   · 若 §2.2 的 sm 内边距改 8 或 12px，则 155 处要逐页改版（可感知，需截图确认）。
 * 在此之前 10px 一律按离刻度计数（保守方向：多算债不会导致误删，替设计提前追认才会），
 * ui.tsx 那条预算 1 就是这场冲突的现场——它是 sizes.sm 的 px-2.5，不是谁写错了数字。
 *
 * ✔ 后续（批次 BD-25，用户裁定落地）：选了上面第一条——**§5.1 增补 10px 一档**，
 *   冲突以「10px 合法」收口。落地方式是令牌追加 `--sp-12: 10px`（**不插号重编号**：
 *   --sp-0…--sp-11 的名字是规格 §5.1、design-sample 与本判据存在性断言的活引用，
 *   插号会让 20/24/32/40/48 全体左移一格，等于用一次「加档」偷换 11 个既有令牌的
 *   含义——那是改版。编号在本表从来只当名字、不当大小）。
 *   裁定前重扫的本体实测（不是上面那组立项数字）：10px 字面值 157 处 + p-2.5 折算
 *   8 处 = 165 处，占当时 460 处的 36%；加档后 460→295、文件 28→20。
 *   ⚠ 上面「155 处 / 三分之一」是批次 AW 立项时的数，本批重扫得 157 / 36%：
 *   中间隔着 BD-22 删的 5 条级联已死声明与 BD-24 的行高改动，计数会漂——按老规矩
 *   不回写历史数字，只在此声明它过期。
 *   ⚠ 这条裁定**没改一个像素**：它改的是「什么算合法」，不是界面。所以产物 maps
 *   与赢家值探针必须逐字节不变——这是本批唯一的验收线，也是它和
 *   「顺手把 10 收成 8」那条路的本质区别。
 *   新牙：10px 从此必须**绿**（反向），且「拿 --sp-12 换掉一处 8px」这种把加档当成
 *   改版工具用的写法不在本判据管辖（它改的是像素，归截图那条流程）。
 */
{
  // ⚠ 批次 BD-25 给这张表加了一道双向锁，理由两种失败各占一边：
  //   · 纯手抄（原样）：加一档要改两处，漏一处就出现「令牌有这档、判据不认」，
  //     本批的 --sp-12 正是这个形状——先改令牌再忘改集合，165 处当场全红。
  //   · 纯派生（从 tokens.css 扫出来当合法集）：往 tokens.css 塞一个 --sp-13: 7px
  //     就等于给全仓散写的 7px 发证，债从声明搬进定义、判据当场瞎掉（批次 L 的原话）。
  //   所以合法集仍是这份**显式表**，但下面加一条断言：表必须与 tokens.css 的 --sp-*
  //   值集**完全相等**（多一档少一档都红）。这样加档必须两处同改、且改动是可见的，
  //   漂移不再是静默的。
  const SP_SCALE_PX = new Set([0, 2, 4, 6, 8, 10, 12, 16, 20, 24, 32, 40, 48]);
  {
    const tokenSp = new Set(
      [...lightBlock.matchAll(/--sp-\d+:\s*(\d+(?:\.\d+)?)px\b/g)].map((m) => parseFloat(m[1]))
    );
    // --sp-0: 0 没有 px 后缀，上面那条正则漏它；0 在表里，手工补齐再比
    if (/--sp-0:\s*0\s*;/.test(lightBlock)) tokenSp.add(0);
    const onlyTable = [...SP_SCALE_PX].filter((v) => !tokenSp.has(v));
    const onlyToken = [...tokenSp].filter((v) => !SP_SCALE_PX.has(v));
    if (onlyTable.length || onlyToken.length) {
      fail(
        `第 8 步间距刻度：SP_SCALE_PX 与 tokens.css 的 --sp-* 值集不一致——` +
          `只在判据表里有 ${onlyTable.join("/") || "无"}；只在令牌里有 ${onlyToken.join("/") || "无"}。` +
          `加档/删档必须两处同改（令牌是设计的事实，这张表是判据的合法集，谁单方面变了都算事故）。`
      );
    }
  }
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
  // 批次 BD-25（用户裁定落地）：10px 进刻度后，本表由判据本体重扫，460→295、28→20 文件。
  //   本批复测过这条账的拆法（拿判据本体跑两遍，第二遍把 10 从 SP_SCALE_PX 里摘掉）：
  //   消掉的 165 处 = CSS 字面 10px 157 处 + @apply …-2.5 1 处 + TSX 工具类 -2.5 7 处。
  //   ⚠ 台账（--show-spacing-ledger）只登 CSS 侧，TSX 那 7 处只进计数不进台账，
  //   所以 448 行台账 ≠ 460 处；拿台账去核对总数会对不上，别据此判判据漏数。
  // 归零并**移除条目**的 8 份（空表 = 新增即红，比留 0 更诚实）：ErrorBoundary.tsx、ui.tsx、
  // scene-candidate.css、inbox-local.css、overview-local.css、ReaderSidePanel.tsx、
  // ReaderTopNav.tsx、StorageSection.tsx——它们原本的债就全是 10px/p-2.5 这一族，
  // 裁定的意思是「这一族不再算债」，所以是合法化、不是还债；条目数字来自重扫，不来自减法。
  // ⚠ ui.tsx 那条预算 1 是 sizes.sm 的 px-2.5，它曾是「规格自相冲突」的现场（见上面 ③ 的
  //   立项文字）；裁定之后它合规、条目消失，钉在它上面的「间距棘轮②」随之失去锚点，
  //   本批把它搬到仍有活债的 TSX 宿主（见 inject 脚本 BD-25 注释）。
  const SPACING_BUDGET = {
    "src/features/creation/cards/cards-local.css": 7,
    // 批次 BD-59（gap 7px 族·12 成员批，跨 5 文件）：9→7。本文件还 2 处：
    //   .global-card-library-assets ul `gap: 7px`→8px（单列 grid 列表容器，同文件
    //   trash-list/type-list/link-list 三个行列表全 8px；零实拍——seed 卡 0 附件）、
    //   .global-card-delete-impact `gap: 7px`→8px（双列网格瓷砖间距，多列网格
    //   在档票 .cte-field-grid/.scene-planning-grid/.cards-checkbox-group 全 8px）。
    //   值账：本文件 gap 7px 桶 2→0（整桶移除）。实拍一枚（03-delete-impact
    //   w406/h126.5、2 列 199.5、5 瓦片 3 行）。
    // 批次 BD-58（gap 3px 族整族清算，跨 4 文件）：14→9。本文件还 4 处：
    //   .cards-type-list-item > div:first-child、.project-card-link-list li > div、
    //   .global-card-trash-list li > div、.global-card-library-heading 全 `gap: 3px`→2px
    //   （「行/卡内层 strong+说明双行 grid 栈」族，在档票 .global-card-library-assets
    //   li > div 2px + styles.css .cards-list-main/.inbox-item-main/.project-home-item-main
    //   2px + operation.css .bundle-import-result li 2px 10px，零 4px 票）；
    //   .cards-relation-delete `gap: 3px`→4px（行内裸图标+11px 字小控件族，在档票
    //   .cards-relations-head button/.cards-view-switch button/.writing-watch/
    //   .scene-save-status/.creation-proof-ignore/.desktop-dock-* 全 4px，零 2px 票）。
    //   值账：本文件 gap 3px 桶 5→0（整桶移除）。实拍三枚（03-type-item w132/h63、
    //   03-link-item w96.02/h45、02-trash w199.3/h43.5）、零实拍一枚（relation-delete）。
    // 批次 BD-57（padding 9px 垂直槽族·11 成员批，跨 5 文件）：15→14。本文件还 1 处：
    //   .cards-type-list-item `9px 10px`→8px 10px（卡片类型列表行，h=10px 行容器族顶槽票
    //   8×20 对 10×6，9 为孤档；探针一 04-typeitem 实拍 8 行 h 83）。值账：本文件 padding 9px 桶
    //   1→0（清零移除），判据在条上方。
    // 批次 BD-56（padding 9px 水平槽族·11 成员批，跨 4 文件）：18→15。本文件还 3 处：
    //   .global-card-library-assets li `8px 9px`→8px 10px（附件行卡，双胞胎 delete-impact
    //   li 8px 10px，零实拍如实记）、.global-card-library-usage li `4px 9px`→4px 8px（压缩档
    //   药丸，双胞胎 usage header strong 3px 8px）、.global-card-library-policy
    //   `padding-left: 9px`→10px（2px copper 强调条缩进，双胞胎 outline-revert 8px 10px）。
    //   值账：本文件 padding 9px 桶 3→1、padding-left 9px 桶 1→0（清零移除），判据在各条上方。
    // 批次 BD-47（间距还债批·关系图）：条目 4→0 **整条移除**（空表=新增即红，比留 0 更诚实）。
    // .relation-graph-chip `padding: 3px 9px` 与 .relation-graph-zoom button `padding: 3px 7px`
    // 收成本文件 .relation-graph-search 已在用的 4px 8px——同族控件取同值，不是新造档。
    // 值账同批重钉：本文件 3 桶消失（padding 3px ⑉ 2、7px ⑉ 1、9px ⑉ 1——4 处债、
    // 3 个桶，3px 那桶本来就两笔）；全站 136 桶 → 133 桶、271 处 → 267 处，数字来自 dump（BD-48 之后是 131 桶 / 263 处，BD-49 之后是 124 桶 / 256 处，BD-50 之后是 121 桶 / 252 处，BD-51 之后是 117 桶 / 248 处，BD-52 之后是 114 桶 / 239 处，BD-53 之后是 110 桶 / 233 处，BD-54 之后是 108 桶 / 221 处）。
    "src/features/creation/editor/continuous-editor.css": 2,
    // 批次 BD-53（族批次·分段岛外壳 3px 族 + chip 行间隙 5px 族 + 页签基础档）：条目 5→2。
    // 本文件还 3 处：.writing-margin-tabs `padding: 3px`→4px（岛族三胞胎之一，另两个
    //   成员在 editorial-studio.css：那条覆盖三座岛的 .writing-mode-switch 组规则与
    //   .writing-outline-view-switch 的 !important 副本，同批同值 ⇒ editorial 条目 65→63）、
    //   .writing-margin-tabs button `padding: 5px 8px`→6px 8px（只还那个 5，8 在档上；
    //   BD-51 当年把它整条判成「被 :not 压死的死副本」是**半错**——:not 排除了折叠钮，
    //   这条对 .writing-margin-collapse 一直是活的，真宿主实测 pad 5px 8px 为证，勘误
    //   写进条内注释）、.scene-radar-tags `gap: 5px`→6px（与 search.css 的
    //   .uni-search-filters 是逐字同值的双胞胎 ⇒ search 条目 7→6）。
    // ⚠ 本文件**留下 2 处不还**：`.scene-radar` gap 14px 与 `.writing-margin-tabs`
    //   margin-bottom 14px 都是两档正中的平票（14∈12/16），而同角色的另一侧证据在
    //   别的文件里自己还欠着账：14px 那一族的成员（.creation-writing-stack、
    //   .creation-goals-grid 的 12px 14px、.writing-margin dl、.desktop-sidebar 窄屏档）
    //   没有一条能当「在档先例」投——它们全是同一种离刻度值。按 BD-51 立的规矩，
    //   族里找不到在档成员 ⇒ 这一族不是「还债」而是「定档」，得先由规格批给 14px
    //   一个去向（收 12 还是 16 会推着 styles.css / editorial 一批文件一起动），
    //   不在本批顺手挑档。判据与两条读数（岛下块距 14 实测 = margin-bottom，
    //   覆盖 12/16/10 段距跟着走）都写在 scene-radar.css 各条上方。
    "src/features/creation/editor/scene-radar.css": 1,
    "src/features/creation/editor/writing-quick-reference.css": 10,
    // 批次 BD-59（gap 7px 族）：11→10。本文件还 1 处：.writing-quick-search
    //   `gap: 7px`→6px（搜索容器内 图标↔input；三胞胎 .writing-quick-toggle /
    //   relation-graph-search / cards-search 全 6px）。值账：本文件 gap 7px 桶
    //   1→0（整桶移除）。实拍 05-quick w295/h40、svg+input 两子项。
    // 批次 BD-57（padding 9px 垂直槽族）：14→11。本文件还 3 处：
    //   .writing-quick-reference-head 底槽 9→10（h=16px 家族底槽 8 零票、10 有
    //   proof-summary 逐字同槽票；顶槽 11 离刻度债留在 padding 11px 桶）、
    //   .writing-quick-tabs button 顶槽 9→8 + 底槽 7→6（h=4px 紧凑族孤档 9，
    //   7 本身离刻度，同批下移保 pt−pb=2px 光学不对称）。值账：本文件 padding 9px
    //   桶 2→0（整桶移除）、padding 7px 桶 5→4（余 4：quick-context 7px 15px 的 7、
    //   quick-search 7px 8px、quick-list li button 7px、head button 5px 7px——
    //   水平槽/独立值，留 7px 桶批）。判据在各条上方。
    // 批次 BD-56（padding 9px 水平槽族）：16→14。本文件还 2 处：
    //   .writing-quick-search `7px 9px`→7px 8px（搜索输入容器，双胞胎 quick-field input 6px 8px、
    //   relation-graph-search 4px 8px）、.writing-quick-toggle `6px 9px`→6px 8px（6px 顶槽小钮，
    //   双胞胎 scene-radar writing-margin-tabs button 6px 8px）。值账：本文件 padding 9px 桶 4→2
    //   （余 2 是 head 底槽 9px 与 tabs button 顶槽 9px，垂直槽、留给垂直槽批），判据在各条上方。
    // 批次 BD-55（padding 14px 左右档族·14 成员批，跨 5 文件）：17→16。本文件还 1 处：
    //   .writing-quick-reference-head `11px 14px 9px 16px`→`11px 16px 9px 16px`
    //   （右槽与左槽自己错位；头/面板档的在档先例全在 16，判据在该条上方。真宿主
    //   实拍探针 02b：面板恒宽 320，pl 16 / pr 14）。
    // 批次 BD-54（gap 5px 族整族清算，跨 3 文件）：18→17。本文件还 1 处：
    //   .writing-quick-context `gap: 5px`→4px（裸的 11px 图标+文字状态标签族第四条，
    //   判据与票形见 styles.css .writing-watch 条上方；本文件头部按钮与 .writing-quick-field
    //   早就都在 4px 上）。真宿主实测盒形零变化（h 30.5、w 319 全档恒定）。
    // 批次 BD-49（间距还债批·写作台侧栏）：条目 7→0 **整条移除**（空表=新增即红）。
    // 还的是 writing-reference.css 全部七处，每处都是**两档正中的平票**（1/3/5/7/9/18px，
    // 刻度 0/2/4/6/8/10/12/16/20…），「就近」一律不裁决 ⇒ 逐条按同角色先例 + 实渲探针定档，
    // 判据与实测数字都写在该文件对应规则上方（先例一律按选择器记、不钉行号——BD-48 立的规矩）：
    //   selection gap 3→4（同面板 .writing-margin dl > div 用 4px）；value 5px 8px→6px 8px
    //   （框内文字族 vs 控件族分界，BD-48 立的那条）；close 3→6（图标关闭钮族地板 26px，
    //   现状 20×20 是族内最低）；item 7px 8px→8px 10px（可点击行最强档，且 6px 会压矮命中）；
    //   item-main gap 1→2（双行紧凑 *-main 族无一例用 0）；revert 8px 9px→8px 10px
    //   （竖向本在档上，只还横向那 1px——见该条的读账勘误）；actions margin-top 18→16
    //   （对话框底部动作行的收敛写法 mt-4=16px，两处同形）。
    // 值账同批重钉：本文件 7 桶全消失；全站 131 桶 → 124 桶、263 处 → 256 处，数字来自 dump。
    // 批次 BD-52（行内徽标族整族还债，跨 3 文件）：本文件条目 9→6。还的是
    //   .snapshot-category-badge 1px 7px→2px 8px 与 .history-protection-id 1px 6px→2px 6px
    //   （共 3 处）；判据与实测在 history-local.css 各条上方。
    // 批次 BD-55（padding 14px 左右档族）：条目 6→4。还 2 处：.history-item
    //   `11px 14px`→11px 12px（行族票，零实拍如实记）与 .history-restore-success
    //   `12px 14px`→12px（本来四边 12、只有左右写歪）；判据在各条上方。
    "src/features/creation/history/history-local.css": 3,
    // 批次 BD-57（padding 9px 垂直槽族）：4→3。还 1 处：.history-confirm-name input
    //   `9px 10px`→8px 10px（h=10px 输入容器族顶槽票 8×20 多数 + 逐字同族 .history-field
    //   input[type="text"] 8px 10px；探针二 05b 预置软删场景实拍对话框输入框 h 39.5）。
    //   值账：本文件 padding 9px 桶 1→0（整桶移除）。
    "src/features/creation/inbox/components/InboxQuickInput.tsx": 1,
    // 批次 BD-50（间距还债批·长任务对话框）：条目 4→0 **整条移除**（空表=新增即红）。
    // 三处规则、四处债（.bundle-import-result li 是 7px 与 9px 两个平票叠在一处）：
    //   .operation-result padding 14→12（对话框体内状态块族 = 12px，页面级大面才是 16px）；
    //   .resource-scan-empty padding 14→12（直系同族 .scene-radar-empty 就是 12px，且与本条
    //     同一次渲染里是父子，两处同步降档）；.bundle-import-result li 7px 9px→8px 10px
    //     （同文件同角色兄弟 .resource-scan-item 与 .operation-commit-note 都是 8px 10px）。
    //   三处全是两档正中的平票 ⇒ 「就近」不裁决，判据与实渲读数写在 operation.css 各条上方。
    // 值账同批重钉：本文件 3 桶全消失；全站 124 桶 → 121 桶、256 处 → 252 处，数字来自 dump。
    // 批次 BD-48（间距还债批·大纲）：条目 3→0 **整条移除**（空表=新增即红）。
    // 还的是 .outline-numbering-select / .card-board-move-select 的 `padding: 3px 8px`
    // 与 .outline-impact-notice 的 `padding: 9px 12px`——三处收 4px 8px / 10px 12px，
    // 档位由实渲高度探针 + 同角色邻居先例裁决（见 outline-reorg.css 的批注）。
    // 批次 BD-55（padding 14px 左右档族）：条目 19→18。还 1 处：
    //   .preview-block--letter `10px 14px`→10px 12px（直系双胞胎 .preview-block--note
    //   就是 8px 12px；seed 里两种插块各 0 个 ⇒ 零实拍如实记在条上）。
    "src/features/creation/preview/preview-local.css": 17,
    // 批次 BD-58（gap 3px 族整族清算，跨 4 文件）：18→17。本文件还 1 处：
    //   .preview-jump `gap: 3px`→4px（「11px 文字标签 + 30px select」的 label→控件
    //   栈，同族在档票全在 4：encryption.css .pe-field、replace.css
    //   .replace-query-form label、styles.css .scene-planning-grid label）。
    //   值账：本文件 gap 3px 桶 1→0（整桶移除）。探针三 05-preview 实拍 w 200/h 49.5。
    // 批次 BD-51（间距还债批·查找替换浮层 + 口令框）：条目 3→0 **整条移除**（空表=新增即红）。
    //   .replace-panel padding 18→16（同角色 fixed 浮层只有 .background-detail 在档，它就是 16px）；
    //   .replace-progress-card 22→20（同角色居中卡只有 .writing-reanchor-dialog 在档，20px）；
    //   .replace-query-form input 7px 10px→8px 10px（同角色输入框 .history-field input 就是 8px 10px，
    //   且命中区只增不减）。三处全是两档正中的平票 ⇒「就近」不裁决，判据与实渲读数在 replace.css 各条上方。
    //   同批还掉 encryption.css 的 .pe-input 8px 11px→8px 10px（横向改档实测高度 39 不变，零成本归位）。
    // 值账同批重钉：replace.css 3 桶 + encryption.css 1 桶全消失；全站 121 桶 → 117 桶、252 处 → 248 处，数字来自 dump。
    "src/features/library/ExcerptPicker.tsx": 1,
    "src/features/library/LibraryPage.tsx": 1,
    "src/features/library/reader/components/ReaderSearchOverlay.tsx": 1,
    "src/features/library/toc/TocList.tsx": 1,
    // 批次 BD-32（还死声明账第一批）：10→9。摘掉的 .uni-search-state `padding: 26px 12px`
    // 是从不渲染的死声明，26px 又离刻度——一条墓碑同时还了两本账。
    // 批次 BD-52（行内徽标族整族还债，跨 3 文件）：条目 9→7。还的是 .uni-result-pending
    //   `padding: 1px 7px`（2 处，1 与 7 各算一笔）⇒ 收 2px 8px，判据在 styles.css
    //   .card-board-card-status 条上方（同族三胞胎一起还）。.uni-hit `padding: 0 1px`
    //   本批**不动**：那是 <mark> 命中高亮的左右 1px 间隙，不是徽标内衬，族不同。
    // 批次 BD-53（chip 行间隙族，跨 2 文件）：条目 7→6。还的是 .uni-search-filters
    //   `gap: 5px`→6px（与 scene-radar.css 的 .scene-radar-tags 逐字同值的双胞胎，
    //   同批同值；档位投票 6px×6、8px×12、4px 零成员，判据在两条各自上方）。
    //   ⚠ .uni-search-filters button `5px 10px` 与 .uni-search-close `5px` **不动**——
    //   那是钮的内衬，与「行间隙」两个族（条内注释已记）。
    "src/features/search/search.css": 5,
    // 批次 BD-59（gap 7px 族）：6→5。本文件还 1 处：.uni-result-title `gap: 7px`→8px
    //   （标题 + 「待迁移」徽标行；同角色 .inbox-item-title / .export-preset-option-head /
    //   .desktop-titlebar-title 全 8px）。值账：本文件 gap 7px 桶 1→0（整桶移除）。
    //   实拍 04-search 四枚结果标题 w680/h19.5（该 seed 无 pending 徽标）。
    // 批次 BD-51：encryption.css 条目 1→0 **整条移除**（.pe-input 8px 11px→8px 10px，判据见上方
    // replace.css 的 BD-51 批注与 encryption.css 条内注释）。
    // 批次 BD-33（还死声明账第二批，桌面壳三族）：119→112。摘掉的 22 条死声明里 7 处同时
    // 离 §5.1 刻度（.desktop-commandbar gap:18px / padding:14px 22px、.desktop-page-hero
    // gap:18px / padding:18px、.project-nav gap:3px / padding:10px / button padding:9px 12px）
    // ——和 BD-32 那条 26px 一样，一条墓碑清两本账，这类条目仍排在还债队列最前。
    // 批次 BD-34（还死声明账第三批，写作页 .writing* 七族）：112→108。这 4 处来自两条
    // 死 padding：.writing-manuscript-head `padding: 15px 28px 13px`（三个数全离刻度）与
    // .writing-margin `padding: 20px 18px`（18px 离刻度）。
    // 批次 BD-35（还死声明账第四批，桌面壳零散六族）：108→102。这 6 处来自五条死声明：
    // .desktop-sidebar `padding: 18px 14px`（两个数全离刻度）、.desktop-nav small
    // `margin-top: 1px`、.desktop-search-command `gap: 9px`、同条 `padding: 0 10px 0 13px`
    // 的 13px、.desktop-canvas `padding: 18px`。
    // 批次 BD-39（还死声明账第八批）：101→100。这条来自 .project-workbench 的
    // `gap: 14px`——14 不在 §5.1 八档上，摘掉死副本就是还一笔真的刻度债（全站同批
    // 273→272）。同批另摘的 margin-top: 2px / padding: 12px 在档上，不连带。
    // 批次 BD-40（还死声明账第九批，块级胜者十二族）：100→99。这 1 处来自 .history-empty
    // 的 `padding: 18px`——18 不在 §5.1 八档上（胜者 history-local.css:19 是 32px 16px，
    // 两个数也都不在档上，那是块级表自己的债，不在本文件账上）。同批另两条间距
    // gap: 6px / gap: 10px / padding: 10px 12px 全在档上，不连带。全站同批 272→271。
    // 批次 BD-48（间距还债批·大纲）：99→98。这 1 处来自 .outline-node-actions button
    // 的 `padding: 3px`→4px（同批把 outline-reorg.css 那三处 3px 族一起收掉，不留半还）。
    // ⚠ 本文件另有三处 `padding: 3px` **不在本批账上**——按宿主记而不是按行号记
    // （行号随注释增删自漂：本批一条注释就把首处从 :2640 顶到 :2651，BD-46 的
    // 「行号不进 key」同样适用于注释）：.cards-board-column-head button、
    // .creation-proof-ignore、.desktop-home-inbox-count。值账实测该桶 4→3 而不是
    // 消失，正是这条作用域边界的证据（选择器前缀限定了改动范围）。
    "src/styles.css": 52,
    // 批次 BD-59（gap 7px 族）：67→60。本文件还 6 处 + 摘死 1：
    //   收 8 三笔——.creation-wizard-steps li（圆序号+文字，双胞胎 continuous-editor
    //   .chapter-continuous-scene-head 8px）、.writing-boundary 与 .migration-error
    //   （图标+多行说明/告警，flex-start 同形态）；
    //   收 6 三笔——.writing-project-switcher（label→控件单列栈，四票 6px）、
    //   .migration-report-head（center 图标+单行字，族票 18:8）、.cards-fields-list
    //   （dl 行距，.stats-words/.background-detail-fields 两票 6px）；
    //   摘死一笔——@1180 `.desktop-nav button` 整条四键从未渲染（被 .nav-spine-item
    //   高特异性压住，逐窗实拍 1360/1150/1100/1000 无一致值）。
    //   值账：本文件 gap 7px 桶 7→0（整桶移除）。实拍四枚（02-wizard li w87.02/h20、
    //   05-writing switcher w239/h81 与 boundary w238/h74.75、03-library fields w658.45/h127）、
    //   零实拍两枚（migration-error / migration-report-head，正常态不渲染）。
    // 批次 BD-58（gap 3px 族整族清算，跨 4 文件）：71→67。本文件还 4 处：
    //   .scene-tool-group、.cards-board-card 两条 `gap: 3px`→4px（工具条内联按钮组、
    //   看板列内可拖卡：同角色在档票分别全在 4——.cards-detail-actions/
    //   .cards-view-switch button/.relation-graph-zoom 与 live 的 outline-reorg.css
    //   .card-board-card 4px）；.creation-proof-item-main、.writing-annotation-main
    //   `gap: 3px`→2px（双行紧凑文本栈，同族 *-main/li>div 全 2px）。
    //   值账：本文件 gap 3px 桶 4→0（整桶移除）。实拍两枚（04-write 首组 w71/h30、
    //   03-cards-board w178/h37.5、03-ann w224/h59.69 三处），零实拍一枚（proof-item-main）。
    // 批次 BD-57（padding 9px 垂直槽族·styles.css 六条）：77→71。还 6 处：
    //   .card-board-card / .cards-board-column-head / .cards-board-card 三条 `9px 10px`
    //   →8px 10px（h=10px 行容器族顶槽票 8×20 多数；探针一 04-typeitem、探针二
    //   03-cards-board/04-card-board 实拍活宿主）、.creation-palette-command `9px 12px`
    //   →10px 12px（h=12px 可点击单行项票 .creation-proof-item/.export-preset-option
    //   10px 12px）、.creation-palette-footer `9px 16px`→10px 16px（h=16 家族轻档票
    //   .creation-proof-summary）、.migration-error `9px 12px`→10px 12px（同对话框
    //   .migration-banner 10px 12px 票；探针二 02b 以坏 JSON 实拍 h 38.8）。
    //   值账：本文件 padding 9px 桶 6→0（整桶移除——BD-56 注释里预留的「垂直槽批」
    //   即此批，那句话兑现）。判据在各条上方。
    // 批次 BD-56（padding 9px 水平槽族·styles.css 三条）：80→77。还 3 处：
    //   .creation-proof-item-rule `2px 9px`→2px 8px（压缩档药丸规则徽标，零实拍如实记）、
    //   .creation-proof-ignore `3px 9px`→3px 8px（压缩档药丸忽略钮，与 editorial
    //   .inbox-save-status 逐字双胞胎同批同值，零实拍如实记）、.background-detail-kind
    //   `2px 9px`→2px 8px（压缩档药丸类型徽标，探针 p2 实拍活）。值账：本文件 padding 9px
    //   桶 9→6（余 6 全在垂直槽——card-board-card / creation-palette-command /
    //   creation-palette-footer / migration-error 等的顶槽 9px，留给垂直槽批），判据在各条上方。
    // 批次 BD-55（padding 14px 左右档族·styles.css 六条）：84→80。还 5 处 + 记档 1 + 摘死 1：
    //   .scene-conflict-notice/.scene-save-error `12px 14px`→12px（告示条族票全在 12；
    //   零实拍如实记）、.migration-banner `10px 14px`→10px 12px（横幅族票 + 探针 3
    //   实拍基档 14 活着）、.background-card `12px 14px`→12px（卡列表项族票）、
    //   @media 920 的 .desktop-commandbar `10px 14px` **整条摘除**——同键被 editorial
    //   @1024 的 `9px 16px` 压死（探针实测 900/860 两档 computed 都是 9px 16px），
    //   一条墓碑同时还一笔刻度债（BD-39 同型）；.desktop-library-grid-head `11px 14px`
    //   记档不动（与同网格 .desktop-library-row 的四边 14 逐像素绑定，单动即列错位，
    //   归「四边档」批成对收），.desktop-titlebar padding-left 14 记档在同值在 editorial。
    // 批次 BD-54（gap 5px 族整族清算，跨 3 文件）：94→84。本文件还 10 处（本族在
    //   styles.css 有 11 条，其中 .migration-report dl 普查后判为 dl 族「定档」问题、
    //   记档不动，见那条上方）：.creation-chip 与 .creation-proof-rules label
    //   5px→6px（药丸基础档，票 3:1）、.writing-annotation-actions 5px→6px
    //   （动作按钮行，票 7:2 加 .inbox-item-actions 双胞胎）、.writing-watch /
    //   .scene-save-status / .background-card-kind 5px→4px（裸的 11px 图标微标签
    //   三胞胎，票 4×2 压 6×1）、.cards-field / .creation-proof-banned /
    //   .inbox-target-project 5px→4px（label→控件字段堆叠，票 4×5 压 6×4；后者
    //   inbox-local.css 里那条 hero 覆盖档本来就是 4px）、.inbox-item-main
    //   5px→2px（*item-main 三胞胎全 2px，族同值优先于就近）。
    //   editorial 条目同批 63→62（.inbox-save-status 压缩档灯 5→4，与 styles.css 的
    //   .creation-proof-ignore 同 padding 同 gap），writing-quick-reference.css
    //   18→17（.writing-quick-context 5→4，11px 微标签族第四条）。
    //   ⚠ 值账 110→108 桶、233→221 处（dump 实测）：还掉的 12 处全部收进刻度上的
    //   4/6/2，不产生新桶；消失的两桶是 editorial 与 wqr 的 gap 5px（各自整桶还清），
    //   styles.css 的 gap 5px 桶剩 .migration-report dl 1 处（记档动）。
    // 批次 BD-52（行内徽标族整族还债，跨 3 文件）：98→94。本文件还 4 处：
    //   .card-board-card-status 1px 7px→2px 8px（2 处，两个数各算一笔）、
    //   .inbox-item-title em 1px 8px→2px 8px（1 处）、.writing-annotation-relocate
    //   1px 8px→2px 8px（1 处）。这一族六条规则里本文件占三条，其中两条是逐字同值
    //   的双胞胎（1px 8px）⇒ 同批同值还，判据与实测写在 .card-board-card-status 条上方。
    //   同族在 src/styles.css 里的另一支 .desktop-home-inbox-count 3px 7px 本批不动：
    //   它是朱砂计数印（line-height 1.1 的圆点），不是文字角标，族不同。
    // 批次 BD-26（写作页迁移批）：68→66。这 2 处不是「收敛成令牌」还掉的，是随第 6 步
    // 12 条容器接管规则**一起删掉**的——`.creation-writing-hero .desktop-page-actions
    // button` 的 `padding: 0 9px`（9px 离刻度）与 `.desktop-page-actions button,
    // .desktop-home-inbox` 的 `padding: 0 14px`（14px 离刻度）。组件侧接管为 §2.2 的
    // 10px（sm）/ 12px（md），两档都在刻度上，所以这 2 处是真还债、不是重新归类。
    // ⚠ 数字来自判据本体重扫（--show-spacing-ledger 本批实测 288 行台账 / 计数 293 处；
    //   台账只登 CSS 侧，TSX 那几处只进计数不进台账，两个数天生不相等，别拿台账核总数）。
    // 批次 BD-32：66→65。摘掉的 .overview-status-list `gap: 9px` 是死声明，9px 离刻度。
    // 批次 BD-53（分段岛外壳内衬族，跨 2 文件）：65→63。本文件还 2 处：
    //   .writing-mode-switch,.writing-outline-view-switch,.cards-view-switch 这条组规则
    //   （一条声明覆盖三座岛，但值账按声明计数，只算 1 处）与
    //   .writing-outline-view-switch 的 !important 副本各 3px→4px——两条必须同值，
    //   否则同一族在产物里出现两个档（判据与实测在 scene-radar.css 的
    //   .writing-margin-tabs 条上方，那三胞胎的第三胞胎与本文件这两条同批还掉）。
    //   ⚠ BD-51 曾把 scene-radar 那条页签基础档整条判死，本批勘误为「半死半活」，
    //   见那边注释；这条族里的 editorial 两条**本来就活着**，不属勘误范围。
    // 批次 BD-54（gap 5px 族整族清算，跨 3 文件）：63→62。本文件还 1 处：
    //   .inbox-save-status `gap: 5px`→4px（压缩档药丸灯，与 styles.css 的
    //   .creation-proof-ignore 3px 9px 逐字双胞胎 ⇒ 同值 4px；判据写在那条上方）。
    // 批次 BD-55（padding 14px 左右档族·editorial 五条）：62→58。还 4 处：
    //   .project-home > .stats-card > h3 `0 14px 13px`→0 16px 13px（宿主 .stats-card
    //   自己就是 14px 16px，头档先例全 16）、.writing-margin `16px 14px 40px`→
    //   padding:16px + padding-bottom:40px（面板档票 + 探针 02 岛内容整块跟槽走）、
    //   .inbox-load-more `10px 14px !imp`→10px 12px !imp（托盘条票，探针 09 实拍
    //   14 活着）、.reading-stat-tile `13px 14px !imp`→13px 16px !imp（每枚 tile
    //   都同挂 .stats-card，16 是宿主自家左右档；探针 3 实拍 computed 全 13/14）。
    //   .desktop-titlebar padding-left: 14px **记档不动**——标题栏的印与侧栏的印
    //   同列（sidebar 12 + brand margin 2 = 14，探针 01 实拍 x=14 全等），动它就
    //   把印章从整条左栏列线上扯下来；判据写在那条上方。
    // 批次 BD-56（padding 9px 水平槽族·editorial 三条）：58→55。还 2 处 + 摘死 1：
    //   .desktop-brand.nav-spine-item `6px 9px 14px`→6px 10px 14px（侧栏脊钮品牌行，
    //   双胞胎同 .nav-spine-item 类的 desktop-nav button 7px 10px；探针 01 实拍活）、
    //   .inbox-save-status `3px 9px`→3px 8px（压缩档药丸状态灯，与 styles.css
    //   .creation-proof-ignore 逐字双胞胎同批同值；探针 2 实拍活）；
    //   @media 1024 的 .project-nav button `6px 6px 6px 9px` **摘死声明**——被壳内
    //   `.creation-writing-page--active .project-nav button:not(.project-nav-back)` 与
    //   `.project-nav-back` 两条 `padding: 0 10px !important` 逐钮压死（探针 3 实拍
    //   八个导航钮 computed 全 0/10/0/10、active:true），min-height/font-size 两活键保留，
    //   与 BD-55 摘 @920 commandbar 死声明同法。值账：本文件 padding 9px 桶 4→1（余 1 是
    //   @1024 .desktop-commandbar `9px 16px` 的顶槽 9px，垂直槽、留给垂直槽批），判据在各条上方。
    "src/styles/editorial-studio.css": 50
  // 批次 BD-59（gap 7px 族）：52→51。本文件还 1 处：.desktop-sidebar-stats span
  //   `gap: 7px`→8px（align-items: baseline 的「大数值 + 小标签」串；baseline 族
  //   三票 8px：.card-board-group-head / .stats-words > div / .export-preset-option-head，
  //   6px 那批是 center 图标行不是基线串）。值账：本文件 gap 7px 桶 1→0
  //   （整桶移除）。实拍 01-shell：3 枚 span 各 strong+small、w163/h23.5。
  // 批次 BD-58（gap 3px 族整族清算）：54→52。还 2 处：.desktop-nav `gap: 3px`→4px
  //   （侧栏竖排导航栈；同文件 .desktop-sidebar--rail .desktop-nav 已写 4px 自证、
  //   .settings-nav 4px 同向，对 .project-nav 2px 一票，取多数）、
  //   .stats-rhythm-chart `gap: 3px`→4px（按天柱图列间距，同角色
  //   overview-local.css .overview-daily 4px）。值账：本文件 gap 3px 桶 2→0
  //   （整桶移除——全站 gap 3px 桶随本批清零：4 个文件 12 处）。实拍两枚
  //   （01-nav w191/h444.5、07-stats w667/h144）。判据在各条上方。
  // 批次 BD-57（padding 9px 垂直槽族）：55→54。还 1 处：.desktop-commandbar
  //   @1024 `9px 16px`→10px 16px（commandbar 自己窄屏链的垂直槽回到基档 10，
  //   基档 10px 26px 即直系票；探针二 01 实拍 1000px 窗 computed 9px 16px、h 恒 68
  //   被 min-height 钳住 ⇒ 零像素，A/B 预期 IDENTICAL）。值账：本文件 padding 9px
  //   桶 1→0（整桶移除——全站 padding 9px 桶随本批清零，水平槽 BD-56 + 垂直槽 BD-57
  //   两批还清 9px 全部 22 处）。判据在条上方。
  };

  const actual = {};
  const ledger = [];
  // BD-46 值账：直方图 key =「文件 ⑉ 属性(@apply/工具类另有写法) ⑉ 折算后的 px」，值 = 处数。
  // 为什么不是 BD-45 那种逐声明一行账：这本账在 BD-46 立项时实测 266+5=271 条，逐条钉会把守卫撑成没人读的
  // 文件——间距债的形状是「同一个散写值在一处文件里成簇出现」（立项实测 distinct 文件×属性×值
  // 只有 136 个）。直方图照样抓得住本批实测的那两种漂移（7→15、7→11 都让桶的计数或成员变样），
  // 而「桶的精确组成+每桶计数」与「逐条账」在这两个方向上判据等价；对**换写法不换像素**
  // （7px ↔ 0.4375rem，折算同值）它保持绿——那是本仓认可的零像素合法迁移，逐条带行号的账
  // 反而会把它咬红。行号不进 key 也是同一理由：往文件里加一行不该让后面全部债的 key 漂移。
  const spValMap = new Map();
  const spVal = (rel, prop, px) => {
    const k = `${rel}\t${prop}\t${px}px`;
    spValMap.set(k, (spValMap.get(k) || 0) + 1);
  };
  // 工具类侧的「属性」取类目前的这一段（gap-3 → gap、mx-7 → mx、hover:pt-3 → pt）：
  // 同文件同像素的两个不同属性是两个桶——把 gap: 14 写成 padding: 14 是布局改动，该红。
  // ⚠ BD-46 裁定：key 用**原属性**（padding-top 就是 padding-top），不用「家族归一」
  //   （padding-top/padding-bottom → padding）。CSS 侧探针实测家族版 120 桶、原始版 131 桶
  //   （+TSX 工具类 5 桶 = 立项时本账 136），只差 11 行，但家族版会让「把同一笔 14px 债从
  //   padding-top 挪到 padding-bottom」这种真布局改动保持绿——判据要抓的漂移里，
  //   换方向和换数值同级。代价是简写拆长写（`padding: 14px 14px` 拆成四条边）会红；
  //   那种写法在本仓实测零使用，且它同时改变每桶计数，按 BD-45 语义本来就该走
  //   「有意改动 ⇒ 重 dump 重钉」的流程，不算误伤。
  const spUtilityProp = (whole) => {
    const s = whole.replace(/^[^a-z]+/, "").replace(/^[a-z-]+:/g, "");
    const m = /^(?:gap-x|gap-y|space-x|space-y|px|py|ps|pe|pt|pr|pb|pl|pi|mx|my|ms|me|mt|mr|mb|ml|mi|p|m|gap)/.exec(s);
    return m ? m[0] : s;
  };
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
            spVal(rel, d.prop, px);
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
            spVal(rel, "@apply " + spUtilityProp(m[0]), px);
            ledger.push(`${rel}:${a.source.start.line} @apply …-${m[1]}`);
          }
        }
      });
    } else {
      for (const line of preserveNewlines(src).split("\n")) {
        for (const m of line.matchAll(SP_UTILITY)) {
          const px = spUtilityPx(m[1]);
          if (px !== null && !SP_SCALE_PX.has(px)) {
            n += 1;
            spVal(rel, spUtilityProp(m[0]), px);
          }
        }
      }
    }
    if (n) actual[rel] = n;
  }

  const over = Object.keys(actual).filter((f) => actual[f] > (SPACING_BUDGET[f] ?? 0));
  const stale = Object.keys(SPACING_BUDGET).filter((f) => (SPACING_BUDGET[f] ?? 0) > (actual[f] ?? 0));
  // ── BD-46 值账（直方图版）：手法沿用 BD-45，形状按本账规模裁定。
  // key =「文件 ⑉ 原属性 ⑉ 折算 px ⑉ 处数」，排序后与钉死的账**逐字全等**比对——
  // 每桶计数写进 key，所以桶成员变化和桶内计数变化都红（BD-45 是多重集逐条比对，
  // 这里是压缩后的等价判据：见上面「为什么不是逐声明一行账」）。
  // 唯一重钉入口 DESIGN_TOKEN_DUMP_SPACING_VALUES=1；行数=87、总处数=152 都来自 dump（BD-53 还掉分段岛 3px 族 + chip 行 5px 族 + 页签基础档共 6 处，BD-54 还掉 gap 5px 族 12 处 / 跨 3 文件——chip 内档两笔收 6、11px 图标微标签族四笔收 4、字段堆叠三笔收 4、压缩档灯一笔收 4、动作行一笔收 6、item-main 一笔收 2；styles.css 那条 gap 5px ⑉ 1 是 .migration-report dl 的记档动，见那条上方；BD-55 还掉 padding 14px 左右档族 12 处 / 跨 5 文件——16 档五笔（quick-reference-head、stats-card>h3、writing-margin、reading-stat-tile、@920 commandbar 死声明摘除）、12 档七笔（history-item、history-restore-success、preview-block--letter、scene notice、migration-banner、background-card、inbox-load-more），.desktop-library-grid-head 与 .desktop-titlebar 两条记档不动；BD-56 还掉 padding 9px 水平槽族 11 处 / 跨 4 文件——收 8 七笔（usage li、quick-search、quick-toggle、inbox-save-status、proof-item-rule、proof-ignore、background-detail-kind）、收 10 三笔（assets li、policy padding-left、brand nav-spine-item）、@1024 project-nav button 死声明摘除一笔；BD-57 还掉 padding 9px **垂直槽**族 11 成员 / 12 处离刻度值 / 跨 5 文件——收 8 五笔（cards-type-list-item、history-confirm-name input、card-board-card、cards-board-column-head、cards-board-card，h=10px 行容器族顶槽票 8×20 多数）、收 10 五笔（quick-reference-head 底槽、commandbar @1024、palette-command、palette-footer、migration-error，h=12/16px 直系票）、quick-tabs button 一笔声明还两处值（顶槽 9→8 + 底槽 7→6，故 11 成员 = 12 值）。至此全站 padding 9px 桶清零：9px 五桶尽除、wqr padding 7px 桶 5→4（净 104→99 桶）、总处数 198→186，数字来自 dump；BD-58 还掉 **gap 3px** 族 12 成员 / 12 处 / 跨 4 文件——收 2 六笔（cards-type-list-item > div:first-child、project-card-link-list li > div、global-card-trash-list li > div、global-card-library-heading、creation-proof-item-main、writing-annotation-main；「行/卡内层 strong+说明双行 grid 栈」族，在档票 *-main 与 li > div 全 2px、零 4px）、收 4 六笔（cards-relation-delete、preview-jump、desktop-nav、stats-rhythm-chart、scene-tool-group、cards-board-card；依次为行内图标+文字小控件族、label→控件栈、侧栏竖排导航（rail 态自写 4px）、按天柱图同角色、工具条内联按钮组、live 看板卡双胞胎，全在 4px、零 2px）。至此全站 gap 3px 桶清零：四桶尽除、95 桶 / 174 处，数字来自 dump；BD-59 还掉 **gap 7px** 族 12 成员 / 12 处 / 跨 5 文件——收 8 七笔（cards-local 附件 ul、cards-local 删除影响双列格、uni-result-title、desktop-sidebar-stats span 基线串、creation-wizard-steps li 圆序号、writing-boundary、migration-error；依次为单列 grid 列表容器、多列网格瓷砖、标题+徽标行、baseline 计量串、序号章+文字、图标+多行说明、图标+多行告警，全在 8px）、收 6 四笔（writing-quick-search 搜索框内图标↔input、writing-project-switcher label→控件单列栈、cards-fields-list dl 行距、migration-report-head 图标+单行字，依次为搜索容器三胞胎 6px、label→控件栈四票 6px、dl 行距两票 6px、center 图标行十八票）、摘死一笔（styles.css @1180 的 .desktop-nav button 整条：四键全被高特异性 .nav-spine-item 压住，逐窗实拍 1360/1150/1100/1000 无一致值）。至此全站 gap 7px 桶清零：五桶尽除、90 桶 / 162 处，数字来自 dump；BD-60 还掉 **gap 14px** 族 9 成员 / 10 处离刻度值 / 跨 3 文件——收 12 六笔（desktop-library-grid-head, desktop-library-row 表头与数据行共用的 9 列行栅格、creation-goals-grid 双列字段栅格（12px 14px→单值 12px，一处声明两值）、scene-conflict-notice 两端对齐告警条、scene-radar 右栏面板根栈、inbox-detail-head 顶部错落双栏头；依次为记录行栅格、表单栅格、告警条、面板根栈、双栏头，全在 12px）、收 16 一笔（creation-form-grid 向导表单根，与同向导 .creation-wizard-steps 16 同档）、收 8 一笔（writing-margin dl 让位给 SceneRadar 自写的 .scene-radar-grid 8px——它一直压着面板自家样式表）、摘死三笔（creation-writing-stack：宿主恒挂 desktop-page-stack 16 与 --active 的 0!important，实测 16/0；styles.css @1180 与 @920 两条 .desktop-sidebar gap 14：逐窗实拍 1150/1100 恒 18、1000/900 恒 12，媒体查询不加特异度压不过 editorial 基础档）。至此全站 gap 14px 桶清零：六桶尽除、87 桶 / 152 处，数字来自 dump）。
  const SPACING_VALUE_LEDGER = [
        "src/features/creation/cards/cards-local.css\tmargin\t14px\t2",
        "src/features/creation/cards/cards-local.css\tmargin\t9px\t2",
        "src/features/creation/cards/cards-local.css\tpadding\t13px\t2",
        "src/features/creation/cards/cards-local.css\tpadding\t3px\t1",
        "src/features/creation/editor/continuous-editor.css\tpadding\t120px\t1",
        "src/features/creation/editor/continuous-editor.css\tpadding\t18px\t1",
        "src/features/creation/editor/scene-radar.css\tmargin-bottom\t14px\t1",
        "src/features/creation/editor/writing-quick-reference.css\tmargin\t9px\t1",
        "src/features/creation/editor/writing-quick-reference.css\tmargin-bottom\t9px\t1",
        "src/features/creation/editor/writing-quick-reference.css\tpadding\t11px\t1",
        "src/features/creation/editor/writing-quick-reference.css\tpadding\t15px\t1",
        "src/features/creation/editor/writing-quick-reference.css\tpadding\t18px\t1",
        "src/features/creation/editor/writing-quick-reference.css\tpadding\t5px\t1",
        "src/features/creation/editor/writing-quick-reference.css\tpadding\t7px\t4",
        "src/features/creation/history/history-local.css\tgap\t22px\t1",
        "src/features/creation/history/history-local.css\tmargin-bottom\t3px\t1",
        "src/features/creation/history/history-local.css\tpadding\t11px\t1",
        "src/features/creation/inbox/components/InboxQuickInput.tsx\tpr\t56px\t1",
        "src/features/creation/preview/preview-local.css\tmargin\t14px\t3",
        "src/features/creation/preview/preview-local.css\tmargin\t18px\t1",
        "src/features/creation/preview/preview-local.css\tmargin\t26px\t1",
        "src/features/creation/preview/preview-local.css\tmargin\t3px\t1",
        "src/features/creation/preview/preview-local.css\tmargin-top\t34px\t1",
        "src/features/creation/preview/preview-local.css\tpadding\t18px\t1",
        "src/features/creation/preview/preview-local.css\tpadding\t22px\t2",
        "src/features/creation/preview/preview-local.css\tpadding\t26px\t1",
        "src/features/creation/preview/preview-local.css\tpadding\t30px\t3",
        "src/features/creation/preview/preview-local.css\tpadding\t34px\t1",
        "src/features/creation/preview/preview-local.css\tpadding\t44px\t1",
        "src/features/creation/preview/preview-local.css\tpadding-top\t26px\t1",
        "src/features/library/ExcerptPicker.tsx\tml\t28px\t1",
        "src/features/library/LibraryPage.tsx\tpl\t36px\t1",
        "src/features/library/reader/components/ReaderSearchOverlay.tsx\tpl\t28px\t1",
        "src/features/library/toc/TocList.tsx\tpr\t28px\t1",
        "src/features/search/search.css\tpadding\t14px\t1",
        "src/features/search/search.css\tpadding\t1px\t1",
        "src/features/search/search.css\tpadding\t5px\t2",
        "src/features/search/search.css\tpadding\t64px\t1",
        "src/styles.css\tgap\t11px\t1",
        "src/styles.css\tgap\t18px\t1",
        "src/styles.css\tgap\t1px\t1",
        "src/styles.css\tgap\t5px\t1",
        "src/styles.css\tgap\t9px\t1",
        "src/styles.css\tmargin\t18px\t2",
        "src/styles.css\tmargin\t28px\t1",
        "src/styles.css\tmargin-bottom\t14px\t1",
        "src/styles.css\tmargin-top\t14px\t1",
        "src/styles.css\tmargin-top\t1px\t1",
        "src/styles.css\tmargin-top\t3px\t1",
        "src/styles.css\tmargin-top\t7px\t1",
        "src/styles.css\tpadding\t11px\t4",
        "src/styles.css\tpadding\t14px\t11",
        "src/styles.css\tpadding\t18px\t2",
        "src/styles.css\tpadding\t1px\t2",
        "src/styles.css\tpadding\t26px\t1",
        "src/styles.css\tpadding\t28px\t1",
        "src/styles.css\tpadding\t3px\t3",
        "src/styles.css\tpadding\t5px\t7",
        "src/styles.css\tpadding\t7px\t3",
        "src/styles.css\tpadding\t96px\t1",
        "src/styles.css\tpadding-left\t18px\t1",
        "src/styles.css\tpadding-left\t7px\t1",
        "src/styles.css\tpadding-right\t18px\t1",
        "src/styles.css\tpadding-right\t7px\t1",
        "src/styles/editorial-studio.css\tgap\t11px\t1",
        "src/styles/editorial-studio.css\tgap\t18px\t2",
        "src/styles/editorial-studio.css\tmargin\t14px\t2",
        "src/styles/editorial-studio.css\tmargin-top\t3px\t2",
        "src/styles/editorial-studio.css\tmargin-top\t7px\t1",
        "src/styles/editorial-studio.css\tpadding\t11px\t1",
        "src/styles/editorial-studio.css\tpadding\t13px\t4",
        "src/styles/editorial-studio.css\tpadding\t14px\t5",
        "src/styles/editorial-studio.css\tpadding\t17px\t1",
        "src/styles/editorial-studio.css\tpadding\t18px\t7",
        "src/styles/editorial-studio.css\tpadding\t22px\t1",
        "src/styles/editorial-studio.css\tpadding\t26px\t3",
        "src/styles/editorial-studio.css\tpadding\t30px\t3",
        "src/styles/editorial-studio.css\tpadding\t36px\t1",
        "src/styles/editorial-studio.css\tpadding\t3px\t3",
        "src/styles/editorial-studio.css\tpadding\t44px\t1",
        "src/styles/editorial-studio.css\tpadding\t50px\t1",
        "src/styles/editorial-studio.css\tpadding\t5px\t1",
        "src/styles/editorial-studio.css\tpadding\t7px\t5",
        "src/styles/editorial-studio.css\tpadding-bottom\t13px\t2",
        "src/styles/editorial-studio.css\tpadding-left\t14px\t1",
        "src/styles/editorial-studio.css\tpadding-left\t44px\t1",
        "src/styles/editorial-studio.css\tpadding-top\t28px\t1"
  ].sort();
  {
    const measuredSp = [...spValMap.entries()]
      .map(([k, c]) => `${k}\t${c}`)
      .sort();
    const spHistTotal = [...spValMap.values()].reduce((a, b) => a + b, 0);
    // 不叫 totalActual：外层那条计数行后面还要声明它，块里同名 const 会踩 TDZ。
    const spCountTotal = Object.values(actual).reduce((a, b) => a + b, 0);
    // 条数闸（BD-45 同款）：直方图总量必须等于计数账总量。哪天新增一条 `n += 1`
    // 忘了配 spVal，那笔债就从值账隐形——这把尺子量的正是它自己。
    if (spHistTotal !== spCountTotal) {
      fail(
        `第 8 步间距值账：计数账认出 ${spCountTotal} 处债，值账只记到 ${spHistTotal} 处——两条口径不同源。` +
          `要么某处 ` + "`n += 1`" + ` 旁边漏了 spVal（那笔债从此在值账上隐形），要么值账多记了不判债的分支。` +
          `先核是哪一侧的分支数错了，别改数字凑平。`
      );
    }
    // 重钉账本的唯一入口：账由实测生成，不许手写。
    if (process.env.DESIGN_TOKEN_DUMP_SPACING_VALUES === "1") {
      console.log(`${TAG} [DESIGN_TOKEN_DUMP_SPACING_VALUES] 间距值账 ${measuredSp.length} 桶 / 共 ${spHistTotal} 处（逐字粘进 SPACING_VALUE_LEDGER）：`);
      for (const k of measuredSp) console.log(`    ${JSON.stringify(k)},`);
    }
    // 空账 = 立项没钉（BD-43 手法：dump 跑完必须粘回来，否则这里红，不静默放过）。
    if (SPACING_VALUE_LEDGER.length === 0 && process.env.DESIGN_TOKEN_DUMP_SPACING_VALUES !== "1") {
      fail(
        `第 8 步间距值账：账本空着，实测 ${measuredSp.length} 桶 / 共 ${spHistTotal} 处——` +
          `钉账入口是 DESIGN_TOKEN_DUMP_SPACING_VALUES=1，跑一遍把输出整块粘进 SPACING_VALUE_LEDGER。` +
          `空账不是「还没开始判」，是判据缝好没钉上，值漂移照样沉默。`
      );
    }
    const pinnedSp = SPACING_VALUE_LEDGER.slice().sort();
    const sMiss = measuredSp.filter((k) => !pinnedSp.includes(k));
    const sStale = pinnedSp.filter((k) => !measuredSp.includes(k));
    if (sMiss.length || sStale.length) {
      const parts = [];
      if (sMiss.length) {
        parts.push(
          `新增 ${sMiss.length} 桶不在账上（现存 ${pinnedSp.length} 桶）：` +
            sMiss.slice(0, 6).map((k) => k.replace(/\t/g, " ⑉ ")).join(" ｜ ")
        );
      }
      if (sStale.length) {
        parts.push(
          `账上 ${sStale.length} 桶已经扫不到：` + sStale.slice(0, 6).map((k) => k.replace(/\t/g, " ⑉ ")).join(" ｜ ")
        );
      }
      fail(
        `第 8 步间距值账与实测不符（${parts.join("；")}）。\n` +
          `  这条的语义是**像素漂移或计数漂移**而不是文件级预算：同一处债换了值（7px→15px）、` +
          `同一桶里多一笔少一笔、属性换了（gap→padding）——文件计数一个字没动，版面却变了。\n` +
          `  改法只有两种：\n` +
          `  · 这是本批有意的设计改动 ⇒ 重跑 DESIGN_TOKEN_DUMP_SPACING_VALUES=1 拿新账整块粘回来，` +
          `并在提交信息里写清是哪一页、版面怎么变（不许顺手「同步一下账」）。\n` +
          `  · 不是有意的 ⇒ 把值改回去。\n` +
          `  ⚠ 别拿「计数没超预算」当理由：这本账就是为了让数量不变的改版也红（同 BD-45 阴影值账）。` +
          `  ⚠ 折算后同像素的换写法（7px ↔ 0.4375rem）不红——那是本仓认可的零像素合法迁移。`
      );
    }
  }
  if (over.length) {
    fail(
      `第 8 步间距刻度：${over.length} 个文件的离刻度间距比冻结预算多——${over.slice(0, 5).map((f) => `${f}(${actual[f]}/${SPACING_BUDGET[f] ?? 0})`).join("、")}。` +
        `间距请用 §5.1 的 13 档（--sp-0…--sp-12 = 0/2/4/6/8/10/12/16/20/24/32/40/48px，` +
          `其中 10px 是批次 BD-25 由用户裁定增补的 --sp-12），` +
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
  console.log(`${TAG} [第 8 步待收敛] 间距离刻度 ${totalActual} 处（${Object.keys(actual).length} 个文件，已按文件冻结预算；值账 ${SPACING_VALUE_LEDGER.length} 桶逐桶相等，BD-46）`);
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
 *    ✔ 批次 BD-20（稿纸批）把那三件一起重算完，本账**清零**（见下方 LH_BUDGET 的注）。
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
 *    ✔ 批次 BD-24（本批）把这 43 处全部换成 leading-[var(--leading-K)]。产物实测该形式
 *      发射的是真声明：.leading-\[var\(--leading-14\)\]{line-height: var(--leading-14)}，
 *      与批次 K 的 shadow-[var(--shadow-N)] 幻影（只产 --tw-shadow-color）不同类，
 *      所以这次还债是**真还**，不是把债搬进判据看不见的地方。代价如实记一句：它不是
 *      把数值改小，而是改配对，**画面上 43/43 处像素都变了**（明细见提交信息）。
 *    ⚠ 刻意**不**把 text-* 具名工具类自带的行高计进这本：产物实测 .text-sm 同时输出
 *    font-size:.875rem 与 line-height:1.25rem（全仓 196 处 / 45 文件挂着具名 text-*），
 *    但那行 rem 行高是工具类的附属产物、不是作者写的数值——要消掉它只能改字号选择本身，
 *    而字号已经由①这本管着了。同一处挂两本账会让「还一笔债」变成两次改动的耦合，
 *    也违反判据不重复计数的老规矩（同批次 AW 对 var(--radius-*) 的处理）。
 *
 * ③b BD-24 还债时补上的盲区（这条是本批的全部技术风险所在）：
 *    旧的 LH_UTILITY 只匹配 `leading-` 后面紧跟数字的形态，所以任意值方括号形式
 *    ——leading-[24px]、leading-[1.5rem]、leading-[var(--leading-14)]——**一个都不数**。
 *    立项时实测「CSS 侧 0 处」靠的是 postcss 读声明值，而 TSX 侧数的是 leading-N；
 *    于是还完这 43 处之后，债一旦以 leading-[24px] 的形态重新出现，判据会当场瞎掉：
 *    这正是批次 L 记过的那类失效（「债从 CSS 声明搬进令牌定义后判据就瞎了」）。
 *    所以本批同时补下四条判据，缺一即等于把债搬进口袋：
 *    · LH_UTILITY 扩到方括号形态，带长度单位的任意值照计 LHL（新增即红）；
 *    · leading-[var(--leading-K)] 是唯一豁免形态，但**不白免**——同行扫到唯一的具名
 *      text-* 时校验 K 与 §3.2 同名档（12↔1.55 等），不配即红。这条校验刻意只在
 *      「同行恰好一个字号工具类」时生效：本仓字号大量走继承（实测 43 处里 41 处同行
 *      有唯一 text-*，InboxItemDetail:174 与 ui.tsx:184 那 2 处走继承链，已由 Electron
 *      产物探针实测各自 12/14），多字号与纯继承一律不猜——猜错就是把正常写法咬成债，
 *      同②「不判配对」的老规矩。⚠ 诚实记下反向漏洞：拿 `leading-[var(--leading-16)]`
 *      挂在 text-sm 上避开长度式这本账，只要同行有具名 text-* 就会被配对校验咬住；
 *      但字号纯走继承时可绕开——这条豁免的代价留在此处，不假装它不存在。
 *    · 纯数字任意值 leading-[1.7] 不计数、但红：它就是 §3.2 禁止的那件事换了个写法，
 *      放过去等于给「无单位但不走令牌」开后门（八档同名值已经在令牌里，没有理由散写）。
 *    · 方括号令牌形态的 K 不在 --leading-* 定义集内即红（未定义 var 浏览器静默回退，
 *      与 D-2 那族同形态），并且「非 var 方括号形态」的写法在实仓当前为 0 处——
 *      所以这条不需要预算，新增即红。
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
  const LH_UTILITY = /(?:^|[\s"'`{,(])(?:[a-z-]+:)*leading-(?:([0-9][\d.]*|\[[^\]]*\]))/g;

  // BD-24：§3.2 的同名配对表（字号档 → 该档行高）。左边的键就是 --leading-* 的命名，
  // 所以「K 与字号同不同名」是文本扫描能可靠判的事，而「继承链上字号是多少」不能——
  // 后者留给探针，不在这条判据里猜。
  const LH_PAIR = { 11: "1.5", 12: "1.55", 13: "1.6", 14: "1.7", 16: "1.45", 20: "1.3", 24: "1.25", 32: "1.1" };
  // 同行唯一的具名/任意值字号工具类才拿来验配对；0 个（纯继承）与 ≥2 个（多档串）一律返回 null
  const lineFsPx = (text) => {
    const sizes = [...text.matchAll(FS_UTILITY)]
      .map((m) => (m[1].startsWith("[") ? parseFloat(m[1].slice(1, -1)) : TW_FS[m[1]]))
      .filter((v) => v !== undefined);
    return sizes.length === 1 ? sizes[0] : null;
  };
  // 返回 true = 已就地判完（豁免或红），false = 交给调用方按 LHL 计数。
  const leadingForm = (rel, ln, raw, text, where) => {
    if (!raw.startsWith("[")) return false; // leading-N：长度式，照计
    const body = raw.slice(1, -1);
    const tk = /^var\(--leading-(\d+(?:\.\d+)?)\)$/.exec(body);
    if (tk) {
      const k = tk[1];
      if (!(k in LH_PAIR)) {
        fail(`第 8 步行高长度式：${rel}:${ln} ${where}leading-[var(--leading-${k})] 里的 ${k} 不是 §3.2 的八档之一（tokens.css 无此定义 → 浏览器静默回退到继承行高，看着走了令牌其实没走，同 D-2 那族形态）。`);
        return true;
      }
      const fs = lineFsPx(text);
      if (fs !== null && String(fs) !== k) {
        fail(`第 8 步行高长度式：${rel}:${ln} ${where}leading-[var(--leading-${k})] 与同行字号 text-${fs}px 不同名（§3.2 给的是同名配对，12↔1.55、14↔1.7…）。要换档就整对换，别只动一半。`);
        return true;
      }
      return true; // 同名（或字号走继承、本判据不猜）：合法形态，不计数
    }
    if (/^[\d.]+$/.test(body)) {
      fail(`第 8 步行高长度式：${rel}:${ln} ${where}leading-[${body}] 是散写的无单位比率——§3.2 的八档同名值已经在 tokens.css 里，走 --leading-*，别在类名里再抄一份字面量（否则这八档的单一真相就散了）。`);
      return true;
    }
    return false; // 带长度单位（px/rem/em/%/pt）与未知形态：按 LHL 计债，保守方向是债留在账上
  };

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
  //                                   12px !important（editorial-studio.css:3641，.creation-writing-page--active
  //                                   .project-nav button:not(.project-nav-back)），
  //                                   紧邻的同族返回键 .project-nav-back 也是 12
  //                                   （styles.css:3943）。导航项彼此必须同档，
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
  //   .writing-manuscript-head h2  22→20   主题表 styles.css:1320 对**完全同一个选择器**
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
  //   .overview-hero-title h2  22（overview-local.css:26 那条规则）与
  //   .desktop-page-hero h2    clamp(20px, 1.8vw, 23px)（editorial-studio.css:2930）
  //   命中的是**同一个 h2**（OverviewPage 的 section 同时挂 desktop-page-hero 与
  //   overview-hero-title）。这不是「22 离刻度、该去 20 还是 24」，而是**流式标题 vs
  //   页面写死标题**的架构选择：收 20 就等于把 hero 标题改成固定值、让那条 clamp
  //   在概览页失效；改 clamp 的中值又与「§3.2 是像素档位表」冲突。两种答案对应两种
  //   设计意图，判据不能替人挑——留给用户（或与 §4 page-header 20px 一起整族定）。
  //   ✔ 后续：用户已授权由我拍板，**批次 BD-17 选了流式那一侧**（删掉局部那条
  //   font-size: 22px，把字号交回 :2839 的 clamp），这笔账本批已清，见下方 BD-17 注。
  //   另注：注入脚本「反向③」那颗牙钉的正是 editorial-studio.css 的
  //   clamp(22px, 2vw, 27px)（:629，另一处 clamp，不是本批任何一条），本批一个字没碰。
  // 本批同样不动棘轮牙锚点：字号③/行高比率②钉稿纸 17px+lh:2（:1248，未动，改后逐字
  // 验证仍唯一命中）；行高比率①钉 history-local 的 11px+lh1.5（未动）。
  //
  // 批次 BD-11：再从 35 里还掉 2 处，口径是 BD-7 注释里挂的那笔跨页许诺——「面板头」
  // 这一档一次定完。收的 2 处都是 18px，走的是**已定判据**（不是本批新发明的答案）：
  //   .writing-quick-reference-head h3  18→16  BD-3 已对同角色规则定档：
  //                                   .cards-detail-head h3（styles.css:2322）与
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
  //   .desktop-brand-mark  18 ×2（editorial-studio.css:137 基础 + styles.css:4267
  //                                   @media≤920）它是标题栏那颗 38×38 印章里的
  //                                   「洛」字（font-serif + font-weight:700 + 固定
  //                                   宽高 + place-items:center）。字号是**为那个盒子
  //                                   选的**，不是标题阶梯上的一级：套 §3.2 的 16/20
  //                                   分别会让字撑不满盒子或溢出盒子。两条规则
  //                                   （基础与窄屏）同值也是「字形尺寸」而非「压缩档」
  //                                   的证据（对比 BD-9 的 project-nav：那里窄屏比基础
  //                                   低一档；这里窄屏与基础同值）。归品牌族单独定。
  //                                   ✔ 后续：批次 BD-23 品牌字形批按真机量兑现——那句
  //                                   「20 会溢出盒子」实测是假的（20 的着墨 18×21，环内
  //                                   净空 30×30），两处 18 同值收回八档 20；上面那两个
  //                                   :NNN 是 BD-11 写下当时的行号，按规矩不回写，
  //                                   当前行号以 `--show-type-ledger` 为准。
  //                                   ⚠ 另订正一处**事实**错误（行号可以不回写，事实不行）：
  //                                   印章里那个字是「阅」不是「洛」——DesktopFrame.tsx:122
  //                                   `<span className="desktop-brand-mark">阅</span>`，
  //                                   BD-11 写下当时也是同一个字（同一文件同一行，git 可查）。
  //                                   它不影响任何判据，但下一批人会照着「洛」去找字。
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
  // 本批不动棘轮牙锚点：字号③与行高比率②钉的稿纸 17px/lh:2（editorial-studio.css:1278）
  // 没动；replace.css 里那颗第 7 步的牙钉的是 .replace-hit-before 的 line-through
  // 那两行（inject:169 needle），与本批 :32 不同一条，改前改后逐字验证仍唯一命中。
  //   ⚠ 上面这三处行号引用**在本批之后的批次里已各自失效**，批次 BD-23 复扫时逐条核实：
  //   稿纸 :1278 的 17px/lh:2 已被 BD-20 三件同算还掉（16/1.85/29.6px）——行高比率②随
  //   LH_BUDGET 清零退役，字号③先搬 .desktop-brand-mark 18px、又随 BD-23 搬到 hero
  //   11.5px；`.replace-hit-before` 那颗牙现在在 inject **第 178 行**（当时是 169）；
  //   「与本批 :32 不同一条」指的是 BD-17 当时备份清单里的圆角棘轮 needle，那份
  //   清单早已随 BD-19/BD-21 的宿主变迁重排。按 BD-17 自己的规矩不回写历史批次的数字，
  //   只在这里声明它过期——否则读者拿 :169 去找 line-through 那颗牙，找到的是无关条目。
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
  //
  // 批次 BD-14：再从 26 里还掉 2 处，口径回到 BD-5/BD-8 的**同页同角色邻居**，
  // 这一页是写作页右边的信息栏（.writing-margin \*，宿主 WritingDeskMargin.tsx）。
  // 选它的理由是：这两处的邻居**不是**跨页先例，而是同一块信息栏里并排写着的
  // 「雷达面板」那一份——同一个 aside 的两个 tab，一份离刻度、一份在刻度上：
  //   .writing-margin dt   11.5→11   信息栏的字段**标签**（dt），同一条规则里
  //                                   值（.writing-margin dd）是 12px mono+tabular。
  //                                   对照 scene-radar.css 那对**同名结构**：
  //                                   .scene-radar-block dt=11 / dd=12
  //                                   （:77/:104，宿主 SceneRadar.tsx 的 <dt><dd>），
  //                                   还有 .scene-radar-item dt=11 / dd=13。
  //                                   ⚠ 关键一条：本条规则**上面那行英文注释**原本
  //                                   就写着 "labels 11.5px sans, values tabular mono"
  //                                   ——作者知道自己写的是半档；本批把注释里的
  //                                   11.5px 一起改成 11px，不留旧数字当假证据。
  //   .writing-margin-tabs
  //     button:not(.writing-margin-collapse)  11.5→11  它是页签（role="tablist" 里的
  //                                   tab），基础档 .writing-margin-tabs button 就是
  //                                   12px（scene-radar.css:20，同一条链上更早的规则），
  //                                   这条 :not() 只是**压缩档**（padding 从 5px 8px
  //                                   缩到 4px 6px + nowrap + ellipsis）。语义与 BD-9
  //                                   的 project-nav 窄屏档完全一样：压缩档 = 比基础
  //                                   档低一档 = §3.2 的 11。同族旁证：写作页另一个
  //                                   页签条 .writing-quick-tabs button 在 BD-7 已定 11。
  // 页内改后单调性：14(h3 标题) > 13(展开钮) > 12(值 dd·tab 基础档·收起钮) > 11(标签 dt
  // ·压缩页签)。信息栏里「标签 < 值」这个关系从此成立，原先 11.5 与 12 只差半档、
  // 标签和值实际糊在同一级。
  // ⚠ editorial-studio.css 里原本有 **4** 处 11.5px（:261/:1276/:2996/:3455），本批只还
  // 其中证据同向的 2 处，**另 2 处刻意不还**，理由各不相同：
  //   .desktop-sidebar-stats small  11.5（:261）——它是侧栏底部「N 项目 / N 待办」
  //                                   计数下面的**说明文字**（DesktopFrame.tsx:155-163
  //                                   里 <strong>数字</strong><small>标签</small>）。
  //                                   两处同角色邻居给出**相反**的答案：
  //                                     · .reading-stat-head = 12（ReadingStatsPage
  //                                       统计格的标签，BD-3 定的，同为「大数字下面/
  //                                       上面的那行标签」）
  //                                     · .desktop-dock-label / .desktop-nav small /
  //                                       .desktop-card-label = 11（侧栏族自己的小字）
  //                                   它挂在侧栏（支持 11）却是统计标签（支持 12），
  //                                   两条同角色证据方向相反、且差整档——这是判据
  //                                   该停手的地方，不是该挑边的地方。留给用户，
  //                                   或与侧栏 dock 族一起整族定（§4.1 第 1 条）。
  //   .creation-writing-page--active
  //     .creation-writing-hero
  //     .desktop-page-actions button  11.5px **!important**（:3455）——它属于写作页
  //                                   接管态那族 !important 容器规则（同一族还挂着
  //                                   height: 28px !important），也就是第 6 步
  //                                   「遗留按钮容器规则 12 条」与 §2.5 按钮投影
  //                                   冻结那两本账上的成员。这族的收法是**随页面
  //                                   <Button> 迁移整条删除**，不是把半档挪到 11——
  //                                   在这里改字号只会让一条本该消失的规则看起来
  //                                   「已合规」，反而把迁移的线索抹掉。故本批不碰，
  //                                   与阴影 BE/LEGACY 那批一起做。
  //
  // 批次 BD-15：再从 24 里还掉 3 处（styles.css 12→9），口径是**同页角色 +
  // 已定档的同族邻居**，不用「就近」——11.5/12.5 这些值在两侧等距，就近选不出方向：
  //   .scene-planning-grid label  11.5→12  场景任务卡里的**表单字段标签**
  //                                   （「场景摘要」「场景状态」，OutlinePage.tsx:66-73）。
  //                                   §3.2 用途列 12 =「标签、辅助说明」直接点名；
  //                                   同族旁证 .cards-field-label=12、
  //                                   .cards-field-row dt=12 / dd=13。⚠ 决定性的
  //                                   一条是**相对关系**：这个表单的输入框用的就是
  //                                   .cards-input(13)，标签必须比它低一档才成层级——
  //                                   BD-7 在快查面板定下的正是同一条规矩
  //                                   （.writing-quick-field > span=11 / input=12）。
  //                                   11.5 让标签与输入只差 1.5px，实际糊在一起。
  //   .writing-annotation-empty  11.5→12  批注列表为空时那**一句说明**
  //                                   （WritingDeskMargin.tsx:518-520）。§3.2 用途列
  //                                   12 =「辅助说明」；同页邻居 .writing-annotation
  //                                   -note=12、-text=12、-meta=11、-relocate=11，
  //                                   它是整句说明、不是计数，落 12。
  //                                   ⚠ 这条是 BD-4 明文记下的**配对判据反例**
  //                                   （11.5 + lh 1.6 按配对法该去 13，差 1.5px 超出
  //                                   半档所以当时不收）。本批收它靠整页角色 + 用途列，
  //                                   和 BD-8 收 scene-candidate 那两处同一口径：
  //                                   两条判据给的答案不同（13 vs 12）、依据也不同，
  //                                   别把这条读成「配对法后来也认 12」。
  //   .outline-page-side h3  15→16  侧栏「场景任务卡」的面板头（OutlinePage.tsx:351，
  //                                   上面还有 .desktop-card-label=11 的眉标）。
  //                                   §3.2 用途列里 14 是「面板正文·设置项描述」
  //                                   （正文角色），16 才是「小节标题 h4」；本条的
  //                                   角色是标题。旁证 4 条同角色声明已在 16：
  //                                   .cards-detail-head h3、.background-detail-head
  //                                   h3（BD-3 的 17→16），.preview-toolbar-meta h2
  //                                   （BD-11 的 18→16）与 .preview-empty/failure h2。
  //                                   ⚠ 诚实记一笔反面证据：.writing-margin h3 是 14
  //                                   （BD-14 刚确认过）。也就是本仓面板头目前同时
  //                                   存在 14/15/16 三种值。本批按「用途列把标题归
  //                                   16」+「16 旁证 4 条、14 只有 1 条」收这一处；
  //                                   **面板头整族统一成 14 还是 16 是族问题**，
  //                                   不由我在相邻两批里各挑一次边——那条 14 在刻度上
  //                                   （不欠债，判据管不到它），要改只能整族一起改，
  //                                   连同下面刻意不还的 15 一族归给「标题阶梯」那一批。
  // ⚠ 本批刻意不还的 15px：styles.css 3 处（.creation-step legend:972、
  //   .creation-search-input:2772、.stats-words dd:2869）+ editorial-studio.css 2 处
  //   （.stats-card h3 与 .background-page-title 是**同一条规则** :750，动一处动两家；
  //   .library-cover-glyph:1826 是字形不是标题）+ preview-local.css 2 处
  //   （.preview-chapter-heading:153、.preview-block:178——§3.3 给正文的只有 17 与 16，
  //   书页预览该按阅读器档还是按稿纸档是设计选择）。这些跨「标题阶梯」与「控件正文」
  //   两族，单条收会把族打散，逐条就近又只在等距处失效——按族一起定。
  // 本批不动棘轮牙：170 条 needle 逐条复验，非唯一的只有本批之前就已存在的
  // `.project-workbench {\n`（文件里 3 次，靠首次替换定位）；稿纸 :1248 的
  // 17px+lh:2 与反向③的 clamp(22px, 2vw, 27px) 一个字没动。
  //
  // 批次 BD-16：再从 21 里还掉 2 处，两处是**同一道题**——「一行键值对里那个键」：
  //   .stats-words dt           12.5→12  统计卡里「今日 / 本周 / 累计」这些**行标签**
  //   .overview-status-name     12.5→12  章节状态分布条里每行的**状态名**
  // 判据是同一条，两条都成立才收：
  //   ① §3.2 用途列 12 =「标签、辅助说明、**表格次级列**」，这两处都是行标签；
  //   ② 同族**并排的键**全在 12：.cards-field-row dt=12 / dd=13（同是 <dl> 键值行）、
  //      .reading-stat-head=12、.stats-note=12、.overview-recent-chapter=12、
  //      .overview-goal-numbers em=12；.overview-status-name 的**同行兄弟**
  //      .overview-status-count 本来就是 12——同一行里「名字 12.5 + 数字 12」两种高度。
  // 值的另一侧跟着各自页面的既有阶梯走，本批**没有**一起动：.stats-words dd=15（欠债、
  // 归 15 一族）、.overview-status-bar / -count 不动。
  // ⚠ 这两处都是**当前胜者**（对过字号胜者图），所以是真实的 0.5px 视觉变化。
  // ⚠ 剩下的 19 处里 12.5px 还有 **6** 处，但都**不是**「行标签」这道题，拆开收会打散族：
  //   .migration-note(:3351)        整句说明（配对法 →14 差 1.5px 不收；按角色 →12 又
  //                                 要连 -error(13)/-head(14)/-failures(12) 一起重排
  //                                 迁移弹窗阶梯）→ 归「弹窗正文」整族定。
  //   .migration-report dl > div(:3399)  同一弹窗的明细行，与上一条互相牵连。
  //   .import-volume-title(:3519)   卷标题（12.5 + weight 700，同页 li=12 / em=11），
  //                                 它是**标题**不是标签，得按标题阶梯那批一起定。
  //   .background-detail-fields > div(:4106)  背景详情行，dt/dd 都从这条继承，
  //                                 改它等于同时改两家，同 :750 那条一个道理。
  //   .desktop-search-command span(:564) / .desktop-page-actions button +
  //   .desktop-home-inbox(:698)     **控件文字**：§3.2 说 13 =「控件与界面正文（默认）」，
  //                                 可同族控件现在分着 11.5/12/12.5/13 四种，且 :698
  //                                 就挂在 §2.5 冻结按钮族的规则上——「控件归 13」必须
  //                                 整族收，和 hero !important 族一起处理才不自相矛盾。
  //   ✔ 后续：上面这六处 12.5px 已随**批次 BD-17** 整族收口（dl 行→12、说明句→13、
  //   命令面板行→13、工具栏按钮→12），只有 hero 接管态那条 11.5px !important 仍按
  //   本注释的说法留给 LEGACY 迁移批。⚠ 这批注释里的 :NNNN 是**写下当时**的行号，
  //   BD-17 往同文件插了多行说明后它们会漂；活引用（CSS 注释里指向当前胜者的那些）
  //   已逐条按实测重编，历史注释不回写——回写就看不出当时盘点了什么。
  // 本批同样不动棘轮牙锚点：稿纸 :1248 的 17px+lh:2、反向③ clamp(22px, 2vw, 27px)、
  // 行高比率① history-local 的 11px+lh1.5 逐字复验均未动；170 条 needle 复验，
  // 非唯一者仍只有先前就存在的 `.project-workbench {\n`（文件里 3 次）。
  // 本批不动棘轮牙：editorial-studio.css 里 47 条牙 needle 逐条复验，钉的是
  // .cards-toolbar/.history-page/.inbox-detail-head/.motion-toast/.desktop-panel-card/
  // .outline-scene/稿纸 17px+lh:2/clamp(22px,2vw,27px)/hero !important 族——
  // 没有一条含 writing-margin；稿纸那条 :1248 逐字复验仍是唯一命中。
  // 批次 BD-17：再从 19 里还掉 14 处（→ 5），这一批是 BD-11/BD-16 注释里挂着的
  // **两族一次性定完**——「标题/面板头阶梯」与「控件·键值行正文」。用户已授权由我拍板
  // （原文：把「标题阶梯 + 阴影分级」这两族的设计侧由我定并继续），所以本批的
  // 判据优先级在这里写死，后面同族的新增债照这个次序核：
  //   ① 作者自己的另一处声明（同一角色/同一元素的第二种写法）
  //   ② 同角色邻居已在刻度上（胜者图实测，不是注释里的许诺）
  //   ③ 压缩档 = 基础档低一档（等距值只能靠它）
  //   ④ §3.2 用途列直接指认
  //   ⑤ 同行/同列的相对结构（键值行里的值不能大于键、标题不能小于自己的正文）
  // 严格「最近档」在 x.5 处失效（两侧等距），本批 12.5/11.5 全靠 ①②⑤ 投票。
  //
  // 【标题阶梯 → 16】§3.2 给 16 那行的用途是「小节标题 h4」，本仓面板头的胜者
  //   早已全是 16（.cards-detail-head h3 / .background-detail-head h3 /
  //   .preview-toolbar-meta h2 / .writing-quick-reference-head h3 /
  //   .replace-panel-header h2 / .outline-page-side h3，BD-3/11/15 收），
  //   剩这几处是漏网的：
  //   .stats-card h3 + .background-page-title  15→16  **一条规则两个宿主**（:765），
  //                                   概览/统计页卡片头 + 背景设定页标题。
  //                                   ⚠ 附带收口一个陈旧事实：.background-page-title
  //                                   在 styles.css:4072 自己就写着 16px，被这条
  //                                   后置主题表的 15 长期压着——改后两表同值。
  //   .creation-step legend           15→16  <fieldset><legend> 是分组小节标题；
  //                                   本页 16 > 14(template strong) > 12(note/li/span)
  //                                   与改前 15 > 14 > 12 同构，只是档位归位。
  //   .preview-chapter-heading        15→16  判据是 ⑤：本页书页正文 .preview-block
  //                                   本批落到 16，章节题若收 §3.2 给 h3 的 20 就
  //                                   **大于本页标题** .preview-toolbar-meta h2(16)，
  //                                   收 14 又**小于自己的正文**——两头都不行，
  //                                   只有 16 与现状的层级关系同构（同尺寸、靠
  //                                   font-family 与颜色分层）。
  // 【书页正文 → 16】
  //   .preview-block                  15→16  §3.3 给正文只有 17 与 16 两个值，没有 15；
  //                                   17 属于「阅读器正文」那行，而 17 与「15–22」是
  //                                   **用户设置**的取值区间、经 inline style 落地
  //                                   （守卫说明①已把这条讲死：不能给 CSS 字面量发证）。
  //                                   本页是自己书稿的只读通读/打印版式（页头明写
  //                                   「正文改动请回到写作台」），取稿纸那行的 16。
  //                                   ⚠ 行高 1.9 一个字没动：它在合法集里（§3.2∪§3.3，
  //                                   LH_ALLOWED），而「16 配 1.85 还是 1.9」是行高
  //                                   配对的另一道题，改了动整页行距——留给稿纸批。
  //                                   ✔ 后续：批次 BD-20 收了它，取 1.85。判据是本批
  //                                   上面自己写的那句「本页内容是稿纸文本，不是 EPUB/TXT
  //                                   阅读器」（优先级 ①：作者自己的另一处声明）——既然
  //                                   角色是稿纸，§3.3 就整行取稿纸那行，不能字号取一行、
  //                                   行高留另一行。实测代价 −2.6% 页高，打印分页下限
  //                                   （验收写死 ≥2 页/章）留着一倍余量，不构成风险。
  // 【控件与键值行正文 → 12/13/14】
  //   .desktop-page-actions button
  //     + .desktop-home-inbox         12.5→12  判据是 ①：同一批按钮在写作页接管态里
  //                                   作者自己写的就是 12px（本文件
  //                                   .creation-writing-hero .desktop-page-actions
  //                                   button，12 + 32px 高）。胜者图里 18 条带 px 值的
  //                                   按钮类名分支，12px 占 10 条，是最大簇。
  //                                   ⚠ .desktop-home-inbox 在 TSX 里已无使用者（只剩
  //                                   -count=11 活着），幻影判据按「整条规则有活分支」
  //                                   放行，所以这里照改；孤儿整族清理仍是另一笔账。
  //   .desktop-search-command span    12.5→13  判据是 ①+②：它是「按 Ctrl K 打开的
  //                                   可点行（label + kbd）」，孪生兄弟
  //                                   .creation-palette-command 在 BD-6 已收 13，
  //                                   两个搜索面板的行长长期 12.5 vs 13 两种高度；
  //                                   §3.2 的 13 用途正是「控件与界面正文（默认）」。
  //   .creation-search-input          15→14  判据是 ②：同角色（对话框顶部搜索框）
  //                                   的 .uni-search-input 已在 14（BD-3 收）。
  //                                   ⚠ 不取 13：本对话框结果区 .creation-search-state
  //                                   是 13，输入框收到 13 就和自己的结果列表同尺寸，
  //                                   是现状（输入 > 结果）的层级倒退。
  //   .migration-note                 12.5→13  判据是 ②：同弹窗里 .migration-banner 13、
  //                                   .migration-error 13——三段正文两种尺寸。
  //                                   该条同时服务 ExportDraftDialog 的说明行。
  //   .migration-report dl > div      12.5→12  §3.2 明写 12 =「标签、辅助说明、
  //                                   **表格次级列**」；本条是 <dl> 键值行的行字号
  //                                   （dt+dd 都从它继承）。
  //   .background-detail-fields > div 12.5→12  同上，同族 dl 行；本页 16 > 14 > 12 > 11
  //                                   一档不差。
  //   .import-volume-title            12.5→12  同卡邻居 li=12 / em=11 / toggle=12，
  //                                   全卡只有它半档；卷名文字本体在 .paper-input
  //                                   （0.875rem）里，本条管这一行的档位与 700 字重。
  //   .stats-words dd                 15→12  判据是 ⑤：与同行的 dt=12 baseline 对齐，
  //                                   一行字两种尺寸；同族 dl 值全在 12/13
  //                                   （.writing-margin dd 12、.cards-field-row dd 13、
  //                                   .import-volume li 12）。强调交给
  //                                   700 字重 + tabular-nums + 墨色。
  //                                   ⚠ 为什么不套用 BD-10 的「统计大数字 = 20」：
  //                                   那三处（.reading-stat-value / .stats-streak /
  //                                   .desktop-sidebar-stats strong）都是**独立成块的
  //                                   大数字**，这里是密集小表的一行值；抬到 20 会把
  //                                   四行卡片撑成另一种东西，而大数字档在同卡另有其人
  //                                   （.stats-streak=20）。
  //   .desktop-sidebar-stats small    11.5→11  判据是 ②：侧栏整族的标签全是 11
  //                                   （.desktop-brand small / .desktop-nav small /
  //                                   .desktop-card-label / .desktop-dock-label），
  //                                   §3.2 也给 11 =「角标、计数、时间戳」；
  //                                   11.5 是这块岛上唯一的半档值。
  // 页内结果（本批改动处）：概览/背景 16(标题) > 12(行)；向导 16(legend) > 14 > 12；
  // 通读页 16(标题) = 16(正文，靠字族分层) > 12 > 11；命令面板 13 = 13；
  // 侧栏 20(数字) > 11(标签)；工具栏按钮 12 = 12（接管态与非接管态同档）。
  //
  // ⚠ **本批刻意不还的 5 处，每一处都已经有名字、不是「还没想好」**：
  //   .scene-editor-content 17（:1278）  稿纸批。17×line-height:2 = 34px 恰好是
  //                                   background 里那条横线的节距，字号/行高/节距
  //                                   三件必须一起重算；棘轮牙「字号③」「行高比率②」
  //                                   钉的就是这六行，本批一个字没碰（改前改后逐字
  //                                   复验仍唯一命中）。
  //                                   ✔ 后续：批次 BD-20 三件同算落地（16 / 1.85 /
  //                                   29.6px），本条债清、editorial 预算 4→3；两颗牙
  //                                   同批搬家——字号③改钉本文件 .desktop-brand-mark
  //                                   的 18px（下一笔最慢还），行高比率②随 LH_BUDGET
  //                                   清零退役并补三颗「写回必须红」。
  //   hero 接管态 11.5px !important（:3485）  LEGACY 族。§2.5 那 3 条冻结按钮投影 +
  //                                   第 6 步 12 条容器接管规则一起随「写作页迁移批」
  //                                   删掉；给它换个刻度值等于让一条注定要删的规则
  //                                   看起来已合规，是把债洗成账面干净。
  //   .desktop-brand-mark 18 ×2（editorial:137 基础 + styles.css:4267 @media≤920）
  //                                   品牌字形族，BD-11 已立案：38×38 印章里的「阅」
  //                                   字，字号是为那个盒子选的；基础档与窄屏档**同值**
  //                                   正是「字形尺寸」而非「压缩档」的证据
  //                                   （对比 BD-9 的 project-nav：窄屏低一档）。
  //                                   ✔ 后续：批次 BD-23 兑现（18→20 ×2，「溢出盒子」
  //                                   实测为假），:137/:4267 是本表写下当时的行号，不回写。
  //   .library-cover-glyph 15（:1856）  同族：38×50 书脊块里的白字，且带作者明写的
  //                                   「这条必须留着，而且是活的」。
  //                                   ✔ 后续：批次 BD-23 兑现（15→16）——判据是⑤同行
  //                                   邻居书名 text-base=16，不是「同族都收 16」；
  //                                   BD-17 自己对另外 5 处 15px 的拆分是 4→16 / 1→14 /
  //                                   1→12，这条的邻居把它投进了 16 那一族。:1856 同上，
  //                                   不回写行号。
  //                                   ⚠ 后两处不是「懒得动」——把字形尺寸套进文本
  //                                   阶梯会真的撑破/填不满盒子。它们要么随品牌批
  //                                   一起定一个显式的字形档位约定，要么继续记账。
  // 本批不动的其它棘轮牙：行高比率①（history-local 的 11px+lh1.5，未动该文件）、
  // 反向③（clamp(22px, 2vw, 27px)，未动）、AH/AO/AS/AU/N/Z 那批钉在
  // .creation-step {\n  margin: 0; 的锚点（改后复验仍唯一命中）；
  // 171 条 needle 逐条复验，非唯一者仍只有先前就存在的 `.project-workbench {`（3 次）。
  // ⚠ 本批插入的多行注释推移了行号，全仓所有 `xxx.css:NNNN` 形式的引用（19 处）
  // 已用 difflib 逐条重映射核对；其中 styles.css 那段品牌注释里的 :118/:2832 是
  // **本批之前就已过期**的旧引用，一并修正为实测的 :120/:2890 并在注释里声明。
  // 批次 BD-26：本表清零 → 空表对象。表体那条逐批搬迁史注释原样保留在下面。
  const FS_BUDGET = {};
    // 批次 BD-23（品牌字形批）：styles.css 那条是 @media≤920 的 .desktop-brand-mark 18px，
    // 与基础档同值一起收到 20，条目清零后**删除**（空表 = 新增即红，比留一个 0 更诚实）。
    // 批次 BD-20（稿纸批）：4→3。还掉的正是 BD-17 那张「刻意不还」表里排在第一位的
    // .scene-editor-content 17px——它当时被三件耦合冻住，本批把 字号/行高/节距 一起
    // 重算（16 / 1.85 / 29.6px），债当场清。剩下 3 处仍是 BD-17 点名的两族：
    // .desktop-brand-mark 18（品牌字形）+ .library-cover-glyph 15（书脊字形）+
    // hero 接管态 11.5px !important（LEGACY 族，随写作页迁移整族删）。
    // ⚠ 那张表里的 :NNNN 是 BD-17 写下当时的行号，按该批自己的规矩不回写；
    //   当前行号唯一权威是 `node scripts/verify-design-tokens.mjs --show-type-ledger`。
    // 批次 BD-23：3→1。品牌字形族那 2 处（18 + 15）按真机量收回八档（→20、→16），
    // 剩下的 1 处是 hero 接管态 11.5px !important——它属于第 6 步那 12 条容器接管规则，
    // 随「写作页迁移批」整族删除；给它换个刻度值等于把注定要删的规则洗成账面合规。
    // ✔ 批次 BD-26（写作页迁移批）：上面那句许诺兑现，本表**清零并移除条目**（照
    //   LH_BUDGET / LHL_BUDGET 的写法留空表 = 新增即红）。还法是关键：11.5 不是被
    //   改成 12 或 11「收敛到刻度」，而是那条规则连同 !important 整条不存在了，
    //   外观由 <Button size="sm"> 的 §2.2 档接管（28px / 12px / 10px）。
    //   ⚠ 清零后 stale 分支（还了债却不降预算）对字号**不再可达**：没有条目，actual 不可能
    //     小于一个不存在的预算。钉在它上面的那颗牙「排版棘轮·字号③」因此**退役**，
    //     不是搬家——本账已经没有「活预算文件」可搬（照批次 AV 圆角、AS 幻影预算、
    //     BD-20 行高比率② 的规矩，留字在此 + inject 里留字，别让下一批人以为那条判据坏了）。
    //     这颗牙的历史就是本账的搬迁史：BD-6 从 .creation-palette-command 13.5px 搬到
    //     稿纸 .scene-editor-content 17px，BD-20 搬到 .desktop-brand-mark 18px，BD-23 随
    //     18→20 再搬到 hero 11.5px !important，本批 hero 随接管族整条下线 → 账清零。
    //     「新增即红」那一半仍由「排版棘轮·字号①／②」守着（空表下任何一处离刻度字号
    //     都是新增债），没有失守。
  // 批次 BC 还掉 15 处（17→2）：其余四份文件的行高比率债清零，条目随之删除
  // （空表 = 新增即红，比留一个 0 更诚实）。剩下的 2 处是稿纸那一对 `line-height: 2`
  // （styles.css 的 `.scene-editor-content` 与 editorial-studio.css 的同名规则；后者在
  // bundle 里更靠后、是胜者，前者从未画出来——批次 BD-2 记下了这件事但**没有**顺手删，
  // 理由见下）——它们不是「多抄了一档」，而是和 background 里那条 34px 横线的节距
  // **算出来的耦合**（17px × 2 = 34px 恰好一格），单改行高就会让横线漂到字腰上。
  // §3.3 给稿纸定的是 16px / 1.85，要落它必须字号、行高、节距三件一起重算，所以这两处
  // 归下一批专门的稿纸批次，不混进「离刻度值就近收敛」，也不混进「删死声明」。
  //
  // 批次 BD-20（稿纸批）：上面那笔许诺兑现，本表**清零并移除条目**。三件一起重算：
  //   字号 17→16、行高 2→1.85、节距 34px→29.6px（16×1.85=29.6，gradient 停靠点仍按
  //   「节距−1 / 节距」写，与改前同构）。styles.css 那条同名规则里的 `line-height: 2`
  //   走 BD-2 的「级联已死」路删掉（同特异度、editorial 后置，从未画出来）。
  // ⚠ 三件**没做**，都记在这里防止下一批当成遗漏：
  //   · 横线相位不往基线上「修正」：真机同法复测两版，改前横线在基线上 4px、改后 2px，
  //     都在拉丁下伸部（墨迹下沿 4px）那一带穿过，改后反而更贴基线。§3.3 那行只给
  //     字号/行高/行长三列，相位从未被任何人定过，挪它属于另一件设计决定。
  //   · max-width: 760px 不动（16px 下正文 696px = 43.5em，§3.3 要 42em = 736px 边框盒）：
  //     它与 styles.css 窄屏那条 `width: min(760px, calc(100% - 30px))` 是作者自己写死的
  //     一对，且收 24px 是真重排（每行少约 1.5 个汉字）——留给「行长 / measure 批」同定。
  //     ✔ 后续（批次 BD-22 落地）：两条**同批**一起改——正文 `calc(42em + 64px)` = 736 边框盒
  //       （42em=672 正文 + 32×2 内边距），≤1180 那条纸壳写 738 而不是 736，因为纸壳自己那圈
  //       1px 实线在 border-box 下吃 2px（editorial-studio.css:1288），736 只剩 734、掉回 41 字。
  //       死对没拆散，本条债清。
  //   · 段间 margin 1.1em（=17.6px）与 hr 2.7em、.author-note 0.86em 一律不动：它们是 em
  //     写法、随字号自动缩放，且不在 §3.3 那一行里。唯一被动的 em 是 min-height 1.9em→1.85em
  //     ——它不是「顺手收敛」，而是 1.9em 在新行盒下**从不生效变成会顶高每段**（实测
  //     30.4px > 29.6px），改它正是为了**不**改版。
  // ⚠ 本表清零后 stale 分支（还了债却不降预算）对行高比率不再可达：没有条目，actual
  //   不可能小于一个不存在的预算。它对应的注入用例「行高比率②」随本批退役（同批次 AV
  //   圆角、AS 幻影预算的规矩，留字在此，别让下一批人以为那条判据坏了）。
  const LH_BUDGET = {};
  // 批次 BD-24（行高长度式批）：立项那 43 处 / 22 文件全部换成 leading-[var(--leading-K)]，
  // 本表**清零并移除条目**（空表 = 新增即红）。还法照立项目录：不是把数值改小，而是把
  // leading-N 换成 §3.2 的八档同名无单位比率——每处取的是**该元素实际生效字号**同名那一档，
  // 生效字号由 Electron 产物探针逐处实测（43 处全部落在 11/12/13/14 四个刻度档上，
  // 无一需要猜）。这条换法会改像素，明细见提交信息；它是「按规格收敛」，不是「顺手改版」，
  // 区别在于规格只给一个合法方向（无单位），而档位的选取用了同名规则而非就近取整。
  // ⚠ 同批必须补的判据（见上面 ③b）：leading-[...] 方括号形态原本是 LH_UTILITY 的盲区，
  //   只换源码不补判据 = 把 43 处债从「看得见」搬进「看不见」，正是批次 L 记过的失效形态。
  // ⚠ 本表清零后 stale 分支（还了债却不降预算）对长度式不再可达，对应的注入用例
  //   「行高长度式②」随本批退役（needle 是 ui.tsx 那条 leading-6，已不存在）。同批次 AV
  //   圆角、AS 幻影预算、BD-20 行高比率②的规矩，留字在此，别让下一批人以为那条判据坏了。
  //   「新增即红」那一半仍有牙：长度式①（leading-6 落进零预算文件）＋本批新补的
  //   方括号长度式／档位名／配对三颗，见 inject 脚本。
  const LHL_BUDGET = {};

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
          if (v.startsWith("var(")) {
            // BD-24 把这条原本无条件的豁免换成档位校验：批次 L 的教训是「豁免一种写法
            // 等于把债搬进判据看不见的地方」。八档之外的 var（自创名字、写错档位、
            // 带字面量兜底）一律红——实仓此刻 line-height: var(...) 为 0 处，所以这条
            // 严格化不需要预算，也不会误伤任何现有写法。
            const tk = /^var\(--leading-(\d+(?:\.\d+)?)\)$/.exec(v);
            if (!tk) {
              fail(`第 8 步行高长度式：${rel}:${ln} line-height: ${v} 不是 §3.2 八档令牌的引用形式（合法写法只有 line-height: var(--leading-11/12/13/14/16/20/24/32)）。自创名字或带字面量兜底会让这本账看不见实际行高。`);
            } else if (!(tk[1] in LH_PAIR)) {
              fail(`第 8 步行高长度式：${rel}:${ln} line-height: ${v} 里的 ${tk[1]} 不是 §3.2 的八档之一（tokens.css 无此定义 → 静默回退）。`);
            }
            return;
          }
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
        for (const m of a.params.matchAll(LH_UTILITY)) {
          if (leadingForm(rel, ln, m[1], a.params, "@apply ")) continue;
          bump("LHL", rel, ln, `@apply leading-${m[1]}`);
        }
      });
    } else {
      stripped.split("\n").forEach((L, i) => {
        const ln = i + 1;
        for (const m of L.matchAll(FS_UTILITY)) {
          const body = m[1];
          const px = body.startsWith("[") ? parseFloat(body.slice(1, -1)) : TW_FS[body];
          if (px !== undefined && !FS_SCALE_PX.has(px)) bump("FS", rel, ln, `text-${body}`);
        }
        for (const m of L.matchAll(LH_UTILITY)) {
          if (leadingForm(rel, ln, m[1], L, "")) continue;
          bump("LHL", rel, ln, `leading-${m[1]}`);
        }
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
 *
 * ✔ 批次 BD-26（写作页迁移批）：上面这档兑现完毕，豁免名单与数字**一起清零**。
 *   那 3 条（首钮 hover 铜影 `0 5px 14px color-mix(--accent-spine 28%)`、全族
 *   `inset 0 1px 0 rgba(255,255,255,.6) + 0 1px 2px`、首钮 `inset 0 1px 0 … .22 +
 *   0 2px 6px color-mix 26%`）随 12 条接管规则整族删除，四条「物理感」:active 冠光
 *   一并下线；组件侧 <Button> 的 base 从未带投影，交接后「按钮无阴影」是默认值而不是
 *   豁免结果。
 *   ⚠ 清零的写法选的是「名单留空 + 计数留 0」，不是「把这段判据删掉」：
 *     · 名单空 → 这一族的任何投影直接落进 offenders，硬红（比原来的冻结更严）；
 *     · 计数留 0 → 若有人把类名加回 FROZEN_BUTTON_SHADOW 想给按钮「开个例外」，
 *       frozenHits 立刻变成 1 ≠ 0，同样红。批次 AF 立这条冻结自检时说的
 *       「冻结名单也是账，悄悄松绑比漏检更坏」，清零之后依然是它自己在守。
 *   钉在旧声明上的那颗 AF·冻结自检牙随之失去 needle，本批把它搬到仍然活着的
 *   同类形状上（见 inject 里 BD-26 的注释与退役留字）。
 */
{
  const BUTTON_ROLE = /(^|[\s>+~(])button(?![\w-])/;
  const FROZEN_BUTTON_SHADOW = [];
  // 数字由本判据扫出。批次 BD-26 起为 0：接管族整族下线，按钮投影不再有任何例外。
  // 保留这条相等判据的理由见上面注释的 ⚠ 段。
  const FROZEN_BUTTON_SHADOW_COUNT = 0;

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
      `规格 §2.5 冻结计数对不上：FROZEN_BUTTON_SHADOW 名单实测命中 ${frozenHits.length} 条、登记 ${FROZEN_BUTTON_SHADOW_COUNT} 条。` +
        `${frozenHits.slice(0, 3).join(" / ")}。批次 BD-26 起两边都必须是 0——` +
        `名单里出现类名意味着有人给某个按钮族开了 §2.5 例外（那正是「悄悄松绑」的形状），` +
        `要么把投影按 §2.5 收掉、名单留空，要么就这笔例外走人工裁定并同步本数字；` +
        `另一种可能是判据的按钮元素型或 inset/环豁免被动过。`
    );
  }
  console.log(
    `${TAG} [第 8 步已收口] §2.5 按钮投影遗留 ${frozenHits.length} 条（批次 BD-26 起 .desktop-page-actions 接管族整族下线，冻结名单与计数同为 0）；` +
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

  /**
   * 批次 BD-27：匹配口径从「子串」收紧为「词边界」。
   *
   * 子串口径有一个安静的钝点，BD-26 的墓碑里记过一半：`hostText.includes(cls)`
   * 会把「作为别的类名前缀出现」当成宿主存在。实测撞上的两例——
   *   · "desktop-home-inbox" 是 "desktop-home-inbox-count" 的前缀，
   *     所以接管族里那个零使用者的 .desktop-home-inbox 从没被判成死支；
   *   · "paper-muted" 是工具类 "text-paper-muted" 的子串，同理隐身。
   * 收紧后类名必须作为完整 token 出现（前后不接 [A-Za-z0-9_-]）。动态前缀那条
   * 出口保持原样（startsWith），因为 BEM 修饰的完整串本来就不在源码里。
   *
   * ⚠ 危险方向永远是「把活 CSS 判死」，所以收紧前先算了差集（/tmp/bd27-lens3.cjs，
   * 逐字复刻两副透镜）：幻影账翻出 9 条规则、死分支账翻出 1 条支，
   * 全部逐条人工核实（src 剥注释后无该 token、产物 JS 里也没有——JSX 的 className
   * 原样进 bundle，这是比源码更强的证据），且 MUST_BE_LIVE 七颗哨兵在词边界下
   * 全部仍判活。翻出来的 9 条本批一并删净，所以收紧与还债是同一批，不留红。
   */
  const clsTokenRe = (cls) => new RegExp(`(^|[^A-Za-z0-9_-])${cls.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}(?![A-Za-z0-9_-])`);
  const canRender = (cls) => {
    const re = clsTokenRe(cls);
    return re.test(hostText) || re.test(bundleText) || [...dynPrefix].some((p) => p.length >= 3 && cls.startsWith(p));
  };

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

  return { canRender, positiveClasses, clsTokenRe };
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

/* ------- 同键异值不得有两个真源（第 8 步批次 BD-29 立，BD-30 重写顺序真源，BD-31 补挂载覆盖 + 扩到全属性） */

/**
 * 批次 BD-31 动的是这副透镜的**前提**，不是账目：本批一行 CSS 都没改（构建产物逐字节相同）。
 * 两件：
 *  ① 挂载覆盖前提。BD-30 shipped 的版本默认「块级胜者总会挂载」，这条不成立——块级表只在
 *    宿主页面挂载时注入，而 .history-item-actions 由 HistoryPage.tsx **和** StatsPage.tsx
 *    两处渲染，后者根本不引那份表，主题侧那条在统计页是唯一的、在画的声明。活体实验（两侧
 *    各注入一条异值 color）证明旧判据会当场要求删掉它——照判决执行就是改版。BD-30 已摘的
 *    20 条逐条复核过宿主，没有一条真被误删；坏的是判据。现在块级胜者必须先证「全部宿主
 *    沿 import 链都挂得上」，证不了就不判，并把不判的条数与理由打进日志（静默豁免与失明
 *    长得一样）。顺带收掉一处含糊：候选现在按发射序从晚到早试，块级盖不住时回落到更晚的
 *    **静态**候选，而不是直接放弃判决。
 *  ② 判据从颜色扩到全属性。同一个形状在 padding（22）、gap（17）、font-weight（12）、
 *    display（10）、font-size（10）上一处不少，实测 130 条从未渲染的非颜色声明，BD-29/BD-30
 *    的透镜看不见它们。颜色仍零容忍（已归零、还得起），其余本批不还——还它要逐页证
 *    「每个宿主都挂得上胜者表」，一批做 130 条必然出错（BD-29 正是这么犯错的）——改为钉一份
 *    逐条清单 DEAD_LEDGER（键含文件/@上下文/选择器表/属性/值，刻意不含行号），
 *    账外新增要红、还了不改账也要红；重钉的唯一入口是 DESIGN_TOKEN_DUMP_DEAD=1。
 *
 * 上面那条只查 border-radius / box-shadow。同一形状的债在**颜色属性**上多一个量级，
 * 却一直没人名下：批次 BD-29 动手前实测 styles.css 里有 96 条颜色声明（color 39、
 * background 38、border 13、border-color 6）被 editorial-studio.css 用逐字相同的选择器
 * 分支、同 @上下文、同 important 异值压着——一个像素都没画出来过，却被当作活样式传给
 * 每一批读源码的人。BD-29 摘掉其中 95 条 + 拆开 1 条半死的；批次 BD-30 再摘掉
 * 「主题表 × 特性表」两向共 20 条 + 拆开 1 条（search.css 10 条、styles.css 历史页 6 条、
 * editorial-studio.css 总览页 4 条），本判据由此**全站归零、不再有预算这一档**。
 *
 * 判据（与几何分支同源，语义抄规范，不是我发明的）：两条声明若
 *   选择器文本相同（⇒ 特异度必然相同）+ @规则上下文相同（⇒ 同激活同失效）
 *   + 属性文本相同（⇒ 覆盖完全相同的长写集合）+ important 状态相同，
 * 则**发射更晚的一条整条压住更早的那条**。与被压那条有没有元素命中、处于什么状态无关，
 * 所以 :hover、.active、::-webkit-scrollbar 这些截图 A/B 触发不了的分支同样判得动。
 *
 * ⚠⚠ 发射顺序的真源是 **main.tsx 的 import 图**，不是「文件叫主题表还是特性表」。
 * 这条是 BD-30 修的，也是本批判据真正的收获。BD-29 那版把「特性局部表一律排在两份主题表
 * 之后」当事实写进判据，实测是**错的**：search.css 经 App.tsx→SearchPanel 静态 import，
 * 和 styles.css、editorial-studio.css 同进**一个主 bundle**，未压缩产物里各表的声明序列号
 * 实测是 settings-controls.css 0..158 → search.css 159..285 → styles.css 299..2898 →
 * editorial-studio.css 2950..4467（按「只属于这一张表的 选择器+属性+值 三元组」归属，
 * 无倒序）。也就是说 search.css 比两份主题表都早一整层。于是 BD-29 的账里有 10 条
 * 方向记反：真正的死侧在 search.css，主题侧那 10 条**一直在画**。
 * 后果不是账目难看，是**下一批会删错东西**：照 BD-29 那份账还债，就把在画的声明删掉、
 * 把从不渲染的留下，界面当场改版——而这正是本判据想防的东西，判据自己却犯了。
 * 所以顺序现在由代码从 import 图走出来的，并且把「与产物相符」这条前提也验上（见自检①）：
 *   · 从 src/app/main.tsx 出发顺静态 import 深度优先（含 "@/" 别名），得出 eager 发射序；
 *   · 动态 import(() => …) 的模块自成一批记为 lazy，排在全部 eager 表之后——它是代码块，
 *     CSS 由 vite 运行时注入 <head> 末尾（实测挂载历史页时 document.styleSheets 从 4 张
 *     变 5 张、新增的就是 HistoryPage-*.css）；
 *   · **lazy × lazy 不判**：两份块级表的相对顺序取决于用户先点进哪个界面，静态读不出来，
 *     硬猜就会造出假死侧（实测本仓两块级表无同名类，暂时也没有可判对象）；
 *   · 同一张表既被静态引又被动态引时按静态算（先到先得，实测 settings-controls.css 如此）。
 *
 * ⚠ 三条判据收紧的地方，都是实测踩出来的，不是洁癖：
 *  · **多选择器规则要逐个选择器判**：一条 `.a, .b { color: X }` 里 .a 被压死、.b 没人压，
 *    整条声明仍然是活的（删它 .b 就掉色）。两批各撞上一条，处理办法都是**拆选择器**：
 *    BD-29 是 styles.css 的 .background-card:hover / .active，BD-30 是 search.css 的
 *    .uni-search-group li button:hover / .active（:hover 的 background 被 editorial:1806
 *    压死，.active 那半边没有胜者）。把活半边留下、死半边摘掉，而不是整条留着撒谎。
 *  · **值相同不算分叉**（那是冗余），同值共存还剩 2 处（.project-nav button 的 border: 0
 *    与 background: transparent），另账。
 *  · **一侧带 !important 不判**：胜者由 !important 单独定，与几何分支同一豁免理由。
 *    ⚠ 这条在本仓不是纸面豁免：.stats-card / .desktop-panel-card 的 background 在
 *    editorial-studio.css:770 带 !important、styles.css 那侧不带——同键异值确实存在，
 *    但「谁发射得晚」这件事对它不成立，判成分叉就会逼人删掉该留的兜底。
 *
 * 为什么不用 CSSOM **规则层**读值判死没死：Chrome 对含 var() 的简写不在解析期展开长写，
 * 规则层 getPropertyValue("background-color") 拿到空串，`border: 0` 又被序列化成
 * `border-top: 0px; …` 碎片。BD-29 第一版真跑了浏览器，照这个形状误判出 57 条「查无此规则」
 * 再加 14 条「胜者不存在」。⚠ BD-30 补一句区分：**元素级** getComputedStyle 是另一回事，
 * 读的是级联算完的最终值，能当证据（本批就用它实测了 lazy 注入前后：同一个
 * .history-item small，挂载 history-local.css 前读 rgb(102,109,119)＝主题侧本条，
 * 挂载后读 rgb(128,137,148)＝胜者）——但它证不了「谁压谁」：两侧同值时读数一样，
 * 而且只看得见当前已挂载的页面。所以判据落在源码文本层 + import 图，产物与浏览器负责复核。
 *
 * 判「死」的第三种证法（BD-31 加）：胜者在块级表时，还要证**全部宿主都挂得上那份表**——
 * 块级表只在宿主页面挂载时注入，有宿主够不着它，那条被压的声明在它身上就是活的。
 * 这一层是 BD-31 从自己的上一批里挖出来的洞，见下面 MOUNT 那份记录的活体实验。
 *
 * 归零之后自检不能再拿债当样本（BD-29 那版正是如此，债一还完探针就瞎）：
 * 下面三条全部不依赖账上恰好有分叉。
 */
{
  // 挂载覆盖要找「渲染这个类名的模块」，用的正是幻影账 / 死分支账那位老熟人（批次 AD 立的
  // 规矩：谁也不带副本）。clsTokenRe 在 BD-31 之前没进过这个块，本批补上。
  const { positiveClasses, clsTokenRe } = hostPredicate;
  const relOf = (p) => path.relative(root, p).split(path.sep).join("/");
  // 批次 BD-31：判据从「颜色」扩到**全属性**。同一个失效形状（同键异值、靠发射顺序决胜）
  // 在 padding / gap / font-weight / display 上一处不少，而 BD-29/BD-30 的透镜只看颜色，
  // 于是那 130 条从未渲染的声明照样躲在账外——扩族不是加需求，是把同一副眼镜擦干净。
  // 颜色那一族仍**零容忍**（BD-29 95 + BD-30 20 已归零）；其余属性本批还来不及还，
  // 改为钉死一份账（见 DEAD_LEDGER）：债只许降、不许升，降了不改账也红。
  const COLOR_PROP = /^(color|background|background-color|border-color|border-top-color|border-right-color|border-bottom-color|border-left-color|outline-color|fill|caret-color|text-decoration-color|border)$/i;
  // 全属性的键只看「属性文本相同」，所以 `background` 与 `background-color`、`border` 与
  // `border-color` 这种「后发射的简写确实会重置先发射的长写」的组合**不在本判决范围**
  // ——那是漏报（方向安全），不是假账。带前导连字符的厂商属性同样先放过。
  const ALL_PROP = /^(?!--)[a-z][a-z0-9-]*$/i;
  const isFile = (p) => {
    if (!existsSync(p)) return false;
    try {
      readdirSync(p);
      return false; // 是目录
    } catch {
      return true;
    }
  };

  /**
   * 发射序：从 main.tsx 走 import 图（深度优先、子项按源码出现顺序）。
   * 走不出那两行主题表就返回 null——前提没了必须红，不许退回「按文件名猜谁在后」。
   */
  const SRC = path.join(root, "src");
  // resolveSpec 提到本块作用域：发射序（EMISSION）与挂载覆盖（MOUNT）走的是同一张
  // import 图，两处各写一份解析规则的话，两者对「这份 CSS 归哪个模块」就会分歧。
  const resolveSpec = (spec, fromFile) => {
    let base = null;
    if (spec.startsWith("@/")) base = path.join(SRC, spec.slice(2));
    else if (spec.startsWith("./") || spec.startsWith("../")) base = path.resolve(path.dirname(fromFile), spec);
    if (!base) return null;
    for (const cand of [base, base + ".tsx", base + ".ts", base + ".jsx", base + ".js", base + ".css", path.join(base, "index.tsx"), path.join(base, "index.ts")]) {
      if (isFile(cand)) return cand;
    }
    return null;
  };
  const EMISSION = (() => {
    const eager = [];
    const lazy = new Set();
    const visited = new Set();
    const collectLazy = (f, seen) => {
      if (!f || seen.has(f)) return;
      seen.add(f);
      if (f.endsWith(".css")) {
        lazy.add(relOf(f));
        return;
      }
      if (!/\.(tsx|ts|jsx|js)$/.test(f)) return;
      const code = readFileSync(f, "utf8");
      for (const m of code.matchAll(/["']([^"']+\.(?:css|ts|tsx|js|jsx))["']|["'](@\/[^"']+)["']|["'](\.[^"']+)["']/g)) {
        const r = resolveSpec(m[1] || m[2] || m[3], f);
        if (r) collectLazy(r, seen);
      }
    };
    const walk = (file) => {
      if (visited.has(file)) return;
      visited.add(file);
      if (file.endsWith(".css")) {
        if (!eager.includes(relOf(file))) eager.push(relOf(file)); // 静态优先：先到先得
        return;
      }
      if (!/\.(tsx|ts|jsx|js)$/.test(file)) return;
      const code = readFileSync(file, "utf8");
      const hits = [];
      const RE = /\bimport\s+(?:type\s+)?(?:[\w{},*\s]+from\s*)?["']([^"']+)["']|\bimport\(\s*["']([^"']+)["']\s*\)/g;
      for (const m of code.matchAll(RE)) {
        const spec = m[1] || m[2];
        const dyn = !m[1];
        const r = resolveSpec(spec, file);
        if (r) hits.push({ r, dyn, i: m.index });
      }
      hits.sort((a, b) => a.i - b.i);
      for (const h of hits) {
        if (h.dyn) collectLazy(h.r, new Set());
        else walk(h.r);
      }
    };
    walk(path.join(SRC, "app", "main.tsx"));
    const THEME2 = ["src/styles.css", "src/styles/editorial-studio.css"];
    const idx = THEME2.map((rel) => eager.indexOf(rel));
    if (idx.some((i) => i < 0) || !(idx[0] < idx[1])) return null;
    return { eager, lazy };
  })();

  /**
   * 挂载覆盖（批次 BD-31 补的前提）：块级表只在宿主页面挂载时才注入。
   * 若某个渲染该类名的模块沿 import 链**根本到不了**那份胜者表，那么在那个页面上，
   * 被压的那条就是唯一的、在画的声明——判它死就是给「删掉一条活样式」开许可证。
   * BD-30 shipped 的判据默认「lazy 胜者总会挂载」，这个前提在本批动手前的
   * 活体注入实验里被打穿：给 styles.css 的 .history-item-actions 注入 color，
   * history-local.css 同键那条在更晚的块级表里，判据当场要求删 styles.css 那条——
   * 而 StatsPage.tsx 渲染这个类名却不 import history-local.css，那条在那个页面是活的。
   * （BD-30 已摘的 20 条逐条复核过宿主，全部宿主只出现在挂载胜者表的页面里，
   * 没有误删；坏的是判据，不是那批账。）
   *
   * 与发射序走**同一张 import 图**（同一个 resolveSpec，两处各写一份必分歧）：
   * 节点 = src 下全部 .tsx/.ts/.jsx/.js（排除 __tests__），边 = 静态 + 动态 import；
   * 某模块可达的 CSS 集合 = 它自己 import 的 + import 链上带进的。
   * 类名宿主按词边界找（与 hostPredicate.canRender 同口径的 clsTokenRe），
   * 用**未剥注释**的原文：宿主多算一个只是更保守（更可能不判），
   * 少算一个才会把活声明判死——危险方向永远要往保守里错。
   */
  const MOUNT = (() => {
    const RE = /\bimport\s+(?:type\s+)?(?:[\w{},*\s]+from\s*)?["']([^"']+)["']|\bimport\(\s*["']([^"']+)["']\s*\)/g;
    const mods = [];
    const collect = (dir) => {
      for (const e of readdirSync(dir, { withFileTypes: true })) {
        const p = path.join(dir, e.name);
        if (e.isDirectory()) collect(p);
        else if (/\.(tsx|ts|jsx|js)$/.test(p) && !p.includes(`${path.sep}__tests__${path.sep}`)) mods.push(p);
      }
    };
    collect(SRC);
    const edges = new Map();
    const texts = new Map();
    // 批次 BD-43：同一份源码要再分一次「静态边 / 动态边」，因为发射顺序与「一条载得起宿主
    // 的路带不带某张表」用的口径不同。⚠ 下面那个 js 一字未动（BD-31 的并集口径归它），
    // 静态闭包另用 sj + 桶文件 re-export 边；re-export 不算边的话，components/index.ts 这种
    // 桶文件下面的每个组件都会被判成「无人 import 的独立入口」，虚报缺席（实测踩过）。
    const RELRE = /\bexport\s+(?:type\s+)?(?:[\w{},*\s]+from\s*)?["']([^"']+)["']/g;
    const modSet = new Set(mods);
    for (const f of mods) {
      const code = readFileSync(f, "utf8");
      texts.set(relOf(f), code);
      const js = new Set();
      const sj = new Set();
      const dj = new Set();
      const css = new Set();
      for (const m of code.matchAll(RE)) {
        const r = resolveSpec(m[1] || m[2], f);
        if (!r) continue;
        if (r.endsWith(".css")) css.add(r);
        else if (/\.(tsx|ts|jsx|js)$/.test(r)) {
          js.add(r);
          (m[1] ? sj : dj).add(r);
        }
      }
      for (const m of code.matchAll(RELRE)) {
        const r = resolveSpec(m[1], f);
        if (r && modSet.has(r)) sj.add(r);
      }
      edges.set(f, { js, css, sj, dj });
    }
    const cssOfModule = new Map();    for (const f of mods) {
      const out = new Set();
      const seen = new Set();
      const stack = [f];
      while (stack.length) {
        const cur = stack.pop();
        if (seen.has(cur)) continue;
        seen.add(cur);
        const e = edges.get(cur);
        if (!e) continue;
        for (const c of e.css) out.add(relOf(c));
        for (const j of e.js) stack.push(j);
      }
      cssOfModule.set(relOf(f), out);
    }
    /**
     * 批次 BD-43 的两份新数据（BD-31 那份 cssOfModule 一字未动）：
     *   · entryStatic：每个「加载入口」的**静态闭包**（模块集 + CSS 集）。入口 = main.tsx
     *     加上全部 import() 的目标——这是 Vite 的 chunk 边界，走哪个入口进来，那个入口的
     *     静态闭包就必然在场（产物实测：每个动态 chunk 的 CSS 依赖打在它自己的 preload
     *     deps 数组里）。闭包只走静态边 + 桶文件 re-export 边。
     *   · hostEntries：某模块被哪些入口的静态闭包含住 = 「载得起它的路」有哪些。
     */
    const edgesRel = new Map();
    for (const f of mods) {
      const e = edges.get(f);
      edgesRel.set(relOf(f), { sj: [...e.sj].map((x) => relOf(x)), dj: [...e.dj].map((x) => relOf(x)), css: [...e.css].map((x) => relOf(x)) });
    }
    const entryNames = new Set(["src/app/main.tsx"]);
    for (const [, e] of edgesRel) for (const d of e.dj) if (edgesRel.has(d)) entryNames.add(d);
    const entryStatic = new Map();
    for (const e0 of entryNames) {
      const ms = new Set(), cssSet = new Set(), stack = [e0];
      while (stack.length) {
        const cur = stack.pop();
        if (ms.has(cur)) continue;
        ms.add(cur);
        const e = edgesRel.get(cur);
        if (!e) continue;
        for (const c of e.css) cssSet.add(c);
        for (const j of e.sj) if (!ms.has(j)) stack.push(j);
      }
      entryStatic.set(e0, cssSet);
    }
    const hostEntries = new Map();
    for (const e0 of entryStatic.keys()) {
      const mset = new Set();
      const stack = [e0];
      while (stack.length) {
        const cur = stack.pop();
        if (mset.has(cur)) continue;
        mset.add(cur);
        for (const j of edgesRel.get(cur)?.sj || []) if (!mset.has(j)) stack.push(j);
      }
      for (const m of mset) {
        if (!hostEntries.has(m)) hostEntries.set(m, new Set());
        hostEntries.get(m).add(e0);
      }
    }
    /**
     * 这个 lazy 胜者表能否覆盖该选择器的全部宿主。
     * 返回 null = 覆盖成立（可以判死）；返回字符串 = 不判的**理由**，必须报出来——
     * 豁免如果只体现为「账上少了几条」，半年后没人分得清这是判据准还是判据瞎。
     */
    const whyNotCovered = (sel, winnerRel) => {
      const cls = positiveClasses(sel);
      if (!cls.length) return "无类名分支，宿主无从登记"; // 裸元素选择器：宿主可能来自任何地方，保守不判
      const hosts = [...new Set(cls.flatMap((c) => {
        const re = clsTokenRe(c);
        return [...texts.entries()].filter(([, t]) => re.test(t)).map(([k]) => k);
      }))];
      if (!hosts.length) return "查无宿主（幻影族另判）";
      const bad = hosts.filter((h) => !(cssOfModule.get(h) || new Set()).has(winnerRel));
      // 报告里只列前三个宿主：这条要进守卫的红话与绿话，全量清单会把关键部分顶出屏幕。
      if (bad.length) return `${bad.length} 个宿主沿 import 链够不着胜者表：${bad.slice(0, 3).join("、")}${bad.length > 3 ? "…" : ""}`;
      return null;
    };
    return { whyNotCovered, cssOfModule, moduleCount: mods.length, entryStatic, hostEntries, texts };
  })();
  const { whyNotCovered } = MOUNT;

  const deadBy = new Map();
  /**
   * 非颜色死声明账（批次 BD-31 钉）。130 条，全部由本判据实测生成——重钉的唯一入口是
   * `DESIGN_TOKEN_DUMP_DEAD=1 node scripts/verify-design-tokens.mjs`，不许手写：
   * 手抄一遍就有两个真源，而这份账的全部意义是「与实测逐字相同」。
   * 键 = 文件 ⑅ @上下文 ⑅ 选择器表 ⑅ 属性 ⑅ 值，刻意**不含行号**（上方加删一行不该让整本账
   * 重钉），但含选择器表与值：拆了半条规则、改了值，键就变，账当场对不上——那正是要红的事。
   * 每行注释里的行号只为方便跳转，不参与匹配。
   *
   * 这份账为什么本批不还：130 条要逐条判「删了会不会改版」，其中 padding / gap / font-size
   * 这些几何与字号一族，删掉静态副本后**没挂上块级表的页面**会直接掉回更小的默认值——
   * 也就是挂载覆盖那层前提在这里是双向的：判死要证「胜者总会挂载」，还债要证「每个宿主
   * 都挂得上」。这需要逐页 A/B 截图与探针，一批做 130 条必然出错（BD-29 就是这么犯错的）。
   * 所以本批把「看得见」立住（判据 + 账 + 牙），还债按文件分批排在后头（BD-32+）。
   *
   * 还债进度（数字都由重钉实测得到，不手填）：BD-31 立账 130 条 → BD-32 还 10 条
   * （search.css 4 + editorial 6）→ BD-33 还 22 条（styles.css 桌面壳三族
   * .desktop-commandbar / .desktop-page-hero / .project-nav，胜者全是静态表 editorial，
   * 删除前提天然成立，是最安全的一族）→ BD-34 还 19 条（写作页 .writing* 七族，同样全是
   * 静态胜者）→ BD-35 还 18 条（桌面壳零散六族：.desktop-sidebar 3、.desktop-nav 3、
   * .desktop-sidebar-stats 5、.desktop-search-command 5、.desktop-canvas 1、
   * .desktop-page-scroll 1）→ BD-36 还 11 条（项目首页 .project-home* 一族：-list 1、
   * -item 3、-item:hover 1、-item-main strong 2、-arrow 1、-chars 1、-empty 2）→ BD-37 还 14 条
   * （改按**宿主页面**凑整屏，不再按族凑：卡片板 .cards-board* 5、大纲页 .outline-page* 5 +
   * .outline-scene-main 1、背景设定 .background* 3）→ BD-38 还 19 条（仍是按宿主页面凑：
   * 桌面壳零散 ::-webkit-scrollbar 2、.desktop-workbench 2、.desktop-card-label 2、
   * .desktop-library-grid-head 2、.desktop-library-row 1 + :hover 1、灵感页
   * .desktop-inspiration-page 2、.scene-editor-content 1、reduced-motion 的 * 2、
   * .stats-card h3 2、收件箱 .inbox-list 1 + .inbox-item-title 1。这批第一次连带还掉
   * 一笔真的间距刻度债：灵感页那条 gap: 14px 不在 §5.1 八档上，styles.css 的预算因此
   * 102→101、全站 274→273——BD-36/37 两批「全在档上所以预算不动」的反例就此补齐，
   * 三批合起来才是这条耦合的完整形状：判据会自己红，数字跟着实测走）→ BD-39 还 4 条
   * （这是「人工锚批」：账上最后 5 条静态胜者里绕不开的那 4 条。动手前必须先重钉 6 颗
   * 既有牙——L544/545/546（AQ 三颗，锚在 styles.css 第 584 行 .desktop-sidebar-card 的
   * padding: 12px）与 L722/732/733（BD-29 死色，锚在第 507 行 .desktop-brand small 的
   * margin-top: 2px），全部改钉「规则名 + 本批墓碑注释头」（BD-37 立的模式），插回的
   * 声明一律放在注释头**之前**，否则落进注释里牙就哑了。510（letter-spacing）与 3711
   * （.project-workbench gap: 14px）经审计没有牙钉着，可直接摘；3711 虽然与自动锚 3710
   * 同规则，但 gap 属间距族、本就被 deadAnchor() 的候选条件排除，摘它不伤自动锚——这条
   * 是摘完重跑候选测试实测确认的（合格候选仍 1 条 = 3710）。连带还掉第二笔真刻度债：
   * 14px 离 §5.1 八档，styles.css 预算 101→100、全站 273→272）→ BD-40 还 12 条
   * （块级胜者批：11 条 history-local.css + 1 条 outline-reorg.css。这批走的是 BD-31 立账
   * 注释里那条「还债要证每个宿主都挂得上」的反方向前提：摘之前按判据同一张 import 图
   * 逐条复核宿主模块可达胜者表（.card-board-card 只有 CardBoard.tsx、.history* 只有
   * HistoryPage.tsx——StatsPage.tsx 里那个 history-item-actions 是**另一个类名**，
   * clsTokenRe 的负向断言把它排除掉，宿主集合是干净的），锚冲突审计 77 条 needle 零重叠。
   * 连带还掉第三笔真刻度债：.history-empty 的 padding: 18px 离 §5.1 八档，
   * styles.css 预算 100→99、全站 272→271；gap 6px / gap 10px / padding 10px 12px 在档上）→
   * BD-41 还最后 1 条（第 3710 行 .project-workbench grid-template-columns: 168px minmax(0, 1fr)，
   * 胜者 editorial-studio.css:944 是 140px；168 与 140 都不在 §5.1 八档上、而
   * grid-template-columns 不在间距棘轮的属性集里，所以本批**不连带**还刻度债）→
   * **现存 0 条，账已清空**。清账的代价是先把两颗「死账」哨兵牙从债上解绑：它们原先由
   * deadAnchor() 从本清单实测结果里自动挑锚（BD-34 立），前提就是「账上得一直留一条债」——
   * BD-36/37 两批连着把锚所在行还掉、它自动换人，BD-40 之后账上只剩 3710 一条，那颗牙等于
   * 把最后这笔债锁成了人质：判据明明能清零，却因为牙而不能还。这是「人在伺候牙」的最后一种
   * 形状。现在 ③④ 各钉永久锚，账空也测得出方向（详见电池里 deadAnchor() 原位留下的注释）。⚠ BD-35 起新增一条固定动作：还债前跑锚冲突审计（脚本在
   * /tmp，每批按当批候选重建：把电池里全部 mutate("src/styles.css", …) 的 needle 解出来
   * 定位覆盖行，与本批候选取交集）——BD-35 的 507（.desktop-brand small margin-top，
   * BD-29 死色①②反向②③ 的锚）与 584（.desktop-sidebar-card padding，AQ 三颗牙的锚）
   * 就是因此绕开的；锚随声明一起变墓碑，牙当场哑掉。摘完还要再跑一次，用「全部 needle
   * 仍命中且唯一」当没伤到既有牙的证据。
   *
   * BD-34 起「死账」那两颗牙不再手挑锚点，改由电池里的 deadAnchor() 从本清单实测结果里
   * 自动选（值含 px、不碰间距/字号棘轮、逐字唯一、摘掉不空规则、文件在备份清单）——
   * 前面三批每还一批就要手工换一次锚，那是人在伺候牙。
   */
  const DEAD_LEDGER = new Set([
  ]);
  const unjudgedBy = new Map(); // 因挂载覆盖不成立（或判不动）而放过的，BD-31 起单独计数
  const sheets = new Map();
  if (!EMISSION) {
    fail(
      `第 8 步颜色分支：从 src/app/main.tsx 走不出可信的发射顺序（styles.css / editorial-studio.css 至少一份不在静态可达清单里，或两份的行序反了）——` +
        `本判据的「谁压谁」整个建立在发射顺序上，前提没了就必须红，不许退回「按文件名猜谁在后」。` +
        `BD-29 那版就是这么猜的（把特性表一律排到主题表之后），结果把 10 条在画的声明记成了死色，照那份账还债会直接改版。`
    );
  } else {
    const rank = (rel) => {
      const i = EMISSION.eager.indexOf(rel);
      if (i >= 0) return i;
      return EMISSION.lazy.has(rel) ? EMISSION.eager.length : null;
    };
    for (const cssFile of cssFiles) {
      const rel = relOf(cssFile);
      if (rel.includes("__tests__")) continue;
      let ast;
      try {
        ast = postcss.parse(preserveNewlines(readFileSync(cssFile, "utf8")), { from: cssFile });
      } catch {
        continue;
      }
      const m = new Map();
      ast.walkRules((rule) => {
        const ctx = [];
        for (let p = rule.parent; p && p.type !== "root"; p = p.parent) if (p.type === "atrule") ctx.push(`@${p.name} ${p.params}`);
        const c = ctx.slice().reverse().join(" ").replace(/\s+/g, "");
        // @layer 里的声明与未分层声明不是「同键比顺序」的关系（分层永远输给未分层），
        // 本判据的顺序模型覆盖不了它，遇到就跳过——当前全站 CSS 有 42 条 @layer 声明，
        // 全部集中在 styles.css 的 .prose 一族，没有一条参与过同键决胜。
        if (/@layer/.test(c)) return;
        // @keyframes 的时间步与「发射顺序决胜」是两回事（同一动画里同名步进才比先后，
        // 跨表的 @keyframes 同名整条被后者替换、不同名互不相干），本顺序模型覆盖不了，
        // 保守不判。当前全站的 keyframes 都在两份主题表里同名成对，判了就是给自己造假账。
        if (/@keyframes/.test(c)) return;
        const sels = rule.selectors.map((s) => s.replace(/\s+/g, " ").trim());
        const selJoin = sels.join(",");
        const nDecls = rule.nodes.filter((x) => x.type === "decl").length;
        rule.walkDecls((d) => {
          if (d.prop.startsWith("--") || !ALL_PROP.test(d.prop)) return;
          const prop = d.prop.toLowerCase();
          const value = d.value.replace(/\s+/g, " ").trim();
          const rec = {
            rel,
            line: d.source?.start?.line ?? 0,
            prop,
            value,
            imp: !!d.important,
            sels,
            // 账本键：不含行号。上方改一处行数不该让整本账重钉，
            // 而它依赖的正是判据本身看的四件事 + 这条规则自己的形状（选择器表、声明条数）——
            // 「拆了半条多选择器规则」「摘到规则只剩这一条」都会让键变，那正是要红的事。
            key: `${rel}\t${c}\t${selJoin}\t${prop}\t${value}`,
            nSels: sels.length,
            nDecls,
          };
          for (const s of sels) {
            const k = `${c}\t${s}\t${rec.prop}`;
            if (!m.has(k)) m.set(k, []);
            m.get(k).push(rec);
          }
        });
      });
      sheets.set(rel, m);
    }
    for (const [rel, m] of sheets) {
      const r0 = rank(rel);
      if (r0 === null) continue;
      for (const [k, list] of m) {
        for (const a of list) {
          const winners = [];
          let missing = false;
          let uncovered = null;
          for (const s of a.sels) {
            const kk = `${k.split("\t")[0]}\t${s}\t${k.split("\t")[2]}`;
            const cands = [];
            for (const [o, om] of sheets) {
              if (o === rel) continue;
              const r1 = rank(o);
              if (r1 === null || r1 <= r0) continue; // 只认更晚发射的那一侧压住我
              if (r0 >= EMISSION.eager.length && r1 >= EMISSION.eager.length) continue; // lazy × lazy 不可判
              for (const x of om.get(kk) || []) {
                if (x.value !== a.value && x.imp === a.imp) cands.push({ x, r1 });
              }
            }
            if (!cands.length) { missing = true; break; }
            // 候选按发射序**从晚到早**试。块级胜者要先过「挂载覆盖」这层前提（块级表只在
            // 宿主页面挂载时注入，有宿主够不着它，这条被压的声明在那个页面上就是活的，
            // 不许判死，BD-31）；静态表发射时必然在场，不需要这层前提。所以块级盖不住时
            // 回落到更晚的静态候选——「有没有一张总会发射的表整条压着它」才是死的充分条件。
            // ⚠ BD-30 shipped 版在这里直接取 Map 序最后一个候选（既没按 rank 排、也不看覆盖），
            // 本批连这处含糊一起收掉。
            cands.sort((p, q) => q.r1 - p.r1);
            let covered = null;
            let why = null;
            for (const c of cands) {
              if (c.r1 >= EMISSION.eager.length) {
                const w = whyNotCovered(s, c.x.rel);
                if (w) { why = w; continue; }
              }
              covered = c.x;
              break;
            }
            if (!covered) { uncovered = why; break; }
            winners.push(`${s} ⇐ ${covered.rel}:${covered.line}`);
          }
          // 覆盖不成立要抢在 missing / !winners.length 之前入账：够不着胜者表时
          // winners 本来就是空的，顺序写反它会被当成「没有胜者」悄悄溜走。
          if (uncovered) {
            const id = `${rel}:${a.line}:${a.prop}`;
            if (!unjudgedBy.has(id)) unjudgedBy.set(id, { a, why: uncovered });
            continue;
          }
          if (missing || !winners.length) continue;
          if (!deadBy.has(a.key)) deadBy.set(a.key, { a, winners });
        }
      }
    }
    // 零容忍只留给颜色那一族（BD-29 摘 95 拆 1 + BD-30 摘 20 拆 1，已经归零、还得起）。
    // 其余属性的同键异值死账是 BD-31 扩族才第一次看见的 130 条，本批不还，改为**钉账**：
    // 与 DEAD_LEDGER 逐条比，多一条（新增死声明）要红，少一条（还了没改账）也要红——
    // 和几何分支、幻影账同一套手法，方向安全靠的是「账目必须与实测同步」，不是宽容。
    const colorDead = [...deadBy.values()].filter((x) => COLOR_PROP.test(x.a.prop));
    const otherDead = [...deadBy.values()].filter((x) => !COLOR_PROP.test(x.a.prop));
    if (colorDead.length) {
      const list = colorDead.slice(0, 4).map((x) => `${x.a.rel}:${x.a.line} {${x.a.prop}: ${x.a.value}} ⇐ ${x.winners.join("；")}`);
      fail(
        `第 8 步颜色分支：全站 ${colorDead.length} 条颜色声明在同键（同选择器文本 + 同 @上下文 + 同属性 + 同 important）上被发射更晚的一条整条压住，从未画出一个像素——${list.join(" ｜ ")}` +
          `\n  发射顺序取自 main.tsx 的 import 图（${EMISSION.eager.length} 张静态表按行序 + ${EMISSION.lazy.size} 张块级表在其后）；块级胜者还要过「挂载覆盖」这层前提（BD-31），前提不成立的另列。` +
          `\n  批次 BD-29 摘 95 拆 1、BD-30 摘 20 拆 1 之后这里是零，没有预算档。` +
          `\n  要么删掉不生效那条并留注释写明胜者（多选择器规则要拆开，别整条留着撒谎），要么让两条真正分工（不同选择器 / 不同属性 / 不同 important）。`
      );
    }
    const ledgerMiss = otherDead.filter((x) => !DEAD_LEDGER.has(x.a.key));
    const ledgerStale = [...DEAD_LEDGER].filter((k) => !deadBy.has(k));
    // 重钉账本的唯一入口（DESIGN_TOKEN_DUMP_DEAD=1）：账由实测生成，不许手写——
    // 手抄一遍就会有两个真源，而这份账的全部意义就是「与实测逐字相同」。
    if (process.env.DESIGN_TOKEN_DUMP_DEAD === "1") {
      const dump = otherDead.map((x) => `    ${JSON.stringify(x.a.key.replace(/\t/g, "\t"))}, // ${x.a.rel}:${x.a.line} {${x.a.prop}: ${x.a.value}} ⇐ ${x.winners[0]}`);
      console.log(`${TAG} [DESIGN_TOKEN_DUMP_DEAD] 非颜色死声明 ${otherDead.length} 条：\n${dump.join("\n")}`);
    }
    if (ledgerMiss.length || ledgerStale.length) {
      const show = (x) => `${x.a.rel}:${x.a.line} {${x.a.prop}: ${x.a.value}} ⇐ ${x.winners[0]}`;
      const parts = [];
      if (ledgerMiss.length) {
        parts.push(
          `新增 ${ledgerMiss.length} 条非颜色死声明不在账上（现存 ${otherDead.length} 条）——同键异值靠发射顺序决胜 = 更早那条永远不渲染：${ledgerMiss.slice(0, 4).map(show).join(" ｜ ")}` +
            `\n  要么按判据给出的胜者摘掉它（留注释写明胜者，行式墓碑保住行号），要么让两条真正分工；确实判得对又本批还不完的，才允许进 DEAD_LEDGER，并把「为什么本批不还」写在那儿。`
        );
      }
      if (ledgerStale.length) {
        parts.push(
          `账上 ${ledgerStale.length} 条已经扫不到了：${ledgerStale.slice(0, 4).map((k) => k.replace(/\t/g, " ⑅ ")).join(" ｜ ")}。` +
            `\n  还了债就得把 DEAD_LEDGER 同步删掉——留在账上，下一批人照着它去找一笔已经不存在的债，还会顺手把「新增」当合法额度。`
        );
      }
      fail(`第 8 步非颜色死声明账与实测不符（${parts.join("\n  ")}`);
    }
    if (unjudgedBy.size) {
      const list = [...unjudgedBy.values()].slice(0, 6).map((x) => `${x.a.rel}:${x.a.line} {${x.a.prop}: ${x.a.value}}——${x.why}`);
      console.log(`${TAG} [第 8 步挂载覆盖] ${unjudgedBy.size} 条同键异值声明**不判**（块级胜者未必挂得上）：${list.join(" ｜ ")}`);
    }
    // 上面 colorDead 那条就是零容忍本体。原先「deadBy.size 一律红」的写法在 BD-31 扩族后
    // 会把 130 条非颜色债也按「颜色分支」报出来（红话撒谎），故拆成两支：颜色零容忍、
    // 其余钉账。「按文件冻结预算」是 BD-29 的错处之一：它把**没核实的方向**写成合法额度，
    // 于是记反的方向也被冻成了预算，看起来像待还的债，其实是判据在撒谎——钉的是**逐条清单**
    // 而不是额度，就是为了不再犯同一个错。
    {
      // 判据自检三条，全部不依赖账上有债：
      // ① 顺序模型必须与真实产物相符：search.css 与 editorial-studio.css 同进主 bundle，
      //    而前者在 import 图里更靠前（这正是 BD-29 记反的那一对，现在是**核实过的前提**）。
      //    自检不能读产物（守卫不依赖 out/，那里可能还没构建），改用「import 图里两份表都
      //    静态可达且 App.tsx 先引 SearchPanel 后引主题样式」这条源码事实钉住。
      // ② 归一化没坏：两份主题表现在仍共存的同键声明必须两侧都扫到——扫不到就是键被改坏，
      //    而那时任何分叉都会被安静放行。
      // ③ 反向：同键同值的共存不得进死账（冗余不是分叉，判红会逼人多删一条在画的）。
      const rSearch = EMISSION.eager.indexOf("src/features/search/search.css");
      const rEditorial = EMISSION.eager.indexOf("src/styles/editorial-studio.css");
      const appSrc = read("src", "app", "App.tsx");
      const iSearch = appSrc.indexOf('from "@/features/search/SearchPanel"');
      const iStyles = EMISSION.eager.indexOf("src/styles.css");
      // ⚠ 只验「search 在 editorial 之前」不够：BD-30 自己造的牙⑦（把 main.tsx 的
      //   `import App` 挪到两份主题表之间）实测**还是绿的**——那条改动把 styles.css 从
      //   rank2 顶到 rank0，死侧整体换位，而上面这两个关系依旧成立。所以这里把
      //   产物实测过的那份完整清单钉死：改这个顺序当然允许，但必须连本判据的账一起重算。
      const WANT_ORDER = [
        "src/features/settings/settings-controls.css", // 产物 seq 0..158
        "src/features/search/search.css", // 159..285
        "src/styles.css", // 299..2898
        "src/styles/editorial-studio.css", // 2950..4467
      ];
      const gotOrder = EMISSION.eager.join(" , ");
      const wantOrder = WANT_ORDER.join(" , ");
      if (gotOrder !== wantOrder) {
        fail(
          `第 8 步颜色分支判据退化：import 图走出的发射顺序是 [${gotOrder}]，与构建产物实测的 [${wantOrder}] 不符。` +
            `顺序一变「谁压谁」就整体换位（死侧换位 = 照旧账还债会把在画的声明删掉，BD-29 已经犯过一次）：` +
            `要么这条 import 改动会真的改版，请回退；要么产物顺序确实跟着变了，请重新实测 index-*.css 的声明序列号区间并同步本清单。`
        );
      }
      if (!(rSearch >= 0 && rEditorial > rSearch && iSearch >= 0 && iStyles >= 0)) {
        fail(
          `第 8 步颜色分支判据退化：import 图算出的发射顺序里 search.css(rank${rSearch}) 不在 editorial-studio.css(rank${rEditorial}) 之前，` +
            `或 App.tsx 里读不到 SearchPanel 的 import（index=${iSearch}）。这两份表同进一个主 bundle，未压缩产物实测 seq 159..285 对 2950..4467——` +
            `顺序模型与产物不符时「谁压谁」整体不可信，本判据的所有判决作废。`
        );
      }
      const SAME_KEY = "\t.project-nav button\tborder"; // 两份主题表都还写着 border: 0（同值共存）
      const aSide = sheets.get("src/styles.css")?.get(SAME_KEY) || [];
      const bSide = sheets.get("src/styles/editorial-studio.css")?.get(SAME_KEY) || [];
      if (!aSide.length || !bSide.length) {
        fail(
          `第 8 步颜色分支判据退化：.project-nav button 的 border 本该在两份主题表里各有一份同键声明，现在扫不到两侧同现（styles=${aSide.length}、editorial=${bSide.length}）——` +
            `键归一化（选择器空白 / @上下文 / 属性大小写）被改坏的话，分叉会被安静放行。`
        );
      }
      const leaked = [...deadBy.values()].some((x) => x.a.prop === "border" && x.a.sels.includes(".project-nav button") && x.a.value === bSide[0]?.value);
      if (leaked) {
        fail(`第 8 步颜色分支判据退化：.project-nav button 的 border 两侧**同值**（border: 0），同值重复是冗余不是分叉，却进了死账——判据过严就会逼人删掉在画的兜底。`);
      }
      // ④（批次 BD-31）挂载覆盖这层前提自己也要有哨兵，因为它天生的失效方向是
      // 「把一切判成不可判」：那样死账归零、判据全绿，却什么都不再守——比误判更安静。
      // 用一对真实宿主钉住它的两种结局，两者必须**同时**成立：
      //   · .history-item small 的宿主只有 HistoryPage.tsx，而它自己 import 了那份胜者表
      //     → 覆盖成立，这条要能判（.history-item small 那条 font-size: 11px 曾挂在账上，
      //       批次 BD-40 已把它摘成墓碑——哨兵钉的是「判得动」这件事，不是某一行）；
      //   · 批次 BD-42 起「覆盖不成立」那一半改钉 .history-modal：它由历史页的三个子对话框
      //     （CreateMilestoneDialog / PurgeTrashDialog / RestoreSnapshotDialog）渲染，三个都
      //     **不 import** 那份表——运行时表必然已注入（它们只在 HistoryPage 里出现），可 import 图
      //     只沿模块往外找、够不着，判据就保守不判。这正好是这层前提的本意：证不了就别判死。
      //     ⚠ 原先钉的是 .history-item-actions（宿主 StatsPage.tsx 借这个类名却不挂表）。批次
      //     BD-42 把那次借用改成各自类名（借类名会让统计页的钮间距随「先看没看过历史页」在
      //     4px/6px 之间漂，实测过），那条分叉就此合法消失——本句按 4562 行那句预告一起重算。
      // 哪天 import 图、宿主谓词或 resolveSpec 被改坏，这两句会一起塌掉。
      const LAZY_WINNER = "src/features/creation/history/history-local.css";
      const covJudged = whyNotCovered(".history-item small", LAZY_WINNER);
      const covSkipped = whyNotCovered(".history-modal", LAZY_WINNER);
      if (covJudged !== null || covSkipped === null) {
        fail(
          `第 8 步颜色分支判据退化（挂载覆盖·BD-31）：` +
            `「.history-item small → 覆盖成立（${covJudged === null ? "对" : covJudged}）」与` +
            `「.history-modal → 覆盖不成立（${covSkipped === null ? "被误判成覆盖成立" : covSkipped}）」这两句现在不是**一同一异**。` +
            `两个都成立说明覆盖前提塌成了整体豁免（判据瞎，账上从此不会再有 lazy 胜者），两个都不成立说明 import 图 / 宿主谓词被改坏（判据会把活 CSS 判死）。` +
            `若产品真的让那三个子对话框也 import 这份表（或把类名改掉），那是合法的改版——请连同这两句哨兵一起重算（批次 BD-42 就是这么改的，改完哨兵当场红、按本句重锚）。`
        );
      }
      if (MOUNT.moduleCount < 50) {
        fail(`第 8 步颜色分支判据退化（挂载覆盖·BD-31）：import 图只走到 ${MOUNT.moduleCount} 个模块，少于 src 下的实际量级——resolveSpec 或模块收集被改坏时，「宿主够不着胜者表」会变成常态，判据悄悄放弃全部判决。`);
      }
    }
    /* ---------------- 第 8 步属性级挂载覆盖（批次 BD-43 立项） */
    /**
     * BD-42 修的那条缺陷为什么会被看见，纯属运气：styles.css 里恰好还有一条**同名同属性
     * 异值**的声明，撞上「同键异值」才进了判决。同一个形状换个维度就完全隐身——
     * 「这边有这个键、那边根本没有这个键」不构成同键，任何判据都不响。
     *
     * 本判据补的就是这一维：一个**裸类** c 的属性 p 只在块级表 T 里声明（任何静态表都没有 p），
     * 而 c 有宿主 H——若**存在一条把 H 载起来的路，整条路上没人加载过任何一张声明 (c,p) 的表**，
     * 那么走那条路渲染时 p 一个像素都不画。p 在别的页面上照常生效，所以这不是死声明：
     * 是「外观取决于用户这次会话先点过什么」的活缺陷（BD-42 的 4px↔6px 就是这个）。
     *
     * 三条口径是这一批踩出来的，别再改回去：
     *   ① 不能用「H 自己沿 import 往外走够不到 T」当缺席——本仓惯例是子组件的样式由挂载它的
     *      页面 import（哨兵④ 的 .history-modal 正是这形状）。那么算是 564 条假账。
     *   ② 不能用「T 的下游闭包」算覆盖——那算的是「谁会跟着 T 一起被载」，方向反了。
     *   ③ 按**加载路**枚举：入口 = main.tsx + 全部 import() 的目标；入口 e 的**静态闭包**
     *      （只走静态边 + 桶文件 re-export 边）= 走 e 进来必然在场的一切。这与构建产物实测一致：
     *      Vite 把每个动态 chunk 的 CSS 依赖打进它自己的 preload deps 数组，逐条读产物
     *      （__vite__mapDeps 的表 + 每个 lazy() 调用点的那串下标）实测：
     *        GlobalCardLibraryPage → 带 cards-local-Cg3UUoe2.css
     *        CardsPage            → 带（同上，另一处 lazy 点）
     *        WritingDesk          → **不带**（它是 CreationProjectsPage chunk 内部再 lazy 的）
     *        CreationProjectsPage → 也不带（本判据的三条路里没有它，别把这两个名字搞混）
     *      判据算出「CardDynamicFields 有 3 条载入路、其中 WritingDesk 那条不带这张表」，
     *      与产物逐字对得上——本判据唯一的账上条目就是这个差别。
     *   失效方向：静态图证不了运行时的组件树，所以这条**只登记、不判死**（同 BD-25 那 6 条材质
     *   登记的立场），且只在「一条带表的路都找不到」时才往宽里报——宁多登记一条良性，
     *   也不能把活样式判成可删。多出来的那条用③的入口枚举兜住：任何一条路带表就不报。
     */
    {
      // 账：宿主 ⑅ 块级表 ⑅ 选择器文本 ⑅ 属性 ⑅ @上下文。不含行号（同 DEAD_LEDGER 的理由）。
      // 重钉的唯一入口是 DESIGN_TOKEN_DUMP_ABSENCE=1，不许手写。
      //
      // **BD-44 还清：账 8 → 0。** 那 8 条是同一条缺陷的 8 个属性（原档全文见本仓库
      // git blame / BD-43 提交说明）：CardDynamicFields.tsx 的附件说明行样式只在
      // cards-local.css:137 声明，而这个共享组件的三条载入路里「写作台 → 速查面板」那条
      // 整路没人 import 过那张表 ⇒ 同一行 markup 的外观取决于这次会话先点没点过设定卡
      // （实测 A：12px/18px 灰字、1px dashed、6px 圆角、8px 10px；B：16px/24px 正文色、
      //  无边框无底色无内边距）。修法是本批选的那条：**组件自己带上这张表**
      // （CardDynamicFields.tsx 顶部 import cards-local.css），单一真相、不动发射顺序
      // （仍静态 4 张 + 块级 16 张）、不进后代规则那份第二真相。
      // 预检实测过这条路的代价：cards-local 全部 86 条选择器里，在写作台那条路的静态闭包
      // 上「词面够得着」的只有 1 条，就是 .cards-attachment-note 本身——也就是说除了我们要
      // 修的那一条，这张表落到写作台上不产生任何新作用面。像素按截图规矩另核（见提交说明）。
      //
      // 为什么这条账**清零之后**判据仍然不能撤：它测的是「载入路带不带表」这一维，与
      // DEAD_LEDGER（同键异值）是两个口径，BD-43 立它就是因为同键异值看不见这一维。
      // 清零后判据的活体证据改由 benign（判过但放过）名单提供，见下面自检⑤⑥。
      //
      // 原档留下的四条教训（跟这批的口径绑在一起，删了会被重新踩一遍）：
      //   ① 八条里 margin 那条**两侧实测都是 0px**——Tailwind preflight 的
      //     `… p, pre {margin:0}` 同值兜住了，所以 cards-local 那条 margin: 0 本身是冗余声明。
      //     判据登记它仍然对（判的是「这张表在那条路上不在场」这个事实），但别把八条都说成「会漂」。
      //   ② 两侧 noteRect 都是 319×72，**高度相等不是没漂**：A = 3×18 行高 + 内边距 16 + 边框 2，
      //     B = 3×24。行高一档之差正好被内边距+边框补齐——相等的高度可以来自完全不同的算法。
      //     （那两式是拿实测 72/18/24/16/2 反推的、两侧自洽，行数没单独量过。）
      //   ③ 「把表 import 进来会牵连一堆裸类」这个担心，词面看着成立、判据上不成立：
      //     BackgroundPage.tsx:136 的 `confirming`、:83 的 `cards-search` 在 cards-local 里只出现在
      //     复合/后代选择器（`.cards-icon-btn.confirming` 等）里，那个页面不带那些祖先类 ⇒ 不落地。
      //     与 BD-40 那个 StatsPage 词面陷阱同一课：**判据是判据，grep 不算数**。
      //   ④ 我先前断言「组件自己 import 会把这张表推进 main.tsx 的静态闭包、发射顺序整体换位」
      //     ——**错的**，实测推翻：App.tsx 每个页面都是 lazy()，GlobalCardLibraryPage 自己就是动态
      //     入口，它下面的一切都不在 main.tsx 的静态闭包里。加那行 import 后守卫仍报「静态 4 张 /
      //     块级 16 张」。想当然的爆炸半径不是不还债的理由，先跑一遍再写。
      const ABSENCE_LEDGER = new Set([
      ]);
      const { entryStatic, hostEntries } = MOUNT;
      // 裸类索引：cls -> Map(`${rel}\t${ctx}` -> Set(prop))，从已建好的 sheets 里取
      // （sheets 的键是 ctx ⑅ 选择器文本 ⑅ 属性；只收「选择器文本等于一个裸类」的那支）
      const propsByBare = new Map();
      for (const [rel, m] of sheets) {
        for (const [k, list] of m) {
          const [ctx, sel, prop] = k.split("\t");
          if (!/^\.-?[_a-zA-Z][\w-]*$/.test(sel)) continue;
          const cls = sel.slice(1);
          if (!propsByBare.has(cls)) propsByBare.set(cls, new Map());
          const per = propsByBare.get(cls);
          const pk = `${rel}\t${ctx}`;
          if (!per.has(pk)) per.set(pk, new Set());
          per.get(pk).add(prop);
        }
      }
      const absence = new Map();
      // 「判过但放过」的名单（每条路都带表 = 良性）。BD-44 还清账之后 absence 为空，
      // 这一份就是判据仍在做功的活体证据；自检⑧ 钉它非空、且 .history-modal 那族在其中。
      const benign = new Set();
      for (const [cls, per] of propsByBare) {
        const rels = [...new Set([...per.keys()].map((k) => k.split("\t")[0]))];
        if (!rels.some((r) => EMISSION.lazy.has(r))) continue; // 没有块级来源，谈不上挂载覆盖
        const eagerProps = new Set();
        for (const [k, ps] of per) if (!EMISSION.lazy.has(k.split("\t")[0])) for (const p of ps) eagerProps.add(p);
        const re = clsTokenRe(cls);
        const hosts = [...new Set([...MOUNT.texts.entries()].filter(([, t]) => re.test(t)).map(([h]) => h))];
        if (!hosts.length) continue; // 查无宿主 = 幻影族另判
        for (const [k, ps] of per) {
          const [rel, ctx] = k.split("\t");
          if (!EMISSION.lazy.has(rel)) continue;
          for (const prop of ps) {
            if (eagerProps.has(prop)) continue; // 静态表也声明它 ⇒ 永远在场
            const declRels = [...per.entries()].filter(([, s]) => s.has(prop)).map(([kk]) => kk.split("\t")[0]);
            for (const h of hosts) {
              const es = [...(hostEntries.get(h) || [])];
              if (!es.length) continue; // 没有任何入口载得起它 ⇒ 孤儿模块，幻影族另判
              // 缺陷 = 存在一条载得起宿主、整条却不带任何声明表的路（走那条路渲染时属性缺席，
              // 也就是「同一元素、外观取决于先点过什么」——BD-42 的靶子就是这个漂移）。
              // ⚠ 别改成「每条路都不带才报」：那恰好把漂移漏掉，只会剩「这行 markup 全仓
              // 都画不出来」这一族（另一回事，归 markup 批），漂移反而没人管了。
              const lacking = es.filter((e) => !declRels.some((r) => entryStatic.get(e).has(r)));
              if (!lacking.length) {
                // 每条路都带表 ⇒ 任何会话里都在场。记进「放过名单」：本批判据还清账之后 absence
                // 恒为空，「判不出」与「判不动」在账面上长得一模一样，所以判据**确实在跑**这件事
                // 需要一个正面的活体证据，不能只靠缺席条数。
                benign.add(`${h}\t${rel}\t${"." + cls}\t${prop}\t${ctx || "—"}`);
                continue;
              }
              const key = `${h}\t${rel}\t${"." + cls}\t${prop}\t${ctx || "—"}`;
              if (!absence.has(key)) absence.set(key, { h, rel, cls, prop, ctx, declRels, es, lacking });
            }
          }
        }
      }
      const aMiss = [...absence.keys()].filter((k) => !ABSENCE_LEDGER.has(k));
      const aStale = [...ABSENCE_LEDGER].filter((k) => !absence.has(k));
      // 唯一允许的钉账入口（同 DEAD_LEDGER 那条纪律：账由实测生成，不许手写）
      if (process.env.DESIGN_TOKEN_DUMP_ABSENCE === "1") {
        for (const k of absence.keys()) {
          const x = absence.get(k);
          console.log(`    ${JSON.stringify(k)}, // ${x.h} ← ${x.rel} 的 .${x.cls} {${x.prop}} @${x.ctx || "根"}；${x.es.length} 条载入路里 ${x.lacking.length} 条不带`);
        }
        console.log(`${TAG} [DESIGN_TOKEN_DUMP_ABSENCE] 属性级挂载缺席 ${absence.size} 条（上面即账，逐字粘进 ABSENCE_LEDGER）`);
      }
      if (aMiss.length || aStale.length) {
        const show = (k) => k.replace(/\t/g, " ⑅ ");
        const parts = [];
        if (aMiss.length)
          parts.push(
            `新增 ${aMiss.length} 条「属性只在块级表声明、而有载入路载得起宿主却整条不带那张表」不在账上（现存 ${absence.size} 条）：${aMiss.slice(0, 3).map(show).join(" ｜ ")}` +
              `\n  这条的语义是**活缺陷**而不是死声明：属性在它自己的页面上在画，只有沿那条路渲染时整块缺席（外观随导航顺序变；BD-42 实测过 4px↔6px 的同形状漂移）。` +
              `\n  先查宿主在不在 main.tsx 的**静态**闭包里（第 8 步的 entryStatic 自己就能核，别猜）：` +
              `\n  · 在 ⇒ 让宿主 import 那张表会把它变成静态表、发射顺序整体换位，必须连 DEAD_LEDGER / WANT_ORDER / 本账一起重算；` +
              `\n  · 不在（宿主只经 lazy() 可达，BD-44 那支）⇒ import 不动顺序（实测仍「静态 4 张 / 块级 16 张」），` +
              `代价只是那张表的裸类会在这条路上落地，用选择器可达性逐条数一遍。` +
              `\n  其余两条路：把这条属性搬进静态表，或把类名拆开各用各的（BD-42 的修法）。确实判得对又本批还不完的，才允许进账，并写明为什么本批不还。`
          );
        if (aStale.length)
          parts.push(`账上 ${aStale.length} 条现在扫不到了（还了没改账，或那张表换了挂载路）：${aStale.slice(0, 3).map(show).join(" ｜ ")}`);
        fail(`第 8 步属性级挂载覆盖账与实测不符（${parts.join("\n  ")}`);
      }
      // 判据自检⑤⑥：这条的失效方向是「把良性的父挂载全报成缺陷」，所以用一对真实宿主
      // 钉住它的两种结局，必须**一判一跳**（和哨兵④同一手法、同一份 import 图）：
      //   · .cards-attachment-note × cards-local.css：宿主 CardDynamicFields.tsx 被写作台
      //     那条路静态带进来，而那条路上没有任何模块 import 过 cards-local.css
      //     ⇒ 必须判出缺席（本账唯一的条目，运行时实测过：走写作台时那张表整张不在
      //       document.styleSheets 里，note 的 8 个属性全部掉回默认值）。
      //   · .history-modal × history-local.css：宿主是历史页的三个子对话框，它们只能经
      //     HistoryPage 那条路进来，而 HistoryPage 自己 import 了那张表 ⇒ 必须判良性。
      //     ⚠ 这条同时钉住「存在一条不带表的路」与「每条路都不带」的区别：对话框宿主
      //     同样够不到那张表的 import 链（BD-31 的并集口径里它是不覆盖的），本判据按
      //     载入路判就必须放过——两个口径各自的用处正差在这一对上。
      {
        // 自检⑤（BD-44 重锚）：BD-43 那版钉的是「.cards-attachment-note 判出缺席 / .history-modal
        // 判良性」这一对**一判一跳**。本批把那条真缺陷合法还掉了，k1 从此不在缺席里——牙按设计
        // 咬人（红话正是「判出缺席=false … 不是**一判一跳**」），现在换锚。
        // ⚠ 不能再随便挑一条「判得出的缺席」顶上：账已清零，任何一条缺席都是**新缺陷**，
        // 拿新缺陷当锚等于把人质锁进哨兵（BD-41 为这事把两颗死账牙从账上解绑，同一课）。
        // 所以⑤ 改钉**良性那一侧**：.history-modal 这一族必须在「判过但放过」的名单里——
        // 它同时钉住两个方向：豁免逻辑还在跑（没被改成「只要有一条不带就报」），
        // 以及那条「按载入路判」的口径没漂回 BD-31 的并集口径（并集口径下 .history-modal 不覆盖）。
        const benignKeys = [...benign];
        const histRe = /\.history-modal\t/;
        if (!benignKeys.some((k) => histRe.test(k))) {
          fail(
            `第 8 步属性级挂载覆盖判据退化（BD-44 自检⑤）：.history-modal 一族不在「判过但放过」的名单里` +
              `（名单现在 ${benign.size} 条）。这条判据的良性侧全靠「每条路都带表就放过」那层豁免撑着，` +
              `豁免塌了会把本仓几十处「子组件的样式由挂载它的页面带进来」全记成缺陷（BD-43 实测饱和到 1576 条）；` +
              `豁免整个没了则是口径漂回 BD-31 的并集口径。两种都是这条判据最危险的退化方向。`
          );
        }
        // 自检⑥（BD-43 立、BD-44 收紧）：入口数量闸。<10 那条只能挡住「动态边解析彻底坏掉」，
        // 挡不住账清零后「判据不再做功」这种静默失效，所以补一道**放过名单的下限**：
        // 全站块级表的裸类属性 × 宿主 这个笛卡尔积本来就远大于 100，名单空掉意味着扫描本身断了。
        if (entryStatic.size < 10) {
          fail(`第 8 步属性级挂载覆盖判据退化（BD-43 自检⑥）：只枚举出 ${entryStatic.size} 个载入入口，少于 src 下 import() 的量级——动态边解析坏掉时「载得起它的路」会被算少，本判据整体失灵。`);
        }
        if (benign.size < 100) {
          fail(`第 8 步属性级挂载覆盖判据退化（BD-44 自检⑥·加强）：「判过但放过」的名单只有 ${benign.size} 条，低于全站块级表裸属性 × 宿主 的量级下限 100——缺席账清零之后，这条名单是判据仍在做功的唯一活体证据；它掉下来说明扫描本身（sheets 索引 / 宿主发现 / 载入路枚举）断了，而不是债还干净了。`);
        }
        // 自检⑦（BD-43 补钉）：桶文件 re-export 边**必须算静态边**，而且这条要用图上的事实测，
        // 不能用判决结果测——实测踩过：把 sj.add(r) 那行删掉，本批 8 条一条不少、守卫全绿，
        // hostEntries 却从 231 个模块掉到 204 个（掉 27）。少掉的那些宿主走的是 `if (!es.length) continue;`
        // 「无人载得起 ⇒ 另判」那一支，等于判据对这 27 个模块当场闭嘴，而这正是本批判据唯一的失效方向。
        // 所以钉三个**只能经桶文件被载起**的模块：桶边断掉它们就从 hostEntries 消失，红。
        // ⚠ 这三条不是账、不随还债消失；若某批把某个模块改成被直接 import（桶边不再是它唯一的路），
        //   这条不会红（我们只查「有没有路」），换锚时才需要动——换锚手续同 BD-42 那批的记录。
        for (const barrelOnly of [
          "src/features/creation/inbox/components/InboxConvertToCardDialog.tsx", // 经 inbox/components 的桶文件 re-export 载起
          "src/components/ui/FontPicker.tsx", // 经 components/ui 的桶文件 re-export 载起
          "src/stores/creation/card-slice.ts", // 经 stores/creation 的桶文件 re-export 载起
        ]) {
          if (!(hostEntries.get(barrelOnly)?.size || 0)) {
            fail(
              `第 8 步属性级挂载覆盖判据退化（BD-43 自检⑦）：${barrelOnly} 在 hostEntries 里查无载入路——` +
                `它只能经桶文件的 \`export * from\` 被载起，说明 sj 那条「re-export 也算静态边」的边断了。` +
                `这条断了不表现为错判，表现为「载得起宿主的路」被算少、\`if (!es.length) continue\` 把那批宿主整体放过（实测一次少 27 个模块）。`
            );
          }
        }
      }
      if (!aMiss.length && !aStale.length) {
        console.log(
          `${TAG} [第 8 步属性级挂载覆盖] 缺席 ${absence.size} 条${absence.size ? "已逐条钉账" : "（BD-44 还清账，判据不撤）"}；判过但放过 ${benign.size} 条（每条载入路都带表 = 良性，自检⑤⑥ 钉的就是这份名单还在动）；载入入口 ${entryStatic.size} 个、静态表 ${EMISSION.eager.length} 张、块级表 ${EMISSION.lazy.size} 张`
        );
      }
    }
    // 红了就别再打「0 条」——绿话与红话同时出现，等于守卫自己撒谎（几何分支那条老规矩）。
    if (!colorDead.length && !ledgerMiss.length && !ledgerStale.length) {
      console.log(
        `${TAG} [第 8 步颜色分支] 全站同键异值死色 0 条（BD-29 摘 95 拆 1 + BD-30 摘 20 拆 1，预算档已取消）；发射顺序由 main.tsx import 图推出：静态 ${EMISSION.eager.length} 张按行序、块级 ${EMISSION.lazy.size} 张在其后`
      );
      console.log(
        `${TAG} [第 8 步非颜色死声明] 同键异值 ${otherDead.length} 条已逐条钉账（BD-31 扩族首次实测，含 ${unjudgedBy.size} 条因挂载覆盖不成立而不判）；账与实测逐条比对，增删都要同步`
      );
    }
  }
}

/* ------------------------------------------------------------------ 批次 BD-22：MEASURE 不变量 */

/**
 * §3.3「稿纸 / 编辑器」那行第四列给的是行长 **42em**。它不是某一条声明能独立成立的值：
 * border-box 全局生效（styles.css `* { box-sizing: border-box }`），max-width **含**内边距
 * 与边框，于是「正文一行的墨迹 = 42 个 16px 汉字」这条不变量横跨三份文件的几组声明——
 *
 *   ① 稿纸正文 editorial-studio.css `.scene-editor-content`：
 *      `max-width: calc(42em + 64px)` 减自己的 `padding-inline: 32px`×2 = 672px = 正好 42em。
 *      要求**恰好**：真机量过换行按整字发生（672.02px=42 字、688.02px=43 字、656.02px=41 字），
 *      「多留半格」不是余量，是下一个字。
 *   ② ≤1180 的纸壳 styles.css `.scene-editor` `width: min(738px, …)`：纸壳那圈 1px 实线
 *      （editorial-studio.css `.scene-editor`）在 border-box 下吃 2px，738 − 2 = 736 才等于
 *      ①的边框盒上限。写 736 的「作者写死的一对」正是 BD-22 纠正的错觉——那 2px 从来不是留白。
 *      要求相等（死对不拆散）；宽屏 min(820px, …) 那档只要求 ≥（纸边是有意留的）。
 *   ③ 通读页 preview-local.css：`.preview-document` 是**滚动容器**，纸面 cap 的补偿加法
 *      （padding 34×2 + border 1×2 + 纵向滚动条 9）与 `.preview-chapter` 的 `max-width: 42em`
 *      配对——纸面只保证「窄到挤不动列」永远不发生，列自己钉规格。滚动条宽度的胜者、
 *      ≤900 档的 padding、打印档的清零**全在别的文件/别的分支**，光看本文件读不出这笔
 *      加法，所以必须实算：对每个状态算「cap − padding − border − (滚动容器 ? 滚动条 : 0)」，
 *      要求 ≥ 42em。
 *
 * 判据方向与失效剧本：①②改任何一个数字、③在别处动 padding/border/滚动条宽度而不挪补偿，
 * 都会把 42 字挤成 41——那种错 vitest 全绿、截图也看不出（只差一个字/行），只有实算看得见。
 * 结构性事实（选择器在哪份文件、main.tsx 的发射顺序、html/body 有没有 font-size）全部现读；
 * 唯一允许的字面量是 em 基准 16（App.tsx 的 `root.style.fontSize = scale×100%`，未缩放时
 * 100% = 浏览器默认 16px）——这条前提本身也有判据在守（见「em 基准」那条）。
 */
{
  const EM = 16;
  const MEASURE_EM = 42;
  const MEASURE_PX = MEASURE_EM * EM; // 672
  let mFails = 0;
  const mfail = (m) => {
    fail(`MEASURE 不变量：${m}`);
    mFails += 1;
  };

  const STY = "src/styles.css";
  const EDX = "src/styles/editorial-studio.css";
  const PREV = "src/features/creation/preview/preview-local.css";
  const asts = new Map();
  const sheet = (rel) => {
    if (!asts.has(rel)) asts.set(rel, postcss.parse(read(rel), { from: path.join(root, rel) }));
    return asts.get(rel);
  };

  // 找「选择器 + 属性」的声明。mediaHas === null 只看非媒体内的顶层声明；给了字符串则
  // 只看所在 @media 参数含该字符串的规则。返回按源码顺序，调用方取末位 = 发射胜者
  // （同特异度靠顺序，见第 8 步几何分支一节）；!important 另判。
  function finds(rel, selector, prop, mediaHas = null) {
    const hits = [];
    sheet(rel).walkRules((rule) => {
      if (!rule.selectors.some((s) => s.replace(/\s+/g, " ").trim() === selector)) return;
      const media = [];
      for (let p = rule.parent; p && p.type !== "root"; p = p.parent) if (p.type === "atrule") media.push(p.params);
      if (mediaHas === null) {
        if (media.length) return;
      } else if (!media.some((m) => m.includes(mediaHas))) {
        return;
      }
      rule.walkDecls(prop, (d) => hits.push({ value: d.value.trim(), important: !!d.important, line: rule.source?.start?.line ?? 0 }));
    });
    return hits;
  }
  // 该上下文里显式声明过就覆盖、没声明就沿用基表值——媒体查询正是这么工作的。
  function pick(rel, selector, prop, mediaHas) {
    const over = mediaHas ? finds(rel, selector, prop, mediaHas) : [];
    const base = finds(rel, selector, prop, null);
    const src = over.length ? over : base;
    if (!src.length) return null;
    const important = src.some((d) => d.important);
    const pool = important ? src.filter((d) => d.important) : src;
    return { value: pool[pool.length - 1].value, important };
  }
  const pxOnly = (v) => {
    const m = v.match(/^-?[\d.]+px$/);
    return m ? parseFloat(m[0]) : /^0$/.test(v) ? 0 : null;
  };
  // 只认本仓实际使用的写法；读不懂返回 null 让调用方红——判据退化成「看不见就当错」，
  // 不会「看不见就放行」。乘除法 calc、分件 padding-left/right、逻辑物理混写都走这条路。
  function calcParts(v) {
    const lit = pxOnly(v);
    if (lit !== null) return { em: 0, px: lit };
    const plain = v.match(/^([\d.]+)em$/);
    if (plain) return { em: parseFloat(plain[1]), px: 0 };
    const m = v.match(/^calc\((.+)\)$/);
    if (!m) return null;
    let em = 0;
    let px = 0;
    for (const t of m[1].split("+").map((x) => x.trim())) {
      const mm = t.match(/^([\d.]+)(em|px)$/);
      if (!mm) return null;
      if (mm[2] === "em") em += parseFloat(mm[1]);
      else px += parseFloat(mm[1]);
    }
    return { em, px };
  }
  const asTotal = (c) => (c === null ? null : c.em * EM + c.px);
  function horizontal(prop, value) {
    const parts = value.split(/\s+/);
    if (prop === "border") {
      if (/^0(px)?$/.test(parts[0]) && parts.length === 1) return 0;
      const m = value.match(/^([\d.]+)px\s+\S+\s/);
      return m ? parseFloat(m[1]) : null;
    }
    if (prop === "padding-inline") return parts.length === 1 ? pxOnly(parts[0]) : null;
    if (prop === "padding") {
      if (parts.length === 1) return pxOnly(parts[0]);
      if (parts.length >= 3) return pxOnly(parts[1]);
    }
    return null;
  }

  // —— ① 稿纸正文：cap 的 px 项必须**正好等于**自己的横向内边距，em 项必须是 42em。
  const contentCap = finds(EDX, ".scene-editor-content", "max-width");
  const contentPad = finds(EDX, ".scene-editor-content", "padding-inline");
  if (!contentCap.length || !contentPad.length) {
    mfail(`锚点缺失：editorial-studio.css 的 .scene-editor-content 读不到 max-width(${contentCap.length}) 或 padding-inline(${contentPad.length})——改名/挪家了请同步本判据，缺锚点不算通过`);
  } else {
    const cap = calcParts(contentCap[contentCap.length - 1].value);
    const pad = horizontal("padding-inline", contentPad[contentPad.length - 1].value);
    if (cap === null || pad === null) {
      mfail(`稿纸正文那对声明写法超出本判据的读法（max-width: "${contentCap.at(-1).value}" / padding-inline: "${contentPad.at(-1).value}"）——判据宁可红不可盲`);
    } else if (cap.em !== MEASURE_EM || cap.px !== pad * 2) {
      mfail(`稿纸正文 ${contentCap.at(-1).value}（editorial-studio.css:${contentCap.at(-1).line}）应等于 ${MEASURE_EM}em + 自身横向内边距 ${pad}×2：em 项 ${cap.em}、px 项 ${cap.px}。正文 = ${asTotal(cap) - pad * 2}px，42em 是 ${MEASURE_PX}px——每行字数就是换行字数，±半格都不是余量`);
    }
  }

  // —— ② 纸壳与正文是一对：≤1180 的 738 − 边框 2 = 736 = ①的边框盒；宽屏只要求 ≥。
  const border = pick(EDX, ".scene-editor", "border", null);
  const borderX = border === null ? null : horizontal("border", border.value);
  const capBorderBox = contentCap.length ? asTotal(calcParts(contentCap.at(-1).value)) : null;
  if (borderX === null || capBorderBox === null) {
    mfail(`锚点缺失/写法读不懂：.scene-editor 的 border（editorial-studio.css）或 .scene-editor-content 的 max-width——②没法算`);
  } else {
    const shellPairs = [
      ["≤1180", STY, "max-width: 1180px", "eq"],
      ["宽屏", STY, null, "ge"],
    ];
    for (const [tag, rel, mediaHas, mode] of shellPairs) {
      const w = pick(rel, ".scene-editor", "width", mediaHas);
      const m = w && w.value.match(/^min\(([\d.]+)px,\s*calc\(/);
      if (!m) {
        mfail(`${tag}纸壳那条读不懂（width: "${w ? w.value : "缺失"}"，期望 min(NNNpx, calc(…))）：738 这个数就是②的全部含义，写法换了请同步判据`);
        continue;
      }
      const inner = parseFloat(m[1]) - borderX * 2;
      if (mode === "eq" && inner !== capBorderBox) {
        mfail(`${tag}纸壳 ${m[1]} − 边框 ${borderX}×2 = ${inner}，而正文边框盒是 ${capBorderBox}——差的那 ${capBorderBox - inner}px 会把整行挤掉一个字（738 改回 736 正是这个形状：纸壳那圈 1px 实线不是留白）`);
      }
      if (mode === "ge" && inner < capBorderBox) {
        mfail(`${tag}纸壳内宽 ${inner} < 正文边框盒 ${capBorderBox}：宽屏纸边被吃到比正文上限还窄，①的 42em 落不了地`);
      }
    }
  }

  // —— em 基准：本判据所有 16 都押在「html/body 没定过 font-size」上，这条前提自己也要守。
  {
    const setters = [];
    for (const rel of [STY, EDX]) {
      sheet(rel).walkRules((rule) => {
        const norm = rule.selector.replace(/\s+/g, " ").trim();
        if (norm !== "html" && norm !== "body" && norm !== "html, body, #root" && norm !== "html,body,#root") return;
        const media = [];
        for (let p = rule.parent; p && p.type !== "root"; p = p.parent) if (p.type === "atrule") media.push(p.params);
        if (media.length) return;
        rule.walkDecls("font-size", (d) => setters.push(`${rel}:${d.source?.start?.line ?? 0} ${d.value}`));
      });
    }
    if (setters.length) mfail(`em 基准不再是 16：${setters.join("、")} 给 html/body 定了 font-size（App.tsx 只动 <html> 的百分比），42em=${MEASURE_PX}px 这套实算全部作废，请连同三份注释一起重算`);
  }

  // —— 滚动条胜者：两份全局表按 main.tsx 的发射顺序取 `::-webkit-scrollbar` 的 width 末位。
  function scrollbarPx() {
    const mainSrc = read("src", "app", "main.tsx");
    const specs = [STY, EDX].map((rel) => `import "${rel.replace("src/", "@/")}"`);
    const idx = specs.map((s) => mainSrc.indexOf(s));
    if (idx.some((i) => i < 0)) {
      mfail(`锚点缺失：main.tsx 里读不到 ${specs.filter((s) => mainSrc.indexOf(s) < 0).join("、")} 的 import——滚动条胜者判定依赖发射顺序，顺序来源没了必须红`);
      return null;
    }
    if (idx[1] < idx[0]) {
      mfail(`前提漂移：main.tsx 里 editorial-studio.css 不再排在 styles.css 之后——9px 那份盖不掉 10px 了，③的补偿加法与第 8 步几何分支的胜者表都要重算`);
      return null;
    }
    let last = null;
    for (const rel of [STY, EDX]) {
      const hits = finds(rel, "::-webkit-scrollbar", "width");
      if (hits.length) last = { rel, value: hits[hits.length - 1].value };
    }
    if (!last) {
      mfail("两份全局表都读不到 ::-webkit-scrollbar 的 width——③的滚动条补偿项失去了来源");
      return null;
    }
    const v = pxOnly(last.value);
    if (v === null) {
      mfail(`::-webkit-scrollbar width 写法读不懂（${last.rel}: "${last.value}"）`);
      return null;
    }
    return v;
  }
  const SB = scrollbarPx();

  // —— ③ 通读页：列钉 42em，纸面在三态下都必须装得下列。
  {
    const docCapHits = finds(PREV, ".preview-document", "max-width");
    const chapHits = finds(PREV, ".preview-chapter", "max-width");
    if (!docCapHits.length || !chapHits.length) {
      mfail(`锚点缺失：preview-local.css 读不到 .preview-document 的 max-width(${docCapHits.length}) 或 .preview-chapter 的 max-width(${chapHits.length})——42em 的落点没了`);
    } else {
      const chap = calcParts(chapHits[chapHits.length - 1].value);
      if (chap === null || chap.em !== MEASURE_EM || chap.px !== 0) {
        mfail(`.preview-chapter 应恰好是 ${MEASURE_EM}em 纯 em（读到 "${chapHits.at(-1).value}"）——规格值必须留在列自己的声明里，换成 px 字面量等于把 42 这个数字的理由丢掉`);
      }
      const baseCap = calcParts(docCapHits[docCapHits.length - 1].value);
      const states = [
        ["常态", null],
        ["≤900", "max-width: 900px"],
        ["打印", "print"],
      ];
      for (const [tag, mediaHas] of states) {
        if (SB === null) break; // 已经红过了，不再逐态刷
        const capHit = pick(PREV, ".preview-document", "max-width", mediaHas);
        const padHit = pick(PREV, ".preview-document", "padding", mediaHas) ?? pick(PREV, ".preview-document", "padding-inline", mediaHas);
        const bordHit = pick(PREV, ".preview-document", "border", mediaHas);
        const ovfHit = pick(PREV, ".preview-document", "overflow", mediaHas);
        const padProp = pick(PREV, ".preview-document", "padding-inline", mediaHas) ? "padding-inline" : "padding";
        const cap = capHit ? asTotal(calcParts(capHit.value)) : null;
        const pad = padHit ? horizontal(padProp, padHit.value) : null;
        const bx = bordHit ? horizontal("border", bordHit.value) : null;
        if (cap === null || pad === null || bx === null || !ovfHit) {
          mfail(`通读页「${tag}」态读不懂（cap ${capHit?.value ?? "缺"} / padding ${padHit?.value ?? "缺"} / border ${bordHit?.value ?? "缺"} / overflow ${ovfHit?.value ?? "缺"}）——③的加法全靠这三态实读，判据宁可红不可盲`);
          continue;
        }
        const scrolls = /auto|scroll/.test(ovfHit.value);
        const avail = cap - pad * 2 - bx * 2 - (scrolls ? SB : 0);
        if (avail < MEASURE_PX) {
          mfail(`通读页「${tag}」态纸面可用宽 ${avail}px < 列的 42em(${MEASURE_PX}px)：列会被纸面挤窄、整行掉到 41 字。这笔加法的三项（padding ${pad}×2、border ${bx}×2、滚动条 ${scrolls ? `${SB}（胜者，见全局表 ::-webkit-scrollbar）` : "0（本态不滚动）"}）任何一处改动都要挪 .preview-document 的 cap——注释里写明的就是这个漂移`);
        } else if (tag === "常态" && baseCap && Math.abs(avail - MEASURE_PX) > 24) {
          // 常态还要求「别过度补偿」：cap 里 42em 之外只该是补偿项，多出一整档说明有人
          // 挪了 padding 却忘了减回来——纸面会比正文宽出一截谁也没定的空白。
          mfail(`通读页常态可用宽 ${avail}px 比 42em 多出 ${avail - MEASURE_PX}px（> 24px，一档换行都富余出来）：.preview-document 的补偿加法（cap ${baseCap.px}px）与真实 padding/border/滚动条不再逐项对应，请照实重写那条 calc`);
        }
      }
    }
  }

  if (mFails === 0) {
    console.log(`${TAG} [MEASURE 不变量] 稿纸 calc(42em+64px)−32×2=672、≤1180 纸壳 738−边框2=736、通读三态可用宽 ≥ 42em（滚动条按 main.tsx 发射顺序实读胜者）——实算通过`);
  }
}

/* ------------------------------------------------------------------ 批次 BD-23：SEAL 不变量 */

/**
 * §3.2 那八档是给**文字**定的，仓里另有三处不是文字、是「方框里刻一个字的图形」：
 *   .desktop-brand-mark   38×38 印章（editorial-studio.css 基础档 + styles.css @media≤920）
 *   .desktop-titlebar-mark 18×18 同一枚印的小形态（editorial-studio.css）
 *   .library-cover-glyph   38×50 书脊块里的白字（editorial-studio.css）
 * BD-11/BD-17 两次把这四处冻成「品牌字形族」，冻的理由是「字号是为那个盒子选的，套 §3.2 的
 * 16/20 会让字撑不满盒子或**溢出盒子**」。批次 BD-23 在最终产物上真机量过，那半句是错的
 * ——20 从来没溢出过（着墨 18×21，环内净空 30×30）。既然「装不下」是假的，冻结前提就没了，
 * 本批把三处离刻度值收回八档（18→20 ×2、15→16），并把「注释里许诺过的关系」里**唯一那条
 * 不含口味**的做成判据：
 *
 *   ① 印章的两档必须同值。基础档与 ≤920 窄屏档同值不是巧合：窄屏那条只把「带文字的横排」
 *      改成「居中方印」（重复同样的 38×38、只换圆角），它从来不是一档压缩（对比 BD-9 的
 *      project-nav：那里窄屏比基础低一档）。改一处忘另一处 = 窄屏印面突然换尺寸。
 *   ② 字号必须装得下盒子去掉边框与 inset 环之后的净空（inset spread 从内边框边向内画，
 *      所以净空 = 宽 − 2×border − 2×最大 spread）。汉字 advance = 1em（这条与字体无关，
 *      所有 CJK 字体都给 1em 方格），字号大于净空就是字压到环上。盒宽、边框、环宽**任何一处**
 *      收窄而不挪字号，都会在这里红——这正是本批新造出来的风险：值已经落到刻度上，
 *      下一批人最容易顺手挪的就是那只盒子。
 *
 * ⚠ 本判据刻意**不做**「两枚印字号占净空的比例要同族」那条。写它的时候觉得理所当然，
 *   复看才发现它是循环论证：16 与 20 都装得下、都在八档上，唯一能把 16 判红的就是这条
 *   比例带，而这条带宽是本批自己挑的——等于把「我选了 20」写成法律，下一批人带着新证据
 *   改回 16 反而要先来拆判据。比例只作为**选档时的证据**留在两处声明的注释里
 *   （titlebar 68.75% ↔ brand 66.67%，16 则 53.33%），这里把它当读数打印出来，不判红。
 *   本仓这条错法不是第一次：BD-22 那三处「预测当实测」、批次 A 在死声明上算对比度，
 *   都是同一个形状——先把结论写进判据，再让判据替结论背书。
 *
 * 判据方向与失效剧本：①②是跨文件/跨声明的算术，vitest 与截图都看不出（一次只差几 px），
 * 靠的是现读声明。结构性事实（选择器在哪份文件、盒子多大、环多厚）全部现读，
 * 判据读不懂就红（宁可红不可盲）。
 * ⚠ 看不见的方向也要说清：本判据只认**裸 `.desktop-brand-mark` 选择器**的声明。若有人改用
 *   带祖先前缀的选择器（如 `.desktop-sidebar--rail .desktop-brand-mark`）压小盒子，这里读不到
 *   那个宽度，②就瞎——那种覆盖同时会被「第 8 步几何分支」那族判据盯住，不在此判据职责内。
 */
{
  const STY = "src/styles.css";
  const EDX = "src/styles/editorial-studio.css";
  let sFails = 0;
  const sfail = (m) => {
    fail(`SEAL 不变量：${m}`);
    sFails += 1;
  };

  const sealAsts = new Map();
  const sealSheet = (rel) => {
    if (!sealAsts.has(rel)) sealAsts.set(rel, postcss.parse(read(rel), { from: path.join(root, rel) }));
    return sealAsts.get(rel);
  };
  // 返回**声明行**（不是规则起始行）——本仓 glyph/投影台账的口径，见 BD-22 的说明。
  function sealFinds(rel, selector, prop, mediaHas = null) {
    const hits = [];
    sealSheet(rel).walkRules((rule) => {
      if (!rule.selectors.some((s) => s.replace(/\s+/g, " ").trim() === selector)) return;
      const media = [];
      for (let p = rule.parent; p && p.type !== "root"; p = p.parent) if (p.type === "atrule") media.push(p.params);
      if (mediaHas === null) {
        if (media.length) return;
      } else if (!media.some((m) => m.includes(mediaHas))) {
        return;
      }
      rule.walkDecls(prop, (d) => hits.push({ value: d.value.trim(), line: d.source?.start?.line ?? 0 }));
    });
    return hits;
  }
  const sealPx = (v) => {
    const m = /^(-?[\d.]+)(px)?$/.exec((v || "").trim());
    return m ? parseFloat(m[1]) : null;
  };
  const sealBorderX = (v) => {
    const m = (v || "").match(/^([\d.]+)px\s+\S+/);
    return m ? parseFloat(m[1]) : /^0(px)?$/.test((v || "").trim()) ? 0 : null;
  };
  // 逐层拆 box-shadow 的逗号（color-mix(...) 里的逗号不算分隔），取 inset 层的 spread。
  function insetSpreads(value) {
    const parts = [];
    let depth = 0;
    let cur = "";
    for (const ch of value) {
      if (ch === "(") depth += 1;
      else if (ch === ")") depth -= 1;
      if (ch === "," && depth === 0) {
        parts.push(cur);
        cur = "";
        continue;
      }
      cur += ch;
    }
    parts.push(cur);
    const out = [];
    for (const raw of parts) {
      const p = raw.trim();
      if (!/(^|\s)inset(\s|$)/.test(p)) continue;
      // 作者写法里 inset 在最前、四个长度在色值之前；长度可无单位（0）。
      const nums = (p.match(/-?(?:\d+\.?\d*|\.\d+)(?:px)?/g) || []).map(parseFloat);
      out.push(nums.length >= 4 ? nums[3] : 0);
    }
    return out;
  }

  const brandBase = {
    fs: sealFinds(EDX, ".desktop-brand-mark", "font-size"),
    w: sealFinds(EDX, ".desktop-brand-mark", "width"),
    border: sealFinds(EDX, ".desktop-brand-mark", "border"),
    shadow: sealFinds(EDX, ".desktop-brand-mark", "box-shadow"),
  };
  const brandNarrow = {
    fs: sealFinds(STY, ".desktop-brand-mark", "font-size", "max-width: 920px"),
    w: sealFinds(STY, ".desktop-brand-mark", "width", "max-width: 920px"),
    border: sealFinds(STY, ".desktop-brand-mark", "border", "max-width: 920px"),
    shadow: sealFinds(STY, ".desktop-brand-mark", "box-shadow", "max-width: 920px"),
  };
  const tb = {
    fs: sealFinds(EDX, ".desktop-titlebar-mark", "font-size"),
    w: sealFinds(EDX, ".desktop-titlebar-mark", "width"),
    border: sealFinds(EDX, ".desktop-titlebar-mark", "border"),
    shadow: sealFinds(EDX, ".desktop-titlebar-mark", "box-shadow"),
  };
  // 必需锚点：基础档的字号/盒宽，小印的字号/盒宽。边框与 box-shadow 允许缺席
  // （缺席 = 没有那圈装饰，净空按盒宽算，下面的读法已覆盖）。
  // 窄屏档的字号/盒宽**允许整条缺席**——那等于把 ≤920 的版式覆盖删掉、全部继承基础档，
  // ①②照样成立；但「只读到一半」不行：那正是选择器或属性名写错的症状，而它和真删掉
  // 表现一模一样，所以单独判。
  const missing = [];
  for (const [name, hit] of [
    ["印章基础档.font-size", brandBase.fs],
    ["印章基础档.width", brandBase.w],
    ["小印 titlebar.font-size", tb.fs],
    ["小印 titlebar.width", tb.w],
  ]) {
    if (!hit.length) missing.push(name);
  }
  if (brandNarrow.fs.length !== brandNarrow.w.length) {
    sfail(`锚点漂移：≤920 那条 .desktop-brand-mark 里 font-size(${brandNarrow.fs.length} 条) 与 width(${brandNarrow.w.length} 条) 只读到一半（${STY}）。要么这条规则真的拆了（那就两份一起没了），要么选择器/属性名写错了——后者会让本判据安静地瞎掉`);
  }
  if (missing.length) {
    sfail(`锚点缺失：${missing.join("、")} 读不到——印章一族的选择器/媒体分支挪家或改名了，请同步本判据（缺锚点不算通过）`);
  } else {
    // 窄屏态：两份声明都在就用它们，都不在就继承基础档（媒体查询正是这么工作的）。
    const nFsd = brandNarrow.fs.at(-1);
    const nWd = brandNarrow.w.at(-1);
    const baseFs = parseFloat(brandBase.fs.at(-1).value);
    const narrowFs = nFsd ? parseFloat(nFsd.value) : baseFs;
    const narrowWhere = nFsd ? `${STY}:${nFsd.line}` : `${EDX}:${brandBase.fs.at(-1).line}（≤920 无覆盖，继承）`;

    // —— ① 两档同值（窄屏若整条不再覆盖，就是继承基础档，同样算同值）
    if (baseFs !== narrowFs) {
      sfail(`①两档不同值：基础档 ${brandBase.fs.at(-1).value}（${EDX}:${brandBase.fs.at(-1).line}）vs 窄屏档 ${nFsd ? `${nFsd.value}（${STY}:${nFsd.line}）` : "无覆盖但盒宽仍在窄屏改过"}。窄屏那条不是压缩档（它只改版式、盒子宽高逐字重复基础档），要么一起改、要么删掉窄屏那条 font-size 让它继承基础档`);
    }

    // —— ② 字号 ≤ 环内净空（印章常态、印章 ≤920、小印）
    // 每一态各算自己的四个量：窄屏那条若覆盖了 border/box-shadow，就得用覆盖值；
    // 没覆盖就沿用基础档——媒体查询就是这么工作的（与 MEASURE 的 pick 同语义）。
    const narrowOr = (narrowHit, baseHit) => (narrowHit ? narrowHit : baseHit);
    const shadowOf = (hit) => (hit ? insetSpreads(hit.value) : []);
    const bxOf = (hit) => (hit ? sealBorderX(hit.value) : 0);
    const ringOf = (hit) => {
      const s = shadowOf(hit);
      return s.length ? Math.max(...s) : 0;
    };
    const baseBx = bxOf(brandBase.border.at(-1));
    const baseRing = ringOf(brandBase.shadow.at(-1));
    const narrowBx = bxOf(narrowOr(brandNarrow.border.at(-1), brandBase.border.at(-1)));
    const narrowRing = ringOf(narrowOr(brandNarrow.shadow.at(-1), brandBase.shadow.at(-1)));
    const tbBx = bxOf(tb.border.at(-1));
    const tbRing = ringOf(tb.shadow.at(-1));
    const tbFsRaw = parseFloat(tb.fs.at(-1).value);
    const tbW = sealPx(tb.w.at(-1).value);
    if (baseBx === null || narrowBx === null || tbBx === null || tbW === null) {
      sfail(`②读不懂盒子的边框（印章 "${brandBase.border.at(-1)?.value ?? "无"}"、小印 "${tb.border.at(-1)?.value ?? "无"}"）——判据宁可红不可盲，写法换了请同步本判据`);
    } else {
      // [标签, 盒宽声明, 边框, 环, 字号, 字号声明出处]
      const seals = [
        ["印章常态", brandBase.w.at(-1), baseBx, baseRing, baseFs, `${EDX}:${brandBase.fs.at(-1).line}`],
        ["印章≤920", narrowOr(nWd, brandBase.w.at(-1)), narrowBx, narrowRing, narrowFs, narrowWhere],
        ["小印 titlebar", tb.w.at(-1), tbBx, tbRing, tbFsRaw, `${EDX}:${tb.fs.at(-1).line}`],
      ];
      // 净空按标签存，不用数组下标——上面某个态读不懂盒宽时会 continue，下标会整体错位。
      const clearOf = {};
      for (const [tag, wHit, b, r, fs, where] of seals) {
        const w = sealPx(wHit.value);
        if (w === null) {
          sfail(`②读不懂${tag}的盒宽（"${wHit.value}" @${wHit.line}）——盒子的宽高写法换了请同步判据，读不懂不算通过`);
          continue;
        }
        const clear = w - b * 2 - r * 2;
        clearOf[tag] = clear;
        if (fs > clear) {
          sfail(`②${tag}装不下：字号 ${fs} > 环内净空 ${clear}（盒宽 ${w} − 边框 ${b}×2 − inset 环 ${r}×2，字号声明见 ${where}）。汉字的 advance 恒等于 1em（这条与装的是哪套 CJK 字体无关），字号一旦超过净空，墨迹就压到内环上——BD-11 当年那句「溢出盒子」说的就是这个形状，只是它当时是假的；把它变成真红的唯一合法路径是先改盒子，改完要连注释一起重看`);
        }
      }
      // —— 比例只读数、不判红（理由见本节 ⚠ 那段：它是循环论证）。
      if (sFails === 0) {
        const clearBrand = clearOf["印章常态"];
        const clearNarrow = clearOf["印章≤920"];
        const clearTb = clearOf["小印 titlebar"];
        console.log(
          `${TAG} [SEAL 不变量] 印章 ${baseFs}px／≤920 ${narrowFs}px 同值，环内净空 常态 ${clearBrand}、≤920 ${clearNarrow}（边框 ${baseBx}、inset 环 ${baseRing}）；小印 ${tbFsRaw}px / 净空 ${clearTb} ——实算通过。字号占净空 印章 ${((baseFs / clearBrand) * 100).toFixed(2)}% ↔ 小印 ${((tbFsRaw / clearTb) * 100).toFixed(2)}%（BD-23 按这条投的档，**不判红**，见本节注释）`
        );
      }
    }
  }
}

/* ------------------------------------------------------------------ 结论 */

if (failures > 0) {
  console.error(`${TAG} 失败 ${failures} 项`);
  process.exit(1);
}
console.log(`${TAG} 全部通过（令牌定义、D-1/D-2 回归、Button 能力面、${hardPairs.length} 组对比度硬断言${pending > 0 ? `，另有 ${pending} 组待收敛` : ""}）`);
