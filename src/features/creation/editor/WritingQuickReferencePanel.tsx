import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent } from "react";
import { BookOpenCheck, Link2, PanelRightClose, Search, Sparkles, Unlink2, UsersRound } from "lucide-react";
import { CardDynamicFields } from "@/features/creation/cards/components/CardDynamicFields";
import { parseList } from "@/features/creation/cards/components/CardEditorForm";
import {
  cardLink,
  cardRead,
  cardsList,
  cardTypesList,
  runStructure
} from "@/services/creation-service";
import type { CardSummary, CardType, ScenePlanning } from "@/types/creation";
import { messageFromError } from "@/utils/format";
import "./writing-quick-reference.css";

type QuickScope = "scene" | "project" | "global";
type SaveStatus = "saved" | "dirty" | "saving" | "error";

const MIN_WIDTH = 280;
const MAX_WIDTH = 480;
const DEFAULT_WIDTH = 320;
const WIDTH_STORAGE_KEY = "creation-writing-quick-reference-width";

function readInitialWidth(): number {
  const stored = Number.parseInt(window.localStorage.getItem(WIDTH_STORAGE_KEY) ?? "", 10);
  return Number.isFinite(stored) ? Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, stored)) : DEFAULT_WIDTH;
}

function mergeCard(cards: CardSummary[], next: CardSummary): CardSummary[] {
  return cards.some((card) => card.id === next.id)
    ? cards.map((card) => card.id === next.id ? next : card)
    : [...cards, next];
}

export interface WritingQuickReferencePanelProps {
  open: boolean;
  projectId: string;
  selectedSceneId?: string;
  planning?: ScenePlanning;
  contextCards: CardSummary[];
  onContextCardsChange(cards: CardSummary[]): void;
  onPlanningSaved(): Promise<void> | void;
  onClose(): void;
  onRestoreEditorFocus(): void;
}

