import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ArrowRight, FileText, FolderOpen, Library, Search, Tag, X } from "lucide-react";
import { useCreationActions } from "@/hooks/useCreationActions";
import type { CreationSearchHit, CreationSearchScope } from "@/types/creation";

const SCOPE_LABELS: Array<{ scope: CreationSearchScope; label: string }> = [
  { scope: "scene", label: "正文" },
  { scope: "card", label: "卡片" },
  { scope: "chapter", label: "章节" },
  { scope: "project", label: "项目" }
];

const HIT_ICONS: Record<CreationSearchScope, typeof FileText> = {
  scene: FileText,
  card: Tag,
  chapter: FolderOpen,
  project: Library
};

interface SearchPanelProps {
  projectId?: string;
  onNavigate(hit: CreationSearchHit): void;
  onClose(): void;
}

export function SearchPanel({ projectId, onNavigate, onClose }: SearchPanelProps) {
  const { search } = useCreationActions();
  const inputRef = useRef<HTMLInputElement>(null);
  const [text, setText] = useState("");
  const [scopes, setScopes] = useState<CreationSearchScope[]>([]);
  const [allProjects, setAllProjects] = useState(false);
  const [hits, setHits] = useState<CreationSearchHit[]>([]);
  const [searched, setSearched] = useState(false);
  const [searching, setSearching] = useState(false);
  const requestIdRef = useRef(0);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const runSearch = useCallback(
    async (keyword: string, activeScopes: CreationSearchScope[], acrossAll: boolean) => {
      const trimmed = keyword.trim();
      if (!trimmed) {
        setHits([]);
        setSearched(false);
        return;
      }
      const requestId = ++requestIdRef.current;
      setSearching(true);
      const view = await search({
        text: trimmed,
        projectId: acrossAll ? undefined : projectId,
        scopes: activeScopes.length > 0 ? activeScopes : undefined
      });
      if (requestId !== requestIdRef.current) return;
      setHits(view.hits);
      setSearched(true);
      setSearching(false);
    },
    [projectId, search]
  );

  const debounced = useMemo(() => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    return (keyword: string, activeScopes: CreationSearchScope[], acrossAll: boolean) => {
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => void runSearch(keyword, activeScopes, acrossAll), 250);
    };
  }, [runSearch]);

  useEffect(() => {
    debounced(text, scopes, allProjects);
  }, [debounced, text, scopes, allProjects]);

  const grouped = useMemo(() => {
    const groups: Array<{ scope: CreationSearchScope; label: string; items: CreationSearchHit[] }> = [];
    for (const { scope, label } of SCOPE_LABELS) {
      const items = hits.filter((hit) => hit.kind === scope);
      if (items.length > 0) groups.push({ scope, label, items });
    }
    return groups;
  }, [hits]);

  const highlight = (value: string) => {
    const keyword = text.trim();
    if (!keyword) return value;
    const lower = value.toLowerCase();
    const index = lower.indexOf(keyword.toLowerCase());
    if (index < 0) return value;
    return (
      <>
        {value.slice(0, index)}
        <mark className="creation-search-mark">{value.slice(index, index + keyword.length)}</mark>
        {value.slice(index + keyword.length)}
      </>
    );
  };

  return (
    <div className="creation-search-overlay" role="dialog" aria-label="搜索" aria-modal="true">
      <div className="creation-search-shell" role="search">
        <div className="creation-search-head">
          <Search size={16} className="creation-search-head-icon" />
          <input
            ref={inputRef}
            className="creation-search-input"
            placeholder="搜索正文、卡片、章节与项目标题…"
            value={text}
            onChange={(event) => setText(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Escape") onClose();
            }}
          />
          <button type="button" className="creation-search-close" onClick={onClose} aria-label="关闭搜索">
            <X size={16} />
          </button>
        </div>

        <div className="creation-search-filters">
          <div className="creation-search-scopes" role="group" aria-label="搜索范围">
            <button
              type="button"
              className={scopes.length === 0 ? "active" : ""}
              onClick={() => setScopes([])}
            >
              全部
            </button>
            {SCOPE_LABELS.map(({ scope, label }) => (
              <button
                key={scope}
                type="button"
                className={scopes.includes(scope) ? "active" : ""}
                onClick={() =>
                  setScopes((current) =>
                    current.includes(scope) ? current.filter((item) => item !== scope) : [...current, scope]
                  )
                }
              >
                {label}
              </button>
            ))}
          </div>
          {projectId && (
            <button
              type="button"
              className={`creation-search-global ${allProjects ? "active" : ""}`}
              onClick={() => setAllProjects((value) => !value)}
            >
              {allProjects ? "全部项目" : "当前项目"}
            </button>
          )}
        </div>

        <div className="creation-search-results">
          {searching && <p className="creation-search-state" role="status">正在搜索…</p>}
          {!searching && searched && hits.length === 0 && (
            <p className="creation-search-state">没有找到与「{text.trim()}」匹配的内容。</p>
          )}
          {!searched && !searching && (
            <p className="creation-search-state">输入关键词开始搜索；正文按场景、卡片按标题/别名/字段/标签匹配。</p>
          )}
          {!searching &&
            grouped.map((group) => (
              <section key={group.scope} className="creation-search-group" aria-label={group.label}>
                <h3>{group.label}</h3>
                <ul>
                  {group.items.map((hit) => {
                    const Icon = HIT_ICONS[hit.kind];
                    return (
                      <li key={`${hit.kind}-${hit.id}`}>
                        <button type="button" className="creation-search-hit" onClick={() => onNavigate(hit)}>
                          <span className="creation-search-hit-icon"><Icon size={14} /></span>
                          <span className="creation-search-hit-main">
                            <span className="creation-search-hit-title">
                              {highlight(hit.kind === "project" ? hit.projectTitle : hit.title)}
                            </span>
                            {hit.snippet && (
                              <span className="creation-search-hit-snippet">{highlight(hit.snippet)}</span>
                            )}
                            <span className="creation-search-hit-meta">
                              {hit.kind === "scene" && <>场景 · {hit.chapterTitle}</>}
                              {hit.kind === "card" && <>卡片 · {hit.cardKind}</>}
                              {hit.kind === "chapter" && <>章节</>}
                              {hit.kind === "project" && <>项目</>}
                              {hit.projectId !== projectId && hit.kind !== "project" && (
                                <> · {hit.projectTitle}</>
                              )}
                            </span>
                          </span>
                          <ArrowRight size={14} className="creation-search-hit-arrow" />
                        </button>
                      </li>
                    );
                  })}
                </ul>
              </section>
            ))}
        </div>
      </div>
    </div>
  );
}
