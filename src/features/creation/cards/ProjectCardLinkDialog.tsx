import { useEffect, useState } from "react";
import { Link2, Search } from "lucide-react";
import { Button, Dialog, Select } from "@/components/ui";
import { useCardActions } from "@/hooks/creation/useCardActions";
import type { CardSummary, CardType } from "@/types/creation";

interface ProjectCardLinkDialogProps {
  projectId: string;
  linkedCardIds: string[];
  cardTypes: CardType[];
  onClose: () => void;
  onLink: (cardId: string) => Promise<boolean>;
}

/** 项目卡片页的全局库选择器；查询不接管项目页的 cards store 作用域。 */
export function ProjectCardLinkDialog({ projectId, linkedCardIds, cardTypes, onClose, onLink }: ProjectCardLinkDialogProps) {
  const { listGlobalCards } = useCardActions();
  const [cards, setCards] = useState<CardSummary[]>([]);
  const [search, setSearch] = useState("");
  const [kind, setKind] = useState("");
  const [loading, setLoading] = useState(true);
  const [linkingCardId, setLinkingCardId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    void listGlobalCards({ cardKind: kind || undefined, search: search.trim() || undefined }).then((result) => {
      if (!cancelled) {
        setCards(result);
        setLoading(false);
      }
    });
    return () => { cancelled = true; };
  }, [kind, listGlobalCards, search]);

  const linked = (card: CardSummary) => linkedCardIds.includes(card.id) || card.linkedProjectIds?.includes(projectId) === true;
  const handleLink = async (cardId: string) => {
    setLinkingCardId(cardId);
    const ok = await onLink(cardId);
    setLinkingCardId(null);
    if (ok) {
      setCards((current) => current.map((card) => card.id === cardId
        ? { ...card, linkedProjectIds: [...new Set([...(card.linkedProjectIds ?? []), projectId])], usageCount: (card.usageCount ?? 0) + 1 }
        : card));
    }
  };

  return (
    <Dialog open title="关联全局卡片" ariaLabel="关联全局卡片" onClose={onClose} width="max-w-2xl">
      <p className="project-card-link-note">选择卡片后会关联到当前项目；不会复制或删除卡片库中的原件。</p>
      <div className="project-card-link-filters">
        <label className="cards-search"><Search size={14} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="搜索名称、别名或字段" aria-label="搜索全局卡片" /></label>
        <Select value={kind} onChange={(event) => setKind(event.target.value)} aria-label="按类型筛选全局卡片">
          <option value="">全部类型</option>
          {cardTypes.map((type) => <option key={type.id} value={type.kind}>{type.name}</option>)}
        </Select>
      </div>
      {loading ? <p className="project-card-link-empty" role="status">正在读取全局卡片…</p>
        : cards.length === 0 ? <p className="project-card-link-empty">没有符合条件的全局卡片。</p>
          : <ul className="project-card-link-list">{cards.map((card) => {
            const isLinked = linked(card);
            const typeName = cardTypes.find((type) => type.kind === card.kind)?.name ?? card.kind;
            return <li key={card.id}><div><strong>{card.title || "未命名卡片"}</strong><span>{typeName}{card.aliases.length > 0 ? ` · ${card.aliases.join("、")}` : ""}</span></div>
              {isLinked ? <span className="project-card-link-status">已关联</span>
                : <Button type="button" variant="secondary" disabled={linkingCardId !== null} onClick={() => void handleLink(card.id)}><Link2 size={14} /> {linkingCardId === card.id ? "关联中…" : "关联到当前项目"}</Button>}
            </li>;
          })}</ul>}
    </Dialog>
  );
}
