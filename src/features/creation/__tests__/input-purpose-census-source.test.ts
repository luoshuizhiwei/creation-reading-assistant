// @vitest-environment node
/**
 * BD-255 源码账：输入用途普查（WCAG 1.3.5 Identify Input Purpose）的口径与那一处用词修法。
 *
 * 这本账读文件，不读 DOM——「解锁框的 autocomplete 到底是什么」由
 * scripts/audit-input-purpose.mjs 在真 app 里量。两边各钉一件事：
 *   · 判据的命门（用途词合法 / 新旧口令不许搞反 / 只管用户自身信息 / 画得出才算测 /
 *     空屏不算绿 / 不许失明 / 大小写归一）谁简化回去，本账当场红。
 *   · 修法只许是把 confirm 分支从 off 改成 current-password：不许改成 on、
 *     不许把新口令那两框也一起改、不许把 autocomplete 整个摘掉。
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const at = (rel: string) => readFileSync(path.resolve(process.cwd(), rel), "utf8");
const census = at("scripts/audit-input-purpose.mjs");
const pkg = at("package.json");
const dialog = at("src/features/settings/encryption/PassphraseDialog.tsx");
const modesTest = at("src/features/settings/encryption/__tests__/passphrase-dialog-modes.test.tsx");

/** 只取代码行：注释里要留下教训原文，不算「引用了那条写法」。 */
const codeOf = (text: string) =>
  text
    .split("\n")
    .filter((line) => !line.trim().startsWith("//") && !line.trim().startsWith("*") && !line.trim().startsWith("/*"))
    .join("\n");

const siteBlock = () => census.slice(census.indexOf("const SITES = ["), census.indexOf("/* 页内读数"));
const purposeBlock = () => census.slice(census.indexOf("const PURPOSES = ["), census.indexOf("const STORAGE_ACTIONS"));

