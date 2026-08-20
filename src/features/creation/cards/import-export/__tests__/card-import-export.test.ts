import { describe, expect, it } from "vitest";
import {
  buildDefaultMapping,
  mappingCanResolveType,
  mappingIsReady,
  summarizePlan
} from "../card-import-export-client";
import type {
  CardImportPlan,
  CardImportPreview,
  CardImportSchemaContext
} from "../card-import-export-types";

const schema: CardImportSchemaContext = {
  projectId: "p1",
  cardTypes: [
    {
      kind: "custom-abc",
      name: "人物",
      fields: [
        { key: "age", label: "年龄", kind: "number" },
        { key: "note", label: "备注", kind: "multiline" }
      ]
    }
  ],
  relationTypes: [],
  existingCards: []
};

describe("buildDefaultMapping (CSV)", () => {
  const preview: CardImportPreview = {
    format: "csv",
    rows: [
      { index: 0, fields: { 标题: "林晚", 类型: "人物", 别名: "晚妹", 标签: "主角", age: "18" }, notes: [] }
    ],
    headers: ["标题", "类型", "别名", "标签", "age"],
    warnings: []
  };

  it("按语义匹配标题/类型/别名/标签，并按字段名匹配自定义字段", () => {
    const mapping = buildDefaultMapping(preview, schema);
    expect(mapping.titleSource).toBe("标题");
    expect(mapping.typeSource).toBe("类型");
    expect(mapping.aliasSources).toContain("别名");
    expect(mapping.tagSources).toContain("标签");
    expect(mapping.fieldSources?.age).toBe("age");
  });

  it("无类型列时取首个表头作为标题", () => {
    const noTitle: CardImportPreview = {
      format: "csv",
      rows: [{ index: 0, fields: { 名称: "x" }, notes: [] }],
      headers: ["名称", "类型"],
      warnings: []
    };
    const mapping = buildDefaultMapping(noTitle, schema);
    expect(mapping.titleSource).toBe("名称");
  });
});

describe("buildDefaultMapping (Markdown)", () => {
  const preview: CardImportPreview = {
    format: "markdown",
    rows: [
      { index: 0, fields: { heading: "人物：林晚", 别名: "晚妹", age: "18" }, notes: [] }
    ],
    warnings: []
  };

  it("标题来自 heading，使用「：」拆分类型，字段键直接对应", () => {
    const mapping = buildDefaultMapping(preview, schema);
    expect(mapping.titleSource).toBe("heading");
    expect(mapping.headingTypeDelimiter).toBe("：");
    expect(mapping.aliasSources).toContain("别名");
    expect(mapping.fieldSources?.age).toBe("age");
  });
});

describe("summarizePlan", () => {
  it("把计划压缩为 UI 友好的计数与错误行", () => {
    const plan: CardImportPlan = {
      projectId: "p1",
      cards: [{ rowIndex: 0, kind: "k", title: "a", aliases: [], tags: [], fields: {}, cardRefFields: {} }],
      relations: [],
      skippedRowCount: 1,
      errorCount: 1,
      errors: [{ rowIndex: 2, messages: ["未知类型"] }],
      warnings: ["重复表头"]
    };
    const summary = summarizePlan(plan);
    expect(summary.cardCount).toBe(1);
    expect(summary.skipped).toBe(1);
    expect(summary.errorCount).toBe(1);
    expect(summary.errorLines[0]).toContain("第 3 行");
    expect(summary.warningCount).toBe(1);
  });
});

describe("mapping 校验助手", () => {
  it("有 typeSource 即可解析类型", () => {
    expect(mappingCanResolveType({ format: "csv", titleSource: "标题", typeSource: "类型" })).toBe(true);
  });
  it("无 typeSource 但有 typeFallback 也可解析类型", () => {
    expect(mappingCanResolveType({ format: "csv", titleSource: "标题", typeFallback: "custom-abc" })).toBe(true);
  });
  it("两者皆无则无法解析类型", () => {
    expect(mappingCanResolveType({ format: "csv", titleSource: "标题" })).toBe(false);
  });
  it("标题来源为空则映射未就绪", () => {
    expect(mappingIsReady({ format: "csv", titleSource: "" })).toBe(false);
    expect(mappingIsReady({ format: "csv", titleSource: "标题" })).toBe(true);
  });
});
