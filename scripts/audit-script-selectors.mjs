/**
 * 批次 BD-101：脚本选择器审计。
 *
 * 由来：BD-99 修的是 51 个脚本里的 Electron 二进制路径，BD-100 修的是实拍脚本里
 * 一整套随版式改版消失的类名（.project-home-hero / .creation-wizard / .creation-field）。
 * 两次都是「脚本烂在没人跑的地方」——验收脚本不像产品代码有测试盯着，类名一改版就
 * 静默失效，下次想用才发现跑不起来。
 *
 * 这个审计把 `scripts/*.mjs` 里所有**字面量**选择器抽出来，把其中的类名拿去
 * `src/` 与 `electron/` 的源码里找出处。找不到就说明这个选择器已经指向不存在的元素，
 * 脚本要么已经跑不通，要么正在等一个永远不会出现的节点。
 *
 * 判据与边界：
 *   ① 只看字面量：`querySelector / querySelectorAll / waitForSelector / locator`
 *      的参数必须是引号字符串；模板字符串里带 `${}` 的无法静态核对，只计数并点名。
 *   ② 类名按「整词」比对（前后不能挨着字母、数字、下划线或连字符），避免
 *      `hero` 被 `creation-writing-hero` 误判为存在。
 *   ③ 唯一豁免是 `lucide-*`：它由 lucide-react 在运行期按图标名生成，源码里
 *      只写 `<Check />` 这类组件，查不到类名字面量。除此之外不开豁免口子——
 *      真遇到运行期类名，应该把它写进这条注释并说明生成者，而不是静默跳过。
 *   ④ 纯标签 / 属性 / id 选择器不含类名，不参与比对，但会把条数报出来，
 *      免得「扫了个寂寞」被当成「全部通过」。
 *   ⑤ 跳过 `*.test.mjs`：单元测试的夹具里会故意写不存在的类名（BD-101 自己就
 *      有一条这样的反向用例），把它们当验收脚本扫会自己咬自己。
 *
 * 用法：node scripts/audit-script-selectors.mjs [--root=<dir>]   （失败 exit 1）
 *       `--root` 只给测试夹具用，正常跑不要传。
 */
import { readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const rootArg = process.argv.slice(2).find((a) => a.startsWith("--root="));
const root = rootArg ? path.resolve(rootArg.split("=")[1]) : here;

const SCRIPT_DIR = path.join(root, "scripts");
const SOURCE_DIRS = ["src", "electron"].map((d) => path.join(root, d));
const SOURCE_EXT = /\.(?:tsx?|css|html)$/;

// querySelector(All)("…") / waitForSelector("…") / locator("…")
const SELECTOR_CALL =
  /querySelector(?:All)?\(\s*["'`]([^"'`]+)["'`]|waitForSelector\(\s*["'`]([^"'`]+)["'`]|locator\(\s*["'`]([^"'`]+)["'`]/g;
const CLASS_TOKEN = /\.([A-Za-z_][\w-]*)/g;
const ALLOW = [/^lucide-/];

const walk = (dir, out = []) => {
  let entries;
  try {
    entries = readdirSync(dir, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, out);
    else if (SOURCE_EXT.test(entry.name)) out.push(full);
  }
  return out;
};

const sourceText = SOURCE_DIRS.flatMap((dir) => walk(dir))
  .map((file) => {
    const raw = readFileSync(file, "utf8");
    // 注释先摘掉再比对：守卫的「摘死声明」会把删掉的类名连注释一起留在 CSS 里
    //（例如 `.project-home-hero` 的退役说明），不摘就会把死类名当成有出处。
    // 行注释只摘独占一行的，避免把字符串里的 `//`（URL）截断。
    const noBlock = raw.replace(/\/\*[\s\S]*?\*\//g, "");
    if (/\.(?:tsx?|html)$/.test(file)) {
      return noBlock.replace(/^[ \t]*\/\/.*$/gm, "").replace(/<!--[\s\S]*?-->/g, "");
    }
    return noBlock;
  })
  .join("\n");

const hasToken = (name) => {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(`(?<![\\w-])${escaped}(?![\\w-])`).test(sourceText);
};

let scriptCount = 0;
let selectorCount = 0;
let dynamicCount = 0;
let classCount = 0;
let allowedCount = 0;
let plainCount = 0;
const selectorsSeen = new Set();
const classesSeen = new Set();
const missing = [];
const dynamicByFile = new Map();

for (const file of readdirSync(SCRIPT_DIR).filter((f) => f.endsWith(".mjs") && !f.endsWith(".test.mjs")).sort()) {
  const full = path.join(SCRIPT_DIR, file);
  if (!statSync(full).isFile()) continue;
  scriptCount += 1;
  const text = readFileSync(full, "utf8");

  for (const match of text.matchAll(SELECTOR_CALL)) {
    const selector = match[1] ?? match[2] ?? match[3];
    if (selector.includes("${")) {
      dynamicCount += 1;
      dynamicByFile.set(file, (dynamicByFile.get(file) ?? 0) + 1);
      continue;
    }
    selectorCount += 1;
    selectorsSeen.add(selector);
    const classes = [...selector.matchAll(CLASS_TOKEN)].map((m) => m[1]);
    if (!classes.length) {
      plainCount += 1;
      continue;
    }
    const unseen = [];
    for (const name of classes) {
      if (ALLOW.some((re) => re.test(name))) {
        allowedCount += 1;
        continue;
      }
      classCount += 1;
      classesSeen.add(name);
      if (!hasToken(name) && !unseen.includes(name)) unseen.push(name);
    }
    if (unseen.length) missing.push({ file, selector, classes: unseen });
  }
}

console.log(
  `[选择器审计] 扫了 ${scriptCount} 个脚本：字面量选择器 ${selectorCount} 处` +
    `（${selectorsSeen.size} 个不同选择器）——类名 ${classCount} 处（${classesSeen.size} 个不同类名）、` +
    `纯标签/属性 ${plainCount} 处、豁免 ${allowedCount} 处 lucide-*` +
    `；模板动态选择器 ${dynamicCount} 处（无法静态核对）`
);
if (dynamicByFile.size) {
  const top = [...dynamicByFile.entries()].sort((a, b) => b[1] - a[1]).slice(0, 8);
  for (const [file, n] of top) console.log(`   ${String(n).padStart(3)}× ${file}`);
}

if (missing.length) {
  console.error(`\n[选择器审计] 失配 ${missing.length} 条（类名在 src/ 与 electron/ 里找不到出处）：`);
  for (const item of missing) {
    console.error(`   ${item.file} :: ${item.selector}  →  ${item.classes.map((c) => `.${c}`).join(", ")}`);
  }
  console.error(
    "\n[选择器审计] 未通过。改法是：把脚本里的选择器修到现界面能命中的节点上（或用稳定属性、按钮文案），" +
      "不是删掉这段验收。"
  );
  process.exit(1);
}

console.log(`\nSELECTOR_AUDIT_OK：${classesSeen.size} 个类名全部在 src/ 与 electron/ 里有出处。`);
