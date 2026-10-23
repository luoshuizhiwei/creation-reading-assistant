// @vitest-environment node
/**
 * BD-120 源码级护栏：占位符对比度不能再靠 UA 默认灰或低 alpha 隐身。
 * 运行时普查（audit-contrast.mjs）BD-120 起已实测 ::placeholder，这条测试再做一层
 * 静态兜底：确认 styles.css 里的 base 占位符兜底规则在位，且 .paper-input 的
 * placeholder 不回落成低 alpha 斜杠类（历史 text-paper-muted/55 浅色只有 2.16:1）。
 * 显式钉 @vitest-environment node：本目录默认环境非 file:，import.meta.url→readFileSync 会抛错。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

const css = readFileSync(fileURLToPath(new URL("../../styles.css", import.meta.url)), "utf8");

describe("BD-120 占位符对比度护栏", () => {
  it("存在 base 层 input/textarea::placeholder 兜底，上色为 --text-secondary 且 opacity:1", () => {
    const body = css.replace(/\/\*[\s\S]*?\*\//g, "");
    const block = /input::placeholder,\s*textarea::placeholder \{([^}]*)\}/.exec(body);
    expect(block, "找不到 input/textarea::placeholder 兜底规则").toBeTruthy();
    expect(block![1]).toMatch(/color:\s*var\(--text-secondary\)/);
    expect(block![1]).toMatch(/opacity:\s*1/);
  });

  it(".paper-input 的 placeholder 不再用低 alpha 斜杠文字类", () => {
    const body = css.replace(/\/\*[\s\S]*?\*\//g, "");
    const paperInput = /\.paper-input \{[\s\S]*?\}/.exec(body);
    expect(paperInput).toBeTruthy();
    expect(paperInput![0]).not.toMatch(/placeholder:text-paper-muted\/\d/);
  });
});
