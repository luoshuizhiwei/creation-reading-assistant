import { Copy, Highlighter, Quote, Scissors, X } from "lucide-react";
import type { HighlightColor } from "@/types/library";

export const HIGHLIGHT_COLORS_TXT: { value: HighlightColor; label: string; hex: string }[] = [
  { value: "yellow", label: "黄色", hex: "#fde047" },
  { value: "red", label: "红色", hex: "#fca5a5" },
  { value: "green", label: "绿色", hex: "#86efac" },
  { value: "blue", label: "蓝色", hex: "#93c5fd" },
  { value: "purple", label: "紫色", hex: "#d8b4fe" }
];

export interface SelectionToolbarState {
  visible: boolean;
  x: number;
  y: number;
  charOffset: number;
  charLength: number;
  text: string;
}

interface ReaderSelectionToolbarProps {
  toolbar: SelectionToolbarState;
  showColorPicker: boolean;
  setShowColorPicker: (show: boolean | ((prev: boolean) => boolean)) => void;
  isTxt: boolean;
  onHighlight: (color: HighlightColor) => Promise<void>;
  onCopy: () => Promise<void>;
  onExcerpt: () => void;
  onAddChapterStart: (offset: number) => void;
  onClose: () => void;
}

export function ReaderSelectionToolbar({
  toolbar,
  showColorPicker,
  setShowColorPicker,
  isTxt,
  onHighlight,
  onCopy,
  onExcerpt,
  onAddChapterStart,
  onClose
}: ReaderSelectionToolbarProps) {
  if (!toolbar.visible) return null;

  return (
    <div
      className="fixed z-50 flex items-center gap-0.5 rounded-lg bg-stone-800 px-2 py-1.5 text-sm text-white shadow-xl"
      style={{ left: toolbar.x, top: toolbar.y, transform: "translate(-50%, -100%)" }}
      onMouseDown={(e) => e.stopPropagation()}
    >
      {/* Highlight with color picker */}
      <div className="relative">
        <button
          className="rounded px-2 py-1 hover:bg-stone-700 text-yellow-400"
          title="高亮"
          onClick={(e) => {
            e.stopPropagation();
            setShowColorPicker((v) => !v);
          }}
        >
          <Highlighter size={14} />
        </button>
        {showColorPicker && (
          <div
            className="absolute top-full left-1/2 mt-1 flex -translate-x-1/2 gap-1 rounded-lg bg-stone-800 p-1.5 shadow-xl"
            onMouseDown={(e) => e.stopPropagation()}
          >
            {HIGHLIGHT_COLORS_TXT.map((c) => (
              <button
                key={c.value}
                className="h-5 w-5 rounded-full border border-stone-600 transition-transform hover:scale-110"
                style={{ background: c.hex }}
                title={c.label}
                onClick={async (e) => {
                  e.stopPropagation();
                  await onHighlight(c.value);
                }}
              />
            ))}
          </div>
        )}
      </div>
      <button className="rounded px-2 py-1 hover:bg-stone-700" title="复制" onClick={() => void onCopy()}>
        <Copy size={14} />
      </button>
      <button className="rounded px-2 py-1 hover:bg-stone-700 text-copper-300" title="摘录到资料" onClick={onExcerpt}>
        <Quote size={14} />
      </button>
      {isTxt && (
        <button
          className="rounded px-2 py-1 hover:bg-stone-700"
          title="设为章节起点（目录编辑）"
          onClick={() => onAddChapterStart(toolbar.charOffset)}
        >
          <Scissors size={14} />
        </button>
      )}
      <button className="rounded px-2 py-1 hover:bg-stone-700" title="关闭" onClick={onClose}>
        <X size={14} />
      </button>
    </div>
  );
}
