import React, { useRef } from "react";
import { MoreHorizontal } from "lucide-react";
import type { MobileBook } from "../../types/mobile";
import { formatCompactDateTime } from "../../utils/format";
import { bookStorageLabel, getBookReadiness } from "./book-status";
import type { ShelfViewMode } from "./shelf-types";
import type { ReaderPositionLabel } from "./book-progress";
import { formatReaderPositionLabel } from "./book-progress";

export const BookTile = React.memo(function BookTile({
  book,
  progress,
  position,
  downloaded,
  viewMode,
  actionsOpen,
  selectionMode,
  selected,
  onOpenBook,
  onToggleActions,
  onToggleSelected
}: {
  book: MobileBook;
  progress: number;
  position?: ReaderPositionLabel;
  downloaded: boolean;
  viewMode: ShelfViewMode;
  actionsOpen: boolean;
  selectionMode?: boolean;
  selected?: boolean;
  onOpenBook: (book: MobileBook) => void;
  onToggleActions: (bookId: string) => void;
  onToggleSelected?: (bookId: string) => void;
}) {
  const tileRef = useRef<HTMLElement>(null);
  const readiness = getBookReadiness(book);
  const displayPosition = position ?? { progressPercent: progress };
  const longPressTriggeredRef = useRef(false);
  const longPressTimerRef = useRef<number | undefined>(undefined);

  const clearLongPressTimer = () => {
    if (longPressTimerRef.current !== undefined) {
      window.clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = undefined;
    }
  };

  return (
    <article
      ref={tileRef}
      className={`book-tile reading-book-tile ${viewMode === "list" ? "as-list" : "as-grid"} ${downloaded ? "is-readable" : "needs-content"} ${selectionMode ? "selection-mode" : ""} ${selected ? "is-selected" : ""}`}
      onClick={() => {
        if (selectionMode) {
          onToggleSelected?.(book.id);
          return;
        }
        if (longPressTriggeredRef.current) {
          longPressTriggeredRef.current = false;
          return;
        }
        onOpenBook(book);
      }}
      onTouchStart={(event) => {
        if (selectionMode) return;
        clearLongPressTimer();
        longPressTriggeredRef.current = false;
        longPressTimerRef.current = window.setTimeout(() => {
          longPressTriggeredRef.current = true;
          onToggleActions(book.id);
        }, 500);
      }}
      onTouchEnd={() => {
        if (selectionMode) return;
        clearLongPressTimer();
      }}
      onTouchMove={clearLongPressTimer}
      onTouchCancel={clearLongPressTimer}
    >
      <>
          {book.coverDataUrl ? (
            <div className="book-cover book-cover-image" role="img" aria-label={`《${book.title}》封面`}>
              <img src={book.coverDataUrl} alt={book.title} loading="lazy" />
              <em>{book.format.toUpperCase()}</em>
            </div>
          ) : (
            <div className="book-cover">
              <span>{book.title.slice(0, 4)}</span>
              <em>{book.format.toUpperCase()}</em>
            </div>
          )}
          {selectionMode ? (
            <span className={`book-select-marker ${selected ? "is-checked" : ""}`} aria-hidden="true">
              {selected ? "✓" : ""}
            </span>
          ) : null}
          <div className="book-meta">
            <h3>{book.title}</h3>
            {viewMode === "list" ? (
              <>
                <p>{book.author || "作者未知"} · {formatCompactDateTime(book.lastOpenedAt ?? book.updatedAt)}</p>
                <small>{formatReaderPositionLabel(displayPosition)} · {readiness.label}</small>
                <div className="book-badges">
                  <em className={`book-readiness-pill ${readiness.tone}`}>{bookStorageLabel(book)}</em>
                  {book.duplicateIndex && book.duplicateIndex > 1 ? <em>{book.importLabel}</em> : null}
                </div>
              </>
            ) : (
              <>
                <p className="grid-book-author">{book.author || "作者未知"}</p>
                <small className={`grid-progress-text ${readiness.tone !== "ready" ? `is-${readiness.tone}` : ""}`}>
                  {readiness.tone === "ready" ? formatReaderPositionLabel(displayPosition) : readiness.label}
                </small>
              </>
            )}
            <div className="book-progress-line" aria-label={`阅读进度 ${progress.toFixed(1)}%`}>
              <span style={{ width: `${Math.min(100, Math.max(0, progress))}%` }} />
            </div>
          </div>
          <button
            className="tile-more"
            onClick={(event) => {
              event.stopPropagation();
              onToggleActions(book.id);
            }}
            aria-expanded={actionsOpen}
            aria-label={`打开《${book.title}》管理菜单`}
          >
            <MoreHorizontal size={14} strokeWidth={2.4} aria-hidden="true" />
          </button>
        </>
    </article>
  );
});
