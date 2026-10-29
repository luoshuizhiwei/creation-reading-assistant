// @vitest-environment jsdom
/**
 * BD-214 账本：WCAG 3.3.2 标签或说明（A）+ 4.1.2 表单控件名称。
 *
 * 主判据是运行时普查 scripts/audit-form-labels.mjs：14 个页面级视图里逐个可见输入控件，
 * 必须拿到**持续存在**的标签——自身 aria-label / aria-labelledby / label[for] /
 * 包裹 label 的文字。placeholder 与 title 都不算：前者一输入就消失，后者只是悬浮提示。
 *
 * 本批真实缺陷：卡片库搜索框把 aria-label 挂在了包裹它的 <label> 上。label 的 aria-label
 * 只改 label 自己的名字，不会成为里层 input 的可访问名；输入框实际只剩会把字吃掉一半的
 * placeholder。修复：标签挪到 input 本身。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const census = read("../../../../../scripts/audit-form-labels.mjs");
const library = read("../GlobalCardLibraryPage.tsx");
const pkg = read("../../../../../package.json");

describe("BD-214 普查判据：持续标签", () => {
  it("红/绿结论、退出码与四条标签来源都在", () => {
    expect(census).toContain("FORM_LABEL_RED");
    expect(census).toContain("FORM_LABEL_OK");
    expect(census).toContain("process.exit(1)");
    expect(census).toContain("const byRef = labelledby");
    expect(census).toContain("const aria = (el.getAttribute(\"aria-label\") || \"\").trim();");
    expect(census).toContain("label[for=");
    expect(census).toContain("const wrap = el.closest(\"label\")?.textContent?.trim() || \"\";");
  });

  it("placeholder 与 title 明确不算标签（否则缺陷会被普查放过）", () => {
    expect(census).toContain("placeholder 不算标签");
    expect(census).toContain("title 只算补充提示");
    expect(census).toContain("if (ownLabel(el)) continue;");
  });

  it("14 个范围与 npm 脚本", () => {
    for (const label of ["项目·首页", "项目·写作台", "项目·设定卡（列表）", "书库", "卡片库", "收件箱", "资料阅读", "设置"]) {
      expect(census).toContain(`["${label}"`);
    }
    expect(pkg).toContain('"audit:form-labels": "node scripts/audit-form-labels.mjs"');
  });
});

describe("BD-214 产品锚：卡片库搜索框的标签在自己身上", () => {
  it("input 自带 aria-label，包裹 label 不再挂 aria-label", () => {
    expect(library).toContain('aria-label="搜索卡片名称、别名或字段"');
    expect(library).not.toContain('<label className="cards-search" aria-label=');
    const input = library.slice(library.indexOf('<label className="cards-search">'));
    expect(input.slice(0, 400)).toContain('aria-label="搜索卡片名称、别名或字段"');
  });

  it("同名搜索框在其他页面都是 input 自带名字（口径一致）", () => {
    const cardsPage = read("../CardsPage.tsx");
    expect(cardsPage).toContain('aria-label="搜索卡片名称或别名"');
  });
});
