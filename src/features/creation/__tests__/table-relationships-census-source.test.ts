// @vitest-environment node
/**
 * BD-254 源码账：表格关系普查（WCAG 1.3.1 表格行列对应维度）的口径与那一处属性修法。
 *
 * 这本账读文件，不读 DOM——「零结果那一行到底占几列」由 scripts/audit-table-relationships.mjs
 * 在真 app 里量。两边各钉一件事：
 *   · 判据的命门（有表头 / 列数对齐 / 不许空行 / 画得出才算测 / 空表不算绿 / 不许失明 /
 *     坏 colspan 当 1 列）谁简化回去，本账当场红。
 *   · 修法只许是给空态单元格补 aria-colspan：不许把空态行搬出表格、不许降 role、
 *     不许改回 HTML colspan（ARIA 表格上不生效）。
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const at = (rel: string) => readFileSync(path.resolve(process.cwd(), rel), "utf8");
const census = at("scripts/audit-table-relationships.mjs");
const pkg = at("package.json");
const library = at("src/features/library/LibraryPage.tsx");
const diffDialog = at("src/features/creation/history/RestoreSnapshotDialog.tsx");

/** 只取代码行：注释里要留下教训原文，不算「引用了那条写法」。 */
const codeOf = (text: string) =>
  text
    .split("\n")
    .filter((line) => !line.trim().startsWith("//") && !line.trim().startsWith("*") && !line.trim().startsWith("/*"))
    .join("\n");

const siteBlock = () => census.slice(census.indexOf("const SITES = ["), census.indexOf("const filterToZero ="));

