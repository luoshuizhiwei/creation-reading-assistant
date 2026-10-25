// @vitest-environment node
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * BD-187 源码账：polite 播报区不得「随内容一起挂载」。
 *
 * role="status" 节点在内容出现那一刻才插入 DOM 时，读屏器不会回读（BD-175/180 已
 * 判据），所以播报必须由一条常驻区承载、状态变化只换它的文本。jsdom 直接断言「播报
 * 了」不可能，这里钉两件可静态核对的事：
 *  1) 每个案发文件都有一条常驻 `sr-only role="status"` 播报区；
 *  2) 当年那些「可见状态块自带 role="status"」的宿主类名，不得再与 role="status"
 *     同标签共存（一旦回退，播报就重新变回随内容挂载）。
 */

function read(rel: string): string {
  return readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf-8");
}

const PERSISTENT = /<span className="sr-only" role="status">/;

const sites: Array<{ file: string; banned: RegExp[] }> = [
  { file: "../command/CommandPalette.tsx", banned: [/className="creation-search-state" role="status"/] },
  {
    file: "../proof/ProofPanel.tsx",
    banned: [/creation-proof-summary[^>]*role="status"/, /creation-search-state"[^>]*role="status"/]
  },
  { file: "../editor/WritingQuickReferencePanel.tsx", banned: [/writing-quick-empty"[^>]*role="status"/] },
  { file: "../editor/desk/WritingDeskOutlineSidebar.tsx", banned: [/writing-outline-revert"[^>]*role="status"/] },
  {
    file: "../history/RestoreSnapshotDialog.tsx",
    banned: [/history-impact-loading"[^>]*role="status"/, /history-restore-success"[^>]*role="status"/]
  },
  { file: "../history/PurgeTrashDialog.tsx", banned: [/history-impact-loading"[^>]*role="status"/] },
  { file: "../operation/OperationProgressDialog.tsx", banned: [/operation-result[^>]*role="status"/] }
];

describe("BD-187 源码账：条件挂载的 polite 播报区", () => {
  for (const site of sites) {
    it(`${site.file.split("../").pop()}：常驻播报区存在，可见状态块不再自带 role="status"`, () => {
      const src = read(site.file);
      expect(src.match(PERSISTENT)?.length ?? 0, "缺少常驻 sr-only 播报区").toBeGreaterThanOrEqual(1);
      for (const banned of site.banned) {
        expect(banned.test(src), `回退：${banned.source} 又把播报挂回了条件节点`).toBe(false);
      }
    });
  }
});
