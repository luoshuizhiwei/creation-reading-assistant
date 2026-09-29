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
//   ① button 的「直接父级类名」（TAIL）——不是选择器开头的类名。
//      `.creation-writing-hero .desktop-page-actions button` 的泄漏来自后者，
//      只看行首会整条漏掉。
//   ② 该父级类名内部确实渲染过 <Button>——只看「类名在已迁移文件里出现过」会
//      大量误报：`.history-tabs button` 服务的是共享 Tabs 内部的裸
//      <button role="tab">，它们本来就不该被组件样式接管。
const BUTTON_COMBINATOR = String.raw`(?:[ \t]*[>+~][ \t]*|[ \t]+)`;
const TAIL_ANCHOR = new RegExp(String.raw`(?:^|[ \t>+~])\.([\w-]+)(?:\.[\w-]+)*` + BUTTON_COMBINATOR + String.raw`button\b`);

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

// 全局样式表（main.tsx 里无条件加载，会漏到每个页面）与 feature 局部样式表同等对待
const GLOBAL_SHEETS = ["src/styles.css", "src/styles/tokens.css", "src/styles/editorial-studio.css"].map((p) => path.join(root, p));

/**
 * 遗留债务冻结清单（棘轮）：<Button> 系统落地前，全局「按钮容器」类规则本来就在
 * 用后代选择器接管页面里的按钮（.desktop-page-actions button 这一族）。给它们统一
 * 加 :not([data-variant]) 会当场改掉 4 个尚未迁移页面的外观，违背「一页一提交、
 * 每页零回归」的推进方式，所以先冻结为警告，迁移到哪个页面就把对应锚点删掉。
 *
 * 只有在清单之外的新增违规才会变红。
 */
const LEGACY_DESCENDANT_BUTTON = new Set([
  "src/styles/editorial-studio.css :: desktop-page-actions",
  "src/styles/editorial-studio.css :: history-page"
]);

// 排除判据必须是 :not([data-variant —— 带引号值的形式（:not([data-variant="quiet"])）
// 也算已排除；把 ] 写进 needle 会一条都匹配不上，等于检查失效。
const isExcluded = (sel) => sel.includes(":not([data-variant");

let legacyHits = 0;
const newHits = [];
for (const cssFile of cssFiles) {
  const dir = path.dirname(cssFile);
  const isGlobal = GLOBAL_SHEETS.includes(cssFile);
  const migrated = tsxByDir[dir];
  // 局部样式表：只看同目录已迁移页面；全局样式表：任何已迁移页面都可能被命中
  if (!isGlobal && !migrated) continue;
  let scope;
  if (isGlobal) {
    scope = globalHostTokens;
  } else {
    scope = new Set();
    for (const p of migrated) for (const c of classesHostingButton(readFileSync(p, "utf8"))) scope.add(c);
  }
  const rel = path.relative(root, cssFile).split(path.sep).join("/");
  const ast = postcss.parse(readFileSync(cssFile, "utf8"), { from: cssFile });
  ast.walkRules((rule) => {
    for (const part of rule.selector.split(",")) {
      const sel = part.trim();
      const anchor = TAIL_ANCHOR.exec(sel);
      if (!anchor) continue;
      if (isExcluded(sel)) continue; // 已排除，安全
      // 没有任何「内部渲染了 <Button> 的元素」带这个父级类名 → 规则碰不到迁移后的元素
      if (!scope.has(anchor[1])) continue;
      if (LEGACY_DESCENDANT_BUTTON.has(`${rel} :: ${anchor[1]}`)) legacyHits += 1;
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

/* ------------------------------------------------------------------ 结论 */

if (failures > 0) {
  console.error(`${TAG} 失败 ${failures} 项`);
  process.exit(1);
}
console.log(`${TAG} 全部通过（令牌定义、D-1/D-2 回归、Button 能力面、${hardPairs.length} 组对比度硬断言${pending > 0 ? `，另有 ${pending} 组待收敛` : ""}）`);
