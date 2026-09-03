/**
 * 目录树派生与可见性行计算。
 *
 * 持久化的目录数据是"扁平数组 + level"（EpubTocItem / TXT / MD 皆然），树在这里
 * 按level 游标派生，不改存储 schema。层级跳级（level 比前一项大超过 1）时钳制到
 * 前一项 + 1，避免个别书籍的脏 level 把树打穿。
 */

export interface TocEntry {
  id: string;
  label: string;
  level: number;
}

export interface TocNode {
  entry: TocEntry;
  children: TocNode[];
}

export function buildTocTree(entries: TocEntry[]): TocNode[] {
  const roots: TocNode[] = [];
  const stack: TocNode[] = [];
  let prevLevel = 0;
  for (const raw of entries) {
    let level = Math.max(1, Math.floor(raw.level) || 1);
    if (stack.length > 0 && level > prevLevel + 1) level = prevLevel + 1;
    const node: TocNode = { entry: { ...raw, level }, children: [] };
    while (stack.length >= level) stack.pop();
    const parent = stack[stack.length - 1];
    if (parent) parent.children.push(node);
    else roots.push(node);
    stack.push(node);
    prevLevel = level;
  }
  return roots;
}

export function treeMaxDepth(nodes: TocNode[]): number {
  let max = 0;
  for (const node of nodes) {
    max = Math.max(max, node.children.length > 0 ? 1 + treeMaxDepth(node.children) : 1);
  }
  return max;
}

export interface VisibleTocRow {
  node: TocNode;
  depth: number;
  /** 有子节点且当前处于展开态 */
  expanded: boolean;
  hasChildren: boolean;
}

/** 按展开集合展开树：深度 1 的行恒可见，其余行要求全部祖先展开。 */
export function flattenVisibleTree(nodes: TocNode[], expanded: ReadonlySet<string>, depth = 1, out: VisibleTocRow[] = []): VisibleTocRow[] {
  for (const node of nodes) {
    const hasChildren = node.children.length > 0;
    const isOpen = expanded.has(node.entry.id);
    out.push({ node, depth, expanded: isOpen, hasChildren });
    if (hasChildren && isOpen) flattenVisibleTree(node.children, expanded, depth + 1, out);
  }
  return out;
}

/** 目标项的全部祖先 id（不含自身），用于"跳转到当前项时展开路径"。未找到返回空数组。 */
export function collectAncestorIds(nodes: TocNode[], id: string, trail: string[] = []): string[] {
  for (const node of nodes) {
    if (node.entry.id === id) return trail;
    const found = collectAncestorIds(node.children, id, [...trail, node.entry.id]);
    if (found.length > 0) return found;
  }
  return [];
}

/** 目录树中所有有子节点的条目 id（供"全部收起"）。 */
export function collectParentIds(nodes: TocNode[], out: string[] = []): string[] {
  for (const node of nodes) {
    if (node.children.length > 0) {
      out.push(node.entry.id);
      collectParentIds(node.children, out);
    }
  }
  return out;
}
