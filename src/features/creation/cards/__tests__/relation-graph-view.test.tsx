// @vitest-environment jsdom
import React from "react";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RelationGraphView } from "@/features/creation/cards/RelationGraphView";
import type { RelationGraphEdge, RelationGraphNode, RelationGraphView as GraphData } from "@/types/creation";

afterEach(() => cleanup());

function node(cardId: string, title: string, kind: string, kindName: string, aliases: string[] = []): RelationGraphNode {
  return { cardId, title, kind, kindName, aliases, summary: "", degree: 0 };
}

function edge(
  id: string,
  from: string,
  to: string,
  relationTypeId = "rt1",
  names: { relationName?: string; forwardName?: string; reverseName?: string } = {}
): RelationGraphEdge {
  return {
    id,
    fromCardId: from,
    toCardId: to,
    relationTypeId,
    relationName: names.relationName ?? "师徒",
    forwardName: names.forwardName ?? "师父",
    reverseName: names.reverseName ?? "徒弟",
    note: null
  };
}

function graph(overrides: Partial<GraphData> = {}): GraphData {
  const nodes = [node("a", "苏青", "character", "角色"), node("b", "顾淮", "character", "角色"), node("c", "旧书店", "location", "地点")];
  const edges = [
    edge("e1", "a", "b"),
    edge("e2", "a", "c", "rt2", { relationName: "常去", forwardName: "常去", reverseName: "常客" })
  ];
  const degree = new Map<string, number>();
  for (const item of edges) {
    degree.set(item.fromCardId, (degree.get(item.fromCardId) ?? 0) + 1);
    degree.set(item.toCardId, (degree.get(item.toCardId) ?? 0) + 1);
  }
  return {
    scope: "project",
    projectId: "p1",
    nodes: nodes.map((item) => ({ ...item, degree: degree.get(item.cardId) ?? 0 })),
    edges,
    kindFacets: [
      { kind: "character", name: "角色", count: 2 },
      { kind: "location", name: "地点", count: 1 }
    ],
    relationFacets: [
      { id: "rt1", name: "师徒", forwardName: "师父", reverseName: "徒弟", count: 1 },
      { id: "rt2", name: "常去", forwardName: "常去", reverseName: "常客", count: 1 }
    ],
    truncatedNodeCount: 0,
    hiddenRelationCount: 0,
    isolatedNodeCount: 0,
    ...overrides
  };
}

const titles: Record<string, string> = { a: "苏青", b: "顾淮", c: "旧书店" };

function renderGraph(overrides: Partial<GraphData> = {}, props: Partial<Parameters<typeof RelationGraphView>[0]> = {}) {
  const onSelectCard = vi.fn();
  render(
    <RelationGraphView
      graph={graph(overrides)}
      loading={false}
      titleOf={(cardId) => titles[cardId] ?? cardId}
      onSelectCard={onSelectCard}
      {...props}
    />
  );
  return { onSelectCard };
}

