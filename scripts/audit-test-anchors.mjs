/**
 * 批次 BD-95：把电池锚点审计从 /tmp 收进仓库。
 *
 * 由来：BD-57 那批改了 5 份 CSS 的注释与值，担心「还了债却把哨兵牙磨平」，
 * 临时在 /tmp 写了个审计脚本核对每颗牙的 needle 还找不找得到。后来每批都靠它
 * 把关，但脚本本身从未入库——沙箱一重来就没了，换个人也无从复现。
 *
 * 它检查两件事：
 *   ① 失配：电池里每个 `mutate("文件", "needle", …)` 的 needle 必须能在**当前**
 *      文件里原样找到。找不到说明源码被改过、牙已经哑了（电池会把它记成 SKIP）。
 *   ② 非唯一：needle 在目标文件里出现多次时，`String.replace` 只替换第一处，
 *      牙打中的是哪一条完全取决于行序。BD-94 起按 0 容忍处理——将来真要复用
 *      同一段文本，就把 needle 加长到唯一，而不是靠行序。
 *
 * 只审计**字面量**锚（文件与 needle 都是双引号字面量）；带变量的动态锚无法静态
 * 核对，所以下面会把审计到的条数与电池里 mutate 调用总数一起报出来，免得
 * 「覆盖率悄悄下降」被当成「全部通过」。
 *
 * 没有豁免名单：往守卫账本塞幻影行的牙，needle 钉的是账本数组头这类**真实存在**
 * 的源码文本，幻影内容在替换值那一侧，不是 needle。真出现「needle 本身就不该存在」
 * 的形状，该改的是那颗牙的写法，不是给审计开一个静默跳过的口子。
 *
 * 用法：node scripts/audit-test-anchors.mjs  （失败 exit 1）
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const batPath = "scripts/inject-test-design-tokens.mjs";
const bat = readFileSync(path.join(root, batPath), "utf8");

const unescape = (s) => JSON.parse(`"${s}"`);

// mutate("path", "needle", "replacement") —— 第三项是替换后的文本，不审计。
const CALL = /mutate\(\s*"((?:[^"\\]|\\.)*)",\s*"((?:[^"\\]|\\.)*)"/g;
// mutate(file, "needle", …)：文件走变量，无法静态核对，只计数并点名。
const DYNAMIC_CALL = /(?<!function )\bmutate\(\s*([A-Za-z_$][\w$]*)\s*,/g;

const fileCache = new Map();
const readTarget = (file) => {
  if (!fileCache.has(file)) fileCache.set(file, readFileSync(path.join(root, file), "utf8"));
  return fileCache.get(file);
};

let audited = 0;
const byFile = new Map();
const missing = [];
const ambiguous = [];

for (const m of bat.matchAll(CALL)) {
  const file = m[1];
  const needle = unescape(m[2]);
  audited += 1;
  byFile.set(file, (byFile.get(file) ?? 0) + 1);

  let text;
  try {
    text = readTarget(file);
  } catch {
    missing.push({ file, needle, why: "文件缺失" });
    continue;
  }
  const count = text.split(needle).length - 1;
  if (count === 0) missing.push({ file, needle, why: "找不到" });
  else if (count > 1) ambiguous.push({ file, needle, count });
}

const dynamic = [...bat.matchAll(DYNAMIC_CALL)].map((m) => m[1]);

const brief = (s) => JSON.stringify(s.length > 70 ? `${s.slice(0, 70)}…` : s);

console.log(
  `[锚点审计] 字面量锚 ${audited} 个（静态可核对）；动态锚 ${dynamic.length} 个（文件走变量，无法静态核对）`
);
if (dynamic.length) console.log(`   动态锚来自变量：${[...new Set(dynamic)].join("、")}`);
const top = [...byFile.entries()].sort((a, b) => b[1] - a[1]).slice(0, 12);
for (const [file, n] of top) console.log(`   ${String(n).padStart(3)}× ${file}`);

if (missing.length) {
  console.error(`\n[锚点审计] 失配 ${missing.length} 个（牙已哑：源码里找不到这段文本，电池会记成 SKIP）：`);
  for (const x of missing) console.error(`   [${x.why}] ${x.file} :: ${brief(x.needle)}`);
}
if (ambiguous.length) {
  console.error(`\n[锚点审计] 非唯一 ${ambiguous.length} 个（replace 只改第一处，目标随行序漂移）：`);
  for (const x of ambiguous) console.error(`   ×${x.count} ${x.file} :: ${brief(x.needle)}`);
}

if (missing.length || ambiguous.length) {
  console.error(
    `\n[锚点审计] 未通过：${missing.length} 个失配 + ${ambiguous.length} 个非唯一。` +
      `\n  失配的修法是给牙换锚（改到现存文本上），不是删牙；非唯一的修法是把 needle 加长到只命中一处。`
  );
  process.exit(1);
}

console.log(`\nANCHOR_AUDIT_OK：${audited} 个锚点全部命中且唯一（${byFile.size} 个文件）。`);
