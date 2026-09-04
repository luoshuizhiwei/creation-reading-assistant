import { Plus, Search } from "lucide-react";
import { Select } from "@/components/ui";
import type { CardSummary, CardType } from "@/types/creation";

export interface CardListSidebarProps {
  cards: CardSummary[];
  selectedCardId?: string;
  cardsLoading: boolean;
  onSelectCard: (cardId: string) => void;
  typeNameMap: Map<string, string>;
  cardTypes?: CardType[];
  filterKind?: string;
  onFilterKindChange?: (kind: string) => void;
  search?: string;
  onSearchChange?: (search: string) => void;
  onNewCard?: () => void;
  showControls?: boolean;
}

export function CardListSidebar({
  cards,
  selectedCardId,
  cardsLoading,
  onSelectCard,
  typeNameMap,
  cardTypes,
  filterKind,
  onFilterKindChange,
  search,
  onSearchChange,
  onNewCard,
  showControls = false
}: CardListSidebarProps) {
  return (
    <aside className="cards-list-pane">
      {showControls && (
        <div className="cards-sidebar-controls" style={{ display: "grid", gap: 6, marginBottom: 6 }}>
          {onSearchChange && (
            <span className="cards-search" style={{ width: "100%" }}>
              <Search size={14} />
              <input
                value={search ?? ""}
                onChange={(event) => onSearchChange(event.target.value)}
                placeholder="搜索名称或别名"
                style={{ width: "100%" }}
              />
            </span>
          )}
          {cardTypes && onFilterKindChange && (
            <Select
              className="cards-input cards-kind-filter"
              value={filterKind ?? ""}
              onChange={(event) => onFilterKindChange(event.target.value)}
              aria-label="按类型筛选"
              style={{ width: "100%" }}
            >
              <option value="">全部类型</option>
              {cardTypes.map((type) => (
                <option key={type.id} value={type.kind}>{type.name}</option>
              ))}
            </Select>
          )}
          {onNewCard && (
            <button type="button" className="cards-add" onClick={onNewCard} style={{ justifyContent: "center" }}>
              <Plus size={15} /> 新建卡片
            </button>
          )}
        </div>
      )}
      {cardsLoading ? (
        <p className="cards-empty" role="status">正在读取卡片…</p>
      ) : cards.length === 0 ? (
        <p className="cards-empty">还没有卡片。点右上角「新建卡片」创建第一张。</p>
      ) : (
        cards.map((card) => (
          <button
            type="button"
            key={card.id}
            className={`cards-list-item ${card.id === selectedCardId ? "active" : ""}`}
            onClick={() => onSelectCard(card.id)}
          >
            <span className="cards-list-kind">{typeNameMap.get(card.kind) ?? card.kind}</span>
            <strong>{card.title}</strong>
            {card.tags.length > 0 && (
              <span className="cards-list-tags">{card.tags.map((tag) => `#${tag}`).join(" ")}</span>
            )}
          </button>
        ))
      )}
    </aside>
  );
}