describe("RelationGraphView（Stage 4-F）", () => {
  it("渲染范围说明，并显式报出未绘制的关系条数", () => {
    renderGraph({ hiddenRelationCount: 3, truncatedNodeCount: 2 });
    const scope = screen.getByTestId("relation-graph-scope").textContent ?? "";
    expect(scope).toContain("当前项目的引用投影");
    expect(scope).toContain("3 条关系指向范围外卡片，未绘制");
    expect(scope).toContain("另有 2 张卡片因节点上限未显示");
  });

  it("每个节点与每条连线都渲染出来", () => {
    renderGraph();
    expect(screen.getByTestId("relation-graph-node-a")).toBeTruthy();
    expect(screen.getByTestId("relation-graph-node-b")).toBeTruthy();
    expect(screen.getByTestId("relation-graph-node-c")).toBeTruthy();
    const canvas = screen.getByTestId("relation-graph-canvas");
    expect(canvas.querySelectorAll("line.relation-graph-edge")).toHaveLength(2);
  });

  it("点击节点：显示摘要并跳转到该卡片", () => {
    const { onSelectCard } = renderGraph();
    fireEvent.click(screen.getByTestId("relation-graph-node-a"));

    expect(onSelectCard).toHaveBeenCalledWith("a");
    const summary = screen.getByTestId("relation-graph-summary").textContent ?? "";
    expect(summary).toContain("苏青");
    expect(summary).toContain("关联 2 条");
    expect(summary).toContain("师父 · 顾淮");
    expect(summary).toContain("常去 · 旧书店");
  });

  it("关系清单用方向读法：以选中节点为主语", () => {
    renderGraph();
    fireEvent.click(screen.getByTestId("relation-graph-node-b"));
    const summary = screen.getByTestId("relation-graph-summary").textContent ?? "";
    expect(summary).toContain("徒弟 · 苏青");
    expect(summary).not.toContain("师父 · 顾淮");
  });

  it("点击关系清单可跳到对端卡片", () => {
    const { onSelectCard } = renderGraph();
    fireEvent.click(screen.getByTestId("relation-graph-node-a"));
    fireEvent.click(screen.getByRole("button", { name: "师父 · 顾淮" }));
    expect(onSelectCard).toHaveBeenLastCalledWith("b");
    expect(screen.getByTestId("relation-graph-summary").textContent).toContain("顾淮");
  });

  it("键盘 Enter 也能选中节点", () => {
    const { onSelectCard } = renderGraph();
    fireEvent.keyDown(screen.getByTestId("relation-graph-node-c"), { key: "Enter" });
    expect(onSelectCard).toHaveBeenCalledWith("c");
  });

  it("按卡片类型过滤：类型标签是正向筛选，只保留被选中的类型", () => {
    renderGraph();
    fireEvent.click(screen.getByRole("button", { name: "地点 1" }));
    expect(screen.getByTestId("relation-graph-node-c")).toBeTruthy();
    expect(screen.queryByTestId("relation-graph-node-a")).toBeNull();
    // 再点一次取消该类型的筛选
    fireEvent.click(screen.getByRole("button", { name: "地点 1" }));
    expect(screen.getByTestId("relation-graph-node-a")).toBeTruthy();
  });

  it("按关系类型过滤：未选中的关系类型不绘制", () => {
    renderGraph();
    fireEvent.click(screen.getByRole("button", { name: "师徒 1" }));
    const canvas = screen.getByTestId("relation-graph-canvas");
    expect(canvas.querySelectorAll("line.relation-graph-edge")).toHaveLength(1);
  });

  it("搜索命中别名", () => {
    renderGraph({
      nodes: [{ ...node("a", "苏青", "character", "角色", ["小苏"]), degree: 1 }, { ...node("b", "顾淮", "character", "角色"), degree: 1 }],
      edges: [edge("e1", "a", "b")]
    });
    fireEvent.change(screen.getByLabelText("搜索卡片名或别名"), { target: { value: "小苏" } });
    expect(screen.getByTestId("relation-graph-node-a")).toBeTruthy();
    expect(screen.queryByTestId("relation-graph-node-b")).toBeNull();
  });

  it("只看有关系的卡片：孤立节点被隐藏", () => {
    renderGraph({
      nodes: [{ ...node("a", "苏青", "character", "角色"), degree: 1 }, { ...node("z", "未使用设定", "character", "角色"), degree: 0 }],
      edges: [edge("e1", "a", "a")]
    });
    expect(screen.getByTestId("relation-graph-node-z")).toBeTruthy();
    fireEvent.click(screen.getByLabelText("只看有关系的卡片"));
    expect(screen.queryByTestId("relation-graph-node-z")).toBeNull();
  });

  it("重置过滤恢复全图", () => {
    renderGraph();
    fireEvent.change(screen.getByLabelText("搜索卡片名或别名"), { target: { value: "苏青" } });
    expect(screen.queryByTestId("relation-graph-node-b")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: /重置过滤/ }));
    expect(screen.getByTestId("relation-graph-node-b")).toBeTruthy();
  });

  it("加载中 / 失败 / 无数据三态都要有明确呈现", () => {
    const { unmount } = render(
      <RelationGraphView graph={null} loading onSelectCard={vi.fn()} />
    );
    expect(screen.getByRole("status").textContent).toContain("正在读取关系图");
    unmount();

    const onRetry = vi.fn();
    render(<RelationGraphView graph={null} loading={false} error="关系图读取失败。" onSelectCard={vi.fn()} onRetry={onRetry} />);
    expect(screen.getByRole("alert").textContent).toContain("关系图读取失败。");
    fireEvent.click(screen.getByRole("button", { name: "重试" }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it("空图不报错而是给出说明", () => {
    renderGraph({ nodes: [], edges: [], kindFacets: [], relationFacets: [] });
    expect(screen.getByTestId("relation-graph-canvas")).toBeTruthy();
    expect(screen.getByTestId("relation-graph-summary").textContent).toContain("点击节点");
  });
});
