import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, ChevronRight, Check, Search, X } from "lucide-react";
import { Button } from "@/components/ui";
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
  /** 已读章节 id 集合（派生自当前进度），以勾选图标标记；不靠压暗文字表达，避免二级文字掉到对比度线以下 */
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
    return <div className="rounded-md border border-dashed border-paper-line bg-paper-panel/70 p-3 text-sm text-paper-muted">{emptyText}</div>;
  }

  const showSearch = !disableSearch && entries.length > 12;
  const searching = Boolean(query.trim());

  return (
    <div className={`flex min-h-0 flex-col gap-2 ${className ?? ""}`}>
      {showSearch && (
        <div className="relative">
          <Search size={13} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-paper-muted" />
          {/* 批次 BD-71（28px 桶·目录搜索右让位槽）：pr-7→pr-8（28→32）。
              28 是 24/32 正中平票，就近不裁决 ⇒ 判据是本框自己的清除钮：
              钮 `absolute right-1.5` + `p-1` + X size 12 ⇒ 宽 20px，右端贴输入框
              右缘内 6px 处。真宿主实拍（/tmp/bd71-tsx.json，目录栏输入「第」）：钮盒
              x1318–1338、输入框右缘 1344；pr 24 → 文字区右界 1320，**压进钮盒 2px**；
              pr 28 → 右界 1316，与钮左缘只剩 2px 缝；pr 32 → 右界 1312，与钮左缘留 6px，
              与钮自身的 6px 右缩进对称。即 32 让「文字停 → 6px 缝 → 钮 → 6px → 框边」
              成为左右等距的唯一一档。
              框盒 287 与 scrollWidth 285 在 24/28/32 三档全不动；占位文字实测 70px，
              32 档可用 287−32−32=223px，不截断。左槽 pl-8（32）本就在档，收 32 让
              左右内衬同值。 */}
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Escape") setQuery("");
            }}
            placeholder="搜索章节…"
            className="paper-input h-8 w-full pl-8 pr-8 text-sm"
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
          <Button variant="ghost" size="sm" onClick={() => setExpanded(new Set(collectParentIds(tree)))}>
            全部展开
          </Button>
          <Button variant="ghost" size="sm" onClick={() => setExpanded(new Set())}>
            全部收起
          </Button>
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
            <div className="rounded-md border border-dashed border-paper-line bg-paper-panel/70 p-3 text-sm text-paper-muted">没有匹配「{query.trim()}」的章节</div>
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
  // BD-124：行容器不再挂 role="button"/tabIndex/onKeyDown——折叠钮是 <button>，套在 role=button
  // 的行里就成了 nested-interactive（AT 报"按钮套按钮"、多一个 tab 停靠点）。跳章动作改由行内
  // 一个原生 <button> 承担（Enter/Space 由浏览器原生处理），折叠钮作为它的兄弟并补 aria-expanded。
  return (
    <div
      data-toc-row-id={entry.id}
      className="flex items-start rounded"
      style={{ paddingLeft: `${indent}px` }}
    >
      {hasChildren ? (
        <button
          type="button"
          className="mt-1 shrink-0 rounded p-0.5 text-paper-muted hover:text-paper-ink"
          aria-label={expanded ? "收起子目录" : "展开子目录"}
          aria-expanded={expanded}
          onClick={() => onToggle?.()}
        >
          {expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
        </button>
      ) : (
        <span className="w-[18px] shrink-0" />
      )}
      <button
        type="button"
        aria-current={active ? "true" : undefined}
        title={read ? `${entry.label}（已读）` : entry.label}
        onClick={() => onJump(entry.id)}
        className={`flex min-w-0 flex-1 items-start rounded text-left text-sm transition-colors ${
          active
            ? "bg-copper/10 font-medium text-copper"
            : // BD-119：读态曾用 text-paper-muted/55（≈2.2:1，二级文字不达 4.5），且读/未读同色后
              // 已无需分叉。已读改由行尾 Check 图标与 title 表达，文字统一为不透明 paper-muted
              // （浅色 4.96 / 深色 7.47），不再靠压暗区分状态。
              "text-paper-muted hover:bg-paper-panel hover:text-paper-ink"
        }`}
      >
        <span className="line-clamp-2 py-1.5 pr-2">{entry.label}</span>
        {read && !active && <Check size={12} className="ml-auto mr-2 mt-2 shrink-0 text-paper-muted" aria-hidden />}
      </button>
    </div>
  );
}
