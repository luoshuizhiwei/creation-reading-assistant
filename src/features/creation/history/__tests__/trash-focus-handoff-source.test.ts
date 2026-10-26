// Node 环境源码账：BD-197 的交接接线不许被拆回散装。
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

function read(rel: string): string {
  return readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
}

describe("BD-197 源码账：回收站行卸载焦点交接", () => {
  it("HistoryPage：恢复与永久删除都预约交接，取消路径作废，成功路径保留", () => {
    const code = read("../HistoryPage.tsx");
    expect(code).toContain("BD-197");
    // 两个动作入口都要在条目卸载前记行序快照。
    expect(code, "恢复流丢了交接预约").toMatch(/handleRestoreTrash[\s\S]{0,200}pendingFocusRef\.current = \{ key: rowKey\(item\)/);
    expect(code, "永久删除流丢了交接预约").toMatch(/handleOpenPurge[\s\S]{0,400}pendingFocusRef\.current = \{ key: rowKey\(item\)/);
    // 取消作废预约；成功关闭走独立回调（PurgeTrashDialog 借用 onCancel 关窗的历史坑）。
    expect(code).toMatch(/handleCancelPurge[\s\S]{0,200}pendingFocusRef\.current = null/);
    expect(code, "成功关闭必须不复用取消回调").toMatch(/onCloseSuccess=\{handleClosePurged\}/);
    // 删空的退路锚点。
    expect(code).toMatch(/listRef\.current \?\? emptyRef\.current/);
    expect(code).toMatch(/<ul className="history-list" ref=\{listRef\} tabIndex=\{-1\}/);
  });

  it("PurgeTrashDialog：确认成功改走 onCloseSuccess，取消语义不变", () => {
    const code = read("../PurgeTrashDialog.tsx");
    expect(code).toContain("BD-197");
    expect(code, "成功路径又借道 onCancel（会误清交接预约）").toMatch(/\(onCloseSuccess \?\? onCancel\)\(\)/);
    expect(code).toMatch(/onCloseSuccess\?: \(\) => void/);
  });

  it("GlobalCardLibraryPage：回收站恢复预约交接、关窗作废", () => {
    const code = read("../../cards/GlobalCardLibraryPage.tsx");
    expect(code).toContain("BD-197");
    expect(code, "恢复流丢了交接预约").toMatch(/restoreCard = async[\s\S]{0,200}pendingTrashFocusRef\.current = \{ id: item\.id/);
    expect(code, "关窗没作废预约").toMatch(/pendingTrashFocusRef\.current = null/);
    expect(code).toMatch(/<ul className="global-card-trash-list" ref=\{trashListRef\} tabIndex=\{-1\}/);
  });
});
