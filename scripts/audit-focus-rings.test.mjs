/**
 * 批次 BD-205：焦点环普查脚本的判据账本。
 *
 * 这一批立的不是产品缺陷，是**量具的缺陷**：Electron 33.4.11 / Chromium 130 里
 * `getComputedStyle(el, "::-webkit-slider-thumb")` 恒读回 "none"——连无条件命中的
 * 基础规则都读不出来（像素取证证明 thumb 环实际在绘制，见脚本头注释）。当初拿这把
 * 坏尺子量出的「:focus-visible::-webkit-slider-thumb 永不命中」是假阳性，产品侧
 * 一处未改，改的是普查判据：range 输入框改走裁剪区像素差分。
 *
 * 本账本把这条教训钉成常驻断言：伪元素 computed 读取禁止再进判据；像素差分分支
 * （聚焦图 vs 双离焦基线、focus() 归位续走 Tab 序列）不许被悄悄删掉。
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

const src = readFileSync(
  path.join(path.dirname(fileURLToPath(import.meta.url)), "audit-focus-rings.mjs"),
  "utf8"
);
// 剥掉注释后的纯代码：头注释里引用坏尺子写法是文档，不是判据。
const code = src.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^\s*\/\/.*$/gm, "");

describe("焦点环普查判据账本（BD-205）", () => {
  it("判据里禁止出现伪元素 computed 读取（Chromium 130 恒读 none，坏尺子）", () => {
    expect(code).not.toMatch(/getComputedStyle\(\s*[\w.]+\s*,/);
  });

  it("range 停靠点必须走像素差分：聚焦后取标、双离焦基线、聚焦图比对", () => {
    expect(src).toMatch(/isRange[\s\S]*?data-ring-probe/);
    expect(src).toMatch(/Buffer\.compare\(shotOff1,\s*shotOff2\)/); // 基线稳定性
    expect(src).toMatch(/Buffer\.compare\(shotOn,\s*shotOff1\)/); // 聚焦差分
  });

  it("探针结束要 focus() 归位保住键盘模态，Tab 序列不得被 blur 打断", () => {
    expect(src).toMatch(/removeAttribute\("data-ring-probe"\);\s*\n\s*el\?\.focus\(\);/);
  });

  it("纯环形状与着陆点跳过规则不得丢失", () => {
    expect(src).toMatch(/0 0 0 \[1-9\]/);
    expect(src).toMatch(/\[tabindex='-1'\]/);
  });

  // —— BD-206：阅读器覆盖扩面钉住 ——
  it("书库种子必须跑在正式实例之前（首启建 schema → seed → 重开）", () => {
    expect(code).toMatch(/const \{ app \} = await launch\(\);\s*\n\s*await app\.close\(\);/);
    expect(code).toMatch(/round4-seed\.cjs/);
    expect(code.indexOf("round4-seed.cjs")).toBeLessThan(code.lastIndexOf("await launch()"));
  });

  it("epub.js 正文 iframe 必须豁免（环由书内 CSS 管，不归 App 判）", () => {
    expect(code).toMatch(/tagName\.toLowerCase\(\) === "iframe"/);
  });

  it("阅读器与向导键盘面必须各成范围：TXT/EPUB/抽屉/目录/搜索/向导", () => {
    for (const label of ["阅读器·TXT", "阅读器·TXT设置抽屉", "阅读器·TXT目录面板", "阅读器·TXT搜索浮层", "阅读器·EPUB", "弹层·EPUB设置抽屉", "弹层·新建项目向导"]) {
      expect(src, `缺范围「${label}」`).toContain(`"${label}"`);
    }
    // 目录面板默认展开，prep 必须幂等（count 判存在再点）
    expect(code).toMatch(/if \(await expand\.count\(\)\) await expand\.first\(\)\.click\(\);/);
  });
});
