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
  const [announcement, setAnnouncement] = useState("");

  // BD-211（WCAG 2.5.7）：拖到别列改类型原本是唯一入口——键盘用户按 Enter 只能「选中」。
  // 补一条非拖拽路径：选中卡片后 Ctrl/⌘ + ←/→ 在类型列之间移动（与拖动同义），
  // 结果走常驻 role=status 播报（BD-175 的账：状态区必须常驻，不能随内容一起挂）。
  const moveByKeyboard = (card: CardSummary, direction: -1 | 1) => {
    const index = cardTypes.findIndex((type) => type.kind === card.kind);
    if (index < 0) return;
    const target = cardTypes[index + direction];
    if (!target) return;
    onMoveKind(card.id, target.kind);
    setAnnouncement(`已把「${card.title}」移到「${target.name}」`);
  };

  return (
    <div className="cards-board">
      <span className="sr-only" role="status">{announcement}</span>
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
              <button
                type="button"
                title="在此类型下新建卡片"
                aria-label={`在「${type.name}」下新建卡片`}
                onClick={() => onNewCard(type.kind)}
              >
                <Plus size={13} />
              </button>
            </header>
            <div className="cards-board-column-body">
              {columnCards.map((card) => (
                <div
                  key={card.id}
                  className={`cards-board-card ${card.id === selectedCardId ? "active" : ""}`}
                  draggable
                  role="button"
                  tabIndex={0}
                  aria-pressed={card.id === selectedCardId}
                  aria-label={`选择卡片：${card.title}`}
                  onDragStart={() => setDraggingId(card.id)}
                  onClick={() => onSelectCard(card.id)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      onSelectCard(card.id);
                      return;
                    }
                    if ((event.key === "ArrowLeft" || event.key === "ArrowRight") && (event.ctrlKey || event.metaKey)) {
                      event.preventDefault();
                      moveByKeyboard(card, event.key === "ArrowLeft" ? -1 : 1);
                    }
                  }}
                  title="拖到其他列可改变卡片类型；也可 Ctrl+←/→ 移动"
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
