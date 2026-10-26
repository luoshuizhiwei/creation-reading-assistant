// Node 环境源码账：BD-195 的防线落在字面量上，不依赖运行时。
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

function read(rel: string): string {
  return readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
}

// 注释里会引述被禁的属性名（判据本身），先剥掉块注释与 // 行注释再查属性。
function codeOnly(rel: string): string {
  return read(rel)
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .replace(/\/\/[^\n]*/g, "");
}

describe("BD-195 源码账：Spinner 是纯视觉原语", () => {
  it("Spinner.tsx 内不得再出现 role=status / aria-label / aria-live，也不得恢复 label prop", () => {
    const code = codeOnly("../../components/ui/Spinner.tsx");
    expect(code).not.toMatch(/role="status"/);
    expect(code).not.toMatch(/aria-label/);
    expect(code).not.toMatch(/aria-live/);
    expect(code).not.toMatch(/label\s*=/);
    expect(code).not.toMatch(/label\?:/);
    expect(code).toMatch(/aria-hidden="true"/);
    expect(read("../../components/ui/Spinner.tsx")).toContain("BD-195");
  });

  it("全部使用点不得再向 Spinner 传 label（死了的 prop 不许借尸还魂）", () => {
    const sites = [
      "../../features/creation/export/ExportDraftDialog.tsx",
      "../../features/creation/outline/StructureImpactDialog.tsx",
      "../../features/creation/migration/MigrationDialog.tsx",
      "../../features/creation/cards/import-export/CardExportDialog.tsx",
      "../../features/creation/cards/import-export/CardImportDialog.tsx",
      "../../features/creation/operation/OperationProgressDialog.tsx",
      "../../features/creation/import/ImportDraftDialog.tsx",
      "../../features/creation/editor/SceneEditor.tsx",
      "../../features/library/EpubReaderPage.tsx"
    ];
    for (const rel of sites) {
      const code = codeOnly(rel);
      expect(code, `${rel}：又给 Spinner 传 label 了`).not.toMatch(/<Spinner[^>]*label=/);
    }
  });

  it("操作对话框：可见不确定提示块不带 aria-live，播报判据与常驻区共用同一变量", () => {
    const code = codeOnly("../../features/creation/operation/OperationProgressDialog.tsx");
    expect(code, "回退：可见提示块又挂回自带 aria-live（插入即带内容的死区）").not.toMatch(
      /className="operation-indeterminate[^"]*"[^>]*aria-live/
    );
    expect(code).toMatch(/const showingIndeterminateHint =/);
    expect(code, "常驻区换文本必须复用可见分支的同一判据").toMatch(/\{showingIndeterminateHint/);
    expect(code).toMatch(/正在处理，请稍候…/);
    expect(read("../../features/creation/operation/OperationProgressDialog.tsx")).toContain("BD-195");
  });

  it("指纹防线：Spinner 原语不得再长出 status+aria-label 双装饰", () => {
    const code = codeOnly("../../components/ui/Spinner.tsx");
    expect(code).not.toMatch(/role="status"[^>]*aria-label|aria-label[^>]*role="status"/);
  });
});