describe("BD-254 普查常驻与口径", () => {
  it("命令登记在 package.json（想当一次性脚本？不算常驻）", () => {
    expect(pkg).toContain('"audit:table-relationships": "node scripts/audit-table-relationships.mjs"');
  });

  it("站点 5 个：书库表两个挂载点 × 两种状态 ＋ 只有行头的差异表负对照", () => {
    const labels = [...siteBlock().matchAll(/label: "([^"]+)"/g)].map((m) => m[1]);
    expect(labels).toEqual([
      "书库·有数据（负对照）",
      "书库·筛选零结果",
      "资料阅读·有数据（负对照）",
      "资料阅读·筛选零结果",
      "版本历史·恢复弹层差异表（只有行头的负对照）"
    ]);
    expect(labels.filter((l) => l.includes("负对照")).length).toBe(3);
    expect(labels.filter((l) => l.includes("零结果")).length).toBe(2);
  });

  it("两个表选择器钉住：书库那张 ARIA 表按 aria-label 取，差异表按类名取", () => {
    const code = codeOf(census);
    expect(code).toContain('const LIBRARY_TABLE = \'[role="table"][aria-label="书库书籍"]\';');
    expect(code).toContain('const DIFF_TABLE = ".history-diff-table";');
    expect([...siteBlock().matchAll(/sel: (\w+)/g)].map((m) => m[1])).toEqual([
      "LIBRARY_TABLE", "LIBRARY_TABLE", "LIBRARY_TABLE", "LIBRARY_TABLE", "DIFF_TABLE"
    ]);
  });

  it("判据①：列头或行头至少有一种，一个都没有记 noheader", () => {
    const code = codeOf(census);
    expect(code).toContain("if (!obs.colHeaders && !obs.rowHeaders) {");
    expect(code).toContain('return { level: "noheader"');
    expect(code).toContain('colHeaders: t.querySelectorAll(\'th, [role="columnheader"]\').length');
    expect(code).toContain('rowHeaders: t.querySelectorAll(\'[role="rowheader"], tbody th\').length');
  });

  it("判据②：有效列数按 colspan 累加，只有行头的表以首行宽度为准", () => {
    const code = codeOf(census);
    expect(code).toContain("return (row.spans ?? []).reduce((sum, raw) => sum + spanOf(raw), 0);");
    expect(code).toContain("const expect = obs.colHeaders || widthOf(rows[0]);");
    expect(code).toContain("else if (width !== expect) bad.push(");
    expect(code).toContain('c.getAttribute("aria-colspan") || c.getAttribute("colspan") || ""');
  });

  it("坏 colspan 当 1 列：不许裸 parseFloat 把 0/乱码吃成 0 列而掩盖列数不对", () => {
    const code = codeOf(census);
    expect(code).toContain('const n = Number.parseInt(String(raw ?? ""), 10);');
    expect(code).toContain("return Number.isFinite(n) && n > 0 ? n : 1;");
    expect(code).not.toContain("Number.parseFloat(raw)");
  });

  it("判据③：一个单元格都没有的行单独报（不混进列数不对）", () => {
    const code = codeOf(census);
    expect(code).toContain("if (width === 0) bad.push(");
    expect(code).toContain("一个单元格都没有");
  });

  it("判据④⑤：画不出来记 invisible、0 行记 unproven，都不算绿", () => {
    const code = codeOf(census);
    expect(code).toContain('if (!obs.painted) return { level: "invisible"');
    expect(code).toContain('if (rows.length === 0) return { level: "unproven"');
    expect(code).toContain("const painted = rect.width > 1 && rect.height > 1 && !t.closest('[aria-hidden=\"true\"]');");
  });

  it("判据⑥：测到 0 个站点先报失明红；表缺席记 absent 只跳过", () => {
    const code = codeOf(census);
    expect(code).toContain('if (!measured) rows.push({ level: "red"');
    expect(code).toContain("普查失明");
    expect(code).toContain('if (!obs) return { level: "absent"');
  });

  it("单元格只数本行直属的（:scope >），嵌套表格的行按归属过滤", () => {
    const code = codeOf(census);
    expect(code).toContain("const CELL = ':scope >");
    expect(code).toContain('const owns = (row) => row.closest(\'table, [role="table"], [role="grid"], [role="treegrid"]\') === t;');
    expect(code).toContain(".filter(owns)");
  });

  it("零结果态要真进去：数据行没清空就抛，不拿「有数据」那张表冒充", () => {
    const code = codeOf(census);
    expect(code).toContain('if (rows !== 0) throw new Error(`筛选零结果没进去（还剩 ${rows} 行数据）`);');
    expect(code).toContain('const NO_MATCH = "普查不可能匹配的书名XYZ";');
  });

  it("每站进站先清筛选（筛选词跨屏不清，量具踩过）", () => {
    const code = codeOf(census);
    expect(code).toContain("const clearFilter = async () => {");
    const enters = code.slice(code.indexOf("const ENTER = {"), code.indexOf("/* ---------- 页内量具"));
    expect([...enters.matchAll(/await clearFilter\(\);/g)].length).toBe(4);
    expect(census).toContain("筛选词存在共享 store 里");
  });

  it("差异表站点现造里程碑再开恢复弹层，每一步缺席都抛", () => {
    const code = codeOf(census);
    expect(code).toContain('throw new Error("「创建里程碑」不可用，差异表站点建不起来");');
    expect(code).toContain('throw new Error("里程碑没有可选对象（场景种子没建出来）");');
    expect(code).toContain('throw new Error("快照列表里没有「恢复」钮");');
    expect(code).toContain('throw new Error("恢复弹层里没有对象级差异表");');
  });

  it("自证针 12 根，且喂进同一个 levelOf()/verdicts()（不另写一套判定）", () => {
    const block = census.slice(census.indexOf("function runNeedles()"));
    const names = [...block.matchAll(/results\.push\(\["([A-L]) /g)].map((m) => m[1]);
    expect(names).toEqual(["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"]);
    expect(block).toContain('const judge = (obs) => verdicts([{ site: "针", ...levelOf(SITE, obs) }], 1);');
    expect(block).toContain('has(verdicts([], 0), "red")');
    expect(block).not.toContain('level: "mismatch"');
    expect(block).not.toContain('level: "noheader"');
  });

  it("退出码分级：针没过 / 红 / 跳过 / 全绿各一条标记", () => {
    const code = codeOf(census);
    for (const marker of ["TABLE_REL_NEEDLE_FAIL", "TABLE_REL_RED", "TABLE_REL_SKIP", "TABLE_REL_OK"]) {
      expect(code).toContain(marker);
    }
    expect(code).toContain("process.exit(2);");
    expect(code.indexOf("TABLE_REL_NEEDLE_FAIL")).toBeLessThan(code.indexOf("TABLE_REL_RED"));
  });

  it("与既有 1.3.1 三腿的分工写在 header 里", () => {
    expect(census).toContain("audit-list-structure.mjs");
    expect(census).toContain("audit-landmarks.mjs");
    expect(census).toContain("audit-heading-order.mjs");
    expect(census).toContain("BD-178");
    expect(census).toContain("https://www.w3.org/WAI/WCAG22/Understanding/info-and-relationships.html");
  });

  it("红基线与干净基线逐字记档（红 2 处 / 绿 0 处，站点 5 个）", () => {
    expect(census).toContain("关系问题 2 处｜对得上 3 处");
    expect(census).toContain("站点 5 个｜实际测到 5 个｜关系问题 0 处");
    expect(census).toContain("/tmp/bd254-red2.log");
    expect(census).toContain("/tmp/bd254-green.log");
  });

  it("牙齿记录：运行时 T1…T9 ＋ 静态 S1…S20，每档还原并重建", () => {
    for (const tooth of ["T1 ", "T2 ", "T3 ", "T4 ", "T5 ", "T6 ", "T7 ", "T8 ", "T9 "]) {
      expect(census).toContain(tooth);
    }
    expect(census).toContain("静态 S1…S20");
    expect(census).toContain("立刻逐字节还原**并重建**");
  });

  it("产品修法：空态单元格 aria-colspan={6}，与 6 个列头对齐", () => {
    expect(library).toContain('<div role="cell" aria-colspan={6} className="py-10 text-center text-sm text-paper-muted">');
    const heads = [...library.matchAll(/role="columnheader"/g)].length;
    expect(heads).toBe(6);
    expect(library).toContain("BD-254");
  });

  it("修法不许退化：空态行仍在表内、仍是 row/cell，不许改回 HTML colspan", () => {
    const block = library.slice(library.indexOf("{filteredAndSorted.length === 0 ?"), library.indexOf("filteredAndSorted.map((book)"));
    expect(block).toContain('<div role="row">');
    expect(block).toContain('role="cell"');
    expect(block).not.toContain("role=\"presentation\"");
    expect(block).not.toMatch(/\scolspan=/);
    expect(block).toContain("aria-colspan={6}");
  });

  it("只有行头的那张差异表保持原样（负对照不许被顺手加列头）", () => {
    expect(diffDialog).toContain('role="table" aria-label="对象级差异"');
    expect(diffDialog).toContain('role="rowheader"');
    expect(diffDialog).not.toContain('role="columnheader"');
  });
});