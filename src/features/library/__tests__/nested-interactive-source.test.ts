// @vitest-environment node
/**
 * BD-124 源码级护栏：可点击的行容器不得再挂 role="button"（行内已有真实 <button>，
 * 会构成 nested-interactive）。TocList 改为行内原生按钮承担跳章；LibraryPage 行去掉
 * role/tabIndex/onKeyDown、保留整行 onClick 作鼠标便利（无 role 不触发该规则）。
 * 另钉住命令面板 Ctrl/⌘+P 的 Shift 排除（对齐 BD-113 和弦契约）。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const strip = (s: string) => s.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/[^\n]*/g, "");

describe("BD-124 行级交互控件不得嵌套", () => {
  it("TocList 与 LibraryPage 的可点击行不再挂 role=button（剥注释后）", () => {
    expect(strip(read("../toc/TocList.tsx"))).not.toMatch(/role="button"/);
    expect(strip(read("../LibraryPage.tsx"))).not.toMatch(/role="button"/);
  });

  it("命令面板 Ctrl/⌘+P 排除 Shift", () => {
    expect(read("../../creation/CreationProjectsPage.tsx")).toMatch(
      /!event\.altKey && !event\.shiftKey && event\.key\.toLowerCase\(\) === "p"/,
    );
  });
});
