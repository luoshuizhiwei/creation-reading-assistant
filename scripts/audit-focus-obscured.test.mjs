/**
 * 批次 BD-209：焦点遮挡普查（WCAG 2.4.11 Focus Not Obscured (Minimum)）判据账本。
 *
 * 摸底取证（/tmp/bd209-probe.json，564 停靠点 19 红）分三簇，各自钉法不同：
 *  ① TXT/EPUB 设置抽屉 14 停靠点——全屏遮罩（inset-0 z-30）盖住正文/顶栏页签/
 *     底栏滑杆，Tab 却照样路过。修复 = 抽屉接 BD-130 键盘契约（Tab 环把键盘
 *     圈在可见层里）；这里同时钉 hook 接线与 BD-201 开手回交并存（双还同节点，
 *     幂等），撤掉任何一边都会红回那 14 个。
 *  ② 写作速查 4 停靠点——面板 z-45 全高贴右、宽度下限 280 ≥ 检查列宽，被盖列
 *     指针不可达却仍是 Tab 停靠点。面板非模态（边写边核对是核心用法），修复 =
 *     被盖层 inert，列内 z70+ 确认浮层开着时豁免（inert 会锁死它们的输入）。
 *  ③ 向导 sr-only radio 1 停靠点——焦点指示画在包装卡（:focus-within 轮廓），
 *     量 1×1 input 自身矩形没有意义。裁决为普查侧「隐形代理豁免」，产品零改动。
 * 判据本体同样易被悄悄改坏：视口裁剪、6 点采样、外来相交归因、全遮才红，
 * 以及模态面 noBlur 起步——blur 起步会从 body 走进弹层背后，测错了世界。
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

const here = path.dirname(fileURLToPath(import.meta.url));
const src = readFileSync(path.join(here, "audit-focus-obscured.mjs"), "utf8");
// 剥掉注释后的纯代码：头注释里写口径是文档，不是判据。
const code = src.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^\s*\/\/.*$/gm, "");

describe("焦点遮挡普查判据账本（BD-209）", () => {
  it("2.4.11 主判据两头齐全：视口裁剪外 + 采样全被外来相交元素盖", () => {
    expect(code).toMatch(/r\.bottom <= 0 \|\| r\.top >= vh \|\| r\.right <= 0 \|\| r\.left >= vw/);
    expect(code).toMatch(/for \(const fx of \[0\.15, 0\.5, 0\.85\]\) for \(const fy of \[0\.3, 0\.7\]\)/);
    expect(code).toMatch(/info\.outOfViewport \|\| \(info\.samples > 0 && info\.clearHits === 0\)/);
  });

  it("可见点只认自身或后代；遮挡源必须与自身矩形实相交（>0.5px 两轴）", () => {
    expect(code).toMatch(/h === el \|\| el\.contains\(h\)/);
    expect(code).toMatch(/ix > 0\.5 && iy > 0\.5/);
  });

  it("隐形代理豁免（簇③裁决）：<2px 矩形豁免计数、不进红名单，也不得参与遮挡判定", () => {
    expect(code).toMatch(/if \(r\.width < 2 \|\| r\.height < 2\) return \{ id, exemptTiny: true \};/);
    expect(code).toMatch(/info\.exemptTiny/);
    expect(code.indexOf("exemptTiny")).toBeLessThan(code.indexOf("outOfViewport = "));
  });

  it("非停靠点先跳过：disabled、inert、tabindex=-1、iframe、display/visibility/opacity", () => {
    expect(code).toMatch(/el\.matches\(":disabled"\) \|\| el\.closest\("\[inert\]"\) \|\| el\.matches\("\[tabindex='-1'\]"\)/);
    expect(code).toMatch(/tag === "iframe"/);
    expect(code).toMatch(/cs\.display === "none" \|\| cs\.visibility === "hidden" \|\| Number\(cs\.opacity\) === 0/);
  });

  it("模态面必须 noBlur 起步（焦点已由 BD-130 落在弹层内），非模态速查从 blur 走全页", () => {
    expect(code).toMatch(/if \(!noBlur\) \{[\s\S]*?document\.activeElement\?\.blur\?\.\(\)/);
    for (const label of ["弹层·新建项目向导", "弹层·全局搜索", "阅读器·TXT设置抽屉", "弹层·EPUB设置抽屉"]) {
      const i = code.lastIndexOf(label);
      expect(code.slice(i, i + 220), `「${label}」的 sweep 应带 noBlur: true`).toMatch(/noBlur: true/);
    }
    const q = code.indexOf('"弹层·写作速查"');
    expect(code.slice(q, q + 220)).not.toMatch(/noBlur: true/);
  });

  it("范围名册与目标尺寸普查同口径（阅读器五面 + 弹层四面 + 基面十一个）", () => {
    for (const label of ["项目·写作台", "项目·设定卡", "项目·大纲", "项目·概览", "项目·全书预览", "项目·写作统计", "项目·版本历史", "书库", "收件箱", "资料阅读", "设置", "项目·写作台", "弹层·新建项目向导", "弹层·全局搜索", "弹层·写作速查", "阅读器·TXT", "阅读器·TXT设置抽屉", "阅读器·TXT目录面板", "阅读器·TXT搜索浮层", "阅读器·EPUB", "弹层·EPUB设置抽屉"]) {
      expect(src, `缺范围「${label}」`).toContain(`"${label}"`);
    }
    expect(code).toMatch(/if \(await expand\.count\(\)\) await expand\.first\(\)\.click\(\);/);
  });

  it("书库种子必须跑在正式实例之前（首启建 schema → seed → 重开）", () => {
    expect(code).toMatch(/const \{ app \} = await launch\(\);\s*\n\s*await app\.close\(\);/);
    expect(code).toMatch(/round4-seed\.cjs/);
    expect(code.indexOf("round4-seed.cjs")).toBeLessThan(code.lastIndexOf("await launch()"));
  });

  it("停靠点、豁免数、红数一并报出；OK/RED 双标记在场", () => {
    expect(src).toMatch(/隐形代理豁免 \$\{totalTiny\}/);
    expect(src).toMatch(/FOCUSOBSCURED_OK/);
    expect(src).toMatch(/FOCUSOBSCURED_RED/);
  });

  it("簇①产品锚点：抽屉 BD-130 接线与 BD-201 开手回交并存，缺一边就红回 14 个停靠点", () => {
    const drawer = readFileSync(path.join(here, "..", "src", "features", "library", "ReaderSettingsDrawer.tsx"), "utf8");
    expect(drawer).toMatch(/role="dialog"/);
    expect(drawer).toMatch(/aria-modal="true"/);
    expect(drawer).toMatch(/useModalKeyboard\(\{\s*active: open,[\s\S]*?containerRef: drawerRef,[\s\S]*?onEscape: onClose,[\s\S]*?initialFocusRef: titleRef/);
    expect(drawer).toMatch(/onKeyDown=\{handleDrawerKeyDown\}/);
    // BD-201 回交覆盖面原样在场（宿主直接翻 open=false 的路径靠它）。
    expect(drawer).toMatch(/const opener = document\.activeElement;/);
    expect(drawer).toMatch(/ref=\{titleRef\} tabIndex=\{-1\}/);
    expect(drawer).toMatch(/aria-labelledby="reader-settings-drawer-title"/);
  });

  it("簇②产品锚点：速查面板打开 ⇒ 被盖检查列 inert；z70+ 确认浮层开着时豁免", () => {
    const margin = readFileSync(path.join(here, "..", "src", "features", "creation", "editor", "desk", "WritingDeskMargin.tsx"), "utf8");
    expect(margin).toMatch(/covered: boolean;/);
    expect(margin).toMatch(/const marginInert = covered && !modalOverlaysOpen;/);
    for (const overlay of ["reanchorCandidate", "aiPackDialog", "aiReport", "aiCandidate"]) {
      expect(margin, `浮层豁免漏了 ${overlay}`).toContain(`Boolean(${overlay})`);
    }
    expect((margin.match(/\.\.\.\(marginInert \? \{ inert: "" \} : \{\}\)/g) ?? []).length, "展开钮与检查列两处 inert 展开缺一不可").toBe(2);
    const desk = readFileSync(path.join(here, "..", "src", "features", "creation", "editor", "WritingDesk.tsx"), "utf8");
    expect(desk).toMatch(/covered=\{!focusMode && quickReferenceOpen\}/);
  });

  it("npm 入口在场：audit:focus-obscured 指向本普查脚本", () => {
    const pkg = readFileSync(path.join(here, "..", "package.json"), "utf8");
    expect(pkg).toMatch(/"audit:focus-obscured": "node scripts\/audit-focus-obscured\.mjs"/);
  });
});
