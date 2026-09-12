import type { RelationGraphEdge, RelationGraphNode, RelationGraphView } from "@/types/creation";

/**
 * 关系图的纯计算层（Stage 4-F）：过滤 + 确定性布局 + 文案。
 * 无 React、无 IPC、无随机数——同样的输入永远得到同样的坐标，便于测试与截图比对。
 */

export interface RelationGraphFilters {
  /** 卡片类型白名单；空集表示不限。 */
  kinds: ReadonlySet<string>;
  /** 关系类型白名单；空集表示不限。 */
  relationTypeIds: ReadonlySet<string>;
  /** 标题/别名子串搜索（大小写不敏感）。 */
  search: string;
  /** 隐藏没有任何关系的卡片（孤立节点）。 */
  hideIsolated: boolean;
}

export const EMPTY_RELATION_GRAPH_FILTERS: RelationGraphFilters = {
  kinds: new Set(),
  relationTypeIds: new Set(),
  search: "",
  hideIsolated: false
};

export interface RelationGraphPoint {
  cardId: string;
  x: number;
  y: number;
}

export interface RelationGraphLayout {
  points: RelationGraphPoint[];
  /** 建议 viewBox（已含边距）。 */
  minX: number;
  minY: number;
  width: number;
  height: number;
}

/** 节点是否命中搜索（标题或别名子串，大小写不敏感）。 */
export function relationNodeMatches(node: RelationGraphNode, search: string): boolean {
  const keyword = search.trim().toLowerCase();
  if (keyword === "") return true;
  if (node.title.toLowerCase().includes(keyword)) return true;
  return node.aliases.some((alias) => alias.toLowerCase().includes(keyword));
}

/**
 * 按条件裁剪关系图。
 * 注意：分面（kindFacets / relationFacets）**不随过滤收缩**——它们描述整图的可用筛选项，
 * 若跟着结果收缩，用户一旦勾选就把其它选项清空，等于把自己锁死在当前选择里。
 */
export function filterRelationGraph(view: RelationGraphView, filters: RelationGraphFilters): RelationGraphView {
  const keyword = filters.search.trim().toLowerCase();
  const candidates = view.nodes.filter(
    (node) =>
      (filters.kinds.size === 0 || filters.kinds.has(node.kind)) && relationNodeMatches(node, keyword)
  );
  const allowed = new Set(candidates.map((node) => node.cardId));

  const edges = view.edges.filter(
    (edge) =>
      allowed.has(edge.fromCardId) &&
      allowed.has(edge.toCardId) &&
      (filters.relationTypeIds.size === 0 || filters.relationTypeIds.has(edge.relationTypeId))
  );

  const degree = new Map<string, number>();
  for (const edge of edges) {
    degree.set(edge.fromCardId, (degree.get(edge.fromCardId) ?? 0) + 1);
    degree.set(edge.toCardId, (degree.get(edge.toCardId) ?? 0) + 1);
  }

  const nodes = candidates
    .map((node) => ({ ...node, degree: degree.get(node.cardId) ?? 0 }))
    .filter((node) => (filters.hideIsolated ? node.degree > 0 : true));

  return {
    ...view,
    nodes,
    edges,
    isolatedNodeCount: nodes.filter((node) => node.degree === 0).length
  };
}

/** 关系的方向读法：以 `viewpointCardId` 为主语，返回正向或反向名。 */
export function relationLabel(edge: RelationGraphEdge, viewpointCardId: string): string {
  return edge.fromCardId === viewpointCardId ? edge.forwardName : edge.reverseName;
}

/** 关系的另一端卡片 ID；自环（罕见但数据可能脏）返回自身。 */
export function relationOtherEnd(edge: RelationGraphEdge, viewpointCardId: string): string {
  return edge.fromCardId === viewpointCardId ? edge.toCardId : edge.fromCardId;
}

/** 与某张卡片直接相连的全部关系（出边与入边）。 */
export function edgesOfCard(edges: RelationGraphEdge[], cardId: string): RelationGraphEdge[] {
  return edges.filter((edge) => edge.fromCardId === cardId || edge.toCardId === cardId);
}

