import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, ChevronRight, Check, Search, X } from "lucide-react";
import { buildTocTree, flattenVisibleTree, collectAncestorIds, collectParentIds, treeMaxDepth, type TocEntry } from "./tree";

/**
 * 三格式共用的目录列表：层级折叠、搜索过滤、当前章高亮与自动跟随。
 *
 * 自动跟随策略：当前项变化时把该行滚进可视区（block: nearest，无动画），
 * 但用户正停留在目录上（hover / 键盘焦点）时挂起，离开后恢复——避免"用户在找
 * 目录项、列表自己滚走"的打架。跟随只滚目录列表，永不反向滚动正文。
 */

const COLLAPSE_WHEN_ABOVE = 200;

export interface TocListProps {
  entries: TocEntry[];
  currentId?: string;
  onJump(id: string): void;
  emptyText: string;
  /** 关闭搜索框（条目很少时由调用方决定） */
  disableSearch?: boolean;
  /** 附加到根节点的类（如在 flex 侧栏中传 min-h-0 flex-1 以获得内部滚动） */
  className?: string;
  /** 已读章节 id 集合（派生自当前进度），行内弱化并标记 */
  readIds?: ReadonlySet<string>;
}

export function TocList({ entries, currentId, onJump, emptyText, disableSearch, className, readIds }: TocListProps) {
  const listRef = useRef<HTMLDivElement>(null);
  const userBrowsingRef = useRef(false);
  const [query, setQuery] = useState("");
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set());
  const [defaultExpanded, setDefaultExpanded] = useState<boolean>(() => entries.length <= COLLAPSE_WHEN_ABOVE);

  const tree = useMemo(() => buildTocTree(entries), [entries]);
  const depth = useMemo(() => treeMaxDepth(tree), [tree]);
  const hasHierarchy = depth > 1;

  // 条目集变化（换书/重识别）时重置折叠策略与搜索
  useEffect(() => {
    setQuery("");
    setDefaultExpanded(entries.length <= COLLAPSE_WHEN_ABOVE);
    setExpanded(new Set());
  }, [entries]);

  // 当前项所在路径强制展开（不受默认折叠策略影响）
  useEffect(() => {
    if (!currentId || !hasHierarchy) return;
    const ancestors = collectAncestorIds(tree, currentId);
    if (ancestors.length === 0) return;
    setExpanded((prev) => {
      let changed = false;
      const next = new Set(prev);
      for (const id of ancestors) {
        if (!next.has(id)) {
          next.add(id);
          changed = true;
        }
      }
      return changed ? next : prev;
    });
  }, [currentId, hasHierarchy, tree]);

  const rows = useMemo(() => {
    if (query.trim()) return null;
    const effectiveExpanded = defaultExpanded
      ? expanded
      : new Set([...collectParentIds(tree), ...expanded]);
    return flattenVisibleTree(tree, effectiveExpanded);
  }, [tree, expanded, defaultExpanded, query]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return null;
    return entries.filter((entry) => entry.label.toLowerCase().includes(q));
  }, [entries, query]);

  // 当前项变化时跟随滚动（用户浏览目录时挂起）
  useEffect(() => {
    if (!currentId || userBrowsingRef.current) return;
    const row = listRef.current?.querySelector<HTMLElement>(`[data-toc-row-id="${CSS.escape(currentId)}"]`);
    row?.scrollIntoView({ block: "nearest" });
  }, [currentId, rows, filtered]);

  const toggle = (id: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  if (entries.length === 0) {
    return <div className="rounded-lg border border-dashed border-paper-line bg-paper-panel/70 p-3 text-sm text-paper-muted">{emptyText}</div>;
  }

  const showSearch = !disableSearch && entries.length > 12;
  const searching = Boolean(query.trim());

  return (
    <div className={`flex min-h-0 flex-col gap-2 ${className ?? ""}`}>
      {showSearch && (
        <div className="relative">
          <Search size={13} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-paper-muted" />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Escape") setQuery("");
            }}
            placeholder="搜索章节…"
            className="paper-input h-8 w-full pl-8 pr-7 text-sm"
            aria-label="搜索章节目录"
          />
          {query && (
            <button
              className="absolute right-1.5 top-1/2 -translate-y-1/2 rounded p-1 text-paper-muted hover:text-paper-ink"
              onClick={() => setQuery("")}
              title="清除搜索"
            >
              <X size={12} />
            </button>
          )}
        </div>
      )}

      {hasHierarchy && !searching && (
        <div className="flex items-center gap-2 text-xs text-paper-muted">
          <button className="rounded px-1.5 py-0.5 hover:bg-paper-panel hover:text-paper-ink" onClick={() => setExpanded(new Set(collectParentIds(tree)))}>
            全部展开
          </button>
          <button className="rounded px-1.5 py-0.5 hover:bg-paper-panel hover:text-paper-ink" onClick={() => setExpanded(new Set())}>
            全部收起
          </button>
        </div>
      )}

      <div
        ref={listRef}
        className="min-h-0 flex-1 overflow-y-auto pr-0.5"
        onMouseEnter={() => {
          userBrowsingRef.current = true;
        }}
        onMouseLeave={() => {
          userBrowsingRef.current = false;
        }}
        onFocus={() => {
          userBrowsingRef.current = true;
        }}
        onBlur={() => {
          userBrowsingRef.current = false;
        }}
      >
        {searching ? (
          filtered && filtered.length > 0 ? (
            <div className="grid gap-1">
              {filtered.map((entry) => (
                <TocRow
                  key={entry.id}
                  entry={entry}
                  active={entry.id === currentId}
                  read={readIds?.has(entry.id) ?? false}
                  indent={8 + Math.max(0, entry.level - 1) * 12}
                  onJump={onJump}
                />
              ))}
            </div>
          ) : (
            <div className="rounded-lg border border-dashed border-paper-line bg-paper-panel/70 p-3 text-sm text-paper-muted">没有匹配「{query.trim()}」的章节</div>
          )
        ) : (
          rows && (
            <div className="grid gap-1">
              {rows.map(({ node, expanded: isOpen, hasChildren: hasKids }) => (
                <TocRow
                  key={node.entry.id}
                  entry={node.entry}
                  active={node.entry.id === currentId}
                  read={readIds?.has(node.entry.id) ?? false}
                  indent={8 + Math.max(0, node.entry.level - 1) * 12}
                  onJump={onJump}
                  hasChildren={hasKids}
                  expanded={isOpen}
                  onToggle={() => toggle(node.entry.id)}
                />
              ))}
            </div>
          )
        )}
      </div>

      {searching && filtered && (
        <div className="text-xs text-paper-muted">共 {filtered.length} 个匹配</div>
      )}
    </div>
  );
}

