import { ChevronLeft, ChevronRight } from "lucide-react";

export interface EpubPageTurnButtonsProps {
  onPrev(): void;
  onNext(): void;
}

export function EpubPageTurnButtons({ onPrev, onNext }: EpubPageTurnButtonsProps) {
  return (
    <div className="pointer-events-none absolute inset-x-6 bottom-4 flex justify-between">
      <button
        className="pointer-events-auto inline-flex h-10 w-10 items-center justify-center rounded-full border border-paper-line bg-paper-panel/90 text-paper-muted hover:text-copper"
        onClick={onPrev}
        title="上一页"
        aria-label="上一页"
      >
        <ChevronLeft size={18} />
      </button>
      <button
        className="pointer-events-auto inline-flex h-10 w-10 items-center justify-center rounded-full border border-paper-line bg-paper-panel/90 text-paper-muted hover:text-copper"
        onClick={onNext}
        title="下一页"
        aria-label="下一页"
      >
        <ChevronRight size={18} />
      </button>
    </div>
  );
}
