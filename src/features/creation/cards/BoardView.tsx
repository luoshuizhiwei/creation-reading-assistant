import { useState } from "react";
import { Plus } from "lucide-react";
import type { CardSummary, CardType } from "@/types/creation";

interface BoardViewProps {
  cardTypes: CardType[];
  cards: CardSummary[];
  selectedCardId?: string;
  onSelectCard: (cardId: string) => void;
  onMoveKind: (cardId: string, kind: string) => void;
  onNewCard: (kind: string) => void;
}

export function BoardView({
  cardTypes,
  cards,
  selectedCardId,
  onSelectCard,
  onMoveKind,
  onNewCard
}: BoardViewProps) {
  const [draggingId, setDraggingId] = useState<string>();

  return (
    <div className="cards-board">
      {cardTypes.map((type) => {
        const columnCards = cards.filter((card) => card.kind === type.kind);
        return (
          <section
            key={type.id}
            className="cards-board-column"
            onDragOver={(event) => event.preventDefault()}
            onDrop={() => {
              if (draggingId) onMoveKind(draggingId, type.kind);
              setDraggingId(undefined);
            }}
          >
            <header className="cards-board-column-head">
              <span className="cards-board-column-title">{type.name}</span>
              <em>{columnCards.length}</em>
              <button type="button" title="在此类型下新建卡片" onClick={() => onNewCard(type.kind)}>
                <Plus size={13} />
              </button>
            </header>
            <div className="cards-board-column-body">
              {columnCards.map((card) => (
                <div
                  key={card.id}
                  className={`cards-board-card ${card.id === selectedCardId ? "active" : ""}`}
                  draggable
                  onDragStart={() => setDraggingId(card.id)}
                  onClick={() => onSelectCard(card.id)}
                  title="拖到其他列可改变卡片类型"
                >
                  <strong>{card.title}</strong>
                  {card.aliases.length > 0 && (
                    <small className="cards-board-aliases">别名：{card.aliases.join("、")}</small>
                  )}
                  {card.tags.length > 0 && (
                    <span className="cards-board-tags">{card.tags.map((tag) => `#${tag}`).join(" ")}</span>
                  )}
                </div>
              ))}
              {columnCards.length === 0 && <span className="cards-board-empty">暂无卡片</span>}
            </div>
          </section>
        );
      })}
    </div>
  );
}
