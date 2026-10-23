// @vitest-environment node
/**
 * BD-123 跨文件快捷键和弦契约：把 BD-113（Ctrl+Shift+K 被全局搜索抢）与
 * BD-122（Ctrl+Shift+F 被本书查找抢）这两起同源缺陷上升成一条系统级不变量——
 * {Ctrl/⌘+K, Ctrl/⌘+Shift+K, Ctrl/⌘+F, Ctrl/⌘+Shift+F} 四个和弦必须各自被唯一
 * 处理器拥有，谁都不许重复认领（尤其带不带 Shift 的互斥）。
 *
 * 单文件测试（search-panel.test.tsx / reader-find-shortcut.test.ts / writing-desk-reference.test.tsx）
 * 各守各的门；这里守的是三扇门之间的接缝：一旦有人把某个 `!shiftKey` 护栏删掉、
 * 或给某支加上与别人重叠的分支，下面对应断言就红。判据取自各文件里那条真实谓词的
 * 字面形态（源码级，稳定且零运行时依赖，按 BD-117/119/120/122 惯例）。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const searchSrc = read("../../../search/SearchPanel.tsx");
const quickRefSrc = read("../../../creation/editor/WritingDesk.tsx");
const readerSrc = read("../TxtMarkdownReader.tsx");

describe("快捷键和弦归属契约（Ctrl/⌘ 系）", () => {
  it("全局搜索：Ctrl/⌘+K 只在「不带 Shift」时认领（Shift 让给写作速查）", () => {
    expect(searchSrc).toMatch(/=== "k" \? !event\.shiftKey/);
  });

  it("全局搜索：Ctrl/⌘+F 一支显式要求「带 Shift」（Ctrl+Shift+F 才归它）", () => {
    expect(searchSrc).toMatch(/event\.shiftKey && event\.key\.toLowerCase\(\) === "f"/);
  });

  it("写作速查：只认领「Ctrl/⌘+Shift+K」这一支带 Shift 的和弦", () => {
    expect(quickRefSrc).toMatch(/!event\.shiftKey \|\| event\.key\.toLowerCase\(\) !== "k"/);
  });

  it("本书查找：Ctrl/⌘+F 只在「不带 Shift」时认领（Shift 让给全局搜索）", () => {
    expect(readerSrc).toMatch(/!e\.shiftKey && e\.key\.toLowerCase\(\) === "f"/);
  });

  it("四把锁互斥：没有任何一支把裸的 ctrl&&key 判据不带 shift 护栏地认领（防回归）", () => {
    // 本书查找绝不能再退回旧的无 !shiftKey 裸判据（BD-122 的成因）。
    expect(readerSrc).not.toMatch(/\(e\.ctrlKey \|\| e\.metaKey\) && e\.key\.toLowerCase\(\) === "f"/);
  });
});
