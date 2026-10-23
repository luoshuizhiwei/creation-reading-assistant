// @vitest-environment node
/**
 * BD-122 源码级护栏：本书查找的 Ctrl/⌘+F 必须排除 Shift，避免与全局搜索
 * （SearchPanel 的 Ctrl+Shift+F，BD-113 定的和弦）双触发——旧写法 `(ctrl||meta) && key==="f"`
 * 不带 !shiftKey，一次按键同时弹起本书查找面板与全屏抢焦点的 .uni-search-overlay。
 * 这是 BD-113 的 Ctrl+Shift+K 缺陷镜像到 f 这一支。完整挂载 TxtMarkdownReader 需要 Electron
 * IPC / settings / epub 存储，代价高且脆，按 BD-117/119/120 的惯例走源码断言钉住判据。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

const src = readFileSync(
  fileURLToPath(new URL("../TxtMarkdownReader.tsx", import.meta.url)),
  "utf8",
);

describe("BD-122 本书查找 Ctrl+F 不与全局搜索 Ctrl+Shift+F 抢和弦", () => {
  it("f 分支判据含 !e.shiftKey（Shift 让给全局搜索）", () => {
    expect(src).toMatch(/!e\.shiftKey && e\.key\.toLowerCase\(\) === "f"/);
  });

  it("旧的裸 ctrl/f 判据（无 shiftKey 护栏）已消失", () => {
    expect(src).not.toMatch(/\(e\.ctrlKey \|\| e\.metaKey\) && e\.key\.toLowerCase\(\) === "f"/);
  });

  it("keydown 处理开头挡输入法合成态（isComposing / keyCode 229）", () => {
    expect(src).toMatch(/isComposing \|\| e\.keyCode === 229/);
  });
});
