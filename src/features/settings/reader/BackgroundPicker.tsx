import { Check } from "lucide-react";
import type { ReaderBackground } from "@/types/library";
import { READER_BACKGROUNDS } from "./reader-backgrounds";

export interface BackgroundPickerProps {
  value: ReaderBackground;
  onChange(value: ReaderBackground): void;
  label?: string;
  className?: string;
  settingId?: string;
}

/**
 * BackgroundPicker — 书籍背景色卡
 *
 * 色块直接复用 .reader-bg-* 类（与阅读页同一颜色来源），
 * 选中态为印刷蓝描边 + 对勾。role=radiogroup 保证键盘可达。
 */
export function BackgroundPicker({ value, onChange, label = "书籍背景", className = "", settingId }: BackgroundPickerProps) {
  return (
    <div className={`grid gap-1.5 ${className}`} data-setting-id={settingId}>
      <span className="text-sm font-medium text-paper-ink">{label}</span>
      <div role="radiogroup" aria-label={label} className="flex flex-wrap gap-1">
        {READER_BACKGROUNDS.map((option) => {
          const selected = option.value === value;
          return (
            <button
              key={option.value}
              type="button"
              role="radio"
              aria-checked={selected}
              aria-label={option.label}
              title={option.label}
              className="reader-bg-swatch"
              onClick={() => onChange(option.value)}
            >
              <span className={`reader-bg-swatch-chip ${option.className}`}>
                {selected && <Check size={14} aria-hidden="true" />}
              </span>
              <span className="reader-bg-swatch-name">{option.label}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
