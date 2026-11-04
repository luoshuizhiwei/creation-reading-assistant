import { useId, useState, type CSSProperties } from "react";

export interface SliderProps {
  label: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  /** 数值显示格式化，默认原样输出 */
  format?(value: number): string;
  onChange(value: number): void;
  /** 显示轨道下方刻度点 */
  ticks?: boolean;
  className?: string;
  disabled?: boolean;
}

/**
 * Slider — 纸感定制滑块
 *
 * 保留原生 input[type=range] 的键盘与无障碍行为，视觉通过
 * settings-controls.css 的 .paper-slider 重绘，右侧数值徽章拖动时高亮。
 */
export function Slider({
  label,
  value,
  min,
  max,
  step = 1,
  format,
  onChange,
  ticks = false,
  className = "",
  disabled = false
}: SliderProps) {
  const inputId = useId();
  const [dragging, setDragging] = useState(false);
  const progress = max > min ? ((Math.min(max, Math.max(min, value)) - min) / (max - min)) * 100 : 0;
  const display = format ? format(value) : String(value);

  return (
    <div className={`grid gap-1 ${className}`}>
      <div className="flex items-center justify-between gap-2">
        <label htmlFor={inputId} className="text-sm font-medium text-paper-ink">
          {label}
        </label>
        <span className={`paper-slider-value ${dragging ? "paper-slider-value--active" : ""}`} aria-hidden="true">
          {display}
        </span>
      </div>
      <input
        id={inputId}
        type="range"
        className="paper-slider"
        style={{ "--slider-progress": `${progress}%` } as CSSProperties}
        min={min}
        max={max}
        step={step}
        value={value}
        disabled={disabled}
        aria-valuetext={display}
        onChange={(event) => onChange(Number(event.target.value))}
        onPointerDown={() => setDragging(true)}
        onPointerUp={() => setDragging(false)}
        onBlur={() => setDragging(false)}
      />
      {ticks && (
        <div className="paper-slider-ticks" aria-hidden="true">
          {Array.from({ length: 5 }, (_, index) => (
            <span key={index} />
          ))}
        </div>
      )}
    </div>
  );
}
