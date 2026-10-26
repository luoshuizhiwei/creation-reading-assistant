// Node 环境源码账：BD-199 的交接接线不许被拆回散装。
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

function read(rel: string): string {
  return readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
}

describe("BD-199 源码账：卡片编辑族行卸载焦点交接", () => {
  for (const [label, rel, listExpr, awaitExpr] of [
    ["CardTypeEditor", "../CardTypeEditor.tsx", "cardTypes.map((item) => item.id)", "await actions.deleteCardType"],
    ["RelationTypeEditor", "../RelationTypeEditor.tsx", "relationTypes.map((item) => item.id)", "await actions.deleteRelationType"]
  ] as const) {
    it(`${label}：预约记在 await 前、交接链带回标题退路、假 anchor 齐备`, () => {
      const code = read(rel);
      expect(code, `${label} 缺 BD-199 判据注释`).toContain("BD-199");
      const reserveAt = code.indexOf(`pendingFocusRef.current = { key: type.id, order: ${listExpr} }`);
      const awaitAt = code.indexOf(awaitExpr);
      expect(reserveAt, `${label}：删除流丢了交接预约`).toBeGreaterThan(-1);
      expect(awaitAt, `${label}：删除 IPC 结构变了，账约失效需重订`).toBeGreaterThan(reserveAt);
      // 条目没走不动焦点的守卫必须在。
      expect(code).toMatch(/if \(current\.includes\(pending\.key\)\) return;/);
      // disabled 幸存行不可聚焦时的两级退路：列表容器 → 编辑器标题。
      expect(code, `${label}：退路链被拆薄`).toMatch(/listRef\.current \?\? titleRef\.current/);
      expect(code, `${label}：!target.hasAttribute("disabled") 守卫被拆——disabled 钮 focus() 静默失败`).toMatch(
        /!target\.hasAttribute\("disabled"\)/
      );
      expect(code).toMatch(/<section className="cards-type-list" aria-label="现有(?:卡片|关系)类型" ref=\{listRef\} tabIndex=\{-1\}/);
      expect(code).toMatch(/<h4 className="cards-type-editor-title" ref=\{titleRef\} tabIndex=\{-1\}/);
      expect(code, `${label}：删除钮不再登记 ref，交接无从找回`).toMatch(
        /rowActionRefs\.current\.set\(type\.id, node\)/
      );
    });
  }

  it("CardRelationsManager：预约记在 await 前、换卡作废预约、删空退空态段", () => {
    const code = read("../components/CardRelationsManager.tsx");
    expect(code, "CardRelationsManager 缺 BD-199 判据注释").toContain("BD-199");
    const reserveAt = code.indexOf("pendingFocusRef.current = { key: relationId, order: relationIds(relations) }");
    const awaitAt = code.indexOf("await onDeleteRelation(relationId)");
    expect(reserveAt, "删除流丢了交接预约").toBeGreaterThan(-1);
    expect(awaitAt, "onDeleteRelation 结构变了，账约失效需重订").toBeGreaterThan(reserveAt);
    // 行序快照必须与渲染序一致：出边在前、入边在后。
    expect(code).toMatch(
      /\[\.\.\.value\.outgoing\.map\(\(item\) => item\.id\), \.\.\.value\.incoming\.map\(\(item\) => item\.id\)\]/
    );
    expect(code, "换卡不作废预约——上一张卡的交接会咬新卡的焦点").toMatch(
      /setConfirmingRelationId\(null\);\s*pendingFocusRef\.current = null;/
    );
    expect(code).toMatch(/if \(current\.includes\(pending\.key\)\) return;/);
    expect(code, "删空退路被拆——ul 卸载后无处可退").toMatch(/listRef\.current \?\? emptyRef\.current/);
    expect(code).toMatch(/<ul className="cards-relation-list" ref=\{listRef\} tabIndex=\{-1\}/);
    expect(code).toMatch(/<p className="cards-relations-empty" ref=\{emptyRef\} tabIndex=\{-1\}>暂无关系。<\/p>/);
    const registrations = code.match(/rowActionRefs\.current\.set\(relation\.id, node\)/g) ?? [];
    expect(registrations.length, `出边/入边两处删除钮都要登记 ref，实为 ${registrations.length}`).toBe(2);
  });

  it("BD-200：建关系表单收起焦点回交开合钮，成功/取消两条收起路径都在账", () => {
    const code = read("../components/CardRelationsManager.tsx");
    expect(code, "BD-200 判据注释缺失").toContain("BD-200");
    expect(code).toMatch(/<button\s+type="button"\s+aria-expanded=\{showRelationForm\}\s+ref=\{formToggleRef\}/);
    // 成功与取消两处收起都以「setShowRelationForm(false) → … → focus(formToggleRef)」成对出现。
    const handoffs = code.match(/setShowRelationForm\(false\);[\s\S]{0,220}formToggleRef\.current\?\.focus\(\{ preventScroll: true \}\);/g) ?? [];
    expect(handoffs.length, `成功/取消两条收起路径都要回交焦点，实为 ${handoffs.length}`).toBe(2);
    // 失败不收起：回交只住在 ok 分支里（表单还开着，焦点原地不动才是对的）。
    expect(code).toMatch(/if \(ok\) \{\s*setShowRelationForm\(false\);/);
  });
});