/** 节点摘要一行文案：`类型 · 别名 · 字段摘要`。 */
export function relationNodeSummary(node: RelationGraphNode): string {
  const parts: string[] = [node.kindName];
  if (node.aliases.length > 0) parts.push(`别名：${node.aliases.join("、")}`);
  if (node.summary) parts.push(node.summary);
  return parts.join(" · ");
}

const TWO_PI = Math.PI * 2;

/** 稳定排序：度数降序 → 标题 → id，保证布局可复现。 */
function stableSort(nodes: RelationGraphNode[]): RelationGraphNode[] {
  return [...nodes].sort((a, b) => {
    if (a.degree !== b.degree) return b.degree - a.degree;
    if (a.title !== b.title) return a.title < b.title ? -1 : 1;
    return a.cardId < b.cardId ? -1 : 1;
  });
}

/**
 * 确定性布局：按卡片类型分簇，簇中心落在同一个环上，簇内节点再各占一个小环。
 * 不用力导向的原因：力导向对初始条件敏感、每次结果不同，既不好测也不好截图比对；
 * 分簇环布局对「几十张卡片」这一真实量级足够清晰，而且同类型天然聚在一起。
 */
export function layoutRelationGraph(nodes: RelationGraphNode[]): RelationGraphLayout {
  if (nodes.length === 0) {
    return { points: [], minX: 0, minY: 0, width: 0, height: 0 };
  }
  const ordered = stableSort(nodes);
  const groups = new Map<string, RelationGraphNode[]>();
  for (const node of ordered) {
    const bucket = groups.get(node.kind);
    if (bucket) bucket.push(node);
    else groups.set(node.kind, [node]);
  }
  const groupEntries = [...groups.entries()].sort((a, b) => {
    if (a[1].length !== b[1].length) return b[1].length - a[1].length;
    return a[0] < b[0] ? -1 : 1;
  });

  const points: RelationGraphPoint[] = [];
  if (groupEntries.length === 1) {
    const members = groupEntries[0]![1];
    if (members.length === 1) {
      points.push({ cardId: members[0]!.cardId, x: 0, y: 0 });
    } else {
      const radius = Math.min(360, 70 + 26 * members.length);
      members.forEach((node, index) => {
        const angle = (index / members.length) * TWO_PI - Math.PI / 2;
        points.push({ cardId: node.cardId, x: Math.cos(angle) * radius, y: Math.sin(angle) * radius });
      });
    }
  } else {
    const groupRadius = Math.min(340, 150 + 30 * groupEntries.length);
    groupEntries.forEach(([, members], groupIndex) => {
      const angle = (groupIndex / groupEntries.length) * TWO_PI - Math.PI / 2;
      const centerX = Math.cos(angle) * groupRadius;
      const centerY = Math.sin(angle) * groupRadius;
      const clusterRadius = Math.min(150, 40 + 20 * Math.sqrt(members.length));
      if (members.length === 1) {
        points.push({ cardId: members[0]!.cardId, x: centerX, y: centerY });
        return;
      }
      members.forEach((node, index) => {
        const inner = (index / members.length) * TWO_PI - Math.PI / 2;
        points.push({
          cardId: node.cardId,
          x: centerX + Math.cos(inner) * clusterRadius,
          y: centerY + Math.sin(inner) * clusterRadius
        });
      });
    });
  }

  const xs = points.map((point) => point.x);
  const ys = points.map((point) => point.y);
  const padding = 90;
  const minX = Math.min(...xs) - padding;
  const minY = Math.min(...ys) - padding;
  const maxX = Math.max(...xs) + padding;
  const maxY = Math.max(...ys) + padding;
  return { points, minX, minY, width: maxX - minX, height: maxY - minY };
}

/** 图例/说明文案：把「范围 + 被隐藏的关系数」说清楚，避免用户误以为关系丢失。 */
export function describeRelationGraphScope(view: RelationGraphView): string {
  const scope = view.scope === "project" ? "当前项目的引用投影" : "全局卡片库";
  const parts = [`${scope}：${view.nodes.length} 张卡片 · ${view.edges.length} 条关系`];
  if (view.hiddenRelationCount > 0) {
    parts.push(`${view.hiddenRelationCount} 条关系指向范围外卡片，未绘制`);
  }
  if (view.truncatedNodeCount > 0) {
    parts.push(`另有 ${view.truncatedNodeCount} 张卡片因节点上限未显示`);
  }
  return parts.join("；");
}
