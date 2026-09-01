import { useEffect, useRef, useState } from "react";
import { BookOpen, Globe2, Landmark, Users } from "lucide-react";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import {
  canDeleteBackgroundCard,
  isBackgroundCard,
  resolveBackgroundSelection
} from "@/features/creation/background/background-guard";

interface BackgroundPageProps {
  projectId: string;
}

const BACKGROUND_KIND_LABELS = [
  { kind: "location", label: "地点", icon: Landmark },
  { kind: "worldRule", label: "世界规则", icon: Globe2 },
  { kind: "organization", label: "组织", icon: Users },
  { kind: "reference", label: "资料", icon: BookOpen }
] as const;

/** 小说创作背景设定：聚合地点/世界规则/组织/资料等背景类卡片（卡片页二级入口）。 */
export function BackgroundPage({ projectId }: BackgroundPageProps) {
  const { loadCards, runStructure } = useCreationActions();
  const cards = useCreationStore((state) => state.cards);
  const selectedCardId = useCreationStore((state) => state.selectedCardId);
  const selectCard = useCreationStore((state) => state.selectCard);
  const showToast = useUIStore((state) => state.showToast);
  const [search, setSearch] = useState("");
  const [confirmDelete, setConfirmDelete] = useState(false);
  const loadedProjectRef = useRef<string | null>(null);

  useEffect(() => {
    if (loadedProjectRef.current !== projectId) {
      loadedProjectRef.current = projectId;
      selectCard(undefined);
      setConfirmDelete(false);
    }
    void loadCards({ projectId });
  }, [loadCards, projectId, selectCard]);

  const selected = resolveBackgroundSelection(cards, projectId, search, selectedCardId);

  useEffect(() => {
    const current = resolveBackgroundSelection(cards, projectId, search, selectedCardId);
    if (!current && selectedCardId) {
      selectCard(undefined);
      setConfirmDelete(false);
    }
  }, [cards, projectId, search, selectedCardId, selectCard]);

  const handleDelete = async () => {
    if (!selected || !canDeleteBackgroundCard(selected, projectId)) {
      showToast({ tone: "error", title: "无法删除", body: "只能删除当前项目的背景卡（地点/世界规则/组织/资料）。" });
      selectCard(undefined);
      setConfirmDelete(false);
      return;
    }
    const ok = await runStructure({ type: "card.delete", cardId: selected.id });
    if (ok) {
      showToast({ tone: "success", title: "背景卡片已删除", body: `「${selected.title}」已进入回收站。` });
      selectCard(undefined);
      void loadCards({ projectId });
    }
    setConfirmDelete(false);
  };

  const backgroundCards = cards.filter(
    (card) =>
      card.projectId === projectId &&
      isBackgroundCard(card) &&
      (search.trim() ? card.title.includes(search.trim()) || card.aliases.some((alias) => alias.includes(search.trim())) : true)
  );

  return (
    <section className="cards-page background-page" aria-label="背景设定">
      <header className="cards-toolbar">
        <div className="cards-toolbar-left">
          <span className="desktop-card-label">World building</span>
          <h2 className="background-page-title">创作背景设定</h2>
          <input
            className="cards-input cards-search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="搜索背景卡…"
          />
        </div>
      </header>

      <div className="background-grid">
        {backgroundCards.length === 0 ? (
          <p className="cards-relations-empty">还没有背景设定卡。到「卡片」页创建地点、世界规则、组织或资料卡，这里会自动汇总。</p>
        ) : (
          backgroundCards.map((card) => {
            const meta = BACKGROUND_KIND_LABELS.find((item) => item.kind === card.kind);
            const Icon = meta?.icon ?? BookOpen;
            return (
              <button
                key={card.id}
                type="button"
                className={`background-card ${card.id === selectedCardId ? "active" : ""}`}
                onClick={() => selectCard(card.id)}
              >
                <span className="background-card-kind"><Icon size={13} /> {meta?.label ?? card.kind}</span>
                <span className="background-card-title">{card.title}</span>
                <span className="background-card-fields">
                  {Object.entries(card.fields).slice(0, 2).map(([key, value]) => (
                    <em key={key}>{String(value).slice(0, 60)}</em>
                  ))}
                </span>
              </button>
            );
          })
        )}
      </div>

      {selected && (
        <aside className="background-detail" aria-label="背景卡详情">
          <div className="background-detail-head">
            <h3>{selected.title}</h3>
            <span className="background-detail-kind">
              {BACKGROUND_KIND_LABELS.find((item) => item.kind === selected.kind)?.label ?? selected.kind}
            </span>
          </div>
          {selected.aliases.length > 0 && <p className="cards-aliases">别名：{selected.aliases.join("、")}</p>}
          {selected.tags.length > 0 && <p className="cards-tags">{selected.tags.map((tag) => `#${tag}`).join(" ")}</p>}
          <dl className="background-detail-fields">
            {Object.entries(selected.fields).map(([key, value]) => (
              <div key={key}><dt>{key}</dt><dd>{String(value)}</dd></div>
            ))}
          </dl>
          <div className="background-detail-actions">
            <button
              type="button"
              className={confirmDelete ? "confirming" : ""}
              onClick={() => {
                if (confirmDelete) void handleDelete();
                else setConfirmDelete(true);
              }}
            >
              {confirmDelete ? "确认删除" : "删除"}
            </button>
          </div>
        </aside>
      )}
    </section>
  );
}
