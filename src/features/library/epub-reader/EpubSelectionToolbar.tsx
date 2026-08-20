import { Copy, GripHorizontal, Highlighter, Quote, X } from "lucide-react";
import type { HighlightColor } from "@/types/library";
import { HIGHLIGHT_COLORS } from "./highlight-colors";

export interface SelectionToolbarState {
  visible: boolean;
  x: number;
  y: number;
  cfiRange: string;
  text: string;
}

export interface EpubSelectionToolbarProps {
  toolbar: SelectionToolbarState | null;
  toolbarOffset: { x: number; y: number };
  showColorPicker: boolean;
  onToggleColorPicker(): void;
  onDragStart(e: React.MouseEvent): void;
  onHighlight(color: HighlightColor): void;
  onCopy(text: string): void;
  onExcerpt(): void;
  onClose(): void;
}

export function EpubSelectionToolbar({
  toolbar,
  toolbarOffset,
  showColorPicker,
  onToggleColorPicker,
  onDragStart,
  onHighlight,
  onCopy,
  onExcerpt,
  onClose
}: EpubSelectionToolbarProps) {
  if (!toolbar?.visible) return null;
  return (
    <div
      className="absolute z-50 flex items-center gap-0.5 rounded-lg bg-stone-800 px-2 py-1.5 text-sm text-white shadow-xl"
      style={{ left: toolbar.x + toolbarOffset.x, top: toolbar.y + toolbarOffset.y, transform: "translate(-50%, -100%)" }}
      onMouseDown={(e) => e.stopPropagation()}
    >
      <span
        className="mr-0.5 cursor-grab rounded px-0.5 py-0.5 text-stone-400 hover:text-white active:cursor-grabbing"
        onMouseDown={onDragStart}
        title="拖动"
      >
        <GripHorizontal size={12} />
      </span>
      <div className="relative">
        <button
          className="rounded px-2 py-1 hover:bg-stone-700 text-yellow-400"
          title="高亮"
          onClick={(e) => {
            e.stopPropagation();
            onToggleColorPicker();
          }}
        >
          <Highlighter size={14} />
        </button>
        {showColorPicker && (
          <div
            className="absolute top-full left-1/2 mt-1 flex -translate-x-1/2 gap-1 rounded-lg bg-stone-800 p-1.5 shadow-xl"
            onMouseDown={(e) => e.stopPropagation()}
          >
            {HIGHLIGHT_COLORS.map((c) => (
              <button
                key={c.value}
                className="h-5 w-5 rounded-full border border-stone-600 transition-transform hover:scale-110"
                style={{ background: c.hex }}
                title={c.label}
                onClick={async (e) => {
                  e.stopPropagation();
                  onHighlight(c.value);
                }}
              />
            ))}
          </div>
        )}
      </div>
      <button
        className="rounded px-2 py-1 hover:bg-stone-700"
        title="复制"
        onClick={() => onCopy(toolbar.text)}
      >
        <Copy size={14} />
      </button>
      <button
        className="rounded px-2 py-1 hover:bg-stone-700 text-copper-300"
        title="摘录到资料"
        onClick={onExcerpt}
      >
        <Quote size={14} />
      </button>
      <button
        className="rounded px-2 py-1 hover:bg-stone-700"
        title="关闭"
        onClick={onClose}
      >
        <X size={14} />
      </button>
    </div>
  );
}
