import { describe, expect, it } from "vitest";
import {
  EMPTY_RELATION_GRAPH_FILTERS,
  describeRelationGraphScope,
  edgesOfCard,
  filterRelationGraph,
  layoutRelationGraph,
  relationLabel,
  relationNodeSummary,
  relationOtherEnd,
  type RelationGraphFilters
} from "@/features/creation/cards/relation-graph";
import type {
  RelationGraphEdge,
  RelationGraphNode,
  RelationGraphView
} from "@/types/creation";

function node(cardId: string, kind: string, overrides: Partial<RelationGraphNode> = {}): RelationGraphNode {
  return { cardId, title: cardId, kind, kindName: kind, aliases: [], summary: "", degree: 0, ...overrides };
}

function edge(id: string, from: string, to: string, relationTypeId = "rt1"): RelationGraphEdge {
  return {
    id,
    fromCardId: from,
    toCardId: to,
    relationTypeId,
    relationName: "关系",
    forwardName: "正向",
    reverseName: "反向",
    note: null
  };
}

function view(nodes: RelationGraphNode[], edges: RelationGraphEdge[]): RelationGraphView {
  return {
    scope: "global",
    projectId: null,
    nodes,
    edges,
    kindFacets: [],
    relationFacets: [],
    truncatedNodeCount: 0,
    hiddenRelationCount: 0,
    isolatedNodeCount: nodes.filter((n) => n.degree === 0).length
  };
}

function filters(patch: Partial<RelationGraphFilters> = {}): RelationGraphFilters {
  return { ...EMPTY_RELATION_GRAPH_FILTERS, ...patch };
}

describe("filterRelationGraph（关系图过滤）", () => {
  const nodes = [
    node("a", "character", { title: "苏青", aliases: ["小苏"] }),
    node("b", "character", { title: "顾淮" }),
    node("c", "location", { title: "旧书店" }),
    node("d", "location", { title: "桥头" })
  ];
  const edges = [edge("e1", "a", "b"), edge("e2", "a", "c", "rt2")];
  const base = view(nodes, edges);

  it("按卡片类型过滤：只保留选中类型，且关系两端都在结果内", () => {
    const result = filterRelationGraph(base, filters({ kinds: new Set(["character"]) }));
    expect(result.nodes.map((n) => n.cardId)).toEqual(["a", "b"]);
    expect(result.edges.map((e) => e.id)).toEqual(["e1"]);
  });

  it("按关系类型过滤：被排除的类型不画", () => {
    const result = filterRelationGraph(base, filters({ relationTypeIds: new Set(["rt2"]) }));
    expect(result.edges.map((e) => e.id)).toEqual(["e2"]);
  });

  it("搜索命中标题或别名", () => {
    expect(filterRelationGraph(base, filters({ search: "苏青" })).nodes.map((n) => n.cardId)).toEqual(["a"]);
    expect(filterRelationGraph(base, filters({ search: "小苏" })).nodes.map((n) => n.cardId)).toEqual(["a"]);
    expect(filterRelationGraph(base, filters({ search: "不存在的名字" })).nodes).toEqual([]);
  });

  it("度数按过滤后的边重算，不是沿用整图度数", () => {
    const result = filterRelationGraph(base, filters({ relationTypeIds: new Set(["rt1"]) }));
    expect(result.nodes.find((n) => n.cardId === "a")?.degree).toBe(1);
    expect(result.nodes.find((n) => n.cardId === "c")?.degree).toBe(0);
  });

  it("隐藏孤立节点：只留参与关系的卡片", () => {
    const result = filterRelationGraph(base, filters({ hideIsolated: true }));
    expect(result.nodes.map((n) => n.cardId).sort()).toEqual(["a", "b", "c"]);
    expect(result.isolatedNodeCount).toBe(0);
  });

  it("分面不随过滤收缩，避免把可选项清空", () => {
    const withFacets: RelationGraphView = {
      ...base,
      kindFacets: [{ kind: "character", name: "角色", count: 2 }, { kind: "location", name: "地点", count: 2 }]
    };
    const result = filterRelationGraph(withFacets, filters({ kinds: new Set(["character"]) }));
    expect(result.kindFacets).toHaveLength(2);
  });
});

