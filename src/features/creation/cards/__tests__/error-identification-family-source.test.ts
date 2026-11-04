// @vitest-environment node
/**
 * BD-244（WCAG 3.3.1 错误识别）静态账：重复项表单的校验文案必须点名重复项，
 * 并且把错误挂到那一格控件上。
 *
 * 与运行时那本的分工：npm run audit:error-identification（scripts/audit-error-identification.mjs）
 * 量活体行为——真去点「创建类型」，看文案换没换字、控件亮没亮；这本静态账管「形状不许塌」：
 * 全仓凡是「用 map 渲染重复必填行 + 有表单级报错」的站点，一起登记造册，
 * 不许再出现一句盖住所有行的概括文案。
 *
 * 立账依据（运行时实测 /tmp/bd244-probe.log）：卡片类型编辑器两行字段，
 * 第 1 行、第 2 行的「名称」各留空一次，两次都是同一句
 * 「每个字段都需要填写名称（label）。」——换位测试没换字；六种失败态（A0～A5）全部
 * 「带 aria-invalid=true 的控件：0 ｜ 有 aria-describedby 且目标存在的：0」。
 */
import { readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const here = path.dirname(fileURLToPath(import.meta.url));
const srcRoot = path.resolve(here, "../../../.."); // src/

function walk(dir: string): string[] {
  const out: string[] = [];
  for (const name of readdirSync(dir)) {
    if (name === "node_modules" || name.startsWith(".")) continue;
    const p = path.join(dir, name);
    if (statSync(p).isDirectory()) out.push(...walk(p));
    else if (/\.(tsx|ts)$/.test(name)) out.push(p);
  }
  return out;
}

const sources = () =>
  walk(srcRoot)
    .filter((p) => !p.includes("__tests__"))
    .map((p) => ({ rel: path.relative(srcRoot, p).split(path.sep).join("/"), text: readFileSync(p, "utf8") }));

/** 「重复必填行 + 表单级报错」的站点：行用 map(…, index) 渲染、格上有 aria-required、错误走 setError。 */
const repeatRowValidationSites = () =>
  sources().filter(
    ({ text }) =>
      /\.map\(\s*\(?[A-Za-z_]+,\s*index\)?\s*=>/.test(text) &&
      /set(?:Save)?Error\(/.test(text) &&
      text.includes('aria-required="true"')
  );

describe("BD-244 重复项校验的点名账（3.3.1 静态形状）", () => {
  it("站点表不为空——重复行表单在本仓确实存在，这本账不是空转", () => {
    const sites = repeatRowValidationSites();
    expect(sites.length).toBeGreaterThanOrEqual(1);
    expect(sites.map((s) => s.rel)).toContain("features/creation/cards/CardTypeEditor.tsx");
  });

  it("每一处重复行校验都给自己留了行号插值，不许再来一句『每个字段都需要…』", () => {
    const offenders: string[] = [];
    for (const { rel, text } of repeatRowValidationSites()) {
      const messages = [...text.matchAll(/(?:show|set)Error\(\s*[`"]([^`"]*)[`"]/g)].map((m) => m[1]);
      const blanket = messages.filter((m) => /每个字段|所有字段|各项/.test(m));
      if (blanket.length > 0) offenders.push(`${rel}：概括式文案没点名 → ${blanket.join(" / ")}`);
      const rowNamed = /第 \$\{[^}]*index[^}]*\} 个/.test(text) || /第 \$\{[^}]*\} 行/.test(text);
      if (!rowNamed) offenders.push(`${rel}：字段级文案没有插值行号`);
    }
    expect(offenders).toEqual([]);
  });

  it("出错控件的关联两头都在：错误元素有固定 id，控件用 aria-invalid + aria-describedby 指过去", () => {
    const pairs: Record<string, string> = {
      "features/creation/cards/CardTypeEditor.tsx": "card-type-form-error",
      "features/creation/cards/RelationTypeEditor.tsx": "relation-type-form-error",
      "features/creation/outline/OutlineTree.tsx": "outline-prompt-error"
    };
    const all = sources();
    const notes: string[] = [];
    for (const [rel, errorId] of Object.entries(pairs)) {
      const text = all.find((s) => s.rel === rel)?.text ?? "";
      expect(text, `${rel} 还在原位`).toBeTruthy();
      expect(text).toContain(errorId);
      expect(text).toContain("aria-invalid");
      expect(text).toContain("aria-describedby");
      // 错误元素自己得挂上这个 id（字面量 id="…"，或 id={常量} 且该常量就等于这个 id）。
      // 带不带 role="alert" 由运行时那本管，这里只钉「指得到人」。
      let holderOk = new RegExp(`id="${errorId}"`).test(text);
      for (const m of text.matchAll(/id=\{([A-Za-z0-9_]+)\}/g)) {
        const decl = new RegExp(`const ${m[1]}\\s*(?::[^=]+)?=\\s*"${errorId}"`);
        if (decl.test(text)) {
          holderOk = true;
          break;
        }
      }
      if (!holderOk) notes.push(`${rel} 没有承载 id=${errorId} 的元素`);
      expect(notes).toEqual([]);
    }
  });

  it("卡片类型编辑器：五类字段级失败态各有各的 target id，旧那句概括文案不许回来", () => {
    const text = sources().find((s) => s.rel === "features/creation/cards/CardTypeEditor.tsx")?.text ?? "";
    for (const id of ["cte-type-name", "cte-field-label-", "cte-field-key-", "cte-field-options-", "cte-field-default-"]) {
      expect(text, `缺 target id：${id}`).toContain(id);
    }
    // 当年那句全体概括的文案不许回来（注释里引用它做对照是允许的，只查真的发出去的那些）。
    expect(text).not.toMatch(/(?:show|set)Error\(\s*"[^"\n]*每个字段/);
    expect(text).not.toMatch(/(?:show|set)Error\(\s*`[^`\n]*每个字段/);
  });

  it("关系类型编辑器：缺哪项点哪项，两项都缺才说两句", () => {
    const text = sources().find((s) => s.rel === "features/creation/cards/RelationTypeEditor.tsx")?.text ?? "";
    expect(text).toContain('missing.push("正向名称")');
    expect(text).toContain('missing.push("反向名称")');
    expect(text).toContain("请填写${missing.join");
    expect(text).not.toContain('请填写正向名称和反向名称。");');
  });
});
