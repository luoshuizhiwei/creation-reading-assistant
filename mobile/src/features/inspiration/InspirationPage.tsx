import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowDownAZ,
  ArrowLeft,
  BookOpen,
  Check,
  ChevronDown,
  Copy,
  MoreHorizontal,
  Plus,
  Search,
  SlidersHorizontal,
  Sparkles,
  Trash2,
  X
} from "lucide-react";
import type { InspirationItem, InspirationStatus, InspirationType } from "../../../../src/types/inspiration";
import {
  addMobileInspiration,
  deleteMobileInspiration,
  updateMobileInspiration,
  type MobileSnapshot
} from "../../services/mobile-storage";
import type { MobileBook } from "../../types/mobile";
import { InspirationDetailPanel } from "./InspirationDetailPanel";
import { getInspirationTypeLabel, parseTagInput } from "./inspiration-helpers";

type SortMode = "updated" | "created" | "title" | "source";
type PageMode = "list" | "detail" | "editor";

const typeOptions: Array<{ value: InspirationType; label: string }> = [
  { value: "note", label: "灵感" },
  { value: "plot", label: "剧情" },
  { value: "character", label: "人物" },
  { value: "world", label: "世界观" },
  { value: "scene", label: "场景" },
  { value: "line", label: "对话" },
  { value: "trope", label: "设定" },
  { value: "conflict", label: "冲突" }
];

const statusOptions: Array<{ value: InspirationStatus; label: string }> = [
  { value: "inbox", label: "未整理" },
  { value: "reviewing", label: "待整理" },
  { value: "usable", label: "可使用" },
  { value: "polished", label: "已打磨" },
  { value: "used", label: "已采用" },
  { value: "archived", label: "已归档" }
];

const sortOptions: Array<{ value: SortMode; label: string }> = [
  { value: "updated", label: "最近更新" },
  { value: "created", label: "创建时间" },
  { value: "title", label: "标题" },
  { value: "source", label: "来源书籍" }
];

function formatListTime(value: string | undefined) {
  if (!value) return "时间未知";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "时间未知";
  const now = new Date();
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false });
  }
  return date.toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" });
}

function normalizeSearchText(value: unknown) {
  return String(value ?? "").trim().toLocaleLowerCase("zh-CN");
}

function isBookReadable(book: MobileBook) {
  if (book.contentStatus === "missing" || book.contentStatus === "failed" || book.origin === "sync_placeholder") return false;
  return Boolean(book.localUri || book.localFilePath || book.localContentPath || book.contentStatus === "available" || book.origin === "local_import" || book.origin === "sync_downloaded");
}

