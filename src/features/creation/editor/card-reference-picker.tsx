import { useEffect, useMemo, useRef, useState } from "react";
import { Search, Tag, X } from "lucide-react";
import type { CardSummary } from "@/types/creation";

interface CardReferencePickerProps {
  /** 候选卡片：调用方保证只传当前项目的卡片（异项目卡片不可见）。 */
  cards: CardSummary[];
  onSelect(card: CardSummary): void;
  onClose(): void;
}

function matchesCard(card: CardSummary, query: string): boolean {
  if (!query.trim()) return true;
  const lower = query.trim().toLowerCase();
  return (
    card.title.toLowerCase().includes(lower) ||
    card.aliases.some((alias) => alias.toLowerCase().includes(lower))
  );
}

/**
 * @ 卡片引用选择器：搜索当前项目卡片（主名称 + 别名）。
 * 选择卡片后由父级建立批注关联；本组件不写正文、不修改文档。
 */
export function CardReferencePicker({ cards, onSelect, onClose }: CardReferencePickerProps) {
  const [query, setQuery] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const restoreFocusRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    restoreFocusRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;
    inputRef.current?.focus();
    const target = restoreFocusRef.current;
    return () => {
      if (target && document.contains(target) && typeof target.focus === "function") {
        target.focus({ preventScroll: true });
      }
    };
  }, []);

  const filtered = useMemo(() => cards.filter((card) => matchesCard(card, query)), [cards, query]);
  const listId = "card-reference-list";
  const optionId = (id: string) => `card-reference-option-${id}`;
  const activeCard = filtered[Math.max(0, activeIndex)];

  useEffect(() => {
    setActiveIndex(0);
  }, [query]);

  const commit = (card: CardSummary) => {
    onSelect(card);
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.nativeEvent.isComposing || event.nativeEvent.keyCode === 229) return;
    if (filtered.length === 0) return;
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActiveIndex((current) => (current + 1) % filtered.length);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActiveIndex((current) => (current <= 0 ? filtered.length - 1 : current - 1));
    } else if (event.key === "Enter") {
      event.preventDefault();
      const target = filtered[Math.max(0, activeIndex)];
      if (target) commit(target);
    }
  };

  // 焦点可能停在输入框、关闭钮或某张卡片上，Esc 要在整个面板范围内生效。
  const handleRootKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.nativeEvent.isComposing || event.nativeEvent.keyCode === 229) return;
    if (event.key === "Escape") {
      event.preventDefault();
      onClose();
    }
  };

  useEffect(() => {
    const activeElement = listRef.current?.querySelector<HTMLElement>(".card-reference-item.active");
    if (activeElement && typeof activeElement.scrollIntoView === "function") {
      activeElement.scrollIntoView({ block: "nearest" });
    }
  }, [activeIndex]);

  return (
    <div className="card-reference-picker" role="dialog" aria-label="引用卡片搜索" aria-modal="false" onKeyDown={handleRootKeyDown}>
      <div className="card-reference-head">
        <Search size={14} />
        <input
          ref={inputRef}
          className="card-reference-input"
          aria-label="搜索卡片（标题或别名）"
          placeholder="搜索卡片（标题或别名）…"
          role="combobox"
          aria-expanded="true"
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={activeCard ? optionId(activeCard.id) : undefined}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={handleKeyDown}
        />
        <button type="button" className="card-reference-close" onClick={onClose} aria-label="关闭卡片引用搜索">
          <X size={14} />
        </button>
      </div>
      <div className="card-reference-list" ref={listRef} id={listId} role="listbox" aria-label="可引用卡片">
        {filtered.length === 0 ? (
          <p className="card-reference-empty">没有匹配当前项目的卡片。可先到「卡片」页创建。</p>
        ) : (
          filtered.map((card, index) => (
            <button
              key={card.id}
              id={optionId(card.id)}
              type="button"
              role="option"
              aria-selected={index === activeIndex}
              className={`card-reference-item ${index === activeIndex ? "active" : ""}`}
              onClick={() => commit(card)}
            >
              <Tag size={12} />
              <span className="card-reference-item-main">
                <strong>{card.title}</strong>
                {card.aliases.length > 0 && <small>别名：{card.aliases.join("、")}</small>}
              </span>
            </button>
          ))
        )}
      </div>
    </div>
  );
}
