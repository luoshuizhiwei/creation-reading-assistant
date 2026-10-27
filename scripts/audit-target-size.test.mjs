/**
 * 批次 BD-208：目标尺寸普查（WCAG 2.5.8）判据账本。
 *
 * 这一批和产品修复一起立的量具有三条容易被悄悄改坏的判据，全部钉在这里：
 *  ① 遮挡过滤——抽屉/弹层打开时背景目标指针不可达，不得进间距邻域。
 *     BD-208 取证（/tmp/bd208-geo2.log）：TXT 设置抽屉 `.paper-slider` 279×20
 *     唯一「邻」是遮罩下的目录章按钮（elementFromPoint 打回 slider 本身、
 *     按钮 closest(".motion-drawer") 为 null）——没有遮挡过滤就会假红，
 *     而假红的代价是产品侧被改一个根本没有缺陷的控件。
 *  ② 间距豁免的圆规则必须与 W3C Understanding 2.5.8 一致：他方**矩形**用
 *     点到矩形距离 <12，他方**小目标**用中心距 <24（两边都是 24 圆）。
 *     写成单向（只查中心距）会把「大矩形压进圆里」漏判。
 *  ③ Inline / User-agent control 豁免不许被删——删了阅读器正文行内链接
 *     和原生 checkbox 会整族假红。
 *  ④ 范围名册与 audit-focus-rings（BD-206）同口径：阅读器五面 + 两个弹层 +
 *     向导缺一个，普查就少盖一片指针面；目录面板 prep 必须幂等。
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

const src = readFileSync(
  path.join(path.dirname(fileURLToPath(import.meta.url)), "audit-target-size.mjs"),
  "utf8"
);
// 剥掉注释后的纯代码：头注释里写豁免口径是文档，不是判据。
const code = src.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^\s*\/\/.*$/gm, "");

describe("目标尺寸普查判据账本（BD-208）", () => {
  it("遮挡过滤必须在场：elementFromPoint 采样、只认自身或后代可达", () => {
    expect(code).toMatch(/document\.elementFromPoint\(/);
    expect(code).toMatch(/hit === el \|\| el\.contains\(hit\)/);
    // 三内侧采样点（中心 + 两角朝内），单点会被内容层/滚动条带偏
    expect(code).toMatch(/r\.x \+ r\.width \/ 2/);
    expect(code).toMatch(/occluded \+= 1; continue;/);
  });

  it("间距圆规则两头齐全：他方矩形点距 <12、他方小目标中心距 <24", () => {
    expect(code).toMatch(/Math\.hypot\(dx, dy\) < 12/);
    expect(code).toMatch(/Math\.hypot\(cx - ox, cy - oy\) < 24/);
    // 邻域只数活体（live），不得退回 document 全量查询
    expect(code).toMatch(/for \(const o of live\)/);
  });

  it("2.5.8 主判据是 24×24，豁免五条中可机械判定者须在场", () => {
    expect(code).toMatch(/t\.w >= 24 && t\.h >= 24/);
    expect(code).toMatch(/type === "checkbox" \|\| t\.type === "radio"/); // UA 豁免
    expect(code).toMatch(/isInline/); // Inline 豁免
  });

  it("不可达/隐形目标先跳过：disabled、inert、iframe、<1px、pointer-events:none", () => {
    expect(code).toMatch(/el\.disabled \|\| el\.closest\("\[inert\]"\)/);
    expect(code).toMatch(/if \(tag === "iframe"\) continue;/);
    expect(code).toMatch(/r\.width < 1 \|\| r\.height < 1/);
    expect(code).toMatch(/cs\.pointerEvents === "none"/);
  });

  it("牙齿教训（BD-208）：摸底假红两根源不得复发——disabled 不进邻域、判交用中心圆", () => {
    // ① disabled 目标整体跳过（既是判定豁免也是邻域豁免）：过滤在收集期，
    //    判定循环遍历的是过滤后的 live 数组。
    expect(code.indexOf("el.disabled")).toBeLessThan(code.indexOf("live.push("));
    // ② 大邻目标用「点到矩形距离 <12」判压圆，不是拿 h<24 之类的矩形高粗判。
    expect(code).toMatch(/Math\.max\(or\.left - cx, 0, cx - or\.right\)/);
    expect(code).toMatch(/Math\.max\(or\.top - cy, 0, cy - or\.bottom\)/);
  });

  it("范围名册与焦点环普查同口径（阅读器五面 + 弹层三面 + 目录幂等）", () => {
    for (const label of ["项目·写作台", "项目·大纲", "项目·写作统计", "弹层·全局搜索", "弹层·写作速查", "弹层·新建项目向导", "阅读器·TXT", "阅读器·TXT设置抽屉", "阅读器·TXT目录面板", "阅读器·TXT搜索浮层", "阅读器·EPUB", "弹层·EPUB设置抽屉"]) {
      expect(src, `缺范围「${label}」`).toContain(`"${label}"`);
    }
    expect(code).toMatch(/if \(await expand\.count\(\)\) await expand\.first\(\)\.click\(\);/);
  });

  it("书库种子必须跑在正式实例之前（首启建 schema → seed → 重开）", () => {
    expect(code).toMatch(/const \{ app \} = await launch\(\);\s*\n\s*await app\.close\(\);/);
    expect(code).toMatch(/round4-seed\.cjs/);
    expect(code.indexOf("round4-seed.cjs")).toBeLessThan(code.lastIndexOf("await launch()"));
  });

  it("红名单与通过数一并报出，防止遮挡过滤把全网滤空还报绿", () => {
    expect(src).toMatch(/遮挡过滤 \$\{totalOccluded\}/);
    expect(src).toMatch(/TARGETSIZE_OK/);
    expect(src).toMatch(/TARGETSIZE_RED/);
  });

  it("BD-208 产品修复锚点：五处 2.5.8 修复的判据出处不许散架", () => {
    const styles = readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "src", "styles.css"), "utf8");
    const editorial = readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "src", "styles", "editorial-studio.css"), "utf8");
    const interaction = readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "src", "components", "interaction.tsx"), "utf8");
    const search = readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "src", "features", "search", "search.css"), "utf8");
    expect(styles).toMatch(/\.outline-node-actions button\s*\{[^}]*padding: 6px/);
    expect(styles).toMatch(/\.stats-metric-switch button\s*\{[^}]*padding-inline: 6px/);
    expect(editorial).toMatch(/\.outline-scene-main\s*\{[\s\S]*?min-height: 24px/);
    expect(interaction).toMatch(/rounded-md p-1\.5 flex items-center justify-center/);
    expect(search).toMatch(/\.uni-search-input\s*\{[\s\S]*?min-height: 24px/);
  });
});
