/**
 * BD-119 源码级护栏：TocList 不得再用低 alpha 的 text-paper-muted/<数字> 压暗文字。
 * 这类"用透明度表达状态"的写法把二级文字叠到 ~2.2:1（远低于 4.5）；种子 TXT 书无标题、
 * TOC 为空，运行时对比度普查（50 范围）扫不到读态，故在源码层钉死这一类名。
 * 放在 node 环境的 .ts 里：jsdom 下 import.meta.url 不是 file: 协议，readFileSync 会抛错。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

describe("TocList 源码不含低 alpha 压暗文字类", () => {
  it("text-paper-muted/<数字> 不再出现（先剥注释，说明注释里逐字写了旧类名）", () => {
    const raw = readFileSync(fileURLToPath(new URL("../TocList.tsx", import.meta.url)), "utf8");
    const src = raw.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/[^\n]*/g, "");
    expect(src).not.toMatch(/text-paper-muted\/\d/);
  });
});