export function WritingQuickReferencePanel({
  open,
  projectId,
  selectedSceneId,
  planning,
  contextCards,
  onContextCardsChange,
  onPlanningSaved,
  onClose,
  onRestoreEditorFocus
}: WritingQuickReferencePanelProps) {
  const [scope, setScope] = useState<QuickScope>("scene");
  const [width, setWidth] = useState(readInitialWidth);
  const [projectCards, setProjectCards] = useState<CardSummary[]>([]);
  const [globalCards, setGlobalCards] = useState<CardSummary[]>([]);
  const [cardTypes, setCardTypes] = useState<CardType[]>([]);
  const [query, setQuery] = useState("");
  const [selectedCardId, setSelectedCardId] = useState<string>();
  const [draft, setDraft] = useState<CardSummary>();
  const [loading, setLoading] = useState(false);
  const [saveStatus, setSaveStatus] = useState<SaveStatus>("saved");
  const [saveError, setSaveError] = useState("");
  const saveTimerRef = useRef<ReturnType<typeof setTimeout>>();
  const draftRef = useRef<CardSummary>();
  draftRef.current = draft;

  const refreshProjectCards = useCallback(async () => {
    const result = await cardsList({ kind: "cards.list", projectId });
    setProjectCards(result);
    return result;
  }, [projectId]);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setLoading(true);
    Promise.all([cardsList({ kind: "cards.list", projectId }), cardTypesList()])
      .then(([cards, types]) => {
        if (cancelled) return;
        setProjectCards(cards);
        setCardTypes(types);
      })
      .catch((error) => {
        if (!cancelled) {
          setSaveError(messageFromError(error));
          setSaveStatus("error");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [open, projectId]);

  useEffect(() => {
    if (!open || scope !== "global") return;
    if (!query.trim()) {
      setGlobalCards([]);
      return;
    }
    let cancelled = false;
    const timer = setTimeout(() => {
      setLoading(true);
      cardsList({ kind: "cards.list", search: query.trim() })
        .then((cards) => {
          if (!cancelled) setGlobalCards(cards);
        })
        .catch((error) => {
          if (!cancelled) {
            setSaveError(messageFromError(error));
            setSaveStatus("error");
          }
        })
        .finally(() => {
          if (!cancelled) setLoading(false);
        });
    }, 240);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [open, query, scope]);

  useEffect(() => {
    setSelectedCardId(undefined);
    setDraft(undefined);
    setScope("scene");
    setSaveStatus("saved");
    setSaveError("");
  }, [projectId, selectedSceneId]);

  useEffect(() => () => {
    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
  }, []);

  const sceneCardIds = useMemo(() => new Set([
    planning?.perspectiveCardId,
    planning?.locationCardId,
    ...(planning?.castCardIds ?? [])
  ].filter((value): value is string => Boolean(value))), [planning]);
  const sceneCards = useMemo(
    () => projectCards.filter((card) => sceneCardIds.has(card.id)),
    [projectCards, sceneCardIds]
  );
  const visibleCards = scope === "scene" ? sceneCards : scope === "project" ? projectCards : globalCards;
  const selectedType = cardTypes.find((type) => type.kind === draft?.kind);
  const isLinked = draft ? draft.linkedProjectIds.includes(projectId) : false;
  const isInScene = draft ? (planning?.castCardIds ?? []).includes(draft.id) : false;
  const isContextCard = draft ? contextCards.some((card) => card.id === draft.id) : false;

  const saveNow = useCallback(async (): Promise<boolean> => {
    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    const current = draftRef.current;
    if (!current || saveStatus === "saved") return true;
    if (!current.title.trim()) {
      setSaveError("名称不能为空；修改仍保留在面板中。");
      setSaveStatus("error");
      return false;
    }
    const type = cardTypes.find((item) => item.kind === current.kind);
    const missing = (type?.fields ?? []).filter((field) => {
      if (!field.required) return false;
      const value = current.fields[field.key];
      return Array.isArray(value) ? value.length === 0 : value === undefined || value === null || value === "";
    });
    if (missing.length > 0) {
      setSaveError(`请填写：${missing.map((field) => field.label).join("、")}`);
      setSaveStatus("error");
      return false;
    }
    setSaveStatus("saving");
    setSaveError("");
    try {
      await runStructure({
        type: "card.update",
        cardId: current.id,
        title: current.title.trim(),
        aliases: current.aliases,
        fields: current.fields,
        tags: current.tags,
        baseRevision: current.revision
      });
      const saved = await cardRead(current.id);
      if (!saved) throw new Error("保存后无法重新读取卡片。");
      setDraft(saved);
      setProjectCards((cards) => mergeCard(cards, saved));
      setGlobalCards((cards) => mergeCard(cards, saved));
      if (contextCards.some((card) => card.id === saved.id)) {
        onContextCardsChange(mergeCard(contextCards, saved));
      }
      setSaveStatus("saved");
      return true;
    } catch (error) {
      setSaveError(`${messageFromError(error)} 修改仍保留，可再次失焦重试。`);
      setSaveStatus("error");
      return false;
    }
  }, [cardTypes, contextCards, onContextCardsChange, saveStatus]);

  const scheduleSave = useCallback(() => {
    if (!draftRef.current || saveStatus === "saved" || saveStatus === "saving") return;
    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    saveTimerRef.current = setTimeout(() => { void saveNow(); }, 800);
  }, [saveNow, saveStatus]);

  const changeDraft = (next: CardSummary) => {
    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    setDraft(next);
    setSaveStatus("dirty");
    setSaveError("");
  };

  const openCard = async (card: CardSummary) => {
    if (draft && draft.id !== card.id && !(await saveNow())) return;
    const latest = await cardRead(card.id) ?? card;
    setSelectedCardId(latest.id);
    setDraft(latest);
    setSaveStatus("saved");
    setSaveError("");
    onContextCardsChange(mergeCard(contextCards, latest));
  };

  const closePanel = async () => {
    if (!(await saveNow())) return;
    onClose();
    requestAnimationFrame(onRestoreEditorFocus);
  };

  const linkToProject = async () => {
    if (!draft || !(await saveNow())) return;
    try {
      await cardLink(projectId, draft.id);
      const latest = await cardRead(draft.id);
      if (latest) {
        setDraft(latest);
        setProjectCards((cards) => mergeCard(cards, latest));
        onContextCardsChange(mergeCard(contextCards, latest));
      }
      await refreshProjectCards();
      requestAnimationFrame(onRestoreEditorFocus);
    } catch (error) {
      setSaveError(messageFromError(error));
      setSaveStatus("error");
    }
  };

  const addToScene = async () => {
    if (!draft || !selectedSceneId || !isLinked || draft.kind !== "character" || !(await saveNow())) return;
    try {
      await runStructure({
        type: "scene.updatePlanning",
        sceneId: selectedSceneId,
        planning: {
          ...planning,
          castCardIds: [...new Set([...(planning?.castCardIds ?? []), draft.id])]
        }
      });
      await onPlanningSaved();
      requestAnimationFrame(onRestoreEditorFocus);
    } catch (error) {
      setSaveError(messageFromError(error));
      setSaveStatus("error");
    }
  };

  const beginResize = (event: ReactPointerEvent<HTMLDivElement>) => {
    const startX = event.clientX;
    const startWidth = width;
    event.currentTarget.setPointerCapture(event.pointerId);
    const move = (moveEvent: PointerEvent) => {
      const next = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, startWidth + startX - moveEvent.clientX));
      setWidth(next);
      window.localStorage.setItem(WIDTH_STORAGE_KEY, String(next));
    };
    const finish = () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", finish);
      window.removeEventListener("pointercancel", finish);
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", finish);
    window.addEventListener("pointercancel", finish);
  };

  if (!open) return null;
  const statusText = saveStatus === "saving" ? "保存中…" : saveStatus === "dirty" ? "等待失焦保存" : saveStatus === "error" ? "保存失败" : "已保存";

  return (
    <aside
      className="writing-quick-reference"
      aria-label="写作速查"
      style={{ "--quick-reference-width": `${width}px` } as CSSProperties}
    >
      <div className="writing-quick-reference-resizer" role="separator" aria-orientation="vertical" aria-label="调整速查面板宽度" onPointerDown={beginResize} />
      <header className="writing-quick-reference-head">
        <div>
          <p className="desktop-card-label">Index drawer</p>
          <h3>写作速查</h3>
        </div>
        <button type="button" onClick={() => void closePanel()} aria-label="关闭写作速查" title="关闭（Ctrl+Shift+K）"><PanelRightClose size={16} /></button>
      </header>

      <div className="writing-quick-context" aria-label="速查 AI 上下文">
        <Sparkles size={13} /> AI 上下文 {contextCards.length}
        {contextCards.length > 0 && <span>发送前可逐组排除</span>}
      </div>

      <nav className="writing-quick-tabs" aria-label="速查范围">
        <button type="button" className={scope === "scene" ? "active" : ""} onClick={() => setScope("scene")}>本场景 <span>{sceneCards.length}</span></button>
        <button type="button" className={scope === "project" ? "active" : ""} onClick={() => setScope("project")}>本项目 <span>{projectCards.length}</span></button>
        <button type="button" className={scope === "global" ? "active" : ""} onClick={() => setScope("global")}>全局搜索</button>
      </nav>

      {scope === "global" && (
        <label className="writing-quick-search">
          <Search size={14} />
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索名称、别名或字段" autoFocus />
        </label>
      )}

      <div className="writing-quick-body">
        <ul className="writing-quick-list" aria-label={`${scope === "scene" ? "本场景" : scope === "project" ? "本项目" : "全局搜索"}卡片`}>
          {visibleCards.map((card) => (
            <li key={card.id}>
              <button type="button" className={selectedCardId === card.id ? "active" : ""} onClick={() => void openCard(card)}>
                <span className={`writing-quick-kind writing-quick-kind--${card.kind}`} />
                <span><strong>{card.title}</strong><small>{cardTypes.find((type) => type.kind === card.kind)?.name ?? card.kind}</small></span>
                <em>{card.usageCount}</em>
              </button>
            </li>
          ))}
          {!loading && visibleCards.length === 0 && (
            <li className="writing-quick-empty">
              {scope === "global" && !query.trim() ? "输入关键词，在整个卡片库中查找。" : scope === "scene" ? "本场景尚未引用卡片。" : "没有匹配的卡片。"}
            </li>
          )}
          {loading && <li className="writing-quick-empty">正在整理索引…</li>}
        </ul>

        {draft ? (
          <section className="writing-quick-detail" aria-label={`速查卡片：${draft.title}`} onBlurCapture={scheduleSave}>
            <div className="writing-quick-detail-head">
              <span>{selectedType?.name ?? draft.kind}</span>
              <button
                type="button"
                className={isContextCard ? "active" : ""}
                onClick={() => onContextCardsChange(isContextCard ? contextCards.filter((card) => card.id !== draft.id) : mergeCard(contextCards, draft))}
              >
                {isContextCard ? <Unlink2 size={12} /> : <Sparkles size={12} />}
                {isContextCard ? "移出 AI 上下文" : "加入 AI 上下文"}
              </button>
            </div>
            <label className="writing-quick-field"><span>名称</span><input value={draft.title} onChange={(event) => changeDraft({ ...draft, title: event.target.value })} /></label>
            <label className="writing-quick-field"><span>别名</span><input value={draft.aliases.join("，")} onChange={(event) => changeDraft({ ...draft, aliases: parseList(event.target.value) })} /></label>
            <CardDynamicFields
              fields={selectedType?.fields ?? []}
              values={draft.fields}
              allCards={projectCards}
              onChange={(key, value) => changeDraft({ ...draft, fields: { ...draft.fields, [key]: value } })}
            />
            <label className="writing-quick-field"><span>标签</span><input value={draft.tags.join("，")} onChange={(event) => changeDraft({ ...draft, tags: parseList(event.target.value) })} /></label>
            <div className={`writing-quick-save writing-quick-save--${saveStatus}`} role="status">
              <span>{statusText}</span>{saveError && <small>{saveError}</small>}
            </div>
            <div className="writing-quick-actions">
              <button type="button" onClick={() => void linkToProject()} disabled={isLinked}><Link2 size={13} />{isLinked ? "已关联项目" : "关联到项目"}</button>
              {draft.kind === "character" && (
                <button type="button" onClick={() => void addToScene()} disabled={!isLinked || !selectedSceneId || isInScene}>
                  <UsersRound size={13} />{isInScene ? "已在本场景" : "加入本场景"}
                </button>
              )}
              <button type="button" onClick={() => void closePanel()}><BookOpenCheck size={13} />回到正文</button>
            </div>
          </section>
        ) : (
          <section className="writing-quick-detail writing-quick-detail--empty">
            <BookOpenCheck size={18} /><p>打开一张卡片，边写边核对设定。</p><small>打开的卡片会进入可见 AI 上下文组。</small>
          </section>
        )}
      </div>
    </aside>
  );
}
