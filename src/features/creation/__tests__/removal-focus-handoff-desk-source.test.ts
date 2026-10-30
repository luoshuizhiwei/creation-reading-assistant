// Node 环境源码账：BD-221 的详情面板/侧栏条目删除焦点交接不许被拆回散装。
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

function read(rel: string): string {
  return readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
}

describe("BD-221 源码账：背景卡与批注删除的焦点交接", () => {
  it("BackgroundPage：预约记在 await 前、删空退空态文案", () => {
    const code = read("../background/BackgroundPage.tsx");
    expect(code, "BackgroundPage 缺 BD-221 判据注释").toContain("BD-221");
    const reserveAt = code.indexOf(
      "pendingCardFocusRef.current = { id: selected.id, order: backgroundCards.map((card) => card.id) };"
    );
    expect(reserveAt, "BackgroundPage：删除流丢了交接预约").toBeGreaterThan(-1);
    const awaitAt = code.indexOf("await runStructure({ type: \"card.delete\", cardId: selected.id });", reserveAt);
    expect(awaitAt, "BackgroundPage：预约必须记在 await 之前").toBeGreaterThan(reserveAt);
    expect(code, "BackgroundPage：删除失败时必须作废预约，避免下一次刷新抢焦点").toContain(
      "pendingCardFocusRef.current = null;"
    );
    expect(code, "BackgroundPage：条目没走时必须在动焦点前返回").toContain("if (current.includes(pending.id)) return;");
    expect(code, "BackgroundPage：删空退路被拆").toContain(
      "(target ?? (current.length === 0 ? emptyRef.current : undefined) ?? gridRef.current)?.focus({ preventScroll: true });"
    );
    expect(code, "BackgroundPage：卡片钮不再登记 ref，交接无从找回").toContain(
      "cardButtonRefs.current.set(card.id, node)"
    );
    expect(code, "BackgroundPage：空态文案不再是可聚焦退路").toContain('ref={emptyRef} tabIndex={-1}');
    expect(code, "BackgroundPage：网格不再是可聚焦退路").toContain('className="background-grid" ref={gridRef} tabIndex={-1}');
    expect(code, "BackgroundPage：交接 effect 的依赖列表变了").toContain("}, [cards, projectId, search]);");
  });

  it("WritingDeskMargin：预约记在删除前、行序取自当前列表、删空退空态文案", () => {
    const code = read("../editor/desk/WritingDeskMargin.tsx");
    expect(code, "WritingDeskMargin 缺 BD-221 判据注释").toContain("BD-221");
    const reserveAt = code.indexOf(
      "pendingAnnotationFocusRef.current = { id: annotation.id, order: annotations.map((item) => item.id) };"
    );
    expect(reserveAt, "WritingDeskMargin：删除流丢了交接预约").toBeGreaterThan(-1);
    const deleteAt = code.indexOf("void deleteAnnotation({ annotationId: annotation.id })", reserveAt);
    expect(deleteAt, "WritingDeskMargin：预约必须记在删除动作之前").toBeGreaterThan(reserveAt);
    expect(code, "WritingDeskMargin：删除失败时必须作废预约，避免下一次刷新抢焦点").toContain(
      "if (!ok) pendingAnnotationFocusRef.current = null;"
    );
    expect(code, "WritingDeskMargin：条目没走时必须在动焦点前返回").toContain("if (current.includes(pending.id)) return;");
    expect(code, "WritingDeskMargin：删空退路被拆").toContain(
      "(target ?? annotationEmptyRef.current ?? annotationListRef.current)?.focus({ preventScroll: true });"
    );
    expect(code, "WritingDeskMargin：删除钮不再登记 ref，交接无从找回").toContain(
      "annotationDeleteRefs.current.set(annotation.id, node)"
    );
    expect(code, "WritingDeskMargin：空态文案不再是可聚焦退路").toContain('ref={annotationEmptyRef} tabIndex={-1}');
    expect(code, "WritingDeskMargin：批注列表不再是可聚焦退路").toContain('<ul ref={annotationListRef} tabIndex={-1}>');
    expect(code, "WritingDeskMargin：交接 effect 的依赖列表变了").toContain("}, [annotations]);");
  });
});