export function InspirationPage({
  snapshot,
  onSnapshotChange,
  onConfirm,
  onMessage,
  onOpenBook,
  initialSelectedId
}: {
  snapshot: MobileSnapshot;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onConfirm: (dialog: { title: string; message: string; onConfirm: () => void } | null) => void;
  onMessage: (message: string) => void;
  onOpenBook: (book: MobileBook) => void;
  initialSelectedId?: string;
}) {
  const [mode, setMode] = useState<PageMode>(initialSelectedId ? "detail" : "list");
  const [selectedId, setSelectedId] = useState<string | null>(initialSelectedId ?? null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [typeFilter, setTypeFilter] = useState<InspirationType | "all">("all");
  const [sortMode, setSortMode] = useState<SortMode>(() => {
    const stored = localStorage.getItem("mobile-inspiration-sort");
    return sortOptions.some((option) => option.value === stored) ? stored as SortMode : "updated";
  });
  const [sortOpen, setSortOpen] = useState(false);
  const [actionItemId, setActionItemId] = useState<string | null>(null);
  const [variantsExpanded, setVariantsExpanded] = useState(false);
  const [editorDirty, setEditorDirty] = useState(false);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const listScrollRef = useRef<HTMLDivElement>(null);
  const listScrollTopRef = useRef(0);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedQuery(query.trim()), 180);
    return () => window.clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    if (searchOpen) window.setTimeout(() => searchInputRef.current?.focus(), 40);
  }, [searchOpen]);

  useEffect(() => {
    localStorage.setItem("mobile-inspiration-sort", sortMode);
  }, [sortMode]);

  useEffect(() => {
    if (!initialSelectedId) return;
    if (snapshot.inspirations.some((item) => item.id === initialSelectedId)) {
      setSelectedId(initialSelectedId);
      setMode("detail");
    }
  }, [initialSelectedId, snapshot.inspirations]);

  useEffect(() => {
    if (mode !== "list") return;
    window.requestAnimationFrame(() => {
      if (listScrollRef.current) listScrollRef.current.scrollTop = listScrollTopRef.current;
    });
  }, [mode]);

  const selectedItem = useMemo(
    () => selectedId ? snapshot.inspirations.find((item) => item.id === selectedId) : undefined,
    [selectedId, snapshot.inspirations]
  );

  const availableTypes = useMemo(() => {
    const counts = new Map<InspirationType, number>();
    for (const item of snapshot.inspirations) counts.set(item.type ?? "note", (counts.get(item.type ?? "note") ?? 0) + 1);
    return typeOptions.filter((option) => counts.has(option.value)).map((option) => ({ ...option, count: counts.get(option.value) ?? 0 }));
  }, [snapshot.inspirations]);

  const filteredItems = useMemo(() => {
    const needle = normalizeSearchText(debouncedQuery);
    return snapshot.inspirations
      .map((item, index) => ({ item, index }))
      .filter(({ item }) => {
        if (typeFilter !== "all" && (item.type ?? "note") !== typeFilter) return false;
        if (!needle) return true;
        const searchText = normalizeSearchText([
          item.title,
          item.body,
          ...(item.tags ?? []),
          item.source?.bookTitle,
          item.source?.chapterTitle,
          item.source?.locationLabel,
          item.source?.excerpt
        ].filter(Boolean).join(" "));
        return searchText.includes(needle);
      })
      .sort((left, right) => {
        let result = 0;
        if (sortMode === "updated" || sortMode === "created") {
          const key = sortMode === "updated" ? "updatedAt" : "createdAt";
          const a = Date.parse(left.item[key] ?? "") || 0;
          const b = Date.parse(right.item[key] ?? "") || 0;
          result = b - a;
        } else {
          const a = normalizeSearchText(sortMode === "title" ? left.item.title : left.item.source?.bookTitle);
          const b = normalizeSearchText(sortMode === "title" ? right.item.title : right.item.source?.bookTitle);
          if (!a && b) result = 1;
          else if (a && !b) result = -1;
          else result = a.localeCompare(b, "zh-CN");
        }
        return result || left.index - right.index;
      })
      .map(({ item }) => item);
  }, [snapshot.inspirations, debouncedQuery, typeFilter, sortMode]);

  const rememberListPosition = () => {
    listScrollTopRef.current = listScrollRef.current?.scrollTop ?? listScrollTopRef.current;
  };

  const openDetail = useCallback((id: string) => {
    rememberListPosition();
    setSelectedId(id);
    setVariantsExpanded(false);
    setActionItemId(null);
    setMode("detail");
  }, []);

  const closeEditor = useCallback(() => {
    setEditorDirty(false);
    if (editingId) {
      setMode("detail");
    } else {
      setMode("list");
      setSelectedId(null);
    }
    setEditingId(null);
  }, [editingId]);

  const requestEditorBack = useCallback(() => {
    const active = document.activeElement;
    if (active instanceof HTMLInputElement || active instanceof HTMLTextAreaElement || active instanceof HTMLSelectElement) {
      active.blur();
      return;
    }
    if (!editorDirty) {
      closeEditor();
      return;
    }
    onConfirm({
      title: "放弃未保存修改？",
      message: "返回后，本次输入的内容不会保存。",
      onConfirm: () => {
        onConfirm(null);
        closeEditor();
      }
    });
  }, [closeEditor, editorDirty, onConfirm]);

  useEffect(() => {
    const handleTabBack = (event: Event) => {
      if (actionItemId) {
        event.preventDefault();
        setActionItemId(null);
        return;
      }
      if (sortOpen) {
        event.preventDefault();
        setSortOpen(false);
        return;
      }
      if (mode === "editor") {
        event.preventDefault();
        requestEditorBack();
        return;
      }
      if (mode === "detail") {
        event.preventDefault();
        if (variantsExpanded) {
          setVariantsExpanded(false);
          return;
        }
        setMode("list");
        setSelectedId(null);
        return;
      }
      if (searchOpen) {
        event.preventDefault();
        setSearchOpen(false);
        setQuery("");
      }
    };
    window.addEventListener("mobile-tab-back", handleTabBack);
    return () => window.removeEventListener("mobile-tab-back", handleTabBack);
  }, [actionItemId, mode, requestEditorBack, searchOpen, sortOpen, variantsExpanded]);

  const openSource = (item: InspirationItem) => {
    const book = item.source?.bookId ? snapshot.books.find((entry) => entry.id === item.source?.bookId) : undefined;
    if (!book) {
      onMessage("来源书籍已不可用，这条灵感仍会保留。");
      return;
    }
    if (!isBookReadable(book)) {
      onMessage("这本书在当前设备没有可读正文，请先到书架下载或重新导入。");
      return;
    }
    onOpenBook(book);
  };

  const copyItem = async (item: InspirationItem) => {
    const text = [item.title, item.body, item.source?.excerpt].filter(Boolean).join("\n\n");
    try {
      await navigator.clipboard.writeText(text);
      onMessage("灵感内容已复制。");
    } catch {
      onMessage("复制失败，请在详情页长按文字复制。");
    }
    setActionItemId(null);
  };

  const requestDelete = (item: InspirationItem) => {
    setActionItemId(null);
    onConfirm({
      title: "删除这条灵感？",
      message: "只会删除当前灵感，不会删除来源书籍、笔记或正文。",
      onConfirm: async () => {
        try {
          const next = await deleteMobileInspiration(snapshot, item.id);
          onSnapshotChange(next);
          onConfirm(null);
          if (selectedId === item.id) {
            setSelectedId(null);
            setMode("list");
          }
          onMessage("灵感已删除。");
        } catch {
          onConfirm(null);
          onMessage("删除失败，灵感仍然保留，请稍后重试。");
        }
      }
    });
  };

  if (mode === "editor") {
    const item = editingId ? snapshot.inspirations.find((entry) => entry.id === editingId) : undefined;
    return (
      <InspirationEditorView
        item={item}
        books={snapshot.books}
        snapshot={snapshot}
        onSnapshotChange={onSnapshotChange}
        onDirtyChange={setEditorDirty}
        onCancel={requestEditorBack}
        onSaved={(nextItemId) => {
          setEditingId(null);
          setSelectedId(nextItemId);
          setEditorDirty(false);
          setMode("detail");
          onMessage(item ? "修改已保存。" : "灵感已保存。");
        }}
        onMessage={onMessage}
      />
    );
  }

  if (mode === "detail" && selectedItem) {
    return (
      <InspirationDetailPanel
        item={selectedItem}
        snapshot={snapshot}
        variantsExpanded={variantsExpanded}
        onVariantsExpandedChange={setVariantsExpanded}
        onSnapshotChange={onSnapshotChange}
        onMessage={onMessage}
        onBack={() => {
          setSelectedId(null);
          setMode("list");
        }}
        onEdit={() => {
          setEditingId(selectedItem.id);
          setEditorDirty(false);
          setMode("editor");
        }}
        onMore={() => setActionItemId(selectedItem.id)}
        onOpenSource={() => openSource(selectedItem)}
      />
    );
  }

  const actionItem = actionItemId ? snapshot.inspirations.find((item) => item.id === actionItemId) : undefined;
  const hasAny = snapshot.inspirations.length > 0;
  const hasSearch = Boolean(debouncedQuery);
  const hasFilter = typeFilter !== "all";

  return (
    <div className="screen-stack inspiration-page inspiration-page-v2">
      <header className="inspiration-topbar">
        {searchOpen ? (
          <div className="inspiration-search-mode">
            <Search size={20} aria-hidden="true" />
            <input
              ref={searchInputRef}
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="搜索标题、正文、标签或来源"
              aria-label="搜索灵感"
            />
            {query && <button onClick={() => setQuery("")} aria-label="清除搜索"><X size={20} /></button>}
            <button onClick={() => { setSearchOpen(false); setQuery(""); }} aria-label="退出搜索">取消</button>
          </div>
        ) : (
          <>
            <h1>灵感</h1>
            <div className="inspiration-topbar-actions">
              <button onClick={() => setSearchOpen(true)} aria-label="搜索灵感"><Search size={22} /></button>
              <button onClick={() => { setEditingId(null); setEditorDirty(false); setMode("editor"); }} aria-label="新建灵感"><Plus size={23} /></button>
            </div>
          </>
        )}
      </header>

      <div className="inspiration-list-scroll" ref={listScrollRef} onScroll={rememberListPosition}>
        <div className="inspiration-filter-row">
          <div className="inspiration-type-rail" aria-label="按类型筛选">
            <button className={typeFilter === "all" ? "active" : ""} onClick={() => setTypeFilter("all")}>全部</button>
            {availableTypes.map((option) => (
              <button key={option.value} className={typeFilter === option.value ? "active" : ""} onClick={() => setTypeFilter(option.value)}>
                {option.label}<span>{option.count}</span>
              </button>
            ))}
          </div>
          <button className="inspiration-sort-button" onClick={() => setSortOpen(true)} aria-label="灵感排序">
            <SlidersHorizontal size={18} />
            <span>{sortOptions.find((option) => option.value === sortMode)?.label}</span>
            <ChevronDown size={15} />
          </button>
        </div>

        {filteredItems.length ? (
          <section className="inspiration-record-list" aria-label="灵感列表">
            {filteredItems.map((item) => (
              <article key={item.id} className="inspiration-record" onClick={() => openDetail(item.id)}>
                <div className="inspiration-record-head">
                  <span className="inspiration-type-tag">{getInspirationTypeLabel(item.type)}</span>
                  <time dateTime={item.updatedAt}>{formatListTime(item.updatedAt)}</time>
                  <button
                    className="inspiration-card-more"
                    onClick={(event) => { event.stopPropagation(); setActionItemId(item.id); }}
                    aria-label={`更多操作：${item.title || "未命名灵感"}`}
                  >
                    <MoreHorizontal size={20} />
                  </button>
                </div>
                <h2>{item.title?.trim() || "未命名灵感"}</h2>
                <p className="inspiration-record-summary">{item.body?.trim() || item.source?.excerpt?.trim() || "还没有正文"}</p>
                {(item.source?.bookTitle || item.source?.locationLabel) && (
                  <p className="inspiration-record-source">
                    <BookOpen size={14} />
                    <span>来源：{item.source?.bookTitle ? `《${item.source.bookTitle}》` : "阅读记录"}{item.source?.locationLabel ? ` · ${item.source.locationLabel}` : ""}</span>
                  </p>
                )}
                {item.tags?.length > 0 && (
                  <div className="inspiration-record-tags">{item.tags.slice(0, 3).map((tag) => <span key={tag}>#{tag}</span>)}</div>
                )}
              </article>
            ))}
          </section>
        ) : (
          <InspirationEmptyState
            kind={!hasAny ? "empty" : hasSearch ? "search" : hasFilter ? "filter" : "empty"}
            onCreate={() => { setEditingId(null); setEditorDirty(false); setMode("editor"); }}
            onReset={() => { setQuery(""); setTypeFilter("all"); }}
          />
        )}
      </div>

      {sortOpen && (
        <div className="inspiration-overlay" role="dialog" aria-modal="true" aria-label="排序方式">
          <button className="inspiration-overlay-mask" onClick={() => setSortOpen(false)} aria-label="关闭排序" />
          <section className="inspiration-action-sheet">
            <header><strong>排序方式</strong><button onClick={() => setSortOpen(false)} aria-label="关闭"><X size={20} /></button></header>
            {sortOptions.map((option) => (
              <button key={option.value} onClick={() => { setSortMode(option.value); setSortOpen(false); }}>
                {option.value === "title" ? <ArrowDownAZ size={19} /> : <SlidersHorizontal size={19} />}
                <span>{option.label}</span>
                {sortMode === option.value && <Check size={19} />}
              </button>
            ))}
          </section>
        </div>
      )}

      {actionItem && (
        <div className="inspiration-overlay" role="dialog" aria-modal="true" aria-label="灵感操作">
          <button className="inspiration-overlay-mask" onClick={() => setActionItemId(null)} aria-label="关闭操作菜单" />
          <section className="inspiration-action-sheet">
            <header><strong>{actionItem.title || "未命名灵感"}</strong><button onClick={() => setActionItemId(null)} aria-label="关闭"><X size={20} /></button></header>
            <button onClick={() => openDetail(actionItem.id)}><Sparkles size={19} /><span>查看详情</span></button>
            <button onClick={() => { setSelectedId(actionItem.id); setEditingId(actionItem.id); setActionItemId(null); setEditorDirty(false); setMode("editor"); }}><MoreHorizontal size={19} /><span>编辑</span></button>
            <button onClick={() => void copyItem(actionItem)}><Copy size={19} /><span>复制内容</span></button>
            {actionItem.source?.bookId && <button onClick={() => { setActionItemId(null); openSource(actionItem); }}><BookOpen size={19} /><span>查看来源书籍</span></button>}
            <button className="danger" onClick={() => requestDelete(actionItem)}><Trash2 size={19} /><span>删除灵感</span></button>
          </section>
        </div>
      )}
    </div>
  );
}

function InspirationEmptyState({ kind, onCreate, onReset }: { kind: "empty" | "search" | "filter"; onCreate: () => void; onReset: () => void }) {
  const content = kind === "empty"
    ? { title: "还没有灵感", body: "记录设定、摘录或创作片段。" }
    : kind === "search"
      ? { title: "没有搜索结果", body: "试试更短的关键词，或清除搜索。" }
      : { title: "这个类型还没有内容", body: "切换到全部，或新建一条灵感。" };
  return (
    <section className="inspiration-empty-v2">
      <Sparkles size={27} />
      <strong>{content.title}</strong>
      <p>{content.body}</p>
      {kind === "empty" ? <button onClick={onCreate}><Plus size={18} />新建灵感</button> : <button onClick={onReset}>查看全部</button>}
    </section>
  );
}

function InspirationEditorView({
  item,
  books,
  snapshot,
  onSnapshotChange,
  onDirtyChange,
  onCancel,
  onSaved,
  onMessage
}: {
  item?: InspirationItem;
  books: MobileBook[];
  snapshot: MobileSnapshot;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onDirtyChange: (dirty: boolean) => void;
  onCancel: () => void;
  onSaved: (id: string) => void;
  onMessage: (message: string) => void;
}) {
  const [title, setTitle] = useState(item?.title ?? "");
  const [body, setBody] = useState(item?.body ?? "");
  const [type, setType] = useState<InspirationType>(item?.type ?? "note");
  const [status, setStatus] = useState<InspirationStatus>(item?.status ?? "inbox");
  const [tags, setTags] = useState((item?.tags ?? []).join("，"));
  const [sourceBookId, setSourceBookId] = useState(item?.source?.bookId ?? "");
  const [sourceLocation, setSourceLocation] = useState(item?.source?.locationLabel ?? "");
  const [sourceExcerpt, setSourceExcerpt] = useState(item?.source?.excerpt ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const initialRef = useRef(JSON.stringify({ title, body, type, status, tags, sourceBookId, sourceLocation, sourceExcerpt }));
  const current = JSON.stringify({ title, body, type, status, tags, sourceBookId, sourceLocation, sourceExcerpt });

  useEffect(() => onDirtyChange(current !== initialRef.current), [current, onDirtyChange]);

  const save = async () => {
    if (saving) return;
    if (!title.trim() && !body.trim() && !sourceExcerpt.trim()) {
      setError("请至少填写标题、正文或来源摘录中的一项。");
      return;
    }
    setSaving(true);
    setError("");
    try {
      const sourceBook = sourceBookId ? books.find((book) => book.id === sourceBookId) : undefined;
      const keepMissingSource = Boolean(sourceBookId && !sourceBook && item?.source?.bookId === sourceBookId);
      const hasSource = Boolean(sourceBookId || sourceLocation.trim() || sourceExcerpt.trim());
      const source = hasSource ? {
        ...(item?.source ?? {}),
        bookId: sourceBook?.id ?? (keepMissingSource ? item?.source?.bookId : undefined),
        bookTitle: sourceBook?.title ?? (keepMissingSource ? item?.source?.bookTitle : undefined),
        bookAuthor: sourceBook?.author ?? (keepMissingSource ? item?.source?.bookAuthor : undefined),
        format: sourceBook?.format ?? item?.source?.format,
        locationLabel: sourceLocation.trim() || undefined,
        excerpt: sourceExcerpt.trim() || undefined,
        createdFrom: item?.source?.createdFrom ?? "manual" as const,
        createdAt: item?.source?.createdAt ?? new Date().toISOString()
      } : undefined;
      let next: MobileSnapshot;
      let savedId: string;
      if (item) {
        next = await updateMobileInspiration(snapshot, item.id, {
          title: title.trim() || "未命名灵感",
          body,
          type,
          status,
          tags: parseTagInput(tags),
          source: source ?? null
        });
        savedId = item.id;
      } else {
        next = await addMobileInspiration(snapshot, {
          title: title.trim() || "未命名灵感",
          body,
          type,
          status,
          tags: parseTagInput(tags),
          source
        });
        savedId = next.inspirations[0]?.id;
      }
      if (!savedId) throw new Error("保存后未找到灵感记录");
      onSnapshotChange(next);
      onDirtyChange(false);
      onSaved(savedId);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "保存失败，请稍后重试。");
      onMessage("保存失败，输入内容仍然保留。");
    } finally {
      setSaving(false);
    }
  };

  const missingSource = Boolean(sourceBookId && !books.some((book) => book.id === sourceBookId));
  return (
    <div className="screen-stack inspiration-editor-v2">
      <header className="inspiration-subpage-topbar">
        <button onClick={onCancel} aria-label="返回"><ArrowLeft size={23} /></button>
        <h1>{item ? "编辑灵感" : "新建灵感"}</h1>
        <button className="inspiration-save-button" disabled={saving} onClick={() => void save()}>{saving ? "保存中…" : "保存"}</button>
      </header>
      <div className="inspiration-editor-scroll">
        <label className="inspiration-field">
          <span>标题</span>
          <input value={title} onChange={(event) => setTitle(event.target.value)} placeholder="给这条灵感一个清楚的名字" maxLength={120} />
        </label>
        <div className="inspiration-editor-pair">
          <label className="inspiration-field"><span>类型</span><select value={type} onChange={(event) => setType(event.target.value as InspirationType)}>{typeOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
          <label className="inspiration-field"><span>状态</span><select value={status} onChange={(event) => setStatus(event.target.value as InspirationStatus)}>{statusOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
        </div>
        <label className="inspiration-field inspiration-body-field">
          <span>正文</span>
          <textarea value={body} onChange={(event) => setBody(event.target.value)} placeholder="写下设定、冲突、人物动作或可以继续发展的片段……" />
        </label>
        <label className="inspiration-field">
          <span>标签</span>
          <input value={tags} onChange={(event) => setTags(event.target.value)} placeholder="用逗号或空格分隔" />
          {parseTagInput(tags).length > 0 && <div className="inspiration-tag-preview">{parseTagInput(tags).map((tag) => <span key={tag}>#{tag}</span>)}</div>}
        </label>
        <section className="inspiration-source-editor">
          <h2>来源（可选）</h2>
          <label className="inspiration-field"><span>来源书籍</span><select value={sourceBookId} onChange={(event) => setSourceBookId(event.target.value)}><option value="">不关联书籍</option>{missingSource && <option value={sourceBookId}>原来源书籍已不可用</option>}{books.map((book) => <option key={book.id} value={book.id}>《{book.title}》{book.author ? ` · ${book.author}` : ""}</option>)}</select></label>
          <label className="inspiration-field"><span>章节或位置</span><input value={sourceLocation} onChange={(event) => setSourceLocation(event.target.value)} placeholder="例如：第 12 章 / 38.5%" /></label>
          <label className="inspiration-field"><span>原文摘录</span><textarea className="inspiration-excerpt-input" value={sourceExcerpt} onChange={(event) => setSourceExcerpt(event.target.value)} placeholder="记录触发灵感的原文，不会混入正文" /></label>
        </section>
        {error && <p className="inspiration-form-error" role="alert">{error}</p>}
        <button className="inspiration-editor-bottom-save" disabled={saving} onClick={() => void save()}>{saving ? "正在保存…" : item ? "保存修改" : "保存灵感"}</button>
      </div>
    </div>
  );
}