interface TocRowProps {
  entry: TocEntry;
  active: boolean;
  read: boolean;
  indent: number;
  onJump(id: string): void;
  hasChildren?: boolean;
  expanded?: boolean;
  onToggle?: () => void;
}

function TocRow({ entry, active, read, indent, onJump, hasChildren, expanded, onToggle }: TocRowProps) {
  return (
    <div
      data-toc-row-id={entry.id}
      role="button"
      tabIndex={0}
      aria-current={active ? "true" : undefined}
      className={`group flex items-start rounded text-left text-sm transition-colors ${
        active
          ? "bg-copper/10 font-medium text-copper"
          : read
            ? "text-paper-muted/55 hover:bg-paper-panel hover:text-paper-ink"
            : "text-paper-muted hover:bg-paper-panel hover:text-paper-ink"
      }`}
      style={{ paddingLeft: `${indent}px` }}
      onClick={() => onJump(entry.id)}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onJump(entry.id);
        }
      }}
      title={read ? `${entry.label}（已读）` : entry.label}
    >
      {hasChildren ? (
        <button
          className="mt-1 shrink-0 rounded p-0.5 text-paper-muted hover:text-paper-ink"
          aria-label={expanded ? "收起子目录" : "展开子目录"}
          onClick={(event) => {
            event.stopPropagation();
            onToggle?.();
          }}
        >
          {expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
        </button>
      ) : (
        <span className="w-[18px] shrink-0" />
      )}
      <span className="line-clamp-2 py-1.5 pr-2">{entry.label}</span>
      {read && !active && <Check size={12} className="ml-auto mr-2 mt-2 shrink-0 text-paper-muted/50" aria-hidden />}
    </div>
  );
}
