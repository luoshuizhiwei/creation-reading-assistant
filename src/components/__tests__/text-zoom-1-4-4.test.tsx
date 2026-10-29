// @vitest-environment jsdom
/**
 * BD-218 账本：WCAG 1.4.4 文本缩放（Level AA）。
 *
 * 主判据是 scripts/audit-text-zoom.mjs：
 *   ① 走 webContents.sendInputEvent 的真实 before-input-event 路径，核对
 *      Ctrl/Cmd + = / - / 0 能把 zoomFactor 从 1.0 推到 2.0、拉回 0.5、还原 1.0，
 *      Ctrl+Alt 与无修饰键不抢；
 *   ② 200% 下逐视图扫可见文本的硬裁切（overflow hidden/clip + scroll 超界），要求 0。
 *
 * 本批真实缺陷：原来的字号档（appFontScale 0.85–1.4）只改根字号，Tailwind 的 rem
 * 文案跟着走，全站固定 px 字号不动，上限也只有 140%——用户没有任何路径把文字放到
 * 200%，1.4.4 的「可放大到 200%」根本不成立。修复：主进程接整页缩放（px/rem 一起
 * 放大），并把 200% 下会被切掉的写作台页头说明与书库书名改成自然折行。
 *
 * 路径账（__tests__/ 起算）：../../../ = 仓库根；../../features = src/features/。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const census = read("../../../scripts/audit-text-zoom.mjs");
const pkg = read("../../../package.json");
const main = read("../../../electron/main/index.ts");
const helper = read("../../../electron/main/zoom-shortcuts.ts");
const heroCss = read("../../../src/styles/editorial-studio.css");
const libraryPage = read("../../features/library/LibraryPage.tsx");

const heroRule = heroCss.slice(heroCss.indexOf(".creation-writing-hero-desc {"), heroCss.indexOf(".creation-writing-hero-desc {") + 600);

describe("BD-218 普查判据：1.4.4 文本缩放", () => {
  it("红/绿结论、退出码与两条腿都在", () => {
    expect(census).toContain("ZOOM_RED");
    expect(census).toContain("ZOOM_OK");
    expect(census).toContain("process.exit(1)");
    expect(census).toContain("sendInputEvent");
    expect(census).toContain("getZoomFactor");
    expect(census).toContain("scrollWidth");
    expect(pkg).toContain('"audit:text-zoom": "node scripts/audit-text-zoom.mjs"');
  });

  it("主进程接上 before-input-event 缩放，上下限对齐 200%", () => {
    expect(main).toContain('webContents.on("before-input-event"');
    expect(main).toContain("resolveZoomFactor");
    expect(main).toContain("setZoomFactor");
    expect(helper).toContain("ZOOM_MIN = 0.5");
    expect(helper).toContain("ZOOM_MAX = 2");
    expect(helper).toContain("ZOOM_STEP = 0.1");
  });

  it("写作台页头说明不再单行省略", () => {
    expect(heroRule).toContain("white-space: normal !important");
    expect(heroRule).not.toContain("text-overflow: ellipsis");
    expect(heroRule).not.toContain("overflow: hidden");
  });

  it("书库书名与作者不再 truncate", () => {
    expect(libraryPage).toContain("paper-title min-w-0 break-words");
    expect(libraryPage).not.toContain("paper-title truncate");
    expect(libraryPage).toContain("mt-1 break-words text-xs");
    expect(libraryPage).not.toContain("mt-1 truncate text-xs");
  });
});
