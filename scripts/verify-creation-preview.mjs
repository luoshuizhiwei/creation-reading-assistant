import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { buildSync } from "esbuild";

const root = path.resolve(import.meta.dirname, "..");
const bundleDirectory = mkdtempSync(path.join(os.tmpdir(), "creation-preview-contract-"));
const bundlePath = path.join(bundleDirectory, "contract.cjs");

function fail(message) {
  console.error(`[verify-creation-preview] ${message}`);
  process.exitCode = 1;
}

/**
 * 打印版式不变量。
 *
 * 这条守卫来自一次真实缺陷：Chromium 的分页算法遇到 `display: flex / grid` 的祖先时会把整棵
 * 子树当成不可分割的单块，强制分页被忽略、超出一页的内容直接丢失（实测 3 个
 * `break-before: page` 的章节在 flex 祖先下只剩 1 页）。应用外壳的 `.project-workbench` 与
 * `.creation-writing-stack` 都是 flex，因此「把通读页的全部祖先还原成静态块级流」这一条
 * 一旦被删掉或写错选择器，单元测试与契约测试都不会报错，只有真实打印会少印内容。
 * 这里对源码样式表做静态断言，作为该缺陷的回归守卫。
 */
function verifyPrintStylesheet() {
  const stylesheetPath = path.join(root, "src", "features", "creation", "preview", "preview-local.css");
  const css = readFileSync(stylesheetPath, "utf8");
  const printIndex = css.indexOf("@media print");
  if (printIndex < 0) {
    fail("打印样式表缺少 @media print 块。");
    return;
  }
  const printBlock = css.slice(printIndex);
  const required = [
    ["A4 页面尺寸", /@page\s*\{[^}]*size:\s*A4/],
    ["祖先链兜底选择器", /body\s*\*:has\(\.preview-page\)/],
    ["祖先 display 还原为块级", /body\s*\*:has\(\.preview-page\)\s*\{[^}]*display:\s*block\s*!important/],
    ["祖先 overflow 还原", /body\s*\*:has\(\.preview-page\)\s*\{[^}]*overflow:\s*visible\s*!important/],
    ["祖先高度还原", /body\s*\*:has\(\.preview-page\)\s*\{[^}]*height:\s*auto\s*!important/],
    // 外壳在 .creation-writing-page--active 下有 (0,2,0) !important 的 flex 规则，
    // 必须用抬过特异性的选择器压过，否则打印会残留 flex 祖先并印出应用 chrome。
    ["外壳工作台抬特异性还原", /\.creation-writing-page--active\s+\.project-workbench\.project-workbench/],
    ["外壳工作区抬特异性还原", /\.creation-writing-page--active\s+\.project-workbench-main\.project-workbench-main/],
    ["外壳纵向栈抬特异性还原", /\.creation-writing-stack--active\.creation-writing-stack--active/],
    ["抬特异性隐藏应用 hero", /\.creation-writing-page--active\s+\.creation-writing-hero\.creation-writing-hero/],
    ["抬特异性隐藏项目导航", /\.creation-writing-page--active\s+\.project-nav\.project-nav/],
    ["逐章强制分页", /break-before:\s*page/],
    ["隐藏应用标题栏", /\.desktop-titlebar/],
    ["隐藏应用侧栏", /\.desktop-sidebar/],
    ["隐藏通读页工具条", /\.preview-toolbar/],
    ["打印强制浅色底", /background:\s*#ffffff\s*!important/]
  ];
  const missing = required.filter(([, pattern]) => !pattern.test(printBlock)).map(([label]) => label);
  if (missing.length > 0) {
    fail(`打印样式表缺少必要规则：${missing.join("、")}`);
    return;
  }
  console.log(`[verify-creation-preview] print layout invariants verified (${required.length} rules).`);
}

try {
  const tsc = spawnSync(
    process.execPath,
    [path.join(root, "node_modules", "typescript", "bin", "tsc"), "--noEmit", "-p", "tsconfig.main.json"],
    { cwd: root, encoding: "utf8", windowsHide: true, timeout: 120_000 }
  );
  if (tsc.status !== 0) {
    fail(`TypeScript contract failed.\n${tsc.stdout}\n${tsc.stderr}`);
  } else {
    buildSync({
      entryPoints: [path.join(root, "electron", "main", "creation-workspace", "preview-contract.ts")],
      outfile: bundlePath,
      bundle: true,
      platform: "node",
      format: "cjs",
      target: "node20",
      external: ["electron", "better-sqlite3"]
    });

    const electron = path.join(root, "node_modules", "electron", "dist", "electron.exe");
    const contract = spawnSync(electron, [bundlePath], {
      cwd: root,
      encoding: "utf8",
      env: {
        ...process.env,
        ELECTRON_RUN_AS_NODE: "1",
        NODE_PATH: path.join(root, "node_modules")
      },
      windowsHide: true,
      timeout: 120_000
    });
    if (contract.status !== 0) {
      fail(`Runtime contract failed.\n${contract.stdout}\n${contract.stderr}`);
    } else {
      const evidence = JSON.parse(contract.stdout.trim().split(/\r?\n/).filter(Boolean).at(-1));
      if (evidence.allPass !== true) fail(`Incomplete evidence: ${JSON.stringify(evidence)}`);
      else {
        console.log(`[verify-creation-preview] ${evidence.tests} preview/print contracts verified.`);
        verifyPrintStylesheet();
      }
    }
  }
} catch (error) {
  fail(error instanceof Error ? error.stack ?? error.message : String(error));
} finally {
  rmSync(bundleDirectory, { recursive: true, force: true });
}
