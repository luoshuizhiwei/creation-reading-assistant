import { useEffect, useRef, useState } from "react";
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

  // BD-190：关联成功后这一行的按钮被换成「已关联」span，握着焦点的节点被卸载、
  // 焦点跌回 body（WCAG 2.4.3，同 BD-179/BD-190 收件箱的删除流）。发起前记下
  // 最近的未关联邻行（先下后上），cards 更新后把焦点交接过去；无处可交退到搜索框。
  const searchRef = useRef<HTMLInputElement>(null);
  const linkRefs = useRef(new Map<string, HTMLButtonElement>());
  const pendingFocusRef = useRef<string | null>(null);
  useEffect(() => {
    const pending = pendingFocusRef.current;
    if (!pending) return;
    pendingFocusRef.current = null;
    const target = linkRefs.current.get(pending);
    (target?.isConnected ? target : searchRef.current)?.focus({ preventScroll: true });
  }, [cards]);

  const handleLink = async (cardId: string) => {
    const index = cards.findIndex((card) => card.id === cardId);
    const next =
      cards.slice(index + 1).find((card) => !linked(card)) ??
      [...cards.slice(0, index)].reverse().find((card) => !linked(card));
    pendingFocusRef.current = next?.id ?? cardId;
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
        <label className="cards-search"><Search size={14} /><input ref={searchRef} value={search} onChange={(event) => setSearch(event.target.value)} placeholder="搜索名称、别名或字段" aria-label="搜索全局卡片" /></label>
        <Select value={kind} onChange={(event) => setKind(event.target.value)} aria-label="按类型筛选全局卡片">
          <option value="">全部类型</option>
          {cardTypes.map((type) => <option key={type.id} value={type.kind}>{type.name}</option>)}
        </Select>
      </div>
      {/* BD-189：首帧的「正在读取…」随对话框一起挂载，带内容的 role="status" 不会被
          播报；检索触发的忙/空结果同理。播报交给这条常驻区，可见横幅只作视觉提示。 */}
      <span className="sr-only" role="status">
        {loading ? "正在读取全局卡片…" : cards.length === 0 ? "没有符合条件的全局卡片。" : ""}
      </span>
      {loading ? <p className="project-card-link-empty">正在读取全局卡片…</p>
        : cards.length === 0 ? <p className="project-card-link-empty">没有符合条件的全局卡片。</p>
          : <ul className="project-card-link-list">{cards.map((card) => {
            const isLinked = linked(card);
            const typeName = cardTypes.find((type) => type.kind === card.kind)?.name ?? card.kind;
            return <li key={card.id}><div><strong>{card.title || "未命名卡片"}</strong><span>{typeName}{card.aliases.length > 0 ? ` · ${card.aliases.join("、")}` : ""}</span></div>
              {isLinked ? <span className="project-card-link-status">已关联</span>
                : <Button type="button" variant="secondary" disabled={linkingCardId !== null} ref={(el) => {
                    if (el) linkRefs.current.set(card.id, el);
                    else linkRefs.current.delete(card.id);
                  }} onClick={() => void handleLink(card.id)}><Link2 size={14} /> {linkingCardId === card.id ? "关联中…" : "关联到当前项目"}</Button>}
            </li>;
          })}</ul>}
    </Dialog>
  );
}