describe("关系方向读法", () => {
  const e = edge("e1", "a", "b");

  it("以 from 为主语用正向名，以 to 为主语用反向名", () => {
    expect(relationLabel(e, "a")).toBe("正向");
    expect(relationLabel(e, "b")).toBe("反向");
  });

  it("取另一端卡片", () => {
    expect(relationOtherEnd(e, "a")).toBe("b");
    expect(relationOtherEnd(e, "b")).toBe("a");
  });

  it("取出与某卡片相连的全部关系（含入边）", () => {
    const edges = [edge("e1", "a", "b"), edge("e2", "c", "a"), edge("e3", "b", "c")];
    expect(edgesOfCard(edges, "a").map((item) => item.id)).toEqual(["e1", "e2"]);
  });
});

describe("节点摘要", () => {
  it("类型 / 别名 / 字段摘要按顺序拼接，缺项自动跳过", () => {
    expect(relationNodeSummary(node("a", "character", { kindName: "角色" }))).toBe("角色");
    expect(relationNodeSummary(node("a", "character", { kindName: "角色", aliases: ["小苏"] }))).toBe("角色 · 别名：小苏");
    expect(
      relationNodeSummary(node("a", "character", { kindName: "角色", aliases: ["小苏"], summary: "性格: 克制" }))
    ).toBe("角色 · 别名：小苏 · 性格: 克制");
  });
});

describe("layoutRelationGraph（确定性布局）", () => {
  it("空图返回空布局", () => {
    expect(layoutRelationGraph([]).points).toEqual([]);
  });

  it("单节点居中", () => {
    const layout = layoutRelationGraph([node("a", "character")]);
    expect(layout.points).toEqual([{ cardId: "a", x: 0, y: 0 }]);
  });

  it("同样输入两次得到完全一致的结果（可复现、可截图比对）", () => {
    const nodes = [
      node("a", "character", { degree: 3 }),
      node("b", "character", { degree: 1 }),
      node("c", "location", { degree: 2 })
    ];
    expect(layoutRelationGraph(nodes)).toEqual(layoutRelationGraph(nodes));
  });

  it("每个节点都有坐标，且 viewBox 覆盖全部节点", () => {
    const nodes = [node("a", "character"), node("b", "location"), node("c", "character"), node("d", "location")];
    const layout = layoutRelationGraph(nodes);
    expect(layout.points).toHaveLength(4);
    for (const point of layout.points) {
      expect(point.x).toBeGreaterThanOrEqual(layout.minX);
      expect(point.x).toBeLessThanOrEqual(layout.minX + layout.width);
      expect(point.y).toBeGreaterThanOrEqual(layout.minY);
      expect(point.y).toBeLessThanOrEqual(layout.minY + layout.height);
    }
    expect(layout.width).toBeGreaterThan(0);
    expect(layout.height).toBeGreaterThan(0);
  });

  it("同类型聚成一簇：簇内点间距小于簇间距离", () => {
    const nodes = [
      node("a", "character"),
      node("b", "character"),
      node("c", "character"),
      node("d", "location"),
      node("e", "location"),
      node("f", "location")
    ];
    const layout = layoutRelationGraph(nodes);
    const at = (id: string) => layout.points.find((p) => p.cardId === id)!;
    const dist = (p: { x: number; y: number }, q: { x: number; y: number }) => Math.hypot(p.x - q.x, p.y - q.y);
    const withinCharacter = Math.max(dist(at("a"), at("b")), dist(at("b"), at("c")), dist(at("a"), at("c")));
    const betweenClusters = Math.min(dist(at("a"), at("d")), dist(at("b"), at("e")), dist(at("c"), at("f")));
    expect(withinCharacter).toBeLessThan(betweenClusters);
  });
});

describe("describeRelationGraphScope", () => {
  it("全局视角与被隐藏关系都显式说明", () => {
    expect(describeRelationGraphScope(view([node("a", "character")], []))).toContain("全局卡片库：1 张卡片 · 0 条关系");
    expect(
      describeRelationGraphScope({ ...view([node("a", "character")], []), hiddenRelationCount: 3, scope: "project" })
    ).toContain("3 条关系指向范围外卡片，未绘制");
    expect(describeRelationGraphScope({ ...view([], []), truncatedNodeCount: 7 })).toContain("另有 7 张卡片因节点上限未显示");
  });
});