describe("BD-255 普查常驻与口径", () => {
  it("命令登记在 package.json（想当一次性脚本？不算常驻）", () => {
    expect(pkg).toContain('"audit:input-purpose": "node scripts/audit-input-purpose.mjs"');
  });

  it("站点 5 个：两个口令弹层 ＋ 三个应用内容负对照", () => {
    const labels = [...siteBlock().matchAll(/label: "([^"]+)"/g)].map((m) => m[1]);
    expect(labels).toEqual([
      "设置·存储（负对照：应用内容框）",
      "弹层·设置加密备份口令（新口令两框）",
      "弹层·恢复加密备份口令（已有口令）",
      "项目·写作台（负对照：应用内容框）",
      "收件箱（负对照：应用内容框）"
    ]);
    expect(labels.filter((l) => l.includes("负对照"))).toHaveLength(3);
    expect(labels.filter((l) => l.includes("口令"))).toHaveLength(2);
  });

  it("两个口令弹层进的是不同 mode：create 等确认框，confirm 见到确认框就抛错", () => {
    expect(census).toContain('await page.waitForSelector(\'[data-testid="pe-confirm"]\'');
    expect(census).toContain("进的是设置新口令弹层，不是解锁弹层");
    expect(census).toContain('hasText: "导出加密备份"');
    expect(census).toContain('hasText: "恢复加密备份"');
    expect(census).toContain('button:has-text("选择备份文件")');
  });

  it("判据①：用途词必须落在 WCAG 词表里，off / on 不算用途", () => {
    const words = purposeBlock();
    expect(words).toContain('"current-password"');
    expect(words).toContain('"new-password"');
    expect(words).toContain('"one-time-code"');
    expect(words).toContain('"username"');
    expect(words).not.toContain('"off"');
    expect(words).not.toContain('"on"');
    expect(codeOf(census)).toContain("if (!PURPOSES.includes(token)) {");
  });

  it("判据②：新旧口令不许搞反，期望从可见标签推而不是写死在站点表", () => {
    expect(codeOf(census)).toContain("const existing = /解锁|当前口令|已有口令|原口令/.test(labelText);");
    expect(codeOf(census)).toContain('const expect = credential ? (existing ? "current-password" : "new-password") : "";');
    expect(codeOf(census)).toContain("} else if (f.expect && token !== f.expect) {");
    expect(siteBlock()).not.toContain("current-password");
  });

  it("判据③：应用内容框不参与判定（项目名/筛选/目录/速记不是用户自身信息）", () => {
    expect(codeOf(census)).toContain('const kind = credential || personal ? "user" : "content";');
    expect(codeOf(census)).toContain('const judged = painted.filter((f) => f.kind === "user");');
    expect(census).toContain("按③不在 1.3.5 范围里（负对照成立）");
  });

  it("判据④⑤⑥：画不出跳过、空屏跳过、失明红，一条都不许省", () => {
    expect(codeOf(census)).toContain('if (fields.length === 0) return { level: "unproven"');
    expect(codeOf(census)).toContain('if (painted.length === 0) return { level: "invisible"');
    expect(codeOf(census)).toContain('return { level: "absent"');
    expect(codeOf(census)).toContain('if (!measured) rows.push({ level: "red"');
    expect(census).toContain("分母为 0 的「绿」不算通过");
  });

  it("读到的全是应用内容框也算测到（负对照不许被当成没测）", () => {
    const branch = census.slice(census.indexOf("if (judged.length === 0) {"), census.indexOf("const bad = [];"));
    expect(branch).toContain('return { level: "ok"');
    expect(branch).not.toContain("unproven");
    expect(codeOf(census)).toContain('if (["ok", "bad"].includes(record.level)) measured += 1;');
  });

  it("大小写归一：autocomplete 取值 ASCII 大小写不敏感，口径不许比 HTML 严", () => {
    expect(codeOf(census)).toContain('.trim().toLowerCase();');
    expect(codeOf(census)).toContain("const token = tokenOf(f.ac);");
  });

  it("画得出的口径同时看尺寸与 aria-hidden 子树", () => {
    expect(codeOf(census)).toContain('const hidden = Boolean(el.closest(\'[aria-hidden="true"]\'));');
    expect(codeOf(census)).toContain("painted: r.width >= 2 && r.height >= 2 && !hidden");
  });

  it("14 条自证针喂进同一个 levelOf() + verdicts()，不另写判定条件", () => {
    for (const needle of ["A ", "B ", "C ", "D ", "E ", "F ", "G ", "H ", "I ", "J ", "K ", "L ", "M ", "N "]) {
      expect(census).toContain(`results.push(["${needle}`);
    }
    expect(codeOf(census)).toContain("const judge = (fields) => verdicts([{ site: \"针\", ...levelOf(SITE, { fields }) }], 1);");
    const needleBlock = census.slice(census.indexOf("function runNeedles()"));
    expect(needleBlock).not.toContain("getBoundingClientRect");
    expect(needleBlock).not.toContain("document.querySelector");
  });

  it("退出码分级：针坏 / 有红 / 有跳过 / 全绿各走各的", () => {
    expect(census).toContain("INPUT_PURPOSE_NEEDLE_FAIL");
    expect(census).toContain("INPUT_PURPOSE_RED");
    expect(census).toContain("INPUT_PURPOSE_SKIP");
    expect(census).toContain("INPUT_PURPOSE_OK");
    const skipAt = census.indexOf('console.log("\\nINPUT_PURPOSE_SKIP');
    expect(census.slice(skipAt, skipAt + 200)).toContain("process.exit(2)");
  });

  it("与既有账分工写清楚：名字腿不管用途，3.3.8 摸过是干净的", () => {
    expect(census).toContain("audit-form-labels.mjs");
    expect(census).toContain("audit-label-in-name.mjs");
    expect(census).toContain("audit-error-identification.mjs");
    expect(census).toContain("3.3.8");
    expect(census).toContain("https://www.w3.org/WAI/WCAG22/Understanding/identify-input-purpose.html");
    expect(census).toContain("https://www.w3.org/TR/WCAG22/#input-purposes");
  });

  it("另一个挂载点没活体走到，如实记档而不是假装测过", () => {
    expect(census).toContain("CreationProjectsPage.tsx:722");
    expect(census).toContain("本账**没有**活体走到它");
  });

  it("红基线与干净基线逐字记档（红 1 处 / 绿 0 处，站点 5 个）", () => {
    expect(census).toContain("用途问题 1 处｜说得清 4 处");
    expect(census).toContain("站点 5 个｜实际测到 5 个｜用途问题 0 处");
    expect(census).toContain("/tmp/bd255-red.log");
    expect(census).toContain("/tmp/bd255-green.log");
    expect(census).toContain("/tmp/bd255c-probe.log");
  });

  it("牙齿记录：运行时 T1…T9 ＋ 静态 S1…S18，每档还原并重建", () => {
    for (const tooth of ["T1 ", "T2 ", "T3 ", "T4 ", "T5 ", "T6 ", "T7 ", "T8 ", "T9 "]) {
      expect(census).toContain(tooth);
    }
    expect(census).toContain("静态 S1…S18");
    expect(census).toContain("立刻逐字节还原**并重建**");
    // T8 的两处勘误必须留在账里：期望是五站，且这一档针拦不住（分类在页内）。
    expect(census).toContain("**五站全红**");
    expect(census).toContain("这一档**针拦不住**");
    expect(census).toContain("-runtime3.log");
  });

  it("产品修法：confirm 分支是 current-password，create 分支仍是 new-password", () => {
    expect(dialog).toContain('autoComplete={isCreate ? "new-password" : "current-password"}');
    expect(dialog).toContain('autoComplete="new-password"');
    expect(dialog).toContain("BD-255");
    const acLines = dialog.split("\n").filter((line) => line.includes("autoComplete="));
    expect(acLines).toHaveLength(2);
  });

  it("修法不许退化：不许改回 off、不许用 on、不许把 autocomplete 摘掉", () => {
    const code = codeOf(dialog);
    expect(code).not.toContain('"off"');
    expect(code).not.toContain('autoComplete="on"');
    expect(code).not.toContain("autoComplete={undefined}");
    expect(code).toContain('type="password"');
  });

  it("原来那条把 off 钉成契约的单测已改成钉 current-password", () => {
    expect(modesTest).toContain('expect(ac).toBe("current-password")');
    expect(modesTest).toContain('expect(ac).not.toBe("off")');
    expect(modesTest).not.toMatch(/(?<!not\.)toBe\("off"\)/);
  });
});