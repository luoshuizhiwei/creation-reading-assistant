/**
 * 批次 BD-101：脚本选择器审计进 `npm test`。
 *
 * 审计本身 0.2 秒，扫的是「验收脚本还在不在瞄着现界面」——这类腐烂没有产品代码
 * 的编译错误兜底，只能靠一条常驻的静态检查。除了跑真仓库，还建一个临时夹具，
 * 把「类名不存在」和「整词边界」两条牙各咬一次，确认它不是永远绿。
 */
import { spawnSync } from "node:child_process";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));

describe("脚本选择器审计", () => {
  it("所有字面量选择器的类名都要在 src/ 与 electron/ 里有出处", () => {
    const r = spawnSync(process.execPath, ["scripts/audit-script-selectors.mjs"], {
      cwd: root,
      encoding: "utf8",
    });
    expect(r.status, r.stderr || r.stdout).toBe(0);
    expect(r.stdout).toContain("SELECTOR_AUDIT_OK");
  }, 30_000);

  it("指向不存在的类名时必须红，且按整词比对", () => {
    const fixture = mkdtempSync(path.join(os.tmpdir(), "selector-audit-"));
    try {
      mkdirSync(path.join(fixture, "scripts"), { recursive: true });
      mkdirSync(path.join(fixture, "src"), { recursive: true });
      writeFileSync(
        path.join(fixture, "scripts", "probe.mjs"),
        [
          'await page.waitForSelector(".gone-class");',
          'await page.locator(".present");',
          'await page.locator(".present-class");',
          'await page.locator("button");',
          "",
        ].join("\n"),
        "utf8"
      );
      writeFileSync(
        path.join(fixture, "src", "App.tsx"),
        'export const App = () => <div className="present-class" />;\n',
        "utf8"
      );

      const r = spawnSync(process.execPath, ["scripts/audit-script-selectors.mjs", `--root=${fixture}`], {
        cwd: root,
        encoding: "utf8",
      });
      expect(r.status).toBe(1);
      expect(r.stderr).toContain(":: .gone-class  →  .gone-class");
      // .present 是 .present-class 的前缀，整词比对必须把它也判红
      expect(r.stderr).toContain(":: .present  →  .present");
      // .present-class 有出处，不能误伤
      expect(r.stderr).not.toContain(".present-class");
    } finally {
      rmSync(fixture, { recursive: true, force: true });
    }
  }, 30_000);
});
