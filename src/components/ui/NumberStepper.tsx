import { useEffect, useId, useState } from "react";
import { Minus, Plus } from "lucide-react";

export interface NumberStepperProps {
  label?: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  unit?: string;
  onChange(value: number): void;
  className?: string;
  disabled?: boolean;
  /** 标签下方的说明文字 */
  hint?: string;
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

/**
 * NumberStepper — 带 −/+ 步进与单位后缀的数字输入
 *
 * 输入过程允许暂存非法文本，失焦时钳制回 [min, max]；
 * 步进按钮在边界处禁用。
 */
export function NumberStepper({
  label,
  value,
  min,
  max,
  step = 1,
  unit,
  onChange,
  className = "",
  disabled = false,
  hint
}: NumberStepperProps) {
  const inputId = useId();
  const [text, setText] = useState(String(value));

  useEffect(() => {
    setText(String(value));
  }, [value]);

  const commit = (raw: string) => {
    const next = Number(raw);
    if (raw.trim() === "" || !Number.isFinite(next)) {
      setText(String(value));
      return;
    }
    const clamped = clamp(Math.round(next / step) * step, min, max);
    setText(String(clamped));
    if (clamped !== value) onChange(clamped);
  };

  const bump = (delta: number) => {
    const clamped = clamp(value + delta, min, max);
    if (clamped !== value) onChange(clamped);
  };

  const control = (
    <div className={`paper-stepper ${disabled ? "opacity-45" : ""}`}>
      <button
        type="button"
        tabIndex={-1}
        aria-label={label ? `减小${label}` : "减小"}
        disabled={disabled || value <= min}
        onClick={() => bump(-step)}
      >
        <Minus size={13} />
      </button>
      <input
        id={label ? inputId : undefined}
        type="number"
        inputMode="numeric"
        min={min}
        max={max}
        step={step}
        value={text}
        disabled={disabled}
        aria-label={label}
        onChange={(event) => setText(event.target.value)}
        onBlur={(event) => commit(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === "Enter") commit((event.target as HTMLInputElement).value);
        }}
      />
      <button
        type="button"
        tabIndex={-1}
        aria-label={label ? `增大${label}` : "增大"}
        disabled={disabled || value >= max}
        onClick={() => bump(step)}
      >
        <Plus size={13} />
      </button>
      {unit && <span className="paper-stepper-unit">{unit}</span>}
    </div>
  );

  if (!label) return <div className={className}>{control}</div>;

  return (
    <div className={`grid gap-1.5 text-sm ${className}`}>
      <label htmlFor={inputId} className="grid gap-1">
        <span className="font-medium text-paper-ink">{label}</span>
        {hint && <span className="text-xs leading-5 text-paper-muted">{hint}</span>}
      </label>
      {control}
    </div>
  );
}
