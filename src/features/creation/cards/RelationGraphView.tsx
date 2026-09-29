import { useMemo, useState } from "react";
import { RotateCcw, Search, ZoomIn, ZoomOut } from "lucide-react";
import { Button } from "@/components/ui";
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
import type { RelationGraphView } from "@/types/creation";
import "./relation-graph.css";

/**
 * 卡片关系图（Stage 4-F）。
 *
 * 边界：关系是**全局卡片资产**。这里只做展示与跳转：
 * - 不新建、不删除、不修改任何关系（关系编辑在卡片详情里）；
 * - 项目视图只画「本项目已关联卡片」之间的连线，指向范围外的关系不画但显式报数；
 * - 点击节点只做「选中并跳转到该卡片」，不触发任何写入。
 */

export interface RelationGraphViewProps {
  graph: RelationGraphView | null;
  loading: boolean;
  error?: string | null;
  /** 卡片标题索引（cardId → 标题），用于关系清单里显示对端名称。 */
  titleOf?: (cardId: string) => string;
  onSelectCard: (cardId: string) => void;
  onRetry?: () => void;
}

const ZOOM_MIN = 0.6;
const ZOOM_MAX = 2;

export function RelationGraphView({
  graph,
  loading,
  error = null,
  titleOf,
  onSelectCard,
  onRetry
}: RelationGraphViewProps) {
  const [filters, setFilters] = useState<RelationGraphFilters>(EMPTY_RELATION_GRAPH_FILTERS);
  const [selectedCardId, setSelectedCardId] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);

  const filtered = useMemo(() => (graph ? filterRelationGraph(graph, filters) : null), [graph, filters]);
  const layout = useMemo(() => layoutRelationGraph(filtered?.nodes ?? []), [filtered]);
  const positionOf = useMemo(() => {
    const map = new Map<string, { x: number; y: number }>();
    for (const point of layout.points) map.set(point.cardId, { x: point.x, y: point.y });
    return map;
  }, [layout]);

  const selectedNode = useMemo(
    () => filtered?.nodes.find((node) => node.cardId === selectedCardId) ?? null,
    [filtered, selectedCardId]
  );
  const selectedEdges = useMemo(
    () => (selectedCardId && filtered ? edgesOfCard(filtered.edges, selectedCardId) : []),
    [filtered, selectedCardId]
  );

  const toggleIn = (key: "kinds" | "relationTypeIds", value: string): void => {
    setFilters((current) => {
      const next = new Set(current[key]);
      if (next.has(value)) next.delete(value);
      else next.add(value);
      return { ...current, [key]: next };
    });
  };

  const handleSelect = (cardId: string): void => {
    setSelectedCardId(cardId);
    onSelectCard(cardId);
  };

  const centerX = layout.minX + layout.width / 2;
  const centerY = layout.minY + layout.height / 2;

  if (loading && !graph) {
    return (
      <div className="relation-graph" data-testid="relation-graph">
        <p className="relation-graph-state" role="status">
          正在读取关系图…
        </p>
      </div>
    );
  }
  if (error) {
    return (
      <div className="relation-graph" data-testid="relation-graph">
        <p className="relation-graph-state relation-graph-state--error" role="alert">
          {error}
        </p>
        {onRetry && (
          <Button variant="outline" onClick={onRetry}>
            重试
          </Button>
        )}
      </div>
    );
  }
  if (!graph || !filtered) {
    return (
      <div className="relation-graph" data-testid="relation-graph">
        <p className="relation-graph-state">暂无关系图数据。</p>
      </div>
    );
  }

  return (
    <div className="relation-graph" data-testid="relation-graph">
      <div className="relation-graph-toolbar">
        <span className="relation-graph-search">
          <Search size={13} />
          <input
            value={filters.search}
            onChange={(event) => setFilters((current) => ({ ...current, search: event.target.value }))}
            placeholder="搜索卡片名或别名"
            aria-label="搜索卡片名或别名"
          />
        </span>
        <div className="relation-graph-chips" role="group" aria-label="按卡片类型过滤">
          {graph.kindFacets.map((facet) => (
            <button
              key={facet.kind}
              type="button"
              className="relation-graph-chip"
              aria-pressed={filters.kinds.has(facet.kind)}
              onClick={() => toggleIn("kinds", facet.kind)}
            >
              {facet.name} {facet.count}
            </button>
          ))}
        </div>
        <div className="relation-graph-chips" role="group" aria-label="按关系类型过滤">
          {graph.relationFacets.map((facet) => (
            <button
              key={facet.id}
              type="button"
              className="relation-graph-chip"
              aria-pressed={filters.relationTypeIds.has(facet.id)}
              onClick={() => toggleIn("relationTypeIds", facet.id)}
            >
              {facet.name} {facet.count}
            </button>
          ))}
        </div>
        <label className="relation-graph-chip">
          <input
            type="checkbox"
            checked={filters.hideIsolated}
            onChange={(event) => setFilters((current) => ({ ...current, hideIsolated: event.target.checked }))}
          />
          只看有关系的卡片
        </label>
        <button
          type="button"
          className="relation-graph-chip"
          onClick={() => {
            setFilters(EMPTY_RELATION_GRAPH_FILTERS);
            setSelectedCardId(null);
          }}
        >
          <RotateCcw size={11} /> 重置过滤
        </button>
        <span className="relation-graph-zoom">
          <button type="button" aria-label="缩小" onClick={() => setZoom((z) => Math.max(ZOOM_MIN, z - 0.2))}>
            <ZoomOut size={12} />
          </button>
          <button type="button" aria-label="放大" onClick={() => setZoom((z) => Math.min(ZOOM_MAX, z + 0.2))}>
            <ZoomIn size={12} />
          </button>
        </span>
      </div>

      <p className="relation-graph-scope" data-testid="relation-graph-scope">
        {describeRelationGraphScope(graph)}
        {filtered.nodes.length !== graph.nodes.length && `；当前过滤后 ${filtered.nodes.length} 张卡片 · ${filtered.edges.length} 条关系`}
      </p>

      <div className="relation-graph-body">
        <svg
          className="relation-graph-canvas"
          viewBox={`${layout.minX} ${layout.minY} ${layout.width} ${layout.height}`}
          role="img"
          aria-label="卡片关系图"
          data-testid="relation-graph-canvas"
        >
          <g transform={`translate(${centerX} ${centerY}) scale(${zoom}) translate(${-centerX} ${-centerY})`}>
            {filtered.edges.map((edge) => {
              const from = positionOf.get(edge.fromCardId);
              const to = positionOf.get(edge.toCardId);
              if (!from || !to) return null;
              const active = selectedCardId === edge.fromCardId || selectedCardId === edge.toCardId;
              return (
                <g key={edge.id}>
                  <line
                    className={`relation-graph-edge ${active ? "active" : ""}`}
                    x1={from.x}
                    y1={from.y}
                    x2={to.x}
                    y2={to.y}
                  />
                  {active && selectedCardId && (
                    <text
                      className="relation-graph-edge-label"
                      x={(from.x + to.x) / 2}
                      y={(from.y + to.y) / 2}
                    >
                      {relationLabel(edge, selectedCardId)}
                    </text>
                  )}
                </g>
              );
            })}
            {filtered.nodes.map((node) => {
              const point = positionOf.get(node.cardId);
              if (!point) return null;
              const selected = selectedCardId === node.cardId;
              return (
                <g
                  key={node.cardId}
                  className={`relation-graph-node ${selected ? "selected" : ""}`}
                  role="button"
                  tabIndex={0}
                  aria-label={`${node.title}（${node.kindName}）`}
                  aria-pressed={selected}
                  data-testid={`relation-graph-node-${node.cardId}`}
                  onClick={() => handleSelect(node.cardId)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      handleSelect(node.cardId);
                    }
                  }}
                >
                  <circle cx={point.x} cy={point.y} r={14 + Math.min(10, node.degree)} />
                  <text x={point.x} y={point.y + 34}>
                    {node.title}
                  </text>
                </g>
              );
            })}
          </g>
        </svg>

        <aside className="relation-graph-summary" aria-label="节点摘要" data-testid="relation-graph-summary">
          {selectedNode ? (
            <>
              <p className="desktop-card-label">{selectedNode.kindName}</p>
              <h4>{selectedNode.title}</h4>
              <p className="relation-graph-summary-meta">{relationNodeSummary(selectedNode)}</p>
              <p className="relation-graph-summary-meta">关联 {selectedNode.degree} 条</p>
              <ul className="relation-graph-summary-list">
                {selectedEdges.map((edge) => {
                  const otherId = relationOtherEnd(edge, selectedNode.cardId);
                  const otherTitle = titleOf?.(otherId) ?? otherId;
                  return (
                    <li key={edge.id}>
                      <button type="button" onClick={() => handleSelect(otherId)}>
                        {relationLabel(edge, selectedNode.cardId)} · {otherTitle}
                      </button>
                    </li>
                  );
                })}
              </ul>
            </>
          ) : (
            <p className="relation-graph-summary-meta">
              点击节点查看摘要并跳转到该卡片；图中只画卡片之间的关系，关系的编辑在卡片详情里。
            </p>
          )}
        </aside>
      </div>
    </div>
  );
}
